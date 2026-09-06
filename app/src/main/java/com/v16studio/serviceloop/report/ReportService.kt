package com.v16studio.serviceloop.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.withTransaction
import com.v16studio.serviceloop.data.ReportRenditionEntity
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.domain.PublicReportModel
import com.v16studio.serviceloop.domain.ReportRendition
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface PdfWriteGate { suspend fun beforeRender() }
fun interface ReportMetadataGate { suspend fun beforeReadyCommit() }
fun interface ReportWriter { fun render(model: PublicReportModel, renditionId: String, generatedAtEpochMillis: Long, file: File): Int }

interface ReportService {
    suspend fun generate(recordId: String): ReportRendition
    suspend fun pageCount(relativePath: String): Int
    fun file(relativePath: String): File
}

class AndroidReportService(
    private val context: Context,
    private val database: ServiceLoopDatabase,
    private val repository: ServiceLoopRepository,
    private val writeGate: PdfWriteGate = PdfWriteGate {},
    private val writer: ReportWriter = ReportWriter(FixedServiceRecordPdf::render),
    private val metadataGate: ReportMetadataGate = ReportMetadataGate {},
) : ReportService {
    private val dao = database.serviceLoopDao()

    override fun file(relativePath: String): File = File(context.filesDir, relativePath)

    override suspend fun generate(recordId: String): ReportRendition = mutex.withLock {
        val detail = repository.finalRecord(recordId) ?: error("Final service record no longer exists")
        dao.reportRendition(detail.public.revisionId)?.let { existing ->
            if (existing.status == "READY" && file(existing.relativePath).isFile) return@withLock existing.toDomain()
            if (existing.status == "READY") error("Report file is missing")
        }
        val renditionId = dao.reportRendition(detail.public.revisionId)?.id ?: UUID.nameUUIDFromBytes("report-v1:${detail.public.revisionId}".toByteArray()).toString()
        val relative = "reports/$recordId/$renditionId.pdf"
        val existing = dao.reportRendition(detail.public.revisionId)
        val generating = ReportRenditionEntity(renditionId, detail.public.revisionId, 1, null, relative, null, null, null, "GENERATING", "ORIGINAL", null)
        if (existing == null) dao.insertReportRendition(generating) else dao.updateReportRendition(generating)
        val target = file(relative); target.parentFile?.mkdirs(); val temp = File(target.parentFile, "$renditionId.tmp")
        var adopted = false
        try {
            writeGate.beforeRender()
            val generatedAt = System.currentTimeMillis()
            val pageCount = writer.render(detail.public, renditionId, generatedAt, temp)
            require(temp.length() > 0) { "Generated PDF was empty" }
            if (target.exists()) target.delete()
            check(temp.renameTo(target)) { "Could not adopt generated report" }
            adopted = true
            val bytes = target.length(); val hash = sha256(target)
            val ready = generating.copy(generatedAtEpochMillis = generatedAt, sha256 = hash, byteSize = bytes, pageCount = pageCount, status = "READY")
            metadataGate.beforeReadyCommit()
            database.withTransaction { dao.updateReportRendition(ready) }
            ready.toDomain()
        } catch (cancelled: CancellationException) {
            temp.delete()
            if (adopted) target.delete()
            throw cancelled
        } catch (failure: Exception) {
            temp.delete()
            if (adopted) target.delete()
            val failed = generating.copy(status = "FAILED", failureMessage = failure.message ?: "PDF generation failed")
            runCatching { dao.updateReportRendition(failed) }
            throw IllegalStateException("PDF generation failed; the service record is already finalized", failure)
        }
    }

    override suspend fun pageCount(relativePath: String): Int {
        val pdf = file(relativePath); require(pdf.isFile) { "Report file is missing" }
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor -> PdfRenderer(descriptor).use { return it.pageCount } }
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    private fun ReportRenditionEntity.toDomain() = ReportRendition(id, revisionId, versionNumber, generatedAtEpochMillis, relativePath, sha256, byteSize, pageCount, status, failureMessage)

    private companion object { val mutex = Mutex() }
}

