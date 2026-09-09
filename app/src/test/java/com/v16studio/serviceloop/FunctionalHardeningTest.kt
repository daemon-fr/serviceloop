package com.v16studio.serviceloop

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.CustomerEntity
import com.v16studio.serviceloop.data.EquipmentEntity
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.data.ServiceObligationEntity
import com.v16studio.serviceloop.data.ServicePlanEntity
import com.v16studio.serviceloop.data.SiteEntity
import com.v16studio.serviceloop.data.WorkingVisitEntity
import com.v16studio.serviceloop.domain.BusinessTime
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FunctionalHardeningTest {
    private lateinit var database: ServiceLoopDatabase
    private lateinit var attachmentRoot: File
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val time = object : BusinessTime {
        override val zoneId = ZoneId.of("Europe/Bucharest")
        override fun instant() = Instant.parse("2026-09-09T09:00:00Z")
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        attachmentRoot = File(context.cacheDir, "functional-hardening-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        database.close()
        attachmentRoot.deleteRecursively()
    }

    @Test
    fun largerDatasetKeepsPrincipalRegistersDueWorkVisitsHomeAndSearchComplete() = runTest {
        val branchCount = 250
        val bookedVisitCount = 120
        val customers = (1..branchCount).map { index ->
            CustomerEntity("customer-$index", "CU-${index.toString().padStart(4, '0')}", "Scale Customer ${index.toString().padStart(4, '0')}")
        }
        val sites = (1..branchCount).map { index ->
            SiteEntity("site-$index", "customer-$index", "ST-${index.toString().padStart(4, '0')}", "Scale Site ${index.toString().padStart(4, '0')}", "$index Test Road", null)
        }
        val equipment = (1..branchCount).map { index ->
            EquipmentEntity("equipment-$index", "site-$index", "EQ-${index.toString().padStart(4, '0')}", "ASSET-$index", "Scale Equipment ${index.toString().padStart(4, '0')}", "Maker", "Model", "SER-$index", null)
        }
        val plans = (1..branchCount).map { index ->
            ServicePlanEntity("plan-$index", "equipment-$index", "PL-${index.toString().padStart(4, '0')}", "Scale Service ${index.toString().padStart(4, '0')}", 1, "YEARS", if (index % 2 == 0) "2026-09-01" else "2026-09-20", "ACTIVE", "obligation-$index")
        }
        val obligations = plans.mapIndexed { index, plan ->
            ServiceObligationEntity("obligation-${index + 1}", plan.id, 1, plan.currentDueDate, index.toLong())
        }
        val visits = (1..bookedVisitCount).map { index ->
            WorkingVisitEntity("visit-$index", "V-${index.toString().padStart(4, '0')}", "customer-$index", "site-$index", "2026-10-${((index - 1) % 28 + 1).toString().padStart(2, '0')}", customers[index - 1].name, sites[index - 1].name, sites[index - 1].address, "BOOKED", index.toLong())
        }
        database.withTransaction {
            val dao = database.serviceLoopDao()
            dao.insertCustomers(customers)
            dao.insertSites(sites)
            dao.insertEquipment(equipment)
            dao.insertPlans(plans)
            dao.insertObligations(obligations)
            dao.insertVisits(visits)
        }

        val repository = RoomServiceLoopRepository(database, time, attachmentRoot = attachmentRoot)
        assertEquals(branchCount, repository.customerList().size)
        assertEquals(branchCount, repository.siteList().size)
        assertEquals(branchCount, repository.equipmentList().size)
        assertEquals(branchCount, repository.dueServices().size)
        assertEquals(bookedVisitCount, repository.visits().size)
        assertEquals(bookedVisitCount, repository.home().bookedVisitCount)
        assertEquals(branchCount / 2, repository.home().overdueCount)
        val target = repository.search("Scale Equipment 0249")
        assertEquals(1, target.size)
        assertEquals("equipment-249", target.single().id)
        assertTrue(target.single().title.contains("0249"))
    }

    @Test
    fun productionUiContainsNoStaleDispatchStatusOrDeadFoundationRoute() {
        val uiSources = File("src/main/java/com/v16studio/serviceloop/ui")
            .walkTopDown()
            .filter { it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        assertFalse(uiSources.contains("Coordinator tools · Experimental"))
        assertFalse(uiSources.contains("Experimental coordinator conveniences"))
        assertFalse(uiSources.contains("composable(\"scope/{title}\")"))
        assertFalse(uiSources.contains("without claiming the later workflow is complete"))
    }
}
