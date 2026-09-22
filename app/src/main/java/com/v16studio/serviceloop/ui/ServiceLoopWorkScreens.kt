package com.v16studio.serviceloop.ui

import android.content.Intent
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopCheckbox as Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.v16studio.serviceloop.domain.ReminderPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.v16studio.serviceloop.domain.CompletionLine
import com.v16studio.serviceloop.domain.CustomerSummary
import com.v16studio.serviceloop.domain.CustomerType
import com.v16studio.serviceloop.domain.EquipmentDetail
import com.v16studio.serviceloop.domain.EquipmentSummary
import com.v16studio.serviceloop.domain.FulfillmentEligibility
import com.v16studio.serviceloop.domain.HomeSummary
import com.v16studio.serviceloop.domain.OperationalDashboardSection
import com.v16studio.serviceloop.domain.OperationalDashboardProjection
import com.v16studio.serviceloop.domain.OperationalWorkItem
import com.v16studio.serviceloop.domain.OperationalWorkKind
import com.v16studio.serviceloop.domain.OperationalWorkState
import com.v16studio.serviceloop.domain.OperationalWorkClassifier
import com.v16studio.serviceloop.domain.AgendaItem
import com.v16studio.serviceloop.domain.AgendaProjector
import com.v16studio.serviceloop.domain.formatAppointmentTime
import com.v16studio.serviceloop.domain.WorkScope
import com.v16studio.serviceloop.domain.includesCustomer
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.InspectionQuestion
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.domain.SaveStatus
import com.v16studio.serviceloop.domain.ServiceDraftFieldKeys
import com.v16studio.serviceloop.domain.BusinessProfile
import com.v16studio.serviceloop.domain.FinalRecordDetail
import com.v16studio.serviceloop.domain.RecordVersionSummary
import com.v16studio.serviceloop.domain.ReportVersionSummary
import com.v16studio.serviceloop.domain.PublicPhoto
import com.v16studio.serviceloop.domain.VisitSummary
import com.v16studio.serviceloop.domain.SiteRegisterSummary
import com.v16studio.serviceloop.domain.CompletionBlockerKind
import com.v16studio.serviceloop.domain.FollowUpDateFilter
import com.v16studio.serviceloop.domain.FollowUpStatusFilter
import com.v16studio.serviceloop.domain.VisitDateFilter
import com.v16studio.serviceloop.domain.VisitStatusFilter
import com.v16studio.serviceloop.domain.WorkSubjectType
import com.v16studio.serviceloop.domain.filterFollowUps
import com.v16studio.serviceloop.domain.filterVisits
import com.v16studio.serviceloop.domain.sortVisitsForDisplay
import com.v16studio.serviceloop.ui.theme.LocalServiceLoopColors
import com.v16studio.serviceloop.ui.theme.AppearanceMode
import com.v16studio.serviceloop.ui.theme.AppearancePreferences
import com.v16studio.serviceloop.ui.designsystem.LocalServiceLoopTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrivateLabel
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopLongTextEditor
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNotice
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopNoticeKind
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSurfaceCard
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSecondaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopBrandStrip
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDetailToolbar
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopContentTabs
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopChoiceGroup
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPresetChoiceGroup
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSelectionOption
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDayToggle
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFilterSelector
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopFilterSelectorRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSavedStatus
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopEntityRecord
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSectionDivider
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopActionStack
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextField
import com.v16studio.serviceloop.ui.designsystem.OperationalDashboard
import com.v16studio.serviceloop.ui.designsystem.OperationalWorkRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDenseNavigableRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopDashboardGateway
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopAttentionRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopVersionRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopIconAction
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopButtonAdapter as Button
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopOutlinedButtonAdapter as OutlinedButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextButtonAdapter as TextButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextFieldAdapter as OutlinedTextField
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopCardAdapter as Card
import com.v16studio.serviceloop.ui.designsystem.serviceLoopAdaptiveScaffoldPadding
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcon
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun HomeScreen(state: UiState, nav: NavHostController, viewModel: ServiceLoopViewModel) {
    val capabilities = LocalWorkspaceCapabilities.current
    LaunchedEffect(Unit) { viewModel.observeOperationalDashboard(WorkScope.Global) }
    var tab by rememberSaveable { mutableStateOf("DASHBOARD") }
    Column(Modifier.fillMaxSize().background(LocalServiceLoopTokens.current.canvas)) {
        Box(Modifier.fillMaxWidth().background(LocalServiceLoopTokens.current.surface).padding(top = ServiceLoopUiTokens.Space.xs)) {
            ServiceLoopContentTabs(
                listOf("DASHBOARD" to "Dashboard", "AGENDA" to "Agenda"),
                tab,
                { tab = it },
                testTagPrefix = "home-tab",
            )
        }
        if (tab == "DASHBOARD") {
            LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { WorkspaceHomeActions(nav) }
                state.operationalDashboard?.takeIf { it.scope == WorkScope.Global }?.let { projection ->
                    item {
                        OperationalDashboard(
                            projection = projection,
                            onOpenItem = { openOperationalWork(nav, it, capabilities.canPerformFieldWork) },
                            onViewAll = { openOperationalSection(nav, it, projection.scope) },
                            modifier = Modifier.testTag("home-operational-dashboard"),
                            showTitle = false,
                        )
                    }
                } ?: item {
                    Text(state.operationalDashboardError ?: "Reading current work", modifier = Modifier.testTag("home-operational-dashboard-loading"))
                }
                if (capabilities.canCreateLocalWork) item { Button(onClick = { nav.navigate("visit/new") }, modifier = Modifier.fillMaxWidth().testTag("new-visit-home")) { Text("New visit") } }
            }
        } else {
            HomeAgendaScreen(state, nav, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun HomeAgendaScreen(state: UiState, nav: NavHostController, modifier: Modifier = Modifier) {
    val projection = AgendaProjector.project(
        visits = state.visits,
        dueServices = state.dueServices,
        followUps = state.followUps,
        today = state.businessDate,
        businessZone = runCatching { ZoneId.of(state.businessZoneId) }.getOrDefault(ZoneId.systemDefault()),
    )
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("home-agenda"),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
    ) {
        agendaSection("Unresolved", projection.unresolved, state.businessZoneId, nav)
        agendaSection("Upcoming", projection.upcoming, state.businessZoneId, nav)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.agendaSection(
    title: String,
    items: List<AgendaItem>,
    zoneId: String,
    nav: NavHostController,
) {
    item(key = "agenda-heading-$title") {
        Text(title + " (" + items.size + ")", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("agenda-section-${title.lowercase()}"))
    }
    if (items.isEmpty()) {
        item(key = "agenda-empty-$title") { Text("Nothing here.", style = MaterialTheme.typography.bodySmall, color = LocalServiceLoopTokens.current.textMuted) }
    } else {
        items(items, key = { "agenda-${it.kind.name}-${it.recordId}" }) { agendaItem ->
            val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
            val tokens = LocalServiceLoopTokens.current
            val date = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH).format(agendaItem.date)
            val dateAndTime = listOfNotNull(date, formatAppointmentTime(agendaItem.scheduledAtEpochMillis, zone)).joinToString(" ")
            val inline = listOf(dateAndTime, agendaItem.kind.title, agendaItem.identity, agendaItem.context)
                .filter(String::isNotBlank)
                .joinToString(" · ")
            Row(
                Modifier.fillMaxWidth()
                    .background(if (items.indexOf(agendaItem) % 2 == 0) tokens.surface else tokens.surfaceSubtle)
                    .heightIn(min = ServiceLoopUiTokens.Size.listRowMin)
                    .clickable(role = Role.Button) {
                        when (agendaItem.kind) {
                            OperationalWorkKind.VISIT -> nav.navigate("visit/${agendaItem.recordId}")
                            OperationalWorkKind.SERVICE -> nav.navigate("plan/${agendaItem.recordId}")
                            OperationalWorkKind.FOLLOW_UP -> nav.navigate("follow-up/${agendaItem.recordId}")
                        }
                    }
                    .semantics { contentDescription = inline }
                    .drawBehind { drawLine(tokens.outlineDecorative, Offset(0f, size.height), Offset(size.width, size.height), ServiceLoopUiTokens.Stroke.divider.toPx()) }
                    .padding(vertical = ServiceLoopUiTokens.Space.sm)
                    .testTag("agenda-item-${agendaItem.recordId}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ServiceLoopUiTokens.Space.sm),
            ) {
                ServiceLoopIcon(
                    when (agendaItem.kind) {
                        OperationalWorkKind.VISIT -> ServiceLoopIcons.Work
                        OperationalWorkKind.SERVICE -> ServiceLoopIcons.Calendar
                        OperationalWorkKind.FOLLOW_UP -> ServiceLoopIcons.PencilSimple
                    },
                    agendaItem.kind.title,
                    Modifier.size(ServiceLoopUiTokens.Size.icon),
                    tokens.icon,
                )
                Text(inline, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = ServiceLoopUiTokens.Type.itemTitle)
                ServiceLoopIcon(ServiceLoopIcons.Disclosure, null, Modifier.size(ServiceLoopUiTokens.Size.icon), tokens.icon)
            }
        }
    }
}

internal fun openOperationalWork(nav: NavHostController, item: OperationalWorkItem, canPerformFieldWork: Boolean = true) {
    when (item.kind) {
        OperationalWorkKind.VISIT -> {
            if (canPerformFieldWork && item.state == OperationalWorkState.IN_PROGRESS && item.workItemId != null) nav.navigate("inspection/${item.workItemId}")
            else nav.navigate("visit/${item.recordId}")
        }
        OperationalWorkKind.SERVICE -> nav.navigate("plan/${item.recordId}")
        OperationalWorkKind.FOLLOW_UP -> nav.navigate("follow-up/${item.recordId}")
    }
}

internal fun openOperationalSection(nav: NavHostController, section: OperationalDashboardSection, scope: WorkScope) {
    val tab = when (section.kind) {
        OperationalWorkKind.VISIT -> WorkTab.VISITS
        OperationalWorkKind.SERVICE -> WorkTab.DUE_SERVICES
        OperationalWorkKind.FOLLOW_UP -> WorkTab.FOLLOW_UPS
    }
    nav.navigate(workRoute(tab, "OP_${section.kind.name}_${section.state.name}", scope))
}

@Composable
internal fun WorkScreen(
    state: UiState,
    nav: NavHostController,
    tab: WorkTab,
    viewModel: ServiceLoopViewModel,
    contextualFilter: String? = null,
    scope: WorkScope = WorkScope.Global,
    onTabSelected: (WorkTab) -> Unit,
) {
    val parsedOperationalFilter = parseOperationalFilter(contextualFilter)
    val initialVisitStatus = if (parsedOperationalFilter?.first == OperationalWorkKind.VISIT) VisitStatusFilter.ALL else VisitStatusFilter.entries.firstOrNull { it.name == contextualFilter } ?: VisitStatusFilter.ALL
    val initialVisitDate = if (parsedOperationalFilter?.first == OperationalWorkKind.VISIT) VisitDateFilter.ALL else VisitDateFilter.entries.firstOrNull { it.name == contextualFilter }
        ?: if (initialVisitStatus == VisitStatusFilter.ALL) VisitDateFilter.TODAY else VisitDateFilter.ALL
    val initialFollowUpDate = FollowUpDateFilter.entries.firstOrNull { it.name == contextualFilter } ?: FollowUpDateFilter.ALL
    val colors = LocalServiceLoopTokens.current
    val operationalProjection = state.operationalDashboard?.takeIf { it.scope == scope }
    val operationalStateFor: (OperationalWorkKind, String) -> OperationalWorkState? = { kind, recordId ->
        operationalProjection?.stateFor(kind, recordId)
    }
    val customerScopeName = (scope as? WorkScope.Customer)?.let { customerScope ->
        state.customer?.takeIf { it.id == customerScope.customerId }?.name
            ?: state.customerList.firstOrNull { it.id == customerScope.customerId }?.name
    }
    Column(Modifier.fillMaxSize().background(colors.canvas)) {
        Box(Modifier.fillMaxWidth().background(colors.surface).padding(top = 12.dp)) {
            ServiceLoopContentTabs(WorkTab.entries.map { it to it.label }, tab, onTabSelected)
        }
        customerScopeName?.let { name ->
            Text(
                "Customer: $name",
                modifier = Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 16.dp, vertical = 6.dp).testTag("work-customer-scope"),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        when (tab) {
            WorkTab.DUE_SERVICES -> {
                val contextualDueBucket = runCatching { com.v16studio.serviceloop.domain.DueBucket.valueOf(contextualFilter.orEmpty()) }.getOrNull()
                DueServicesScreen(
                    values = state.dueServices,
                    padding = PaddingValues(),
                    state = state,
                    viewModel = viewModel,
                    nav = nav,
                    modifier = Modifier.weight(1f),
                    initialBucket = contextualDueBucket,
                    initialOperationalState = parsedOperationalFilter?.takeIf { it.first == OperationalWorkKind.SERVICE }?.second,
                    contextualFilter = contextualFilter,
                    scope = scope,
                    operationalStateFor = { due -> operationalStateFor(OperationalWorkKind.SERVICE, due.planId) },
                    onNewVisit = { nav.navigate("visit/new") },
                )
            }
            WorkTab.VISITS -> VisitsWorkScreen(
                values = state.visits,
                businessDate = state.businessDate,
                padding = PaddingValues(),
                nav = nav,
                modifier = Modifier.weight(1f),
                initialDateFilter = initialVisitDate,
                initialStatusFilter = initialVisitStatus,
                initialOperationalState = parsedOperationalFilter?.takeIf { it.first == OperationalWorkKind.VISIT }?.second,
                contextualFilter = contextualFilter,
                scope = scope,
                operationalStateFor = { visit -> operationalStateFor(OperationalWorkKind.VISIT, visit.id) },
                onNewVisit = { nav.navigate("visit/new") },
            )
            WorkTab.FOLLOW_UPS -> FollowUpsWorkScreen(
                values = state.followUps,
                businessDate = state.businessDate,
                padding = PaddingValues(),
                nav = nav,
                modifier = Modifier.weight(1f),
                initialDateFilter = initialFollowUpDate,
                dueSoonHorizonDays = state.home?.dueSoonHorizonDays ?: 14,
                initialOperationalState = parsedOperationalFilter?.takeIf { it.first == OperationalWorkKind.FOLLOW_UP }?.second,
                contextualFilter = contextualFilter,
                scope = scope,
                operationalStateFor = { followUp -> operationalStateFor(OperationalWorkKind.FOLLOW_UP, followUp.id) },
                onNewVisit = { nav.navigate("visit/new") },
            )
        }
    }
}

private fun parseOperationalFilter(value: String?): Pair<OperationalWorkKind, OperationalWorkState>? {
    val encoded = value?.removePrefix("OP_")?.takeIf { it != value } ?: return null
    val kind = OperationalWorkKind.entries.firstOrNull { encoded.startsWith("${it.name}_") } ?: return null
    val stateName = encoded.removePrefix("${kind.name}_")
    val state = runCatching { OperationalWorkState.valueOf(stateName) }.getOrNull() ?: return null
    return kind to state
}

@Composable
internal fun VisitsWorkScreen(
    values: List<VisitSummary>,
    businessDate: LocalDate,
    padding: PaddingValues,
    nav: NavHostController,
    modifier: Modifier = Modifier,
    initialDateFilter: VisitDateFilter = VisitDateFilter.TODAY,
    initialStatusFilter: VisitStatusFilter = VisitStatusFilter.ALL,
    initialOperationalState: OperationalWorkState? = null,
    contextualFilter: String? = null,
    scope: WorkScope = WorkScope.Global,
    operationalStateFor: (VisitSummary) -> OperationalWorkState? = { null },
    onNewVisit: () -> Unit = {},
) {
    val capabilities = LocalWorkspaceCapabilities.current
    val context = LocalContext.current
    val filterPreferences = remember(context) { UiFilterPreferences(context) }
    val remembersFilters = contextualFilter == null && initialOperationalState == null
    var dateFilter by rememberSaveable(remembersFilters, initialDateFilter) { mutableStateOf(if (remembersFilters) filterPreferences.visitDate(initialDateFilter) else initialDateFilter) }
    var statusFilter by rememberSaveable(remembersFilters, initialStatusFilter) { mutableStateOf(if (remembersFilters) filterPreferences.visitStatus(initialStatusFilter) else initialStatusFilter) }
    var query by rememberSaveable { mutableStateOf("") }
    val scopedValues = values.filter { scope.includesCustomer(it.customerId) }
    val filtered = if (initialOperationalState == null) filterVisits(scopedValues, dateFilter, statusFilter, businessDate, query) else scopedValues.filter { visit ->
        operationalStateFor(visit) == initialOperationalState &&
            (query.isBlank() || listOf(visit.reference, visit.siteName, visit.actualServiceDate, visit.state, visit.customerName).any { it.contains(query, true) })
    }
    val ordered = sortVisitsForDisplay(filtered, dateFilter, statusFilter)
    val listState = rememberLazyListState()
    val actionState = rememberWorkNewVisitActionState(listState)
    Box(modifier.padding(padding)) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("work-visits-list"),
            state = listState,
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, workNewVisitListBottomPadding(actionState)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item {
            ServiceLoopTextField(query, { query = it }, "Search visits", modifier = Modifier.testTag("visit-search"))
            Spacer(Modifier.height(ServiceLoopUiTokens.Space.lg))
            if (initialOperationalState != null) Text(OperationalWorkClassifier.sectionTitle(OperationalWorkKind.VISIT, initialOperationalState), style = MaterialTheme.typography.titleMedium)
            if (initialOperationalState == null) ServiceLoopFilterSelectorRow(
                first = {
                    ServiceLoopFilterSelector(
                        label = "Date",
                        selected = dateFilter,
                        options = VisitDateFilter.entries.map { it to it.label },
                        onSelected = { dateFilter = it; if (remembersFilters) filterPreferences.saveVisitDate(it) },
                        testTag = "visit-date-selector",
                    )
                },
                second = {
                    ServiceLoopFilterSelector(
                        label = "Status",
                        selected = statusFilter,
                        options = VisitStatusFilter.entries.map { it to it.label },
                        onSelected = { statusFilter = it; if (remembersFilters) filterPreferences.saveVisitStatus(it) },
                        testTag = "visit-status-selector",
                    )
                },
            )
        }
        if (ordered.isEmpty()) item { Text("No visits match these filters.") }
        items(ordered) { visit ->
            ServiceLoopEntityRecord(visit.reference, visit.siteName, visit.actualServiceDate, visit.state, operationalState = operationalStateFor(visit)) {
                if (visit.finalRecordId != null) nav.navigate("record/${visit.finalRecordId}") else nav.navigate("visit/${visit.id}")
            }
        }
            if (capabilities.canCreateLocalWork) item(key = WORK_NEW_VISIT_SLOT_KEY) { WorkNewVisitReservedSlot(onNewVisit) }
        }
        if (capabilities.canCreateLocalWork) WorkNewVisitFloatingAction(actionState, onNewVisit)
    }
}

@Composable
internal fun FollowUpsWorkScreen(
    values: List<com.v16studio.serviceloop.domain.FollowUpDetail>,
    businessDate: LocalDate,
    padding: PaddingValues,
    nav: NavHostController,
    modifier: Modifier = Modifier,
    initialDateFilter: FollowUpDateFilter = FollowUpDateFilter.ALL,
    initialStatusFilter: FollowUpStatusFilter = FollowUpStatusFilter.OPEN,
    dueSoonHorizonDays: Int = 14,
    initialOperationalState: OperationalWorkState? = null,
    contextualFilter: String? = null,
    scope: WorkScope = WorkScope.Global,
    operationalStateFor: (com.v16studio.serviceloop.domain.FollowUpDetail) -> OperationalWorkState? = { followUp ->
        OperationalWorkClassifier.classifyFollowUp(followUp.state, followUp.dueDate, businessDate, dueSoonHorizonDays)
    },
    onNewVisit: () -> Unit = {},
) {
    val capabilities = LocalWorkspaceCapabilities.current
    val context = LocalContext.current
    val filterPreferences = remember(context) { UiFilterPreferences(context) }
    val remembersFilters = contextualFilter == null && initialOperationalState == null
    var dateFilter by rememberSaveable(remembersFilters, initialDateFilter) { mutableStateOf(if (remembersFilters) filterPreferences.followUpDate(initialDateFilter) else initialDateFilter) }
    var statusFilter by rememberSaveable(remembersFilters, initialStatusFilter) { mutableStateOf(if (remembersFilters) filterPreferences.followUpStatus(initialStatusFilter) else initialStatusFilter) }
    var query by rememberSaveable { mutableStateOf("") }
    val scopedValues = values.filter { scope.includesCustomer(it.customerId) }
    val filtered = if (initialOperationalState == null) filterFollowUps(scopedValues, dateFilter, statusFilter, businessDate, query) else scopedValues.filter { followUp ->
        operationalStateFor(followUp) == initialOperationalState &&
            (query.isBlank() || listOfNotNull(followUp.reference, followUp.title, followUp.customerName, followUp.siteName, followUp.equipmentName, followUp.dueDate).any { it.contains(query, true) })
    }
    val listState = rememberLazyListState()
    val actionState = rememberWorkNewVisitActionState(listState)
    Box(modifier.padding(padding)) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("work-follow-ups-list"),
            state = listState,
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, workNewVisitListBottomPadding(actionState)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item {
            ServiceLoopTextField(query, { query = it }, "Search follow-ups", modifier = Modifier.testTag("follow-up-search"))
            Spacer(Modifier.height(ServiceLoopUiTokens.Space.lg))
            if (initialOperationalState != null) Text(OperationalWorkClassifier.sectionTitle(OperationalWorkKind.FOLLOW_UP, initialOperationalState), style = MaterialTheme.typography.titleMedium)
            if (initialOperationalState == null) ServiceLoopFilterSelectorRow(
                first = {
                    ServiceLoopFilterSelector(
                        label = "Due date",
                        selected = dateFilter,
                        options = FollowUpDateFilter.entries.map { it to it.label },
                        onSelected = { dateFilter = it; if (remembersFilters) filterPreferences.saveFollowUpDate(it) },
                        testTag = "follow-up-date-selector",
                    )
                },
                second = {
                    ServiceLoopFilterSelector(
                        label = "Status",
                        selected = statusFilter,
                        options = FollowUpStatusFilter.entries.map { it to it.label },
                        onSelected = { statusFilter = it; if (remembersFilters) filterPreferences.saveFollowUpStatus(it) },
                        testTag = "follow-up-status-selector",
                    )
                },
            )
        }
        if (filtered.isEmpty()) item { Text("No follow-ups match these filters.") }
        items(filtered) { follow ->
            ServiceLoopEntityRecord(
                "${follow.reference} · ${follow.title}",
                listOfNotNull(follow.customerName, follow.siteName, follow.equipmentName).filter { it.isNotBlank() }.joinToString(" · "),
                "Due ${follow.dueDate}",
                follow.state,
                operationalState = operationalStateFor(follow),
            ) { nav.navigate("follow-up/${follow.id}") }
        }
            if (capabilities.canCreateLocalWork) item(key = WORK_NEW_VISIT_SLOT_KEY) { WorkNewVisitReservedSlot(onNewVisit) }
        }
        if (capabilities.canCreateLocalWork) WorkNewVisitFloatingAction(actionState, onNewVisit)
    }
}
