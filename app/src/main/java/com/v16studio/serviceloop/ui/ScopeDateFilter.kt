package com.v16studio.serviceloop.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.v16studio.serviceloop.data.CustomerEntity
import com.v16studio.serviceloop.data.EquipmentEntity
import com.v16studio.serviceloop.data.SiteEntity
import com.v16studio.serviceloop.domain.ServiceLoopScopeFilter
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFieldAction
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFilterSelector
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons
import java.time.LocalDate

/** Shared hierarchical scope and inclusive date controls for exports and customer reports. */
@Composable
internal fun ServiceLoopScopeDateFilter(
    title: String,
    filter: ServiceLoopScopeFilter,
    customers: List<CustomerEntity>,
    sites: List<SiteEntity>,
    equipment: List<EquipmentEntity>,
    onFilterChange: (ServiceLoopScopeFilter) -> Unit,
    fromText: String,
    onFromTextChange: (String) -> Unit,
    toText: String,
    onToTextChange: (String) -> Unit,
    fromLabel: String,
    toLabel: String,
    testTagPrefix: String,
    dateHelp: String? = null,
) {
    val siteOptions = sites.filter { it.customerId == filter.customerId }
    val equipmentOptions = equipment.filter { it.siteId == filter.siteId }

    Column(
        Modifier.fillMaxWidth().testTag("$testTagPrefix-scope-date-filter"),
        verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm),
    ) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        ServiceLoopFilterSelector(
            "Customer",
            filter.customerId,
            listOf(null to "All customers") + customers.map { it.id to "${it.reference} · ${it.name}" },
            { onFilterChange(filter.withCustomer(it)) },
            testTag = "$testTagPrefix-customer",
        )
        ServiceLoopFilterSelector(
            "Site",
            filter.siteId,
            listOf(null to "All sites") + siteOptions.map { it.id to "${it.reference} · ${it.name}" },
            { onFilterChange(filter.withSite(it)) },
            enabled = filter.customerId != null,
            testTag = "$testTagPrefix-site",
        )
        ServiceLoopFilterSelector(
            "Equipment",
            filter.equipmentId,
            listOf(null to "All equipment") + equipmentOptions.map { it.id to "${it.reference} · ${it.name}" },
            { onFilterChange(filter.withEquipment(it)) },
            enabled = filter.siteId != null,
            testTag = "$testTagPrefix-equipment",
        )
        ServiceLoopDateInput(fromLabel, fromText, onFromTextChange, "$testTagPrefix-from-date")
        ServiceLoopDateInput(toLabel, toText, onToTextChange, "$testTagPrefix-to-date")
        dateHelp?.let { Text(it, color = LocalServiceLoopTokens.current.textSecondary) }
    }
}

@Composable
private fun ServiceLoopDateInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTagPrefix: String,
) {
    var showPicker by rememberSaveable(testTagPrefix) { mutableStateOf(false) }
    val parsedDate = runCatching { LocalDate.parse(value) }.getOrNull()
    val context = LocalContext.current

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Layout.fieldActionGap),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.weight(1f).testTag(testTagPrefix),
            singleLine = true,
        )
        ServiceLoopFieldAction(
            accessibleName = "Choose $label",
            onClick = { showPicker = true },
            modifier = Modifier.testTag("$testTagPrefix-picker"),
            content = { ServiceLoopIcon(ServiceLoopIcons.Calendar, null, Modifier.size(ServiceLoopUiTokens.Size.icon), LocalServiceLoopTokens.current.action) },
        )
    }

    if (showPicker) {
        val initial = parsedDate ?: LocalDate.now()
        val dialog = remember(context, initial) {
            DatePickerDialog(context, { _, year, month, day ->
                onValueChange(LocalDate.of(year, month + 1, day).toString())
                showPicker = false
            }, initial.year, initial.monthValue - 1, initial.dayOfMonth)
        }
        DisposableEffect(dialog) {
            dialog.setOnCancelListener { showPicker = false }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }
}
