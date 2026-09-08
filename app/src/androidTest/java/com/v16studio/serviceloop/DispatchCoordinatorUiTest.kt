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
        compose.onNodeWithTag("root-nav-home").performClick();compose.onNodeWithText("Settings").performClick();compose.onNodeWithText("Coordinator tools · Experimental").performClick();compose.onNodeWithTag("coordinator-tools-switch").performClick();pressBack();pressBack()
        compose.onNodeWithTag("root-nav-work").performClick();compose.onNodeWithText("Visits",useUnmergedTree=true).performClick();compose.onNodeWithTag("work-visits-list").performScrollToNode(hasText("Create work package"));compose.onNodeWithText("Create work package").assertIsDisplayed()
    }

    @Test fun packageCompositionDoesNotCreateLocalPlannerVisit(){
        prefs.edit().putBoolean(COORDINATOR_ENABLED,true).commit();val app=compose.activity.application as ServiceLoopApplication;val dao=app.container.database.serviceLoopDao();val before=runBlocking{dao.visitCount()};val site=runBlocking{dao.allSites().first{s->dao.equipmentForSite(s.id).isNotEmpty()}};val equipment=runBlocking{dao.equipmentForSite(site.id).first()}
        compose.onNodeWithTag("root-nav-work").performClick();compose.onNodeWithText("Visits",useUnmergedTree=true).performClick();compose.onNodeWithTag("work-visits-list").performScrollToNode(hasText("Create work package"));compose.onNodeWithText("Create work package").performClick()
        compose.onNodeWithTag("dispatch-sender").performTextInput("Prototype coordinator");compose.onNodeWithTag("dispatch-site-${site.id}").performClick();compose.onNodeWithTag("dispatch-composer").performScrollToNode(androidx.compose.ui.test.hasTestTag("dispatch-equipment-${equipment.id}"));compose.onNodeWithTag("dispatch-equipment-${equipment.id}").performClick();compose.onNodeWithTag("dispatch-task").performTextInput("Prototype inspection");compose.onNodeWithTag("dispatch-composer").performScrollToNode(androidx.compose.ui.test.hasTestTag("add-dispatch-visit"));compose.onNodeWithTag("add-dispatch-visit").performClick()
        assertEquals(before,runBlocking{dao.visitCount()})
    }
}
