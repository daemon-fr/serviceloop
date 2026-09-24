package com.v16studio.serviceloop.data

import android.graphics.BitmapFactory
import com.v16studio.serviceloop.domain.ServiceLoopScopeFilter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.sync.withLock

/** Builds the native additive-sharing package from the same choices as readable Export Center. */
class DataTransferExportService(private val database: ServiceLoopDatabase, private val filesRoot: File) {
    private val dao = database.serviceLoopDao()

    private data class DirectoryDependencies(
        val customers: MutableSet<String> = linkedSetOf(),
        val sites: MutableSet<String> = linkedSetOf(),
        val equipment: MutableSet<String> = linkedSetOf(),
    )

    suspend fun export(selection: ExportCenterSelection): ByteArray = BusinessFileCoordinator.mutex.withLock {
        exportLocked(selection)
    }

    private suspend fun exportLocked(selection: ExportCenterSelection): ByteArray {
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
        val selectedCustomer = selection.scope.customerId?.let { dao.customer(it) ?: error("Selected customer is unavailable") }
        val selectedSite = selection.scope.siteId?.let { dao.site(it) ?: error("Selected site is unavailable") }
        val selectedEquipment = selection.scope.equipmentId?.let { dao.equipment(it) ?: error("Selected equipment is unavailable") }
        require(selectedCustomer == null || selectedSite == null || selectedSite.customerId == selectedCustomer.id) { "Selected Site is outside the Customer scope" }
        require(selectedSite == null || selectedEquipment == null || selectedEquipment.siteId == selectedSite.id) { "Selected Equipment is outside the Site scope" }
        require(selectedCustomer == null || selectedEquipment == null || dao.site(selectedEquipment.siteId)?.customerId == selectedCustomer.id) { "Selected Equipment is outside the Customer scope" }
        val scopedCustomerId = selectedCustomer?.id ?: selectedSite?.customerId ?: selectedEquipment?.let { dao.site(it.siteId)?.customerId }
        val scopedSiteId = selectedSite?.id ?: selectedEquipment?.siteId
        val scopedCustomers = customers.filter { scopedCustomerId == null || it.id == scopedCustomerId }
        val scopedCustomerIds = scopedCustomers.map { it.id }.toSet()
        val sites = dao.allSites().filter { it.customerId in scopedCustomerIds && (scopedSiteId == null || it.id == scopedSiteId) }
        require(selection.scope.siteId == null || sites.size == 1) { "Selected site is unavailable" }
        val scopedSiteIds = sites.map { it.id }.toSet()
        val equipment = dao.allEquipment().filter { it.siteId in scopedSiteIds && (selection.scope.equipmentId == null || it.id == selection.scope.equipmentId) }
        require(selection.scope.equipmentId == null || equipment.size == 1) { "Selected equipment is unavailable" }
        val scopedEquipmentIds = equipment.map { it.id }.toSet()

        val dependencies = DirectoryDependencies()
        val explicitlySelectedCustomers = if (ExportFamily.CUSTOMERS in requested) {
            scopedCustomers.filter { selection.includeInactive || it.state == "ACTIVE" }.map { it.id }.toSet()
        } else emptySet()
        val explicitlySelectedSites = if (ExportFamily.SITES in requested) {
            sites.filter { selection.includeInactive || it.state == "ACTIVE" }.map { it.id }.toSet()
        } else emptySet()
        val explicitlySelectedEquipment = if (ExportFamily.EQUIPMENT in requested) {
            equipment.filter { selection.includeInactive || it.state == "ACTIVE" }.map { it.id }.toSet()
        } else emptySet()
        explicitlySelectedCustomers.forEach { addDirectoryDependencies(dependencies, customerId = it) }
        explicitlySelectedSites.forEach { addDirectoryDependencies(dependencies, siteId = it) }
        explicitlySelectedEquipment.forEach { addDirectoryDependencies(dependencies, equipmentId = it) }

        val contactCustomerIds = if (ExportFamily.CONTACTS in requested) {
            scopedCustomers
                .filter { selection.includeInactive || it.state == "ACTIVE" }
                .filter { dao.customerContacts(it.id).isNotEmpty() }
                .map { it.id }
                .toSet()
        } else emptySet()
        contactCustomerIds.forEach { addDirectoryDependencies(dependencies, customerId = it) }

        val selectedPlans = if (wantsPlans) dao.allPlans().filter {
            it.equipmentId in scopedEquipmentIds && (selection.includeInactive || it.state == "ACTIVE")
        } else emptyList()
        selectedPlans.forEach { addDirectoryDependencies(dependencies, equipmentId = it.equipmentId) }

        val families = linkedMapOf<DataTransferFamily, ByteArray>()
        val localTemplates = dao.reusableTemplates()
        if (wantsTemplates || wantsPlans) {
            val usedTemplateIds = selectedPlans.mapNotNull { it.reusableTemplateId }.toSet()
            val selectedTemplateIds = (if (wantsTemplates) localTemplates.filter { it.state != "DELETED" && (selection.includeInactive || it.state == "ACTIVE") }.map { it.id }.toSet() else emptySet()) + usedTemplateIds
            if (selectedTemplateIds.isNotEmpty()) families[DataTransferFamily.INSPECTION_TEMPLATES] = encodeTemplates(selectedTemplateIds, identity, selection.includePrivate)
        }
        if (wantsPlans) families[DataTransferFamily.SERVICE_PLANS] = encodePlans(identity, selectedPlans)
        if (wantsPerformed) families[DataTransferFamily.PERFORMED_WORK] = encodePerformed(selection, identity, dependencies)
        if (wantsFollowUps) families[DataTransferFamily.FOLLOW_UPS] = encodeTransferredContext(selection, identity, "FOLLOW_UPS", dependencies)
        if (wantsContactNotes) families[DataTransferFamily.CONTACT_NOTES] = encodeTransferredContext(selection, identity, "CONTACT_NOTES", dependencies)
        if (wantsChanges) families[DataTransferFamily.CHANGE_HISTORY] = encodeTransferredContext(selection, identity, "CHANGE_HISTORY", dependencies)
        val evidence = if (wantsEvidence) encodeEvidence(selection, identity, dependencies) else null
        val binaries = evidence?.binaries.orEmpty()
        if (evidence != null) families[DataTransferFamily.EVIDENCE] = evidence.metadata
        if (hasRegisterContent(requested) || wantsPlans || wantsPerformed || wantsFollowUps || wantsContactNotes || wantsChanges || wantsEvidence) {
            families[DataTransferFamily.REGISTER] = encodeRegister(
                selection, identity, customers, sites, equipment,
                customerIds = explicitlySelectedCustomers + dependencies.customers,
                contactCustomerIds = contactCustomerIds,
                siteIds = explicitlySelectedSites + dependencies.sites,
                equipmentIds = explicitlySelectedEquipment + dependencies.equipment,
                includeCustomers = ExportFamily.CUSTOMERS in requested || requested.any { it in setOf(ExportFamily.CONTACTS, ExportFamily.SITES, ExportFamily.EQUIPMENT, ExportFamily.PLANS) } || wantsPerformed || wantsFollowUps || wantsContactNotes || wantsChanges || wantsEvidence,
                includeContacts = ExportFamily.CONTACTS in requested,
                includeSites = ExportFamily.SITES in requested || ExportFamily.EQUIPMENT in requested || wantsPlans || wantsPerformed || wantsFollowUps || wantsContactNotes || wantsChanges || wantsEvidence,
                includeEquipment = ExportFamily.EQUIPMENT in requested || wantsPlans || wantsPerformed || wantsFollowUps || wantsContactNotes || wantsChanges || wantsEvidence,
            )
        }
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

    private suspend fun addDirectoryDependencies(
        target: DirectoryDependencies,
        customerId: String? = null,
        siteId: String? = null,
        equipmentId: String? = null,
    ) {
        customerId?.takeIf { dao.customer(it) != null }?.let(target.customers::add)
        siteId?.let { id ->
            dao.site(id)?.let { site ->
                target.sites += site.id
                dao.customer(site.customerId)?.let { target.customers += it.id }
            }
        }
        equipmentId?.let { id ->
            dao.equipment(id)?.let { item ->
                target.equipment += item.id
                dao.site(item.siteId)?.let { site ->
                    target.sites += site.id
                    dao.customer(site.customerId)?.let { target.customers += it.id }
                }
            }
        }
    }

    private suspend fun encodeRegister(selection: ExportCenterSelection, origin: String, customers: List<CustomerEntity>, sites: List<SiteEntity>, equipment: List<EquipmentEntity>,
        customerIds: Set<String>, contactCustomerIds: Set<String>, siteIds: Set<String>, equipmentIds: Set<String>,
        includeCustomers: Boolean, includeContacts: Boolean, includeSites: Boolean, includeEquipment: Boolean): ByteArray {
        val root = JSONObject().put("version", 1).put("customers", JSONArray()).put("contacts", JSONArray()).put("sites", JSONArray()).put("equipment", JSONArray())
        if (includeCustomers) customers.filter { it.id in customerIds }.forEach { row ->
            val json = JSONObject().put("originWorkspaceId", originOf("CUSTOMER", row.id, origin)).put("sourceEntityId", sourceOf("CUSTOMER", row.id))
                .put("reference", row.reference).put("name", row.name).put("customerType", row.customerType).put("state", row.state)
                .put("contactName", row.contactName ?: JSONObject.NULL).put("phone", row.phone ?: JSONObject.NULL).put("email", row.email ?: JSONObject.NULL)
            if (selection.includePrivate) json.put("privateFieldsIncluded", true).put("privateNote", row.privateNote ?: JSONObject.NULL)
            root.getJSONArray("customers").put(json)
        }
        if (includeContacts) customers.filter { it.id in contactCustomerIds }.forEach { customer ->
            dao.customerContacts(customer.id).sortedWith(compareBy<CustomerContactEntity> { it.position }.thenBy { it.id }).forEachIndexed { index, row ->
                root.getJSONArray("contacts").put(JSONObject().put("originWorkspaceId", originOf("CUSTOMER_CONTACT", row.id, origin))
                    .put("sourceEntityId", sourceOf("CUSTOMER_CONTACT", row.id)).put("customerOriginWorkspaceId", originOf("CUSTOMER", row.customerId, origin))
                    .put("customerSourceEntityId", sourceOf("CUSTOMER", row.customerId)).put("position", index + 1).put("personName", row.personName ?: JSONObject.NULL)
                    .put("channel", row.channel).put("value", row.value).apply { if (selection.includePrivate) put("notes", row.notes ?: JSONObject.NULL) })
            }
        }
        if (includeSites) sites.filter { it.id in siteIds }.forEach { row ->
            val json = JSONObject().put("originWorkspaceId", originOf("SITE", row.id, origin)).put("sourceEntityId", sourceOf("SITE", row.id))
                .put("customerOriginWorkspaceId", originOf("CUSTOMER", row.customerId, origin)).put("customerSourceEntityId", sourceOf("CUSTOMER", row.customerId))
                .put("reference", row.reference).put("name", row.name).put("isDefault", row.isDefault).put("state", row.state)
                .put("address", row.address ?: JSONObject.NULL).put("contactName", row.contactName ?: JSONObject.NULL)
                .put("phone", row.phone ?: JSONObject.NULL).put("email", row.email ?: JSONObject.NULL)
            if (selection.includePrivate) json.put("privateFieldsIncluded", true).put("privateAccessNotes", row.privateAccessNotes ?: JSONObject.NULL)
            root.getJSONArray("sites").put(json)
        }
        if (includeEquipment) equipment.filter { it.id in equipmentIds }.forEach { row ->
            val json = JSONObject().put("originWorkspaceId", originOf("EQUIPMENT", row.id, origin)).put("sourceEntityId", sourceOf("EQUIPMENT", row.id))
                .put("siteOriginWorkspaceId", originOf("SITE", row.siteId, origin)).put("siteSourceEntityId", sourceOf("SITE", row.siteId))
                .put("reference", row.reference).put("name", row.name).put("state", row.state)
                .put("technicianIdentifier", row.technicianIdentifier ?: JSONObject.NULL).put("make", row.make ?: JSONObject.NULL)
                .put("model", row.model ?: JSONObject.NULL).put("serialNumber", row.serialNumber ?: JSONObject.NULL)
            if (selection.includePrivate) json.put("privateFieldsIncluded", true).put("privateNotes", row.privateNotes ?: JSONObject.NULL)
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
                sourceEntityId = binding?.sourceEntityId ?: template.id,
                state = template.state)
        }
        return InspectionTemplateCodec.encode(InspectionTemplateTransfer(Instant.now().toString(), entries))
    }

