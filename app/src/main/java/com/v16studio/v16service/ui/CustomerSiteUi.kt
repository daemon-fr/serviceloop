package com.v16studio.v16service.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.v16studio.v16service.domain.*
import com.v16studio.v16service.ui.designsystem.V16ServiceActionStack
import com.v16studio.v16service.ui.designsystem.V16ServiceAdaptiveActionRow
import com.v16studio.v16service.ui.designsystem.V16ServiceButtonAdapter as Button
import com.v16studio.v16service.ui.designsystem.V16ServiceContentTabs
import com.v16studio.v16service.ui.designsystem.V16ServiceChoiceGroup
import com.v16studio.v16service.ui.designsystem.V16ServiceDenseNavigableRow
import com.v16studio.v16service.ui.designsystem.V16ServiceEntityRecord
import com.v16studio.v16service.ui.designsystem.V16ServicePrimaryButton
import com.v16studio.v16service.ui.designsystem.V16ServiceSectionDivider
import com.v16studio.v16service.ui.designsystem.V16ServiceSecondaryButton
import com.v16studio.v16service.ui.designsystem.V16ServiceNavigationButton
import com.v16studio.v16service.ui.designsystem.V16ServiceTextButtonAdapter as TextButton
import com.v16studio.v16service.ui.designsystem.LocalV16ServiceTokens
import com.v16studio.v16service.ui.designsystem.V16ServiceUiTokens
import com.v16studio.v16service.ui.designsystem.OperationalWorkGateway
import com.v16studio.v16service.domain.OperationalDashboardProjection
import com.v16studio.v16service.domain.OperationalWorkClassifier
import com.v16studio.v16service.domain.OperationalWorkState
import java.time.LocalDate
import com.v16studio.v16service.ui.icons.V16ServiceIcon
import com.v16studio.v16service.ui.icons.V16ServiceIcons

