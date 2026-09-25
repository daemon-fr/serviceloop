package com.v16studio.v16service

import android.app.Instrumentation
import android.content.pm.ActivityInfo
import android.provider.Settings
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortraitOnlyRuntimeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun autoRotateAndDeviceRotationKeepV16ServicePortrait() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val originalAutoRotate = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1)
        val originalUserRotation = Settings.System.getInt(resolver, Settings.System.USER_ROTATION, 0)
        try {
            shell(instrumentation, "settings put system accelerometer_rotation 1")
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, compose.activity.requestedOrientation)
            assertPortraitWindow()

            shell(instrumentation, "settings put system user_rotation 1")
            Thread.sleep(500)
            assertPortraitWindow()
        } finally {
            shell(instrumentation, "settings put system accelerometer_rotation $originalAutoRotate")
            shell(instrumentation, "settings put system user_rotation $originalUserRotation")
        }
    }

    private fun shell(instrumentation: Instrumentation, command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).close()
    }

    private fun assertPortraitWindow() {
        val bounds = compose.activity.windowManager.currentWindowMetrics.bounds
        assertTrue("V16 Service must remain portrait: ${bounds.width()}x${bounds.height()}", bounds.height() >= bounds.width())
    }
}
