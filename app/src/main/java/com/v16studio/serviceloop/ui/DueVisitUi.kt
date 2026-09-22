package com.v16studio.serviceloop.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.v16studio.serviceloop.domain.*
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopActionStack
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopAdaptiveActionRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopButtonAdapter as Button
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopCardAdapter as Card
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChoiceGroup
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChoicePair
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopContentTabs
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopEntityRecord
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFilterSelector
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFilterSelectorRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopIconAction
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFieldAction
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopOutlinedButtonAdapter as OutlinedButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopResponsivePair
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSecondaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNavigationButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextButtonAdapter as TextButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopWorkItemRow
import com.v16studio.serviceloop.ui.designsystem.serviceLoopFocusRing
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.LocalDate
import org.json.JSONObject
import kotlinx.coroutines.launch

@Composable
internal fun DueServicesScreen(
    values: List<DueService>,
    padding: PaddingValues,
    state: UiState,
    viewModel: ServiceLoopViewModel,
    nav: NavHostController,
    modifier: Modifier = Modifier,
    initialBucket: DueBucket? = null,
    initialOperationalState: OperationalWorkState? = null,
    contextualFilter: String? = null,
    scope: WorkScope = WorkScope.Global,
    operationalStateFor: (DueService) -> OperationalWorkState? = { due ->
        OperationalWorkClassifier.classifyService(due.dueDate, state.businessDate, state.home?.dueSoonHorizonDays ?: 14)
    },
    onNewVisit: () -> Unit = {},
) {
    val capabilities = LocalWorkspaceCapabilities.current
    val context = LocalContext.current
    val filterPreferences = remember(context) { UiFilterPreferences(context) }
    val contextualEntry = contextualFilter != null || initialBucket != null || initialOperationalState != null
    val remembersFilters = !contextualEntry
    val defaultDateFilter = DueServiceDateFilter.entries.firstOrNull { it.bucket == initialBucket } ?: DueServiceDateFilter.ALL
    var dateFilter by rememberSaveable(remembersFilters, initialBucket) {
        mutableStateOf(if (remembersFilters) filterPreferences.dueDate(defaultDateFilter) else defaultDateFilter)
    }
    var visitFilter by rememberSaveable(remembersFilters) {
        mutableStateOf(if (remembersFilters) filterPreferences.dueVisit(DueServiceVisitFilter.ALL) else DueServiceVisitFilter.ALL)
    }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    if (!state.dueServicesReady) {
        Column(modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.md)) {
                    Text(state.dueServicesError?.let { "Unable to read due services — $it" } ?: "Reading due services")
                    state.dueServicesError?.let { ServiceLoopPrimaryButton("Retry", { viewModel.retryDueServices() }, Modifier.testTag("retry-due-services")) }
                }
            }
            DueServiceSelectionActions(emptyList(), false, state, viewModel, nav)
        }
        return
    }
    val scopedValues = values.filter { scope.includesCustomer(it.customerId) }
    val filtered = if (initialOperationalState != null) scopedValues.filter { due ->
        operationalStateFor(due) == initialOperationalState &&
            when (visitFilter) {
                DueServiceVisitFilter.ALL -> true
                DueServiceVisitFilter.NO_VISIT -> due.claimedVisitId == null
                DueServiceVisitFilter.HAS_VISIT -> due.claimedVisitId != null
            } &&
            (query.isBlank() || listOf(due.planReference, due.planName, due.equipmentName, due.equipmentReference, due.customerName, due.siteName).any { it.contains(query, true) })
    } else filterDueServices(scopedValues, dateFilter, visitFilter, query)
    val selectedRows = scopedValues.filter { it.planId in selected }
    val selectionSite = selectedRows.firstOrNull()?.siteId
    val selectionEnabled = selectedRows.isNotEmpty() && selectedRows.all { it.claimedVisitId == null && it.siteId == selectionSite } && !state.operationInProgress
    val listState = rememberLazyListState()
    val actionState = rememberWorkNewVisitActionState(listState)
    Column(modifier.fillMaxSize().padding(padding)) {
        Box(Modifier.weight(1f)) {
        LazyColumn(Modifier.fillMaxSize().testTag("due-services-list"), state = listState, contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, workNewVisitListBottomPadding(actionState)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                DailyField(query, { query = it }, "Search due services")
                if (initialOperationalState != null) {
                    Text(OperationalWorkClassifier.sectionTitle(OperationalWorkKind.SERVICE, initialOperationalState), style = MaterialTheme.typography.titleMedium)
                } else {
                    ServiceLoopFilterSelectorRow(
                        first = {
                            ServiceLoopFilterSelector("Due date", dateFilter, DueServiceDateFilter.entries.map { it to it.label }, { dateFilter = it; if (remembersFilters) filterPreferences.saveDueDate(it) }, testTag = "due-date-selector")
                        },
                        second = {
                            ServiceLoopFilterSelector("Visit", visitFilter, DueServiceVisitFilter.entries.map { it to it.label }, { visitFilter = it; if (remembersFilters) filterPreferences.saveDueVisit(it) }, testTag = "due-visit-selector")
                        },
                    )
                }
                state.dueServicesError?.let { Text("Due services could not update — showing the last saved database result.", color = MaterialTheme.colorScheme.error) }
            }
            if (filtered.isEmpty()) item { Text("No services match these filters.") }
            items(filtered, key = { it.planId }) { due ->
                val selectable = due.claimedVisitId == null && (selectionSite == null || selectionSite == due.siteId)
                ServiceLoopEntityRecord(
                    title = "${due.planReference} · ${due.planName}",
                    context = "${due.equipmentReference} · ${due.equipmentName}\n${due.customerName} · ${due.siteName}",
                    metadata = listOf("Due ${due.dueDate}", due.bucket.name.lowercase().replace('_', ' '), due.claimedVisitId?.let { "Has visit" }).filterNotNull().joinToString(" · "),
                    selected = due.planId in selected,
                    onClick = { nav.navigate(due.claimedVisitId?.let { "visit/$it" } ?: "plan/${due.planId}") },
                    actionDescription = if (due.claimedVisitId == null) "Open service plan ${due.planReference} ${due.planName}" else "Open existing visit ${due.planReference} ${due.planName}",
                    selectionChecked = if (selectable) due.planId in selected else null,
                    onSelectionChange = if (selectable) { checked -> selected = if (checked) selected + due.planId else selected - due.planId } else null,
                    operationalState = operationalStateFor(due),
                )
            }
            if (capabilities.canCreateLocalWork) item(key = WORK_NEW_VISIT_SLOT_KEY) { WorkNewVisitReservedSlot(onNewVisit) }
        }
        if (capabilities.canCreateLocalWork) WorkNewVisitFloatingAction(actionState, onNewVisit, WorkNewVisitDueBottomInset, respectNavigationBars = false)
        }
        if (capabilities.canCreateLocalWork) DueServiceSelectionActions(selected, selectionEnabled, state, viewModel, nav)
    }
}

