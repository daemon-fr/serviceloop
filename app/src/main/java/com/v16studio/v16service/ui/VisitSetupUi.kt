package com.v16studio.v16service.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import com.v16studio.v16service.ui.designsystem.V16ServiceCheckbox as Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
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
import com.v16studio.v16service.domain.AdHocWorkInput
import com.v16studio.v16service.domain.DueService
import com.v16studio.v16service.domain.EquipmentSummary
import com.v16studio.v16service.domain.TemplateSummary
import com.v16studio.v16service.domain.VisitSiteOption
import com.v16studio.v16service.domain.WorkSubjectType
import com.v16studio.v16service.ui.designsystem.V16ServiceChoiceGroup
import com.v16studio.v16service.ui.designsystem.V16ServiceChoicePair
import com.v16studio.v16service.ui.designsystem.V16ServiceContentTabs
import com.v16studio.v16service.ui.designsystem.V16ServiceEntityRecord
import com.v16studio.v16service.ui.designsystem.V16ServiceFieldAction
import com.v16studio.v16service.ui.designsystem.V16ServiceFilterSelector
import com.v16studio.v16service.ui.designsystem.LocalV16ServiceTokens
import com.v16studio.v16service.ui.designsystem.v16ServiceFocusRing
import com.v16studio.v16service.ui.designsystem.V16ServiceActionStack
import com.v16studio.v16service.ui.designsystem.V16ServiceOutlinedButtonAdapter as OutlinedButton
import com.v16studio.v16service.ui.designsystem.V16ServicePrimaryButton
import com.v16studio.v16service.ui.designsystem.V16ServicePinnedBar
import com.v16studio.v16service.ui.designsystem.V16ServiceSecondaryButton
import com.v16studio.v16service.ui.designsystem.V16ServiceNotice
import com.v16studio.v16service.ui.designsystem.V16ServiceNoticeKind
import com.v16studio.v16service.ui.designsystem.V16ServiceTextButtonAdapter as TextButton
import com.v16studio.v16service.ui.designsystem.V16ServiceUiTokens
import com.v16studio.v16service.ui.designsystem.V16ServiceTextField
import com.v16studio.v16service.ui.designsystem.V16ServiceLongTextEditor
import com.v16studio.v16service.ui.icons.V16ServiceIcon
import com.v16studio.v16service.ui.icons.V16ServiceIcons
import com.v16studio.v16service.domain.appointmentEpochMillis
import com.v16studio.v16service.data.AssignedVisitInput
import com.v16studio.v16service.data.DispatchTeamDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

internal enum class VisitSetupMode { EXISTING, NEW }

private fun assignmentLabel(ids: Set<String>?, participants: List<com.v16studio.v16service.data.DispatchTechnicianEntity>): String =
    ids.orEmpty().mapNotNull { id -> participants.firstOrNull { it.technicianId == id }?.displayName }
        .joinToString(" · ").ifBlank { "Everyone on selected teams" }

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

internal data class VisitSetupTaskEditorState(
    val taskName: String = "",
    val subjectType: WorkSubjectType = WorkSubjectType.SITE,
    val equipmentId: String? = null,
    val equipmentDescription: String = "",
    val reusableTemplateId: String? = null,
    val editingTaskId: String? = null,
    val templateSelectionExplicit: Boolean = false,
)

internal fun saveVisitSetupTaskEditorState(state: VisitSetupTaskEditorState): List<String> = listOf(
    state.taskName,
    state.subjectType.name,
    state.equipmentId.orEmpty(),
    state.equipmentDescription,
    state.reusableTemplateId.orEmpty(),
    state.editingTaskId.orEmpty(),
    state.templateSelectionExplicit.toString(),
)

internal fun restoreVisitSetupTaskEditorState(values: List<String>): VisitSetupTaskEditorState? {
    if (values.size !in 6..7) return null
    return runCatching {
        VisitSetupTaskEditorState(
            taskName = values[0],
            subjectType = WorkSubjectType.valueOf(values[1]),
            equipmentId = values[2].takeIf(String::isNotBlank),
            equipmentDescription = values[3],
            reusableTemplateId = values[4].takeIf(String::isNotBlank),
            editingTaskId = values[5].takeIf(String::isNotBlank),
            templateSelectionExplicit = values.getOrNull(6)?.toBooleanStrictOrNull() ?: false,
        )
    }.getOrNull()
}

internal val VisitSetupTaskEditorStateSaver = listSaver<VisitSetupTaskEditorState, String>(
    save = { state -> saveVisitSetupTaskEditorState(state) },
    restore = ::restoreVisitSetupTaskEditorState,
)

internal enum class VisitSetupTemplateSuggestionSource {
    EQUIPMENT_PLAN,
    SITE_PLANS,
    ONLY_ACTIVE_TEMPLATE,
    NONE,
}

