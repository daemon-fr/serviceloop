package com.v16studio.serviceloop

import android.content.ContentValues
import android.accessibilityservice.AccessibilityService
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.CustomerEntity
import com.v16studio.serviceloop.data.DataTransferCodec
import com.v16studio.serviceloop.data.DataTransferFamily
import com.v16studio.serviceloop.data.SERVICE_LOOP_SYNC_MIME
import com.v16studio.serviceloop.data.ServiceLoopPeerTrustStore
import com.v16studio.serviceloop.data.TechnicianIdCodec
import com.v16studio.serviceloop.ui.DISPATCH_PREFS
import com.v16studio.serviceloop.ui.TEAM_ROLE
import com.v16studio.serviceloop.ui.TeamRole
import com.v16studio.serviceloop.ui.setTeamRole
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class B049SystemHandoffUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val prefs get() = compose.activity.getSharedPreferences(DISPATCH_PREFS, 0)
    private var previousRole: String? = null
    private var fixtureDownload: Uri? = null
    private var fixtureTrustedPeer: String? = null
    private var generatedShare: java.io.File? = null

    @Before fun memberRole() {
        previousRole = prefs.getString(TEAM_ROLE, null)
        compose.runOnUiThread { compose.activity.setTeamRole(TeamRole.SUBCONTRACTOR) }
    }

    @After fun restoreRole() {
        prefs.edit().apply { if (previousRole == null) remove(TEAM_ROLE) else putString(TEAM_ROLE, previousRole) }.commit()
        fixtureDownload?.let { compose.activity.contentResolver.delete(it, null, null) }
        generatedShare?.delete()
        fixtureTrustedPeer?.let { peer ->
            runBlocking { (compose.activity.application as ServiceLoopApplication).container.database.dispatchDao().deleteTrustedServiceLoopId(peer) }
        }
    }

    @Test fun directImportOpensAndroidPickerOnceAndCancelReturnsToReview() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("home-tab-TEAM").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home-tab-TEAM").performClick()
        compose.onNodeWithTag("team-import-work").performClick()
        compose.waitUntil(10_000) { automation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui", ignoreCase = true) == true }
        assertTrue(automation.rootInActiveWindow.packageName.toString().contains("documentsui", ignoreCase = true))
        automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("dispatch-import").fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(1_500)
        compose.onNodeWithTag("dispatch-import").assertIsDisplayed()
        assertTrue(automation.rootInActiveWindow.packageName.toString().contains("serviceloop", ignoreCase = true))
    }

    @Test fun nativeCustomerExportAndGeneratedV2ImportUseRealAndroidSystemSurfaces() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val target = instrumentation.targetContext
        val app = target.applicationContext as ServiceLoopApplication
        val database = app.container.database
        compose.runOnUiThread { compose.activity.setTeamRole(TeamRole.COORDINATOR) }

        compose.waitUntil(10_000) { compose.onAllNodesWithTag("home-tab-TEAM").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home-tab-TEAM").performClick()
        compose.onNodeWithTag("home-team").assertIsDisplayed()
        compose.onNodeWithTag("home-team").performScrollToNode(hasTestTag("team-export"))
        compose.onNodeWithTag("team-export").performClick()
        compose.waitForIdle()
        if (compose.onAllNodesWithTag("export-center").fetchSemanticsNodes().isEmpty()) {
            throw AssertionError("Team export did not reach Export Center. Current Compose tree:\n${compose.onRoot(useUnmergedTree = true).printToString()}")
        }
        compose.onNodeWithTag("export-center").performScrollToNode(hasTestTag("export-format-native"))
        compose.onNodeWithTag("export-format-native").assertTextContains("ServiceLoop file (.slsync)", substring = true)
        val customer = runBlocking { database.serviceLoopDao().allCustomers().firstOrNull() }
        if (customer != null) {
            compose.onNodeWithTag("export-center").performScrollToNode(hasTestTag("export-customer"))
            compose.onNodeWithTag("export-customer").performClick()
            val customerOption = "${customer.reference} · ${customer.name}"
            compose.onNodeWithTag("export-customer-option-${customerOption.filter(Char::isLetterOrDigit).lowercase()}").performClick()
        }
        val existingShares = File(target.cacheDir, "export-center").listFiles().orEmpty().map { it.name }.toSet()
        compose.onNodeWithTag("export-center").performScrollToNode(hasTestTag("export-center-create"))
        compose.onNodeWithTag("export-center-create").performClick()
        compose.waitUntil(20_000) {
            val root = automation.rootInActiveWindow
            root != null && root.packageName?.toString() != target.packageName &&
                File(target.cacheDir, "export-center").listFiles().orEmpty().any { it.name.endsWith(".slsync") && it.name !in existingShares }
        }
        val exportSurface = requireNotNull(automation.rootInActiveWindow)
        assertNotEquals(target.packageName, exportSurface.packageName?.toString())
        generatedShare = File(target.cacheDir, "export-center").listFiles().orEmpty()
            .filter { it.name.endsWith(".slsync") && it.name !in existingShares }
            .maxByOrNull { it.lastModified() }
        assertTrue("The native export stream must use a .slsync filename", generatedShare?.name?.endsWith(".slsync") == true)
        val exportUri = FileProvider.getUriForFile(target, "${target.packageName}.reports", requireNotNull(generatedShare))
        val outgoing = com.v16studio.serviceloop.ui.serviceLoopFileShareIntent(exportUri, SERVICE_LOOP_SYNC_MIME, "ServiceLoop data transfer")
        assertEquals(android.content.Intent.ACTION_SEND, outgoing.action)
        assertEquals(SERVICE_LOOP_SYNC_MIME, outgoing.type)
        assertEquals(exportUri, outgoing.getParcelableExtra(android.content.Intent.EXTRA_STREAM))

        automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("export-center").fetchSemanticsNodes().isNotEmpty() }
        automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("home-team").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home-team").performScrollToNode(hasTestTag("team-import"))

        val exporter = TechnicianIdCodec.generate()
        fixtureTrustedPeer = exporter
        runBlocking { ServiceLoopPeerTrustStore(database).add(exporter, "B049 system handoff fixture") }
        val reference = "B049-${java.util.UUID.randomUUID().toString().take(8)}"
        val register = JSONObject().put("version", 1).put("customers", JSONArray().put(
            JSONObject().put("originWorkspaceId", exporter).put("sourceEntityId", "source-$reference")
                .put("reference", reference).put("name", "Generated native handoff fixture")
                .put("customerType", "STANDARD").put("state", "ACTIVE")
        )).put("contacts", JSONArray()).put("sites", JSONArray()).put("equipment", JSONArray())
        val packageBytes = DataTransferCodec.encode(
            exporter,
            families = mapOf(DataTransferFamily.REGISTER to register.toString().toByteArray()),
            sourceWorkspaceId = exporter,
        )
        val filename = "b049-generated-v2-${reference.lowercase()}.slsync"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, SERVICE_LOOP_SYNC_MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        fixtureDownload = requireNotNull(target.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        target.contentResolver.openOutputStream(requireNotNull(fixtureDownload), "w")!!.use { it.write(packageBytes) }
        target.contentResolver.update(requireNotNull(fixtureDownload), ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)

        compose.onNodeWithTag("team-import").performClick()
        compose.onNodeWithTag("choose-serviceloop-file").performClick()
        compose.waitUntil(15_000) {
            automation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui", ignoreCase = true) == true
        }
        val picker = requireNotNull(automation.rootInActiveWindow)
        assertTrue(picker.packageName.toString().contains("documentsui", ignoreCase = true))
        if (!visibleSystemText(picker).any { it.contains(filename, ignoreCase = true) }) {
            clickSystemNode("Show roots", automation)
            compose.waitUntil(5_000) { visibleSystemText(automation.rootInActiveWindow).any { it.contains("Downloads", ignoreCase = true) } }
            clickSystemNode("Downloads", automation)
        }
        compose.waitUntil(15_000) { visibleSystemText(automation.rootInActiveWindow).any { it.contains(filename, ignoreCase = true) } }
        val pickerActions = listOf("Open", "Select", "Use this file", "Open file")
        clickSystemNode(filename, automation)
        var followUp = visibleSystemText(automation.rootInActiveWindow).firstOrNull { text -> pickerActions.any { text.equals(it, ignoreCase = true) } }
        if (followUp != null) clickSystemNode(followUp, automation)
        val returnedToApp = runCatching {
            compose.waitUntil(20_000) { automation.rootInActiveWindow?.packageName?.toString() == target.packageName }
        }.isSuccess
        if (!returnedToApp) {
            val activeWindow = automation.rootInActiveWindow
            val safeLabels = visibleSystemText(activeWindow).filter { label ->
                label.contains(filename, ignoreCase = true) || listOf("Downloads", "Recent", "Open", "Select", "Use this file", "Open file", "Cancel").any { label.equals(it, ignoreCase = true) }
            }
            throw AssertionError("Android document picker did not return after selecting the generated file. package=${activeWindow?.packageName}; visible fixture/actions=$safeLabels; file nodes=${systemMatchSummary(filename, activeWindow)}")
        }
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("sync-data-transfer-import").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sync-data-transfer-import").assertIsEnabled()
        compose.onNodeWithText("Selected data · additive merge").assertIsDisplayed()
        compose.onNodeWithText("Register · 1", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Full workspace", substring = true).assertDoesNotExist()
        assertTrue(automation.rootInActiveWindow?.packageName?.toString()?.contains("serviceloop", ignoreCase = true) == true)
    }

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

    private fun clickSystemNode(text: String, automation: android.app.UiAutomation) {
        val root = requireNotNull(automation.rootInActiveWindow) { "No active system window while selecting $text" }
        val nodes = root.findAccessibilityNodeInfosByText(text).sortedBy { node ->
            node.viewIdResourceName?.endsWith("preview_icon") == true ||
                (node.isClickable && node.className?.toString()?.contains("FrameLayout") == true)
        }
        for (node in nodes) {
            var current: AccessibilityNodeInfo? = node
            if (current?.viewIdResourceName?.endsWith("preview_icon") == true ||
                (current?.isClickable == true && current.className?.toString()?.contains("FrameLayout") == true)
            ) current = current?.parent
            repeat(6) {
                val candidate = current ?: return@repeat
                if (candidate.isClickable && candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                current = candidate.parent
            }
        }
        error("Could not activate $text in the Android system surface")
    }

    private fun systemMatchSummary(text: String, root: AccessibilityNodeInfo?): String {
        if (root == null) return "no active root"
        return root.findAccessibilityNodeInfosByText(text).take(5).joinToString(" | ") { node ->
            val parents = mutableListOf<String>()
            var current: AccessibilityNodeInfo? = node
            repeat(5) {
                val candidate = current ?: return@repeat
                parents += "${candidate.className}#${candidate.viewIdResourceName}[click=${candidate.isClickable},long=${candidate.isLongClickable},focus=${candidate.isFocusable},actions=${candidate.actionList.map { it.id }}]"
                current = candidate.parent
            }
            parents.joinToString(" -> ")
        }.ifBlank { "no matching nodes" }
    }
}
