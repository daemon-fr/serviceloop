package com.v16studio.v16service.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.v16studio.v16service.domain.*
import com.v16studio.v16service.ui.designsystem.V16ServiceButtonAdapter as Button
import com.v16studio.v16service.ui.designsystem.V16ServiceChoiceGroup
import com.v16studio.v16service.ui.designsystem.V16ServiceChoicePair
import com.v16studio.v16service.ui.designsystem.V16ServiceEntityRecord
import com.v16studio.v16service.ui.designsystem.V16ServicePrimaryButton
import com.v16studio.v16service.ui.designsystem.V16ServiceSecondaryButton
import com.v16studio.v16service.ui.designsystem.LocalV16ServiceTokens
import com.v16studio.v16service.ui.designsystem.V16ServiceUiTokens
import com.v16studio.v16service.ui.icons.V16ServiceIcon
import com.v16studio.v16service.ui.icons.V16ServiceIcons
import java.time.LocalDate

internal val CONTACT_CHANNELS = listOf(
    "CALL" to "Call",
    "SMS" to "SMS",
    "EMAIL" to "Email",
    "IN_PERSON" to "In person",
    "OTHER" to "Other",
)

internal fun contactChannelLabel(channel: String): String = CONTACT_CHANNELS.firstOrNull { it.first == channel }?.second ?: channel

internal fun contactChannelIcon(channel: String): Int = when (channel) {
    "CALL" -> V16ServiceIcons.Call
    "SMS" -> V16ServiceIcons.Sms
    "EMAIL" -> V16ServiceIcons.Mail
    "IN_PERSON" -> V16ServiceIcons.Person
    else -> V16ServiceIcons.ContactOtherWebcam
}

@Composable
internal fun ContactChannelChoices(selected: String, onSelected: (String) -> Unit) {
    val colors = LocalV16ServiceTokens.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CONTACT_CHANNELS.forEach { (value, label) ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = V16ServiceUiTokens.Size.touchMin)
                    .selectable(selected = value == selected, enabled = true, role = Role.RadioButton) { onSelected(value) }
                    .semantics { contentDescription = label }
                    .testTag("contact-channel-$value"),
                shape = RoundedCornerShape(V16ServiceUiTokens.Radius.field),
                color = if (value == selected) colors.selection else colors.surface,
                border = BorderStroke(V16ServiceUiTokens.Stroke.outline, if (value == selected) colors.selectionOutline else colors.outlineControl),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    V16ServiceIcon(contactChannelIcon(value), null, Modifier.size(V16ServiceUiTokens.Size.icon), if (value == selected) colors.selectionInk else colors.icon)
                }
            }
        }
    }
}

@Composable
internal fun FollowUpListScreen(values: List<FollowUpDetail>, padding: PaddingValues, nav: NavHostController) { LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { if (values.isEmpty()) item { Text("No follow-ups") }; items(values) { follow -> V16ServiceEntityRecord("${follow.reference} · ${follow.title}",listOfNotNull(follow.customerName,follow.siteName,follow.equipmentName).filter{it.isNotBlank()}.joinToString(" · "),"${follow.type.lowercase()} · Due ${follow.dueDate}",follow.state){nav.navigate("follow-up/${follow.id}")} } } }

