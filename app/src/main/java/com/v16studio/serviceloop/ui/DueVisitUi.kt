package com.v16studio.serviceloop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.domain.*
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopActionStack
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopButtonAdapter as Button
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopCardAdapter as Card
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChoiceGroup
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopEntityRecord
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFilterSelector
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFilterSelectorRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopOutlinedButtonAdapter as OutlinedButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPresetChoiceGroup
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopResponsivePair
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSecondaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextButtonAdapter as TextButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopWorkItemRow
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons
import java.time.LocalDate
import org.json.JSONObject
import kotlinx.coroutines.launch

@Composable
internal fun DueServicesScreen(values: List<DueService>, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController, modifier: Modifier = Modifier, initialBucket: DueBucket? = null, topContent: (@Composable () -> Unit)? = null) {
    var dateFilter by rememberSaveable(initialBucket) {
        mutableStateOf(DueServiceDateFilter.entries.firstOrNull { it.bucket == initialBucket } ?: DueServiceDateFilter.ALL)
    }
    var visitFilter by rememberSaveable { mutableStateOf(DueServiceVisitFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    if (!state.dueServicesReady) {
        DailyEmpty(padding, state.dueServicesError?.let { "Unable to read due services — $it" } ?: "Reading due services", state.dueServicesError?.let { viewModel::retryDueServices })
        return
    }
    val filtered = filterDueServices(values, dateFilter, visitFilter, query)
    val selectedRows = values.filter { it.planId in selected }; val selectionSite = selectedRows.firstOrNull()?.siteId
    LazyColumn(modifier.padding(padding).testTag("due-services-list"), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        topContent?.let { action -> item { action() } }
        item {
            DailyField(query, { query = it }, "Search due services")
            ServiceLoopFilterSelectorRow(
                first = {
                    ServiceLoopFilterSelector(
                        label = "Due date",
                        selected = dateFilter,
                        options = DueServiceDateFilter.entries.map { it to it.label },
                        onSelected = { dateFilter = it },
                        testTag = "due-date-selector",
                    )
                },
                second = {
                    ServiceLoopFilterSelector(
                        label = "Visit",
                        selected = visitFilter,
                        options = DueServiceVisitFilter.entries.map { it to it.label },
                        onSelected = { visitFilter = it },
                        testTag = "due-visit-selector",
                    )
                },
            )
            state.dueServicesError?.let { Text("Due services could not update — showing the last saved database result.", color = MaterialTheme.colorScheme.error) }
        }
        if (filtered.isEmpty()) item { Text("No services match these filters.") }
        items(filtered, key = { it.planId }) { due -> val selectable=due.claimedVisitId==null&&(selectionSite==null||selectionSite==due.siteId); ServiceLoopEntityRecord("${due.planReference} · ${due.planName}","${due.equipmentReference} · ${due.equipmentName}\n${due.customerName} · ${due.siteName}",listOf("Due ${due.dueDate}", due.bucket.name.lowercase().replace('_',' '), due.claimedVisitId?.let { "Has visit" }).filterNotNull().joinToString(" · "),selected=due.planId in selected,onClick={nav.navigate(due.claimedVisitId?.let{"visit/$it"}?:"plan/${due.planId}")},actionDescription=if(due.claimedVisitId==null) "Open service plan ${due.planReference} ${due.planName}" else "Open existing visit ${due.planReference} ${due.planName}",selectionChecked=if(selectable) due.planId in selected else null,onSelectionChange=if(selectable) { checked->if(checked)selected=selected+due.planId else selected=selected-due.planId } else null) }
        if (selected.isNotEmpty()) item { Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) { ServiceLoopSecondaryButton("Book selected",{ nav.currentBackStackEntry?.savedStateHandle?.set("visit-setup-plan-ids", ArrayList(selected)); nav.navigate("visit/new") }, enabled = !state.operationInProgress, modifier = Modifier.weight(1f).fillMaxHeight()); ServiceLoopPrimaryButton("Start selected",{ viewModel.createVisit(selected, "WORKING", state.businessDate.toString(), null) { nav.navigate("visit/$it") } }, enabled = !state.operationInProgress, modifier = Modifier.weight(1f).fillMaxHeight()) } }
    }
}

private data class NewVisitTaskDraft(
    val taskName: String,
    val subjectType: WorkSubjectType,
    val equipmentId: String?,
    val equipmentDescription: String,
    val templateId: String?,
) {
    fun toInput() = AdHocWorkInput(taskName, subjectType, equipmentId, equipmentDescription, templateId)
}

private val NewVisitTaskDraftListSaver = listSaver<List<NewVisitTaskDraft>, String>(
    save = { tasks ->
        tasks.map { task ->
            JSONObject().apply {
                put("taskName", task.taskName)
                put("subjectType", task.subjectType.name)
                put("equipmentId", task.equipmentId)
                put("equipmentDescription", task.equipmentDescription)
                put("templateId", task.templateId)
            }.toString()
        }
    },
    restore = { values ->
        values.mapNotNull { encoded ->
            runCatching {
                val value = JSONObject(encoded)
                NewVisitTaskDraft(
                    taskName = value.getString("taskName"),
                    subjectType = WorkSubjectType.valueOf(value.getString("subjectType")),
                    equipmentId = value.optString("equipmentId").takeIf { it.isNotBlank() },
                    equipmentDescription = value.optString("equipmentDescription"),
                    templateId = value.optString("templateId").takeIf { it.isNotBlank() },
                )
            }.getOrNull()
        }
    },
)

@Composable
private fun NewVisitTaskEditor(
    taskName: String,
    onTaskName: (String) -> Unit,
    subjectType: WorkSubjectType,
    onSubjectType: (WorkSubjectType) -> Unit,
    equipmentId: String?,
    onEquipmentId: (String?) -> Unit,
    equipmentDescription: String,
    onEquipmentDescription: (String) -> Unit,
    templates: List<TemplateSummary>,
    templateId: String?,
    onTemplateId: (String?) -> Unit,
    equipment: List<EquipmentSummary>,
    allowKnownEquipment: Boolean,
    valid: Boolean,
    editing: Boolean,
    onSave: () -> Unit,
) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (editing) "Edit task" else "Add task", fontWeight = FontWeight.Bold)
            DailyField(taskName, onTaskName, "Task name · Required")
            Text("Subject", fontWeight = FontWeight.Medium)
            ServiceLoopChoiceGroup(listOf(WorkSubjectType.SITE to "Site", WorkSubjectType.EQUIPMENT to "Equipment"), subjectType, onSubjectType, testTagPrefix = "task-subject")
            if (subjectType == WorkSubjectType.EQUIPMENT) {
                if (!allowKnownEquipment) {
                    Text("No registered equipment is available in a new one-time branch.", style = MaterialTheme.typography.bodySmall)
                    DailyField(equipmentDescription, onEquipmentDescription, "Equipment description · Optional")
                } else {
                    ServiceLoopChoiceGroup(listOf<Pair<String?, String>>(null to "No specific equipment yet") + equipment.map { it.id to "${it.name} · ${it.reference}" }, equipmentId, onEquipmentId, testTagPrefix = "task-equipment")
                    if (equipmentId == null) DailyField(equipmentDescription, onEquipmentDescription, "Equipment description · Optional")
                }
            }
            Text("Inspection checklist", fontWeight = FontWeight.Medium)
            ServiceLoopChoiceGroup(listOf<Pair<String?, String>>(null to "None") + templates.filter { it.state == "ACTIVE" }.map { it.id to "${it.name} · r${it.revisionNumber}" }, templateId, onTemplateId, testTagPrefix = "task-template")
            OutlinedButton(onSave, enabled = valid, modifier = Modifier.fillMaxWidth().testTag(if (editing) "update-task" else "add-task")) { Text(if (editing) "Update task" else "Add task") }
        }
    }
}

