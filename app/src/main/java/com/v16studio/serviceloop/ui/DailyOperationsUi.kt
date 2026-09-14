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
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopResponsivePair
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
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
import com.v16studio.serviceloop.data.InspectionTemplateCodec
import com.v16studio.serviceloop.data.InspectionTemplateExchangeService
import com.v16studio.serviceloop.data.InspectionTemplateImportClassification
import com.v16studio.serviceloop.data.InspectionTemplateImportPreview
import com.v16studio.serviceloop.data.InspectionTemplateTransfer
import com.v16studio.serviceloop.data.INSPECTION_TEMPLATES_MIME
import com.v16studio.serviceloop.ServiceLoopApplication
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopLongTextEditor
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSurfaceCard
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextField
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopWorkItemRow
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons
import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import org.json.JSONObject
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
                if (detail.customerType == CustomerType.ONE_TIME) {
                    Text("One-time customer", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("one-time-customer-label"))
                    ServiceLoopPrimaryButton("Make Standard", { viewModel.makeCustomerStandard(detail.id) { viewModel.loadCustomer(detail.id) } }, Modifier.fillMaxWidth().testTag("make-standard-customer"))
                }
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
    EditorColumn(padding, state, tag = "customer-editor") {
        item { DailyHeading(if (existing == null) "Add customer" else "Edit ${existing.reference}"); Text("A stable reference is assigned on Save.") }
        item { DailyField(name, { name = it }, "Customer name · Required"); DailyField(contact, { contact = it }, "Main contact"); DailyField(phone, { phone = it }, "Phone"); DailyField(email, { email = it }, "Email") }
        if (existing == null) item { DailyHeading("First site"); Text("Every new customer starts with a default site. Blank contact fields inherit the customer contact."); DailyField(firstSiteName, { firstSiteName = it }, "Site name · Required"); DailyField(firstSiteAddress, { firstSiteAddress = it }, "Site address") }
        item { LongTextEditor(note, { note = it }, "Private customer note", true) }
        item { Button({ val input = CustomerInput(name, contact, phone, email, note); if (existing == null) viewModel.createCustomerWithFirstSite(input, SiteInput(firstSiteName, firstSiteAddress, isDefault = true)) { (customerId, _) -> nav.navigate("customer/$customerId") { popUpTo("customer/new") { inclusive = true } } } else viewModel.updateCustomer(existing.id, input) { nav.popBackStack() } }, enabled = name.isNotBlank() && (existing != null || firstSiteName.isNotBlank()) && !state.operationInProgress, modifier = Modifier.fillMaxWidth().testTag("save-customer")) { Text("Save customer${if (existing == null) " and first site" else ""}") } }
    }
}

@Composable
internal fun SiteDetailScreen(detail: SiteDetail?, padding: PaddingValues, nav: NavHostController, viewModel: ServiceLoopViewModel) {
    if (detail == null) return DailyEmpty(padding, "Reading site")
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
            Spacer(Modifier.height(ServiceLoopUiTokens.Space.lg)); Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement = Arrangement.spacedBy(8.dp)) { ServiceLoopSecondaryButton("Customer",{nav.navigate("customer/${detail.customerId}")},Modifier.weight(1f).fillMaxHeight().testTag("site-customer-link")); ServiceLoopSecondaryButton("Edit",{nav.navigate("site/edit/${detail.id}")},Modifier.weight(1f).fillMaxHeight()); ServiceLoopSecondaryButton("Maps",{handoffStatus=handoff(context,Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(detail.address)}")),"maps")},Modifier.weight(1f).fillMaxHeight(),enabled=detail.address.isNotBlank()) }
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
    EditorColumn(padding, state, tag = "site-editor") {
        item { DailyHeading(if (existing == null) "Add site" else "Edit ${existing.reference}") }
        item { DailyField(name, { name = it }, "Site name · Required"); DailyField(address, { address = it }, "Address"); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(useCustomerContact, { useCustomerContact = it }); Text("Use customer contact") }; if (!useCustomerContact) { DailyField(contact, { contact = it }, "Contact override"); DailyField(phone, { phone = it }, "Phone override"); DailyField(email, { email = it }, "Email override") } else Text("Customer contact is inherited; any staged overrides remain available if inheritance is turned off before Save.", style = MaterialTheme.typography.bodySmall) }
        item { LongTextEditor(note, { note = it }, "PRIVATE access note", true); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(default, { default = it }); Text("Default site for this customer") } }
        item { Button({ val input = SiteInput(name, address, contact.takeUnless { useCustomerContact }.orEmpty(), phone.takeUnless { useCustomerContact }.orEmpty(), email.takeUnless { useCustomerContact }.orEmpty(), note, default); if (existing == null) viewModel.createSite(customerId!!, input) { nav.navigate("site/$it") { popUpTo("site/new/$customerId") { inclusive = true } } } else viewModel.updateSite(existing.id, input) { nav.popBackStack() } }, enabled = name.isNotBlank() && !state.operationInProgress, modifier = Modifier.fillMaxWidth().testTag("save-site")) { Text("Save site") } }
    }
}

@Composable
internal fun EquipmentEditorScreen(siteId: String?, existing: EquipmentDetail?, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }; var identifier by rememberSaveable(existing?.id) { mutableStateOf(existing?.technicianIdentifier.orEmpty()) }; var make by rememberSaveable(existing?.id) { mutableStateOf(existing?.make.orEmpty()) }; var model by rememberSaveable(existing?.id) { mutableStateOf(existing?.model.orEmpty()) }; var serial by rememberSaveable(existing?.id) { mutableStateOf(existing?.serialNumber.orEmpty()) }; var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.privateNote.orEmpty()) }
    UnsavedChangesGuard(name!=existing?.name.orEmpty()||identifier!=existing?.technicianIdentifier.orEmpty()||make!=existing?.make.orEmpty()||model!=existing?.model.orEmpty()||serial!=existing?.serialNumber.orEmpty()||note!=existing?.privateNote.orEmpty(),nav)
    EditorColumn(padding, state, tag = "equipment-editor") {
        item { DailyHeading(if (existing == null) "Add equipment" else "Edit ${existing.reference}") }
        item { DailyField(name, { name = it }, "Equipment name · Required"); DailyField(identifier, { identifier = it }, "Technician identifier"); DailyField(make, { make = it }, "Make"); DailyField(model, { model = it }, "Model"); DailyField(serial, { serial = it }, "Serial") }
        item { LongTextEditor(note, { note = it }, "Private equipment note", true) }
        item { Button({ val input = EquipmentInput(name, identifier, make, model, serial, note); if (existing == null) viewModel.createEquipment(siteId!!, input) { nav.navigate("equipment/$it") { popUpTo("equipment/new/$siteId") { inclusive = true } } } else viewModel.updateEquipment(existing.id, input) { nav.popBackStack() } }, enabled = name.isNotBlank() && !state.operationInProgress, modifier = Modifier.fillMaxWidth().testTag("save-equipment")) { Text("Save equipment") } }
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
        item { Button({ val parsed = count.toIntOrNull() ?: 0; val input = PlanInput(name, parsed, unit, due, templateId,dueReason); if (existing == null) viewModel.createPlan(equipmentId!!, input) { nav.navigate("plan/$it") { popUpTo("plan/new/$equipmentId") { inclusive = true } } } else viewModel.updatePlan(existing.id, input) { nav.popBackStack() } }, enabled = name.isNotBlank() && (count.toIntOrNull() ?: 0) > 0 && runCatching { LocalDate.parse(due) }.isSuccess && (existing==null||due==existing.dueDate||dueReason.isNotBlank()) && !state.operationInProgress, modifier = Modifier.fillMaxWidth().testTag("save-plan")) { Text("Save plan") } }
    }
}

