package com.v16studio.serviceloop.ui.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons
import com.v16studio.serviceloop.domain.OperationalWorkState

@Composable
fun ServiceLoopSectionHeading(title: String, trailing: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}


@Composable
fun ServiceLoopBackAction(onBack: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onBack, modifier = modifier.heightIn(min = ServiceLoopUiTokens.Size.touchMin)) {
        ServiceLoopIcon(ServiceLoopIcons.Back, null, Modifier.size(ServiceLoopUiTokens.Size.icon), LocalContentColor.current)
        Spacer(Modifier.width(ServiceLoopUiTokens.Space.xs))
        Text("Back")
    }
}

@Composable
fun ServiceLoopDetailToolbar(title: String, onBack: () -> Unit, topAction: (@Composable RowScope.() -> Unit)? = null) {
    var leftWidthPx by remember { mutableStateOf(0) }
    var rightWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = ServiceLoopUiTokens.Size.topBarMin)) {
        val sideReserve = with(density) { maxOf(leftWidthPx, rightWidthPx).toDp() }
        val stackTitle = maxWidth - sideReserve * 2 < 160.dp
        if (stackTitle) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    ServiceLoopBackAction(onBack, Modifier.onSizeChanged { leftWidthPx = it.width })
                    topAction?.let { action -> Row(Modifier.onSizeChanged { rightWidthPx = it.width }, content = action) }
                }
                Text(title, style = ServiceLoopUiTokens.Type.screenTitle, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = ServiceLoopUiTokens.Space.lg, vertical = ServiceLoopUiTokens.Space.sm))
            }
        } else {
            Box(Modifier.fillMaxWidth().heightIn(min = ServiceLoopUiTokens.Size.topBarMin)) {
                Text(title, style = ServiceLoopUiTokens.Type.screenTitle, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = sideReserve + ServiceLoopUiTokens.Space.sm))
                ServiceLoopBackAction(onBack, Modifier.align(Alignment.CenterStart).onSizeChanged { leftWidthPx = it.width })
                topAction?.let { action -> Row(Modifier.align(Alignment.CenterEnd).onSizeChanged { rightWidthPx = it.width }, content = action) }
            }
        }
    }
}

