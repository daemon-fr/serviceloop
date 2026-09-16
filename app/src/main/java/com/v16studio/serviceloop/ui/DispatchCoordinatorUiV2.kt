package com.v16studio.serviceloop.ui

import com.v16studio.serviceloop.ui.designsystem.ServiceLoopButtonAdapter as Button
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopOutlinedButtonAdapter as OutlinedButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextButtonAdapter as TextButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopIconButtonAdapter as IconButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextFieldAdapter as OutlinedTextField
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopCardAdapter as Card
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopElevatedCardAdapter as ElevatedCard
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChoiceGroup
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSelectionOption

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.ServiceLoopApplication
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.domain.CustomerType
import com.v16studio.serviceloop.domain.OneTimeVisitInput
import com.v16studio.serviceloop.domain.WorkSubjectType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.v16studio.serviceloop.ui.designsystem.*
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons

private fun dispatchService(context:Context)=DispatchPackageService((context.applicationContext as ServiceLoopApplication).container.database,context.filesDir)

@Composable private fun DispatchValueDialog(title:String,label:String,value:String,optional:Boolean=false,onDismiss:()->Unit,onApply:(String)->Unit){
    var staged by remember(value){mutableStateOf(value)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={ServiceLoopTextField(staged,{staged=it},label,singleLine=true)},dismissButton={ServiceLoopTextAction("Cancel",onDismiss)},confirmButton={Row{if(optional&&staged.isNotBlank())ServiceLoopTextAction("Clear",{onApply("")});ServiceLoopPrimaryButton("Apply",{onApply(staged)})}})
}

@Composable private fun DispatchCancellationReasonDialog(title:String,reason:String,dispatched:Boolean,confirmLabel:String,onDismiss:()->Unit,onApply:(String)->Unit){
    var staged by remember(reason){mutableStateOf(reason)}
    AlertDialog(
        modifier=Modifier.testTag("dispatch-cancel-dialog"),
        onDismissRequest=onDismiss,
        title={Text(title)},
        text={Column(verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)){
            ServiceLoopTextField(staged,{staged=it},"Cancellation reason",singleLine=true,modifier=Modifier.testTag("dispatch-cancel-reason"))
            Text(if(dispatched)"A newer work package must be created to communicate this cancellation to technicians." else "This Draft stays in local cancellation history and is not exported.",color=LocalServiceLoopTokens.current.textSecondary)
        }},
        dismissButton={ServiceLoopTextAction("Cancel",onDismiss)},
        confirmButton={ServiceLoopPrimaryButton(confirmLabel,{onApply(staged.trim())},Modifier.testTag("dispatch-cancel-confirm"),enabled=staged.isNotBlank())},
    )
}

