package com.v16studio.v16service

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v16studio.v16service.ui.InspectionChecklistSelector
import com.v16studio.v16service.ui.VisitDateInput
import com.v16studio.v16service.ui.VisitTimeInput
import com.v16studio.v16service.ui.designsystem.V16ServiceChoicePair
import com.v16studio.v16service.ui.theme.V16ServiceTheme
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
            V16ServiceTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    InspectionChecklistSelector(emptyList(), null, {}, "b036-template")
                    VisitDateInput("2026-09-19", {}, LocalDate.parse("2026-09-18"))
                    VisitTimeInput("", {})
                }
            }
        }

        listOf("b036-template-create", "appointment-date-picker", "appointment-time-picker").forEach { tag ->
            val node = compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertHasClickAction().fetchSemanticsNode()
            assertTrue("$tag must remain at least 48dp square", node.boundsInRoot.width >= 48f && node.boundsInRoot.height >= 48f)
        }
        compose.onNodeWithContentDescription("Create inspection template").assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose appointment date").assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose appointment time").assertIsDisplayed()
    }

    @Test
    fun appointmentInputsShareHeightAndRemainManuallyEditable() {
        compose.setContent {
            V16ServiceTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    var date by remember { mutableStateOf("2026-09-19") }
                    var time by remember { mutableStateOf("") }
                    VisitDateInput(date, { date = it }, LocalDate.parse("2026-09-18"))
                    VisitTimeInput(time, { time = it })
                }
            }
        }

        val dateField = compose.onNodeWithTag("field-appointment-service-date-yyyy-mm-dd").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val timeField = compose.onNodeWithTag("field-appointment-time").performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val dateAction = compose.onNodeWithTag("appointment-date-picker").fetchSemanticsNode().boundsInRoot
        val timeAction = compose.onNodeWithTag("appointment-time-picker").fetchSemanticsNode().boundsInRoot
        assertEquals(dateField.height, timeField.height, 0.1f)
        assertEquals(dateAction.width, timeAction.width, 0.1f)
        assertEquals(dateAction.height, timeAction.height, 0.1f)
        compose.onNodeWithTag("appointment-date-weekday", useUnmergedTree = true).assertTextContains("Sat")
        compose.onNodeWithTag("field-appointment-service-date-yyyy-mm-dd").performTextReplacement("2026-09-20")
        compose.onNodeWithTag("appointment-date-weekday", useUnmergedTree = true).assertTextContains("Sun")
        compose.onNodeWithTag("field-appointment-time").performTextReplacement("09:30")
        compose.onNodeWithTag("field-appointment-time").assertTextContains("09:30")
    }

    @Test
    fun followUpTypeChoicesStayEqualWidthAndOnOneRowAtLargeFont() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                V16ServiceTheme {
                    V16ServiceChoicePair(
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
