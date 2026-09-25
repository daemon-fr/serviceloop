package com.v16studio.v16service.ui

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
import com.v16studio.v16service.data.isFiniteSignedDecimal
import com.v16studio.v16service.domain.InspectionDraft
import com.v16studio.v16service.domain.InspectionQuestion
import com.v16studio.v16service.domain.ResponseDisposition
import com.v16studio.v16service.domain.ServiceDraftFieldKeys
import com.v16studio.v16service.ui.designsystem.InspectionStatusChoice
import com.v16studio.v16service.ui.designsystem.LocalV16ServiceTokens
import com.v16studio.v16service.ui.designsystem.ServiceSectionStripe
import com.v16studio.v16service.ui.designsystem.V16ServiceChecklistChoice
import com.v16studio.v16service.ui.designsystem.V16ServiceInspectionStatusGrid
import com.v16studio.v16service.ui.designsystem.V16ServiceLongTextEditor
import com.v16studio.v16service.ui.designsystem.V16ServicePrivateLabel
import com.v16studio.v16service.ui.designsystem.V16ServiceSurfaceCard
import com.v16studio.v16service.ui.designsystem.V16ServiceTextField
import com.v16studio.v16service.ui.designsystem.V16ServiceUiTokens
import com.v16studio.v16service.ui.icons.V16ServiceIcon
import com.v16studio.v16service.ui.icons.V16ServiceIcons

