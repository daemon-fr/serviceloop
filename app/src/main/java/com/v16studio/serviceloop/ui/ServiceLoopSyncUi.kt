package com.v16studio.serviceloop.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.ServiceLoopApplication
import com.v16studio.serviceloop.data.DispatchPackageService
import com.v16studio.serviceloop.data.DispatchVisitClassification
import com.v16studio.serviceloop.data.DispatchPreview
import com.v16studio.serviceloop.data.InspectionTemplateExchangeService
import com.v16studio.serviceloop.data.InspectionTemplateImportClassification
import com.v16studio.serviceloop.data.InspectionTemplateImportPreview
import com.v16studio.serviceloop.data.SERVICE_LOOP_SYNC_MIME
import com.v16studio.serviceloop.data.ServiceLoopSyncCodec
import com.v16studio.serviceloop.data.ServiceLoopSyncEnvelope
import com.v16studio.serviceloop.data.ServiceLoopSyncEnvelopeCodec
import com.v16studio.serviceloop.data.ServiceLoopSyncPackage
import com.v16studio.serviceloop.data.SyncContentFamily
import com.v16studio.serviceloop.data.count
import com.v16studio.serviceloop.data.filterFullWorkspacePackage
import com.v16studio.serviceloop.data.normalizeSyncContentSelection
import com.v16studio.serviceloop.data.removeSyncContentFamily
import com.v16studio.serviceloop.domain.TemplateSummary
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopActionStack
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDenseNavigableRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNotice
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNoticeKind
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopOutlinedButtonAdapter as OutlinedButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSelectionOption
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextButtonAdapter as TextButton
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

private data class DecodedImport(val envelope: ServiceLoopSyncEnvelope, val fullWorkspace: ServiceLoopSyncPackage? = null, val workPreview: DispatchPreview? = null, val templatePreview: InspectionTemplateImportPreview? = null)

