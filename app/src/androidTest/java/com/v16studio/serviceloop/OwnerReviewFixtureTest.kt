package com.v16studio.serviceloop

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.domain.ClockBusinessTime
import com.v16studio.serviceloop.domain.ResponseDisposition
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnerReviewFixtureTest {
    @Test
    fun debugFixtureProvidesOneFreshEditableInspectionForOwnerReview() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            FixtureSeederFactory.create(database).seedIfNeeded()
            FixtureSeederFactory.create(database).seedIfNeeded()

            val dao = database.serviceLoopDao()
            val visit = dao.visit(FixtureIds.OWNER_REVIEW_VISIT)
            val work = dao.workItem(FixtureIds.OWNER_REVIEW_WORK)
            assertNotNull(visit)
            assertNotNull(work)
            assertEquals("WORKING", visit!!.state)
            assertEquals("V-003", visit.reference)
            assertEquals("plan-005", work!!.servicePlanId)
            assertEquals("obl-005", work.capturedObligationId)
            assertNull(dao.finalRecordForVisit(FixtureIds.OWNER_REVIEW_VISIT))
            assertEquals(1, dao.visits().count { it.id == FixtureIds.OWNER_REVIEW_VISIT })

            val repository = RoomServiceLoopRepository(
                database,
                ClockBusinessTime(zoneId = ZoneId.of("Europe/Bucharest")),
            )
            val inspection = repository.inspection(FixtureIds.OWNER_REVIEW_WORK)
            assertNotNull(inspection)
            assertEquals("Electrical inspection", inspection!!.serviceName)
            assertTrue(inspection.questions.any { it.label == "Guard fixing" && it.disposition == ResponseDisposition.NOT_CHECKED })
            assertTrue(inspection.questions.any { it.label == "Belt condition" && it.disposition == ResponseDisposition.NOT_CHECKED })
        } finally {
            database.close()
        }
    }
}
