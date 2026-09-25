package com.v16studio.v16service.ui.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.stateDescription
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
import com.v16studio.v16service.ui.icons.V16ServiceIcon
import com.v16studio.v16service.ui.icons.V16ServiceIcons
import com.v16studio.v16service.domain.OperationalWorkState

@Composable
fun V16ServiceSectionHeading(title: String, trailing: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}


@Composable
fun V16ServiceBackAction(onBack: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onBack, modifier = modifier.heightIn(min = V16ServiceUiTokens.Size.touchMin)) {
        V16ServiceIcon(V16ServiceIcons.Back, null, Modifier.size(V16ServiceUiTokens.Size.icon), LocalContentColor.current)
        Spacer(Modifier.width(V16ServiceUiTokens.Space.xs))
        Text("Back")
    }
}

@Composable
fun V16ServiceDetailToolbar(title: String, onBack: () -> Unit, topAction: (@Composable RowScope.() -> Unit)? = null) {
    var leftWidthPx by remember { mutableStateOf(0) }
    var rightWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = V16ServiceUiTokens.Size.topBarMin)) {
        val sideReserve = with(density) { maxOf(leftWidthPx, rightWidthPx).toDp() }
        val stackTitle = maxWidth - sideReserve * 2 < 160.dp
        if (stackTitle) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    V16ServiceBackAction(onBack, Modifier.onSizeChanged { leftWidthPx = it.width })
                    topAction?.let { action -> Row(Modifier.onSizeChanged { rightWidthPx = it.width }, content = action) }
                }
                Text(title, style = V16ServiceUiTokens.Type.screenTitle, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = V16ServiceUiTokens.Space.lg, vertical = V16ServiceUiTokens.Space.sm))
            }
        } else {
            Box(Modifier.fillMaxWidth().heightIn(min = V16ServiceUiTokens.Size.topBarMin)) {
                Text(title, style = V16ServiceUiTokens.Type.screenTitle, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = sideReserve + V16ServiceUiTokens.Space.sm))
                V16ServiceBackAction(onBack, Modifier.align(Alignment.CenterStart).onSizeChanged { leftWidthPx = it.width })
                topAction?.let { action -> Row(Modifier.align(Alignment.CenterEnd).onSizeChanged { rightWidthPx = it.width }, content = action) }
            }
        }
    }
}

