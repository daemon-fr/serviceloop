package com.v16studio.serviceloop.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.domain.AdHocWorkInput
import com.v16studio.serviceloop.domain.DueService
import com.v16studio.serviceloop.domain.EquipmentSummary
import com.v16studio.serviceloop.domain.TemplateSummary
import com.v16studio.serviceloop.domain.VisitSiteOption
import com.v16studio.serviceloop.domain.WorkSubjectType
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChoiceGroup
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChoicePair
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopContentTabs
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopEntityRecord
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFieldAction
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFilterSelector
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.serviceLoopFocusRing
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopActionStack
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopOutlinedButtonAdapter as OutlinedButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSecondaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNotice
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNoticeKind
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextButtonAdapter as TextButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons
import com.v16studio.serviceloop.domain.appointmentEpochMillis
import org.json.JSONObject
import java.util.UUID
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

internal enum class VisitSetupMode { EXISTING, NEW }

internal const val CREATED_INSPECTION_TEMPLATE_ID_KEY = "created-inspection-template-id"
internal const val PAST_BOOKED_VISIT_DATE_MESSAGE =
    "A booked visit cannot be scheduled in the past. Choose today or a future date, or record past work."

internal data class VisitSetupTaskDraft(
    val stableUiId: String = UUID.randomUUID().toString(),
    val taskName: String,
    val subjectType: WorkSubjectType,
    val equipmentId: String?,
    val equipmentDescription: String,
    val reusableTemplateId: String?,
) {
    fun toAdHocWorkInput() = AdHocWorkInput(taskName, subjectType, equipmentId, equipmentDescription, reusableTemplateId)
}

internal data class VisitSetupDraft(
    val mode: VisitSetupMode = VisitSetupMode.EXISTING,
    val siteId: String? = null,
    val selectedPlanIds: Set<String> = emptySet(),
    val serviceDate: String,
    val appointmentTime: String = "",
    val newCustomer: CustomerCreationDraft = CustomerCreationDraft(),
    val tasks: List<VisitSetupTaskDraft> = emptyList(),
)

private const val SETUP_SEPARATOR = "\u001f"

internal val VisitSetupDraftSaver = listSaver<VisitSetupDraft, String>(
    save = { draft ->
        listOf(
            draft.mode.name,
            draft.siteId.orEmpty(),
            draft.selectedPlanIds.sorted().joinToString(SETUP_SEPARATOR),
            draft.serviceDate,
            draft.appointmentTime,
            draft.newCustomer.name,
            draft.newCustomer.contactName,
            draft.newCustomer.phone,
            draft.newCustomer.email,
            draft.newCustomer.privateNote,
            draft.newCustomer.customerType.name,
            draft.newCustomer.siteName,
            draft.newCustomer.siteAddress,
        ) + draft.tasks.map { task ->
            JSONObject().apply {
                put("stableUiId", task.stableUiId)
                put("taskName", task.taskName)
                put("subjectType", task.subjectType.name)
                put("equipmentId", task.equipmentId)
                put("equipmentDescription", task.equipmentDescription)
                put("reusableTemplateId", task.reusableTemplateId)
            }.toString()
        }
    },
    restore = { values ->
        if (values.size < 13) return@listSaver null
        runCatching {
            VisitSetupDraft(
                mode = VisitSetupMode.valueOf(values[0]),
                siteId = values[1].takeIf(String::isNotBlank),
                selectedPlanIds = values[2].split(SETUP_SEPARATOR).filter(String::isNotBlank).toSet(),
                serviceDate = values[3],
                appointmentTime = values[4],
                newCustomer = CustomerCreationDraft(
                    name = values[5], contactName = values[6], phone = values[7], email = values[8],
                    privateNote = values[9], customerType = com.v16studio.serviceloop.domain.CustomerType.valueOf(values[10]),
                    siteName = values[11], siteAddress = values[12],
                ),
                tasks = values.drop(13).mapNotNull { encoded ->
                    runCatching {
                        val value = JSONObject(encoded)
                        VisitSetupTaskDraft(
                            stableUiId = value.getString("stableUiId"),
                            taskName = value.getString("taskName"),
                            subjectType = WorkSubjectType.valueOf(value.getString("subjectType")),
                            equipmentId = value.optString("equipmentId").takeIf(String::isNotBlank),
                            equipmentDescription = value.optString("equipmentDescription"),
                            reusableTemplateId = value.optString("reusableTemplateId").takeIf(String::isNotBlank),
                        )
                    }.getOrNull()
                },
            )
        }.getOrNull()
    },
)

internal fun visitSetupIsDirty(initial: VisitSetupDraft, current: VisitSetupDraft): Boolean =
    initial.selectedPlanIds != current.selectedPlanIds ||
        initial.serviceDate != current.serviceDate ||
        initial.appointmentTime != current.appointmentTime ||
        initial.newCustomer != current.newCustomer ||
        initial.tasks != current.tasks

