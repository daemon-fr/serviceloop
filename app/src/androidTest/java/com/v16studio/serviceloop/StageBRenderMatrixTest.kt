package com.v16studio.serviceloop

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import com.v16studio.serviceloop.domain.VisitDetail
import com.v16studio.serviceloop.domain.VisitLine
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.UiState
import com.v16studio.serviceloop.ui.VisitDetailScreen
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StageBRenderMatrixTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    private data class Shot(val group:String,val name:String,val route:String?)

    private fun render(dark:Boolean) {
        val app=compose.activity.application as ServiceLoopApplication
        val dao=app.container.database.serviceLoopDao()
        val record=runBlocking{dao.finalRecordForVisit(FixtureIds.VISIT_1)} ?: error("V-001 final record fixture is required")
        val revision=runBlocking{dao.finalRevisions(record.id).first()}
        val route=mutableStateOf<String?>(null)
        val vm=ServiceLoopViewModel(app.container.repository,app.container.reportService,app.container.restrictedRecoveryState,app.container.businessDateSignal,app.container.reminderCoordinator,app.container.calendarCoordinator){app.container.startup.await()}
        compose.activity.setContent{ServiceLoopTheme(darkTheme=dark){ServiceLoopApp(vm,route.value)}}
        val shots=listOf(
            Shot("B1","home",null),Shot("B1","work","work?tab=DUE_SERVICES"),Shot("B1","customers","customers"),
            Shot("B1","customer-detail","customer/${FixtureIds.CUSTOMER}"),Shot("B1","site-detail","site/${FixtureIds.SITE}"),
            Shot("B1","equipment-detail","equipment/${FixtureIds.EQUIPMENT_1}"),Shot("B1","equipment-editor","equipment/edit/${FixtureIds.EQUIPMENT_1}"),
            Shot("B1","visit-setup","visit/new"),
            Shot("B2","working-visit","visit/${FixtureIds.OWNER_REVIEW_VISIT}"),Shot("B2","checklist","inspection/${FixtureIds.OWNER_REVIEW_WORK}"),
            Shot("B2","evidence-photos","field/${FixtureIds.OWNER_REVIEW_WORK}"),Shot("B2","completion-review","review/${FixtureIds.OWNER_REVIEW_VISIT}"),
            Shot("B2","final-record","record/${record.id}"),Shot("B2","follow-up","follow-up/follow-up-001"),
            Shot("B3","history","history/global"),Shot("B3","report-viewer","report/${record.id}"),Shot("B3","correction","correction/${record.id}"),
            Shot("B3","superseded-context","record-version/${record.id}/${revision.id}"),Shot("B3","recovery","data-recovery"),
            Shot("B3","backup","backup/create"),Shot("B3","restore-inspection","backup/restore"),Shot("B3","csv-export","csv/export"),
            Shot("B3","csv-import","csv/import"),Shot("B3","restricted-destructive","data/erase"),
            Shot("B4","coordinator","dispatch/settings"),Shot("B4","technician-identity","dispatch/identity"),Shot("B4","teams","dispatch/teams"),
            Shot("B4","outbox","dispatch/create"),Shot("B4","dispatch-import-review","dispatch/import"),Shot("B4","reminders","reminders"),
            Shot("B4","calendar-settings","calendar"),Shot("B4","visit-calendar","visit/${FixtureIds.VISIT_2}"),Shot("B4","settings-info","settings"),
        )
        shots.forEach { shot ->
            shot.route?.let{compose.runOnIdle{route.value=it}}
            awaitSettledContent(shot,vm)
            capture(shot,dark)
        }
        compose.activity.setContent {
            ServiceLoopTheme(darkTheme=dark) {
                val nav=rememberNavController()
                Scaffold { padding ->
                    VisitDetailScreen(
                        VisitDetail("render-booked","V-BOOKED","BOOKED",FixtureIds.CUSTOMER,"Aster Facilities",FixtureIds.SITE,"Riverside plant","14 Riverside Way","2026-09-11",null,"Europe/Bucharest",listOf(VisitLine("render-work","Air compressor","EQ-001","Quarterly service","2026-09-11",null)),null),
                        padding,UiState(loading=false,businessZoneId="Europe/Bucharest"),vm,nav,
                    )
                }
            }
        }
        awaitSettledContent(Shot("B1","booked-visit",null),vm)
        capture(Shot("B1","booked-visit",null),dark)
    }

    private fun awaitSettledContent(shot:Shot,vm:ServiceLoopViewModel) {
        val loadingLabels=listOf("Reading saved service book","Reading customer","Reading site","Reading service plan","Reading due services","Reading template","Reading visit","Reading follow-up","Reading final service record")
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis=10_000) {
            !vm.state.value.loading &&
                (shot.name!="visit-setup" || vm.state.value.visitSites.isNotEmpty()) &&
                (shot.name!="history" || vm.state.value.history.isNotEmpty()) &&
                loadingLabels.all { compose.onAllNodesWithText(it).fetchSemanticsNodes().isEmpty() }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(900)
    }

    private fun capture(shot:Shot,dark:Boolean) {
            val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val dir=InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir ?: InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
            val appearance=if(dark)"dark" else "light"
            FileOutputStream(File(dir,"stage-b-${shot.group.lowercase()}-$appearance-${shot.name}.png")).use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
            bitmap.recycle()
    }

    @Test fun lightB1ToB4Matrix()=render(false)
    @Test fun darkB1ToB4Matrix()=render(true)
}