@Composable internal fun DispatchOutboxScreen(padding:PaddingValues,nav:NavHostController,businessDate:LocalDate,serviceOverride:DispatchPackageService?=null,databaseOverride:ServiceLoopDatabase?=null,showEmbeddedTopAction:Boolean=true){
    val context=LocalContext.current;val svc=remember(serviceOverride){serviceOverride?:dispatchService(context)};val db=databaseOverride?:(context.applicationContext as ServiceLoopApplication).container.database;val scope=rememberCoroutineScope();val lifecycleOwner=LocalLifecycleOwner.current
    var outbox by remember{mutableStateOf(emptyList<DispatchOutboxVisitEntity>())};var sites by remember{mutableStateOf(emptyList<SiteEntity>())};var customers by remember{mutableStateOf(emptyList<CustomerEntity>())};var itemCounts by remember{mutableStateOf(emptyMap<String,Int>())}
    var search by rememberSaveable{mutableStateOf("")};var statusFilter by rememberSaveable{mutableStateOf("Active")};var dateFilter by rememberSaveable{mutableStateOf("All dates")};var customStart by rememberSaveable{mutableStateOf(businessDate.toString())};var customEnd by rememberSaveable{mutableStateOf(businessDate.plusDays(7).toString())};var checked by remember{mutableStateOf(setOf<String>())};var pendingStatusAction by remember{mutableStateOf<String?>(null)};var cancelReason by rememberSaveable{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)}
    fun reload(){scope.launch{withContext(Dispatchers.IO){outbox=svc.outboxVisits();sites=db.serviceLoopDao().allSites();customers=db.serviceLoopDao().allCustomers();itemCounts=outbox.associate{it.dispatchVisitId to svc.outboxItems(it.dispatchVisitId).size}}}}
    LaunchedEffect(Unit){reload()};DisposableEffect(lifecycleOwner){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)reload()};lifecycleOwner.lifecycle.addObserver(observer);onDispose{lifecycleOwner.lifecycle.removeObserver(observer)}}
    val today=businessDate;val monday=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));val customRange=runCatching{LocalDate.parse(customStart)..LocalDate.parse(customEnd)}.getOrNull();val siteMap=sites.associateBy{it.id};val customerMap=customers.associateBy{it.id};val needle=search.trim().lowercase()
    val visible=outbox.filter{visit->val statusMatch=when(statusFilter){"Active"->visit.outboxStatus in setOf(DispatchOutboxStatus.DRAFT,DispatchOutboxStatus.DISPATCHED);"Draft"->visit.outboxStatus==DispatchOutboxStatus.DRAFT;"Dispatched"->visit.outboxStatus==DispatchOutboxStatus.DISPATCHED;"Canceled"->visit.outboxStatus==DispatchOutboxStatus.CANCELED;"Concluded"->visit.outboxStatus==DispatchOutboxStatus.CONCLUDED;else->true};val date=LocalDate.parse(visit.serviceDate);val dateMatch=when(dateFilter){"Today"->date==today;"Tomorrow"->date==today.plusDays(1);"This week"->date in monday..monday.plusDays(6);"Next 7 days"->date in today..today.plusDays(6);"Custom range"->customRange?.let{date in it}==true;else->true};val site=siteMap[visit.siteId];val customer=site?.let{customerMap[it.customerId]};val searchMatch=needle.isEmpty()||listOf(visit.managerReference,site?.reference,site?.name,customer?.reference,customer?.name).any{it?.lowercase()?.contains(needle)==true};statusMatch&&dateMatch&&searchMatch}.sortedWith(compareBy({it.serviceDate},{it.appointmentLocalTime.orEmpty()},{it.createdAtEpochMillis},{it.dispatchVisitId}))
    LaunchedEffect(visible.map{it.dispatchVisitId}){checked=checked.intersect(visible.map{it.dispatchVisitId}.toSet())};val selectedRows=visible.filter{it.dispatchVisitId in checked}
    when(pendingStatusAction){
        "cancel" -> DispatchCancellationReasonDialog("Cancel selected Visits",cancelReason,selectedRows.any{it.outboxStatus==DispatchOutboxStatus.DISPATCHED},"Apply",onDismiss={pendingStatusAction=null;cancelReason=""}){reason->
            val ids=selectedRows.map{it.dispatchVisitId}
            scope.launch{runCatching{withContext(Dispatchers.IO){svc.cancelOutboxVisits(ids,reason)}}.onSuccess{checked=emptySet();pendingStatusAction=null;cancelReason="";reload()}.onFailure{if(it is CancellationException)throw it else error=it.message;pendingStatusAction=null;cancelReason=""}}
        }
        "conclude", "reopen" -> AlertDialog(modifier=Modifier.testTag("dispatch-status-confirm"),onDismissRequest={pendingStatusAction=null},title={Text(if(pendingStatusAction=="conclude")"Mark ${selectedRows.size} dispatched Visits as concluded?" else "Reopen ${selectedRows.size} concluded Visits?")},text={Text(if(pendingStatusAction=="conclude")"This closes them only in this coordinator Outbox. Technician devices are not updated." else "Reopened Visits return to Dispatched. Export history and generation are preserved.")},dismissButton={ServiceLoopTextAction("Cancel",{pendingStatusAction=null})},confirmButton={ServiceLoopPrimaryButton(if(pendingStatusAction=="conclude")"Mark concluded" else "Reopen",{val ids=selectedRows.map{it.dispatchVisitId};scope.launch{runCatching{withContext(Dispatchers.IO){if(pendingStatusAction=="conclude")svc.concludeOutboxVisits(ids)else svc.reopenOutboxVisits(ids)}}.onSuccess{checked=emptySet();pendingStatusAction=null;reload()}.onFailure{if(it is CancellationException)throw it else error=it.message;pendingStatusAction=null}}},Modifier.testTag("dispatch-status-confirm-action"))})
        null -> Unit
        else -> Unit
    }
    var moreOpen by remember { mutableStateOf(false) }
    Column(Modifier.padding(padding).fillMaxSize().testTag("dispatch-outbox")) {
        Column(Modifier.fillMaxWidth().padding(ServiceLoopUiTokens.Layout.pageInsetCompact),verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)) {
            if(showEmbeddedTopAction) ServiceLoopPrimaryButton("New visit",{nav.navigate("dispatch/visit/new")},Modifier.fillMaxWidth().testTag("dispatch-new-visit"),leadingIcon={ServiceLoopIcon(ServiceLoopIcons.Add,null,Modifier.size(ServiceLoopUiTokens.Size.icon))})
            ServiceLoopTextField(search,{search=it},"Search visits",singleLine=true,modifier=Modifier.testTag("dispatch-search"))
            ServiceLoopFilterSelectorRow(
                first={ServiceLoopFilterSelector("Status",statusFilter,listOf("Active","Draft","Dispatched","Canceled","Concluded","All").map{it to it},{statusFilter=it},testTag="dispatch-status-filter")},
                second={ServiceLoopFilterSelector("Service date",dateFilter,listOf("Today","Tomorrow","This week","Next 7 days","Custom range","All dates").map{it to it},{dateFilter=it},testTag="dispatch-date-filter")},
            )
            if(dateFilter=="Custom range")ServiceLoopResponsivePair(
                first={ServiceLoopTextField(customStart,{customStart=it},"From",singleLine=true)},
                second={ServiceLoopTextField(customEnd,{customEnd=it},"Through",singleLine=true)},
            )
            Text("Tick visits to export or change their status together.",color=LocalServiceLoopTokens.current.textSecondary)
            error?.let{ServiceLoopNotice("Action needed",it,ServiceLoopNoticeKind.Error)}
            if(visible.isNotEmpty()) {
                val allVisibleSelected=visible.all{it.dispatchVisitId in checked}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text("${visible.size} ${if(statusFilter=="Active")"active " else ""}${if(visible.size==1)"visit" else "visits"}",style=MaterialTheme.typography.labelLarge,modifier=Modifier.weight(1f))
                    ServiceLoopTextAction(if(allVisibleSelected)"Deselect all shown" else "Select all shown",{checked=if(allVisibleSelected)checked-visible.map{it.dispatchVisitId}.toSet() else visible.map{it.dispatchVisitId}.toSet()},Modifier.testTag("dispatch-select-all"))
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("dispatch-outbox-list"),contentPadding=PaddingValues(ServiceLoopUiTokens.Layout.pageInsetCompact,0.dp,ServiceLoopUiTokens.Layout.pageInsetCompact,ServiceLoopUiTokens.Space.section),verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.md)){
            if(outbox.isEmpty())item{ServiceLoopNotice("No dispatch visits yet","Create a visit when work is ready to prepare for technicians.",ServiceLoopNoticeKind.Info)}
            else if(visible.isEmpty())item{ServiceLoopNotice(if(search.isNotBlank())"No matching dispatch visits" else "No visits match these filters","Clear search or change the active filters.",ServiceLoopNoticeKind.Info)}
            visible.groupBy{it.serviceDate}.forEach { (date, visits) ->
                item("heading-$date") { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(dispatchDayHeading(date),style=ServiceLoopUiTokens.DayHeadingStyle,modifier=Modifier.weight(1f));Text("${visits.size} ${if(visits.size==1)"VISIT" else "VISITS"}",style=ServiceLoopUiTokens.DayHeadingStyle,color=LocalServiceLoopTokens.current.textSecondary)} }
                items(visits,key={it.dispatchVisitId}){visit->
                    val site=siteMap[visit.siteId];val customer=site?.let{customerMap[it.customerId]};val selected=visit.dispatchVisitId in checked
                    ServiceLoopSurfaceCard(Modifier.testTag("dispatch-outbox-visit-${visit.dispatchVisitId}"),selected=selected){
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top){
                            Box(Modifier.size(ServiceLoopUiTokens.Size.touchMin),contentAlignment=Alignment.Center){Checkbox(selected,{isChecked->checked=if(isChecked)checked+visit.dispatchVisitId else checked-visit.dispatchVisitId},Modifier.testTag("dispatch-select-${visit.dispatchVisitId}").semantics{contentDescription="Select visit ${visit.managerReference?:visit.dispatchVisitId}"})}
                            Column(Modifier.weight(1f).serviceLoopFocusRing(ServiceLoopUiTokens.Radius.card).clickable(role=Role.Button){nav.navigate("dispatch/visit/${visit.dispatchVisitId}")}.testTag("dispatch-open-${visit.dispatchVisitId}").semantics{contentDescription="Open visit ${visit.managerReference?:visit.dispatchVisitId}"},verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)){
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top){Text(visit.appointmentLocalTime?:"Date only",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f));ServiceLoopStatusBadge(visit.outboxStatus.name)}
                                 Text(if(customer?.let{CustomerType.fromCode(it.customerType)}==CustomerType.ONE_TIME)"${customer?.name?:"Customer unavailable"} · One-time" else customer?.name?:"Customer unavailable",style=MaterialTheme.typography.titleMedium)
                                Text(listOfNotNull(site?.name,site?.address).joinToString(" · ").ifBlank{"Site unavailable"},color=LocalServiceLoopTokens.current.textSecondary)
                                Text("${visit.managerReference?:visit.dispatchVisitId.take(8)} · ${itemCounts[visit.dispatchVisitId]?:0} ${if((itemCounts[visit.dispatchVisitId]?:0)==1)"work item" else "work items"}${visit.lastExportedGeneration?.let{" · Version $it"}.orEmpty()}",color=LocalServiceLoopTokens.current.textSecondary,style=MaterialTheme.typography.bodyMedium)
                                 if((itemCounts[visit.dispatchVisitId]?:0)==0)Text("Needs a work item before export",color=LocalServiceLoopTokens.current.errorInk,style=MaterialTheme.typography.bodyMedium)
                                 if(visit.outboxStatus==DispatchOutboxStatus.CANCELED){
                                     when{
                                         visit.cancellationExportPending->Text("Cancellation export pending",color=LocalServiceLoopTokens.current.warningInk,style=MaterialTheme.typography.bodyMedium)
                                         visit.lastExportedGeneration!=null->Text("Cancellation exported · Version ${visit.lastExportedGeneration}",color=LocalServiceLoopTokens.current.textSecondary,style=MaterialTheme.typography.bodyMedium)
                                     }
                                 }
                            }
                        }
                    }
                }
            }
        }
        if(selectedRows.isNotEmpty())ServiceLoopPinnedBar(Modifier.testTag("dispatch-batch-actions")){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${selectedRows.size} selected",Modifier.weight(1f).testTag("dispatch-selected-count"),style=MaterialTheme.typography.titleMedium);ServiceLoopTextAction("Clear",{checked=emptySet()},Modifier.testTag("dispatch-clear-selection"))}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Layout.buttonGap)){
                 ServiceLoopPrimaryButton("Export (${selectedRows.size})",{when{selectedRows.size>DispatchPackageCodec.MAX_VISITS->error="A work package can contain at most ${DispatchPackageCodec.MAX_VISITS} Visits. Reduce the selection.";selectedRows.any{it.outboxStatus==DispatchOutboxStatus.CONCLUDED}->error="Concluded Visits must be reopened before export.";selectedRows.any{it.outboxStatus==DispatchOutboxStatus.CANCELED&&it.lastExportedGeneration==null}->error="A canceled Draft has no technician export to send.";selectedRows.any{(itemCounts[it.dispatchVisitId]?:0)==0}->error="Every selected Visit needs at least one work item before export.";else->{nav.currentBackStackEntry?.savedStateHandle?.set("dispatch-export-ids",ArrayList(selectedRows.map{it.dispatchVisitId}));nav.navigate("dispatch-export-review")}}},Modifier.weight(1f).testTag("dispatch-export-selected"))
                Box { ServiceLoopIconAction("More selected visit actions",{moreOpen=true},Modifier.testTag("dispatch-more-selection"),content = { ServiceLoopIcon(ServiceLoopIcons.More,null,Modifier.size(ServiceLoopUiTokens.Size.icon),LocalServiceLoopTokens.current.action) });DropdownMenu(moreOpen,{moreOpen=false}){
                    if(selectedRows.all{it.outboxStatus==DispatchOutboxStatus.DRAFT||it.outboxStatus==DispatchOutboxStatus.DISPATCHED})DropdownMenuItem({Text("Cancel")},{moreOpen=false;pendingStatusAction="cancel"},Modifier.testTag("dispatch-cancel-selected"))
                    if(selectedRows.all{it.outboxStatus==DispatchOutboxStatus.DISPATCHED})DropdownMenuItem({Text("Mark concluded")},{moreOpen=false;pendingStatusAction="conclude"},Modifier.testTag("dispatch-conclude-selected"))
                    else if(selectedRows.all{it.outboxStatus==DispatchOutboxStatus.CONCLUDED})DropdownMenuItem({Text("Reopen")},{moreOpen=false;pendingStatusAction="reopen"},Modifier.testTag("dispatch-reopen-selected"))
                    else DropdownMenuItem({Text("No status action for mixed selection")},{moreOpen=false},enabled=false)
                }}
            }
        }
    }
}

