package com.v16studio.serviceloop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.domain.WorkSubjectType
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class B045SlsyncTest {
    private lateinit var database: ServiceLoopDatabase
    private lateinit var attachmentRoot: File
    private val exporterId = TechnicianIdCodec.generate()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val time = object : BusinessTime {
        override val zoneId = ZoneId.of("Europe/Bucharest")
        override fun instant() = Instant.parse("2026-09-22T10:00:00Z")
    }

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        ServiceLoopDatabase.configureStage4Tracking(database.openHelper.writableDatabase)
        kotlinx.coroutines.runBlocking { database.dispatchDao().insertTechnicianIdentity(TechnicianIdentityEntity("primary", exporterId, "Device technician", 1L, 1L)) }
        attachmentRoot = File(context.cacheDir, "b045-${System.nanoTime()}").apply { mkdirs() }
    }

    @After fun tearDown() { database.close(); attachmentRoot.deleteRecursively() }

    @Test fun codecRoundTripsTinyValidFullWorkspace() {
        val original = packageValue()
        val decoded = ServiceLoopSyncCodec.decode(ServiceLoopSyncCodec.encode(original))
        assertEquals(original, decoded)
        assertEquals(1, ServiceLoopSyncCodec.preview(decoded).counts.customers)
        assertEquals(exporterId, decoded.manifest.exporterId)
        val envelope = ServiceLoopSyncEnvelopeCodec.decode(ServiceLoopSyncCodec.encode(original))
        assertEquals(2, envelope.manifest.formatVersion)
        assertEquals(2, envelope.manifest.sections.single { it.name == "register" }.version)
        assertEquals(2, envelope.manifest.sections.single { it.name == "team" }.version)
        assertEquals("office contact", decoded.register.customerContacts.single().personName)
        assertEquals("Technician note", decoded.team.technicians.single().notes)
    }

    @Test fun localIdentityAndTrustedIdsAreStableAndManageable() = runTest {
        val store = ServiceLoopPeerTrustStore(database)
        val original = store.localIdentity()
        assertEquals(exporterId, original.technicianId)
        assertEquals(ServiceLoopSourceTrust.TRUSTED, store.assess(exporterId).trust)
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { store.add(exporterId, "This device") } }
        val peerId = TechnicianIdCodec.generate()
        val added = store.add(peerId, "  Pandomur coordinator  ")
        assertEquals("Pandomur coordinator", added.name)
        assertEquals(ServiceLoopSourceTrust.TRUSTED, store.assess(peerId).trust)
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { store.add(peerId, "Duplicate") } }
        store.rename(peerId, "Regional coordinator")
        assertEquals("Regional coordinator", store.assess(peerId).friendlyName)
        assertEquals(original.technicianId, store.localIdentity().technicianId)
        store.remove(peerId)
        assertEquals(ServiceLoopSourceTrust.NOT_TRUSTED, store.assess(peerId).trust)
    }

    @Test fun fullWorkspaceMutationRequiresTrustedExporterAndPreservesLocalIdentityAndTrust() = runTest {
        val dao = database.serviceLoopDao()
        dao.insertCustomers(listOf(CustomerEntity("old-customer", "OLD-001", "Old customer")))
        val outsiderId = TechnicianIdCodec.generate()
        val untrusted = packageValue().copy(manifest = packageValue().manifest.copy(exporterId = outsiderId))
        val repo = RoomServiceLoopRepository(database, time, attachmentRoot = attachmentRoot)
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { repo.importServiceLoopSync(untrusted) } }
        assertNotNull(dao.customer("old-customer"))
        assertNull(dao.customer("customer-1"))
        val store = ServiceLoopPeerTrustStore(database)
        store.add(outsiderId, "Office")
        repo.importServiceLoopSync(untrusted)
        assertNull(dao.customer("old-customer"))
        assertNotNull(dao.customer("customer-1"))
        assertEquals("office@example.com", dao.customerContacts("customer-1").single().value)
        assertEquals(exporterId, store.localIdentity().technicianId)
        assertEquals("Office", store.trustedIds().single().name)
        store.remove(outsiderId)
        val currentCustomer = dao.customer("customer-1")!!
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { repo.importServiceLoopSync(untrusted) } }
        assertEquals(currentCustomer, dao.customer("customer-1"))
    }

    @Test fun recoveryBackupRestoresTrustedIdsAndStableIdentity() = runTest {
        val store = ServiceLoopPeerTrustStore(database)
        val local = store.localIdentity()
        val peerId = TechnicianIdCodec.generate()
        store.add(peerId, "Backup coordinator")
        val repo = RoomServiceLoopRepository(database, time, attachmentRoot = attachmentRoot)
        val passphrase = "trusted peers backup".toCharArray()
        val inspection = repo.inspectBackup(repo.createBackup(passphrase).bytes, passphrase)
        store.remove(peerId)
        repo.restoreBackup(inspection, "REPLACE", false)
        assertEquals(peerId, store.trustedIds().single().peerId)
        assertEquals(local.technicianId, store.localIdentity().technicianId)
    }

    @Test fun fullWorkspaceV1IsReadableButHasNoSourceId() {
        val legacy = asLegacyV1(ServiceLoopSyncCodec.encode(packageValue()))
        val decoded = ServiceLoopSyncCodec.decode(legacy)
        assertEquals(1, decoded.manifest.formatVersion)
        assertNull(decoded.manifest.exporterId)
        assertTrue(decoded.register.customerContacts.isEmpty())
        assertNull(decoded.team.technicians.single().notes)
    }

    @Test fun recoverySchema16AddsEmptyTrustedIdsTableAndPreservesLocalIdentity() {
        val identityRow = JSONObject().put("id", "primary").put("technicianId", exporterId).put("displayName", "Device technician")
        val root = JSONObject().put("schemaVersion", 16).put(
            "tables",
            JSONArray().put(JSONObject().put("name", "technician_identity").put("rows", JSONArray().put(identityRow))),
        )

        RecoveryPackage(database, attachmentRoot).normalizeTrustedServiceLoopIds(root)

        val tables = root.getJSONArray("tables")
        assertEquals(17, root.getInt("schemaVersion"))
        assertEquals("technician_identity", tables.getJSONObject(0).getString("name"))
        assertEquals(exporterId, tables.getJSONObject(0).getJSONArray("rows").getJSONObject(0).getString("technicianId"))
        assertEquals("trusted_service_loop_ids", tables.getJSONObject(1).getString("name"))
        assertEquals(0, tables.getJSONObject(1).getJSONArray("rows").length())
    }

    @Test fun initialImportReplacesBusinessRowsAndPreservesDeviceState() = runTest {
        val dao = database.serviceLoopDao()
        val identity = database.dispatchDao().technicianIdentity()!!
        dao.insertCustomers(listOf(CustomerEntity("old-customer", "OLD-001", "Old customer")))
        val preferences = ReminderPreferencesEntity(dueSoonHorizonDays = 42)
        dao.upsertReminderPreferences(preferences)
        val oldMetadata = RecoveryMetadataEntity(datasetId = "old-dataset", firstBusinessWriteAtEpochMillis = 1L, lastBusinessWriteAtEpochMillis = 2L, lastBackupAttemptAtEpochMillis = 3L, lastVerifiedFullBackupAtEpochMillis = 4L, lastVerifiedSnapshotAtEpochMillis = 5L, lastVerifiedDestination = "old", lastVerifiedSize = 6L, backupReminderDays = 19, restoredFromIncompleteCopy = true, restrictedRecoveryState = false, adoptionToken = "old-token")
        dao.upsertRecoveryMetadata(oldMetadata)
        val repo = RoomServiceLoopRepository(database, time, attachmentRoot = attachmentRoot)

        val result = repo.importServiceLoopSync(packageValue())

        assertEquals(1, dao.customerCount())
        assertNull(dao.customer("old-customer"))
        assertNotNull(dao.customer("customer-1"))
        assertNotNull(dao.reusableTemplate("template-1"))
        assertNotNull(dao.plan("plan-1"))
        assertNotNull(dao.obligation("plan-1-obligation-1"))
        assertNotNull(dao.visit("visit-1"))
        assertNotNull(dao.workItem("work-1"))
        assertNotNull(dao.claimForObligation("plan-1-obligation-1"))
        assertNotNull(dao.followUp("followup-1"))
        assertNotNull(dao.contactNote("note-1"))
        assertEquals(1, database.dispatchDao().technicians().size)
        assertEquals("Technician note", database.dispatchDao().technician("import-tech")!!.notes)
        assertEquals(1, database.dispatchDao().outboxVisits().size)
        assertTrue(repo.dueServices().isNotEmpty())
        assertNull(dao.obligation("plan-1-obligation-1")?.consumedAtEpochMillis)
        assertEquals(identity, database.dispatchDao().technicianIdentity())
        assertEquals(preferences, dao.reminderPreferences())
        val newMetadata = dao.recoveryMetadata()!!
        assertNotEquals(oldMetadata.datasetId, newMetadata.datasetId)
        assertEquals(time.instant().toEpochMilli(), newMetadata.firstBusinessWriteAtEpochMillis)
        assertEquals(19, newMetadata.backupReminderDays)
        assertNull(newMetadata.lastVerifiedFullBackupAtEpochMillis)
        assertFalse(newMetadata.restoredFromIncompleteCopy)
        assertFalse(newMetadata.restrictedRecoveryState)
        assertNull(newMetadata.adoptionToken)
        assertEquals(1, result.counts.dispatchDrafts)
    }

    @Test fun invalidReferenceRollsBackAfterReplacementHasStarted() = runTest {
        val dao = database.serviceLoopDao()
        dao.insertCustomers(listOf(CustomerEntity("old-customer", "OLD-001", "Old customer")))
        dao.upsertRecoveryMetadata(RecoveryMetadataEntity(datasetId = "old-dataset", firstBusinessWriteAtEpochMillis = 1L, lastBusinessWriteAtEpochMillis = 2L, lastBackupAttemptAtEpochMillis = null, lastVerifiedFullBackupAtEpochMillis = null, lastVerifiedSnapshotAtEpochMillis = null, lastVerifiedDestination = null, lastVerifiedSize = null))
        val repo = RoomServiceLoopRepository(database, time, attachmentRoot = attachmentRoot)
        val invalidVisit = packageValue().visits.bookedVisits.single().copy(work = listOf(SyncBookedWork("work-bad", "AD_HOC", null, "Bad task", "EQUIPMENT", "equipment-1", null, "missing-template")))
        val invalid = packageValue().copy(visits = packageValue().visits.copy(bookedVisits = listOf(invalidVisit)))

        assertThrows(RuntimeException::class.java) { kotlinx.coroutines.runBlocking { repo.importServiceLoopSync(invalid) } }
        assertNotNull(dao.customer("old-customer"))
        assertNull(dao.customer("customer-1"))
        assertEquals("old-dataset", dao.recoveryMetadata()?.datasetId)
    }

    @Test fun universalEnvelopeWrapsWorkAndTemplatePayloadsWithoutChangingThem() {
        val work = dispatchPackage()
        val decodedWork = ServiceLoopSyncEnvelopeCodec.unwrapWorkAssignment(ServiceLoopSyncEnvelopeCodec.wrapWorkAssignment(work, exporterId))
        assertEquals(work, decodedWork)
        val transfer = InspectionTemplateTransfer("2026-09-22T10:00:00Z", listOf(InspectionTemplateTransferEntry("IT-1", "Safety", 1, listOf(DispatchInspectionItem(1, "Guard", "STATUS", null, true, null)))) )
        val decodedTemplates = ServiceLoopSyncEnvelopeCodec.unwrapTemplateShare(ServiceLoopSyncEnvelopeCodec.wrapTemplateShare(transfer, exporterId))
        assertEquals(transfer.generatedAt, decodedTemplates.generatedAt)
        assertEquals(transfer.templates.single().copy(fingerprint = InspectionTemplateCodec.fingerprint(transfer.templates.single())), decodedTemplates.templates.single())
    }

    @Test fun purposeRoutingRejectsUnknownAndFamilyFilteringKeepsReferencesValid() {
        val value = packageValue()
        val filtered = filterFullWorkspacePackage(value, setOf(SyncContentFamily.VISITS))
        assertTrue(filtered.register.customers.isNotEmpty())
        assertTrue(filtered.register.sites.isNotEmpty())
        assertTrue(filtered.plans.plans.isNotEmpty())
        assertTrue(filtered.followups.followUps.isEmpty())
        ServiceLoopSyncCodec.decode(ServiceLoopSyncCodec.encode(filtered))
        val invalidManifest = ServiceLoopSyncManifest("x", "Unknown", "NOT_A_PURPOSE", "2026-09-22T10:00:00Z", sections = listOf(ServiceLoopSyncSectionDeclaration("x", 1, "x.json")), exporterId = exporterId)
        assertThrows(IllegalArgumentException::class.java) { ServiceLoopSyncEnvelopeCodec.encode(invalidManifest, mapOf("x" to byteArrayOf(1))) }
    }

    @Test fun removingParentFamilyClosesSelectedDependents() {
        val value = packageValue()
        val selected = SyncContentFamily.entries.toSet()
        val withoutCustomers = removeSyncContentFamily(value, selected, SyncContentFamily.CUSTOMERS)
        assertEquals(setOf(SyncContentFamily.BUSINESS_PROFILE, SyncContentFamily.INSPECTION_TEMPLATES, SyncContentFamily.TECHNICIANS, SyncContentFamily.TEAMS), withoutCustomers)
        val withoutTechnicians = removeSyncContentFamily(value, selected, SyncContentFamily.TECHNICIANS)
        assertFalse(SyncContentFamily.TEAMS in withoutTechnicians)
        assertFalse(SyncContentFamily.DISPATCH_DRAFTS in withoutTechnicians)
    }

    private fun dispatchPackage() = DispatchPackage(
        packageId = "package-1",
        createdAt = "2026-09-22T10:00:00Z",
        senderLabel = "Coordinator",
        customers = listOf(DispatchCustomer("CU-1", "Customer")),
        sites = listOf(DispatchSite("ST-1", "CU-1", "Site", "Address")),
        equipment = emptyList(),
        visits = listOf(
            DispatchVisit(
                dispatchVisitId = "DV-1", generation = 1, managerReference = "JOB-1", serviceDate = "2026-09-23",
                appointmentLocalTime = null, appointmentZoneId = "Europe/Bucharest", siteReference = "ST-1", instructions = null,
                teams = listOf(DispatchTeamSnapshot("TEAM-1", "Team", listOf("tech-1"), listOf("tech-1"))),
                participants = listOf(DispatchTechnicianSnapshot("tech-1", "Technician")), leaderTechnicianIds = listOf("tech-1"),
                work = listOf(DispatchWork("ITEM-1", WorkSubjectType.SITE, taskName = "Inspect site", assignedTechnicians = listOf(DispatchTechnicianSnapshot("tech-1", "Technician")))),
            ),
        ),
    )

    private fun packageValue() = ServiceLoopSyncPackage(
        manifest = ServiceLoopSyncManifest("sync-1", "B045 test workspace", "FULL_WORKSPACE", "2026-09-22T10:00:00Z", exporterId = exporterId),
        business = SyncBusinessProfile("Test business", "Test technician", "+40", "test@example.com", "Test street", "Europe/Bucharest"),
        register = SyncRegister(
            customers = listOf(SyncCustomer("customer-1", "CU-001", "Customer One", null, null, null, null, "STANDARD", "ACTIVE")),
            sites = listOf(SyncSite("site-1", "customer-1", "ST-001", "Main site", "Test address", null, null, null, null, true, "ACTIVE")),
            equipment = listOf(SyncEquipment("equipment-1", "site-1", "EQ-001", "unit-1", "Boiler", "Maker", "Model", "Serial", null, "ACTIVE")),
            customerContacts = listOf(SyncCustomerContact("contact-1", "customer-1", "office contact", "EMAIL", "office@example.com")),
        ),
        inspections = SyncInspections(listOf(SyncTemplate("template-1", "IT-001", "Annual inspection", "ACTIVE", 1, listOf(SyncTemplateItem(1, "Condition", "STATUS", null, true, null))))),
        plans = SyncPlans(listOf(SyncPlan("plan-1", "P-001", "equipment-1", "Annual service", 1, "YEARS", "2026-09-22", "template-1", "ACTIVE"))),
        team = SyncTeamDirectory(listOf(SyncTechnician("import-tech", "Imported technician", "Lead", "Technician note")), listOf(SyncTeam("team-1", "Imported team", listOf("import-tech"), listOf("import-tech")))),
        visits = SyncVisits(
            bookedVisits = listOf(SyncBookedVisit("visit-1", "V-001", "site-1", "2026-09-23", "09:30", "Europe/Bucharest", listOf(SyncBookedWork("work-1", "PLAN", "plan-1", null, "EQUIPMENT", null, null, null)))),
            dispatchDrafts = listOf(SyncDispatchDraft("dispatch-1", "M-001", "site-1", "2026-09-25", "08:00", "Europe/Bucharest", "Bring tools", listOf("team-1"), listOf(SyncDispatchItem("dispatch-item-1", "PLAN", "plan-1", listOf("import-tech"))))),
        ),
        followups = SyncFollowUps(
            followUps = listOf(SyncFollowUp("followup-1", "FU-001", "CONTACT", "Call customer", "2026-09-22", "OPEN", "customer-1", "site-1", null, "Private note")),
            contactNotes = listOf(SyncContactNote("note-1", "CN-001", "customer-1", "site-1", null, "EMAIL", "2026-09-19T08:00:00Z", "Sent details", null)),
        ),
    )

    private fun asLegacyV1(bytes: ByteArray): ByteArray {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        val manifest = JSONObject(entries.getValue("manifest.json").toString(Charsets.UTF_8))
            .put("formatVersion", 1)
            .also { it.remove("exporterId") }
        val sections = manifest.getJSONArray("sections")
        for (index in 0 until sections.length()) sections.getJSONObject(index).put("version", 1)
        entries["manifest.json"] = manifest.toString().toByteArray(Charsets.UTF_8)
        val register = JSONObject(entries.getValue("register.json").toString(Charsets.UTF_8)).also { it.remove("customerContacts") }
        entries["register.json"] = register.toString().toByteArray(Charsets.UTF_8)
        val team = JSONObject(entries.getValue("team.json").toString(Charsets.UTF_8))
        val technicians = team.getJSONArray("technicians")
        for (index in 0 until technicians.length()) technicians.getJSONObject(index).remove("notes")
        entries["team.json"] = team.toString().toByteArray(Charsets.UTF_8)
        return ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { zip -> entries.forEach { (name, data) -> zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() } } }.toByteArray()
    }
}
