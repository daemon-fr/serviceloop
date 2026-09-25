package com.v16studio.v16service

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
import com.v16studio.v16service.data.V16ServiceSyncEnvelopeCodec
import com.v16studio.v16service.data.V16ServiceSyncManifest
import com.v16studio.v16service.data.V16ServiceSyncSectionDeclaration
import com.v16studio.v16service.data.TechnicianIdCodec
import com.v16studio.v16service.ui.DISPATCH_PREFS
import com.v16studio.v16service.ui.DetailScaffold
import com.v16studio.v16service.ui.ImportDispatchPackageScreen
import com.v16studio.v16service.ui.V16ServiceApp
import com.v16studio.v16service.ui.V16ServiceViewModel
import com.v16studio.v16service.ui.TEAM_ROLE
import com.v16studio.v16service.ui.TeamRole
import com.v16studio.v16service.ui.setTeamRole
import com.v16studio.v16service.ui.theme.V16ServiceTheme
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
        val uri = uriFor("wrong-purpose.v16service", envelope("FULL_WORKSPACE"))
        val application = compose.activity.application as V16ServiceApplication
        val viewModel = V16ServiceViewModel(application.container.repository) {}
        compose.runOnUiThread {
            previousRole = compose.activity.getSharedPreferences(DISPATCH_PREFS, 0).getString(TEAM_ROLE, null)
            compose.activity.setTeamRole(TeamRole.SUBCONTRACTOR)
            compose.activity.setContent {
                V16ServiceTheme {
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
        compose.onNodeWithText("This is a V16 Service workspace file, but Import assigned work accepts work assignment packages.", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("import-error-alternate-action").assertIsDisplayed()
        compose.onNodeWithText("Open Import shared data").assertIsDisplayed()
    }

    @Test fun unreadableAssignedFileExplainsHowToRecover() {
        val uri = uriFor("unreadable.v16service", byteArrayOf(1, 2, 3, 4))
        val application = compose.activity.application as V16ServiceApplication
        val viewModel = V16ServiceViewModel(application.container.repository) {}
        compose.runOnUiThread {
            previousRole = compose.activity.getSharedPreferences(DISPATCH_PREFS, 0).getString(TEAM_ROLE, null)
            compose.activity.setTeamRole(TeamRole.SUBCONTRACTOR)
            compose.activity.setContent {
                V16ServiceTheme {
                    val nav = rememberNavController()
                    DetailScaffold("Import assigned work", nav) { padding ->
                        ImportDispatchPackageScreen(padding, nav, viewModel, incomingUri = uri.toString())
                    }
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("import-error-dialog").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Could not read this work assignment").assertIsDisplayed()
        compose.onNodeWithText("This file is unreadable or is not a valid V16 Service file.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Choose a supported V16 Service work assignment file and try again.", substring = true).assertIsDisplayed()
        captureRenderedErrorSurface("import-assigned-unreadable-error.png")
        compose.runOnUiThread {
            compose.activity.setContent {
                V16ServiceTheme(darkTheme = true, window = compose.activity.window) {
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

    @Test fun sharedImportExplainsUnsupportedV16ServiceVersion() {
        val uri = uriFor("future-version.v16service", unsupportedVersionEnvelope())
        val application = compose.activity.application as V16ServiceApplication
        val viewModel = V16ServiceViewModel(application.container.repository) {}
        compose.runOnUiThread {
            previousRole = compose.activity.getSharedPreferences(DISPATCH_PREFS, 0).getString(TEAM_ROLE, null)
            compose.activity.setTeamRole(TeamRole.COORDINATOR)
            compose.activity.setContent {
                V16ServiceTheme { V16ServiceApp(viewModel, incomingV16ServiceSync = uri.toString(), incomingV16ServiceSyncEvent = 4901) }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("import-error-dialog").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("V16 Service file version not supported").assertIsDisplayed()
        compose.onNodeWithText("Ask the sender to export it from a compatible V16 Service version", substring = true).assertIsDisplayed()
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
        val manifest = V16ServiceSyncManifest(
            syncId = UUID.randomUUID().toString(),
            title = "UI import error fixture",
            purpose = purpose,
            generatedAt = Instant.now().toString(),
            sections = listOf(V16ServiceSyncSectionDeclaration("fixture", 1, "fixture.json")),
            exporterId = TechnicianIdCodec.generate(),
        )
        return V16ServiceSyncEnvelopeCodec.encode(manifest, mapOf("fixture" to "{}".toByteArray()))
    }

    private fun unsupportedVersionEnvelope(): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("{\"format\":\"V16ServiceSync\",\"formatVersion\":99}".toByteArray())
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
