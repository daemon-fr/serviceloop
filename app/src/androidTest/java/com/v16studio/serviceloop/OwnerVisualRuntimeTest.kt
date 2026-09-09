package com.v16studio.serviceloop

import android.graphics.Bitmap
import android.accessibilityservice.AccessibilityService
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.activity.compose.setContent
import androidx.room.Room
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import java.time.Instant
import java.time.ZoneId
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class OwnerVisualRuntimeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun captureRenderedEvidence(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        val evidenceDirectory = instrumentation.targetContext.externalCacheDir ?: instrumentation.targetContext.cacheDir
        FileOutputStream(File(evidenceDirectory, "closure-$name.png")).use {
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot.recycle()
    }

    @Test
    fun canonicalFinalRecordPdfAndTextOpenFromVisits() {
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onAllNodesWithTag("root-home").assertCountEquals(1) }.isSuccess
        }
        composeRule.onNodeWithText("Work").performClick()
        composeRule.onNodeWithText("Visits").performClick()
        composeRule.onNodeWithTag("work-visits-list").performScrollToNode(hasText("V-001", substring = true))
        composeRule.onNodeWithText("V-001", substring = true).performClick()
        composeRule.onNodeWithText("V-001 · Finalized").assertIsDisplayed()
        captureRenderedEvidence("final-record")

        composeRule.onNodeWithTag("final-record-list").performScrollToNode(hasText("View report"))
        composeRule.onNodeWithText("View report").performClick()
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithText("PDF view").fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithText("PDF view").assertIsDisplayed()
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithContentDescription("Rendered customer report page 1").fetchSemanticsNode() }.isSuccess
        }
        composeRule.onNodeWithTag("report-preview-list").performScrollToNode(hasContentDescription("Rendered customer report page 1"))
        composeRule.onNodeWithContentDescription("Rendered customer report page 1").assertIsDisplayed()
        captureRenderedEvidence("pdf")

        composeRule.onNodeWithText("Text view").performClick()
        composeRule.onNodeWithText("Service record V-001 · Revision 1").assertIsDisplayed()
        captureRenderedEvidence("text")
        if(InstrumentationRegistry.getArguments().getString("systemHandoff")=="true") {
            composeRule.onNodeWithTag("report-preview-list").performScrollToNode(hasTestTag("share-pdf"))
            composeRule.onNodeWithTag("share-pdf").performClick()
            val instrumentation=InstrumentationRegistry.getInstrumentation(); var external=false
            repeat(30) { if(instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()!="com.v16studio.serviceloop") external=true else Thread.sleep(100) }
            assertTrue("Sharesheet did not open",external)
            if(instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()!="com.v16studio.serviceloop") instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }

    @Test
    fun rootSwitchingLeavesExactlyOneRequestedRootVisible() {
        fun assertOnlyRoot(tag: String) {
            listOf("root-home", "root-work", "root-customers").forEach { candidate ->
                if (candidate == tag) composeRule.onAllNodesWithTag(candidate).assertCountEquals(1)
                else composeRule.onAllNodesWithTag(candidate).assertCountEquals(0)
            }
            composeRule.onAllNodesWithText("Reading saved service book").assertCountEquals(0)
        }

        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onAllNodesWithTag("root-home").assertCountEquals(1) }.isSuccess
        }
        assertOnlyRoot("root-home")

        repeat(3) {
            composeRule.onNodeWithText("Work").performClick()
            assertOnlyRoot("root-work")
            composeRule.onNodeWithText("Customers").performClick()
            assertOnlyRoot("root-customers")
            composeRule.onNodeWithText("Home").performClick()
            assertOnlyRoot("root-home")
        }

        composeRule.onNodeWithText("Customers").performClick()
        assertOnlyRoot("root-customers")
        composeRule.onNodeWithText("Work").performClick()
        assertOnlyRoot("root-work")
        composeRule.onNodeWithTag("field-search-due-services").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithTag("work-visits-list").assertCountEquals(0)

        composeRule.onNodeWithText("Visits").performClick()
        composeRule.onNodeWithTag("work-visits-list").assertIsDisplayed()
        composeRule.onNodeWithText("Customers").performClick()
        assertOnlyRoot("root-customers")
        composeRule.onNodeWithText("Work").performClick()
        assertOnlyRoot("root-work")
        composeRule.onNodeWithTag("field-search-due-services").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithTag("work-visits-list").assertCountEquals(0)
        composeRule.onNodeWithText("Home").performClick()
        assertOnlyRoot("root-home")
    }

    @Test
    fun canonicalStageASmokeReachesExistingSurfacesWithoutBusinessWrites() {
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("root-home").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithText("Work").performClick()
        composeRule.onNodeWithText("Visits").performClick()
        composeRule.onNodeWithTag("work-visits-list").performScrollToNode(hasText("History"))
        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithTag("history-list").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("Customers").performClick()
        composeRule.onNodeWithTag("root-customers").assertIsDisplayed()
        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithTag("root-home").assertIsDisplayed()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Reminders", substring = true).performClick()
        composeRule.onNodeWithTag("reminder-settings").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("Calendar", substring = true).performClick()
        composeRule.onNodeWithTag("calendar-settings").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("Coordinator tools").performClick()
        composeRule.onNodeWithTag("coordinator-tools-switch").assertIsDisplayed()
        pressBack()
        pressBack()

        if (composeRule.onAllNodesWithTag("coordinator-home-actions").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Outbox").performClick()
            composeRule.onNodeWithTag("dispatch-outbox").assertIsDisplayed()
        }
    }

    @Test
    fun inspectionResponseDraftsSwitchWithoutWarningAndRestoreSavedAndLocalBuffers() {
        val database = Room.inMemoryDatabaseBuilder(composeRule.activity, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        runBlocking {
            val dao = database.serviceLoopDao()
            dao.insertCustomers(listOf(CustomerEntity("c", "CU-1", "Customer")))
            dao.insertSites(listOf(SiteEntity("s", "c", "ST-1", "Site", null, null)))
            dao.insertEquipment(listOf(EquipmentEntity("e", "s", "EQ-1", null, "Equipment", null, null, null, null)))
            dao.insertTemplateSnapshots(listOf(TemplateSnapshotEntity("t", null, "Inspection", 1, 1)))
            dao.insertChecklistItems(listOf(ChecklistItemSnapshotEntity("check-belt", "t", 1, "Belt condition", "STATUS", null, true, null), ChecklistItemSnapshotEntity("check-note", "t", 2, "Record cabinet observations", "TEXT", null, true, null)))
            dao.insertVisits(listOf(WorkingVisitEntity("v", "V-TEST", "c", "s", "2026-09-05", "Customer", "Site", null, "WORKING", 1)))
            dao.insertWorkItems(listOf(WorkItemEntity("w", "v", "e", null, null, "t", "Equipment", "EQ-1", "Inspection", null, null, null, null, false, null, false)))
            dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity("w", ""))); dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity("w", "")))
            dao.upsertResponses(listOf(WorkingResponseEntity("response", "w", "check-belt", "ISSUE_FOUND", null, null, "Initial finding", 1, issueFoundReasonDraft = "Initial finding"), WorkingResponseEntity("response-note", "w", "check-note", "VALUE", "Initial cabinet note", null, null, 1)))
        }
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val viewModel = ServiceLoopViewModel(RoomServiceLoopRepository(database, time)) {}
        composeRule.activity.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel) } }
        composeRule.waitUntil(5_000) { runCatching { composeRule.onNodeWithText("Resume visit").fetchSemanticsNode() }.isSuccess }
        composeRule.onNodeWithText("Resume visit").performClick()
        composeRule.onNodeWithTag("long-text-public-work-performed", useUnmergedTree = true).performScrollTo()
        captureRenderedEvidence("public-work-expand-icon")
        val field = composeRule.onNodeWithTag("long-text-public-finding-description", useUnmergedTree = true)
        field.performScrollTo()
        captureRenderedEvidence("issue-found-expand-icon")

        composeRule.onNodeWithTag("long-text-public-finding-description-expand", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onAllNodesWithText("Expand").assertCountEquals(0)
        composeRule.onNodeWithTag("long-text-public-finding-description-expand", useUnmergedTree = true)
            .assertContentDescriptionEquals("Expand text editor")

        field.performTextClearance()
        field.performTextInput("Belt edge wear observed during inspection")
        composeRule.onNodeWithTag("finding-save-check-belt", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("finding-save-check-belt", useUnmergedTree = true).assertIsNotEnabled() }.isSuccess
        }

        composeRule.onNodeWithText("Back").performClick()
        composeRule.onAllNodesWithTag("root-home").assertCountEquals(1)
        composeRule.onAllNodesWithText("Reading saved service book").assertCountEquals(0)
        composeRule.onNodeWithText("Resume visit").performClick()
        composeRule.onNodeWithTag("long-text-public-finding-description", useUnmergedTree = true).performScrollTo().assertTextContains("Belt edge wear observed during inspection")

        composeRule.onNodeWithTag("response-check-belt-OK").performClick()
        composeRule.onAllNodesWithTag("long-text-public-finding-description", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Discard saved response detail?").assertCountEquals(0)

        composeRule.onNodeWithTag("response-check-belt-NOT_APPLICABLE").performClick()
        val naField=composeRule.onNodeWithTag("not-applicable-reason-check-belt")
        naField.performTextInput("Guard unavailable")
        composeRule.onNodeWithTag("not-applicable-save-check-belt").performClick()
        composeRule.onNodeWithTag("response-check-belt-ISSUE_FOUND").performClick()
        val restored=composeRule.onNodeWithTag("long-text-public-finding-description", useUnmergedTree = true).performScrollTo().assertTextContains("Belt edge wear observed during inspection")
        restored.performTextInput("; local unsaved note")
        composeRule.onNodeWithTag("response-check-belt-OK").performClick()
        composeRule.onNodeWithTag("response-check-belt-ISSUE_FOUND").performClick()
        composeRule.onNodeWithTag("long-text-public-finding-description", useUnmergedTree = true).assertTextContains("local unsaved note", substring = true)
        composeRule.onNodeWithTag("response-check-belt-NOT_APPLICABLE").performClick()
        composeRule.onNodeWithTag("not-applicable-reason-check-belt").assertTextContains("Guard unavailable")
        composeRule.onNodeWithTag("inspection-list").performScrollToNode(hasTestTag("value-check-note"))
        val valueField=composeRule.onNodeWithTag("value-check-note").assertTextContains("Initial cabinet note")
        composeRule.onNodeWithTag("not-applicable-check-note").performClick()
        composeRule.onNodeWithTag("not-applicable-reason-check-note").performTextInput("Cabinet isolated")
        composeRule.onNodeWithTag("not-applicable-save-check-note").performClick()
        valueField.assertTextContains("Initial cabinet note")
        composeRule.onNodeWithTag("value-save-check-note").performClick()
        composeRule.waitUntil(5_000) { runCatching { composeRule.onNodeWithTag("value-save-check-note").assertIsEnabled() }.isSuccess }
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Resume visit").performClick()
        composeRule.onNodeWithTag("inspection-list").performScrollToNode(hasTestTag("value-check-note"))
        composeRule.onNodeWithTag("value-check-note").assertTextContains("Initial cabinet note")
        composeRule.onNodeWithTag("not-applicable-check-note").performClick()
        composeRule.onNodeWithTag("not-applicable-reason-check-note").assertTextContains("Cabinet isolated")
        composeRule.onNodeWithTag("inspection-list").performScrollToNode(hasTestTag("open-field-evidence"))
        composeRule.onNodeWithTag("open-field-evidence").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Parts and photographs").assertIsDisplayed()
        database.close()
    }
}
