package com.v16studio.serviceloop.ui

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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.domain.*
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopActionStack
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopAdaptiveActionRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopButtonAdapter as Button
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopContentTabs
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDenseNavigableRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopEntityRecord
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSectionDivider
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSecondaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNavigationButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextButtonAdapter as TextButton
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.designsystem.OperationalWorkGateway
import com.v16studio.serviceloop.domain.OperationalDashboardProjection
import com.v16studio.serviceloop.domain.OperationalWorkClassifier
import com.v16studio.serviceloop.domain.OperationalWorkState
import java.time.LocalDate
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons

@Composable
internal fun CustomerDetailScreen(
    detail: CustomerDetail?,
    padding: PaddingValues,
    nav: NavHostController,
    viewModel: ServiceLoopViewModel,
    workDashboard: OperationalDashboardProjection? = null,
    businessDate: LocalDate = LocalDate.now(),
    dueSoonHorizonDays: Int = 14,
) {
    if (detail == null) return DailyEmpty(padding, "Reading customer")
    val capabilities = LocalWorkspaceCapabilities.current
    val context = LocalContext.current
    var handoffStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable(detail.id) { mutableStateOf("SITES") }
    val colors = LocalServiceLoopTokens.current
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(0.dp, 8.dp, 0.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)) {
        item {
            Column(Modifier.fillMaxWidth().background(colors.surface)) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("${detail.reference} · ${detail.name}", style = MaterialTheme.typography.headlineSmall); Text(detail.contactName.ifBlank { "No main contact" }); Text(listOf(detail.phone, detail.email).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "No phone or email" }); Spacer(Modifier.height(ServiceLoopUiTokens.Space.lg)); Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement = Arrangement.spacedBy(8.dp)) { if(capabilities.canManageRegister) ServiceLoopSecondaryButton("Edit",{nav.navigate("customer/edit/${detail.id}")},Modifier.weight(1f).fillMaxHeight()); ServiceLoopSecondaryButton("Record contact",{nav.navigate("contact/new/${detail.id}")},Modifier.weight(1f).fillMaxHeight()) }; Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextButton({handoffStatus=handoff(context,Intent(Intent.ACTION_DIAL,Uri.parse("tel:${Uri.encode(detail.phone)}")),"dialer")},enabled=detail.phone.isNotBlank()){ServiceLoopIcon(ServiceLoopIcons.Call,null,Modifier.size(24.dp));Spacer(Modifier.width(4.dp));Text("Call")}; TextButton({handoffStatus=handoff(context,Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:${Uri.encode(detail.phone)}")),"SMS composer")},enabled=detail.phone.isNotBlank()){ServiceLoopIcon(ServiceLoopIcons.Sms,null,Modifier.size(24.dp));Spacer(Modifier.width(4.dp));Text("SMS")}; TextButton({handoffStatus=handoff(context,Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:${Uri.encode(detail.email)}")),"email composer")},enabled=detail.email.isNotBlank()){ServiceLoopIcon(ServiceLoopIcons.Mail,null,Modifier.size(24.dp));Spacer(Modifier.width(4.dp));Text("Email")} }; handoffStatus?.let{Text(it)} }
                if (detail.customerType == CustomerType.ONE_TIME) {
                    Text("One-time customer", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("one-time-customer-label"))
                }
                workDashboard?.takeIf { it.totalItemCount > 0 }?.mostUrgentState?.let { urgency ->
                    Spacer(Modifier.height(ServiceLoopUiTokens.Space.md))
                    OperationalWorkGateway(workDashboard.totalItemCount, urgency, onClick = { nav.navigate("work-dashboard/customer/${detail.id}") }, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(ServiceLoopUiTokens.Space.md))
                }
                if ((workDashboard?.totalItemCount ?: 0) == 0) Spacer(Modifier.height(ServiceLoopUiTokens.Space.md))
                ServiceLoopContentTabs(listOf("SITES" to "Sites", "EQUIPMENT" to "Equipment"),tab,{tab=it})
            }
        }
        if (tab == "SITES") { item { Column(Modifier.padding(horizontal = 16.dp)) { DailyHeading("Sites"); if(capabilities.canManageRegister){ Spacer(Modifier.height(ServiceLoopUiTokens.Space.md)); Button({ nav.navigate("site/new/${detail.id}") }, Modifier.fillMaxWidth().testTag("add-site")) { Text("Add site") } } } }; items(detail.sites) { site -> ServiceLoopEntityRecord("${site.reference} · ${site.name}",site.address,"${site.equipmentCount} equipment${if(site.isDefault) " · Default" else ""}", modifier = Modifier.padding(horizontal = 16.dp)){nav.navigate("site/${site.id}")} } }
        else { item { Box(Modifier.padding(horizontal = 16.dp)) { DailyHeading("Equipment") } }; items(detail.equipment) { item -> ServiceLoopEntityRecord("${item.technicianIdentifier ?: item.reference} · ${item.name}",item.siteName,"Next due ${item.nearestDueDate ?: "not scheduled"}", modifier = Modifier.padding(horizontal = 16.dp)){nav.navigate("equipment/${item.id}")} } }
        item { Column(Modifier.padding(horizontal = 16.dp)) { ServiceLoopSectionDivider(); DailyHeading("Active follow-ups"); Spacer(Modifier.height(ServiceLoopUiTokens.Space.md)); ServiceLoopPrimaryButton("Add follow-up",{nav.navigate("follow-up/new/${detail.id}")},Modifier.fillMaxWidth()) } }
        items(detail.openFollowUps) { follow -> ServiceLoopEntityRecord("${follow.reference} · ${follow.title}",metadata="Due ${follow.dueDate}", modifier = Modifier.padding(horizontal = 16.dp), operationalState = OperationalWorkClassifier.classifyFollowUp(follow.state, follow.dueDate, businessDate, dueSoonHorizonDays)){nav.navigate("follow-up/${follow.id}")} }
        if (detail.recentContacts.isNotEmpty()) item { Box(Modifier.padding(horizontal = 16.dp)) { DailyHeading("Recent contact") } }
        items(detail.recentContacts, key = { it.id }) { note ->
            ServiceLoopDenseNavigableRow(
                title = "${note.reference} · ${contactChannelLabel(note.channel)}",
                context = note.outcome,
                status = if (note.enteredInError) "Entered in error" else null,
                leadingIcon = contactChannelIcon(note.channel),
                modifier = Modifier.padding(horizontal = 16.dp).testTag("customer-contact-${note.id}"),
                onClick = { nav.navigate("contact/${note.id}") },
            )
        }
        if (detail.privateNote.isNotBlank()) item { Box(Modifier.padding(horizontal = 16.dp)) { PrivateBlock("Private customer note", detail.privateNote) } }
        item { Column(Modifier.padding(horizontal = 16.dp)) { ServiceLoopSectionDivider(); DailyHeading(if(capabilities.canManageRegister) "History and management" else "History"); Spacer(Modifier.height(ServiceLoopUiTokens.Space.md)); ServiceLoopActionStack { ServiceLoopNavigationButton("Customer history",{nav.navigate("history/CUSTOMER/${detail.id}")},Modifier.fillMaxWidth()); if(capabilities.canManageRegister) ServiceLoopSecondaryButton(if(detail.state=="ACTIVE") "Archive customer" else "Restore customer",{nav.navigate("lifecycle/CUSTOMER/${detail.id}/${if(detail.state=="ACTIVE")"ARCHIVE" else "RESTORE"}")},Modifier.fillMaxWidth()) } } }
    }
}

