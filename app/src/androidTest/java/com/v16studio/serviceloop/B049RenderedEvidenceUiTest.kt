package com.v16studio.serviceloop

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.ui.ExportCenterScreen
import com.v16studio.serviceloop.ui.DISPATCH_PREFS
import com.v16studio.serviceloop.ui.DetailScaffold
import com.v16studio.serviceloop.ui.ImageCleanupScreen
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.TeamRole
import com.v16studio.serviceloop.ui.TEAM_ROLE
import com.v16studio.serviceloop.ui.setTeamRole
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class B049RenderedEvidenceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var previousRole: String? = null
    private lateinit var evidenceDirectory: File

    @After fun restoreRole() {
        compose.activity.getSharedPreferences(DISPATCH_PREFS, 0).edit().apply {
            if (previousRole == null) remove(TEAM_ROLE) else putString(TEAM_ROLE, previousRole)
        }.commit()
    }

    @Test fun captureRootTeamOutboxExportAndDarkCleanup() {
        evidenceDirectory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "b049-rendered-${System.nanoTime()}",
        ).apply { check(exists() || mkdirs()) }
        previousRole = compose.activity.getSharedPreferences(DISPATCH_PREFS, 0).getString(TEAM_ROLE, null)
        compose.runOnUiThread {
            compose.activity.setTeamRole(TeamRole.COORDINATOR)
            val app = compose.activity.application as ServiceLoopApplication
            val viewModel = ServiceLoopViewModel(app.container.repository) {}
            compose.activity.setContent { ServiceLoopTheme(false, window = compose.activity.window) { ServiceLoopApp(viewModel) } }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("root-home").fetchSemanticsNodes().isNotEmpty() }
        capture("home-light.png")
        compose.onNodeWithTag("root-nav-work").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("root-work").fetchSemanticsNodes().isNotEmpty() }
        capture("work-light.png")
        compose.onNodeWithTag("root-nav-customers").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("root-customers").fetchSemanticsNodes().isNotEmpty() }
        capture("register-light.png")
        compose.onNodeWithTag("root-nav-home").performClick()
        compose.onNodeWithTag("home-tab-TEAM").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("home-team").fetchSemanticsNodes().isNotEmpty() }
        capture("team-light.png")
        compose.onNodeWithTag("team-dispatch").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("dispatch-outbox").fetchSemanticsNodes().isNotEmpty() }
        capture("outbox-light.png")
        compose.runOnUiThread { compose.activity.setContent { ServiceLoopTheme(false, window = compose.activity.window) { val nav = rememberNavController(); DetailScaffold("Export data", nav) { ExportCenterScreen(it) } } } }
        compose.onNodeWithTag("export-center").assertIsDisplayed()
        capture("export-center-light.png")
        compose.runOnUiThread { compose.activity.setContent { ServiceLoopTheme(true, window = compose.activity.window) { val nav = rememberNavController(); DetailScaffold("Export data", nav) { ExportCenterScreen(it) } } } }
        compose.onNodeWithTag("export-center").assertIsDisplayed()
        capture("export-center-dark.png")
        compose.runOnUiThread { compose.activity.setContent { ServiceLoopTheme(true, window = compose.activity.window) { val nav = rememberNavController(); DetailScaffold("Image cleanup", nav) { ImageCleanupScreen(it) } } } }
        compose.onNodeWithTag("image-cleanup").assertIsDisplayed()
        capture("image-cleanup-dark.png")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val dir = evidenceDirectory
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        FileOutputStream(File(dir, name)).use { check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
        android.util.Log.i("B049_RENDERED", "Rendered evidence directory: ${dir.absolutePath}")
    }
}
