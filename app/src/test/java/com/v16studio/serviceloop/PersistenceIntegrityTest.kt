package com.v16studio.serviceloop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.AttachmentEntity
import com.v16studio.serviceloop.data.ChecklistItemSnapshotEntity
import com.v16studio.serviceloop.data.CustomerEntity
import com.v16studio.serviceloop.data.DraftWriteGate
import com.v16studio.serviceloop.data.EquipmentEntity
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.data.ServiceObligationEntity
import com.v16studio.serviceloop.data.ServicePlanEntity
import com.v16studio.serviceloop.data.SiteEntity
import com.v16studio.serviceloop.data.TemplateSnapshotEntity
import com.v16studio.serviceloop.data.WorkItemEntity
import com.v16studio.serviceloop.data.WorkItemPrivateDraftEntity
import com.v16studio.serviceloop.data.WorkItemPublicDraftEntity
import com.v16studio.serviceloop.data.WorkingResponseEntity
import com.v16studio.serviceloop.data.WorkingVisitEntity
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.domain.FulfillmentEligibility
import com.v16studio.serviceloop.domain.ResponseDisposition
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PersistenceIntegrityTest {
    private lateinit var database: ServiceLoopDatabase
    private val time = object : BusinessTime {
        override val zoneId: ZoneId = ZoneId.of("Europe/Bucharest")
        override fun instant(): Instant = Instant.parse("2026-09-05T07:15:00Z")
    }

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test fun customerSiteEquipmentAndPlansPersistWithIndependentStableIdentity() = runTest {
        seedFoundation()
        val rows = database.serviceLoopDao().equipmentPlans("equipment-1")
        assertEquals(1, rows.size)
        assertEquals("customer-1", database.serviceLoopDao().site("site-1")?.customerId)
        assertEquals("site-1", database.serviceLoopDao().equipment("equipment-1")?.siteId)
        assertEquals("equipment-1", database.serviceLoopDao().plan("plan-1")?.equipmentId)

        database.serviceLoopDao().renameCustomer("customer-1", "Renamed customer")
        database.serviceLoopDao().renameEquipment("equipment-1", "Renamed machine")
        assertEquals("customer-1", database.serviceLoopDao().customer("customer-1")?.id)
        assertEquals("equipment-1", database.serviceLoopDao().equipment("equipment-1")?.id)
        assertNotEquals(database.serviceLoopDao().equipment("equipment-1")?.name, database.serviceLoopDao().equipment("equipment-1")?.id)
    }

    @Test fun currentObligationIdentitySurvivesReloadAndIsNotDueDateOrDisplayText() = runTest {
        seedFoundation()
        val plan = database.serviceLoopDao().plan("plan-1")!!
        val obligation = database.serviceLoopDao().obligation(plan.currentObligationId!!)!!
        assertEquals("obligation-1", obligation.id)
        assertEquals(plan.id, obligation.planId)
        assertNotEquals(plan.currentDueDate, obligation.id)
        assertNotEquals(plan.name, obligation.id)
        val reconstructed = RoomServiceLoopRepository(database, time).equipment("equipment-1")!!
        assertEquals("obligation-1", reconstructed.plans.single().currentObligationId)
    }

    @Test fun capturedSnapshotRemainsFixedWhenMasterDataIsRenamed() = runTest {
        seedFoundation()
        val repository = RoomServiceLoopRepository(database, time)
        database.serviceLoopDao().renameCustomer("customer-1", "Current customer changed")
        database.serviceLoopDao().renameEquipment("equipment-1", "Current equipment changed")
        val draft = repository.inspection("work-1")!!
        assertEquals("Captured customer", database.serviceLoopDao().visit("visit-1")?.customerNameSnapshot)
        assertEquals("Captured equipment", draft.equipmentName)
        assertEquals("Captured service", draft.serviceName)
        assertEquals("Captured question", draft.questions.single().label)
    }

    @Test fun durableDraftSurvivesRepositoryReconstruction() = runTest {
        seedFoundation()
        val firstRepository = RoomServiceLoopRepository(database, time)
        firstRepository.saveResponse("work-1", "check-1", ResponseDisposition.OK, null, null)
        val reconstructedRepository = RoomServiceLoopRepository(database, time)
        assertEquals(ResponseDisposition.OK, reconstructedRepository.inspection("work-1")!!.questions.single().disposition)
    }

    @Test fun failedDraftWriteDoesNotPersistTheOptimisticAnswer() = runTest {
        seedFoundation()
        val repository = RoomServiceLoopRepository(database, time, DraftWriteGate { error("controlled write failure") })
        try {
            repository.saveResponse("work-1", "check-1", ResponseDisposition.OK, null, null)
            fail("Expected the controlled write failure")
        } catch (expected: IllegalStateException) {
            assertEquals("controlled write failure", expected.message)
        }
        assertEquals(ResponseDisposition.NOT_CHECKED, RoomServiceLoopRepository(database, time).inspection("work-1")!!.questions.single().disposition)
    }

    @Test fun issueReasonBecomesInactiveDraftAfterTransitionToOk() = runTest {
        seedFoundation()
        database.serviceLoopDao().upsertResponses(listOf(WorkingResponseEntity("response-1", "work-1", "check-1", "ISSUE_FOUND", null, null, "Fraying edge", 2, issueFoundReasonDraft = "Fraying edge")))

        RoomServiceLoopRepository(database, time).saveResponse("work-1", "check-1", ResponseDisposition.OK, null, "Fraying edge")

        val question = RoomServiceLoopRepository(database, time).inspection("work-1")!!.questions.single()
        assertEquals(ResponseDisposition.OK, question.disposition)
        assertEquals(null, question.reason)
        assertEquals(null, question.textValue)
        assertEquals(null, question.numberValue)
        assertEquals("Fraying edge", question.issueFoundReasonDraft)
    }

    @Test fun savedNumericValueSurvivesAsInactiveDraft() = runTest {
        seedFoundation()
        val dao = database.serviceLoopDao()
        dao.insertChecklistItems(listOf(ChecklistItemSnapshotEntity("check-number", "template-snapshot-1", 2, "Reading", "NUMBER", "km", true, null)))
        dao.upsertResponses(listOf(WorkingResponseEntity("response-number", "work-1", "check-number", "VALUE", null, "1240.5", null, 2)))

        RoomServiceLoopRepository(database, time).saveResponse("work-1", "check-number", ResponseDisposition.NOT_APPLICABLE, "1240.5", "Not fitted")

        val question = RoomServiceLoopRepository(database, time).inspection("work-1")!!.questions.first { it.snapshotItemId == "check-number" }
        assertEquals(ResponseDisposition.NOT_APPLICABLE, question.disposition)
        assertEquals("1240.5", question.numberValue)
        assertEquals("Not fitted", question.reason)
    }

    @Test fun statusModeDraftsSurviveSwitchingAndRestoreOnlyActiveReason() = runTest {
        seedFoundation()
        val repository = RoomServiceLoopRepository(database, time)
        repository.saveResponse("work-1", "check-1", ResponseDisposition.ISSUE_FOUND, null, "Belt frayed")
        repository.saveResponse("work-1", "check-1", ResponseDisposition.OK, null, null)
        repository.saveResponse("work-1", "check-1", ResponseDisposition.NOT_APPLICABLE, null, "Access impossible")
        repository.saveResponse("work-1", "check-1", ResponseDisposition.NOT_CHECKED, null, null)
        repository.saveResponse("work-1", "check-1", ResponseDisposition.ISSUE_FOUND, null, null)
        var question = repository.inspection("work-1")!!.questions.single()
        assertEquals("Belt frayed", question.reason)
        assertEquals("Access impossible", question.notApplicableReasonDraft)
        repository.saveResponse("work-1", "check-1", ResponseDisposition.NOT_APPLICABLE, null, null)
        question = repository.inspection("work-1")!!.questions.single()
        assertEquals("Access impossible", question.reason)
        assertEquals("Belt frayed", question.issueFoundReasonDraft)
    }

    @Test fun inactiveDraftsDoNotLeakIntoOkFinalSnapshot() = runTest {
        seedFoundation()
        val repository = RoomServiceLoopRepository(database, time)
        repository.saveResponse("work-1", "check-1", ResponseDisposition.ISSUE_FOUND, null, "Frayed belt")
        repository.saveResponse("work-1", "check-1", ResponseDisposition.NOT_APPLICABLE, null, "Access blocked")
        repository.saveResponse("work-1", "check-1", ResponseDisposition.OK, null, null)
        assertEquals(null, repository.inspection("work-1")!!.questions.single().reason)
        repository.markChecklistReviewed("work-1")
        val result=repository.finalizeVisit("visit-1") as com.v16studio.serviceloop.domain.FinalizeResult.Success
        val record=database.serviceLoopDao().finalRecord(result.recordId)!!
        val finalWork=database.serviceLoopDao().finalWorkItems(record.currentRevisionId).single()
        val finalAnswer=database.serviceLoopDao().finalChecklistItems(finalWork.id).single()
        assertEquals("OK",finalAnswer.disposition)
        assertEquals(null,finalAnswer.reason)
        assertEquals(null,finalAnswer.textValue)
        assertEquals(null,finalAnswer.numberValue)
    }

    @Test fun harmlessStatusChangePersistsWithoutContradictoryFields() = runTest {
        seedFoundation()

        RoomServiceLoopRepository(database, time).saveResponse("work-1", "check-1", ResponseDisposition.OK, "stale", "stale")

        val question = RoomServiceLoopRepository(database, time).inspection("work-1")!!.questions.single()
        assertEquals(ResponseDisposition.OK, question.disposition)
        assertEquals(null, question.textValue)
        assertEquals(null, question.numberValue)
        assertEquals(null, question.reason)
    }

    @Test fun performedOutcomeDoesNotImplicitlyFulfillCurrentObligation() = runTest {
        seedFoundation()
        val line = RoomServiceLoopRepository(database, time).completionLines("visit-1").single()
        assertEquals("PERFORMED", line.outcome)
        assertEquals(FulfillmentEligibility.ELIGIBLE, line.fulfillmentEligibility)
        assertFalse(line.fulfillsCurrentObligation)
        assertEquals("2026-09-01", line.dueDate)
        assertEquals(null, line.proposedNextDueDate)
    }

    @Test fun fulfillmentEligibilityRejectsPartialAndNotPerformedEvenIfPersistedTrue() = runTest {
        seedFoundation()
        insertAdditionalWorkItem("work-partial", "PARTLY_PERFORMED", true)
        insertAdditionalWorkItem("work-not-performed", "NOT_PERFORMED", true)
        insertAdditionalWorkItem("work-unreviewed", "PERFORMED", true, templateSnapshotId = "template-snapshot-1")

        val lines = RoomServiceLoopRepository(database, time).completionLines("visit-1").associateBy { it.workItemId }
        listOf("work-partial", "work-not-performed").forEach { id ->
            assertEquals(FulfillmentEligibility.OUTCOME_INELIGIBLE, lines.getValue(id).fulfillmentEligibility)
            assertFalse(lines.getValue(id).fulfillsCurrentObligation)
            assertEquals(null, lines.getValue(id).proposedNextDueDate)
        }
        assertEquals(FulfillmentEligibility.CHECKLIST_NOT_REVIEWED, lines.getValue("work-unreviewed").fulfillmentEligibility)
        assertFalse(lines.getValue("work-unreviewed").fulfillsCurrentObligation)
        assertEquals(null, lines.getValue("work-unreviewed").proposedNextDueDate)
    }

    @Test fun explicitEligibleFulfillmentUsesCapturedIntervalFromActualServiceDate() = runTest {
        seedFoundation()
        insertAdditionalWorkItem("work-fulfilled", "PERFORMED", true)

        val line = RoomServiceLoopRepository(database, time).completionLines("visit-1").first { it.workItemId == "work-fulfilled" }
        assertEquals(FulfillmentEligibility.ELIGIBLE, line.fulfillmentEligibility)
        assertTrue(line.fulfillsCurrentObligation)
        assertEquals("2026-12-05", line.proposedNextDueDate)
    }

    @Test fun performedOneOffNeverFulfillsOrDerivesRecurringDueDate() = runTest {
        seedFoundation()
        insertOneOffWorkItem("work-one-off", false)
        insertOneOffWorkItem("work-one-off-stale", true)

        val lines = RoomServiceLoopRepository(database, time).completionLines("visit-1").associateBy { it.workItemId }
        listOf("work-one-off", "work-one-off-stale").forEach { id ->
            val line = lines.getValue(id)
            assertEquals(FulfillmentEligibility.NO_CURRENT_OBLIGATION, line.fulfillmentEligibility)
            assertFalse(line.fulfillsCurrentObligation)
            assertEquals(null, line.proposedNextDueDate)
        }
    }

    @Test fun attachmentUsesStableOwnerAndOwnedPathNotDisplayNameOrExternalUri() = runTest {
        seedFoundation()
        val attachment = AttachmentEntity("attachment-1", "WORK_ITEM", "work-1", "attachments/attachment-1/original.jpg", "sha256-content", "customer photo.jpg", "image/jpeg", false, "PRESENT")
        database.serviceLoopDao().insertAttachments(listOf(attachment))
        val reloaded = database.serviceLoopDao().attachment("attachment-1")!!
        assertEquals("work-1", reloaded.ownerId)
        assertNotEquals(reloaded.originalDisplayName, reloaded.id)
        assertTrue(reloaded.storedRelativePath.startsWith("attachments/${reloaded.id}/"))
        assertFalse(reloaded.storedRelativePath.contains("://"))
    }

    private suspend fun seedFoundation() {
        val dao = database.serviceLoopDao()
        dao.insertCustomers(listOf(CustomerEntity("customer-1", "CU-001", "Current customer")))
        dao.insertSites(listOf(SiteEntity("site-1", "customer-1", "ST-001", "Current site", null, "Private access")))
        dao.insertEquipment(listOf(EquipmentEntity("equipment-1", "site-1", "EQ-001", "T-01", "Current equipment", null, null, null, "Private equipment note")))
        dao.insertPlans(listOf(ServicePlanEntity("plan-1", "equipment-1", "P-001", "Current plan", 3, "MONTHS", "2026-09-01", "ACTIVE", "obligation-1")))
        dao.insertObligations(listOf(ServiceObligationEntity("obligation-1", "plan-1", 1, "2026-09-01", 1)))
        dao.insertTemplateSnapshots(listOf(TemplateSnapshotEntity("template-snapshot-1", "template-1", "Captured template", 2, 2)))
        dao.insertChecklistItems(listOf(ChecklistItemSnapshotEntity("check-1", "template-snapshot-1", 1, "Captured question", "STATUS", null, true, "Private guidance")))
        dao.insertVisits(listOf(WorkingVisitEntity("visit-1", "V-001", "customer-1", "site-1", "2026-09-05", "Captured customer", "Captured site", "Captured address", "WORKING", 3, "CU-001", "ST-001", "Business", "Technician", null, null, null, "Europe/Bucharest")))
        dao.insertWorkItems(listOf(WorkItemEntity("work-1", "visit-1", "equipment-1", "plan-1", "obligation-1", "template-snapshot-1", "Captured equipment", "EQ-001", "Captured service", "P-001", "2026-09-01", 3, "MONTHS", true, "PERFORMED", false)))
        dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity("work-1", "Public work")))
        dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity("work-1", "Private work")))
    }

    private suspend fun insertAdditionalWorkItem(id: String, outcome: String, fulfills: Boolean, templateSnapshotId: String? = null) {
        val dao = database.serviceLoopDao()
        dao.insertWorkItems(listOf(WorkItemEntity(id, "visit-1", "equipment-1", "plan-1", "obligation-1", templateSnapshotId, "Captured equipment", "EQ-001", "Captured service", "P-001", "2026-09-01", 3, "MONTHS", false, outcome, fulfills)))
        dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity(id, "Work note")))
        dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity(id, "")))
    }

    private suspend fun insertOneOffWorkItem(id: String, fulfills: Boolean) {
        val dao = database.serviceLoopDao()
        dao.insertWorkItems(listOf(WorkItemEntity(id, "visit-1", "equipment-1", null, null, null, "Captured equipment", "EQ-001", "One-off service", null, null, null, null, false, "PERFORMED", fulfills)))
        dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity(id, "One-off work note")))
        dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity(id, "")))
    }
}
