package com.v16studio.v16service

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v16studio.v16service.ui.designsystem.V16ServiceAdaptiveActionRow
import com.v16studio.v16service.ui.designsystem.V16ServicePrimaryButton
import com.v16studio.v16service.ui.designsystem.V16ServiceSecondaryButton
import com.v16studio.v16service.ui.theme.V16ServiceTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V16ServiceAdaptiveActionRowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun actionRowMeasuresWithinLazyColumnAndRetainsResponsivePacking() {
        compose.setContent {
            V16ServiceTheme {
                LazyColumn(Modifier.fillMaxSize().testTag("unbounded-action-list")) {
                    item {
                        V16ServiceAdaptiveActionRow(
                            actions = listOf({ V16ServiceSecondaryButton("Open", {}, Modifier.testTag("single-action")) }),
                            modifier = Modifier.width(260.dp),
                        )
                    }
                    item {
                        V16ServiceAdaptiveActionRow(
                            actions = listOf(
                                { V16ServiceSecondaryButton("Open", {}, Modifier.testTag("two-action-first")) },
                                { V16ServicePrimaryButton("Clear", {}, Modifier.testTag("two-action-second")) },
                            ),
                            modifier = Modifier.width(180.dp),
                        )
                    }
                    item {
                        V16ServiceAdaptiveActionRow(
                            actions = listOf(
                                { V16ServiceSecondaryButton("Book", {}, Modifier.testTag("three-action-first")) },
                                { V16ServicePrimaryButton("Start", {}, Modifier.testTag("three-action-second")) },
                                { V16ServiceSecondaryButton("Clear", {}, Modifier.testTag("three-action-third")) },
                            ),
                            modifier = Modifier.width(220.dp),
                        )
                    }
                }
            }
        }

        val density = compose.activity.resources.displayMetrics.density
        val single = compose.onNodeWithTag("single-action").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals("a single action fills its row", 260f * density, single.width, 1.5f)
        assertTrue("single action has a finite content-driven height", single.height.isFinite() && single.height >= 48f * density - 1.5f)

        val shortFirst = compose.onNodeWithTag("two-action-first").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val shortSecond = compose.onNodeWithTag("two-action-second").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("two short actions fit on one row", shortFirst.top == shortSecond.top && shortFirst.right <= shortSecond.left)
        assertTrue("short action heights are finite and touch-sized", shortFirst.height.isFinite() && shortSecond.height.isFinite() && shortFirst.height >= 48f * density - 1.5f && shortSecond.height >= 48f * density - 1.5f)

        val first = compose.onNodeWithTag("three-action-first").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("three-action-second").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val third = compose.onNodeWithTag("three-action-third").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("two long actions remain together when they fit: first=$first second=$second", first.top == second.top && first.right <= second.left)
        assertTrue("third action wraps to the next row: first=$first second=$second third=$third", second.bottom <= third.top)
        assertTrue("wrapped action heights are finite and touch-sized", listOf(first, second, third).all { it.height.isFinite() && it.height >= 48f * density - 1.5f })
        assertTrue("actions remain within their requested parent width", listOf(first, second, third).all { it.right - first.left <= 220f * density + 1.5f })
    }
}
