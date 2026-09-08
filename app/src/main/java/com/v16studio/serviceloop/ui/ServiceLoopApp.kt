package com.v16studio.serviceloop.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.v16studio.serviceloop.domain.CompletionLine
import com.v16studio.serviceloop.domain.CustomerSummary
import com.v16studio.serviceloop.domain.EquipmentDetail
import com.v16studio.serviceloop.domain.EquipmentSummary
import com.v16studio.serviceloop.domain.FulfillmentEligibility
import com.v16studio.serviceloop.domain.HomeSummary
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.InspectionQuestion
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.domain.SaveStatus
import com.v16studio.serviceloop.domain.BusinessProfile
import com.v16studio.serviceloop.domain.FinalRecordDetail
import com.v16studio.serviceloop.domain.VisitSummary
import com.v16studio.serviceloop.domain.SiteRegisterSummary
import com.v16studio.serviceloop.domain.CompletionBlockerKind
import com.v16studio.serviceloop.domain.VisitFilter
import com.v16studio.serviceloop.domain.VisitDateWindow
import com.v16studio.serviceloop.domain.FollowUpFilter
import com.v16studio.serviceloop.ui.theme.LocalServiceLoopColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val HOME = "home"
private const val WORK = "work"
private const val CUSTOMERS = "customers"

private enum class RootDestination(val route: String, val label: String) {
    HOME("home", "Home"),
    WORK("work", "Work"),
    CUSTOMERS("customers", "Customers"),
}

internal enum class WorkTab(val label: String) {
    DUE_SERVICES("Due services"),
    VISITS("Visits"),
    FOLLOW_UPS("Follow-ups"),
}

