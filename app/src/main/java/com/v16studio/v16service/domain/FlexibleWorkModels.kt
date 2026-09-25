package com.v16studio.v16service.domain

/** The durable customer relationship classification. Lifecycle state is separate. */
enum class CustomerType(val code: String) {
    STANDARD("STANDARD"),
    ONE_TIME("ONE_TIME");

    companion object {
        fun fromCode(value: String): CustomerType = entries.firstOrNull { it.code == value.trim().uppercase() }
            ?: error("Unknown CustomerType: $value")
    }
}

/** The durable subject of one WorkItem/FinalWorkItem line. */
enum class WorkSubjectType(val code: String) {
    SITE("SITE"),
    EQUIPMENT("EQUIPMENT");

    companion object {
        fun fromCode(value: String): WorkSubjectType = entries.firstOrNull { it.code == value.trim().uppercase() }
            ?: error("Unknown WorkSubjectType: $value")
    }
}

data class AdHocWorkInput(
    val taskName: String,
    val subjectType: WorkSubjectType,
    val equipmentId: String? = null,
    val equipmentDescription: String = "",
    val reusableTemplateId: String? = null,
)

data class NewCustomerVisitInput(
    val customerName: String,
    val phone: String = "",
    val email: String = "",
    val locationLabel: String = "",
    val address: String = "",
    val customerType: CustomerType = CustomerType.STANDARD,
)

/** Compatibility input for the adopted Dispatch one-time branch. */
@Deprecated("Use NewCustomerVisitInput for technician-created Visits")
data class OneTimeVisitInput(
    val customerName: String,
    val phone: String = "",
    val email: String = "",
    val locationLabel: String = "",
    val address: String = "",
)

data class EquipmentLinkContext(
    val workItemId: String,
    val visitId: String,
    val siteId: String,
    val siteName: String,
    val equipment: List<EquipmentSummary>,
)
