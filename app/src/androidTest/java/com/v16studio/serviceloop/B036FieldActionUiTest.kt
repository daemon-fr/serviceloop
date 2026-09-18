package com.v16studio.serviceloop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v16studio.serviceloop.ui.InspectionChecklistSelector
import com.v16studio.serviceloop.ui.VisitDateInput
import com.v16studio.serviceloop.ui.VisitTimeInput
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChoicePair
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class B036FieldActionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun fieldAdjacentActionsShareAccessibleSquareGeometry() {
        compose.setContent {
            ServiceLoopTheme {
                Column {
                    InspectionChecklistSelector(emptyList(), null, {}, "b036-template")
                    VisitDateInput("2026-09-19", {}, LocalDate.parse("2026-09-18"))
                    VisitTimeInput("", {})
                }
            }
        }

        listOf("b036-template-create", "appointment-date-picker", "appointment-time-picker").forEach { tag ->
            val node = compose.onNodeWithTag(tag).assertIsDisplayed().assertHasClickAction().fetchSemanticsNode()
            assertTrue("$tag must remain at least 48dp square", node.boundsInRoot.width >= 48f && node.boundsInRoot.height >= 48f)
        }
        compose.onNodeWithContentDescription("Create inspection template").assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose appointment date").assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose appointment time").assertIsDisplayed()
    }

    @Test
    fun followUpTypeChoicesStayEqualWidthAndOnOneRowAtLargeFont() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                ServiceLoopTheme {
                    ServiceLoopChoicePair(
                        options = listOf("CONTACT" to "Contact", "CORRECTIVE" to "Corrective"),
                        selected = "CONTACT",
                        onSelected = {},
                        modifier = Modifier.width(320.dp).testTag("b036-follow-up-types"),
                        testTagPrefix = "b036-follow-up-type",
                        stackWhenLargeFont = false,
                    )
                }
            }
        }
        val contact = compose.onNodeWithTag("b036-follow-up-type-CONTACT").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val corrective = compose.onNodeWithTag("b036-follow-up-type-CORRECTIVE").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(contact.top, corrective.top, 0.1f)
        assertEquals(contact.width, corrective.width, 0.1f)
    }
}
