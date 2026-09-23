package com.v16studio.serviceloop.data

import com.v16studio.serviceloop.domain.ServiceLoopScopeFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
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
        val finalRecords = dao.allFinalRecords().filter { it.visitId in visitIds && (selection.includeInactive || !it.voided) }
        val revisions = finalRecords.flatMap { record ->
            (if (selection.includePreviousRevisions) dao.finalRevisions(record.id) else listOfNotNull(dao.finalRevision(record.currentRevisionId))).map { record to it }
        }
        val finalWork = revisions.flatMap { (_, revision) -> dao.finalWorkItems(revision.id).filter { scope.equipmentId == null || it.equipmentId == scope.equipmentId }.map { revision to it } }
        val remote = dao.reportableRemoteFinalResults().filter { result -> result.customerId in customerIds && result.localVisitId in visitIds &&
            (scope.equipmentId == null || result.localWorkItemId?.let { dao.workItem(it)?.equipmentId } == scope.equipmentId) &&
            scope.matches(result.customerId ?: "", result.localVisitId?.let { dao.visit(it)?.siteId }, scope.equipmentId, LocalDate.parse(result.serviceDate)) }
        val entries = linkedMapOf<String, ByteArray>()
        fun add(family: ExportFamily, name: String, headers: List<String>, rows: List<List<String>>) {
            if (family in selection.families) entries[name] = csv(headers, rows)
        }
        add(ExportFamily.CUSTOMERS, "customers.csv", listOf("id","reference","name","type","state","private_note"), customers.map { listOf(it.id,it.reference,it.name,it.customerType,it.state,if(selection.includePrivate) it.privateNote.orEmpty() else "") })
        add(ExportFamily.CONTACTS, "contacts.csv", listOf("id","customer_id","position","person","channel","value","notes"), customers.flatMap { customer -> dao.customerContacts(customer.id).map { listOf(it.id,it.customerId,it.position.toString(),it.personName.orEmpty(),it.channel,it.value,if(selection.includePrivate) it.notes.orEmpty() else "") } })
        add(ExportFamily.SITES, "sites.csv", listOf("id","customer_id","reference","name","address","state","private_access_notes"), sites.map { listOf(it.id,it.customerId,it.reference,it.name,it.address.orEmpty(),it.state,if(selection.includePrivate) it.privateAccessNotes.orEmpty() else "") })
        add(ExportFamily.EQUIPMENT, "equipment.csv", listOf("id","site_id","reference","name","make","model","serial","state","private_notes"), equipment.map { listOf(it.id,it.siteId,it.reference,it.name,it.make.orEmpty(),it.model.orEmpty(),it.serialNumber.orEmpty(),it.state,if(selection.includePrivate) it.privateNotes.orEmpty() else "") })
        add(ExportFamily.PLANS, "service_plans.csv", listOf("id","equipment_id","reference","name","interval_count","interval_unit","current_due","state"), dao.allPlans().filter { it.equipmentId in equipmentIds && (selection.includeInactive || it.state == "ACTIVE") }.map { listOf(it.id,it.equipmentId,it.reference,it.name,it.intervalCount.toString(),it.intervalUnit,it.currentDueDate,it.state) })
        add(ExportFamily.VISITS, "visits.csv", listOf("id","reference","customer_id","site_id","service_date","state"), visits.map { listOf(it.id,it.reference,it.customerId,it.siteId,it.actualServiceDate,it.state) })
        add(ExportFamily.SERVICE_RECORDS, "service_records.csv", listOf("revision_id","visit_id","service_date","technician","service","outcome","public_work","next_due","private_note","source"),
            finalWork.map { (revision,work) -> listOf(revision.id,dao.finalRecord(revision.recordId)?.visitId.orEmpty(),revision.actualServiceDate,revision.technicianName,work.serviceName,work.outcome,work.publicWorkNote.orEmpty(),work.nextDueDate.orEmpty(),if(selection.includePrivate) work.privateInternalNote.orEmpty() else "","LOCAL") } +
                remote.map { result -> listOf(result.sourceFinalRevisionId,result.localVisitId.orEmpty(),result.serviceDate,result.technicianName,org.json.JSONObject(result.provenanceJson).optString("serviceName"),result.outcome,result.workPerformed.orEmpty(),org.json.JSONObject(result.recurrenceJson).optString("nextDueDate"),if(selection.includePrivate) result.internalNotes.orEmpty() else "","IMPORTED") })
        add(ExportFamily.CHECKLIST, "checklist.csv", listOf("revision_id","work_id","position","question","response_type","disposition","answer","reason","source"),
            finalWork.flatMap { (revision,work) -> dao.finalChecklistItems(work.id).map { listOf(revision.id,work.id,it.position.toString(),it.label,it.responseType,it.disposition,it.textValue ?: it.numberValue.orEmpty(),it.reason.orEmpty(),"LOCAL") } } +
                remote.flatMap { result -> val values = org.json.JSONArray(result.checklistJson); (0 until values.length()).map { index -> val item = values.getJSONObject(index); listOf(result.sourceFinalRevisionId,result.dispatchItemId,item.optString("position"),item.optString("label"),item.optString("responseType"),item.optString("disposition"),item.optString("textValue").ifBlank { item.optString("numberValue") },item.optString("reason"),"IMPORTED") } })
        add(ExportFamily.PARTS, "parts.csv", listOf("revision_id","work_id","description","quantity","unit","source"),
            finalWork.flatMap { (revision,work) -> dao.finalParts(work.id).map { listOf(revision.id,work.id,it.description,it.quantity,it.unit,"LOCAL") } } +
                remote.flatMap { result -> val values = org.json.JSONArray(result.partsJson); (0 until values.length()).map { index -> val part = values.getJSONObject(index); listOf(result.sourceFinalRevisionId,result.dispatchItemId,part.optString("description"),part.optString("quantity"),part.optString("unit"),"IMPORTED") } })
        add(ExportFamily.FOLLOW_UPS, "followups.csv", listOf("id","customer_id","site_id","equipment_id","title","due_date","state","private_note"), dao.allFollowUps().filter { it.customerId in customerIds && (scope.siteId == null || it.siteId == scope.siteId) && (scope.equipmentId == null || it.equipmentId == scope.equipmentId) }.map { listOf(it.id,it.customerId,it.siteId.orEmpty(),it.equipmentId.orEmpty(),it.title,it.dueDate,it.state,if(selection.includePrivate) it.privatePlanningNote.orEmpty() else "") })
        add(ExportFamily.CONTACT_NOTES, "contact_notes.csv", listOf("id","customer_id","site_id","equipment_id","date","outcome","private_note"), dao.allContactNotes().filter { it.customerId in customerIds && (scope.siteId == null || it.siteId == scope.siteId) && (scope.equipmentId == null || it.equipmentId == scope.equipmentId) }.map { listOf(it.id,it.customerId,it.siteId.orEmpty(),it.equipmentId.orEmpty(),java.time.Instant.ofEpochMilli(it.occurredAtEpochMillis).toString(),it.outcome,if(selection.includePrivate) it.privateNote.orEmpty() else "") })
        add(ExportFamily.HISTORY, "changes.csv", listOf("id","customer_id","site_id","equipment_id","date","type","reason","old","new"), dao.allChangeEntries().filter { it.customerId in customerIds && (scope.siteId == null || it.siteId == scope.siteId) && (scope.equipmentId == null || it.equipmentId == scope.equipmentId) }.map { listOf(it.id,it.customerId.orEmpty(),it.siteId.orEmpty(),it.equipmentId.orEmpty(),it.eventDate,it.changeType,it.reason,if(selection.includePrivate) it.oldValue.orEmpty() else "",if(selection.includePrivate) it.newValue.orEmpty() else "") })
        add(ExportFamily.TEMPLATES, "inspection_templates.csv", listOf("id","reference","name","state"), dao.reusableTemplates().filter { selection.includeInactive || it.state == "ACTIVE" }.map { listOf(it.id,it.reference,it.name,it.state) })

        val imageRows = mutableListOf<List<String>>()
        if (ExportFamily.PHOTO_METADATA in selection.families || ExportFamily.IMAGE_FILES in selection.families) {
            val workToVisit = visits.flatMap { visit -> dao.visitWorkItems(visit.id).map { it.id to visit } }.toMap()
            dao.allAttachments().filter { it.ownerType == "WORK_ITEM" && it.ownerId in workToVisit }.forEach { photo ->
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
                imageRows += listOf(photo.id,visit.customerId,visit.siteId,dao.workItem(photo.ownerId)?.equipmentId.orEmpty(),visit.id,photo.ownerId,visit.actualServiceDate,photo.caption.orEmpty(),if(photo.includedInCustomerReport) "PUBLIC" else "PRIVATE",photo.includedInCustomerReport.toString(),if(useOriginal) "ORIGINAL" else "DERIVATIVE",photo.sha256)
            }
            remote.forEach { result -> dao.remoteResultPhotos(result.id).forEach { photo ->
                val file = ownedFile(photo.relativePath)
                val bytes = file.readBytes()
                require(sha256(bytes) == photo.sha256 && bytes.size.toLong() == photo.byteSize)
                if (ExportFamily.IMAGE_FILES in selection.families) entries["images/${photo.id}.jpg"] = bytes
                imageRows += listOf(photo.id,result.customerId.orEmpty(),result.localVisitId?.let { dao.visit(it)?.siteId }.orEmpty(),result.localWorkItemId?.let { dao.workItem(it)?.equipmentId }.orEmpty(),result.localVisitId.orEmpty(),result.dispatchItemId,result.serviceDate,photo.caption.orEmpty(),photo.visibility,photo.includeInReport.toString(),"IMPORTED_DERIVATIVE",photo.sha256)
            } }
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
    private fun csv(headers: List<String>, rows: List<List<String>>): ByteArray = (listOf(headers) + rows).joinToString("\r\n") { row -> row.joinToString(",") { value -> "\"${value.replace("\"", "\"\"")}\"" } }.plus("\r\n").toByteArray(Charsets.UTF_8)
}
