package com.v16studio.serviceloop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.domain.*
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class B026FlexibleWorkTest {
    private lateinit var db: ServiceLoopDatabase
    private lateinit var root: File
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
    private val repo get() = RoomServiceLoopRepository(db, time, attachmentRoot = root)

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        ServiceLoopDatabase.configureStage4Tracking(db.openHelper.writableDatabase)
        root = File(context.cacheDir, "b026-${System.nanoTime()}").apply { mkdirs() }
    }

    @After fun close() { db.close(); root.deleteRecursively() }

    @Test fun standardPlanCreationRemainsAllowedAndOneTimePlanCreationIsRejected() = runTest {
        val ids = seedBranch()
        assertNotNull(repo.createPlan(ids.equipment, PlanInput("Annual", 1, "YEARS", "2026-09-01")))
        db.serviceLoopDao().updateCustomer(db.serviceLoopDao().customer(ids.customer)!!.copy(customerType = CustomerType.ONE_TIME.code))
        val beforePlans = db.serviceLoopDao().planCount()
        val failure = runCatching { repo.createPlan(ids.equipment, PlanInput("Blocked", 1, "YEARS", "2026-10-01")) }.exceptionOrNull()
        assertEquals("Recurring service requires a Standard customer", failure?.message)
        assertEquals(beforePlans, db.serviceLoopDao().planCount())
    }

    @Test fun updatePlanDefensivelyRejectsOneTimeOwnerWithoutMutation() = runTest {
        val ids = seedBranch(); val plan = repo.createPlan(ids.equipment, PlanInput("Annual", 1, "YEARS", "2026-09-01")); val before = db.serviceLoopDao().plan(plan)!!
        db.serviceLoopDao().updateCustomer(db.serviceLoopDao().customer(ids.customer)!!.copy(customerType = CustomerType.ONE_TIME.code))
        val failure = runCatching { repo.updatePlan(plan, PlanInput("Changed", 2, "MONTHS", "2026-10-01", dueDateChangeReason = "test")) }.exceptionOrNull()
        assertEquals("Recurring service requires a Standard customer", failure?.message)
        assertEquals(before, db.serviceLoopDao().plan(plan))
    }

    @Test fun equipmentSummariesCarryTheirOwningCustomerTypeAcrossAllProjections() = runTest {
        val ids = seedOneTimeBranch()

        assertEquals(CustomerType.ONE_TIME, repo.equipmentList().single { it.id == ids.equipment }.customerType)
        assertEquals(CustomerType.ONE_TIME, repo.customer(ids.customer)!!.equipment.single().customerType)
        assertEquals(CustomerType.ONE_TIME, repo.site(ids.site)!!.equipment.single().customerType)
        val visitSite = repo.visitSites().single { it.id == ids.site }
        assertEquals(CustomerType.ONE_TIME, visitSite.customerType)
        assertEquals(CustomerType.ONE_TIME, visitSite.equipment.single().customerType)
    }

    @Test fun equipmentWithPlansCannotMoveToOneTimeCustomerWithoutAnyMutation() = runTest {
        val source = seedBranch()
        val planId = repo.createPlan(source.equipment, PlanInput("Annual", 1, "YEARS", "2026-09-01"))
        val beforePlan = db.serviceLoopDao().plan(planId)!!
        val beforeObligation = db.serviceLoopDao().obligation(beforePlan.currentObligationId!!)!!
        val destination = seedOneTimeBranch()

        assertEquals(CustomerType.ONE_TIME, repo.moveReview(source.equipment).destinations.single { it.id == destination.site }.customerType)
        val failure = runCatching { repo.moveEquipment(source.equipment, destination.site, "2026-09-05", "Not allowed", acknowledged = true) }.exceptionOrNull()

        assertEquals("Recurring service requires a Standard customer", failure?.message)
        assertEquals(source.site, db.serviceLoopDao().equipment(source.equipment)!!.siteId)
        assertEquals(beforePlan, db.serviceLoopDao().plan(planId))
        assertEquals(beforeObligation, db.serviceLoopDao().obligation(beforeObligation.id))
        assertEquals(0, db.serviceLoopDao().equipmentMoveCount(source.equipment))
        assertTrue(db.serviceLoopDao().allChangeEntries().none { it.subjectId == source.equipment && it.changeType in setOf("MOVE", "MOVE_IN") })
    }

    @Test fun equipmentWithoutPlansMayMoveToOneTimeCustomer() = runTest {
        val source = seedBranch()
        val destination = seedOneTimeBranch()

        assertTrue(repo.moveEquipment(source.equipment, destination.site, "2026-09-05", "Ad-hoc move", acknowledged = true))

        assertEquals(destination.site, db.serviceLoopDao().equipment(source.equipment)!!.siteId)
        assertEquals(1, db.serviceLoopDao().equipmentMoveCount(source.equipment))
        assertEquals(2, db.serviceLoopDao().allChangeEntries().count { it.subjectId == source.equipment && it.changeType in setOf("MOVE", "MOVE_IN") })
    }

    @Test fun siteAndUnidentifiedEquipmentFinalizeWithoutRecurrenceOrFakeEquipment() = runTest {
        val siteIds = seedBranch(); val siteVisit = seedVisit("site-visit", siteIds); val siteWork = seedWork(siteVisit, siteIds, WorkSubjectType.SITE)
        val siteRecord = finalize(siteVisit, siteWork); val siteFinal = db.serviceLoopDao().finalWorkItems(db.serviceLoopDao().finalRecord(siteRecord)!!.currentRevisionId).single()
        assertEquals(WorkSubjectType.SITE.code, siteFinal.subjectType); assertNull(siteFinal.equipmentId); assertNull(siteFinal.equipmentName); assertNull(siteFinal.equipmentReference); assertFalse(siteFinal.fulfilledObligation)
        val unknownVisit = seedVisit("unknown-visit", siteIds); val unknownWork = seedWork(unknownVisit, siteIds, WorkSubjectType.EQUIPMENT, description = "Copy machine beside back-office desk")
        val unknownRecord = finalize(unknownVisit, unknownWork); val unknownFinal = db.serviceLoopDao().finalWorkItems(db.serviceLoopDao().finalRecord(unknownRecord)!!.currentRevisionId).single()
        assertEquals(WorkSubjectType.EQUIPMENT.code, unknownFinal.subjectType); assertNull(unknownFinal.equipmentId); assertEquals("Copy machine beside back-office desk", unknownFinal.equipmentDescription); assertFalse(unknownFinal.fulfilledObligation)
        assertTrue(db.serviceLoopDao().allPlans().isEmpty())
    }

    @Test fun malformedSiteAndUnidentifiedRecurringRowsAreRejectedAtFinalization() = runTest {
        val ids = seedBranch(); val visit = seedVisit("invalid-visit", ids)
        val invalid = WorkItemEntity("invalid", visit, ids.equipment, null, null, null, "Equipment", "EQ-1", "Invalid site", null, null, null, null, false, "PERFORMED", false, subjectType = WorkSubjectType.SITE.code)
        db.serviceLoopDao().insertWorkItems(listOf(invalid)); db.serviceLoopDao().insertPublicDrafts(listOf(WorkItemPublicDraftEntity("invalid", "Done"))); db.serviceLoopDao().insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity("invalid", "")))
        assertTrue(runCatching { repo.finalizeVisit(visit) }.isFailure)
        val unknown = WorkItemEntity("unknown-invalid", visit, null, "plan", null, null, null, null, "Invalid recurrence", "P-1", "2026-09-01", 1, "YEARS", false, "PERFORMED", false, subjectType = WorkSubjectType.EQUIPMENT.code)
        assertTrue(runCatching { WorkSubjectValidator.validateWorkItem(unknown, CustomerType.STANDARD, ids.equipment) }.isFailure)
    }

    @Test fun correctionAndCorrectiveFollowUpPreserveNonEquipmentSubjects() = runTest {
        val ids = seedBranch(); val visit = seedVisit("correction-visit", ids); val work = seedWork(visit, ids, WorkSubjectType.SITE); val record = finalize(visit, work)
        val follow = repo.createCorrectiveFollowUp(work, "Return to inspect", "2026-09-10", "")
        assertNull(db.serviceLoopDao().followUp(follow)!!.equipmentId); assertEquals(ids.customer, db.serviceLoopDao().followUp(follow)!!.customerId); assertEquals(ids.site, db.serviceLoopDao().followUp(follow)!!.siteId)
        val correction = repo.openCorrection(record); repo.saveCorrection(correction.copy(reason = "Correct the note", items = correction.items.map { it.copy(publicWorkNote = "Corrected note") })); val revision = repo.commitCorrection(record)
        val corrected = db.serviceLoopDao().finalWorkItems(revision).single(); assertEquals(WorkSubjectType.SITE.code, corrected.subjectType); assertNull(corrected.equipmentId); assertNull(corrected.equipmentName); assertNull(corrected.equipmentReference)
    }

    @Test fun publicProjectionAndHistoryKeepAllThreeSubjectFormsTruthful() = runTest {
        val ids = seedBranch(); val visit = seedVisit("mixed-visit", ids)
        val site = seedWork(visit, ids, WorkSubjectType.SITE, id = "site-line"); val unknown = seedWork(visit, ids, WorkSubjectType.EQUIPMENT, id = "unknown-line", description = "Exterior shutter"); val known = seedWork(visit, ids, WorkSubjectType.EQUIPMENT, id = "known-line", equipmentId = ids.equipment)
        listOf(site, unknown, known).forEach { repo.savePublicWork(it, "Work $it"); repo.saveCompletionDraft(it, "PERFORMED", false, null, null, null, null) }
        val record = (repo.finalizeVisit(visit) as FinalizeResult.Success).recordId; val detail = repo.finalRecord(record)!!; val lines = detail.public.lines
        assertEquals(setOf(WorkSubjectType.SITE, WorkSubjectType.EQUIPMENT), lines.map { it.subjectType }.toSet()); assertTrue(lines.any { it.equipmentDescription == "Exterior shutter" && it.equipmentReference == null }); assertTrue(lines.any { it.equipmentReference == "EQ-1" && it.equipmentName == "Equipment" }); assertTrue(lines.any { it.subjectType == WorkSubjectType.SITE && it.equipmentName == null && it.equipmentReference == null && it.equipmentIdentification == null })
        val history = repo.history(HistoryQuery(scope = HistoryScope(HistoryScopeType.SITE, ids.site), type = HistoryType.SERVICE_RECORDS)); assertEquals(3, history.count { it.routeType == "RECORD" }); assertEquals(1, history.count { it.equipmentId == ids.equipment }); assertEquals(2, history.count { it.equipmentId == null })
    }

    @Test fun directoryAndRecordsExportsCarryB026Truth() = runTest {
        val ids = seedBranch(); db.serviceLoopDao().updateCustomer(db.serviceLoopDao().customer(ids.customer)!!.copy(customerType = CustomerType.ONE_TIME.code)); val directory = repo.directoryCsv(true, false).toString(Charsets.UTF_8); assertTrue(directory.lineSequence().first().contains("customer_type")); assertTrue(directory.contains("\"ONE_TIME\"")); assertEquals(0, repo.validateDirectoryCsv(directory.toByteArray()).errors)
        val visit = seedVisit("csv-visit", ids)
        val siteWork = seedWork(visit, ids, WorkSubjectType.SITE, id = "csv-site")
        val unknownWork = seedWork(visit, ids, WorkSubjectType.EQUIPMENT, id = "csv-unknown", description = "Unregistered unit")
        val knownWork = seedWork(visit, ids, WorkSubjectType.EQUIPMENT, id = "csv-known", equipmentId = ids.equipment)
        listOf(siteWork, unknownWork, knownWork).forEach { work -> repo.savePublicWork(work, "Completed $work"); repo.saveCompletionDraft(work, "PERFORMED", false, null, null, null, null) }
        db.serviceLoopDao().upsertBusinessProfile(BusinessProfileEntity(businessName = "Business", technicianName = "Technician", phone = null, email = null, postalAddress = null, zoneId = "Europe/Bucharest", modifiedAtEpochMillis = 1))
        val record = (repo.finalizeVisit(visit) as FinalizeResult.Success).recordId
        val bytes = repo.recordsCsvPackage(true, false, true, ids.customer)
        val workRows = csvRows(bytes, "work_items.csv")
        val finalRows = csvRows(bytes, "final_work_items.csv")
        assertCsvSubject(workRows, "SITE", equipmentId = "", equipmentDescription = "")
        assertCsvSubject(workRows, "EQUIPMENT", equipmentId = "", equipmentDescription = "Unregistered unit")
        assertCsvSubject(workRows, "EQUIPMENT", equipmentId = ids.equipment, equipmentDescription = "")
        assertCsvSubject(finalRows, "SITE", equipmentId = "", equipmentDescription = "")
        assertCsvSubject(finalRows, "EQUIPMENT", equipmentId = "", equipmentDescription = "Unregistered unit")
        assertCsvSubject(finalRows, "EQUIPMENT", equipmentId = ids.equipment, equipmentDescription = "")
        assertTrue(finalRows.single { it["equipment_id"] == ids.equipment }["equipment_reference"] == "EQ-1")
        assertNotNull(repo.finalRecord(record))
    }

    @Test fun currentRecoveryRoundTripPreservesOneTimeAndFlexibleFinalSubjects() = runTest {
        val ids = seedBranch(); db.serviceLoopDao().updateCustomer(db.serviceLoopDao().customer(ids.customer)!!.copy(customerType = CustomerType.ONE_TIME.code))
        val siteVisit = seedVisit("recovery-site", ids); val siteWork = seedWork(siteVisit, ids, WorkSubjectType.SITE); finalize(siteVisit, siteWork)
        val unknownVisit = seedVisit("recovery-unknown", ids); val unknownWork = seedWork(unknownVisit, ids, WorkSubjectType.EQUIPMENT, description = "Unregistered unit"); finalize(unknownVisit, unknownWork)
        val recovery = RecoveryPackage(db, root); val passphrase = "b026 recovery passphrase".toCharArray(); val backup = recovery.create(passphrase, false); val inspection = recovery.inspect(backup.bytes, passphrase)
        recovery.eraseDatabaseAndOwnedFiles("replacement-dataset"); recovery.restore(inspection)
        assertEquals(CustomerType.ONE_TIME.code, db.serviceLoopDao().customer(ids.customer)!!.customerType)
        assertTrue(db.serviceLoopDao().finalWorkItems(db.serviceLoopDao().finalRecord( db.serviceLoopDao().allFinalRecords().first { it.visitId == siteVisit }.id)!!.currentRevisionId).single().subjectType == WorkSubjectType.SITE.code)
        assertEquals("Unregistered unit", db.serviceLoopDao().finalWorkItems(db.serviceLoopDao().finalRecord(db.serviceLoopDao().allFinalRecords().first { it.visitId == unknownVisit }.id)!!.currentRevisionId).single().equipmentDescription)
    }

    private data class Branch(val customer: String, val site: String, val equipment: String)

    private suspend fun seedBranch(): Branch {
        db.serviceLoopDao().insertCustomers(listOf(CustomerEntity("c", "CU-1", "Customer")))
        db.serviceLoopDao().insertSites(listOf(SiteEntity("s", "c", "ST-1", "Site", null, null, isDefault = true)))
        db.serviceLoopDao().insertEquipment(listOf(EquipmentEntity("e", "s", "EQ-1", "ID-1", "Equipment", "Maker", "Model", "Serial", null)))
        return Branch("c", "s", "e")
    }

    private suspend fun seedOneTimeBranch(): Branch {
        db.serviceLoopDao().insertCustomers(listOf(CustomerEntity("one-time-c", "CU-OT", "One-time Customer", customerType = CustomerType.ONE_TIME.code)))
        db.serviceLoopDao().insertSites(listOf(SiteEntity("one-time-s", "one-time-c", "ST-OT", "One-time Site", null, null, isDefault = true)))
        db.serviceLoopDao().insertEquipment(listOf(EquipmentEntity("one-time-e", "one-time-s", "EQ-OT", "ID-OT", "One-time Equipment", "Maker", "Model", "Serial", null)))
        return Branch("one-time-c", "one-time-s", "one-time-e")
    }

    private suspend fun seedVisit(id: String, branch: Branch): String {
        db.serviceLoopDao().insertVisits(listOf(WorkingVisitEntity(id, "V-$id", branch.customer, branch.site, "2026-09-05", "Customer", "Site", null, "WORKING", 1, "CU-1", "ST-1", "Business", "Technician", null, null, null, "Europe/Bucharest")))
        return id
    }

    private suspend fun seedWork(visit: String, branch: Branch, subject: WorkSubjectType, id: String = "work-$visit", equipmentId: String? = null, description: String? = null): String {
        val known = equipmentId != null
        val item = WorkItemEntity(id, visit, equipmentId, null, null, null, if (known) "Equipment" else null, if (known) "EQ-1" else null, "Service", null, null, null, null, false, null, false, subjectType = subject.code, equipmentDescriptionSnapshot = description)
        db.serviceLoopDao().insertWorkItems(listOf(item)); db.serviceLoopDao().insertPublicDrafts(listOf(WorkItemPublicDraftEntity(id, ""))); db.serviceLoopDao().insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity(id, "")))
        return id
    }

    private suspend fun finalize(visit: String, work: String): String {
        repo.savePublicWork(work, "Completed")
        repo.saveCompletionDraft(work, "PERFORMED", false, null, null, null, null)
        db.serviceLoopDao().upsertBusinessProfile(BusinessProfileEntity(businessName = "Business", technicianName = "Technician", phone = null, email = null, postalAddress = null, zoneId = "Europe/Bucharest", modifiedAtEpochMillis = 1))
        return when (val result = repo.finalizeVisit(visit)) { is FinalizeResult.Success -> result.recordId; is FinalizeResult.Blocked -> error("Finalize blocked: ${result.message}") }
    }

    private fun assertCsvSubject(rows: List<Map<String, String>>, subjectType: String, equipmentId: String, equipmentDescription: String) {
        assertTrue(rows.any { it["subject_type"] == subjectType && it["equipment_id"] == equipmentId && it["equipment_description"] == equipmentDescription })
    }

    private fun csvRows(bytes: ByteArray, entryName: String): List<Map<String, String>> {
        val lines = zipEntry(bytes, entryName).lineSequence().filter(String::isNotBlank).map { line -> line.removePrefix("\"").removeSuffix("\"").split("\",\"") }.toList()
        val headers = lines.first()
        return lines.drop(1).map { values -> headers.zip(values).toMap() }
    }

    private fun zipEntry(bytes: ByteArray, entryName: String): String = ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.name == entryName) return zip.readBytes().toString(Charsets.UTF_8)
        }
        error("ZIP entry missing: $entryName")
    }
}
