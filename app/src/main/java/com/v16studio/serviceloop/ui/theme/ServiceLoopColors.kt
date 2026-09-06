package com.v16studio.serviceloop.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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

val LightCanvas = Color(0xFFF5F7FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceSecondary = Color(0xFFEEF2F5)
val LightText = Color(0xFF162328)
val LightSupporting = Color(0xFF47565D)
val LightPrimary = Color(0xFF075E67)
val LightOutline = Color(0xFF687B84)

val DarkCanvas = Color(0xFF11181C)
val DarkSurface = Color(0xFF172126)
val DarkSurfaceSecondary = Color(0xFF253238)
val DarkText = Color(0xFFEFF5F7)
val DarkSupporting = Color(0xFFBAC9CF)
val DarkPrimary = Color(0xFF80D4DD)
val DarkOutline = Color(0xFF8AA2AD)

val LightSemantic = ServiceLoopSemanticColors(Color(0xFF714600), Color(0xFFFFF0CD), Color(0xFF164D8C), Color(0xFFE5EFFB), Color(0xFFA32628), Color(0xFFFCE9E9), Color(0xFF225D3C), Color(0xFFE3F2E8))
val DarkSemantic = ServiceLoopSemanticColors(Color(0xFFFFD58A), Color(0xFF45320C), Color(0xFFA9CEFF), Color(0xFF163553), Color(0xFFFFB4AB), Color(0xFF512B2C), Color(0xFFA6DFB8), Color(0xFF203D2B))

val LocalServiceLoopColors = staticCompositionLocalOf { LightSemantic }
