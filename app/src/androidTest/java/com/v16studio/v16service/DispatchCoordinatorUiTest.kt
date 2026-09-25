package com.v16studio.v16service

import android.graphics.Bitmap
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.setContent
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
import com.v16studio.v16service.data.*
import com.v16studio.v16service.domain.TemplateSummary
import com.v16studio.v16service.ui.*
import com.v16studio.v16service.ui.theme.V16ServiceTheme
import java.io.File
import java.time.LocalDate
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
    private var previousRole:String?=null
    @Before fun off(){previousRole=prefs.getString(TEAM_ROLE,null);prefs.edit().putString(TEAM_ROLE,TeamRole.SOLO.name).remove(COORDINATOR_ENABLED).commit()}
    @After fun restorePreference(){prefs.edit().apply{if(previousRole==null)remove(TEAM_ROLE) else putString(TEAM_ROLE,previousRole)}.commit()}
    private fun disposeCompositionAndClose(database: V16ServiceDatabase) {
        try {
            compose.runOnUiThread { compose.activity.setContent {} }
            compose.waitForIdle()
        } finally {
            database.close()
        }
    }

    @Test fun coordinatorWorkspaceMovesFromSettingsToReactiveHomeActions(){
        compose.onNodeWithTag("root-nav-home").assertIsDisplayed()
        compose.onNodeWithTag("root-nav-work").assertIsDisplayed()
        compose.onNodeWithTag("root-nav-customers").assertIsDisplayed()
        compose.onNodeWithTag("home-tab-TEAM").performClick()
        compose.onNodeWithTag("team-role-identity").performClick()
        compose.onNodeWithTag("team-role-COORDINATOR").performClick()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("team-dispatch").assertIsDisplayed().performClick()
        compose.onNodeWithTag("dispatch-outbox").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("dispatch-new-visit-floating").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("dispatch-new-visit-bottom").fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodesWithTag("dispatch-new-visit-floating").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("dispatch-new-visit-floating").performClick()
        else { compose.onNodeWithTag("dispatch-outbox-list").performScrollToNode(hasTestTag("dispatch-new-visit-bottom")); compose.onNodeWithTag("dispatch-new-visit-bottom").performClick() }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("new-visit-form").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("new-visit-form").assertIsDisplayed()
    }

    @Test fun memberSeesImportOnHomeAndHistoryLivesInSettingsData(){
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("History").assertIsDisplayed().performClick()
        compose.onNodeWithText("History",useUnmergedTree=true).assertIsDisplayed()
        pressBack()
        pressBack()
        compose.onNodeWithTag("home-tab-TEAM").performClick()
        compose.onNodeWithTag("team-role-identity").performClick()
        compose.onNodeWithTag("team-role-SUBCONTRACTOR").performClick()
        pressBack()
        compose.onNodeWithTag("team-import-work").assertIsDisplayed()
        compose.onNodeWithTag("coordinator-home-actions").assertDoesNotExist()
        compose.onNodeWithText("Work").performClick()
        compose.onNodeWithTag("work-more-actions").assertDoesNotExist()
    }

    @Test fun memberIdentityShowsPersistedIdBeforeDesignationWithoutReportNameSection(){
        compose.onNodeWithTag("home-tab-TEAM").performClick()
        compose.onNodeWithTag("team-role-identity").performClick()
        compose.onNodeWithTag("team-role-SUBCONTRACTOR").performClick()
        pressBack()
        compose.onNodeWithTag("team-id-row").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("team-local-id").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Your technician ID").assertIsDisplayed()
        compose.onNodeWithTag("team-local-id").assertIsDisplayed()
        compose.onNodeWithTag("technician-designation").assertIsDisplayed()
        compose.onNodeWithText("Actual report name").assertDoesNotExist()
        compose.onNodeWithTag("technician-report-name").assertDoesNotExist()
        compose.onNodeWithTag("open-business-report-identity").assertDoesNotExist()
        val idBounds = compose.onNodeWithTag("team-local-id").fetchSemanticsNode().boundsInRoot
        val designationBounds = compose.onNodeWithTag("technician-designation").fetchSemanticsNode().boundsInRoot
        assertTrue(idBounds.bottom <= designationBounds.top)
        val identityScreenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "b051-identity-screen.png").outputStream().use { output ->
            check(identityScreenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        identityScreenshot.recycle()
    }

    @Test fun coordinatorRoleUsesOneHelperParagraphAndNormalGaps(){
        compose.onNodeWithTag("home-tab-TEAM").performClick()
        compose.onNodeWithTag("team-role-identity").performClick()
        compose.onNodeWithTag("team-role-COORDINATOR").performClick()
        val role = compose.onNodeWithTag("team-role-COORDINATOR").fetchSemanticsNode().boundsInRoot
        val helper = compose.onNodeWithTag("team-role-helper").fetchSemanticsNode().boundsInRoot
        compose.onNode(hasText("Roles only control local file-based workflows. No account, synchronization, or shared database is created. Coordinator tools are available from Home.", substring = true)).assertIsDisplayed()
        assertTrue(helper.top >= role.bottom)
    }

    @Test fun memberExternalPackageOpensImportReview(){
        compose.onNodeWithTag("home-tab-TEAM").performClick()
        compose.onNodeWithTag("team-role-identity").performClick()
        compose.onNodeWithTag("team-role-SUBCONTRACTOR").performClick()
        pressBack()
        compose.runOnUiThread { prefs.edit().putString(TEAM_ROLE, TeamRole.SUBCONTRACTOR.name).commit() }
        compose.runOnUiThread { compose.activity.onNewIntent(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///sdcard/Download/sample.v16service"), V16_SERVICE_SYNC_MIME)) }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag("v16-service-import").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("choose-v16service-file").assertIsDisplayed()
    }

    @Test fun soloExternalPackageShowsRoleExplanationWithoutImport(){
        compose.onNodeWithTag("home-tab-TEAM").performClick()
        compose.onNodeWithTag("team-role-identity").performClick()
        compose.onNodeWithTag("team-role-SOLO").performClick()
        pressBack()
        compose.runOnUiThread { prefs.edit().putString(TEAM_ROLE, TeamRole.SOLO.name).commit() }
        compose.runOnUiThread { assertEquals(TeamRole.SOLO, compose.activity.teamRole()) }
        compose.runOnUiThread { compose.activity.onNewIntent(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///sdcard/Download/sample.v16service"), V16_SERVICE_SYNC_MIME)) }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag("v16-service-import").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("choose-v16service-file").assertIsDisplayed()
    }

    @Test fun soloExternalSendPackageShowsSameExplanation(){
        compose.onNodeWithTag("home-tab-TEAM").performClick()
        compose.onNodeWithTag("team-role-identity").performClick()
        compose.onNodeWithTag("team-role-SOLO").performClick()
        pressBack()
        compose.runOnUiThread { prefs.edit().putString(TEAM_ROLE, TeamRole.SOLO.name).commit() }
        compose.runOnUiThread { compose.activity.onNewIntent(Intent(Intent.ACTION_SEND).setType(V16_SERVICE_SYNC_MIME).putExtra(Intent.EXTRA_STREAM, Uri.parse("file:///sdcard/Download/sample.v16service"))) }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag("v16-service-import").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("choose-v16service-file").assertIsDisplayed()
    }

    @Test fun reopeningSameExternalPackageAfterBecomingMemberUsesFreshRole(){
        val packageIntent = { Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///sdcard/Download/reopen.v16service"), V16_SERVICE_SYNC_MIME) }
        compose.onNodeWithTag("home-tab-TEAM").performClick()
        compose.onNodeWithTag("team-role-identity").performClick()
        compose.onNodeWithTag("team-role-SOLO").performClick()
        pressBack()
        compose.runOnUiThread { prefs.edit().putString(TEAM_ROLE, TeamRole.SOLO.name).commit(); compose.activity.onNewIntent(packageIntent()) }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag("v16-service-import").fetchSemanticsNodes().isNotEmpty() }
        pressBack()
        compose.onNodeWithTag("home-tab-TEAM").performClick()
        compose.onNodeWithTag("team-role-identity").performClick()
        compose.onNodeWithTag("team-role-SUBCONTRACTOR").performClick()
        pressBack()
        compose.runOnUiThread { prefs.edit().putString(TEAM_ROLE, TeamRole.SUBCONTRACTOR.name).commit(); compose.activity.onNewIntent(packageIntent()) }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag("v16-service-import").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("choose-v16service-file").assertIsDisplayed()
    }

    @Test fun coordinatorExternalPackageShowsRoleExplanationWithoutImport(){
        compose.onNodeWithTag("home-tab-TEAM").performClick()
        compose.onNodeWithTag("team-role-identity").performClick()
        compose.onNodeWithTag("team-role-COORDINATOR").performClick()
        pressBack()
        compose.runOnUiThread { prefs.edit().putString(TEAM_ROLE, TeamRole.COORDINATOR.name).commit() }
        compose.runOnUiThread { compose.activity.onNewIntent(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///sdcard/Download/sample.v16service"), V16_SERVICE_SYNC_MIME)) }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag("v16-service-import").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("choose-v16service-file").assertIsDisplayed()
    }

    @Test fun listFirstOutboxHandlesLargeDirectoryAndExplicitSelection(){
        val context=ApplicationProvider.getApplicationContext<Context>();val db=Room.inMemoryDatabaseBuilder(context,V16ServiceDatabase::class.java).allowMainThreadQueries().build();val service=DispatchPackageService(db);val ids=runBlocking{val dao=db.v16ServiceDao();dao.insertCustomers(listOf(CustomerEntity("customer","CU-LARGE","Large Customer")));val sites=(1..120).map{SiteEntity("site-$it","customer","SITE-${it.toString().padStart(3,'0')}","Site $it","$it Test Road",null)};dao.insertSites(sites);dao.insertEquipment(sites.take(3).mapIndexed{index,site->EquipmentEntity("equipment-$index",site.id,"EQ-$index",null,"Pump $index",null,null,null,null)});val self=service.identity();service.importTechnician(self);val team=service.createTeam("North Team");service.setTeamMember(team,self.technicianId,true,true);sites.take(3).mapIndexed{index,site->service.saveOutboxVisit(DispatchOutboxEditorDraft(managerReference="JOB-$index",siteId=site.id,serviceDate="2026-09-${20+index}",appointmentLocalTime="${(8+index).toString().padStart(2,'0')}:00",appointmentZoneId="Europe/Bucharest",teamIds=listOf(team),items=listOf(DispatchOutboxItemDraft("item-$index","equipment-$index","Inspect pump $index"))))}}
        try{compose.runOnUiThread{compose.activity.setContent{V16ServiceTheme(false){val nav=rememberNavController();NavHost(nav,"outbox"){composable("outbox"){DispatchOutboxScreen(PaddingValues(),nav,LocalDate.of(2026,4,15),service,db)};composable("dispatch/visit/new"){Text("New editor",Modifier.testTag("fake-new-editor"))};composable("dispatch/visit/{id}"){Text("Editor opened",Modifier.testTag("fake-editor"))};composable("dispatch/export-review"){Text("Review",Modifier.testTag("fake-review"))}}}}};compose.onNodeWithTag("dispatch-outbox").assertIsDisplayed();compose.waitUntil(10_000){compose.onAllNodesWithTag("dispatch-new-visit-floating").fetchSemanticsNodes().isNotEmpty()||compose.onAllNodesWithTag("dispatch-new-visit-bottom").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithTag("dispatch-outbox-list").performScrollToNode(hasTestTag("dispatch-new-visit-bottom"));compose.onNodeWithTag("dispatch-new-visit-bottom").assertIsDisplayed();compose.onNodeWithTag("dispatch-search").assertIsDisplayed();compose.onNodeWithTag("dispatch-status-filter").assertIsDisplayed();compose.onNodeWithTag("dispatch-date-filter").assertIsDisplayed();compose.onNodeWithText("Sender label").assertDoesNotExist();compose.onNodeWithText("Create outbox Visit").assertDoesNotExist();compose.onNodeWithText("SITE-120",substring=true).assertDoesNotExist();compose.onNodeWithText("Export selected (0)").assertDoesNotExist()
            compose.onNodeWithTag("dispatch-outbox-list").performScrollToNode(hasTestTag("dispatch-select-${ids[0]}"));compose.onNodeWithTag("dispatch-select-${ids[0]}").performClick();compose.onNodeWithTag("dispatch-new-visit-floating").assertDoesNotExist();compose.onNodeWithTag("dispatch-outbox-list").performScrollToNode(hasTestTag("dispatch-select-${ids[1]}"));compose.onNodeWithTag("dispatch-select-${ids[1]}").performClick();compose.onNodeWithText("2 selected").assertIsDisplayed();compose.onNodeWithText("Export (2)").assertIsDisplayed();assertTrue(compose.onAllNodesWithText("Draft").fetchSemanticsNodes().size>=2);compose.onNodeWithTag("dispatch-select-${ids[0]}").assertExists();compose.onNodeWithTag("dispatch-outbox-visit-${ids[0]}").assertExists()
            compose.onNodeWithTag("dispatch-search").performTextInput("JOB-0");compose.waitUntil{compose.onAllNodesWithText("1 selected").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithTag("dispatch-outbox-list").performScrollToNode(hasTestTag("dispatch-outbox-visit-${ids[0]}"));compose.onNodeWithTag("dispatch-outbox-visit-${ids[0]}").performClick();compose.onNodeWithTag("fake-editor").assertIsDisplayed()
        }finally{disposeCompositionAndClose(db)}
    }

    @Test fun sitePickerSearchFindsLateLargeDirectoryEntry(){
        val customers=listOf(CustomerEntity("customer","CU-SEARCH","Search Customer"));val sites=(1..150).map{SiteEntity("site-$it","customer","SITE-${it.toString().padStart(3,'0')}","Branch $it","$it Long Road",null)};var selected:String?=null
        compose.runOnUiThread{compose.activity.setContent{V16ServiceTheme(false){DispatchSitePickerDialog(sites,customers,{}, {selected=it})}}};compose.onNodeWithTag("dispatch-site-picker").assertIsDisplayed();compose.onNodeWithTag("dispatch-site-search").performTextInput("SITE-150");compose.onNodeWithTag("dispatch-site-picker-list").performScrollToNode(hasTestTag("dispatch-site-site-150"));compose.onNodeWithTag("dispatch-site-site-150",useUnmergedTree=true).assertIsDisplayed();compose.onNodeWithTag("dispatch-site-site-150",useUnmergedTree=true).performClick();assertEquals("site-150",selected)
    }


    @Test fun renderedEditorAndExportReviewExposeSeparateWorkflowSurfaces(){
        val context=ApplicationProvider.getApplicationContext<Context>();val db=Room.inMemoryDatabaseBuilder(context,V16ServiceDatabase::class.java).allowMainThreadQueries().build();val service=DispatchPackageService(db);val ids=runBlocking{val dao=db.v16ServiceDao();dao.insertCustomers(listOf(CustomerEntity("render-customer","CU-RENDER","Rendered Customer")));dao.insertSites(listOf(SiteEntity("render-site","render-customer","ST-RENDER","Rendered Site","1 Render Road",null)));dao.insertEquipment(listOf(EquipmentEntity("render-equipment","render-site","EQ-RENDER",null,"Rendered Pump",null,null,null,null)));dao.insertReusableTemplate(ReusableTemplateEntity("render-template","IT-RENDER","Rendered checklist","render-template-revision","ACTIVE",1L));dao.insertReusableTemplateRevision(ReusableTemplateRevisionEntity("render-template-revision","render-template",1,"Rendered checklist",1L));dao.insertReusableTemplateItems(listOf(ReusableTemplateItemEntity("render-template-item","render-template-revision",1,"Check system","STATUS",null,true,null)));val self=service.identity();service.importTechnician(self);val team=service.createTeam("Rendered Team");service.setTeamMember(team,self.technicianId,true,true);listOf("A","B").mapIndexed{index,ref->service.saveOutboxVisit(DispatchOutboxEditorDraft(managerReference="JOB-RENDER-$ref",siteId="render-site",serviceDate="2026-10-0${index+3}",appointmentLocalTime="09:00",appointmentZoneId="Europe/Bucharest",teamIds=listOf(team),items=listOf(DispatchOutboxItemDraft("render-item-$ref","render-equipment","Inspect rendered pump $ref"))))}}
        try{
            compose.runOnUiThread{compose.activity.setContent{V16ServiceTheme(false){val nav=rememberNavController();CompositionLocalProvider(LocalDetailBackInterceptor provides remember{mutableStateOf<(() -> Unit)?>(null)}){NavHost(nav,"start"){composable("start"){Button({nav.navigate("editor")},Modifier.testTag("open-render-editor")){Text("Open editor")}};composable("editor"){DispatchVisitEditorScreen(PaddingValues(),nav,null,LocalDate.of(2026,4,15),service,db,templatesOverride=listOf(TemplateSummary("render-template","IT-RENDER","Rendered checklist",1,1,"ACTIVE")))}}}}}}
            compose.onNodeWithTag("open-render-editor").performClick();compose.onNodeWithTag("dispatch-new-visit").performScrollToNode(hasTestTag("visit-site-render-site-select"));compose.onNodeWithTag("visit-site-render-site-select").performClick();compose.onNodeWithTag("visit-site-continue").performClick()
            compose.onNodeWithTag("dispatch-new-visit").performScrollToNode(hasTestTag("dispatch-choose-teams"));compose.onNodeWithTag("dispatch-choose-teams").performClick();compose.onNodeWithText("Rendered Team").performClick();compose.onNodeWithTag("dispatch-team-apply").performClick()
            compose.onNodeWithTag("dispatch-new-visit").performScrollToNode(hasTestTag("field-task-name-required"));compose.onNodeWithTag("task-subject-EQUIPMENT").performClick();compose.onNodeWithTag("task-equipment-render-equipment").performClick();compose.onNodeWithTag("field-task-name-required").performTextInput("New rendered work");compose.onNodeWithTag("task-template-selector").performClick();compose.onNodeWithTag("task-template-selector-option-renderedchecklistv1").performClick();compose.onNodeWithTag("add-task").performClick()
            compose.onNodeWithTag("visit-setup-list").performScrollToNode(hasTestTag("dispatch-save-visit"));compose.onNodeWithTag("dispatch-save-visit").performClick();compose.waitUntil(10_000){compose.onAllNodesWithTag("open-render-editor").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithTag("open-render-editor").assertIsDisplayed();assertEquals(3,runBlocking{service.outboxVisits().size})
            compose.runOnUiThread{compose.activity.setContent{V16ServiceTheme(false){val nav=rememberNavController();DispatchExportReviewScreen(PaddingValues(),nav,ids,service)}}};compose.onNodeWithTag("dispatch-export-sender").performTextInput("Prototype coordinator");compose.waitUntil(10_000){compose.onAllNodesWithText("First export · Version 1").fetchSemanticsNodes().size==2};compose.onAllNodesWithText("First export · Version 1").assertCountEquals(2)
        }finally{disposeCompositionAndClose(db)}
    }    @Test fun packageCompositionDoesNotCreateLocalPlannerVisit(){
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),V16ServiceDatabase::class.java).build();try{val dao=db.v16ServiceDao();runBlocking{dao.insertCustomers(listOf(CustomerEntity("c","CU-I","Customer")));dao.insertSites(listOf(SiteEntity("s","c","ST-I","Site",null,null)));dao.insertEquipment(listOf(EquipmentEntity("e","s","EQ-I",null,"Pump",null,null,null,null)));val svc=DispatchPackageService(db);val identity=svc.identity();val alex=TechnicianIdentity("alex-instrumented-id","Alex");svc.importTechnician(identity);svc.importTechnician(alex);val first=svc.createTeam("Instrumentation north");val second=svc.createTeam("Instrumentation emergency");svc.setTeamMember(first,identity.technicianId,true,true);svc.setTeamMember(first,alex.technicianId,true,false);svc.setTeamMember(second,alex.technicianId,true,true);val outbox=svc.createOutboxVisit("JOB-UI","s","2026-09-20","09:00","Europe/Bucharest","Rear entrance",listOf(first,second));svc.addOutboxItem(outbox,"e","John only",null,null,listOf(identity.technicianId));svc.addOutboxItem(outbox,"e","John and Alex",null,null,listOf(identity.technicianId,alex.technicianId));svc.addOutboxItem(outbox,"e","Everyone",null,null,emptyList());val exported=svc.exportPackage(listOf(outbox),"Prototype coordinator");assertEquals(2,exported.visits.single().teams.size);assertEquals(listOf(1,2,0),exported.visits.single().work.map{it.assignedTechnicians.size});assertEquals(0,dao.visitCount())}}finally{disposeCompositionAndClose(db)}
    }

    @Test fun batchCancelOpensOnlyCancellationDialogAndPersistsCanceledState(){
        val context=ApplicationProvider.getApplicationContext<Context>();val db=Room.inMemoryDatabaseBuilder(context,V16ServiceDatabase::class.java).allowMainThreadQueries().build();val service=DispatchPackageService(db)
        val id=runBlocking{val dao=db.v16ServiceDao();dao.insertCustomers(listOf(CustomerEntity("cancel-customer","CU-CANCEL","Cancel customer")));dao.insertSites(listOf(SiteEntity("cancel-site","cancel-customer","ST-CANCEL","Cancel site",null,null)));dao.insertEquipment(listOf(EquipmentEntity("cancel-equipment","cancel-site","EQ-CANCEL",null,"Cancel pump",null,null,null,null)));val self=service.identity();service.importTechnician(self);val team=service.createTeam("Cancel team");service.setTeamMember(team,self.technicianId,true,true);service.createOutboxVisit("JOB-CANCEL-UI","cancel-site","2026-09-20","09:00","Europe/Bucharest",null,listOf(team)).also{service.addOutboxItem(it,"cancel-equipment","Inspect",null,null,emptyList())}}
        try{
            compose.runOnUiThread{compose.activity.setContent{V16ServiceTheme(false){val nav=rememberNavController();NavHost(nav,"outbox"){composable("outbox"){DispatchOutboxScreen(PaddingValues(),nav,LocalDate.of(2026,9,11),service,db)}}}}}
            compose.waitUntil(10_000){compose.onAllNodesWithTag("dispatch-outbox-visit-$id").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("dispatch-select-$id").performClick();compose.onNodeWithTag("dispatch-cancel-selected").performClick()
            compose.onNodeWithText("Cancel selected Visits").assertIsDisplayed();compose.onNodeWithTag("dispatch-status-confirm").assertDoesNotExist();compose.onNodeWithTag("dispatch-cancel-reason").performTextInput("Coordinator cancellation");compose.onNodeWithText("Apply").performClick()
            compose.waitUntil(10_000){runBlocking{db.dispatchDao().outboxVisit(id)?.outboxStatus==DispatchOutboxStatus.CANCELED}};compose.waitUntil(10_000){compose.onAllNodesWithText("No visits match these filters").fetchSemanticsNodes().isNotEmpty()};compose.waitForIdle()
        }finally{disposeCompositionAndClose(db)}
    }

    @Test fun individualDraftCancelActionUsesReasonAndReloadsReadOnlyCanceledEditor(){
        val context=ApplicationProvider.getApplicationContext<Context>();val db=Room.inMemoryDatabaseBuilder(context,V16ServiceDatabase::class.java).allowMainThreadQueries().build();val service=DispatchPackageService(db)
        val id=runBlocking{val dao=db.v16ServiceDao();dao.insertCustomers(listOf(CustomerEntity("single-customer","CU-SINGLE","Single customer")));dao.insertSites(listOf(SiteEntity("single-site","single-customer","ST-SINGLE","Single site",null,null)));dao.insertEquipment(listOf(EquipmentEntity("single-equipment","single-site","EQ-SINGLE",null,"Single pump",null,null,null,null)));val self=service.identity();service.importTechnician(self);val team=service.createTeam("Single team");service.setTeamMember(team,self.technicianId,true,true);service.createOutboxVisit("JOB-SINGLE-UI","single-site","2026-09-20","09:00","Europe/Bucharest",null,listOf(team)).also{service.addOutboxItem(it,"single-equipment","Inspect",null,null,emptyList())}}
        try{
            compose.runOnUiThread{compose.activity.setContent{V16ServiceTheme(false){val nav=rememberNavController();CompositionLocalProvider(LocalDetailBackInterceptor provides remember{mutableStateOf<(() -> Unit)?>(null)}){DispatchVisitEditorScreen(PaddingValues(),nav,id,LocalDate.of(2026,9,11),service,db)}}}}
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Single customer · Single site").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("visit-setup-list").performScrollToNode(hasTestTag("dispatch-cancel-visit"))
            compose.onNodeWithTag("dispatch-cancel-visit").assertIsDisplayed()
            compose.onNodeWithTag("dispatch-cancel-visit").performClick()
            compose.onNodeWithTag("dispatch-cancel-dialog").assertIsDisplayed()
            compose.onNodeWithTag("dispatch-cancel-reason").performTextInput("No longer needed")
            compose.onNodeWithTag("dispatch-cancel-confirm").performClick()
            compose.waitUntil(10_000) { runBlocking { db.dispatchDao().outboxVisit(id)?.outboxStatus == DispatchOutboxStatus.CANCELED } }
            compose.onNodeWithTag("visit-setup-list").performScrollToNode(hasText("Read-only"))
            compose.onNodeWithText("Read-only").assertIsDisplayed()
            compose.onNodeWithTag("dispatch-cancel-visit").assertDoesNotExist()
            compose.onNodeWithTag("dispatch-reopen-visit").assertDoesNotExist()
        }finally{disposeCompositionAndClose(db)}
    }

    @Test fun individualConcludedVisitReopensToDispatchedAndRemainsFunctional(){
        val context=ApplicationProvider.getApplicationContext<Context>();val db=Room.inMemoryDatabaseBuilder(context,V16ServiceDatabase::class.java).allowMainThreadQueries().build();val service=DispatchPackageService(db)
        val id=runBlocking{val dao=db.v16ServiceDao();dao.insertCustomers(listOf(CustomerEntity("concluded-customer","CU-CONCLUDED","Concluded customer")));dao.insertSites(listOf(SiteEntity("concluded-site","concluded-customer","ST-CONCLUDED","Concluded site",null,null)));dao.insertEquipment(listOf(EquipmentEntity("concluded-equipment","concluded-site","EQ-CONCLUDED",null,"Concluded pump",null,null,null,null)));val self=service.identity();service.importTechnician(self);val team=service.createTeam("Concluded team");service.setTeamMember(team,self.technicianId,true,true);service.createOutboxVisit("JOB-CONCLUDED-UI","concluded-site","2026-09-20","09:00","Europe/Bucharest",null,listOf(team)).also{service.addOutboxItem(it,"concluded-equipment","Inspect",null,null,emptyList());service.createExportFile(listOf(it),"Coordinator",context.cacheDir);service.concludeOutboxVisits(listOf(it))}}
        try{
            compose.runOnUiThread{compose.activity.setContent{V16ServiceTheme(false){val nav=rememberNavController();CompositionLocalProvider(LocalDetailBackInterceptor provides remember{mutableStateOf<(() -> Unit)?>(null)}){DispatchVisitEditorScreen(PaddingValues(),nav,id,LocalDate.of(2026,9,11),service,db)}}}}
            compose.waitUntil(10_000){compose.onAllNodesWithText("Read-only").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithTag("visit-setup-list").performScrollToNode(hasTestTag("dispatch-reopen-visit"));compose.onNodeWithTag("dispatch-cancel-visit").assertDoesNotExist();compose.onNodeWithTag("dispatch-reopen-visit").assertIsDisplayed();compose.onNodeWithTag("dispatch-reopen-visit").performClick()
            compose.waitUntil(10_000){runBlocking{db.dispatchDao().outboxVisit(id)?.outboxStatus==DispatchOutboxStatus.DISPATCHED}};compose.waitUntil(10_000){compose.onAllNodesWithTag("dispatch-save-visit").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithTag("dispatch-reopen-visit").assertDoesNotExist();compose.onNodeWithTag("dispatch-cancel-visit").assertIsDisplayed()
        }finally{disposeCompositionAndClose(db)}
    }
}
