package com.v16studio.v16service.data

import androidx.room.withTransaction
import android.graphics.BitmapFactory
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Coordinator ingestion preserves technician final truth separately from local Working edits. */
class WorkResultImportService(private val database: V16ServiceDatabase, private val attachmentRoot: File) {
    private val dao = database.v16ServiceDao()
    private val dispatch = database.dispatchDao()

    data class ItemPreview(
        val resultId: String, val sourceFinalRevisionId: String, val dispatchVisitId: String, val dispatchItemId: String,
        val status: String, val reason: String?, val committedStatus: String? = null, val recurrenceAppliedNow: Boolean = false,
    )
    data class Preview(val packageId: String, val exporterId: String, val targetIssuerId: String, val items: List<ItemPreview>)
    private data class Prepared(val result: WorkResultPackageCodec.Result, val preview: ItemPreview, val outbox: DispatchOutboxVisitEntity, val item: DispatchOutboxItemEntity, val localWork: WorkItemEntity?, val payloadHash: String, val sourceRecordedAt: String)

    suspend fun preview(bytes: ByteArray): Preview = preflight(bytes).first

    suspend fun import(bytes: ByteArray): Preview = BusinessFileCoordinator.mutex.withLock { importLocked(bytes) }

    private suspend fun importLocked(bytes: ByteArray): Preview {
        val (preview, prepared) = preflight(bytes)
        V16ServicePeerTrustStore(database).requireTrusted(preview.exporterId)
        check(BusinessFileAdoptionJournal.recoverInterrupted(database, attachmentRoot)) {
            "An earlier result photo adoption needs recovery"
        }
        val newPhotos = linkedMapOf<String, ByteArray>()
        prepared.filter { it.preview.status != "ALREADY_RECEIVED" }.forEach { item ->
            item.result.photos.forEach { photo ->
                val path = "remote-results/${stable(preview.exporterId, item.preview.resultId, item.preview.sourceFinalRevisionId, photo.sourcePhotoId)}.jpg"
                val file = ownedFile(path)
                if (file.exists() && dao.remotePhotoReferenceCount(path) == 0) check(file.delete()) {
                    "Unowned result staging file could not be removed"
                }
                if (!file.exists()) require(newPhotos.putIfAbsent(path, photo.bytes) == null) { "Duplicate result photo target" }
            }
        }
        val adoption = BusinessFileAdoptionJournal.begin(database, attachmentRoot, newPhotos)
        try {
            val ownedPhotos = prepared.filter { it.preview.status != "ALREADY_RECEIVED" }.associateWith { item ->
                item.result.photos.map { photo ->
                    val path = "remote-results/${stable(preview.exporterId, item.preview.resultId, item.preview.sourceFinalRevisionId, photo.sourcePhotoId)}.jpg"
                    val file = ownedFile(path)
                    if (file.exists()) {
                        require(file.length() == photo.bytes.size.toLong() && WorkResultPackageCodec.sha256(file.readBytes()) == WorkResultPackageCodec.sha256(photo.bytes)) { "An owned result photo conflicts with this package" }
                    } else {
                        adoption.adopt(path, photo.bytes)
                    }
                    path
                }
            }
            val committedItems = database.withTransaction {
                val outcomes = mutableListOf<ItemPreview>()
                val currentIdentity = dispatch.technicianIdentity()?.technicianId ?: error("Local V16 Service identity is unavailable")
                require(currentIdentity == preview.targetIssuerId) { "This result belongs to another assignment issuer" }
                V16ServicePeerTrustStore(database).requireTrustedInCurrentTransaction(preview.exporterId)
                val now = System.currentTimeMillis()
                prepared.forEach { item ->
                    if (item.preview.status == "ALREADY_RECEIVED") {
                        val receipt = dao.workResultReceipt(preview.exporterId, item.preview.resultId, item.preview.sourceFinalRevisionId)
                            ?: error("Received result disappeared before commit")
                        validateReceivedRevision(preview.exporterId, item, receipt)
                        outcomes += item.preview.copy(reason = receipt.conflictReason, committedStatus = receipt.status)
                        return@forEach
                    }
                    require(dao.workResultReceipt(preview.exporterId, item.preview.resultId, item.preview.sourceFinalRevisionId) == null) { "Result changed while importing" }
                    require(dispatch.outboxVisit(item.preview.dispatchVisitId) == item.outbox &&
                        dispatch.outboxItems(item.preview.dispatchVisitId).firstOrNull { it.dispatchItemId == item.preview.dispatchItemId } == item.item &&
                        authorMayDocument(item.preview.dispatchVisitId, item.preview.dispatchItemId, preview.exporterId)) {
                        "Assignment changed while importing; review the result again"
                    }
                    val json = item.result.value
                    val work = json.getJSONObject("workSnapshot")
                    val resultDbId = stable("remote-final", preview.exporterId, item.preview.resultId, item.preview.sourceFinalRevisionId)
                    val localVisitId = item.outbox.localVisitId
                    val localWorkId = item.item.localWorkItemId
                    dao.insertRemoteFinalResult(RemoteFinalResultEntity(
                        resultDbId, item.preview.resultId, item.preview.sourceFinalRevisionId, item.preview.dispatchVisitId, item.preview.dispatchItemId,
                        localVisitId, localWorkId, json.getString("technicianId"), json.getString("technicianName"), json.optString("technicianDesignation").takeIf { it.isNotBlank() },
                        localVisitId?.let { dao.visit(it)?.customerId }, json.getJSONObject("customerSnapshot").toString(), json.getJSONObject("siteSnapshot").toString(),
                        json.getJSONObject("subjectSnapshot").toString(), json.getString("serviceDate"), json.getString("outcome"),
                        work.optString("publicWork").takeIf { it.isNotBlank() }, work.optString("notPerformedReason").takeIf { it.isNotBlank() },
                        json.getJSONArray("checklist").toString(), json.getJSONArray("findings").toString(), json.getJSONArray("parts").toString(),
                        listOfNotNull(json.optString("privateInternalNote").takeIf { it.isNotBlank() }, work.optString("privateInternalNote").takeIf { it.isNotBlank() }).joinToString("\n").takeIf { it.isNotBlank() }, json.getJSONArray("followUps").toString(),
                        json.getJSONObject("recurrence").toString(), JSONObject().put("packageId", preview.packageId).put("exporterId", preview.exporterId)
                        .put("serviceName", work.optString("serviceName"))
                            .put("recordedAt", item.sourceRecordedAt)
                            .put("assignmentGeneration", json.getInt("assignmentGeneration")).put("assignmentMaterialHash", json.getString("assignmentMaterialHash"))
                            .put("assignmentIssuerId", preview.targetIssuerId).toString(), now,
                        sourcePayloadJson = FinalSourceSnapshot.encode("WORK_RESULT", 1, 1, json),
                    ))
                    dao.insertRemoteResultPhotos(item.result.photos.mapIndexed { index, photo ->
                        RemoteResultPhotoEntity(stable("remote-photo", resultDbId, photo.sourcePhotoId), resultDbId, photo.sourcePhotoId,
                            ownedPhotos.getValue(item)[index], WorkResultPackageCodec.sha256(photo.bytes), photo.bytes.size.toLong(), photo.width, photo.height,
                            "image/jpeg", photo.caption, photo.workItemId, photo.includeInReport, photo.visibility)
                    })
                    var status = item.preview.status
                    var reason = item.preview.reason
                    var recurrenceAt: Long? = null
                    val previousApplied = if (status == "APPLIED") dao.appliedWorkResultLineage(preview.exporterId, item.preview.resultId) else null
                    if (status == "APPLIED" && !work.optBoolean("fulfilledObligation") && previousApplied?.recurrenceAppliedAtEpochMillis != null) {
                        status = "CONFLICT"; reason = "Correction removes an already-applied recurrence; review before changing the plan"
                    }
                    if (status == "APPLIED" && work.optBoolean("fulfilledObligation")) {
                        val recurrence = json.getJSONObject("recurrence")
                        val previousRemote = previousApplied?.let { dao.remoteFinalResult(it.exporterId, it.resultId, it.sourceFinalRevisionId) }
                        val planId = item.localWork?.servicePlanId
                        val obligationId = item.localWork?.capturedObligationId
                        val plan = planId?.let { dao.plan(it) }
                        val obligation = obligationId?.let { dao.obligation(it) }
                        val next = recurrence.optString("nextDueDate").takeIf { it.isNotBlank() }
                        val old = recurrence.optString("oldDueDate").takeIf { it.isNotBlank() }
                        if (previousRemote != null && canonicalJson(JSONObject(previousRemote.recurrenceJson)) == canonicalJson(recurrence)) {
                            // A corrected exact revision supersedes presentation, not the business effect.
                        } else if (previousRemote != null) {
                            status = "CONFLICT"; reason = "Correction changes an already-applied recurrence; review before changing the plan"
                        } else if (plan == null || obligation == null || next == null || old == null || plan.currentObligationId != obligation.id ||
                            obligation.planId != plan.id || obligation.consumedAtEpochMillis != null || obligation.dueDate != old ||
                            item.localWork.planReferenceSnapshot != work.optString("planReference") || plan.state != "ACTIVE") {
                            status = "CONFLICT"; reason = "The captured service obligation is no longer current; recurrence was not advanced"
                        } else {
                            LocalDate.parse(next)
                            val nextId = stable("remote-next-obligation", preview.exporterId, item.preview.resultId, item.preview.sourceFinalRevisionId, plan.id)
                            require(dao.consumeObligation(obligation.id, plan.id, now, item.preview.sourceFinalRevisionId) == 1) { "Obligation changed during import" }
                            dao.insertObligations(listOf(ServiceObligationEntity(nextId, plan.id, obligation.sequence + 1, next, now)))
                            require(dao.advancePlan(plan.id, obligation.id, next, nextId, json.getString("serviceDate"), item.preview.sourceFinalRevisionId) == 1) { "Plan changed during import" }
                            recurrenceAt = now
                        }
                    }
                    dao.insertWorkResultReceipt(WorkResultReceiptEntity(
                        stable("receipt", preview.exporterId, item.preview.resultId, item.preview.sourceFinalRevisionId), preview.packageId,
                        item.preview.resultId, item.preview.sourceFinalRevisionId, preview.targetIssuerId, preview.exporterId,
                        item.preview.dispatchVisitId, item.preview.dispatchItemId, json.getInt("assignmentGeneration"), json.getString("assignmentMaterialHash"),
                        now, item.payloadHash, status, reason, if (status == "APPLIED") now else null, recurrenceAt,
                    ))
                    outcomes += item.preview.copy(status = status, reason = reason, committedStatus = status, recurrenceAppliedNow = recurrenceAt != null)
                }
                prepared.map { it.outbox.dispatchVisitId }.distinct().forEach { visitId ->
                    val outbox = dispatch.outboxVisit(visitId) ?: return@forEach
                    if (outbox.outboxStatus == DispatchOutboxStatus.DISPATCHED) {
                        val items = dispatch.outboxItems(visitId)
                        val localFinalWorkIds = outbox.localVisitId?.let { localId ->
                            dao.finalRecordForVisit(localId)?.let { record -> dao.finalWorkItems(record.currentRevisionId).map { it.sourceWorkItemId }.toSet() }
                        }.orEmpty()
                        val received = dao.appliedWorkResultReceipts(visitId).map { it.dispatchItemId }.toSet()
                        val complete = items.isNotEmpty() && items.all { item -> item.dispatchItemId in received || item.localWorkItemId in localFinalWorkIds }
                        if (complete) {
                            outbox.localVisitId?.let { localId ->
                                val visit = dao.visit(localId) ?: error("Linked canonical Visit is missing")
                                require(visit.state != "CANCELED") { "A canceled Visit cannot be completed by a result" }
                                if (visit.state != "COMPLETED") {
                                    dao.updateVisit(visit.copy(state = "COMPLETED", modifiedAtEpochMillis = now))
                                    dao.releaseVisitClaims(localId)
                                }
                            }
                            dispatch.updateOutboxVisit(outbox.copy(concludedAtEpochMillis = now, modifiedAtEpochMillis = maxOf(now, outbox.modifiedAtEpochMillis + 1)))
                        }
                    }
                }
                outcomes
            }
            adoption.finish()
            return preview.copy(items = committedItems)
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                adoption.reconcileAfterFailure()
            }
            throw failure
        }
    }

    private suspend fun preflight(bytes: ByteArray): Pair<Preview, List<Prepared>> {
        val packageValue = WorkResultPackageCodec.decode(bytes)
        val localId = dispatch.technicianIdentity()?.technicianId ?: error("Local V16 Service identity is unavailable")
        require(packageValue.targetIssuerId == localId) { "This result belongs to another assignment issuer" }
        val prepared = packageValue.results.map { result ->
            val json = result.value
            val visitId = json.getString("dispatchVisitId")
            val itemId = json.getString("dispatchItemId")
            require(json.getString("technicianId") == packageValue.exporterId) { "Result author differs from the exporting V16 Service identity" }
            require(result.photos.all { it.workItemId == itemId }) { "Photo is associated with another work item" }
            result.photos.forEach { photo ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(photo.bytes, 0, photo.bytes.size, bounds)
                require(photo.bytes.size >= 4 && photo.bytes[0] == 0xFF.toByte() && photo.bytes[1] == 0xD8.toByte() &&
                    bounds.outWidth == photo.width && bounds.outHeight == photo.height && maxOf(photo.width, photo.height) <= 1200) { "Invalid result photo derivative" }
            }
            val outbox = dispatch.outboxVisit(visitId) ?: error("Assigned Dispatch Visit is unknown")
            val item = dispatch.outboxItems(visitId).firstOrNull { it.dispatchItemId == itemId } ?: error("Assigned Dispatch work item is unknown")
            require(authorMayDocument(visitId, itemId, packageValue.exporterId)) { "Result author is not assigned to this work item" }
            val resultId = json.getString("resultId")
            val revisionId = json.getString("sourceFinalRevisionId")
            val sourceRecordedAt = Instant.parse(json.getString("recordedAt")).toString()
            val fingerprintObject = JSONObject(json.toString()).apply {
                remove("photos")
                put("photos", JSONArray().apply {
                    result.photos.sortedWith(compareBy({ it.workItemId }, { it.sourcePhotoId })).forEach { photo ->
                        put(JSONObject().put("sourcePhotoId", photo.sourcePhotoId).put("workItemId", photo.workItemId)
                            .put("sha256", WorkResultPackageCodec.sha256(photo.bytes)).put("byteSize", photo.bytes.size)
                            .put("mimeType", "image/jpeg").put("width", photo.width).put("height", photo.height)
                            .put("caption", photo.caption).put("includeInReport", photo.includeInReport).put("visibility", photo.visibility))
                    }
                })
            }
            val content = SourceCanonicalJson.bytes(fingerprintObject)
            val payloadHash = WorkResultPackageCodec.sha256(content)
            val prior = dao.workResultReceipt(packageValue.exporterId, resultId, revisionId)
            if (prior != null) require(prior.payloadSha256 == payloadHash) {
                "The same source revision has conflicting contents"
            }
            val status = when {
                prior != null -> "ALREADY_RECEIVED"
                outbox.outboxStatus == DispatchOutboxStatus.CANCELED || outbox.localVisitId?.let { dao.visit(it)?.state == "CANCELED" } == true -> "STALE"
                outbox.lastExportedMaterialHash != json.getString("assignmentMaterialHash") -> "STALE"
                json.getInt("assignmentGeneration") > (outbox.lastExportedGeneration ?: 0) -> "CONFLICT"
                else -> "APPLIED"
            }
            val reason = when {
                status == "STALE" && outbox.outboxStatus == DispatchOutboxStatus.CANCELED -> "The assigned Visit was canceled"
                status == "STALE" && outbox.localVisitId?.let { dao.visit(it)?.state == "CANCELED" } == true -> "The linked Visit was canceled"
                status == "STALE" -> "Assignment content changed after this result was prepared"
                status == "CONFLICT" -> "Assignment generation is newer than the issuing workspace"
                else -> null
            }
            Prepared(result, ItemPreview(resultId, revisionId, visitId, itemId, status, reason), outbox, item,
                item.localWorkItemId?.let { dao.workItem(it) }, payloadHash, sourceRecordedAt)
        }
        return Preview(packageValue.packageId, packageValue.exporterId, packageValue.targetIssuerId, prepared.map { it.preview }) to prepared
    }

    private fun ownedFile(path: String): File {
        val root = attachmentRoot.canonicalFile
        val file = File(root, path).canonicalFile
        require(file.path.startsWith(root.path + File.separator)) { "Unsafe result photo path" }
        return file
    }

    private suspend fun authorMayDocument(visitId: String, itemId: String, authorId: String): Boolean {
        val assignees = dispatch.outboxItemAssignees(itemId)
        if (assignees.isNotEmpty()) return assignees.any { it.technicianId == authorId }
        return DispatchPackageService(database).outboxParticipants(visitId).first.any { it.technicianId == authorId }
    }

    private suspend fun validateReceivedRevision(authorId: String, item: Prepared, receipt: WorkResultReceiptEntity) {
        val remote = dao.remoteFinalResult(authorId, item.preview.resultId, item.preview.sourceFinalRevisionId)
            ?: error("Received result has no durable source record")
        val source = item.result.value
        val stored = FinalSourceSnapshot.record(remote.sourcePayloadJson, "WORK_RESULT")
        val storedFacts = JSONObject(stored.toString()).apply { remove("photos") }
        val incomingFacts = JSONObject(source.toString()).apply { remove("photos") }
        require(SourceCanonicalJson.text(storedFacts) == SourceCanonicalJson.text(incomingFacts)) {
            "Previously received source revision differs from its durable snapshot"
        }
        val work = source.getJSONObject("workSnapshot")
        fun same(saved: String, incoming: Any) = canonicalJson(JSONObject(saved)) == canonicalJson(incoming)
        fun sameArray(saved: String, incoming: Any) = canonicalJson(JSONArray(saved)) == canonicalJson(incoming)
        require(remote.dispatchVisitId == item.preview.dispatchVisitId && remote.dispatchItemId == item.preview.dispatchItemId &&
            remote.technicianId == authorId && remote.technicianName == source.getString("technicianName") &&
            remote.technicianDesignation == source.optString("technicianDesignation").takeIf { it.isNotBlank() } &&
            remote.serviceDate == source.getString("serviceDate") && remote.outcome == source.getString("outcome") &&
            remote.workPerformed == work.optString("publicWork").takeIf { it.isNotBlank() } &&
            remote.notPerformedReason == work.optString("notPerformedReason").takeIf { it.isNotBlank() } &&
            remote.internalNotes == listOfNotNull(source.optString("privateInternalNote").takeIf { it.isNotBlank() },
                work.optString("privateInternalNote").takeIf { it.isNotBlank() }).joinToString("\n").takeIf { it.isNotBlank() } &&
            same(remote.customerSnapshotJson, source.getJSONObject("customerSnapshot")) &&
            same(remote.siteSnapshotJson, source.getJSONObject("siteSnapshot")) &&
            same(remote.subjectSnapshotJson, source.getJSONObject("subjectSnapshot")) &&
            same(remote.recurrenceJson, source.getJSONObject("recurrence")) &&
            sameArray(remote.checklistJson, source.getJSONArray("checklist")) &&
            sameArray(remote.findingsJson, source.getJSONArray("findings")) &&
            sameArray(remote.partsJson, source.getJSONArray("parts")) &&
            sameArray(remote.followUpsJson, source.getJSONArray("followUps"))) {
            "Previously received result no longer matches its durable source fields"
        }
        val storedPhotos = dao.remoteResultPhotos(remote.id)
        require(storedPhotos.size == item.result.photos.size && item.result.photos.all { photo ->
            storedPhotos.any { storedPhoto -> storedPhoto.sourcePhotoId == photo.sourcePhotoId && storedPhoto.dispatchItemId == photo.workItemId &&
                storedPhoto.sha256 == WorkResultPackageCodec.sha256(photo.bytes) && storedPhoto.byteSize == photo.bytes.size.toLong() &&
                storedPhoto.width == photo.width && storedPhoto.height == photo.height && storedPhoto.mimeType == "image/jpeg" &&
                storedPhoto.caption == photo.caption && storedPhoto.visibility == photo.visibility && storedPhoto.includeInReport == photo.includeInReport }
        }) { "Previously received photo metadata changed" }
        require(receipt.exporterId == authorId && receipt.assignmentIssuerId == source.getString("assignmentIssuerId") &&
            receipt.assignmentGeneration == source.getInt("assignmentGeneration") &&
            receipt.assignmentMaterialHash == source.getString("assignmentMaterialHash") &&
            receipt.payloadSha256 == item.payloadHash) { "Previously received assignment facts changed" }
    }

    private fun stable(vararg parts: String): String = UUID.nameUUIDFromBytes(
        parts.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8),
    ).toString()

    private fun canonicalJson(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { key -> "${JSONObject.quote(key)}:${canonicalJson(value.get(key))}" }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { index -> canonicalJson(value.get(index)) }
        is String -> JSONObject.quote(value)
        null, JSONObject.NULL -> "null"
        else -> value.toString()
    }
}
