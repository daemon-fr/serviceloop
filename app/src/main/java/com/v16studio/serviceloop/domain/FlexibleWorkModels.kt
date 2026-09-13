package com.v16studio.serviceloop.domain

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
