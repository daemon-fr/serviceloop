package com.v16studio.serviceloop.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

/** Domain export of immutable finalized technician work. The caller owns Android file handoff. */
class WorkResultExchangeService(private val database: ServiceLoopDatabase, private val attachmentRoot: File) {
    private val dao = database.serviceLoopDao()
    private val dispatch = database.dispatchDao()

    suspend fun exportFinalRevisions(revisionIds: List<String>): ByteArray {
        require(revisionIds.isNotEmpty() && revisionIds.size <= WorkResultPackageCodec.MAX_RESULTS)
        val exporterId = ServiceLoopPeerTrustStore(database).localIdentity().technicianId
        val results = revisionIds.distinct().flatMap { revisionId ->
            val revision = dao.finalRevision(revisionId) ?: error("Final revision missing")
            val record = dao.finalRecord(revision.recordId) ?: error("Final record missing")
            require(!record.voided) { "A voided result cannot be shared as current work" }
            val provenance = dispatch.finalDispatchVisit(revisionId) ?: error("Only assigned finalized work can be returned")
            require(provenance.documentingTechnicianId == exporterId) { "Only this ServiceLoop identity's finalized work can be returned" }
            val issuer = provenance.assignmentIssuerId ?: dispatch.visitBinding(provenance.dispatchVisitId)?.assignmentIssuerId
            require(!issuer.isNullOrBlank()) { "The assignment issuer is unknown; this result needs review" }
            val materialHash = provenance.assignmentMaterialHash ?: dispatch.visitBinding(provenance.dispatchVisitId)?.appliedMaterialHash
            require(!materialHash.isNullOrBlank()) { "Assignment material provenance is missing" }
            val visit = dao.visit(record.visitId) ?: error("Final Visit missing")
            val dispatchItems = dispatch.finalDispatchItems(revisionId).associateBy { it.finalWorkItemId }
            dao.finalWorkItems(revisionId).mapNotNull { item ->
                val itemProvenance = dispatchItems[item.id] ?: return@mapNotNull null
                val value = JSONObject()
                    .put("resultId", stable("work-result", issuer, provenance.dispatchVisitId, itemProvenance.dispatchItemId))
                    .put("sourceFinalRevisionId", revision.id)
                    .put("dispatchVisitId", provenance.dispatchVisitId)
                    .put("dispatchItemId", itemProvenance.dispatchItemId)
                    .put("assignmentGeneration", provenance.generation)
                    .put("assignmentMaterialHash", materialHash)
                    .put("assignmentIssuerId", issuer)
                    .put("technicianId", provenance.documentingTechnicianId)
                    .put("technicianName", provenance.documentingTechnicianName)
                    .put("technicianDesignation", revision.technicianDesignation)
                    .put("serviceDate", revision.actualServiceDate)
                    .put("outcome", item.outcome)
                    .put("customerSnapshot", JSONObject().put("name", revision.customerName).put("reference", revision.customerReference))
                    .put("siteSnapshot", JSONObject().put("name", revision.siteName).put("reference", revision.siteReference).put("address", revision.siteAddress))
                    .put("subjectSnapshot", JSONObject().put("type", item.subjectType).put("equipmentName", item.equipmentName).put("equipmentReference", item.equipmentReference).put("equipmentDescription", item.equipmentDescription))
                    .put("workSnapshot", JSONObject().put("serviceName", item.serviceName).put("planId", item.planId).put("planReference", item.planReference)
                        .put("publicWork", item.publicWorkNote).put("notPerformedReason", item.notPerformedReason).put("fulfilledObligation", item.fulfilledObligation)
                        .put("oldDueDate", item.oldDueDate).put("nextDueDate", item.nextDueDate).put("nextDueDateCalculated", item.nextDueDateCalculated)
                        .put("nextDueOverrideReason", item.nextDueOverrideReason).put("capturedObligationId", item.capturedObligationId)
                        .put("privateInternalNote", item.privateInternalNote))
                    .put("checklist", JSONArray().also { array -> dao.finalChecklistItems(item.id).forEach { q -> array.put(JSONObject()
                        .put("position", q.position).put("label", q.label).put("responseType", q.responseType).put("unit", q.unit)
                        .put("required", q.required).put("disposition", q.disposition).put("textValue", q.textValue).put("numberValue", q.numberValue).put("reason", q.reason)) } })
                    .put("findings", JSONArray().also { array -> dao.finalChecklistItems(item.id).filter { it.disposition == "ISSUE_FOUND" }.forEach { finding -> array.put(JSONObject().put("position", finding.position).put("label", finding.label).put("description", finding.reason)) } })
                    .put("parts", JSONArray().also { array -> dao.finalParts(item.id).forEach { part -> array.put(JSONObject().put("position", part.position).put("description", part.description).put("quantity", part.quantity).put("unit", part.unit)) } })
                    .put("followUpCaptureState", if (item.followUpsSnapshotJson == null) "UNAVAILABLE_LEGACY" else "CAPTURED_AT_REVISION")
                    .put("followUps", item.followUpsSnapshotJson?.let(FinalFollowUpSnapshot::records) ?: JSONArray())
                    .put("recurrence", JSONObject().put("planId", item.planId).put("capturedObligationId", item.capturedObligationId)
                        .put("oldDueDate", item.oldDueDate).put("nextDueDate", item.nextDueDate).put("fulfilledObligation", item.fulfilledObligation))
                    .put("visitReference", revision.visitReference).put("sourceVisitId", visit.id)
                    .put("recordedAt", Instant.ofEpochMilli(revision.recordedAtEpochMillis).toString())
                    .put("privateInternalNote", revision.privateInternalNote)
                val finalPhotos = dao.finalPhotos(item.id)
                val photos = finalPhotos.map { finalPhoto ->
                        val bytes = runCatching { readOwnedPhoto(finalPhoto.storedRelativePath, finalPhoto.sha256, finalPhoto.byteSize) }.getOrNull()
                            ?: run {
                                val retained = dao.retainedImage("ATTACHMENT", finalPhoto.sourceAttachmentId)
                                    ?: dao.retainedImage("FINAL_PHOTO", finalPhoto.id)
                                    ?: dao.retainedImageByOriginalPath(finalPhoto.storedRelativePath)
                                    ?: error("Final photo has no retained copy")
                                readOwnedPhoto(retained.derivativeRelativePath, retained.derivativeSha256, retained.derivativeByteSize)
                            }
                        val derivative = AppOwnedImageNormalizer.workResultDerivative(bytes)
                        WorkResultPackageCodec.Photo(finalPhoto.sourceAttachmentId, derivative.bytes, finalPhoto.caption,
                            finalPhoto.includedInCustomerReport, finalPhoto.visibility,
                            itemProvenance.dispatchItemId, derivative.width, derivative.height)
                    }
                WorkResultPackageCodec.Result(value, photos)
            }
        }
        require(results.isNotEmpty()) { "No assigned final work was selected" }
        val issuers = results.map { it.value.getString("assignmentIssuerId") }.toSet()
        require(issuers.size == 1) { "Select results from one assignment issuer" }
        ServiceLoopPeerTrustStore(database).requireTrusted(issuers.single())
        return WorkResultPackageCodec.encode(WorkResultPackageCodec.Package(UUID.randomUUID().toString(), exporterId, issuers.single(), Instant.now().toString(), results))
    }

    private fun readOwnedPhoto(path: String, hash: String, size: Long): ByteArray {
        val root = attachmentRoot.canonicalFile
        val file = File(root, path).canonicalFile
        require(file.path.startsWith(root.path + File.separator) && file.isFile && file.length() == size && size in 1..AppOwnedImageNormalizer.MAX_SOURCE_BYTES.toLong()) { "Final photo is missing or outside app storage" }
        return file.readBytes().also { require(WorkResultPackageCodec.sha256(it) == hash) { "Final photo changed after finalization" } }
    }

    private fun stable(vararg parts: String): String = UUID.nameUUIDFromBytes(parts.joinToString("|").toByteArray(Charsets.UTF_8)).toString()
}
