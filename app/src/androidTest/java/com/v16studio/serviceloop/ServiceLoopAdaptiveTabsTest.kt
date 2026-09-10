package com.v16studio.serviceloop

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopContentTabs
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceLoopAdaptiveTabsTest {
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
                        ServiceLoopTheme {
                            val selected = remember { mutableStateOf("DUE") }
                            ServiceLoopContentTabs(options, selected.value, { selected.value = it }, Modifier.width(width.dp))
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

    @Test fun dueServicesFailureOffersWorkingRetry() {
        val repository = RetryRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        compose.runOnUiThread { compose.activity.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel) } } }
        compose.waitUntil(5_000) { viewModel.state.value.rootDataReady && viewModel.state.value.dueServicesError != null }
        compose.onNodeWithText("Work").performClick()
        compose.onNodeWithTag("retry-due-services").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { viewModel.state.value.dueServicesReady }
        compose.onNodeWithText("P-RETRY · Maintenance", substring = true).assertIsDisplayed()
        assertEquals(2, repository.subscriptions)
    }

    private class RetryRepository : ServiceLoopRepository {
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
