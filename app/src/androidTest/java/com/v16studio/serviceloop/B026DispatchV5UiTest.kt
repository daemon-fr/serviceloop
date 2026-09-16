package com.v16studio.serviceloop

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v16studio.serviceloop.data.CustomerEntity
import com.v16studio.serviceloop.data.DispatchPackageService
import com.v16studio.serviceloop.data.EquipmentEntity
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.data.SiteEntity
import com.v16studio.serviceloop.domain.CustomerType
import com.v16studio.serviceloop.domain.WorkSubjectType
import com.v16studio.serviceloop.ui.DispatchSitePickerDialog
import com.v16studio.serviceloop.ui.DispatchVisitEditorScreen
import com.v16studio.serviceloop.ui.LocalDetailBackInterceptor
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class B026DispatchV5UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: ServiceLoopDatabase
    private lateinit var service: DispatchPackageService
    private lateinit var teamId: String

    @Before fun setup() {
        val context = compose.activity
        db = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        service = DispatchPackageService(db)
        runBlocking {
            val dao = db.serviceLoopDao()
            dao.insertCustomers(
                listOf(
                    CustomerEntity("standard-customer", "CU-STD", "Standard customer"),
                    CustomerEntity("one-time-customer", "CU-OT", "One-time customer", customerType = CustomerType.ONE_TIME.code),
                ),
            )
            dao.insertSites(
                listOf(
                    SiteEntity("standard-site", "standard-customer", "ST-STD", "Standard site", "1 Standard Road", null, isDefault = true),
                    SiteEntity("one-time-site", "one-time-customer", "ST-OT", "One-time site", "2 One-time Road", null, isDefault = true),
                ),
            )
            dao.insertEquipment(listOf(EquipmentEntity("one-time-equipment", "one-time-site", "EQ-OT", null, "Registered one-time equipment", null, null, null, null)))
            val identity = service.identity()
            service.importTechnician(identity)
            teamId = service.createTeam("Dispatch team")
            service.setTeamMember(teamId, identity.technicianId, present = true, isLeader = true)
        }
    }

    @After fun close() { db.close() }

    @Test fun sitePickerHidesOneTimeUntilMatchingSearch() {
        val sites = runBlocking { db.serviceLoopDao().allSites() }
        val customers = runBlocking { db.serviceLoopDao().allCustomers() }
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme(false) { DispatchSitePickerDialog(sites, customers, {}, {}) }
            }
        }
        compose.onNodeWithTag("dispatch-site-standard-site").assertIsDisplayed()
        compose.onNodeWithTag("dispatch-site-one-time-site").assertDoesNotExist()
        compose.onNodeWithTag("dispatch-site-search").performTextInput("One-time")
        compose.onNodeWithTag("dispatch-site-one-time-site").assertIsDisplayed()
        compose.onNodeWithTag("dispatch-site-one-time-site").assertIsDisplayed()
    }

    @Test fun newOneTimeEditorSavesSiteAndUnidentifiedWorkAtomically() {
        renderNewEditor()
        compose.onNodeWithTag("dispatch-mode-ONE_TIME").performClick()
        compose.onNodeWithTag("dispatch-new-visit").performScrollToNode(hasTestTag("dispatch-one-time-customer-name"))
        compose.onNodeWithTag("dispatch-one-time-customer-name").performTextInput("Walk-in customer")
        compose.onNodeWithTag("dispatch-new-visit").performScrollToNode(hasTestTag("dispatch-choose-teams"))
        compose.onNodeWithTag("dispatch-choose-teams").performClick()
        compose.onNodeWithTag("dispatch-team-$teamId").performClick()
        compose.onNodeWithTag("dispatch-team-apply").performClick()

        compose.onNodeWithTag("dispatch-new-visit").performScrollToNode(hasTestTag("dispatch-add-item"))
        compose.onNodeWithTag("dispatch-add-item").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("dispatch-subject-SITE").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("dispatch-subject-SITE").performClick()
        compose.onNodeWithTag("dispatch-work-task").performTextInput("Inspect location")
        compose.onNodeWithTag("dispatch-work-item-save").performClick()

        compose.onNodeWithTag("dispatch-new-visit").performScrollToNode(hasTestTag("dispatch-add-item"))
        compose.onNodeWithTag("dispatch-add-item").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("dispatch-subject-EQUIPMENT").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("dispatch-subject-EQUIPMENT").performClick()
        compose.onNodeWithTag("dispatch-equipment-description").performTextInput("Unit beside the gate")
        compose.onNodeWithTag("dispatch-work-task").performTextInput("Identify unit")
        compose.onNodeWithTag("dispatch-work-item-save").performClick()
        compose.onNodeWithTag("dispatch-save-visit").performClick()

        compose.waitUntil(10_000) { runBlocking { db.dispatchDao().outboxVisits().size == 1 } }
        val outbox = runBlocking { db.dispatchDao().outboxVisits().single() }
        val site = runBlocking { db.serviceLoopDao().site(outbox.siteId)!! }
        val customer = runBlocking { db.serviceLoopDao().customer(site.customerId)!! }
        assertEquals(CustomerType.ONE_TIME.code, customer.customerType)
        assertEquals(listOf(WorkSubjectType.SITE.code, WorkSubjectType.EQUIPMENT.code), runBlocking { db.dispatchDao().outboxItems(outbox.dispatchVisitId).map { it.subjectType } })
        assertTrue(runBlocking { db.serviceLoopDao().allEquipment().none { it.siteId == site.id } })
    }

    @Test fun existingOneTimeDispatchEditorCanAuthorKnownEquipment() {
        val visitId = runBlocking {
            service.saveOutboxVisit(
                com.v16studio.serviceloop.data.DispatchOutboxEditorDraft(
                    managerReference = "JOB-OT",
                    siteId = "one-time-site",
                    serviceDate = "2026-09-25",
                    appointmentLocalTime = "09:00",
                    appointmentZoneId = "Europe/Bucharest",
                    teamIds = listOf(teamId),
                    items = emptyList(),
                ),
            )
        }
        renderEditor(visitId)
        compose.onNodeWithTag("dispatch-new-visit").assertDoesNotExist()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("One-time customer", substring = false, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("dispatch-choose-site").performClick()
        compose.onNodeWithTag("dispatch-site-search").performTextInput("One-time")
        hideKeyboard()
        compose.onNodeWithTag("dispatch-site-picker-list").performScrollToNode(hasTestTag("dispatch-site-one-time-site"))
        compose.onNodeWithTag("dispatch-site-one-time-site").assertIsDisplayed().performClick()
        compose.onNodeWithTag("dispatch-visit-editor").performScrollToNode(hasTestTag("dispatch-add-item"))
        compose.onNodeWithTag("dispatch-add-item").performClick()
        compose.onNodeWithTag("dispatch-work-item-editor", useUnmergedTree = true).performScrollToNode(hasTestTag("dispatch-equipment-one-time-equipment"))
        compose.onNodeWithTag("dispatch-equipment-one-time-equipment", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.onNodeWithTag("dispatch-work-task").performTextInput("Inspect registered unit")
        compose.onNodeWithTag("dispatch-work-item-save").performClick()
        compose.onNodeWithTag("dispatch-save-visit").performClick()
        compose.waitUntil(10_000) { runBlocking { db.dispatchDao().outboxItems(visitId).size == 1 } }
        assertEquals("one-time-equipment", runBlocking { db.dispatchDao().outboxItems(visitId).single().equipmentId })
    }

    private fun renderNewEditor() = renderEditor(null)

    private fun renderEditor(visitId: String?) {
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme(false) {
                    val nav = rememberNavController()
                    CompositionLocalProvider(LocalDetailBackInterceptor provides remember { mutableStateOf<(() -> Unit)?>(null) }) {
                        DispatchVisitEditorScreen(PaddingValues(), nav, visitId, LocalDate.of(2026, 9, 13), service, db)
                    }
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(if (visitId == null) "dispatch-new-visit" else "dispatch-visit-editor").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun hideKeyboard() {
        compose.runOnUiThread {
            val manager = compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            manager.hideSoftInputFromWindow(compose.activity.currentFocus?.windowToken, 0)
        }
        compose.waitForIdle()
    }
}