/** Shared geometry for the three root workspace tab strips. */
@Composable
fun <T> V16ServiceRootSecondaryTabs(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    testTagPrefix: String? = null,
) {
    val colors = LocalV16ServiceTokens.current
    Box(
        Modifier.fillMaxWidth().height(64.dp).background(colors.surface).padding(top = 8.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        V16ServiceContentTabs(options, selected, onSelected, testTagPrefix = testTagPrefix)
    }
}

@Composable
fun <T> V16ServiceContentTabs(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
    enabled: Boolean = true,
) {
    val c = LocalV16ServiceTokens.current
    val gap = V16ServiceUiTokens.Space.xs
    Layout(
        content = {
            options.forEach { (value, label) ->
                val active = value == selected
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    Modifier.heightIn(min = V16ServiceUiTokens.Size.touchMin).testTag(testTagPrefix?.let { "$it-$value" } ?: "content-tab-$label")
                        .clip(RoundedCornerShape(topStart = V16ServiceUiTokens.Radius.field, topEnd = V16ServiceUiTokens.Radius.field))
                        .background(if (active) c.canvas else Color.Transparent)
                        .drawBehind {
                            if (active) {
                                val stroke = V16ServiceUiTokens.Stroke.tab.toPx()
                                val radius = V16ServiceUiTokens.Radius.field.toPx()
                                drawLine(c.outlineDecorative, Offset(0f, size.height), Offset(0f, radius), stroke)
                                drawArc(c.outlineDecorative, 180f, 90f, false, Offset.Zero, Size(radius * 2, radius * 2), style = Stroke(stroke))
                                drawLine(c.outlineDecorative, Offset(radius, 0f), Offset(size.width - radius, 0f), stroke)
                                drawArc(c.outlineDecorative, 270f, 90f, false, Offset(size.width - radius * 2, 0f), Size(radius * 2, radius * 2), style = Stroke(stroke))
                                drawLine(c.outlineDecorative, Offset(size.width, radius), Offset(size.width, size.height), stroke)
                            }
                        }
                        .v16ServiceFocusRing(V16ServiceUiTokens.Radius.field)
                        .clickable(enabled = enabled, role = Role.Tab, interactionSource = interactionSource, indication = null) { onSelected(value) }.focusable()
                        .semantics { this.role = Role.Tab; this.selected = active; if (!enabled) disabled() }
                        .padding(horizontal = V16ServiceUiTokens.Space.xs, vertical = V16ServiceUiTokens.Space.md),
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = V16ServiceUiTokens.Type.label, color = if (active) c.action else c.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, softWrap = true, maxLines = 2) }
            }
        },
        modifier = modifier.fillMaxWidth()
            .drawBehind { drawLine(c.outlineDecorative, Offset(0f, size.height - V16ServiceUiTokens.Stroke.tab.toPx()), Offset(size.width, size.height - V16ServiceUiTokens.Stroke.tab.toPx()), V16ServiceUiTokens.Stroke.tab.toPx()) },
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
fun V16ServiceAdaptiveActionRow(
    actions: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = V16ServiceUiTokens.Space.sm
        Layout(
            content = { actions.forEach { action -> action() } },
            modifier = Modifier.fillMaxWidth().widthIn(max = maxWidth),
        ) { measurables, constraints ->
            if (measurables.isEmpty()) return@Layout layout(constraints.minWidth, 0) {}
            val gapPx = gap.roundToPx()
            val touchMinPx = V16ServiceUiTokens.Size.touchMin.roundToPx()
            val intrinsicWidths = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity).coerceAtLeast(touchMinPx) }
            val preferredRowWidth = intrinsicWidths.fold(0L) { total, width -> total + width.toLong() } + gapPx.toLong() * (measurables.size - 1)
            val rowWidthLimit = if (constraints.hasBoundedWidth) constraints.maxWidth else
                maxOf(constraints.minWidth, preferredRowWidth.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            val minimumWidth = minOf(touchMinPx, rowWidthLimit)
            val natural = intrinsicWidths.map { it.coerceIn(minimumWidth, rowWidthLimit) }
            val rows = mutableListOf<MutableList<Int>>()
            var current = mutableListOf<Int>()
            var currentWidth = 0L
            val rowWidthLimitLong = rowWidthLimit.toLong()
            natural.forEachIndexed { index, width ->
                val nextWidth = if (current.isEmpty()) width.toLong() else currentWidth + gapPx + width
                if (current.isNotEmpty() && nextWidth > rowWidthLimitLong) {
                    rows += current
                    current = mutableListOf()
                    currentWidth = 0L
                }
                current += index
                currentWidth = if (current.size == 1) width.toLong() else currentWidth + gapPx + width
            }
            if (current.isNotEmpty()) rows += current

            val placeables = measurables.mapIndexed { index, measurable ->
                val row = rows.first { index in it }
                val rowNatural = row.sumOf { natural[it].toLong() } + gapPx.toLong() * (row.size - 1)
                val remaining = (rowWidthLimitLong - rowNatural).coerceAtLeast(0L)
                val width = if (row.size == 1) rowWidthLimitLong else natural[index].toLong() + remaining / row.size
                val targetWidth = width.coerceIn(minimumWidth.toLong(), rowWidthLimitLong).toInt()
                measurable.measure(
                    Constraints(
                        minWidth = targetWidth,
                        maxWidth = targetWidth,
                        minHeight = 0,
                        maxHeight = constraints.maxHeight,
                    ),
                )
            }
            val rowHeights = rows.map { row -> row.maxOf { placeables[it].height } }
            val desiredHeight = (rowHeights.sumOf { it.toLong() } + gapPx.toLong() * (rows.size - 1))
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val height = desiredHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(rowWidthLimit, height) {
                var y = 0
                rows.forEachIndexed { rowIndex, row ->
                    var x = 0
                    row.forEach { index ->
                        placeables[index].placeRelative(x, y)
                        x += placeables[index].width + gapPx
                    }
                    y += rowHeights[rowIndex] + gapPx
                }
            }
        }
    }
}

