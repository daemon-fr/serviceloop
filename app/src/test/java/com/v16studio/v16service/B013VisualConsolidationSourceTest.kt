package com.v16studio.v16service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class B013VisualConsolidationSourceTest {
    @Test fun obsoleteUniversalRowsCannotReturn() {
        val production = productionKotlinSource("com/v16studio/v16service/ui")
        assertFalse(Regex("\\bSummaryRow\\s*\\(").containsMatchIn(production))
        assertFalse(Regex("\\bDailyRow\\s*\\(").containsMatchIn(production))
    }

    @Test fun brandGeometryUsesRestrainedCodeRenderedLines() {
        val components = productionKotlinSourceContaining("private fun BrandLine", "V16ServiceBrandStrip")
        assertTrue(components.contains("private fun BrandLine"))
        assertTrue(components.contains("drawLine"))
        assertTrue(components.contains("brand-left-line"))
        assertTrue(components.contains("brand-right-line"))
        val brandLine = components.substringAfter("private fun BrandLine").substringBefore("@Composable\nfun V16ServiceBrandStrip")
        assertEquals(1, Regex("drawLine\\(").findAll(brandLine).count())
        assertFalse(brandLine.contains("copy(alpha"))
        assertTrue(components.contains("v16ServiceBrandNeutral"))
        assertFalse(components.contains("TrailSquare"))
        assertFalse(components.contains("Random("))
        val raster = java.io.File(requireNotNull(System.getProperty("user.dir"))).let { if (it.name == "app") it else java.io.File(it, "app") }
            .resolve("src/main/res").walkTopDown().filter { it.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp") }.map { it.name }.toList()
        assertFalse(raster.any{it.contains("brand",true)||it.contains("logo",true)})
    }

    @Test fun sharedBrandStripRemainsInBothScaffoldsAndWorkUtilitiesAreCentralized() {
        val rootScaffold = productionKotlinFunctionSource("internal fun RootScaffold", "internal fun DetailScaffold")
        val detailScaffold = productionKotlinFunctionSource("internal fun DetailScaffold", "internal val LocalDetailBackInterceptor")
        val production = productionKotlinSource("com/v16studio/v16service/ui")
        assertTrue(rootScaffold.contains("V16ServiceBrandStrip()"))
        assertTrue(detailScaffold.contains("V16ServiceBrandStrip()"))
        assertFalse(production.contains("More Work actions"))
        assertFalse(production.contains("includeWorkDestinations"))
    }

    @Test fun equipmentPrivateNoteIsInlineBeforeActionsAndLegacyCardIsGone() {
        val screen = productionKotlinFunctionSource("internal fun EquipmentScreen", "internal fun servicePlanDueLabel")
        assertTrue(screen.indexOf("equipment-private-note") in 0 until screen.indexOf("equipment-actions"))
        assertFalse(screen.contains("Private equipment notes"))
        assertFalse(screen.contains("V16ServiceSurfaceCard(Modifier.padding(top=V16ServiceUiTokens.Space.section)"))
    }

    @Test fun visitProgressAlwaysShowsGroupedServicesAndCompleteCounts() {
        val screen = productionKotlinSource("com/v16studio/v16service/ui")
        assertFalse(screen.contains("service-group-toggle-"))
        assertTrue(screen.contains("serviceProgressGroupSummary"))
        assertTrue(screen.contains("serviceProgressStatusPhrase"))
        assertTrue(screen.contains("showDivider = index < group.items.lastIndex"))
        assertTrue(screen.contains("group.items.forEachIndexed"))
    }

    @Test fun ordinarySelectionsUseOneFramedPrimitiveWithoutFilterChipCallSites() {
        val production = productionKotlinSource("com/v16studio/v16service/ui")
        val components = production
        assertTrue(components.contains("fun V16ServiceSelectionOption"))
        assertTrue(components.contains("fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal"))
        assertTrue(components.contains("role = Role.RadioButton"))
        assertFalse(production.contains("FilterChip("))
    }

    @Test fun compactPresetFamilyStaysSeparateFromDescriptiveSelectionRows() {
        val components = productionKotlinFunctionSource("fun <T> V16ServicePresetChoiceGroup")
        val appearance = productionKotlinSourceContaining("options = AppearanceMode.entries.map")
        val reminders = productionKotlinSourceContaining("V16ServicePresetChoiceGroup(ReminderPreferences.APPOINTMENT_LEAD_PRESETS")
        val production = productionKotlinSource()
        assertTrue(components.contains("FlowRow("))
        assertTrue(components.contains("Role.RadioButton"))
        assertTrue(components.contains("V16ServiceUiTokens.Radius.pill"))
        assertTrue(productionKotlinSourceContaining("V16ServicePresetChoiceGroup(listOf(1 to \"1 day\"").isNotEmpty())
        assertTrue(reminders.contains("V16ServicePresetChoiceGroup(ReminderPreferences.APPOINTMENT_LEAD_PRESETS"))
        assertTrue(production.contains("AppointmentReminderSelector("))
        assertTrue(production.contains("label = \"Appointment reminder\""))
        assertTrue(production.contains("defaultAppointmentLeadMinutes"))
        assertFalse(production.contains("V16ServicePresetChoiceGroup(listOf(null to \"Default\",0 to \"Off\") + ReminderPreferences.APPOINTMENT_LEAD_PRESETS"))
        assertTrue(production.contains("V16ServicePresetChoiceGroup(listOf(\"DAYS\",\"WEEKS\",\"MONTHS\",\"YEARS\")"))
        assertTrue(appearance.contains("options = AppearanceMode.entries.map"))
    }
}
