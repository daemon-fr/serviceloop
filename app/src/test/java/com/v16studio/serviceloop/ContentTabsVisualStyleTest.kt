package com.v16studio.serviceloop

import org.junit.Assert.assertTrue
import org.junit.Test
class ContentTabsVisualStyleTest {
    @Test
    fun sharedTabsJoinTheLowerCanvasWithPaleEdgeToEdgeBaseline() {
        val components = productionKotlinFunctionSource("fun <T> ServiceLoopContentTabs")
        val tokens = productionKotlinSourceContaining("val tab=1.5.dp")

        assertTrue(components.contains(".background(if (active) c.canvas else Color.Transparent)"))
        assertTrue(components.contains("drawLine(c.outlineDecorative"))
        assertTrue(components.contains("ServiceLoopUiTokens.Stroke.tab"))
        assertTrue(tokens.contains("val tab=1.5.dp"))
        assertTrue(components.contains("indication = null"))
        assertTrue(components.contains("role = Role.Tab"))
        assertTrue(components.contains("this.selected = active"))
    }

    @Test
    fun rootWorkspacesShareOneTabHeaderWhileDetailTabsKeepTheirOwnSurface() {
        val app = productionKotlinSource("com/v16studio/serviceloop/ui")
        val root = productionKotlinFunctionSource("fun <T> ServiceLoopRootSecondaryTabs")
        val dailyOperations = productionKotlinSourceContaining("Column(Modifier.fillMaxWidth().background(colors.surface))")

        assertTrue(app.contains("CUSTOMERS(\"customers\", \"Register\")"))
        assertTrue(root.contains("height(64.dp).background(colors.surface).padding(top = 8.dp)"))
        assertTrue(root.contains("ServiceLoopContentTabs(options, selected, onSelected"))
        assertTrue(app.contains("ServiceLoopRootSecondaryTabs(WorkTab.entries"))
        assertTrue(app.contains("ServiceLoopRootSecondaryTabs(listOf(\"CUSTOMERS\""))
        assertTrue(app.contains("Column(Modifier.fillMaxWidth().background(colors.surface))"))
        assertTrue(dailyOperations.contains("Column(Modifier.fillMaxWidth().background(colors.surface))"))
    }
}