internal fun VisitSetupDraft.hasDiscardableBranchContent(): Boolean =
    selectedPlanIds.isNotEmpty() || tasks.isNotEmpty() || newCustomer.hasMeaningfulInput()

private fun CustomerCreationDraft.toInputOrNull() = if (isValidForCreate()) toInput() else null

@Composable
private fun VisitSetupTemplateReturnBridge(
    nav: NavHostController,
    templates: List<TemplateSummary>,
    onRefreshTemplates: () -> Unit,
    onTemplateSelected: (String) -> Unit,
) {
    val entry = nav.currentBackStackEntry
    val returnedTemplateId = entry?.savedStateHandle
        ?.getStateFlow<String?>(CREATED_INSPECTION_TEMPLATE_ID_KEY, null)
        ?.collectAsState()
        ?.value
    var pendingTemplateId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(returnedTemplateId) {
        returnedTemplateId?.let { id ->
            pendingTemplateId = id
            entry?.savedStateHandle?.remove<String>(CREATED_INSPECTION_TEMPLATE_ID_KEY)
            onRefreshTemplates()
        }
    }
    LaunchedEffect(pendingTemplateId, templates) {
        pendingTemplateId?.takeIf { id -> templates.any { it.id == id && it.state == "ACTIVE" } }?.let { id ->
            onTemplateSelected(id)
            pendingTemplateId = null
        }
    }
}

