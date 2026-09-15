package com.v16studio.serviceloop.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopButtonAdapter as Button
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopPrimaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopTextButtonAdapter as TextButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import com.v16studio.serviceloop.ui.theme.AppearancePreferences

private const val HOME = "home"
private const val WORK_ROUTE = "work?tab={tab}&filter={filter}"
private const val CUSTOMERS = "customers"

internal enum class RootDestination(val route: String, val label: String) {
    HOME("home", "Home"),
    WORK("work", "Work"),
    CUSTOMERS("customers", "Register"),
}

internal enum class WorkTab(val label: String) {
    DUE_SERVICES("Due services"),
    VISITS("Visits"),
    FOLLOW_UPS("Follow-ups"),
}

/** The application route graph. Screens remain ordinary feature composables. */
@Composable
internal fun ServiceLoopNavGraph(
    nav: NavHostController,
    state: UiState,
    viewModel: ServiceLoopViewModel,
    appearanceController: AppearancePreferences,
    incomingWorkPackage: String?,
    incomingWorkPackageEvent: Int,
    incomingInspectionTemplates: String?,
    incomingInspectionTemplatesEvent: Int,
) {
    val context = LocalContext.current
    val incomingRole = remember(incomingWorkPackage, incomingWorkPackageEvent) { incomingWorkPackage?.let { context.teamRole() } }
    val incomingTemplateRole = remember(incomingInspectionTemplates, incomingInspectionTemplatesEvent) { incomingInspectionTemplates?.let { context.teamRole() } }
    var showExternalRoleDialog by remember(incomingWorkPackage, incomingWorkPackageEvent, incomingInspectionTemplates, incomingInspectionTemplatesEvent) {
        mutableStateOf(
            (incomingWorkPackage != null && incomingRole != TeamRole.MEMBER) ||
                (incomingInspectionTemplates != null && incomingTemplateRole !in setOf(TeamRole.MEMBER, TeamRole.COORDINATOR)),
        )
    }
    LaunchedEffect(incomingWorkPackage, incomingWorkPackageEvent, state.restrictedRecoveryState) {
        if (!state.restrictedRecoveryState && incomingWorkPackage != null && incomingRole == TeamRole.MEMBER) nav.navigate("dispatch/import") { launchSingleTop = true }
    }
    LaunchedEffect(incomingInspectionTemplates, incomingInspectionTemplatesEvent, state.restrictedRecoveryState) {
        if (!state.restrictedRecoveryState && incomingInspectionTemplates != null && incomingTemplateRole in setOf(TeamRole.MEMBER, TeamRole.COORDINATOR)) nav.navigate("template/list") { launchSingleTop = true }
    }
    NavHost(
        navController = nav,
        startDestination = if (state.restrictedRecoveryState) "data-recovery" else HOME,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(HOME) {
            LaunchedEffect(Unit) { viewModel.refreshRootDataNonBlocking() }
            RootScaffold(nav, RootDestination.HOME) { padding ->
                ScreenState(state.loading && !state.rootDataReady, state.error.takeUnless { state.rootDataReady }, padding, "root-home", state.rootRefreshError, viewModel::refreshRootDataNonBlocking) { HomeScreen(state, state.home, state.equipmentList, state.visits, state.attention, nav, viewModel) }
            }
        }
        composable(
            WORK_ROUTE,
            arguments = listOf(
                navArgument("tab") { type = NavType.StringType; defaultValue = WorkTab.DUE_SERVICES.name },
                navArgument("filter") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val requested = runCatching { WorkTab.valueOf(entry.arguments?.getString("tab").orEmpty()) }.getOrDefault(WorkTab.DUE_SERVICES)
            val contextualFilter = entry.arguments?.getString("filter")
            var workTab by androidx.compose.runtime.saveable.rememberSaveable(requested, contextualFilter) { mutableStateOf(requested) }
            LaunchedEffect(Unit) { viewModel.refreshRootDataNonBlocking(); viewModel.loadVisits(); viewModel.loadFollowUps() }
            RootScaffold(nav, RootDestination.WORK) { padding ->
                ScreenState(state.loading && !state.rootDataReady, state.error.takeUnless { state.rootDataReady }, padding, "root-work", state.rootRefreshError, viewModel::refreshRootDataNonBlocking) {
                    WorkScreen(state, nav, workTab, viewModel, contextualFilter) { workTab = it }
                }
            }
        }
        composable(CUSTOMERS) {
            var equipmentMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) { viewModel.refreshRootDataNonBlocking() }
            RootScaffold(nav, RootDestination.CUSTOMERS) { padding ->
                ScreenState(state.loading && !state.rootDataReady, state.error.takeUnless { state.rootDataReady }, padding, "root-customers", state.rootRefreshError, viewModel::refreshRootDataNonBlocking) { CustomersScreen(state.customerList, state.siteList, state.equipmentList, nav) }
            }
        }
        composable("equipment/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            LaunchedEffect(id) { viewModel.loadEquipment(id) }
            DetailScaffold("Equipment", nav) { padding ->
                ScreenState(state.loading, state.error, padding) { state.equipment?.let { EquipmentScreen(it, nav, state.businessDate, state.home?.dueSoonHorizonDays ?: 14, viewModel) } }
            }
        }
        composable("customer/new") { DetailScaffold("Add customer", nav) { CustomerEditorScreen(null, it, state, viewModel, nav) } }
        composable("customer/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadCustomer(id)}; DetailScaffold("Customer",nav){CustomerDetailScreen(state.customer,it,nav,viewModel)} }
        composable("customer/edit/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadCustomer(id)}; DetailScaffold("Edit customer",nav){CustomerEditorScreen(state.customer,it,state,viewModel,nav)} }
        composable("site/new/{customerId}") { entry -> val id=entry.arguments?.getString("customerId"); DetailScaffold("Add site",nav){SiteEditorScreen(id,null,it,state,viewModel,nav)} }
        composable("site/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadSite(id)}; DetailScaffold("Site",nav){SiteDetailScreen(state.site,it,nav,viewModel)} }
        composable("site/edit/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadSite(id)}; DetailScaffold("Edit site",nav){SiteEditorScreen(null,state.site,it,state,viewModel,nav)} }
        composable("equipment/new/{siteId}") { entry -> val id=entry.arguments?.getString("siteId"); DetailScaffold("Add equipment",nav){EquipmentEditorScreen(id,null,it,state,viewModel,nav)} }
        composable("equipment/select-site") { LaunchedEffect(Unit){viewModel.loadVisitSetup()}; DetailScaffold("Choose equipment site",nav){EquipmentSiteSelectorScreen(state.visitSites,it,nav)} }
        composable("equipment/edit/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadEquipment(id)}; DetailScaffold("Edit equipment",nav){EquipmentEditorScreen(null,state.equipment,it,state,viewModel,nav)} }
        composable("plan/new/{equipmentId}") { entry -> val id=entry.arguments?.getString("equipmentId"); LaunchedEffect(Unit){viewModel.loadTemplates()}; DetailScaffold("Add service plan",nav){PlanEditorScreen(id,null,state.templates,it,state,viewModel,nav)} }
        composable("plan/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadPlan(id)}; DetailScaffold("Service plan",nav){PlanDetailScreen(state.plan,it,nav)} }
        composable("plan/edit/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadPlan(id)}; DetailScaffold("Edit service plan",nav){PlanEditorScreen(null,state.plan,state.templates,it,state,viewModel,nav)} }
        composable("template/list") { LaunchedEffect(Unit){viewModel.loadTemplates()}; DetailScaffold("Inspection templates",nav){TemplateListScreen(state.templates,it,nav,viewModel,incomingInspectionTemplates)} }
        composable("template/new") { DetailScaffold("Create template",nav){TemplateEditorScreen(null,it,state,viewModel,nav)} }
        composable("template/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadTemplate(id)}; DetailScaffold("Inspection template",nav){TemplateDetailScreen(state.template,it,nav)} }
        composable("template/edit/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadTemplate(id)}; DetailScaffold("New template revision",nav){TemplateEditorScreen(state.template,it,state,viewModel,nav)} }
        composable("visit/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadVisit(id)}; DetailScaffold("Visit",nav){VisitDetailScreen(state.visit,it,state,viewModel,nav)} }
        composable("visit/new") { entry -> val ids=remember(entry){nav.previousBackStackEntry?.savedStateHandle?.remove<ArrayList<String>>("visit-setup-plan-ids")?.toList().orEmpty()}; LaunchedEffect(Unit){viewModel.loadVisitSetup()}; DetailScaffold("Create visit",nav){NewVisitScreen(state.visitSites,state.dueServices,it,state,viewModel,nav,ids)} }
        composable("visit/new/{planId}") { entry -> val id=entry.arguments?.getString("planId").orEmpty(); LaunchedEffect(id){viewModel.loadVisitSetup()}; DetailScaffold("Create visit",nav){NewVisitScreen(state.visitSites,state.dueServices,it,state,viewModel,nav,listOf(id))} }
        composable("field/{workItemId}") { entry -> val id=entry.arguments?.getString("workItemId").orEmpty(); LaunchedEffect(id){viewModel.loadFieldEvidence(id)}; DetailScaffold("Parts and photographs",nav){FieldEvidenceScreen(id,state,it,viewModel,nav)} }
        composable("follow-up/list") { LaunchedEffect(Unit){viewModel.loadFollowUps()}; DetailScaffold("Follow-ups",nav){FollowUpListScreen(state.followUps,it,nav)} }
        composable("follow-up/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadFollowUp(id)}; DetailScaffold("Follow-up",nav){FollowUpDetailScreen(state.followUp,it,state,viewModel,nav)} }
        composable("follow-up/new/{customerId}") { entry -> val id=entry.arguments?.getString("customerId").orEmpty(); DetailScaffold("Add follow-up",nav){FollowUpEditorScreen(id,it,state,viewModel,nav)} }
        composable("contact/new/{customerId}") { entry -> val id=entry.arguments?.getString("customerId").orEmpty(); DetailScaffold("Record contact",nav){ContactNoteEditorScreen(id,it,state,viewModel,nav)} }
        composable("contact/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadContactNote(id)}; DetailScaffold("Contact note",nav){padding -> ContactNoteScreen(state.contactNote,padding)} }
        composable("search") { DetailScaffold("Search",nav){SearchScreen(state.searchResults,it,viewModel,nav)} }
        composable("inspection/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            LaunchedEffect(id) { viewModel.loadInspection(id) }
            DetailScaffold("Service", nav) { padding ->
                ScreenState(state.loading, state.error, padding) { state.inspection?.let { draft ->
                    ServiceScreen(draft, state.serviceProgress, state.saveStatus, state.inspectionFocus, viewModel, nav, PaddingValues(), LocalDetailBackInterceptor.current)
                } }
            }
        }
        composable("review/{visitId}") { entry ->
            val visitId = entry.arguments?.getString("visitId").orEmpty()
            LaunchedEffect(visitId) { viewModel.loadCompletion(visitId) }
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner, visitId) { val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.loadCompletion(visitId) }; lifecycleOwner.lifecycle.addObserver(observer); onDispose { lifecycleOwner.lifecycle.removeObserver(observer); viewModel.clearCompletionContext(visitId) } }
            LaunchedEffect(state.finalizedRecordId) { state.finalizedRecordId?.let { recordId -> viewModel.consumeFinalizedNavigation(); nav.navigate("record/$recordId") { popUpTo("review/{visitId}") { inclusive = true } } } }
            DetailScaffold("Review visit", nav) { padding -> CompletionReviewScreen(visitId, state.completionLines, state.businessProfile, state, padding, viewModel, nav) }
        }
        composable("settings") { LaunchedEffect(Unit) { viewModel.loadReminderSettings() }; DetailScaffold("Settings", nav) { padding -> SettingsScreen(state, padding, nav, appearanceController) } }
        composable("work/{workItemId}/link-equipment") { entry -> val id = entry.arguments?.getString("workItemId").orEmpty(); LaunchedEffect(id) { viewModel.loadEquipmentLinkContext(id) }; DetailScaffold("Link equipment", nav) { padding -> EquipmentLinkScreen(id, state.equipmentLinkContext, padding, state, viewModel, nav) } }
        composable("appearance") { DetailScaffold("Appearance", nav) { padding -> AppearanceSettingsScreen(padding, appearanceController) } }
        composable("reminders") { LaunchedEffect(Unit) { viewModel.loadReminderSettings() }; DetailScaffold("Reminders", nav) { padding -> ReminderSettingsScreen(state, padding, viewModel, nav) } }
        composable("calendar") { LaunchedEffect(Unit) { viewModel.loadCalendarSettings() }; DetailScaffold("Calendar integration", nav) { padding -> CalendarSettingsScreen(state, padding, viewModel) } }
        composable("dispatch/settings") { DetailScaffold("Team role settings",nav){DispatchSettings(it,nav)} }
        composable("dispatch/technicians") { DetailScaffold("Technicians",nav){DispatchTechniciansScreen(it)} }
        composable("dispatch/teams") { DetailScaffold("Teams",nav){DispatchTeamsScreen(it)} }
        composable("dispatch/create") {
            DetailScaffold(
                "Outbox",
                nav,
                topAction = {
                    ServiceLoopPrimaryButton(
                        "New visit",
                        { nav.navigate("dispatch/visit/new") },
                        Modifier.testTag("dispatch-new-visit"),
                        leadingIcon = { com.v16studio.serviceloop.ui.icons.ServiceLoopIcon(com.v16studio.serviceloop.ui.icons.ServiceLoopIcons.Add, null, Modifier.size(ServiceLoopUiTokens.Size.icon)) },
                    )
                },
            ) { DispatchOutboxScreen(it, nav, state.businessDate, showEmbeddedTopAction = false) }
        }
        composable("dispatch/visit/new") { DetailScaffold("New dispatch visit",nav){DispatchVisitEditorScreen(it,nav,null,state.businessDate)} }
        composable("dispatch/visit/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); DetailScaffold("Dispatch visit",nav){DispatchVisitEditorScreen(it,nav,id,state.businessDate)} }
        composable("dispatch/export-review") { val ids=nav.previousBackStackEntry?.savedStateHandle?.get<ArrayList<String>>("dispatch-export-ids").orEmpty(); DetailScaffold("Review export",nav){DispatchExportReviewScreen(it,nav,ids)} }
        composable("dispatch/import") { DetailScaffold("Import work package",nav){ImportDispatchPackageScreen(it,nav,viewModel,incomingWorkPackage)} }
        composable("business-profile") { LaunchedEffect(Unit) { viewModel.loadBusinessProfile() }; DetailScaffold("Business and report identity", nav) { padding -> BusinessProfileScreen(state.businessProfile, state.businessProfileSaveStatus, padding, viewModel) } }
        composable("record/{id}") { entry -> val id = entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id) { viewModel.loadFinalRecord(id); viewModel.loadRecordVersions(id) }; DetailScaffold("Final service record", nav) { padding -> FinalRecordScreen(state.finalRecord, state.recordVersions, state.reportVersions, false, state.generatingReport, state.error, padding, viewModel, nav) } }
        composable("record-version/{id}/{revisionId}") { entry -> val recordId = entry.arguments?.getString("id").orEmpty(); val revisionId = entry.arguments?.getString("revisionId").orEmpty(); LaunchedEffect(recordId, revisionId) { viewModel.loadFinalRecordRevision(recordId, revisionId) }; DetailScaffold("Historical record revision", nav) { padding -> FinalRecordScreen(state.finalRecord, emptyList(), emptyList(), true, state.generatingReport, state.error, padding, viewModel, nav) } }
        composable("report-version/{id}/{revisionId}/{renditionId}") { entry -> val recordId = entry.arguments?.getString("id").orEmpty(); val revisionId = entry.arguments?.getString("revisionId").orEmpty(); val renditionId = entry.arguments?.getString("renditionId").orEmpty(); LaunchedEffect(recordId, revisionId, renditionId) { viewModel.loadFinalRecordRevision(recordId, revisionId, renditionId) }; DetailScaffold("Historical report rendition", nav) { padding -> ReportPreviewScreen(state.finalRecord, padding, initialTextView = false, historical = true, viewModel = viewModel) } }
        composable("report/{id}") { entry -> val id = entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id) { viewModel.loadFinalRecord(id) }; DetailScaffold("Customer report", nav) { padding -> ReportPreviewScreen(state.finalRecord, padding, initialTextView = false) } }
        composable("report-text/{id}") { entry -> val id = entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id) { viewModel.loadFinalRecord(id) }; DetailScaffold("Customer report", nav) { padding -> ReportPreviewScreen(state.finalRecord, padding, initialTextView = true) } }
        composable("history/global") { DetailScaffold("History", nav) { padding -> HistoryScreen(com.v16studio.serviceloop.domain.HistoryScope(com.v16studio.serviceloop.domain.HistoryScopeType.GLOBAL), state, padding, viewModel, nav) } }
        composable("history/{scope}/{id}") { entry -> val scope = runCatching { com.v16studio.serviceloop.domain.HistoryScopeType.valueOf(entry.arguments?.getString("scope").orEmpty()) }.getOrDefault(com.v16studio.serviceloop.domain.HistoryScopeType.GLOBAL); val id = entry.arguments?.getString("id"); DetailScaffold("History", nav) { padding -> HistoryScreen(com.v16studio.serviceloop.domain.HistoryScope(scope, id, "${scope.name.lowercase()} history"), state, padding, viewModel, nav) } }
        composable("correction/{recordId}") { entry -> val id = entry.arguments?.getString("recordId").orEmpty(); DetailScaffold("Correct service record", nav) { padding -> CorrectionScreen(id, state, padding, viewModel, nav) } }
        composable("lifecycle/{subject}/{id}/{action}") { entry -> val subject = entry.arguments?.getString("subject").orEmpty(); val id = entry.arguments?.getString("id").orEmpty(); val action = entry.arguments?.getString("action").orEmpty(); DetailScaffold("Lifecycle review", nav) { padding -> LifecycleScreen(subject, id, action, state, padding, viewModel, nav) } }
        composable("equipment/move/{id}") { entry -> val id = entry.arguments?.getString("id").orEmpty(); DetailScaffold("Move equipment", nav) { padding -> MoveEquipmentScreen(id, state, padding, viewModel, nav) } }
        composable("data-recovery") { DetailScaffold("Data and recovery", nav) { padding -> DataRecoveryScreen(state, padding, viewModel, nav) } }
        composable("backup/{mode}") { entry -> val mode = entry.arguments?.getString("mode").orEmpty(); DetailScaffold(if (mode == "create") "Create backup" else if (mode == "restore") "Restore backup" else "Inspect backup", nav) { padding -> BackupScreen(mode, state, padding, viewModel, nav) } }
        composable("csv/export") { DetailScaffold("Export readable CSV", nav) { padding -> CsvExportScreen(state, padding, viewModel) } }
        composable("csv/import") { DetailScaffold("Import directory CSV", nav) { padding -> CsvImportScreen(state, padding, viewModel, nav) } }
        composable("data/erase") { DetailScaffold("Erase local data", nav) { padding -> EraseScreen(state, padding, viewModel, nav) } }
        composable("change/{id}") { entry -> val id = entry.arguments?.getString("id"); DetailScaffold("Recorded change", nav) { padding -> val row = state.history.firstOrNull { it.id == id }; androidx.compose.foundation.layout.Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) { androidx.compose.material3.Text(row?.title.orEmpty(), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall); androidx.compose.material3.Text(row?.subtitle.orEmpty()); androidx.compose.material3.Text("This is a read-only historical change. It cannot be replayed or undone here.") } } }
    }
    if (!state.restrictedRecoveryState && showExternalRoleDialog) {
        AlertDialog(
            onDismissRequest = { showExternalRoleDialog = false },
            title = { androidx.compose.material3.Text(if (incomingInspectionTemplates != null) "Inspection templates received" else "Work package received") },
            text = { androidx.compose.material3.Text(if (incomingInspectionTemplates != null) "Inspection template files are imported in Member or Coordinator mode." else "Work packages are imported in Member mode.") },
            confirmButton = { Button({ showExternalRoleDialog = false; nav.navigate("dispatch/settings") }, Modifier.testTag("external-package-open-role")) { androidx.compose.material3.Text("Open Team role settings") } },
            dismissButton = { TextButton({ showExternalRoleDialog = false }) { androidx.compose.material3.Text("Not now") } },
        )
    }
}