@Composable
fun <T> ServiceLoopContentTabs(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
) {
    val c = LocalServiceLoopTokens.current
    val gap = ServiceLoopUiTokens.Space.xs
    Layout(
        content = {
            options.forEach { (value, label) ->
                val active = value == selected
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    Modifier.heightIn(min = ServiceLoopUiTokens.Size.touchMin).testTag(testTagPrefix?.let { "$it-$value" } ?: "content-tab-$label")
                        .clip(RoundedCornerShape(topStart = ServiceLoopUiTokens.Radius.field, topEnd = ServiceLoopUiTokens.Radius.field))
                        .background(if (active) c.canvas else Color.Transparent)
                        .drawBehind {
                            if (active) {
                                val stroke = ServiceLoopUiTokens.Stroke.tab.toPx()
                                val radius = ServiceLoopUiTokens.Radius.field.toPx()
                                drawLine(c.outlineDecorative, Offset(0f, size.height), Offset(0f, radius), stroke)
                                drawArc(c.outlineDecorative, 180f, 90f, false, Offset.Zero, Size(radius * 2, radius * 2), style = Stroke(stroke))
                                drawLine(c.outlineDecorative, Offset(radius, 0f), Offset(size.width - radius, 0f), stroke)
                                drawArc(c.outlineDecorative, 270f, 90f, false, Offset(size.width - radius * 2, 0f), Size(radius * 2, radius * 2), style = Stroke(stroke))
                                drawLine(c.outlineDecorative, Offset(size.width, radius), Offset(size.width, size.height), stroke)
                            }
                        }
                        .serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field)
                        .clickable(role = Role.Tab, interactionSource = interactionSource, indication = null) { onSelected(value) }.focusable()
                        .semantics { this.role = Role.Tab; this.selected = active }
                        .padding(horizontal = ServiceLoopUiTokens.Space.xs, vertical = ServiceLoopUiTokens.Space.md),
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = ServiceLoopUiTokens.Type.label, color = if (active) c.action else c.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, softWrap = true, maxLines = 2) }
            }
        },
        modifier = modifier.fillMaxWidth()
            .drawBehind { drawLine(c.outlineDecorative, Offset(0f, size.height - ServiceLoopUiTokens.Stroke.tab.toPx()), Offset(size.width, size.height - ServiceLoopUiTokens.Stroke.tab.toPx()), ServiceLoopUiTokens.Stroke.tab.toPx()) },
    ) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(constraints.minWidth, 0) {}
        val gapPx = gap.roundToPx()
        val available = (constraints.maxWidth - gapPx * (measurables.size - 1)).coerceAtLeast(0)
        val minimum = measurables.map { it.minIntrinsicWidth(Constraints.Infinity).coerceAtLeast(1) }
        val natural = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity).coerceAtLeast(1) }
        val minimumTotal = minimum.sum()
        val widths = if (minimumTotal >= available) {
            minimum.map { (available.toLong() * it / minimumTotal).toInt().coerceAtLeast(1) }.toMutableList()
        } else {
            val allocated = minimum.toMutableList()
            var remaining = available - minimumTotal
            val desiredExtras = natural.mapIndexed { index, width -> (width - minimum[index]).coerceAtLeast(0) }
            val desiredTotal = desiredExtras.sum()
            val towardNatural = minOf(remaining, desiredTotal)
            if (towardNatural > 0 && desiredTotal > 0) {
                desiredExtras.forEachIndexed { index, extra -> allocated[index] += (towardNatural.toLong() * extra / desiredTotal).toInt() }
                remaining -= allocated.sum() - minimumTotal
            }
            if (remaining > 0) allocated.indices.forEach { index -> allocated[index] += remaining / allocated.size + if (index < remaining % allocated.size) 1 else 0 }
            allocated
        }.also { allocated ->
            val difference = available - allocated.sum()
            if (allocated.isNotEmpty()) allocated[allocated.lastIndex] = (allocated.last() + difference).coerceAtLeast(1)
        }
        val height = (measurables.mapIndexed { index, measurable -> measurable.maxIntrinsicHeight(widths[index]) }
            .maxOrNull() ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeables = measurables.mapIndexed { index, measurable -> measurable.measure(Constraints.fixed(widths[index], height)) }
        layout(constraints.maxWidth, height) {
            var x = 0
            placeables.forEach { placeable -> placeable.placeRelative(x, 0); x += placeable.width + gapPx }
        }
    }
}

