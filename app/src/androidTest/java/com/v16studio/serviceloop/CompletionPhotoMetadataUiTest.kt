package com.v16studio.serviceloop

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.domain.*
import com.v16studio.serviceloop.ui.CompletionReviewScreen
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.UiState
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompletionPhotoMetadataUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun pendingAndFailedPhotoChoiceBothKeepFinalizeDisabled() {
        val repository = object : ServiceLoopRepository {
            override suspend fun home() = HomeSummary(null, null, null, null, null, null, null, 0, null, null, 0, 0)
            override suspend fun equipment(id: String): EquipmentDetail? = null
            override suspend fun equipmentList(): List<EquipmentSummary> = emptyList()
            override suspend fun customerList(): List<CustomerSummary> = emptyList()
            override suspend fun inspection(workItemId: String): InspectionDraft? = null
            override suspend fun completionLines(visitId: String): List<CompletionLine> = emptyList()
            override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?): Long = 0
        }
        val viewModel = ServiceLoopViewModel(repository) {}
        val photo = PhotoEntry("photo-1", "missing.jpg", "image/jpeg", 1, false, "Photo evidence")
        val line = CompletionLine(
            workItemId = "work-1", equipmentName = null, equipmentReference = null, serviceName = "Annual service",
            outcome = "PERFORMED", fulfillmentEligibility = FulfillmentEligibility.HISTORY_ONLY,
            fulfillsCurrentObligation = false, dueDate = null, proposedNextDueDate = null, workPerformed = "Completed",
            photos = listOf(photo),
        )
        val profile = BusinessProfile("Service Co", "Alex Dobre", zoneId = "Europe/Bucharest")
        val screenState = mutableStateOf(UiState(visitReportIdentity = profile, photoMetadataPendingIds = setOf("photo-1")))

        compose.setContent {
            ServiceLoopTheme {
                CompletionReviewScreen(
                    "visit-1", listOf(line), profile,
                    screenState.value,
                    androidx.compose.foundation.layout.PaddingValues(), viewModel, rememberNavController(),
                )
            }
        }
        compose.onNodeWithTag("photo-selection-saving-photo-1").assertIsDisplayed()
        compose.onNodeWithTag("finalize-record").assertIsNotEnabled()

        compose.runOnUiThread {
            screenState.value = UiState(
                visitReportIdentity = profile,
                photoMetadataErrorId = "photo-1",
                photoMetadataErrorMessages = mapOf("photo-1" to "Photo selection was not saved. Change the choice to retry."),
            )
        }
        compose.waitForIdle()
        compose.onNodeWithTag("photo-selection-error-photo-1").assertIsDisplayed()
        compose.onNodeWithTag("finalize-record").assertIsNotEnabled()
    }
}
