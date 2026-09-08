package com.v16studio.serviceloop

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.ui.reportShareEligible
import com.v16studio.serviceloop.ui.reportShareIntent
import java.time.Instant
import java.time.Clock
import java.time.ZoneId
import com.v16studio.serviceloop.domain.ClockBusinessTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class DispatchPackageTest {
    private lateinit var db:ServiceLoopDatabase
    private lateinit var service:DispatchPackageService
    @Before fun setup(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),ServiceLoopDatabase::class.java).allowMainThreadQueries().build();service=DispatchPackageService(db)}
    @After fun close(){db.close()}

    private suspend fun pkg(generation:Int=1,date:String="2026-09-12",assignedToSelf:Boolean=true,dispatchId:String="DV-1",itemId:String="ITEM-1"):DispatchPackage {
        val self=service.identity();val tech=DispatchTechnicianSnapshot(self.technicianId,self.name);val team=DispatchTeamSnapshot("TEAM-1","Field",listOf(self.technicianId),emptyList())
        return DispatchPackage("PKG-${generation}-${date}",Instant.parse("2026-09-08T10:00:00Z").toString(),"Central Service Team",listOf(DispatchCustomer("CU-D","Dispatch Customer")),listOf(DispatchSite("ST-D","CU-D","Dispatch Site","1 Test Road")),listOf(DispatchEquipment("EQ-D","ST-D","Pump","P-1","Maker","Model","S-1")),listOf(DispatchVisit(dispatchId,generation,"JOB-1",date,"09:30","Europe/Bucharest","ST-D","Ring office",listOf(team),listOf(tech),emptyList(),listOf(DispatchWork(itemId,"EQ-D","Annual service",assignedTechnicians=if(assignedToSelf)listOf(tech)else emptyList())))))
    }

    @Test fun identityIsStableAndRenameKeepsOpaqueId()=runTest{val first=service.identity();val second=service.identity();assertEquals(first,second);val renamed=service.renameIdentity("Maria");assertEquals(first.technicianId,renamed.technicianId);assertEquals("Maria",renamed.name);assertEquals(renamed,TechnicianIdentityCodec.decode(TechnicianIdentityCodec.encode(renamed)))}
    @Test fun validV2RoundTripPreservesTimezoneAssignmentsAndReadableData()=runTest{val value=pkg();val decoded=DispatchPackageCodec.decode(DispatchPackageCodec.encode(value));assertEquals(value,decoded);assertEquals("Europe/Bucharest",decoded.visits.single().appointmentZoneId);assertEquals("Ring office",decoded.visits.single().instructions)}
    @Test fun rejectsV1MalformedDuplicateItemAndOutsideTeamAssignment()=runTest{val value=pkg();val json=DispatchPackageCodec.encode(value).toString(Charsets.UTF_8);assertThrows(IllegalArgumentException::class.java){DispatchPackageCodec.decode(json.replace("\"formatVersion\": 2","\"formatVersion\": 1").toByteArray())};assertThrows(IllegalArgumentException::class.java){DispatchPackageCodec.decode("not json".toByteArray())};assertThrows(IllegalArgumentException::class.java){DispatchPackageCodec.encode(value.copy(visits=listOf(value.visits.single().copy(work=value.visits.single().work+value.visits.single().work.single()))))};val outsider=DispatchTechnicianSnapshot("outsider-identity-0001","Outsider");assertThrows(IllegalArgumentException::class.java){DispatchPackageCodec.encode(value.copy(visits=listOf(value.visits.single().copy(work=listOf(value.visits.single().work.single().copy(assignedTechnicians=listOf(outsider)))))))} }
    @Test fun importUsesStableVisitAndItemIdentityAndDoesNotClaimRecurrence()=runTest{val value=pkg();val preview=service.preview(value);assertEquals(DispatchVisitClassification.NEW_VISIT,preview.visits.single().classification);val first=service.import(preview);val local=first.createdVisitIds.single();assertEquals("BOOKED",db.serviceLoopDao().visit(local)!!.state);assertEquals("Europe/Bucharest",db.serviceLoopDao().visit(local)!!.appointmentZoneId);assertEquals(1,db.serviceLoopDao().visitCount());assertTrue(db.serviceLoopDao().visitWorkItems(local).single().servicePlanId==null);assertEquals(DispatchVisitClassification.ALREADY_CURRENT,service.preview(value.copy(packageId="OTHER-TRANSPORT")).visits.single().classification);assertEquals(1,db.serviceLoopDao().visitCount());assertEquals("ITEM-1",db.dispatchDao().itemBindings("DV-1").single().dispatchItemId)}
    @Test fun newerGenerationUpdatesSameBookedVisitAndOlderDoesNotRollback()=runTest{val first=service.import(service.preview(pkg()));val local=first.createdVisitIds.single();val newer=pkg(2,"2026-09-14");val preview=service.preview(newer);assertEquals(DispatchVisitClassification.UPDATE,preview.visits.single().classification);assertEquals(listOf(local),service.import(preview).updatedVisitIds);assertEquals(1,db.serviceLoopDao().visitCount());assertEquals("2026-09-14",db.serviceLoopDao().visit(local)!!.actualServiceDate);assertEquals(DispatchVisitClassification.OLDER_GENERATION,service.preview(pkg(1,"2026-09-12")).visits.single().classification);assertEquals("2026-09-14",db.serviceLoopDao().visit(local)!!.actualServiceDate)}
    @Test fun sameGenerationDifferentMaterialConflictsAndLocalRescheduleBlocksNewer()=runTest{val local=service.import(service.preview(pkg())).createdVisitIds.single();assertEquals(DispatchVisitClassification.CONFLICT,service.preview(pkg(1,"2026-09-13")).visits.single().classification);val visit=db.serviceLoopDao().visit(local)!!;db.serviceLoopDao().updateVisit(visit.copy(actualServiceDate="2026-09-20"));assertEquals(DispatchVisitClassification.LOCAL_CONFLICT,service.preview(pkg(2,"2026-09-14")).visits.single().classification)}
    @Test fun startedVisitRejectsNewerGenerationWithoutDuplicate()=runTest{val local=service.import(service.preview(pkg())).createdVisitIds.single();val visit=db.serviceLoopDao().visit(local)!!;db.serviceLoopDao().updateVisit(visit.copy(state="WORKING"));assertEquals(DispatchVisitClassification.UPDATE_BLOCKED,service.preview(pkg(2,"2026-09-14")).visits.single().classification);assertEquals(1,db.serviceLoopDao().visitCount())}
    @Test fun emptyAssignmentMeansEveryoneAndNonParticipantGetsNothing()=runTest{val value=pkg(assignedToSelf=false);val p=service.preview(value);assertEquals("ASSIGNED",p.visits.single().items.single().localRole);assertEquals(DispatchVisitClassification.NEW_VISIT,p.visits.single().classification);val other=value.copy(visits=listOf(value.visits.single().copy(participants=listOf(DispatchTechnicianSnapshot("different-technician-01","Alex")),teams=listOf(DispatchTeamSnapshot("T2","Other",listOf("different-technician-01"),emptyList())))));val notAssigned=service.preview(other);assertEquals(DispatchVisitClassification.NOT_ASSIGNED,notAssigned.visits.single().classification);assertFalse(notAssigned.canImport);assertEquals(0,db.serviceLoopDao().customerCount())}
    @Test fun teamsDeduplicateParticipantsAndSupportSeveralLeaders()=runTest{val a=TechnicianIdentity("tech-a-identity-0001","A");val b=TechnicianIdentity("tech-b-identity-0001","B");service.importTechnician(a);service.importTechnician(b);val t1=service.createTeam("North");val t2=service.createTeam("Emergency");service.setTeamMember(t1,a.technicianId,true,true);service.setTeamMember(t1,b.technicianId,true,true);service.setTeamMember(t2,a.technicianId,true,false);val teams=service.teams();assertEquals(2,teams.single{it.team.id==t1}.members.count{it.second});assertEquals(2,teams.flatMap{it.members}.map{it.first.technicianId}.toSet().size)}
    @Test fun officeShareKeepsVoidedAndSupersededSafety() {val uri=Uri.parse("content://serviceloop/report.pdf");val intent=reportShareIntent(uri,"office@example.com");assertEquals(Intent.ACTION_SEND,intent.action);assertArrayEquals(arrayOf("office@example.com"),intent.getStringArrayExtra(Intent.EXTRA_EMAIL));assertFalse(reportShareEligible(true,true,"ORIGINAL",false,false));assertTrue(reportShareEligible(true,true,"VOID_NOTICE",false,false))}

    @Test fun finalizationIncludesOnlyLocalDocumentationAndFreezesDispatchProvenance()=runTest{
        val dao=db.serviceLoopDao();dao.upsertBusinessProfile(BusinessProfileEntity(businessName="Service Co",technicianName="Report tech",phone=null,email=null,postalAddress=null,zoneId="Europe/Bucharest",modifiedAtEpochMillis=1));val value=pkg();val local=service.import(service.preview(value)).createdVisitIds.single();val repo=RoomServiceLoopRepository(db,ClockBusinessTime(Clock.fixed(Instant.parse("2026-09-12T08:00:00Z"),ZoneId.of("Europe/Bucharest")),ZoneId.of("Europe/Bucharest")));repo.startVisit(local);service.documentLocally(local,"ITEM-1");val work=dao.visitWorkItems(local).single();repo.savePublicWork(work.id,"Completed independently");repo.saveCompletionDraft(work.id,"PERFORMED",false,null,null,null,null);val record=(repo.finalizeVisit(local) as com.v16studio.serviceloop.domain.FinalizeResult.Success).recordId;val detail=repo.finalRecord(record)!!;assertEquals("DV-1",detail.public.dispatch!!.dispatchVisitId);assertEquals("ITEM-1",detail.public.lines.single().dispatchItemId);assertEquals(service.identity().technicianId,detail.public.dispatch!!.documentingTechnicianId)
    }

    @Test fun handoffIsNonExclusiveAndParticipationCompletionCreatesNoRecord()=runTest{
        val self=service.identity();val colleague=DispatchTechnicianSnapshot("colleague-identity-0001","Maria");val selfSnap=DispatchTechnicianSnapshot(self.technicianId,self.name);val base=pkg();val visit=base.visits.single().copy(participants=listOf(selfSnap,colleague),teams=listOf(DispatchTeamSnapshot("TEAM-1","Field",listOf(self.technicianId,colleague.technicianId),listOf(colleague.technicianId))),leaderTechnicianIds=listOf(colleague.technicianId),work=listOf(base.visits.single().work.single().copy(assignedTechnicians=listOf(selfSnap))));val local=service.import(service.preview(base.copy(visits=listOf(visit)))).createdVisitIds.single();val current=db.serviceLoopDao().visit(local)!!;db.serviceLoopDao().updateVisit(current.copy(state="WORKING"));service.handoff(local,"ITEM-1",colleague);assertEquals("DEFERRED",db.dispatchDao().itemBinding("DV-1","ITEM-1")!!.documentationDisposition);service.finishInvolvement(local);assertEquals("PARTICIPATION_COMPLETE",db.serviceLoopDao().visit(local)!!.state);assertNull(db.serviceLoopDao().finalRecordForVisit(local))
    }
}
