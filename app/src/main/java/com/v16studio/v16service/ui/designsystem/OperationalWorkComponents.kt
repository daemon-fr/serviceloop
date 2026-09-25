package com.v16studio.v16service.ui.designsystem

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.v16studio.v16service.domain.OperationalDashboardProjection
import com.v16studio.v16service.domain.OperationalDashboardSection
import com.v16studio.v16service.domain.OperationalWorkItem
import com.v16studio.v16service.domain.OperationalWorkKind
import com.v16studio.v16service.domain.OperationalWorkState
import com.v16studio.v16service.domain.WorkScope
import com.v16studio.v16service.ui.icons.V16ServiceIcon
import com.v16studio.v16service.ui.icons.V16ServiceIcons
import com.v16studio.v16service.ui.icons.V16ServiceEntityIcons
import com.v16studio.v16service.ui.icons.V16ServiceEntityType

data class OperationalStatePalette(val accent: Color, val container: Color)

object OperationalWorkColors {
    private val light = mapOf(
        OperationalWorkState.IN_PROGRESS to OperationalStatePalette(Color(0xFF315F96), Color(0xFFEAF0FA)),
        OperationalWorkState.OVERDUE to OperationalStatePalette(Color(0xFFA32D35), Color(0xFFFBE9E8)),
        OperationalWorkState.DUE_SOON to OperationalStatePalette(Color(0xFF875100), Color(0xFFFFF3DD)),
        OperationalWorkState.BOOKED to OperationalStatePalette(Color(0xFF08666B), Color(0xFFE5F3F2)),
    )
    private val dark = mapOf(
        OperationalWorkState.IN_PROGRESS to OperationalStatePalette(Color(0xFFB8D7FF), Color(0xFF20384F)),
        OperationalWorkState.OVERDUE to OperationalStatePalette(Color(0xFFFFC0C4), Color(0xFF49282D)),
        OperationalWorkState.DUE_SOON to OperationalStatePalette(Color(0xFFFFDA97), Color(0xFF493519)),
        OperationalWorkState.BOOKED to OperationalStatePalette(Color(0xFFA0E4DF), Color(0xFF163B3E)),
    )

    fun forState(state: OperationalWorkState, tokens: V16ServiceColorRoles): OperationalStatePalette =
        (if (tokens.canvas == V16ServiceUiTokens.DarkColors.canvas) dark else light).getValue(state)
}

@Composable
fun OperationalDashboard(
    projection: OperationalDashboardProjection,
    onOpenItem: (OperationalWorkItem) -> Unit,
    onViewAll: (OperationalDashboardSection) -> Unit,
    modifier: Modifier = Modifier,
    showCustomerTitle: String? = null,
    showTitle: Boolean = true,
) {
    val sectionKeys = projection.sections.map(::operationalSectionKey)
    var initialized by rememberSaveable(scopeKey(projection.scope)) { mutableStateOf(false) }
    var expandedKeys by rememberSaveable(scopeKey(projection.scope)) { mutableStateOf("") }
    LaunchedEffect(sectionKeys) {
        if (!initialized && sectionKeys.isNotEmpty()) {
            val preferred = projection.sections.firstOrNull {
                it.kind == OperationalWorkKind.VISIT && it.state == OperationalWorkState.IN_PROGRESS
            } ?: projection.sections.first()
            expandedKeys = operationalSectionKey(preferred)
            initialized = true
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.md)) {
        showCustomerTitle?.let { Text(it, style = V16ServiceUiTokens.Type.screenTitle, modifier = Modifier.semantics { heading() }.testTag("scoped-dashboard-customer")) }
        if (showTitle) Text("Work items", style = V16ServiceUiTokens.Type.sectionTitle, modifier = Modifier.semantics { heading() }.testTag("operational-dashboard-title"))
        if (projection.sections.isEmpty()) {
            Text("No current work needs attention.", color = LocalV16ServiceTokens.current.textSecondary, modifier = Modifier.testTag("operational-dashboard-empty"))
        } else {
            projection.sections.forEach { section ->
                val key = operationalSectionKey(section)
                val expanded = key in expandedKeys.split('|').filter(String::isNotBlank)
                OperationalDashboardSectionCard(
                    section = section,
                    expanded = expanded,
                    onToggle = {
                        val current = expandedKeys.split('|').filter(String::isNotBlank).toSet()
                        expandedKeys = (if (expanded) current - key else current + key).sorted().joinToString("|")
                    },
                    onOpenItem = onOpenItem,
                    onViewAll = onViewAll,
                )
            }
        }
    }
}

