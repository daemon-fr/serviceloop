package com.v16studio.serviceloop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.designsystem.serviceLoopDarkColorScheme
import com.v16studio.serviceloop.ui.designsystem.serviceLoopLightColorScheme

@Composable
fun ServiceLoopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val tokens = if (darkTheme) ServiceLoopUiTokens.DarkColors else ServiceLoopUiTokens.LightColors
    CompositionLocalProvider(LocalServiceLoopColors provides if (darkTheme) DarkSemantic else LightSemantic, LocalServiceLoopTokens provides tokens) {
        MaterialTheme(
            colorScheme = if (darkTheme) serviceLoopDarkColorScheme() else serviceLoopLightColorScheme(),
            typography = ServiceLoopUiTokens.Typography,
            shapes = ServiceLoopUiTokens.Shapes,
            content = content,
        )
    }
}
