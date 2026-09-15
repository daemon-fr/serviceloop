package com.v16studio.serviceloop.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.InspectionQuestion
import com.v16studio.serviceloop.domain.CompletionBlockerKind
import com.v16studio.serviceloop.domain.CompletionLine
import com.v16studio.serviceloop.domain.FulfillmentEligibility
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
import com.v16studio.serviceloop.data.isFiniteSignedDecimal
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChecklistChoice
import com.v16studio.serviceloop.ui.designsystem.InspectionStatusChoice
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopInspectionStatusGrid
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrivateLabel
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChoiceGroup
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSelectionOption
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
import com.v16studio.serviceloop.ui.designsystem.serviceLoopFocusRing
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons
import com.v16studio.serviceloop.ui.service.ServiceDraftFieldState
import com.v16studio.serviceloop.ui.service.ServiceDraftFieldId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate

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
                TextButton(onClick = {
                    viewModel.retryFailedServiceEdits(draft.workItemId)
                    showLeaveDialog = false
                    leaveService()
                }) { Text("Retry") }
            },
             dismissButton = { TextButton(onClick = { showLeaveDialog = false; openVisitOverview(nav, draft.visitId) }) { Text("Leave service") } },
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
private fun ServiceCompletionSection(line: CompletionLine, draft: InspectionDraft, viewModel: ServiceLoopViewModel, editingEnabled: Boolean, outcomeRequester: BringIntoViewRequester, reasonRequester: BringIntoViewRequester, fulfillmentRequester: BringIntoViewRequester, nextDueRequester: BringIntoViewRequester) {
    val visitId = draft.visitId
    val workItemId = draft.workItemId
    ServiceLoopSurfaceCard(modifier = Modifier.fillMaxWidth().testTag("service-outcome")) {
        Text("Outcome", style = MaterialTheme.typography.titleLarge)
        ServiceLoopChoiceGroup(
            options = listOf("PERFORMED" to "Performed", "PARTLY_PERFORMED" to "Partly performed", "NOT_PERFORMED" to "Not performed"),
            selected = line.outcome.orEmpty(),
            onSelected = { viewModel.chooseOutcome(workItemId, visitId, it) },
            enabled = editingEnabled,
            testTagPrefix = "outcome-$workItemId",
            modifier = Modifier.bringIntoViewRequester(outcomeRequester),
        )
        if (line.outcome == "NOT_PERFORMED") {
            val initial = draft.rawInputs[ServiceDraftFieldKeys.NOT_PERFORMED_REASON] ?: line.notPerformedReason.orEmpty()
            var reason by remember(workItemId, initial) { mutableStateOf(initial) }
            OutlinedTextField(reason, { reason = it; viewModel.scheduleNotPerformedReason(workItemId, visitId, it) }, label = { Text("Not performed reason") }, enabled = editingEnabled, modifier = Modifier.fillMaxWidth().bringIntoViewRequester(reasonRequester).testTag("not-performed-reason"))
        }
        if (line.outcome != null) {
            when (line.fulfillmentEligibility) {
                FulfillmentEligibility.ELIGIBLE -> {
                    if (line.outcome == "PARTLY_PERFORMED") {
                        Text("Does this complete the due service?", style = MaterialTheme.typography.titleMedium)
                        Column(Modifier.bringIntoViewRequester(fulfillmentRequester), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)) {
                            ServiceLoopSelectionOption(
                                selected = line.fulfillsCurrentObligation == true,
                                onClick = { viewModel.chooseFulfillment(workItemId, visitId, true) },
                                label = "Fulfill — advance next due",
                                enabled = editingEnabled,
                                modifier = Modifier.testTag("fulfill-$workItemId"),
                            )
                            ServiceLoopSelectionOption(
                                selected = line.fulfillsCurrentObligation == false,
                                onClick = { viewModel.chooseFulfillment(workItemId, visitId, false) },
                                label = "Keep due — service remains outstanding",
                                enabled = editingEnabled,
                                modifier = Modifier.testTag("keep-due-$workItemId"),
                            )
                        }
                    }
                }
                FulfillmentEligibility.HISTORY_ONLY -> Text("History only — current due date is unchanged.")
                FulfillmentEligibility.NO_CURRENT_OBLIGATION -> Text("No recurring due date for this Service.")
                FulfillmentEligibility.CHECKLIST_INCOMPLETE -> Text("Complete all required checklist questions")
                FulfillmentEligibility.PLAN_INELIGIBLE -> Text("This plan is no longer active; it cannot advance the due date.")
                FulfillmentEligibility.CURRENT_OBLIGATION_CHANGED -> Text("The current obligation changed; review this Service before finalizing.")
                FulfillmentEligibility.OUTCOME_INELIGIBLE -> Unit
            }
            if (line.fulfillsCurrentObligation == true && line.confirmedNextDueDate != null) {
                Text("Next due · ${formatServiceLoopDate(line.confirmedNextDueDate)}", style = MaterialTheme.typography.titleMedium)
                if (line.nextDueDateCalculated == true) Text("Calculated next due")
                else Text("Manual override · ${line.nextDueOverrideReason.orEmpty()}")
                var changeDue by rememberSaveable(workItemId) { mutableStateOf(false) }
                if (!changeDue && editingEnabled) ServiceLoopTextAction("Change next due", { changeDue = true }, Modifier.testTag("change-next-due"))
                if (changeDue && editingEnabled) {
                    val initialDate = draft.rawInputs[ServiceDraftFieldKeys.OVERRIDE_DATE] ?: line.confirmedNextDueDate
                    val initialReason = draft.rawInputs[ServiceDraftFieldKeys.OVERRIDE_REASON] ?: line.nextDueOverrideReason.orEmpty()
                    var date by remember(workItemId, initialDate) { mutableStateOf(initialDate) }
                    var reason by remember(workItemId, initialReason) { mutableStateOf(initialReason) }
                    OutlinedTextField(date, { date = it; viewModel.scheduleRecurrenceOverrideDate(workItemId, it) }, label = { Text("Next due (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth().testTag("override-date"))
                    OutlinedTextField(reason, { reason = it; viewModel.scheduleRecurrenceOverrideReason(workItemId, it) }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth().testTag("override-reason"))
                    val validDate = runCatching { LocalDate.parse(date).isAfter(LocalDate.parse(viewModel.state.value.serviceProgress?.serviceDate ?: "")) }.getOrDefault(false)
                    ServiceLoopPrimaryButton("Apply override", { viewModel.applyRecurrenceOverride(workItemId, visitId, date, reason); changeDue = false }, Modifier.fillMaxWidth().testTag("apply-override"), enabled = validDate && reason.isNotBlank())
                    ServiceLoopTextAction("Cancel", { changeDue = false })
                }
            } else if (line.fulfillsCurrentObligation == true && line.confirmedNextDueDate == null) {
                val hasOverrideDraft = draft.rawInputs.containsKey(ServiceDraftFieldKeys.OVERRIDE_DATE) ||
                    draft.rawInputs.containsKey(ServiceDraftFieldKeys.OVERRIDE_REASON)
                Column(
                    Modifier.fillMaxWidth().bringIntoViewRequester(nextDueRequester).testTag("missing-next-due-resolution"),
                    verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm),
                ) {
                    Text("Next due needs confirmation", style = MaterialTheme.typography.titleMedium)
                    line.calculatedNextDueDate?.let { Text("Calculated date · ${formatServiceLoopDate(it)} (not saved)") }
                        ?: Text("A calculated date is not available for this Service.")
                    if (hasOverrideDraft && editingEnabled) {
                        var date by remember(workItemId, draft.rawInputs[ServiceDraftFieldKeys.OVERRIDE_DATE]) {
                            mutableStateOf(draft.rawInputs[ServiceDraftFieldKeys.OVERRIDE_DATE].orEmpty())
                        }
                        var reason by remember(workItemId, draft.rawInputs[ServiceDraftFieldKeys.OVERRIDE_REASON]) {
                            mutableStateOf(draft.rawInputs[ServiceDraftFieldKeys.OVERRIDE_REASON].orEmpty())
                        }
                        OutlinedTextField(date, { date = it; viewModel.scheduleRecurrenceOverrideDate(workItemId, it) }, label = { Text("Next due (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth().testTag("override-date"))
                        OutlinedTextField(reason, { reason = it; viewModel.scheduleRecurrenceOverrideReason(workItemId, it) }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth().testTag("override-reason"))
                        val validDate = runCatching { LocalDate.parse(date).isAfter(LocalDate.parse(viewModel.state.value.serviceProgress?.serviceDate ?: "")) }.getOrDefault(false)
                        ServiceLoopPrimaryButton("Apply override", { viewModel.applyRecurrenceOverride(workItemId, visitId, date, reason) }, Modifier.fillMaxWidth().testTag("apply-override"), enabled = validDate && reason.isNotBlank())
                    } else if (editingEnabled && line.calculatedNextDueDate != null) {
                        ServiceLoopTextAction(
                            "Use calculated date",
                            { viewModel.useCalculatedNextDue(workItemId, visitId) },
                            Modifier.testTag("use-calculated-next-due"),
                        )
                    }
                }
            } else if (line.currentObligationOutstanding && line.dueDate != null) {
                Text("Remains due · ${formatServiceLoopDate(line.dueDate)}")
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
    onSelect: (ServiceProgressItem) -> Unit,
    rowTagPrefix: String = "service-row",
) {
    val currentGroupKey = currentWorkItemId?.let { progress.groupFor(it)?.key }
    val defaultExpandedKeys = if (currentWorkItemId == null) {
        progress.groups.map { it.key }
    } else {
        listOfNotNull(currentGroupKey)
    }
    var expandedGroupKeys by rememberSaveable(progress.visitId, currentWorkItemId) {
        mutableStateOf(defaultExpandedKeys)
    }

    ServiceLoopSurfaceCard(modifier = Modifier.testTag("visit-progress")) {
        Text("Visit progress", style = MaterialTheme.typography.titleLarge)
        Text(progressSummary(progress), modifier = Modifier.testTag("visit-progress-summary"))
        progress.groups.forEachIndexed { groupIndex, group ->
            val groupTag = serviceProgressGroupTag(group.key)
            val expanded = group.key in expandedGroupKeys
            Column(
                Modifier.fillMaxWidth().testTag("service-group-$groupTag"),
                verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs),
            ) {
                Text(
                    group.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = if (groupIndex == 0) ServiceLoopUiTokens.Space.sm else ServiceLoopUiTokens.Space.md),
                )
                ServiceProgressGroupToggle(
                    group = group,
                    expanded = expanded,
                    onToggle = {
                        expandedGroupKeys = if (expanded) expandedGroupKeys - group.key else expandedGroupKeys + group.key
                    },
                    modifier = Modifier.testTag("service-group-toggle-$groupTag"),
                )
                if (expanded) {
                    group.items.forEachIndexed { index, item ->
                        val selected = item.workItemId == currentWorkItemId
                        ServiceLoopDenseNavigableRow(
                            title = item.serviceName,
                            context = null,
                            statusContent = { ServiceProgressBadge(item) },
                            modifier = Modifier.padding(horizontal = ServiceLoopUiTokens.Space.sm).testTag("$rowTagPrefix-${item.workItemId}"),
                            selected = selected,
                            showDisclosure = true,
                            contentPadding = PaddingValues(horizontal = ServiceLoopUiTokens.Space.md, vertical = ServiceLoopUiTokens.Space.md),
                            onClick = { onSelect(item) },
                            showDivider = index < group.items.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun VisitServiceProgressOverview(progress: VisitServiceProgress, onSelect: (ServiceProgressItem) -> Unit) {
    ServiceProgressNavigator(progress, null, onSelect, rowTagPrefix = "visit-line")
}

@Composable
private fun ServiceProgressGroupToggle(
    group: ServiceProgressGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalServiceLoopTokens.current
    val attentionCount = group.items.count {
        it.documentationMode in setOf(ServiceDocumentationMode.LOCAL, ServiceDocumentationMode.CHOICE_REQUIRED) && it.status == ServiceEntryStatus.NEEDS_ATTENTION
    }
    val actionWord = if (expanded) "Hide" else "Show"
    val accessibleName = "$actionWord services (${group.items.size})${if (attentionCount > 0) " — $attentionCount ${if (attentionCount == 1) "needs" else "need"} attention" else ""} for ${group.label}"
    val label = buildAnnotatedString {
        append("$actionWord services (${group.items.size})")
        if (attentionCount > 0) {
            append(" — ")
            withStyle(androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic)) {
                append("$attentionCount ${if (attentionCount == 1) "needs" else "need"} attention")
            }
        }
    }
    Row(
        modifier = modifier.fillMaxWidth()
            .heightIn(min = ServiceLoopUiTokens.Size.touchMin)
            .serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics {
                contentDescription = accessibleName
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
            .padding(horizontal = ServiceLoopUiTokens.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs),
    ) {
        ServiceLoopIcon(
            if (expanded) ServiceLoopIcons.CaretDown else ServiceLoopIcons.CaretRight,
            null,
            Modifier.size(ServiceLoopUiTokens.Size.icon),
            c.icon,
        )
        Text(label, style = ServiceLoopUiTokens.Type.label, color = c.textSecondary, modifier = Modifier.weight(1f))
    }
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
        else -> {
            val savedAt = states.filterIsInstance<ServiceDraftFieldState.Clean>().mapNotNull { it.savedAtEpochMillis }.maxOrNull()
                ?: (saveStatus as? SaveStatus.Saved)?.atEpochMillis ?: draft.modifiedAtEpochMillis
            Text(if (savedAt > 0) "Saved · ${formatTime(savedAt)}" else "Saved", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("service-save-state"))
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
        ServiceLoopPrivateLabel("PRIVATE WORK CONTEXT", style = MaterialTheme.typography.labelLarge)
        rows.forEach { (label, value) ->
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value)
        }
    }
}

@Composable
private fun ServiceCompletionLandmark() {
    val colors = LocalServiceLoopTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("service-completion-landmark"),
        color = colors.surfaceSubtle,
        shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field),
    ) {
        Text("Service completion", style = ServiceLoopUiTokens.Type.sectionTitle, modifier = Modifier.padding(horizontal = ServiceLoopUiTokens.Space.md, vertical = ServiceLoopUiTokens.Space.sm))
    }
}

@Composable
private fun ChecklistSectionHeader(draft: InspectionDraft) {
    val colors = LocalServiceLoopTokens.current
    Column(Modifier.testTag("checklist-section"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Checklist", style = MaterialTheme.typography.titleLarge)
        if (draft.questions.isNotEmpty()) {
            Text("Required complete ${draft.requiredComplete} of ${draft.requiredTotal}", color = if (draft.checklistComplete) colors.successInk else colors.errorInk)
        }
    }
}

@Composable
private fun ServiceQuestionBlock(workItemId: String, rawInputs: Map<String, String>, question: InspectionQuestion, editingEnabled: Boolean, viewModel: ServiceLoopViewModel) {
    val initialIssue = rawInputs[ServiceDraftFieldKeys.questionIssue(question.snapshotItemId)] ?: question.issueFoundReasonDraft ?: question.reason.takeIf { question.disposition == ResponseDisposition.ISSUE_FOUND }.orEmpty()
    val initialNotApplicable = rawInputs[ServiceDraftFieldKeys.questionNotApplicable(question.snapshotItemId)] ?: question.notApplicableReasonDraft ?: question.reason.takeIf { question.disposition == ResponseDisposition.NOT_APPLICABLE }.orEmpty()
    var issueBuffer by rememberSaveable("issue-${question.snapshotItemId}", initialIssue) { mutableStateOf(initialIssue) }
    var notApplicableBuffer by rememberSaveable("na-${question.snapshotItemId}", initialNotApplicable) { mutableStateOf(initialNotApplicable) }
    ServiceLoopSurfaceCard(modifier = Modifier.testTag("question-${question.snapshotItemId}")) {
        ServiceQuestionHeader(question)
        Text(
            if (question.required) "Required · ${question.responseType.lowercase().replaceFirstChar(Char::uppercase)}" else "Optional · ${question.responseType.lowercase().replaceFirstChar(Char::uppercase)}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("question-${question.snapshotItemId}-metadata"),
        )
        question.privateGuidance?.takeIf(String::isNotBlank)?.let {
            ServiceLoopPrivateLabel("PRIVATE · $it", modifier = Modifier.testTag("question-${question.snapshotItemId}-private-guidance"))
        }
        when (question.responseType) {
            "STATUS" -> StatusChoiceGrid(question, editingEnabled, viewModel, workItemId)
            else -> ValueQuestion(workItemId, rawInputs, question, editingEnabled, viewModel)
        }
        if (question.disposition == ResponseDisposition.ISSUE_FOUND) {
            Text("Finding · Customer report", style = MaterialTheme.typography.labelLarge)
            Text("Required for checklist completion.", style = MaterialTheme.typography.bodySmall)
            ServiceLoopLongTextEditor(issueBuffer, { issueBuffer = it; if (editingEnabled) viewModel.scheduleIssueDescription(workItemId, question.snapshotItemId, it) }, "Public finding description", false, enabled = editingEnabled, fieldTestTag = "long-text-public-finding-description", onFocusLost = { viewModel.flushServiceDraftAsync(workItemId) })
        }
        if (question.disposition == ResponseDisposition.NOT_APPLICABLE && (question.required || question.responseType != "TEXT")) {
            Text("Not applicable reason", style = MaterialTheme.typography.labelLarge)
            Text("Required for checklist completion.", style = MaterialTheme.typography.bodySmall)
            ServiceLoopLongTextEditor(notApplicableBuffer, { notApplicableBuffer = it; if (editingEnabled) viewModel.scheduleNotApplicableReason(workItemId, question.snapshotItemId, it) }, "Not applicable reason", false, enabled = editingEnabled, fieldTestTag = "not-applicable-reason-${question.snapshotItemId}", onFocusLost = { viewModel.flushServiceDraftAsync(workItemId) })
        }
    }
}

/**
 * The resolved indicator owns a real, always-present slot. Keeping that slot in the
 * measured Row makes the header height independent of the resolved state while the
 * empty slot remains semantics-free.
 */
@Composable
internal fun ServiceQuestionHeader(question: InspectionQuestion) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("question-${question.snapshotItemId}-header"),
        horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "${question.position}. ${question.label}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).testTag("question-${question.snapshotItemId}-title"),
        )
        Box(Modifier.size(ServiceLoopUiTokens.Size.iconSmall)) {
            if (question.isResolved()) {
                ServiceLoopIcon(
                    ServiceLoopIcons.SelectionCheck,
                    null,
                    Modifier.fillMaxSize().testTag("question-${question.snapshotItemId}-complete"),
                    LocalServiceLoopTokens.current.successInk,
                )
            }
        }
    }
}

@Composable
private fun StatusChoiceGrid(question: InspectionQuestion, enabled: Boolean, viewModel: ServiceLoopViewModel, workItemId: String) {
    val colors = LocalServiceLoopTokens.current
    val choices = listOf(
        InspectionStatusChoice(ResponseDisposition.NOT_APPLICABLE.name, "Not applicable", ServiceLoopIcons.XCircle, colors.warningInk, "response-${question.snapshotItemId}-${ResponseDisposition.NOT_APPLICABLE.name}"),
        InspectionStatusChoice(ResponseDisposition.OK.name, "OK", ServiceLoopIcons.CheckCircle, colors.successInk, "response-${question.snapshotItemId}-${ResponseDisposition.OK.name}"),
        InspectionStatusChoice(ResponseDisposition.NOT_CHECKED.name, "Not checked", ServiceLoopIcons.Circle, colors.textMuted, "response-${question.snapshotItemId}-${ResponseDisposition.NOT_CHECKED.name}"),
        InspectionStatusChoice(ResponseDisposition.ISSUE_FOUND.name, "Issue found", ServiceLoopIcons.WarningCircle, colors.errorInk, "response-${question.snapshotItemId}-${ResponseDisposition.ISSUE_FOUND.name}"),
    )
    ServiceLoopInspectionStatusGrid(
        choices = choices,
        selectedKey = question.disposition.name,
        onSelected = { value -> viewModel.chooseResponse(workItemId, question.snapshotItemId, ResponseDisposition.valueOf(value)) },
        modifier = Modifier.testTag("response-grid-${question.snapshotItemId}"),
        enabled = enabled,
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
    if (question.required || question.responseType != "TEXT") {
        ServiceLoopChecklistChoice(
            selected = question.disposition == ResponseDisposition.NOT_APPLICABLE,
            onClick = { viewModel.chooseResponse(workItemId, question.snapshotItemId, ResponseDisposition.NOT_APPLICABLE) },
            label = "Not applicable",
            modifier = Modifier.fillMaxWidth().testTag("not-applicable-${question.snapshotItemId}"),
            enabled = enabled,
        )
    }
}

private fun InspectionQuestion.isResolved(): Boolean = when (responseType) {
    "STATUS" -> when (disposition) {
        ResponseDisposition.OK -> true
        ResponseDisposition.ISSUE_FOUND, ResponseDisposition.NOT_APPLICABLE -> !reason.isNullOrBlank()
        else -> false
    }
    "TEXT" -> when (disposition) {
        ResponseDisposition.VALUE -> !textValue.isNullOrBlank()
        ResponseDisposition.NOT_APPLICABLE -> !reason.isNullOrBlank()
        else -> false
    }
    "NUMBER" -> when (disposition) {
        ResponseDisposition.VALUE -> isFiniteSignedDecimal(numberValue.orEmpty())
        ResponseDisposition.NOT_APPLICABLE -> !reason.isNullOrBlank()
        else -> false
    }
    else -> false
}

internal fun progressSummary(progress: VisitServiceProgress): String {
    val actionable = progress.actionableItems
    val values = listOf(
        actionable.count { it.status == ServiceEntryStatus.READY } to "ready for review",
        actionable.count { it.status == ServiceEntryStatus.IN_PROGRESS } to "in progress",
        actionable.count { it.status == ServiceEntryStatus.NEEDS_ATTENTION } to "needs attention",
        actionable.count { it.status == ServiceEntryStatus.NOT_STARTED } to "not started",
    ).filter { it.first > 0 }
    return values.joinToString(" · ") { serviceProgressStatusPhrase(it.first, it.second) }.ifBlank { "No local service work" }
}

internal fun serviceProgressGroupSummary(group: ServiceProgressGroup): String {
    val actionable = group.items.filter { it.documentationMode in setOf(ServiceDocumentationMode.LOCAL, ServiceDocumentationMode.CHOICE_REQUIRED) }
    val statusClauses = listOf(
        actionable.count { it.status == ServiceEntryStatus.NEEDS_ATTENTION } to "needs attention",
        actionable.count { it.status == ServiceEntryStatus.READY } to "ready for review",
        actionable.count { it.status == ServiceEntryStatus.IN_PROGRESS } to "in progress",
        actionable.count { it.status == ServiceEntryStatus.NOT_STARTED } to "not started",
    ).filter { it.first > 0 }
    return buildList {
        add("${group.items.size} ${serviceWord(group.items.size)}")
        addAll(statusClauses.map { serviceProgressStatusPhrase(it.first, it.second) })
    }.joinToString(" · ")
}

private fun serviceWord(count: Int): String = if (count == 1) "service" else "services"

private fun serviceProgressStatusPhrase(count: Int, status: String): String {
    val verb = if (status == "needs attention" && count != 1) "need attention" else status
    return "$count ${serviceWord(count)} $verb"
}

private fun serviceProgressGroupTag(key: String): String = key.replace(Regex("[^A-Za-z0-9_-]"), "-")

private fun ServiceProgressItem.navigationStatusLabel(): String = when (documentationMode) {
    ServiceDocumentationMode.CHOICE_REQUIRED -> "Choose documentation"
    ServiceDocumentationMode.LEADER_OBSERVE -> "Leader view"
    ServiceDocumentationMode.DEFERRED -> "Handed off"
    ServiceDocumentationMode.LOCAL -> when (status) {
        ServiceEntryStatus.NOT_STARTED -> "Not started"
        ServiceEntryStatus.IN_PROGRESS -> "In progress"
        ServiceEntryStatus.NEEDS_ATTENTION -> "Needs attention"
        ServiceEntryStatus.READY -> "Ready for review"
    }
}

@Composable
private fun ServiceProgressBadge(item: ServiceProgressItem) {
    val colors = LocalServiceLoopTokens.current
    val (container, ink) = when (item.documentationMode) {
        ServiceDocumentationMode.CHOICE_REQUIRED -> colors.warningContainer to colors.warningInk
        ServiceDocumentationMode.LEADER_OBSERVE, ServiceDocumentationMode.DEFERRED -> colors.infoContainer to colors.infoInk
        ServiceDocumentationMode.LOCAL -> when (item.status) {
            ServiceEntryStatus.NOT_STARTED -> colors.infoContainer to colors.infoInk
            ServiceEntryStatus.IN_PROGRESS -> colors.workingContainer to colors.workingInk
            ServiceEntryStatus.NEEDS_ATTENTION -> colors.warningContainer to colors.warningInk
            ServiceEntryStatus.READY -> colors.successContainer to colors.successInk
        }
    }
    Text(
        item.navigationStatusLabel(),
        color = ink,
        style = ServiceLoopUiTokens.Type.badge,
        modifier = Modifier.clip(RoundedCornerShape(ServiceLoopUiTokens.Radius.badge))
            .background(container)
            .padding(horizontal = ServiceLoopUiTokens.Space.sm, vertical = ServiceLoopUiTokens.Space.xs),
    )
}

private fun fallbackProgress(draft: InspectionDraft): VisitServiceProgress {
    val hasActivity = draft.workPerformed.isNotBlank() || draft.privateInternalNote.isNotBlank() ||
        draft.questions.any { it.disposition !in setOf(ResponseDisposition.UNANSWERED, ResponseDisposition.NOT_CHECKED) } ||
        draft.rawInputs.isNotEmpty() || draft.outcome != null
    val status = serviceEntryStatus(
        hasActivity = hasActivity,
        hasUnresolvedRawBuffer = draft.rawInputs.isNotEmpty(),
        hasMissingIssueDescription = draft.issueMissingDescription.isNotEmpty(),
        hasInvalidExplicitAnswer = draft.invalidExplicitAnswers.isNotEmpty(),
    )
    val item = ServiceProgressItem(draft.workItemId, 1, draft.subjectType, draft.equipmentId, draft.equipmentName, draft.equipmentReference, draft.equipmentDescription, draft.serviceName, status, serviceDocumentationMode(draft.dispatchLocalRole, draft.dispatchDocumentationDisposition), draft.dispatchLocalRole, draft.dispatchDocumentationDisposition)
    return VisitServiceProgress(draft.visitId, draft.visitReference, draft.customerName, draft.siteName, "", listOf(item), serviceProgressGroups(listOf(item)))
}
