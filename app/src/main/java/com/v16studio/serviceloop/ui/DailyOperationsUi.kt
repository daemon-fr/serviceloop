package com.v16studio.serviceloop.ui

import com.v16studio.serviceloop.ui.designsystem.ServiceLoopButtonAdapter as Button
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopOutlinedButtonAdapter as OutlinedButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextButtonAdapter as TextButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopIconButtonAdapter as IconButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextFieldAdapter as OutlinedTextField
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopCardAdapter as Card
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopElevatedCardAdapter as ElevatedCard
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDenseNavigableRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopContentTabs
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChoiceGroup
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChoiceChip
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopEntityRecord
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFilterSelector
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFilterSelectorRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSecondaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSectionDivider
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopActionStack
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDangerTonalButton

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import androidx.core.content.FileProvider
import com.v16studio.serviceloop.domain.*
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopLongTextEditor
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSurfaceCard
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextField
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons
import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CustomerDetailScreen(detail: CustomerDetail?, padding: PaddingValues, nav: NavHostController, viewModel: ServiceLoopViewModel) {
    if (detail == null) return DailyEmpty(padding, "Reading customer")
    val context = LocalContext.current
    var handoffStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable(detail.id) { mutableStateOf("SITES") }
    val colors = LocalServiceLoopTokens.current
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(0.dp, 8.dp, 0.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)) {
        item {
            Column(Modifier.fillMaxWidth().background(colors.surface)) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("${detail.reference} · ${detail.name}", style = MaterialTheme.typography.headlineSmall); Text(detail.contactName.ifBlank { "No main contact" }); Text(listOf(detail.phone, detail.email).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "No phone or email" }); Spacer(Modifier.height(ServiceLoopUiTokens.Space.lg)); Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement = Arrangement.spacedBy(8.dp)) { ServiceLoopSecondaryButton("Edit",{nav.navigate("customer/edit/${detail.id}")},Modifier.weight(1f).fillMaxHeight()); ServiceLoopSecondaryButton("Record contact",{nav.navigate("contact/new/${detail.id}")},Modifier.weight(1f).fillMaxHeight()) }; Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextButton({handoffStatus=handoff(context,Intent(Intent.ACTION_DIAL,Uri.parse("tel:${Uri.encode(detail.phone)}")),"dialer")},enabled=detail.phone.isNotBlank()){ServiceLoopIcon(ServiceLoopIcons.Call,null,Modifier.size(24.dp));Spacer(Modifier.width(4.dp));Text("Call")}; TextButton({handoffStatus=handoff(context,Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:${Uri.encode(detail.phone)}")),"SMS composer")},enabled=detail.phone.isNotBlank()){ServiceLoopIcon(ServiceLoopIcons.Sms,null,Modifier.size(24.dp));Spacer(Modifier.width(4.dp));Text("SMS")}; TextButton({handoffStatus=handoff(context,Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:${Uri.encode(detail.email)}")),"email composer")},enabled=detail.email.isNotBlank()){ServiceLoopIcon(ServiceLoopIcons.Mail,null,Modifier.size(24.dp));Spacer(Modifier.width(4.dp));Text("Email")} }; handoffStatus?.let{Text(it)}; Text("External handoff does not record a successful contact.",style=MaterialTheme.typography.bodySmall); Spacer(Modifier.height(ServiceLoopUiTokens.Space.lg)) }
                ServiceLoopContentTabs(listOf("SITES" to "Sites", "EQUIPMENT" to "Equipment"),tab,{tab=it})
            }
        }
        if (tab == "SITES") { item { Column(Modifier.padding(horizontal = 16.dp)) { DailyHeading("Sites"); Spacer(Modifier.height(ServiceLoopUiTokens.Space.md)); Button({ nav.navigate("site/new/${detail.id}") }, Modifier.fillMaxWidth().testTag("add-site")) { Text("Add site") } } }; items(detail.sites) { site -> ServiceLoopEntityRecord("${site.reference} · ${site.name}",site.address,"${site.equipmentCount} equipment${if(site.isDefault) " · Default" else ""}", modifier = Modifier.padding(horizontal = 16.dp)){nav.navigate("site/${site.id}")} } }
        else { item { Box(Modifier.padding(horizontal = 16.dp)) { DailyHeading("Equipment") } }; items(detail.equipment) { item -> ServiceLoopEntityRecord("${item.technicianIdentifier ?: item.reference} · ${item.name}",item.siteName,"Next due ${item.nearestDueDate ?: "not scheduled"}", modifier = Modifier.padding(horizontal = 16.dp)){nav.navigate("equipment/${item.id}")} } }
        item { Column(Modifier.padding(horizontal = 16.dp)) { ServiceLoopSectionDivider(); DailyHeading("Active follow-ups"); Spacer(Modifier.height(ServiceLoopUiTokens.Space.md)); ServiceLoopPrimaryButton("Add follow-up",{nav.navigate("follow-up/new/${detail.id}")},Modifier.fillMaxWidth()) } }
        items(detail.openFollowUps) { follow -> ServiceLoopEntityRecord("${follow.reference} · ${follow.title}",metadata="Due ${follow.dueDate}", modifier = Modifier.padding(horizontal = 16.dp)){nav.navigate("follow-up/${follow.id}")} }
        if (detail.recentContacts.isNotEmpty()) item { Box(Modifier.padding(horizontal = 16.dp)) { DailyHeading("Recent contact") } }
        items(detail.recentContacts) { note -> var errorReason by rememberSaveable(note.id){mutableStateOf("")}; Column(Modifier.padding(horizontal = 16.dp)) { Text("${note.reference} · ${note.channel} · ${note.outcome}${if (note.enteredInError) " · Entered in error: ${note.errorReason}" else ""}"); if(!note.enteredInError){ DailyField(errorReason,{errorReason=it},"Entered-in-error reason"); TextButton({viewModel.markContactNoteEnteredInError(note.id,errorReason){viewModel.loadCustomer(detail.id)}},enabled=errorReason.isNotBlank()){Text("Mark entered in error")} } } }
        if (detail.privateNote.isNotBlank()) item { Box(Modifier.padding(horizontal = 16.dp)) { PrivateBlock("Private customer note", detail.privateNote) } }
        item { Column(Modifier.padding(horizontal = 16.dp)) { ServiceLoopSectionDivider(); DailyHeading("History and management"); Spacer(Modifier.height(ServiceLoopUiTokens.Space.md)); ServiceLoopActionStack { ServiceLoopSecondaryButton("Customer history",{nav.navigate("history/CUSTOMER/${detail.id}")},Modifier.fillMaxWidth()); ServiceLoopSecondaryButton(if(detail.state=="ACTIVE") "Archive customer" else "Restore customer",{nav.navigate("lifecycle/CUSTOMER/${detail.id}/${if(detail.state=="ACTIVE")"ARCHIVE" else "RESTORE"}")},Modifier.fillMaxWidth()) } } }
    }
}

