package com.v16studio.serviceloop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class B049WorkResultExportTest {
    private lateinit var database: ServiceLoopDatabase
    private lateinit var root: File
    private val technician = TechnicianIdCodec.generate()
    private val issuer = TechnicianIdCodec.generate()

    @Before fun setUp() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        ServiceLoopDatabase.configureStage4Tracking(database.openHelper.writableDatabase)
        root = File(context.cacheDir, "b049-result-export-${System.nanoTime()}").apply { mkdirs() }
        val dao = database.serviceLoopDao()
        dao.insertCustomers(listOf(CustomerEntity("c", "CU-1", "Customer")))
        dao.insertSites(listOf(SiteEntity("s", "c", "ST-1", "Site", null, null)))
        dao.insertVisits(listOf(WorkingVisitEntity("v", "V-1", "c", "s", "2026-09-23", "Customer", "Site", null, "FINALIZED", 1)))
        dao.insertWorkItems(listOf(WorkItemEntity("w", "v", null, null, null, null, null, null, "Site inspection", null, null, null, null, false, null, null, subjectType = "SITE")))
        dao.insertFinalRecord(FinalRecordEntity("record", "v", "revision", 2))
        dao.insertFinalRevision(FinalRecordRevisionEntity("revision", "record", 1, "V-1", "2026-09-23", 2, "Customer", "Site", null, "Business", "Tech", null, null, null, "Europe/Bucharest", null, "CU-1", "ST-1"))
        dao.insertFinalWorkItems(listOf(FinalWorkItemEntity("final-work", "revision", 1, "w", null, null, null, null, null, null, null, "Site inspection", null, null, "PERFORMED", "Inspected site", null, false, null, null, null, null, null, null, subjectType = "SITE")))
        val dispatch = database.dispatchDao()
        dispatch.insertTechnicianIdentity(TechnicianIdentityEntity("primary", technician, "Tech", 1, 1))
        dispatch.insertFinalDispatchVisit(FinalDispatchVisitEntity("revision", "dispatch-v", 1, null, "Coordinator", technician, "Tech", "a".repeat(64), issuer))
        dispatch.insertFinalDispatchItems(listOf(FinalDispatchItemEntity("final-work", "dispatch-i", "[]", "ASSIGNED", "TECHNICIAN")))
    }

    @After fun tearDown() { database.close(); root.deleteRecursively() }

    @Test fun issuerTrustAndDocumentingIdentityGateExportAndRevisionIdentityIsStable() = runTest {
        val exchange = WorkResultExchangeService(database, root)
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { exchange.exportFinalRevisions(listOf("revision")) } }
        ServiceLoopPeerTrustStore(database).add(issuer, "Coordinator")
        val first = WorkResultPackageCodec.decode(exchange.exportFinalRevisions(listOf("revision")))
        val second = WorkResultPackageCodec.decode(exchange.exportFinalRevisions(listOf("revision")))
        assertEquals(issuer, first.targetIssuerId)
        assertEquals(technician, first.exporterId)
        assertEquals(first.results.single().value.getString("resultId"), second.results.single().value.getString("resultId"))
        assertEquals("revision", first.results.single().value.getString("sourceFinalRevisionId"))
        database.openHelper.writableDatabase.execSQL("UPDATE final_dispatch_visits SET documentingTechnicianId=?, documentingTechnicianName=? WHERE revisionId='revision'", arrayOf(issuer, "Wrong author"))
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { exchange.exportFinalRevisions(listOf("revision")) } }
    }
}
