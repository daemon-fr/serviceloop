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
)

enum class FulfillmentEligibility {
    ELIGIBLE,
    OUTCOME_INELIGIBLE,
    CHECKLIST_NOT_REVIEWED,
}

sealed interface SaveStatus {
    data object Idle : SaveStatus
    data object Saving : SaveStatus
    data class Saved(val atEpochMillis: Long) : SaveStatus
    data class Failed(val message: String, val lastSavedAtEpochMillis: Long?) : SaveStatus
}
