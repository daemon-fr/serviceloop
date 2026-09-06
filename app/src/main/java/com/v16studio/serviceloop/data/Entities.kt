package com.v16studio.serviceloop.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "customers", indices = [Index(value = ["reference"], unique = true)])
data class CustomerEntity(@PrimaryKey val id: String, val reference: String, val name: String)

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
)