@Composable
internal fun CustomerEditorScreen(existing: CustomerDetail?, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }; var contact by rememberSaveable(existing?.id) { mutableStateOf(existing?.contactName.orEmpty()) }; var phone by rememberSaveable(existing?.id) { mutableStateOf(existing?.phone.orEmpty()) }; var email by rememberSaveable(existing?.id) { mutableStateOf(existing?.email.orEmpty()) }; var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.privateNote.orEmpty()) }
    var firstSiteName by rememberSaveable(existing?.id) { mutableStateOf("") }; var firstSiteAddress by rememberSaveable(existing?.id) { mutableStateOf("") }
    UnsavedChangesGuard(name!=existing?.name.orEmpty()||contact!=existing?.contactName.orEmpty()||phone!=existing?.phone.orEmpty()||email!=existing?.email.orEmpty()||note!=existing?.privateNote.orEmpty()||firstSiteName.isNotBlank()||firstSiteAddress.isNotBlank(),nav)
    EditorColumn(padding, state) {
        item { DailyHeading(if (existing == null) "Add customer" else "Edit ${existing.reference}"); Text("A stable reference is assigned on Save.") }
        item { DailyField(name, { name = it }, "Customer name · Required"); DailyField(contact, { contact = it }, "Main contact"); DailyField(phone, { phone = it }, "Phone"); DailyField(email, { email = it }, "Email") }
        if (existing == null) item { DailyHeading("First site"); Text("Every new customer starts with a default site. Blank contact fields inherit the customer contact."); DailyField(firstSiteName, { firstSiteName = it }, "Site name · Required"); DailyField(firstSiteAddress, { firstSiteAddress = it }, "Site address") }
        item { LongTextEditor(note, { note = it }, "Private customer note", true) }
        item { Button({ val input = CustomerInput(name, contact, phone, email, note); if (existing == null) viewModel.createCustomerWithFirstSite(input, SiteInput(firstSiteName, firstSiteAddress, isDefault = true)) { (customerId, _) -> nav.navigate("customer/$customerId") { popUpTo("customer/new") { inclusive = true } } } else viewModel.updateCustomer(existing.id, input) { nav.popBackStack() } }, enabled = name.isNotBlank() && (existing != null || firstSiteName.isNotBlank()) && !state.operationInProgress, modifier = Modifier.fillMaxWidth()) { Text("Save customer${if (existing == null) " and first site" else ""}") } }
    }
}

@Composable
internal fun SiteDetailScreen(detail: SiteDetail?, padding: PaddingValues, nav: NavHostController) {
    if (detail == null) return DailyEmpty(padding, "Reading site")
    val context = LocalContext.current
    var handoffStatus by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)) {
        item {
            Text("${detail.reference} · ${detail.name}", style = MaterialTheme.typography.headlineSmall)
            Text(detail.customerName)
            Text(detail.address.ifBlank { "No address" })
            Text(listOf(detail.effectiveContactName, detail.effectivePhone, detail.effectiveEmail).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "No contact details" })
            if (detail.usesCustomerContact) Text("Inherited from customer", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(ServiceLoopUiTokens.Space.lg)); Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement = Arrangement.spacedBy(8.dp)) { ServiceLoopSecondaryButton("Edit",{nav.navigate("site/edit/${detail.id}")},Modifier.weight(1f).fillMaxHeight()); ServiceLoopSecondaryButton("Maps",{handoffStatus=handoff(context,Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(detail.address)}")),"maps")},Modifier.weight(1f).fillMaxHeight(),enabled=detail.address.isNotBlank()) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton({handoffStatus=handoff(context,Intent(Intent.ACTION_DIAL,Uri.parse("tel:${Uri.encode(detail.effectivePhone)}")),"dialer")},enabled=detail.effectivePhone.isNotBlank()){ServiceLoopIcon(ServiceLoopIcons.Call,null,Modifier.size(24.dp));Spacer(Modifier.width(4.dp));Text("Call")}
                TextButton({handoffStatus=handoff(context,Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:${Uri.encode(detail.effectivePhone)}")),"SMS composer")},enabled=detail.effectivePhone.isNotBlank()){ServiceLoopIcon(ServiceLoopIcons.Sms,null,Modifier.size(24.dp));Spacer(Modifier.width(4.dp));Text("SMS")}
                TextButton({handoffStatus=handoff(context,Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:${Uri.encode(detail.effectiveEmail)}")),"email composer")},enabled=detail.effectiveEmail.isNotBlank()){ServiceLoopIcon(ServiceLoopIcons.Mail,null,Modifier.size(24.dp));Spacer(Modifier.width(4.dp));Text("Email")}
            }
            handoffStatus?.let{Text(it)}
            Text("External handoff does not record a successful contact.",style=MaterialTheme.typography.bodySmall)
        }
        if (detail.privateAccessNote.isNotBlank()) item { PrivateBlock("PRIVATE access note", detail.privateAccessNote) }
        item { DailyHeading("Equipment"); Button({ nav.navigate("equipment/new/${detail.id}") }, Modifier.fillMaxWidth().testTag("add-equipment")) { Text("Add equipment") } }
        items(detail.equipment) { equipment -> ServiceLoopEntityRecord("${equipment.reference} · ${equipment.name}",metadata="${equipment.technicianIdentifier.orEmpty()} · Due ${equipment.nearestDueDate ?: "not scheduled"}"){nav.navigate("equipment/${equipment.id}")} }
        item { ServiceLoopSectionDivider(); DailyHeading("History and management"); Spacer(Modifier.height(ServiceLoopUiTokens.Space.md)); ServiceLoopActionStack { ServiceLoopSecondaryButton("Site history",{nav.navigate("history/SITE/${detail.id}")},Modifier.fillMaxWidth()); ServiceLoopSecondaryButton(if(detail.state=="ACTIVE") "Archive site" else "Restore site",{nav.navigate("lifecycle/SITE/${detail.id}/${if(detail.state=="ACTIVE")"ARCHIVE" else "RESTORE"}")},Modifier.fillMaxWidth()) } }
    }
}

@Composable
internal fun SiteEditorScreen(customerId: String?, existing: SiteDetail?, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }; var address by rememberSaveable(existing?.id) { mutableStateOf(existing?.address.orEmpty()) }; var contact by rememberSaveable(existing?.id) { mutableStateOf(existing?.contactName.orEmpty()) }; var phone by rememberSaveable(existing?.id) { mutableStateOf(existing?.phone.orEmpty()) }; var email by rememberSaveable(existing?.id) { mutableStateOf(existing?.email.orEmpty()) }; var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.privateAccessNote.orEmpty()) }; var default by rememberSaveable(existing?.id) { mutableStateOf(existing?.isDefault ?: false) }
    val originallyInherited = existing == null || (existing.contactName.isBlank() && existing.phone.isBlank() && existing.email.isBlank())
    var useCustomerContact by rememberSaveable(existing?.id) { mutableStateOf(originallyInherited) }
    UnsavedChangesGuard(name!=existing?.name.orEmpty()||address!=existing?.address.orEmpty()||contact!=existing?.contactName.orEmpty()||phone!=existing?.phone.orEmpty()||email!=existing?.email.orEmpty()||note!=existing?.privateAccessNote.orEmpty()||default!=(existing?.isDefault?:false)||useCustomerContact!=originallyInherited,nav)
    EditorColumn(padding, state) {
        item { DailyHeading(if (existing == null) "Add site" else "Edit ${existing.reference}") }
        item { DailyField(name, { name = it }, "Site name · Required"); DailyField(address, { address = it }, "Address"); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(useCustomerContact, { useCustomerContact = it }); Text("Use customer contact") }; if (!useCustomerContact) { DailyField(contact, { contact = it }, "Contact override"); DailyField(phone, { phone = it }, "Phone override"); DailyField(email, { email = it }, "Email override") } else Text("Customer contact is inherited; any staged overrides remain available if inheritance is turned off before Save.", style = MaterialTheme.typography.bodySmall) }
        item { LongTextEditor(note, { note = it }, "PRIVATE access note", true); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(default, { default = it }); Text("Default site for this customer") } }
        item { Button({ val input = SiteInput(name, address, contact.takeUnless { useCustomerContact }.orEmpty(), phone.takeUnless { useCustomerContact }.orEmpty(), email.takeUnless { useCustomerContact }.orEmpty(), note, default); if (existing == null) viewModel.createSite(customerId!!, input) { nav.navigate("site/$it") { popUpTo("site/new/$customerId") { inclusive = true } } } else viewModel.updateSite(existing.id, input) { nav.popBackStack() } }, enabled = name.isNotBlank() && !state.operationInProgress, modifier = Modifier.fillMaxWidth()) { Text("Save site") } }
    }
}

