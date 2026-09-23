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
