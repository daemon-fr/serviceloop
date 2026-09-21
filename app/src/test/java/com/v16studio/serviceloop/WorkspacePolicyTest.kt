package com.v16studio.serviceloop

import com.v16studio.serviceloop.ui.TEAM_ROLE_OPTIONS
import com.v16studio.serviceloop.ui.TeamRole
import com.v16studio.serviceloop.ui.parseTeamRole
import com.v16studio.serviceloop.ui.workspaceCapabilities
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspacePolicyTest {
    @Test
    fun `roles use adopted visible order and exact copy`() {
        assertEquals(
            listOf(
                Triple(TeamRole.SOLO, "Solo", "I manage and perform my own service work."),
                Triple(TeamRole.SUBCONTRACTOR, "Subcontractor", "I manage my own work and also receive tasks from others."),
                Triple(TeamRole.EMPLOYEE, "Employee", "I work on tasks assigned by a coordinator."),
                Triple(TeamRole.TEAM_LEADER, "Team Leader", "I perform assigned work and coordinate other technicians."),
                Triple(TeamRole.COORDINATOR, "Coordinator", "I plan, assign, and oversee work for technicians."),
            ),
            TEAM_ROLE_OPTIONS.map { Triple(it.role, it.title, it.description) },
        )
    }

    @Test
    fun `capability matrix is exact`() {
        assertEquals(listOf(true, true, false, true, true, false, false, true, false), TeamRole.SOLO.workspaceCapabilities.values())
        assertEquals(listOf(true, true, true, true, true, false, false, true, true), TeamRole.SUBCONTRACTOR.workspaceCapabilities.values())
        assertEquals(listOf(false, false, true, true, false, false, false, false, false), TeamRole.EMPLOYEE.workspaceCapabilities.values())
        assertEquals(listOf(true, true, true, true, true, true, true, true, true), TeamRole.TEAM_LEADER.workspaceCapabilities.values())
        assertEquals(listOf(true, true, false, false, false, true, true, true, true), TeamRole.COORDINATOR.workspaceCapabilities.values())
    }

    @Test
    fun `legacy and corrupt roles canonicalize deterministically`() {
        assertEquals(TeamRole.SUBCONTRACTOR, parseTeamRole("MEMBER", false))
        assertEquals(TeamRole.COORDINATOR, parseTeamRole(null, true))
        assertEquals(TeamRole.SOLO, parseTeamRole(null, false))
        assertEquals(TeamRole.SOLO, parseTeamRole("CORRUPT", true))
        TeamRole.entries.forEach { role -> assertEquals(role, parseTeamRole(role.name, false)) }
        assertEquals(TeamRole.SUBCONTRACTOR, parseTeamRole(parseTeamRole("MEMBER", false).name, false))
    }

    private fun com.v16studio.serviceloop.ui.WorkspaceCapabilities.values() = listOf(
        showRegister,
        canManageRegister,
        canReceiveAssignedWork,
        canPerformFieldWork,
        canCreateLocalWork,
        canUseCoordinatorTools,
        canConcludeDelegatedWork,
        canManageTemplates,
        canExchangeTemplates,
    )
}
