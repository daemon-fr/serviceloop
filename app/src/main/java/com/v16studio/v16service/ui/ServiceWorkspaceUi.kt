package com.v16studio.v16service.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.v16studio.v16service.domain.InspectionDraft
import com.v16studio.v16service.domain.InspectionQuestion
import com.v16studio.v16service.domain.SaveStatus
import com.v16studio.v16service.domain.ServiceDocumentationMode
import com.v16studio.v16service.domain.ServiceEntryStatus
import com.v16studio.v16service.domain.ServiceProgressItem
import com.v16studio.v16service.domain.ServiceDraftFieldKeys
import com.v16studio.v16service.domain.WorkSubjectType
import com.v16studio.v16service.ui.designsystem.LocalV16ServiceTokens
import com.v16studio.v16service.ui.designsystem.V16ServiceNotice
import com.v16studio.v16service.ui.designsystem.V16ServiceNoticeKind
import com.v16studio.v16service.ui.designsystem.V16ServicePrivateLabel
import com.v16studio.v16service.ui.designsystem.V16ServiceSecondaryButton
import com.v16studio.v16service.ui.designsystem.V16ServiceSurfaceCard
import com.v16studio.v16service.ui.designsystem.V16ServiceTextAction
import com.v16studio.v16service.ui.designsystem.V16ServiceUiTokens
import com.v16studio.v16service.ui.designsystem.ServiceSectionStripe
import com.v16studio.v16service.ui.service.ServiceDraftFieldId
import com.v16studio.v16service.ui.service.ServiceDraftFieldState

@Composable
internal fun ServiceIdentityBlock(
    draft: InspectionDraft,
    position: Int,
    total: Int,
    current: ServiceProgressItem?,
    fieldStates: Map<ServiceDraftFieldId, ServiceDraftFieldState>,
    saveStatus: SaveStatus,
    viewModel: V16ServiceViewModel,
    nav: NavHostController,
) {
    V16ServiceSurfaceCard(modifier = Modifier.testTag("service-identity")) {
        Text("Service $position of $total", style = MaterialTheme.typography.titleLarge)
        Text(v16ServiceSubjectLabel(draft.subjectType, draft.equipmentName, draft.equipmentReference, draft.equipmentDescription), style = MaterialTheme.typography.titleMedium)
        Text(draft.serviceName, style = MaterialTheme.typography.bodyLarge)
        Text("${draft.visitReference} · ${draft.siteName}", style = MaterialTheme.typography.bodyMedium)
        draft.dueDate?.takeIf(String::isNotBlank)?.let { Text("Due $it") }
        draft.interval?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (draft.subjectType == WorkSubjectType.EQUIPMENT && draft.equipmentId == null && current?.documentationMode == ServiceDocumentationMode.LOCAL) {
            V16ServiceSecondaryButton("Link equipment", { nav.navigate("work/${draft.workItemId}/link-equipment") }, Modifier.fillMaxWidth().testTag("link-equipment"))
        }
        ServiceSaveState(draft, fieldStates, saveStatus, viewModel)
    }
}

@Composable
internal fun ServiceSaveState(
    draft: InspectionDraft,
    fieldStates: Map<ServiceDraftFieldId, ServiceDraftFieldState>,
    saveStatus: SaveStatus,
    viewModel: V16ServiceViewModel,
) {
    val states = fieldStates.filterKeys { it.workItemId == draft.workItemId }.values
    val failed = states.filterIsInstance<ServiceDraftFieldState.Failed>().firstOrNull()
    val invalid = states.filterIsInstance<ServiceDraftFieldState.Invalid>().firstOrNull()
    val rawUnsettled = draft.rawInputs.keys.any { key -> fieldStates[ServiceDraftFieldId(draft.workItemId, key)] !is ServiceDraftFieldState.Clean }
    when {
        failed != null || saveStatus is SaveStatus.Failed -> V16ServiceNotice(
            "Not saved — retry",
            failed?.message ?: (saveStatus as? SaveStatus.Failed)?.message,
            V16ServiceNoticeKind.Error,
            action = { V16ServiceTextAction("Retry", { viewModel.retryFailedServiceEdits(draft.workItemId) }, Modifier.testTag("retry-service-edits")) },
        )
        invalid != null -> V16ServiceNotice("Needs attention", invalid.message, V16ServiceNoticeKind.Warning)
        rawUnsettled || states.any { it is ServiceDraftFieldState.Pending || it is ServiceDraftFieldState.Saving } || saveStatus is SaveStatus.Saving -> V16ServiceNotice("Saving…", "The latest Service edit is being saved.", V16ServiceNoticeKind.Working)
        else -> Unit
    }
}

@Composable
internal fun DocumentationModeNotice(mode: ServiceDocumentationMode, onOpenAssignment: () -> Unit) {
    when (mode) {
        ServiceDocumentationMode.CHOICE_REQUIRED -> V16ServiceNotice("Documentation choice required", "Choose whether to document this work on this device or hand it off from the Visit overview.", V16ServiceNoticeKind.Warning, action = { V16ServiceSecondaryButton("Open visit assignment", onOpenAssignment, Modifier.fillMaxWidth().testTag("open-visit-assignment")) })
        ServiceDocumentationMode.LEADER_OBSERVE -> V16ServiceNotice("Leader view", "This work is visible because this Technician is a Visit leader. It is not being documented on this device.", V16ServiceNoticeKind.Info, action = { V16ServiceSecondaryButton("Open visit assignment", onOpenAssignment, Modifier.fillMaxWidth().testTag("open-visit-assignment")) })
        ServiceDocumentationMode.DEFERRED -> V16ServiceNotice("Documentation handed off", "This Service is read-only on this device.", V16ServiceNoticeKind.Info)
        ServiceDocumentationMode.LOCAL -> Unit
    }
}

@Composable
internal fun PrivateWorkContext(rows: List<Pair<String, String>>) {
    V16ServiceSurfaceCard(modifier = Modifier.testTag("private-work-context")) {
        V16ServicePrivateLabel("PRIVATE WORK CONTEXT", style = MaterialTheme.typography.labelLarge)
        rows.forEach { (label, value) ->
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value)
        }
    }
}

@Composable
internal fun ServiceCompletionLandmark() {
    ServiceSectionStripe("Service completion", modifier = Modifier.testTag("service-completion-landmark"), testTag = "service-completion-stripe")
}
