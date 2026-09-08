package com.v16studio.serviceloop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v16studio.serviceloop.data.*
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DispatchPackageInstrumentedTest {
    private lateinit var database:ServiceLoopDatabase;private lateinit var service:DispatchPackageService
    @Before fun setup(){database=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),ServiceLoopDatabase::class.java).build();service=DispatchPackageService(database)}
    @After fun close(){database.close()}
    @Test fun isolatedV2ImportCreatesOneBookedVisitAndGenerationUpdatesSameVisit()=runBlocking{
        val self=service.identity();val tech=DispatchTechnicianSnapshot(self.technicianId,self.name);val team=DispatchTeamSnapshot("TEAM-I","Field",listOf(self.technicianId),emptyList())
        fun value(g:Int,date:String)=DispatchPackage("DEVICE-PKG-$g",Instant.now().toString(),"Instrumented coordinator",listOf(DispatchCustomer("CU-I","Fixture customer")),listOf(DispatchSite("ST-I","CU-I","Fixture site",null)),listOf(DispatchEquipment("EQ-I","ST-I","Fixture pump",null,null,null,null)),listOf(DispatchVisit("DV-I",g,"JOB-I",date,null,"Europe/Bucharest","ST-I",null,listOf(team),listOf(tech),emptyList(),listOf(DispatchWork("ITEM-I","EQ-I","Inspect pump",assignedTechnicians=listOf(tech))))))
        val first=service.import(service.preview(value(1,"2026-09-20")));val local=first.createdVisitIds.single();assertEquals("BOOKED",database.serviceLoopDao().visit(local)!!.state)
        val second=service.import(service.preview(value(2,"2026-09-21")));assertEquals(listOf(local),second.updatedVisitIds);assertEquals("2026-09-21",database.serviceLoopDao().visit(local)!!.actualServiceDate);assertEquals(1,database.serviceLoopDao().visitCount());assertEquals(0,database.serviceLoopDao().claimCountForVisit(local))
    }
}
