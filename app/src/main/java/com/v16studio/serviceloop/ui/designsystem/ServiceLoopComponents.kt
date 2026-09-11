package com.v16studio.serviceloop.ui.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons

@Composable
fun ServiceLoopSectionHeading(title: String, trailing: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}

@Immutable
internal data class TrailSquare(val x: Float, val y: Float, val sizeDp: Float, val alpha: Float)

internal val LeftTrailSquares = listOf(
    TrailSquare(.04f,.42f,2f,.03f), TrailSquare(.12f,.70f,2.5f,.04f), TrailSquare(.20f,.22f,2f,.04f),
    TrailSquare(.28f,.53f,3f,.05f), TrailSquare(.35f,.80f,2.5f,.06f), TrailSquare(.42f,.30f,3.5f,.06f),
    TrailSquare(.48f,.62f,3f,.07f), TrailSquare(.55f,.12f,4f,.07f), TrailSquare(.60f,.44f,3.5f,.08f),
    TrailSquare(.65f,.76f,4.5f,.08f), TrailSquare(.70f,.26f,4f,.09f), TrailSquare(.74f,.56f,5f,.09f),
    TrailSquare(.78f,.86f,3.5f,.10f), TrailSquare(.82f,.12f,4.5f,.10f), TrailSquare(.84f,.42f,5.5f,.11f),
    TrailSquare(.87f,.70f,4f,.12f), TrailSquare(.90f,.25f,5f,.12f), TrailSquare(.92f,.54f,6f,.13f),
    TrailSquare(.94f,.82f,4.5f,.12f), TrailSquare(.96f,.08f,4f,.11f), TrailSquare(.97f,.34f,5.5f,.14f),
    TrailSquare(.98f,.64f,4f,.13f), TrailSquare(.99f,.90f,3.5f,.10f), TrailSquare(.995f,.48f,5f,.15f),
)
internal val RightTrailSquares = listOf(
    TrailSquare(.03f,.58f,5.5f,.15f), TrailSquare(.05f,.18f,4f,.12f), TrailSquare(.07f,.82f,4.5f,.13f),
    TrailSquare(.09f,.39f,6f,.14f), TrailSquare(.12f,.68f,4f,.11f), TrailSquare(.15f,.08f,5f,.12f),
    TrailSquare(.18f,.48f,4.5f,.11f), TrailSquare(.21f,.88f,3.5f,.09f), TrailSquare(.24f,.27f,5f,.10f),
    TrailSquare(.28f,.61f,4f,.09f), TrailSquare(.32f,.13f,4.5f,.08f), TrailSquare(.37f,.76f,3.5f,.08f),
    TrailSquare(.42f,.37f,4f,.07f), TrailSquare(.48f,.66f,3f,.07f), TrailSquare(.54f,.18f,3.5f,.06f),
    TrailSquare(.60f,.50f,3f,.06f), TrailSquare(.67f,.83f,2.5f,.05f), TrailSquare(.72f,.29f,3f,.05f),
    TrailSquare(.78f,.59f,2.5f,.04f), TrailSquare(.84f,.12f,2f,.04f), TrailSquare(.89f,.73f,2.5f,.03f),
    TrailSquare(.94f,.41f,1.5f,.03f), TrailSquare(.98f,.86f,2f,.02f),
)

@Composable
private fun SquareTrail(squares: List<TrailSquare>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        squares.forEach { square ->
            val side = square.sizeDp.dp.toPx()
            drawRect(color.copy(alpha = square.alpha), Offset((size.width-side)*square.x, (size.height-side)*square.y), Size(side, side))
        }
    }
}

