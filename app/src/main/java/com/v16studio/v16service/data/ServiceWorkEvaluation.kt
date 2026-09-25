package com.v16studio.v16service.data

import com.v16studio.v16service.domain.CompletionBlocker
import com.v16studio.v16service.domain.CompletionBlockerKind
import com.v16studio.v16service.domain.FulfillmentEligibility
import com.v16studio.v16service.domain.RecurrenceCalculator
import java.time.LocalDate

/**
 * The persisted facts needed to evaluate one Service work item.
 *
 * This is deliberately a value-only input. Loading these facts, and deciding which
 * documentation rows are in scope, remain repository responsibilities.
 */
data class ServiceWorkEvaluationInput(
    val servicePlanId: String?,
    val capturedObligationId: String?,
    val outcome: String?,
    val fulfillsCurrentObligation: Boolean?,
    val dueDateSnapshot: String?,
    val intervalCountSnapshot: Int?,
    val intervalUnitSnapshot: String?,
    val actualServiceDate: LocalDate,
    val publicWork: String,
    val notPerformedReason: String?,
    val confirmedNextDueDate: String?,
    val nextDueDateCalculated: Boolean?,
    val nextDueOverrideReason: String?,
    val plan: ServicePlanFacts?,
    val obligation: ServiceObligationFacts?,
    val checklist: ChecklistCompleteness?,
    val checklistQuestionLabels: Map<String, String> = emptyMap(),
)

data class ServicePlanFacts(
    val id: String,
    val state: String,
    val currentObligationId: String?,
)

data class ServiceObligationFacts(
    val id: String,
    val planId: String,
    val dueDate: String,
    val consumedAtEpochMillis: Long?,
)

data class ServiceWorkEvaluation(
    val fulfillmentEligibility: FulfillmentEligibility,
    val currentObligationOutstanding: Boolean,
    val projectedFulfillsCurrentObligation: Boolean?,
    val calculatedNextDueDate: String?,
    val completionBlockers: List<CompletionBlocker>,
) {
    val completionReady: Boolean get() = completionBlockers.isEmpty()
}

/** Pure Service-work evaluation shared by read projections and command validation. */
object ServiceWorkEvaluator {
    fun requiresOutcome(outcome: String?): Boolean = outcome.isNullOrBlank()

    fun requiresPublicWork(outcome: String?): Boolean = outcome == "PERFORMED" || outcome == "PARTLY_PERFORMED"

    fun evaluate(input: ServiceWorkEvaluationInput): ServiceWorkEvaluation {
        val eligibility = fulfillmentEligibility(input)
        val projectedFulfills = when {
            input.outcome == null -> null
            input.outcome == "NOT_PERFORMED" -> false
            input.outcome == "PERFORMED" && eligibility == FulfillmentEligibility.ELIGIBLE -> true
            input.outcome == "PARTLY_PERFORMED" && eligibility == FulfillmentEligibility.ELIGIBLE -> input.fulfillsCurrentObligation
            else -> false
        }
        val calculated = if (
            eligibility == FulfillmentEligibility.ELIGIBLE &&
            projectedFulfills == true &&
            input.intervalCountSnapshot != null &&
            input.intervalUnitSnapshot != null
        ) {
            RecurrenceCalculator.nextDate(
                input.actualServiceDate,
                input.intervalCountSnapshot,
                input.intervalUnitSnapshot,
            ).toString()
        } else {
            null
        }

        val blockers = buildList {
            if (requiresOutcome(input.outcome)) add(CompletionBlocker(CompletionBlockerKind.OUTCOME, "Choose an outcome"))
            if (requiresPublicWork(input.outcome)) {
                if (input.publicWork.isBlank()) add(CompletionBlocker(CompletionBlockerKind.WORK_PERFORMED, "Work performed is required"))
            }
            if (input.outcome == "NOT_PERFORMED" && input.notPerformedReason.isNullOrBlank()) {
                add(CompletionBlocker(CompletionBlockerKind.NOT_PERFORMED_REASON, "Reason is required"))
            }
            input.checklist?.let { completeness ->
                if (!completeness.complete) add(CompletionBlocker(CompletionBlockerKind.CHECKLIST_INCOMPLETE, "Complete all required checklist questions"))
                completeness.issueMissingDescription.forEach { questionId ->
                    add(
                        CompletionBlocker(
                            CompletionBlockerKind.FINDING_DESCRIPTION,
                            "${input.checklistQuestionLabels[questionId] ?: questionId}: Issue found needs a public description",
                            questionId,
                            input.checklistQuestionLabels[questionId],
                        ),
                    )
                }
            }
            if (input.outcome == "PARTLY_PERFORMED" && eligibility == FulfillmentEligibility.ELIGIBLE && projectedFulfills == null) {
                add(CompletionBlocker(CompletionBlockerKind.NEXT_DUE, "Choose whether this completes the due service"))
            }
            if (projectedFulfills == true && input.confirmedNextDueDate == null) {
                add(CompletionBlocker(CompletionBlockerKind.NEXT_DUE, "Confirm the next due date"))
            }
        }

        return ServiceWorkEvaluation(
            fulfillmentEligibility = eligibility,
            currentObligationOutstanding = currentObligationOutstanding(input),
            projectedFulfillsCurrentObligation = projectedFulfills,
            calculatedNextDueDate = calculated,
            completionBlockers = blockers,
        )
    }

    fun fulfillmentEligibility(input: ServiceWorkEvaluationInput): FulfillmentEligibility = when {
        input.servicePlanId == null -> FulfillmentEligibility.NO_CURRENT_OBLIGATION
        input.capturedObligationId == null -> FulfillmentEligibility.HISTORY_ONLY
        input.outcome == null -> FulfillmentEligibility.OUTCOME_INELIGIBLE
        input.outcome !in setOf("PERFORMED", "PARTLY_PERFORMED") -> FulfillmentEligibility.OUTCOME_INELIGIBLE
        input.plan == null || input.plan.state != "ACTIVE" -> FulfillmentEligibility.PLAN_INELIGIBLE
        input.obligation == null ||
            input.obligation.planId != input.plan.id ||
            input.obligation.consumedAtEpochMillis != null ||
            input.plan.currentObligationId != input.capturedObligationId -> FulfillmentEligibility.CURRENT_OBLIGATION_CHANGED
        else -> FulfillmentEligibility.ELIGIBLE
    }

    private fun currentObligationOutstanding(input: ServiceWorkEvaluationInput): Boolean =
        input.outcome in setOf("PARTLY_PERFORMED", "NOT_PERFORMED") &&
            input.servicePlanId != null &&
            input.capturedObligationId != null &&
            input.fulfillsCurrentObligation == false &&
            !input.dueDateSnapshot.isNullOrBlank() &&
            input.plan != null &&
            input.plan.state == "ACTIVE" &&
            input.plan.currentObligationId == input.capturedObligationId &&
            input.obligation != null &&
            input.obligation.planId == input.plan.id &&
            input.obligation.consumedAtEpochMillis == null &&
            input.obligation.dueDate == input.dueDateSnapshot
}
