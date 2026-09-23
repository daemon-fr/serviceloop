package com.v16studio.serviceloop

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.domain.ClockBusinessTime
import com.v16studio.serviceloop.domain.ServiceLoopScopeFilter
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class B049DataTransferEndToEndInstrumentedTest {
    @Test fun twoWorkspacesMergeSafeCurrentStateAndImmutableHistoryThenRelayOriginalOrigin() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspaceA = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        val workspaceB = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        val rootA = File(context.cacheDir, "b049-transfer-a-device-${System.nanoTime()}").apply { mkdirs() }
        val rootB = File(context.cacheDir, "b049-transfer-b-device-${System.nanoTime()}").apply { mkdirs() }
        try {
            ServiceLoopDatabase.configureStage4Tracking(workspaceA.openHelper.writableDatabase)
            ServiceLoopDatabase.configureStage4Tracking(workspaceB.openHelper.writableDatabase)
            val a = workspaceA.serviceLoopDao()
            a.insertCustomers(listOf(CustomerEntity("c", "CU-DEVICE", "Device customer")))
            a.insertCustomerContacts(listOf(CustomerContactEntity("cc1", "c", "Office", "PHONE", "+400", 1, 1, position = 0), CustomerContactEntity("cc2", "c", "Dispatch", "EMAIL", "dispatch@device.test", 1, 1, position = 1)))
            a.insertSites(listOf(SiteEntity("s", "c", "ST-DEVICE", "Device site", null, null)))
            a.insertEquipment(listOf(EquipmentEntity("e", "s", "EQ-DEVICE", null, "Boiler", "Maker", "Model", "Serial", null)))
            a.insertReusableTemplate(ReusableTemplateEntity("template", "IT-DEVICE", "Safety", "template-revision", modifiedAtEpochMillis = 1))
            a.insertReusableTemplateRevision(ReusableTemplateRevisionEntity("template-revision", "template", 1, "Safety", 1))
            a.insertReusableTemplateItems(listOf(ReusableTemplateItemEntity("template-item", "template-revision", 1, "Pressure", "NUMBER", "bar", true, null)))
            a.insertPlans(listOf(ServicePlanEntity("plan", "e", "PL-DEVICE", "Annual", 1, "YEARS", "2027-09-24", "ACTIVE", "obligation", reusableTemplateId = "template")))
            a.insertObligations(listOf(ServiceObligationEntity("obligation", "plan", 1, "2027-09-24", 1)))
            a.insertVisits(listOf(WorkingVisitEntity("visit", "V-DEVICE", "c", "s", "2026-09-24", "Device customer", "Device site", null, "COMPLETED", 1)))
            a.insertWorkItems(listOf(WorkItemEntity("work", "visit", "e", "plan", "obligation", null, "Boiler", "EQ-DEVICE", "Annual", "PL-DEVICE", "2027-09-24", 1, "YEARS", true, "PERFORMED", true, confirmedNextDueDate = "2027-09-24")))
            a.insertFinalRecord(FinalRecordEntity("record", "visit", "revision", 2))
            a.insertFinalRevision(FinalRecordRevisionEntity("revision", "record", 1, "V-DEVICE", "2026-09-24", 2, "Device customer", "Device site", null, "Device business", "Device technician", null, null, null, "UTC", null, customerReference = "CU-DEVICE", siteReference = "ST-DEVICE"))
            a.insertFinalWorkItems(listOf(FinalWorkItemEntity("final-work", "revision", 1, "work", "e", "Boiler", "EQ-DEVICE", "Serial", "Maker", "Model", "Serial", "Annual", "plan", "PL-DEVICE", "PERFORMED", "Serviced", null, true, "2026-09-24", "2027-09-24", 1, "YEARS", "obligation", "private", subjectType = "EQUIPMENT")))
            val photoBytes = ByteArrayOutputStream().also { Bitmap.createBitmap(24, 18, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
            val photo = File(rootA, "photo.jpg").apply { writeBytes(photoBytes) }
            val hash = WorkResultPackageCodec.sha256(photoBytes)
            a.insertAttachments(listOf(AttachmentEntity("attachment", "WORK_ITEM", "work", "photo.jpg", hash, "public.jpg", "image/jpeg", true, "PRESENT", photoBytes.size.toLong(), "Public evidence", "PUBLIC")))
            a.insertFinalPhotos(listOf(FinalPhotoEntryEntity("photo", "final-work", 1, "attachment", "photo.jpg", hash, photoBytes.size.toLong(), "image/jpeg", "Public evidence", includedInCustomerReport = true, visibility = "PUBLIC")))
            val sourceId = ServiceLoopPeerTrustStore(workspaceA).localIdentity().technicianId
            ServiceLoopPeerTrustStore(workspaceB).add(sourceId, "Workspace A")

            val selection = ExportCenterSelection(families = ExportPreset.CUSTOMER_DATA.families + ExportPreset.WORK_PERFORMED.families + ExportPreset.IMAGE_ARCHIVE.families)
            val packageBytes = DataTransferExportService(workspaceA, rootA).export(selection)
            assertEquals(sourceId, DataTransferCodec.decode(packageBytes).sourceWorkspaceId)
            val importer = DataTransferImportService(workspaceB, rootB)
            val preview = importer.preview(packageBytes)
            assertTrue(preview.canImport())
            importer.import(preview)

            val b = workspaceB.serviceLoopDao()
            assertEquals(2, b.customerContacts(b.allCustomers().single().id).size)
            assertEquals("IT-DEVICE", b.reusableTemplate(b.allPlans().single().reusableTemplateId!!)?.reference)
            assertEquals("2027-09-24", b.plan(b.allPlans().single().id)?.currentDueDate)
            assertEquals(1, b.obligationCount(b.allPlans().single().id))
            assertEquals(0, b.allVisits().size)
            assertEquals(0, workspaceB.openHelper.writableDatabase.query("SELECT COUNT(*) FROM visit_claims").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(1, b.allTransferredFinalResults().size)
            val evidence = b.allTransferredEvidence().single()
            assertTrue(File(rootB, evidence.relativePath).isFile)
            assertEquals(sourceId, evidence.originWorkspaceId)
            val report = AggregateReportService(workspaceB, RoomServiceLoopRepository(workspaceB, ClockBusinessTime(zoneId = ZoneId.of("UTC")), attachmentRoot = rootB), rootB)
                .reportable(ServiceLoopScopeFilter(customerId = b.allCustomers().single().id)).single()
            assertEquals(1, report.sources.size)

            val retry = importer.preview(packageBytes)
            assertTrue(retry.conflicts.isEmpty())
            importer.import(retry)
            assertEquals(1, b.allTransferredFinalResults().size)
            assertEquals(1, b.allTransferredEvidence().size)
            assertEquals(1, b.obligationCount(b.allPlans().single().id))

            val relay = DataTransferCodec.decode(DataTransferExportService(workspaceB, rootB).export(selection))
            val visit = JSONObject(relay.families.getValue(DataTransferFamily.PERFORMED_WORK).toString(Charsets.UTF_8)).getJSONArray("visits").getJSONObject(0)
            assertEquals("visit", visit.getString("sourceVisitId"))
            assertEquals(sourceId, visit.getString("originWorkspaceId"))
        } finally {
            workspaceA.close(); workspaceB.close(); rootA.deleteRecursively(); rootB.deleteRecursively()
        }
    }
}
