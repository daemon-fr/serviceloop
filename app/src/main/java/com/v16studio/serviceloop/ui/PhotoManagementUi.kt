package com.v16studio.serviceloop.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.v16studio.serviceloop.domain.PhotoEntry
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopActionStack
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopCheckbox
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import java.io.File

@Composable
internal fun ServicePhotoManagementScreen(workItemId: String, state: UiState, padding: PaddingValues, viewModel: ServiceLoopViewModel) {
    val context = LocalContext.current
    val photos = state.photos.takeIf { state.fieldEvidenceWorkItemId == workItemId }.orEmpty()
    var selected by remember(workItemId) { mutableStateOf<Set<String>>(emptySet()) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var batchBusy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(padding).testTag("service-photo-management")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Service photos", style = MaterialTheme.typography.headlineSmall)
            if (selected.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${selected.size} selected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("selected-photo-count"))
                    TextButton(onClick = { selected = emptySet() }, modifier = Modifier.testTag("clear-photo-selection")) { Text("Clear selection") }
                }
                ServiceLoopActionStack {
                    OutlinedButton(onClick = { viewModel.setPhotoReportInclusions(workItemId, selected.toList(), true) { failure -> batchBusy = false; message = failure ?: "Included selected photos in the customer report." } ; batchBusy = true }, enabled = !batchBusy, modifier = Modifier.fillMaxWidth().testTag("include-selected-photos")) { Text("Include in customer report") }
                    OutlinedButton(onClick = { viewModel.setPhotoReportInclusions(workItemId, selected.toList(), false) { failure -> batchBusy = false; message = failure ?: "Excluded selected photos from the customer report." } ; batchBusy = true }, enabled = !batchBusy, modifier = Modifier.fillMaxWidth().testTag("exclude-selected-photos")) { Text("Exclude from customer report") }
                    OutlinedButton(onClick = { viewModel.setPhotosPrivate(workItemId, selected.toList()) { failure -> batchBusy = false; message = failure ?: "Kept selected photos private." } ; batchBusy = true }, enabled = !batchBusy, modifier = Modifier.fillMaxWidth().testTag("keep-selected-photos-private")) { Text("Keep private") }
                    OutlinedButton(onClick = { message = null; confirmDelete = true }, enabled = !batchBusy, modifier = Modifier.fillMaxWidth().testTag("delete-selected-photos")) { Text("Delete selected") }
                }
            } else {
                Text("Tap a photo to view it. Select photos to manage report visibility or remove ServiceLoop’s stored copies.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            message?.let { Text(it, color = if (it.contains("not saved", true) || it.contains("could not", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("photo-management-message")) }
            state.error?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (photos.isEmpty()) Text("No photos for this Service.", modifier = Modifier.testTag("service-photo-empty"))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(132.dp),
            modifier = Modifier.fillMaxSize().testTag("service-photo-grid"),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(photos, key = { it.id }) { photo ->
                val isSelected = photo.id in selected
                val bitmap = remember(photo.relativePath, photo.byteSize) {
                    BitmapFactory.decodeFile(File(context.filesDir, photo.relativePath).absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 })
                }
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f)
                        .combinedClickable(
                            onClick = { if (selected.isNotEmpty()) selected = if (isSelected) selected - photo.id else selected + photo.id else viewerIndex = photos.indexOfFirst { it.id == photo.id } },
                            onLongClick = { selected = selected + photo.id },
                        )
                        .testTag("service-photo-tile-${photo.id}"),
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                        if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = photo.caption?.takeIf(String::isNotBlank) ?: "Service photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Image unavailable", style = MaterialTheme.typography.bodySmall) }
                    }
                    ServiceLoopCheckbox(
                        checked = isSelected,
                        onCheckedChange = { checked -> selected = if (checked) selected + photo.id else selected - photo.id },
                        modifier = Modifier.align(Alignment.TopStart).testTag("select-service-photo-${photo.id}"),
                        contentDescription = "Select ${photo.caption?.takeIf(String::isNotBlank) ?: "photo"}",
                    )
                    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = .90f)).padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(when (photo.visibility) { "PRIVATE" -> "Private evidence"; "INTERNAL" -> "Internal evidence"; else -> if (photo.includedInReport) "Included in customer report" else "Service evidence" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        photo.caption?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                    }
                }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { if (!batchBusy) confirmDelete = false },
        title = { Text("Delete ${selected.size} photos?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${selected.size} ServiceLoop-stored ${if (selected.size == 1) "copy will" else "copies will"} be deleted from this Service. The original photos in your source gallery will not be affected.")
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("photo-delete-error")) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                batchBusy = true
                viewModel.removePhotos(workItemId, selected.toList()) { failure ->
                    batchBusy = false
                    if (failure == null) {
                        selected = emptySet()
                        confirmDelete = false
                        message = "Selected photos removed."
                    } else {
                        message = failure
                    }
                }
            }, enabled = !batchBusy, modifier = Modifier.testTag("confirm-delete-selected-photos")) { Text("Delete photos") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }, enabled = !batchBusy) { Text("Cancel") } },
    )

    viewerIndex?.let { start ->
        ServicePhotoViewer(photos, start, context, viewModel, workItemId) { viewerIndex = it }
    }
}

