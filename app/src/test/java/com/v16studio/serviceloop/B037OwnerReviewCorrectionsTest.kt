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
        assertTrue(newVisitDraftIsDirty(baseline, baseline.copy(selectedPlanIds = setOf("plan-1"))))
        assertFalse(newVisitDraftIsDirty(baseline, baseline.copy(siteId = "site-a")))
        val selectedSite = baseline.copy(siteId = "site-a")
        assertFalse(newVisitDraftIsDirty(selectedSite, selectedSite.copy(siteId = "site-b")))
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
        assertTrue(screen.contains("VisitSetupSectionHeading(\"Choose a customer\", \"choose-visit-customer\""))
        assertTrue(screen.contains("Modifier.fillMaxWidth().testTag(\"visit-mode-tabs\")"))
        assertFalse(screen.contains("background(colors.canvas).testTag(\"visit-mode-tabs\")"))
    }

    @Test
    fun b043CorrectionSurfacesKeepFocusedInteractionContracts() {
        val production = productionKotlinSource("com/v16studio/serviceloop/ui")
        val contact = productionKotlinSourceContaining("CONTACT_CHANNELS = listOf(", "internal fun ContactNoteEditorScreen")
        val detail = productionKotlinSourceContaining("internal fun ContactNoteScreen", "Mark entered in error")
        val outbox = productionKotlinSourceContaining("internal fun DispatchOutboxScreen", "dispatch-new-visit-bottom")
        val visit = productionKotlinFunctionSource("internal fun NewVisitScreen", "internal fun VisitDetailScreen")
        val customer = productionKotlinFunctionSource("internal fun CustomerDetailScreen", "internal fun CustomerEditorScreen")

        assertTrue(contact.contains("CONTACT_CHANNELS = listOf("))
        assertTrue(contact.contains("testTag(\"contact-channel-"))
        assertTrue(contact.contains("contentDescription = label"))
        assertTrue(contact.contains("compact = true"))
        assertTrue(contact.contains("Outcome · Required"))
        assertTrue(contact.contains("Private note · Optional"))
        assertTrue(contact.contains("Calls, messages and email are recorded here only when you save an outcome."))
        assertFalse(contact.contains("Record actual contact outcome"))
        assertFalse(contact.contains("Opening an external app does not create this note."))

        assertTrue(customer.contains("ServiceLoopDenseNavigableRow"))
        assertTrue(customer.contains("nav.navigate(\"contact/${'$'}{note.id}\")"))
        assertFalse(customer.contains("Entered-in-error reason"))
        assertFalse(customer.contains("Mark entered in error"))
        assertFalse(customer.contains("note.privateNote"))

        assertTrue(detail.contains("Mark entered in error"))
        assertTrue(detail.contains("Reason · Required"))
        assertTrue(detail.contains("Confirm entered in error"))
        assertTrue(detail.contains("reason.isNotBlank() && !state.operationInProgress"))
        assertTrue(outbox.contains("dispatch-new-visit-bottom"))
        assertTrue(outbox.contains("dispatch-new-visit-floating"))
        assertTrue(outbox.contains("nav.navigate(\"dispatch/visit/new\")"))
        assertTrue(outbox.contains("if(selectedRows.isEmpty())"))
        assertTrue(visit.contains("showOperationMessage = false"))
        assertTrue(visit.contains("ServiceLoopFieldAction"))
        assertTrue(visit.contains("visit-change-customer-site"))
        assertTrue(production.contains("if ((workDashboard?.totalItemCount ?: 0) == 0) Spacer(Modifier.height(ServiceLoopUiTokens.Space.md))"))
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
