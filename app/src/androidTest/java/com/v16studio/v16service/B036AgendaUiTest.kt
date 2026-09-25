package com.v16studio.v16service

import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v16studio.v16service.domain.DueBucket
import com.v16studio.v16service.domain.DueService
import com.v16studio.v16service.domain.FollowUpDetail
import com.v16studio.v16service.domain.VisitSummary
import com.v16studio.v16service.ui.HomeAgendaScreen
import com.v16studio.v16service.ui.DueServicesProjection
import com.v16studio.v16service.ui.UiState
import com.v16studio.v16service.ui.theme.V16ServiceTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class B036AgendaUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun agendaShowsExactSectionsDenseRowsAndNavigatesVisits() {
        lateinit var navController: androidx.navigation.NavHostController
        val state = UiState(
            loading = false,
            visits = listOf(
                VisitSummary(
                    id = "visit-1",
                    reference = "V-001",
                    siteName = "North site",
                    actualServiceDate = "2026-09-18",
                    state = "BOOKED",
                    finalRecordId = null,
                    customerId = "customer-1",
                    customerName = "Acme",
                    siteId = "site-1",
                    scheduledAtEpochMillis = 1_758_193_800_000L,
                ),
            ),
            dueServicesProjection = DueServicesProjection.Available(listOf(
                DueService(
                    planId = "plan-1",
                    planReference = "PL-001",
                    planName = "Quarterly service",
                    dueDate = "2026-09-19",
                    obligationId = "obligation-1",
                    equipmentId = "equipment-1",
                    equipmentReference = "EQ-001",
                    equipmentName = "Boiler",
                    siteId = "site-1",
                    siteName = "North site",
                    customerId = "customer-1",
                    customerName = "Acme",
                    claimedVisitId = null,
                    bucket = DueBucket.UPCOMING,
                ),
            )),
            followUps = listOf(
                FollowUpDetail(
                    id = "follow-up-1",
                    reference = "FU-001",
                    type = "CONTACT",
                    title = "Confirm access",
                    dueDate = "2026-09-17",
                    state = "OPEN",
                    customerId = "customer-1",
                    siteId = "site-1",
                    equipmentId = null,
                    privatePlanningNote = "",
                    closureReason = null,
                    customerName = "Acme",
                    siteName = "North site",
                ),
            ),
            businessDate = LocalDate.parse("2026-09-18"),
            businessZoneId = "Europe/Bucharest",
        )

        compose.setContent {
            V16ServiceTheme {
                val nav = rememberNavController()
                navController = nav
                NavHost(nav, "agenda") {
                    composable("agenda") { HomeAgendaScreen(state, nav) }
                    composable("visit/{id}") { Text("Visit destination") }
                    composable("plan/{id}") { Text("Plan destination") }
                    composable("follow-up/{id}") { Text("Follow-up destination") }
                }
            }
        }

        compose.onNodeWithTag("agenda-section-unresolved").assertIsDisplayed()
        compose.onNodeWithTag("agenda-section-upcoming").assertIsDisplayed()
        compose.onNodeWithTag("agenda-item-follow-up-1").assertIsDisplayed()
        compose.onNodeWithTag("agenda-item-visit-1").assertIsDisplayed()
        compose.onNodeWithTag("agenda-item-plan-1").assertIsDisplayed()
        compose.onNodeWithTag("agenda-item-visit-1").performClick()
        compose.waitForIdle()
        assertEquals("visit/{id}", navController.currentDestination?.route)
    }
}
