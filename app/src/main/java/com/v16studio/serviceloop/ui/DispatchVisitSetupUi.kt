package com.v16studio.serviceloop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopCheckbox as Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.ServiceLoopApplication
import com.v16studio.serviceloop.data.DispatchOutboxEditorDraft
import com.v16studio.serviceloop.data.DispatchOutboxItemDraft
import com.v16studio.serviceloop.data.DispatchOutboxStatus
import com.v16studio.serviceloop.data.DispatchPackageService
import com.v16studio.serviceloop.data.DispatchTeamDetail
import com.v16studio.serviceloop.data.EquipmentEntity
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.data.outboxStatus
import com.v16studio.serviceloop.domain.CustomerType
import com.v16studio.serviceloop.domain.DueService
import com.v16studio.serviceloop.domain.EquipmentSummary
import com.v16studio.serviceloop.domain.TemplateSummary
import com.v16studio.serviceloop.domain.VisitSiteOption
import com.v16studio.serviceloop.domain.WorkSubjectType
import com.v16studio.serviceloop.ui.designsystem.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private data class DispatchLoadedSetup(
    val sites: List<VisitSiteOption>,
    val teams: List<DispatchTeamDetail>,
    val visit: com.v16studio.serviceloop.data.DispatchOutboxVisitEntity?,
    val teamIds: List<String>,
    val items: List<Pair<DispatchOutboxItemDraft, List<String>>>,
)

internal fun dispatchItemIdForWorkKey(sessionIds: MutableMap<String, String>, workKey: String): String =
    sessionIds.getOrPut(workKey) { UUID.randomUUID().toString() }

private fun equipmentSummary(entity: EquipmentEntity, customerName: String, siteName: String) = EquipmentSummary(
    id = entity.id,
    name = entity.name,
    reference = entity.reference,
    technicianIdentifier = entity.technicianIdentifier,
    siteName = siteName,
    customerName = customerName,
    nearestDueDate = null,
    customerType = CustomerType.STANDARD,
)