@Composable
internal fun VisitSetupForm(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp, 8.dp, 0.dp, 32.dp),
    draft: VisitSetupDraft,
    sites: List<VisitSiteOption>,
    plannedWorkState: DueServicesProjection,
    templates: List<TemplateSummary>,
    businessDate: java.time.LocalDate,
    editable: Boolean = true,
    busy: Boolean = false,
    errorMessage: String? = null,
    onDraftChange: (VisitSetupDraft) -> Unit,
    onRetryDueServices: () -> Unit = {},
    allowTemplateCreation: Boolean = true,
    onCreateTemplate: () -> Unit = {},
    onRefreshTemplates: () -> Unit = {},
    templateReturnNav: NavHostController? = null,
    dateErrorMessage: String? = null,
    preludeItems: (LazyListScope.() -> Unit)? = null,
    extensionItems: (LazyListScope.() -> Unit)? = null,
    actionItems: (LazyListScope.() -> Unit)? = null,
) {
    var siteQuery by remember { mutableStateOf("") }
    var taskName by remember { mutableStateOf("") }
    var subjectType by remember { mutableStateOf(WorkSubjectType.SITE) }
    var equipmentId by remember { mutableStateOf<String?>(null) }
    var equipmentDescription by remember { mutableStateOf("") }
    var reusableTemplateId by remember { mutableStateOf<String?>(null) }
    var editingTaskId by remember { mutableStateOf<String?>(null) }
    var pendingSiteId by remember { mutableStateOf<String?>(null) }
    var pendingSiteChange by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf<VisitSetupMode?>(null) }

    val dueServices = (plannedWorkState as? DueServicesProjection.Available)?.rows.orEmpty()
    val plannedWorkError = when (val state = plannedWorkState) {
        is DueServicesProjection.Available -> state.updateError
        is DueServicesProjection.Unavailable -> state.message
        DueServicesProjection.Unresolved -> null
    }
    val plannedWorkLoading = plannedWorkState is DueServicesProjection.Unresolved

    val selectedSite = sites.firstOrNull { it.id == draft.siteId }
    val matchingSites = sites.filter { option ->
        siteQuery.isNotBlank() || option.customerType == com.v16studio.serviceloop.domain.CustomerType.STANDARD
    }.filter { option ->
        siteQuery.isBlank() || option.customerName.contains(siteQuery, true) || option.name.contains(siteQuery, true) || option.reference.contains(siteQuery, true)
    }
    val availablePlans = dueServices.filter { it.siteId == draft.siteId && it.claimedVisitId == null }
    val equipment = selectedSite?.equipment.orEmpty()
    val allowKnownEquipment = draft.mode == VisitSetupMode.EXISTING && selectedSite != null
    val taskValid = taskName.trim().isNotBlank() && taskName.trim().length <= 200 && when {
        subjectType == WorkSubjectType.SITE -> equipmentId == null && equipmentDescription.isBlank()
        !allowKnownEquipment -> equipmentId == null && equipmentDescription.length <= 500
        else -> equipmentDescription.isBlank() && (equipmentId == null || equipment.any { it.id == equipmentId })
    } && (reusableTemplateId != null || draft.tasks.firstOrNull { it.stableUiId == editingTaskId }?.reusableTemplateId == null && editingTaskId != null)

    templateReturnNav?.let { nav ->
        VisitSetupTemplateReturnBridge(nav, templates, onRefreshTemplates) { id -> reusableTemplateId = id }
    }

    fun update(next: VisitSetupDraft) { if (editable) onDraftChange(next) }
    fun resetTask() {
        taskName = ""; subjectType = WorkSubjectType.SITE; equipmentId = null
        equipmentDescription = ""; reusableTemplateId = null; editingTaskId = null
    }
    fun applyMode(next: VisitSetupMode) {
        update(
            draft.copy(
                mode = next,
                siteId = if (next == VisitSetupMode.NEW) null else draft.siteId,
                selectedPlanIds = if (next == VisitSetupMode.NEW) emptySet() else draft.selectedPlanIds,
                newCustomer = if (next == VisitSetupMode.EXISTING) CustomerCreationDraft() else draft.newCustomer,
                tasks = emptyList(),
            ),
        )
        resetTask()
        siteQuery = ""
    }
    fun requestMode(next: VisitSetupMode) {
        if (!editable || next == draft.mode) return
        if (draft.hasDiscardableBranchContent()) pendingMode = next else applyMode(next)
    }
    fun clearSite() {
        update(draft.copy(siteId = null, selectedPlanIds = emptySet(), tasks = emptyList()))
        siteQuery = ""; resetTask()
    }
    fun selectSite(next: String) {
        if (draft.tasks.isEmpty() && draft.selectedPlanIds.isEmpty()) update(draft.copy(siteId = next))
        else { pendingSiteId = next; pendingSiteChange = true }
    }
    fun requestSiteChange() {
        if (draft.tasks.isEmpty() && draft.selectedPlanIds.isEmpty()) clearSite()
        else { pendingSiteId = null; pendingSiteChange = true }
    }
    fun saveTask() {
        val task = VisitSetupTaskDraft(
            stableUiId = editingTaskId ?: UUID.randomUUID().toString(),
            taskName = taskName.trim(), subjectType = subjectType,
            equipmentId = equipmentId, equipmentDescription = equipmentDescription.trim(), reusableTemplateId = reusableTemplateId,
        )
        update(draft.copy(tasks = if (editingTaskId == null) draft.tasks + task else draft.tasks.map { if (it.stableUiId == editingTaskId) task else it }))
        resetTask()
    }

    pendingSiteChange.takeIf { it }?.let {
        AlertDialog(
            onDismissRequest = { pendingSiteChange = false; pendingSiteId = null },
            title = { Text("Change customer or site?") },
            text = { Text("Planned and ad-hoc work added for this site will be cleared.") },
            confirmButton = { TextButton({ pendingSiteId?.let { update(draft.copy(siteId = it, selectedPlanIds = emptySet(), tasks = emptyList())) } ?: clearSite(); pendingSiteChange = false; pendingSiteId = null; resetTask() }) { Text("Change") } },
            dismissButton = { TextButton({ pendingSiteChange = false; pendingSiteId = null }) { Text("Keep current") } },
        )
    }
    pendingMode?.let { requested ->
        AlertDialog(
            onDismissRequest = { pendingMode = null },
            title = { Text("Switch visit setup?") },
            text = { Text("Switching will clear the current branch work.") },
            confirmButton = { TextButton({ applyMode(requested); pendingMode = null }) { Text("Switch") } },
            dismissButton = { TextButton({ pendingMode = null }) { Text("Cancel") } },
        )
    }

    val targetReady = when (draft.mode) {
        VisitSetupMode.EXISTING -> selectedSite != null
        VisitSetupMode.NEW -> draft.newCustomer.isValidForCreate()
    }

    LazyColumn(modifier.fillMaxWidth(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.md)) {
        preludeItems?.invoke(this)
        item {
            val colors = LocalServiceLoopTokens.current
            Column(Modifier.fillMaxWidth().background(colors.surface)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = ServiceLoopUiTokens.Layout.pageInsetCompact)) {
                    VisitSetupSectionHeading("Choose a customer", "choose-visit-customer")
                    Spacer(Modifier.height(ServiceLoopUiTokens.Space.sm))
                }
                Box(Modifier.fillMaxWidth().testTag("visit-mode-tabs")) {
                    ServiceLoopContentTabs(
                        listOf(VisitSetupMode.EXISTING to "Existing", VisitSetupMode.NEW to "New"),
                        draft.mode,
                        ::requestMode,
                        testTagPrefix = "visit-mode",
                        enabled = editable,
                    )
                }
            }
        }
        if (draft.mode == VisitSetupMode.NEW) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = ServiceLoopUiTokens.Layout.pageInsetCompact)) {
                    CustomerCreationForm(draft.newCustomer, { update(draft.copy(newCustomer = it)) }, editable = editable)
                    if (targetReady) {
                        Spacer(Modifier.height(ServiceLoopUiTokens.Space.sm))
                        VisitSetupSectionHeading("Set up visit", "visit-setup-heading")
                    }
                }
            }
        } else {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = ServiceLoopUiTokens.Layout.pageInsetCompact)) {
                    Text("Customer / site", fontWeight = FontWeight.Bold)
                    if (selectedSite != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Layout.fieldActionGap)) {
                            Text("${selectedSite.customerName} · ${selectedSite.name}", Modifier.weight(1f))
                            ServiceLoopFieldAction("Change customer or site", ::requestSiteChange, Modifier.testTag("visit-change-customer-site"), enabled = editable, content = { ServiceLoopIcon(ServiceLoopIcons.Search, null, Modifier.size(ServiceLoopUiTokens.Size.icon), LocalContentColor.current) })
                        }
                    } else {
                        DailyField(siteQuery, { siteQuery = it }, "Find customer or site", enabled = editable)
                    }
                }
            }
            if (selectedSite == null) {
                items(matchingSites, key = { "visit-site-${it.id}" }) { option ->
                    VisitSiteSelectionRow(option, editable, ::selectSite)
                }
                if (matchingSites.isEmpty()) item { Text(if (sites.isEmpty()) "Add a customer site before creating a visit." else "No matching customer sites.", modifier = Modifier.padding(horizontal = ServiceLoopUiTokens.Layout.pageInsetCompact)) }
            } else {
                if (selectedSite.customerType == com.v16studio.serviceloop.domain.CustomerType.ONE_TIME) item { Text("One-time customers use ad-hoc work.", modifier = Modifier.padding(horizontal = ServiceLoopUiTokens.Layout.pageInsetCompact)) }
                if (selectedSite.customerType == com.v16studio.serviceloop.domain.CustomerType.STANDARD) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = ServiceLoopUiTokens.Layout.pageInsetCompact)) {
                            Text("Planned work", fontWeight = FontWeight.Bold)
                            when {
                                plannedWorkLoading -> Text("Reading planned services", color = LocalServiceLoopTokens.current.textSecondary, modifier = Modifier.testTag("planned-work-loading"))
                                plannedWorkError != null -> ServiceLoopNotice(
                                    "Planned services are unavailable.",
                                    plannedWorkError,
                                    ServiceLoopNoticeKind.Error,
                                    action = { OutlinedButton(onRetryDueServices, enabled = editable, modifier = Modifier.fillMaxWidth().testTag("retry-due-services")) { Text("Retry") } },
                                )
                                else -> {
                                    availablePlans.forEach { due ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(due.planId in draft.selectedPlanIds, { checked -> if (editable) update(draft.copy(selectedPlanIds = if (checked) draft.selectedPlanIds + due.planId else draft.selectedPlanIds - due.planId)) }, enabled = editable, modifier = Modifier.testTag("visit-plan-${due.planId}"))
                                            Text("${due.equipmentName} · ${due.planName} · Due ${due.dueDate}")
                                        }
                                    }
                                    if (availablePlans.isEmpty()) Text("No unclaimed current plans at this site.")
                                }
                            }
                        }
                    }
                }
                if (targetReady) item { VisitSetupSectionHeading("Set up visit", "visit-setup-heading", Modifier.padding(horizontal = ServiceLoopUiTokens.Layout.pageInsetCompact)) }
            }
        }
        if (targetReady) {
            item {
                Column(Modifier.padding(horizontal = ServiceLoopUiTokens.Layout.pageInsetCompact), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.md)) {
                    VisitDateInput(draft.serviceDate, { update(draft.copy(serviceDate = it)) }, businessDate.plusDays(1), enabled = editable, errorMessage = dateErrorMessage)
                    VisitTimeInput(draft.appointmentTime, { update(draft.copy(appointmentTime = it)) }, enabled = editable)
                }
            }
            item {
                Box(Modifier.padding(horizontal = ServiceLoopUiTokens.Layout.pageInsetCompact)) {
                    VisitSetupTaskEditor(
                        taskName, { taskName = it }, subjectType, { value -> subjectType = value; if (value == WorkSubjectType.SITE) { equipmentId = null; equipmentDescription = "" } },
                        equipmentId, { equipmentId = it; equipmentDescription = "" }, equipmentDescription, { equipmentDescription = it },
                        templates, reusableTemplateId, { reusableTemplateId = it }, equipment, allowKnownEquipment,
                        taskValid && editable, editingTaskId != null, editable, allowTemplateCreation, onCreateTemplate, ::saveTask,
                    )
                }
            }
            if (draft.tasks.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = ServiceLoopUiTokens.Layout.pageInsetCompact)) {
                        DailyHeading("Tasks")
                        draft.tasks.forEachIndexed { index, task ->
                            VisitSetupTaskRow(index, task, templates, equipment, editable,
                                onEdit = { taskName = task.taskName; subjectType = task.subjectType; equipmentId = task.equipmentId; equipmentDescription = task.equipmentDescription; reusableTemplateId = task.reusableTemplateId; editingTaskId = task.stableUiId },
                                onRemove = { update(draft.copy(tasks = draft.tasks.filterNot { it.stableUiId == task.stableUiId })) },
                            )
                        }
                    }
                }
            }
            extensionItems?.invoke(this)
        }
        if (errorMessage != null) item { Text(errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("visit-setup-error")) }
        if (targetReady) actionItems?.invoke(this)
        if (busy) item { Text("Saving…", modifier = Modifier.testTag("visit-setup-saving")) }
    }
}

