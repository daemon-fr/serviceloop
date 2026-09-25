package com.v16studio.v16service.ui

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
import com.v16studio.v16service.V16ServiceApplication
import com.v16studio.v16service.data.V16_SERVICE_SYNC_MIME
import com.v16studio.v16service.data.WorkResultExchangeService
import com.v16studio.v16service.ui.designsystem.V16ServiceNotice
import com.v16studio.v16service.ui.designsystem.V16ServiceNoticeKind
import com.v16studio.v16service.ui.designsystem.V16ServicePrimaryButton
import com.v16studio.v16service.ui.designsystem.V16ServiceSelectionOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ReturnableFinal(val revisionId: String, val label: String, val issuerId: String)

@Composable
internal fun WorkResultExportScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val database = remember { (context.applicationContext as V16ServiceApplication).container.database }
    val exchange = remember { WorkResultExchangeService(database, context.filesDir) }
    val scope = rememberCoroutineScope()
    var available by remember { mutableStateOf<List<ReturnableFinal>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(database) {
        available = withContext(Dispatchers.IO) {
            val dao = database.v16ServiceDao()
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
            Text("Choose finalized assigned Visits from one issuing V16 Service workspace. The file includes structured service truth and copies of finalized photos.")
            if (loading) Text("Loading final records…")
            if (!loading && available.isEmpty()) V16ServiceNotice("No results ready", "Finalize assigned work before returning results.", V16ServiceNoticeKind.Warning)
        }
        items(available, key = { it.revisionId }) { value ->
            V16ServiceSelectionOption(value.revisionId in selected, { selected = if (value.revisionId in selected) selected - value.revisionId else selected + value.revisionId }, value.label, Modifier.testTag("work-result-${value.revisionId}"))
        }
        item {
            if (selectedIssuers.size > 1) V16ServiceNotice("Choose one issuer", "Separate results from different assignment issuers into different files.", V16ServiceNoticeKind.Warning)
            V16ServicePrimaryButton("Share work results", { scope.launch {
                busy = true; message = null
                runCatching {
                    val bytes = withContext(Dispatchers.IO) { exchange.exportFinalRevisions(selected.toList()) }
                    shareFile(context, "work-results", "v16service-work-results-${System.currentTimeMillis()}.v16service", V16_SERVICE_SYNC_MIME, bytes,
                        "Share work results", "V16 Service finalized work results", "Finalized work results from V16 Service")
                }.onSuccess { message = "Work-result file ready to share." }
                    .onFailure { if (it is CancellationException) throw it else message = it.message ?: "Could not export work results" }
                busy = false
            } }, Modifier.fillMaxWidth().testTag("share-work-results"), enabled = selected.isNotEmpty() && selectedIssuers.size == 1 && !busy, busy = busy)
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
