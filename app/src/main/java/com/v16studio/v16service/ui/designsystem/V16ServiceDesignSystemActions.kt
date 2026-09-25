package com.v16studio.v16service.ui.designsystem

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
import com.v16studio.v16service.ui.icons.V16ServiceIcon
import com.v16studio.v16service.ui.icons.V16ServiceIcons

@Composable
internal fun V16ServiceButtonContent(
    onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,primary:Boolean,
    busy:Boolean=false,interactionSource:MutableInteractionSource=remember{MutableInteractionSource()},
    trailingIcon:(@Composable (() -> Unit))?=null,
    content:@Composable RowScope.()->Unit,
) {
    val c=LocalV16ServiceTokens.current
    val pressed by interactionSource.collectIsPressedAsState()
    val container=if(primary)V16ServiceButtonContract.primaryContainer(c,enabled,pressed) else V16ServiceButtonContract.secondaryContainer(c,enabled)
    val ink=if(primary)V16ServiceButtonContract.primaryInk(c,enabled) else V16ServiceButtonContract.secondaryInk(c,enabled)
    val shape=RoundedCornerShape(V16ServiceButtonContract.radius)
    Row(
        modifier.heightIn(min=if(primary)V16ServiceButtonContract.primaryMinHeight else V16ServiceButtonContract.secondaryMinHeight)
            .v16ServiceFocusRing(V16ServiceUiTokens.Radius.field).clip(shape).background(container)
            .clickable(enabled=enabled&&!busy,role=Role.Button,interactionSource=interactionSource,indication=LocalIndication.current,onClick=onClick)
            .focusable(enabled=enabled).semantics(mergeDescendants=true) { if(!enabled||busy) disabled() }
            .padding(horizontal=V16ServiceButtonContract.horizontalPadding,vertical=V16ServiceButtonContract.verticalPadding),
        horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically,
    ) {
        if(busy) {
            CircularProgressIndicator(Modifier.size(V16ServiceUiTokens.Size.iconSmall),strokeWidth=V16ServiceUiTokens.Stroke.focus,color=ink)
            Spacer(Modifier.width(V16ServiceUiTokens.Space.sm))
        }
        CompositionLocalProvider(LocalContentColor provides ink) {
            ProvideTextStyle(V16ServiceButtonContract.textStyle) {
                if (trailingIcon == null) {
                    content()
                } else {
                    Box(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth()
                                .padding(end = V16ServiceUiTokens.Size.icon + V16ServiceUiTokens.Space.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) { content() }
                        Box(Modifier.align(Alignment.CenterEnd).padding(end = V16ServiceUiTokens.Space.xs)) { trailingIcon() }
                    }
                }
            }
        }
    }
}

@Composable fun V16ServicePrimaryButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,leadingIcon:(@Composable (() -> Unit))?=null,busy:Boolean=false,trailingIcon:(@Composable (() -> Unit))?=null)=V16ServiceButtonContent(onClick,modifier,enabled,true,busy,trailingIcon=trailingIcon){if(!busy)leadingIcon?.let{it();Spacer(Modifier.width(V16ServiceUiTokens.Space.sm))};Text(label)}
@Composable fun V16ServiceSecondaryButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,leadingIcon:(@Composable (() -> Unit))?=null,busy:Boolean=false,trailingIcon:(@Composable (() -> Unit))?=null)=V16ServiceButtonContent(onClick,modifier,enabled,false,busy,trailingIcon=trailingIcon){if(!busy)leadingIcon?.let{it();Spacer(Modifier.width(V16ServiceUiTokens.Space.sm))};Text(label)}

/** Full-width or paired control for opening related information inside V16 Service. */
@Composable
fun V16ServiceNavigationButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = V16ServiceSecondaryButton(
    label = label,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    trailingIcon = { V16ServiceIcon(V16ServiceIcons.Disclosure, null, Modifier.size(V16ServiceUiTokens.Size.icon).testTag("v16-service-disclosure-icon")) },
)

/** Full-width operational commands belong in this explicit, consistently spaced vertical group. */
@Composable fun V16ServiceActionStack(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Layout.buttonGap), content = content)

@Composable fun V16ServiceDangerTonalButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true) {
    val c=LocalV16ServiceTokens.current
    val shape=RoundedCornerShape(V16ServiceButtonContract.radius)
    Row(
        modifier.heightIn(min=V16ServiceButtonContract.secondaryMinHeight).v16ServiceFocusRing(V16ServiceButtonContract.radius).clip(shape)
            .background(if(enabled)c.errorContainer else c.disabledContainer)
            .clickable(enabled=enabled,role=Role.Button,onClick=onClick).focusable(enabled)
            .semantics(mergeDescendants=true) { if(!enabled) disabled() }
            .padding(horizontal=V16ServiceButtonContract.horizontalPadding,vertical=V16ServiceButtonContract.verticalPadding),
        horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically,
    ) { ProvideTextStyle(V16ServiceButtonContract.textStyle) { Text(label,color=if(enabled)c.errorInk else c.disabledText) } }
}

