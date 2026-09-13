package com.v16studio.serviceloop

import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.domain.BusinessProfile
import com.v16studio.serviceloop.domain.CompletionLine
import com.v16studio.serviceloop.domain.CustomerDetail
import com.v16studio.serviceloop.domain.CustomerSummary
import com.v16studio.serviceloop.domain.EquipmentDetail
import com.v16studio.serviceloop.domain.EquipmentSummary
import com.v16studio.serviceloop.domain.HomeSummary
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.domain.ServiceDraftFieldKeys
import com.v16studio.serviceloop.domain.SiteRegisterSummary
import com.v16studio.serviceloop.domain.SiteDetail
import com.v16studio.serviceloop.domain.VisitSummary
import com.v16studio.serviceloop.ui.service.ServiceDraftAutosaveCoordinator
import com.v16studio.serviceloop.ui.service.ServiceDraftFieldId
import com.v16studio.serviceloop.ui.service.ServiceDraftFieldState
import com.v16studio.serviceloop.ui.service.ServiceDraftValidators
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ServiceDraftAutosaveCoordinatorTest {
    @Test fun validTextDebouncesWritesCanonicalTrimAndClearsRawBuffer() = runTest {
        val repository = BufferRepository()
        val coordinator = ServiceDraftAutosaveCoordinator(repository, this)
        val field = ServiceDraftFieldId("work-1", ServiceDraftFieldKeys.WORK)

        coordinator.scheduleText(field, "  completed work  ", ServiceDraftValidators.alwaysValid()) { value -> repository.canonical(field, value.trim()); 42L }
        advanceUntilIdle()

        assertEquals(listOf("  completed work  "), repository.rawWrites)
        assertEquals("completed work", repository.canonicalValues[field])
        assertFalse(repository.buffers.containsKey(field))
        assertEquals(ServiceDraftFieldState.Clean(42L), coordinator.states.value[field])
    }

    @Test fun invalidTextIsDurableRawInputAndNeverCallsCanonicalWriter() = runTest {
        val repository = BufferRepository()
        val coordinator = ServiceDraftAutosaveCoordinator(repository, this)
        val field = ServiceDraftFieldId("work-1", ServiceDraftFieldKeys.questionValue("q1"))

        coordinator.scheduleText(field, "12.", ServiceDraftValidators.number()) { error("must not write") }
        advanceUntilIdle()

        assertEquals("12.", repository.buffers[field])
        assertTrue(repository.canonicalValues.isEmpty())
        assertEquals(ServiceDraftFieldState.Invalid("12.", "Enter a complete number"), coordinator.states.value[field])
    }

    @Test fun latestVersionWinsAndOldWriterCannotClearNewerBuffer() = runTest {
        val repository = BufferRepository()
        val coordinator = ServiceDraftAutosaveCoordinator(repository, this)
        val field = ServiceDraftFieldId("work-1", ServiceDraftFieldKeys.WORK)

        coordinator.scheduleText(field, "old") { value -> repository.canonical(field, value); 1L }
        coordinator.scheduleText(field, "new") { value -> repository.canonical(field, value); 2L }
        advanceUntilIdle()

        assertEquals("new", repository.canonicalValues[field])
        assertFalse(repository.buffers.containsKey(field))
        assertEquals(ServiceDraftFieldState.Clean(2L), coordinator.states.value[field])
    }

    @Test fun flushLeavesInvalidAndFailedFieldsVisible() = runTest {
        val repository = BufferRepository()
        val coordinator = ServiceDraftAutosaveCoordinator(repository, this)
        val invalid = ServiceDraftFieldId("work-1", ServiceDraftFieldKeys.questionValue("q1"))
        val failed = ServiceDraftFieldId("work-1", ServiceDraftFieldKeys.WORK)
        coordinator.scheduleText(invalid, "-", ServiceDraftValidators.number()) { error("must not write") }
        coordinator.scheduleText(failed, "kept") { error("disk unavailable") }
        val result = coordinator.flush("work-1")

        assertFalse(result.success)
        assertTrue(invalid in result.invalidFields)
        assertTrue(failed in result.failedFields)
        assertEquals("kept", repository.buffers[failed])
        assertEquals(ServiceDraftFieldState.Failed("kept", "disk unavailable", null), coordinator.states.value[failed])
    }

    @Test fun freshCoordinatorFlushDiscoversPersistedRawBufferAndLeavesItPending() = runTest {
        val repository = BufferRepository()
        val field = ServiceDraftFieldId("work-1", ServiceDraftFieldKeys.WORK)
        repository.buffers[field] = "recovered raw edit"
        val coordinator = ServiceDraftAutosaveCoordinator(repository, this)

        val result = coordinator.flush("work-1")

        assertFalse(result.success)
        assertTrue(field in result.pendingFields)
        assertEquals(ServiceDraftFieldState.Pending("recovered raw edit"), coordinator.states.value[field])
        assertEquals("recovered raw edit", repository.buffers[field])
    }

    @Test fun immediateChoiceDoesNotCreateAWorkingInputBuffer() = runTest {
        val repository = BufferRepository()
        val coordinator = ServiceDraftAutosaveCoordinator(repository, this)
        val field = ServiceDraftFieldId("work-1", "response:q1")

        coordinator.immediateChoice(field, "OK") { value -> repository.canonical(field, value); 7L }
        advanceUntilIdle()

        assertTrue(repository.buffers.isEmpty())
        assertEquals("OK", repository.canonicalValues[field])
        assertEquals(ServiceDraftFieldState.Clean(7L), coordinator.states.value[field])
    }

    @Test fun blankIssueDescriptionPersistsClearAndLeavesChecklistIncomplete() = runTest {
        val repository = BufferRepository()
        val coordinator = ServiceDraftAutosaveCoordinator(repository, this)
        val field = ServiceDraftFieldId("work-1", ServiceDraftFieldKeys.questionIssue("q1"))
        repository.canonicalValues[field] = "Old"
        repository.preservedDrafts[field] = "Old"

        coordinator.scheduleText(field, "", ServiceDraftValidators.issueDescription()) { value ->
            if (value.trim().isBlank()) {
                repository.canonicalValues.remove(field)
                repository.preservedDrafts.remove(field)
                repository.checklistComplete = false
            }
            9L
        }
        advanceUntilIdle()

        assertEquals(null, repository.canonicalValues[field])
        assertEquals(null, repository.preservedDrafts[field])
        assertTrue(repository.buffers.isEmpty())
        assertFalse(repository.checklistComplete)
        assertEquals(ServiceDraftFieldState.Clean(9L), coordinator.states.value[field])
    }

    @Test fun blankNotApplicableReasonPersistsClearAndLeavesChecklistIncomplete() = runTest {
        val repository = BufferRepository()
        val coordinator = ServiceDraftAutosaveCoordinator(repository, this)
        val field = ServiceDraftFieldId("work-1", ServiceDraftFieldKeys.questionNotApplicable("q1"))
        repository.canonicalValues[field] = "Old"
        repository.preservedDrafts[field] = "Old"

        coordinator.scheduleText(field, "", ServiceDraftValidators.notApplicableReason()) { value ->
            if (value.trim().isBlank()) {
                repository.canonicalValues.remove(field)
                repository.preservedDrafts.remove(field)
                repository.checklistComplete = false
            }
            10L
        }
        advanceUntilIdle()

        assertEquals(null, repository.canonicalValues[field])
        assertEquals(null, repository.preservedDrafts[field])
        assertTrue(repository.buffers.isEmpty())
        assertFalse(repository.checklistComplete)
        assertEquals(ServiceDraftFieldState.Clean(10L), coordinator.states.value[field])
    }

    @Test fun cancelAndJoinPreventsLateRawBufferRewriteAfterCallerClearsIt() = runTest {
        val repository = BlockingBufferRepository()
        val coordinator = ServiceDraftAutosaveCoordinator(repository, this)
        val field = ServiceDraftFieldId("work-1", ServiceDraftFieldKeys.OVERRIDE_DATE)

        coordinator.scheduleRaw(field, "2026-12-05", ServiceDraftValidators.isoDate())
        repository.writeEntered.await()

        val cancellation = launch { coordinator.cancelAndJoin(field) }
        runCurrent()
        assertTrue(cancellation.isActive)

        repository.releaseWrite.complete(Unit)
        cancellation.join()
        repository.clearWorkingInputBuffer(field.workItemId, field.fieldKey)
        advanceUntilIdle()

        assertTrue(repository.buffers.isEmpty())
        assertTrue(coordinator.states.value[field] == null)
    }

    private class BufferRepository : ServiceLoopRepository {
        val buffers = mutableMapOf<ServiceDraftFieldId, String>()
        val rawWrites = mutableListOf<String>()
        val canonicalValues = mutableMapOf<ServiceDraftFieldId, String>()
        val preservedDrafts = mutableMapOf<ServiceDraftFieldId, String>()
        var checklistComplete = true
        private var timestamp = 0L

        fun canonical(field: ServiceDraftFieldId, value: String) { canonicalValues[field] = value }

        override suspend fun home(): HomeSummary = error("unused")
        override suspend fun equipment(id: String): EquipmentDetail? = error("unused")
        override suspend fun equipmentList(): List<EquipmentSummary> = error("unused")
        override suspend fun customerList(): List<CustomerSummary> = error("unused")
        override suspend fun siteList(): List<SiteRegisterSummary> = error("unused")
        override suspend fun inspection(workItemId: String): InspectionDraft? = error("unused")
        override suspend fun completionLines(visitId: String): List<CompletionLine> = error("unused")
        override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?): Long = error("unused")
        override suspend fun saveWorkingInputBuffer(workItemId: String, fieldKey: String, rawValue: String): Long {
            rawWrites += rawValue
            buffers[ServiceDraftFieldId(workItemId, fieldKey)] = rawValue
            return ++timestamp
        }
        override suspend fun clearWorkingInputBuffer(workItemId: String, fieldKey: String): Long {
            buffers.remove(ServiceDraftFieldId(workItemId, fieldKey))
            return ++timestamp
        }
        override suspend fun workingInputBuffers(workItemId: String): Map<String, String> = buffers
            .filterKeys { it.workItemId == workItemId }
            .mapKeys { it.key.fieldKey }
        override suspend fun serviceDraftWorkItemIds(visitId: String): List<String> = listOf("work-1")
    }

    private class BlockingBufferRepository : ServiceLoopRepository {
        val writeEntered = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val buffers = mutableMapOf<ServiceDraftFieldId, String>()

        override suspend fun home(): HomeSummary = error("unused")
        override suspend fun equipment(id: String): EquipmentDetail? = error("unused")
        override suspend fun equipmentList(): List<EquipmentSummary> = error("unused")
        override suspend fun customerList(): List<CustomerSummary> = error("unused")
        override suspend fun inspection(workItemId: String): InspectionDraft? = error("unused")
        override suspend fun completionLines(visitId: String): List<CompletionLine> = error("unused")
        override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?): Long = error("unused")

        override suspend fun saveWorkingInputBuffer(workItemId: String, fieldKey: String, rawValue: String): Long {
            writeEntered.complete(Unit)
            withContext(NonCancellable) {
                releaseWrite.await()
                buffers[ServiceDraftFieldId(workItemId, fieldKey)] = rawValue
            }
            return 1L
        }

        override suspend fun clearWorkingInputBuffer(workItemId: String, fieldKey: String): Long {
            buffers.remove(ServiceDraftFieldId(workItemId, fieldKey))
            return 2L
        }
    }
}
