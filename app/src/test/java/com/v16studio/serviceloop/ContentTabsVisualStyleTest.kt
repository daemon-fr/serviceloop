package com.v16studio.serviceloop

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContentTabsVisualStyleTest {
    @Test
    fun sharedTabsJoinTheLowerCanvasWithPaleEdgeToEdgeBaseline() {
        val components = File("src/main/java/com/v16studio/serviceloop/ui/designsystem/ServiceLoopComponents.kt").readText()
        val tokens = File("src/main/java/com/v16studio/serviceloop/ui/designsystem/ServiceLoopUiTokens.kt").readText()

        assertTrue(components.contains(".background(if (active) c.canvas else Color.Transparent)"))
        assertTrue(components.contains("drawLine(c.outlineDecorative"))
        assertTrue(components.contains("ServiceLoopUiTokens.Stroke.tab"))
        assertTrue(tokens.contains("val tab=1.5.dp"))
    }

    @Test
    fun commonCallersProvideAnUpperSurfaceThroughTheirTabBoundary() {
        val app = File("src/main/java/com/v16studio/serviceloop/ui/ServiceLoopApp.kt").readText()
        val dailyOperations = File("src/main/java/com/v16studio/serviceloop/ui/DailyOperationsUi.kt").readText()

        assertTrue(app.contains("CUSTOMERS(\"customers\", \"Register\")"))
        assertTrue(app.contains("Box(Modifier.fillMaxWidth().background(colors.surface).padding(top = 12.dp))"))
        assertTrue(app.contains("Column(Modifier.fillMaxWidth().background(colors.surface))"))
        assertTrue(dailyOperations.contains("Column(Modifier.fillMaxWidth().background(colors.surface))"))
    }
}
