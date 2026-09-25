package com.v16studio.v16service

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.util.Log
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class B047CoreNavigationSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val filterPreferences get() = compose.activity.getSharedPreferences("v16service_ui_filter_preferences", 0)
    private var hadVisitDatePreference = false
    private var previousVisitDatePreference: String? = null
    private var hadVisitStatusPreference = false
    private var previousVisitStatusPreference: String? = null

    @Before fun preserveVisitDateFilter() {
        hadVisitDatePreference = filterPreferences.contains("visits.date")
        previousVisitDatePreference = filterPreferences.getString("visits.date", null)
        hadVisitStatusPreference = filterPreferences.contains("visits.status")
        previousVisitStatusPreference = filterPreferences.getString("visits.status", null)
    }

    @After fun restoreVisitDateFilter() {
        filterPreferences.edit().apply {
            if (hadVisitDatePreference) putString("visits.date", previousVisitDatePreference) else remove("visits.date")
            if (hadVisitStatusPreference) putString("visits.status", previousVisitStatusPreference) else remove("visits.status")
        }.commit()
    }

    @Test fun coreReadOnlyScreensOpenOnThePreservedAppDataset() {
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("root-home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("root-home").assertIsDisplayed()
        Log.i("B047NavigationSmoke", "Home opened")
        val application = compose.activity.application as V16ServiceApplication
        runBlocking { application.container.startup.await() }
        val readyFinalizedVisit = runBlocking {
            application.container.repository.visits().firstOrNull { visit ->
                visit.finalRecordId?.let { recordId ->
                    application.container.repository.finalRecord(recordId)?.report?.status == "READY"
                } == true
            }
        }

        compose.onNodeWithTag("root-nav-work").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("root-work").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("content-tab-Due services").assertIsDisplayed()
        compose.onNodeWithTag("due-services-list").assertIsDisplayed()
        Log.i("B047NavigationSmoke", "Work and Due Services opened")

        if (compose.onAllNodesWithTag("root-nav-customers").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("root-nav-customers").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("root-customers").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("root-customers").assertIsDisplayed()
            val customers = compose.onAllNodesWithTag("entity-record-card").fetchSemanticsNodes()
            if (customers.isNotEmpty()) {
                compose.onAllNodesWithTag("entity-record-card")[0].performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("content-tab-Sites").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("content-tab-Sites").assertIsDisplayed()
                compose.onNodeWithTag("content-tab-Equipment").performClick()
                compose.onNodeWithTag("content-tab-Equipment").assertIsDisplayed()
                compose.onNodeWithText("Back").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("root-customers").fetchSemanticsNodes().isNotEmpty() }
                Log.i("B047NavigationSmoke", "Register and an existing Customer opened")
            } else {
                Log.i("B047NavigationSmoke", "Register opened; no standard Customer row was available")
            }
        } else {
            Log.i("B047NavigationSmoke", "Register is unavailable for the current role")
        }

        compose.onNodeWithTag("root-nav-work").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("root-work").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("content-tab-Visits").performClick()
        compose.onNodeWithTag("visit-date-selector").performClick()
        compose.onNodeWithTag("visit-date-selector-option-all").performClick()
        compose.onNodeWithTag("visit-status-selector").performClick()
        compose.onNodeWithTag("visit-status-selector-option-all").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("work-visits-list").fetchSemanticsNodes().isNotEmpty() }
        val visits = compose.onAllNodesWithTag("entity-record-card").fetchSemanticsNodes()
        if (visits.isNotEmpty()) {
            compose.onAllNodesWithTag("entity-record-card")[0].performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("visit-detail-list").fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithTag("final-record-list").fetchSemanticsNodes().isNotEmpty()
            }
            if (compose.onAllNodesWithTag("final-record-list").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithTag("final-record-list").assertIsDisplayed()
                val openedReport = runCatching {
                    compose.onNodeWithTag("final-record-list").performScrollToNode(hasText("View report"))
                    compose.onNodeWithText("View report").performClick()
                    compose.waitUntil(10_000) { compose.onAllNodesWithTag("report-preview-list").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithTag("report-preview-list").assertIsDisplayed()
                }.isSuccess
                Log.i("B047NavigationSmoke", if (openedReport) "Existing Visit and finalized report viewer opened" else "Existing finalized Visit opened; no ready report viewer was available")
                if (openedReport) compose.onNodeWithText("Back").performClick()
            } else {
                compose.onNodeWithTag("visit-detail-list").assertIsDisplayed()
                Log.i("B047NavigationSmoke", "Existing Visit opened")
            }
            compose.onNodeWithText("Back").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("root-work").fetchSemanticsNodes().isNotEmpty() }
        } else {
            Log.i("B047NavigationSmoke", "Work opened; no existing Visit matched the All filter")
        }

        if (readyFinalizedVisit != null) {
            compose.onNodeWithTag("visit-search").performTextInput(readyFinalizedVisit.reference)
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("entity-record-card").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithTag("entity-record-card")[0].performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("final-record-list").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("final-record-list").performScrollToNode(hasText("View report", substring = true))
            compose.onNodeWithText("View report", substring = true).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("report-preview-list").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("report-preview-list").assertIsDisplayed()
            Log.i("B047NavigationSmoke", "Existing finalized report viewer opened")
            compose.onNodeWithText("Back").performClick()
            compose.onNodeWithText("Back").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("root-work").fetchSemanticsNodes().isNotEmpty() }
        } else {
            Log.i("B047NavigationSmoke", "No ready finalized report exists in the preserved dataset")
        }

        compose.onAllNodesWithText("Settings")[0].performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings-reminders").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("settings-reminders").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reminder-settings").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reminder-settings").assertIsDisplayed()
        Log.i("B047NavigationSmoke", "Settings and Reminders opened")

        val dispatchPreferences = compose.activity.getSharedPreferences("dispatch_prototype", 0)
        val savedRole = dispatchPreferences.getString("team_role", null)
        val coordinatorAllowed = savedRole == "TEAM_LEADER" || savedRole == "COORDINATOR" ||
            (savedRole == null && dispatchPreferences.getBoolean("coordinator_enabled", false))
        if (coordinatorAllowed) {
            compose.onNodeWithText("Back").performClick()
            compose.onNodeWithText("Back").performClick()
            compose.onNodeWithTag("root-nav-home").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("coordinator-home-actions").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Outbox").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("dispatch-outbox").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("dispatch-outbox").assertIsDisplayed()
            Log.i("B047NavigationSmoke", "Coordinator Outbox opened for the configured role")
        } else {
            Log.i("B047NavigationSmoke", "Coordinator Outbox skipped because the configured role has no coordinator tools")
        }
    }
}
