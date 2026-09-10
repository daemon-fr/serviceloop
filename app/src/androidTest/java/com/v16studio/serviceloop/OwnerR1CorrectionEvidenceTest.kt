package com.v16studio.serviceloop

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnerR1CorrectionEvidenceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private data class Proof(val number: String, val name: String, val route: String?, val prepareCapture: () -> Unit = {}, val assertReady: (ServiceLoopViewModel) -> Unit)

    private fun render(dark: Boolean) {
        val app = compose.activity.application as ServiceLoopApplication
        val bookedVisitId = runBlocking { app.container.startup.await(); app.container.repository.visits().first { it.state == "BOOKED" }.id }
        val route = mutableStateOf<String?>(null)
        val viewModel = ServiceLoopViewModel(
            app.container.repository,
            app.container.reportService,
            app.container.restrictedRecoveryState,
            app.container.businessDateSignal,
            app.container.reminderCoordinator,
            app.container.calendarCoordinator,
        ) { app.container.startup.await() }
        compose.activity.setContent { ServiceLoopTheme(darkTheme = dark) { key(route.value) { ServiceLoopApp(viewModel, route.value) } } }

        val proofs = listOf(
            Proof("01", "home-brand", null) {
                compose.onNodeWithTag("root-home").assertIsDisplayed()
                compose.onAllNodesWithText("ServiceLoop")[0].assertIsDisplayed()
            },
            Proof("02", "work-tabs", "work?tab=DUE_SERVICES") {
                compose.onNodeWithTag("root-work").assertIsDisplayed()
                compose.onNodeWithText("P-004 · Maintenance", substring = true).assertIsDisplayed()
            },
            Proof("06", "directory-tabs", "customers") {
                compose.onNodeWithTag("root-customers").assertIsDisplayed()
                assertTrue(compose.onAllNodesWithText("Customers").fetchSemanticsNodes().isNotEmpty())
                assertTrue(compose.onAllNodesWithText("Equipment").fetchSemanticsNodes().isNotEmpty())
            },
            Proof("05", "site-title-commands", "site/${FixtureIds.SITE}") { vm ->
                assertEquals(FixtureIds.SITE, vm.state.value.site?.id)
                compose.onNodeWithText("Site").assertIsDisplayed()
                compose.onNodeWithText("Edit").assertIsDisplayed()
                compose.onNodeWithText("Maps").assertIsDisplayed()
            },
            Proof("07", "equipment-command-peers", "equipment/${FixtureIds.EQUIPMENT_1}") { vm ->
                assertEquals(FixtureIds.EQUIPMENT_1, vm.state.value.equipment?.id)
                compose.onNodeWithText("Equipment").assertIsDisplayed()
                compose.onNodeWithText("Edit").assertIsDisplayed()
                compose.onNodeWithText("Start / resume").assertIsDisplayed()
            },
            Proof("08", "service-saved-editor", "inspection/${FixtureIds.OWNER_REVIEW_WORK}") { vm ->
                assertEquals(FixtureIds.OWNER_REVIEW_WORK, vm.state.value.inspection?.workItemId)
                compose.onNodeWithText("Service").assertIsDisplayed()
                compose.onNodeWithText("Saved on this device", substring = true).assertIsDisplayed()
                assertTrue(compose.onAllNodesWithContentDescription("Expand Public work performed").fetchSemanticsNodes().isNotEmpty())
            },
            Proof("08a", "expanded-editor-chrome", "inspection/${FixtureIds.OWNER_REVIEW_WORK}", prepareCapture = {
                compose.onAllNodesWithContentDescription("Expand Public work performed")[0].performClick()
                compose.onNodeWithTag("long-text-public-work-performed-expanded").assertIsDisplayed()
                assertTrue(compose.onAllNodesWithText("ServiceLoop").fetchSemanticsNodes().size >= 2)
            }) { vm ->
                assertEquals(FixtureIds.OWNER_REVIEW_WORK, vm.state.value.inspection?.workItemId)
                compose.onAllNodesWithContentDescription("Expand Public work performed")[0].assertIsDisplayed()
            },
            Proof("08b", "responsive-follow-up-filters", "work?tab=FOLLOW_UPS") {
                compose.onNodeWithTag("root-work").assertIsDisplayed()
                compose.onNodeWithText("Closed").assertIsDisplayed()
            },
            Proof("09", "history-sort", "history/global") { vm ->
                assertTrue(vm.state.value.history.isNotEmpty())
                compose.onNodeWithText("History").assertIsDisplayed()
                compose.onNodeWithTag("history-list").performScrollToNode(hasText("Event newest"))
                compose.onNodeWithText("Event newest").assertIsDisplayed()
            },
            Proof("10", "due-services-settled", "work?tab=DUE_SERVICES") { vm ->
                assertTrue(vm.state.value.dueServicesReady)
                assertTrue(vm.state.value.dueServices.isNotEmpty())
                compose.onNodeWithText("Upcoming").assertIsDisplayed()
                compose.onNodeWithText("Reading due services").assertDoesNotExist()
            },
            Proof("11", "create-visit-settled", "visit/new") { vm ->
                assertTrue(vm.state.value.dueServicesReady)
                assertTrue(vm.state.value.visitSites.isNotEmpty())
                compose.onNodeWithText("Set up visit").assertIsDisplayed()
                compose.onNodeWithText("Reading due services").assertDoesNotExist()
            },
            Proof("12", "booked-visit-actions", "visit/$bookedVisitId") { vm ->
                assertEquals(bookedVisitId, vm.state.value.visit?.id)
                compose.onNodeWithTag("visit-detail-list").performScrollToNode(hasText("Cancel booking"))
                compose.onNodeWithText("Cancel booking").assertIsDisplayed()
            },
            Proof("13", "follow-up-detail", "follow-up/follow-up-001") { vm ->
                assertEquals("follow-up-001", vm.state.value.followUp?.id)
                compose.onNode(hasScrollAction()).performScrollToNode(hasText("Save follow-up changes"))
                compose.onNodeWithText("Save follow-up changes").assertIsDisplayed()
            },
        )

        proofs.forEach { proof ->
            compose.runOnIdle { route.value = proof.route }
            compose.waitForIdle()
            compose.waitUntil(15_000) {
                val state = viewModel.state.value
                val destinationReady = when (proof.name) {
                    "site-title-commands" -> state.site?.id == FixtureIds.SITE
                    "equipment-command-peers" -> state.equipment?.id == FixtureIds.EQUIPMENT_1
                    "service-saved-editor" -> state.inspection?.workItemId == FixtureIds.OWNER_REVIEW_WORK
                    "history-sort" -> state.history.isNotEmpty()
                    "create-visit-settled" -> state.visitSites.isNotEmpty() && state.dueServicesReady
                    else -> true
                }
                !state.loading && state.rootDataReady && destinationReady &&
                    compose.onAllNodesWithText("Reading due services").fetchSemanticsNodes().isEmpty()
            }
            proof.assertReady(viewModel)
            proof.prepareCapture()
            compose.waitForIdle()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(600)
            capture("r1-after-${if (dark) "dark" else "light"}-${proof.number}-${proof.name}.png")
        }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val directory = instrumentation.targetContext.externalCacheDir ?: instrumentation.targetContext.cacheDir
        FileOutputStream(File(directory, name)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    @Test fun lightOwnerR1CorrectionEvidence() = render(false)
    @Test fun darkOwnerR1CorrectionEvidence() = render(true)
}
