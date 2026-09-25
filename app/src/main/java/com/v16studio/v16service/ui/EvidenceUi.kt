package com.v16studio.v16service.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.v16studio.v16service.domain.*
import com.v16studio.v16service.ui.designsystem.LocalV16ServiceTokens
import com.v16studio.v16service.ui.designsystem.V16ServiceActionStack
import com.v16studio.v16service.ui.designsystem.V16ServiceAdaptiveActionRow
import com.v16studio.v16service.ui.designsystem.V16ServiceButtonAdapter as Button
import com.v16studio.v16service.ui.designsystem.V16ServiceCardAdapter as Card
import com.v16studio.v16service.ui.designsystem.V16ServiceEntityRecord
import com.v16studio.v16service.ui.designsystem.V16ServiceOutlinedButtonAdapter as OutlinedButton
import com.v16studio.v16service.ui.designsystem.V16ServicePrivateLabel
import com.v16studio.v16service.ui.designsystem.V16ServicePrimaryButton
import com.v16studio.v16service.ui.designsystem.V16ServiceSurfaceCard
import com.v16studio.v16service.ui.designsystem.V16ServiceTextButtonAdapter as TextButton
import com.v16studio.v16service.ui.designsystem.V16ServiceTextFieldAdapter as OutlinedTextField
import com.v16studio.v16service.ui.designsystem.V16ServiceUiTokens
import com.v16studio.v16service.ui.icons.V16ServiceIcon
import com.v16studio.v16service.ui.icons.V16ServiceIcons
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun EquipmentLinkScreen(workItemId: String, context: EquipmentLinkContext?, padding: PaddingValues, state: UiState, viewModel: V16ServiceViewModel, nav: NavHostController) {
    val capabilities = LocalWorkspaceCapabilities.current
    var showAdd by rememberSaveable(workItemId) { mutableStateOf(false) }
    var name by rememberSaveable(workItemId) { mutableStateOf("") }
    var identifier by rememberSaveable(workItemId) { mutableStateOf("") }
    var make by rememberSaveable(workItemId) { mutableStateOf("") }
    var model by rememberSaveable(workItemId) { mutableStateOf("") }
    var serial by rememberSaveable(workItemId) { mutableStateOf("") }
    var note by rememberSaveable(workItemId) { mutableStateOf("") }
    if (context == null || context.workItemId != workItemId) return DailyEmpty(padding, "Loading equipment")
    EditorColumn(padding, state) {
        item {
            DailyHeading("Identify equipment")
            Text(
                if (capabilities.canManageRegister) {
                    "${context.siteName} · Link this task to registered equipment or create a new item."
                } else {
                    "${context.siteName} · Link this task to registered equipment."
                },
            )
        }
        if (context.equipment.isNotEmpty()) {
            item { Text("Registered equipment", fontWeight = FontWeight.Bold) }
            items(context.equipment, key = { "link-equipment-${it.id}" }) { equipment ->
                V16ServiceEntityRecord("${equipment.name} · ${equipment.reference}", equipment.technicianIdentifier.orEmpty(), "Link to this task", modifier = Modifier.testTag("link-equipment-${equipment.id}")) {
                    viewModel.linkWorkItemEquipment(workItemId, equipment.id) { nav.popBackStack() }
                }
            }
        } else item { Text("No registered equipment exists at this site yet.") }
        if (capabilities.canManageRegister) item { OutlinedButton({ showAdd = !showAdd }, Modifier.fillMaxWidth().testTag("link-add-equipment")) { Text(if (showAdd) "Cancel new equipment" else "Add equipment") } }
        if (capabilities.canManageRegister && showAdd) item {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("New equipment", fontWeight = FontWeight.Bold)
                    DailyField(name, { name = it }, "Equipment name · Required")
                    DailyField(identifier, { identifier = it }, "Technician identifier")
                    DailyField(make, { make = it }, "Make")
                    DailyField(model, { model = it }, "Model")
                    DailyField(serial, { serial = it }, "Serial")
                    LongTextEditor(note, { note = it }, "Private equipment note", true)
                    V16ServicePrimaryButton("Save and link equipment", { viewModel.createAndLinkEquipment(workItemId, EquipmentInput(name, identifier, make, model, serial, note)) { nav.popBackStack() } }, Modifier.fillMaxWidth().testTag("save-and-link-equipment"), enabled = name.isNotBlank() && !state.operationInProgress)
                }
            }
        }
    }
}

