package com.v16studio.serviceloop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorkFilterSelectorStyleTest {
    @Test
    fun selectorUsesCanonicalSelectionContainerAndPhosphorIcons() {
        val components = File("src/main/java/com/v16studio/serviceloop/ui/designsystem/ServiceLoopComponents.kt").readText()
        val icons = File("src/main/java/com/v16studio/serviceloop/ui/icons/ServiceLoopIcons.kt").readText()

        assertTrue(components.contains("color = colors.selection"))
        assertTrue(components.contains(".background(if (isSelected) colors.selection else Color.Transparent)"))
        assertTrue(components.contains("fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal"))
        assertTrue(components.contains("ServiceLoopIcons.Dropdown"))
        assertTrue(components.contains("ServiceLoopIcons.SelectionCheck"))
        assertFalse(components.contains("Text(\"▾\""))
        assertFalse(components.contains("Text(\"✓\""))
        assertTrue(icons.contains("val Dropdown = R.drawable.ic_sl_dropdown"))
        assertTrue(icons.contains("val SelectionCheck = R.drawable.ic_sl_selection_check"))
    }
}
