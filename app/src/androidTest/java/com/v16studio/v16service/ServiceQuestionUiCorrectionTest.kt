package com.v16studio.v16service

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
import com.v16studio.v16service.ui.V16ServiceViewModel
import com.v16studio.v16service.ui.ServiceScreen
import com.v16studio.v16service.ui.theme.V16ServiceTheme
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceQuestionUiCorrectionTest {
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
    fun resolvedCheckDoesNotMoveFollowingContentForStatusTextOrNumber() {
        val cases = listOf(
            QuestionCase(
                id = "status",
                type = "STATUS",
                unresolved = ResponseDisposition.NOT_CHECKED,
                resolved = ResponseDisposition.OK,
                resolvedText = null,
                resolvedNumber = null,
                followingTag = "response-grid-status",
            ),
            QuestionCase(
                id = "text",
                type = "TEXT",
                unresolved = ResponseDisposition.UNANSWERED,
                resolved = ResponseDisposition.VALUE,
                resolvedText = "Recorded observation",
                resolvedNumber = null,
                followingTag = "value-text",
            ),
            QuestionCase(
                id = "number",
                type = "NUMBER",
                unresolved = ResponseDisposition.UNANSWERED,
                resolved = ResponseDisposition.VALUE,
                resolvedText = null,
                resolvedNumber = "12.5",
                followingTag = "value-number",
            ),
        )
        val draftState = mutableStateOf(draft(cases.map { it.question(it.unresolved) }))
        renderDraft(draftState)
        val unresolved = cases.associate { testCase ->
            scrollToQuestion(testCase.id)
            testCase.id to followingBounds(testCase)
        }
        compose.runOnIdle { draftState.value = draft(cases.map { it.question(it.resolved) }) }
        compose.waitForIdle()
        cases.forEach { testCase ->
            scrollToQuestion(testCase.id)
            val resolved = followingBounds(testCase)
            val unresolvedBounds = unresolved.getValue(testCase.id)

            assertEquals(
                "${testCase.type} metadata moved when the resolved check appeared",
                unresolvedBounds.metadataTop,
                resolved.metadataTop,
                1f,
            )
            assertEquals(
                "${testCase.type} content moved when the resolved check appeared",
                unresolvedBounds.followingTop,
                resolved.followingTop,
                1f,
            )
            assertEquals(
                "${testCase.type} private guidance moved when the resolved check appeared",
                unresolvedBounds.privateGuidanceTop,
                resolved.privateGuidanceTop,
                1f,
            )
            compose.onAllNodesWithTag("question-${testCase.id}-complete", useUnmergedTree = true).assertCountEquals(1)
        }
    }

    @Test
    fun wrappedTitleAt320DpAndFontScale2KeepsFollowingContentStable() {
        val testCase = QuestionCase(
            id = "wrapped",
            type = "STATUS",
            unresolved = ResponseDisposition.NOT_CHECKED,
            resolved = ResponseDisposition.OK,
            resolvedText = null,
            resolvedNumber = null,
            followingTag = "response-grid-wrapped",
            label = "Inspect the full drive assembly, guards, belt edges, tension, alignment, fasteners, and nearby cable routing for defects",
        )
        val questionState = mutableStateOf(draft(testCase.question(testCase.unresolved)))
        renderDraft(questionState, widthDp = 320, fontScale = 2f)
        scrollToQuestion(testCase.id)
        val titleBefore = bounds(testCase.id, "title")
        val before = followingBounds(testCase)

        compose.runOnIdle { questionState.value = draft(testCase.question(testCase.resolved)) }
        compose.waitForIdle()
        scrollToQuestion(testCase.id)
        val after = followingBounds(testCase)

        assertTrue("the long question title should still wrap", titleBefore.height > 48f)
        assertEquals(before.metadataTop, after.metadataTop, 1f)
        assertEquals(before.followingTop, after.followingTop, 1f)
        assertEquals(before.privateGuidanceTop, after.privateGuidanceTop, 1f)
    }

    @Test
    fun optionalTextHasOnlyResponseEditorWhileRequiredTextAndNumberKeepNa() {
        val questions = listOf(
            QuestionCase("optional-text", "TEXT", ResponseDisposition.UNANSWERED, ResponseDisposition.VALUE, "Observed", null, "value-optional-text").question(ResponseDisposition.UNANSWERED),
            QuestionCase("required-text", "TEXT", ResponseDisposition.UNANSWERED, ResponseDisposition.VALUE, "Recorded", null, "value-required-text").question(ResponseDisposition.UNANSWERED).copy(required = true),
            QuestionCase("optional-number", "NUMBER", ResponseDisposition.UNANSWERED, ResponseDisposition.VALUE, null, "12.5", "value-optional-number").question(ResponseDisposition.UNANSWERED),
        )
        val draftState = mutableStateOf(draft(questions))
        renderDraft(draftState)

        scrollToQuestion("optional-text")
        compose.onNodeWithTag("value-optional-text").assertIsDisplayed()
        compose.onAllNodesWithTag("not-applicable-optional-text", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithTag("not-applicable-reason-optional-text", useUnmergedTree = true).assertCountEquals(0)

        scrollToQuestion("required-text")
        compose.onNodeWithTag("not-applicable-required-text", useUnmergedTree = true).assertIsDisplayed()
        scrollToQuestion("optional-number")
        compose.onNodeWithTag("not-applicable-optional-number", useUnmergedTree = true).assertIsDisplayed()

        val populated = questions.first { it.snapshotItemId == "optional-text" }.copy(
            disposition = ResponseDisposition.VALUE,
            textValue = "Observed",
        )
        compose.runOnIdle { draftState.value = draftState.value.copy(questions = listOf(populated) + questions.drop(1)) }
        compose.waitForIdle()
        scrollToQuestion("optional-text")
        compose.onNodeWithTag("value-optional-text").assertTextContains("Observed")
        compose.onAllNodesWithTag("not-applicable-optional-text", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun persistedOptionalTextNaKeepsEditorButDoesNotExposeNaControls() {
        val question = QuestionCase(
            id = "legacy-optional-text",
            type = "TEXT",
            unresolved = ResponseDisposition.NOT_APPLICABLE,
            resolved = ResponseDisposition.NOT_APPLICABLE,
            resolvedText = null,
            resolvedNumber = null,
            followingTag = "value-legacy-optional-text",
        ).question(ResponseDisposition.NOT_APPLICABLE).copy(
            reason = "Technician recorded no access",
            notApplicableReasonDraft = "Technician recorded no access",
        )
        val draftState = mutableStateOf(draft(question))
        renderDraft(draftState)
        scrollToQuestion(question.snapshotItemId)

        compose.onNodeWithTag("value-legacy-optional-text").assertIsDisplayed()
        compose.onAllNodesWithTag("not-applicable-legacy-optional-text", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithTag("not-applicable-reason-legacy-optional-text", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun representativeInspectionCardsRenderInBothThemesAtNarrowLargeText() {
        val cases = listOf(
            QuestionCase("render-status", "STATUS", ResponseDisposition.NOT_CHECKED, ResponseDisposition.OK, null, null, "response-grid-render-status"),
            QuestionCase("render-optional-text", "TEXT", ResponseDisposition.UNANSWERED, ResponseDisposition.VALUE, "Observed", null, "value-render-optional-text"),
            QuestionCase("render-required-text", "TEXT", ResponseDisposition.NOT_APPLICABLE, ResponseDisposition.NOT_APPLICABLE, null, null, "value-render-required-text"),
        )
        val draftState = mutableStateOf(draft(cases.map { testCase ->
            testCase.question(testCase.unresolved).copy(
                required = testCase.id == "render-required-text",
                reason = "No access".takeIf { testCase.id == "render-required-text" },
            )
        }))
        val darkState = renderDraft(draftState, widthDp = 320, fontScale = 2f)
        listOf("light", "dark").forEachIndexed { index, name ->
            if (index == 1) compose.runOnIdle { darkState.value = true }
            compose.waitForIdle()
            scrollToQuestion("render-status")
            captureCard("$name-status")
            scrollToQuestion("render-optional-text")
            captureCard("$name-optional-text")
            scrollToQuestion("render-required-text")
            captureCard("$name-required-text")
        }
    }

    private data class QuestionCase(
        val id: String,
        val type: String,
        val unresolved: ResponseDisposition,
        val resolved: ResponseDisposition,
        val resolvedText: String?,
        val resolvedNumber: String?,
        val followingTag: String,
        val label: String = "Question $id",
    ) {
        fun question(disposition: ResponseDisposition) = InspectionQuestion(
            responseId = "response-$id",
            snapshotItemId = id,
            position = 1,
            label = label,
            responseType = type,
            unit = if (type == "NUMBER") "mm/s" else null,
            required = false,
            disposition = disposition,
            textValue = if (disposition == ResponseDisposition.VALUE) resolvedText else null,
            numberValue = if (disposition == ResponseDisposition.VALUE) resolvedNumber else null,
            reason = null,
            privateGuidance = "Keep this guidance private",
        )
    }

    private data class QuestionBounds(val metadataTop: Float, val privateGuidanceTop: Float, val followingTop: Float)

    private fun renderDraft(
        draftState: androidx.compose.runtime.MutableState<InspectionDraft>,
        dark: Boolean = false,
        widthDp: Int = 360,
        fontScale: Float = 1f,
    ): androidx.compose.runtime.MutableState<Boolean> {
        val viewModel = V16ServiceViewModel(RoomV16ServiceRepository(database, fixedTime())) {}
        val darkState = mutableStateOf(dark)
        compose.setContent {
            V16ServiceTheme(darkTheme = darkState.value) {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale)) {
                    Box(Modifier.width(widthDp.dp).height(1_200.dp)) {
                        ServiceScreen(
                            draft = draftState.value,
                            progress = null,
                            saveStatus = SaveStatus.Saved(1L),
                            focus = null,
                            viewModel = viewModel,
                            nav = rememberNavController(),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        return darkState
    }

    private fun scrollToQuestion(id: String) {
        compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("question-$id"))
        compose.onNodeWithTag("question-$id").assertIsDisplayed()
    }

    private fun followingBounds(testCase: QuestionCase): QuestionBounds {
        val questionTop = boundsByTag("question-${testCase.id}").top
        return QuestionBounds(
            metadataTop = bounds(testCase.id, "metadata").top - questionTop,
            privateGuidanceTop = bounds(testCase.id, "private-guidance").top - questionTop,
            followingTop = boundsByTag(testCase.followingTag).top - questionTop,
        )
    }

    private fun bounds(questionId: String, suffix: String) = boundsByTag("question-$questionId-$suffix")

    private fun boundsByTag(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun draft(question: InspectionQuestion) = draft(listOf(question))

    private fun draft(questions: List<InspectionQuestion>) = InspectionDraft(
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
        subjectType = com.v16studio.v16service.domain.WorkSubjectType.EQUIPMENT,
        equipmentId = "equipment-1",
        customerName = "Customer",
    )

    private fun captureCard(name: String) {
        try {
            val bitmap = compose.onNodeWithTag("question-${if (name.contains("status")) "render-status" else if (name.contains("optional")) "render-optional-text" else "render-required-text"}").captureToImage().asAndroidBitmap()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = context.externalCacheDir ?: context.cacheDir
            FileOutputStream(File(directory, "service-question-$name.png")).use { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            bitmap.recycle()
        } catch (failure: Throwable) {
            android.util.Log.w("V16ServiceRenderEvidence", "Best-effort artifact capture failed for $name", failure)
        }
    }

    private fun fixedTime() = object : BusinessTime {
        override val zoneId: ZoneId = ZoneId.of("Europe/Bucharest")
        override fun instant(): Instant = Instant.parse("2026-09-14T10:00:00Z")
    }
}