@Composable
fun ServiceLoopApp(viewModel: ServiceLoopViewModel) {
    val state by viewModel.state.collectAsState()
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = HOME,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(HOME) {
            LaunchedEffect(Unit) { viewModel.refreshRootDataNonBlocking() }
            RootScaffold(nav, RootDestination.HOME) { padding ->
                ScreenState(state.loading && !state.rootDataReady, state.error.takeUnless { state.rootDataReady }, padding, "root-home") { HomeScreen(state.home, state.equipmentList, state.visits, nav) }
            }
        }
        composable(WORK) {
            var workTab by rememberSaveable { mutableStateOf(WorkTab.DUE_SERVICES) }
            LaunchedEffect(Unit) { viewModel.refreshRootDataNonBlocking(); viewModel.loadVisits(); viewModel.loadDueServices(); viewModel.loadFollowUps() }
            RootScaffold(nav, RootDestination.WORK) { padding ->
                ScreenState(state.loading && !state.rootDataReady, state.error.takeUnless { state.rootDataReady }, padding, "root-work") { WorkScreen(state, nav, workTab, viewModel) { workTab = it } }
            }
        }
        composable("work/{tab}") { entry ->
            val requested = runCatching { WorkTab.valueOf(entry.arguments?.getString("tab").orEmpty()) }.getOrDefault(WorkTab.DUE_SERVICES)
            var workTab by rememberSaveable(requested) { mutableStateOf(requested) }
            LaunchedEffect(Unit) { viewModel.refreshRootDataNonBlocking(); viewModel.loadVisits(); viewModel.loadDueServices(); viewModel.loadFollowUps() }
            RootScaffold(nav, RootDestination.WORK) { padding -> ScreenState(state.loading && !state.rootDataReady, state.error.takeUnless { state.rootDataReady }, padding) { WorkScreen(state, nav, workTab, viewModel) { workTab = it } } }
        }
        composable("work/{tab}/{filter}") { entry ->
            val requested=runCatching{WorkTab.valueOf(entry.arguments?.getString("tab").orEmpty())}.getOrDefault(WorkTab.DUE_SERVICES)
            val contextualFilter=entry.arguments?.getString("filter")
            var workTab by rememberSaveable(requested){mutableStateOf(requested)}
            LaunchedEffect(Unit){viewModel.refreshRootDataNonBlocking();viewModel.loadVisits();viewModel.loadDueServices();viewModel.loadFollowUps()}
            RootScaffold(nav,RootDestination.WORK){padding->ScreenState(state.loading&&!state.rootDataReady,state.error.takeUnless{state.rootDataReady},padding){WorkScreen(state,nav,workTab,viewModel,contextualFilter){workTab=it}}}
        }
        composable(CUSTOMERS) {
            var equipmentMode by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) { viewModel.refreshRootDataNonBlocking() }
            RootScaffold(nav, RootDestination.CUSTOMERS) { padding ->
                ScreenState(state.loading && !state.rootDataReady, state.error.takeUnless { state.rootDataReady }, padding, "root-customers") { CustomersScreen(state.customerList, state.siteList, state.equipmentList, nav) }
            }
        }
        composable("equipment/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            LaunchedEffect(id) { viewModel.loadEquipment(id) }
            DetailScaffold("Equipment", nav) { padding ->
                ScreenState(state.loading, state.error, padding) { state.equipment?.let { EquipmentScreen(it, nav) } }
            }
        }
        composable("customer/new") { DetailScaffold("Add customer", nav) { CustomerEditorScreen(null, it, state, viewModel, nav) } }
        composable("customer/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadCustomer(id)}; DetailScaffold("Customer",nav){CustomerDetailScreen(state.customer,it,nav,viewModel)} }
        composable("customer/edit/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadCustomer(id)}; DetailScaffold("Edit customer",nav){CustomerEditorScreen(state.customer,it,state,viewModel,nav)} }
        composable("site/new/{customerId}") { entry -> val id=entry.arguments?.getString("customerId"); DetailScaffold("Add site",nav){SiteEditorScreen(id,null,it,state,viewModel,nav)} }
        composable("site/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadSite(id)}; DetailScaffold("Site",nav){SiteDetailScreen(state.site,it,nav)} }
        composable("site/edit/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadSite(id)}; DetailScaffold("Edit site",nav){SiteEditorScreen(null,state.site,it,state,viewModel,nav)} }
        composable("equipment/new/{siteId}") { entry -> val id=entry.arguments?.getString("siteId"); DetailScaffold("Add equipment",nav){EquipmentEditorScreen(id,null,it,state,viewModel,nav)} }
        composable("equipment/select-site") { LaunchedEffect(Unit){viewModel.loadVisitSetup()}; DetailScaffold("Choose equipment site",nav){EquipmentSiteSelectorScreen(state.visitSites,it,nav)} }
        composable("equipment/edit/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadEquipment(id)}; DetailScaffold("Edit equipment",nav){EquipmentEditorScreen(null,state.equipment,it,state,viewModel,nav)} }
        composable("plan/new/{equipmentId}") { entry -> val id=entry.arguments?.getString("equipmentId"); LaunchedEffect(Unit){viewModel.loadTemplates()}; DetailScaffold("Add service plan",nav){PlanEditorScreen(id,null,state.templates,it,state,viewModel,nav)} }
        composable("plan/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadPlan(id)}; DetailScaffold("Service plan",nav){PlanDetailScreen(state.plan,it,nav)} }
        composable("plan/edit/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadPlan(id)}; DetailScaffold("Edit service plan",nav){PlanEditorScreen(null,state.plan,state.templates,it,state,viewModel,nav)} }
        composable("template/list") { LaunchedEffect(Unit){viewModel.loadTemplates()}; DetailScaffold("Inspection templates",nav){TemplateListScreen(state.templates,it,nav)} }
        composable("template/new") { DetailScaffold("Create template",nav){TemplateEditorScreen(null,it,state,viewModel,nav)} }
        composable("template/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadTemplate(id)}; DetailScaffold("Inspection template",nav){TemplateDetailScreen(state.template,it,nav)} }
        composable("template/edit/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadTemplate(id)}; DetailScaffold("New template revision",nav){TemplateEditorScreen(state.template,it,state,viewModel,nav)} }
        composable("visit/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadVisit(id)}; DetailScaffold("Visit",nav){VisitDetailScreen(state.visit,it,state,viewModel,nav)} }
        composable("visit/new") { LaunchedEffect(Unit){viewModel.loadVisitSetup()}; DetailScaffold("Create visit",nav){NewVisitScreen(state.visitSites,state.dueServices,it,state,viewModel,nav)} }
        composable("visit/new/{planId}") { entry -> val id=entry.arguments?.getString("planId").orEmpty(); LaunchedEffect(id){viewModel.loadVisitSetup()}; DetailScaffold("Create visit",nav){NewVisitScreen(state.visitSites,state.dueServices,it,state,viewModel,nav,id)} }
        composable("field/{workItemId}") { entry -> val id=entry.arguments?.getString("workItemId").orEmpty(); LaunchedEffect(id){viewModel.loadFieldEvidence(id)}; DetailScaffold("Parts and photographs",nav){FieldEvidenceScreen(id,state,it,viewModel,nav)} }
        composable("follow-up/list") { LaunchedEffect(Unit){viewModel.loadFollowUps()}; DetailScaffold("Follow-ups",nav){FollowUpListScreen(state.followUps,it,nav)} }
        composable("follow-up/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadFollowUp(id)}; DetailScaffold("Follow-up",nav){FollowUpDetailScreen(state.followUp,it,state,viewModel,nav)} }
        composable("follow-up/new/{customerId}") { entry -> val id=entry.arguments?.getString("customerId").orEmpty(); DetailScaffold("Add follow-up",nav){FollowUpEditorScreen(id,it,state,viewModel,nav)} }
        composable("contact/new/{customerId}") { entry -> val id=entry.arguments?.getString("customerId").orEmpty(); DetailScaffold("Record contact",nav){ContactNoteEditorScreen(id,it,state,viewModel,nav)} }
        composable("search") { DetailScaffold("Search",nav){SearchScreen(state.searchResults,it,viewModel,nav)} }
        composable("inspection/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            LaunchedEffect(id) { viewModel.loadInspection(id) }
            DetailScaffold(state.inspection?.serviceName ?: "Inspection", nav) { padding ->
                ScreenState(state.loading, state.error, padding) { state.inspection?.let { InspectionScreen(it, state.saveStatus, state.inspectionFocus, viewModel, nav) } }
            }
        }
        composable("review/{visitId}") { entry ->
            val visitId = entry.arguments?.getString("visitId").orEmpty()
            LaunchedEffect(visitId) { viewModel.loadCompletion(visitId) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, visitId) { val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.loadCompletion(visitId) }; lifecycleOwner.lifecycle.addObserver(observer); onDispose { lifecycleOwner.lifecycle.removeObserver(observer) } }
            LaunchedEffect(state.finalizedRecordId) { state.finalizedRecordId?.let { recordId -> viewModel.consumeFinalizedNavigation(); nav.navigate("record/$recordId") { popUpTo("review/{visitId}") { inclusive = true } } } }
            DetailScaffold("Review completion", nav) { padding -> CompletionReviewScreen(visitId, state.completionLines, state.businessProfile, state, padding, viewModel, nav) }
        }
        composable("settings") {
            DetailScaffold("Settings", nav) { padding -> SettingsScreen(padding, nav) }
        }
        composable("business-profile") {
            LaunchedEffect(Unit) { viewModel.loadBusinessProfile() }
            DetailScaffold("Business and report identity", nav) { padding -> BusinessProfileScreen(state.businessProfile, state.businessProfileSaveStatus, padding, viewModel) }
        }
        composable("record/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id) { viewModel.loadFinalRecord(id) }
            DetailScaffold("Final service record", nav) { padding -> FinalRecordScreen(state.finalRecord, state.generatingReport, state.error, padding, viewModel, nav) }
        }
        composable("report/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id) { viewModel.loadFinalRecord(id) }
            DetailScaffold("Customer report", nav) { padding -> ReportPreviewScreen(state.finalRecord, padding, initialTextView = false) }
        }
        composable("report-text/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id) { viewModel.loadFinalRecord(id) }
            DetailScaffold("Customer report", nav) { padding -> ReportPreviewScreen(state.finalRecord, padding, initialTextView = true) }
        }
        composable("scope/{title}") { entry ->
            DetailScaffold(entry.arguments?.getString("title") ?: "ServiceLoop", nav) { padding ->
                HonestPlaceholder(padding, "This foundation exposes the entry point without claiming the later workflow is complete.")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootScaffold(nav: NavHostController, selected: RootDestination, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selected == RootDestination.HOME) {
                        Column {
                            Text("ServiceLoop", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text("Home", style = MaterialTheme.typography.titleLarge)
                        }
                    } else {
                        Text(selected.label, style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    TextButton(onClick = { nav.navigate("search") }) { Text("Search") }
                    TextButton(onClick = { nav.navigate("settings") }) { Text("Settings") }
                },
            )
        },
        bottomBar = { RootNavigation(selected, nav::navigateToRoot) },
        floatingActionButton = { if (selected == RootDestination.WORK) Button(onClick = { nav.navigate("visit/new") }, modifier = Modifier.testTag("new-visit-work")) { Text("New visit") } },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScaffold(title: String, nav: NavHostController, content: @Composable (PaddingValues) -> Unit) {
    val interceptor=remember { mutableStateOf<(() -> Unit)?>(null) }
    CompositionLocalProvider(LocalDetailBackInterceptor provides interceptor) { Scaffold(topBar = { TopAppBar(title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) }, navigationIcon = { TextButton(onClick = { interceptor.value?.invoke() ?: nav.popBackStack() }) { Text("Back") } }) }, content = content) }
}

internal val LocalDetailBackInterceptor = compositionLocalOf<MutableState<(() -> Unit)?>> { error("Detail back interceptor unavailable") }

@Composable
private fun RootNavigation(selected: RootDestination, onNavigate: (RootDestination) -> Unit) {
    NavigationBar {
        RootDestination.entries.forEach { destination ->
            NavigationBarItem(selected = selected == destination, onClick = { onNavigate(destination) }, icon = { Text(if (destination == RootDestination.HOME) "⌂" else if (destination == RootDestination.WORK) "✓" else "◎") }, label = { Text(destination.label) })
        }
    }
}

private fun NavHostController.navigateToRoot(destination: RootDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = false }
        launchSingleTop = true
        restoreState = false
    }
}

@Composable
private fun ScreenState(loading: Boolean, error: String?, padding: PaddingValues, contentTag: String? = null, content: @Composable () -> Unit) {
    when {
        loading -> Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Text("Reading saved service book", Modifier.padding(16.dp)) }
        error != null -> HonestPlaceholder(padding, "Cannot read saved data. $error")
        else -> Surface(Modifier.fillMaxSize().padding(padding).then(if (contentTag == null) Modifier else Modifier.testTag(contentTag)), color = MaterialTheme.colorScheme.background) { content() }
    }
}

@Composable
private fun HomeScreen(home: HomeSummary?, equipment: List<EquipmentSummary>, visits: List<VisitSummary>, nav: NavHostController) {
    if (home == null) return HonestPlaceholder(PaddingValues(), "Add a customer to create your first service obligation.")
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (home.workingVisitId != null) item {
            SectionTitle("Unfinished visits · ${home.workingVisitCount}")
            AccentCard {
                Text(home.workingSite.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text("${home.workingVisitReference} · Working", color = LocalServiceLoopColors.current.workflowInk)
                Text("Saved on this device · ${formatTime(home.savedAtEpochMillis)}", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { home.inspectionWorkItemId?.let { nav.navigate("inspection/$it") } }, modifier = Modifier.fillMaxWidth()) { Text("Resume visit") }
                TextButton(onClick = { nav.navigate("work/${WorkTab.VISITS.name}/WORKING") }) { Text("View all unfinished") }
            }
        }
        item {
            SectionTitle("Booked visits · ${home.bookedVisitCount}")
            val bookedSite = visits.firstOrNull { it.state == "BOOKED" && it.reference == home.bookedVisitReference }?.siteName
            val bookedSummary = listOfNotNull(
                home.bookedVisitReference?.let { "$it · ${home.bookedVisitDate}" },
                bookedSite,
            ).joinToString("\n").ifBlank { "No booked visits" }
            SummaryRow(bookedSummary, "Open") { nav.navigate("work/${WorkTab.VISITS.name}/BOOKED") }
        }
        item { SectionTitle("Overdue services · ${home.overdueCount}"); SummaryRow("Booked service remains due until its obligation is explicitly fulfilled.","View all"){nav.navigate("work/${WorkTab.DUE_SERVICES.name}/OVERDUE")} }
        items(equipment.take(3)) { item -> SummaryRow("${item.technicianIdentifier ?: item.reference} · ${item.name}\nDue ${item.nearestDueDate ?: "not scheduled"}", "Open") { nav.navigate("equipment/${item.id}") } }
        item { SectionTitle("Due soon · ${home.dueSoonCount}"); SummaryRow("Next 14 business-local days", "View all") { nav.navigate("work/${WorkTab.DUE_SERVICES.name}/DUE_SOON") } }
        item { SectionTitle("Follow-ups due · ${home.dueFollowUpCount}"); SummaryRow(listOfNotNull(home.dueFollowUpReference, home.dueFollowUpTitle).joinToString(" · ").ifBlank { "No follow-ups due" }, "Open due") { nav.navigate("work/${WorkTab.FOLLOW_UPS.name}/DUE_OR_OVERDUE") } }
        item { SectionTitle("Records needing attention"); Text("No report or correction failures in this fixture.") }
        item { Button(onClick = { nav.navigate("visit/new") }, modifier = Modifier.fillMaxWidth().testTag("new-visit-home")) { Text("New visit") } }
    }
}

@Composable
private fun WorkScreen(state: UiState, nav: NavHostController, tab: WorkTab, viewModel: ServiceLoopViewModel, contextualFilter: String? = null, onTabSelected: (WorkTab) -> Unit) {
    if (tab == WorkTab.DUE_SERVICES) {
        val contextualDueBucket=runCatching{com.v16studio.serviceloop.domain.DueBucket.valueOf(contextualFilter.orEmpty())}.getOrNull()
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { WorkTab.entries.forEach { option -> if (tab == option) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(option.label) } else OutlinedButton(onClick = { onTabSelected(option) }, modifier = Modifier.weight(1f)) { Text(option.label) } } }
            DueServicesScreen(state.dueServices, PaddingValues(), state, viewModel, nav, Modifier.weight(1f), contextualDueBucket)
        }
        return
    }
    var visitFilter by rememberSaveable(tab,contextualFilter){mutableStateOf(runCatching{VisitFilter.valueOf(contextualFilter.orEmpty())}.getOrDefault(VisitFilter.ALL))}
    var visitWindow by rememberSaveable(tab){mutableStateOf(VisitDateWindow.ALL_DATES)}
    var followFilter by rememberSaveable(tab,contextualFilter){mutableStateOf(runCatching{FollowUpFilter.valueOf(contextualFilter.orEmpty())}.getOrDefault(FollowUpFilter.ALL_OPEN))}
    val today=LocalDate.now()
    LazyColumn(Modifier.testTag("work-${tab.name.lowercase()}-list"), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { WorkTab.entries.forEach { option -> if (tab == option) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(option.label) } else OutlinedButton(onClick = { onTabSelected(option) }, modifier = Modifier.weight(1f)) { Text(option.label) } } } }
        when (tab) {
            WorkTab.DUE_SERVICES -> Unit
            WorkTab.VISITS -> {
                val filtered=state.visits.filter { visit -> (visitFilter==VisitFilter.ALL||visit.state==visitFilter.name) && when(visitWindow){VisitDateWindow.ALL_DATES->true;VisitDateWindow.PAST_30_DAYS->runCatching{LocalDate.parse(visit.actualServiceDate) in today.minusDays(30)..today}.getOrDefault(false);VisitDateWindow.NEXT_30_DAYS->runCatching{LocalDate.parse(visit.actualServiceDate) in today..today.plusDays(30)}.getOrDefault(false)} }
                item { SectionTitle("Visits"); Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){VisitFilter.entries.forEach{option->FilterChip(visitFilter==option,{visitFilter=option},{Text(option.name.lowercase().replaceFirstChar(Char::uppercase))},modifier=Modifier.testTag("visit-filter-${option.name}"))}}; Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){VisitDateWindow.entries.forEach{option->FilterChip(visitWindow==option,{visitWindow=option},{Text(option.name.lowercase().replace('_',' '))},modifier=Modifier.testTag("visit-window-${option.name}"))}} }
                if (filtered.isEmpty()) item { Text("No visits match these filters") }
                items(filtered) { visit -> SummaryRow("${visit.reference} · ${visit.state.lowercase().replaceFirstChar { it.uppercase() }} · ${visit.actualServiceDate}\n${visit.siteName}", "Open") { if (visit.finalRecordId != null) nav.navigate("record/${visit.finalRecordId}") else nav.navigate("visit/${visit.id}") } }
            }
            WorkTab.FOLLOW_UPS -> {
                val filtered=state.followUps.filter{follow->val due=LocalDate.parse(follow.dueDate);when(followFilter){FollowUpFilter.DUE_OR_OVERDUE->follow.state=="OPEN"&&!due.isAfter(today);FollowUpFilter.UPCOMING->follow.state=="OPEN"&&due.isAfter(today);FollowUpFilter.ALL_OPEN->follow.state=="OPEN";FollowUpFilter.CLOSED->follow.state!="OPEN"}}
                item { SectionTitle("Follow-ups · ${filtered.size}"); Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){FollowUpFilter.entries.forEach{option->FilterChip(followFilter==option,{followFilter=option},{Text(option.name.lowercase().replace('_',' '))},modifier=Modifier.testTag("follow-filter-${option.name}"))}} }
                if(filtered.isEmpty()) item{Text("No follow-ups match this filter")}
                items(filtered) { follow -> SummaryRow("${follow.reference} · ${follow.title}\n${follow.state} · Due ${follow.dueDate}\n${listOfNotNull(follow.customerName,follow.siteName,follow.equipmentName).filter{it.isNotBlank()}.joinToString(" · ")}", "Open") { nav.navigate("follow-up/${follow.id}") } }
            }
        }
        item { SummaryRow("History and records needing attention", "Open") { nav.navigate("scope/History") } }
    }
}