@Composable
private fun VisitSiteSelectionRow(option: VisitSiteOption, enabled: Boolean, onSelected: (String) -> Unit) {
    val colors = LocalServiceLoopTokens.current
    val label = "Use ${option.name} at ${option.customerName} for this visit"
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ServiceLoopUiTokens.Size.listRowMin)
            .selectable(selected = false, enabled = enabled, role = Role.RadioButton) { onSelected(option.id) }
            .semantics { contentDescription = label; if (!enabled) disabled() }
            .testTag("visit-site-${option.id}"),
        color = colors.surface,
        border = BorderStroke(ServiceLoopUiTokens.Stroke.outline, colors.outlineControl),
        shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field),
    ) {
        Row(Modifier.fillMaxWidth().padding(ServiceLoopUiTokens.Space.md), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = false, onClick = null, enabled = enabled)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
                Text("${option.reference} · ${option.name}", style = ServiceLoopUiTokens.Type.itemTitle, color = colors.textPrimary)
                Text(option.customerName, style = ServiceLoopUiTokens.Type.supporting, color = colors.textSecondary)
                if (option.customerType == com.v16studio.serviceloop.domain.CustomerType.ONE_TIME) {
                    Text("One-time", style = ServiceLoopUiTokens.Type.meta, color = colors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun VisitSetupTaskEditor(
    taskName: String, onTaskName: (String) -> Unit, subjectType: WorkSubjectType, onSubjectType: (WorkSubjectType) -> Unit,
    equipmentId: String?, onEquipmentId: (String?) -> Unit, equipmentDescription: String, onEquipmentDescription: (String) -> Unit,
    templates: List<TemplateSummary>, reusableTemplateId: String?, onReusableTemplateId: (String?) -> Unit,
    equipment: List<EquipmentSummary>, allowKnownEquipment: Boolean, valid: Boolean, editing: Boolean, editable: Boolean, allowTemplateCreation: Boolean,
    onCreateTemplate: () -> Unit, onSave: () -> Unit,
) {
    androidx.compose.material3.Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (editing) "Edit task" else "Add task", fontWeight = FontWeight.Bold)
            DailyField(taskName, onTaskName, "Task name · Required", enabled = editable)
            Text("Subject", fontWeight = FontWeight.Medium)
            ServiceLoopChoicePair(listOf(WorkSubjectType.SITE to "Site", WorkSubjectType.EQUIPMENT to "Equipment"), subjectType, onSubjectType, testTagPrefix = "task-subject", enabled = editable)
            if (subjectType == WorkSubjectType.EQUIPMENT) {
                if (!allowKnownEquipment) {
                    Text("Describe the equipment for this new site.", style = MaterialTheme.typography.bodySmall)
                    DailyField(equipmentDescription, onEquipmentDescription, "Equipment description · Optional", enabled = editable)
                } else {
                    VisitEquipmentRadioList(listOf<Pair<String?, String>>(null to "No specific equipment yet") + equipment.map { it.id to "${it.reference} · ${it.name}" }, equipmentId, onEquipmentId, editable)
                    if (equipmentId == null) DailyField(equipmentDescription, onEquipmentDescription, "Equipment description · Optional", enabled = editable)
                }
            }
            InspectionChecklistSelector(templates, reusableTemplateId, onReusableTemplateId, "task-template", onCreateTemplate, enabled = editable && allowTemplateCreation, required = !editing || reusableTemplateId != null)
            OutlinedButton(onSave, enabled = valid && editable, modifier = Modifier.fillMaxWidth().testTag(if (editing) "update-task" else "add-task")) { Text(if (editing) "Update task" else "Add task") }
        }
    }
}

