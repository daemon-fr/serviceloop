package com.v16studio.serviceloop.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.BitmapFactory
import android.graphics.RectF
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
    private val writer: ReportWriter = ReportWriter { model, renditionId, generatedAt, file -> FixedServiceRecordPdf.render(model, renditionId, generatedAt, file, context.filesDir) },
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
    internal const val WIDTH = 595
    internal const val HEIGHT = 842
    internal const val LEFT = 42f
    internal const val RIGHT = 42f
    internal const val TOP = 48f
    internal const val BOTTOM = 54f
    internal const val CONTENT_WIDTH = WIDTH - LEFT - RIGHT
    internal const val CONTENT_HEIGHT = HEIGHT - TOP - BOTTOM

    internal enum class LineStyle(val textSize: Float, val height: Float, val bold: Boolean) {
        TITLE(18f, 31f, true), SECTION(12f, 20f, true), BODY(10f, 15f, false), FOOTER(8f, 10f, false),
    }

    internal data class ReportDrawLine(val text: String, val style: LineStyle) { val height: Float get() = style.height }
    internal data class ReportPage(val lines: List<ReportDrawLine>) { val contentHeight: Float get() = lines.sumOf { it.height.toDouble() }.toFloat() }
    private data class RawLine(val text: String, val style: LineStyle)

    fun render(model: PublicReportModel, renditionId: String, generatedAtEpochMillis: Long, file: File, attachmentRoot: File? = null): Int {
        val pages = layout(model)
        val photos = model.lines.flatMap { line -> line.photos.mapIndexed { index, photo -> Triple(line, index, photo) } }
        val totalPages = pages.size + photos.size
        val document = PdfDocument()
        try {
            pages.forEachIndexed { pageIndex, lines ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, pageIndex + 1).create())
                var y = TOP
                lines.lines.forEach { line ->
                    page.canvas.drawText(line.text, LEFT, y + line.style.textSize, paint(line.style))
                    y += line.height
                }
                val footer = "${model.visitReference} · R${model.revisionNumber} · PDF v1 · ${renditionId.take(8)} · ${Instant.ofEpochMilli(generatedAtEpochMillis).toString().take(10)} · Page ${pageIndex + 1} of $totalPages"
                wrap(RawLine(footer, LineStyle.FOOTER)).take(4).forEachIndexed { footerIndex, line ->
                    page.canvas.drawText(line.text, LEFT, HEIGHT - 44f + footerIndex * LineStyle.FOOTER.height + LineStyle.FOOTER.textSize, paint(LineStyle.FOOTER).apply { color = Color.DKGRAY })
                }
                document.finishPage(page)
            }
            photos.forEachIndexed { photoPageIndex, (line, photoIndex, photo) ->
                val source=attachmentRoot?.let{File(it,photo.relativePath)} ?: error("Photograph storage is unavailable")
                val bitmap=BitmapFactory.decodeFile(source.absolutePath) ?: error("Selected report photograph is missing or unreadable")
                val pageNumber=pages.size+photoPageIndex+1; val page=document.startPage(PdfDocument.PageInfo.Builder(WIDTH,HEIGHT,pageNumber).create())
                page.canvas.drawText("${line.equipmentReference} · ${line.equipmentName}",LEFT,TOP+LineStyle.SECTION.textSize,paint(LineStyle.SECTION))
                page.canvas.drawText("Photograph ${photoIndex+1}${photo.caption?.let{": $it"}.orEmpty()}",LEFT,TOP+38f,paint(LineStyle.BODY))
                val availableHeight=HEIGHT-TOP-BOTTOM-70f; val scale=minOf(CONTENT_WIDTH/bitmap.width,availableHeight/bitmap.height); val width=bitmap.width*scale; val height=bitmap.height*scale
                page.canvas.drawBitmap(bitmap,null,RectF(LEFT,TOP+55f,LEFT+width,TOP+55f+height),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                val footer="${model.visitReference} · R${model.revisionNumber} · PDF v1 · ${renditionId.take(8)} · Page $pageNumber of $totalPages"; page.canvas.drawText(footer,LEFT,HEIGHT-34f,paint(LineStyle.FOOTER).apply{color=Color.DKGRAY})
                document.finishPage(page); bitmap.recycle()
            }
            FileOutputStream(file).use(document::writeTo)
        } finally { document.close() }
        return totalPages
    }

    internal fun layout(model: PublicReportModel): List<ReportPage> {
        val lines = buildLines(model).ifEmpty { listOf(ReportDrawLine("No public service content", LineStyle.BODY)) }
        val pages = mutableListOf<MutableList<ReportDrawLine>>(); var current = mutableListOf<ReportDrawLine>(); var used = 0f
        fun newPage() { if (current.isNotEmpty()) pages += current; current = mutableListOf(); used = 0f }
        lines.forEachIndexed { index, line ->
            val keepWithNext = line.style == LineStyle.SECTION && index + 1 < lines.size
            val required = line.height + if (keepWithNext) lines[index + 1].height else 0f
            if (current.isNotEmpty() && used + required > CONTENT_HEIGHT) newPage()
            if (current.isNotEmpty() && used + line.height > CONTENT_HEIGHT) newPage()
            current += line; used += line.height
        }
        newPage()
        return pages.map(::ReportPage)
    }

    internal fun measuredWidth(line: ReportDrawLine): Float = paint(line.style).measureText(line.text)

    private fun buildLines(model: PublicReportModel): List<ReportDrawLine> {
        val raw = mutableListOf(RawLine(model.businessName, LineStyle.TITLE), RawLine("Service record ${model.visitReference} · Revision ${model.revisionNumber}", LineStyle.BODY), RawLine("Technician: ${model.technicianName}", LineStyle.BODY), RawLine(model.businessContact, LineStyle.BODY), RawLine("Service date: ${model.actualServiceDate}", LineStyle.BODY), RawLine("Customer and site", LineStyle.SECTION), RawLine("${model.customerReference.orEmpty()} · ${model.customerName}", LineStyle.BODY), RawLine("${model.siteReference.orEmpty()} · ${model.siteName}", LineStyle.BODY), RawLine(model.siteAddress.orEmpty(), LineStyle.BODY))
        model.lines.forEach { line ->
            raw += RawLine("${line.equipmentReference} · ${line.equipmentName}", LineStyle.SECTION)
            raw += listOf(RawLine(line.equipmentIdentification, LineStyle.BODY), RawLine("Service: ${line.planReference?.let { "$it · " }.orEmpty()}${line.serviceName}", LineStyle.BODY), RawLine("Outcome: ${line.outcome.replace('_', ' ')}", LineStyle.BODY))
            line.publicWorkNote?.let { raw += RawLine("Work: $it", LineStyle.BODY) }; line.notPerformedReason?.let { raw += RawLine("Reason: $it", LineStyle.BODY) }
            line.parts.forEach { part -> raw += RawLine("Part: ${part.description} — ${part.quantity} ${part.unit}", LineStyle.BODY) }
            line.photos.forEachIndexed { photoIndex, photo -> raw += RawLine("Photograph ${photoIndex + 1}${photo.caption?.let { ": $it" }.orEmpty()}", LineStyle.BODY) }
            raw += RawLine(when { !line.isRecurringPlan -> "Due effect: one-off work — no recurring due date effect"; line.fulfilledObligation -> "Due effect: ${line.oldDueDate} to ${line.nextDueDate}"; else -> "Due effect: current service remains due ${line.oldDueDate}" }, LineStyle.BODY)
            line.checklist.forEach { q -> raw += RawLine("${q.position}. ${q.label}: ${q.value ?: q.disposition.replace('_', ' ')}${q.unit?.let { " $it" }.orEmpty()}${q.reason?.let { " — $it" }.orEmpty()}", LineStyle.BODY) }
        }
        return raw.filter { it.text.isNotBlank() }.flatMap(::wrap)
    }

    private fun wrap(raw: RawLine): List<ReportDrawLine> {
        val paint = paint(raw.style); val result = mutableListOf<ReportDrawLine>(); var current = ""
        fun flush() { if (current.isNotEmpty()) { result += ReportDrawLine(current, raw.style); current = "" } }
        fun acceptWord(word: String) {
            var remaining = word
            while (remaining.isNotEmpty()) {
                if (paint.measureText(remaining) <= CONTENT_WIDTH) { current = remaining; return }
                val count = paint.breakText(remaining, true, CONTENT_WIDTH, null).coerceAtLeast(1)
                result += ReportDrawLine(remaining.take(count), raw.style); remaining = remaining.drop(count)
            }
        }
        raw.text.trim().split(Regex("\\s+")).filter(String::isNotEmpty).forEach { word ->
            if (current.isEmpty()) acceptWord(word)
            else {
                val candidate = "$current $word"
                if (paint.measureText(candidate) <= CONTENT_WIDTH) current = candidate else { flush(); acceptWord(word) }
            }
        }
        flush(); return result
    }

    private fun paint(style: LineStyle) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(31, 42, 48); textSize = style.textSize; typeface = Typeface.create("sans", if (style.bold) Typeface.BOLD else Typeface.NORMAL) }
}
