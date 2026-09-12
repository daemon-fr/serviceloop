package com.v16studio.serviceloop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.v16studio.serviceloop.data.WORK_PACKAGE_MIME
import androidx.compose.ui.unit.dp
import com.v16studio.serviceloop.ui.summaryDaysUseSingleRow
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens

class TeamRoleAndImportEntrySourceTest {
    private fun source(path: String) = File("src/main/$path").readText()

    @Test fun legacyCoordinatorPreferenceMigratesToPersistedRole() {
        val dispatch = source("java/com/v16studio/serviceloop/ui/DispatchUi.kt")
        assertTrue(dispatch.contains("enum class TeamRole { SOLO, MEMBER, COORDINATOR }"))
        assertTrue(dispatch.contains("if (prefs.getBoolean(COORDINATOR_ENABLED, false)) TeamRole.COORDINATOR else TeamRole.SOLO"))
        assertTrue(dispatch.contains("putString(TEAM_ROLE, migrated.name).remove(COORDINATOR_ENABLED)"))
    }

    @Test fun workOverflowIsRemovedAndRoleGatewaysLiveOnHome() {
        val app = source("java/com/v16studio/serviceloop/ui/ServiceLoopApp.kt")
        val coordinator = source("java/com/v16studio/serviceloop/ui/DispatchCoordinatorUiV2.kt")
        assertFalse(app.contains("WorkMoreMenu"))
        assertTrue(app.contains("ServiceLoopDenseNavigableRow(\"History\""))
        assertTrue(coordinator.contains("role==TeamRole.COORDINATOR"))
        assertTrue(coordinator.contains("role==TeamRole.MEMBER"))
        assertTrue(coordinator.contains("member-import-work-package"))
    }

    @Test fun externalPackageEntryReviewsInsteadOfAutoImporting() {
        val manifest = source("AndroidManifest.xml")
        val activity = source("java/com/v16studio/serviceloop/MainActivity.kt")
        assertTrue(manifest.contains("android.intent.action.VIEW"))
        assertTrue(manifest.contains("android.intent.action.SEND"))
        assertTrue(WORK_PACKAGE_MIME == "application/vnd.serviceloop.work-package+json")
        assertTrue(manifest.contains(WORK_PACKAGE_MIME))
        assertFalse(manifest.contains("application/vnd.serviceloop.work+json"))
        assertTrue(manifest.contains(".*\\.slwork"))
        assertTrue(activity.contains("incomingWorkPackage"))
        assertTrue(activity.contains("incomingWorkPackageEvent++"))
        assertTrue(activity.contains("ACTION_SEND"))
        assertTrue(appSource().contains("Work packages are imported in Member mode."))
        assertTrue(appSource().contains("external-package-open-role"))
    }

    private fun appSource() = source("java/com/v16studio/serviceloop/ui/ServiceLoopApp.kt")

    @Test fun reminderDaysHaveSevenAcrossAndBalancedFallback() {
        val app = source("java/com/v16studio/serviceloop/ui/ServiceLoopApp.kt")
        assertTrue(ServiceLoopUiTokens.Size.touchMin >= 48.dp)
        assertTrue(summaryDaysUseSingleRow(360.dp, 1f))
        assertFalse(summaryDaysUseSingleRow(359.dp, 1f))
        assertFalse(summaryDaysUseSingleRow(360.dp, 1.3f))
        assertTrue(app.contains("days.take(4)"))
        assertTrue(app.contains("days.drop(4)"))
        assertTrue(app.contains("Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)"))
    }
}
