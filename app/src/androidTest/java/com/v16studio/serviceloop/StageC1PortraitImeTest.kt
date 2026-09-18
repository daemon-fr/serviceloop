package com.v16studio.serviceloop

import android.os.Build
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import org.junit.After
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
    }

    @After
    fun hideImeBeforeActivityTeardown() {
        compose.runOnUiThread {
            compose.activity.currentFocus?.clearFocus()
            compose.activity.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.waitForIdle()
    }

    private fun isImeVisible(): Boolean =
        Build.VERSION.SDK_INT < 30 || compose.activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true

}