/** The Due Services selection glyph with an invisible 48dp checkbox target. */
@Composable
fun V16ServiceCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    glyphTestTag: String? = null,
) {
    val colors = LocalV16ServiceTokens.current
    val glyphTint = when {
        !enabled && checked -> colors.disabledText.copy(alpha = .55f)
        !enabled -> colors.disabledText.copy(alpha = .28f)
        checked -> colors.action
        else -> colors.recordBorder.copy(alpha = .55f)
    }
    Box(
        modifier = modifier.size(V16ServiceUiTokens.Size.touchMin)
            .then(if (onCheckedChange == null) Modifier else Modifier.toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange))
            .semantics {
                role = Role.Checkbox
                stateDescription = if (checked) "Selected" else "Not selected"
                if (!enabled) disabled()
                contentDescription?.let { this.contentDescription = it }
            },
        contentAlignment = Alignment.Center,
    ) {
        V16ServiceIcon(
            if (checked) V16ServiceIcons.SelectionChecked else V16ServiceIcons.SelectionEmpty,
            null,
            Modifier.size(V16ServiceUiTokens.Size.checkboxGlyph)
                .then(if (glyphTestTag == null) Modifier else Modifier.testTag(glyphTestTag)),
            glyphTint,
        )
    }
}

@Composable
fun <T> V16ServiceChoiceGroup(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
    enabled: Boolean = true,
    selectedCheck: Boolean = false,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm)) {
        options.forEach { (value, label) ->
            V16ServiceSelectionOption(
                selected = value == selected,
                onClick = { onSelected(value) },
                label = label,
                enabled = enabled,
                selectedCheck = selectedCheck,
                modifier = if (testTagPrefix == null) Modifier else Modifier.testTag("$testTagPrefix-$value"),
            )
        }
    }
}

@Composable
fun ServiceSectionStripe(title: String, modifier: Modifier = Modifier, testTag: String? = null) {
    val tokens = LocalV16ServiceTokens.current
    val background = tokens.surfaceSubtle
    val ink = tokens.textPrimary
    Box(
        modifier.fillMaxWidth().background(background)
            .drawBehind { drawLine(tokens.outlineDecorative, Offset(0f, 0f), Offset(size.width, 0f), V16ServiceUiTokens.Stroke.divider.toPx()) }
            .padding(horizontal = V16ServiceUiTokens.Space.md, vertical = V16ServiceUiTokens.Space.xs)
            .semantics { heading() }
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag)),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = ink, style = V16ServiceUiTokens.Type.sectionTitle, textAlign = TextAlign.Center)
    }
}
/** A compact, wrapped single-choice family for short numeric, time, and unit presets. */
@Composable
fun <T> V16ServicePresetChoiceGroup(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
    enabled: Boolean = true,
    singleRow: Boolean = false,
) {
    if (singleRow) {
            val rowModifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm)) {
                options.forEach { (value, label) ->
                    V16ServicePresetChoice(
                        selected = value == selected,
                        onClick = { onSelected(value) },
                        label = label,
                        enabled = enabled,
                        compact = true,
                        modifier = Modifier.widthIn(min = V16ServiceUiTokens.Size.touchMin)
                            .then(if (testTagPrefix == null) Modifier else Modifier.testTag("$testTagPrefix-$value")),
                    )
                }
            }
    } else {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm),
            verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm),
        ) {
            options.forEach { (value, label) ->
                V16ServicePresetChoice(
                    selected = value == selected,
                    onClick = { onSelected(value) },
                    label = label,
                    enabled = enabled,
                    modifier = if (testTagPrefix == null) Modifier else Modifier.testTag("$testTagPrefix-$value"),
                )
            }
        }
    }
}

