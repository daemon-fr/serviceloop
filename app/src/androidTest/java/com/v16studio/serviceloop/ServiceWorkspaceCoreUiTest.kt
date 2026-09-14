package com.v16studio.serviceloop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.InspectionQuestion
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.domain.SaveStatus
import com.v16studio.serviceloop.domain.ServiceDocumentationMode
import com.v16studio.serviceloop.domain.ServiceEntryStatus
import com.v16studio.serviceloop.domain.ServiceProgressItem
import com.v16studio.serviceloop.domain.VisitServiceProgress
import com.v16studio.serviceloop.domain.WorkSubjectType
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.ServiceScreen
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import java.time.Instant
import java.time.ZoneId
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class ServiceWorkspaceCoreUiTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var database: ServiceLoopDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ServiceLoopDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun serviceWorkspaceShowsProgressWorkChecklistAndAutosaveLanguage() {
        render(draft())

        compose.onNodeWithTag("service-list").assertIsDisplayed()
        compose.onNodeWithTag("service-identity").assertIsDisplayed()
        compose.onNodeWithTag("visit-progress").assertIsDisplayed()
        compose.onNodeWithTag("work-performed-section").assertIsDisplayed()
        compose.onNodeWithTag("add-private-note").assertIsDisplayed()
        compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("open-field-evidence"))
        compose.onNodeWithTag("open-field-evidence").assertIsDisplayed()
        compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("response-q-status-OK"))
        compose.onNodeWithTag("response-q-status-OK").assertIsDisplayed()
        compose.onNodeWithTag("response-q-status-ISSUE_FOUND").assertIsDisplayed()
        compose.onNodeWithTag("response-q-status-NOT_APPLICABLE").assertIsDisplayed()
        compose.onNodeWithTag("response-q-status-NOT_CHECKED").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Save work performed", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Save response", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun pendingDispatchServiceIsReadOnlyAndPointsToVisitAssignment() {
        val draft = draft()
        val progress = VisitServiceProgress(
            visitId = draft.visitId,
            visitReference = draft.visitReference,
            customerName = draft.customerName,
            siteName = draft.siteName,
            serviceDate = "2026-09-14",
            items = listOf(ServiceProgressItem(
                draft.workItemId, 1, draft.subjectType, draft.equipmentId, draft.equipmentName,
                draft.equipmentReference, draft.equipmentDescription, draft.serviceName,
                ServiceEntryStatus.NOT_STARTED, ServiceDocumentationMode.CHOICE_REQUIRED,
                dispatchLocalRole = "ASSIGNED", dispatchDocumentationDisposition = "PENDING",
            )),
            groups = emptyList(),
        )
        render(draft, progress)

        compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("open-visit-assignment"))
        compose.onNodeWithTag("open-visit-assignment").assertIsDisplayed()
        compose.onNodeWithTag("long-text-public-work-performed", useUnmergedTree = true).assertIsNotEnabled()
        assertTrue(compose.onAllNodesWithTag("add-private-note").fetchSemanticsNodes().isEmpty())
    }

    private fun render(draft: InspectionDraft, progress: VisitServiceProgress? = null) {
        val viewModel = ServiceLoopViewModel(RoomServiceLoopRepository(database, fixedTime())) {}
        compose.setContent {
            ServiceLoopTheme {
                ServiceScreen(draft, progress, SaveStatus.Saved(draft.modifiedAtEpochMillis), null, viewModel, rememberNavController())
            }
        }
    }

    private fun draft() = InspectionDraft(
        workItemId = "work-1",
        visitId = "visit-1",
        visitReference = "V-001",
        siteName = "Site",
        equipmentName = "Machine",
        equipmentReference = "EQ-001",
        serviceName = "Service",
        dueDate = "2026-09-14",
        interval = null,
        templateRevision = 1,
        workPerformed = "",
        privateInternalNote = "",
        checklistReviewed = false,
        outcome = null,
        fulfillsCurrentObligation = null,
        modifiedAtEpochMillis = 1L,
        questions = listOf(InspectionQuestion(
            responseId = "response-status",
            snapshotItemId = "q-status",
            position = 1,
            label = "Safety guard",
            responseType = "STATUS",
            unit = null,
            required = true,
            disposition = ResponseDisposition.UNANSWERED,
            textValue = null,
            numberValue = null,
            reason = null,
        )),
        checklistComplete = false,
        requiredComplete = 0,
        requiredTotal = 1,
        subjectType = WorkSubjectType.EQUIPMENT,
        equipmentId = "equipment-1",
        customerName = "Customer",
    )

    private fun fixedTime() = object : BusinessTime {
        override val zoneId: ZoneId = ZoneId.of("Europe/Bucharest")
        override fun instant(): Instant = Instant.parse("2026-09-14T10:00:00Z")
    }
}