@Composable
internal fun EquipmentEditorScreen(siteId: String?, existing: EquipmentDetail?, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }; var identifier by rememberSaveable(existing?.id) { mutableStateOf(existing?.technicianIdentifier.orEmpty()) }; var make by rememberSaveable(existing?.id) { mutableStateOf(existing?.make.orEmpty()) }; var model by rememberSaveable(existing?.id) { mutableStateOf(existing?.model.orEmpty()) }; var serial by rememberSaveable(existing?.id) { mutableStateOf(existing?.serialNumber.orEmpty()) }; var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.privateNote.orEmpty()) }
    UnsavedChangesGuard(name!=existing?.name.orEmpty()||identifier!=existing?.technicianIdentifier.orEmpty()||make!=existing?.make.orEmpty()||model!=existing?.model.orEmpty()||serial!=existing?.serialNumber.orEmpty()||note!=existing?.privateNote.orEmpty(),nav)
    EditorColumn(padding, state) {
        item { DailyHeading(if (existing == null) "Add equipment" else "Edit ${existing.reference}") }
        item { DailyField(name, { name = it }, "Equipment name · Required"); DailyField(identifier, { identifier = it }, "Technician identifier"); DailyField(make, { make = it }, "Make"); DailyField(model, { model = it }, "Model"); DailyField(serial, { serial = it }, "Serial") }
        item { LongTextEditor(note, { note = it }, "Private equipment note", true) }
        item { Button({ val input = EquipmentInput(name, identifier, make, model, serial, note); if (existing == null) viewModel.createEquipment(siteId!!, input) { nav.navigate("equipment/$it") { popUpTo("equipment/new/$siteId") { inclusive = true } } } else viewModel.updateEquipment(existing.id, input) { nav.popBackStack() } }, enabled = name.isNotBlank() && !state.operationInProgress, modifier = Modifier.fillMaxWidth()) { Text("Save equipment") } }
    }
}

@Composable
internal fun PlanDetailScreen(plan: PlanDetail?, padding: PaddingValues, nav: NavHostController) {
    if (plan == null) return DailyEmpty(padding, "Reading service plan")
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)) { item { Text("${plan.reference} · ${plan.name}", style = MaterialTheme.typography.headlineSmall); Text(plan.equipmentName); Text("Every ${plan.intervalCount} ${plan.intervalUnit.lowercase()} · Due ${plan.dueDate}"); Text("Current obligation remains separate from bookings and contact."); ServiceLoopActionStack { ServiceLoopPrimaryButton("Edit plan",{ nav.navigate("plan/edit/${plan.id}") }, Modifier.fillMaxWidth(), enabled=plan.state!="ENDED"); ServiceLoopSecondaryButton("Create visit",{ nav.navigate("visit/new/${plan.id}") }, Modifier.fillMaxWidth(), enabled=plan.state=="ACTIVE"); if(plan.state=="ACTIVE") ServiceLoopSecondaryButton("Pause plan",{nav.navigate("lifecycle/PLAN/${plan.id}/PAUSE")},Modifier.fillMaxWidth()) else if(plan.state=="PAUSED") ServiceLoopSecondaryButton("Resume plan",{nav.navigate("lifecycle/PLAN/${plan.id}/RESUME")},Modifier.fillMaxWidth()); if(plan.state!="ENDED") ServiceLoopDangerTonalButton("End plan",{nav.navigate("lifecycle/PLAN/${plan.id}/END")},Modifier.fillMaxWidth()) } } }
}

@Composable
internal fun PlanEditorScreen(equipmentId: String?, existing: PlanDetail?, templates: List<TemplateSummary>, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }; var count by rememberSaveable(existing?.id) { mutableStateOf(existing?.intervalCount?.toString() ?: "6") }; var unit by rememberSaveable(existing?.id) { mutableStateOf(existing?.intervalUnit ?: "MONTHS") }; var due by rememberSaveable(existing?.id) { mutableStateOf(existing?.dueDate ?: state.businessDate.toString()) }; var templateId by rememberSaveable(existing?.id) { mutableStateOf(existing?.reusableTemplateId) }; var dueReason by rememberSaveable(existing?.id){mutableStateOf("")}
    UnsavedChangesGuard(name!=existing?.name.orEmpty()||count!=(existing?.intervalCount?.toString()?:"6")||unit!=(existing?.intervalUnit?:"MONTHS")||due!=(existing?.dueDate?:state.businessDate.toString())||templateId!=existing?.reusableTemplateId||dueReason.isNotBlank(),nav)
    EditorColumn(padding, state, "plan-editor") {
        item { DailyHeading(if (existing == null) "Add recurring service plan" else "Edit ${existing.reference}"); DailyField(name, { name = it }, "Plan name · Required"); DailyField(count, { count = it }, "Positive interval") }
        item { ServiceLoopChoiceGroup(listOf("DAYS","WEEKS","MONTHS","YEARS").map{it to it.lowercase().replaceFirstChar(Char::uppercase)},unit,{unit=it}); DailyField(due, { due = it }, "Next due date · YYYY-MM-DD"); if(existing!=null&&due!=existing.dueDate) LongTextEditor(dueReason,{dueReason=it},"Due-date change reason · Required",false) }
        item { Text("Reusable inspection template", fontWeight = FontWeight.Medium); FilterChip(templateId == null, { templateId = null }, { Text("None") }); templates.forEach { template -> FilterChip(templateId == template.id, { templateId = template.id }, { Text("${template.name} · r${template.revisionNumber}") }) } }
        item { Button({ val parsed = count.toIntOrNull() ?: 0; val input = PlanInput(name, parsed, unit, due, templateId,dueReason); if (existing == null) viewModel.createPlan(equipmentId!!, input) { nav.navigate("plan/$it") { popUpTo("plan/new/$equipmentId") { inclusive = true } } } else viewModel.updatePlan(existing.id, input) { nav.popBackStack() } }, enabled = name.isNotBlank() && (count.toIntOrNull() ?: 0) > 0 && runCatching { LocalDate.parse(due) }.isSuccess && (existing==null||due==existing.dueDate||dueReason.isNotBlank()) && !state.operationInProgress, modifier = Modifier.fillMaxWidth()) { Text("Save plan") } }
    }
}

