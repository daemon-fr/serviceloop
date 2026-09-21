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
fun <T> ServiceLoopChoicePair(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
    stackWhenLargeFont: Boolean = true,
    enabled: Boolean = true,
) {
    require(options.size == 2) { "A paired choice requires exactly two options" }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stack = maxWidth < 260.dp || (stackWhenLargeFont && LocalDensity.current.fontScale >= 1.8f)
        val first = options[0]
        val second = options[1]
        val firstChoice: @Composable () -> Unit = {
            ServiceLoopSelectionOption(
                selected = first.first == selected,
                onClick = { onSelected(first.first) },
                label = first.second,
                enabled = enabled,
                modifier = if (testTagPrefix == null) Modifier else Modifier.testTag("$testTagPrefix-${first.first}"),
            )
        }
        val secondChoice: @Composable () -> Unit = {
            ServiceLoopSelectionOption(
                selected = second.first == selected,
                onClick = { onSelected(second.first) },
                label = second.second,
                enabled = enabled,
                modifier = if (testTagPrefix == null) Modifier else Modifier.testTag("$testTagPrefix-${second.first}"),
            )
        }
        if (stack) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Layout.pairGap)) {
                firstChoice()
                secondChoice()
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Layout.pairGap)) {
                Box(Modifier.weight(1f)) { firstChoice() }
                Box(Modifier.weight(1f)) { secondChoice() }
            }
        }
    }
}

@Composable
fun ServiceLoopLongTextEditor(value: String, onValueChange: (String) -> Unit, label: String, private: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, isError: Boolean = false, fieldTestTag: String? = null, onFocusLost: (() -> Unit)? = null, compact: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    var restoreCompactFocus by remember { mutableStateOf(false) }
    var wasFocused by remember { mutableStateOf(false) }
    val compactFocusRequester = remember { FocusRequester() }
    val editorState = rememberTextFieldState(value)
    val c = LocalServiceLoopTokens.current
    val tag = "long-text-" + label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    LaunchedEffect(value) {
        if (value != editorState.text.toString()) {
            editorState.edit {
                replace(0, length, value)
                placeCursorAtEnd()
            }
        }
    }
    LaunchedEffect(editorState) {
        snapshotFlow { editorState.text.toString() }
            .drop(1)
            .collectLatest(onValueChange)
    }
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
            state = editorState, label = { Text(label) }, lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = if (compact) 2 else 3), enabled = enabled, isError = isError,
            shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.focus, unfocusedBorderColor = c.outlineControl, cursorColor = c.action, errorBorderColor = c.errorInk, errorCursorColor = c.errorInk, disabledTextColor = c.disabledText, disabledBorderColor = c.disabledContainer, disabledLabelColor = c.disabledText),
            textStyle = ServiceLoopUiTokens.Type.body,
            // Keep the final line clear of the 48dp expand target without reserving
            // a full-width suffix on every line of ordinary text.
             contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 12.dp, bottom = 52.dp)
                .takeUnless { compact }
                ?: PaddingValues(start = 16.dp, top = 10.dp, end = 52.dp, bottom = 10.dp),
            modifier = Modifier.fillMaxWidth().focusRequester(compactFocusRequester)
                .onFocusChanged { focusState ->
                    if (wasFocused && !focusState.isFocused) onFocusLost?.invoke()
                    wasFocused = focusState.isFocused
                }
                .testTag(fieldTestTag ?: tag),
        )
        ServiceLoopIconAction(
            "Expand $label",
            { expanded = true },
            enabled = enabled,
            // Keep the 48dp target, but place the visible glyph toward its bottom-right
            // so its edge sits close to the field's inner corner.
            modifier = Modifier.align(Alignment.BottomEnd)
                .size(ServiceLoopUiTokens.Size.touchMin),
            testTag = "$tag-expand",
            contentAlignment = Alignment.BottomEnd,
            content = {
                Box(
                    Modifier.fillMaxSize().padding(end = 4.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ServiceLoopIcon(
                        ServiceLoopIcons.Expand,
                        null,
                        Modifier.size(ServiceLoopUiTokens.Size.iconSmall).testTag("$tag-expand-glyph"),
                        LocalServiceLoopTokens.current.action,
                    )
                }
            },
        )
    }
    if (private) ServiceLoopPrivateLabel("PRIVATE · Not included in the customer report")
    }
    if (expanded) {
        BackHandler { collapse() }
        Dialog(onDismissRequest = { collapse() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize(), color = LocalServiceLoopTokens.current.canvas) {
                Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                    ServiceLoopBrandStrip()
                    ServiceLoopDetailToolbar(label, { collapse() })
                    Column(Modifier.fillMaxSize().padding(ServiceLoopUiTokens.Space.lg),horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.md)) {
                         if (private) ServiceLoopPrivateLabel("PRIVATE · Not included in the customer report")
                         OutlinedTextField(state = editorState, enabled = enabled, textStyle=ServiceLoopUiTokens.Type.body, lineLimits = TextFieldLineLimits.MultiLine(), modifier = Modifier.fillMaxWidth().widthIn(max=ServiceLoopUiTokens.Size.contentMaxWidth).weight(1f).onFocusChanged { focusState ->
                             if (wasFocused && !focusState.isFocused) onFocusLost?.invoke()
                             wasFocused = focusState.isFocused
                         }.testTag("$tag-expanded"), shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field))
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