/** Keeps a small action family readable by giving longer labels the width they need. */
@Composable
fun ServiceLoopAdaptiveActionRow(
    actions: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stack = maxWidth < 300.dp || LocalDensity.current.fontScale >= 1.8f
        if (stack) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm),
            ) {
                actions.forEach { action -> action() }
            }
        } else {
            val gap = ServiceLoopUiTokens.Space.sm
            Layout(
                content = { actions.forEach { action -> action() } },
                modifier = Modifier.fillMaxWidth(),
            ) { measurables, constraints ->
                if (measurables.isEmpty()) return@Layout layout(constraints.minWidth, 0) {}
                val gapPx = gap.roundToPx()
                val available = (constraints.maxWidth - gapPx * (measurables.size - 1)).coerceAtLeast(0)
                val minimum = measurables.map { it.minIntrinsicWidth(Constraints.Infinity).coerceAtLeast(1) }
                val natural = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity).coerceAtLeast(1) }
                val minimumTotal = minimum.sum()
                val widths = if (minimumTotal >= available) {
                    minimum.map { (available.toLong() * it / minimumTotal).toInt().coerceAtLeast(1) }.toMutableList()
                } else {
                    val allocated = minimum.toMutableList()
                    var remaining = available - minimumTotal
                    val desiredExtras = natural.mapIndexed { index, width -> (width - minimum[index]).coerceAtLeast(0) }
                    val desiredTotal = desiredExtras.sum()
                    val towardNatural = minOf(remaining, desiredTotal)
                    if (towardNatural > 0 && desiredTotal > 0) {
                        desiredExtras.forEachIndexed { index, extra ->
                            allocated[index] += (towardNatural.toLong() * extra / desiredTotal).toInt()
                        }
                        remaining -= allocated.sum() - minimumTotal
                    }
                    if (remaining > 0) {
                        allocated.indices.forEach { index ->
                            allocated[index] += remaining / allocated.size + if (index < remaining % allocated.size) 1 else 0
                        }
                    }
                    allocated
                }.also { allocated ->
                    val difference = available - allocated.sum()
                    if (allocated.isNotEmpty()) allocated[allocated.lastIndex] = (allocated.last() + difference).coerceAtLeast(1)
                }
                val height = measurables.mapIndexed { index, measurable -> measurable.maxIntrinsicHeight(widths[index]) }
                    .maxOrNull()
                    ?.coerceIn(constraints.minHeight, constraints.maxHeight)
                    ?: constraints.minHeight
                val placeables = measurables.mapIndexed { index, measurable ->
                    measurable.measure(Constraints.fixed(widths[index], height))
                }
                layout(constraints.maxWidth, height) {
                    var x = 0
                    placeables.forEach { placeable ->
                        placeable.placeRelative(x, 0)
                        x += placeable.width + gapPx
                    }
                }
            }
        }
    }
}

@Composable
fun <T> ServiceLoopChoiceGroup(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)) {
        options.forEach { (value, label) ->
            ServiceLoopSelectionOption(
                selected = value == selected,
                onClick = { onSelected(value) },
                label = label,
                enabled = enabled,
                modifier = if (testTagPrefix == null) Modifier else Modifier.testTag("$testTagPrefix-$value"),
            )
        }
    }
}

@Composable
fun ServiceSectionStripe(title: String, modifier: Modifier = Modifier, testTag: String? = null) {
    val tokens = LocalServiceLoopTokens.current
    val dark = tokens.canvas == ServiceLoopUiTokens.DarkColors.canvas
    val background = if (dark) Color(0xFFD5DFE2) else Color(0xFF182A30)
    val ink = if (dark) Color(0xFF10191C) else Color.White
    Box(
        modifier.fillMaxWidth().background(background)
            .heightIn(min = ServiceLoopUiTokens.Size.touchMin)
            .padding(horizontal = ServiceLoopUiTokens.Space.md, vertical = ServiceLoopUiTokens.Space.xs)
            .semantics { heading() }
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag)),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = ink, style = ServiceLoopUiTokens.Type.label, textAlign = TextAlign.Center)
    }
}
/** A compact, wrapped single-choice family for short numeric, time, and unit presets. */
@Composable
fun <T> ServiceLoopPresetChoiceGroup(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
    enabled: Boolean = true,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm),
        verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm),
    ) {
        options.forEach { (value, label) ->
            ServiceLoopPresetChoice(
                selected = value == selected,
                onClick = { onSelected(value) },
                label = label,
                enabled = enabled,
                modifier = if (testTagPrefix == null) Modifier else Modifier.testTag("$testTagPrefix-$value"),
            )
        }
    }
}

@Composable
fun ServiceLoopPresetChoice(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalServiceLoopTokens.current
    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = ServiceLoopUiTokens.Size.touchMin, minHeight = ServiceLoopUiTokens.Size.touchMin)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.pill),
        color = if (selected) c.selection else c.surface,
        border = BorderStroke(ServiceLoopUiTokens.Stroke.outline, if (selected) c.selectionOutline else c.outlineControl),
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = ServiceLoopUiTokens.Size.touchMin).padding(horizontal = ServiceLoopUiTokens.Space.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = when {
                    !enabled -> c.disabledText
                    selected -> c.selectionInk
                    else -> c.textPrimary
                },
                style = ServiceLoopUiTokens.Type.label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                softWrap = false,
            )
        }
    }
}

