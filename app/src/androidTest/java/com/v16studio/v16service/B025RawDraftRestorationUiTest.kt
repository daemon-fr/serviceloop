package com.v16studio.v16service

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.v16service.data.RoomV16ServiceRepository
import com.v16studio.v16service.data.V16ServiceDatabase
import com.v16studio.v16service.domain.BusinessTime
import com.v16studio.v16service.domain.InspectionDraft
import com.v16studio.v16service.domain.InspectionQuestion
import com.v16studio.v16service.domain.ResponseDisposition
import com.v16studio.v16service.domain.SaveStatus
import com.v16studio.v16service.domain.ServiceDraftFieldKeys
import com.v16studio.v16service.ui.InspectionScreen
import com.v16studio.v16service.ui.V16ServiceViewModel
import com.v16studio.v16service.ui.theme.V16ServiceTheme
import java.time.Instant
import java.time.ZoneId
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class B025RawDraftRestorationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: V16ServiceDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            V16ServiceDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        compose.runOnUiThread { compose.activity.setContent {} }
        compose.waitForIdle()
        database.close()
    }

    @Test
    fun recoveredWorkInputIsVisibleOverCanonicalWork() {
        render(
            draft(
                rawInputs = mapOf(ServiceDraftFieldKeys.WORK to "Recovered work edit"),
            ),
        )

        compose.onNodeWithTag("long-text-public-work-performed", useUnmergedTree = true)
            .assertTextContains("Recovered work edit")
    }

    @Test
    fun recoveredInvalidNumberIsVisibleOverCanonicalNumber() {
        val question = InspectionQuestion(
            responseId = "response-number",
            snapshotItemId = "q-number",
            position = 1,
            label = "Reading",
            responseType = "NUMBER",
            unit = "mm",
            required = true,
            disposition = ResponseDisposition.VALUE,
            textValue = null,
            numberValue = "10.0",
            reason = null,
        )
        render(
            draft(
                questions = listOf(question),
                rawInputs = mapOf(ServiceDraftFieldKeys.questionValue("q-number") to "12."),
            ),
        )

        compose.onNodeWithTag("inspection-list").performScrollToNode(hasTestTag("value-q-number"))
        compose.onNodeWithTag("value-q-number", useUnmergedTree = true).assertTextEquals("12.")
    }

    @Test
    fun recoveredIssueTextIsVisibleOverCanonicalFinding() {
        val question = InspectionQuestion(
            responseId = "response-issue",
            snapshotItemId = "q-issue",
            position = 1,
            label = "Guard",
            responseType = "STATUS",
            unit = null,
            required = true,
            disposition = ResponseDisposition.ISSUE_FOUND,
            textValue = null,
            numberValue = null,
            reason = "Old finding",
        )
        render(
            draft(
                questions = listOf(question),
                rawInputs = mapOf(ServiceDraftFieldKeys.questionIssue("q-issue") to "Recovered finding edit"),
            ),
        )

        compose.onNodeWithTag("inspection-list").performScrollToNode(hasTestTag("long-text-public-finding-description"))
        compose.onNodeWithTag("long-text-public-finding-description", useUnmergedTree = true)
            .assertTextContains("Recovered finding edit")
    }

    @Test
    fun recoveredNotApplicableTextIsVisibleOverCanonicalReason() {
        val question = InspectionQuestion(
            responseId = "response-na",
            snapshotItemId = "q-na",
            position = 1,
            label = "Access",
            responseType = "STATUS",
            unit = null,
            required = true,
            disposition = ResponseDisposition.NOT_APPLICABLE,
            textValue = null,
            numberValue = null,
            reason = "Old reason",
        )
        render(
            draft(
                questions = listOf(question),
                rawInputs = mapOf(ServiceDraftFieldKeys.questionNotApplicable("q-na") to "Recovered N/A edit"),
            ),
        )

        compose.onNodeWithTag("inspection-list").performScrollToNode(hasTestTag("not-applicable-reason-q-na"))
        compose.onNodeWithTag("not-applicable-reason-q-na")
            .assertTextContains("Recovered N/A edit")
    }

    private fun render(draft: InspectionDraft) {
        val repository = RoomV16ServiceRepository(database, fixedTime())
        val viewModel = V16ServiceViewModel(repository) {}
        compose.setContent {
            V16ServiceTheme {
                InspectionScreen(draft, SaveStatus.Saved(1L), null, viewModel, rememberNavController())
            }
        }
    }

    private fun draft(
        questions: List<InspectionQuestion> = emptyList(),
        rawInputs: Map<String, String> = emptyMap(),
    ) = InspectionDraft(
        workItemId = "work-1",
        visitId = "visit-1",
        visitReference = "V-001",
        siteName = "Site",
        equipmentName = "Machine",
        equipmentReference = "EQ-001",
        serviceName = "Service",
        dueDate = "2026-09-01",
        interval = "Every 3 months",
        templateRevision = 1,
        workPerformed = "Old work",
        privateInternalNote = "",
        checklistReviewed = false,
        outcome = "PERFORMED",
        fulfillsCurrentObligation = false,
        modifiedAtEpochMillis = 1L,
        questions = questions,
        checklistComplete = false,
        requiredComplete = 0,
        requiredTotal = questions.count { it.required },
        rawInputs = rawInputs,
    )

    private fun fixedTime() = object : BusinessTime {
        override val zoneId: ZoneId = ZoneId.of("Europe/Bucharest")
        override fun instant(): Instant = Instant.parse("2026-09-13T10:00:00Z")
    }
}
