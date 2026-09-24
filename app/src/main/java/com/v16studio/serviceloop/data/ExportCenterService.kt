package com.v16studio.serviceloop.data

import com.v16studio.serviceloop.domain.ServiceLoopScopeFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFamily(val title: String) {
    CUSTOMERS("Customers"), CONTACTS("Customer contacts"), SITES("Sites"), EQUIPMENT("Equipment"), PLANS("Service Plans"),
    VISITS("Visits"), SERVICE_RECORDS("Service records"), CHECKLIST("Checklist and findings"), PARTS("Parts"),
    FOLLOW_UPS("Follow-ups"), CONTACT_NOTES("Contact notes"), HISTORY("History and changes"),
    PHOTO_METADATA("Photo metadata"), IMAGE_FILES("Image files"), TEMPLATES("Inspection templates"),
}

enum class ExportPreset(val title: String, val families: Set<ExportFamily>) {
    CUSTOMER_DATA("Customer Data", setOf(ExportFamily.CUSTOMERS, ExportFamily.CONTACTS, ExportFamily.SITES, ExportFamily.EQUIPMENT, ExportFamily.PLANS)),
    WORK_PERFORMED("Work Performed", setOf(ExportFamily.VISITS, ExportFamily.SERVICE_RECORDS, ExportFamily.CHECKLIST, ExportFamily.PARTS, ExportFamily.FOLLOW_UPS, ExportFamily.CONTACT_NOTES, ExportFamily.HISTORY)),
    IMAGE_ARCHIVE("Image Archive", setOf(ExportFamily.PHOTO_METADATA, ExportFamily.IMAGE_FILES)),
    CUSTOM("Custom", emptySet()),
}

data class ExportCenterSelection(
    val scope: ServiceLoopScopeFilter = ServiceLoopScopeFilter(),
    val families: Set<ExportFamily> = ExportPreset.CUSTOMER_DATA.families,
    val includePrivate: Boolean = false,
    val includeInactive: Boolean = false,
    val includePreviousRevisions: Boolean = false,
)

/** Readable, scoped CSV and image ZIP. This is not a database backup. */
class ExportCenterService(private val database: ServiceLoopDatabase, private val filesRoot: File) {
    private val dao = database.serviceLoopDao()

