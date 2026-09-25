package com.v16studio.v16service

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.v16service.calendar.VisitCalendarState
import com.v16studio.v16service.domain.VisitDetail
import com.v16studio.v16service.ui.DispatchSettings
import com.v16studio.v16service.ui.DetailScaffold
import com.v16studio.v16service.ui.LocalTeamRole
import com.v16studio.v16service.ui.LocalWorkspaceCapabilities
import com.v16studio.v16service.ui.V16ServiceApp
import com.v16studio.v16service.ui.V16ServiceViewModel
import com.v16studio.v16service.ui.TeamRole
import com.v16studio.v16service.ui.UiState
import com.v16studio.v16service.ui.VisitDetailScreen
import com.v16studio.v16service.ui.setTeamRole
import com.v16studio.v16service.ui.theme.V16ServiceTheme
import com.v16studio.v16service.ui.workspaceCapabilities
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Rule
import org.junit.Test

class B043RenderedSmokeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @After fun restoreRole() = compose.runOnUiThread { compose.activity.setTeamRole(TeamRole.SOLO) }

    @Test fun captureBoundedB043DemoSurfaces() {
        renderRoleSettings(dark = false, suffix = "light")
        renderRoleSettings(dark = true, suffix = "dark")
        renderHome(TeamRole.EMPLOYEE, "employee-home")
        renderHome(TeamRole.TEAM_LEADER, "team-leader-home")
        renderHome(TeamRole.COORDINATOR, "coordinator-home")
        renderVisit()
    }

    private fun renderRoleSettings(dark: Boolean, suffix: String) {
        compose.runOnUiThread {
            compose.activity.setContent {
                V16ServiceTheme(dark, window = compose.activity.window) {
                    val nav = rememberNavController()
                    DetailScaffold("Team role settings", nav) { DispatchSettings(it, nav) }
                }
            }
        }
        compose.onNodeWithTag("team-role-settings").performScrollToNode(hasTestTag("team-role-SOLO"))
        capture("role-$suffix-top.png")
        compose.onNodeWithTag("team-role-settings").performScrollToNode(hasTestTag("team-role-COORDINATOR"))
        capture("role-$suffix-bottom.png")
    }

    private fun renderHome(role: TeamRole, name: String) {
        compose.runOnUiThread {
            compose.activity.setTeamRole(role)
            val application = compose.activity.application as V16ServiceApplication
            val viewModel = V16ServiceViewModel(application.container.repository) {}
            compose.activity.setContent { V16ServiceTheme(false, window = compose.activity.window) { V16ServiceApp(viewModel) } }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("root-home").fetchSemanticsNodes().isNotEmpty() }
        capture("$name.png")
    }

    private fun renderVisit() {
        compose.runOnUiThread {
            val role = TeamRole.TEAM_LEADER
            val application = compose.activity.application as V16ServiceApplication
            val viewModel = V16ServiceViewModel(application.container.repository) {}
            val detail = VisitDetail("visit-render", "V-043", "BOOKED", "customer", "North Plant", "site", "Boiler room", "17 Snapshot Road, Bucharest", "2026-09-22", null, null, emptyList(), null)
            compose.activity.setContent {
                V16ServiceTheme(false, window = compose.activity.window) {
                    CompositionLocalProvider(LocalTeamRole provides role, LocalWorkspaceCapabilities provides role.workspaceCapabilities) {
                        val nav = rememberNavController()
                        DetailScaffold("Visit", nav) { padding ->
                            VisitDetailScreen(detail, padding, UiState(loading = false, visitCalendarState = VisitCalendarState("Calendar integration is off")), viewModel, nav)
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("visit-maps-link").fetchSemanticsNode()
        capture("visit-maps.png")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "b043")
        check(directory.exists() || directory.mkdirs())
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        FileOutputStream(File(directory, name)).use { check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
    }
}
