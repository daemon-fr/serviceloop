package com.v16studio.serviceloop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.pressBack
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.ui.COORDINATOR_ENABLED
import com.v16studio.serviceloop.ui.DISPATCH_PREFS
import org.junit.After
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

    @Test fun technicianRootsStayThreeAndCoordinatorActionIsPreferenceGated(){
        compose.onNodeWithTag("root-nav-home").assertIsDisplayed();compose.onNodeWithTag("root-nav-work").assertIsDisplayed();compose.onNodeWithTag("root-nav-customers").assertIsDisplayed()
        compose.onNodeWithTag("root-nav-work").performClick();compose.onNodeWithText("Visits",useUnmergedTree=true).performClick();compose.onNodeWithTag("work-visits-list").performScrollToNode(hasText("Import work package"));compose.onNodeWithText("Import work package").assertIsDisplayed();compose.onNodeWithText("Create work package").assertDoesNotExist()
        compose.onNodeWithTag("root-nav-home").performClick();compose.onNodeWithText("Settings").performClick();compose.onNodeWithText("Coordinator tools · Experimental").performClick();compose.onNodeWithText("Open Technician identity").performClick();compose.onNodeWithTag("technician-identity").assertIsDisplayed();compose.onNodeWithText("Copy ID").assertIsDisplayed();compose.onNodeWithText("Share identity").assertIsDisplayed();pressBack();compose.onNodeWithTag("coordinator-tools-switch").performClick();pressBack();pressBack()
        compose.onNodeWithTag("root-nav-work").performClick();compose.onNodeWithText("Visits",useUnmergedTree=true).performClick();compose.onNodeWithTag("work-visits-list").performScrollToNode(hasText("Create work package"));compose.onNodeWithText("Create work package").performClick();compose.onNodeWithTag("dispatch-outbox").assertIsDisplayed();listOf("Service date · YYYY-MM-DD","ZoneId","Dispatch instructions","Teams · select one or more").forEach{label->compose.onNodeWithTag("dispatch-outbox").performScrollToNode(hasText(label));compose.onNodeWithText(label).assertIsDisplayed()}
    }

    @Test fun packageCompositionDoesNotCreateLocalPlannerVisit(){
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),ServiceLoopDatabase::class.java).build();try{val dao=db.serviceLoopDao();runBlocking{dao.insertCustomers(listOf(com.v16studio.serviceloop.data.CustomerEntity("c","CU-I","Customer")));dao.insertSites(listOf(com.v16studio.serviceloop.data.SiteEntity("s","c","ST-I","Site",null,null)));dao.insertEquipment(listOf(com.v16studio.serviceloop.data.EquipmentEntity("e","s","EQ-I",null,"Pump",null,null,null,null)));val svc=com.v16studio.serviceloop.data.DispatchPackageService(db);val identity=svc.identity();val alex=com.v16studio.serviceloop.data.TechnicianIdentity("alex-instrumented-id","Alex");svc.importTechnician(identity);svc.importTechnician(alex);val first=svc.createTeam("Instrumentation north");val second=svc.createTeam("Instrumentation emergency");svc.setTeamMember(first,identity.technicianId,true,true);svc.setTeamMember(first,alex.technicianId,true,false);svc.setTeamMember(second,alex.technicianId,true,true);val outbox=svc.createOutboxVisit("JOB-UI","s","2026-09-20","09:00","Europe/Bucharest","Rear entrance",listOf(first,second));svc.addOutboxItem(outbox,"e","John only",null,null,listOf(identity.technicianId));svc.addOutboxItem(outbox,"e","John and Alex",null,null,listOf(identity.technicianId,alex.technicianId));svc.addOutboxItem(outbox,"e","Everyone",null,null,emptyList());val exported=svc.exportPackage(listOf(outbox),"Prototype coordinator");assertEquals(2,exported.visits.single().teams.size);assertEquals(listOf(1,2,0),exported.visits.single().work.map{it.assignedTechnicians.size});assertEquals(0,dao.visitCount())}}finally{db.close()}
    }
}
