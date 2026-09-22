package com.v16studio.serviceloop

import com.v16studio.serviceloop.data.validateNewBookedVisitDate
import com.v16studio.serviceloop.domain.CustomerType
import com.v16studio.serviceloop.domain.WorkSubjectType
import com.v16studio.serviceloop.ui.CustomerCreationDraft
import com.v16studio.serviceloop.ui.VisitSetupDraft
import com.v16studio.serviceloop.ui.VisitSetupMode
import com.v16studio.serviceloop.ui.VisitSetupTaskEditorState
import com.v16studio.serviceloop.ui.VisitSetupTaskDraft
import com.v16studio.serviceloop.ui.dispatchItemIdForWorkKey
import com.v16studio.serviceloop.ui.restoreVisitSetupTaskEditorState
import com.v16studio.serviceloop.ui.saveVisitSetupTaskEditorState
import com.v16studio.serviceloop.ui.visitSetupIsDirty
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class B044VisitSetupTest {
    @Test
    fun taskEditorStateSaverPreservesEquipmentTemplateAndEditingIdentity() {
        val original = VisitSetupTaskEditorState(
            taskName = "Inspect HVAC unit",
            subjectType = WorkSubjectType.EQUIPMENT,
            equipmentId = "equipment-1",
            equipmentDescription = "Roof unit",
            reusableTemplateId = "template-1",
            editingTaskId = "task-1",
        )
        val saved = saveVisitSetupTaskEditorState(original)

        assertEquals(original, restoreVisitSetupTaskEditorState(saved))
    }

    @Test
    fun customerCreationDraftProducesCanonicalCustomerAndDefaultSite() {
        val input = CustomerCreationDraft(
            name = "Northside Foods",
            contactName = "Mara",
            phone = "555-0100",
            email = "mara@example.com",
            privateNote = "Gate code is private",
            customerType = CustomerType.ONE_TIME,
            siteName = "Main plant",
            siteAddress = "1 Test Road",
        ).toInput()

        assertEquals("Northside Foods", input.customer.name)
        assertEquals(CustomerType.ONE_TIME, input.customer.customerType)
        assertEquals("Main plant", input.site.name)
        assertEquals("1 Test Road", input.site.address)
        assertTrue(input.site.isDefault)
    }

    @Test
    fun visitSetupDirtyStateIgnoresPresentationModeAndSiteSelectionOnly() {
        val baseline = VisitSetupDraft(serviceDate = "2026-09-23")
        assertFalse(visitSetupIsDirty(baseline, baseline.copy(mode = VisitSetupMode.NEW)))
        assertFalse(visitSetupIsDirty(baseline, baseline.copy(siteId = "site-1")))
        assertTrue(visitSetupIsDirty(baseline, baseline.copy(selectedPlanIds = setOf("plan-1"))))
        assertTrue(visitSetupIsDirty(baseline, baseline.copy(tasks = listOf(VisitSetupTaskDraft(taskName = "Inspect", subjectType = WorkSubjectType.SITE, equipmentId = null, equipmentDescription = "", reusableTemplateId = null)))))
    }

    @Test
    fun dispatchPlanItemIdsAreStableWithinAnEditorSessionAndUniquePerWorkKey() {
        val ids = mutableMapOf<String, String>()

        val first = dispatchItemIdForWorkKey(ids, "PLAN:annual-pump")
        val sameWork = dispatchItemIdForWorkKey(ids, "PLAN:annual-pump")
        val otherWork = dispatchItemIdForWorkKey(ids, "PLAN:boiler-room")

        assertEquals(first, sameWork)
        assertNotEquals(first, otherWork)
        assertTrue(first.matches(Regex("[0-9a-f-]{36}")))
        assertEquals(setOf("PLAN:annual-pump", "PLAN:boiler-room"), ids.keys)
    }

    @Test
    fun newBookedVisitsRejectDatesBeforeTheBusinessDateButAllowTodayAndFuture() {
        val today = LocalDate.of(2026, 9, 22)

        assertThrows(IllegalArgumentException::class.java) {
            validateNewBookedVisitDate("BOOKED", "2026-09-21", today)
        }
        validateNewBookedVisitDate("BOOKED", "2026-09-22", today)
        validateNewBookedVisitDate("BOOKED", "2026-09-23", today)
        validateNewBookedVisitDate("HISTORICAL", "2026-09-21", today)
    }
}