@Composable
private fun CustomersScreen(customers: List<CustomerSummary>, sites: List<SiteRegisterSummary>, equipment: List<EquipmentSummary>, nav: NavHostController) {
    var tab by rememberSaveable { mutableStateOf("CUSTOMERS") }
    LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("CUSTOMERS" to "Customers", "SITES" to "Sites", "EQUIPMENT" to "Equipment").forEach { (value, label) -> if (tab == value) Button(onClick = {}, modifier = Modifier.testTag("customers-tab-$value")) { Text(label) } else OutlinedButton(onClick = { tab = value }, modifier = Modifier.testTag("customers-tab-$value")) { Text(label) } } }; Text("${tab.lowercase().replaceFirstChar { it.uppercase() }} register", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
        item {
            when (tab) { "CUSTOMERS" -> OutlinedButton(onClick = { nav.navigate("customer/new") }, modifier = Modifier.fillMaxWidth().testTag("add-customer")) { Text("Add customer") }; "EQUIPMENT" -> OutlinedButton(onClick = { nav.navigate("equipment/select-site") }, modifier = Modifier.fillMaxWidth().testTag("add-equipment-from-register")) { Text("Add equipment") }; else -> Unit }
        }
        when (tab) {
            "CUSTOMERS" -> {
            if (customers.isEmpty()) item { Text("Add a customer to begin.") }
            items(customers) { item -> SummaryRow("${item.name}\n${item.reference} · ${item.siteCount} site · ${item.equipmentCount} equipment", "Open") { nav.navigate("customer/${item.id}") } }
            }
            "SITES" -> {
                if (sites.isEmpty()) item { Text("No sites yet.") }
                items(sites) { item -> SummaryRow("${item.reference} · ${item.name}\n${item.customerName}\n${item.address.ifBlank { "No address" }} · ${item.equipmentCount} equipment", "Open") { nav.navigate("site/${item.id}") } }
            }
            else -> {
            if (equipment.isEmpty()) item { Text("Add an equipment item to begin.") }
            items(equipment) { item -> SummaryRow("${item.technicianIdentifier ?: item.reference} · ${item.name}\n${item.customerName} · ${item.siteName}\nNext due ${item.nearestDueDate ?: "not scheduled"}", "Open") { nav.navigate("equipment/${item.id}") } }
            }
        }
    }
}

