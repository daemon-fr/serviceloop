package com.v16studio.v16service

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.v16service.data.RoomV16ServiceRepository
import com.v16studio.v16service.data.V16ServiceDatabase
import com.v16studio.v16service.domain.ClockBusinessTime
import com.v16studio.v16service.domain.ResponseDisposition
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
        val database = Room.inMemoryDatabaseBuilder(context, V16ServiceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            FixtureSeederFactory.createFixtureSeeder(database).seedIfNeeded()
            FixtureSeederFactory.createFixtureSeeder(database).seedIfNeeded()

            val dao = database.v16ServiceDao()
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

            val repository = RoomV16ServiceRepository(
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