@Composable
fun ServiceLoopSelectionOption(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalServiceLoopTokens.current
    Surface(
        modifier = modifier.fillMaxWidth()
            .heightIn(min = ServiceLoopUiTokens.Size.touchMin)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field),
        color = if (selected) c.selection else c.surface,
        border = BorderStroke(ServiceLoopUiTokens.Stroke.outline, if (selected) c.selectionOutline else c.outlineControl),
    ) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = ServiceLoopUiTokens.Space.md, vertical = ServiceLoopUiTokens.Space.sm),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                label,
                color = when {
                    !enabled -> c.disabledText
                    selected -> c.selectionInk
                    else -> c.textPrimary
                },
                style = ServiceLoopUiTokens.Type.label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/** Compatibility wrapper for older callers; ordinary choices use the framed selection primitive. */
@Composable
fun ServiceLoopChoiceChip(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    ServiceLoopSelectionOption(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
    )
}

/** A checklist answer selector. It keeps answer state visually distinct from Service commands. */
@Composable
fun ServiceLoopChecklistChoice(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ServiceLoopSelectionOption(selected, onClick, label, modifier, enabled)
}

@Composable
fun ServiceLoopPrivateLabel(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    val colors = LocalServiceLoopTokens.current
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs),
    ) {
        ServiceLoopIcon(ServiceLoopIcons.EyeSlash, null, Modifier.size(ServiceLoopUiTokens.Size.iconSmall), colors.textSecondary)
        Text(text, style = style, color = colors.textSecondary)
    }
}

/** A compact, menu-backed selector for a stable Work-filter dimension. */
@Composable
fun <T> ServiceLoopFilterSelector(
    label: String,
    selected: T,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    val colors = LocalServiceLoopTokens.current
    val selectorSurface = colors.surface
    val primaryInk = colors.textPrimary
    val secondaryInk = colors.textSecondary
    val accentInk = colors.action
    val selectedContainer = colors.selection
    val selectorOutline = serviceLoopFilterSelectorOutline(colors)
    val selectedLabel = options.firstOrNull { it.first == selected }?.second.orEmpty()
    Box(modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field),
            color = selectorSurface,
            border = BorderStroke(ServiceLoopUiTokens.Stroke.outline, selectorOutline),
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = ServiceLoopUiTokens.Size.fieldMin)
                .then(if (testTag == null) Modifier else Modifier.testTag(testTag))
                .semantics { contentDescription = "$label, $selectedLabel" },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(
                    horizontal = ServiceLoopUiTokens.Space.md,
                    vertical = ServiceLoopUiTokens.Space.sm,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
                    Text(label, style = ServiceLoopUiTokens.Type.meta, color = secondaryInk)
                    Text(selectedLabel, style = ServiceLoopUiTokens.Type.label, color = primaryInk)
                }
                ServiceLoopIcon(
                    ServiceLoopIcons.Dropdown,
                    null,
                    Modifier.size(ServiceLoopUiTokens.Size.icon),
                    accentInk,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(
                min = ServiceLoopUiTokens.Size.menuMinWidth,
                max = ServiceLoopUiTokens.Size.menuMaxWidth,
            ),
            containerColor = selectorSurface,
        ) {
            options.forEach { (value, optionLabel) ->
                val isSelected = value == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            optionLabel,
                            style = ServiceLoopUiTokens.Type.body.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            ),
                            color = if (isSelected) accentInk else primaryInk,
                        )
                    },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                    leadingIcon = {
                        if (isSelected) {
                            ServiceLoopIcon(
                                ServiceLoopIcons.SelectionCheck,
                                null,
                                Modifier.size(ServiceLoopFilterSelectorContract.menuCheckSize),
                                accentInk,
                            )
                        } else {
                            Spacer(Modifier.width(ServiceLoopFilterSelectorContract.menuCheckSize))
                        }
                    },
                    modifier = Modifier
                        .background(if (isSelected) selectedContainer else Color.Transparent)
                        .then(if (testTag == null) Modifier else Modifier.testTag("$testTag-option-${optionLabel.filter { it.isLetterOrDigit() }.lowercase()}"))
                        .semantics { this.selected = isSelected },
                )
            }
        }
    }
}

