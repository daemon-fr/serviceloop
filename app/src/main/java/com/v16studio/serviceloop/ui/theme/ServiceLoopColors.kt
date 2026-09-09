package com.v16studio.serviceloop.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens

data class ServiceLoopSemanticColors(
    val urgencyInk: Color,
    val urgencyTint: Color,
    val workflowInk: Color,
    val workflowTint: Color,
    val errorInk: Color,
    val errorTint: Color,
    val confirmedInk: Color,
    val confirmedTint: Color,
)

val LightCanvas = ServiceLoopUiTokens.LightColors.canvas
val LightSurface = ServiceLoopUiTokens.LightColors.surface
val LightSurfaceSecondary = ServiceLoopUiTokens.LightColors.surfaceSubtle
val LightText = ServiceLoopUiTokens.LightColors.textPrimary
val LightSupporting = ServiceLoopUiTokens.LightColors.textSecondary
val LightPrimary = ServiceLoopUiTokens.LightColors.action
val LightOutline = ServiceLoopUiTokens.LightColors.outlineControl

val DarkCanvas = ServiceLoopUiTokens.DarkColors.canvas
val DarkSurface = ServiceLoopUiTokens.DarkColors.surface
val DarkSurfaceSecondary = ServiceLoopUiTokens.DarkColors.surfaceSubtle
val DarkText = ServiceLoopUiTokens.DarkColors.textPrimary
val DarkSupporting = ServiceLoopUiTokens.DarkColors.textSecondary
val DarkPrimary = ServiceLoopUiTokens.DarkColors.action
val DarkOutline = ServiceLoopUiTokens.DarkColors.outlineControl

val LightSemantic = ServiceLoopUiTokens.LightColors.let { ServiceLoopSemanticColors(it.warningInk, it.warningContainer, it.infoInk, it.infoContainer, it.errorInk, it.errorContainer, it.successInk, it.successContainer) }
val DarkSemantic = ServiceLoopUiTokens.DarkColors.let { ServiceLoopSemanticColors(it.warningInk, it.warningContainer, it.infoInk, it.infoContainer, it.errorInk, it.errorContainer, it.successInk, it.successContainer) }

val LocalServiceLoopColors = staticCompositionLocalOf { LightSemantic }
