package com.v16studio.serviceloop

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.core.content.FileProvider
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.ServiceLoopSyncEnvelopeCodec
import com.v16studio.serviceloop.data.ServiceLoopSyncManifest
import com.v16studio.serviceloop.data.ServiceLoopSyncSectionDeclaration
import com.v16studio.serviceloop.data.TechnicianIdCodec
import com.v16studio.serviceloop.ui.DISPATCH_PREFS
import com.v16studio.serviceloop.ui.DetailScaffold
import com.v16studio.serviceloop.ui.ImportDispatchPackageScreen
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.TEAM_ROLE
import com.v16studio.serviceloop.ui.TeamRole
import com.v16studio.serviceloop.ui.setTeamRole
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Rule
import org.junit.Test

class B049ImportErrorUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var previousRole: String? = null
    private val files = mutableListOf<File>()

    @After fun restoreState() {
        compose.activity.getSharedPreferences(DISPATCH_PREFS, 0).edit().apply {
            if (previousRole == null) remove(TEAM_ROLE) else putString(TEAM_ROLE, previousRole)
        }.commit()
        files.forEach(File::delete)
    }

    @Test fun wrongPurposeAssignmentFileOffersSharedImportRoute() {
        val uri = uriFor("wrong-purpose.slsync", envelope("FULL_WORKSPACE"))
        val application = compose.activity.application as ServiceLoopApplication
        val viewModel = ServiceLoopViewModel(application.container.repository) {}
        compose.runOnUiThread {
            previousRole = compose.activity.getSharedPreferences(DISPATCH_PREFS, 0).getString(TEAM_ROLE, null)
            compose.activity.setTeamRole(TeamRole.SUBCONTRACTOR)
            compose.activity.setContent {
                ServiceLoopTheme {
                    val nav = rememberNavController()
                    DetailScaffold("Import assigned work", nav) { padding ->
                        ImportDispatchPackageScreen(padding, nav, viewModel, incomingUri = uri.toString())
                    }
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("import-error-dialog").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("import-error-dialog").assertIsDisplayed()
        compose.onNodeWithText("This file is for a different import").assertIsDisplayed()
        compose.onNodeWithText("This is a ServiceLoop workspace file, but Import assigned work accepts work assignment packages.", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("import-error-alternate-action").assertIsDisplayed()
        compose.onNodeWithText("Open Import shared data").assertIsDisplayed()
    }

    @Test fun unreadableAssignedFileExplainsHowToRecover() {
        val uri = uriFor("unreadable.slsync", byteArrayOf(1, 2, 3, 4))
        val application = compose.activity.application as ServiceLoopApplication
        val viewModel = ServiceLoopViewModel(application.container.repository) {}
        compose.runOnUiThread {
            previousRole = compose.activity.getSharedPreferences(DISPATCH_PREFS, 0).getString(TEAM_ROLE, null)
            compose.activity.setTeamRole(TeamRole.SUBCONTRACTOR)
            compose.activity.setContent {
                ServiceLoopTheme {
                    val nav = rememberNavController()
                    DetailScaffold("Import assigned work", nav) { padding ->
                        ImportDispatchPackageScreen(padding, nav, viewModel, incomingUri = uri.toString())
                    }
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("import-error-dialog").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Could not read this work assignment").assertIsDisplayed()
        compose.onNodeWithText("This file is unreadable or is not a valid ServiceLoop file.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Choose a supported ServiceLoop work assignment file and try again.", substring = true).assertIsDisplayed()
        captureRenderedErrorSurface("import-assigned-unreadable-error.png")
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme(darkTheme = true, window = compose.activity.window) {
                    val nav = rememberNavController()
                    DetailScaffold("Import assigned work", nav) { padding ->
                        ImportDispatchPackageScreen(padding, nav, viewModel, incomingUri = uri.toString())
                    }
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("import-error-dialog").fetchSemanticsNodes().isNotEmpty() }
        captureRenderedErrorSurface("import-assigned-unreadable-error-dark.png")
    }

    @Test fun sharedImportExplainsUnsupportedServiceLoopVersion() {
        val uri = uriFor("future-version.slsync", unsupportedVersionEnvelope())
        val application = compose.activity.application as ServiceLoopApplication
        val viewModel = ServiceLoopViewModel(application.container.repository) {}
        compose.runOnUiThread {
            previousRole = compose.activity.getSharedPreferences(DISPATCH_PREFS, 0).getString(TEAM_ROLE, null)
            compose.activity.setTeamRole(TeamRole.COORDINATOR)
            compose.activity.setContent {
                ServiceLoopTheme { ServiceLoopApp(viewModel, incomingServiceLoopSync = uri.toString(), incomingServiceLoopSyncEvent = 4901) }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("import-error-dialog").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ServiceLoop file version not supported").assertIsDisplayed()
        compose.onNodeWithText("Ask the sender to export it from a compatible ServiceLoop version", substring = true).assertIsDisplayed()
    }

    private fun uriFor(name: String, bytes: ByteArray): android.net.Uri {
        val file = File(compose.activity.cacheDir, "export-center/$name").apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }
        files += file
        return FileProvider.getUriForFile(compose.activity, "${compose.activity.packageName}.reports", file)
    }

    private fun envelope(purpose: String): ByteArray {
        val manifest = ServiceLoopSyncManifest(
            syncId = UUID.randomUUID().toString(),
            title = "UI import error fixture",
            purpose = purpose,
            generatedAt = Instant.now().toString(),
            sections = listOf(ServiceLoopSyncSectionDeclaration("fixture", 1, "fixture.json")),
            exporterId = TechnicianIdCodec.generate(),
        )
        return ServiceLoopSyncEnvelopeCodec.encode(manifest, mapOf("fixture" to "{}".toByteArray()))
    }

    private fun unsupportedVersionEnvelope(): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("{\"format\":\"ServiceLoopSync\",\"formatVersion\":99}".toByteArray())
            zip.closeEntry()
        }
    }.toByteArray()

    private fun captureRenderedErrorSurface(fileName: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "b049-owner-ui-import-${System.nanoTime()}")
            .apply { check(exists() || mkdirs()) }
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        FileOutputStream(File(directory, fileName)).use {
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot.recycle()
        android.util.Log.i("B049_RENDERED", "Rendered evidence directory: ${directory.absolutePath}")
    }
}
