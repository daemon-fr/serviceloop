package com.v16studio.serviceloop.ui

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
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.InspectionQuestion
import com.v16studio.serviceloop.domain.SaveStatus
import com.v16studio.serviceloop.domain.ServiceDocumentationMode
import com.v16studio.serviceloop.domain.ServiceEntryStatus
import com.v16studio.serviceloop.domain.ServiceProgressItem
import com.v16studio.serviceloop.domain.ServiceDraftFieldKeys
import com.v16studio.serviceloop.domain.WorkSubjectType
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNotice
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNoticeKind
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrivateLabel
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSecondaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSurfaceCard
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextAction
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.service.ServiceDraftFieldId
import com.v16studio.serviceloop.ui.service.ServiceDraftFieldState

@Composable
internal fun ServiceIdentityBlock(
    draft: InspectionDraft,
    position: Int,
    total: Int,
    current: ServiceProgressItem?,
    fieldStates: Map<ServiceDraftFieldId, ServiceDraftFieldState>,
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
internal fun ServiceSaveState(
    draft: InspectionDraft,
    fieldStates: Map<ServiceDraftFieldId, ServiceDraftFieldState>,
    saveStatus: SaveStatus,
    viewModel: ServiceLoopViewModel,
) {
    val states = fieldStates.filterKeys { it.workItemId == draft.workItemId }.values
    val failed = states.filterIsInstance<ServiceDraftFieldState.Failed>().firstOrNull()
    val invalid = states.filterIsInstance<ServiceDraftFieldState.Invalid>().firstOrNull()
    val rawUnsettled = draft.rawInputs.keys.any { key -> fieldStates[ServiceDraftFieldId(draft.workItemId, key)] !is ServiceDraftFieldState.Clean }
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
internal fun DocumentationModeNotice(mode: ServiceDocumentationMode, onOpenAssignment: () -> Unit) {
    when (mode) {
        ServiceDocumentationMode.CHOICE_REQUIRED -> ServiceLoopNotice("Documentation choice required", "Choose whether to document this work on this device or hand it off from the Visit overview.", ServiceLoopNoticeKind.Warning, action = { ServiceLoopSecondaryButton("Open visit assignment", onOpenAssignment, Modifier.fillMaxWidth().testTag("open-visit-assignment")) })
        ServiceDocumentationMode.LEADER_OBSERVE -> ServiceLoopNotice("Leader view", "This work is visible because this Technician is a Visit leader. It is not being documented on this device.", ServiceLoopNoticeKind.Info, action = { ServiceLoopSecondaryButton("Open visit assignment", onOpenAssignment, Modifier.fillMaxWidth().testTag("open-visit-assignment")) })
        ServiceDocumentationMode.DEFERRED -> ServiceLoopNotice("Documentation handed off", "This Service is read-only on this device.", ServiceLoopNoticeKind.Info)
        ServiceDocumentationMode.LOCAL -> Unit
    }
}

@Composable
internal fun PrivateWorkContext(rows: List<Pair<String, String>>) {
    ServiceLoopSurfaceCard(modifier = Modifier.testTag("private-work-context")) {
        ServiceLoopPrivateLabel("PRIVATE WORK CONTEXT", style = MaterialTheme.typography.labelLarge)
        rows.forEach { (label, value) ->
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value)
        }
    }
}

@Composable
internal fun ServiceCompletionLandmark() {
    val colors = LocalServiceLoopTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("service-completion-landmark"),
        color = colors.surfaceSubtle,
        shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field),
    ) {
        Text("Service completion", style = ServiceLoopUiTokens.Type.sectionTitle, modifier = Modifier.padding(horizontal = ServiceLoopUiTokens.Space.md, vertical = ServiceLoopUiTokens.Space.sm))
    }
}
