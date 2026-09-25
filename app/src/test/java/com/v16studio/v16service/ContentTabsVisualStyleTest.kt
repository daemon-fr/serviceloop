package com.v16studio.v16service

import org.junit.Assert.assertTrue
import org.junit.Test
class ContentTabsVisualStyleTest {
    @Test
    fun sharedTabsJoinTheLowerCanvasWithPaleEdgeToEdgeBaseline() {
        val components = productionKotlinFunctionSource("fun <T> V16ServiceContentTabs")
        val tokens = productionKotlinSourceContaining("val tab=1.5.dp")

        assertTrue(components.contains(".background(if (active) c.canvas else Color.Transparent)"))
        assertTrue(components.contains("drawLine(c.outlineDecorative"))
        assertTrue(components.contains("V16ServiceUiTokens.Stroke.tab"))
        assertTrue(tokens.contains("val tab=1.5.dp"))
        assertTrue(components.contains("indication = null"))
        assertTrue(components.contains("role = Role.Tab"))
        assertTrue(components.contains("this.selected = active"))
    }

    @Test
    fun rootWorkspacesShareOneTabHeaderWhileDetailTabsKeepTheirOwnSurface() {
        val app = productionKotlinSource("com/v16studio/v16service/ui")
        val root = productionKotlinFunctionSource("fun <T> V16ServiceRootSecondaryTabs")
        val dailyOperations = productionKotlinSourceContaining("Column(Modifier.fillMaxWidth().background(colors.surface))")

        assertTrue(app.contains("CUSTOMERS(\"customers\", \"Register\")"))
        assertTrue(root.contains("height(64.dp).background(colors.surface).padding(top = 8.dp)"))
        assertTrue(root.contains("V16ServiceContentTabs(options, selected, onSelected"))
        assertTrue(app.contains("V16ServiceRootSecondaryTabs(WorkTab.entries"))
        assertTrue(app.contains("V16ServiceRootSecondaryTabs(listOf(\"CUSTOMERS\""))
        assertTrue(app.contains("Column(Modifier.fillMaxWidth().background(colors.surface))"))
        assertTrue(dailyOperations.contains("Column(Modifier.fillMaxWidth().background(colors.surface))"))
    }
}
