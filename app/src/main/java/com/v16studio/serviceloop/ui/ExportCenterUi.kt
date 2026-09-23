package com.v16studio.serviceloop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import com.v16studio.serviceloop.data.CustomerEntity
import com.v16studio.serviceloop.data.EquipmentEntity
import com.v16studio.serviceloop.data.ExportCenterSelection
import com.v16studio.serviceloop.data.ExportCenterService
import com.v16studio.serviceloop.data.ExportFamily
import com.v16studio.serviceloop.data.ExportPreset
import com.v16studio.serviceloop.data.SiteEntity
import com.v16studio.serviceloop.domain.ServiceLoopScopeFilter
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNotice
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNoticeKind
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFilterSelector
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSelectionOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
internal fun ExportCenterScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val database = remember { (context.applicationContext as ServiceLoopApplication).container.database }
    val service = remember { ExportCenterService(database, context.filesDir) }
    val scope = rememberCoroutineScope()
    var customers by remember { mutableStateOf<List<CustomerEntity>>(emptyList()) }
    var sites by remember { mutableStateOf<List<SiteEntity>>(emptyList()) }
    var equipment by remember { mutableStateOf<List<EquipmentEntity>>(emptyList()) }
    var preset by remember { mutableStateOf(ExportPreset.CUSTOMER_DATA) }
    var families by remember { mutableStateOf(ExportPreset.CUSTOMER_DATA.families) }
    var filter by remember { mutableStateOf(ServiceLoopScopeFilter()) }
    var fromText by remember { mutableStateOf("") }
    var toText by remember { mutableStateOf("") }
    var includePrivate by remember { mutableStateOf(false) }
    var includeInactive by remember { mutableStateOf(false) }
    var includePrevious by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(database) { withContext(Dispatchers.IO) { Triple(database.serviceLoopDao().allCustomers(), database.serviceLoopDao().allSites(), database.serviceLoopDao().allEquipment()) }.let { (c,s,e) -> customers = c; sites = s; equipment = e } }
    val siteOptions = sites.filter { it.customerId == filter.customerId }
    val equipmentOptions = equipment.filter { it.siteId == filter.siteId }
    val from = runCatching { fromText.takeIf { it.isNotBlank() }?.let(LocalDate::parse) }.getOrNull()
    val to = runCatching { toText.takeIf { it.isNotBlank() }?.let(LocalDate::parse) }.getOrNull()
    val datesValid = (fromText.isBlank() || from != null) && (toText.isBlank() || to != null) && (from == null || to == null || !from.isAfter(to))
    LazyColumn(Modifier.padding(padding).testTag("export-center"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Export Center", style = MaterialTheme.typography.headlineSmall)
            Text("Create readable CSV and image files for use outside ServiceLoop. Exports are unencrypted and are not recovery backups.")
            ServiceLoopFilterSelector("Preset", preset, ExportPreset.entries.map { it to it.title }, { next -> preset = next; if (next != ExportPreset.CUSTOM) families = next.families }, testTag = "export-preset")
        }
        item {
            Text("Scope", style = MaterialTheme.typography.titleMedium)
            ServiceLoopFilterSelector("Customer", filter.customerId, listOf(null to "All customers") + customers.map { it.id to "${it.reference} · ${it.name}" }, { filter = filter.withCustomer(it) }, testTag = "export-customer")
            ServiceLoopFilterSelector("Site", filter.siteId, listOf(null to "All sites") + siteOptions.map { it.id to "${it.reference} · ${it.name}" }, { filter = filter.withSite(it) }, enabled = filter.customerId != null, testTag = "export-site")
            ServiceLoopFilterSelector("Equipment", filter.equipmentId, listOf(null to "All equipment") + equipmentOptions.map { it.id to "${it.reference} · ${it.name}" }, { filter = filter.withEquipment(it) }, enabled = filter.siteId != null, testTag = "export-equipment")
            OutlinedTextField(fromText, { fromText = it }, label = { Text("From date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth().testTag("export-from-date"), singleLine = true)
            OutlinedTextField(toText, { toText = it }, label = { Text("To date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth().testTag("export-to-date"), singleLine = true)
            Text("Dates apply to service work and image history. Directory, templates, Follow-ups, and Contact notes use the selected Customer, Site, and Equipment.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!datesValid) ServiceLoopNotice("Check dates", "Use YYYY-MM-DD and keep From on or before To.", ServiceLoopNoticeKind.Error)
        }
        item { Text("Contents", style = MaterialTheme.typography.titleMedium) }
        items(ExportFamily.entries, key = { it.name }) { family ->
            ServiceLoopSelectionOption(family in families, { families = if (family in families) families - family else families + family; preset = ExportPreset.CUSTOM }, family.title, Modifier.fillMaxWidth().testTag("export-family-${family.name.lowercase()}"), selectedCheck = true)
        }
        item {
            Text("Options", style = MaterialTheme.typography.titleMedium)
            Row { ServiceLoopSelectionOption(includePrivate, { includePrivate = !includePrivate }, "Include private/internal information", selectedCheck = true) }
            Row { ServiceLoopSelectionOption(includeInactive, { includeInactive = !includeInactive }, "Include inactive/archived/retired", selectedCheck = true) }
            Row { ServiceLoopSelectionOption(includePrevious, { includePrevious = !includePrevious }, "Include previous revisions", selectedCheck = true) }
            ServiceLoopPrimaryButton("Create export ZIP", { scope.launch {
                busy = true; message = null
                runCatching {
                    val requested = ExportCenterSelection(filter.copy(fromDate = from, toDate = to), families, includePrivate, includeInactive, includePrevious)
                    val bytes = withContext(Dispatchers.IO) { service.export(requested) }
                    shareFile(context, "export-center", "serviceloop-export-${System.currentTimeMillis()}.zip", "application/zip", bytes, "Share ServiceLoop export", "ServiceLoop readable export")
                }.onSuccess { message = "Export ZIP ready to share." }.onFailure { if (it is CancellationException) throw it else message = it.message ?: "Could not create export" }
                busy = false
            } }, Modifier.fillMaxWidth().testTag("export-center-create"), enabled = families.isNotEmpty() && datesValid && !busy, busy = busy)
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