internal data class VisitSetupTemplateSuggestion(
    val autoSelectedTemplateId: String? = null,
    val source: VisitSetupTemplateSuggestionSource = VisitSetupTemplateSuggestionSource.NONE,
    val preferredTemplateIds: List<String> = emptyList(),
)

/**
 * Resolves the contextual checklist convenience for a new ad-hoc task. The
 * relationship hints are a Visit-setup projection; only currently active
 * reusable templates are eligible for selection or ordering.
 */
internal fun suggestVisitSetupTemplate(
    subjectType: WorkSubjectType,
    selectedEquipmentId: String?,
    templateIdsByEquipment: Map<String, Set<String>>,
    activeTemplates: List<TemplateSummary>,
): VisitSetupTemplateSuggestion {
    val activeIds = activeTemplates.asSequence()
        .filter { it.state == "ACTIVE" }
        .map { it.id }
        .toSet()
    fun ordered(ids: Set<String>): List<String> = activeTemplates
        .asSequence()
        .filter { it.id in ids && it.id in activeIds }
        .map { it.id }
        .distinct()
        .toList()

    val contextualIds = when {
        subjectType == WorkSubjectType.EQUIPMENT && selectedEquipmentId != null ->
            templateIdsByEquipment[selectedEquipmentId].orEmpty()
        else -> templateIdsByEquipment.values.asSequence().flatten().toSet()
    }
    val preferred = ordered(contextualIds)
    if (preferred.size == 1) {
        return VisitSetupTemplateSuggestion(
            autoSelectedTemplateId = preferred.single(),
            source = if (subjectType == WorkSubjectType.EQUIPMENT && selectedEquipmentId != null) {
                VisitSetupTemplateSuggestionSource.EQUIPMENT_PLAN
            } else {
                VisitSetupTemplateSuggestionSource.SITE_PLANS
            },
            preferredTemplateIds = preferred,
        )
    }
    if (preferred.size > 1) {
        return VisitSetupTemplateSuggestion(preferredTemplateIds = preferred)
    }

    val globalActive = activeTemplates.filter { it.id in activeIds }.map { it.id }.distinct()
    return if (globalActive.size == 1) {
        VisitSetupTemplateSuggestion(
            autoSelectedTemplateId = globalActive.single(),
            source = VisitSetupTemplateSuggestionSource.ONLY_ACTIVE_TEMPLATE,
            preferredTemplateIds = globalActive,
        )
    } else {
        VisitSetupTemplateSuggestion()
    }
}

internal fun applyVisitSetupTemplateSuggestion(
    state: VisitSetupTaskEditorState,
    suggestion: VisitSetupTemplateSuggestion,
): VisitSetupTaskEditorState = if (state.editingTaskId == null && !state.templateSelectionExplicit) {
    state.copy(reusableTemplateId = suggestion.autoSelectedTemplateId)
} else {
    state
}

