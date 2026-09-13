package com.v16studio.serviceloop

import com.v16studio.serviceloop.data.ChecklistItemSnapshotEntity
import com.v16studio.serviceloop.data.WorkingResponseEntity
import com.v16studio.serviceloop.data.checklistCompleteness
import org.junit.Assert.assertEquals
import org.junit.Test

/** The adopted inspection rules are deliberately table-driven so each matrix row stays visible. */
class ChecklistCompletenessMatrixTest {
    private data class Case(val name: String, val questions: List<ChecklistItemSnapshotEntity>, val responses: List<WorkingResponseEntity>, val complete: Boolean, val requiredComplete: Int, val missingIssue: List<String> = emptyList(), val invalid: List<String> = emptyList())

    @Test fun adopted55CaseChecklistMatrix() {
        val cases = listOf(
            c("status-required-no-answer", "STATUS", true, null, null, null, false, 0),
            c("status-required-not-checked", "STATUS", true, "NOT_CHECKED", null, null, false, 0),
            c("status-required-ok", "STATUS", true, "OK", null, null, true, 1),
            c("status-required-issue-described", "STATUS", true, "ISSUE_FOUND", null, "Loose guard", true, 1),
            c("status-required-issue-blank", "STATUS", true, "ISSUE_FOUND", null, " ", false, 0, listOf("q"), listOf("q")),
            c("status-required-na-described", "STATUS", true, "NOT_APPLICABLE", null, "No access", true, 1),
            c("status-required-na-blank", "STATUS", true, "NOT_APPLICABLE", null, "", false, 0, invalid = listOf("q")),
            c("status-required-unknown", "STATUS", true, "UNANSWERED", null, null, false, 0, invalid = listOf("q")),
            c("status-required-issue-spaces", "STATUS", true, "ISSUE_FOUND", null, "  ", false, 0, listOf("q"), listOf("q")),
            c("status-required-na-spaces", "STATUS", true, "NOT_APPLICABLE", null, "  ", false, 0, invalid = listOf("q")),
            c("status-optional-no-answer", "STATUS", false, null, null, null, true, 0),
            c("status-optional-not-checked", "STATUS", false, "NOT_CHECKED", null, null, true, 0),
            c("status-optional-ok", "STATUS", false, "OK", null, null, true, 0),
            c("status-optional-issue-described", "STATUS", false, "ISSUE_FOUND", null, "Observed", true, 0),
            c("status-optional-issue-blank", "STATUS", false, "ISSUE_FOUND", null, "", false, 0, listOf("q"), listOf("q")),
            c("status-optional-na-described", "STATUS", false, "NOT_APPLICABLE", null, "Not fitted", true, 0),
            c("status-optional-na-blank", "STATUS", false, "NOT_APPLICABLE", null, " ", false, 0, invalid = listOf("q")),
            c("status-optional-unknown", "STATUS", false, "UNANSWERED", null, null, false, 0, invalid = listOf("q")),
            c("text-required-no-answer", "TEXT", true, null, null, null, false, 0),
            c("text-required-unanswered", "TEXT", true, "UNANSWERED", null, null, false, 0),
            c("text-required-value", "TEXT", true, "VALUE", "Recorded", null, true, 1),
            c("text-required-value-blank", "TEXT", true, "VALUE", " ", null, false, 0, invalid = listOf("q")),
            c("text-required-na-described", "TEXT", true, "NOT_APPLICABLE", null, "Not installed", true, 1),
            c("text-required-na-blank", "TEXT", true, "NOT_APPLICABLE", null, "", false, 0, invalid = listOf("q")),
            c("text-required-status-answer", "TEXT", true, "OK", null, null, false, 0, invalid = listOf("q")),
            c("text-optional-no-answer", "TEXT", false, null, null, null, true, 0),
            c("text-optional-unanswered", "TEXT", false, "UNANSWERED", null, null, true, 0),
            c("text-optional-value", "TEXT", false, "VALUE", "Recorded", null, true, 0),
            c("text-optional-value-blank", "TEXT", false, "VALUE", "", null, false, 0, invalid = listOf("q")),
            c("text-optional-na-described", "TEXT", false, "NOT_APPLICABLE", null, "Not applicable", true, 0),
            c("text-optional-na-blank", "TEXT", false, "NOT_APPLICABLE", null, " ", false, 0, invalid = listOf("q")),
            c("text-optional-status-answer", "TEXT", false, "OK", null, null, false, 0, invalid = listOf("q")),
            c("number-required-no-answer", "NUMBER", true, null, null, null, false, 0),
            c("number-required-unanswered", "NUMBER", true, "UNANSWERED", null, null, false, 0),
            c("number-required-value", "NUMBER", true, "VALUE", null, "12.5", true, 1),
            c("number-required-value-invalid", "NUMBER", true, "VALUE", null, "NaN", false, 0, invalid = listOf("q")),
            c("number-required-value-blank", "NUMBER", true, "VALUE", null, " ", false, 0, invalid = listOf("q")),
            c("number-required-na-described", "NUMBER", true, "NOT_APPLICABLE", null, "No gauge", true, 1),
            c("number-required-na-blank", "NUMBER", true, "NOT_APPLICABLE", null, "", false, 0, invalid = listOf("q")),
            c("number-required-value-multiple-decimal", "NUMBER", true, "VALUE", null, "1.2.3", false, 0, invalid = listOf("q")),
            c("number-required-unknown", "NUMBER", true, "OK", null, null, false, 0, invalid = listOf("q")),
            c("number-optional-no-answer", "NUMBER", false, null, null, null, true, 0),
            c("number-optional-unanswered", "NUMBER", false, "UNANSWERED", null, null, true, 0),
            c("number-optional-value", "NUMBER", false, "VALUE", null, "-12", true, 0),
            c("number-optional-value-invalid", "NUMBER", false, "VALUE", null, "abc", false, 0, invalid = listOf("q")),
            c("number-optional-value-blank", "NUMBER", false, "VALUE", null, " ", false, 0, invalid = listOf("q")),
            c("number-optional-na-described", "NUMBER", false, "NOT_APPLICABLE", null, "No reading", true, 0),
            c("number-optional-na-blank", "NUMBER", false, "NOT_APPLICABLE", null, "", false, 0, invalid = listOf("q")),
            c("number-optional-plus-decimal", "NUMBER", false, "VALUE", null, "+.5", true, 0),
            c("number-optional-leading-dot", "NUMBER", false, "VALUE", null, ".5", true, 0),
            c("number-optional-transitional-minus", "NUMBER", false, "VALUE", null, "-", false, 0, invalid = listOf("q")),
            c("number-optional-transitional-trailing-dot", "NUMBER", false, "VALUE", null, "12.", false, 0, invalid = listOf("q")),
            multi("required-complete-optional-invalid-issue", listOf(q("required", "STATUS", true), q("q", "STATUS", false)), listOf(a("required", "OK"), a("q", "ISSUE_FOUND", reason = "")), false, 1, listOf("q"), listOf("q")),
            multi("required-incomplete-optional-valid", listOf(q("required", "TEXT", true), q("q", "STATUS", false)), listOf(a("q", "OK")), false, 0),
            multi("inactive-draft-does-not-count", listOf(q("q", "STATUS", true)), listOf(a("q", "OK", issueDraft = "Old finding", naDraft = "Old reason")), true, 1),
        )
        assertEquals("Expected the adopted 55-row inspection matrix", 55, cases.size)
        cases.forEach { value ->
            val result = checklistCompleteness(value.questions, value.responses)
            assertEquals(value.name, value.complete, result.complete)
            assertEquals(value.name, value.requiredComplete, result.requiredComplete)
            assertEquals(value.name, value.questions.count { it.required }, result.requiredTotal)
            assertEquals(value.name, value.missingIssue, result.issueMissingDescription)
            assertEquals(value.name, value.invalid, result.invalidExplicitAnswers)
        }
    }

