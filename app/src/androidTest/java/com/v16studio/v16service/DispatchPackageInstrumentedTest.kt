package com.v16studio.v16service

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v16studio.v16service.data.*
import java.time.Instant
import java.time.Clock
import java.time.ZoneId
import com.v16studio.v16service.domain.ClockBusinessTime
import com.v16studio.v16service.domain.FinalizeResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DispatchPackageInstrumentedTest {
    private lateinit var database:V16ServiceDatabase;private lateinit var service:DispatchPackageService
    @Before fun setup(){database=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),V16ServiceDatabase::class.java).build();service=DispatchPackageService(database)}
    @After fun close(){database.close()}
    private suspend fun importTrusted(preview: DispatchPreview) = service.import(preview, service.identity().technicianId)
    @Test fun isolatedV1ImportCreatesOneBookedVisitAndGenerationUpdatesSameVisit()=runBlocking{
        val self=service.identity();val tech=DispatchTechnicianSnapshot(self.technicianId,self.name);val team=DispatchTeamSnapshot("TEAM-I","Field",listOf(self.technicianId),emptyList())
        fun value(g:Int,date:String)=DispatchPackage("DEVICE-PKG-$g",Instant.now().toString(),"Instrumented coordinator",listOf(DispatchCustomer("CU-I","Fixture customer")),listOf(DispatchSite("ST-I","CU-I","Fixture site",null)),listOf(DispatchEquipment("EQ-I","ST-I","Fixture pump",null,null,null,null)),listOf(DispatchVisit("DV-I",g,"JOB-I",date,null,"Europe/Bucharest","ST-I",null,listOf(team),listOf(tech),emptyList(),listOf(DispatchWork("ITEM-I","EQ-I","Inspect pump",assignedTechnicians=listOf(tech))))))
        val first=importTrusted(service.preview(value(1,"2026-09-20")));val local=first.createdVisitIds.single();assertEquals("BOOKED",database.v16ServiceDao().visit(local)!!.state)
        val second=importTrusted(service.preview(value(2,"2026-09-21")));assertEquals(listOf(local),second.updatedVisitIds);assertEquals("2026-09-21",database.v16ServiceDao().visit(local)!!.actualServiceDate);assertEquals(1,database.v16ServiceDao().visitCount());assertEquals(0,database.v16ServiceDao().claimCountForVisit(local))
    }

    @Test fun missingIdentityCannotPartiallyFinalizeDispatchVisit() = runBlocking {
        val dao = database.v16ServiceDao()
        dao.upsertBusinessProfile(BusinessProfileEntity(businessName="Service Co", technicianName="Report tech", phone=null, email=null, postalAddress=null, zoneId="Europe/Bucharest", modifiedAtEpochMillis=1))
        val self = service.identity()
        val tech = DispatchTechnicianSnapshot(self.technicianId, self.name)
        val team = DispatchTeamSnapshot("TEAM-ATOMIC", "Field", listOf(self.technicianId), emptyList())
        val value = DispatchPackage("PKG-ATOMIC", Instant.parse("2026-09-08T10:00:00Z").toString(), "Coordinator", listOf(DispatchCustomer("CU-ATOMIC", "Customer")), listOf(DispatchSite("ST-ATOMIC", "CU-ATOMIC", "Site", null)), listOf(DispatchEquipment("EQ-ATOMIC", "ST-ATOMIC", "Pump", null, null, null, null)), listOf(DispatchVisit("DV-ATOMIC", 1, "JOB-ATOMIC", "2026-09-12", null, "Europe/Bucharest", "ST-ATOMIC", null, listOf(team), listOf(tech), emptyList(), listOf(DispatchWork("ITEM-ATOMIC", "EQ-ATOMIC", "Inspect", assignedTechnicians=listOf(tech))))))
        val visitId = importTrusted(service.preview(value)).createdVisitIds.single()
        val repository = RoomV16ServiceRepository(database, ClockBusinessTime(Clock.fixed(Instant.parse("2026-09-12T08:00:00Z"), ZoneId.of("Europe/Bucharest")), ZoneId.of("Europe/Bucharest")))
        repository.startVisit(visitId)
        service.documentLocally(visitId, "ITEM-ATOMIC")
        val workId = dao.visitWorkItems(visitId).single().id
        repository.savePublicWork(workId, "Inspected")
        repository.saveCompletionDraft(workId, "PERFORMED", false, null, null, null, null)
        val beforeVisit = dao.visit(visitId)
        val beforeWork = dao.visitWorkItems(visitId)
        val beforeBinding = database.dispatchDao().visitBindingForLocalVisit(visitId)
        val identity = database.dispatchDao().technicianIdentity()!!
        database.openHelper.writableDatabase.execSQL("DELETE FROM technician_identity WHERE id='primary'")
        repeat(2) {
            assertEquals("Technician identity is unavailable", (repository.finalizeVisit(visitId) as FinalizeResult.Blocked).message)
            assertNull(dao.finalRecordForVisit(visitId))
            assertEquals(beforeVisit, dao.visit(visitId))
            assertEquals(beforeWork, dao.visitWorkItems(visitId))
            assertEquals(beforeBinding, database.dispatchDao().visitBindingForLocalVisit(visitId))
        }
        database.dispatchDao().insertTechnicianIdentity(identity)
        val first = repository.finalizeVisit(visitId) as FinalizeResult.Success
        assertEquals(first.recordId, (repository.finalizeVisit(visitId) as FinalizeResult.Success).recordId)
        assertEquals("COMPLETED", dao.visit(visitId)!!.state)
        assertEquals("DV-ATOMIC", repository.finalRecord(first.recordId)!!.public.dispatch!!.dispatchVisitId)
    }
}