@Composable
internal fun ChecklistSectionHeader(draft: InspectionDraft) {
    val colors = LocalV16ServiceTokens.current
    Column(Modifier.fillMaxWidth().testTag("checklist-section"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ServiceSectionStripe("Checklist", testTag = "service-checklist-stripe")
        if (draft.questions.isNotEmpty()) {
            Text("Required complete ${draft.requiredComplete} of ${draft.requiredTotal}", color = if (draft.checklistComplete) colors.successInk else colors.errorInk, modifier = Modifier.padding(horizontal = V16ServiceUiTokens.Space.xs).testTag("checklist-completeness"))
        }
    }
}

@Composable
internal fun ServiceQuestionBlock(workItemId: String, rawInputs: Map<String, String>, question: InspectionQuestion, editingEnabled: Boolean, viewModel: V16ServiceViewModel) {
    val initialIssue = rawInputs[ServiceDraftFieldKeys.questionIssue(question.snapshotItemId)] ?: question.issueFoundReasonDraft ?: question.reason.takeIf { question.disposition == ResponseDisposition.ISSUE_FOUND }.orEmpty()
    val initialNotApplicable = rawInputs[ServiceDraftFieldKeys.questionNotApplicable(question.snapshotItemId)] ?: question.notApplicableReasonDraft ?: question.reason.takeIf { question.disposition == ResponseDisposition.NOT_APPLICABLE }.orEmpty()
    var issueBuffer by rememberSaveable("issue-${question.snapshotItemId}", initialIssue) { mutableStateOf(initialIssue) }
    var notApplicableBuffer by rememberSaveable("na-${question.snapshotItemId}", initialNotApplicable) { mutableStateOf(initialNotApplicable) }
    V16ServiceSurfaceCard(modifier = Modifier.testTag("question-${question.snapshotItemId}")) {
        ServiceQuestionHeader(question)
        Text(
            if (question.required) "Required · ${question.responseType.lowercase().replaceFirstChar(Char::uppercase)}" else "Optional · ${question.responseType.lowercase().replaceFirstChar(Char::uppercase)}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("question-${question.snapshotItemId}-metadata"),
        )
        question.privateGuidance?.takeIf(String::isNotBlank)?.let {
            V16ServicePrivateLabel("PRIVATE · $it", modifier = Modifier.testTag("question-${question.snapshotItemId}-private-guidance"))
        }
        when (question.responseType) {
            "STATUS" -> StatusChoiceGrid(question, editingEnabled, viewModel, workItemId)
            else -> ValueQuestion(workItemId, rawInputs, question, editingEnabled, viewModel)
        }
        if (question.disposition == ResponseDisposition.ISSUE_FOUND) {
            Text("Finding · Customer report", style = MaterialTheme.typography.labelLarge)
            Text("Required for checklist completion.", style = MaterialTheme.typography.bodySmall)
            V16ServiceLongTextEditor(issueBuffer, { issueBuffer = it; if (editingEnabled) viewModel.scheduleIssueDescription(workItemId, question.snapshotItemId, it) }, "Public finding description", false, enabled = editingEnabled, fieldTestTag = "long-text-public-finding-description", onFocusLost = { viewModel.flushServiceDraftAsync(workItemId) })
        }
        if (question.disposition == ResponseDisposition.NOT_APPLICABLE && (question.required || question.responseType != "TEXT")) {
            Text("Not applicable reason", style = MaterialTheme.typography.labelLarge)
            Text("Required for checklist completion.", style = MaterialTheme.typography.bodySmall)
            V16ServiceLongTextEditor(notApplicableBuffer, { notApplicableBuffer = it; if (editingEnabled) viewModel.scheduleNotApplicableReason(workItemId, question.snapshotItemId, it) }, "Not applicable reason", false, enabled = editingEnabled, fieldTestTag = "not-applicable-reason-${question.snapshotItemId}", onFocusLost = { viewModel.flushServiceDraftAsync(workItemId) })
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
        horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "${question.position}. ${question.label}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).testTag("question-${question.snapshotItemId}-title"),
        )
        Box(Modifier.size(V16ServiceUiTokens.Size.iconSmall)) {
            if (question.isResolved()) {
                V16ServiceIcon(
                    V16ServiceIcons.SelectionCheck,
                    null,
                    Modifier.fillMaxSize().testTag("question-${question.snapshotItemId}-complete"),
                    LocalV16ServiceTokens.current.successInk,
                )
            }
        }
    }
}

@Composable
private fun StatusChoiceGrid(question: InspectionQuestion, enabled: Boolean, viewModel: V16ServiceViewModel, workItemId: String) {
    val colors = LocalV16ServiceTokens.current
    val choices = listOf(
        InspectionStatusChoice(ResponseDisposition.NOT_APPLICABLE.name, "Not applicable", V16ServiceIcons.XCircle, colors.warningInk, "response-${question.snapshotItemId}-${ResponseDisposition.NOT_APPLICABLE.name}"),
        InspectionStatusChoice(ResponseDisposition.OK.name, "OK", V16ServiceIcons.CheckCircle, colors.successInk, "response-${question.snapshotItemId}-${ResponseDisposition.OK.name}"),
        InspectionStatusChoice(ResponseDisposition.NOT_CHECKED.name, "Not checked", V16ServiceIcons.Circle, colors.textMuted, "response-${question.snapshotItemId}-${ResponseDisposition.NOT_CHECKED.name}"),
        InspectionStatusChoice(ResponseDisposition.ISSUE_FOUND.name, "Issue found", V16ServiceIcons.WarningCircle, colors.errorInk, "response-${question.snapshotItemId}-${ResponseDisposition.ISSUE_FOUND.name}"),
    )
    V16ServiceInspectionStatusGrid(
        choices = choices,
        selectedKey = question.disposition.name,
        onSelected = { value -> viewModel.chooseResponse(workItemId, question.snapshotItemId, ResponseDisposition.valueOf(value)) },
        modifier = Modifier.testTag("response-grid-${question.snapshotItemId}"),
        enabled = enabled,
    )
}

@Composable
private fun ValueQuestion(workItemId: String, rawInputs: Map<String, String>, question: InspectionQuestion, enabled: Boolean, viewModel: V16ServiceViewModel) {
    val initialValue = rawInputs[ServiceDraftFieldKeys.questionValue(question.snapshotItemId)] ?: question.textValue ?: question.numberValue ?: ""
    var value by rememberSaveable("value-${question.snapshotItemId}", initialValue) { mutableStateOf(initialValue) }
    var wasFocused by remember { mutableStateOf(false) }
    val invalid = question.responseType == "NUMBER" && value.isNotBlank() && !isFiniteSignedDecimal(value)
    V16ServiceTextField(
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
        V16ServiceChecklistChoice(
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
