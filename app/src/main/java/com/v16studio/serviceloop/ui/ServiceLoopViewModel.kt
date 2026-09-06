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
import kotlinx.coroutines.CancellationException
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
    val saveStatus: SaveStatus = SaveStatus.Idle,
    val pendingResponseTransition: PendingResponseTransition? = null,
    val error: String? = null,
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
    private val startup: suspend () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refreshHome() }

    fun refreshHome() = launchLoad { _state.value = _state.value.copy(home = repository.home()) }

    fun loadEquipment(id: String) = launchLoad { _state.value = _state.value.copy(equipment = repository.equipment(id)) }

    fun loadEquipmentList() = launchLoad { _state.value = _state.value.copy(equipmentList = repository.equipmentList()) }

    fun loadCustomers() = launchLoad { _state.value = _state.value.copy(customerList = repository.customerList()) }

    fun loadInspection(id: String) = launchLoad {
        val draft = repository.inspection(id)
        _state.value = _state.value.copy(
            inspection = draft,
            saveStatus = draft?.let { SaveStatus.Saved(it.modifiedAtEpochMillis) } ?: SaveStatus.Idle,
        )
    }

    fun loadCompletion(visitId: String) = launchLoad {
        _state.value = _state.value.copy(completionLines = repository.completionLines(visitId))
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
                val refreshed = repository.inspection(draft.workItemId)
                _state.value = _state.value.copy(inspection = refreshed, saveStatus = SaveStatus.Saved(savedAt))
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
            ServiceLoopViewModel(container.repository) { container.startup.await() } as T
    }
}