@Composable
private fun OperationalDashboardSectionCard(
    section: OperationalDashboardSection,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenItem: (OperationalWorkItem) -> Unit,
    onViewAll: (OperationalDashboardSection) -> Unit,
) {
    val tokens = LocalV16ServiceTokens.current
    val colors = OperationalWorkColors.forState(section.state, tokens)
    val shape = RoundedCornerShape(V16ServiceUiTokens.Radius.card)
    val railWidth = 6.dp
    val visibleItems = section.items.take(OPERATIONAL_PREVIEW_LIMIT)

    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(colors.container)
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = colors.accent,
                    topLeft = androidx.compose.ui.geometry.Offset.Zero,
                    size = androidx.compose.ui.geometry.Size(railWidth.toPx(), size.height),
                    cornerRadius = CornerRadius(V16ServiceUiTokens.Radius.card.toPx()),
                )
            }
            .padding(start = 20.dp, end = V16ServiceUiTokens.Space.md, top = V16ServiceUiTokens.Space.sm, bottom = V16ServiceUiTokens.Space.md)
            .testTag("operational-section-${section.kind.name.lowercase()}-${section.state.name.lowercase()}"),
        verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = V16ServiceUiTokens.Size.touchMin)
                .clip(RoundedCornerShape(V16ServiceUiTokens.Radius.field))
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics {
                    role = Role.Button
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                    contentDescription = "${section.title} · ${section.itemCount}; ${if (expanded) "Collapse" else "Expand"} section"
                }
                .testTag("operational-section-header-${section.kind.name.lowercase()}-${section.state.name.lowercase()}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm),
        ) {
        V16ServiceIcon(iconForKind(section.kind), null, Modifier.size(V16ServiceUiTokens.Size.icon), colors.accent)
            Text("${section.title} • ${section.itemCount}", style = V16ServiceUiTokens.Type.itemTitle, color = colors.accent, modifier = Modifier.weight(1f).semantics { heading() })
            V16ServiceIcon(
                if (expanded) V16ServiceIcons.CaretDown else V16ServiceIcons.CaretRight,
                null,
                Modifier.size(V16ServiceUiTokens.Size.icon),
                colors.accent,
            )
        }
        sectionSupportingLine(section)?.let { Text(it, style = V16ServiceUiTokens.Type.supporting, color = tokens.textSecondary, modifier = Modifier.fillMaxWidth().testTag("operational-section-supporting")) }
        if (expanded) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm)) {
                visibleItems.forEach { item -> OperationalWorkRow(item, onClick = { onOpenItem(item) }) }
                if (section.itemCount > OPERATIONAL_PREVIEW_LIMIT) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = V16ServiceUiTokens.Size.touchMin)
                            .clip(RoundedCornerShape(V16ServiceUiTokens.Radius.field))
                            .clickable(role = Role.Button, onClick = { onViewAll(section) })
                            .semantics { contentDescription = viewAllLabel(section) }
                            .testTag("operational-view-all-${section.kind.name.lowercase()}-${section.state.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(viewAllLabel(section), color = colors.accent, style = V16ServiceUiTokens.Type.label, modifier = Modifier.weight(1f))
                        V16ServiceIcon(V16ServiceIcons.Disclosure, null, Modifier.size(V16ServiceUiTokens.Size.icon), colors.accent)
                    }
                }
            }
        }
    }
}

