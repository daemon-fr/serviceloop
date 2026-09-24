package com.v16studio.serviceloop

import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Isolated, bounded qualification data; never opens the owner's installed database. */
@RunWith(AndroidJUnit4::class)
class PersistenceWorkloadInstrumentedTest {
    @Test fun customerBranchesAndBookingsRemainReadableAtRepresentativeScale() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).build()
        val root = File(context.cacheDir, "persistence-workload-${System.nanoTime()}").apply { mkdirs() }
        try {
            ServiceLoopDatabase.configureStage4Tracking(database.openHelper.writableDatabase)
            val dao = database.serviceLoopDao()
            val start = SystemClock.elapsedRealtime()
            dao.insertCustomers((1..250).map { CustomerEntity("c$it", "CU-$it", "Customer $it") })
            dao.insertSites((1..250).map { SiteEntity("s$it", "c$it", "ST-$it", "Site $it", null, null) })
            dao.insertEquipment((1..250).map { EquipmentEntity("e$it", "s$it", "EQ-$it", null, "Unit $it", null, null, null, null) })
            dao.insertPlans((1..250).map { ServicePlanEntity("p$it", "e$it", "PL-$it", "Annual service", 1, "YEARS", "2026-10-01", "ACTIVE", null) })
            dao.insertObligations((1..250).map { ServiceObligationEntity("o$it", "p$it", 1, "2026-10-01", 1) })
            (1..250).forEach { dao.updatePlan(requireNotNull(dao.plan("p$it")).copy(currentObligationId = "o$it")) }
            dao.insertVisits((1..120).map { WorkingVisitEntity("v$it", "V-$it", "c$it", "s$it", "2026-10-01",
                "Customer $it", "Site $it", null, "BOOKED", 1) })
            dao.insertWorkItems((1..120).map { WorkItemEntity("w$it", "v$it", "e$it", "p$it", "o$it", null,
                "Unit $it", "EQ-$it", "Annual service", "PL-$it", "2026-10-01", 1, "YEARS", false, null, null) })
            val setupMs = SystemClock.elapsedRealtime() - start
            assertEquals(250, dao.allCustomers().size)
            assertEquals(250, dao.allPlans().size)
            assertEquals(120, dao.allVisits().size)
            val selection = ExportCenterSelection(families = ExportPreset.CUSTOMER_DATA.families)
            val nativeStart = SystemClock.elapsedRealtime()
            val native = DataTransferExportService(database, root).export(selection)
            val nativeMs = SystemClock.elapsedRealtime() - nativeStart
            val readableStart = SystemClock.elapsedRealtime()
            val readable = ExportCenterService(database, root).export(selection)
            val readableMs = SystemClock.elapsedRealtime() - readableStart
            assertTrue(native.isNotEmpty() && readable.isNotEmpty())
            val runtime = Runtime.getRuntime()
            Log.i("ServiceLoopWorkload", "250-branch 120-booking setupMs=$setupMs nativeMs=$nativeMs readableMs=$readableMs " +
                "nativeBytes=${native.size} readableBytes=${readable.size} usedHeapBytes=${runtime.totalMemory() - runtime.freeMemory()} " +
                "maxHeapBytes=${runtime.maxMemory()}")
        } finally {
            database.close()
            root.deleteRecursively()
        }
        Unit
    }
}
