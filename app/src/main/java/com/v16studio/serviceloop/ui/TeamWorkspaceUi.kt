package com.v16studio.serviceloop.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.ServiceLoopApplication
import com.v16studio.serviceloop.data.ServiceLoopPeerTrustStore
import com.v16studio.serviceloop.data.TechnicianIdCodec
import com.v16studio.serviceloop.data.TrustedServiceLoopIdEntity
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDenseNavigableRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFieldAction
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSecondaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextButtonAdapter as TextButton
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class TeamWorkspaceContent { ROOT, ID, TRUSTED_IDS }

internal fun TeamRole.idHeading(): String = when (this) {
    TeamRole.SUBCONTRACTOR, TeamRole.EMPLOYEE -> "Your technician ID"
    TeamRole.TEAM_LEADER -> "Your Team Leader ID"
    TeamRole.COORDINATOR -> "Your Coordinator ID"
    TeamRole.SOLO -> "Your ID"
}

@Composable
internal fun TeamWorkspaceScreen(nav: NavHostController, modifier: Modifier = Modifier, content: TeamWorkspaceContent = TeamWorkspaceContent.ROOT) {
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
        modifier.fillMaxWidth().testTag(if (content == TeamWorkspaceContent.ROOT) "home-team" else "team-${content.name.lowercase()}"),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (content == TeamWorkspaceContent.ROOT) {
            if (role != TeamRole.SOLO) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Daily work exchange", style = MaterialTheme.typography.titleLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (role.workspaceCapabilities.canReceiveAssignedWork) {
                            ServiceLoopPrimaryButton("Import work package", { nav.navigate("dispatch/import/pick") }, Modifier.fillMaxWidth().testTag("team-import-work"))
                            ServiceLoopSecondaryButton("Export work results", { nav.navigate("work-results/export") }, Modifier.fillMaxWidth().testTag("team-export-results"))
                        }
                        if (role.workspaceCapabilities.canUseCoordinatorTools) {
                            ServiceLoopPrimaryButton("Dispatch work package", { nav.navigate("dispatch/create") }, Modifier.fillMaxWidth().testTag("team-dispatch"))
                            ServiceLoopSecondaryButton("Import work results", { nav.navigate("import") }, Modifier.fillMaxWidth().testTag("team-import-results"))
                            ServiceLoopSecondaryButton("Generate report", { nav.navigate("aggregate-report/new") }, Modifier.fillMaxWidth().testTag("team-generate-report"))
                        }
                    }
                }
            }
            item {
                Text("Manage team", style = MaterialTheme.typography.titleLarge)
                if (role != TeamRole.EMPLOYEE) ServiceLoopDenseNavigableRow("Business and report identity", leadingIcon = ServiceLoopIcons.Report) { nav.navigate("business-profile") }
                ServiceLoopDenseNavigableRow("Your team role", leadingIcon = ServiceLoopIcons.TeamRole, modifier = Modifier.testTag("team-role-identity")) { nav.navigate("dispatch/settings") }
                if (role != TeamRole.SOLO) {
                    ServiceLoopDenseNavigableRow(role.idHeading(), leadingIcon = ServiceLoopIcons.Copy, modifier = Modifier.testTag("team-id-row")) { nav.navigate("team/id") }
                    ServiceLoopDenseNavigableRow("Trusted IDs", leadingIcon = ServiceLoopIcons.CheckCircle, modifier = Modifier.testTag("team-trusted-ids")) { nav.navigate("team/trusted-ids") }
                }
                if (role.workspaceCapabilities.canUseCoordinatorTools) {
                    ServiceLoopDenseNavigableRow("Technicians", leadingIcon = ServiceLoopIcons.TeamRole) { nav.navigate("dispatch/technicians") }
                    ServiceLoopDenseNavigableRow("Teams and leaders", leadingIcon = ServiceLoopIcons.TeamRole) { nav.navigate("dispatch/teams") }
                }
                if (role != TeamRole.SOLO) ServiceLoopDenseNavigableRow("Import shared data", leadingIcon = ServiceLoopIcons.ArrowCircleDown, modifier = Modifier.testTag("team-import")) { nav.navigate("import") }
                if (role == TeamRole.SUBCONTRACTOR || role.workspaceCapabilities.canUseCoordinatorTools) ServiceLoopDenseNavigableRow("Export data", leadingIcon = ServiceLoopIcons.ArrowCircleUp, modifier = Modifier.testTag("team-export")) { nav.navigate("data-transfer/export") }
            }
        }
        if (content == TeamWorkspaceContent.ID) item {
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(localId?.let(TechnicianIdCodec::display).orEmpty(), modifier = Modifier.weight(1f).testTag("team-local-id"), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ServiceLoopFieldAction(accessibleName = "Copy ID", onClick = { localId?.let { id -> (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ServiceLoop ID", id)) } }, enabled = localId != null, modifier = Modifier.testTag("team-copy-id"), content = { ServiceLoopIcon(ServiceLoopIcons.Copy, null) })
            }
            Text("This ID identifies this workspace for file exchange. It is not authentication.", style = MaterialTheme.typography.bodySmall)
            TechnicianIdentityContent(showId = false)
        }
        if (content == TeamWorkspaceContent.TRUSTED_IDS) {
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