@Composable
private fun EquipmentScreen(detail: EquipmentDetail, nav: NavHostController) {
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(detail.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(listOfNotNull(detail.technicianIdentifier, detail.reference).joinToString(" · "), style = MaterialTheme.typography.titleMedium)
            Text(if (detail.makeModel.isBlank()) "Make/model not supplied" else detail.makeModel)
            Text(detail.serialNumber?.let { "Serial $it" } ?: "Serial not supplied", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${detail.customerName}\n${detail.siteName}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { nav.navigate("equipment/edit/${detail.id}") }, Modifier.weight(1f)) { Text("Edit") }; Button(onClick = { detail.workingItemId?.let { nav.navigate("inspection/$it") } }, Modifier.weight(1f), enabled = detail.workingItemId != null) { Text("Start / resume") } } }
        item { SectionTitle("Service plans") }
        items(detail.plans) { plan ->
            AccentCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(plan.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); StatusChip(if (plan.isOverdue) "Overdue" else "Due soon", urgency = plan.isOverdue) }
                Text("${plan.reference} · ${plan.interval}")
                Text("Due ${plan.dueDate}", fontWeight = FontWeight.Medium)
                Text(if (plan.state == "ACTIVE") "Current service remains due until explicitly fulfilled" else "Plan ${plan.state.lowercase()}", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { nav.navigate("plan/${plan.id}") }) { Text("Open plan") }
            }
        }
        item { Button(onClick = { nav.navigate("plan/new/${detail.id}") }, modifier = Modifier.fillMaxWidth().testTag("add-service-plan")) { Text("Add service plan") }; SummaryRow("Equipment history", "Open") { nav.navigate("scope/History") }; Text("Private equipment notes", style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun InspectionScreen(draft: InspectionDraft, saveStatus: SaveStatus, focus: InspectionFocus?, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    val listState = rememberLazyListState()
    LaunchedEffect(draft.workItemId, focus) { focus?.let { target -> val index = when (target.kind) { CompletionBlockerKind.WORK_PERFORMED -> 1; CompletionBlockerKind.CHECKLIST_REVIEW -> 3 + draft.questions.size; CompletionBlockerKind.FINDING_DESCRIPTION -> 3 + draft.questions.indexOfFirst { it.snapshotItemId == target.questionId }.coerceAtLeast(0); else -> 0 }; listState.scrollToItem(index); viewModel.clearInspectionFocus() } }
    viewModel.state.collectAsState().value.pendingResponseTransition?.let { transition ->
        AlertDialog(
            onDismissRequest = viewModel::cancelResponseTransition,
            title = { Text("Discard saved response detail?") },
            text = { Text("Changing this answer will discard the ${transition.detailBeingDiscarded}.") },
            confirmButton = { TextButton(onClick = viewModel::confirmResponseTransition) { Text("Discard and change") } },
            dismissButton = { TextButton(onClick = viewModel::cancelResponseTransition) { Text("Cancel") } },
        )
    }
    LazyColumn(Modifier.testTag("inspection-list"), state=listState, contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("${draft.equipmentReference} · ${draft.equipmentName}", style = MaterialTheme.typography.titleMedium)
            Text("${draft.visitReference} · ${draft.siteName}", style = MaterialTheme.typography.bodyMedium)
            SaveStateBanner(saveStatus)
            Text("Due ${draft.dueDate} · ${draft.interval} · Checklist revision ${draft.templateRevision}", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            var work by rememberSaveable(draft.workItemId, draft.workPerformed) { mutableStateOf(draft.workPerformed) }
            Text("Work performed · Customer report", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            LongTextEditor(work, { work = it }, "Public work performed", false)
            Button(onClick = { viewModel.savePublicWork(draft.workItemId, work) }, enabled = saveStatus !is SaveStatus.Saving && work.trim() != draft.workPerformed, modifier = Modifier.fillMaxWidth()) { Text("Save work performed") }
            LabelledValue("Private — not in customer report", draft.privateInternalNote.ifBlank { "Not recorded" }, public = false)
        }
        item { SectionTitle("Inspection responses"); Text("Unanswered and Not checked are never treated as OK.") }
        items(draft.questions, key = { it.snapshotItemId }) { question -> QuestionBlock(question, saveStatus is SaveStatus.Saving, viewModel) }
        item {
            val required = draft.questions.count { it.required }; val complete = draft.questions.count { q -> q.required && when(q.responseType) { "STATUS" -> q.disposition != ResponseDisposition.NOT_CHECKED && !(q.disposition in setOf(ResponseDisposition.ISSUE_FOUND, ResponseDisposition.NOT_APPLICABLE) && q.reason.isNullOrBlank()); "NUMBER" -> q.disposition != ResponseDisposition.UNANSWERED && !(q.disposition == ResponseDisposition.NOT_APPLICABLE && q.reason.isNullOrBlank()) && !(q.disposition == ResponseDisposition.VALUE && !signedDecimal(q.numberValue.orEmpty())); else -> q.disposition != ResponseDisposition.UNANSWERED && !(q.disposition == ResponseDisposition.NOT_APPLICABLE && q.reason.isNullOrBlank()) } }
            val missingFinding = draft.questions.any { it.disposition == ResponseDisposition.ISSUE_FOUND && it.reason.isNullOrBlank() }
            StatusChip(if (draft.checklistReviewed) "Reviewed" else "Needs review", urgency = false); Text("Required complete $complete of $required")
            Button(onClick = { viewModel.markChecklistReviewed(draft.workItemId) }, enabled = !draft.checklistReviewed && complete == required && !missingFinding && saveStatus !is SaveStatus.Saving, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Mark checklist reviewed" }) { Text("Mark checklist reviewed") }
            Text("Reviewed describes the checklist workflow, not equipment safety or obligation fulfillment.", style = MaterialTheme.typography.bodyMedium)
        }
        item { OutlinedButton(onClick = { nav.navigate("field/${draft.workItemId}") }, modifier = Modifier.fillMaxWidth().testTag("open-field-evidence")) { Text("Parts and photographs") }; Button(onClick = { nav.navigate("review/${draft.visitId}") }, modifier = Modifier.fillMaxWidth().testTag("open-completion-review"), enabled = saveStatus !is SaveStatus.Saving && saveStatus !is SaveStatus.Failed) { Text("Review completion") } }
    }
}

@Composable
private fun QuestionBlock(question: InspectionQuestion, saving: Boolean, viewModel: ServiceLoopViewModel) {
    AccentCard {
        Text("${question.position}. ${question.label}", style = MaterialTheme.typography.titleMedium)
        Text("${question.responseType.lowercase().replaceFirstChar { it.uppercase() }} · ${if (question.required) "Required" else "Optional"}", style = MaterialTheme.typography.bodySmall)
        when (question.responseType) {
            "STATUS" -> listOf(ResponseDisposition.OK to "OK", ResponseDisposition.ISSUE_FOUND to "Issue found", ResponseDisposition.NOT_APPLICABLE to "Not applicable", ResponseDisposition.NOT_CHECKED to "Not checked").forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().testTag("response-${question.snapshotItemId}-${value.name}").selectable(selected = question.disposition == value, enabled = !saving, onClick = { viewModel.requestResponseChange(question.snapshotItemId, value, reason = if (value == ResponseDisposition.NOT_APPLICABLE) "Not applicable during this visit" else null) }).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = question.disposition == value, onClick = null); Text(label) }
            }
            else -> ValueQuestion(question, saving, viewModel)
        }
        if (question.disposition != ResponseDisposition.ISSUE_FOUND) question.reason?.takeIf { it.isNotBlank() }?.let { Text("Reason: $it", style = MaterialTheme.typography.bodyMedium) }
        if (question.disposition == ResponseDisposition.ISSUE_FOUND) {
            InlineFindingEditor(question, saving, viewModel)
        }
    }
}

@Composable
private fun InlineFindingEditor(question: InspectionQuestion, saving: Boolean, viewModel: ServiceLoopViewModel) {
    var text by rememberSaveable(question.snapshotItemId, question.reason) { mutableStateOf(question.reason.orEmpty()) }
    val changed = text != question.reason.orEmpty()
    Surface(color = LocalServiceLoopColors.current.errorTint, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Finding details · Customer report", color = LocalServiceLoopColors.current.errorInk, fontWeight = FontWeight.Medium)
            LongTextEditor(text,{text=it},"Public finding description",false)
            Button(
                onClick = { viewModel.requestResponseChange(question.snapshotItemId, ResponseDisposition.ISSUE_FOUND, reason = text) },
                enabled = !saving && changed,
                modifier = Modifier.fillMaxWidth().testTag("finding-save-${question.snapshotItemId}"),
            ) { Text("Save finding") }
        }
    }
}

