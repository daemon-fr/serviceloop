package com.v16studio.v16service

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.v16service.data.RoomV16ServiceRepository
import com.v16studio.v16service.data.V16ServiceDatabase
import com.v16studio.v16service.domain.BusinessTime
import com.v16studio.v16service.domain.CustomerInput
import com.v16studio.v16service.domain.EquipmentInput
import com.v16studio.v16service.domain.PlanInput
import com.v16studio.v16service.domain.SiteInput
import com.v16studio.v16service.ui.V16ServiceApp
import com.v16studio.v16service.ui.V16ServiceViewModel
import com.v16studio.v16service.ui.theme.V16ServiceTheme
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DueServicesProjectionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val time = object : BusinessTime {
        override val zoneId = ZoneId.of("Europe/Bucharest")
        override fun instant() = Instant.parse("2026-09-08T10:00:00Z")
    }
    private lateinit var database: V16ServiceDatabase

    @Before fun resetUiFilterPreferences() {
        context.getSharedPreferences("v16service_ui_filter_preferences", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After fun close() {
        compose.runOnUiThread { compose.activity.setContent {} }
        compose.waitForIdle()
        if (::database.isInitialized) database.close()
    }

    @Test fun roomProjectionStaysAvailableAcrossNavigationAndUpdatesClaims() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, V16ServiceDatabase::class.java).allowMainThreadQueries().build()
        val repository = RoomV16ServiceRepository(database, time, attachmentRoot = context.filesDir)
        val customer = repository.createCustomer(CustomerInput("Projection customer"))
        val site = repository.createSite(customer, SiteInput("Projection site", "1 Test Street"))
        val equipment = repository.createEquipment(site, EquipmentInput("Projection machine"))
        val plan = repository.createPlan(equipment, PlanInput("Projection service", 1, "YEARS", "2026-09-01"))
        val viewModel = V16ServiceViewModel(repository) {}
        compose.runOnUiThread { compose.activity.setContent { V16ServiceTheme { V16ServiceApp(viewModel) } } }
        compose.waitUntil(10_000) { viewModel.state.value.dueServicesReady && viewModel.state.value.dueServices.size == 1 }

        repeat(8) { index ->
            compose.onNodeWithText("Work").performClick()
            compose.onNodeWithText("P-001 · Projection service", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Refreshing due services").assertDoesNotExist()
            compose.onNodeWithText(if (index % 2 == 0) "Home" else "Register").performClick()
        }

        val visit = repository.createVisit(listOf(plan), "BOOKED", "2026-09-12")
        compose.onNodeWithText("Work").performClick()
        compose.waitUntil(10_000) { viewModel.state.value.dueServices.singleOrNull()?.claimedVisitId == visit }
        assertEquals(plan, viewModel.state.value.dueServices.single().planId)

        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Visit, All").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Visit, All").assertIsDisplayed().performClick()
        compose.onNodeWithTag("due-visit-selector-option-hasvisit").performClick()
        compose.onNodeWithContentDescription("Visit, Has visit").assertIsDisplayed()
        compose.onNodeWithText("P-001 · Projection service", substring = true).assertIsDisplayed()

        repository.cancelVisit(visit, "Projection regression")
        compose.waitUntil(10_000) { viewModel.state.value.dueServices.singleOrNull()?.claimedVisitId == null }
        compose.onNodeWithContentDescription("Visit, Has visit").performClick()
        compose.onNodeWithTag("due-visit-selector-option-novisit").performClick()
        compose.onNodeWithContentDescription("Visit, No visit").assertIsDisplayed()
        compose.onNodeWithText("P-001 · Projection service", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Refreshing due services").assertDoesNotExist()
    }
}
