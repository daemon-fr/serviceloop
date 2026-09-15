package com.v16studio.serviceloop.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.domain.CompletionBlockerKind
import com.v16studio.serviceloop.domain.CompletionLine
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.InspectionQuestion
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.domain.SaveStatus
import com.v16studio.serviceloop.domain.ServiceDocumentationMode
import com.v16studio.serviceloop.domain.ServiceDraftFieldKeys
import com.v16studio.serviceloop.domain.ServiceEntryStatus
import com.v16studio.serviceloop.domain.VisitServiceProgress
import com.v16studio.serviceloop.domain.serviceEntryStatus
import com.v16studio.serviceloop.ui.service.ServiceDraftFieldId
import com.v16studio.serviceloop.ui.service.ServiceDraftFieldState
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopLongTextEditor
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNotice
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNoticeKind
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPinnedBar
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrivateLabel
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSecondaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSurfaceCard
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextAction
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ServiceListIndices(
    val workPerformed: Int,
    val privateNote: Int,
    val checklistHeader: Int,
    val firstQuestion: Int,
    val evidence: Int,
)

internal fun serviceListIndices(
    hasNavigationMessage: Boolean,
    hasDocumentationNotice: Boolean,
    hasPrivateContext: Boolean,
    questionCount: Int = 0,
): ServiceListIndices {
    var next = 1 // Service identity block
    if (hasNavigationMessage) next++
    next++ // Visit progress
    if (hasDocumentationNotice) next++
    if (hasPrivateContext) next++
    val workPerformed = next++
    val privateNote = next++
    val checklistHeader = next++
    val firstQuestion = next
    val questionItems = if (questionCount == 0) 1 else questionCount
    val evidence = firstQuestion + questionItems + 1
    return ServiceListIndices(workPerformed, privateNote, checklistHeader, firstQuestion, evidence)
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

private fun focusForRawServiceField(workItemId: String, fieldKey: String): InspectionFocus = when {
    fieldKey == ServiceDraftFieldKeys.WORK -> InspectionFocus(CompletionBlockerKind.WORK_PERFORMED, workItemId = workItemId, fieldKey = fieldKey, attention = true)
    fieldKey == ServiceDraftFieldKeys.PRIVATE -> InspectionFocus(CompletionBlockerKind.WORK_PERFORMED, workItemId = workItemId, fieldKey = fieldKey, attention = true)
    fieldKey == ServiceDraftFieldKeys.NOT_PERFORMED_REASON -> InspectionFocus(CompletionBlockerKind.NOT_PERFORMED_REASON, workItemId = workItemId, fieldKey = fieldKey, attention = true)
    fieldKey == ServiceDraftFieldKeys.OVERRIDE_DATE || fieldKey == ServiceDraftFieldKeys.OVERRIDE_REASON -> InspectionFocus(CompletionBlockerKind.NEXT_DUE, workItemId = workItemId, fieldKey = fieldKey, attention = true)
    ServiceDraftFieldKeys.parsePhotoCaption(fieldKey) != null -> InspectionFocus(CompletionBlockerKind.WORK_PERFORMED, workItemId = workItemId, fieldKey = fieldKey, attention = true)
    ServiceDraftFieldKeys.parseQuestionField(fieldKey) != null -> {
        val parsed = ServiceDraftFieldKeys.parseQuestionField(fieldKey)!!
        InspectionFocus(CompletionBlockerKind.FINDING_DESCRIPTION, parsed.snapshotItemId, workItemId, fieldKey, attention = true)
    }
    else -> InspectionFocus(CompletionBlockerKind.WORK_PERFORMED, workItemId = workItemId, fieldKey = fieldKey, attention = true)
}

internal fun resolveAutomaticInspectionFocus(
    draft: InspectionDraft,
    fieldStates: Map<ServiceDraftFieldId, ServiceDraftFieldState>,
    completion: CompletionLine?,
): InspectionFocus {
    val workItemId = draft.workItemId
    val localStates = fieldStates.filterKeys { it.workItemId == workItemId }
    val severeKeys = localStates.filterValues { it is ServiceDraftFieldState.Failed || it is ServiceDraftFieldState.Invalid }
        .keys.map(ServiceDraftFieldId::fieldKey).toSet()
    val unresolvedKeys = (localStates.filterValues { it !is ServiceDraftFieldState.Clean }
        .keys.map(ServiceDraftFieldId::fieldKey) + draft.rawInputs.keys).toSet()
    val questionPositions = draft.questions.mapIndexed { index, question -> question.snapshotItemId to index }.toMap()
    val candidates = linkedMapOf<String, AutomaticInspectionFocusCandidate>()

    fun addCandidate(fieldKey: String, severe: Boolean = fieldKey in severeKeys) {
        val candidate = automaticInspectionFocusCandidate(draft, fieldKey, severe) ?: return
        val previous = candidates[fieldKey]
        if (previous == null || candidate.severe && !previous.severe) candidates[fieldKey] = candidate
    }

    unresolvedKeys.forEach(::addCandidate)
    draft.questions.forEach { question ->
        val activeFieldKey = activeQuestionFieldKey(question) ?: return@forEach
        val missingIssue = question.snapshotItemId in draft.issueMissingDescription && question.disposition == ResponseDisposition.ISSUE_FOUND
        val missingNotApplicable = question.disposition == ResponseDisposition.NOT_APPLICABLE && question.reason.isNullOrBlank()
        val invalidValue = question.snapshotItemId in draft.invalidExplicitAnswers && question.disposition == ResponseDisposition.VALUE
        if (missingIssue || missingNotApplicable || invalidValue) addCandidate(activeFieldKey)
    }

    val selectedCandidate = candidates.values
        .filter { it.severe }
        .ifEmpty { candidates.values }
        .minWithOrNull(compareBy<AutomaticInspectionFocusCandidate>({ it.sectionOrder }, { it.itemOrder }, { it.fieldOrder }, { it.fieldKey }))
    selectedCandidate?.let { return focusForRawServiceField(workItemId, it.fieldKey) }

    draft.issueMissingDescription
        .firstOrNull { it !in questionPositions }
        ?.let { questionId ->
            return InspectionFocus(CompletionBlockerKind.FINDING_DESCRIPTION, questionId, workItemId, ServiceDraftFieldKeys.questionIssue(questionId), attention = true)
        }
    draft.invalidExplicitAnswers
        .firstOrNull { it !in questionPositions }
        ?.let { questionId ->
            return InspectionFocus(CompletionBlockerKind.FINDING_DESCRIPTION, questionId, workItemId, ServiceDraftFieldKeys.questionValue(questionId), attention = true)
        }
    completion?.blockers?.firstOrNull()?.let { blocker ->
        return InspectionFocus(blocker.kind, blocker.questionId, workItemId, attention = true)
    }
    return InspectionFocus(CompletionBlockerKind.WORK_PERFORMED, workItemId = workItemId, attention = true)
}

private data class AutomaticInspectionFocusCandidate(
    val fieldKey: String,
    val sectionOrder: Int,
    val itemOrder: Int,
    val fieldOrder: Int,
    val severe: Boolean,
)

private fun activeQuestionFieldKey(question: InspectionQuestion): String? = when (question.disposition) {
    ResponseDisposition.VALUE -> ServiceDraftFieldKeys.questionValue(question.snapshotItemId)
    ResponseDisposition.ISSUE_FOUND -> ServiceDraftFieldKeys.questionIssue(question.snapshotItemId)
    ResponseDisposition.NOT_APPLICABLE -> ServiceDraftFieldKeys.questionNotApplicable(question.snapshotItemId)
    else -> null
}

private fun automaticInspectionFocusCandidate(
    draft: InspectionDraft,
    fieldKey: String,
    severe: Boolean,
): AutomaticInspectionFocusCandidate? {
    val questionField = ServiceDraftFieldKeys.parseQuestionField(fieldKey)
    if (questionField != null) {
        val questionIndex = draft.questions.indexOfFirst { it.snapshotItemId == questionField.snapshotItemId }
        if (questionIndex >= 0) {
            if (activeQuestionFieldKey(draft.questions[questionIndex]) != fieldKey) return null
            return AutomaticInspectionFocusCandidate(fieldKey, sectionOrder = 2, itemOrder = questionIndex, fieldOrder = questionField.kind.ordinal, severe = severe)
        }
        return AutomaticInspectionFocusCandidate(fieldKey, sectionOrder = 5, itemOrder = 0, fieldOrder = 0, severe = severe)
    }
    return when {
        fieldKey == ServiceDraftFieldKeys.WORK -> AutomaticInspectionFocusCandidate(fieldKey, 0, 0, 0, severe)
        fieldKey == ServiceDraftFieldKeys.PRIVATE -> AutomaticInspectionFocusCandidate(fieldKey, 1, 0, 0, severe)
        ServiceDraftFieldKeys.parsePhotoCaption(fieldKey) != null -> AutomaticInspectionFocusCandidate(fieldKey, 3, 0, 0, severe)
        fieldKey == ServiceDraftFieldKeys.NOT_PERFORMED_REASON -> AutomaticInspectionFocusCandidate(fieldKey, 4, 0, 0, severe)
        fieldKey == ServiceDraftFieldKeys.OVERRIDE_DATE -> AutomaticInspectionFocusCandidate(fieldKey, 4, 0, 1, severe)
        fieldKey == ServiceDraftFieldKeys.OVERRIDE_REASON -> AutomaticInspectionFocusCandidate(fieldKey, 4, 0, 2, severe)
        else -> AutomaticInspectionFocusCandidate(fieldKey, 5, 0, 0, severe)
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
    var navigationMessage by rememberSaveable(draft.workItemId) { mutableStateOf<String?>(null) }
    var showLeaveDialog by rememberSaveable(draft.workItemId) { mutableStateOf(false) }
    var leaveFlushResult by remember { mutableStateOf<com.v16studio.serviceloop.ui.service.ServiceDraftFlushResult?>(null) }
    val current = resolvedProgress.items.firstOrNull { it.workItemId == draft.workItemId }
    val currentPosition = current?.position ?: 1
    val next = resolvedProgress.nextService(draft.workItemId)
    val reviewAvailable = resolvedProgress.actionableItems.isNotEmpty() && resolvedProgress.actionableItems.all { it.status == ServiceEntryStatus.READY }
    val editingEnabled = current?.documentationMode != ServiceDocumentationMode.CHOICE_REQUIRED &&
        current?.documentationMode != ServiceDocumentationMode.LEADER_OBSERVE && current?.documentationMode != ServiceDocumentationMode.DEFERRED
    val contextRows = listOf(
        "Site access" to draft.siteAccessNote,
        "Equipment" to draft.equipmentPrivateNote,
        "Dispatch" to draft.dispatchInstructions.orEmpty(),
    ).filter { it.second.isNotBlank() }
    val completion = viewState.completionLines.firstOrNull { it.workItemId == draft.workItemId }
    val outcomeRequester = remember(draft.workItemId) { BringIntoViewRequester() }
    val reasonRequester = remember(draft.workItemId) { BringIntoViewRequester() }
    val fulfillmentRequester = remember(draft.workItemId) { BringIntoViewRequester() }
    val nextDueRequester = remember(draft.workItemId) { BringIntoViewRequester() }
    LaunchedEffect(draft.workItemId) {
        viewModel.loadFieldEvidence(draft.workItemId)
        viewModel.loadCompletion(draft.visitId)
    }
    val listIndices = serviceListIndices(
        hasNavigationMessage = navigationMessage != null,
        hasDocumentationNotice = current?.documentationMode != ServiceDocumentationMode.LOCAL,
        hasPrivateContext = contextRows.isNotEmpty(),
        questionCount = draft.questions.size,
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
    LaunchedEffect(draft.workItemId, focus?.requestId, listIndices, completion?.outcome, completion?.fulfillsCurrentObligation, completion?.confirmedNextDueDate, draft.rawInputs, fieldStates) {
        val requested = focus?.takeIf { it.workItemId == null || it.workItemId == draft.workItemId }
        if (requested?.attention == true && completion == null) return@LaunchedEffect
        requested?.let { requestedTarget ->
            val target = if (requestedTarget.attention) resolveAutomaticInspectionFocus(draft, fieldStates, completion) else requestedTarget
            if (target.kind in setOf(CompletionBlockerKind.OUTCOME, CompletionBlockerKind.NOT_PERFORMED_REASON, CompletionBlockerKind.NEXT_DUE) && completion == null) return@let
            val unansweredPartlyFulfillment = completion?.outcome == "PARTLY_PERFORMED" && completion.fulfillsCurrentObligation == null
            val missingConfirmedNextDue = completion?.fulfillsCurrentObligation == true && completion.confirmedNextDueDate == null
            if (target.kind == CompletionBlockerKind.NEXT_DUE && !unansweredPartlyFulfillment && !missingConfirmedNextDue) return@let
            val rawQuestion = target.fieldKey?.let(ServiceDraftFieldKeys::parseQuestionField)
            val index = when {
                target.fieldKey == ServiceDraftFieldKeys.PRIVATE -> listIndices.privateNote
                target.fieldKey != null && ServiceDraftFieldKeys.parsePhotoCaption(target.fieldKey) != null -> listIndices.evidence
                rawQuestion != null -> draft.questions.indexOfFirst { it.snapshotItemId == rawQuestion.snapshotItemId }
                    .takeIf { it >= 0 }
                    ?.let { listIndices.firstQuestion + it }
                    ?: listIndices.checklistHeader
                else -> when (target.kind) {
                    CompletionBlockerKind.WORK_PERFORMED -> listIndices.workPerformed
                    CompletionBlockerKind.CHECKLIST_INCOMPLETE -> listIndices.checklistHeader
                    CompletionBlockerKind.FINDING_DESCRIPTION -> draft.questions.indexOfFirst { it.snapshotItemId == target.questionId }
                        .takeIf { it >= 0 }
                        ?.let { listIndices.firstQuestion + it }
                        ?: listIndices.checklistHeader
                    CompletionBlockerKind.OUTCOME, CompletionBlockerKind.NOT_PERFORMED_REASON, CompletionBlockerKind.NEXT_DUE -> listIndices.firstQuestion + (if (draft.questions.isEmpty()) 1 else draft.questions.size) + 2
                }
            }
            val targetIndex = index.coerceAtLeast(0)
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > targetIndex }
            listState.scrollToItem(targetIndex)
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex } }.first { it }
            withFrameNanos { }
            when (target.kind) {
                CompletionBlockerKind.OUTCOME -> outcomeRequester.bringIntoView()
                CompletionBlockerKind.NOT_PERFORMED_REASON -> reasonRequester.bringIntoView()
                CompletionBlockerKind.NEXT_DUE -> if (unansweredPartlyFulfillment) fulfillmentRequester.bringIntoView() else nextDueRequester.bringIntoView()
                else -> Unit
            }
            viewModel.clearInspectionFocus(requestedTarget.requestId)
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Service edit still pending") },
            text = { Text("The unfinished edit is kept on this device and will be restored. Retry before leaving when possible.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.retryFailedServiceEdits(draft.workItemId)
                    showLeaveDialog = false
                    leaveService()
                }) { Text("Retry") }
            },
             dismissButton = { androidx.compose.material3.TextButton(onClick = { showLeaveDialog = false; openVisitOverview(nav, draft.visitId) }) { Text("Leave service") } },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val movePrimaryActionIntoList = maxHeight < ServiceLoopUiTokens.Size.compactHeightThreshold || imeVisible

        @Composable
        fun PrimaryServiceAction(modifier: Modifier = Modifier) {
            if (next != null) {
                ServiceLoopPrimaryButton("Next service", { flushAndThen { replaceServiceDestination(nav, next.workItemId) } }, modifier.testTag("next-service"))
            } else {
                ServiceLoopPrimaryButton(
                    "Review visit",
                    {
                        scope.launch {
                            val result = runCatching { viewModel.flushVisitDraft(draft.visitId) }.getOrNull()
                            if (result?.success == true) withContext(Dispatchers.Main.immediate) { nav.navigate("review/${draft.visitId}") } else navigationMessage = "Resolve unsaved service edits before continuing."
                        }
                    },
                    modifier.testTag("review-visit"),
                    enabled = reviewAvailable,
                )
            }
        }

        Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).imePadding().testTag(listTag),
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
                    onSelect = { target ->
                        if (target.workItemId == draft.workItemId) viewModel.focusService(draft.workItemId)
                        else flushAndThen { replaceServiceDestination(nav, target.workItemId) }
                    },
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
                 var privateExpanded by remember(draft.workItemId, initialPrivate) { mutableStateOf(initialPrivate.isNotBlank()) }
                 var privateNote by remember(draft.workItemId, initialPrivate) { mutableStateOf(initialPrivate) }
                if (!privateExpanded) {
                    ServiceLoopTextAction("+ Private note", { privateExpanded = true }, Modifier.testTag("add-private-note"), enabled = editingEnabled)
                } else {
                    ServiceLoopSurfaceCard(modifier = Modifier.testTag("private-work-note")) {
                        ServiceLoopPrivateLabel("Private note", style = MaterialTheme.typography.titleMedium)
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
            }
            item { ServiceCompletionLandmark() }
            item { ServiceEvidenceContent(draft.workItemId, if (viewState.fieldEvidenceWorkItemId == draft.workItemId) viewState else viewState.copy(parts = emptyList(), photos = emptyList(), serviceFollowUps = emptyList()), viewModel, editingEnabled) }
            if (completion != null) item { ServiceCompletionSection(completion, draft, viewModel, editingEnabled, outcomeRequester, reasonRequester, fulfillmentRequester, nextDueRequester) }
            if (viewState.contentRefreshError != null) item {
                Text(viewState.contentRefreshError!!, color = MaterialTheme.colorScheme.error)
            }
            if (viewState.error != null) item { Text(viewState.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("service-operation-error")) }
            if (movePrimaryActionIntoList) item { PrimaryServiceAction(Modifier.fillMaxWidth()) }
        }
        ServiceLoopPinnedBar {
            ServiceLoopSecondaryButton("Back to visit", { leaveService() }, Modifier.fillMaxWidth().testTag("service-visit-overview"))
            if (!movePrimaryActionIntoList) PrimaryServiceAction(Modifier.fillMaxWidth())
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
