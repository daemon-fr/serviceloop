package com.v16studio.serviceloop

import android.graphics.pdf.PdfRenderer
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.domain.PublicChecklistItem
import com.v16studio.serviceloop.domain.PublicReportModel
import com.v16studio.serviceloop.domain.PublicWorkLine
import com.v16studio.serviceloop.report.FixedServiceRecordPdf
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Sl2RuntimeIntegrityTest {
    @Test fun canonicalMigratedFixturePreservesFinalVisitAnswersIdentityAndRecurrence() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = ServiceLoopDatabase.open(context)
            try {
            val dao = database.serviceLoopDao()
            assertEquals("Harbor Fitness and Rehabilitation Cooperative", dao.customer(FixtureIds.CUSTOMER)?.name)
            assertEquals("FINALIZED", dao.visit(FixtureIds.VISIT_1)?.state)
            val savedFinding = dao.responses(FixtureIds.WORK_INSPECTION).first { it.checklistItemSnapshotId == "check-belt" }
            assertEquals("ISSUE_FOUND", savedFinding.disposition)
            assertFalse(savedFinding.reason.isNullOrBlank())
            assertNotNull(dao.businessProfile())
            val plan = dao.plan("plan-001")
            assertEquals("2026-12-05", plan?.currentDueDate)
            assertEquals("2026-09-05", plan?.lastCountedCompletionDate)
            val final = dao.finalRecordForVisit(FixtureIds.VISIT_1)
            assertNotNull(final)
            assertEquals("a6590fcd-7398-308a-8c72-64fa7c23db0e", final!!.id)
            assertEquals(final!!.currentRevisionId, plan?.lastCountedRevisionId)
            assertEquals(2, dao.obligationCount("plan-001"))
            val consumed = dao.obligation("obl-001")
            assertNotNull(consumed?.consumedAtEpochMillis)
            assertEquals(final.currentRevisionId, consumed?.consumedByRevisionId)
            val current = dao.obligation(plan!!.currentObligationId!!)
            assertEquals(2L, current?.sequence)
            assertEquals("2026-12-05", current?.dueDate)
            assertNull(current?.consumedAtEpochMillis)
            val rendition = dao.reportRendition(final.currentRevisionId)
            assertEquals("ec1c0f1c-a227-37ff-b12d-82e0863a8810", rendition?.id)
            assertEquals(1, rendition?.versionNumber)
            assertEquals("READY", rendition?.status)
            assertEquals(65_369L, rendition?.byteSize)
            assertEquals("2cc923ef53f53fb6d0c8e314e7b854009708c5107af4f7ad4bee58f01c71b1b8", rendition?.sha256)
            assertEquals(1, rendition?.pageCount)
            assertEquals("reports/a6590fcd-7398-308a-8c72-64fa7c23db0e/ec1c0f1c-a227-37ff-b12d-82e0863a8810.pdf", rendition?.relativePath)
            } finally { database.close() }
        }
    }

    @Test fun nativeRendererProducesAndReopensMultipagePdf() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "sl2-renderer-test.pdf")
        val longFinding = (1..80).joinToString(" ") { "Detailed public finding sentence $it." }
        val token = "SERIAL".repeat(24)
        val lines = (1..24).map { position -> PublicWorkLine(position, "Captured equipment $position", "EQ-$position", token, "Condition inspection", "PERFORMED", longFinding, null, true, "2026-09-01", "2026-12-05", (1..4).map { PublicChecklistItem(it, "Checklist item $it", "STATUS", null, true, "ISSUE_FOUND", null, longFinding) }) }
        val model = PublicReportModel("record", "revision", 1, "V-1", "2026-09-05", 1, "Service Business", "Technician", "Contact", "Captured customer", "Captured site", "Captured address", lines)
        val pages = FixedServiceRecordPdf.render(model, "rendition-1", 1, 1_788_708_000_000L, file)
        assertTrue(file.length() > 0); assertTrue(pages > 1)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { renderer ->
            assertEquals(pages, renderer.pageCount)
            renderer.openPage(0).close()
            renderer.openPage(renderer.pageCount - 1).use { page -> val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); assertTrue(bitmap.width > 0); bitmap.recycle() }
        } }
        file.delete()
    }
}
