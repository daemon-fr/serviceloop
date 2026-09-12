package com.v16studio.serviceloop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class B013VisualConsolidationSourceTest {
    private val root = File(requireNotNull(System.getProperty("user.dir"))).let { if (it.name == "app") it.parentFile!! else it }
    private fun source(path:String)=File(root,path).readText()

    @Test fun obsoleteUniversalRowsCannotReturn() {
        val production=File(root,"app/src/main/java/com/v16studio/serviceloop/ui").walkTopDown().filter{it.extension=="kt"}.joinToString("\n"){it.readText()}
        assertFalse(Regex("\\bSummaryRow\\s*\\(").containsMatchIn(production))
        assertFalse(Regex("\\bDailyRow\\s*\\(").containsMatchIn(production))
    }

    @Test fun brandGeometryUsesRestrainedCodeRenderedLines() {
        val components=source("app/src/main/java/com/v16studio/serviceloop/ui/designsystem/ServiceLoopComponents.kt")
        assertTrue(components.contains("private fun BrandLine"))
        assertTrue(components.contains("drawLine"))
        assertTrue(components.contains("brand-left-line"))
        assertTrue(components.contains("brand-right-line"))
        assertFalse(components.contains("TrailSquare"))
        assertFalse(components.contains("Random("))
        val raster=File(root,"app/src/main/res").walkTopDown().filter{it.extension.lowercase() in setOf("png","jpg","jpeg","webp")}.map{it.name}.toList()
        assertFalse(raster.any{it.contains("brand",true)||it.contains("logo",true)})
    }

    @Test fun sharedBrandStripRemainsInBothScaffoldsAndWorkUtilitiesAreCentralized() {
        val app=source("app/src/main/java/com/v16studio/serviceloop/ui/ServiceLoopApp.kt")
        assertTrue(app.substringAfter("private fun RootScaffold").substringBefore("internal fun DetailScaffold").contains("ServiceLoopBrandStrip()"))
        assertTrue(app.substringAfter("internal fun DetailScaffold").substringBefore("internal val LocalDetailBackInterceptor").contains("ServiceLoopBrandStrip()"))
        assertFalse(app.contains("More Work actions"))
        assertFalse(app.contains("includeWorkDestinations"))
    }

    @Test fun equipmentPrivateNoteIsInlineBeforeActionsAndLegacyCardIsGone() {
        val app=source("app/src/main/java/com/v16studio/serviceloop/ui/ServiceLoopApp.kt")
        val screen=app.substringAfter("private fun EquipmentScreen").substringBefore("internal fun InspectionScreen")
        assertTrue(screen.indexOf("equipment-private-note") in 0 until screen.indexOf("equipment-actions"))
        assertFalse(screen.contains("Private equipment notes"))
        assertFalse(screen.contains("ServiceLoopSurfaceCard(Modifier.padding(top=ServiceLoopUiTokens.Space.section)"))
    }
}