@Composable
private fun ValueQuestion(question: InspectionQuestion, saving: Boolean, viewModel: ServiceLoopViewModel) {
    var value by remember(question.snapshotItemId, question.textValue, question.numberValue) { mutableStateOf(question.textValue ?: question.numberValue.orEmpty()) }
    val invalidNumber = question.responseType == "NUMBER" && value.isNotBlank() && !signedDecimal(value)
    OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(if (question.responseType == "NUMBER") "Recorded value" else "Response") }, supportingText = { Text(if (invalidNumber) "Enter a signed decimal, for example -12.5" else question.unit.orEmpty()) }, isError = invalidNumber, enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("value-${question.snapshotItemId}"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { viewModel.requestResponseChange(question.snapshotItemId, ResponseDisposition.VALUE, value = value) }, enabled = !saving && value.isNotBlank() && !invalidNumber, modifier = Modifier.weight(1f).testTag("value-save-${question.snapshotItemId}")) { Text("Save response") }
        OutlinedButton(onClick = { viewModel.requestResponseChange(question.snapshotItemId, ResponseDisposition.NOT_APPLICABLE, reason = "Not applicable during this visit") }, enabled = !saving, modifier = Modifier.weight(1f)) { Text("Not applicable") }
    }
    if (question.disposition == ResponseDisposition.UNANSWERED) Text("Not recorded", color = LocalServiceLoopColors.current.errorInk)
}