internal fun selectVisitSetupTemplateExplicitly(
    state: VisitSetupTaskEditorState,
    templateId: String?,
): VisitSetupTaskEditorState = state.copy(reusableTemplateId = templateId, templateSelectionExplicit = true)

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
                    privateNote = values[9], customerType = com.v16studio.v16service.domain.CustomerType.valueOf(values[10]),
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
    contentPadding: PaddingValues = PaddingValues(0.dp, 0.dp, 0.dp, 32.dp),
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
    startInConfiguration: Boolean = false,
    dateErrorMessage: String? = null,
    preludeItems: (LazyListScope.() -> Unit)? = null,
    extensionItems: (LazyListScope.() -> Unit)? = null,
    actionItems: (LazyListScope.() -> Unit)? = null,
) {
    var siteQuery by remember { mutableStateOf("") }
    var taskEditor by rememberSaveable(stateSaver = VisitSetupTaskEditorStateSaver) { mutableStateOf(VisitSetupTaskEditorState()) }
    var siteSelectionVisible by rememberSaveable { mutableStateOf(!startInConfiguration) }
    var stagedSiteId by rememberSaveable { mutableStateOf(draft.siteId) }
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
    val templateSuggestion = suggestVisitSetupTemplate(
        subjectType = taskEditor.subjectType,
        selectedEquipmentId = taskEditor.equipmentId,
        templateIdsByEquipment = selectedSite?.templateIdsByEquipment.orEmpty(),
        activeTemplates = templates,
    )
    val matchingSites = sites.filter { option ->
        siteQuery.isNotBlank() || option.customerType == com.v16studio.v16service.domain.CustomerType.STANDARD
    }.filter { option ->
        siteQuery.isBlank() || option.customerName.contains(siteQuery, true) || option.name.contains(siteQuery, true) || option.reference.contains(siteQuery, true)
    }
    val availablePlans = dueServices.filter { it.siteId == draft.siteId && it.claimedVisitId == null }
    val equipment = selectedSite?.equipment.orEmpty()
    val allowKnownEquipment = draft.mode == VisitSetupMode.EXISTING && selectedSite != null
    val taskValid = taskEditor.taskName.trim().isNotBlank() && taskEditor.taskName.trim().length <= 200 && when {
        taskEditor.subjectType == WorkSubjectType.SITE -> taskEditor.equipmentId == null && taskEditor.equipmentDescription.isBlank()
        !allowKnownEquipment -> taskEditor.equipmentId == null && taskEditor.equipmentDescription.length <= 500
        else -> taskEditor.equipmentDescription.isBlank() && (taskEditor.equipmentId == null || equipment.any { it.id == taskEditor.equipmentId })
    } && (taskEditor.reusableTemplateId != null || draft.tasks.firstOrNull { it.stableUiId == taskEditor.editingTaskId }?.reusableTemplateId == null && taskEditor.editingTaskId != null)

    LaunchedEffect(startInConfiguration) {
        if (startInConfiguration) {
            stagedSiteId = draft.siteId
            siteSelectionVisible = false
        }
    }
    LaunchedEffect(draft.mode) {
        if (draft.mode == VisitSetupMode.NEW) {
            siteSelectionVisible = false
        } else if (!startInConfiguration && draft.siteId == null) {
            stagedSiteId = null
            siteSelectionVisible = true
        }
    }
    LaunchedEffect(
        selectedSite?.id,
        selectedSite?.templateIdsByEquipment,
        taskEditor.subjectType,
        taskEditor.equipmentId,
        templates,
        taskEditor.editingTaskId,
        taskEditor.templateSelectionExplicit,
    ) {
        val suggestedState = applyVisitSetupTemplateSuggestion(taskEditor, templateSuggestion)
        if (suggestedState != taskEditor) {
            taskEditor = suggestedState
        }
    }

    templateReturnNav?.let { nav ->
        VisitSetupTemplateReturnBridge(nav, templates, onRefreshTemplates) { id ->
            taskEditor = selectVisitSetupTemplateExplicitly(taskEditor, id)
        }
    }

    fun update(next: VisitSetupDraft) { if (editable) onDraftChange(next) }
    fun resetTask() { taskEditor = VisitSetupTaskEditorState() }
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
        stagedSiteId = null
        siteQuery = ""; resetTask()
    }
    fun selectSite(next: String) {
        if (editable) stagedSiteId = next
    }
    fun requestSiteChange() {
        if (!editable) return
        stagedSiteId = draft.siteId
        siteQuery = ""
        siteSelectionVisible = true
    }
    fun continueSiteSelection() {
        val next = stagedSiteId ?: return
        when {
            draft.siteId == next -> {
                siteSelectionVisible = false
                siteQuery = ""
            }
            draft.tasks.isEmpty() && draft.selectedPlanIds.isEmpty() -> {
                update(draft.copy(siteId = next))
                siteSelectionVisible = false
                siteQuery = ""
            }
            else -> {
                pendingSiteId = next
                pendingSiteChange = true
            }
        }
    }
    fun saveTask() {
        val task = VisitSetupTaskDraft(
            stableUiId = taskEditor.editingTaskId ?: UUID.randomUUID().toString(),
            taskName = taskEditor.taskName.trim(), subjectType = taskEditor.subjectType,
            equipmentId = taskEditor.equipmentId, equipmentDescription = taskEditor.equipmentDescription.trim(), reusableTemplateId = taskEditor.reusableTemplateId,
        )
        update(draft.copy(tasks = if (taskEditor.editingTaskId == null) draft.tasks + task else draft.tasks.map { if (it.stableUiId == taskEditor.editingTaskId) task else it }))
        resetTask()
    }

    pendingSiteChange.takeIf { it }?.let {
        AlertDialog(
            onDismissRequest = {
                pendingSiteChange = false
                pendingSiteId = null
                stagedSiteId = draft.siteId
                siteSelectionVisible = false
            },
            title = { Text("Change customer or site?") },
            text = { Text("Planned and ad-hoc work added for this site will be cleared.") },
            confirmButton = {
                TextButton({
                    pendingSiteId?.let { next ->
                        update(draft.copy(siteId = next, selectedPlanIds = emptySet(), tasks = emptyList()))
                        stagedSiteId = next
                    }
                    pendingSiteChange = false
                    pendingSiteId = null
                    siteSelectionVisible = false
                    siteQuery = ""
                    resetTask()
                }) { Text("Change") }
            },
            dismissButton = {
                TextButton({
                    pendingSiteChange = false
                    pendingSiteId = null
                    stagedSiteId = draft.siteId
                    siteSelectionVisible = false
                    siteQuery = ""
                }) { Text("Keep current") }
            },
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
        VisitSetupMode.EXISTING -> selectedSite != null && !siteSelectionVisible
        VisitSetupMode.NEW -> draft.newCustomer.isValidForCreate()
    }

    Box(modifier.fillMaxWidth().fillMaxHeight()) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight().testTag("visit-setup-list"), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.md)) {
        preludeItems?.invoke(this)
        item {
            val colors = LocalV16ServiceTokens.current
            Column(Modifier.fillMaxWidth().background(colors.surface)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = V16ServiceUiTokens.Layout.pageInsetCompact)) {
                    VisitSetupSectionHeading("Choose a customer", "choose-visit-customer")
                    Spacer(Modifier.height(V16ServiceUiTokens.Space.sm))
                }
                Box(Modifier.fillMaxWidth().testTag("visit-mode-tabs")) {
                    V16ServiceContentTabs(
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
                Column(Modifier.fillMaxWidth().padding(horizontal = V16ServiceUiTokens.Layout.pageInsetCompact)) {
                    CustomerCreationForm(draft.newCustomer, { update(draft.copy(newCustomer = it)) }, editable = editable)
                    if (targetReady) {
                        Spacer(Modifier.height(V16ServiceUiTokens.Space.sm))
                        VisitSetupSectionHeading("Set up visit", "visit-setup-heading")
                    }
                }
            }
        } else {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = V16ServiceUiTokens.Layout.pageInsetCompact)) {
                    Text("Customer / site", fontWeight = FontWeight.Bold)
                    if (selectedSite != null && !siteSelectionVisible) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Layout.fieldActionGap)) {
                            Text("${selectedSite.customerName} · ${selectedSite.name}", Modifier.weight(1f))
                            V16ServiceFieldAction("Change customer or site", ::requestSiteChange, Modifier.testTag("visit-change-customer-site"), enabled = editable, content = { V16ServiceIcon(V16ServiceIcons.Search, null, Modifier.size(V16ServiceUiTokens.Size.icon), LocalContentColor.current) })
                        }
                    } else {
                        DailyField(siteQuery, { siteQuery = it }, "Find customer or site", enabled = editable)
                    }
                }
            }
            if (siteSelectionVisible || selectedSite == null) {
                items(matchingSites, key = { "visit-site-${it.id}" }) { option ->
                    VisitSiteSelectionRow(option, stagedSiteId == option.id, editable, ::selectSite)
                }
                if (matchingSites.isEmpty()) item { Text(if (sites.isEmpty()) "Add a customer site before creating a visit." else "No matching customer sites.", modifier = Modifier.padding(horizontal = V16ServiceUiTokens.Layout.pageInsetCompact)) }
            } else {
                if (selectedSite.customerType == com.v16studio.v16service.domain.CustomerType.ONE_TIME) item { Text("One-time customers use ad-hoc work.", modifier = Modifier.padding(horizontal = V16ServiceUiTokens.Layout.pageInsetCompact)) }
                if (selectedSite.customerType == com.v16studio.v16service.domain.CustomerType.STANDARD) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = V16ServiceUiTokens.Layout.pageInsetCompact)) {
                            Text("Planned work", fontWeight = FontWeight.Bold)
                            when {
                                plannedWorkLoading -> Text("Reading planned services", color = LocalV16ServiceTokens.current.textSecondary, modifier = Modifier.testTag("planned-work-loading"))
                                plannedWorkError != null -> V16ServiceNotice(
                                    "Planned services are unavailable.",
                                    plannedWorkError,
                                    V16ServiceNoticeKind.Error,
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
                if (targetReady) item { VisitSetupSectionHeading("Set up visit", "visit-setup-heading", Modifier.padding(horizontal = V16ServiceUiTokens.Layout.pageInsetCompact)) }
            }
        }
        if (targetReady) {
            item {
                Column(Modifier.padding(horizontal = V16ServiceUiTokens.Layout.pageInsetCompact), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.md)) {
                    VisitDateInput(draft.serviceDate, { update(draft.copy(serviceDate = it)) }, businessDate.plusDays(1), enabled = editable, errorMessage = dateErrorMessage)
                    VisitTimeInput(draft.appointmentTime, { update(draft.copy(appointmentTime = it)) }, enabled = editable)
                }
            }
            item {
                Box(Modifier.padding(horizontal = V16ServiceUiTokens.Layout.pageInsetCompact)) {
                    VisitSetupTaskEditor(
                        taskEditor.taskName, { taskEditor = taskEditor.copy(taskName = it) }, taskEditor.subjectType, { value -> taskEditor = taskEditor.copy(subjectType = value, equipmentId = if (value == WorkSubjectType.SITE) null else taskEditor.equipmentId, equipmentDescription = if (value == WorkSubjectType.SITE) "" else taskEditor.equipmentDescription) },
                        taskEditor.equipmentId, { taskEditor = taskEditor.copy(equipmentId = it, equipmentDescription = "") }, taskEditor.equipmentDescription, { taskEditor = taskEditor.copy(equipmentDescription = it) },
                        templates,
                        taskEditor.reusableTemplateId,
                        { taskEditor = selectVisitSetupTemplateExplicitly(taskEditor, it) },
                        templateSuggestion.preferredTemplateIds,
                        if (!taskEditor.templateSelectionExplicit && taskEditor.editingTaskId == null && taskEditor.reusableTemplateId == templateSuggestion.autoSelectedTemplateId) templateSuggestion.source else VisitSetupTemplateSuggestionSource.NONE,
                        equipment,
                        allowKnownEquipment,
                        taskValid && editable, taskEditor.editingTaskId != null, editable, allowTemplateCreation, onCreateTemplate, ::saveTask,
                    )
                }
            }
            if (draft.tasks.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = V16ServiceUiTokens.Layout.pageInsetCompact)) {
                        DailyHeading("Tasks")
                        draft.tasks.forEachIndexed { index, task ->
                            VisitSetupTaskRow(index, task, templates, equipment, editable,
                                onEdit = { taskEditor = VisitSetupTaskEditorState(task.taskName, task.subjectType, task.equipmentId, task.equipmentDescription, task.reusableTemplateId, task.stableUiId, templateSelectionExplicit = true) },
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
            if (siteSelectionVisible) item { Spacer(Modifier.height(V16ServiceUiTokens.Size.buttonPrimaryMin + V16ServiceUiTokens.Space.lg)) }
        }
        if (draft.mode == VisitSetupMode.EXISTING && siteSelectionVisible) {
            V16ServicePinnedBar(Modifier.align(Alignment.BottomCenter)) {
                V16ServicePrimaryButton(
                    "Continue",
                    ::continueSiteSelection,
                    Modifier.fillMaxWidth().testTag("visit-site-continue"),
                    enabled = editable && stagedSiteId != null,
                )
            }
        }
    }
}

