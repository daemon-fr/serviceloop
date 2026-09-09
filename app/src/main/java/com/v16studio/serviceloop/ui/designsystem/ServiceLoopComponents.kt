package com.v16studio.serviceloop.ui.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ServiceLoopSectionHeading(title: String, trailing: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}

@Composable
fun ServiceLoopStatusBadge(code: String, modifier: Modifier = Modifier) {
    val style = serviceLoopStateStyle(code, LocalServiceLoopTokens.current)
    Text(
        style.label,
        color = style.ink,
        style = MaterialTheme.typography.labelMedium,
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
    Column(
        modifier.fillMaxWidth().background(pair.first, RoundedCornerShape(ServiceLoopUiTokens.Radius.field)).padding(ServiceLoopUiTokens.Space.lg),
        verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm),
    ) {
        Text(title, color = pair.second, style = MaterialTheme.typography.titleMedium)
        body?.let { Text(it, color = pair.second, style = MaterialTheme.typography.bodyLarge) }
        action?.invoke()
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
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
    ) { Column(Modifier.padding(ServiceLoopUiTokens.Layout.cardPadding), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm), content = content) }
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
        if (enabled) Chevron(Modifier.size(24.dp), c.icon)
    }
}

@Composable
private fun Chevron(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        drawLine(color, Offset(size.width * .38f, size.height * .22f), Offset(size.width * .65f, size.height * .5f), stroke)
        drawLine(color, Offset(size.width * .65f, size.height * .5f), Offset(size.width * .38f, size.height * .78f), stroke)
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
            cursorColor = c.action,
            errorBorderColor = c.errorInk,
            errorCursorColor = c.errorInk,
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = ServiceLoopUiTokens.Size.fieldMin),
    )
}

@Composable
fun ServiceLoopResponsivePair(first: @Composable () -> Unit, second: @Composable () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stack = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.3f || (maxWidth - ServiceLoopUiTokens.Layout.pairGap) / 2 < 148.dp
        if (stack) Column(verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Layout.pairGap)) { first(); second() }
        else Row(horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Layout.pairGap)) { Box(Modifier.weight(1f)) { first() }; Box(Modifier.weight(1f)) { second() } }
    }
}

@Composable
fun ServiceLoopLongTextEditor(value: String, onValueChange: (String) -> Unit, label: String, private: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, isError: Boolean = false, fieldTestTag: String? = null) {
    var expanded by remember { mutableStateOf(false) }
    val c = LocalServiceLoopTokens.current
    val tag = "long-text-" + label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value, onValueChange, label = { Text(label) }, minLines = 3, maxLines = 5, enabled = enabled, isError = isError,
            shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.focus, cursorColor = c.action, errorBorderColor = c.errorInk, errorCursorColor = c.errorInk),
            modifier = Modifier.fillMaxWidth().testTag(fieldTestTag ?: tag),
        )
        IconButton(
            onClick = { expanded = true }, enabled = enabled,
            modifier = Modifier.align(Alignment.BottomEnd).size(ServiceLoopUiTokens.Size.touchMin)
                .semantics { role = Role.Button; contentDescription = "Expand text editor" }.testTag("$tag-expand"),
        ) { ExpandGlyph(Modifier.size(24.dp), LocalServiceLoopTokens.current.action) }
    }
    if (private) Text("PRIVATE · Not included in the customer report", style = MaterialTheme.typography.bodySmall, color = LocalServiceLoopTokens.current.textSecondary)
    if (expanded) {
        BackHandler { expanded = false }
        Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize(), color = LocalServiceLoopTokens.current.canvas) {
                Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(ServiceLoopUiTokens.Space.lg), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.md)) {
                    Text(label, style = MaterialTheme.typography.headlineSmall)
                    if (private) Text("PRIVATE · Not included in the customer report", color = LocalServiceLoopTokens.current.textSecondary)
                    OutlinedTextField(value, onValueChange, modifier = Modifier.fillMaxWidth().weight(1f).testTag("$tag-expanded"), shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field))
                    Button({ expanded = false }, Modifier.fillMaxWidth().heightIn(min = ServiceLoopUiTokens.Size.buttonPrimaryMin)) { Text("Done") }
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
            val gap = 2.dp.toPx()
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
private fun ExpandGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val s = 2.dp.toPx(); val i = 3.dp.toPx(); val a = 7.dp.toPx()
        drawLine(color, Offset(i, a+i), Offset(i, i), s); drawLine(color, Offset(i, i), Offset(a+i, i), s)
        drawLine(color, Offset(size.width-i, size.height-a-i), Offset(size.width-i, size.height-i), s)
        drawLine(color, Offset(size.width-a-i, size.height-i), Offset(size.width-i, size.height-i), s)
    }
}

@Composable
fun ServiceLoopPinnedBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalServiceLoopTokens.current
    Surface(modifier.fillMaxWidth(), color = c.surface, tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Column(
            Modifier.fillMaxWidth().border(ServiceLoopUiTokens.Stroke.divider, c.outlineDecorative).navigationBarsPadding().padding(ServiceLoopUiTokens.Layout.barPadding),
            verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.md), content = content,
        )
    }
}
