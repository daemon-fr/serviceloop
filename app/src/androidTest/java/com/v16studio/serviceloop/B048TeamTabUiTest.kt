package com.v16studio.serviceloop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class B048TeamTabUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun teamTabShowsIdentityTrustAndSharingEntryPoints() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("root-home").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home-tab-TEAM").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("home-team").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("team-local-id").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("home-team").assertIsDisplayed()
        compose.onNodeWithText("Your role").assertIsDisplayed()
        compose.onNodeWithTag("team-role-identity").assertIsDisplayed()
        compose.onNodeWithText("Your ID").assertIsDisplayed()
        compose.onNodeWithTag("team-local-id").assertIsDisplayed()
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithTag("team-copy-id").assertIsEnabled() }.isSuccess
        }
        compose.onNodeWithTag("team-copy-id").assertIsDisplayed().assertIsEnabled()

        compose.onNodeWithTag("home-team").performScrollToNode(hasText("Trusted IDs"))
        compose.onNodeWithText("Trusted IDs").assertIsDisplayed()
        compose.onNodeWithTag("team-add-trusted-id").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("trusted-id-input").assertIsDisplayed()
        compose.onNodeWithTag("trusted-name-input").assertIsDisplayed()
        compose.onNodeWithTag("trusted-id-save").assertIsDisplayed()

        compose.onNodeWithTag("home-team").performScrollToNode(hasText("Share & receive"))
        compose.onNodeWithText("Share & receive").assertIsDisplayed()
        compose.onNodeWithTag("team-import").assertIsDisplayed()
        compose.onNodeWithTag("team-export").assertIsDisplayed()
        compose.onNodeWithTag("team-verify").assertIsDisplayed()
    }
}