@Composable
internal fun DispatchVisitEditorScreen(
    padding: PaddingValues,
    nav: NavHostController,
    visitId: String?,
    businessDate: LocalDate,
    serviceOverride: DispatchPackageService? = null,
    databaseOverride: ServiceLoopDatabase? = null,
    canConcludeDelegatedWork: Boolean = true,
    sitesOverride: List<VisitSiteOption> = emptyList(),
    dueServicesOverride: List<DueService> = emptyList(),
    templatesOverride: List<TemplateSummary> = emptyList(),
    businessZoneId: String = ZoneId.systemDefault().id,
    plannedWorkState: DueServicesProjection = DueServicesProjection.Available(dueServicesOverride),
    onRetryDueServices: () -> Unit = {},
    onRefreshTemplates: () -> Unit = {},
) {
    val context = LocalContext.current
    val database = databaseOverride ?: (context.applicationContext as ServiceLoopApplication).container.database
    val service = remember(serviceOverride) { serviceOverride ?: dispatchService(context) }
    val scope = rememberCoroutineScope()
    var localSites by remember { mutableStateOf(emptyList<VisitSiteOption>()) }
    var teams by remember { mutableStateOf(emptyList<DispatchTeamDetail>()) }
    var loadedVisit by remember { mutableStateOf<com.v16studio.serviceloop.data.DispatchOutboxVisitEntity?>(null) }
    var originalItems by remember { mutableStateOf(emptyList<Pair<DispatchOutboxItemDraft, List<String>>>()) }
    var originalOrder by remember { mutableStateOf(emptyList<String>()) }
    var unmatchedItems by remember { mutableStateOf(emptyMap<String, DispatchOutboxItemDraft>()) }
    var selectedTeams by remember { mutableStateOf(emptySet<String>()) }
    var assignments by remember { mutableStateOf(emptyMap<String, Set<String>>()) }
    var manager by rememberSaveable(visitId) { mutableStateOf("") }
    var instructions by rememberSaveable(visitId) { mutableStateOf("") }
    var appointmentZone by rememberSaveable(visitId) { mutableStateOf(businessZoneId) }
    var draft by rememberSaveable(visitId, stateSaver = VisitSetupDraftSaver) {
        mutableStateOf(VisitSetupDraft(serviceDate = businessDate.plusDays(1).toString()))
    }
    var baselineFingerprint by remember { mutableStateOf<String?>(null) }
    var initialized by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showTeams by remember { mutableStateOf(false) }
    var assigneeKey by remember { mutableStateOf<String?>(null) }
    var showCancel by remember { mutableStateOf(false) }
    var cancelReason by rememberSaveable(visitId) { mutableStateOf("") }
    val dispatchItemIdByWorkKey = remember(visitId) { mutableMapOf<String, String>() }

    fun fingerprint() = listOf(draft, manager, instructions, appointmentZone, selectedTeams.sorted(), assignments.toSortedMap().mapValues { it.value.sorted() }).toString()
    fun updateAssignments(key: String, value: Set<String>) { assignments = assignments.toMutableMap().also { it[key] = value } }

    fun load() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val dao = database.serviceLoopDao()
                val customers = dao.allCustomers().associateBy { it.id }
                val equipment = dao.allEquipment().filter { it.state == "ACTIVE" }.groupBy { it.siteId }
                val activePlans = dao.allPlans().filter { it.state == "ACTIVE" }.groupBy { it.equipmentId }
                val sites = dao.allSites().filter { it.state == "ACTIVE" }.mapNotNull { site ->
                    val customer = customers[site.customerId] ?: return@mapNotNull null
                    val siteEquipment = equipment[site.id].orEmpty()
                    VisitSiteOption(
                        site.id,
                        site.reference,
                        site.name,
                        customer.name,
                        siteEquipment.map { item -> equipmentSummary(entity = item, customerName = customer.name, siteName = site.name).copy(nearestDueDate = activePlans[item.id].orEmpty().minOfOrNull { plan -> plan.currentDueDate }) },
                        CustomerType.fromCode(customer.customerType),
                        siteEquipment.associate { item -> item.id to activePlans[item.id].orEmpty().mapNotNull { plan -> plan.reusableTemplateId }.toSet() },
                    )
                }
                val visit = visitId?.let { service.outboxVisits().firstOrNull { value -> value.dispatchVisitId == it } }
                val loadedItems = visitId?.let { service.outboxItems(it) }.orEmpty().map { (item, assignees) ->
                    DispatchOutboxItemDraft(
                        dispatchItemId = item.dispatchItemId,
                        subjectType = WorkSubjectType.fromCode(item.subjectType),
                        equipmentId = item.equipmentId,
                        equipmentDescription = item.equipmentDescription.orEmpty(),
                        taskName = item.taskName,
                        servicePlanReference = item.servicePlanReference,
                        dueDateSnapshot = item.dueDateSnapshot,
                        reusableTemplateId = item.reusableTemplateId,
                        assignedTechnicianIds = assignees,
                    ) to assignees
                }
                DispatchLoadedSetup(sites, service.teams(), visit, visitId?.let { service.outboxTeamIds(it) }.orEmpty(), loadedItems)
            }
            localSites = loaded.sites
            teams = loaded.teams
            loadedVisit = loaded.visit
            selectedTeams = loaded.teamIds.toSet()
            originalItems = loaded.items
            dispatchItemIdByWorkKey.clear()
            val due = (plannedWorkState as? DueServicesProjection.Available)?.rows.orEmpty()
            val selectedPlanIds = linkedSetOf<String>()
            val tasks = mutableListOf<VisitSetupTaskDraft>()
            val unmatched = linkedMapOf<String, DispatchOutboxItemDraft>()
            val loadedAssignments = linkedMapOf<String, Set<String>>()
            val order = mutableListOf<String>()
            loaded.items.forEach { (item, assignees) ->
                val dueService = item.servicePlanReference?.let { reference -> due.firstOrNull { it.planReference == reference && (loaded.visit == null || it.siteId == loaded.visit.siteId) } }
                if (dueService != null) {
                    val key = "PLAN:${dueService.planId}"
                    dispatchItemIdByWorkKey[key] = item.dispatchItemId
                    selectedPlanIds += dueService.planId
                    loadedAssignments[key] = assignees.toSet()
                    order += key
                } else if (item.servicePlanReference != null) {
                    val key = "UNMATCHED:${item.dispatchItemId}"
                    unmatched[key] = item
                    loadedAssignments[key] = assignees.toSet()
                    order += key
                } else {
                    val key = "TASK:${item.dispatchItemId}"
                    tasks += VisitSetupTaskDraft(item.dispatchItemId, item.taskName, item.subjectType, item.equipmentId, item.equipmentDescription, item.reusableTemplateId)
                    loadedAssignments[key] = assignees.toSet()
                    order += key
                }
            }
            unmatchedItems = unmatched
            originalOrder = order
            assignments = loadedAssignments
            draft = VisitSetupDraft(
                mode = VisitSetupMode.EXISTING,
                siteId = loaded.visit?.siteId,
                selectedPlanIds = selectedPlanIds,
                serviceDate = loaded.visit?.serviceDate ?: businessDate.plusDays(1).toString(),
                appointmentTime = loaded.visit?.appointmentLocalTime.orEmpty(),
                tasks = tasks,
            )
            manager = loaded.visit?.managerReference.orEmpty()
            instructions = loaded.visit?.instructions.orEmpty()
            appointmentZone = loaded.visit?.appointmentZoneId ?: businessZoneId
            initialized = true
            baselineFingerprint = fingerprint()
        }
    }
    val plannedWorkStatePhase = when (plannedWorkState) {
        DueServicesProjection.Unresolved -> "loading"
        is DueServicesProjection.Available -> "ready"
        is DueServicesProjection.Unavailable -> "error"
    }
    LaunchedEffect(visitId, dueServicesOverride.size, sitesOverride.size, plannedWorkStatePhase) { load() }

    val sites = sitesOverride.ifEmpty { localSites }
    val dueServices = (plannedWorkState as? DueServicesProjection.Available)?.rows.orEmpty()
    val templates = templatesOverride
    val readOnly = loadedVisit?.outboxStatus in setOf(DispatchOutboxStatus.CONCLUDED, DispatchOutboxStatus.CANCELED)
    val selectedSite = sites.firstOrNull { it.id == draft.siteId }
    val participants = teams.filter { it.team.id in selectedTeams }.flatMap { it.members.map { member -> member.first } }.distinctBy { it.technicianId }
    val dateValid = runCatching { LocalDate.parse(draft.serviceDate) }.isSuccess
    val timeValid = draft.appointmentTime.isBlank() || parseAppointmentTimeInput(draft.appointmentTime) != null
    val hasWork = draft.tasks.isNotEmpty() || unmatchedItems.isNotEmpty() || draft.selectedPlanIds.any { planId -> dueServices.any { it.planId == planId } }
    val commonValid = dateValid && timeValid && hasWork && if (draft.mode == VisitSetupMode.NEW) draft.newCustomer.isValidForCreate() else selectedSite != null
    val changed = initialized && baselineFingerprint != fingerprint()
    UnsavedChangesGuard(changed, nav)

    if (showTeams) DispatchTeamSelectionDialog(teams, selectedTeams, onDismiss = { showTeams = false }) { next ->
        val allowed = teams.filter { it.team.id in next }.flatMap { it.members }.map { it.first.technicianId }.toSet()
        if (assignments.values.any { values -> values.any { it !in allowed } }) error = "Update item assignments before removing those Team members."
        else { selectedTeams = next; showTeams = false }
    }
    assigneeKey?.let { key ->
        DispatchAssigneeDialog(participants, assignments[key].orEmpty(), onDismiss = { assigneeKey = null }) { next -> updateAssignments(key, next); assigneeKey = null }
    }
    if (showCancel && loadedVisit != null) DispatchVisitCancelDialog(cancelReason, loadedVisit!!.outboxStatus == DispatchOutboxStatus.DISPATCHED, onDismiss = { showCancel = false; cancelReason = "" }) { reason ->
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { service.cancelOutboxVisits(listOf(requireNotNull(visitId)), reason) } }
                .onSuccess { showCancel = false; cancelReason = ""; load() }
                .onFailure { if (it is CancellationException) throw it else error = it.message }
        }
    }

    fun buildItems(): List<DispatchOutboxItemDraft> {
        val byKey = linkedMapOf<String, DispatchOutboxItemDraft>()
        dueServices.filter { it.planId in draft.selectedPlanIds }.forEach { due ->
            val key = "PLAN:${due.planId}"
            val original = dispatchItemIdByWorkKey[key]?.let { id -> originalItems.firstOrNull { it.first.dispatchItemId == id }?.first }
            byKey[key] = original?.copy(assignedTechnicianIds = assignments[key].orEmpty().toList())
                ?: DispatchOutboxItemDraft(
                    dispatchItemId = dispatchItemIdForWorkKey(dispatchItemIdByWorkKey, key),
                    subjectType = WorkSubjectType.EQUIPMENT,
                    equipmentId = due.equipmentId,
                    taskName = due.planName,
                    servicePlanReference = due.planReference,
                    dueDateSnapshot = due.dueDate,
                    assignedTechnicianIds = assignments[key].orEmpty().toList(),
                )
        }
        draft.tasks.forEach { task ->
            val key = "TASK:${task.stableUiId}"
            byKey[key] = DispatchOutboxItemDraft(
                dispatchItemId = task.stableUiId,
                subjectType = task.subjectType,
                equipmentId = task.equipmentId,
                equipmentDescription = task.equipmentDescription,
                taskName = task.taskName,
                reusableTemplateId = task.reusableTemplateId,
                assignedTechnicianIds = assignments[key].orEmpty().toList(),
            )
        }
        unmatchedItems.forEach { (key, item) -> byKey[key] = item.copy(assignedTechnicianIds = assignments[key].orEmpty().toList()) }
        return (originalOrder.filter { it in byKey } + byKey.keys.filter { it !in originalOrder }).distinct().mapNotNull { byKey[it] }
    }

    fun save() {
        error = null
        when {
            selectedTeams.isEmpty() -> error = "Choose at least one Team."
            participants.isEmpty() -> error = "Selected Teams need at least one Technician."
            !commonValid -> error = "Complete the shared visit setup before saving."
            else -> {
                busy = true
                val editor = DispatchOutboxEditorDraft(
                    dispatchVisitId = visitId,
                    expectedModifiedAtEpochMillis = loadedVisit?.modifiedAtEpochMillis,
                    managerReference = manager,
                    siteId = draft.siteId.orEmpty(),
                    serviceDate = draft.serviceDate,
                    appointmentLocalTime = draft.appointmentTime.ifBlank { null },
                    appointmentZoneId = appointmentZone,
                    instructions = instructions,
                    teamIds = selectedTeams.toList().sorted(),
                    items = buildItems(),
                    newCustomerSite = if (draft.mode == VisitSetupMode.NEW) draft.newCustomer.toInput() else null,
                )
                scope.launch {
                    val result = runCatching { withContext(Dispatchers.IO) { service.saveOutboxVisit(editor) } }
                    if (result.isSuccess) {
                        withContext(Dispatchers.Main.immediate) { nav.popBackStack() }
                    } else {
                        result.exceptionOrNull()?.let { if (it is CancellationException) throw it else error = it.message ?: "Could not save this Dispatch Visit." }
                    }
                    busy = false
                }
            }
        }
    }

    val setupModifier = Modifier.padding(padding).fillMaxWidth().testTag(if (visitId == null) "dispatch-new-visit" else "dispatch-visit-editor")
    VisitSetupForm(
        modifier = setupModifier,
        draft = draft,
        sites = sites,
        templates = templates,
        businessDate = businessDate,
        editable = !readOnly && !busy,
        busy = busy,
        errorMessage = error,
        onDraftChange = { draft = it },
        plannedWorkState = plannedWorkState,
        onRetryDueServices = onRetryDueServices,
        allowTemplateCreation = !readOnly,
        onRefreshTemplates = onRefreshTemplates,
        templateReturnNav = nav,
        startInConfiguration = loadedVisit != null,
        onCreateTemplate = { nav.navigate("template/new?returnTo=visit-setup") },
        preludeItems = {
            if (loadedVisit != null) {
                item {
                    val visit = requireNotNull(loadedVisit)
                    ServiceLoopStatusBadge(visit.outboxStatus.name)
                    if (visit.outboxStatus == DispatchOutboxStatus.DISPATCHED) ServiceLoopNotice("Dispatched", "Material changes create a later version only when this visit is exported again.", ServiceLoopNoticeKind.Info)
                    if (readOnly) ServiceLoopNotice("Read-only", "Canceled and Concluded visits cannot be edited.", ServiceLoopNoticeKind.Info)
                }
            }
        },
        extensionItems = {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = ServiceLoopUiTokens.Layout.pageInsetCompact), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)) {
                    ServiceLoopSectionHeading("Assignment")
                    val teamLabel = teams.filter { it.team.id in selectedTeams }.joinToString(" · ") { it.team.name }.ifBlank { "Choose teams" }
                    ServiceLoopSecondaryButton(teamLabel, { showTeams = true }, Modifier.fillMaxWidth().testTag("dispatch-choose-teams"), enabled = !readOnly && !busy)
                    val planEntries = dueServices.filter { it.planId in draft.selectedPlanIds }.map { "PLAN:${it.planId}" to it.planName }
                    val taskEntries = draft.tasks.map { "TASK:${it.stableUiId}" to it.taskName }
                    (planEntries + taskEntries).forEach { (key, label) ->
                        val names = assignments[key].orEmpty().mapNotNull { id -> participants.firstOrNull { it.technicianId == id }?.displayName }
                        Row(Modifier.fillMaxWidth().clickable(enabled = !readOnly && !busy) { assigneeKey = key }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(label); Text(if (names.isEmpty()) "Everyone on selected teams" else names.joinToString(" · "), style = MaterialTheme.typography.bodySmall) }
                            Text("Assign", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (unmatchedItems.isNotEmpty()) {
                        ServiceLoopNotice("Planned work preserved", "Some saved plan items are not in the current due-work projection. They remain unchanged and will not be silently converted.", ServiceLoopNoticeKind.Warning)
                    }
                }
            }
            item {
                var expanded by rememberSaveable(visitId) { mutableStateOf(false) }
                Column(Modifier.fillMaxWidth().padding(horizontal = ServiceLoopUiTokens.Layout.pageInsetCompact).clickable(enabled = !readOnly) { expanded = !expanded }.padding(vertical = ServiceLoopUiTokens.Space.md), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
                    Text("Reference & instructions", style = MaterialTheme.typography.titleMedium)
                    Text(if (manager.isBlank() && instructions.isBlank()) "Optional" else "Details entered", style = MaterialTheme.typography.bodySmall)
                    if (expanded) {
                        Spacer(Modifier.height(ServiceLoopUiTokens.Space.sm))
                        ServiceLoopTextField(manager, { manager = it }, "Reference", enabled = !readOnly, modifier = Modifier.testTag("dispatch-manager-reference"))
                        ServiceLoopLongTextEditor(instructions, { instructions = it }, "Instructions", private = false, enabled = !readOnly)
                    }
                }
            }
        },
        actionItems = {
            item {
                ServiceLoopPinnedBar {
                    if (!readOnly) ServiceLoopPrimaryButton(if (visitId == null) "Save draft" else "Save changes", ::save, Modifier.fillMaxWidth().testTag("dispatch-save-visit"), enabled = !busy && commonValid && selectedTeams.isNotEmpty() && participants.isNotEmpty(), busy = busy)
                    if (loadedVisit?.outboxStatus in setOf(DispatchOutboxStatus.DRAFT, DispatchOutboxStatus.DISPATCHED)) ServiceLoopSecondaryButton("Cancel visit", { cancelReason = ""; showCancel = true }, Modifier.fillMaxWidth().testTag("dispatch-cancel-visit"), enabled = !busy)
                    if (canConcludeDelegatedWork && loadedVisit?.outboxStatus == DispatchOutboxStatus.DISPATCHED) ServiceLoopSecondaryButton("Mark concluded", { scope.launch { runCatching { withContext(Dispatchers.IO) { service.concludeOutboxVisits(listOf(requireNotNull(visitId))) } }.onSuccess { nav.popBackStack() }.onFailure { if (it is CancellationException) throw it else error = it.message } } }, Modifier.fillMaxWidth().testTag("dispatch-conclude-visit"), enabled = !changed && !busy)
                    if (canConcludeDelegatedWork && loadedVisit?.outboxStatus == DispatchOutboxStatus.CONCLUDED) ServiceLoopPrimaryButton("Reopen", { scope.launch { runCatching { withContext(Dispatchers.IO) { service.reopenOutboxVisits(listOf(requireNotNull(visitId))) } }.onSuccess { load() }.onFailure { if (it is CancellationException) throw it else error = it.message } } }, Modifier.fillMaxWidth().testTag("dispatch-reopen-visit"))
                }
            }
        },
    )
}

