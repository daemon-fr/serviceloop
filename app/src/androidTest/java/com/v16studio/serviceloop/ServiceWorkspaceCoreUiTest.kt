package com.v16studio.serviceloop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.domain.CompletionBlockerKind
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.InspectionQuestion
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.domain.SaveStatus
import com.v16studio.serviceloop.domain.ServiceDocumentationMode
import com.v16studio.serviceloop.domain.ServiceEntryStatus
import com.v16studio.serviceloop.domain.ServiceProgressItem
import com.v16studio.serviceloop.domain.VisitServiceProgress
import com.v16studio.serviceloop.domain.WorkSubjectType
import com.v16studio.serviceloop.domain.serviceProgressGroups
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.InspectionFocus
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
        compose.onNodeWithTag("service-save-state").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Saved ·", substring = true).fetchSemanticsNodes().size == 1)
        assertTrue(compose.onAllNodesWithText("Saved ", substring = true).fetchSemanticsNodes().size <= 1)
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
    fun currentServiceIsSelectedWithoutNavigationAndReadyStatusIsExplicit() {
        val current = progressItem("work-1", 1, "Electrical inspection").copy(status = ServiceEntryStatus.READY)
        val other = progressItem("work-2", 2, "Mechanical inspection")
        render(draft(), progress(draft(), listOf(current, other)))
        compose.onNodeWithTag("show-services").performClick()
        compose.onNodeWithTag("service-row-work-1").assertIsSelected().assertHasNoClickAction()
        compose.onNodeWithTag("service-row-work-2").assertHasClickAction()
        compose.onNodeWithText("Ready for outcome").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("State unavailable").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Back to visit").assertIsDisplayed()
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

    @Test
    fun savedPrivateNoteIsInlineAndExpandIsExplicit() {
        render(draft().copy(privateInternalNote = "Saved private note"))

        compose.onNodeWithTag("private-work-note").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTag("long-text-private-note-expanded").fetchSemanticsNodes().isEmpty())

        compose.onNodeWithTag("long-text-private-note-expand").performClick()
        compose.onNodeWithTag("long-text-private-note-expanded").assertIsDisplayed()
    }

    @Test
    fun choiceRequiredServiceCannotOpenPartsAndPhotos() {
        assertReadOnlyParts(ServiceDocumentationMode.CHOICE_REQUIRED, "ASSIGNED", "PENDING")
    }

    @Test
    fun leaderObserveServiceCannotOpenPartsAndPhotos() {
        assertReadOnlyParts(ServiceDocumentationMode.LEADER_OBSERVE, "LEADER_VISIBLE", null)
    }

    @Test
    fun deferredServiceCannotOpenPartsAndPhotos() {
        assertReadOnlyParts(ServiceDocumentationMode.DEFERRED, "ASSIGNED", "DEFERRED")
    }

    @Test
    fun serviceToServiceNavigationReplacesDestinationAndVisitOverviewReturnsToVisit() {
        val progress = progress(
            draft(),
            listOf(
                progressItem("work-a", 1, "Service A"),
                progressItem("work-b", 2, "Service B"),
                progressItem("work-c", 3, "Service C"),
            ),
        )
        val viewModel = ServiceLoopViewModel(RoomServiceLoopRepository(database, fixedTime())) {}

        compose.setContent {
            ServiceLoopTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "visit/visit-1") {
                    composable("visit/{visitId}") {
                        androidx.compose.material3.Text("Visit overview", Modifier.testTag("visit-overview"))
                    }
                    composable("inspection/{workItemId}") { entry ->
                        val workItemId = entry.arguments?.getString("workItemId").orEmpty()
                        ServiceScreen(
                            draft = draftFor(workItemId),
                            progress = progress,
                            saveStatus = SaveStatus.Saved(1L),
                            focus = null,
                            viewModel = viewModel,
                            nav = nav,
                        )
                    }
                }
                LaunchedEffect(Unit) { nav.navigate("inspection/work-a") }
            }
        }

        compose.waitUntil(10_000) { compose.onAllNodesWithText("Service A").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("next-service").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Service B").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("next-service").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Service C").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("service-visit-overview").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("visit-overview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("visit-overview").assertIsDisplayed()
    }

    @Test
    fun privateContextDoesNotShiftFindingDeepLinkAwayFromExactQuestion() {
        val base = draft()
        val first = base.questions.single().copy(snapshotItemId = "q1", label = "First question")
        val second = base.questions.single().copy(snapshotItemId = "q2", position = 2, label = "Exact finding question")
        render(
            base.copy(siteAccessNote = "Private gate instructions", questions = listOf(first, second)),
            focus = InspectionFocus(CompletionBlockerKind.FINDING_DESCRIPTION, "q2"),
        )

        compose.waitUntil(10_000) { compose.onAllNodesWithTag("question-q2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("question-q2").assertIsDisplayed()
    }

    private fun render(draft: InspectionDraft, progress: VisitServiceProgress? = null, focus: InspectionFocus? = null) {
        val viewModel = ServiceLoopViewModel(RoomServiceLoopRepository(database, fixedTime())) {}
        compose.setContent {
            ServiceLoopTheme {
                ServiceScreen(draft, progress, SaveStatus.Saved(draft.modifiedAtEpochMillis), focus, viewModel, rememberNavController())
            }
        }
    }

    private fun assertReadOnlyParts(mode: ServiceDocumentationMode, localRole: String?, disposition: String?) {
        val currentDraft = draft()
        render(currentDraft, progress(currentDraft, listOf(progressItem(currentDraft.workItemId, 1, currentDraft.serviceName, mode, localRole, disposition))))
        compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("open-field-evidence"))
        compose.onNodeWithTag("open-field-evidence").assertIsNotEnabled()
    }

    private fun progress(draft: InspectionDraft, items: List<ServiceProgressItem>) = VisitServiceProgress(
        visitId = draft.visitId,
        visitReference = draft.visitReference,
        customerName = draft.customerName,
        siteName = draft.siteName,
        serviceDate = draft.dueDate ?: "2026-09-14",
        items = items,
        groups = serviceProgressGroups(items),
    )

    private fun progressItem(
        workItemId: String,
        position: Int,
        serviceName: String,
        mode: ServiceDocumentationMode = ServiceDocumentationMode.LOCAL,
        localRole: String? = null,
        disposition: String? = null,
    ) = ServiceProgressItem(
        workItemId = workItemId,
        position = position,
        subjectType = WorkSubjectType.EQUIPMENT,
        equipmentId = "equipment-1",
        equipmentName = "Machine",
        equipmentReference = "EQ-001",
        equipmentDescription = null,
        serviceName = serviceName,
        status = ServiceEntryStatus.NOT_STARTED,
        documentationMode = mode,
        dispatchLocalRole = localRole,
        dispatchDocumentationDisposition = disposition,
    )

    private fun draftFor(workItemId: String) = draft().copy(
        workItemId = workItemId,
        serviceName = when (workItemId) {
            "work-a" -> "Service A"
            "work-b" -> "Service B"
            else -> "Service C"
        },
    )

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
