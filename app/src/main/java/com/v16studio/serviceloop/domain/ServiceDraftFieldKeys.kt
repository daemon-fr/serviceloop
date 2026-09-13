package com.v16studio.serviceloop.domain

/** Durable, non-localized identities for raw Working Service draft fields. */
object ServiceDraftFieldKeys {
    const val WORK = "work"
    const val PRIVATE = "private"
    const val NOT_PERFORMED_REASON = "result:not-performed-reason"
    const val OVERRIDE_DATE = "recurrence:override-date"
    const val OVERRIDE_REASON = "recurrence:override-reason"

    fun questionValue(snapshotItemId: String) = question(snapshotItemId, "value")
    fun questionIssue(snapshotItemId: String) = question(snapshotItemId, "issue")
    fun questionNotApplicable(snapshotItemId: String) = question(snapshotItemId, "na")

    fun isSupported(fieldKey: String): Boolean = when (fieldKey) {
        WORK, PRIVATE, NOT_PERFORMED_REASON, OVERRIDE_DATE, OVERRIDE_REASON -> true
        else -> QUESTION_KEY.matches(fieldKey)
    }

    private fun question(snapshotItemId: String, suffix: String): String {
        require(snapshotItemId.isNotBlank() && !snapshotItemId.contains(':')) { "Invalid snapshot item id" }
        return "question:$snapshotItemId:$suffix"
    }

    private val QUESTION_KEY = Regex("^question:[^:]+:(value|issue|na)$")
}
