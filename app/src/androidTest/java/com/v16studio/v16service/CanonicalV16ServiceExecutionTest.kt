package com.v16studio.v16service

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.v16service.data.RoomV16ServiceRepository
import com.v16studio.v16service.data.V16ServiceDatabase
import com.v16studio.v16service.domain.BusinessProfile
import com.v16studio.v16service.domain.ClockBusinessTime
import com.v16studio.v16service.domain.CustomerInput
import com.v16studio.v16service.domain.EquipmentInput
import com.v16studio.v16service.domain.FinalizeResult
import com.v16studio.v16service.domain.PlanInput
import com.v16studio.v16service.domain.SiteInput
import com.v16studio.v16service.report.AndroidReportService
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalV16ServiceExecutionTest {
    @Test fun executeRepresentativeLoopExactlyOnceAndGenerateRealReport() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, V16ServiceDatabase::class.java).build()
        try {
            val repository = RoomV16ServiceRepository(database, ClockBusinessTime(zoneId = ZoneId.of("Europe/Bucharest")), attachmentRoot = context.filesDir)
            repository.saveBusinessProfile(BusinessProfile("Exact-once Test Business", "Test Technician", zoneId = "Europe/Bucharest"))
            val customerId = repository.createCustomer(CustomerInput("Exact-once Customer"))
            val siteId = repository.createSite(customerId, SiteInput("Exact-once Site", "1 Test Street"))
            val equipmentId = repository.createEquipment(siteId, EquipmentInput("Exact-once Equipment"))
            val planId = repository.createPlan(equipmentId, PlanInput("Quarterly service", 3, "MONTHS", "2026-09-01"))
            val planBeforeVisit = database.v16ServiceDao().plan(planId)!!
            val initialObligationId = requireNotNull(planBeforeVisit.currentObligationId)
            val obligationBeforeVisit = database.v16ServiceDao().obligation(initialObligationId)!!
            assertEquals("2026-09-01", planBeforeVisit.currentDueDate)
            assertEquals(initialObligationId, obligationBeforeVisit.id)
            assertEquals("2026-09-01", obligationBeforeVisit.dueDate)
            assertNull(obligationBeforeVisit.consumedAtEpochMillis)

            val visitId = repository.createVisit(listOf(planId), "WORKING", "2026-09-05")
            val workId = database.v16ServiceDao().firstWorkItemId(visitId)!!
            assertEquals("WORKING", database.v16ServiceDao().visit(visitId)?.state)
            assertEquals(1, database.v16ServiceDao().claimCountForVisit(visitId))
            assertEquals(planBeforeVisit, database.v16ServiceDao().plan(planId))
            repository.savePublicWork(workId, "Inspected the equipment and documented the service work.")
            repository.saveCompletionDraft(workId, "PERFORMED", null, null, null, null, null)
            val preparedWork = database.v16ServiceDao().workItem(workId)!!
            assertEquals("PERFORMED", preparedWork.outcome)
            assertEquals(true, preparedWork.fulfillsCurrentObligation)
            assertEquals("2026-12-05", preparedWork.confirmedNextDueDate)
            assertEquals(true, preparedWork.nextDueDateCalculated)
            assertEquals("2026-09-01", database.v16ServiceDao().plan(planId)?.currentDueDate)
            assertNull(database.v16ServiceDao().obligation(initialObligationId)?.consumedAtEpochMillis)

            val initialFinalRecordCount = database.v16ServiceDao().finalRecordCount()
            val initialFinalRevisionCount = database.v16ServiceDao().finalRevisionCount()
            val firstResult = repository.finalizeVisit(visitId)
            assertTrue(firstResult is FinalizeResult.Success)
            val recordId = (firstResult as FinalizeResult.Success).recordId
            val recordAfterFirst = database.v16ServiceDao().finalRecordForVisit(visitId)!!
            val firstPlan = database.v16ServiceDao().plan(planId)!!
            val firstCurrentObligationId = requireNotNull(firstPlan.currentObligationId)
            val firstCurrentObligation = database.v16ServiceDao().obligation(firstCurrentObligationId)!!
            val consumedObligation = database.v16ServiceDao().obligation(initialObligationId)!!
            assertEquals("COMPLETED", database.v16ServiceDao().visit(visitId)?.state)
            assertEquals("2026-12-05", firstPlan.currentDueDate)
            assertNotEquals(initialObligationId, firstCurrentObligationId)
            assertEquals(2, database.v16ServiceDao().obligationCount(planId))
            assertNotNull(consumedObligation.consumedAtEpochMillis)
            assertEquals(recordAfterFirst.currentRevisionId, consumedObligation.consumedByRevisionId)
            assertEquals(2L, firstCurrentObligation.sequence)
            assertEquals("2026-12-05", firstCurrentObligation.dueDate)
            assertNull(firstCurrentObligation.consumedAtEpochMillis)
            assertEquals(initialFinalRecordCount + 1, database.v16ServiceDao().finalRecordCount())
            assertEquals(initialFinalRevisionCount + 1, database.v16ServiceDao().finalRevisionCount())
            assertEquals(1, database.v16ServiceDao().finalWorkItems(recordAfterFirst.currentRevisionId).size)
            assertEquals(0, database.v16ServiceDao().claimCountForVisit(visitId))

            val secondResult = repository.finalizeVisit(visitId)
            assertTrue(secondResult is FinalizeResult.Success)
            assertEquals(recordId, (secondResult as FinalizeResult.Success).recordId)
            val recordAfterSecond = database.v16ServiceDao().finalRecordForVisit(visitId)!!
            val secondPlan = database.v16ServiceDao().plan(planId)!!
            val secondCurrentObligation = database.v16ServiceDao().obligation(requireNotNull(secondPlan.currentObligationId))!!
            assertEquals(recordAfterFirst, recordAfterSecond)
            assertEquals(firstPlan, secondPlan)
            assertEquals(consumedObligation, database.v16ServiceDao().obligation(initialObligationId))
            assertEquals(firstCurrentObligation, secondCurrentObligation)
            assertEquals(1, database.v16ServiceDao().finalRecordCount() - initialFinalRecordCount)
            assertEquals(1, database.v16ServiceDao().finalRevisionCount() - initialFinalRevisionCount)
            assertEquals(1, database.v16ServiceDao().finalRevisions(recordId).size)
            assertEquals(1, database.v16ServiceDao().finalWorkItems(recordAfterSecond.currentRevisionId).size)
            assertEquals(2, database.v16ServiceDao().obligationCount(planId))
            assertEquals(0, database.v16ServiceDao().claimCountForVisit(visitId))

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
