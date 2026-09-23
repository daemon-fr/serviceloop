package com.v16studio.serviceloop.domain

import java.time.LocalDate

/** Shared selection scope for readable exports and aggregate reports. Dates are inclusive. */
data class ServiceLoopScopeFilter(
    val customerId: String? = null,
    val siteId: String? = null,
    val equipmentId: String? = null,
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
) {
    init {
        require(siteId == null || customerId != null) { "Choose a customer before a site" }
        require(equipmentId == null || siteId != null) { "Choose a site before equipment" }
        require(fromDate == null || toDate == null || !fromDate.isAfter(toDate)) { "From date must be on or before To date" }
    }

    fun withCustomer(id: String?): ServiceLoopScopeFilter = if (id == customerId) this else copy(customerId = id, siteId = null, equipmentId = null)

    fun withSite(id: String?): ServiceLoopScopeFilter {
        require(id == null || customerId != null) { "Choose a customer before a site" }
        return if (id == siteId) this else copy(siteId = id, equipmentId = null)
    }

    fun withEquipment(id: String?): ServiceLoopScopeFilter {
        require(id == null || siteId != null) { "Choose a site before equipment" }
        return copy(equipmentId = id)
    }

    fun matches(customer: String, site: String?, equipment: String?, serviceDate: LocalDate?): Boolean =
        (customerId == null || customerId == customer) &&
            (siteId == null || siteId == site) &&
            (equipmentId == null || equipmentId == equipment) &&
            (fromDate == null || serviceDate != null && !serviceDate.isBefore(fromDate)) &&
            (toDate == null || serviceDate != null && !serviceDate.isAfter(toDate))
}
