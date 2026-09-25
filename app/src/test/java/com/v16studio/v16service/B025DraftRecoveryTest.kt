package com.v16studio.v16service

import com.v16studio.v16service.data.V16ServiceRepository
import com.v16studio.v16service.domain.CompletionLine
import com.v16studio.v16service.domain.CompletionBlocker
import com.v16studio.v16service.domain.CustomerSummary
import com.v16studio.v16service.domain.EquipmentDetail
import com.v16studio.v16service.domain.EquipmentSummary
import com.v16studio.v16service.domain.FulfillmentEligibility
import com.v16studio.v16service.domain.HomeSummary
import com.v16studio.v16service.domain.InspectionDraft
import com.v16studio.v16service.domain.InspectionQuestion
import com.v16studio.v16service.domain.ResponseDisposition
import com.v16studio.v16service.domain.SaveStatus
import com.v16studio.v16service.domain.ServiceDraftFieldKeys
import com.v16studio.v16service.ui.V16ServiceViewModel
import com.v16studio.v16service.ui.service.ServiceDraftFieldId
import com.v16studio.v16service.ui.service.ServiceDraftFieldState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class B025DraftRecoveryTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun freshViewModelReconcilesPersistedWorkTextAndClearsItsBuffer() = runTest {
        val fieldKey = ServiceDraftFieldKeys.WORK
        val repository = RecoveryRepository(inspectionDraft().copy(rawInputs = mapOf(fieldKey to "Recovered work")))
        val viewModel = V16ServiceViewModel(repository, restrictedRecoveryState = true) {}

        viewModel.loadInspection("work-1")
        advanceUntilIdle()

        assertEquals("Recovered work", repository.workPerformed)
        assertTrue(repository.buffers.isEmpty())
        assertEquals(ServiceDraftFieldState.Clean(200L), viewModel.serviceDraftStates.value[ServiceDraftFieldId("work-1", fieldKey)])
    }

    @Test fun freshViewModelRetainsInvalidNumericDraftAndFlushFails() = runTest {
        val fieldKey = ServiceDraftFieldKeys.questionValue("check-number")
        val repository = RecoveryRepository(
            inspectionDraft().copy(
                questions = listOf(InspectionQuestion("response-number", "check-number", 1, "Reading", "NUMBER", "mm", true, ResponseDisposition.VALUE, null, "10.0", null)),
                rawInputs = mapOf(fieldKey to "12."),
            ),
        )
        val viewModel = V16ServiceViewModel(repository, restrictedRecoveryState = true) {}

        viewModel.loadInspection("work-1")
        advanceUntilIdle()

        assertEquals("10.0", repository.numberValue)
        assertEquals("12.", repository.buffers[fieldKey])
        assertTrue(viewModel.serviceDraftStates.value[ServiceDraftFieldId("work-1", fieldKey)] is ServiceDraftFieldState.Invalid)
        val flushed = viewModel.flushServiceDraft("work-1")
        assertFalse(flushed.success)
        assertTrue(ServiceDraftFieldId("work-1", fieldKey) in flushed.invalidFields)
    }

    @Test fun applyRecurrenceOverridePerformsOneCanonicalWriteAndClearsBothBuffers() = runTest {
        val repository = RecoveryRepository()
        repository.buffers[ServiceDraftFieldKeys.OVERRIDE_DATE] = "2026-12-05"
        repository.buffers[ServiceDraftFieldKeys.OVERRIDE_REASON] = "Customer requested a later date"
        val viewModel = V16ServiceViewModel(repository, restrictedRecoveryState = true) {}

        viewModel.loadCompletion("visit-1")
        viewModel.applyRecurrenceOverride("work-1", "visit-1", "2026-12-05", "Customer requested a later date")
        advanceUntilIdle()

        assertEquals(1, repository.completionWriteCount)
        assertEquals(listOf("PERFORMED", true, null, "2026-12-05", false, "Customer requested a later date"), repository.lastCompletionWrite)
        assertTrue(repository.buffers.isEmpty())
        assertTrue(viewModel.state.value.saveStatus is SaveStatus.Saved)
    }

    @Test fun recurrenceOverrideFailureLeavesBothRawBuffersIntact() = runTest {
        val repository = RecoveryRepository(failCompletionWrite = true)
        repository.buffers[ServiceDraftFieldKeys.OVERRIDE_DATE] = "2026-12-05"
        repository.buffers[ServiceDraftFieldKeys.OVERRIDE_REASON] = "Customer requested a later date"
        val viewModel = V16ServiceViewModel(repository, restrictedRecoveryState = true) {}

        viewModel.loadCompletion("visit-1")
        viewModel.applyRecurrenceOverride("work-1", "visit-1", "2026-12-05", "Customer requested a later date")
        advanceUntilIdle()

        assertEquals(1, repository.completionWriteCount)
        assertEquals("2026-12-05", repository.buffers[ServiceDraftFieldKeys.OVERRIDE_DATE])
        assertEquals("Customer requested a later date", repository.buffers[ServiceDraftFieldKeys.OVERRIDE_REASON])
        assertTrue(viewModel.state.value.saveStatus is SaveStatus.Failed)
    }

    private fun inspectionDraft() = InspectionDraft(
        workItemId = "work-1",
        visitId = "visit-1",
        visitReference = "V-001",
        siteName = "Site",
        equipmentName = "Machine",
        equipmentReference = "EQ-001",
        serviceName = "Service",
        dueDate = "2026-09-01",
        interval = "Every 3 months",
        templateRevision = 1,
        workPerformed = "Old work",
        privateInternalNote = "Private",
        checklistReviewed = false,
        outcome = "PERFORMED",
        fulfillsCurrentObligation = false,
        modifiedAtEpochMillis = 100L,
        questions = listOf(InspectionQuestion("response-1", "check-1", 1, "Guard", "STATUS", null, true, ResponseDisposition.OK, null, null, null)),
        checklistComplete = true,
        requiredComplete = 1,
        requiredTotal = 1,
    )

    private class RecoveryRepository(
        private var draft: InspectionDraft = InspectionDraft(
            workItemId = "work-1",
            visitId = "visit-1",
            visitReference = "V-001",
            siteName = "Site",
            equipmentName = "Machine",
            equipmentReference = "EQ-001",
            serviceName = "Service",
            dueDate = "2026-09-01",
            interval = "Every 3 months",
            templateRevision = 1,
            workPerformed = "Old work",
            privateInternalNote = "Private",
            checklistReviewed = false,
            outcome = "PERFORMED",
            fulfillsCurrentObligation = false,
            modifiedAtEpochMillis = 100L,
            questions = listOf(InspectionQuestion("response-1", "check-1", 1, "Guard", "STATUS", null, true, ResponseDisposition.OK, null, null, null)),
            checklistComplete = true,
            requiredComplete = 1,
            requiredTotal = 1,
        ),
        private val failCompletionWrite: Boolean = false,
    ) : V16ServiceRepository {
        val buffers = draft.rawInputs.toMutableMap()
        var workPerformed = draft.workPerformed
            private set
        val numberValue: String?
            get() = draft.questions.firstOrNull { it.snapshotItemId == "check-number" }?.numberValue
        var completionWriteCount = 0
            private set
        var lastCompletionWrite: List<Any?>? = null
            private set
        private var timestamp = 199L

        override suspend fun home() = HomeSummary(null, null, null, null, null, null, null, 0, null, null, 0, 0)
        override suspend fun equipment(id: String): EquipmentDetail? = null
        override suspend fun equipmentList(): List<EquipmentSummary> = emptyList()
        override suspend fun customerList(): List<CustomerSummary> = emptyList()
        override suspend fun inspection(workItemId: String): InspectionDraft = draft.copy(workPerformed = workPerformed, rawInputs = buffers.toMap())
        override suspend fun completionLines(visitId: String): List<CompletionLine> = listOf(
            CompletionLine(
                workItemId = "work-1",
                equipmentName = "Machine",
                equipmentReference = "EQ-001",
                serviceName = "Service",
                outcome = "PERFORMED",
                fulfillmentEligibility = FulfillmentEligibility.ELIGIBLE,
                fulfillsCurrentObligation = false,
                dueDate = "2026-09-01",
                proposedNextDueDate = "2026-12-05",
                workPerformed = workPerformed,
                notPerformedReason = null,
                checklistComplete = true,
                blockers = emptyList<CompletionBlocker>(),
            ),
        )
        override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?): Long = ++timestamp
        override suspend fun saveWorkingInputBuffer(workItemId: String, fieldKey: String, rawValue: String): Long {
            buffers[fieldKey] = rawValue
            return ++timestamp
        }
        override suspend fun clearWorkingInputBuffer(workItemId: String, fieldKey: String): Long {
            buffers.remove(fieldKey)
            return ++timestamp
        }
        override suspend fun workingInputBuffers(workItemId: String): Map<String, String> = buffers.toMap()
        override suspend fun savePublicWork(workItemId: String, text: String): Long {
            workPerformed = text.trim()
            return 200L
        }
        override suspend fun savePrivateNote(workItemId: String, text: String): Long = ++timestamp
        override suspend fun saveCompletionDraft(workItemId: String, outcome: String?, fulfills: Boolean?, reason: String?, nextDue: String?, calculated: Boolean?, overrideReason: String?): Long {
            completionWriteCount++
            lastCompletionWrite = listOf(outcome, fulfills, reason, nextDue, calculated, overrideReason)
            if (failCompletionWrite) error("completion write failed")
            return 300L
        }
    }
}
