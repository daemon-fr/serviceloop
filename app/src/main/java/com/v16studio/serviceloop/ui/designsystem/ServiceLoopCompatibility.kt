package com.v16studio.serviceloop.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * Thin migration adapters for production screens whose action content is richer than the
 * strongly typed C05/C07 helpers. They keep business call sites intact while enforcing the
 * same ServiceLoop geometry, colors, typography, ripple, focus, and disabled families.
 */
@Composable
fun ServiceLoopButtonAdapter(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ServiceLoopUiTokens.Shapes.small,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = LocalServiceLoopTokens.current.action,
        contentColor = LocalServiceLoopTokens.current.onAction,
        disabledContainerColor = LocalServiceLoopTokens.current.disabledContainer,
        disabledContentColor = LocalServiceLoopTokens.current.disabledText,
    ),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = ServiceLoopUiTokens.Space.xl, vertical = ServiceLoopUiTokens.Space.md),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = ServiceLoopButtonContent(onClick,modifier,enabled,primary=true,interactionSource=interactionSource ?: androidx.compose.runtime.remember { MutableInteractionSource() },content=content)

@Composable
fun ServiceLoopOutlinedButtonAdapter(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ServiceLoopUiTokens.Shapes.small,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(
        containerColor = LocalServiceLoopTokens.current.surface,
        contentColor = LocalServiceLoopTokens.current.action,
        disabledContainerColor = LocalServiceLoopTokens.current.disabledContainer,
        disabledContentColor = LocalServiceLoopTokens.current.disabledText,
    ),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = BorderStroke(ServiceLoopUiTokens.Stroke.outline, LocalServiceLoopTokens.current.outlineControl),
    contentPadding: PaddingValues = PaddingValues(horizontal = ServiceLoopUiTokens.Space.xl, vertical = ServiceLoopUiTokens.Space.md),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = ServiceLoopButtonContent(onClick,modifier,enabled,primary=false,interactionSource=interactionSource ?: androidx.compose.runtime.remember { MutableInteractionSource() },content=content)

@Composable
fun ServiceLoopTextButtonAdapter(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ServiceLoopUiTokens.Shapes.small,
    colors: ButtonColors = ButtonDefaults.textButtonColors(
        contentColor = LocalServiceLoopTokens.current.action,
        disabledContentColor = LocalServiceLoopTokens.current.disabledText,
    ),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = ServiceLoopUiTokens.Space.sm),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.TextButton(
    onClick = onClick,
    modifier = modifier.heightIn(min = ServiceLoopUiTokens.Size.touchMin).serviceLoopFocusRing(ServiceLoopUiTokens.Radius.field),
    enabled = enabled,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    contentPadding = contentPadding,
    interactionSource = interactionSource,
    content = content,
)

@Composable
fun ServiceLoopIconButtonAdapter(
    accessibleName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(
        contentColor = LocalServiceLoopTokens.current.action,
        disabledContentColor = LocalServiceLoopTokens.current.disabledText,
    ),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) = ServiceLoopIconAction(accessibleName,onClick,modifier,enabled,content)

@Composable
fun ServiceLoopTextFieldAdapter(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = ServiceLoopUiTokens.Type.body,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = ServiceLoopUiTokens.Shapes.small,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = LocalServiceLoopTokens.current.surface,
        unfocusedContainerColor = LocalServiceLoopTokens.current.surface,
        disabledContainerColor = LocalServiceLoopTokens.current.disabledContainer,
        focusedTextColor = LocalServiceLoopTokens.current.textPrimary,
        unfocusedTextColor = LocalServiceLoopTokens.current.textPrimary,
        disabledTextColor = LocalServiceLoopTokens.current.disabledText,
        focusedBorderColor = LocalServiceLoopTokens.current.focus,
        unfocusedBorderColor = LocalServiceLoopTokens.current.outlineControl,
        disabledBorderColor = LocalServiceLoopTokens.current.disabledContainer,
        errorBorderColor = LocalServiceLoopTokens.current.errorInk,
        cursorColor = LocalServiceLoopTokens.current.action,
        errorCursorColor = LocalServiceLoopTokens.current.errorInk,
        focusedLabelColor = LocalServiceLoopTokens.current.textSecondary,
        unfocusedLabelColor = LocalServiceLoopTokens.current.textSecondary,
        disabledLabelColor = LocalServiceLoopTokens.current.disabledText,
        errorLabelColor = LocalServiceLoopTokens.current.errorInk,
        focusedSupportingTextColor = LocalServiceLoopTokens.current.textSecondary,
        unfocusedSupportingTextColor = LocalServiceLoopTokens.current.textSecondary,
        disabledSupportingTextColor = LocalServiceLoopTokens.current.disabledText,
        errorSupportingTextColor = LocalServiceLoopTokens.current.errorInk,
    ),
) = androidx.compose.material3.OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.padding(bottom = ServiceLoopUiTokens.Space.lg).heightIn(min = ServiceLoopUiTokens.Size.fieldMin),
    enabled = enabled,
    readOnly = readOnly,
    textStyle = textStyle,
    label = label,
    placeholder = placeholder,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    prefix = prefix,
    suffix = suffix,
    supportingText = supportingText,
    isError = isError,
    visualTransformation = visualTransformation,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    singleLine = singleLine,
    maxLines = maxLines,
    minLines = minLines,
    interactionSource = interactionSource,
    shape = shape,
    colors = colors,
)

