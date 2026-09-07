package com.v16studio.serviceloop

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertTextContains
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.domain.*
import com.v16studio.serviceloop.report.AndroidReportService
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import java.time.Instant
import java.time.ZoneId
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage3DailyOperationsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var database: ServiceLoopDatabase
    private lateinit var repository: ServiceLoopRepository

    @Before fun setup() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        database=Room.inMemoryDatabaseBuilder(context,ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        val time=object:BusinessTime{override val zoneId=ZoneId.of("Europe/Bucharest");override fun instant()=Instant.parse("2026-09-06T10:00:00Z")}
        repository=RoomServiceLoopRepository(database,time,attachmentRoot=context.filesDir)
        val viewModel=ServiceLoopViewModel(repository){}
        compose.runOnUiThread { compose.activity.setContent{ServiceLoopTheme{ServiceLoopApp(viewModel)}} }
    }
    @After fun close()=database.close()

    @Test fun directoryToRecurringPlanIsReachableThroughNormalUi() {
        compose.onNodeWithText("Customers").performClick()
        compose.onNodeWithTag("add-customer").performClick()
        compose.onNodeWithText("Customer name · Required").performTextInput("Stage Three Customer")
        compose.onNodeWithText("Main contact").performTextInput("Dana")
        compose.onNodeWithText("Save customer").performScrollTo().performClick()
        compose.waitUntil(5_000){kotlinx.coroutines.runBlocking{database.serviceLoopDao().customerCount()==1}}
        compose.onNodeWithTag("add-site").performScrollTo().performClick()
        compose.onNodeWithText("Site name · Required").performTextInput("Workshop")
        compose.onNodeWithText("Address").performTextInput("1 Development Street")
        compose.onNodeWithText("Save site").performScrollTo().performClick()
        compose.waitUntil(5_000){kotlinx.coroutines.runBlocking{database.serviceLoopDao().siteCount()==1}}
        compose.onNodeWithTag("add-equipment").performScrollTo().performClick()
        compose.onNodeWithText("Equipment name · Required").performTextInput("Test compressor")
        compose.onNodeWithText("Technician identifier").performTextInput("TC-01")
        compose.onNodeWithText("Save equipment").performScrollTo().performClick()
        compose.waitUntil(5_000){kotlinx.coroutines.runBlocking{database.serviceLoopDao().equipmentCount()==1}}
        compose.onNodeWithTag("add-service-plan").performScrollTo().performClick()
        compose.onNodeWithText("Plan name · Required").performTextInput("Quarterly inspection")
        compose.onNodeWithText("Save plan").performScrollTo().performClick()
        compose.waitUntil(5_000){kotlinx.coroutines.runBlocking{database.serviceLoopDao().planCount()==1}}
        compose.onNodeWithText("Current obligation remains separate from bookings and contact.").assertIsDisplayed()
        val pair=kotlinx.coroutines.runBlocking{val plan=database.serviceLoopDao().dueServices().single();plan to database.serviceLoopDao().obligationCount(plan.planId)}
        assertEquals(1,pair.second)
        val dueBefore=pair.first.dueDate
        compose.onNodeWithText("Create visit").performClick()
        compose.onNodeWithText("Record past visit").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Book visit").performScrollTo().performClick()
        compose.waitUntil(5_000){kotlinx.coroutines.runBlocking{database.serviceLoopDao().visitCount()==1}}
        compose.onNodeWithText("Start with current details").assertIsDisplayed()
        compose.onNodeWithText("New appointment date").performTextReplacement("2026-09-12")
        compose.onNodeWithText("Reschedule reason").performTextInput("Customer requested another date")
        compose.onNodeWithText("Reschedule booking").performScrollTo().performClick()
        compose.waitUntil(5_000){kotlinx.coroutines.runBlocking{database.serviceLoopDao().visits().single().actualServiceDate=="2026-09-12"}}
        assertEquals(dueBefore,kotlinx.coroutines.runBlocking{database.serviceLoopDao().dueServices().single().dueDate})
        compose.onNodeWithText("Cancellation reason").performTextInput("Customer unavailable")
        compose.onNodeWithText("Cancel booking").performScrollTo().performClick()
        compose.waitUntil(5_000){kotlinx.coroutines.runBlocking{database.serviceLoopDao().visits().single().state=="CANCELLED"}}
        assertEquals(null,kotlinx.coroutines.runBlocking{database.serviceLoopDao().dueServices().single().claimedVisitId})
    }

    @Test fun appBarAndSystemBackProtectUnsavedCreateAndEditForms()=runBlocking {
        val customer=repository.createCustomer(CustomerInput("Guard customer"))
        compose.onNodeWithText("Customers").performClick(); compose.onNodeWithTag("add-customer").performClick(); compose.onNodeWithText("Customer name · Required").performTextInput("Unsaved")
        compose.onNodeWithText("Back").performClick(); compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed(); compose.onNodeWithText("Keep editing").performClick(); compose.onNodeWithText("Unsaved").assertIsDisplayed(); compose.onNodeWithText("Discard changes").assertDoesNotExist()
        compose.activity.onBackPressedDispatcher.onBackPressed(); compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed(); compose.onNodeWithText("Discard changes").performClick()
        compose.onNodeWithText("Guard customer").performClick(); compose.onNodeWithText("Edit").performClick(); compose.onNodeWithText("Customer name · Required").performTextReplacement("Guard edited")
        compose.activity.onBackPressedDispatcher.onBackPressed(); compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed(); compose.onNodeWithText("Keep editing").performClick(); compose.onNodeWithText("Guard edited").assertIsDisplayed(); Unit
    }

    @Test fun expandedLongTextUsesSameBufferUntilExplicitSave()=runBlocking {
        compose.onNodeWithText("Customers").performClick(); compose.onNodeWithTag("add-customer").performClick(); compose.onNodeWithText("Customer name · Required").performTextInput("Long text customer")
        compose.onNodeWithTag("long-text-private-customer-note-expand").performClick(); compose.onNodeWithTag("long-text-private-customer-note-expanded").performTextInput("Unsaved long private note"); compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("long-text-private-customer-note").assertTextContains("Unsaved long private note"); compose.onNodeWithText("Save customer").performScrollTo().performClick(); compose.waitUntil(5_000){kotlinx.coroutines.runBlocking{database.serviceLoopDao().customerCount()==1}}; assertEquals("Unsaved long private note",repository.customer(database.serviceLoopDao().customerList().single().id)!!.privateNote); Unit
    }

    @Test fun globalAddEquipmentUsesSiteChooserAndRealEditor()=runBlocking {
        val customer=repository.createCustomer(CustomerInput("Selector customer")); repository.createSite(customer,SiteInput("Selector site",""))
        compose.onNodeWithText("Customers").performClick(); compose.onNodeWithText("Equipment").performClick(); compose.onNodeWithTag("add-equipment-from-register").performClick()
        compose.onNodeWithText("Select the customer site where the equipment is installed.").assertIsDisplayed(); compose.onNodeWithText("Selector customer\nST-001 · Selector site").performClick(); compose.onNodeWithText("Equipment name · Required").assertIsDisplayed(); Unit
    }


    @Test fun selectedPhotoIsRenderedAsItsOwnCustomerPdfPage()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext; val customer=repository.createCustomer(CustomerInput("Photo customer")); val site=repository.createSite(customer,SiteInput("Photo site","1 Photo Street")); val equipment=repository.createEquipment(site,EquipmentInput("Photo machine")); val plan=repository.createPlan(equipment,PlanInput("Photo service",1,"YEARS","2026-09-01")); repository.saveBusinessProfile(BusinessProfile("Service Co","Alex",zoneId="Europe/Bucharest")); val visit=repository.createVisit(listOf(plan),"WORKING","2026-09-06"); val work=database.serviceLoopDao().firstWorkItemId(visit)!!; repository.savePublicWork(work,"Photographed service"); repository.saveCompletionDraft(work,"PERFORMED",false,null,null,null,null)
        val bitmap=Bitmap.createBitmap(40,30,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.CYAN)}; val bytes=ByteArrayOutputStream().also{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray(); repository.savePhoto(work,bytes,"evidence.png","image/png",true,"Machine evidence")
        val record=(repository.finalizeVisit(visit) as FinalizeResult.Success).recordId; val report=AndroidReportService(context,database,repository).generate(record); assertEquals(2,report.pageCount); assertEquals(2,AndroidReportService(context,database,repository).pageCount(report.relativePath)); File(context.filesDir,report.relativePath).delete(); Unit
    }
}
