package com.v16studio.serviceloop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorkFilterSelectorStyleTest {
    @Test
    fun selectorUsesFixedWhiteSurfaceAndReadableInkInBothAppearances() {
        val components = File("src/main/java/com/v16studio/serviceloop/ui/designsystem/ServiceLoopComponents.kt").readText()
        val tokens = File("src/main/java/com/v16studio/serviceloop/ui/designsystem/ServiceLoopUiTokens.kt").readText()
        val icons = File("src/main/java/com/v16studio/serviceloop/ui/icons/ServiceLoopIcons.kt").readText()

        assertTrue(components.contains("color = ServiceLoopFilterSelectorContract.surface"))
        assertTrue(components.contains("containerColor = ServiceLoopFilterSelectorContract.surface"))
        assertTrue(components.contains("ServiceLoopFilterSelectorContract.primaryInk"))
        assertTrue(components.contains("ServiceLoopFilterSelectorContract.secondaryInk"))
        assertTrue(components.contains("ServiceLoopFilterSelectorContract.accentInk"))
        assertTrue(components.contains(".background(if (isSelected) ServiceLoopFilterSelectorContract.selectedContainer else Color.Transparent)"))
        assertTrue(components.contains("ServiceLoopFilterSelectorContract.menuCheckSize"))
        assertTrue(tokens.contains("val surface = Color.White"))
        assertTrue(tokens.contains("val filterMenuCheck=16.dp"))
        assertTrue(components.contains("fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal"))
        assertTrue(components.contains("ServiceLoopIcons.Dropdown"))
        assertTrue(components.contains("ServiceLoopIcons.SelectionCheck"))
        assertFalse(components.contains("Text(\"▾\""))
        assertFalse(components.contains("Text(\"✓\""))
        assertTrue(icons.contains("val Dropdown = R.drawable.ic_sl_dropdown"))
        assertTrue(icons.contains("val SelectionCheck = R.drawable.ic_sl_selection_check"))
    }
}
