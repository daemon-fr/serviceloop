package com.v16studio.serviceloop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.platform.LocalDensity
import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.domain.*
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReminderSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun remindersDestinationShowsDefaultsAndRequiresOneSummaryDay() {
        val repository = FakeRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        var density = 1f
        compose.setContent { ServiceLoopTheme { density = LocalDensity.current.density; ServiceLoopApp(viewModel) } }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Reminders").performClick()
        compose.onNodeWithTag("reminder-settings").assertIsDisplayed()
        compose.onNodeWithTag("due-horizon-14").assertIsDisplayed()
        val days = java.time.DayOfWeek.entries.map { compose.onNodeWithTag("summary-day-${it.name.lowercase()}").fetchSemanticsNode().boundsInRoot }
        assertTrue(days.all { it.width >= 48f * density && it.height >= 48f * density })
        assertTrue(days.drop(1).all { abs(it.width - days.first().width) < 2f })
        compose.onNodeWithText("MO").assertIsDisplayed()
        compose.onNodeWithTag("summary-day-monday").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("summary-day-monday").performClick().assertIsOn()
        java.time.DayOfWeek.entries.forEach { compose.onNodeWithTag("summary-day-${it.name.lowercase()}").performClick() }
        compose.onNodeWithText("Select at least one summary day").assertIsDisplayed()
        compose.onNodeWithTag("reminder-settings").performScrollToNode(hasTestTag("appointment-lead-60"))
        listOf("1h", "3h", "6h", "12h", "24h", "48h").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithTag("reminder-settings").performScrollToNode(hasTestTag("save-reminders"))
        compose.onNodeWithTag("save-reminders").assertIsNotEnabled()
        compose.onNodeWithTag("reminder-settings").performScrollToNode(hasTestTag("summary-day-monday"))
        compose.onNodeWithTag("summary-day-monday").performClick()
        compose.onNodeWithTag("reminder-settings").performScrollToNode(hasTestTag("save-reminders"))
        compose.onNodeWithTag("save-reminders").assertIsEnabled()
    }

    @Test fun dueHorizonUsesCompactSingleRowRadioPresets() {
        val repository = FakeRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        compose.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel) } }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Reminders").performClick()

        val tags = listOf("due-horizon-1", "due-horizon-7", "due-horizon-14", "due-horizon-30")
        tags.forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        compose.onNodeWithTag("due-horizon-14").assertIsSelected()
        compose.onNodeWithTag("due-horizon-1").assertIsNotSelected()
        val settingsWidth = compose.onNodeWithTag("reminder-settings").fetchSemanticsNode().boundsInRoot.width
        val bounds = tags.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        assertTrue(bounds.all { it.width < settingsWidth / 2f })
        assertTrue(bounds.map { it.top }.distinct().size == 1)

        compose.onNodeWithTag("due-horizon-7").performClick().assertIsSelected()
        compose.onNodeWithTag("due-horizon-14").assertIsNotSelected()
    }

    private class FakeRepository : ServiceLoopRepository {
        var preferences = ReminderPreferences()
        override suspend fun home() = HomeSummary(null,null,null,null,null,null,null,0,null,null,0,0)
        override suspend fun equipment(id: String) = null
        override suspend fun equipmentList(): List<EquipmentSummary> = emptyList()
        override suspend fun customerList(): List<CustomerSummary> = emptyList()
        override suspend fun inspection(workItemId: String): InspectionDraft? = null
        override suspend fun completionLines(visitId: String): List<CompletionLine> = emptyList()
        override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?) = 0L
        override suspend fun reminderPreferences() = preferences
        override suspend fun saveReminderPreferences(value: ReminderPreferences): Long { value.validate(); preferences = value; return 1L }
    }
}
