package com.v16studio.serviceloop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.domain.*
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import org.junit.Rule
import org.junit.Test

class ReminderSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun remindersDestinationShowsDefaultsAndRequiresOneSummaryDay() {
        val repository = FakeRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        compose.setContent { ServiceLoopApp(viewModel) }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Reminders").performClick()
        compose.onNodeWithTag("reminder-settings").assertIsDisplayed()
        compose.onNodeWithTag("due-horizon-14").assertIsDisplayed()
        java.time.DayOfWeek.entries.forEach { compose.onNodeWithTag("summary-day-${it.name.lowercase()}").performClick() }
        compose.onNodeWithText("Select at least one summary day").assertIsDisplayed()
        compose.onNodeWithTag("reminder-settings").performScrollToNode(hasTestTag("save-reminders"))
        compose.onNodeWithTag("save-reminders").assertIsNotEnabled()
        compose.onNodeWithTag("reminder-settings").performScrollToNode(hasTestTag("summary-day-monday"))
        compose.onNodeWithTag("summary-day-monday").performClick()
        compose.onNodeWithTag("reminder-settings").performScrollToNode(hasTestTag("save-reminders"))
        compose.onNodeWithTag("save-reminders").assertIsEnabled()
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
