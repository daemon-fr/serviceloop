package com.v16studio.v16service

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.v16studio.v16service.data.V16ServiceRepository
import com.v16studio.v16service.domain.PhotoEntry
import com.v16studio.v16service.ui.V16ServiceViewModel
import com.v16studio.v16service.ui.ServicePhotoManagementScreen
import com.v16studio.v16service.ui.UiState
import com.v16studio.v16service.ui.theme.V16ServiceTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class B048PhotoManagementUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var fixtureRoot: File? = null

    @After
    fun removeOnlyFixtureFiles() {
        compose.runOnUiThread { compose.activity.setContent {} }
        compose.waitForIdle()
        fixtureRoot?.deleteRecursively()
    }

    @Test
    fun galleryOpensViewerSelectsMultipleAndConfirmsWithoutDeletingOnCancel() {
        val root = File(compose.activity.filesDir, "b048-photo-gallery-${System.nanoTime()}").apply { mkdirs() }
        fixtureRoot = root
        val firstPath = createImage(root, "first.png")
        val secondPath = createImage(root, "second.png")
        val photos = listOf(
            PhotoEntry("first", firstPath.relativeTo(compose.activity.filesDir).path.replace('\\', '/'), "image/png", firstPath.length(), true, "First evidence"),
            PhotoEntry("second", secondPath.relativeTo(compose.activity.filesDir).path.replace('\\', '/'), "image/png", secondPath.length(), false, "Second evidence"),
        )
        val state = UiState(photos = photos, fieldEvidenceWorkItemId = "work-b048")
        val viewModel = V16ServiceViewModel(EmptyRepository()) {}

        compose.runOnUiThread {
            compose.activity.setContent {
                V16ServiceTheme {
                    ServicePhotoManagementScreen("work-b048", state, PaddingValues(), viewModel)
                }
            }
        }

        compose.onNodeWithTag("service-photo-management").assertIsDisplayed()
        compose.onNodeWithTag("service-photo-tile-first").assertIsDisplayed().performClick()
        compose.onNodeWithTag("service-photo-viewer").assertIsDisplayed()
        compose.onNodeWithText("Photo 1 of 2").assertIsDisplayed()
        compose.onNodeWithTag("close-service-photo-viewer").performClick()

        compose.onNodeWithTag("select-service-photo-first").performClick()
        compose.onNodeWithTag("select-service-photo-second").performClick()
        compose.onNodeWithTag("selected-photo-count").assertIsDisplayed()
        compose.onNodeWithText("2 selected").assertIsDisplayed()
        compose.onNodeWithTag("include-selected-photos").assertIsDisplayed()
        compose.onNodeWithTag("keep-selected-photos-private").assertIsDisplayed()
        compose.onNodeWithTag("delete-selected-photos").assertIsDisplayed().performClick()
        compose.onNodeWithText("Delete 2 photos?").assertIsDisplayed()
        compose.onNodeWithTag("confirm-delete-selected-photos").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithText("2 selected").assertIsDisplayed()
        compose.onNodeWithTag("delete-selected-photos").performClick()
        compose.onNodeWithTag("confirm-delete-selected-photos").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("photo-delete-error").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("photo-delete-error").assertIsDisplayed()
        compose.onNodeWithTag("confirm-delete-selected-photos").assertIsEnabled()
        assertTrue(firstPath.isFile && secondPath.isFile)
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("2 selected").assertIsDisplayed()
        compose.onNodeWithTag("clear-photo-selection").performClick()
        assertTrue(compose.onAllNodesWithTag("selected-photo-count").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("service-photo-tile-first").assertIsDisplayed()
        compose.onNodeWithTag("service-photo-tile-second").assertIsDisplayed()
        assertTrue(firstPath.isFile && secondPath.isFile)
    }

    private fun createImage(root: File, name: String): File {
        val file = File(root, name)
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(33, 129, 151)) }
        FileOutputStream(file).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        return file
    }

    private class EmptyRepository : V16ServiceRepository {
        override suspend fun home() = com.v16studio.v16service.domain.HomeSummary(null, null, null, null, null, null, null, 0, null, null, 0, 0)
        override suspend fun equipment(id: String) = null
        override suspend fun equipmentList(): List<com.v16studio.v16service.domain.EquipmentSummary> = emptyList()
        override suspend fun customerList(): List<com.v16studio.v16service.domain.CustomerSummary> = emptyList()
        override suspend fun inspection(workItemId: String): com.v16studio.v16service.domain.InspectionDraft? = null
        override suspend fun completionLines(visitId: String): List<com.v16studio.v16service.domain.CompletionLine> = emptyList()
        override suspend fun saveResponse(workItemId: String, questionId: String, disposition: com.v16studio.v16service.domain.ResponseDisposition, value: String?, reason: String?) = 0L
        override fun observeDueServices(): Flow<List<com.v16studio.v16service.domain.DueService>> = emptyFlow()
    }
}