@Composable
private fun NewVisitTaskRow(index: Int, task: NewVisitTaskDraft, templates: List<TemplateSummary>, equipment: List<EquipmentSummary>, onEdit: () -> Unit, onRemove: () -> Unit) {
    ServiceLoopEntityRecord(
        title = task.taskName,
        context = when (task.subjectType) {
            WorkSubjectType.SITE -> "Site"
            WorkSubjectType.EQUIPMENT -> task.equipmentId?.let { id -> equipment.firstOrNull { it.id == id }?.name } ?: task.equipmentDescription.ifBlank { "Equipment not specified" }
        },
        metadata = task.templateId?.let { id -> templates.firstOrNull { it.id == id }?.let { "Inspection checklist · ${it.name} · r${it.revisionNumber}" } } ?: "No checklist",
        modifier = Modifier.testTag("visit-task-$index"),
        onClick = onEdit,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onEdit, Modifier.weight(1f).testTag("visit-task-edit-$index")) { Text("Edit") }
        TextButton(onRemove, Modifier.weight(1f).testTag("visit-task-remove-$index")) { Text("Remove") }
    }
}

@Composable
internal fun NewVisitScreen(sites: List<VisitSiteOption>, dueServices: List<DueService>, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController, initialPlanIds: List<String> = emptyList()) {
    var mode by rememberSaveable { mutableStateOf("EXISTING") }
    var siteId by rememberSaveable { mutableStateOf(dueServices.firstOrNull { it.planId in initialPlanIds }?.siteId) }
    var selectedPlans by rememberSaveable { mutableStateOf(initialPlanIds) }
    var date by rememberSaveable { mutableStateOf(state.businessDate.plusDays(1).toString()) }
    var siteQuery by rememberSaveable { mutableStateOf("") }
    var customerName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var locationLabel by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var taskName by rememberSaveable { mutableStateOf("") }
    var subjectType by rememberSaveable { mutableStateOf(WorkSubjectType.SITE) }
    var equipmentId by rememberSaveable { mutableStateOf<String?>(null) }
    var equipmentDescription by rememberSaveable { mutableStateOf("") }
    var templateId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var tasks by rememberSaveable(stateSaver = NewVisitTaskDraftListSaver) { mutableStateOf<List<NewVisitTaskDraft>>(emptyList()) }
    var pendingSiteId by remember { mutableStateOf<String?>(null) }
    var pendingSiteChange by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf<String?>(null) }
    val site = sites.firstOrNull { it.id == siteId }
    val setupRoute = nav.currentBackStackEntry?.destination?.route ?: "visit/new"
    val initialSite = dueServices.firstOrNull { it.planId in initialPlanIds }?.siteId
    val matchingSites = sites.filter { option -> siteQuery.isNotBlank() || option.customerType == CustomerType.STANDARD }.filter { option -> siteQuery.isBlank() || option.customerName.contains(siteQuery, true) || option.name.contains(siteQuery, true) || option.reference.contains(siteQuery, true) }
    val available = dueServices.filter { it.siteId == siteId && it.claimedVisitId == null }
    val equipment = site?.equipment.orEmpty()
    val allowKnownEquipment = mode == "EXISTING"
    val taskValid = taskName.trim().isNotBlank() && taskName.trim().length <= 200 && when {
        subjectType == WorkSubjectType.SITE -> equipmentId == null && equipmentDescription.isBlank()
        !allowKnownEquipment -> equipmentId == null && equipmentDescription.length <= 500
        else -> equipmentDescription.isBlank() && (equipmentId == null || equipment.any { it.id == equipmentId })
    }
    val validDate = runCatching { LocalDate.parse(date) }.isSuccess
    val visitValid = validDate && !state.operationInProgress && if (mode == "ONE_TIME") customerName.trim().isNotBlank() && tasks.isNotEmpty() else site != null && (selectedPlans.isNotEmpty() || tasks.isNotEmpty())
    LaunchedEffect(initialPlanIds, dueServices) { if (siteId == null && initialPlanIds.isNotEmpty()) siteId = dueServices.firstOrNull { it.planId in initialPlanIds }?.siteId }
    UnsavedChangesGuard(mode != "EXISTING" || siteId != initialSite || selectedPlans != initialPlanIds || date != state.businessDate.plusDays(1).toString() || siteQuery.isNotBlank() || customerName.isNotBlank() || phone.isNotBlank() || email.isNotBlank() || locationLabel.isNotBlank() || address.isNotBlank() || tasks.isNotEmpty() || taskName.isNotBlank(), nav)
    fun resetTask() { taskName = ""; subjectType = WorkSubjectType.SITE; equipmentId = null; equipmentDescription = ""; templateId = null; editingIndex = null }
    fun changeSite(nextSiteId: String) { if (tasks.isEmpty()) { siteId = nextSiteId; selectedPlans = emptyList(); resetTask() } else { pendingSiteId = nextSiteId; pendingSiteChange = true } }
    fun clearSiteSelection() { siteId = null; selectedPlans = emptyList(); siteQuery = ""; resetTask() }
    fun requestSiteChange() { if (tasks.isEmpty()) clearSiteSelection() else { pendingSiteId = null; pendingSiteChange = true } }
    fun applyMode(nextMode: String) { mode = nextMode; siteId = if (nextMode == "EXISTING") siteId else null; selectedPlans = emptyList(); tasks = emptyList(); resetTask() }
    fun requestModeChange(nextMode: String) { if (nextMode == mode) return; if (tasks.isEmpty()) applyMode(nextMode) else pendingMode = nextMode }
    fun save(targetState: String) {
        val inputs = tasks.map { it.toInput() }
        val success: (String) -> Unit = { id ->
            if (targetState == "WORKING") {
                viewModel.resolveWorkingVisitResume(id) { workItemId ->
                    if (workItemId == null) {
                        nav.navigate("visit/$id") { popUpTo(setupRoute) { inclusive = true } }
                    } else {
                        // Put the Visit overview underneath the active Service so the
                        // workspace has a truthful logical context and route-aware exit.
                        nav.navigate("visit/$id") { popUpTo(setupRoute) { inclusive = true } }
                        nav.navigate("inspection/$workItemId")
                    }
                }
            } else nav.navigate("visit/$id") { popUpTo(setupRoute) { inclusive = true } }
        }
        if (mode == "ONE_TIME") viewModel.createOneTimeVisit(OneTimeVisitInput(customerName, phone, email, locationLabel, address), inputs, targetState, if (targetState == "WORKING") state.businessDate.toString() else date, null, success)
        else viewModel.createVisitForSite(site!!.id, selectedPlans, inputs, targetState, if (targetState == "WORKING") state.businessDate.toString() else date, null, success)
    }
    if (pendingSiteChange) AlertDialog(onDismissRequest = { pendingSiteChange = false; pendingSiteId = null }, title = { Text("Change site?") }, text = { Text("Tasks added for this site will be cleared.") }, confirmButton = { TextButton({ pendingSiteId?.let { siteId = it } ?: clearSiteSelection(); tasks = emptyList(); pendingSiteChange = false; pendingSiteId = null }) { Text("Change site") } }, dismissButton = { TextButton({ pendingSiteChange = false; pendingSiteId = null }) { Text("Keep current site") } })
    pendingMode?.let { requested -> AlertDialog(onDismissRequest = { pendingMode = null }, title = { Text("Change customer mode?") }, text = { Text("Tasks already added will be cleared.") }, confirmButton = { TextButton({ applyMode(requested); pendingMode = null }) { Text("Change mode") } }, dismissButton = { TextButton({ pendingMode = null }) { Text("Keep current mode") } }) }
    EditorColumn(padding, state, tag = "new-visit-form") {
        item { DailyHeading("Set up visit"); Text("Create local tasks for one site, or start with a one-time customer.") }
        item { ServiceLoopChoiceGroup(listOf("EXISTING" to "Existing", "ONE_TIME" to "One-time"), mode, { value -> if (initialPlanIds.isEmpty()) requestModeChange(value) }, testTagPrefix = "visit-mode") }
        if (mode == "ONE_TIME") item { DailyHeading("One-time customer"); DailyField(customerName, { customerName = it }, "Customer name · Required"); DailyField(phone, { phone = it }, "Phone"); DailyField(email, { email = it }, "Email"); DailyField(locationLabel, { locationLabel = it }, "Location label"); DailyField(address, { address = it }, "Service address") }
        else {
            item { Text("Customer / site", fontWeight = FontWeight.Bold); if (site != null) Row(verticalAlignment = Alignment.CenterVertically) { Text("${site.customerName} · ${site.name}", Modifier.weight(1f)); if (initialPlanIds.isEmpty()) TextButton(::requestSiteChange) { Text("Change") } } else DailyField(siteQuery, { siteQuery = it }, "Find customer or site") }
            if (site == null) items(matchingSites, key = { "visit-site-${it.id}" }) { option -> ServiceLoopEntityRecord("${option.reference} · ${option.name}", option.customerName, if (option.customerType == CustomerType.ONE_TIME) "One-time" else null, modifier = Modifier.testTag("visit-site-${option.id}"), onClick = { changeSite(option.id) }) }
            if (site == null && matchingSites.isEmpty()) item { Text(if (sites.isEmpty()) "Add a customer site before creating a visit." else "No matching customer sites.") }
            if (site != null && site.customerType == CustomerType.ONE_TIME) item { Text("One-time customers use ad-hoc work until made Standard.") }
            if (site != null && site.customerType == CustomerType.STANDARD) item { Text("Planned work", fontWeight = FontWeight.Bold); if (!state.dueServicesReady) { Text("Planned services are unavailable.", color = MaterialTheme.colorScheme.error); state.dueServicesError?.let { OutlinedButton({ viewModel.retryDueServices() }, Modifier.fillMaxWidth()) { Text("Retry") } } }; available.forEach { due -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(due.planId in selectedPlans, { checked -> selectedPlans = if (checked) selectedPlans + due.planId else selectedPlans - due.planId }); Text("${due.equipmentName} · ${due.planName} · Due ${due.dueDate}") } }; if (state.dueServicesReady && available.isEmpty()) Text("No unclaimed current plans at this site.") }
        }
        if (mode == "ONE_TIME" || site != null) item { NewVisitTaskEditor(taskName, { taskName = it }, subjectType, { value -> subjectType = value; if (value == WorkSubjectType.SITE) { equipmentId = null; equipmentDescription = "" } }, equipmentId, { equipmentId = it; equipmentDescription = "" }, equipmentDescription, { equipmentDescription = it }, state.templates, templateId, { templateId = it }, equipment, allowKnownEquipment, taskValid, editingIndex != null) { val draft = NewVisitTaskDraft(taskName.trim(), subjectType, equipmentId, equipmentDescription.trim(), templateId); tasks = if (editingIndex == null) tasks + draft else tasks.mapIndexed { index, old -> if (index == editingIndex) draft else old }; resetTask() } }
        if (tasks.isNotEmpty()) item { DailyHeading("Tasks"); tasks.forEachIndexed { index, task -> NewVisitTaskRow(index, task, state.templates, equipment, { taskName = task.taskName; subjectType = task.subjectType; equipmentId = task.equipmentId; equipmentDescription = task.equipmentDescription; templateId = task.templateId; editingIndex = index }, { tasks = tasks.filterIndexed { itemIndex, _ -> itemIndex != index } }) } }
        item { DailyField(date, { date = it }, "Appointment / service date · YYYY-MM-DD"); Text("Date-only booking in the ${state.businessZoneId} business zone."); val parsed = runCatching { LocalDate.parse(date) }.getOrNull(); val primary = when { parsed == null || parsed.isAfter(state.businessDate) -> "BOOKED"; parsed == state.businessDate -> "WORKING"; else -> "HISTORICAL" }; @Composable fun action(kind: String, label: String) { val enabled = visitValid && (kind != "HISTORICAL" || parsed != null && !parsed.isAfter(state.businessDate)); val click = { save(kind) }; if (primary == kind) ServiceLoopPrimaryButton(label, click, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("primary-visit-action-$kind")) else ServiceLoopSecondaryButton(label, click, enabled = enabled, modifier = Modifier.fillMaxWidth()) }; ServiceLoopActionStack { action("BOOKED", "Book visit"); action("WORKING", "Start now"); action("HISTORICAL", "Record past visit") } }
    }
}

