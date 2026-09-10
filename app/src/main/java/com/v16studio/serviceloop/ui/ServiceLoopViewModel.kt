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
import com.v16studio.serviceloop.reminders.ReminderCoordinator
import com.v16studio.serviceloop.domain.BusinessDateSignal
import com.v16studio.serviceloop.calendar.CalendarCoordinator
import com.v16studio.serviceloop.calendar.CalendarRuntimeState
import com.v16studio.serviceloop.calendar.VisitCalendarState
import com.v16studio.serviceloop.calendar.WritableCalendar
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

sealed interface DueServicesProjection {
    data object Unresolved : DueServicesProjection
    data class Available(val rows: List<DueService>, val updateError: String? = null) : DueServicesProjection
    data class Unavailable(val message: String) : DueServicesProjection
}

data class UiState(
    val loading: Boolean = true,
    val recoveryCheckComplete: Boolean = true,
    val restrictedRecoveryState: Boolean = false,
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
    val contactNote: ContactNoteDetail? = null,
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
    val reminderPreferences: ReminderPreferences? = null,
    val reminderRuntimeState: ReminderRuntimeState = ReminderRuntimeState(),
    val reminderSaveStatus: SaveStatus = SaveStatus.Idle,
    val businessDate: java.time.LocalDate = java.time.LocalDate.now(),
    val businessZoneId: String = java.time.ZoneId.systemDefault().id,
    val calendarRuntimeState: CalendarRuntimeState = CalendarRuntimeState(),
    val visitCalendarState: VisitCalendarState? = null,
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

class ServiceLoopViewModel(
    private val repository: ServiceLoopRepository,
    private val reportService: ReportService? = null,
    restrictedRecoveryState: Boolean = false,
    private val businessDateSignal: BusinessDateSignal? = null,
    private val reminderCoordinator: ReminderCoordinator? = null,
    private val calendarCoordinator: CalendarCoordinator? = null,
    private val startup: suspend () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(UiState(restrictedRecoveryState = restrictedRecoveryState, businessDate = businessDateSignal?.tokens?.value?.date ?: java.time.LocalDate.now(), businessZoneId = businessDateSignal?.tokens?.value?.zoneId?.id ?: java.time.ZoneId.systemDefault().id))
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var rootRefreshJob: Job? = null
    private var searchJob: Job? = null
    private val activeLoads = AtomicInteger(0)
    private val requestLock = Any()
    private val requestVersions = mutableMapOf<String, Long>()
    private var datasetGeneration = 0L

    private data class RequestToken(val key: String, val version: Long, val datasetGeneration: Long)

    private fun issueRequest(key: String): RequestToken = synchronized(requestLock) {
        val version = requestVersions.getOrDefault(key, 0L) + 1L
        requestVersions[key] = version
        RequestToken(key, version, datasetGeneration)
    }

    private fun isCurrent(token: RequestToken): Boolean = synchronized(requestLock) {
        requestVersions[token.key] == token.version && datasetGeneration == token.datasetGeneration
    }

    private fun advanceDatasetGeneration() = synchronized(requestLock) {
        datasetGeneration += 1L
        requestVersions.clear()
    }

    init {
        if (restrictedRecoveryState) loadDatasetSummary() else { observeDueServices(); observeRootInvalidations(); loadInitialRootData() }
        businessDateSignal?.let { signal -> viewModelScope.launch { signal.tokens.collect { token -> _state.update { it.copy(businessDate = token.date, businessZoneId = token.zoneId.id) }; refreshRootDataNonBlocking() } } }
    }

    private fun loadInitialRootData() = launchLoad {
        val home = repository.home()
        val equipmentList = repository.equipmentList()
        val customerList = repository.customerList()
        val siteList = repository.siteList()
        val visits = repository.visits()
        _state.update { current -> current.copy(
            home = home,
            equipmentList = equipmentList,
            customerList = customerList,
            siteList = siteList,
            visits = visits,
            rootDataReady = true,
            recoveryCheckComplete = true,
        ) }
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
                _state.update { current -> current.copy(
                    home = home,
                    equipmentList = equipmentList,
                    customerList = customerList,
                    siteList = siteList,
                    visits = visits,
                    rootRefreshError = null,
                ) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { it.copy(rootRefreshError = failure.message ?: "Unable to refresh saved data") }
            }
        }
    }

    fun loadEquipment(id: String) {
        val request = issueRequest("equipment")
        launchLoad { val value = repository.equipment(id); if (isCurrent(request)) _state.update { it.copy(equipment = value) } }
    }

    fun loadInspection(id: String) {
        val request = issueRequest("inspection")
        launchLoad {
            val draft = repository.inspection(id)
            if (!isCurrent(request)) return@launchLoad
            _state.update { current -> current.copy(
                inspection = draft,
                saveStatus = draft?.let { SaveStatus.Saved(it.modifiedAtEpochMillis) } ?: SaveStatus.Idle,
            ) }
        }
    }
    fun focusInspection(kind: CompletionBlockerKind, questionId: String?) { _state.update { it.copy(inspectionFocus = InspectionFocus(kind, questionId)) } }
    fun clearInspectionFocus() { _state.update { it.copy(inspectionFocus = null) } }

    fun loadCompletion(visitId: String) = launchLoad {
        val lines = repository.completionLines(visitId)
        val profile = repository.businessProfile()
        val identity = repository.visitReportIdentity(visitId)
        _state.update { it.copy(completionLines = lines, businessProfile = profile, visitReportIdentity = identity) }
    }

    fun loadVisits() = launchLoad { val values = repository.visits(); _state.update { it.copy(visits = values) } }
    fun loadCustomer(id: String) {
        val request = issueRequest("customer")
        launchLoad { val value = repository.customer(id); if (isCurrent(request)) _state.update { it.copy(customer = value) } }
    }
    fun loadSite(id: String) {
        val request = issueRequest("site")
        launchLoad { val value = repository.site(id); if (isCurrent(request)) _state.update { it.copy(site = value) } }
    }
    fun loadPlan(id: String) {
        val request = issueRequest("plan")
        launchLoad { val plan = repository.plan(id); val templates = repository.templates(); if (isCurrent(request)) _state.update { it.copy(plan = plan, templates = templates) } }
    }
    fun loadVisitSetup() {
        val request = issueRequest("visitSetup")
        launchLoad { val sites = repository.visitSites(); if (isCurrent(request)) _state.update { it.copy(visitSites = sites) } }
    }

    private fun observeDueServices() {
        viewModelScope.launch {
            try {
                startup()
                repository.observeDueServices().collect { loaded ->
                    _state.update { current -> current.copy(
                        dueServicesProjection = DueServicesProjection.Available(loaded),
                    ) }
                }
            } catch (cancelled: CancellationException) {
                if (!currentCoroutineContext().isActive) throw cancelled
                settleDueServicesFailure(cancelled)
            } catch (failure: Exception) {
                settleDueServicesFailure(failure)
            }
        }
    }

    private fun observeRootInvalidations() {
        viewModelScope.launch {
            try { startup(); repository.observeRootInvalidations().collect { refreshRootDataNonBlocking() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _state.update { it.copy(rootRefreshError = failure.message ?: "Unable to observe saved data") } }
        }
    }

    private fun settleDueServicesFailure(failure: Throwable) {
        val message = failure.message ?: "Unable to read due services"
        _state.update { current -> current.copy(
            dueServicesProjection = when (val projection = current.dueServicesProjection) {
                is DueServicesProjection.Available -> projection.copy(updateError = message)
                is DueServicesProjection.Unavailable, DueServicesProjection.Unresolved -> DueServicesProjection.Unavailable(message)
            },
        ) }
    }
    fun loadTemplates() = launchLoad { val values = repository.templates(); _state.update { it.copy(templates = values) } }
    fun loadTemplate(id: String) {
        val request = issueRequest("template")
        launchLoad { val value = repository.template(id); if (isCurrent(request)) _state.update { it.copy(template = value) } }
    }
    fun loadVisit(id: String) {
        val request = issueRequest("visit")
        launchLoad {
            val visit = repository.visit(id)
            val site = visit?.let { repository.site(it.siteId) }
            val calendarState = calendarCoordinator?.visitState(id)
            if (isCurrent(request)) _state.update { it.copy(visit = visit, site = site, visitCalendarState = calendarState) }
        }
    }
    fun loadFollowUps() = launchLoad { val values = repository.followUps(); _state.update { it.copy(followUps = values) } }
    fun loadFollowUp(id: String) {
        val request = issueRequest("followUp")
        launchLoad { val value = repository.followUp(id); if (isCurrent(request)) _state.update { it.copy(followUp = value) } }
    }
    fun onAppResumed() {
        businessDateSignal?.invalidate()
        reminderCoordinator?.reconcileAsync()
        calendarCoordinator?.reconcileAsync()
        refreshRootDataNonBlocking()
        loadReminderSettings()
        loadCalendarSettings()
    }

    fun loadReminderSettings() = launchLoad {
        val preferences = repository.reminderPreferences()
        val runtime = reminderCoordinator?.runtimeState() ?: ReminderRuntimeState()
        _state.update { it.copy(reminderPreferences = preferences, reminderRuntimeState = runtime) }
    }

    fun saveReminderSettings(value: ReminderPreferences, deliveryRequested: Boolean) {
        val lastSaved = _state.value.reminderSaveStatus.lastSavedCheckpoint()
        _state.update { it.copy(reminderSaveStatus = SaveStatus.Saving, error = null) }
        viewModelScope.launch {
            try {
                value.validate()
                val savedAt = repository.saveReminderPreferences(value)
                reminderCoordinator?.setDeliveryRequested(deliveryRequested)
                val preferences = repository.reminderPreferences()
                val runtime = reminderCoordinator?.runtimeState() ?: ReminderRuntimeState()
                _state.update { it.copy(reminderPreferences = preferences, reminderRuntimeState = runtime, reminderSaveStatus = SaveStatus.Saved(savedAt)) }
                refreshRootDataNonBlocking()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _state.update { it.copy(reminderSaveStatus = SaveStatus.Failed(failure.message ?: "Reminder settings not saved", lastSaved)) } }
        }
    }

    fun sendTestNotification() {
        val requested = reminderCoordinator?.sendTestNotification() == true
        val runtime = reminderCoordinator?.runtimeState() ?: ReminderRuntimeState()
        _state.update { it.copy(operationMessage = if (requested) "Test notification requested — Android controls delivery" else "Test notification unavailable — review Android permission and channels", reminderRuntimeState = runtime) }
    }

    fun loadCalendarSettings() = launchLoad { val runtime=calendarCoordinator?.runtimeState()?:CalendarRuntimeState(); _state.update { it.copy(calendarRuntimeState=runtime) } }
    fun setCalendarEnabled(enabled:Boolean)=runOperation({calendarCoordinator?.setEnabled(enabled);enabled}){loadCalendarSettings()}
    fun selectCalendar(value:WritableCalendar)=runOperation({calendarCoordinator?.select(value);value.id}){loadCalendarSettings()}
    fun loadVisitCalendar(visitId:String) {
        val request = issueRequest("visitCalendar")
        launchLoad { val value=calendarCoordinator?.visitState(visitId); if(isCurrent(request)) _state.update { it.copy(visitCalendarState=value) } }
    }
    fun addVisitToCalendar(visitId:String)=runOperation({calendarCoordinator?.add(visitId);visitId}){loadVisitCalendar(it);loadCalendarSettings()}
    fun removeVisitFromCalendar(visitId:String)=runOperation({calendarCoordinator?.remove(visitId);visitId}){loadVisitCalendar(it);loadCalendarSettings()}
    fun calendarEventIntent(eventId:Long)=calendarCoordinator?.eventIntent(eventId)

    fun setAppointmentReminderLead(visitId: String, minutes: Int?) = runOperation({ repository.setAppointmentReminderLead(visitId, minutes); visitId }) { loadVisit(it) }
    fun loadFieldEvidence(workItemId: String) {
        val request = issueRequest("fieldEvidence")
        launchLoad { val parts=repository.parts(workItemId); val photos=repository.photos(workItemId); if(isCurrent(request)) _state.update { it.copy(parts=parts, photos=photos) } }
    }

    fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try { val results = repository.search(query); ensureActive(); _state.update { it.copy(searchResults = results, error = null) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _state.update { it.copy(error = failure.message ?: "Search failed") } }
        }
    }

    fun createCustomer(input: CustomerInput, onSuccess: (String) -> Unit) = runOperation({ repository.createCustomer(input) }, onSuccess)
    fun createCustomerWithFirstSite(customer: CustomerInput, site: SiteInput, onSuccess: (Pair<String, String>) -> Unit) = runOperation({ repository.createCustomerWithFirstSite(customer, site) }, onSuccess)
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
        _state.update { it.copy(operationInProgress = true, operationMessage = null, error = null) }
        viewModelScope.launch {
            try {
                repository.rescheduleVisit(id, date, scheduledAt, reason)
                val refreshed = repository.visit(id) ?: error("Visit was saved but could not be reloaded")
                val site = repository.site(refreshed.siteId)
                _state.update { it.copy(visit = refreshed, site = site, operationInProgress = false, operationMessage = "Saved on this device") }
                refreshRootDataNonBlocking(); onSuccess(id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _state.update { it.copy(operationInProgress = false, error = failure.message ?: "Not saved") } }
        }
    }
    fun cancelVisit(id: String, reason: String, onSuccess: (String) -> Unit) = runOperation({ repository.cancelVisit(id, reason); id }, onSuccess)
    fun restoreVisit(id: String, date: String, onSuccess: (String) -> Unit) = runOperation({ repository.restoreVisit(id, date); id }, onSuccess)
    fun addOneOff(visitId: String, equipmentId: String, name: String) = runOperation({ repository.addOneOffWork(visitId, equipmentId, name) }) { loadVisit(visitId) }
    fun addPart(workItemId: String, description: String, quantity: String, unit: String) = runOperation({ repository.addPart(workItemId, description, quantity, unit) }) { loadFieldEvidence(workItemId) }
    fun savePhoto(workItemId: String, bytes: ByteArray, displayName: String?, mimeType: String, include: Boolean, caption: String?) = runOperation({ withContext(Dispatchers.IO) { repository.savePhoto(workItemId, bytes, displayName, mimeType, include, caption) } }) { loadFieldEvidence(workItemId) }
    fun reportOperationFailure(message:String) { _state.update { it.copy(operationInProgress=false,error=message,operationMessage=null) } }
    fun createContactNote(input: ContactNoteInput, onSuccess: (String) -> Unit = {}) = runOperation({ repository.createContactNote(input) }, onSuccess)
    fun loadContactNote(id: String) {
        val request = issueRequest("contactNote")
        launchLoad { val value=repository.contactNote(id); if(isCurrent(request)) _state.update { it.copy(contactNote=value) } }
    }
    fun markContactNoteEnteredInError(id:String,reason:String,onSuccess:(String)->Unit={})=runOperation({repository.markContactNoteEnteredInError(id,reason);id},onSuccess)
    fun createFollowUp(input: FollowUpInput, onSuccess: (String) -> Unit) = runOperation({ repository.createFollowUp(input) }, onSuccess)
    fun createCorrectiveFollowUp(workItemId: String, title: String, dueDate: String, privateNote: String, onSuccess: (String) -> Unit = {}) = runOperation({ repository.createCorrectiveFollowUp(workItemId, title, dueDate, privateNote) }, onSuccess)
    fun updateFollowUp(id:String,title:String,dueDate:String,note:String,reason:String,onSuccess:(String)->Unit={})=runOperation({repository.updateFollowUp(id,title,dueDate,note,reason);id},onSuccess)
    fun changeFollowUpState(id: String, target: String, reason: String, newDue: String?, onSuccess: (String) -> Unit = {}) = runOperation({ repository.changeFollowUpState(id, target, reason, newDue); id }, onSuccess)
    fun loadBusinessProfile() = launchLoad {
        val profile = repository.businessProfile()
        _state.update { it.copy(businessProfile = profile, businessProfileSaveStatus = profile?.modifiedAtEpochMillis?.let { timestamp -> SaveStatus.Saved(timestamp) } ?: SaveStatus.Idle) }
    }
    fun loadFinalRecord(id: String) {
        val request=issueRequest("finalRecord"); _state.update { it.copy(finalRecord = null) }
        launchLoad { val value=repository.finalRecord(id); if(isCurrent(request)) _state.update { it.copy(finalRecord=value) } }
    }
    fun loadFinalRecordRevision(recordId: String, revisionId: String, renditionId: String? = null) {
        val request=issueRequest("finalRecord"); _state.update { it.copy(finalRecord = null) }
        launchLoad { val value=repository.finalRecordRevision(recordId,revisionId,renditionId); if(isCurrent(request)) _state.update { it.copy(finalRecord=value) } }
    }
    fun loadHistory(query: HistoryQuery) {
        val request=issueRequest("history")
        launchLoad { val value=repository.history(query); if(isCurrent(request)) _state.update { it.copy(history=value) } }
    }
    fun loadAttention() = launchLoad { val value=repository.attention(); _state.update { it.copy(attention=value) } }
    fun loadRecordVersions(id: String) {
        val request=issueRequest("recordVersions")
        launchLoad { val versions=repository.recordVersions(id); if(isCurrent(request)) _state.update { it.copy(recordVersions=versions.first,reportVersions=versions.second) } }
    }
    fun loadCorrection(recordId: String) {
        val request=issueRequest("correction")
        launchLoad { val value=repository.openCorrection(recordId); if(isCurrent(request)) _state.update { it.copy(correction=value) } }
    }
    fun saveCorrection(value: CorrectionDraft) = runOperation({ repository.saveCorrection(value) }) { loadCorrection(value.recordId) }
    fun addCorrectionEvidence(recordId: String, correctionWorkItemId: String, bytes: ByteArray, displayName: String?, mimeType: String, caption: String?) = runOperation({ repository.addCorrectionEvidence(recordId, correctionWorkItemId, bytes, displayName, mimeType, caption) }) { loadCorrection(recordId) }
    fun commitCorrection(recordId: String, onSuccess: (String) -> Unit) = runOperation({ repository.commitCorrection(recordId) }, onSuccess)
    fun discardCorrection(recordId: String, onSuccess: (String) -> Unit) = runOperation({ repository.discardCorrection(recordId); recordId }, onSuccess)
    fun voidRecord(recordId: String, publicReason: String, privateReason: String, onSuccess: (String) -> Unit) = runOperation({ repository.voidRecord(recordId, publicReason, privateReason); recordId }, onSuccess)
    fun loadLifecycle(subjectType: String, id: String, action: String) {
        val request=issueRequest("lifecycle")
        launchLoad { val value=repository.lifecycleReview(subjectType,id,action); if(isCurrent(request)) _state.update { it.copy(lifecycleReview=value) } }
    }
    fun applyLifecycle(subjectType: String, id: String, action: String, reason: String, onSuccess: (String) -> Unit) = runOperation({ repository.applyLifecycle(subjectType, id, action, reason); id }, onSuccess)
    fun loadMove(equipmentId: String) {
        val request=issueRequest("move")
        launchLoad { val value=repository.moveReview(equipmentId); if(isCurrent(request)) _state.update { it.copy(moveReview=value) } }
    }
    fun moveEquipment(equipmentId: String, destination: String, date: String, reason: String, acknowledged: Boolean, onSuccess: (String) -> Unit) = runOperation({ repository.moveEquipment(equipmentId, destination, date, reason, acknowledged); equipmentId }, onSuccess)
    fun loadDatasetSummary() = launchLoad { val summary=repository.datasetSummary(); val attention=repository.attention(); _state.update { it.copy(datasetSummary=summary,attention=attention) } }
    fun setBackupReminder(days: Int) = runOperation({ repository.setBackupReminder(days); days }) { loadDatasetSummary() }
    fun createBackup(passphrase: CharArray, incomplete: Boolean) = runOperation({ repository.createBackup(passphrase, incomplete) }) { result -> _state.update { it.copy(backupResult = result) } }
    fun verifyWrittenBackup(bytes: ByteArray, passphrase: CharArray, destination: String) = runOperation({ val inspection = repository.inspectBackup(bytes, passphrase); val result = _state.value.backupResult ?: error("Prepared backup is unavailable"); require(inspection.snapshotAtEpochMillis == result.snapshotAtEpochMillis); if (result.complete) repository.recordVerifiedBackup(result, destination); inspection }) { inspection -> _state.update { it.copy(backupInspection = inspection) }; loadDatasetSummary() }
    fun inspectBackup(bytes: ByteArray, passphrase: CharArray) = runOperation({ repository.inspectBackup(bytes, passphrase) }) { value -> _state.update { it.copy(backupInspection = value) } }
    fun restoreBackup(confirmation: String, incompleteAcknowledged: Boolean, onSuccess: () -> Unit) { val inspection = _state.value.backupInspection ?: return; advanceDatasetGeneration(); runOperation({ reminderCoordinator?.resetForDatasetReplacement(); calendarCoordinator?.resetForDatasetReplacement(); repository.restoreBackup(inspection, confirmation, incompleteAcknowledged); true }) { onSuccess() } }
    fun prepareDirectoryCsv(includeInactive: Boolean, includePrivate: Boolean, customerId: String? = null) = runOperation({ repository.directoryCsv(includeInactive, includePrivate, customerId) }) { bytes -> _state.update { it.copy(exportBytes = bytes) } }
    fun prepareRecordsCsv(includeInactive: Boolean, includePrivate: Boolean, previous: Boolean, customerId: String? = null) = runOperation({ repository.recordsCsvPackage(includeInactive, includePrivate, previous, customerId) }) { bytes -> _state.update { it.copy(exportBytes = bytes) } }
    fun validateCsv(bytes: ByteArray) = runOperation({ repository.validateDirectoryCsv(bytes) }) { value -> _state.update { it.copy(csvPreview = value, importResult = null) } }
    fun importCsv(createSeparate: Set<String> = emptySet(), skipped: Set<String> = emptySet()) { val preview = _state.value.csvPreview ?: return; runOperation({ repository.importDirectory(preview, createSeparate, skipped) }) { value -> _state.update { it.copy(importResult = value) }; refreshRootDataNonBlocking() } }
    fun erase(acknowledged: Boolean, confirmation: String, onSuccess: () -> Unit) { advanceDatasetGeneration(); runOperation({ reminderCoordinator?.resetForDatasetReplacement(); calendarCoordinator?.resetForDatasetReplacement(); repository.erase(acknowledged, confirmation); true }) { onSuccess() } }
    fun consumeFinalizedNavigation() { _state.update { it.copy(finalizedRecordId = null) } }

    fun savePublicWork(workItemId: String, text: String) = persistDraft({ repository.savePublicWork(workItemId, text) }) { val value=repository.inspection(workItemId); _state.update { it.copy(inspection=value) } }
    fun markChecklistReviewed(workItemId: String) = persistDraft({ repository.markChecklistReviewed(workItemId) }) { val value=repository.inspection(workItemId); _state.update { it.copy(inspection=value) } }
    fun saveBusinessProfile(profile: BusinessProfile) {
        val lastSaved = _state.value.businessProfileSaveStatus.lastSavedCheckpoint() ?: _state.value.businessProfile?.modifiedAtEpochMillis
        _state.update { it.copy(businessProfileSaveStatus = SaveStatus.Saving, error = null) }
        viewModelScope.launch {
            try {
                val savedAt = repository.saveBusinessProfile(profile)
                _state.update { it.copy(businessProfileSaveStatus = SaveStatus.Saved(savedAt)) }
                try { val refreshed=repository.businessProfile(); _state.update { it.copy(businessProfile=refreshed,contentRefreshError=null) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { _state.update { it.copy(contentRefreshError = failure.message ?: "Profile was saved, but the screen could not refresh") } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _state.update { it.copy(businessProfileSaveStatus = SaveStatus.Failed(failure.message ?: "Profile not saved", lastSaved)) } }
        }
    }
    fun refreshVisitReportIdentity(visitId: String) = persistDraft({ repository.refreshVisitReportIdentity(visitId) }) { val value=repository.visitReportIdentity(visitId); _state.update { it.copy(visitReportIdentity=value) } }
    fun saveCompletion(workItemId: String, outcome: String?, fulfills: Boolean, reason: String?, nextDue: String?, calculated: Boolean?, overrideReason: String?, visitId: String) = persistDraft({
        repository.saveCompletionDraft(workItemId, outcome, fulfills, reason, nextDue, calculated, overrideReason)
    }) { val values=repository.completionLines(visitId); _state.update { it.copy(completionLines=values) } }

    fun finalizeVisit(visitId: String) {
        if (_state.value.finalizing) return
        _state.update { it.copy(finalizing = true, error = null) }
        viewModelScope.launch {
            try {
                when (val result = repository.finalizeVisit(visitId)) {
                    is FinalizeResult.Success -> {
                        _state.update { it.copy(finalizing = false, finalizedRecordId = result.recordId) }
                        refreshRootDataNonBlocking()
                    }
                    is FinalizeResult.Blocked -> _state.update { it.copy(finalizing = false, error = result.message) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _state.update { it.copy(finalizing = false, error = failure.message ?: "Finalization failed") } }
        }
    }

    fun generateReport(recordId: String, revisionId: String? = null) {
        val service = reportService ?: return
        if (_state.value.generatingReport) return
        _state.update { it.copy(generatingReport = true, error = null) }
        viewModelScope.launch {
            try {
                val rendition = if (revisionId == null) service.generate(recordId) else service.generateRevision(recordId, revisionId)
                _state.update { it.copy(generatingReport = false, finalRecord = it.finalRecord?.copy(report = rendition)) }
                try { val refreshed=if(revisionId==null) repository.finalRecord(recordId) else repository.finalRecordRevision(recordId,revisionId,rendition.id); _state.update { it.copy(finalRecord=refreshed,contentRefreshError=null) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { _state.update { it.copy(contentRefreshError = failure.message ?: "Report was generated, but the screen could not refresh") } }
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _state.update { it.copy(generatingReport = false, error = failure.message ?: "PDF generation failed") } }
        }
    }

    private fun persistDraft(write: suspend () -> Long, refresh: suspend () -> Unit = {}) {
        val lastSaved = _state.value.saveStatus.lastSavedCheckpoint()
        _state.update { it.copy(saveStatus = SaveStatus.Saving, error = null) }
        viewModelScope.launch {
            try {
                val savedAt = write()
                _state.update { it.copy(saveStatus = SaveStatus.Saved(savedAt)) }
                try { refresh(); _state.update { it.copy(contentRefreshError = null) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { _state.update { it.copy(contentRefreshError = failure.message ?: "Saved, but the screen could not refresh") } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _state.update { it.copy(saveStatus = SaveStatus.Failed(failure.message ?: "Not saved", lastSaved)) } }
        }
    }

    private fun <T> runOperation(block: suspend () -> T, onSuccess: (T) -> Unit = {}) {
        if (_state.value.operationInProgress) return
        _state.update { it.copy(operationInProgress = true, operationMessage = null, error = null) }
        viewModelScope.launch {
            try { val id = block(); try { calendarCoordinator?.reconcile() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* Calendar is an independent projection; business writes stay committed. */ }; _state.update { it.copy(operationInProgress = false, operationMessage = "Saved on this device") }; refreshRootDataNonBlocking(); onSuccess(id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _state.update { it.copy(operationInProgress = false, error = failure.message ?: "Not saved") } }
        }
    }

    fun requestResponseChange(questionId: String, disposition: ResponseDisposition, value: String? = null, reason: String? = null) {
        val draft = _state.value.inspection ?: return
        val question = draft.questions.firstOrNull { it.snapshotItemId == questionId } ?: return
        if (question.semanticallyMatches(disposition, value, reason)) return
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
            ResponseDisposition.ISSUE_FOUND, ResponseDisposition.NOT_APPLICABLE -> reason.orEmpty() == requestedReason?.trim().orEmpty()
            else -> true
        }
    }

    private fun persistResponse(draft: InspectionDraft, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?) {
        val lastSaved = state.value.saveStatus.lastSavedCheckpoint() ?: draft.modifiedAtEpochMillis
        _state.update { it.copy(saveStatus = SaveStatus.Saving, error = null) }
        viewModelScope.launch {
            try {
                val savedAt = repository.saveResponse(draft.workItemId, questionId, disposition, value, reason)
                _state.update { it.copy(saveStatus = SaveStatus.Saved(savedAt)) }
                try { val refreshed=repository.inspection(draft.workItemId); _state.update { it.copy(inspection=refreshed,contentRefreshError=null) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { _state.update { it.copy(contentRefreshError = failure.message ?: "Saved, but the screen could not refresh") } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { current -> current.copy(
                    saveStatus = SaveStatus.Failed(failure.message ?: "Draft write failed", lastSaved),
                ) }
            }
        }
    }

    private fun launchLoad(block: suspend () -> Unit) {
        if (activeLoads.incrementAndGet() == 1) _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                startup()
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.update { it.copy(error = failure.message ?: "Unable to read saved data") }
            } finally {
                val remaining = activeLoads.decrementAndGet().coerceAtLeast(0)
                if (remaining == 0) _state.update { it.copy(loading = false) }
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ServiceLoopViewModel(container.repository, container.reportService, container.restrictedRecoveryState, container.businessDateSignal, container.reminderCoordinator, container.calendarCoordinator) { container.startup.await() } as T
    }

    private fun SaveStatus.lastSavedCheckpoint(): Long? = when (this) {
        is SaveStatus.Saved -> atEpochMillis
        is SaveStatus.Failed -> lastSavedAtEpochMillis
        else -> null
    }
}
