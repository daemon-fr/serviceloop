package com.v16studio.serviceloop.data

import androidx.room.withTransaction
import com.v16studio.serviceloop.domain.PublicChecklistItem
import com.v16studio.serviceloop.domain.PublicPart
import com.v16studio.serviceloop.domain.PublicPhoto
import com.v16studio.serviceloop.domain.PublicReportModel
import com.v16studio.serviceloop.domain.PublicWorkLine
import com.v16studio.serviceloop.domain.ServiceLoopScopeFilter
import com.v16studio.serviceloop.domain.WorkSubjectType
import com.v16studio.serviceloop.report.AggregateReportPdf
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

data class ReportableFinalSource(
    val key: String,
    val kind: String,
    val revisionId: String,
    val visitId: String,
    val customerId: String,
    val siteId: String,
    val equipmentId: String?,
    val serviceDate: String,
    val visitReference: String,
    val technicianName: String,
    val workItemId: String? = null,
    val sourcePosition: Int? = null,
)

data class ReportableVisitSource(
    val key: String,
    val visitId: String,
    val customerId: String,
    val siteId: String,
    val serviceDate: String,
    val visitReference: String,
    val technicians: List<String>,
    val sources: List<ReportableFinalSource>,
) {
    val revisionId: String get() = sources.first().revisionId
}

data class AggregateReportResult(val reportId: String, val relativePath: String, val pageCount: Int)

fun interface AggregateReportWriter { fun render(models: List<PublicReportModel>, businessName: String, businessContact: String, reportId: String, target: File, filesRoot: File): Int }

