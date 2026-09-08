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
        compose.onNodeWithTag("root-nav-work").performClick();compose.onNodeWithText("Visits",useUnmergedTree=true).performClick();compose.onNodeWithTag("work-visits-list").performScrollToNode(hasText("Create work package"));compose.onNodeWithText("Create work package").assertIsDisplayed()
    }

    @Test fun packageCompositionDoesNotCreateLocalPlannerVisit(){
        prefs.edit().putBoolean(COORDINATOR_ENABLED,true).commit();val app=compose.activity.application as ServiceLoopApplication;val db=app.container.database;val dao=db.serviceLoopDao();val before=runBlocking{dao.visitCount()};runBlocking{val svc=com.v16studio.serviceloop.data.DispatchPackageService(db);val identity=svc.identity();svc.importTechnician(identity);val team=svc.createTeam("Instrumentation team");svc.setTeamMember(team,identity.technicianId,true,true);val site=dao.allSites().first{s->dao.equipmentForSite(s.id).isNotEmpty()};val equipment=dao.equipmentForSite(site.id).first();val outbox=svc.createOutboxVisit("JOB-UI",site.id,"2026-09-20","09:00","Europe/Bucharest",null,listOf(team));svc.addOutboxItem(outbox,equipment.id,"Prototype inspection",null,null,emptyList());svc.exportPackage(listOf(outbox),"Prototype coordinator")}
        assertEquals(before,runBlocking{dao.visitCount()})
    }
}
