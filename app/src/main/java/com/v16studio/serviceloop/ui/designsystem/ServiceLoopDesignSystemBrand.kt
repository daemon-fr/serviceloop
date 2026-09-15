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
private fun BrandLine(color: Color, modifier: Modifier) = Canvas(modifier) {
    drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
}

@Composable
fun ServiceLoopBrandStrip(modifier: Modifier = Modifier) {
    val c = LocalServiceLoopTokens.current
    val brandNeutral = serviceLoopBrandNeutral(c)
    Row(
        modifier.fillMaxWidth().heightIn(min = ServiceLoopUiTokens.Size.brandMin)
            .background(c.brandBand).padding(horizontal = ServiceLoopUiTokens.Space.md, vertical = ServiceLoopUiTokens.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandLine(brandNeutral, Modifier.weight(1f).height(22.dp).testTag("brand-left-line"))
        Spacer(Modifier.width(ServiceLoopUiTokens.Space.md))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color=brandNeutral)) { append("Service") }
                withStyle(SpanStyle(color=c.action)) { append("Loop") }
            },
            modifier=Modifier.testTag("serviceloop-wordmark"), fontSize=18.sp, lineHeight=22.sp,
            fontWeight=FontWeight.Bold, letterSpacing=(-.2).sp, maxLines=1, softWrap=false,
        )
        Spacer(Modifier.width(ServiceLoopUiTokens.Space.md))
        BrandLine(c.action, Modifier.weight(1f).height(22.dp).testTag("brand-right-line"))
    }
}

/** A compact, purpose-built weekday toggle. It intentionally has no chip-style padding. */
@Composable
fun ServiceLoopDayToggle(
    label: String,
    selected: Boolean,
    onSelectedChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalServiceLoopTokens.current
    val shape = RoundedCornerShape(ServiceLoopUiTokens.Radius.field)
    Box(
        modifier = modifier
            .defaultMinSize(ServiceLoopUiTokens.Size.touchMin, ServiceLoopUiTokens.Size.touchMin)
            .clip(shape)
            .background(if (selected) colors.selection else colors.surface)
            .border(
                BorderStroke(ServiceLoopUiTokens.Stroke.outline, if (selected) colors.selectionOutline else colors.outlineControl),
                shape,
            )
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onSelectedChange() })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = ServiceLoopUiTokens.Type.label, color = if (selected) colors.action else colors.textPrimary, textAlign = TextAlign.Center, softWrap = false)
    }
}
