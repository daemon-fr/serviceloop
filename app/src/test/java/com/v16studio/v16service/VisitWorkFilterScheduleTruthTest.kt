package com.v16studio.v16service

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.v16service.data.RoomV16ServiceRepository
import com.v16studio.v16service.data.V16ServiceDatabase
import com.v16studio.v16service.domain.BusinessTime
import com.v16studio.v16service.domain.CustomerInput
import com.v16studio.v16service.domain.EquipmentInput
import com.v16studio.v16service.domain.PlanInput
import com.v16studio.v16service.domain.SiteInput
import com.v16studio.v16service.domain.VisitDateFilter
import com.v16studio.v16service.domain.VisitStatusFilter
import com.v16studio.v16service.domain.OperationalWorkKind
import com.v16studio.v16service.domain.OperationalWorkState
import com.v16studio.v16service.domain.WorkScope
import com.v16studio.v16service.domain.filterVisits
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VisitWorkFilterScheduleTruthTest {
    private lateinit var db: V16ServiceDatabase
    private lateinit var root: File
    private lateinit var time: MutableTestBusinessTime
    private lateinit var repo: RoomV16ServiceRepository
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, V16ServiceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        V16ServiceDatabase.configureStage4Tracking(db.openHelper.writableDatabase); V16ServiceDatabase.configureReminderDefaults(db.openHelper.writableDatabase)
        root = File(context.cacheDir, "work-filter-schedule-${System.nanoTime()}").apply { mkdirs() }
        time = MutableTestBusinessTime(Instant.parse("2026-09-06T10:00:00Z"))
        repo = RoomV16ServiceRepository(db, time, attachmentRoot = root)
    }

    @After
    fun close() {
        db.close()
        root.deleteRecursively()
    }

    @Test
    fun bookedDateSurvivesStartAndDrivesOverdueWorkProjection() = runTest {
        val planId = foundation()
        val visitId = repo.createVisit(listOf(planId), "BOOKED", "2026-09-07")

        time.now = Instant.parse("2026-09-09T10:00:00Z")
        repo.startVisit(visitId)

        val persisted = db.v16ServiceDao().visit(visitId)!!
        assertEquals("WORKING", persisted.state)
        assertEquals("2026-09-09", persisted.actualServiceDate)

        val started = db.v16ServiceDao().visitScheduleEvents(visitId).single { it.eventType == "STARTED" }
        assertEquals("2026-09-07", started.oldServiceDate)
        assertEquals("2026-09-09", started.newServiceDate)

        val summary = repo.visits().single { it.id == visitId }
        assertEquals("2026-09-07", summary.actualServiceDate)
        assertEquals(
            listOf(visitId),
            filterVisits(
                listOf(summary),
                VisitDateFilter.OVERDUE,
                VisitStatusFilter.ALL,
                LocalDate.of(2026, 9, 9),
                "",
            ).map { it.id },
        )
    }

    @Test
    fun latestRescheduledDateBecomesTheWorkingOverdueReference() = runTest {
        val planId = foundation()
        val visitId = repo.createVisit(listOf(planId), "BOOKED", "2026-09-08")
        repo.rescheduleVisit(visitId, "2026-09-09", null, "Customer requested another day")

        time.now = Instant.parse("2026-09-11T10:00:00Z")
        repo.startVisit(visitId)

        val persisted = db.v16ServiceDao().visit(visitId)!!
        assertEquals("2026-09-11", persisted.actualServiceDate)
        val started = db.v16ServiceDao().visitScheduleEvents(visitId).single { it.eventType == "STARTED" }
        assertEquals("2026-09-09", started.oldServiceDate)

        val summary = repo.visits().single { it.id == visitId }
        assertEquals("2026-09-09", summary.actualServiceDate)
        assertTrue(
            filterVisits(
                listOf(summary),
                VisitDateFilter.OVERDUE,
                VisitStatusFilter.WORKING,
                LocalDate.of(2026, 9, 11),
                "",
            ).single().id == visitId,
        )
    }

    @Test
    fun directWorkingVisitFallsBackToActualDateWithoutInventingBookingHistory() = runTest {
        val planId = foundation()
        time.now = Instant.parse("2026-09-09T10:00:00Z")
        val visitId = repo.createVisit(listOf(planId), "WORKING", "2026-09-09")

        assertTrue(db.v16ServiceDao().visitScheduleEvents(visitId).none { it.eventType == "STARTED" })
        val summary = repo.visits().single { it.id == visitId }
        assertEquals("2026-09-09", summary.actualServiceDate)
        assertEquals(
            listOf(visitId),
            filterVisits(
                listOf(summary),
                VisitDateFilter.TODAY,
                VisitStatusFilter.WORKING,
                LocalDate.of(2026, 9, 9),
                "",
            ).map { it.id },
        )
    }

    @Test
    fun exactBookedAppointmentUsesInjectedBusinessTimeForDashboardTruth() = runTest {
        val planId = foundation()
        val appointment = Instant.parse("2026-09-06T12:00:00Z")
        val visitId = repo.createVisit(listOf(planId), "BOOKED", "2026-09-06", appointment.toEpochMilli())

        val projection = repo.operationalDashboard(WorkScope.Global)

        assertEquals(OperationalWorkState.DUE_SOON, projection.stateFor(OperationalWorkKind.VISIT, visitId))
        assertEquals(
            visitId,
            projection.sections.single { it.kind == OperationalWorkKind.VISIT && it.state == OperationalWorkState.DUE_SOON }
                .items.single().recordId,
        )
    }

    private suspend fun foundation(): String {
        val customerId = repo.createCustomer(CustomerInput("Filter customer"))
        val siteId = repo.createSite(customerId, SiteInput("Filter site", "1 Service Road"))
        val equipmentId = repo.createEquipment(siteId, EquipmentInput("Filter equipment"))
        return repo.createPlan(
            equipmentId,
            PlanInput("Annual service", 1, "YEARS", "2026-09-01"),
        )
    }

    private class MutableTestBusinessTime(var now: Instant) : BusinessTime {
        override val zoneId: ZoneId = ZoneId.of("Europe/Bucharest")
        override fun instant(): Instant = now
    }
}