@Composable
internal fun DueServicesScreen(values: List<DueService>, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController, modifier: Modifier = Modifier, initialBucket: DueBucket? = null) {
    var dateFilter by rememberSaveable(initialBucket) {
        mutableStateOf(DueServiceDateFilter.entries.firstOrNull { it.bucket == initialBucket } ?: DueServiceDateFilter.ALL)
    }
    var visitFilter by rememberSaveable { mutableStateOf(DueServiceVisitFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    if (!state.dueServicesReady) {
        DailyEmpty(padding, state.dueServicesError?.let { "Unable to read due services — $it" } ?: "Reading due services", state.dueServicesError?.let { viewModel::retryDueServices })
        return
    }
    val filtered = filterDueServices(values, dateFilter, visitFilter, query)
    val selectedRows = values.filter { it.planId in selected }; val selectionSite = selectedRows.firstOrNull()?.siteId
    LazyColumn(modifier.padding(padding).testTag("due-services-list"), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            DailyField(query, { query = it }, "Search due services")
            ServiceLoopFilterSelectorRow(
                first = {
                    ServiceLoopFilterSelector(
                        label = "Due date",
                        selected = dateFilter,
                        options = DueServiceDateFilter.entries.map { it to it.label },
                        onSelected = { dateFilter = it },
                        testTag = "due-date-selector",
                    )
                },
                second = {
                    ServiceLoopFilterSelector(
                        label = "Visit",
                        selected = visitFilter,
                        options = DueServiceVisitFilter.entries.map { it to it.label },
                        onSelected = { visitFilter = it },
                        testTag = "due-visit-selector",
                    )
                },
            )
            state.dueServicesError?.let { Text("Due services could not update — showing the last saved database result.", color = MaterialTheme.colorScheme.error) }
        }
        if (filtered.isEmpty()) item { Text("No services match these filters.") }
        items(filtered, key = { it.planId }) { due -> val selectable=due.claimedVisitId==null&&(selectionSite==null||selectionSite==due.siteId); ServiceLoopEntityRecord("${due.planReference} · ${due.planName}","${due.equipmentReference} · ${due.equipmentName}\n${due.customerName} · ${due.siteName}",listOf("Due ${due.dueDate}", due.bucket.name.lowercase().replace('_',' '), due.claimedVisitId?.let { "Has visit" }).filterNotNull().joinToString(" · "),selected=due.planId in selected,onClick={nav.navigate(due.claimedVisitId?.let{"visit/$it"}?:"plan/${due.planId}")},actionDescription=if(due.claimedVisitId==null) "Open service plan ${due.planReference} ${due.planName}" else "Open existing visit ${due.planReference} ${due.planName}",selectionChecked=if(selectable) due.planId in selected else null,onSelectionChange=if(selectable) { checked->if(checked)selected=selected+due.planId else selected=selected-due.planId } else null) }
        if (selected.isNotEmpty()) item { Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) { ServiceLoopSecondaryButton("Book selected",{ nav.currentBackStackEntry?.savedStateHandle?.set("visit-setup-plan-ids", ArrayList(selected)); nav.navigate("visit/new") }, enabled = !state.operationInProgress, modifier = Modifier.weight(1f).fillMaxHeight()); ServiceLoopPrimaryButton("Start selected",{ viewModel.createVisit(selected, "WORKING", state.businessDate.toString(), null) { nav.navigate("visit/$it") } }, enabled = !state.operationInProgress, modifier = Modifier.weight(1f).fillMaxHeight()) } }
    }
}

@Composable
internal fun NewVisitScreen(sites: List<VisitSiteOption>, dueServices: List<DueService>, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController, initialPlanIds: List<String> = emptyList()) {
    if (!state.dueServicesReady) {
        DailyEmpty(padding, state.dueServicesError?.let { "Unable to read due services — $it" } ?: "Reading due services", state.dueServicesError?.let { viewModel::retryDueServices })
        return
    }
    var siteId by rememberSaveable { mutableStateOf(dueServices.firstOrNull { it.planId in initialPlanIds }?.siteId) }
    var selectedPlans by rememberSaveable { mutableStateOf(initialPlanIds) }
    var date by rememberSaveable { mutableStateOf(state.businessDate.plusDays(1).toString()) }
    var oneOffEquipmentId by rememberSaveable { mutableStateOf<String?>(null) }
    var oneOffName by rememberSaveable { mutableStateOf("") }
    var siteQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(initialPlanIds,dueServices) { if(siteId==null&&initialPlanIds.isNotEmpty()) siteId=dueServices.firstOrNull{it.planId in initialPlanIds}?.siteId }
    val site = sites.firstOrNull { it.id == siteId }
    val setupRoute = nav.currentBackStackEntry?.destination?.route ?: "visit/new"
    val available = dueServices.filter { it.siteId == siteId && it.claimedVisitId == null }
    val valid = site != null && (selectedPlans.isNotEmpty() || (oneOffEquipmentId != null && oneOffName.isNotBlank())) && runCatching { LocalDate.parse(date) }.isSuccess && !state.operationInProgress
    val initialSite=dueServices.firstOrNull{it.planId in initialPlanIds}?.siteId
    val matchingSites = sites.filter { siteQuery.isBlank() || it.customerName.contains(siteQuery,true) || it.name.contains(siteQuery,true) }
    UnsavedChangesGuard(siteId!=initialSite||selectedPlans!=initialPlanIds||date!=state.businessDate.plusDays(1).toString()||oneOffEquipmentId!=null||oneOffName.isNotBlank()||siteQuery.isNotBlank(),nav)
    fun save(targetState: String, serviceDate: String, scheduledAt: Long?) {
        viewModel.createVisitForSite(site!!.id, selectedPlans, oneOffEquipmentId, oneOffName.takeIf(String::isNotBlank), targetState, serviceDate, scheduledAt) { nav.navigate("visit/$it") { popUpTo(setupRoute) { inclusive = true } } }
    }
    EditorColumn(padding,state) {
        item { DailyHeading("Set up visit"); Text("Choose one site. Planned and one-off work cannot cross sites.") }
        item {
            Text("Customer / site",fontWeight=FontWeight.Bold)
            if (site != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${site.customerName} · ${site.name}", Modifier.weight(1f))
                    if (initialPlanIds.isEmpty()) TextButton({ siteId=null; selectedPlans=emptyList(); oneOffEquipmentId=null }) { Text("Change") }
                }
            } else DailyField(siteQuery,{siteQuery=it},"Find customer or site")
        }
        if (site == null) items(matchingSites, key = { "visit-site-${it.id}" }) { option -> ServiceLoopEntityRecord("${option.reference} · ${option.name}",option.customerName,onClick={siteId=option.id;selectedPlans=emptyList();oneOffEquipmentId=null},actionDescription="Select site ${option.name.ifBlank { option.reference }}") }
        if (site == null && sites.isEmpty()) item { Text("Add a customer site before creating a visit.") }
        else if (site == null && matchingSites.isEmpty()) item { Text("No matching customer sites.") }
        if(site!=null) item { Text("Planned work",fontWeight=FontWeight.Bold); available.forEach { due -> Row(verticalAlignment=Alignment.CenterVertically){Checkbox(due.planId in selectedPlans,{checked->selectedPlans=if(checked) selectedPlans+due.planId else selectedPlans-due.planId});Text("${due.equipmentName} · ${due.planName} · Due ${due.dueDate}") } }; if(available.isEmpty()) Text("No unclaimed current plans at this site.") }
        if(site!=null) item { Text("Optional one-off work",fontWeight=FontWeight.Bold); site.equipment.forEach { equipment -> FilterChip(oneOffEquipmentId==equipment.id,{oneOffEquipmentId=equipment.id},{Text(equipment.name)}) }; DailyField(oneOffName,{oneOffName=it},"One-off service name") }
        item { DailyField(date,{date=it},"Appointment / service date · YYYY-MM-DD"); Text("Date-only booking in the ${state.businessZoneId} business zone. A time is recorded only when explicitly scheduled."); val parsed=runCatching{LocalDate.parse(date)}.getOrNull(); val primary=when { parsed==null || parsed.isAfter(state.businessDate) -> "BOOKED"; parsed==state.businessDate -> "WORKING"; else -> "HISTORICAL" }; @Composable fun action(kind:String,label:String){ val click={ when(kind){"BOOKED"->save("BOOKED",date,null);"WORKING"->save("WORKING",state.businessDate.toString(),null);else->save("HISTORICAL",date,null) } }; val enabled=valid && (kind!="HISTORICAL" || parsed!=null&&!parsed.isAfter(state.businessDate)); if(primary==kind) ServiceLoopPrimaryButton(label,click,enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("primary-visit-action-$kind")) else ServiceLoopSecondaryButton(label,click,enabled=enabled,modifier=Modifier.fillMaxWidth()) }; ServiceLoopActionStack { action("BOOKED","Book visit"); action("WORKING","Start now"); action("HISTORICAL","Record past visit") }; Text("Record past creates History-only recurring work; it never claims or advances today's obligation.") }
    }
}