@Composable
fun V16ServicePresetChoice(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val c = LocalV16ServiceTokens.current
    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = V16ServiceUiTokens.Size.touchMin, minHeight = V16ServiceUiTokens.Size.touchMin)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(V16ServiceUiTokens.Radius.pill),
        color = if (selected) c.selection else c.surface,
        border = BorderStroke(V16ServiceUiTokens.Stroke.outline, if (selected) c.selectionOutline else c.outlineControl),
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = V16ServiceUiTokens.Size.touchMin)
                .padding(horizontal = if (compact) V16ServiceUiTokens.Space.sm else V16ServiceUiTokens.Space.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = when {
                    !enabled -> c.disabledText
                    selected -> c.selectionInk
                    else -> c.textPrimary
                },
                style = V16ServiceUiTokens.Type.label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                softWrap = false,
            )
        }
    }
}

@Composable
fun V16ServiceSelectionOption(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedCheck: Boolean = false,
) {
    val c = LocalV16ServiceTokens.current
    Surface(
        modifier = modifier.fillMaxWidth()
            .heightIn(min = V16ServiceUiTokens.Size.touchMin)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(V16ServiceUiTokens.Radius.field),
        color = if (selected) c.selection else c.surface,
        border = BorderStroke(V16ServiceUiTokens.Stroke.outline, if (selected) c.selectionOutline else c.outlineControl),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = V16ServiceUiTokens.Space.md, vertical = V16ServiceUiTokens.Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedCheck) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    V16ServiceIcon(
                        V16ServiceIcons.CheckFat,
                        null,
                        Modifier.size(24.dp),
                        if (selected) c.action else c.textMuted,
                    )
                }
                Spacer(Modifier.width(V16ServiceUiTokens.Space.sm))
            }
            Text(
                label,
                color = when {
                    !enabled -> c.disabledText
                    selected -> c.selectionInk
                    else -> c.textPrimary
                },
                style = V16ServiceUiTokens.Type.label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/** Compatibility wrapper for older callers; ordinary choices use the framed selection primitive. */
@Composable
fun V16ServiceChoiceChip(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    V16ServiceSelectionOption(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
    )
}

/** A checklist answer selector. It keeps answer state visually distinct from Service commands. */
@Composable
fun V16ServiceChecklistChoice(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    V16ServiceSelectionOption(selected, onClick, label, modifier, enabled)
}

@Composable
fun V16ServicePrivateLabel(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    val colors = LocalV16ServiceTokens.current
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.xs),
    ) {
        V16ServiceIcon(V16ServiceIcons.EyeSlash, null, Modifier.size(V16ServiceUiTokens.Size.iconSmall), colors.textSecondary)
        Text(text, style = style, color = colors.textSecondary)
    }
}

