package com.v16studio.serviceloop

import com.v16studio.serviceloop.data.ExportFamily
import com.v16studio.serviceloop.data.ExportPreset
import com.v16studio.serviceloop.domain.ServiceLoopScopeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class B049ScopeAndExportTest {
    @Test fun parentChangesClearDependentScopeAndDatesAreInclusive() {
        val original = ServiceLoopScopeFilter("customer-1", "site-1", "equipment-1", LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"))
        assertTrue(original.matches("customer-1", "site-1", "equipment-1", LocalDate.parse("2026-09-01")))
        assertTrue(original.matches("customer-1", "site-1", "equipment-1", LocalDate.parse("2026-09-30")))
        assertFalse(original.matches("customer-1", "site-1", "equipment-1", LocalDate.parse("2026-10-01")))
        assertFalse(original.matches("customer-2", "site-1", "equipment-1", LocalDate.parse("2026-09-15")))
        assertEquals(null, original.withSite("site-2").equipmentId)
        assertEquals(null, original.withCustomer("customer-2").siteId)
        assertEquals(null, original.withCustomer("customer-2").equipmentId)
        assertThrows(IllegalArgumentException::class.java) { ServiceLoopScopeFilter(siteId = "site") }
        assertThrows(IllegalArgumentException::class.java) { ServiceLoopScopeFilter(fromDate = LocalDate.parse("2026-09-30"), toDate = LocalDate.parse("2026-09-01")) }
    }

    @Test fun presetsSeedDistinctLogicalFamilies() {
        assertEquals(setOf(ExportFamily.CUSTOMERS, ExportFamily.CONTACTS, ExportFamily.SITES, ExportFamily.EQUIPMENT, ExportFamily.PLANS), ExportPreset.CUSTOMER_DATA.families)
        assertTrue(ExportFamily.SERVICE_RECORDS in ExportPreset.WORK_PERFORMED.families)
        assertFalse(ExportFamily.SERVICE_RECORDS in ExportPreset.CUSTOMER_DATA.families)
        assertEquals(setOf(ExportFamily.PHOTO_METADATA, ExportFamily.IMAGE_FILES), ExportPreset.IMAGE_ARCHIVE.families)
        assertTrue(ExportPreset.CUSTOM.families.isEmpty())
    }
}
