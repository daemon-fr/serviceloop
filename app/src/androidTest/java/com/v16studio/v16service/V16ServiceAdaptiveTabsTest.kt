package com.v16studio.v16service

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v16studio.v16service.ui.designsystem.V16ServiceContentTabs
import com.v16studio.v16service.ui.designsystem.V16ServiceResponsivePair
import com.v16studio.v16service.ui.designsystem.V16ServiceSecondaryButton
import com.v16studio.v16service.ui.V16ServiceApp
import com.v16studio.v16service.ui.DueServicesScreen
import com.v16studio.v16service.ui.DueServicesProjection
import com.v16studio.v16service.ui.V16ServiceViewModel
import com.v16studio.v16service.ui.UiState
import com.v16studio.v16service.ui.theme.V16ServiceTheme
import com.v16studio.v16service.data.V16ServiceRepository
import com.v16studio.v16service.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V16ServiceAdaptiveTabsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun contentAwareTabsRemainReadableAndSelectableAcrossRequiredWidthAndFontMatrix() {
        val cases = buildList {
            listOf(320, 360, 411).forEach { width -> listOf(1f, 1.3f, 2f).forEach { scale -> add(width to scale) } }
            add(600 to 1f)
        }
        val options = listOf("DUE" to "Due services", "VISITS" to "Visits", "FOLLOW" to "Follow-ups")

        cases.forEach { (width, fontScale) ->
            compose.runOnUiThread {
                compose.activity.setContent {
                    val deviceDensity = LocalDensity.current.density
                    val fittedDensity = deviceDensity * minOf(1f, 400f / width)
                    CompositionLocalProvider(LocalDensity provides Density(fittedDensity, fontScale)) {
                        V16ServiceTheme {
                            val selected = remember { mutableStateOf("DUE") }
                            V16ServiceContentTabs(options, selected.value, { selected.value = it }, Modifier.width(width.dp))
                        }
                    }
                }
            }
            compose.waitForIdle()
            val tabBounds = options.associate { (_, label) ->
                label to compose.onNodeWithTag("content-tab-$label").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            }
            assertTrue("$width dp/$fontScale: content-aware width", tabBounds.getValue("Due services").width > tabBounds.getValue("Visits").width)
            assertTrue("$width dp/$fontScale: content-aware width", tabBounds.getValue("Follow-ups").width > tabBounds.getValue("Visits").width)
            assertEquals("$width dp/$fontScale: equal height", tabBounds.getValue("Due services").height, tabBounds.getValue("Visits").height, 0.5f)
            assertEquals("$width dp/$fontScale: equal height", tabBounds.getValue("Due services").height, tabBounds.getValue("Follow-ups").height, 0.5f)
            options.forEach { (_, label) ->
                val textNode = compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
                val textBounds = textNode.fetchSemanticsNode().boundsInRoot
                val parent = tabBounds.getValue(label)
                assertTrue("$width dp/$fontScale: $label starts inside its tab", textBounds.left >= parent.left - 0.5f)
                assertTrue("$width dp/$fontScale: $label ends inside its tab", textBounds.right <= parent.right + 0.5f)
                assertTrue("$width dp/$fontScale: $label top is visible", textBounds.top >= parent.top - 0.5f)
                assertTrue("$width dp/$fontScale: $label bottom is visible", textBounds.bottom <= parent.bottom + 0.5f)
                val layouts = mutableListOf<TextLayoutResult>()
                textNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getResults -> getResults(layouts) }
                assertTrue("$width dp/$fontScale: $label produced a layout", layouts.isNotEmpty())
                assertTrue(
                    "$width dp/$fontScale: $label is complete, unellipsized, and within its text layout; " +
                        "tabWidth=${parent.width}, textBoundsWidth=${textBounds.width}; " +
                        layouts.joinToString {
                            "widthFlag=${it.didOverflowWidth}, height=${it.didOverflowHeight}, lines=${it.lineCount}, size=${it.size}, paragraphWidth=${it.multiParagraph.width}, " +
                                "end=${it.getLineEnd(it.lineCount - 1, visibleEnd = false)}/${label.length}, " +
                                "ellipsized=${(0 until it.lineCount).map(it::isLineEllipsized)}, " +
                                "leftRight=${(0 until it.lineCount).map { line -> it.getLineLeft(line) to it.getLineRight(line) }}"
                        },
                    layouts.all { result ->
                        !result.didOverflowHeight &&
                            result.lineCount > 0 &&
                            result.getLineEnd(result.lineCount - 1, visibleEnd = false) == label.length &&
                            (0 until result.lineCount).none { line -> result.isLineEllipsized(line) } &&
                            (0 until result.lineCount).all { line -> result.getLineRight(line) - result.getLineLeft(line) <= textBounds.width + 1.5f }
                    },
                )
                compose.onNodeWithTag("content-tab-$label").performClick().assertIsSelected()
                tabBounds.forEach { (tabLabel, before) ->
                    val after = compose.onNodeWithTag("content-tab-$tabLabel").fetchSemanticsNode().boundsInRoot
                    assertEquals("$width dp/$fontScale: selected state preserves $tabLabel width", before.width, after.width, 0.5f)
                    assertEquals("$width dp/$fontScale: selected state preserves $tabLabel height", before.height, after.height, 0.5f)
                }
            }
        }
    }

    @Test fun responsivePairsStackBelowThePhoneThresholdOrAtLargeText() {
        listOf(320 to 1f, 360 to 1f, 360 to 1.3f, 411 to 1f, 411 to 2f).forEach { (width, fontScale) ->
            compose.runOnUiThread {
                compose.activity.setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                        V16ServiceTheme {
                            Box(Modifier.width(width.dp)) {
                                V16ServiceResponsivePair(
                                    first = { V16ServiceSecondaryButton("Customer", {}, Modifier.testTag("pair-first")) },
                                    second = { V16ServiceSecondaryButton("Site", {}, Modifier.testTag("pair-second")) },
                                )
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            val first = compose.onNodeWithTag("pair-first").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val second = compose.onNodeWithTag("pair-second").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$width dp/$fontScale: first target remains touch-sized", first.height >= 48f)
            assertTrue("$width dp/$fontScale: second target remains touch-sized", second.height >= 48f)
            if (width < 360 || fontScale >= 1.3f) {
                assertTrue("$width dp/$fontScale: pair stacks", first.bottom <= second.top)
            } else {
                assertTrue("$width dp/$fontScale: pair remains contained side-by-side", first.right <= second.left)
            }
        }
    }

    @Test fun dueServicesFailureOffersWorkingRetry() {
        val repository = RetryRepository()
        val viewModel = V16ServiceViewModel(repository) {}
        compose.runOnUiThread { compose.activity.setContent { V16ServiceTheme { V16ServiceApp(viewModel) } } }
        compose.waitUntil(5_000) { viewModel.state.value.rootDataReady && viewModel.state.value.dueServicesError != null }
        compose.onNodeWithText("Work").performClick()
        compose.onNodeWithTag("retry-due-services").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { viewModel.state.value.dueServicesReady }
        compose.onNodeWithText("P-RETRY · Maintenance", substring = true).assertIsDisplayed()
        assertEquals(2, repository.subscriptions)
    }

    @Test fun registerRootKeepsTheCustomerDomainTab() {
        val viewModel = V16ServiceViewModel(RetryRepository()) {}
        compose.runOnUiThread { compose.activity.setContent { V16ServiceTheme { V16ServiceApp(viewModel) } } }

        compose.waitUntil(5_000) { compose.onAllNodesWithTag("root-home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Register").assertIsDisplayed().performClick()
        compose.onNodeWithTag("root-customers").assertIsDisplayed()
        compose.onNodeWithTag("content-tab-Customers").assertIsDisplayed()
        compose.onNodeWithText("Customers").assertIsDisplayed()
    }

    @Test fun dueServiceSelectorsAreBalancedAtPhoneWidthAndStackSafelyForLargeText() {
        val viewModel = V16ServiceViewModel(RetryRepository()) {}
        val state = UiState(
            loading = false,
            dueServicesProjection = DueServicesProjection.Available(emptyList()),
            businessDate = java.time.LocalDate.of(2026, 9, 11),
            businessZoneId = "Europe/Bucharest",
        )
        listOf(360 to 1f, 411 to 1f, 360 to 2f).forEach { (width, fontScale) ->
            compose.runOnUiThread {
                compose.activity.setContent {
                    val deviceDensity = LocalDensity.current.density
                    val fittedDensity = deviceDensity * minOf(1f, 400f / width)
                    CompositionLocalProvider(LocalDensity provides Density(fittedDensity, fontScale)) {
                        V16ServiceTheme {
                            Box(Modifier.width(width.dp).fillMaxHeight()) {
                                DueServicesScreen(emptyList(), PaddingValues(), state, viewModel, rememberNavController())
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            val dueDateSelector = compose.onNodeWithTag("due-date-selector").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val visitSelector = compose.onNodeWithTag("due-visit-selector").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$width dp/$fontScale: due-date selector is touch-sized", dueDateSelector.height >= 64f)
            assertTrue("$width dp/$fontScale: visit selector is touch-sized", visitSelector.height >= 64f)
            if (fontScale < 1.3f) {
                assertEquals("$width dp/$fontScale: side-by-side selector height", dueDateSelector.height, visitSelector.height, 0.5f)
                assertEquals("$width dp/$fontScale: side-by-side selector top", dueDateSelector.top, visitSelector.top, 0.5f)
                assertEquals("$width dp/$fontScale: side-by-side selector width", dueDateSelector.width, visitSelector.width, 0.5f)
            } else {
                assertTrue("$width dp/$fontScale: large-text selectors stack", dueDateSelector.bottom <= visitSelector.top)
            }
            assertTrue(compose.onAllNodesWithText("Booking").fetchSemanticsNodes().isEmpty())
            assertTrue(compose.onAllNodesWithText("Booked only").fetchSemanticsNodes().isEmpty())
        }
    }

    @Test fun dueServiceSelectionBarAppearsCountsClearsAndKeepsSameSiteEligibility() {
        val rows = listOf(
            DueService("plan-a", "P-A", "Maintenance", "2026-09-01", "ob-a", "eq-a", "EQ-A", "Pump", "site-a", "Main site", "customer", "Customer", null, DueBucket.OVERDUE),
            DueService("plan-b", "P-B", "Maintenance", "2026-09-02", "ob-b", "eq-b", "EQ-B", "Boiler", "site-a", "Main site", "customer", "Customer", null, DueBucket.OVERDUE),
            DueService("plan-c", "P-C", "Maintenance", "2026-09-03", "ob-c", "eq-c", "EQ-C", "Fan", "site-b", "Other site", "customer", "Customer", null, DueBucket.OVERDUE),
            DueService("plan-d", "P-D", "Maintenance", "2026-09-04", "ob-d", "eq-d", "EQ-D", "Motor", "site-a", "Main site", "customer", "Customer", "visit-d", DueBucket.OVERDUE),
        )
        val state = UiState(
            loading = false,
            dueServicesProjection = DueServicesProjection.Available(rows),
            businessDate = java.time.LocalDate.of(2026, 9, 5),
            businessZoneId = "Europe/Bucharest",
        )
        val viewModel = V16ServiceViewModel(RetryRepository()) {}
        compose.runOnUiThread { compose.activity.setContent {
            V16ServiceTheme {
                Box(Modifier.width(360.dp).fillMaxHeight().testTag("due-screen-with-navigation-inset")) {
                    DueServicesScreen(rows, PaddingValues(bottom = 80.dp), state, viewModel, rememberNavController())
                }
            }
        } }
        compose.waitForIdle()

        assertTrue(compose.onAllNodesWithTag("due-service-selection-bar").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithTag("book-selected-services").fetchSemanticsNodes().isEmpty())
        compose.onAllNodesWithTag("entity-record-selection").assertCountEquals(3)
        compose.onAllNodesWithTag("entity-record-selection")[0].performClick()
        compose.onNodeWithTag("due-service-selected-count").assertIsDisplayed()
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        compose.onAllNodesWithTag("entity-record-selection").assertCountEquals(2)
        assertTrue(compose.onAllNodesWithContentDescription("Select P-C · Maintenance").fetchSemanticsNodes().isEmpty())
        compose.onAllNodesWithTag("entity-record-selection")[1].performClick()
        compose.onNodeWithText("2 selected").assertIsDisplayed()
        compose.onNodeWithTag("book-selected-services").assertIsDisplayed()
        compose.onNodeWithTag("start-selected-services").assertIsDisplayed()
        val container = compose.onNodeWithTag("due-screen-with-navigation-inset").fetchSemanticsNode().boundsInRoot
        val selectionBar = compose.onNodeWithTag("due-service-selection-bar").fetchSemanticsNode().boundsInRoot
        assertTrue("selection area stays above the simulated bottom navigation", selectionBar.bottom <= container.bottom - 79f)
        compose.onNodeWithTag("clear-selected-services").performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithTag("due-service-selection-bar").fetchSemanticsNodes().isEmpty())
    }

    private class RetryRepository : V16ServiceRepository {
        var subscriptions = 0
        override suspend fun home() = HomeSummary(null,null,null,null,null,null,null,0,null,null,0,0)
        override suspend fun equipment(id: String) = null
        override suspend fun equipmentList(): List<EquipmentSummary> = emptyList()
        override suspend fun customerList(): List<CustomerSummary> = emptyList()
        override suspend fun inspection(workItemId: String) = null
        override suspend fun completionLines(visitId: String): List<CompletionLine> = emptyList()
        override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?) = 0L
        override fun observeDueServices(): Flow<List<DueService>> = flow {
            subscriptions++
            if(subscriptions==1) error("initial failure")
            emit(listOf(DueService("plan","P-RETRY","Maintenance","2026-09-01","obligation","equipment","EQ-1","Machine","site","Site","customer","Customer",null,DueBucket.OVERDUE)))
        }
    }
}
