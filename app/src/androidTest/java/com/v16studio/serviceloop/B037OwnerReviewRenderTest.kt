package com.v16studio.serviceloop

import android.content.Context
import android.graphics.Bitmap
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.domain.ClockBusinessTime
import com.v16studio.serviceloop.domain.CustomerType
import com.v16studio.serviceloop.domain.DueBucket
import com.v16studio.serviceloop.domain.DueService
import com.v16studio.serviceloop.domain.FollowUpDetail
import com.v16studio.serviceloop.domain.PlanDetail
import com.v16studio.serviceloop.domain.TemplateSummary
import com.v16studio.serviceloop.domain.VisitSiteOption
import com.v16studio.serviceloop.ui.FollowUpDetailScreen
import com.v16studio.serviceloop.ui.DueServicesProjection
import com.v16studio.serviceloop.ui.LocalDetailBackInterceptor
import com.v16studio.serviceloop.ui.NewVisitScreen
import com.v16studio.serviceloop.ui.PlanEditorScreen
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.UiState
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import androidx.navigation.compose.rememberNavController
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

class B037OwnerReviewRenderTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var visitContentKey = 0

    @Test
    fun captureOwnerReviewSurfacesForLightAndDarkInspection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        val viewModel = ServiceLoopViewModel(
            RoomServiceLoopRepository(database, ClockBusinessTime(zoneId = ZoneId.of("Europe/Bucharest"))),
        ) {}
        val state = UiState(
            loading = false,
            dueServicesProjection = DueServicesProjection.Available(emptyList()),
            businessDate = LocalDate.of(2026, 9, 18),
            businessZoneId = "Europe/Bucharest",
            templates = listOf(TemplateSummary("template", "T-001", "Safety checklist", 2, 3, "ACTIVE")),
        )
        try {
            setRenderedContent {
                PlanEditorScreen(
                    equipmentId = "equipment",
                    existing = PlanDetail("plan", "equipment", "Boiler", "P-004", "Annual service", 6, "MONTHS", "2026-10-01", "ACTIVE", "template"),
                    templates = state.templates,
                    padding = PaddingValues(),
                    state = state,
                    viewModel = viewModel,
                    nav = rememberNavController(),
                )
            }
            capture("plan-editor")

            setRenderedContent {
                NewVisitScreen(
                    sites = listOf(VisitSiteOption("site", "S-001", "Workshop", "Acme", emptyList(), CustomerType.STANDARD)),
                    dueServices = emptyList(),
                    padding = PaddingValues(),
                    state = state,
                    viewModel = viewModel,
                    nav = rememberNavController(),
                )
            }
            capture("create-visit-existing-top")
            compose.onNodeWithTag("new-visit-form").performScrollToNode(hasTestTag("field-appointment-time"))
            capture("create-visit-existing-appointment")

            compose.onNodeWithTag("visit-mode-NEW").performClick()
            compose.onNodeWithTag("new-visit-form").performScrollToNode(hasTestTag("visit-setup-heading"))
            capture("create-visit-new")

            setRenderedContent {
                FollowUpDetailScreen(
                    detail = FollowUpDetail("follow-up", "F-001", "CONTACT", "Call customer", "2026-09-20", "OPEN", "customer", "site", null, "", null, "Acme", "Workshop"),
                    padding = PaddingValues(),
                    state = state,
                    viewModel = viewModel,
                    nav = rememberNavController(),
                )
            }
            capture("follow-up-detail")

            setRenderedContent(darkTheme = true) {
                PlanEditorScreen(
                    equipmentId = "equipment",
                    existing = PlanDetail("plan", "equipment", "Boiler", "P-004", "Annual service", 6, "MONTHS", "2026-10-01", "ACTIVE", "template"),
                    templates = state.templates,
                    padding = PaddingValues(),
                    state = state,
                    viewModel = viewModel,
                    nav = rememberNavController(),
                )
            }
            capture("dark-plan-editor")

            setRenderedContent(darkTheme = true) {
                NewVisitScreen(
                    sites = listOf(VisitSiteOption("site", "S-001", "Workshop", "Acme", emptyList(), CustomerType.STANDARD)),
                    dueServices = emptyList(),
                    padding = PaddingValues(),
                    state = state,
                    viewModel = viewModel,
                    nav = rememberNavController(),
                )
            }
            compose.onNodeWithTag("new-visit-form").performScrollToNode(hasTestTag("field-appointment-time"))
            capture("dark-create-visit-appointment")

            setRenderedContent(darkTheme = true) {
                FollowUpDetailScreen(
                    detail = FollowUpDetail("follow-up", "F-001", "CONTACT", "Call customer", "2026-09-20", "OPEN", "customer", "site", null, "", null, "Acme", "Workshop"),
                    padding = PaddingValues(),
                    state = state,
                    viewModel = viewModel,
                    nav = rememberNavController(),
                )
            }
            capture("dark-follow-up-detail")
        } finally {
            compose.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            database.close()
        }
    }

    @Test
    fun newVisitBackPromptTracksOnlyMeaningfulDraftChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        val viewModel = ServiceLoopViewModel(
            RoomServiceLoopRepository(database, ClockBusinessTime(zoneId = ZoneId.of("Europe/Bucharest"))),
        ) {}
        val state = UiState(
            loading = false,
            dueServicesProjection = DueServicesProjection.Available(emptyList()),
            businessDate = LocalDate.of(2026, 9, 18),
            businessZoneId = "Europe/Bucharest",
        )
        val sites = listOf(VisitSiteOption("site", "S-001", "Workshop", "Acme", emptyList(), CustomerType.STANDARD))
        val dueService = DueService("plan", "P-001", "Annual service", "2026-09-20", "obligation", "equipment", "EQ-001", "Boiler", "site", "Workshop", "customer", "Acme", null, DueBucket.UPCOMING)
        try {
            setVisitContent(viewModel, state, sites)
            compose.onNodeWithTag("field-appointment-service-date-yyyy-mm-dd").performTextReplacement("2026-09-20")
            pressBack()
            compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed()
            compose.onNodeWithText("Keep editing").performClick()
            compose.onNodeWithTag("field-appointment-service-date-yyyy-mm-dd").performTextReplacement("2026-09-19")
            pressBack()
            compose.onNodeWithText("Discard unsaved changes?").assertDoesNotExist()

            setVisitContent(viewModel, state, sites)
            compose.onNodeWithTag("field-appointment-time").performTextInput("09:00")
            pressBack()
            compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed()
            compose.onNodeWithText("Keep editing").performClick()
            compose.onNodeWithTag("field-appointment-time").performTextClearance()
            pressBack()
            compose.onNodeWithText("Discard unsaved changes?").assertDoesNotExist()

            setVisitContent(viewModel, state, sites)
            compose.onNodeWithTag("visit-mode-NEW").performClick()
            pressBack()
            compose.onNodeWithText("Discard unsaved changes?").assertDoesNotExist()

            setVisitContent(viewModel, state, sites)
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("field-find-customer-or-site").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("field-find-customer-or-site").performTextInput("Acme")
            pressBack()
            compose.onNodeWithText("Discard unsaved changes?").assertDoesNotExist()

            setVisitContent(viewModel, state, sites, initialPlanIds = listOf("plan"), dueServices = listOf(dueService))
            pressBack()
            compose.onNodeWithText("Discard unsaved changes?").assertDoesNotExist()
        } finally {
            compose.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
            database.close()
        }
    }

    private fun setVisitContent(
        viewModel: ServiceLoopViewModel,
        state: UiState,
        sites: List<VisitSiteOption>,
        initialPlanIds: List<String> = emptyList(),
        dueServices: List<DueService> = emptyList(),
    ) {
        val contentKey = ++visitContentKey
        setRenderedContent {
            key(contentKey) {
                NewVisitScreen(
                    sites = sites,
                    dueServices = dueServices,
                    padding = PaddingValues(),
                    state = state,
                    viewModel = viewModel,
                    nav = rememberNavController(),
                    initialPlanIds = initialPlanIds,
                )
            }
        }
    }

    private fun pressBack() {
        compose.runOnUiThread {
            val manager = compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            manager.hideSoftInputFromWindow(compose.activity.currentFocus?.windowToken, 0)
        }
        compose.waitForIdle()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
    }

    private fun setRenderedContent(darkTheme: Boolean = false, content: @Composable () -> Unit) {
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme(darkTheme = darkTheme) {
                    Surface(Modifier.fillMaxSize().systemBarsPadding(), color = LocalServiceLoopTokens.current.canvas) {
                        CompositionLocalProvider(LocalDetailBackInterceptor provides remember { mutableStateOf<(() -> Unit)?>(null) }) {
                            content()
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = File(requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir), "b037-$name.png")
        FileOutputStream(file).use { check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
    }
}
