package com.v16studio.serviceloop.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class OperationalWorkKind(val title: String, val plural: String) {
    VISIT("Visit", "visits"),
    SERVICE("Service", "services"),
    FOLLOW_UP("Follow-up", "follow-ups"),
}

enum class OperationalWorkState(val label: String) {
    IN_PROGRESS("In progress"),
    OVERDUE("Overdue"),
    DUE_SOON("Due soon"),
    BOOKED("Booked"),
}

sealed interface WorkScope {
    data object Global : WorkScope
    data class Customer(val customerId: String) : WorkScope
}

fun WorkScope.includesCustomer(customerId: String): Boolean = when (this) {
    WorkScope.Global -> true
    is WorkScope.Customer -> this.customerId == customerId
}

data class OperationalWorkItem(
    val kind: OperationalWorkKind,
    val state: OperationalWorkState,
    val recordId: String,
    val customerId: String,
    val siteId: String?,
    val equipmentId: String?,
    val displayReference: String,
    val displayTitle: String,
    val displayContext: String,
    val dueDate: LocalDate?,
    val scheduledAtEpochMillis: Long?,
    val modifiedAtEpochMillis: Long,
    /** Current work-item identity is needed to resume a Working Visit without another lookup. */
    val workItemId: String? = null,
)

data class OperationalDashboardSection(
    val kind: OperationalWorkKind,
    val state: OperationalWorkState,
    val title: String,
    val items: List<OperationalWorkItem>,
) {
    val itemCount: Int get() = items.size
}

data class OperationalDashboardProjection(
    val scope: WorkScope,
    val sections: List<OperationalDashboardSection>,
) {
    val totalItemCount: Int get() = sections.sumOf { it.itemCount }

    fun stateFor(kind: OperationalWorkKind, recordId: String): OperationalWorkState? = sections
        .asSequence()
        .flatMap { it.items.asSequence() }
        .firstOrNull { it.kind == kind && it.recordId == recordId }
        ?.state

    val mostUrgentState: OperationalWorkState?
        get() = sections.minByOrNull { OperationalWorkClassifier.urgencyRank(it.state) }?.state
}

/** One derived path for Home, scoped dashboards, and live Work row states. */
object OperationalWorkClassifier {
    fun classifyVisit(
        visit: VisitSummary,
        today: LocalDate,
        now: Instant,
        businessZone: ZoneId,
        dueSoonHorizonDays: Int,
    ): OperationalWorkState? {
        val lifecycle = runCatching { VisitLifecycleState.fromPersisted(visit.state) }.getOrNull() ?: return null
        if (lifecycle == VisitLifecycleState.WORKING) return OperationalWorkState.IN_PROGRESS
        if (lifecycle != VisitLifecycleState.BOOKED) return null

        return classifyBookedVisit(visit.actualServiceDate, visit.scheduledAtEpochMillis, today, now, businessZone, dueSoonHorizonDays)
    }

    /** Shared scheduling classification for canonical Visits and legacy unlinked Outbox rows. */
    fun classifyBookedVisit(
        serviceDateText: String,
        scheduledAtEpochMillis: Long?,
        today: LocalDate,
        now: Instant,
        businessZone: ZoneId,
        dueSoonHorizonDays: Int,
    ): OperationalWorkState? {
        val appointment = scheduledAtEpochMillis?.let(Instant::ofEpochMilli)
        if (appointment != null && appointment.isBefore(now)) return OperationalWorkState.OVERDUE

        val serviceDate = serviceDateText.toLocalDateOrNull() ?: return null
        if (appointment == null && serviceDate.isBefore(today)) return OperationalWorkState.OVERDUE

        val applicableDate = appointment?.atZone(businessZone)?.toLocalDate() ?: serviceDate
        return if (!applicableDate.isBefore(today) && !applicableDate.isAfter(today.plusDays(dueSoonHorizonDays.toLong()))) {
            OperationalWorkState.DUE_SOON
        } else {
            OperationalWorkState.BOOKED
        }
    }

    fun classifyService(dueDate: String, today: LocalDate, dueSoonHorizonDays: Int): OperationalWorkState? {
        val due = dueDate.toLocalDateOrNull() ?: return null
        return when {
            due.isBefore(today) -> OperationalWorkState.OVERDUE
            !due.isAfter(today.plusDays(dueSoonHorizonDays.toLong())) -> OperationalWorkState.DUE_SOON
            else -> null
        }
    }

    fun classifyFollowUp(state: String, dueDate: String, today: LocalDate, dueSoonHorizonDays: Int): OperationalWorkState? {
        if (state != "OPEN") return null
        val due = dueDate.toLocalDateOrNull() ?: return null
        return when {
            due.isBefore(today) -> OperationalWorkState.OVERDUE
            !due.isAfter(today.plusDays(dueSoonHorizonDays.toLong())) -> OperationalWorkState.DUE_SOON
            else -> OperationalWorkState.BOOKED
        }
    }

    fun urgencyRank(state: OperationalWorkState): Int = when (state) {
        OperationalWorkState.OVERDUE -> 0
        OperationalWorkState.DUE_SOON -> 1
        OperationalWorkState.IN_PROGRESS -> 2
        OperationalWorkState.BOOKED -> 3
    }

    fun sectionTitle(kind: OperationalWorkKind, state: OperationalWorkState): String = "${kind.title}s - ${state.label}"

    private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
}