@Composable
private fun VisitSiteSelectionRow(option: VisitSiteOption, selected: Boolean, enabled: Boolean, onSelected: (String) -> Unit) {
    val colors = LocalV16ServiceTokens.current
    val siteLabel = "${option.reference} ${option.name}"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = V16ServiceUiTokens.Layout.pageInsetCompact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Layout.fieldActionGap),
    ) {
        val shape = RoundedCornerShape(V16ServiceUiTokens.Radius.field)
        Box(
            Modifier.weight(1f)
                .heightIn(min = V16ServiceUiTokens.Size.listRowMin)
                .clip(shape)
                .background(colors.surface)
                .drawWithContent {
                    drawContent()
                    drawRoundRect(
                        color = colors.recordBorder,
                        cornerRadius = CornerRadius(V16ServiceUiTokens.Radius.field.toPx()),
                        style = Stroke(
                            width = V16ServiceUiTokens.Stroke.record.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                        ),
                    )
                }
                .testTag("visit-site-${option.id}"),
        ) {
            Column(Modifier.fillMaxWidth().padding(V16ServiceUiTokens.Space.lg), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.xs)) {
                Text("${option.reference} · ${option.name}", style = V16ServiceUiTokens.Type.itemTitle, color = colors.textPrimary)
                Text(option.customerName, style = V16ServiceUiTokens.Type.supporting, color = colors.textSecondary)
                if (option.customerType == com.v16studio.v16service.domain.CustomerType.ONE_TIME) {
                    Text("One-time", style = V16ServiceUiTokens.Type.meta, color = colors.textMuted)
                }
            }
        }
        Box(
            Modifier.size(V16ServiceUiTokens.Size.touchMin)
                .v16ServiceFocusRing(V16ServiceUiTokens.Radius.field)
                .clip(shape)
                .background(if (selected) colors.action else colors.tonalCommandContainer)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) { onSelected(option.id) }
                .semantics {
                    contentDescription = if (selected) "$siteLabel selected for this visit" else "Select $siteLabel for this visit"
                    if (!enabled) disabled()
                }
                .testTag("visit-site-${option.id}-select"),
            contentAlignment = Alignment.Center,
        ) {
            V16ServiceIcon(V16ServiceIcons.CheckFat, null, Modifier.size(V16ServiceUiTokens.Size.icon), if (selected) colors.selection else colors.tonalCommandInk)
        }
    }
}

