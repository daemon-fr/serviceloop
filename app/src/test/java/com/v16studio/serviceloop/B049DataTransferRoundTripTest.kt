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
        ServiceLoopDatabase.configureReminderDefaults(a.openHelper.writableDatabase)
        ServiceLoopDatabase.configureReminderDefaults(b.openHelper.writableDatabase)
        rootA = File(context.cacheDir, "b049-transfer-a-${System.nanoTime()}").apply { mkdirs() }
        rootB = File(context.cacheDir, "b049-transfer-b-${System.nanoTime()}").apply { mkdirs() }
    }

    @After fun teardown() { a.close(); b.close(); rootA.deleteRecursively(); rootB.deleteRecursively() }

    private suspend fun seedDirectoryAndTemplate() {
        val dao = a.serviceLoopDao()
        dao.insertCustomers(listOf(CustomerEntity("c", "CU-1", "Customer", privateNote = "private")))
        dao.insertCustomerContacts(listOf(
            CustomerContactEntity("cc1", "c", "Office", "PHONE", "+401", 1, 1, "first", 1),
            CustomerContactEntity("cc2", "c", "Dispatch", "EMAIL", "dispatch@example.test", 1, 1, "second", 2),
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

    @Test fun sameSourceIdAcrossNativeEntityTypesKeepsItsHierarchy() = runBlocking {
        val source = a.serviceLoopDao()
        source.insertCustomers(listOf(CustomerEntity("shared", "CU-SHARED", "Shared customer")))
        source.insertSites(listOf(SiteEntity("shared", "shared", "ST-SHARED", "Shared site", null, null)))
        source.insertEquipment(listOf(EquipmentEntity("shared", "shared", "EQ-SHARED", null, "Shared equipment", null, null, null, null)))
        val sourceId = ServiceLoopPeerTrustStore(a).localIdentity().technicianId
        val bytes = DataTransferExportService(a, rootA).export(ExportCenterSelection(families = ExportPreset.CUSTOMER_DATA.families))
        trustedReceiver(sourceId)
        val importer = DataTransferImportService(b, rootB)
        importer.import(importer.preview(bytes))
        val receiver = b.serviceLoopDao()
        val customer = receiver.allCustomers().single()
        val site = receiver.allSites().single()
        val equipment = receiver.allEquipment().single()
        assertEquals(customer.id, site.customerId)
        assertEquals(site.id, equipment.siteId)
        assertEquals("CU-SHARED", customer.reference)
        assertEquals("ST-SHARED", site.reference)
        assertEquals("EQ-SHARED", equipment.reference)
    }

    @Test fun customerPlanTemplateAndOrderedContactsMergeIdempotentlyAndDetectDrift() = runBlocking {
        seedDirectoryAndTemplate()
        val sourceId = ServiceLoopPeerTrustStore(a).localIdentity().technicianId
        val families = ExportPreset.CUSTOMER_DATA.families + setOf(ExportFamily.FOLLOW_UPS, ExportFamily.CONTACT_NOTES, ExportFamily.HISTORY)
        val bytes = DataTransferExportService(a, rootA).export(ExportCenterSelection(families = families))
        val transferredTemplate = InspectionTemplateCodec.decode(DataTransferCodec.decode(bytes).families.getValue(DataTransferFamily.INSPECTION_TEMPLATES)).templates.single()
        assertNull(transferredTemplate.items.single().privateGuidance)
        trustedReceiver(sourceId)
        val importer = DataTransferImportService(b, rootB)
        val first = importer.preview(bytes)
        assertTrue(first.canImport())
        assertTrue(first.items.all { it.classification in setOf(DataTransferClassification.NEW, DataTransferClassification.NEW_HISTORY) })
        importer.import(first)
        val dao = b.serviceLoopDao()
        assertEquals(listOf("+401", "dispatch@example.test"), dao.customerContacts(dao.allCustomers().single().id).map { it.value })
        assertEquals(listOf(1, 2), dao.customerContacts(dao.allCustomers().single().id).map { it.position })
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
            WorkItemEntity("work-2", "visit", "e", null, null, null, "Boiler", "EQ-2", "Inspection", null, null, null, null, false, null, null),
        ))
        dao.insertFinalRecord(FinalRecordEntity("record", "visit", "revision", 2))
        dao.insertFinalRevision(FinalRecordRevisionEntity("revision", "record", 1, "V-2", "2026-09-24", 2, "Customer Two", "Site Two", null, "Business", "Technician A", null, null, null, "UTC", null, customerReference = "CU-2", siteReference = "ST-2"))
        dao.insertFinalWorkItems(listOf(
            FinalWorkItemEntity("final-1", "revision", 1, "work-1", "e", "Boiler", "EQ-2", "Serial", "Maker", "Model", "Serial", "Inspection", null, null, "PERFORMED", "Inspected", null, true, "2026-09-24", "2027-09-24", 1, "YEARS", "obligation", "secret one"),
            FinalWorkItemEntity("final-2", "revision", 2, "work-2", "e", "Boiler", "EQ-2", "Serial", "Maker", "Model", "Serial", "Inspection", null, null, "PERFORMED", "Cleaned", null, false, null, null, null, null, null, "secret two"),
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
        assertEquals(2, decoded.familyVersions.getValue(DataTransferFamily.PERFORMED_WORK))
        val evidencePhotos = JSONObject(decoded.families.getValue(DataTransferFamily.EVIDENCE).toString(Charsets.UTF_8)).getJSONArray("photos")
        assertEquals(2, evidencePhotos.length())
        assertTrue((0 until evidencePhotos.length()).map { evidencePhotos.getJSONObject(it).getString("visibility") }.all { it == "PUBLIC" })
        val preview = importer.preview(bytes)
        assertTrue(preview.canImport())
        importer.import(preview)
        val dao = b.serviceLoopDao()
        assertEquals(0, dao.allVisits().size)
        assertEquals(2, dao.allTransferredFinalResults().size)
        assertTrue(dao.allTransferredFinalResults().all { it.sourcePayloadJson != null })
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
        assertEquals(listOf("Inspection", "Inspection"), rendered.single().lines.map { it.serviceName })
        assertEquals(setOf("Inspected", "Cleaned"), rendered.single().lines.map { it.publicWorkNote }.toSet())
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
        assertEquals(2, relay.familyVersions.getValue(DataTransferFamily.PERFORMED_WORK))
        val relayedRows = JSONObject(relay.families.getValue(DataTransferFamily.PERFORMED_WORK).toString(Charsets.UTF_8)).getJSONArray("visits").getJSONObject(0).getJSONArray("records")
        assertEquals(2, relayedRows.length())
        assertTrue((0 until relayedRows.length()).all { relayedRows.getJSONObject(it).getString("originWorkspaceId") == sourceId })
        assertEquals("visit", JSONObject(relay.families.getValue(DataTransferFamily.PERFORMED_WORK).toString(Charsets.UTF_8)).getJSONArray("visits").getJSONObject(0).getString("sourceVisitId"))

        val comparison = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        val comparisonRoot = File(context.cacheDir, "b049-transfer-compare-${System.nanoTime()}").apply { mkdirs() }
        try {
            ServiceLoopDatabase.configureStage4Tracking(comparison.openHelper.writableDatabase)
            ServiceLoopDatabase.configureReminderDefaults(comparison.openHelper.writableDatabase)
            ServiceLoopPeerTrustStore(comparison).add(sourceId, "Original workspace")
            ServiceLoopPeerTrustStore(comparison).add(relay.exporterId, "Relay workspace")
            val compareImporter = DataTransferImportService(comparison, comparisonRoot)
            compareImporter.import(compareImporter.preview(bytes))
            val historyOnly = DataTransferExportService(a, rootA).export(ExportCenterSelection(
                families = setOf(ExportFamily.SERVICE_RECORDS), includePrivate = false))
            assertTrue(compareImporter.preview(historyOnly).items.filter { it.family == DataTransferFamily.PERFORMED_WORK }
                .all { it.classification == DataTransferClassification.ALREADY_IMPORTED })
            val replay = compareImporter.preview(relayBytes)
            assertTrue(replay.items.toString(), replay.canImport())
            assertTrue(replay.items.filter { it.family == DataTransferFamily.PERFORMED_WORK }
                .all { it.classification == DataTransferClassification.ALREADY_IMPORTED })
            val altered = JSONObject(decoded.families.getValue(DataTransferFamily.PERFORMED_WORK).toString(Charsets.UTF_8))
            altered.getJSONArray("visits").getJSONObject(0).getJSONArray("records").getJSONObject(0)
                .put("unrecognizedSourceMeaning", "changed")
            val alteredFamilies = decoded.families.toMutableMap().apply {
                put(DataTransferFamily.PERFORMED_WORK, altered.toString().toByteArray(Charsets.UTF_8))
            }
            val alteredPackage = DataTransferCodec.encode(sourceId, families = alteredFamilies, binaries = decoded.binaries,
                sourceWorkspaceId = decoded.sourceWorkspaceId, options = decoded.options)
            assertTrue(compareImporter.preview(alteredPackage).items.any {
                it.family == DataTransferFamily.PERFORMED_WORK && it.classification == DataTransferClassification.CONFLICT
            })
        } finally { comparison.close(); comparisonRoot.deleteRecursively() }

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

    @Test fun nativePlanCannotAttachToStagedOneTimeCustomer() = runBlocking {
        seedDirectoryAndTemplate()
        val sourceId = ServiceLoopPeerTrustStore(a).localIdentity().technicianId
        val exported = DataTransferCodec.decode(DataTransferExportService(a, rootA).export(
            ExportCenterSelection(families = ExportPreset.CUSTOMER_DATA.families)))
        val register = JSONObject(exported.families.getValue(DataTransferFamily.REGISTER).toString(Charsets.UTF_8))
        register.getJSONArray("customers").getJSONObject(0).put("customerType", "ONE_TIME")
        val changed = DataTransferCodec.encode(sourceId,
            families = exported.families + (DataTransferFamily.REGISTER to register.toString().toByteArray(Charsets.UTF_8)),
            sourceWorkspaceId = sourceId)
        trustedReceiver(sourceId)
        val preview = DataTransferImportService(b, rootB).preview(changed)
        assertEquals(DataTransferClassification.CONFLICT,
            preview.items.single { it.family == DataTransferFamily.SERVICE_PLANS }.classification)
        assertFalse(preview.canImport())
        assertEquals(0, b.serviceLoopDao().allPlans().size)
    }

    @Test fun literalNullFreeTextRemainsTextThroughNativeImportAndRetry() = runBlocking {
        a.serviceLoopDao().insertCustomers(listOf(CustomerEntity("literal", "CU-N", "Literal customer", privateNote = "null")))
        val sourceId = ServiceLoopPeerTrustStore(a).localIdentity().technicianId
        val bytes = DataTransferExportService(a, rootA).export(ExportCenterSelection(
            families = setOf(ExportFamily.CUSTOMERS), includePrivate = true))
        trustedReceiver(sourceId)
        val importer = DataTransferImportService(b, rootB)
        importer.import(importer.preview(bytes))
        assertEquals("null", b.serviceLoopDao().allCustomers().single().privateNote)
        assertEquals(DataTransferClassification.ALREADY_CURRENT,
            importer.preview(bytes).items.single { it.family == DataTransferFamily.REGISTER }.classification)
    }

    @Test fun malformedCurrentNativeRowsRejectBeforeRoomMutation() = runBlocking {
        seedDirectoryAndTemplate()
        val sourceId = ServiceLoopPeerTrustStore(a).localIdentity().technicianId
        val payload = DataTransferCodec.decode(DataTransferExportService(a, rootA).export(
            ExportCenterSelection(families = ExportPreset.CUSTOMER_DATA.families)))
        trustedReceiver(sourceId)
        val importer = DataTransferImportService(b, rootB)
        fun changed(family: DataTransferFamily, mutate: (JSONObject) -> Unit): ByteArray {
            val familyJson = JSONObject(payload.families.getValue(family).toString(Charsets.UTF_8))
            mutate(familyJson)
            return DataTransferCodec.encode(sourceId,
                families = payload.families + (family to familyJson.toString().toByteArray(Charsets.UTF_8)),
                sourceWorkspaceId = sourceId)
        }
        listOf(
            changed(DataTransferFamily.REGISTER) { it.getJSONArray("customers").getJSONObject(0).put("name", " ") },
            changed(DataTransferFamily.REGISTER) { it.getJSONArray("sites").getJSONObject(0).put("state", "UNKNOWN") },
            changed(DataTransferFamily.REGISTER) { it.getJSONArray("contacts").getJSONObject(0).put("position", "1") },
            changed(DataTransferFamily.SERVICE_PLANS) { it.getJSONArray("plans").getJSONObject(0).put("intervalCount", "1") },
        ).forEach { bytes ->
            assertThrows(IllegalArgumentException::class.java) { runBlocking { importer.preview(bytes) } }
            assertEquals(0, b.serviceLoopDao().allCustomers().size)
            assertEquals(0, b.serviceLoopDao().allPlans().size)
        }
    }

    @Test fun evidenceImportedBeforeHistoryLinksToExactFinalRevisionOnLaterImport() = runBlocking {
        val sourceId = seedCompletedWorkWithEvidence()
        trustedReceiver(sourceId)
        val exporter = DataTransferExportService(a, rootA)
        val evidenceOnly = exporter.export(ExportCenterSelection(
            families = setOf(ExportFamily.PHOTO_METADATA, ExportFamily.IMAGE_FILES),
            includePrivate = false,
        ))
        val workOnly = exporter.export(ExportCenterSelection(
            families = setOf(ExportFamily.VISITS, ExportFamily.SERVICE_RECORDS, ExportFamily.CHECKLIST, ExportFamily.PARTS),
            includePrivate = false,
        ))
        val importer = DataTransferImportService(b, rootB)
        importer.import(importer.preview(evidenceOnly))
        assertEquals(2, b.serviceLoopDao().allTransferredEvidence().size)
        assertTrue(b.serviceLoopDao().allTransferredEvidence().all { it.transferredFinalResultId == null })
        importer.import(importer.preview(workOnly))
        val results = b.serviceLoopDao().allTransferredFinalResults()
        val linked = b.serviceLoopDao().allTransferredEvidence()
        assertEquals(2, results.size)
        assertEquals(2, linked.size)
        assertTrue(linked.all { evidence -> results.any { result ->
            result.id == evidence.transferredFinalResultId &&
                result.originWorkspaceId == evidence.originWorkspaceId &&
                result.sourceVisitId == evidence.sourceVisitId &&
                result.sourceWorkItemId == evidence.sourceWorkItemId &&
                result.sourceFinalRevisionId == evidence.sourceFinalRevisionId
        } })
        importer.import(importer.preview(workOnly))
        assertEquals(linked, b.serviceLoopDao().allTransferredEvidence())
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

    @Test fun privacyOffNativeCustomerDataCarriesOperationalFieldsAndDetectsPhoneDrift() = runBlocking {
        val dao = a.serviceLoopDao()
        dao.insertCustomers(listOf(CustomerEntity("c", "CU-OP", "Operational customer", "Mara Pop", "+40 721 000 111", "mara@example.test", "customer secret")))
        dao.insertCustomerContacts(listOf(
            CustomerContactEntity("contact-1", "c", "Office", "PHONE", "+40 721 000 111", 1, 1, "contact secret", 1),
            CustomerContactEntity("contact-2", "c", "Dispatch", "EMAIL", "dispatch@example.test", 1, 1, "email secret", 2),
        ))
        dao.insertSites(listOf(SiteEntity("s", "c", "ST-OP", "Workshop", "42 Service Road", "Gate code 8421", "Radu Ionescu", "+40 722 000 222", "site@example.test", true)))
        dao.insertEquipment(listOf(EquipmentEntity("e", "s", "EQ-OP", "TECH-17", "Boiler", "Acme", "Heat 4", "SN-7788", "equipment secret")))
        val sourceId = ServiceLoopPeerTrustStore(a).localIdentity().technicianId
        val families = setOf(ExportFamily.CUSTOMERS, ExportFamily.CONTACTS, ExportFamily.SITES, ExportFamily.EQUIPMENT)
        val selection = ExportCenterSelection(ServiceLoopScopeFilter(customerId = "c"), families, includePrivate = false)
        val bytes = DataTransferExportService(a, rootA).export(selection)
        val register = JSONObject(DataTransferCodec.decode(bytes).families.getValue(DataTransferFamily.REGISTER).toString(Charsets.UTF_8))
        val customer = register.getJSONArray("customers").getJSONObject(0)
        assertEquals("Mara Pop", customer.getString("contactName"))
        assertEquals("+40 721 000 111", customer.getString("phone"))
        assertEquals("mara@example.test", customer.getString("email"))
        assertFalse(customer.has("privateNote"))
        assertFalse(customer.has("privateFieldsIncluded"))
        val site = register.getJSONArray("sites").getJSONObject(0)
        assertEquals("42 Service Road", site.getString("address"))
        assertEquals("Radu Ionescu", site.getString("contactName"))
        assertEquals("+40 722 000 222", site.getString("phone"))
        assertEquals("site@example.test", site.getString("email"))
        assertFalse(site.has("privateAccessNotes"))
        val equipment = register.getJSONArray("equipment").getJSONObject(0)
        assertEquals("TECH-17", equipment.getString("technicianIdentifier"))
        assertEquals("Acme", equipment.getString("make"))
        assertEquals("Heat 4", equipment.getString("model"))
        assertEquals("SN-7788", equipment.getString("serialNumber"))
        assertFalse(equipment.has("privateNotes"))
        val contacts = register.getJSONArray("contacts")
        assertEquals("Office", contacts.getJSONObject(0).getString("personName"))
        assertEquals("PHONE", contacts.getJSONObject(0).getString("channel"))
        assertEquals("+40 721 000 111", contacts.getJSONObject(0).getString("value"))
        assertEquals(1, contacts.getJSONObject(0).getInt("position"))
        assertFalse(contacts.getJSONObject(0).has("notes"))
        assertFalse(contacts.getJSONObject(1).has("notes"))

        val readable = unzip(ExportCenterService(a, rootA).export(ExportCenterSelection(families = setOf(ExportFamily.SITES), includePrivate = false)))
            .getValue("sites.csv").toString(Charsets.UTF_8)
        assertTrue(readable.contains("42 Service Road"))
        assertFalse(readable.contains("Gate code 8421"))

        val conflictDb = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        val conflictRoot = File(context.cacheDir, "b049-transfer-conflict-${System.nanoTime()}").apply { mkdirs() }
        try {
            ServiceLoopDatabase.configureStage4Tracking(conflictDb.openHelper.writableDatabase)
            val conflictDao = conflictDb.serviceLoopDao()
            conflictDao.insertCustomers(listOf(CustomerEntity("local-c", "CU-OP", "Operational customer", "Mara Pop", "+40 721 999 999", "mara@example.test", "receiver private")))
            conflictDao.insertSites(listOf(SiteEntity("local-s", "local-c", "ST-OP", "Workshop", "42 Service Road", "receiver access notes", "Radu Ionescu", "+40 722 000 222", "site@example.test", true)))
            conflictDao.insertEquipment(listOf(EquipmentEntity("local-e", "local-s", "EQ-OP", "TECH-17", "Boiler", "Acme", "Heat 4", "SN-7788", "receiver equipment notes")))
            conflictDao.insertCustomerContacts(listOf(
                CustomerContactEntity("local-contact-1", "local-c", "Office", "PHONE", "+40 721 000 111", 1, 1, position = 1),
                CustomerContactEntity("local-contact-2", "local-c", "Dispatch", "EMAIL", "dispatch@example.test", 1, 1, position = 2),
            ))
            ServiceLoopPeerTrustStore(conflictDb).add(sourceId, "Workspace A")
            val conflictImporter = DataTransferImportService(conflictDb, conflictRoot)
            suspend fun classification(type: String) = conflictImporter.preview(bytes).items.single { it.family == DataTransferFamily.REGISTER && it.description.startsWith(type) }.classification
            assertEquals(DataTransferClassification.CONFLICT, classification("Customer"))
            conflictDb.openHelper.writableDatabase.execSQL("UPDATE customers SET phone=? WHERE id=?", arrayOf("+40 721 000 111", "local-c"))
            for ((column, changedValue) in listOf("contactName" to "Changed contact", "phone" to "Changed phone", "email" to "changed@example.test")) {
                conflictDb.openHelper.writableDatabase.execSQL("UPDATE customers SET $column=? WHERE id=?", arrayOf(changedValue, "local-c"))
                assertEquals("customer $column", DataTransferClassification.CONFLICT, classification("Customer"))
                conflictDb.openHelper.writableDatabase.execSQL("UPDATE customers SET $column=? WHERE id=?", arrayOf(mapOf("contactName" to "Mara Pop", "phone" to "+40 721 000 111", "email" to "mara@example.test").getValue(column), "local-c"))
            }
            assertEquals(DataTransferClassification.MATCH_EXISTING, classification("Customer"))
            for ((column, changedValue) in listOf("address" to "Changed address", "contactName" to "Changed site contact", "phone" to "Changed site phone", "email" to "changed-site@example.test")) {
                conflictDb.openHelper.writableDatabase.execSQL("UPDATE sites SET $column=? WHERE id=?", arrayOf(changedValue, "local-s"))
                assertEquals("site $column", DataTransferClassification.CONFLICT, classification("Site"))
                conflictDb.openHelper.writableDatabase.execSQL("UPDATE sites SET $column=? WHERE id=?", arrayOf(mapOf("address" to "42 Service Road", "contactName" to "Radu Ionescu", "phone" to "+40 722 000 222", "email" to "site@example.test").getValue(column), "local-s"))
            }
            assertEquals(DataTransferClassification.MATCH_EXISTING, classification("Site"))
            for ((column, changedValue) in listOf("technicianIdentifier" to "CHANGED-ID", "make" to "Changed make", "model" to "Changed model", "serialNumber" to "CHANGED-SERIAL")) {
                conflictDb.openHelper.writableDatabase.execSQL("UPDATE equipment SET $column=? WHERE id=?", arrayOf(changedValue, "local-e"))
                assertEquals("equipment $column", DataTransferClassification.CONFLICT, classification("Equipment"))
                conflictDb.openHelper.writableDatabase.execSQL("UPDATE equipment SET $column=? WHERE id=?", arrayOf(mapOf("technicianIdentifier" to "TECH-17", "make" to "Acme", "model" to "Heat 4", "serialNumber" to "SN-7788").getValue(column), "local-e"))
            }
            assertEquals(DataTransferClassification.MATCH_EXISTING, classification("Equipment"))
        } finally { conflictDb.close(); conflictRoot.deleteRecursively() }

        trustedReceiver(sourceId)
        val importer = DataTransferImportService(b, rootB)
        importer.import(importer.preview(bytes))
        val importedDao = b.serviceLoopDao()
        val importedCustomer = importedDao.allCustomers().single()
        val importedSite = importedDao.allSites().single()
        val importedEquipment = importedDao.allEquipment().single()
        assertEquals("Mara Pop", importedCustomer.contactName)
        assertEquals("+40 721 000 111", importedCustomer.phone)
        assertEquals("mara@example.test", importedCustomer.email)
        assertNull(importedCustomer.privateNote)
        assertEquals("42 Service Road", importedSite.address)
        assertEquals("Radu Ionescu", importedSite.contactName)
        assertEquals("+40 722 000 222", importedSite.phone)
        assertEquals("site@example.test", importedSite.email)
        assertNull(importedSite.privateAccessNotes)
        assertEquals("TECH-17", importedEquipment.technicianIdentifier)
        assertEquals("Acme", importedEquipment.make)
        assertEquals("Heat 4", importedEquipment.model)
        assertEquals("SN-7788", importedEquipment.serialNumber)
        assertNull(importedEquipment.privateNotes)
        assertEquals(listOf(1, 2), importedDao.customerContacts(importedCustomer.id).map { it.position })
        assertTrue(importedDao.customerContacts(importedCustomer.id).all { it.notes == null })
    }

    @Test fun matchedContactsKeepLocalOrderAndNewContactsAppendBySourceOrderWithRetrySafety() = runBlocking {
        val sourceDao = a.serviceLoopDao()
        sourceDao.insertCustomers(listOf(CustomerEntity("c", "CU-CONTACT", "Contact customer")))
        sourceDao.insertCustomerContacts(listOf(
            CustomerContactEntity("source-match", "c", "Office", "PHONE", "+401", 1, 1, position = 1),
            CustomerContactEntity("source-new-b", "c", "Second new", "EMAIL", "second@example.test", 1, 1, position = 2),
            CustomerContactEntity("source-new-a", "c", "Third new", "OTHER", "third@example.test", 1, 1, position = 3),
        ))
        val sourceId = ServiceLoopPeerTrustStore(a).localIdentity().technicianId
        b.serviceLoopDao().insertCustomers(listOf(CustomerEntity("local-c", "CU-CONTACT", "Contact customer")))
        b.serviceLoopDao().insertCustomerContacts(listOf(
            CustomerContactEntity("local-other", "local-c", "Existing one", "PHONE", "+402", 1, 1, position = 1),
            CustomerContactEntity("local-match", "local-c", "Office", "PHONE", "+401", 1, 1, "receiver-private", 2),
        ))
        trustedReceiver(sourceId)
        val selection = ExportCenterSelection(families = setOf(ExportFamily.CUSTOMERS, ExportFamily.CONTACTS), includePrivate = false)
        val bytes = DataTransferExportService(a, rootA).export(selection)
        val importer = DataTransferImportService(b, rootB)
        val preview = importer.preview(bytes)
        assertTrue(preview.items.none { it.classification == DataTransferClassification.CONFLICT })
        importer.import(preview)
        val dao = b.serviceLoopDao()
        val after = dao.customerContacts("local-c").associateBy { it.id }
        assertEquals(1, after.getValue("local-other").position)
        assertEquals(2, after.getValue("local-match").position)
        assertEquals(3, after.values.single { it.value == "second@example.test" }.position)
        assertEquals(4, after.values.single { it.value == "third@example.test" }.position)
        assertEquals("receiver-private", after.getValue("local-match").notes)

        val retry = importer.preview(bytes)
        assertTrue(retry.items.toString(), retry.items.all { it.classification == DataTransferClassification.ALREADY_CURRENT })
        importer.import(retry)
        assertEquals(4, dao.customerContacts("local-c").size)
        assertEquals(listOf(1, 2, 3, 4), dao.customerContacts("local-c").map { it.position })

        val payload = DataTransferCodec.decode(bytes)
        val register = JSONObject(payload.families.getValue(DataTransferFamily.REGISTER).toString(Charsets.UTF_8))
        register.getJSONArray("contacts").getJSONObject(0).put("position", 0)
        val families = payload.families + (DataTransferFamily.REGISTER to register.toString().toByteArray(Charsets.UTF_8))
        val malformed = repackage(payload, families)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { importer.preview(malformed) } }
        assertEquals(4, dao.customerContacts("local-c").size)
    }

    @Test fun customerDataContactsRespectCustomerLifecycleAndScope() = runBlocking {
        val dao = a.serviceLoopDao()
        dao.insertCustomers(listOf(
            CustomerEntity("customer-a", "CU-A", "Active Customer"),
            CustomerEntity("customer-b", "CU-B", "Archived Customer", state = "ARCHIVED"),
        ))
        dao.insertCustomerContacts(listOf(
            CustomerContactEntity("contact-a", "customer-a", "Office A", "PHONE", "+401", 1, 1, position = 1),
            CustomerContactEntity("contact-b", "customer-b", "Office B", "PHONE", "+402", 1, 1, position = 1),
        ))

        fun rows(bytes: ByteArray) = JSONObject(
            DataTransferCodec.decode(bytes).families.getValue(DataTransferFamily.REGISTER).toString(Charsets.UTF_8),
        )

        fun references(register: JSONObject, key: String) = register.getJSONArray(key).let { values ->
            (0 until values.length()).map { values.getJSONObject(it).getString("reference") }.toSet()
        }

        fun contactValues(register: JSONObject) = register.getJSONArray("contacts").let { values ->
            (0 until values.length()).map { values.getJSONObject(it).getString("value") }.toSet()
        }

        val exporter = DataTransferExportService(a, rootA)
        val excluded = rows(exporter.export(ExportCenterSelection(
            families = ExportPreset.CUSTOMER_DATA.families,
            includeInactive = false,
        )))
        assertEquals(setOf("CU-A"), references(excluded, "customers"))
        assertEquals(setOf("+401"), contactValues(excluded))

        val included = rows(exporter.export(ExportCenterSelection(
            families = ExportPreset.CUSTOMER_DATA.families,
            includeInactive = true,
        )))
        assertEquals(setOf("CU-A", "CU-B"), references(included, "customers"))
        assertEquals(setOf("+401", "+402"), contactValues(included))

        val scoped = rows(exporter.export(ExportCenterSelection(
            scope = ServiceLoopScopeFilter(customerId = "customer-a"),
            families = ExportPreset.CUSTOMER_DATA.families,
            includeInactive = true,
        )))
        assertEquals(setOf("CU-A"), references(scoped, "customers"))
        assertEquals(setOf("+401"), contactValues(scoped))
    }

    @Test fun inactiveHistoryEvidenceAndDisabledTemplateDependenciesCloseWithoutUnrelatedInactiveRows() = runBlocking {
        seedCompletedWorkWithEvidence()
        val dao = a.serviceLoopDao()
        a.openHelper.writableDatabase.execSQL("UPDATE customers SET state='ARCHIVED' WHERE id='c'")
        a.openHelper.writableDatabase.execSQL("UPDATE sites SET state='ARCHIVED' WHERE id='s'")
        a.openHelper.writableDatabase.execSQL("UPDATE equipment SET state='RETIRED' WHERE id='e'")
        dao.insertCustomers(listOf(
            CustomerEntity("plan-c", "CU-PLAN", "Plan customer"),
            CustomerEntity("unrelated-c", "CU-UNRELATED", "Unrelated inactive", state = "ARCHIVED"),
        ))
        dao.insertSites(listOf(
            SiteEntity("plan-s", "plan-c", "ST-PLAN", "Plan site", null, null),
            SiteEntity("unrelated-s", "unrelated-c", "ST-UNRELATED", "Unrelated inactive", null, null, state = "ARCHIVED"),
        ))
        dao.insertEquipment(listOf(
            EquipmentEntity("plan-e", "plan-s", "EQ-PLAN", null, "Plan equipment", null, null, null, null),
            EquipmentEntity("unrelated-e", "unrelated-s", "EQ-UNRELATED", null, "Unrelated inactive", null, null, null, null, "RETIRED"),
        ))
        dao.insertReusableTemplate(ReusableTemplateEntity("disabled-template", "IT-DISABLED", "Disabled checklist", "disabled-revision", state = "DISABLED", modifiedAtEpochMillis = 1))
        dao.insertReusableTemplateRevision(ReusableTemplateRevisionEntity("disabled-revision", "disabled-template", 1, "Disabled checklist", 1))
        dao.insertReusableTemplateItems(listOf(ReusableTemplateItemEntity("disabled-item", "disabled-revision", 1, "Check valve", "STATUS", null, true, null)))
        dao.insertPlans(listOf(ServicePlanEntity("plan", "plan-e", "PL-DISABLED", "Annual check", 1, "YEARS", "2027-09-24", "ACTIVE", null, reusableTemplateId = "disabled-template")))

        val sourceId = ServiceLoopPeerTrustStore(a).localIdentity().technicianId
        val families = ExportPreset.WORK_PERFORMED.families + ExportPreset.IMAGE_ARCHIVE.families + setOf(ExportFamily.PLANS)
        val selection = ExportCenterSelection(families = families, includePrivate = false, includeInactive = false)
        val bytes = DataTransferExportService(a, rootA).export(selection)
        val payload = DataTransferCodec.decode(bytes)
        val register = JSONObject(payload.families.getValue(DataTransferFamily.REGISTER).toString(Charsets.UTF_8))
        val customerRows = register.getJSONArray("customers")
        val siteRows = register.getJSONArray("sites")
        val equipmentRows = register.getJSONArray("equipment")
        assertEquals(setOf("CU-2", "CU-PLAN"), (0 until customerRows.length()).map { customerRows.getJSONObject(it).getString("reference") }.toSet())
        assertEquals("ARCHIVED", (0 until customerRows.length()).map { customerRows.getJSONObject(it) }.single { it.getString("reference") == "CU-2" }.getString("state"))
        assertEquals(setOf("ST-2", "ST-PLAN"), (0 until siteRows.length()).map { siteRows.getJSONObject(it).getString("reference") }.toSet())
        assertEquals(setOf("EQ-2", "EQ-PLAN"), (0 until equipmentRows.length()).map { equipmentRows.getJSONObject(it).getString("reference") }.toSet())
        assertFalse((0 until customerRows.length()).any { customerRows.getJSONObject(it).getString("reference") == "CU-UNRELATED" })
        val transferredTemplate = InspectionTemplateCodec.decode(payload.families.getValue(DataTransferFamily.INSPECTION_TEMPLATES)).templates.single()
        assertEquals("DISABLED", transferredTemplate.state)
        val photos = JSONObject(payload.families.getValue(DataTransferFamily.EVIDENCE).toString(Charsets.UTF_8)).getJSONArray("photos")
        assertEquals(2, photos.length())

        trustedReceiver(sourceId)
        val importer = DataTransferImportService(b, rootB)
        val preview = importer.preview(bytes)
        assertTrue(preview.conflicts.toString(), preview.canImport())
        importer.import(preview)
        val imported = b.serviceLoopDao()
        assertEquals("ARCHIVED", imported.allCustomers().single { it.reference == "CU-2" }.state)
        assertEquals("ARCHIVED", imported.allSites().single { it.reference == "ST-2" }.state)
        assertEquals("RETIRED", imported.allEquipment().single { it.reference == "EQ-2" }.state)
        assertEquals("DISABLED", imported.reusableTemplates().single().state)
        assertEquals(2, imported.allTransferredFinalResults().size)
        assertEquals(2, imported.allTransferredEvidence().size)
        assertEquals(0, imported.allVisits().size)
        assertEquals("2027-09-24", imported.allPlans().single().currentDueDate)
        assertEquals(1, imported.obligationCount(imported.allPlans().single().id))
        assertTrue(imported.allCustomers().none { it.reference == "CU-UNRELATED" })

        val scopedBytes = DataTransferExportService(a, rootA).export(selection.copy(scope = ServiceLoopScopeFilter(customerId = "c")))
        val scopedPayload = DataTransferCodec.decode(scopedBytes)
        val scopedRegister = JSONObject(scopedPayload.families.getValue(DataTransferFamily.REGISTER).toString(Charsets.UTF_8))
        assertEquals(listOf("CU-2"), (0 until scopedRegister.getJSONArray("customers").length()).map { scopedRegister.getJSONArray("customers").getJSONObject(it).getString("reference") })
        assertTrue(JSONObject(scopedPayload.families.getValue(DataTransferFamily.SERVICE_PLANS).toString(Charsets.UTF_8)).getJSONArray("plans").length() == 0)
    }

    @Test fun transferredEvidenceRetryComparesImmutableMetadataAndBytesWithoutMutationOnConflict() = runBlocking {
        val sourceId = seedCompletedWorkWithEvidence()
        trustedReceiver(sourceId)
        val importer = DataTransferImportService(b, rootB)
        val selection = ExportCenterSelection(families = setOf(ExportFamily.PHOTO_METADATA, ExportFamily.IMAGE_FILES), includePrivate = true)
        val originalBytes = DataTransferExportService(a, rootA).export(selection)
        val originalPayload = DataTransferCodec.decode(originalBytes)
        importer.import(importer.preview(originalBytes))
        val originalEvidence = b.serviceLoopDao().allTransferredEvidence().first()
        val evidenceRoot = JSONObject(originalPayload.families.getValue(DataTransferFamily.EVIDENCE).toString(Charsets.UTF_8))
        val originalRow = evidenceRoot.getJSONArray("photos").getJSONObject(0)
        val originalBinaryName = originalRow.getString("binaryName")
        val originalImage = originalPayload.binaries.getValue(originalBinaryName)

        fun packageWith(change: (JSONObject) -> Unit = {}, binaryName: String = originalBinaryName, image: ByteArray = originalImage): ByteArray {
            val row = JSONObject(originalRow.toString())
            row.put("binaryName", binaryName)
            row.put("sha256", WorkResultPackageCodec.sha256(image)).put("byteSize", image.size)
            change(row)
            val evidence = JSONObject().put("version", 1).put("photos", JSONArray().put(row)).toString().toByteArray(Charsets.UTF_8)
            val families = originalPayload.families + (DataTransferFamily.EVIDENCE to evidence)
            return repackage(originalPayload, families, mapOf(binaryName to image))
        }
        suspend fun evidenceClassification(bytes: ByteArray) = importer.preview(bytes).items.single { it.family == DataTransferFamily.EVIDENCE }.classification

        assertEquals(DataTransferClassification.ALREADY_IMPORTED, evidenceClassification(packageWith()))
        assertEquals(DataTransferClassification.ALREADY_IMPORTED, evidenceClassification(packageWith({ it.put("binaryName", "binary-renamed") }, "binary-renamed")))
        assertEquals(DataTransferClassification.ALREADY_IMPORTED, evidenceClassification(packageWith({ it.put("localCustomerId", "receiver-local-change") })))
        val conflictPackages = listOf(
            packageWith({ it.put("caption", "Changed caption") }),
            packageWith({ it.put("visibility", "PRIVATE") }),
            packageWith({ it.put("includedInCustomerReport", false) }),
            packageWith({ it.put("serviceName", "Different service") }),
            packageWith({ it.put("sourceVisitId", "different-source-visit") }),
            packageWith({ it.put("originCustomerSourceId", "different-source-customer") }),
        )
        conflictPackages.forEach { bytes ->
            val preview = importer.preview(bytes)
            assertEquals(DataTransferClassification.CONFLICT, preview.items.single { it.family == DataTransferFamily.EVIDENCE }.classification)
        }
        val countBefore = b.serviceLoopDao().allTransferredEvidence().size
        val imageBefore = File(rootB, originalEvidence.relativePath).readBytes()
        val conflict = importer.preview(conflictPackages.first())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { importer.import(conflict) } }
        assertEquals(countBefore, b.serviceLoopDao().allTransferredEvidence().size)
        assertArrayEquals(imageBefore, File(rootB, originalEvidence.relativePath).readBytes())

        val changedImage = ByteArrayOutputStream().also { output ->
            Bitmap.createBitmap(24, 18, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }.compress(Bitmap.CompressFormat.JPEG, 90, output)
        }.toByteArray()
        val binaryConflict = importer.preview(packageWith(image = changedImage))
        assertEquals(DataTransferClassification.CONFLICT, binaryConflict.items.single { it.family == DataTransferFamily.EVIDENCE }.classification)
    }

    private fun repackage(
        payload: DataTransferPayload,
        families: Map<DataTransferFamily, ByteArray> = payload.families,
        binaries: Map<String, ByteArray> = payload.binaries,
    ): ByteArray = DataTransferCodec.encode(
        exporterId = payload.exporterId,
        sourceWorkspaceId = payload.sourceWorkspaceId,
        families = families,
        binaries = binaries,
        options = payload.options,
    )

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = linkedMapOf<String, ByteArray>().also { entries ->
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() } }
    }
}
