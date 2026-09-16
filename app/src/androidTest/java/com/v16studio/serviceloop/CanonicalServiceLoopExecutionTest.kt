package com.v16studio.serviceloop

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.domain.BusinessProfile
import com.v16studio.serviceloop.domain.ClockBusinessTime
import com.v16studio.serviceloop.domain.CustomerInput
import com.v16studio.serviceloop.domain.EquipmentInput
import com.v16studio.serviceloop.domain.FinalizeResult
import com.v16studio.serviceloop.domain.PlanInput
import com.v16studio.serviceloop.domain.SiteInput
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
        val database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).build()
        try {
            val repository = RoomServiceLoopRepository(database, ClockBusinessTime(zoneId = ZoneId.of("Europe/Bucharest")), attachmentRoot = context.filesDir)
            repository.saveBusinessProfile(BusinessProfile("Exact-once Test Business", "Test Technician", zoneId = "Europe/Bucharest"))
            val customerId = repository.createCustomer(CustomerInput("Exact-once Customer"))
            val siteId = repository.createSite(customerId, SiteInput("Exact-once Site", "1 Test Street"))
            val equipmentId = repository.createEquipment(siteId, EquipmentInput("Exact-once Equipment"))
            val planId = repository.createPlan(equipmentId, PlanInput("Quarterly service", 3, "MONTHS", "2026-09-01"))
            val planBeforeVisit = database.serviceLoopDao().plan(planId)!!
            val initialObligationId = requireNotNull(planBeforeVisit.currentObligationId)
            val obligationBeforeVisit = database.serviceLoopDao().obligation(initialObligationId)!!
            assertEquals("2026-09-01", planBeforeVisit.currentDueDate)
            assertEquals(initialObligationId, obligationBeforeVisit.id)
            assertEquals("2026-09-01", obligationBeforeVisit.dueDate)
            assertNull(obligationBeforeVisit.consumedAtEpochMillis)

            val visitId = repository.createVisit(listOf(planId), "WORKING", "2026-09-05")
            val workId = database.serviceLoopDao().firstWorkItemId(visitId)!!
            assertEquals("WORKING", database.serviceLoopDao().visit(visitId)?.state)
            assertEquals(1, database.serviceLoopDao().claimCountForVisit(visitId))
            assertEquals(planBeforeVisit, database.serviceLoopDao().plan(planId))
            repository.savePublicWork(workId, "Inspected the equipment and documented the service work.")
            repository.saveCompletionDraft(workId, "PERFORMED", null, null, null, null, null)
            val preparedWork = database.serviceLoopDao().workItem(workId)!!
            assertEquals("PERFORMED", preparedWork.outcome)
            assertEquals(true, preparedWork.fulfillsCurrentObligation)
            assertEquals("2026-12-05", preparedWork.confirmedNextDueDate)
            assertEquals(true, preparedWork.nextDueDateCalculated)
            assertEquals("2026-09-01", database.serviceLoopDao().plan(planId)?.currentDueDate)
            assertNull(database.serviceLoopDao().obligation(initialObligationId)?.consumedAtEpochMillis)

            val initialFinalRecordCount = database.serviceLoopDao().finalRecordCount()
            val initialFinalRevisionCount = database.serviceLoopDao().finalRevisionCount()
            val firstResult = repository.finalizeVisit(visitId)
            assertTrue(firstResult is FinalizeResult.Success)
            val recordId = (firstResult as FinalizeResult.Success).recordId
            val recordAfterFirst = database.serviceLoopDao().finalRecordForVisit(visitId)!!
            val firstPlan = database.serviceLoopDao().plan(planId)!!
            val firstCurrentObligationId = requireNotNull(firstPlan.currentObligationId)
            val firstCurrentObligation = database.serviceLoopDao().obligation(firstCurrentObligationId)!!
            val consumedObligation = database.serviceLoopDao().obligation(initialObligationId)!!
            assertEquals("COMPLETED", database.serviceLoopDao().visit(visitId)?.state)
            assertEquals("2026-12-05", firstPlan.currentDueDate)
            assertNotEquals(initialObligationId, firstCurrentObligationId)
            assertEquals(2, database.serviceLoopDao().obligationCount(planId))
            assertNotNull(consumedObligation.consumedAtEpochMillis)
            assertEquals(recordAfterFirst.currentRevisionId, consumedObligation.consumedByRevisionId)
            assertEquals(2L, firstCurrentObligation.sequence)
            assertEquals("2026-12-05", firstCurrentObligation.dueDate)
            assertNull(firstCurrentObligation.consumedAtEpochMillis)
            assertEquals(initialFinalRecordCount + 1, database.serviceLoopDao().finalRecordCount())
            assertEquals(initialFinalRevisionCount + 1, database.serviceLoopDao().finalRevisionCount())
            assertEquals(1, database.serviceLoopDao().finalWorkItems(recordAfterFirst.currentRevisionId).size)
            assertEquals(0, database.serviceLoopDao().claimCountForVisit(visitId))

            val secondResult = repository.finalizeVisit(visitId)
            assertTrue(secondResult is FinalizeResult.Success)
            assertEquals(recordId, (secondResult as FinalizeResult.Success).recordId)
            val recordAfterSecond = database.serviceLoopDao().finalRecordForVisit(visitId)!!
            val secondPlan = database.serviceLoopDao().plan(planId)!!
            val secondCurrentObligation = database.serviceLoopDao().obligation(requireNotNull(secondPlan.currentObligationId))!!
            assertEquals(recordAfterFirst, recordAfterSecond)
            assertEquals(firstPlan, secondPlan)
            assertEquals(consumedObligation, database.serviceLoopDao().obligation(initialObligationId))
            assertEquals(firstCurrentObligation, secondCurrentObligation)
            assertEquals(1, database.serviceLoopDao().finalRecordCount() - initialFinalRecordCount)
            assertEquals(1, database.serviceLoopDao().finalRevisionCount() - initialFinalRevisionCount)
            assertEquals(1, database.serviceLoopDao().finalRevisions(recordId).size)
            assertEquals(1, database.serviceLoopDao().finalWorkItems(recordAfterSecond.currentRevisionId).size)
            assertEquals(2, database.serviceLoopDao().obligationCount(planId))
            assertEquals(0, database.serviceLoopDao().claimCountForVisit(visitId))

            val reportService = AndroidReportService(context, database, repository)
            val report = reportService.generate(recordId)
            assertEquals("READY", report.status)
            assertTrue(report.byteSize!! > 0)
            assertEquals(64, report.sha256!!.length)
            assertTrue(report.pageCount!! >= 1)
            assertTrue(reportService.file(report.relativePath).isFile)
            reportService.file(report.relativePath).delete()
            Unit
        } finally { database.close() }
    }
}
