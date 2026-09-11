package com.v16studio.serviceloop

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.domain.CompletionLine
import com.v16studio.serviceloop.domain.CustomerSummary
import com.v16studio.serviceloop.domain.DueBucket
import com.v16studio.serviceloop.domain.DueService
import com.v16studio.serviceloop.domain.EquipmentSummary
import com.v16studio.serviceloop.domain.FollowUpDetail
import com.v16studio.serviceloop.domain.HomeSummary
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.domain.VisitSummary
import com.v16studio.serviceloop.ui.DueServicesProjection
import com.v16studio.serviceloop.ui.DueServicesScreen
import com.v16studio.serviceloop.ui.FollowUpsWorkScreen
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.UiState
import com.v16studio.serviceloop.ui.VisitsWorkScreen
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WorkFilterSelectorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun dueVisitSelectorUsesSelectedMenuStateAndUpdatesResultsImmediately() {
        val state = UiState(
            loading = false,
            dueServicesProjection = DueServicesProjection.Available(emptyList()),
            businessDate = LocalDate.of(2026, 9, 11),
            businessZoneId = "Europe/Bucharest",
        )
        val viewModel = ServiceLoopViewModel(EmptyRepository()) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    DueServicesScreen(
                        values = listOf(due("no-visit", null), due("has-visit", "visit-1")),
                        padding = PaddingValues(),
                        state = state,
                        viewModel = viewModel,
                        nav = rememberNavController(),
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Due date, All").assertIsDisplayed()
        compose.onNodeWithContentDescription("Visit, All").assertIsDisplayed().performClick()
        compose.onNodeWithTag("due-visit-selector-option-all").assertIsSelected()
        compose.onNodeWithTag("due-visit-selector-option-novisit").performClick()
        assertTrue(compose.onAllNodesWithTag("due-visit-selector-option-novisit").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithContentDescription("Visit, No visit").assertIsDisplayed()
        compose.onNodeWithText("P-no-visit · Service no-visit").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("P-has-visit · Service has-visit").fetchSemanticsNodes().isEmpty())
    }

    @Test fun visitsAndFollowUpsExposeAdoptedDefaultsAndNoSupersededFilterCopy() {
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    VisitsWorkScreen(
                        values = emptyList(),
                        businessDate = LocalDate.of(2026, 9, 11),
                        padding = PaddingValues(),
                        nav = rememberNavController(),
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Date, Today").assertIsDisplayed()
        compose.onNodeWithContentDescription("Status, All").assertIsDisplayed()
        compose.onNodeWithTag("visit-date-selector").performClick()
        compose.onNodeWithTag("visit-date-selector-option-past30days").assertIsDisplayed()

        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    FollowUpsWorkScreen(
                        values = emptyList(),
                        businessDate = LocalDate.of(2026, 9, 11),
                        padding = PaddingValues(),
                        nav = rememberNavController(),
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Due date, All").assertIsDisplayed()
        compose.onNodeWithContentDescription("Status, Open").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("All open").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Due or overdue").fetchSemanticsNodes().isEmpty())
    }

    @Test fun darkAppearanceKeepsWhiteSelectorMenuInteractionReadable() {
        val state = UiState(
            loading = false,
            dueServicesProjection = DueServicesProjection.Available(emptyList()),
            businessDate = LocalDate.of(2026, 9, 11),
            businessZoneId = "Europe/Bucharest",
        )
        val viewModel = ServiceLoopViewModel(EmptyRepository()) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme(darkTheme = true) {
                    DueServicesScreen(
                        values = listOf(due("dark-no-visit", null)),
                        padding = PaddingValues(),
                        state = state,
                        viewModel = viewModel,
                        nav = rememberNavController(),
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Visit, All").assertIsDisplayed().performClick()
        compose.onNodeWithTag("due-visit-selector-option-all").assertIsSelected()
        compose.onNodeWithTag("due-visit-selector-option-novisit").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Visit, No visit").assertIsDisplayed()
    }

    private fun due(id: String, claimedVisitId: String?) = DueService(
        planId = id,
        planReference = "P-$id",
        planName = "Service $id",
        dueDate = "2026-09-11",
        obligationId = "obligation-$id",
        equipmentId = "equipment-$id",
        equipmentReference = "EQ-$id",
        equipmentName = "Equipment $id",
        siteId = "site-$id",
        siteName = "Site $id",
        customerId = "customer-$id",
        customerName = "Customer $id",
        claimedVisitId = claimedVisitId,
        bucket = DueBucket.TODAY,
    )

    private class EmptyRepository : ServiceLoopRepository {
        override suspend fun home() = HomeSummary(null, null, null, null, null, null, null, 0, null, null, 0, 0)
        override suspend fun equipment(id: String) = null
        override suspend fun equipmentList(): List<EquipmentSummary> = emptyList()
        override suspend fun customerList(): List<CustomerSummary> = emptyList()
        override suspend fun inspection(workItemId: String): InspectionDraft? = null
        override suspend fun completionLines(visitId: String): List<CompletionLine> = emptyList()
        override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?) = 0L
        override fun observeDueServices(): Flow<List<DueService>> = emptyFlow()
    }
}
