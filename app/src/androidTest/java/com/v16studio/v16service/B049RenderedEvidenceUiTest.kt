package com.v16studio.v16service

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.v16service.ui.ExportCenterScreen
import com.v16studio.v16service.ui.DISPATCH_PREFS
import com.v16studio.v16service.ui.DetailScaffold
import com.v16studio.v16service.ui.ImageCleanupScreen
import com.v16studio.v16service.ui.V16ServiceApp
import com.v16studio.v16service.ui.V16ServiceViewModel
import com.v16studio.v16service.ui.TeamRole
import com.v16studio.v16service.ui.TEAM_ROLE
import com.v16studio.v16service.ui.setTeamRole
import com.v16studio.v16service.ui.theme.V16ServiceTheme
import com.v16studio.v16service.domain.OperationalWorkItem
import com.v16studio.v16service.domain.OperationalWorkKind
import com.v16studio.v16service.domain.OperationalWorkState
import com.v16studio.v16service.ui.designsystem.OperationalWorkRow
import com.v16studio.v16service.ui.designsystem.V16ServicePrimaryButton
import com.v16studio.v16service.ui.icons.V16ServiceIcon
import com.v16studio.v16service.ui.icons.V16ServiceIcons
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import androidx.compose.ui.unit.dp
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
            val app = compose.activity.application as V16ServiceApplication
            val viewModel = V16ServiceViewModel(app.container.repository) {}
            compose.activity.setContent { V16ServiceTheme(false, window = compose.activity.window) { V16ServiceApp(viewModel) } }
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
        compose.runOnUiThread { compose.activity.setContent { V16ServiceTheme(false, window = compose.activity.window) { val nav = rememberNavController(); DetailScaffold("Export data", nav) { ExportCenterScreen(it) } } } }
        compose.onNodeWithTag("export-center").assertIsDisplayed()
        capture("export-center-light.png")
        compose.runOnUiThread { compose.activity.setContent { V16ServiceTheme(true, window = compose.activity.window) { val nav = rememberNavController(); DetailScaffold("Export data", nav) { ExportCenterScreen(it) } } } }
        compose.onNodeWithTag("export-center").assertIsDisplayed()
        capture("export-center-dark.png")
        compose.runOnUiThread { compose.activity.setContent { V16ServiceTheme(true, window = compose.activity.window) { val nav = rememberNavController(); DetailScaffold("Image cleanup", nav) { ImageCleanupScreen(it) } } } }
        compose.onNodeWithTag("image-cleanup").assertIsDisplayed()
        capture("image-cleanup-dark.png")
    }

    @Test fun captureOwnerReviewedUiDefectScreens() {
        evidenceDirectory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "b049-owner-ui-${System.nanoTime()}",
        ).apply { check(exists() || mkdirs()) }
        previousRole = compose.activity.getSharedPreferences(DISPATCH_PREFS, 0).getString(TEAM_ROLE, null)

        openApp()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("root-home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home-dashboard-list").performScrollToNode(hasTestTag("new-visit-home"))
        compose.onNodeWithTag("new-visit-home-icon", useUnmergedTree = true).assertIsDisplayed()
        capture("dashboard-new-visit.png")

        compose.onNodeWithTag("root-nav-home").performClick()
        compose.onNodeWithTag("home-tab-TEAM").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("home-team").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home-team").performScrollToNode(hasTestTag("team-dispatch"))
        capture("team-daily-work.png")
        compose.onNodeWithTag("home-team").performScrollToNode(hasTestTag("team-id-row"))
        compose.onNodeWithTag("team-id-row").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("team-local-id").fetchSemanticsNodes().isNotEmpty() }
        capture("team-id.png")

        openApp("data-transfer/export")
        compose.onNodeWithTag("export-center").assertIsDisplayed()
        capture("export-center-formats.png")
        compose.onNodeWithTag("export-center").performScrollToNode(hasTestTag("export-from-date-picker"))
        compose.onNodeWithTag("export-from-date-picker").assertIsDisplayed()
        capture("export-center-scope-dates.png")
        compose.onNodeWithTag("export-preset").performClick()
        compose.onNodeWithTag("export-preset-option-workperformed").performClick()
        compose.onNodeWithTag("export-center").performScrollToNode(hasTestTag("export-family-image_files"))
        capture("export-center-contents.png")
        compose.onNodeWithTag("export-center").performScrollToNode(hasTestTag("export-option-private"))
        capture("export-center-options.png")

        openApp("aggregate-report/new")
        compose.onNodeWithTag("aggregate-report-new").assertIsDisplayed()
        capture("generate-report-top.png")
        compose.onNodeWithTag("aggregate-report-new").performScrollToNode(hasTestTag("aggregate-from-date-picker"))
        capture("generate-report-scope-dates.png")

        openApp("dispatch/settings")
        compose.onNodeWithTag("team-role-settings").assertIsDisplayed()
        capture("team-role-settings.png")

        openApp("settings")
        compose.onNodeWithTag("settings-screen").assertIsDisplayed()
        capture("settings-app.png")
        compose.onNodeWithTag("settings-screen").performScrollToNode(hasTestTag("settings-section-data"))
        capture("settings-data.png")

        openApp("image-cleanup")
        compose.onNodeWithTag("image-cleanup").assertIsDisplayed()
        compose.onNodeWithTag("image-cleanup").performScrollToNode(hasTestTag("save-image-cleanup"))
        capture("image-cleanup-buttons.png")

        openApp("data-recovery")
        compose.onNodeWithTag("data-recovery").assertIsDisplayed()
        compose.onNodeWithTag("data-recovery").performScrollToNode(hasTestTag("backup-reminder-presets-90"))
        capture("backup-reminder-90-days.png")
        compose.onNodeWithTag("data-recovery").performScrollToNode(hasTestTag("erase-device-data"))
        capture("backup-recovery-erase.png")

        captureDashboardVisitRow()
        captureDarkOwnerReviewedScreens()
    }

    private fun captureDarkOwnerReviewedScreens() {
        openApp(dark = true)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("root-home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home-dashboard-list").performScrollToNode(hasTestTag("new-visit-home"))
        compose.onNodeWithTag("new-visit-home-icon", useUnmergedTree = true).assertIsDisplayed()
        capture("dashboard-new-visit-dark.png")

        compose.onNodeWithTag("home-tab-TEAM").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("home-team").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home-team").performScrollToNode(hasTestTag("team-dispatch"))
        capture("team-daily-work-dark.png")
        compose.onNodeWithTag("home-team").performScrollToNode(hasTestTag("team-id-row"))
        compose.onNodeWithTag("team-id-row").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("team-local-id").fetchSemanticsNodes().isNotEmpty() }
        capture("team-id-dark.png")

        openApp("data-transfer/export", dark = true)
        compose.onNodeWithTag("export-center").assertIsDisplayed()
        capture("export-center-formats-dark.png")
        compose.onNodeWithTag("export-center").performScrollToNode(hasTestTag("export-from-date-picker"))
        capture("export-center-scope-dates-dark.png")
        compose.onNodeWithTag("export-preset").performClick()
        compose.onNodeWithTag("export-preset-option-workperformed").performClick()
        compose.onNodeWithTag("export-center").performScrollToNode(hasTestTag("export-family-image_files"))
        capture("export-center-contents-dark.png")
        compose.onNodeWithTag("export-center").performScrollToNode(hasTestTag("export-option-private"))
        capture("export-center-options-dark.png")

        openApp("aggregate-report/new", dark = true)
        compose.onNodeWithTag("aggregate-report-new").assertIsDisplayed()
        compose.onNodeWithTag("aggregate-report-new").performScrollToNode(hasTestTag("aggregate-from-date-picker"))
        capture("generate-report-scope-dates-dark.png")

        openApp("dispatch/settings", dark = true)
        compose.onNodeWithTag("team-role-settings").assertIsDisplayed()
        capture("team-role-settings-dark.png")

        openApp("settings", dark = true)
        compose.onNodeWithTag("settings-screen").performScrollToNode(hasTestTag("settings-section-data"))
        capture("settings-data-dark.png")

        openApp("image-cleanup", dark = true)
        compose.onNodeWithTag("image-cleanup").performScrollToNode(hasTestTag("save-image-cleanup"))
        capture("image-cleanup-buttons-dark.png")

        openApp("data-recovery", dark = true)
        compose.onNodeWithTag("data-recovery").performScrollToNode(hasTestTag("backup-reminder-presets-90"))
        capture("backup-reminder-90-days-dark.png")
        compose.onNodeWithTag("data-recovery").performScrollToNode(hasTestTag("erase-device-data"))
        capture("backup-recovery-erase-dark.png")

        captureDashboardVisitRow(dark = true)
    }

    private fun openApp(route: String? = null, dark: Boolean = false) {
        compose.runOnUiThread {
            compose.activity.setTeamRole(TeamRole.COORDINATOR)
            val app = compose.activity.application as V16ServiceApplication
            val viewModel = V16ServiceViewModel(app.container.repository) {}
            compose.activity.setContent {
                V16ServiceTheme(dark, window = compose.activity.window) {
                    V16ServiceApp(viewModel, notificationRoute = route)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun captureDashboardVisitRow(dark: Boolean = false) {
        compose.runOnUiThread {
            compose.activity.setContent {
                V16ServiceTheme(dark, window = compose.activity.window) {
                    val nav = rememberNavController()
                    DetailScaffold("Dashboard visit card", nav) { padding ->
                        androidx.compose.foundation.layout.Column(
                            Modifier.padding(padding).padding(16.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                        ) {
                            OperationalWorkRow(
                                OperationalWorkItem(
                                    kind = OperationalWorkKind.VISIT,
                                    state = OperationalWorkState.BOOKED,
                                    recordId = "ui-visit",
                                    customerId = "ui-customer",
                                    siteId = "ui-site",
                                    equipmentId = null,
                                    displayReference = "VIS-2048",
                                    displayTitle = "Annual service",
                                    displayContext = "North site · Customer Example",
                                    dueDate = LocalDate.of(2026, 9, 25),
                                    scheduledAtEpochMillis = Instant.parse("2026-09-25T08:30:00Z").toEpochMilli(),
                                    modifiedAtEpochMillis = 1,
                                ),
                                onClick = {},
                            )
                            V16ServicePrimaryButton(
                                "New visit",
                                onClick = {},
                                modifier = Modifier.fillMaxWidth().testTag("new-visit-home"),
                                leadingIcon = { V16ServiceIcon(V16ServiceIcons.Add, null, Modifier.size(24.dp).testTag("new-visit-home-icon")) },
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("operational-work-row-ui-visit").assertIsDisplayed()
        compose.onNodeWithTag("operational-work-meta-ui-visit", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("new-visit-home-icon", useUnmergedTree = true).assertIsDisplayed()
        val meta = compose.onNodeWithTag("operational-work-meta-ui-visit", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val date = compose.onNodeWithText("Due 2026-09-25", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val booked = compose.onNodeWithText("Booked visit", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assert(date.top >= meta.top && date.bottom <= meta.bottom && booked.top >= meta.top && booked.bottom <= meta.bottom) {
            "Visit date and status should be inside their shared metadata row: meta=$meta date=$date booked=$booked"
        }
        assert(kotlin.math.abs(date.center.y - booked.center.y) < 2f) { "Visit date and status should share one row: date=$date booked=$booked" }
        capture(if (dark) "dashboard-visit-card-dark.png" else "dashboard-visit-card.png")
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
