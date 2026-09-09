package com.v16studio.serviceloop

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.ChecklistItemSnapshotEntity
import com.v16studio.serviceloop.data.CustomerEntity
import com.v16studio.serviceloop.data.DispatchOutboxEditorDraft
import com.v16studio.serviceloop.data.DispatchOutboxItemDraft
import com.v16studio.serviceloop.data.DispatchPackageService
import com.v16studio.serviceloop.data.EquipmentEntity
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.data.SiteEntity
import com.v16studio.serviceloop.data.TemplateSnapshotEntity
import com.v16studio.serviceloop.data.WorkItemEntity
import com.v16studio.serviceloop.data.WorkItemPrivateDraftEntity
import com.v16studio.serviceloop.data.WorkItemPublicDraftEntity
import com.v16studio.serviceloop.data.WorkingResponseEntity
import com.v16studio.serviceloop.data.WorkingVisitEntity
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.domain.SaveStatus
import com.v16studio.serviceloop.ui.DetailScaffold
import com.v16studio.serviceloop.ui.DispatchExportReviewScreen
import com.v16studio.serviceloop.ui.DispatchOutboxScreen
import com.v16studio.serviceloop.ui.DispatchVisitEditorScreen
import com.v16studio.serviceloop.ui.InspectionScreen
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopLongTextEditor
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StageAVisualProofTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private lateinit var database: ServiceLoopDatabase
    private lateinit var dispatch: DispatchPackageService
    private lateinit var repository: RoomServiceLoopRepository
    private lateinit var viewModel: ServiceLoopViewModel
    private lateinit var visitIds: List<String>
    private val businessDate = LocalDate.of(2026, 9, 9)

    @Before
    fun seedActualModels() {
        database = Room.inMemoryDatabaseBuilder(compose.activity, ServiceLoopDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dispatch = DispatchPackageService(database)
        val time = object : BusinessTime {
            override val zoneId = ZoneId.of("Europe/Bucharest")
            override fun instant() = Instant.parse("2026-09-09T09:30:00Z")
        }
        repository = RoomServiceLoopRepository(database, time)
        runBlocking {
            val dao = database.serviceLoopDao()
            dao.insertCustomers(listOf(CustomerEntity("customer", "CU-1042", "Northside Foods")))
            dao.insertSites(listOf(SiteEntity("site", "customer", "SITE-021", "Northside Plant", "18 Mill Lane", null)))
            dao.insertEquipment(listOf(EquipmentEntity("equipment", "site", "EQ-117", "AHU-04", "Air handling unit", "Ventra", "VX-4", "SN-4102", null)))
            val identity = dispatch.identity()
            dispatch.importTechnician(identity)
            val team = dispatch.createTeam("North service team")
            dispatch.setTeamMember(team, identity.technicianId, true, true)
            visitIds = listOf(
                Triple("JOB-2048", "2026-09-10", "09:00"),
                Triple("JOB-2051", "2026-09-10", "13:30"),
                Triple("JOB-2059", "2026-09-12", "08:00"),
            ).mapIndexed { index, (reference, date, appointment) ->
                dispatch.saveOutboxVisit(
                    DispatchOutboxEditorDraft(
                        managerReference = reference,
                        siteId = "site",
                        serviceDate = date,
                        appointmentLocalTime = appointment,
                        appointmentZoneId = "Europe/Bucharest",
                        instructions = "Call site contact before arrival and use the rear service entrance.",
                        teamIds = listOf(team),
                        items = listOf(
                            DispatchOutboxItemDraft("dispatch-item-$index", "equipment", "Inspect supply fan and record vibration"),
                        ),
                    ),
                )
            }

            dao.insertTemplateSnapshots(listOf(TemplateSnapshotEntity("template", null, "AHU service", 4, 1)))
            dao.insertChecklistItems(
                listOf(
                    ChecklistItemSnapshotEntity("check-status", "template", 1, "Inspect the full drive assembly, guards, belt edges, tension, alignment, fasteners, and nearby cable routing for defects or unsafe conditions", "STATUS", null, true, null),
                    ChecklistItemSnapshotEntity("check-text", "template", 2, "Record observations from the internal cabinet inspection and any follow-up advice for the customer", "TEXT", null, true, null),
                    ChecklistItemSnapshotEntity("check-number", "template", 3, "Measured supply fan vibration", "NUMBER", "mm/s", true, null),
                ),
            )
            dao.insertVisits(listOf(WorkingVisitEntity("working-visit", "V-104", "customer", "site", "2026-09-09", "Northside Foods", "Northside Plant", "18 Mill Lane", "WORKING", 1)))
            dao.insertWorkItems(listOf(WorkItemEntity("working-item", "working-visit", "equipment", null, null, "template", "Air handling unit", "EQ-117", "Quarterly AHU service", null, "2026-09-09", 3, "MONTHS", false, null, false)))
            dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity("working-item", "Inspected drive assembly and cleaned accessible surfaces.")))
            dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity("working-item", "Access panel key held by facilities.")))
            dao.upsertResponses(
                listOf(
                    WorkingResponseEntity("response-status", "working-item", "check-status", "ISSUE_FOUND", null, null, "Belt edge wear observed; replacement should be planned.", 1, issueFoundReasonDraft = "Belt edge wear observed; replacement should be planned."),
                    WorkingResponseEntity("response-text", "working-item", "check-text", "NOT_APPLICABLE", "Light dust only; no overheating marks observed.", null, "Internal cabinet was inaccessible while production was running.", 1, notApplicableReasonDraft = "Internal cabinet was inaccessible while production was running."),
                    WorkingResponseEntity("response-number", "working-item", "check-number", "VALUE", null, "4.7", null, 1, notApplicableReasonDraft = "Measurement point obstructed on prior attempt."),
                ),
            )
        }
        viewModel = ServiceLoopViewModel(repository) {}
    }

    @After fun closeDatabase() = database.close()

    private fun render(width: Int, dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme(darkTheme = dark) {
                    Box(
                        Modifier
                            .width(width.dp)
                            .fillMaxHeight()
                            .background(LocalServiceLoopTokens.current.canvas)
                            .testTag("stage-a-proof"),
                    ) { content() }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "stage-a-proof")
        assertTrue(directory.exists() || directory.mkdirs())
        File(directory, name).outputStream().use {
            assertTrue(compose.onNodeWithTag("stage-a-proof").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test
    fun capturesRequiredStageASurfaceMatrixFromActualComposables() {
        listOf(360, 411).forEach { width ->
            listOf(false, true).forEach { dark ->
                val suffix = "${width}-${if (dark) "dark" else "light"}.png"

                render(width, dark) {
                    val nav = rememberNavController()
                    DetailScaffold("Outbox", nav) {
                        DispatchOutboxScreen(it, nav, businessDate, dispatch, database)
                    }
                }
                compose.waitUntil(5_000) { compose.onAllNodesWithText("JOB-2048", substring = true).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("dispatch-new-visit").assertHeightIsAtLeast(52.dp)
                compose.onNodeWithTag("dispatch-status-filter").assertHeightIsAtLeast(48.dp)
                capture("outbox-unselected-$suffix")

                compose.onNodeWithTag("dispatch-select-${visitIds[0]}").performClick()
                compose.onNodeWithTag("dispatch-select-${visitIds[1]}").performClick()
                compose.onAllNodesWithText("2 selected").fetchSemanticsNodes()
                compose.onNodeWithTag("dispatch-export-selected").assertHeightIsAtLeast(52.dp)
                capture("outbox-selected-$suffix")

                render(width, dark) {
                    val nav = rememberNavController()
                    DetailScaffold("New dispatch visit", nav) {
                        DispatchVisitEditorScreen(it, nav, visitIds.first(), businessDate, dispatch, database)
                    }
                }
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Northside Foods").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("dispatch-save-visit").assertHeightIsAtLeast(52.dp)
                capture("dispatch-editor-$suffix")

                render(width, dark) {
                    val nav = rememberNavController()
                    DetailScaffold("Review export", nav) {
                        DispatchExportReviewScreen(it, nav, visitIds.take(2), dispatch)
                    }
                }
                compose.onNodeWithTag("dispatch-export-sender").performTextInput("ServiceLoop coordinator")
                compose.waitUntil(5_000) { compose.onAllNodesWithText("First export", substring = true).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("dispatch-export-confirm").assertHeightIsAtLeast(52.dp)
                capture("export-review-$suffix")
            }
        }

        listOf(false, true).forEach { dark ->
            val draft = runBlocking { repository.inspection("working-item")!! }
            render(411, dark) {
                val nav = rememberNavController()
                DetailScaffold("Working visit", nav) {
                    Box(Modifier.fillMaxSize().padding(it)) {
                        InspectionScreen(draft, SaveStatus.Failed("Storage unavailable", 1), null, viewModel, nav)
                    }
                }
            }
            assertTrue(compose.onAllNodesWithText("Not saved — action needed").fetchSemanticsNodes().isNotEmpty())
            assertTrue(compose.onAllNodesWithText("Storage unavailable", substring = true).fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("inspection-list").performScrollToNode(hasTestTag("response-check-status-ISSUE_FOUND"))
            compose.onNodeWithText("Not saved — action needed").assertIsDisplayed()
            capture("working-checklist-411-${if (dark) "dark" else "light"}.png")
        }
    }

    @Test
    fun editorRemainsOperableAt320DpAndTwoXFontScale() {
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    val deviceDensity = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(deviceDensity.density, 2f)) {
                        Box(
                            Modifier
                                .width(320.dp)
                                .fillMaxHeight()
                                .background(LocalServiceLoopTokens.current.canvas)
                                .testTag("stage-a-adaptation-proof"),
                        ) {
                            val nav = rememberNavController()
                            DetailScaffold("New dispatch visit", nav) {
                                DispatchVisitEditorScreen(it, nav, visitIds.first(), businessDate, dispatch, database)
                            }
                        }
                    }
                }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Northside Foods").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("dispatch-choose-site").assertIsDisplayed()
        compose.onNodeWithTag("dispatch-save-visit").assertIsDisplayed()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "stage-a-proof")
        File(directory, "dispatch-editor-320-light-fontscale-2.png").outputStream().use {
            assertTrue(compose.onNodeWithTag("stage-a-adaptation-proof").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test
    fun foundationBusyAndExpandedEditorContractsRemainOperable() {
        var activations = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    var buffer by remember { mutableStateOf("Original") }
                    Column(Modifier.padding(16.dp)) {
                        ServiceLoopPrimaryButton(
                            label = "Saving visit",
                            onClick = { activations++ },
                            busy = true,
                            modifier = Modifier.fillMaxWidth().testTag("busy-contract"),
                        )
                        Spacer(Modifier.height(12.dp))
                        ServiceLoopLongTextEditor(
                            value = buffer,
                            onValueChange = { buffer = it },
                            label = "Issue details",
                            private = false,
                            fieldTestTag = "focus-contract-field",
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("busy-contract").assertTextContains("Saving visit").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(activations == 0) }

        compose.onNodeWithTag("long-text-issue-details-expand")
            .assertContentDescriptionEquals("Expand Issue details")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        compose.onNodeWithTag("long-text-issue-details-expanded").performTextInput(" updated")
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("focus-contract-field").assertIsFocused().assertTextContains("Original updated")

        compose.onNodeWithTag("long-text-issue-details-expand").performClick()
        compose.onNodeWithTag("long-text-issue-details-expanded").performTextInput(" again")
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        compose.onNodeWithTag("focus-contract-field").assertIsFocused().assertTextContains("Original updated again")
    }
}
