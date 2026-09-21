package com.v16studio.serviceloop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.v16studio.serviceloop.domain.CustomerType
import com.v16studio.serviceloop.domain.CustomerWithFirstSiteInput
import com.v16studio.serviceloop.domain.CustomerInput
import com.v16studio.serviceloop.domain.SiteInput
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens

internal data class CustomerCreationDraft(
    val name: String = "",
    val contactName: String = "",
    val phone: String = "",
    val email: String = "",
    val privateNote: String = "",
    val customerType: CustomerType = CustomerType.STANDARD,
    val siteName: String = "",
    val siteAddress: String = "",
) {
    fun toInput() = CustomerWithFirstSiteInput(
        customer = CustomerInput(
            name = name,
            contactName = contactName,
            phone = phone,
            email = email,
            privateNote = privateNote,
            customerType = customerType,
        ),
        site = SiteInput(
            name = siteName,
            address = siteAddress,
            isDefault = true,
        ),
    )

    fun hasMeaningfulInput(): Boolean = listOf(name, contactName, phone, email, privateNote, siteName, siteAddress).any { it.isNotBlank() } || customerType != CustomerType.STANDARD

    fun isValidForCreate(): Boolean = name.isNotBlank() && siteName.isNotBlank()
}

internal val CustomerCreationDraftSaver = listSaver<CustomerCreationDraft, String>(
    save = { draft: CustomerCreationDraft ->
        listOf(
            draft.name,
            draft.contactName,
            draft.phone,
            draft.email,
            draft.privateNote,
            draft.customerType.code,
            draft.siteName,
            draft.siteAddress,
        )
    },
    restore = { values: List<String> ->
        CustomerCreationDraft(
            name = values.getOrNull(0).orEmpty(),
            contactName = values.getOrNull(1).orEmpty(),
            phone = values.getOrNull(2).orEmpty(),
            email = values.getOrNull(3).orEmpty(),
            privateNote = values.getOrNull(4).orEmpty(),
            customerType = values.getOrNull(5)?.let(CustomerType::fromCode) ?: CustomerType.STANDARD,
            siteName = values.getOrNull(6).orEmpty(),
            siteAddress = values.getOrNull(7).orEmpty(),
        )
    },
)

@Composable
internal fun CustomerCreationForm(
    draft: CustomerCreationDraft,
    onDraftChange: (CustomerCreationDraft) -> Unit,
    editable: Boolean = true,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs),
    ) {
        DailyField(draft.name, { onDraftChange(draft.copy(name = it)) }, "Customer name · Required", enabled = editable)
        DailyField(draft.contactName, { onDraftChange(draft.copy(contactName = it)) }, "Main contact", enabled = editable)
        DailyField(draft.phone, { onDraftChange(draft.copy(phone = it)) }, "Phone", enabled = editable)
        DailyField(draft.email, { onDraftChange(draft.copy(email = it)) }, "Email", enabled = editable)

        DailyHeading("First site")
        Text("Every new customer starts with a default site. Blank contact fields inherit the customer contact.")
        DailyField(draft.siteName, { onDraftChange(draft.copy(siteName = it)) }, "Site name · Required", enabled = editable)
        DailyField(draft.siteAddress, { onDraftChange(draft.copy(siteAddress = it)) }, "Site address", enabled = editable)

        LongTextEditor(draft.privateNote, { onDraftChange(draft.copy(privateNote = it)) }, "Private customer note", private = true, enabled = editable)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().testTag("customer-creation-type-control"),
        ) {
            Checkbox(
                checked = draft.customerType == CustomerType.ONE_TIME,
                onCheckedChange = { checked -> onDraftChange(draft.copy(customerType = if (checked) CustomerType.ONE_TIME else CustomerType.STANDARD)) },
                enabled = editable,
                modifier = Modifier.testTag("customer-creation-one-time-checkbox"),
            )
            Text("One-time customer (no contract)")
        }
    }
}