@Composable
private fun CompletionReviewScreen(visitId: String, lines: List<CompletionLine>, profile: BusinessProfile?, state: UiState, padding: PaddingValues, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    LazyColumn(Modifier.padding(padding).testTag("completion-review-list"), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Outcome and fulfillment are separate decisions.", style = MaterialTheme.typography.titleMedium); Text("Only explicitly fulfilled recurring work advances its obligation.") }
        if (state.visitReportIdentity?.ready != true) item { AccentCard { Text("This visit needs a captured report identity before finalization."); if (profile?.ready == true) Button(onClick = { viewModel.refreshVisitReportIdentity(visitId) }, modifier = Modifier.fillMaxWidth().testTag("capture-report-identity")) { Text("Use current business identity for this visit") } else Button(onClick = { nav.navigate("business-profile") }, modifier = Modifier.fillMaxWidth()) { Text("Set business identity") } } }
        else item { AccentCard { Text("Report identity: ${state.visitReportIdentity.businessName} · ${state.visitReportIdentity.technicianName}"); OutlinedButton(onClick = { viewModel.refreshVisitReportIdentity(visitId) }, modifier = Modifier.fillMaxWidth().testTag("refresh-report-identity")) { Text("Refresh report identity from current profile") } } }
        items(lines) { line -> CompletionLineCard(visitId, line, state.saveStatus is SaveStatus.Saving, viewModel, nav) }
        item { Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) { Column(Modifier.padding(16.dp)) { Text("Customer report review", style = MaterialTheme.typography.titleMedium); Text("Public work, explicit unanswered responses, due effects, and findings will be included. Private notes stay excluded.") } } }
        state.error?.let { message -> item { Text("Finalization failed — $message", color = MaterialTheme.colorScheme.error) } }
        item { Button(onClick = { viewModel.finalizeVisit(visitId) }, enabled = !state.finalizing && state.visitReportIdentity?.ready == true && lines.isNotEmpty() && lines.all { it.blockers.isEmpty() }, modifier = Modifier.fillMaxWidth().testTag("finalize-record").semantics { contentDescription = "Finalize record" }) { Text(if (state.finalizing) "Finalizing…" else "Finalize record") } }
    }
}