@Composable
internal fun DueServicesScreen(values: List<DueService>, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController, modifier: Modifier = Modifier, initialBucket: DueBucket? = null, topContent: (@Composable () -> Unit)? = null) {
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
        topContent?.let { action -> item { action() } }
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

private data class NewVisitTaskDraft(
    val taskName: String,
    val subjectType: WorkSubjectType,
    val equipmentId: String?,
    val equipmentDescription: String,
    val templateId: String?,
) {
    fun toInput() = AdHocWorkInput(taskName, subjectType, equipmentId, equipmentDescription, templateId)
}

private val NewVisitTaskDraftListSaver = listSaver<List<NewVisitTaskDraft>, String>(
    save = { tasks ->
        tasks.map { task ->
            JSONObject().apply {
                put("taskName", task.taskName)
                put("subjectType", task.subjectType.name)
                put("equipmentId", task.equipmentId)
                put("equipmentDescription", task.equipmentDescription)
                put("templateId", task.templateId)
            }.toString()
        }
    },
    restore = { values ->
        values.mapNotNull { encoded ->
            runCatching {
                val value = JSONObject(encoded)
                NewVisitTaskDraft(
                    taskName = value.getString("taskName"),
                    subjectType = WorkSubjectType.valueOf(value.getString("subjectType")),
                    equipmentId = value.optString("equipmentId").takeIf { it.isNotBlank() },
                    equipmentDescription = value.optString("equipmentDescription"),
                    templateId = value.optString("templateId").takeIf { it.isNotBlank() },
                )
            }.getOrNull()
        }
    },
)

@Composable
private fun NewVisitTaskEditor(
    taskName: String,
    onTaskName: (String) -> Unit,
    subjectType: WorkSubjectType,
    onSubjectType: (WorkSubjectType) -> Unit,
    equipmentId: String?,
    onEquipmentId: (String?) -> Unit,
    equipmentDescription: String,
    onEquipmentDescription: (String) -> Unit,
    templates: List<TemplateSummary>,
    templateId: String?,
    onTemplateId: (String?) -> Unit,
    equipment: List<EquipmentSummary>,
    allowKnownEquipment: Boolean,
    valid: Boolean,
    editing: Boolean,
    onSave: () -> Unit,
) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (editing) "Edit task" else "Add task", fontWeight = FontWeight.Bold)
            DailyField(taskName, onTaskName, "Task name · Required")
            Text("Subject", fontWeight = FontWeight.Medium)
            ServiceLoopChoiceGroup(listOf(WorkSubjectType.SITE to "Site", WorkSubjectType.EQUIPMENT to "Equipment"), subjectType, onSubjectType, testTagPrefix = "task-subject")
            if (subjectType == WorkSubjectType.EQUIPMENT) {
                if (!allowKnownEquipment) {
                    Text("No registered equipment is available in a new one-time branch.", style = MaterialTheme.typography.bodySmall)
                    DailyField(equipmentDescription, onEquipmentDescription, "Equipment description · Optional")
                } else {
                    ServiceLoopChoiceGroup(listOf<Pair<String?, String>>(null to "No specific equipment yet") + equipment.map { it.id to "${it.name} · ${it.reference}" }, equipmentId, onEquipmentId, testTagPrefix = "task-equipment")
                    if (equipmentId == null) DailyField(equipmentDescription, onEquipmentDescription, "Equipment description · Optional")
                }
            }
            Text("Inspection checklist", fontWeight = FontWeight.Medium)
            ServiceLoopChoiceGroup(listOf<Pair<String?, String>>(null to "None") + templates.filter { it.state == "ACTIVE" }.map { it.id to "${it.name} · r${it.revisionNumber}" }, templateId, onTemplateId, testTagPrefix = "task-template")
            OutlinedButton(onSave, enabled = valid, modifier = Modifier.fillMaxWidth().testTag(if (editing) "update-task" else "add-task")) { Text(if (editing) "Update task" else "Add task") }
        }
    }
}

@Composable
private fun NewVisitTaskRow(index: Int, task: NewVisitTaskDraft, templates: List<TemplateSummary>, equipment: List<EquipmentSummary>, onEdit: () -> Unit, onRemove: () -> Unit) {
    ServiceLoopEntityRecord(
        title = task.taskName,
        context = when (task.subjectType) {
            WorkSubjectType.SITE -> "Site"
            WorkSubjectType.EQUIPMENT -> task.equipmentId?.let { id -> equipment.firstOrNull { it.id == id }?.name } ?: task.equipmentDescription.ifBlank { "Equipment not specified" }
        },
        metadata = task.templateId?.let { id -> templates.firstOrNull { it.id == id }?.let { "Inspection checklist · ${it.name} · r${it.revisionNumber}" } } ?: "No checklist",
        modifier = Modifier.testTag("visit-task-$index"),
        onClick = onEdit,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onEdit, Modifier.weight(1f).testTag("visit-task-edit-$index")) { Text("Edit") }
        TextButton(onRemove, Modifier.weight(1f).testTag("visit-task-remove-$index")) { Text("Remove") }
    }
}

