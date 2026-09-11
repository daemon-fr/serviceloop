package com.v16studio.serviceloop.ui

import com.v16studio.serviceloop.ui.designsystem.ServiceLoopButtonAdapter as Button
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopOutlinedButtonAdapter as OutlinedButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextButtonAdapter as TextButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopIconButtonAdapter as IconButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextFieldAdapter as OutlinedTextField
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopCardAdapter as Card
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopElevatedCardAdapter as ElevatedCard
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChoiceGroup
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFilterSelector
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFilterSelectorRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDenseNavigableRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDestructiveButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNotice
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNoticeKind
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopResponsivePair
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopActionStack
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.v16studio.serviceloop.data.Stage4Service
import com.v16studio.serviceloop.data.RecoveryPackage
import com.v16studio.serviceloop.domain.*
import java.time.LocalDate
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal data class HistoryDateValidation(val from: String?, val to: String?, val fromError: String? = null, val toError: String? = null)

internal fun validateHistoryDates(fromText: String, toText: String): HistoryDateValidation {
    val from = fromText.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val to = toText.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val malformedFrom = fromText.isNotBlank() && from == null
    val malformedTo = toText.isNotBlank() && to == null
    val reversed = !malformedFrom && !malformedTo && from != null && to != null && from.isAfter(to)
    if (malformedFrom || malformedTo || reversed) return HistoryDateValidation(null, null, if (malformedFrom) "Use YYYY-MM-DD" else if (reversed) "From must not be after To" else null, if (malformedTo) "Use YYYY-MM-DD" else if (reversed) "To must not be before From" else null)
    return HistoryDateValidation(from?.toString(), to?.toString())
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun HistoryScreen(scope: HistoryScope, state: UiState, padding: PaddingValues, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    var type by remember { mutableStateOf(HistoryType.ALL) }; var sort by remember { mutableStateOf(HistorySort.EVENT_NEWEST) }
    var from by remember { mutableStateOf("") }; var to by remember { mutableStateOf("") }; var search by remember { mutableStateOf("") }
    val dateValidation = remember(from, to) { validateHistoryDates(from, to) }
    val query = remember(scope, type, sort, dateValidation, search) { HistoryQuery(scope, type, dateValidation.from, dateValidation.to, sort, search) }
    LaunchedEffect(query) { delay(150); viewModel.loadHistory(query) }
    LazyColumn(Modifier.padding(padding).testTag("history-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(scope.label, style = MaterialTheme.typography.headlineSmall); OutlinedTextField(search, { search = it }, label = { Text("Search within history") }, leadingIcon={ServiceLoopIcon(ServiceLoopIcons.Search,null,Modifier.size(24.dp))}, modifier = Modifier.fillMaxWidth()) }
        item { ServiceLoopFilterSelectorRow(first={ServiceLoopFilterSelector("Type",type,listOf(HistoryType.ALL to "All",HistoryType.SERVICE_RECORDS to "Service records",HistoryType.CONTACTS to "Contacts",HistoryType.FOLLOW_UPS to "Follow-ups",HistoryType.CHANGES to "Changes"),{type=it},testTag="history-type")},second={ServiceLoopFilterSelector("Sort",sort,listOf(HistorySort.EVENT_NEWEST to "Event newest",HistorySort.EVENT_OLDEST to "Event oldest",HistorySort.RECORDED_NEWEST to "Recorded newest"),{sort=it},testTag="history-sort")}) }
        item { ServiceLoopResponsivePair(first={OutlinedTextField(from,{from=it},label={Text("From · YYYY-MM-DD")},modifier=Modifier.testTag("history-from"),isError=dateValidation.fromError!=null,supportingText=dateValidation.fromError?.let{message->{Text(message)}})},second={OutlinedTextField(to,{to=it},label={Text("To · YYYY-MM-DD")},modifier=Modifier.testTag("history-to"),isError=dateValidation.toError!=null,supportingText=dateValidation.toError?.let{message->{Text(message)}})});if(from.isNotBlank()||to.isNotBlank())TextButton(onClick={from="";to=""},modifier=Modifier.testTag("history-clear-dates")){Text("Clear dates")} }
        item { ServiceLoopPrimaryButton("Record past visit",{nav.navigate("visit/new")},Modifier.fillMaxWidth()) }
        if (state.history.isEmpty()) item { ServiceLoopNotice("No history found","No activity matches this scope and filters.",ServiceLoopNoticeKind.Info) }
        items(state.history, key = { it.id }) { row -> ServiceLoopDenseNavigableRow(title=row.title,context=row.subtitle,metadata="Event ${row.eventDate} · Recorded ${java.time.Instant.ofEpochMilli(row.recordedAtEpochMillis)}",leadingIcon=when(row.routeType){"RECORD"->ServiceLoopIcons.Report;"CONTACT"->ServiceLoopIcons.Customers;else->ServiceLoopIcons.History},onClick={when(row.routeType){"RECORD"->nav.navigate("record/${row.routeId}");"VISIT"->nav.navigate("visit/${row.routeId}");"FOLLOW_UP"->nav.navigate("follow-up/${row.routeId}");"CONTACT"->nav.navigate("contact/${row.routeId}");else->nav.navigate("change/${row.routeId}")}}) }
    }
}

@Composable
internal fun ContactNoteScreen(note: ContactNoteDetail?, padding: PaddingValues) {
    if (note == null) return Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(note.reference, style = MaterialTheme.typography.headlineSmall)
        Text("${note.channel.replace('_', ' ')} · ${Instant.ofEpochMilli(note.occurredAtEpochMillis)}")
        Text(note.outcome)
        if (note.privateNote.isNotBlank()) { HorizontalDivider(); Text("Internal / Not in customer report", style = MaterialTheme.typography.titleMedium); Text(note.privateNote) }
        if (note.enteredInError) Text("Entered in error${note.errorReason?.let { ": $it" }.orEmpty()}", color = MaterialTheme.colorScheme.error)
        Text("Read-only history entry", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun CorrectionScreen(recordId: String, state: UiState, padding: PaddingValues, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingEvidenceLine by remember { mutableStateOf<String?>(null) }
    val evidencePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val lineId = pendingEvidenceLine; pendingEvidenceLine = null
        if (uri != null && lineId != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBounded(30 * 1024 * 1024) } ?: error("Unable to read selected image") } }
                .onSuccess { bytes -> viewModel.addCorrectionEvidence(recordId, lineId, bytes, uri.lastPathSegment, context.contentResolver.getType(uri) ?: "image/jpeg", null) }
        }
    }
    LaunchedEffect(recordId) { viewModel.loadCorrection(recordId) }
    val loaded = state.correction?.takeIf { it.recordId == recordId } ?: return Box(Modifier.padding(padding).fillMaxSize()) { CircularProgressIndicator() }
    var draft by remember(loaded.modifiedAtEpochMillis) { mutableStateOf(loaded) }
    var initialized by remember(loaded.id) { mutableStateOf(false) }
    LaunchedEffect(draft) { if (initialized) { delay(600); viewModel.saveCorrection(draft) } else initialized = true }
    LazyColumn(Modifier.padding(padding).testTag("correction-workspace"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)) {
        item { Text("Original revision remains current until Commit succeeds."); Text("Subject links are fixed. A wrong customer/site/equipment requires Void and a separate correct visit.", style = MaterialTheme.typography.bodySmall) }
        item { OutlinedTextField(draft.reason, { draft = draft.copy(reason = it) }, label = { Text("Correction reason · Required") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(draft.actualServiceDate, { draft = draft.copy(actualServiceDate = it) }, label = { Text("Actual service date · Required") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(draft.customerName, { draft = draft.copy(customerName = it) }, label = { Text("Historical customer text") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(draft.siteName, { draft = draft.copy(siteName = it) }, label = { Text("Historical site text") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(draft.siteAddress, { draft = draft.copy(siteAddress = it) }, label = { Text("Historical address") }, modifier = Modifier.fillMaxWidth()) }
        items(draft.items, key = { it.id }) { line ->
            val index = draft.items.indexOfFirst { it.id == line.id }
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("${line.equipmentName} · ${line.serviceName}", style = MaterialTheme.typography.titleMedium); ServiceLoopChoiceGroup(listOf("PERFORMED", "PARTLY_PERFORMED", "NOT_PERFORMED").map { it to it.lowercase().replace('_', ' ') },line.outcome,{ outcome -> draft = draft.copy(items = draft.items.toMutableList().also { it[index] = line.copy(outcome = outcome) }) }); OutlinedTextField(line.publicWorkNote, { text -> draft = draft.copy(items = draft.items.toMutableList().also { it[index] = line.copy(publicWorkNote = text) }) }, label = { Text("Customer-visible work") }, minLines = 2, modifier = Modifier.fillMaxWidth()); if (line.fulfilledObligation) { ServiceLoopChoiceGroup(listOf(true to "Calculate from corrected date",false to "Retain / manual"),line.nextDueDateCalculated == true,{ calculated -> draft = draft.copy(items = draft.items.toMutableList().also { it[index] = line.copy(nextDueDateCalculated = calculated, nextDueOverrideReason = if(calculated) "" else line.nextDueOverrideReason) }) }); OutlinedTextField(line.proposedNextDueDate.orEmpty(), { date -> draft = draft.copy(items = draft.items.toMutableList().also { it[index] = line.copy(proposedNextDueDate = date, nextDueDateCalculated = false) }) }, label = { Text("Proposed next due") }, modifier = Modifier.fillMaxWidth(), enabled = line.nextDueDateCalculated != true); if (line.nextDueDateCalculated == false) OutlinedTextField(line.nextDueOverrideReason, { reason -> draft = draft.copy(items = draft.items.toMutableList().also { it[index] = line.copy(nextDueOverrideReason = reason) }) }, label = { Text("Reason for retained/manual date · Required") }, modifier = Modifier.fillMaxWidth()) } } }
        }
        items(draft.items, key = { "historical-${it.id}" }) { line ->
            val lineIndex = draft.items.indexOfFirst { it.id == line.id }
            fun update(value: CorrectionWorkDraft) { draft = draft.copy(items = draft.items.toMutableList().also { it[lineIndex] = value }) }
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Captured inspection, parts and evidence", style = MaterialTheme.typography.titleMedium)
                line.checklist.forEachIndexed { checkIndex, check ->
                    Text("${check.position}. ${check.label}", style = MaterialTheme.typography.labelLarge)
                    if (check.responseType == "STATUS") ServiceLoopChoiceGroup(listOf("OK", "ISSUE_FOUND", "NOT_APPLICABLE", "NOT_CHECKED").map { it to it.lowercase().replace('_', ' ') },check.disposition,{ disposition -> update(line.copy(checklist = line.checklist.toMutableList().also { it[checkIndex] = check.copy(disposition = disposition) })) })
                    else OutlinedTextField(check.textValue.orEmpty(), { text -> update(line.copy(checklist = line.checklist.toMutableList().also { it[checkIndex] = check.copy(textValue = text) })) }, label = { Text("Corrected answer") }, modifier = Modifier.fillMaxWidth())
                    if (check.disposition == "ISSUE_FOUND") OutlinedTextField(check.reason.orEmpty(), { text -> update(line.copy(checklist = line.checklist.toMutableList().also { it[checkIndex] = check.copy(reason = text) })) }, label = { Text("Public finding description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
                Text("Parts", style = MaterialTheme.typography.titleSmall)
                line.parts.forEachIndexed { partIndex, part -> Column { OutlinedTextField(part.description, { text -> update(line.copy(parts = line.parts.toMutableList().also { it[partIndex] = part.copy(description = text) })) }, label = { Text("Part") }, modifier = Modifier.fillMaxWidth()); Row { OutlinedTextField(part.quantity, { text -> update(line.copy(parts = line.parts.toMutableList().also { it[partIndex] = part.copy(quantity = text) })) }, label = { Text("Quantity") }, modifier = Modifier.weight(1f)); OutlinedTextField(part.unit, { text -> update(line.copy(parts = line.parts.toMutableList().also { it[partIndex] = part.copy(unit = text) })) }, label = { Text("Unit") }, modifier = Modifier.weight(1f)) }; TextButton({ update(line.copy(parts = line.parts.toMutableList().also { it.removeAt(partIndex) })) }) { Text("Remove from corrected revision") } } }
                TextButton({ update(line.copy(parts = line.parts + CorrectionPartDraft(null, "", "1", "item"))) }) { Text("Add part") }
                Text("Selected photographs/evidence", style = MaterialTheme.typography.titleSmall)
                line.photos.forEachIndexed { photoIndex, photo -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(photo.selected, { selected -> update(line.copy(photos = line.photos.toMutableList().also { it[photoIndex] = photo.copy(selected = selected) })) }); Column { Text(photo.caption ?: photo.storedRelativePath); if (photo.addedInCorrection) Text("Added in correction · ${photo.addedAtEpochMillis?.let { Instant.ofEpochMilli(it).toString() }.orEmpty()}", style = MaterialTheme.typography.bodySmall) } } }
                OutlinedButton(onClick = { pendingEvidenceLine = line.id; evidencePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("Add correction evidence") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(line.fulfilledObligation, { update(line.copy(fulfilledObligation = it)) }); Text("Fulfills current obligation") }
            } }
        }
        item { OutlinedTextField(draft.publicNote, { draft = draft.copy(publicNote = it) }, label = { Text("Relevant public note") }, minLines = 2, modifier = Modifier.fillMaxWidth()); OutlinedTextField(draft.privateNote, { draft = draft.copy(privateNote = it) }, label = { Text("Internal note · Not in customer report") }, minLines = 2, modifier = Modifier.fillMaxWidth()) }
        items(draft.followUps, key = { "follow-up-${it.id}" }) { followUp ->
            val index = draft.followUps.indexOfFirst { it.id == followUp.id }
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("Affected follow-up · ${followUp.title}"); ServiceLoopChoiceGroup(listOf("KEEP" to "Keep","CANCEL" to "Cancel as erroneous"),followUp.action,{ action -> draft = draft.copy(followUps = draft.followUps.toMutableList().also { it[index] = followUp.copy(action = action) }) }); if (followUp.action == "CANCEL") OutlinedTextField(followUp.cancellationReason, { reason -> draft = draft.copy(followUps = draft.followUps.toMutableList().also { it[index] = followUp.copy(cancellationReason = reason) }) }, label = { Text("Cancellation reason · Required") }, modifier = Modifier.fillMaxWidth()) } }
        }
        item { TextButton({ draft = draft.copy(newFollowUps = draft.newFollowUps + CorrectionNewFollowUpDraft("", state.businessDate.toString())) }) { Text("Add required corrective task") }; draft.newFollowUps.forEachIndexed { index, followUp -> OutlinedTextField(followUp.title, { text -> draft = draft.copy(newFollowUps = draft.newFollowUps.toMutableList().also { it[index] = followUp.copy(title = text) }) }, label = { Text("Corrective task title") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(followUp.dueDate, { text -> draft = draft.copy(newFollowUps = draft.newFollowUps.toMutableList().also { it[index] = followUp.copy(dueDate = text) }) }, label = { Text("Due date") }, modifier = Modifier.fillMaxWidth()) } }
        item { Row { Checkbox(draft.scheduleAcknowledged, { draft = draft.copy(scheduleAcknowledged = it) }); Text("I reviewed the before/after schedule effect", modifier = Modifier.padding(top = 12.dp)) } }
        if (state.error != null) item { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Correction save failed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium); Text("Your current text remains on this screen but is not confirmed saved. Clipboard contents may be visible to other apps; copied text is not a backup."); ServiceLoopActionStack { OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }, modifier = Modifier.fillMaxWidth()) { Text("Open device storage settings") }; OutlinedButton(onClick = { val rescue = buildString { appendLine("Correction reason: ${draft.reason}"); appendLine("Service date: ${draft.actualServiceDate}"); appendLine("Customer: ${draft.customerName}"); appendLine("Site: ${draft.siteName}"); draft.items.forEach { appendLine("${it.equipmentName} / ${it.serviceName}: ${it.publicWorkNote}") } }; (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Unsaved ServiceLoop correction", rescue)) }, modifier = Modifier.fillMaxWidth()) { Text("Copy unsaved text") } } } } }
        item { ServiceLoopActionStack { Button(onClick = { viewModel.commitCorrection(recordId) { nav.navigate("record/$recordId") { popUpTo("correction/$recordId") { inclusive = true } } } }, enabled = draft.reason.isNotBlank() && !state.operationInProgress, modifier = Modifier.fillMaxWidth().testTag("commit-correction")) { Text("Commit correction") }; OutlinedButton(onClick = { viewModel.discardCorrection(recordId) { nav.popBackStack() } }, modifier = Modifier.fillMaxWidth()) { Text("Discard correction draft") } }; Text("Back leaves this durable correction draft for later.") }
    }
}

@Composable
internal fun LifecycleScreen(subjectType: String, id: String, action: String, state: UiState, padding: PaddingValues, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    LaunchedEffect(subjectType, id, action) { viewModel.loadLifecycle(subjectType, id, action) }; val review = state.lifecycleReview
    RefreshOnResume { viewModel.loadLifecycle(subjectType, id, action) }
    var reason by remember { mutableStateOf("") }
    LazyColumn(Modifier.padding(padding).testTag("lifecycle-review"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)) {
        item { Text("${action.lowercase().replaceFirstChar { it.uppercase() }} ${review?.subjectLabel.orEmpty()}", style = MaterialTheme.typography.headlineSmall); review?.consequences?.forEach { Text("• $it") } }
        review?.blockers?.let { blockers -> items(blockers) { blocker -> ServiceLoopDenseNavigableRow(blocker.label,"Open blocking item",leadingIcon=ServiceLoopIcons.Lock,onClick={nav.navigate(blocker.route)}) } }
        item { OutlinedTextField(reason, { reason = it }, label = { Text("Reason · Required") }, modifier = Modifier.fillMaxWidth()); Button(onClick = { viewModel.applyLifecycle(subjectType, id, action, reason) { nav.popBackStack() } }, enabled = review?.allowed == true && reason.isNotBlank() && !state.operationInProgress, modifier = Modifier.fillMaxWidth()) { Text("Confirm ${action.lowercase()}") } }
    }
}

@Composable
internal fun MoveEquipmentScreen(id: String, state: UiState, padding: PaddingValues, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    LaunchedEffect(id) { viewModel.loadMove(id) }; val review = state.moveReview
    RefreshOnResume { viewModel.loadMove(id) }
    var destination by remember { mutableStateOf("") }; var date by remember { mutableStateOf(state.businessDate.toString()) }; var reason by remember { mutableStateOf("") }; var acknowledge by remember { mutableStateOf(false) }
    LazyColumn(Modifier.padding(padding).testTag("move-equipment"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)) {
        item { Text(review?.equipmentLabel.orEmpty(), style = MaterialTheme.typography.headlineSmall); Text("From ${review?.oldContext.orEmpty()}") }
        review?.blockers?.let { items(it) { blocker -> ServiceLoopDenseNavigableRow(blocker.label,if(blocker.kind=="CORRECTION")"Open correction" else "Open blocking item",leadingIcon=ServiceLoopIcons.Lock,onClick={nav.navigate(blocker.route)}) } }
        item { Text("Destination site · Required"); review?.destinations?.forEach { site -> FilterChip(destination == site.id, { destination = site.id }, { Text("${site.customerName} · ${site.name}") }) }; OutlinedTextField(date, { date = it }, label = { Text("Effective date · Required") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(reason, { reason = it }, label = { Text("Reason · Required") }, modifier = Modifier.fillMaxWidth()) }
        item { review?.carriedPlans?.forEach { Text("${it.reference} · ${it.state} · Due ${it.dueDate}") }; Row { Checkbox(acknowledge, { acknowledge = it }); Text("Carry these plans and dates; retain history at the original site.", modifier = Modifier.padding(top = 12.dp)) }; Button(onClick = { viewModel.moveEquipment(id, destination, date, reason, acknowledge) { nav.popBackStack() } }, enabled = review?.blockers?.isEmpty() == true && destination.isNotBlank() && reason.isNotBlank() && acknowledge, modifier = Modifier.fillMaxWidth()) { Text("Move equipment") } }
    }
}

@Composable
private fun RefreshOnResume(refresh: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(refresh)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) latest() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

@Composable
internal fun DataRecoveryScreen(state: UiState, padding: PaddingValues, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    LaunchedEffect(Unit) { viewModel.loadDatasetSummary() }; val data = state.datasetSummary
    LazyColumn(Modifier.padding(padding).testTag("data-recovery"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)) {
        item { Text("One authoritative local dataset", style = MaterialTheme.typography.headlineSmall); Text("${data?.customers ?: 0} customers · ${data?.equipment ?: 0} equipment · ${data?.visits ?: 0} visits"); Text("${data?.attachments ?: 0} attachments · ${data?.reports ?: 0} reports · ${data?.storedBytes ?: 0} bytes"); if (data?.restoredFromIncompleteCopy == true) Text("Restored from an incomplete recovery copy · Some named files remain missing", color = MaterialTheme.colorScheme.error); if (data?.restrictedRecoveryState == true) Text("Recovery required · Normal business use is restricted", color = MaterialTheme.colorScheme.error); Text(if (data?.changedSinceBackup == true) "Saved business changes exist after the last verified full backup" else "No later saved changes detected") }
        if (data?.restrictedRecoveryState != true) item { Text("Backup reminder"); ServiceLoopChoiceGroup(listOf(0,1,7,30).map{it to if(it==0) "Off" else "$it day"},data?.backupReminderDays ?: 7,{viewModel.setBackupReminder(it)}) }
        item { ServiceLoopActionStack { if (data?.restrictedRecoveryState != true) Button(onClick = { nav.navigate("backup/create") }, modifier = Modifier.fillMaxWidth()) { ServiceLoopIcon(ServiceLoopIcons.Backup,null,Modifier.size(24.dp));Spacer(Modifier.width(8.dp));Text("Create full backup") }; OutlinedButton(onClick = { nav.navigate("backup/inspect") }, modifier = Modifier.fillMaxWidth()) { ServiceLoopIcon(ServiceLoopIcons.Lock,null,Modifier.size(24.dp));Spacer(Modifier.width(8.dp));Text("Inspect/verify a backup") }; OutlinedButton(onClick = { nav.navigate("backup/restore") }, modifier = Modifier.fillMaxWidth()) { ServiceLoopIcon(ServiceLoopIcons.Restore,null,Modifier.size(24.dp));Spacer(Modifier.width(8.dp));Text("Restore a backup") }; if (data?.restrictedRecoveryState != true) { OutlinedButton(onClick = { nav.navigate("csv/export") }, modifier = Modifier.fillMaxWidth()) { ServiceLoopIcon(ServiceLoopIcons.Share,null,Modifier.size(24.dp));Spacer(Modifier.width(8.dp));Text("Export readable CSV") }; OutlinedButton(onClick = { nav.navigate("csv/import") }, modifier = Modifier.fillMaxWidth()) { ServiceLoopIcon(ServiceLoopIcons.ExternalOpen,null,Modifier.size(24.dp));Spacer(Modifier.width(8.dp));Text("Import customers/sites/equipment") } }; TextButton(onClick = { nav.navigate("data/erase") }, modifier = Modifier.fillMaxWidth()) { ServiceLoopIcon(ServiceLoopIcons.Delete,null,Modifier.size(24.dp));Spacer(Modifier.width(8.dp));Text("Erase this device's data") } } }
    }
}

@Composable
internal fun BackupScreen(mode: String, state: UiState, padding: PaddingValues, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    val context = LocalContext.current; var passphrase by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }; var incomplete by remember { mutableStateOf(false) }; var launched by remember { mutableStateOf(false) }; var selectedBytes by remember { mutableStateOf<ByteArray?>(null) }; var replacement by remember { mutableStateOf("") }; var acknowledgeIncomplete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? -> uri?.let { target -> state.backupResult?.let { result -> scope.launch { val readback = withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(target, "w")!!.use { it.write(result.bytes) }; context.contentResolver.openInputStream(target)!!.use { it.readBounded(RecoveryPackage.MAX_PACKAGE_BYTES) } }; viewModel.verifyWrittenBackup(readback, passphrase.toCharArray(), target.toString()) } } } }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let { scope.launch { selectedBytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(it)?.use { stream -> stream.readBounded(RecoveryPackage.MAX_PACKAGE_BYTES) } } } } }
    LaunchedEffect(mode) { if (mode == "restore") viewModel.loadDatasetSummary() }
    LaunchedEffect(state.backupResult) { if (mode == "create" && state.backupResult != null && !launched) { launched = true; create.launch("ServiceLoop-${state.businessDate}.slbackup") } }
    LazyColumn(Modifier.padding(padding).testTag("backup-$mode"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)) {
        item { Text(if (mode == "create") "Create and verify backup" else if (mode == "restore") "Restore replaces this dataset" else "Inspect backup", style = MaterialTheme.typography.headlineSmall); Text("The passphrase is never stored. There is no reset or backdoor.") }
        if (mode != "create") item { Button(onClick = { open.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) }, modifier = Modifier.fillMaxWidth()) { ServiceLoopIcon(ServiceLoopIcons.Lock,null,Modifier.size(24.dp));Spacer(Modifier.width(8.dp));Text("Choose backup") } }
        item { OutlinedTextField(passphrase, { passphrase = it }, label = { Text("Passphrase · Required · 12+ characters") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()); if (mode == "create") OutlinedTextField(confirm, { confirm = it }, label = { Text("Confirm passphrase · Required") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()) }
        if (mode == "create") item { Row { Checkbox(incomplete, { incomplete = it }); Text("Create incomplete recovery copy only if named files are missing", modifier = Modifier.padding(top = 12.dp)) }; Button(onClick = { launched = false; viewModel.createBackup(passphrase.toCharArray(), incomplete) }, enabled = passphrase.length >= 12 && passphrase == confirm && !state.operationInProgress, modifier = Modifier.fillMaxWidth()) { Text(if (incomplete) "Create recovery copy" else "Create backup") } }
        if (mode != "create") item { Button(onClick = { selectedBytes?.let { viewModel.inspectBackup(it, passphrase.toCharArray()) } }, enabled = selectedBytes != null && passphrase.length >= 12, modifier = Modifier.fillMaxWidth()) { ServiceLoopIcon(ServiceLoopIcons.Lock,null,Modifier.size(24.dp));Spacer(Modifier.width(8.dp));Text("Unlock and inspect") } }
        state.backupInspection?.let { inspection -> item { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text(if (inspection.complete) "Verified complete package" else "Incomplete recovery copy", style = MaterialTheme.typography.titleMedium); Text("Snapshot ${java.time.Instant.ofEpochMilli(inspection.snapshotAtEpochMillis)}"); Text("${inspection.records} records · ${inspection.files} files"); inspection.missingFiles.forEach { Text("Missing: $it", color = MaterialTheme.colorScheme.error) } } } } }
        if (mode == "restore" && state.backupInspection != null) item { Text("Current: ${state.datasetSummary?.customers ?: 0} customers · ${state.datasetSummary?.visits ?: 0} visits. Candidate: ${state.backupInspection.records} records · snapshot ${Instant.ofEpochMilli(state.backupInspection.snapshotAtEpochMillis)}."); if (state.datasetSummary?.changedSinceBackup == true) Text("Warning: current local work is newer than its last verified backup.", color = MaterialTheme.colorScheme.error); Text("All current local records, drafts and app-held files will be replaced. This is not a merge."); OutlinedButton(onClick = { nav.navigate("backup/create") }, modifier = Modifier.fillMaxWidth()) { Text("Back up current data first") }; Text("Returning here preserves this inspected candidate; replacement never starts automatically.", style = MaterialTheme.typography.bodySmall); if (state.backupInspection.complete.not()) Row { Checkbox(acknowledgeIncomplete, { acknowledgeIncomplete = it }); Text("I accept the declared missing files") }; OutlinedTextField(replacement, { replacement = it }, label = { Text("Type REPLACE") }, modifier = Modifier.fillMaxWidth()); Button(onClick = { viewModel.restoreBackup(replacement, acknowledgeIncomplete) { nav.navigate("home") { popUpTo(0) } } }, enabled = replacement == "REPLACE" && (state.backupInspection.complete || acknowledgeIncomplete), modifier = Modifier.fillMaxWidth()) { Text("Replace local dataset") } }
    }
}

@Composable
internal fun CsvExportScreen(state: UiState, padding: PaddingValues, viewModel: ServiceLoopViewModel) {
    val context = LocalContext.current; var inactive by remember { mutableStateOf(true) }; var privateFields by remember { mutableStateOf(false) }; var previous by remember { mutableStateOf(false) }; var selectedCustomer by remember { mutableStateOf<String?>(null) }; var customerQuery by remember { mutableStateOf("") }; var launched by remember { mutableStateOf(false) }; var filename by remember { mutableStateOf("ServiceLoop-directory.csv") }
    val scope = rememberCoroutineScope()
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { state.exportBytes?.let { bytes -> scope.launch { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(it, "w")!!.use { out -> out.write(bytes) } } } } } }
    LaunchedEffect(state.exportBytes) { if (state.exportBytes != null && !launched) { launched = true; create.launch(filename) } }
    val visibleCustomers = state.customerList.filter { customerQuery.isBlank() || it.name.contains(customerQuery, true) || it.reference.contains(customerQuery, true) }
    LazyColumn(Modifier.padding(padding).testTag("csv-export"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)) {
        item { Text("Readable CSV export", style = MaterialTheme.typography.headlineSmall); Text("CSV files are readable and unencrypted. They do not restore photographs, report files, or the complete app."); Text("Scope"); FilterChip(selectedCustomer == null, { selectedCustomer = null }, { Text("All customers") }); OutlinedTextField(customerQuery, { customerQuery = it }, label = { Text("Find customer") }, modifier = Modifier.fillMaxWidth()) }
        items(visibleCustomers, key = { it.id }) { customer -> FilterChip(selectedCustomer == customer.id, { selectedCustomer = customer.id }, { Text("${customer.reference} · ${customer.name}") }, modifier = Modifier.fillMaxWidth()) }
        item { Row { Checkbox(inactive, { inactive = it }); Text("Include archived / retired records") }; Row { Checkbox(privateFields, { privateFields = it }); Text("Include private/access notes (plaintext)") }; Row { Checkbox(previous, { previous = it }); Text("Include previous record revisions (records package)") }; ServiceLoopActionStack { Button(onClick = { filename = "ServiceLoop-directory.csv"; launched = false; viewModel.prepareDirectoryCsv(inactive, privateFields, selectedCustomer) }, modifier = Modifier.fillMaxWidth()) { Text("Create directory export") }; OutlinedButton(onClick = { filename = "ServiceLoop-records.zip"; launched = false; viewModel.prepareRecordsCsv(inactive, privateFields, previous, selectedCustomer) }, modifier = Modifier.fillMaxWidth()) { Text("Create records package") } } }
    }
}

@Composable
internal fun CsvImportScreen(state: UiState, padding: PaddingValues, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { scope.launch { val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(it)?.use { stream -> stream.readBounded(20 * 1024 * 1024) } }; bytes?.let(viewModel::validateCsv) } } }
    var templateBytes by remember { mutableStateOf(Stage4Service.blankTemplate()) }
    val saveTemplate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { scope.launch { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(it, "w")?.use { output -> output.write(templateBytes) } } } } }
    var createSeparate by remember { mutableStateOf(emptySet<String>()) }
    var skipped by remember { mutableStateOf(emptySet<String>()) }
    var filter by remember { mutableStateOf("ALL") }
    var validationBytes by remember { mutableStateOf(ByteArray(0)) }
    LaunchedEffect(state.csvPreview) { createSeparate = emptySet(); skipped = emptySet(); filter = "ALL" }
    val saveValidation = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { scope.launch { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(it, "w")?.use { output -> output.write(validationBytes) } } } } }
    LazyColumn(Modifier.padding(padding).testTag("csv-import"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Fixed create-only directory import", style = MaterialTheme.typography.headlineSmall); Text("No overwrite, merge, plans, visits, PDFs, or photographs. Maximum 20 MiB / 10,000 rows."); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { templateBytes = Stage4Service.blankTemplate(); saveTemplate.launch("ServiceLoop-import-template.csv") }) { Text("Save blank template") }; OutlinedButton(onClick = { templateBytes = Stage4Service.workedExample(); saveTemplate.launch("ServiceLoop-import-example.csv") }) { Text("Save example") } }; Text("Exact references are reused only when supplied fields and parents match. Different fields are hard conflicts; possible duplicates require Create separate or Skip branch."); Button(onClick = { open.launch(arrayOf("text/csv", "text/*", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Choose CSV") } }
        state.csvPreview?.let { preview -> item { Text("${preview.newCustomers} customers · ${preview.newSites} sites · ${preview.newEquipment} equipment"); Text("${preview.errors} errors · ${preview.warnings} warnings"); ServiceLoopFilterSelector("Rows",filter,listOf("ALL" to "All", "ERROR" to "Errors", "WARNING" to "Warnings", "NEW" to "New", "EXISTING_UNCHANGED" to "Existing unchanged"),{filter=it},Modifier.fillMaxWidth(),testTag="csv-row-filter"); OutlinedButton({ validationBytes = ("row,status,branch,messages\r\n" + preview.rows.joinToString("\r\n") { row -> "${row.rowNumber},${row.status},${row.branchKey},\"${row.messages.joinToString("; ").replace("\"", "\"\"")}\"" }).toByteArray(); saveValidation.launch("ServiceLoop-import-validation.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Save validation report") } }; items(preview.rows.filter { filter == "ALL" || it.status == filter }, key = { it.rowNumber }) { row -> ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) { Text("Row ${row.rowNumber} · ${row.status}"); row.messages.forEach { Text(it, color = if(row.status=="WARNING") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error) }; if(row.status=="WARNING") Row { TextButton({createSeparate=createSeparate+row.branchKey;skipped=skipped-row.branchKey}){Text("Create separate")}; TextButton({skipped=skipped+row.branchKey;createSeparate=createSeparate-row.branchKey}){Text("Skip branch")} } } } }; item { Button(onClick = { viewModel.importCsv(createSeparate, skipped) }, enabled = preview.errors == 0 && preview.rows.filter{it.status=="WARNING"}.all{it.branchKey in createSeparate || it.branchKey in skipped}, modifier = Modifier.fillMaxWidth()) { Text("Import reviewed records") } } }
        state.importResult?.let { result -> item { Text("Imported ${result.customersCreated} customers · ${result.sitesCreated} sites · ${result.equipmentCreated} equipment"); Text("${result.existingUnchanged} existing rows unchanged"); ServiceLoopActionStack { Button(onClick = { nav.navigate("customers") }, modifier = Modifier.fillMaxWidth()) { Text("View imported customers") }; OutlinedButton(onClick = { open.launch(arrayOf("text/csv", "text/*", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Choose corrected file") } } } }
    }
}

private fun InputStream.readBounded(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024)); val buffer = ByteArray(16 * 1024); var total = 0
    while (true) { val count = read(buffer); if (count < 0) break; total += count; require(total <= maxBytes) { "Selected file exceeds the supported size" }; output.write(buffer, 0, count) }
    return output.toByteArray()
}

@Composable
internal fun EraseScreen(state: UiState, padding: PaddingValues, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    LaunchedEffect(Unit) { viewModel.loadDatasetSummary() }; var acknowledged by remember { mutableStateOf(false) }; var confirmation by remember { mutableStateOf("") }
    Column(Modifier.padding(padding).padding(16.dp).testTag("erase-data"), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Erase this device's data", style = MaterialTheme.typography.headlineSmall); Text("${state.datasetSummary?.customers ?: 0} customers · ${state.datasetSummary?.unfinishedVisits ?: 0} unfinished visits · ${state.datasetSummary?.attachments ?: 0} attachments"); Text("Exported/shared copies remain outside ServiceLoop. This does not delete an account because ServiceLoop has no account."); OutlinedButton(onClick = { nav.navigate("backup/create") }, modifier = Modifier.fillMaxWidth()) { Text("Make backup first") }; Row { Checkbox(acknowledged, { acknowledged = it }); Text("I understand this local data will be lost") }; OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Type ERASE") }, modifier = Modifier.fillMaxWidth()); ServiceLoopDestructiveButton("Erase local data",{viewModel.erase(acknowledged,confirmation){nav.navigate("home"){popUpTo(0)}}},Modifier.fillMaxWidth(),enabled=acknowledged&&confirmation=="ERASE") }
}
