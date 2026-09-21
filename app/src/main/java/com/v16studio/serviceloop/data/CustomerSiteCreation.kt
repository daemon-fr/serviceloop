package com.v16studio.serviceloop.data

import androidx.room.withTransaction
import com.v16studio.serviceloop.domain.CustomerWithFirstSiteInput
import com.v16studio.serviceloop.domain.CustomerInput
import com.v16studio.serviceloop.domain.SiteInput
import java.util.UUID

internal data class CreatedCustomerSite(
    val customer: CustomerEntity,
    val site: SiteEntity,
)

internal fun validateCustomerInput(input: CustomerInput) {
    require(input.name.trim().isNotEmpty()) { "Customer name is required" }
    require(input.name.trim().length <= 200) { "Customer name must be 200 characters or fewer" }
    require(input.contactName.trim().length <= 200) { "Main contact must be 200 characters or fewer" }
    require(input.phone.trim().length <= 100) { "Phone must be 100 characters or fewer" }
    require(input.email.trim().length <= 320) { "Email must be 320 characters or fewer" }
    require(input.privateNote.trim().length <= 5000) { "Private customer note must be 5000 characters or fewer" }
}

internal fun validateSiteInput(input: SiteInput) {
    require(input.name.trim().isNotEmpty()) { "Site name is required" }
    require(input.name.trim().length <= 200) { "Site name must be 200 characters or fewer" }
    require(input.address.trim().length <= 500) { "Site address must be 500 characters or fewer" }
    require(input.contactName.trim().length <= 200) { "Site contact must be 200 characters or fewer" }
    require(input.phone.trim().length <= 100) { "Site phone must be 100 characters or fewer" }
    require(input.email.trim().length <= 320) { "Site email must be 320 characters or fewer" }
    require(input.privateAccessNote.trim().length <= 5000) { "Private access note must be 5000 characters or fewer" }
}

/** Ordinary CU/ST reference generation shared by all transactional customer/site creation. */
internal fun ordinaryReference(prefix: String, sequence: Int): String = "$prefix-${sequence.toString().padStart(3, '0')}"

internal fun normalizedOptional(value: String?): String? = value?.trim()?.ifBlank { null }

/**
 * Inserts a Customer and its first default Site into the caller's existing Room transaction.
 * This function deliberately does not call withTransaction or the write gate.
 */
internal suspend fun createCustomerWithFirstSiteInTransaction(
    dao: ServiceLoopDao,
    input: CustomerWithFirstSiteInput,
): CreatedCustomerSite {
    validateCustomerInput(input.customer)
    validateSiteInput(input.site)
    val customerId = UUID.randomUUID().toString()
    val siteId = UUID.randomUUID().toString()
    val customer = CustomerEntity(
        id = customerId,
        reference = ordinaryReference("CU", dao.customerCount() + 1),
        name = input.customer.name.trim(),
        contactName = normalizedOptional(input.customer.contactName),
        phone = normalizedOptional(input.customer.phone),
        email = normalizedOptional(input.customer.email),
        privateNote = normalizedOptional(input.customer.privateNote),
        customerType = input.customer.customerType.code,
    )
    val site = SiteEntity(
        id = siteId,
        customerId = customerId,
        reference = ordinaryReference("ST", dao.siteCount() + 1),
        name = input.site.name.trim(),
        address = normalizedOptional(input.site.address),
        privateAccessNotes = normalizedOptional(input.site.privateAccessNote),
        contactName = normalizedOptional(input.site.contactName),
        phone = normalizedOptional(input.site.phone),
        email = normalizedOptional(input.site.email),
        isDefault = true,
    )
    dao.insertCustomers(listOf(customer))
    dao.insertSites(listOf(site))
    return CreatedCustomerSite(customer, site)
}
