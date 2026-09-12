package com.v16studio.serviceloop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertTrue(manifest.contains("application/vnd.serviceloop.work+json"))
        assertTrue(manifest.contains(".*\\.slwork"))
        assertTrue(activity.contains("incomingWorkPackage"))
    }

    @Test fun reminderDaysHaveSevenAcrossAndBalancedFallback() {
        val app = source("java/com/v16studio/serviceloop/ui/ServiceLoopApp.kt")
        assertTrue(app.contains("if (maxWidth >= 350.dp)"))
        assertTrue(app.contains("days.take(4)"))
        assertTrue(app.contains("days.drop(4)"))
    }
}