@Composable
internal fun ServiceLoopSyncScreen(state: UiState, padding: PaddingValues, viewModel: ServiceLoopViewModel, nav: NavHostController, incomingUri: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val capabilities = LocalWorkspaceCapabilities.current
    val dispatch = remember { DispatchPackageService((context.applicationContext as ServiceLoopApplication).container.database) }
    val templates = remember { InspectionTemplateExchangeService((context.applicationContext as ServiceLoopApplication).container.database) }
    var decoded by remember { mutableStateOf<DecodedImport?>(null) }
    var selectedFamilies by remember { mutableStateOf<Set<SyncContentFamily>>(emptySet()) }
    var workSelected by remember { mutableStateOf(true) }
    var templateSelected by remember { mutableStateOf(true) }
    var createSeparate by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun read(uri: Uri) {
        scope.launch {
            busy = true; error = null; message = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBounded(ServiceLoopSyncEnvelopeCodec.MAX_PACKAGE_BYTES) } ?: error("Unable to read the selected ServiceLoop file")
                    val envelope = ServiceLoopSyncEnvelopeCodec.decode(bytes)
                    when (envelope.manifest.purpose) {
                        "FULL_WORKSPACE" -> DecodedImport(envelope, fullWorkspace = ServiceLoopSyncCodec.decode(bytes))
                        "WORK_ASSIGNMENT" -> DecodedImport(envelope, workPreview = dispatch.preview(ServiceLoopSyncEnvelopeCodec.unwrapWorkAssignment(bytes)))
                        "TEMPLATE_SHARE" -> DecodedImport(envelope, templatePreview = templates.preview(envelope.section("inspections")))
                        else -> error("Unsupported ServiceLoop file purpose")
                    }
                }
            }.onSuccess { result ->
                decoded = result
                result.fullWorkspace?.let { value -> selectedFamilies = SyncContentFamily.entries.filter { it.count(value) > 0 }.toSet() }
                workSelected = true; templateSelected = true; createSeparate = emptySet()
            }.onFailure { failure -> if (failure is CancellationException) throw failure else error = failure.message ?: "Could not read this ServiceLoop file" }
            busy = false
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::read) }
    LaunchedEffect(incomingUri) { incomingUri?.let { read(Uri.parse(it)) } }

    LazyColumn(Modifier.padding(padding).testTag("service-loop-import"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Import shared data", style = MaterialTheme.typography.headlineSmall)
                Text("Import ServiceLoop data shared from another workspace.")
                if (state.restrictedRecoveryState) ServiceLoopNotice("Recovery is restricted", "Resolve recovery before importing ordinary data.", ServiceLoopNoticeKind.Error)
                error?.let { ServiceLoopNotice("Could not read this ServiceLoop file", it.take(240), ServiceLoopNoticeKind.Error) }
                message?.let { ServiceLoopNotice("Import complete", it, ServiceLoopNoticeKind.Success) }
            }
        }
        item {
            ServiceLoopActionStack { OutlinedButton({ open.launch(arrayOf(SERVICE_LOOP_SYNC_MIME, "application/zip", "application/octet-stream", "*/*")) }, Modifier.fillMaxWidth().testTag("choose-serviceloop-file"), enabled = !busy && !state.restrictedRecoveryState) { ServiceLoopIcon(ServiceLoopIcons.ArrowCircleDown, null, Modifier.padding(end = 8.dp)); Text("Choose ServiceLoop file") } }
        }
        if (capabilities.canManageRegister) item {
            ServiceLoopDenseNavigableRow("Import customers/sites/equipment from CSV", context = "For directory data from spreadsheets or external systems.", leadingIcon = ServiceLoopIcons.ArrowCircleDown, modifier = Modifier.testTag("import-directory-csv"), onClick = { nav.navigate("csv/import") })
        }
        decoded?.let { current ->
            item { Text(current.envelope.manifest.title, style = MaterialTheme.typography.titleLarge); Text("${current.envelope.manifest.purpose.replace('_', ' ')} · generated ${current.envelope.manifest.generatedAt}") }
            current.fullWorkspace?.let { value ->
                item {
                    Text("Contents", style = MaterialTheme.typography.titleMedium)
                    Text("Choose which contents from this file should be included in the new replacement workspace. Unchecked content from this file will not be imported; it does not preserve the current local family.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SyncContentFamily.entries.filter { it.count(value) > 0 }.forEach { family ->
                        val checked = family in selectedFamilies
                        Row(Modifier.fillMaxWidth().testTag("sync-content-family-${family.name.lowercase()}"), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, { next -> selectedFamilies = if (next) normalizeSyncContentSelection(value, selectedFamilies + family) else removeSyncContentFamily(value, selectedFamilies, family) }, enabled = family != SyncContentFamily.BUSINESS_PROFILE); Text(family.label, Modifier.weight(1f)); Text(family.count(value).toString()) }
                    }
                    if (!capabilities.canManageRegister) ServiceLoopNotice("Unavailable for this Team role", "This full workspace file cannot be imported for the current Team role.", ServiceLoopNoticeKind.Error)
                    Spacer(Modifier.height(8.dp)); ServiceLoopNotice("Full workspace", "This full workspace import replaces current ServiceLoop business data. Unchecked content from this file will not be imported.", ServiceLoopNoticeKind.Warning)
                }
                item { ServiceLoopActionStack { ServiceLoopPrimaryButton("Replace workspace", { viewModel.importServiceLoopSync(filterFullWorkspacePackage(value, selectedFamilies)) { nav.navigateToRoot(RootDestination.HOME) } }, Modifier.fillMaxWidth().testTag("sync-full-workspace-replace"), enabled = capabilities.canManageRegister && !busy && !state.restrictedRecoveryState, busy = state.operationInProgress); TextButton({ decoded = null }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Cancel") } } }
            }
            current.workPreview?.let { preview ->
                item {
                    Text("Work assignment", style = MaterialTheme.typography.titleMedium)
                    Text("From ${preview.value.senderLabel} · ${preview.value.visits.size} Visits · ${preview.value.visits.sumOf { it.work.size }} tasks")
                    SyncWorkFamilyRow("Customers", preview.value.customers.size, true, false) {}
                    SyncWorkFamilyRow("Sites", preview.value.sites.size, true, false) {}
                    SyncWorkFamilyRow("Equipment", preview.value.equipment.size, true, false) {}
                    SyncWorkFamilyRow("Visits", preview.value.visits.size, workSelected, true) { workSelected = it }
                    Text("The mature Dispatch preview remains authoritative for new, unchanged, update, conflict, and assignment classifications.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!capabilities.canReceiveAssignedWork) ServiceLoopNotice("Unavailable for this Team role", "This ServiceLoop file cannot be imported for the current Team role.", ServiceLoopNoticeKind.Error)
                    if (!preview.canImport) ServiceLoopNotice("Import needs review", if (preview.visits.isNotEmpty() && preview.visits.all { it.classification == DispatchVisitClassification.ALREADY_CURRENT }) "This work assignment is already current." else "This work assignment needs review before it can be imported.", ServiceLoopNoticeKind.Warning)
                }
                item { ServiceLoopActionStack { ServiceLoopPrimaryButton("Import work", { scope.launch { busy = true; runCatching { withContext(Dispatchers.IO) { dispatch.import(preview) } }.onSuccess { result -> message = "Applied ${result.createdVisitIds.size + result.updatedVisitIds.size} Visits without replacing the workspace."; viewModel.loadVisits(); decoded = null }.onFailure { failure -> if (failure is CancellationException) throw failure else error = failure.message }; busy = false } }, Modifier.fillMaxWidth().testTag("sync-work-import"), enabled = workSelected && capabilities.canReceiveAssignedWork && preview.canImport && !busy, busy = busy); TextButton({ decoded = null }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Cancel") } } }
            }
            current.templatePreview?.let { preview ->
                item {
                    Text("Inspection templates", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth().testTag("sync-template-import"), verticalAlignment = Alignment.CenterVertically) { Checkbox(templateSelected, { templateSelected = it }); Text("Inspection templates", Modifier.weight(1f)); Text(preview.entries.size.toString()) }
                    preview.entries.forEach { entry -> Text("${entry.transfer.reference} · ${entry.transfer.name} · ${entry.classification.name.replace('_', ' ').lowercase()}"); if (entry.classification == InspectionTemplateImportClassification.CONFLICT) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(entry.transfer.reference in createSeparate, { checked -> createSeparate = if (checked) createSeparate + entry.transfer.reference else createSeparate - entry.transfer.reference }); Text("Create separate") } }
                    if (!capabilities.canExchangeTemplates) ServiceLoopNotice("Unavailable for this Team role", "This ServiceLoop file cannot be imported for the current Team role.", ServiceLoopNoticeKind.Error)
                }
                item { ServiceLoopActionStack { ServiceLoopPrimaryButton("Import templates", { scope.launch { busy = true; runCatching { withContext(Dispatchers.IO) { templates.import(preview, createSeparate) } }.onSuccess { result -> message = "Imported ${result.importedReferences.size} inspection templates. Exact matches were left unchanged."; viewModel.loadTemplates(); decoded = null }.onFailure { failure -> if (failure is CancellationException) throw failure else error = failure.message }; busy = false } }, Modifier.fillMaxWidth().testTag("sync-template-import-confirm"), enabled = templateSelected && capabilities.canExchangeTemplates && preview.canImport(createSeparate) && !busy, busy = busy); TextButton({ decoded = null }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Cancel") } } }
            }
        }
    }
}

@Composable
private fun SyncWorkFamilyRow(label: String, count: Int, checked: Boolean, enabled: Boolean, onChecked: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onChecked, enabled = enabled); Text(label, Modifier.weight(1f)); Text(count.toString()) }

@Composable
internal fun DataTransferScreen(padding: PaddingValues, nav: NavHostController) { LazyColumn(Modifier.padding(padding).testTag("settings-data-transfer"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("Import / export data", style = MaterialTheme.typography.headlineSmall); Text("Move ordinary ServiceLoop data. Backups and recovery are managed separately.") }; item { ServiceLoopDenseNavigableRow("Import shared data", context = "Import a ServiceLoop file shared from another workspace.", modifier = Modifier.testTag("data-transfer-import"), leadingIcon = ServiceLoopIcons.ArrowCircleDown, onClick = { nav.navigate("import") }) }; item { ServiceLoopDenseNavigableRow("Export / share data", context = "Share inspection templates or export readable CSV.", modifier = Modifier.testTag("data-transfer-export"), leadingIcon = ServiceLoopIcons.ArrowCircleUp, onClick = { nav.navigate("data-transfer/export") }) }; item { ServiceLoopDenseNavigableRow("Verify ServiceLoop file", context = "Check a ServiceLoop file without changing local data.", modifier = Modifier.testTag("data-transfer-verify"), leadingIcon = ServiceLoopIcons.CheckCircle, onClick = { nav.navigate("data-transfer/verify") }) } } }

@Composable
internal fun DataExportScreen(state: UiState, padding: PaddingValues, nav: NavHostController) { LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("Export / share data", style = MaterialTheme.typography.headlineSmall) }; item { ServiceLoopDenseNavigableRow("Share inspection templates", context = "Create a ServiceLoop file with reusable inspection templates.", leadingIcon = ServiceLoopIcons.CheckCircle, onClick = { nav.navigate("data-transfer/templates") }) }; item { ServiceLoopDenseNavigableRow("Export readable CSV", context = "For spreadsheets and external systems.", leadingIcon = ServiceLoopIcons.ArrowCircleUp, onClick = { nav.navigate("csv/export") }) }; item { Text("Work assignments are shared from Dispatch Outbox. Full backups are created in Backup and recovery.", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }

@Composable
internal fun TemplateExportScreen(values: List<TemplateSummary>, padding: PaddingValues, nav: NavHostController) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val exchange = remember { InspectionTemplateExchangeService((context.applicationContext as ServiceLoopApplication).container.database) }; var selected by remember { mutableStateOf<Set<String>>(emptySet()) }; var message by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.padding(padding).testTag("export-inspection-templates"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Text("Export inspection templates", style = MaterialTheme.typography.headlineSmall); Text("Select reusable templates to share as a ServiceLoop file.") }; items(values.filter { it.state != "DELETED" }, key = { it.id }) { template -> ServiceLoopSelectionOption(template.id in selected, { selected = if (template.id in selected) selected - template.id else selected + template.id }, "${template.reference} · ${template.name} (v${template.revisionNumber}) · ${template.itemCount} items", Modifier.testTag("export-template-${template.id}")) }; item { ServiceLoopPrimaryButton("Share inspection templates", { scope.launch { runCatching { val transfer = withContext(Dispatchers.IO) { exchange.export(selected) }; val bytes = withContext(Dispatchers.IO) { ServiceLoopSyncEnvelopeCodec.wrapTemplateShare(transfer) }; shareFile(context, "inspection-templates", "serviceloop-inspection-templates-${System.currentTimeMillis()}.slsync", SERVICE_LOOP_SYNC_MIME, bytes, "Share inspection templates", "ServiceLoop inspection templates", "ServiceLoop inspection templates\nGenerated with ServiceLoop") }.onSuccess { message = "ServiceLoop file ready to share." }.onFailure { message = it.message } } }, Modifier.fillMaxWidth(), enabled = selected.isNotEmpty()); message?.let { Text(it, color = MaterialTheme.colorScheme.primary) } } }
}

@Composable
internal fun ServiceLoopFileVerifyScreen(padding: PaddingValues) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); var result by remember { mutableStateOf<String?>(null) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { scope.launch { result = runCatching { withContext(Dispatchers.IO) { val bytes = context.contentResolver.openInputStream(it)?.use { input -> input.readBounded(ServiceLoopSyncEnvelopeCodec.MAX_PACKAGE_BYTES) } ?: error("Could not read this ServiceLoop file"); val envelope = ServiceLoopSyncEnvelopeCodec.decode(bytes); when (envelope.manifest.purpose) { "FULL_WORKSPACE" -> ServiceLoopSyncCodec.decode(bytes); "WORK_ASSIGNMENT" -> ServiceLoopSyncEnvelopeCodec.unwrapWorkAssignment(bytes); "TEMPLATE_SHARE" -> ServiceLoopSyncEnvelopeCodec.unwrapTemplateShare(bytes); else -> error("Unsupported purpose") }; "ServiceLoop file is readable and supported.\n${envelope.manifest.title}\n${envelope.manifest.purpose}\n${envelope.manifest.generatedAt}" } }.getOrElse { "Could not verify this ServiceLoop file. ${it.message.orEmpty()}" } } } }
    LazyColumn(Modifier.padding(padding).testTag("data-transfer-verify"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("Verify ServiceLoop file", style = MaterialTheme.typography.headlineSmall); Text("This checks readability and support without writing business data."); OutlinedButton({ open.launch(arrayOf(SERVICE_LOOP_SYNC_MIME, "application/zip", "application/octet-stream", "*/*")) }, Modifier.fillMaxWidth()) { ServiceLoopIcon(ServiceLoopIcons.CheckCircle, null, Modifier.padding(end = 8.dp)); Text("Choose ServiceLoop file") }; result?.let { Text(it, color = if (it.startsWith("ServiceLoop file")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) } } }
}

private suspend fun InputStream.readBounded(limit: Int): ByteArray { val output = ByteArrayOutputStream(); val buffer = ByteArray(8192); while (true) { val count = read(buffer); if (count < 0) break; require(output.size() + count <= limit) { "Selected ServiceLoop file is too large" }; output.write(buffer, 0, count) }; return output.toByteArray() }