@Composable
fun ServiceLoopFilterSelectorRow(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stack = maxWidth < ServiceLoopUiTokens.Size.pairMinCellWidth * 2 + ServiceLoopUiTokens.Layout.pairGap ||
            LocalDensity.current.fontScale >= ServiceLoopUiTokens.Layout.fontScaleStackThreshold
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Layout.pairGap)) {
                first()
                second()
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Layout.pairGap)) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        }
    }
}

@Composable
fun ServiceLoopSavedStatus(atEpochMillis: Long, modifier: Modifier = Modifier, iconSize: Dp = ServiceLoopUiTokens.Size.icon) {
    val time = remember(atEpochMillis) { java.time.Instant.ofEpochMilli(atEpochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0).toString() }
    val c = LocalServiceLoopTokens.current
    Row(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)) {
        ServiceLoopIcon(ServiceLoopIcons.LocalSaved, null, Modifier.size(iconSize), c.successInk)
        Text("Saved on this device · $time", color = c.successInk, style = ServiceLoopUiTokens.Type.supporting)
    }
}

@Composable
fun ServiceLoopSectionDivider(modifier: Modifier = Modifier) = HorizontalDivider(modifier.padding(vertical = ServiceLoopUiTokens.Space.md), color = LocalServiceLoopTokens.current.outlineDecorative)

@Composable
fun ServiceLoopEntityRecord(
    title: String,
    context: String? = null,
    metadata: String? = null,
    status: String? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    actionDescription: String? = null,
    selectionChecked: Boolean? = null,
    onSelectionChange: ((Boolean) -> Unit)? = null,
    operationalState: OperationalWorkState? = null,
    onClick: () -> Unit,
) {
    val c = LocalServiceLoopTokens.current
    val operationalPalette = operationalState?.let { OperationalWorkColors.forState(it, c) }
    val shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field)
    Box(
        modifier.fillMaxWidth().heightIn(min = ServiceLoopUiTokens.Size.listRowMin)
            .serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field).clip(shape)
            .background(if (selected && operationalPalette == null) c.selection else c.surface)
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = operationalPalette?.accent ?: if (selected) c.selectionOutline else c.recordBorder,
                    cornerRadius = CornerRadius(ServiceLoopUiTokens.Radius.field.toPx()),
                    style = Stroke(
                        width = if (selected && operationalPalette == null) ServiceLoopUiTokens.Stroke.selected.toPx() else ServiceLoopUiTokens.Stroke.record.toPx(),
                        pathEffect = if (selected && operationalPalette == null) null else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    ),
                )
            }
            .testTag("entity-record-card")
            .clickable(role = Role.Button, onClick = onClick).focusable()
            .semantics { contentDescription = actionDescription ?: "Open $title" },
    ) {
        val selectable = selectionChecked != null && onSelectionChange != null
        Column(Modifier.fillMaxWidth().padding(ServiceLoopUiTokens.Space.lg), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)) {
                Text(
                    title,
                    style = ServiceLoopUiTokens.Type.itemTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                        .then(if (selectable) Modifier.padding(start = ServiceLoopUiTokens.Space.major) else Modifier)
                        .testTag("entity-record-title"),
                )
                status?.takeIf(String::isNotBlank)?.let { ServiceLoopStatusBadge(it) }
                ServiceLoopIcon(ServiceLoopIcons.Disclosure, null, Modifier.size(ServiceLoopUiTokens.Size.icon), c.icon)
            }
            context?.takeIf(String::isNotBlank)?.let { Text(it, style = ServiceLoopUiTokens.Type.supporting, color = c.textSecondary, modifier = Modifier.fillMaxWidth().testTag("entity-record-context")) }
            metadata?.takeIf(String::isNotBlank)?.let { Text(it, style = ServiceLoopUiTokens.Type.meta, color = c.textMuted, modifier = Modifier.fillMaxWidth()) }
        }
        if (selectable) {
            Box(
                Modifier.align(Alignment.TopStart).size(ServiceLoopUiTokens.Size.touchMin)
                    .testTag("entity-record-selection")
                    .serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field)
                    .toggleable(
                        value = selectionChecked!!,
                        onValueChange = onSelectionChange,
                        role = Role.Checkbox,
                    )
                    .semantics { contentDescription = "Select $title" },
                contentAlignment = Alignment.TopStart,
            ) {
                ServiceLoopIcon(
                    if (selectionChecked) ServiceLoopIcons.SelectionChecked else ServiceLoopIcons.SelectionEmpty,
                    null,
                    Modifier.padding(start=ServiceLoopUiTokens.Space.sm, top=ServiceLoopUiTokens.Space.sm)
                        .size(ServiceLoopUiTokens.Size.checkboxGlyph).testTag("entity-record-selection-icon"),
                    if (selectionChecked) c.action else c.recordBorder.copy(alpha=.55f),
                )
            }
        }
    }
}

