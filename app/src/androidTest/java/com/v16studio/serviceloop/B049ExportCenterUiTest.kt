package com.v16studio.serviceloop

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import com.v16studio.serviceloop.ui.DISPATCH_PREFS
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.TEAM_ROLE
import com.v16studio.serviceloop.ui.TeamRole
import com.v16studio.serviceloop.ui.setTeamRole
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking

class B049ExportCenterUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val prefs get() = compose.activity.getSharedPreferences(DISPATCH_PREFS, 0)
    private var previous: String? = null

    @After fun restoreRole() { prefs.edit().apply { if (previous == null) remove(TEAM_ROLE) else putString(TEAM_ROLE, previous) }.commit() }

    private fun open(route: String, role: TeamRole, dark: Boolean = false) {
        compose.runOnUiThread {
            if (previous == null) previous = prefs.getString(TEAM_ROLE, null)
            compose.activity.setTeamRole(role)
            val application = compose.activity.application as ServiceLoopApplication
            val viewModel = ServiceLoopViewModel(application.container.repository) {}
            compose.activity.setContent { ServiceLoopTheme(dark) { ServiceLoopApp(viewModel, route) } }
        }
    }

    @Test fun exportCenterShowsPresetsScopeFamiliesAndRouteGate() {
        open("data-transfer/export", TeamRole.COORDINATOR)
        compose.onNodeWithTag("export-center").assertIsDisplayed()
        compose.onNodeWithTag("export-preset").assertIsDisplayed()
        compose.onNodeWithTag("export-customer").assertIsDisplayed()
        compose.onNodeWithTag("export-site").assertIsNotEnabled()
        compose.onNodeWithTag("export-equipment").assertIsNotEnabled()
        compose.onNodeWithTag("export-center").performScrollToNode(hasTestTag("export-family-image_files"))
        compose.onNodeWithTag("export-family-image_files").assertIsDisplayed()
        compose.onNodeWithTag("export-center").performScrollToNode(hasTestTag("export-center-create"))
        compose.onNodeWithTag("export-center-create").assertIsDisplayed()
        open("data-transfer/export", TeamRole.EMPLOYEE)
        compose.onNodeWithTag("workspace-unavailable").assertIsDisplayed()
        compose.onNodeWithTag("export-center").assertDoesNotExist()
    }

    @Test fun aggregateRequiresOneCustomerAndImageCleanupOffersRealPolicy() {
        open("aggregate-report/new", TeamRole.COORDINATOR)
        compose.onNodeWithTag("aggregate-report-new").assertIsDisplayed()
        compose.onNodeWithTag("aggregate-generate").assertIsNotEnabled()
        compose.onNodeWithText("Choose a Customer").assertIsDisplayed()
        open("image-cleanup", TeamRole.COORDINATOR)
        compose.onNodeWithTag("image-cleanup").assertIsDisplayed()
        compose.onNodeWithTag("image-cleanup").performScrollToNode(hasTestTag("image-retention-one_month"))
        compose.onNodeWithTag("image-retention-one_month").assertIsDisplayed()
        compose.onNodeWithTag("image-cleanup").performScrollToNode(hasTestTag("save-image-cleanup"))
        compose.onNodeWithTag("save-image-cleanup").assertIsDisplayed()
    }

    @Test fun workResultExportRouteEnforcesReceiverRolesAndTransferHasNoDuplicateVerify() {
        listOf(TeamRole.SUBCONTRACTOR, TeamRole.EMPLOYEE, TeamRole.TEAM_LEADER).forEach { role ->
            open("work-results/export", role)
            compose.onNodeWithTag("work-results-export").assertIsDisplayed()
        }
        listOf(TeamRole.SOLO, TeamRole.COORDINATOR).forEach { role ->
            open("work-results/export", role)
            compose.onNodeWithTag("workspace-unavailable").assertIsDisplayed()
        }
        open("data-transfer", TeamRole.COORDINATOR)
        compose.onNodeWithTag("data-transfer-import").assertIsDisplayed()
        compose.onNodeWithTag("data-transfer-verify").assertDoesNotExist()
    }

    @Test fun aggregateVisitSelectionRendersInLightAndDark() {
        listOf(false, true).forEach { dark ->
            open("aggregate-report/new", TeamRole.COORDINATOR, dark)
            compose.onNodeWithTag("aggregate-report-new").assertIsDisplayed()
            val customer = runBlocking { (compose.activity.application as ServiceLoopApplication).container.database.serviceLoopDao().allCustomers().first() }
            val optionLabel = "${customer.reference} · ${customer.name}"
            compose.onNodeWithTag("aggregate-customer").performClick()
            compose.onNodeWithTag("aggregate-customer-option-${optionLabel.filter { it.isLetterOrDigit() }.lowercase()}").performClick()
            compose.onNodeWithTag("aggregate-report-new").performScrollToNode(hasText("Visits ·", substring = true))
            compose.onNodeWithText("Visits ·", substring = true).assertIsDisplayed()
            val file = java.io.File(compose.activity.getExternalFilesDir(null), "b049-aggregate-${if (dark) "dark" else "light"}.png")
            file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
