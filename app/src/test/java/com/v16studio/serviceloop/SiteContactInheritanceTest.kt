package com.v16studio.serviceloop

import com.v16studio.serviceloop.domain.SiteDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteContactInheritanceTest {
    private fun detail(contact: String = "", phone: String = "", email: String = "") = SiteDetail(
        id = "site", customerId = "customer", customerName = "Customer", reference = "ST-1",
        name = "Site", address = "Address", contactName = contact, phone = phone, email = email,
        privateAccessNote = "", isDefault = true, equipment = emptyList(),
        customerContactName = "Customer contact", customerPhone = "+40111", customerEmail = "customer@example.test",
    )

    @Test fun blankSiteContactUsesCurrentCustomerContact() {
        val value = detail()
        assertTrue(value.usesCustomerContact)
        assertEquals("Customer contact", value.effectiveContactName)
        assertEquals("+40111", value.effectivePhone)
        assertEquals("customer@example.test", value.effectiveEmail)
    }

    @Test fun siteOverridesRemainTheEffectiveContact() {
        val value = detail("Site contact", "+40222", "site@example.test")
        assertFalse(value.usesCustomerContact)
        assertEquals("Site contact", value.effectiveContactName)
        assertEquals("+40222", value.effectivePhone)
        assertEquals("site@example.test", value.effectiveEmail)
    }
}
