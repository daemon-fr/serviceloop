package com.v16studio.serviceloop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.ui.validateHistoryDates
import com.v16studio.serviceloop.ui.servicePlanDueLabel
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

    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build(); ServiceLoopDatabase.configureStage4Tracking(db.openHelper.writableDatabase); root = File(context.cacheDir, "sl3-${System.nanoTime()}").apply { mkdirs() } }
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

    @Test fun customerAndRequiredFirstSiteCommitAtomicallyWithInheritedContact() = runTest {
        val (customerId, siteId) = repo.createCustomerWithFirstSite(
            CustomerInput("Northside", "Mira", "+40 700", "mira@example.invalid"),
            SiteInput("Main plant", "18 Mill Lane", isDefault = true),
        )
        val customer = db.serviceLoopDao().customer(customerId)!!
        val site = db.serviceLoopDao().site(siteId)!!
        assertEquals(customerId, site.customerId)
        assertTrue(site.isDefault)
        assertNull(site.contactName)
        assertNull(site.phone)
        assertNull(site.email)
        assertEquals("Mira", customer.contactName)
        assertEquals("Mira",repo.site(siteId)!!.effectiveContactName)
        assertEquals("+40 700",repo.site(siteId)!!.effectivePhone)
        assertEquals("mira@example.invalid",repo.site(siteId)!!.effectiveEmail)
        repo.updateCustomer(customerId,CustomerInput("Northside","Updated customer","+40 711","updated@example.invalid"))
        assertEquals("Updated customer",repo.site(siteId)!!.effectiveContactName)
        assertEquals("+40 711",repo.site(siteId)!!.effectivePhone)
        assertEquals("updated@example.invalid",repo.site(siteId)!!.effectiveEmail)
        repo.updateSite(siteId,SiteInput("Main plant","18 Mill Lane","Site contact","+40 722","site@example.invalid",isDefault=true))
        assertFalse(repo.site(siteId)!!.usesCustomerContact)
        assertEquals("Site contact",repo.site(siteId)!!.effectiveContactName)
        assertEquals("+40 722",repo.site(siteId)!!.effectivePhone)
        assertEquals("site@example.invalid",repo.site(siteId)!!.effectiveEmail)
    }

    @Test fun equipmentDueLabelsUseBusinessDateAndSharedHorizonTruthfully() {
        val today = java.time.LocalDate.of(2026, 9, 10)
        assertEquals("Overdue", servicePlanDueLabel("2026-09-09", today, 14))
        assertEquals("Due today", servicePlanDueLabel("2026-09-10", today, 14))
        assertEquals("Due soon", servicePlanDueLabel("2026-09-24", today, 14))
        assertEquals("Upcoming", servicePlanDueLabel("2026-09-25", today, 14))
        assertEquals("State unavailable", servicePlanDueLabel("bad-date", today, 14))
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
        val start=launch{assertTrue(runCatching{delayed.startVisit(visit)}.isFailure)}; entered.await(); repo.cancelVisit(visit,"Cancelled first"); release.complete(Unit); start.join(); assertEquals("CANCELED",repo.visit(visit)!!.state)
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
        assertEquals(setOf("RESCHEDULED", "CANCELED"), db.serviceLoopDao().visitScheduleEvents(visit).map { it.eventType }.toSet())
        assertNotNull(repo.createVisit(listOf(ids.plan), "BOOKED", "2026-09-10", 4))
    }

    @Test fun cancelledBookingRestoresSameVisitAndClaimWithoutChangingDueDate() = runTest {
        val ids=foundation(); val due=db.serviceLoopDao().plan(ids.plan)!!.currentDueDate; val visit=repo.createVisit(listOf(ids.plan),"BOOKED","2026-09-06",1); val obligation=db.serviceLoopDao().visitWorkItems(visit).single().capturedObligationId!!
        repo.cancelVisit(visit,"Mistake"); repo.restoreVisit(visit,"2026-09-06")
        assertEquals("BOOKED",repo.visit(visit)!!.state); assertEquals(1,db.serviceLoopDao().visitOwnsClaim(visit,obligation)); assertEquals(due,db.serviceLoopDao().plan(ids.plan)!!.currentDueDate); assertEquals(listOf("CANCELED","RESTORED"),db.serviceLoopDao().visitScheduleEvents(visit).map{it.eventType})
    }

    @Test fun restoreRejectsStaleOrAlreadyClaimedObligationAtomically() = runTest {
        val ids=foundation(); val visit=repo.createVisit(listOf(ids.plan),"BOOKED","2026-09-06",1); repo.cancelVisit(visit,"Mistake"); val old=db.serviceLoopDao().visitWorkItems(visit).single().capturedObligationId!!
        db.serviceLoopDao().insertObligations(listOf(com.v16studio.serviceloop.data.ServiceObligationEntity("replacement",ids.plan,2,"2027-09-01",2))); db.serviceLoopDao().setCurrentObligationForTest(ids.plan,"replacement")
        assertTrue(runCatching{repo.restoreVisit(visit,"2026-09-06")}.isFailure); assertEquals("CANCELED",repo.visit(visit)!!.state); assertNull(db.serviceLoopDao().claimForObligation(old))
    }

    @Test fun oneOffOnlyCancelledVisitRestoresWithoutClaims() = runTest {
        val ids=foundation(); val visit=repo.createVisitForSite(ids.site,emptyList(),ids.equipment,"Emergency","BOOKED","2026-09-06",1); repo.cancelVisit(visit,"Mistake"); repo.restoreVisit(visit,"2026-09-06")
        assertEquals("BOOKED",repo.visit(visit)!!.state); assertTrue(db.serviceLoopDao().visitWorkItems(visit).all{it.capturedObligationId==null})
    }

    @Test fun restoreRejectsConsumedOrInactivePlanAndConcurrentAttemptsLeaveOneClaim() = runTest {
        suspend fun cancelled(): Pair<Ids,String> { val ids=foundation(); val visit=repo.createVisit(listOf(ids.plan),"BOOKED","2026-09-06",1); repo.cancelVisit(visit,"Mistake"); return ids to visit }
        val (consumedIds, consumedVisit)=cancelled(); val obligation=db.serviceLoopDao().visitWorkItems(consumedVisit).single().capturedObligationId!!; db.serviceLoopDao().consumeObligation(obligation,consumedIds.plan,2,"revision"); assertTrue(runCatching { repo.restoreVisit(consumedVisit,"2026-09-06") }.isFailure); assertEquals("CANCELED",repo.visit(consumedVisit)!!.state)
        val (inactiveIds,inactiveVisit)=cancelled(); db.serviceLoopDao().updatePlan(db.serviceLoopDao().plan(inactiveIds.plan)!!.copy(state="INACTIVE")); assertTrue(runCatching { repo.restoreVisit(inactiveVisit,"2026-09-06") }.isFailure); assertEquals("CANCELED",repo.visit(inactiveVisit)!!.state)
        val (raceIds,raceVisit)=cancelled(); val attempts=listOf(async { runCatching{repo.restoreVisit(raceVisit,"2026-09-06")} },async { runCatching{repo.restoreVisit(raceVisit,"2026-09-06")} }).awaitAll(); assertEquals(1,attempts.count{it.isSuccess}); val claimed=db.serviceLoopDao().visitWorkItems(raceVisit).single().capturedObligationId!!; assertEquals(1,db.serviceLoopDao().visitOwnsClaim(raceVisit,claimed)); assertEquals("BOOKED",repo.visit(raceVisit)!!.state)
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

    @Test fun correctionAppendsImmutableRevisionAndTextOnlyChangeDoesNotAdvanceSchedule() = runTest {
        val ids = foundation(); val recordId = finalizedRecord(ids); val beforePlan = db.serviceLoopDao().plan(ids.plan)!!
        val original = repo.finalRecord(recordId)!!; val draft = repo.openCorrection(recordId)
        repo.saveCorrection(draft.copy(reason = "Correct captured customer spelling", customerName = "Acme Service Customer SRL"))
        val correctedRevision = repo.commitCorrection(recordId); val current = repo.finalRecord(recordId)!!; val versions = repo.recordVersions(recordId).first
        assertEquals(2, versions.size); assertEquals(correctedRevision, current.public.revisionId); assertEquals("Acme Service Customer SRL", current.public.customerName)
        assertEquals(original.public.customerName, db.serviceLoopDao().finalRevision(original.public.revisionId)!!.customerName)
        assertEquals(original.public.revisionId, db.serviceLoopDao().finalRevision(correctedRevision)!!.supersedesRevisionId)
        assertEquals(beforePlan.currentDueDate, db.serviceLoopDao().plan(ids.plan)!!.currentDueDate)
        assertTrue(repo.history(HistoryQuery(search = "spelling")).any { it.eventKind == "CORRECTION" })
    }

    @Test fun correctionDraftIsDurableAndVoidingPreservesIssuedVersions() = runTest {
        val ids = foundation(); val recordId = finalizedRecord(ids); val draft = repo.openCorrection(recordId)
        repo.saveCorrection(draft.copy(reason = "Saved for later")); assertEquals(AttentionKind.CORRECTION_DRAFT, repo.attention().single().kind)
        assertEquals(draft.id, repo.openCorrection(recordId).id); assertTrue(repo.discardCorrection(recordId))
        assertTrue(repo.voidRecord(recordId, "Issued for the wrong service visit", "Operator verification")); assertFalse(repo.voidRecord(recordId, "duplicate", null))
        val record = repo.finalRecord(recordId)!!; assertTrue(record.voided); assertEquals("Issued for the wrong service visit", record.publicVoidReason)
        assertEquals(1, repo.recordVersions(recordId).first.size); assertTrue(repo.history(HistoryQuery(type = HistoryType.CHANGES)).any { it.eventKind == "VOID" })
    }

    @Test fun correctionCanReplaceCapturedChecklistPartsAndSelectedEvidenceWithoutMutatingOriginal() = runTest {
        val ids = foundation(); repo.saveBusinessProfile(BusinessProfile("Service Co","Alex",zoneId="Europe/Bucharest")); val template = repo.createTemplate("Captured", listOf(TemplateItemDraft("Original question", "STATUS", required = true))); repo.updatePlan(ids.plan, PlanInput("Annual service",1,"YEARS","2026-09-01",template))
        val visit = repo.createVisit(listOf(ids.plan), "WORKING", "2026-09-05"); val work = db.serviceLoopDao().firstWorkItemId(visit)!!; val question = repo.inspection(work)!!.questions.single()
        repo.saveResponse(work, question.snapshotItemId, ResponseDisposition.OK, null, null); repo.markChecklistReviewed(work); repo.savePublicWork(work,"Original work"); repo.saveCompletionDraft(work,"PERFORMED",false,null,null,null,null); repo.addPart(work,"Old part","1","pc"); repo.savePhoto(work,testImageBytes(Color.RED),"old.png","image/png",true,"Old evidence")
        val recordId = (repo.finalizeVisit(visit) as FinalizeResult.Success).recordId; val original = repo.finalRecord(recordId)!!; val draft = repo.openCorrection(recordId); val line = draft.items.single(); val check = line.checklist.single()
        repo.addCorrectionEvidence(recordId, line.id, testImageBytes(Color.BLUE), "new.png", "image/png", "Correction proof")
        val enriched = repo.openCorrection(recordId); val enrichedLine = enriched.items.single()
        repo.saveCorrection(enriched.copy(reason="Correct captured content", publicNote="Customer-visible correction note", items=listOf(enrichedLine.copy(checklist=listOf(check.copy(disposition="ISSUE_FOUND", reason="Corrected public finding")), parts=listOf(CorrectionPartDraft(null,"New part","2","pcs")), photos=enrichedLine.photos.map { if (it.addedInCorrection) it else it.copy(selected=false) }))))
        repo.commitCorrection(recordId); val corrected = repo.finalRecord(recordId)!!.public.lines.single()
        assertEquals("ISSUE_FOUND", corrected.checklist.single().disposition); assertEquals("Corrected public finding", corrected.checklist.single().reason); assertEquals("New part", corrected.parts.single().description); assertEquals("Customer-visible correction note", repo.finalRecord(recordId)!!.public.publicNote); assertTrue(corrected.photos.single().addedInCorrection); assertNotNull(corrected.photos.single().addedAtEpochMillis)
        assertEquals("OK", db.serviceLoopDao().finalChecklistItems(original.public.lines.single().let { db.serviceLoopDao().finalWorkItems(original.public.revisionId).single().id }).single().disposition)
    }

    @Test fun correctionEvidenceIsNormalizedAndOpenDraftBackupRoundTripsWithDraftOwnership() = runTest {
        val ids = foundation(); val recordId = finalizedRecord(ids); val draft = repo.openCorrection(recordId); val line = draft.items.single()
        val raw = testImageBytes(Color.BLUE) + "PRIVATE_SOURCE_TRAILER".toByteArray()
        repo.addCorrectionEvidence(recordId, line.id, raw, "raw.png", "image/png", "Draft proof")
        val evidence = db.serviceLoopDao().attachmentsForOwner("CORRECTION_DRAFT", draft.id).single(); val stored = File(root, evidence.storedRelativePath)
        assertTrue(stored.isFile); assertFalse(stored.readBytes().contentEquals(raw)); assertFalse(stored.readText(Charsets.ISO_8859_1).contains("PRIVATE_SOURCE_TRAILER")); assertEquals(stored.length(), evidence.byteSize)
        val password = "draft evidence backup".toCharArray(); val inspection = repo.inspectBackup(repo.createBackup(password).bytes, password)
        repo.erase(true, "ERASE"); repo.restoreBackup(inspection, "REPLACE", false)
        val restored = db.serviceLoopDao().attachmentsForOwner("CORRECTION_DRAFT", draft.id).single()
        assertEquals(evidence.id, restored.id); assertTrue(File(root, restored.storedRelativePath).isFile); assertEquals(draft.id, repo.openCorrection(recordId).id)
    }

    @Test fun discardedCorrectionEvidenceRemovesMetadataFileDirectoryAndStaysAbsentFromBackup() = runTest {
        val ids = foundation(); val recordId = finalizedRecord(ids); val draft = repo.openCorrection(recordId); val line = draft.items.single()
        repo.addCorrectionEvidence(recordId, line.id, testImageBytes(Color.GREEN), "discard.png", "image/png", null)
        val evidence = db.serviceLoopDao().attachmentsForOwner("CORRECTION_DRAFT", draft.id).single(); val file = File(root, evidence.storedRelativePath); val directory = file.parentFile!!
        assertTrue(repo.discardCorrection(recordId)); assertNull(db.serviceLoopDao().attachment(evidence.id)); assertFalse(file.exists()); assertFalse(directory.exists())
        val password = "discarded evidence backup".toCharArray(); val inspection = repo.inspectBackup(repo.createBackup(password).bytes, password)
        repo.erase(true, "ERASE"); repo.restoreBackup(inspection, "REPLACE", false)
        assertNull(db.serviceLoopDao().attachment(evidence.id)); assertFalse(File(root, evidence.storedRelativePath).exists())
    }

    @Test fun committedCorrectionEvidenceGetsRevisionOwnershipAndRoundTripsSelectedAndUnselectedPolicy() = runTest {
        val ids = foundation(); val recordId = finalizedRecord(ids); val draft = repo.openCorrection(recordId); val line = draft.items.single()
        repo.addCorrectionEvidence(recordId, line.id, testImageBytes(Color.BLUE), "selected.png", "image/png", "Selected")
        repo.addCorrectionEvidence(recordId, line.id, testImageBytes(Color.GREEN), "internal.png", "image/png", "Internal")
        val enriched = repo.openCorrection(recordId); val added = enriched.items.single().photos.filter { it.addedInCorrection }
        repo.saveCorrection(enriched.copy(reason = "Add corrected evidence", items = listOf(enriched.items.single().copy(photos = enriched.items.single().photos.map { if (it.sourceId == added.last().sourceId) it.copy(selected = false) else it }))))
        val revisionId = repo.commitCorrection(recordId); val owned = db.serviceLoopDao().attachmentsForOwner("FINAL_REVISION", revisionId)
        assertEquals(2, owned.size); assertTrue(owned.single { it.id == added.first().sourceId }.includedInCustomerReport); assertFalse(owned.single { it.id == added.last().sourceId }.includedInCustomerReport)
        val finalPhoto = db.serviceLoopDao().finalPhotos(db.serviceLoopDao().finalWorkItems(revisionId).single().id).single { it.addedInCorrection }
        assertEquals(added.first().sourceId, finalPhoto.sourceAttachmentId); assertTrue(db.serviceLoopDao().attachmentsForOwner("CORRECTION_DRAFT", draft.id).isEmpty())
        val password = "committed evidence backup".toCharArray(); val inspection = repo.inspectBackup(repo.createBackup(password).bytes, password)
        repo.erase(true, "ERASE"); repo.restoreBackup(inspection, "REPLACE", false)
        assertEquals(2, db.serviceLoopDao().attachmentsForOwner("FINAL_REVISION", revisionId).size); assertEquals(finalPhoto.sourceAttachmentId, db.serviceLoopDao().finalPhotos(db.serviceLoopDao().finalWorkItems(revisionId).single().id).single { it.addedInCorrection }.sourceAttachmentId)
    }

    @Test fun backupInspectionRejectsAuthenticatedStructuralAndCurrentIdentityDamageWithoutMutatingLiveData() = runTest {
        val ids = foundation(); val password = "structural validation".toCharArray(); val dao = db.serviceLoopDao(); val visit = repo.createVisit(listOf(ids.plan), "BOOKED", "2026-09-06", 1); val work = dao.firstWorkItemId(visit)!!
        db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys=OFF")
        db.openHelper.writableDatabase.execSQL("UPDATE work_items SET equipmentId='missing-equipment' WHERE id=?", arrayOf(work))
        val dangling = repo.createBackup(password).bytes
        db.openHelper.writableDatabase.execSQL("UPDATE work_items SET equipmentId=? WHERE id=?", arrayOf(ids.equipment, work)); db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys=ON")
        assertTrue(runCatching { repo.inspectBackup(dangling, password) }.isFailure); assertEquals(ids.equipment, dao.workItem(work)!!.equipmentId); assertTrue(root.listFiles().orEmpty().none { it.name.startsWith("recovery-validation-") })
        val originalObligation = dao.plan(ids.plan)!!.currentObligationId!!
        db.openHelper.writableDatabase.execSQL("UPDATE service_plans SET currentObligationId='wrong-obligation' WHERE id=?", arrayOf(ids.plan))
        val wrongCurrent = repo.createBackup(password).bytes
        db.openHelper.writableDatabase.execSQL("UPDATE service_plans SET currentObligationId=? WHERE id=?", arrayOf(originalObligation, ids.plan))
        assertTrue(runCatching { repo.inspectBackup(wrongCurrent, password) }.isFailure); assertEquals(originalObligation, dao.plan(ids.plan)!!.currentObligationId)
        repo.cancelVisit(visit, "Clear validation booking"); val recordId = finalizedRecord(ids); val originalRevision = dao.finalRecord(recordId)!!.currentRevisionId
        db.openHelper.writableDatabase.execSQL("UPDATE final_records SET currentRevisionId='wrong-revision' WHERE id=?", arrayOf(recordId)); val wrongRevision = repo.createBackup(password).bytes
        db.openHelper.writableDatabase.execSQL("UPDATE final_records SET currentRevisionId=? WHERE id=?", arrayOf(originalRevision, recordId))
        assertTrue(runCatching { repo.inspectBackup(wrongRevision, password) }.isFailure); assertEquals(originalRevision, dao.finalRecord(recordId)!!.currentRevisionId)
    }

    @Test fun historyDateValidationNeverAppliesMalformedOrReversedRanges() {
        assertNull(validateHistoryDates("not-a-date", "").from); assertNotNull(validateHistoryDates("not-a-date", "").fromError)
        assertNull(validateHistoryDates("", "2026-99-99").to); assertNotNull(validateHistoryDates("", "2026-99-99").toError)
        val reversed = validateHistoryDates("2026-09-10", "2026-09-01"); assertNull(reversed.from); assertNull(reversed.to); assertNotNull(reversed.fromError)
        val valid = validateHistoryDates("2026-09-01", "2026-09-10"); assertEquals("2026-09-01", valid.from); assertEquals("2026-09-10", valid.to)
        val cleared = validateHistoryDates("", ""); assertNull(cleared.from); assertNull(cleared.to); assertNull(cleared.fromError); assertNull(cleared.toError)
    }

    @Test fun correctionFollowUpEffectsAreExplicitAndAtomic() = runTest {
        val ids = foundation(); repo.saveBusinessProfile(BusinessProfile("Service Co","Alex",zoneId="Europe/Bucharest")); val visit=repo.createVisit(listOf(ids.plan),"WORKING","2026-09-05"); val work=db.serviceLoopDao().firstWorkItemId(visit)!!; val follow=repo.createCorrectiveFollowUp(work,"Inspect leak","2026-09-10",""); repo.savePublicWork(work,"Done"); repo.saveCompletionDraft(work,"PERFORMED",false,null,null,null,null); val record=(repo.finalizeVisit(visit) as FinalizeResult.Success).recordId
        val draft=repo.openCorrection(record); repo.saveCorrection(draft.copy(reason="Finding entered incorrectly", followUps=draft.followUps.map { it.copy(action="CANCEL", cancellationReason="Not actually required") }, newFollowUps=listOf(CorrectionNewFollowUpDraft("Return with gauge","2026-09-12"))))
        repo.commitCorrection(record); assertEquals("CANCELLED",repo.followUp(follow)!!.state); assertTrue(repo.followUps().any { it.title=="Return with gauge" && it.state=="OPEN" })
    }

    @Test fun latestCountedCorrectionAndVoidReconcileScheduleAndProvenance() = runTest {
        val ids=foundation(); val record=finalizedRecord(ids); val correction=repo.openCorrection(record); val changed=correction.items.single().copy(fulfilledObligation=false)
        repo.saveCorrection(correction.copy(reason="Did not fulfill scheduled service",scheduleAcknowledged=true,items=listOf(changed))); repo.commitCorrection(record)
        val afterCorrection=db.serviceLoopDao().plan(ids.plan)!!; assertEquals("2026-09-01",afterCorrection.currentDueDate); assertNull(afterCorrection.lastCountedRevisionId)
        val nextRecord=finalizedRecord(ids); val currentRevision=repo.finalRecord(nextRecord)!!.public.revisionId; assertEquals(currentRevision,db.serviceLoopDao().plan(ids.plan)!!.lastCountedRevisionId)
        repo.voidRecord(nextRecord,"Service was attributed in error",null); val afterVoid=db.serviceLoopDao().plan(ids.plan)!!; assertEquals("2026-09-01",afterVoid.currentDueDate); assertNull(afterVoid.lastCountedRevisionId)
    }

    @Test fun recordsPackageContainsAdoptedReadableOwnershipTables() = runTest {
        val ids=foundation(); finalizedRecord(ids); val bytes=repo.recordsCsvPackage(true,false,true,ids.customer); val names=mutableSetOf<String>(); java.util.zip.ZipInputStream(bytes.inputStream()).use { zip -> while(true){ val entry=zip.nextEntry?:break; names+=entry.name } }
        assertTrue(names.containsAll(setOf("customers.csv","sites.csv","equipment.csv","service_plans.csv","visits.csv","work_items.csv","checklist_answers.csv","findings.csv","parts.csv","followups.csv","contact_notes.csv","changes.csv","report_index.csv","attachment_index.csv","README.txt")))
    }

    @Test fun encryptedBackupRejectsWrongPassphraseAndRestoresAfterExplicitErase() = runTest {
        foundation(); val password = "correct horse battery".toCharArray(); val backup = repo.createBackup(password)
        assertTrue(backup.complete); assertFalse(backup.bytes.toString(Charsets.ISO_8859_1).contains("Acme Service Customer"))
        assertTrue(runCatching { repo.inspectBackup(backup.bytes, "wrong password value".toCharArray()) }.isFailure)
        val damaged = backup.bytes.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertTrue(runCatching { repo.inspectBackup(damaged, password) }.isFailure)
        val inspection = repo.inspectBackup(backup.bytes, password); repo.erase(true, "ERASE"); assertEquals(0, repo.datasetSummary().customers)
        repo.restoreBackup(inspection, "REPLACE", false); assertEquals(1, repo.datasetSummary().customers)
    }

    @Test fun sameDatasetRestoreRemovesOldOnlyBusinessFiles() = runTest {
        val ids = foundation(); val password = "same dataset restore".toCharArray(); val backup = repo.createBackup(password); val inspection = repo.inspectBackup(backup.bytes, password)
        repo.updateCustomer(ids.customer, CustomerInput("Changed after backup", "Dana"))
        val oldOnly = File(root, "reports/old-only/private.pdf").apply { parentFile!!.mkdirs(); writeText("private old bytes") }
        repo.restoreBackup(inspection, "REPLACE", false)
        assertEquals("Acme Service Customer", repo.customer(ids.customer)!!.name)
        assertFalse(oldOnly.exists())
    }

    @Test fun restoreCrashMatrixUsesAdoptionTokenAndAlwaysKeepsOneCoherentSide() = runTest {
        val ids = foundation(); val password = "restore crash matrix".toCharArray(); val backup = repo.createBackup(password); val inspection = repo.inspectBackup(backup.bytes, password)
        val beforeCommit = setOf(RecoveryPackage.FailurePoint.BEFORE_FILE_ADOPTION, RecoveryPackage.FailurePoint.DURING_FILE_ADOPTION, RecoveryPackage.FailurePoint.BEFORE_DB_TRANSACTION, RecoveryPackage.FailurePoint.DURING_DB_TRANSACTION)
        val points = beforeCommit + setOf(RecoveryPackage.FailurePoint.AFTER_DB_COMMIT, RecoveryPackage.FailurePoint.AFTER_COMMITTED_JOURNAL)
        points.forEach { point ->
            repo.updateCustomer(ids.customer, CustomerInput("Live $point", "Dana"))
            val liveFile = File(root, "attachments/live-$point.bin").apply { parentFile!!.mkdirs(); writeText("live") }
            val packageWithFailure = RecoveryPackage(db, root) { reached -> if (reached == point) error("injected $point") }
            assertTrue(runCatching { packageWithFailure.restore(inspection) }.isFailure)
            assertNotEquals(RecoveryPackage.RecoveryResult.RESTRICTED, RecoveryPackage.recoverInterrupted(db, root))
            if (point in beforeCommit) { assertEquals("Live $point", repo.customer(ids.customer)!!.name); assertTrue(liveFile.exists()) }
            else { assertEquals("Acme Service Customer", repo.customer(ids.customer)!!.name); assertFalse(liveFile.exists()) }
        }
    }

    @Test fun malformedRecoveryJournalEntersRestrictedStateInsteadOfOpeningSilently() = runTest {
        foundation(); repo.datasetSummary(); File(root, "recovery").mkdirs(); File(root, "recovery/restore-journal.json").writeText("not-json")
        assertEquals(RecoveryPackage.RecoveryResult.RESTRICTED, RecoveryPackage.recoverInterrupted(db, root))
        assertTrue(db.serviceLoopDao().recoveryMetadata()!!.restrictedRecoveryState)
    }

    @Test fun explicitValidatedReplacementCanResolveRestrictedDamagedJournal() = runTest {
        foundation(); val password = "restricted replacement".toCharArray(); val backup = repo.createBackup(password); val inspection = repo.inspectBackup(backup.bytes, password)
        File(root, "recovery").mkdirs(); File(root, "recovery/restore-journal.json").writeText("not-json"); assertEquals(RecoveryPackage.RecoveryResult.RESTRICTED, RecoveryPackage.recoverInterrupted(db, root))
        repo.restoreBackup(inspection, "REPLACE", false)
        assertFalse(repo.datasetSummary().restrictedRecoveryState); assertEquals(1, repo.datasetSummary().customers); assertFalse(File(root, "recovery").exists())
    }

    @Test fun incompleteRestorePersistsMissingAttachmentAvailability() = runTest {
        val ids = foundation(); val visit = repo.createVisit(listOf(ids.plan), "WORKING", "2026-09-05"); val work = db.serviceLoopDao().firstWorkItemId(visit)!!
        val attachment = repo.savePhoto(work, testImageBytes(Color.BLUE), "evidence.png", "image/png", true, "Evidence")
        val stored = db.serviceLoopDao().attachment(attachment)!!; File(root, stored.storedRelativePath).delete()
        val password = "incomplete recovery".toCharArray(); val backup = repo.createBackup(password, true); assertFalse(backup.complete)
        repo.restoreBackup(repo.inspectBackup(backup.bytes, password), "REPLACE", true)
        assertEquals("MISSING", db.serviceLoopDao().attachment(attachment)!!.availability)
        assertTrue(db.serviceLoopDao().recoveryMetadata()!!.restoredFromIncompleteCopy)
    }

    @Test fun eraseFileFailureRollsBackDatabaseAndPrivateFiles() = runTest {
        foundation(); val privateFile=File(root,"attachments/private.bin").apply { parentFile!!.mkdirs(); writeText("private") }
        val failing=RecoveryPackage(db,root){ if(it==RecoveryPackage.FailurePoint.DURING_ERASE_FILES) error("delete failure") }
        assertTrue(runCatching { failing.eraseDatabaseAndOwnedFiles(UUID.randomUUID().toString()) }.isFailure)
        assertEquals(1,repo.datasetSummary().customers); assertTrue(privateFile.isFile); assertFalse(repo.datasetSummary().restrictedRecoveryState)
    }

    @Test fun verifiedSnapshotTracksBusinessWritesButNotRecoveryMetadataWrites() = runTest {
        val ids=foundation(); val password="business dirty state".toCharArray(); val first=repo.createBackup(password); repo.recordVerifiedBackup(first,"test destination"); assertFalse(repo.datasetSummary().changedSinceBackup)
        repo.setBackupReminder(30); assertFalse(repo.datasetSummary().changedSinceBackup)
        repo.updateCustomer(ids.customer,CustomerInput("Changed business name","Dana")); assertTrue(repo.datasetSummary().changedSinceBackup)
        val second=repo.createBackup(password); repo.recordVerifiedBackup(second,"test destination"); assertFalse(repo.datasetSummary().changedSinceBackup)
    }

    @Test fun completeBackupRoundTripsCombinedSl4DispatchAndWorkingResponseDraftState() = runTest {
        val ids=foundation();val recordId=finalizedRecord(ids);val correction=repo.openCorrection(recordId)
        val template=repo.createTemplate("Integrated inspection",listOf(TemplateItemDraft("Guard condition","STATUS",required=true)))
        val due=db.serviceLoopDao().plan(ids.plan)!!.currentDueDate;repo.updatePlan(ids.plan,PlanInput("Annual service",1,"YEARS",due,template))
        val workingVisit=repo.createVisit(listOf(ids.plan),"WORKING","2026-09-06");val work=db.serviceLoopDao().firstWorkItemId(workingVisit)!!;val question=repo.inspection(work)!!.questions.single()
        repo.saveResponse(work,question.snapshotItemId,ResponseDisposition.ISSUE_FOUND,null,"Guard cracked")
        repo.saveResponse(work,question.snapshotItemId,ResponseDisposition.NOT_APPLICABLE,null,"Machine isolated")

        val dispatch=DispatchPackageService(db,root);val identity=dispatch.identity();dispatch.importTechnician(identity)
        val teamId=dispatch.createTeam("Integrated team").also{dispatch.setTeamMember(it,identity.technicianId,true,true)}
        val outbox=dispatch.createOutboxVisit("INTEGRATED-OUTBOX",ids.site,"2026-09-10","09:00","Europe/Bucharest","Bring guard",listOf(teamId))
        dispatch.addOutboxItem(outbox,ids.equipment,"Inspect guard",null,null,listOf(identity.technicianId));dispatch.createExportFile(listOf(outbox),"Service office",root)
        val technician=DispatchTechnicianSnapshot(identity.technicianId,identity.name);val team=DispatchTeamSnapshot("REMOTE-TEAM","Remote team",listOf(identity.technicianId),emptyList())
        val packageValue=DispatchPackage("INTEGRATED-PACKAGE",Instant.parse("2026-09-05T12:00:00Z").toString(),"Service office",listOf(DispatchCustomer("CU-REMOTE","Remote customer")),listOf(DispatchSite("ST-REMOTE","CU-REMOTE","Remote site",null)),listOf(DispatchEquipment("EQ-REMOTE","ST-REMOTE","Remote pump",null,null,null,null)),listOf(DispatchVisit("DV-INTEGRATED",1,"REMOTE-JOB","2026-09-11",null,"Europe/Bucharest","ST-REMOTE","Remote instructions",listOf(team),listOf(technician),emptyList(),listOf(DispatchWork("ITEM-INTEGRATED","EQ-REMOTE","Inspect",assignedTechnicians=listOf(technician))))))
        val importedVisit=dispatch.import(dispatch.preview(packageValue)).createdVisitIds.single()

        val password="integrated recovery state".toCharArray();val inspection=repo.inspectBackup(repo.createBackup(password).bytes,password)
        repo.erase(true,"ERASE");assertNull(db.serviceLoopDao().finalRecord(recordId));assertNull(db.dispatchDao().visitBinding("DV-INTEGRATED"));assertNull(db.dispatchDao().technicianIdentity());assertTrue(db.dispatchDao().teams().isEmpty());assertTrue(db.dispatchDao().outboxVisits().isEmpty())
        repo.restoreBackup(inspection,"REPLACE",false)

        assertEquals(correction.id,db.serviceLoopDao().correctionDraftForRecord(recordId)!!.id);assertEquals(recordId,repo.finalRecord(recordId)!!.public.recordId)
        val restoredResponse=repo.inspection(work)!!.questions.single();assertEquals(ResponseDisposition.NOT_APPLICABLE,restoredResponse.disposition);assertEquals("Machine isolated",restoredResponse.reason);assertEquals("Guard cracked",restoredResponse.issueFoundReasonDraft);assertEquals("Machine isolated",restoredResponse.notApplicableReasonDraft)
        assertEquals(identity.technicianId,dispatch.identity().technicianId);assertTrue(dispatch.teams().single{it.team.id==teamId}.members.single().second);assertEquals(DispatchOutboxStatus.DISPATCHED,dispatch.outboxVisits().single{it.dispatchVisitId==outbox}.outboxStatus)
        assertEquals(importedVisit,db.dispatchDao().visitBinding("DV-INTEGRATED")!!.localVisitId);db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use{assertEquals(0,it.count)}
        val restoredCheckpoint=repo.createBackup(password);repo.recordVerifiedBackup(restoredCheckpoint,"integrated test");assertFalse(repo.datasetSummary().changedSinceBackup)
        repo.saveResponse(work,question.snapshotItemId,ResponseDisposition.OK,null,null);assertTrue(repo.datasetSummary().changedSinceBackup)
        val responseCheckpoint=repo.createBackup(password);repo.recordVerifiedBackup(responseCheckpoint,"integrated test");assertFalse(repo.datasetSummary().changedSinceBackup)
        dispatch.concludeOutboxVisits(listOf(outbox));assertTrue(repo.datasetSummary().changedSinceBackup)
    }

    @Test fun directoryCsvEscapesSpreadsheetFormulasAndImportIsIdempotent() = runTest {
        repo.createCustomer(CustomerInput("=2+2", "Dana")); val csv = repo.directoryCsv(includeInactive = true, includePrivate = false).toString(Charsets.UTF_8)
        assertTrue(csv.contains("\"'=2+2\"")); val preview = repo.validateDirectoryCsv(csv.toByteArray())
        assertEquals(preview.rows.toString(), 0, preview.errors); val result = repo.importDirectory(preview); assertTrue(result.existingUnchanged >= 1)
        assertEquals(1, repo.datasetSummary().customers)
    }

    private data class Ids(val customer: String, val site: String, val equipment: String, val plan: String)
    private suspend fun finalizedRecord(ids: Ids): String { repo.saveBusinessProfile(BusinessProfile("Service Co", "Alex", zoneId = "Europe/Bucharest")); val visit=repo.createVisit(listOf(ids.plan),"WORKING","2026-09-05"); val work=db.serviceLoopDao().firstWorkItemId(visit)!!; repo.savePublicWork(work,"Annual service completed"); repo.saveCompletionDraft(work,"PERFORMED",true,null,"2027-09-05",true,null); return (repo.finalizeVisit(visit) as FinalizeResult.Success).recordId }
    private suspend fun attachPreCorrectionBookedSnapshot(visit:String,plan:String,template:String):String { val dao=db.serviceLoopDao(); val master=dao.reusableTemplate(template)!!; val revision=dao.reusableTemplateRevision(master.currentRevisionId)!!; val snapshot=stableTestId("template-snapshot",visit,plan,revision.id); dao.insertTemplateSnapshots(listOf(com.v16studio.serviceloop.data.TemplateSnapshotEntity(snapshot,master.id,revision.nameSnapshot,revision.revisionNumber,1))); dao.insertChecklistItems(dao.reusableTemplateItems(revision.id).map{item->com.v16studio.serviceloop.data.ChecklistItemSnapshotEntity(stableTestId("snapshot-item",snapshot,item.id),snapshot,item.position,item.label,item.responseType,item.unit,item.required,item.privateGuidance)}); val work=dao.firstWorkItemId(visit)!!; db.openHelper.writableDatabase.execSQL("UPDATE work_items SET templateSnapshotId=? WHERE id=?",arrayOf(snapshot,work)); return snapshot }
    private fun stableTestId(vararg parts:String)=UUID.nameUUIDFromBytes(parts.joinToString(":").toByteArray()).toString()
    private suspend fun foundation(): Ids { val customer=repo.createCustomer(CustomerInput("Acme Service Customer", "Dana")); val site=repo.createSite(customer, SiteInput("Main site", "1 Test Street")); val equipment=repo.createEquipment(site, EquipmentInput("Compressor", "C-01")); val plan=repo.createPlan(equipment, PlanInput("Annual service",1,"YEARS","2026-09-01")); return Ids(customer,site,equipment,plan) }
    private fun testImageBytes(color:Int):ByteArray { val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888); bitmap.eraseColor(color); return ByteArrayOutputStream().also{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray() }
}