/** A compact, menu-backed selector for a stable Work-filter dimension. */
@Composable
fun <T> V16ServiceFilterSelector(
    label: String,
    selected: T,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    enabled: Boolean = true,
) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    val colors = LocalV16ServiceTokens.current
    val selectorSurface = colors.surface
    val primaryInk = colors.textPrimary
    val secondaryInk = colors.textSecondary
    val accentInk = colors.action
    val selectedContainer = colors.selection
    val selectorOutline = v16ServiceFilterSelectorOutline(colors)
    val selectedLabel = options.firstOrNull { it.first == selected }?.second.orEmpty()
    Box(modifier) {
        Surface(
            onClick = { if (enabled) expanded = true },
            shape = RoundedCornerShape(V16ServiceUiTokens.Radius.field),
            color = selectorSurface,
            border = BorderStroke(V16ServiceUiTokens.Stroke.outline, selectorOutline),
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = V16ServiceUiTokens.Size.fieldMin)
                .then(if (testTag == null) Modifier else Modifier.testTag(testTag))
                .semantics { contentDescription = "$label, $selectedLabel"; if (!enabled) disabled() },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(
                    horizontal = V16ServiceUiTokens.Space.md,
                    vertical = V16ServiceUiTokens.Space.sm,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.xs)) {
                    Text(label, style = V16ServiceUiTokens.Type.meta, color = secondaryInk)
                    Text(selectedLabel, style = V16ServiceUiTokens.Type.label, color = primaryInk)
                }
                V16ServiceIcon(
                    V16ServiceIcons.Dropdown,
                    null,
                    Modifier.size(V16ServiceUiTokens.Size.icon),
                    accentInk,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(
                min = V16ServiceUiTokens.Size.menuMinWidth,
                max = V16ServiceUiTokens.Size.menuMaxWidth,
            ),
            containerColor = selectorSurface,
        ) {
            options.forEach { (value, optionLabel) ->
                val isSelected = value == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            optionLabel,
                            style = V16ServiceUiTokens.Type.body.copy(
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
                            V16ServiceIcon(
                                V16ServiceIcons.SelectionCheck,
                                null,
                                Modifier.size(V16ServiceFilterSelectorContract.menuCheckSize),
                                accentInk,
                            )
                        } else {
                            Spacer(Modifier.width(V16ServiceFilterSelectorContract.menuCheckSize))
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
fun V16ServiceFilterSelectorRow(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stack = maxWidth < V16ServiceUiTokens.Size.pairMinCellWidth * 2 + V16ServiceUiTokens.Layout.pairGap ||
            LocalDensity.current.fontScale >= V16ServiceUiTokens.Layout.fontScaleStackThreshold
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Layout.pairGap)) {
                first()
                second()
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Layout.pairGap)) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        }
    }
}

@Composable
fun V16ServiceSavedStatus(atEpochMillis: Long, modifier: Modifier = Modifier, iconSize: Dp = V16ServiceUiTokens.Size.icon) {
    val time = remember(atEpochMillis) { java.time.Instant.ofEpochMilli(atEpochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0).toString() }
    val c = LocalV16ServiceTokens.current
    Row(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm)) {
        V16ServiceIcon(V16ServiceIcons.LocalSaved, null, Modifier.size(iconSize), c.successInk)
        Text("Saved · $time", color = c.successInk, style = V16ServiceUiTokens.Type.supporting)
    }
}

@Composable
fun V16ServiceSectionDivider(modifier: Modifier = Modifier) = HorizontalDivider(modifier.padding(vertical = V16ServiceUiTokens.Space.md), color = LocalV16ServiceTokens.current.outlineDecorative)

