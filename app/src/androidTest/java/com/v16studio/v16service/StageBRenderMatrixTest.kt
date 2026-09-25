package com.v16studio.v16service

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.v16studio.v16service.data.FinalRecordEntity
import com.v16studio.v16service.data.WorkItemPrivateDraftEntity
import com.v16studio.v16service.data.WorkItemPublicDraftEntity
import com.v16studio.v16service.domain.FinalizeResult
import androidx.navigation.compose.rememberNavController
import com.v16studio.v16service.domain.VisitDetail
import com.v16studio.v16service.domain.VisitLine
import com.v16studio.v16service.ui.V16ServiceApp
import com.v16studio.v16service.ui.V16ServiceViewModel
import com.v16studio.v16service.ui.UiState
import com.v16studio.v16service.ui.VisitDetailScreen
import com.v16studio.v16service.ui.theme.V16ServiceTheme
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class StageBRenderMatrixTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Before
    fun requireRenderEvidenceOptIn() = assumeRenderEvidenceSuite()

    private data class Shot(val group:String,val name:String,val route:String?)

    private fun ensureStageBFinalRecord(app: V16ServiceApplication): FinalRecordEntity {
        val dao = app.container.database.v16ServiceDao()
        val existing = runBlocking { dao.finalRecordForVisit(STAGE_B_VISIT_ID) }
        if (existing != null) return existing

        runBlocking {
            check(dao.visit(STAGE_B_VISIT_ID) == null) { "Stage B fixture visit exists without its final record" }
            val sourceVisit = dao.visit(FixtureIds.VISIT_1) ?: error("V-001 source fixture is required")
            val sourceWork = dao.visitWorkItems(FixtureIds.VISIT_1).firstOrNull() ?: error("V-001 work fixture is required")
            dao.insertVisits(listOf(
                sourceVisit.copy(
                    id = STAGE_B_VISIT_ID,
                    reference = STAGE_B_VISIT_REFERENCE,
                    state = "WORKING",
                ),
            ))
            dao.insertWorkItems(listOf(
                sourceWork.copy(
                    id = STAGE_B_WORK_ID,
                    visitId = STAGE_B_VISIT_ID,
                    servicePlanId = null,
                    capturedObligationId = null,
                    templateSnapshotId = null,
                    serviceNameSnapshot = "Stage B rendering service",
                    planReferenceSnapshot = null,
                    dueDateSnapshot = null,
                    intervalCountSnapshot = null,
                    intervalUnitSnapshot = null,
                    checklistReviewed = false,
                    outcome = "PERFORMED",
                    fulfillsCurrentObligation = false,
                    notPerformedReason = null,
                    confirmedNextDueDate = null,
                    nextDueDateCalculated = null,
                    nextDueOverrideReason = null,
                ),
            ))
            dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity(STAGE_B_WORK_ID, "Stage B fixture work was completed for rendering.")))
            dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity(STAGE_B_WORK_ID, "Stage B test-owned final record fixture.")))
        }

        val result = runBlocking { app.container.repository.finalizeVisit(STAGE_B_VISIT_ID) }
        check(result is FinalizeResult.Success) { "Stage B final fixture could not be finalized: $result" }
        return runBlocking { dao.finalRecordForVisit(STAGE_B_VISIT_ID) }
            ?: error("Stage B final record was not persisted")
    }

    private fun render(dark:Boolean) {
        val app=compose.activity.application as V16ServiceApplication
        val dao=app.container.database.v16ServiceDao()
        val record = ensureStageBFinalRecord(app)
        val revision=runBlocking{dao.finalRevisions(record.id).first()}
        val route=mutableStateOf<String?>(null)
        app.container.appearancePreferences.setMode(if (dark) com.v16studio.v16service.ui.theme.AppearanceMode.DARK else com.v16studio.v16service.ui.theme.AppearanceMode.LIGHT)
        val vm=V16ServiceViewModel(app.container.repository,app.container.reportService,app.container.restrictedRecoveryState,app.container.businessDateSignal,app.container.reminderCoordinator,app.container.calendarCoordinator){app.container.startup.await()}
        compose.activity.setContent{V16ServiceTheme(darkTheme=dark){V16ServiceApp(vm,route.value)}}
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
            Shot("B4","coordinator","dispatch/settings"),Shot("B4","member-identity","dispatch/settings"),Shot("B4","teams","dispatch/teams"),
            Shot("B4","outbox","dispatch/create"),Shot("B4","dispatch-import-review","dispatch/import"),Shot("B4","reminders","reminders"),
            Shot("B4","calendar-settings","calendar"),Shot("B4","visit-calendar","visit/${FixtureIds.VISIT_2}"),Shot("B4","settings-info","settings"),Shot("B4","appearance","appearance"),
        )
        shots.forEach { shot ->
            shot.route?.let{compose.runOnIdle{route.value=it}}
            awaitSettledContent(shot,vm)
            capture(shot,dark)
        }
        compose.activity.setContent {
            V16ServiceTheme(darkTheme=dark) {
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
        val canceled = VisitDetail("render-canceled","V-CANCELED","CANCELED",FixtureIds.CUSTOMER,"Aster Facilities",FixtureIds.SITE,"Riverside plant","14 Riverside Way","2026-09-11",null,"Europe/Bucharest",listOf(VisitLine("render-canceled-work","Air compressor","EQ-001","Quarterly service","2026-09-11",null)),"Coordinator removed the assignment","COORDINATOR")
        compose.activity.setContent {
            V16ServiceTheme(darkTheme=dark) {
                val nav=rememberNavController()
                Scaffold { padding -> VisitDetailScreen(canceled,padding,UiState(loading=false,businessZoneId="Europe/Bucharest"),vm,nav) }
            }
        }
        awaitSettledContent(Shot("B1","canceled-visit",null),vm)
        capture(Shot("B1","canceled-visit",null),dark)
        val completed = VisitDetail("render-completed","V-COMPLETED","COMPLETED",FixtureIds.CUSTOMER,"Aster Facilities",FixtureIds.SITE,"Riverside plant","14 Riverside Way","2026-09-11",null,"Europe/Bucharest",listOf(VisitLine("render-completed-work","Air compressor","EQ-001","Quarterly service","2026-09-11",null)),null)
        compose.activity.setContent {
            V16ServiceTheme(darkTheme=dark) {
                val nav=rememberNavController()
                Scaffold { padding -> VisitDetailScreen(completed,padding,UiState(loading=false,businessZoneId="Europe/Bucharest"),vm,nav) }
            }
        }
        awaitSettledContent(Shot("B1","completed-no-record",null),vm)
        capture(Shot("B1","completed-no-record",null),dark)
    }

    private fun awaitSettledContent(shot:Shot,vm:V16ServiceViewModel) {
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

    private companion object {
        const val STAGE_B_VISIT_ID = "stage-b-render-final-visit"
        const val STAGE_B_VISIT_REFERENCE = "V-STAGE-B"
        const val STAGE_B_WORK_ID = "stage-b-render-final-work"
    }
}