    suspend fun export(selection: ExportCenterSelection): ByteArray {
        require(selection.families.isNotEmpty()) { "Choose at least one content family" }
        val scope = selection.scope
        val businessZone = dao.businessProfile()?.zoneId?.let(ZoneId::of) ?: ZoneId.systemDefault()
        fun includesDate(date: LocalDate?) =
            (scope.fromDate == null || date != null && !date.isBefore(scope.fromDate)) &&
                (scope.toDate == null || date != null && !date.isAfter(scope.toDate))
        fun sourceDate(value: String?): LocalDate? {
            if (scope.fromDate == null && scope.toDate == null) return null
            return value?.takeIf(String::isNotBlank)?.let { text ->
                runCatching { LocalDate.parse(text) }.getOrElse {
                    runCatching { Instant.parse(text) }.getOrElse { OffsetDateTime.parse(text).toInstant() }
                        .atZone(businessZone).toLocalDate()
                }
            }
        }
        val allCustomers = dao.allCustomers()
        val customers = allCustomers.filter { (scope.customerId == null || it.id == scope.customerId) && (selection.includeInactive || it.state == "ACTIVE") }
        require(scope.customerId == null || customers.size == 1) { "Selected customer is unavailable" }
        val customerIds = customers.map { it.id }.toSet()
        val sites = dao.allSites().filter { it.customerId in customerIds && (scope.siteId == null || it.id == scope.siteId) && (selection.includeInactive || it.state == "ACTIVE") }
        require(scope.siteId == null || sites.size == 1) { "Selected site is unavailable" }
        val siteIds = sites.map { it.id }.toSet()
        val equipment = dao.allEquipment().filter { it.siteId in siteIds && (scope.equipmentId == null || it.id == scope.equipmentId) && (selection.includeInactive || it.state == "ACTIVE") }
        require(scope.equipmentId == null || equipment.size == 1) { "Selected equipment is unavailable" }
        val equipmentIds = equipment.map { it.id }.toSet()
        val visits = dao.allVisits().filter { visit -> visit.siteId in siteIds && (scope.equipmentId == null || dao.visitWorkItems(visit.id).any { it.equipmentId == scope.equipmentId }) &&
            scope.matches(visit.customerId, visit.siteId, scope.equipmentId, LocalDate.parse(visit.actualServiceDate)) }
        val visitIds = visits.map { it.id }.toSet()
        val finalRecords = dao.allFinalRecords().filter { it.visitId in visitIds && !it.voided }
        val revisions = finalRecords.flatMap { record ->
            (if (selection.includePreviousRevisions) dao.finalRevisions(record.id) else listOfNotNull(dao.finalRevision(record.currentRevisionId))).map { record to it }
        }
        val finalWork = revisions.flatMap { (_, revision) -> dao.finalWorkItems(revision.id).filter { scope.equipmentId == null || it.equipmentId == scope.equipmentId }.map { revision to it } }
        val effectiveImports = effectiveImportedFinalResults(dao.appliedRemoteFinalResultsIncludingVoids(), dao.allTransferredFinalResults(), selection.includePreviousRevisions)
        val remote = effectiveImports.filter { it.kind == ImportedFinalKind.WORK_RESULT }.mapNotNull { it.remote }.filter { result -> result.customerId in customerIds && result.localVisitId in visitIds &&
            (scope.equipmentId == null || result.localWorkItemId?.let { dao.workItem(it)?.equipmentId } == scope.equipmentId) &&
            scope.matches(result.customerId ?: "", result.localVisitId?.let { dao.visit(it)?.siteId }, result.localWorkItemId?.let { dao.workItem(it)?.equipmentId }, LocalDate.parse(result.serviceDate)) }
        val transferred = effectiveImports.filter { it.kind == ImportedFinalKind.DATA_TRANSFER }.mapNotNull { it.transferred }.filter { result -> result.localCustomerId in customerIds && result.localSiteId in siteIds &&
            (scope.equipmentId == null || result.localEquipmentId == scope.equipmentId) &&
            scope.matches(result.localCustomerId.orEmpty(), result.localSiteId, result.localEquipmentId, LocalDate.parse(result.serviceDate)) }
        val transferredVisits = transferred.distinctBy { it.originWorkspaceId to it.sourceVisitId }
        val performedVisitIds = (finalRecords.filterNot { it.voided }.map { it.visitId } + remote.mapNotNull { it.localVisitId }).toSet()
        val performedVisits = visits.filter { it.state == "COMPLETED" && it.id in performedVisitIds }
        val entries = linkedMapOf<String, ByteArray>()
        fun add(family: ExportFamily, name: String, headers: List<String>, rows: List<List<String>>) {
            if (family in selection.families) entries[name] = csv(headers, rows)
        }
        add(ExportFamily.CUSTOMERS, "customers.csv", listOf("id","reference","name","type","state","private_note"), customers.map { listOf(it.id,it.reference,it.name,it.customerType,it.state,if(selection.includePrivate) it.privateNote.orEmpty() else "") })
        add(ExportFamily.CONTACTS, "contacts.csv", listOf("id","customer_id","position","person","channel","value","notes"), customers.flatMap { customer -> dao.customerContacts(customer.id).map { listOf(it.id,it.customerId,it.position.toString(),it.personName.orEmpty(),it.channel,it.value,if(selection.includePrivate) it.notes.orEmpty() else "") } })
        add(ExportFamily.SITES, "sites.csv", listOf("id","customer_id","reference","name","address","state","private_access_notes"), sites.map { listOf(it.id,it.customerId,it.reference,it.name,it.address.orEmpty(),it.state,if(selection.includePrivate) it.privateAccessNotes.orEmpty() else "") })
        add(ExportFamily.EQUIPMENT, "equipment.csv", listOf("id","site_id","reference","name","make","model","serial","state","private_notes"), equipment.map { listOf(it.id,it.siteId,it.reference,it.name,it.make.orEmpty(),it.model.orEmpty(),it.serialNumber.orEmpty(),it.state,if(selection.includePrivate) it.privateNotes.orEmpty() else "") })
        add(ExportFamily.PLANS, "service_plans.csv", listOf("id","equipment_id","reference","name","interval_count","interval_unit","current_due","state"), dao.allPlans().filter { it.equipmentId in equipmentIds && (selection.includeInactive || it.state == "ACTIVE") }.map { listOf(it.id,it.equipmentId,it.reference,it.name,it.intervalCount.toString(),it.intervalUnit,it.currentDueDate,it.state) })
        add(ExportFamily.VISITS, "visits.csv", listOf("id","reference","customer_id","site_id","service_date","state"), performedVisits.map { listOf(it.id,it.reference,it.customerId,it.siteId,it.actualServiceDate,it.state) } + transferredVisits.map { listOf("${it.originWorkspaceId}:${it.sourceVisitId}",it.visitReference,it.localCustomerId.orEmpty(),it.localSiteId.orEmpty(),it.serviceDate,"TRANSFERRED_HISTORY") })
        add(ExportFamily.SERVICE_RECORDS, "service_records.csv", listOf("revision_id","visit_id","service_date","technician","service","outcome","public_work","next_due","private_note","source"),
            finalWork.map { (revision,work) -> listOf(revision.id,dao.finalRecord(revision.recordId)?.visitId.orEmpty(),revision.actualServiceDate,revision.technicianName,work.serviceName,work.outcome,work.publicWorkNote.orEmpty(),work.nextDueDate.orEmpty(),if(selection.includePrivate) work.privateInternalNote.orEmpty() else "","LOCAL") } +
                remote.map { result -> listOf(result.sourceFinalRevisionId,result.localVisitId.orEmpty(),result.serviceDate,result.technicianName,org.json.JSONObject(result.provenanceJson).optString("serviceName"),result.outcome,result.workPerformed.orEmpty(),org.json.JSONObject(result.recurrenceJson).optString("nextDueDate"),if(selection.includePrivate) result.internalNotes.orEmpty() else "","WORK_RESULT") } +
                transferred.map { result -> listOf(result.sourceFinalRevisionId,"${result.originWorkspaceId}:${result.sourceVisitId}",result.serviceDate,result.technicianName,result.serviceName,result.outcome,result.workPerformed.orEmpty(),org.json.JSONObject(result.recurrenceJson).optString("nextDueDate"),if(selection.includePrivate) result.internalNotes.orEmpty() else "","DATA_TRANSFER") })
        add(ExportFamily.CHECKLIST, "checklist.csv", listOf("revision_id","work_id","position","question","response_type","disposition","answer","reason","source"),
            finalWork.flatMap { (revision,work) -> dao.finalChecklistItems(work.id).map { listOf(revision.id,work.id,it.position.toString(),it.label,it.responseType,it.disposition,it.textValue ?: it.numberValue.orEmpty(),it.reason.orEmpty(),"LOCAL") } } +
                remote.flatMap { result -> val values = org.json.JSONArray(result.checklistJson); (0 until values.length()).map { index -> val item = values.getJSONObject(index); listOf(result.sourceFinalRevisionId,result.dispatchItemId,item.optString("position"),item.optString("label"),item.optString("responseType"),item.optString("disposition"),item.optString("textValue").ifBlank { item.optString("numberValue") },item.optString("reason"),"WORK_RESULT") } } +
                transferred.flatMap { result -> val values = org.json.JSONArray(result.checklistJson); (0 until values.length()).map { index -> val item = values.getJSONObject(index); listOf(result.sourceFinalRevisionId,result.sourceWorkItemId,item.optString("position"),item.optString("label"),item.optString("responseType"),item.optString("disposition"),item.optString("textValue").ifBlank { item.optString("numberValue") },item.optString("reason"),"DATA_TRANSFER") } })
        add(ExportFamily.PARTS, "parts.csv", listOf("revision_id","work_id","description","quantity","unit","source"),
            finalWork.flatMap { (revision,work) -> dao.finalParts(work.id).map { listOf(revision.id,work.id,it.description,it.quantity,it.unit,"LOCAL") } } +
                remote.flatMap { result -> val values = org.json.JSONArray(result.partsJson); (0 until values.length()).map { index -> val part = values.getJSONObject(index); listOf(result.sourceFinalRevisionId,result.dispatchItemId,part.optString("description"),part.optString("quantity"),part.optString("unit"),"WORK_RESULT") } } +
                transferred.flatMap { result -> val values = org.json.JSONArray(result.partsJson); (0 until values.length()).map { index -> val part = values.getJSONObject(index); listOf(result.sourceFinalRevisionId,result.sourceWorkItemId,part.optString("description"),part.optString("quantity"),part.optString("unit"),"DATA_TRANSFER") } })
        val receivedFollowUps = dao.allTransferredHistoryEntries().filter { it.family == "FOLLOW_UPS" && it.localCustomerId in customerIds && (scope.siteId == null || it.localSiteId == scope.siteId) && (scope.equipmentId == null || it.localEquipmentId == scope.equipmentId) && includesDate(sourceDate(JSONObject(it.payloadJson).optString("dueDate"))) }.map { row ->
            val value = org.json.JSONObject(row.payloadJson)
            listOf("${row.originWorkspaceId}:${row.sourceEntityId}", row.localCustomerId.orEmpty(), row.localSiteId.orEmpty(), row.localEquipmentId.orEmpty(), value.optString("title"), value.optString("dueDate"), value.optString("state"), if (selection.includePrivate) value.optString("privatePlanningNote") else "")
        }
        add(ExportFamily.FOLLOW_UPS, "followups.csv", listOf("id","customer_id","site_id","equipment_id","title","due_date","state","private_note"), dao.allFollowUps().filter { it.customerId in customerIds && (scope.siteId == null || it.siteId == scope.siteId) && (scope.equipmentId == null || it.equipmentId == scope.equipmentId) && includesDate(sourceDate(it.dueDate)) }.map { listOf(it.id,it.customerId,it.siteId.orEmpty(),it.equipmentId.orEmpty(),it.title,it.dueDate,it.state,if(selection.includePrivate) it.privatePlanningNote.orEmpty() else "") } + receivedFollowUps)
        val receivedContactNotes = dao.allTransferredHistoryEntries().filter { it.family == "CONTACT_NOTES" && it.localCustomerId in customerIds && (scope.siteId == null || it.localSiteId == scope.siteId) && (scope.equipmentId == null || it.localEquipmentId == scope.equipmentId) && includesDate(sourceDate(it.eventDateTime)) }.map { row ->
            val value = org.json.JSONObject(row.payloadJson)
            listOf("${row.originWorkspaceId}:${row.sourceEntityId}", row.localCustomerId.orEmpty(), row.localSiteId.orEmpty(), row.localEquipmentId.orEmpty(), row.eventDateTime.orEmpty(), value.optString("outcome"), if (selection.includePrivate) value.optString("privateNote") else "")
        }
        add(ExportFamily.CONTACT_NOTES, "contact_notes.csv", listOf("id","customer_id","site_id","equipment_id","date","outcome","private_note"), dao.allContactNotes().filter { it.customerId in customerIds && (scope.siteId == null || it.siteId == scope.siteId) && (scope.equipmentId == null || it.equipmentId == scope.equipmentId) && includesDate(Instant.ofEpochMilli(it.occurredAtEpochMillis).atZone(businessZone).toLocalDate()) }.map { listOf(it.id,it.customerId,it.siteId.orEmpty(),it.equipmentId.orEmpty(),Instant.ofEpochMilli(it.occurredAtEpochMillis).toString(),it.outcome,if(selection.includePrivate) it.privateNote.orEmpty() else "") } + receivedContactNotes)
        val receivedChanges = dao.allTransferredHistoryEntries().filter { it.family == "CHANGE_HISTORY" && it.localCustomerId in customerIds && (scope.siteId == null || it.localSiteId == scope.siteId) && (scope.equipmentId == null || it.localEquipmentId == scope.equipmentId) && includesDate(sourceDate(it.eventDateTime)) }.map { row ->
            val value = org.json.JSONObject(row.payloadJson)
            listOf("${row.originWorkspaceId}:${row.sourceEntityId}", row.localCustomerId.orEmpty(), row.localSiteId.orEmpty(), row.localEquipmentId.orEmpty(), row.eventDateTime.orEmpty(), value.optString("changeType"), value.optString("reason"), if(selection.includePrivate) value.optString("oldValue") else "", if(selection.includePrivate) value.optString("newValue") else "")
        }
        add(ExportFamily.HISTORY, "changes.csv", listOf("id","customer_id","site_id","equipment_id","date","type","reason","old","new"), dao.allChangeEntries().filter { it.customerId in customerIds && (scope.siteId == null || it.siteId == scope.siteId) && (scope.equipmentId == null || it.equipmentId == scope.equipmentId) && includesDate(sourceDate(it.eventDate)) }.map { listOf(it.id,it.customerId.orEmpty(),it.siteId.orEmpty(),it.equipmentId.orEmpty(),it.eventDate,it.changeType,it.reason,if(selection.includePrivate) it.oldValue.orEmpty() else "",if(selection.includePrivate) it.newValue.orEmpty() else "") } + receivedChanges)
        add(ExportFamily.TEMPLATES, "inspection_templates.csv", listOf("id","reference","name","state"), dao.reusableTemplates().filter { selection.includeInactive || it.state == "ACTIVE" }.map { listOf(it.id,it.reference,it.name,it.state) })

        val imageRows = mutableListOf<List<String>>()
        if (ExportFamily.PHOTO_METADATA in selection.families || ExportFamily.IMAGE_FILES in selection.families) {
            val workToVisit = visits.filter { it.state != "COMPLETED" || it.id in performedVisitIds }
                .flatMap { visit -> dao.visitWorkItems(visit.id).map { it.id to visit } }.toMap()
            val frozenPhotos = revisions.flatMap { (record, revision) ->
                val visit = visits.firstOrNull { it.id == record.visitId } ?: return@flatMap emptyList()
                dao.finalWorkItems(revision.id).flatMap { finalWork -> dao.finalPhotos(finalWork.id).map { Triple(visit, finalWork, it) } }
            }
            val frozenAttachmentIds = frozenPhotos.map { it.third.sourceAttachmentId }.toSet()
            dao.allAttachments().filter { it.ownerType == "WORK_ITEM" && it.ownerId in workToVisit && it.id !in frozenAttachmentIds && (selection.includePrivate || it.visibility == "PUBLIC") }.forEach { photo ->
                val visit = workToVisit.getValue(photo.ownerId)
                val retained = dao.retainedImage("ATTACHMENT", photo.id)
                val original = File(filesRoot, photo.storedRelativePath)
                val useOriginal = original.isFile && original.length() == photo.byteSize && sha256(original.readBytes()) == photo.sha256
                val path = if (useOriginal) photo.storedRelativePath else retained?.derivativeRelativePath ?: error("Photo ${photo.id} has no readable retained copy")
                val file = ownedFile(path)
                val bytes = file.readBytes()
                val expectedHash = if (useOriginal) photo.sha256 else requireNotNull(retained).derivativeSha256
                require(sha256(bytes) == expectedHash) { "Photo ${photo.id} failed integrity check" }
                if (ExportFamily.IMAGE_FILES in selection.families) entries["images/${photo.id}.${if (path.endsWith(".png", true)) "png" else "jpg"}"] = bytes
                imageRows += listOf(photo.id,visit.customerId,visit.siteId,dao.workItem(photo.ownerId)?.equipmentId.orEmpty(),visit.id,photo.ownerId,visit.actualServiceDate,photo.caption.orEmpty(),photo.visibility,photo.includedInCustomerReport.toString(),if(useOriginal) "ORIGINAL" else "DERIVATIVE",photo.sha256)
            }
            frozenPhotos.filter { selection.includePrivate || it.third.visibility == "PUBLIC" }.forEach { (visit, work, photo) ->
                val original = File(filesRoot, photo.storedRelativePath)
                val useOriginal = original.isFile && original.length() == photo.byteSize && sha256(original.readBytes()) == photo.sha256
                val retained = if (useOriginal) null else dao.retainedImage("ATTACHMENT", photo.sourceAttachmentId)
                    ?: dao.retainedImage("FINAL_PHOTO", photo.id) ?: dao.retainedImageByOriginalPath(photo.storedRelativePath)
                    ?: error("Final photo ${photo.id} has no retained copy")
                val path = if (useOriginal) photo.storedRelativePath else retained!!.derivativeRelativePath
                val bytes = ownedFile(path).readBytes()
                require(sha256(bytes) == if (useOriginal) photo.sha256 else retained!!.derivativeSha256) { "Final photo ${photo.id} failed integrity check" }
                if (ExportFamily.IMAGE_FILES in selection.families) entries["images/${photo.id}.${if (path.endsWith(".png", true)) "png" else "jpg"}"] = bytes
                imageRows += listOf(photo.id, visit.customerId, visit.siteId, work.equipmentId.orEmpty(), visit.id, work.sourceWorkItemId,
                    visit.actualServiceDate, photo.caption.orEmpty(), photo.visibility, photo.includedInCustomerReport.toString(),
                    if (useOriginal) "ORIGINAL" else "DERIVATIVE", photo.sha256)
            }
            remote.forEach { result -> dao.remoteResultPhotos(result.id).filter { selection.includePrivate || it.visibility == "PUBLIC" }.forEach { photo ->
                val file = ownedFile(photo.relativePath)
                val bytes = file.readBytes()
                require(sha256(bytes) == photo.sha256 && bytes.size.toLong() == photo.byteSize)
                if (ExportFamily.IMAGE_FILES in selection.families) entries["images/${photo.id}.jpg"] = bytes
                imageRows += listOf(photo.id,result.customerId.orEmpty(),result.localVisitId?.let { dao.visit(it)?.siteId }.orEmpty(),result.localWorkItemId?.let { dao.workItem(it)?.equipmentId }.orEmpty(),result.localVisitId.orEmpty(),result.dispatchItemId,result.serviceDate,photo.caption.orEmpty(),photo.visibility,photo.includeInReport.toString(),"IMPORTED_DERIVATIVE",photo.sha256)
            } }
            transferred.forEach { result -> dao.allTransferredEvidence().filter { it.transferredFinalResultId == result.id && (selection.includePrivate || it.visibility == "PUBLIC") }.forEach { photo ->
                val file = ownedFile(photo.relativePath)
                val bytes = file.readBytes()
                require(sha256(bytes) == photo.sha256 && bytes.size.toLong() == photo.byteSize)
                if (ExportFamily.IMAGE_FILES in selection.families) entries["images/${photo.id}.jpg"] = bytes
                imageRows += listOf(photo.id,result.localCustomerId.orEmpty(),result.localSiteId.orEmpty(),result.localEquipmentId.orEmpty(),"${result.originWorkspaceId}:${result.sourceVisitId}",result.sourceWorkItemId,result.serviceDate,photo.caption.orEmpty(),photo.visibility,photo.includedInCustomerReport.toString(),"TRANSFERRED_DERIVATIVE",photo.sha256)
            } }
            // Image Archive is useful on its own, so include transferred evidence even when its
            // source performed-work family was not selected or its final record is unavailable.
            val linkedTransferEvidence = transferred.flatMap { result -> dao.allTransferredEvidence().filter { it.transferredFinalResultId == result.id }.map { it.id } }.toSet()
            dao.allTransferredEvidence().filter { it.id !in linkedTransferEvidence && it.localCustomerId in customerIds && (scope.siteId == null || it.localSiteId == scope.siteId) && (scope.equipmentId == null || it.localEquipmentId == scope.equipmentId) && (selection.includePrivate || it.visibility == "PUBLIC") }.forEach { photo ->
                val file = ownedFile(photo.relativePath); val bytes = file.readBytes()
                require(sha256(bytes) == photo.sha256 && bytes.size.toLong() == photo.byteSize)
                if (ExportFamily.IMAGE_FILES in selection.families) entries["images/${photo.id}.jpg"] = bytes
                imageRows += listOf(photo.id,photo.localCustomerId.orEmpty(),photo.localSiteId.orEmpty(),photo.localEquipmentId.orEmpty(),"${photo.originWorkspaceId}:${photo.sourceVisitId}",photo.sourceWorkItemId,photo.serviceDate,photo.caption.orEmpty(),photo.visibility,photo.includedInCustomerReport.toString(),"TRANSFERRED_DERIVATIVE",photo.sha256)
            }
        }
        add(ExportFamily.PHOTO_METADATA, "photos.csv", listOf("id","customer_id","site_id","equipment_id","visit_id","work_id","date","caption","visibility","included_in_report","copy_type","source_sha256"), imageRows)
        val readme = buildString {
            appendLine("ServiceLoop Export Center")
            appendLine("Readable CSV and images; not a recovery backup. This archive is unencrypted.")
            appendLine("Families: ${selection.families.joinToString { it.title }}")
            appendLine("Customer: ${scope.customerId ?: "All"}; Site: ${scope.siteId ?: "All"}; Equipment: ${scope.equipmentId ?: "All"}")
            appendLine("Service dates: ${scope.fromDate ?: "Any"} through ${scope.toDate ?: "Any"}")
            appendLine("Date filters apply to work and photo history. Current directory, templates, follow-ups, and contact notes use the hierarchy only.")
            appendLine("Private: ${selection.includePrivate}; inactive: ${selection.includeInactive}; previous revisions: ${selection.includePreviousRevisions}")
        }.toByteArray(Charsets.UTF_8)
        entries["README.txt"] = readme
        return ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } } }.toByteArray()
    }

    private fun ownedFile(path: String): File {
        val root = filesRoot.canonicalFile
        val file = File(root, path).canonicalFile
        require(file.path.startsWith(root.path + File.separator) && file.isFile) { "Image is outside ServiceLoop storage or missing" }
        return file
    }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun csv(headers: List<String>, rows: List<List<String>>): ByteArray {
        val numericColumns = setOf("position", "interval_count", "quantity", "included_in_report")
        val numeric = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?")
        fun quote(value: String) = "\"${value.replace("\"", "\"\"")}\""
        fun presentation(header: String, value: String): String {
            if (header in numericColumns && numeric.matches(value)) return value
            val firstSignificant = value.firstOrNull { !it.isWhitespace() && !it.isISOControl() }
            val formulaLike = firstSignificant in setOf('=', '+', '-', '@') ||
                value.firstOrNull()?.let { it.isISOControl() } == true
            // CSV is a readable projection; the apostrophe is intentionally absent from native transfer.
            return if (formulaLike) "'$value" else value
        }
        return (listOf(headers.map(::quote)) + rows.map { row ->
            require(row.size == headers.size) { "Readable export row does not match its header" }
            row.mapIndexed { index, value -> quote(presentation(headers[index], value)) }
        }).joinToString("\r\n") { it.joinToString(",") }.plus("\r\n").toByteArray(Charsets.UTF_8)
    }
}
