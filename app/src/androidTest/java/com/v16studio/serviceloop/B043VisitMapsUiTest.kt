package com.v16studio.serviceloop

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.compose.rememberNavController
import com.v16studio.serviceloop.calendar.VisitCalendarState
import com.v16studio.serviceloop.domain.VisitDetail
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.UiState
import com.v16studio.serviceloop.ui.VisitDetailScreen
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import com.v16studio.serviceloop.ui.visitMapsIntent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class B043VisitMapsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun visitMapsUsesCapturedAddressAndBlankAddressIsDisabled() {
        val application = compose.activity.application as ServiceLoopApplication
        val viewModel = ServiceLoopViewModel(application.container.repository) {}
        val captured = "17 Snapshot Road, Bucharest"
        compose.runOnUiThread {
            val intent = visitMapsIntent(captured)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("geo:0,0?q=17%20Snapshot%20Road%2C%20Bucharest", intent.dataString)
            compose.activity.setContent {
                ServiceLoopTheme(false) {
                    VisitDetailScreen(visit(captured), PaddingValues(), state(), viewModel, rememberNavController())
                }
            }
        }
        compose.onNodeWithTag("visit-maps-link").assertIsDisplayed().assertIsEnabled()

        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme(false) {
                    VisitDetailScreen(visit(""), PaddingValues(), state(), viewModel, rememberNavController())
                }
            }
        }
        compose.onNodeWithTag("visit-maps-link").assertIsDisplayed().assertIsNotEnabled()
    }

    private fun visit(address: String) = VisitDetail(
        "visit-maps", "V-MAPS", "BOOKED", "customer", "Customer", "site", "Site", address,
        "2026-09-21", null, null, emptyList(), null,
    )

    private fun state() = UiState(
        loading = false,
        visitCalendarState = VisitCalendarState("Calendar integration is off"),
    )
}
