package com.v16studio.serviceloop.ui.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalServiceLoopTokens = staticCompositionLocalOf { ServiceLoopUiTokens.LightColors }

fun serviceLoopLightColorScheme(): ColorScheme = scheme(ServiceLoopUiTokens.LightColors, false)
fun serviceLoopDarkColorScheme(): ColorScheme = scheme(ServiceLoopUiTokens.DarkColors, true)

private fun scheme(c: ServiceLoopColorRoles, dark: Boolean): ColorScheme {
    val fixedPrimary = Color(0xFFE7F3F1)
    val fixedPrimaryDim = Color(0xFFCCEBE5)
    val fixedPrimaryInk = Color(0xFF075C62)
    val fixedSecondaryVariant = Color(0xFF425D67)
    val fixedTertiary = Color(0xFFEEEAF4)
    val fixedTertiaryDim = Color(0xFFDDD4EA)
    val fixedTertiaryInk = Color(0xFF66517F)
    val fixedTertiaryVariant = Color(0xFF44305D)
    return (if (dark) darkColorScheme() else lightColorScheme()).copy(
            primary=c.action, onPrimary=c.onAction, primaryContainer=c.selection, onPrimaryContainer=c.onSelection,
            secondary=c.onSelection, onSecondary=if(dark)c.onAction else c.surface, secondaryContainer=c.selection, onSecondaryContainer=c.onSelection,
            tertiary=c.historyInk, onTertiary=if(dark)c.onAction else c.surface, tertiaryContainer=c.historyContainer, onTertiaryContainer=c.historyInk,
            background=c.canvas, onBackground=c.textPrimary, surface=c.surface, onSurface=c.textPrimary,
            surfaceVariant=c.surfaceSubtle, onSurfaceVariant=c.textSecondary, surfaceTint=c.action,
            inverseSurface=if(dark) Color(0xFFEDF2F3) else Color(0xFF223239),
            inverseOnSurface=if(dark) Color(0xFF182A30) else Color(0xFFEAF2F4),
            inversePrimary=if(dark) Color(0xFF08666B) else Color(0xFF79D4CE),
            error=c.destructive, onError=c.onDestructive, errorContainer=c.errorContainer, onErrorContainer=c.errorInk,
            outline=c.outlineControl, outlineVariant=c.outlineDecorative, scrim=c.scrimBase,
            surfaceBright=c.surfaceRaised, surfaceDim=c.canvas, surfaceContainerLowest=c.canvas, surfaceContainerLow=c.surface,
            surfaceContainer=c.surfaceSubtle, surfaceContainerHigh=c.surfaceRaised, surfaceContainerHighest=c.surfaceRaised,
            primaryFixed=fixedPrimary, primaryFixedDim=fixedPrimaryDim, onPrimaryFixed=fixedPrimaryInk, onPrimaryFixedVariant=c.textPrimary,
            secondaryFixed=fixedPrimary, secondaryFixedDim=fixedPrimaryDim, onSecondaryFixed=fixedPrimaryInk, onSecondaryFixedVariant=fixedSecondaryVariant,
            tertiaryFixed=fixedTertiary, tertiaryFixedDim=fixedTertiaryDim, onTertiaryFixed=fixedTertiaryInk, onTertiaryFixedVariant=fixedTertiaryVariant,
    )
}
