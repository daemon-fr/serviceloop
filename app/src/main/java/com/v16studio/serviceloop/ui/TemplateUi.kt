package com.v16studio.serviceloop.ui

import com.v16studio.serviceloop.ui.designsystem.ServiceLoopCheckbox as Checkbox

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.ServiceLoopApplication
import com.v16studio.serviceloop.data.InspectionTemplateCodec
import com.v16studio.serviceloop.data.InspectionTemplateExchangeService
import com.v16studio.serviceloop.data.InspectionTemplateImportClassification
import com.v16studio.serviceloop.data.InspectionTemplateImportPreview
import com.v16studio.serviceloop.domain.*
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopButtonAdapter as Button
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopCardAdapter as Card
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChoiceGroup
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopEntityRecord
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDenseNavigableRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDestructiveButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopLongTextEditor
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopOutlinedButtonAdapter as OutlinedButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopResponsivePair
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSecondaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopStatusBadge
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopIconOnlyAction
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFilterSelector
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNavigationButton
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextButtonAdapter as TextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun TemplateListScreen(values: List<TemplateSummary>, padding: PaddingValues, nav: NavHostController, viewModel: ServiceLoopViewModel? = null, incomingTemplates: String? = null) {
    InspectionTemplateLibraryContent(values, padding, nav, viewModel, incomingTemplates, showHeading = false)
}

@Composable
internal fun InspectionTemplateLibraryContent(
    values: List<TemplateSummary>,
    padding: PaddingValues,
    nav: NavHostController,
    viewModel: ServiceLoopViewModel? = null,
    incomingTemplates: String? = null,
    showHeading: Boolean = true,
    createReturnTo: String? = null,
) {
    val capabilities = LocalWorkspaceCapabilities.current
    Column(Modifier.padding(padding).testTag("inspection-templates"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showHeading) Text("Inspection templates", style = MaterialTheme.typography.titleLarge)
        Button({ nav.navigate(if (createReturnTo == null) "template/new" else "template/new?returnTo=$createReturnTo") }, Modifier.fillMaxWidth().testTag("create-inspection-template")) { Text("Create inspection template") }
        if (values.none { it.state != "DELETED" }) Text("No inspection templates yet")
        values.asSequence()
            .filter { it.state != "DELETED" }
            .sortedWith(compareBy<TemplateSummary> { it.state != "ACTIVE" }.thenBy { it.name.lowercase() }.thenBy { it.reference })
            .forEach { template ->
                ServiceLoopEntityRecord("${template.reference} · ${template.name} (v${template.revisionNumber})", metadata = "${template.itemCount} items", status = template.state, modifier = Modifier.fillMaxWidth()) { nav.navigate("template/${template.id}") }
            }
    }
}

