package com.v16studio.serviceloop

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalDueServicesProjectionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun preservedProjectionRemainsStableAcrossAdversarialRootNavigation() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("preservedDue") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ServiceLoopApplication
        app.container.startup.await()
        val dao = app.container.database.serviceLoopDao()
        val expected = dao.dueServices()
        assertTrue(expected.size >= 3)
        assertEquals(listOf("P-004", "P-002", "P-003"), expected.take(3).map { it.planReference })
        for (reference in listOf("P-002", "P-003", "P-004")) {
            val row = expected.single { it.planReference == reference }
            val plan = dao.plan(row.planId)!!
            val obligation = dao.obligation(row.obligationId)!!
            val equipment = dao.equipment(row.equipmentId)!!
            val site = dao.site(row.siteId)!!
            val customer = dao.customer(row.customerId)!!
            assertEquals("ACTIVE", plan.state)
            assertEquals("ACTIVE", equipment.state)
            assertEquals("ACTIVE", site.state)
            assertEquals("ACTIVE", customer.state)
            assertEquals("2026-09-01", plan.currentDueDate)
            assertEquals(plan.currentObligationId, obligation.id)
            assertEquals("2026-09-01", obligation.dueDate)
            assertNull(obligation.consumedAtEpochMillis)
            assertNull(obligation.consumedByRevisionId)
            assertNull(dao.claimForObligation(obligation.id))
        }

        compose.waitUntil(10_000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Work").performClick()
        compose.onNodeWithText("P-004 · Maintenance", substring = true).assertIsDisplayed()

        val viewModel = ServiceLoopViewModel(app.container.repository) { app.container.startup.await() }
        compose.runOnUiThread { compose.activity.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel) } } }
        compose.waitUntil(10_000) { viewModel.state.value.rootDataReady && viewModel.state.value.dueServicesReady }
        repeat(20) { index ->
            compose.onNodeWithText("Work").performClick()
            compose.onNodeWithText("P-004 · Maintenance", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Refreshing due services").assertDoesNotExist()
            compose.onNodeWithText("Reading due services").assertDoesNotExist()
            compose.onNodeWithText(if (index % 2 == 0) "Home" else "Customers").performClick()
        }
        compose.onNodeWithText("Work").performClick()
        compose.onNodeWithText("Visits").performClick()
        compose.onNodeWithText("Due services").performClick()
        compose.onNodeWithText("P-002 · Lubrication service", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Follow-ups").performClick()
        compose.onNodeWithText("Due services").performClick()
        compose.onNodeWithText("P-003 · Maintenance", substring = true).assertIsDisplayed()
        compose.onNodeWithText("New visit").performClick()
        compose.onNodeWithText("Set up visit").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("P-004 · Maintenance", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Home").performClick()
        compose.onAllNodesWithText("View all")[0].performClick()
        compose.onNodeWithText("P-004 · Maintenance", substring = true).assertIsDisplayed()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(10_000) { viewModel.state.value.dueServicesReady }
        compose.onNodeWithText("P-004 · Maintenance", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onAllNodesWithText("Settings")[0].assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Work").performClick()
        compose.onNodeWithText("P-004 · Maintenance", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Refreshing due services").assertDoesNotExist()
        assertEquals(expected.map { it.planReference }, viewModel.state.value.dueServices.map { it.planReference })
    }
}
