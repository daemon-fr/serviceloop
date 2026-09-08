package com.v16studio.serviceloop

import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.domain.CompletionLine
import com.v16studio.serviceloop.domain.CustomerSummary
import com.v16studio.serviceloop.domain.EquipmentDetail
import com.v16studio.serviceloop.domain.EquipmentSummary
import com.v16studio.serviceloop.domain.HomeSummary
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.InspectionQuestion
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.domain.SaveStatus
import com.v16studio.serviceloop.domain.BusinessProfile
import com.v16studio.serviceloop.domain.FinalRecordDetail
import com.v16studio.serviceloop.domain.PublicReportModel
import com.v16studio.serviceloop.domain.ReportRendition
import com.v16studio.serviceloop.report.ReportService
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class StateSemanticsTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun unansweredAndNotCheckedAreDistinctFromPassing() {
        assertTrue(ResponseDisposition.UNANSWERED != ResponseDisposition.OK)
        assertTrue(ResponseDisposition.NOT_CHECKED != ResponseDisposition.OK)
        assertFalse(ResponseDisposition.NOT_CHECKED.name.contains("OK"))
    }

    @Test fun initialRootDataLoadsAsOneBlockingProjection() = runTest {
        val repository = RootRefreshRepository(inspectionDraft())
        val viewModel = ServiceLoopViewModel(repository) {}

        assertTrue(viewModel.state.value.rootDataReady)
        assertFalse(viewModel.state.value.loading)
        assertEquals(100L, viewModel.state.value.home?.savedAtEpochMillis)
        assertEquals(1, repository.homeReads)
        assertEquals(1, repository.equipmentReads)
        assertEquals(1, repository.customerReads)
    }

    @Test fun quietRootRefreshKeepsUsableContentVisibleUntilAtomicReplacement() = runTest {
        val repository = RootRefreshRepository(inspectionDraft())
        val viewModel = ServiceLoopViewModel(repository) {}
        repository.nextSavedAt = 200L
        repository.refreshGate = CompletableDeferred()

        viewModel.refreshRootDataNonBlocking()

        assertFalse(viewModel.state.value.loading)
        assertEquals(100L, viewModel.state.value.home?.savedAtEpochMillis)
        repository.refreshGate?.complete(Unit)
        assertEquals(200L, viewModel.state.value.home?.savedAtEpochMillis)
        assertTrue(viewModel.state.value.equipmentList.isNotEmpty())
        assertTrue(viewModel.state.value.customerList.isNotEmpty())
    }

    @Test fun quietRefreshFailurePreservesRootDataAndSuccessfulSaveState() = runTest {
        val repository = RootRefreshRepository(inspectionDraft())
        val viewModel = ServiceLoopViewModel(repository) {}
        viewModel.loadInspection("work-1")
        viewModel.requestResponseChange("check-1", ResponseDisposition.OK)
        assertEquals(SaveStatus.Saved(200), viewModel.state.value.saveStatus)
        repository.failRefresh = true

        viewModel.refreshRootDataNonBlocking()

        assertEquals(100L, viewModel.state.value.home?.savedAtEpochMillis)
        assertTrue(viewModel.state.value.equipmentList.isNotEmpty())
        assertTrue(viewModel.state.value.customerList.isNotEmpty())
        assertEquals(SaveStatus.Saved(200), viewModel.state.value.saveStatus)
        assertEquals("root refresh failed", viewModel.state.value.rootRefreshError)
        assertFalse(viewModel.state.value.loading)
    }

    @Test fun viewModelReportsFailedNotSavedAfterPersistenceFailure() = runTest {
        val draft = inspectionDraft()
        val repository = object : ServiceLoopRepository {
            override suspend fun home() = HomeSummary(null, null, null, null, null, null, null, 0, null, null, 0, 0)
            override suspend fun equipment(id: String): EquipmentDetail? = null
            override suspend fun equipmentList(): List<EquipmentSummary> = emptyList()
            override suspend fun customerList(): List<CustomerSummary> = emptyList()
            override suspend fun inspection(workItemId: String): InspectionDraft = draft
            override suspend fun completionLines(visitId: String): List<CompletionLine> = emptyList()
            override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?): Long = error("database unavailable")
        }
        val viewModel = ServiceLoopViewModel(repository) {}
        viewModel.loadInspection("work-1")
        viewModel.requestResponseChange("check-1", ResponseDisposition.OK)
        val status = viewModel.state.value.saveStatus
        assertTrue(status is SaveStatus.Failed)
        assertEquals(100L, (status as SaveStatus.Failed).lastSavedAtEpochMillis)
    }

    @Test fun committedWritesRemainSavedWhenTheirPostWriteRefreshFails() = runTest {
        val actions = listOf<(ServiceLoopViewModel) -> Unit>(
            { it.loadInspection("work-1"); it.savePublicWork("work-1", "changed") },
            { it.loadInspection("work-1"); it.markChecklistReviewed("work-1") },
            { it.loadCompletion("visit-1"); it.saveCompletion("work-1", "PARTLY_PERFORMED", false, null, null, null, null, "visit-1") },
        )
        actions.forEach { action ->
            val viewModel = ServiceLoopViewModel(RefreshFailAfterWriteRepository(inspectionDraft())) {}
            action(viewModel)
            assertEquals(SaveStatus.Saved(200), viewModel.state.value.saveStatus)
            assertEquals("refresh failed", viewModel.state.value.contentRefreshError)
        }
    }

    @Test fun genericDraftRepeatedFailuresPreserveLastDurableCheckpoint() = runTest {
        val repository = CheckpointRepository(inspectionDraft(), draftWriteResults = mutableListOf<Long?>(null, null, null))
        val viewModel = ServiceLoopViewModel(repository) {}
        viewModel.loadInspection("work-1")

        repeat(3) { attempt ->
            viewModel.savePublicWork("work-1", "attempt-$attempt")
            val failed = viewModel.state.value.saveStatus as SaveStatus.Failed
            assertEquals(100L, failed.lastSavedAtEpochMillis)
        }
    }

    @Test fun genericDraftFailureThenSuccessThenFailureAdvancesAndPreservesCheckpoint() = runTest {
        val repository = CheckpointRepository(inspectionDraft(), draftWriteResults = mutableListOf<Long?>(null, 200L, null))
        val viewModel = ServiceLoopViewModel(repository) {}
        viewModel.loadInspection("work-1")

        viewModel.savePublicWork("work-1", "first")
        assertEquals(100L, (viewModel.state.value.saveStatus as SaveStatus.Failed).lastSavedAtEpochMillis)
        viewModel.savePublicWork("work-1", "second")
        assertEquals(SaveStatus.Saved(200), viewModel.state.value.saveStatus)
        viewModel.savePublicWork("work-1", "third")
        assertEquals(200L, (viewModel.state.value.saveStatus as SaveStatus.Failed).lastSavedAtEpochMillis)
    }

    @Test fun responseRepeatedFailuresPreserveDurableInspectionCheckpoint() = runTest {
        val repository = CheckpointRepository(inspectionDraft(), responseWriteResults = mutableListOf<Long?>(null, null, null))
        val viewModel = ServiceLoopViewModel(repository) {}
        viewModel.loadInspection("work-1")

        repeat(3) {
            viewModel.requestResponseChange("check-1", ResponseDisposition.OK)
            assertEquals(100L, (viewModel.state.value.saveStatus as SaveStatus.Failed).lastSavedAtEpochMillis)
        }
    }

    @Test fun businessProfileRepeatedFailuresPreserveItsPersistedCheckpoint() = runTest {
        val repository = CheckpointRepository(inspectionDraft(), profileWriteResults = mutableListOf<Long?>(null, null, null))
        val viewModel = ServiceLoopViewModel(repository) {}
        viewModel.loadBusinessProfile()

        repeat(3) {
            viewModel.saveBusinessProfile(BusinessProfile("Changed", "Technician", zoneId = "Europe/Bucharest"))
            assertEquals(75L, (viewModel.state.value.businessProfileSaveStatus as SaveStatus.Failed).lastSavedAtEpochMillis)
        }
    }

    @Test fun businessProfileSaveStateIsIndependentTruthfulAndUsesItsOwnPersistedTimestamp() = runTest {
        val successfulRepository = RefreshFailAfterWriteRepository(inspectionDraft())
        val viewModel = ServiceLoopViewModel(successfulRepository) {}
        viewModel.loadInspection("work-1"); viewModel.loadBusinessProfile()
        assertEquals(SaveStatus.Saved(100), viewModel.state.value.saveStatus)
        assertEquals(SaveStatus.Saved(75), viewModel.state.value.businessProfileSaveStatus)
        viewModel.savePublicWork("work-1", "changed")
        assertEquals(SaveStatus.Saved(75), viewModel.state.value.businessProfileSaveStatus)

        val profileViewModel = ServiceLoopViewModel(RefreshFailAfterWriteRepository(inspectionDraft())) {}
        profileViewModel.loadBusinessProfile()
        profileViewModel.saveBusinessProfile(BusinessProfile("Business", "Technician", zoneId = "Europe/Bucharest"))
        assertEquals(SaveStatus.Saved(200), profileViewModel.state.value.businessProfileSaveStatus)
        assertEquals(SaveStatus.Idle, profileViewModel.state.value.saveStatus)
        assertEquals("refresh failed", profileViewModel.state.value.contentRefreshError)

        val failedViewModel = ServiceLoopViewModel(RefreshFailAfterWriteRepository(inspectionDraft(), failProfileWrite = true)) {}
        failedViewModel.loadBusinessProfile()
        failedViewModel.saveBusinessProfile(BusinessProfile("Changed", "Technician", zoneId = "Europe/Bucharest"))
        val failed = failedViewModel.state.value.businessProfileSaveStatus as SaveStatus.Failed
        assertEquals(75L, failed.lastSavedAtEpochMillis); assertEquals(SaveStatus.Idle, failedViewModel.state.value.saveStatus)
    }

    @Test fun successfulReportGenerationRemainsReadyWhenRecordRefreshFails() = runTest {
        val public = PublicReportModel("record", "revision", 1, "V-1", "2026-09-05", 1, "Business", "Technician", "", "Customer", "Site", null, emptyList())
        val repository = object : ServiceLoopRepository {
            var generated = false
            override suspend fun home() = HomeSummary(null, null, null, null, null, null, null, 0, null, null, 0, 0)
            override suspend fun equipment(id: String): EquipmentDetail? = null
            override suspend fun equipmentList(): List<EquipmentSummary> = emptyList()
            override suspend fun customerList(): List<CustomerSummary> = emptyList()
            override suspend fun inspection(workItemId: String): InspectionDraft? = null
            override suspend fun completionLines(visitId: String): List<CompletionLine> = emptyList()
            override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?) = 1L
            override suspend fun finalRecord(recordId: String): FinalRecordDetail { if (generated) error("record refresh failed"); return FinalRecordDetail(public, emptyList(), null) }
        }
        val ready = ReportRendition("rendition", "revision", 1, 2, "reports/r.pdf", "hash", 10, 1, "READY", null)
        val reportService = object : ReportService {
            override suspend fun generate(recordId: String): ReportRendition { repository.generated = true; return ready }
            override suspend fun pageCount(relativePath: String) = 1
            override fun file(relativePath: String) = File(relativePath)
        }
        val viewModel = ServiceLoopViewModel(repository, reportService) {}
        viewModel.loadFinalRecord("record")
        viewModel.generateReport("record")
        assertEquals("READY", viewModel.state.value.finalRecord?.report?.status)
        assertEquals("record refresh failed", viewModel.state.value.contentRefreshError)
        assertFalse(viewModel.state.value.generatingReport)
    }

    @Test fun cancellingDestructiveTransitionLeavesSavedIssueResponseUntouched() = runTest {
        val repository = MutableInspectionRepository(issueDraft())
        val viewModel = ServiceLoopViewModel(repository) {}
        viewModel.loadInspection("work-1")

        viewModel.requestResponseChange("check-1", ResponseDisposition.OK)
        assertEquals("saved issue detail", viewModel.state.value.pendingResponseTransition?.detailBeingDiscarded)
        assertEquals(0, repository.saveCount)

        viewModel.cancelResponseTransition()
        assertEquals(null, viewModel.state.value.pendingResponseTransition)
        assertEquals(ResponseDisposition.ISSUE_FOUND, viewModel.state.value.inspection?.questions?.single()?.disposition)
        assertEquals("Fraying edge", viewModel.state.value.inspection?.questions?.single()?.reason)
        assertEquals(0, repository.saveCount)
    }

    @Test fun confirmingDestructiveTransitionPersistsOnlyAfterConfirmation() = runTest {
        val repository = MutableInspectionRepository(issueDraft())
        val viewModel = ServiceLoopViewModel(repository) {}
        viewModel.loadInspection("work-1")

        viewModel.requestResponseChange("check-1", ResponseDisposition.OK)
        assertEquals(0, repository.saveCount)
        viewModel.confirmResponseTransition()

        assertEquals(1, repository.saveCount)
        assertEquals(ResponseDisposition.OK, viewModel.state.value.inspection?.questions?.single()?.disposition)
        assertEquals(null, viewModel.state.value.inspection?.questions?.single()?.reason)
        assertTrue(viewModel.state.value.saveStatus is SaveStatus.Saved)
    }

    @Test fun repeatedNotApplicablePreservesSpecificReasonAndCheckpoint() = runTest {
        val repository = MutableInspectionRepository(responseDraft(ResponseDisposition.NOT_APPLICABLE, reason = "Not fitted"))
        val viewModel = ServiceLoopViewModel(repository) {}
        viewModel.loadInspection("work-1")

        viewModel.requestResponseChange("check-1", ResponseDisposition.NOT_APPLICABLE, reason = "Not applicable during this visit")

        assertEquals(0, repository.saveCount)
        assertEquals("Not fitted", viewModel.state.value.inspection?.questions?.single()?.reason)
        assertEquals(SaveStatus.Saved(100), viewModel.state.value.saveStatus)
    }

    @Test fun repeatedOkIsSemanticNoOp() = runTest {
        val repository = MutableInspectionRepository(responseDraft(ResponseDisposition.OK))
        val viewModel = ServiceLoopViewModel(repository) {}
        viewModel.loadInspection("work-1")

        viewModel.requestResponseChange("check-1", ResponseDisposition.OK)

        assertEquals(0, repository.saveCount)
        assertEquals(SaveStatus.Saved(100), viewModel.state.value.saveStatus)
    }

    @Test fun repeatedIdenticalValueIsSemanticNoOp() = runTest {
        val repository = MutableInspectionRepository(valueDraft("1240.5"))
        val viewModel = ServiceLoopViewModel(repository) {}
        viewModel.loadInspection("work-1")

        viewModel.requestResponseChange("check-1", ResponseDisposition.VALUE, value = "1240.5")

        assertEquals(0, repository.saveCount)
        assertEquals(SaveStatus.Saved(100), viewModel.state.value.saveStatus)
    }

    @Test fun genuinelyChangedValuePersists() = runTest {
        val repository = MutableInspectionRepository(valueDraft("1240.5"))
        val viewModel = ServiceLoopViewModel(repository) {}
        viewModel.loadInspection("work-1")

        viewModel.requestResponseChange("check-1", ResponseDisposition.VALUE, value = "1241.0")

        assertEquals(1, repository.saveCount)
        assertEquals("1241.0", viewModel.state.value.inspection?.questions?.single()?.numberValue)
        assertEquals(SaveStatus.Saved(200), viewModel.state.value.saveStatus)
    }

    @Test fun transitionIntoNotApplicableUsesReasonAndStillConfirmsDestructiveChange() = runTest {
        val repository = MutableInspectionRepository(issueDraft())
        val viewModel = ServiceLoopViewModel(repository) {}
        viewModel.loadInspection("work-1")

        viewModel.requestResponseChange("check-1", ResponseDisposition.NOT_APPLICABLE, reason = "Not applicable during this visit")
        assertEquals(0, repository.saveCount)
        assertEquals("saved issue detail", viewModel.state.value.pendingResponseTransition?.detailBeingDiscarded)

        viewModel.confirmResponseTransition()
        assertEquals(1, repository.saveCount)
        assertEquals(ResponseDisposition.NOT_APPLICABLE, viewModel.state.value.inspection?.questions?.single()?.disposition)
        assertEquals("Not applicable during this visit", viewModel.state.value.inspection?.questions?.single()?.reason)
    }

    @Test fun savedIssueFindingLoadsAndUnchangedSaveIsNoOp() = runTest {
        val repository = MutableInspectionRepository(issueDraft())
        val viewModel = ServiceLoopViewModel(repository) {}
        viewModel.loadInspection("work-1")

        assertEquals("Fraying edge", viewModel.state.value.inspection?.questions?.single()?.reason)
        viewModel.requestResponseChange("check-1", ResponseDisposition.ISSUE_FOUND, reason = "Fraying edge")

        assertEquals(0, repository.saveCount)
        assertEquals(SaveStatus.Saved(100), viewModel.state.value.saveStatus)
    }

    @Test fun changedInlineFindingPersistsAndAdvancesSavedCheckpoint() = runTest {
        val repository = MutableInspectionRepository(issueDraft())
        val viewModel = ServiceLoopViewModel(repository) {}
        viewModel.loadInspection("work-1")

        viewModel.requestResponseChange("check-1", ResponseDisposition.ISSUE_FOUND, reason = "Fraying along the outer edge")

        assertEquals(1, repository.saveCount)
        assertEquals("Fraying along the outer edge", viewModel.state.value.inspection?.questions?.single()?.reason)
        assertEquals(SaveStatus.Saved(200), viewModel.state.value.saveStatus)
    }

    @Test fun clearingInlineFindingPersistsIncompleteWorkingState() = runTest {
        val repository = MutableInspectionRepository(issueDraft())
        val viewModel = ServiceLoopViewModel(repository) {}
        viewModel.loadInspection("work-1")

        viewModel.requestResponseChange("check-1", ResponseDisposition.ISSUE_FOUND, reason = "")

        assertEquals(1, repository.saveCount)
        assertEquals(null, viewModel.state.value.inspection?.questions?.single()?.reason)
    }

    @Test fun workTabsUseTypedNonDefaultRepresentations() {
        assertEquals("Due services", com.v16studio.serviceloop.ui.WorkTab.DUE_SERVICES.label)
        assertEquals("Visits", com.v16studio.serviceloop.ui.WorkTab.VISITS.label)
        assertEquals("Follow-ups", com.v16studio.serviceloop.ui.WorkTab.FOLLOW_UPS.label)
        assertEquals("work?tab=DUE_SERVICES", com.v16studio.serviceloop.ui.workRoute(com.v16studio.serviceloop.ui.WorkTab.DUE_SERVICES))
        assertEquals("work?tab=VISITS&filter=WORKING", com.v16studio.serviceloop.ui.workRoute(com.v16studio.serviceloop.ui.WorkTab.VISITS, "WORKING"))
    }

    @Test fun releaseFactoryIsNoOpAndProductionSourceContainsNoFixtureCustomer() {
        val releaseFactory = File("src/release/java/com/v16studio/serviceloop/FixtureSeederFactory.kt").readText()
        val productionSources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertTrue(releaseFactory.contains("NoOpStartupSeeder"))
        assertFalse(releaseFactory.contains("DebugFixtureSeeder"))
        assertFalse(productionSources.contains("Harbor Fitness and Rehabilitation Cooperative"))
    }

    private fun inspectionDraft() = InspectionDraft(
        "work-1", "visit-1", "V-001", "Captured site", "Captured machine", "EQ-001", "Inspection",
        "2026-09-01", "Every 3 months", 2, "Public", "Private", false, null, null, 100,
        listOf(InspectionQuestion("response-1", "check-1", 1, "Guard", "STATUS", null, true, ResponseDisposition.NOT_CHECKED, null, null, null)),
    )

    private fun issueDraft() = inspectionDraft().copy(
        questions = listOf(inspectionDraft().questions.single().copy(disposition = ResponseDisposition.ISSUE_FOUND, reason = "Fraying edge")),
    )

    private fun responseDraft(disposition: ResponseDisposition, reason: String? = null) = inspectionDraft().copy(
        questions = listOf(inspectionDraft().questions.single().copy(disposition = disposition, reason = reason)),
    )

    private fun valueDraft(value: String) = inspectionDraft().copy(
        questions = listOf(inspectionDraft().questions.single().copy(responseType = "NUMBER", disposition = ResponseDisposition.VALUE, numberValue = value)),
    )

    private class MutableInspectionRepository(private var draft: InspectionDraft) : ServiceLoopRepository {
        var saveCount = 0

        override suspend fun home() = HomeSummary(null, null, null, null, null, null, null, 0, null, null, 0, 0)
        override suspend fun equipment(id: String): EquipmentDetail? = null
        override suspend fun equipmentList(): List<EquipmentSummary> = emptyList()
        override suspend fun customerList(): List<CustomerSummary> = emptyList()
        override suspend fun inspection(workItemId: String): InspectionDraft = draft
        override suspend fun completionLines(visitId: String): List<CompletionLine> = emptyList()
        override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?): Long {
            saveCount++
            draft = draft.copy(
                modifiedAtEpochMillis = 200,
                questions = draft.questions.map { question ->
                    if (question.snapshotItemId == questionId) question.copy(
                        disposition = disposition,
                        textValue = if (question.responseType == "TEXT" && disposition == ResponseDisposition.VALUE) value else null,
                        numberValue = if (question.responseType == "NUMBER" && disposition == ResponseDisposition.VALUE) value else null,
                        reason = if (disposition == ResponseDisposition.ISSUE_FOUND || disposition == ResponseDisposition.NOT_APPLICABLE) reason?.trim()?.takeIf(String::isNotEmpty) else null,
                    ) else question
                },
            )
            return 200
        }
    }

    private class RootRefreshRepository(private var draft: InspectionDraft) : ServiceLoopRepository {
        var homeReads = 0
        var equipmentReads = 0
        var customerReads = 0
        var nextSavedAt = 100L
        var refreshGate: CompletableDeferred<Unit>? = null
        var failRefresh = false
        override suspend fun home(): HomeSummary {
            homeReads++
            if (homeReads > 1) refreshGate?.await()
            if (homeReads > 1 && failRefresh) error("root refresh failed")
            return HomeSummary("visit-1", "V-001", "Site", nextSavedAt, "work-1", null, null, 0, null, null, 0, 0)
        }
        override suspend fun equipment(id: String): EquipmentDetail? = null
        override suspend fun equipmentList(): List<EquipmentSummary> {
            equipmentReads++
            return listOf(EquipmentSummary("equipment-1", "Machine", "EQ-1", null, "Site", "Customer", null))
        }
        override suspend fun customerList(): List<CustomerSummary> {
            customerReads++
            return listOf(CustomerSummary("customer-1", "Customer", "CU-1", 1, 1))
        }
        override suspend fun inspection(workItemId: String): InspectionDraft = draft
        override suspend fun completionLines(visitId: String): List<CompletionLine> = emptyList()
        override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?): Long {
            draft = draft.copy(modifiedAtEpochMillis = 200, questions = draft.questions.map { if (it.snapshotItemId == questionId) it.copy(disposition = disposition) else it })
            nextSavedAt = 200L
            return 200L
        }
    }

    private class RefreshFailAfterWriteRepository(private val draft: InspectionDraft, private val failProfileWrite: Boolean = false) : ServiceLoopRepository {
        private var written = false
        override suspend fun home() = HomeSummary(null, null, null, null, null, null, null, 0, null, null, 0, 0)
        override suspend fun equipment(id: String): EquipmentDetail? = null
        override suspend fun equipmentList(): List<EquipmentSummary> = emptyList()
        override suspend fun customerList(): List<CustomerSummary> = emptyList()
        override suspend fun inspection(workItemId: String): InspectionDraft { if (written) error("refresh failed"); return draft }
        override suspend fun completionLines(visitId: String): List<CompletionLine> { if (written) error("refresh failed"); return emptyList() }
        override suspend fun businessProfile(): BusinessProfile? { if (written) error("refresh failed"); return BusinessProfile("Business", "Technician", zoneId = "Europe/Bucharest", modifiedAtEpochMillis = 75) }
        override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?) = 200L
        override suspend fun savePublicWork(workItemId: String, text: String): Long { written = true; return 200 }
        override suspend fun markChecklistReviewed(workItemId: String): Long { written = true; return 200 }
        override suspend fun saveCompletionDraft(workItemId: String, outcome: String?, fulfills: Boolean, reason: String?, nextDue: String?, calculated: Boolean?, overrideReason: String?): Long { written = true; return 200 }
        override suspend fun saveBusinessProfile(profile: BusinessProfile): Long { if (failProfileWrite) error("profile write failed"); written = true; return 200 }
    }

    private class CheckpointRepository(
        private val draft: InspectionDraft,
        private val draftWriteResults: MutableList<Long?> = mutableListOf(),
        private val responseWriteResults: MutableList<Long?> = mutableListOf(),
        private val profileWriteResults: MutableList<Long?> = mutableListOf(),
    ) : ServiceLoopRepository {
        override suspend fun home() = HomeSummary(null, null, null, null, null, null, null, 0, null, null, 0, 0)
        override suspend fun equipment(id: String): EquipmentDetail? = null
        override suspend fun equipmentList(): List<EquipmentSummary> = emptyList()
        override suspend fun customerList(): List<CustomerSummary> = emptyList()
        override suspend fun inspection(workItemId: String) = draft
        override suspend fun completionLines(visitId: String): List<CompletionLine> = emptyList()
        override suspend fun businessProfile() = BusinessProfile("Business", "Technician", zoneId = "Europe/Bucharest", modifiedAtEpochMillis = 75)
        override suspend fun savePublicWork(workItemId: String, text: String) = next(draftWriteResults, "draft")
        override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?) = next(responseWriteResults, "response")
        override suspend fun saveBusinessProfile(profile: BusinessProfile) = next(profileWriteResults, "profile")

        private fun next(results: MutableList<Long?>, operation: String): Long =
            results.removeAt(0) ?: error("$operation write failed")
    }
}