@Composable
private fun CompletionLineCard(visitId: String, line: CompletionLine, saving: Boolean, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    AccentCard {
        Text("${line.equipmentReference} · ${line.equipmentName}", style = MaterialTheme.typography.labelLarge)
        Text(line.serviceName, style = MaterialTheme.typography.titleMedium)
        Text("Outcome", style = MaterialTheme.typography.labelLarge)
        listOf("PERFORMED" to "Performed", "PARTLY_PERFORMED" to "Partly performed", "NOT_PERFORMED" to "Not performed").forEach { (value, label) ->
            Row(Modifier.fillMaxWidth().testTag("outcome-${line.workItemId}-$value").selectable(line.outcome == value, enabled = !saving) { viewModel.saveCompletion(line.workItemId, value, line.fulfillsCurrentObligation, line.notPerformedReason, line.confirmedNextDueDate, line.nextDueDateCalculated, line.nextDueOverrideReason, visitId) }.semantics { contentDescription = "Outcome $label" }, verticalAlignment = Alignment.CenterVertically) { RadioButton(line.outcome == value, null); Text(label) }
        }
        if (line.outcome == "NOT_PERFORMED") {
            var reason by rememberSaveable(line.workItemId, line.notPerformedReason) { mutableStateOf(line.notPerformedReason.orEmpty()) }
            OutlinedTextField(reason, { reason = it }, label = { Text("Not performed reason") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { viewModel.saveCompletion(line.workItemId, line.outcome, false, reason, null, null, null, visitId) }, enabled = !saving && reason.isNotBlank() && reason != line.notPerformedReason, modifier = Modifier.fillMaxWidth()) { Text("Save reason") }
        }
        when (line.fulfillmentEligibility) {
            FulfillmentEligibility.ELIGIBLE -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = "Fulfills current obligation" }) { Checkbox(checked = line.fulfillsCurrentObligation, enabled = !saving, onCheckedChange = { viewModel.saveCompletion(line.workItemId, line.outcome, it, line.notPerformedReason, null, null, null, visitId) }, modifier = Modifier.testTag("fulfills-${line.workItemId}")); Column { Text("Fulfills current obligation", fontWeight = FontWeight.Medium); Text(if (line.fulfillsCurrentObligation) "Explicitly selected" else "Eligible, not selected — outstanding obligation is preserved", style = MaterialTheme.typography.bodySmall) } }
            FulfillmentEligibility.HISTORY_ONLY -> Text("Recurring historical work · History only — no current obligation will be fulfilled.", style = MaterialTheme.typography.bodyMedium)
            FulfillmentEligibility.NO_CURRENT_OBLIGATION -> Text("Fulfillment unavailable — one-off work has no recurring obligation to fulfill.", style = MaterialTheme.typography.bodyMedium)
            FulfillmentEligibility.OUTCOME_INELIGIBLE -> Text("Fulfillment unavailable — ${line.outcome?.replace('_', ' ')?.lowercase()} work cannot fulfill the current obligation.", style = MaterialTheme.typography.bodyMedium)
            FulfillmentEligibility.CHECKLIST_NOT_REVIEWED -> Text("Fulfillment unavailable — review the assigned checklist first.", style = MaterialTheme.typography.bodyMedium)
            FulfillmentEligibility.PLAN_INELIGIBLE -> Text("Fulfillment unavailable — this plan is no longer active.", style = MaterialTheme.typography.bodyMedium)
            FulfillmentEligibility.CURRENT_OBLIGATION_CHANGED -> Text("Fulfillment unavailable — current service obligation changed. Review this work before finalizing.", style = MaterialTheme.typography.bodyMedium)
        }
        when {
            line.fulfillmentEligibility == FulfillmentEligibility.HISTORY_ONLY -> Text("History only — current recurring due date is unchanged")
            line.fulfillmentEligibility == FulfillmentEligibility.NO_CURRENT_OBLIGATION -> Text("No recurring due date changes")
            line.fulfillsCurrentObligation && line.dueDate != null && line.proposedNextDueDate != null -> Text("Due before ${line.dueDate} → Proposed next due ${line.proposedNextDueDate}")
            line.fulfillsCurrentObligation -> Text("Recurring due-date proposal unavailable", color = LocalServiceLoopColors.current.urgencyInk)
            line.dueDate != null -> Text("Remains due ${line.dueDate}", color = LocalServiceLoopColors.current.urgencyInk)
            else -> Text("Current recurring obligation remains outstanding", color = LocalServiceLoopColors.current.urgencyInk)
        }
        if (line.fulfillsCurrentObligation && line.calculatedNextDueDate != null) {
            Text("Calculated next due ${line.calculatedNextDueDate}")
            Button(onClick = { viewModel.saveCompletion(line.workItemId, line.outcome, true, line.notPerformedReason, line.calculatedNextDueDate, true, null, visitId) }, enabled = !saving && line.confirmedNextDueDate != line.calculatedNextDueDate, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Use calculated date" }) { Text("Use calculated date") }
            var date by rememberSaveable(line.workItemId, line.confirmedNextDueDate) { mutableStateOf(line.confirmedNextDueDate.orEmpty()) }
            var overrideReason by rememberSaveable(line.workItemId, line.nextDueOverrideReason) { mutableStateOf(line.nextDueOverrideReason.orEmpty()) }
            OutlinedTextField(date, { date = it }, label = { Text("Override next due (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(overrideReason, { overrideReason = it }, label = { Text("Override reason") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { viewModel.saveCompletion(line.workItemId, line.outcome, true, line.notPerformedReason, date, false, overrideReason, visitId) }, enabled = !saving && date.isNotBlank() && overrideReason.isNotBlank() && date != line.calculatedNextDueDate, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Override next due" }) { Text("Save override") }
        }
        line.blockers.forEach { blocker ->
            val actionable = blocker.kind in setOf(CompletionBlockerKind.WORK_PERFORMED, CompletionBlockerKind.CHECKLIST_REVIEW, CompletionBlockerKind.FINDING_DESCRIPTION)
            if (actionable) TextButton(onClick = { viewModel.focusInspection(blocker.kind, blocker.questionId); nav.navigate("inspection/${line.workItemId}") }, modifier = Modifier.fillMaxWidth().testTag("completion-blocker-${line.workItemId}-${blocker.kind}-${blocker.questionId.orEmpty()}")) { Text(blocker.message, color = MaterialTheme.colorScheme.error) }
            else Text(blocker.message, color = MaterialTheme.colorScheme.error)
        }
        if (line.outcome in setOf("PARTLY_PERFORMED", "NOT_PERFORMED") && !line.checklistReviewed) Text("Checklist incomplete — unrecorded items will remain explicit in the final record.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (line.workPerformed.isNotBlank()) Text(line.workPerformed, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SettingsScreen(padding: PaddingValues, nav: NavHostController) {
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Settings"); SummaryRow("Business and report identity", "Open") { nav.navigate("business-profile") } }
        item { SummaryRow("Inspection templates", "Open") { nav.navigate("template/list") }; SummaryRow("Reminders · foundation", "Later") { nav.navigate("scope/Reminders") }; SummaryRow("Data and recovery · foundation", "Later") { nav.navigate("scope/Data and recovery") } }
    }
}

@Composable
private fun BusinessProfileScreen(profile: BusinessProfile?, saveStatus: SaveStatus, padding: PaddingValues, viewModel: ServiceLoopViewModel) {
    val zone = profile?.zoneId ?: ZoneId.systemDefault().id
    var business by rememberSaveable(profile) { mutableStateOf(profile?.businessName.orEmpty()) }; var technician by rememberSaveable(profile) { mutableStateOf(profile?.technicianName.orEmpty()) }
    var phone by rememberSaveable(profile) { mutableStateOf(profile?.phone.orEmpty()) }; var email by rememberSaveable(profile) { mutableStateOf(profile?.email.orEmpty()) }; var address by rememberSaveable(profile) { mutableStateOf(profile?.postalAddress.orEmpty()) }
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("These details are frozen into each finalized record."); SaveStateBanner(saveStatus) }
        item { OutlinedTextField(business, { business = it }, label = { Text("Business/display name · Required") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(technician, { technician = it }, label = { Text("Technician name · Required") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(address, { address = it }, label = { Text("Postal address") }, minLines = 2, modifier = Modifier.fillMaxWidth()) }
        item { LabelledValue("Business time zone", zone, true); Button(onClick = { viewModel.saveBusinessProfile(BusinessProfile(business, technician, phone, email, address, zone)) }, enabled = business.isNotBlank() && technician.isNotBlank() && saveStatus !is SaveStatus.Saving, modifier = Modifier.fillMaxWidth()) { Text("Save profile") } }
    }
}

@Composable
private fun FinalRecordScreen(detail: FinalRecordDetail?, generating: Boolean, error: String?, padding: PaddingValues, viewModel: ServiceLoopViewModel, nav: NavHostController) {
    if (detail == null) return HonestPlaceholder(padding, "Reading final service record")
    val report = detail.public
    val context = LocalContext.current
    val reportPresent = detail.report?.let { File(context.filesDir, it.relativePath).isFile } == true
    LazyColumn(Modifier.padding(padding).testTag("final-record-list"), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionTitle("${report.visitReference} · Finalized"); Text("Service date ${report.actualServiceDate} · Revision ${report.revisionNumber}"); Text("Recorded on ${formatRecordedOn(report.recordedAtEpochMillis)}"); Text("${report.customerReference.orEmpty()} · ${report.customerName}\n${report.siteReference.orEmpty()} · ${report.siteName}\n${report.siteAddress.orEmpty()}") }
        items(report.lines) { line -> AccentCard { Text("${line.equipmentReference} · ${line.equipmentName}", style = MaterialTheme.typography.titleMedium); Text("${line.planReference?.let { "$it · " }.orEmpty()}${line.serviceName}"); Text("Outcome: ${line.outcome.replace('_', ' ')}"); line.publicWorkNote?.let { Text(it) }; line.notPerformedReason?.let { Text("Reason: $it") }; Text(dueEffect(line)); line.parts.forEach { Text("Part: ${it.description} · ${it.quantity} ${it.unit}") }; line.photos.forEachIndexed { index, photo -> Text("Photograph ${index + 1}${photo.caption?.let { caption -> ": $caption" }.orEmpty()}") }; line.checklist.forEach { Text("${it.position}. ${it.label}: ${it.value ?: it.disposition.replace('_', ' ')}${it.reason?.let { reason -> " — $reason" }.orEmpty()}") } } }
        if (detail.privateNotes.isNotEmpty()) item { AccentCard { Text("Internal / Not in customer report", style = MaterialTheme.typography.titleMedium); detail.privateNotes.forEach { Text(it) } } }
        item {
            Text("Customer PDF", style = MaterialTheme.typography.titleMedium)
            Text(when (detail.report?.status) { "READY" -> if (reportPresent) "Ready · Version 1 · ${detail.report.byteSize} bytes" else "File missing · Version 1"; "FAILED" -> "Generation failed"; "GENERATING" -> "Generating…"; else -> "Not generated" })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (detail.report?.status == "READY") Button(onClick = { nav.navigate(if (reportPresent) "report/${report.recordId}" else "report-text/${report.recordId}") }, modifier = Modifier.fillMaxWidth()) { Text(if (reportPresent) "View report" else "View report text") }
            else Button(onClick = { viewModel.generateReport(report.recordId) }, enabled = !generating, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Generate customer PDF" }) { Text(if (generating) "Generating…" else if (detail.report?.status == "FAILED") "Retry customer PDF" else "Generate customer PDF") }
        }
    }
}

@Composable
private fun ReportPreviewScreen(detail: FinalRecordDetail?, padding: PaddingValues, initialTextView: Boolean) {
    val rendition = detail?.report
    if (detail == null || rendition?.status != "READY") return HonestPlaceholder(padding, "Report file is not ready")
    val context = LocalContext.current; val file = remember(rendition.relativePath) { File(context.filesDir, rendition.relativePath) }
    var textView by rememberSaveable(rendition.id) { mutableStateOf(initialTextView || !file.isFile) }; var pageIndex by rememberSaveable { mutableStateOf(0) }; var bitmap by remember { mutableStateOf<Bitmap?>(null) }; var pageCount by remember { mutableStateOf(rendition.pageCount ?: 1) }; var missing by remember { mutableStateOf(!file.isFile) }
    LaunchedEffect(file, pageIndex, textView) {
        if (!textView && file.isFile) withContext(Dispatchers.IO) { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { renderer -> pageCount = renderer.pageCount; val page = renderer.openPage(pageIndex.coerceIn(0, renderer.pageCount - 1)); bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888).also { page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }; page.close() } } } else missing = !file.isFile
    }
    Column(Modifier.padding(padding).fillMaxSize()) {
    Text(if (missing) "File missing · Structured report remains available" else "PDF file ready", modifier = Modifier.fillMaxWidth().background(if(missing) LocalServiceLoopColors.current.errorTint else LocalServiceLoopColors.current.confirmedTint).padding(10.dp))
    LazyColumn(Modifier.weight(1f).testTag("report-preview-list"), contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text("${detail.public.visitReference} · Revision ${detail.public.revisionNumber} · PDF v${rendition.versionNumber} · ${rendition.id.take(8)}"); Text("Service ${detail.public.actualServiceDate} · ${if (missing) "Structured text only" else "Ready"}"); rendition.generatedAtEpochMillis?.let { Text("Generated ${Instant.ofEpochMilli(it)}") } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (!textView) Button(onClick = {}, modifier = Modifier.testTag("report-pdf-view").semantics { selected = true }) { Text("PDF view") } else OutlinedButton(onClick = { textView = false }, modifier = Modifier.testTag("report-pdf-view").semantics { selected = false }) { Text("PDF view") }; if (textView) Button(onClick = {}, modifier = Modifier.testTag("report-text-view").semantics { selected = true }) { Text("Text view") } else OutlinedButton(onClick = { textView = true }, modifier = Modifier.testTag("report-text-view").semantics { selected = false }) { Text("Text view") } } }
        if (textView) item { StructuredReportText(detail) }
        else if (missing) item { Text("File missing", color = MaterialTheme.colorScheme.error) }
        else { item { bitmap?.let { Image(it.asImageBitmap(), "Rendered customer report page ${pageIndex + 1}", Modifier.fillMaxWidth()) } }; item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { OutlinedButton(onClick = { pageIndex-- }, enabled = pageIndex > 0) { Text("Previous page") }; Text("Page ${pageIndex + 1} of $pageCount"); OutlinedButton(onClick = { pageIndex++ }, enabled = pageIndex + 1 < pageCount) { Text("Next page") } } } }
        item { Button(onClick = { val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file); val intent = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); context.startActivity(Intent.createChooser(intent, "Share customer service record")) }, enabled = file.isFile, modifier = Modifier.fillMaxWidth().testTag("share-pdf").semantics { contentDescription = "Share PDF" }) { Text("Share PDF") }; Text("Sharing initiates the Android handoff; it does not prove delivery.", style = MaterialTheme.typography.bodySmall) }
    }
    }
}