private fun dispatchDayHeading(value:String):String=runCatching{LocalDate.parse(value).format(DateTimeFormatter.ofPattern("EEE d MMM",Locale.ENGLISH)).uppercase(Locale.ENGLISH)}.getOrDefault(value)

@Composable internal fun DispatchSitePickerDialog(sites:List<SiteEntity>,customers:List<CustomerEntity>,onDismiss:()->Unit,onSelect:(String)->Unit){
    var query by rememberSaveable{mutableStateOf("")}
    val customersById=customers.associateBy{it.id};val needle=query.trim().lowercase()
    val results=sites.filter{site->
        if(site.state!="ACTIVE") false else {
            val customer=customersById[site.customerId];val type=customer?.let{CustomerType.fromCode(it.customerType)}
            val matching=listOf(site.reference,site.name,site.address,customer?.reference,customer?.name).any{it?.lowercase()?.contains(needle)==true}
            type==CustomerType.STANDARD || (needle.isNotBlank()&&type==CustomerType.ONE_TIME&&matching)
        }
    }
    AlertDialog(modifier=Modifier.testTag("dispatch-site-picker"),onDismissRequest=onDismiss,title={Text("Choose Site")},text={Column{
        OutlinedTextField(query,{query=it},label={Text("Search Site, Customer, or address")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("dispatch-site-search"))
        LazyColumn(Modifier.heightIn(max=420.dp).testTag("dispatch-site-picker-list")){items(results,key={it.id}){site->val customer=customersById[site.customerId];val oneTime=customer?.let{CustomerType.fromCode(it.customerType)}==CustomerType.ONE_TIME;TextButton({onSelect(site.id)},Modifier.fillMaxWidth().testTag("dispatch-site-${site.id}")){Column(Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${site.reference} · ${site.name}",Modifier.weight(1f));if(oneTime)Text("One-time",Modifier.testTag("dispatch-one-time-marker"),color=MaterialTheme.colorScheme.tertiary,style=MaterialTheme.typography.labelMedium)};Text(listOfNotNull(customer?.name,site.address).joinToString(" · "),style=MaterialTheme.typography.bodySmall)}}};if(results.isEmpty())item{Text("No matching Sites.",Modifier.padding(top=12.dp))}}}},confirmButton={TextButton(onDismiss){Text("Cancel")}})
}

@Composable private fun DispatchTeamPickerDialog(teams:List<DispatchTeamDetail>,selected:Set<String>,onDismiss:()->Unit,onApply:(Set<String>)->Unit){
    var staged by remember(selected){mutableStateOf(selected)}
    AlertDialog(modifier=Modifier.testTag("dispatch-team-picker"),onDismissRequest=onDismiss,title={Text("Choose Teams")},text={LazyColumn(Modifier.heightIn(max=420.dp)){items(teams,key={it.team.id}){team->Row(Modifier.fillMaxWidth().clickable{staged=if(team.team.id in staged)staged-team.team.id else staged+team.team.id}.testTag("dispatch-team-${team.team.id}"),verticalAlignment=Alignment.CenterVertically){Checkbox(team.team.id in staged,null);Column{Text(team.team.name);Text("${team.members.size} members",style=MaterialTheme.typography.bodySmall)}}};if(teams.isEmpty())item{Text("Create a Team in Coordinator tools first.")}}},dismissButton={TextButton(onDismiss){Text("Cancel")}},confirmButton={Button({onApply(staged)},Modifier.testTag("dispatch-team-apply")){Text("Apply")}})
}

@Composable private fun DispatchWorkItemDialog(existing:DispatchOutboxItemDraft?,equipment:List<EquipmentEntity>,customerType:CustomerType,participants:List<DispatchTechnicianEntity>,onDismiss:()->Unit,onSave:(DispatchOutboxItemDraft)->Unit){
    var subjectType by remember(existing){mutableStateOf(existing?.subjectType?:WorkSubjectType.EQUIPMENT)};var equipmentId by remember(existing){mutableStateOf(existing?.equipmentId)};var description by remember(existing){mutableStateOf(existing?.equipmentDescription.orEmpty())};var task by remember(existing){mutableStateOf(existing?.taskName.orEmpty())};var plan by remember(existing){mutableStateOf(existing?.servicePlanReference.orEmpty())};var due by remember(existing){mutableStateOf(existing?.dueDateSnapshot.orEmpty())};var assigned by remember(existing){mutableStateOf(existing?.assignedTechnicianIds?.toSet().orEmpty())};var error by remember{mutableStateOf<String?>(null)}
    fun selectSubject(value:WorkSubjectType){subjectType=value;if(value==WorkSubjectType.SITE){equipmentId=null;description="";plan="";due=""}else if(customerType==CustomerType.ONE_TIME){plan="";due=""}}
    AlertDialog(modifier=Modifier.testTag("dispatch-work-item-editor"),onDismissRequest=onDismiss,title={Text(if(existing==null)"Add work item" else "Edit work item")},text={LazyColumn(Modifier.heightIn(max=500.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Text("Subject");ServiceLoopChoiceGroup(listOf(WorkSubjectType.SITE to "Site",WorkSubjectType.EQUIPMENT to "Equipment"),subjectType,{selectSubject(it)},testTagPrefix="dispatch-subject")}
        if(subjectType==WorkSubjectType.EQUIPMENT){item{Text("Equipment at selected Site")};item{ServiceLoopSelectionOption(equipmentId==null,{equipmentId=null;description=""},"No specific equipment yet",Modifier.testTag("dispatch-equipment-none"))};items(equipment,key={it.id}){value->ServiceLoopSelectionOption(equipmentId==value.id,{equipmentId=value.id;description=""},"${value.reference} · ${value.name}",Modifier.testTag("dispatch-equipment-${value.id}"))};if(equipmentId==null)item{OutlinedTextField(description,{description=it},label={Text("Equipment description · Optional")},modifier=Modifier.fillMaxWidth().testTag("dispatch-equipment-description"))}}
        item{OutlinedTextField(task,{task=it},label={Text("Task name")},modifier=Modifier.fillMaxWidth().testTag("dispatch-work-task"));if(subjectType==WorkSubjectType.EQUIPMENT&&equipmentId!=null&&customerType==CustomerType.STANDARD){OutlinedTextField(plan,{plan=it},label={Text("Local Service Plan reference · Optional")},modifier=Modifier.fillMaxWidth().testTag("dispatch-work-plan"));OutlinedTextField(due,{due=it},label={Text("Due snapshot · Optional YYYY-MM-DD")},modifier=Modifier.fillMaxWidth().testTag("dispatch-work-due"))};Text("Assignment");Text("No selected Technician means Everyone on the selected Teams.",style=MaterialTheme.typography.bodySmall);participants.forEach{tech->Row(Modifier.fillMaxWidth().clickable{assigned=if(tech.technicianId in assigned)assigned-tech.technicianId else assigned+tech.technicianId},verticalAlignment=Alignment.CenterVertically){Checkbox(tech.technicianId in assigned,null);Text(tech.displayName)}};error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}}},dismissButton={TextButton(onDismiss){Text("Cancel")}},confirmButton={Button({when{task.isBlank()->error="Enter a task name.";subjectType==WorkSubjectType.EQUIPMENT&&equipmentId==null&&description.trim().length>500->error="Equipment description must be 500 characters or fewer.";due.isNotBlank()&&runCatching{LocalDate.parse(due)}.isFailure->error="Enter due date as YYYY-MM-DD.";due.isNotBlank()&&plan.isBlank()->error="Due date requires a service plan reference.";else->onSave(DispatchOutboxItemDraft(dispatchItemId=existing?.dispatchItemId?:UUID.randomUUID().toString(),subjectType=subjectType,equipmentId=equipmentId,equipmentDescription=description,taskName=task,servicePlanReference=plan.ifBlank{null},dueDateSnapshot=due.ifBlank{null},assignedTechnicianIds=assigned.toList()))}},Modifier.testTag("dispatch-work-item-save")){Text("Save item")}})
}

private data class DispatchEditorLoad(
    val sites:List<SiteEntity>,val customers:List<CustomerEntity>,val equipment:List<EquipmentEntity>,val teams:List<DispatchTeamDetail>,
    val visit:DispatchOutboxVisitEntity?,val teamIds:List<String>,val items:List<DispatchOutboxItemDraft>,
)

private fun dispatchOutboxSubjectLabel(item:DispatchOutboxItemDraft,equipment:EquipmentEntity?):String=when{
    item.subjectType==WorkSubjectType.SITE->"Site"
    equipment!=null->"${equipment.reference} · ${equipment.name}"
    !item.equipmentDescription.isNullOrBlank()->item.equipmentDescription.trim()
    else->"Equipment not specified"
}

private fun dispatchPackageSubjectLabel(item:DispatchWork,equipment:DispatchEquipment?):String=when{
    item.subjectType==WorkSubjectType.SITE->"Site"
    equipment!=null->"${equipment.reference} · ${equipment.name}"
    !item.equipmentDescription.isNullOrBlank()->item.equipmentDescription.trim()
    else->"Equipment not specified"
}

@Composable internal fun DispatchVisitEditorScreen(padding:PaddingValues,nav:NavHostController,visitId:String?,businessDate:LocalDate,serviceOverride:DispatchPackageService?=null,databaseOverride:ServiceLoopDatabase?=null){
    val context=LocalContext.current;val svc=remember(serviceOverride){serviceOverride?:dispatchService(context)};val db=databaseOverride?:(context.applicationContext as ServiceLoopApplication).container.database;val scope=rememberCoroutineScope()
    var sites by remember{mutableStateOf(emptyList<SiteEntity>())};var customers by remember{mutableStateOf(emptyList<CustomerEntity>())};var equipment by remember{mutableStateOf(emptyList<EquipmentEntity>())};var teams by remember{mutableStateOf(emptyList<DispatchTeamDetail>())};var loadedVisit by remember{mutableStateOf<DispatchOutboxVisitEntity?>(null)}
    var manager by rememberSaveable(visitId){mutableStateOf("")};var date by rememberSaveable(visitId){mutableStateOf(businessDate.plusDays(1).toString())};var time by rememberSaveable(visitId){mutableStateOf("09:00")};var zone by rememberSaveable(visitId){mutableStateOf(ZoneId.systemDefault().id)};var instructions by rememberSaveable(visitId){mutableStateOf("")};var siteId by rememberSaveable(visitId){mutableStateOf("")};var dispatchMode by rememberSaveable(visitId){mutableStateOf("EXISTING")};var oneTimeCustomerName by rememberSaveable(visitId){mutableStateOf("")};var oneTimePhone by rememberSaveable(visitId){mutableStateOf("")};var oneTimeEmail by rememberSaveable(visitId){mutableStateOf("")};var oneTimeLocationLabel by rememberSaveable(visitId){mutableStateOf("")};var oneTimeAddress by rememberSaveable(visitId){mutableStateOf("")};var selectedTeams by remember{mutableStateOf(setOf<String>())};var stagedItems by remember{mutableStateOf(emptyList<DispatchOutboxItemDraft>())}
    var initialized by remember{mutableStateOf(false)};var initialDraft by remember{mutableStateOf<DispatchOutboxEditorDraft?>(null)};var showSites by remember{mutableStateOf(false)};var showTeams by remember{mutableStateOf(false)};var showWorkItem by remember{mutableStateOf(false)};var editingItem by remember{mutableStateOf<DispatchOutboxItemDraft?>(null)};var pendingSite by remember{mutableStateOf<String?>(null)};var showCancelDialog by remember{mutableStateOf(false)};var cancelReason by rememberSaveable(visitId){mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)}
    var editDate by remember{mutableStateOf(false)};var editTime by remember{mutableStateOf(false)};var editZone by remember{mutableStateOf(false)};var optionalExpanded by rememberSaveable(visitId){mutableStateOf(false)}
    fun currentDraft()=DispatchOutboxEditorDraft(visitId,loadedVisit?.modifiedAtEpochMillis,manager,if(dispatchMode=="ONE_TIME"&&visitId==null)"" else siteId,date,time.ifBlank{null},zone,instructions,selectedTeams.toList().sorted(),stagedItems,if(dispatchMode=="ONE_TIME"&&visitId==null)OneTimeVisitInput(oneTimeCustomerName,oneTimePhone,oneTimeEmail,oneTimeLocationLabel,oneTimeAddress) else null)
    fun load(){scope.launch{val data=withContext(Dispatchers.IO){val visit=visitId?.let{id->svc.outboxVisits().find{it.dispatchVisitId==id}};DispatchEditorLoad(db.serviceLoopDao().allSites(),db.serviceLoopDao().allCustomers(),db.serviceLoopDao().allEquipment(),svc.teams(),visit,visitId?.let{svc.outboxTeamIds(it)}.orEmpty(),visitId?.let{svc.outboxItems(it)}.orEmpty().map{(item,assigned)->DispatchOutboxItemDraft(dispatchItemId=item.dispatchItemId,subjectType=WorkSubjectType.fromCode(item.subjectType),equipmentId=item.equipmentId,equipmentDescription=item.equipmentDescription.orEmpty(),taskName=item.taskName,servicePlanReference=item.servicePlanReference,dueDateSnapshot=item.dueDateSnapshot,assignedTechnicianIds=assigned)})};sites=data.sites;customers=data.customers;equipment=data.equipment;teams=data.teams;loadedVisit=data.visit;selectedTeams=data.teamIds.toSet();stagedItems=data.items;data.visit?.let{manager=it.managerReference.orEmpty();date=it.serviceDate;time=it.appointmentLocalTime.orEmpty();zone=it.appointmentZoneId;instructions=it.instructions.orEmpty();siteId=it.siteId};initialized=true;initialDraft=currentDraft()}}
    LaunchedEffect(visitId){load()}
    val readOnly=loadedVisit?.outboxStatus in setOf(DispatchOutboxStatus.CONCLUDED,DispatchOutboxStatus.CANCELED);val changed=initialized&&initialDraft!=currentDraft();UnsavedChangesGuard(changed,nav)
    val siteMap=sites.associateBy{it.id};val customerMap=customers.associateBy{it.id};val chosenSite=siteMap[siteId];val oneTimeMode=visitId==null&&dispatchMode=="ONE_TIME";val selectedCustomerType=chosenSite?.let{customerMap[it.customerId]?.let{customer->CustomerType.fromCode(customer.customerType)}}?:if(oneTimeMode)CustomerType.ONE_TIME else CustomerType.STANDARD;val participants=teams.filter{it.team.id in selectedTeams}.flatMap{it.members.map{member->member.first}}.distinctBy{it.technicianId};val equipmentAtSite=equipment.filter{it.siteId==siteId&&it.state=="ACTIVE"}
    if(showSites&&!oneTimeMode)DispatchSitePickerDialog(sites,customers,{showSites=false}){newSite->if(newSite!=siteId&&stagedItems.isNotEmpty())pendingSite=newSite else siteId=newSite;showSites=false}
    pendingSite?.let{newSite->AlertDialog(onDismissRequest={pendingSite=null},title={Text("Change Site and remove work items?")},text={Text("Changing Site will remove all staged work items because their subject belongs to the current Site.")},dismissButton={TextButton({pendingSite=null}){Text("Keep current Site")}},confirmButton={Button({siteId=newSite;stagedItems=emptyList();pendingSite=null}){Text("Remove items and change Site")}})}
    if(showTeams)DispatchTeamPickerDialog(teams,selectedTeams,{showTeams=false}){newTeams->val allowed=teams.filter{it.team.id in newTeams}.flatMap{it.members}.map{it.first.technicianId}.toSet();if(stagedItems.any{item->item.assignedTechnicianIds.any{it !in allowed}}){error="Update item assignments before removing those Team members.";showTeams=false} else{selectedTeams=newTeams;showTeams=false}}
    if(showWorkItem)DispatchWorkItemDialog(editingItem,equipmentAtSite,selectedCustomerType,participants,{showWorkItem=false;editingItem=null}){item->stagedItems=if(editingItem==null)stagedItems+item else stagedItems.map{if(it.dispatchItemId==item.dispatchItemId)item else it};showWorkItem=false;editingItem=null}
    if(editDate)DispatchValueDialog("Service date","YYYY-MM-DD",date,onDismiss={editDate=false}){date=it;editDate=false}
    if(editTime)DispatchValueDialog("Appointment time","HH:mm",time,optional=true,onDismiss={editTime=false}){time=it;editTime=false}
    if(editZone)DispatchValueDialog("Time zone","IANA ZoneId",zone,onDismiss={editZone=false}){zone=it;editZone=false}
    if(showCancelDialog&&loadedVisit!=null)DispatchCancellationReasonDialog("Cancel Visit",cancelReason,loadedVisit!!.outboxStatus==DispatchOutboxStatus.DISPATCHED,"Cancel visit",onDismiss={showCancelDialog=false;cancelReason=""}){reason->
        cancelReason=reason
        scope.launch{runCatching{withContext(Dispatchers.IO){svc.cancelOutboxVisits(listOf(visitId!!),reason)}}.onSuccess{showCancelDialog=false;cancelReason="";load()}.onFailure{if(it is CancellationException)throw it else error=it.message};}
    }
    fun saveDraft(){error=null;when{!oneTimeMode&&siteId.isBlank()->error="Choose a Site.";oneTimeMode&&oneTimeCustomerName.trim().isBlank()->error="Customer name is required.";selectedTeams.isEmpty()->error="Choose at least one Team.";participants.isEmpty()->error="Selected Teams need at least one Technician.";runCatching{LocalDate.parse(date)}.isFailure->error="Enter service date as YYYY-MM-DD.";time.isNotBlank()&&runCatching{LocalTime.parse(time)}.isFailure->error="Enter appointment time as HH:mm.";runCatching{ZoneId.of(zone)}.isFailure->error="Enter a valid ZoneId.";else->{busy=true;scope.launch{val result=runCatching{withContext(Dispatchers.IO){svc.saveOutboxVisit(currentDraft())}};withContext(Dispatchers.Main.immediate){result.onSuccess{initialDraft=currentDraft();nav.popBackStack()}.onFailure{if(it is CancellationException)throw it else error=it.message?:"Could not save this Dispatch Visit."};busy=false}}}}}
    Column(Modifier.padding(padding).fillMaxSize().testTag("dispatch-editor-root")){
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag(if(visitId==null)"dispatch-new-visit" else "dispatch-visit-editor"),contentPadding=PaddingValues(ServiceLoopUiTokens.Layout.pageInsetCompact,ServiceLoopUiTokens.Space.lg,ServiceLoopUiTokens.Layout.pageInsetCompact,ServiceLoopUiTokens.Space.section),verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Layout.bodyGap)){
            item{loadedVisit?.let{visit->ServiceLoopStatusBadge(visit.outboxStatus.name);if(visit.outboxStatus==DispatchOutboxStatus.DISPATCHED)ServiceLoopNotice("Dispatched","Material changes create a later version only when this visit is exported again.",ServiceLoopNoticeKind.Info);if(visit.outboxStatus==DispatchOutboxStatus.CANCELED){val message=when{visit.cancellationExportPending->"Cancellation export pending until a newer package is created.";visit.lastExportedGeneration!=null->"Cancellation exported · Version ${visit.lastExportedGeneration}.";else->"This Draft is stored in cancellation history and is not exported."};ServiceLoopNotice("Canceled",message,if(visit.cancellationExportPending)ServiceLoopNoticeKind.Warning else ServiceLoopNoticeKind.Info)};if(readOnly)ServiceLoopNotice("Read-only","Canceled and Concluded visits cannot be edited.",ServiceLoopNoticeKind.Info)}}
            item{Text("WHERE & WHEN",style=ServiceLoopUiTokens.DayHeadingStyle,color=LocalServiceLoopTokens.current.textSecondary)}
            if(visitId==null)item{Text("Dispatch type");ServiceLoopChoiceGroup(listOf("EXISTING" to "Existing","ONE_TIME" to "One-time"),dispatchMode,{dispatchMode=it},testTagPrefix="dispatch-mode")}
            if(oneTimeMode)item{Column(verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)){Text("One-time customer");OutlinedTextField(oneTimeCustomerName,{oneTimeCustomerName=it},label={Text("Customer name · Required")},modifier=Modifier.fillMaxWidth().testTag("dispatch-one-time-customer-name"));OutlinedTextField(oneTimePhone,{oneTimePhone=it},label={Text("Phone")},modifier=Modifier.fillMaxWidth().testTag("dispatch-one-time-phone"));OutlinedTextField(oneTimeEmail,{oneTimeEmail=it},label={Text("Email")},modifier=Modifier.fillMaxWidth().testTag("dispatch-one-time-email"));OutlinedTextField(oneTimeLocationLabel,{oneTimeLocationLabel=it},label={Text("Location label")},modifier=Modifier.fillMaxWidth().testTag("dispatch-one-time-location-label"));OutlinedTextField(oneTimeAddress,{oneTimeAddress=it},label={Text("Service address")},modifier=Modifier.fillMaxWidth().testTag("dispatch-one-time-address"));Text("A local ONE_TIME customer and default Site are created only when this Visit is saved.",color=LocalServiceLoopTokens.current.textSecondary)}}
            if(!oneTimeMode)item{ServiceLoopPickerSummary("Customer & site",chosenSite?.let{customerMap[it.customerId]?.name?:it.name}?:"Choose customer & site",chosenSite?.let{listOfNotNull(it.name,it.address).joinToString(" — ")},required=true,enabled=!readOnly,modifier=Modifier.testTag("dispatch-choose-site")){showSites=true}}
            item{ServiceLoopResponsivePair(first={ServiceLoopPickerSummary("Service date",date,required=true,enabled=!readOnly){editDate=true}},second={ServiceLoopPickerSummary("Appointment time",time.ifBlank{"Date only"},"Optional",enabled=!readOnly){editTime=true}})}
            item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("$zone time",Modifier.weight(1f),color=LocalServiceLoopTokens.current.textSecondary);ServiceLoopTextAction("Change",{editZone=true},enabled=!readOnly,modifier=Modifier.testTag("dispatch-change-zone"))}}
            item{ServiceLoopPickerSummary("Teams",if(selectedTeams.isEmpty())"Choose Teams" else teams.filter{it.team.id in selectedTeams}.joinToString{it.team.name},if(selectedTeams.isEmpty())null else "${participants.size} ${if(participants.size==1)"person" else "people"}",required=true,enabled=!readOnly,modifier=Modifier.testTag("dispatch-choose-teams")){showTeams=true}}
            item{ServiceLoopSectionHeading("Work items (${stagedItems.size})",if(readOnly)null else {{ServiceLoopTextAction("+ Add work",{if(!oneTimeMode&&siteId.isBlank())error="Choose a Site before adding work." else{editingItem=null;showWorkItem=true}},Modifier.testTag("dispatch-add-item"))}});if(stagedItems.isEmpty())Text("No work items yet. This draft will not be export-ready.",color=LocalServiceLoopTokens.current.textSecondary)}
            items(stagedItems,key={it.dispatchItemId}){item->val eq=equipment.find{it.id==item.equipmentId};val names=item.assignedTechnicianIds.map{id->participants.find{it.technicianId==id}?.displayName?:"Technician ${id.take(8)}"};ServiceLoopSurfaceCard(Modifier.testTag("dispatch-item-${item.dispatchItemId}")){Text(item.taskName,style=MaterialTheme.typography.titleMedium);Text(dispatchOutboxSubjectLabel(item,eq),color=LocalServiceLoopTokens.current.textSecondary);Text(if(names.isEmpty())"Everyone on selected teams" else names.joinToString(" · "),color=LocalServiceLoopTokens.current.textSecondary);if(!readOnly)Row{ServiceLoopTextAction("Edit",{editingItem=item;showWorkItem=true});ServiceLoopTextAction("Remove",{stagedItems=stagedItems.filterNot{it.dispatchItemId==item.dispatchItemId}})}}}
            item{Column(Modifier.fillMaxWidth().clickable(enabled=!readOnly){optionalExpanded=!optionalExpanded}.padding(vertical=ServiceLoopUiTokens.Space.md).testTag("dispatch-optional-details"),verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)){Row(verticalAlignment=Alignment.CenterVertically){Text("Reference & instructions",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f));ServiceLoopIcon(if(optionalExpanded)ServiceLoopIcons.Back else ServiceLoopIcons.Disclosure,null,Modifier.size(ServiceLoopUiTokens.Size.icon),LocalServiceLoopTokens.current.icon)};Text(if(manager.isBlank()&&instructions.isBlank())"Optional" else "Details entered",color=LocalServiceLoopTokens.current.textSecondary)};if(optionalExpanded){Spacer(Modifier.height(ServiceLoopUiTokens.Space.sm));ServiceLoopTextField(manager,{manager=it},"Reference",enabled=!readOnly,modifier=Modifier.testTag("dispatch-manager-reference"));Spacer(Modifier.height(ServiceLoopUiTokens.Space.md));ServiceLoopLongTextEditor(instructions,{instructions=it},"Instructions",private=false,enabled=!readOnly)}}
            item{error?.let{ServiceLoopNotice("Not saved — action needed",it,ServiceLoopNoticeKind.Error)};if(loadedVisit?.outboxStatus==DispatchOutboxStatus.DISPATCHED&&changed)Text("Save or discard your edits before marking this visit concluded.",color=LocalServiceLoopTokens.current.textSecondary)}
        }
        ServiceLoopPinnedBar{
            ServiceLoopResponsivePair(
                first={Text(if(readOnly)"Read-only" else if(loadedVisit?.outboxStatus==DispatchOutboxStatus.DISPATCHED)"Not sent again until exported" else "Not sent to technicians",color=LocalServiceLoopTokens.current.textSecondary)},
                second={Text("${stagedItems.size} work items",color=LocalServiceLoopTokens.current.textSecondary)},
            )
            ServiceLoopActionStack {
                if(!readOnly)ServiceLoopPrimaryButton(if(visitId==null)"Save draft" else "Save changes",{saveDraft()},Modifier.fillMaxWidth().testTag("dispatch-save-visit"),enabled=!busy,busy=busy)
                if(loadedVisit?.outboxStatus in setOf(DispatchOutboxStatus.DRAFT,DispatchOutboxStatus.DISPATCHED))ServiceLoopSecondaryButton("Cancel visit",{cancelReason="";showCancelDialog=true},Modifier.fillMaxWidth().testTag("dispatch-cancel-visit"),enabled=!busy)
                if(loadedVisit?.outboxStatus==DispatchOutboxStatus.DISPATCHED)ServiceLoopSecondaryButton("Mark concluded",{scope.launch{runCatching{withContext(Dispatchers.IO){svc.concludeOutboxVisits(listOf(visitId!!))}}.onSuccess{initialDraft=currentDraft();nav.popBackStack()}.onFailure{if(it is CancellationException)throw it else error=it.message}}},Modifier.fillMaxWidth().testTag("dispatch-conclude-visit"),enabled=!changed&&!busy)
                if(loadedVisit?.outboxStatus==DispatchOutboxStatus.CONCLUDED)ServiceLoopPrimaryButton("Reopen",{scope.launch{runCatching{withContext(Dispatchers.IO){svc.reopenOutboxVisits(listOf(visitId!!))}}.onSuccess{load()}.onFailure{if(it is CancellationException)throw it else error=it.message}}},Modifier.fillMaxWidth().testTag("dispatch-reopen-visit"))
            }
        }
    }
}

