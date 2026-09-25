package com.v16studio.v16service.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.SideEffect
import android.view.Window
import androidx.core.view.WindowCompat
import com.v16studio.v16service.ui.designsystem.LocalV16ServiceTokens
import com.v16studio.v16service.ui.designsystem.V16ServiceUiTokens
import com.v16studio.v16service.ui.designsystem.v16ServiceDarkColorScheme
import com.v16studio.v16service.ui.designsystem.v16ServiceLightColorScheme

@Composable
fun V16ServiceTheme(
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
    val tokens = if (effectiveDarkTheme) V16ServiceUiTokens.DarkColors else V16ServiceUiTokens.LightColors
    CompositionLocalProvider(LocalV16ServiceColors provides if (effectiveDarkTheme) DarkSemantic else LightSemantic, LocalV16ServiceTokens provides tokens) {
        MaterialTheme(
            colorScheme = if (effectiveDarkTheme) v16ServiceDarkColorScheme() else v16ServiceLightColorScheme(),
            typography = V16ServiceUiTokens.Typography,
            shapes = V16ServiceUiTokens.Shapes,
            content = content,
        )
    }
}
