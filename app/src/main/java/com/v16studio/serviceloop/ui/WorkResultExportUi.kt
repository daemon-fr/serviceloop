package com.v16studio.serviceloop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
import com.v16studio.serviceloop.ServiceLoopApplication
import com.v16studio.serviceloop.data.SERVICE_LOOP_SYNC_MIME
import com.v16studio.serviceloop.data.WorkResultExchangeService
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNotice
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNoticeKind
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSelectionOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ReturnableFinal(val revisionId: String, val label: String, val issuerId: String)

@Composable
internal fun WorkResultExportScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val database = remember { (context.applicationContext as ServiceLoopApplication).container.database }
    val exchange = remember { WorkResultExchangeService(database, context.filesDir) }
    val scope = rememberCoroutineScope()
    var available by remember { mutableStateOf<List<ReturnableFinal>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(database) {
        available = withContext(Dispatchers.IO) {
            val dao = database.serviceLoopDao()
            val dispatch = database.dispatchDao()
            dao.allFinalRecords().filterNot { it.voided }.mapNotNull { record ->
                val revision = dao.finalRevision(record.currentRevisionId) ?: return@mapNotNull null
                val assignment = dispatch.finalDispatchVisit(revision.id) ?: return@mapNotNull null
                val issuer = assignment.assignmentIssuerId ?: dispatch.visitBinding(assignment.dispatchVisitId)?.assignmentIssuerId ?: return@mapNotNull null
                ReturnableFinal(revision.id, "${revision.visitReference} · ${revision.actualServiceDate} · ${revision.customerName}", issuer)
            }
        }
        loading = false
    }
    val selectedIssuers = available.filter { it.revisionId in selected }.map { it.issuerId }.toSet()
    LazyColumn(Modifier.padding(padding).testTag("work-results-export"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Export finalized work", style = MaterialTheme.typography.headlineSmall)
            Text("Choose finalized assigned Visits from one issuing ServiceLoop workspace. The file includes structured service truth and copies of finalized photos.")
            if (loading) Text("Loading final records…")
            if (!loading && available.isEmpty()) ServiceLoopNotice("No results ready", "Finalize assigned work before returning results.", ServiceLoopNoticeKind.Warning)
        }
        items(available, key = { it.revisionId }) { value ->
            ServiceLoopSelectionOption(value.revisionId in selected, { selected = if (value.revisionId in selected) selected - value.revisionId else selected + value.revisionId }, value.label, Modifier.testTag("work-result-${value.revisionId}"))
        }
        item {
            if (selectedIssuers.size > 1) ServiceLoopNotice("Choose one issuer", "Separate results from different assignment issuers into different files.", ServiceLoopNoticeKind.Warning)
            ServiceLoopPrimaryButton("Share work results", { scope.launch {
                busy = true; message = null
                runCatching {
                    val bytes = withContext(Dispatchers.IO) { exchange.exportFinalRevisions(selected.toList()) }
                    shareFile(context, "work-results", "serviceloop-work-results-${System.currentTimeMillis()}.slsync", SERVICE_LOOP_SYNC_MIME, bytes,
                        "Share work results", "ServiceLoop finalized work results", "Finalized work results from ServiceLoop")
                }.onSuccess { message = "Work-result file ready to share." }
                    .onFailure { if (it is CancellationException) throw it else message = it.message ?: "Could not export work results" }
                busy = false
            } }, Modifier.fillMaxWidth().testTag("share-work-results"), enabled = selected.isNotEmpty() && selectedIssuers.size == 1 && !busy, busy = busy)
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
