package com.v16studio.v16service

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
import com.v16studio.v16service.data.CustomerEntity
import com.v16studio.v16service.data.RoomV16ServiceRepository
import com.v16studio.v16service.data.V16ServiceDatabase
import com.v16studio.v16service.data.SiteEntity
import com.v16studio.v16service.data.WorkItemEntity
import com.v16studio.v16service.data.WorkItemPrivateDraftEntity
import com.v16studio.v16service.data.WorkItemPublicDraftEntity
import com.v16studio.v16service.data.WorkingVisitEntity
import com.v16studio.v16service.domain.BusinessTime
import com.v16studio.v16service.ui.ServiceScreen
import com.v16studio.v16service.ui.V16ServiceViewModel
import com.v16studio.v16service.ui.theme.V16ServiceTheme
import com.v16studio.v16service.domain.SaveStatus
import com.v16studio.v16service.domain.ServiceDraftFieldKeys
import com.v16studio.v16service.ui.service.ServiceDraftFieldId
import com.v16studio.v16service.ui.service.ServiceDraftFieldState
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
        val db = Room.databaseBuilder(context, V16ServiceDatabase::class.java, dbName).build()
        try {
            runBlocking {
                val dao = db.v16ServiceDao()
                dao.insertCustomers(listOf(CustomerEntity("private-c", "CU-P", "Private note customer")))
                dao.insertSites(listOf(SiteEntity("private-s", "private-c", "ST-P", "Private note site", null, null)))
                dao.insertVisits(listOf(WorkingVisitEntity("private-v", "V-P", "private-c", "private-s", "2026-09-05", "Private note customer", "Private note site", null, "WORKING", 1)))
                dao.insertWorkItems(listOf(WorkItemEntity("private-w", "private-v", null, null, null, null, null, null, "Private note service", null, null, null, null, false, null, false, subjectType = "SITE")))
                dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity("private-w", "")))
                dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity("private-w", "")))
            }
            val repo = RoomV16ServiceRepository(db, time)
            val draft = runBlocking { repo.inspection("private-w")!! }
            val viewModel = V16ServiceViewModel(repo) {}
            compose.setContent { V16ServiceTheme { ServiceScreen(draft, null, SaveStatus.Idle, null, viewModel, rememberNavController()) } }
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
        val db = Room.databaseBuilder(context, V16ServiceDatabase::class.java, dbName).build()
        try {
            val repo = RoomV16ServiceRepository(db, time)
            runBlocking {
                assertEquals("WORKING", repo.visit("private-v")!!.state)
                assertEquals("PRIVATE_RESTART_CHECK_2B", repo.inspection("private-w")!!.privateInternalNote)
            }
            val draft = runBlocking { repo.inspection("private-w")!! }
            val viewModel = V16ServiceViewModel(repo) {}
            compose.setContent { V16ServiceTheme { ServiceScreen(draft, null, SaveStatus.Saved(draft.modifiedAtEpochMillis), null, viewModel, rememberNavController()) } }
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
