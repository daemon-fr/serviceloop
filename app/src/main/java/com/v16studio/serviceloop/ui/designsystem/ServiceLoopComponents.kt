package com.v16studio.serviceloop.ui.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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
private fun ServiceLoopButton(
    label:String,onClick:()->Unit,modifier:Modifier,enabled:Boolean,primary:Boolean,
    leadingIcon:(@Composable (() -> Unit))?,busy:Boolean,
) {
    val c=LocalServiceLoopTokens.current
    val interaction=remember{MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    val container=if(primary)ServiceLoopButtonContract.primaryContainer(c,enabled,pressed) else ServiceLoopButtonContract.secondaryContainer(c,enabled)
    val ink=if(primary)ServiceLoopButtonContract.primaryInk(c,enabled) else ServiceLoopButtonContract.secondaryInk(c,enabled)
    val shape=RoundedCornerShape(ServiceLoopButtonContract.radius)
    Row(
        modifier.heightIn(min=if(primary)ServiceLoopButtonContract.primaryMinHeight else ServiceLoopButtonContract.secondaryMinHeight)
            .serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field).clip(shape).background(container)
            .then(if(primary)Modifier else Modifier.border(ServiceLoopUiTokens.Stroke.outline,c.outlineControl,shape))
            .clickable(enabled=enabled&&!busy,role=Role.Button,interactionSource=interaction,indication=LocalIndication.current,onClick=onClick)
            .focusable(enabled=enabled).semantics { if(!enabled||busy) disabled() }
            .padding(horizontal=ServiceLoopButtonContract.horizontalPadding,vertical=ServiceLoopButtonContract.verticalPadding),
        horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically,
    ) {
        if(busy) {
            CircularProgressIndicator(Modifier.size(ServiceLoopUiTokens.Size.iconSmall),strokeWidth=ServiceLoopUiTokens.Stroke.focus,color=ink)
            Spacer(Modifier.width(ServiceLoopUiTokens.Space.sm))
        } else leadingIcon?.let { it(); Spacer(Modifier.width(ServiceLoopUiTokens.Space.sm)) }
        Text(label,color=ink,style=ServiceLoopButtonContract.textStyle)
    }
}

@Composable fun ServiceLoopPrimaryButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,leadingIcon:(@Composable (() -> Unit))?=null,busy:Boolean=false)=ServiceLoopButton(label,onClick,modifier,enabled,true,leadingIcon,busy)
@Composable fun ServiceLoopSecondaryButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,leadingIcon:(@Composable (() -> Unit))?=null,busy:Boolean=false)=ServiceLoopButton(label,onClick,modifier,enabled,false,leadingIcon,busy)

@Composable fun ServiceLoopTextAction(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true) {
    val c=LocalServiceLoopTokens.current
    Box(modifier.heightIn(min=ServiceLoopUiTokens.Size.touchMin).serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field).clip(RoundedCornerShape(ServiceLoopUiTokens.Radius.field)).clickable(enabled=enabled,role=Role.Button,onClick=onClick).focusable(enabled).padding(horizontal=ServiceLoopUiTokens.Space.sm),contentAlignment=Alignment.Center){Text(label,style=ServiceLoopUiTokens.Type.label,color=if(enabled)c.action else c.disabledText)}
}

@Composable fun ServiceLoopIconAction(accessibleName:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:@Composable ()->Unit) {
    Box(modifier.size(ServiceLoopUiTokens.Size.touchMin).serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field).clip(RoundedCornerShape(ServiceLoopUiTokens.Radius.field)).clickable(enabled=enabled,role=Role.Button,onClick=onClick).focusable(enabled).semantics{contentDescription=accessibleName},contentAlignment=Alignment.Center){content()}
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
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            editorValue, updateEditor, label = { Text(label) }, minLines = 3, enabled = enabled, isError = isError,
            shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.focus, unfocusedBorderColor = c.outlineControl, cursorColor = c.action, errorBorderColor = c.errorInk, errorCursorColor = c.errorInk, disabledTextColor = c.disabledText, disabledBorderColor = c.disabledContainer, disabledLabelColor = c.disabledText),
            textStyle = ServiceLoopUiTokens.Type.body,
            trailingIcon={ServiceLoopIconAction("Expand $label",{expanded=true},enabled=enabled,modifier=Modifier.testTag("$tag-expand")){ServiceLoopIcon(ServiceLoopIcons.Expand,null,Modifier.size(ServiceLoopUiTokens.Size.icon),LocalServiceLoopTokens.current.action)}},
            modifier = Modifier.fillMaxWidth().focusRequester(compactFocusRequester).testTag(fieldTestTag ?: tag),
        )
    }
    if (private) Text("PRIVATE · Not included in the customer report", style = MaterialTheme.typography.bodySmall, color = LocalServiceLoopTokens.current.textSecondary)
    if (expanded) {
        BackHandler { collapse() }
        Dialog(onDismissRequest = { collapse() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize(), color = LocalServiceLoopTokens.current.canvas) {
                Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(ServiceLoopUiTokens.Space.lg),horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.md)) {
                    Text(label, style = MaterialTheme.typography.headlineSmall)
                    if (private) Text("PRIVATE · Not included in the customer report", color = LocalServiceLoopTokens.current.textSecondary)
                    OutlinedTextField(editorValue, updateEditor,textStyle=ServiceLoopUiTokens.Type.body, modifier = Modifier.fillMaxWidth().widthIn(max=ServiceLoopUiTokens.Size.contentMaxWidth).weight(1f).testTag("$tag-expanded"), shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field))
                    ServiceLoopPrimaryButton("Done",{collapse()},Modifier.fillMaxWidth().widthIn(max=ServiceLoopUiTokens.Size.contentMaxWidth))
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
