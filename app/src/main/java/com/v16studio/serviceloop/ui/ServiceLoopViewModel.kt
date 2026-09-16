package com.v16studio.serviceloop.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.v16studio.serviceloop.AppContainer
import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.data.NextDueRecoveryRequest
import com.v16studio.serviceloop.data.NextDueRecoveryResult
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
import com.v16studio.serviceloop.ui.service.ServiceDraftAutosaveCoordinator
import com.v16studio.serviceloop.ui.service.ServiceDraftFieldId
import com.v16studio.serviceloop.ui.service.ServiceDraftFieldState
import com.v16studio.serviceloop.ui.service.ServiceDraftFlushResult
import com.v16studio.serviceloop.ui.service.ServiceDraftValidators
import com.v16studio.serviceloop.ui.service.ServiceDraftValidation
import com.v16studio.serviceloop.ui.service.ServiceDraftQuestionFieldKind
import com.v16studio.serviceloop.ui.service.ServiceDraftQuestionId
import com.v16studio.serviceloop.ui.service.ServiceDraftChoiceWriteResult
import com.v16studio.serviceloop.domain.ServiceDraftFieldKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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
    val operationalDashboard: OperationalDashboardProjection? = null,
    val operationalDashboardError: String? = null,
    val equipment: EquipmentDetail? = null,
    val equipmentList: List<EquipmentSummary> = emptyList(),
    val customerList: List<CustomerSummary> = emptyList(),
    val siteList: List<SiteRegisterSummary> = emptyList(),
    val serviceContext: ServiceContext? = null,
    val completionContext: CompletionContext? = null,
    val serviceFollowUps: List<FollowUpDetail> = emptyList(),
    val fieldEvidenceWorkItemId: String? = null,
    val photoMetadataPendingId: String? = null,
    val photoMetadataErrorId: String? = null,
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
    val templateVersions: List<TemplateRevisionDetail> = emptyList(),
    val templatePlanReferenceCount: Int? = null,
    val equipmentLinkContext: EquipmentLinkContext? = null,
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
    /** Compatibility projections for existing Service and Review composables. */
    val inspection: InspectionDraft?
        get() = serviceContext?.workspace?.inspection
    val serviceProgress: VisitServiceProgress?
        get() = serviceContext?.progress ?: serviceContext?.workspace?.serviceProgress
    val activeServiceVisitId: String?
        get() = serviceContext?.activeVisitId
    val activeServiceWorkItemId: String?
        get() = serviceContext?.activeWorkItemId
    val completionLines: List<CompletionLine>
        get() = completionContext?.lines ?: serviceContext?.workspace?.completionLines.orEmpty()
    val completionVisitId: String?
        get() = completionContext?.visitId ?: serviceContext?.workspace?.inspection?.visitId

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