@Composable
fun ServiceLoopCardAdapter(
    modifier: Modifier = Modifier,
    shape: Shape = ServiceLoopUiTokens.Shapes.medium,
    colors: CardColors = CardDefaults.cardColors(containerColor = LocalServiceLoopTokens.current.surface),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = ServiceLoopUiTokens.Elevation.rest),
    border: BorderStroke? = BorderStroke(ServiceLoopUiTokens.Stroke.outline, LocalServiceLoopTokens.current.outlineDecorative),
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.material3.Card(modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = border, content = content)

@Composable
fun ServiceLoopCardAdapter(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ServiceLoopUiTokens.Shapes.medium,
    colors: CardColors = CardDefaults.cardColors(containerColor = LocalServiceLoopTokens.current.surface),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = ServiceLoopUiTokens.Elevation.rest),
    border: BorderStroke? = BorderStroke(ServiceLoopUiTokens.Stroke.outline, LocalServiceLoopTokens.current.outlineDecorative),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.material3.Card(onClick = onClick, modifier = modifier.serviceLoopFocusRing(ServiceLoopUiTokens.Radius.card), enabled = enabled, shape = shape, colors = colors, elevation = elevation, border = border, interactionSource = interactionSource, content = content)

@Composable
fun ServiceLoopElevatedCardAdapter(
    modifier: Modifier = Modifier,
    shape: Shape = ServiceLoopUiTokens.Shapes.medium,
    colors: CardColors = CardDefaults.cardColors(containerColor = LocalServiceLoopTokens.current.surface),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = ServiceLoopUiTokens.Elevation.rest),
    content: @Composable ColumnScope.() -> Unit,
) = ServiceLoopCardAdapter(modifier = modifier, shape = shape, colors = colors, elevation = elevation, content = content)

@Composable
fun ServiceLoopElevatedCardAdapter(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ServiceLoopUiTokens.Shapes.medium,
    colors: CardColors = CardDefaults.cardColors(containerColor = LocalServiceLoopTokens.current.surface),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = ServiceLoopUiTokens.Elevation.rest),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) = ServiceLoopCardAdapter(onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors, elevation = elevation, interactionSource = interactionSource, content = content)

@Composable
fun serviceLoopPagePadding(top: Dp = ServiceLoopUiTokens.Space.sm, bottom: Dp = ServiceLoopUiTokens.Space.major): PaddingValues = PaddingValues(
    start = serviceLoopPageInset(LocalConfiguration.current.screenWidthDp.dp),
    top = top,
    end = serviceLoopPageInset(LocalConfiguration.current.screenWidthDp.dp),
    bottom = bottom,
)

fun serviceLoopAdaptiveScaffoldPadding(base: PaddingValues, windowWidth: Dp, layoutDirection: LayoutDirection): PaddingValues {
    val extra = (serviceLoopPageInset(windowWidth) - ServiceLoopUiTokens.Layout.pageInsetCompact).coerceAtLeast(0.dp)
    return PaddingValues(
        start = base.calculateStartPadding(layoutDirection) + extra,
        top = base.calculateTopPadding(),
        end = base.calculateEndPadding(layoutDirection) + extra,
        bottom = base.calculateBottomPadding(),
    )
}
