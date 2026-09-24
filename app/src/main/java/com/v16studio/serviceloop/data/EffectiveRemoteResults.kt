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
    return if (includePreviousRevisions) ordered.filter { it.voidedAtEpochMillis == null }
    else ordered.groupBy { it.technicianId to it.resultId }.values.map { lineage ->
        val numbered = lineage.mapNotNull { row ->
            row.sourcePayloadJson?.let { raw -> runCatching { JSONObject(raw).getJSONObject("result").getInt("sourceFinalRevisionNumber") }.getOrNull() }
                ?.let { number -> row to number }
        }
        if (numbered.size != lineage.size) lineage.last() else {
            require(numbered.groupBy { it.second }.values.all { values -> values.map { it.first.sourceFinalRevisionId }.distinct().size == 1 }) {
                "Conflicting source revision numbers in received work"
            }
            numbered.maxWith(compareBy<Pair<RemoteFinalResultEntity, Int>> { it.second }.thenBy { it.first.importedAtEpochMillis }).first
        }
    }.filter { it.voidedAtEpochMillis == null }
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
    val identityNamespace: String = "SOURCE",
    val voided: Boolean = false,
    val sourceRevisionNumber: Int? = null,
)

internal fun effectiveImportedFinalResults(
    remoteResults: List<RemoteFinalResultEntity>,
    transferredResults: List<TransferredFinalResultEntity>,
    includePreviousRevisions: Boolean = false,
): List<EffectiveImportedFinalResult> {
    val remote = remoteResults.map { row ->
        val provenance = runCatching { JSONObject(row.provenanceJson) }.getOrDefault(JSONObject())
        val source = row.sourcePayloadJson?.let { runCatching { JSONObject(it).optJSONObject("result") }.getOrNull() }
        EffectiveImportedFinalResult(
            ImportedFinalKind.WORK_RESULT, row.resultId, row.technicianId,
            source?.optString("sourceVisitId")?.takeIf(String::isNotBlank) ?: row.dispatchVisitId,
            source?.optString("sourceWorkItemId")?.takeIf(String::isNotBlank) ?: row.dispatchItemId,
            row.sourceFinalRevisionId, runCatching { Instant.parse(source?.optString("recordedAt")?.takeIf(String::isNotBlank)
                ?: provenance.optString("recordedAt")).toEpochMilli() }.getOrDefault(row.importedAtEpochMillis), remote = row,
            identityNamespace = if (source?.optString("sourceVisitId").isNullOrBlank() || source?.optString("sourceWorkItemId").isNullOrBlank()) "WORK_RESULT_LEGACY" else "SOURCE",
            voided = row.voidedAtEpochMillis != null,
            sourceRevisionNumber = source?.optInt("sourceFinalRevisionNumber")?.takeIf { it > 0 },
        )
    }
    val transfer = transferredResults.map { row ->
        val provenance = runCatching { JSONObject(row.provenanceJson) }.getOrDefault(JSONObject())
        EffectiveImportedFinalResult(
            ImportedFinalKind.DATA_TRANSFER, row.logicalResultId, row.originWorkspaceId, row.sourceVisitId, row.sourceWorkItemId,
            row.sourceFinalRevisionId, runCatching { Instant.parse(provenance.optString("recordedAt")).toEpochMilli() }.getOrDefault(row.importedAtEpochMillis), transferred = row,
            identityNamespace = if (row.sourcePayloadJson == null) "TRANSFER_LEGACY" else "SOURCE",
            voided = row.voidedAtEpochMillis != null,
            sourceRevisionNumber = row.sourcePayloadJson?.let { raw -> runCatching { JSONObject(raw).getInt("sourceFinalRevisionNumber") }.getOrNull() },
        )
    }
    val ordered = (remote + transfer).sortedWith(compareBy<EffectiveImportedFinalResult> { it.chronologyEpochMillis }
        .thenBy { it.originWorkspaceId }.thenBy { it.sourceVisitId }.thenBy { it.sourceWorkItemId }
        .thenBy { it.sourceFinalRevisionId }.thenBy { it.kind.name })
    val revisions = ordered.groupBy {
        listOf(it.identityNamespace, it.originWorkspaceId, it.sourceVisitId, it.sourceWorkItemId, it.sourceFinalRevisionId)
    }.values.map { matching -> matching.lastOrNull { it.voided } ?: matching.last() }
    return if (includePreviousRevisions) revisions.filterNot { it.voided } else revisions.groupBy {
        listOf(it.identityNamespace, it.originWorkspaceId, it.sourceVisitId, it.sourceWorkItemId)
    }.values.map { lineage ->
        val numbered = lineage.filter { it.sourceRevisionNumber != null }
        require(numbered.groupBy { it.sourceRevisionNumber }.values.all { revisionsAtNumber ->
            revisionsAtNumber.map { it.sourceFinalRevisionId }.distinct().size == 1
        }) { "Conflicting source revision numbers in imported work" }
        if (numbered.isEmpty()) lineage.last() else numbered.maxWith(compareBy<EffectiveImportedFinalResult> { it.sourceRevisionNumber!! }
            .thenBy { it.chronologyEpochMillis }.thenBy { it.sourceFinalRevisionId })
    }.filterNot { it.voided }
}