@Composable
internal fun NewVisitScreen(sites: List<VisitSiteOption>, dueServices: List<DueService>, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController, initialPlanIds: List<String> = emptyList()) {
    var mode by rememberSaveable { mutableStateOf("EXISTING") }
    var siteId by rememberSaveable { mutableStateOf(dueServices.firstOrNull { it.planId in initialPlanIds }?.siteId) }
    var selectedPlans by rememberSaveable { mutableStateOf(initialPlanIds) }
    var date by rememberSaveable { mutableStateOf(state.businessDate.plusDays(1).toString()) }
    var siteQuery by rememberSaveable { mutableStateOf("") }
    var customerName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var locationLabel by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var taskName by rememberSaveable { mutableStateOf("") }
    var subjectType by rememberSaveable { mutableStateOf(WorkSubjectType.SITE) }
    var equipmentId by rememberSaveable { mutableStateOf<String?>(null) }
    var equipmentDescription by rememberSaveable { mutableStateOf("") }
    var templateId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var tasks by rememberSaveable(stateSaver = NewVisitTaskDraftListSaver) { mutableStateOf<List<NewVisitTaskDraft>>(emptyList()) }
    var pendingSiteId by remember { mutableStateOf<String?>(null) }
    var pendingSiteChange by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf<String?>(null) }
    val site = sites.firstOrNull { it.id == siteId }
    val setupRoute = nav.currentBackStackEntry?.destination?.route ?: "visit/new"
    val initialSite = dueServices.firstOrNull { it.planId in initialPlanIds }?.siteId
    val matchingSites = sites.filter { option -> siteQuery.isNotBlank() || option.customerType == CustomerType.STANDARD }.filter { option -> siteQuery.isBlank() || option.customerName.contains(siteQuery, true) || option.name.contains(siteQuery, true) || option.reference.contains(siteQuery, true) }
    val available = dueServices.filter { it.siteId == siteId && it.claimedVisitId == null }
    val equipment = site?.equipment.orEmpty()
    val allowKnownEquipment = mode == "EXISTING"
    val taskValid = taskName.trim().isNotBlank() && taskName.trim().length <= 200 && when {
        subjectType == WorkSubjectType.SITE -> equipmentId == null && equipmentDescription.isBlank()
        !allowKnownEquipment -> equipmentId == null && equipmentDescription.length <= 500
        else -> equipmentDescription.isBlank() && (equipmentId == null || equipment.any { it.id == equipmentId })
    }
    val validDate = runCatching { LocalDate.parse(date) }.isSuccess
    val visitValid = validDate && !state.operationInProgress && if (mode == "ONE_TIME") customerName.trim().isNotBlank() && tasks.isNotEmpty() else site != null && (selectedPlans.isNotEmpty() || tasks.isNotEmpty())
    LaunchedEffect(initialPlanIds, dueServices) { if (siteId == null && initialPlanIds.isNotEmpty()) siteId = dueServices.firstOrNull { it.planId in initialPlanIds }?.siteId }
    UnsavedChangesGuard(mode != "EXISTING" || siteId != initialSite || selectedPlans != initialPlanIds || date != state.businessDate.plusDays(1).toString() || siteQuery.isNotBlank() || customerName.isNotBlank() || phone.isNotBlank() || email.isNotBlank() || locationLabel.isNotBlank() || address.isNotBlank() || tasks.isNotEmpty() || taskName.isNotBlank(), nav)
    fun resetTask() { taskName = ""; subjectType = WorkSubjectType.SITE; equipmentId = null; equipmentDescription = ""; templateId = null; editingIndex = null }
    fun changeSite(nextSiteId: String) { if (tasks.isEmpty()) { siteId = nextSiteId; selectedPlans = emptyList(); resetTask() } else { pendingSiteId = nextSiteId; pendingSiteChange = true } }
    fun clearSiteSelection() { siteId = null; selectedPlans = emptyList(); siteQuery = ""; resetTask() }
    fun requestSiteChange() { if (tasks.isEmpty()) clearSiteSelection() else { pendingSiteId = null; pendingSiteChange = true } }
    fun applyMode(nextMode: String) { mode = nextMode; siteId = if (nextMode == "EXISTING") siteId else null; selectedPlans = emptyList(); tasks = emptyList(); resetTask() }
    fun requestModeChange(nextMode: String) { if (nextMode == mode) return; if (tasks.isEmpty()) applyMode(nextMode) else pendingMode = nextMode }
    fun save(targetState: String) {
        val inputs = tasks.map { it.toInput() }
        val success: (String) -> Unit = { id ->
            if (targetState == "WORKING") {
                viewModel.resolveWorkingVisitResume(id) { workItemId ->
                    if (workItemId == null) {
                        nav.navigate("visit/$id") { popUpTo(setupRoute) { inclusive = true } }
                    } else {
                        // Put the Visit overview underneath the active Service so the
                        // workspace has a truthful logical context and route-aware exit.
                        nav.navigate("visit/$id") { popUpTo(setupRoute) { inclusive = true } }
                        nav.navigate("inspection/$workItemId")
                    }
                }
            } else nav.navigate("visit/$id") { popUpTo(setupRoute) { inclusive = true } }
        }
        if (mode == "ONE_TIME") viewModel.createOneTimeVisit(OneTimeVisitInput(customerName, phone, email, locationLabel, address), inputs, targetState, if (targetState == "WORKING") state.businessDate.toString() else date, null, success)
        else viewModel.createVisitForSite(site!!.id, selectedPlans, inputs, targetState, if (targetState == "WORKING") state.businessDate.toString() else date, null, success)
    }
    if (pendingSiteChange) AlertDialog(onDismissRequest = { pendingSiteChange = false; pendingSiteId = null }, title = { Text("Change site?") }, text = { Text("Tasks added for this site will be cleared.") }, confirmButton = { TextButton({ pendingSiteId?.let { siteId = it } ?: clearSiteSelection(); tasks = emptyList(); pendingSiteChange = false; pendingSiteId = null }) { Text("Change site") } }, dismissButton = { TextButton({ pendingSiteChange = false; pendingSiteId = null }) { Text("Keep current site") } })
    pendingMode?.let { requested -> AlertDialog(onDismissRequest = { pendingMode = null }, title = { Text("Change customer mode?") }, text = { Text("Tasks already added will be cleared.") }, confirmButton = { TextButton({ applyMode(requested); pendingMode = null }) { Text("Change mode") } }, dismissButton = { TextButton({ pendingMode = null }) { Text("Keep current mode") } }) }
    EditorColumn(padding, state, tag = "new-visit-form") {
        item { DailyHeading("Set up visit"); Text("Create local tasks for one site, or start with a one-time customer.") }
        item { ServiceLoopChoiceGroup(listOf("EXISTING" to "Existing", "ONE_TIME" to "One-time"), mode, { value -> if (initialPlanIds.isEmpty()) requestModeChange(value) }, testTagPrefix = "visit-mode") }
        if (mode == "ONE_TIME") item { DailyHeading("One-time customer"); DailyField(customerName, { customerName = it }, "Customer name · Required"); DailyField(phone, { phone = it }, "Phone"); DailyField(email, { email = it }, "Email"); DailyField(locationLabel, { locationLabel = it }, "Location label"); DailyField(address, { address = it }, "Service address") }
        else {
            item { Text("Customer / site", fontWeight = FontWeight.Bold); if (site != null) Row(verticalAlignment = Alignment.CenterVertically) { Text("${site.customerName} · ${site.name}", Modifier.weight(1f)); if (initialPlanIds.isEmpty()) TextButton(::requestSiteChange) { Text("Change") } } else DailyField(siteQuery, { siteQuery = it }, "Find customer or site") }
            if (site == null) items(matchingSites, key = { "visit-site-${it.id}" }) { option -> ServiceLoopEntityRecord("${option.reference} · ${option.name}", option.customerName, if (option.customerType == CustomerType.ONE_TIME) "One-time" else null, modifier = Modifier.testTag("visit-site-${option.id}"), onClick = { changeSite(option.id) }) }
            if (site == null && matchingSites.isEmpty()) item { Text(if (sites.isEmpty()) "Add a customer site before creating a visit." else "No matching customer sites.") }
            if (site != null && site.customerType == CustomerType.ONE_TIME) item { Text("One-time customers use ad-hoc work until made Standard.") }
            if (site != null && site.customerType == CustomerType.STANDARD) item { Text("Planned work", fontWeight = FontWeight.Bold); if (!state.dueServicesReady) { Text("Planned services are unavailable.", color = MaterialTheme.colorScheme.error); state.dueServicesError?.let { OutlinedButton({ viewModel.retryDueServices() }, Modifier.fillMaxWidth()) { Text("Retry") } } }; available.forEach { due -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(due.planId in selectedPlans, { checked -> selectedPlans = if (checked) selectedPlans + due.planId else selectedPlans - due.planId }); Text("${due.equipmentName} · ${due.planName} · Due ${due.dueDate}") } }; if (state.dueServicesReady && available.isEmpty()) Text("No unclaimed current plans at this site.") }
        }
        if (mode == "ONE_TIME" || site != null) item { NewVisitTaskEditor(taskName, { taskName = it }, subjectType, { value -> subjectType = value; if (value == WorkSubjectType.SITE) { equipmentId = null; equipmentDescription = "" } }, equipmentId, { equipmentId = it; equipmentDescription = "" }, equipmentDescription, { equipmentDescription = it }, state.templates, templateId, { templateId = it }, equipment, allowKnownEquipment, taskValid, editingIndex != null) { val draft = NewVisitTaskDraft(taskName.trim(), subjectType, equipmentId, equipmentDescription.trim(), templateId); tasks = if (editingIndex == null) tasks + draft else tasks.mapIndexed { index, old -> if (index == editingIndex) draft else old }; resetTask() } }
        if (tasks.isNotEmpty()) item { DailyHeading("Tasks"); tasks.forEachIndexed { index, task -> NewVisitTaskRow(index, task, state.templates, equipment, { taskName = task.taskName; subjectType = task.subjectType; equipmentId = task.equipmentId; equipmentDescription = task.equipmentDescription; templateId = task.templateId; editingIndex = index }, { tasks = tasks.filterIndexed { itemIndex, _ -> itemIndex != index } }) } }
        item { DailyField(date, { date = it }, "Appointment / service date · YYYY-MM-DD"); Text("Date-only booking in the ${state.businessZoneId} business zone."); val parsed = runCatching { LocalDate.parse(date) }.getOrNull(); val primary = when { parsed == null || parsed.isAfter(state.businessDate) -> "BOOKED"; parsed == state.businessDate -> "WORKING"; else -> "HISTORICAL" }; @Composable fun action(kind: String, label: String) { val enabled = visitValid && (kind != "HISTORICAL" || parsed != null && !parsed.isAfter(state.businessDate)); val click = { save(kind) }; if (primary == kind) ServiceLoopPrimaryButton(label, click, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("primary-visit-action-$kind")) else ServiceLoopSecondaryButton(label, click, enabled = enabled, modifier = Modifier.fillMaxWidth()) }; ServiceLoopActionStack { action("BOOKED", "Book visit"); action("WORKING", "Start now"); action("HISTORICAL", "Record past visit") } }
    }
}

