package com.v16studio.serviceloop

import androidx.room.Room
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.domain.ServiceLoopScopeFilter
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class B049WorkResultEndToEndInstrumentedTest {
    @Test fun exporterProducedV2PhotoResultIsAppliedAndReplayedWithoutEffects() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).build()
        val receiver = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).build()
        val sourceRoot = File(context.cacheDir, "v2-source-${System.nanoTime()}").apply { mkdirs() }
        val receiverRoot = File(context.cacheDir, "v2-receiver-${System.nanoTime()}").apply { mkdirs() }
        try {
            ServiceLoopDatabase.configureStage4Tracking(source.openHelper.writableDatabase)
            ServiceLoopDatabase.configureStage4Tracking(receiver.openHelper.writableDatabase)
            val technician = TechnicianIdCodec.generate()
            val issuer = TechnicianIdCodec.generate()
            val material = "a".repeat(64)
            val a = source.serviceLoopDao()
            a.insertCustomers(listOf(CustomerEntity("c", "CU-1", "Customer")))
            a.insertSites(listOf(SiteEntity("s", "c", "ST-1", "Site", null, null)))
            a.insertVisits(listOf(WorkingVisitEntity("source-v", "V-1", "c", "s", "2026-09-23", "Customer", "Site", null, "FINALIZED", 1)))
            a.insertWorkItems(listOf(WorkItemEntity("source-w", "source-v", null, null, null, null, null, null, "Inspect site", null, null, null, null, false, null, null, subjectType = "SITE")))
            a.insertFinalRecord(FinalRecordEntity("source-record", "source-v", "source-r", 2))
            a.insertFinalRevision(FinalRecordRevisionEntity("source-r", "source-record", 1, "V-1", "2026-09-23", 2,
                "Customer", "Site", null, "Business", "Field technician", null, null, null, "Europe/Bucharest", null))
            a.insertFinalWorkItems(listOf(FinalWorkItemEntity("source-final", "source-r", 1, "source-w", null, null, null, null,
                null, null, null, "Inspect site", null, null, "PERFORMED", "Inspected", null, false, null, null,
                null, null, null, null, subjectType = "SITE")))
            val bitmap = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888)
            val original = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it); bitmap.recycle() }.toByteArray()
            File(sourceRoot, "attachments").mkdirs()
            File(sourceRoot, "attachments/original.jpg").writeBytes(original)
            a.insertFinalPhotos(listOf(FinalPhotoEntryEntity("source-final-photo", "source-final", 1, "photo-1",
                "attachments/original.jpg", WorkResultPackageCodec.sha256(original), original.size.toLong(), "image/jpeg", "Public photo")))
            val sourceDispatch = source.dispatchDao()
            sourceDispatch.insertTechnicianIdentity(TechnicianIdentityEntity("primary", technician, "Field technician", 1, 1))
            sourceDispatch.insertFinalDispatchVisit(FinalDispatchVisitEntity("source-r", "dispatch-v", 1, null, "Coordinator",
                technician, "Field technician", material, issuer))
            sourceDispatch.insertFinalDispatchItems(listOf(FinalDispatchItemEntity("source-final", "dispatch-1", "[]", "ASSIGNED", "TECHNICIAN")))
            ServiceLoopPeerTrustStore(source).add(issuer, "Coordinator")
            val bytes = WorkResultExchangeService(source, sourceRoot).exportFinalRevisions(listOf("source-r"))
            assertEquals("source-w", WorkResultPackageCodec.decode(bytes).results.single().value.getString("sourceWorkItemId"))
            val b = receiver.serviceLoopDao()
            b.insertCustomers(listOf(CustomerEntity("c", "CU-1", "Customer")))
            b.insertSites(listOf(SiteEntity("s", "c", "ST-1", "Site", null, null)))
            b.insertVisits(listOf(WorkingVisitEntity("v", "V-1", "c", "s", "2026-09-23", "Customer", "Site", null, "BOOKED", 1)))
            b.insertWorkItems(listOf(WorkItemEntity("w", "v", null, null, null, null, null, null, "Inspect site", null,
                null, null, null, false, null, null, subjectType = "SITE")))
            val receiverDispatch = receiver.dispatchDao()
            receiverDispatch.insertTechnicianIdentity(TechnicianIdentityEntity("primary", issuer, "Coordinator", 1, 1))
            receiverDispatch.insertTrustedServiceLoopId(TrustedServiceLoopIdEntity(technician, "Field technician", 1, 1))
            receiverDispatch.insertTechnician(DispatchTechnicianEntity(technician, "Field technician", 1, 1))
            receiverDispatch.insertOutboxVisit(DispatchOutboxVisitEntity("dispatch-v", null, "s", "2026-09-23", null,
                "Europe/Bucharest", null, 1, material, 1, 1, 1, localVisitId = "v"))
            receiverDispatch.insertOutboxItem(DispatchOutboxItemEntity("dispatch-1", "dispatch-v", 1, null, "Inspect site", null,
                null, subjectType = "SITE", localWorkItemId = "w"))
            receiverDispatch.insertOutboxItemAssignees(listOf(DispatchOutboxItemAssigneeEntity("dispatch-1", technician)))
            val importer = WorkResultImportService(receiver, receiverRoot)
            val applied = importer.import(bytes).items.single()
            assertEquals("APPLIED", applied.committedStatus)
            assertEquals("COMPLETED", b.visit("v")!!.state)
            val accepted = b.appliedRemoteFinalResultsIncludingVoids().single()
            assertEquals("source-v", FinalSourceSnapshot.record(accepted.sourcePayloadJson!!, "WORK_RESULT").getString("sourceVisitId"))
            val photo = b.remoteResultPhotos(accepted.id).single()
            val retained = File(receiverRoot, photo.relativePath)
            assertEquals(photo.sha256, WorkResultPackageCodec.sha256(retained.readBytes()))
            assertEquals("ALREADY_RECEIVED", importer.import(bytes).items.single().status)
            assertEquals(1, b.appliedWorkResultReceipts("dispatch-v").size)
        } finally { source.close(); receiver.close(); sourceRoot.deleteRecursively(); receiverRoot.deleteRecursively() }
    }

    @Test fun partialThenCompleteResultsConvergeOnOneCanonicalReportableVisit() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).build()
        val root = File(context.cacheDir, "b049-result-device-${System.nanoTime()}").apply { mkdirs() }
        try {
            ServiceLoopDatabase.configureStage4Tracking(database.openHelper.writableDatabase)
            val dao = database.serviceLoopDao()
            val dispatch = database.dispatchDao()
            val issuer = TechnicianIdCodec.generate()
            val exporter = TechnicianIdCodec.generate()
            val material = "a".repeat(64)
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
            fun result(item: Int): WorkResultPackageCodec.Result {
                val first = item == 1
                val json = JSONObject().put("resultId", "result-$item").put("sourceFinalRevisionId", "revision-$item")
                    .put("dispatchVisitId", "dispatch-v").put("dispatchItemId", "dispatch-$item")
                    .put("assignmentIssuerId", issuer).put("assignmentGeneration", 1).put("assignmentMaterialHash", material)
                    .put("technicianId", exporter).put("technicianName", "Field technician").put("serviceDate", "2026-09-23")
                    .put("outcome", "PERFORMED").put("customerSnapshot", JSONObject().put("name", "Customer"))
                    .put("siteSnapshot", JSONObject().put("name", "Site"))
                    .put("subjectSnapshot", JSONObject().put("type", if (first) "EQUIPMENT" else "SITE"))
                    .put("workSnapshot", JSONObject().put("serviceName", if (first) "Annual" else "Inspect site")
                        .put("planReference", if (first) "PL-1" else JSONObject.NULL).put("fulfilledObligation", first).put("publicWork", "Completed"))
                    .put("checklist", JSONArray()).put("findings", JSONArray()).put("parts", JSONArray()).put("followUps", JSONArray())
                    .put("recurrence", JSONObject().put("fulfilledObligation", first).put("oldDueDate", if (first) "2026-09-01" else JSONObject.NULL)
                        .put("nextDueDate", if (first) "2027-09-01" else JSONObject.NULL))
                return WorkResultPackageCodec.Result(json, emptyList())
            }
            fun bytes(item: Int) = WorkResultPackageCodec.encode(WorkResultPackageCodec.Package("package-$item", exporter, issuer, "2026-09-23T10:00:00Z", listOf(result(item))))
            val importer = WorkResultImportService(database, root)
            importer.import(bytes(1))
            assertEquals("BOOKED", dao.visit("v")!!.state)
            assertEquals(DispatchOutboxStatus.DISPATCHED, dispatch.outboxVisit("dispatch-v")!!.outboxStatus)
            importer.import(bytes(2))
            assertEquals("COMPLETED", dao.visit("v")!!.state)
            assertEquals(DispatchOutboxStatus.CONCLUDED, dispatch.outboxVisit("dispatch-v")!!.outboxStatus)
            assertEquals(2, dao.obligationCount("plan"))
            importer.import(bytes(2))
            assertEquals(2, dao.obligationCount("plan"))
            val time = object : BusinessTime {
                override val zoneId = ZoneId.of("UTC")
                override fun instant(): Instant = Instant.parse("2026-09-23T12:00:00Z")
            }
            val reports = AggregateReportService(database, RoomServiceLoopRepository(database, time, attachmentRoot = root), root)
            val visits = reports.reportable(ServiceLoopScopeFilter(customerId = "c"))
            assertEquals(1, visits.size)
            assertEquals(2, visits.single().sources.size)
        } finally { database.close(); root.deleteRecursively() }
    }
}