@Composable
private fun VisitSetupTaskEditor(
    taskName: String, onTaskName: (String) -> Unit, subjectType: WorkSubjectType, onSubjectType: (WorkSubjectType) -> Unit,
    equipmentId: String?, onEquipmentId: (String?) -> Unit, equipmentDescription: String, onEquipmentDescription: (String) -> Unit,
    templates: List<TemplateSummary>, reusableTemplateId: String?, onReusableTemplateId: (String?) -> Unit,
    preferredTemplateIds: List<String>, suggestionSource: VisitSetupTemplateSuggestionSource,
    equipment: List<EquipmentSummary>, allowKnownEquipment: Boolean, valid: Boolean, editing: Boolean, editable: Boolean, allowTemplateCreation: Boolean,
    onCreateTemplate: () -> Unit, onSave: () -> Unit,
) {
    androidx.compose.material3.Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (editing) "Edit task" else "Add task", fontWeight = FontWeight.Bold)
            DailyField(taskName, onTaskName, "Task name · Required", enabled = editable)
            Text("Subject", fontWeight = FontWeight.Medium)
            V16ServiceChoicePair(listOf(WorkSubjectType.SITE to "Site", WorkSubjectType.EQUIPMENT to "Equipment"), subjectType, onSubjectType, testTagPrefix = "task-subject", enabled = editable)
            if (subjectType == WorkSubjectType.EQUIPMENT) {
                if (!allowKnownEquipment) {
                    Text("Describe the equipment for this new site.", style = MaterialTheme.typography.bodySmall)
                    DailyField(equipmentDescription, onEquipmentDescription, "Equipment description · Optional", enabled = editable)
                } else {
                    VisitEquipmentRadioList(listOf<Pair<String?, String>>(null to "No specific equipment yet") + equipment.map { it.id to "${it.reference} · ${it.name}" }, equipmentId, onEquipmentId, editable)
                    if (equipmentId == null) DailyField(equipmentDescription, onEquipmentDescription, "Equipment description · Optional", enabled = editable)
                }
            }
            InspectionChecklistSelector(
                templates,
                reusableTemplateId,
                onReusableTemplateId,
                "task-template",
                onCreateTemplate,
                enabled = editable && allowTemplateCreation,
                required = !editing || reusableTemplateId != null,
                preferredTemplateIds = preferredTemplateIds,
                suggestionSource = suggestionSource,
            )
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
    val colors = LocalV16ServiceTokens.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.xs)) {
        options.forEach { (value, label) ->
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = V16ServiceUiTokens.Size.touchMin)
                    .background(if (value == selected) colors.selection else colors.surface, RoundedCornerShape(V16ServiceUiTokens.Radius.field))
                    .selectable(selected = value == selected, enabled = enabled, role = Role.RadioButton) { onSelected(value) }
                    .semantics { if (!enabled) disabled() }
                    .testTag("task-equipment-${value ?: "none"}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = value == selected, onClick = null, enabled = enabled)
                Text(label, color = if (enabled) colors.textPrimary else colors.disabledText, style = V16ServiceUiTokens.Type.supporting)
            }
        }
    }
}

