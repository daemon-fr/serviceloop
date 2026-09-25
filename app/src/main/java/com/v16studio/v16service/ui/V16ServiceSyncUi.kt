package com.v16studio.v16service.ui

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
import com.v16studio.v16service.ui.designsystem.V16ServiceCheckbox as Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import com.v16studio.v16service.V16ServiceApplication
import com.v16studio.v16service.data.DispatchPackageService
import com.v16studio.v16service.data.DispatchVisitClassification
import com.v16studio.v16service.data.DispatchPreview
import com.v16studio.v16service.data.InspectionTemplateExchangeService
import com.v16studio.v16service.data.InspectionTemplateImportClassification
import com.v16studio.v16service.data.InspectionTemplateImportPreview
import com.v16studio.v16service.data.DataTransferImportPreview
import com.v16studio.v16service.data.DataTransferImportService
import com.v16studio.v16service.data.DataTransferFamily
import com.v16studio.v16service.data.DataTransferClassification
import com.v16studio.v16service.data.DataTransferCodec
import com.v16studio.v16service.data.V16_SERVICE_SYNC_MIME
import com.v16studio.v16service.data.V16ServiceSyncCodec
import com.v16studio.v16service.data.V16ServiceSyncEnvelope
import com.v16studio.v16service.data.V16ServiceSyncEnvelopeCodec
import com.v16studio.v16service.data.V16ServiceSyncPackage
import com.v16studio.v16service.data.V16ServicePeerTrustStore
import com.v16studio.v16service.data.V16ServiceTrustDecision
import com.v16studio.v16service.data.V16ServiceSourceTrust
import com.v16studio.v16service.data.WorkResultImportService
import com.v16studio.v16service.data.SyncContentFamily
import com.v16studio.v16service.data.count
import com.v16studio.v16service.data.filterFullWorkspacePackage
import com.v16studio.v16service.data.normalizeSyncContentSelection
import com.v16studio.v16service.data.removeSyncContentFamily
import com.v16studio.v16service.domain.TemplateSummary
import com.v16studio.v16service.ui.designsystem.V16ServiceActionStack
import com.v16studio.v16service.ui.designsystem.V16ServiceDenseNavigableRow
import com.v16studio.v16service.ui.designsystem.V16ServiceNotice
import com.v16studio.v16service.ui.designsystem.V16ServiceNoticeKind
import com.v16studio.v16service.ui.designsystem.V16ServiceOutlinedButtonAdapter as OutlinedButton
import com.v16studio.v16service.ui.designsystem.V16ServicePrimaryButton
import com.v16studio.v16service.ui.designsystem.V16ServiceSelectionOption
import com.v16studio.v16service.ui.designsystem.V16ServiceTextButtonAdapter as TextButton
import com.v16studio.v16service.ui.icons.V16ServiceIcon
import com.v16studio.v16service.ui.icons.V16ServiceIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

private data class DecodedImport(val envelope: V16ServiceSyncEnvelope, val trust: V16ServiceTrustDecision, val fullWorkspace: V16ServiceSyncPackage? = null, val workPreview: DispatchPreview? = null, val templatePreview: InspectionTemplateImportPreview? = null, val workResultPreview: WorkResultImportService.Preview? = null, val workResultBytes: ByteArray? = null, val dataTransferPreview: DataTransferImportPreview? = null, val dataTransferBytes: ByteArray? = null)

internal fun workResultCommitMessage(result: WorkResultImportService.Preview): String {
    val applied = result.items.count { it.status != "ALREADY_RECEIVED" && it.committedStatus == "APPLIED" }
    val stale = result.items.count { it.status != "ALREADY_RECEIVED" && it.committedStatus == "STALE" }
    val conflicts = result.items.count { it.status != "ALREADY_RECEIVED" && it.committedStatus == "CONFLICT" }
    val repeated = result.items.count { it.status == "ALREADY_RECEIVED" }
    val advanced = result.items.count { it.recurrenceAppliedNow }
    val summary = "Applied $applied; stale $stale; conflicts $conflicts; already received $repeated; service plans advanced $advanced."
    val reasons = result.items.filter { it.committedStatus in setOf("STALE", "CONFLICT") }
        .mapNotNull { item -> item.reason?.let { "${item.dispatchItemId}: $it" } }
    return if (reasons.isEmpty()) summary else "$summary ${reasons.joinToString(" ")}"
}