object FixedServiceRecordPdf {
    private const val WIDTH = 595
    private const val HEIGHT = 842
    private const val LEFT = 42f
    private const val TOP = 48f
    private const val BOTTOM = 54f
    private const val LINE = 15f

    fun render(model: PublicReportModel, renditionId: String, generatedAtEpochMillis: Long, file: File): Int {
        val logical = buildLines(model)
        val linesPerPage = ((HEIGHT - TOP - BOTTOM) / LINE).toInt()
        val pages = logical.chunked(linesPerPage).ifEmpty { listOf(listOf("No public service content")) }
        val document = PdfDocument()
        try {
            pages.forEachIndexed { pageIndex, lines ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, pageIndex + 1).create())
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(31, 42, 48); textSize = 10f; typeface = Typeface.create("sans", Typeface.NORMAL) }
                var y = TOP
                lines.forEach { line ->
                    if (line.startsWith("# ")) { paint.textSize = 18f; paint.typeface = Typeface.DEFAULT_BOLD; page.canvas.drawText(line.removePrefix("# "), LEFT, y, paint); paint.textSize = 10f; paint.typeface = Typeface.DEFAULT; y += 8f }
                    else if (line.startsWith("## ")) { paint.textSize = 12f; paint.typeface = Typeface.DEFAULT_BOLD; page.canvas.drawText(line.removePrefix("## "), LEFT, y, paint); paint.textSize = 10f; paint.typeface = Typeface.DEFAULT }
                    else page.canvas.drawText(line, LEFT, y, paint)
                    y += LINE
                }
                paint.textSize = 8f; paint.color = Color.DKGRAY
                page.canvas.drawText("Revision ${model.revisionNumber} · Report v1 · ${renditionId.take(8)} · Generated ${Instant.ofEpochMilli(generatedAtEpochMillis)} · Page ${pageIndex + 1} of ${pages.size}", LEFT, HEIGHT - 28f, paint)
                document.finishPage(page)
            }
            FileOutputStream(file).use(document::writeTo)
        } finally { document.close() }
        return pages.size
    }

    private fun buildLines(model: PublicReportModel): List<String> {
        val raw = mutableListOf("# ${model.businessName}", "Service record ${model.visitReference} · Revision ${model.revisionNumber}", "Technician: ${model.technicianName}", model.businessContact, "Service date: ${model.actualServiceDate}", "## Customer and site", "${model.customerReference.orEmpty()} · ${model.customerName}", "${model.siteReference.orEmpty()} · ${model.siteName}", model.siteAddress.orEmpty())
        model.lines.forEach { line ->
            raw += listOf("## ${line.equipmentReference} · ${line.equipmentName}", line.equipmentIdentification, "Service: ${line.planReference?.let { "$it · " }.orEmpty()}${line.serviceName}", "Outcome: ${line.outcome.replace('_', ' ')}")
            line.publicWorkNote?.let { raw += "Work: $it" }; line.notPerformedReason?.let { raw += "Reason: $it" }
            raw += when { !line.isRecurringPlan -> "Due effect: one-off work — no recurring due date effect"; line.fulfilledObligation -> "Due effect: ${line.oldDueDate} to ${line.nextDueDate}"; else -> "Due effect: current service remains due ${line.oldDueDate}" }
            line.checklist.forEach { q -> raw += "${q.position}. ${q.label}: ${q.value ?: q.disposition.replace('_', ' ')}${q.unit?.let { " $it" }.orEmpty()}${q.reason?.let { " — $it" }.orEmpty()}" }
        }
        return raw.filter(String::isNotBlank).flatMap { wrap(it, 86) }
    }

    private fun wrap(text: String, width: Int): List<String> {
        val prefix = when { text.startsWith("# ") -> "# "; text.startsWith("## ") -> "## "; else -> "" }; val words = text.removePrefix(prefix).split(Regex("\\s+")); val result = mutableListOf<String>(); var line = prefix
        words.forEach { word -> if (line.removePrefix(prefix).isNotEmpty() && line.length + word.length + 1 > width) { result += line; line = word } else line += (if (line.isEmpty() || line == prefix) "" else " ") + word }
        if (line.isNotBlank()) result += line; return result
    }
}