@Composable
private fun DueServiceSelectionActions(selected: List<String>, enabled: Boolean, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ServiceLoopSecondaryButton("Book selected", { nav.currentBackStackEntry?.savedStateHandle?.set("visit-setup-plan-ids", ArrayList(selected)); nav.navigate("visit/new") }, enabled = enabled, modifier = Modifier.weight(1f).fillMaxHeight().testTag("book-selected-services"))
        ServiceLoopPrimaryButton("Start selected", { viewModel.createVisit(selected, "WORKING", state.businessDate.toString(), null) { nav.navigate("visit/$it") } }, enabled = enabled, modifier = Modifier.weight(1f).fillMaxHeight().testTag("start-selected-services"))
    }
}
@Composable
private fun AdHocWorkEditor(
    equipment: List<EquipmentSummary>,
    templates: List<TemplateSummary>,
    allowKnownEquipment: Boolean,
    busy: Boolean,
    onAdd: (AdHocWorkInput) -> Unit,
    onCreateTemplate: () -> Unit = {},
    nav: NavHostController? = null,
) {
    var taskName by rememberSaveable { mutableStateOf("") }
    var subjectType by rememberSaveable { mutableStateOf(WorkSubjectType.SITE) }
    var equipmentId by rememberSaveable { mutableStateOf<String?>(null) }
    var equipmentDescription by rememberSaveable { mutableStateOf("") }
    var templateId by rememberSaveable { mutableStateOf<String?>(null) }
    val createdTemplateId = nav?.currentBackStackEntry?.savedStateHandle?.getStateFlow<String?>("created-inspection-template-id", null)?.collectAsState()
    LaunchedEffect(createdTemplateId?.value) {
        createdTemplateId?.value?.let { createdId ->
            templateId = createdId
            nav.currentBackStackEntry?.savedStateHandle?.remove<String>("created-inspection-template-id")
        }
    }
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
            ServiceLoopChoicePair(listOf(WorkSubjectType.SITE to "Site", WorkSubjectType.EQUIPMENT to "Equipment"), subjectType, { value -> subjectType = value; if (value == WorkSubjectType.SITE) { equipmentId = null; equipmentDescription = "" } }, testTagPrefix = "visit-task-subject")
            if (subjectType == WorkSubjectType.EQUIPMENT) {
                if (!allowKnownEquipment) Text("No registered equipment is available in a new customer branch.", style = MaterialTheme.typography.bodySmall)
                else ServiceLoopChoiceGroup(listOf<Pair<String?, String>>(null to "No specific equipment yet") + equipment.map { it.id to "${it.name} · ${it.reference}" }, equipmentId, { equipmentId = it; equipmentDescription = "" }, testTagPrefix = "visit-task-equipment")
                if (!allowKnownEquipment || equipmentId == null) DailyField(equipmentDescription, { equipmentDescription = it }, "Equipment description · Optional")
            }
            InspectionChecklistSelector(templates, templateId, { templateId = it }, "visit-task-template", onCreateTemplate)
            ServiceLoopPrimaryButton("Add task", { val input = AdHocWorkInput(taskName.trim(), subjectType, equipmentId, equipmentDescription.trim(), templateId); onAdd(input); taskName = ""; subjectType = WorkSubjectType.SITE; equipmentId = null; equipmentDescription = ""; templateId = null }, Modifier.fillMaxWidth().testTag("add-visit-task"), enabled = valid && !busy)
             if (!allowKnownEquipment) Text("This task will remain local to the new visit.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun VisitDateLandmark(state: String, persistedDate: String, scheduledAtEpochMillis: Long?, appointmentZoneId: String?, businessZoneId: String) {
    val colors = LocalServiceLoopTokens.current
    Column(Modifier.fillMaxWidth().testTag("visit-date-landmark"), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.xs)) {
        Text(if (state == "BOOKED") "APPOINTMENT" else "SERVICE DATE", style = ServiceLoopUiTokens.Type.dayHeading, color = colors.action, modifier = Modifier.testTag("visit-date-landmark-label"))
        val zone = runCatching { java.time.ZoneId.of(appointmentZoneId ?: businessZoneId) }.getOrDefault(java.time.ZoneId.systemDefault())
        Text(listOfNotNull(formatServiceLoopDate(persistedDate), formatAppointmentTime(scheduledAtEpochMillis, zone)).joinToString(" · "), style = ServiceLoopUiTokens.Type.screenTitle, modifier = Modifier.testTag("visit-date-landmark-value"))
    }
}

