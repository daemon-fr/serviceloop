package com.v16studio.serviceloop

import android.graphics.pdf.PdfRenderer
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
    @Test fun canonicalMigratedFixtureStillContainsWorkingVisitAnswersAndIdentity() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = ServiceLoopDatabase.open(context)
            try {
            val dao = database.serviceLoopDao()
            assertEquals("Harbor Fitness and Rehabilitation Cooperative", dao.customer(FixtureIds.CUSTOMER)?.name)
            assertEquals("WORKING", dao.visit(FixtureIds.VISIT_1)?.state)
            val savedFinding = dao.responses(FixtureIds.WORK_INSPECTION).first { it.checklistItemSnapshotId == "check-belt" }
            assertEquals("ISSUE_FOUND", savedFinding.disposition)
            assertFalse(savedFinding.reason.isNullOrBlank())
            assertNotNull(dao.businessProfile())
            assertNull(dao.plan("plan-001")?.lastCountedCompletionDate)
            } finally { database.close() }
        }
    }

    @Test fun nativeRendererProducesAndReopensMultipagePdf() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "sl2-renderer-test.pdf")
        val longFinding = (1..80).joinToString(" ") { "Detailed public finding sentence $it." }
        val line = PublicWorkLine(1, "Captured equipment", "EQ-1", "Maker · Model · Serial", "Condition inspection", "PERFORMED", longFinding, null, true, "2026-09-01", "2026-12-05", (1..30).map { PublicChecklistItem(it, "Checklist item $it", "STATUS", null, true, "ISSUE_FOUND", null, longFinding) })
        val model = PublicReportModel("record", "revision", 1, "V-1", "2026-09-05", 1, "Service Business", "Technician", "Contact", "Captured customer", "Captured site", "Captured address", listOf(line, line.copy(position = 2)))
        val pages = FixedServiceRecordPdf.render(model, "rendition-1", file)
        assertTrue(file.length() > 0); assertTrue(pages > 1)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { renderer -> assertEquals(pages, renderer.pageCount); renderer.openPage(0).close() } }
        file.delete()
    }
}
