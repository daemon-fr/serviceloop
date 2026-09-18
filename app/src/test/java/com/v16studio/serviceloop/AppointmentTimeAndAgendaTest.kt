package com.v16studio.serviceloop

import com.v16studio.serviceloop.domain.AgendaProjector
import com.v16studio.serviceloop.domain.CustomerType
import com.v16studio.serviceloop.domain.DueBucket
import com.v16studio.serviceloop.domain.DueService
import com.v16studio.serviceloop.domain.FollowUpDetail
import com.v16studio.serviceloop.domain.OperationalWorkKind
import com.v16studio.serviceloop.domain.VisitSummary
import com.v16studio.serviceloop.domain.appointmentEpochMillis
import com.v16studio.serviceloop.domain.formatAppointmentTime
import com.v16studio.serviceloop.domain.sortVisitsForDisplay
import com.v16studio.serviceloop.domain.VisitDateFilter
import com.v16studio.serviceloop.domain.VisitStatusFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppointmentTimeAndAgendaTest {
    private val zone = ZoneId.of("Europe/Bucharest")
    private val today = LocalDate.parse("2026-09-18")

    @Test
    fun appointmentTimeUsesBusinessZoneAndDateOnlyRemainsNull() {
        val epoch = appointmentEpochMillis("2026-09-19", "09:30", zone)
        assertEquals(Instant.parse("2026-09-19T06:30:00Z").toEpochMilli(), epoch)
        assertEquals("09:30", formatAppointmentTime(epoch, zone))
        assertNull(appointmentEpochMillis("2026-09-19", null, zone))
    }

    @Test
    fun agendaSeparatesActionableUnresolvedAndUpcomingAndExcludesResolvedHistory() {
        val overdueTimed = visit("overdue-timed", "2026-09-17", appointmentEpochMillis("2026-09-17", "09:00", zone))
        val overdueUntimed = visit("overdue-untimed", "2026-09-17", null)
        val todayTimed = visit("today-timed", "2026-09-18", appointmentEpochMillis("2026-09-18", "10:00", zone))
        val todayUntimed = visit("today-untimed", "2026-09-18", null)
        val completed = visit("completed", "2026-09-16", null, state = "COMPLETED")
        val canceled = visit("canceled", "2026-09-20", null, state = "CANCELED")
        val service = dueService("service", "2026-09-19")
        val followUp = followUp("follow-up", "2026-09-18", state = "OPEN")
        val closedFollowUp = followUp("closed-follow-up", "2026-09-19", state = "CLOSED")

        val projection = AgendaProjector.project(
            visits = listOf(overdueUntimed, overdueTimed, todayUntimed, todayTimed, completed, canceled),
            dueServices = listOf(service),
            followUps = listOf(followUp, closedFollowUp),
            today = today,
            businessZone = zone,
        )

        assertEquals(listOf("overdue-timed", "overdue-untimed"), projection.unresolved.map { it.recordId })
        assertEquals(listOf("today-timed", "today-untimed", "follow-up", "service"), projection.upcoming.map { it.recordId })
        assertFalse(projection.unresolved.any { it.recordId in setOf("completed", "canceled", "closed-follow-up") })
        assertEquals(6, projection.unresolved.size + projection.upcoming.size)
    }

    @Test
    fun visitQueueUsesTimedVisitsBeforeUntimedVisitsOnSameDate() {
        val untimed = visit("untimed", "2026-09-19", null)
        val late = visit("late", "2026-09-19", appointmentEpochMillis("2026-09-19", "14:00", zone))
        val early = visit("early", "2026-09-19", appointmentEpochMillis("2026-09-19", "08:00", zone))
        val ordered = sortVisitsForDisplay(listOf(untimed, late, early), VisitDateFilter.UPCOMING, VisitStatusFilter.BOOKED)
        assertEquals(listOf("early", "late", "untimed"), ordered.map { it.id })
    }

    private fun visit(id: String, date: String, scheduled: Long?, state: String = "BOOKED") = VisitSummary(
        id = id,
        reference = id.uppercase(),
        siteName = "Site $id",
        actualServiceDate = date,
        state = state,
        finalRecordId = null,
        customerId = "customer-$id",
        customerName = "Customer $id",
        siteId = "site-$id",
        scheduledAtEpochMillis = scheduled,
    )

    private fun dueService(id: String, date: String) = DueService(
        planId = id,
        planReference = id.uppercase(),
        planName = "Plan $id",
        dueDate = date,
        obligationId = "obligation-$id",
        equipmentId = "equipment-$id",
        equipmentReference = "EQ-$id",
        equipmentName = "Equipment $id",
        siteId = "site-$id",
        siteName = "Site $id",
        customerId = "customer-$id",
        customerName = "Customer $id",
        claimedVisitId = null,
        bucket = DueBucket.UPCOMING,
    )

    private fun followUp(id: String, date: String, state: String) = FollowUpDetail(
        id = id,
        reference = id.uppercase(),
        type = "CONTACT",
        title = "Follow-up $id",
        dueDate = date,
        state = state,
        customerId = "customer-$id",
        siteId = "site-$id",
        equipmentId = null,
        privatePlanningNote = "",
        closureReason = null,
        customerName = "Customer $id",
        siteName = "Site $id",
    )
}