@Composable
internal fun TemplateListScreen(values: List<TemplateSummary>, padding: PaddingValues, nav: NavHostController, viewModel: ServiceLoopViewModel? = null, incomingTemplates: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exchange = remember { InspectionTemplateExchangeService((context.applicationContext as ServiceLoopApplication).container.database) }
    val role = remember { context.teamRole() }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var preview by remember { mutableStateOf<InspectionTemplateImportPreview?>(null) }
    var createSeparate by remember { mutableStateOf<Set<String>>(emptySet()) }
    var message by remember { mutableStateOf<String?>(null) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input -> exchange.preview(input.readBounded(InspectionTemplateCodec.MAX_BYTES) ?: error("Selected file is too large")) }
                            ?: error("Selected file is not readable")
                    }
                }
                result.onSuccess { preview = it; createSeparate = emptySet() }.onFailure { message = it.message }
            }
        }
    }
    LaunchedEffect(incomingTemplates) {
        if (incomingTemplates != null) {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(Uri.parse(incomingTemplates))?.use { input -> exchange.preview(input.readBounded(InspectionTemplateCodec.MAX_BYTES) ?: error("Received inspection template file is too large")) }
                        ?: error("Received inspection template file is not readable")
                }
            }
            result.onSuccess { preview = it; createSeparate = emptySet() }.onFailure { message = it.message }
        }
    }
    preview?.let { incoming ->
        AlertDialog(
            modifier = Modifier.testTag("inspection-template-import-preview"),
            onDismissRequest = { preview = null },
            title = { Text("Import inspection templates") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Review the current-only .slinsp package before saving it locally.")
                    incoming.entries.forEach { entry ->
                        Text("${entry.transfer.reference} · ${entry.transfer.name}")
                        Text("Revision ${entry.transfer.revision} · ${entry.transfer.items.size} items · ${entry.classification.name.replace('_', ' ')}")
                        if (entry.classification == InspectionTemplateImportClassification.CONFLICT) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(entry.transfer.reference in createSeparate, { checked -> createSeparate = if (checked) createSeparate + entry.transfer.reference else createSeparate - entry.transfer.reference }, Modifier.testTag("create-separate-${entry.transfer.reference}"))
                                Text("Create separate")
                            }
                        }
                    }
                }
            },
            dismissButton = { TextButton({ preview = null }) { Text("Cancel") } },
            confirmButton = {
                Button({
                    scope.launch {
                        val result = runCatching { withContext(Dispatchers.IO) { exchange.import(incoming, createSeparate) } }
                        result.onSuccess { imported ->
                            message = "Imported ${imported.importedReferences.size} inspection template${if (imported.importedReferences.size == 1) "" else "s"}. Exact matches were left unchanged."
                            preview = null
                            viewModel?.loadTemplates()
                        }.onFailure { message = it.message }
                    }
                }, enabled = incoming.canImport(createSeparate), modifier = Modifier.testTag("import-inspection-templates")) { Text("Import selected") }
            },
        )
    }
    LazyColumn(Modifier.padding(padding).testTag("inspection-templates"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Button({ nav.navigate("template/new") }, Modifier.fillMaxWidth()) { Text("Create inspection template") }
            if (role in setOf(TeamRole.MEMBER, TeamRole.COORDINATOR)) {
                Spacer(Modifier.height(8.dp))
                Text("Inspection templates", style = MaterialTheme.typography.titleMedium)
                Text("Share reusable inspection definitions only. A work package still carries the immutable snapshot used by its Visit.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton({ open.launch(arrayOf(INSPECTION_TEMPLATES_MIME, "application/json")) }, Modifier.weight(1f).testTag("import-inspection-templates-button")) { Text("Import templates") }
                    OutlinedButton({ scope.launch { runCatching { val export = withContext(Dispatchers.IO) { exchange.export(selected) }; val bytes = withContext(Dispatchers.IO) { InspectionTemplateCodec.encode(export) }; shareFile(context, "inspection-templates", "serviceloop-inspection-templates-${System.currentTimeMillis()}.slinsp", INSPECTION_TEMPLATES_MIME, bytes, "Share inspection templates", "ServiceLoop inspection templates", "ServiceLoop inspection templates\nGenerated with ServiceLoop") }.onFailure { message = it.message } } }, enabled = selected.isNotEmpty(), modifier = Modifier.weight(1f).testTag("export-inspection-templates")) { Text("Export selected") }
                }
            }
            message?.let { Text(it, color = if (it.startsWith("Imported")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.testTag("inspection-template-message")) }
        }
        if (values.isEmpty()) item { Text("No reusable templates") }
        items(values, key = { it.id }) { template ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (role in setOf(TeamRole.MEMBER, TeamRole.COORDINATOR)) Checkbox(template.id in selected, { checked -> selected = if (checked) selected + template.id else selected - template.id }, Modifier.testTag("template-select-${template.id}"))
                ServiceLoopEntityRecord("${template.reference} · ${template.name}", metadata = "Revision ${template.revisionNumber} · ${template.itemCount} items", status = template.state, modifier = Modifier.weight(1f)) { nav.navigate("template/${template.id}") }
            }
        }
    }
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
private fun AdHocWorkEditor(
    equipment: List<EquipmentSummary>,
    templates: List<TemplateSummary>,
    allowKnownEquipment: Boolean,
    busy: Boolean,
    onAdd: (AdHocWorkInput) -> Unit,
) {
    var taskName by rememberSaveable { mutableStateOf("") }
    var subjectType by rememberSaveable { mutableStateOf(WorkSubjectType.SITE) }
    var equipmentId by rememberSaveable { mutableStateOf<String?>(null) }
    var equipmentDescription by rememberSaveable { mutableStateOf("") }
    var templateId by rememberSaveable { mutableStateOf<String?>(null) }
    val valid = taskName.isNotBlank() && taskName.length <= 200 && when {
        subjectType == WorkSubjectType.SITE -> equipmentId == null && equipmentDescription.isBlank()
        !allowKnownEquipment -> equipmentId == null && equipmentDescription.length <= 500
        else -> equipmentDescription.isBlank() && (equipmentId == null || equipment.any { it.id == equipmentId })
    }
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Add task", fontWeight = FontWeight.Bold)
            DailyField(taskName, { taskName = it }, "Task name · Required")
            Text("Subject", fontWeight = FontWeight.Medium)
            ServiceLoopChoiceGroup(listOf(WorkSubjectType.SITE to "Site", WorkSubjectType.EQUIPMENT to "Equipment"), subjectType, { value -> subjectType = value; if (value == WorkSubjectType.SITE) { equipmentId = null; equipmentDescription = "" } }, testTagPrefix = "visit-task-subject")
            if (subjectType == WorkSubjectType.EQUIPMENT) {
                if (!allowKnownEquipment) Text("No registered equipment is available in a new one-time branch.", style = MaterialTheme.typography.bodySmall)
                else ServiceLoopChoiceGroup(listOf<Pair<String?, String>>(null to "No specific equipment yet") + equipment.map { it.id to "${it.name} · ${it.reference}" }, equipmentId, { equipmentId = it; equipmentDescription = "" }, testTagPrefix = "visit-task-equipment")
                if (!allowKnownEquipment || equipmentId == null) DailyField(equipmentDescription, { equipmentDescription = it }, "Equipment description · Optional")
            }
            Text("Inspection checklist", fontWeight = FontWeight.Medium)
            ServiceLoopChoiceGroup(listOf<Pair<String?, String>>(null to "None") + templates.filter { it.state == "ACTIVE" }.map { it.id to "${it.name} · r${it.revisionNumber}" }, templateId, { templateId = it }, testTagPrefix = "visit-task-template")
            ServiceLoopPrimaryButton("Add task", { val input = AdHocWorkInput(taskName.trim(), subjectType, equipmentId, equipmentDescription.trim(), templateId); onAdd(input); taskName = ""; subjectType = WorkSubjectType.SITE; equipmentId = null; equipmentDescription = ""; templateId = null }, Modifier.fillMaxWidth().testTag("add-visit-task"), enabled = valid && !busy)
            if (!allowKnownEquipment) Text("This task will remain local to the one-time visit.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun VisitDateLandmark(state: String, persistedDate: String) {
    val colors = LocalServiceLoopTokens.current
    Column(Modifier.fillMaxWidth().testTag("visit-date-landmark"), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
        Text(if (state == "BOOKED") "APPOINTMENT" else "SERVICE DATE", style = ServiceLoopUiTokens.Type.dayHeading, color = colors.action, modifier = Modifier.testTag("visit-date-landmark-label"))
        Text(formatServiceLoopDate(persistedDate), style = ServiceLoopUiTokens.Type.screenTitle, modifier = Modifier.testTag("visit-date-landmark-value"))
    }
}

@Composable
internal fun VisitDetailScreen(detail: VisitDetail?, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    if (detail == null) return DailyEmpty(padding, "Reading visit")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var newDate by rememberSaveable(detail.id) { mutableStateOf(detail.serviceDate) }; var reason by rememberSaveable(detail.id) { mutableStateOf("") }; var cancelReason by rememberSaveable(detail.id) { mutableStateOf("") }; var oneOffName by rememberSaveable(detail.id){mutableStateOf("")}; var oneOffEquipment by rememberSaveable(detail.id){mutableStateOf<String?>(null)}
    var reviewError by rememberSaveable(detail.id) { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.padding(padding).testTag("visit-detail-list"), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)) {
         item { Column(Modifier.testTag("visit-identity")) { Text("${detail.reference} · ${detail.state.lowercase().replace('_',' ').replaceFirstChar(Char::uppercase)}", style = MaterialTheme.typography.headlineSmall); Text(detail.customerName); Text(detail.siteName); Text(detail.siteAddress); if (detail.customerType == CustomerType.ONE_TIME) Text("One-time customer", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("one-time-customer-label")) } }
        item { ServiceLoopResponsivePair(first = { ServiceLoopSecondaryButton("Customer", { nav.navigate("customer/${detail.customerId}") }, Modifier.fillMaxWidth().testTag("visit-customer-link")) }, second = { ServiceLoopSecondaryButton("Site", { nav.navigate("site/${detail.siteId}") }, Modifier.fillMaxWidth().testTag("visit-site-link")) }, modifier = Modifier.testTag("visit-relationship-actions")) }
        item { VisitDateLandmark(detail.state, detail.serviceDate) }
        item { DispatchVisitPanel(detail) }
        item { val calendar=state.visitCalendarState;Card(Modifier.fillMaxWidth().testTag("visit-calendar")){Column(Modifier.padding(12.dp)){Text("Calendar",fontWeight=FontWeight.Bold);Text(calendar?.label?:"Checking Calendar status");when(calendar?.action){"Add to Calendar","Recreate event"->OutlinedButton({viewModel.addVisitToCalendar(detail.id)},Modifier.fillMaxWidth().testTag("visit-calendar-add")){Text(calendar.action)};"Remove from Calendar"->OutlinedButton({viewModel.removeVisitFromCalendar(detail.id)},Modifier.fillMaxWidth().testTag("visit-calendar-remove")){Text(calendar.action)}};calendar?.eventId?.let{id->TextButton({viewModel.calendarEventIntent(id)?.let(context::startActivity)}){Text("Open Calendar event")}}}} }
        if (state.serviceProgress?.visitId == detail.id) item {
            VisitServiceProgressOverview(state.serviceProgress, onSelect = { item -> nav.navigate("inspection/${item.workItemId}") })
        } else {
            items(detail.lines) { line -> ServiceLoopWorkItemRow(serviceLoopSubjectLabel(line.subjectType, line.equipmentName, line.equipmentReference, line.equipmentDescription),line.serviceName,"Due ${line.dueDate ?: "one-off"} · ${line.outcome?.lowercase()?.replace('_',' ') ?: detail.state.lowercase().replaceFirstChar(Char::uppercase)}",detail.state=="WORKING",Modifier.testTag("visit-line-${line.workItemId}")){nav.navigate("inspection/${line.workItemId}")} }
        }
        if(detail.state in setOf("BOOKED","WORKING")&&state.site!=null) item { AdHocWorkEditor(state.site.equipment, state.templates, allowKnownEquipment = true, state.operationInProgress) { input -> viewModel.addAdHocWork(detail.id, input) { viewModel.loadVisit(detail.id) } } }
        if (detail.state == "BOOKED") item { var rescheduleSaved by rememberSaveable(detail.id) { mutableStateOf(false) }; Text("Start reloads current customer, site, equipment, plan, business, and reusable-template details. Changed details replace booking-time display details in the Working visit."); Button({ viewModel.startVisit(detail.id) { id -> val target = viewModel.state.value.serviceProgress?.preferredResumeItem()?.workItemId; if (target != null) nav.navigate("inspection/$target") else nav.navigate("visit/$id") } }, enabled = detail.lines.isNotEmpty() && !state.operationInProgress, modifier = Modifier.fillMaxWidth().testTag("start-visit")) { Text("Start visit") }; if(detail.lines.isEmpty()) Text("Add at least one service line before starting."); Text("Appointment reminder",fontWeight=FontWeight.Bold); ServiceLoopChoiceGroup(listOf(null to "Default",0 to "Off") + ReminderPreferences.APPOINTMENT_LEAD_PRESETS,detail.appointmentReminderLeadMinutes,{viewModel.setAppointmentReminderLead(detail.id,it)},testTagPrefix="visit-reminder"); if (detail.appointmentReminderLeadMinutes == ReminderPreferences.LEGACY_APPOINTMENT_LEAD_MINUTES) Text("Current saved lead: 2h. Choose a new preset to replace it.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("legacy-visit-appointment-lead")); Column { DailyField(newDate, { newDate = it; rescheduleSaved=false }, "New appointment date"); if(rescheduleSaved) Row(Modifier.align(Alignment.End).testTag("reschedule-saved"),verticalAlignment=Alignment.CenterVertically){ServiceLoopIcon(ServiceLoopIcons.LocalSaved,null,Modifier.size(ServiceLoopUiTokens.Size.iconSmall),MaterialTheme.colorScheme.primary);Spacer(Modifier.width(ServiceLoopUiTokens.Space.xs));Text("Saved",color=MaterialTheme.colorScheme.primary)} }; LongTextEditor(reason, { reason = it; rescheduleSaved=false }, "Reschedule reason", false); OutlinedButton({ viewModel.rescheduleVisit(detail.id, newDate, null, reason) { rescheduleSaved=true } }, enabled = reason.isNotBlank() && runCatching { LocalDate.parse(newDate) }.isSuccess, modifier = Modifier.fillMaxWidth()) { Text("Reschedule booking") }; LongTextEditor(cancelReason, { cancelReason = it }, "Cancellation reason", false); OutlinedButton({ viewModel.cancelVisit(detail.id, cancelReason) { viewModel.loadVisit(it) } }, enabled = cancelReason.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Cancel booking") }; Text("Rescheduling or cancelling does not fulfill or alter the due obligation.") }
        if (detail.state == "CANCELED" && detail.cancellationOrigin !in setOf("COORDINATOR","ASSIGNMENT_REMOVAL")) item { var restoreDate by rememberSaveable(detail.id) { mutableStateOf(if (runCatching { LocalDate.parse(detail.serviceDate) }.getOrNull()?.isBefore(state.businessDate) == true) state.businessDate.toString() else detail.serviceDate) }; Text("This cancellation left the obligation due."); DailyField(restoreDate, { restoreDate=it }, "Restore appointment date · YYYY-MM-DD"); Spacer(Modifier.height(ServiceLoopUiTokens.Space.lg)); Button({ viewModel.restoreVisit(detail.id, restoreDate) { viewModel.loadVisit(it) } }, enabled=!state.operationInProgress && runCatching { !LocalDate.parse(restoreDate).isBefore(state.businessDate) }.getOrDefault(false), modifier=Modifier.fillMaxWidth().testTag("restore-booking")){Text("Restore booking")} }
        if (detail.state == "WORKING") item {
            val session = state.activeServiceWorkItemId?.takeIf { state.activeServiceVisitId == detail.id && state.serviceProgress?.items?.any { item -> item.workItemId == it } == true }
            val target = session ?: state.serviceProgress?.preferredResumeItem()?.workItemId ?: detail.lines.firstOrNull()?.workItemId
            ServiceLoopActionStack {
            ServiceLoopPrimaryButton("Resume service", { if (target != null) nav.navigate("inspection/$target") }, Modifier.fillMaxWidth().testTag("resume-service"), enabled = target != null)
            ServiceLoopSecondaryButton("Review visit", {
                scope.launch {
                    val result = runCatching { viewModel.flushVisitDraft(detail.id) }.getOrNull()
                    if (result?.success == true) {
                        reviewError = null
                        nav.navigate("review/${detail.id}")
                    } else reviewError = "Resolve unsaved service edits before continuing."
                }
            }, Modifier.fillMaxWidth().testTag("visit-review"), enabled = state.serviceProgress?.let { progress -> progress.actionableItems.isNotEmpty() && progress.actionableItems.all { it.status == com.v16studio.serviceloop.domain.ServiceEntryStatus.READY } } == true)
            }
            reviewError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("visit-review-error")) }
        }
        if (detail.state == "CANCELED") item { Text("Cancellation reason: ${detail.cancellationReason}"); if(detail.cancellationOrigin!=null) Text("Cancellation source: ${detail.cancellationOrigin.lowercase().replace('_',' ')}"); Text("The service obligation remains due and may be booked again.") }
    }
}