@Composable
private fun VisitEquipmentRadioList(
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelected: (String?) -> Unit,
    enabled: Boolean,
) {
    val colors = LocalServiceLoopTokens.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
        options.forEach { (value, label) ->
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = ServiceLoopUiTokens.Size.touchMin)
                    .background(if (value == selected) colors.selection else colors.surface, RoundedCornerShape(ServiceLoopUiTokens.Radius.field))
                    .selectable(selected = value == selected, enabled = enabled, role = Role.RadioButton) { onSelected(value) }
                    .semantics { if (!enabled) disabled() }
                    .testTag("task-equipment-${value ?: "none"}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = value == selected, onClick = null, enabled = enabled)
                Text(label, color = if (enabled) colors.textPrimary else colors.disabledText, style = ServiceLoopUiTokens.Type.supporting)
            }
        }
    }
}

@Composable
private fun VisitSetupTaskRow(index: Int, task: VisitSetupTaskDraft, templates: List<TemplateSummary>, equipment: List<EquipmentSummary>, enabled: Boolean, onEdit: () -> Unit, onRemove: () -> Unit) {
    ServiceLoopEntityRecord(
        title = task.taskName,
        context = when (task.subjectType) { WorkSubjectType.SITE -> "Site"; WorkSubjectType.EQUIPMENT -> task.equipmentId?.let { id -> equipment.firstOrNull { it.id == id }?.name } ?: task.equipmentDescription.ifBlank { "Equipment not specified" } },
        metadata = task.reusableTemplateId?.let { id -> templates.firstOrNull { it.id == id }?.let { "Inspection checklist · ${it.name} (v${it.revisionNumber})" } } ?: "No checklist",
        modifier = Modifier.testTag("visit-task-$index"), onClick = onEdit, enabled = enabled,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onEdit, Modifier.weight(1f).testTag("visit-task-edit-$index"), enabled = enabled) { Text("Edit") }
        TextButton(onRemove, Modifier.weight(1f).testTag("visit-task-remove-$index"), enabled = enabled) { Text("Remove") }
    }
}