@Composable
fun ServiceLoopStatusBadge(code: String, modifier: Modifier = Modifier) {
    val style = serviceLoopStateStyle(code, LocalServiceLoopTokens.current)
    Text(
        style.label,
        color = style.ink,
        style = ServiceLoopUiTokens.Type.badge,
        modifier = modifier
            .clip(RoundedCornerShape(ServiceLoopUiTokens.Radius.badge))
            .background(style.container)
            .padding(horizontal = ServiceLoopUiTokens.Space.sm, vertical = ServiceLoopUiTokens.Space.xs),
    )
}

enum class ServiceLoopNoticeKind { Info, Warning, Error, Success, Working }

@Composable
fun ServiceLoopNotice(title: String, body: String? = null, kind: ServiceLoopNoticeKind, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = LocalServiceLoopTokens.current
    val pair = when (kind) {
        ServiceLoopNoticeKind.Info -> c.infoContainer to c.infoInk
        ServiceLoopNoticeKind.Warning -> c.warningContainer to c.warningInk
        ServiceLoopNoticeKind.Error -> c.errorContainer to c.errorInk
        ServiceLoopNoticeKind.Success -> c.successContainer to c.successInk
        ServiceLoopNoticeKind.Working -> c.workingContainer to c.workingInk
    }
    Row(
        modifier.fillMaxWidth().semantics { if(kind==ServiceLoopNoticeKind.Error || kind==ServiceLoopNoticeKind.Success) liveRegion=LiveRegionMode.Polite }.background(pair.first, RoundedCornerShape(ServiceLoopUiTokens.Radius.field)).padding(ServiceLoopUiTokens.Space.lg),
        horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.md),verticalAlignment=Alignment.Top,
    ) {
        ServiceLoopIcon(
            when(kind){
                ServiceLoopNoticeKind.Error -> ServiceLoopIcons.Error
                ServiceLoopNoticeKind.Success -> ServiceLoopIcons.LocalSaved
                ServiceLoopNoticeKind.Warning -> ServiceLoopIcons.Warning
                ServiceLoopNoticeKind.Info -> ServiceLoopIcons.Info
                ServiceLoopNoticeKind.Working -> ServiceLoopIcons.Time
            }, null, Modifier.size(ServiceLoopUiTokens.Size.icon), pair.second,
        )
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.md)){
            Text(title, color = pair.second, style = MaterialTheme.typography.titleMedium)
            body?.let { Text(it, color = pair.second, style = MaterialTheme.typography.bodyLarge) }
            action?.invoke()
        }
    }
}


