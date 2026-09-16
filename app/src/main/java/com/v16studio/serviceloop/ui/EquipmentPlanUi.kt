package com.v16studio.serviceloop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.domain.*
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopActionStack
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopButtonAdapter as Button
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDangerTonalButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopEntityRecord
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPresetChoiceGroup
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSecondaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChoiceGroup
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import java.time.LocalDate

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
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)) { item { Text("${plan.reference} · ${plan.name}", style = MaterialTheme.typography.headlineSmall); Text(plan.equipmentName); Text("Every ${plan.intervalCount} ${plan.intervalUnit.lowercase()} · Due ${plan.dueDate}"); Text("Current obligation remains separate from bookings and contact."); Spacer(Modifier.height(ServiceLoopUiTokens.Space.large)); ServiceLoopActionStack { ServiceLoopPrimaryButton("Edit plan",{ nav.navigate("plan/edit/${plan.id}") }, Modifier.fillMaxWidth(), enabled=plan.state!="ENDED"); ServiceLoopSecondaryButton("Create visit",{ nav.navigate("visit/new/${plan.id}") }, Modifier.fillMaxWidth(), enabled=plan.state=="ACTIVE"); if(plan.state=="ACTIVE") ServiceLoopSecondaryButton("Pause plan",{nav.navigate("lifecycle/PLAN/${plan.id}/PAUSE")},Modifier.fillMaxWidth()) else if(plan.state=="PAUSED") ServiceLoopSecondaryButton("Resume plan",{nav.navigate("lifecycle/PLAN/${plan.id}/RESUME")},Modifier.fillMaxWidth()); if(plan.state!="ENDED") ServiceLoopDangerTonalButton("End plan",{nav.navigate("lifecycle/PLAN/${plan.id}/END")},Modifier.fillMaxWidth()) } } }
}

@Composable
internal fun PlanEditorScreen(equipmentId: String?, existing: PlanDetail?, templates: List<TemplateSummary>, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }; var count by rememberSaveable(existing?.id) { mutableStateOf(existing?.intervalCount?.toString() ?: "6") }; var unit by rememberSaveable(existing?.id) { mutableStateOf(existing?.intervalUnit ?: "MONTHS") }; var due by rememberSaveable(existing?.id) { mutableStateOf(existing?.dueDate ?: state.businessDate.toString()) }; var templateId by rememberSaveable(existing?.id) { mutableStateOf(existing?.reusableTemplateId) }; var dueReason by rememberSaveable(existing?.id){mutableStateOf("")}
    val createdTemplateId = nav.currentBackStackEntry?.savedStateHandle?.getStateFlow<String?>("created-inspection-template-id", null)?.collectAsState()
    LaunchedEffect(createdTemplateId?.value) { createdTemplateId?.value?.let { id -> templateId = id; nav.currentBackStackEntry?.savedStateHandle?.remove<String>("created-inspection-template-id"); viewModel.loadTemplates() } }
    UnsavedChangesGuard(name!=existing?.name.orEmpty()||count!=(existing?.intervalCount?.toString()?:"6")||unit!=(existing?.intervalUnit?:"MONTHS")||due!=(existing?.dueDate?:state.businessDate.toString())||templateId!=existing?.reusableTemplateId||dueReason.isNotBlank(),nav)
    EditorColumn(padding, state, "plan-editor") {
        item { DailyHeading(if (existing == null) "Add recurring service plan" else "Edit ${existing.reference}"); DailyField(name, { name = it }, "Plan name · Required"); DailyField(count, { count = it }, "Positive interval") }
        item { ServiceLoopPresetChoiceGroup(listOf("DAYS","WEEKS","MONTHS","YEARS").map{it to it.lowercase().replaceFirstChar(Char::uppercase)},unit,{unit=it}); DailyField(due, { due = it }, "Next due date · YYYY-MM-DD"); if(existing!=null&&due!=existing.dueDate) LongTextEditor(dueReason,{dueReason=it},"Due-date change reason · Required",false) }
        item { InspectionChecklistSelector(templates, templateId, { templateId = it }, "plan-template", onCreateTemplate = { nav.navigate("template/new?returnTo=plan") }, label = "Reusable inspection template") }
        item { Button({ val parsed = count.toIntOrNull() ?: 0; val input = PlanInput(name, parsed, unit, due, templateId,dueReason); if (existing == null) viewModel.createPlan(equipmentId!!, input) { nav.navigate("plan/$it") { popUpTo("plan/new/$equipmentId") { inclusive = true } } } else viewModel.updatePlan(existing.id, input) { nav.popBackStack() } }, enabled = name.isNotBlank() && (count.toIntOrNull() ?: 0) > 0 && runCatching { LocalDate.parse(due) }.isSuccess && (existing==null||due==existing.dueDate||dueReason.isNotBlank()) && !state.operationInProgress, modifier = Modifier.fillMaxWidth().testTag("save-plan")) { Text("Save plan") } }
    }
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
