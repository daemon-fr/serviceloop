package com.v16studio.serviceloop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.v16studio.serviceloop.ServiceLoopApplication
import com.v16studio.serviceloop.data.AggregateReportService
import com.v16studio.serviceloop.data.CustomerEntity
import com.v16studio.serviceloop.data.EquipmentEntity
import com.v16studio.serviceloop.data.ReportableVisitSource
import com.v16studio.serviceloop.data.SiteEntity
import com.v16studio.serviceloop.domain.ServiceLoopScopeFilter
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFilterSelector
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNotice
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNoticeKind
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSelectionOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@Composable
internal fun AggregateReportScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as ServiceLoopApplication).container }
    val service = remember { AggregateReportService(container.database, container.repository, context.filesDir) }
    val scope = rememberCoroutineScope()
    var customers by remember { mutableStateOf<List<CustomerEntity>>(emptyList()) }
    var sites by remember { mutableStateOf<List<SiteEntity>>(emptyList()) }
    var equipment by remember { mutableStateOf<List<EquipmentEntity>>(emptyList()) }
    var filter by remember { mutableStateOf(ServiceLoopScopeFilter()) }
    var fromText by remember { mutableStateOf("") }
    var toText by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ReportableVisitSource>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(container.database) { withContext(Dispatchers.IO) { Triple(container.database.serviceLoopDao().allCustomers(), container.database.serviceLoopDao().allSites(), container.database.serviceLoopDao().allEquipment()) }.let { (c,s,e) -> customers=c; sites=s; equipment=e } }
    val from = runCatching { fromText.takeIf(String::isNotBlank)?.let(LocalDate::parse) }.getOrNull()
    val to = runCatching { toText.takeIf(String::isNotBlank)?.let(LocalDate::parse) }.getOrNull()
    val validDates = (fromText.isBlank() || from != null) && (toText.isBlank() || to != null) && (from == null || to == null || !from.isAfter(to))
    val effective = if (validDates) filter.copy(fromDate = from, toDate = to) else null
    LaunchedEffect(effective) {
        selected = emptySet()
        results = if (effective == null) emptyList() else withContext(Dispatchers.IO) { service.reportable(effective) }
    }
    LazyColumn(Modifier.padding(padding).testTag("aggregate-report-new"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Aggregate customer report", style = MaterialTheme.typography.headlineSmall)
            Text("Select one Customer and the concluded Visits to include. The PDF freezes their exact final revisions.")
            ServiceLoopFilterSelector("Customer", filter.customerId, listOf(null to "All customers") + customers.map { it.id to "${it.reference} · ${it.name}" }, { filter = filter.withCustomer(it) }, testTag = "aggregate-customer")
            ServiceLoopFilterSelector("Site", filter.siteId, listOf(null to "All sites") + sites.filter { it.customerId == filter.customerId }.map { it.id to "${it.reference} · ${it.name}" }, { filter = filter.withSite(it) }, enabled = filter.customerId != null, testTag = "aggregate-site")
            ServiceLoopFilterSelector("Equipment", filter.equipmentId, listOf(null to "All equipment") + equipment.filter { it.siteId == filter.siteId }.map { it.id to "${it.reference} · ${it.name}" }, { filter = filter.withEquipment(it) }, enabled = filter.siteId != null, testTag = "aggregate-equipment")
            OutlinedTextField(fromText, { fromText = it }, label = { Text("From service date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth().testTag("aggregate-from-date"), singleLine = true)
            OutlinedTextField(toText, { toText = it }, label = { Text("Through service date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth().testTag("aggregate-to-date"), singleLine = true)
            if (!validDates) ServiceLoopNotice("Check dates", "Use YYYY-MM-DD and keep From on or before Through.", ServiceLoopNoticeKind.Error)
            if (filter.customerId == null) ServiceLoopNotice("Choose a Customer", "One Customer is required to generate a report.", ServiceLoopNoticeKind.Warning)
        }
        item { Text("Visits · ${results.size}", style = MaterialTheme.typography.titleMedium) }
        items(results, key = { it.key }) { source ->
            ServiceLoopSelectionOption(source.key in selected, { selected = if (source.key in selected) selected - source.key else selected + source.key },
                "${source.visitReference} · ${source.serviceDate} · ${source.technicians.joinToString(", ")} · ${source.sources.size} final source(s)", Modifier.testTag("aggregate-source-${source.key.replace(':','-')}"), selectedCheck = true)
        }
        item {
            ServiceLoopPrimaryButton("Generate customer PDF", { scope.launch {
                busy = true; message = null
                runCatching {
                    val generated = withContext(Dispatchers.IO) { service.generate(requireNotNull(effective), selected.toList()) }
                    val bytes = withContext(Dispatchers.IO) { File(context.filesDir, generated.relativePath).readBytes() }
                    shareFile(context, "aggregate-reports", "serviceloop-customer-report-${generated.reportId.take(8)}.pdf", "application/pdf", bytes,
                        "Share customer report", "ServiceLoop aggregate customer report")
                    generated
                }.onSuccess { message = "Report generated and ready to share." }
                    .onFailure { if (it is CancellationException) throw it else message = it.message ?: "Report generation failed" }
                busy = false
            } }, Modifier.fillMaxWidth().testTag("aggregate-generate"), enabled = filter.customerId != null && validDates && selected.isNotEmpty() && !busy, busy = busy)
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