@Composable
fun ServiceLoopSurfaceCard(modifier: Modifier = Modifier, selected: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalServiceLoopTokens.current
    val borderColor = if (selected) c.selectionOutline else c.outlineDecorative
    val borderWidth = if (selected) ServiceLoopUiTokens.Stroke.selected else ServiceLoopUiTokens.Stroke.outline
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.card),
        color = if (selected) c.selection else c.surface,
        tonalElevation = ServiceLoopUiTokens.Elevation.rest,
        shadowElevation = ServiceLoopUiTokens.Elevation.rest,
        border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
    ) { Column(Modifier.padding(ServiceLoopUiTokens.Layout.cardPadding), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm), content = content) }
}

@Composable
fun ServiceLoopDenseNavigableRow(
    title:String,context:String?=null,metadata:String?=null,status:String?=null,actionLabel:String?=null,
    modifier:Modifier=Modifier,leadingIcon:Int?=null,
    leadingContent:(@Composable RowScope.()->Unit)?=null,
    showDisclosure:Boolean=true,selected:Boolean=false,statusContent:(@Composable ()->Unit)?=null,showDivider:Boolean=true,
    selectedBackground:Boolean=true,
    contentPadding: PaddingValues = PaddingValues(vertical = ServiceLoopUiTokens.Space.md),
    disclosureIcon: Int = ServiceLoopIcons.Disclosure,
    disclosureTestTag: String? = null,
    onClick:(()->Unit)?,
) {
    val c=LocalServiceLoopTokens.current
    Row(
        modifier.fillMaxWidth().heightIn(min=ServiceLoopUiTokens.Size.listRowMin)
            .then(if (selected && selectedBackground) Modifier.background(c.selection, RoundedCornerShape(ServiceLoopUiTokens.Radius.field)) else Modifier)
            .then(if (onClick != null) Modifier.serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field).clickable(role=Role.Button,onClick=onClick).focusable() else Modifier)
            .semantics { this.selected = selected }
            .then(if (showDivider) Modifier.drawBehind{drawLine(c.outlineDecorative,Offset(0f,size.height),Offset(size.width,size.height),ServiceLoopUiTokens.Stroke.divider.toPx())} else Modifier)
            .padding(contentPadding),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.md),
    ) {
        leadingIcon?.let{ServiceLoopIcon(it,null,Modifier.size(ServiceLoopUiTokens.Size.icon),c.icon)}
        leadingContent?.invoke(this)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
            Text(title,style=ServiceLoopUiTokens.Type.itemTitle)
            context?.takeIf{it.isNotBlank()}?.let{Text(it,style=ServiceLoopUiTokens.Type.supporting,color=c.textSecondary)}
            metadata?.takeIf{it.isNotBlank()}?.let{Text(it,style=ServiceLoopUiTokens.Type.meta,color=c.textMuted)}
            statusContent?.invoke() ?: status?.takeIf{it.isNotBlank()}?.let{ServiceLoopStatusBadge(it)}
            actionLabel?.takeIf{it.isNotBlank()}?.let{Text(it,style=ServiceLoopUiTokens.Type.meta,color=c.action)}
        }
        if(showDisclosure) ServiceLoopIcon(disclosureIcon,null,Modifier.size(ServiceLoopUiTokens.Size.icon).testTag(disclosureTestTag ?: "service-loop-disclosure-icon"),c.icon)
    }
}

