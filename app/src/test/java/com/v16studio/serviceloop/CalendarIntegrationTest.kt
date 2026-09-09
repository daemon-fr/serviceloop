package com.v16studio.serviceloop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.calendar.*
import com.v16studio.serviceloop.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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

    @Before fun setUp(){context=ApplicationProvider.getApplicationContext();store=CalendarDeviceStore(context);store.resetForDatasetReplacement();db=Room.inMemoryDatabaseBuilder(context,ServiceLoopDatabase::class.java).allowMainThreadQueries().build();gateway=FakeCalendarGateway();coordinator=CalendarCoordinator(context,db,gateway,store,CoroutineScope(SupervisorJob()));runBlocking{seed("dataset-a")}}
    @After fun tearDown(){db.close();store.resetForDatasetReplacement()}

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