@Composable internal fun DispatchExportReviewScreen(padding:PaddingValues,nav:NavHostController,ids:List<String>,serviceOverride:DispatchPackageService?=null){
    val context=LocalContext.current;val svc=remember(serviceOverride){serviceOverride?:dispatchService(context)};val scope=rememberCoroutineScope();var sender by rememberSaveable{mutableStateOf("")};var prepared by remember{mutableStateOf<DispatchExportPreparation?>(null)};var currentGenerations by remember{mutableStateOf(emptyMap<String,Int?>())};var error by remember{mutableStateOf<String?>(null)};var refresh by remember{mutableStateOf(0)};var busy by remember{mutableStateOf(false)}
    LaunchedEffect(ids,sender,refresh){prepared=null;if(sender.isNotBlank())runCatching{withContext(Dispatchers.IO){svc.prepareExport(ids,sender) to svc.outboxVisits().filter{it.dispatchVisitId in ids}.associate{it.dispatchVisitId to it.lastExportedGeneration}}}.onSuccess{prepared=it.first;currentGenerations=it.second}.onFailure{if(it is CancellationException)throw it else error=it.message}}
    val visits=prepared?.packageValue?.visits.orEmpty();val itemCount=visits.sumOf{it.work.size};val first=visits.minOfOrNull{it.serviceDate};val last=visits.maxOfOrNull{it.serviceDate}
    fun createAndShare(){val review=prepared;if(review==null){error=if(sender.isBlank())"Enter a sender name." else "Wait for the review to finish preparing."}else{busy=true;scope.launch{runCatching{withContext(Dispatchers.IO){svc.createExportFile(review,context.cacheDir)}}.onSuccess{artifact->shareExistingFile(context,artifact.file,WORK_PACKAGE_MIME,"Share ServiceLoop work package","ServiceLoop work package · ${artifact.packageValue.visits.size} visit(s)","ServiceLoop work package\nGenerated with ServiceLoop");nav.popBackStack()}.onFailure{failure->if(failure is CancellationException)throw failure else{error=if(failure.message?.contains("changed while") == true)"A selected visit changed while review was open. The review has been refreshed; confirm it again." else failure.message;refresh++}};busy=false}}}
    Column(Modifier.padding(padding).fillMaxSize().testTag("dispatch-export-review")){
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(ServiceLoopUiTokens.Layout.pageInsetCompact,ServiceLoopUiTokens.Space.lg,ServiceLoopUiTokens.Layout.pageInsetCompact,ServiceLoopUiTokens.Space.section),verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.lg)){
            item{ServiceLoopSurfaceCard{Text("ONE WORK PACKAGE",style=ServiceLoopUiTokens.DayHeadingStyle,color=LocalServiceLoopTokens.current.textSecondary);Text("${if(visits.isEmpty())ids.size else visits.size} visits",style=ServiceLoopUiTokens.HeroCountStyle);Text("$itemCount work items${if(first!=null)" · ${if(first==last)first else "$first – $last"}" else ""}",color=LocalServiceLoopTokens.current.textSecondary,modifier=Modifier.testTag("dispatch-export-summary"));ServiceLoopNotice("Assignment visibility","Each technician imports only assigned work. Team leads receive their wider overview.",ServiceLoopNoticeKind.Info)}}
            item{ServiceLoopTextField(sender,{sender=it;prepared=null;error=null},"Sender name",required=true,modifier=Modifier.testTag("dispatch-export-sender"));if(sender.isBlank())Text("Sender name is required before file creation.",color=LocalServiceLoopTokens.current.errorInk,style=MaterialTheme.typography.bodyMedium);if(prepared==null&&sender.isNotBlank())ServiceLoopNotice("Preparing review…","No visit status or version has changed.",ServiceLoopNoticeKind.Working)}
            if(visits.isNotEmpty())item{ServiceLoopSectionHeading("Included visits",trailing={Text("${visits.size} selected",color=LocalServiceLoopTokens.current.textSecondary)})}
            items(visits,key={it.dispatchVisitId}){visit->Column(Modifier.fillMaxWidth().padding(vertical=ServiceLoopUiTokens.Space.md),verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)){Text("${visit.appointmentLocalTime?:"Date only"} · ${visit.siteReference}",style=MaterialTheme.typography.titleMedium);Text("${visit.managerReference?:visit.dispatchVisitId.take(8)} · ${visit.work.size} ${if(visit.work.size==1)"work item" else "work items"}",color=LocalServiceLoopTokens.current.textSecondary);visit.work.forEach{work->Text("${dispatchPackageSubjectLabel(work,prepared?.packageValue?.equipment?.find{it.reference==work.equipmentReference})} · ${work.taskName}",color=LocalServiceLoopTokens.current.textSecondary)};if(visit.transportLifecycle=="CANCELED")Text("Canceled · reason: ${visit.cancellationReason}",color=LocalServiceLoopTokens.current.warningInk);val current=currentGenerations[visit.dispatchVisitId];Text(when{current==null->"First export · Version ${visit.generation}";visit.generation==current->"Unchanged · Version ${visit.generation}";else->"Updated · Version ${visit.generation}"},color=LocalServiceLoopTokens.current.textSecondary);HorizontalDivider(color=LocalServiceLoopTokens.current.outlineDecorative)} }
            item{Text("The .slwork file contains readable customer, site and assignment information. Share it only with intended recipients.",color=LocalServiceLoopTokens.current.textSecondary);Spacer(Modifier.height(ServiceLoopUiTokens.Space.sm));Text("Dispatched means a usable file was created — not that it was received.",color=LocalServiceLoopTokens.current.textSecondary);error?.let{Spacer(Modifier.height(ServiceLoopUiTokens.Space.md));ServiceLoopNotice("File not created",it,ServiceLoopNoticeKind.Error)}}
        }
        ServiceLoopPinnedBar{ServiceLoopPrimaryButton("Create & share file",{createAndShare()},Modifier.fillMaxWidth().testTag("dispatch-export-confirm"),enabled=!busy,busy=busy)}
    }
}

