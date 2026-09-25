package com.v16studio.v16service.domain

/** Durable, non-localized identities for raw Working Service draft fields. */
object ServiceDraftFieldKeys {
    enum class QuestionFieldKind {
        VALUE,
        ISSUE,
        NOT_APPLICABLE,
    }

    data class ParsedQuestionField(
        val snapshotItemId: String,
        val kind: QuestionFieldKind,
    )

    const val WORK = "work"
    const val PRIVATE = "private"
    const val NOT_PERFORMED_REASON = "result:not-performed-reason"
    const val OVERRIDE_DATE = "recurrence:override-date"
    const val OVERRIDE_REASON = "recurrence:override-reason"

    fun questionValue(snapshotItemId: String) = question(snapshotItemId, "value")
    fun questionIssue(snapshotItemId: String) = question(snapshotItemId, "issue")
    fun questionNotApplicable(snapshotItemId: String) = question(snapshotItemId, "na")
    fun photoCaption(photoId: String): String {
        require(photoId.isNotBlank() && !photoId.contains(':')) { "Invalid photo id" }
        return "photo:$photoId:caption"
    }

    fun parsePhotoCaption(fieldKey: String): String? = fieldKey.split(':').takeIf { it.size == 3 && it[0] == "photo" && it[1].isNotBlank() && it[2] == "caption" }?.get(1)

    fun parseQuestionField(fieldKey: String): ParsedQuestionField? {
        val parts = fieldKey.split(':')
        if (parts.size != 3 || parts[0] != "question" || parts[1].isBlank()) return null
        val kind = when (parts[2]) {
            "value" -> QuestionFieldKind.VALUE
            "issue" -> QuestionFieldKind.ISSUE
            "na" -> QuestionFieldKind.NOT_APPLICABLE
            else -> return null
        }
        return ParsedQuestionField(parts[1], kind)
    }

    fun isSupported(fieldKey: String): Boolean = when (fieldKey) {
        WORK, PRIVATE, NOT_PERFORMED_REASON, OVERRIDE_DATE, OVERRIDE_REASON -> true
        else -> parseQuestionField(fieldKey) != null || parsePhotoCaption(fieldKey) != null
    }

    private fun question(snapshotItemId: String, suffix: String): String {
        require(snapshotItemId.isNotBlank() && !snapshotItemId.contains(':')) { "Invalid snapshot item id" }
        return "question:$snapshotItemId:$suffix"
    }

}
