package com.v16studio.serviceloop.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.v16studio.serviceloop.AppContainer
import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.domain.CompletionLine
import com.v16studio.serviceloop.domain.CustomerSummary
import com.v16studio.serviceloop.domain.EquipmentDetail
import com.v16studio.serviceloop.domain.HomeSummary
import com.v16studio.serviceloop.domain.EquipmentSummary
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.domain.SaveStatus
import com.v16studio.serviceloop.domain.BusinessProfile
import com.v16studio.serviceloop.domain.FinalRecordDetail
import com.v16studio.serviceloop.domain.FinalizeResult
import com.v16studio.serviceloop.domain.VisitSummary
import com.v16studio.serviceloop.report.ReportService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = true,
    val home: HomeSummary? = null,
    val equipment: EquipmentDetail? = null,
    val equipmentList: List<EquipmentSummary> = emptyList(),
    val customerList: List<CustomerSummary> = emptyList(),
    val inspection: InspectionDraft? = null,
    val completionLines: List<CompletionLine> = emptyList(),
    val visits: List<VisitSummary> = emptyList(),
    val businessProfile: BusinessProfile? = null,
    val visitReportIdentity: BusinessProfile? = null,
    val finalRecord: FinalRecordDetail? = null,
    val finalizedRecordId: String? = null,
    val finalizing: Boolean = false,
    val generatingReport: Boolean = false,
    val saveStatus: SaveStatus = SaveStatus.Idle,
    val pendingResponseTransition: PendingResponseTransition? = null,
    val error: String? = null,
    val rootDataReady: Boolean = false,
    val rootRefreshError: String? = null,
    val contentRefreshError: String? = null,
)

data class PendingResponseTransition(
    val questionId: String,
    val disposition: ResponseDisposition,
    val value: String?,
    val reason: String?,
    val detailBeingDiscarded: String,
)

