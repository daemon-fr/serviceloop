package com.v16studio.serviceloop

import com.v16studio.serviceloop.domain.DueBucket
import com.v16studio.serviceloop.domain.DueService
import com.v16studio.serviceloop.domain.DueServiceDateFilter
import com.v16studio.serviceloop.domain.DueServiceVisitFilter
import com.v16studio.serviceloop.domain.FollowUpDateFilter
import com.v16studio.serviceloop.domain.FollowUpDetail
import com.v16studio.serviceloop.domain.FollowUpStatusFilter
import com.v16studio.serviceloop.domain.VisitDateFilter
import com.v16studio.serviceloop.domain.VisitStatusFilter
import com.v16studio.serviceloop.domain.VisitSummary
import com.v16studio.serviceloop.domain.filterDueServices
import com.v16studio.serviceloop.domain.filterFollowUps
import com.v16studio.serviceloop.domain.filterVisits
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WorkFiltersTest {
    private val today = LocalDate.of(2026, 9, 11)

    @Test fun dueServiceFiltersKeepDateBucketsAndVisitAssociationIndependent() {
        val values = listOf(
            due("overdue-no-visit", DueBucket.OVERDUE, null),
            due("overdue-has-visit", DueBucket.OVERDUE, "visit-1"),
            due("today-no-visit", DueBucket.TODAY, null),
        )

        assertEquals(
            listOf("overdue-no-visit", "today-no-visit"),
            filterDueServices(values, DueServiceDateFilter.ALL, DueServiceVisitFilter.NO_VISIT, "").map { it.planId },
        )
        assertEquals(
            listOf("overdue-has-visit"),
            filterDueServices(values, DueServiceDateFilter.ALL, DueServiceVisitFilter.HAS_VISIT, "").map { it.planId },
        )
        assertEquals(
            listOf("overdue-no-visit"),
            filterDueServices(values, DueServiceDateFilter.OVERDUE, DueServiceVisitFilter.NO_VISIT, "").map { it.planId },
        )
        assertEquals(
            listOf("overdue-no-visit", "overdue-has-visit"),
            filterDueServices(values, DueServiceDateFilter.OVERDUE, DueServiceVisitFilter.ALL, "").map { it.planId },
        )
    }

    @Test fun visitDateFiltersApplyOperationalOverdueAndAdoptedCalendarBoundaries() {
        val values = listOf(
            visit("old-booked", "2026-08-01", "BOOKED"),
            visit("old-working", "2026-09-01", "WORKING"),
            visit("old-completed", "2026-09-01", "COMPLETED"),
            visit("old-canceled", "2026-09-01", "CANCELED"),
            visit("past-boundary", "2026-08-12", "COMPLETED"),
            visit("past-outside", "2026-08-11", "COMPLETED"),
            visit("today-booked", "2026-09-11", "BOOKED"),
            visit("today-canceled", "2026-09-11", "CANCELED"),
            visit("future-working", "2026-09-12", "WORKING"),
        )

        assertEquals(
            listOf("old-booked", "old-working"),
            filterVisits(values, VisitDateFilter.OVERDUE, VisitStatusFilter.ALL, today, "").map { it.id },
        )
        assertEquals(
            listOf("today-booked", "today-canceled"),
            filterVisits(values, VisitDateFilter.TODAY, VisitStatusFilter.ALL, today, "").map { it.id },
        )
        assertEquals(
            listOf("future-working"),
            filterVisits(values, VisitDateFilter.UPCOMING, VisitStatusFilter.ALL, today, "").map { it.id },
        )
        assertEquals(
            listOf("old-working", "old-completed", "old-canceled", "past-boundary"),
            filterVisits(values, VisitDateFilter.PAST_30_DAYS, VisitStatusFilter.ALL, today, "").map { it.id },
        )
        assertEquals(
            listOf("old-working"),
            filterVisits(values, VisitDateFilter.OVERDUE, VisitStatusFilter.WORKING, today, "").map { it.id },
        )
        assertEquals(
            emptyList<String>(),
            filterVisits(values, VisitDateFilter.OVERDUE, VisitStatusFilter.COMPLETED, today, "").map { it.id },
        )
    }

    @Test fun followUpFiltersUseIndependentNonOverlappingDateAndStatusDimensions() {
        val values = listOf(
            follow("overdue-open", "2026-09-10", "OPEN"),
            follow("today-open", "2026-09-11", "OPEN"),
            follow("upcoming-open", "2026-09-12", "OPEN"),
            follow("overdue-closed", "2026-09-10", "RESOLVED"),
        )

        assertEquals(
            listOf("overdue-open", "overdue-closed"),
            filterFollowUps(values, FollowUpDateFilter.OVERDUE, FollowUpStatusFilter.ALL, today, "").map { it.id },
        )
        assertEquals(
            listOf("today-open"),
            filterFollowUps(values, FollowUpDateFilter.TODAY, FollowUpStatusFilter.OPEN, today, "").map { it.id },
        )
        assertEquals(
            listOf("upcoming-open"),
            filterFollowUps(values, FollowUpDateFilter.UPCOMING, FollowUpStatusFilter.OPEN, today, "").map { it.id },
        )
        assertEquals(
            listOf("overdue-closed"),
            filterFollowUps(values, FollowUpDateFilter.ALL, FollowUpStatusFilter.CLOSED, today, "").map { it.id },
        )
    }

    @Test fun filterLabelsMatchTheAdoptedWorkMatrix() {
        assertEquals(listOf("All", "Overdue", "Today", "Due soon", "Upcoming"), DueServiceDateFilter.entries.map { it.label })
        assertEquals(listOf("All", "No visit", "Has visit"), DueServiceVisitFilter.entries.map { it.label })
        assertEquals(listOf("All", "Overdue", "Today", "Upcoming", "Past 30 days"), VisitDateFilter.entries.map { it.label })
        assertEquals(listOf("All", "Booked", "Working", "Completed", "Canceled"), VisitStatusFilter.entries.map { it.label })
        assertEquals(listOf("All", "Overdue", "Today", "Upcoming"), FollowUpDateFilter.entries.map { it.label })
        assertEquals(listOf("All", "Open", "Closed"), FollowUpStatusFilter.entries.map { it.label })
    }

    private fun due(id: String, bucket: DueBucket, claimedVisitId: String?) = DueService(
        planId = id,
        planReference = "P-$id",
        planName = "Service $id",
        dueDate = "2026-09-11",
        obligationId = "obligation-$id",
        equipmentId = "equipment-$id",
        equipmentReference = "EQ-$id",
        equipmentName = "Equipment $id",
        siteId = "site-$id",
        siteName = "Site $id",
        customerId = "customer-$id",
        customerName = "Customer $id",
        claimedVisitId = claimedVisitId,
        bucket = bucket,
    )

    private fun visit(id: String, date: String, state: String) = VisitSummary(
        id = id,
        reference = "V-$id",
        siteName = "Site $id",
        actualServiceDate = date,
        state = state,
        finalRecordId = null,
    )

    private fun follow(id: String, date: String, state: String) = FollowUpDetail(
        id = id,
        reference = "F-$id",
        type = "CONTACT",
        title = "Follow-up $id",
        dueDate = date,
        state = state,
        customerId = "customer-$id",
        siteId = null,
        equipmentId = null,
        privatePlanningNote = "",
        closureReason = null,
    )
}