@Composable
internal fun TemplateListScreen(values: List<TemplateSummary>, padding: PaddingValues, nav: NavHostController) {
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Button({ nav.navigate("template/new") }, Modifier.fillMaxWidth()) { Text("Create inspection template") } }; if (values.isEmpty()) item { Text("No reusable templates") }; items(values) { template -> ServiceLoopEntityRecord("${template.reference} · ${template.name}",metadata="Revision ${template.revisionNumber} · ${template.itemCount} items",status=template.state){nav.navigate("template/${template.id}")} } }
}

@Composable
internal fun TemplateDetailScreen(detail: TemplateDetail?, padding: PaddingValues, nav: NavHostController) {
    if (detail == null) return DailyEmpty(padding, "Reading template")
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Text("${detail.reference} · ${detail.name}", style = MaterialTheme.typography.headlineSmall); Text("Revision ${detail.revisionNumber} · ${detail.state}"); Text("Changing this template publishes a new revision. Existing work keeps its captured snapshot."); Button({ nav.navigate("template/edit/${detail.id}") }, Modifier.fillMaxWidth()) { Text("New revision") } }; items(detail.items.withIndex().toList()) { (index,item) -> Text("${index+1}. ${item.label} · ${item.responseType} · ${if (item.required) "Required" else "Optional"}${item.unit.ifBlank { "" }.let { if (it.isBlank()) "" else " · $it" }}") } }
}

@Composable
internal fun TemplateEditorScreen(existing: TemplateDetail?, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }; var draftItems by remember(existing?.id) { mutableStateOf(existing?.items ?: emptyList()) }; var label by rememberSaveable { mutableStateOf("") }; var type by rememberSaveable { mutableStateOf("STATUS") }; var unit by rememberSaveable { mutableStateOf("") }; var required by rememberSaveable { mutableStateOf(true) }; var guidance by rememberSaveable { mutableStateOf("") }
    UnsavedChangesGuard(name!=existing?.name.orEmpty()||draftItems!=(existing?.items?:emptyList<TemplateItemDraft>())||label.isNotBlank()||unit.isNotBlank()||guidance.isNotBlank(),nav)
    EditorColumn(padding, state) {
        item { DailyHeading(if (existing == null) "Create reusable template" else "Publish revision ${existing.revisionNumber + 1}"); DailyField(name, { name = it }, "Template name · Required") }
        items(draftItems.withIndex().toList()) { (index,item) -> Card { Column(Modifier.padding(12.dp).fillMaxWidth()) { Text("${index+1}. ${item.label} · ${item.responseType}"); Row { TextButton({ val copy=draftItems.toMutableList(); copy[index]=copy[index-1].also{copy[index-1]=copy[index]}; draftItems=copy },enabled=index>0){Text("Move up")}; TextButton({ val copy=draftItems.toMutableList(); copy[index]=copy[index+1].also{copy[index+1]=copy[index]}; draftItems=copy },enabled=index<draftItems.lastIndex){Text("Move down")}; TextButton({ draftItems = draftItems.filterIndexed { i,_ -> i != index } }) { Text("Remove") } } } } }
        item { Text("Add item", fontWeight = FontWeight.Bold); DailyField(label, { label = it }, "Item label"); ServiceLoopChoiceGroup(listOf("STATUS","TEXT","NUMBER").map{it to it},type,{type=it}); if (type == "NUMBER") DailyField(unit, { unit = it }, "Unit"); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(required, { required = it }); Text("Required response") }; LongTextEditor(guidance, { guidance = it }, "Private technician guidance", true); OutlinedButton({ draftItems = draftItems + TemplateItemDraft(label, type, unit, required, guidance); label=""; unit=""; guidance="" }, enabled = label.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Add item") } }
        item { Button({ if (existing == null) viewModel.createTemplate(name, draftItems) { nav.navigate("template/$it") { popUpTo("template/new") { inclusive = true } } } else viewModel.reviseTemplate(existing.id, name, draftItems) { nav.popBackStack() } }, enabled = name.isNotBlank() && draftItems.isNotEmpty() && !state.operationInProgress, modifier = Modifier.fillMaxWidth()) { Text(if (existing == null) "Save template" else "Publish new revision") } }
    }
}