@Composable
private fun StructuredReportText(detail: FinalRecordDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { val r = detail.public; Text(r.businessName, style = MaterialTheme.typography.titleLarge); Text("Service record ${r.visitReference} · Revision ${r.revisionNumber}"); Text("Service date ${r.actualServiceDate}"); Text("Technician ${r.technicianName}\n${r.businessContact}"); Text("${r.customerReference.orEmpty()} · ${r.customerName}\n${r.siteReference.orEmpty()} · ${r.siteName}\n${r.siteAddress.orEmpty()}"); r.lines.forEach { line -> Text("${line.equipmentReference} · ${line.equipmentName}", style = MaterialTheme.typography.titleMedium); Text(line.equipmentIdentification); Text("${line.planReference?.let { "$it · " }.orEmpty()}${line.serviceName} — ${line.outcome.replace('_', ' ')}"); line.publicWorkNote?.let { Text(it) }; line.notPerformedReason?.let { Text("Reason: $it") }; Text(dueEffect(line)); line.parts.forEach { Text("Part: ${it.description} · ${it.quantity} ${it.unit}") }; line.photos.forEachIndexed { index, photo -> Text("Photograph ${index + 1}${photo.caption?.let { caption -> ": $caption" }.orEmpty()}") }; line.checklist.forEach { Text("${it.position}. ${it.label}: ${it.value ?: it.disposition.replace('_', ' ')}${it.unit?.let { unit -> " $unit" }.orEmpty()}${it.reason?.let { reason -> " — $reason" }.orEmpty()}") } } }
}

@Composable
private fun SaveStateBanner(status: SaveStatus) {
    val colors = LocalServiceLoopColors.current
    val (text, background, foreground) = when (status) {
        SaveStatus.Idle -> Triple("Not saved", colors.errorTint, colors.errorInk)
        SaveStatus.Saving -> Triple("Saving…", colors.workflowTint, colors.workflowInk)
        is SaveStatus.Saved -> Triple("Saved on this device · ${formatTime(status.atEpochMillis)}", colors.confirmedTint, colors.confirmedInk)
        is SaveStatus.Failed -> Triple("Not saved — ${status.message}. Last saved ${formatTime(status.lastSavedAtEpochMillis)}", colors.errorTint, colors.errorInk)
    }
    Text(text, color = foreground, modifier = Modifier.fillMaxWidth().background(background, MaterialTheme.shapes.small).padding(12.dp), style = MaterialTheme.typography.labelLarge)
}

@Composable private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })

@Composable private fun AccentCard(content: @Composable ColumnScope.() -> Unit) = Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }

@Composable private fun SummaryRow(text: String, action: String, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) { Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(text, Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); Text(action, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium) } } }

@Composable private fun StatusChip(text: String, urgency: Boolean) { val colors = LocalServiceLoopColors.current; Text(text, color = if (urgency) colors.urgencyInk else colors.workflowInk, modifier = Modifier.background(if (urgency) colors.urgencyTint else colors.workflowTint, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium) }

@Composable private fun LabelledValue(label: String, value: String, public: Boolean) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (public) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant); Text(value); Spacer(Modifier.height(8.dp)); HorizontalDivider() }

@Composable private fun HonestPlaceholder(padding: PaddingValues, message: String) { Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(message, style = MaterialTheme.typography.titleMedium) } }

private fun formatTime(epochMillis: Long?): String = epochMillis?.let { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it)) } ?: "not yet"
private fun formatRecordedOn(epochMillis: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))

private fun signedDecimal(value: String): Boolean = Regex("^[+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)$").matches(value.trim())

private fun dueEffect(line: com.v16studio.serviceloop.domain.PublicWorkLine): String = when {
    line.historyOnly -> "Recurring historical work · History only — no current due-date effect"
    !line.isRecurringPlan -> "One-off work — no recurring due date effect"
    line.fulfilledObligation -> "Due ${line.oldDueDate} → ${line.nextDueDate}"
    else -> "Current service remains due ${line.oldDueDate}"
}