@Composable
internal fun InspectionChecklistSelector(
    templates: List<TemplateSummary>,
    selectedTemplateId: String?,
    onSelected: (String?) -> Unit,
    testTag: String,
    onCreateTemplate: () -> Unit = {},
    label: String = "Inspection checklist",
    enabled: Boolean = true,
    required: Boolean = true,
) {
    val active = templates.filter { it.state == "ACTIVE" }
    val selectedDisabled = templates.firstOrNull { it.id == selectedTemplateId && it.state == "DISABLED" }
    val options = buildList<Pair<String?, String>> {
        add(null to "None")
        addAll(active.map { it.id to "${it.name} (v${it.revisionNumber})" })
        selectedDisabled?.let { add(it.id to "${it.name} (v${it.revisionNumber}) · Disabled") }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Layout.fieldActionGap)) {
            if (active.isEmpty() && selectedDisabled == null) {
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            } else {
                ServiceLoopFilterSelector(label, selectedTemplateId, options, if (enabled) onSelected else { _ -> }, Modifier.weight(1f), "$testTag-selector", enabled = enabled)
            }
            ServiceLoopFieldAction(
                "Create inspection template",
                onCreateTemplate,
                Modifier.testTag("$testTag-create"),
                enabled = enabled,
                content = { ServiceLoopIcon(ServiceLoopIcons.PlusBold, null, Modifier.size(ServiceLoopUiTokens.Size.icon), LocalContentColor.current) },
            )
        }
        if (required && active.isEmpty() && selectedDisabled == null) {
            ServiceLoopNotice(
                "Inspection checklist required",
                "Create an inspection template before adding this task.",
                ServiceLoopNoticeKind.Warning,
                modifier = Modifier.testTag("$testTag-required"),
            )
        } else if (required && selectedTemplateId == null) {
            Text("Select an inspection checklist.", color = LocalServiceLoopTokens.current.errorInk, modifier = Modifier.testTag("$testTag-required-selection"), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
@Suppress("NonObservableLocale")
internal fun VisitDateInput(
    value: String,
    onValueChange: (String) -> Unit,
    defaultDate: LocalDate,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val parsedDate = runCatching { LocalDate.parse(value) }.getOrNull()
    val weekday = parsedDate?.dayOfWeek?.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
        Text("Date", style = ServiceLoopUiTokens.Type.label)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Layout.fieldActionGap)) {
            VisitAppointmentTextField(
                value = value,
                onValueChange = onValueChange,
                leadingText = weekday ?: "—",
                placeholder = null,
                testTag = "field-appointment-service-date-yyyy-mm-dd",
                leadingTestTag = "appointment-date-weekday",
                keyboardType = KeyboardType.Ascii,
                isError = errorMessage != null,
                modifier = Modifier.weight(1f),
                enabled = enabled,
            )
            ServiceLoopFieldAction(
                "Choose appointment date",
                { showPicker = true },
                Modifier.testTag("appointment-date-picker"),
                enabled = enabled,
                content = { ServiceLoopIcon(ServiceLoopIcons.Calendar, null, Modifier.size(ServiceLoopUiTokens.Size.icon), LocalContentColor.current) },
            )
        }
        errorMessage?.let { Text(it, color = LocalServiceLoopTokens.current.errorInk, style = ServiceLoopUiTokens.Type.meta, modifier = Modifier.testTag("appointment-date-error")) }
    }
    if (showPicker) {
        val initial = parsedDate ?: defaultDate
        val context = LocalContext.current
        val dialog = remember(context, initial) {
            DatePickerDialog(context, { _, year, month, day ->
                onValueChange(LocalDate.of(year, month + 1, day).toString())
                showPicker = false
            }, initial.year, initial.monthValue - 1, initial.dayOfMonth)
        }
        DisposableEffect(dialog) {
            dialog.setOnCancelListener { showPicker = false }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }
}