@Composable
internal fun CustomerDetailScreen(
    detail: CustomerDetail?,
    padding: PaddingValues,
    nav: NavHostController,
    viewModel: V16ServiceViewModel,
    workDashboard: OperationalDashboardProjection? = null,
    businessDate: LocalDate = LocalDate.now(),
    dueSoonHorizonDays: Int = 14,
) {
    if (detail == null) return DailyEmpty(padding, "Reading customer")
    val capabilities = LocalWorkspaceCapabilities.current
    val context = LocalContext.current
    var handoffStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var showContactForm by rememberSaveable { mutableStateOf(false) }
    var contactPerson by rememberSaveable { mutableStateOf("") }
    var contactValue by rememberSaveable { mutableStateOf("") }
    var contactNotes by rememberSaveable { mutableStateOf("") }
    var contactChannel by rememberSaveable { mutableStateOf("PHONE") }
    var editingContactId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingContactId by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable(detail.id) { mutableStateOf("SITES") }
    val colors = LocalV16ServiceTokens.current
    val viewState by viewModel.state.collectAsState()
    if (showContactForm) CustomerContactFormDialog(
        editingContactId = editingContactId,
        person = contactPerson,
        onPersonChange = { contactPerson = it },
        channel = contactChannel,
        onChannelChange = { contactChannel = it },
        value = contactValue,
        onValueChange = { contactValue = it },
        notes = contactNotes,
        onNotesChange = { contactNotes = it },
        busy = viewState.operationInProgress,
        testTagPrefix = "customer-contact",
        onDismiss = { showContactForm = false },
        onSave = { person, channel, value, notes ->
            val input = CustomerContactInput(detail.id, person, channel, value, notes)
            val contactId = editingContactId
            if (contactId == null) viewModel.createCustomerContact(input) {
                contactPerson = ""; contactValue = ""; contactNotes = ""; contactChannel = "PHONE"; showContactForm = false; viewModel.loadCustomer(detail.id)
            } else viewModel.updateCustomerContact(contactId, input) {
                editingContactId = null; contactPerson = ""; contactValue = ""; contactNotes = ""; contactChannel = "PHONE"; showContactForm = false; viewModel.loadCustomer(detail.id)
            }
        },
    )
    deletingContactId?.let { contactId ->
        AlertDialog(
            onDismissRequest = { deletingContactId = null },
            title = { Text("Remove customer contact?") },
            text = { Text("This contact will be removed from the customer. The primary contact fields remain unchanged.") },
            dismissButton = { TextButton({ deletingContactId = null }) { Text("Cancel") } },
            confirmButton = { Button({ viewModel.deleteCustomerContact(detail.id, contactId) { deletingContactId = null; viewModel.loadCustomer(detail.id) } }) { Text("Remove") } },
        )
    }
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(0.dp, 8.dp, 0.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.section)) {
        item {
            Column(Modifier.fillMaxWidth().background(colors.surface)) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("${detail.reference} · ${detail.name}", style = MaterialTheme.typography.headlineSmall); Text(detail.contactName.ifBlank { "No main contact" }); Text(listOf(detail.phone, detail.email).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "No phone or email" }); Spacer(Modifier.height(V16ServiceUiTokens.Space.lg)); Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement = Arrangement.spacedBy(8.dp)) { if(capabilities.canManageRegister) V16ServiceSecondaryButton("Edit",{nav.navigate("customer/edit/${detail.id}")},Modifier.weight(1f).fillMaxHeight()); V16ServiceSecondaryButton("Record contact",{nav.navigate("contact/new/${detail.id}")},Modifier.weight(1f).fillMaxHeight()) }; Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextButton({handoffStatus=handoff(context,Intent(Intent.ACTION_DIAL,Uri.parse("tel:${Uri.encode(detail.phone)}")),"dialer")},enabled=detail.phone.isNotBlank()){V16ServiceIcon(V16ServiceIcons.Call,null,Modifier.size(24.dp));Spacer(Modifier.width(4.dp));Text("Call")}; TextButton({handoffStatus=handoff(context,Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:${Uri.encode(detail.phone)}")),"SMS composer")},enabled=detail.phone.isNotBlank()){V16ServiceIcon(V16ServiceIcons.Sms,null,Modifier.size(24.dp));Spacer(Modifier.width(4.dp));Text("SMS")}; TextButton({handoffStatus=handoff(context,Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:${Uri.encode(detail.email)}")),"email composer")},enabled=detail.email.isNotBlank()){V16ServiceIcon(V16ServiceIcons.Mail,null,Modifier.size(24.dp));Spacer(Modifier.width(4.dp));Text("Email")} }; handoffStatus?.let{Text(it)} }
                if (detail.customerType == CustomerType.ONE_TIME) {
                    Text("One-time customer", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("one-time-customer-label"))
                }
                workDashboard?.takeIf { it.totalItemCount > 0 }?.mostUrgentState?.let { urgency ->
                    Spacer(Modifier.height(V16ServiceUiTokens.Space.lg))
                    OperationalWorkGateway(workDashboard.totalItemCount, urgency, onClick = { nav.navigate("work-dashboard/customer/${detail.id}") }, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(V16ServiceUiTokens.Space.lg))
                }
                if ((workDashboard?.totalItemCount ?: 0) == 0) Spacer(Modifier.height(V16ServiceUiTokens.Space.md))
                V16ServiceContentTabs(listOf("SITES" to "Sites", "EQUIPMENT" to "Equipment"),tab,{tab=it})
            }
        }
        if (tab == "SITES") { item { Column(Modifier.padding(horizontal = 16.dp)) { DailyHeading("Sites"); if(capabilities.canManageRegister){ Spacer(Modifier.height(V16ServiceUiTokens.Space.md)); Button({ nav.navigate("site/new/${detail.id}") }, Modifier.fillMaxWidth().testTag("add-site")) { Text("Add site") } } } }; items(detail.sites) { site -> V16ServiceEntityRecord("${site.reference} · ${site.name}",site.address,"${site.equipmentCount} equipment${if(site.isDefault) " · Default" else ""}", modifier = Modifier.padding(horizontal = 16.dp)){nav.navigate("site/${site.id}")} } }
        else { item { Box(Modifier.padding(horizontal = 16.dp)) { DailyHeading("Equipment") } }; items(detail.equipment) { item -> V16ServiceEntityRecord("${item.technicianIdentifier ?: item.reference} · ${item.name}",item.siteName,"Next due ${item.nearestDueDate ?: "not scheduled"}", modifier = Modifier.padding(horizontal = 16.dp)){nav.navigate("equipment/${item.id}")} } }
        item { Column(Modifier.padding(horizontal = 16.dp)) { V16ServiceSectionDivider(); DailyHeading("Active follow-ups"); Spacer(Modifier.height(V16ServiceUiTokens.Space.md)); V16ServicePrimaryButton("Add follow-up",{nav.navigate("follow-up/new/${detail.id}")},Modifier.fillMaxWidth()) } }
        items(detail.openFollowUps) { follow -> V16ServiceEntityRecord("${follow.reference} · ${follow.title}",metadata="Due ${follow.dueDate}", modifier = Modifier.padding(horizontal = 16.dp), operationalState = OperationalWorkClassifier.classifyFollowUp(follow.state, follow.dueDate, businessDate, dueSoonHorizonDays)){nav.navigate("follow-up/${follow.id}")} }
        if (detail.recentContacts.isNotEmpty()) item { Box(Modifier.padding(horizontal = 16.dp)) { DailyHeading("Recent contact") } }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    DailyHeading("Additional contacts")
                    if (capabilities.canManageRegister) V16ServiceSecondaryButton("Add contact", { showContactForm = true }, Modifier.testTag("add-customer-contact"))
                }
                if (detail.repeatableContacts.isEmpty()) Text("No additional contacts saved.", style = MaterialTheme.typography.bodySmall)
                detail.repeatableContacts.forEach { contact ->
                    V16ServiceDenseNavigableRow(
                        title = listOf(contact.personName, contact.value).filter { it.isNotBlank() }.joinToString(" · "),
                        context = contact.channel.lowercase().replaceFirstChar { it.uppercase() },
                        leadingIcon = when (contact.channel) { "PHONE" -> V16ServiceIcons.Call; "SMS", "WHATSAPP" -> V16ServiceIcons.Sms; "EMAIL" -> V16ServiceIcons.Mail; else -> V16ServiceIcons.Customers },
                        modifier = Modifier.testTag("customer-repeatable-contact-${contact.id}"),
                        onClick = {
                            handoffStatus = when (contact.channel) {
                                "PHONE" -> handoff(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(contact.value)}")), "dialer")
                                "SMS" -> handoff(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(contact.value)}")), "SMS composer")
                                "EMAIL" -> handoff(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(contact.value)}")), "email composer")
                                "WHATSAPP" -> handoff(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${contact.value.filter(Char::isDigit)}")), "WhatsApp")
                                else -> null
                            }
                        },
                    )
                    if (capabilities.canManageRegister) {
                        Row(horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm)) {
                            TextButton({ editingContactId = contact.id; contactPerson = contact.personName; contactValue = contact.value; contactChannel = contact.channel; contactNotes = contact.notes; showContactForm = true }, Modifier.testTag("edit-customer-contact-${contact.id}")) { Text("Edit") }
                            TextButton({ deletingContactId = contact.id }, Modifier.testTag("delete-customer-contact-${contact.id}")) { Text("Remove") }
                        }
                    }
                }
            }
        }
        items(detail.recentContacts, key = { it.id }) { note ->
            V16ServiceDenseNavigableRow(
                title = "${note.reference} · ${contactChannelLabel(note.channel)}",
                context = note.outcome,
                status = if (note.enteredInError) "Entered in error" else null,
                leadingIcon = contactChannelIcon(note.channel),
                modifier = Modifier.padding(horizontal = 16.dp).testTag("customer-contact-${note.id}"),
                onClick = { nav.navigate("contact/${note.id}") },
            )
        }
        if (detail.privateNote.isNotBlank()) item { Box(Modifier.padding(horizontal = 16.dp)) { PrivateBlock("Private customer note", detail.privateNote) } }
        item { Column(Modifier.padding(horizontal = 16.dp)) { V16ServiceSectionDivider(); DailyHeading(if(capabilities.canManageRegister) "History and management" else "History"); Spacer(Modifier.height(V16ServiceUiTokens.Space.md)); V16ServiceActionStack { V16ServiceNavigationButton("Customer history",{nav.navigate("history/CUSTOMER/${detail.id}")},Modifier.fillMaxWidth()); if(capabilities.canManageRegister) V16ServiceSecondaryButton(if(detail.state=="ACTIVE") "Archive customer" else "Restore customer",{nav.navigate("lifecycle/CUSTOMER/${detail.id}/${if(detail.state=="ACTIVE")"ARCHIVE" else "RESTORE"}")},Modifier.fillMaxWidth()) } } }
    }
}

