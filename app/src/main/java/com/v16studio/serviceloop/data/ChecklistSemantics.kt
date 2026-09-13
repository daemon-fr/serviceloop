package com.v16studio.serviceloop.data

import java.math.BigDecimal

/** One shared projection of current immutable checklist snapshots and active answers. */
data class ChecklistCompleteness(
    val complete: Boolean,
    val requiredComplete: Int,
    val requiredTotal: Int,
    val issueMissingDescription: List<String>,
    val invalidExplicitAnswers: List<String>,
)

fun checklistCompleteness(
    questions: List<ChecklistItemSnapshotEntity>,
    responses: List<WorkingResponseEntity>,
): ChecklistCompleteness {
    val answers = responses.associateBy { it.checklistItemSnapshotId }
    val missingIssues = mutableListOf<String>()
    val invalid = mutableListOf<String>()
    var requiredComplete = 0

    questions.forEach { question ->
        val answer = answers[question.id]
        if (answer?.disposition == "ISSUE_FOUND" && answer.reason.isNullOrBlank()) {
            missingIssues += question.id
        }
        if (answer != null && !isValidExplicitAnswer(question, answer)) {
            invalid += question.id
        }
        if (question.required && isCompleteAnswer(question, answer)) {
            requiredComplete++
        }
    }

    val requiredTotal = questions.count { it.required }
    return ChecklistCompleteness(
        complete = requiredComplete == requiredTotal && invalid.isEmpty(),
        requiredComplete = requiredComplete,
        requiredTotal = requiredTotal,
        issueMissingDescription = missingIssues.distinct(),
        invalidExplicitAnswers = invalid.distinct(),
    )
}

fun isFiniteSignedDecimal(value: String): Boolean = SIGNED_DECIMAL.matches(value.trim()) && runCatching { BigDecimal(value.trim()) }.isSuccess

private fun isCompleteAnswer(question: ChecklistItemSnapshotEntity, answer: WorkingResponseEntity?): Boolean {
    answer ?: return false
    return when (question.responseType) {
        "STATUS" -> when (answer.disposition) {
            "OK" -> true
            "ISSUE_FOUND", "NOT_APPLICABLE" -> !answer.reason.isNullOrBlank()
            else -> false
        }
        "TEXT" -> when (answer.disposition) {
            "VALUE" -> !answer.textValue.isNullOrBlank()
            "NOT_APPLICABLE" -> !answer.reason.isNullOrBlank()
            else -> false
        }
        "NUMBER" -> when (answer.disposition) {
            "VALUE" -> isFiniteSignedDecimal(answer.numberValue.orEmpty())
            "NOT_APPLICABLE" -> !answer.reason.isNullOrBlank()
            else -> false
        }
        else -> false
    }
}

private fun isValidExplicitAnswer(question: ChecklistItemSnapshotEntity, answer: WorkingResponseEntity): Boolean = when (question.responseType) {
    "STATUS" -> when (answer.disposition) {
        "OK", "NOT_CHECKED" -> true
        "ISSUE_FOUND", "NOT_APPLICABLE" -> !answer.reason.isNullOrBlank()
        else -> false
    }
    "TEXT" -> when (answer.disposition) {
        "UNANSWERED" -> true
        "VALUE" -> !answer.textValue.isNullOrBlank()
        "NOT_APPLICABLE" -> !answer.reason.isNullOrBlank()
        else -> false
    }
    "NUMBER" -> when (answer.disposition) {
        "UNANSWERED" -> true
        "VALUE" -> isFiniteSignedDecimal(answer.numberValue.orEmpty())
        "NOT_APPLICABLE" -> !answer.reason.isNullOrBlank()
        else -> false
    }
    else -> false
}

private val SIGNED_DECIMAL = Regex("^[+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)$")
