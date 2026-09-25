package com.v16studio.v16service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.printToString
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.v16studio.v16service.data.DataTransferCodec
import com.v16studio.v16service.data.DataTransferFamily
import com.v16studio.v16service.data.V16ServicePeerTrustStore
import com.v16studio.v16service.data.CustomerEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import com.v16studio.v16service.ui.DISPATCH_PREFS
import com.v16studio.v16service.ui.V16ServiceApp
import com.v16studio.v16service.ui.V16ServiceViewModel
import com.v16studio.v16service.ui.TEAM_ROLE
import com.v16studio.v16service.ui.TeamRole
import com.v16studio.v16service.ui.setTeamRole
import com.v16studio.v16service.ui.theme.V16ServiceTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking

class B049ExportCenterUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val prefs get() = compose.activity.getSharedPreferences(DISPATCH_PREFS, 0)
    private var previous: String? = null
    private val testPeers = mutableSetOf<String>()
    private val testFiles = mutableListOf<File>()
    private var collisionCustomerId: String? = null

    @After fun restoreRole() {
        prefs.edit().apply { if (previous == null) remove(TEAM_ROLE) else putString(TEAM_ROLE, previous) }.commit()
        testFiles.forEach(File::delete)
        val app = compose.activity.application as V16ServiceApplication
        collisionCustomerId?.let { id -> app.container.database.openHelper.writableDatabase.execSQL("DELETE FROM customers WHERE id=?", arrayOf(id)) }
        runBlocking { testPeers.forEach { app.container.database.dispatchDao().deleteTrustedV16ServiceId(it) } }
    }

    private fun open(route: String, role: TeamRole, dark: Boolean = false) {
        compose.runOnUiThread {
            if (previous == null) previous = prefs.getString(TEAM_ROLE, null)
            compose.activity.setTeamRole(role)
            val application = compose.activity.application as V16ServiceApplication
            val viewModel = V16ServiceViewModel(application.container.repository) {}
            compose.activity.setContent { V16ServiceTheme(dark) { V16ServiceApp(viewModel, route) } }
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
        compose.onNodeWithText("Create V16 Service file").assertIsDisplayed()
        compose.onNodeWithTag("export-center").performScrollToNode(hasTestTag("export-format-readable"))
        compose.onNodeWithTag("export-format-native").assertIsDisplayed()
        compose.onNodeWithTag("export-format-readable").assertIsDisplayed()
        compose.onNodeWithTag("export-format-readable").performClick()
        compose.onNodeWithTag("export-center").performScrollToNode(hasTestTag("export-center-create"))
        compose.onNodeWithText("Create readable ZIP").assertIsDisplayed()
        compose.onNodeWithTag("export-center").performScrollToNode(hasTestTag("export-from-date-picker"))
        compose.onNodeWithTag("export-from-date-picker").assertIsDisplayed()
        open("data-transfer/export", TeamRole.EMPLOYEE)
        compose.onNodeWithTag("workspace-unavailable").assertIsDisplayed()
        compose.onNodeWithTag("export-center").assertDoesNotExist()
    }

    @Test fun aggregateRequiresOneCustomerAndImageCleanupOffersRealPolicy() {
        open("aggregate-report/new", TeamRole.COORDINATOR)
        compose.onNodeWithTag("aggregate-report-new").assertIsDisplayed()
        compose.onNodeWithTag("aggregate-generate").assertIsNotEnabled()
        compose.onNodeWithText("Choose a Customer").assertIsDisplayed()
        compose.onNodeWithTag("aggregate-report-new").performScrollToNode(hasTestTag("aggregate-from-date-picker"))
        compose.onNodeWithTag("aggregate-from-date-picker").assertIsDisplayed()
        open("image-cleanup", TeamRole.COORDINATOR)
        compose.onNodeWithTag("image-cleanup").assertIsDisplayed()
        compose.onNodeWithTag("image-cleanup").performScrollToNode(hasTestTag("image-retention-one_month"))
        compose.onNodeWithTag("image-retention-one_month").assertIsDisplayed()
        compose.onNodeWithTag("image-cleanup").performScrollToNode(hasTestTag("save-image-cleanup"))
        compose.onNodeWithTag("save-image-cleanup").assertIsDisplayed()
        compose.onNodeWithTag("run-image-cleanup").assertIsDisplayed()
        val saveBottom = compose.onNodeWithTag("save-image-cleanup").fetchSemanticsNode().boundsInRoot.bottom
        val runTop = compose.onNodeWithTag("run-image-cleanup").fetchSemanticsNode().boundsInRoot.top
        assert(runTop > saveBottom)
    }

    @Test fun roleSettingsUseBothSelectionIconsAndCoordinatorNote() {
        open("dispatch/settings", TeamRole.COORDINATOR)
        compose.onNodeWithTag("team-role-settings").assertIsDisplayed()
        compose.onNodeWithTag("team-role-COORDINATOR-selection-icon", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("team-role-SOLO-selection-icon", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Coordinator tools are available from Home > Team page.").assertIsDisplayed()
        open("settings", TeamRole.COORDINATOR)
        compose.onNodeWithTag("settings-section-app").assertIsDisplayed()
        compose.onNodeWithTag("settings-section-data").assertIsDisplayed()
        compose.onNodeWithTag("settings-appearance").assertIsDisplayed()
        compose.onNodeWithTag("settings-about-version").assertIsDisplayed()
    }

    @Test fun backupRecoveryOffersNinetyDaysAndEraseUsesDestructiveControl() {
        open("data-recovery", TeamRole.COORDINATOR)
        compose.onNodeWithTag("data-recovery").assertIsDisplayed()
        compose.onNodeWithTag("backup-reminder-presets-90").assertIsDisplayed()
        compose.onNodeWithTag("data-recovery").performScrollToNode(hasTestTag("erase-device-data"))
        compose.onNodeWithTag("erase-device-data").assertIsDisplayed()
    }

    @Test fun backupCreateAndRestoreUseTheCurrentAndroidDocumentPickers() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        open("backup/create", TeamRole.COORDINATOR)
        compose.onNodeWithText("Passphrase · Required · 12+ characters").performTextInput("B051 device recovery passphrase")
        compose.onNodeWithText("Confirm passphrase · Required").performTextInput("B051 device recovery passphrase")
        compose.onNode(hasText("Create backup") and hasClickAction()).performClick()
        compose.waitUntil(30_000) {
            isDocumentsUi(automation.rootInActiveWindow) && visibleSystemText(automation.rootInActiveWindow)
                .any { Regex("v16service-\\d{4}-\\d{2}-\\d{2}\\.v16backup").containsMatchIn(it) }
        }
        val createLabels = visibleSystemText(automation.rootInActiveWindow)
        assertTrue("CreateDocument should propose a dated .v16backup filename: $createLabels", createLabels.any {
            Regex("v16service-\\d{4}-\\d{2}-\\d{2}\\.v16backup").containsMatchIn(it)
        })
        automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitUntil(10_000) { automation.rootInActiveWindow?.packageName?.toString() == compose.activity.packageName }

        open("backup/restore", TeamRole.COORDINATOR)
        compose.onNodeWithText("Choose backup").performClick()
        compose.waitUntil(15_000) { isDocumentsUi(automation.rootInActiveWindow) }
        automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitUntil(10_000) { automation.rootInActiveWindow?.packageName?.toString() == compose.activity.packageName }
    }

    private fun isDocumentsUi(root: AccessibilityNodeInfo?): Boolean =
        root?.packageName?.toString()?.contains("documentsui", ignoreCase = true) == true

    private fun visibleSystemText(root: AccessibilityNodeInfo?): List<String> {
        if (root == null) return emptyList()
        val result = mutableListOf<String>()
        fun visit(node: AccessibilityNodeInfo) {
            node.text?.toString()?.let(result::add)
            node.contentDescription?.toString()?.let(result::add)
            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
        }
        visit(root)
        return result
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

    @Test fun importSharedDataShowsAdditiveFamilyPreviewAndBlocksReferenceConflicts() {
        val app = compose.activity.application as V16ServiceApplication
        val database = app.container.database
        val reference = "CU-UI-${UUID.randomUUID().toString().take(8)}"
        fun packageBytes(exporter: String, name: String): ByteArray {
            val row = JSONObject().put("originWorkspaceId", exporter).put("sourceEntityId", "source-$reference")
                .put("reference", reference).put("name", name).put("customerType", "STANDARD").put("state", "ACTIVE")
            val register = JSONObject().put("version", 1).put("customers", JSONArray().put(row))
                .put("contacts", JSONArray()).put("sites", JSONArray()).put("equipment", JSONArray())
            return DataTransferCodec.encode(exporter, families = mapOf(DataTransferFamily.REGISTER to register.toString().toByteArray()), sourceWorkspaceId = exporter)
        }
        fun incoming(bytes: ByteArray, event: Int) {
            val exporter = DataTransferCodec.decode(bytes).exporterId
            kotlinx.coroutines.runBlocking { V16ServicePeerTrustStore(database).add(exporter, "UI source") }
            testPeers += exporter
            val file = File(compose.activity.cacheDir, "export-center/ui-$event.v16service").apply { parentFile?.mkdirs(); writeBytes(bytes) }
            testFiles += file
            val uri = FileProvider.getUriForFile(compose.activity, "${compose.activity.packageName}.reports", file)
            compose.runOnUiThread {
                if (previous == null) previous = prefs.getString(TEAM_ROLE, null)
                compose.activity.setTeamRole(TeamRole.COORDINATOR)
                val viewModel = V16ServiceViewModel(app.container.repository) {}
                compose.activity.setContent { V16ServiceTheme { V16ServiceApp(viewModel, incomingV16ServiceSync = uri.toString(), incomingV16ServiceSyncEvent = event) } }
            }
        }

        val first = packageBytes(com.v16studio.v16service.data.TechnicianIdCodec.generate(), "UI customer")
        incoming(first, 1)
        awaitImportPreview("first package")
        compose.onNodeWithText("Selected data · additive merge").assertIsDisplayed()
        compose.onNodeWithText("This copies the selected records into this workspace. Missing records in the file never delete local data.", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("sync-data-transfer-import").assertIsEnabled()
        compose.onNodeWithText("Full workspace", substring = true).assertDoesNotExist()

        collisionCustomerId = "ui-collision-${UUID.randomUUID().toString().take(8)}"
        kotlinx.coroutines.runBlocking { database.v16ServiceDao().insertCustomers(listOf(CustomerEntity(requireNotNull(collisionCustomerId), reference, "Local version"))) }
        val conflict = packageBytes(com.v16studio.v16service.data.TechnicianIdCodec.generate(), "Different source version")
        incoming(conflict, 2)
        awaitImportPreview("conflicting package")
        compose.onNodeWithTag("sync-data-transfer-import").assertIsNotEnabled()
        compose.onNodeWithText("Review conflicts").assertIsDisplayed()
    }

    private fun awaitImportPreview(label: String) {
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Selected data · additive merge").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("v16-service-import").performScrollToNode(hasTestTag("sync-data-transfer-import"))
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("sync-data-transfer-import").fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            throw AssertionError("No DATA_TRANSFER preview for $label. Current Compose tree:\n${compose.onRoot(useUnmergedTree = true).printToString()}", failure)
        }
    }

    @Test fun aggregateVisitSelectionRendersInLightAndDark() {
        listOf(false, true).forEach { dark ->
            open("aggregate-report/new", TeamRole.COORDINATOR, dark)
            compose.onNodeWithTag("aggregate-report-new").assertIsDisplayed()
            val customer = runBlocking { (compose.activity.application as V16ServiceApplication).container.database.v16ServiceDao().allCustomers().first() }
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
