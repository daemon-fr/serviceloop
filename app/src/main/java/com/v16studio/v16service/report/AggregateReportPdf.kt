package com.v16studio.v16service.report

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.v16studio.v16service.domain.PublicPhoto
import com.v16studio.v16service.domain.PublicReportModel
import java.io.File
import java.io.FileOutputStream

/** A new document rendered from frozen final models, never from existing PDF pages. */
object AggregateReportPdf {
    private sealed interface Page {
        data class Lines(val source: PublicReportModel, val value: FixedServiceRecordPdf.ReportPage) : Page
        data class Photo(val source: PublicReportModel, val label: String, val value: PublicPhoto) : Page
    }

    fun render(models: List<PublicReportModel>, businessName: String, businessContact: String, reportId: String, target: File, filesRoot: File): Int {
        require(models.isNotEmpty()) { "Choose at least one final service result" }
        val pages = models.flatMap { model ->
            FixedServiceRecordPdf.layout(model).map { Page.Lines(model, it) } + model.lines.flatMap { line ->
                line.photos.map { Page.Photo(model, "${line.serviceName}${it.caption?.let { caption -> " · $caption" }.orEmpty()}", it) }
            }
        }
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, item ->
                val number = index + 1
                val page = document.startPage(PdfDocument.PageInfo.Builder(FixedServiceRecordPdf.WIDTH, FixedServiceRecordPdf.HEIGHT, number).create())
                page.canvas.drawColor(Color.WHITE)
                val titlePaint = paint(12f, true)
                val bodyPaint = paint(9.5f)
                page.canvas.drawText(businessName, FixedServiceRecordPdf.LEFT, 35f, titlePaint)
                page.canvas.drawText("Aggregate customer service report", FixedServiceRecordPdf.LEFT, 50f, bodyPaint)
                page.canvas.drawText(businessContact.take(92), FixedServiceRecordPdf.LEFT, 63f, paint(8f))
                page.canvas.drawLine(FixedServiceRecordPdf.LEFT, 70f, FixedServiceRecordPdf.WIDTH - FixedServiceRecordPdf.RIGHT, 70f, paint(1f))
                when (item) {
                    is Page.Lines -> {
                        var y = FixedServiceRecordPdf.TOP
                        item.value.lines.forEach { line ->
                            if (line.style == FixedServiceRecordPdf.LineStyle.TABLE_HEADER) page.canvas.drawRect(FixedServiceRecordPdf.LEFT, y, FixedServiceRecordPdf.WIDTH - FixedServiceRecordPdf.RIGHT, y + line.height, Paint().apply { color = Color.rgb(231, 244, 244) })
                            page.canvas.drawText(line.text, FixedServiceRecordPdf.LEFT, y + line.style.textSize + 3f, paint(line.style.textSize, line.style.bold))
                            y += line.height
                        }
                    }
                    is Page.Photo -> {
                        page.canvas.drawText("${item.source.visitReference} · ${item.source.actualServiceDate} · ${item.source.siteName}", FixedServiceRecordPdf.LEFT, FixedServiceRecordPdf.TOP + 15f, paint(10f, true))
                        page.canvas.drawText(item.label.take(100), FixedServiceRecordPdf.LEFT, FixedServiceRecordPdf.TOP + 33f, bodyPaint)
                        val file = File(filesRoot, item.value.relativePath)
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: error("Aggregate source photo is missing")
                        try {
                            val top = FixedServiceRecordPdf.TOP + 48f
                            val available = FixedServiceRecordPdf.HEIGHT - FixedServiceRecordPdf.BOTTOM - top - 12f
                            val scale = minOf(FixedServiceRecordPdf.CONTENT_WIDTH / bitmap.width, available / bitmap.height)
                            val width = bitmap.width * scale
                            val height = bitmap.height * scale
                            val left = FixedServiceRecordPdf.LEFT + (FixedServiceRecordPdf.CONTENT_WIDTH - width) / 2f
                            page.canvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                        } finally { bitmap.recycle() }
                    }
                }
                val footer = "V16 Service · ${item.let { when (it) { is Page.Lines -> it.source.visitReference; is Page.Photo -> it.source.visitReference } }} · ${reportId.take(8)} · Page $number of ${pages.size}"
                page.canvas.drawLine(FixedServiceRecordPdf.LEFT, FixedServiceRecordPdf.HEIGHT - FixedServiceRecordPdf.BOTTOM - 9f, FixedServiceRecordPdf.WIDTH - FixedServiceRecordPdf.RIGHT, FixedServiceRecordPdf.HEIGHT - FixedServiceRecordPdf.BOTTOM - 9f, paint(1f))
                page.canvas.drawText(footer, FixedServiceRecordPdf.LEFT, FixedServiceRecordPdf.HEIGHT - FixedServiceRecordPdf.BOTTOM + 9f, paint(8f))
                document.finishPage(page)
            }
            FileOutputStream(target).use(document::writeTo)
        } finally { document.close() }
        return pages.size
    }

    private fun paint(size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(31, 42, 48)
        textSize = size
        typeface = Typeface.create("sans", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
}
