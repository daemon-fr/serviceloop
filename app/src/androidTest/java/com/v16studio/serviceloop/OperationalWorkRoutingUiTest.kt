package com.v16studio.serviceloop

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.domain.CompletionLine
import com.v16studio.serviceloop.domain.CustomerSummary
import com.v16studio.serviceloop.domain.DueBucket
import com.v16studio.serviceloop.domain.DueService
import com.v16studio.serviceloop.domain.EquipmentDetail
import com.v16studio.serviceloop.domain.EquipmentSummary
import com.v16studio.serviceloop.domain.HomeSummary
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.OperationalDashboardProjection
import com.v16studio.serviceloop.domain.OperationalDashboardProjector
import com.v16studio.serviceloop.domain.OperationalWorkKind
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.domain.VisitSummary
import com.v16studio.serviceloop.domain.WorkScope
import com.v16studio.serviceloop.ui.DueServicesProjection
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.UiState
import com.v16studio.serviceloop.ui.WorkScreen
import com.v16studio.serviceloop.ui.WorkTab
import com.v16studio.serviceloop.ui.openOperationalSection
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class OperationalWorkRoutingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun customerViewAllKeepsVisitScopeWhileGlobalHomeRemainsGlobal() {
        val values = (1..6).map { visit("A-V$it", "customer-a", "Customer A") } + visit("B-V1", "customer-b", "Customer B")
        render(OperationalWorkKind.VISIT, values, emptyList())

        expandCustomerSectionIfNeeded("View all 6 overdue visits", "operational-section-header-visit-overdue")
        compose.onNodeWithText("View all 6 overdue visits").performClick()
        compose.onNodeWithTag("work-customer-scope").assertIsDisplayed()
        compose.onNodeWithText("Customer: Customer A").assertIsDisplayed()
        compose.onNodeWithTag("work-visits-list").performScrollToNode(hasText("A-V6"))
        compose.onNodeWithText("A-V6").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("B-V1").fetchSemanticsNodes().isEmpty())

        compose.onNodeWithTag("open-global-home").performClick()
        compose.onNodeWithText("View all 7 overdue visits").performClick()
        compose.onNodeWithTag("work-visits-list").performScrollToNode(hasText("B-V1"))
        compose.onNodeWithText("B-V1").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTag("work-customer-scope").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun customerViewAllKeepsDueServiceScope() {
        val values = (6 downTo 1).map { due("A-S$it", "customer-a", "Customer A") } + due("B-S1", "customer-b", "Customer B")
        render(OperationalWorkKind.SERVICE, emptyList(), values)

        expandCustomerSectionIfNeeded("View all 6 overdue services", "operational-section-header-service-overdue")
        compose.onNodeWithText("View all 6 overdue services").performClick()
        compose.onNodeWithTag("work-customer-scope").assertIsDisplayed()
        compose.onNodeWithTag("due-services-list").performScrollToNode(hasText("A-S6", substring = true))
        compose.onNodeWithText("A-S6", substring = true).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("B-S1").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun deterministicBusinessTimeStateIsUsedByVisitWorkFilter() {
        val values = (1..6).map {
            visit(
                id = "A-boundary-$it",
                customerId = "customer-a",
                customerName = "Customer A",
                date = "2026-09-06",
                scheduledAt = BUSINESS_NOW.plusSeconds(3_600L + it).toEpochMilli(),
            )
        }
        render(OperationalWorkKind.VISIT, values, emptyList())

        expandCustomerSectionIfNeeded("View all 6 due soon visits", "operational-section-header-visit-due_soon")
        compose.onNodeWithText("View all 6 due soon visits").performClick()
        compose.onNodeWithTag("work-visits-list").performScrollToNode(hasText("A-boundary-6"))
        compose.onNodeWithText("A-boundary-6").assertIsDisplayed()
    }

    private fun expandCustomerSectionIfNeeded(viewAllLabel: String, headerTag: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(viewAllLabel).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag(headerTag).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText(viewAllLabel).fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag(headerTag).performClick()
        }
    }

    private fun render(kind: OperationalWorkKind, visits: List<VisitSummary>, dueServices: List<DueService>) {
        val globalProjection = project(WorkScope.Global, visits, dueServices)
        val customerProjection = project(WorkScope.Customer("customer-a"), visits, dueServices)
        val viewModel = ServiceLoopViewModel(EmptyRepository()) {}
        compose.setContent {
            ServiceLoopTheme {
                val nav = rememberNavController()
                Column(Modifier.fillMaxSize()) {
                    NavHost(navController = nav, startDestination = "customer-dashboard", modifier = Modifier.weight(1f)) {
                        composable("customer-dashboard") {
                            com.v16studio.serviceloop.ui.designsystem.OperationalDashboard(
                                projection = customerProjection,
                                onOpenItem = {},
                                onViewAll = { openOperationalSection(nav, it, customerProjection.scope) },
                                modifier = Modifier.testTag("customer-dashboard"),
                            )
                        }
                        composable("global-home") {
                            com.v16studio.serviceloop.ui.designsystem.OperationalDashboard(
                                projection = globalProjection,
                                onOpenItem = {},
                                onViewAll = { openOperationalSection(nav, it, globalProjection.scope) },
                                modifier = Modifier.testTag("global-home"),
                            )
                        }
                        composable(
                            "work?tab={tab}&filter={filter}&customerId={customerId}",
                            arguments = listOf(
                                navArgument("tab") { type = NavType.StringType },
                                navArgument("filter") { type = NavType.StringType },
                                navArgument("customerId") { type = NavType.StringType; nullable = true; defaultValue = null },
                            ),
                        ) { entry ->
                            val scope = entry.arguments?.getString("customerId")
                                ?.takeIf(String::isNotBlank)
                                ?.let(WorkScope::Customer)
                                ?: WorkScope.Global
                            val tab = runCatching { WorkTab.valueOf(entry.arguments?.getString("tab").orEmpty()) }
                                .getOrDefault(WorkTab.DUE_SERVICES)
                            val filter = entry.arguments?.getString("filter").orEmpty()
                            val projection = if (scope == WorkScope.Global) globalProjection else customerProjection
                            WorkScreen(
                                state = UiState(
                                    loading = false,
                                    rootDataReady = true,
                                    home = HomeSummary(null, null, null, null, null, null, null, 0, null, null, 0, 0, dueSoonHorizonDays = 14),
                                    operationalDashboard = projection,
                                    customerList = listOf(CustomerSummary("customer-a", "Customer A", "CU-A", 0, 0)),
                                    visits = visits,
                                    dueServicesProjection = DueServicesProjection.Available(dueServices),
                                    businessDate = BUSINESS_DATE,
                                    businessZoneId = BUSINESS_ZONE.id,
                                ),
                                nav = nav,
                                tab = tab,
                                viewModel = viewModel,
                                contextualFilter = filter,
                                scope = scope,
                                onTabSelected = {},
                            )
                        }
                    }
                    Button(onClick = { nav.navigate("global-home") }, modifier = Modifier.testTag("open-global-home")) {
                        Text("Global Home")
                    }
                }
            }
        }
        compose.waitForIdle()
        assertTrue(globalProjection.sections.any { it.kind == kind })
    }

    private fun project(scope: WorkScope, visits: List<VisitSummary>, dueServices: List<DueService>): OperationalDashboardProjection =
        OperationalDashboardProjector.project(
            scope = scope,
            visits = visits,
            dueServices = dueServices,
            followUps = emptyList(),
            today = BUSINESS_DATE,
            now = BUSINESS_NOW,
            businessZone = BUSINESS_ZONE,
            dueSoonHorizonDays = 14,
        )

    private fun visit(
        id: String,
        customerId: String,
        customerName: String,
        date: String = "2026-09-05",
        scheduledAt: Long? = null,
    ) = VisitSummary(
        id = id,
        reference = id,
        siteName = "$customerName site",
        actualServiceDate = date,
        state = "BOOKED",
        finalRecordId = null,
        customerId = customerId,
        customerName = customerName,
        siteId = "$customerId-site",
        scheduledAtEpochMillis = scheduledAt,
        modifiedAtEpochMillis = 1L,
    )

    private fun due(id: String, customerId: String, customerName: String) = DueService(
        planId = id,
        planReference = id,
        planName = "$customerName service",
        dueDate = "2026-09-05",
        obligationId = "$id-obligation",
        equipmentId = "$id-equipment",
        equipmentReference = "$id-equipment",
        equipmentName = "$customerName equipment",
        siteId = "$customerId-site",
        siteName = "$customerName site",
        customerId = customerId,
        customerName = customerName,
        claimedVisitId = null,
        bucket = DueBucket.OVERDUE,
    )

    private class EmptyRepository : ServiceLoopRepository {
        override suspend fun home() = HomeSummary(null, null, null, null, null, null, null, 0, null, null, 0, 0)
        override suspend fun equipment(id: String): EquipmentDetail? = null
        override suspend fun equipmentList(): List<EquipmentSummary> = emptyList()
        override suspend fun customerList(): List<CustomerSummary> = emptyList()
        override suspend fun inspection(workItemId: String): InspectionDraft? = null
        override suspend fun completionLines(visitId: String): List<CompletionLine> = emptyList()
        override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?): Long = 0L
        override fun observeDueServices(): Flow<List<DueService>> = emptyFlow()
    }

    private companion object {
        val BUSINESS_DATE: LocalDate = LocalDate.of(2026, 9, 6)
        val BUSINESS_NOW: Instant = Instant.parse("2026-09-06T10:00:00Z")
        val BUSINESS_ZONE: ZoneId = ZoneId.of("Europe/Bucharest")
    }
}
