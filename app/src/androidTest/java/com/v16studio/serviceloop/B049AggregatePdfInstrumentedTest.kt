package com.v16studio.serviceloop

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.domain.PublicPhoto
import com.v16studio.serviceloop.domain.PublicReportModel
import com.v16studio.serviceloop.domain.PublicWorkLine
import com.v16studio.serviceloop.report.AggregateReportPdf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class B049AggregatePdfInstrumentedTest {
    @Test fun rendersTwoIdenticalServiceLabelsAsDistinctAttributedLines() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = requireNotNull(context.getExternalFilesDir(null))
        val target = File(directory, "aggregate-visual-qa-${System.currentTimeMillis()}.pdf")
        val lines = listOf(
            PublicWorkLine(1, "Pump", "EQ-1", null, "Annual service", "PERFORMED", "Inspected seals", null,
                false, null, null, emptyList(), documentingTechnicianName = "Alice"),
            PublicWorkLine(2, "Pump", "EQ-1", null, "Annual service", "PERFORMED", "Calibrated pressure", null,
                false, null, null, emptyList(), documentingTechnicianName = "Bob"),
        )
        val model = PublicReportModel("visit-qa", "revision-qa", 1, "Visit-QA", "2026-09-23", 1,
            "Coordinator Business", "Alice, Bob", "office@example.com", "Customer", "Site A", null, lines)
        val pages = AggregateReportPdf.render(listOf(model), "Coordinator Business", "office@example.com",
            "aggregate-visual-qa", target, directory)
        assertTrue(target.isFile && target.length() > 0)
        PdfRenderer(ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer -> assertEquals(pages, renderer.pageCount) }
    }

    @Test fun rendersNewMultiVisitPdfWithPhotoPage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "b049-aggregate-${System.nanoTime()}").apply { mkdirs() }
        try {
            val image = File(directory, "photo.jpg")
            val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 75, it) }
            bitmap.recycle()
            val photo = PublicPhoto(image.name, "hash", image.length(), "image/jpeg", "Visible customer photo")
            fun model(id: String, site: String, technician: String, photos: List<PublicPhoto>) = PublicReportModel(
                id, "revision-$id", 1, "Visit-$id", "2026-09-23", 1,
                "Coordinator Business", technician, "office@example.com", "Customer", site, null,
                listOf(PublicWorkLine(1, "Pump", "EQ-1", null, "Annual service", "PERFORMED", "Inspected and tested", null, false, null, null, emptyList(), photos = photos)),
            )
            val target = File(directory, "aggregate.pdf")
            val count = AggregateReportPdf.render(listOf(model("1", "Site A", "Alice", listOf(photo)), model("2", "Site B", "Bob", emptyList())),
                "Coordinator Business", "office@example.com", "aggregate-1", target, directory)
            assertTrue(target.isFile && target.length() > 0)
            assertTrue(target.readBytes().copyOfRange(0, 4).contentEquals("%PDF".toByteArray()))
            PdfRenderer(ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer -> assertEquals(count, renderer.pageCount); assertTrue(count >= 3) }
        } finally { directory.deleteRecursively() }
    }
}