    private suspend fun encodePlans(origin: String, plans: List<ServicePlanEntity>): ByteArray {
        val rows = JSONArray()
        plans.forEach { row ->
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

    private suspend fun encodePerformed(selection: ExportCenterSelection, origin: String, dependencies: DirectoryDependencies): ByteArray {
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
            val revisions = if (selection.includePreviousRevisions) dao.finalRevisions(record.id) else listOfNotNull(dao.finalRevision(record.currentRevisionId))
            revisions.forEach { revision ->
                val customer = JSONObject().put("name", revision.customerName).put("reference", revision.customerReference)
                val site = JSONObject().put("name", revision.siteName).put("reference", revision.siteReference).put("address", revision.siteAddress)
                dao.finalWorkItems(revision.id).forEach { item ->
                    if (selection.scope.equipmentId != null && item.equipmentId != selection.scope.equipmentId) return@forEach
                    if (!selection.scope.matches(visit.customerId, visit.siteId, item.equipmentId, LocalDate.parse(revision.actualServiceDate))) return@forEach
                    addDirectoryDependencies(dependencies, visit.customerId, visit.siteId, item.equipmentId)
                    val payload = JSONObject().put("originWorkspaceId", origin).put("sourceVisitId", visit.id).put("sourceWorkItemId", item.sourceWorkItemId).put("sourceWorkItemPosition", item.position)
                        .put("sourceFinalRevisionId", revision.id).put("logicalResultId", "$origin:${visit.id}:${item.sourceWorkItemId}")
                        .put("sourceFinalRevisionNumber", revision.revisionNumber)
                        .put("supersedesSourceFinalRevisionId", revision.supersedesRevisionId ?: JSONObject.NULL)
                        .put("correctionReason", revision.correctionReason ?: JSONObject.NULL)
                        .put("publicNote", revision.publicNote ?: JSONObject.NULL)
                        .put("technicianId", origin).put("technicianName", revision.technicianName)
                        .put("technicianDesignation", revision.technicianDesignation ?: JSONObject.NULL)
                        .put("visitReference", revision.visitReference).put("serviceDate", revision.actualServiceDate)
                        .put("customerSnapshot", customer).put("siteSnapshot", site)
                        .put("subjectSnapshot", JSONObject().put("type", item.subjectType).put("equipmentName", item.equipmentName).put("equipmentReference", item.equipmentReference)
                            .put("equipmentIdentifier", item.equipmentIdentifier).put("make", item.equipmentMake).put("model", item.equipmentModel).put("serial", item.equipmentSerial).put("equipmentDescription", item.equipmentDescription))
                        .put("serviceName", item.serviceName).put("outcome", item.outcome).put("workPerformed", item.publicWorkNote)
                        .put("customer", entityRef("CUSTOMER", visit.customerId, origin)).put("site", entityRef("SITE", visit.siteId, origin))
                        .put("equipment", item.equipmentId?.let { entityRef("EQUIPMENT", it, origin) })
                        .put("notPerformedReason", item.notPerformedReason ?: JSONObject.NULL)
                        .put("checklist", checklistJson(item.id)).put("findings", findingsJson(item.id))
                        .put("parts", partsJson(item.id)).put("recurrence", JSONObject().put("planId", item.planId)
                            .put("capturedObligationId", item.capturedObligationId).put("fulfilledObligation", item.fulfilledObligation)
                            .put("oldDueDate", item.oldDueDate).put("nextDueDate", item.nextDueDate)
                            .put("intervalCount", item.intervalCount).put("intervalUnit", item.intervalUnit)
                            .put("nextDueDateCalculated", item.nextDueDateCalculated).put("nextDueOverrideReason", item.nextDueOverrideReason))
                        .put("recordedAt", Instant.ofEpochMilli(revision.recordedAtEpochMillis).toString())
                    payload.put("followUpCaptureState", if (item.followUpsSnapshotJson == null) "UNAVAILABLE_LEGACY" else "CAPTURED_AT_REVISION")
                    payload.put("followUps", item.followUpsSnapshotJson?.let(FinalFollowUpSnapshot::records)?.let {
                        if (selection.includePrivate) it else publicFollowUps(it)
                    } ?: JSONArray())
                    payload.put("sourcePhotos", JSONArray().also { array -> dao.finalPhotos(item.id).forEach { photo ->
                        if (selection.includePrivate || photo.visibility == "PUBLIC") array.put(JSONObject()
                            .put("sourcePhotoId", photo.sourceAttachmentId).put("sourceWorkItemId", item.sourceWorkItemId)
                            .put("position", photo.position).put("originalSha256", photo.sha256)
                            .put("originalByteSize", photo.byteSize).put("originalMimeType", photo.mimeType)
                            .put("caption", photo.caption ?: JSONObject.NULL).put("visibility", photo.visibility)
                            .put("includeInReport", photo.includedInCustomerReport).put("addedInCorrection", photo.addedInCorrection)
                            .put("addedAtEpochMillis", photo.addedAtEpochMillis ?: JSONObject.NULL))
                    } })
                    val assignment = database.dispatchDao().finalDispatchVisit(revision.id)
                    val assignedItem = database.dispatchDao().finalDispatchItems(revision.id).firstOrNull { it.finalWorkItemId == item.id }
                    if (assignment != null && assignedItem != null) {
                        val binding = database.dispatchDao().visitBinding(assignment.dispatchVisitId)
                        payload.put("assignmentProvenance", JSONObject()
                            .put("issuerId", assignment.assignmentIssuerId ?: binding?.assignmentIssuerId ?: JSONObject.NULL)
                            .put("dispatchVisitId", assignment.dispatchVisitId).put("dispatchItemId", assignedItem.dispatchItemId)
                            .put("generation", assignment.generation)
                            .put("materialHash", assignment.assignmentMaterialHash ?: binding?.appliedMaterialHash ?: JSONObject.NULL))
                    }
                    if (selection.includePrivate) payload.put("internalNotes", item.privateInternalNote ?: JSONObject.NULL)
                        .put("finalInternalNote", revision.privateInternalNote ?: JSONObject.NULL)
                    val sourceOrigin = originOf("PERFORMED_WORK", item.sourceWorkItemId, origin)
                    payload.put("originWorkspaceId", sourceOrigin)
                    val sourceVisit = if (sourceOrigin == origin) visit.id else sourceOf("VISIT", visit.id)
                    payload.put("sourceVisitId", sourceVisit)
                    addResult(payload, sourceOrigin, sourceVisit, revision.visitReference, revision.actualServiceDate, customer, site)
                }
            }
        }
        effectiveImportedFinalResults(dao.appliedRemoteFinalResultsIncludingVoids(), dao.allTransferredFinalResults(), selection.includePreviousRevisions).forEach { imported ->
            val result = imported.transferred ?: imported.remote ?: return@forEach
            val service = when (imported.kind) {
                ImportedFinalKind.WORK_RESULT -> encodeRemoteResult(result as RemoteFinalResultEntity, selection.includePrivate, origin)
                ImportedFinalKind.DATA_TRANSFER -> encodeTransferredResult(result as TransferredFinalResultEntity, selection.includePrivate)
                else -> null
            } ?: return@forEach
            val customerId = service.optString("localCustomerId").takeIf { it.isNotBlank() }
            val siteId = service.optString("localSiteId").takeIf { it.isNotBlank() }
            val equipmentId = service.optString("localEquipmentId").takeIf { it.isNotBlank() }
            val date = service.getString("serviceDate")
            if (!selection.scope.matches(customerId ?: "", siteId, equipmentId, LocalDate.parse(date))) return@forEach
            addDirectoryDependencies(dependencies, customerId, siteId, equipmentId)
            val originId = service.getString("originWorkspaceId")
            val visitId = service.getString("sourceVisitId")
            addResult(service, originId, visitId, service.getString("visitReference"), date,
                JSONObject(service.getString("customerSnapshot")), JSONObject(service.getString("siteSnapshot")))
        }
        return JSONObject().put("version", 2).put("visits", JSONArray(groups.values.toList())).toString().toByteArray(Charsets.UTF_8)
    }

    private suspend fun checklistJson(workItemId: String) = JSONArray().also { array -> dao.finalChecklistItems(workItemId).forEach { item ->
        array.put(JSONObject().put("position", item.position).put("label", item.label).put("responseType", item.responseType).put("unit", item.unit)
            .put("required", item.required).put("disposition", item.disposition).put("textValue", item.textValue).put("numberValue", item.numberValue).put("reason", item.reason))
    } }
    private suspend fun findingsJson(workItemId: String) = JSONArray().also { array -> dao.finalChecklistItems(workItemId).filter { it.disposition == "ISSUE_FOUND" }.forEach { item -> array.put(JSONObject().put("position", item.position).put("label", item.label).put("description", item.reason)) } }
    private suspend fun partsJson(workItemId: String) = JSONArray().also { array -> dao.finalParts(workItemId).forEach { item -> array.put(JSONObject().put("position", item.position).put("description", item.description).put("quantity", item.quantity).put("unit", item.unit)) } }

    private suspend fun encodeRemoteResult(value: RemoteFinalResultEntity, includePrivate: Boolean, localWorkspaceId: String): JSONObject {
        val accepted = value.sourcePayloadJson
            ?: throw IllegalStateException("This legacy work result needs its original package re-imported before a lossless native relay")
        require(FinalSourceSnapshot.fingerprintVersion(accepted) == 2) { "This work result needs its original v2 package before native relay" }
        val source = FinalSourceSnapshot.record(accepted, "WORK_RESULT")
        require(source.has("sourceVisitId") && source.has("sourceWorkItemId") && source.has("sourceFinalRevisionNumber")) {
            "This legacy work result lacks source execution identity; re-import the original v2 package"
        }
        val sourceWork = source.getJSONObject("workSnapshot")
        val localVisit = value.localVisitId?.let { dao.visit(it) }
        val localWork = value.localWorkItemId?.let { dao.workItem(it) }
        val work = JSONObject().put("originWorkspaceId", source.getString("originWorkspaceId")).put("sourceVisitId", source.getString("sourceVisitId"))
            .put("sourceWorkItemId", source.getString("sourceWorkItemId")).put("sourceWorkItemPosition", source.getInt("sourceWorkItemPosition"))
            .put("sourceFinalRevisionId", source.getString("sourceFinalRevisionId"))
            .put("sourceFinalRevisionNumber", source.getInt("sourceFinalRevisionNumber"))
            .put("supersedesSourceFinalRevisionId", source.opt("supersedesSourceFinalRevisionId") ?: JSONObject.NULL)
            .put("correctionReason", source.opt("correctionReason") ?: JSONObject.NULL)
            .put("publicNote", source.opt("publicNote") ?: JSONObject.NULL)
            .put("logicalResultId", source.getString("resultId"))
            .put("technicianId", source.getString("technicianId")).put("technicianName", source.getString("technicianName"))
            .put("technicianDesignation", source.opt("technicianDesignation") ?: JSONObject.NULL)
            .put("visitReference", source.getString("visitReference")).put("serviceDate", source.getString("serviceDate"))
            .put("customerSnapshot", source.getJSONObject("customerSnapshot")).put("siteSnapshot", source.getJSONObject("siteSnapshot"))
            .put("subjectSnapshot", source.getJSONObject("subjectSnapshot"))
            .put("serviceName", sourceWork.getString("serviceName")).put("outcome", source.getString("outcome"))
            .put("workPerformed", sourceWork.opt("publicWork") ?: JSONObject.NULL)
            .put("notPerformedReason", sourceWork.opt("notPerformedReason") ?: JSONObject.NULL)
            .put("checklist", source.getJSONArray("checklist")).put("findings", source.getJSONArray("findings"))
            .put("parts", source.getJSONArray("parts")).put("recurrence", source.getJSONObject("recurrence"))
            .put("recordedAt", source.getString("recordedAt"))
            .put("followUpCaptureState", source.getString("followUpCaptureState"))
            .put("followUps", if (includePrivate) source.getJSONArray("followUps") else publicFollowUps(source.getJSONArray("followUps")))
            .put("sourcePhotos", if (includePrivate) source.getJSONArray("sourcePhotos") else publicSourcePhotos(source.getJSONArray("sourcePhotos")))
            .put("assignmentProvenance", JSONObject().put("issuerId", source.getString("assignmentIssuerId"))
                .put("dispatchVisitId", source.getString("dispatchVisitId")).put("dispatchItemId", source.getString("dispatchItemId"))
                .put("generation", source.getInt("assignmentGeneration")).put("materialHash", source.getString("assignmentMaterialHash")))
            .put("localCustomerId", value.customerId).put("localSiteId", localVisit?.siteId).put("localEquipmentId", localWork?.equipmentId)
        value.customerId?.let { work.put("customer", entityRef("CUSTOMER", it, localWorkspaceId)) }
        localVisit?.let { work.put("site", entityRef("SITE", it.siteId, localWorkspaceId)) }
        localWork?.equipmentId?.let { work.put("equipment", entityRef("EQUIPMENT", it, localWorkspaceId)) }
        if (includePrivate) work.put("internalNotes", sourceWork.opt("privateInternalNote") ?: JSONObject.NULL)
            .put("finalInternalNote", source.opt("privateInternalNote") ?: JSONObject.NULL)
        return work
    }

    private fun publicFollowUps(values: JSONArray) = JSONArray().also { public ->
        for (index in 0 until values.length()) {
            val row = values.getJSONObject(index)
            public.put(JSONObject().put("sourceId", row.opt("sourceId") ?: JSONObject.NULL)
                .put("type", row.getString("type")).put("title", row.getString("title"))
                .put("dueDate", row.opt("dueDate") ?: JSONObject.NULL).put("state", row.getString("state")))
        }
    }

    private fun publicSourcePhotos(values: JSONArray) = JSONArray().also { public ->
        for (index in 0 until values.length()) {
            val row = values.getJSONObject(index)
            if (row.getString("visibility") == "PUBLIC") public.put(row)
        }
    }

    private fun encodeTransferredResult(value: TransferredFinalResultEntity, includePrivate: Boolean): JSONObject {
        val source = value.sourcePayloadJson?.let { FinalSourceSnapshot.record(it, "PERFORMED_WORK") }
            ?: throw IllegalStateException("This legacy performed record needs its original package re-imported before a lossless native relay")
        if (includePrivate) return source
        return JSONObject(source.toString()).apply {
            remove("internalNotes")
            remove("finalInternalNote")
            put("followUps", publicFollowUps(source.getJSONArray("followUps")))
            put("sourcePhotos", publicSourcePhotos(source.getJSONArray("sourcePhotos")))
        }
    }

    private suspend fun encodeTransferredContext(selection: ExportCenterSelection, origin: String, family: String, dependencies: DirectoryDependencies): ByteArray {
        val records = JSONArray()
        if (family == "FOLLOW_UPS") dao.allFollowUps().forEach { row ->
            if (!scopeMatches(selection.scope, row.customerId, row.siteId, row.equipmentId, row.dueDate)) return@forEach
            addDirectoryDependencies(dependencies, row.customerId, row.siteId, row.equipmentId)
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
        if (family == "CONTACT_NOTES") {
          val businessZone = java.time.ZoneId.of(dao.businessProfile()?.zoneId ?: "UTC")
          dao.allContactNotes().forEach { row ->
            val businessDate = Instant.ofEpochMilli(row.occurredAtEpochMillis).atZone(businessZone).toLocalDate().toString()
            if (!scopeMatches(selection.scope, row.customerId, row.siteId, row.equipmentId, businessDate)) return@forEach
            addDirectoryDependencies(dependencies, row.customerId, row.siteId, row.equipmentId)
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
        }
        if (family == "CHANGE_HISTORY") dao.allChangeEntries().forEach { row ->
            if (!scopeMatches(selection.scope, row.customerId.orEmpty(), row.siteId, row.equipmentId, row.eventDate)) return@forEach
            addDirectoryDependencies(dependencies, row.customerId, row.siteId, row.equipmentId)
            val value = JSONObject().put("originWorkspaceId", originOf(family, row.id, origin)).put("sourceEntityId", sourceOf(family, row.id))
                .put("eventDateTime", row.eventDate).put("customer", row.customerId?.let { entityRef("CUSTOMER", it, origin) })
                .put("site", row.siteId?.let { entityRef("SITE", it, origin) }).put("equipment", row.equipmentId?.let { entityRef("EQUIPMENT", it, origin) })
                .put("payload", JSONObject().put("subjectType", row.subjectType).put("subjectId", row.subjectId)
                    .put("changeType", row.changeType).put("reason", row.reason).put("oldValue", row.oldValue).put("newValue", row.newValue)
                    .put("customerNameSnapshot", row.customerNameSnapshot).put("siteNameSnapshot", row.siteNameSnapshot).put("equipmentNameSnapshot", row.equipmentNameSnapshot))
            if (!selection.includePrivate) { value.getJSONObject("payload").remove("oldValue"); value.getJSONObject("payload").remove("newValue") }
            records.put(value)
        }
        val importedZone = java.time.ZoneId.of(dao.businessProfile()?.zoneId ?: "UTC")
        val imported = dao.allTransferredHistoryEntries().filter { it.family == family }.filter { row ->
            val scopedDate = if (family == "CONTACT_NOTES") row.eventDateTime?.let { text ->
                runCatching { Instant.parse(text) }.getOrElse {
                    java.time.OffsetDateTime.parse(text).toInstant()
                }.atZone(importedZone).toLocalDate().toString()
            } else row.eventDateTime?.take(10)
            scopeMatches(selection.scope, row.localCustomerId.orEmpty(), row.localSiteId, row.localEquipmentId, scopedDate)
        }
        imported.forEach { row ->
            addDirectoryDependencies(dependencies, row.localCustomerId, row.localSiteId, row.localEquipmentId)
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

    private suspend fun encodeEvidence(selection: ExportCenterSelection, origin: String, dependencies: DirectoryDependencies): EncodedEvidence {
        val binaries = linkedMapOf<String, ByteArray>()
        val photos = JSONArray()
        val already = mutableSetOf<String>()
        val derivativeStore = CanonicalFinalPhotoDerivative(database, filesRoot)
        fun add(row: JSONObject, bytes: ByteArray, retained: AppOwnedImageNormalizer.TransportDerivative? = null): Boolean {
            val sourceKey = transferEvidenceSourceKey(row.getString("originWorkspaceId"), row.getString("sourcePhotoId"), row.optNullable("sourceFinalRevisionId"))
            if (!selection.includePrivate && row.getString("visibility") != "PUBLIC") return false
            if (!already.add(sourceKey)) return false
            val derivative = retained ?: AppOwnedImageNormalizer.workResultDerivative(bytes)
            val name = "binary-${sha256(sourceKey.toByteArray()).take(24)}"
            row.put("binaryName", name).put("sha256", sha256(derivative.bytes)).put("byteSize", derivative.bytes.size)
                .put("width", derivative.width).put("height", derivative.height).put("mimeType", "image/jpeg")
            binaries[name] = derivative.bytes
            photos.put(row)
            return true
        }
        fun verifiedDerivative(bytes: ByteArray, width: Int, height: Int): AppOwnedImageNormalizer.TransportDerivative {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bytes.size in 1..WorkResultPackageCodec.MAX_PHOTO_BYTES && bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() && width in 1..1200 && height in 1..1200 &&
                bounds.outWidth == width && bounds.outHeight == height) { "Retained evidence derivative is invalid" }
            return AppOwnedImageNormalizer.TransportDerivative(bytes, width, height)
        }
        // Finalized local photos are revision-frozen and remain distinct across corrections.
        dao.allFinalRecords().filterNot { it.voided }.forEach { record ->
            val visit = dao.visit(record.visitId) ?: return@forEach
            val revisions = if (selection.includePreviousRevisions) dao.finalRevisions(record.id) else listOfNotNull(dao.finalRevision(record.currentRevisionId))
            revisions.forEach { revision -> dao.finalWorkItems(revision.id).forEach workLoop@{ work ->
                if (!selection.scope.matches(visit.customerId, visit.siteId, work.equipmentId, LocalDate.parse(revision.actualServiceDate))) return@workLoop
                dao.finalPhotos(work.id).forEach { photo ->
                val derivative = derivativeStore.bytes(photo)
                val row = JSONObject().put("originWorkspaceId", origin).put("sourcePhotoId", photo.sourceAttachmentId).put("sourceVisitId", visit.id)
                    .put("sourceWorkItemId", work.sourceWorkItemId).put("sourceFinalRevisionId", revision.id).put("originCustomerSourceId", sourceOf("CUSTOMER", visit.customerId))
                    .put("originCustomerWorkspaceId", originOf("CUSTOMER", visit.customerId, origin)).put("originSiteSourceId", sourceOf("SITE", visit.siteId))
                    .put("originSiteWorkspaceId", originOf("SITE", visit.siteId, origin)).put("originEquipmentSourceId", work.equipmentId?.let { sourceOf("EQUIPMENT", it) } ?: JSONObject.NULL)
                    .put("originEquipmentWorkspaceId", work.equipmentId?.let { originOf("EQUIPMENT", it, origin) } ?: JSONObject.NULL)
                    .put("serviceDate", revision.actualServiceDate).put("visitReference", revision.visitReference).put("serviceName", work.serviceName)
                    .put("customer", entityRef("CUSTOMER",visit.customerId,origin)).put("site",entityRef("SITE",visit.siteId,origin))
                    .put("equipment",work.equipmentId?.let{entityRef("EQUIPMENT",it,origin)})
                    .put("caption", photo.caption).put("visibility", photo.visibility).put("includedInCustomerReport", photo.includedInCustomerReport)
                if (add(row, derivative.bytes, derivative))
                    addDirectoryDependencies(dependencies, visit.customerId, visit.siteId, work.equipmentId)
            } }
            }
        }
        // Standalone current/working evidence not already frozen into a final revision.
        val frozenSourceIds = dao.allFinalRecords().flatMap { record -> dao.finalRevisions(record.id).flatMap { revision -> dao.finalWorkItems(revision.id).flatMap { work -> dao.finalPhotos(work.id).map { it.sourceAttachmentId } } } }.toSet()
        val allVisits = dao.allVisits().associateBy { it.id }
        dao.allAttachments().filter { it.ownerType == "WORK_ITEM" && it.id !in frozenSourceIds }.forEach { photo ->
            val work = dao.workItem(photo.ownerId) ?: return@forEach
            val visit = allVisits[work.visitId] ?: return@forEach
            if (!selection.scope.matches(visit.customerId, visit.siteId, work.equipmentId, LocalDate.parse(visit.actualServiceDate))) return@forEach
            val retained = dao.retainedImage("ATTACHMENT", photo.id)
            val source = if (retained != null) readImage(retained.derivativeRelativePath, retained.derivativeSha256, retained.derivativeByteSize)
                ?: error("Retained photo ${photo.id} is missing") else readImage(photo.storedRelativePath, photo.sha256, photo.byteSize)
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
            if (add(row, source, retained?.let { verifiedDerivative(source, it.derivativeWidth, it.derivativeHeight) }))
                addDirectoryDependencies(dependencies, visit.customerId, visit.siteId, work.equipmentId)
        }
        // Imported WORK_RESULT evidence already owns bounded JPEG derivatives.
        dao.reportableRemoteFinalResults().forEach { result -> dao.remoteResultPhotos(result.id).forEach { photo ->
            val siteId = result.localVisitId?.let { dao.visit(it)?.siteId }
            val equipmentId = result.localWorkItemId?.let { dao.workItem(it)?.equipmentId }
            if (!selection.scope.matches(result.customerId.orEmpty(), siteId, equipmentId, LocalDate.parse(result.serviceDate))) return@forEach
            val bytes = readImage(photo.relativePath, photo.sha256, photo.byteSize) ?: error("Imported result photo is missing")
            val source = result.sourcePayloadJson?.let { FinalSourceSnapshot.record(it, "WORK_RESULT") }
            require(source != null && source.has("sourceVisitId") && source.has("sourceWorkItemId")) {
                "This legacy result photo lacks source execution identity; re-import the original v2 package before native relay"
            }
            val row = JSONObject().put("originWorkspaceId", source.getString("originWorkspaceId")).put("sourcePhotoId", photo.sourcePhotoId)
                .put("sourceVisitId", source.getString("sourceVisitId")).put("sourceWorkItemId", source.getString("sourceWorkItemId"))
                .put("sourceFinalRevisionId", source.getString("sourceFinalRevisionId"))
                .put("originCustomerSourceId", JSONObject.NULL).put("originCustomerWorkspaceId", JSONObject.NULL)
                .put("originSiteSourceId", JSONObject.NULL).put("originSiteWorkspaceId", JSONObject.NULL)
                .put("originEquipmentSourceId", JSONObject.NULL).put("originEquipmentWorkspaceId", JSONObject.NULL)
                .put("serviceDate", source.getString("serviceDate")).put("visitReference", source.getString("visitReference"))
                .put("serviceName", source.getJSONObject("workSnapshot").getString("serviceName"))
                .put("caption", photo.caption).put("visibility", photo.visibility)
                .put("includedInCustomerReport", photo.includeInReport).put("localCustomerId", result.customerId).put("localSiteId", result.localVisitId?.let { dao.visit(it)?.siteId })
                .put("localEquipmentId", result.localWorkItemId?.let { dao.workItem(it)?.equipmentId })
            result.customerId?.let{row.put("customer",entityRef("CUSTOMER",it,origin))}
            result.localVisitId?.let{dao.visit(it)?.let{visit->row.put("site",entityRef("SITE",visit.siteId,origin))}}
            result.localWorkItemId?.let{dao.workItem(it)?.equipmentId?.let{equipmentId->row.put("equipment",entityRef("EQUIPMENT",equipmentId,origin))}}
            if (add(row, bytes, verifiedDerivative(bytes, photo.width, photo.height))) addDirectoryDependencies(dependencies, result.customerId, siteId, equipmentId)
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
            if (add(row, bytes, verifiedDerivative(bytes, photo.width, photo.height))) addDirectoryDependencies(dependencies, photo.localCustomerId, photo.localSiteId, photo.localEquipmentId)
        }
        return EncodedEvidence(JSONObject().put("version", 1).put("photos", photos).toString().toByteArray(Charsets.UTF_8), binaries)
    }

    private suspend fun sourceBinding(type: String, localId: String): DataTransferBindingEntity? {
        val aliases = dao.dataTransferBindingsForLocal(type, localId)
            .sortedWith(compareBy<DataTransferBindingEntity> { it.originWorkspaceId }.thenBy { it.sourceEntityId })
        require(aliases.map { it.originWorkspaceId }.distinct().size <= 1) {
            "Several source origins map to this $type; choose an unambiguous source before native export"
        }
        return aliases.firstOrNull()
    }

    private suspend fun originOf(type: String, localId: String, localWorkspaceId: String): String =
        sourceBinding(type, localId)?.originWorkspaceId ?: localWorkspaceId

    private suspend fun sourceOf(type: String, localId: String): String =
        sourceBinding(type, localId)?.sourceEntityId ?: localId

    private suspend fun entityRef(type: String, localId: String, localWorkspaceId: String): JSONObject {
        val binding = sourceBinding(type, localId)
        return JSONObject().put("originWorkspaceId", binding?.originWorkspaceId ?: localWorkspaceId)
            .put("sourceEntityId", binding?.sourceEntityId ?: localId)
    }

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
    private fun transferEvidenceSourceKey(origin: String, photo: String, revision: String?) = SourceIdentityKeys.evidence(origin, photo, revision)
}
