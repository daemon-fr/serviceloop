package com.v16studio.serviceloop.data

import org.json.JSONObject
import java.time.Instant

/** Effective imported truth is selected by source chronology, never Room row order. */
internal fun effectiveRemoteResults(
    results: List<RemoteFinalResultEntity>,
    includePreviousRevisions: Boolean = false,
): List<RemoteFinalResultEntity> {
    val ordered = results.sortedWith(compareBy<RemoteFinalResultEntity> { result ->
        runCatching { Instant.parse(JSONObject(result.provenanceJson).optString("recordedAt")).toEpochMilli() }
            .getOrDefault(result.importedAtEpochMillis)
    }.thenBy { it.importedAtEpochMillis }.thenBy { it.id })
    return if (includePreviousRevisions) ordered else ordered.groupBy { it.resultId }.values.map { it.last() }
}

internal enum class ImportedFinalKind { WORK_RESULT, DATA_TRANSFER }

/** One shared immutable projection for reportable imported revisions of either transport. */
internal data class EffectiveImportedFinalResult(
    val kind: ImportedFinalKind,
    val logicalResultId: String,
    val originWorkspaceId: String,
    val sourceVisitId: String,
    val sourceWorkItemId: String,
    val sourceFinalRevisionId: String,
    val chronologyEpochMillis: Long,
    val remote: RemoteFinalResultEntity? = null,
    val transferred: TransferredFinalResultEntity? = null,
)

internal fun effectiveImportedFinalResults(
    remoteResults: List<RemoteFinalResultEntity>,
    transferredResults: List<TransferredFinalResultEntity>,
    includePreviousRevisions: Boolean = false,
): List<EffectiveImportedFinalResult> {
    val remote = remoteResults.filter { it.voidedAtEpochMillis == null }.map { row ->
        val provenance = runCatching { JSONObject(row.provenanceJson) }.getOrDefault(JSONObject())
        EffectiveImportedFinalResult(
            ImportedFinalKind.WORK_RESULT, row.resultId, row.technicianId, row.dispatchVisitId, row.dispatchItemId,
            row.sourceFinalRevisionId, runCatching { Instant.parse(provenance.optString("recordedAt")).toEpochMilli() }.getOrDefault(row.importedAtEpochMillis), remote = row,
        )
    }
    val transfer = transferredResults.filter { it.voidedAtEpochMillis == null }.map { row ->
        val provenance = runCatching { JSONObject(row.provenanceJson) }.getOrDefault(JSONObject())
        EffectiveImportedFinalResult(
            ImportedFinalKind.DATA_TRANSFER, row.logicalResultId, row.originWorkspaceId, row.sourceVisitId, row.sourceWorkItemId,
            row.sourceFinalRevisionId, runCatching { Instant.parse(provenance.optString("recordedAt")).toEpochMilli() }.getOrDefault(row.importedAtEpochMillis), transferred = row,
        )
    }
    val ordered = (remote + transfer).sortedWith(compareBy<EffectiveImportedFinalResult> { it.chronologyEpochMillis }
        .thenBy { it.originWorkspaceId }.thenBy { it.sourceVisitId }.thenBy { it.sourceWorkItemId }
        .thenBy { it.sourceFinalRevisionId }.thenBy { it.kind.name })
    return if (includePreviousRevisions) ordered else ordered.groupBy { it.originWorkspaceId to it.logicalResultId }.values.map { it.last() }
}
