package com.v16studio.serviceloop

import com.v16studio.serviceloop.domain.appointmentEpochMillis
import com.v16studio.serviceloop.ui.NewVisitDraftSnapshot
import com.v16studio.serviceloop.ui.NewVisitTaskDraft
import com.v16studio.serviceloop.ui.newVisitDraftIsDirty
import com.v16studio.serviceloop.ui.parseAppointmentTimeInput
import com.v16studio.serviceloop.domain.WorkSubjectType
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class B037OwnerReviewCorrectionsTest {
    private val zone = ZoneId.of("Europe/Bucharest")

    @Test
    fun appointmentTimeAcceptsBlankAndStrictTwentyFourHourValuesOnly() {
        assertNull(parseAppointmentTimeInput(""))
        assertNotNull(parseAppointmentTimeInput("09:00"))
        assertNotNull(parseAppointmentTimeInput("23:59"))
        assertNull(parseAppointmentTimeInput("25:10"))
        assertNull(parseAppointmentTimeInput("9:75"))
        assertTrue(runCatching { appointmentEpochMillis("2026-09-19", "25:10", zone) }.isFailure)
    }

    @Test
    fun visitDraftDirtyStateComparesBusinessFieldsAndReturnsCleanWhenRestored() {
        val baseline = snapshot()
        assertFalse(newVisitDraftIsDirty(baseline, baseline))
        assertTrue(newVisitDraftIsDirty(baseline, baseline.copy(date = "2026-09-20")))
        assertTrue(newVisitDraftIsDirty(baseline, baseline.copy(appointmentTime = "09:30")))
        val restoredTime = baseline.copy(appointmentTime = "09:30").copy(appointmentTime = "")
        assertFalse(newVisitDraftIsDirty(baseline, restoredTime))
        assertTrue(newVisitDraftIsDirty(baseline, baseline.copy(customerName = "New customer")))
        assertTrue(newVisitDraftIsDirty(baseline, baseline.copy(tasks = listOf(NewVisitTaskDraft("Inspect", WorkSubjectType.SITE, null, "", null)))))
        assertFalse(newVisitDraftIsDirty(baseline, baseline.copy(selectedPlanIds = emptySet())))
    }

    @Test
    fun createVisitDirtySnapshotDoesNotContainPresentationOnlyModeOrSiteSearch() {
        val source = productionKotlinFunctionSource("internal data class NewVisitDraftSnapshot", "@Composable\ninternal fun InspectionChecklistSelector")
        assertFalse(source.contains("siteQuery"))
        assertFalse(source.contains("mode:"))
        assertTrue(productionKotlinSource("com/v16studio/serviceloop/ui").contains("newVisitDraftIsDirty(initialDraft, currentDraft)"))
    }

    @Test
    fun ownerReviewSurfaceContractsAreCentralized() {
        val production = productionKotlinSource("com/v16studio/serviceloop/ui")
        val fieldAction = productionKotlinFunctionSource("fun ServiceLoopFieldAction", "@Composable fun ServiceLoopTextAction")
        assertTrue(fieldAction.contains("ServiceLoopButtonContract.secondaryContainer(c, enabled)"))
        assertTrue(fieldAction.contains("ServiceLoopButtonContract.secondaryInk(c, enabled)"))
        assertFalse(fieldAction.contains("c.selection"))
        assertFalse(fieldAction.contains(".border("))
        assertTrue(production.contains("singleRow: Boolean = false"))
        assertTrue(production.contains("singleRow = true"))
        assertTrue(production.contains("ServiceLoopIcons.PlusBold"))
        assertFalse(production.contains("Follow-up state never changes a service plan or historical report."))
    }

    @Test
    fun createVisitCustomerTabsShareOneUpperSurfaceBand() {
        val screen = productionKotlinFunctionSource("internal fun NewVisitScreen", "internal fun VisitDetailScreen")
        assertTrue(screen.contains("Column(Modifier.fillMaxWidth().background(colors.surface)"))
        assertTrue(screen.contains("VisitSetupSectionHeading(\"Choose a customer\", \"choose-visit-customer\")"))
        assertTrue(screen.contains("Modifier.fillMaxWidth().testTag(\"visit-mode-tabs\")"))
        assertFalse(screen.contains("background(colors.canvas).testTag(\"visit-mode-tabs\")"))
    }

    private fun snapshot(
        date: String = "2026-09-19",
        appointmentTime: String = "",
        customerName: String = "",
        selectedPlanIds: Set<String> = emptySet(),
        tasks: List<NewVisitTaskDraft> = emptyList(),
    ) = NewVisitDraftSnapshot(
        siteId = null,
        selectedPlanIds = selectedPlanIds,
        date = date,
        appointmentTime = appointmentTime,
        customerName = customerName,
        phone = "",
        email = "",
        locationLabel = "",
        address = "",
        oneTimeCustomer = false,
        taskName = "",
        subjectType = WorkSubjectType.SITE,
        equipmentId = null,
        equipmentDescription = "",
        templateId = null,
        tasks = tasks,
    )
}
