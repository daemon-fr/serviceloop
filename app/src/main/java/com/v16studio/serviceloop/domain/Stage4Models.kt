package com.v16studio.serviceloop.domain

enum class HistoryType { ALL, SERVICE_RECORDS, CONTACTS, FOLLOW_UPS, CHANGES }
enum class HistorySort { EVENT_NEWEST, EVENT_OLDEST, RECORDED_NEWEST }
enum class HistoryScopeType { GLOBAL, CUSTOMER, SITE, EQUIPMENT }

data class HistoryScope(val type: HistoryScopeType, val id: String? = null, val label: String = "All records")

data class HistoryEntry(
    val id: String,
    val type: HistoryType,
    val eventKind: String,
    val title: String,
    val subtitle: String,
    val eventDate: String,
    val recordedAtEpochMillis: Long,
    val routeType: String,
    val routeId: String,
    val customerId: String? = null,
    val siteId: String? = null,
    val equipmentId: String? = null,
)

data class HistoryQuery(
    val scope: HistoryScope = HistoryScope(HistoryScopeType.GLOBAL),
    val type: HistoryType = HistoryType.ALL,
    val from: String? = null,
    val to: String? = null,
    val sort: HistorySort = HistorySort.EVENT_NEWEST,
    val search: String = "",
)

data class RecordVersionSummary(val id: String, val revisionNumber: Int, val recordedAtEpochMillis: Long, val correctionReason: String?, val current: Boolean)
data class ReportVersionSummary(val id: String, val revisionId: String, val versionNumber: Int, val generatedAtEpochMillis: Long?, val status: String, val kind: String, val relativePath: String, val currentRevision: Boolean)

data class CorrectionWorkDraft(
    val id: String,
    val sourceFinalWorkItemId: String,
    val position: Int,
    val equipmentName: String,
    val serviceName: String,
    val outcome: String,
    val publicWorkNote: String,
    val notPerformedReason: String,
    val fulfilledObligation: Boolean,
    val oldDueDate: String?,
    val proposedNextDueDate: String?,
)

data class CorrectionDraft(
    val id: String,
    val recordId: String,
    val baseRevisionId: String,
    val reason: String,
    val actualServiceDate: String,
    val customerName: String,
    val siteName: String,
    val siteAddress: String,
    val businessName: String,
    val technicianName: String,
    val privateNote: String,
    val scheduleAcknowledged: Boolean,
    val modifiedAtEpochMillis: Long,
    val items: List<CorrectionWorkDraft>,
)

data class LifecycleBlocker(val kind: String, val id: String, val label: String, val route: String)
data class LifecycleReview(val action: String, val subjectId: String, val subjectLabel: String, val blockers: List<LifecycleBlocker>, val consequences: List<String>) { val allowed get() = blockers.isEmpty() }

data class MoveReview(
    val equipmentId: String,
    val equipmentLabel: String,
    val oldSiteId: String,
    val oldContext: String,
    val destinations: List<VisitSiteOption>,
    val carriedPlans: List<PlanDetail>,
    val blockers: List<LifecycleBlocker>,
)

enum class AttentionKind { CORRECTION_DRAFT, REPORT_FAILED, REPORT_MISSING }
data class AttentionItem(val id: String, val kind: AttentionKind, val title: String, val detail: String, val route: String)

data class DatasetSummary(
    val datasetId: String,
    val customers: Int,
    val sites: Int,
    val equipment: Int,
    val plans: Int,
    val visits: Int,
    val unfinishedVisits: Int,
    val attachments: Int,
    val reports: Int,
    val storedBytes: Long,
    val availableBytes: Long,
    val lastBackupAttemptAtEpochMillis: Long?,
    val lastVerifiedFullBackupAtEpochMillis: Long?,
    val lastVerifiedSnapshotAtEpochMillis: Long?,
    val lastVerifiedDestination: String?,
    val lastVerifiedSize: Long?,
    val changedSinceBackup: Boolean,
    val backupReminderDays: Int,
    val restrictedRecoveryState: Boolean,
)

data class BackupInspection(
    val formatVersion: Int,
    val snapshotAtEpochMillis: Long,
    val datasetId: String,
    val complete: Boolean,
    val tables: Int,
    val records: Int,
    val files: Int,
    val missingFiles: List<String>,
    val stagedPayload: ByteArray,
)

data class BackupResult(val bytes: ByteArray, val snapshotAtEpochMillis: Long, val complete: Boolean, val missingFiles: List<String>)

data class CsvImportRow(val rowNumber: Int, val values: Map<String, String>, val status: String, val messages: List<String>, val branchKey: String)
data class CsvImportPreview(val rows: List<CsvImportRow>, val newCustomers: Int, val newSites: Int, val newEquipment: Int, val errors: Int, val warnings: Int)
data class ImportResult(val customersCreated: Int, val sitesCreated: Int, val equipmentCreated: Int, val existingUnchanged: Int)
