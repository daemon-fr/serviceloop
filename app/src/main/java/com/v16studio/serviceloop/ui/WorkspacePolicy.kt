package com.v16studio.serviceloop.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

internal enum class TeamRole {
    SOLO,
    SUBCONTRACTOR,
    EMPLOYEE,
    TEAM_LEADER,
    COORDINATOR,
}

internal data class TeamRoleOption(
    val role: TeamRole,
    val title: String,
    val description: String,
)

internal val TEAM_ROLE_OPTIONS = listOf(
    TeamRoleOption(TeamRole.SOLO, "Solo", "I manage and perform my own service work."),
    TeamRoleOption(TeamRole.SUBCONTRACTOR, "Subcontractor", "I manage my own work and also receive tasks from others."),
    TeamRoleOption(TeamRole.EMPLOYEE, "Employee", "I work on tasks assigned by a coordinator."),
    TeamRoleOption(TeamRole.TEAM_LEADER, "Team Leader", "I perform assigned work and coordinate other technicians."),
    TeamRoleOption(TeamRole.COORDINATOR, "Coordinator", "I plan, assign, and oversee work for technicians."),
)

internal data class WorkspaceCapabilities(
    val showRegister: Boolean,
    val canManageRegister: Boolean,
    val canReceiveAssignedWork: Boolean,
    val canPerformFieldWork: Boolean,
    val canCreateLocalWork: Boolean,
    val canUseCoordinatorTools: Boolean,
    val canConcludeDelegatedWork: Boolean,
    val canManageTemplates: Boolean,
    val canExchangeTemplates: Boolean,
)

internal val TeamRole.workspaceCapabilities: WorkspaceCapabilities
    get() = when (this) {
        TeamRole.SOLO -> WorkspaceCapabilities(true, true, false, true, true, false, false, true, false)
        TeamRole.SUBCONTRACTOR -> WorkspaceCapabilities(true, true, true, true, true, false, false, true, true)
        TeamRole.EMPLOYEE -> WorkspaceCapabilities(false, false, true, true, false, false, false, false, false)
        TeamRole.TEAM_LEADER -> WorkspaceCapabilities(true, true, true, true, true, true, true, true, true)
        TeamRole.COORDINATOR -> WorkspaceCapabilities(true, true, false, false, false, true, true, true, true)
    }

internal fun parseTeamRole(stored: String?, legacyCoordinatorEnabled: Boolean): TeamRole = when (stored) {
    null -> if (legacyCoordinatorEnabled) TeamRole.COORDINATOR else TeamRole.SOLO
    TeamRole.SOLO.name -> TeamRole.SOLO
    "MEMBER", TeamRole.SUBCONTRACTOR.name -> TeamRole.SUBCONTRACTOR
    TeamRole.EMPLOYEE.name -> TeamRole.EMPLOYEE
    TeamRole.TEAM_LEADER.name -> TeamRole.TEAM_LEADER
    TeamRole.COORDINATOR.name -> TeamRole.COORDINATOR
    else -> TeamRole.SOLO
}

internal fun Context.teamRole(): TeamRole {
    val preferences = getSharedPreferences(DISPATCH_PREFS, 0)
    val stored = preferences.getString(TEAM_ROLE, null)
    val role = parseTeamRole(stored, preferences.getBoolean(COORDINATOR_ENABLED, false))
    if (stored != role.name || preferences.contains(COORDINATOR_ENABLED)) {
        preferences.edit().putString(TEAM_ROLE, role.name).remove(COORDINATOR_ENABLED).apply()
    }
    return role
}

internal fun Context.setTeamRole(role: TeamRole) {
    getSharedPreferences(DISPATCH_PREFS, 0)
        .edit()
        .putString(TEAM_ROLE, role.name)
        .remove(COORDINATOR_ENABLED)
        .apply()
}

@Composable
internal fun rememberTeamRoleState(context: Context): State<TeamRole> {
    val preferences = remember(context) { context.getSharedPreferences(DISPATCH_PREFS, 0) }
    val role = remember(context) { mutableStateOf(context.teamRole()) }
    DisposableEffect(preferences, context) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == TEAM_ROLE || key == COORDINATOR_ENABLED) role.value = context.teamRole()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return role
}

internal val LocalTeamRole = compositionLocalOf { TeamRole.SOLO }
internal val LocalWorkspaceCapabilities = compositionLocalOf { TeamRole.SOLO.workspaceCapabilities }
