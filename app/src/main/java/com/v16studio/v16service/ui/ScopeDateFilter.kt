package com.v16studio.v16service.ui

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
import com.v16studio.v16service.data.CustomerEntity
import com.v16studio.v16service.data.EquipmentEntity
import com.v16studio.v16service.data.SiteEntity
import com.v16studio.v16service.domain.V16ServiceScopeFilter
import com.v16studio.v16service.ui.designsystem.LocalV16ServiceTokens
import com.v16studio.v16service.ui.designsystem.V16ServiceFieldAction
import com.v16studio.v16service.ui.designsystem.V16ServiceFilterSelector
import com.v16studio.v16service.ui.designsystem.V16ServiceUiTokens
import com.v16studio.v16service.ui.icons.V16ServiceIcon
import com.v16studio.v16service.ui.icons.V16ServiceIcons
import java.time.LocalDate

/** Shared hierarchical scope and inclusive date controls for exports and customer reports. */
@Composable
internal fun V16ServiceScopeDateFilter(
    title: String,
    filter: V16ServiceScopeFilter,
    customers: List<CustomerEntity>,
    sites: List<SiteEntity>,
    equipment: List<EquipmentEntity>,
    onFilterChange: (V16ServiceScopeFilter) -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm),
    ) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        V16ServiceFilterSelector(
            "Customer",
            filter.customerId,
            listOf(null to "All customers") + customers.map { it.id to "${it.reference} · ${it.name}" },
            { onFilterChange(filter.withCustomer(it)) },
            testTag = "$testTagPrefix-customer",
        )
        V16ServiceFilterSelector(
            "Site",
            filter.siteId,
            listOf(null to "All sites") + siteOptions.map { it.id to "${it.reference} · ${it.name}" },
            { onFilterChange(filter.withSite(it)) },
            enabled = filter.customerId != null,
            testTag = "$testTagPrefix-site",
        )
        V16ServiceFilterSelector(
            "Equipment",
            filter.equipmentId,
            listOf(null to "All equipment") + equipmentOptions.map { it.id to "${it.reference} · ${it.name}" },
            { onFilterChange(filter.withEquipment(it)) },
            enabled = filter.siteId != null,
            testTag = "$testTagPrefix-equipment",
        )
        V16ServiceDateInput(fromLabel, fromText, onFromTextChange, "$testTagPrefix-from-date")
        V16ServiceDateInput(toLabel, toText, onToTextChange, "$testTagPrefix-to-date")
        dateHelp?.let { Text(it, color = LocalV16ServiceTokens.current.textSecondary) }
    }
}

@Composable
private fun V16ServiceDateInput(
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
        horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Layout.fieldActionGap),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.weight(1f).testTag(testTagPrefix),
            singleLine = true,
        )
        V16ServiceFieldAction(
            accessibleName = "Choose $label",
            onClick = { showPicker = true },
            modifier = Modifier.testTag("$testTagPrefix-picker"),
            content = { V16ServiceIcon(V16ServiceIcons.Calendar, null, Modifier.size(V16ServiceUiTokens.Size.icon), LocalV16ServiceTokens.current.action) },
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
