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
import com.v16studio.serviceloop.domain.*
import com.v16studio.serviceloop.report.ReportService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed interface DueServicesProjection {
    data object Unresolved : DueServicesProjection
    data class Available(val rows: List<DueService>, val updateError: String? = null) : DueServicesProjection
    data class Unavailable(val message: String) : DueServicesProjection
}

data class UiState(
    val loading: Boolean = true,
    val home: HomeSummary? = null,
    val equipment: EquipmentDetail? = null,
    val equipmentList: List<EquipmentSummary> = emptyList(),
    val customerList: List<CustomerSummary> = emptyList(),
    val siteList: List<SiteRegisterSummary> = emptyList(),
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
    val businessProfileSaveStatus: SaveStatus = SaveStatus.Idle,
    val pendingResponseTransition: PendingResponseTransition? = null,
    val error: String? = null,
    val rootDataReady: Boolean = false,
    val rootRefreshError: String? = null,
    val contentRefreshError: String? = null,
    val customer: CustomerDetail? = null,
    val site: SiteDetail? = null,
    val plan: PlanDetail? = null,
    val dueServicesProjection: DueServicesProjection = DueServicesProjection.Unresolved,
    val visitSites: List<VisitSiteOption> = emptyList(),
    val templates: List<TemplateSummary> = emptyList(),
    val template: TemplateDetail? = null,
    val visit: VisitDetail? = null,
    val followUps: List<FollowUpDetail> = emptyList(),
    val followUp: FollowUpDetail? = null,
    val parts: List<PartEntry> = emptyList(),
    val photos: List<PhotoEntry> = emptyList(),
    val searchResults: List<SearchTarget> = emptyList(),
    val operationInProgress: Boolean = false,
    val operationMessage: String? = null,
    val inspectionFocus: InspectionFocus? = null,
    val history: List<HistoryEntry> = emptyList(),
    val attention: List<AttentionItem> = emptyList(),
    val recordVersions: List<RecordVersionSummary> = emptyList(),
    val reportVersions: List<ReportVersionSummary> = emptyList(),
    val correction: CorrectionDraft? = null,
    val lifecycleReview: LifecycleReview? = null,
    val moveReview: MoveReview? = null,
    val datasetSummary: DatasetSummary? = null,
    val backupResult: BackupResult? = null,
    val backupInspection: BackupInspection? = null,
    val csvPreview: CsvImportPreview? = null,
    val importResult: ImportResult? = null,
    val exportBytes: ByteArray? = null,
) {
    val dueServices: List<DueService>
        get() = (dueServicesProjection as? DueServicesProjection.Available)?.rows.orEmpty()
    val dueServicesReady: Boolean
        get() = dueServicesProjection is DueServicesProjection.Available
    val dueServicesError: String?
        get() = when (val projection = dueServicesProjection) {
            is DueServicesProjection.Available -> projection.updateError
            is DueServicesProjection.Unavailable -> projection.message
            DueServicesProjection.Unresolved -> null
        }
}

