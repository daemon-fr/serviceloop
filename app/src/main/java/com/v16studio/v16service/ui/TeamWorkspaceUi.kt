package com.v16studio.v16service.ui

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
import com.v16studio.v16service.V16ServiceApplication
import com.v16studio.v16service.data.V16ServicePeerTrustStore
import com.v16studio.v16service.data.TechnicianIdCodec
import com.v16studio.v16service.data.TrustedV16ServiceIdEntity
import com.v16studio.v16service.ui.designsystem.V16ServiceDenseNavigableRow
import com.v16studio.v16service.ui.designsystem.V16ServiceFieldAction
import com.v16studio.v16service.ui.designsystem.V16ServicePrimaryButton
import com.v16studio.v16service.ui.designsystem.V16ServiceSecondaryButton
import com.v16studio.v16service.ui.designsystem.V16ServiceTextButtonAdapter as TextButton
import com.v16studio.v16service.ui.icons.V16ServiceIcons
import com.v16studio.v16service.ui.icons.V16ServiceIcon
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
    val database = remember { (context.applicationContext as V16ServiceApplication).container.database }
    val trustStore = remember(database) { V16ServicePeerTrustStore(database) }
    val role = LocalTeamRole.current
    var localId by remember { mutableStateOf<String?>(null) }
    var trustedIds by remember { mutableStateOf(emptyList<TrustedV16ServiceIdEntity>()) }
    var adding by remember { mutableStateOf(false) }
    var peerId by remember { mutableStateOf("") }
    var friendlyName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<TrustedV16ServiceIdEntity?>(null) }
    var removing by remember { mutableStateOf<TrustedV16ServiceIdEntity?>(null) }
    var editName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { trustStore.localIdentity().technicianId to trustStore.trustedIds() }
            }.onSuccess { (id, rows) -> localId = id; trustedIds = rows }
                .onFailure { error = it.message ?: "Could not load this device's V16 Service IDs" }
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
                            V16ServicePrimaryButton("Import work package", { nav.navigate("dispatch/import/pick") }, Modifier.fillMaxWidth().testTag("team-import-work"))
                            V16ServiceSecondaryButton("Export work results", { nav.navigate("work-results/export") }, Modifier.fillMaxWidth().testTag("team-export-results"))
                        }
                        if (role.workspaceCapabilities.canUseCoordinatorTools) {
                            V16ServicePrimaryButton("Dispatch work package", { nav.navigate("dispatch/create") }, Modifier.fillMaxWidth().testTag("team-dispatch"))
                            V16ServiceSecondaryButton("Import work results", { nav.navigate("import") }, Modifier.fillMaxWidth().testTag("team-import-results"))
                            V16ServiceSecondaryButton("Generate report", { nav.navigate("aggregate-report/new") }, Modifier.fillMaxWidth().testTag("team-generate-report"))
                        }
                    }
                }
            }
            item {
                Text("Manage team", style = MaterialTheme.typography.titleLarge)
                if (role != TeamRole.EMPLOYEE) V16ServiceDenseNavigableRow("Business and report identity", leadingIcon = V16ServiceIcons.Report) { nav.navigate("business-profile") }
                V16ServiceDenseNavigableRow("Your team role", leadingIcon = V16ServiceIcons.TeamRole, modifier = Modifier.testTag("team-role-identity")) { nav.navigate("dispatch/settings") }
                if (role != TeamRole.SOLO) {
                    V16ServiceDenseNavigableRow(role.idHeading(), leadingIcon = V16ServiceIcons.Copy, modifier = Modifier.testTag("team-id-row")) { nav.navigate("team/id") }
                    V16ServiceDenseNavigableRow("Trusted IDs", leadingIcon = V16ServiceIcons.CheckCircle, modifier = Modifier.testTag("team-trusted-ids")) { nav.navigate("team/trusted-ids") }
                }
                if (role.workspaceCapabilities.canUseCoordinatorTools) {
                    V16ServiceDenseNavigableRow("Technicians", leadingIcon = V16ServiceIcons.TeamRole) { nav.navigate("dispatch/technicians") }
                    V16ServiceDenseNavigableRow("Teams and leaders", leadingIcon = V16ServiceIcons.TeamRole) { nav.navigate("dispatch/teams") }
                }
                if (role != TeamRole.SOLO) V16ServiceDenseNavigableRow("Import shared data", leadingIcon = V16ServiceIcons.ArrowCircleDown, modifier = Modifier.testTag("team-import")) { nav.navigate("import") }
                if (role == TeamRole.SUBCONTRACTOR || role.workspaceCapabilities.canUseCoordinatorTools) V16ServiceDenseNavigableRow("Export data", leadingIcon = V16ServiceIcons.ArrowCircleUp, modifier = Modifier.testTag("team-export")) { nav.navigate("data-transfer/export") }
            }
        }
        if (content == TeamWorkspaceContent.ID) item {
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(localId?.let(TechnicianIdCodec::display).orEmpty(), modifier = Modifier.weight(1f).testTag("team-local-id"), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                V16ServiceFieldAction(accessibleName = "Copy ID", onClick = { localId?.let { id -> (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("V16 Service ID", id)) } }, enabled = localId != null, modifier = Modifier.testTag("team-copy-id"), content = { V16ServiceIcon(V16ServiceIcons.Copy, null) })
            }
            Text("This ID identifies this workspace for file exchange. It is not authentication.", style = MaterialTheme.typography.bodySmall)
            TechnicianIdentityContent(showId = false)
        }
        if (content == TeamWorkspaceContent.TRUSTED_IDS) {
        item {
            Text("Trusted IDs", style = MaterialTheme.typography.titleLarge)
            Text("Only V16 Service files from trusted IDs can be imported.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            if (!adding) V16ServicePrimaryButton("Add trusted ID", { adding = true; error = null }, Modifier.fillMaxWidth().testTag("team-add-trusted-id"))
            else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(peerId, { peerId = it }, label = { Text("V16 Service ID") }, modifier = Modifier.fillMaxWidth().testTag("trusted-id-input"), singleLine = true)
                OutlinedTextField(friendlyName, { friendlyName = it }, label = { Text("Friendly name") }, modifier = Modifier.fillMaxWidth().testTag("trusted-name-input"), singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                V16ServicePrimaryButton("Save trusted ID", {
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