@Composable
internal fun TemplateDetailScreen(
    detail: TemplateDetail?,
    padding: PaddingValues,
    nav: NavHostController,
    viewModel: ServiceLoopViewModel? = null,
    planReferenceCount: Int = 0,
    versions: List<TemplateRevisionDetail> = emptyList(),
) {
    if (detail == null) return DailyEmpty(padding, "Reading template")
    var showDeleteConfirmation by remember(detail.id) { mutableStateOf(false) }
    var showDeleteBlocked by remember(detail.id, planReferenceCount) { mutableStateOf(false) }
    LazyColumn(Modifier.padding(padding).testTag("inspection-template-detail"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.md)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)) {
                Text("${detail.reference} · ${detail.name} (v${detail.revisionNumber})", style = MaterialTheme.typography.headlineSmall)
                ServiceLoopStatusBadge(detail.state)
                ServiceLoopResponsivePair(
                    first = { ServiceLoopDestructiveButton("Delete", { if (planReferenceCount > 0) showDeleteBlocked = true else showDeleteConfirmation = true }, Modifier.fillMaxWidth(), enabled = viewModel != null, showIcon = false) },
                    second = { ServiceLoopSecondaryButton("Clone", { nav.navigate("template/new?cloneFrom=${detail.id}") }, Modifier.fillMaxWidth()) },
                )
                ServiceLoopResponsivePair(
                    first = { ServiceLoopSecondaryButton(if (detail.state == "DISABLED") "Activate" else "Disable", { viewModel?.setTemplateState(detail.id, if (detail.state == "DISABLED") "ACTIVE" else "DISABLED") }, Modifier.fillMaxWidth(), enabled = viewModel != null) },
                    second = { ServiceLoopPrimaryButton("Edit", { nav.navigate("template/edit/${detail.id}") }, Modifier.fillMaxWidth(), enabled = detail.state != "DELETED") },
                )
            }
        }
        item { DailyHeading("Checklist") }
        items(detail.items.withIndex().toList(), key = { "template-detail-item-${it.index}" }) { (index, item) ->
            ServiceLoopDenseNavigableRow(
                title = "${index + 1}. ${item.label}",
                context = item.responseType,
                metadata = buildString { append(if (item.required) "Required" else "Optional"); item.unit.takeIf(String::isNotBlank)?.let { append(" · $it") } },
                showDisclosure = false,
                onClick = null,
                modifier = Modifier.testTag("template-detail-item-$index"),
            )
        }
        item {
            ServiceLoopNavigationButton(
                label = "Version history (${versions.size.coerceAtLeast(1)})",
                onClick = { nav.navigate("template/history/${detail.id}") },
                modifier = Modifier.fillMaxWidth().testTag("template-version-history"),
            )
        }
    }
    if (showDeleteBlocked) AlertDialog(
        onDismissRequest = { showDeleteBlocked = false },
        title = { Text("Cannot delete template") },
        text = { Text("This template is still used by a service plan. Remove it from those plans before deleting it.") },
        confirmButton = { TextButton({ showDeleteBlocked = false }) { Text("Close") } },
    )
    if (showDeleteConfirmation) AlertDialog(
        onDismissRequest = { showDeleteConfirmation = false },
        title = { Text("Delete inspection template?") },
        text = { Text("This removes the template from ordinary lists. Existing Visit checklists and records are kept.") },
        dismissButton = { TextButton({ showDeleteConfirmation = false }) { Text("Cancel") } },
        confirmButton = { ServiceLoopDestructiveButton("Delete", { viewModel?.deleteTemplate(detail.id) { nav.popBackStack() } }, enabled = viewModel != null) },
    )
}

@Composable
internal fun TemplateHistoryScreen(versions: List<TemplateRevisionDetail>, padding: PaddingValues, templateId: String, nav: NavHostController) {
    LazyColumn(Modifier.padding(padding).testTag("template-version-history-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)) {
        item { DailyHeading("Version history (${versions.size})") }
        items(versions, key = { it.id }) { version ->
            ServiceLoopDenseNavigableRow(
                title = "${version.name} (v${version.revisionNumber})",
                context = "${version.items.size} items",
                metadata = if (version == versions.firstOrNull()) "Current" else "Read-only revision",
                onClick = { nav.navigate("template/version/$templateId/${version.id}") },
                modifier = Modifier.testTag("template-version-${version.revisionNumber}"),
            )
        }
    }
}

@Composable
internal fun TemplateVersionScreen(version: TemplateRevisionDetail?, padding: PaddingValues) {
    if (version == null) return DailyEmpty(padding, "Reading template version")
    LazyColumn(Modifier.padding(padding).testTag("template-version-detail"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)) {
        item { Text("${version.name} (v${version.revisionNumber})", style = MaterialTheme.typography.headlineSmall); Text("Read-only version") }
        items(version.items.withIndex().toList(), key = { "template-version-item-${it.index}" }) { (index, item) ->
            ServiceLoopDenseNavigableRow(
                title = "${index + 1}. ${item.label}",
                context = item.responseType,
                metadata = if (item.required) "Required" else "Optional",
                showDisclosure = false,
                onClick = null,
            )
        }
    }
}

@Composable
private fun TemplateTypeChoice(type: String, selected: Boolean, description: String, onSelected: () -> Unit) {
    val colors = LocalServiceLoopTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = ServiceLoopUiTokens.Size.touchMin)
            .selectable(selected = selected, enabled = true, role = Role.RadioButton, onClick = onSelected)
            .testTag("new-template-type-$type"),
        shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field),
        color = if (selected) colors.selection else colors.surface,
        border = BorderStroke(
            if (selected) ServiceLoopUiTokens.Stroke.selected else ServiceLoopUiTokens.Stroke.outline,
            if (selected) colors.selectionOutline else colors.outlineControl,
        ),
    ) {
        Column(Modifier.padding(ServiceLoopUiTokens.Space.md), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
            Text(
                type,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) colors.selectionInk else colors.textSecondary,
            )
            Text(description, maxLines = 2, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
    }
}

