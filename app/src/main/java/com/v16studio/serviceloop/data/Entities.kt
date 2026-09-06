package com.v16studio.serviceloop.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "customers", indices = [Index(value = ["reference"], unique = true)])
data class CustomerEntity(
    @PrimaryKey val id: String,
    val reference: String,
    val name: String,
    val contactName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val privateNote: String? = null,
    @ColumnInfo(defaultValue = "'ACTIVE'") val state: String = "ACTIVE",
)

@Entity(
    tableName = "sites",
    foreignKeys = [ForeignKey(CustomerEntity::class, ["id"], ["customerId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("customerId"), Index(value = ["reference"], unique = true)],
)
data class SiteEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val reference: String,
    val name: String,
    val address: String?,
    val privateAccessNotes: String?,
    val contactName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    @ColumnInfo(defaultValue = "0") val isDefault: Boolean = false,
    @ColumnInfo(defaultValue = "'ACTIVE'") val state: String = "ACTIVE",
)

@Entity(
    tableName = "equipment",
    foreignKeys = [ForeignKey(SiteEntity::class, ["id"], ["siteId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("siteId"), Index(value = ["reference"], unique = true)],
)
data class EquipmentEntity(
    @PrimaryKey val id: String,
    val siteId: String,
    val reference: String,
    val technicianIdentifier: String?,
    val name: String,
    val make: String?,
    val model: String?,
    val serialNumber: String?,
    val privateNotes: String?,
    @ColumnInfo(defaultValue = "'ACTIVE'") val state: String = "ACTIVE",
)

@Entity(
    tableName = "service_plans",
    foreignKeys = [ForeignKey(EquipmentEntity::class, ["id"], ["equipmentId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("equipmentId"), Index(value = ["reference"], unique = true), Index(value = ["currentObligationId"], unique = true)],
)
data class ServicePlanEntity(
    @PrimaryKey val id: String,
    val equipmentId: String,
    val reference: String,
    val name: String,
    val intervalCount: Int,
    val intervalUnit: String,
    val currentDueDate: String,
    val state: String,
    val currentObligationId: String?,
    val lastCountedCompletionDate: String? = null,
    val lastCountedRevisionId: String? = null,
    val reusableTemplateId: String? = null,
)

@Entity(
    tableName = "service_obligations",
    foreignKeys = [ForeignKey(ServicePlanEntity::class, ["id"], ["planId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("planId"), Index(value = ["planId", "sequence"], unique = true)],
)
data class ServiceObligationEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val sequence: Long,
    val dueDate: String,
    val createdAtEpochMillis: Long,
    val consumedAtEpochMillis: Long? = null,
    val consumedByRevisionId: String? = null,
)

@Entity(tableName = "template_snapshots")
data class TemplateSnapshotEntity(
    @PrimaryKey val id: String,
    val sourceTemplateId: String?,
    val templateName: String,
    val revision: Int,
    val capturedAtEpochMillis: Long,
)

@Entity(
    tableName = "checklist_item_snapshots",
    foreignKeys = [ForeignKey(TemplateSnapshotEntity::class, ["id"], ["templateSnapshotId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("templateSnapshotId"), Index(value = ["templateSnapshotId", "position"], unique = true)],
)
data class ChecklistItemSnapshotEntity(
    @PrimaryKey val id: String,
    val templateSnapshotId: String,
    val position: Int,
    val label: String,
    val responseType: String,
    val unit: String?,
    val required: Boolean,
    val privateGuidance: String?,
)

@Entity(
    tableName = "working_visits",
    foreignKeys = [
        ForeignKey(CustomerEntity::class, ["id"], ["customerId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(SiteEntity::class, ["id"], ["siteId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("customerId"), Index("siteId"), Index(value = ["reference"], unique = true)],
)
data class WorkingVisitEntity(
    @PrimaryKey val id: String,
    val reference: String,
    val customerId: String,
    val siteId: String,
    val actualServiceDate: String,
    val customerNameSnapshot: String,
    val siteNameSnapshot: String,
    val siteAddressSnapshot: String?,
    val state: String,
    val modifiedAtEpochMillis: Long,
    val customerReferenceSnapshot: String? = null,
    val siteReferenceSnapshot: String? = null,
    val reportBusinessNameSnapshot: String? = null,
    val reportTechnicianNameSnapshot: String? = null,
    val reportPhoneSnapshot: String? = null,
    val reportEmailSnapshot: String? = null,
    val reportPostalAddressSnapshot: String? = null,
    val reportZoneIdSnapshot: String? = null,
    val scheduledAtEpochMillis: Long? = null,
    val appointmentZoneId: String? = null,
    val scheduleChangeReason: String? = null,
    val cancellationReason: String? = null,
    val cancelledAtEpochMillis: Long? = null,
)

@Entity(
    tableName = "work_items",
    foreignKeys = [
        ForeignKey(WorkingVisitEntity::class, ["id"], ["visitId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(EquipmentEntity::class, ["id"], ["equipmentId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(ServicePlanEntity::class, ["id"], ["servicePlanId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(ServiceObligationEntity::class, ["id"], ["capturedObligationId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(TemplateSnapshotEntity::class, ["id"], ["templateSnapshotId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("visitId"), Index("equipmentId"), Index("servicePlanId"), Index("capturedObligationId"), Index("templateSnapshotId")],
)
data class WorkItemEntity(
    @PrimaryKey val id: String,
    val visitId: String,
    val equipmentId: String,
    val servicePlanId: String?,
    val capturedObligationId: String?,
    val templateSnapshotId: String?,
    val equipmentNameSnapshot: String,
    val equipmentReferenceSnapshot: String,
    val serviceNameSnapshot: String,
    val planReferenceSnapshot: String?,
    val dueDateSnapshot: String?,
    val intervalCountSnapshot: Int?,
    val intervalUnitSnapshot: String?,
    val checklistReviewed: Boolean,
    val outcome: String?,
    val fulfillsCurrentObligation: Boolean?,
    val notPerformedReason: String? = null,
    val confirmedNextDueDate: String? = null,
    val nextDueDateCalculated: Boolean? = null,
    val nextDueOverrideReason: String? = null,
    val equipmentIdentifierSnapshot: String? = null,
    val equipmentMakeSnapshot: String? = null,
    val equipmentModelSnapshot: String? = null,
    val equipmentSerialSnapshot: String? = null,
)

@Entity(tableName = "business_profiles")
data class BusinessProfileEntity(
    @PrimaryKey val id: String = "primary",
    val businessName: String,
    val technicianName: String,
    val phone: String?,
    val email: String?,
    val postalAddress: String?,
    val zoneId: String,
    val modifiedAtEpochMillis: Long,
)

@Entity(
    tableName = "final_records",
    foreignKeys = [ForeignKey(WorkingVisitEntity::class, ["id"], ["visitId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["visitId"], unique = true)],
)
data class FinalRecordEntity(
    @PrimaryKey val id: String,
    val visitId: String,
    val currentRevisionId: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "final_record_revisions",
    foreignKeys = [ForeignKey(FinalRecordEntity::class, ["id"], ["recordId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["recordId", "revisionNumber"], unique = true)],
)
data class FinalRecordRevisionEntity(
    @PrimaryKey val id: String,
    val recordId: String,
    val revisionNumber: Int,
    val visitReference: String,
    val actualServiceDate: String,
    val recordedAtEpochMillis: Long,
    val customerName: String,
    val siteName: String,
    val siteAddress: String?,
    val businessName: String,
    val technicianName: String,
    val businessPhone: String?,
    val businessEmail: String?,
    val businessAddress: String?,
    val businessZoneId: String,
    val privateInternalNote: String?,
    val customerReference: String? = null,
    val siteReference: String? = null,
)

@Entity(
    tableName = "final_work_items",
    foreignKeys = [ForeignKey(FinalRecordRevisionEntity::class, ["id"], ["revisionId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("revisionId"), Index(value = ["revisionId", "position"], unique = true)],
)
data class FinalWorkItemEntity(
    @PrimaryKey val id: String,
    val revisionId: String,
    val position: Int,
    val sourceWorkItemId: String,
    val equipmentId: String,
    val equipmentName: String,
    val equipmentReference: String,
    val equipmentIdentifier: String?,
    val equipmentMake: String?,
    val equipmentModel: String?,
    val equipmentSerial: String?,
    val serviceName: String,
    val planId: String?,
    val planReference: String?,
    val outcome: String,
    val publicWorkNote: String?,
    val notPerformedReason: String?,
    val fulfilledObligation: Boolean,
    val oldDueDate: String?,
    val nextDueDate: String?,
    val intervalCount: Int?,
    val intervalUnit: String?,
    val capturedObligationId: String?,
    val privateInternalNote: String?,
    val nextDueDateCalculated: Boolean? = null,
    val nextDueOverrideReason: String? = null,
)

@Entity(
    tableName = "final_checklist_items",
    foreignKeys = [ForeignKey(FinalWorkItemEntity::class, ["id"], ["finalWorkItemId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("finalWorkItemId"), Index(value = ["finalWorkItemId", "position"], unique = true)],
)
data class FinalChecklistItemEntity(
    @PrimaryKey val id: String,
    val finalWorkItemId: String,
    val position: Int,
    val templateSnapshotId: String?,
    val templateRevision: Int?,
    val label: String,
    val responseType: String,
    val unit: String?,
    val required: Boolean,
    val disposition: String,
    val textValue: String?,
    val numberValue: String?,
    val reason: String?,
)

@Entity(
    tableName = "report_renditions",
    foreignKeys = [ForeignKey(FinalRecordRevisionEntity::class, ["id"], ["revisionId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["revisionId", "versionNumber"], unique = true), Index(value = ["relativePath"], unique = true)],
)
data class ReportRenditionEntity(
    @PrimaryKey val id: String,
    val revisionId: String,
    val versionNumber: Int,
    val generatedAtEpochMillis: Long?,
    val relativePath: String,
    val sha256: String?,
    val byteSize: Long?,
    val pageCount: Int?,
    val status: String,
    val kind: String,
    val failureMessage: String?,
)

@Entity(
    tableName = "work_item_public_drafts",
    foreignKeys = [ForeignKey(WorkItemEntity::class, ["id"], ["workItemId"], onDelete = ForeignKey.CASCADE)],
)
data class WorkItemPublicDraftEntity(@PrimaryKey val workItemId: String, val workPerformed: String)

@Entity(
    tableName = "work_item_private_drafts",
    foreignKeys = [ForeignKey(WorkItemEntity::class, ["id"], ["workItemId"], onDelete = ForeignKey.CASCADE)],
)
data class WorkItemPrivateDraftEntity(@PrimaryKey val workItemId: String, val internalNote: String)

@Entity(
    tableName = "working_responses",
    foreignKeys = [
        ForeignKey(WorkItemEntity::class, ["id"], ["workItemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ChecklistItemSnapshotEntity::class, ["id"], ["checklistItemSnapshotId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("workItemId"), Index("checklistItemSnapshotId"), Index(value = ["workItemId", "checklistItemSnapshotId"], unique = true)],
)
data class WorkingResponseEntity(
    @PrimaryKey val id: String,
    val workItemId: String,
    val checklistItemSnapshotId: String,
    val disposition: String,
    val textValue: String?,
    val numberValue: String?,
    val reason: String?,
    val modifiedAtEpochMillis: Long,
)

@Entity(
    tableName = "follow_ups",
    foreignKeys = [
        ForeignKey(CustomerEntity::class, ["id"], ["customerId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(SiteEntity::class, ["id"], ["siteId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(EquipmentEntity::class, ["id"], ["equipmentId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("customerId"), Index("siteId"), Index("equipmentId"), Index(value = ["reference"], unique = true)],
)
data class FollowUpEntity(
    @PrimaryKey val id: String,
    val reference: String,
    val type: String,
    val title: String,
    val dueDate: String,
    val state: String,
    val customerId: String,
    val siteId: String?,
    val equipmentId: String?,
    val privatePlanningNote: String?,
    val sourceVisitId: String? = null,
    val sourceWorkItemId: String? = null,
    val updatedAtEpochMillis: Long = 0,
    val closedAtEpochMillis: Long? = null,
    val closureReason: String? = null,
)

@Entity(tableName = "attachments", indices = [Index(value = ["storedRelativePath"], unique = true), Index(value = ["ownerType", "ownerId"])])
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val ownerType: String,
    val ownerId: String,
    val storedRelativePath: String,
    val sha256: String,
    val originalDisplayName: String?,
    val mimeType: String,
    val includedInCustomerReport: Boolean,
    val availability: String,
    val byteSize: Long = 0,
    val caption: String? = null,
)

@Entity(tableName = "reusable_templates", indices = [Index(value = ["reference"], unique = true)])
data class ReusableTemplateEntity(
    @PrimaryKey val id: String,
    val reference: String,
    val name: String,
    val currentRevisionId: String,
    @ColumnInfo(defaultValue = "'ACTIVE'") val state: String = "ACTIVE",
    val modifiedAtEpochMillis: Long,
)

@Entity(
    tableName = "reusable_template_revisions",
    foreignKeys = [ForeignKey(ReusableTemplateEntity::class, ["id"], ["templateId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("templateId"), Index(value = ["templateId", "revisionNumber"], unique = true)],
)
data class ReusableTemplateRevisionEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val revisionNumber: Int,
    val nameSnapshot: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "reusable_template_items",
    foreignKeys = [ForeignKey(ReusableTemplateRevisionEntity::class, ["id"], ["revisionId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("revisionId"), Index(value = ["revisionId", "position"], unique = true)],
)
data class ReusableTemplateItemEntity(
    @PrimaryKey val id: String,
    val revisionId: String,
    val position: Int,
    val label: String,
    val responseType: String,
    val unit: String?,
    val required: Boolean,
    val privateGuidance: String?,
)

@Entity(
    tableName = "contact_notes",
    foreignKeys = [ForeignKey(CustomerEntity::class, ["id"], ["customerId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("customerId"), Index("siteId"), Index("equipmentId"), Index(value = ["reference"], unique = true)],
)
data class ContactNoteEntity(
    @PrimaryKey val id: String,
    val reference: String,
    val customerId: String,
    val siteId: String?,
    val equipmentId: String?,
    val channel: String,
    val occurredAtEpochMillis: Long,
    val outcome: String,
    val privateNote: String?,
    val createdAtEpochMillis: Long,
    val editedAtEpochMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val enteredInError: Boolean = false,
    val errorReason: String? = null,
)

@Entity(
    tableName = "follow_up_events",
    foreignKeys = [ForeignKey(FollowUpEntity::class, ["id"], ["followUpId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("followUpId")],
)
data class FollowUpEventEntity(
    @PrimaryKey val id: String,
    val followUpId: String,
    val eventType: String,
    val occurredAtEpochMillis: Long,
    val reason: String,
    val dueDate: String?,
)

@Entity(
    tableName = "part_entries",
    foreignKeys = [ForeignKey(WorkItemEntity::class, ["id"], ["workItemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workItemId")],
)
data class PartEntryEntity(
    @PrimaryKey val id: String,
    val workItemId: String,
    val description: String,
    val quantity: String,
    val unit: String,
    val modifiedAtEpochMillis: Long,
)

@Entity(
    tableName = "visit_claims",
    foreignKeys = [
        ForeignKey(ServiceObligationEntity::class, ["id"], ["obligationId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(WorkingVisitEntity::class, ["id"], ["visitId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["visitId", "obligationId"], unique = true), Index("visitId")],
)
data class VisitClaimEntity(
    @PrimaryKey val obligationId: String,
    val visitId: String,
    val claimedAtEpochMillis: Long,
)

@Entity(
    tableName = "final_part_entries",
    foreignKeys = [ForeignKey(FinalWorkItemEntity::class, ["id"], ["finalWorkItemId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("finalWorkItemId")],
)
data class FinalPartEntryEntity(
    @PrimaryKey val id: String,
    val finalWorkItemId: String,
    val position: Int,
    val description: String,
    val quantity: String,
    val unit: String,
)

@Entity(
    tableName = "final_photo_entries",
    foreignKeys = [ForeignKey(FinalWorkItemEntity::class, ["id"], ["finalWorkItemId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("finalWorkItemId"), Index("sourceAttachmentId")],
)
data class FinalPhotoEntryEntity(
    @PrimaryKey val id: String,
    val finalWorkItemId: String,
    val position: Int,
    val sourceAttachmentId: String,
    val storedRelativePath: String,
    val sha256: String,
    val byteSize: Long,
    val mimeType: String,
    val caption: String?,
)

@Entity(tableName = "plan_schedule_changes", foreignKeys = [ForeignKey(ServicePlanEntity::class, ["id"], ["planId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("planId")])
data class PlanScheduleChangeEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val oldDueDate: String,
    val newDueDate: String,
    val reason: String,
    val changedAtEpochMillis: Long,
)

@Entity(tableName = "visit_schedule_events", foreignKeys = [ForeignKey(WorkingVisitEntity::class, ["id"], ["visitId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("visitId")])
data class VisitScheduleEventEntity(
    @PrimaryKey val id: String,
    val visitId: String,
    val eventType: String,
    val oldServiceDate: String,
    val newServiceDate: String?,
    val oldScheduledAtEpochMillis: Long?,
    val newScheduledAtEpochMillis: Long?,
    val reason: String,
    val occurredAtEpochMillis: Long,
)
