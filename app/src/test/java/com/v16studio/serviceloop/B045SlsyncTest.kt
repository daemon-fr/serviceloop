package com.v16studio.serviceloop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.domain.BusinessTime
import java.io.File
import java.time.Instant
import java.time.ZoneId
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class B045SlsyncTest {
    private lateinit var database: ServiceLoopDatabase
    private lateinit var attachmentRoot: File
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val time = object : BusinessTime {
        override val zoneId = ZoneId.of("Europe/Bucharest")
        override fun instant() = Instant.parse("2026-09-22T10:00:00Z")
    }

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        ServiceLoopDatabase.configureStage4Tracking(database.openHelper.writableDatabase)
        attachmentRoot = File(context.cacheDir, "b045-${System.nanoTime()}").apply { mkdirs() }
    }

    @After fun tearDown() { database.close(); attachmentRoot.deleteRecursively() }

    @Test fun codecRoundTripsTinyValidFullWorkspace() {
        val original = packageValue()
        val decoded = ServiceLoopSyncCodec.decode(ServiceLoopSyncCodec.encode(original))
        assertEquals(original, decoded)
        assertEquals(1, ServiceLoopSyncCodec.preview(decoded).counts.customers)
    }

    @Test fun initialImportReplacesBusinessRowsAndPreservesDeviceState() = runTest {
        val dao = database.serviceLoopDao()
        val identity = TechnicianIdentityEntity("primary", "device-tech", "Device Technician", 1L, 2L)
        database.dispatchDao().insertTechnicianIdentity(identity)
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

    private fun packageValue() = ServiceLoopSyncPackage(
        manifest = ServiceLoopSyncManifest("sync-1", "B045 test workspace", "FULL_WORKSPACE", "2026-09-22T10:00:00Z"),
        business = SyncBusinessProfile("Test business", "Test technician", "+40", "test@example.com", "Test street", "Europe/Bucharest"),
        register = SyncRegister(
            customers = listOf(SyncCustomer("customer-1", "CU-001", "Customer One", null, null, null, null, "STANDARD", "ACTIVE")),
            sites = listOf(SyncSite("site-1", "customer-1", "ST-001", "Main site", "Test address", null, null, null, null, true, "ACTIVE")),
            equipment = listOf(SyncEquipment("equipment-1", "site-1", "EQ-001", "unit-1", "Boiler", "Maker", "Model", "Serial", null, "ACTIVE")),
        ),
        inspections = SyncInspections(listOf(SyncTemplate("template-1", "IT-001", "Annual inspection", "ACTIVE", 1, listOf(SyncTemplateItem(1, "Condition", "STATUS", null, true, null))))),
        plans = SyncPlans(listOf(SyncPlan("plan-1", "P-001", "equipment-1", "Annual service", 1, "YEARS", "2026-09-22", "template-1", "ACTIVE"))),
        team = SyncTeamDirectory(listOf(SyncTechnician("import-tech", "Imported technician", "Lead")), listOf(SyncTeam("team-1", "Imported team", listOf("import-tech"), listOf("import-tech")))),
        visits = SyncVisits(
            bookedVisits = listOf(SyncBookedVisit("visit-1", "V-001", "site-1", "2026-09-23", "09:30", "Europe/Bucharest", listOf(SyncBookedWork("work-1", "PLAN", "plan-1", null, "EQUIPMENT", null, null, null)))),
            dispatchDrafts = listOf(SyncDispatchDraft("dispatch-1", "M-001", "site-1", "2026-09-25", "08:00", "Europe/Bucharest", "Bring tools", listOf("team-1"), listOf(SyncDispatchItem("dispatch-item-1", "PLAN", "plan-1", listOf("import-tech"))))),
        ),
        followups = SyncFollowUps(
            followUps = listOf(SyncFollowUp("followup-1", "FU-001", "CONTACT", "Call customer", "2026-09-22", "OPEN", "customer-1", "site-1", null, "Private note")),
            contactNotes = listOf(SyncContactNote("note-1", "CN-001", "customer-1", "site-1", null, "EMAIL", "2026-09-19T08:00:00Z", "Sent details", null)),
        ),
    )
}
