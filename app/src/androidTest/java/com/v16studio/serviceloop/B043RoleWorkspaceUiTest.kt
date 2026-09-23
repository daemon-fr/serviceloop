package com.v16studio.serviceloop

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import com.v16studio.serviceloop.domain.CustomerType
import com.v16studio.serviceloop.domain.EquipmentLinkContext
import com.v16studio.serviceloop.domain.EquipmentSummary
import com.v16studio.serviceloop.domain.SearchTarget
import com.v16studio.serviceloop.ui.COORDINATOR_ENABLED
import com.v16studio.serviceloop.ui.DISPATCH_PREFS
import com.v16studio.serviceloop.ui.EquipmentLinkScreen
import com.v16studio.serviceloop.ui.LocalTeamRole
import com.v16studio.serviceloop.ui.LocalWorkspaceCapabilities
import com.v16studio.serviceloop.ui.SearchScreen
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.UiState
import com.v16studio.serviceloop.ui.TEAM_ROLE
import com.v16studio.serviceloop.ui.TEAM_ROLE_OPTIONS
import com.v16studio.serviceloop.ui.TeamRole
import com.v16studio.serviceloop.ui.setTeamRole
import com.v16studio.serviceloop.ui.teamRole
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import com.v16studio.serviceloop.ui.workspaceCapabilities
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class B043RoleWorkspaceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val preferences get() = compose.activity.getSharedPreferences(DISPATCH_PREFS, 0)
    private var previousRole: String? = null

    @Before fun useSolo() {
        previousRole = preferences.getString(TEAM_ROLE, null)
        compose.runOnUiThread { compose.activity.setTeamRole(TeamRole.SOLO) }
    }

    @After fun restoreRole() {
        preferences.edit().apply {
            if (previousRole == null) remove(TEAM_ROLE) else putString(TEAM_ROLE, previousRole)
        }.commit()
    }

    @Test fun fiveRoleSettingsRenderAdoptedCopyAndOrder() {
        compose.onNodeWithTag("home-tab-TEAM").performClick()
        compose.onNodeWithTag("team-role-identity").performClick()
        TEAM_ROLE_OPTIONS.forEach { option ->
            compose.onNodeWithTag("team-role-settings").performScrollToNode(hasTestTag("team-role-${option.role.name}"))
            compose.onNodeWithTag("team-role-${option.role.name}").assertIsDisplayed()
            compose.onNodeWithTag("team-role-${option.role.name}-title", useUnmergedTree = true).assertTextEquals(option.title)
            compose.onNodeWithTag("team-role-${option.role.name}-description", useUnmergedTree = true).assertTextEquals(option.description)
            compose.onNodeWithText("${option.title} (${option.description})", substring = true).assertDoesNotExist()
        }
    }

    @Test fun allRolesProjectExpectedRootAndHomeDispatchActionsReactively() {
        assertWorkspace(TeamRole.SOLO, register = true, receive = false, coordinate = false)
        assertWorkspace(TeamRole.SUBCONTRACTOR, register = true, receive = true, coordinate = false)
        assertWorkspace(TeamRole.EMPLOYEE, register = false, receive = true, coordinate = false)
        assertWorkspace(TeamRole.TEAM_LEADER, register = true, receive = true, coordinate = true)
        assertWorkspace(TeamRole.COORDINATOR, register = true, receive = false, coordinate = true)
    }

    @Test fun coordinatorCanCreateVisitButCannotPerformFieldWorkAndEmployeeCannotCreateVisit() {
        compose.runOnUiThread {
            compose.activity.setTeamRole(TeamRole.COORDINATOR)
            val application = compose.activity.application as ServiceLoopApplication
            val viewModel = ServiceLoopViewModel(application.container.repository) {}
            compose.activity.setContent { ServiceLoopTheme(false) { ServiceLoopApp(viewModel, "inspection/not-a-work-item") } }
        }
        compose.onNodeWithTag("workspace-unavailable").assertIsDisplayed()
        compose.onNodeWithText("Technician field work is unavailable for the current Team role.").assertIsDisplayed()

        compose.runOnUiThread {
            val application = compose.activity.application as ServiceLoopApplication
            val viewModel = ServiceLoopViewModel(application.container.repository) {}
            compose.activity.setContent { ServiceLoopTheme(false) { ServiceLoopApp(viewModel, "visit/new") } }
        }
        compose.onNodeWithTag("workspace-unavailable").assertDoesNotExist()
        compose.onNodeWithText("Create visit").assertIsDisplayed()

        compose.runOnUiThread {
            compose.activity.setTeamRole(TeamRole.EMPLOYEE)
            val application = compose.activity.application as ServiceLoopApplication
            val viewModel = ServiceLoopViewModel(application.container.repository) {}
            compose.activity.setContent { ServiceLoopTheme(false) { ServiceLoopApp(viewModel, "visit/new") } }
        }
        compose.onNodeWithText("Local Visit creation is unavailable for the current Team role.").assertIsDisplayed()

        compose.runOnUiThread {
            compose.activity.setTeamRole(TeamRole.EMPLOYEE)
            val application = compose.activity.application as ServiceLoopApplication
            val viewModel = ServiceLoopViewModel(application.container.repository) {}
            compose.activity.setContent { ServiceLoopTheme(false) { ServiceLoopApp(viewModel, "inspection/not-a-work-item") } }
        }
        compose.onNodeWithTag("workspace-unavailable").assertDoesNotExist()
        compose.onNodeWithText("Service").assertIsDisplayed()

        assertEquals(true, TeamRole.COORDINATOR.workspaceCapabilities.canCreateVisits)
        assertEquals(false, TeamRole.COORDINATOR.workspaceCapabilities.canPerformFieldWork)
        assertEquals(true, TeamRole.COORDINATOR.workspaceCapabilities.canAssignWork)
        assertEquals(false, TeamRole.EMPLOYEE.workspaceCapabilities.canCreateVisits)
        assertEquals(true, TeamRole.EMPLOYEE.workspaceCapabilities.canPerformFieldWork)
        assertEquals(true, TeamRole.TEAM_LEADER.workspaceCapabilities.canCreateVisits)
        assertEquals(true, TeamRole.TEAM_LEADER.workspaceCapabilities.canPerformFieldWork)
        assertEquals(true, TeamRole.TEAM_LEADER.workspaceCapabilities.canAssignWork)
    }

    @Test fun legacyMemberCanonicalizesAndEmployeeSearchIsOperationalOnly() {
        compose.runOnUiThread {
            preferences.edit().putString(TEAM_ROLE, "MEMBER").remove(COORDINATOR_ENABLED).commit()
            assertEquals(TeamRole.SUBCONTRACTOR, compose.activity.teamRole())
            assertEquals(TeamRole.SUBCONTRACTOR.name, preferences.getString(TEAM_ROLE, null))
            val role = TeamRole.EMPLOYEE
            val application = compose.activity.application as ServiceLoopApplication
            val viewModel = ServiceLoopViewModel(application.container.repository) {}
            compose.activity.setContent {
                ServiceLoopTheme(false) {
                    CompositionLocalProvider(LocalTeamRole provides role, LocalWorkspaceCapabilities provides role.workspaceCapabilities) {
                        SearchScreen(
                            listOf(SearchTarget("CUSTOMER", "customer-only", "CU-1", "Matching customer", "Matching site")),
                            PaddingValues(),
                            viewModel,
                            rememberNavController(),
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Search visits, follow-ups, and final records.").assertIsDisplayed()
        compose.onNodeWithText("Search customers, sites, equipment, templates, service plans, visits, final records, and follow-ups.").assertDoesNotExist()
        compose.onNodeWithTag("field-search-names-and-references").performTextInput("customer")
        compose.onNodeWithText("No matching work records.").assertIsDisplayed()
    }

    @Test fun employeeCanLinkExistingEquipmentWithoutCreatingMasterEquipment() {
        compose.runOnUiThread {
            val role = TeamRole.EMPLOYEE
            val application = compose.activity.application as ServiceLoopApplication
            val viewModel = ServiceLoopViewModel(application.container.repository) {}
            val equipment = EquipmentSummary("equipment", "Pump", "EQ-1", "P-01", "Site", "Customer", null, CustomerType.STANDARD)
            val context = EquipmentLinkContext("work", "visit", "site", "Site", listOf(equipment))
            compose.activity.setContent {
                ServiceLoopTheme(false) {
                    CompositionLocalProvider(LocalTeamRole provides role, LocalWorkspaceCapabilities provides role.workspaceCapabilities) {
                        EquipmentLinkScreen("work", context, PaddingValues(), UiState(loading = false), viewModel, rememberNavController())
                    }
                }
            }
        }
        compose.onNodeWithTag("link-equipment-equipment").assertIsDisplayed()
        compose.onNodeWithTag("link-add-equipment").assertDoesNotExist()
        compose.onNodeWithTag("save-and-link-equipment").assertDoesNotExist()
    }

    private fun assertWorkspace(role: TeamRole, register: Boolean, receive: Boolean, coordinate: Boolean) {
        compose.runOnUiThread { compose.activity.setTeamRole(role) }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("root-nav-customers").fetchSemanticsNodes().isNotEmpty() == register
        }
        compose.onNodeWithTag("root-nav-home").assertIsDisplayed()
        compose.onNodeWithTag("root-nav-work").assertIsDisplayed()
        if (register) compose.onNodeWithTag("root-nav-customers").assertIsDisplayed() else compose.onNodeWithTag("root-nav-customers").assertDoesNotExist()
        compose.onNodeWithTag("home-tab-TEAM").performClick()
        if (receive) compose.onNodeWithTag("team-import-work").assertIsDisplayed() else compose.onNodeWithTag("team-import-work").assertDoesNotExist()
        if (coordinate) compose.onNodeWithTag("team-dispatch").assertIsDisplayed() else compose.onNodeWithTag("team-dispatch").assertDoesNotExist()
        compose.onNodeWithTag("home-tab-DASHBOARD").performClick()
        compose.onNodeWithTag("root-nav-work").performClick()
        if (role.workspaceCapabilities.canCreateVisits) {
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("new-visit-work-bottom").fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithTag("new-visit-work-floating").fetchSemanticsNodes().isNotEmpty()
            }
        } else {
            compose.onNodeWithTag("new-visit-work-bottom").assertDoesNotExist()
            compose.onNodeWithTag("new-visit-work-floating").assertDoesNotExist()
        }
        compose.onNodeWithTag("root-nav-home").performClick()
    }
}
