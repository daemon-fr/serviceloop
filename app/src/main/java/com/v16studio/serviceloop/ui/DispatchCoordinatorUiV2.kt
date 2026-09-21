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
import androidx.compose.foundation.lazy.rememberLazyListState
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

internal fun dispatchService(context:Context)=DispatchPackageService((context.applicationContext as ServiceLoopApplication).container.database,context.filesDir)

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

@Composable internal fun DispatchOutboxScreen(padding:PaddingValues,nav:NavHostController,businessDate:LocalDate,serviceOverride:DispatchPackageService?=null,databaseOverride:ServiceLoopDatabase?=null,canConcludeDelegatedWork:Boolean=true){
    val context=LocalContext.current;val svc=remember(serviceOverride){serviceOverride?:dispatchService(context)};val db=databaseOverride?:(context.applicationContext as ServiceLoopApplication).container.database;val scope=rememberCoroutineScope();val lifecycleOwner=LocalLifecycleOwner.current
    var outbox by remember{mutableStateOf(emptyList<DispatchOutboxVisitEntity>())};var sites by remember{mutableStateOf(emptyList<SiteEntity>())};var customers by remember{mutableStateOf(emptyList<CustomerEntity>())};var itemCounts by remember{mutableStateOf(emptyMap<String,Int>())}
    var search by rememberSaveable{mutableStateOf("")};var statusFilter by rememberSaveable{mutableStateOf("Active")};var dateFilter by rememberSaveable{mutableStateOf("All dates")};var customStart by rememberSaveable{mutableStateOf(businessDate.toString())};var customEnd by rememberSaveable{mutableStateOf(businessDate.plusDays(7).toString())};var checked by remember{mutableStateOf(setOf<String>())};var pendingStatusAction by remember{mutableStateOf<String?>(null)};var cancelReason by rememberSaveable{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)}
    fun reload(){scope.launch{withContext(Dispatchers.IO){outbox=svc.outboxVisits();sites=db.serviceLoopDao().allSites();customers=db.serviceLoopDao().allCustomers();itemCounts=outbox.associate{it.dispatchVisitId to svc.outboxItems(it.dispatchVisitId).size}}}}
    LaunchedEffect(Unit){reload()};DisposableEffect(lifecycleOwner){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)reload()};lifecycleOwner.lifecycle.addObserver(observer);onDispose{lifecycleOwner.lifecycle.removeObserver(observer)}}
    val today=businessDate;val monday=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));val customRange=runCatching{LocalDate.parse(customStart)..LocalDate.parse(customEnd)}.getOrNull();val siteMap=sites.associateBy{it.id};val customerMap=customers.associateBy{it.id};val needle=search.trim().lowercase()
    val visible=outbox.filter{visit->val statusMatch=when(statusFilter){"Active"->visit.outboxStatus in setOf(DispatchOutboxStatus.DRAFT,DispatchOutboxStatus.DISPATCHED);"Draft"->visit.outboxStatus==DispatchOutboxStatus.DRAFT;"Dispatched"->visit.outboxStatus==DispatchOutboxStatus.DISPATCHED;"Canceled"->visit.outboxStatus==DispatchOutboxStatus.CANCELED;"Concluded"->visit.outboxStatus==DispatchOutboxStatus.CONCLUDED;else->true};val date=LocalDate.parse(visit.serviceDate);val dateMatch=when(dateFilter){"Today"->date==today;"Tomorrow"->date==today.plusDays(1);"This week"->date in monday..monday.plusDays(6);"Next 7 days"->date in today..today.plusDays(6);"Custom range"->customRange?.let{date in it}==true;else->true};val site=siteMap[visit.siteId];val customer=site?.let{customerMap[it.customerId]};val searchMatch=needle.isEmpty()||listOf(visit.managerReference,site?.reference,site?.name,customer?.reference,customer?.name).any{it?.lowercase()?.contains(needle)==true};statusMatch&&dateMatch&&searchMatch}.sortedWith(compareBy({it.serviceDate},{it.appointmentLocalTime.orEmpty()},{it.createdAtEpochMillis},{it.dispatchVisitId}))
    LaunchedEffect(visible.map{it.dispatchVisitId}){checked=checked.intersect(visible.map{it.dispatchVisitId}.toSet())};val selectedRows=visible.filter{it.dispatchVisitId in checked};val listState=rememberLazyListState();val actionState=rememberWorkNewVisitActionState(listState,DISPATCH_NEW_VISIT_SLOT_KEY);val listBottomPadding=if(selectedRows.isEmpty())workNewVisitListBottomPadding(actionState)else ServiceLoopUiTokens.Space.sm
    when(pendingStatusAction){
        "cancel" -> DispatchCancellationReasonDialog("Cancel selected Visits",cancelReason,selectedRows.any{it.outboxStatus==DispatchOutboxStatus.DISPATCHED},"Apply",onDismiss={pendingStatusAction=null;cancelReason=""}){reason->
            val ids=selectedRows.map{it.dispatchVisitId}
            scope.launch{runCatching{withContext(Dispatchers.IO){svc.cancelOutboxVisits(ids,reason)}}.onSuccess{checked=emptySet();pendingStatusAction=null;cancelReason="";reload()}.onFailure{if(it is CancellationException)throw it else error=it.message;pendingStatusAction=null;cancelReason=""}}
        }
        "conclude", "reopen" -> if(canConcludeDelegatedWork) AlertDialog(modifier=Modifier.testTag("dispatch-status-confirm"),onDismissRequest={pendingStatusAction=null},title={Text(if(pendingStatusAction=="conclude")"Mark ${selectedRows.size} dispatched Visits as concluded?" else "Reopen ${selectedRows.size} concluded Visits?")},text={Text(if(pendingStatusAction=="conclude")"This closes them only in this coordinator Outbox. Technician devices are not updated." else "Reopened Visits return to Dispatched. Export history and generation are preserved.")},dismissButton={ServiceLoopTextAction("Cancel",{pendingStatusAction=null})},confirmButton={ServiceLoopPrimaryButton(if(pendingStatusAction=="conclude")"Mark concluded" else "Reopen",{val ids=selectedRows.map{it.dispatchVisitId};scope.launch{runCatching{withContext(Dispatchers.IO){if(pendingStatusAction=="conclude")svc.concludeOutboxVisits(ids)else svc.reopenOutboxVisits(ids)}}.onSuccess{checked=emptySet();pendingStatusAction=null;reload()}.onFailure{if(it is CancellationException)throw it else error=it.message;pendingStatusAction=null}}},Modifier.testTag("dispatch-status-confirm-action"))}) else pendingStatusAction=null
        null -> Unit
        else -> Unit
    }
    var moreOpen by remember { mutableStateOf(false) }
    Column(Modifier.padding(padding).fillMaxSize().testTag("dispatch-outbox")) {
        Column(Modifier.fillMaxWidth().padding(ServiceLoopUiTokens.Layout.pageInsetCompact),verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm)) {
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
        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(Modifier.fillMaxSize().testTag("dispatch-outbox-list"),state=listState,contentPadding=PaddingValues(ServiceLoopUiTokens.Layout.pageInsetCompact,0.dp,ServiceLoopUiTokens.Layout.pageInsetCompact,listBottomPadding),verticalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Space.md)){
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
            item(key=DISPATCH_NEW_VISIT_SLOT_KEY){WorkNewVisitReservedSlot({nav.navigate("dispatch/visit/new")},slotTestTag="dispatch-new-visit-slot",actionTestTag="dispatch-new-visit-bottom")}
        }
        if(selectedRows.isEmpty()) WorkNewVisitFloatingAction(actionState,{nav.navigate("dispatch/visit/new")},respectNavigationBars=false,actionTestTag="dispatch-new-visit-floating")
        }
        if(selectedRows.isNotEmpty())ServiceLoopPinnedBar(Modifier.testTag("dispatch-batch-actions")){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${selectedRows.size} selected",Modifier.weight(1f).testTag("dispatch-selected-count"),style=MaterialTheme.typography.titleMedium);ServiceLoopTextAction("Clear",{checked=emptySet()},Modifier.testTag("dispatch-clear-selection"))}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(ServiceLoopUiTokens.Layout.buttonGap)){
                 ServiceLoopPrimaryButton("Export (${selectedRows.size})",{when{selectedRows.size>DispatchPackageCodec.MAX_VISITS->error="A work package can contain at most ${DispatchPackageCodec.MAX_VISITS} Visits. Reduce the selection.";selectedRows.any{it.outboxStatus==DispatchOutboxStatus.CONCLUDED}->error="Concluded Visits must be reopened before export.";selectedRows.any{it.outboxStatus==DispatchOutboxStatus.CANCELED&&it.lastExportedGeneration==null}->error="A canceled Draft has no technician export to send.";selectedRows.any{(itemCounts[it.dispatchVisitId]?:0)==0}->error="Every selected Visit needs at least one work item before export.";else->{nav.currentBackStackEntry?.savedStateHandle?.set("dispatch-export-ids",ArrayList(selectedRows.map{it.dispatchVisitId}));nav.navigate("dispatch-export-review")}}},Modifier.weight(1f).testTag("dispatch-export-selected"))
                Box { ServiceLoopIconAction("More selected visit actions",{moreOpen=true},Modifier.testTag("dispatch-more-selection"),content = { ServiceLoopIcon(ServiceLoopIcons.More,null,Modifier.size(ServiceLoopUiTokens.Size.icon),LocalServiceLoopTokens.current.action) });DropdownMenu(moreOpen,{moreOpen=false}){
                    if(selectedRows.all{it.outboxStatus==DispatchOutboxStatus.DRAFT||it.outboxStatus==DispatchOutboxStatus.DISPATCHED})DropdownMenuItem({Text("Cancel")},{moreOpen=false;pendingStatusAction="cancel"},Modifier.testTag("dispatch-cancel-selected"))
                    if(canConcludeDelegatedWork&&selectedRows.all{it.outboxStatus==DispatchOutboxStatus.DISPATCHED})DropdownMenuItem({Text("Mark concluded")},{moreOpen=false;pendingStatusAction="conclude"},Modifier.testTag("dispatch-conclude-selected"))
                    else if(canConcludeDelegatedWork&&selectedRows.all{it.outboxStatus==DispatchOutboxStatus.CONCLUDED})DropdownMenuItem({Text("Reopen")},{moreOpen=false;pendingStatusAction="reopen"},Modifier.testTag("dispatch-reopen-selected"))
                    else DropdownMenuItem({Text("No status action for mixed selection")},{moreOpen=false},enabled=false)
                }}
            }
        }
    }
}

