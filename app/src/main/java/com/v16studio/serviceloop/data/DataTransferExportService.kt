package com.v16studio.serviceloop.data

import android.graphics.BitmapFactory
import com.v16studio.serviceloop.domain.ServiceLoopScopeFilter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

/** Builds the native additive-sharing package from the same choices as readable Export Center. */
class DataTransferExportService(private val database: ServiceLoopDatabase, private val filesRoot: File) {
    private val dao = database.serviceLoopDao()

    suspend fun export(selection: ExportCenterSelection): ByteArray {
        require(selection.families.isNotEmpty()) { "Choose at least one content family" }
        val identity = ServiceLoopPeerTrustStore(database).localIdentity().technicianId
        val requested = selection.families
        val wantsPlans = ExportFamily.PLANS in requested
        val wantsTemplates = ExportFamily.TEMPLATES in requested
        val wantsPerformed = requested.any { it in setOf(ExportFamily.VISITS, ExportFamily.SERVICE_RECORDS, ExportFamily.CHECKLIST, ExportFamily.PARTS) }
        val wantsFollowUps = ExportFamily.FOLLOW_UPS in requested
        val wantsContactNotes = ExportFamily.CONTACT_NOTES in requested
        val wantsChanges = ExportFamily.HISTORY in requested
        val wantsEvidence = requested.any { it in setOf(ExportFamily.PHOTO_METADATA, ExportFamily.IMAGE_FILES) }

        val customers = dao.allCustomers()
        val scopedCustomers = customers.filter { selection.scope.customerId == null || it.id == selection.scope.customerId }
        require(selection.scope.customerId == null || scopedCustomers.size == 1) { "Selected customer is unavailable" }
        val customerIds = scopedCustomers.map { it.id }.toSet()
        val sites = dao.allSites().filter { it.customerId in customerIds && (selection.scope.siteId == null || it.id == selection.scope.siteId) }
        require(selection.scope.siteId == null || sites.size == 1) { "Selected site is unavailable" }
        val siteIds = sites.map { it.id }.toSet()
        val equipment = dao.allEquipment().filter { it.siteId in siteIds && (selection.scope.equipmentId == null || it.id == selection.scope.equipmentId) }
        require(selection.scope.equipmentId == null || equipment.size == 1) { "Selected equipment is unavailable" }
        val equipmentIds = equipment.map { it.id }.toSet()

        val families = linkedMapOf<DataTransferFamily, ByteArray>()
        if (hasRegisterContent(requested) || wantsPlans || wantsPerformed || wantsFollowUps || wantsContactNotes || wantsChanges || wantsEvidence) {
            val register = encodeRegister(selection, identity, scopedCustomers, sites, equipment,
                includeCustomers = ExportFamily.CUSTOMERS in requested || requested.any { it in setOf(ExportFamily.CONTACTS, ExportFamily.SITES, ExportFamily.EQUIPMENT, ExportFamily.PLANS) } || wantsPerformed || wantsFollowUps || wantsContactNotes || wantsChanges || wantsEvidence,
                includeContacts = ExportFamily.CONTACTS in requested,
                includeSites = ExportFamily.SITES in requested || ExportFamily.EQUIPMENT in requested || wantsPlans || wantsPerformed || wantsEvidence,
                includeEquipment = ExportFamily.EQUIPMENT in requested || wantsPlans || wantsPerformed || wantsEvidence)
            families[DataTransferFamily.REGISTER] = register
        }
        val localTemplates = dao.reusableTemplates().filter { selection.includeInactive || it.state == "ACTIVE" }
        if (wantsTemplates || wantsPlans) {
            val usedTemplateIds = if (wantsPlans) dao.allPlans().filter { it.equipmentId in equipmentIds && (selection.includeInactive || it.state == "ACTIVE") }.mapNotNull { it.reusableTemplateId }.toSet() else emptySet()
            val selectedTemplateIds = if (wantsTemplates) localTemplates.map { it.id }.toSet() + usedTemplateIds else usedTemplateIds
            if (selectedTemplateIds.isNotEmpty()) families[DataTransferFamily.INSPECTION_TEMPLATES] = encodeTemplates(selectedTemplateIds, identity, selection.includePrivate)
        }
        if (wantsPlans) families[DataTransferFamily.SERVICE_PLANS] = encodePlans(selection, identity, equipmentIds)
        if (wantsPerformed) families[DataTransferFamily.PERFORMED_WORK] = encodePerformed(selection, identity, customerIds, siteIds)
        if (wantsFollowUps) families[DataTransferFamily.FOLLOW_UPS] = encodeTransferredContext(selection, identity, "FOLLOW_UPS")
        if (wantsContactNotes) families[DataTransferFamily.CONTACT_NOTES] = encodeTransferredContext(selection, identity, "CONTACT_NOTES")
        if (wantsChanges) families[DataTransferFamily.CHANGE_HISTORY] = encodeTransferredContext(selection, identity, "CHANGE_HISTORY")
        val evidence = if (wantsEvidence) encodeEvidence(selection, identity, customerIds, siteIds) else null
        val binaries = evidence?.binaries.orEmpty()
        if (evidence != null) families[DataTransferFamily.EVIDENCE] = evidence.metadata
        return DataTransferCodec.encode(
            exporterId = identity,
            generatedAt = Instant.now().toString(),
            families = families,
            binaries = binaries,
            sourceWorkspaceId = identity,
            options = DataTransferOptions(selection.includePrivate, selection.includeInactive, selection.includePreviousRevisions),
        )
    }

