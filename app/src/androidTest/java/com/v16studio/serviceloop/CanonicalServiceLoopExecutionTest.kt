package com.v16studio.serviceloop

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.domain.ClockBusinessTime
import com.v16studio.serviceloop.domain.FinalizeResult
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.report.AndroidReportService
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalServiceLoopExecutionTest {
    @Test fun executeRepresentativeLoopExactlyOnceAndGenerateRealReport() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = ServiceLoopDatabase.open(context)
        try {
            val repository = RoomServiceLoopRepository(database, ClockBusinessTime(zoneId = ZoneId.of("Europe/Bucharest")))
            val existing = database.serviceLoopDao().finalRecordForVisit(FixtureIds.VISIT_1)
            val recordId = if (existing != null) existing.id else {
                repository.savePublicWork(FixtureIds.WORK_INSPECTION, "Inspected recorded items; belt-edge wear documented for customer review.")
                repository.saveResponse(FixtureIds.WORK_INSPECTION, "check-observation", ResponseDisposition.VALUE, "Completion review performed on device.", null)
                repository.markChecklistReviewed(FixtureIds.WORK_INSPECTION)
                repository.saveCompletionDraft(FixtureIds.WORK_INSPECTION, "PERFORMED", true, null, "2026-12-05", true, null)
                repository.saveCompletionDraft("work-002", "PARTLY_PERFORMED", false, null, null, null, null)
                repository.saveCompletionDraft("work-003", "PERFORMED", false, null, null, null, null)
                repository.saveCompletionDraft("work-004", "NOT_PERFORMED", false, "Service area unavailable during this visit.", null, null, null)
                (repository.finalizeVisit(FixtureIds.VISIT_1) as FinalizeResult.Success).recordId
            }
            val resultAgain = repository.finalizeVisit(FixtureIds.VISIT_1) as FinalizeResult.Success
            assertEquals(recordId, resultAgain.recordId)
            assertEquals("COMPLETED", database.serviceLoopDao().visit(FixtureIds.VISIT_1)?.state)
            assertEquals("2026-12-05", database.serviceLoopDao().plan("plan-001")?.currentDueDate)
            assertEquals("2026-09-01", database.serviceLoopDao().plan("plan-003")?.currentDueDate)
            val report = AndroidReportService(context, database, repository).generate(recordId)
            assertEquals("READY", report.status); assertTrue(report.byteSize!! > 0); assertEquals(64, report.sha256!!.length); assertTrue(report.pageCount!! >= 1)
        } finally { database.close() }
    }
}
