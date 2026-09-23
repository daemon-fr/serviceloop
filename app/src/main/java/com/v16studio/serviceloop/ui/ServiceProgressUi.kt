package com.v16studio.serviceloop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.domain.ServiceDocumentationMode
import com.v16studio.serviceloop.domain.ServiceEntryStatus
import com.v16studio.serviceloop.domain.ServiceProgressGroup
import com.v16studio.serviceloop.domain.ServiceProgressItem
import com.v16studio.serviceloop.domain.VisitServiceProgress
import com.v16studio.serviceloop.domain.serviceDocumentationMode
import com.v16studio.serviceloop.domain.serviceEntryStatus
import com.v16studio.serviceloop.domain.serviceProgressGroups
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDenseNavigableRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSurfaceCard
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.designsystem.serviceLoopFocusRing
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons

@Composable
internal fun ServiceProgressNavigator(
    progress: VisitServiceProgress,
    currentWorkItemId: String?,
    onSelect: (ServiceProgressItem) -> Unit,
    rowTagPrefix: String = "service-row",
) {
    ServiceLoopSurfaceCard(modifier = Modifier.testTag("visit-progress")) {
        Text("Visit progress", style = MaterialTheme.typography.titleLarge)
        Text(progressSummary(progress), modifier = Modifier.testTag("visit-progress-summary"))
        progress.groups.forEachIndexed { groupIndex, group ->
            val groupTag = serviceProgressGroupTag(group.key)
            Column(
                Modifier.fillMaxWidth().testTag("service-group-$groupTag"),
                verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs),
            ) {
                Text(
                    group.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = if (groupIndex == 0) ServiceLoopUiTokens.Space.sm else ServiceLoopUiTokens.Space.md),
                )
                group.items.forEachIndexed { index, item ->
                        val selected = item.workItemId == currentWorkItemId
                        ServiceLoopDenseNavigableRow(
                            title = "${item.position}. ${item.serviceName}",
                            context = null,
                            statusContent = { ServiceProgressBadge(item) },
                            modifier = Modifier.testTag("$rowTagPrefix-${item.workItemId}"),
                            selected = selected,
                            selectedBackground = false,
                            showDisclosure = !selected && group.items.size > 1,
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = ServiceLoopUiTokens.Space.md),
                            onClick = if (selected) null else ({ onSelect(item) }),
                            showDivider = index < group.items.lastIndex,
                        )
                }
            }
        }
    }
}

@Composable
internal fun VisitServiceProgressOverview(progress: VisitServiceProgress, onSelect: (ServiceProgressItem) -> Unit) {
    ServiceProgressNavigator(progress, null, onSelect, rowTagPrefix = "visit-line")
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

internal fun fallbackProgress(draft: InspectionDraft): VisitServiceProgress {
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