@Composable
internal fun V16ServiceSyncScreen(state: UiState, padding: PaddingValues, viewModel: V16ServiceViewModel, nav: NavHostController, incomingUri: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val capabilities = LocalWorkspaceCapabilities.current
    val database = remember { (context.applicationContext as V16ServiceApplication).container.database }
    val dispatch = remember { DispatchPackageService(database) }
    val templates = remember { InspectionTemplateExchangeService(database) }
    val trustStore = remember { V16ServicePeerTrustStore(database) }
    val workResults = remember { WorkResultImportService(database, context.filesDir) }
    val dataTransfers = remember { DataTransferImportService(database, context.filesDir) }
    var decoded by remember { mutableStateOf<DecodedImport?>(null) }
    var selectedFamilies by remember { mutableStateOf<Set<SyncContentFamily>>(emptySet()) }
    var workSelected by remember { mutableStateOf(true) }
    var templateSelected by remember { mutableStateOf(true) }
    var createSeparate by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<ImportErrorPresentation?>(null) }
    var trustedName by remember { mutableStateOf("") }
    var confirmTrust by remember { mutableStateOf(false) }

    fun read(uri: Uri) {
        scope.launch {
            busy = true; error = null; message = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readSyncPackageBounded(V16ServiceSyncEnvelopeCodec.MAX_PACKAGE_BYTES) } ?: error("Unable to read the selected V16 Service file")
                    val envelope = V16ServiceSyncEnvelopeCodec.decode(bytes)
                    val trust = trustStore.assess(envelope.manifest.exporterId)
                    when (envelope.manifest.purpose) {
                        "FULL_WORKSPACE" -> DecodedImport(envelope, trust, fullWorkspace = V16ServiceSyncCodec.decode(bytes))
                        "WORK_ASSIGNMENT" -> DecodedImport(envelope, trust, workPreview = dispatch.preview(V16ServiceSyncEnvelopeCodec.unwrapWorkAssignment(bytes)))
                        "DATA_TRANSFER" -> {
                            val transfer = DataTransferCodec.decode(bytes)
                            if (transfer.families.keys == setOf(DataTransferFamily.INSPECTION_TEMPLATES)) {
                                val inspectionBytes = transfer.families.getValue(DataTransferFamily.INSPECTION_TEMPLATES)
                                DecodedImport(envelope, trust, templatePreview = templates.preview(inspectionBytes))
                            } else {
                                val preview = dataTransfers.preview(bytes)
                                DecodedImport(envelope, trust, dataTransferPreview = preview, dataTransferBytes = bytes)
                            }
                        }
                        "WORK_RESULT" -> DecodedImport(envelope, trust, workResultPreview = workResults.preview(bytes), workResultBytes = bytes)
                        else -> error("Unsupported V16 Service file purpose")
                    }
                }
            }.onSuccess { result ->
                decoded = result
                trustedName = result.trust.friendlyName.orEmpty()
                result.fullWorkspace?.let { value -> selectedFamilies = SyncContentFamily.entries.filter { it.count(value) > 0 }.toSet() }
                workSelected = true; templateSelected = true; createSeparate = emptySet()
            }.onFailure { failure -> if (failure is CancellationException) throw failure else error = importErrorPresentation(failure, ImportRouteKind.SHARED_DATA) }
            busy = false
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(::read) }
    LaunchedEffect(incomingUri) { incomingUri?.let { read(Uri.parse(it)) } }

    LazyColumn(Modifier.padding(padding).testTag("v16-service-import"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Import shared data", style = MaterialTheme.typography.headlineSmall)
                Text("Import V16 Service data shared from another workspace.")
                if (state.restrictedRecoveryState) V16ServiceNotice("Recovery is restricted", "Resolve recovery before importing ordinary data.", V16ServiceNoticeKind.Error)
                message?.let { V16ServiceNotice("Import complete", it, V16ServiceNoticeKind.Success) }
            }
        }
        item {
            V16ServiceActionStack { OutlinedButton({ open.launch("*/*") }, Modifier.fillMaxWidth().testTag("choose-v16service-file"), enabled = !busy && !state.restrictedRecoveryState) { V16ServiceIcon(V16ServiceIcons.ArrowCircleDown, null, Modifier.padding(end = 8.dp)); Text("Choose V16 Service file") } }
        }
        if (capabilities.canManageRegister) item {
            V16ServiceDenseNavigableRow("Import customers/sites/equipment from CSV", context = "For directory data from spreadsheets or external systems.", leadingIcon = V16ServiceIcons.ArrowCircleDown, modifier = Modifier.testTag("import-directory-csv"), onClick = { nav.navigate("csv/import") })
        }
        decoded?.let { current ->
            item { Text(current.envelope.manifest.title, style = MaterialTheme.typography.titleLarge); Text("${current.envelope.manifest.purpose.replace('_', ' ')} · generated ${current.envelope.manifest.generatedAt}") }
            item {
                Text("Source", style = MaterialTheme.typography.titleMedium)
                when (current.trust.trust) {
                    V16ServiceSourceTrust.TRUSTED -> {
                        Text(current.trust.friendlyName ?: current.trust.sourceId.orEmpty())
                        Text(current.trust.sourceId.orEmpty())
                        Text("Trusted", color = MaterialTheme.colorScheme.primary)
                    }
                    V16ServiceSourceTrust.NOT_TRUSTED -> {
                        Text(current.trust.sourceId.orEmpty())
                        Text("Not trusted", color = MaterialTheme.colorScheme.error)
                        OutlinedTextField(trustedName, { trustedName = it }, label = { Text("Friendly name") }, modifier = Modifier.fillMaxWidth().testTag("sync-trusted-source-name"), singleLine = true)
                        OutlinedButton({ confirmTrust = true }, Modifier.fillMaxWidth().testTag("sync-add-trusted-source"), enabled = trustedName.trim().isNotEmpty() && !busy) { Text("Add to trusted IDs") }
                    }
                }
            }
            if (confirmTrust) item {
                AlertDialog(
                    onDismissRequest = { confirmTrust = false },
                    title = { Text("Trust this V16 Service ID?") },
                    text = { Text("${trustedName.trim()} will be able to send files that can be imported on this device. This identifies the source within V16 Service; it does not verify who possesses the ID.") },
                    confirmButton = {
                        TextButton({
                            scope.launch {
                                runCatching { trustStore.add(current.trust.sourceId.orEmpty(), trustedName) }
                                    .onSuccess { trust ->
                                        decoded = current.copy(trust = V16ServiceTrustDecision(trust.peerId, trust.name, V16ServiceSourceTrust.TRUSTED))
                                        confirmTrust = false
                                    }
                                    .onFailure { failure -> error = ImportErrorPresentation("Could not add trusted ID", failure.message ?: "Check the ID and try again.") }
                            }
                        }, Modifier.testTag("sync-confirm-trust-source")) { Text("Trust ID") }
                    },
                    dismissButton = { TextButton({ confirmTrust = false }) { Text("Cancel") } },
                )
            }
            current.fullWorkspace?.let { value ->
                item {
                    Text("Contents", style = MaterialTheme.typography.titleMedium)
                    Text("Choose which contents from this file should be included in the new replacement workspace. Unchecked content from this file will not be imported; it does not preserve the current local family.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SyncContentFamily.entries.filter { it.count(value) > 0 }.forEach { family ->
                        val checked = family in selectedFamilies
                        Row(Modifier.fillMaxWidth().testTag("sync-content-family-${family.name.lowercase()}"), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, { next -> selectedFamilies = if (next) normalizeSyncContentSelection(value, selectedFamilies + family) else removeSyncContentFamily(value, selectedFamilies, family) }, enabled = family != SyncContentFamily.BUSINESS_PROFILE); Text(family.label, Modifier.weight(1f)); Text(family.count(value).toString()) }
                    }
                    if (!capabilities.canManageRegister) V16ServiceNotice("Unavailable for this Team role", "This full workspace file cannot be imported for the current Team role.", V16ServiceNoticeKind.Error)
                    Spacer(Modifier.height(8.dp)); V16ServiceNotice("Full workspace", "This full workspace import replaces current V16 Service business data. Unchecked content from this file will not be imported.", V16ServiceNoticeKind.Warning)
                }
                item { V16ServiceActionStack { V16ServicePrimaryButton("Replace workspace", { viewModel.importV16ServiceSync(filterFullWorkspacePackage(value, selectedFamilies)) { nav.navigateToRoot(RootDestination.HOME) } }, Modifier.fillMaxWidth().testTag("sync-full-workspace-replace"), enabled = current.trust.canImport && capabilities.canManageRegister && !busy && !state.restrictedRecoveryState, busy = state.operationInProgress); TextButton({ decoded = null }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Cancel") } } }
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
                    if (!capabilities.canReceiveAssignedWork) V16ServiceNotice("Unavailable for this Team role", "This V16 Service file cannot be imported for the current Team role.", V16ServiceNoticeKind.Error)
                    if (!preview.canImport) V16ServiceNotice("Import needs review", if (preview.visits.isNotEmpty() && preview.visits.all { it.classification == DispatchVisitClassification.ALREADY_CURRENT }) "This work assignment is already current." else "This work assignment needs review before it can be imported.", V16ServiceNoticeKind.Warning)
                }
                item { V16ServiceActionStack { V16ServicePrimaryButton("Import work", { scope.launch { busy = true; runCatching { withContext(Dispatchers.IO) { dispatch.import(preview, current.envelope.manifest.exporterId) } }.onSuccess { result -> message = "Applied ${result.createdVisitIds.size + result.updatedVisitIds.size} Visits without replacing the workspace."; viewModel.loadVisits(); decoded = null }.onFailure { failure -> if (failure is CancellationException) throw failure else error = ImportErrorPresentation("Import could not be completed", failure.message ?: "V16 Service could not finish importing this work assignment. Try again.") }; busy = false } }, Modifier.fillMaxWidth().testTag("sync-work-import"), enabled = current.trust.canImport && workSelected && capabilities.canReceiveAssignedWork && preview.canImport && !busy, busy = busy); TextButton({ decoded = null }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Cancel") } } }
            }
            current.templatePreview?.let { preview ->
                item {
                    Text("Inspection templates", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth().testTag("sync-template-import"), verticalAlignment = Alignment.CenterVertically) { Checkbox(templateSelected, { templateSelected = it }); Text("Inspection templates", Modifier.weight(1f)); Text(preview.entries.size.toString()) }
                    preview.entries.forEach { entry -> Text("${entry.transfer.reference} · ${entry.transfer.name} · ${entry.classification.name.replace('_', ' ').lowercase()}"); if (entry.classification == InspectionTemplateImportClassification.CONFLICT) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(entry.transfer.reference in createSeparate, { checked -> createSeparate = if (checked) createSeparate + entry.transfer.reference else createSeparate - entry.transfer.reference }); Text("Create separate") } }
                    if (!capabilities.canExchangeTemplates) V16ServiceNotice("Unavailable for this Team role", "This V16 Service file cannot be imported for the current Team role.", V16ServiceNoticeKind.Error)
                }
                item { V16ServiceActionStack { V16ServicePrimaryButton("Import templates", { scope.launch { busy = true; runCatching { withContext(Dispatchers.IO) { templates.import(preview, current.envelope.manifest.exporterId, createSeparate) } }.onSuccess { result -> message = "Imported ${result.importedReferences.size} inspection templates. Exact matches were left unchanged."; viewModel.loadTemplates(); decoded = null }.onFailure { failure -> if (failure is CancellationException) throw failure else error = ImportErrorPresentation("Import could not be completed", failure.message ?: "V16 Service could not finish importing these templates. Try again.") }; busy = false } }, Modifier.fillMaxWidth().testTag("sync-template-import-confirm"), enabled = current.trust.canImport && templateSelected && capabilities.canExchangeTemplates && preview.canImport(createSeparate) && !busy, busy = busy); TextButton({ decoded = null }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Cancel") } } }
            }
            current.dataTransferPreview?.let { preview ->
                val includesTemplates = preview.counts[DataTransferFamily.INSPECTION_TEMPLATES]?.let { it > 0 } == true
                val roleAllowed = capabilities.canManageRegister && (!includesTemplates || capabilities.canExchangeTemplates)
                item {
                    Text("Selected data · additive merge", style = MaterialTheme.typography.titleMedium)
                    Text("This copies the selected records into this workspace. Missing records in the file never delete local data. Historical work is read-only context; it does not create Visits, claims, technician execution, or recurrence changes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Package source workspace · ${preview.payload.sourceWorkspaceId}")
                    preview.counts.forEach { (family, count) ->
                        val label = family.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
                        val statuses = preview.items.filter { it.family == family }.groupingBy { it.classification }.eachCount()
                        Text("$label · $count${statuses.entries.joinToString(prefix = if (statuses.isEmpty()) "" else " · ") { "${it.value} ${it.key.name.lowercase().replace('_', ' ')}" }}")
                    }
                    preview.templatePreview?.entries?.filter { it.classification == InspectionTemplateImportClassification.CONFLICT }?.forEach { entry ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(entry.transfer.reference in createSeparate, { checked -> createSeparate = if (checked) createSeparate + entry.transfer.reference else createSeparate - entry.transfer.reference }, enabled = !busy)
                            Text("Create separate template for ${entry.transfer.reference}")
                        }
                    }
                    if (preview.conflicts.isNotEmpty() && preview.conflicts.any { it.family != DataTransferFamily.INSPECTION_TEMPLATES || it.sourceKey !in createSeparate }) {
                        V16ServiceNotice("Review conflicts", "Changed bound records and conflicting references must be resolved before any selected data is imported.", V16ServiceNoticeKind.Error)
                    }
                    if (!roleAllowed) V16ServiceNotice("Unavailable for this Team role", "A role with register access is required; template data also requires template exchange access.", V16ServiceNoticeKind.Error)
                }
                item { V16ServiceActionStack {
                    V16ServicePrimaryButton("Import selected data", { scope.launch {
                        busy = true; error = null
                        runCatching { withContext(Dispatchers.IO) { dataTransfers.import(preview, createSeparate) } }
                            .onSuccess { result ->
                                message = "Imported selected data. ${result.items.count { it.classification == DataTransferClassification.NEW || it.classification == DataTransferClassification.NEW_HISTORY }} new record(s); current matches were left unchanged."
                                decoded = null
                                viewModel.refreshRootDataNonBlocking()
                                viewModel.loadVisits(); viewModel.loadTemplates(); viewModel.loadFollowUps()
                            }
                            .onFailure { failure -> if (failure is CancellationException) throw failure else error = ImportErrorPresentation("Import could not be completed", failure.message ?: "V16 Service could not finish importing the selected data. Try again.") }
                        busy = false
                    } }, Modifier.fillMaxWidth().testTag("sync-data-transfer-import"), enabled = current.trust.canImport && roleAllowed && preview.canImport(createSeparate) && !busy && !state.restrictedRecoveryState, busy = busy)
                    TextButton({ decoded = null }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Cancel") }
                } }
            }
            current.workResultPreview?.let { preview ->
                item {
                    Text("Final work results", style = MaterialTheme.typography.titleMedium)
                    Text("${preview.items.size} finalized service results from ${current.trust.friendlyName ?: preview.exporterId}")
                    preview.items.forEach { result -> Text("${result.dispatchVisitId.take(8)} · ${result.dispatchItemId.take(8)} · ${result.status.replace('_', ' ').lowercase()}") ; result.reason?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
                    Text("Received results retain the documenting technician and assignment provenance. Stale or conflicting work is kept for review without advancing the service plan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!capabilities.canAssignWork) V16ServiceNotice("Unavailable for this Team role", "Only a coordinator can receive work results.", V16ServiceNoticeKind.Error)
                }
                item { V16ServiceActionStack { V16ServicePrimaryButton("Import final results", { scope.launch { busy = true; runCatching { withContext(Dispatchers.IO) { workResults.import(requireNotNull(current.workResultBytes)) } }.onSuccess { result -> message = workResultCommitMessage(result); decoded = null; viewModel.loadVisits() }.onFailure { failure -> if (failure is CancellationException) throw failure else error = ImportErrorPresentation("Import could not be completed", failure.message ?: "V16 Service could not finish importing these results. Try again.") }; busy = false } }, Modifier.fillMaxWidth().testTag("sync-work-result-import"), enabled = current.trust.canImport && capabilities.canAssignWork && !busy && !state.restrictedRecoveryState, busy = busy); TextButton({ decoded = null }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Cancel") } } }
            }
        }
    }
    error?.let { presentation -> ImportErrorDialog(presentation, onDismiss = { error = null }) }
}

@Composable
private fun SyncWorkFamilyRow(label: String, count: Int, checked: Boolean, enabled: Boolean, onChecked: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onChecked, enabled = enabled); Text(label, Modifier.weight(1f)); Text(count.toString()) }

@Composable
internal fun DataTransferScreen(padding: PaddingValues, nav: NavHostController) { LazyColumn(Modifier.padding(padding).testTag("settings-data-transfer"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("Import / export data", style = MaterialTheme.typography.headlineSmall); Text("Move ordinary V16 Service data. Backups and recovery are managed separately.") }; item { V16ServiceDenseNavigableRow("Import shared data", context = "Import a V16 Service file shared from another workspace.", modifier = Modifier.testTag("data-transfer-import"), leadingIcon = V16ServiceIcons.ArrowCircleDown, onClick = { nav.navigate("import") }) }; item { V16ServiceDenseNavigableRow("Export / share data", context = "Share inspection templates or export readable CSV.", modifier = Modifier.testTag("data-transfer-export"), leadingIcon = V16ServiceIcons.ArrowCircleUp, onClick = { nav.navigate("data-transfer/export") }) } } }

@Composable
internal fun DataExportScreen(state: UiState, padding: PaddingValues, nav: NavHostController) { ExportCenterScreen(padding) }

@Composable
internal fun TemplateExportScreen(values: List<TemplateSummary>, padding: PaddingValues, nav: NavHostController) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val database = remember { (context.applicationContext as V16ServiceApplication).container.database }; val exchange = remember(database) { InspectionTemplateExchangeService(database) }; val trustStore = remember(database) { V16ServicePeerTrustStore(database) }; var selected by remember { mutableStateOf<Set<String>>(emptySet()) }; var message by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.padding(padding).testTag("export-inspection-templates"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Text("Export inspection templates", style = MaterialTheme.typography.headlineSmall); Text("Select reusable templates to share as a V16 Service file.") }; items(values.filter { it.state != "DELETED" }, key = { it.id }) { template -> V16ServiceSelectionOption(template.id in selected, { selected = if (template.id in selected) selected - template.id else selected + template.id }, "${template.reference} · ${template.name} (v${template.revisionNumber}) · ${template.itemCount} items", Modifier.testTag("export-template-${template.id}")) }; item { V16ServicePrimaryButton("Share inspection templates", { scope.launch { runCatching { val transfer = withContext(Dispatchers.IO) { exchange.export(selected) }; val exporterId = withContext(Dispatchers.IO) { trustStore.localIdentity().technicianId }; val bytes = withContext(Dispatchers.IO) { V16ServiceSyncEnvelopeCodec.wrapTemplateTransfer(transfer, exporterId) }; shareFile(context, "inspection-templates", "v16service-inspection-templates-${System.currentTimeMillis()}.v16service", V16_SERVICE_SYNC_MIME, bytes, "Share inspection templates", "V16 Service inspection templates", "V16 Service inspection templates\nGenerated with V16 Service") }.onSuccess { message = "V16 Service file ready to share." }.onFailure { message = it.message } } }, Modifier.fillMaxWidth(), enabled = selected.isNotEmpty()); message?.let { Text(it, color = MaterialTheme.colorScheme.primary) } } }
}

@Composable
internal fun V16ServiceFileVerifyScreen(padding: PaddingValues) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val database = remember { (context.applicationContext as V16ServiceApplication).container.database }; val trustStore = remember(database) { V16ServicePeerTrustStore(database) }; var result by remember { mutableStateOf<String?>(null) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { scope.launch { result = runCatching { withContext(Dispatchers.IO) { val bytes = context.contentResolver.openInputStream(it)?.use { input -> input.readSyncPackageBounded(V16ServiceSyncEnvelopeCodec.MAX_PACKAGE_BYTES) } ?: error("Could not read this V16 Service file"); val envelope = V16ServiceSyncEnvelopeCodec.decode(bytes); when (envelope.manifest.purpose) { "FULL_WORKSPACE" -> V16ServiceSyncCodec.decode(bytes); "WORK_ASSIGNMENT" -> V16ServiceSyncEnvelopeCodec.unwrapWorkAssignment(bytes); "DATA_TRANSFER" -> DataTransferCodec.decode(bytes); else -> error("Unsupported purpose") }; val trust = trustStore.assess(envelope.manifest.exporterId); val source = when (trust.trust) { V16ServiceSourceTrust.TRUSTED -> "Trusted · ${trust.friendlyName} · ${trust.sourceId}"; V16ServiceSourceTrust.NOT_TRUSTED -> "Not trusted · ${trust.sourceId}" }; "Structurally valid V16 Service file\n${envelope.manifest.purpose.replace('_', ' ')}\n$source" } }.getOrElse { "Could not verify this V16 Service file. ${it.message.orEmpty()}" } } } }
    LazyColumn(Modifier.padding(padding).testTag("data-transfer-verify"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("Verify V16 Service file", style = MaterialTheme.typography.headlineSmall); Text("This checks readability and support without writing business data."); OutlinedButton({ open.launch(arrayOf(V16_SERVICE_SYNC_MIME, "application/zip", "application/octet-stream", "*/*")) }, Modifier.fillMaxWidth()) { V16ServiceIcon(V16ServiceIcons.CheckCircle, null, Modifier.padding(end = 8.dp)); Text("Choose V16 Service file") }; result?.let { Text(it, color = if (it.startsWith("V16 Service file")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) } } }
}

private suspend fun InputStream.readSyncPackageBounded(limit: Int): ByteArray { val output = ByteArrayOutputStream(); val buffer = ByteArray(8192); while (true) { val count = read(buffer); if (count < 0) break; require(output.size() + count <= limit) { "Selected V16 Service file is too large" }; output.write(buffer, 0, count) }; return output.toByteArray() }
