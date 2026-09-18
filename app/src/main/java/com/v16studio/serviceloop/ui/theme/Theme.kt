package com.v16studio.serviceloop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.SideEffect
import android.view.Window
import androidx.core.view.WindowCompat
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.designsystem.serviceLoopDarkColorScheme
import com.v16studio.serviceloop.ui.designsystem.serviceLoopLightColorScheme

@Composable
fun ServiceLoopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appearancePreferences: AppearancePreferences? = null,
    window: Window? = null,
    content: @Composable () -> Unit
) {
    val selectedMode = appearancePreferences?.mode?.collectAsState()?.value
    val effectiveDarkTheme = when (selectedMode) {
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
        AppearanceMode.SYSTEM, null -> darkTheme
    }
    SideEffect {
        window?.let { target ->
            val controller = WindowCompat.getInsetsController(target, target.decorView)
            controller.isAppearanceLightStatusBars = !effectiveDarkTheme
            controller.isAppearanceLightNavigationBars = !effectiveDarkTheme
        }
    }
    val tokens = if (effectiveDarkTheme) ServiceLoopUiTokens.DarkColors else ServiceLoopUiTokens.LightColors
    CompositionLocalProvider(LocalServiceLoopColors provides if (effectiveDarkTheme) DarkSemantic else LightSemantic, LocalServiceLoopTokens provides tokens) {
        MaterialTheme(
            colorScheme = if (effectiveDarkTheme) serviceLoopDarkColorScheme() else serviceLoopLightColorScheme(),
            typography = ServiceLoopUiTokens.Typography,
            shapes = ServiceLoopUiTokens.Shapes,
            content = content,
        )
    }
}