@Composable
internal fun VisitDetailScreen(detail: VisitDetail?, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    if (detail == null) return DailyEmpty(padding, "Reading visit")
    val context = LocalContext.current
    var newDate by rememberSaveable(detail.id) { mutableStateOf(detail.serviceDate) }; var reason by rememberSaveable(detail.id) { mutableStateOf("") }; var cancelReason by rememberSaveable(detail.id) { mutableStateOf("") }; var oneOffName by rememberSaveable(detail.id){mutableStateOf("")}; var oneOffEquipment by rememberSaveable(detail.id){mutableStateOf<String?>(null)}
    LazyColumn(Modifier.padding(padding).testTag("visit-detail-list"), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)) {
        item { Text("${detail.reference} · ${detail.state.lowercase().replace('_',' ').replaceFirstChar(Char::uppercase)}", style = MaterialTheme.typography.headlineSmall); Text("${detail.customerName}\n${detail.siteName}\n${detail.siteAddress}"); Text("${if (detail.state == "BOOKED") "Appointment" else "Service date"} ${detail.serviceDate}") }
        item { DispatchVisitPanel(detail) }
        item { val calendar=state.visitCalendarState;Card(Modifier.fillMaxWidth().testTag("visit-calendar")){Column(Modifier.padding(12.dp)){Text("Calendar",fontWeight=FontWeight.Bold);Text(calendar?.label?:"Checking Calendar status");when(calendar?.action){"Add to Calendar","Recreate event"->OutlinedButton({viewModel.addVisitToCalendar(detail.id)},Modifier.fillMaxWidth().testTag("visit-calendar-add")){Text(calendar.action)};"Remove from Calendar"->OutlinedButton({viewModel.removeVisitFromCalendar(detail.id)},Modifier.fillMaxWidth().testTag("visit-calendar-remove")){Text(calendar.action)}};calendar?.eventId?.let{id->TextButton({viewModel.calendarEventIntent(id)?.let(context::startActivity)}){Text("Open Calendar event")}}}} }
        items(detail.lines) { line -> DailyRow("${line.equipmentReference} · ${line.equipmentName}\n${line.serviceName} · Due ${line.dueDate ?: "one-off"}${line.outcome?.let { " · ${it.lowercase()}" }.orEmpty()}", "visit-line-${line.workItemId}") { if (detail.state == "WORKING") nav.navigate("inspection/${line.workItemId}") } }
        if(detail.state in setOf("BOOKED","WORKING")&&state.site!=null) item { Text("Add one-off service line",fontWeight=FontWeight.Bold); ServiceLoopChoiceGroup(state.site.equipment.map{it.id to it.name},oneOffEquipment,{oneOffEquipment=it}); DailyField(oneOffName,{oneOffName=it},"One-off service"); Spacer(Modifier.height(ServiceLoopUiTokens.Space.lg)); ServiceLoopPrimaryButton("Add one-off line",{viewModel.addOneOff(detail.id,oneOffEquipment!!,oneOffName);oneOffName=""},Modifier.fillMaxWidth(),enabled=oneOffEquipment!=null&&oneOffName.isNotBlank()) }
        if (detail.state == "BOOKED") item { var rescheduleSaved by rememberSaveable(detail.id) { mutableStateOf(false) }; Text("Start reloads current customer, site, equipment, plan, business, and reusable-template details. Changed details will replace booking-time display details in the Working visit."); Button({ viewModel.startVisit(detail.id) { id -> viewModel.loadVisit(id) } }, enabled = detail.lines.isNotEmpty() && !state.operationInProgress, modifier = Modifier.fillMaxWidth()) { Text("Start with current details") }; if(detail.lines.isEmpty()) Text("Add at least one service line before starting."); Text("Appointment reminder",fontWeight=FontWeight.Bold); ServiceLoopChoiceGroup(listOf(null to "Default",0 to "Off",120 to "2 hours",1440 to "1 day"),detail.appointmentReminderLeadMinutes,{viewModel.setAppointmentReminderLead(detail.id,it)},testTagPrefix="visit-reminder"); Column { DailyField(newDate, { newDate = it; rescheduleSaved=false }, "New appointment date"); if(rescheduleSaved) Row(Modifier.align(Alignment.End).testTag("reschedule-saved"),verticalAlignment=Alignment.CenterVertically){ServiceLoopIcon(ServiceLoopIcons.LocalSaved,null,Modifier.size(ServiceLoopUiTokens.Size.iconSmall),MaterialTheme.colorScheme.primary);Spacer(Modifier.width(ServiceLoopUiTokens.Space.xs));Text("Saved",color=MaterialTheme.colorScheme.primary)} }; LongTextEditor(reason, { reason = it; rescheduleSaved=false }, "Reschedule reason", false); OutlinedButton({ viewModel.rescheduleVisit(detail.id, newDate, null, reason) { rescheduleSaved=true } }, enabled = reason.isNotBlank() && runCatching { LocalDate.parse(newDate) }.isSuccess, modifier = Modifier.fillMaxWidth()) { Text("Reschedule booking") }; LongTextEditor(cancelReason, { cancelReason = it }, "Cancellation reason", false); OutlinedButton({ viewModel.cancelVisit(detail.id, cancelReason) { viewModel.loadVisit(it) } }, enabled = cancelReason.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Cancel booking") }; Text("Rescheduling or cancelling does not fulfill or alter the due obligation.") }
        if (detail.state == "CANCELED" && detail.cancellationOrigin !in setOf("COORDINATOR","ASSIGNMENT_REMOVAL")) item { var restoreDate by rememberSaveable(detail.id) { mutableStateOf(if (runCatching { LocalDate.parse(detail.serviceDate) }.getOrNull()?.isBefore(state.businessDate) == true) state.businessDate.toString() else detail.serviceDate) }; Text("This cancellation left the obligation due."); DailyField(restoreDate, { restoreDate=it }, "Restore appointment date · YYYY-MM-DD"); Spacer(Modifier.height(ServiceLoopUiTokens.Space.lg)); Button({ viewModel.restoreVisit(detail.id, restoreDate) { viewModel.loadVisit(it) } }, enabled=!state.operationInProgress && runCatching { !LocalDate.parse(restoreDate).isBefore(state.businessDate) }.getOrDefault(false), modifier=Modifier.fillMaxWidth().testTag("restore-booking")){Text("Restore booking")} }
        if (detail.state == "WORKING") item { Button({ detail.lines.firstOrNull()?.let { nav.navigate("inspection/${it.workItemId}") } }, Modifier.fillMaxWidth()) { Text("Continue working visit") } }
        if (detail.state == "CANCELED") item { Text("Cancellation reason: ${detail.cancellationReason}"); if(detail.cancellationOrigin!=null) Text("Cancellation source: ${detail.cancellationOrigin.lowercase().replace('_',' ')}"); Text("The service obligation remains due and may be booked again.") }
    }
}

@Composable
internal fun FieldEvidenceScreen(workItemId: String, state: UiState, padding: PaddingValues, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    var description by rememberSaveable { mutableStateOf("") }; var quantity by rememberSaveable { mutableStateOf("1") }; var unit by rememberSaveable { mutableStateOf("item") }; var include by rememberSaveable { mutableStateOf(false) }; var caption by rememberSaveable { mutableStateOf("") }; var followTitle by rememberSaveable { mutableStateOf("") }; var followDue by rememberSaveable { mutableStateOf(state.businessDate.plusDays(7).toString()) }; var followNote by rememberSaveable { mutableStateOf("") }; val context = LocalContext.current
    val scope=rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) scope.launch { val bytes=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{it.readBounded(MAX_PHOTO_PICK_BYTES)}}; if(bytes!=null)viewModel.savePhoto(workItemId,bytes,uri.lastPathSegment,context.contentResolver.getType(uri)?:"image/jpeg",include,caption) else viewModel.reportOperationFailure("Photo not added — choose a readable image smaller than 30 MB") } }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> val path=cameraPath; if(success&&path!=null)scope.launch{val file=File(path);val bytes=withContext(Dispatchers.IO){if(file.isFile&&file.length() in 1..MAX_PHOTO_PICK_BYTES.toLong())file.readBytes()else null};if(bytes!=null)viewModel.savePhoto(workItemId,bytes,file.name,"image/jpeg",include,caption)else viewModel.reportOperationFailure("Photo not added — camera output was missing or too large");withContext(Dispatchers.IO){file.delete()}};cameraPath=null }
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)) {
        item { DailyHeading("Parts used"); DailyField(description, { description=it }, "Description"); DailyField(quantity, { quantity=it }, "Positive quantity"); DailyField(unit, { unit=it }, "Unit"); Button({ viewModel.addPart(workItemId, description, quantity, unit); description="" }, enabled = description.isNotBlank() && !state.operationInProgress, modifier = Modifier.fillMaxWidth()) { Text("Save part") } }
        items(state.parts) { part -> Text("${part.description} · ${part.quantity} ${part.unit}") }
        item { DailyHeading("Photographs"); Text("Maximum 20 per machine and 100 per visit; optimized to a 2,560 px longest edge."); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(include, { include=it }); Text("Include selected photo in customer report") }; DailyField(caption, { caption = it }, "Customer-visible caption"); ServiceLoopActionStack { Button({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth()) { ServiceLoopIcon(ServiceLoopIcons.Photo,null,Modifier.size(ServiceLoopUiTokens.Size.icon));Spacer(Modifier.width(ServiceLoopUiTokens.Space.sm));Text("Choose photo") }; OutlinedButton({ val directory=File(context.cacheDir,"camera-staging").apply{mkdirs()}; val file=File(directory,"capture-${System.currentTimeMillis()}.jpg"); cameraPath=file.absolutePath; camera.launch(FileProvider.getUriForFile(context,"${context.packageName}.reports",file)) },Modifier.fillMaxWidth()){ServiceLoopIcon(ServiceLoopIcons.Camera,null,Modifier.size(ServiceLoopUiTokens.Size.icon));Spacer(Modifier.width(ServiceLoopUiTokens.Space.sm));Text("Take photo") } }; Text("The selected image is copied into ServiceLoop storage before it is marked Saved.") }
        items(state.photos, key = { it.id }) { photo -> PhotoEvidenceCard(photo, context) }
        item { DailyHeading("Corrective follow-up"); DailyField(followTitle,{followTitle=it},"Follow-up title"); DailyField(followDue,{followDue=it},"Due date"); LongTextEditor(followNote,{followNote=it},"PRIVATE planning note",true); OutlinedButton({viewModel.createCorrectiveFollowUp(workItemId,followTitle,followDue,followNote);followTitle=""},enabled=followTitle.isNotBlank()&&runCatching{LocalDate.parse(followDue)}.isSuccess,modifier=Modifier.fillMaxWidth()){Text("Create corrective follow-up")} }
        state.inspection?.takeIf { it.workItemId == workItemId }?.let { draft -> item { Button({nav.navigate("review/${draft.visitId}")},Modifier.fillMaxWidth().testTag("field-review-completion")){Text("Review completion")} } }
    }
}

