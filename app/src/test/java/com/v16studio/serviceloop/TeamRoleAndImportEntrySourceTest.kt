package com.v16studio.serviceloop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.v16studio.serviceloop.data.SERVICE_LOOP_SYNC_MIME
import androidx.compose.ui.unit.dp
import com.v16studio.serviceloop.ui.summaryDaysUseSingleRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens

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
        val app = productionKotlinSourceContaining("ServiceLoopDenseNavigableRow(\"History\"")
        val workspace = productionKotlinSourceContaining("internal fun WorkspaceHomeActions")
        assertFalse(productionKotlinSource("com/v16studio/serviceloop/ui").contains("WorkMoreMenu"))
        assertTrue(app.contains("ServiceLoopDenseNavigableRow(\"History\""))
        assertFalse(workspace.contains("canReceiveAssignedWork"))
        assertTrue(workspace.contains("canUseCoordinatorTools"))
        val team = productionKotlinSourceContaining("internal fun TeamWorkspaceScreen")
        assertTrue(team.contains("Import shared data"))
        assertTrue(team.contains("role.workspaceCapabilities.canUseCoordinatorTools"))
        assertTrue(productionKotlinSourceContaining("composable(\"import\")").contains("ServiceLoopSyncScreen"))
    }

    @Test fun externalPackageEntryReviewsInsteadOfAutoImporting() {
        val manifest = source("AndroidManifest.xml")
        val activity = source("java/com/v16studio/serviceloop/MainActivity.kt")
        assertTrue(manifest.contains("android.intent.action.VIEW"))
        assertTrue(manifest.contains("android.intent.action.SEND"))
        assertTrue(SERVICE_LOOP_SYNC_MIME == "application/vnd.serviceloop.sync+zip")
        assertTrue(manifest.contains(SERVICE_LOOP_SYNC_MIME))
        assertFalse(manifest.contains("application/vnd.serviceloop.work-package+json"))
        assertTrue(manifest.contains(".*\\.slsync"))
        assertTrue(activity.contains("incomingServiceLoopSync"))
        assertTrue(activity.contains("incomingServiceLoopSyncEvent++"))
        assertTrue(activity.contains("ACTION_SEND"))
        assertFalse(appSource().contains("Work packages can be received by Subcontractors, Employees, and Team Leaders."))
    }

    private fun appSource() = productionKotlinSource("com/v16studio/serviceloop/ui")

    @Test fun reminderDaysHaveSevenAcrossAndBalancedFallback() {
        val app = productionKotlinSourceContaining("private fun SummaryDayChoices")
        assertTrue(ServiceLoopUiTokens.Size.touchMin >= 48.dp)
        assertTrue(summaryDaysUseSingleRow(360.dp, 1f))
        assertFalse(summaryDaysUseSingleRow(359.dp, 1f))
        assertFalse(summaryDaysUseSingleRow(360.dp, 1.3f))
        assertTrue(app.contains("days.take(4)"))
        assertTrue(app.contains("days.drop(4)"))
        assertTrue(app.contains("Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)"))
        assertTrue(app.contains("ServiceLoopDayToggle"))
        assertTrue(app.contains("label = day.name.take(2)"))
        assertFalse(app.contains("FilterChip"))
    }

    @Test fun teamRoleSettingsUseAdoptedLabelsAndEmbedReceiverIdentity() {
        val dispatch = productionKotlinSourceContaining("internal fun DispatchSettings")
        val policy = productionKotlinSourceContaining("internal val TEAM_ROLE_OPTIONS")
        assertTrue(policy.contains("I manage and perform my own service work."))
        assertTrue(policy.contains("I perform assigned work and coordinate other technicians."))
        assertTrue(dispatch.contains("Send report copies to (optional)"))
        assertTrue(dispatch.contains("canReceiveAssignedWork"))
        assertTrue(dispatch.contains("TechnicianIdentityContent"))
        assertFalse(dispatch.contains("Open Technician identity"))
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
        assertTrue(dispatch.contains("roleExplanation"))
        assertTrue(dispatch.contains("ServiceLoopUiTokens.Layout.bodyGap"))
        assertTrue(dispatch.contains("team-role-helper"))
    }

    @Test fun appearanceIsARealPersistedRootThemeSetting() {
        val route = productionKotlinSourceContaining("composable(\"appearance\")")
        val settings = productionKotlinSourceContaining("ServiceLoopDenseNavigableRow(\"Appearance\"")
        val theme = source("java/com/v16studio/serviceloop/ui/theme/Theme.kt")
        val prefs = source("java/com/v16studio/serviceloop/ui/theme/AppearancePreferences.kt")
        assertTrue(route.contains("composable(\"appearance\")"))
        assertTrue(settings.contains("ServiceLoopDenseNavigableRow(\"Appearance\""))
        assertTrue(theme.contains("AppearanceMode.LIGHT -> false"))
        assertTrue(theme.contains("AppearanceMode.DARK -> true"))
        assertTrue(prefs.contains("const val PREFERENCES = \"serviceloop_appearance\""))
        assertTrue(prefs.contains("SYSTEM(\"System default\""))
    }
}
