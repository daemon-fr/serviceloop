package com.v16studio.serviceloop

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.domain.ClockBusinessTime
import com.v16studio.serviceloop.domain.ServiceLoopScopeFilter
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class B049DataTransferRoundTripTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var a: ServiceLoopDatabase
    private lateinit var b: ServiceLoopDatabase
    private lateinit var rootA: File
    private lateinit var rootB: File

    @Before fun setup() {
        a = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        b = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        ServiceLoopDatabase.configureStage4Tracking(a.openHelper.writableDatabase)
        ServiceLoopDatabase.configureStage4Tracking(b.openHelper.writableDatabase)
        rootA = File(context.cacheDir, "b049-transfer-a-${System.nanoTime()}").apply { mkdirs() }
        rootB = File(context.cacheDir, "b049-transfer-b-${System.nanoTime()}").apply { mkdirs() }
    }

    @After fun teardown() { a.close(); b.close(); rootA.deleteRecursively(); rootB.deleteRecursively() }

    private suspend fun seedDirectoryAndTemplate() {
        val dao = a.serviceLoopDao()
        dao.insertCustomers(listOf(CustomerEntity("c", "CU-1", "Customer", privateNote = "private")))
        dao.insertCustomerContacts(listOf(
            CustomerContactEntity("cc1", "c", "Office", "PHONE", "+401", 1, 1, "first", 0),
            CustomerContactEntity("cc2", "c", "Dispatch", "EMAIL", "dispatch@example.test", 1, 1, "second", 1),
        ))
        dao.insertSites(listOf(SiteEntity("s", "c", "ST-1", "Site", "Private address", "Gate code", isDefault = true)))
        dao.insertEquipment(listOf(EquipmentEntity("e", "s", "EQ-1", "Boiler", "Equipment", "Maker", "Model", "Serial", "Private equipment notes")))
        dao.insertFollowUps(listOf(FollowUpEntity("followup", "FU-1", "CALL", "Confirm access", "2026-10-01", "OPEN", "c", "s", "e", "private planning")))
        dao.insertContactNote(ContactNoteEntity("contact-note", "CN-1", "c", "s", "e", "PHONE", 1_790_208_000_000, "Reached customer", "private contact note", 1))
        dao.insertChangeEntry(ChangeEntryEntity("change", "CUSTOMER", "c", "CUSTOMER_UPDATED", "2026-09-24", 1, "Owner update", "private old", "private new", "c", "s", "e", null, "Customer", "Site", "Equipment"))
        dao.insertReusableTemplate(ReusableTemplateEntity("template", "IT-1", "Safety", "template-revision", modifiedAtEpochMillis = 1))
        dao.insertReusableTemplateRevision(ReusableTemplateRevisionEntity("template-revision", "template", 1, "Safety", 1))
        dao.insertReusableTemplateItems(listOf(ReusableTemplateItemEntity("template-item", "template-revision", 1, "Check pressure", "NUMBER", "bar", true, "Private guidance")))
        dao.insertPlans(listOf(ServicePlanEntity("plan", "e", "PL-1", "Annual service", 1, "YEARS", "2027-09-24", "ACTIVE", "obligation", reusableTemplateId = "template")))
        dao.insertObligations(listOf(ServiceObligationEntity("obligation", "plan", 1, "2027-09-24", 1)))
    }

    private suspend fun trustedReceiver(sourceId: String) {
        ServiceLoopPeerTrustStore(b).add(sourceId, "Workspace A")
    }

    @Test fun customerPlanTemplateAndOrderedContactsMergeIdempotentlyAndDetectDrift() = runBlocking {
        seedDirectoryAndTemplate()
        val sourceId = ServiceLoopPeerTrustStore(a).localIdentity().technicianId
        val families = ExportPreset.CUSTOMER_DATA.families + setOf(ExportFamily.FOLLOW_UPS, ExportFamily.CONTACT_NOTES, ExportFamily.HISTORY)
        val bytes = DataTransferExportService(a, rootA).export(ExportCenterSelection(families = families))
        trustedReceiver(sourceId)
        val importer = DataTransferImportService(b, rootB)
        val first = importer.preview(bytes)
        assertTrue(first.canImport())
        assertTrue(first.items.all { it.classification in setOf(DataTransferClassification.NEW, DataTransferClassification.NEW_HISTORY) })
        importer.import(first)
        val dao = b.serviceLoopDao()
        assertEquals(listOf("+401", "dispatch@example.test"), dao.customerContacts(dao.allCustomers().single().id).map { it.value })
        assertEquals(listOf(0, 1), dao.customerContacts(dao.allCustomers().single().id).map { it.position })
        assertEquals("2027-09-24", dao.allPlans().single().currentDueDate)
        assertEquals(1, dao.obligationCount(dao.allPlans().single().id))
        assertEquals("IT-1", dao.reusableTemplate(dao.allPlans().single().reusableTemplateId!!)?.reference)
        assertEquals(0, dao.allVisits().size)
        assertEquals(0, dao.allFollowUps().size)
        assertEquals(0, dao.allContactNotes().size)
        assertEquals(3, dao.allTransferredHistoryEntries().size)

        val retry = importer.preview(bytes)
        assertTrue(retry.items.toString(), retry.canImport())
        assertTrue(retry.items.all { it.classification in setOf(DataTransferClassification.ALREADY_CURRENT, DataTransferClassification.ALREADY_IMPORTED) })
        importer.import(retry)
        val rewrappedSameData = DataTransferCodec.encode(sourceId, families = DataTransferCodec.decode(bytes).families, sourceWorkspaceId = sourceId)
        assertTrue(importer.preview(rewrappedSameData).items.all { it.classification in setOf(DataTransferClassification.ALREADY_CURRENT, DataTransferClassification.ALREADY_IMPORTED) })
        assertEquals(2, dao.customerContacts(dao.allCustomers().single().id).size)
        assertEquals(1, dao.obligationCount(dao.allPlans().single().id))
        val relayedContext = DataTransferCodec.decode(DataTransferExportService(b, rootB).export(ExportCenterSelection(families = families)))
        listOf(DataTransferFamily.FOLLOW_UPS, DataTransferFamily.CONTACT_NOTES, DataTransferFamily.CHANGE_HISTORY).forEach { family ->
            val records = JSONObject(relayedContext.families.getValue(family).toString(Charsets.UTF_8)).getJSONArray("records")
            assertEquals(1, records.length())
            assertEquals(sourceId, records.getJSONObject(0).getString("originWorkspaceId"))
        }

        b.openHelper.writableDatabase.execSQL("UPDATE customers SET name='Locally changed' WHERE id=?", arrayOf(dao.allCustomers().single().id))
        assertEquals(DataTransferClassification.CONFLICT, importer.preview(bytes).items.first { it.family == DataTransferFamily.REGISTER && it.description.startsWith("Customer") }.classification)
        assertEquals(0, dao.allVisits().size)
    }

    private suspend fun seedCompletedWorkWithEvidence(): String {
        val dao = a.serviceLoopDao()
        dao.insertCustomers(listOf(CustomerEntity("c", "CU-2", "Customer Two")))
        dao.insertSites(listOf(SiteEntity("s", "c", "ST-2", "Site Two", null, null)))
        dao.insertEquipment(listOf(EquipmentEntity("e", "s", "EQ-2", null, "Boiler", "Maker", "Model", "Serial", null)))
        dao.insertVisits(listOf(WorkingVisitEntity("visit", "V-2", "c", "s", "2026-09-24", "Customer Two", "Site Two", null, "COMPLETED", 1)))
        dao.insertWorkItems(listOf(
            WorkItemEntity("work-1", "visit", "e", null, null, null, "Boiler", "EQ-2", "Inspection", null, null, null, null, false, null, null),
            WorkItemEntity("work-2", "visit", "e", null, null, null, "Boiler", "EQ-2", "Cleaning", null, null, null, null, false, null, null),
        ))
        dao.insertFinalRecord(FinalRecordEntity("record", "visit", "revision", 2))
        dao.insertFinalRevision(FinalRecordRevisionEntity("revision", "record", 1, "V-2", "2026-09-24", 2, "Customer Two", "Site Two", null, "Business", "Technician A", null, null, null, "UTC", null, customerReference = "CU-2", siteReference = "ST-2"))
        dao.insertFinalWorkItems(listOf(
            FinalWorkItemEntity("final-1", "revision", 1, "work-1", "e", "Boiler", "EQ-2", "Serial", "Maker", "Model", "Serial", "Inspection", null, null, "PERFORMED", "Inspected", null, true, "2026-09-24", "2027-09-24", 1, "YEARS", "obligation", "secret one"),
            FinalWorkItemEntity("final-2", "revision", 2, "work-2", "e", "Boiler", "EQ-2", "Serial", "Maker", "Model", "Serial", "Cleaning", null, null, "PERFORMED", "Cleaned", null, false, null, null, null, null, null, "secret two"),
        ))
        dao.insertFinalChecklistItems(listOf(FinalChecklistItemEntity("check", "final-1", 1, null, null, "Pressure", "NUMBER", "bar", true, "PASS", "2.0", null, null)))
        dao.insertFinalParts(listOf(FinalPartEntryEntity("part", "final-2", 1, "Filter", "1", "each")))

        val imageDir = File(rootA, "photos").apply { mkdirs() }
        fun jpeg(fileName: String): Pair<String, Pair<ByteArray, String>> {
            val bytes = ByteArrayOutputStream().also { output -> Bitmap.createBitmap(24, 18, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, output) }.toByteArray()
            val file = File(imageDir, fileName).apply { writeBytes(bytes) }
            return file.relativeTo(rootA).path.replace('\\', '/') to (bytes to WorkResultPackageCodec.sha256(bytes))
        }
        val photoRows = listOf(
            Triple("photo-in", "PUBLIC", true), Triple("photo-out", "PUBLIC", false), Triple("photo-private", "PRIVATE", false),
        ).mapIndexed { index, (id, visibility, included) ->
            val (relative, data) = jpeg("$id.jpg")
            val (bytes, hash) = data
            dao.insertAttachments(listOf(AttachmentEntity("attachment-$id", "WORK_ITEM", if (index == 0) "work-1" else "work-2", relative, hash, id, "image/jpeg", included, "PRESENT", bytes.size.toLong(), id, visibility)))
            FinalPhotoEntryEntity(id, if (index == 0) "final-1" else "final-2", index + 1, "attachment-$id", relative, hash, bytes.size.toLong(), "image/jpeg", id, includedInCustomerReport = included, visibility = visibility)
        }
        dao.insertFinalPhotos(photoRows)
        return ServiceLoopPeerTrustStore(a).localIdentity().technicianId
    }

    @Test fun performedWorkAndStandaloneImageArchiveStayImmutableAndRelayOriginalOrigin() = runBlocking {
        val sourceId = seedCompletedWorkWithEvidence()
        trustedReceiver(sourceId)
        val importer = DataTransferImportService(b, rootB)
        val workSelection = ExportCenterSelection(families = setOf(ExportFamily.VISITS, ExportFamily.SERVICE_RECORDS, ExportFamily.CHECKLIST, ExportFamily.PARTS, ExportFamily.PHOTO_METADATA, ExportFamily.IMAGE_FILES), includePrivate = false)
        val bytes = DataTransferExportService(a, rootA).export(workSelection)
        val decoded = DataTransferCodec.decode(bytes)
        val evidencePhotos = JSONObject(decoded.families.getValue(DataTransferFamily.EVIDENCE).toString(Charsets.UTF_8)).getJSONArray("photos")
        assertEquals(2, evidencePhotos.length())
        assertTrue((0 until evidencePhotos.length()).map { evidencePhotos.getJSONObject(it).getString("visibility") }.all { it == "PUBLIC" })
        val preview = importer.preview(bytes)
        assertTrue(preview.canImport())
        importer.import(preview)
        val dao = b.serviceLoopDao()
        assertEquals(0, dao.allVisits().size)
        assertEquals(2, dao.allTransferredFinalResults().size)
        assertEquals(2, dao.allTransferredEvidence().size)
        assertTrue(dao.allTransferredEvidence().all { File(rootB, it.relativePath).isFile })
        val visit = AggregateReportService(b, RoomServiceLoopRepository(b, ClockBusinessTime(java.time.Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), java.time.ZoneId.of("UTC")), java.time.ZoneId.of("UTC")), attachmentRoot = rootB), rootB)
        val reportable = visit.reportable(ServiceLoopScopeFilter(customerId = dao.allCustomers().single().id))
        assertEquals(1, reportable.size)
        assertEquals(2, reportable.single().sources.size)

        dao.upsertBusinessProfile(BusinessProfileEntity(businessName = "Receiver", technicianName = "Coordinator", phone = null, email = null, postalAddress = null, zoneId = "UTC", modifiedAtEpochMillis = 1))
        var rendered: List<com.v16studio.serviceloop.domain.PublicReportModel> = emptyList()
        val reports = AggregateReportService(b, RoomServiceLoopRepository(b, ClockBusinessTime(java.time.Clock.systemUTC(), java.time.ZoneId.of("UTC")), attachmentRoot = rootB), rootB,
            AggregateReportWriter { models, _, _, _, target, _ -> rendered = models; target.writeBytes(byteArrayOf(1, 2, 3)); 1 })
        reports.generate(ServiceLoopScopeFilter(customerId = dao.allCustomers().single().id), listOf(reportable.single().key))
        assertEquals(2, rendered.single().lines.size)
        assertEquals(1, rendered.single().lines.sumOf { it.photos.size })

        val readable = ExportCenterService(b, rootB).export(ExportCenterSelection(families = workSelection.families))
        val files = unzip(readable)
        assertTrue(files.getValue("service_records.csv").toString(Charsets.UTF_8).contains("DATA_TRANSFER"))
        assertEquals(2, files.getValue("service_records.csv").toString(Charsets.UTF_8).split("DATA_TRANSFER").size - 1)

        val restoreRoot = File(context.cacheDir, "b049-transfer-recovery-${System.nanoTime()}").apply { mkdirs() }
        val restored = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        try {
            val passphrase = "data transfer recovery round trip".toCharArray()
            val recovery = RecoveryPackage(b, rootB)
            val backup = recovery.create(passphrase, false)
            assertTrue(backup.complete)
            val restoredRecovery = RecoveryPackage(restored, restoreRoot)
            restoredRecovery.restore(restoredRecovery.inspect(backup.bytes, passphrase))
            assertEquals(dao.allDataTransferBindings(), restored.serviceLoopDao().allDataTransferBindings())
            assertEquals(dao.allTransferredFinalResults(), restored.serviceLoopDao().allTransferredFinalResults())
            assertEquals(dao.allTransferredEvidence(), restored.serviceLoopDao().allTransferredEvidence())
            assertTrue(restored.serviceLoopDao().allTransferredEvidence().all { File(restoreRoot, it.relativePath).isFile })
        } finally { restored.close(); restoreRoot.deleteRecursively() }

        val relayBytes = DataTransferExportService(b, rootB).export(workSelection)
        val relay = DataTransferCodec.decode(relayBytes)
        val relayedRows = JSONObject(relay.families.getValue(DataTransferFamily.PERFORMED_WORK).toString(Charsets.UTF_8)).getJSONArray("visits").getJSONObject(0).getJSONArray("records")
        assertEquals(2, relayedRows.length())
        assertTrue((0 until relayedRows.length()).all { relayedRows.getJSONObject(it).getString("originWorkspaceId") == sourceId })
        assertEquals("visit", JSONObject(relay.families.getValue(DataTransferFamily.PERFORMED_WORK).toString(Charsets.UTF_8)).getJSONArray("visits").getJSONObject(0).getString("sourceVisitId"))

        val standalone = DataTransferExportService(a, rootA).export(ExportCenterSelection(families = setOf(ExportFamily.PHOTO_METADATA, ExportFamily.IMAGE_FILES)))
        val standaloneB = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        val rootStandalone = File(context.cacheDir, "b049-transfer-img-${System.nanoTime()}").apply { mkdirs() }
        try {
            ServiceLoopDatabase.configureStage4Tracking(standaloneB.openHelper.writableDatabase)
            ServiceLoopPeerTrustStore(standaloneB).add(sourceId, "Workspace A")
            val standaloneImport = DataTransferImportService(standaloneB, rootStandalone)
            standaloneImport.import(standaloneImport.preview(standalone))
            assertEquals(0, standaloneB.serviceLoopDao().allVisits().size)
            assertEquals(2, standaloneB.serviceLoopDao().allTransferredEvidence().size)
            assertTrue(standaloneB.serviceLoopDao().allTransferredEvidence().all { File(rootStandalone, it.relativePath).isFile })
        } finally { standaloneB.close(); rootStandalone.deleteRecursively() }
    }

    @Test fun untrustedSourceAndConflictingBoundRecordCannotPartiallyWrite() = runBlocking {
        seedDirectoryAndTemplate()
        val sourceId = ServiceLoopPeerTrustStore(a).localIdentity().technicianId
        val bytes = DataTransferExportService(a, rootA).export(ExportCenterSelection(families = ExportPreset.CUSTOMER_DATA.families))
        val untrusted = DataTransferImportService(b, rootB).preview(bytes)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { DataTransferImportService(b, rootB).import(untrusted) } }
        assertEquals(0, b.serviceLoopDao().allCustomers().size)
        ServiceLoopPeerTrustStore(b).add(sourceId, "Workspace A")
        b.serviceLoopDao().insertCustomers(listOf(CustomerEntity("collision", "CU-1", "Different customer")))
        val conflict = DataTransferImportService(b, rootB).preview(bytes)
        assertTrue(conflict.conflicts.isNotEmpty())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { DataTransferImportService(b, rootB).import(conflict) } }
        assertEquals(1, b.serviceLoopDao().allCustomers().size)
        assertEquals(0, b.serviceLoopDao().allSites().size)
    }

    @Test fun privateFieldsOmittedBySenderDoNotEraseReceiverValuesWhenMatching() = runBlocking {
        a.serviceLoopDao().insertCustomers(listOf(CustomerEntity("c", "CU-PRIVATE", "Same shared name", privateNote = "Sender private value")))
        b.serviceLoopDao().insertCustomers(listOf(CustomerEntity("local-c", "CU-PRIVATE", "Same shared name", privateNote = "Receiver-only private value")))
        val sourceId = ServiceLoopPeerTrustStore(a).localIdentity().technicianId
        ServiceLoopPeerTrustStore(b).add(sourceId, "Workspace A")
        val bytes = DataTransferExportService(a, rootA).export(ExportCenterSelection(families = setOf(ExportFamily.CUSTOMERS), includePrivate = false))
        val importer = DataTransferImportService(b, rootB)
        val preview = importer.preview(bytes)
        assertEquals(DataTransferClassification.MATCH_EXISTING, preview.items.single().classification)
        importer.import(preview)
        assertEquals("Receiver-only private value", b.serviceLoopDao().customer("local-c")?.privateNote)
        assertEquals(1, b.serviceLoopDao().allCustomers().size)
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = linkedMapOf<String, ByteArray>().also { entries ->
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() } }
    }
}