@Composable internal fun CoordinatorHomeActions(nav:NavHostController){
    val context=LocalContext.current;val prefs=remember{context.getSharedPreferences(DISPATCH_PREFS,0)};var role by remember{mutableStateOf(context.teamRole())}
    DisposableEffect(prefs){val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key==TEAM_ROLE)role=context.teamRole()};prefs.registerOnSharedPreferenceChangeListener(listener);onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}}
    if(role==TeamRole.COORDINATOR){Row(Modifier.fillMaxWidth().testTag("coordinator-home-actions"),horizontalArrangement=Arrangement.spacedBy(6.dp)){ServiceLoopSecondaryButton("Technicians",{nav.navigate("dispatch/technicians")},Modifier.weight(1f));ServiceLoopSecondaryButton("Teams",{nav.navigate("dispatch/teams")},Modifier.weight(1f));ServiceLoopSecondaryButton("Outbox",{nav.navigate("dispatch/create")},Modifier.weight(1f))}}
    if(role==TeamRole.MEMBER){ServiceLoopSecondaryButton("Import work package",{nav.navigate("dispatch/import")},Modifier.fillMaxWidth().testTag("member-import-work-package"),leadingIcon={ServiceLoopIcon(ServiceLoopIcons.Backup,null,Modifier.size(ServiceLoopUiTokens.Size.icon))})}
}