@Composable
private fun TemplateItemActionRow(
    index: Int,
    lastIndex: Int,
    primaryAccessibleName: String,
    primaryIcon: Int,
    primaryTestTag: String,
    onPrimary: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalServiceLoopTokens.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ServiceLoopIconOnlyAction(
            accessibleName = "Delete item",
            icon = ServiceLoopIcons.Delete,
            onClick = onDelete,
            containerColor = colors.destructive,
            contentColor = colors.onDestructive,
            testTag = "template-item-delete-$index",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
            ServiceLoopIconOnlyAction(
                accessibleName = "Move item up",
                icon = ServiceLoopIcons.ArrowFatLineUp,
                onClick = onMoveUp,
                enabled = index > 0,
                containerColor = colors.selection,
                contentColor = colors.action,
                testTag = "template-item-move-up-$index",
            )
            ServiceLoopIconOnlyAction(
                accessibleName = "Move item down",
                icon = ServiceLoopIcons.ArrowFatLineDown,
                onClick = onMoveDown,
                enabled = index < lastIndex,
                containerColor = colors.selection,
                contentColor = colors.action,
                testTag = "template-item-move-down-$index",
            )
        }
        ServiceLoopIconOnlyAction(
            accessibleName = primaryAccessibleName,
            icon = primaryIcon,
            onClick = onPrimary,
            containerColor = colors.action,
            contentColor = colors.onAction,
            testTag = primaryTestTag,
        )
    }
}

@Composable
private fun TemplateItemDraftEditor(
    index: Int,
    lastIndex: Int,
    item: TemplateItemDraft,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (TemplateItemDraft) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().testTag("template-item-$index")) {
        Column(Modifier.padding(ServiceLoopUiTokens.Space.md), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)) {
            ServiceLoopDenseNavigableRow(
                title = "${index + 1}. ${item.label}",
                context = item.responseType,
                metadata = if (item.required) "Required" else "Optional",
                showDisclosure = true,
                onClick = onToggle,
                contentPadding = PaddingValues(0.dp),
                disclosureIcon = if (expanded) ServiceLoopIcons.CaretDown else ServiceLoopIcons.CaretRight,
                disclosureTestTag = "template-item-caret-${if (expanded) "down" else "right"}-$index",
            )
            if (expanded) {
                DailyField(item.label, { onChange(item.copy(label = it)) }, "Item label")
                ServiceLoopFilterSelector("Response type", item.responseType, listOf("STATUS", "TEXT", "NUMBER").map { it to it }, { onChange(item.copy(responseType = it)) }, testTag = "template-item-type-$index")
                if (item.responseType == "NUMBER") DailyField(item.unit, { onChange(item.copy(unit = it)) }, "Unit")
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(item.required, { onChange(item.copy(required = it)) }); Text("Required") }
                ServiceLoopLongTextEditor(item.privateGuidance, { onChange(item.copy(privateGuidance = it)) }, "Private technician guidance", true, fieldTestTag = "template-item-guidance-$index", compact = true)
                TemplateItemActionRow(
                    index = index,
                    lastIndex = lastIndex,
                    primaryAccessibleName = "Save item changes",
                    primaryIcon = ServiceLoopIcons.CheckFat,
                    primaryTestTag = "template-item-save-$index",
                    onPrimary = onToggle,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onDelete = onRemove,
                    modifier = Modifier.testTag("template-item-tool-strip-$index"),
                )
            } else {
                TemplateItemActionRow(
                    index = index,
                    lastIndex = lastIndex,
                    primaryAccessibleName = "Edit item",
                    primaryIcon = ServiceLoopIcons.PencilSimple,
                    primaryTestTag = "template-item-edit-$index",
                    onPrimary = onToggle,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onDelete = onRemove,
                    modifier = Modifier.testTag("template-item-collapsed-actions-$index"),
                )
            }
        }
    }
}

