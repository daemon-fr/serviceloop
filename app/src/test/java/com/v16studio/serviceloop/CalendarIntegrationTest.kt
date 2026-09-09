package com.v16studio.serviceloop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.calendar.*
import com.v16studio.serviceloop.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class CalendarIntegrationTest {
    private lateinit var context:Context
    private lateinit var db:ServiceLoopDatabase
    private lateinit var store:CalendarDeviceStore
    private lateinit var gateway:FakeCalendarGateway
    private lateinit var coordinator:CalendarCoordinator
    private lateinit var scope:CoroutineScope

    @Before fun setUp(){context=ApplicationProvider.getApplicationContext();store=CalendarDeviceStore(context);store.resetForDatasetReplacement();db=Room.inMemoryDatabaseBuilder(context,ServiceLoopDatabase::class.java).allowMainThreadQueries().build();gateway=FakeCalendarGateway();scope=CoroutineScope(SupervisorJob());coordinator=CalendarCoordinator(context,db,gateway,store,scope);runBlocking{seed("dataset-a")}}
    @After fun tearDown(){scope.cancel();db.close();store.resetForDatasetReplacement()}

    @Test fun roomObservationUpdatesAndCancelsWithoutExplicitReconcile()=runBlocking{
        coordinator.setEnabled(true);coordinator.select(gateway.calendar);coordinator.start();coordinator.start()
        val eventId=gateway.events.keys.single();val visit=db.serviceLoopDao().visit("visit")!!
        db.serviceLoopDao().updateVisit(visit.copy(scheduledAtEpochMillis=visit.scheduledAtEpochMillis!!+60_000,modifiedAtEpochMillis=2))
        await{gateway.updates==1};assertEquals(eventId,gateway.events.keys.single())
        db.serviceLoopDao().updateCustomer(db.serviceLoopDao().customer("customer")!!.copy(name="Updated Customer"));db.serviceLoopDao().updateSite(db.serviceLoopDao().site("site")!!.copy(name="Updated Site",address="2 New Road"))
        await{gateway.updates>=2&&gateway.events[eventId]?.title=="ServiceLoop · Updated Site"};assertEquals("2 New Road",gateway.events[eventId]!!.location);assertTrue(gateway.events[eventId]!!.description.contains("Updated Customer"))
        db.serviceLoopDao().updateVisit(db.serviceLoopDao().visit("visit")!!.copy(state="CANCELLED",cancellationReason="Cancelled",cancelledAtEpochMillis=3))
        await{gateway.events.isEmpty()};assertEquals(1,gateway.inserts)
    }

    @Test fun createIsIdempotentAndUpdateUsesSameEvent()=runBlocking{
        coordinator.setEnabled(true);assertTrue(store.read("dataset-a").enabled);coordinator.select(gateway.calendar);assertEquals(File(context.noBackupFilesDir,"calendar-integration.json").readText(),7L,store.read("dataset-a").selectedCalendarId)
        coordinator.reconcile();coordinator.reconcile()
        assertEquals(1,gateway.inserts);assertEquals(1,gateway.events.size)
        assertEquals(File(context.noBackupFilesDir,"calendar-integration.json").readText(),1,CalendarDeviceStore(context).read("dataset-a").links.size)
        val eventId=gateway.events.keys.single();val visit=db.serviceLoopDao().visit("visit")!!
        db.serviceLoopDao().updateVisit(visit.copy(scheduledAtEpochMillis=visit.scheduledAtEpochMillis!!+2*60*60*1000,modifiedAtEpochMillis=2))
        coordinator.reconcile()
        assertEquals(eventId,gateway.events.keys.single());assertEquals(1,gateway.updates);assertEquals(11*60*60*1000L,gateway.events[eventId]!!.startMillis)
    }

    @Test fun externalDeleteStaysMissingUntilDeliberateRecreate()=runBlocking{
        coordinator.setEnabled(true);coordinator.select(gateway.calendar);val old=gateway.events.keys.single();gateway.events.remove(old)
        coordinator.reconcile();coordinator.reconcile();assertEquals(1,gateway.inserts);assertEquals("Calendar event missing",coordinator.visitState("visit").label)
        coordinator.add("visit");assertEquals(2,gateway.inserts);assertEquals(1,gateway.events.size)
    }

    @Test fun removeSuppressesAutomaticRecreation()=runBlocking{
        coordinator.setEnabled(true);coordinator.select(gateway.calendar);coordinator.remove("visit");coordinator.reconcile()
        assertEquals(1,gateway.inserts);assertTrue(gateway.events.isEmpty());assertEquals("Removed from Calendar",coordinator.visitState("visit").label)
        coordinator.add("visit");assertEquals(2,gateway.inserts)
    }

    @Test fun datasetReplacementDropsBindingsWithoutDeletingExternalEvent()=runBlocking{
        coordinator.setEnabled(true);coordinator.select(gateway.calendar);assertEquals(1,gateway.events.size)
        db.serviceLoopDao().upsertRecoveryMetadata(metadata("dataset-b"));coordinator.reconcile()
        assertFalse(coordinator.runtimeState().enabled);assertEquals(1,gateway.events.size);assertEquals(0,gateway.deletes)
    }

    @Test fun disableRetainsEventAndReenableUpdatesWithoutDuplicate()=runBlocking{
        coordinator.setEnabled(true);coordinator.select(gateway.calendar);val eventId=gateway.events.keys.single();coordinator.setEnabled(false)
        val visit=db.serviceLoopDao().visit("visit")!!;db.serviceLoopDao().updateVisit(visit.copy(scheduledAtEpochMillis=visit.scheduledAtEpochMillis!!+60_000,modifiedAtEpochMillis=2));coordinator.reconcile()
        assertEquals(0,gateway.updates);assertTrue(eventId in gateway.events)
        coordinator.setEnabled(true);assertEquals(1,gateway.updates);assertEquals(1,gateway.inserts)
    }

    @Test fun cancellationFailureRetainsPendingLinkWithoutBusinessRollback()=runBlocking{
        coordinator.setEnabled(true);coordinator.select(gateway.calendar);gateway.failDeletes=true
        val visit=db.serviceLoopDao().visit("visit")!!;db.serviceLoopDao().updateVisit(visit.copy(state="CANCELLED",cancellationReason="Customer requested",cancelledAtEpochMillis=3));coordinator.reconcile()
        assertEquals("CANCELLED",db.serviceLoopDao().visit("visit")!!.state);assertEquals(1,coordinator.runtimeState().problemCount);assertEquals(1,gateway.events.size)
    }

    @Test fun workingAndFinalizedStatesRetainHistoricalEvent()=runBlocking{
        coordinator.setEnabled(true);coordinator.select(gateway.calendar);val dao=db.serviceLoopDao();val visit=dao.visit("visit")!!
        dao.updateVisit(visit.copy(state="WORKING"));coordinator.reconcile();assertEquals(1,gateway.events.size);assertEquals(0,gateway.deletes)
        dao.updateVisit(visit.copy(state="FINALIZED"));coordinator.reconcile();assertEquals(1,gateway.events.size);assertEquals(0,gateway.deletes)
    }

    @Test fun corruptStoreDisablesSafelyWithoutProviderMutation(){
        File(context.noBackupFilesDir,"calendar-integration.json").writeText("not-json")
        val broken=CalendarDeviceStore(context).read("dataset-a")
        assertFalse(broken.enabled);assertTrue(broken.storeProblem);assertEquals(0,gateway.inserts+gateway.updates+gateway.deletes)
    }

    @Test fun unavailableSelectedCalendarIsReportedTruthfully()=runBlocking{
        coordinator.setEnabled(true);coordinator.select(gateway.calendar);gateway.available=false
        assertEquals("Selected calendar unavailable",coordinator.runtimeState().label)
    }

    @Test fun eventContentIsMinimalAndPrivateFieldsCannotLeak(){
        val event=CalendarCoordinator.event(visit())
        assertEquals("ServiceLoop · Site",event.title);assertEquals("1 Public Road",event.location);assertEquals("Visit V-1\nCustomer",event.description);assertEquals(60*60*1000,event.endMillis-event.startMillis)
        val rendered=listOf(event.title,event.location,event.description).joinToString(" ")
        listOf("secret access","private customer","dispatch instruction","phone@example","SLT-SECRET").forEach{assertFalse(rendered.contains(it))}
    }

    private suspend fun seed(dataset:String){val dao=db.serviceLoopDao();dao.upsertRecoveryMetadata(metadata(dataset));dao.insertCustomers(listOf(CustomerEntity("customer","CU-1","Customer",privateNote="private customer")));dao.insertSites(listOf(SiteEntity("site","customer","ST-1","Site","1 Public Road","secret access")));dao.insertVisits(listOf(visit()))}
    private fun metadata(id:String)=RecoveryMetadataEntity(datasetId=id,firstBusinessWriteAtEpochMillis=null,lastBusinessWriteAtEpochMillis=null,lastBackupAttemptAtEpochMillis=null,lastVerifiedFullBackupAtEpochMillis=null,lastVerifiedSnapshotAtEpochMillis=null,lastVerifiedDestination=null,lastVerifiedSize=null)
    private fun visit()=WorkingVisitEntity("visit","V-1","customer","site","2026-09-10","Customer","Site","1 Public Road","BOOKED",1,scheduledAtEpochMillis=9*60*60*1000,appointmentZoneId="Europe/Bucharest")
    private suspend fun await(condition:()->Boolean){repeat(300){if(condition())return;delay(10)};fail("Timed out waiting for Room-driven Calendar reconciliation")}
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class CalendarDispatchObservationTest {
    private lateinit var context:Context;private lateinit var db:ServiceLoopDatabase;private lateinit var store:CalendarDeviceStore;private lateinit var gateway:FakeCalendarGateway;private lateinit var coordinator:CalendarCoordinator;private lateinit var scope:CoroutineScope;private lateinit var dispatch:DispatchPackageService
    @Before fun setup()=runBlocking{context=ApplicationProvider.getApplicationContext();store=CalendarDeviceStore(context);store.resetForDatasetReplacement();db=Room.inMemoryDatabaseBuilder(context,ServiceLoopDatabase::class.java).allowMainThreadQueries().build();db.serviceLoopDao().upsertRecoveryMetadata(metadata("dispatch-calendar"));gateway=FakeCalendarGateway();scope=CoroutineScope(SupervisorJob());coordinator=CalendarCoordinator(context,db,gateway,store,scope);dispatch=DispatchPackageService(db,context.cacheDir);coordinator.setEnabled(true);coordinator.select(gateway.calendar);coordinator.start()}
    @After fun close(){scope.cancel();db.close();store.resetForDatasetReplacement()}

    @Test fun dispatchCreateGenerationUpdateAndWithdrawalProjectAutomatically()=runBlocking{
        val first=pkg();val local=dispatch.import(dispatch.preview(first)).createdVisitIds.single();await{gateway.inserts==1};val eventId=gateway.events.keys.single()
        val newer=pkg(2,"2026-09-14","10:30");dispatch.import(dispatch.preview(newer));await{gateway.updates==1};assertEquals(eventId,gateway.events.keys.single());assertEquals(1,gateway.inserts)
        dispatch.import(dispatch.preview(withdrawn(newer,3)));await{gateway.events.isEmpty()&&store.read("dispatch-calendar").links.isEmpty()};assertEquals("DISPATCH_WITHDRAWN",db.serviceLoopDao().visit(local)!!.state)
    }

    @Test fun withdrawalDeleteFailureKeepsBusinessStateAndRetriesOnLaterInvalidation()=runBlocking{
        val first=pkg();val local=dispatch.import(dispatch.preview(first)).createdVisitIds.single();await{gateway.inserts==1};gateway.failDeletes=true
        dispatch.import(dispatch.preview(withdrawn(first,2)));await{store.read("dispatch-calendar").links[local]?.state==CalendarLinkState.DELETE_PENDING}
        assertEquals("DISPATCH_WITHDRAWN",db.serviceLoopDao().visit(local)!!.state);assertEquals(1,gateway.events.size)
        gateway.failDeletes=false;val visit=db.serviceLoopDao().visit(local)!!;db.serviceLoopDao().updateVisit(visit.copy(modifiedAtEpochMillis=visit.modifiedAtEpochMillis+1));await{gateway.events.isEmpty()&&local !in store.read("dispatch-calendar").links}
    }

    @Test fun redundantObservedExplicitAndResumeTriggersDoNotDuplicate()=runBlocking{
        dispatch.import(dispatch.preview(pkg()));coordinator.reconcileAsync();coordinator.reconcileAsync();coordinator.start();await{gateway.inserts==1};delay(100);assertEquals(1,gateway.events.size);assertEquals(1,gateway.inserts)
    }

    private suspend fun pkg(generation:Int=1,date:String="2026-09-12",time:String="09:30"):DispatchPackage{val self=dispatch.identity();val tech=DispatchTechnicianSnapshot(self.technicianId,self.name);val team=DispatchTeamSnapshot("TEAM-1","Field",listOf(self.technicianId),emptyList());return DispatchPackage("PKG-$generation",java.time.Instant.parse("2026-09-08T10:00:00Z").toString(),"Central",listOf(DispatchCustomer("CU-D","Customer")),listOf(DispatchSite("ST-D","CU-D","Site","1 Road")),listOf(DispatchEquipment("EQ-D","ST-D","Pump",null,null,null,null)),listOf(DispatchVisit("DV-1",generation,"JOB-1",date,time,"Europe/Bucharest","ST-D",null,listOf(team),listOf(tech),emptyList(),listOf(DispatchWork("ITEM-1","EQ-D","Service",assignedTechnicians=listOf(tech))))))}
    private fun withdrawn(base:DispatchPackage,generation:Int):DispatchPackage{val other=DispatchTechnicianSnapshot("other-technician-0001","Other");val visit=base.visits.single();return base.copy(packageId="PKG-$generation",visits=listOf(visit.copy(generation=generation,participants=listOf(other),teams=listOf(DispatchTeamSnapshot("TEAM-X","Other",listOf(other.technicianId),emptyList())),work=listOf(visit.work.single().copy(assignedTechnicians=listOf(other))))))}
    private fun metadata(id:String)=RecoveryMetadataEntity(datasetId=id,firstBusinessWriteAtEpochMillis=null,lastBusinessWriteAtEpochMillis=null,lastBackupAttemptAtEpochMillis=null,lastVerifiedFullBackupAtEpochMillis=null,lastVerifiedSnapshotAtEpochMillis=null,lastVerifiedDestination=null,lastVerifiedSize=null)
    private suspend fun await(condition:()->Boolean){repeat(300){if(condition())return;delay(10)};fail("Timed out waiting for Room-driven Calendar reconciliation")}
}

private class FakeCalendarGateway:CalendarGateway{
    val calendar=WritableCalendar(7,"Test calendar","local")
    val events=linkedMapOf<Long,ManagedCalendarEvent>();var inserts=0;var updates=0;var deletes=0;var failDeletes=false;var available=true;private var next=100L
    override fun hasPermissions()=true
    override fun writableCalendars()=if(available)listOf(calendar)else emptyList()
    override fun eventExists(calendarId:Long,eventId:Long)=eventId in events
    override fun insert(calendarId:Long,event:ManagedCalendarEvent):Long{inserts++;return next++.also{events[it]=event}}
    override fun update(calendarId:Long,eventId:Long,event:ManagedCalendarEvent):Boolean{updates++;return if(eventId in events){events[eventId]=event;true}else false}
    override fun delete(calendarId:Long,eventId:Long):Boolean{deletes++;return !failDeletes&&events.remove(eventId)!=null}
}