@Composable
fun V16ServiceEntityRecord(
    title: String,
    context: String? = null,
    metadata: String? = null,
    status: String? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    actionDescription: String? = null,
    selectionChecked: Boolean? = null,
    onSelectionChange: ((Boolean) -> Unit)? = null,
    selectionTestTag: String = "entity-record-selection",
    operationalState: OperationalWorkState? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = LocalV16ServiceTokens.current
    val operationalPalette = operationalState?.let { OperationalWorkColors.forState(it, c) }
    val shape = RoundedCornerShape(V16ServiceUiTokens.Radius.field)
    Box(
        modifier.fillMaxWidth().heightIn(min = V16ServiceUiTokens.Size.listRowMin)
            .v16ServiceFocusRing(V16ServiceUiTokens.Radius.field).clip(shape)
            .background(if (selected && operationalPalette == null) c.selection else c.surface)
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = operationalPalette?.accent ?: if (selected) c.selectionOutline else c.recordBorder,
                    cornerRadius = CornerRadius(V16ServiceUiTokens.Radius.field.toPx()),
                    style = Stroke(
                        width = if (selected && operationalPalette == null) V16ServiceUiTokens.Stroke.selected.toPx() else V16ServiceUiTokens.Stroke.record.toPx(),
                        pathEffect = if (selected && operationalPalette == null) null else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    ),
                )
            }
            .testTag("entity-record-card")
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).focusable()
            .semantics { contentDescription = actionDescription ?: "Open $title"; if (!enabled) disabled() },
    ) {
        val selectable = selectionChecked != null && onSelectionChange != null
        Column(Modifier.fillMaxWidth().padding(V16ServiceUiTokens.Space.lg), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.xs)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm)) {
                Text(
                    title,
                    style = V16ServiceUiTokens.Type.itemTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                        .then(if (selectable) Modifier.padding(start = V16ServiceUiTokens.Space.major) else Modifier)
                        .testTag("entity-record-title"),
                )
                status?.takeIf(String::isNotBlank)?.let { V16ServiceStatusBadge(it) }
                V16ServiceIcon(V16ServiceIcons.Disclosure, null, Modifier.size(V16ServiceUiTokens.Size.icon), c.icon)
            }
            context?.takeIf(String::isNotBlank)?.let { Text(it, style = V16ServiceUiTokens.Type.supporting, color = c.textSecondary, modifier = Modifier.fillMaxWidth().testTag("entity-record-context")) }
            metadata?.takeIf(String::isNotBlank)?.let { Text(it, style = V16ServiceUiTokens.Type.meta, color = c.textMuted, modifier = Modifier.fillMaxWidth()) }
        }
        if (selectable) {
            V16ServiceCheckbox(
                checked = selectionChecked!!,
                onCheckedChange = onSelectionChange,
                modifier = Modifier.align(Alignment.TopStart)
                    .testTag(selectionTestTag)
                    .v16ServiceFocusRing(V16ServiceUiTokens.Radius.field),
                enabled = enabled,
                contentDescription = "Select $title",
                glyphTestTag = "entity-record-selection-icon",
            )
        }
    }
}

@Composable
fun V16ServiceStatusBadge(code: String, modifier: Modifier = Modifier) {
    val style = v16ServiceStateStyle(code, LocalV16ServiceTokens.current)
    Text(
        style.label,
        color = style.ink,
        style = V16ServiceUiTokens.Type.badge,
        modifier = modifier
            .clip(RoundedCornerShape(V16ServiceUiTokens.Radius.badge))
            .background(style.container)
            .padding(horizontal = V16ServiceUiTokens.Space.sm, vertical = V16ServiceUiTokens.Space.xs),
    )
}

enum class V16ServiceNoticeKind { Info, Warning, Error, Success, Working }

@Composable
fun V16ServiceNotice(title: String, body: String? = null, kind: V16ServiceNoticeKind, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = LocalV16ServiceTokens.current
    val pair = when (kind) {
        V16ServiceNoticeKind.Info -> c.infoContainer to c.infoInk
        V16ServiceNoticeKind.Warning -> c.warningContainer to c.warningInk
        V16ServiceNoticeKind.Error -> c.errorContainer to c.errorInk
        V16ServiceNoticeKind.Success -> c.successContainer to c.successInk
        V16ServiceNoticeKind.Working -> c.workingContainer to c.workingInk
    }
    Row(
        modifier.fillMaxWidth().semantics { if(kind==V16ServiceNoticeKind.Error || kind==V16ServiceNoticeKind.Success) liveRegion=LiveRegionMode.Polite }.background(pair.first, RoundedCornerShape(V16ServiceUiTokens.Radius.field)).padding(V16ServiceUiTokens.Space.lg),
        horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.md),verticalAlignment=Alignment.Top,
    ) {
        V16ServiceIcon(
            when(kind){
                V16ServiceNoticeKind.Error -> V16ServiceIcons.Error
                V16ServiceNoticeKind.Success -> V16ServiceIcons.LocalSaved
                V16ServiceNoticeKind.Warning -> V16ServiceIcons.Warning
                V16ServiceNoticeKind.Info -> V16ServiceIcons.Info
                V16ServiceNoticeKind.Working -> V16ServiceIcons.Time
            }, null, Modifier.size(V16ServiceUiTokens.Size.icon), pair.second,
        )
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(V16ServiceUiTokens.Space.md)){
            Text(title, color = pair.second, style = MaterialTheme.typography.titleMedium)
            body?.let { Text(it, color = pair.second, style = MaterialTheme.typography.bodyLarge) }
            action?.invoke()
        }
    }
}


