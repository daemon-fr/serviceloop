package com.v16studio.v16service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.v16studio.v16service.data.V16_SERVICE_SYNC_MIME
import androidx.compose.ui.unit.dp
import com.v16studio.v16service.ui.summaryDaysUseSingleRow
import com.v16studio.v16service.ui.designsystem.V16ServiceUiTokens

class TeamRoleAndImportEntrySourceTest {
    private fun source(path: String) = File("src/main/$path").readText()

    @Test fun legacyCoordinatorPreferenceMigratesToPersistedRole() {
        val policy = productionKotlinSourceContaining("internal enum class TeamRole")
        assertTrue(policy.contains("SUBCONTRACTOR"))
        assertTrue(policy.contains("\"MEMBER\", TeamRole.SUBCONTRACTOR.name -> TeamRole.SUBCONTRACTOR"))
        assertTrue(policy.contains("if (legacyCoordinatorEnabled) TeamRole.COORDINATOR else TeamRole.SOLO"))
        assertTrue(policy.contains("putString(TEAM_ROLE, role.name).remove(COORDINATOR_ENABLED)"))
    }

    @Test fun workOverflowIsRemovedAndRoleGatewaysLiveOnHome() {
        val app = productionKotlinSourceContaining("V16ServiceDenseNavigableRow(\"History\"")
        val workspace = productionKotlinSourceContaining("internal fun WorkspaceHomeActions")
        assertFalse(productionKotlinSource("com/v16studio/v16service/ui").contains("WorkMoreMenu"))
        assertTrue(app.contains("V16ServiceDenseNavigableRow(\"History\""))
        assertFalse(workspace.contains("canReceiveAssignedWork"))
        assertTrue(workspace.contains("canUseCoordinatorTools"))
        val team = productionKotlinSourceContaining("internal fun TeamWorkspaceScreen")
        assertTrue(team.contains("Import shared data"))
        assertTrue(team.contains("role.workspaceCapabilities.canUseCoordinatorTools"))
        assertTrue(productionKotlinSourceContaining("composable(\"import\")").contains("V16ServiceSyncScreen"))
    }

    @Test fun externalPackageEntryReviewsInsteadOfAutoImporting() {
        val manifest = source("AndroidManifest.xml")
        val activity = source("java/com/v16studio/v16service/MainActivity.kt")
        assertTrue(manifest.contains("android.intent.action.VIEW"))
        assertTrue(manifest.contains("android.intent.action.SEND"))
        assertTrue(V16_SERVICE_SYNC_MIME == "application/vnd.v16studio.v16service+zip")
        assertTrue(manifest.contains(V16_SERVICE_SYNC_MIME))
        assertFalse(manifest.contains("application/vnd.v16service.work-package+json"))
        assertTrue(manifest.contains(".*\\.v16service"))
        assertTrue(activity.contains("incomingV16ServiceSync"))
        assertTrue(activity.contains("incomingV16ServiceSyncEvent++"))
        assertTrue(activity.contains("ACTION_SEND"))
        assertFalse(appSource().contains("Work packages can be received by Subcontractors, Employees, and Team Leaders."))
    }

    private fun appSource() = productionKotlinSource("com/v16studio/v16service/ui")

    @Test fun reminderDaysHaveSevenAcrossAndBalancedFallback() {
        val app = productionKotlinSourceContaining("private fun SummaryDayChoices")
        assertTrue(V16ServiceUiTokens.Size.touchMin >= 48.dp)
        assertTrue(summaryDaysUseSingleRow(360.dp, 1f))
        assertFalse(summaryDaysUseSingleRow(359.dp, 1f))
        assertFalse(summaryDaysUseSingleRow(360.dp, 1.3f))
        assertTrue(app.contains("days.take(4)"))
        assertTrue(app.contains("days.drop(4)"))
        assertTrue(app.contains("Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)"))
        assertTrue(app.contains("V16ServiceDayToggle"))
        assertTrue(app.contains("label = day.name.take(2)"))
        assertFalse(app.contains("FilterChip"))
    }

    @Test fun teamRoleSettingsKeepAdoptedChoicesAndMoveIdentityToItsOwnScreen() {
        val dispatch = productionKotlinSourceContaining("internal fun DispatchSettings")
        val policy = productionKotlinSourceContaining("internal val TEAM_ROLE_OPTIONS")
        val team = productionKotlinSourceContaining("internal fun TeamWorkspaceScreen")
        assertTrue(policy.contains("I manage and perform my own service work."))
        assertTrue(policy.contains("I perform assigned work and coordinate other technicians."))
        assertFalse(dispatch.contains("Send report copies to (optional)"))
        assertFalse(dispatch.contains("TechnicianIdentityContent()"))
        assertTrue(team.contains("TeamWorkspaceContent.ID"))
        assertTrue(team.contains("TeamWorkspaceContent.TRUSTED_IDS"))
    }

    @Test fun memberIdentityShowsTechnicianIdBeforeDesignationWithoutReportNameDuplication() {
        val dispatch = productionKotlinSourceContaining("internal fun TechnicianIdentityContent")
        val identity = productionKotlinFunctionSource("internal fun TechnicianIdentityContent", "private fun")
        assertTrue(identity.contains("Text(\"Technician ID\""))
        assertTrue(identity.contains("technician-id-value"))
        assertTrue(identity.contains("technician-designation"))
        assertTrue(identity.indexOf("Technician ID") < identity.indexOf("technician-id-value"))
        assertTrue(identity.indexOf("technician-id-value") < identity.indexOf("technician-designation"))
        assertFalse(identity.contains("Actual report name"))
        assertFalse(identity.contains("technician-report-name"))
        assertFalse(identity.contains("Business and report identity"))
        assertTrue(dispatch.contains("V16ServiceIcons.CheckFat,"))
        assertTrue(dispatch.contains("tint = if (selected) colors.action else colors.textMuted"))
        assertTrue(dispatch.contains("Coordinator tools are available from Home > Team page."))
        assertTrue(dispatch.contains("V16ServiceUiTokens.Layout.bodyGap"))
        assertTrue(dispatch.contains("team-role-helper"))
    }

    @Test fun appearanceIsARealPersistedRootThemeSetting() {
        val route = productionKotlinSourceContaining("composable(\"appearance\")")
        val settings = productionKotlinSourceContaining("V16ServiceDenseNavigableRow(\"Appearance\"")
        val theme = source("java/com/v16studio/v16service/ui/theme/Theme.kt")
        val prefs = source("java/com/v16studio/v16service/ui/theme/AppearancePreferences.kt")
        assertTrue(route.contains("composable(\"appearance\")"))
        assertTrue(settings.contains("V16ServiceDenseNavigableRow(\"Appearance\""))
        assertTrue(theme.contains("AppearanceMode.LIGHT -> false"))
        assertTrue(theme.contains("AppearanceMode.DARK -> true"))
        assertTrue(prefs.contains("const val PREFERENCES = \"v16service_appearance\""))
        assertTrue(prefs.contains("SYSTEM(\"System default\""))
    }
}
