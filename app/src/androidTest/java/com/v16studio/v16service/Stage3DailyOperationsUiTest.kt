package com.v16studio.v16service

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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.v16service.data.RoomV16ServiceRepository
import com.v16studio.v16service.data.V16ServiceDatabase
import com.v16studio.v16service.data.V16ServiceRepository
import com.v16studio.v16service.domain.BusinessTime
import com.v16studio.v16service.domain.*
import com.v16studio.v16service.report.AndroidReportService
import com.v16studio.v16service.ui.V16ServiceApp
import com.v16studio.v16service.ui.V16ServiceViewModel
import com.v16studio.v16service.ui.theme.V16ServiceTheme
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
    private lateinit var database: V16ServiceDatabase
    private lateinit var repository: V16ServiceRepository

    @Before fun setup() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        database=Room.inMemoryDatabaseBuilder(context,V16ServiceDatabase::class.java).allowMainThreadQueries().build()
        val time=object:BusinessTime{override val zoneId=ZoneId.of("Europe/Bucharest");override fun instant()=Instant.parse("2026-09-06T10:00:00Z")}
        repository=RoomV16ServiceRepository(database,time,attachmentRoot=context.filesDir)
    }
    @After fun close() {
        compose.runOnUiThread { compose.activity.setContent {} }
        compose.waitForIdle()
        database.close()
    }

    private fun showApp() {
        val viewModel = V16ServiceViewModel(repository) {}
        compose.runOnUiThread { compose.activity.setContent { V16ServiceTheme { V16ServiceApp(viewModel) } } }
        compose.waitForIdle()
    }

    @Test fun directoryToRecurringPlanIsReachableThroughNormalUi() {
        showApp()
        compose.onNodeWithText("Register").performClick()
        compose.onNodeWithTag("add-customer").performClick()
        compose.onNodeWithText("Customer name · Required").performTextInput("Stage Three Customer")
        compose.onNodeWithText("Main contact").performTextInput("Dana")
        compose.onNodeWithText("Site name · Required").performTextInput("Primary workshop")
        compose.onNodeWithTag("customer-editor").performScrollToNode(hasTestTag("save-customer"))
        compose.onNodeWithTag("save-customer").performClick()
        compose.waitUntil(5_000){kotlinx.coroutines.runBlocking{database.v16ServiceDao().customerCount()==1}}
        compose.waitUntil(5_000){compose.onAllNodesWithTag("add-site").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("add-site").performScrollTo().performClick()
        compose.onNodeWithText("Site name · Required").performTextInput("Workshop")
        compose.onNodeWithText("Address").performTextInput("1 Development Street")
        compose.onNodeWithText("Save site").performScrollTo().performClick()
        compose.waitUntil(5_000){kotlinx.coroutines.runBlocking{database.v16ServiceDao().siteCount()==2}}
        compose.waitUntil(5_000){compose.onAllNodesWithTag("add-equipment").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("add-equipment").performScrollTo().performClick()
        compose.onNodeWithText("Equipment name · Required").performTextInput("Test compressor")
        compose.onNodeWithText("Technician identifier").performTextInput("TC-01")
        compose.onNodeWithText("Save equipment").performScrollTo().performClick()
        compose.waitUntil(5_000){kotlinx.coroutines.runBlocking{database.v16ServiceDao().equipmentCount()==1}}
        compose.waitUntil(5_000){compose.onAllNodesWithTag("add-service-plan", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("add-service-plan", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Plan name · Required").performTextInput("Quarterly inspection")
        compose.onNodeWithText("Save plan").performScrollTo().performClick()
        compose.waitUntil(5_000){kotlinx.coroutines.runBlocking{database.v16ServiceDao().planCount()==1}}
        compose.onNodeWithText("Current obligation remains separate from bookings and contact.").assertIsDisplayed()
        val pair=kotlinx.coroutines.runBlocking{val plan=database.v16ServiceDao().dueServices().single();plan to database.v16ServiceDao().obligationCount(plan.planId)}
        assertEquals(1,pair.second)
        val dueBefore=pair.first.dueDate
        compose.onNodeWithText("Create visit").performClick()
        if (compose.onAllNodesWithText("Find customer or site").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Find customer or site").performTextInput("Workshop")
            compose.onNodeWithTag("visit-site-${pair.first.siteId}-select").performClick()
            compose.onNodeWithTag("visit-site-continue").performClick()
        }
        compose.onNodeWithTag("new-visit-form").performScrollToNode(hasText("Record past visit"))
        compose.onNodeWithText("Record past visit").assertIsDisplayed()
        compose.onNodeWithTag("new-visit-form").performScrollToNode(hasText("Book visit"))
        compose.onNodeWithText("Book visit").performClick()
        compose.waitUntil(15_000){kotlinx.coroutines.runBlocking{database.v16ServiceDao().visitCount()==1}}
        compose.waitUntil(15_000){compose.onAllNodesWithTag("visit-detail-list").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("visit-detail-list").performScrollToNode(hasTestTag("start-visit"))
        compose.onNodeWithTag("start-visit").assertIsDisplayed()
        compose.onNodeWithText("New appointment date").performTextReplacement("2026-09-12")
        compose.onNodeWithText("Reschedule reason").performTextInput("Customer requested another date")
        compose.onNodeWithText("Reschedule booking").performScrollTo().performClick()
        compose.waitUntil(15_000){kotlinx.coroutines.runBlocking{database.v16ServiceDao().visits().single().actualServiceDate=="2026-09-12"}}
        assertEquals(dueBefore,kotlinx.coroutines.runBlocking{database.v16ServiceDao().dueServices().single().dueDate})
        compose.onNodeWithText("Cancellation reason").performTextInput("Customer unavailable")
        compose.onNodeWithText("Cancel booking").performScrollTo().performClick()
        compose.waitUntil(15_000){kotlinx.coroutines.runBlocking{database.v16ServiceDao().visits().single().state=="CANCELED"}}
        assertEquals(null,kotlinx.coroutines.runBlocking{database.v16ServiceDao().dueServices().single().claimedVisitId})
    }

    @Test fun appBarAndSystemBackProtectUnsavedCreateAndEditForms()=runBlocking {
        showApp()
        compose.onNodeWithText("Register").performClick(); compose.onNodeWithTag("add-customer").performClick(); compose.onNodeWithText("Customer name · Required").performTextInput("Unsaved")
        compose.onNodeWithText("Back").performClick(); compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed(); compose.onNodeWithText("Keep editing").performClick(); compose.onNodeWithText("Unsaved").assertIsDisplayed(); compose.onNodeWithText("Discard changes").assertDoesNotExist()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; compose.waitUntil(5_000){runCatching{compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed()}.isSuccess}; compose.onNodeWithText("Discard changes").performClick()
        compose.onNodeWithTag("add-customer").performClick(); compose.onNodeWithText("Customer name · Required").performTextInput("Guard customer"); compose.onNodeWithText("Site name · Required").performTextInput("Guard site"); compose.onNodeWithTag("customer-editor").performScrollToNode(hasTestTag("save-customer")); compose.onNodeWithTag("save-customer").performClick()
        compose.waitUntil(5_000){kotlinx.coroutines.runBlocking{database.v16ServiceDao().customerCount()==1}}; compose.onNodeWithText("Discard unsaved changes?").assertDoesNotExist()
        compose.waitUntil(5_000){compose.onAllNodesWithText("Edit", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()}; compose.onNodeWithText("Edit", useUnmergedTree = true).performClick(); compose.onNodeWithText("Customer name · Required").performTextReplacement("Guard edited")
        compose.onNodeWithText("Back").performClick(); compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed(); compose.onNodeWithText("Keep editing").performClick(); compose.onNodeWithText("Guard edited").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick(); compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed(); compose.onNodeWithText("Discard changes").performClick()
        assertEquals("Guard customer",repository.customer(database.v16ServiceDao().customerList().single().id)!!.name)
        compose.onNodeWithText("Edit", useUnmergedTree = true).performClick(); compose.onNodeWithText("Customer name · Required").performTextReplacement("Guard saved"); compose.onNodeWithTag("customer-editor").performScrollToNode(hasTestTag("save-customer")); compose.onNodeWithTag("save-customer").performClick()
        compose.waitUntil(5_000){kotlinx.coroutines.runBlocking{repository.customer(database.v16ServiceDao().customerList().single().id)?.name=="Guard saved"}}; compose.onNodeWithText("Discard unsaved changes?").assertDoesNotExist(); Unit
    }

    @Test fun expandedLongTextUsesSameBufferUntilExplicitSave()=runBlocking {
        showApp()
        compose.onNodeWithText("Register").performClick(); compose.onNodeWithTag("add-customer").performClick(); compose.onNodeWithText("Customer name · Required").performTextInput("Long text customer")
        compose.onNodeWithText("Site name · Required").performTextInput("Long text site")
        val longNote="Unsaved long private note that deliberately occupies enough compact-field space to exercise the reserved expand affordance region without creating a second editing buffer."
        compose.onNodeWithTag("customer-editor").performScrollToNode(hasTestTag("long-text-private-customer-note-expand")); compose.onNodeWithTag("long-text-private-customer-note-expand").assertIsDisplayed().performClick(); compose.onNodeWithTag("long-text-private-customer-note-expanded").performTextInput(longNote); compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("long-text-private-customer-note-expand").assertIsDisplayed(); compose.onNodeWithTag("long-text-private-customer-note").assertTextContains(longNote); assertEquals(0,database.v16ServiceDao().customerCount()); compose.onNodeWithTag("customer-editor").performScrollToNode(hasTestTag("save-customer")); compose.onNodeWithTag("save-customer").performClick(); compose.waitUntil(5_000){kotlinx.coroutines.runBlocking{database.v16ServiceDao().customerCount()==1}}; assertEquals(longNote,repository.customer(database.v16ServiceDao().customerList().single().id)!!.privateNote); Unit
    }

    @Test fun globalAddEquipmentUsesSiteChooserAndRealEditor()=runBlocking {
        val customer=repository.createCustomer(CustomerInput("Selector customer")); repository.createSite(customer,SiteInput("Selector site",""))
        val viewModel=V16ServiceViewModel(repository){}
        compose.runOnUiThread { compose.activity.setContent { V16ServiceTheme { V16ServiceApp(viewModel) } } }
        compose.waitForIdle()
        compose.onNodeWithText("Register").performClick(); compose.onNodeWithText("Equipment").performClick(); compose.onNodeWithTag("add-equipment-from-register").performClick()
        compose.onNodeWithText("Select the customer site where the equipment is installed.").assertIsDisplayed(); compose.waitUntil(5_000){compose.onAllNodesWithText("ST-001", substring=true).fetchSemanticsNodes().isNotEmpty()}; compose.onNodeWithText("ST-001", substring=true).performClick(); compose.onNodeWithText("Equipment name · Required").assertIsDisplayed(); Unit
    }

    @Test fun customerRegistersAndCustomerScopedEquipmentRemainDisambiguated()=runBlocking {
        val customerA=repository.createCustomer(CustomerInput("Customer A")); val a1=repository.createSite(customerA,SiteInput("A1","1 Alpha Street")); val a2=repository.createSite(customerA,SiteInput("A2","2 Alpha Street")); repository.createEquipment(a1,EquipmentInput("A machine one")); repository.createEquipment(a2,EquipmentInput("A machine two"))
        val customerB=repository.createCustomer(CustomerInput("Customer B")); val b1=repository.createSite(customerB,SiteInput("B1","1 Beta Street")); repository.createEquipment(b1,EquipmentInput("B machine"))
        val viewModel=V16ServiceViewModel(repository){}; compose.runOnUiThread { compose.activity.setContent { V16ServiceTheme { V16ServiceApp(viewModel) } } }
        compose.onNodeWithText("Register").performClick(); compose.waitUntil(5_000){compose.onAllNodesWithText("Customer A",substring=true).fetchSemanticsNodes().isNotEmpty()}; compose.onNodeWithTag("content-tab-Customers").assertIsDisplayed(); compose.onNodeWithText("Customer A",substring=true).assertIsDisplayed(); compose.onNodeWithText("Customer B",substring=true).assertIsDisplayed()
        compose.onNodeWithText("Sites").performClick(); compose.onNodeWithTag("content-tab-Sites").assertIsDisplayed(); compose.onNodeWithText("1 Alpha Street",substring=true).assertIsDisplayed(); compose.onNodeWithText("Customer B",substring=true).assertIsDisplayed()
        compose.onNodeWithText("Equipment").performClick(); compose.onNodeWithTag("content-tab-Equipment").assertIsDisplayed(); compose.onNodeWithText("A machine one",substring=true).assertIsDisplayed(); compose.onAllNodesWithText("Customer A",substring=true).assertCountEquals(2); compose.onNodeWithText("B machine",substring=true).assertIsDisplayed(); compose.onNodeWithTag("add-equipment-from-register").performClick(); compose.onNodeWithText("Select the customer site where the equipment is installed.").assertIsDisplayed(); compose.onNodeWithText("Back").performClick()
        compose.onNodeWithTag("content-tab-Customers").performClick(); compose.onNodeWithText("Customer A",substring=true).performClick(); compose.waitUntil(5_000){runCatching{compose.onNodeWithTag("content-tab-Sites").assertIsDisplayed()}.isSuccess}; compose.onNodeWithTag("content-tab-Sites").assertIsDisplayed(); compose.onNodeWithText("A1",substring=true).assertIsDisplayed(); compose.onNodeWithText("A2",substring=true).assertIsDisplayed(); compose.onNodeWithText("B1",substring=true).assertDoesNotExist()
        compose.onNodeWithTag("content-tab-Equipment").performClick(); compose.onNodeWithText("A machine one",substring=true).assertIsDisplayed(); compose.onNodeWithText("A machine two",substring=true).assertIsDisplayed(); compose.onNodeWithText("B machine",substring=true).assertDoesNotExist(); compose.onNodeWithText("A1",substring=true).assertIsDisplayed(); compose.onNodeWithText("A2",substring=true).assertIsDisplayed(); Unit
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
        val beforeContacts=database.v16ServiceDao().contactNoteCount(); val beforeFollowUps=database.v16ServiceDao().followUpCount()
        val viewModel=V16ServiceViewModel(repository){}
        compose.runOnUiThread { compose.activity.setContent{V16ServiceTheme{V16ServiceApp(viewModel)}} }
        compose.waitUntil(5_000){compose.onAllNodesWithTag("root-home").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Register").performClick()
        compose.waitUntil(5_000){compose.onAllNodesWithText("Handoff customer",substring=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Handoff customer",substring=true).performClick()
        if(navigateToSite) compose.onNodeWithText("Handoff site",substring=true).performClick()
        compose.waitUntil(5_000){compose.onAllNodesWithText(label,substring=false).fetchSemanticsNodes().isNotEmpty()}
        val reached=systemHandoff(label)
        assertEquals(beforeContacts,database.v16ServiceDao().contactNoteCount()); assertEquals(beforeFollowUps,database.v16ServiceDao().followUpCount())
        assumeTrue("No compatible system handler for $label on this AVD",reached)
    }

    private fun fieldHandoff(label:String)=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("systemHandoff")=="true")
        val customer=repository.createCustomer(CustomerInput("Media customer")); val site=repository.createSite(customer,SiteInput("Media site","")); val equipment=repository.createEquipment(site,EquipmentInput("Media machine")); val plan=repository.createPlan(equipment,PlanInput("Media service",1,"YEARS","2026-09-01")); repository.createVisit(listOf(plan),"WORKING","2026-09-06")
        val beforePhotos=database.v16ServiceDao().visitPhotoCount(database.v16ServiceDao().visits().single().id)
        val viewModel=V16ServiceViewModel(repository){}; compose.runOnUiThread { compose.activity.setContent{V16ServiceTheme{V16ServiceApp(viewModel)}} }
        compose.waitUntil(5_000){compose.onAllNodesWithTag("resume-service").fetchSemanticsNodes().isNotEmpty()}; compose.onNodeWithTag("resume-service").performClick()
        compose.waitUntil(5_000){compose.onAllNodesWithTag("service-list").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("service-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("service-photos"))
        compose.onNodeWithTag(if(label=="Choose photo") "choose-photo" else "take-photo").performClick()
        val reached=externalSurfaceReached(label, InstrumentationRegistry.getInstrumentation())
        assertEquals(beforePhotos,database.v16ServiceDao().visitPhotoCount(database.v16ServiceDao().visits().single().id))
        assumeTrue("No compatible system handler for $label on this AVD",reached)
    }


    @Test fun selectedPhotoIsRenderedAsItsOwnCustomerPdfPage()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext; val customer=repository.createCustomer(CustomerInput("Photo customer")); val site=repository.createSite(customer,SiteInput("Photo site","1 Photo Street")); val equipment=repository.createEquipment(site,EquipmentInput("Photo machine")); val plan=repository.createPlan(equipment,PlanInput("Photo service",1,"YEARS","2026-09-01")); repository.saveBusinessProfile(BusinessProfile("Service Co","Alex",zoneId="Europe/Bucharest")); val visit=repository.createVisit(listOf(plan),"WORKING","2026-09-06"); val work=database.v16ServiceDao().firstWorkItemId(visit)!!; repository.savePublicWork(work,"Photographed service"); repository.saveCompletionDraft(work,"PERFORMED",false,null,null,null,null)
        val bitmap=Bitmap.createBitmap(40,30,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.CYAN)}; val bytes=ByteArrayOutputStream().also{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray(); repository.savePhoto(work,bytes,"evidence.png","image/png",true,"Machine evidence")
        val record=(repository.finalizeVisit(visit) as FinalizeResult.Success).recordId; val report=AndroidReportService(context,database,repository).generate(record); assertEquals(2,report.pageCount); assertEquals(2,AndroidReportService(context,database,repository).pageCount(report.relativePath)); File(context.filesDir,report.relativePath).delete(); Unit
    }

    private fun systemHandoff(label:String):Boolean {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        compose.onNodeWithText(label).performClick()
        return externalSurfaceReached(label, instrumentation)
    }

    private fun externalSurfaceReached(label:String, instrumentation:android.app.Instrumentation):Boolean {
        var external=false
        repeat(30) {
            val packageName=instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
            if(packageName!=null && packageName!="com.v16studio.v16service") { external=true; return@repeat }
            Thread.sleep(100)
        }
        if(external && instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()!="com.v16studio.v16service") {
            instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
        return external
    }
}
