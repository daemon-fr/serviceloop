package com.v16studio.v16service.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.v16studio.v16service.ui.designsystem.V16ServiceUiTokens

data class V16ServiceSemanticColors(
    val urgencyInk: Color,
    val urgencyTint: Color,
    val workflowInk: Color,
    val workflowTint: Color,
    val errorInk: Color,
    val errorTint: Color,
    val confirmedInk: Color,
    val confirmedTint: Color,
)

val LightCanvas = V16ServiceUiTokens.LightColors.canvas
val LightSurface = V16ServiceUiTokens.LightColors.surface
val LightSurfaceSecondary = V16ServiceUiTokens.LightColors.surfaceSubtle
val LightText = V16ServiceUiTokens.LightColors.textPrimary
val LightSupporting = V16ServiceUiTokens.LightColors.textSecondary
val LightPrimary = V16ServiceUiTokens.LightColors.action
val LightOutline = V16ServiceUiTokens.LightColors.outlineControl

val DarkCanvas = V16ServiceUiTokens.DarkColors.canvas
val DarkSurface = V16ServiceUiTokens.DarkColors.surface
val DarkSurfaceSecondary = V16ServiceUiTokens.DarkColors.surfaceSubtle
val DarkText = V16ServiceUiTokens.DarkColors.textPrimary
val DarkSupporting = V16ServiceUiTokens.DarkColors.textSecondary
val DarkPrimary = V16ServiceUiTokens.DarkColors.action
val DarkOutline = V16ServiceUiTokens.DarkColors.outlineControl

val LightSemantic = V16ServiceUiTokens.LightColors.let { V16ServiceSemanticColors(it.warningInk, it.warningContainer, it.infoInk, it.infoContainer, it.errorInk, it.errorContainer, it.successInk, it.successContainer) }
val DarkSemantic = V16ServiceUiTokens.DarkColors.let { V16ServiceSemanticColors(it.warningInk, it.warningContainer, it.infoInk, it.infoContainer, it.errorInk, it.errorContainer, it.successInk, it.successContainer) }

val LocalV16ServiceColors = staticCompositionLocalOf { LightSemantic }
