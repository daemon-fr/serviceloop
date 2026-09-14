package com.v16studio.serviceloop.domain

/** Progress of the Service-entry portion of a Working Visit. This is derived UI state, not business persistence. */
enum class ServiceEntryStatus {
    NOT_STARTED,
    IN_PROGRESS,
    NEEDS_ATTENTION,
    READY,
}

/** Identifies who owns the documentation decision for a dispatched item on this device. */
enum class ServiceDocumentationMode {
    LOCAL,
    CHOICE_REQUIRED,
    LEADER_OBSERVE,
    DEFERRED,
}

fun serviceDocumentationMode(localRole: String?, documentationDisposition: String?): ServiceDocumentationMode = when {
    localRole == "ASSIGNMENT_REMOVED" || documentationDisposition == "ASSIGNMENT_REMOVED" || documentationDisposition == "DEFERRED" -> ServiceDocumentationMode.DEFERRED
    documentationDisposition == "DOCUMENT_LOCAL" -> ServiceDocumentationMode.LOCAL
    documentationDisposition == "PENDING" -> ServiceDocumentationMode.CHOICE_REQUIRED
    documentationDisposition == "LEADER_OBSERVE" -> ServiceDocumentationMode.LEADER_OBSERVE
    localRole == "LEADER_VISIBLE" && documentationDisposition == null -> ServiceDocumentationMode.LEADER_OBSERVE
    localRole == null && documentationDisposition == null -> ServiceDocumentationMode.LOCAL
    else -> ServiceDocumentationMode.DEFERRED
}

fun serviceEntryStatus(
    hasActivity: Boolean,
    checklistComplete: Boolean,
    hasUnresolvedRawBuffer: Boolean = false,
    hasMissingIssueDescription: Boolean = false,
    hasInvalidExplicitAnswer: Boolean = false,
    completionReady: Boolean = false,
): ServiceEntryStatus = when {
    !hasActivity -> ServiceEntryStatus.NOT_STARTED
    hasUnresolvedRawBuffer || hasMissingIssueDescription || hasInvalidExplicitAnswer -> ServiceEntryStatus.NEEDS_ATTENTION
    checklistComplete && completionReady -> ServiceEntryStatus.READY
    else -> ServiceEntryStatus.IN_PROGRESS
}

data class ServiceProgressItem(
    val workItemId: String,
    val position: Int,
    val subjectType: WorkSubjectType,
    val equipmentId: String?,
    val equipmentName: String?,
    val equipmentReference: String?,
    val equipmentDescription: String?,
    val serviceName: String,
    val status: ServiceEntryStatus,
    val documentationMode: ServiceDocumentationMode = ServiceDocumentationMode.LOCAL,
    val dispatchLocalRole: String? = null,
    val dispatchDocumentationDisposition: String? = null,
)

data class ServiceProgressGroup(
    val key: String,
    val label: String,
    val items: List<ServiceProgressItem>,
)

fun serviceProgressGroups(items: List<ServiceProgressItem>): List<ServiceProgressGroup> {
    val grouped = linkedMapOf<String, MutableList<ServiceProgressItem>>()
    items.forEach { item -> grouped.getOrPut(serviceProgressGroupKey(item)) { mutableListOf() } += item }
    return grouped.map { (key, groupItems) -> ServiceProgressGroup(key, serviceProgressGroupLabel(groupItems.first()), groupItems.toList()) }
}

data class VisitServiceProgress(
    val visitId: String,
    val visitReference: String,
    val customerName: String,
    val siteName: String,
    val serviceDate: String,
    val items: List<ServiceProgressItem>,
    val groups: List<ServiceProgressGroup>,
) {
    /** Local work and unresolved documentation choices are the only navigation candidates. */
    val actionableItems: List<ServiceProgressItem>
        get() = items.filter { it.documentationMode in setOf(ServiceDocumentationMode.LOCAL, ServiceDocumentationMode.CHOICE_REQUIRED) }

    /** Preferred resume order: attention, active work, untouched work, choices, ready work. */
    fun preferredResumeItem(): ServiceProgressItem? {
        val local = items.filter { it.documentationMode == ServiceDocumentationMode.LOCAL }
        return local.firstOrNull { it.status == ServiceEntryStatus.NEEDS_ATTENTION }
            ?: local.firstOrNull { it.status == ServiceEntryStatus.IN_PROGRESS }
            ?: local.firstOrNull { it.status == ServiceEntryStatus.NOT_STARTED }
            ?: items.firstOrNull { it.documentationMode == ServiceDocumentationMode.CHOICE_REQUIRED }
            ?: local.firstOrNull { it.status == ServiceEntryStatus.READY }
            ?: items.firstOrNull()
    }

    /** Selects the next unfinished actionable Service using Visit order and same-group preference. */
    fun nextService(currentWorkItemId: String): ServiceProgressItem? {
        val current = items.firstOrNull { it.workItemId == currentWorkItemId } ?: return null
        val unfinished = actionableItems.filter { it.workItemId != currentWorkItemId && it.status != ServiceEntryStatus.READY }
        val laterSameGroup = unfinished.filter { it.position > current.position && groupKey(it) == groupKey(current) }
        return laterSameGroup.minByOrNull(ServiceProgressItem::position)
            ?: unfinished.filter { it.position > current.position }.minByOrNull(ServiceProgressItem::position)
            ?: unfinished.filter { it.position < current.position }.minByOrNull(ServiceProgressItem::position)
    }

    fun groupFor(workItemId: String): ServiceProgressGroup? = groups.firstOrNull { group -> group.items.any { it.workItemId == workItemId } }

    private fun groupKey(item: ServiceProgressItem): String = serviceProgressGroupKey(item)
}

private fun serviceProgressGroupKey(item: ServiceProgressItem): String = when {
    item.subjectType == WorkSubjectType.SITE -> "SITE"
    item.equipmentId != null -> "EQUIPMENT:${item.equipmentId}"
    else -> "WORK:${item.workItemId}"
}

private fun serviceProgressGroupLabel(item: ServiceProgressItem): String = when {
    item.subjectType == WorkSubjectType.SITE -> "Site work"
    item.equipmentId == null -> item.equipmentDescription?.trim()?.takeIf(String::isNotBlank) ?: "Equipment not specified"
    else -> listOfNotNull(
        item.equipmentReference?.trim()?.takeIf(String::isNotBlank),
        item.equipmentName?.trim()?.takeIf(String::isNotBlank),
    ).joinToString(" · ").ifBlank { "Equipment" }
}