data class InspectionFocus(
    val kind: CompletionBlockerKind,
    val questionId: String? = null,
    val workItemId: String? = null,
    val fieldKey: String? = null,
    val attention: Boolean = false,
    val requestId: Long = 0L,
)

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
    private val serviceDraftAutosaveCoordinator = ServiceDraftAutosaveCoordinator(repository, viewModelScope)
    val serviceDraftStates: StateFlow<Map<ServiceDraftFieldId, ServiceDraftFieldState>> = serviceDraftAutosaveCoordinator.states
    private var rootRefreshJob: Job? = null
    private var operationalDashboardJob: Job? = null
    private var operationalDashboardScope: WorkScope? = null
    private var searchJob: Job? = null
    private var dueServicesJob: Job? = null
    private val activeLoads = AtomicInteger(0)
    private val requestLock = Any()
    private val requestVersions = mutableMapOf<String, Long>()
    private val focusSequence = AtomicLong(0L)
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

    private fun loadInitialRootData() {
        val request = issueRequest("root")
        val visitsRequest = issueRequest("visits")
        launchLoad {
        val home = repository.home()
        val equipmentList = repository.equipmentList()
        val customerList = repository.customerList()
        val siteList = repository.siteList()
        val visits = repository.visits()
        val publishVisits = isCurrent(visitsRequest)
        if (isCurrent(request)) _state.update { current -> current.copy(
            home = home,
            equipmentList = equipmentList,
            customerList = customerList,
            siteList = siteList,
            visits = if (publishVisits) visits else current.visits,
            rootDataReady = true,
            recoveryCheckComplete = true,
        ) }
        }
    }

    internal fun refreshRootDataNonBlocking() {
        if (!_state.value.rootDataReady) return
        val request = issueRequest("root")
        val visitsRequest = issueRequest("visits")
        rootRefreshJob?.cancel()
        rootRefreshJob = viewModelScope.launch {
            try {
                val home = repository.home()
                val equipmentList = repository.equipmentList()
                val customerList = repository.customerList()
                val siteList = repository.siteList()
                val visits = repository.visits()
                ensureActive()
                val publishVisits = isCurrent(visitsRequest)
                if (isCurrent(request)) _state.update { current -> current.copy(
                    home = home,
                    equipmentList = equipmentList,
                    customerList = customerList,
                    siteList = siteList,
                    visits = if (publishVisits) visits else current.visits,
                    rootRefreshError = null,
                ) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isCurrent(request)) _state.update { it.copy(rootRefreshError = failure.message ?: "Unable to refresh saved data") }
            }
        }
    }

    fun loadEquipment(id: String) {
        val request = issueRequest("equipment")
        launchLoad { val value = repository.equipment(id); if (isCurrent(request)) _state.update { it.copy(equipment = value) } }
    }

    fun loadInspection(id: String) {
        _state.value.activeServiceWorkItemId?.takeIf { it != id }?.let(::supersedeNextDueRecovery)
        val request = issueRequest("inspection")
        issueRequest("draftSaveContext")
        launchLoad {
            val workspace = repository.serviceWorkspace(id)
            if (!isCurrent(request)) return@launchLoad
            val published = workspace?.let { value ->
                if (value.serviceProgress == null) value.copy(serviceProgress = progressFallback(value.inspection)) else value
            }
            _state.update { current -> current.copy(
                serviceContext = published?.let { value -> ServiceContext(value, activeVisitId = value.inspection.visitId, activeWorkItemId = value.inspection.workItemId) },
                completionContext = null,
                saveStatus = published?.inspection?.let { SaveStatus.Saved(it.modifiedAtEpochMillis) } ?: SaveStatus.Idle,
            ) }
            published?.inspection?.let(::reconcilePersistedServiceDrafts)
        }
    }

    /** Refresh Service content after a canonical field write without replacing the screen with a loader. */
    fun refreshServiceContext(workItemId: String) {
        val request = issueRequest("inspection")
        viewModelScope.launch {
            try {
                val workspace = repository.serviceWorkspace(workItemId) ?: return@launch
                val published = if (workspace.serviceProgress == null) workspace.copy(serviceProgress = progressFallback(workspace.inspection)) else workspace
                if (isCurrent(request)) _state.update { current ->
                    if (current.inspection?.workItemId == workItemId) current.copy(
                        serviceContext = ServiceContext(published, activeVisitId = published.inspection.visitId, activeWorkItemId = published.inspection.workItemId),
                        contentRefreshError = null,
                    ) else current
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isCurrent(request)) _state.update { current ->
                    if (current.inspection?.workItemId == workItemId) current.copy(contentRefreshError = failure.message ?: "Saved, but the Service could not refresh") else current
                }
            }
        }
    }

    private fun reconcilePersistedServiceDrafts(draft: InspectionDraft) {
        var unsupportedFieldFound = false
        draft.rawInputs.forEach { (fieldKey, rawValue) ->
            when (fieldKey) {
                ServiceDraftFieldKeys.WORK -> scheduleWorkText(draft.workItemId, rawValue)
                ServiceDraftFieldKeys.PRIVATE -> schedulePrivateText(draft.workItemId, rawValue)
                ServiceDraftFieldKeys.NOT_PERFORMED_REASON -> scheduleNotPerformedReason(draft.workItemId, draft.visitId, rawValue)
                ServiceDraftFieldKeys.OVERRIDE_DATE -> scheduleRecurrenceOverrideDate(draft.workItemId, rawValue)
                ServiceDraftFieldKeys.OVERRIDE_REASON -> scheduleRecurrenceOverrideReason(draft.workItemId, rawValue)
                else -> {
                    val photoId = ServiceDraftFieldKeys.parsePhotoCaption(fieldKey)
                    if (photoId != null) {
                        schedulePhotoCaption(draft.workItemId, photoId, rawValue)
                        return@forEach
                    }
                    val parsed = ServiceDraftFieldKeys.parseQuestionField(fieldKey)
                    val question = parsed?.let { parsedField ->
                        draft.questions.firstOrNull { it.snapshotItemId == parsedField.snapshotItemId }
                    }
                    when {
                        question == null -> unsupportedFieldFound = true
                        parsed?.kind == ServiceDraftFieldKeys.QuestionFieldKind.VALUE -> scheduleQuestionValue(draft.workItemId, parsed.snapshotItemId, rawValue)
                        parsed?.kind == ServiceDraftFieldKeys.QuestionFieldKind.ISSUE -> scheduleIssueDescription(draft.workItemId, parsed.snapshotItemId, rawValue)
                        parsed?.kind == ServiceDraftFieldKeys.QuestionFieldKind.NOT_APPLICABLE -> scheduleNotApplicableReason(draft.workItemId, parsed.snapshotItemId, rawValue)
                        else -> unsupportedFieldFound = true
                    }
                }
            }
        }
        if (unsupportedFieldFound) _state.update { current ->
            if (current.inspection?.workItemId == draft.workItemId) current.copy(contentRefreshError = "A saved service edit could not be restored") else current
        }
    }
    fun focusInspection(kind: CompletionBlockerKind, questionId: String?, workItemId: String? = null) {
        _state.update { it.copy(inspectionFocus = InspectionFocus(kind, questionId, workItemId = workItemId, requestId = focusSequence.incrementAndGet())) }
    }
    fun focusService(workItemId: String) {
        _state.update {
            it.copy(
                inspectionFocus = InspectionFocus(
                    kind = CompletionBlockerKind.WORK_PERFORMED,
                    workItemId = workItemId,
                    attention = true,
                    requestId = focusSequence.incrementAndGet(),
                ),
            )
        }
    }
    fun clearInspectionFocus(requestId: Long? = null) {
        _state.update { state ->
            if (requestId == null || state.inspectionFocus?.requestId == requestId) state.copy(inspectionFocus = null) else state
        }
    }

    fun loadCompletion(visitId: String) {
        val request = issueRequest("completion")
        issueRequest("draftSaveContext")
        viewModelScope.launch {
            try {
                val lines = repository.completionLines(visitId)
                val profile = repository.businessProfile()
                val identity = repository.visitReportIdentity(visitId)
                if (isCurrent(request)) _state.update { current -> current.copy(completionContext = CompletionContext(visitId, lines), businessProfile = profile, visitReportIdentity = identity, contentRefreshError = null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (isCurrent(request)) _state.update { it.copy(contentRefreshError = failure.message ?: "Unable to load service completion") } }
        }
    }
    fun clearCompletionContext(visitId: String) {
        issueRequest("completion")
        issueRequest("draftSaveContext")
        _state.update { current -> if(current.completionVisitId == visitId) current.copy(completionContext = null, finalizing=false, finalizedRecordId=null) else current }
    }

    fun loadVisits() {
        val request = issueRequest("visits")
        launchLoad { val values = repository.visits(); if (isCurrent(request)) _state.update { it.copy(visits = values) } }
    }
    fun loadCustomer(id: String) {
        val request = issueRequest("customer")
        launchLoad { val value = repository.customer(id); if (isCurrent(request)) _state.update { it.copy(customer = value) } }
    }

    fun observeOperationalDashboard(scope: WorkScope) {
        if (operationalDashboardScope == scope && operationalDashboardJob?.isActive == true) return
        operationalDashboardScope = scope
        operationalDashboardJob?.cancel()
        val request = issueRequest("operationalDashboard")
        _state.update { current -> current.copy(operationalDashboard = null, operationalDashboardError = null) }
        operationalDashboardJob = viewModelScope.launch {
            try {
                repository.observeOperationalDashboard(scope).collect { projection ->
                    if (isCurrent(request) && operationalDashboardScope == scope) {
                        _state.update { current -> current.copy(operationalDashboard = projection, operationalDashboardError = null) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isCurrent(request)) _state.update { current -> current.copy(operationalDashboardError = failure.message ?: "Unable to read current work") }
            }
        }
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
        launchLoad { val sites = repository.visitSites(); val templates = repository.templates(); if (isCurrent(request)) _state.update { it.copy(visitSites = sites, templates = templates) } }
    }

    private fun observeDueServices() {
        dueServicesJob = viewModelScope.launch {
            try {
                startup()
                repository.observeDueServices().collect { loaded ->
                    _state.update { current -> current.copy(
                        dueServicesProjection = DueServicesProjection.Available(loaded),
                    ) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                settleDueServicesFailure(failure)
            }
        }
    }

    fun retryDueServices() {
        if (dueServicesJob?.isActive == true) return
        _state.update { current -> current.copy(
            dueServicesProjection = when (val projection = current.dueServicesProjection) {
                is DueServicesProjection.Available -> projection.copy(updateError = null)
                is DueServicesProjection.Unavailable, DueServicesProjection.Unresolved -> DueServicesProjection.Unresolved
            },
        ) }
        observeDueServices()
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
    fun loadTemplates() { val request=issueRequest("templates"); launchLoad { val values = repository.templates(); if(isCurrent(request)) _state.update { it.copy(templates = values) } } }
    fun loadTemplate(id: String) {
        val request = issueRequest("template")
        launchLoad {
            val value = repository.template(id)
            val usage = value?.let { repository.templateServicePlanReferenceCount(id) }
            if (isCurrent(request)) _state.update { it.copy(template = value, templateVersions = emptyList(), templatePlanReferenceCount = usage) }
        }
    }
    fun loadTemplateHistory(id: String) {
        val request = issueRequest("templateHistory")
        launchLoad { val values = repository.templateRevisions(id); if (isCurrent(request)) _state.update { it.copy(templateVersions = values) } }
    }
    fun loadTemplatePlanUsage(id: String) {
        val request = issueRequest("templateUsage")
        launchLoad { val value = repository.templateServicePlanReferenceCount(id); if (isCurrent(request)) _state.update { it.copy(templatePlanReferenceCount = value) } }
    }
    fun loadVisit(id: String) {
        val request = issueRequest("visit")
        launchLoad {
            val visit = repository.visit(id)
            val site = visit?.let { repository.site(it.siteId) }
            val calendarState = calendarCoordinator?.visitState(id)
            val templates = repository.templates()
            val progress = visit?.let { value -> runCatching { repository.serviceVisitProgress(value.id) }.getOrNull() }
            if (isCurrent(request)) _state.update { current -> current.copy(visit = visit, site = site, templates = templates, visitCalendarState = calendarState, serviceContext = ServiceContext(progress = progress, activeVisitId = progress?.visitId ?: current.activeServiceVisitId, activeWorkItemId = progress?.preferredResumeItem()?.workItemId ?: current.activeServiceWorkItemId)) }
        }
    }

    /** Resolves the first Service for a newly-created Working Visit before navigation. */
    fun resolveWorkingVisitResume(visitId: String, onResolved: (String?) -> Unit) {
        viewModelScope.launch {
            val progress = runCatching { repository.serviceVisitProgress(visitId) }.getOrNull()
            val workItemId = progress?.preferredResumeItem()?.workItemId
            _state.update { current -> current.copy(serviceContext = ServiceContext(progress = progress, activeVisitId = visitId, activeWorkItemId = workItemId)) }
            onResolved(workItemId)
        }
    }
    fun loadFollowUps() { val request=issueRequest("followUps"); launchLoad { val values = repository.followUps(); if(isCurrent(request)) _state.update { it.copy(followUps = values) } } }
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

    fun loadReminderSettings() {
        val request = issueRequest("reminderSettings")
        launchLoad {
            val preferences = repository.reminderPreferences()
            val runtime = reminderCoordinator?.runtimeState() ?: ReminderRuntimeState()
            if (isCurrent(request)) _state.update { it.copy(reminderPreferences = preferences, reminderRuntimeState = runtime) }
        }
    }

    fun saveReminderSettings(value: ReminderPreferences, deliveryRequested: Boolean) {
        val request = issueRequest("reminderSettings")
        val lastSaved = _state.value.reminderSaveStatus.lastSavedCheckpoint()
        _state.update { it.copy(reminderSaveStatus = SaveStatus.Saving, error = null) }
        viewModelScope.launch {
            try {
                value.validate()
                val savedAt = repository.saveReminderPreferences(value)
                reminderCoordinator?.setDeliveryRequested(deliveryRequested)
                val preferences = repository.reminderPreferences()
                val runtime = reminderCoordinator?.runtimeState() ?: ReminderRuntimeState()
                if (isCurrent(request)) _state.update { it.copy(reminderPreferences = preferences, reminderRuntimeState = runtime, reminderSaveStatus = SaveStatus.Saved(savedAt)) }
                refreshRootDataNonBlocking()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (isCurrent(request)) _state.update { it.copy(reminderSaveStatus = SaveStatus.Failed(failure.message ?: "Reminder settings not saved", lastSaved)) } }
        }
    }

    fun sendTestNotification() {
        val requested = reminderCoordinator?.sendTestNotification() == true
        val runtime = reminderCoordinator?.runtimeState() ?: ReminderRuntimeState()
        _state.update { it.copy(operationMessage = if (requested) "Test notification requested — Android controls delivery" else "Test notification unavailable — review Android permission and channels", reminderRuntimeState = runtime) }
    }

    fun loadCalendarSettings() {
        val request = issueRequest("calendarSettings")
        launchLoad { val runtime=calendarCoordinator?.runtimeState()?:CalendarRuntimeState(); if(isCurrent(request)) _state.update { it.copy(calendarRuntimeState=runtime) } }
    }
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
        viewModelScope.launch {
            try {
                val parts=repository.parts(workItemId); val photos=repository.photos(workItemId); val followUps=repository.correctiveFollowUps(workItemId)
                if(isCurrent(request)) _state.update { it.copy(parts=parts, photos=photos, serviceFollowUps=followUps, fieldEvidenceWorkItemId=workItemId, contentRefreshError=null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (isCurrent(request)) _state.update { it.copy(contentRefreshError = failure.message ?: "Unable to load Service evidence") } }
        }
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
    fun setTemplateState(id: String, state: String, onSuccess: (String) -> Unit = {}) = runOperation({ repository.setTemplateState(id, state); id }) { value -> loadTemplate(value); loadTemplates(); onSuccess(value) }
    fun deleteTemplate(id: String, onSuccess: (String) -> Unit = {}) = runOperation({ repository.deleteTemplate(id); id }) { value -> loadTemplates(); onSuccess(value) }
    fun cloneTemplate(id: String, onSuccess: (String) -> Unit) = runOperation({ repository.cloneTemplate(id) }, onSuccess)
    fun createVisit(planIds: List<String>, state: String, date: String, scheduledAt: Long?, onSuccess: (String) -> Unit) = runOperation({ repository.createVisit(planIds, state, date, scheduledAt) }, onSuccess)
    fun createVisitForSite(siteId: String, planIds: List<String>, adHocWork: List<AdHocWorkInput>, state: String, date: String, scheduledAt: Long?, onSuccess: (String) -> Unit) = runOperation({ repository.createVisitForSite(siteId, planIds, adHocWork, state, date, scheduledAt) }, onSuccess)
    fun createNewCustomerVisit(input: NewCustomerVisitInput, adHocWork: List<AdHocWorkInput>, state: String, date: String, scheduledAt: Long?, onSuccess: (String) -> Unit) = runOperation({ repository.createNewCustomerVisit(input, adHocWork, state, date, scheduledAt) }, onSuccess)
    fun createOneTimeVisit(input: OneTimeVisitInput, adHocWork: List<AdHocWorkInput>, state: String, date: String, scheduledAt: Long?, onSuccess: (String) -> Unit) = runOperation({ repository.createOneTimeVisit(input, adHocWork, state, date, scheduledAt) }, onSuccess)
    fun makeCustomerStandard(customerId: String, onSuccess: (String) -> Unit = {}) = runOperation({ repository.makeCustomerStandard(customerId); customerId }) { id -> refreshRootDataNonBlocking(); loadCustomer(id); onSuccess(id) }
    fun startVisit(id: String, onSuccess: (String) -> Unit) {
        if (_state.value.operationInProgress) return
        val request = issueRequest("visit")
        _state.update { it.copy(operationInProgress = true, operationMessage = null, error = null) }
        viewModelScope.launch {
            try {
                repository.startVisit(id)
                val visit = repository.visit(id)
                val site = visit?.let { repository.site(it.siteId) }
                val progress = runCatching { repository.serviceVisitProgress(id) }.getOrNull()
                try { calendarCoordinator?.reconcile() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
                if (isCurrent(request)) _state.update { current -> current.copy(visit = visit, site = site, serviceContext = ServiceContext(progress = progress, activeVisitId = progress?.visitId ?: current.activeServiceVisitId, activeWorkItemId = progress?.preferredResumeItem()?.workItemId ?: current.activeServiceWorkItemId), operationInProgress = false, operationMessage = "Saved on this device") }
                refreshRootDataNonBlocking()
                onSuccess(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isCurrent(request)) _state.update { it.copy(operationInProgress = false, error = failure.message ?: "Visit could not be started") }
            }
        }
    }
    fun rescheduleVisit(id: String, date: String, scheduledAt: Long?, reason: String, onSuccess: (String) -> Unit) {
        if (_state.value.operationInProgress) return
        val request = issueRequest("visit")
        _state.update { it.copy(operationInProgress = true, operationMessage = null, error = null) }
        viewModelScope.launch {
            try {
                repository.rescheduleVisit(id, date, scheduledAt, reason)
                val refreshed = repository.visit(id) ?: error("Visit was saved, but its details could not be refreshed")
                val site = repository.site(refreshed.siteId)
                if (isCurrent(request)) _state.update { it.copy(visit = refreshed, site = site, operationInProgress = false, operationMessage = "Saved on this device") }
                refreshRootDataNonBlocking(); onSuccess(id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if(isCurrent(request)) _state.update { it.copy(operationInProgress = false, error = failure.message ?: "Not saved") } }
        }
    }
    fun cancelVisit(id: String, reason: String, onSuccess: (String) -> Unit) = runOperation({ repository.cancelVisit(id, reason); id }, onSuccess)
    fun restoreVisit(id: String, date: String, onSuccess: (String) -> Unit) = runOperation({ repository.restoreVisit(id, date); id }, onSuccess)
    fun addAdHocWork(visitId: String, input: AdHocWorkInput, onSuccess: (String) -> Unit = {}) = runOperation({ repository.addAdHocWork(visitId, input) }) { workItemId -> loadVisit(visitId); onSuccess(workItemId) }
    fun loadEquipmentLinkContext(workItemId: String) {
        val request = issueRequest("equipmentLinkContext")
        launchLoad { val value = repository.equipmentLinkContext(workItemId); if (isCurrent(request)) _state.update { it.copy(equipmentLinkContext = value) } }
    }
    fun linkWorkItemEquipment(workItemId: String, equipmentId: String, onSuccess: () -> Unit = {}) = runOperation({ repository.linkWorkItemEquipment(workItemId, equipmentId); workItemId }) { id -> loadInspection(id); onSuccess() }
    fun createAndLinkEquipment(workItemId: String, input: EquipmentInput, onSuccess: () -> Unit = {}) = runOperation({ repository.createAndLinkEquipment(workItemId, input); workItemId }) { id -> loadInspection(id); onSuccess() }
    private fun refreshEvidence(workItemId: String) { loadFieldEvidence(workItemId); refreshServiceContext(workItemId) }
    fun addPart(workItemId: String, description: String, quantity: String, unit: String, onSuccess: () -> Unit = {}) = runOperation({ repository.addPart(workItemId, description, quantity, unit) }) { refreshEvidence(workItemId); onSuccess() }
    fun updatePart(workItemId: String, partId: String, description: String, quantity: String, unit: String, onSuccess: () -> Unit = {}) = runOperation({ repository.updatePart(workItemId, partId, description, quantity, unit) }) { refreshEvidence(workItemId); onSuccess() }
    fun removePart(workItemId: String, partId: String) = runOperation({ repository.removePart(workItemId, partId) }) { refreshEvidence(workItemId) }
    fun savePhoto(workItemId: String, bytes: ByteArray, displayName: String?, mimeType: String, include: Boolean, caption: String?) = runOperation({ withContext(Dispatchers.IO) { repository.savePhoto(workItemId, bytes, displayName, mimeType, include, caption) } }) { refreshEvidence(workItemId) }
    private val photoMetadataMutex = Mutex()
    private val photoMetadataVersions = mutableMapOf<String, Long>()
    fun setPhotoReportInclusion(workItemId: String, photoId: String, include: Boolean) {
        val version = (photoMetadataVersions[photoId] ?: 0L) + 1L
        photoMetadataVersions[photoId] = version
        _state.update { it.copy(photoMetadataPendingId = photoId, photoMetadataErrorId = null, error = null) }
        viewModelScope.launch {
            try {
                photoMetadataMutex.withLock {
                    if (photoMetadataVersions[photoId] != version) return@withLock
                    repository.setPhotoReportInclusion(workItemId, photoId, include)
                    if (photoMetadataVersions[photoId] == version) {
                        loadFieldEvidence(workItemId)
                        _state.update { it.copy(photoMetadataPendingId = null, photoMetadataErrorId = null) }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (photoMetadataVersions[photoId] == version) _state.update { it.copy(photoMetadataPendingId = null, photoMetadataErrorId = photoId, error = failure.message ?: "Photo details not saved") }
            }
        }
    }
    fun removePhoto(workItemId: String, photoId: String) {
        photoMetadataVersions[photoId] = (photoMetadataVersions[photoId] ?: 0L) + 1L
        runOperation({
            serviceDraftAutosaveCoordinator.cancelAndJoin(ServiceDraftFieldId(workItemId, ServiceDraftFieldKeys.photoCaption(photoId)))
            repository.clearWorkingInputBuffer(workItemId, ServiceDraftFieldKeys.photoCaption(photoId))
            withContext(Dispatchers.IO) { repository.removePhoto(workItemId, photoId) }
        }) { refreshEvidence(workItemId) }
    }

    fun schedulePhotoCaption(workItemId: String, photoId: String, rawValue: String) = serviceDraftAutosaveCoordinator.scheduleText(
        ServiceDraftFieldId(workItemId, ServiceDraftFieldKeys.photoCaption(photoId)), rawValue,
        validator = { value -> if (value.length <= 500) ServiceDraftValidation() else ServiceDraftValidation("Caption is too long") },
        writer = { value -> repository.savePhotoCaption(workItemId, photoId, value) },
        onSaved = { refreshEvidence(workItemId) },
    )
    fun reportOperationFailure(message:String) { _state.update { it.copy(operationInProgress=false,error=message,operationMessage=null) } }
    fun createContactNote(input: ContactNoteInput, onSuccess: (String) -> Unit = {}) = runOperation({ repository.createContactNote(input) }, onSuccess)
    fun loadContactNote(id: String) {
        val request = issueRequest("contactNote")
        launchLoad { val value=repository.contactNote(id); if(isCurrent(request)) _state.update { it.copy(contactNote=value) } }
    }
    fun markContactNoteEnteredInError(id:String,reason:String,onSuccess:(String)->Unit={})=runOperation({repository.markContactNoteEnteredInError(id,reason);id},onSuccess)
    fun createFollowUp(input: FollowUpInput, onSuccess: (String) -> Unit) = runOperation({ repository.createFollowUp(input) }, onSuccess)
    fun createCorrectiveFollowUp(workItemId: String, title: String, dueDate: String, privateNote: String, onSuccess: (String) -> Unit = {}) = runOperation({ repository.createCorrectiveFollowUp(workItemId, title, dueDate, privateNote) }) { id -> loadFieldEvidence(workItemId); onSuccess(id) }
    fun updateFollowUp(id:String,title:String,dueDate:String,note:String,reason:String,onSuccess:(String)->Unit={})=runOperation({repository.updateFollowUp(id,title,dueDate,note,reason);id},onSuccess)
    fun changeFollowUpState(id: String, target: String, reason: String, newDue: String?, onSuccess: (String) -> Unit = {}) = runOperation({ repository.changeFollowUpState(id, target, reason, newDue); id }, onSuccess)
    fun loadBusinessProfile() {
        val request=issueRequest("businessProfile")
        launchLoad {
            val profile = repository.businessProfile()
            if(isCurrent(request)) _state.update { it.copy(businessProfile = profile, businessProfileSaveStatus = profile?.modifiedAtEpochMillis?.let { timestamp -> SaveStatus.Saved(timestamp) } ?: SaveStatus.Idle) }
        }
    }
    fun loadFinalRecord(id: String) {
        val request=issueRequest("finalRecord"); _state.update { it.copy(finalRecord = null, generatingReport = false) }
        launchLoad { val value=repository.finalRecord(id); if(isCurrent(request)) _state.update { it.copy(finalRecord=value) } }
    }
    fun loadFinalRecordRevision(recordId: String, revisionId: String, renditionId: String? = null) {
        val request=issueRequest("finalRecord"); _state.update { it.copy(finalRecord = null, generatingReport = false) }
        launchLoad { val value=repository.finalRecordRevision(recordId,revisionId,renditionId); if(isCurrent(request)) _state.update { it.copy(finalRecord=value) } }
    }
    fun loadHistory(query: HistoryQuery) {
        val request=issueRequest("history")
        launchLoad { val value=repository.history(query); if(isCurrent(request)) _state.update { it.copy(history=value) } }
    }
    fun loadAttention() { val request=issueRequest("attention"); launchLoad { val value=repository.attention(); if(isCurrent(request)) _state.update { it.copy(attention=value) } } }
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
    fun loadDatasetSummary() { val request=issueRequest("datasetSummary"); launchLoad { val summary=repository.datasetSummary(); val attention=repository.attention(); if(isCurrent(request)) _state.update { it.copy(datasetSummary=summary,attention=attention) } } }
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

    fun scheduleWorkText(workItemId: String, rawValue: String) = serviceDraftAutosaveCoordinator.scheduleText(
        ServiceDraftFieldId(workItemId, ServiceDraftFieldKeys.WORK), rawValue, ServiceDraftValidators.alwaysValid(),
        writer = { value -> repository.savePublicWork(workItemId, value) },
        onSaved = { refreshServiceContext(workItemId) },
    )

    fun schedulePrivateText(workItemId: String, rawValue: String) = serviceDraftAutosaveCoordinator.scheduleText(
        ServiceDraftFieldId(workItemId, ServiceDraftFieldKeys.PRIVATE), rawValue, ServiceDraftValidators.privateNote(),
        writer = { value -> repository.savePrivateNote(workItemId, value) },
        onSaved = { refreshServiceContext(workItemId) },
    )

    fun scheduleQuestionValue(workItemId: String, questionId: String, rawValue: String) {
        val question = _state.value.inspection?.takeIf { it.workItemId == workItemId }?.questions?.firstOrNull { it.snapshotItemId == questionId } ?: return
        val validator = when {
            question.responseType == "NUMBER" -> if (rawValue.trim().isBlank() && !question.required) ServiceDraftValidators.alwaysValid() else ServiceDraftValidators.number()
            question.required -> ServiceDraftValidators.requiredText("Response")
            else -> ServiceDraftValidators.alwaysValid()
        }
        serviceDraftAutosaveCoordinator.scheduleQuestionText(
            question = ServiceDraftQuestionId(workItemId, questionId),
            fieldId = ServiceDraftFieldId(workItemId, ServiceDraftFieldKeys.questionValue(questionId)),
            fieldKind = ServiceDraftQuestionFieldKind.VALUE,
            rawValue = rawValue,
            validator = validator,
            writer = { value, drafts ->
                if (value.trim().isBlank() && !question.required) {
                    repository.saveQuestionTransition(workItemId, questionId, ResponseDisposition.UNANSWERED, null, null, drafts.issueReason, drafts.notApplicableReason)
                } else {
                    repository.saveQuestionTransition(workItemId, questionId, ResponseDisposition.VALUE, value, null, drafts.issueReason, drafts.notApplicableReason)
                }
            },
            onSaved = { refreshServiceContext(workItemId) },
        )
    }

    fun scheduleIssueDescription(workItemId: String, questionId: String, rawValue: String) = serviceDraftAutosaveCoordinator.scheduleQuestionText(
        question = ServiceDraftQuestionId(workItemId, questionId),
        fieldId = ServiceDraftFieldId(workItemId, ServiceDraftFieldKeys.questionIssue(questionId)),
        fieldKind = ServiceDraftQuestionFieldKind.ISSUE_REASON,
        rawValue = rawValue,
        validator = ServiceDraftValidators.issueDescription(),
        writer = { value, drafts -> repository.saveQuestionTransition(workItemId, questionId, ResponseDisposition.ISSUE_FOUND, null, value, drafts.issueReason, drafts.notApplicableReason) },
        onSaved = { refreshServiceContext(workItemId) },
    )

    fun scheduleNotApplicableReason(workItemId: String, questionId: String, rawValue: String) = serviceDraftAutosaveCoordinator.scheduleQuestionText(
        question = ServiceDraftQuestionId(workItemId, questionId),
        fieldId = ServiceDraftFieldId(workItemId, ServiceDraftFieldKeys.questionNotApplicable(questionId)),
        fieldKind = ServiceDraftQuestionFieldKind.NOT_APPLICABLE_REASON,
        rawValue = rawValue,
        validator = ServiceDraftValidators.notApplicableReason(),
        writer = { value, drafts -> repository.saveQuestionTransition(workItemId, questionId, ResponseDisposition.NOT_APPLICABLE, null, value, drafts.issueReason, drafts.notApplicableReason) },
        onSaved = { refreshServiceContext(workItemId) },
    )

    fun scheduleNotPerformedReason(workItemId: String, visitId: String, rawValue: String) {
        val obsoleteRecovery = supersedeNextDueRecovery(workItemId)
        viewModelScope.launch {
            obsoleteRecovery?.cancelAndJoin()
            serviceDraftAutosaveCoordinator.scheduleText(
                ServiceDraftFieldId(workItemId, ServiceDraftFieldKeys.NOT_PERFORMED_REASON), rawValue, ServiceDraftValidators.alwaysValid(),
                writer = { value -> repository.saveCompletionDraft(workItemId, "NOT_PERFORMED", false, value, null, null, null) },
                onSaved = { refreshServiceContext(workItemId) },
            )
        }
    }

    fun scheduleRecurrenceOverrideDate(workItemId: String, rawValue: String) {
        supersedeNextDueRecovery(workItemId)
        serviceDraftAutosaveCoordinator.scheduleRaw(
            ServiceDraftFieldId(workItemId, ServiceDraftFieldKeys.OVERRIDE_DATE), rawValue, ServiceDraftValidators.isoDate(),
        )
    }

    fun scheduleRecurrenceOverrideReason(workItemId: String, rawValue: String) {
        supersedeNextDueRecovery(workItemId)
        serviceDraftAutosaveCoordinator.scheduleRaw(
            ServiceDraftFieldId(workItemId, ServiceDraftFieldKeys.OVERRIDE_REASON), rawValue, ServiceDraftValidators.requiredText("Override reason"),
        )
    }

    fun chooseResponse(workItemId: String, questionId: String, disposition: ResponseDisposition) {
        serviceDraftAutosaveCoordinator.immediateQuestionChoice(
            question = ServiceDraftQuestionId(workItemId, questionId),
            fieldId = ServiceDraftFieldId(workItemId, "response:$questionId"),
            rawValue = disposition.name,
            discardRawFields = if (disposition == ResponseDisposition.NOT_APPLICABLE) {
                setOf(ServiceDraftFieldId(workItemId, ServiceDraftFieldKeys.questionValue(questionId)))
            } else emptySet(),
            writer = { drafts -> repository.saveQuestionTransition(workItemId, questionId, disposition, null, null, drafts.issueReason, drafts.notApplicableReason) },
            onSaved = { refreshServiceContext(workItemId) },
        )
    }

    fun chooseOutcome(workItemId: String, visitId: String, outcome: String) {
        val line = _state.value.completionLines.firstOrNull { it.workItemId == workItemId } ?: return
        val sameOutcome = line.outcome == outcome
        val fulfills = when (outcome) {
            "PERFORMED" -> true
            "PARTLY_PERFORMED" -> line.fulfillsCurrentObligation.takeIf { sameOutcome }
            "NOT_PERFORMED" -> false
            else -> null
        }
        val obsoleteRecovery = supersedeNextDueRecovery(workItemId)
        viewModelScope.launch {
            obsoleteRecovery?.cancelAndJoin()
            serviceDraftAutosaveCoordinator.immediateChoice(
                ServiceDraftFieldId(workItemId, "result:outcome"), outcome,
                writer = { repository.saveCompletionDraft(workItemId, outcome, fulfills, line.notPerformedReason.takeIf { outcome == "NOT_PERFORMED" }, line.confirmedNextDueDate.takeIf { sameOutcome }, line.nextDueDateCalculated.takeIf { sameOutcome }, line.nextDueOverrideReason.takeIf { sameOutcome }) },
                onSaved = { refreshServiceContext(workItemId) },
            )
        }
    }

    fun chooseFulfillment(workItemId: String, visitId: String, fulfills: Boolean) {
        val line = _state.value.completionLines.firstOrNull { it.workItemId == workItemId } ?: return
        val obsoleteRecovery = supersedeNextDueRecovery(workItemId)
        viewModelScope.launch {
            obsoleteRecovery?.cancelAndJoin()
            serviceDraftAutosaveCoordinator.immediateChoice(
                ServiceDraftFieldId(workItemId, "result:fulfillment"), fulfills.toString(),
                writer = { repository.saveCompletionDraft(workItemId, line.outcome, fulfills, line.notPerformedReason, line.confirmedNextDueDate, line.nextDueDateCalculated, line.nextDueOverrideReason) },
                onSaved = { refreshServiceContext(workItemId) },
            )
        }
    }

    fun useCalculatedNextDue(workItemId: String, visitId: String) {
        val line = _state.value.completionLines.firstOrNull { it.workItemId == workItemId } ?: return
        val calculatedDate = line.calculatedNextDueDate ?: return
        val capturedObligationId = line.capturedObligationId ?: return
        if (line.fulfillsCurrentObligation != true || line.confirmedNextDueDate != null) return
        val request = issueRequest("nextDueRecovery:$workItemId")
        serviceDraftAutosaveCoordinator.immediateConditionalChoice(
            ServiceDraftFieldId(workItemId, "result:nextDueRecovery"), calculatedDate,
            writer = {
                if (!isCurrent(request)) ServiceDraftChoiceWriteResult.Superseded
                else when (val result = repository.recoverMissingCalculatedNextDue(NextDueRecoveryRequest(workItemId, visitId, line.outcome!!, capturedObligationId, calculatedDate))) {
                    is NextDueRecoveryResult.Applied -> ServiceDraftChoiceWriteResult.Applied(result.savedAtEpochMillis)
                    NextDueRecoveryResult.Superseded -> ServiceDraftChoiceWriteResult.Superseded
                }
            },
            onSaved = {
                if (isCurrent(request) && _state.value.completionVisitId == visitId) {
                    if (_state.value.completionContext == null) refreshServiceContext(workItemId) else loadCompletion(visitId)
                }
            },
            onSuperseded = {
                if (isCurrent(request) && _state.value.completionVisitId == visitId) {
                    if (_state.value.completionContext == null) refreshServiceContext(workItemId) else loadCompletion(visitId)
                }
            },
        )
    }

    private fun supersedeNextDueRecovery(workItemId: String): Job? {
        issueRequest("nextDueRecovery:$workItemId")
        return serviceDraftAutosaveCoordinator.invalidate(ServiceDraftFieldId(workItemId, "result:nextDueRecovery"))
    }

    fun applyRecurrenceOverride(workItemId: String, visitId: String, date: String, reason: String) {
        val line = _state.value.completionLines.firstOrNull { it.workItemId == workItemId } ?: return
        val activeServiceWorkspace = _state.value.completionContext == null && _state.value.inspection?.workItemId == workItemId
        if (runCatching { LocalDate.parse(date.trim()) }.isFailure) {
            _state.update { it.copy(error = "Enter a date as YYYY-MM-DD") }
            return
        }
        if (reason.trim().isBlank()) {
            _state.update { it.copy(error = "Override reason is required") }
            return
        }
        val obsoleteRecovery = supersedeNextDueRecovery(workItemId)
        val request = issueRequest("completion")
        val saveContext = issueRequest("draftSaveContext")
        val lastSaved = _state.value.saveStatus.lastSavedCheckpoint()
        _state.update { current -> if (current.completionVisitId == visitId) current.copy(saveStatus = SaveStatus.Saving, error = null) else current }
        viewModelScope.launch {
            try {
                obsoleteRecovery?.cancelAndJoin()
                serviceDraftAutosaveCoordinator.cancelAndJoin(
                    ServiceDraftFieldId(workItemId, ServiceDraftFieldKeys.OVERRIDE_DATE),
                )
                serviceDraftAutosaveCoordinator.cancelAndJoin(
                    ServiceDraftFieldId(workItemId, ServiceDraftFieldKeys.OVERRIDE_REASON),
                )
                val savedAt = repository.saveCompletionDraft(workItemId, line.outcome, true, line.notPerformedReason, date, false, reason)
                var cleanupFailure: Exception? = null
                listOf(ServiceDraftFieldKeys.OVERRIDE_DATE, ServiceDraftFieldKeys.OVERRIDE_REASON).forEach { fieldKey ->
                    try {
                        repository.clearWorkingInputBuffer(workItemId, fieldKey)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        if (cleanupFailure == null) cleanupFailure = failure
                    }
                }
                try {
                    val workspace = if (activeServiceWorkspace) repository.serviceWorkspace(workItemId) else null
                    val published = workspace?.let { value ->
                        if (value.serviceProgress == null) value.copy(serviceProgress = progressFallback(value.inspection)) else value
                    }
                    val lines = if (!activeServiceWorkspace) repository.completionLines(visitId) else emptyList()
                    if (isCurrent(request) && isCurrent(saveContext)) _state.update { current ->
                        if (current.completionVisitId == visitId && (!activeServiceWorkspace || published != null)) current.copy(
                            serviceContext = if (activeServiceWorkspace) ServiceContext(published!!, activeVisitId = published.inspection.visitId, activeWorkItemId = published.inspection.workItemId) else current.serviceContext,
                            completionContext = if (activeServiceWorkspace) current.completionContext else CompletionContext(visitId, lines),
                            saveStatus = SaveStatus.Saved(savedAt),
                            contentRefreshError = cleanupFailure?.let { "Saved, but service draft cleanup needs attention: ${it.message ?: "cleanup failed"}" },
                        ) else current
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (isCurrent(request) && isCurrent(saveContext)) _state.update { current ->
                        if (current.completionVisitId == visitId) current.copy(
                            saveStatus = SaveStatus.Saved(savedAt),
                            contentRefreshError = cleanupFailure?.let { "Saved, but service draft cleanup needs attention: ${it.message ?: "cleanup failed"}" }
                                ?: "Saved, but the screen could not refresh: ${failure.message ?: "refresh failed"}",
                        ) else current
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (isCurrent(request) && isCurrent(saveContext)) _state.update { current ->
                    if (current.completionVisitId == visitId) current.copy(
                        saveStatus = SaveStatus.Failed(failure.message ?: "Override not saved", lastSaved),
                        error = failure.message ?: "Override not saved",
                    ) else current
                }
            }
        }
    }

    suspend fun flushServiceDraft(workItemId: String): ServiceDraftFlushResult = serviceDraftAutosaveCoordinator.flush(workItemId)
    suspend fun flushVisitDraft(visitId: String): ServiceDraftFlushResult = serviceDraftAutosaveCoordinator.flushVisit(visitId)
    fun retryServiceDraft(fieldId: ServiceDraftFieldId) = serviceDraftAutosaveCoordinator.retry(fieldId)
    fun retryFailedServiceEdits(workItemId: String) {
        serviceDraftStates.value.filterKeys { it.workItemId == workItemId }.forEach { (fieldId, fieldState) ->
            if (fieldState is ServiceDraftFieldState.Failed) serviceDraftAutosaveCoordinator.retry(fieldId)
        }
    }
    fun flushServiceDraftAsync(workItemId: String) {
        viewModelScope.launch { runCatching { flushServiceDraft(workItemId) } }
    }

    fun savePublicWork(workItemId: String, text: String) = persistInspectionDraft(workItemId) { repository.savePublicWork(workItemId, text) }
    fun markChecklistReviewed(workItemId: String) = persistInspectionDraft(workItemId) { repository.markChecklistReviewed(workItemId) }
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
    fun refreshVisitReportIdentity(visitId: String) = persistCompletionDraft(visitId,{ repository.refreshVisitReportIdentity(visitId) }) { val value=repository.visitReportIdentity(visitId); { current -> current.copy(visitReportIdentity=value) } }
    fun saveCompletion(workItemId: String, outcome: String?, fulfills: Boolean?, reason: String?, nextDue: String?, calculated: Boolean?, overrideReason: String?, visitId: String) = persistCompletionDraft(visitId,{
        repository.saveCompletionDraft(workItemId, outcome, fulfills, reason, nextDue, calculated, overrideReason)
    }) { val values=repository.completionLines(visitId); { current -> current.copy(completionContext=CompletionContext(visitId, values)) } }

    fun finalizeVisit(visitId: String) {
        if (_state.value.finalizing) return
        if (_state.value.completionVisitId != visitId) return
        val request = issueRequest("completion")
        val saveContext = issueRequest("draftSaveContext")
        _state.update { current -> if(current.completionVisitId == visitId) current.copy(finalizing = true, error = null) else current }
        viewModelScope.launch {
            try {
                val flushed = serviceDraftAutosaveCoordinator.flushVisit(visitId)
                if (!flushed.success) {
                    val details = buildList {
                        if (flushed.pendingFields.isNotEmpty()) add("${flushed.pendingFields.size} pending")
                        if (flushed.invalidFields.isNotEmpty()) add("${flushed.invalidFields.size} invalid")
                        if (flushed.failedFields.isNotEmpty()) add("${flushed.failedFields.size} failed")
                    }.joinToString(", ")
                    if (isCurrent(request) && isCurrent(saveContext)) _state.update { current -> if (current.completionVisitId == visitId) current.copy(finalizing = false, error = "Save inspection fields before finalizing${details.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}") else current }
                    return@launch
                }
                when (val result = repository.finalizeVisit(visitId)) {
                    is FinalizeResult.Success -> {
                        if(isCurrent(request) && isCurrent(saveContext)) _state.update { current -> if(current.completionVisitId == visitId) current.copy(finalizing = false, finalizedRecordId = result.recordId) else current }
                        refreshRootDataNonBlocking()
                    }
                    is FinalizeResult.Blocked -> if(isCurrent(request) && isCurrent(saveContext)) _state.update { current -> if(current.completionVisitId == visitId) current.copy(finalizing = false, error = result.message) else current }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if(isCurrent(request) && isCurrent(saveContext)) _state.update { current -> if(current.completionVisitId == visitId) current.copy(finalizing = false, error = failure.message ?: "Finalization failed") else current } }
        }
    }

    fun generateReport(recordId: String, revisionId: String? = null) {
        val service = reportService ?: return
        if (_state.value.generatingReport) return
        val currentRecord = _state.value.finalRecord ?: return
        if (currentRecord.public.recordId != recordId || (revisionId != null && currentRecord.public.revisionId != revisionId)) return
        val targetRevisionId = revisionId ?: currentRecord.public.revisionId
        val request = issueRequest("finalRecord")
        _state.update { it.copy(generatingReport = true, error = null) }
        viewModelScope.launch {
            try {
                val rendition = if (revisionId == null) service.generate(recordId) else service.generateRevision(recordId, revisionId)
                if (isCurrent(request)) _state.update { current -> if(current.matchesReportTarget(recordId,targetRevisionId)) current.copy(generatingReport = false, finalRecord = current.finalRecord?.copy(report = rendition)) else current }
                try { val refreshed=if(revisionId==null) repository.finalRecord(recordId) else repository.finalRecordRevision(recordId,revisionId,rendition.id); if(isCurrent(request)) _state.update { current -> if(current.matchesReportTarget(recordId,targetRevisionId)) current.copy(finalRecord=refreshed,contentRefreshError=null,generatingReport=false) else current } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { if(isCurrent(request)) _state.update { current -> if(current.matchesReportTarget(recordId,targetRevisionId)) current.copy(contentRefreshError = failure.message ?: "Report was generated, but the screen could not refresh",generatingReport=false) else current } }
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if(isCurrent(request)) _state.update { current -> if(current.matchesReportTarget(recordId,targetRevisionId)) current.copy(generatingReport = false, error = failure.message ?: "PDF generation failed") else current } }
        }
    }

    private fun UiState.matchesReportTarget(recordId:String,revisionId:String) = finalRecord?.public?.let { it.recordId == recordId && it.revisionId == revisionId } == true

    private fun persistCompletionDraft(visitId: String, write: suspend () -> Long, refresh: suspend () -> (UiState) -> UiState) {
        val request = issueRequest("completion")
        val saveContext = issueRequest("draftSaveContext")
        val lastSaved = _state.value.saveStatus.lastSavedCheckpoint()
        _state.update { current -> if(current.completionVisitId == visitId) current.copy(saveStatus = SaveStatus.Saving, error = null) else current }
        viewModelScope.launch {
            try {
                val savedAt = write()
                try {
                    val reducer = refresh()
                    if(isCurrent(request) && isCurrent(saveContext)) _state.update { current -> if(current.completionVisitId == visitId) reducer(current).copy(saveStatus = SaveStatus.Saved(savedAt), contentRefreshError = null) else current }
                }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { if(isCurrent(request) && isCurrent(saveContext)) _state.update { current -> if(current.completionVisitId == visitId) current.copy(saveStatus = SaveStatus.Saved(savedAt), contentRefreshError = failure.message ?: "Saved, but the screen could not refresh") else current } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if(isCurrent(request) && isCurrent(saveContext)) _state.update { current -> if(current.completionVisitId == visitId) current.copy(saveStatus = SaveStatus.Failed(failure.message ?: "Not saved", lastSaved)) else current } }
        }
    }

    private fun persistInspectionDraft(workItemId: String, write: suspend () -> Long) {
        val request = issueRequest("inspection")
        val saveContext = issueRequest("draftSaveContext")
        val lastSaved = _state.value.saveStatus.lastSavedCheckpoint() ?: _state.value.inspection?.takeIf { it.workItemId == workItemId }?.modifiedAtEpochMillis
        _state.update { current -> if (current.inspection?.workItemId == workItemId) current.copy(saveStatus = SaveStatus.Saving, error = null) else current }
        viewModelScope.launch {
            try {
                val savedAt = write()
                val refreshed = try { repository.serviceWorkspace(workItemId) } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) {
                    if (isCurrent(request) && isCurrent(saveContext)) _state.update { current -> if (current.inspection?.workItemId == workItemId) current.copy(saveStatus = SaveStatus.Saved(savedAt), contentRefreshError = failure.message ?: "Saved, but the screen could not refresh") else current }
                    return@launch
                }
                val published = refreshed?.let { value ->
                    if (value.serviceProgress == null) value.copy(serviceProgress = progressFallback(value.inspection)) else value
                }
                if (isCurrent(request) && isCurrent(saveContext)) _state.update { current ->
                    if (current.inspection?.workItemId == workItemId && published != null) current.copy(
                        serviceContext = ServiceContext(published, activeVisitId = published.inspection.visitId, activeWorkItemId = published.inspection.workItemId),
                        saveStatus = SaveStatus.Saved(savedAt),
                        contentRefreshError = null,
                    ) else current
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (isCurrent(request) && isCurrent(saveContext)) _state.update { current -> if (current.inspection?.workItemId == workItemId) current.copy(saveStatus = SaveStatus.Failed(failure.message ?: "Not saved", lastSaved)) else current }
            }
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
        val request = issueRequest("inspection")
        val saveContext = issueRequest("draftSaveContext")
        val lastSaved = state.value.saveStatus.lastSavedCheckpoint() ?: draft.modifiedAtEpochMillis
        _state.update { current -> if (current.inspection?.workItemId == draft.workItemId) current.copy(saveStatus = SaveStatus.Saving, error = null) else current }
        serviceDraftAutosaveCoordinator.immediateQuestionChoice(
            question = ServiceDraftQuestionId(draft.workItemId, questionId),
            fieldId = ServiceDraftFieldId(draft.workItemId, "response:$questionId"),
            rawValue = disposition.name,
            discardRawFields = if (disposition == ResponseDisposition.NOT_APPLICABLE) {
                setOf(ServiceDraftFieldId(draft.workItemId, ServiceDraftFieldKeys.questionValue(questionId)))
            } else emptySet(),
            writer = { drafts -> repository.saveQuestionTransition(draft.workItemId, questionId, disposition, value, reason, drafts.issueReason, drafts.notApplicableReason) },
            onSaved = { savedAt ->
                if (isCurrent(request) && isCurrent(saveContext)) _state.update { current ->
                    if (current.inspection?.workItemId == draft.workItemId) current.copy(saveStatus = SaveStatus.Saved(savedAt), error = null) else current
                }
                refreshServiceContext(draft.workItemId)
            },
            onFailed = { failure ->
                if (isCurrent(request) && isCurrent(saveContext)) _state.update { current ->
                    if (current.inspection?.workItemId == draft.workItemId) current.copy(saveStatus = SaveStatus.Failed(failure.message ?: "Draft write failed", lastSaved)) else current
                }
            },
        )
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

private fun progressFallback(draft: InspectionDraft): VisitServiceProgress {
    val hasActivity = draft.workPerformed.isNotBlank() || draft.privateInternalNote.isNotBlank() ||
        draft.questions.any { it.disposition !in setOf(ResponseDisposition.UNANSWERED, ResponseDisposition.NOT_CHECKED) } ||
        draft.rawInputs.isNotEmpty() || draft.outcome != null
    val status = serviceEntryStatus(
        hasActivity = hasActivity,
        hasUnresolvedRawBuffer = draft.rawInputs.isNotEmpty(),
        hasMissingIssueDescription = draft.issueMissingDescription.isNotEmpty(),
        hasInvalidExplicitAnswer = draft.invalidExplicitAnswers.isNotEmpty(),
    )
    val item = ServiceProgressItem(
        workItemId = draft.workItemId,
        position = 1,
        subjectType = draft.subjectType,
        equipmentId = draft.equipmentId,
        equipmentName = draft.equipmentName,
        equipmentReference = draft.equipmentReference,
        equipmentDescription = draft.equipmentDescription,
        serviceName = draft.serviceName,
        status = status,
        documentationMode = serviceDocumentationMode(draft.dispatchLocalRole, draft.dispatchDocumentationDisposition),
    )
    return VisitServiceProgress(
        visitId = draft.visitId,
        visitReference = draft.visitReference,
        customerName = draft.customerName,
        siteName = draft.siteName,
        serviceDate = "",
        items = listOf(item),
        groups = serviceProgressGroups(listOf(item)),
    )
}
