package com.v16studio.serviceloop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.v16studio.serviceloop.data.isFiniteSignedDecimal
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.InspectionQuestion
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.domain.ServiceDraftFieldKeys
import com.v16studio.serviceloop.ui.designsystem.InspectionStatusChoice
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceSectionStripe
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChecklistChoice
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopInspectionStatusGrid
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopLongTextEditor
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrivateLabel
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSurfaceCard
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextField
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons

@Composable
internal fun ChecklistSectionHeader(draft: InspectionDraft) {
    val colors = LocalServiceLoopTokens.current
    Column(Modifier.testTag("checklist-section"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ServiceSectionStripe("Checklist", testTag = "service-checklist-stripe")
        if (draft.questions.isNotEmpty()) {
            Text("Required complete ${draft.requiredComplete} of ${draft.requiredTotal}", color = if (draft.checklistComplete) colors.successInk else colors.errorInk, modifier = Modifier.padding(horizontal = ServiceLoopUiTokens.Space.xs).testTag("checklist-completeness"))
        }
    }
}

@Composable
internal fun ServiceQuestionBlock(workItemId: String, rawInputs: Map<String, String>, question: InspectionQuestion, editingEnabled: Boolean, viewModel: ServiceLoopViewModel) {
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
    val invalid = question.responseType == "NUMBER" && value.isNotBlank() && !isFiniteSignedDecimal(value)
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
