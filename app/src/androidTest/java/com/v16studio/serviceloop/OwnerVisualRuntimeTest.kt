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
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.report.AndroidReportService
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class OwnerVisualRuntimeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun captureRenderedEvidence(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(500)
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        val evidenceDirectory = instrumentation.targetContext.externalCacheDir ?: instrumentation.targetContext.cacheDir
        FileOutputStream(File(evidenceDirectory, "closure-$name.png")).use {
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot.recycle()
    }

    @Test
    fun finalRecordPdfAndTextOpenFromTestOwnedRecord() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        try {
            val time = object : BusinessTime {
                override val zoneId = ZoneId.of("Europe/Bucharest")
                override fun instant() = Instant.parse("2026-09-05T10:00:00Z")
            }
            runBlocking {
                val dao = database.serviceLoopDao()
                dao.insertCustomers(listOf(CustomerEntity("c", "CU-1", "Customer")))
                dao.insertSites(listOf(SiteEntity("s", "c", "ST-1", "Site", "Address", null)))
                dao.insertEquipment(listOf(EquipmentEntity("e", "s", "EQ-1", "TECH-1", "Equipment", "Maker", "Model", "Serial", null)))
                dao.insertPlans(listOf(ServicePlanEntity("p", "e", "P-1", "Service", 3, "MONTHS", "2026-09-01", "ACTIVE", "o")))
                dao.insertObligations(listOf(ServiceObligationEntity("o", "p", 1, "2026-09-01", 1)))
                dao.insertVisits(listOf(WorkingVisitEntity("v", "V-UI", "c", "s", "2026-09-05", "Customer", "Site", "Address", "WORKING", 1, "CU-1", "ST-1", "Business", "Technician", null, null, null, "Europe/Bucharest")))
                dao.insertWorkItems(listOf(WorkItemEntity("w", "v", "e", "p", "o", null, "Equipment", "EQ-1", "Service", "P-1", "2026-09-01", 3, "MONTHS", false, null, false, equipmentIdentifierSnapshot = "TECH-1", equipmentMakeSnapshot = "Maker", equipmentModelSnapshot = "Model", equipmentSerialSnapshot = "Serial")))
                dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity("w", "Completed service")))
                dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity("w", "")))
                dao.insertFinalRecord(FinalRecordEntity("r", "v", "rev", 2))
                dao.insertFinalRevision(FinalRecordRevisionEntity("rev", "r", 1, "V-UI", "2026-09-05", 2, "Customer", "Site", "Address", "Business", "Technician", null, null, null, "Europe/Bucharest", null, "CU-1", "ST-1"))
                dao.insertFinalWorkItems(listOf(FinalWorkItemEntity("fw", "rev", 1, "w", "e", "Equipment", "EQ-1", "TECH-1", "Maker", "Model", "Serial", "Service", "p", "P-1", "NOT_PERFORMED", null, "Access unavailable", false, "2026-09-01", null, 3, "MONTHS", "o", null)))
                dao.finalizeVisit("v", 2)
            }
            val repository = RoomServiceLoopRepository(database, time, attachmentRoot = context.filesDir)
            val reportService = AndroidReportService(context, database, repository)
            runBlocking { reportService.generate("r") }
            val viewModel = ServiceLoopViewModel(repository, reportService) {}
            composeRule.activity.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel, "record/r") } }
            composeRule.waitUntil(10_000) {
                runCatching { composeRule.onNodeWithTag("final-record-list").assertIsDisplayed() }.isSuccess
            }
            composeRule.onNodeWithTag("final-record-list").assertIsDisplayed()
            composeRule.onNodeWithText("V-UI", substring = true).assertIsDisplayed()
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
            composeRule.onNodeWithText("Service record V-UI · Revision 1").assertIsDisplayed()
            captureRenderedEvidence("text")
            if (InstrumentationRegistry.getArguments().getString("systemHandoff") == "true") {
                composeRule.onNodeWithTag("report-preview-list").performScrollToNode(hasTestTag("share-pdf"))
                composeRule.onNodeWithTag("share-pdf").performClick()
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                var external = false
                repeat(30) { if (instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() != "com.v16studio.serviceloop") external = true else Thread.sleep(100) }
                assertTrue("Sharesheet did not open", external)
                if (instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() != "com.v16studio.serviceloop") instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }
        } finally {
            database.close()
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
            composeRule.onNodeWithText("Register").performClick()
            assertOnlyRoot("root-customers")
            composeRule.onNodeWithText("Home").performClick()
            assertOnlyRoot("root-home")
        }

        composeRule.onNodeWithText("Register").performClick()
        assertOnlyRoot("root-customers")
        composeRule.onNodeWithText("Work").performClick()
        assertOnlyRoot("root-work")
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("field-search-due-services").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("field-search-due-services").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithTag("work-visits-list").assertCountEquals(0)

        composeRule.onNodeWithText("Visits").performClick()
        composeRule.onNodeWithTag("work-visits-list").assertIsDisplayed()
        composeRule.onNodeWithText("Register").performClick()
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
        composeRule.onNodeWithTag("work-visits-list").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithTag("history-list").assertIsDisplayed()
        goBack()
        goBack()
        composeRule.onNodeWithText("Register").performClick()
        composeRule.onNodeWithTag("root-customers").assertIsDisplayed()
        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithTag("root-home").assertIsDisplayed()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithTag("settings-reminders").performClick()
        composeRule.onNodeWithTag("reminder-settings").assertIsDisplayed()
        goBack()
        composeRule.onNodeWithTag("settings-calendar").performClick()
        composeRule.onNodeWithTag("calendar-settings").assertIsDisplayed()
        goBack()
        composeRule.onNodeWithTag("settings-team-role").performClick()
        composeRule.onNodeWithTag("team-role-settings").assertIsDisplayed()
        goBack()
        goBack()

        if (composeRule.onAllNodesWithTag("coordinator-home-actions").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Outbox").performClick()
            composeRule.onNodeWithTag("dispatch-outbox").assertIsDisplayed()
        }
    }

    @Test
    fun inspectionResponseDraftsSwitchWithoutWarningAndRestoreSavedAndLocalBuffers() {
        val database = Room.inMemoryDatabaseBuilder(composeRule.activity, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        val dao = database.serviceLoopDao()
        runBlocking {
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
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("operational-work-row-v").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("operational-work-row-v").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("service-list").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("service-list").performScrollToNode(hasTestTag("long-text-public-work-performed"))
        captureRenderedEvidence("public-work-expand-icon")
        composeRule.onNodeWithTag("service-list").performScrollToNode(hasTestTag("long-text-public-finding-description"))
        val field = composeRule.onNodeWithTag("long-text-public-finding-description", useUnmergedTree = true)
        captureRenderedEvidence("issue-found-expand-icon")

        composeRule.onNodeWithTag("long-text-public-finding-description-expand", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onAllNodesWithText("Expand").assertCountEquals(0)
        composeRule.onNodeWithTag("long-text-public-finding-description-expand", useUnmergedTree = true)
            .assertContentDescriptionEquals("Expand Public finding description")

        field.performTextClearance()
        field.performTextInput("Belt edge wear observed during inspection")

        composeRule.onNodeWithText("Back").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("visit-detail-list").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("visit-detail-list").assertIsDisplayed()
        composeRule.onAllNodesWithText("Reading saved service book").assertCountEquals(0)
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("visit-line-w").fetchSemanticsNodes().isNotEmpty() }
        val durableFinding = runBlocking { dao.responses("w").single { it.checklistItemSnapshotId == "check-belt" } }
        assertEquals("Belt edge wear observed during inspection", durableFinding.reason)
        assertEquals("Belt edge wear observed during inspection", durableFinding.issueFoundReasonDraft)
        composeRule.onNodeWithTag("visit-line-w").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("service-list").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("service-list").performScrollToNode(hasTestTag("long-text-public-finding-description"))
        composeRule.onNodeWithTag("long-text-public-finding-description", useUnmergedTree = true).assertTextContains("Belt edge wear observed during inspection")

        composeRule.onNodeWithTag("response-check-belt-OK", useUnmergedTree = true).performClick()
        composeRule.onAllNodesWithTag("long-text-public-finding-description", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Discard saved response detail?").assertCountEquals(0)

        composeRule.onNodeWithTag("response-check-belt-NOT_APPLICABLE", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("service-list").performScrollToNode(hasTestTag("not-applicable-reason-check-belt"))
        val naField=composeRule.onNodeWithTag("not-applicable-reason-check-belt", useUnmergedTree = true)
        naField.performTextInput("Guard unavailable")
        composeRule.onNodeWithTag("response-check-belt-ISSUE_FOUND", useUnmergedTree = true).performClick()
        composeRule.waitUntil(5_000) {
            runBlocking { dao.responses("w").single { it.checklistItemSnapshotId == "check-belt" }.disposition == ResponseDisposition.ISSUE_FOUND.name }
        }
        val preservedNaReason = runBlocking { dao.responses("w").single { it.checklistItemSnapshotId == "check-belt" }.notApplicableReasonDraft }
        assertEquals("Guard unavailable", preservedNaReason)
        composeRule.waitUntil(5_000) {
            viewModel.state.value.inspection?.questions?.firstOrNull { it.snapshotItemId == "check-belt" }?.disposition == ResponseDisposition.ISSUE_FOUND
        }
        composeRule.onNodeWithTag("service-list").performScrollToNode(hasTestTag("long-text-public-finding-description"))
        val restored=composeRule.onNodeWithTag("long-text-public-finding-description", useUnmergedTree = true).assertTextContains("Belt edge wear observed during inspection")
        restored.performTextInput("; local unsaved note")
        composeRule.onNodeWithTag("response-check-belt-OK", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("response-check-belt-ISSUE_FOUND", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("service-list").performScrollToNode(hasTestTag("long-text-public-finding-description"))
        composeRule.onNodeWithTag("long-text-public-finding-description", useUnmergedTree = true).assertTextContains("local unsaved note", substring = true)
        composeRule.onNodeWithTag("response-check-belt-NOT_APPLICABLE", useUnmergedTree = true).performClick()
        composeRule.waitUntil(5_000) { runCatching { composeRule.onNodeWithTag("not-applicable-reason-check-belt", useUnmergedTree = true).assertTextContains("Guard unavailable") }.isSuccess }
        composeRule.onNodeWithTag("service-list").performScrollToNode(hasTestTag("value-check-note"))
        val valueField=composeRule.onNodeWithTag("value-check-note").assertTextContains("Initial cabinet note")
        composeRule.onNodeWithTag("service-list").performScrollToNode(hasTestTag("not-applicable-check-note"))
        composeRule.onNodeWithTag("not-applicable-check-note", useUnmergedTree = true).performClick()
        composeRule.waitUntil(5_000) {
            runBlocking { database.serviceLoopDao().responses("w").firstOrNull { it.checklistItemSnapshotId == "check-note" }?.disposition == ResponseDisposition.NOT_APPLICABLE.name }
        }
        composeRule.waitUntil(5_000) {
            viewModel.state.value.inspection?.questions?.firstOrNull { it.snapshotItemId == "check-note" }?.disposition == ResponseDisposition.NOT_APPLICABLE
        }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("not-applicable-reason-check-note", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("not-applicable-reason-check-note", useUnmergedTree = true).performTextInput("Cabinet isolated")
        valueField.assertTextContains("Initial cabinet note")
        composeRule.onNodeWithText("Back").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("visit-line-w").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("visit-line-w").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("service-list").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("service-list").performScrollToNode(hasTestTag("value-check-note"))
        composeRule.onNodeWithTag("value-check-note").assertTextContains("Initial cabinet note")
        composeRule.onNodeWithTag("service-list").performScrollToNode(hasTestTag("not-applicable-check-note"))
        composeRule.onNodeWithTag("not-applicable-check-note", useUnmergedTree = true).performClick()
        composeRule.waitUntil(5_000) {
            runBlocking { database.serviceLoopDao().responses("w").firstOrNull { it.checklistItemSnapshotId == "check-note" }?.disposition == ResponseDisposition.NOT_APPLICABLE.name }
        }
        composeRule.waitUntil(5_000) {
            viewModel.state.value.inspection?.questions?.firstOrNull { it.snapshotItemId == "check-note" }?.disposition == ResponseDisposition.NOT_APPLICABLE
        }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("not-applicable-reason-check-note", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("not-applicable-reason-check-note", useUnmergedTree = true).assertTextContains("Cabinet isolated")
        composeRule.onNodeWithTag("service-list").performScrollToNode(hasTestTag("service-photos"))
        composeRule.onNodeWithTag("service-photos").assertIsDisplayed()
        database.close()
    }

    private fun goBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    }
}
