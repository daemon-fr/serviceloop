package com.v16studio.serviceloop.domain

data class HomeSummary(
    val workingVisitId: String?,
    val workingVisitReference: String?,
    val workingSite: String?,
    val savedAtEpochMillis: Long?,
    val inspectionWorkItemId: String?,
    val bookedVisitReference: String?,
    val bookedVisitDate: String?,
    val dueFollowUpCount: Int,
    val dueFollowUpReference: String?,
    val dueFollowUpTitle: String?,
    val overdueCount: Int,
    val dueSoonCount: Int,
    val workingVisitCount: Int = 0,
    val bookedVisitCount: Int = 0,
)

data class EquipmentSummary(
    val id: String,
    val name: String,
    val reference: String,
    val technicianIdentifier: String?,
    val siteName: String,
    val customerName: String,
    val nearestDueDate: String?,
)

data class CustomerSummary(
    val id: String,
    val name: String,
    val reference: String,
    val siteCount: Int,
    val equipmentCount: Int,
)

data class EquipmentPlan(
    val id: String,
    val name: String,
    val reference: String,
    val interval: String,
    val dueDate: String,
    val state: String,
    val currentObligationId: String?,
    val isOverdue: Boolean,
)

data class EquipmentDetail(
    val id: String,
    val name: String,
    val reference: String,
    val technicianIdentifier: String?,
    val makeModel: String,
    val serialNumber: String?,
    val siteName: String,
    val customerName: String,
    val plans: List<EquipmentPlan>,
    val workingItemId: String?,
    val make: String = "",
    val model: String = "",
    val privateNote: String = "",
    val state: String = "ACTIVE",
)

enum class ResponseDisposition {
    UNANSWERED, NOT_CHECKED, OK, ISSUE_FOUND, NOT_APPLICABLE, VALUE
}

data class InspectionQuestion(
    val responseId: String,
    val snapshotItemId: String,
    val position: Int,
    val label: String,
    val responseType: String,
    val unit: String?,
    val required: Boolean,
    val disposition: ResponseDisposition,
    val textValue: String?,
    val numberValue: String?,
    val reason: String?,
)

data class InspectionDraft(
    val workItemId: String,
    val visitId: String,
    val visitReference: String,
    val siteName: String,
    val equipmentName: String,
    val equipmentReference: String,
    val serviceName: String,
    val dueDate: String?,
    val interval: String?,
    val templateRevision: Int?,
    val workPerformed: String,
    val privateInternalNote: String,
    val checklistReviewed: Boolean,
    val outcome: String?,
    val fulfillsCurrentObligation: Boolean?,
    val modifiedAtEpochMillis: Long,
    val questions: List<InspectionQuestion>,
)

data class VisitSummary(val id: String, val reference: String, val siteName: String, val actualServiceDate: String, val state: String, val finalRecordId: String?, val resumeWorkItemId: String? = null)

data class BusinessProfile(
    val businessName: String,
    val technicianName: String,
    val phone: String = "",
    val email: String = "",
    val postalAddress: String = "",
    val zoneId: String,
    val modifiedAtEpochMillis: Long? = null,
) {
    val ready: Boolean get() = businessName.isNotBlank() && technicianName.isNotBlank()
}

data class CompletionLine(
    val workItemId: String,
    val equipmentName: String,
    val equipmentReference: String,
    val serviceName: String,
    val outcome: String?,
    val fulfillmentEligibility: FulfillmentEligibility,
    val fulfillsCurrentObligation: Boolean,
    val dueDate: String?,
    val proposedNextDueDate: String?,
    val workPerformed: String,
    val checklistReviewed: Boolean = false,
    val notPerformedReason: String? = null,
    val calculatedNextDueDate: String? = null,
    val confirmedNextDueDate: String? = null,
    val nextDueDateCalculated: Boolean? = null,
    val nextDueOverrideReason: String? = null,
    val blockers: List<CompletionBlocker> = emptyList(),
)

enum class CompletionBlockerKind { OUTCOME, WORK_PERFORMED, NOT_PERFORMED_REASON, CHECKLIST_REVIEW, FINDING_DESCRIPTION, NEXT_DUE }
data class CompletionBlocker(val kind: CompletionBlockerKind, val message: String, val questionId: String? = null, val questionLabel: String? = null)

enum class FulfillmentEligibility {
    ELIGIBLE,
    HISTORY_ONLY,
    NO_CURRENT_OBLIGATION,
    OUTCOME_INELIGIBLE,
    CHECKLIST_NOT_REVIEWED,
    PLAN_INELIGIBLE,
    CURRENT_OBLIGATION_CHANGED,
}

data class PublicChecklistItem(
    val position: Int,
    val label: String,
    val responseType: String,
    val unit: String?,
    val required: Boolean,
    val disposition: String,
    val value: String?,
    val reason: String?,
)

data class PublicWorkLine(
    val position: Int,
    val equipmentName: String,
    val equipmentReference: String,
    val equipmentIdentification: String,
    val serviceName: String,
    val outcome: String,
    val publicWorkNote: String?,
    val notPerformedReason: String?,
    val fulfilledObligation: Boolean,
    val oldDueDate: String?,
    val nextDueDate: String?,
    val checklist: List<PublicChecklistItem>,
    val planReference: String? = null,
    val isRecurringPlan: Boolean = false,
    val parts: List<PublicPart> = emptyList(),
    val photos: List<PublicPhoto> = emptyList(),
    val historyOnly: Boolean = false,
)

data class PublicPart(val description: String, val quantity: String, val unit: String)
data class PublicPhoto(val relativePath: String, val sha256: String, val byteSize: Long, val mimeType: String, val caption: String?)

data class PublicReportModel(
    val recordId: String,
    val revisionId: String,
    val revisionNumber: Int,
    val visitReference: String,
    val actualServiceDate: String,
    val recordedAtEpochMillis: Long,
    val businessName: String,
    val technicianName: String,
    val businessContact: String,
    val customerName: String,
    val siteName: String,
    val siteAddress: String?,
    val lines: List<PublicWorkLine>,
    val customerReference: String? = null,
    val siteReference: String? = null,
)

data class FinalRecordDetail(
    val public: PublicReportModel,
    val privateNotes: List<String>,
    val report: ReportRendition?,
    val voided: Boolean = false,
    val publicVoidReason: String? = null,
)

data class ReportRendition(
    val id: String,
    val revisionId: String,
    val versionNumber: Int,
    val generatedAtEpochMillis: Long?,
    val relativePath: String,
    val sha256: String?,
    val byteSize: Long?,
    val pageCount: Int?,
    val status: String,
    val failureMessage: String?,
)

sealed interface FinalizeResult {
    data class Success(val recordId: String) : FinalizeResult
    data class Blocked(val message: String) : FinalizeResult
}

sealed interface SaveStatus {
    data object Idle : SaveStatus
    data object Saving : SaveStatus
    data class Saved(val atEpochMillis: Long) : SaveStatus
    data class Failed(val message: String, val lastSavedAtEpochMillis: Long?) : SaveStatus
}