@Composable
internal fun VisitTimeInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val parsed = parseAppointmentTimeInput(value)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
        Text("Time · optional", style = ServiceLoopUiTokens.Type.label)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Layout.fieldActionGap)) {
            VisitAppointmentTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = "No time set",
                testTag = "field-appointment-time",
                keyboardType = KeyboardType.Ascii,
                isError = value.isNotBlank() && parsed == null,
                modifier = Modifier.weight(1f),
                enabled = enabled,
            )
            ServiceLoopFieldAction(
                "Choose appointment time",
                { showPicker = true },
                Modifier.testTag("appointment-time-picker"),
                enabled = enabled,
                content = { ServiceLoopIcon(ServiceLoopIcons.Time, null, Modifier.size(ServiceLoopUiTokens.Size.icon), LocalContentColor.current) },
            )
        }
        if (value.isNotBlank() && parsed == null) {
            Text("Enter time as HH:mm", style = ServiceLoopUiTokens.Type.meta, color = LocalServiceLoopTokens.current.errorInk, modifier = Modifier.testTag("appointment-time-error"))
        }
    }
    if (showPicker) {
        val initial = parsed ?: java.time.LocalTime.of(9, 0)
        val dialog = remember(context, initial) {
            TimePickerDialog(context, { _, hour, minute ->
                onValueChange("%02d:%02d".format(hour, minute))
                showPicker = false
            }, initial.hour, initial.minute, true)
        }
        DisposableEffect(dialog) {
            dialog.setOnCancelListener { showPicker = false }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }
}

private val appointmentTimeInputFormatter = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)

internal fun parseAppointmentTimeInput(value: String): LocalTime? = value.trim().takeIf(String::isNotEmpty)?.let { runCatching { LocalTime.parse(it, appointmentTimeInputFormatter) }.getOrNull() }

internal fun isValidAppointmentTimeInput(value: String): Boolean = value.isBlank() || parseAppointmentTimeInput(value) != null

@Composable
internal fun VisitSetupSectionHeading(title: String, testTag: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        color = LocalServiceLoopTokens.current.textPrimary,
        modifier = modifier.fillMaxWidth().testTag(testTag),
    )
}

@Composable
private fun VisitAppointmentTextField(
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    leadingText: String? = null,
    leadingTestTag: String? = null,
    placeholder: String? = null,
    keyboardType: KeyboardType,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalServiceLoopTokens.current
    val shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field)
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            isError = isError,
            enabled = enabled,
            textStyle = ServiceLoopUiTokens.Type.body.copy(color = colors.textPrimary, textAlign = TextAlign.Center),
            placeholder = placeholder?.let { hint ->
                {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(hint, style = ServiceLoopUiTokens.Type.body, color = colors.textMuted, textAlign = TextAlign.Center)
                    }
                }
            },
            shape = shape,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.focus,
                unfocusedBorderColor = if (isError) colors.errorInk else colors.outlineControl,
                errorBorderColor = colors.errorInk,
                cursorColor = if (isError) colors.errorInk else colors.action,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = ServiceLoopUiTokens.Size.fieldMin)
                .serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field).testTag(testTag),
        )
        leadingText?.let {
            Text(
                it,
                style = ServiceLoopUiTokens.Type.label,
                color = if (it == "—") colors.textMuted else colors.textSecondary,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = ServiceLoopUiTokens.Space.md)
                    .widthIn(min = 52.dp)
                    .then(if (leadingTestTag == null) Modifier else Modifier.testTag(leadingTestTag)),
            )
        }
    }
}


