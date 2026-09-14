package com.v16studio.serviceloop

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.room.Room
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.ui.ServiceLoopApp
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import java.time.Instant
import java.time.ZoneId
import java.io.ByteArrayOutputStream
import java.io.File
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

    @Test fun noChecklistBlockerLinksRevealCompletionControls() {
        assertCompletionBlockerNavigation("OUTCOME", false)
    }

    @Test fun checklistBlockerLinksRevealCompletionControls() {
        assertCompletionBlockerNavigation("OUTCOME", true)
    }

    @Test fun noChecklistNotPerformedReasonLinkRevealsReason() {
        assertCompletionBlockerNavigation("NOT_PERFORMED_REASON", false)
    }

    @Test fun checklistNotPerformedReasonLinkRevealsReason() {
        assertCompletionBlockerNavigation("NOT_PERFORMED_REASON", true)
    }

    @Test fun noChecklistNextDueLinkRevealsFulfillment() {
        assertCompletionBlockerNavigation("NEXT_DUE", false)
    }

    @Test fun checklistNextDueLinkRevealsFulfillmentAtLargeText() {
        assertCompletionBlockerNavigation("NEXT_DUE", true, largeText = true)
    }

    private fun assertCompletionBlockerNavigation(kind: String, withChecklist: Boolean, largeText: Boolean = false) {
        runBlocking {
            val dao = database.serviceLoopDao()
            if (withChecklist) {
                dao.insertTemplateSnapshots(listOf(TemplateSnapshotEntity("snapshot", null, "Fixture checklist", 1, 1)))
                dao.insertChecklistItems(listOf(ChecklistItemSnapshotEntity("question", "snapshot", 1, "Optional check", "STATUS", null, false, null)))
            }
            dao.updateWorkItem(dao.workItem("w")!!.copy(
                templateSnapshotId = if (withChecklist) "snapshot" else null,
                outcome = when (kind) { "NOT_PERFORMED_REASON" -> "NOT_PERFORMED"; "NEXT_DUE" -> "PARTLY_PERFORMED"; else -> null },
                fulfillsCurrentObligation = if (kind == "NOT_PERFORMED_REASON") false else null,
            ))
        }
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val viewModel = ServiceLoopViewModel(RoomServiceLoopRepository(database, time)) {}
        compose.setContent { ServiceLoopTheme {
            if (largeText) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) { ServiceLoopApp(viewModel, "review/v") }
            } else ServiceLoopApp(viewModel, "review/v")
        } }
        compose.waitUntil(10_000) { viewModel.state.value.completionLines.any { it.workItemId == "w" } }
        val blockerTag = "completion-blocker-w-$kind-"
        compose.onNodeWithTag("completion-review-list").performScrollToNode(hasTestTag(blockerTag))
        compose.onNodeWithTag(blockerTag).assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("service-list").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { viewModel.state.value.completionLines.any { it.workItemId == "w" } }
        compose.onNodeWithTag("service-outcome").assertIsDisplayed()
        when (kind) {
            "NOT_PERFORMED_REASON" -> compose.onNodeWithTag("not-performed-reason").assertIsDisplayed()
            "NEXT_DUE" -> compose.onNodeWithTag("fulfill-w").assertIsDisplayed()
            else -> compose.onNodeWithTag("outcome-w-PERFORMED").assertIsDisplayed()
        }
    }

    @Test fun workingVisitIdentityAndActionsAreSeparated() {
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val viewModel = ServiceLoopViewModel(RoomServiceLoopRepository(database, time)) {}
        compose.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel, "visit/v") } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("visit-identity").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("visit-identity").assertIsDisplayed()
        compose.onNodeWithText("V-UI · Working").assertIsDisplayed()
        org.junit.Assert.assertTrue(compose.onAllNodesWithText("Customer").fetchSemanticsNodes().isNotEmpty())
        org.junit.Assert.assertTrue(compose.onAllNodesWithText("Site").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Address").assertIsDisplayed()
        compose.onNodeWithTag("visit-date-landmark-label").assertTextContains("SERVICE DATE")
        compose.onNodeWithTag("visit-date-landmark-value").assertTextContains("5 Sep 2026")
        compose.onAllNodesWithText("Service date 2026-09-05").assertCountEquals(0)
        compose.onNodeWithTag("visit-relationship-actions").assertIsDisplayed()
        compose.onNodeWithTag("visit-customer-link").assertIsDisplayed()
        compose.onNodeWithTag("visit-site-link").assertIsDisplayed()
        compose.onNodeWithTag("visit-detail-list").performScrollToNode(hasTestTag("resume-service"))
        compose.onNodeWithTag("resume-service").assertIsDisplayed()
        compose.onNodeWithTag("visit-review").assertIsDisplayed()
        compose.onNodeWithTag("visit-detail-list").performScrollToNode(hasText("Inspection checklist"))
        compose.onNodeWithText("Inspection checklist").assertIsDisplayed()
        compose.onAllNodesWithText("Checklist template").assertCountEquals(0)
    }

    @Test fun reviewWaitsForOutcomeBeforeShowingFulfillmentAndKeepsIdentityCompact() {
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val viewModel = ServiceLoopViewModel(RoomServiceLoopRepository(database, time)) {}
        compose.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel, "review/v") } }
        compose.waitUntil(5_000) { viewModel.state.value.completionLines.isNotEmpty() }
        compose.onNodeWithText("Review the service details before finalizing.").assertIsDisplayed()
        compose.onAllNodesWithText("Checklist completion records inspection facts.").assertCountEquals(0)
        compose.onAllNodesWithText("Service outcome and recurring fulfillment remain separate decisions.").assertCountEquals(0)
        compose.onNodeWithText("Report identity").assertIsDisplayed()
        compose.onNodeWithText("Business · Technician").assertIsDisplayed()
        compose.onNodeWithText("Update from current profile").assertIsDisplayed()
        compose.onNodeWithTag("completion-review-list").performScrollToNode(hasText("Choose an outcome"))
        compose.onNodeWithText("Choose an outcome").assertIsDisplayed()
        compose.onAllNodesWithText("null work", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Fulfillment unavailable", substring = true).assertCountEquals(0)
    }

    @Test fun currentRecurringNotPerformedShowsItsOutstandingDueDateInService() {
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val repository = RoomServiceLoopRepository(database, time)
        runBlocking { repository.saveCompletionDraft("w", "NOT_PERFORMED", false, "Access unavailable", null, null, null) }
        val viewModel = ServiceLoopViewModel(repository) {}
        compose.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel, "inspection/w") } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("service-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("service-outcome"))
        compose.onNodeWithText("Remains due · 1 Sep 2026").assertIsDisplayed()
    }

    @Test fun currentRecurringNotPerformedShowsItsOutstandingDueDateInReview() {
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val repository = RoomServiceLoopRepository(database, time)
        runBlocking { repository.saveCompletionDraft("w", "NOT_PERFORMED", false, "Access unavailable", null, null, null) }
        val viewModel = ServiceLoopViewModel(repository) {}
        compose.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel, "review/v") } }
        compose.waitUntil(10_000) { viewModel.state.value.completionLines.any { it.workItemId == "w" } }
        compose.onNodeWithText("Remains due · 1 Sep 2026").assertIsDisplayed()
    }

    @Test fun oneOffNotPerformedDoesNotShowARecurringDueConsequence() {
        runBlocking {
            val dao = database.serviceLoopDao()
            dao.insertWorkItems(listOf(WorkItemEntity("oneoff-not-performed", "v", "e", null, null, null, "Equipment", "EQ-1", "One-off repair", null, null, null, null, false, null, null, subjectType = "EQUIPMENT")))
            dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity("oneoff-not-performed", "")))
            dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity("oneoff-not-performed", "")))
        }
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val repository = RoomServiceLoopRepository(database, time)
        runBlocking { repository.saveCompletionDraft("oneoff-not-performed", "NOT_PERFORMED", false, "Access unavailable", null, null, null) }
        val viewModel = ServiceLoopViewModel(repository) {}
        compose.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel, "inspection/oneoff-not-performed") } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("service-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("service-outcome"))
        compose.onNodeWithText("No recurring due date for this Service.").assertIsDisplayed()
        compose.onAllNodesWithText("Remains due", substring = true).assertCountEquals(0)
    }

    @Test fun historyOnlyNotPerformedDoesNotShowACurrentDueConsequence() {
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val repository = RoomServiceLoopRepository(database, time)
        val historyVisit = runBlocking { repository.createVisit(listOf("p"), "HISTORICAL", "2026-08-01") }
        val historyWork = runBlocking { database.serviceLoopDao().visitWorkItems(historyVisit).single().id }
        runBlocking { repository.saveCompletionDraft(historyWork, "NOT_PERFORMED", false, "Access unavailable", null, null, null) }
        val viewModel = ServiceLoopViewModel(repository) {}
        compose.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel, "inspection/$historyWork") } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("service-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("service-outcome"))
        compose.onNodeWithText("History only — current due date is unchanged.").assertIsDisplayed()
        compose.onAllNodesWithText("Remains due", substring = true).assertCountEquals(0)
    }

    @Test fun checklistHeaderUsesRequiredCountWithoutPageLevelFindingWarning() {
        runBlocking {
            val dao = database.serviceLoopDao()
            dao.insertTemplateSnapshots(listOf(TemplateSnapshotEntity("checklist-template", null, "Inspection", 1, 1)))
            dao.insertChecklistItems(listOf(ChecklistItemSnapshotEntity("checklist-question", "checklist-template", 1, "Guard", "STATUS", null, true, null)))
            dao.updateWorkItem(dao.workItem("w")!!.copy(templateSnapshotId = "checklist-template"))
            dao.upsertResponses(listOf(WorkingResponseEntity("checklist-response", "w", "checklist-question", "ISSUE_FOUND", null, null, null, 1)))
        }
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val viewModel = ServiceLoopViewModel(RoomServiceLoopRepository(database, time)) {}
        compose.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel, "inspection/w") } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("service-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("checklist-section"))
        compose.onNodeWithText("Required complete 0 of 1").assertIsDisplayed()
        compose.onAllNodesWithText("Issue findings require a public description before checklist completion.").assertCountEquals(0)
        compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("question-checklist-question"))
        compose.onNodeWithText("Required for checklist completion.").assertIsDisplayed()
    }

    @Test fun actualCompletionControlsFinalizeAndNavigateToFinalRecord() {
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val repository = RoomServiceLoopRepository(database, time)
        val viewModel = ServiceLoopViewModel(repository) {}
        compose.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel, "visit/v") } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("visit-detail-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("visit-detail-list").performScrollToNode(hasTestTag("resume-service"))
        compose.onNodeWithTag("resume-service").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("service-list").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) { viewModel.state.value.completionLines.any { it.workItemId == "w" } }
        compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("outcome-w-PERFORMED"))
        compose.onNodeWithTag("outcome-w-PERFORMED").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.completionLines.singleOrNull()?.outcome == "PERFORMED" }
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.completionLines.singleOrNull()?.fulfillsCurrentObligation == true }
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.completionLines.singleOrNull()?.confirmedNextDueDate == "2026-12-05" }
        compose.onAllNodesWithTag("fulfill-w").assertCountEquals(0)
        compose.onAllNodesWithTag("keep-due-w").assertCountEquals(0)
        compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("change-next-due"))
        compose.onNodeWithTag("change-next-due").assertIsDisplayed()
        compose.onAllNodesWithTag("override-date").assertCountEquals(0)
        compose.onNodeWithTag("change-next-due").performClick()
        compose.onNodeWithTag("override-date").performTextReplacement("2026-12-20")
        compose.onNodeWithTag("override-reason").performTextReplacement("Customer requested later date")
        compose.onNodeWithTag("override-date").assertTextContains("2026-12-20")
        compose.onNodeWithTag("override-reason").assertTextContains("Customer requested later date")
        org.junit.Assert.assertEquals("v", viewModel.state.value.completionVisitId)
        compose.onNodeWithTag("apply-override").assertIsEnabled()
        org.junit.Assert.assertEquals("2026-12-05", viewModel.state.value.completionLines.single().confirmedNextDueDate)
        org.junit.Assert.assertEquals("2026-12-05", runBlocking { repository.completionLines("v").single().confirmedNextDueDate })
        compose.onNodeWithTag("apply-override").performScrollTo().assertIsDisplayed().performClick()
        compose.onAllNodesWithTag("override-date").assertCountEquals(0)
        compose.waitUntil(5_000) { viewModel.state.value.completionLines.singleOrNull()?.confirmedNextDueDate == "2026-12-20" }
        compose.onNodeWithTag("review-visit").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("completion-review-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithTag("outcome-w-PERFORMED").assertCountEquals(0)
        compose.onAllNodesWithTag("fulfill-w").assertCountEquals(0)
        compose.onNodeWithTag("completion-review-list").performScrollToNode(hasTestTag("finalize-record"))
        compose.onNodeWithTag("finalize-record").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.finalRecord != null }
        compose.onNodeWithText("Final service record").assertIsDisplayed()
    }

    @Test fun largeTextKeepsServiceOutcomeAndFulfillmentChoicesSeparatelyReachable() {
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val viewModel = ServiceLoopViewModel(RoomServiceLoopRepository(database, time)) {}
        compose.setContent {
            ServiceLoopTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    ServiceLoopApp(viewModel, "visit/v")
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("visit-detail-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("visit-detail-list").performScrollToNode(hasTestTag("resume-service"))
        compose.onNodeWithTag("resume-service").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("service-list").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5_000) { viewModel.state.value.completionLines.any { it.workItemId == "w" } }
        listOf("PERFORMED", "PARTLY_PERFORMED", "NOT_PERFORMED").forEach { outcome ->
            compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("outcome-w-$outcome"))
            compose.onNodeWithTag("outcome-w-$outcome").assertIsDisplayed()
        }
        compose.onNodeWithTag("outcome-w-PERFORMED").performClick()
        compose.waitUntil(5_000) { viewModel.state.value.completionLines.singleOrNull()?.outcome == "PERFORMED" }
        compose.onAllNodesWithTag("fulfill-w").assertCountEquals(0)
        compose.onAllNodesWithTag("keep-due-w").assertCountEquals(0)
        compose.onNodeWithTag("outcome-w-PARTLY_PERFORMED").performClick()
        compose.waitUntil(5_000) { viewModel.state.value.completionLines.singleOrNull()?.outcome == "PARTLY_PERFORMED" }
        compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("fulfill-w"))
        compose.onNodeWithTag("fulfill-w").assertIsDisplayed()
        compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("keep-due-w"))
        compose.onNodeWithTag("keep-due-w").assertIsDisplayed()
    }

    @Test fun oneOffServiceCanBeReadyWithoutARecurringFulfillmentChoice() {
        runBlocking {
            val dao = database.serviceLoopDao()
            dao.insertWorkItems(listOf(WorkItemEntity("oneoff", "v", null, null, null, null, null, null, "Site repair", null, null, null, null, false, null, null, subjectType = "SITE")))
            dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity("oneoff", "Repaired the door closer")))
            dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity("oneoff", "")))
        }
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val viewModel = ServiceLoopViewModel(RoomServiceLoopRepository(database, time)) {}
        compose.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel, "visit/v") } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("visit-detail-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("visit-detail-list").performScrollToNode(hasTestTag("visit-line-oneoff"))
        compose.onNodeWithTag("visit-line-oneoff").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("service-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("service-parts"))
        compose.onNodeWithTag("add-part").assertExists()
        compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("service-photos"))
        compose.onNodeWithTag("choose-photo").assertExists()
        compose.onNodeWithTag("take-photo").assertExists()
        compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("outcome-oneoff-PERFORMED"))
        compose.onNodeWithTag("outcome-oneoff-PERFORMED").performClick()
        compose.waitUntil(5_000) { viewModel.state.value.serviceProgress?.items?.firstOrNull { it.workItemId == "oneoff" }?.status == com.v16studio.serviceloop.domain.ServiceEntryStatus.READY }
        compose.onAllNodesWithTag("fulfill-oneoff").assertCountEquals(0)
        compose.onAllNodesWithTag("keep-due-oneoff").assertCountEquals(0)
        compose.onNodeWithText("No recurring due date for this Service.").assertExists()
        InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)?.let { directory ->
            File(directory, "service-flow-2b-oneoff.png").outputStream().use { output ->
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
    }

    @Test fun incompleteChecklistBlocksPartlyAndNotPerformedServicesUntilCompleted() {
        runBlocking {
            val dao = database.serviceLoopDao()
            dao.insertTemplateSnapshots(listOf(TemplateSnapshotEntity("progress-template", null, "Optional inspection", 1, 1)))
            dao.insertChecklistItems(listOf(ChecklistItemSnapshotEntity("progress-question", "progress-template", 1, "Optional check", "STATUS", null, true, null)))
            dao.updateWorkItem(dao.workItem("w")!!.copy(templateSnapshotId = "progress-template"))
        }
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val repository = RoomServiceLoopRepository(database, time)
        runBlocking {
            repository.savePublicWork("w", "Partly serviced")
            repository.saveCompletionDraft("w", "PARTLY_PERFORMED", false, null, null, null, null)
            org.junit.Assert.assertEquals(
                com.v16studio.serviceloop.domain.ServiceEntryStatus.IN_PROGRESS,
                repository.serviceVisitProgress("v").items.single().status,
            )
        }
        val viewModel = ServiceLoopViewModel(repository) {}
        compose.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel, "visit/v") } }
        compose.waitUntil(10_000) { viewModel.state.value.serviceProgress?.items?.singleOrNull()?.status == com.v16studio.serviceloop.domain.ServiceEntryStatus.IN_PROGRESS }
        compose.onNodeWithText("In progress").assertIsDisplayed()

        runBlocking {
            repository.saveCompletionDraft("w", "NOT_PERFORMED", false, "Access unavailable", null, null, null)
            org.junit.Assert.assertEquals(
                com.v16studio.serviceloop.domain.ServiceEntryStatus.IN_PROGRESS,
                repository.serviceVisitProgress("v").items.single().status,
            )
        }
        viewModel.loadVisit("v")
        compose.waitUntil(5_000) { viewModel.state.value.serviceProgress?.items?.singleOrNull()?.status == com.v16studio.serviceloop.domain.ServiceEntryStatus.IN_PROGRESS }
        compose.onNodeWithText("In progress").assertIsDisplayed()
    }

    @Test fun inlinePhotoDetailsPersistAndRemovalRequiresConfirmation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val repository = RoomServiceLoopRepository(database, time, attachmentRoot = context.filesDir)
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val photoId = runBlocking { repository.savePhoto("w", bytes, "test.png", "image/png", false, null) }
        val path = runBlocking { database.serviceLoopDao().attachment(photoId)!!.storedRelativePath }
        try {
            val viewModel = ServiceLoopViewModel(repository) {}
            compose.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel, "visit/v") } }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("visit-detail-list").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("visit-detail-list").performScrollToNode(hasTestTag("resume-service"))
            compose.onNodeWithTag("resume-service").performClick()
            compose.waitUntil(10_000) { viewModel.state.value.fieldEvidenceWorkItemId == "w" && compose.onAllNodesWithTag("service-list").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("service-list").performScrollToNode(hasTestTag("photo-$photoId"))
            compose.onNodeWithTag("photo-report-$photoId").performScrollTo().assertIsOff().performClick()
            compose.waitUntil(5_000) { runBlocking { repository.photos("w").single().includedInReport } }
            compose.onNodeWithTag("photo-caption-$photoId").performScrollTo().performTextReplacement("Inspection view")
            compose.waitUntil(5_000) { runBlocking { repository.photos("w").single().caption == "Inspection view" } }
            compose.onNodeWithTag("remove-photo-$photoId").performScrollTo().performClick()
            compose.onNodeWithText("Remove photo?").assertIsDisplayed()
            org.junit.Assert.assertTrue(File(context.filesDir, path).isFile)
            compose.onNodeWithTag("confirm-remove-photo-$photoId").performClick()
            compose.waitUntil(5_000) { runBlocking { repository.photos("w").isEmpty() } }
            org.junit.Assert.assertFalse(File(context.filesDir, path).exists())
        } finally { File(context.filesDir, path).delete() }
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
        compose.onNodeWithTag("visit-date-selector").performClick()
        compose.onNodeWithTag("visit-date-selector-option-all").performClick()
        compose.onNodeWithTag("work-visits-list").performScrollToNode(androidx.compose.ui.test.hasText("V-UI"))
        compose.onNodeWithText("V-UI").performClick()
        compose.waitUntil(5_000){compose.onAllNodesWithText("Recorded on", substring = true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Recorded on", substring = true).assertIsDisplayed()
        compose.onNodeWithText("View report text").performScrollTo().performClick()
        compose.waitUntil(5_000){compose.onAllNodesWithTag("report-view-tabs").fetchSemanticsNodes().isNotEmpty()}
        compose.waitUntil(5_000){compose.onAllNodesWithText("File missing",substring=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("File missing", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("content-tab-Text view").assertIsSelected()
        compose.onNodeWithText("Service record V-UI · Revision 1").assertIsDisplayed()
        compose.onNodeWithTag("share-pdf").assertIsNotEnabled()
    }

    @Test fun homeRendersTrueMultipleWorkingAndBookedCounts() {
        runBlocking {
            database.serviceLoopDao().insertVisits(listOf(
                WorkingVisitEntity("v2", "V-UI-2", "c", "s", "2026-09-06", "Customer", "Site", null, "WORKING", 3),
                WorkingVisitEntity("b1", "B-1", "c", "s", "2026-09-07", "Customer", "Site", null, "BOOKED", 2),
                WorkingVisitEntity("b2", "B-2", "c", "s", "2026-09-08", "Customer", "Site", null, "BOOKED", 2),
                WorkingVisitEntity("b3", "B-3", "c", "s", "2026-09-09", "Customer", "Site", null, "BOOKED", 2),
            ))
        }
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val viewModel = ServiceLoopViewModel(RoomServiceLoopRepository(database, time)) {}
        compose.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel) } }
        compose.waitUntil(5_000){compose.onAllNodesWithText("Unfinished visits · 2").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Unfinished visits · 2").assertIsDisplayed()
        compose.onNodeWithText("Booked visits · 3").assertIsDisplayed()
    }

    @Test fun historyDateFieldsShowErrorsAndClearWithoutApplyingInvalidRanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val viewModel = ServiceLoopViewModel(RoomServiceLoopRepository(database, time, attachmentRoot = context.filesDir)) {}
        compose.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel) } }
        compose.onNodeWithText("Settings").performClick(); compose.onNodeWithText("History").performClick()
        compose.onNodeWithTag("history-from").performTextReplacement("not-a-date"); compose.onNodeWithText("Use YYYY-MM-DD").assertIsDisplayed()
        compose.onNodeWithTag("history-from").performTextReplacement("2026-09-10"); compose.onNodeWithTag("history-to").performTextReplacement("2026-09-01")
        compose.onNodeWithText("From must not be after To").assertIsDisplayed(); compose.onNodeWithText("To must not be before From").assertIsDisplayed()
        compose.onNodeWithTag("history-clear-dates").performClick(); compose.onNodeWithText("From must not be after To").assertDoesNotExist(); compose.onNodeWithText("To must not be before From").assertDoesNotExist()
    }

    @Test fun staleObligationIsUnavailableInCompletionReview() {
        runBlocking {
            val dao = database.serviceLoopDao()
            dao.updateCompletionDraft("w", "PERFORMED", true, null, "2026-12-05", true, null)
            dao.insertObligations(listOf(ServiceObligationEntity("o2", "p", 2, "2026-12-05", 2)))
            dao.setCurrentObligationForTest("p", "o2")
        }
        val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
        val viewModel = ServiceLoopViewModel(RoomServiceLoopRepository(database, time)) {}
        compose.setContent { ServiceLoopTheme { ServiceLoopApp(viewModel, "review/v") } }
        compose.waitUntil(5_000){viewModel.state.value.completionLines.isNotEmpty()}
        val unavailable = androidx.compose.ui.test.hasText("Current service obligation changed — this Service cannot advance the current due date.")
        compose.onNodeWithTag("completion-review-list").performScrollToNode(unavailable)
        compose.onNode(unavailable).assertIsDisplayed()
        compose.onAllNodesWithTag("fulfills-w").assertCountEquals(0)
    }
}
