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
            a.insertCustomers(listOf(CustomerEntity("c", "CU-DEVICE", "Device customer", "Ana Client", "+40 700 111 222", "ana@device.test", "private customer note")))
            a.insertCustomerContacts(listOf(CustomerContactEntity("cc1", "c", "Office", "PHONE", "+400", 1, 1, position = 1), CustomerContactEntity("cc2", "c", "Dispatch", "EMAIL", "dispatch@device.test", 1, 1, position = 2)))
            a.insertSites(listOf(SiteEntity("s", "c", "ST-DEVICE", "Device site", "1 Device Road", "private gate code", "Site contact", "+40 700 333 444", "site@device.test")))
            a.insertEquipment(listOf(EquipmentEntity("e", "s", "EQ-DEVICE", "ASSET-17", "Boiler", "Maker", "Model", "Serial", "private equipment notes")))
            a.insertReusableTemplate(ReusableTemplateEntity("template", "IT-DEVICE", "Safety", "template-revision", modifiedAtEpochMillis = 1))
            a.insertReusableTemplateRevision(ReusableTemplateRevisionEntity("template-revision", "template", 1, "Safety", 1))
            a.insertReusableTemplateItems(listOf(ReusableTemplateItemEntity("template-item", "template-revision", 1, "Pressure", "NUMBER", "bar", true, null)))
            a.insertPlans(listOf(ServicePlanEntity("plan", "e", "PL-DEVICE", "Annual", 1, "YEARS", "2027-09-24", "ACTIVE", "obligation", reusableTemplateId = "template")))
            a.insertObligations(listOf(ServiceObligationEntity("obligation", "plan", 1, "2027-09-24", 1)))
            assertEquals(1, a.updateTemplateState("template", "ACTIVE", "DISABLED", 2))
            a.insertCustomers(listOf(
                CustomerEntity("inactive-c", "CU-INACTIVE", "Historical customer", state = "ARCHIVED"),
                CustomerEntity("unrelated-c", "CU-UNRELATED", "Unrelated inactive", state = "ARCHIVED"),
            ))
            a.insertSites(listOf(
                SiteEntity("inactive-s", "inactive-c", "ST-INACTIVE", "Historical site", null, null, state = "ARCHIVED"),
                SiteEntity("unrelated-s", "unrelated-c", "ST-UNRELATED", "Unrelated inactive", null, null, state = "ARCHIVED"),
            ))
            a.insertEquipment(listOf(
                EquipmentEntity("inactive-e", "inactive-s", "EQ-INACTIVE", null, "Historical equipment", null, null, null, null, "RETIRED"),
                EquipmentEntity("unrelated-e", "unrelated-s", "EQ-UNRELATED", null, "Unrelated inactive", null, null, null, null, "RETIRED"),
            ))
            a.insertContactNote(ContactNoteEntity("inactive-note", "CN-INACTIVE", "inactive-c", "inactive-s", "inactive-e", "PHONE", 1_790_208_000_000, "Historical note", "private note", 1))
            a.insertVisits(listOf(WorkingVisitEntity("visit", "V-DEVICE", "c", "s", "2026-09-24", "Device customer", "Device site", null, "COMPLETED", 1)))
            a.insertWorkItems(listOf(WorkItemEntity("work", "visit", "e", "plan", "obligation", null, "Boiler", "EQ-DEVICE", "Annual", "PL-DEVICE", "2027-09-24", 1, "YEARS", true, "PERFORMED", true, confirmedNextDueDate = "2027-09-24")))
            a.insertFinalRecord(FinalRecordEntity("record", "visit", "revision", 2))
            a.insertFinalRevision(FinalRecordRevisionEntity("revision", "record", 1, "V-DEVICE", "2026-09-24", 2, "Device customer", "Device site", null, "Device business", "Device technician", null, null, null, "UTC", null, customerReference = "CU-DEVICE", siteReference = "ST-DEVICE"))
            a.insertFinalWorkItems(listOf(FinalWorkItemEntity("final-work", "revision", 1, "work", "e", "Boiler", "EQ-DEVICE", "Serial", "Maker", "Model", "Serial", "Annual", "plan", "PL-DEVICE", "PERFORMED", "Serviced", null, true, "2026-09-24", "2027-09-24", 1, "YEARS", "obligation", "private", subjectType = "EQUIPMENT")))
            val photoBytes = ByteArrayOutputStream().also { Bitmap.createBitmap(24, 18, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
            val photo = File(rootA, "attachments/photo.jpg").apply { parentFile!!.mkdirs(); writeBytes(photoBytes) }
            val hash = WorkResultPackageCodec.sha256(photoBytes)
            a.insertAttachments(listOf(AttachmentEntity("attachment", "WORK_ITEM", "work", "attachments/photo.jpg", hash, "public.jpg", "image/jpeg", true, "PRESENT", photoBytes.size.toLong(), "Public evidence", "PUBLIC")))
            a.insertFinalPhotos(listOf(FinalPhotoEntryEntity("photo", "final-work", 1, "attachment", "attachments/photo.jpg", hash, photoBytes.size.toLong(), "image/jpeg", "Public evidence", includedInCustomerReport = true, visibility = "PUBLIC")))
            val sourceId = ServiceLoopPeerTrustStore(workspaceA).localIdentity().technicianId
            ServiceLoopPeerTrustStore(workspaceB).add(sourceId, "Workspace A")

            val selection = ExportCenterSelection(families = ExportPreset.CUSTOMER_DATA.families + ExportPreset.WORK_PERFORMED.families + ExportPreset.IMAGE_ARCHIVE.families)
            val packageBytes = DataTransferExportService(workspaceA, rootA).export(selection)
            val decodedPackage = DataTransferCodec.decode(packageBytes)
            assertEquals(sourceId, decodedPackage.sourceWorkspaceId)
            val register = JSONObject(decodedPackage.families.getValue(DataTransferFamily.REGISTER).toString(Charsets.UTF_8))
            val customerRow = (0 until register.getJSONArray("customers").length()).map { register.getJSONArray("customers").getJSONObject(it) }.single { it.getString("reference") == "CU-DEVICE" }
            assertEquals("Ana Client", customerRow.getString("contactName"))
            assertEquals("+40 700 111 222", customerRow.getString("phone"))
            assertEquals("ana@device.test", customerRow.getString("email"))
            assertFalse(customerRow.has("privateNote"))
            val siteRow = (0 until register.getJSONArray("sites").length()).map { register.getJSONArray("sites").getJSONObject(it) }.single { it.getString("reference") == "ST-DEVICE" }
            assertEquals("1 Device Road", siteRow.getString("address"))
            assertFalse(siteRow.has("privateAccessNotes"))
            val equipmentRow = (0 until register.getJSONArray("equipment").length()).map { register.getJSONArray("equipment").getJSONObject(it) }.single { it.getString("reference") == "EQ-DEVICE" }
            assertEquals("ASSET-17", equipmentRow.getString("technicianIdentifier"))
            assertEquals("Serial", equipmentRow.getString("serialNumber"))
            assertFalse(equipmentRow.has("privateNotes"))
            val contactRows = register.getJSONArray("contacts")
            assertEquals(listOf(1, 2), (0 until contactRows.length()).map { contactRows.getJSONObject(it).getInt("position") })
            assertFalse(contactRows.getJSONObject(0).has("notes"))
            val includedCustomers = (0 until register.getJSONArray("customers").length()).map { register.getJSONArray("customers").getJSONObject(it).getString("reference") }.toSet()
            assertTrue(includedCustomers.contains("CU-INACTIVE"))
            assertFalse(includedCustomers.contains("CU-UNRELATED"))
            assertEquals("DISABLED", InspectionTemplateCodec.decode(decodedPackage.families.getValue(DataTransferFamily.INSPECTION_TEMPLATES)).templates.single().state)
            val importer = DataTransferImportService(workspaceB, rootB)
            val preview = importer.preview(packageBytes)
            assertTrue(preview.canImport())
            importer.import(preview)

            val b = workspaceB.serviceLoopDao()
            val importedCustomer = b.allCustomers().single { it.reference == "CU-DEVICE" }
            val importedSite = b.allSites().single { it.reference == "ST-DEVICE" }
            val importedEquipment = b.allEquipment().single { it.reference == "EQ-DEVICE" }
            assertEquals("Ana Client", importedCustomer.contactName)
            assertEquals("+40 700 111 222", importedCustomer.phone)
            assertEquals("ana@device.test", importedCustomer.email)
            assertNull(importedCustomer.privateNote)
            assertEquals("1 Device Road", importedSite.address)
            assertEquals("Site contact", importedSite.contactName)
            assertEquals("+40 700 333 444", importedSite.phone)
            assertEquals("site@device.test", importedSite.email)
            assertNull(importedSite.privateAccessNotes)
            assertEquals("ASSET-17", importedEquipment.technicianIdentifier)
            assertEquals("Maker", importedEquipment.make)
            assertEquals("Model", importedEquipment.model)
            assertEquals("Serial", importedEquipment.serialNumber)
            assertNull(importedEquipment.privateNotes)
            assertEquals(2, b.customerContacts(importedCustomer.id).size)
            assertEquals(listOf(1, 2), b.customerContacts(importedCustomer.id).map { it.position })
            assertTrue(b.customerContacts(importedCustomer.id).all { it.notes == null })
            assertEquals("IT-DEVICE", b.reusableTemplate(b.allPlans().single().reusableTemplateId!!)?.reference)
            assertEquals("DISABLED", b.reusableTemplate(b.allPlans().single().reusableTemplateId!!)?.state)
            assertEquals("2027-09-24", b.plan(b.allPlans().single().id)?.currentDueDate)
            assertEquals(1, b.obligationCount(b.allPlans().single().id))
            assertEquals(0, b.allVisits().size)
            assertEquals(0, workspaceB.openHelper.writableDatabase.query("SELECT COUNT(*) FROM visit_claims").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(1, b.allTransferredFinalResults().size)
            val evidence = b.allTransferredEvidence().single()
            assertTrue(File(rootB, evidence.relativePath).isFile)
            assertEquals(sourceId, evidence.originWorkspaceId)
            val report = AggregateReportService(workspaceB, RoomServiceLoopRepository(workspaceB, ClockBusinessTime(zoneId = ZoneId.of("UTC")), attachmentRoot = rootB), rootB)
                .reportable(ServiceLoopScopeFilter(customerId = importedCustomer.id)).single()
            assertEquals(1, report.sources.size)

            val retry = importer.preview(packageBytes)
            assertTrue(retry.conflicts.isEmpty())
            importer.import(retry)
            assertEquals(1, b.allTransferredFinalResults().size)
            assertEquals(1, b.allTransferredEvidence().size)
            assertEquals(1, b.obligationCount(b.allPlans().single().id))

            val evidenceCountBeforeConflict = b.allTransferredEvidence().size
            val evidenceEntityBeforeConflict = b.allTransferredEvidence().single()
            val evidenceJson = JSONObject(decodedPackage.families.getValue(DataTransferFamily.EVIDENCE).toString(Charsets.UTF_8))
            evidenceJson.getJSONArray("photos").getJSONObject(0).put("caption", "Changed immutable caption")
            val conflictFamilies = decodedPackage.families + (DataTransferFamily.EVIDENCE to evidenceJson.toString().toByteArray(Charsets.UTF_8))
            val metadataConflictBytes = DataTransferCodec.encode(
                exporterId = decodedPackage.exporterId,
                sourceWorkspaceId = decodedPackage.sourceWorkspaceId,
                families = conflictFamilies,
                binaries = decodedPackage.binaries,
                options = decodedPackage.options,
            )
            val metadataConflict = importer.preview(metadataConflictBytes)
            assertEquals(DataTransferClassification.CONFLICT, metadataConflict.items.single { it.family == DataTransferFamily.EVIDENCE }.classification)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { importer.import(metadataConflict) } }
            assertEquals(evidenceCountBeforeConflict, b.allTransferredEvidence().size)
            assertEquals(evidenceEntityBeforeConflict, b.allTransferredEvidence().single())

            val relay = DataTransferCodec.decode(DataTransferExportService(workspaceB, rootB).export(selection))
            val visit = JSONObject(relay.families.getValue(DataTransferFamily.PERFORMED_WORK).toString(Charsets.UTF_8)).getJSONArray("visits").getJSONObject(0)
            assertEquals("visit", visit.getString("sourceVisitId"))
            assertEquals(sourceId, visit.getString("originWorkspaceId"))
        } finally {
            workspaceA.close(); workspaceB.close(); rootA.deleteRecursively(); rootB.deleteRecursively()
        }
    }
}
