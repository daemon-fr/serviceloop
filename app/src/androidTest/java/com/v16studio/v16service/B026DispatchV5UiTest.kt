package com.v16studio.v16service

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
import com.v16studio.v16service.data.CustomerEntity
import com.v16studio.v16service.data.DispatchPackageService
import com.v16studio.v16service.data.EquipmentEntity
import com.v16studio.v16service.data.ReusableTemplateEntity
import com.v16studio.v16service.data.ReusableTemplateRevisionEntity
import com.v16studio.v16service.data.V16ServiceDatabase
import com.v16studio.v16service.data.SiteEntity
import com.v16studio.v16service.domain.CustomerType
import com.v16studio.v16service.domain.WorkSubjectType
import com.v16studio.v16service.domain.TemplateSummary
import com.v16studio.v16service.ui.DispatchSitePickerDialog
import com.v16studio.v16service.ui.DispatchVisitEditorScreen
import com.v16studio.v16service.ui.LocalDetailBackInterceptor
import com.v16studio.v16service.ui.theme.V16ServiceTheme
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
    private lateinit var db: V16ServiceDatabase
    private lateinit var service: DispatchPackageService
    private lateinit var teamId: String

    @Before fun setup() {
        val context = compose.activity
        db = Room.inMemoryDatabaseBuilder(context, V16ServiceDatabase::class.java).allowMainThreadQueries().build()
        service = DispatchPackageService(db)
        runBlocking {
            val dao = db.v16ServiceDao()
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
            dao.insertReusableTemplate(ReusableTemplateEntity("dispatch-template", "IT-DISPATCH", "Dispatch checklist", "dispatch-template-revision", "ACTIVE", 1))
            dao.insertReusableTemplateRevision(ReusableTemplateRevisionEntity("dispatch-template-revision", "dispatch-template", 1, "Dispatch checklist", 1))
            val identity = service.identity()
            service.importTechnician(identity)
            teamId = service.createTeam("Dispatch team")
            service.setTeamMember(teamId, identity.technicianId, present = true, isLeader = true)
        }
    }

    @After fun close() {
        compose.runOnUiThread { compose.activity.setContent {} }
        compose.waitForIdle()
        db.close()
    }

    @Test fun sitePickerHidesOneTimeUntilMatchingSearch() {
        val sites = runBlocking { db.v16ServiceDao().allSites() }
        val customers = runBlocking { db.v16ServiceDao().allCustomers() }
        compose.runOnUiThread {
            compose.activity.setContent {
                V16ServiceTheme(false) { DispatchSitePickerDialog(sites, customers, {}, {}) }
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
        compose.onNodeWithTag("visit-mode-NEW").performClick()
        compose.onNodeWithTag("dispatch-new-visit").performScrollToNode(hasTestTag("field-customer-name-required"))
        compose.onNodeWithTag("field-customer-name-required").performTextInput("Walk-in customer")
        compose.onNodeWithTag("field-site-name-required").performTextInput("Walk-in site")
        compose.onNodeWithTag("customer-creation-one-time-checkbox").performClick()
        compose.onNodeWithTag("dispatch-new-visit").performScrollToNode(hasTestTag("dispatch-choose-teams"))
        compose.onNodeWithTag("dispatch-choose-teams").performClick()
        compose.onNodeWithTag("dispatch-team-$teamId").performClick()
        compose.onNodeWithTag("dispatch-team-apply").performClick()

        compose.onNodeWithTag("dispatch-new-visit").performScrollToNode(hasTestTag("field-task-name-required"))
        compose.onNodeWithTag("field-task-name-required").performTextInput("Inspect location")
        compose.onNodeWithTag("task-template-selector").performClick()
        compose.onNodeWithText("Dispatch checklist (v1)", substring = false).performClick()
        compose.onNodeWithTag("add-task").performClick()

        compose.onNodeWithTag("dispatch-new-visit").performScrollToNode(hasTestTag("field-task-name-required"))
        compose.onNodeWithTag("task-subject-EQUIPMENT").performClick()
        compose.onNodeWithTag("field-equipment-description-optional").performTextInput("Unit beside the gate")
        compose.onNodeWithTag("field-task-name-required").performTextInput("Identify unit")
        compose.onNodeWithTag("task-template-selector").performClick()
        compose.onNodeWithText("Dispatch checklist (v1)", substring = false).performClick()
        compose.onNodeWithTag("add-task").performClick()
        compose.onNodeWithTag("dispatch-new-visit").performScrollToNode(hasTestTag("dispatch-save-visit"))
        compose.onNodeWithTag("dispatch-save-visit").performClick()

        compose.waitUntil(10_000) { runBlocking { db.dispatchDao().outboxVisits().size == 1 } }
        val outbox = runBlocking { db.dispatchDao().outboxVisits().single() }
        val site = runBlocking { db.v16ServiceDao().site(outbox.siteId)!! }
        val customer = runBlocking { db.v16ServiceDao().customer(site.customerId)!! }
        assertEquals(CustomerType.ONE_TIME.code, customer.customerType)
        assertEquals(listOf(WorkSubjectType.SITE.code, WorkSubjectType.EQUIPMENT.code), runBlocking { db.dispatchDao().outboxItems(outbox.dispatchVisitId).map { it.subjectType } })
        assertTrue(runBlocking { db.v16ServiceDao().allEquipment().none { it.siteId == site.id } })
    }

    @Test fun existingOneTimeDispatchEditorCanAuthorKnownEquipment() {
        val visitId = runBlocking {
            service.createOutboxVisit("JOB-OT", "one-time-site", "2026-09-25", "09:00", "Europe/Bucharest", null, listOf(teamId))
        }
        renderEditor(visitId)
        compose.onNodeWithTag("dispatch-visit-editor").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("One-time customers use ad-hoc work.", substring = false, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("dispatch-visit-editor").performScrollToNode(hasTestTag("field-task-name-required"))
        compose.onNodeWithTag("task-subject-EQUIPMENT").performClick()
        compose.onNodeWithTag("task-equipment-one-time-equipment").assertIsDisplayed().performClick()
        compose.onNodeWithTag("field-task-name-required").performTextInput("Inspect registered unit")
        compose.onNodeWithTag("task-template-selector").performClick()
        compose.onNodeWithText("Dispatch checklist (v1)", substring = false).performClick()
        compose.onNodeWithTag("update-task").assertDoesNotExist()
        compose.onNodeWithTag("add-task").performClick()
        compose.onNodeWithTag("dispatch-visit-editor").performScrollToNode(hasTestTag("dispatch-save-visit"))
        compose.onNodeWithTag("dispatch-save-visit").performClick()
        compose.waitUntil(10_000) { runBlocking { db.dispatchDao().outboxItems(visitId).size == 1 } }
        assertEquals("one-time-equipment", runBlocking { db.dispatchDao().outboxItems(visitId).single().equipmentId })
    }

    private fun renderNewEditor() = renderEditor(null)

    private fun renderEditor(visitId: String?) {
        compose.runOnUiThread {
            compose.activity.setContent {
                V16ServiceTheme(false) {
                    val nav = rememberNavController()
                    CompositionLocalProvider(LocalDetailBackInterceptor provides remember { mutableStateOf<(() -> Unit)?>(null) }) {
                        DispatchVisitEditorScreen(PaddingValues(), nav, visitId, LocalDate.of(2026, 9, 13), service, db, templatesOverride = listOf(TemplateSummary("dispatch-template", "IT-DISPATCH", "Dispatch checklist", 1, 1, "ACTIVE")))
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
