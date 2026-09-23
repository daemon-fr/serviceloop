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
import com.v16studio.serviceloop.data.BusinessFileCoordinator
import com.v16studio.serviceloop.brand.ServiceLoopBrandSpec
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

internal fun technicianReportName(name: String, designation: String?): String =
    name + designation?.trim()?.takeIf(String::isNotEmpty)?.let { " · $it" }.orEmpty()

fun interface PdfWriteGate { suspend fun beforeRender() }
fun interface ReportMetadataGate { suspend fun beforeReadyCommit() }
fun interface ReportWriter { fun render(model: PublicReportModel, renditionId: String, versionNumber: Int, generatedAtEpochMillis: Long, file: File): Int }

interface ReportService {
    suspend fun generate(recordId: String): ReportRendition
    suspend fun generateRevision(recordId: String, revisionId: String): ReportRendition = generate(recordId)
    suspend fun pageCount(relativePath: String): Int
    fun file(relativePath: String): File
}

class AndroidReportService(
    private val context: Context,
    private val database: ServiceLoopDatabase,
    private val repository: ServiceLoopRepository,
    private val writeGate: PdfWriteGate = PdfWriteGate {},
    private val writer: ReportWriter = ReportWriter { model, renditionId, version, generatedAt, file -> FixedServiceRecordPdf.render(model, renditionId, version, generatedAt, file, context.filesDir) },
    private val metadataGate: ReportMetadataGate = ReportMetadataGate {},
) : ReportService {
    private val dao = database.serviceLoopDao()

    override fun file(relativePath: String): File = File(context.filesDir, relativePath)

    override suspend fun generate(recordId: String): ReportRendition = generateLocked(recordId, null)

    override suspend fun generateRevision(recordId: String, revisionId: String): ReportRendition = generateLocked(recordId, revisionId)

    private suspend fun generateLocked(recordId: String, requestedRevisionId: String?): ReportRendition = BusinessFileCoordinator.mutex.withLock { mutex.withLock {
        val detail = (if (requestedRevisionId == null) repository.finalRecord(recordId) else repository.finalRecordRevision(recordId, requestedRevisionId))
            ?: error("Final service record revision no longer exists")
        val previous = dao.reportRendition(detail.public.revisionId)
        previous?.let { existing -> if (existing.status == "READY" && file(existing.relativePath).isFile && (!detail.voided || existing.kind == "VOID_NOTICE")) return@withLock existing.toDomain() }
        verifySelectedPhotographs(detail.public)
        val recreatingMissing = previous?.status == "MISSING" || (previous?.status == "READY" && !file(previous.relativePath).isFile)
        val creatingVoidNotice = requestedRevisionId == null && detail.voided && previous?.kind != "VOID_NOTICE"
        val version = if (recreatingMissing || creatingVoidNotice) (previous?.versionNumber ?: 0) + 1 else previous?.versionNumber ?: 1
        val renditionId = if (recreatingMissing || creatingVoidNotice) UUID.randomUUID().toString() else previous?.id ?: UUID.nameUUIDFromBytes("report-v1:${detail.public.revisionId}".toByteArray()).toString()
        val relative = "reports/$recordId/$renditionId.pdf"
        val generating = ReportRenditionEntity(renditionId, detail.public.revisionId, version, null, relative, null, null, null, "GENERATING", if (creatingVoidNotice) "VOID_NOTICE" else if (recreatingMissing) "RECREATED" else if (detail.public.revisionNumber > 1) "CORRECTION" else "ORIGINAL", null)
        if (recreatingMissing || creatingVoidNotice || previous == null) dao.insertReportRendition(generating) else dao.updateReportRendition(generating)
        val target = file(relative); target.parentFile?.mkdirs(); val temp = File(target.parentFile, "$renditionId.tmp")
        var adopted = false
        try {
            writeGate.beforeRender()
            val generatedAt = System.currentTimeMillis()
            val pageCount = writer.render(detail.public, renditionId, version, generatedAt, temp)
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
    } }

    override suspend fun pageCount(relativePath: String): Int {
        val pdf = file(relativePath); require(pdf.isFile) { "Report file is missing" }
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor -> PdfRenderer(descriptor).use { return it.pageCount } }
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    private fun verifySelectedPhotographs(model: PublicReportModel) {
        model.lines.flatMap { it.photos }.forEach { photo ->
            val source = file(photo.relativePath)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (!source.isFile || source.length() != photo.byteSize || sha256(source) != photo.sha256) {
                error(PHOTO_INTEGRITY_FAILURE)
            }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error(PHOTO_INTEGRITY_FAILURE)
        }
    }
    private fun ReportRenditionEntity.toDomain() = ReportRendition(id, revisionId, versionNumber, generatedAtEpochMillis, relativePath, sha256, byteSize, pageCount, status, failureMessage, kind)

    private companion object {
        val mutex = Mutex()
        const val PHOTO_INTEGRITY_FAILURE = "Selected report photograph is missing or no longer matches the recorded evidence. Restore the original exact file or use an explicit correction."
    }
}

object FixedServiceRecordPdf {
    internal const val WIDTH = 595
    internal const val HEIGHT = 842
    internal const val LEFT = 42f
    internal const val RIGHT = 42f
    internal const val TOP = 72f
    internal const val BOTTOM = 68f
    internal const val CONTENT_WIDTH = WIDTH - LEFT - RIGHT
    internal const val CONTENT_HEIGHT = HEIGHT - TOP - BOTTOM

    internal enum class LineStyle(val textSize: Float, val height: Float, val bold: Boolean) {
        TITLE(19f, 30f, true), SECTION(11f, 21f, true), SUBSECTION(10f, 18f, true), BODY(9.5f, 14f, false),
        TABLE_HEADER(8.5f, 17f, true), TABLE_ROW(9f, 16f, false), ALERT(10f, 19f, true), META(8.5f, 13f, false), FOOTER(7.5f, 10f, false),
    }

    internal data class ReportDrawLine(val text: String, val style: LineStyle) { val height: Float get() = style.height }
    internal data class ReportPage(val lines: List<ReportDrawLine>) { val contentHeight: Float get() = lines.sumOf { it.height.toDouble() }.toFloat() }
    private data class RawLine(val text: String, val style: LineStyle)

    fun render(model: PublicReportModel, renditionId: String, versionNumber: Int = 1, generatedAtEpochMillis: Long, file: File, attachmentRoot: File? = null): Int {
        val pages = layout(model)
        val photos = model.lines.flatMap { line -> line.photos.mapIndexed { index, photo -> Triple(line, index, photo) } }
        val totalPages = pages.size + photos.size
        val document = PdfDocument()
        try {
            pages.forEachIndexed { pageIndex, lines ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, pageIndex + 1).create())
                page.canvas.drawColor(Color.WHITE)
                drawHeader(page.canvas, model)
                var y = TOP
                lines.lines.forEach { line ->
                    val linePaint = paint(line.style)
                    val x = if (line.style == LineStyle.ALERT || line.style == LineStyle.TABLE_ROW) LEFT + 8f else LEFT
                    if (line.style == LineStyle.ALERT) {
                        page.canvas.drawRect(LEFT, y + 1f, WIDTH - RIGHT, y + line.height - 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 244, 226) })
                    } else if (line.style == LineStyle.TABLE_HEADER) {
                        page.canvas.drawRect(LEFT, y + 1f, WIDTH - RIGHT, y + line.height - 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(231, 244, 244) })
                    }
                    page.canvas.drawText(line.text, x, y + line.style.textSize + 3f, linePaint)
                    y += line.height
                }
                val footer = "${ServiceLoopBrandSpec.GENERATED_WITH} · ${model.visitReference} · R${model.revisionNumber} · PDF v$versionNumber · ${renditionId.take(8)} · ${Instant.ofEpochMilli(generatedAtEpochMillis).toString().take(10)} · Page ${pageIndex + 1} of $totalPages"
                page.canvas.drawLine(LEFT, HEIGHT - BOTTOM - 9f, WIDTH - RIGHT, HEIGHT - BOTTOM - 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; strokeWidth = 1f })
                wrap(RawLine(footer, LineStyle.FOOTER)).take(3).forEachIndexed { footerIndex, line ->
                    page.canvas.drawText(line.text, LEFT, HEIGHT - BOTTOM + footerIndex * LineStyle.FOOTER.height + LineStyle.FOOTER.textSize, paint(LineStyle.FOOTER).apply { color = Color.DKGRAY })
                }
                document.finishPage(page)
            }
            photos.forEachIndexed { photoPageIndex, (line, photoIndex, photo) ->
                val source=attachmentRoot?.let{File(it,photo.relativePath)} ?: error("Photograph storage is unavailable")
                val bitmap=BitmapFactory.decodeFile(source.absolutePath) ?: error("Selected report photograph is missing or unreadable")
                val pageNumber=pages.size+photoPageIndex+1; val page=document.startPage(PdfDocument.PageInfo.Builder(WIDTH,HEIGHT,pageNumber).create())
                page.canvas.drawColor(Color.WHITE)
                drawHeader(page.canvas, model)
                page.canvas.drawText("Photographic evidence", LEFT, TOP + LineStyle.SECTION.textSize + 3f, paint(LineStyle.SECTION))
                val contextLines = wrap(RawLine(subjectLabel(line), LineStyle.SUBSECTION))
                contextLines.take(2).forEachIndexed { index, context -> page.canvas.drawText(context.text, LEFT, TOP + 31f + index * context.height, paint(LineStyle.SUBSECTION)) }
                val caption = "Photograph ${photoIndex + 1}${photo.caption?.let { ": $it" }.orEmpty()}${if (photo.addedInCorrection) " · Added in correction ${photo.addedAtEpochMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() }.orEmpty()}" else ""}"
                val captionLines = wrap(RawLine(caption, LineStyle.BODY))
                captionLines.take(3).forEachIndexed { index, captionLine -> page.canvas.drawText(captionLine.text, LEFT, TOP + 65f + index * captionLine.height, paint(LineStyle.BODY)) }
                val imageTop = TOP + 65f + captionLines.take(3).size * LineStyle.BODY.height + 12f
                val availableHeight=HEIGHT-BOTTOM-imageTop-18f; val scale=minOf(CONTENT_WIDTH/bitmap.width,availableHeight/bitmap.height); val width=bitmap.width*scale; val height=bitmap.height*scale
                val imageLeft = LEFT + (CONTENT_WIDTH - width) / 2f
                page.canvas.drawBitmap(bitmap,null,RectF(imageLeft,imageTop,imageLeft+width,imageTop+height),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                page.canvas.drawLine(LEFT, HEIGHT - BOTTOM - 9f, WIDTH - RIGHT, HEIGHT - BOTTOM - 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; strokeWidth = 1f })
                val footer="${ServiceLoopBrandSpec.GENERATED_WITH} · ${model.visitReference} · R${model.revisionNumber} · Page $pageNumber of $totalPages"; page.canvas.drawText(footer,LEFT,HEIGHT-BOTTOM+LineStyle.FOOTER.textSize,paint(LineStyle.FOOTER).apply{color=Color.DKGRAY})
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
        val headingStyles = setOf(LineStyle.SECTION, LineStyle.SUBSECTION, LineStyle.TABLE_HEADER)
        fun requiredHeight(index: Int, line: ReportDrawLine): Float {
            if (line.style !in headingStyles) return line.height

            var height = line.height
            var followingIndex = index + 1
            while (followingIndex < lines.size && lines[followingIndex].style in headingStyles) {
                height += lines[followingIndex].height
                followingIndex++
            }
            if (followingIndex < lines.size) height += lines[followingIndex].height
            return height
        }

        lines.forEachIndexed { index, line ->
            val required = requiredHeight(index, line)
            if (current.isNotEmpty() && used + required > CONTENT_HEIGHT) newPage()
            if (current.isNotEmpty() && used + line.height > CONTENT_HEIGHT) newPage()
            current += line; used += line.height
        }
        newPage()
        return pages.map(::ReportPage)
    }

    internal fun measuredWidth(line: ReportDrawLine): Float = paint(line.style).measureText(line.text)

    private fun buildLines(model: PublicReportModel): List<ReportDrawLine> {
        val raw = mutableListOf(
            RawLine(model.businessName, LineStyle.TITLE),
            RawLine("Service record ${model.visitReference} · Revision ${model.revisionNumber}", LineStyle.META),
            RawLine("Service date: ${model.actualServiceDate}", LineStyle.BODY),
            RawLine("Technician: ${technicianReportName(model.technicianName, model.technicianDesignation)}", LineStyle.BODY),
            RawLine(model.businessContact, LineStyle.BODY),
            RawLine("Customer & site", LineStyle.SECTION),
            RawLine("${model.customerReference.orEmpty()} · ${model.customerName}", LineStyle.BODY),
            RawLine("${model.siteReference.orEmpty()} · ${model.siteName}", LineStyle.BODY),
            RawLine(model.siteAddress.orEmpty(), LineStyle.BODY),
            RawLine("Work completed", LineStyle.SECTION),
        )
        if (model.voided) {
            raw.add(0, RawLine("VOID NOTICE — this service record is void", LineStyle.ALERT))
            raw.add(1, RawLine("Customer explanation: ${model.publicVoidReason.orEmpty()}", LineStyle.ALERT))
        }
        if (model.revisionNumber > 1) raw.add(5, RawLine("Correction revision ${model.revisionNumber} — earlier issued revisions remain historical", LineStyle.ALERT))
        model.publicNote?.let { raw += RawLine("Customer note: $it", LineStyle.BODY) }
        model.dispatch?.let { dispatch ->
            raw += RawLine("Service coordination", LineStyle.SUBSECTION)
            raw += RawLine("Job reference: ${dispatch.managerReference ?: dispatch.dispatchVisitId}", LineStyle.BODY)
            raw += RawLine("Documented by: ${dispatch.documentingTechnicianName}", LineStyle.BODY)
            raw += RawLine("Technician reference: ${dispatch.documentingTechnicianId.take(8)}", LineStyle.META)
        }
        model.lines.forEach { line ->
            raw += RawLine(subjectLabel(line), LineStyle.SUBSECTION)
            if (line.subjectType == com.v16studio.serviceloop.domain.WorkSubjectType.EQUIPMENT && line.equipmentReference != null) {
                raw += RawLine("Equipment identification: ${line.equipmentIdentification.orEmpty()}", LineStyle.BODY)
            }
            raw += RawLine("Service: ${line.planReference?.let { "$it · " }.orEmpty()}${line.serviceName}", LineStyle.BODY)
            line.documentingTechnicianName?.let { raw += RawLine("Documenting Technician: $it", LineStyle.BODY) }
            val outcomeStyle = if (line.outcome == "NOT_PERFORMED" || line.outcome == "PARTLY_PERFORMED") LineStyle.ALERT else LineStyle.BODY
            raw += RawLine("Outcome: ${line.outcome.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}", outcomeStyle)
            line.publicWorkNote?.let { raw += RawLine("Work performed: $it", LineStyle.BODY) }
            line.notPerformedReason?.let { raw += RawLine("Reason work was not performed: $it", LineStyle.ALERT) }
            raw += RawLine(when { line.historyOnly -> "History only — this recurring work has no current due-date effect"; !line.isRecurringPlan -> "One-off work — no recurring due-date effect"; line.fulfilledObligation -> "Obligation advanced: ${line.oldDueDate} → ${line.nextDueDate}"; else -> "Current obligation remains due: ${line.oldDueDate}" }, if (line.historyOnly || !line.fulfilledObligation && line.isRecurringPlan) LineStyle.ALERT else LineStyle.META)
            if (line.parts.isNotEmpty()) {
                raw += RawLine("Parts and materials", LineStyle.SUBSECTION)
                line.parts.forEach { part -> raw += RawLine("${part.description} — ${part.quantity} ${part.unit}", LineStyle.TABLE_ROW) }
            }
            if (line.checklist.isNotEmpty()) {
                raw += RawLine("Inspection checklist", LineStyle.SUBSECTION)
                raw += RawLine("# · Check · Result", LineStyle.TABLE_HEADER)
                line.checklist.forEach { q ->
                    val result = q.value ?: q.disposition.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
                    raw += RawLine("${q.position} · ${q.label} · $result${q.unit?.let { " $it" }.orEmpty()}", LineStyle.TABLE_ROW)
                    q.reason?.takeIf(String::isNotBlank)?.let { raw += RawLine("Finding: $it", LineStyle.ALERT) }
                }
            }
            val outstanding = buildList {
                line.notPerformedReason?.let { add(it) }
                line.checklist.mapNotNull { it.reason?.takeIf(String::isNotBlank) }.forEach(::add)
            }
            if (outstanding.isNotEmpty()) {
                raw += RawLine("Findings & follow-up", LineStyle.SECTION)
                outstanding.distinct().forEach { raw += RawLine("Outstanding: $it", LineStyle.ALERT) }
            }
        }
        return raw.filter { it.text.isNotBlank() }.flatMap(::wrap)
    }

    private fun subjectLabel(line: com.v16studio.serviceloop.domain.PublicWorkLine): String = when {
        line.subjectType == com.v16studio.serviceloop.domain.WorkSubjectType.SITE -> "${line.position.toString().padStart(2, '0')} · Site task"
        line.equipmentReference == null -> line.equipmentDescription?.trim().takeIf { !it.isNullOrBlank() }?.let { "${line.position.toString().padStart(2, '0')} · $it" } ?: line.position.toString().padStart(2, '0')
        else -> "${line.position.toString().padStart(2, '0')} · ${line.equipmentReference.orEmpty()} · ${line.equipmentName.orEmpty()}"
    }

    private fun wrap(raw: RawLine): List<ReportDrawLine> {
        val paint = paint(raw.style); val result = mutableListOf<ReportDrawLine>(); var current = ""
        val availableWidth = CONTENT_WIDTH - if (raw.style == LineStyle.ALERT || raw.style == LineStyle.TABLE_ROW) 8f else 0f
        fun flush() { if (current.isNotEmpty()) { result += ReportDrawLine(current, raw.style); current = "" } }
        fun acceptWord(word: String) {
            var remaining = word
            while (remaining.isNotEmpty()) {
                if (paint.measureText(remaining) <= availableWidth) { current = remaining; return }
                val count = paint.breakText(remaining, true, availableWidth, null).coerceAtLeast(1)
                result += ReportDrawLine(remaining.take(count), raw.style); remaining = remaining.drop(count)
            }
        }
        raw.text.trim().split(Regex("\\s+")).filter(String::isNotEmpty).forEach { word ->
            if (current.isEmpty()) acceptWord(word)
            else {
                val candidate = "$current $word"
                if (paint.measureText(candidate) <= availableWidth) current = candidate else { flush(); acceptWord(word) }
            }
        }
        flush(); return result
    }

    private fun drawHeader(canvas: android.graphics.Canvas, model: PublicReportModel) {
        drawBrandMark(canvas, 24f)
        val headerPaint = paint(LineStyle.META).apply { color = Color.DKGRAY }
        canvas.drawText(model.businessName, LEFT, 56f, headerPaint)
        val reference = "${model.visitReference} · R${model.revisionNumber}"
        canvas.drawText(reference, WIDTH - RIGHT - headerPaint.measureText(reference), 56f, headerPaint)
    }

    private fun drawBrandMark(canvas: android.graphics.Canvas, top: Float) {
        val servicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 11f; typeface = Typeface.create("sans", Typeface.BOLD) }
        val loopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(8, 102, 107); textSize = 11f; typeface = Typeface.create("sans", Typeface.BOLD) }
        val serviceWidth = servicePaint.measureText("Service")
        val loopWidth = loopPaint.measureText("Loop")
        val wordmarkWidth = serviceWidth + loopWidth
        val start = (WIDTH - wordmarkWidth) / 2f
        val lineY = top + 7f
        val lineGap = ServiceLoopBrandSpec.WORDMARK_LINE_GAP_PX
        val leftLineEnd = start - lineGap
        val rightLineStart = start + wordmarkWidth + lineGap
        canvas.drawLine(LEFT, lineY, leftLineEnd, lineY, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = ServiceLoopBrandSpec.LINE_THICKNESS_PX })
        canvas.drawLine(rightLineStart, lineY, WIDTH - RIGHT, lineY, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(8, 102, 107); strokeWidth = ServiceLoopBrandSpec.LINE_THICKNESS_PX })
        canvas.drawText("Service", start, top + 11f, servicePaint)
        canvas.drawText("Loop", start + serviceWidth, top + 11f, loopPaint)
    }

    private fun paint(style: LineStyle) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (style == LineStyle.ALERT) Color.rgb(125, 64, 0) else Color.rgb(31, 42, 48); textSize = style.textSize; typeface = Typeface.create("sans", if (style.bold) Typeface.BOLD else Typeface.NORMAL) }
}
