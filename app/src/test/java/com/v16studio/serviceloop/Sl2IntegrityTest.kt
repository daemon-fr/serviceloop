package com.v16studio.serviceloop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.domain.*
import com.v16studio.serviceloop.report.AndroidReportService
import com.v16studio.serviceloop.report.PdfWriteGate
import com.v16studio.serviceloop.report.ReportWriter
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Sl2IntegrityTest {
    private lateinit var db: ServiceLoopDatabase
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }

    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() = db.close()

    @Test fun recurrenceUsesCompletionDateWithCalendarClipping() {
        assertEquals(LocalDate.parse("2026-02-28"), RecurrenceCalculator.nextDate(LocalDate.parse("2026-01-31"), 1, "MONTHS"))
        assertEquals(LocalDate.parse("2025-02-28"), RecurrenceCalculator.nextDate(LocalDate.parse("2024-02-29"), 1, "YEARS"))
        assertEquals(LocalDate.parse("2026-09-19"), RecurrenceCalculator.nextDate(LocalDate.parse("2026-09-05"), 2, "WEEKS"))
        assertEquals(LocalDate.parse("2026-09-08"), RecurrenceCalculator.nextDate(LocalDate.parse("2026-09-05"), 3, "DAYS"))
    }

    @Test fun checklistReviewRequiresCompleteFindingAndSavedEditInvalidatesReview() = runTest {
        seed(withChecklist = true); val repository = repo()
        try { repository.markChecklistReviewed("work-1"); fail("Expected incomplete review") } catch (_: IllegalArgumentException) {}
        repository.saveResponse("work-1", "check-1", ResponseDisposition.ISSUE_FOUND, null, "Public finding")
        repository.markChecklistReviewed("work-1")
        assertTrue(repository.inspection("work-1")!!.checklistReviewed)
        repository.saveResponse("work-1", "check-1", ResponseDisposition.OK, null, null)
        assertFalse(repository.inspection("work-1")!!.checklistReviewed)
    }

    @Test fun fulfilledFinalizationIsAtomicIdempotentAndSnapshotBased() = runTest {
        seed(); val repository = repo(); repository.saveCompletionDraft("work-1", "PERFORMED", true, null, "2026-12-05", true, null)
        val first = repository.finalizeVisit("visit-1") as FinalizeResult.Success
        val again = repo().finalizeVisit("visit-1") as FinalizeResult.Success
        assertEquals(first.recordId, again.recordId); assertEquals(1, db.serviceLoopDao().finalRecordCount()); assertEquals(1, db.serviceLoopDao().finalRevisionCount()); assertEquals(2, db.serviceLoopDao().obligationCount("plan-1"))
        assertEquals("2026-12-05", db.serviceLoopDao().plan("plan-1")!!.currentDueDate); assertNotNull(db.serviceLoopDao().obligation("obligation-1")!!.consumedByRevisionId)
        db.serviceLoopDao().renameCustomer("customer-1", "Renamed customer"); db.serviceLoopDao().renameEquipment("equipment-1", "Renamed equipment")
        val frozen = repository.finalRecord(first.recordId)!!
        assertEquals("Captured customer", frozen.public.customerName); assertEquals("Captured equipment", frozen.public.lines.single().equipmentName)
        assertFalse(frozen.public.toString().contains("PRIVATE_ACCESS_SENTINEL")); assertFalse(frozen.public.toString().contains("PRIVATE_INTERNAL_SENTINEL")); assertTrue(frozen.privateNotes.contains("PRIVATE_INTERNAL_SENTINEL"))
    }

    @Test fun staleCapturedObligationBlocksWithoutPartialRecord() = runTest {
        seed(); val dao = db.serviceLoopDao(); repo().saveCompletionDraft("work-1", "PERFORMED", true, null, "2026-12-05", true, null)
        dao.insertObligations(listOf(ServiceObligationEntity("obligation-2", "plan-1", 2, "2026-10-01", 2))); dao.setCurrentObligationForTest("plan-1", "obligation-2")
        val result = repo().finalizeVisit("visit-1") as FinalizeResult.Blocked
        assertTrue(result.message.contains("obligation changed")); assertEquals(0, dao.finalRecordCount()); assertNull(dao.obligation("obligation-1")!!.consumedAtEpochMillis); assertNull(dao.obligation("obligation-2")!!.consumedAtEpochMillis); assertEquals("WORKING", dao.visit("visit-1")!!.state)
    }

    @Test fun controlledFinalizationFailureRollsBackEverything() = runTest {
        seed(); val dao = db.serviceLoopDao(); repo().saveCompletionDraft("work-1", "PERFORMED", true, null, "2026-12-05", true, null)
        val failing = RoomServiceLoopRepository(db, time, finalizationWriteGate = FinalizationWriteGate { error("controlled") })
        try { failing.finalizeVisit("visit-1"); fail("Expected failure") } catch (_: IllegalStateException) {}
        assertEquals(0, dao.finalRecordCount()); assertEquals(1, dao.obligationCount("plan-1")); assertEquals("obligation-1", dao.plan("plan-1")!!.currentObligationId); assertEquals("2026-09-01", dao.plan("plan-1")!!.currentDueDate); assertEquals("WORKING", dao.visit("visit-1")!!.state)
    }

    @Test fun partialNotPerformedUnfulfilledAndOneOffRemainHistoryOnly() = runTest {
        seed(); val dao = db.serviceLoopDao(); val repository = repo()
        repository.saveCompletionDraft("work-1", "PERFORMED", false, null, null, null, null)
        insertLine("work-partial", "PARTLY_PERFORMED", "Partial public work")
        insertLine("work-not", "NOT_PERFORMED", "", reason = "Could not access equipment")
        insertLine("work-one-off", "PERFORMED", "One-off public work", oneOff = true)
        val recordId = (repository.finalizeVisit("visit-1") as FinalizeResult.Success).recordId
        assertEquals("2026-09-01", dao.plan("plan-1")!!.currentDueDate); assertEquals(1, dao.obligationCount("plan-1")); assertNull(dao.obligation("obligation-1")!!.consumedAtEpochMillis)
        val lines = repository.finalRecord(recordId)!!.public.lines
        assertEquals(4, lines.size); assertTrue(lines.none { it.fulfilledObligation }); assertTrue(lines.any { it.outcome == "PARTLY_PERFORMED" }); assertTrue(lines.any { it.outcome == "NOT_PERFORMED" })
    }

    @Test fun fulfillingOneOfTwoPlansDoesNotAdvanceTheOther() = runTest {
        seed(); val dao = db.serviceLoopDao(); val repository = repo()
        dao.insertPlans(listOf(ServicePlanEntity("plan-2", "equipment-1", "P-2", "Other service", 6, "MONTHS", "2026-09-02", "ACTIVE", "obligation-2")))
        dao.insertObligations(listOf(ServiceObligationEntity("obligation-2", "plan-2", 1, "2026-09-02", 1)))
        dao.insertWorkItems(listOf(WorkItemEntity("work-2", "visit-1", "equipment-1", "plan-2", "obligation-2", null, "Captured equipment", "EQ-1", "Other service", "P-2", "2026-09-02", 6, "MONTHS", false, "PERFORMED", false)))
        dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity("work-2", "Other work"))); dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity("work-2", "")))
        repository.saveCompletionDraft("work-1", "PERFORMED", true, null, "2026-12-05", true, null)
        repository.finalizeVisit("visit-1")
        assertEquals("2026-12-05", dao.plan("plan-1")!!.currentDueDate); assertEquals("2026-09-02", dao.plan("plan-2")!!.currentDueDate); assertNull(dao.obligation("obligation-2")!!.consumedAtEpochMillis)
    }

    @Test fun pdfFailureAndRetryNeverRepeatBusinessEffects() = runTest {
        seed(); val repository = repo(); repository.saveCompletionDraft("work-1", "PERFORMED", true, null, "2026-12-05", true, null); val record = (repository.finalizeVisit("visit-1") as FinalizeResult.Success).recordId
        val due = db.serviceLoopDao().plan("plan-1")!!.currentDueDate
        try { AndroidReportService(context, db, repository, PdfWriteGate { error("controlled PDF failure") }).generate(record); fail("Expected PDF failure") } catch (_: IllegalStateException) {}
        assertEquals(due, db.serviceLoopDao().plan("plan-1")!!.currentDueDate); assertEquals(2, db.serviceLoopDao().obligationCount("plan-1"))
        val fakeWriter = ReportWriter { _, _, _, file -> FileOutputStream(file).use { it.write("%PDF-1.4\n%%EOF".toByteArray()) }; 1 }
        val ready = AndroidReportService(context, db, repository, writer = fakeWriter).generate(record)
        assertEquals("READY", ready.status); assertTrue(ready.byteSize!! > 0); assertEquals(64, ready.sha256!!.length); assertTrue(ready.pageCount!! >= 1)
        assertEquals(due, db.serviceLoopDao().plan("plan-1")!!.currentDueDate); assertEquals(2, db.serviceLoopDao().obligationCount("plan-1"))
    }

    private fun repo() = RoomServiceLoopRepository(db, time)

    private suspend fun insertLine(id: String, outcome: String, work: String, reason: String? = null, oneOff: Boolean = false) {
        val dao = db.serviceLoopDao()
        dao.insertWorkItems(listOf(WorkItemEntity(id, "visit-1", "equipment-1", if (oneOff) null else "plan-1", if (oneOff) null else "obligation-1", null, "Captured equipment", "EQ-1", if (oneOff) "One-off service" else "Captured service", if (oneOff) null else "P-1", if (oneOff) null else "2026-09-01", if (oneOff) null else 3, if (oneOff) null else "MONTHS", false, outcome, false, notPerformedReason = reason)))
        dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity(id, work))); dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity(id, "")))
    }

    private suspend fun seed(withChecklist: Boolean = false) {
        val dao = db.serviceLoopDao()
        dao.insertCustomers(listOf(CustomerEntity("customer-1", "CU-1", "Current customer")))
        dao.insertSites(listOf(SiteEntity("site-1", "customer-1", "ST-1", "Current site", "Current address", "PRIVATE_ACCESS_SENTINEL")))
        dao.insertEquipment(listOf(EquipmentEntity("equipment-1", "site-1", "EQ-1", "T-1", "Current equipment", "Maker", "Model", "Serial", "Private machine")))
        dao.insertPlans(listOf(ServicePlanEntity("plan-1", "equipment-1", "P-1", "Inspection", 3, "MONTHS", "2026-09-01", "ACTIVE", "obligation-1")))
        dao.insertObligations(listOf(ServiceObligationEntity("obligation-1", "plan-1", 1, "2026-09-01", 1)))
        if (withChecklist) { dao.insertTemplateSnapshots(listOf(TemplateSnapshotEntity("template-1", null, "Template", 1, 1))); dao.insertChecklistItems(listOf(ChecklistItemSnapshotEntity("check-1", "template-1", 1, "Guard", "STATUS", null, true, "Private guidance"))) }
        dao.insertVisits(listOf(WorkingVisitEntity("visit-1", "V-1", "customer-1", "site-1", "2026-09-05", "Captured customer", "Captured site", "Captured address", "WORKING", 1)))
        dao.insertWorkItems(listOf(WorkItemEntity("work-1", "visit-1", "equipment-1", "plan-1", "obligation-1", if (withChecklist) "template-1" else null, "Captured equipment", "EQ-1", "Captured service", "P-1", "2026-09-01", 3, "MONTHS", !withChecklist, "PERFORMED", false)))
        dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity("work-1", "Public work completed"))); dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity("work-1", "PRIVATE_INTERNAL_SENTINEL")))
        dao.upsertBusinessProfile(BusinessProfileEntity(businessName = "Service Business", technicianName = "Technician", phone = null, email = null, postalAddress = null, zoneId = "Europe/Bucharest", modifiedAtEpochMillis = 1))
    }
}
