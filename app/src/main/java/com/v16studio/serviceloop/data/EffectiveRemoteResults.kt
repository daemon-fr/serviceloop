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
            row.sourcePayloadJson?.let { raw -> runCatching { FinalSourceSnapshot.record(raw, "WORK_RESULT").getInt("sourceFinalRevisionNumber") }.getOrNull() }
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
    val chronologyProvenance: String = "UNKNOWN",
)

internal fun effectiveImportedFinalResults(
    remoteResults: List<RemoteFinalResultEntity>,
    transferredResults: List<TransferredFinalResultEntity>,
    includePreviousRevisions: Boolean = false,
): List<EffectiveImportedFinalResult> {
    val remote = remoteResults.map { row ->
        val provenance = runCatching { JSONObject(row.provenanceJson) }.getOrDefault(JSONObject())
        val source = row.sourcePayloadJson?.let { runCatching { FinalSourceSnapshot.record(it, "WORK_RESULT") }.getOrNull() }
        EffectiveImportedFinalResult(
            ImportedFinalKind.WORK_RESULT, row.resultId, row.technicianId,
            source?.optString("sourceVisitId")?.takeIf(String::isNotBlank) ?: row.dispatchVisitId,
            source?.optString("sourceWorkItemId")?.takeIf(String::isNotBlank) ?: row.dispatchItemId,
            row.sourceFinalRevisionId, runCatching { Instant.parse(source?.optString("recordedAt")?.takeIf(String::isNotBlank)
                ?: provenance.optString("recordedAt")).toEpochMilli() }.getOrDefault(row.importedAtEpochMillis), remote = row,
            identityNamespace = if (source?.optString("sourceVisitId").isNullOrBlank() || source?.optString("sourceWorkItemId").isNullOrBlank()) "WORK_RESULT_LEGACY" else "SOURCE",
            voided = row.voidedAtEpochMillis != null,
            sourceRevisionNumber = source?.optInt("sourceFinalRevisionNumber")?.takeIf { it > 0 },
            chronologyProvenance = provenance.optString("chronologyProvenance", "UNKNOWN"),
        )
    }
    val transfer = transferredResults.map { row ->
        val provenance = runCatching { JSONObject(row.provenanceJson) }.getOrDefault(JSONObject())
        EffectiveImportedFinalResult(
            ImportedFinalKind.DATA_TRANSFER, row.logicalResultId, row.originWorkspaceId, row.sourceVisitId, row.sourceWorkItemId,
            row.sourceFinalRevisionId, runCatching { Instant.parse(provenance.optString("recordedAt")).toEpochMilli() }.getOrDefault(row.importedAtEpochMillis), transferred = row,
            identityNamespace = if (row.sourcePayloadJson == null) "TRANSFER_LEGACY" else "SOURCE",
            voided = row.voidedAtEpochMillis != null,
            sourceRevisionNumber = row.sourcePayloadJson?.let { raw -> runCatching { FinalSourceSnapshot.record(raw, "PERFORMED_WORK").getInt("sourceFinalRevisionNumber") }.getOrNull() },
            chronologyProvenance = if (row.sourcePayloadJson == null) "UNKNOWN" else "SOURCE_RECORDED_AT",
        )
    }
    val ordered = (remote + transfer).sortedWith(compareBy<EffectiveImportedFinalResult> { it.chronologyEpochMillis }
        .thenBy { it.originWorkspaceId }.thenBy { it.sourceVisitId }.thenBy { it.sourceWorkItemId }
        .thenBy { it.sourceFinalRevisionId }.thenBy { it.kind.name })
    val revisions = ordered.groupBy {
        listOf(it.identityNamespace, it.originWorkspaceId, it.sourceVisitId, it.sourceWorkItemId, it.sourceFinalRevisionId)
    }.values.map { matching ->
        if (matching.size > 1 && matching.all { it.identityNamespace == "SOURCE" }) {
            val projections = matching.map(FinalSourceProjectionV2::of)
            require(projections.map { it.publicFacts }.distinct().size == 1 &&
                projections.filter { it.privateFacts != null }.map { it.privateFacts }.distinct().size <= 1) {
                "Conflicting representations of the same source revision"
            }
        }
        matching.lastOrNull { it.voided } ?: matching.firstOrNull { it.remote != null } ?: matching.first()
    }
    return if (includePreviousRevisions) revisions.filterNot { it.voided } else revisions.groupBy {
        listOf(it.identityNamespace, it.originWorkspaceId, it.sourceVisitId, it.sourceWorkItemId)
    }.values.map { lineage ->
        val numbered = lineage.filter { it.sourceRevisionNumber != null }
        require(numbered.groupBy { it.sourceRevisionNumber }.values.all { revisionsAtNumber ->
            revisionsAtNumber.map { it.sourceFinalRevisionId }.distinct().size == 1
        }) { "Conflicting source revision numbers in imported work" }
        if (numbered.isEmpty()) {
            val known = lineage.filter { it.chronologyProvenance == "SOURCE_RECORDED_AT" }
            val newestKnown = known.maxWithOrNull(compareBy<EffectiveImportedFinalResult> { it.chronologyEpochMillis }.thenBy { it.sourceFinalRevisionId })
            val uncertain = lineage.filter { it.chronologyProvenance != "SOURCE_RECORDED_AT" }
            require(lineage.size == 1 || (newestKnown != null && uncertain.all { it.chronologyEpochMillis < newestKnown.chronologyEpochMillis } &&
                known.count { it.chronologyEpochMillis == newestKnown.chronologyEpochMillis } == 1)) {
                "Legacy final revisions have ambiguous chronology; review source history before reporting"
            }
            newestKnown ?: lineage.single()
        } else numbered.maxWith(compareBy<EffectiveImportedFinalResult> { it.sourceRevisionNumber!! }
            .thenBy { it.chronologyEpochMillis }.thenBy { it.sourceFinalRevisionId })
    }.filterNot { it.voided }
}