/** One reportable projection joins current local finals and received remote final truth. */
class AggregateReportService(private val database: ServiceLoopDatabase, private val repository: ServiceLoopRepository, private val filesRoot: File,
    private val writer: AggregateReportWriter = AggregateReportWriter { models, name, contact, id, target, root -> AggregateReportPdf.render(models, name, contact, id, target, root) }) {
    private val dao = database.serviceLoopDao()

    suspend fun reportable(scope: ServiceLoopScopeFilter): List<ReportableVisitSource> {
        val local = dao.allFinalRecords().filterNot { it.voided }.mapNotNull { record ->
            val visit = dao.visit(record.visitId) ?: return@mapNotNull null
            val revision = dao.finalRevision(record.currentRevisionId) ?: return@mapNotNull null
            val work = dao.finalWorkItems(revision.id)
            val equipmentId = work.mapNotNull { it.equipmentId }.distinct().singleOrNull()
            if (!scope.matches(visit.customerId, visit.siteId, equipmentId, LocalDate.parse(revision.actualServiceDate)) &&
                !(scope.equipmentId != null && work.any { it.equipmentId == scope.equipmentId } && scope.copy(equipmentId = null).matches(visit.customerId, visit.siteId, null, LocalDate.parse(revision.actualServiceDate)))) return@mapNotNull null
            ReportableFinalSource("LOCAL:${revision.id}", "LOCAL", revision.id, visit.id, visit.customerId, visit.siteId, equipmentId, revision.actualServiceDate, revision.visitReference, revision.technicianName)
        }
        val remote = effectiveRemoteResults(dao.appliedRemoteFinalResultsIncludingVoids()).mapNotNull { result ->
            val visit = result.localVisitId?.let { dao.visit(it) } ?: return@mapNotNull null
            val equipmentId = result.localWorkItemId?.let { dao.workItem(it)?.equipmentId }
            if (!scope.matches(visit.customerId, visit.siteId, equipmentId, LocalDate.parse(result.serviceDate))) return@mapNotNull null
            ReportableFinalSource("REMOTE:${result.id}", "REMOTE", result.sourceFinalRevisionId, visit.id, visit.customerId, visit.siteId, equipmentId, result.serviceDate, visit.reference, result.technicianName, result.localWorkItemId)
        }
        val transferred = effectiveImportedFinalResults(dao.appliedRemoteFinalResultsIncludingVoids(), dao.allTransferredFinalResults())
            .filter { it.kind == ImportedFinalKind.DATA_TRANSFER }
            .mapNotNull { effective ->
                val row = effective.transferred ?: return@mapNotNull null
                val customer = row.localCustomerId ?: return@mapNotNull null
                val site = row.localSiteId ?: return@mapNotNull null
                if (!scope.matches(customer, site, row.localEquipmentId, LocalDate.parse(row.serviceDate))) return@mapNotNull null
                val visitKey = "TRANSFERRED:${row.originWorkspaceId}:${row.sourceVisitId}"
                ReportableFinalSource("TRANSFERRED:${row.id}", "TRANSFERRED", row.sourceFinalRevisionId, visitKey, customer, site,
                    row.localEquipmentId, row.serviceDate, row.visitReference, row.technicianName, row.sourceWorkItemId,
                    JSONObject(row.provenanceJson).optInt("sourceWorkItemPosition").takeIf { it > 0 })
            }
        return (local + remote + transferred).groupBy { it.visitId }.mapNotNull { (visitId, sources) ->
            val visit = dao.visit(visitId)
            if (visit != null && visit.state != "COMPLETED") return@mapNotNull null
            val first = sources.firstOrNull() ?: return@mapNotNull null
            val workOrder = if (visit != null) dao.visitWorkItems(visitId).mapIndexed { index, work -> work.id to index }.toMap() else emptyMap()
            val ordered = sources.distinctBy { it.key }.sortedWith(compareBy<ReportableFinalSource> { it.sourcePosition ?: it.workItemId?.let { workOrder[it] } ?: Int.MAX_VALUE }.thenBy { it.kind }.thenBy { it.key })
            ReportableVisitSource(if (visit != null) "VISIT:$visitId" else visitId, visitId, first.customerId, first.siteId,
                visit?.actualServiceDate ?: first.serviceDate, visit?.reference ?: first.visitReference,
                ordered.map { it.technicianName }.distinct(), ordered)
        }.sortedWith(compareBy<ReportableVisitSource> { it.serviceDate }.thenBy { it.visitReference }.thenBy { it.key })
    }

    suspend fun generate(scope: ServiceLoopScopeFilter, selectedKeys: List<String>): AggregateReportResult {
        val customerId = requireNotNull(scope.customerId) { "Choose one Customer" }
        require(selectedKeys.isNotEmpty() && selectedKeys.size <= 100 && selectedKeys.distinct().size == selectedKeys.size) { "Choose up to 100 Visits" }
        val candidates = reportable(scope).associateBy { it.key }
        val selected = selectedKeys.map { candidates[it] ?: error("A selected Visit changed; review the selection") }
        require(selected.all { it.customerId == customerId }) { "Aggregate reports cannot mix Customers" }
        val customer = dao.customer(customerId) ?: error("Customer is missing")
        val business = dao.businessProfile() ?: error("Business identity is missing")
        val models = selected.map { visitSource ->
            val parts = visitSource.sources.map { source -> when (source.kind) {
                "LOCAL" -> {
                    val record = dao.finalRecordForVisit(source.visitId) ?: error("Final record is missing")
                    repository.finalRecordRevision(record.id, source.revisionId)?.public ?: error("Final revision is missing")
                }
                "REMOTE" -> remoteModel(source)
                "TRANSFERRED" -> transferredModel(source)
                else -> error("Unknown final source")
            } }
            val equipmentReference = scope.equipmentId?.let { dao.equipment(it)?.reference }
            val lines = parts.flatMap { model -> model.lines.map { it.copy(documentingTechnicianName = model.technicianName) } }
                .filter { equipmentReference == null || it.equipmentReference == equipmentReference }
                .mapIndexed { index, line -> line.copy(position = index + 1) }
            parts.first().copy(recordId = visitSource.visitId, visitReference = visitSource.visitReference,
                technicianName = visitSource.technicians.joinToString(", "), lines = lines)
        }
        val businessContact = listOfNotNull(business.phone, business.email, business.postalAddress).filter { it.isNotBlank() }.joinToString(" · ")
        val frozen = models.map { it.copy(businessName = business.businessName, businessContact = businessContact) }
        frozen.flatMap { it.lines }.flatMap { it.photos }.forEach { photo ->
            val file = ownedFile(photo.relativePath)
            require(file.length() == photo.byteSize && WorkResultPackageCodec.sha256(file.readBytes()) == photo.sha256) { "A selected customer-report photo is missing or changed" }
        }
        val now = System.currentTimeMillis()
        val reportId = UUID.randomUUID().toString()
        val renditionId = UUID.randomUUID().toString()
        val relative = "aggregate-reports/$reportId/$renditionId.pdf"
        val generating = AggregateReportRenditionEntity(renditionId, reportId, relative, null, null, null, now, "GENERATING", null)
        database.withTransaction {
            dao.insertAggregateReport(AggregateReportEntity(reportId, customerId,
                JSONObject().put("reference", customer.reference).put("name", customer.name).toString(), scope.siteId, scope.equipmentId,
                scope.fromDate?.toString(), scope.toDate?.toString(), JSONObject().put("name", business.businessName).put("contact", businessContact).toString(), now, "ACTIVE"))
            dao.insertAggregateSources(selected.flatMap { it.sources }.mapIndexed { index, source -> AggregateReportSourceEntity(reportId, index + 1, source.revisionId, source.kind, source.visitId, source.key.substringAfter(':')) })
            dao.insertAggregateRendition(generating)
        }
        val target = File(filesRoot, relative)
        require(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
        val temp = File(target.parentFile, "$renditionId.tmp")
        try {
            val count = writer.render(frozen, business.businessName, businessContact, reportId, temp, filesRoot)
            require(temp.length() > 0) { "Aggregate report is empty" }
            check(temp.renameTo(target)) { "Could not store aggregate report" }
            val bytes = target.readBytes()
            dao.updateAggregateRendition(generating.copy(sha256 = WorkResultPackageCodec.sha256(bytes), byteSize = bytes.size.toLong(), pageCount = count, status = "READY"))
            return AggregateReportResult(reportId, relative, count)
        } catch (failure: Throwable) {
            temp.delete()
            dao.updateAggregateRendition(generating.copy(status = "FAILED", failureReason = failure.message?.take(500)))
            throw failure
        }
    }

    private suspend fun remoteModel(source: ReportableFinalSource): PublicReportModel {
        val result = dao.reportableRemoteFinalResults().firstOrNull { "REMOTE:${it.id}" == source.key } ?: error("Remote result changed")
        val customer = JSONObject(result.customerSnapshotJson)
        val site = JSONObject(result.siteSnapshotJson)
        val subject = JSONObject(result.subjectSnapshotJson)
        val recurrence = JSONObject(result.recurrenceJson)
        val checklist = org.json.JSONArray(result.checklistJson)
        val parts = org.json.JSONArray(result.partsJson)
        val photos = dao.remoteResultPhotos(result.id).filter { it.includeInReport && it.visibility == "PUBLIC" }.map { PublicPhoto(it.relativePath,it.sha256,it.byteSize,it.mimeType,it.caption) }
        val line = PublicWorkLine(1, subject.optString("equipmentName").takeIf { it.isNotBlank() }, subject.optString("equipmentReference").takeIf { it.isNotBlank() }, null,
            JSONObject(result.provenanceJson).optString("serviceName").ifBlank { "Assigned service" }, result.outcome, result.workPerformed, result.notPerformedReason,
            recurrence.optBoolean("fulfilledObligation"), recurrence.optString("oldDueDate").takeIf { it.isNotBlank() }, recurrence.optString("nextDueDate").takeIf { it.isNotBlank() },
            (0 until checklist.length()).map { index -> val item = checklist.getJSONObject(index); PublicChecklistItem(item.optInt("position",index+1),item.optString("label"),item.optString("responseType"),item.optString("unit").takeIf { it.isNotBlank() },item.optBoolean("required"),item.optString("disposition"),item.optString("textValue").ifBlank { item.optString("numberValue") }.takeIf { it.isNotBlank() },item.optString("reason").takeIf { it.isNotBlank() }) },
            parts = (0 until parts.length()).map { index -> val part = parts.getJSONObject(index); PublicPart(part.optString("description"),part.optString("quantity"),part.optString("unit")) },
            photos = photos, subjectType = WorkSubjectType.fromCode(subject.optString("type", "EQUIPMENT")), equipmentDescription = subject.optString("equipmentDescription").takeIf { it.isNotBlank() })
        return PublicReportModel(result.id, result.sourceFinalRevisionId, 1, source.visitReference, result.serviceDate, result.importedAtEpochMillis,
            "", result.technicianName, "", customer.optString("name"), site.optString("name"), site.optString("address").takeIf { it.isNotBlank() }, listOf(line),
            customerReference = customer.optString("reference").takeIf { it.isNotBlank() }, siteReference = site.optString("reference").takeIf { it.isNotBlank() }, technicianDesignation = result.technicianDesignation)
    }

    private suspend fun transferredModel(source: ReportableFinalSource): PublicReportModel {
        val result = dao.allTransferredFinalResults().firstOrNull { "TRANSFERRED:${it.id}" == source.key } ?: error("Transferred result changed")
        val customer = JSONObject(result.customerSnapshotJson)
        val site = JSONObject(result.siteSnapshotJson)
        val subject = JSONObject(result.subjectSnapshotJson)
        val recurrence = JSONObject(result.recurrenceJson)
        val checklist = org.json.JSONArray(result.checklistJson)
        val parts = org.json.JSONArray(result.partsJson)
        val photos = dao.allTransferredEvidence().filter { it.transferredFinalResultId == result.id && it.includedInCustomerReport && it.visibility == "PUBLIC" }
            .map { PublicPhoto(it.relativePath, it.sha256, it.byteSize, it.mimeType, it.caption) }
        val line = PublicWorkLine(1, subject.optString("equipmentName").takeIf { it.isNotBlank() }, subject.optString("equipmentReference").takeIf { it.isNotBlank() },
            subject.optString("equipmentIdentifier").takeIf { it.isNotBlank() }, result.serviceName, result.outcome, result.workPerformed, result.notPerformedReason,
            recurrence.optBoolean("fulfilledObligation"), recurrence.optString("oldDueDate").takeIf { it.isNotBlank() }, recurrence.optString("nextDueDate").takeIf { it.isNotBlank() },
            (0 until checklist.length()).map { index -> val item = checklist.getJSONObject(index); PublicChecklistItem(item.optInt("position", index + 1), item.optString("label"), item.optString("responseType"), item.optString("unit").takeIf { it.isNotBlank() }, item.optBoolean("required"), item.optString("disposition"), item.optString("textValue").ifBlank { item.optString("numberValue") }.takeIf { it.isNotBlank() }, item.optString("reason").takeIf { it.isNotBlank() }) },
            parts = (0 until parts.length()).map { index -> val part = parts.getJSONObject(index); PublicPart(part.optString("description"), part.optString("quantity"), part.optString("unit")) },
            photos = photos, subjectType = WorkSubjectType.fromCode(subject.optString("type", "EQUIPMENT")), equipmentDescription = subject.optString("equipmentDescription").takeIf { it.isNotBlank() })
        val provenance = JSONObject(result.provenanceJson)
        return PublicReportModel(result.id, result.sourceFinalRevisionId, 1, result.visitReference, result.serviceDate, runCatching { java.time.Instant.parse(provenance.optString("recordedAt")).toEpochMilli() }.getOrDefault(result.importedAtEpochMillis),
            "", result.technicianName, "", customer.optString("name"), site.optString("name"), site.optString("address").takeIf { it.isNotBlank() }, listOf(line),
            customerReference = customer.optString("reference").takeIf { it.isNotBlank() }, siteReference = site.optString("reference").takeIf { it.isNotBlank() }, technicianDesignation = result.technicianDesignation)
    }

    private fun ownedFile(path: String): File {
        val root = filesRoot.canonicalFile
        val file = File(root, path).canonicalFile
        require(file.path.startsWith(root.path + File.separator) && file.isFile) { "Photo is missing or outside ServiceLoop storage" }
        return file
    }
}