    private data class EncodedEvidence(val metadata: ByteArray, val binaries: Map<String, ByteArray>)

    private fun hasRegisterContent(families: Set<ExportFamily>) = families.any { it in setOf(ExportFamily.CUSTOMERS, ExportFamily.CONTACTS, ExportFamily.SITES, ExportFamily.EQUIPMENT) }

    private suspend fun encodeRegister(selection: ExportCenterSelection, origin: String, customers: List<CustomerEntity>, sites: List<SiteEntity>, equipment: List<EquipmentEntity>,
        includeCustomers: Boolean, includeContacts: Boolean, includeSites: Boolean, includeEquipment: Boolean): ByteArray {
        val selectedCustomerIds = customers.map { it.id }.toSet()
        val selectedSiteIds = sites.map { it.id }.toSet()
        val selectedEquipmentIds = equipment.map { it.id }.toSet()
        val root = JSONObject().put("version", 1).put("customers", JSONArray()).put("contacts", JSONArray()).put("sites", JSONArray()).put("equipment", JSONArray())
        if (includeCustomers) customers.filter { selection.includeInactive || it.state == "ACTIVE" }.forEach { row ->
            val json = JSONObject().put("originWorkspaceId", originOf("CUSTOMER", row.id, origin)).put("sourceEntityId", sourceOf("CUSTOMER", row.id))
                .put("reference", row.reference).put("name", row.name).put("customerType", row.customerType).put("state", row.state)
            if (selection.includePrivate) json.put("privateFieldsIncluded", true).put("contactName", row.contactName).put("phone", row.phone).put("email", row.email).put("privateNote", row.privateNote)
            root.getJSONArray("customers").put(json)
        }
        if (includeContacts) customers.filter { it.id in selectedCustomerIds && (selection.includeInactive || it.state == "ACTIVE") }.forEach { customer ->
            dao.customerContacts(customer.id).forEach { row ->
                root.getJSONArray("contacts").put(JSONObject().put("originWorkspaceId", originOf("CUSTOMER_CONTACT", row.id, origin))
                    .put("sourceEntityId", sourceOf("CUSTOMER_CONTACT", row.id)).put("customerOriginWorkspaceId", originOf("CUSTOMER", row.customerId, origin))
                    .put("customerSourceEntityId", sourceOf("CUSTOMER", row.customerId)).put("position", row.position).put("personName", row.personName)
                    .put("channel", row.channel).put("value", row.value).apply { if (selection.includePrivate) put("notes", row.notes) })
            }
        }
        if (includeSites) sites.filter { (selection.includeInactive || it.state == "ACTIVE") && it.customerId in selectedCustomerIds }.forEach { row ->
            val json = JSONObject().put("originWorkspaceId", originOf("SITE", row.id, origin)).put("sourceEntityId", sourceOf("SITE", row.id))
                .put("customerOriginWorkspaceId", originOf("CUSTOMER", row.customerId, origin)).put("customerSourceEntityId", sourceOf("CUSTOMER", row.customerId))
                .put("reference", row.reference).put("name", row.name).put("isDefault", row.isDefault).put("state", row.state)
            if (selection.includePrivate) json.put("privateFieldsIncluded", true).put("address", row.address).put("privateAccessNotes", row.privateAccessNotes).put("contactName", row.contactName).put("phone", row.phone).put("email", row.email)
            root.getJSONArray("sites").put(json)
        }
        if (includeEquipment) equipment.filter { (selection.includeInactive || it.state == "ACTIVE") && it.siteId in selectedSiteIds }.forEach { row ->
            val json = JSONObject().put("originWorkspaceId", originOf("EQUIPMENT", row.id, origin)).put("sourceEntityId", sourceOf("EQUIPMENT", row.id))
                .put("siteOriginWorkspaceId", originOf("SITE", row.siteId, origin)).put("siteSourceEntityId", sourceOf("SITE", row.siteId))
                .put("reference", row.reference).put("name", row.name).put("state", row.state)
            if (selection.includePrivate) json.put("privateFieldsIncluded", true).put("technicianIdentifier", row.technicianIdentifier).put("make", row.make).put("model", row.model).put("serialNumber", row.serialNumber).put("privateNotes", row.privateNotes)
            root.getJSONArray("equipment").put(json)
        }
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    private suspend fun encodeTemplates(ids: Set<String>, origin: String, includePrivate: Boolean): ByteArray {
        val entries = ids.sorted().map { id ->
            val template = dao.reusableTemplate(id) ?: error("Inspection template no longer exists")
            val revision = dao.reusableTemplateRevision(template.currentRevisionId) ?: error("Inspection template revision is missing")
            val binding = dao.dataTransferBindingsForLocal("INSPECTION_TEMPLATE", template.id).firstOrNull()
            InspectionTemplateTransferEntry(template.reference, revision.nameSnapshot, revision.revisionNumber,
                dao.reusableTemplateItems(revision.id).map { DispatchInspectionItem(it.position, it.label, it.responseType, it.unit, it.required, if (includePrivate) it.privateGuidance else null) },
                originWorkspaceId = binding?.originWorkspaceId ?: origin,
                sourceEntityId = binding?.sourceEntityId ?: template.id)
        }
        return InspectionTemplateCodec.encode(InspectionTemplateTransfer(Instant.now().toString(), entries))
    }

    private suspend fun encodePlans(selection: ExportCenterSelection, origin: String, equipmentIds: Set<String>): ByteArray {
        val rows = JSONArray()
        dao.allPlans().filter { it.equipmentId in equipmentIds && (selection.includeInactive || it.state == "ACTIVE") }.forEach { row ->
            val equipment = dao.equipment(row.equipmentId) ?: return@forEach
            val site = dao.site(equipment.siteId) ?: return@forEach
            val customer = dao.customer(site.customerId) ?: return@forEach
            val templateBinding = row.reusableTemplateId?.let { dao.dataTransferBindingsForLocal("INSPECTION_TEMPLATE", it).firstOrNull() }
            val templateOrigin = row.reusableTemplateId?.let { templateBinding?.originWorkspaceId ?: origin }
            val templateSource = row.reusableTemplateId?.let { templateBinding?.sourceEntityId ?: it }
            rows.put(JSONObject().put("originWorkspaceId", originOf("SERVICE_PLAN", row.id, origin)).put("sourceEntityId", sourceOf("SERVICE_PLAN", row.id))
                .put("customerOriginWorkspaceId", originOf("CUSTOMER", customer.id, origin)).put("customerSourceEntityId", sourceOf("CUSTOMER", customer.id))
                .put("siteOriginWorkspaceId", originOf("SITE", site.id, origin)).put("siteSourceEntityId", sourceOf("SITE", site.id))
                .put("equipmentOriginWorkspaceId", originOf("EQUIPMENT", equipment.id, origin)).put("equipmentSourceEntityId", sourceOf("EQUIPMENT", equipment.id))
                .put("reference", row.reference).put("name", row.name).put("intervalCount", row.intervalCount).put("intervalUnit", row.intervalUnit)
                .put("currentDueDate", row.currentDueDate).put("state", row.state)
                .put("templateOriginWorkspaceId", templateOrigin ?: JSONObject.NULL).put("templateSourceEntityId", templateSource ?: JSONObject.NULL))
        }
        return JSONObject().put("version", 1).put("plans", rows).toString().toByteArray(Charsets.UTF_8)
    }

    private suspend fun encodePerformed(selection: ExportCenterSelection, origin: String, customerIds: Set<String>, siteIds: Set<String>): ByteArray {
        val groups = linkedMapOf<Pair<String, String>, JSONObject>()
        fun addResult(row: JSONObject, sourceOrigin: String, sourceVisit: String, visitReference: String, serviceDate: String, customer: JSONObject, site: JSONObject) {
            val key = sourceOrigin to sourceVisit
            val group = groups.getOrPut(key) { JSONObject().put("originWorkspaceId", sourceOrigin).put("sourceVisitId", sourceVisit)
                .put("visitReference", visitReference).put("serviceDate", serviceDate).put("customerSnapshot", customer).put("siteSnapshot", site)
                .put("records", JSONArray()) }
            group.getJSONArray("records").put(row)
        }
        dao.allFinalRecords().filterNot { it.voided }.forEach { record ->
            val visit = dao.visit(record.visitId) ?: return@forEach
            if (visit.customerId !in customerIds || visit.siteId !in siteIds) return@forEach
            val revisions = if (selection.includePreviousRevisions) dao.finalRevisions(record.id) else listOfNotNull(dao.finalRevision(record.currentRevisionId))
            revisions.forEach { revision ->
                val customer = JSONObject().put("name", revision.customerName).put("reference", revision.customerReference)
                val site = JSONObject().put("name", revision.siteName).put("reference", revision.siteReference).put("address", revision.siteAddress)
                dao.finalWorkItems(revision.id).forEach { item ->
                    if (selection.scope.equipmentId != null && item.equipmentId != selection.scope.equipmentId) return@forEach
                    if (!selection.scope.matches(visit.customerId, visit.siteId, item.equipmentId, LocalDate.parse(revision.actualServiceDate))) return@forEach
                    val payload = JSONObject().put("originWorkspaceId", origin).put("sourceVisitId", visit.id).put("sourceWorkItemId", item.sourceWorkItemId).put("sourceWorkItemPosition", item.position)
                        .put("sourceFinalRevisionId", revision.id).put("logicalResultId", "$origin:${visit.id}:${item.sourceWorkItemId}")
                        .put("technicianId", origin).put("technicianName", revision.technicianName).put("technicianDesignation", revision.technicianDesignation)
                        .put("visitReference", revision.visitReference).put("serviceDate", revision.actualServiceDate)
                        .put("customerSnapshot", customer).put("siteSnapshot", site)
                        .put("subjectSnapshot", JSONObject().put("type", item.subjectType).put("equipmentName", item.equipmentName).put("equipmentReference", item.equipmentReference)
                            .put("equipmentIdentifier", item.equipmentIdentifier).put("make", item.equipmentMake).put("model", item.equipmentModel).put("serial", item.equipmentSerial).put("equipmentDescription", item.equipmentDescription))
                        .put("serviceName", item.serviceName).put("outcome", item.outcome).put("workPerformed", item.publicWorkNote)
                        .put("customer", entityRef("CUSTOMER", visit.customerId, origin)).put("site", entityRef("SITE", visit.siteId, origin))
                        .put("equipment", item.equipmentId?.let { entityRef("EQUIPMENT", it, origin) })
                        .put("notPerformedReason", item.notPerformedReason).put("checklist", checklistJson(item.id)).put("findings", findingsJson(item.id))
                        .put("parts", partsJson(item.id)).put("recurrence", JSONObject().put("fulfilledObligation", item.fulfilledObligation)
                            .put("oldDueDate", item.oldDueDate).put("nextDueDate", item.nextDueDate).put("intervalCount", item.intervalCount).put("intervalUnit", item.intervalUnit))
                        .put("recordedAt", Instant.ofEpochMilli(revision.recordedAtEpochMillis).toString())
                    if (selection.includePrivate) payload.put("internalNotes", item.privateInternalNote).put("finalInternalNote", revision.privateInternalNote)
                    val sourceOrigin = originOf("PERFORMED_WORK", item.sourceWorkItemId, origin)
                    payload.put("originWorkspaceId", sourceOrigin)
                    val sourceVisit = if (sourceOrigin == origin) visit.id else sourceOf("VISIT", visit.id)
                    payload.put("sourceVisitId", sourceVisit)
                    addResult(payload, sourceOrigin, sourceVisit, revision.visitReference, revision.actualServiceDate, customer, site)
                }
            }
        }
        effectiveImportedFinalResults(dao.reportableRemoteFinalResults(), dao.allTransferredFinalResults(), selection.includePreviousRevisions).forEach { imported ->
            val result = imported.transferred ?: imported.remote ?: return@forEach
            val service = when (imported.kind) {
                ImportedFinalKind.WORK_RESULT -> encodeRemoteResult(result as RemoteFinalResultEntity, selection.includePrivate, origin)
                ImportedFinalKind.DATA_TRANSFER -> encodeTransferredResult(result as TransferredFinalResultEntity, selection.includePrivate, origin)
                else -> null
            } ?: return@forEach
            val customerId = service.optString("localCustomerId").takeIf { it.isNotBlank() }
            val siteId = service.optString("localSiteId").takeIf { it.isNotBlank() }
            if ((customerId != null && customerId !in customerIds) || (siteId != null && siteId !in siteIds)) return@forEach
            val date = service.getString("serviceDate")
            if (!selection.scope.matches(customerId ?: "", siteId, service.optString("localEquipmentId").takeIf { it.isNotBlank() }, LocalDate.parse(date))) return@forEach
            val originId = service.getString("originWorkspaceId")
            val visitId = service.getString("sourceVisitId")
            addResult(service, originId, visitId, service.getString("visitReference"), date,
                JSONObject(service.getString("customerSnapshot")), JSONObject(service.getString("siteSnapshot")))
        }
        return JSONObject().put("version", 1).put("visits", JSONArray(groups.values.toList())).toString().toByteArray(Charsets.UTF_8)
    }

    private suspend fun checklistJson(workItemId: String) = JSONArray().also { array -> dao.finalChecklistItems(workItemId).forEach { item ->
        array.put(JSONObject().put("position", item.position).put("label", item.label).put("responseType", item.responseType).put("unit", item.unit)
            .put("required", item.required).put("disposition", item.disposition).put("textValue", item.textValue).put("numberValue", item.numberValue).put("reason", item.reason))
    } }
    private suspend fun findingsJson(workItemId: String) = JSONArray().also { array -> dao.finalChecklistItems(workItemId).filter { it.disposition == "ISSUE_FOUND" }.forEach { item -> array.put(JSONObject().put("position", item.position).put("label", item.label).put("description", item.reason)) } }
    private suspend fun partsJson(workItemId: String) = JSONArray().also { array -> dao.finalParts(workItemId).forEach { item -> array.put(JSONObject().put("position", item.position).put("description", item.description).put("quantity", item.quantity).put("unit", item.unit)) } }

    private suspend fun encodeRemoteResult(value: RemoteFinalResultEntity, includePrivate: Boolean, localWorkspaceId: String): JSONObject {
        val provenance = JSONObject(value.provenanceJson)
        val localVisit = value.localVisitId?.let { dao.visit(it) }
        val localWork = value.localWorkItemId?.let { dao.workItem(it) }
        val work = JSONObject().put("originWorkspaceId", value.technicianId).put("sourceVisitId", value.dispatchVisitId)
            .put("sourceWorkItemId", value.dispatchItemId).put("sourceFinalRevisionId", value.sourceFinalRevisionId).put("logicalResultId", value.resultId)
            .put("technicianId", value.technicianId).put("technicianName", value.technicianName).put("technicianDesignation", value.technicianDesignation)
            .put("visitReference", provenance.optString("visitReference", value.dispatchVisitId.take(12))).put("serviceDate", value.serviceDate)
            .put("customerSnapshot", JSONObject(value.customerSnapshotJson)).put("siteSnapshot", JSONObject(value.siteSnapshotJson)).put("subjectSnapshot", JSONObject(value.subjectSnapshotJson))
            .put("serviceName", provenance.optString("serviceName").ifBlank { "Assigned service" }).put("outcome", value.outcome).put("workPerformed", value.workPerformed)
            .put("notPerformedReason", value.notPerformedReason).put("checklist", JSONArray(value.checklistJson)).put("findings", JSONArray(value.findingsJson))
            .put("parts", JSONArray(value.partsJson)).put("recurrence", JSONObject(value.recurrenceJson)).put("recordedAt", provenance.optString("recordedAt"))
            .put("localCustomerId", value.customerId).put("localSiteId", localVisit?.siteId).put("localEquipmentId", localWork?.equipmentId)
        value.customerId?.let { work.put("customer", entityRef("CUSTOMER", it, localWorkspaceId)) }
        localVisit?.let { work.put("site", entityRef("SITE", it.siteId, localWorkspaceId)) }
        localWork?.equipmentId?.let { work.put("equipment", entityRef("EQUIPMENT", it, localWorkspaceId)) }
        if (includePrivate) work.put("internalNotes", value.internalNotes)
        return work
    }

    private suspend fun encodeTransferredResult(value: TransferredFinalResultEntity, includePrivate: Boolean, localWorkspaceId: String): JSONObject {
        val result = JSONObject().put("originWorkspaceId", value.originWorkspaceId).put("sourceVisitId", value.sourceVisitId)
            .put("sourceWorkItemId", value.sourceWorkItemId).put("sourceFinalRevisionId", value.sourceFinalRevisionId).put("logicalResultId", value.logicalResultId)
            .put("technicianId", value.technicianId).put("technicianName", value.technicianName).put("technicianDesignation", value.technicianDesignation)
            .put("visitReference", value.visitReference).put("serviceDate", value.serviceDate).put("customerSnapshot", JSONObject(value.customerSnapshotJson))
            .put("siteSnapshot", JSONObject(value.siteSnapshotJson)).put("subjectSnapshot", JSONObject(value.subjectSnapshotJson)).put("serviceName", value.serviceName)
            .put("outcome", value.outcome).put("workPerformed", value.workPerformed).put("notPerformedReason", value.notPerformedReason)
            .put("checklist", JSONArray(value.checklistJson)).put("findings", JSONArray(value.findingsJson)).put("parts", JSONArray(value.partsJson))
            .put("recurrence", JSONObject(value.recurrenceJson)).put("localCustomerId", value.localCustomerId).put("localSiteId", value.localSiteId).put("localEquipmentId", value.localEquipmentId)
            .put("recordedAt", JSONObject(value.provenanceJson).optString("recordedAt"))
        value.localCustomerId?.let { result.put("customer", entityRef("CUSTOMER", it, localWorkspaceId)) }
        value.localSiteId?.let { result.put("site", entityRef("SITE", it, localWorkspaceId)) }
        value.localEquipmentId?.let { result.put("equipment", entityRef("EQUIPMENT", it, localWorkspaceId)) }
        if (includePrivate) result.put("internalNotes", value.internalNotes)
        return result
    }

    private suspend fun encodeTransferredContext(selection: ExportCenterSelection, origin: String, family: String): ByteArray {
        val records = JSONArray()
        if (family == "FOLLOW_UPS") dao.allFollowUps().forEach { row ->
            if (!scopeMatches(selection.scope, row.customerId, row.siteId, row.equipmentId, row.dueDate)) return@forEach
            val customer = row.customerId
            val value = JSONObject().put("originWorkspaceId", originOf(family, row.id, origin)).put("sourceEntityId", sourceOf(family, row.id))
                .put("eventDateTime", row.dueDate).put("customer", entityRef("CUSTOMER", customer, origin))
                .put("site", row.siteId?.let { entityRef("SITE", it, origin) }).put("equipment", row.equipmentId?.let { entityRef("EQUIPMENT", it, origin) })
                .put("payload", JSONObject().put("type", row.type).put("title", row.title).put("dueDate", row.dueDate).put("state", row.state)
                    .put("customer", entityRef("CUSTOMER", customer, origin)).put("site", row.siteId?.let { entityRef("SITE", it, origin) })
                    .put("equipment", row.equipmentId?.let { entityRef("EQUIPMENT", it, origin) }))
            if (selection.includePrivate) value.getJSONObject("payload").put("privatePlanningNote", row.privatePlanningNote)
            records.put(value)
        }
        if (family == "CONTACT_NOTES") dao.allContactNotes().forEach { row ->
            if (!scopeMatches(selection.scope, row.customerId, row.siteId, row.equipmentId, LocalDate.parse(Instant.ofEpochMilli(row.occurredAtEpochMillis).toString().take(10)).toString())) return@forEach
            val value = JSONObject().put("originWorkspaceId", originOf(family, row.id, origin)).put("sourceEntityId", sourceOf(family, row.id))
                .put("eventDateTime", Instant.ofEpochMilli(row.occurredAtEpochMillis).toString())
                .put("customer", entityRef("CUSTOMER", row.customerId, origin)).put("site", row.siteId?.let { entityRef("SITE", it, origin) })
                .put("equipment", row.equipmentId?.let { entityRef("EQUIPMENT", it, origin) })
                .put("payload", JSONObject().put("reference", row.reference).put("customer", entityRef("CUSTOMER", row.customerId, origin))
                    .put("site", row.siteId?.let { entityRef("SITE", it, origin) }).put("equipment", row.equipmentId?.let { entityRef("EQUIPMENT", it, origin) })
                    .put("channel", row.channel).put("outcome", row.outcome).put("enteredInError", row.enteredInError).put("errorReason", row.errorReason))
            if (selection.includePrivate) value.getJSONObject("payload").put("privateNote", row.privateNote)
            records.put(value)
        }
        if (family == "CHANGE_HISTORY") dao.allChangeEntries().forEach { row ->
            if (!scopeMatches(selection.scope, row.customerId.orEmpty(), row.siteId, row.equipmentId, row.eventDate)) return@forEach
            val value = JSONObject().put("originWorkspaceId", originOf(family, row.id, origin)).put("sourceEntityId", sourceOf(family, row.id))
                .put("eventDateTime", row.eventDate).put("customer", row.customerId?.let { entityRef("CUSTOMER", it, origin) })
                .put("site", row.siteId?.let { entityRef("SITE", it, origin) }).put("equipment", row.equipmentId?.let { entityRef("EQUIPMENT", it, origin) })
                .put("payload", JSONObject().put("subjectType", row.subjectType).put("subjectId", row.subjectId)
                    .put("changeType", row.changeType).put("reason", row.reason).put("oldValue", row.oldValue).put("newValue", row.newValue)
                    .put("customerNameSnapshot", row.customerNameSnapshot).put("siteNameSnapshot", row.siteNameSnapshot).put("equipmentNameSnapshot", row.equipmentNameSnapshot))
            if (!selection.includePrivate) { value.getJSONObject("payload").remove("oldValue"); value.getJSONObject("payload").remove("newValue") }
            records.put(value)
        }
        val imported = dao.allTransferredHistoryEntries().filter { it.family == family }.filter { row ->
            scopeMatches(selection.scope, row.localCustomerId.orEmpty(), row.localSiteId, row.localEquipmentId, row.eventDateTime?.take(10))
        }
        imported.forEach { row ->
            val payload = JSONObject(row.payloadJson)
            if (!selection.includePrivate) when (family) {
                "FOLLOW_UPS" -> payload.remove("privatePlanningNote")
                "CONTACT_NOTES" -> payload.remove("privateNote")
                "CHANGE_HISTORY" -> { payload.remove("oldValue"); payload.remove("newValue") }
            }
            records.put(JSONObject().put("originWorkspaceId", row.originWorkspaceId).put("sourceEntityId", row.sourceEntityId)
                .put("sourceRevisionId", row.sourceRevisionId ?: JSONObject.NULL).put("eventDateTime", row.eventDateTime ?: JSONObject.NULL)
                .put("customer", row.localCustomerId?.let { entityRef("CUSTOMER", it, origin) })
                .put("site", row.localSiteId?.let { entityRef("SITE", it, origin) }).put("equipment", row.localEquipmentId?.let { entityRef("EQUIPMENT", it, origin) })
                .put("payload", payload))
        }
        return JSONObject().put("version", 1).put("records", records).toString().toByteArray(Charsets.UTF_8)
    }

    private suspend fun encodeEvidence(selection: ExportCenterSelection, origin: String, customerIds: Set<String>, siteIds: Set<String>): EncodedEvidence {
        val binaries = linkedMapOf<String, ByteArray>()
        val photos = JSONArray()
        val already = mutableSetOf<String>()
        fun add(row: JSONObject, bytes: ByteArray) {
            val sourceKey = transferEvidenceSourceKey(row.getString("originWorkspaceId"), row.getString("sourcePhotoId"), row.optNullable("sourceFinalRevisionId"))
            if (!already.add(sourceKey)) return
            if (!selection.includePrivate && row.getString("visibility") != "PUBLIC") return
            val derivative = AppOwnedImageNormalizer.workResultDerivative(bytes)
            val name = "binary-${sha256(sourceKey.toByteArray()).take(24)}"
            row.put("binaryName", name).put("sha256", sha256(derivative.bytes)).put("byteSize", derivative.bytes.size)
                .put("width", derivative.width).put("height", derivative.height).put("mimeType", "image/jpeg")
            binaries[name] = derivative.bytes
            photos.put(row)
        }
        // Finalized local photos are revision-frozen and remain distinct across corrections.
        dao.allFinalRecords().filterNot { it.voided }.forEach { record ->
            val visit = dao.visit(record.visitId) ?: return@forEach
            if (visit.customerId !in customerIds || visit.siteId !in siteIds) return@forEach
            val revisions = if (selection.includePreviousRevisions) dao.finalRevisions(record.id) else listOfNotNull(dao.finalRevision(record.currentRevisionId))
            revisions.forEach { revision -> dao.finalWorkItems(revision.id).forEach { work -> dao.finalPhotos(work.id).forEach { photo ->
                val source = readImage(photo.storedRelativePath, photo.sha256, photo.byteSize)
                    ?: dao.retainedImage("ATTACHMENT", photo.sourceAttachmentId)?.let { readImage(it.derivativeRelativePath, it.derivativeSha256, it.derivativeByteSize) }
                    ?: dao.retainedImage("FINAL_PHOTO", photo.id)?.let { readImage(it.derivativeRelativePath, it.derivativeSha256, it.derivativeByteSize) }
                    ?: dao.retainedImageByOriginalPath(photo.storedRelativePath)?.let { readImage(it.derivativeRelativePath, it.derivativeSha256, it.derivativeByteSize) }
                    ?: error("Final photo ${photo.id} has no verified owned copy")
                val row = JSONObject().put("originWorkspaceId", origin).put("sourcePhotoId", photo.sourceAttachmentId).put("sourceVisitId", visit.id)
                    .put("sourceWorkItemId", work.sourceWorkItemId).put("sourceFinalRevisionId", revision.id).put("originCustomerSourceId", sourceOf("CUSTOMER", visit.customerId))
                    .put("originCustomerWorkspaceId", originOf("CUSTOMER", visit.customerId, origin)).put("originSiteSourceId", sourceOf("SITE", visit.siteId))
                    .put("originSiteWorkspaceId", originOf("SITE", visit.siteId, origin)).put("originEquipmentSourceId", work.equipmentId?.let { sourceOf("EQUIPMENT", it) } ?: JSONObject.NULL)
                    .put("originEquipmentWorkspaceId", work.equipmentId?.let { originOf("EQUIPMENT", it, origin) } ?: JSONObject.NULL)
                    .put("serviceDate", revision.actualServiceDate).put("visitReference", revision.visitReference).put("serviceName", work.serviceName)
                    .put("customer", entityRef("CUSTOMER",visit.customerId,origin)).put("site",entityRef("SITE",visit.siteId,origin))
                    .put("equipment",work.equipmentId?.let{entityRef("EQUIPMENT",it,origin)})
                    .put("caption", photo.caption).put("visibility", photo.visibility).put("includedInCustomerReport", photo.includedInCustomerReport)
                add(row, source)
            } } }
        }
        // Standalone current/working evidence not already frozen into a final revision.
        val frozenSourceIds = dao.allFinalRecords().flatMap { record -> dao.finalRevisions(record.id).flatMap { revision -> dao.finalWorkItems(revision.id).flatMap { work -> dao.finalPhotos(work.id).map { it.sourceAttachmentId } } } }.toSet()
        val allVisits = dao.allVisits().associateBy { it.id }
        dao.allAttachments().filter { it.ownerType == "WORK_ITEM" && it.id !in frozenSourceIds }.forEach { photo ->
            val work = dao.workItem(photo.ownerId) ?: return@forEach
            val visit = allVisits[work.visitId] ?: return@forEach
            if (visit.customerId !in customerIds || visit.siteId !in siteIds || (selection.scope.equipmentId != null && work.equipmentId != selection.scope.equipmentId) || !selection.scope.matches(visit.customerId, visit.siteId, work.equipmentId, LocalDate.parse(visit.actualServiceDate))) return@forEach
            val source = readImage(photo.storedRelativePath, photo.sha256, photo.byteSize)
                ?: dao.retainedImage("ATTACHMENT", photo.id)?.let { readImage(it.derivativeRelativePath, it.derivativeSha256, it.derivativeByteSize) }
                ?: error("Photo ${photo.id} has no verified owned copy")
            val row = JSONObject().put("originWorkspaceId", origin).put("sourcePhotoId", photo.id).put("sourceVisitId", visit.id)
                .put("sourceWorkItemId", work.id).put("sourceFinalRevisionId", JSONObject.NULL).put("originCustomerSourceId", sourceOf("CUSTOMER", visit.customerId))
                .put("originCustomerWorkspaceId", originOf("CUSTOMER", visit.customerId, origin)).put("originSiteSourceId", sourceOf("SITE", visit.siteId))
                .put("originSiteWorkspaceId", originOf("SITE", visit.siteId, origin)).put("originEquipmentSourceId", work.equipmentId?.let { sourceOf("EQUIPMENT", it) } ?: JSONObject.NULL)
                .put("originEquipmentWorkspaceId", work.equipmentId?.let { originOf("EQUIPMENT", it, origin) } ?: JSONObject.NULL)
                .put("serviceDate", visit.actualServiceDate).put("visitReference", visit.reference).put("serviceName", work.serviceNameSnapshot)
                .put("customer", entityRef("CUSTOMER",visit.customerId,origin)).put("site",entityRef("SITE",visit.siteId,origin))
                .put("equipment",work.equipmentId?.let{entityRef("EQUIPMENT",it,origin)})
                .put("caption", photo.caption).put("visibility", photo.visibility).put("includedInCustomerReport", photo.includedInCustomerReport)
            add(row, source)
        }
        // Imported WORK_RESULT evidence already owns bounded JPEG derivatives.
        dao.reportableRemoteFinalResults().forEach { result -> dao.remoteResultPhotos(result.id).forEach { photo ->
            if (!selection.scope.matches(result.customerId.orEmpty(), result.localVisitId?.let { dao.visit(it)?.siteId }, result.localWorkItemId?.let { dao.workItem(it)?.equipmentId }, LocalDate.parse(result.serviceDate))) return@forEach
            val bytes = readImage(photo.relativePath, photo.sha256, photo.byteSize) ?: error("Imported result photo is missing")
            val provenance = JSONObject(result.provenanceJson)
            val row = JSONObject().put("originWorkspaceId", result.technicianId).put("sourcePhotoId", photo.sourcePhotoId).put("sourceVisitId", result.dispatchVisitId)
                .put("sourceWorkItemId", result.dispatchItemId).put("sourceFinalRevisionId", result.sourceFinalRevisionId)
                .put("originCustomerSourceId", JSONObject(result.customerSnapshotJson).optString("reference"))
                .put("originCustomerWorkspaceId", result.technicianId).put("originSiteSourceId", JSONObject(result.siteSnapshotJson).optString("reference"))
                .put("originSiteWorkspaceId", result.technicianId).put("originEquipmentSourceId", JSONObject(result.subjectSnapshotJson).optString("equipmentReference"))
                .put("originEquipmentWorkspaceId", result.technicianId).put("serviceDate", result.serviceDate).put("visitReference", provenance.optString("visitReference", result.dispatchVisitId.take(12)))
                .put("serviceName", provenance.optString("serviceName", "Assigned service")).put("caption", photo.caption).put("visibility", photo.visibility)
                .put("includedInCustomerReport", photo.includeInReport).put("localCustomerId", result.customerId).put("localSiteId", result.localVisitId?.let { dao.visit(it)?.siteId })
                .put("localEquipmentId", result.localWorkItemId?.let { dao.workItem(it)?.equipmentId })
            result.customerId?.let{row.put("customer",entityRef("CUSTOMER",it,origin))}
            result.localVisitId?.let{dao.visit(it)?.let{visit->row.put("site",entityRef("SITE",visit.siteId,origin))}}
            result.localWorkItemId?.let{dao.workItem(it)?.equipmentId?.let{equipmentId->row.put("equipment",entityRef("EQUIPMENT",equipmentId,origin))}}
            add(row, bytes)
        } }
        dao.allTransferredEvidence().forEach { photo ->
            if (!selection.scope.matches(photo.localCustomerId.orEmpty(), photo.localSiteId, photo.localEquipmentId, LocalDate.parse(photo.serviceDate))) return@forEach
            val bytes = readImage(photo.relativePath, photo.sha256, photo.byteSize) ?: error("Transferred evidence is missing")
            val row = JSONObject().put("originWorkspaceId", photo.originWorkspaceId).put("sourcePhotoId", photo.sourcePhotoId).put("sourceVisitId", photo.sourceVisitId)
                .put("sourceWorkItemId", photo.sourceWorkItemId).put("sourceFinalRevisionId", photo.sourceFinalRevisionId ?: JSONObject.NULL)
                .put("originCustomerSourceId", photo.provenanceJson.toJsonObject().optString("customerSourceEntityId"))
                .put("originCustomerWorkspaceId", photo.provenanceJson.toJsonObject().optString("customerOriginWorkspaceId"))
                .put("originSiteSourceId", photo.provenanceJson.toJsonObject().optString("siteSourceEntityId"))
                .put("originSiteWorkspaceId", photo.provenanceJson.toJsonObject().optString("siteOriginWorkspaceId"))
                .put("originEquipmentSourceId", photo.provenanceJson.toJsonObject().optString("equipmentSourceEntityId"))
                .put("originEquipmentWorkspaceId", photo.provenanceJson.toJsonObject().optString("equipmentOriginWorkspaceId"))
                .put("serviceDate", photo.serviceDate).put("visitReference", photo.visitReference).put("serviceName", photo.serviceName)
                .put("caption", photo.caption).put("visibility", photo.visibility).put("includedInCustomerReport", photo.includedInCustomerReport)
                .put("localCustomerId", photo.localCustomerId).put("localSiteId", photo.localSiteId).put("localEquipmentId", photo.localEquipmentId)
            photo.localCustomerId?.let{row.put("customer",entityRef("CUSTOMER",it,origin))}
            photo.localSiteId?.let{row.put("site",entityRef("SITE",it,origin))}
            photo.localEquipmentId?.let{row.put("equipment",entityRef("EQUIPMENT",it,origin))}
            add(row, bytes)
        }
        return EncodedEvidence(JSONObject().put("version", 1).put("photos", photos).toString().toByteArray(Charsets.UTF_8), binaries)
    }

    private suspend fun originOf(type: String, localId: String, localWorkspaceId: String): String =
        dao.dataTransferBindingsForLocal(type, localId).firstOrNull()?.originWorkspaceId ?: localWorkspaceId

    private suspend fun sourceOf(type: String, localId: String): String =
        dao.dataTransferBindingsForLocal(type, localId).firstOrNull()?.sourceEntityId ?: localId

    private suspend fun entityRef(type: String, localId: String, localWorkspaceId: String) = JSONObject()
        .put("originWorkspaceId", originOf(type, localId, localWorkspaceId)).put("sourceEntityId", sourceOf(type, localId))

    private fun scopeMatches(scope: ServiceLoopScopeFilter, customerId: String, siteId: String?, equipmentId: String?, date: String?): Boolean {
        if (scope.customerId != null && scope.customerId != customerId) return false
        if (scope.siteId != null && scope.siteId != siteId) return false
        if (scope.equipmentId != null && scope.equipmentId != equipmentId) return false
        val parsed = date?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return scope.fromDate == null && scope.toDate == null
        return (scope.fromDate == null || !parsed.isBefore(scope.fromDate)) && (scope.toDate == null || !parsed.isAfter(scope.toDate))
    }

    private fun readImage(path: String, hash: String, size: Long): ByteArray? {
        val root = filesRoot.canonicalFile
        val file = File(root, path).canonicalFile
        if (!file.path.startsWith(root.path + File.separator) || !file.isFile || file.length() != size || size <= 0) return null
        val bytes = file.readBytes()
        return bytes.takeIf { sha256(it) == hash }
    }

    private fun String.toJsonObject() = runCatching { JSONObject(this) }.getOrDefault(JSONObject())
    private fun JSONObject.optNullable(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun transferEvidenceSourceKey(origin: String, photo: String, revision: String?) = "$origin|$photo|${revision ?: "standalone"}"
}