@Composable
fun ServiceLoopBrandStrip(modifier: Modifier = Modifier) {
    val c = LocalServiceLoopTokens.current
    Row(
        modifier.fillMaxWidth().heightIn(min = ServiceLoopUiTokens.Size.brandMin)
            .background(c.brandBand).padding(horizontal = ServiceLoopUiTokens.Space.md, vertical = ServiceLoopUiTokens.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquareTrail(LeftTrailSquares, c.textPrimary, Modifier.weight(1f).height(22.dp).testTag("brand-left-trail"))
        Spacer(Modifier.width(ServiceLoopUiTokens.Space.md))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color=c.textPrimary)) { append("Service") }
                withStyle(SpanStyle(color=c.action)) { append("Loop") }
            },
            modifier=Modifier.testTag("serviceloop-wordmark"), fontSize=18.sp, lineHeight=22.sp,
            fontWeight=FontWeight.Bold, letterSpacing=(-.2).sp, maxLines=1, softWrap=false,
        )
        Spacer(Modifier.width(ServiceLoopUiTokens.Space.md))
        SquareTrail(RightTrailSquares, c.action, Modifier.weight(1f).height(22.dp).testTag("brand-right-trail"))
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
) {
    val c = LocalServiceLoopTokens.current
    val gap = ServiceLoopUiTokens.Space.xs
    Layout(
        content = {
            options.forEach { (value, label) ->
                val active = value == selected
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    Modifier.heightIn(min = ServiceLoopUiTokens.Size.touchMin).testTag("content-tab-$label")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ServiceLoopChoiceGroup(options: List<Pair<T, String>>, selected: T, onSelected: (T) -> Unit, modifier: Modifier = Modifier, testTagPrefix: String? = null) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)) {
        options.forEach { (value, label) ->
            ServiceLoopChoiceChip(value == selected, { onSelected(value) }, label, if (testTagPrefix == null) Modifier else Modifier.testTag("$testTagPrefix-$value"))
        }
    }
}

@Composable
fun ServiceLoopChoiceChip(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, softWrap = false, style = ServiceLoopUiTokens.Type.label) },
        modifier = modifier.width(IntrinsicSize.Max).heightIn(min = ServiceLoopUiTokens.Size.touchMin),
    )
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
    val selectedLabel = options.firstOrNull { it.first == selected }?.second.orEmpty()
    Box(modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field),
            color = ServiceLoopFilterSelectorContract.surface,
            border = BorderStroke(ServiceLoopUiTokens.Stroke.outline, ServiceLoopFilterSelectorContract.outline),
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
                    Text(label, style = ServiceLoopUiTokens.Type.meta, color = ServiceLoopFilterSelectorContract.secondaryInk)
                    Text(selectedLabel, style = ServiceLoopUiTokens.Type.label, color = ServiceLoopFilterSelectorContract.primaryInk)
                }
                ServiceLoopIcon(
                    ServiceLoopIcons.Dropdown,
                    null,
                    Modifier.size(ServiceLoopUiTokens.Size.icon),
                    ServiceLoopFilterSelectorContract.accentInk,
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
            containerColor = ServiceLoopFilterSelectorContract.surface,
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
                            color = if (isSelected) ServiceLoopFilterSelectorContract.accentInk else ServiceLoopFilterSelectorContract.primaryInk,
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
                                ServiceLoopFilterSelectorContract.accentInk,
                            )
                        } else {
                            Spacer(Modifier.width(ServiceLoopFilterSelectorContract.menuCheckSize))
                        }
                    },
                    modifier = Modifier
                        .background(if (isSelected) ServiceLoopFilterSelectorContract.selectedContainer else Color.Transparent)
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
fun ServiceLoopSavedStatus(atEpochMillis: Long, modifier: Modifier = Modifier) {
    val time = remember(atEpochMillis) { java.time.Instant.ofEpochMilli(atEpochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0).toString() }
    val c = LocalServiceLoopTokens.current
    Row(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)) {
        ServiceLoopIcon(ServiceLoopIcons.LocalSaved, null, Modifier.size(ServiceLoopUiTokens.Size.icon), c.successInk)
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
    onClick: () -> Unit,
) {
    val c = LocalServiceLoopTokens.current
    val shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field)
    Box(
        modifier.fillMaxWidth().heightIn(min = ServiceLoopUiTokens.Size.listRowMin)
            .serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field).clip(shape)
            .background(if (selected) c.selection else c.surface)
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = if (selected) c.selectionOutline else c.recordBorder,
                    cornerRadius = CornerRadius(ServiceLoopUiTokens.Radius.field.toPx()),
                    style = Stroke(width = if (selected) ServiceLoopUiTokens.Stroke.selected.toPx() else ServiceLoopUiTokens.Stroke.record.toPx(), pathEffect = if (selected) null else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
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
internal fun ServiceLoopButtonContent(
    onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,primary:Boolean,
    busy:Boolean=false,interactionSource:MutableInteractionSource=remember{MutableInteractionSource()},
    content:@Composable RowScope.()->Unit,
) {
    val c=LocalServiceLoopTokens.current
    val pressed by interactionSource.collectIsPressedAsState()
    val container=if(primary)ServiceLoopButtonContract.primaryContainer(c,enabled,pressed) else ServiceLoopButtonContract.secondaryContainer(c,enabled)
    val ink=if(primary)ServiceLoopButtonContract.primaryInk(c,enabled) else ServiceLoopButtonContract.secondaryInk(c,enabled)
    val shape=RoundedCornerShape(ServiceLoopButtonContract.radius)
    Row(
        modifier.heightIn(min=if(primary)ServiceLoopButtonContract.primaryMinHeight else ServiceLoopButtonContract.secondaryMinHeight)
            .serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field).clip(shape).background(container)
            .clickable(enabled=enabled&&!busy,role=Role.Button,interactionSource=interactionSource,indication=LocalIndication.current,onClick=onClick)
            .focusable(enabled=enabled).semantics(mergeDescendants=true) { if(!enabled||busy) disabled() }
            .padding(horizontal=ServiceLoopButtonContract.horizontalPadding,vertical=ServiceLoopButtonContract.verticalPadding),
        horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically,
    ) {
        if(busy) {
            CircularProgressIndicator(Modifier.size(ServiceLoopUiTokens.Size.iconSmall),strokeWidth=ServiceLoopUiTokens.Stroke.focus,color=ink)
            Spacer(Modifier.width(ServiceLoopUiTokens.Space.sm))
        }
        CompositionLocalProvider(LocalContentColor provides ink) {
            ProvideTextStyle(ServiceLoopButtonContract.textStyle) { content() }
        }
    }
}

@Composable fun ServiceLoopPrimaryButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,leadingIcon:(@Composable (() -> Unit))?=null,busy:Boolean=false)=ServiceLoopButtonContent(onClick,modifier,enabled,true,busy){if(!busy)leadingIcon?.let{it();Spacer(Modifier.width(ServiceLoopUiTokens.Space.sm))};Text(label)}
@Composable fun ServiceLoopSecondaryButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,leadingIcon:(@Composable (() -> Unit))?=null,busy:Boolean=false)=ServiceLoopButtonContent(onClick,modifier,enabled,false,busy){if(!busy)leadingIcon?.let{it();Spacer(Modifier.width(ServiceLoopUiTokens.Space.sm))};Text(label)}

/** Full-width operational commands belong in this explicit, consistently spaced vertical group. */
@Composable fun ServiceLoopActionStack(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Layout.buttonGap), content = content)

@Composable fun ServiceLoopDangerTonalButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true) {
    val c=LocalServiceLoopTokens.current
    val shape=RoundedCornerShape(ServiceLoopButtonContract.radius)
    Row(
        modifier.heightIn(min=ServiceLoopButtonContract.secondaryMinHeight).serviceLoopFocusRing(ServiceLoopButtonContract.radius).clip(shape)
            .background(if(enabled)c.errorContainer else c.disabledContainer)
            .clickable(enabled=enabled,role=Role.Button,onClick=onClick).focusable(enabled)
            .semantics(mergeDescendants=true) { if(!enabled) disabled() }
            .padding(horizontal=ServiceLoopButtonContract.horizontalPadding,vertical=ServiceLoopButtonContract.verticalPadding),
        horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically,
    ) { ProvideTextStyle(ServiceLoopButtonContract.textStyle) { Text(label,color=if(enabled)c.errorInk else c.disabledText) } }
}

@Composable fun ServiceLoopDestructiveButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true) {
    val c=LocalServiceLoopTokens.current
    Button(onClick,modifier.heightIn(min=ServiceLoopButtonContract.primaryMinHeight).serviceLoopFocusRing(ServiceLoopButtonContract.radius),enabled,
        shape=RoundedCornerShape(ServiceLoopButtonContract.radius),colors=ButtonDefaults.buttonColors(containerColor=c.destructive,contentColor=c.onDestructive,disabledContainerColor=c.disabledContainer,disabledContentColor=c.disabledText),
        contentPadding=PaddingValues(horizontal=ServiceLoopButtonContract.horizontalPadding,vertical=ServiceLoopButtonContract.verticalPadding)) {
        ProvideTextStyle(ServiceLoopButtonContract.textStyle){ServiceLoopIcon(ServiceLoopIcons.Delete,null,Modifier.size(ServiceLoopUiTokens.Size.icon));Spacer(Modifier.width(ServiceLoopUiTokens.Space.sm));Text(label)}
    }
}

@Composable fun ServiceLoopTextAction(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true) {
    val c=LocalServiceLoopTokens.current
    Box(modifier.heightIn(min=ServiceLoopUiTokens.Size.touchMin).serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field).clip(RoundedCornerShape(ServiceLoopUiTokens.Radius.field)).clickable(enabled=enabled,role=Role.Button,onClick=onClick).focusable(enabled).padding(horizontal=ServiceLoopUiTokens.Space.sm),contentAlignment=Alignment.Center){Text(label,style=ServiceLoopUiTokens.Type.label,color=if(enabled)c.action else c.disabledText)}
}

