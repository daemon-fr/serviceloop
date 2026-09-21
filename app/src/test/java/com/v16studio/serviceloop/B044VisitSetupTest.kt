package com.v16studio.serviceloop

import com.v16studio.serviceloop.domain.CustomerType
import com.v16studio.serviceloop.domain.WorkSubjectType
import com.v16studio.serviceloop.ui.CustomerCreationDraft
import com.v16studio.serviceloop.ui.VisitSetupDraft
import com.v16studio.serviceloop.ui.VisitSetupMode
import com.v16studio.serviceloop.ui.VisitSetupTaskDraft
import com.v16studio.serviceloop.ui.visitSetupIsDirty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class B044VisitSetupTest {
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
}
