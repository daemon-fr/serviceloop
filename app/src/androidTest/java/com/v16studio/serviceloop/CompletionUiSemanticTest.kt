package com.v16studio.serviceloop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompletionUiSemanticTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: ServiceLoopDatabase

    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        runBlocking {
            val dao = database.serviceLoopDao()
            dao.insertCustomers(listOf(CustomerEntity("c", "CU-1", "Customer")))
            dao.insertSites(listOf(SiteEntity("s", "c", "ST-1", "Site", "Address", null)))
            dao.insertEquipment(listOf(EquipmentEntity("e", "s", "EQ-1", "TECH-1", "Equipment", "Maker", "Model", "Serial", null)))
            dao.insertPlans(listOf(ServicePlanEntity("p", "e", "P-1", "Service", 3, "MONTHS", "2026-09-01", "ACTIVE", "o")))
            dao.insertObligations(listOf(ServiceObligationEntity("o", "p", 1, "2026-09-01", 1)))
            dao.insertVisits(listOf(WorkingVisitEntity("v", "V-UI", "c", "s", "2026-09-05", "Customer", "Site", "Address", "WORKING", 1, "CU-1", "ST-1", "Business", "Technician", null, null, null, "Europe/Bucharest")))
            dao.insertWorkItems(listOf(WorkItemEntity("w", "v", "e", "p", "o", null, "Equipment", "EQ-1", "Service", "P-1", "2026-09-01", 3, "MONTHS", false, null, false, equipmentIdentifierSnapshot = "TECH-1", equipmentMakeSnapshot = "Maker", equipmentModelSnapshot = "Model", equipmentSerialSnapshot = "Serial")))
            dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity("w", "Completed service")))
            dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity("w", "")))
        }
    }

    @After fun tearDown() = database.close()

    @Test fun actualCompletionControlsFinalizeAndNavigateToFinalRecord() {
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val repository = RoomServiceLoopRepository(database, time)
        val viewModel = ServiceLoopViewModel(repository) {}
        compose.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel) } }

        compose.onNodeWithText("Resume visit").performClick()
        compose.onNodeWithText("Review completion").performScrollTo().performClick()
        compose.onNodeWithTag("outcome-w-PERFORMED").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.completionLines.singleOrNull()?.outcome == "PERFORMED" }
        compose.onNodeWithTag("fulfills-w").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.completionLines.singleOrNull()?.fulfillsCurrentObligation == true }
        compose.onNodeWithContentDescription("Use calculated date").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.completionLines.singleOrNull()?.confirmedNextDueDate == "2026-12-05" }
        compose.onNodeWithTag("completion-review-list").performScrollToNode(hasTestTag("finalize-record"))
        compose.onNodeWithTag("finalize-record").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.finalRecord != null }
        compose.onNodeWithText("Final service record").assertIsDisplayed()
    }

    @Test fun readyMetadataWithMissingPdfKeepsStructuredTextAndDisablesShare() {
        runBlocking {
            val dao = database.serviceLoopDao()
            dao.insertFinalRecord(FinalRecordEntity("r", "v", "rev", 2))
            dao.insertFinalRevision(FinalRecordRevisionEntity("rev", "r", 1, "V-UI", "2026-09-05", 2, "Customer", "Site", "Address", "Business", "Technician", null, null, null, "Europe/Bucharest", null, "CU-1", "ST-1"))
            dao.insertFinalWorkItems(listOf(FinalWorkItemEntity("fw", "rev", 1, "w", "e", "Equipment", "EQ-1", "TECH-1", "Maker", "Model", "Serial", "Service", "p", "P-1", "NOT_PERFORMED", null, "Access unavailable", false, "2026-09-01", null, 3, "MONTHS", "o", null)))
            dao.insertReportRendition(ReportRenditionEntity("rr", "rev", 1, 3, "reports/r/missing.pdf", "hash", 10, 1, "READY", "ORIGINAL", null))
            dao.finalizeVisit("v", 2)
        }
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val viewModel = ServiceLoopViewModel(RoomServiceLoopRepository(database, time)) {}
        compose.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel) } }
        compose.onNodeWithText("Work").performClick()
        compose.onNodeWithText("Visits").performClick()
        compose.onNodeWithText("V-UI · Finalized · 2026-09-05\nSite").performClick()
        compose.onNodeWithText("View report text").performScrollTo().performClick()
        compose.onNodeWithText("File missing").assertIsDisplayed()
        compose.onNodeWithTag("report-text-view").performClick()
        compose.onNodeWithText("Service record V-UI · Revision 1").assertIsDisplayed()
        compose.onNodeWithTag("share-pdf").assertIsNotEnabled()
    }
}
