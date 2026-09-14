package com.v16studio.serviceloop.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.InspectionQuestion
import com.v16studio.serviceloop.domain.CompletionBlockerKind
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.domain.SaveStatus
import com.v16studio.serviceloop.domain.ServiceDocumentationMode
import com.v16studio.serviceloop.domain.ServiceEntryStatus
import com.v16studio.serviceloop.domain.ServiceProgressGroup
import com.v16studio.serviceloop.domain.ServiceProgressItem
import com.v16studio.serviceloop.domain.VisitServiceProgress
import com.v16studio.serviceloop.domain.WorkSubjectType
import com.v16studio.serviceloop.domain.ServiceDraftFieldKeys
import com.v16studio.serviceloop.domain.serviceDocumentationMode
import com.v16studio.serviceloop.domain.serviceEntryStatus
import com.v16studio.serviceloop.domain.serviceProgressGroups
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChoiceChip
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDenseNavigableRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopLongTextEditor
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNotice
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNoticeKind
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPinnedBar
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSecondaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSurfaceCard
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextAction
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextField
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.service.ServiceDraftFieldState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ServiceListIndices(
    val workPerformed: Int,
    val checklistHeader: Int,
    val firstQuestion: Int,
)

internal fun serviceListIndices(
    hasNavigationMessage: Boolean,
    hasDocumentationNotice: Boolean,
    hasPrivateContext: Boolean,
): ServiceListIndices {
    var next = 1 // Service identity block
    if (hasNavigationMessage) next++
    next++ // Visit progress
    if (hasDocumentationNotice) next++
    if (hasPrivateContext) next++
    val workPerformed = next++
    next++ // Private note, either collapsed action or inline field
    val checklistHeader = next++
    return ServiceListIndices(workPerformed, checklistHeader, next)
}

/** Replaces only the active Service entry, preserving any Visit below it. */
internal fun replaceServiceDestination(nav: NavHostController, workItemId: String) {
    nav.navigate("inspection/$workItemId") {
        popUpTo("inspection/{id}") { inclusive = true }
        launchSingleTop = true
    }
}

/** Flushes callers should use this route-aware return instead of popping an arbitrary entry. */
internal fun openVisitOverview(nav: NavHostController, visitId: String) {
    val visitRoute = "visit/$visitId"
    if (!nav.popBackStack(visitRoute, false)) {
        nav.navigate(visitRoute) {
            popUpTo("inspection/{id}") { inclusive = true }
            launchSingleTop = true
        }
    }
}

