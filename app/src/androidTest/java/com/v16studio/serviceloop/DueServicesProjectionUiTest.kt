package com.v16studio.serviceloop

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.domain.CustomerInput
import com.v16studio.serviceloop.domain.EquipmentInput
import com.v16studio.serviceloop.domain.PlanInput
import com.v16studio.serviceloop.domain.SiteInput
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
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
    private lateinit var database: ServiceLoopDatabase

    @After fun close() { if (::database.isInitialized) database.close() }

    @Test fun roomProjectionStaysAvailableAcrossNavigationAndUpdatesClaims() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        val repository = RoomServiceLoopRepository(database, time, attachmentRoot = context.filesDir)
        val customer = repository.createCustomer(CustomerInput("Projection customer"))
        val site = repository.createSite(customer, SiteInput("Projection site", "1 Test Street"))
        val equipment = repository.createEquipment(site, EquipmentInput("Projection machine"))
        val plan = repository.createPlan(equipment, PlanInput("Projection service", 1, "YEARS", "2026-09-01"))
        val viewModel = ServiceLoopViewModel(repository) {}
        compose.runOnUiThread { compose.activity.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel) } } }
        compose.waitUntil(10_000) { viewModel.state.value.dueServicesReady && viewModel.state.value.dueServices.size == 1 }

        repeat(8) { index ->
            compose.onNodeWithText("Work").performClick()
            compose.onNodeWithText("P-001 · Projection service", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Refreshing due services").assertDoesNotExist()
            compose.onNodeWithText(if (index % 2 == 0) "Home" else "Customers").performClick()
        }

        val visit = repository.createVisit(listOf(plan), "BOOKED", "2026-09-12")
        compose.onNodeWithText("Work").performClick()
        compose.waitUntil(10_000) { viewModel.state.value.dueServices.singleOrNull()?.claimedVisitId == visit }
        compose.onNodeWithText("Already in visit · Open existing").assertIsDisplayed()
        assertEquals(plan, viewModel.state.value.dueServices.single().planId)

        repository.cancelVisit(visit, "Projection regression")
        compose.waitUntil(10_000) { viewModel.state.value.dueServices.singleOrNull()?.claimedVisitId == null }
        compose.onNodeWithText("P-001 · Projection service", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Refreshing due services").assertDoesNotExist()
    }
}
