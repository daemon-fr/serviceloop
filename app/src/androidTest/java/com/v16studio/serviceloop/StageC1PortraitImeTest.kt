package com.v16studio.serviceloop

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StageC1PortraitImeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun portraitImeKeepsLowerServiceEditorAndPrimaryActionReachable() {
        val app = compose.activity.application as ServiceLoopApplication
        val viewModel = ServiceLoopViewModel(
            app.container.repository,
            app.container.reportService,
            app.container.restrictedRecoveryState,
            app.container.businessDateSignal,
            app.container.reminderCoordinator,
            app.container.calendarCoordinator,
        ) { app.container.startup.await() }
        compose.activity.setContent {
            ServiceLoopTheme(darkTheme = false) {
                ServiceLoopApp(viewModel, "inspection/${FixtureIds.OWNER_REVIEW_WORK}")
            }
        }

        compose.waitUntil(10_000) { compose.onAllNodesWithTag("service-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("long-text-public-work-performed"))
        compose.onNodeWithTag("long-text-public-work-performed").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { isImeVisible() }
        assertTrue("Portrait IME should be visible after focusing the lower Service editor", isImeVisible())

        compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("review-visit"))
        compose.onNodeWithTag("review-visit").assertIsDisplayed()
        captureImeEvidence()
    }

    private fun isImeVisible(): Boolean =
        Build.VERSION.SDK_INT < 30 || compose.activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true

    private fun captureImeEvidence() {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir
            ?: InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        FileOutputStream(File(dir, "stage-c1-service-portrait-ime-320-fs2.png")).use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
