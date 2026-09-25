package com.v16studio.v16service.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
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
import com.v16studio.v16service.data.CustomerEntity
import com.v16studio.v16service.data.EquipmentEntity
import com.v16studio.v16service.data.ExportCenterSelection
import com.v16studio.v16service.data.ExportCenterService
import com.v16studio.v16service.data.ExportFamily
import com.v16studio.v16service.data.ExportPreset
import com.v16studio.v16service.data.DataTransferExportService
import com.v16studio.v16service.data.V16_SERVICE_SYNC_MIME
import com.v16studio.v16service.data.SiteEntity
import com.v16studio.v16service.ui.designsystem.V16ServiceNotice
import com.v16studio.v16service.ui.designsystem.V16ServiceNoticeKind
import com.v16studio.v16service.ui.designsystem.V16ServiceFilterSelector
import com.v16studio.v16service.ui.designsystem.V16ServicePrimaryButton
import com.v16studio.v16service.ui.designsystem.V16ServiceSelectionOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
internal fun ExportCenterScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val database = remember { (context.applicationContext as V16ServiceApplication).container.database }
    val service = remember { ExportCenterService(database, context.filesDir) }
    val nativeService = remember { DataTransferExportService(database, context.filesDir) }
    val scope = rememberCoroutineScope()
    var customers by remember { mutableStateOf<List<CustomerEntity>>(emptyList()) }
    var sites by remember { mutableStateOf<List<SiteEntity>>(emptyList()) }
    var equipment by remember { mutableStateOf<List<EquipmentEntity>>(emptyList()) }
    var preset by remember { mutableStateOf(ExportPreset.CUSTOMER_DATA) }
    var format by remember { mutableStateOf("NATIVE") }
    var families by remember { mutableStateOf(ExportPreset.CUSTOMER_DATA.families) }
    var filter by remember { mutableStateOf(com.v16studio.v16service.domain.V16ServiceScopeFilter()) }
    var fromText by remember { mutableStateOf("") }
    var toText by remember { mutableStateOf("") }
    var includePrivate by remember { mutableStateOf(false) }
    var includeInactive by remember { mutableStateOf(false) }
    var includePrevious by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(database) { withContext(Dispatchers.IO) { Triple(database.v16ServiceDao().allCustomers(), database.v16ServiceDao().allSites(), database.v16ServiceDao().allEquipment()) }.let { (c,s,e) -> customers = c; sites = s; equipment = e } }
    val from = runCatching { fromText.takeIf { it.isNotBlank() }?.let(LocalDate::parse) }.getOrNull()
    val to = runCatching { toText.takeIf { it.isNotBlank() }?.let(LocalDate::parse) }.getOrNull()
    val datesValid = (fromText.isBlank() || from != null) && (toText.isBlank() || to != null) && (from == null || to == null || !from.isAfter(to))
    LazyColumn(Modifier.padding(padding).testTag("export-center"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Export Center", style = MaterialTheme.typography.headlineSmall)
                    Text("Share selected data with another V16 Service workspace or create a readable archive. Exports are unencrypted and are not recovery backups.")
                }
                V16ServiceFilterSelector("Preset", preset, ExportPreset.entries.map { it to it.title }, { next -> preset = next; if (next != ExportPreset.CUSTOM) families = next.families }, testTag = "export-preset")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Output format", style = MaterialTheme.typography.titleMedium)
                    V16ServiceSelectionOption(
                        format == "NATIVE",
                        { format = "NATIVE" },
                        "V16 Service file (.v16service) — for another workspace",
                        Modifier.testTag("export-format-native"),
                        selectedCheck = true,
                    )
                    V16ServiceSelectionOption(
                        format == "READABLE",
                        { format = "READABLE" },
                        "Readable archive (.zip) — ZIP containing CSV files",
                        Modifier.testTag("export-format-readable"),
                        selectedCheck = true,
                    )
                }
            }
        }
        item {
            V16ServiceScopeDateFilter(
                title = "Scope",
                filter = filter,
                customers = customers,
                sites = sites,
                equipment = equipment,
                onFilterChange = { filter = it },
                fromText = fromText,
                onFromTextChange = { fromText = it },
                toText = toText,
                onToTextChange = { toText = it },
                fromLabel = "From date (YYYY-MM-DD)",
                toLabel = "To date (YYYY-MM-DD)",
                testTagPrefix = "export",
                dateHelp = "Dates apply to service work and image history. Directory, templates, Follow-ups, and Contact notes use the selected Customer, Site, and Equipment.",
            )
            if (!datesValid) V16ServiceNotice("Check dates", "Use YYYY-MM-DD and keep From on or before To.", V16ServiceNoticeKind.Error)
        }
        item { Text("Contents", style = MaterialTheme.typography.titleMedium) }
        items(ExportFamily.entries, key = { it.name }) { family ->
            V16ServiceSelectionOption(family in families, { families = if (family in families) families - family else families + family; preset = ExportPreset.CUSTOM }, family.title, Modifier.fillMaxWidth().testTag("export-family-${family.name.lowercase()}"), selectedCheck = true)
        }
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Options", style = MaterialTheme.typography.titleMedium)
                V16ServiceSelectionOption(includePrivate, { includePrivate = !includePrivate }, "Include private/internal information", Modifier.testTag("export-option-private"), selectedCheck = true)
                V16ServiceSelectionOption(includeInactive, { includeInactive = !includeInactive }, "Include inactive/archived/retired", Modifier.testTag("export-option-inactive"), selectedCheck = true)
                V16ServiceSelectionOption(includePrevious, { includePrevious = !includePrevious }, "Include previous revisions", Modifier.testTag("export-option-revisions"), selectedCheck = true)
                val native = format == "NATIVE"
                V16ServicePrimaryButton(if (native) "Create V16 Service file" else "Create readable ZIP", { scope.launch {
                    busy = true; message = null
                    runCatching {
                        val requested = ExportCenterSelection(filter.copy(fromDate = from, toDate = to), families, includePrivate, includeInactive, includePrevious)
                        if (native) {
                            val bytes = withContext(Dispatchers.IO) { nativeService.export(requested) }
                            shareFile(context, "export-center", "v16service-data-${System.currentTimeMillis()}.v16service", V16_SERVICE_SYNC_MIME, bytes, "Share V16 Service file", "V16 Service data transfer")
                        } else {
                            val bytes = withContext(Dispatchers.IO) { service.export(requested) }
                            shareFile(context, "export-center", "v16service-export-${System.currentTimeMillis()}.zip", "application/zip", bytes, "Share V16 Service export", "V16 Service readable export")
                        }
                    }.onSuccess { message = if (native) "V16 Service file ready to share." else "Readable ZIP ready to share." }.onFailure { if (it is CancellationException) throw it else message = it.message ?: "Could not create export" }
                    busy = false
                } }, Modifier.fillMaxWidth().testTag("export-center-create"), enabled = families.isNotEmpty() && datesValid && !busy, busy = busy)
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}
