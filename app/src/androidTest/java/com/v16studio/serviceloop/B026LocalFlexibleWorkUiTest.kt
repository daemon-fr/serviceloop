package com.v16studio.serviceloop.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.domain.*
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class B026LocalFlexibleWorkUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun registerHidesOneTimeRowsByDefaultAcrossAllPanes() {
        showRegister()
        compose.onNodeWithText("Standard customer").assertIsDisplayed()
        assertAbsentText("One-time customer")
        compose.onNodeWithTag("content-tab-Sites").performClick()
        compose.onNodeWithText("Standard site", substring = true).assertIsDisplayed()
        assertAbsentText("One-time site")
        compose.onNodeWithTag("content-tab-Equipment").performClick()
        compose.onNodeWithText("Standard equipment", substring = true).assertIsDisplayed()
        assertAbsentText("One-time equipment")
    }

    @Test fun registerRevealShowsMarkedOneTimeRowsAndUncheckingHidesThem() {
        showRegister()
        compose.onNodeWithTag("show-one-time-customers").performClick()
        compose.onNodeWithText("One-time customer").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("One-time", substring = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("content-tab-Sites").performClick()
        compose.onNodeWithText("One-time site", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("content-tab-Equipment").performClick()
        compose.onNodeWithText("One-time equipment", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("show-one-time-customers").performClick()
        assertAbsentText("One-time customer")
        assertAbsentText("One-time site")
        assertAbsentText("One-time equipment")
    }

    @Test fun searchFindsOneTimeRecordWithoutRegisterReveal() {
        val result = SearchTarget("CUSTOMER", "one-time-customer", "CU-OT", "Unique one-time customer", "Unique one-time site", CustomerType.ONE_TIME)
        val viewModel = ServiceLoopViewModel(FakeRepository()) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    SearchScreen(listOf(result), PaddingValues(), viewModel, rememberNavController())
                }
            }
        }
        compose.onNodeWithText("Unique one-time customer", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("field-search-names-and-references").performTextInput("Unique one-time")
        compose.onNodeWithText("Unique one-time customer", substring = true).assertIsDisplayed()
        compose.onNodeWithText("One-time · CUSTOMER", substring = true).assertIsDisplayed()
        assertAbsentTag("show-one-time-customers")
    }

    @Test fun newVisitRemainsUsableWhenDueServicesFail() {
        val standard = site("standard-site", "Standard site", CustomerType.STANDARD, equipment = listOf(equipment("standard-equipment", "Standard equipment")))
        val due = due(standard, "plan-unavailable")
        val state = UiState(
            loading = false,
            dueServicesProjection = DueServicesProjection.Unavailable("Due service projection failed"),
            templates = emptyList(),
            businessDate = LocalDate.of(2026, 9, 5),
            businessZoneId = "Europe/Bucharest",
        )
        showNewVisit(listOf(standard), listOf(due), state, listOf(due.planId))
        compose.onNodeWithText("Set up visit").assertIsDisplayed()
        compose.onNodeWithTag("visit-mode-ONE_TIME").assertIsDisplayed()
        compose.onNodeWithText("Planned services are unavailable.").assertIsDisplayed()
        compose.onNodeWithTag("new-visit-form").performScrollToNode(hasTestTag("field-task-name-required")).assertIsDisplayed()
        assertAbsentText("Unable to read due services")
    }

    @Test fun startNowCreatesWorkingVisitAndOpensItsFirstService() {
        val standard = site("standard-site", "Standard site", CustomerType.STANDARD)
        val due = due(standard, "plan-start")
        val repository = FakeRepository().apply {
            workingProgress = VisitServiceProgress(
                visitId = "created-working-visit",
                visitReference = "V-START",
                customerName = "Customer",
                siteName = "Standard site",
                serviceDate = "2026-09-05",
                items = listOf(ServiceProgressItem(
                    workItemId = "created-work-item",
                    position = 1,
                    subjectType = WorkSubjectType.SITE,
                    equipmentId = null,
                    equipmentName = null,
                    equipmentReference = null,
                    equipmentDescription = null,
                    serviceName = "Started service",
                    status = ServiceEntryStatus.NOT_STARTED,
                )),
                groups = emptyList(),
            )
        }
        val state = UiState(
            loading = false,
            dueServicesProjection = DueServicesProjection.Available(listOf(due)),
            businessDate = LocalDate.of(2026, 9, 5),
            businessZoneId = "Europe/Bucharest",
        )
        val viewModel = ServiceLoopViewModel(repository) {}

        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    CompositionLocalProvider(LocalDetailBackInterceptor provides remember { mutableStateOf<(() -> Unit)?>(null) }) {
                        val nav = rememberNavController()
                        NavHost(nav, "visit/new") {
                            composable("visit/new") { NewVisitScreen(listOf(standard), listOf(due), PaddingValues(), state, viewModel, nav, listOf(due.planId)) }
                            composable("visit/{visitId}") { androidx.compose.material3.Text("Visit overview", modifier = androidx.compose.ui.Modifier.testTag("visit-overview")) }
                            composable("inspection/{workItemId}") { androidx.compose.material3.Text("Service destination", modifier = androidx.compose.ui.Modifier.testTag("service-destination")) }
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("new-visit-form").performScrollToNode(hasTestTag("field-task-name-required"))
        compose.onNodeWithTag("field-task-name-required").performTextInput("Start maintenance")
        compose.onNodeWithTag("add-task").performClick()
        compose.onNodeWithTag("new-visit-form").performScrollToNode(hasTestTag("field-appointment-service-date-yyyy-mm-dd"))
        compose.onNodeWithTag("field-appointment-service-date-yyyy-mm-dd").performTextReplacement("2026-09-05")
        compose.onNodeWithTag("primary-visit-action-WORKING").performScrollTo().assertIsEnabled().performClick()

        compose.waitUntil(10_000) { repository.createdVisitId != null }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("service-destination").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("service-destination").assertIsDisplayed()
    }

    @Test fun existingSiteSearchDeliberatelyRevealsMatchingOneTimeSite() {
        val standard = site("standard-site", "Standard site", CustomerType.STANDARD)
        val oneTime = site("one-time-site", "Unique one-time site", CustomerType.ONE_TIME)
        showNewVisit(listOf(standard, oneTime), emptyList())
        compose.onNodeWithTag("visit-site-standard-site").assertIsDisplayed()
        assertAbsentText("Unique one-time site")
        compose.onNodeWithTag("field-find-customer-or-site").performTextInput("Unique one-time")
        compose.onNodeWithTag("visit-site-one-time-site").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("One-time", substring = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("visit-site-one-time-site").performClick()
        compose.onNodeWithTag("new-visit-form").performScrollToNode(hasTestTag("field-task-name-required")).assertIsDisplayed()
    }

    @Test fun changingSiteProtectsTaskDraftsAndUsesTheSameOneTimeDiscoveryRule() {
        val standard = site("standard-site", "Standard site", CustomerType.STANDARD)
        val oneTime = site("one-time-site", "Unrelated one-time site", CustomerType.ONE_TIME)
        showNewVisit(listOf(standard, oneTime), emptyList())
        compose.onNodeWithTag("visit-site-standard-site").performClick()
        compose.onNodeWithTag("new-visit-form").performScrollToNode(hasTestTag("field-task-name-required"))
        compose.onNodeWithTag("field-task-name-required").performTextInput("Draft task")
        compose.onNodeWithTag("add-task").performClick()
        compose.onNodeWithText("Change").performScrollTo().performClick()
        compose.onNodeWithText("Change site?").assertIsDisplayed()
        compose.onNodeWithText("Tasks added for this site will be cleared.").assertIsDisplayed()
        compose.onNodeWithText("Keep current site").performClick()
        compose.onNodeWithTag("visit-task-0").assertIsDisplayed()
        compose.onNodeWithText("Change").performScrollTo().performClick()
        compose.onNodeWithText("Change site").performClick()
        assertAbsentText("Unrelated one-time site")
        compose.onNodeWithTag("field-find-customer-or-site").performTextInput("Unrelated one-time")
        compose.onNodeWithTag("visit-site-one-time-site").assertIsDisplayed()
    }

    @Test fun existingOneTimeVisitCanAddKnownRegisteredEquipment() {
        val registered = equipment("one-time-equipment", "Registered one-time equipment")
        val site = site("one-time-site", "One-time site", CustomerType.ONE_TIME, listOf(registered))
        val visit = visit(site, "visit-one-time")
        val repository = FakeRepository(site = site.toDetail(), visit = visit)
        val viewModel = ServiceLoopViewModel(repository) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    val state by viewModel.state.collectAsState()
                    LaunchedEffect(Unit) { viewModel.loadVisit(visit.id) }
                    VisitDetailScreen(detail = state.visit ?: visit, padding = PaddingValues(), state = state.copy(site = state.site ?: site.toDetail()), viewModel = viewModel, nav = rememberNavController())
                }
            }
        }
        compose.onNodeWithTag("visit-task-subject-EQUIPMENT").performClick()
        compose.onNodeWithTag("visit-task-equipment-null").assertIsDisplayed()
        compose.onNodeWithTag("visit-task-equipment-one-time-equipment").assertIsDisplayed()
        compose.onNodeWithText("Registered one-time equipment", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("field-task-name-required").performTextInput("Inspect registered unit")
        compose.onNodeWithTag("visit-task-equipment-one-time-equipment").performClick()
        compose.onNodeWithTag("add-visit-task").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("visit-line-known-equipment").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Registered one-time equipment", substring = true).assertIsDisplayed()
        assertEquals("known-equipment", repository.lastAddedWorkItemId)
    }

    @Test fun makeStandardRemovesOneTimeActionAndReturnsCustomerToDefaultRegister() {
        val repository = FakeRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    val state by viewModel.state.collectAsState()
                    LaunchedEffect(Unit) { viewModel.loadCustomer("one-time-customer") }
                    CustomerDetailScreen(state.customer ?: repository.customerDetail, PaddingValues(), rememberNavController(), viewModel)
                }
            }
        }
        compose.onNodeWithTag("make-standard-customer").performClick()
        compose.waitUntil(10_000) { repository.customerDetail.customerType == CustomerType.STANDARD && compose.onAllNodesWithTag("make-standard-customer").fetchSemanticsNodes().isEmpty() }
        assertAbsentText("One-time customer")
        compose.runOnUiThread {
            compose.activity.setContent { ServiceLoopTheme { CustomersScreen(listOf(repository.customerSummary()), emptyList(), emptyList(), rememberNavController()) } }
        }
        compose.onNodeWithText("Promoted customer").assertIsDisplayed()
    }

    @Test fun oneTimeEquipmentPromotesCustomerBeforeOpeningAddPlan() {
        val repository = FakeRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    val nav = rememberNavController()
                    NavHost(nav, "equipment") {
                        composable("equipment") { EquipmentScreen(repository.equipmentDetail, nav, LocalDate.of(2026, 9, 5), 14, viewModel) }
                        composable("plan/new/one-time-equipment") { androidx.compose.material3.Text("Add service plan") }
                    }
                }
            }
        }
        compose.onNodeWithTag("make-standard-and-add-plan").performScrollTo().performClick()
        compose.waitUntil(10_000) { repository.customerDetail.customerType == CustomerType.STANDARD && compose.onAllNodesWithText("Add service plan").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add service plan").assertIsDisplayed()
        assertTrue(repository.customerDetail.customerType == CustomerType.STANDARD)
    }

    @Test fun linkExistingEquipmentReturnsToSameServiceWithTheSameWorkItem() {
        val repository = FakeRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        renderServiceAndLink(repository, viewModel)
        compose.onNodeWithTag("link-equipment").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("link-equipment-known-equipment").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("link-equipment-known-equipment").performClick()
        compose.waitUntil(10_000) { viewModel.state.value.inspection?.equipmentName == "Known equipment" }
        compose.waitForIdle()
        compose.onNodeWithTag("service-identity").assert(hasAnyDescendant(hasText("Known equipment", substring = true)))
        assertEquals("working-unidentified", repository.lastInspectedWorkItemId)
        assertEquals("known-equipment", repository.inspectedDraft?.equipmentId)
    }

    @Test fun createAndLinkEquipmentReturnsToSameServiceWithoutCreatingAPlan() {
        val repository = FakeRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        renderServiceAndLink(repository, viewModel)
        compose.onNodeWithTag("link-equipment").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("link-add-equipment").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("link-add-equipment").performClick()
        compose.onNodeWithTag("field-equipment-name-required").performTextInput("Created equipment")
        compose.onNodeWithTag("save-and-link-equipment").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(10_000) { repository.createdEquipment && repository.inspectedDraft?.equipmentId == "created-equipment" }
        compose.waitUntil(10_000) { viewModel.state.value.inspection?.equipmentName == "Created equipment" }
        compose.waitForIdle()
        compose.onNodeWithTag("service-identity").assert(hasAnyDescendant(hasText("Created equipment", substring = true)))
        assertTrue(repository.createdEquipment)
        assertTrue(repository.createdEquipmentPlanCount == 0)
    }

    private fun showRegister() {
        val standardCustomer = CustomerSummary("standard-customer", "Standard customer", "CU-STD", 1, 1, CustomerType.STANDARD)
        val oneTimeCustomer = CustomerSummary("one-time-customer", "One-time customer", "CU-OT", 1, 1, CustomerType.ONE_TIME)
        val standardSite = SiteRegisterSummary("standard-site", "ST-STD", "Standard site", "Standard customer", "", 1, CustomerType.STANDARD)
        val oneTimeSite = SiteRegisterSummary("one-time-site", "ST-OT", "One-time site", "One-time customer", "", 1, CustomerType.ONE_TIME)
        val standardEquipment = equipment("standard-equipment", "Standard equipment")
        val oneTimeEquipment = equipment("one-time-equipment", "One-time equipment").copy(customerType = CustomerType.ONE_TIME)
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    CustomersScreen(listOf(standardCustomer, oneTimeCustomer), listOf(standardSite, oneTimeSite), listOf(standardEquipment, oneTimeEquipment), rememberNavController())
                }
            }
        }
    }

    private fun showNewVisit(sites: List<VisitSiteOption>, due: List<DueService>, state: UiState = UiState(loading = false, dueServicesProjection = DueServicesProjection.Available(due), businessDate = LocalDate.of(2026, 9, 5), businessZoneId = "Europe/Bucharest"), initialPlanIds: List<String> = emptyList()) {
        val viewModel = ServiceLoopViewModel(FakeRepository()) {}
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    CompositionLocalProvider(LocalDetailBackInterceptor provides remember { mutableStateOf<(() -> Unit)?>(null) }) {
                        NewVisitScreen(sites, due, PaddingValues(), state, viewModel, rememberNavController(), initialPlanIds)
                    }
                }
            }
        }
    }

    private fun renderServiceAndLink(repository: FakeRepository, viewModel: ServiceLoopViewModel) {
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    val nav = rememberNavController()
                    val state by viewModel.state.collectAsState()
                    LaunchedEffect(Unit) { viewModel.loadInspection(repository.inspectedDraft!!.workItemId) }
                    NavHost(nav, "service") {
                        composable("service") { TestServiceRoute(repository, viewModel, nav) }
                        composable("work/{workItemId}/link-equipment") { entry ->
                            val id = entry.arguments?.getString("workItemId").orEmpty()
                            LaunchedEffect(id) { viewModel.loadEquipmentLinkContext(id) }
                            EquipmentLinkScreen(id, state.equipmentLinkContext, PaddingValues(), state, viewModel, nav)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TestServiceRoute(repository: FakeRepository, viewModel: ServiceLoopViewModel, nav: androidx.navigation.NavHostController) {
        val state by viewModel.state.collectAsState()
        val draft = state.inspection ?: repository.inspectedDraft ?: return
        key(draft.equipmentId) { InspectionScreen(draft, state.saveStatus, state.inspectionFocus, viewModel, nav) }
    }

    private fun assertAbsentText(text: String) = assertTrue(compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty())
    private fun assertAbsentTag(tag: String) = assertTrue(compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty())

    private fun equipment(id: String, name: String) = EquipmentSummary(id, name, "EQ-$id", "ID-$id", "Site", "Customer", null, CustomerType.STANDARD)
    private fun site(id: String, name: String, type: CustomerType, equipment: List<EquipmentSummary> = emptyList()) = VisitSiteOption(id, "ST-$id", name, "Customer $id", equipment, type)
    private fun VisitSiteOption.toDetail() = SiteDetail(id, "customer-$id", customerName, reference, name, "", "", "", "", "", true, equipment, customerType = customerType)
    private fun visit(site: VisitSiteOption, id: String) = VisitDetail(id, "V-$id", "WORKING", "customer-${site.id}", site.customerName, site.id, site.name, "", "2026-09-05", null, null, emptyList(), null, customerType = site.customerType)
    private fun due(site: VisitSiteOption, planId: String) = DueService(planId, "P-$planId", "Planned service", "2026-09-05", "obligation-$planId", site.equipment.firstOrNull()?.id ?: "equipment", site.equipment.firstOrNull()?.reference ?: "EQ", site.equipment.firstOrNull()?.name ?: "Equipment", site.id, site.name, "customer-${site.id}", site.customerName, null, DueBucket.TODAY)

    private class FakeRepository(
        private val site: SiteDetail? = null,
        private var visit: VisitDetail? = null,
    ) : ServiceLoopRepository {
        var customerDetail = CustomerDetail("one-time-customer", "CU-OT", "Promoted customer", "", "", "", "", emptyList(), emptyList(), emptyList(), emptyList(), customerType = CustomerType.ONE_TIME)
        var equipmentDetail = EquipmentDetail("one-time-equipment", "One-time equipment", "EQ-OT", "ID-OT", "Maker Model", "SN-OT", "One-time site", "Promoted customer", emptyList(), null, "Maker", "Model", customerType = CustomerType.ONE_TIME, siteId = "one-time-site")
        var inspectedDraft: InspectionDraft? = InspectionDraft("working-unidentified", "visit-link", "V-LINK", "Site", null, null, "Identify equipment", null, null, null, "", "", false, null, null, 0L, emptyList(), subjectType = WorkSubjectType.EQUIPMENT, equipmentDescription = "Unknown equipment")
        var linkContext = EquipmentLinkContext("working-unidentified", "visit-link", "site-link", "Site", listOf(EquipmentSummary("known-equipment", "Known equipment", "EQ-KNOWN", "ID-KNOWN", "Site", "Customer", null, CustomerType.STANDARD)))
        var lastAddedWorkItemId: String? = null
        var lastInspectedWorkItemId: String? = null
        var createdEquipment = false
        var createdEquipmentPlanCount = 0
        var workingProgress: VisitServiceProgress? = null
        @Volatile var createdVisitId: String? = null

        override suspend fun home() = HomeSummary(null, null, null, null, null, null, null, 0, null, null, 0, 0)
        override suspend fun equipment(id: String) = equipmentDetail
        override suspend fun equipmentList() = listOf(EquipmentSummary(equipmentDetail.id, equipmentDetail.name, equipmentDetail.reference, equipmentDetail.technicianIdentifier, equipmentDetail.siteName, equipmentDetail.customerName, null, customerDetail.customerType))
        override suspend fun customerList() = listOf(customerSummary())
        override suspend fun inspection(workItemId: String) = inspectedDraft?.also { lastInspectedWorkItemId = it.workItemId }
        override suspend fun completionLines(visitId: String) = emptyList<CompletionLine>()
        override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?) = 0L
        override suspend fun customer(id: String) = customerDetail
        override suspend fun site(id: String) = site
        override suspend fun visit(id: String) = visit
        override suspend fun serviceVisitProgress(visitId: String) = workingProgress ?: error("No progress")
        override suspend fun templates() = emptyList<TemplateSummary>()
        override suspend fun equipmentLinkContext(workItemId: String) = linkContext
        override suspend fun linkWorkItemEquipment(workItemId: String, equipmentId: String): Long {
            inspectedDraft = inspectedDraft?.copy(equipmentId = equipmentId, equipmentName = "Known equipment", equipmentReference = "EQ-KNOWN", equipmentDescription = null)
            return 1L
        }
        override suspend fun createAndLinkEquipment(workItemId: String, input: EquipmentInput): String {
            createdEquipment = true
            inspectedDraft = inspectedDraft?.copy(equipmentId = "created-equipment", equipmentName = input.name, equipmentReference = "EQ-CREATED", equipmentDescription = null)
            return "created-equipment"
        }
        override suspend fun addAdHocWork(visitId: String, input: AdHocWorkInput): String {
            lastAddedWorkItemId = "known-equipment"
            visit = visit?.copy(lines = visit!!.lines + VisitLine("known-equipment", "Registered one-time equipment", "EQ-one-time-equipment", input.taskName, null, null, WorkSubjectType.EQUIPMENT, "one-time-equipment", null))
            return "known-equipment"
        }
        override suspend fun createVisitForSite(siteId: String, planIds: List<String>, adHocWork: List<AdHocWorkInput>, state: String, serviceDate: String, scheduledAtEpochMillis: Long?): String {
            createdVisitId = "created-working-visit"
            return createdVisitId!!
        }
        override suspend fun makeCustomerStandard(customerId: String): Long {
            customerDetail = customerDetail.copy(customerType = CustomerType.STANDARD)
            equipmentDetail = equipmentDetail.copy(customerType = CustomerType.STANDARD)
            return 1L
        }
        fun customerSummary() = CustomerSummary(customerDetail.id, customerDetail.name, customerDetail.reference, 0, 1, customerDetail.customerType)
    }
}