@Composable
private fun VisitSetupTaskRow(index: Int, task: VisitSetupTaskDraft, templates: List<TemplateSummary>, equipment: List<EquipmentSummary>, enabled: Boolean, onEdit: () -> Unit, onRemove: () -> Unit) {
    V16ServiceEntityRecord(
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
    preferredTemplateIds: List<String> = emptyList(),
    suggestionSource: VisitSetupTemplateSuggestionSource = VisitSetupTemplateSuggestionSource.NONE,
) {
    val active = templates.filter { it.state == "ACTIVE" }
    val selectedDisabled = templates.firstOrNull { it.id == selectedTemplateId && it.state == "DISABLED" }
    val orderedActive = buildList {
        addAll(preferredTemplateIds.mapNotNull { id -> active.firstOrNull { it.id == id } })
        addAll(active.filterNot { template -> preferredTemplateIds.contains(template.id) })
    }.distinctBy { it.id }
    val options = buildList<Pair<String?, String>> {
        add(null to "None")
        addAll(orderedActive.map { it.id to "${it.name} (v${it.revisionNumber})" })
        selectedDisabled?.let { add(it.id to "${it.name} (v${it.revisionNumber}) · Disabled") }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Layout.fieldActionGap)) {
            if (active.isEmpty() && selectedDisabled == null) {
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            } else {
                V16ServiceFilterSelector(label, selectedTemplateId, options, if (enabled) onSelected else { _ -> }, Modifier.weight(1f), "$testTag-selector", enabled = enabled)
            }
            V16ServiceFieldAction(
                "Create inspection template",
                onCreateTemplate,
                Modifier.testTag("$testTag-create"),
                enabled = enabled,
                content = { V16ServiceIcon(V16ServiceIcons.PlusBold, null, Modifier.size(V16ServiceUiTokens.Size.icon), LocalContentColor.current) },
            )
        }
        when (suggestionSource) {
            VisitSetupTemplateSuggestionSource.EQUIPMENT_PLAN -> Text("Suggested from this equipment's service plan.", style = V16ServiceUiTokens.Type.meta, color = LocalV16ServiceTokens.current.textSecondary, modifier = Modifier.testTag("$testTag-suggestion"))
            VisitSetupTemplateSuggestionSource.SITE_PLANS -> Text("Suggested from this site's service plans.", style = V16ServiceUiTokens.Type.meta, color = LocalV16ServiceTokens.current.textSecondary, modifier = Modifier.testTag("$testTag-suggestion"))
            VisitSetupTemplateSuggestionSource.ONLY_ACTIVE_TEMPLATE -> Text("Only active inspection checklist.", style = V16ServiceUiTokens.Type.meta, color = LocalV16ServiceTokens.current.textSecondary, modifier = Modifier.testTag("$testTag-suggestion"))
            VisitSetupTemplateSuggestionSource.NONE -> Unit
        }
        if (required && active.isEmpty() && selectedDisabled == null) {
            V16ServiceNotice(
                "Inspection checklist required",
                "Create an inspection template before adding this task.",
                V16ServiceNoticeKind.Warning,
                modifier = Modifier.testTag("$testTag-required"),
            )
        } else if (required && selectedTemplateId == null) {
            Text("Select an inspection checklist.", color = LocalV16ServiceTokens.current.errorInk, modifier = Modifier.testTag("$testTag-required-selection"), style = MaterialTheme.typography.bodySmall)
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.xs)) {
        Text("Date", style = V16ServiceUiTokens.Type.label)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Layout.fieldActionGap)) {
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
            V16ServiceFieldAction(
                "Choose appointment date",
                { showPicker = true },
                Modifier.testTag("appointment-date-picker"),
                enabled = enabled,
                content = { V16ServiceIcon(V16ServiceIcons.Calendar, null, Modifier.size(V16ServiceUiTokens.Size.icon), LocalContentColor.current) },
            )
        }
        errorMessage?.let { Text(it, color = LocalV16ServiceTokens.current.errorInk, style = V16ServiceUiTokens.Type.meta, modifier = Modifier.testTag("appointment-date-error")) }
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.xs)) {
        Text("Time · optional", style = V16ServiceUiTokens.Type.label)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Layout.fieldActionGap)) {
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
            V16ServiceFieldAction(
                "Choose appointment time",
                { showPicker = true },
                Modifier.testTag("appointment-time-picker"),
                enabled = enabled,
                content = { V16ServiceIcon(V16ServiceIcons.Time, null, Modifier.size(V16ServiceUiTokens.Size.icon), LocalContentColor.current) },
            )
        }
        if (value.isNotBlank() && parsed == null) {
            Text("Enter time as HH:mm", style = V16ServiceUiTokens.Type.meta, color = LocalV16ServiceTokens.current.errorInk, modifier = Modifier.testTag("appointment-time-error"))
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
        color = LocalV16ServiceTokens.current.textPrimary,
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
    val colors = LocalV16ServiceTokens.current
    val shape = RoundedCornerShape(V16ServiceUiTokens.Radius.field)
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            isError = isError,
            enabled = enabled,
            textStyle = V16ServiceUiTokens.Type.body.copy(color = colors.textPrimary, textAlign = TextAlign.Center),
            placeholder = placeholder?.let { hint ->
                {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(hint, style = V16ServiceUiTokens.Type.body, color = colors.textMuted, textAlign = TextAlign.Center)
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
            modifier = Modifier.fillMaxWidth().heightIn(min = V16ServiceUiTokens.Size.fieldMin)
                .v16ServiceFocusRing(V16ServiceUiTokens.Radius.field).testTag(testTag),
        )
        leadingText?.let {
            Text(
                it,
                style = V16ServiceUiTokens.Type.label,
                color = if (it == "—") colors.textMuted else colors.textSecondary,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = V16ServiceUiTokens.Space.md)
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
    viewModel: V16ServiceViewModel,
    nav: NavHostController,
    initialPlanIds: List<String> = emptyList(),
) {
    val capabilities = LocalWorkspaceCapabilities.current
    val context = LocalContext.current
    var teams by remember { mutableStateOf(emptyList<DispatchTeamDetail>()) }
    var selectedTeamIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var assignmentJson by rememberSaveable { mutableStateOf("{}") }
    var showTeamPicker by remember { mutableStateOf(false) }
    var assigneeKey by remember { mutableStateOf<String?>(null) }
    var managerReference by rememberSaveable { mutableStateOf("") }
    var dispatchInstructions by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(capabilities.canAssignWork) {
        if (capabilities.canAssignWork) teams = withContext(Dispatchers.IO) { dispatchService(context).teams() }
    }
    val selectedTeams = selectedTeamIds.toSet()
    val participants = teams.filter { it.team.id in selectedTeams }.flatMap { it.members.map { member -> member.first } }.distinctBy { it.technicianId }
    val assignments = remember(assignmentJson) {
        val obj = JSONObject(assignmentJson)
        obj.keys().asSequence().associateWith { key -> obj.getJSONArray(key).let { array -> (0 until array.length()).map { array.getString(it) }.toSet() } }
    }
    fun setAssignment(key: String, ids: Set<String>) {
        assignmentJson = JSONObject(assignmentJson).put(key, org.json.JSONArray(ids.sorted())).toString()
    }
    if (showTeamPicker) DispatchTeamSelectionDialog(teams, selectedTeams, { showTeamPicker = false }) { next ->
        val allowed = teams.filter { it.team.id in next }.flatMap { it.members }.map { it.first.technicianId }.toSet()
        if (assignments.values.all { ids -> ids.all { it in allowed } }) { selectedTeamIds = ArrayList(next.sorted()); showTeamPicker = false }
    }
    assigneeKey?.let { key -> DispatchAssigneeDialog(participants, assignments[key].orEmpty(), { assigneeKey = null }) { next -> setAssignment(key, next); assigneeKey = null } }
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
    val valid = targetReady && validDate && validTime && !state.operationInProgress && (selectedTeams.isEmpty() || participants.isNotEmpty()) && if (draft.mode == VisitSetupMode.NEW) {
        draft.newCustomer.isValidForCreate() && draft.tasks.isNotEmpty()
    } else {
        selectedSite != null && (draft.selectedPlanIds.isNotEmpty() || draft.tasks.isNotEmpty())
    }
    val parsedDate = runCatching { java.time.LocalDate.parse(draft.serviceDate) }.getOrNull()
    val pastBookedDate = parsedDate?.isBefore(state.businessDate) == true
    UnsavedChangesGuard(visitSetupIsDirty(baseline, draft) || selectedTeams.isNotEmpty() || assignmentJson != "{}" || managerReference.isNotBlank() || dispatchInstructions.isNotBlank(), nav)

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
        if (selectedTeams.isNotEmpty() && targetState == "BOOKED") {
            val planIds = draft.selectedPlanIds.toList()
            val assignees = planIds.map { assignments["PLAN:$it"].orEmpty().toList() } + draft.tasks.map { assignments["TASK:${it.stableUiId}"].orEmpty().toList() }
            viewModel.createAssignedVisit(AssignedVisitInput(
                siteId = draft.siteId.takeIf { draft.mode == VisitSetupMode.EXISTING },
                newCustomerSite = draft.newCustomer.toInput().takeIf { draft.mode == VisitSetupMode.NEW },
                planIds = planIds, tasks = adHoc, serviceDate = serviceDate, scheduledAtEpochMillis = scheduledAt,
                teamIds = selectedTeamIds, workAssignees = assignees,
                managerReference = managerReference, instructions = dispatchInstructions,
            ), success)
        } else if (draft.mode == VisitSetupMode.NEW) {
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
        extensionItems = {
            if (capabilities.canAssignWork) item {
                Column(Modifier.fillMaxWidth().padding(horizontal = V16ServiceUiTokens.Layout.pageInsetCompact), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm)) {
                    Text("Assignment", style = MaterialTheme.typography.titleMedium)
                    V16ServiceSecondaryButton(teams.filter { it.team.id in selectedTeams }.joinToString(" · ") { it.team.name }.ifBlank { "Choose teams" }, { showTeamPicker = true }, Modifier.fillMaxWidth().testTag("visit-choose-teams"))
                    if (selectedTeams.isNotEmpty()) {
                        state.dueServices.filter { it.planId in draft.selectedPlanIds }.forEach { due ->
                            val key = "PLAN:${due.planId}"
                            V16ServiceSecondaryButton("${due.planName} · ${assignmentLabel(assignments[key], participants)}", { assigneeKey = key }, Modifier.fillMaxWidth().testTag("visit-assign-${due.planId}"))
                        }
                        draft.tasks.forEach { task ->
                            val key = "TASK:${task.stableUiId}"
                            V16ServiceSecondaryButton("${task.taskName} · ${assignmentLabel(assignments[key], participants)}", { assigneeKey = key }, Modifier.fillMaxWidth().testTag("visit-assign-${task.stableUiId}"))
                        }
                        V16ServiceTextField(managerReference, { managerReference = it }, "Reference", modifier = Modifier.testTag("visit-manager-reference"))
                        V16ServiceLongTextEditor(dispatchInstructions, { dispatchInstructions = it }, "Instructions", private = false)
                    }
                }
            }
        },
        actionItems = {
            item {
                val parsed = runCatching { java.time.LocalDate.parse(draft.serviceDate) }.getOrNull()
                val primary = when { parsed == null || parsed.isAfter(state.businessDate) -> "BOOKED"; parsed == state.businessDate -> "WORKING"; else -> "HISTORICAL" }
                @Composable fun action(kind: String, label: String) {
                    val enabled = valid && (selectedTeams.isEmpty() || kind == "BOOKED") && (kind != "HISTORICAL" || parsed != null && !parsed.isAfter(state.businessDate)) && (kind != "BOOKED" || !pastBookedDate)
                    val click = { save(kind) }
                    if (primary == kind) V16ServicePrimaryButton(label, click, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("primary-visit-action-$kind"))
                    else V16ServiceSecondaryButton(label, click, enabled = enabled, modifier = Modifier.fillMaxWidth())
                }
                V16ServiceActionStack {
                    action("BOOKED", "Book visit")
                    if (capabilities.canPerformFieldWork) {
                        action("WORKING", "Start now")
                        action("HISTORICAL", "Record past visit")
                    }
                }
            }
        },
    )
}
