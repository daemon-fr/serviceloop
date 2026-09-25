package com.v16studio.v16service

import com.v16studio.v16service.domain.appointmentEpochMillis
import com.v16studio.v16service.ui.CustomerCreationDraft
import com.v16studio.v16service.ui.VisitSetupDraft
import com.v16studio.v16service.ui.VisitSetupTaskDraft
import com.v16studio.v16service.ui.visitSetupIsDirty
import com.v16studio.v16service.ui.parseAppointmentTimeInput
import com.v16studio.v16service.domain.WorkSubjectType
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
        val baseline = VisitSetupDraft(serviceDate = "2026-09-19")
        assertFalse(visitSetupIsDirty(baseline, baseline))
        assertTrue(visitSetupIsDirty(baseline, baseline.copy(serviceDate = "2026-09-20")))
        assertTrue(visitSetupIsDirty(baseline, baseline.copy(appointmentTime = "09:30")))
        val restoredTime = baseline.copy(appointmentTime = "09:30").copy(appointmentTime = "")
        assertFalse(visitSetupIsDirty(baseline, restoredTime))
        assertTrue(visitSetupIsDirty(baseline, baseline.copy(newCustomer = CustomerCreationDraft(name = "New customer"))))
        assertTrue(visitSetupIsDirty(baseline, baseline.copy(tasks = listOf(VisitSetupTaskDraft(taskName = "Inspect", subjectType = WorkSubjectType.SITE, equipmentId = null, equipmentDescription = "", reusableTemplateId = null)))))
        assertTrue(visitSetupIsDirty(baseline, baseline.copy(selectedPlanIds = setOf("plan-1"))))
        assertFalse(visitSetupIsDirty(baseline, baseline.copy(siteId = "site-a")))
        val selectedSite = baseline.copy(siteId = "site-a")
        assertFalse(visitSetupIsDirty(selectedSite, selectedSite.copy(siteId = "site-b")))
        assertFalse(visitSetupIsDirty(baseline, baseline.copy(selectedPlanIds = emptySet())))
    }

    @Test
    fun createVisitDirtySnapshotDoesNotContainPresentationOnlyModeOrSiteSearch() {
        val source = productionKotlinFunctionSource("internal data class VisitSetupDraft", "private const val SETUP_SEPARATOR")
        assertFalse(source.contains("siteQuery"))
        assertTrue(source.contains("mode:"))
        assertTrue(productionKotlinSource("com/v16studio/v16service/ui").contains("visitSetupIsDirty(baseline, draft)"))
    }

    @Test
    fun ownerReviewSurfaceContractsAreCentralized() {
        val production = productionKotlinSource("com/v16studio/v16service/ui")
        val fieldAction = productionKotlinFunctionSource("fun V16ServiceFieldAction", "@Composable fun V16ServiceTextAction")
        assertTrue(fieldAction.contains("V16ServiceButtonContract.secondaryContainer(c, enabled)"))
        assertTrue(fieldAction.contains("V16ServiceButtonContract.secondaryInk(c, enabled)"))
        assertFalse(fieldAction.contains("c.selection"))
        assertFalse(fieldAction.contains(".border("))
        assertTrue(production.contains("singleRow: Boolean = false"))
        assertTrue(production.contains("singleRow = true"))
        assertTrue(production.contains("V16ServiceIcons.PlusBold"))
        assertFalse(production.contains("Follow-up state never changes a service plan or historical report."))
    }

    @Test
    fun ownerReviewRemovedPermanentLocalStorageCopy() {
        val production = productionKotlinSource("com/v16studio/v16service/ui")
        assertFalse(production.contains("Saved on this device"))
        assertFalse(production.contains("Stored on this device"))
        assertFalse(production.contains("This also controls the Home and default Due services horizon"))
        assertFalse(production.contains("Calls, messages, and email are recorded here only when you save an outcome"))
    }

    @Test
    fun createVisitCustomerTabsShareOneUpperSurfaceBand() {
        val screen = productionKotlinSource("com/v16studio/v16service/ui")
        assertTrue(screen.contains("internal fun VisitSetupForm"))
        assertTrue(screen.contains("VisitSetupSectionHeading(\"Choose a customer\", \"choose-visit-customer\""))
        assertTrue(screen.contains("CustomerCreationForm"))
    }

    @Test
    fun b043CorrectionSurfacesKeepFocusedInteractionContracts() {
        val production = productionKotlinSource("com/v16studio/v16service/ui")
        val contact = productionKotlinSourceContaining("CONTACT_CHANNELS = listOf(", "internal fun ContactNoteEditorScreen")
        val detail = productionKotlinSourceContaining("internal fun ContactNoteScreen", "Mark entered in error")
        val outbox = productionKotlinSourceContaining("internal fun DispatchOutboxScreen", "dispatch-new-visit-bottom")
        val visit = productionKotlinSource("com/v16studio/v16service/ui")
        val customer = productionKotlinFunctionSource("internal fun CustomerDetailScreen", "internal fun CustomerEditorScreen")

        assertTrue(contact.contains("CONTACT_CHANNELS = listOf("))
        assertTrue(contact.contains("testTag(\"contact-channel-"))
        assertTrue(contact.contains("contentDescription = label"))
        assertTrue(contact.contains("compact = true"))
        assertTrue(contact.contains("Outcome · Required"))
        assertTrue(contact.contains("Private note · Optional"))
        assertFalse(contact.contains("Calls, messages and email are recorded here only when you save an outcome."))
        assertFalse(contact.contains("Record actual contact outcome"))
        assertFalse(contact.contains("Opening an external app does not create this note."))

        assertTrue(customer.contains("V16ServiceDenseNavigableRow"))
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
        assertTrue(visit.contains("actionItems"))
        assertTrue(visit.contains("V16ServiceFieldAction"))
        assertTrue(visit.contains("visit-change-customer-site"))
        assertTrue(production.contains("if ((workDashboard?.totalItemCount ?: 0) == 0) Spacer(Modifier.height(V16ServiceUiTokens.Space.md))"))
    }

}