class ServiceLoopViewModel(
    private val repository: ServiceLoopRepository,
    private val reportService: ReportService? = null,
    private val startup: suspend () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var rootRefreshJob: Job? = null

    init { loadInitialRootData() }

    private fun loadInitialRootData() = launchLoad {
        val home = repository.home()
        val equipmentList = repository.equipmentList()
        val customerList = repository.customerList()
        val visits = repository.visits()
        _state.value = _state.value.copy(
            home = home,
            equipmentList = equipmentList,
            customerList = customerList,
            visits = visits,
            rootDataReady = true,
        )
    }

    internal fun refreshRootDataNonBlocking() {
        if (!_state.value.rootDataReady) return
        rootRefreshJob?.cancel()
        rootRefreshJob = viewModelScope.launch {
            try {
                val home = repository.home()
                val equipmentList = repository.equipmentList()
                val customerList = repository.customerList()
                val visits = repository.visits()
                ensureActive()
                _state.value = _state.value.copy(
                    home = home,
                    equipmentList = equipmentList,
                    customerList = customerList,
                    visits = visits,
                    rootRefreshError = null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(rootRefreshError = failure.message ?: "Unable to refresh saved data")
            }
        }
    }

    fun loadEquipment(id: String) = launchLoad { _state.value = _state.value.copy(equipment = repository.equipment(id)) }

    fun loadInspection(id: String) = launchLoad {
        val draft = repository.inspection(id)
        _state.value = _state.value.copy(
            inspection = draft,
            saveStatus = draft?.let { SaveStatus.Saved(it.modifiedAtEpochMillis) } ?: SaveStatus.Idle,
        )
    }

    fun loadCompletion(visitId: String) = launchLoad {
        _state.value = _state.value.copy(completionLines = repository.completionLines(visitId), businessProfile = repository.businessProfile(), visitReportIdentity = repository.visitReportIdentity(visitId))
    }

    fun loadVisits() = launchLoad { _state.value = _state.value.copy(visits = repository.visits()) }
    fun loadBusinessProfile() = launchLoad { _state.value = _state.value.copy(businessProfile = repository.businessProfile()) }
    fun loadFinalRecord(id: String) { _state.value = _state.value.copy(finalRecord = null); launchLoad { _state.value = _state.value.copy(finalRecord = repository.finalRecord(id)) } }
    fun consumeFinalizedNavigation() { _state.value = _state.value.copy(finalizedRecordId = null) }

    fun savePublicWork(workItemId: String, text: String) = persistDraft({ repository.savePublicWork(workItemId, text) }) { _state.value = _state.value.copy(inspection = repository.inspection(workItemId)) }
    fun markChecklistReviewed(workItemId: String) = persistDraft({ repository.markChecklistReviewed(workItemId) }) { _state.value = _state.value.copy(inspection = repository.inspection(workItemId)) }
    fun saveBusinessProfile(profile: BusinessProfile) = persistDraft({ repository.saveBusinessProfile(profile) }) { _state.value = _state.value.copy(businessProfile = repository.businessProfile()) }
    fun refreshVisitReportIdentity(visitId: String) = persistDraft({ repository.refreshVisitReportIdentity(visitId) }) { _state.value = _state.value.copy(visitReportIdentity = repository.visitReportIdentity(visitId)) }
    fun saveCompletion(workItemId: String, outcome: String?, fulfills: Boolean, reason: String?, nextDue: String?, calculated: Boolean?, overrideReason: String?, visitId: String) = persistDraft({
        repository.saveCompletionDraft(workItemId, outcome, fulfills, reason, nextDue, calculated, overrideReason)
    }) { _state.value = _state.value.copy(completionLines = repository.completionLines(visitId)) }

    fun finalizeVisit(visitId: String) {
        if (_state.value.finalizing) return
        _state.value = _state.value.copy(finalizing = true, error = null)
        viewModelScope.launch {
            try {
                when (val result = repository.finalizeVisit(visitId)) {
                    is FinalizeResult.Success -> {
                        _state.value = _state.value.copy(finalizing = false, finalizedRecordId = result.recordId)
                        refreshRootDataNonBlocking()
                    }
                    is FinalizeResult.Blocked -> _state.value = _state.value.copy(finalizing = false, error = result.message)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _state.value = _state.value.copy(finalizing = false, error = failure.message ?: "Finalization failed") }
        }
    }

    fun generateReport(recordId: String) {
        val service = reportService ?: return
        if (_state.value.generatingReport) return
        _state.value = _state.value.copy(generatingReport = true, error = null)
        viewModelScope.launch {
            try {
                val rendition = service.generate(recordId)
                _state.value = _state.value.copy(generatingReport = false, finalRecord = _state.value.finalRecord?.copy(report = rendition))
                try { _state.value = _state.value.copy(finalRecord = repository.finalRecord(recordId), contentRefreshError = null) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { _state.value = _state.value.copy(contentRefreshError = failure.message ?: "Report was generated, but the screen could not refresh") }
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _state.value = _state.value.copy(generatingReport = false, error = failure.message ?: "PDF generation failed") }
        }
    }

    private fun persistDraft(write: suspend () -> Long, refresh: suspend () -> Unit = {}) {
        val lastSaved = (_state.value.saveStatus as? SaveStatus.Saved)?.atEpochMillis
        _state.value = _state.value.copy(saveStatus = SaveStatus.Saving, error = null)
        viewModelScope.launch {
            try {
                val savedAt = write()
                _state.value = _state.value.copy(saveStatus = SaveStatus.Saved(savedAt))
                try { refresh(); _state.value = _state.value.copy(contentRefreshError = null) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { _state.value = _state.value.copy(contentRefreshError = failure.message ?: "Saved, but the screen could not refresh") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _state.value = _state.value.copy(saveStatus = SaveStatus.Failed(failure.message ?: "Not saved", lastSaved)) }
        }
    }

    fun requestResponseChange(questionId: String, disposition: ResponseDisposition, value: String? = null, reason: String? = null) {
        val draft = _state.value.inspection ?: return
        val question = draft.questions.firstOrNull { it.snapshotItemId == questionId } ?: return
        if (question.semanticallyMatches(disposition, value, reason)) return
        val discardedDetail = when {
            question.disposition == disposition -> null
            question.disposition == ResponseDisposition.ISSUE_FOUND && !question.reason.isNullOrBlank() -> "saved issue detail"
            question.disposition == ResponseDisposition.NOT_APPLICABLE && !question.reason.isNullOrBlank() -> "saved not-applicable reason"
            question.disposition == ResponseDisposition.VALUE && !question.textValue.isNullOrBlank() -> "saved text response"
            question.disposition == ResponseDisposition.VALUE && !question.numberValue.isNullOrBlank() -> "saved numeric response"
            else -> null
        }
        if (discardedDetail != null) {
            _state.value = _state.value.copy(
                pendingResponseTransition = PendingResponseTransition(questionId, disposition, value, reason, discardedDetail),
            )
            return
        }
        persistResponse(draft, questionId, disposition, value, reason)
    }

    private fun com.v16studio.serviceloop.domain.InspectionQuestion.semanticallyMatches(
        requestedDisposition: ResponseDisposition,
        requestedValue: String?,
        requestedReason: String?,
    ): Boolean {
        if (disposition != requestedDisposition) return false
        return when (requestedDisposition) {
            ResponseDisposition.VALUE -> (if (responseType == "NUMBER") numberValue else textValue) == requestedValue
            ResponseDisposition.ISSUE_FOUND -> reason.orEmpty() == requestedReason.orEmpty()
            else -> true
        }
    }

    fun cancelResponseTransition() {
        _state.value = _state.value.copy(pendingResponseTransition = null)
    }

    fun confirmResponseTransition() {
        val transition = _state.value.pendingResponseTransition ?: return
        val draft = _state.value.inspection ?: return
        _state.value = _state.value.copy(pendingResponseTransition = null)
        persistResponse(draft, transition.questionId, transition.disposition, transition.value, transition.reason)
    }

    private fun persistResponse(draft: InspectionDraft, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?) {
        val lastSaved = (state.value.saveStatus as? SaveStatus.Saved)?.atEpochMillis ?: draft.modifiedAtEpochMillis
        _state.value = _state.value.copy(saveStatus = SaveStatus.Saving, error = null)
        viewModelScope.launch {
            try {
                val savedAt = repository.saveResponse(draft.workItemId, questionId, disposition, value, reason)
                _state.value = _state.value.copy(saveStatus = SaveStatus.Saved(savedAt))
                try { _state.value = _state.value.copy(inspection = repository.inspection(draft.workItemId), contentRefreshError = null) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { _state.value = _state.value.copy(contentRefreshError = failure.message ?: "Saved, but the screen could not refresh") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(
                    saveStatus = SaveStatus.Failed(failure.message ?: "Draft write failed", lastSaved),
                )
            }
        }
    }

    private fun launchLoad(block: suspend () -> Unit) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                startup()
                block()
                _state.value = _state.value.copy(loading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(loading = false, error = failure.message ?: "Unable to read saved data")
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ServiceLoopViewModel(container.repository, container.reportService) { container.startup.await() } as T
    }
}
