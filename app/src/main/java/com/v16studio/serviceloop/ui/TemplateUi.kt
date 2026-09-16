package com.v16studio.serviceloop.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.v16studio.serviceloop.data.INSPECTION_TEMPLATES_MIME
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
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopIconAction
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFilterSelector
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exchange = remember { InspectionTemplateExchangeService((context.applicationContext as ServiceLoopApplication).container.database) }
    val role = remember { context.teamRole() }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var preview by remember { mutableStateOf<InspectionTemplateImportPreview?>(null) }
    var createSeparate by remember { mutableStateOf<Set<String>>(emptySet()) }
    var message by remember { mutableStateOf<String?>(null) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input -> exchange.preview(input.readDailyBounded(InspectionTemplateCodec.MAX_BYTES) ?: error("Selected file is too large")) }
                            ?: error("Selected file is not readable")
                    }
                }
                result.onSuccess { preview = it; createSeparate = emptySet() }.onFailure { message = it.message }
            }
        }
    }
    LaunchedEffect(incomingTemplates) {
        if (incomingTemplates != null) {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(Uri.parse(incomingTemplates))?.use { input -> exchange.preview(input.readDailyBounded(InspectionTemplateCodec.MAX_BYTES) ?: error("Received inspection template file is too large")) }
                        ?: error("Received inspection template file is not readable")
                }
            }
            result.onSuccess { preview = it; createSeparate = emptySet() }.onFailure { message = it.message }
        }
    }
    preview?.let { incoming ->
        AlertDialog(
            modifier = Modifier.testTag("inspection-template-import-preview"),
            onDismissRequest = { preview = null },
            title = { Text("Import inspection templates") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Review the current-only .slinsp package before saving it locally.")
                    incoming.entries.forEach { entry ->
                        Text("${entry.transfer.reference} · ${entry.transfer.name}")
                        Text("Revision ${entry.transfer.revision} · ${entry.transfer.items.size} items · ${entry.classification.name.replace('_', ' ')}")
                        if (entry.classification == InspectionTemplateImportClassification.CONFLICT) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Checkbox(entry.transfer.reference in createSeparate, { checked -> createSeparate = if (checked) createSeparate + entry.transfer.reference else createSeparate - entry.transfer.reference }, Modifier.testTag("create-separate-${entry.transfer.reference}"))
                                Text("Create separate")
                            }
                        }
                    }
                }
            },
            dismissButton = { TextButton({ preview = null }) { Text("Cancel") } },
            confirmButton = {
                Button({
                    scope.launch {
                        val result = runCatching { withContext(Dispatchers.IO) { exchange.import(incoming, createSeparate) } }
                        result.onSuccess { imported ->
                            message = "Imported ${imported.importedReferences.size} inspection template${if (imported.importedReferences.size == 1) "" else "s"}. Exact matches were left unchanged."
                            preview = null
                            viewModel?.loadTemplates()
                        }.onFailure { message = it.message }
                    }
                }, enabled = incoming.canImport(createSeparate), modifier = Modifier.testTag("import-inspection-templates")) { Text("Import selected") }
            },
        )
    }
    LazyColumn(Modifier.padding(padding).testTag("inspection-templates"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Button({ nav.navigate("template/new") }, Modifier.fillMaxWidth()) { Text("Create inspection template") }
            if (role in setOf(TeamRole.MEMBER, TeamRole.COORDINATOR)) {
                Spacer(Modifier.height(8.dp))
                Text("Inspection templates", style = MaterialTheme.typography.titleMedium)
                Text("Share inspection templates only. Visits already created with a template keep their checklist.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton({ open.launch(arrayOf(INSPECTION_TEMPLATES_MIME, "application/json")) }, Modifier.weight(1f).testTag("import-inspection-templates-button")) { Text("Import templates") }
                    OutlinedButton({ scope.launch { runCatching { val export = withContext(Dispatchers.IO) { exchange.export(selected) }; val bytes = withContext(Dispatchers.IO) { InspectionTemplateCodec.encode(export) }; shareFile(context, "inspection-templates", "serviceloop-inspection-templates-${System.currentTimeMillis()}.slinsp", INSPECTION_TEMPLATES_MIME, bytes, "Share inspection templates", "ServiceLoop inspection templates", "ServiceLoop inspection templates\nGenerated with ServiceLoop") }.onFailure { message = it.message } } }, enabled = selected.isNotEmpty(), modifier = Modifier.weight(1f).testTag("export-inspection-templates")) { Text("Export selected") }
                }
            }
            message?.let { Text(it, color = if (it.startsWith("Imported")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.testTag("inspection-template-message")) }
        }
        if (values.isEmpty()) item { Text("No inspection templates yet") }
        items(values.sortedWith(compareBy<TemplateSummary> { it.state != "ACTIVE" }.thenBy { it.name.lowercase() }.thenBy { it.reference }), key = { it.id }) { template ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (role in setOf(TeamRole.MEMBER, TeamRole.COORDINATOR)) Checkbox(template.id in selected, { checked -> selected = if (checked) selected + template.id else selected - template.id }, Modifier.testTag("template-select-${template.id}"))
                ServiceLoopEntityRecord("${template.reference} · ${template.name} (v${template.revisionNumber})", metadata = "${template.itemCount} items", status = template.state, modifier = Modifier.weight(1f)) { nav.navigate("template/${template.id}") }
            }
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
                    first = { ServiceLoopDestructiveButton("Delete", { if (planReferenceCount > 0) showDeleteBlocked = true else showDeleteConfirmation = true }, Modifier.fillMaxWidth(), enabled = viewModel != null) },
                    second = { ServiceLoopSecondaryButton("Clone", { nav.navigate("template/new?cloneFrom=${detail.id}") }, Modifier.fillMaxWidth()) },
                )
                ServiceLoopResponsivePair(
                    first = { ServiceLoopSecondaryButton(if (detail.state == "DISABLED") "Enable" else "Disable", { viewModel?.setTemplateState(detail.id, if (detail.state == "DISABLED") "ACTIVE" else "DISABLED") }, Modifier.fillMaxWidth(), enabled = viewModel != null) },
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
                onClick = { nav.navigate("template/edit/${detail.id}?focusItem=$index") },
                modifier = Modifier.testTag("template-detail-item-$index"),
            )
        }
        item {
            ServiceLoopDenseNavigableRow(
                title = "Version history (${versions.size.coerceAtLeast(1)})",
                context = "Read-only revisions",
                onClick = { nav.navigate("template/history/${detail.id}") },
                modifier = Modifier.testTag("template-version-history"),
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
    Card(Modifier.fillMaxWidth().testTag("new-template-type-$type").clickable(onClick = onSelected).semantics { this.role = Role.RadioButton; this.selected = selected }) {
        Column(Modifier.padding(ServiceLoopUiTokens.Space.md), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
            Text(type, style = MaterialTheme.typography.labelLarge, color = if (selected) LocalServiceLoopTokens.current.action else LocalServiceLoopTokens.current.textSecondary)
            Text(description, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
        }
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
            )
            if (expanded) {
                DailyField(item.label, { onChange(item.copy(label = it)) }, "Item label")
                ServiceLoopFilterSelector("Response type", item.responseType, listOf("STATUS", "TEXT", "NUMBER").map { it to it }, { onChange(item.copy(responseType = it)) }, testTag = "template-item-type-$index")
                if (item.responseType == "NUMBER") DailyField(item.unit, { onChange(item.copy(unit = it)) }, "Unit")
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(item.required, { onChange(item.copy(required = it)) }); Text("Required") }
                ServiceLoopLongTextEditor(item.privateGuidance, { onChange(item.copy(privateGuidance = it)) }, "Private technician guidance", true, fieldTestTag = "template-item-guidance-$index")
                ServiceLoopResponsivePair(
                    first = { TextButton(onMoveUp, Modifier.fillMaxWidth(), enabled = index > 0) { Text("Move up") } },
                    second = { TextButton(onMoveDown, Modifier.fillMaxWidth(), enabled = index < lastIndex) { Text("Move down") } },
                )
                ServiceLoopResponsivePair(
                    first = { TextButton(onToggle, Modifier.fillMaxWidth()) { Text("Edit") } },
                    second = { TextButton(onRemove, Modifier.fillMaxWidth()) { Text("Remove") } },
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
            nav.previousBackStackEntry?.savedStateHandle?.set("created-inspection-template-id", id)
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
                onMoveUp = { if (index > 0) draftItems = draftItems.toMutableList().also { it[index] = it[index - 1].also { previous -> it[index - 1] = it[index] } } },
                onMoveDown = { if (index < draftItems.lastIndex) draftItems = draftItems.toMutableList().also { it[index] = it[index + 1].also { next -> it[index + 1] = it[index] } } },
                onRemove = { draftItems = draftItems.filterIndexed { itemIndex, _ -> itemIndex != index }; expandedIndex = null },
            )
        }
        item {
            Text("Add new item", style = MaterialTheme.typography.titleMedium)
            DailyField(label, { label = it }, "Item label")
            Column(verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)) {
                TemplateTypeChoice("STATUS", type == "STATUS", "Record whether the check is satisfactory, needs attention, or cannot be completed.") { type = "STATUS" }
                TemplateTypeChoice("TEXT", type == "TEXT", "Record a written observation, note, or result.") { type = "TEXT" }
                TemplateTypeChoice("NUMBER", type == "NUMBER", "Record a measured value, with a unit when needed.") { type = "NUMBER" }
            }
            if (type == "NUMBER") DailyField(unit, { unit = it }, "Unit")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(required, { required = it }); Text("Required") }
            ServiceLoopLongTextEditor(guidance, { guidance = it }, "Private technician guidance", true, fieldTestTag = "new-template-private-guidance")
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
