package com.v16studio.serviceloop.ui

import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.InspectionQuestion
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.domain.SaveStatus
import com.v16studio.serviceloop.domain.ServiceDraftFieldKeys
import com.v16studio.serviceloop.domain.WorkSubjectType
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.ServiceScreen
import com.v16studio.serviceloop.ui.service.ServiceDraftFieldId
import com.v16studio.serviceloop.ui.service.ServiceDraftFieldState
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceAttentionOrderUiTest {
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
    fun workPerformedRawBufferWinsOverPrivateRawBuffer() {
        val draft = draft(rawInputs = raw(ServiceDraftFieldKeys.PRIVATE, ServiceDraftFieldKeys.WORK))

        assertEquals(ServiceDraftFieldKeys.WORK, resolve(draft).fieldKey)
    }

    @Test
    fun privateRawBufferWinsOverChecklistRawBuffer() {
        val draft = draft(rawInputs = raw(ServiceDraftFieldKeys.PRIVATE, ServiceDraftFieldKeys.questionValue("check")))

        assertEquals(ServiceDraftFieldKeys.PRIVATE, resolve(draft).fieldKey)
    }

    @Test
    fun checklistUsesDisplayedQuestionOrderInsteadOfQuestionIdOrder() {
        val draft = draft(
            questions = listOf(question("z-first", 1), question("a-second", 2)),
            rawInputs = raw(ServiceDraftFieldKeys.questionValue("a-second"), ServiceDraftFieldKeys.questionValue("z-first")),
        )

        assertEquals(ServiceDraftFieldKeys.questionValue("z-first"), resolve(draft).fieldKey)
    }

    @Test
    fun activeIssueFieldWinsOverInactivePreservedNaField() {
        val draft = draft(
            questions = listOf(question("check", 1, ResponseDisposition.ISSUE_FOUND)),
            rawInputs = raw(ServiceDraftFieldKeys.questionNotApplicable("check"), ServiceDraftFieldKeys.questionIssue("check")),
        )

        assertEquals(ServiceDraftFieldKeys.questionIssue("check"), resolve(draft).fieldKey)
    }

    @Test
    fun checklistRawBufferWinsOverPhotoCaption() {
        val draft = draft(
            questions = listOf(question("check", 1)),
            rawInputs = raw(ServiceDraftFieldKeys.photoCaption("photo-1"), ServiceDraftFieldKeys.questionValue("check")),
        )

        assertEquals(ServiceDraftFieldKeys.questionValue("check"), resolve(draft).fieldKey)
    }

    @Test
    fun photoCaptionWinsOverNextDueOverrideRawBuffer() {
        val draft = draft(rawInputs = raw(ServiceDraftFieldKeys.OVERRIDE_DATE, ServiceDraftFieldKeys.photoCaption("photo-1")))

        assertEquals(ServiceDraftFieldKeys.photoCaption("photo-1"), resolve(draft).fieldKey)
    }

    @Test
    fun afterFirstAttentionItemIsResolvedTheNextScreenItemIsSelected() {
        val first = draft(rawInputs = raw(ServiceDraftFieldKeys.PRIVATE, ServiceDraftFieldKeys.WORK))
        val afterWorkResolved = first.copy(rawInputs = raw(ServiceDraftFieldKeys.PRIVATE))

        assertEquals(ServiceDraftFieldKeys.WORK, resolve(first).fieldKey)
        assertEquals(ServiceDraftFieldKeys.PRIVATE, resolve(afterWorkResolved).fieldKey)
    }

    @Test
    fun failedFieldStillWinsOverAnEarlierOrdinaryRawBuffer() {
        val draft = draft(rawInputs = raw(ServiceDraftFieldKeys.WORK, ServiceDraftFieldKeys.OVERRIDE_REASON))
        val states = mapOf(
            ServiceDraftFieldId(draft.workItemId, ServiceDraftFieldKeys.OVERRIDE_REASON) to ServiceDraftFieldState.Failed("bad", "Save failed", null),
        )

        assertEquals(ServiceDraftFieldKeys.OVERRIDE_REASON, resolve(draft, states).fieldKey)
    }

    @Test
    fun currentServiceFocusUsesNextScreenOrderItemAfterFirstBufferIsResolved() {
        seedWorkingService()
        val firstDraft = draft(rawInputs = raw(ServiceDraftFieldKeys.PRIVATE, ServiceDraftFieldKeys.WORK))
        val secondDraft = firstDraft.copy(rawInputs = raw(ServiceDraftFieldKeys.PRIVATE))
        val viewModel = ServiceLoopViewModel(RoomServiceLoopRepository(database, fixedTime())) {}
        var shownDraft by mutableStateOf(firstDraft)

        compose.setContent {
            ServiceLoopTheme {
                val state by viewModel.state.collectAsState()
                ServiceScreen(
                    draft = shownDraft,
                    progress = null,
                    saveStatus = SaveStatus.Saved(1L),
                    focus = state.inspectionFocus,
                    viewModel = viewModel,
                    nav = rememberNavController(),
                )
            }
        }
        compose.waitUntil(10_000) { viewModel.state.value.completionLines.any { it.workItemId == "work-1" } }

        compose.waitUntil(10_000) { compose.onAllNodesWithTag("service-group-toggle-EQUIPMENT-equipment-1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("service-group-toggle-EQUIPMENT-equipment-1").assertIsDisplayed()
        capture("service-group-expanded.png")
        compose.onNodeWithTag("service-group-toggle-EQUIPMENT-equipment-1").performClick()
        capture("service-group-collapsed.png")
        compose.onNodeWithTag("service-group-toggle-EQUIPMENT-equipment-1").performClick()

        viewModel.focusService("work-1")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("work-performed-section").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("work-performed-section").assertIsDisplayed()
        capture("service-attention-focus.png")

        compose.runOnIdle { shownDraft = secondDraft }
        viewModel.focusService("work-1")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("private-work-note").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("private-work-note").assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "serviceloop-owner-correction")
        check(directory.exists() || directory.mkdirs())
        File(directory, name).outputStream().use { output ->
            check(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun resolve(
        draft: InspectionDraft,
        states: Map<ServiceDraftFieldId, ServiceDraftFieldState> = emptyMap(),
    ) = resolveAutomaticInspectionFocus(draft, states, completion = null)

    private fun raw(vararg keys: String) = keys.associateWith { "unfinished" }

    private fun question(id: String, position: Int, disposition: ResponseDisposition = ResponseDisposition.VALUE) = InspectionQuestion(
        responseId = "response-$id",
        snapshotItemId = id,
        position = position,
        label = "Question $id",
        responseType = "TEXT",
        unit = null,
        required = true,
        disposition = disposition,
        textValue = null,
        numberValue = null,
        reason = null,
    )

    private fun draft(
        questions: List<InspectionQuestion> = listOf(question("check", 1)),
        rawInputs: Map<String, String> = emptyMap(),
    ) = InspectionDraft(
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
        questions = questions,
        checklistComplete = false,
        requiredComplete = 0,
        requiredTotal = questions.count { it.required },
        subjectType = WorkSubjectType.EQUIPMENT,
        equipmentId = "equipment-1",
        customerName = "Customer",
        rawInputs = rawInputs,
    )

    private fun seedWorkingService() {
        runBlocking {
            val dao = database.serviceLoopDao()
            dao.insertCustomers(listOf(CustomerEntity("customer-1", "CU-1", "Customer")))
            dao.insertSites(listOf(SiteEntity("site-1", "customer-1", "ST-1", "Site", "Address", null)))
            dao.insertEquipment(listOf(EquipmentEntity("equipment-1", "site-1", "EQ-1", "TECH-1", "Machine", "Maker", "Model", "Serial", null)))
            dao.insertPlans(listOf(ServicePlanEntity("plan-1", "equipment-1", "P-1", "Service", 3, "MONTHS", "2026-09-01", "ACTIVE", "obligation-1")))
            dao.insertObligations(listOf(ServiceObligationEntity("obligation-1", "plan-1", 1, "2026-09-01", 1)))
            dao.insertVisits(listOf(WorkingVisitEntity("visit-1", "V-001", "customer-1", "site-1", "2026-09-05", "Customer", "Site", "Address", "WORKING", 1, "CU-1", "ST-1", "Business", "Technician", null, null, null, "Europe/Bucharest")))
            dao.insertWorkItems(listOf(WorkItemEntity("work-1", "visit-1", "equipment-1", "plan-1", "obligation-1", null, "Equipment", "EQ-1", "Service", "P-1", "2026-09-01", 3, "MONTHS", false, "PERFORMED", true, equipmentIdentifierSnapshot = "TECH-1", equipmentMakeSnapshot = "Maker", equipmentModelSnapshot = "Model", equipmentSerialSnapshot = "Serial")))
            dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity("work-1", "Saved work")))
            dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity("work-1", "")))
        }
    }

    private fun fixedTime() = object : BusinessTime {
        override val zoneId: ZoneId = ZoneId.of("Europe/Bucharest")
        override fun instant(): Instant = Instant.parse("2026-09-14T10:00:00Z")
    }
}
