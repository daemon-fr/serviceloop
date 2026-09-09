package com.v16studio.serviceloop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class CalendarSettingsUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun settingsShowsTruthfulNonDestructiveCalendarState(){
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Calendar ·",substring=true).performClick()
        compose.onNodeWithTag("calendar-settings").assertIsDisplayed()
        compose.onNodeWithText("ServiceLoop remains the source of truth.",substring=true).assertIsDisplayed()
        compose.onNodeWithTag("calendar-status").assertIsDisplayed()
    }
}
