package com.v16studio.v16service.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Stable source-to-local mapping for additive DATA_TRANSFER imports. */
@Entity(tableName = "data_transfer_bindings", indices = [
    Index(value = ["originWorkspaceId", "entityType", "sourceEntityId"], unique = true),
    Index(value = ["entityType", "localEntityId"]),
])
data class DataTransferBindingEntity(
    @PrimaryKey val id: String,
    val originWorkspaceId: String,
    val entityType: String,
    val sourceEntityId: String,
    val localEntityId: String,
    val appliedSourceFingerprint: String,
    val importedAtEpochMillis: Long,
)

/** Immutable transferred service result. This is deliberately independent of local Visit/obligation rows. */
@Entity(tableName = "transferred_final_results", indices = [
    Index(value = ["originWorkspaceId", "sourceWorkItemId", "sourceFinalRevisionId"], unique = true),
    Index(value = ["originWorkspaceId", "sourceVisitId"]),
    Index("logicalResultId"), Index("localCustomerId"), Index("localSiteId"), Index("localEquipmentId"),
])
data class TransferredFinalResultEntity(
    @PrimaryKey val id: String,
    val originWorkspaceId: String,
    val sourceVisitId: String,
    val sourceWorkItemId: String,
    val sourceFinalRevisionId: String,
    val logicalResultId: String,
    val relayExporterId: String,
    val importedAtEpochMillis: Long,
    val localCustomerId: String?,
    val localSiteId: String?,
    val localEquipmentId: String?,
    val customerSnapshotJson: String,
    val siteSnapshotJson: String,
    val subjectSnapshotJson: String,
    val visitReference: String,
    val serviceDate: String,
    val technicianId: String,
    val technicianName: String,
    val technicianDesignation: String?,
    val serviceName: String,
    val outcome: String,
    val workPerformed: String?,
    val notPerformedReason: String?,
    val checklistJson: String,
    val findingsJson: String,
    val partsJson: String,
    val internalNotes: String?,
    val recurrenceJson: String,
    val payloadSha256: String,
    val provenanceJson: String,
    val voidedAtEpochMillis: Long? = null,
    val sourcePayloadJson: String,
)

/** Receiver-owned bytes and immutable context, including standalone Image Archive transfers. */
@Entity(tableName = "transferred_evidence", indices = [
    Index(value = ["sourceIdentityKey"], unique = true),
    Index("localCustomerId"), Index("localSiteId"), Index("localEquipmentId"), Index("transferredFinalResultId"),
])
data class TransferredEvidenceEntity(
    @PrimaryKey val id: String,
    val sourceIdentityKey: String,
    val originWorkspaceId: String,
    val sourcePhotoId: String,
    val sourceVisitId: String,
    val sourceWorkItemId: String,
    val sourceFinalRevisionId: String?,
    val transferredFinalResultId: String?,
    val relayExporterId: String,
    val localCustomerId: String?,
    val localSiteId: String?,
    val localEquipmentId: String?,
    val serviceDate: String,
    val visitReference: String,
    val serviceName: String,
    val relativePath: String,
    val sha256: String,
    val byteSize: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val caption: String?,
    val visibility: String,
    val includedInCustomerReport: Boolean,
    val importedAtEpochMillis: Long,
    val provenanceJson: String,
)

/** Immutable imported planning/contact/change context. Never an active FollowUpEntity or local ChangeEntry. */
@Entity(tableName = "transferred_history_entries", indices = [
    Index(value = ["originWorkspaceId", "family", "sourceEntityId", "sourceRevisionKey"], unique = true),
    Index("localCustomerId"), Index("localSiteId"), Index("localEquipmentId"),
])
data class TransferredHistoryEntryEntity(
    @PrimaryKey val id: String,
    val originWorkspaceId: String,
    val family: String,
    val sourceEntityId: String,
    val sourceRevisionId: String?,
    val sourceRevisionKey: String,
    val relayExporterId: String,
    val localCustomerId: String?,
    val localSiteId: String?,
    val localEquipmentId: String?,
    val eventDateTime: String?,
    val payloadJson: String,
    val payloadSha256: String,
    val importedAtEpochMillis: Long,
)