/** The single Working Visit Service workspace. InspectionScreen below is only a compatibility entry point. */
@Composable
internal fun ServiceScreen(
    draft: InspectionDraft,
    progress: VisitServiceProgress?,
    saveStatus: SaveStatus,
    focus: InspectionFocus?,
    viewModel: ServiceLoopViewModel,
    nav: NavHostController,
    padding: PaddingValues = PaddingValues(),
    backInterceptor: MutableState<(() -> Unit)?>? = null,
    listTag: String = "service-list",
) {
    val resolvedProgress = progress ?: fallbackProgress(draft)
    val fieldStates by viewModel.serviceDraftStates.collectAsState()
    val viewState by viewModel.state.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    var servicesExpanded by rememberSaveable(draft.visitId) { mutableStateOf(false) }
    var navigationMessage by rememberSaveable(draft.workItemId) { mutableStateOf<String?>(null) }
    var showLeaveDialog by rememberSaveable(draft.workItemId) { mutableStateOf(false) }
    var leaveFlushResult by remember { mutableStateOf<com.v16studio.serviceloop.ui.service.ServiceDraftFlushResult?>(null) }
    val current = resolvedProgress.items.firstOrNull { it.workItemId == draft.workItemId }
    val currentPosition = current?.position ?: 1
    val next = resolvedProgress.nextService(draft.workItemId)
    val editingEnabled = current?.documentationMode != ServiceDocumentationMode.CHOICE_REQUIRED &&
        current?.documentationMode != ServiceDocumentationMode.LEADER_OBSERVE && current?.documentationMode != ServiceDocumentationMode.DEFERRED
    val contextRows = listOf(
        "Site access" to draft.siteAccessNote,
        "Equipment" to draft.equipmentPrivateNote,
        "Dispatch" to draft.dispatchInstructions.orEmpty(),
    ).filter { it.second.isNotBlank() }
    val listIndices = serviceListIndices(
        hasNavigationMessage = navigationMessage != null,
        hasDocumentationNotice = current?.documentationMode != ServiceDocumentationMode.LOCAL,
        hasPrivateContext = contextRows.isNotEmpty(),
    )

    fun flushAndThen(action: () -> Unit) {
        scope.launch {
            val result = runCatching { viewModel.flushServiceDraft(draft.workItemId) }.getOrElse {
                navigationMessage = "Resolve unsaved service edits before continuing."
                return@launch
            }
            if (result.success) withContext(Dispatchers.Main.immediate) { action() } else navigationMessage = "Resolve unsaved service edits before continuing."
        }
    }

    fun leaveService() {
        scope.launch {
            val result = runCatching { viewModel.flushServiceDraft(draft.workItemId) }.getOrElse {
                leaveFlushResult = null
                showLeaveDialog = true
                return@launch
            }
            if (result.success) withContext(Dispatchers.Main.immediate) { openVisitOverview(nav, draft.visitId) } else {
                leaveFlushResult = result
                showLeaveDialog = true
            }
        }
    }

    DisposableEffect(backInterceptor, draft.workItemId) {
        if (backInterceptor == null) onDispose { }
        else {
            val previous = backInterceptor.value
            val handler: () -> Unit = { leaveService() }
            backInterceptor.value = handler
            onDispose { if (backInterceptor.value === handler) backInterceptor.value = previous }
        }
    }
    BackHandler(enabled = backInterceptor == null) { leaveService() }
    DisposableEffect(lifecycleOwner, draft.workItemId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flushServiceDraftAsync(draft.workItemId)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(draft.workItemId, focus, listIndices) {
        focus?.let { target ->
             val index = when (target.kind) {
                 CompletionBlockerKind.WORK_PERFORMED -> listIndices.workPerformed
                 CompletionBlockerKind.CHECKLIST_INCOMPLETE -> listIndices.checklistHeader
                 CompletionBlockerKind.FINDING_DESCRIPTION -> draft.questions.indexOfFirst { it.snapshotItemId == target.questionId }
                     .takeIf { it >= 0 }
                     ?.let { listIndices.firstQuestion + it }
                     ?: listIndices.checklistHeader
                 else -> 0
             }
            listState.scrollToItem(index.coerceAtLeast(0))
            viewModel.clearInspectionFocus()
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Service edit still pending") },
            text = { Text("The unfinished edit is kept on this device and will be restored. Retry before leaving when possible.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.retryFailedServiceEdits(draft.workItemId)
                    showLeaveDialog = false
                    leaveService()
                }) { Text("Retry") }
            },
             dismissButton = { TextButton(onClick = { showLeaveDialog = false; openVisitOverview(nav, draft.visitId) }) { Text("Leave service") } },
        )
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            Modifier.weight(1f).testTag(listTag),
            state = listState,
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section),
        ) {
            item {
                ServiceIdentityBlock(draft, currentPosition, resolvedProgress.items.size, current, fieldStates, saveStatus, viewModel, nav)
            }
            navigationMessage?.let { message -> item { ServiceLoopNotice("Resolve unsaved service edits before continuing.", message, ServiceLoopNoticeKind.Error) } }
            item {
                ServiceProgressNavigator(
                    progress = resolvedProgress,
                    currentWorkItemId = draft.workItemId,
                    expanded = servicesExpanded,
                    onExpandedChange = { servicesExpanded = it },
                    onSelect = { target -> if (target.workItemId != draft.workItemId) flushAndThen { replaceServiceDestination(nav, target.workItemId) } },
                )
            }
            if (current?.documentationMode != ServiceDocumentationMode.LOCAL) {
                item { DocumentationModeNotice(current?.documentationMode ?: ServiceDocumentationMode.DEFERRED) { openVisitOverview(nav, draft.visitId) } }
            }
            if (contextRows.isNotEmpty()) item { PrivateWorkContext(contextRows) }
            item {
                ServiceLoopSurfaceCard(modifier = Modifier.testTag("work-performed-section")) {
                    Text("Work performed", style = MaterialTheme.typography.titleLarge)
                    Text("Customer report", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    val initialWork = draft.rawInputs[ServiceDraftFieldKeys.WORK] ?: draft.workPerformed
                    var work by rememberSaveable(draft.workItemId, initialWork) { mutableStateOf(initialWork) }
                    ServiceLoopLongTextEditor(
                        value = work,
                        onValueChange = { work = it; if (editingEnabled) viewModel.scheduleWorkText(draft.workItemId, it) },
                        label = "Work performed",
                        private = false,
                        enabled = editingEnabled,
                        fieldTestTag = "long-text-public-work-performed",
                        onFocusLost = { viewModel.flushServiceDraftAsync(draft.workItemId) },
                    )
                }
            }
            item {
                val initialPrivate = draft.rawInputs[ServiceDraftFieldKeys.PRIVATE] ?: draft.privateInternalNote
                 var privateExpanded by rememberSaveable(draft.workItemId, initialPrivate) { mutableStateOf(initialPrivate.isNotBlank()) }
                 var privateNote by rememberSaveable(draft.workItemId, initialPrivate) { mutableStateOf(initialPrivate) }
                if (!privateExpanded) {
                    ServiceLoopTextAction("+ Private note", { privateExpanded = true }, Modifier.testTag("add-private-note"), enabled = editingEnabled)
                } else {
                    ServiceLoopSurfaceCard(modifier = Modifier.testTag("private-work-note")) {
                        Text("Private note", style = MaterialTheme.typography.titleMedium)
                        ServiceLoopLongTextEditor(
                            value = privateNote,
                            onValueChange = { privateNote = it; if (editingEnabled) viewModel.schedulePrivateText(draft.workItemId, it) },
                            label = "Private note",
                            private = true,
                            enabled = editingEnabled,
                            onFocusLost = { viewModel.flushServiceDraftAsync(draft.workItemId) },
                            fieldTestTag = "long-text-private-note",
                        )
                    }
                }
            }
            item {
                ChecklistSectionHeader(draft)
            }
            if (draft.questions.isEmpty()) {
                item { Text("No checklist for this service.", modifier = Modifier.testTag("no-checklist")) }
            } else {
                items(draft.questions, key = { it.snapshotItemId }) { question ->
                    ServiceQuestionBlock(draft.workItemId, draft.rawInputs, question, editingEnabled, viewModel)
                }
                item { ChecklistCompletionSummary(draft) }
            }
            item {
                ServiceLoopSecondaryButton("Parts & photos", { nav.navigate("field/${draft.workItemId}") }, Modifier.fillMaxWidth().testTag("open-field-evidence"), enabled = editingEnabled)
            }
            if (viewState.contentRefreshError != null) item {
                Text(viewState.contentRefreshError!!, color = MaterialTheme.colorScheme.error)
            }
        }
        ServiceLoopPinnedBar {
            ServiceLoopSecondaryButton("Visit overview", { leaveService() }, Modifier.fillMaxWidth().testTag("service-visit-overview"))
            if (next != null) {
                ServiceLoopPrimaryButton("Next service", { flushAndThen { replaceServiceDestination(nav, next.workItemId) } }, Modifier.fillMaxWidth().testTag("next-service"))
            } else {
                ServiceLoopPrimaryButton("Review visit", {
                    scope.launch {
                         val result = runCatching { viewModel.flushVisitDraft(draft.visitId) }.getOrNull()
                         if (result?.success == true) withContext(Dispatchers.Main.immediate) { nav.navigate("review/${draft.visitId}") } else navigationMessage = "Resolve unsaved service edits before continuing."
                    }
                }, Modifier.fillMaxWidth().testTag("review-visit"), enabled = true)
            }
        }
    }
}

