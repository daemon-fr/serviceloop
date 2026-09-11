package com.v16studio.serviceloop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.domain.CustomerInput
import com.v16studio.serviceloop.domain.EquipmentInput
import com.v16studio.serviceloop.domain.PlanInput
import com.v16studio.serviceloop.domain.SiteInput
import com.v16studio.serviceloop.domain.VisitDateFilter
import com.v16studio.serviceloop.domain.VisitStatusFilter
import com.v16studio.serviceloop.domain.filterVisits
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
    private lateinit var db: ServiceLoopDatabase
    private lateinit var root: File
    private lateinit var time: MutableTestBusinessTime
    private lateinit var repo: RoomServiceLoopRepository
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ServiceLoopDatabase.configureStage4Tracking(db.openHelper.writableDatabase)
        root = File(context.cacheDir, "work-filter-schedule-${System.nanoTime()}").apply { mkdirs() }
        time = MutableTestBusinessTime(Instant.parse("2026-09-06T10:00:00Z"))
        repo = RoomServiceLoopRepository(db, time, attachmentRoot = root)
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

        val persisted = db.serviceLoopDao().visit(visitId)!!
        assertEquals("WORKING", persisted.state)
        assertEquals("2026-09-09", persisted.actualServiceDate)

        val started = db.serviceLoopDao().visitScheduleEvents(visitId).single { it.eventType == "STARTED" }
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

        val persisted = db.serviceLoopDao().visit(visitId)!!
        assertEquals("2026-09-11", persisted.actualServiceDate)
        val started = db.serviceLoopDao().visitScheduleEvents(visitId).single { it.eventType == "STARTED" }
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

        assertTrue(db.serviceLoopDao().visitScheduleEvents(visitId).none { it.eventType == "STARTED" })
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
