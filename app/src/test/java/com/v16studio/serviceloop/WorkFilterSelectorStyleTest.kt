package com.v16studio.serviceloop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorkFilterSelectorStyleTest {
    @Test
    fun ownerR3SurfacesUseSharedSelectorsAndSelectionAliases() {
        val components = File("src/main/java/com/v16studio/serviceloop/ui/designsystem/ServiceLoopComponents.kt").readText()
        val history = File("src/main/java/com/v16studio/serviceloop/ui/Stage4Ui.kt").readText()
        val dispatch = File("src/main/java/com/v16studio/serviceloop/ui/DispatchCoordinatorUiV2.kt").readText()
        val equipment = File("src/main/java/com/v16studio/serviceloop/ui/ServiceLoopApp.kt").readText()
        val daily = File("src/main/java/com/v16studio/serviceloop/ui/DailyOperationsUi.kt").readText()

        assertTrue(components.contains("ServiceLoopIcons.SelectionEmpty"))
        assertTrue(components.contains("ServiceLoopIcons.SelectionChecked"))
        assertFalse(components.contains("Checkbox(selectionChecked"))
        assertTrue(history.contains("ServiceLoopFilterSelectorRow"))
        assertTrue(history.contains("ServiceLoopFilterSelector(\"Rows\""))
        assertTrue(dispatch.contains("ServiceLoopFilterSelector(\"Status\""))
        assertTrue(equipment.contains("Text(detail.privateNote"))
        assertTrue(daily.contains("ServiceLoopDangerTonalButton(\"End plan\""))
        assertTrue(daily.contains("Active follow-ups"))
    }

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
        assertTrue(tokens.contains("val outline = Color(0xFF67B9B8)"))
        assertTrue(tokens.contains("val filterMenuCheck=16.dp"))
        assertTrue(components.contains("fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal"))
        assertTrue(components.contains("ServiceLoopIcons.Dropdown"))
        assertTrue(components.contains("ServiceLoopIcons.SelectionCheck"))
        assertFalse(components.contains("Text(\"▾\""))
        assertFalse(components.contains("Text(\"✓\""))
        assertTrue(icons.contains("val Dropdown = R.drawable.ic_sl_dropdown"))
        assertTrue(icons.contains("val SelectionCheck = R.drawable.ic_sl_selection_check"))
        assertTrue(icons.contains("val SelectionEmpty = R.drawable.ic_sl_selection_empty"))
        assertTrue(icons.contains("val SelectionChecked = R.drawable.ic_sl_selection_checked"))
    }
}