/** Compare common frozen source facts across direct WORK_RESULT and native relay transports. */
internal data class FinalSourceProjectionV2(val publicFacts: String, val privateFacts: String?) {
    companion object {
        const val VERSION = 2

        fun of(value: EffectiveImportedFinalResult): FinalSourceProjectionV2 = projectFinalSourceV2(value)
    }
}

/** Versioned common facts: public relays may omit private facts, while two full copies must agree. */
private fun projectFinalSourceV2(value: EffectiveImportedFinalResult): FinalSourceProjectionV2 {
    val remote = value.remote
    val transferred = value.transferred
    val result = remote?.sourcePayloadJson?.let { FinalSourceSnapshot.record(it, "WORK_RESULT") }
    val native = transferred?.sourcePayloadJson?.let { FinalSourceSnapshot.record(it, "PERFORMED_WORK") }
    val facts = JSONObject()
    if (result != null) {
        val work = result.getJSONObject("workSnapshot")
        facts.put("customer", result.getJSONObject("customerSnapshot"))
            .put("site", result.getJSONObject("siteSnapshot"))
            .put("subject", result.getJSONObject("subjectSnapshot"))
            .put("serviceName", work.getString("serviceName"))
            .put("outcome", result.getString("outcome"))
            .put("workPerformed", work.opt("publicWork") ?: JSONObject.NULL)
            .put("notPerformedReason", work.opt("notPerformedReason") ?: JSONObject.NULL)
            .put("checklist", result.getJSONArray("checklist"))
            .put("findings", result.getJSONArray("findings"))
            .put("parts", result.getJSONArray("parts"))
            .put("recurrence", result.getJSONObject("recurrence"))
            .put("serviceDate", result.getString("serviceDate"))
            .put("visitReference", result.getString("visitReference"))
            .put("recordedAt", result.getString("recordedAt"))
            .put("sourceWorkItemPosition", result.getInt("sourceWorkItemPosition"))
            .put("sourceFinalRevisionNumber", result.getInt("sourceFinalRevisionNumber"))
            .put("supersedes", result.opt("supersedesSourceFinalRevisionId") ?: JSONObject.NULL)
            .put("technicianId", result.getString("technicianId"))
            .put("technicianName", result.getString("technicianName"))
            .put("technicianDesignation", result.opt("technicianDesignation") ?: JSONObject.NULL)
            .put("publicNote", result.opt("publicNote") ?: JSONObject.NULL)
            .put("followUpCaptureState", result.getString("followUpCaptureState"))
            .put("followUps", publicFollowUpFacts(result.getJSONArray("followUps")))
            .put("sourcePhotos", publicPhotoFacts(result.getJSONArray("sourcePhotos")))
            .put("sourceWorkSnapshot", comparableWorkFacts(work))
            .put("privateWorkNote", work.opt("privateInternalNote") ?: JSONObject.NULL)
            .put("privateRevisionNote", result.opt("privateInternalNote") ?: JSONObject.NULL)
            .put("correctionReason", result.opt("correctionReason") ?: JSONObject.NULL)
            .put("sourceCustomerRef", result.opt("sourceCustomerRef") ?: JSONObject.NULL)
            .put("sourceSiteRef", result.opt("sourceSiteRef") ?: JSONObject.NULL)
            .put("sourceEquipmentRef", result.opt("sourceEquipmentRef") ?: JSONObject.NULL)
            .put("assignment", JSONObject().put("issuerId", result.opt("assignmentIssuerId") ?: JSONObject.NULL)
                .put("dispatchVisitId", result.opt("dispatchVisitId") ?: JSONObject.NULL)
                .put("dispatchItemId", result.opt("dispatchItemId") ?: JSONObject.NULL)
                .put("generation", result.opt("assignmentGeneration") ?: JSONObject.NULL)
                .put("materialHash", result.opt("assignmentMaterialHash") ?: JSONObject.NULL))
            .put("privateFollowUps", privateFollowUpFacts(result.getJSONArray("followUps")))
            .put("privateSourcePhotos", privatePhotoFacts(result.getJSONArray("sourcePhotos")))
    } else if (transferred != null) {
        facts.put("customer", JSONObject(transferred.customerSnapshotJson))
            .put("site", JSONObject(transferred.siteSnapshotJson))
            .put("subject", JSONObject(transferred.subjectSnapshotJson))
            .put("serviceName", transferred.serviceName)
            .put("outcome", transferred.outcome)
            .put("workPerformed", transferred.workPerformed ?: JSONObject.NULL)
            .put("notPerformedReason", transferred.notPerformedReason ?: JSONObject.NULL)
            .put("checklist", org.json.JSONArray(transferred.checklistJson))
            .put("findings", org.json.JSONArray(transferred.findingsJson))
            .put("parts", org.json.JSONArray(transferred.partsJson))
            .put("recurrence", JSONObject(transferred.recurrenceJson))
            .put("serviceDate", transferred.serviceDate)
            .put("visitReference", transferred.visitReference)
            .put("recordedAt", native?.getString("recordedAt"))
            .put("sourceWorkItemPosition", native?.getInt("sourceWorkItemPosition"))
            .put("sourceFinalRevisionNumber", native?.getInt("sourceFinalRevisionNumber"))
            .put("supersedes", native?.opt("supersedesSourceFinalRevisionId") ?: JSONObject.NULL)
            .put("technicianId", transferred.technicianId)
            .put("technicianName", transferred.technicianName)
            .put("technicianDesignation", transferred.technicianDesignation ?: JSONObject.NULL)
            .put("publicNote", native?.opt("publicNote") ?: JSONObject.NULL)
            .put("followUpCaptureState", native?.getString("followUpCaptureState"))
            .put("followUps", native?.getJSONArray("followUps")?.let(::publicFollowUpFacts))
            .put("sourcePhotos", native?.getJSONArray("sourcePhotos")?.let(::publicPhotoFacts))
            .put("sourceWorkSnapshot", native?.optJSONObject("sourceWorkSnapshot")?.let(::comparableWorkFacts))
            .put("privateWorkNote", native?.optJSONObject("sourceWorkSnapshot")?.opt("privateInternalNote") ?: JSONObject.NULL)
            .put("privateRevisionNote", native?.opt("finalInternalNote") ?: JSONObject.NULL)
            .put("correctionReason", native?.opt("correctionReason") ?: JSONObject.NULL)
            .put("sourceCustomerRef", native?.opt("sourceCustomerRef") ?: JSONObject.NULL)
            .put("sourceSiteRef", native?.opt("sourceSiteRef") ?: JSONObject.NULL)
            .put("sourceEquipmentRef", native?.opt("sourceEquipmentRef") ?: JSONObject.NULL)
            .put("assignment", native?.opt("assignmentProvenance") ?: JSONObject.NULL)
            .put("privateFollowUps", native?.getJSONArray("followUps")?.let(::privateFollowUpFacts))
            .put("privateSourcePhotos", native?.getJSONArray("sourcePhotos")?.let(::privatePhotoFacts))
    }
    val hasPrivateFacts = result != null || native?.let { it.has("internalNotes") && it.has("finalInternalNote") } == true
    val privateFacts = if (hasPrivateFacts) SourceCanonicalJson.text(JSONObject()
        .put("version", FinalSourceProjectionV2.VERSION)
        .put("workNote", facts.remove("privateWorkNote") ?: JSONObject.NULL)
        .put("revisionNote", facts.remove("privateRevisionNote") ?: JSONObject.NULL)
        .put("followUps", facts.remove("privateFollowUps") ?: JSONObject.NULL)
        .put("photos", facts.remove("privateSourcePhotos") ?: JSONObject.NULL)) else {
        listOf("privateWorkNote", "privateRevisionNote", "privateFollowUps", "privateSourcePhotos").forEach(facts::remove)
        null
    }
    return FinalSourceProjectionV2(SourceCanonicalJson.text(JSONObject()
        .put("version", FinalSourceProjectionV2.VERSION).put("facts", facts)), privateFacts)
}