@Composable
internal fun TemplateEditorScreen(
    existing: TemplateDetail?,
    padding: PaddingValues,
    state: UiState,
    viewModel: ServiceLoopViewModel,
    nav: NavHostController,
    cloneSource: TemplateDetail? = null,
    returnTo: String? = null,
    focusItem: Int? = null,
) {
    val seed = cloneSource ?: existing
    val initialName = when {
        existing != null -> existing.name
        cloneSource != null -> "${cloneSource.name} copy"
        else -> ""
    }
    val initialItems = seed?.items.orEmpty()
    var name by rememberSaveable(existing?.id, cloneSource?.id) { mutableStateOf(initialName) }
    var draftItems by remember(existing?.id, cloneSource?.id) { mutableStateOf(initialItems) }
    var expandedIndex by rememberSaveable(focusItem) { mutableStateOf(focusItem) }
    var label by rememberSaveable(existing?.id, cloneSource?.id) { mutableStateOf("") }
    var type by rememberSaveable(existing?.id, cloneSource?.id) { mutableStateOf("STATUS") }
    var unit by rememberSaveable(existing?.id, cloneSource?.id) { mutableStateOf("") }
    var required by rememberSaveable(existing?.id, cloneSource?.id) { mutableStateOf(true) }
    var guidance by rememberSaveable(existing?.id, cloneSource?.id) { mutableStateOf("") }
    val dirty = name != initialName || draftItems != initialItems || label.isNotBlank() || unit.isNotBlank() || guidance.isNotBlank()
    UnsavedChangesGuard(dirty, nav)
    fun finish(id: String) {
        viewModel.loadTemplates()
        if (returnTo != null) {
            nav.previousBackStackEntry?.savedStateHandle?.set(CREATED_INSPECTION_TEMPLATE_ID_KEY, id)
            nav.popBackStack()
        } else {
            nav.navigate("template/$id") { launchSingleTop = true }
        }
    }
    EditorColumn(padding, state, tag = "template-editor") {
        item { DailyHeading(if (existing == null) "Create inspection template" else "Edit template"); DailyField(name, { name = it }, "Template name · Required") }
        items(draftItems.indices.toList(), key = { "template-item-editor-$it" }) { index ->
            val item = draftItems[index]
            TemplateItemDraftEditor(
                index = index,
                lastIndex = draftItems.lastIndex,
                item = item,
                expanded = expandedIndex == index,
                onToggle = { expandedIndex = if (expandedIndex == index) null else index },
                onChange = { value -> draftItems = draftItems.toMutableList().also { it[index] = value } },
                onMoveUp = {
                    if (index > 0) {
                        draftItems = draftItems.toMutableList().also { list ->
                            val moved = list[index]
                            list[index] = list[index - 1]
                            list[index - 1] = moved
                        }
                        if (expandedIndex == index) expandedIndex = index - 1
                    }
                },
                onMoveDown = {
                    if (index < draftItems.lastIndex) {
                        draftItems = draftItems.toMutableList().also { list ->
                            val moved = list[index]
                            list[index] = list[index + 1]
                            list[index + 1] = moved
                        }
                        if (expandedIndex == index) expandedIndex = index + 1
                    }
                },
                onRemove = { draftItems = draftItems.filterIndexed { itemIndex, _ -> itemIndex != index }; expandedIndex = null },
            )
        }
        item {
            Text("Add new item", style = MaterialTheme.typography.titleMedium)
            DailyField(label, { label = it }, "Item label")
            Text("Item type", style = ServiceLoopUiTokens.Type.label, color = LocalServiceLoopTokens.current.textSecondary, modifier = Modifier.testTag("new-template-item-type-label"))
            Spacer(Modifier.height(ServiceLoopUiTokens.Space.xs))
            Column(verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)) {
                TemplateTypeChoice("STATUS", type == "STATUS", "Record whether the check is satisfactory, needs attention, or cannot be completed.") { type = "STATUS" }
                TemplateTypeChoice("TEXT", type == "TEXT", "Record a written observation, note, or result.") { type = "TEXT" }
                TemplateTypeChoice("NUMBER", type == "NUMBER", "Record a measured value, with a unit when needed.") { type = "NUMBER" }
            }
            if (type == "NUMBER") DailyField(unit, { unit = it }, "Unit")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(required, { required = it }); Text("Required") }
            ServiceLoopLongTextEditor(guidance, { guidance = it }, "Private technician guidance", true, fieldTestTag = "new-template-private-guidance", compact = true)
            ServiceLoopSecondaryButton("Add item", { draftItems = draftItems + TemplateItemDraft(label.trim(), type, unit.trim(), required, guidance.trim()); label = ""; unit = ""; guidance = ""; required = true }, enabled = label.trim().isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("add-template-item"))
        }
        item {
            ServiceLoopPrimaryButton(
                "Save template",
                { if (existing == null) viewModel.createTemplate(name, draftItems, ::finish) else viewModel.reviseTemplate(existing.id, name, draftItems, ::finish) },
                enabled = name.trim().isNotBlank() && draftItems.isNotEmpty() && !state.operationInProgress,
                modifier = Modifier.fillMaxWidth().testTag("save-template"),
            )
        }
    }
}
