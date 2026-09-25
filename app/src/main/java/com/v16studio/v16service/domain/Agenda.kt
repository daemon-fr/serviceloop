package com.v16studio.v16service.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class AgendaSection { UNRESOLVED, UPCOMING }

data class AgendaItem(
    val kind: OperationalWorkKind,
    val recordId: String,
    val reference: String,
    val date: LocalDate,
    val scheduledAtEpochMillis: Long?,
    val identity: String,
    val context: String,
)

data class AgendaProjection(
    val unresolved: List<AgendaItem>,
    val upcoming: List<AgendaItem>,
)

/** A read-only projection of current Visits, obligations, and open Follow-ups. */
object AgendaProjector {
    fun project(
        visits: List<VisitSummary>,
        dueServices: List<DueService>,
        followUps: List<FollowUpDetail>,
        today: LocalDate,
        businessZone: ZoneId,
    ): AgendaProjection {
        val items = buildList {
            visits.forEach { visit ->
                val lifecycle = runCatching { VisitLifecycleState.fromPersisted(visit.state) }.getOrNull() ?: return@forEach
                if (lifecycle !in setOf(VisitLifecycleState.BOOKED, VisitLifecycleState.WORKING)) return@forEach
                val date = runCatching { LocalDate.parse(visit.actualServiceDate) }.getOrNull() ?: return@forEach
                add(
                    AgendaItem(
                        kind = OperationalWorkKind.VISIT,
                        recordId = visit.id,
                        reference = visit.reference,
                        date = date,
                        scheduledAtEpochMillis = visit.scheduledAtEpochMillis,
                        identity = visit.customerName.ifBlank { visit.siteName },
                        context = visit.siteName,
                    ),
                )
            }
            dueServices.forEach { service ->
                val date = runCatching { LocalDate.parse(service.dueDate) }.getOrNull() ?: return@forEach
                add(
                    AgendaItem(
                        kind = OperationalWorkKind.SERVICE,
                        recordId = service.planId,
                        reference = service.planReference,
                        date = date,
                        scheduledAtEpochMillis = null,
                        identity = service.planName,
                        context = listOf(service.equipmentName, service.siteName).filter(String::isNotBlank).joinToString(" · "),
                    ),
                )
            }
            followUps.forEach { followUp ->
                if (followUp.state != "OPEN") return@forEach
                val date = runCatching { LocalDate.parse(followUp.dueDate) }.getOrNull() ?: return@forEach
                add(
                    AgendaItem(
                        kind = OperationalWorkKind.FOLLOW_UP,
                        recordId = followUp.id,
                        reference = followUp.reference,
                        date = date,
                        scheduledAtEpochMillis = null,
                        identity = followUp.title,
                        context = listOfNotNull(followUp.customerName, followUp.siteName, followUp.equipmentName)
                            .filter(String::isNotBlank)
                            .joinToString(" · "),
                    ),
                )
            }
        }
        val sorted = items.sortedWith(itemComparator(businessZone))
        return AgendaProjection(
            unresolved = sorted.filter { it.date.isBefore(today) },
            upcoming = sorted.filter { !it.date.isBefore(today) },
        )
    }

    private fun itemComparator(businessZone: ZoneId): Comparator<AgendaItem> = compareBy<AgendaItem> { it.date }
        .thenBy { if (it.kind == OperationalWorkKind.VISIT && it.scheduledAtEpochMillis != null) 0 else 1 }
        .thenBy { timedLocalInstant(it, businessZone) ?: Instant.MAX }
        .thenBy { it.kind.ordinal }
        .thenBy { it.reference }
        .thenBy { it.recordId }

    private fun timedLocalInstant(item: AgendaItem, businessZone: ZoneId): Instant? =
        if (item.kind == OperationalWorkKind.VISIT && item.scheduledAtEpochMillis != null) {
            Instant.ofEpochMilli(item.scheduledAtEpochMillis)
                .atZone(businessZone)
                .toLocalTime()
                .atDate(item.date)
                .atZone(businessZone)
                .toInstant()
        } else null
}
