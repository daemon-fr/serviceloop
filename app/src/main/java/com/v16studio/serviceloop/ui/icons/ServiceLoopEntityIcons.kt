package com.v16studio.serviceloop.ui.icons

/** User-visible ServiceLoop entity kinds. This registry centralizes their semantic icon choices. */
enum class ServiceLoopEntityType {
    CUSTOMER, SITE, EQUIPMENT, SERVICE_PLAN, VISIT, SERVICE, FOLLOW_UP, CONTACT_NOTE,
    INSPECTION_TEMPLATE, REPORT, TECHNICIAN, TEAM, PHOTO, PART, WORK_ASSIGNMENT,
}

object ServiceLoopEntityIcons {
    private val adoptedHomeMappings = mapOf(
        ServiceLoopEntityType.VISIT to ServiceLoopIcons.HomeVisit,
        ServiceLoopEntityType.SERVICE to ServiceLoopIcons.HomeService,
        ServiceLoopEntityType.FOLLOW_UP to ServiceLoopIcons.HomeFollowUp,
    )

    fun forType(type: ServiceLoopEntityType): Int? = adoptedHomeMappings[type]
}