data class InspectionFocus(val kind: CompletionBlockerKind, val questionId: String? = null)

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
    private var searchJob: Job? = null

    init {
        observeDueServices()
        loadInitialRootData()
    }

    private fun loadInitialRootData() = launchLoad {
        val home = repository.home()
        val equipmentList = repository.equipmentList()
        val customerList = repository.customerList()
        val siteList = repository.siteList()
        val visits = repository.visits()
        _state.value = _state.value.copy(
            home = home,
            equipmentList = equipmentList,
            customerList = customerList,
            siteList = siteList,
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
                val siteList = repository.siteList()
                val visits = repository.visits()
                ensureActive()
                _state.value = _state.value.copy(
                    home = home,
                    equipmentList = equipmentList,
                    customerList = customerList,
                    siteList = siteList,
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
    fun focusInspection(kind: CompletionBlockerKind, questionId: String?) { _state.value = _state.value.copy(inspectionFocus = InspectionFocus(kind, questionId)) }
    fun clearInspectionFocus() { _state.value = _state.value.copy(inspectionFocus = null) }

    fun loadCompletion(visitId: String) = launchLoad {
        _state.value = _state.value.copy(completionLines = repository.completionLines(visitId), businessProfile = repository.businessProfile(), visitReportIdentity = repository.visitReportIdentity(visitId))
    }

    fun loadVisits() = launchLoad { _state.value = _state.value.copy(visits = repository.visits()) }
    fun loadCustomer(id: String) = launchLoad { _state.value = _state.value.copy(customer = repository.customer(id)) }
    fun loadSite(id: String) = launchLoad { _state.value = _state.value.copy(site = repository.site(id)) }
    fun loadPlan(id: String) = launchLoad { _state.value = _state.value.copy(plan = repository.plan(id), templates = repository.templates()) }
    fun loadVisitSetup() {
        launchLoad { _state.value = _state.value.copy(visitSites = repository.visitSites()) }
    }

    private fun observeDueServices() {
        viewModelScope.launch {
            try {
                startup()
                repository.observeDueServices().collect { loaded ->
                    _state.value = _state.value.copy(
                        dueServicesProjection = DueServicesProjection.Available(loaded),
                    )
                }
            } catch (cancelled: CancellationException) {
                if (!currentCoroutineContext().isActive) throw cancelled
                settleDueServicesFailure(cancelled)
            } catch (failure: Exception) {
                settleDueServicesFailure(failure)
            }
        }
    }

    private fun settleDueServicesFailure(failure: Throwable) {
        val message = failure.message ?: "Unable to read due services"
        val projection = _state.value.dueServicesProjection
        _state.value = _state.value.copy(
            dueServicesProjection = when (projection) {
                is DueServicesProjection.Available -> projection.copy(updateError = message)
                is DueServicesProjection.Unavailable, DueServicesProjection.Unresolved -> DueServicesProjection.Unavailable(message)
            },
        )
    }
    fun loadTemplates() = launchLoad { _state.value = _state.value.copy(templates = repository.templates()) }
    fun loadTemplate(id: String) = launchLoad { _state.value = _state.value.copy(template = repository.template(id)) }
    fun loadVisit(id: String) = launchLoad { val visit=repository.visit(id); _state.value = _state.value.copy(visit = visit, site = visit?.let { repository.site(it.siteId) }) }
    fun loadFollowUps() = launchLoad { _state.value = _state.value.copy(followUps = repository.followUps()) }
    fun loadFollowUp(id: String) = launchLoad { _state.value = _state.value.copy(followUp = repository.followUp(id)) }
    fun loadFieldEvidence(workItemId: String) = launchLoad { _state.value = _state.value.copy(parts = repository.parts(workItemId), photos = repository.photos(workItemId)) }

    fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try { val results = repository.search(query); ensureActive(); _state.value = _state.value.copy(searchResults = results, error = null) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _state.value = _state.value.copy(error = failure.message ?: "Search failed") }
        }
    }

    fun createCustomer(input: CustomerInput, onSuccess: (String) -> Unit) = runOperation({ repository.createCustomer(input) }, onSuccess)
    fun updateCustomer(id: String, input: CustomerInput, onSuccess: (String) -> Unit) = runOperation({ repository.updateCustomer(id, input); id }, onSuccess)
    fun createSite(customerId: String, input: SiteInput, onSuccess: (String) -> Unit) = runOperation({ repository.createSite(customerId, input) }, onSuccess)
    fun updateSite(id: String, input: SiteInput, onSuccess: (String) -> Unit) = runOperation({ repository.updateSite(id, input); id }, onSuccess)
    fun createEquipment(siteId: String, input: EquipmentInput, onSuccess: (String) -> Unit) = runOperation({ repository.createEquipment(siteId, input) }, onSuccess)
    fun updateEquipment(id: String, input: EquipmentInput, onSuccess: (String) -> Unit) = runOperation({ repository.updateEquipment(id, input); id }, onSuccess)
    fun createPlan(equipmentId: String, input: PlanInput, onSuccess: (String) -> Unit) = runOperation({ repository.createPlan(equipmentId, input) }, onSuccess)
    fun updatePlan(id: String, input: PlanInput, onSuccess: (String) -> Unit) = runOperation({ repository.updatePlan(id, input); id }, onSuccess)
    fun createTemplate(name: String, items: List<TemplateItemDraft>, onSuccess: (String) -> Unit) = runOperation({ repository.createTemplate(name, items) }, onSuccess)
    fun reviseTemplate(id: String, name: String, items: List<TemplateItemDraft>, onSuccess: (String) -> Unit) = runOperation({ repository.reviseTemplate(id, name, items); id }, onSuccess)
    fun createVisit(planIds: List<String>, state: String, date: String, scheduledAt: Long?, onSuccess: (String) -> Unit) = runOperation({ repository.createVisit(planIds, state, date, scheduledAt) }, onSuccess)
    fun createVisitForSite(siteId: String, planIds: List<String>, oneOffEquipmentId: String?, oneOffName: String?, state: String, date: String, scheduledAt: Long?, onSuccess: (String) -> Unit) = runOperation({ repository.createVisitForSite(siteId, planIds, oneOffEquipmentId, oneOffName, state, date, scheduledAt) }, onSuccess)
    fun startVisit(id: String, onSuccess: (String) -> Unit) = runOperation({ repository.startVisit(id); id }, onSuccess)
    fun rescheduleVisit(id: String, date: String, scheduledAt: Long?, reason: String, onSuccess: (String) -> Unit) {
        if (_state.value.operationInProgress) return
        _state.value = _state.value.copy(operationInProgress = true, operationMessage = null, error = null)
        viewModelScope.launch {
            try {
                repository.rescheduleVisit(id, date, scheduledAt, reason)
                val refreshed = repository.visit(id) ?: error("Visit was saved but could not be reloaded")
                _state.value = _state.value.copy(visit = refreshed, site = repository.site(refreshed.siteId), operationInProgress = false, operationMessage = "Saved on this device")
                refreshRootDataNonBlocking(); onSuccess(id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _state.value = _state.value.copy(operationInProgress = false, error = failure.message ?: "Not saved") }
        }
    }
    fun cancelVisit(id: String, reason: String, onSuccess: (String) -> Unit) = runOperation({ repository.cancelVisit(id, reason); id }, onSuccess)
    fun restoreVisit(id: String, date: String, onSuccess: (String) -> Unit) = runOperation({ repository.restoreVisit(id, date); id }, onSuccess)
    fun addOneOff(visitId: String, equipmentId: String, name: String) = runOperation({ repository.addOneOffWork(visitId, equipmentId, name) }) { loadVisit(visitId) }
    fun addPart(workItemId: String, description: String, quantity: String, unit: String) = runOperation({ repository.addPart(workItemId, description, quantity, unit) }) { loadFieldEvidence(workItemId) }
    fun savePhoto(workItemId: String, bytes: ByteArray, displayName: String?, mimeType: String, include: Boolean, caption: String?) = runOperation({ withContext(Dispatchers.IO) { repository.savePhoto(workItemId, bytes, displayName, mimeType, include, caption) } }) { loadFieldEvidence(workItemId) }
    fun reportOperationFailure(message:String) { _state.value=_state.value.copy(operationInProgress=false,error=message,operationMessage=null) }
    fun createContactNote(input: ContactNoteInput, onSuccess: (String) -> Unit = {}) = runOperation({ repository.createContactNote(input) }, onSuccess)
    fun markContactNoteEnteredInError(id:String,reason:String,onSuccess:(String)->Unit={})=runOperation({repository.markContactNoteEnteredInError(id,reason);id},onSuccess)
    fun createFollowUp(input: FollowUpInput, onSuccess: (String) -> Unit) = runOperation({ repository.createFollowUp(input) }, onSuccess)
    fun createCorrectiveFollowUp(workItemId: String, title: String, dueDate: String, privateNote: String, onSuccess: (String) -> Unit = {}) = runOperation({ repository.createCorrectiveFollowUp(workItemId, title, dueDate, privateNote) }, onSuccess)
    fun updateFollowUp(id:String,title:String,dueDate:String,note:String,reason:String,onSuccess:(String)->Unit={})=runOperation({repository.updateFollowUp(id,title,dueDate,note,reason);id},onSuccess)
    fun changeFollowUpState(id: String, target: String, reason: String, newDue: String?, onSuccess: (String) -> Unit = {}) = runOperation({ repository.changeFollowUpState(id, target, reason, newDue); id }, onSuccess)
    fun loadBusinessProfile() = launchLoad {
        val profile = repository.businessProfile()
        _state.value = _state.value.copy(businessProfile = profile, businessProfileSaveStatus = profile?.modifiedAtEpochMillis?.let { SaveStatus.Saved(it) } ?: SaveStatus.Idle)
    }
    fun loadFinalRecord(id: String) { _state.value = _state.value.copy(finalRecord = null); launchLoad { _state.value = _state.value.copy(finalRecord = repository.finalRecord(id)) } }
    fun loadHistory(query: HistoryQuery) = launchLoad { _state.value = _state.value.copy(history = repository.history(query)) }
    fun loadAttention() = launchLoad { _state.value = _state.value.copy(attention = repository.attention()) }
    fun loadRecordVersions(id: String) = launchLoad { val versions = repository.recordVersions(id); _state.value = _state.value.copy(recordVersions = versions.first, reportVersions = versions.second) }
    fun loadCorrection(recordId: String) = launchLoad { _state.value = _state.value.copy(correction = repository.openCorrection(recordId)) }
    fun saveCorrection(value: CorrectionDraft) = runOperation({ repository.saveCorrection(value) }) { loadCorrection(value.recordId) }
    fun commitCorrection(recordId: String, onSuccess: (String) -> Unit) = runOperation({ repository.commitCorrection(recordId) }, onSuccess)
    fun discardCorrection(recordId: String, onSuccess: (String) -> Unit) = runOperation({ repository.discardCorrection(recordId); recordId }, onSuccess)
    fun voidRecord(recordId: String, publicReason: String, privateReason: String, onSuccess: (String) -> Unit) = runOperation({ repository.voidRecord(recordId, publicReason, privateReason); recordId }, onSuccess)
    fun loadLifecycle(subjectType: String, id: String, action: String) = launchLoad { _state.value = _state.value.copy(lifecycleReview = repository.lifecycleReview(subjectType, id, action)) }
    fun applyLifecycle(subjectType: String, id: String, action: String, reason: String, onSuccess: (String) -> Unit) = runOperation({ repository.applyLifecycle(subjectType, id, action, reason); id }, onSuccess)
    fun loadMove(equipmentId: String) = launchLoad { _state.value = _state.value.copy(moveReview = repository.moveReview(equipmentId)) }
    fun moveEquipment(equipmentId: String, destination: String, date: String, reason: String, acknowledged: Boolean, onSuccess: (String) -> Unit) = runOperation({ repository.moveEquipment(equipmentId, destination, date, reason, acknowledged); equipmentId }, onSuccess)
    fun loadDatasetSummary() = launchLoad { _state.value = _state.value.copy(datasetSummary = repository.datasetSummary(), attention = repository.attention()) }
    fun setBackupReminder(days: Int) = runOperation({ repository.setBackupReminder(days); days }) { loadDatasetSummary() }
    fun createBackup(passphrase: CharArray, incomplete: Boolean) = runOperation({ repository.createBackup(passphrase, incomplete) }) { result -> _state.value = _state.value.copy(backupResult = result) }
    fun verifyWrittenBackup(bytes: ByteArray, passphrase: CharArray, destination: String) = runOperation({ val inspection = repository.inspectBackup(bytes, passphrase); val result = _state.value.backupResult ?: error("Prepared backup is unavailable"); require(inspection.snapshotAtEpochMillis == result.snapshotAtEpochMillis); if (result.complete) repository.recordVerifiedBackup(result, destination); inspection }) { inspection -> _state.value = _state.value.copy(backupInspection = inspection); loadDatasetSummary() }
    fun inspectBackup(bytes: ByteArray, passphrase: CharArray) = runOperation({ repository.inspectBackup(bytes, passphrase) }) { _state.value = _state.value.copy(backupInspection = it) }
    fun restoreBackup(confirmation: String, incompleteAcknowledged: Boolean, onSuccess: () -> Unit) { val inspection = _state.value.backupInspection ?: return; runOperation({ repository.restoreBackup(inspection, confirmation, incompleteAcknowledged); true }) { onSuccess() } }
    fun prepareDirectoryCsv(includeInactive: Boolean, includePrivate: Boolean) = runOperation({ repository.directoryCsv(includeInactive, includePrivate) }) { bytes -> _state.value = _state.value.copy(exportBytes = bytes) }
    fun prepareRecordsCsv(includeInactive: Boolean, includePrivate: Boolean, previous: Boolean) = runOperation({ repository.recordsCsvPackage(includeInactive, includePrivate, previous) }) { bytes -> _state.value = _state.value.copy(exportBytes = bytes) }
    fun validateCsv(bytes: ByteArray) = runOperation({ repository.validateDirectoryCsv(bytes) }) { _state.value = _state.value.copy(csvPreview = it, importResult = null) }
    fun importCsv(createSeparate: Set<String> = emptySet(), skipped: Set<String> = emptySet()) { val preview = _state.value.csvPreview ?: return; runOperation({ repository.importDirectory(preview, createSeparate, skipped) }) { _state.value = _state.value.copy(importResult = it); refreshRootDataNonBlocking() } }
    fun erase(acknowledged: Boolean, confirmation: String, onSuccess: () -> Unit) = runOperation({ repository.erase(acknowledged, confirmation); true }) { onSuccess() }
    fun consumeFinalizedNavigation() { _state.value = _state.value.copy(finalizedRecordId = null) }

    fun savePublicWork(workItemId: String, text: String) = persistDraft({ repository.savePublicWork(workItemId, text) }) { _state.value = _state.value.copy(inspection = repository.inspection(workItemId)) }
    fun markChecklistReviewed(workItemId: String) = persistDraft({ repository.markChecklistReviewed(workItemId) }) { _state.value = _state.value.copy(inspection = repository.inspection(workItemId)) }
    fun saveBusinessProfile(profile: BusinessProfile) {
        val lastSaved = _state.value.businessProfileSaveStatus.lastSavedCheckpoint() ?: _state.value.businessProfile?.modifiedAtEpochMillis
        _state.value = _state.value.copy(businessProfileSaveStatus = SaveStatus.Saving, error = null)
        viewModelScope.launch {
            try {
                val savedAt = repository.saveBusinessProfile(profile)
                _state.value = _state.value.copy(businessProfileSaveStatus = SaveStatus.Saved(savedAt))
                try { _state.value = _state.value.copy(businessProfile = repository.businessProfile(), contentRefreshError = null) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { _state.value = _state.value.copy(contentRefreshError = failure.message ?: "Profile was saved, but the screen could not refresh") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _state.value = _state.value.copy(businessProfileSaveStatus = SaveStatus.Failed(failure.message ?: "Profile not saved", lastSaved)) }
        }
    }
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
        val lastSaved = _state.value.saveStatus.lastSavedCheckpoint()
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

    private fun <T> runOperation(block: suspend () -> T, onSuccess: (T) -> Unit = {}) {
        if (_state.value.operationInProgress) return
        _state.value = _state.value.copy(operationInProgress = true, operationMessage = null, error = null)
        viewModelScope.launch {
            try { val id = block(); _state.value = _state.value.copy(operationInProgress = false, operationMessage = "Saved on this device"); refreshRootDataNonBlocking(); onSuccess(id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _state.value = _state.value.copy(operationInProgress = false, error = failure.message ?: "Not saved") }
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
            ResponseDisposition.VALUE -> (if (responseType == "NUMBER") numberValue else textValue) == requestedValue?.trim()
            ResponseDisposition.ISSUE_FOUND -> reason.orEmpty() == requestedReason?.trim().orEmpty()
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
        val lastSaved = state.value.saveStatus.lastSavedCheckpoint() ?: draft.modifiedAtEpochMillis
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

    private fun SaveStatus.lastSavedCheckpoint(): Long? = when (this) {
        is SaveStatus.Saved -> atEpochMillis
        is SaveStatus.Failed -> lastSavedAtEpochMillis
        else -> null
    }
}
