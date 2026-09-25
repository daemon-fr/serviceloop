package com.v16studio.v16service.ui

import com.v16studio.v16service.ui.designsystem.V16ServiceCheckbox as Checkbox

import com.v16studio.v16service.ui.designsystem.V16ServiceButtonAdapter as Button
import com.v16studio.v16service.ui.designsystem.V16ServiceOutlinedButtonAdapter as OutlinedButton
import com.v16studio.v16service.ui.designsystem.V16ServiceTextButtonAdapter as TextButton
import com.v16studio.v16service.ui.designsystem.V16ServiceIconButtonAdapter as IconButton
import com.v16studio.v16service.ui.designsystem.V16ServiceTextFieldAdapter as OutlinedTextField
import com.v16studio.v16service.ui.designsystem.V16ServiceCardAdapter as Card
import com.v16studio.v16service.ui.designsystem.V16ServiceElevatedCardAdapter as ElevatedCard
import com.v16studio.v16service.ui.designsystem.V16ServiceChoiceGroup
import com.v16studio.v16service.ui.designsystem.V16ServiceSelectionOption

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
import com.v16studio.v16service.V16ServiceApplication
import com.v16studio.v16service.data.*
import com.v16studio.v16service.domain.CustomerType
import com.v16studio.v16service.domain.OperationalWorkClassifier
import com.v16studio.v16service.domain.WorkSubjectType
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
import com.v16studio.v16service.ui.designsystem.*
import com.v16studio.v16service.ui.icons.V16ServiceIcon
import com.v16studio.v16service.ui.icons.V16ServiceIcons

internal fun dispatchService(context:Context)=DispatchPackageService((context.applicationContext as V16ServiceApplication).container.database,context.filesDir)

@Composable private fun DispatchCancellationReasonDialog(title:String,reason:String,dispatched:Boolean,confirmLabel:String,onDismiss:()->Unit,onApply:(String)->Unit){
    var staged by remember(reason){mutableStateOf(reason)}
    AlertDialog(
        modifier=Modifier.testTag("dispatch-cancel-dialog"),
        onDismissRequest=onDismiss,
        title={Text(title)},
        text={Column(verticalArrangement=Arrangement.spacedBy(V16ServiceUiTokens.Space.sm)){
            V16ServiceTextField(staged,{staged=it},"Cancellation reason",singleLine=true,modifier=Modifier.testTag("dispatch-cancel-reason"))
            Text(if(dispatched)"A newer work package must be created to communicate this cancellation to technicians." else "This Draft stays in local cancellation history and is not exported.",color=LocalV16ServiceTokens.current.textSecondary)
        }},
        dismissButton={V16ServiceTextAction("Cancel",onDismiss)},
        confirmButton={V16ServicePrimaryButton(confirmLabel,{onApply(staged.trim())},Modifier.testTag("dispatch-cancel-confirm"),enabled=staged.isNotBlank())},
    )
}

