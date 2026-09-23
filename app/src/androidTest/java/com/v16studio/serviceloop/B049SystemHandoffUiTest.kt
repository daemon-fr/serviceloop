package com.v16studio.serviceloop

import android.accessibilityservice.AccessibilityService
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.ui.DISPATCH_PREFS
import com.v16studio.serviceloop.ui.TEAM_ROLE
import com.v16studio.serviceloop.ui.TeamRole
import com.v16studio.serviceloop.ui.setTeamRole
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class B049SystemHandoffUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val prefs get() = compose.activity.getSharedPreferences(DISPATCH_PREFS, 0)
    private var previousRole: String? = null

    @Before fun memberRole() {
        previousRole = prefs.getString(TEAM_ROLE, null)
        compose.runOnUiThread { compose.activity.setTeamRole(TeamRole.SUBCONTRACTOR) }
    }

    @After fun restoreRole() {
        prefs.edit().apply { if (previousRole == null) remove(TEAM_ROLE) else putString(TEAM_ROLE, previousRole) }.commit()
    }

    @Test fun directImportOpensAndroidPickerOnceAndCancelReturnsToReview() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.onNodeWithTag("home-tab-TEAM").performClick()
        compose.onNodeWithTag("team-import-work").performClick()
        compose.waitUntil(10_000) { automation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui", ignoreCase = true) == true }
        assertTrue(automation.rootInActiveWindow.packageName.toString().contains("documentsui", ignoreCase = true))
        automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("dispatch-import").fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(1_500)
        compose.onNodeWithTag("dispatch-import").assertIsDisplayed()
        assertTrue(automation.rootInActiveWindow.packageName.toString().contains("serviceloop", ignoreCase = true))
    }
}