@Composable
private fun ServicePhotoViewer(photos: List<PhotoEntry>, initialIndex: Int, context: android.content.Context, viewModel: ServiceLoopViewModel, workItemId: String, onClose: (Int?) -> Unit) {
    var index by rememberSaveable(initialIndex) { mutableStateOf(initialIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))) }
    val photo = photos.getOrNull(index) ?: return
    var photoTransform by remember(photo.id) { mutableStateOf(PdfPageZoomMath.reset()) }
    var viewportSize by remember(photo.id) { mutableStateOf(IntSize.Zero) }
    val bitmap = remember(photo.relativePath, photo.byteSize) {
        BitmapFactory.decodeFile(File(context.filesDir, photo.relativePath).absolutePath, BitmapFactory.Options().apply { inSampleSize = 2 })
    }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        val nextScale = PdfPageZoomMath.clampScale(photoTransform.scale * zoom)
        val displayed = bitmap?.let { image ->
            val fit = minOf(viewportSize.width.toFloat() / image.width.coerceAtLeast(1), viewportSize.height.toFloat() / image.height.coerceAtLeast(1))
            IntSize((image.width * fit).toInt(), (image.height * fit).toInt())
        } ?: IntSize.Zero
        val bounds = PdfPageZoomMath.translationBounds(displayed.width.toFloat(), displayed.height.toFloat(), viewportSize.width.toFloat(), viewportSize.height.toFloat(), nextScale)
        photoTransform = PdfPageZoomMath.clampTranslation(photoTransform.offsetX + pan.x, photoTransform.offsetY + pan.y, bounds, nextScale)
    }
    var caption by rememberSaveable(photo.id, photo.caption) { mutableStateOf(photo.caption.orEmpty()) }

    Dialog(onDismissRequest = { onClose(null) }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("service-photo-viewer"), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Photo ${index + 1} of ${photos.size}", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { onClose(null) }, modifier = Modifier.testTag("close-service-photo-viewer")) { Text("Close") }
                }
                Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).onSizeChanged { viewportSize = it }, contentAlignment = Alignment.Center) {
                    if (bitmap != null) Image(
                        bitmap.asImageBitmap(),
                        contentDescription = photo.caption?.takeIf(String::isNotBlank) ?: "Service photo",
                        modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = photoTransform.scale, scaleY = photoTransform.scale, translationX = photoTransform.offsetX, translationY = photoTransform.offsetY).transformable(transformState, canPan = { photoTransform.scale > PdfPageTransform.MIN_PDF_PAGE_SCALE }).testTag("service-photo-viewer-image"),
                        contentScale = ContentScale.Fit,
                    ) else Text("Image unavailable")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { if (index > 0) index--; photoTransform = PdfPageZoomMath.reset() }, enabled = index > 0, modifier = Modifier.testTag("previous-service-photo")) { Text("Previous") }
                    TextButton(onClick = { photoTransform = PdfPageZoomMath.reset() }, modifier = Modifier.testTag("reset-service-photo-zoom")) { Text("Fit photo") }
                    TextButton(onClick = { if (index < photos.lastIndex) index++; photoTransform = PdfPageZoomMath.reset() }, enabled = index < photos.lastIndex, modifier = Modifier.testTag("next-service-photo")) { Text("Next") }
                }
                OutlinedTextField(caption, { caption = it; viewModel.schedulePhotoCaption(workItemId, photo.id, it) }, label = { Text("Caption") }, modifier = Modifier.fillMaxWidth().testTag("viewer-photo-caption"), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ServiceLoopCheckbox(photo.includedInReport, { include -> viewModel.setPhotoReportInclusion(workItemId, photo.id, include) }, modifier = Modifier.testTag("viewer-photo-report-toggle"), contentDescription = "Include photo in customer report")
                    Text("Include in customer report")
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
