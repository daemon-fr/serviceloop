package com.v16studio.serviceloop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.*
import java.time.Instant
import android.content.Intent
import android.net.Uri
import com.v16studio.serviceloop.ui.reportShareEligible
import com.v16studio.serviceloop.ui.reportShareIntent
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
    private val pkg get()=DispatchPackage("PKG-001",Instant.parse("2026-09-08T10:00:00Z").toString(),"Central Service Team",null,listOf(DispatchCustomer("CU-D","Dispatch Customer")),listOf(DispatchSite("ST-D","CU-D","Dispatch Site","1 Test Road")),listOf(DispatchEquipment("EQ-D","ST-D","Pump","P-1","Maker","Model","S-1")),listOf(DispatchVisit("DV-1","M-1","2026-09-12",1_789_200_000_000,"ST-D","Ring office",listOf(DispatchWork("EQ-D","Annual service")))))

    @Before fun setup(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),ServiceLoopDatabase::class.java).allowMainThreadQueries().build();service=DispatchPackageService(db)}
    @After fun close(){db.close()}

    @Test fun validRoundTripPreservesReadablePrivacyData(){val decoded=DispatchPackageCodec.decode(DispatchPackageCodec.encode(pkg));assertEquals(pkg,decoded);assertEquals("Ring office",decoded.visits.single().instructions)}
    @Test fun rejectsUnsupportedMalformedDuplicateAndExcessive(){val valid=DispatchPackageCodec.encode(pkg).toString(Charsets.UTF_8);assertThrows(IllegalArgumentException::class.java){DispatchPackageCodec.decode(valid.replace("\"formatVersion\": 1","\"formatVersion\": 2").toByteArray())};assertThrows(IllegalArgumentException::class.java){DispatchPackageCodec.decode("not json".toByteArray())};assertThrows(IllegalArgumentException::class.java){DispatchPackageCodec.encode(pkg.copy(visits=pkg.visits+pkg.visits.single()))};assertThrows(IllegalArgumentException::class.java){DispatchPackageCodec.decode(ByteArray(DispatchPackageCodec.MAX_BYTES+1))}}

    @Test fun importsBookedVisitAndIsIdempotent()=runTest {val preview=service.preview(pkg);assertTrue(preview.canImport);assertTrue(preview.directory.all{it.classification==DispatchClassification.NEW});val first=service.import(preview);assertEquals(1,first.createdVisitIds.size);val visit=db.serviceLoopDao().visit(first.createdVisitIds.single())!!;assertEquals("BOOKED",visit.state);assertEquals("2026-09-12",visit.actualServiceDate);assertEquals(1_789_200_000_000,visit.scheduledAtEpochMillis);assertEquals("ST-D",visit.siteReferenceSnapshot);assertEquals(1,db.serviceLoopDao().visitWorkItems(visit.id).size);val repeat=service.preview(pkg);assertEquals(DispatchVisitClassification.ALREADY_IMPORTED,repeat.visits.single().classification);assertTrue(service.import(repeat).createdVisitIds.isEmpty());assertEquals(1,db.serviceLoopDao().visitCount())}

    @Test fun sameDispatchIdentityWithDifferentContentConflicts()=runTest {service.import(service.preview(pkg));val changed=pkg.copy(visits=listOf(pkg.visits.single().copy(serviceDate="2026-09-13")));val preview=service.preview(changed);assertEquals(DispatchVisitClassification.CONFLICT,preview.visits.single().classification);assertFalse(preview.canImport);assertThrows(IllegalArgumentException::class.java){kotlinx.coroutines.runBlocking{service.import(preview)}}}

    @Test fun exactReferenceDifferenceConflictsAndSimilarIdentityWarns()=runTest {val dao=db.serviceLoopDao();dao.insertCustomers(listOf(CustomerEntity("c","CU-D","Different"),CustomerEntity("c2","CU-2","Dispatch Customer")));val preview=service.preview(pkg);assertEquals(DispatchClassification.CONFLICT,preview.directory.first{it.reference=="CU-D"}.classification);assertFalse(preview.canImport)}

    @Test fun packageFailureRollsBackWholeImport()=runTest {val broken=pkg.copy(equipment=emptyList());val preview=service.preview(broken);assertThrows(IllegalStateException::class.java){kotlinx.coroutines.runBlocking{service.import(preview)}};assertEquals(0,db.serviceLoopDao().customerCount());assertEquals(0,db.serviceLoopDao().visitCount())}

    @Test fun officeSharePrefillsEmailWithoutBypassingVoidedOriginalRule(){val uri=Uri.parse("content://serviceloop/report.pdf");val intent=reportShareIntent(uri,"office@example.com");assertEquals(Intent.ACTION_SEND,intent.action);assertEquals("application/pdf",intent.type);assertArrayEquals(arrayOf("office@example.com"),intent.getStringArrayExtra(Intent.EXTRA_EMAIL));assertEquals(uri,intent.getParcelableExtra(Intent.EXTRA_STREAM));assertFalse(reportShareEligible(true,true,"ORIGINAL",false,false));assertTrue(reportShareEligible(true,true,"VOID_NOTICE",false,false));assertTrue(reportShareEligible(true,false,"ORIGINAL",false,false))}

    @Test fun exactSafePlanClaimsWhileDueMismatchFallsBackOneOff()=runTest {val dao=db.serviceLoopDao();dao.insertCustomers(listOf(CustomerEntity("c","CU-D","Dispatch Customer")));dao.insertSites(listOf(SiteEntity("s","c","ST-D","Dispatch Site","1 Test Road",null)));dao.insertEquipment(listOf(EquipmentEntity("e","s","EQ-D","P-1","Pump","Maker","Model","S-1",null)));dao.insertPlans(listOf(ServicePlanEntity("p","e","PL-1","Annual",1,"YEARS","2026-09-15","ACTIVE","o")));dao.insertObligations(listOf(ServiceObligationEntity("o","p",1,"2026-09-15",1)));val linked=pkg.copy(visits=listOf(pkg.visits.single().copy(work=listOf(DispatchWork("EQ-D","Annual","PL-1","2026-09-15")))));val lp=service.preview(linked);assertEquals(1,lp.visits.single().planLinked);val result=service.import(lp);val item=dao.visitWorkItems(result.createdVisitIds.single()).single();assertEquals("p",item.servicePlanId);assertEquals("o",item.capturedObligationId);assertEquals(result.createdVisitIds.single(),dao.claimForObligation("o"));assertNull(dao.obligation("o")!!.consumedAtEpochMillis)
        val mismatch=linked.copy(packageId="PKG-002",visits=listOf(linked.visits.single().dueDateFix().copy(dispatchVisitId="DV-2")))
        val mp=service.preview(mismatch);assertEquals(1,mp.visits.single().oneOff);val r2=service.import(mp);val item2=dao.visitWorkItems(r2.createdVisitIds.single()).single();assertNull(item2.servicePlanId);assertNull(item2.capturedObligationId);assertEquals("2026-09-15",dao.obligation("o")!!.dueDate)
    }

    private fun DispatchVisit.dueDateFix()=copy(work=work.map{it.copy(dueDateSnapshot="2026-10-01")})
}