object OperationalDashboardProjector {
    private val order = listOf(
        OperationalWorkKind.VISIT to OperationalWorkState.IN_PROGRESS,
        OperationalWorkKind.VISIT to OperationalWorkState.OVERDUE,
        OperationalWorkKind.SERVICE to OperationalWorkState.OVERDUE,
        OperationalWorkKind.FOLLOW_UP to OperationalWorkState.OVERDUE,
        OperationalWorkKind.VISIT to OperationalWorkState.DUE_SOON,
        OperationalWorkKind.SERVICE to OperationalWorkState.DUE_SOON,
        OperationalWorkKind.FOLLOW_UP to OperationalWorkState.DUE_SOON,
        OperationalWorkKind.VISIT to OperationalWorkState.BOOKED,
        OperationalWorkKind.FOLLOW_UP to OperationalWorkState.BOOKED,
    )

    fun project(
        scope: WorkScope,
        visits: List<VisitSummary>,
        dueServices: List<DueService>,
        followUps: List<FollowUpDetail>,
        today: LocalDate,
        now: Instant,
        businessZone: ZoneId,
        dueSoonHorizonDays: Int,
    ): OperationalDashboardProjection {
        val items = buildList {
            visits.forEach { visit ->
                val state = OperationalWorkClassifier.classifyVisit(visit, today, now, businessZone, dueSoonHorizonDays) ?: return@forEach
                add(
                    OperationalWorkItem(
                        kind = OperationalWorkKind.VISIT,
                        state = state,
                        recordId = visit.id,
                        customerId = visit.customerId,
                        siteId = visit.siteId.takeIf(String::isNotBlank),
                        equipmentId = visit.equipmentId,
                        displayReference = visit.reference,
                        displayTitle = visit.siteName,
                        displayContext = visit.customerName,
                        dueDate = visit.actualServiceDate.toLocalDateOrNull(),
                        scheduledAtEpochMillis = visit.scheduledAtEpochMillis,
                        modifiedAtEpochMillis = visit.modifiedAtEpochMillis,
                        workItemId = visit.resumeWorkItemId,
                    ),
                )
            }
            dueServices.forEach { service ->
                val state = OperationalWorkClassifier.classifyService(service.dueDate, today, dueSoonHorizonDays) ?: return@forEach
                add(
                    OperationalWorkItem(
                        kind = OperationalWorkKind.SERVICE,
                        state = state,
                        recordId = service.planId,
                        customerId = service.customerId,
                        siteId = service.siteId,
                        equipmentId = service.equipmentId,
                        displayReference = service.planReference,
                        displayTitle = service.planName,
                        displayContext = "${service.equipmentReference} · ${service.equipmentName} · ${service.siteName}",
                        dueDate = service.dueDate.toLocalDateOrNull(),
                        scheduledAtEpochMillis = null,
                        modifiedAtEpochMillis = 0L,
                    ),
                )
            }
            followUps.forEach { followUp ->
                val state = OperationalWorkClassifier.classifyFollowUp(followUp.state, followUp.dueDate, today, dueSoonHorizonDays) ?: return@forEach
                add(
                    OperationalWorkItem(
                        kind = OperationalWorkKind.FOLLOW_UP,
                        state = state,
                        recordId = followUp.id,
                        customerId = followUp.customerId,
                        siteId = followUp.siteId,
                        equipmentId = followUp.equipmentId,
                        displayReference = followUp.reference,
                        displayTitle = followUp.title,
                        displayContext = listOfNotNull(followUp.customerName, followUp.siteName, followUp.equipmentName).filter(String::isNotBlank).joinToString(" · "),
                        dueDate = followUp.dueDate.toLocalDateOrNull(),
                        scheduledAtEpochMillis = null,
                        modifiedAtEpochMillis = followUp.updatedAtEpochMillis,
                    ),
                )
            }
        }.filter { item ->
            when (scope) {
                WorkScope.Global -> true
                is WorkScope.Customer -> item.customerId == scope.customerId
            }
        }

        val sections = order.mapNotNull { (kind, state) ->
            val matching = items.filter { it.kind == kind && it.state == state }.sortedWith(itemComparator(state))
            matching.takeIf(List<OperationalWorkItem>::isNotEmpty)?.let {
                OperationalDashboardSection(kind, state, OperationalWorkClassifier.sectionTitle(kind, state), it)
            }
        }
        return OperationalDashboardProjection(scope, sections)
    }

    private fun itemComparator(state: OperationalWorkState): Comparator<OperationalWorkItem> = when (state) {
        OperationalWorkState.IN_PROGRESS -> compareByDescending<OperationalWorkItem> { it.modifiedAtEpochMillis }
            .thenBy { it.displayReference }.thenBy { it.recordId }
        OperationalWorkState.OVERDUE -> compareBy<OperationalWorkItem> { sortDate(it) }
            .thenBy { visitTimeRank(it) }
            .thenBy { it.scheduledAtEpochMillis ?: Long.MAX_VALUE }
            .thenBy { it.displayReference }.thenBy { it.recordId }
        OperationalWorkState.DUE_SOON, OperationalWorkState.BOOKED -> compareBy<OperationalWorkItem> { sortDate(it) }
            .thenBy { visitTimeRank(it) }
            .thenBy { it.scheduledAtEpochMillis ?: Long.MAX_VALUE }
            .thenBy { it.displayReference }.thenBy { it.recordId }
    }

    private fun sortDate(item: OperationalWorkItem): Long = item.dueDate?.toEpochDay()?.let { Math.multiplyExact(it, MILLIS_PER_DAY) }
        ?: Long.MAX_VALUE

    private fun visitTimeRank(item: OperationalWorkItem): Int =
        if (item.kind == OperationalWorkKind.VISIT && item.scheduledAtEpochMillis != null) 0 else 1

    private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

    private const val MILLIS_PER_DAY = 86_400_000L
}
