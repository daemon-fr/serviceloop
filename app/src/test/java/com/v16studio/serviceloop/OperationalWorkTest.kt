package com.v16studio.serviceloop

import com.v16studio.serviceloop.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class OperationalWorkTest {
    private val today = LocalDate.parse("2026-09-16")
    private val zone = ZoneId.of("Europe/Bucharest")
    private val now = Instant.parse("2026-09-16T09:00:00Z")
    private val horizon = 14

    @Test fun visitsUseLifecycleAppointmentAndBusinessDateRules() {
        assertEquals(OperationalWorkState.IN_PROGRESS, visit("working", "2020-01-01", "WORKING").classify())
        assertEquals(OperationalWorkState.OVERDUE, visit("past-time", "2026-09-16", "BOOKED", now.minusSeconds(1).toEpochMilli()).classify())
        assertEquals(OperationalWorkState.DUE_SOON, visit("later-today", "2026-09-16", "BOOKED", now.plusSeconds(60).toEpochMilli()).classify())
        assertEquals(OperationalWorkState.OVERDUE, visit("yesterday", "2026-09-15", "BOOKED").classify())
        assertEquals(OperationalWorkState.DUE_SOON, visit("today", "2026-09-16", "BOOKED").classify())
        assertEquals(OperationalWorkState.DUE_SOON, visit("horizon", "2026-09-30", "BOOKED").classify())
        assertEquals(OperationalWorkState.BOOKED, visit("later", "2026-10-01", "BOOKED").classify())
        assertNull(visit("completed", "2026-09-16", "COMPLETED").classify())
        assertNull(visit("canceled", "2026-09-16", "CANCELED").classify())
    }

    @Test fun servicesRemainOutstandingWhenClaimedAndFutureBeyondHorizonIsExcluded() {
        assertEquals(OperationalWorkState.OVERDUE, OperationalWorkClassifier.classifyService("2026-09-15", today, horizon))
        assertEquals(OperationalWorkState.DUE_SOON, OperationalWorkClassifier.classifyService("2026-09-16", today, horizon))
        assertEquals(OperationalWorkState.DUE_SOON, OperationalWorkClassifier.classifyService("2026-09-30", today, horizon))
        assertNull(OperationalWorkClassifier.classifyService("2026-10-01", today, horizon))

        val claimed = due("plan-claimed", "2026-09-15", customerId = "customer-a", claimedVisitId = "visit-a")
        val projection = project(due = listOf(claimed))
        assertEquals("plan-claimed", projection.sections.single().items.single().recordId)
        assertEquals(OperationalWorkState.OVERDUE, projection.sections.single().state)
    }

    @Test fun followUpsUseOnlyOpenActionableRows() {
        assertEquals(OperationalWorkState.OVERDUE, OperationalWorkClassifier.classifyFollowUp("OPEN", "2026-09-15", today, horizon))
        assertEquals(OperationalWorkState.DUE_SOON, OperationalWorkClassifier.classifyFollowUp("OPEN", "2026-09-16", today, horizon))
        assertEquals(OperationalWorkState.DUE_SOON, OperationalWorkClassifier.classifyFollowUp("OPEN", "2026-09-30", today, horizon))
        assertEquals(OperationalWorkState.BOOKED, OperationalWorkClassifier.classifyFollowUp("OPEN", "2026-10-01", today, horizon))
        listOf("RESOLVED", "CANCELLED", "CLOSED", "COMPLETED", "CANCELED").forEach {
            assertNull(OperationalWorkClassifier.classifyFollowUp(it, "2026-09-15", today, horizon))
        }
    }

    @Test fun urgencySectionOrderEmptyBucketsAndCustomerIdentityAreShared() {
        assertEquals(
            listOf("Visits - In progress", "Visits - Overdue", "Services - Overdue", "Follow-ups - Overdue", "Visits - Due soon", "Services - Due soon", "Follow-ups - Due soon", "Visits - Booked", "Follow-ups - Booked"),
            project(
                visits = listOf(
                    visit("v-working", "2026-09-16", "WORKING", customerId = "a"),
                    visit("v-overdue", "2026-09-15", "BOOKED", customerId = "a"),
                    visit("v-soon", "2026-09-16", "BOOKED", customerId = "a"),
                    visit("v-booked", "2026-10-08", "BOOKED", customerId = "a"),
                    visit("v-complete", "2026-09-15", "COMPLETED", customerId = "a"),
                ),
                due = listOf(due("s-overdue", "2026-09-15", "a"), due("s-soon", "2026-09-16", "a"), due("s-future", "2026-10-01", "a")),
                followUps = listOf(follow("f-overdue", "2026-09-15", "OPEN", "a"), follow("f-soon", "2026-09-16", "OPEN", "a"), follow("f-booked", "2026-10-01", "OPEN", "a"), follow("f-closed", "2026-09-15", "CLOSED", "a")),
            ).sections.map { it.title },
        )

        val mixedCustomer = project(
            scope = WorkScope.Customer("a"),
            visits = listOf(visit("working-a", "2026-09-16", "WORKING", customerId = "a"), visit("booked-b", "2026-10-08", "BOOKED", customerId = "b")),
            due = listOf(due("overdue-a", "2026-09-15", "a")),
        )
        assertEquals(listOf("working-a", "overdue-a"), mixedCustomer.sections.flatMap { it.items }.map { it.recordId })
        assertEquals(OperationalWorkState.OVERDUE, mixedCustomer.mostUrgentState)
        assertTrue(mixedCustomer.sections.flatMap { it.items }.all { it.customerId == "a" })
        assertEquals(OperationalWorkState.IN_PROGRESS, project(visits = listOf(visit("working-a", "2026-09-16", "WORKING", customerId = "a"), visit("booked-a", "2026-10-08", "BOOKED", customerId = "a"))).mostUrgentState)
        assertEquals(OperationalWorkState.BOOKED, project(visits = listOf(visit("booked-a", "2026-10-08", "BOOKED", customerId = "a"))).mostUrgentState)
        assertTrue(project(scope = WorkScope.Customer("missing")).sections.isEmpty())
        assertTrue(WorkScope.Customer("a").includesCustomer("a"))
        assertTrue(WorkScope.Global.includesCustomer("b"))
        assertEquals(OperationalWorkState.IN_PROGRESS, mixedCustomer.stateFor(OperationalWorkKind.VISIT, "working-a"))
        assertNull(mixedCustomer.stateFor(OperationalWorkKind.VISIT, "booked-b"))
    }

    @Test fun customerUrgencyPriorityIsOverdueThenDueSoonThenInProgressThenBooked() {
        assertEquals(0, OperationalWorkClassifier.urgencyRank(OperationalWorkState.OVERDUE))
        assertEquals(1, OperationalWorkClassifier.urgencyRank(OperationalWorkState.DUE_SOON))
        assertEquals(2, OperationalWorkClassifier.urgencyRank(OperationalWorkState.IN_PROGRESS))
        assertEquals(3, OperationalWorkClassifier.urgencyRank(OperationalWorkState.BOOKED))
    }

    private fun project(
        scope: WorkScope = WorkScope.Global,
        visits: List<VisitSummary> = emptyList(),
        due: List<DueService> = emptyList(),
        followUps: List<FollowUpDetail> = emptyList(),
    ) = OperationalDashboardProjector.project(scope, visits, due, followUps, today, now, zone, horizon)

    private fun visit(id: String, date: String, state: String, scheduled: Long? = null, customerId: String = "customer-a") = VisitSummary(
        id = id,
        reference = id.uppercase(),
        siteName = "Site $customerId",
        actualServiceDate = date,
        state = state,
        finalRecordId = null,
        resumeWorkItemId = "work-$id",
        customerId = customerId,
        customerName = "Customer $customerId",
        siteId = "site-$customerId",
        scheduledAtEpochMillis = scheduled,
        modifiedAtEpochMillis = 1,
    )

    private fun due(id: String, date: String, customerId: String, claimedVisitId: String? = null) = DueService(
        planId = id,
        planReference = "P-$id",
        planName = "Plan $id",
        dueDate = date,
        obligationId = "obligation-$id",
        equipmentId = "equipment-$id",
        equipmentReference = "EQ-$id",
        equipmentName = "Equipment $id",
        siteId = "site-$customerId",
        siteName = "Site $customerId",
        customerId = customerId,
        customerName = "Customer $customerId",
        claimedVisitId = claimedVisitId,
        bucket = DueBucket.OVERDUE,
    )

    private fun follow(id: String, date: String, state: String, customerId: String) = FollowUpDetail(
        id = id,
        reference = "FU-$id",
        type = "CONTACT",
        title = "Follow up $id",
        dueDate = date,
        state = state,
        customerId = customerId,
        siteId = "site-$customerId",
        equipmentId = null,
        privatePlanningNote = "private",
        closureReason = null,
        customerName = "Customer $customerId",
        siteName = "Site $customerId",
    )

    private fun VisitSummary.classify() = OperationalWorkClassifier.classifyVisit(this, today, now, zone, horizon)
}
