package com.v16studio.v16service

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.v16studio.v16service.data.V16ServiceRepository
import com.v16studio.v16service.domain.CustomerDetail
import com.v16studio.v16service.domain.CustomerType
import com.v16studio.v16service.domain.HomeSummary
import com.v16studio.v16service.domain.EquipmentSummary
import com.v16studio.v16service.domain.InspectionDraft
import com.v16studio.v16service.domain.CompletionLine
import com.v16studio.v16service.domain.CustomerSummary
import com.v16studio.v16service.domain.DueService
import com.v16studio.v16service.domain.ResponseDisposition
import com.v16studio.v16service.ui.CustomerEditorScreen
import com.v16studio.v16service.ui.DetailScaffold
import com.v16studio.v16service.ui.V16ServiceViewModel
import com.v16studio.v16service.ui.UiState
import com.v16studio.v16service.ui.theme.V16ServiceTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class B048CustomerContactsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun existingCustomerEditorShowsContactsAndDisabledOneTimeControl() {
        val customer = CustomerDetail(
            id = "customer-b048",
            reference = "CU-B048",
            name = "B048 UI fixture",
            contactName = "",
            phone = "",
            email = "",
            privateNote = "",
            sites = emptyList(),
            equipment = emptyList(),
            openFollowUps = emptyList(),
            recentContacts = emptyList(),
            customerType = CustomerType.STANDARD,
            canMarkOneTime = false,
            oneTimeBlockReason = "This customer has recurring service plans.",
        )
        val viewModel = V16ServiceViewModel(EmptyRepository()) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                V16ServiceTheme {
                    val nav = rememberNavController()
                    DetailScaffold("Edit customer", nav) { padding ->
                        CustomerEditorScreen(customer, padding, UiState(), viewModel, nav)
                    }
                }
            }
        }

        compose.onNodeWithTag("customer-editor-additional-contacts").performScrollTo()
        compose.onNodeWithTag("customer-editor-list-contacts").assertIsDisplayed()
        compose.onNodeWithTag("customer-editor-add-contact").assertIsDisplayed().performClick()
        compose.onNodeWithText("Add customer contact").assertIsDisplayed()
        compose.onNodeWithText("Person or label (optional)").assertIsDisplayed()
        compose.onNodeWithTag("customer-editor-contact-channel-EMAIL").assertIsDisplayed()
        compose.onNodeWithText("Contact value · Required").assertIsDisplayed()
        compose.onNodeWithTag("customer-editor-contact-save-contact").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithTag("customer-type-control").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("customer-one-time-checkbox").assertIsNotEnabled()
        compose.onNodeWithTag("customer-one-time-block-reason").performScrollTo().assertIsDisplayed()
    }

    private class EmptyRepository : V16ServiceRepository {
        override suspend fun home() = HomeSummary(null, null, null, null, null, null, null, 0, null, null, 0, 0)
        override suspend fun equipment(id: String) = null
        override suspend fun equipmentList(): List<EquipmentSummary> = emptyList()
        override suspend fun customerList(): List<CustomerSummary> = emptyList()
        override suspend fun inspection(workItemId: String): InspectionDraft? = null
        override suspend fun completionLines(visitId: String): List<CompletionLine> = emptyList()
        override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?) = 0L
        override fun observeDueServices(): Flow<List<DueService>> = emptyFlow()
    }
}