@Composable
internal fun FieldEvidenceScreen(workItemId: String, state: UiState, padding: PaddingValues, viewModel: V16ServiceViewModel, nav: NavHostController) {
    var description by rememberSaveable { mutableStateOf("") }; var quantity by rememberSaveable { mutableStateOf("1") }; var unit by rememberSaveable { mutableStateOf("item") }; var include by rememberSaveable { mutableStateOf(false) }; var caption by rememberSaveable { mutableStateOf("") }; var followTitle by rememberSaveable { mutableStateOf("") }; var followDue by rememberSaveable { mutableStateOf(state.businessDate.plusDays(7).toString()) }; var followNote by rememberSaveable { mutableStateOf("") }; val context = LocalContext.current
    val scope=rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) scope.launch { val bytes=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{it.readDailyBounded(MAX_PHOTO_PICK_BYTES)}}; if(bytes!=null)viewModel.savePhoto(workItemId,bytes,uri.lastPathSegment,context.contentResolver.getType(uri)?:"image/jpeg",include,caption) else viewModel.reportOperationFailure("Photo not added — choose a readable image smaller than 30 MB") } }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> val path=cameraPath; if(success&&path!=null)scope.launch{val file=File(path);val bytes=withContext(Dispatchers.IO){if(file.isFile&&file.length() in 1..MAX_PHOTO_PICK_BYTES.toLong())file.readBytes()else null};if(bytes!=null)viewModel.savePhoto(workItemId,bytes,file.name,"image/jpeg",include,caption)else viewModel.reportOperationFailure("Photo not added — camera output was missing or too large");withContext(Dispatchers.IO){file.delete()}};cameraPath=null }
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.section)) {
        item { DailyHeading("Parts used"); DailyField(description, { description=it }, "Description"); DailyField(quantity, { quantity=it }, "Positive quantity"); DailyField(unit, { unit=it }, "Unit"); Button({ viewModel.addPart(workItemId, description, quantity, unit); description="" }, enabled = description.isNotBlank() && !state.operationInProgress, modifier = Modifier.fillMaxWidth()) { Text("Save part") } }
        items(state.parts) { part -> Text("${part.description} · ${part.quantity} ${part.unit}") }
        item { DailyHeading("Photographs"); Text("Maximum 20 per machine and 100 per visit; optimized to a 2,560 px longest edge."); Row(verticalAlignment = Alignment.CenterVertically) { com.v16studio.v16service.ui.designsystem.V16ServiceCheckbox(include, { include=it }, contentDescription = "Include selected photo in customer report"); Text("Include selected photo in customer report") }; DailyField(caption, { caption = it }, "Customer-visible caption"); V16ServiceActionStack { Button({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth()) { V16ServiceIcon(V16ServiceIcons.Photo,null,Modifier.size(V16ServiceUiTokens.Size.icon));Spacer(Modifier.width(V16ServiceUiTokens.Space.sm));Text("Choose photo") }; OutlinedButton({ val directory=File(context.cacheDir,"camera-staging").apply{mkdirs()}; val file=File(directory,"capture-${System.currentTimeMillis()}.jpg"); cameraPath=file.absolutePath; camera.launch(FileProvider.getUriForFile(context,"${context.packageName}.reports",file)) },Modifier.fillMaxWidth()){V16ServiceIcon(V16ServiceIcons.Camera,null,Modifier.size(V16ServiceUiTokens.Size.icon));Spacer(Modifier.width(V16ServiceUiTokens.Space.sm));Text("Take photo") } }; Text("Saved with this Service. Photos selected for the customer report are included when the Visit is finalized.") }
        items(state.photos, key = { it.id }) { photo -> PhotoEvidenceCard(photo, context) }
        item { DailyHeading("Corrective follow-up"); DailyField(followTitle,{followTitle=it},"Follow-up title"); DailyField(followDue,{followDue=it},"Due date"); LongTextEditor(followNote,{followNote=it},"PRIVATE planning note",true); OutlinedButton({viewModel.createCorrectiveFollowUp(workItemId,followTitle,followDue,followNote);followTitle=""},enabled=followTitle.isNotBlank()&&runCatching{LocalDate.parse(followDue)}.isSuccess,modifier=Modifier.fillMaxWidth()){Text("Create corrective follow-up")} }
        state.inspection?.takeIf { it.workItemId == workItemId }?.let { draft -> item { Button({nav.navigate("review/${draft.visitId}")},Modifier.fillMaxWidth().testTag("field-review-completion")){Text("Review completion")} } }
    }
}