private fun dispatchDayHeading(value:String):String=runCatching{LocalDate.parse(value).format(DateTimeFormatter.ofPattern("EEE d MMM",Locale.ENGLISH)).uppercase(Locale.ENGLISH)}.getOrDefault(value)

private fun dispatchPackageSubjectLabel(item:DispatchWork,equipment:DispatchEquipment?):String=when{
    item.subjectType==WorkSubjectType.SITE->"Site"
    equipment!=null->"${equipment.reference} · ${equipment.name}"
    !item.equipmentDescription.isNullOrBlank()->item.equipmentDescription.trim()
    else->"Equipment not specified"
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

@Composable internal fun WorkspaceHomeActions(nav:NavHostController){
    val capabilities=LocalWorkspaceCapabilities.current
    Column(Modifier.fillMaxWidth().testTag("workspace-home-actions"),verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(capabilities.canReceiveAssignedWork){ServiceLoopSecondaryButton("Import work package",{nav.navigate("dispatch/import")},Modifier.fillMaxWidth().testTag("import-work-package"),leadingIcon={ServiceLoopIcon(ServiceLoopIcons.Backup,null,Modifier.size(ServiceLoopUiTokens.Size.icon))})}
        if(capabilities.canUseCoordinatorTools){ServiceLoopAdaptiveActionRow(listOf(
            { ServiceLoopSecondaryButton("Technicians",{nav.navigate("dispatch/technicians")},Modifier.fillMaxWidth()) },
            { ServiceLoopSecondaryButton("Teams",{nav.navigate("dispatch/teams")},Modifier.fillMaxWidth()) },
            { ServiceLoopSecondaryButton("Outbox",{nav.navigate("dispatch/create")},Modifier.fillMaxWidth()) },
        ),Modifier.testTag("coordinator-home-actions"))}
    }
}
