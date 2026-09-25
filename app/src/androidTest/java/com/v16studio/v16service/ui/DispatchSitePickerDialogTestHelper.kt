package com.v16studio.v16service.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.v16studio.v16service.data.CustomerEntity
import com.v16studio.v16service.data.SiteEntity
import com.v16studio.v16service.domain.CustomerType

@Composable
internal fun DispatchSitePickerDialog(
    sites: List<SiteEntity>,
    customers: List<CustomerEntity>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val customersById = customers.associateBy { it.id }
    val needle = query.trim().lowercase()
    val results = sites.filter { site ->
        val customer = customersById[site.customerId]
        val type = customer?.let { CustomerType.fromCode(it.customerType) }
        val matching = listOf(site.reference, site.name, site.address, customer?.reference, customer?.name).any { it?.lowercase()?.contains(needle) == true }
        type == CustomerType.STANDARD || (needle.isNotBlank() && type == CustomerType.ONE_TIME && matching)
    }
    AlertDialog(
        modifier = Modifier.testTag("dispatch-site-picker"),
        onDismissRequest = onDismiss,
        title = { Text("Choose Site") },
        text = {
            Column {
                TextField(query, { query = it }, label = { Text("Search Site, Customer, or address") }, modifier = Modifier.fillMaxWidth().testTag("dispatch-site-search"))
                LazyColumn(Modifier.heightIn(max = 420.dp).testTag("dispatch-site-picker-list")) {
                    items(results, key = { it.id }) { site ->
                        val customer = customersById[site.customerId]
                        TextButton({ onSelect(site.id) }, Modifier.fillMaxWidth().testTag("dispatch-site-${site.id}")) {
                            Column(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth()) { Text("${site.reference} · ${site.name}") }
                                Text(listOfNotNull(customer?.name, site.address).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
