package com.v16studio.serviceloop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class B043WorkspaceEnforcementTest {
    private fun ui() = productionKotlinSource("com/v16studio/serviceloop/ui")

    @Test fun `navigation gates every role-sensitive route family`() {
        val source = productionKotlinSourceContaining("private fun WorkspaceGate")
        listOf(
            "capabilities.canManageRegister",
            "capabilities.canCreateLocalWork",
            "capabilities.canPerformFieldWork",
            "capabilities.canUseCoordinatorTools",
            "capabilities.canReceiveAssignedWork",
            "capabilities.canManageTemplates",
        ).forEach { assertTrue(source.contains(it)) }
        listOf(
            "customer/new", "equipment/move/{id}", "visit/new", "inspection/{id}", "field/{workItemId}",
            "review/{visitId}", "work/{workItemId}/link-equipment", "dispatch/technicians", "dispatch/import", "csv/import",
        ).forEach { assertTrue(source.contains(it)) }
    }

    @Test fun `employee projection hides register search and master mutations`() {
        val source = ui()
        assertTrue(source.contains("capabilities.showRegister"))
        assertTrue(source.contains("listOf(SearchCategory.VISIT, SearchCategory.FOLLOW_UP, SearchCategory.FINAL_RECORD)"))
        assertTrue(source.contains("if(capabilities.canManageRegister) OutlinedButton"))
        assertTrue(source.contains("if (capabilities.canManageRegister) item { OutlinedButton"))
    }

    @Test fun `dispatch and storage formats remain frozen`() {
        val gradle = File("../build.gradle.kts").readText() + File("build.gradle.kts").readText()
        val database = productionKotlinSource("com/v16studio/serviceloop/data")
        assertTrue(gradle.contains("versionCode = 2"))
        assertTrue(gradle.contains("versionName = \"1.0.1\""))
        assertTrue(database.contains("version = 15"))
        assertFalse(ui().contains("TeamRole.MEMBER"))
    }

    @Test fun `visit maps uses captured address and neutral handoff copy`() {
        val visit = productionKotlinSourceContaining("internal fun VisitDetailScreen")
        val handoff = productionKotlinSourceContaining("internal fun visitMapsIntent")
        assertTrue(visit.contains("visitMapsIntent(detail.siteAddress)"))
        assertTrue(visit.contains("enabled = detail.siteAddress.isNotBlank()"))
        assertTrue(visit.contains("\"Opened maps.\""))
        assertTrue(visit.contains("\"No compatible maps app is available.\""))
        assertTrue(handoff.contains("geo:0,0?q=${'$'}{Uri.encode(capturedAddress)}"))
    }
}