@Composable internal fun DispatchOutboxScreen(padding:PaddingValues,nav:NavHostController,businessDate:LocalDate,serviceOverride:DispatchPackageService?=null,databaseOverride:V16ServiceDatabase?=null,canConcludeDelegatedWork:Boolean=true){
    val context=LocalContext.current;val svc=remember(serviceOverride){serviceOverride?:dispatchService(context)};val db=databaseOverride?:(context.applicationContext as V16ServiceApplication).container.database;val scope=rememberCoroutineScope();val lifecycleOwner=LocalLifecycleOwner.current
    var outbox by remember{mutableStateOf(emptyList<DispatchOutboxVisitEntity>())};var sites by remember{mutableStateOf(emptyList<SiteEntity>())};var customers by remember{mutableStateOf(emptyList<CustomerEntity>())};var itemCounts by remember{mutableStateOf(emptyMap<String,Int>())}
    var canonicalVisits by remember { mutableStateOf(emptyMap<String, WorkingVisitEntity>()) }
    var receivedCounts by remember { mutableStateOf(emptyMap<String, Int>()) }
    var search by rememberSaveable{mutableStateOf("")};var statusFilter by rememberSaveable{mutableStateOf("Active")};var dateFilter by rememberSaveable{mutableStateOf("All dates")};var customStart by rememberSaveable{mutableStateOf(businessDate.toString())};var customEnd by rememberSaveable{mutableStateOf(businessDate.plusDays(7).toString())};var checked by remember{mutableStateOf(setOf<String>())};var pendingStatusAction by remember{mutableStateOf<String?>(null)};var cancelReason by rememberSaveable{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)}
    fun reload(){scope.launch{withContext(Dispatchers.IO){outbox=svc.outboxVisits();sites=db.v16ServiceDao().allSites();customers=db.v16ServiceDao().allCustomers();itemCounts=outbox.associate{it.dispatchVisitId to svc.outboxItems(it.dispatchVisitId).size};receivedCounts=outbox.associate{it.dispatchVisitId to db.v16ServiceDao().appliedWorkResultReceipts(it.dispatchVisitId).map { receipt -> receipt.dispatchItemId }.distinct().size};canonicalVisits=outbox.mapNotNull{row->row.localVisitId?.let{db.v16ServiceDao().visit(it)}?.let{row.dispatchVisitId to it}}.toMap()}}}
    LaunchedEffect(Unit){reload()};DisposableEffect(lifecycleOwner){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)reload()};lifecycleOwner.lifecycle.addObserver(observer);onDispose{lifecycleOwner.lifecycle.removeObserver(observer)}}
    val today=businessDate;val monday=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));val customRange=runCatching{LocalDate.parse(customStart)..LocalDate.parse(customEnd)}.getOrNull();val siteMap=sites.associateBy{it.id};val customerMap=customers.associateBy{it.id};val needle=search.trim().lowercase()
    val visible=outbox.filter{visit->val statusMatch=when(statusFilter){"Active"->visit.outboxStatus in setOf(DispatchOutboxStatus.DRAFT,DispatchOutboxStatus.DISPATCHED);"Draft"->visit.outboxStatus==DispatchOutboxStatus.DRAFT;"Dispatched"->visit.outboxStatus==DispatchOutboxStatus.DISPATCHED;"Canceled"->visit.outboxStatus==DispatchOutboxStatus.CANCELED;"Concluded"->visit.outboxStatus==DispatchOutboxStatus.CONCLUDED;else->true};val canonical=canonicalVisits[visit.dispatchVisitId];val date=LocalDate.parse(canonical?.actualServiceDate?:visit.serviceDate);val dateMatch=when(dateFilter){"Today"->date==today;"Tomorrow"->date==today.plusDays(1);"This week"->date in monday..monday.plusDays(6);"Next 7 days"->date in today..today.plusDays(6);"Custom range"->customRange?.let{date in it}==true;else->true};val site=siteMap[canonical?.siteId?:visit.siteId];val customer=site?.let{customerMap[it.customerId]};val searchMatch=needle.isEmpty()||listOf(visit.managerReference,site?.reference,site?.name,customer?.reference,customer?.name).any{it?.lowercase()?.contains(needle)==true};statusMatch&&dateMatch&&searchMatch}.sortedWith(compareBy({canonicalVisits[it.dispatchVisitId]?.actualServiceDate?:it.serviceDate},{it.appointmentLocalTime.orEmpty()},{it.createdAtEpochMillis},{it.dispatchVisitId}))
    LaunchedEffect(visible.map{it.dispatchVisitId}){checked=checked.intersect(visible.map{it.dispatchVisitId}.toSet())};val selectedRows=visible.filter{it.dispatchVisitId in checked};val listState=rememberLazyListState();val actionState=rememberWorkNewVisitActionState(listState,DISPATCH_NEW_VISIT_SLOT_KEY);val listBottomPadding=if(selectedRows.isEmpty())workNewVisitListBottomPadding(actionState)else V16ServiceUiTokens.Space.sm
    when(pendingStatusAction){
        "cancel" -> DispatchCancellationReasonDialog("Cancel selected Visits",cancelReason,selectedRows.any{it.outboxStatus==DispatchOutboxStatus.DISPATCHED},"Apply",onDismiss={pendingStatusAction=null;cancelReason=""}){reason->
            val ids=selectedRows.map{it.dispatchVisitId}
            scope.launch{runCatching{withContext(Dispatchers.IO){svc.cancelOutboxVisits(ids,reason)}}.onSuccess{checked=emptySet();pendingStatusAction=null;cancelReason="";reload()}.onFailure{if(it is CancellationException)throw it else error=it.message;pendingStatusAction=null;cancelReason=""}}
        }
        "conclude", "reopen" -> if(canConcludeDelegatedWork) AlertDialog(modifier=Modifier.testTag("dispatch-status-confirm"),onDismissRequest={pendingStatusAction=null},title={Text(if(pendingStatusAction=="conclude")"Mark ${selectedRows.size} dispatched Visits as concluded?" else "Reopen ${selectedRows.size} concluded Visits?")},text={Text(if(pendingStatusAction=="conclude")"This closes them only in this coordinator Outbox. Technician devices are not updated." else "Reopened Visits return to Dispatched. Export history and generation are preserved.")},dismissButton={V16ServiceTextAction("Cancel",{pendingStatusAction=null})},confirmButton={V16ServicePrimaryButton(if(pendingStatusAction=="conclude")"Mark concluded" else "Reopen",{val ids=selectedRows.map{it.dispatchVisitId};scope.launch{runCatching{withContext(Dispatchers.IO){if(pendingStatusAction=="conclude")svc.concludeOutboxVisits(ids)else svc.reopenOutboxVisits(ids)}}.onSuccess{checked=emptySet();pendingStatusAction=null;reload()}.onFailure{if(it is CancellationException)throw it else error=it.message;pendingStatusAction=null}}},Modifier.testTag("dispatch-status-confirm-action"))}) else pendingStatusAction=null
        null -> Unit
        else -> Unit
    }
    var moreOpen by remember { mutableStateOf(false) }
    Column(Modifier.padding(padding).fillMaxSize().testTag("dispatch-outbox")) {
        Column(Modifier.fillMaxWidth().padding(V16ServiceUiTokens.Layout.pageInsetCompact),verticalArrangement=Arrangement.spacedBy(V16ServiceUiTokens.Space.sm)) {
            V16ServiceTextField(search,{search=it},"Search visits",singleLine=true,modifier=Modifier.testTag("dispatch-search"))
            V16ServiceFilterSelectorRow(
                first={V16ServiceFilterSelector("Status",statusFilter,listOf("Active","Draft","Dispatched","Canceled","Concluded","All").map{it to it},{statusFilter=it},testTag="dispatch-status-filter")},
                second={V16ServiceFilterSelector("Service date",dateFilter,listOf("Today","Tomorrow","This week","Next 7 days","Custom range","All dates").map{it to it},{dateFilter=it},testTag="dispatch-date-filter")},
            )
            if(dateFilter=="Custom range")V16ServiceResponsivePair(
                first={V16ServiceTextField(customStart,{customStart=it},"From",singleLine=true)},
                second={V16ServiceTextField(customEnd,{customEnd=it},"Through",singleLine=true)},
            )
            Text("Tick visits to export or change their status together.",color=LocalV16ServiceTokens.current.textSecondary)
            error?.let{V16ServiceNotice("Action needed",it,V16ServiceNoticeKind.Error)}
            if(visible.isNotEmpty()) {
                val allVisibleSelected=visible.all{it.dispatchVisitId in checked}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text("${visible.size} ${if(statusFilter=="Active")"active " else ""}${if(visible.size==1)"visit" else "visits"}",style=MaterialTheme.typography.labelLarge,modifier=Modifier.weight(1f))
                    V16ServiceTextAction(if(allVisibleSelected)"Deselect all shown" else "Select all shown",{checked=if(allVisibleSelected)checked-visible.map{it.dispatchVisitId}.toSet() else visible.map{it.dispatchVisitId}.toSet()},Modifier.testTag("dispatch-select-all"))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(Modifier.fillMaxSize().testTag("dispatch-outbox-list"),state=listState,contentPadding=PaddingValues(V16ServiceUiTokens.Layout.pageInsetCompact,0.dp,V16ServiceUiTokens.Layout.pageInsetCompact,listBottomPadding),verticalArrangement=Arrangement.spacedBy(V16ServiceUiTokens.Space.md)){
            if(outbox.isEmpty())item{V16ServiceNotice("No dispatch visits yet","Create a visit when work is ready to prepare for technicians.",V16ServiceNoticeKind.Info)}
            else if(visible.isEmpty())item{V16ServiceNotice(if(search.isNotBlank())"No matching dispatch visits" else "No visits match these filters","Clear search or change the active filters.",V16ServiceNoticeKind.Info)}
            visible.groupBy{canonicalVisits[it.dispatchVisitId]?.actualServiceDate?:it.serviceDate}.forEach { (date, visits) ->
                item("heading-$date") { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(dispatchDayHeading(date),style=V16ServiceUiTokens.DayHeadingStyle,modifier=Modifier.weight(1f));Text("${visits.size} ${if(visits.size==1)"VISIT" else "VISITS"}",style=V16ServiceUiTokens.DayHeadingStyle,color=LocalV16ServiceTokens.current.textSecondary)} }
                items(visits,key={it.dispatchVisitId}){visit->
                    val canonical=canonicalVisits[visit.dispatchVisitId]
                    val site=siteMap[canonical?.siteId?:visit.siteId];val customer=site?.let{customerMap[it.customerId]};val selected=visit.dispatchVisitId in checked
                    val appointmentInstant = runCatching {
                        canonical?.scheduledAtEpochMillis?.let(java.time.Instant::ofEpochMilli)
                            ?: visit.appointmentLocalTime?.let { LocalDate.parse(visit.serviceDate).atTime(LocalTime.parse(it)).atZone(ZoneId.of(visit.appointmentZoneId)).toInstant() }
                    }.getOrNull()
                    val urgency = if (visit.outboxStatus == DispatchOutboxStatus.DRAFT || visit.outboxStatus == DispatchOutboxStatus.DISPATCHED)
                        OperationalWorkClassifier.classifyBookedVisit(canonical?.actualServiceDate?:visit.serviceDate, appointmentInstant?.toEpochMilli(), businessDate, java.time.Instant.now(), ZoneId.systemDefault(), 14)
                    else null
                    val cancellation = when {
                        visit.outboxStatus != DispatchOutboxStatus.CANCELED -> null
                        visit.cancellationExportPending -> "Cancellation export pending"
                        visit.lastExportedGeneration != null -> "Cancellation exported · Version ${visit.lastExportedGeneration}"
                        else -> null
                    }
                    V16ServiceEntityRecord(
                        title = canonical?.scheduledAtEpochMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.of(canonical.appointmentZoneId?:visit.appointmentZoneId)).toLocalTime().withSecond(0).withNano(0).toString() } ?: if(canonical!=null) "Date only" else visit.appointmentLocalTime ?: "Date only",
                        context = listOfNotNull(
                            (customer?.name ?: "Customer unavailable") + if (customer?.let { CustomerType.fromCode(it.customerType) } == CustomerType.ONE_TIME) " · One-time" else "",
                            listOfNotNull(site?.name, site?.address).joinToString(" · ").ifBlank { "Site unavailable" },
                        ).joinToString("\n"),
                        metadata = listOfNotNull(
                            "${visit.managerReference ?: visit.dispatchVisitId.take(8)} · ${itemCounts[visit.dispatchVisitId] ?: 0} ${if ((itemCounts[visit.dispatchVisitId] ?: 0) == 1) "work item" else "work items"}",
                            visit.lastExportedGeneration?.let { "Version $it" },
                            receivedCounts[visit.dispatchVisitId]?.takeIf { it > 0 }?.let { "$it/${itemCounts[visit.dispatchVisitId] ?: 0} results received" },
                            if ((itemCounts[visit.dispatchVisitId] ?: 0) == 0) "Needs a work item before export" else null,
                            cancellation,
                        ).joinToString(" · "),
                        status = if (visit.outboxStatus == DispatchOutboxStatus.DISPATCHED && (receivedCounts[visit.dispatchVisitId] ?: 0) > 0 && (receivedCounts[visit.dispatchVisitId] ?: 0) < (itemCounts[visit.dispatchVisitId] ?: 0)) "Awaiting results" else visit.outboxStatus.name,
                        modifier = Modifier.testTag("dispatch-outbox-visit-${visit.dispatchVisitId}"),
                        selected = selected,
                        actionDescription = "Open visit ${visit.managerReference ?: visit.dispatchVisitId}",
                        selectionChecked = selected,
                        onSelectionChange = { isChecked -> checked = if (isChecked) checked + visit.dispatchVisitId else checked - visit.dispatchVisitId },
                        selectionTestTag = "dispatch-select-${visit.dispatchVisitId}",
                        operationalState = urgency,
                        onClick = { nav.navigate(canonical?.let { "visit/${it.id}" } ?: "dispatch/visit/${visit.dispatchVisitId}") },
                    )
                }
            }
            item(key=DISPATCH_NEW_VISIT_SLOT_KEY){WorkNewVisitReservedSlot({nav.navigate("dispatch/visit/new")},slotTestTag="dispatch-new-visit-slot",actionTestTag="dispatch-new-visit-bottom")}
        }
        if(selectedRows.isEmpty()) WorkNewVisitFloatingAction(actionState,{nav.navigate("dispatch/visit/new")},respectNavigationBars=false,actionTestTag="dispatch-new-visit-floating")
        }
        if(selectedRows.isNotEmpty())V16ServicePinnedBar(Modifier.testTag("dispatch-batch-actions")){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${selectedRows.size} selected",Modifier.weight(1f).testTag("dispatch-selected-count"),style=MaterialTheme.typography.titleMedium);V16ServiceTextAction("Clear",{checked=emptySet()},Modifier.testTag("dispatch-clear-selection"))}
             V16ServiceAdaptiveActionRow(listOf(
                 { V16ServicePrimaryButton("Export (${selectedRows.size})",{when{selectedRows.size>DispatchPackageCodec.MAX_VISITS->error="A work package can contain at most ${DispatchPackageCodec.MAX_VISITS} Visits. Reduce the selection.";selectedRows.any{it.outboxStatus==DispatchOutboxStatus.CONCLUDED}->error="Concluded Visits must be reopened before export.";selectedRows.any{it.outboxStatus==DispatchOutboxStatus.CANCELED&&it.lastExportedGeneration==null}->error="A canceled Draft has no technician export to send.";selectedRows.any{(itemCounts[it.dispatchVisitId]?:0)==0}->error="Every selected Visit needs at least one work item before export.";else->{nav.currentBackStackEntry?.savedStateHandle?.set("dispatch-export-ids",ArrayList(selectedRows.map{it.dispatchVisitId}));nav.navigate("dispatch-export-review")}}},Modifier.fillMaxWidth().testTag("dispatch-export-selected")) },
                 { if(selectedRows.all{it.outboxStatus==DispatchOutboxStatus.DRAFT||it.outboxStatus==DispatchOutboxStatus.DISPATCHED})V16ServiceSecondaryButton("Cancel visits",{pendingStatusAction="cancel"},Modifier.fillMaxWidth().testTag("dispatch-cancel-selected")) },
                 { if(canConcludeDelegatedWork&&selectedRows.all{it.outboxStatus==DispatchOutboxStatus.DISPATCHED})V16ServiceSecondaryButton("Mark concluded",{pendingStatusAction="conclude"},Modifier.fillMaxWidth().testTag("dispatch-conclude-selected")) else if(canConcludeDelegatedWork&&selectedRows.all{it.outboxStatus==DispatchOutboxStatus.CONCLUDED})V16ServiceSecondaryButton("Reopen",{pendingStatusAction="reopen"},Modifier.fillMaxWidth().testTag("dispatch-reopen-selected")) },
             ))
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
    fun createAndShare(){val review=prepared;if(review==null){error=if(sender.isBlank())"Enter a sender name." else "Wait for the review to finish preparing."}else{busy=true;scope.launch{runCatching{withContext(Dispatchers.IO){svc.createExportFile(review,context.cacheDir)}}.onSuccess{artifact->shareExistingFile(context,artifact.file,V16_SERVICE_SYNC_MIME,"Share V16 Service assignment","V16 Service assignment · ${artifact.packageValue.visits.size} visit(s)","V16 Service assignment\nGenerated with V16 Service");nav.popBackStack()}.onFailure{failure->if(failure is CancellationException)throw failure else{error=if(failure.message?.contains("changed while") == true)"A selected visit changed while review was open. The review has been refreshed; confirm it again." else failure.message;refresh++}};busy=false}}}
    Column(Modifier.padding(padding).fillMaxSize().testTag("dispatch-export-review")){
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(V16ServiceUiTokens.Layout.pageInsetCompact,V16ServiceUiTokens.Space.lg,V16ServiceUiTokens.Layout.pageInsetCompact,V16ServiceUiTokens.Space.section),verticalArrangement=Arrangement.spacedBy(V16ServiceUiTokens.Space.lg)){
            item{V16ServiceSurfaceCard{Text("ONE WORK PACKAGE",style=V16ServiceUiTokens.DayHeadingStyle,color=LocalV16ServiceTokens.current.textSecondary);Text("${if(visits.isEmpty())ids.size else visits.size} visits",style=V16ServiceUiTokens.HeroCountStyle);Text("$itemCount work items${if(first!=null)" · ${if(first==last)first else "$first – $last"}" else ""}",color=LocalV16ServiceTokens.current.textSecondary,modifier=Modifier.testTag("dispatch-export-summary"));V16ServiceNotice("Assignment visibility","Each technician imports only assigned work. Team leads receive their wider overview.",V16ServiceNoticeKind.Info)}}
            item{V16ServiceTextField(sender,{sender=it;prepared=null;error=null},"Sender name",required=true,modifier=Modifier.testTag("dispatch-export-sender"));if(sender.isBlank())Text("Sender name is required before file creation.",color=LocalV16ServiceTokens.current.errorInk,style=MaterialTheme.typography.bodyMedium);if(prepared==null&&sender.isNotBlank())V16ServiceNotice("Preparing review…","No visit status or version has changed.",V16ServiceNoticeKind.Working)}
            if(visits.isNotEmpty())item{V16ServiceSectionHeading("Included visits",trailing={Text("${visits.size} selected",color=LocalV16ServiceTokens.current.textSecondary)})}
            items(visits,key={it.dispatchVisitId}){visit->Column(Modifier.fillMaxWidth().padding(vertical=V16ServiceUiTokens.Space.md),verticalArrangement=Arrangement.spacedBy(V16ServiceUiTokens.Space.xs)){Text("${visit.appointmentLocalTime?:"Date only"} · ${visit.siteReference}",style=MaterialTheme.typography.titleMedium);Text("${visit.managerReference?:visit.dispatchVisitId.take(8)} · ${visit.work.size} ${if(visit.work.size==1)"work item" else "work items"}",color=LocalV16ServiceTokens.current.textSecondary);visit.work.forEach{work->Text("${dispatchPackageSubjectLabel(work,prepared?.packageValue?.equipment?.find{it.reference==work.equipmentReference})} · ${work.taskName}",color=LocalV16ServiceTokens.current.textSecondary)};if(visit.transportLifecycle=="CANCELED")Text("Canceled · reason: ${visit.cancellationReason}",color=LocalV16ServiceTokens.current.warningInk);val current=currentGenerations[visit.dispatchVisitId];Text(when{current==null->"First export · Version ${visit.generation}";visit.generation==current->"Unchanged · Version ${visit.generation}";else->"Updated · Version ${visit.generation}"},color=LocalV16ServiceTokens.current.textSecondary);HorizontalDivider(color=LocalV16ServiceTokens.current.outlineDecorative)} }
            item{Text("The V16 Service file contains customer, site and assignment information. Share it only with intended recipients.",color=LocalV16ServiceTokens.current.textSecondary);Spacer(Modifier.height(V16ServiceUiTokens.Space.sm));Text("Dispatched means a usable file was created — not that it was received.",color=LocalV16ServiceTokens.current.textSecondary);error?.let{Spacer(Modifier.height(V16ServiceUiTokens.Space.md));V16ServiceNotice("File not created",it,V16ServiceNoticeKind.Error)}}
        }
        V16ServicePinnedBar{V16ServicePrimaryButton("Create & share file",{createAndShare()},Modifier.fillMaxWidth().testTag("dispatch-export-confirm"),enabled=!busy,busy=busy)}
    }
}

@Composable internal fun WorkspaceHomeActions(nav:NavHostController){
    val capabilities=LocalWorkspaceCapabilities.current
    Column(Modifier.fillMaxWidth().testTag("workspace-home-actions"),verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(capabilities.canUseCoordinatorTools){
            Text("Coordinator tools", style = MaterialTheme.typography.titleLarge)
            V16ServiceAdaptiveActionRow(listOf(
            { V16ServiceSecondaryButton("Technicians",{nav.navigate("dispatch/technicians")},Modifier.fillMaxWidth()) },
            { V16ServiceSecondaryButton("Teams",{nav.navigate("dispatch/teams")},Modifier.fillMaxWidth()) },
            { V16ServiceSecondaryButton("Outbox",{nav.navigate("dispatch/create")},Modifier.fillMaxWidth()) },
        ),Modifier.testTag("coordinator-home-actions"))
        }
    }
}
