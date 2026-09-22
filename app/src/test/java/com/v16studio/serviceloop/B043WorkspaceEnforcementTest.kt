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
        assertTrue(source.contains("No matching work records."))
        assertTrue(source.contains("capabilities.canManageRegister"))
        assertTrue(source.contains("OutlinedButton"))
    }

    @Test fun `employee assigned work cannot author local tasks or reusable templates`() {
        val visit = productionKotlinFunctionSource("internal fun VisitDetailScreen")
        assertTrue(visit.contains("capabilities.canPerformFieldWork && capabilities.canCreateLocalWork"))
        assertFalse(visit.contains("if(capabilities.canPerformFieldWork && detail.state in"))
        assertTrue(ui().contains("composable(\"template/list\") { WorkspaceGate(capabilities.canManageTemplates"))
        assertTrue(ui().contains("composable(\"visit/new\") { entry -> WorkspaceGate(capabilities.canCreateLocalWork"))
    }

    @Test fun `dispatch and storage formats remain frozen`() {
        val gradle = File("../build.gradle.kts").readText() + File("build.gradle.kts").readText()
        val database = productionKotlinSource("com/v16studio/serviceloop/data")
        assertTrue(gradle.contains("versionCode = 3"))
        assertTrue(gradle.contains("versionName = \"1.1.0\""))
        assertTrue(database.contains("version = 17"))
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
