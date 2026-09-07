package com.v16studio.serviceloop

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
        compose.onNodeWithTag("add-service-plan", useUnmergedTree = true).performScrollTo().performClick()
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
        compose.onNodeWithText("Customers").performClick(); compose.onNodeWithTag("add-customer").performClick(); compose.onNodeWithText("Customer name · Required").performTextInput("Unsaved")
        compose.onNodeWithText("Back").performClick(); compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed(); compose.onNodeWithText("Keep editing").performClick(); compose.onNodeWithText("Unsaved").assertIsDisplayed(); compose.onNodeWithText("Discard changes").assertDoesNotExist()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; compose.waitUntil(5_000){runCatching{compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed()}.isSuccess}; compose.onNodeWithText("Discard changes").performClick()
        compose.onNodeWithTag("add-customer").performClick(); compose.onNodeWithText("Customer name · Required").performTextInput("Guard customer"); compose.onNodeWithText("Save customer").performScrollTo().performClick()
        compose.waitUntil(5_000){kotlinx.coroutines.runBlocking{database.serviceLoopDao().customerCount()==1}}; compose.onNodeWithText("Discard unsaved changes?").assertDoesNotExist()
        compose.onNodeWithText("Edit").performClick(); compose.onNodeWithText("Customer name · Required").performTextReplacement("Guard edited")
        compose.onNodeWithText("Back").performClick(); compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed(); compose.onNodeWithText("Keep editing").performClick(); compose.onNodeWithText("Guard edited").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick(); compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed(); compose.onNodeWithText("Discard changes").performClick()
        assertEquals("Guard customer",repository.customer(database.serviceLoopDao().customerList().single().id)!!.name)
        compose.onNodeWithText("Edit").performClick(); compose.onNodeWithText("Customer name · Required").performTextReplacement("Guard saved"); compose.onNodeWithText("Save customer").performScrollTo().performClick()
        compose.waitUntil(5_000){kotlinx.coroutines.runBlocking{repository.customer(database.serviceLoopDao().customerList().single().id)?.name=="Guard saved"}}; compose.onNodeWithText("Discard unsaved changes?").assertDoesNotExist(); Unit
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

    @Test fun dialerHandoffHasNoBusinessEffect()=contactHandoff("Call")
    @Test fun smsHandoffHasNoBusinessEffect()=contactHandoff("SMS")
    @Test fun emailHandoffHasNoBusinessEffect()=contactHandoff("Email")
    @Test fun mapsHandoffHasNoBusinessEffect()=contactHandoff("Maps",navigateToSite=true)
    @Test fun photoPickerHandoffReachesSystemSurface()=fieldHandoff("Choose photo")
    @Test fun cameraHandoffReachesSystemSurface()=fieldHandoff("Take photo")

    private fun contactHandoff(label:String,navigateToSite:Boolean=false)=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("systemHandoff")=="true")
        val customer=repository.createCustomer(CustomerInput("Handoff customer","Test contact","+40 700 000 000","sl3@example.invalid"))
        repository.createSite(customer,SiteInput("Handoff site","1 Fictional Test Street"))
        val beforeContacts=database.serviceLoopDao().contactNoteCount(); val beforeFollowUps=database.serviceLoopDao().followUpCount()
        val viewModel=ServiceLoopViewModel(repository){}
        compose.runOnUiThread { compose.activity.setContent{ServiceLoopTheme{ServiceLoopApp(viewModel)}} }
        compose.waitUntil(5_000){compose.onAllNodesWithTag("root-home").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Customers").performClick()
        compose.waitUntil(5_000){compose.onAllNodesWithText("Handoff customer",substring=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Handoff customer",substring=true).performClick()
        if(navigateToSite) compose.onNodeWithText("Handoff site",substring=true).performClick()
        systemHandoff(label)
        assertEquals(beforeContacts,database.serviceLoopDao().contactNoteCount()); assertEquals(beforeFollowUps,database.serviceLoopDao().followUpCount())
    }

    private fun fieldHandoff(label:String)=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("systemHandoff")=="true")
        val customer=repository.createCustomer(CustomerInput("Media customer")); val site=repository.createSite(customer,SiteInput("Media site","")); val equipment=repository.createEquipment(site,EquipmentInput("Media machine")); val plan=repository.createPlan(equipment,PlanInput("Media service",1,"YEARS","2026-09-01")); repository.createVisit(listOf(plan),"WORKING","2026-09-06")
        val beforePhotos=database.serviceLoopDao().visitPhotoCount(database.serviceLoopDao().visits().single().id)
        val viewModel=ServiceLoopViewModel(repository){}; compose.runOnUiThread { compose.activity.setContent{ServiceLoopTheme{ServiceLoopApp(viewModel)}} }
        compose.waitUntil(5_000){compose.onAllNodesWithText("Resume visit").fetchSemanticsNodes().isNotEmpty()}; compose.onNodeWithText("Resume visit").performClick()
        compose.onNodeWithTag("inspection-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("open-field-evidence")); compose.onNodeWithTag("open-field-evidence").performClick()
        systemHandoff(label)
        assertEquals(beforePhotos,database.serviceLoopDao().visitPhotoCount(database.serviceLoopDao().visits().single().id))
    }


    @Test fun selectedPhotoIsRenderedAsItsOwnCustomerPdfPage()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext; val customer=repository.createCustomer(CustomerInput("Photo customer")); val site=repository.createSite(customer,SiteInput("Photo site","1 Photo Street")); val equipment=repository.createEquipment(site,EquipmentInput("Photo machine")); val plan=repository.createPlan(equipment,PlanInput("Photo service",1,"YEARS","2026-09-01")); repository.saveBusinessProfile(BusinessProfile("Service Co","Alex",zoneId="Europe/Bucharest")); val visit=repository.createVisit(listOf(plan),"WORKING","2026-09-06"); val work=database.serviceLoopDao().firstWorkItemId(visit)!!; repository.savePublicWork(work,"Photographed service"); repository.saveCompletionDraft(work,"PERFORMED",false,null,null,null,null)
        val bitmap=Bitmap.createBitmap(40,30,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.CYAN)}; val bytes=ByteArrayOutputStream().also{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray(); repository.savePhoto(work,bytes,"evidence.png","image/png",true,"Machine evidence")
        val record=(repository.finalizeVisit(visit) as FinalizeResult.Success).recordId; val report=AndroidReportService(context,database,repository).generate(record); assertEquals(2,report.pageCount); assertEquals(2,AndroidReportService(context,database,repository).pageCount(report.relativePath)); File(context.filesDir,report.relativePath).delete(); Unit
    }

    private fun systemHandoff(label:String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        compose.onNodeWithText(label).performClick()
        var external=false
        repeat(30) {
            val packageName=instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
            if(packageName!=null && packageName!="com.v16studio.serviceloop") { external=true; return@repeat }
            Thread.sleep(100)
        }
        assertTrue("$label did not reach a system handler",external)
        if(instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()!="com.v16studio.serviceloop") {
            instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }
}
