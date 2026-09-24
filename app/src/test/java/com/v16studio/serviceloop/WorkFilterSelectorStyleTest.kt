package com.v16studio.serviceloop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class WorkFilterSelectorStyleTest {
    @Test
    fun ownerR3SurfacesUseSharedSelectorsAndSelectionAliases() {
        val production = productionKotlinSource()
        val components = production
        val history = production
        val dispatch = production
        val equipment = production
        val daily = production

        assertTrue(components.contains("ServiceLoopIcons.SelectionEmpty"))
        assertTrue(components.contains("ServiceLoopIcons.SelectionChecked"))
        assertTrue(components.contains("ServiceLoopIcons.CheckFat"))
        assertTrue(components.contains("if (selected) c.action else c.textMuted"))
        assertFalse(components.contains("Checkbox(selectionChecked"))
        assertTrue(history.contains("ServiceLoopFilterSelectorRow"))
        assertTrue(history.contains("ServiceLoopFilterSelector(\"Rows\""))
        assertTrue(dispatch.contains("ServiceLoopFilterSelector(\"Status\""))
        assertTrue(equipment.contains("Text(detail.privateNote"))
        assertTrue(daily.contains("ServiceLoopDangerTonalButton(\"End plan\""))
        assertTrue(daily.contains("Active follow-ups"))
    }

    @Test
    fun selectorUsesThemeSurfaceAndReadableInkInBothAppearances() {
        val components = productionKotlinSourceContaining("val selectorSurface = colors.surface")
        val tokens = productionKotlinSourceContaining("val filterMenuCheck=16.dp")
        val icons = productionKotlinSourceContaining("val Dropdown = R.drawable.ic_sl_dropdown")

        assertTrue(components.contains("color = selectorSurface"))
        assertTrue(components.contains("containerColor = selectorSurface"))
        assertTrue(components.contains("val selectorSurface = colors.surface"))
        assertTrue(components.contains("val selectorOutline = serviceLoopFilterSelectorOutline(colors)"))
        assertTrue(components.contains("val primaryInk = colors.textPrimary"))
        assertTrue(components.contains("val accentInk = colors.action"))
        assertTrue(components.contains(".background(if (isSelected) selectedContainer else Color.Transparent)"))
        assertTrue(components.contains("ServiceLoopFilterSelectorContract.menuCheckSize"))
        assertTrue(tokens.contains("val filterMenuCheck=16.dp"))
        assertTrue(tokens.contains("if (colors.surface == ServiceLoopUiTokens.LightColors.surface) colors.recordBorder else colors.selectionOutline"))
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

    @Test
    fun b013R31SelectionGeometryAndAuditedCommandGroupsUseSharedContracts() {
        val components = productionKotlinSourceContaining("entity-record-selection")
        val app = productionKotlinSourceContaining("Open Android notification settings")
        val dispatch = productionKotlinSourceContaining("ServiceLoopActionStack")
        val daily = productionKotlinSourceContaining("ServiceLoopActionStack")
        val stage4 = productionKotlinSourceContaining("ServiceLoopActionStack")
        val coordinator = productionKotlinSource("com/v16studio/serviceloop/ui")

        assertTrue(components.contains("selectionTestTag: String = \"entity-record-selection\""))
        assertTrue(components.contains(".testTag(selectionTestTag)"))
        assertTrue(components.contains("Modifier.testTag(glyphTestTag)"))
        assertTrue(components.contains(".testTag(\"entity-record-title\")"))
        assertTrue(components.contains("Modifier.fillMaxWidth().padding(ServiceLoopUiTokens.Space.lg)"))
        assertTrue(components.contains("ServiceLoopCheckbox("))
        assertTrue(components.contains(".size(ServiceLoopUiTokens.Size.checkboxGlyph)"))
        assertTrue(components.contains("glyphTestTag = \"entity-record-selection-icon\""))
        assertTrue(app.contains("Open Android notification settings"))
        assertTrue(app.contains("ServiceLoopActionStack"))
        assertTrue(dispatch.contains("ServiceLoopActionStack"))
        assertTrue(daily.contains("ServiceLoopActionStack"))
        assertTrue(stage4.contains("ServiceLoopActionStack"))
        assertFalse(coordinator.contains("fun DispatchChoiceDialog"))
    }
}