@Composable
internal fun FollowUpDetailScreen(detail: FollowUpDetail?, padding: PaddingValues, state: UiState, viewModel: V16ServiceViewModel, nav: NavHostController) {
    if (detail == null) return DailyEmpty(padding, "Reading follow-up"); var reason by rememberSaveable(detail.id) { mutableStateOf("") }; var newDue by rememberSaveable(detail.id) { mutableStateOf(state.businessDate.plusDays(7).toString()) }; var editTitle by rememberSaveable(detail.id){mutableStateOf(detail.title)}; var editDue by rememberSaveable(detail.id){mutableStateOf(detail.dueDate)}; var editNote by rememberSaveable(detail.id){mutableStateOf(detail.privatePlanningNote)}; var editReason by rememberSaveable(detail.id){mutableStateOf("")}
    UnsavedChangesGuard(editTitle!=detail.title||editDue!=detail.dueDate||editNote!=detail.privatePlanningNote||editReason.isNotBlank()||reason.isNotBlank()||(detail.state!="OPEN"&&newDue!=state.businessDate.plusDays(7).toString()),nav)
  LazyColumn(Modifier.padding(padding).testTag("follow-up-detail-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { item { Text("${detail.reference} · ${detail.title}", style = MaterialTheme.typography.headlineSmall); Text("${detail.type} · ${detail.state} · Due ${detail.dueDate}"); Text(listOfNotNull(detail.customerName,detail.siteName,detail.equipmentName).filter{it.isNotBlank()}.joinToString(" · ")); if(detail.state=="OPEN"){ Spacer(Modifier.height(V16ServiceUiTokens.Space.md)); DailyField(editTitle,{editTitle=it},"Title"); DailyField(editDue,{editDue=it},"Follow-up date"); LongTextEditor(editNote,{editNote=it},"PRIVATE planning note",true); if(editDue!=detail.dueDate) LongTextEditor(editReason,{editReason=it},"Date-change reason · Required",false); V16ServicePrimaryButton("Save follow-up changes",{viewModel.updateFollowUp(detail.id,editTitle,editDue,editNote,editReason){viewModel.loadFollowUp(it)}},Modifier.fillMaxWidth(),enabled=editTitle.isNotBlank()&&(editDue==detail.dueDate||editReason.isNotBlank())) } else if (detail.privatePlanningNote.isNotBlank()) PrivateBlock("PRIVATE planning note", detail.privatePlanningNote); LongTextEditor(reason, { reason=it }, if (detail.state == "OPEN") "Outcome or cancellation reason" else "Reopen reason", true); if (detail.state != "OPEN") DailyField(newDue, { newDue=it }, "New follow-up date"); if (detail.state == "OPEN") Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement = Arrangement.spacedBy(8.dp)) { V16ServicePrimaryButton("Resolve",{viewModel.changeFollowUpState(detail.id,"RESOLVED",reason,null){viewModel.loadFollowUp(it)}},Modifier.weight(1f).fillMaxHeight(),enabled=reason.isNotBlank()&&!state.operationInProgress); V16ServiceSecondaryButton("Cancel",{viewModel.changeFollowUpState(detail.id,"CANCELLED",reason,null){viewModel.loadFollowUp(it)}},Modifier.weight(1f).fillMaxHeight(),enabled=reason.isNotBlank()&&!state.operationInProgress) } else Button({ viewModel.changeFollowUpState(detail.id,"OPEN",reason,newDue) { viewModel.loadFollowUp(it) } }, enabled=reason.isNotBlank(), modifier=Modifier.fillMaxWidth()) { Text("Reopen") } } }
}

@Composable
internal fun FollowUpEditorScreen(customerId: String, padding: PaddingValues, state: UiState, viewModel: V16ServiceViewModel, nav: NavHostController) {
    var type by rememberSaveable { mutableStateOf("CONTACT") }; var title by rememberSaveable { mutableStateOf("") }; var due by rememberSaveable { mutableStateOf(state.businessDate.toString()) }; var note by rememberSaveable { mutableStateOf("") }
    UnsavedChangesGuard(type!="CONTACT"||title.isNotBlank()||due!=state.businessDate.toString()||note.isNotBlank(),nav)
    EditorColumn(padding,state) { item { DailyHeading("Add follow-up"); V16ServiceChoicePair(listOf("CONTACT" to "Contact", "CORRECTIVE" to "Corrective"),type,{type=it}, testTagPrefix = "follow-up-type", stackWhenLargeFont = false); DailyField(title,{title=it},"Title · Required"); DailyField(due,{due=it},"Follow-up date"); LongTextEditor(note,{note=it},"PRIVATE planning note",true); Button({ viewModel.createFollowUp(FollowUpInput(type,title,due,customerId,privatePlanningNote=note)) { nav.navigate("follow-up/$it") { popUpTo("follow-up/new/$customerId") { inclusive=true } } } },enabled=title.isNotBlank()&&runCatching{LocalDate.parse(due)}.isSuccess&&!state.operationInProgress,modifier=Modifier.fillMaxWidth()){Text("Save follow-up")} } }
}

@Composable
internal fun ContactNoteEditorScreen(customerId: String, padding: PaddingValues, state: UiState, viewModel: V16ServiceViewModel, nav: NavHostController) {
    var channel by rememberSaveable { mutableStateOf("CALL") }; var outcome by rememberSaveable { mutableStateOf("") }; var note by rememberSaveable { mutableStateOf("") }
    UnsavedChangesGuard(channel!="CALL"||outcome.isNotBlank()||note.isNotBlank(),nav)
    EditorColumn(padding,state) {
        item {
            Text("Channel", style = V16ServiceUiTokens.Type.label)
            ContactChannelChoices(channel) { channel = it }
            LongTextEditor(outcome, { outcome = it }, "Outcome · Required", false, compact = true)
            LongTextEditor(note, { note = it }, "Private note · Optional", true, compact = true)
            Button(
                { viewModel.createContactNote(ContactNoteInput(customerId, channel = channel, outcome = outcome, privateNote = note)) { nav.popBackStack() } },
                enabled = outcome.isNotBlank() && !state.operationInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save contact note") }
        }
    }
}
