package com.v16studio.serviceloop

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.activity.compose.setContent
import androidx.room.Room
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class OwnerVisualRuntimeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

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
        composeRule.onNodeWithText("Home").performClick()
        assertOnlyRoot("root-home")
    }

    @Test
    fun inlineFindingEditsPersistsAndPreservesDestructiveTransitionSemantics() {
        val database = Room.inMemoryDatabaseBuilder(composeRule.activity, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        runBlocking {
            val dao = database.serviceLoopDao()
            dao.insertCustomers(listOf(CustomerEntity("c", "CU-1", "Customer")))
            dao.insertSites(listOf(SiteEntity("s", "c", "ST-1", "Site", null, null)))
            dao.insertEquipment(listOf(EquipmentEntity("e", "s", "EQ-1", null, "Equipment", null, null, null, null)))
            dao.insertTemplateSnapshots(listOf(TemplateSnapshotEntity("t", null, "Inspection", 1, 1)))
            dao.insertChecklistItems(listOf(ChecklistItemSnapshotEntity("check-belt", "t", 1, "Belt condition", "STATUS", null, true, null)))
            dao.insertVisits(listOf(WorkingVisitEntity("v", "V-TEST", "c", "s", "2026-09-05", "Customer", "Site", null, "WORKING", 1)))
            dao.insertWorkItems(listOf(WorkItemEntity("w", "v", "e", null, null, "t", "Equipment", "EQ-1", "Inspection", null, null, null, null, false, null, false)))
            dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity("w", ""))); dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity("w", "")))
            dao.upsertResponses(listOf(WorkingResponseEntity("response", "w", "check-belt", "ISSUE_FOUND", null, null, "Initial finding", 1)))
        }
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val viewModel = ServiceLoopViewModel(RoomServiceLoopRepository(database, time)) {}
        composeRule.activity.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel) } }
        composeRule.onNodeWithText("Resume visit").performClick()
        val field = composeRule.onNodeWithTag("finding-field-check-belt")
        field.performScrollTo()

        composeRule.onNodeWithTag("finding-expand-check-belt").performClick()
        composeRule.onNodeWithText("Collapse").performClick()

        field.performTextClearance()
        field.performTextInput("Belt edge wear observed during inspection")
        composeRule.onNodeWithTag("finding-save-check-belt").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("finding-save-check-belt").assertIsNotEnabled() }.isSuccess
        }

        composeRule.onNodeWithText("Back").performClick()
        composeRule.onAllNodesWithTag("root-home").assertCountEquals(1)
        composeRule.onAllNodesWithText("Reading saved service book").assertCountEquals(0)
        composeRule.onNodeWithText("Resume visit").performClick()
        composeRule.onNodeWithTag("finding-field-check-belt").performScrollTo().assertTextContains("Belt edge wear observed during inspection")

        composeRule.onNodeWithTag("response-check-belt-OK").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("finding-field-check-belt").assertTextContains("Belt edge wear observed during inspection")

        composeRule.onNodeWithTag("response-check-belt-OK").performClick()
        composeRule.onNodeWithText("Discard and change").performClick()
        composeRule.onAllNodesWithTag("finding-field-check-belt").assertCountEquals(0)

        composeRule.onNodeWithTag("response-check-belt-ISSUE_FOUND").performClick()
        composeRule.onNodeWithTag("finding-field-check-belt").performTextInput("Belt edge wear observed; inspect before next use")
        composeRule.onNodeWithTag("finding-save-check-belt").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("finding-save-check-belt").assertIsNotEnabled() }.isSuccess
        }
        database.close()
    }
}