@Composable
private fun AppointmentReminderSelector(
    currentOverrideMinutes: Int?,
    resolvedDefaultMinutes: Int,
    onSelected: (Int?) -> Unit,
) {
    val options = buildList<Pair<Int?, String>> {
        add(null to "Default • ${shortReminderLead(resolvedDefaultMinutes)}")
        add(0 to "Off")
        ReminderPreferences.APPOINTMENT_LEAD_PRESETS.forEach { (minutes, _) ->
            add(minutes to reminderLeadMenuLabel(minutes))
        }
        if (currentOverrideMinutes == ReminderPreferences.LEGACY_APPOINTMENT_LEAD_MINUTES) {
            add(currentOverrideMinutes to "2 hours before (saved)")
        }
    }
    ServiceLoopFilterSelector(
        label = "Appointment reminder",
        selected = currentOverrideMinutes,
        options = options,
        onSelected = onSelected,
        testTag = "visit-reminder-selector",
    )
}

private fun shortReminderLead(minutes: Int): String = when (minutes) {
    60 -> "1h"
    180 -> "3h"
    360 -> "6h"
    720 -> "12h"
    1440 -> "1 day"
    2880 -> "2 days"
    else -> "${minutes / 60}h"
}

private fun reminderLeadMenuLabel(minutes: Int): String = when (minutes) {
    60 -> "1 hour before"
    180 -> "3 hours before"
    360 -> "6 hours before"
    720 -> "12 hours before"
    1440 -> "1 day before"
    2880 -> "2 days before"
    else -> "${minutes / 60} hours before"
}