@Composable
private fun PhotoEvidenceCard(photo: PhotoEntry, context: Context) {
    val colors = com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens.current
    val bitmap = remember(photo.relativePath, photo.byteSize) {
        BitmapFactory.decodeFile(File(context.filesDir, photo.relativePath).absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 })
    }
    ServiceLoopSurfaceCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(88.dp).background(colors.photoMat, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = photo.caption?.takeIf(String::isNotBlank) ?: "Service evidence photograph", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Text("Image unavailable", color = colors.errorInk, style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (photo.includedInReport) "Included in customer report" else "Private evidence", fontWeight = FontWeight.SemiBold)
                Text(photo.caption?.takeIf(String::isNotBlank) ?: "No caption", style = MaterialTheme.typography.bodyMedium)
                Text("${photo.byteSize} bytes · Saved on this device", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun FollowUpListScreen(values: List<FollowUpDetail>, padding: PaddingValues, nav: NavHostController) { LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { if (values.isEmpty()) item { Text("No follow-ups") }; items(values) { follow -> ServiceLoopEntityRecord("${follow.reference} · ${follow.title}",listOfNotNull(follow.customerName,follow.siteName,follow.equipmentName).filter{it.isNotBlank()}.joinToString(" · "),"${follow.type.lowercase()} · Due ${follow.dueDate}",follow.state){nav.navigate("follow-up/${follow.id}")} } } }

@Composable
internal fun FollowUpDetailScreen(detail: FollowUpDetail?, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    if (detail == null) return DailyEmpty(padding, "Reading follow-up"); var reason by rememberSaveable(detail.id) { mutableStateOf("") }; var newDue by rememberSaveable(detail.id) { mutableStateOf(state.businessDate.plusDays(7).toString()) }; var editTitle by rememberSaveable(detail.id){mutableStateOf(detail.title)}; var editDue by rememberSaveable(detail.id){mutableStateOf(detail.dueDate)}; var editNote by rememberSaveable(detail.id){mutableStateOf(detail.privatePlanningNote)}; var editReason by rememberSaveable(detail.id){mutableStateOf("")}
    UnsavedChangesGuard(editTitle!=detail.title||editDue!=detail.dueDate||editNote!=detail.privatePlanningNote||editReason.isNotBlank()||reason.isNotBlank()||(detail.state!="OPEN"&&newDue!=state.businessDate.plusDays(7).toString()),nav)
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { item { Text("${detail.reference} · ${detail.title}", style = MaterialTheme.typography.headlineSmall); Text("${detail.type} · ${detail.state} · Due ${detail.dueDate}"); Text(listOfNotNull(detail.customerName,detail.siteName,detail.equipmentName).filter{it.isNotBlank()}.joinToString(" · ")); if(detail.state=="OPEN"){ DailyField(editTitle,{editTitle=it},"Title"); DailyField(editDue,{editDue=it},"Follow-up date"); LongTextEditor(editNote,{editNote=it},"PRIVATE planning note",true); if(editDue!=detail.dueDate) LongTextEditor(editReason,{editReason=it},"Date-change reason · Required",false); ServiceLoopPrimaryButton("Save follow-up changes",{viewModel.updateFollowUp(detail.id,editTitle,editDue,editNote,editReason){viewModel.loadFollowUp(it)}},Modifier.fillMaxWidth(),enabled=editTitle.isNotBlank()&&(editDue==detail.dueDate||editReason.isNotBlank())) } else if (detail.privatePlanningNote.isNotBlank()) PrivateBlock("PRIVATE planning note", detail.privatePlanningNote); LongTextEditor(reason, { reason=it }, if (detail.state == "OPEN") "Outcome or cancellation reason" else "Reopen reason", true); if (detail.state != "OPEN") DailyField(newDue, { newDue=it }, "New follow-up date"); if (detail.state == "OPEN") Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement = Arrangement.spacedBy(8.dp)) { ServiceLoopPrimaryButton("Resolve",{viewModel.changeFollowUpState(detail.id,"RESOLVED",reason,null){viewModel.loadFollowUp(it)}},Modifier.weight(1f).fillMaxHeight(),enabled=reason.isNotBlank()&&!state.operationInProgress); ServiceLoopSecondaryButton("Cancel",{viewModel.changeFollowUpState(detail.id,"CANCELLED",reason,null){viewModel.loadFollowUp(it)}},Modifier.weight(1f).fillMaxHeight(),enabled=reason.isNotBlank()&&!state.operationInProgress) } else Button({ viewModel.changeFollowUpState(detail.id,"OPEN",reason,newDue) { viewModel.loadFollowUp(it) } }, enabled=reason.isNotBlank(), modifier=Modifier.fillMaxWidth()) { Text("Reopen") }; Text("Follow-up state never changes a service plan or historical report.") } }
}

@Composable
internal fun FollowUpEditorScreen(customerId: String, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    var type by rememberSaveable { mutableStateOf("CONTACT") }; var title by rememberSaveable { mutableStateOf("") }; var due by rememberSaveable { mutableStateOf(state.businessDate.toString()) }; var note by rememberSaveable { mutableStateOf("") }
    UnsavedChangesGuard(type!="CONTACT"||title.isNotBlank()||due!=state.businessDate.toString()||note.isNotBlank(),nav)
    EditorColumn(padding,state) { item { DailyHeading("Add follow-up"); ServiceLoopChoiceGroup(listOf("CONTACT","CORRECTIVE").map{it to it.lowercase().replaceFirstChar(Char::uppercase)},type,{type=it}); DailyField(title,{title=it},"Title · Required"); DailyField(due,{due=it},"Follow-up date"); LongTextEditor(note,{note=it},"PRIVATE planning note",true); Button({ viewModel.createFollowUp(FollowUpInput(type,title,due,customerId,privatePlanningNote=note)) { nav.navigate("follow-up/$it") { popUpTo("follow-up/new/$customerId") { inclusive=true } } } },enabled=title.isNotBlank()&&runCatching{LocalDate.parse(due)}.isSuccess&&!state.operationInProgress,modifier=Modifier.fillMaxWidth()){Text("Save follow-up")} } }
}

@Composable
internal fun ContactNoteEditorScreen(customerId: String, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    var channel by rememberSaveable { mutableStateOf("CALL") }; var outcome by rememberSaveable { mutableStateOf("") }; var note by rememberSaveable { mutableStateOf("") }
    UnsavedChangesGuard(channel!="CALL"||outcome.isNotBlank()||note.isNotBlank(),nav)
    EditorColumn(padding,state) { item { DailyHeading("Record actual contact outcome"); Text("Opening an external app does not create this note."); ServiceLoopChoiceGroup(listOf("CALL","SMS","EMAIL","IN_PERSON","OTHER").map{it to it.lowercase().replace('_',' ')},channel,{channel=it}); LongTextEditor(outcome,{outcome=it},"Actual context / outcome · Required",false); LongTextEditor(note,{note=it},"PRIVATE note",true); Button({ viewModel.createContactNote(ContactNoteInput(customerId,channel=channel,outcome=outcome,privateNote=note)) { nav.popBackStack() } },enabled=outcome.isNotBlank()&&!state.operationInProgress,modifier=Modifier.fillMaxWidth()){Text("Save contact note")} } }
}

@Composable
internal fun SearchScreen(results: List<SearchTarget>, padding: PaddingValues, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    var query by rememberSaveable { mutableStateOf("") }; LaunchedEffect(query) { viewModel.search(query) }
    LazyColumn(Modifier.padding(padding), contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){ item { DailyField(query,{query=it},"Search names and references"); if(query.isBlank()) Text("Search customers, sites, equipment, plans, visits, final records, and follow-ups.") }; if(query.isNotBlank()&&results.isEmpty()) item { Text("No matching saved records.") }; items(results){ result -> ServiceLoopEntityRecord("${result.reference} · ${result.title}",result.subtitle,metadata=result.type.replace('_',' ')){ val route=when(result.type){"CUSTOMER"->"customer/${result.id}";"SITE"->"site/${result.id}";"EQUIPMENT"->"equipment/${result.id}";"PLAN"->"plan/${result.id}";"VISIT"->"visit/${result.id}";"FINAL_RECORD"->"record/${result.id}";"FOLLOW_UP"->"follow-up/${result.id}";else->null}; route?.let(nav::navigate) } } }
}

@Composable
internal fun EquipmentSiteSelectorScreen(sites: List<VisitSiteOption>, padding: PaddingValues, nav: NavHostController) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered=sites.filter{query.isBlank()||it.customerName.contains(query,true)||it.name.contains(query,true)||it.reference.contains(query,true)}
    LazyColumn(Modifier.padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Text("Select the customer site where the equipment is installed."); DailyField(query,{query=it},"Find customer or site") }
        if(filtered.isEmpty()) item { Text("No active customer sites match. Add a customer and site first.") }
        items(filtered,key={it.id}) { site -> ServiceLoopEntityRecord("${site.reference} · ${site.name}",site.customerName,onClick={nav.navigate("equipment/new/${site.id}")}) }
    }
}

