package com.v16studio.serviceloop

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class B036SystemBarAppearanceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun resolvedAppearanceUpdatesStatusAndNavigationBarIconContrastAtRuntime() {
        compose.setContent {
            var dark by remember { mutableStateOf(false) }
            ServiceLoopTheme(darkTheme = dark, window = compose.activity.window) {
                Button(onClick = { dark = !dark }, modifier = androidx.compose.ui.Modifier.testTag("toggle-appearance")) {
                    Text("Toggle")
                }
            }
        }

        val controller = WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
        compose.waitForIdle()
        assertTrue(controller.isAppearanceLightStatusBars)
        assertTrue(controller.isAppearanceLightNavigationBars)

        compose.onNodeWithTag("toggle-appearance").performClick()
        compose.waitForIdle()
        assertFalse(controller.isAppearanceLightStatusBars)
        assertFalse(controller.isAppearanceLightNavigationBars)

        compose.onNodeWithTag("toggle-appearance").performClick()
        compose.waitForIdle()
        assertTrue(controller.isAppearanceLightStatusBars)
        assertTrue(controller.isAppearanceLightNavigationBars)
    }
}