@Composable
fun OperationalWorkRow(
    item: OperationalWorkItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tokens = LocalV16ServiceTokens.current
    val palette = OperationalWorkColors.forState(item.state, tokens)
    val shape = RoundedCornerShape(V16ServiceUiTokens.Radius.field)
    Column(
        modifier.fillMaxWidth().heightIn(min = V16ServiceUiTokens.Size.listRowMin)
            .clip(shape)
            .background(tokens.surface)
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = palette.accent,
                    cornerRadius = CornerRadius(V16ServiceUiTokens.Radius.field.toPx()),
                    style = Stroke(
                        width = V16ServiceUiTokens.Stroke.record.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    ),
                )
            }
            .v16ServiceFocusRing(V16ServiceUiTokens.Radius.field)
            .clickable(role = Role.Button, onClick = onClick)
            .focusable()
            .semantics { contentDescription = "Open ${item.displayReference} · ${item.displayTitle}" }
            .testTag("operational-work-row-${item.recordId}")
            .padding(V16ServiceUiTokens.Space.md),
        verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.xs),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.md), verticalAlignment = Alignment.Top) {
            V16ServiceIcon(iconForKind(item.kind), null, Modifier.size(V16ServiceUiTokens.Size.icon), palette.accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.xs)) {
                Text("${item.displayReference} · ${item.displayTitle}", style = V16ServiceUiTokens.Type.itemTitle)
                if (item.displayContext.isNotBlank()) Text(item.displayContext, style = V16ServiceUiTokens.Type.supporting, color = tokens.textSecondary)
                if (item.dueDate != null || item.kind == OperationalWorkKind.VISIT && item.scheduledAtEpochMillis != null) {
                    if (item.displayContext.isNotBlank()) Spacer(Modifier.height(V16ServiceUiTokens.Space.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.md), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("operational-work-meta-${item.recordId}")) {
                        item.dueDate?.let { Text("Due $it", style = V16ServiceUiTokens.Type.meta, color = tokens.textMuted) }
                        if (item.kind == OperationalWorkKind.VISIT && item.scheduledAtEpochMillis != null) {
                            Text("Booked visit", style = V16ServiceUiTokens.Type.label, color = tokens.textSecondary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OperationalWorkGateway(
    count: Int,
    state: OperationalWorkState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalV16ServiceTokens.current
    val palette = OperationalWorkColors.forState(state, tokens)
    val shape = RoundedCornerShape(V16ServiceUiTokens.Radius.card)
    Row(
        modifier.fillMaxWidth().heightIn(min = V16ServiceUiTokens.Size.listRowMin)
            .clip(shape).background(palette.container)
            .drawWithContent {
                drawContent()
                drawRoundRect(color = palette.accent, size = androidx.compose.ui.geometry.Size(6.dp.toPx(), size.height), cornerRadius = CornerRadius(V16ServiceUiTokens.Radius.card.toPx()))
            }
            .v16ServiceFocusRing(V16ServiceUiTokens.Radius.card)
            .clickable(role = Role.Button, onClick = onClick)
            .focusable()
            .semantics { contentDescription = "Work items · $count. Open customer work dashboard"; role = Role.Button }
            .testTag("operational-work-gateway"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.md),
    ) {
        Spacer(Modifier.width(2.dp))
        V16ServiceIcon(iconForState(state), null, Modifier.size(V16ServiceUiTokens.Size.icon), palette.accent)
        Text("Work items • $count", style = V16ServiceUiTokens.Type.itemTitle, color = palette.accent, modifier = Modifier.weight(1f))
        V16ServiceIcon(V16ServiceIcons.Disclosure, null, Modifier.size(V16ServiceUiTokens.Size.icon), palette.accent)
        Spacer(Modifier.width(V16ServiceUiTokens.Space.xs))
    }
}

private fun iconForKind(kind: OperationalWorkKind): Int = when (kind) {
    OperationalWorkKind.VISIT -> V16ServiceEntityIcons.forType(V16ServiceEntityType.VISIT)!!
    OperationalWorkKind.SERVICE -> V16ServiceEntityIcons.forType(V16ServiceEntityType.SERVICE)!!
    OperationalWorkKind.FOLLOW_UP -> V16ServiceEntityIcons.forType(V16ServiceEntityType.FOLLOW_UP)!!
}

private fun iconForState(state: OperationalWorkState): Int = when (state) {
    OperationalWorkState.IN_PROGRESS -> V16ServiceIcons.Work
    OperationalWorkState.OVERDUE -> V16ServiceIcons.Warning
    OperationalWorkState.DUE_SOON -> V16ServiceIcons.Time
    OperationalWorkState.BOOKED -> V16ServiceIcons.Calendar
}

private fun operationalSectionKey(section: OperationalDashboardSection) = "${section.kind.name}:${section.state.name}"
private fun scopeKey(scope: WorkScope) = when (scope) {
    WorkScope.Global -> "global"
    is WorkScope.Customer -> "customer-${scope.customerId}"
}

private fun sectionSupportingLine(section: OperationalDashboardSection): String? = when (section.kind to section.state) {
    OperationalWorkKind.VISIT to OperationalWorkState.IN_PROGRESS -> "Resume active work"
    OperationalWorkKind.VISIT to OperationalWorkState.OVERDUE -> "Booked visits that should already have started"
    OperationalWorkKind.SERVICE to OperationalWorkState.OVERDUE -> "Service obligations still outstanding"
    OperationalWorkKind.VISIT to OperationalWorkState.DUE_SOON -> "Booked visits approaching their service date"
    OperationalWorkKind.SERVICE to OperationalWorkState.DUE_SOON -> "Service obligations approaching their due date"
    OperationalWorkKind.VISIT to OperationalWorkState.BOOKED -> "Planned visits scheduled later"
    OperationalWorkKind.FOLLOW_UP to OperationalWorkState.OVERDUE -> "Open follow-ups past their due date"
    OperationalWorkKind.FOLLOW_UP to OperationalWorkState.DUE_SOON -> "Open follow-ups due soon"
    OperationalWorkKind.FOLLOW_UP to OperationalWorkState.BOOKED -> "Open follow-ups planned for later"
    else -> null
}

private fun viewAllLabel(section: OperationalDashboardSection): String {
    val stateText = when (section.state) {
        OperationalWorkState.IN_PROGRESS -> "in-progress"
        OperationalWorkState.OVERDUE -> "overdue"
        OperationalWorkState.DUE_SOON -> "due soon"
        OperationalWorkState.BOOKED -> "booked"
    }
    return "View all ${section.itemCount} $stateText ${section.kind.plural}"
}

private const val OPERATIONAL_PREVIEW_LIMIT = 5
