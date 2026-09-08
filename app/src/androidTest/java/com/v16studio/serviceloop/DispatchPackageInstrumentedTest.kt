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
    private lateinit var database:ServiceLoopDatabase
    private lateinit var service:DispatchPackageService
    @Before fun setup(){database=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),ServiceLoopDatabase::class.java).build();service=DispatchPackageService(database)}
    @After fun close(){database.close()}

    @Test fun isolatedImportCreatesOneOrdinaryBookedVisitAndRepeatDoesNotDuplicate()=runBlocking {
        val value=DispatchPackage("DEVICE-PKG",Instant.now().toString(),"Instrumented coordinator",null,listOf(DispatchCustomer("CU-I","Fixture customer")),listOf(DispatchSite("ST-I","CU-I","Fixture site",null)),listOf(DispatchEquipment("EQ-I","ST-I","Fixture pump",null,null,null,null)),listOf(DispatchVisit("DV-I",null,"2026-09-20",null,"ST-I",null,listOf(DispatchWork("EQ-I","Inspect pump")))))
        val first=service.import(service.preview(value));assertEquals(1,first.createdVisitIds.size);assertEquals("BOOKED",database.serviceLoopDao().visit(first.createdVisitIds.single())!!.state)
        val second=service.import(service.preview(value));assertTrue(second.createdVisitIds.isEmpty());assertEquals(first.createdVisitIds,second.alreadyImportedVisitIds);assertEquals(1,database.serviceLoopDao().visitCount())
    }
}
