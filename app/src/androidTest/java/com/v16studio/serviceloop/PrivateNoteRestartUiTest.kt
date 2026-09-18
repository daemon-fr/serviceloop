package com.v16studio.serviceloop

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.CustomerEntity
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.data.SiteEntity
import com.v16studio.serviceloop.data.WorkItemEntity
import com.v16studio.serviceloop.data.WorkItemPrivateDraftEntity
import com.v16studio.serviceloop.data.WorkItemPublicDraftEntity
import com.v16studio.serviceloop.data.WorkingVisitEntity
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.ui.ServiceScreen
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import com.v16studio.serviceloop.domain.SaveStatus
import com.v16studio.serviceloop.domain.ServiceDraftFieldKeys
import com.v16studio.serviceloop.ui.service.ServiceDraftFieldId
import com.v16studio.serviceloop.ui.service.ServiceDraftFieldState
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run writeCheckpoint, stop the app process, then run readCheckpointInFreshProcess. */
@RunWith(AndroidJUnit4::class)
class PrivateNoteRestartUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val dbName = "private-note-restart-2b.db"
    private val time = object : BusinessTime {
        override val zoneId = ZoneId.of("Europe/Bucharest")
        override fun instant() = Instant.parse("2026-09-05T10:00:00Z")
    }

    @Test fun writeCheckpoint() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)
        val db = Room.databaseBuilder(context, ServiceLoopDatabase::class.java, dbName).build()
        try {
            runBlocking {
                val dao = db.serviceLoopDao()
                dao.insertCustomers(listOf(CustomerEntity("private-c", "CU-P", "Private note customer")))
                dao.insertSites(listOf(SiteEntity("private-s", "private-c", "ST-P", "Private note site", null, null)))
                dao.insertVisits(listOf(WorkingVisitEntity("private-v", "V-P", "private-c", "private-s", "2026-09-05", "Private note customer", "Private note site", null, "WORKING", 1)))
                dao.insertWorkItems(listOf(WorkItemEntity("private-w", "private-v", null, null, null, null, null, null, "Private note service", null, null, null, null, false, null, false, subjectType = "SITE")))
                dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity("private-w", "")))
                dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity("private-w", "")))
            }
            val repo = RoomServiceLoopRepository(db, time)
            val draft = runBlocking { repo.inspection("private-w")!! }
            val viewModel = ServiceLoopViewModel(repo) {}
            compose.setContent { ServiceLoopTheme { ServiceScreen(draft, null, SaveStatus.Idle, null, viewModel, rememberNavController()) } }
            compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("add-private-note"))
            compose.onNodeWithTag("add-private-note").performClick()
            compose.onNodeWithTag("long-text-private-note").performScrollTo().performTextInput("PRIVATE_RESTART_CHECK_2B")
            compose.waitUntil(15_000) {
                runBlocking { repo.inspection("private-w")!!.privateInternalNote == "PRIVATE_RESTART_CHECK_2B" } &&
                    viewModel.serviceDraftStates.value[ServiceDraftFieldId("private-w", ServiceDraftFieldKeys.PRIVATE)] is ServiceDraftFieldState.Clean
            }
            compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("service-save-state"))
            compose.onNodeWithTag("service-save-state").assertTextContains("Saved", substring = true)
        } finally {
            compose.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            db.close()
        }
    }

    @Test fun readCheckpointInFreshProcess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, ServiceLoopDatabase::class.java, dbName).build()
        try {
            val repo = RoomServiceLoopRepository(db, time)
            runBlocking {
                assertEquals("WORKING", repo.visit("private-v")!!.state)
                assertEquals("PRIVATE_RESTART_CHECK_2B", repo.inspection("private-w")!!.privateInternalNote)
            }
            val draft = runBlocking { repo.inspection("private-w")!! }
            val viewModel = ServiceLoopViewModel(repo) {}
            compose.setContent { ServiceLoopTheme { ServiceScreen(draft, null, SaveStatus.Saved(draft.modifiedAtEpochMillis), null, viewModel, rememberNavController()) } }
            compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("private-work-note"))
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("long-text-private-note").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("long-text-private-note").assertTextContains("PRIVATE_RESTART_CHECK_2B")
        } finally {
            compose.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            db.close()
            context.deleteDatabase(dbName)
        }
    }
}