/** Compatibility surface for existing B025/B026 tests and transitional callers. */
@Composable
internal fun InspectionScreen(draft: InspectionDraft, saveStatus: SaveStatus, focus: InspectionFocus?, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    val state by viewModel.state.collectAsState()
    ServiceScreen(draft, state.serviceProgress, saveStatus, focus, viewModel, nav, listTag = "inspection-list")
}

@Composable
internal fun ServiceProgressNavigator(
    progress: VisitServiceProgress,
    currentWorkItemId: String?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (ServiceProgressItem) -> Unit,
    rowTagPrefix: String = "service-row",
) {
    ServiceLoopSurfaceCard(modifier = Modifier.testTag("visit-progress")) {
        Text("Visit progress", style = MaterialTheme.typography.titleLarge)
        Text(progressSummary(progress))
        ServiceLoopTextAction(if (expanded) "Hide services" else "Show services", { onExpandedChange(!expanded) }, Modifier.testTag("show-services"))
        if (expanded) {
            progress.groups.forEach { group ->
                Text(group.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                group.items.forEach { item ->
                    val selected = item.workItemId == currentWorkItemId
                    ServiceLoopDenseNavigableRow(
                        title = item.serviceName,
                        context = serviceLoopSubjectLabel(item.subjectType, item.equipmentName, item.equipmentReference, item.equipmentDescription),
                        status = item.navigationStatusLabel(),
                        modifier = Modifier.testTag("$rowTagPrefix-${item.workItemId}").semantics { this.selected = selected },
                        showDisclosure = true,
                        onClick = { onSelect(item) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun VisitServiceProgressOverview(progress: VisitServiceProgress, onSelect: (ServiceProgressItem) -> Unit) {
    ServiceProgressNavigator(progress, null, true, {}, onSelect, rowTagPrefix = "visit-line")
}

@Composable
private fun ServiceIdentityBlock(
    draft: InspectionDraft,
    position: Int,
    total: Int,
    current: ServiceProgressItem?,
    fieldStates: Map<com.v16studio.serviceloop.ui.service.ServiceDraftFieldId, ServiceDraftFieldState>,
    saveStatus: SaveStatus,
    viewModel: ServiceLoopViewModel,
    nav: NavHostController,
) {
    ServiceLoopSurfaceCard(modifier = Modifier.testTag("service-identity")) {
        Text("Service $position of $total", style = MaterialTheme.typography.titleLarge)
        Text(serviceLoopSubjectLabel(draft.subjectType, draft.equipmentName, draft.equipmentReference, draft.equipmentDescription), style = MaterialTheme.typography.titleMedium)
        Text(draft.serviceName, style = MaterialTheme.typography.bodyLarge)
        Text("${draft.visitReference} · ${draft.siteName}", style = MaterialTheme.typography.bodyMedium)
        draft.dueDate?.takeIf(String::isNotBlank)?.let { Text("Due $it") }
        draft.interval?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (draft.subjectType == WorkSubjectType.EQUIPMENT && draft.equipmentId == null && current?.documentationMode == ServiceDocumentationMode.LOCAL) {
            ServiceLoopSecondaryButton("Link equipment", { nav.navigate("work/${draft.workItemId}/link-equipment") }, Modifier.fillMaxWidth().testTag("link-equipment"))
        }
        ServiceSaveState(draft, fieldStates, saveStatus, viewModel)
    }
}

@Composable
private fun ServiceSaveState(
    draft: InspectionDraft,
    fieldStates: Map<com.v16studio.serviceloop.ui.service.ServiceDraftFieldId, ServiceDraftFieldState>,
    saveStatus: SaveStatus,
    viewModel: ServiceLoopViewModel,
) {
    val states = fieldStates.filterKeys { it.workItemId == draft.workItemId }.values
    val failed = states.filterIsInstance<ServiceDraftFieldState.Failed>().firstOrNull()
    val invalid = states.filterIsInstance<ServiceDraftFieldState.Invalid>().firstOrNull()
    val rawUnsettled = draft.rawInputs.keys.any { key -> fieldStates[com.v16studio.serviceloop.ui.service.ServiceDraftFieldId(draft.workItemId, key)] !is ServiceDraftFieldState.Clean }
    when {
        failed != null || saveStatus is SaveStatus.Failed -> ServiceLoopNotice(
            "Not saved — retry",
            failed?.message ?: (saveStatus as? SaveStatus.Failed)?.message,
            ServiceLoopNoticeKind.Error,
            action = { ServiceLoopTextAction("Retry", { viewModel.retryFailedServiceEdits(draft.workItemId) }, Modifier.testTag("retry-service-edits")) },
        )
        invalid != null -> ServiceLoopNotice("Needs attention", invalid.message, ServiceLoopNoticeKind.Warning)
        rawUnsettled || states.any { it is ServiceDraftFieldState.Pending || it is ServiceDraftFieldState.Saving } || saveStatus is SaveStatus.Saving -> ServiceLoopNotice("Saving…", "The latest Service edit is being saved on this device.", ServiceLoopNoticeKind.Working)
        else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Saved", color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("service-save-state"))
            val savedAt = states.filterIsInstance<ServiceDraftFieldState.Clean>().mapNotNull { it.savedAtEpochMillis }.maxOrNull()
                ?: (saveStatus as? SaveStatus.Saved)?.atEpochMillis ?: draft.modifiedAtEpochMillis
            Text("Saved ${formatTime(savedAt)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DocumentationModeNotice(mode: ServiceDocumentationMode, onOpenAssignment: () -> Unit) {
    when (mode) {
        ServiceDocumentationMode.CHOICE_REQUIRED -> ServiceLoopNotice("Documentation choice required", "Choose whether to document this work on this device or hand it off from the Visit overview.", ServiceLoopNoticeKind.Warning, action = { ServiceLoopSecondaryButton("Open visit assignment", onOpenAssignment, Modifier.fillMaxWidth().testTag("open-visit-assignment")) })
        ServiceDocumentationMode.LEADER_OBSERVE -> ServiceLoopNotice("Leader view", "This work is visible because this Technician is a Visit leader. It is not being documented on this device.", ServiceLoopNoticeKind.Info, action = { ServiceLoopSecondaryButton("Open visit assignment", onOpenAssignment, Modifier.fillMaxWidth().testTag("open-visit-assignment")) })
        ServiceDocumentationMode.DEFERRED -> ServiceLoopNotice("Documentation handed off", "This Service is read-only on this device.", ServiceLoopNoticeKind.Info)
        ServiceDocumentationMode.LOCAL -> Unit
    }
}

@Composable
private fun PrivateWorkContext(rows: List<Pair<String, String>>) {
    ServiceLoopSurfaceCard(modifier = Modifier.testTag("private-work-context")) {
        Text("PRIVATE WORK CONTEXT", style = MaterialTheme.typography.labelLarge)
        rows.forEach { (label, value) ->
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value)
        }
    }
}

@Composable
private fun ChecklistSectionHeader(draft: InspectionDraft) {
    Column(Modifier.testTag("checklist-section"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Checklist", style = MaterialTheme.typography.titleLarge)
        if (draft.questions.isNotEmpty()) {
            Text("Required complete ${draft.requiredComplete} of ${draft.requiredTotal}")
            Text(if (draft.checklistComplete) "Checklist complete" else "Checklist needs attention", color = if (draft.checklistComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Text("Unanswered and Not checked are never treated as OK.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ChecklistCompletionSummary(draft: InspectionDraft) {
    if (draft.issueMissingDescription.isNotEmpty()) Text("Issue findings require a public description before checklist completion.", color = MaterialTheme.colorScheme.error)
    if (draft.invalidExplicitAnswers.isNotEmpty()) Text("A saved checklist answer needs attention.", color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ServiceQuestionBlock(workItemId: String, rawInputs: Map<String, String>, question: InspectionQuestion, editingEnabled: Boolean, viewModel: ServiceLoopViewModel) {
    val initialIssue = rawInputs[ServiceDraftFieldKeys.questionIssue(question.snapshotItemId)] ?: question.issueFoundReasonDraft ?: question.reason.takeIf { question.disposition == ResponseDisposition.ISSUE_FOUND }.orEmpty()
    val initialNotApplicable = rawInputs[ServiceDraftFieldKeys.questionNotApplicable(question.snapshotItemId)] ?: question.notApplicableReasonDraft ?: question.reason.takeIf { question.disposition == ResponseDisposition.NOT_APPLICABLE }.orEmpty()
    var issueBuffer by rememberSaveable("issue-${question.snapshotItemId}", initialIssue) { mutableStateOf(initialIssue) }
    var notApplicableBuffer by rememberSaveable("na-${question.snapshotItemId}", initialNotApplicable) { mutableStateOf(initialNotApplicable) }
    ServiceLoopSurfaceCard(modifier = Modifier.testTag("question-${question.snapshotItemId}")) {
        Text("${question.position}. ${question.label}", style = MaterialTheme.typography.titleMedium)
        Text(if (question.required) "Required · ${question.responseType.lowercase().replaceFirstChar(Char::uppercase)}" else "Optional · ${question.responseType.lowercase().replaceFirstChar(Char::uppercase)}", style = MaterialTheme.typography.bodySmall)
        question.privateGuidance?.takeIf(String::isNotBlank)?.let { Text("PRIVATE · $it", style = MaterialTheme.typography.bodySmall) }
        when (question.responseType) {
            "STATUS" -> StatusChoiceGrid(question, editingEnabled, viewModel, workItemId)
            else -> ValueQuestion(workItemId, rawInputs, question, editingEnabled, viewModel)
        }
        if (question.disposition == ResponseDisposition.ISSUE_FOUND) {
            Text("Finding · Customer report", style = MaterialTheme.typography.labelLarge)
            Text("Required for checklist completion.", style = MaterialTheme.typography.bodySmall)
            ServiceLoopLongTextEditor(issueBuffer, { issueBuffer = it; if (editingEnabled) viewModel.scheduleIssueDescription(workItemId, question.snapshotItemId, it) }, "Public finding description", false, enabled = editingEnabled, fieldTestTag = "long-text-public-finding-description", onFocusLost = { viewModel.flushServiceDraftAsync(workItemId) })
        }
        if (question.disposition == ResponseDisposition.NOT_APPLICABLE) {
            Text("Not applicable reason", style = MaterialTheme.typography.labelLarge)
            Text("Required for checklist completion.", style = MaterialTheme.typography.bodySmall)
            ServiceLoopLongTextEditor(notApplicableBuffer, { notApplicableBuffer = it; if (editingEnabled) viewModel.scheduleNotApplicableReason(workItemId, question.snapshotItemId, it) }, "Not applicable reason", false, enabled = editingEnabled, fieldTestTag = "not-applicable-reason-${question.snapshotItemId}", onFocusLost = { viewModel.flushServiceDraftAsync(workItemId) })
        }
    }
}

@Composable
private fun StatusChoiceGrid(question: InspectionQuestion, enabled: Boolean, viewModel: ServiceLoopViewModel, workItemId: String) {
    val choices = listOf(
        ResponseDisposition.OK to "OK",
        ResponseDisposition.ISSUE_FOUND to "Issue found",
        ResponseDisposition.NOT_APPLICABLE to "Not applicable",
        ResponseDisposition.NOT_CHECKED to "Not checked",
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stack = maxWidth < 360.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.25f
        if (stack) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { choices.forEach { StatusChoice(it, question, enabled, viewModel, workItemId) } }
        else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatusChoice(choices[0], question, enabled, viewModel, workItemId, Modifier.weight(1f)); StatusChoice(choices[1], question, enabled, viewModel, workItemId, Modifier.weight(1f)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatusChoice(choices[2], question, enabled, viewModel, workItemId, Modifier.weight(1f)); StatusChoice(choices[3], question, enabled, viewModel, workItemId, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun StatusChoice(choice: Pair<ResponseDisposition, String>, question: InspectionQuestion, enabled: Boolean, viewModel: ServiceLoopViewModel, workItemId: String, modifier: Modifier = Modifier) {
    ServiceLoopChoiceChip(
        selected = question.disposition == choice.first,
        onClick = { if (enabled) viewModel.chooseResponse(workItemId, question.snapshotItemId, choice.first) },
        label = choice.second,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("response-${question.snapshotItemId}-${choice.first.name}").semantics { role = Role.RadioButton; if (!enabled) disabled() },
    )
}

@Composable
private fun ValueQuestion(workItemId: String, rawInputs: Map<String, String>, question: InspectionQuestion, enabled: Boolean, viewModel: ServiceLoopViewModel) {
    val initialValue = rawInputs[ServiceDraftFieldKeys.questionValue(question.snapshotItemId)] ?: question.textValue ?: question.numberValue ?: ""
    var value by rememberSaveable("value-${question.snapshotItemId}", initialValue) { mutableStateOf(initialValue) }
    var wasFocused by remember { mutableStateOf(false) }
    val invalid = question.responseType == "NUMBER" && value.isNotBlank() && !com.v16studio.serviceloop.data.isFiniteSignedDecimal(value)
    ServiceLoopTextField(
        value = value,
        onValueChange = { value = it; if (enabled) viewModel.scheduleQuestionValue(workItemId, question.snapshotItemId, it) },
        label = if (question.responseType == "NUMBER") "Recorded value" else "Response",
        enabled = enabled,
        isError = invalid,
        supportingText = { Text(if (invalid) "Enter a signed decimal, for example -12.5" else question.unit.orEmpty()) },
        modifier = Modifier.testTag("value-${question.snapshotItemId}").onFocusChanged {
            if (wasFocused && !it.isFocused) viewModel.flushServiceDraftAsync(workItemId)
            wasFocused = it.isFocused
        },
    )
    ServiceLoopSecondaryButton("Not applicable", { viewModel.chooseResponse(workItemId, question.snapshotItemId, ResponseDisposition.NOT_APPLICABLE) }, Modifier.fillMaxWidth().testTag("not-applicable-${question.snapshotItemId}"), enabled = enabled)
}

private fun progressSummary(progress: VisitServiceProgress): String {
    val actionable = progress.actionableItems
    val values = listOf(
        actionable.count { it.status == ServiceEntryStatus.READY } to "ready",
        actionable.count { it.status == ServiceEntryStatus.IN_PROGRESS } to "in progress",
        actionable.count { it.status == ServiceEntryStatus.NEEDS_ATTENTION } to "needs attention",
        actionable.count { it.status == ServiceEntryStatus.NOT_STARTED } to "not started",
    ).filter { it.first > 0 }
    return values.joinToString(" · ") { "${it.first} ${it.second}" }.ifBlank { "No local service work" }
}

private fun ServiceProgressItem.navigationStatusLabel(): String = when (documentationMode) {
    ServiceDocumentationMode.CHOICE_REQUIRED -> "Choose documentation"
    ServiceDocumentationMode.LEADER_OBSERVE -> "Leader view"
    ServiceDocumentationMode.DEFERRED -> "Handed off"
    ServiceDocumentationMode.LOCAL -> when (status) {
        ServiceEntryStatus.NOT_STARTED -> "Not started"
        ServiceEntryStatus.IN_PROGRESS -> "In progress"
        ServiceEntryStatus.NEEDS_ATTENTION -> "Needs attention"
        ServiceEntryStatus.READY -> "Ready"
    }
}

private fun fallbackProgress(draft: InspectionDraft): VisitServiceProgress {
    val hasActivity = draft.workPerformed.isNotBlank() || draft.privateInternalNote.isNotBlank() ||
        draft.questions.any { it.disposition !in setOf(ResponseDisposition.UNANSWERED, ResponseDisposition.NOT_CHECKED) } ||
        draft.rawInputs.isNotEmpty() || draft.outcome != null || draft.fulfillsCurrentObligation != null
    val status = serviceEntryStatus(
        hasActivity = hasActivity,
        checklistComplete = draft.checklistComplete,
        hasUnresolvedRawBuffer = draft.rawInputs.isNotEmpty(),
        hasMissingIssueDescription = draft.issueMissingDescription.isNotEmpty(),
        hasInvalidExplicitAnswer = draft.invalidExplicitAnswers.isNotEmpty(),
    )
    val item = ServiceProgressItem(draft.workItemId, 1, draft.subjectType, draft.equipmentId, draft.equipmentName, draft.equipmentReference, draft.equipmentDescription, draft.serviceName, status, serviceDocumentationMode(draft.dispatchLocalRole, draft.dispatchDocumentationDisposition), draft.dispatchLocalRole, draft.dispatchDocumentationDisposition)
    return VisitServiceProgress(draft.visitId, draft.visitReference, draft.customerName, draft.siteName, "", listOf(item), serviceProgressGroups(listOf(item)))
}
