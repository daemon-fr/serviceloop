package com.v16studio.serviceloop

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class OwnerVisualRuntimeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun rootSwitchingLeavesExactlyOneRequestedRootVisible() {
        fun assertOnlyRoot(tag: String) {
            listOf("root-home", "root-work", "root-customers").forEach { candidate ->
                if (candidate == tag) composeRule.onAllNodesWithTag(candidate).assertCountEquals(1)
                else composeRule.onAllNodesWithTag(candidate).assertCountEquals(0)
            }
        }

        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onAllNodesWithTag("root-home").assertCountEquals(1) }.isSuccess
        }
        assertOnlyRoot("root-home")

        repeat(3) {
            composeRule.onNodeWithText("Work").performClick()
            assertOnlyRoot("root-work")
            composeRule.onNodeWithText("Customers").performClick()
            assertOnlyRoot("root-customers")
            composeRule.onNodeWithText("Home").performClick()
            assertOnlyRoot("root-home")
        }

        composeRule.onNodeWithText("Customers").performClick()
        assertOnlyRoot("root-customers")
        composeRule.onNodeWithText("Work").performClick()
        assertOnlyRoot("root-work")
        composeRule.onNodeWithText("Home").performClick()
        assertOnlyRoot("root-home")
    }

    @Test
    fun inlineFindingEditsPersistsAndPreservesDestructiveTransitionSemantics() {
        composeRule.onNodeWithText("Resume visit").performClick()
        val field = composeRule.onNodeWithTag("finding-field-check-belt")
        field.performScrollTo()

        composeRule.onNodeWithTag("finding-expand-check-belt").performClick()
        composeRule.onNodeWithText("Collapse").performClick()

        field.performTextClearance()
        field.performTextInput("Belt edge wear observed during inspection")
        composeRule.onNodeWithTag("finding-save-check-belt").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("finding-save-check-belt").assertIsNotEnabled() }.isSuccess
        }

        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Resume visit").performClick()
        composeRule.onNodeWithTag("finding-field-check-belt").performScrollTo().assertTextContains("Belt edge wear observed during inspection")

        composeRule.onNodeWithTag("response-check-belt-OK").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("finding-field-check-belt").assertTextContains("Belt edge wear observed during inspection")

        composeRule.onNodeWithTag("response-check-belt-OK").performClick()
        composeRule.onNodeWithText("Discard and change").performClick()
        composeRule.onAllNodesWithTag("finding-field-check-belt").assertCountEquals(0)

        composeRule.onNodeWithTag("response-check-belt-ISSUE_FOUND").performClick()
        composeRule.onNodeWithTag("finding-field-check-belt").performTextInput("Belt edge wear observed; inspect before next use")
        composeRule.onNodeWithTag("finding-save-check-belt").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("finding-save-check-belt").assertIsNotEnabled() }.isSuccess
        }
    }
}