private fun comparableWorkFacts(work: JSONObject): JSONObject = JSONObject().also { facts ->
    listOf("serviceName", "planId", "planReference", "publicWork", "notPerformedReason",
        "fulfilledObligation", "oldDueDate", "nextDueDate", "nextDueDateCalculated",
        "nextDueOverrideReason", "capturedObligationId").forEach { key ->
        facts.put(key, work.opt(key) ?: JSONObject.NULL)
    }
}

private fun privateFollowUpFacts(records: org.json.JSONArray) = org.json.JSONArray().also { private ->
    for (index in 0 until records.length()) {
        val row = records.getJSONObject(index)
        if (row.has("privatePlanningNote") && !row.isNull("privatePlanningNote")) private.put(
            JSONObject().put("sourceId", row.opt("sourceId") ?: JSONObject.NULL)
                .put("privatePlanningNote", row.get("privatePlanningNote")))
    }
}

private fun privatePhotoFacts(records: org.json.JSONArray) = org.json.JSONArray().also { private ->
    for (index in 0 until records.length()) {
        val row = records.getJSONObject(index)
        if (row.getString("visibility") != "PUBLIC") private.put(row)
    }
}

private fun publicFollowUpFacts(records: org.json.JSONArray) = org.json.JSONArray().also { public ->
    for (index in 0 until records.length()) {
        val row = records.getJSONObject(index)
        public.put(JSONObject().put("sourceId", row.opt("sourceId") ?: JSONObject.NULL)
            .put("type", row.getString("type")).put("title", row.getString("title"))
            .put("dueDate", row.opt("dueDate") ?: JSONObject.NULL).put("state", row.getString("state")))
    }
}

private fun publicPhotoFacts(records: org.json.JSONArray) = org.json.JSONArray().also { public ->
    for (index in 0 until records.length()) {
        val row = records.getJSONObject(index)
        if (row.getString("visibility") == "PUBLIC") public.put(row)
    }
}