@Composable
internal fun LongTextEditor(value: String, onValueChange: (String) -> Unit, label: String, private: Boolean) {
    ServiceLoopLongTextEditor(value, onValueChange, label, private)
}

@Composable
internal fun UnsavedChangesGuard(changed:Boolean,nav:NavHostController) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val requestBack:()->Unit={if(changed) confirm=true else { nav.popBackStack(); Unit }}
    val interceptor=LocalDetailBackInterceptor.current
    DisposableEffect(requestBack) { interceptor.value=requestBack; onDispose { if(interceptor.value===requestBack) interceptor.value=null } }
    BackHandler(onBack=requestBack)
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Discard unsaved changes?")},text={Text("This form uses local unsaved input until Save succeeds.")},confirmButton={TextButton({confirm=false;nav.popBackStack()}){Text("Discard changes")}},dismissButton={TextButton({confirm=false}){Text("Keep editing")}})
}

@Composable private fun EditorColumn(padding: PaddingValues,state: UiState,tag:String?=null,content: androidx.compose.foundation.lazy.LazyListScope.()->Unit){ LazyColumn(Modifier.padding(padding).fillMaxWidth().widthIn(max=com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens.Size.formMaxWidth).then(if(tag==null) Modifier else Modifier.testTag(tag)),contentPadding=PaddingValues(16.dp,8.dp,16.dp,32.dp),verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)){ if(state.error!=null)item{Text("Not saved — ${state.error}",color=MaterialTheme.colorScheme.error)}; if(state.operationMessage!=null)item{Text(state.operationMessage,color=MaterialTheme.colorScheme.primary)}; content() } }
@Composable private fun DailyField(value:String,onChange:(String)->Unit,label:String){
    val tag = "field-" + label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    ServiceLoopTextField(value,onChange,label,modifier=Modifier.testTag(tag).padding(bottom=ServiceLoopUiTokens.Space.lg))
}
@Composable private fun DailyHeading(value:String){Text(value,style=MaterialTheme.typography.titleLarge)}
@Composable private fun DailyRow(value:String,tag:String?=null,onClick:()->Unit){
    Surface(onClick=onClick,color=LocalServiceLoopTokens.current.surface,modifier=Modifier.fillMaxWidth().then(if(tag==null) Modifier else Modifier.testTag(tag))){
        Column {
            Box(Modifier.fillMaxWidth().heightIn(min=ServiceLoopUiTokens.Size.listRowMin).padding(horizontal=ServiceLoopUiTokens.Space.sm,vertical=ServiceLoopUiTokens.Space.md),contentAlignment=Alignment.CenterStart){Text(value)}
            HorizontalDivider(color=LocalServiceLoopTokens.current.outlineDecorative)
        }
    }
}
@Composable private fun DailyEmpty(padding:PaddingValues,value:String,onRetry:(() -> Unit)?=null){Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.md)){Text(value);onRetry?.let{ServiceLoopPrimaryButton("Retry",it,Modifier.testTag("retry-due-services"))}}}}
@Composable private fun PrivateBlock(label:String,value:String){Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){Column(Modifier.padding(12.dp)){Text(label,fontWeight=FontWeight.Bold);Text(value)}}}
private fun handoff(context:Context,intent:Intent,label:String)=if(runCatching{context.startActivity(intent);true}.getOrDefault(false)) "Opened $label · no contact outcome was recorded" else "No compatible $label app is available · copy the saved details manually"
private fun InputStream.readBounded(limit:Int):ByteArray? { val output=ByteArrayOutputStream(); val buffer=ByteArray(8192); var total=0; while(true){val count=read(buffer);if(count<0)break;total+=count;if(total>limit)return null;output.write(buffer,0,count)};return output.toByteArray() }
private const val MAX_PHOTO_PICK_BYTES=30*1024*1024
