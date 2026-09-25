package com.v16studio.v16service.ui.designsystem

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
 * same V16 Service geometry, colors, typography, ripple, focus, and disabled families.
 */
@Composable
fun V16ServiceButtonAdapter(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = V16ServiceButtonContent(onClick,modifier,enabled,primary=true,interactionSource=interactionSource ?: androidx.compose.runtime.remember { MutableInteractionSource() },content=content)

@Composable
fun V16ServiceOutlinedButtonAdapter(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = V16ServiceButtonContent(onClick,modifier,enabled,primary=false,interactionSource=interactionSource ?: androidx.compose.runtime.remember { MutableInteractionSource() },content=content)

@Composable
fun V16ServiceTextButtonAdapter(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = V16ServiceUiTokens.Shapes.small,
    colors: ButtonColors = ButtonDefaults.textButtonColors(
        contentColor = LocalV16ServiceTokens.current.action,
        disabledContentColor = LocalV16ServiceTokens.current.disabledText,
    ),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = V16ServiceUiTokens.Space.sm),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.TextButton(
    onClick = onClick,
    modifier = modifier.heightIn(min = V16ServiceUiTokens.Size.touchMin).v16ServiceFocusRing(V16ServiceUiTokens.Radius.field),
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
fun V16ServiceIconButtonAdapter(
    accessibleName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(
        contentColor = LocalV16ServiceTokens.current.action,
        disabledContentColor = LocalV16ServiceTokens.current.disabledText,
    ),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) = V16ServiceIconAction(accessibleName,onClick,modifier,enabled,content = content)

@Composable
fun V16ServiceTextFieldAdapter(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = V16ServiceUiTokens.Type.body,
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
    shape: Shape = V16ServiceUiTokens.Shapes.small,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = LocalV16ServiceTokens.current.surface,
        unfocusedContainerColor = LocalV16ServiceTokens.current.surface,
        disabledContainerColor = LocalV16ServiceTokens.current.disabledContainer,
        focusedTextColor = LocalV16ServiceTokens.current.textPrimary,
        unfocusedTextColor = LocalV16ServiceTokens.current.textPrimary,
        disabledTextColor = LocalV16ServiceTokens.current.disabledText,
        focusedBorderColor = LocalV16ServiceTokens.current.focus,
        unfocusedBorderColor = LocalV16ServiceTokens.current.outlineControl,
        disabledBorderColor = LocalV16ServiceTokens.current.disabledContainer,
        errorBorderColor = LocalV16ServiceTokens.current.errorInk,
        cursorColor = LocalV16ServiceTokens.current.action,
        errorCursorColor = LocalV16ServiceTokens.current.errorInk,
        focusedLabelColor = LocalV16ServiceTokens.current.textSecondary,
        unfocusedLabelColor = LocalV16ServiceTokens.current.textSecondary,
        disabledLabelColor = LocalV16ServiceTokens.current.disabledText,
        errorLabelColor = LocalV16ServiceTokens.current.errorInk,
        focusedSupportingTextColor = LocalV16ServiceTokens.current.textSecondary,
        unfocusedSupportingTextColor = LocalV16ServiceTokens.current.textSecondary,
        disabledSupportingTextColor = LocalV16ServiceTokens.current.disabledText,
        errorSupportingTextColor = LocalV16ServiceTokens.current.errorInk,
    ),
) = androidx.compose.material3.OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.padding(bottom = V16ServiceUiTokens.Space.lg).heightIn(min = V16ServiceUiTokens.Size.fieldMin),
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
fun V16ServiceCardAdapter(
    modifier: Modifier = Modifier,
    shape: Shape = V16ServiceUiTokens.Shapes.medium,
    colors: CardColors = CardDefaults.cardColors(containerColor = LocalV16ServiceTokens.current.surface),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = V16ServiceUiTokens.Elevation.rest),
    border: BorderStroke? = BorderStroke(V16ServiceUiTokens.Stroke.outline, LocalV16ServiceTokens.current.outlineDecorative),
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.material3.Card(modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = border, content = content)

@Composable
fun V16ServiceCardAdapter(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = V16ServiceUiTokens.Shapes.medium,
    colors: CardColors = CardDefaults.cardColors(containerColor = LocalV16ServiceTokens.current.surface),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = V16ServiceUiTokens.Elevation.rest),
    border: BorderStroke? = BorderStroke(V16ServiceUiTokens.Stroke.outline, LocalV16ServiceTokens.current.outlineDecorative),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.material3.Card(onClick = onClick, modifier = modifier.v16ServiceFocusRing(V16ServiceUiTokens.Radius.card), enabled = enabled, shape = shape, colors = colors, elevation = elevation, border = border, interactionSource = interactionSource, content = content)

@Composable
fun V16ServiceElevatedCardAdapter(
    modifier: Modifier = Modifier,
    shape: Shape = V16ServiceUiTokens.Shapes.medium,
    colors: CardColors = CardDefaults.cardColors(containerColor = LocalV16ServiceTokens.current.surface),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = V16ServiceUiTokens.Elevation.rest),
    content: @Composable ColumnScope.() -> Unit,
) = V16ServiceCardAdapter(modifier = modifier, shape = shape, colors = colors, elevation = elevation, content = content)

@Composable
fun V16ServiceElevatedCardAdapter(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = V16ServiceUiTokens.Shapes.medium,
    colors: CardColors = CardDefaults.cardColors(containerColor = LocalV16ServiceTokens.current.surface),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = V16ServiceUiTokens.Elevation.rest),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) = V16ServiceCardAdapter(onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors, elevation = elevation, interactionSource = interactionSource, content = content)

@Composable
fun v16ServicePagePadding(top: Dp = V16ServiceUiTokens.Space.sm, bottom: Dp = V16ServiceUiTokens.Space.major): PaddingValues = PaddingValues(
    start = v16ServicePageInset(LocalConfiguration.current.screenWidthDp.dp),
    top = top,
    end = v16ServicePageInset(LocalConfiguration.current.screenWidthDp.dp),
    bottom = bottom,
)

fun v16ServiceAdaptiveScaffoldPadding(base: PaddingValues, windowWidth: Dp, layoutDirection: LayoutDirection): PaddingValues {
    val extra = (v16ServicePageInset(windowWidth) - V16ServiceUiTokens.Layout.pageInsetCompact).coerceAtLeast(0.dp)
    return PaddingValues(
        start = base.calculateStartPadding(layoutDirection) + extra,
        top = base.calculateTopPadding(),
        end = base.calculateEndPadding(layoutDirection) + extra,
        bottom = base.calculateBottomPadding(),
    )
}
