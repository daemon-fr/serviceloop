package com.v16studio.serviceloop.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.v16studio.serviceloop.ui.designsystem.OperationalDashboard
import com.v16studio.serviceloop.domain.WorkScope
import com.v16studio.serviceloop.ui.theme.AppearancePreferences

private const val HOME = "home"
private const val WORK_ROUTE = "work?tab={tab}&filter={filter}&customerId={customerId}"
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
    val teamRole by rememberTeamRoleState(context)
    val capabilities = teamRole.workspaceCapabilities
    var showExternalRoleDialog by remember(incomingWorkPackage, incomingWorkPackageEvent, incomingInspectionTemplates, incomingInspectionTemplatesEvent) {
        mutableStateOf(false)
    }
    LaunchedEffect(incomingWorkPackage, incomingWorkPackageEvent, state.restrictedRecoveryState, capabilities.canReceiveAssignedWork) {
        if (!state.restrictedRecoveryState && incomingWorkPackage != null) {
            showExternalRoleDialog = !capabilities.canReceiveAssignedWork
            if (capabilities.canReceiveAssignedWork) nav.navigate("dispatch/import") { launchSingleTop = true }
        }
    }
    LaunchedEffect(incomingInspectionTemplates, incomingInspectionTemplatesEvent, state.restrictedRecoveryState, capabilities.canExchangeTemplates) {
        if (!state.restrictedRecoveryState && incomingInspectionTemplates != null) {
            showExternalRoleDialog = !capabilities.canExchangeTemplates
            if (capabilities.canExchangeTemplates) nav.navigate("template/list") { launchSingleTop = true }
        }
    }
    CompositionLocalProvider(LocalTeamRole provides teamRole, LocalWorkspaceCapabilities provides capabilities) {
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
                ScreenState(state.loading && !state.rootDataReady, state.error.takeUnless { state.rootDataReady }, padding, "root-home", state.rootRefreshError, viewModel::refreshRootDataNonBlocking) { HomeScreen(state, nav, viewModel) }
            }
        }
        composable(
            WORK_ROUTE,
            arguments = listOf(
                navArgument("tab") { type = NavType.StringType; defaultValue = WorkTab.DUE_SERVICES.name },
                navArgument("filter") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("customerId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val requested = runCatching { WorkTab.valueOf(entry.arguments?.getString("tab").orEmpty()) }.getOrDefault(WorkTab.DUE_SERVICES)
            val contextualFilter = entry.arguments?.getString("filter")
            val workScope = entry.arguments?.getString("customerId")
                ?.takeIf(String::isNotBlank)
                ?.let(WorkScope::Customer)
                ?: WorkScope.Global
            var workTab by androidx.compose.runtime.saveable.rememberSaveable(requested, contextualFilter) { mutableStateOf(requested) }
            LaunchedEffect(workScope) {
                viewModel.refreshRootDataNonBlocking()
                viewModel.loadVisits()
                viewModel.loadFollowUps()
                if (workScope is WorkScope.Customer) viewModel.loadCustomer(workScope.customerId)
                viewModel.observeOperationalDashboard(workScope)
            }
            RootScaffold(nav, RootDestination.WORK) { padding ->
                ScreenState(state.loading && !state.rootDataReady, state.error.takeUnless { state.rootDataReady }, padding, "root-work", state.rootRefreshError, viewModel::refreshRootDataNonBlocking) {
                    WorkScreen(state, nav, workTab, viewModel, contextualFilter, workScope) { workTab = it }
                }
            }
        }
        composable(CUSTOMERS) {
            if (!capabilities.showRegister) {
                LaunchedEffect(Unit) { nav.navigateToRoot(RootDestination.HOME) }
            } else {
                LaunchedEffect(Unit) { viewModel.refreshRootDataNonBlocking() }
                RootScaffold(nav, RootDestination.CUSTOMERS) { padding ->
                    ScreenState(state.loading && !state.rootDataReady, state.error.takeUnless { state.rootDataReady }, padding, "root-customers", state.rootRefreshError, viewModel::refreshRootDataNonBlocking) { CustomersScreen(state.customerList, state.siteList, state.equipmentList, nav, state.templates, viewModel) }
                }
            }
        }
        composable("equipment/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            LaunchedEffect(id) { viewModel.loadEquipment(id) }
            DetailScaffold("Equipment", nav) { padding ->
                ScreenState(state.loading, state.error, padding) { state.equipment?.let { EquipmentScreen(it, nav, state.businessDate, state.home?.dueSoonHorizonDays ?: 14, viewModel) } }
            }
        }
        composable("customer/new") { WorkspaceGate(capabilities.canManageRegister, "Customer management", nav) { DetailScaffold("Add customer", nav) { CustomerEditorScreen(null, it, state, viewModel, nav) } } }
        composable("customer/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadCustomer(id);viewModel.observeOperationalDashboard(WorkScope.Customer(id))}; DetailScaffold("Customer",nav){CustomerDetailScreen(state.customer,it,nav,viewModel,state.operationalDashboard?.takeIf { projection -> projection.scope == WorkScope.Customer(id) },state.businessDate,state.home?.dueSoonHorizonDays ?: 14)} }
        composable("work-dashboard/customer/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            LaunchedEffect(id) { viewModel.loadCustomer(id); viewModel.observeOperationalDashboard(WorkScope.Customer(id)) }
            val customer = state.customer?.takeIf { it.id == id }
            DetailScaffold(customer?.name ?: "Customer", nav) { padding ->
                val projection = state.operationalDashboard?.takeIf { it.scope == WorkScope.Customer(id) }
                if (projection == null) {
                    androidx.compose.foundation.layout.Column(Modifier.padding(padding).padding(16.dp)) {
                        androidx.compose.material3.Text(state.operationalDashboardError ?: "Reading current work")
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.padding(padding),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ServiceLoopUiTokens.Space.md),
                    ) {
                        item {
                            OperationalDashboard(
                                projection = projection,
                                onOpenItem = { openOperationalWork(nav, it, capabilities.canPerformFieldWork) },
                                onViewAll = { openOperationalSection(nav, it, projection.scope) },
                                modifier = Modifier.testTag("customer-work-dashboard"),
                            )
                        }
                    }
                }
            }
        }
        composable("customer/edit/{id}") { entry -> WorkspaceGate(capabilities.canManageRegister, "Customer management", nav) { val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadCustomer(id)}; DetailScaffold("Edit customer",nav){CustomerEditorScreen(state.customer,it,state,viewModel,nav)} } }
        composable("site/new/{customerId}") { entry -> WorkspaceGate(capabilities.canManageRegister, "Site management", nav) { val id=entry.arguments?.getString("customerId"); DetailScaffold("Add site",nav){SiteEditorScreen(id,null,it,state,viewModel,nav)} } }
        composable("site/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadSite(id)}; DetailScaffold("Site",nav){SiteDetailScreen(state.site,it,nav,viewModel)} }
        composable("site/edit/{id}") { entry -> WorkspaceGate(capabilities.canManageRegister, "Site management", nav) { val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadSite(id)}; DetailScaffold("Edit site",nav){SiteEditorScreen(null,state.site,it,state,viewModel,nav)} } }
        composable("equipment/new/{siteId}") { entry -> WorkspaceGate(capabilities.canManageRegister, "Equipment management", nav) { val id=entry.arguments?.getString("siteId"); DetailScaffold("Add equipment",nav){EquipmentEditorScreen(id,null,it,state,viewModel,nav)} } }
        composable("equipment/select-site") { WorkspaceGate(capabilities.canManageRegister, "Equipment management", nav) { LaunchedEffect(Unit){viewModel.loadVisitSetup()}; DetailScaffold("Choose equipment site",nav){EquipmentSiteSelectorScreen(state.visitSites,it,nav)} } }
        composable("equipment/edit/{id}") { entry -> WorkspaceGate(capabilities.canManageRegister, "Equipment management", nav) { val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadEquipment(id)}; DetailScaffold("Edit equipment",nav){EquipmentEditorScreen(null,state.equipment,it,state,viewModel,nav)} } }
        composable("plan/new/{equipmentId}") { entry -> WorkspaceGate(capabilities.canManageRegister, "Service-plan management", nav) { val id=entry.arguments?.getString("equipmentId"); LaunchedEffect(Unit){viewModel.loadTemplates()}; DetailScaffold("Add service plan",nav){PlanEditorScreen(id,null,state.templates,it,state,viewModel,nav)} } }
        composable("plan/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadPlan(id)}; DetailScaffold("Service plan",nav){PlanDetailScreen(state.plan,it,nav)} }
        composable("plan/edit/{id}") { entry -> WorkspaceGate(capabilities.canManageRegister, "Service-plan management", nav) { val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadPlan(id)}; DetailScaffold("Edit service plan",nav){PlanEditorScreen(null,state.plan,state.templates,it,state,viewModel,nav)} } }
        composable("template/list") { WorkspaceGate(capabilities.canManageTemplates, "Template management", nav) { LaunchedEffect(Unit){viewModel.loadTemplates()}; DetailScaffold("Inspection templates",nav){TemplateListScreen(state.templates,it,nav,viewModel,incomingInspectionTemplates)} } }
        composable(
            "template/new?cloneFrom={cloneFrom}&returnTo={returnTo}",
            arguments = listOf(
                navArgument("cloneFrom") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("returnTo") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry -> WorkspaceGate(capabilities.canManageTemplates, "Template management", nav) {
            val cloneFrom = entry.arguments?.getString("cloneFrom")
            val returnTo = entry.arguments?.getString("returnTo")
            LaunchedEffect(cloneFrom) { cloneFrom?.takeIf(String::isNotBlank)?.let(viewModel::loadTemplate) }
            DetailScaffold("Create template",nav){TemplateEditorScreen(null,it,state,viewModel,nav,state.template?.takeIf { template -> template.id == cloneFrom },returnTo)}
        } }
        composable("template/{id}") { entry -> WorkspaceGate(capabilities.canManageTemplates, "Template management", nav) { val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadTemplate(id);viewModel.loadTemplateHistory(id)}; DetailScaffold("Inspection template",nav){TemplateDetailScreen(state.template,it,nav,viewModel,state.templatePlanReferenceCount ?: 0,state.templateVersions)} } }
        composable(
            "template/edit/{id}?focusItem={focusItem}",
            arguments = listOf(navArgument("focusItem") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry -> WorkspaceGate(capabilities.canManageTemplates, "Template management", nav) {
            val id=entry.arguments?.getString("id").orEmpty()
            val focusItem=entry.arguments?.getString("focusItem")?.toIntOrNull()
            LaunchedEffect(id){viewModel.loadTemplate(id)}
            DetailScaffold("Edit template",nav){TemplateEditorScreen(state.template,it,state,viewModel,nav,focusItem = focusItem)}
        } }
        composable("template/history/{id}") { entry -> WorkspaceGate(capabilities.canManageTemplates, "Template management", nav) { val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadTemplateHistory(id)}; DetailScaffold("Version history",nav){TemplateHistoryScreen(state.templateVersions,it,id,nav)} } }
        composable("template/version/{templateId}/{revisionId}") { entry -> WorkspaceGate(capabilities.canManageTemplates, "Template management", nav) { val templateId=entry.arguments?.getString("templateId").orEmpty(); val revisionId=entry.arguments?.getString("revisionId").orEmpty(); LaunchedEffect(templateId){viewModel.loadTemplateHistory(templateId)}; DetailScaffold("Template version",nav){TemplateVersionScreen(state.templateVersions.firstOrNull { it.id == revisionId },it)} } }
        composable("visit/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadVisit(id);viewModel.loadReminderSettings()}; DetailScaffold("Visit",nav){VisitDetailScreen(state.visit,it,state,viewModel,nav)} }
        composable("visit/new") { entry -> WorkspaceGate(capabilities.canCreateLocalWork, "Local Visit creation", nav) { val ids=remember(entry){nav.previousBackStackEntry?.savedStateHandle?.remove<ArrayList<String>>("visit-setup-plan-ids")?.toList().orEmpty()}; LaunchedEffect(Unit){viewModel.loadVisitSetup()}; DetailScaffold("Create visit",nav){NewVisitScreen(state.visitSites,state.dueServices,it,state,viewModel,nav,ids)} } }
        composable("visit/new/{planId}") { entry -> WorkspaceGate(capabilities.canCreateLocalWork, "Local Visit creation", nav) { val id=entry.arguments?.getString("planId").orEmpty(); LaunchedEffect(id){viewModel.loadVisitSetup()}; DetailScaffold("Create visit",nav){NewVisitScreen(state.visitSites,state.dueServices,it,state,viewModel,nav,listOf(id))} } }
        composable("field/{workItemId}") { entry -> WorkspaceGate(capabilities.canPerformFieldWork, "Technician field work", nav) { val id=entry.arguments?.getString("workItemId").orEmpty(); LaunchedEffect(id){viewModel.loadFieldEvidence(id)}; DetailScaffold("Parts and photographs",nav){FieldEvidenceScreen(id,state,it,viewModel,nav)} } }
        composable("follow-up/list") { LaunchedEffect(Unit){viewModel.loadFollowUps()}; DetailScaffold("Follow-ups",nav){FollowUpListScreen(state.followUps,it,nav)} }
        composable("follow-up/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadFollowUp(id)}; DetailScaffold("Follow-up",nav){FollowUpDetailScreen(state.followUp,it,state,viewModel,nav)} }
        composable("follow-up/new/{customerId}") { entry -> val id=entry.arguments?.getString("customerId").orEmpty(); DetailScaffold("Add follow-up",nav){FollowUpEditorScreen(id,it,state,viewModel,nav)} }
        composable("contact/new/{customerId}") { entry -> val id=entry.arguments?.getString("customerId").orEmpty(); DetailScaffold("Record contact",nav){ContactNoteEditorScreen(id,it,state,viewModel,nav)} }
        composable("contact/{id}") { entry -> val id=entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id){viewModel.loadContactNote(id)}; DetailScaffold("Contact note",nav){padding -> ContactNoteScreen(state.contactNote,padding,state,viewModel)} }
        composable("search") {
            LaunchedEffect(Unit) { viewModel.observeOperationalDashboard(WorkScope.Global) }
            DetailScaffold("Search",nav){SearchScreen(state.searchResults,it,viewModel,nav,state.operationalDashboard)}
        }
        composable("inspection/{id}") { entry -> WorkspaceGate(capabilities.canPerformFieldWork, "Technician field work", nav) {
            val id = entry.arguments?.getString("id").orEmpty()
            LaunchedEffect(id) { viewModel.loadInspection(id) }
            DetailScaffold("Service", nav) { padding ->
                ScreenState(state.loading, state.error, padding) { state.inspection?.let { draft ->
                    ServiceScreen(draft, state.serviceProgress, state.saveStatus, state.inspectionFocus, viewModel, nav, PaddingValues(), LocalDetailBackInterceptor.current)
                } }
            }
        } }
        composable("review/{visitId}") { entry -> WorkspaceGate(capabilities.canPerformFieldWork, "Technician field work", nav) {
            val visitId = entry.arguments?.getString("visitId").orEmpty()
            LaunchedEffect(visitId) { viewModel.loadCompletion(visitId) }
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner, visitId) { val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.loadCompletion(visitId) }; lifecycleOwner.lifecycle.addObserver(observer); onDispose { lifecycleOwner.lifecycle.removeObserver(observer); viewModel.clearCompletionContext(visitId) } }
            LaunchedEffect(state.finalizedRecordId) { state.finalizedRecordId?.let { recordId -> viewModel.consumeFinalizedNavigation(); nav.navigate("record/$recordId") { popUpTo("review/{visitId}") { inclusive = true } } } }
            DetailScaffold("Review visit", nav) { padding -> CompletionReviewScreen(visitId, state.completionLines, state.businessProfile, state, padding, viewModel, nav) }
        } }
        composable("settings") { LaunchedEffect(Unit) { viewModel.loadReminderSettings() }; DetailScaffold("Settings", nav) { padding -> SettingsScreen(state, padding, nav, appearanceController) } }
        composable("work/{workItemId}/link-equipment") { entry -> WorkspaceGate(capabilities.canPerformFieldWork, "Technician field work", nav) { val id = entry.arguments?.getString("workItemId").orEmpty(); LaunchedEffect(id) { viewModel.loadEquipmentLinkContext(id) }; DetailScaffold("Link equipment", nav) { padding -> EquipmentLinkScreen(id, state.equipmentLinkContext, padding, state, viewModel, nav) } } }
        composable("appearance") { DetailScaffold("Appearance", nav) { padding -> AppearanceSettingsScreen(padding, appearanceController) } }
        composable("reminders") { LaunchedEffect(Unit) { viewModel.loadReminderSettings() }; DetailScaffold("Reminders", nav) { padding -> ReminderSettingsScreen(state, padding, viewModel, nav) } }
        composable("calendar") { LaunchedEffect(Unit) { viewModel.loadCalendarSettings() }; DetailScaffold("Calendar integration", nav) { padding -> CalendarSettingsScreen(state, padding, viewModel) } }
        composable("dispatch/settings") { DetailScaffold("Team role settings",nav){DispatchSettings(it,nav)} }
        composable("dispatch/technicians") { WorkspaceGate(capabilities.canUseCoordinatorTools, "Coordinator tools", nav) { DetailScaffold("Technicians",nav){DispatchTechniciansScreen(it)} } }
        composable("dispatch/teams") { WorkspaceGate(capabilities.canUseCoordinatorTools, "Coordinator tools", nav) { DetailScaffold("Teams",nav){DispatchTeamsScreen(it)} } }
        composable("dispatch/create") {
            WorkspaceGate(capabilities.canUseCoordinatorTools, "Coordinator tools", nav) {
            DetailScaffold("Outbox", nav) { DispatchOutboxScreen(it, nav, state.businessDate, canConcludeDelegatedWork = capabilities.canConcludeDelegatedWork) }
            }
        }
        composable("dispatch/visit/new") { WorkspaceGate(capabilities.canUseCoordinatorTools, "Coordinator tools", nav) { DetailScaffold("New dispatch visit",nav){DispatchVisitEditorScreen(it,nav,null,state.businessDate,canConcludeDelegatedWork=capabilities.canConcludeDelegatedWork)} } }
        composable("dispatch/visit/{id}") { entry -> WorkspaceGate(capabilities.canUseCoordinatorTools, "Coordinator tools", nav) { val id=entry.arguments?.getString("id").orEmpty(); DetailScaffold("Dispatch visit",nav){DispatchVisitEditorScreen(it,nav,id,state.businessDate,canConcludeDelegatedWork=capabilities.canConcludeDelegatedWork)} } }
        composable("dispatch/export-review") { WorkspaceGate(capabilities.canUseCoordinatorTools, "Coordinator tools", nav) { val ids=nav.previousBackStackEntry?.savedStateHandle?.get<ArrayList<String>>("dispatch-export-ids").orEmpty(); DetailScaffold("Review export",nav){DispatchExportReviewScreen(it,nav,ids)} } }
        composable("dispatch/import") { WorkspaceGate(capabilities.canReceiveAssignedWork, "Assigned-work receiving", nav) { DetailScaffold("Import work package",nav){ImportDispatchPackageScreen(it,nav,viewModel,incomingWorkPackage)} } }
        composable("business-profile") { LaunchedEffect(Unit) { viewModel.loadBusinessProfile() }; DetailScaffold("Business and report identity", nav) { padding -> BusinessProfileScreen(state.businessProfile, state.businessProfileSaveStatus, padding, viewModel) } }
        composable("record/{id}") { entry -> val id = entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id) { viewModel.loadFinalRecord(id); viewModel.loadRecordVersions(id) }; DetailScaffold("Final service record", nav) { padding -> FinalRecordScreen(state.finalRecord, state.recordVersions, state.reportVersions, false, state.generatingReport, state.error, padding, viewModel, nav) } }
        composable("record-version/{id}/{revisionId}") { entry -> val recordId = entry.arguments?.getString("id").orEmpty(); val revisionId = entry.arguments?.getString("revisionId").orEmpty(); LaunchedEffect(recordId, revisionId) { viewModel.loadFinalRecordRevision(recordId, revisionId) }; DetailScaffold("Historical record revision", nav) { padding -> FinalRecordScreen(state.finalRecord, emptyList(), emptyList(), true, state.generatingReport, state.error, padding, viewModel, nav) } }
        composable("report-version/{id}/{revisionId}/{renditionId}") { entry -> val recordId = entry.arguments?.getString("id").orEmpty(); val revisionId = entry.arguments?.getString("revisionId").orEmpty(); val renditionId = entry.arguments?.getString("renditionId").orEmpty(); LaunchedEffect(recordId, revisionId, renditionId) { viewModel.loadFinalRecordRevision(recordId, revisionId, renditionId) }; DetailScaffold("Historical report rendition", nav) { padding -> ReportPreviewScreen(state.finalRecord, padding, initialTextView = false, historical = true, viewModel = viewModel) } }
        composable("report/{id}") { entry -> val id = entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id) { viewModel.loadFinalRecord(id) }; DetailScaffold("Customer report", nav) { padding -> ReportPreviewScreen(state.finalRecord, padding, initialTextView = false) } }
        composable("report-text/{id}") { entry -> val id = entry.arguments?.getString("id").orEmpty(); LaunchedEffect(id) { viewModel.loadFinalRecord(id) }; DetailScaffold("Customer report", nav) { padding -> ReportPreviewScreen(state.finalRecord, padding, initialTextView = true) } }
        composable("history/global") { DetailScaffold("History", nav) { padding -> HistoryScreen(com.v16studio.serviceloop.domain.HistoryScope(com.v16studio.serviceloop.domain.HistoryScopeType.GLOBAL), state, padding, viewModel, nav) } }
        composable("history/{scope}/{id}") { entry -> val scope = runCatching { com.v16studio.serviceloop.domain.HistoryScopeType.valueOf(entry.arguments?.getString("scope").orEmpty()) }.getOrDefault(com.v16studio.serviceloop.domain.HistoryScopeType.GLOBAL); val id = entry.arguments?.getString("id"); DetailScaffold("History", nav) { padding -> HistoryScreen(com.v16studio.serviceloop.domain.HistoryScope(scope, id, "${scope.name.lowercase()} history"), state, padding, viewModel, nav) } }
        composable("correction/{recordId}") { entry -> val id = entry.arguments?.getString("recordId").orEmpty(); DetailScaffold("Correct service record", nav) { padding -> CorrectionScreen(id, state, padding, viewModel, nav) } }
        composable("lifecycle/{subject}/{id}/{action}") { entry -> WorkspaceGate(capabilities.canManageRegister, "Register management", nav) { val subject = entry.arguments?.getString("subject").orEmpty(); val id = entry.arguments?.getString("id").orEmpty(); val action = entry.arguments?.getString("action").orEmpty(); DetailScaffold("Lifecycle review", nav) { padding -> LifecycleScreen(subject, id, action, state, padding, viewModel, nav) } } }
        composable("equipment/move/{id}") { entry -> WorkspaceGate(capabilities.canManageRegister, "Equipment management", nav) { val id = entry.arguments?.getString("id").orEmpty(); DetailScaffold("Move equipment", nav) { padding -> MoveEquipmentScreen(id, state, padding, viewModel, nav) } } }
        composable("data-recovery") { DetailScaffold("Data and recovery", nav) { padding -> DataRecoveryScreen(state, padding, viewModel, nav) } }
        composable("backup/{mode}") { entry -> val mode = entry.arguments?.getString("mode").orEmpty(); DetailScaffold(if (mode == "create") "Create backup" else if (mode == "restore") "Restore backup" else "Inspect backup", nav) { padding -> BackupScreen(mode, state, padding, viewModel, nav) } }
        composable("csv/export") { DetailScaffold("Export readable CSV", nav) { padding -> CsvExportScreen(state, padding, viewModel) } }
        composable("csv/import") { WorkspaceGate(capabilities.canManageRegister, "Directory CSV import", nav) { DetailScaffold("Import directory CSV", nav) { padding -> CsvImportScreen(state, padding, viewModel, nav) } } }
        composable("data/erase") { DetailScaffold("Erase local data", nav) { padding -> EraseScreen(state, padding, viewModel, nav) } }
        composable("change/{id}") { entry -> val id = entry.arguments?.getString("id"); DetailScaffold("Recorded change", nav) { padding -> val row = state.history.firstOrNull { it.id == id }; androidx.compose.foundation.layout.Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) { androidx.compose.material3.Text(row?.title.orEmpty(), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall); androidx.compose.material3.Text(row?.subtitle.orEmpty()); androidx.compose.material3.Text("This is a read-only historical change. It cannot be replayed or undone here.") } } }
    }
    if (!state.restrictedRecoveryState && showExternalRoleDialog) {
        AlertDialog(
            onDismissRequest = { showExternalRoleDialog = false },
            title = { androidx.compose.material3.Text(if (incomingInspectionTemplates != null) "Inspection templates received" else "Work package received") },
            text = { androidx.compose.material3.Text(if (incomingInspectionTemplates != null) "Inspection template files can be exchanged by Subcontractors, Team Leaders, and Coordinators." else "Work packages can be received by Subcontractors, Employees, and Team Leaders.") },
            confirmButton = { Button({ showExternalRoleDialog = false; nav.navigate("dispatch/settings") }, Modifier.testTag("external-package-open-role")) { androidx.compose.material3.Text("Open Team role settings") } },
            dismissButton = { TextButton({ showExternalRoleDialog = false }) { androidx.compose.material3.Text("Not now") } },
        )
    }
    }
}

@Composable
private fun WorkspaceGate(allowed: Boolean, feature: String, nav: NavHostController, content: @Composable () -> Unit) {
    if (allowed) content() else DetailScaffold("Unavailable", nav) { padding ->
        androidx.compose.foundation.layout.Column(
            Modifier.padding(padding).padding(24.dp).testTag("workspace-unavailable"),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.material3.Text("$feature is unavailable for the current Team role.", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Button({ nav.popBackStack() }, Modifier.testTag("workspace-unavailable-back")) { androidx.compose.material3.Text("Back") }
            TextButton({ nav.navigate("dispatch/settings") }, Modifier.testTag("workspace-unavailable-role-settings")) { androidx.compose.material3.Text("Team role settings") }
        }
    }
}