@Composable
internal fun CustomerEditorScreen(existing: CustomerDetail?, padding: PaddingValues, state: UiState, viewModel: V16ServiceViewModel, nav: NavHostController) {
    var creationDraft by rememberSaveable(existing?.id, stateSaver = CustomerCreationDraftSaver) { mutableStateOf(CustomerCreationDraft()) }
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }; var contact by rememberSaveable(existing?.id) { mutableStateOf(existing?.contactName.orEmpty()) }; var phone by rememberSaveable(existing?.id) { mutableStateOf(existing?.phone.orEmpty()) }; var email by rememberSaveable(existing?.id) { mutableStateOf(existing?.email.orEmpty()) }; var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.privateNote.orEmpty()) }
    var showAdditionalContact by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var editingAdditionalContact by remember { mutableStateOf<CustomerContactDetail?>(null) }
    var removingAdditionalContact by remember { mutableStateOf<CustomerContactDetail?>(null) }
    var additionalContactPerson by rememberSaveable(existing?.id) { mutableStateOf("") }
    var additionalContactValue by rememberSaveable(existing?.id) { mutableStateOf("") }
    var additionalContactNotes by rememberSaveable(existing?.id) { mutableStateOf("") }
    var additionalContactChannel by rememberSaveable(existing?.id) { mutableStateOf("PHONE") }
    var oneTimeCustomer by rememberSaveable(existing?.id) { mutableStateOf(existing?.customerType == CustomerType.ONE_TIME) }
    val oneTimeBlocked = existing != null && existing.customerType == CustomerType.STANDARD && !existing.canMarkOneTime
    val changed = if (existing == null) creationDraft.hasMeaningfulInput() else name!=existing.name||contact!=existing.contactName||phone!=existing.phone||email!=existing.email||note!=existing.privateNote||oneTimeCustomer != (existing.customerType == CustomerType.ONE_TIME)
    UnsavedChangesGuard(changed,nav)
    EditorColumn(padding, state, tag = "customer-editor") {
        item { DailyHeading(if (existing == null) "Add customer" else "Edit ${existing.reference}"); Text("A stable reference is assigned on Save.") }
        if (existing == null) {
            item { CustomerCreationForm(creationDraft, { creationDraft = it }) }
        } else {
            item { DailyField(name, { name = it }, "Customer name · Required"); DailyField(contact, { contact = it }, "Main contact"); DailyField(phone, { phone = it }, "Phone"); DailyField(email, { email = it }, "Email") }
            item { Row(Modifier.fillMaxWidth().testTag("customer-editor-additional-contacts"), horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm)) {
                V16ServiceSecondaryButton("List contacts", { nav.navigate("customer/contacts/${existing.id}") }, Modifier.weight(1f).testTag("customer-editor-list-contacts"))
                V16ServiceSecondaryButton("Add contact", { editingAdditionalContact = null; additionalContactPerson = ""; additionalContactValue = ""; additionalContactNotes = ""; additionalContactChannel = "PHONE"; showAdditionalContact = true }, Modifier.weight(1f).testTag("customer-editor-add-contact"))
            } }
            item { LongTextEditor(note, { note = it }, "Private customer note", true) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("customer-type-control").semantics { if (oneTimeBlocked) disabled() }) {
                    com.v16studio.v16service.ui.designsystem.V16ServiceCheckbox(
                        checked = oneTimeCustomer,
                        onCheckedChange = { checked -> if (!oneTimeBlocked || !checked) oneTimeCustomer = checked },
                        enabled = !oneTimeBlocked,
                        modifier = Modifier.testTag("customer-one-time-checkbox"),
                    )
                    Text("One-time customer (no contract)", color = if (oneTimeBlocked) LocalV16ServiceTokens.current.disabledText else MaterialTheme.colorScheme.onSurface)
                }
                if (oneTimeBlocked) Text(existing.oneTimeBlockReason ?: "This customer has recurring service plans and cannot be marked one-time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("customer-one-time-block-reason"))
            }
        }
        item {
            Button({
                if (existing == null) {
                    val input = creationDraft.toInput()
                    viewModel.createCustomerWithFirstSite(input.customer, input.site) { (customerId, _) -> nav.navigate("customer/$customerId") { popUpTo("customer/new") { inclusive = true } } }
                } else {
                    viewModel.updateCustomer(existing.id, CustomerInput(name, contact, phone, email, note, if (oneTimeCustomer) CustomerType.ONE_TIME else CustomerType.STANDARD)) { nav.popBackStack() }
                }
            }, enabled = (existing == null && creationDraft.isValidForCreate() || existing != null && name.isNotBlank()) && !state.operationInProgress, modifier = Modifier.fillMaxWidth().testTag("save-customer")) { Text("Save customer${if (existing == null) " and first site" else ""}") }
        }
    }
    if (showAdditionalContact && existing != null) CustomerContactFormDialog(
        editingContactId = editingAdditionalContact?.id,
        person = additionalContactPerson,
        onPersonChange = { additionalContactPerson = it },
        channel = additionalContactChannel,
        onChannelChange = { additionalContactChannel = it },
        value = additionalContactValue,
        onValueChange = { additionalContactValue = it },
        notes = additionalContactNotes,
        onNotesChange = { additionalContactNotes = it },
        busy = state.operationInProgress,
        testTagPrefix = "customer-editor-contact",
        onDismiss = { showAdditionalContact = false },
        onSave = { person, channel, value, notes ->
            val input = CustomerContactInput(existing.id, person, channel, value, notes)
            val editing = editingAdditionalContact
            if (editing == null) viewModel.createCustomerContact(input) { showAdditionalContact = false; viewModel.loadCustomer(existing.id) }
            else viewModel.updateCustomerContact(editing.id, input) { showAdditionalContact = false; editingAdditionalContact = null; viewModel.loadCustomer(existing.id) }
        },
    )
    removingAdditionalContact?.let { removing -> AlertDialog(
        onDismissRequest = { removingAdditionalContact = null }, title = { Text("Remove customer contact?") },
        text = { Text("This additional contact will be removed from the customer. The primary contact fields remain unchanged.") },
        dismissButton = { TextButton({ removingAdditionalContact = null }) { Text("Cancel") } },
        confirmButton = { Button({ viewModel.deleteCustomerContact(existing!!.id, removing.id) { removingAdditionalContact = null; viewModel.loadCustomer(existing.id) } }, enabled = !state.operationInProgress, modifier = Modifier.testTag("customer-editor-confirm-remove-contact")) { Text("Remove") } },
    ) }
}

