package com.v16studio.serviceloop.domain

import java.time.LocalDate

/**
 * The three Work queues have deliberately small, explicit filter dimensions.
 * These are presentation/query presets only; they never alter persisted work
 * or lifecycle state.
 */
enum class DueServiceDateFilter(val label: String, val bucket: DueBucket?) {
    ALL("All", null),
    OVERDUE("Overdue", DueBucket.OVERDUE),
    TODAY("Today", DueBucket.TODAY),
    DUE_SOON("Due soon", DueBucket.DUE_SOON),
    UPCOMING("Upcoming", DueBucket.UPCOMING),
}

enum class DueServiceVisitFilter(val label: String) {
    ALL("All"),
    NO_VISIT("No visit"),
    HAS_VISIT("Has visit"),
}

enum class VisitDateFilter(val label: String) {
    ALL("All"),
    OVERDUE("Overdue"),
    TODAY("Today"),
    UPCOMING("Upcoming"),
    PAST_30_DAYS("Past 30 days"),
}

enum class VisitStatusFilter(val label: String, val lifecycle: VisitLifecycleState?) {
    ALL("All", null),
    BOOKED("Booked", VisitLifecycleState.BOOKED),
    WORKING("Working", VisitLifecycleState.WORKING),
    COMPLETED("Completed", VisitLifecycleState.COMPLETED),
    CANCELED("Canceled", VisitLifecycleState.CANCELED),
}

enum class FollowUpDateFilter(val label: String) {
    ALL("All"),
    OVERDUE("Overdue"),
    TODAY("Today"),
    UPCOMING("Upcoming"),
}

enum class FollowUpStatusFilter(val label: String) {
    ALL("All"),
    OPEN("Open"),
    CLOSED("Closed"),
}

fun filterDueServices(
    values: List<DueService>,
    dateFilter: DueServiceDateFilter,
    visitFilter: DueServiceVisitFilter,
    query: String,
): List<DueService> = values.filter { due ->
    (dateFilter.bucket == null || due.bucket == dateFilter.bucket) &&
        when (visitFilter) {
            DueServiceVisitFilter.ALL -> true
            DueServiceVisitFilter.NO_VISIT -> due.claimedVisitId == null
            DueServiceVisitFilter.HAS_VISIT -> due.claimedVisitId != null
        } &&
        due.matchesSearch(query)
}

fun filterVisits(
    values: List<VisitSummary>,
    dateFilter: VisitDateFilter,
    statusFilter: VisitStatusFilter,
    businessDate: LocalDate,
    query: String,
): List<VisitSummary> = values.filter { visit ->
    val scheduledDate = visit.actualServiceDate.toLocalDateOrNull()
    val lifecycle = runCatching { VisitLifecycleState.fromPersisted(visit.state) }.getOrNull()
    scheduledDate != null && lifecycle != null &&
        dateFilter.matches(scheduledDate, lifecycle, businessDate) &&
        (statusFilter.lifecycle == null || lifecycle == statusFilter.lifecycle) &&
        visit.matchesSearch(query)
}

fun filterFollowUps(
    values: List<FollowUpDetail>,
    dateFilter: FollowUpDateFilter,
    statusFilter: FollowUpStatusFilter,
    businessDate: LocalDate,
    query: String,
): List<FollowUpDetail> = values.filter { followUp ->
    val dueDate = followUp.dueDate.toLocalDateOrNull()
    dueDate != null &&
        dateFilter.matches(dueDate, businessDate) &&
        when (statusFilter) {
            FollowUpStatusFilter.ALL -> true
            FollowUpStatusFilter.OPEN -> followUp.state == "OPEN"
            FollowUpStatusFilter.CLOSED -> followUp.state != "OPEN"
        } &&
        followUp.matchesSearch(query)
}

private fun VisitDateFilter.matches(
    scheduledDate: LocalDate,
    lifecycle: VisitLifecycleState,
    businessDate: LocalDate,
): Boolean = when (this) {
    VisitDateFilter.ALL -> true
    VisitDateFilter.OVERDUE -> scheduledDate.isBefore(businessDate) && lifecycle in setOf(
        VisitLifecycleState.BOOKED,
        VisitLifecycleState.WORKING,
    )
    VisitDateFilter.TODAY -> scheduledDate == businessDate
    VisitDateFilter.UPCOMING -> scheduledDate.isAfter(businessDate)
    VisitDateFilter.PAST_30_DAYS -> !scheduledDate.isBefore(businessDate.minusDays(30)) &&
        scheduledDate.isBefore(businessDate)
}

private fun FollowUpDateFilter.matches(dueDate: LocalDate, businessDate: LocalDate): Boolean = when (this) {
    FollowUpDateFilter.ALL -> true
    FollowUpDateFilter.OVERDUE -> dueDate.isBefore(businessDate)
    FollowUpDateFilter.TODAY -> dueDate == businessDate
    FollowUpDateFilter.UPCOMING -> dueDate.isAfter(businessDate)
}

private fun DueService.matchesSearch(query: String): Boolean = query.isBlank() || listOf(
    planReference,
    planName,
    equipmentName,
    equipmentReference,
    customerName,
    siteName,
).any { text -> text.contains(query, ignoreCase = true) }

private fun VisitSummary.matchesSearch(query: String): Boolean = query.isBlank() || listOf(
    reference,
    siteName,
    actualServiceDate,
    state,
).any { text -> text.contains(query, ignoreCase = true) }

private fun FollowUpDetail.matchesSearch(query: String): Boolean = query.isBlank() || listOfNotNull(
    reference,
    title,
    customerName,
    siteName,
    equipmentName,
    dueDate,
    state,
).any { text -> text.contains(query, ignoreCase = true) }

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