@Composable fun ServiceLoopIconAction(accessibleName:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:@Composable ()->Unit) {
    Box(modifier.size(ServiceLoopUiTokens.Size.touchMin).serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field).clip(RoundedCornerShape(ServiceLoopUiTokens.Radius.field)).clickable(enabled=enabled,role=Role.Button,onClick=onClick).focusable(enabled).semantics{contentDescription=accessibleName},contentAlignment=Alignment.Center){Box(Modifier.size(ServiceLoopUiTokens.Size.icon),contentAlignment=Alignment.Center){content()}}
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
    showDisclosure:Boolean=true,onClick:()->Unit,
) {
    val c=LocalServiceLoopTokens.current
    Row(
        modifier.fillMaxWidth().heightIn(min=ServiceLoopUiTokens.Size.listRowMin)
            .serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field)
            .clickable(role=Role.Button,onClick=onClick).focusable()
            .drawBehind{drawLine(c.outlineDecorative,Offset(0f,size.height),Offset(size.width,size.height),ServiceLoopUiTokens.Stroke.divider.toPx())}
            .padding(vertical=ServiceLoopUiTokens.Space.md),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.md),
    ) {
        leadingIcon?.let{ServiceLoopIcon(it,null,Modifier.size(ServiceLoopUiTokens.Size.icon),c.icon)}
        leadingContent?.invoke(this)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
            Text(title,style=ServiceLoopUiTokens.Type.itemTitle)
            context?.takeIf{it.isNotBlank()}?.let{Text(it,style=ServiceLoopUiTokens.Type.supporting,color=c.textSecondary)}
            metadata?.takeIf{it.isNotBlank()}?.let{Text(it,style=ServiceLoopUiTokens.Type.meta,color=c.textMuted)}
            status?.takeIf{it.isNotBlank()}?.let{ServiceLoopStatusBadge(it)}
            actionLabel?.takeIf{it.isNotBlank()}?.let{Text(it,style=ServiceLoopUiTokens.Type.meta,color=c.action)}
        }
        if(showDisclosure) ServiceLoopIcon(ServiceLoopIcons.Disclosure,null,Modifier.size(ServiceLoopUiTokens.Size.icon),c.icon)
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

@Composable
fun ServiceLoopTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
) {
    val c = LocalServiceLoopTokens.current
    OutlinedTextField(
        value, onValueChange,
        label = { Text(label + if (required) " · Required" else "") },
        enabled = enabled, singleLine = singleLine, isError = isError, supportingText = supportingText,
        shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.focus,
            unfocusedBorderColor = c.outlineControl,
            cursorColor = c.action,
            errorBorderColor = c.errorInk,
            errorCursorColor = c.errorInk,
            disabledTextColor = c.disabledText,
            disabledBorderColor = c.disabledContainer,
            disabledLabelColor = c.disabledText,
            disabledSupportingTextColor = c.disabledText,
        ),
        textStyle = ServiceLoopUiTokens.Type.body,
        modifier = modifier.fillMaxWidth().heightIn(min = ServiceLoopUiTokens.Size.fieldMin),
    )
}

@Composable
fun ServiceLoopResponsivePair(first: @Composable () -> Unit, second: @Composable () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stack = maxWidth < ServiceLoopUiTokens.Size.narrowThreshold || LocalDensity.current.fontScale >= ServiceLoopUiTokens.Layout.fontScaleStackThreshold || (maxWidth - ServiceLoopUiTokens.Layout.pairGap) / 2 < ServiceLoopUiTokens.Size.pairMinCellWidth
        if (stack) Column(verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Layout.pairGap)) { first(); second() }
        else Row(horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Layout.pairGap)) { Box(Modifier.weight(1f)) { first() }; Box(Modifier.weight(1f)) { second() } }
    }
}