@Composable
private fun DispatchTeamSelectionDialog(teams: List<DispatchTeamDetail>, selected: Set<String>, onDismiss: () -> Unit, onApply: (Set<String>) -> Unit) {
    var staged by remember(selected) { mutableStateOf(selected) }
    AlertDialog(
        modifier = Modifier.testTag("dispatch-team-picker"), onDismissRequest = onDismiss, title = { Text("Choose teams") },
        text = { Column { teams.forEach { team -> Row(Modifier.fillMaxWidth().clickable { staged = if (team.team.id in staged) staged - team.team.id else staged + team.team.id }.testTag("dispatch-team-${team.team.id}"), verticalAlignment = Alignment.CenterVertically) { Checkbox(team.team.id in staged, null); Text(team.team.name) } }; if (teams.isEmpty()) Text("Create a team in Coordinator tools first.") } },
        dismissButton = if (teams.isEmpty()) null else ({ ServiceLoopTextAction("Cancel", onDismiss) }),
        confirmButton = if (teams.isEmpty()) ({ ServiceLoopTextAction("Close", onDismiss) }) else ({ ServiceLoopPrimaryButton("Apply", { onApply(staged) }, Modifier.testTag("dispatch-team-apply")) }),
    )
}

@Composable
private fun DispatchAssigneeDialog(participants: List<com.v16studio.serviceloop.data.DispatchTechnicianEntity>, selected: Set<String>, onDismiss: () -> Unit, onApply: (Set<String>) -> Unit) {
    var staged by remember(selected) { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Assign work") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) { Text("Leave everyone unchecked to assign this work to everyone on the selected Teams.", style = MaterialTheme.typography.bodySmall); participants.forEach { person -> Row(Modifier.fillMaxWidth().heightIn(min = ServiceLoopUiTokens.Size.touchMin).clickable { staged = if (person.technicianId in staged) staged - person.technicianId else staged + person.technicianId }.padding(vertical = ServiceLoopUiTokens.Space.xs), verticalAlignment = Alignment.CenterVertically) { Checkbox(person.technicianId in staged, null, contentDescription = "Assign ${person.displayName}"); Text(person.displayName) } } } },
        dismissButton = { ServiceLoopTextAction("Cancel", onDismiss) },
        confirmButton = { ServiceLoopPrimaryButton("Apply", { onApply(staged) }) },
    )
}

@Composable
private fun DispatchVisitCancelDialog(reason: String, dispatched: Boolean, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    var staged by remember(reason) { mutableStateOf(reason) }
    AlertDialog(
        modifier = Modifier.testTag("dispatch-cancel-dialog"), onDismissRequest = onDismiss, title = { Text("Cancel Visit") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)) { ServiceLoopTextField(staged, { staged = it }, "Cancellation reason", modifier = Modifier.testTag("dispatch-cancel-reason")); Text(if (dispatched) "A newer work package must be created to communicate this cancellation to technicians." else "This Draft stays in local cancellation history and is not exported.", color = LocalServiceLoopTokens.current.textSecondary) } },
        dismissButton = { ServiceLoopTextAction("Cancel", onDismiss) },
        confirmButton = { ServiceLoopPrimaryButton("Cancel visit", { onApply(staged.trim()) }, Modifier.testTag("dispatch-cancel-confirm"), enabled = staged.isNotBlank()) },
    )
}