@Composable
fun V16ServiceSurfaceCard(modifier: Modifier = Modifier, selected: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalV16ServiceTokens.current
    val borderColor = if (selected) c.selectionOutline else c.outlineDecorative
    val borderWidth = if (selected) V16ServiceUiTokens.Stroke.selected else V16ServiceUiTokens.Stroke.outline
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(V16ServiceUiTokens.Radius.card),
        color = if (selected) c.selection else c.surface,
        tonalElevation = V16ServiceUiTokens.Elevation.rest,
        shadowElevation = V16ServiceUiTokens.Elevation.rest,
        border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
    ) { Column(Modifier.padding(V16ServiceUiTokens.Layout.cardPadding), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm), content = content) }
}

@Composable
fun V16ServiceDenseNavigableRow(
    title:String,context:String?=null,metadata:String?=null,status:String?=null,actionLabel:String?=null,
    modifier:Modifier=Modifier,leadingIcon:Int?=null,
    leadingContent:(@Composable RowScope.()->Unit)?=null,
    showDisclosure:Boolean=true,selected:Boolean=false,statusContent:(@Composable ()->Unit)?=null,showDivider:Boolean=true,
    selectedBackground:Boolean=true,
    contentPadding: PaddingValues = PaddingValues(vertical = V16ServiceUiTokens.Space.md),
    disclosureIcon: Int = V16ServiceIcons.Disclosure,
    disclosureTestTag: String? = null,
    onClick:(()->Unit)?,
) {
    val c=LocalV16ServiceTokens.current
    Row(
        modifier.fillMaxWidth().heightIn(min=V16ServiceUiTokens.Size.listRowMin)
            .then(if (selected && selectedBackground) Modifier.background(c.selection, RoundedCornerShape(V16ServiceUiTokens.Radius.field)) else Modifier)
            .then(if (onClick != null) Modifier.v16ServiceFocusRing(V16ServiceUiTokens.Radius.field).clickable(role=Role.Button,onClick=onClick).focusable() else Modifier)
            .semantics { this.selected = selected }
            .then(if (showDivider) Modifier.drawBehind{drawLine(c.outlineDecorative,Offset(0f,size.height),Offset(size.width,size.height),V16ServiceUiTokens.Stroke.divider.toPx())} else Modifier)
            .padding(contentPadding),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(V16ServiceUiTokens.Space.md),
    ) {
        leadingIcon?.let{V16ServiceIcon(it,null,Modifier.size(V16ServiceUiTokens.Size.icon),c.icon)}
        leadingContent?.invoke(this)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(V16ServiceUiTokens.Space.xs)) {
            Text(title,style=V16ServiceUiTokens.Type.itemTitle)
            context?.takeIf{it.isNotBlank()}?.let{Text(it,style=V16ServiceUiTokens.Type.supporting,color=c.textSecondary)}
            metadata?.takeIf{it.isNotBlank()}?.let{Text(it,style=V16ServiceUiTokens.Type.meta,color=c.textMuted)}
            statusContent?.invoke() ?: status?.takeIf{it.isNotBlank()}?.let{V16ServiceStatusBadge(it)}
            actionLabel?.takeIf{it.isNotBlank()}?.let{Text(it,style=V16ServiceUiTokens.Type.meta,color=c.action)}
        }
        if(showDisclosure) V16ServiceIcon(disclosureIcon,null,Modifier.size(V16ServiceUiTokens.Size.icon).testTag(disclosureTestTag ?: "v16-service-disclosure-icon"),c.icon)
    }
}

