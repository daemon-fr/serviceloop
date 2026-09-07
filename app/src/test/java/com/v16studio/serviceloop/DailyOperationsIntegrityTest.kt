package com.v16studio.serviceloop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.DraftWriteGate
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.domain.*
import java.io.File
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
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

    @Test fun bookedVisitCapturesLatestTemplateAtStartAndWorkingSnapshotsStayFrozen() = runTest {
        val ids = foundation(); repo.saveBusinessProfile(BusinessProfile("Service Co","Alex",zoneId="Europe/Bucharest")); val template = repo.createTemplate("Safety", listOf(TemplateItemDraft("Old wording", "STATUS", required = true))); repo.updatePlan(ids.plan, PlanInput("Annual service", 1, "YEARS", "2026-09-01", template))
        val firstVisit = repo.createVisit(listOf(ids.plan), "BOOKED", "2026-09-06", Instant.parse("2026-09-06T08:00:00Z").toEpochMilli()); val firstWork = db.serviceLoopDao().firstWorkItemId(firstVisit)!!
        val oldSnapshot=attachPreCorrectionBookedSnapshot(firstVisit,ids.plan,template)
        assertEquals("Old wording",repo.inspection(firstWork)!!.questions.single().label)
        repo.reviseTemplate(template, "Safety revised", listOf(TemplateItemDraft("New wording", "NUMBER", "bar", true))); repo.startVisit(firstVisit)
        assertEquals("New wording", repo.inspection(firstWork)!!.questions.single().label)
        repo.reviseTemplate(template, "Safety r3", listOf(TemplateItemDraft("Newest wording", "TEXT", required = true)))
        assertEquals("Old wording",db.serviceLoopDao().checklistItems(oldSnapshot).single().label); assertEquals("New wording", repo.inspection(firstWork)!!.questions.single().label); repo.saveResponse(firstWork,repo.inspection(firstWork)!!.questions.single().snapshotItemId,ResponseDisposition.VALUE,"2.5",null);repo.markChecklistReviewed(firstWork);repo.savePublicWork(firstWork,"Checked");repo.saveCompletionDraft(firstWork,"PERFORMED",false,null,null,null,null);repo.saveBusinessProfile(BusinessProfile("Service Co","Alex",zoneId="Europe/Bucharest"));val record=repo.finalRecord((repo.finalizeVisit(firstVisit) as FinalizeResult.Success).recordId)!!;assertEquals("New wording",record.public.lines.single().checklist.single().label); assertEquals(3, db.serviceLoopDao().reusableTemplateRevisions(template).size)
    }

    @Test fun preCorrectionBookedSnapshotAtSameRevisionIsReusedAtStart() = runTest {
        val ids=foundation(); val template=repo.createTemplate("Legacy booked",listOf(TemplateItemDraft("Revision one check","STATUS",required=true))); repo.updatePlan(ids.plan,PlanInput("Annual service",1,"YEARS","2026-09-01",template)); val visit=repo.createVisit(listOf(ids.plan),"BOOKED","2026-09-06",1); val work=db.serviceLoopDao().firstWorkItemId(visit)!!; val captured=db.serviceLoopDao().workItem(work)!!.capturedObligationId!!; val snapshot=attachPreCorrectionBookedSnapshot(visit,ids.plan,template); val snapshotCount=db.serviceLoopDao().templateSnapshotCount(); val itemCount=db.serviceLoopDao().checklistSnapshotItemCount()
        repo.startVisit(visit)
        assertEquals("WORKING",repo.visit(visit)!!.state); assertEquals(snapshot,db.serviceLoopDao().workItem(work)!!.templateSnapshotId); assertEquals(snapshotCount,db.serviceLoopDao().templateSnapshotCount()); assertEquals(itemCount,db.serviceLoopDao().checklistSnapshotItemCount()); assertEquals("Revision one check",repo.inspection(work)!!.questions.single().label); assertEquals(1,db.serviceLoopDao().templateSnapshot(snapshot)!!.revision); assertEquals(captured,db.serviceLoopDao().workItem(work)!!.capturedObligationId); assertEquals(1,db.serviceLoopDao().visitOwnsClaim(visit,captured))
    }

    @Test fun recordPastRecurringWorkIsHistoryOnlyAndFinalizationDoesNotTouchCurrentObligation() = runTest {
        val ids=foundation(); repo.saveBusinessProfile(BusinessProfile("Service Co","Alex",zoneId="Europe/Bucharest")); val before=db.serviceLoopDao().plan(ids.plan)!!
        val visit=repo.createVisit(listOf(ids.plan),"HISTORICAL","2026-08-01"); val work=db.serviceLoopDao().firstWorkItemId(visit)!!
        assertNull(repo.dueServices().single().claimedVisitId); assertNull(db.serviceLoopDao().workItem(work)!!.capturedObligationId)
        repo.savePublicWork(work,"Historical annual service"); repo.saveCompletionDraft(work,"PERFORMED",true,null,"2027-08-01",true,null)
        assertEquals(FulfillmentEligibility.HISTORY_ONLY,repo.completionLines(visit).single().fulfillmentEligibility); assertFalse(repo.completionLines(visit).single().fulfillsCurrentObligation)
        val record=repo.finalRecord((repo.finalizeVisit(visit) as FinalizeResult.Success).recordId)!!; val after=db.serviceLoopDao().plan(ids.plan)!!
        assertEquals(before.currentObligationId,after.currentObligationId); assertEquals(before.currentDueDate,after.currentDueDate); assertTrue(record.public.lines.single().historyOnly)
    }

    @Test fun obligationReplacementBeforeStartBlocksWithoutRetargeting() = runTest {
        val ids=foundation(); val visit=repo.createVisit(listOf(ids.plan),"BOOKED","2026-09-06",1); val captured=db.serviceLoopDao().visitWorkItems(visit).single().capturedObligationId!!
        db.serviceLoopDao().insertObligations(listOf(com.v16studio.serviceloop.data.ServiceObligationEntity("replacement",ids.plan,2,"2027-09-01",2))); db.serviceLoopDao().setCurrentObligationForTest(ids.plan,"replacement")
        assertTrue(runCatching{repo.startVisit(visit)}.isFailure); assertEquals("BOOKED",repo.visit(visit)!!.state); assertEquals(captured,db.serviceLoopDao().visitWorkItems(visit).single().capturedObligationId)
    }

    @Test fun finalizeWinningPreventsLaterPartMutation() = runTest {
        val ids=foundation(); repo.saveBusinessProfile(BusinessProfile("Service Co","Alex",zoneId="Europe/Bucharest")); val visit=repo.createVisit(listOf(ids.plan),"WORKING","2026-09-05"); val work=db.serviceLoopDao().firstWorkItemId(visit)!!; repo.savePublicWork(work,"Done"); repo.saveCompletionDraft(work,"PERFORMED",false,null,null,null,null)
        val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>(); val delayed=RoomServiceLoopRepository(db,time,DraftWriteGate{entered.complete(Unit);release.await()},attachmentRoot=root)
        val save=launch{assertTrue(runCatching{delayed.addPart(work,"Filter","1","pc")}.isFailure)}; entered.await(); assertTrue(repo.finalizeVisit(visit) is FinalizeResult.Success); release.complete(Unit); save.join(); assertTrue(repo.parts(work).isEmpty())
    }

    @Test fun cancelWinningPreventsStaleStartAndResolveWinningPreventsStaleFollowUpEdit() = runTest {
        val ids=foundation(); val visit=repo.createVisit(listOf(ids.plan),"BOOKED","2026-09-06",1); val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>(); val delayed=RoomServiceLoopRepository(db,time,DraftWriteGate{entered.complete(Unit);release.await()},attachmentRoot=root)
        val start=launch{assertTrue(runCatching{delayed.startVisit(visit)}.isFailure)}; entered.await(); repo.cancelVisit(visit,"Cancelled first"); release.complete(Unit); start.join(); assertEquals("CANCELLED",repo.visit(visit)!!.state)
        val follow=repo.createFollowUp(FollowUpInput("CONTACT","Call","2026-09-06",ids.customer)); val editEntered=CompletableDeferred<Unit>(); val editRelease=CompletableDeferred<Unit>(); val delayedEdit=RoomServiceLoopRepository(db,time,DraftWriteGate{editEntered.complete(Unit);editRelease.await()},attachmentRoot=root)
        val edit=launch{assertTrue(runCatching{delayedEdit.updateFollowUp(follow,"Changed","2026-09-06",""," ")}.isFailure)}; editEntered.await(); repo.changeFollowUpState(follow,"RESOLVED","Done"); editRelease.complete(Unit); edit.join(); assertEquals("RESOLVED",repo.followUp(follow)!!.state); assertEquals("Call",repo.followUp(follow)!!.title)
    }

    @Test fun startWinningPreventsStaleReschedule() = runTest {
        val ids=foundation(); val visit=repo.createVisit(listOf(ids.plan),"BOOKED","2026-09-06",1); val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>(); val delayed=RoomServiceLoopRepository(db,time,DraftWriteGate{entered.complete(Unit);release.await()},attachmentRoot=root)
        val reschedule=launch{assertTrue(runCatching{delayed.rescheduleVisit(visit,"2026-09-10",2,"Move")}.isFailure)}; entered.await(); repo.startVisit(visit); release.complete(Unit); reschedule.join(); assertEquals("WORKING",repo.visit(visit)!!.state)
    }

    @Test fun finalizeWinningPreventsLatePhotoAndPublicWorkWritesAndCleansOwnedFile() = runTest {
        suspend fun readyVisit():Pair<String,String>{val ids=foundation();repo.saveBusinessProfile(BusinessProfile("Service Co","Alex",zoneId="Europe/Bucharest"));val visit=repo.createVisit(listOf(ids.plan),"WORKING","2026-09-05");val work=db.serviceLoopDao().firstWorkItemId(visit)!!;repo.savePublicWork(work,"Original");repo.saveCompletionDraft(work,"PERFORMED",false,null,null,null,null);return visit to work}
        val (photoVisit,photoWork)=readyVisit(); val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>(); val delayed=RoomServiceLoopRepository(db,time,DraftWriteGate{entered.complete(Unit);release.await()},attachmentRoot=root)
        val photo=launch{assertTrue(runCatching{delayed.savePhoto(photoWork,testImageBytes(Color.MAGENTA),"late.png","image/png",true,"Late")}.isFailure)}; entered.await(); assertTrue(repo.finalizeVisit(photoVisit) is FinalizeResult.Success); release.complete(Unit); photo.join(); assertTrue(repo.photos(photoWork).isEmpty()); assertTrue(root.walkTopDown().filter{it.isFile}.none())
        val (workVisit,workItem)=readyVisit(); val workEntered=CompletableDeferred<Unit>(); val workRelease=CompletableDeferred<Unit>(); val delayedWork=RoomServiceLoopRepository(db,time,DraftWriteGate{workEntered.complete(Unit);workRelease.await()},attachmentRoot=root)
        val late=launch{assertTrue(runCatching{delayedWork.savePublicWork(workItem,"Late")}.isFailure)}; workEntered.await(); val record=(repo.finalizeVisit(workVisit) as FinalizeResult.Success).recordId; workRelease.complete(Unit); late.join(); assertEquals("Original",repo.finalRecord(record)!!.public.lines.single().publicWorkNote); assertEquals("Original",repo.inspection(workItem)!!.workPerformed)
    }

    @Test fun templateReorderPublishesNewRevisionWithoutChangingPriorOrder() = runTest {
        val template=repo.createTemplate("Order",listOf(TemplateItemDraft("First","STATUS"),TemplateItemDraft("Second","TEXT"))); val oldId=db.serviceLoopDao().reusableTemplate(template)!!.currentRevisionId
        repo.reviseTemplate(template,"Order",listOf(TemplateItemDraft("Second","TEXT"),TemplateItemDraft("First","STATUS"))); val current=db.serviceLoopDao().reusableTemplate(template)!!.currentRevisionId
        assertEquals(listOf("First","Second"),db.serviceLoopDao().reusableTemplateItems(oldId).map{it.label}); assertEquals(listOf("Second","First"),db.serviceLoopDao().reusableTemplateItems(current).map{it.label})
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

    @Test fun equipmentSearchIncludesMakeAndModel() = runTest { val ids=foundation(); repo.updateEquipment(ids.equipment,EquipmentInput("Compressor","C-01","Kaeser","Sigma 7","S1")); assertEquals(ids.equipment,repo.search("Kaeser").single().id); assertEquals(ids.equipment,repo.search("Sigma 7").single().id) }

    private data class Ids(val customer: String, val site: String, val equipment: String, val plan: String)
    private suspend fun attachPreCorrectionBookedSnapshot(visit:String,plan:String,template:String):String { val dao=db.serviceLoopDao(); val master=dao.reusableTemplate(template)!!; val revision=dao.reusableTemplateRevision(master.currentRevisionId)!!; val snapshot=stableTestId("template-snapshot",visit,plan,revision.id); dao.insertTemplateSnapshots(listOf(com.v16studio.serviceloop.data.TemplateSnapshotEntity(snapshot,master.id,revision.nameSnapshot,revision.revisionNumber,1))); dao.insertChecklistItems(dao.reusableTemplateItems(revision.id).map{item->com.v16studio.serviceloop.data.ChecklistItemSnapshotEntity(stableTestId("snapshot-item",snapshot,item.id),snapshot,item.position,item.label,item.responseType,item.unit,item.required,item.privateGuidance)}); val work=dao.firstWorkItemId(visit)!!; db.openHelper.writableDatabase.execSQL("UPDATE work_items SET templateSnapshotId=? WHERE id=?",arrayOf(snapshot,work)); return snapshot }
    private fun stableTestId(vararg parts:String)=UUID.nameUUIDFromBytes(parts.joinToString(":").toByteArray()).toString()
    private suspend fun foundation(): Ids { val customer=repo.createCustomer(CustomerInput("Acme Service Customer", "Dana")); val site=repo.createSite(customer, SiteInput("Main site", "1 Test Street")); val equipment=repo.createEquipment(site, EquipmentInput("Compressor", "C-01")); val plan=repo.createPlan(equipment, PlanInput("Annual service",1,"YEARS","2026-09-01")); return Ids(customer,site,equipment,plan) }
    private fun testImageBytes(color:Int):ByteArray { val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888); bitmap.eraseColor(color); return ByteArrayOutputStream().also{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray() }
}
