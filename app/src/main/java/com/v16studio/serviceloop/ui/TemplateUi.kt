package com.v16studio.serviceloop.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopLongTextEditor
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopOutlinedButtonAdapter as OutlinedButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
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
                Text("Share reusable inspection definitions only. A work package still carries the immutable snapshot used by its Visit.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton({ open.launch(arrayOf(INSPECTION_TEMPLATES_MIME, "application/json")) }, Modifier.weight(1f).testTag("import-inspection-templates-button")) { Text("Import templates") }
                    OutlinedButton({ scope.launch { runCatching { val export = withContext(Dispatchers.IO) { exchange.export(selected) }; val bytes = withContext(Dispatchers.IO) { InspectionTemplateCodec.encode(export) }; shareFile(context, "inspection-templates", "serviceloop-inspection-templates-${System.currentTimeMillis()}.slinsp", INSPECTION_TEMPLATES_MIME, bytes, "Share inspection templates", "ServiceLoop inspection templates", "ServiceLoop inspection templates\nGenerated with ServiceLoop") }.onFailure { message = it.message } } }, enabled = selected.isNotEmpty(), modifier = Modifier.weight(1f).testTag("export-inspection-templates")) { Text("Export selected") }
                }
            }
            message?.let { Text(it, color = if (it.startsWith("Imported")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.testTag("inspection-template-message")) }
        }
        if (values.isEmpty()) item { Text("No reusable templates") }
        items(values, key = { it.id }) { template ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (role in setOf(TeamRole.MEMBER, TeamRole.COORDINATOR)) Checkbox(template.id in selected, { checked -> selected = if (checked) selected + template.id else selected - template.id }, Modifier.testTag("template-select-${template.id}"))
                ServiceLoopEntityRecord("${template.reference} · ${template.name}", metadata = "Revision ${template.revisionNumber} · ${template.itemCount} items", status = template.state, modifier = Modifier.weight(1f)) { nav.navigate("template/${template.id}") }
            }
        }
    }
}

@Composable
internal fun TemplateDetailScreen(detail: TemplateDetail?, padding: PaddingValues, nav: NavHostController) {
    if (detail == null) return DailyEmpty(padding, "Reading template")
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Text("${detail.reference} · ${detail.name}", style = MaterialTheme.typography.headlineSmall); Text("Revision ${detail.revisionNumber} · ${detail.state}"); Text("Changing this template publishes a new revision. Existing work keeps its captured snapshot."); Button({ nav.navigate("template/edit/${detail.id}") }, Modifier.fillMaxWidth()) { Text("New revision") } }; items(detail.items.withIndex().toList()) { (index,item) -> Text("${index+1}. ${item.label} · ${item.responseType} · ${if (item.required) "Required" else "Optional"}${item.unit.ifBlank { "" }.let { if (it.isBlank()) "" else " · $it" }}") } }
}

@Composable
internal fun TemplateEditorScreen(existing: TemplateDetail?, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }; var draftItems by remember(existing?.id) { mutableStateOf(existing?.items ?: emptyList()) }; var label by rememberSaveable { mutableStateOf("") }; var type by rememberSaveable { mutableStateOf("STATUS") }; var unit by rememberSaveable { mutableStateOf("") }; var required by rememberSaveable { mutableStateOf(true) }; var guidance by rememberSaveable { mutableStateOf("") }
    UnsavedChangesGuard(name!=existing?.name.orEmpty()||draftItems!=(existing?.items?:emptyList<TemplateItemDraft>())||label.isNotBlank()||unit.isNotBlank()||guidance.isNotBlank(),nav)
    EditorColumn(padding, state) {
        item { DailyHeading(if (existing == null) "Create reusable template" else "Publish revision ${existing.revisionNumber + 1}"); DailyField(name, { name = it }, "Template name · Required") }
        items(draftItems.withIndex().toList()) { (index,item) -> Card { Column(Modifier.padding(12.dp).fillMaxWidth()) { Text("${index+1}. ${item.label} · ${item.responseType}"); Row { TextButton({ val copy=draftItems.toMutableList(); copy[index]=copy[index-1].also{copy[index-1]=copy[index]}; draftItems=copy },enabled=index>0){Text("Move up")}; TextButton({ val copy=draftItems.toMutableList(); copy[index]=copy[index+1].also{copy[index+1]=copy[index]}; draftItems=copy },enabled=index<draftItems.lastIndex){Text("Move down")}; TextButton({ draftItems = draftItems.filterIndexed { i,_ -> i != index } }) { Text("Remove") } } } } }
        item { Text("Add item", fontWeight = FontWeight.Bold); DailyField(label, { label = it }, "Item label"); ServiceLoopChoiceGroup(listOf("STATUS","TEXT","NUMBER").map{it to it},type,{type=it}); if (type == "NUMBER") DailyField(unit, { unit = it }, "Unit"); Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(required, { required = it }); Text("Required response") }; LongTextEditor(guidance, { guidance = it }, "Private technician guidance", true); OutlinedButton({ draftItems = draftItems + TemplateItemDraft(label, type, unit, required, guidance); label=""; unit=""; guidance="" }, enabled = label.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Add item") }
        }
        item { Button({ if (existing == null) viewModel.createTemplate(name, draftItems) { nav.navigate("template/$it") { popUpTo("template/new") { inclusive = true } } } else viewModel.reviseTemplate(existing.id, name, draftItems) { nav.popBackStack() } }, enabled = name.isNotBlank() && draftItems.isNotEmpty() && !state.operationInProgress, modifier = Modifier.fillMaxWidth()) { Text(if (existing == null) "Save template" else "Publish new revision") } }
    }
}
