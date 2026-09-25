package com.v16studio.v16service

import com.v16studio.v16service.data.ChecklistCompleteness
import com.v16studio.v16service.data.ServiceObligationFacts
import com.v16studio.v16service.data.ServicePlanFacts
import com.v16studio.v16service.data.ServiceWorkEvaluationInput
import com.v16studio.v16service.data.ServiceWorkEvaluator
import com.v16studio.v16service.domain.CompletionBlockerKind
import com.v16studio.v16service.domain.FulfillmentEligibility
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceWorkEvaluationTest {
    private val actualDate = LocalDate.of(2026, 9, 5)
    private val activePlan = ServicePlanFacts("plan-1", "ACTIVE", "obligation-1")
    private val currentObligation = ServiceObligationFacts("obligation-1", "plan-1", "2026-09-01", null)

    @Test fun eligibilityMatrixKeepsHistoryPlanAndStaleObligationMeaning() {
        assertEquals(
            FulfillmentEligibility.NO_CURRENT_OBLIGATION,
            evaluate(servicePlanId = null, capturedObligationId = null, plan = null, obligation = null).fulfillmentEligibility,
        )
        assertEquals(
            FulfillmentEligibility.HISTORY_ONLY,
            evaluate(capturedObligationId = null).fulfillmentEligibility,
        )
        assertEquals(
            FulfillmentEligibility.PLAN_INELIGIBLE,
            evaluate(plan = activePlan.copy(state = "INACTIVE")).fulfillmentEligibility,
        )
        assertEquals(
            FulfillmentEligibility.CURRENT_OBLIGATION_CHANGED,
            evaluate(plan = activePlan.copy(currentObligationId = "obligation-2")).fulfillmentEligibility,
        )
        assertEquals(FulfillmentEligibility.ELIGIBLE, evaluate().fulfillmentEligibility)
    }

    @Test fun performedAutomaticallyFulfillsAndCalculatesNextDue() {
        val result = ServiceWorkEvaluator.evaluate(evaluateInput(outcome = "PERFORMED"))

        assertEquals(FulfillmentEligibility.ELIGIBLE, result.fulfillmentEligibility)
        assertTrue(result.projectedFulfillsCurrentObligation == true)
        assertEquals("2026-12-05", result.calculatedNextDueDate)
        assertTrue(result.completionReady)
    }

    @Test fun partlyPerformedRemainsUnansweredUntilChoiceAndKeepDueStaysOutstanding() {
        val unanswered = ServiceWorkEvaluator.evaluate(evaluateInput(outcome = "PARTLY_PERFORMED", fulfills = null, confirmedNextDueDate = null))
        assertEquals(listOf(CompletionBlockerKind.NEXT_DUE), unanswered.completionBlockers.map { it.kind })
        assertEquals(null, unanswered.projectedFulfillsCurrentObligation)
        assertFalse(unanswered.completionReady)

        val fulfill = ServiceWorkEvaluator.evaluate(evaluateInput(outcome = "PARTLY_PERFORMED", fulfills = true))
        assertTrue(fulfill.projectedFulfillsCurrentObligation == true)
        assertEquals("2026-12-05", fulfill.calculatedNextDueDate)
        assertTrue(fulfill.completionReady)

        val keepDue = ServiceWorkEvaluator.evaluate(evaluateInput(outcome = "PARTLY_PERFORMED", fulfills = false, confirmedNextDueDate = null))
        assertFalse(keepDue.projectedFulfillsCurrentObligation == true)
        assertTrue(keepDue.currentObligationOutstanding)
        assertTrue(keepDue.completionReady)
    }

    @Test fun notPerformedRequiresReasonAndLeavesTheObligationOutstanding() {
        val missingReason = ServiceWorkEvaluator.evaluate(evaluateInput(outcome = "NOT_PERFORMED", fulfills = false, notPerformedReason = null, confirmedNextDueDate = null))
        assertEquals(listOf(CompletionBlockerKind.NOT_PERFORMED_REASON), missingReason.completionBlockers.map { it.kind })

        val withReason = ServiceWorkEvaluator.evaluate(evaluateInput(outcome = "NOT_PERFORMED", fulfills = false, notPerformedReason = "Access unavailable", confirmedNextDueDate = null))
        assertTrue(withReason.completionReady)
        assertTrue(withReason.currentObligationOutstanding)
        assertEquals(FulfillmentEligibility.OUTCOME_INELIGIBLE, withReason.fulfillmentEligibility)
    }

    @Test fun checklistAndNextDueBlockersRetainTheirSeparateSemantics() {
        val incomplete = ServiceWorkEvaluator.evaluate(
            evaluateInput(
                checklist = ChecklistCompleteness(false, 0, 1, emptyList(), listOf("question-1")),
            ),
        )
        assertTrue(incomplete.completionBlockers.any { it.kind == CompletionBlockerKind.CHECKLIST_INCOMPLETE })
        assertEquals("2026-12-05", incomplete.calculatedNextDueDate)

        val issue = ServiceWorkEvaluator.evaluate(
            evaluateInput(
                checklist = ChecklistCompleteness(true, 0, 0, listOf("question-1"), emptyList()),
                checklistQuestionLabels = mapOf("question-1" to "Guard"),
            ),
        )
        assertEquals(CompletionBlockerKind.FINDING_DESCRIPTION, issue.completionBlockers.single().kind)
        assertEquals("Guard: Issue found needs a public description", issue.completionBlockers.single().message)

        val noChecklist = ServiceWorkEvaluator.evaluate(evaluateInput(checklist = null))
        assertTrue(noChecklist.completionBlockers.isEmpty())

        val missingNextDue = ServiceWorkEvaluator.evaluate(evaluateInput(confirmedNextDueDate = null))
        assertEquals(listOf(CompletionBlockerKind.NEXT_DUE), missingNextDue.completionBlockers.map { it.kind })
    }

    @Test fun manualOverrideAndNoRecurringWorkDoNotAcquireNewBlockers() {
        val override = ServiceWorkEvaluator.evaluate(
            evaluateInput(
                confirmedNextDueDate = "2027-01-15",
                nextDueDateCalculated = false,
                nextDueOverrideReason = "Seasonal access window",
            ),
        )
        assertTrue(override.completionReady)
        assertEquals("2026-12-05", override.calculatedNextDueDate)

        val oneOff = ServiceWorkEvaluator.evaluate(
            evaluateInput(
                servicePlanId = null,
                capturedObligationId = null,
                plan = null,
                obligation = null,
                confirmedNextDueDate = null,
            ),
        )
        assertEquals(FulfillmentEligibility.NO_CURRENT_OBLIGATION, oneOff.fulfillmentEligibility)
        assertTrue(oneOff.completionReady)
        assertEquals(null, oneOff.calculatedNextDueDate)
    }

    private fun evaluate(
        servicePlanId: String? = "plan-1",
        capturedObligationId: String? = "obligation-1",
        plan: ServicePlanFacts? = activePlan,
        obligation: ServiceObligationFacts? = currentObligation,
    ) = ServiceWorkEvaluator.evaluate(evaluateInput(servicePlanId, capturedObligationId, plan, obligation))

    private fun evaluateInput(
        servicePlanId: String? = "plan-1",
        capturedObligationId: String? = "obligation-1",
        plan: ServicePlanFacts? = activePlan,
        obligation: ServiceObligationFacts? = currentObligation,
        outcome: String? = "PERFORMED",
        fulfills: Boolean? = true,
        confirmedNextDueDate: String? = "2026-12-05",
        nextDueDateCalculated: Boolean? = true,
        nextDueOverrideReason: String? = null,
        notPerformedReason: String? = null,
        checklist: ChecklistCompleteness? = null,
        checklistQuestionLabels: Map<String, String> = emptyMap(),
    ) = ServiceWorkEvaluationInput(
        servicePlanId = servicePlanId,
        capturedObligationId = capturedObligationId,
        outcome = outcome,
        fulfillsCurrentObligation = fulfills,
        dueDateSnapshot = "2026-09-01",
        intervalCountSnapshot = 3,
        intervalUnitSnapshot = "MONTHS",
        actualServiceDate = actualDate,
        publicWork = "Completed service",
        notPerformedReason = notPerformedReason,
        confirmedNextDueDate = confirmedNextDueDate,
        nextDueDateCalculated = nextDueDateCalculated,
        nextDueOverrideReason = nextDueOverrideReason,
        plan = plan,
        obligation = obligation,
        checklist = checklist,
        checklistQuestionLabels = checklistQuestionLabels,
    )
}