@Composable
internal fun NewVisitScreen(
    sites: List<VisitSiteOption>,
    dueServices: List<DueService>,
    padding: PaddingValues,
    state: UiState,
    viewModel: ServiceLoopViewModel,
    nav: NavHostController,
    initialPlanIds: List<String> = emptyList(),
) {
    val initialSite = dueServices.firstOrNull { it.planId in initialPlanIds }?.siteId
    val initialDate = state.businessDate.plusDays(1).toString()
    var draft by rememberSaveable(stateSaver = VisitSetupDraftSaver) {
        mutableStateOf(VisitSetupDraft(siteId = initialSite, selectedPlanIds = initialPlanIds.toSet(), serviceDate = initialDate))
    }
    val baseline = remember(initialSite, initialPlanIds, initialDate) {
        VisitSetupDraft(siteId = initialSite, selectedPlanIds = initialPlanIds.toSet(), serviceDate = initialDate)
    }
    val setupRoute = nav.currentBackStackEntry?.destination?.route ?: "visit/new"
    val validDate = runCatching { java.time.LocalDate.parse(draft.serviceDate) }.isSuccess
    val validTime = draft.appointmentTime.isBlank() || parseAppointmentTimeInput(draft.appointmentTime) != null
    val selectedSite = sites.firstOrNull { it.id == draft.siteId }
    val targetReady = if (draft.mode == VisitSetupMode.NEW) draft.newCustomer.isValidForCreate() else selectedSite != null
    val valid = targetReady && validDate && validTime && !state.operationInProgress && if (draft.mode == VisitSetupMode.NEW) {
        draft.newCustomer.isValidForCreate() && draft.tasks.isNotEmpty()
    } else {
        selectedSite != null && (draft.selectedPlanIds.isNotEmpty() || draft.tasks.isNotEmpty())
    }
    val parsedDate = runCatching { java.time.LocalDate.parse(draft.serviceDate) }.getOrNull()
    val pastBookedDate = parsedDate?.isBefore(state.businessDate) == true
    UnsavedChangesGuard(visitSetupIsDirty(baseline, draft), nav)

    fun save(targetState: String) {
        val scheduledAt = if (targetState == "BOOKED") runCatching {
            appointmentEpochMillis(draft.serviceDate, draft.appointmentTime, java.time.ZoneId.of(state.businessZoneId))
        }.getOrNull() else null
        val serviceDate = if (targetState == "WORKING") state.businessDate.toString() else draft.serviceDate
        val adHoc = draft.tasks.map { it.toAdHocWorkInput() }
        val success: (String) -> Unit = { id ->
            if (targetState == "WORKING") {
                viewModel.resolveWorkingVisitResume(id) {
                    nav.navigate("visit/$id") { popUpTo(setupRoute) { inclusive = true } }
                }
            } else nav.navigate("visit/$id") { popUpTo(setupRoute) { inclusive = true } }
        }
        if (draft.mode == VisitSetupMode.NEW) {
            viewModel.createNewCustomerVisit(draft.newCustomer.toInput(), adHoc, targetState, serviceDate, scheduledAt, success)
        } else {
            viewModel.createVisitForSite(requireNotNull(draft.siteId), draft.selectedPlanIds.toList(), adHoc, targetState, serviceDate, scheduledAt, success)
        }
    }

    VisitSetupForm(
        modifier = Modifier.padding(padding).testTag("new-visit-form"),
        draft = draft,
        sites = sites,
        plannedWorkState = state.dueServicesProjection,
        templates = state.templates,
        businessDate = state.businessDate,
        busy = state.operationInProgress,
        errorMessage = state.error,
        onDraftChange = { draft = it },
        onRetryDueServices = viewModel::retryDueServices,
        onRefreshTemplates = viewModel::loadTemplates,
        templateReturnNav = nav,
        onCreateTemplate = { nav.navigate("template/new?returnTo=visit-setup") },
        dateErrorMessage = PAST_BOOKED_VISIT_DATE_MESSAGE.takeIf { pastBookedDate },
        actionItems = {
            item {
                val parsed = runCatching { java.time.LocalDate.parse(draft.serviceDate) }.getOrNull()
                val primary = when { parsed == null || parsed.isAfter(state.businessDate) -> "BOOKED"; parsed == state.businessDate -> "WORKING"; else -> "HISTORICAL" }
                @Composable fun action(kind: String, label: String) {
                    val enabled = valid && (kind != "HISTORICAL" || parsed != null && !parsed.isAfter(state.businessDate)) && (kind != "BOOKED" || !pastBookedDate)
                    val click = { save(kind) }
                    if (primary == kind) ServiceLoopPrimaryButton(label, click, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("primary-visit-action-$kind"))
                    else ServiceLoopSecondaryButton(label, click, enabled = enabled, modifier = Modifier.fillMaxWidth())
                }
                ServiceLoopActionStack { action("BOOKED", "Book visit"); action("WORKING", "Start now"); action("HISTORICAL", "Record past visit") }
            }
        },
    )
}