    private fun c(name: String, type: String, required: Boolean, disposition: String?, text: String?, reason: String?, complete: Boolean, requiredComplete: Int, missing: List<String> = emptyList(), invalid: List<String> = emptyList()) =
        multi(name, listOf(q("q", type, required)), disposition?.let { listOf(a("q", it, text = text.takeIf { type == "TEXT" && disposition == "VALUE" }, number = reason.takeIf { type == "NUMBER" && disposition == "VALUE" }, reason = reason.takeIf { disposition in setOf("ISSUE_FOUND", "NOT_APPLICABLE") })) }.orEmpty(), complete, requiredComplete, missing, invalid)

    private fun multi(name: String, questions: List<ChecklistItemSnapshotEntity>, responses: List<WorkingResponseEntity>, complete: Boolean, requiredComplete: Int, missing: List<String> = emptyList(), invalid: List<String> = emptyList()) = Case(name, questions, responses, complete, requiredComplete, missing, invalid)
    private fun q(id: String, type: String, required: Boolean) = ChecklistItemSnapshotEntity(id, "snapshot", 1, id, type, null, required, null)
    private fun a(id: String, disposition: String, text: String? = null, number: String? = null, reason: String? = null, issueDraft: String? = null, naDraft: String? = null) = WorkingResponseEntity("response-$id", "work", id, disposition, text, number, reason, 1, issueDraft, naDraft)
}