@Composable
internal fun EquipmentLinkScreen(workItemId: String, context: EquipmentLinkContext?, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    var showAdd by rememberSaveable(workItemId) { mutableStateOf(false) }
    var name by rememberSaveable(workItemId) { mutableStateOf("") }
    var identifier by rememberSaveable(workItemId) { mutableStateOf("") }
    var make by rememberSaveable(workItemId) { mutableStateOf("") }
    var model by rememberSaveable(workItemId) { mutableStateOf("") }
    var serial by rememberSaveable(workItemId) { mutableStateOf("") }
    var note by rememberSaveable(workItemId) { mutableStateOf("") }
    if (context == null || context.workItemId != workItemId) return DailyEmpty(padding, "Loading equipment")
    EditorColumn(padding, state) {
        item { DailyHeading("Identify equipment"); Text("${context.siteName} · Link this task to registered equipment or create a new item.") }
        if (context.equipment.isNotEmpty()) {
            item { Text("Registered equipment", fontWeight = FontWeight.Bold) }
            items(context.equipment, key = { "link-equipment-${it.id}" }) { equipment ->
                ServiceLoopEntityRecord("${equipment.name} · ${equipment.reference}", equipment.technicianIdentifier.orEmpty(), "Link to this task", modifier = Modifier.testTag("link-equipment-${equipment.id}")) {
                    viewModel.linkWorkItemEquipment(workItemId, equipment.id) { nav.popBackStack() }
                }
            }
        } else item { Text("No registered equipment exists at this site yet.") }
        item { OutlinedButton({ showAdd = !showAdd }, Modifier.fillMaxWidth().testTag("link-add-equipment")) { Text(if (showAdd) "Cancel new equipment" else "Add equipment") } }
        if (showAdd) item {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("New equipment", fontWeight = FontWeight.Bold)
                    DailyField(name, { name = it }, "Equipment name · Required")
                    DailyField(identifier, { identifier = it }, "Technician identifier")
                    DailyField(make, { make = it }, "Make")
                    DailyField(model, { model = it }, "Model")
                    DailyField(serial, { serial = it }, "Serial")
                    LongTextEditor(note, { note = it }, "Private equipment note", true)
                    ServiceLoopPrimaryButton("Save and link equipment", { viewModel.createAndLinkEquipment(workItemId, EquipmentInput(name, identifier, make, model, serial, note)) { nav.popBackStack() } }, Modifier.fillMaxWidth().testTag("save-and-link-equipment"), enabled = name.isNotBlank() && !state.operationInProgress)
                }
            }
        }
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
internal fun ServiceEvidenceContent(workItemId: String, state: UiState, viewModel: ServiceLoopViewModel, editingEnabled: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var addingPart by rememberSaveable(workItemId) { mutableStateOf(false) }
    var partToEdit by rememberSaveable(workItemId) { mutableStateOf<String?>(null) }
    var description by rememberSaveable(workItemId, partToEdit) { mutableStateOf(state.parts.firstOrNull { it.id == partToEdit }?.description.orEmpty()) }
    var quantity by rememberSaveable(workItemId, partToEdit) { mutableStateOf(state.parts.firstOrNull { it.id == partToEdit }?.quantity ?: "1") }
    var unit by rememberSaveable(workItemId, partToEdit) { mutableStateOf(state.parts.firstOrNull { it.id == partToEdit }?.unit ?: "item") }
    var followOpen by rememberSaveable(workItemId) { mutableStateOf(false) }
    var followTitle by rememberSaveable(workItemId) { mutableStateOf("") }
    var followDue by rememberSaveable(workItemId) { mutableStateOf(state.businessDate.plusDays(7).toString()) }
    var followNote by rememberSaveable(workItemId) { mutableStateOf("") }
    var cameraPath by rememberSaveable(workItemId) { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBounded(MAX_PHOTO_PICK_BYTES) } }
            if (bytes != null) viewModel.savePhoto(workItemId, bytes, uri.lastPathSegment, context.contentResolver.getType(uri) ?: "image/jpeg", false, null)
            else viewModel.reportOperationFailure("Photo not added — choose a readable image smaller than 30 MB")
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = cameraPath
        if (success && path != null) scope.launch {
            val file = File(path)
            val bytes = withContext(Dispatchers.IO) { if (file.isFile && file.length() in 1..MAX_PHOTO_PICK_BYTES.toLong()) file.readBytes() else null }
            if (bytes != null) viewModel.savePhoto(workItemId, bytes, file.name, "image/jpeg", false, null)
            else viewModel.reportOperationFailure("Photo not added — camera output was missing or too large")
            withContext(Dispatchers.IO) { file.delete() }
        }
        cameraPath = null
    }
    Column(verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)) {
        ServiceLoopSurfaceCard(modifier = Modifier.fillMaxWidth().testTag("service-parts")) {
            Text("Parts", style = MaterialTheme.typography.titleLarge)
            state.parts.forEach { part ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${part.description} · ${part.quantity} ${part.unit}", Modifier.weight(1f))
                    if (editingEnabled) TextButton({ partToEdit = part.id; addingPart = true }, modifier = Modifier.testTag("edit-part-${part.id}")) { Text("Edit") }
                }
            }
            if (editingEnabled && !addingPart) OutlinedButton({ partToEdit = null; addingPart = true }, Modifier.fillMaxWidth().testTag("add-part")) { Text("Add part") }
            if (editingEnabled && addingPart) {
                DailyField(description, { description = it }, "Description")
                DailyField(quantity, { quantity = it }, "Positive quantity")
                DailyField(unit, { unit = it }, "Unit")
                Button({
                    val id = partToEdit
                    if (id == null) viewModel.addPart(workItemId, description, quantity, unit) { addingPart = false; partToEdit = null }
                    else viewModel.updatePart(workItemId, id, description, quantity, unit) { addingPart = false; partToEdit = null }
                }, enabled = description.isNotBlank() && unit.isNotBlank() && runCatching { java.math.BigDecimal(quantity) > java.math.BigDecimal.ZERO }.getOrDefault(false) && !state.operationInProgress, modifier = Modifier.fillMaxWidth()) { Text(if (partToEdit == null) "Add part" else "Update part") }
                partToEdit?.let { id -> TextButton({ viewModel.removePart(workItemId, id); addingPart = false; partToEdit = null }, Modifier.testTag("remove-part-$id")) { Text("Remove part") } }
                TextButton({ addingPart = false; partToEdit = null }) { Text("Cancel") }
            }
        }
        ServiceLoopSurfaceCard(modifier = Modifier.fillMaxWidth().testTag("service-photos")) {
            Text("Photos", style = MaterialTheme.typography.titleLarge)
            state.photos.forEach { photo -> ServicePhotoCard(workItemId, photo, context, viewModel, editingEnabled, state) }
            if (editingEnabled) {
                OutlinedButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth().testTag("choose-photo")) { Text("Choose photo") }
                OutlinedButton({
                    val directory = File(context.cacheDir, "camera-staging").apply { mkdirs() }
                    val file = File(directory, "capture-${System.currentTimeMillis()}.jpg")
                    cameraPath = file.absolutePath
                    camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.reports", file))
                }, Modifier.fillMaxWidth().testTag("take-photo")) { Text("Take photo") }
            }
        }
        ServiceLoopSurfaceCard(modifier = Modifier.fillMaxWidth().testTag("service-follow-up")) {
            Text("Corrective follow-up", style = MaterialTheme.typography.titleLarge)
            Text("Create future work only when it remains needed.")
            state.serviceFollowUps.forEach { follow -> Text("${follow.reference} · ${follow.title} · ${follow.state.lowercase()} · Due ${follow.dueDate}") }
            if (editingEnabled && !followOpen) OutlinedButton({ followOpen = true }, Modifier.fillMaxWidth().testTag("add-follow-up")) { Text("Add follow-up") }
            if (editingEnabled && followOpen) {
                DailyField(followTitle, { followTitle = it }, "Follow-up title")
                DailyField(followDue, { followDue = it }, "Due date (YYYY-MM-DD)")
                LongTextEditor(followNote, { followNote = it }, "PRIVATE planning note", true)
                OutlinedButton({ viewModel.createCorrectiveFollowUp(workItemId, followTitle, followDue, followNote) { followOpen = false; followTitle = ""; followNote = "" } }, enabled = followTitle.isNotBlank() && runCatching { LocalDate.parse(followDue) }.isSuccess && !state.operationInProgress, modifier = Modifier.fillMaxWidth()) { Text("Create follow-up") }
                TextButton({ followOpen = false }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun ServicePhotoCard(workItemId: String, photo: PhotoEntry, context: Context, viewModel: ServiceLoopViewModel, editingEnabled: Boolean, state: UiState) {
    val initialCaption = state.inspection?.takeIf { it.workItemId == workItemId }?.rawInputs?.get(com.v16studio.serviceloop.domain.ServiceDraftFieldKeys.photoCaption(photo.id)) ?: photo.caption.orEmpty()
    var caption by rememberSaveable(photo.id, initialCaption) { mutableStateOf(initialCaption) }
    var include by rememberSaveable(photo.id, photo.includedInReport) { mutableStateOf(photo.includedInReport) }
    var confirmRemoval by rememberSaveable(photo.id) { mutableStateOf(false) }
    var fullView by rememberSaveable(photo.id) { mutableStateOf(false) }
    PhotoEvidenceCard(photo, context, onOpen = { fullView = true })
    if (state.photoMetadataPendingId == photo.id) Text("Saving photo details…", modifier = Modifier.testTag("photo-saving-${photo.id}"))
    if (state.photoMetadataErrorId == photo.id) Text("Photo details not saved — retry by editing the caption or report choice.", color = MaterialTheme.colorScheme.error)
    if (editingEnabled) {
        OutlinedTextField(caption, { caption = it; viewModel.schedulePhotoCaption(workItemId, photo.id, it) }, label = { Text("Caption") }, modifier = Modifier.fillMaxWidth().testTag("photo-caption-${photo.id}"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(include, { selected -> include = selected; viewModel.setPhotoReportInclusion(workItemId, photo.id, selected) }, modifier = Modifier.testTag("photo-report-${photo.id}"))
            Text("Include in customer report")
        }
        TextButton({ confirmRemoval = true }, Modifier.testTag("remove-photo-${photo.id}")) { Text("Remove photo") }
    }
    if (confirmRemoval) AlertDialog(onDismissRequest = { confirmRemoval = false }, title = { Text("Remove photo?") }, text = { Text("The photo will be removed from this Service and ServiceLoop's stored copy deleted. Your source image is unaffected.") }, confirmButton = { TextButton({ viewModel.removePhoto(workItemId, photo.id); confirmRemoval = false }, Modifier.testTag("confirm-remove-photo-${photo.id}")) { Text("Remove photo") } }, dismissButton = { TextButton({ confirmRemoval = false }) { Text("Cancel") } })
    if (fullView) AlertDialog(onDismissRequest = { fullView = false }, confirmButton = { TextButton({ fullView = false }) { Text("Close") } }, text = { val bitmap = remember(photo.relativePath) { BitmapFactory.decodeFile(File(context.filesDir, photo.relativePath).absolutePath) }; if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = photo.caption ?: "Service photo", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) else Text("Image unavailable") })
}

@Composable
private fun PhotoEvidenceCard(photo: PhotoEntry, context: Context, onOpen: (() -> Unit)? = null) {
    val colors = com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens.current
    val bitmap = remember(photo.relativePath, photo.byteSize) {
        BitmapFactory.decodeFile(File(context.filesDir, photo.relativePath).absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 })
    }
    ServiceLoopSurfaceCard(modifier = if (onOpen == null) Modifier else Modifier.clickable(onClick = onOpen).testTag("photo-${photo.id}")) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(88.dp).background(colors.photoMat, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = photo.caption?.takeIf(String::isNotBlank) ?: "Service evidence photograph", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Text("Image unavailable", color = colors.errorInk, style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (photo.includedInReport) "Included in customer report" else "Private evidence", fontWeight = FontWeight.SemiBold)
                Text(photo.caption?.takeIf(String::isNotBlank) ?: "No caption", style = MaterialTheme.typography.bodyMedium)
                Text("${photo.byteSize} bytes · Stored on this device", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
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
    LazyColumn(Modifier.padding(padding).testTag("search-results-list"), contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){ item { DailyField(query,{query=it},"Search names and references"); if(query.isBlank()) Text("Search customers, sites, equipment, plans, visits, final records, and follow-ups.") }; if(query.isNotBlank()&&results.isEmpty()) item { Text("No matching saved records.") }; items(results){ result -> ServiceLoopEntityRecord("${result.reference} · ${result.title}",result.subtitle,metadata=buildString { if (result.customerType == CustomerType.ONE_TIME) append("One-time · "); append(result.type.replace('_',' ')) }){ val route=when(result.type){"CUSTOMER"->"customer/${result.id}";"SITE"->"site/${result.id}";"EQUIPMENT"->"equipment/${result.id}";"PLAN"->"plan/${result.id}";"VISIT"->"visit/${result.id}";"FINAL_RECORD"->"record/${result.id}";"FOLLOW_UP"->"follow-up/${result.id}";else->null}; route?.let(nav::navigate) } } }
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
@Composable private fun DailyEmpty(padding:PaddingValues,value:String,onRetry:(() -> Unit)?=null){Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.md)){Text(value);onRetry?.let{ServiceLoopPrimaryButton("Retry",it,Modifier.testTag("retry-due-services"))}}}}
@Composable private fun PrivateBlock(label:String,value:String){Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){Column(Modifier.padding(12.dp)){Text(label,fontWeight=FontWeight.Bold);Text(value)}}}
private fun handoff(context:Context,intent:Intent,label:String)=if(runCatching{context.startActivity(intent);true}.getOrDefault(false)) "Opened $label · no contact outcome was recorded" else "No compatible $label app is available · copy the saved details manually"
private fun InputStream.readBounded(limit:Int):ByteArray? { val output=ByteArrayOutputStream(); val buffer=ByteArray(8192); var total=0; while(true){val count=read(buffer);if(count<0)break;total+=count;if(total>limit)return null;output.write(buffer,0,count)};return output.toByteArray() }
private const val MAX_PHOTO_PICK_BYTES=30*1024*1024