@Composable
fun ServiceLoopLongTextEditor(value: String, onValueChange: (String) -> Unit, label: String, private: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, isError: Boolean = false, fieldTestTag: String? = null) {
    var expanded by remember { mutableStateOf(false) }
    var restoreCompactFocus by remember { mutableStateOf(false) }
    val compactFocusRequester = remember { FocusRequester() }
    var editorValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val c = LocalServiceLoopTokens.current
    val tag = "long-text-" + label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    LaunchedEffect(value) {
        if (value != editorValue.text) {
            val cursor = editorValue.selection.end.coerceAtMost(value.length)
            editorValue = TextFieldValue(value, TextRange(cursor))
        }
    }
    val updateEditor: (TextFieldValue) -> Unit = { next -> editorValue = next; onValueChange(next.text) }
    fun collapse() { expanded = false; restoreCompactFocus = true }
    LaunchedEffect(expanded, restoreCompactFocus) {
        if (!expanded && restoreCompactFocus) {
            withFrameNanos { }
            compactFocusRequester.requestFocus()
            restoreCompactFocus = false
        }
    }
    Column(modifier.fillMaxWidth().padding(bottom = ServiceLoopUiTokens.Space.lg), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            editorValue, updateEditor, label = { Text(label) }, minLines = 3, enabled = enabled, isError = isError,
            shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.focus, unfocusedBorderColor = c.outlineControl, cursorColor = c.action, errorBorderColor = c.errorInk, errorCursorColor = c.errorInk, disabledTextColor = c.disabledText, disabledBorderColor = c.disabledContainer, disabledLabelColor = c.disabledText),
            textStyle = ServiceLoopUiTokens.Type.body,
            suffix = { Spacer(Modifier.width(ServiceLoopUiTokens.Size.editorActionReserve)) },
            modifier = Modifier.fillMaxWidth().focusRequester(compactFocusRequester).testTag(fieldTestTag ?: tag),
        )
        ServiceLoopIconAction(
            "Expand $label",
            { expanded = true },
            enabled = enabled,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = ServiceLoopUiTokens.Space.sm, bottom = ServiceLoopUiTokens.Space.sm).testTag("$tag-expand"),
        ) { ServiceLoopIcon(ServiceLoopIcons.Expand, null, Modifier.size(ServiceLoopUiTokens.Size.icon), LocalServiceLoopTokens.current.action) }
    }
    if (private) Text("PRIVATE · Not included in the customer report", style = MaterialTheme.typography.bodySmall, color = LocalServiceLoopTokens.current.textSecondary)
    }
    if (expanded) {
        BackHandler { collapse() }
        Dialog(onDismissRequest = { collapse() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize(), color = LocalServiceLoopTokens.current.canvas) {
                Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                    ServiceLoopBrandStrip()
                    ServiceLoopDetailToolbar(label, { collapse() })
                    Column(Modifier.fillMaxSize().padding(ServiceLoopUiTokens.Space.lg),horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.md)) {
                        if (private) Text("PRIVATE · Not included in the customer report", color = LocalServiceLoopTokens.current.textSecondary)
                        OutlinedTextField(editorValue, updateEditor,textStyle=ServiceLoopUiTokens.Type.body, modifier = Modifier.fillMaxWidth().widthIn(max=ServiceLoopUiTokens.Size.contentMaxWidth).weight(1f).testTag("$tag-expanded"), shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field))
                        ServiceLoopPrimaryButton("Done",{collapse()},Modifier.fillMaxWidth().widthIn(max=ServiceLoopUiTokens.Size.contentMaxWidth))
                    }
                }
            }
        }
    }
}

fun Modifier.serviceLoopFocusRing(radius: Dp): Modifier = composed {
    val color = LocalServiceLoopTokens.current.focus
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { focused = it.isFocused }.drawWithContent {
        drawContent()
        if (focused) {
            val gap = ServiceLoopButtonContract.focusGap.toPx()
            val stroke = ServiceLoopUiTokens.Stroke.focus.toPx()
            val outset = gap + stroke / 2
            drawRoundRect(
                color = color,
                topLeft = Offset(-outset, -outset),
                size = Size(size.width + outset * 2, size.height + outset * 2),
                cornerRadius = CornerRadius(radius.toPx() + outset),
                style = Stroke(stroke),
            )
        }
    }
}

@Composable
fun ServiceLoopPinnedBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalServiceLoopTokens.current
    Surface(modifier.fillMaxWidth(), color = c.surface, tonalElevation = ServiceLoopUiTokens.Elevation.bottomBar, shadowElevation = ServiceLoopUiTokens.Elevation.bottomBar) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().drawBehind{drawLine(c.outlineDecorative,Offset.Zero,Offset(size.width,0f),ServiceLoopUiTokens.Stroke.divider.toPx())}.navigationBarsPadding().padding(horizontal=serviceLoopPageInset(maxWidth),vertical=ServiceLoopUiTokens.Space.md),
                verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.md), content = content,
            )
        }
    }
}

fun serviceLoopPageInset(width: Dp): Dp = when {
    width >= ServiceLoopUiTokens.Size.expandedThreshold -> ServiceLoopUiTokens.Layout.pageInsetExpanded
    width >= ServiceLoopUiTokens.Size.mediumThreshold -> ServiceLoopUiTokens.Layout.pageInsetMedium
    else -> ServiceLoopUiTokens.Layout.pageInsetCompact
}
