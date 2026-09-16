package com.v16studio.serviceloop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.domain.*
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopButtonAdapter as Button
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChoiceGroup
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopEntityRecord
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSecondaryButton
import java.time.LocalDate

@Composable
internal fun FollowUpListScreen(values: List<FollowUpDetail>, padding: PaddingValues, nav: NavHostController) { LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { if (values.isEmpty()) item { Text("No follow-ups") }; items(values) { follow -> ServiceLoopEntityRecord("${follow.reference} · ${follow.title}",listOfNotNull(follow.customerName,follow.siteName,follow.equipmentName).filter{it.isNotBlank()}.joinToString(" · "),"${follow.type.lowercase()} · Due ${follow.dueDate}",follow.state){nav.navigate("follow-up/${follow.id}")} } } }

@Composable
internal fun FollowUpDetailScreen(detail: FollowUpDetail?, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    if (detail == null) return DailyEmpty(padding, "Reading follow-up"); var reason by rememberSaveable(detail.id) { mutableStateOf("") }; var newDue by rememberSaveable(detail.id) { mutableStateOf(state.businessDate.plusDays(7).toString()) }; var editTitle by rememberSaveable(detail.id){mutableStateOf(detail.title)}; var editDue by rememberSaveable(detail.id){mutableStateOf(detail.dueDate)}; var editNote by rememberSaveable(detail.id){mutableStateOf(detail.privatePlanningNote)}; var editReason by rememberSaveable(detail.id){mutableStateOf("")}
    UnsavedChangesGuard(editTitle!=detail.title||editDue!=detail.dueDate||editNote!=detail.privatePlanningNote||editReason.isNotBlank()||reason.isNotBlank()||(detail.state!="OPEN"&&newDue!=state.businessDate.plusDays(7).toString()),nav)
    LazyColumn(Modifier.padding(padding).testTag("follow-up-detail-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { item { Text("${detail.reference} · ${detail.title}", style = MaterialTheme.typography.headlineSmall); Text("${detail.type} · ${detail.state} · Due ${detail.dueDate}"); Text(listOfNotNull(detail.customerName,detail.siteName,detail.equipmentName).filter{it.isNotBlank()}.joinToString(" · ")); if(detail.state=="OPEN"){ DailyField(editTitle,{editTitle=it},"Title"); DailyField(editDue,{editDue=it},"Follow-up date"); LongTextEditor(editNote,{editNote=it},"PRIVATE planning note",true); if(editDue!=detail.dueDate) LongTextEditor(editReason,{editReason=it},"Date-change reason · Required",false); ServiceLoopPrimaryButton("Save follow-up changes",{viewModel.updateFollowUp(detail.id,editTitle,editDue,editNote,editReason){viewModel.loadFollowUp(it)}},Modifier.fillMaxWidth(),enabled=editTitle.isNotBlank()&&(editDue==detail.dueDate||editReason.isNotBlank())) } else if (detail.privatePlanningNote.isNotBlank()) PrivateBlock("PRIVATE planning note", detail.privatePlanningNote); LongTextEditor(reason, { reason=it }, if (detail.state == "OPEN") "Outcome or cancellation reason" else "Reopen reason", true); if (detail.state != "OPEN") DailyField(newDue, { newDue=it }, "New follow-up date"); if (detail.state == "OPEN") Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement = Arrangement.spacedBy(8.dp)) { ServiceLoopPrimaryButton("Resolve",{viewModel.changeFollowUpState(detail.id,"RESOLVED",reason,null){viewModel.loadFollowUp(it)}},Modifier.weight(1f).fillMaxHeight(),enabled=reason.isNotBlank()&&!state.operationInProgress); ServiceLoopSecondaryButton("Cancel",{viewModel.changeFollowUpState(detail.id,"CANCELLED",reason,null){viewModel.loadFollowUp(it)}},Modifier.weight(1f).fillMaxHeight(),enabled=reason.isNotBlank()&&!state.operationInProgress) } else Button({ viewModel.changeFollowUpState(detail.id,"OPEN",reason,newDue) { viewModel.loadFollowUp(it) } }, enabled=reason.isNotBlank(), modifier=Modifier.fillMaxWidth()) { Text("Reopen") }; Text("Follow-up state never changes a service plan or historical report.") } }
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