@Composable
internal fun CustomerContactsScreen(detail: CustomerDetail?, padding: PaddingValues, state: UiState, viewModel: V16ServiceViewModel) {
    if (detail == null) return DailyEmpty(padding, "Reading contacts")
    var editing by remember { mutableStateOf<CustomerContactDetail?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CustomerContactDetail?>(null) }
    var person by rememberSaveable(detail.id) { mutableStateOf("") }
    var channel by rememberSaveable(detail.id) { mutableStateOf("PHONE") }
    var value by rememberSaveable(detail.id) { mutableStateOf("") }
    var notes by rememberSaveable(detail.id) { mutableStateOf("") }
    fun clearForm() { editing = null; person = ""; channel = "PHONE"; value = ""; notes = ""; showForm = false }
    if (showForm) CustomerContactFormDialog(
        editingContactId = editing?.id, person = person, onPersonChange = { person = it },
        channel = channel, onChannelChange = { channel = it }, value = value, onValueChange = { value = it },
        notes = notes, onNotesChange = { notes = it }, busy = state.operationInProgress,
        testTagPrefix = "customer-contacts", onDismiss = { showForm = false },
        onSave = { newPerson, newChannel, newValue, newNotes ->
            val input = CustomerContactInput(detail.id, newPerson, newChannel, newValue, newNotes)
            val selected = editing
            if (selected == null) viewModel.createCustomerContact(input) { clearForm(); viewModel.loadCustomer(detail.id) }
            else viewModel.updateCustomerContact(selected.id, input) { clearForm(); viewModel.loadCustomer(detail.id) }
        },
    )
    deleting?.let { contact -> AlertDialog(
        onDismissRequest = { deleting = null }, title = { Text("Delete contact?") },
        text = { Text("This removes ${contact.value} from the customer directory.") },
        dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } },
        confirmButton = { Button({ viewModel.deleteCustomerContact(detail.id, contact.id) { deleting = null; viewModel.loadCustomer(detail.id) } }, enabled = !state.operationInProgress) { Text("Delete") } },
    ) }
    LazyColumn(Modifier.padding(padding).testTag("customer-contacts-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.md)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Contacts", style = MaterialTheme.typography.titleLarge)
            V16ServiceSecondaryButton("Add contact", { clearForm(); showForm = true }, Modifier.testTag("customer-contacts-add"))
        } }
        if (detail.repeatableContacts.isEmpty()) item { Text("No contacts saved.") }
        items(detail.repeatableContacts, key = { it.id }) { contact ->
            Card(Modifier.fillMaxWidth().testTag("customer-contacts-${contact.id}")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.xs)) {
                    Text(contact.personName.ifBlank { contact.value }, style = MaterialTheme.typography.titleMedium)
                    if (contact.personName.isNotBlank()) Text(contact.value)
                    Text(contact.channel.lowercase().replaceFirstChar { it.uppercase() }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (contact.notes.isNotBlank()) Text("Notes · ${contact.notes}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.xs)) {
                        TextButton({ editing = contact; person = contact.personName; channel = contact.channel; value = contact.value; notes = contact.notes; showForm = true }, Modifier.testTag("customer-contacts-edit-${contact.id}")) { Text("Edit") }
                        TextButton({ deleting = contact }, Modifier.testTag("customer-contacts-delete-${contact.id}")) { Text("Delete") }
                        TextButton({ viewModel.moveCustomerContact(detail.id, contact.id, -1) { viewModel.loadCustomer(detail.id) } }, enabled = contact.position > 1 && !state.operationInProgress, modifier = Modifier.testTag("customer-contacts-up-${contact.id}")) { Text("Move up") }
                        TextButton({ viewModel.moveCustomerContact(detail.id, contact.id, 1) { viewModel.loadCustomer(detail.id) } }, enabled = contact.position < detail.repeatableContacts.size && !state.operationInProgress, modifier = Modifier.testTag("customer-contacts-down-${contact.id}")) { Text("Move down") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerContactFormDialog(
    editingContactId: String?,
    person: String,
    onPersonChange: (String) -> Unit,
    channel: String,
    onChannelChange: (String) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    busy: Boolean,
    testTagPrefix: String,
    onDismiss: () -> Unit,
    onSave: (person: String, channel: String, value: String, notes: String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingContactId == null) "Add customer contact" else "Edit customer contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.sm)) {
                DailyField(person, onPersonChange, "Person or label (optional)")
                Text("Channel", style = MaterialTheme.typography.labelLarge)
                V16ServiceChoiceGroup(
                    listOf("PHONE" to "Phone", "SMS" to "SMS", "WHATSAPP" to "WhatsApp", "EMAIL" to "Email", "OTHER" to "Other"),
                    channel,
                    onChannelChange,
                    testTagPrefix = "$testTagPrefix-channel",
                )
                DailyField(value, onValueChange, "Contact value · Required")
                DailyField(notes, onNotesChange, "Notes (internal, optional)")
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(
                { onSave(person, channel, value, notes) },
                enabled = value.isNotBlank() && !busy,
                modifier = Modifier.testTag("$testTagPrefix-save-contact"),
            ) { Text(if (editingContactId == null) "Save contact" else "Save changes") }
        },
    )
}

@Composable
internal fun SiteDetailScreen(detail: SiteDetail?, padding: PaddingValues, nav: NavHostController, viewModel: V16ServiceViewModel) {
    if (detail == null) return DailyEmpty(padding, "Reading site")
    val capabilities = LocalWorkspaceCapabilities.current
    val context = LocalContext.current
    var handoffStatus by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(V16ServiceUiTokens.Space.section)) {
        item {
             Text("${detail.reference} · ${detail.name}", style = MaterialTheme.typography.headlineSmall)
            Text(detail.customerName)
            if (detail.customerType == CustomerType.ONE_TIME) Text("One-time customer", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("one-time-customer-label"))
            Text(detail.address.ifBlank { "No address" })
            Text(listOf(detail.effectiveContactName, detail.effectivePhone, detail.effectiveEmail).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "No contact details" })
            if (detail.usesCustomerContact) Text("Inherited from customer", style = MaterialTheme.typography.bodySmall)
             Spacer(Modifier.height(V16ServiceUiTokens.Space.lg)); V16ServiceAdaptiveActionRow(
                actions = listOfNotNull<@Composable () -> Unit>(
                    { V16ServiceNavigationButton("Customer", { nav.navigate("customer/${detail.customerId}") }, Modifier.testTag("site-customer-link")) },
                    if(capabilities.canManageRegister) ({ V16ServiceSecondaryButton("Edit", { nav.navigate("site/edit/${detail.id}") }, Modifier.testTag("site-edit-link")) }) else null,
                    { V16ServiceSecondaryButton("Maps", { handoffStatus = handoff(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(detail.address)}")), "maps") }, Modifier.testTag("site-maps-link"), enabled = detail.address.isNotBlank()) },
                ),
                modifier = Modifier.testTag("site-top-actions"),
             )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton({handoffStatus=handoff(context,Intent(Intent.ACTION_DIAL,Uri.parse("tel:${Uri.encode(detail.effectivePhone)}")),"dialer")},enabled=detail.effectivePhone.isNotBlank()){V16ServiceIcon(V16ServiceIcons.Call,null,Modifier.size(24.dp));Spacer(Modifier.width(4.dp));Text("Call")}
                TextButton({handoffStatus=handoff(context,Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:${Uri.encode(detail.effectivePhone)}")),"SMS composer")},enabled=detail.effectivePhone.isNotBlank()){V16ServiceIcon(V16ServiceIcons.Sms,null,Modifier.size(24.dp));Spacer(Modifier.width(4.dp));Text("SMS")}
                TextButton({handoffStatus=handoff(context,Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:${Uri.encode(detail.effectiveEmail)}")),"email composer")},enabled=detail.effectiveEmail.isNotBlank()){V16ServiceIcon(V16ServiceIcons.Mail,null,Modifier.size(24.dp));Spacer(Modifier.width(4.dp));Text("Email")}
            }
            handoffStatus?.let{Text(it)}
        }
        if (detail.privateAccessNote.isNotBlank()) item { PrivateBlock("PRIVATE access note", detail.privateAccessNote) }
        item { DailyHeading("Equipment"); if(capabilities.canManageRegister) Button({ nav.navigate("equipment/new/${detail.id}") }, Modifier.fillMaxWidth().testTag("add-equipment")) { Text("Add equipment") } }
        items(detail.equipment) { equipment -> V16ServiceEntityRecord("${equipment.reference} · ${equipment.name}",metadata="${equipment.technicianIdentifier.orEmpty()} · Due ${equipment.nearestDueDate ?: "not scheduled"}"){nav.navigate("equipment/${equipment.id}")} }
         item { V16ServiceSectionDivider(); DailyHeading(if(capabilities.canManageRegister) "History and management" else "History"); Spacer(Modifier.height(V16ServiceUiTokens.Space.md)); V16ServiceActionStack { V16ServiceNavigationButton("Site history",{nav.navigate("history/SITE/${detail.id}")},Modifier.fillMaxWidth()); if(capabilities.canManageRegister) V16ServiceSecondaryButton(if(detail.state=="ACTIVE") "Archive site" else "Restore site",{nav.navigate("lifecycle/SITE/${detail.id}/${if(detail.state=="ACTIVE")"ARCHIVE" else "RESTORE"}")},Modifier.fillMaxWidth()) } }
    }
}

@Composable
internal fun SiteEditorScreen(customerId: String?, existing: SiteDetail?, padding: PaddingValues, state: UiState, viewModel: V16ServiceViewModel, nav: NavHostController) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }; var address by rememberSaveable(existing?.id) { mutableStateOf(existing?.address.orEmpty()) }; var contact by rememberSaveable(existing?.id) { mutableStateOf(existing?.contactName.orEmpty()) }; var phone by rememberSaveable(existing?.id) { mutableStateOf(existing?.phone.orEmpty()) }; var email by rememberSaveable(existing?.id) { mutableStateOf(existing?.email.orEmpty()) }; var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.privateAccessNote.orEmpty()) }; var default by rememberSaveable(existing?.id) { mutableStateOf(existing?.isDefault ?: false) }
    val originallyInherited = existing == null || (existing.contactName.isBlank() && existing.phone.isBlank() && existing.email.isBlank())
    var useCustomerContact by rememberSaveable(existing?.id) { mutableStateOf(originallyInherited) }
    UnsavedChangesGuard(name!=existing?.name.orEmpty()||address!=existing?.address.orEmpty()||contact!=existing?.contactName.orEmpty()||phone!=existing?.phone.orEmpty()||email!=existing?.email.orEmpty()||note!=existing?.privateAccessNote.orEmpty()||default!=(existing?.isDefault?:false)||useCustomerContact!=originallyInherited,nav)
    EditorColumn(padding, state, tag = "site-editor") {
        item { DailyHeading(if (existing == null) "Add site" else "Edit ${existing.reference}") }
        item { DailyField(name, { name = it }, "Site name · Required"); DailyField(address, { address = it }, "Address"); Row(verticalAlignment = Alignment.CenterVertically) { com.v16studio.v16service.ui.designsystem.V16ServiceCheckbox(useCustomerContact, { useCustomerContact = it }, contentDescription = "Use customer contact"); Text("Use customer contact") }; if (!useCustomerContact) { DailyField(contact, { contact = it }, "Contact override"); DailyField(phone, { phone = it }, "Phone override"); DailyField(email, { email = it }, "Email override") } else Text("Customer contact is inherited; any staged overrides remain available if inheritance is turned off before Save.", style = MaterialTheme.typography.bodySmall) }
        item { LongTextEditor(note, { note = it }, "PRIVATE access note", true); Row(verticalAlignment = Alignment.CenterVertically) { com.v16studio.v16service.ui.designsystem.V16ServiceCheckbox(default, { default = it }, contentDescription = "Default site for this customer"); Text("Default site for this customer") } }
        item { Button({ val input = SiteInput(name, address, contact.takeUnless { useCustomerContact }.orEmpty(), phone.takeUnless { useCustomerContact }.orEmpty(), email.takeUnless { useCustomerContact }.orEmpty(), note, default); if (existing == null) viewModel.createSite(customerId!!, input) { nav.navigate("site/$it") { popUpTo("site/new/$customerId") { inclusive = true } } } else viewModel.updateSite(existing.id, input) { nav.popBackStack() } }, enabled = name.isNotBlank() && !state.operationInProgress, modifier = Modifier.fillMaxWidth().testTag("save-site")) { Text("Save site") } }
    }
}