@Composable
internal fun CustomerEditorScreen(existing: CustomerDetail?, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    var creationDraft by rememberSaveable(existing?.id, stateSaver = CustomerCreationDraftSaver) { mutableStateOf(CustomerCreationDraft()) }
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }; var contact by rememberSaveable(existing?.id) { mutableStateOf(existing?.contactName.orEmpty()) }; var phone by rememberSaveable(existing?.id) { mutableStateOf(existing?.phone.orEmpty()) }; var email by rememberSaveable(existing?.id) { mutableStateOf(existing?.email.orEmpty()) }; var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.privateNote.orEmpty()) }
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
            item { LongTextEditor(note, { note = it }, "Private customer note", true) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("customer-type-control")) {
                    Checkbox(
                        checked = oneTimeCustomer,
                        onCheckedChange = { checked -> if (!oneTimeBlocked || !checked) oneTimeCustomer = checked },
                        enabled = !oneTimeBlocked,
                        modifier = Modifier.testTag("customer-one-time-checkbox"),
                    )
                    Text("One-time customer (no contract)")
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
}

@Composable
internal fun SiteDetailScreen(detail: SiteDetail?, padding: PaddingValues, nav: NavHostController, viewModel: ServiceLoopViewModel) {
    if (detail == null) return DailyEmpty(padding, "Reading site")
    val capabilities = LocalWorkspaceCapabilities.current
    val context = LocalContext.current
    var handoffStatus by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)) {
        item {
             Text("${detail.reference} · ${detail.name}", style = MaterialTheme.typography.headlineSmall)
            Text(detail.customerName)
            if (detail.customerType == CustomerType.ONE_TIME) Text("One-time customer", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("one-time-customer-label"))
            Text(detail.address.ifBlank { "No address" })
            Text(listOf(detail.effectiveContactName, detail.effectivePhone, detail.effectiveEmail).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "No contact details" })
            if (detail.usesCustomerContact) Text("Inherited from customer", style = MaterialTheme.typography.bodySmall)
             Spacer(Modifier.height(ServiceLoopUiTokens.Space.lg)); ServiceLoopAdaptiveActionRow(
                actions = listOfNotNull<@Composable () -> Unit>(
                    { ServiceLoopNavigationButton("Customer", { nav.navigate("customer/${detail.customerId}") }, Modifier.testTag("site-customer-link")) },
                    if(capabilities.canManageRegister) ({ ServiceLoopSecondaryButton("Edit", { nav.navigate("site/edit/${detail.id}") }, Modifier.testTag("site-edit-link")) }) else null,
                    { ServiceLoopSecondaryButton("Maps", { handoffStatus = handoff(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(detail.address)}")), "maps") }, Modifier.testTag("site-maps-link"), enabled = detail.address.isNotBlank()) },
                ),
                modifier = Modifier.testTag("site-top-actions"),
             )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton({handoffStatus=handoff(context,Intent(Intent.ACTION_DIAL,Uri.parse("tel:${Uri.encode(detail.effectivePhone)}")),"dialer")},enabled=detail.effectivePhone.isNotBlank()){ServiceLoopIcon(ServiceLoopIcons.Call,null,Modifier.size(24.dp));Spacer(Modifier.width(4.dp));Text("Call")}
                TextButton({handoffStatus=handoff(context,Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:${Uri.encode(detail.effectivePhone)}")),"SMS composer")},enabled=detail.effectivePhone.isNotBlank()){ServiceLoopIcon(ServiceLoopIcons.Sms,null,Modifier.size(24.dp));Spacer(Modifier.width(4.dp));Text("SMS")}
                TextButton({handoffStatus=handoff(context,Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:${Uri.encode(detail.effectiveEmail)}")),"email composer")},enabled=detail.effectiveEmail.isNotBlank()){ServiceLoopIcon(ServiceLoopIcons.Mail,null,Modifier.size(24.dp));Spacer(Modifier.width(4.dp));Text("Email")}
            }
            handoffStatus?.let{Text(it)}
        }
        if (detail.privateAccessNote.isNotBlank()) item { PrivateBlock("PRIVATE access note", detail.privateAccessNote) }
        item { DailyHeading("Equipment"); if(capabilities.canManageRegister) Button({ nav.navigate("equipment/new/${detail.id}") }, Modifier.fillMaxWidth().testTag("add-equipment")) { Text("Add equipment") } }
        items(detail.equipment) { equipment -> ServiceLoopEntityRecord("${equipment.reference} · ${equipment.name}",metadata="${equipment.technicianIdentifier.orEmpty()} · Due ${equipment.nearestDueDate ?: "not scheduled"}"){nav.navigate("equipment/${equipment.id}")} }
         item { ServiceLoopSectionDivider(); DailyHeading(if(capabilities.canManageRegister) "History and management" else "History"); Spacer(Modifier.height(ServiceLoopUiTokens.Space.md)); ServiceLoopActionStack { ServiceLoopNavigationButton("Site history",{nav.navigate("history/SITE/${detail.id}")},Modifier.fillMaxWidth()); if(capabilities.canManageRegister) ServiceLoopSecondaryButton(if(detail.state=="ACTIVE") "Archive site" else "Restore site",{nav.navigate("lifecycle/SITE/${detail.id}/${if(detail.state=="ACTIVE")"ARCHIVE" else "RESTORE"}")},Modifier.fillMaxWidth()) } }
    }
}

@Composable
internal fun SiteEditorScreen(customerId: String?, existing: SiteDetail?, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }; var address by rememberSaveable(existing?.id) { mutableStateOf(existing?.address.orEmpty()) }; var contact by rememberSaveable(existing?.id) { mutableStateOf(existing?.contactName.orEmpty()) }; var phone by rememberSaveable(existing?.id) { mutableStateOf(existing?.phone.orEmpty()) }; var email by rememberSaveable(existing?.id) { mutableStateOf(existing?.email.orEmpty()) }; var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.privateAccessNote.orEmpty()) }; var default by rememberSaveable(existing?.id) { mutableStateOf(existing?.isDefault ?: false) }
    val originallyInherited = existing == null || (existing.contactName.isBlank() && existing.phone.isBlank() && existing.email.isBlank())
    var useCustomerContact by rememberSaveable(existing?.id) { mutableStateOf(originallyInherited) }
    UnsavedChangesGuard(name!=existing?.name.orEmpty()||address!=existing?.address.orEmpty()||contact!=existing?.contactName.orEmpty()||phone!=existing?.phone.orEmpty()||email!=existing?.email.orEmpty()||note!=existing?.privateAccessNote.orEmpty()||default!=(existing?.isDefault?:false)||useCustomerContact!=originallyInherited,nav)
    EditorColumn(padding, state, tag = "site-editor") {
        item { DailyHeading(if (existing == null) "Add site" else "Edit ${existing.reference}") }
        item { DailyField(name, { name = it }, "Site name · Required"); DailyField(address, { address = it }, "Address"); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(useCustomerContact, { useCustomerContact = it }); Text("Use customer contact") }; if (!useCustomerContact) { DailyField(contact, { contact = it }, "Contact override"); DailyField(phone, { phone = it }, "Phone override"); DailyField(email, { email = it }, "Email override") } else Text("Customer contact is inherited; any staged overrides remain available if inheritance is turned off before Save.", style = MaterialTheme.typography.bodySmall) }
        item { LongTextEditor(note, { note = it }, "PRIVATE access note", true); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(default, { default = it }); Text("Default site for this customer") } }
        item { Button({ val input = SiteInput(name, address, contact.takeUnless { useCustomerContact }.orEmpty(), phone.takeUnless { useCustomerContact }.orEmpty(), email.takeUnless { useCustomerContact }.orEmpty(), note, default); if (existing == null) viewModel.createSite(customerId!!, input) { nav.navigate("site/$it") { popUpTo("site/new/$customerId") { inclusive = true } } } else viewModel.updateSite(existing.id, input) { nav.popBackStack() } }, enabled = name.isNotBlank() && !state.operationInProgress, modifier = Modifier.fillMaxWidth().testTag("save-site")) { Text("Save site") } }
    }
}
