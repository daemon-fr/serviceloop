package com.v16studio.v16service.data

import org.json.JSONObject
import java.time.Instant

/** Effective imported truth is selected by immutable source revision and chronology. */
internal fun effectiveRemoteResults(
    results: List<RemoteFinalResultEntity>,
    includePreviousRevisions: Boolean = false,
): List<RemoteFinalResultEntity> {
    val ordered = results.sortedWith(compareBy<RemoteFinalResultEntity> {
        Instant.parse(FinalSourceSnapshot.record(it.sourcePayloadJson, "WORK_RESULT").getString("recordedAt"))
    }.thenBy { it.importedAtEpochMillis }.thenBy { it.id })
    return if (includePreviousRevisions) ordered.filter { it.voidedAtEpochMillis == null }
    else ordered.groupBy { it.technicianId to it.resultId }.values.map { lineage ->
        val numbered = lineage.map { row ->
            row to FinalSourceSnapshot.record(row.sourcePayloadJson, "WORK_RESULT").getInt("sourceFinalRevisionNumber")
        }
        require(numbered.groupBy { it.second }.values.all { values ->
            values.map { it.first.sourceFinalRevisionId }.distinct().size == 1
        }) { "Conflicting source revision numbers in received work" }
        numbered.maxWith(compareBy<Pair<RemoteFinalResultEntity, Int>> { it.second }
            .thenBy { it.first.importedAtEpochMillis }).first
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
    val voided: Boolean = false,
    val sourceRevisionNumber: Int,
)

internal fun effectiveImportedFinalResults(
    remoteResults: List<RemoteFinalResultEntity>,
    transferredResults: List<TransferredFinalResultEntity>,
    includePreviousRevisions: Boolean = false,
): List<EffectiveImportedFinalResult> {
    val remote = remoteResults.map { row ->
        val source = FinalSourceSnapshot.record(row.sourcePayloadJson, "WORK_RESULT")
        EffectiveImportedFinalResult(
            kind = ImportedFinalKind.WORK_RESULT,
            logicalResultId = row.resultId,
            originWorkspaceId = source.getString("originWorkspaceId"),
            sourceVisitId = source.getString("sourceVisitId"),
            sourceWorkItemId = source.getString("sourceWorkItemId"),
            sourceFinalRevisionId = row.sourceFinalRevisionId,
            chronologyEpochMillis = Instant.parse(source.getString("recordedAt")).toEpochMilli(),
            remote = row,
            voided = row.voidedAtEpochMillis != null,
            sourceRevisionNumber = source.getInt("sourceFinalRevisionNumber"),
        )
    }
    val transfer = transferredResults.map { row ->
        val source = FinalSourceSnapshot.record(row.sourcePayloadJson, "PERFORMED_WORK")
        EffectiveImportedFinalResult(
            kind = ImportedFinalKind.DATA_TRANSFER,
            logicalResultId = row.logicalResultId,
            originWorkspaceId = source.getString("originWorkspaceId"),
            sourceVisitId = source.getString("sourceVisitId"),
            sourceWorkItemId = source.getString("sourceWorkItemId"),
            sourceFinalRevisionId = row.sourceFinalRevisionId,
            chronologyEpochMillis = Instant.parse(source.getString("recordedAt")).toEpochMilli(),
            transferred = row,
            voided = row.voidedAtEpochMillis != null,
            sourceRevisionNumber = source.getInt("sourceFinalRevisionNumber"),
        )
    }
    val ordered = (remote + transfer).sortedWith(compareBy<EffectiveImportedFinalResult> { it.chronologyEpochMillis }
        .thenBy { it.originWorkspaceId }.thenBy { it.sourceVisitId }.thenBy { it.sourceWorkItemId }
        .thenBy { it.sourceFinalRevisionId }.thenBy { it.kind.name })
    val revisions = ordered.groupBy {
        listOf(it.originWorkspaceId, it.sourceVisitId, it.sourceWorkItemId, it.sourceFinalRevisionId)
    }.values.map { matching ->
        if (matching.size > 1) {
            val projections = matching.map(FinalSourceProjection::of)
            require(projections.map { it.publicFacts }.distinct().size == 1 &&
                projections.filter { it.privateFacts != null }.map { it.privateFacts }.distinct().size <= 1) {
                "Conflicting representations of the same source revision"
            }
        }
        matching.lastOrNull { it.voided } ?: matching.firstOrNull { it.remote != null } ?: matching.first()
    }
    return if (includePreviousRevisions) revisions.filterNot { it.voided } else revisions.groupBy {
        listOf(it.originWorkspaceId, it.sourceVisitId, it.sourceWorkItemId)
    }.values.map { lineage ->
        require(lineage.groupBy { it.sourceRevisionNumber }.values.all { revisionsAtNumber ->
            revisionsAtNumber.map { it.sourceFinalRevisionId }.distinct().size == 1
        }) { "Conflicting source revision numbers in imported work" }
        lineage.maxWith(compareBy<EffectiveImportedFinalResult> { it.sourceRevisionNumber }
            .thenBy { it.chronologyEpochMillis }.thenBy { it.sourceFinalRevisionId })
    }.filterNot { it.voided }
}

/** Compare common frozen source facts across direct WORK_RESULT and native relay transports. */
internal data class FinalSourceProjection(val publicFacts: String, val privateFacts: String?) {
    companion object {
        const val VERSION = 1

        fun of(value: EffectiveImportedFinalResult): FinalSourceProjection = projectFinalSource(value)
    }
}

/** Versioned common facts: public relays may omit private facts, while two full copies must agree. */
private fun projectFinalSource(value: EffectiveImportedFinalResult): FinalSourceProjection {
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
        .put("version", FinalSourceProjection.VERSION)
        .put("workNote", facts.remove("privateWorkNote") ?: JSONObject.NULL)
        .put("revisionNote", facts.remove("privateRevisionNote") ?: JSONObject.NULL)
        .put("followUps", facts.remove("privateFollowUps") ?: JSONObject.NULL)
        .put("photos", facts.remove("privateSourcePhotos") ?: JSONObject.NULL)) else {
        listOf("privateWorkNote", "privateRevisionNote", "privateFollowUps", "privateSourcePhotos").forEach(facts::remove)
        null
    }
    return FinalSourceProjection(SourceCanonicalJson.text(JSONObject()
        .put("version", FinalSourceProjection.VERSION).put("facts", facts)), privateFacts)
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
