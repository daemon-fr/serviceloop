package com.v16studio.v16service.ui.icons

/** User-visible V16 Service entity kinds. This registry centralizes their semantic icon choices. */
enum class V16ServiceEntityType {
    CUSTOMER, SITE, EQUIPMENT, SERVICE_PLAN, VISIT, SERVICE, FOLLOW_UP, CONTACT_NOTE,
    INSPECTION_TEMPLATE, REPORT, TECHNICIAN, TEAM, PHOTO, PART, WORK_ASSIGNMENT,
}

object V16ServiceEntityIcons {
    private val adoptedHomeMappings = mapOf(
        V16ServiceEntityType.VISIT to V16ServiceIcons.HomeVisit,
        V16ServiceEntityType.SERVICE to V16ServiceIcons.HomeService,
        V16ServiceEntityType.FOLLOW_UP to V16ServiceIcons.HomeFollowUp,
    )

    fun forType(type: V16ServiceEntityType): Int? = adoptedHomeMappings[type]
}