@Composable
internal fun VisitDetailScreen(detail: VisitDetail?, padding: PaddingValues, state: UiState, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    if (detail == null) return DailyEmpty(padding, "Reading visit")
    val capabilities = LocalWorkspaceCapabilities.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val detailZone = runCatching { java.time.ZoneId.of(detail.appointmentZoneId ?: state.businessZoneId) }.getOrDefault(java.time.ZoneId.systemDefault())
    var newDate by rememberSaveable(detail.id) { mutableStateOf(detail.serviceDate) }; var appointmentTime by rememberSaveable(detail.id, detail.scheduledAtEpochMillis) { mutableStateOf(parseAppointmentTime(detail.scheduledAtEpochMillis, detailZone)) }; var reason by rememberSaveable(detail.id) { mutableStateOf("") }; var cancelReason by rememberSaveable(detail.id) { mutableStateOf("") }; var oneOffName by rememberSaveable(detail.id){mutableStateOf("")}; var oneOffEquipment by rememberSaveable(detail.id){mutableStateOf<String?>(null)}
    var reviewError by rememberSaveable(detail.id) { mutableStateOf<String?>(null) }
    var handoffStatus by rememberSaveable(detail.id) { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.padding(padding).testTag("visit-detail-list"), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.section)) {
         item { Column(Modifier.testTag("visit-identity")) { Text("${detail.reference} · ${detail.state.lowercase().replace('_',' ').replaceFirstChar(Char::uppercase)}", style = MaterialTheme.typography.headlineSmall); Text(detail.customerName); Text(detail.siteName); Text(detail.siteAddress); if (detail.customerType == CustomerType.ONE_TIME) Text("One-time customer", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("one-time-customer-label")) } }
         item {
             ServiceLoopAdaptiveActionRow(
                 actions = listOf(
                     { ServiceLoopNavigationButton("Customer", { nav.navigate("customer/${detail.customerId}") }, Modifier.testTag("visit-customer-link")) },
                     { ServiceLoopNavigationButton("Site", { nav.navigate("site/${detail.siteId}") }, Modifier.testTag("visit-site-link")) },
                     { ServiceLoopSecondaryButton("Maps", { handoffStatus = handoff(context, visitMapsIntent(detail.siteAddress), "maps", "Opened maps.", "No compatible maps app is available.") }, Modifier.testTag("visit-maps-link"), enabled = detail.siteAddress.isNotBlank()) },
                 ),
                 modifier = Modifier.testTag("visit-relationship-actions"),
             )
             handoffStatus?.let { Text(it, modifier = Modifier.testTag("visit-maps-status")) }
         }
         item { VisitDateLandmark(detail.state, detail.serviceDate, detail.scheduledAtEpochMillis, detail.appointmentZoneId, state.businessZoneId) }
        item { DispatchVisitPanel(detail) }
         item { val calendar=state.visitCalendarState;Card(Modifier.fillMaxWidth().testTag("visit-calendar")){Column(Modifier.padding(12.dp)){Text("Calendar",fontWeight=FontWeight.Bold);Text(calendar?.label?:"Checking Calendar status", modifier = Modifier.testTag("visit-calendar-status"));if(calendar?.label=="Calendar integration is off") ServiceLoopNavigationButton("Calendar settings",{nav.navigate("calendar")},Modifier.fillMaxWidth().testTag("visit-calendar-settings"));when(calendar?.action){"Add to Calendar","Recreate event"->OutlinedButton({viewModel.addVisitToCalendar(detail.id)},Modifier.fillMaxWidth().testTag("visit-calendar-add")){Text(calendar.action)};"Remove from Calendar"->OutlinedButton({viewModel.removeVisitFromCalendar(detail.id)},Modifier.fillMaxWidth().testTag("visit-calendar-remove")){Text(calendar.action)}};calendar?.eventId?.let{id->TextButton({viewModel.calendarEventIntent(id)?.let(context::startActivity)}){Text("Open Calendar event")}}}} }
        val serviceProgress = state.serviceProgress
        if (capabilities.canPerformFieldWork && serviceProgress?.visitId == detail.id) item {
            VisitServiceProgressOverview(serviceProgress, onSelect = { item ->
                viewModel.focusService(item.workItemId)
                nav.navigate("inspection/${item.workItemId}")
            })
        } else {
            items(detail.lines) { line -> ServiceLoopWorkItemRow(serviceLoopSubjectLabel(line.subjectType, line.equipmentName, line.equipmentReference, line.equipmentDescription),line.serviceName,"Due ${line.dueDate ?: "one-off"} · ${line.outcome?.lowercase()?.replace('_',' ') ?: detail.state.lowercase().replaceFirstChar(Char::uppercase)}",capabilities.canPerformFieldWork && detail.state=="WORKING",Modifier.testTag("visit-line-${line.workItemId}")){if(capabilities.canPerformFieldWork) nav.navigate("inspection/${line.workItemId}")} }
        }
        if(capabilities.canPerformFieldWork && capabilities.canCreateLocalWork && detail.state in setOf("BOOKED","WORKING")&&state.site!=null) item { AdHocWorkEditor(state.site.equipment, state.templates, allowKnownEquipment = true, state.operationInProgress, onAdd = { input -> viewModel.addAdHocWork(detail.id, input) { viewModel.loadVisit(detail.id) } }, onCreateTemplate = { nav.navigate("template/new?returnTo=visit") }, nav = nav) }
        if (detail.state == "BOOKED") item {
            var rescheduleSaved by rememberSaveable(detail.id) { mutableStateOf(false) }
            val rescheduleDateValid = runCatching { LocalDate.parse(newDate) }.isSuccess
            val rescheduleTimeValid = isValidAppointmentTimeInput(appointmentTime)
            if(capabilities.canPerformFieldWork) Button(
                { viewModel.startVisit(detail.id) { id ->
                    val target = viewModel.state.value.serviceProgress?.preferredResumeItem()?.workItemId
                    if (target != null) nav.navigate("inspection/$target") else nav.navigate("visit/$id")
            } },
            enabled = detail.lines.isNotEmpty() && !state.operationInProgress,
            modifier = Modifier.fillMaxWidth().testTag("start-visit"),
            ) { Text("Start visit") }
            if (capabilities.canPerformFieldWork && detail.lines.isEmpty()) Text("Add at least one service line before starting.")
            Spacer(Modifier.height(ServiceLoopUiTokens.Space.md))
            AppointmentReminderSelector(
                currentOverrideMinutes = detail.appointmentReminderLeadMinutes,
                resolvedDefaultMinutes = state.reminderPreferences?.defaultAppointmentLeadMinutes ?: ReminderPreferences().defaultAppointmentLeadMinutes,
                onSelected = { viewModel.setAppointmentReminderLead(detail.id, it) },
            )
            if (detail.appointmentReminderLeadMinutes == ReminderPreferences.LEGACY_APPOINTMENT_LEAD_MINUTES) {
                Text("Current saved lead: 2h. Choose a new preset to replace it.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("legacy-visit-appointment-lead"))
            }
            Column(verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.md)) {
                VisitDateInput(newDate, { newDate = it; rescheduleSaved = false }, state.businessDate)
                VisitTimeInput(appointmentTime, { appointmentTime = it; rescheduleSaved = false })
                if (rescheduleSaved) Row(Modifier.align(Alignment.End).testTag("reschedule-saved"), verticalAlignment = Alignment.CenterVertically) {
                    ServiceLoopIcon(ServiceLoopIcons.LocalSaved, null, Modifier.size(ServiceLoopUiTokens.Size.iconSmall), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(ServiceLoopUiTokens.Space.xs))
                    Text("Saved", color = MaterialTheme.colorScheme.primary)
                }
            }
            LongTextEditor(reason, { reason = it; rescheduleSaved = false }, "Reschedule reason", false)
            OutlinedButton({ viewModel.rescheduleVisit(detail.id, newDate, appointmentEpochMillis(newDate, appointmentTime, detailZone), reason) { rescheduleSaved = true } }, enabled = reason.isNotBlank() && rescheduleDateValid && rescheduleTimeValid, modifier = Modifier.fillMaxWidth()) { Text("Reschedule booking") }
            LongTextEditor(cancelReason, { cancelReason = it }, "Cancellation reason", false)
            OutlinedButton({ viewModel.cancelVisit(detail.id, cancelReason) { viewModel.loadVisit(it) } }, enabled = cancelReason.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Cancel booking") }
            Text("Rescheduling or cancelling does not fulfill or alter the due obligation.")
        }
        if (detail.state == "CANCELED" && detail.cancellationOrigin !in setOf("COORDINATOR","ASSIGNMENT_REMOVAL")) item {
            var restoreDate by rememberSaveable(detail.id) { mutableStateOf(if (runCatching { LocalDate.parse(detail.serviceDate) }.getOrNull()?.isBefore(state.businessDate) == true) state.businessDate.toString() else detail.serviceDate) }
            var restoreTime by rememberSaveable(detail.id, detail.scheduledAtEpochMillis) { mutableStateOf(parseAppointmentTime(detail.scheduledAtEpochMillis, detailZone)) }
            val restoreDateValid = runCatching { !LocalDate.parse(restoreDate).isBefore(state.businessDate) }.getOrDefault(false)
            val restoreTimeValid = isValidAppointmentTimeInput(restoreTime)
            Text("This cancellation left the obligation due.")
            Column(verticalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.md)) {
                VisitDateInput(restoreDate, { restoreDate = it }, state.businessDate)
                VisitTimeInput(restoreTime, { restoreTime = it })
            }
            Spacer(Modifier.height(ServiceLoopUiTokens.Space.lg))
            Button({ viewModel.restoreVisit(detail.id, restoreDate, appointmentEpochMillis(restoreDate, restoreTime, detailZone)) { viewModel.loadVisit(it) } }, enabled = !state.operationInProgress && restoreDateValid && restoreTimeValid, modifier = Modifier.fillMaxWidth().testTag("restore-booking")) { Text("Restore booking") }
        }
        if (capabilities.canPerformFieldWork && detail.state == "WORKING") item {
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
