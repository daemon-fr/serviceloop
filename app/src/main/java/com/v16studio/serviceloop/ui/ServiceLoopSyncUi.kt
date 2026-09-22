package com.v16studio.serviceloop.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.data.SERVICE_LOOP_SYNC_MIME
import com.v16studio.serviceloop.data.ServiceLoopSyncCodec
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopActionStack
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNotice
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNoticeKind
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopOutlinedButtonAdapter as OutlinedButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextButtonAdapter as TextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

@Composable
internal fun ServiceLoopSyncScreen(
    state: UiState,
    padding: PaddingValues,
    viewModel: ServiceLoopViewModel,
    nav: NavHostController,
    incomingUri: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBounded(ServiceLoopSyncCodec.MAX_PACKAGE_BYTES) }
                        ?: error("Unable to read the selected sync")
                }
            }.onSuccess(viewModel::validateServiceLoopSync).onFailure { failure ->
                viewModel.reportOperationFailure(failure.message ?: "Could not read this ServiceLoop sync")
            }
        }
    }
    LaunchedEffect(incomingUri) {
        incomingUri?.let { rawUri ->
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(Uri.parse(rawUri))?.use { it.readBounded(ServiceLoopSyncCodec.MAX_PACKAGE_BYTES) }
                        ?: error("Unable to read the selected sync")
                }
            }.onSuccess(viewModel::validateServiceLoopSync).onFailure { failure ->
                viewModel.reportOperationFailure(failure.message ?: "Could not read this ServiceLoop sync")
            }
        }
    }
    LazyColumn(
        Modifier.padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Import ServiceLoop sync", style = MaterialTheme.typography.headlineSmall)
                Text("A trusted full-workspace import replaces the local ServiceLoop business data. App and device settings are kept.")
                if (state.restrictedRecoveryState) ServiceLoopNotice("Recovery is restricted", "Resolve recovery before importing a workspace.", ServiceLoopNoticeKind.Error)
                state.error?.let { ServiceLoopNotice("Could not import this ServiceLoop sync", it.take(240), ServiceLoopNoticeKind.Error) }
                state.operationMessage?.let { if (state.syncPreview != null) ServiceLoopNotice("Import complete", it, ServiceLoopNoticeKind.Success) }
            }
        }
        item {
            ServiceLoopActionStack {
                OutlinedButton(
                    onClick = { open.launch(arrayOf(SERVICE_LOOP_SYNC_MIME, "application/zip", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.operationInProgress && !state.restrictedRecoveryState,
                ) { Text("Choose .slsync file") }
            }
        }
        state.syncPreview?.let { preview ->
            item {
                val manifest = preview.packageValue.manifest
                val counts = preview.counts
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(manifest.title, style = MaterialTheme.typography.titleLarge)
                    Text("Full workspace · generated ${manifest.generatedAt}")
                    Text("${counts.businessProfiles} business profile · ${counts.customers} customers · ${counts.sites} sites · ${counts.equipment} equipment")
                    Text("${counts.templates} inspection templates · ${counts.plans} service plans · ${counts.technicians} technicians · ${counts.teams} teams")
                    Text("${counts.bookedVisits} booked visits · ${counts.followUps} follow-ups · ${counts.contactNotes} contact notes · ${counts.dispatchDrafts} dispatch drafts")
                    Spacer(Modifier.height(8.dp))
                    ServiceLoopNotice("Initial workspace replacement", "This initial workspace import replaces the ServiceLoop business data on this device. App and device settings are kept.", ServiceLoopNoticeKind.Warning)
                }
            }
            item {
                ServiceLoopActionStack {
                    ServiceLoopPrimaryButton(
                        "Import workspace",
                        onClick = { viewModel.importServiceLoopSync { nav.navigateToRoot(RootDestination.HOME) } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.operationInProgress && !state.restrictedRecoveryState,
                        busy = state.operationInProgress,
                    )
                    TextButton(onClick = { viewModel.clearServiceLoopSync(); nav.popBackStack() }, modifier = Modifier.fillMaxWidth(), enabled = !state.operationInProgress) { Text("Cancel") }
                }
            }
        }
    }
}

private suspend fun InputStream.readBounded(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit) { "Selected sync is too large" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
