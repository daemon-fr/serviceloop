package com.v16studio.serviceloop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.domain.ServiceLoopScopeFilter
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class B049WorkResultImportTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: ServiceLoopDatabase
    private lateinit var root: File
    private val issuer = TechnicianIdCodec.generate()
    private val exporter = TechnicianIdCodec.generate()
    private val material = "a".repeat(64)

    @Before fun setup() = kotlinx.coroutines.runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        ServiceLoopDatabase.configureStage4Tracking(database.openHelper.writableDatabase)
        root = File(context.cacheDir, "b049-result-${System.nanoTime()}").apply { mkdirs() }
        val dao = database.serviceLoopDao(); val dispatch = database.dispatchDao()
        dao.insertCustomers(listOf(CustomerEntity("c", "CU-1", "Customer")))
        dao.insertSites(listOf(SiteEntity("s", "c", "ST-1", "Site", null, null)))
        dao.insertEquipment(listOf(EquipmentEntity("e", "s", "EQ-1", null, "Equipment", null, null, null, null)))
        dao.insertPlans(listOf(ServicePlanEntity("plan", "e", "PL-1", "Annual", 1, "YEARS", "2026-09-01", "ACTIVE", "obligation")))
        dao.insertObligations(listOf(ServiceObligationEntity("obligation", "plan", 1, "2026-09-01", 1)))
        dao.insertVisits(listOf(WorkingVisitEntity("v", "V-1", "c", "s", "2026-09-23", "Customer", "Site", null, "BOOKED", 1)))
        dao.insertWorkItems(listOf(
            WorkItemEntity("w1", "v", "e", "plan", "obligation", null, "Equipment", "EQ-1", "Annual", "PL-1", "2026-09-01", 1, "YEARS", false, null, null),
            WorkItemEntity("w2", "v", null, null, null, null, null, null, "Inspect site", null, null, null, null, false, null, null, subjectType = "SITE"),
        ))
        dispatch.insertTechnicianIdentity(TechnicianIdentityEntity("primary", issuer, "Coordinator", 1, 1))
        dispatch.insertTrustedServiceLoopId(TrustedServiceLoopIdEntity(exporter, "Field technician", 1, 1))
        dispatch.insertTechnician(DispatchTechnicianEntity(exporter, "Field technician", 1, 1))
        dispatch.insertOutboxVisit(DispatchOutboxVisitEntity("dispatch-v", null, "s", "2026-09-23", null, "Europe/Bucharest", null, 1, material, 1, 1, 1, localVisitId = "v"))
        dispatch.insertOutboxItem(DispatchOutboxItemEntity("dispatch-1", "dispatch-v", 1, "e", "Annual", "PL-1", "2026-09-01", localWorkItemId = "w1"))
        dispatch.insertOutboxItem(DispatchOutboxItemEntity("dispatch-2", "dispatch-v", 2, null, "Inspect site", null, null, subjectType = "SITE", localWorkItemId = "w2"))
        dispatch.insertOutboxItemAssignees(listOf(DispatchOutboxItemAssigneeEntity("dispatch-1", exporter), DispatchOutboxItemAssigneeEntity("dispatch-2", exporter)))
    }

    @After fun teardown() { database.close(); root.deleteRecursively() }

    private fun result(item: Int, materialHash: String = material): WorkResultPackageCodec.Result {
        val first = item == 1
        val json = JSONObject().put("resultId", "result-$item").put("sourceFinalRevisionId", "revision-$item")
            .put("dispatchVisitId", "dispatch-v").put("dispatchItemId", "dispatch-$item")
            .put("assignmentIssuerId", issuer).put("assignmentGeneration", 1).put("assignmentMaterialHash", materialHash)
            .put("technicianId", exporter).put("technicianName", "Field technician").put("serviceDate", "2026-09-23")
            .put("outcome", "DONE").put("customerSnapshot", JSONObject().put("name", "Customer"))
            .put("siteSnapshot", JSONObject().put("name", "Site"))
            .put("subjectSnapshot", JSONObject().put("type", if(first) "EQUIPMENT" else "SITE"))
            .put("workSnapshot", JSONObject().put("serviceName", if(first) "Annual" else "Inspect site")
                .put("planReference", if(first) "PL-1" else JSONObject.NULL).put("fulfilledObligation", first).put("publicWork", "Completed"))
            .put("checklist", JSONArray()).put("findings", JSONArray()).put("parts", JSONArray()).put("followUps", JSONArray())
            .put("recurrence", JSONObject().put("fulfilledObligation", first).put("oldDueDate", if(first) "2026-09-01" else JSONObject.NULL)
                .put("nextDueDate", if(first) "2027-09-01" else JSONObject.NULL))
        return WorkResultPackageCodec.Result(json, emptyList())
    }

    private fun packageBytes(id: String, vararg results: WorkResultPackageCodec.Result) = WorkResultPackageCodec.encode(
        WorkResultPackageCodec.Package(id, exporter, issuer, "2026-09-23T10:00:00Z", results.toList()))

    @Test fun partialThenCompleteAndRetriesPreserveRemoteTruthAndAdvanceOnce() = runTest {
        val service = WorkResultImportService(database, root)
        val first = packageBytes("package-1", result(1))
        service.import(first)
        assertEquals(1, database.serviceLoopDao().appliedWorkResultReceipts("dispatch-v").size)
        assertEquals("DISPATCHED", database.dispatchDao().outboxVisit("dispatch-v")!!.outboxStatus.name)
        assertEquals("2027-09-01", database.serviceLoopDao().plan("plan")!!.currentDueDate)
        assertEquals("BOOKED", database.serviceLoopDao().visit("v")!!.state)
        assertEquals("ALREADY_RECEIVED", service.import(first).items.single().status)
        assertEquals("ALREADY_RECEIVED", service.import(packageBytes("package-retry", result(1))).items.single().status)
        assertEquals(2, database.serviceLoopDao().obligationCount("plan"))
        val correction = result(1).let { it.copy(value = JSONObject(it.value.toString()).put("sourceFinalRevisionId", "revision-1-corrected").put("recordedAt", "2026-09-24T10:00:00Z").put("publicNote", "Corrected wording")) }
        service.import(packageBytes("package-correction", correction))
        assertEquals("APPLIED", database.serviceLoopDao().workResultReceipt("result-1", "revision-1-corrected")!!.status)
        assertEquals(2, database.serviceLoopDao().obligationCount("plan"))
        service.import(packageBytes("package-2", result(2)))
        assertEquals("CONCLUDED", database.dispatchDao().outboxVisit("dispatch-v")!!.outboxStatus.name)
        assertEquals("COMPLETED", database.serviceLoopDao().visit("v")!!.state)
        assertEquals(3, database.serviceLoopDao().reportableRemoteFinalResults().size)
        assertEquals(2, database.serviceLoopDao().obligationCount("plan"))
        assertEquals("ALREADY_RECEIVED", service.import(packageBytes("package-2-retry", result(2))).items.single().status)
        assertEquals("COMPLETED", database.serviceLoopDao().visit("v")!!.state)
        assertEquals(2, database.serviceLoopDao().obligationCount("plan"))
    }

    @Test fun staleResultIsRetainedWithoutRecurrenceAndWrongTargetIsRejected() = runTest {
        val service = WorkResultImportService(database, root)
        val stale = packageBytes("stale", result(1, "b".repeat(64)))
        assertEquals("STALE", service.preview(stale).items.single().status)
        service.import(stale)
        assertEquals("2026-09-01", database.serviceLoopDao().plan("plan")!!.currentDueDate)
        assertEquals(1, database.serviceLoopDao().obligationCount("plan"))
        assertEquals(0, database.serviceLoopDao().reportableRemoteFinalResults().size)
        assertEquals("DISPATCHED", database.dispatchDao().outboxVisit("dispatch-v")!!.outboxStatus.name)
        service.import(packageBytes("second", result(2)))
        assertEquals("BOOKED", database.serviceLoopDao().visit("v")!!.state)
        assertEquals("DISPATCHED", database.dispatchDao().outboxVisit("dispatch-v")!!.outboxStatus.name)
        val wrongIssuer = TechnicianIdCodec.generate()
        val wrong = WorkResultPackageCodec.encode(WorkResultPackageCodec.Package("wrong", exporter, wrongIssuer, "2026-09-23T10:00:00Z", listOf(result(2).let { it.copy(value = JSONObject(it.value.toString()).put("assignmentIssuerId", wrongIssuer)) })))
        assertNotNull(wrong)
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { service.import(wrong) } }
    }

    @Test fun untrustedResultLeavesNoReceiptOrRemoteFinalTruth() = runTest {
        ServiceLoopPeerTrustStore(database).remove(exporter)
        val bytes = packageBytes("untrusted", result(2))
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { WorkResultImportService(database, root).import(bytes) } }
        assertNull(database.serviceLoopDao().workResultReceipt("result-2", "revision-2"))
        assertNull(database.serviceLoopDao().remoteFinalResult("result-2", "revision-2"))
        assertEquals("DISPATCHED", database.dispatchDao().outboxVisit("dispatch-v")!!.outboxStatus.name)
    }

    @Test fun performedExportExcludesBookedAndWorkingAndRemoteRevisionToggleIsEffective() = runTest {
        val dao = database.serviceLoopDao()
        dao.insertVisits(listOf(
            WorkingVisitEntity("booked", "V-B", "c", "s", "2026-09-23", "Customer", "Site", null, "BOOKED", 1),
            WorkingVisitEntity("working", "V-W", "c", "s", "2026-09-23", "Customer", "Site", null, "WORKING", 1),
            WorkingVisitEntity("canceled", "V-C", "c", "s", "2026-09-23", "Customer", "Site", null, "CANCELED", 1),
            WorkingVisitEntity("local", "V-L", "c", "s", "2026-09-23", "Customer", "Site", null, "COMPLETED", 1),
        ))
        dao.insertWorkItems(listOf(WorkItemEntity("local-work", "local", null, null, null, null, null, null, "Local inspection", null, null, null, null, false, null, null, subjectType = "SITE")))
        dao.insertFinalRecord(FinalRecordEntity("local-record", "local", "local-revision", 2))
        dao.insertFinalRevision(FinalRecordRevisionEntity("local-revision", "local-record", 1, "V-L", "2026-09-23", 2, "Customer", "Site", null, "Business", "Local technician", null, null, null, "UTC", null))
        dao.insertFinalWorkItems(listOf(FinalWorkItemEntity("local-final-work", "local-revision", 1, "local-work", null, null, null, null, null, null, null, "Local inspection", null, null, "DONE", "Inspected", null, false, null, null, null, null, null, null, subjectType = "SITE")))
        val importer = WorkResultImportService(database, root)
        importer.import(packageBytes("first", result(1)))
        importer.import(packageBytes("second", result(2)))
        val corrected = result(1).let { it.copy(value = JSONObject(it.value.toString())
            .put("sourceFinalRevisionId", "revision-1-corrected").put("recordedAt", "2026-09-24T10:00:00Z")) }
        importer.import(packageBytes("corrected", corrected))
        val exporter = ExportCenterService(database, root)
        suspend fun csv(previous: Boolean, name: String): String {
            val bytes = exporter.export(ExportCenterSelection(families = ExportPreset.WORK_PERFORMED.families, includePreviousRevisions = previous))
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == name) return zip.readBytes().toString(Charsets.UTF_8)
                }
            }
            error("Missing $name")
        }
        val visits = csv(false, "visits.csv")
        assertEquals(true, visits.contains("\"v\",\"V-1\""))
        assertEquals(true, visits.contains("\"local\",\"V-L\""))
        assertEquals(false, visits.contains("\"booked\""))
        assertEquals(false, visits.contains("\"working\""))
        assertEquals(false, visits.contains("\"canceled\""))
        val current = csv(false, "service_records.csv")
        assertEquals(true, current.contains("\"local-revision\""))
        assertEquals(false, current.contains("\"revision-1\""))
        assertEquals(true, current.contains("\"revision-1-corrected\""))
        val history = csv(true, "service_records.csv")
        assertEquals(true, history.contains("\"revision-1\""))
        assertEquals(true, history.contains("\"revision-1-corrected\""))
    }

    @Test fun aggregateSelectsOneVisitForTwoRemoteServicesAndFreezesBothRevisions() = runTest {
        val dao = database.serviceLoopDao()
        dao.upsertBusinessProfile(BusinessProfileEntity("primary", "Coordinator Business", "Coordinator", null, null, null, "UTC", 1))
        val importer = WorkResultImportService(database, root)
        importer.import(packageBytes("one", result(1)))
        importer.import(packageBytes("two", result(2)))
        val time = object : BusinessTime {
            override val zoneId = ZoneId.of("UTC")
            override fun instant(): Instant = Instant.parse("2026-09-23T12:00:00Z")
        }
        val repository = RoomServiceLoopRepository(database, time, attachmentRoot = root)
        var renderedLines = 0
        val service = AggregateReportService(database, repository, root, AggregateReportWriter { models, _, _, _, target, _ ->
            assertEquals(1, models.size)
            renderedLines = models.single().lines.size
            target.writeBytes("%PDF-fixture".toByteArray())
            1
        })
        val scope = ServiceLoopScopeFilter(customerId = "c")
        val candidate = service.reportable(scope).single()
        assertEquals("VISIT:v", candidate.key)
        assertEquals(2, candidate.sources.size)
        val generated = service.generate(scope, listOf(candidate.key))
        assertEquals(2, renderedLines)
        assertEquals(setOf("revision-1", "revision-2"), dao.aggregateSources(generated.reportId).map { it.sourceFinalRevisionId }.toSet())
        val corrected = result(1).let { it.copy(value = JSONObject(it.value.toString())
            .put("sourceFinalRevisionId", "revision-1-corrected").put("recordedAt", "2026-09-24T10:00:00Z")) }
        importer.import(packageBytes("corrected", corrected))
        assertEquals(setOf("revision-1-corrected", "revision-2"), service.reportable(scope).single().sources.map { it.revisionId }.toSet())
        assertEquals(setOf("revision-1", "revision-2"), dao.aggregateSources(generated.reportId).map { it.sourceFinalRevisionId }.toSet())
    }
}
