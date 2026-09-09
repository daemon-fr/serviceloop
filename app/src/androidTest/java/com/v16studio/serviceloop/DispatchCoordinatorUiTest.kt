package com.v16studio.serviceloop

import android.content.Context
import androidx.activity.compose.setContent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.ui.*
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DispatchCoordinatorUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val prefs get()=compose.activity.getSharedPreferences(DISPATCH_PREFS,0)
    @Before fun off(){prefs.edit().putBoolean(COORDINATOR_ENABLED,false).commit()}
    @After fun restoreOff(){prefs.edit().putBoolean(COORDINATOR_ENABLED,false).commit()}
    private fun capture(tag:String,name:String){val file=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),name);file.outputStream().use{assertTrue(compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it))}}

    @Test fun coordinatorWorkspaceMovesFromSettingsToReactiveHomeActions(){
        compose.waitUntil{compose.onAllNodesWithTag("coordinator-home-actions").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithTag("root-nav-home").assertIsDisplayed();compose.onNodeWithTag("root-nav-work").assertIsDisplayed();compose.onNodeWithTag("root-nav-customers").assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick();compose.onNodeWithText("Coordinator tools · Experimental").performClick();compose.onNodeWithTag("coordinator-tools-switch").performClick()
        compose.onNodeWithText("Coordinator tools are available from Home.").assertIsDisplayed();compose.onNodeWithText("Technicians").assertDoesNotExist();compose.onNodeWithText("Teams and leaders").assertDoesNotExist();compose.onNodeWithText("Dispatch outbox").assertDoesNotExist()
        compose.onNodeWithText("Back").performClick();compose.onNodeWithText("Back").performClick();compose.onNodeWithTag("coordinator-home-actions").assertIsDisplayed();capture("root-home","dispatch-home-coordinator.png")
        compose.onNodeWithText("Technicians").performClick();compose.onNodeWithTag("dispatch-technicians").assertIsDisplayed();compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Teams").performClick();compose.onNodeWithTag("dispatch-teams").assertIsDisplayed();compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Outbox").performClick();compose.onNodeWithTag("dispatch-outbox").assertIsDisplayed();compose.onNodeWithTag("dispatch-new-visit").performClick();compose.onNodeWithTag("dispatch-new-visit").assertIsDisplayed()
        compose.onNodeWithTag("dispatch-new-visit").performScrollToNode(hasTestTag("dispatch-save-visit"));compose.onNodeWithTag("dispatch-save-visit").assertIsEnabled().performClick();compose.onNodeWithText("Choose a Site.").assertIsDisplayed()
        compose.onNodeWithTag("dispatch-new-visit").performScrollToNode(hasText("Manager reference"));compose.onNodeWithText("Manager reference").performTextInput("unsaved");compose.onNodeWithText("Back").performClick();compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed();compose.onNodeWithText("Keep editing").performClick();compose.onNodeWithText("Back").performClick();compose.onNodeWithText("Discard changes").performClick();compose.onNodeWithText("Back").performClick();compose.onNodeWithText("Settings").performClick();compose.onNodeWithText("Coordinator tools · Experimental").performClick();compose.onNodeWithTag("coordinator-tools-switch").performClick();compose.onNodeWithText("Back").performClick();compose.onNodeWithText("Back").performClick();compose.waitUntil{compose.onAllNodesWithTag("coordinator-home-actions").fetchSemanticsNodes().isEmpty()}
    }

    @Test fun listFirstOutboxHandlesLargeDirectoryAndExplicitSelection(){
        val context=ApplicationProvider.getApplicationContext<Context>();val db=Room.inMemoryDatabaseBuilder(context,ServiceLoopDatabase::class.java).allowMainThreadQueries().build();val service=DispatchPackageService(db);val ids=runBlocking{val dao=db.serviceLoopDao();dao.insertCustomers(listOf(CustomerEntity("customer","CU-LARGE","Large Customer")));val sites=(1..120).map{SiteEntity("site-$it","customer","SITE-${it.toString().padStart(3,'0')}","Site $it","$it Test Road",null)};dao.insertSites(sites);dao.insertEquipment(sites.take(3).mapIndexed{index,site->EquipmentEntity("equipment-$index",site.id,"EQ-$index",null,"Pump $index",null,null,null,null)});val self=service.identity();service.importTechnician(self);val team=service.createTeam("North Team");service.setTeamMember(team,self.technicianId,true,true);sites.take(3).mapIndexed{index,site->service.saveOutboxVisit(DispatchOutboxEditorDraft(managerReference="JOB-$index",siteId=site.id,serviceDate="2026-09-${20+index}",appointmentLocalTime="${(8+index).toString().padStart(2,'0')}:00",appointmentZoneId="Europe/Bucharest",teamIds=listOf(team),items=listOf(DispatchOutboxItemDraft("item-$index","equipment-$index","Inspect pump $index"))))}}
        try{compose.runOnUiThread{compose.activity.setContent{ServiceLoopTheme(false){val nav=rememberNavController();NavHost(nav,"outbox"){composable("outbox"){DispatchOutboxScreen(PaddingValues(),nav,service,db)};composable("dispatch/visit/new"){Text("New editor",Modifier.testTag("fake-new-editor"))};composable("dispatch/visit/{id}"){Text("Editor opened",Modifier.testTag("fake-editor"))};composable("dispatch/export-review"){Text("Review",Modifier.testTag("fake-review"))}}}}};compose.onNodeWithTag("dispatch-outbox").assertIsDisplayed();compose.onNodeWithTag("dispatch-new-visit").assertIsDisplayed();compose.onNodeWithTag("dispatch-search").assertIsDisplayed();compose.onNodeWithTag("dispatch-status-filter").assertIsDisplayed();compose.onNodeWithTag("dispatch-date-filter").assertIsDisplayed();compose.onNodeWithText("Sender label").assertDoesNotExist();compose.onNodeWithText("Create outbox Visit").assertDoesNotExist();compose.onNodeWithText("SITE-120",substring=true).assertDoesNotExist();compose.onNodeWithText("Export selected (0)").assertDoesNotExist();capture("dispatch-outbox","dispatch-outbox-list-first.png")
            compose.onNodeWithTag("dispatch-outbox-list").performScrollToNode(hasTestTag("dispatch-select-${ids[0]}"));compose.onNodeWithTag("dispatch-select-${ids[0]}").performClick();compose.onNodeWithTag("dispatch-outbox-list").performScrollToNode(hasTestTag("dispatch-select-${ids[1]}"));compose.onNodeWithTag("dispatch-select-${ids[1]}").performClick();compose.onNodeWithText("2 selected").assertIsDisplayed();compose.onNodeWithText("Export selected (2)").assertIsDisplayed()
            capture("dispatch-outbox","dispatch-outbox-list-first-selection.png")
            compose.onNodeWithTag("dispatch-search").performTextInput("JOB-0");compose.waitUntil{compose.onAllNodesWithText("1 selected").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithTag("dispatch-outbox-list").performScrollToNode(hasTestTag("dispatch-open-${ids[0]}"));compose.onNodeWithTag("dispatch-open-${ids[0]}").performClick();compose.onNodeWithTag("fake-editor").assertIsDisplayed()
        }finally{db.close()}
    }

    @Test fun sitePickerSearchFindsLateLargeDirectoryEntry(){
        val customers=listOf(CustomerEntity("customer","CU-SEARCH","Search Customer"));val sites=(1..150).map{SiteEntity("site-$it","customer","SITE-${it.toString().padStart(3,'0')}","Branch $it","$it Long Road",null)};var selected:String?=null
        compose.runOnUiThread{compose.activity.setContent{ServiceLoopTheme(false){DispatchSitePickerDialog(sites,customers,{}, {selected=it})}}};compose.onNodeWithTag("dispatch-site-picker").assertIsDisplayed();compose.onNodeWithTag("dispatch-site-search").performTextInput("SITE-150");compose.onNodeWithText("SITE-150 · Branch 150").assertIsDisplayed();capture("dispatch-site-picker","dispatch-site-picker-search.png");compose.onNodeWithText("SITE-150 · Branch 150").performClick();assertEquals("site-150",selected)
    }


    @Test fun renderedEditorAndExportReviewExposeSeparateWorkflowSurfaces(){
        val context=ApplicationProvider.getApplicationContext<Context>();val db=Room.inMemoryDatabaseBuilder(context,ServiceLoopDatabase::class.java).allowMainThreadQueries().build();val service=DispatchPackageService(db);val ids=runBlocking{val dao=db.serviceLoopDao();dao.insertCustomers(listOf(CustomerEntity("render-customer","CU-RENDER","Rendered Customer")));dao.insertSites(listOf(SiteEntity("render-site","render-customer","ST-RENDER","Rendered Site","1 Render Road",null)));dao.insertEquipment(listOf(EquipmentEntity("render-equipment","render-site","EQ-RENDER",null,"Rendered Pump",null,null,null,null)));val self=service.identity();service.importTechnician(self);val team=service.createTeam("Rendered Team");service.setTeamMember(team,self.technicianId,true,true);listOf("A","B").mapIndexed{index,ref->service.saveOutboxVisit(DispatchOutboxEditorDraft(managerReference="JOB-RENDER-$ref",siteId="render-site",serviceDate="2026-10-0${index+3}",appointmentLocalTime="09:00",appointmentZoneId="Europe/Bucharest",teamIds=listOf(team),items=listOf(DispatchOutboxItemDraft("render-item-$ref","render-equipment","Inspect rendered pump $ref"))))}}
        try{
            compose.runOnUiThread{compose.activity.setContent{ServiceLoopTheme(false){val nav=rememberNavController();CompositionLocalProvider(LocalDetailBackInterceptor provides remember{mutableStateOf<(() -> Unit)?>(null)}){NavHost(nav,"start"){composable("start"){Button({nav.navigate("editor")},Modifier.testTag("open-render-editor")){Text("Open editor")}};composable("editor"){DispatchVisitEditorScreen(PaddingValues(),nav,null,service,db)}}}}}};compose.onNodeWithTag("open-render-editor").performClick();compose.onNodeWithTag("dispatch-choose-site").performClick();compose.onNodeWithTag("dispatch-site-render-site").performClick();compose.onNodeWithTag("dispatch-new-visit").performScrollToNode(hasTestTag("dispatch-choose-teams"));compose.onNodeWithTag("dispatch-choose-teams").performClick();compose.onNodeWithText("Rendered Team").performClick();compose.onNodeWithTag("dispatch-team-apply").performClick();compose.onNodeWithTag("dispatch-new-visit").performScrollToNode(hasTestTag("dispatch-add-item"));compose.onNodeWithTag("dispatch-add-item").performClick();compose.onNodeWithTag("dispatch-equipment-render-equipment").performClick();compose.onNodeWithTag("dispatch-work-task").performTextInput("New rendered work");compose.onNodeWithTag("dispatch-work-item-save").performClick();capture("dispatch-new-visit","dispatch-new-visit-editor-top.png");compose.onNodeWithTag("dispatch-new-visit").performScrollToNode(hasTestTag("dispatch-save-visit"));compose.onNodeWithTag("dispatch-save-visit").performClick();compose.onNodeWithTag("open-render-editor").assertIsDisplayed();assertEquals(3,runBlocking{service.outboxVisits().size})
            compose.runOnUiThread{compose.activity.setContent{ServiceLoopTheme(false){val nav=rememberNavController();DispatchExportReviewScreen(PaddingValues(),nav,ids,service)}}};compose.onNodeWithTag("dispatch-export-sender").performTextInput("Prototype coordinator");compose.waitUntil{compose.onAllNodesWithTag("dispatch-export-summary").fetchSemanticsNodes().isNotEmpty()};compose.onAllNodesWithText("New → generation 1").assertCountEquals(2);capture("dispatch-export-review","dispatch-export-review.png")
        }finally{db.close()}
    }    @Test fun packageCompositionDoesNotCreateLocalPlannerVisit(){
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),ServiceLoopDatabase::class.java).build();try{val dao=db.serviceLoopDao();runBlocking{dao.insertCustomers(listOf(CustomerEntity("c","CU-I","Customer")));dao.insertSites(listOf(SiteEntity("s","c","ST-I","Site",null,null)));dao.insertEquipment(listOf(EquipmentEntity("e","s","EQ-I",null,"Pump",null,null,null,null)));val svc=DispatchPackageService(db);val identity=svc.identity();val alex=TechnicianIdentity("alex-instrumented-id","Alex");svc.importTechnician(identity);svc.importTechnician(alex);val first=svc.createTeam("Instrumentation north");val second=svc.createTeam("Instrumentation emergency");svc.setTeamMember(first,identity.technicianId,true,true);svc.setTeamMember(first,alex.technicianId,true,false);svc.setTeamMember(second,alex.technicianId,true,true);val outbox=svc.createOutboxVisit("JOB-UI","s","2026-09-20","09:00","Europe/Bucharest","Rear entrance",listOf(first,second));svc.addOutboxItem(outbox,"e","John only",null,null,listOf(identity.technicianId));svc.addOutboxItem(outbox,"e","John and Alex",null,null,listOf(identity.technicianId,alex.technicianId));svc.addOutboxItem(outbox,"e","Everyone",null,null,emptyList());val exported=svc.exportPackage(listOf(outbox),"Prototype coordinator");assertEquals(2,exported.visits.single().teams.size);assertEquals(listOf(1,2,0),exported.visits.single().work.map{it.assignedTechnicians.size});assertEquals(0,dao.visitCount())}}finally{db.close()}
    }
}