@Composable
private fun AdHocWorkEditor(
    equipment: List<EquipmentSummary>,
    templates: List<TemplateSummary>,
    allowKnownEquipment: Boolean,
    busy: Boolean,
    onAdd: (AdHocWorkInput) -> Unit,
) {
    var taskName by rememberSaveable { mutableStateOf("") }
    var subjectType by rememberSaveable { mutableStateOf(WorkSubjectType.SITE) }
    var equipmentId by rememberSaveable { mutableStateOf<String?>(null) }
    var equipmentDescription by rememberSaveable { mutableStateOf("") }
    var templateId by rememberSaveable { mutableStateOf<String?>(null) }
    val valid = taskName.isNotBlank() && taskName.length <= 200 && when {
        subjectType == WorkSubjectType.SITE -> equipmentId == null && equipmentDescription.isBlank()
        !allowKnownEquipment -> equipmentId == null && equipmentDescription.length <= 500
        else -> equipmentDescription.isBlank() && (equipmentId == null || equipment.any { it.id == equipmentId })
    }
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Add task", fontWeight = FontWeight.Bold)
            DailyField(taskName, { taskName = it }, "Task name · Required")
            Text("Subject", fontWeight = FontWeight.Medium)
            ServiceLoopChoiceGroup(listOf(WorkSubjectType.SITE to "Site", WorkSubjectType.EQUIPMENT to "Equipment"), subjectType, { value -> subjectType = value; if (value == WorkSubjectType.SITE) { equipmentId = null; equipmentDescription = "" } }, testTagPrefix = "visit-task-subject")
            if (subjectType == WorkSubjectType.EQUIPMENT) {
                if (!allowKnownEquipment) Text("No registered equipment is available in a new one-time branch.", style = MaterialTheme.typography.bodySmall)
                else ServiceLoopChoiceGroup(listOf<Pair<String?, String>>(null to "No specific equipment yet") + equipment.map { it.id to "${it.name} · ${it.reference}" }, equipmentId, { equipmentId = it; equipmentDescription = "" }, testTagPrefix = "visit-task-equipment")
                if (!allowKnownEquipment || equipmentId == null) DailyField(equipmentDescription, { equipmentDescription = it }, "Equipment description · Optional")
            }
            Text("Inspection checklist", fontWeight = FontWeight.Medium)
            ServiceLoopChoiceGroup(listOf<Pair<String?, String>>(null to "None") + templates.filter { it.state == "ACTIVE" }.map { it.id to "${it.name} · r${it.revisionNumber}" }, templateId, { templateId = it }, testTagPrefix = "visit-task-template")
            ServiceLoopPrimaryButton("Add task", { val input = AdHocWorkInput(taskName.trim(), subjectType, equipmentId, equipmentDescription.trim(), templateId); onAdd(input); taskName = ""; subjectType = WorkSubjectType.SITE; equipmentId = null; equipmentDescription = ""; templateId = null }, Modifier.fillMaxWidth().testTag("add-visit-task"), enabled = valid && !busy)
            if (!allowKnownEquipment) Text("This task will remain local to the one-time visit.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun VisitDateLandmark(state: String, persistedDate: String) {
    val colors = LocalServiceLoopTokens.current
    Column(Modifier.fillMaxWidth().testTag("visit-date-landmark"), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
        Text(if (state == "BOOKED") "APPOINTMENT" else "SERVICE DATE", style = ServiceLoopUiTokens.Type.dayHeading, color = colors.action, modifier = Modifier.testTag("visit-date-landmark-label"))
        Text(formatServiceLoopDate(persistedDate), style = ServiceLoopUiTokens.Type.screenTitle, modifier = Modifier.testTag("visit-date-landmark-value"))
    }
}

@Composable
internal fun VisitDetailScreen(detail: VisitDetail?, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    if (detail == null) return DailyEmpty(padding, "Reading visit")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var newDate by rememberSaveable(detail.id) { mutableStateOf(detail.serviceDate) }; var reason by rememberSaveable(detail.id) { mutableStateOf("") }; var cancelReason by rememberSaveable(detail.id) { mutableStateOf("") }; var oneOffName by rememberSaveable(detail.id){mutableStateOf("")}; var oneOffEquipment by rememberSaveable(detail.id){mutableStateOf<String?>(null)}
    var reviewError by rememberSaveable(detail.id) { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.padding(padding).testTag("visit-detail-list"), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)) {
         item { Column(Modifier.testTag("visit-identity")) { Text("${detail.reference} · ${detail.state.lowercase().replace('_',' ').replaceFirstChar(Char::uppercase)}", style = MaterialTheme.typography.headlineSmall); Text(detail.customerName); Text(detail.siteName); Text(detail.siteAddress); if (detail.customerType == CustomerType.ONE_TIME) Text("One-time customer", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("one-time-customer-label")) } }
        item { ServiceLoopResponsivePair(first = { ServiceLoopSecondaryButton("Customer", { nav.navigate("customer/${detail.customerId}") }, Modifier.fillMaxWidth().testTag("visit-customer-link")) }, second = { ServiceLoopSecondaryButton("Site", { nav.navigate("site/${detail.siteId}") }, Modifier.fillMaxWidth().testTag("visit-site-link")) }, modifier = Modifier.testTag("visit-relationship-actions")) }
        item { VisitDateLandmark(detail.state, detail.serviceDate) }
        item { DispatchVisitPanel(detail) }
        item { val calendar=state.visitCalendarState;Card(Modifier.fillMaxWidth().testTag("visit-calendar")){Column(Modifier.padding(12.dp)){Text("Calendar",fontWeight=FontWeight.Bold);Text(calendar?.label?:"Checking Calendar status");when(calendar?.action){"Add to Calendar","Recreate event"->OutlinedButton({viewModel.addVisitToCalendar(detail.id)},Modifier.fillMaxWidth().testTag("visit-calendar-add")){Text(calendar.action)};"Remove from Calendar"->OutlinedButton({viewModel.removeVisitFromCalendar(detail.id)},Modifier.fillMaxWidth().testTag("visit-calendar-remove")){Text(calendar.action)}};calendar?.eventId?.let{id->TextButton({viewModel.calendarEventIntent(id)?.let(context::startActivity)}){Text("Open Calendar event")}}}} }
        if (state.serviceProgress?.visitId == detail.id) item {
            VisitServiceProgressOverview(state.serviceProgress, onSelect = { item ->
                viewModel.focusService(item.workItemId)
                nav.navigate("inspection/${item.workItemId}")
            })
        } else {
            items(detail.lines) { line -> ServiceLoopWorkItemRow(serviceLoopSubjectLabel(line.subjectType, line.equipmentName, line.equipmentReference, line.equipmentDescription),line.serviceName,"Due ${line.dueDate ?: "one-off"} · ${line.outcome?.lowercase()?.replace('_',' ') ?: detail.state.lowercase().replaceFirstChar(Char::uppercase)}",detail.state=="WORKING",Modifier.testTag("visit-line-${line.workItemId}")){nav.navigate("inspection/${line.workItemId}")} }
        }
        if(detail.state in setOf("BOOKED","WORKING")&&state.site!=null) item { AdHocWorkEditor(state.site.equipment, state.templates, allowKnownEquipment = true, state.operationInProgress) { input -> viewModel.addAdHocWork(detail.id, input) { viewModel.loadVisit(detail.id) } } }
        if (detail.state == "BOOKED") item { var rescheduleSaved by rememberSaveable(detail.id) { mutableStateOf(false) }; Text("Start reloads current customer, site, equipment, plan, business, and reusable-template details. Changed details replace booking-time display details in the Working visit."); Button({ viewModel.startVisit(detail.id) { id -> val target = viewModel.state.value.serviceProgress?.preferredResumeItem()?.workItemId; if (target != null) nav.navigate("inspection/$target") else nav.navigate("visit/$id") } }, enabled = detail.lines.isNotEmpty() && !state.operationInProgress, modifier = Modifier.fillMaxWidth().testTag("start-visit")) { Text("Start visit") }; if(detail.lines.isEmpty()) Text("Add at least one service line before starting."); Text("Appointment reminder",fontWeight=FontWeight.Bold); ServiceLoopPresetChoiceGroup(listOf(null to "Default",0 to "Off") + ReminderPreferences.APPOINTMENT_LEAD_PRESETS,detail.appointmentReminderLeadMinutes,{viewModel.setAppointmentReminderLead(detail.id,it)},testTagPrefix="visit-reminder"); if (detail.appointmentReminderLeadMinutes == ReminderPreferences.LEGACY_APPOINTMENT_LEAD_MINUTES) Text("Current saved lead: 2h. Choose a new preset to replace it.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("legacy-visit-appointment-lead")); Column { DailyField(newDate, { newDate = it; rescheduleSaved=false }, "New appointment date"); if(rescheduleSaved) Row(Modifier.align(Alignment.End).testTag("reschedule-saved"),verticalAlignment=Alignment.CenterVertically){ServiceLoopIcon(ServiceLoopIcons.LocalSaved,null,Modifier.size(ServiceLoopUiTokens.Size.iconSmall),MaterialTheme.colorScheme.primary);Spacer(Modifier.width(ServiceLoopUiTokens.Space.xs));Text("Saved",color=MaterialTheme.colorScheme.primary)} }; LongTextEditor(reason, { reason = it; rescheduleSaved=false }, "Reschedule reason", false); OutlinedButton({ viewModel.rescheduleVisit(detail.id, newDate, null, reason) { rescheduleSaved=true } }, enabled = reason.isNotBlank() && runCatching { LocalDate.parse(newDate) }.isSuccess, modifier = Modifier.fillMaxWidth()) { Text("Reschedule booking") }; LongTextEditor(cancelReason, { cancelReason = it }, "Cancellation reason", false); OutlinedButton({ viewModel.cancelVisit(detail.id, cancelReason) { viewModel.loadVisit(it) } }, enabled = cancelReason.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Cancel booking") }; Text("Rescheduling or cancelling does not fulfill or alter the due obligation.") }
        if (detail.state == "CANCELED" && detail.cancellationOrigin !in setOf("COORDINATOR","ASSIGNMENT_REMOVAL")) item { var restoreDate by rememberSaveable(detail.id) { mutableStateOf(if (runCatching { LocalDate.parse(detail.serviceDate) }.getOrNull()?.isBefore(state.businessDate) == true) state.businessDate.toString() else detail.serviceDate) }; Text("This cancellation left the obligation due."); DailyField(restoreDate, { restoreDate=it }, "Restore appointment date · YYYY-MM-DD"); Spacer(Modifier.height(ServiceLoopUiTokens.Space.lg)); Button({ viewModel.restoreVisit(detail.id, restoreDate) { viewModel.loadVisit(it) } }, enabled=!state.operationInProgress && runCatching { !LocalDate.parse(restoreDate).isBefore(state.businessDate) }.getOrDefault(false), modifier=Modifier.fillMaxWidth().testTag("restore-booking")){Text("Restore booking")} }
        if (detail.state == "WORKING") item {
            val session = state.activeServiceWorkItemId?.takeIf { state.activeServiceVisitId == detail.id && state.serviceProgress?.items?.any { item -> item.workItemId == it } == true }
            val target = session ?: state.serviceProgress?.preferredResumeItem()?.workItemId ?: detail.lines.firstOrNull()?.workItemId
            ServiceLoopActionStack {
            ServiceLoopPrimaryButton("Resume service", { if (target != null) nav.navigate("inspection/$target") }, Modifier.fillMaxWidth().testTag("resume-service"), enabled = target != null)
            ServiceLoopSecondaryButton("Review visit", {
                scope.launch {
                    val result = runCatching { viewModel.flushVisitDraft(detail.id) }.getOrNull()
                    if (result?.success == true) {
                        reviewError = null
                        nav.navigate("review/${detail.id}")
                    } else reviewError = "Resolve unsaved service edits before continuing."
                }
            }, Modifier.fillMaxWidth().testTag("visit-review"), enabled = state.serviceProgress?.let { progress -> progress.actionableItems.isNotEmpty() && progress.actionableItems.all { it.status == com.v16studio.serviceloop.domain.ServiceEntryStatus.READY } } == true)
            }
            reviewError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("visit-review-error")) }
        }
        if (detail.state == "CANCELED") item { Text("Cancellation reason: ${detail.cancellationReason}"); if(detail.cancellationOrigin!=null) Text("Cancellation source: ${detail.cancellationOrigin.lowercase().replace('_',' ')}"); Text("The service obligation remains due and may be booked again.") }
    }
}