@Composable
internal fun ServiceEvidenceContent(workItemId: String, state: UiState, viewModel: V16ServiceViewModel, editingEnabled: Boolean, onManagePhotos: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var addingPart by rememberSaveable(workItemId) { mutableStateOf(false) }
    var partToEdit by rememberSaveable(workItemId) { mutableStateOf<String?>(null) }
    var description by rememberSaveable(workItemId, partToEdit) { mutableStateOf(state.parts.firstOrNull { it.id == partToEdit }?.description.orEmpty()) }
    var quantity by rememberSaveable(workItemId, partToEdit) { mutableStateOf(state.parts.firstOrNull { it.id == partToEdit }?.quantity ?: "1") }
    var unit by rememberSaveable(workItemId, partToEdit) { mutableStateOf(state.parts.firstOrNull { it.id == partToEdit }?.unit ?: "item") }
    var followOpen by rememberSaveable(workItemId) { mutableStateOf(false) }
    var followTitle by rememberSaveable(workItemId) { mutableStateOf("") }
    var followDue by rememberSaveable(workItemId) { mutableStateOf(state.businessDate.plusDays(7).toString()) }
    var followNote by rememberSaveable(workItemId) { mutableStateOf("") }
    var cameraPath by rememberSaveable(workItemId) { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readDailyBounded(MAX_PHOTO_PICK_BYTES) } }
            if (bytes != null) viewModel.savePhoto(workItemId, bytes, uri.lastPathSegment, context.contentResolver.getType(uri) ?: "image/jpeg", false, null)
            else viewModel.reportOperationFailure("Photo not added — choose a readable image smaller than 30 MB")
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = cameraPath
        if (success && path != null) scope.launch {
            val file = File(path)
            val bytes = withContext(Dispatchers.IO) { if (file.isFile && file.length() in 1..MAX_PHOTO_PICK_BYTES.toLong()) file.readBytes() else null }
            if (bytes != null) viewModel.savePhoto(workItemId, bytes, file.name, "image/jpeg", false, null)
            else viewModel.reportOperationFailure("Photo not added — camera output was missing or too large")
            withContext(Dispatchers.IO) { file.delete() }
        }
        cameraPath = null
    }
    Column(verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.section)) {
        V16ServiceSurfaceCard(modifier = Modifier.fillMaxWidth().testTag("service-parts")) {
            Text("Parts used", style = MaterialTheme.typography.titleLarge)
            state.parts.forEach { part ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${part.description} · ${part.quantity} ${part.unit}", Modifier.weight(1f))
                    if (editingEnabled) TextButton({ partToEdit = part.id; addingPart = true }, modifier = Modifier.testTag("edit-part-${part.id}")) { Text("Edit") }
                }
            }
            if (editingEnabled && !addingPart) OutlinedButton({ partToEdit = null; addingPart = true }, Modifier.fillMaxWidth().testTag("add-part")) { Text("Add part") }
            if (editingEnabled && addingPart) {
                DailyField(description, { description = it }, "Description")
                DailyField(quantity, { quantity = it }, "Positive quantity")
                DailyField(unit, { unit = it }, "Unit")
                Button({
                    val id = partToEdit
                    if (id == null) viewModel.addPart(workItemId, description, quantity, unit) { addingPart = false; partToEdit = null }
                    else viewModel.updatePart(workItemId, id, description, quantity, unit) { addingPart = false; partToEdit = null }
                }, enabled = description.isNotBlank() && unit.isNotBlank() && runCatching { java.math.BigDecimal(quantity) > java.math.BigDecimal.ZERO }.getOrDefault(false) && !state.operationInProgress, modifier = Modifier.fillMaxWidth()) { Text(if (partToEdit == null) "Add part" else "Update part") }
                partToEdit?.let { id -> TextButton({ viewModel.removePart(workItemId, id); addingPart = false; partToEdit = null }, Modifier.testTag("remove-part-$id")) { Text("Remove part") } }
                TextButton({ addingPart = false; partToEdit = null }) { Text("Cancel") }
            }
        }
        V16ServiceSurfaceCard(modifier = Modifier.fillMaxWidth().testTag("service-photos")) {
            Text("Photos", style = MaterialTheme.typography.titleLarge)
            state.photos.forEach { photo -> ServicePhotoCard(workItemId, photo, context, viewModel, editingEnabled, state) }
            if (editingEnabled) {
                V16ServiceAdaptiveActionRow(listOf(
                    { OutlinedButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.testTag("choose-photo")) { Text("Choose photo") } },
                    { OutlinedButton({
                        val directory = File(context.cacheDir, "camera-staging").apply { mkdirs() }
                        val file = File(directory, "capture-${System.currentTimeMillis()}.jpg")
                        cameraPath = file.absolutePath
                        camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.reports", file))
                    }, Modifier.testTag("take-photo")) { Text("Take photo") } },
                    { OutlinedButton(onManagePhotos, Modifier.testTag("manage-service-photos")) { Text("Manage photos") } },
                ))
            }
        }
        V16ServiceSurfaceCard(modifier = Modifier.fillMaxWidth().testTag("service-follow-up")) {
            Text("Corrective follow-up", style = MaterialTheme.typography.titleLarge)
            Text("Create future work only when it remains needed.")
            state.serviceFollowUps.forEach { follow -> Text("${follow.reference} · ${follow.title} · ${follow.state.lowercase()} · Due ${follow.dueDate}") }
            if (editingEnabled && !followOpen) OutlinedButton({ followOpen = true }, Modifier.fillMaxWidth().testTag("add-follow-up")) { Text("Add follow-up") }
            if (editingEnabled && followOpen) {
                DailyField(followTitle, { followTitle = it }, "Follow-up title")
                DailyField(followDue, { followDue = it }, "Due date (YYYY-MM-DD)")
                LongTextEditor(followNote, { followNote = it }, "PRIVATE planning note", true)
                OutlinedButton({ viewModel.createCorrectiveFollowUp(workItemId, followTitle, followDue, followNote) { followOpen = false; followTitle = ""; followNote = "" } }, enabled = followTitle.isNotBlank() && runCatching { LocalDate.parse(followDue) }.isSuccess && !state.operationInProgress, modifier = Modifier.fillMaxWidth()) { Text("Create follow-up") }
                TextButton({ followOpen = false }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun ServicePhotoCard(workItemId: String, photo: PhotoEntry, context: Context, viewModel: V16ServiceViewModel, editingEnabled: Boolean, state: UiState) {
    val initialCaption = state.inspection?.takeIf { it.workItemId == workItemId }?.rawInputs?.get(com.v16studio.v16service.domain.ServiceDraftFieldKeys.photoCaption(photo.id)) ?: photo.caption.orEmpty()
    var caption by rememberSaveable(photo.id, initialCaption) { mutableStateOf(initialCaption) }
    var include by rememberSaveable(photo.id, photo.includedInReport) { mutableStateOf(photo.includedInReport) }
    var confirmRemoval by rememberSaveable(photo.id) { mutableStateOf(false) }
    var fullView by rememberSaveable(photo.id) { mutableStateOf(false) }
    PhotoEvidenceCard(photo, context, onOpen = { fullView = true })
    if (state.photoMetadataPendingId == photo.id || photo.id in state.photoMetadataPendingIds) Text("Saving photo details…", modifier = Modifier.testTag("photo-saving-${photo.id}"))
    (state.photoMetadataErrorMessages[photo.id] ?: if (state.photoMetadataErrorId == photo.id) "Photo details not saved — change the report choice to retry." else null)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (editingEnabled) {
        OutlinedTextField(caption, { caption = it; viewModel.schedulePhotoCaption(workItemId, photo.id, it) }, label = { Text("Caption") }, modifier = Modifier.fillMaxWidth().testTag("photo-caption-${photo.id}"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.v16studio.v16service.ui.designsystem.V16ServiceCheckbox(include, { selected -> include = selected; viewModel.setPhotoReportInclusion(workItemId, photo.id, selected) }, modifier = Modifier.testTag("photo-report-${photo.id}"), contentDescription = "Include ${photo.caption ?: "photo"} in customer report")
            Text("Include in customer report")
        }
        TextButton({ confirmRemoval = true }, Modifier.testTag("remove-photo-${photo.id}")) { Text("Remove photo") }
    }
    if (confirmRemoval) AlertDialog(onDismissRequest = { confirmRemoval = false }, title = { Text("Remove photo?") }, text = { Text("The photo will be removed from this Service and V16 Service's stored copy deleted. Your source image is unaffected.") }, confirmButton = { TextButton({ viewModel.removePhoto(workItemId, photo.id); confirmRemoval = false }, Modifier.testTag("confirm-remove-photo-${photo.id}")) { Text("Remove photo") } }, dismissButton = { TextButton({ confirmRemoval = false }) { Text("Cancel") } })
    if (fullView) AlertDialog(onDismissRequest = { fullView = false }, confirmButton = { TextButton({ fullView = false }) { Text("Close") } }, text = { val bitmap = remember(photo.relativePath) { BitmapFactory.decodeFile(File(context.filesDir, photo.relativePath).absolutePath) }; if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = photo.caption ?: "Service photo", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) else Text("Image unavailable") })
}

@Composable
private fun PhotoEvidenceCard(photo: PhotoEntry, context: Context, onOpen: (() -> Unit)? = null) {
    val colors = LocalV16ServiceTokens.current
    val bitmap = remember(photo.relativePath, photo.byteSize) {
        BitmapFactory.decodeFile(File(context.filesDir, photo.relativePath).absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 })
    }
    V16ServiceSurfaceCard(modifier = if (onOpen == null) Modifier else Modifier.clickable(onClick = onOpen).testTag("photo-${photo.id}")) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(88.dp).background(colors.photoMat, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = photo.caption?.takeIf(String::isNotBlank) ?: "Service evidence photograph", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Text("Image unavailable", color = colors.errorInk, style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (photo.includedInReport) Text("Included in customer report", fontWeight = FontWeight.SemiBold)
                else V16ServicePrivateLabel("Private evidence", style = MaterialTheme.typography.titleSmall)
                Text(photo.caption?.takeIf(String::isNotBlank) ?: "No caption", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