@Composable
fun ServiceLoopDashboardGateway(title:String,count:Int,supporting:String,leadingIcon:Int,warning:Boolean=false,modifier:Modifier=Modifier,onClick:()->Unit) {
    val c=LocalServiceLoopTokens.current
    Surface(onClick=onClick,modifier=modifier.fillMaxWidth().testTag("dashboard-gateway-${title.lowercase().replace(' ','-')}")
        .semantics{role=Role.Button},shape=RoundedCornerShape(ServiceLoopUiTokens.Radius.card),color=c.surface,
        border=BorderStroke(ServiceLoopUiTokens.Stroke.outline,c.outlineDecorative)) {
        Row(Modifier.fillMaxWidth().heightIn(min=ServiceLoopUiTokens.Size.listRowMin).padding(ServiceLoopUiTokens.Space.lg),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.md)) {
            val accent=if(warning)c.warningInk else c.action
            ServiceLoopIcon(leadingIcon,null,Modifier.size(ServiceLoopUiTokens.Size.icon),accent)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
                Text("$title · $count",style=ServiceLoopUiTokens.Type.itemTitle,color=if(warning)c.warningInk else c.textPrimary)
                Text(supporting,style=ServiceLoopUiTokens.Type.supporting,color=c.textSecondary)
            }
            ServiceLoopIcon(ServiceLoopIcons.Disclosure,null,Modifier.size(ServiceLoopUiTokens.Size.icon),c.icon)
        }
    }
}

@Composable
fun ServiceLoopAttentionRow(title:String,detail:String,modifier:Modifier=Modifier,onClick:()->Unit) =
    ServiceLoopDenseNavigableRow(title=title,context=detail,modifier=modifier.testTag("attention-row"),leadingIcon=ServiceLoopIcons.Warning,onClick=onClick)

@Composable
fun ServiceLoopVersionRow(title:String,state:String,provenance:String,modifier:Modifier=Modifier,onClick:()->Unit) =
    ServiceLoopDenseNavigableRow(title=title,context=state,metadata=provenance,modifier=modifier.testTag("version-row"),onClick=onClick)

@Composable
fun ServiceLoopWorkItemRow(title:String,service:String,metadata:String,navigable:Boolean,modifier:Modifier=Modifier,onClick:()->Unit) {
    val c=LocalServiceLoopTokens.current
    val base=modifier.fillMaxWidth().heightIn(min=ServiceLoopUiTokens.Size.listRowMin)
        .drawBehind{drawLine(c.outlineDecorative,Offset(0f,size.height),Offset(size.width,size.height),ServiceLoopUiTokens.Stroke.divider.toPx())}
    val interactive=if(navigable) base.serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field).clickable(role=Role.Button,onClick=onClick).focusable() else base
    Row(interactive.padding(vertical=ServiceLoopUiTokens.Space.md),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.md)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
            Text(title,style=ServiceLoopUiTokens.Type.itemTitle)
            Text(service,style=ServiceLoopUiTokens.Type.supporting,color=c.textSecondary)
            Text(metadata,style=ServiceLoopUiTokens.Type.meta,color=c.textMuted)
        }
        if(navigable) ServiceLoopIcon(ServiceLoopIcons.Disclosure,null,Modifier.size(ServiceLoopUiTokens.Size.icon),c.icon)
    }
}

@Composable
fun ServiceLoopPickerSummary(
    label: String,
    value: String,
    supporting: String? = null,
    required: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalServiceLoopTokens.current
    val shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field)
    Row(
        modifier.fillMaxWidth().heightIn(min = ServiceLoopUiTokens.Size.fieldMin).serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field).clip(shape)
            .border(ServiceLoopUiTokens.Stroke.outline, c.outlineDecorative, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(ServiceLoopUiTokens.Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
            Text(label + if (required) " · Required" else "", color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
            supporting?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.textSecondary, style = MaterialTheme.typography.bodyMedium) }
        }
        if (enabled) ServiceLoopIcon(ServiceLoopIcons.Disclosure, null, Modifier.size(ServiceLoopUiTokens.Size.icon), c.icon)
    }
}
