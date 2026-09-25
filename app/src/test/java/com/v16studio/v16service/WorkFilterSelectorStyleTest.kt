package com.v16studio.v16service

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

        assertTrue(components.contains("V16ServiceIcons.SelectionEmpty"))
        assertTrue(components.contains("V16ServiceIcons.SelectionChecked"))
        assertTrue(components.contains("V16ServiceIcons.CheckFat"))
        assertTrue(components.contains("if (selected) c.action else c.textMuted"))
        assertFalse(components.contains("Checkbox(selectionChecked"))
        assertTrue(history.contains("V16ServiceFilterSelectorRow"))
        assertTrue(history.contains("V16ServiceFilterSelector(\"Rows\""))
        assertTrue(dispatch.contains("V16ServiceFilterSelector(\"Status\""))
        assertTrue(equipment.contains("Text(detail.privateNote"))
        assertTrue(daily.contains("V16ServiceDangerTonalButton(\"End plan\""))
        assertTrue(daily.contains("Active follow-ups"))
    }

    @Test
    fun selectorUsesThemeSurfaceAndReadableInkInBothAppearances() {
        val components = productionKotlinSourceContaining("val selectorSurface = colors.surface")
        val tokens = productionKotlinSourceContaining("val filterMenuCheck=16.dp")
        val icons = productionKotlinSourceContaining("val Dropdown = R.drawable.ic_v16s_dropdown")

        assertTrue(components.contains("color = selectorSurface"))
        assertTrue(components.contains("containerColor = selectorSurface"))
        assertTrue(components.contains("val selectorSurface = colors.surface"))
        assertTrue(components.contains("val selectorOutline = v16ServiceFilterSelectorOutline(colors)"))
        assertTrue(components.contains("val primaryInk = colors.textPrimary"))
        assertTrue(components.contains("val accentInk = colors.action"))
        assertTrue(components.contains(".background(if (isSelected) selectedContainer else Color.Transparent)"))
        assertTrue(components.contains("V16ServiceFilterSelectorContract.menuCheckSize"))
        assertTrue(tokens.contains("val filterMenuCheck=16.dp"))
        assertTrue(tokens.contains("if (colors.surface == V16ServiceUiTokens.LightColors.surface) colors.recordBorder else colors.selectionOutline"))
        assertTrue(components.contains("fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal"))
        assertTrue(components.contains("V16ServiceIcons.Dropdown"))
        assertTrue(components.contains("V16ServiceIcons.SelectionCheck"))
        assertFalse(components.contains("Text(\"▾\""))
        assertFalse(components.contains("Text(\"✓\""))
        assertTrue(icons.contains("val Dropdown = R.drawable.ic_v16s_dropdown"))
        assertTrue(icons.contains("val SelectionCheck = R.drawable.ic_v16s_selection_check"))
        assertTrue(icons.contains("val SelectionEmpty = R.drawable.ic_v16s_selection_empty"))
        assertTrue(icons.contains("val SelectionChecked = R.drawable.ic_v16s_selection_checked"))
    }

    @Test
    fun b013R31SelectionGeometryAndAuditedCommandGroupsUseSharedContracts() {
        val components = productionKotlinSourceContaining("entity-record-selection")
        val app = productionKotlinSourceContaining("Open Android notification settings")
        val dispatch = productionKotlinSourceContaining("V16ServiceActionStack")
        val daily = productionKotlinSourceContaining("V16ServiceActionStack")
        val stage4 = productionKotlinSourceContaining("V16ServiceActionStack")
        val coordinator = productionKotlinSource("com/v16studio/v16service/ui")

        assertTrue(components.contains("selectionTestTag: String = \"entity-record-selection\""))
        assertTrue(components.contains(".testTag(selectionTestTag)"))
        assertTrue(components.contains("Modifier.testTag(glyphTestTag)"))
        assertTrue(components.contains(".testTag(\"entity-record-title\")"))
        assertTrue(components.contains("Modifier.fillMaxWidth().padding(V16ServiceUiTokens.Space.lg)"))
        assertTrue(components.contains("V16ServiceCheckbox("))
        assertTrue(components.contains(".size(V16ServiceUiTokens.Size.checkboxGlyph)"))
        assertTrue(components.contains("glyphTestTag = \"entity-record-selection-icon\""))
        assertTrue(app.contains("Open Android notification settings"))
        assertTrue(app.contains("V16ServiceActionStack"))
        assertTrue(dispatch.contains("V16ServiceActionStack"))
        assertTrue(daily.contains("V16ServiceActionStack"))
        assertTrue(stage4.contains("V16ServiceActionStack"))
        assertFalse(coordinator.contains("fun DispatchChoiceDialog"))
    }
}
