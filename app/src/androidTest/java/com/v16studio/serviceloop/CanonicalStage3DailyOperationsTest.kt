package com.v16studio.serviceloop

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Deliberate, persistent owner-review journey. Run only with -e sl3RuntimeJourney true. */
@RunWith(AndroidJUnit4::class)
class CanonicalStage3DailyOperationsTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun canonicalPersistentDailyOperationsJourney() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("sl3RuntimeJourney")=="true")
        val application=compose.activity.application as ServiceLoopApplication
        val dao=application.container.database.serviceLoopDao()
        runBlocking { application.container.startup.await() }
        assertNotNull(runBlocking{application.container.repository.businessProfile()})
        val suffix=System.currentTimeMillis().toString().takeLast(7)
        val templateName="SL3 Runtime Template $suffix"
        val customerName="SL3 Runtime Customer $suffix"
        val siteName="SL3 Runtime Site $suffix"
        val equipmentName="SL3 Runtime Compressor $suffix"
        val planName="SL3 Runtime Service $suffix"
        val followTitle="SL3 Runtime Follow-up $suffix"

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Inspection templates").performClick()
        compose.onNodeWithText("Create inspection template").performClick()
        compose.onNodeWithText("Template name · Required").performTextInput(templateName)
        compose.onNodeWithText("Item label").performTextInput("Runtime safety check")
        hideKeyboard()
        compose.onNode(hasText("Add item") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Publish new revision",substring=true).assertDoesNotExist()
        compose.onNodeWithText("Save template").performScrollTo().performClick()
        compose.waitUntil(15_000){runBlocking{dao.reusableTemplates().any{it.name==templateName}}}
        waitForText("Revision 1")
        back(); back(); back()

        compose.onNodeWithText("Register").performClick()
        waitForText("Add customer")
        compose.onNode(hasText("Add customer") and hasClickAction()).performClick()
        compose.onNodeWithText("Customer name · Required").performTextInput(customerName)
        compose.onNodeWithText("Main contact").performTextInput("Development Contact")
        compose.onNodeWithText("Phone").performTextInput("+40 700 000 000")
        compose.onNodeWithText("Email").performTextInput("sl3@example.invalid")
        hideKeyboard()
        compose.onNodeWithText("Save customer").performScrollTo().performClick()
        compose.waitUntil(15_000){runBlocking{dao.customerList().any{it.name==customerName}}}
        waitForText("Add site")
        val customerId=runBlocking{dao.customerList().single{it.name==customerName}.id}
        compose.onNode(hasText("Add site") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Site name · Required").performTextInput(siteName)
        compose.onNodeWithText("Address").performTextInput("1 Runtime Review Street")
        compose.onNodeWithText("PRIVATE access note").performTextInput("DEV ACCESS ONLY")
        hideKeyboard()
        compose.onNodeWithText("Save site").performScrollTo().performClick()
        compose.waitUntil(15_000){runBlocking{dao.sitesForCustomer(customerId).any{it.name==siteName}}}
        waitForText("Add equipment")
        compose.onNode(hasText("Add equipment") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Equipment name · Required").performTextInput(equipmentName)
        compose.onNodeWithText("Technician identifier").performTextInput("SL3-$suffix")
        hideKeyboard()
        compose.onNodeWithText("Save equipment").performScrollTo().performClick()
        compose.waitUntil(15_000){runBlocking{dao.equipmentList().any{it.name==equipmentName}}}
        waitForText("Add service plan")
        compose.onNode(hasText("Add service plan") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Plan name · Required").performTextInput(planName)
        compose.onNodeWithText("$templateName · r1").performScrollTo().performClick()
        hideKeyboard()
        compose.onNodeWithTag("plan-editor").performScrollToNode(hasText("Save plan"))
        compose.onNode(hasText("Save plan") and hasClickAction()).performClick()
        compose.waitUntil(15_000){runBlocking{dao.search("%$planName%").any{it.type=="PLAN"}}}
        waitForText("Current obligation remains separate")
        val planId=runBlocking{dao.search("%$planName%").single{it.type=="PLAN"}.id}
        val dueBefore=runBlocking{dao.plan(planId)!!.currentDueDate}

        compose.onNode(hasText("Create visit") and hasClickAction()).performClick()
        waitForText("Start now")
        // The setup route composes before its asynchronously loaded site/plan projection.
        // Wait for the selected plan's site rather than clicking a disabled Book action.
        waitForText("$customerName · $siteName")
        waitForText(planName)
        compose.onNodeWithText("Record past visit").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Book visit").performScrollTo().performClick()
        compose.waitUntil(15_000){runBlocking{dao.dueServices().single{it.planId==planId}.claimedVisitId!=null}}
        var visitId=runBlocking{dao.dueServices().single{it.planId==planId}.claimedVisitId!!}
        compose.waitUntil(15_000){compose.onAllNodesWithTag("field-new-appointment-date").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("field-new-appointment-date").performTextReplacement("2026-09-15")
        compose.onNodeWithTag("long-text-reschedule-reason").performTextInput("Development runtime review")
        hideKeyboard()
        compose.onNodeWithText("Reschedule booking").performScrollTo().performClick()
        compose.waitUntil(15_000){runBlocking{dao.visit(visitId)!!.actualServiceDate=="2026-09-15"}}
        assertEquals(dueBefore,runBlocking{dao.plan(planId)!!.currentDueDate})
        compose.onNodeWithTag("long-text-cancellation-reason").performTextInput("Rebook for runtime start")
        hideKeyboard()
        compose.onNodeWithText("Cancel booking").performScrollTo().performClick()
        compose.waitUntil(15_000){runBlocking{dao.visit(visitId)!!.state=="CANCELED"}}
        assertEquals(null,runBlocking{dao.dueServices().single{it.planId==planId}.claimedVisitId})
        back()

        compose.onNode(hasText("Create visit") and hasClickAction()).performClick()
        compose.onNodeWithText("Start now").performScrollTo().performClick()
        compose.waitUntil(15_000){runBlocking{dao.dueServices().single{it.planId==planId}.claimedVisitId!=null}}
        visitId=runBlocking{dao.dueServices().single{it.planId==planId}.claimedVisitId!!}
        val workId=runBlocking{dao.visitWorkItems(visitId).single().id}
        val questionId=runBlocking{dao.checklistItems(dao.workItem(workId)!!.templateSnapshotId!!).single().id}
        compose.waitUntil(15_000){compose.onAllNodesWithTag("visit-line-$workId").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("visit-line-$workId").performClick()
        compose.waitUntil(15_000){compose.onAllNodesWithTag("long-text-public-work-performed").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("long-text-public-work-performed").performTextInput("Completed runtime inspection")
        hideKeyboard()
        compose.onNodeWithText("Save work performed").performClick()
        compose.waitUntil(15_000){runBlocking{dao.inspection(workId)!!.workPerformed.isNotBlank()}}
        compose.onNodeWithTag("response-$questionId-ISSUE_FOUND").performClick()
        compose.waitUntil(15_000){runBlocking{dao.responses(workId).singleOrNull()?.disposition=="ISSUE_FOUND"}}
        compose.onNodeWithTag("long-text-public-finding-description").performTextInput("Guard needs adjustment")
        compose.onNodeWithTag("long-text-public-finding-description-expand").performScrollTo().performClick()
        compose.onNodeWithText("Done").assertIsDisplayed().performClick()
        compose.onNodeWithTag("finding-save-$questionId").assertIsEnabled().performScrollTo().performClick()
        compose.waitUntil(15_000){runBlocking{dao.responses(workId).single().reason?.contains("Guard needs adjustment")==true}}
        hideKeyboard()
        compose.waitUntil(15_000){runCatching{compose.onNodeWithContentDescription("Mark checklist reviewed").assertIsEnabled()}.isSuccess}
        compose.onNodeWithContentDescription("Mark checklist reviewed").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(15_000){runBlocking{dao.workItem(workId)!!.checklistReviewed}}
        compose.onNodeWithTag("inspection-list").performScrollToNode(hasTestTag("open-field-evidence"))
        compose.onNodeWithTag("open-field-evidence").assertIsDisplayed().performClick()
        compose.waitUntil(15_000){compose.onAllNodesWithTag("field-description").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("field-description").performTextInput("Runtime filter")
        hideKeyboard()
        compose.onNodeWithText("Save part").performClick()
        compose.waitUntil(15_000){runBlocking{dao.parts(workId).isNotEmpty()}}
        compose.onNodeWithTag("field-follow-up-title").performTextInput(followTitle)
        hideKeyboard()
        compose.onNodeWithText("Create corrective follow-up").performScrollTo().performClick()
        compose.waitUntil(15_000){runBlocking{dao.followUps().any{it.title==followTitle}}}
        compose.onNodeWithTag("field-review-completion").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Outcome Performed").performScrollTo().performClick()
        compose.waitUntil(15_000){runBlocking{dao.workItem(workId)!!.outcome=="PERFORMED"}}
        compose.onNodeWithTag("fulfills-$workId").performClick()
        compose.onNodeWithText("Use calculated date").performScrollTo().performClick()
        compose.waitUntil(15_000){runBlocking{dao.workItem(workId)!!.confirmedNextDueDate!=null}}
        compose.onNodeWithTag("completion-review-list").performScrollToNode(hasTestTag("finalize-record"))
        compose.onNodeWithTag("finalize-record").performClick()
        compose.waitUntil(20_000){runBlocking{dao.finalRecordForVisit(visitId)!=null}}
        compose.onNodeWithText("Finalized",substring=true).assertIsDisplayed()

        returnToRoot()
        compose.onNode(hasText("Search") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("field-search-names-and-references").performTextInput(customerName)
        waitForText(customerName)
        compose.onNodeWithTag("field-search-names-and-references").performTextReplacement(followTitle)
        waitForText(followTitle)
        hideKeyboard()
        compose.onNode(hasText("Back") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(hasText("Home") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onAllNodesWithText("View all")[0].performClick()
        compose.onNodeWithTag("due-filter-OVERDUE").assertIsSelected()
    }

    private fun back(){compose.runOnUiThread{compose.activity.onBackPressedDispatcher.onBackPressed()};compose.waitForIdle()}
    private fun returnToRoot(){
        repeat(12) {
            if(compose.onAllNodesWithText("Search",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()) return
            val backNodes=compose.onAllNodesWithText("Back",useUnmergedTree=true).fetchSemanticsNodes()
            if(backNodes.isEmpty()) back() else compose.onNode(hasText("Back") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()
        }
        waitForText("Search")
    }
    private fun hideKeyboard(){
        compose.runOnUiThread {
            val manager=compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            manager.hideSoftInputFromWindow(compose.activity.currentFocus?.windowToken,0)
        }
        compose.waitForIdle()
    }
    private fun waitForText(text:String){compose.waitUntil(15_000){compose.onAllNodesWithText(text,substring=true,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}}
}
