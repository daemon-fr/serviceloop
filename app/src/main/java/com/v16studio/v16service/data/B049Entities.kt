package com.v16studio.v16service.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Each exact source revision is acknowledged once; a logical result may have later revisions. */
@Entity(tableName = "work_result_receipts", indices = [
    Index("packageId"),
    Index(value = ["exporterId", "resultId", "sourceFinalRevisionId"], unique = true),
    Index("dispatchVisitId"), Index("dispatchItemId"),
])
data class WorkResultReceiptEntity(
    @PrimaryKey val id: String,
    val packageId: String,
    val resultId: String,
    val sourceFinalRevisionId: String,
    val assignmentIssuerId: String,
    val exporterId: String,
    val dispatchVisitId: String,
    val dispatchItemId: String,
    val assignmentGeneration: Int,
    val assignmentMaterialHash: String,
    val receivedAtEpochMillis: Long,
    val payloadSha256: String,
    val status: String,
    val conflictReason: String?,
    val appliedAtEpochMillis: Long?,
    val recurrenceAppliedAtEpochMillis: Long?,
)

/** Frozen technician-authored truth; never represented as Coordinator Working drafts. */
@Entity(tableName = "remote_final_results", indices = [
    Index(value = ["technicianId", "resultId", "sourceFinalRevisionId"], unique = true),
    Index("dispatchVisitId"), Index("dispatchItemId"), Index("customerId"),
])
data class RemoteFinalResultEntity(
    @PrimaryKey val id: String,
    val resultId: String,
    val sourceFinalRevisionId: String,
    val dispatchVisitId: String,
    val dispatchItemId: String,
    val localVisitId: String?,
    val localWorkItemId: String?,
    val technicianId: String,
    val technicianName: String,
    val technicianDesignation: String?,
    val customerId: String?,
    val customerSnapshotJson: String,
    val siteSnapshotJson: String,
    val subjectSnapshotJson: String,
    val serviceDate: String,
    val outcome: String,
    val workPerformed: String?,
    val notPerformedReason: String?,
    val checklistJson: String,
    val findingsJson: String,
    val partsJson: String,
    val internalNotes: String?,
    val followUpsJson: String,
    val recurrenceJson: String,
    val provenanceJson: String,
    val importedAtEpochMillis: Long,
    val voidedAtEpochMillis: Long? = null,
    val sourcePayloadJson: String,
)

@Entity(tableName = "remote_result_photos", indices = [Index(value = ["remoteFinalResultId", "sourcePhotoId"], unique = true)])
data class RemoteResultPhotoEntity(
    @PrimaryKey val id: String,
    val remoteFinalResultId: String,
    val sourcePhotoId: String,
    val relativePath: String,
    val sha256: String,
    val byteSize: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val caption: String?,
    val dispatchItemId: String,
    val includeInReport: Boolean,
    val visibility: String,
)

@Entity(tableName = "aggregate_reports", indices = [Index("customerId")])
data class AggregateReportEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val customerSnapshotJson: String,
    val siteId: String?,
    val equipmentId: String?,
    val fromDate: String?,
    val throughDate: String?,
    val businessSnapshotJson: String,
    val createdAtEpochMillis: Long,
    val status: String,
)

@Entity(tableName = "aggregate_report_sources", primaryKeys = ["aggregateReportId", "sourceOrder"], indices = [Index("sourceFinalRevisionId")])
data class AggregateReportSourceEntity(
    val aggregateReportId: String,
    val sourceOrder: Int,
    val sourceFinalRevisionId: String,
    val sourceKind: String,
    val visitId: String,
    val sourceEntityId: String,
)

@Entity(tableName = "aggregate_report_renditions", indices = [Index("aggregateReportId")])
data class AggregateReportRenditionEntity(
    @PrimaryKey val id: String,
    val aggregateReportId: String,
    val relativePath: String?,
    val sha256: String?,
    val byteSize: Long?,
    val pageCount: Int?,
    val generatedAtEpochMillis: Long,
    val status: String,
    val failureReason: String?,
)

/** An owned report-quality derivative must exist before an original can be removed. */
@Entity(tableName = "retained_images", indices = [Index(value = ["sourceKind", "sourceId"], unique = true)])
data class RetainedImageEntity(
    @PrimaryKey val id: String,
    val sourceKind: String,
    val sourceId: String,
    val originalRelativePath: String?,
    val originalDeletedAtEpochMillis: Long?,
    val derivativeRelativePath: String,
    val derivativeSha256: String,
    val derivativeByteSize: Long,
    val derivativeWidth: Int,
    val derivativeHeight: Int,
    val derivativeMimeType: String,
    val createdAtEpochMillis: Long,
    val originalDeletionRequestedAtEpochMillis: Long? = null,
)