@Composable fun V16ServiceDestructiveButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,showIcon:Boolean=true) {
    val c=LocalV16ServiceTokens.current
    Button(onClick,modifier.heightIn(min=V16ServiceButtonContract.primaryMinHeight).v16ServiceFocusRing(V16ServiceButtonContract.radius),enabled,
        shape=RoundedCornerShape(V16ServiceButtonContract.radius),colors=ButtonDefaults.buttonColors(containerColor=c.destructive,contentColor=c.onDestructive,disabledContainerColor=c.disabledContainer,disabledContentColor=c.disabledText),
        contentPadding=PaddingValues(horizontal=V16ServiceButtonContract.horizontalPadding,vertical=V16ServiceButtonContract.verticalPadding)) {
        ProvideTextStyle(V16ServiceButtonContract.textStyle){
            if (showIcon) {
                V16ServiceIcon(V16ServiceIcons.Delete,null,Modifier.size(V16ServiceUiTokens.Size.icon))
                Spacer(Modifier.width(V16ServiceUiTokens.Space.sm))
            }
            Text(label)
        }
    }
}

/** Compact icon-over-label action for dense, ordered tool strips. */
@Composable
fun V16ServiceCompactIconLabelAction(
    accessibleName: String,
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = LocalV16ServiceTokens.current.action,
    contentColor: Color = LocalV16ServiceTokens.current.onAction,
    baseIcon: Int? = null,
    foregroundIcon: Int? = null,
) {
    val c = LocalV16ServiceTokens.current
    val circleColor = if (enabled) containerColor else c.disabledContainer
    val iconInk = if (enabled) contentColor else c.disabledText
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = V16ServiceUiTokens.Size.touchMin)
            .v16ServiceFocusRing(V16ServiceUiTokens.Radius.field)
            .clip(RoundedCornerShape(V16ServiceUiTokens.Radius.field))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .focusable(enabled)
            .semantics(mergeDescendants = true) {
                this.contentDescription = accessibleName
                if (!enabled) disabled()
            }
            .padding(vertical = V16ServiceUiTokens.Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.xs),
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (baseIcon != null && foregroundIcon != null) {
                V16ServiceIcon(baseIcon, null, Modifier.fillMaxSize(), circleColor)
                V16ServiceIcon(foregroundIcon, null, Modifier.size(16.dp), iconInk)
            } else {
                V16ServiceIcon(icon, null, Modifier.fillMaxSize(), iconInk)
            }
        }
        Text(
            label,
            style = V16ServiceUiTokens.Type.meta,
            color = if (enabled) c.textPrimary else c.disabledText,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** Icon-only circular action for compact grouped controls. The accessible name remains explicit. */
@Composable
fun V16ServiceIconOnlyAction(
    accessibleName: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = LocalV16ServiceTokens.current.action,
    contentColor: Color = LocalV16ServiceTokens.current.onAction,
    testTag: String? = null,
) {
    val colors = LocalV16ServiceTokens.current
    val background = if (enabled) containerColor else colors.disabledContainer
    val ink = if (enabled) contentColor else colors.disabledText
    val actionModifier = modifier
        .size(56.dp)
        .v16ServiceFocusRing(V16ServiceUiTokens.Radius.field)
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
        V16ServiceIcon(icon, null, Modifier.size(V16ServiceUiTokens.Size.icon), ink)
    }
}

@Composable fun V16ServiceTextAction(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true) {
    val c=LocalV16ServiceTokens.current
    Box(modifier.heightIn(min=V16ServiceUiTokens.Size.touchMin).v16ServiceFocusRing(V16ServiceUiTokens.Radius.field).clip(RoundedCornerShape(V16ServiceUiTokens.Radius.field)).clickable(enabled=enabled,role=Role.Button,onClick=onClick).focusable(enabled).padding(horizontal=V16ServiceUiTokens.Space.sm),contentAlignment=Alignment.Center){Text(label,style=V16ServiceUiTokens.Type.label,color=if(enabled)c.action else c.disabledText)}
}

@Composable fun V16ServiceIconAction(accessibleName:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:@Composable ()->Unit,testTag:String?=null,contentAlignment: Alignment = Alignment.Center) {
    val actionModifier = modifier.size(V16ServiceUiTokens.Size.touchMin)
        .v16ServiceFocusRing(V16ServiceUiTokens.Radius.field)
        .clip(RoundedCornerShape(V16ServiceUiTokens.Radius.field))
        .clickable(enabled=enabled,role=Role.Button,onClick=onClick)
        .focusable(enabled)
        .semantics { contentDescription = accessibleName }
        .let { if (testTag == null) it else it.testTag(testTag) }
    Box(actionModifier,contentAlignment=contentAlignment){Box(Modifier.size(V16ServiceUiTokens.Size.icon),contentAlignment=Alignment.Center){content()}}
}

/** Square field-side action using the same teal family as secondary navigation buttons. */
@Composable
fun V16ServiceFieldAction(
    accessibleName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
    testTag: String? = null,
) {
    val c = LocalV16ServiceTokens.current
    val shape = RoundedCornerShape(V16ServiceUiTokens.Radius.field)
    val container = V16ServiceButtonContract.secondaryContainer(c, enabled)
    val ink = V16ServiceButtonContract.secondaryInk(c, enabled)
    val actionModifier = modifier
        .size(V16ServiceUiTokens.Size.touchMin)
        .v16ServiceFocusRing(V16ServiceUiTokens.Radius.field)
        .clip(shape)
        .background(container)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .focusable(enabled)
        .semantics { contentDescription = accessibleName }
        .let { if (testTag == null) it else it.testTag(testTag) }
    Box(actionModifier, contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            Box(Modifier.size(V16ServiceUiTokens.Size.icon), contentAlignment = Alignment.Center) { content() }
        }
    }
}
