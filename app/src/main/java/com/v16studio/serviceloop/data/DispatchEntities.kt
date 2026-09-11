package com.v16studio.serviceloop.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "technician_identity", indices = [Index(value = ["technicianId"], unique = true)])
data class TechnicianIdentityEntity(
    @androidx.room.PrimaryKey val id: String = "primary",
    val technicianId: String,
    val displayName: String,
    val createdAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long,
)

@Entity(tableName = "dispatch_technicians")
data class DispatchTechnicianEntity(
    @androidx.room.PrimaryKey val technicianId: String,
    val displayName: String,
    val createdAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long,
)

@Entity(tableName = "dispatch_teams", indices = [Index(value = ["name"], unique = true)])
data class DispatchTeamEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val createdAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long,
)

@Entity(
    tableName = "dispatch_team_members",
    primaryKeys = ["teamId", "technicianId"],
    foreignKeys = [
        ForeignKey(DispatchTeamEntity::class, ["id"], ["teamId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(DispatchTechnicianEntity::class, ["technicianId"], ["technicianId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("teamId"), Index("technicianId")],
)
data class DispatchTeamMemberEntity(val teamId: String, val technicianId: String, val isLeader: Boolean)

@Entity(
    tableName = "dispatch_outbox_visits",
    foreignKeys = [ForeignKey(SiteEntity::class, ["id"], ["siteId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("siteId")],
)
data class DispatchOutboxVisitEntity(
    @androidx.room.PrimaryKey val dispatchVisitId: String,
    val managerReference: String?,
    val siteId: String,
    val serviceDate: String,
    val appointmentLocalTime: String?,
    val appointmentZoneId: String,
    val instructions: String?,
    val lastExportedGeneration: Int?,
    val lastExportedMaterialHash: String?,
    val lastExportedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long,
    val concludedAtEpochMillis: Long? = null,
    val canceledAtEpochMillis: Long? = null,
    val cancellationReason: String? = null,
    val lastExportedCancellationAtEpochMillis: Long? = null,
)

@Entity(
    tableName = "dispatch_outbox_visit_teams",
    primaryKeys = ["dispatchVisitId", "teamId"],
    foreignKeys = [
        ForeignKey(DispatchOutboxVisitEntity::class, ["dispatchVisitId"], ["dispatchVisitId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(DispatchTeamEntity::class, ["id"], ["teamId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("dispatchVisitId"), Index("teamId")],
)
data class DispatchOutboxVisitTeamEntity(val dispatchVisitId: String, val teamId: String)

@Entity(
    tableName = "dispatch_outbox_items",
    foreignKeys = [
        ForeignKey(DispatchOutboxVisitEntity::class, ["dispatchVisitId"], ["dispatchVisitId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(EquipmentEntity::class, ["id"], ["equipmentId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("dispatchVisitId"), Index("equipmentId"), Index(value = ["dispatchVisitId", "position"], unique = true)],
)
data class DispatchOutboxItemEntity(
    @androidx.room.PrimaryKey val dispatchItemId: String,
    val dispatchVisitId: String,
    val position: Int,
    val equipmentId: String,
    val taskName: String,
    val servicePlanReference: String?,
    val dueDateSnapshot: String?,
)

@Entity(
    tableName = "dispatch_outbox_item_assignees",
    primaryKeys = ["dispatchItemId", "technicianId"],
    foreignKeys = [
        ForeignKey(DispatchOutboxItemEntity::class, ["dispatchItemId"], ["dispatchItemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(DispatchTechnicianEntity::class, ["technicianId"], ["technicianId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("dispatchItemId"), Index("technicianId")],
)
data class DispatchOutboxItemAssigneeEntity(val dispatchItemId: String, val technicianId: String)

@Entity(
    tableName = "dispatch_visit_bindings",
    foreignKeys = [ForeignKey(WorkingVisitEntity::class, ["id"], ["localVisitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["localVisitId"], unique = true)],
)
data class DispatchVisitBindingEntity(
    @androidx.room.PrimaryKey val dispatchVisitId: String,
    val localVisitId: String,
    val appliedGeneration: Int,
    val packageId: String,
    val senderLabel: String,
    val managerReference: String?,
    val instructionsSnapshot: String?,
    val participantSnapshotJson: String,
    val leaderIdsJson: String,
    val teamSnapshotJson: String,
    val appliedMaterialHash: String,
    val controlledFingerprint: String,
    val importedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "dispatch_item_bindings",
    primaryKeys = ["dispatchVisitId", "dispatchItemId"],
    foreignKeys = [
        ForeignKey(DispatchVisitBindingEntity::class, ["dispatchVisitId"], ["dispatchVisitId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(WorkItemEntity::class, ["id"], ["localWorkItemId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("dispatchVisitId"), Index(value = ["localWorkItemId"], unique = true)],
)
data class DispatchItemBindingEntity(
    val dispatchVisitId: String,
    val dispatchItemId: String,
    val localWorkItemId: String?,
    val equipmentReferenceSnapshot: String,
    val taskNameSnapshot: String,
    val servicePlanReferenceSnapshot: String?,
    val dueDateSnapshot: String?,
    val assignedTechniciansJson: String,
    val assignmentMeaning: String,
    val localRole: String,
    val documentationDisposition: String,
    val deferredToTechnicianId: String? = null,
    val deferredToName: String? = null,
)

@Entity(
    tableName = "final_dispatch_visits",
    foreignKeys = [ForeignKey(FinalRecordRevisionEntity::class, ["id"], ["revisionId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("dispatchVisitId")],
)
data class FinalDispatchVisitEntity(
    @androidx.room.PrimaryKey val revisionId: String,
    val dispatchVisitId: String,
    val generation: Int,
    val managerReference: String?,
    val senderLabel: String,
    val documentingTechnicianId: String,
    val documentingTechnicianName: String,
)

@Entity(
    tableName = "final_dispatch_items",
    foreignKeys = [ForeignKey(FinalWorkItemEntity::class, ["id"], ["finalWorkItemId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("dispatchItemId")],
)
data class FinalDispatchItemEntity(
    @androidx.room.PrimaryKey val finalWorkItemId: String,
    val dispatchItemId: String,
    val assignedTechniciansJson: String,
    val assignmentMeaning: String,
    val localDocumentationRole: String,
)
