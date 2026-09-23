package com.v16studio.serviceloop.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.ServiceLoopApplication
import com.v16studio.serviceloop.data.ServiceLoopPeerTrustStore
import com.v16studio.serviceloop.data.TechnicianIdCodec
import com.v16studio.serviceloop.data.TrustedServiceLoopIdEntity
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDenseNavigableRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSecondaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextButtonAdapter as TextButton
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun TeamWorkspaceScreen(nav: NavHostController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { (context.applicationContext as ServiceLoopApplication).container.database }
    val trustStore = remember(database) { ServiceLoopPeerTrustStore(database) }
    val role = LocalTeamRole.current
    var localId by remember { mutableStateOf<String?>(null) }
    var trustedIds by remember { mutableStateOf(emptyList<TrustedServiceLoopIdEntity>()) }
    var adding by remember { mutableStateOf(false) }
    var peerId by remember { mutableStateOf("") }
    var friendlyName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<TrustedServiceLoopIdEntity?>(null) }
    var removing by remember { mutableStateOf<TrustedServiceLoopIdEntity?>(null) }
    var editName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { trustStore.localIdentity().technicianId to trustStore.trustedIds() }
            }.onSuccess { (id, rows) -> localId = id; trustedIds = rows }
                .onFailure { error = it.message ?: "Could not load this device's ServiceLoop IDs" }
        }
    }
    LaunchedEffect(Unit) { reload() }

    LazyColumn(
        modifier.fillMaxWidth().testTag("home-team"),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Your role", style = MaterialTheme.typography.titleLarge)
            val option = TEAM_ROLE_OPTIONS.first { it.role == role }
            Text(option.title, style = MaterialTheme.typography.titleMedium)
            ServiceLoopDenseNavigableRow("Role & identity", context = "Change how ServiceLoop is arranged for you.", leadingIcon = ServiceLoopIcons.TeamRole, modifier = Modifier.testTag("team-role-identity")) { nav.navigate("dispatch/settings") }
        }
        item {
            Text("Your ID", style = MaterialTheme.typography.titleLarge)
            Text(if (role == TeamRole.COORDINATOR) "Coordinator ID" else "Technician ID", style = MaterialTheme.typography.titleMedium)
            Text(localId?.let(TechnicianIdCodec::display).orEmpty(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("team-local-id"))
            ServiceLoopSecondaryButton("Copy ID", {
                localId?.let { id ->
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ServiceLoop ID", id))
                }
            }, Modifier.fillMaxWidth().testTag("team-copy-id"), enabled = localId != null)
        }
        item {
            Text("Trusted IDs", style = MaterialTheme.typography.titleLarge)
            Text("Only ServiceLoop files from trusted IDs can be imported.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (trustedIds.isEmpty()) Text("No trusted IDs yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(trustedIds, key = { it.peerId }) { entry ->
            Column(Modifier.fillMaxWidth().testTag("trusted-id-${entry.peerId}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium)
                Text(TechnicianIdCodec.display(entry.peerId), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ editing = entry; editName = entry.name; error = null }, Modifier.weight(1f).testTag("trusted-id-rename")) { Text("Rename") }
                    TextButton({ removing = entry }, Modifier.weight(1f).testTag("trusted-id-remove")) { Text("Remove") }
                }
            }
        }
        item {
            if (!adding) ServiceLoopPrimaryButton("Add trusted ID", { adding = true; error = null }, Modifier.fillMaxWidth().testTag("team-add-trusted-id"))
            else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(peerId, { peerId = it }, label = { Text("ServiceLoop ID") }, modifier = Modifier.fillMaxWidth().testTag("trusted-id-input"), singleLine = true)
                OutlinedTextField(friendlyName, { friendlyName = it }, label = { Text("Friendly name") }, modifier = Modifier.fillMaxWidth().testTag("trusted-name-input"), singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                ServiceLoopPrimaryButton("Save trusted ID", {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { trustStore.add(peerId, friendlyName) } }
                            .onSuccess { peerId = ""; friendlyName = ""; adding = false; error = null; reload() }
                            .onFailure { error = it.message }
                    }
                }, Modifier.fillMaxWidth().testTag("trusted-id-save"), enabled = TechnicianIdCodec.normalize(peerId) != null && friendlyName.trim().isNotEmpty())
                TextButton({ adding = false; peerId = ""; friendlyName = "" }, Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
        item {
            Text("Share & receive", style = MaterialTheme.typography.titleLarge)
            ServiceLoopDenseNavigableRow("Import shared data", context = "Import a ServiceLoop file from a trusted ID.", leadingIcon = ServiceLoopIcons.ArrowCircleDown, modifier = Modifier.testTag("team-import")) { nav.navigate("import") }
            ServiceLoopDenseNavigableRow("Export / share data", context = "Share ServiceLoop files and export readable data.", leadingIcon = ServiceLoopIcons.ArrowCircleUp, modifier = Modifier.testTag("team-export")) { nav.navigate("data-transfer/export") }
            ServiceLoopDenseNavigableRow("Verify ServiceLoop file", context = "Check a file without changing local data.", leadingIcon = ServiceLoopIcons.CheckCircle, modifier = Modifier.testTag("team-verify")) { nav.navigate("data-transfer/verify") }
        }
        if (role.workspaceCapabilities.canUseCoordinatorTools) {
            item { WorkspaceHomeActions(nav) }
        }
    }

    editing?.let { entry ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Rename trusted ID") },
            text = { Column { Text(TechnicianIdCodec.display(entry.peerId)); OutlinedTextField(editName, { editName = it }, label = { Text("Friendly name") }, singleLine = true); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
            confirmButton = { TextButton({ scope.launch { runCatching { withContext(Dispatchers.IO) { trustStore.rename(entry.peerId, editName) } }.onSuccess { editing = null; error = null; reload() }.onFailure { error = it.message } } }, Modifier.testTag("trusted-id-rename-confirm"), enabled = editName.trim().isNotEmpty()) { Text("Save") } },
            dismissButton = { TextButton({ editing = null }) { Text("Cancel") } },
        )
    }
    removing?.let { entry ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove trust?") },
            text = { Text("Files from ${entry.name} will no longer be importable until this ID is trusted again.") },
            confirmButton = { TextButton({ scope.launch { runCatching { withContext(Dispatchers.IO) { trustStore.remove(entry.peerId) } }.onSuccess { removing = null; reload() }.onFailure { error = it.message } } }, Modifier.testTag("trusted-id-remove-confirm")) { Text("Remove trust") } },
            dismissButton = { TextButton({ removing = null }) { Text("Cancel") } },
        )
    }
}
