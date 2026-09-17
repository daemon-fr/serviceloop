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
import androidx.compose.foundation.shape.CircleShape
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
internal fun ServiceLoopButtonContent(
    onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,primary:Boolean,
    busy:Boolean=false,interactionSource:MutableInteractionSource=remember{MutableInteractionSource()},
    trailingIcon:(@Composable (() -> Unit))?=null,
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
            ProvideTextStyle(ServiceLoopButtonContract.textStyle) {
                if (trailingIcon == null) {
                    content()
                } else {
                    Box(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth()
                                .padding(end = ServiceLoopUiTokens.Size.icon + ServiceLoopUiTokens.Space.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) { content() }
                        Box(Modifier.align(Alignment.CenterEnd).padding(end = ServiceLoopUiTokens.Space.xs)) { trailingIcon() }
                    }
                }
            }
        }
    }
}

@Composable fun ServiceLoopPrimaryButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,leadingIcon:(@Composable (() -> Unit))?=null,busy:Boolean=false,trailingIcon:(@Composable (() -> Unit))?=null)=ServiceLoopButtonContent(onClick,modifier,enabled,true,busy,trailingIcon=trailingIcon){if(!busy)leadingIcon?.let{it();Spacer(Modifier.width(ServiceLoopUiTokens.Space.sm))};Text(label)}
@Composable fun ServiceLoopSecondaryButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,leadingIcon:(@Composable (() -> Unit))?=null,busy:Boolean=false,trailingIcon:(@Composable (() -> Unit))?=null)=ServiceLoopButtonContent(onClick,modifier,enabled,false,busy,trailingIcon=trailingIcon){if(!busy)leadingIcon?.let{it();Spacer(Modifier.width(ServiceLoopUiTokens.Space.sm))};Text(label)}

/** Full-width or paired control for opening related information inside ServiceLoop. */
@Composable
fun ServiceLoopNavigationButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = ServiceLoopSecondaryButton(
    label = label,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    trailingIcon = { ServiceLoopIcon(ServiceLoopIcons.Disclosure, null, Modifier.size(ServiceLoopUiTokens.Size.icon).testTag("service-loop-disclosure-icon")) },
)

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

@Composable fun ServiceLoopDestructiveButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,showIcon:Boolean=true) {
    val c=LocalServiceLoopTokens.current
    Button(onClick,modifier.heightIn(min=ServiceLoopButtonContract.primaryMinHeight).serviceLoopFocusRing(ServiceLoopButtonContract.radius),enabled,
        shape=RoundedCornerShape(ServiceLoopButtonContract.radius),colors=ButtonDefaults.buttonColors(containerColor=c.destructive,contentColor=c.onDestructive,disabledContainerColor=c.disabledContainer,disabledContentColor=c.disabledText),
        contentPadding=PaddingValues(horizontal=ServiceLoopButtonContract.horizontalPadding,vertical=ServiceLoopButtonContract.verticalPadding)) {
        ProvideTextStyle(ServiceLoopButtonContract.textStyle){
            if (showIcon) {
                ServiceLoopIcon(ServiceLoopIcons.Delete,null,Modifier.size(ServiceLoopUiTokens.Size.icon))
                Spacer(Modifier.width(ServiceLoopUiTokens.Space.sm))
            }
            Text(label)
        }
    }
}

/** Compact icon-over-label action for dense, ordered tool strips. */
@Composable
fun ServiceLoopCompactIconLabelAction(
    accessibleName: String,
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = LocalServiceLoopTokens.current.action,
    contentColor: Color = LocalServiceLoopTokens.current.onAction,
    baseIcon: Int? = null,
    foregroundIcon: Int? = null,
) {
    val c = LocalServiceLoopTokens.current
    val circleColor = if (enabled) containerColor else c.disabledContainer
    val iconInk = if (enabled) contentColor else c.disabledText
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ServiceLoopUiTokens.Size.touchMin)
            .serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field)
            .clip(RoundedCornerShape(ServiceLoopUiTokens.Radius.field))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .focusable(enabled)
            .semantics(mergeDescendants = true) {
                this.contentDescription = accessibleName
                if (!enabled) disabled()
            }
            .padding(vertical = ServiceLoopUiTokens.Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs),
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (baseIcon != null && foregroundIcon != null) {
                ServiceLoopIcon(baseIcon, null, Modifier.fillMaxSize(), circleColor)
                ServiceLoopIcon(foregroundIcon, null, Modifier.size(16.dp), iconInk)
            } else {
                ServiceLoopIcon(icon, null, Modifier.fillMaxSize(), iconInk)
            }
        }
        Text(
            label,
            style = ServiceLoopUiTokens.Type.meta,
            color = if (enabled) c.textPrimary else c.disabledText,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** Icon-only circular action for compact grouped controls. The accessible name remains explicit. */
@Composable
fun ServiceLoopIconOnlyAction(
    accessibleName: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = LocalServiceLoopTokens.current.action,
    contentColor: Color = LocalServiceLoopTokens.current.onAction,
    testTag: String? = null,
) {
    val colors = LocalServiceLoopTokens.current
    val background = if (enabled) containerColor else colors.disabledContainer
    val ink = if (enabled) contentColor else colors.disabledText
    val actionModifier = modifier
        .size(56.dp)
        .serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field)
        .clip(CircleShape)
        .background(background)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .focusable(enabled)
        .semantics(mergeDescendants = true) {
            this.contentDescription = accessibleName
            if (!enabled) disabled()
        }
        .let { if (testTag == null) it else it.testTag(testTag) }
    Box(actionModifier, contentAlignment = Alignment.Center) {
        ServiceLoopIcon(icon, null, Modifier.size(ServiceLoopUiTokens.Size.icon), ink)
    }
}

@Composable fun ServiceLoopTextAction(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true) {
    val c=LocalServiceLoopTokens.current
    Box(modifier.heightIn(min=ServiceLoopUiTokens.Size.touchMin).serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field).clip(RoundedCornerShape(ServiceLoopUiTokens.Radius.field)).clickable(enabled=enabled,role=Role.Button,onClick=onClick).focusable(enabled).padding(horizontal=ServiceLoopUiTokens.Space.sm),contentAlignment=Alignment.Center){Text(label,style=ServiceLoopUiTokens.Type.label,color=if(enabled)c.action else c.disabledText)}
}

@Composable fun ServiceLoopIconAction(accessibleName:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:@Composable ()->Unit,testTag:String?=null,contentAlignment: Alignment = Alignment.Center) {
    val actionModifier = modifier.size(ServiceLoopUiTokens.Size.touchMin)
        .serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field)
        .clip(RoundedCornerShape(ServiceLoopUiTokens.Radius.field))
        .clickable(enabled=enabled,role=Role.Button,onClick=onClick)
        .focusable(enabled)
        .semantics { contentDescription = accessibleName }
        .let { if (testTag == null) it else it.testTag(testTag) }
    Box(actionModifier,contentAlignment=contentAlignment){Box(Modifier.size(ServiceLoopUiTokens.Size.icon),contentAlignment=Alignment.Center){content()}}
}