@Composable
fun V16ServiceDashboardGateway(title:String,count:Int,supporting:String,leadingIcon:Int,warning:Boolean=false,modifier:Modifier=Modifier,onClick:()->Unit) {
    val c=LocalV16ServiceTokens.current
    Surface(onClick=onClick,modifier=modifier.fillMaxWidth().testTag("dashboard-gateway-${title.lowercase().replace(' ','-')}")
        .semantics{role=Role.Button},shape=RoundedCornerShape(V16ServiceUiTokens.Radius.card),color=c.surface,
        border=BorderStroke(V16ServiceUiTokens.Stroke.outline,c.outlineDecorative)) {
        Row(Modifier.fillMaxWidth().heightIn(min=V16ServiceUiTokens.Size.listRowMin).padding(V16ServiceUiTokens.Space.lg),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(V16ServiceUiTokens.Space.md)) {
            val accent=if(warning)c.warningInk else c.action
            V16ServiceIcon(leadingIcon,null,Modifier.size(V16ServiceUiTokens.Size.icon),accent)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(V16ServiceUiTokens.Space.xs)) {
                Text("$title · $count",style=V16ServiceUiTokens.Type.itemTitle,color=if(warning)c.warningInk else c.textPrimary)
                Text(supporting,style=V16ServiceUiTokens.Type.supporting,color=c.textSecondary)
            }
            V16ServiceIcon(V16ServiceIcons.Disclosure,null,Modifier.size(V16ServiceUiTokens.Size.icon),c.icon)
        }
    }
}

@Composable
fun V16ServiceAttentionRow(title:String,detail:String,modifier:Modifier=Modifier,onClick:()->Unit) =
    V16ServiceDenseNavigableRow(title=title,context=detail,modifier=modifier.testTag("attention-row"),leadingIcon=V16ServiceIcons.Warning,onClick=onClick)

@Composable
fun V16ServiceVersionRow(title:String,state:String,provenance:String,modifier:Modifier=Modifier,onClick:()->Unit) =
    V16ServiceDenseNavigableRow(title=title,context=state,metadata=provenance,modifier=modifier.testTag("version-row"),onClick=onClick)

@Composable
fun V16ServiceWorkItemRow(title:String,service:String,metadata:String,navigable:Boolean,modifier:Modifier=Modifier,onClick:()->Unit) {
    val c=LocalV16ServiceTokens.current
    val base=modifier.fillMaxWidth().heightIn(min=V16ServiceUiTokens.Size.listRowMin)
        .drawBehind{drawLine(c.outlineDecorative,Offset(0f,size.height),Offset(size.width,size.height),V16ServiceUiTokens.Stroke.divider.toPx())}
    val interactive=if(navigable) base.v16ServiceFocusRing(V16ServiceUiTokens.Radius.field).clickable(role=Role.Button,onClick=onClick).focusable() else base
    Row(interactive.padding(vertical=V16ServiceUiTokens.Space.md),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(V16ServiceUiTokens.Space.md)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(V16ServiceUiTokens.Space.xs)) {
            Text(title,style=V16ServiceUiTokens.Type.itemTitle)
            Text(service,style=V16ServiceUiTokens.Type.supporting,color=c.textSecondary)
            Text(metadata,style=V16ServiceUiTokens.Type.meta,color=c.textMuted)
        }
        if(navigable) V16ServiceIcon(V16ServiceIcons.Disclosure,null,Modifier.size(V16ServiceUiTokens.Size.icon),c.icon)
    }
}

@Composable
fun V16ServicePickerSummary(
    label: String,
    value: String,
    supporting: String? = null,
    required: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalV16ServiceTokens.current
    val shape = RoundedCornerShape(V16ServiceUiTokens.Radius.field)
    Row(
        modifier.fillMaxWidth().heightIn(min = V16ServiceUiTokens.Size.fieldMin).v16ServiceFocusRing(V16ServiceUiTokens.Radius.field).clip(shape)
            .border(V16ServiceUiTokens.Stroke.outline, c.outlineDecorative, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(V16ServiceUiTokens.Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.xs)) {
            Text(label + if (required) " · Required" else "", color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
            supporting?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.textSecondary, style = MaterialTheme.typography.bodyMedium) }
        }
        if (enabled) V16ServiceIcon(V16ServiceIcons.Disclosure, null, Modifier.size(V16ServiceUiTokens.Size.icon), c.icon)
    }
}
