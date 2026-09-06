package com.v16studio.serviceloop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.domain.*
import java.io.File
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DailyOperationsIntegrityTest {
    private lateinit var db: ServiceLoopDatabase
    private lateinit var root: File
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
    private val repo get() = RoomServiceLoopRepository(db, time, attachmentRoot = root)

    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build(); root = File(context.cacheDir, "sl3-${System.nanoTime()}").apply { mkdirs() } }
    @After fun close() { db.close(); root.deleteRecursively() }

    @Test fun directoryCreationAndEditRetainStableIdentityAndPlanCreatesExactlyOneObligation() = runTest {
        val customer = repo.createCustomer(CustomerInput("Acme Plant", "Dana", "+40 700", "dana@example.invalid", "PRIVATE_CUSTOMER"))
        val site = repo.createSite(customer, SiteInput("North workshop", "1 Test Street", privateAccessNote = "PRIVATE_ACCESS"))
        val equipment = repo.createEquipment(site, EquipmentInput("Air compressor", "AC-01", "Maker", "M2", "SER-9", "PRIVATE_EQUIPMENT"))
        val plan = repo.createPlan(equipment, PlanInput("Quarterly service", 3, "MONTHS", "2026-09-01"))
        val original = repo.customer(customer)!!; repo.updateCustomer(customer, CustomerInput("Acme Plant Updated", "Dana", "+40 700", "dana@example.invalid", "PRIVATE_CUSTOMER"))
        assertEquals(original.id, repo.customer(customer)!!.id); assertEquals("Acme Plant Updated", repo.customer(customer)!!.name)
        assertEquals(1, db.serviceLoopDao().obligationCount(plan)); assertEquals(db.serviceLoopDao().plan(plan)!!.currentDueDate, db.serviceLoopDao().obligation(db.serviceLoopDao().plan(plan)!!.currentObligationId!!)!!.dueDate)
    }

    @Test fun dueDateEditUpdatesSameObligationWithoutConsumingIt() = runTest {
        val ids = foundation(); val before = db.serviceLoopDao().plan(ids.plan)!!; repo.updatePlan(ids.plan, PlanInput("Annual service", 1, "YEARS", "2026-10-10", dueDateChangeReason = "Customer requested schedule change")); val after = db.serviceLoopDao().plan(ids.plan)!!
        assertEquals(before.currentObligationId, after.currentObligationId); assertEquals(1, db.serviceLoopDao().obligationCount(ids.plan)); assertNull(db.serviceLoopDao().obligation(after.currentObligationId!!)!!.consumedAtEpochMillis); assertEquals("2026-10-10", db.serviceLoopDao().obligation(after.currentObligationId!!)!!.dueDate)
    }

    @Test fun reusableTemplatePublishesAppendOnlyRevisionAndWorkingSnapshotsStayFrozen() = runTest {
        val ids = foundation(); val template = repo.createTemplate("Safety", listOf(TemplateItemDraft("Old wording", "STATUS", required = true))); repo.updatePlan(ids.plan, PlanInput("Annual service", 1, "YEARS", "2026-09-01", template))
        val firstVisit = repo.createVisit(listOf(ids.plan), "BOOKED", "2026-09-06", Instant.parse("2026-09-06T08:00:00Z").toEpochMilli()); val firstWork = db.serviceLoopDao().firstWorkItemId(firstVisit)!!
        repo.reviseTemplate(template, "Safety revised", listOf(TemplateItemDraft("New wording", "NUMBER", "bar", true))); repo.cancelVisit(firstVisit, "Customer unavailable")
        val secondVisit = repo.createVisit(listOf(ids.plan), "WORKING", "2026-09-05"); val secondWork = db.serviceLoopDao().firstWorkItemId(secondVisit)!!
        assertEquals("Old wording", repo.inspection(firstWork)!!.questions.single().label); assertEquals("New wording", repo.inspection(secondWork)!!.questions.single().label); assertEquals(2, db.serviceLoopDao().reusableTemplateRevisions(template).size)
    }

    @Test fun bookingClaimsAreExclusiveAndCancellationReleasesWithoutChangingDueDate() = runTest {
        val ids = foundation(); val dueBefore = db.serviceLoopDao().plan(ids.plan)!!.currentDueDate; val visit = repo.createVisit(listOf(ids.plan), "BOOKED", "2026-09-06", 1)
        runCatching { repo.createVisit(listOf(ids.plan), "BOOKED", "2026-09-07", 2) }.onSuccess { fail("Expected exclusive claim") }
        repo.rescheduleVisit(visit, "2026-09-09", 3, "Customer requested another day"); assertEquals(dueBefore, db.serviceLoopDao().plan(ids.plan)!!.currentDueDate)
        repo.cancelVisit(visit, "Customer unavailable"); assertEquals(dueBefore, db.serviceLoopDao().plan(ids.plan)!!.currentDueDate); assertNull(repo.dueServices().single().claimedVisitId)
        assertEquals(setOf("RESCHEDULED", "CANCELLED"), db.serviceLoopDao().visitScheduleEvents(visit).map { it.eventType }.toSet())
        assertNotNull(repo.createVisit(listOf(ids.plan), "BOOKED", "2026-09-10", 4))
    }

    @Test fun concurrentBookingProducesOnlyOneActiveClaim() = runTest {
        val ids = foundation(); val outcomes = listOf(async { runCatching { repo.createVisit(listOf(ids.plan), "BOOKED", "2026-09-06", 1) } }, async { runCatching { repo.createVisit(listOf(ids.plan), "BOOKED", "2026-09-07", 2) } }).awaitAll()
        assertEquals(1, outcomes.count { it.isSuccess }); assertEquals(1, repo.dueServices().count { it.claimedVisitId != null })
    }

    @Test fun oneVisitRejectsPlansFromDifferentSites() = runTest {
        val first = foundation(); val secondSite = repo.createSite(first.customer, SiteInput("Other site", "2 Test Street")); val secondEquipment = repo.createEquipment(secondSite, EquipmentInput("Pump")); val secondPlan = repo.createPlan(secondEquipment, PlanInput("Pump service", 6, "MONTHS", "2026-09-02"))
        val failure = runCatching { repo.createVisit(listOf(first.plan, secondPlan), "WORKING", "2026-09-05") }.exceptionOrNull(); assertTrue(failure is IllegalArgumentException); assertEquals(0, db.serviceLoopDao().visitCount())
    }

    @Test fun equipmentEditPreservesSeparateMasterFieldsAndPrivateNote() = runTest {
        val ids=foundation(); repo.updateEquipment(ids.equipment,EquipmentInput("Compressor","C-02","Maker One","Model With Spaces","SER-2","PRIVATE_KEEP")); val detail=repo.equipment(ids.equipment)!!
        assertEquals("Maker One",detail.make); assertEquals("Model With Spaces",detail.model); assertEquals("PRIVATE_KEEP",detail.privateNote)
        repo.updateEquipment(ids.equipment,EquipmentInput(detail.name,detail.technicianIdentifier.orEmpty(),detail.make,detail.model,detail.serialNumber.orEmpty(),detail.privateNote))
        assertEquals("PRIVATE_KEEP",repo.equipment(ids.equipment)!!.privateNote)
    }

    @Test fun bookedStartRequiresClaimStillOwnedByThatVisit() = runTest {
        val ids=foundation(); val visit=repo.createVisit(listOf(ids.plan),"BOOKED","2026-09-06",1); db.serviceLoopDao().releaseVisitClaims(visit)
        val failure=runCatching{repo.startVisit(visit)}.exceptionOrNull(); assertNotNull(failure); assertEquals("BOOKED",repo.visit(visit)!!.state)
    }

    @Test fun visitSetupCanPersistAOneOffOnlyVisitAtOneChosenSite() = runTest {
        val ids=foundation(); val visit=repo.createVisitForSite(ids.site,emptyList(),ids.equipment,"Emergency diagnosis","WORKING","2026-09-05")
        val line=repo.visit(visit)!!.lines.single(); assertEquals("Emergency diagnosis",line.serviceName); assertNull(line.dueDate); assertEquals(0,repo.dueServices().count{it.claimedVisitId==visit})
    }

    @Test fun multiMachineFinalizationAdvancesOnlyTheExplicitlyFulfilledLine() = runTest {
        val ids=foundation(); val equipmentB=repo.createEquipment(ids.site,EquipmentInput("Pump","P-02")); val planB=repo.createPlan(equipmentB,PlanInput("Pump service",1,"YEARS","2026-09-02")); repo.saveBusinessProfile(BusinessProfile("Service Co","Alex",zoneId="Europe/Bucharest"))
        val visit=repo.createVisit(listOf(ids.plan,planB),"WORKING","2026-09-05"); val lines=db.serviceLoopDao().visitWorkItems(visit).associateBy{it.servicePlanId}
        val a=lines.getValue(ids.plan).id; val b=lines.getValue(planB).id
        repo.savePublicWork(a,"Annual service complete"); repo.saveCompletionDraft(a,"PERFORMED",true,null,"2027-09-05",true,null)
        repo.savePublicWork(b,"Pump service partly completed"); repo.saveCompletionDraft(b,"PARTLY_PERFORMED",false,null,null,null,null)
        val oldB=db.serviceLoopDao().plan(planB)!!.currentObligationId; val result=repo.finalizeVisit(visit) as FinalizeResult.Success; val record=repo.finalRecord(result.recordId)!!
        assertEquals("2027-09-05",db.serviceLoopDao().plan(ids.plan)!!.currentDueDate); assertEquals(oldB,db.serviceLoopDao().plan(planB)!!.currentObligationId); assertNull(db.serviceLoopDao().obligation(oldB!!)!!.consumedAtEpochMillis)
        assertEquals(2,record.public.lines.size); assertEquals(setOf("Compressor","Pump"),record.public.lines.map{it.equipmentName}.toSet())
    }

    @Test fun contactAndFollowUpLifecycleHaveNoServiceSideEffects() = runTest {
        val ids = foundation(); val obligation = db.serviceLoopDao().plan(ids.plan)!!.currentObligationId; repo.createContactNote(ContactNoteInput(ids.customer, ids.site, ids.equipment, "CALL", "Spoke with Dana", "PRIVATE_CONTACT")); val follow = repo.createFollowUp(FollowUpInput("CONTACT", "Confirm access", "2026-09-05", ids.customer, ids.site, privatePlanningNote = "PRIVATE_FOLLOW"))
        repo.changeFollowUpState(follow, "RESOLVED", "Access confirmed"); repo.changeFollowUpState(follow, "OPEN", "Needs another call", "2026-09-12"); repo.changeFollowUpState(follow, "CANCELLED", "No longer required")
        assertEquals(obligation, db.serviceLoopDao().plan(ids.plan)!!.currentObligationId); assertNull(db.serviceLoopDao().obligation(obligation!!)!!.consumedAtEpochMillis); assertEquals("CANCELLED", repo.followUp(follow)!!.state)
    }

    @Test fun partsAndSelectedPhotosAreFrozenIntoFinalPublicSnapshot() = runTest {
        val ids = foundation(); repo.saveBusinessProfile(BusinessProfile("Service Co", "Alex", zoneId = "Europe/Bucharest")); val visit = repo.createVisit(listOf(ids.plan), "WORKING", "2026-09-05"); val work = db.serviceLoopDao().firstWorkItemId(visit)!!
        repo.savePublicWork(work, "Completed one-off checks"); repo.saveCompletionDraft(work, "PERFORMED", false, null, null, null, null); repo.addPart(work, "Filter", "2.50", "pcs"); repo.savePhoto(work, testImageBytes(Color.RED), "shown.png", "image/png", true, "Filter housing"); repo.savePhoto(work, testImageBytes(Color.BLUE), "private.png", "image/png", false, "PRIVATE_PHOTO")
        val record = repo.finalRecord((repo.finalizeVisit(visit) as FinalizeResult.Success).recordId)!!; val line = record.public.lines.single()
        assertEquals("2.5", line.parts.single().quantity); assertEquals(1, line.photos.size); assertEquals("Filter housing", line.photos.single().caption); assertFalse(record.public.toString().contains("PRIVATE_PHOTO")); assertTrue(File(root, line.photos.single().relativePath).isFile)
    }

    @Test fun failedPhotoMetadataWriteDoesNotLeaveAFalseSavedFile() = runTest {
        val ids=foundation(); val visit=repo.createVisit(listOf(ids.plan),"WORKING","2026-09-05"); val work=db.serviceLoopDao().firstWorkItemId(visit)!!
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_test_attachment BEFORE INSERT ON attachments BEGIN SELECT RAISE(ABORT, 'test rejection'); END")
        assertTrue(runCatching{repo.savePhoto(work,testImageBytes(Color.GREEN),"rejected.png","image/png",true,"Rejected")}.isFailure)
        assertTrue(repo.photos(work).isEmpty()); assertTrue(root.walkTopDown().filter{it.isFile}.none())
    }

    @Test fun missingSelectedPhotoBlocksFinalizationWithoutChangingVisitOrObligation() = runTest {
        val ids=foundation(); repo.saveBusinessProfile(BusinessProfile("Service Co","Alex",zoneId="Europe/Bucharest")); val visit=repo.createVisit(listOf(ids.plan),"WORKING","2026-09-05"); val work=db.serviceLoopDao().firstWorkItemId(visit)!!; repo.savePublicWork(work,"Completed checks"); repo.saveCompletionDraft(work,"PERFORMED",false,null,null,null,null); val photoId=repo.savePhoto(work,testImageBytes(Color.YELLOW),"selected.png","image/png",true,"Required evidence"); File(root,db.serviceLoopDao().attachment(photoId)!!.storedRelativePath).delete()
        assertTrue(repo.finalizeVisit(visit) is FinalizeResult.Blocked); assertEquals("WORKING",repo.visit(visit)!!.state); assertNull(db.serviceLoopDao().obligation(db.serviceLoopDao().plan(ids.plan)!!.currentObligationId!!)!!.consumedAtEpochMillis)
    }

    @Test fun searchReturnsDistinctRoutableEntityTypes() = runTest {
        val ids = foundation(); val visit = repo.createVisit(listOf(ids.plan), "BOOKED", "2026-09-06", 1); val follow = repo.createFollowUp(FollowUpInput("CONTACT", "Call Acme contact", "2026-09-05", ids.customer))
        assertEquals("CUSTOMER", repo.search("Acme").first { it.id == ids.customer }.type); assertEquals("EQUIPMENT", repo.search("Compressor").single().type); assertEquals("VISIT", repo.search(repo.visit(visit)!!.reference).single().type); assertEquals("FOLLOW_UP", repo.search("Call Acme").single { it.id == follow }.type); assertTrue(repo.search("no-such-record").isEmpty())
    }

    private data class Ids(val customer: String, val site: String, val equipment: String, val plan: String)
    private suspend fun foundation(): Ids { val customer=repo.createCustomer(CustomerInput("Acme Service Customer", "Dana")); val site=repo.createSite(customer, SiteInput("Main site", "1 Test Street")); val equipment=repo.createEquipment(site, EquipmentInput("Compressor", "C-01")); val plan=repo.createPlan(equipment, PlanInput("Annual service",1,"YEARS","2026-09-01")); return Ids(customer,site,equipment,plan) }
    private fun testImageBytes(color:Int):ByteArray { val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888); bitmap.eraseColor(color); return ByteArrayOutputStream().also{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray() }
}
