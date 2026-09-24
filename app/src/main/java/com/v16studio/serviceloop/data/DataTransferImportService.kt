package com.v16studio.serviceloop.data

import android.graphics.BitmapFactory
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class DataTransferClassification { NEW, MATCH_EXISTING, ALREADY_CURRENT, NEW_HISTORY, ALREADY_IMPORTED, CONFLICT }

internal enum class SourceEntityType { CUSTOMER, SITE, EQUIPMENT, CUSTOMER_CONTACT, SERVICE_PLAN, INSPECTION_TEMPLATE }
internal data class SourceEntityKey(
    val originWorkspaceId: String,
    val entityType: SourceEntityType,
    val sourceEntityId: String,
) {
    val first get() = originWorkspaceId
    val second get() = sourceEntityId
}

data class DataTransferImportItem(
    val family: DataTransferFamily,
    val sourceKey: String,
    val classification: DataTransferClassification,
    val description: String,
    val conflictReason: String? = null,
)

data class DataTransferImportPreview(
    val payload: DataTransferPayload,
    val packageId: String,
    val counts: Map<DataTransferFamily, Int>,
    val items: List<DataTransferImportItem>,
    val templatePreview: InspectionTemplateImportPreview?,
) {
    val conflicts: List<DataTransferImportItem> get() = items.filter { it.classification == DataTransferClassification.CONFLICT }
    fun canImport(createSeparateTemplates: Set<String> = emptySet()): Boolean =
        conflicts.all { it.family == DataTransferFamily.INSPECTION_TEMPLATES && it.sourceKey in createSeparateTemplates }
}

/** Previews untrusted DATA_TRANSFER v2 and applies a trusted, additive, all-or-nothing merge. */
class DataTransferImportService(private val database: ServiceLoopDatabase, private val filesRoot: File) {
    private val dao = database.serviceLoopDao()
    private val templateExchange = InspectionTemplateExchangeService(database)

    private data class CurrentPlan(
        val family: DataTransferFamily,
        val type: String,
        val json: JSONObject,
        val origin: String,
        val sourceId: String,
        val localId: String,
        val classification: DataTransferClassification,
        val fingerprint: String,
        val parentLocalId: String? = null,
    )
    private data class ParsedHistory(
        val family: DataTransferFamily,
        val identity: String,
        val source: JSONObject,
        val fingerprint: String,
        val classification: DataTransferClassification,
    )
    private data class ParsedEvidence(val value: JSONObject, val bytes: ByteArray, val key: String, val hash: String, val classification: DataTransferClassification)
    private data class PlanSet(
        val current: List<CurrentPlan>,
        val history: List<ParsedHistory>,
        val evidence: List<ParsedEvidence>,
        val templatePreview: InspectionTemplateImportPreview?,
        val templateEntries: List<InspectionTemplateTransferEntry>,
        val mapping: Map<SourceEntityKey, String>,
        val items: List<DataTransferImportItem>,
        val counts: Map<DataTransferFamily, Int>,
    )

    suspend fun preview(bytes: ByteArray): DataTransferImportPreview = preview(DataTransferCodec.decode(bytes))

    suspend fun preview(payload: DataTransferPayload): DataTransferImportPreview {
        val plan = buildPlan(payload, emptySet())
        return DataTransferImportPreview(payload, packageId(payload), plan.counts, plan.items, plan.templatePreview)
    }

    suspend fun import(preview: DataTransferImportPreview, createSeparateTemplates: Set<String> = emptySet()): DataTransferImportPreview {
        require(preview.canImport(createSeparateTemplates)) { "Resolve the listed data-transfer conflicts before importing" }
        val payload = preview.payload
        val initial = buildPlan(payload, createSeparateTemplates)
        require(initial.items.none { item -> item.classification == DataTransferClassification.CONFLICT &&
            !(item.family == DataTransferFamily.INSPECTION_TEMPLATES && item.sourceKey in createSeparateTemplates) }) { "Resolve the listed data-transfer conflicts before importing" }
        return BusinessFileCoordinator.mutex.withLock {
            val createdFiles = mutableListOf<File>()
            try {
                val staged = stageEvidence(initial.evidence, createdFiles)
                val importedAt = System.currentTimeMillis()
                database.withTransaction {
                    ServiceLoopPeerTrustStore(database).requireTrustedInCurrentTransaction(payload.exporterId)
                    val current = buildPlan(payload, createSeparateTemplates)
                    require(current.items.none { item -> item.classification == DataTransferClassification.CONFLICT &&
                        !(item.family == DataTransferFamily.INSPECTION_TEMPLATES && item.sourceKey in createSeparateTemplates) }) { "Data changed while the import was being prepared; review the preview again" }
                    applyCurrent(current.current, payload.options, importedAt)
                    val templateLocalIds = applyTemplates(current, payload, createSeparateTemplates, importedAt)
                    applyPlans(current.current, current.mapping, templateLocalIds, importedAt)
                    applyHistory(current.history, payload, current.mapping, staged, importedAt)
                    applyEvidence(current.evidence, payload, current.mapping, staged, importedAt)
                    reconcileEvidenceAssociations()
                    if (current.current.any { it.classification == DataTransferClassification.NEW || it.classification == DataTransferClassification.MATCH_EXISTING } ||
                        current.history.any { it.classification == DataTransferClassification.NEW_HISTORY } || current.evidence.any { it.classification == DataTransferClassification.NEW_HISTORY }) {
                        dao.recoveryMetadata()?.let { metadata -> dao.upsertRecoveryMetadata(metadata.copy(
                            firstBusinessWriteAtEpochMillis = metadata.firstBusinessWriteAtEpochMillis ?: importedAt,
                            lastBusinessWriteAtEpochMillis = importedAt,
                        )) }
                    }
                    DataTransferImportPreview(payload, preview.packageId, current.counts, current.items, current.templatePreview)
                }
            } catch (failure: Throwable) {
                withContext(NonCancellable) {
                    createdFiles.forEach { file ->
                        val path = relative(file)
                        if (dao.transferredEvidenceReferenceCount(path) == 0) check(file.delete() || !file.exists())
                    }
                }
                throw failure
            }
        }
    }

    private suspend fun buildPlan(payload: DataTransferPayload, createSeparateTemplates: Set<String>): PlanSet {
        require(payload.sourceWorkspaceId.isNotBlank() && TechnicianIdCodec.normalize(payload.sourceWorkspaceId) == payload.sourceWorkspaceId) { "Invalid source workspace" }
        require(TechnicianIdCodec.normalize(payload.exporterId) == payload.exporterId) { "Invalid exporting ServiceLoop ID" }
        val current = mutableListOf<CurrentPlan>()
        val history = mutableListOf<ParsedHistory>()
        val items = mutableListOf<DataTransferImportItem>()
        val mapping = linkedMapOf<SourceEntityKey, String>()
        val register = payload.families[DataTransferFamily.REGISTER]?.let { JSONObject(it.toString(Charsets.UTF_8)) }
        validateRegister(register)
        if (register != null) {
            val customers = register.getJSONArray("customers")
            for (i in 0 until customers.length()) {
                val row = withNullableFields(customers.getJSONObject(i), "contactName", "phone", "email"); val id = sourceKey(row,"CUSTOMER"); val fp = fingerprint(row)
                val bound = dao.dataTransferBinding(id.first, "CUSTOMER", id.second)
                val target = bound?.localEntityId?.let { dao.customer(it) }
                val same = target?.let { fingerprint(customerJson(it, row)) == bound?.appliedSourceFingerprint && bound.appliedSourceFingerprint == fp } == true
                val candidate = if (bound == null) dao.allCustomers().firstOrNull { it.reference == row.getString("reference") } else null
                val equivalent = candidate?.let { fingerprint(customerJson(it, row)) == fp } == true
                val classification = classifyBound(bound, target != null, same, candidate != null, equivalent)
                val localId = target?.id ?: if (candidate != null && classification == DataTransferClassification.MATCH_EXISTING) candidate.id else UUID.randomUUID().toString()
                val item = currentPlan(DataTransferFamily.REGISTER, "CUSTOMER", row, localId, classification, fp)
                current += item; mapping[id] = localId; items += display(item, "Customer ${row.optString("reference")}")
            }
            val sites = register.getJSONArray("sites")
            for (i in 0 until sites.length()) {
                val row = withNullableFields(sites.getJSONObject(i), "address", "contactName", "phone", "email"); val id = sourceKey(row,"SITE"); val customerKey = parentKey(row, "customer")
                val customerId = mapping[customerKey] ?: resolveBinding(customerKey, "CUSTOMER")
                if (customerId == null) { current += conflictPlan(DataTransferFamily.REGISTER,"SITE",row,"Site has no resolvable Customer dependency"); items += display(current.last(),"Site ${row.optString("reference")}"); continue }
                val bound = dao.dataTransferBinding(id.first, "SITE", id.second); val target = bound?.localEntityId?.let { dao.site(it) }
                val fp = fingerprint(row); val same = target?.let { it.customerId == customerId && fingerprint(siteJson(it, row)) == bound?.appliedSourceFingerprint && bound.appliedSourceFingerprint == fp } == true
                val candidate = if (bound == null) dao.allSites().firstOrNull { it.reference == row.getString("reference") } else null
                val equivalent = candidate?.let { it.customerId == customerId && fingerprint(siteJson(it, row)) == fp } == true
                val wrongParent = candidate != null && candidate.customerId != customerId
                val classification = classifyBound(bound,target != null,same,candidate != null,equivalent,wrongParent)
                val localId = target?.id ?: if (classification == DataTransferClassification.MATCH_EXISTING) candidate!!.id else UUID.randomUUID().toString()
                val plan = currentPlan(DataTransferFamily.REGISTER,"SITE",row,localId,classification,fp,customerId)
                current += plan; mapping[id]=localId; items += display(plan,"Site ${row.optString("reference")}")
            }
            val equipment = register.getJSONArray("equipment")
            for (i in 0 until equipment.length()) {
                val row = withNullableFields(equipment.getJSONObject(i), "technicianIdentifier", "make", "model", "serialNumber"); val id = sourceKey(row,"EQUIPMENT"); val siteKey = parentKey(row,"site")
                val siteId = mapping[siteKey] ?: resolveBinding(siteKey,"SITE")
                if (siteId == null) { current += conflictPlan(DataTransferFamily.REGISTER,"EQUIPMENT",row,"Equipment has no resolvable Site dependency"); items += display(current.last(),"Equipment ${row.optString("reference")}"); continue }
                val bound=dao.dataTransferBinding(id.first,"EQUIPMENT",id.second); val target=bound?.localEntityId?.let{dao.equipment(it)}
                val fp=fingerprint(row); val same=target?.let{it.siteId==siteId&&fingerprint(equipmentJson(it,row))==bound?.appliedSourceFingerprint&&bound.appliedSourceFingerprint==fp}==true
                val candidate=if(bound==null)dao.allEquipment().firstOrNull{it.reference==row.getString("reference")}else null
                val equivalent=candidate?.let{it.siteId==siteId&&fingerprint(equipmentJson(it,row))==fp}==true
                val wrongParent=candidate!=null&&candidate.siteId!=siteId
                val classification=classifyBound(bound,target!=null,same,candidate!=null,equivalent,wrongParent)
                val localId=target?.id?:if(classification==DataTransferClassification.MATCH_EXISTING)candidate!!.id else UUID.randomUUID().toString()
                val plan=currentPlan(DataTransferFamily.REGISTER,"EQUIPMENT",row,localId,classification,fp,siteId)
                current+=plan;mapping[id]=localId;items+=display(plan,"Equipment ${row.optString("reference")}")
            }
            val contacts=register.getJSONArray("contacts")
            for(i in 0 until contacts.length()){
                val row=contacts.getJSONObject(i);val id=sourceKey(row,"CUSTOMER_CONTACT");val parent=parentKey(row,"customer");val customerId=mapping[parent]?:resolveBinding(parent,"CUSTOMER")
                if(customerId==null){current+=conflictPlan(DataTransferFamily.REGISTER,"CUSTOMER_CONTACT",row,"Contact has no resolvable Customer dependency");items+=display(current.last(),"Customer contact");continue}
                val bound=dao.dataTransferBinding(id.first,"CUSTOMER_CONTACT",id.second);val target=bound?.localEntityId?.let{contactById(it)}
                val fp=fingerprint(row);val same=target?.let{it.customerId==customerId&&fingerprint(contactJson(it,row))==bound?.appliedSourceFingerprint&&bound.appliedSourceFingerprint==fp}==true
                val equivalent=if(bound==null)dao.customerContacts(customerId).firstOrNull{normalizedContactMatch(it,row)}else null
                val classification=classifyBound(bound,target!=null,same,equivalent!=null,equivalent!=null)
                val localId=target?.id?:if(classification==DataTransferClassification.MATCH_EXISTING)equivalent!!.id else UUID.randomUUID().toString()
                val plan=currentPlan(DataTransferFamily.REGISTER,"CUSTOMER_CONTACT",row,localId,classification,fp,customerId)
                current+=plan;mapping[id]=localId;items+=display(plan,"Contact ${row.optString("value")}")
            }
        }

        val templateBytes = payload.families[DataTransferFamily.INSPECTION_TEMPLATES]
        val templateEntries = templateBytes?.let { InspectionTemplateCodec.decode(it).templates }.orEmpty()
        val templatePreview = templateBytes?.let { templateExchange.preview(it) }
        templateEntries.forEach { entry ->
            val sourceOrigin = entry.originWorkspaceId ?: payload.sourceWorkspaceId
            val sourceId = entry.sourceEntityId ?: entry.reference
            val bound = dao.dataTransferBinding(sourceOrigin,"INSPECTION_TEMPLATE",sourceId)
            val exact = templatePreview?.entries?.firstOrNull{it.transfer.reference==entry.reference}?.classification==InspectionTemplateImportClassification.EXACT_EXISTING
            val target = bound?.localEntityId?.let{dao.reusableTemplate(it)}
            val fingerprint = InspectionTemplateCodec.fingerprint(entry)
            val localContentFingerprint = target?.let { templateContentFingerprint(it) }
            val sourceContentFingerprint = InspectionTemplateCodec.contentFingerprint(entry)
            val classification = when {
                bound != null && target == null -> DataTransferClassification.CONFLICT
                bound != null && bound.appliedSourceFingerprint == fingerprint && target != null && localContentFingerprint == sourceContentFingerprint -> DataTransferClassification.ALREADY_CURRENT
                bound != null -> DataTransferClassification.CONFLICT
                exact -> DataTransferClassification.MATCH_EXISTING
                templatePreview?.entries?.any{it.transfer.reference==entry.reference&&it.classification==InspectionTemplateImportClassification.CONFLICT}==true -> DataTransferClassification.CONFLICT
                else -> DataTransferClassification.NEW
            }
            val localId = target?.id ?: dao.reusableTemplates().firstOrNull { it.reference == entry.reference && exact }?.id ?: UUID.randomUUID().toString()
            val row=JSONObject().put("originWorkspaceId",sourceOrigin).put("sourceEntityId",sourceId).put("reference",entry.reference)
            val plan=currentPlan(DataTransferFamily.INSPECTION_TEMPLATES,"INSPECTION_TEMPLATE",row,localId,classification,fingerprint)
            current+=plan;mapping[SourceEntityKey(sourceOrigin,SourceEntityType.INSPECTION_TEMPLATE,sourceId)]=localId
            items+=DataTransferImportItem(DataTransferFamily.INSPECTION_TEMPLATES,if(classification==DataTransferClassification.CONFLICT)entry.reference else "$sourceOrigin:$sourceId",classification,"Template ${entry.reference}",if(classification==DataTransferClassification.CONFLICT)"An inspection template with this reference or bound source has different content."else null)
        }

        val plansRoot=payload.families[DataTransferFamily.SERVICE_PLANS]?.let{JSONObject(it.toString(Charsets.UTF_8))}
        validatePlans(plansRoot)
        plansRoot?.getJSONArray("plans")?.let{rows->for(i in 0 until rows.length()){
            val row=rows.getJSONObject(i);val id=sourceKey(row,"SERVICE_PLAN");val equipmentKey=parentKey(row,"equipment");val equipmentId=mapping[equipmentKey]?:resolveBinding(equipmentKey,"EQUIPMENT")
            if(equipmentId==null){current+=conflictPlan(DataTransferFamily.SERVICE_PLANS,"SERVICE_PLAN",row,"Service Plan has no resolvable Equipment dependency");items+=display(current.last(),"Plan ${row.optString("reference")}");continue}
            val stagedEquipment = current.firstOrNull { it.type == "EQUIPMENT" && it.localId == equipmentId }
            val siteId = stagedEquipment?.parentLocalId ?: dao.equipment(equipmentId)?.siteId
            val stagedSite = current.firstOrNull { it.type == "SITE" && it.localId == siteId }
            val customerId = stagedSite?.parentLocalId ?: siteId?.let { dao.site(it)?.customerId }
            val stagedCustomer = current.firstOrNull { it.type == "CUSTOMER" && it.localId == customerId }
            val customerType = stagedCustomer?.json?.optString("customerType", "STANDARD") ?: customerId?.let { dao.customer(it)?.customerType }
            if (listOfNotNull(stagedEquipment, stagedSite, stagedCustomer).any { it.classification == DataTransferClassification.CONFLICT } ||
                customerType != "STANDARD") {
                current += conflictPlan(DataTransferFamily.SERVICE_PLANS, "SERVICE_PLAN", row,
                    if (customerType == "ONE_TIME") "Recurring Service Plans require a Standard Customer" else "Service Plan ancestry is unavailable or conflicted")
                items += display(current.last(), "Plan ${row.optString("reference")}")
                continue
            }
            val templateId=if(row.isNull("templateSourceEntityId"))null else {
                val key=SourceEntityKey(validOrigin(row.getString("templateOriginWorkspaceId")),SourceEntityType.INSPECTION_TEMPLATE,required(row,"templateSourceEntityId"))
                mapping[key]?:resolveBinding(key,"INSPECTION_TEMPLATE")
            }
            if(!row.isNull("templateSourceEntityId")&&templateId==null){current+=conflictPlan(DataTransferFamily.SERVICE_PLANS,"SERVICE_PLAN",row,"Service Plan template dependency cannot be resolved");items+=display(current.last(),"Plan ${row.optString("reference")}");continue}
            val bound=dao.dataTransferBinding(id.first,"SERVICE_PLAN",id.second);val target=bound?.localEntityId?.let{dao.plan(it)};val fp=fingerprint(row)
            val same=target?.let{it.equipmentId==equipmentId&&it.reusableTemplateId==templateId&&fingerprint(planJson(it,row))==bound?.appliedSourceFingerprint&&bound.appliedSourceFingerprint==fp}==true
            val candidate=if(bound==null)dao.allPlans().firstOrNull{it.reference==row.getString("reference")}else null
            val equivalent=candidate?.let{it.equipmentId==equipmentId&&it.reusableTemplateId==templateId&&fingerprint(planJson(it,row))==fp}==true
            val wrong=candidate!=null&&(candidate.equipmentId!=equipmentId||candidate.reusableTemplateId!=templateId)
            val classification=classifyBound(bound,target!=null,same,candidate!=null,equivalent,wrong)
            val localId=target?.id?:if(classification==DataTransferClassification.MATCH_EXISTING)candidate!!.id else UUID.randomUUID().toString()
            val item=currentPlan(DataTransferFamily.SERVICE_PLANS,"SERVICE_PLAN",row,localId,classification,fp,equipmentId)
            current+=item;mapping[id]=localId;items+=display(item,"Plan ${row.optString("reference")}")
        }}

        parsePerformed(payload, mapping, history, items)
        parseHistory(payload, mapping, history, items)
        val evidence = parseEvidence(payload, mapping, items)
        val counts = familyCounts(payload)
        return PlanSet(current,history,evidence,templatePreview,templateEntries,mapping,items,counts)
    }

    private suspend fun applyCurrent(items: List<CurrentPlan>, options: DataTransferOptions, now: Long) {
        val inserted = items.filter { it.classification == DataTransferClassification.NEW }
        inserted.filter { it.type == "CUSTOMER" }.forEach { item ->
            val row=item.json;dao.insertCustomers(listOf(CustomerEntity(item.localId,row.getString("reference"),row.getString("name"),row.optNullable("contactName"),row.optNullable("phone"),row.optNullable("email"),row.optNullable("privateNote"),row.optString("state","ACTIVE"),row.optString("customerType","STANDARD"))))
        }
        inserted.filter { it.type == "SITE" }.forEach { item -> val row=item.json;dao.insertSites(listOf(SiteEntity(item.localId,item.parentLocalId!!,row.getString("reference"),row.getString("name"),row.optNullable("address"),row.optNullable("privateAccessNotes"),row.optNullable("contactName"),row.optNullable("phone"),row.optNullable("email"),row.optBoolean("isDefault",false),row.optString("state","ACTIVE")))) }
        inserted.filter { it.type == "EQUIPMENT" }.forEach { item -> val row=item.json;dao.insertEquipment(listOf(EquipmentEntity(item.localId,item.parentLocalId!!,row.getString("reference"),row.optNullable("technicianIdentifier"),row.getString("name"),row.optNullable("make"),row.optNullable("model"),row.optNullable("serialNumber"),row.optNullable("privateNotes"),row.optString("state","ACTIVE")))) }
        inserted.filter { it.type == "CUSTOMER_CONTACT" }.groupBy { it.parentLocalId!! }.forEach { (customerId, contacts) ->
            val existing=dao.customerContacts(customerId);val next=(existing.maxOfOrNull { it.position } ?: 0).coerceAtLeast(0) + 1
            val ordered=contacts.sortedWith(compareBy<CurrentPlan>{it.json.optInt("position",Int.MAX_VALUE)}.thenBy{it.sourceId})
            dao.insertCustomerContacts(ordered.mapIndexed { index,item -> val row=item.json;CustomerContactEntity(item.localId,customerId,row.optNullable("personName"),row.getString("channel"),row.getString("value"),now,now,row.optNullable("notes"),next+index) })
        }
        inserted.filter { it.type == "CUSTOMER" || it.type == "SITE" || it.type == "EQUIPMENT" || it.type == "CUSTOMER_CONTACT" }.forEach { insertBinding(it,now) }
        items.filter { it.type in setOf("CUSTOMER","SITE","EQUIPMENT","CUSTOMER_CONTACT") && it.classification==DataTransferClassification.MATCH_EXISTING }.forEach { insertBinding(it,now) }
    }

    private suspend fun applyTemplates(plan: PlanSet, payload: DataTransferPayload, createSeparate: Set<String>, now: Long): Map<SourceEntityKey,String> {
        val templateRows=plan.current.filter{it.type=="INSPECTION_TEMPLATE"}
        if(templateRows.isEmpty())return emptyMap()
        val conflictedRefs=plan.templatePreview?.entries?.filter{entry->
            val row=templateRows.firstOrNull{it.json.optString("reference")==entry.transfer.reference}
            row?.classification==DataTransferClassification.CONFLICT
        }?.map{it.transfer.reference}.orEmpty()
        require(conflictedRefs.all{it in createSeparate}){"Resolve inspection template conflicts before importing"}
        payload.families[DataTransferFamily.INSPECTION_TEMPLATES]?:return emptyMap()
        val result=if(plan.templatePreview!=null && plan.templatePreview.entries.any{it.classification!=InspectionTemplateImportClassification.EXACT_EXISTING})
            templateExchange.import(plan.templatePreview,payload.exporterId,createSeparate) else InspectionTemplateImportResult(emptyList(),plan.templateEntries.map{it.reference},emptyList())
        val localRows=dao.reusableTemplates()
        val out=linkedMapOf<SourceEntityKey,String>()
        plan.templateEntries.forEach { entry ->
            val origin=entry.originWorkspaceId?:payload.sourceWorkspaceId;val source=entry.sourceEntityId?:entry.reference
            val existingBinding=dao.dataTransferBinding(origin,"INSPECTION_TEMPLATE",source)
            val target=existingBinding?.localEntityId?.let{dao.reusableTemplate(it)} ?: run {
                val separateReference=if(entry.reference in createSeparate) result.createdSeparateReferences.firstOrNull{it.startsWith("${entry.reference}-imported-")} else null
                val wantedReference=separateReference?:entry.reference
                localRows.firstOrNull{it.reference==wantedReference} ?: localRows.firstOrNull{it.reference.startsWith("${entry.reference}-imported-")&&templateContentEquals(it,entry)}
            } ?: error("Imported inspection template could not be resolved")
            out[SourceEntityKey(origin,SourceEntityType.INSPECTION_TEMPLATE,source)]=target.id
            if(existingBinding==null) dao.insertDataTransferBinding(DataTransferBindingEntity(UUID.randomUUID().toString(),origin,"INSPECTION_TEMPLATE",source,target.id,InspectionTemplateCodec.fingerprint(entry),now))
        }
        return out
    }

    private suspend fun applyPlans(items: List<CurrentPlan>, mapping: Map<SourceEntityKey,String>, templateIds: Map<SourceEntityKey,String>, now: Long) {
        val plans=items.filter{it.type=="SERVICE_PLAN"}
        plans.filter{it.classification==DataTransferClassification.NEW}.forEach { item ->
            val row=item.json;val templateKey=if(row.isNull("templateSourceEntityId"))null else SourceEntityKey(validOrigin(row.getString("templateOriginWorkspaceId")),SourceEntityType.INSPECTION_TEMPLATE,required(row,"templateSourceEntityId"))
            val templateId=templateKey?.let{templateIds[it]?:mapping[it]}
            val active=row.optString("state")=="ACTIVE";val obligationId=if(active)UUID.randomUUID().toString()else null
            dao.insertPlans(listOf(ServicePlanEntity(item.localId,item.parentLocalId!!,row.getString("reference"),row.getString("name"),row.getInt("intervalCount"),row.getString("intervalUnit"),row.getString("currentDueDate"),row.getString("state"),obligationId,reusableTemplateId=templateId)))
            if(obligationId!=null)dao.insertObligations(listOf(ServiceObligationEntity(obligationId,item.localId,1,row.getString("currentDueDate"),now,null,null)))
        }
        plans.filter{it.classification in setOf(DataTransferClassification.NEW,DataTransferClassification.MATCH_EXISTING)}.forEach{insertBinding(it,now)}
    }

    private suspend fun applyHistory(history: List<ParsedHistory>, payload: DataTransferPayload, mapping: Map<SourceEntityKey,String>, staged: Map<String,File>, now: Long) {
        history.filter{it.family!=DataTransferFamily.PERFORMED_WORK}.filter{it.classification==DataTransferClassification.NEW_HISTORY}.forEach{item->
            val row=item.source;val refs=historyMappings(row,mapping)
            dao.insertTransferredHistoryEntries(listOf(TransferredHistoryEntryEntity(UUID.randomUUID().toString(),row.getString("originWorkspaceId"),item.family.name,row.getString("sourceEntityId"),row.optNullable("sourceRevisionId"),row.optNullable("sourceRevisionId")?:"",payload.exporterId,refs.first,refs.second,refs.third,row.optNullable("eventDateTime"),row.getJSONObject("payload").toString(),item.fingerprint,now)))
        }
        history.filter{it.family==DataTransferFamily.PERFORMED_WORK}.filter{it.classification==DataTransferClassification.NEW_HISTORY}.forEach{item->
            val row=item.source;val refs=historyMappings(row,mapping);val subject=row.getJSONObject("subjectSnapshot");val recurrence=row.getJSONObject("recurrence")
            val evidence=row.optJSONArray("evidence")?:JSONArray()
            val entity=TransferredFinalResultEntity(UUID.randomUUID().toString(),row.getString("originWorkspaceId"),row.getString("sourceVisitId"),row.getString("sourceWorkItemId"),row.getString("sourceFinalRevisionId"),row.getString("logicalResultId"),payload.exporterId,now,refs.first,refs.second,refs.third,
                row.getJSONObject("customerSnapshot").toString(),row.getJSONObject("siteSnapshot").toString(),subject.toString(),row.getString("visitReference"),row.getString("serviceDate"),row.getString("technicianId"),row.getString("technicianName"),row.optNullable("technicianDesignation"),row.getString("serviceName"),row.getString("outcome"),row.optNullable("workPerformed"),row.optNullable("notPerformedReason"),row.getJSONArray("checklist").toString(),row.getJSONArray("findings").toString(),row.getJSONArray("parts").toString(),row.optNullable("internalNotes"),recurrence.toString(),item.fingerprint,JSONObject().put("recordedAt",row.optString("recordedAt")).put("sourceWorkItemPosition",row.optInt("sourceWorkItemPosition",0)).put("relayExporterId",payload.exporterId).put("evidence",evidence).toString(),
                sourcePayloadJson = if (payload.familyVersions[DataTransferFamily.PERFORMED_WORK] == 2)
                    FinalSourceSnapshot.encode("PERFORMED_WORK", 2, 2, row) else null)
            dao.insertTransferredFinalResults(listOf(entity))
        }
    }

    private suspend fun applyEvidence(evidence: List<ParsedEvidence>, payload: DataTransferPayload, mapping: Map<SourceEntityKey,String>, staged: Map<String,File>, now: Long) {
        evidence.filter{it.classification==DataTransferClassification.NEW_HISTORY}.forEach{item->
            val row=item.value;val customerKey=optionalRef(row,"customer");val siteKey=optionalRef(row,"site");val equipmentKey=optionalRef(row,"equipment")
            val customerId=customerKey?.let{mapping[it]?:resolveBinding(it,"CUSTOMER")};val siteId=siteKey?.let{mapping[it]?:resolveBinding(it,"SITE")};val equipmentId=equipmentKey?.let{mapping[it]?:resolveBinding(it,"EQUIPMENT")}
            val result=dao.transferredFinalResult(row.getString("originWorkspaceId"),row.getString("sourceWorkItemId"),row.optString("sourceFinalRevisionId"))
            val target=staged.getValue(item.key)
            dao.insertTransferredEvidence(listOf(TransferredEvidenceEntity(UUID.randomUUID().toString(),item.key,row.getString("originWorkspaceId"),row.getString("sourcePhotoId"),row.getString("sourceVisitId"),row.getString("sourceWorkItemId"),row.optNullable("sourceFinalRevisionId"),result?.id,payload.exporterId,customerId,siteId,equipmentId,row.getString("serviceDate"),row.getString("visitReference"),row.getString("serviceName"),relative(target),item.hash,item.bytes.size.toLong(),row.getInt("width"),row.getInt("height"),row.optString("mimeType","image/jpeg"),row.optNullable("caption"),row.getString("visibility"),row.optBoolean("includedInCustomerReport"),now,
                JSONObject().put("customerOriginWorkspaceId",row.optNullable("originCustomerWorkspaceId") ?: JSONObject.NULL)
                    .put("customerSourceEntityId",row.optNullable("originCustomerSourceId") ?: JSONObject.NULL)
                    .put("siteOriginWorkspaceId",row.optNullable("originSiteWorkspaceId") ?: JSONObject.NULL)
                    .put("siteSourceEntityId",row.optNullable("originSiteSourceId") ?: JSONObject.NULL)
                    .put("equipmentOriginWorkspaceId",row.optNullable("originEquipmentWorkspaceId") ?: JSONObject.NULL)
                    .put("equipmentSourceEntityId",row.optNullable("originEquipmentSourceId") ?: JSONObject.NULL)
                    .put("relayExporterId",payload.exporterId).toString())))
        }
    }

    private suspend fun reconcileEvidenceAssociations() {
        dao.allTransferredEvidence().forEach { evidence ->
            val revision = evidence.sourceFinalRevisionId ?: return@forEach
            val result = dao.transferredFinalResult(evidence.originWorkspaceId, evidence.sourceWorkItemId, revision)
                ?: return@forEach
            require(result.sourceVisitId == evidence.sourceVisitId &&
                result.originWorkspaceId == evidence.originWorkspaceId &&
                result.sourceWorkItemId == evidence.sourceWorkItemId &&
                result.sourceFinalRevisionId == revision) { "Transferred evidence belongs to another final source" }
            when (evidence.transferredFinalResultId) {
                null -> require(dao.linkTransferredEvidenceIfUnlinked(evidence.id, result.id) == 1) { "Evidence association changed during import" }
                result.id -> Unit
                else -> error("Transferred evidence is linked to a conflicting final result")
            }
        }
    }

    private suspend fun stageEvidence(rows: List<ParsedEvidence>, created: MutableList<File>): Map<String,File> = buildMap {
        rows.forEach { row ->
            val path="transferred-evidence/${sha256(row.key.toByteArray())}.jpg";val file=ownedFile(path)
            if(file.exists())require(file.length()==row.bytes.size.toLong()&&sha256(file.readBytes())==row.hash){"Receiver evidence path contains conflicting bytes"}
            else{require(file.parentFile!!.isDirectory||file.parentFile!!.mkdirs());created+=file;file.outputStream().use{stream->stream.write(row.bytes);stream.fd.sync()}}
            put(row.key,file)
        }
    }

    private suspend fun parsePerformed(payload: DataTransferPayload, mapping: Map<SourceEntityKey,String>, history: MutableList<ParsedHistory>, items: MutableList<DataTransferImportItem>) {
        val bytes=payload.families[DataTransferFamily.PERFORMED_WORK]?:return;val root=JSONObject(bytes.toString(Charsets.UTF_8))
        val version=payload.familyVersions.getValue(DataTransferFamily.PERFORMED_WORK)
        require(root.getInt("version")==version && version in 1..2)
        val visits=root.getJSONArray("visits");val visitKeys=mutableSetOf<Pair<String,String>>();val resultKeys=mutableSetOf<Triple<String,String,String>>()
        for(i in 0 until visits.length()){
            val visit=visits.getJSONObject(i);val origin=validOrigin(visit.getString("originWorkspaceId"));val visitId=required(visit,"sourceVisitId")
            require(visitKeys.add(origin to visitId)){"Duplicate transferred Visit"};LocalDate.parse(visit.getString("serviceDate"))
            require(visit.getJSONArray("records").length()>0)
            val records=visit.getJSONArray("records")
            for(j in 0 until records.length()){
                val row=records.getJSONObject(j);val recordOrigin=validOrigin(row.getString("originWorkspaceId"));val sourceVisit=required(row,"sourceVisitId");val work=required(row,"sourceWorkItemId");val revision=required(row,"sourceFinalRevisionId")
                require(recordOrigin==origin&&sourceVisit==visitId&&row.getString("visitReference")==visit.getString("visitReference")){"Performed record does not match its Visit"}
                LocalDate.parse(row.getString("serviceDate"));require(row.getJSONArray("checklist").length()<=1000&&row.getJSONArray("parts").length()<=1000)
                require(row.getString("outcome") in setOf("PERFORMED","PARTLY_PERFORMED","NOT_PERFORMED"))
                if(version==2){
                    validatePerformedV2(row,payload.options.includePrivate)
                }
                require(resultKeys.add(Triple(recordOrigin,work,revision))){"Duplicate performed revision"}
                validateHistoryMappings(row,mapping)
                val key="${recordOrigin}:$work:$revision";val fp=if(version==2)fingerprintPerformedV2(row)else fingerprint(row)
                val old=dao.transferredFinalResult(recordOrigin,work,revision)
                val classification=when{old==null->DataTransferClassification.NEW_HISTORY;old.payloadSha256==fp->DataTransferClassification.ALREADY_IMPORTED;else->DataTransferClassification.CONFLICT}
                val source=row
                history+=ParsedHistory(DataTransferFamily.PERFORMED_WORK,key,source,fp,classification)
                items+=DataTransferImportItem(DataTransferFamily.PERFORMED_WORK,key,classification,"${visit.getString("visitReference")} · ${row.getString("serviceName")}",if(classification==DataTransferClassification.CONFLICT)"The same immutable source revision has different content."else null)
            }
        }
    }

    private suspend fun parseHistory(payload: DataTransferPayload, mapping: Map<SourceEntityKey,String>, history: MutableList<ParsedHistory>, items: MutableList<DataTransferImportItem>) {
        listOf(DataTransferFamily.FOLLOW_UPS,DataTransferFamily.CONTACT_NOTES,DataTransferFamily.CHANGE_HISTORY).forEach{family->
            val bytes=payload.families[family]?:return@forEach;val array=JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("records");val seen=mutableSetOf<String>()
            for(index in 0 until array.length()){
                val row=array.getJSONObject(index);val origin=validOrigin(row.getString("originWorkspaceId"));val source=required(row,"sourceEntityId");val revision=row.optString("sourceRevisionId").takeIf{it.isNotBlank()&&it!="null"};val revKey=revision?:""
                val key="$origin:${family.name}:$source:$revKey";require(seen.add(key)){"Duplicate transferred history identity"};row.optString("eventDateTime").takeIf{it.isNotBlank()&&it!="null"}?.let{runCatching{LocalDate.parse(it.take(10))}.getOrElse{throw IllegalArgumentException("Invalid history date")}}
                validateHistoryMappings(row,mapping)
                val fp=fingerprint(row);val old=dao.transferredHistoryEntry(origin,family.name,source,revKey)
                val classification=when{old==null->DataTransferClassification.NEW_HISTORY;old.payloadSha256==fp->DataTransferClassification.ALREADY_IMPORTED;else->DataTransferClassification.CONFLICT}
                history+=ParsedHistory(family,key,row,fp,classification);items+=DataTransferImportItem(family,key,classification,"${family.name.replace('_',' ')} · $source",if(classification==DataTransferClassification.CONFLICT)"The same immutable history identity has different content."else null)
            }
        }
    }

    private suspend fun parseEvidence(payload: DataTransferPayload, mapping: Map<SourceEntityKey,String>, items: MutableList<DataTransferImportItem>): List<ParsedEvidence> {
        val bytes=payload.families[DataTransferFamily.EVIDENCE]?:return emptyList();val array=JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("photos");val seen=mutableSetOf<String>();val result=mutableListOf<ParsedEvidence>()
        for(i in 0 until array.length()){
            val row=array.getJSONObject(i);val origin=validOrigin(row.getString("originWorkspaceId"));val photo=required(row,"sourcePhotoId");val revision=row.optString("sourceFinalRevisionId").takeIf{it.isNotBlank()&&it!="null"};val key=transferEvidenceSourceKey(origin,photo,revision)
            require(seen.add(key)){"Duplicate evidence source identity"};val binaryName=required(row,"binaryName");val content=payload.binaries[binaryName]?:error("Evidence binary is missing");val hash=required(row,"sha256")
            require(content.size==row.getLong("byteSize").toInt()&&sha256(content)==hash){"Evidence binary integrity check failed"};require(row.getString("visibility") in setOf("PUBLIC","PRIVATE","INTERNAL"))
            require(row.getString("mimeType")=="image/jpeg"&&row.getInt("width") in 1..1200&&row.getInt("height") in 1..1200&&maxOf(row.getInt("width"),row.getInt("height"))<=1200&&content.size>=4&&content[0]==0xFF.toByte()&&content[1]==0xD8.toByte()){"Transferred evidence is not a bounded JPEG derivative"}
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(content,0,content.size,bounds);require(bounds.outWidth==row.getInt("width")&&bounds.outHeight==row.getInt("height")){"Transferred evidence dimensions do not match"}
            validateHistoryMappings(row,mapping)
            val metadataFingerprint=evidenceMetadataFingerprint(row)
            val matches=dao.transferredEvidenceBySourceIdentity(origin,photo,revision)
            require(matches.size<=1) { "Ambiguous historical evidence source identity" }
            val existing=matches.singleOrNull();val classification=when{
                existing==null->DataTransferClassification.NEW_HISTORY
                existing.sha256==hash&&existing.byteSize==content.size.toLong()&&evidenceMetadataFingerprint(existing)==metadataFingerprint->DataTransferClassification.ALREADY_IMPORTED
                else->DataTransferClassification.CONFLICT
            }
            result+=ParsedEvidence(row,content,key,hash,classification);items+=DataTransferImportItem(DataTransferFamily.EVIDENCE,key,classification,"Photo · ${row.optString("visitReference")}",if(classification==DataTransferClassification.CONFLICT)"The same photo revision has different bytes or immutable metadata."else null)
        }
        return result
    }

    private fun validateRegister(root: JSONObject?) {
        if (root == null) return
        require(root.getInt("version") == 1)
        val arrays = mapOf("CUSTOMER" to "customers", "CUSTOMER_CONTACT" to "contacts", "SITE" to "sites", "EQUIPMENT" to "equipment")
        arrays.forEach { (type, name) ->
            val array = root.getJSONArray(name)
            val seen = mutableSetOf<SourceEntityKey>()
            for (index in 0 until array.length()) {
                val row = array.getJSONObject(index)
                require(seen.add(sourceKey(row, type))) { "Duplicate $type source" }
                if (type != "CUSTOMER_CONTACT") required(row, "reference", 100)
                when (type) {
                    "CUSTOMER" -> {
                        required(row, "name", 200)
                        require(row.optString("customerType", "STANDARD") in setOf("STANDARD", "ONE_TIME"))
                        require(row.optString("state", "ACTIVE") in setOf("ACTIVE", "ARCHIVED"))
                        optionalText(row, "contactName", 200); optionalText(row, "phone", 100)
                        optionalText(row, "email", 320); optionalText(row, "privateNote", 5000)
                    }
                    "SITE" -> {
                        required(row, "name", 200)
                        require(row.optString("state", "ACTIVE") in setOf("ACTIVE", "ARCHIVED"))
                        if (row.has("isDefault")) require(row.get("isDefault") is Boolean) { "Invalid Site default flag" }
                        optionalText(row, "address", 500); optionalText(row, "contactName", 200)
                        optionalText(row, "phone", 100); optionalText(row, "email", 320)
                        optionalText(row, "privateAccessNotes", 5000)
                    }
                    "EQUIPMENT" -> {
                        required(row, "name", 200)
                        require(row.optString("state", "ACTIVE") in setOf("ACTIVE", "RETIRED"))
                        optionalText(row, "technicianIdentifier", 100); optionalText(row, "make", 100)
                        optionalText(row, "model", 100); optionalText(row, "serialNumber", 150)
                        optionalText(row, "privateNotes", 5000)
                    }
                    "CUSTOMER_CONTACT" -> {
                        required(row, "value", 300)
                        require(row.getString("channel") in setOf("PHONE", "SMS", "WHATSAPP", "EMAIL", "OTHER"))
                        require(row.get("position") is Number && row.getInt("position") > 0) { "Invalid Customer contact position" }
                        optionalText(row, "personName", 200); optionalText(row, "notes", 2000)
                    }
                }
            }
        }
        root.getJSONArray("sites").forEachJson { row -> parentKey(row, "customer") }
        root.getJSONArray("equipment").forEachJson { row -> parentKey(row, "site") }
        root.getJSONArray("contacts").forEachJson { row -> parentKey(row, "customer") }
    }

    private fun optionalText(row: JSONObject, key: String, max: Int) {
        if (!row.has(key) || row.isNull(key)) return
        val value = row.get(key)
        require(value is String && value.length <= max) { "Invalid $key" }
    }

    private fun validatePlans(root:JSONObject?) { if(root==null)return;require(root.getInt("version")==1);val a=root.getJSONArray("plans");val seen=mutableSetOf<SourceEntityKey>();for(i in 0 until a.length()){val row=a.getJSONObject(i);val id=sourceKey(row,"SERVICE_PLAN");require(seen.add(id));parentKey(row,"equipment");required(row,"reference",100);required(row,"name",200);require(row.get("intervalCount") is Number && row.get("intervalCount").toString().matches(Regex("[1-9][0-9]*")) && row.getInt("intervalCount")>0 && row.getString("intervalUnit") in setOf("DAYS","WEEKS","MONTHS","YEARS"));LocalDate.parse(row.getString("currentDueDate"));require(row.getString("state") in setOf("ACTIVE","ARCHIVED","RETIRED","DISABLED"));val templateOrigin=row.has("templateOriginWorkspaceId")&&!row.isNull("templateOriginWorkspaceId");val templateSource=row.has("templateSourceEntityId")&&!row.isNull("templateSourceEntityId");require(templateOrigin==templateSource){"Template reference must be fully present or absent"};if(templateOrigin){validOrigin(required(row,"templateOriginWorkspaceId"));required(row,"templateSourceEntityId")}} }

    private fun familyCounts(payload:DataTransferPayload):Map<DataTransferFamily,Int> = payload.families.mapValues{(family,bytes)->runCatching{when(family){DataTransferFamily.REGISTER->{val j=JSONObject(bytes.toString(Charsets.UTF_8));listOf("customers","contacts","sites","equipment").sumOf{j.getJSONArray(it).length()}};DataTransferFamily.SERVICE_PLANS->JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("plans").length();DataTransferFamily.INSPECTION_TEMPLATES->InspectionTemplateCodec.decode(bytes).templates.size;DataTransferFamily.PERFORMED_WORK->JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("visits").let{v->(0 until v.length()).sumOf{v.getJSONObject(it).getJSONArray("records").length()}};DataTransferFamily.FOLLOW_UPS,DataTransferFamily.CONTACT_NOTES,DataTransferFamily.CHANGE_HISTORY->JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("records").length();DataTransferFamily.EVIDENCE->JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("photos").length()}}.getOrElse{0}}

    private fun classifyBound(bound:DataTransferBindingEntity?,targetExists:Boolean,same:Boolean,candidateExists:Boolean,equivalent:Boolean,wrongParent:Boolean=false)=when{
        bound!=null&&!targetExists->DataTransferClassification.CONFLICT
        bound!=null&&same->DataTransferClassification.ALREADY_CURRENT
        bound!=null->DataTransferClassification.CONFLICT
        wrongParent->DataTransferClassification.CONFLICT
        candidateExists&&equivalent->DataTransferClassification.MATCH_EXISTING
        candidateExists->DataTransferClassification.CONFLICT
        else->DataTransferClassification.NEW
    }

    private fun currentPlan(family:DataTransferFamily,type:String,row:JSONObject,localId:String,classification:DataTransferClassification,fingerprint:String,parent:String?=null)=CurrentPlan(family,type,row,runCatching{validOrigin(row.getString("originWorkspaceId"))}.getOrDefault(""),row.optString("sourceEntityId"),localId,classification,fingerprint,parent)
    private fun conflictPlan(family:DataTransferFamily,type:String,row:JSONObject,reason:String)=currentPlan(family,type,row,UUID.randomUUID().toString(),DataTransferClassification.CONFLICT,"",null)
    private fun display(plan:CurrentPlan,description:String)=DataTransferImportItem(plan.family,"${plan.origin}:${plan.sourceId}",plan.classification,description,if(plan.classification==DataTransferClassification.CONFLICT) "A bound or same-reference record has materially different carried content." else null)
    private suspend fun insertBinding(item:CurrentPlan,now:Long){if(dao.dataTransferBinding(item.origin,item.type,item.sourceId)==null)dao.insertDataTransferBinding(DataTransferBindingEntity(UUID.randomUUID().toString(),item.origin,item.type,item.sourceId,item.localId,item.fingerprint,now))}

    private suspend fun resolveBinding(key:SourceEntityKey,type:String):String? {
        require(key.entityType.name == type) { "Source identity has the wrong entity type" }
        val local=dao.dataTransferBinding(key.first,type,key.second)?.localEntityId?:return null
        return when(type){"CUSTOMER"->if(dao.customer(local)!=null)local else null;"SITE"->if(dao.site(local)!=null)local else null;"EQUIPMENT"->if(dao.equipment(local)!=null)local else null;"SERVICE_PLAN"->if(dao.plan(local)!=null)local else null;"INSPECTION_TEMPLATE"->if(dao.reusableTemplate(local)!=null)local else null;"CUSTOMER_CONTACT"->if(contactById(local)!=null)local else null;else->null}
    }
    private suspend fun contactById(id:String):CustomerContactEntity? {
        for(customer in dao.allCustomers()) { val found=dao.customerContacts(customer.id).firstOrNull{it.id==id};if(found!=null)return found }
        return null
    }
    private suspend fun templateContentEquals(template:ReusableTemplateEntity,entry:InspectionTemplateTransferEntry):Boolean{val rev=dao.reusableTemplateRevision(template.currentRevisionId)?:return false;val items=dao.reusableTemplateItems(rev.id).map{DispatchInspectionItem(it.position,it.label,it.responseType,it.unit,it.required,it.privateGuidance)};return InspectionTemplateCodec.contentFingerprint(InspectionTemplateTransferEntry(template.reference,rev.nameSnapshot,rev.revisionNumber,items,state=template.state))==InspectionTemplateCodec.contentFingerprint(entry)}
    private suspend fun templateContentFingerprint(template:ReusableTemplateEntity):String?{val rev=dao.reusableTemplateRevision(template.currentRevisionId)?:return null;val items=dao.reusableTemplateItems(rev.id).map{DispatchInspectionItem(it.position,it.label,it.responseType,it.unit,it.required,it.privateGuidance)};return InspectionTemplateCodec.contentFingerprint(InspectionTemplateTransferEntry(template.reference,rev.nameSnapshot,rev.revisionNumber,items,state=template.state))}

    private fun customerJson(row:CustomerEntity,source:JSONObject)=JSONObject()
        .put("originWorkspaceId",source.optString("originWorkspaceId")).put("sourceEntityId",source.optString("sourceEntityId"))
        .put("reference",row.reference).put("name",row.name).put("customerType",row.customerType).put("state",row.state)
        .put("contactName",row.contactName ?: JSONObject.NULL).put("phone",row.phone ?: JSONObject.NULL).put("email",row.email ?: JSONObject.NULL)
        .apply { if(source.has("privateFieldsIncluded")){put("privateFieldsIncluded",true);if(source.has("privateNote"))put("privateNote",row.privateNote ?: JSONObject.NULL)} }
    private fun siteJson(row:SiteEntity,source:JSONObject)=JSONObject()
        .put("originWorkspaceId",source.optString("originWorkspaceId")).put("sourceEntityId",source.optString("sourceEntityId"))
        .put("customerOriginWorkspaceId",source.optString("customerOriginWorkspaceId")).put("customerSourceEntityId",source.optString("customerSourceEntityId"))
        .put("reference",row.reference).put("name",row.name).put("isDefault",row.isDefault).put("state",row.state)
        .put("address",row.address ?: JSONObject.NULL).put("contactName",row.contactName ?: JSONObject.NULL).put("phone",row.phone ?: JSONObject.NULL).put("email",row.email ?: JSONObject.NULL)
        .apply { if(source.has("privateFieldsIncluded")){put("privateFieldsIncluded",true);if(source.has("privateAccessNotes"))put("privateAccessNotes",row.privateAccessNotes ?: JSONObject.NULL)} }
    private fun equipmentJson(row:EquipmentEntity,source:JSONObject)=JSONObject()
        .put("originWorkspaceId",source.optString("originWorkspaceId")).put("sourceEntityId",source.optString("sourceEntityId"))
        .put("siteOriginWorkspaceId",source.optString("siteOriginWorkspaceId")).put("siteSourceEntityId",source.optString("siteSourceEntityId"))
        .put("reference",row.reference).put("name",row.name).put("state",row.state)
        .put("technicianIdentifier",row.technicianIdentifier ?: JSONObject.NULL).put("make",row.make ?: JSONObject.NULL).put("model",row.model ?: JSONObject.NULL).put("serialNumber",row.serialNumber ?: JSONObject.NULL)
        .apply { if(source.has("privateFieldsIncluded")){put("privateFieldsIncluded",true);if(source.has("privateNotes"))put("privateNotes",row.privateNotes ?: JSONObject.NULL)} }
    private fun contactJson(row:CustomerContactEntity,source:JSONObject)=JSONObject()
        .put("originWorkspaceId",source.optString("originWorkspaceId")).put("sourceEntityId",source.optString("sourceEntityId"))
        .put("customerOriginWorkspaceId",source.optString("customerOriginWorkspaceId")).put("customerSourceEntityId",source.optString("customerSourceEntityId"))
        .put("position",source.optInt("position")).put("personName",row.personName ?: JSONObject.NULL).put("channel",row.channel).put("value",row.value)
        .apply { if(source.has("notes"))put("notes",row.notes ?: JSONObject.NULL) }
    private fun planJson(row:ServicePlanEntity,source:JSONObject)=JSONObject().put("originWorkspaceId",source.optString("originWorkspaceId")).put("sourceEntityId",source.optString("sourceEntityId")).put("customerOriginWorkspaceId",source.optString("customerOriginWorkspaceId")).put("customerSourceEntityId",source.optString("customerSourceEntityId")).put("siteOriginWorkspaceId",source.optString("siteOriginWorkspaceId")).put("siteSourceEntityId",source.optString("siteSourceEntityId")).put("equipmentOriginWorkspaceId",source.optString("equipmentOriginWorkspaceId")).put("equipmentSourceEntityId",source.optString("equipmentSourceEntityId")).put("templateOriginWorkspaceId",if(source.isNull("templateOriginWorkspaceId"))JSONObject.NULL else source.getString("templateOriginWorkspaceId")).put("templateSourceEntityId",if(source.isNull("templateSourceEntityId"))JSONObject.NULL else source.getString("templateSourceEntityId")).put("reference",row.reference).put("name",row.name).put("intervalCount",row.intervalCount).put("intervalUnit",row.intervalUnit).put("currentDueDate",row.currentDueDate).put("state",row.state)
    private fun normalizedContactMatch(local:CustomerContactEntity,incoming:JSONObject)=local.channel.trim().uppercase()==incoming.optString("channel").trim().uppercase()&&local.value.trim()==incoming.optString("value").trim()&&local.personName.orEmpty().trim()==incoming.optString("personName").trim()&&(!incoming.has("notes")||local.notes.orEmpty().trim()==incoming.optString("notes").trim())
    private fun fingerprint(value:JSONObject):String=sha256(canonical(value.copyForFingerprint()).toByteArray(Charsets.UTF_8))
    private fun fingerprintPerformedV2(value:JSONObject):String {
        val immutable=JSONObject(value.toString())
        // Receiver register links and package transport choices are mutable hints, not source history.
        listOf("customer","site","equipment","localCustomerId","localSiteId","localEquipmentId",
            "logicalResultId","evidence","binaryName").forEach(immutable::remove)
        return sha256(SourceCanonicalJson.bytes(immutable))
    }
    private fun validatePerformedV2(row:JSONObject, includePrivate:Boolean) {
        require(row.getInt("sourceWorkItemPosition")>0 && row.getInt("sourceFinalRevisionNumber")>0)
        require(row.has("supersedesSourceFinalRevisionId") && row.has("correctionReason") && row.has("publicNote"))
        require(row.isNull("supersedesSourceFinalRevisionId") || row.getString("supersedesSourceFinalRevisionId") != row.getString("sourceFinalRevisionId"))
        require(row.getString("technicianId") == row.getString("originWorkspaceId")) { "Source author and origin differ" }
        Instant.parse(row.getString("recordedAt"))
        require(row.getJSONObject("subjectSnapshot").getString("type") in setOf("SITE","EQUIPMENT"))
        if (row.has("sourceWorkSnapshot")) {
            val sourceWork = row.getJSONObject("sourceWorkSnapshot")
            fun nullableFact(value: JSONObject, key: String): Any? = if (!value.has(key) || value.isNull(key)) null else value.get(key)
            require(sourceWork.get("serviceName") is String && sourceWork.getString("serviceName") == row.getString("serviceName") &&
                sourceWork.get("fulfilledObligation") is Boolean &&
                sourceWork.getBoolean("fulfilledObligation") == row.getJSONObject("recurrence").getBoolean("fulfilledObligation") &&
                nullableFact(sourceWork, "publicWork") == nullableFact(row, "workPerformed") &&
                nullableFact(sourceWork, "notPerformedReason") == nullableFact(row, "notPerformedReason")) {
                "Source work snapshot disagrees with performed record"
            }
            listOf("planId", "capturedObligationId", "oldDueDate", "nextDueDate",
                "nextDueDateCalculated", "nextDueOverrideReason").forEach { key ->
                require(nullableFact(sourceWork, key) == nullableFact(row.getJSONObject("recurrence"), key)) {
                    "Source work snapshot disagrees with recurrence: $key"
                }
            }
            if (includePrivate && row.has("internalNotes")) {
                require(nullableFact(sourceWork, "privateInternalNote") == nullableFact(row, "internalNotes")) {
                    "Source work private note disagrees with performed record"
                }
            }
            require(includePrivate || !sourceWork.has("privateInternalNote")) { "Private source work in public transfer" }
        }
        val capture=row.getString("followUpCaptureState")
        require(capture in setOf("CAPTURED_AT_REVISION","UNAVAILABLE_LEGACY"))
        val followUps=row.getJSONArray("followUps")
        require(followUps.length()<=1000 && (capture!="UNAVAILABLE_LEGACY" || followUps.length()==0))
        if(!includePrivate) {
            require(!row.has("internalNotes") && !row.has("finalInternalNote")) { "Private notes in public transfer" }
            for(index in 0 until followUps.length()) require(!followUps.getJSONObject(index).has("privatePlanningNote")) { "Private follow-up in public transfer" }
        }
        val photos=row.getJSONArray("sourcePhotos")
        require(photos.length()<=WorkResultPackageCodec.MAX_PHOTOS)
        val photoIds=mutableSetOf<String>()
        for(index in 0 until photos.length()) {
            val photo=photos.getJSONObject(index)
            val id=required(photo,"sourcePhotoId")
            require(photoIds.add(id) && photo.getString("sourceWorkItemId")==row.getString("sourceWorkItemId")) { "Duplicate or foreign source photo" }
            require(photo.getInt("position")>0 && photo.getLong("originalByteSize") in 1..AppOwnedImageNormalizer.MAX_SOURCE_BYTES.toLong() &&
                photo.getString("originalSha256").matches(Regex("[a-fA-F0-9]{64}")) &&
                photo.getString("originalMimeType").startsWith("image/")) { "Invalid original source photo" }
            require(photo.getString("visibility") in setOf("PUBLIC","PRIVATE","INTERNAL") &&
                (includePrivate || photo.getString("visibility")=="PUBLIC")) { "Private source photo in public transfer" }
            require(photo.get("includeInReport") is Boolean && photo.get("addedInCorrection") is Boolean)
            require(!photo.has("storedRelativePath")) { "Transport path is not an immutable source fact" }
        }
    }
    private fun evidenceMetadataFingerprint(row:JSONObject):String = evidenceMetadataFingerprint(
        originWorkspaceId = row.getString("originWorkspaceId"),
        sourcePhotoId = row.getString("sourcePhotoId"),
        sourceVisitId = row.getString("sourceVisitId"),
        sourceWorkItemId = row.getString("sourceWorkItemId"),
        sourceFinalRevisionId = row.optNullable("sourceFinalRevisionId"),
        serviceDate = row.getString("serviceDate"),
        visitReference = row.getString("visitReference"),
        serviceName = row.getString("serviceName"),
        caption = row.optNullable("caption"),
        visibility = row.getString("visibility"),
        includedInCustomerReport = row.getBoolean("includedInCustomerReport"),
        customerWorkspaceId = row.optNullable("originCustomerWorkspaceId"),
        customerSourceId = row.optNullable("originCustomerSourceId"),
        siteWorkspaceId = row.optNullable("originSiteWorkspaceId"),
        siteSourceId = row.optNullable("originSiteSourceId"),
        equipmentWorkspaceId = row.optNullable("originEquipmentWorkspaceId"),
        equipmentSourceId = row.optNullable("originEquipmentSourceId"),
        mimeType = row.getString("mimeType"), width = row.getInt("width"), height = row.getInt("height"),
    )

    private fun evidenceMetadataFingerprint(value:TransferredEvidenceEntity):String {
        val provenance=JSONObject(value.provenanceJson)
        return evidenceMetadataFingerprint(
            originWorkspaceId=value.originWorkspaceId,sourcePhotoId=value.sourcePhotoId,sourceVisitId=value.sourceVisitId,
            sourceWorkItemId=value.sourceWorkItemId,sourceFinalRevisionId=value.sourceFinalRevisionId,serviceDate=value.serviceDate,
            visitReference=value.visitReference,serviceName=value.serviceName,caption=value.caption,visibility=value.visibility,
            includedInCustomerReport=value.includedInCustomerReport,
            customerWorkspaceId=provenance.optNullable("customerOriginWorkspaceId"),customerSourceId=provenance.optNullable("customerSourceEntityId"),
            siteWorkspaceId=provenance.optNullable("siteOriginWorkspaceId"),siteSourceId=provenance.optNullable("siteSourceEntityId"),
            equipmentWorkspaceId=provenance.optNullable("equipmentOriginWorkspaceId"),equipmentSourceId=provenance.optNullable("equipmentSourceEntityId"),
            mimeType=value.mimeType,width=value.width,height=value.height,
        )
    }

    private fun evidenceMetadataFingerprint(
        originWorkspaceId:String,sourcePhotoId:String,sourceVisitId:String,sourceWorkItemId:String,sourceFinalRevisionId:String?,
        serviceDate:String,visitReference:String,serviceName:String,caption:String?,visibility:String,includedInCustomerReport:Boolean,
        customerWorkspaceId:String?,customerSourceId:String?,siteWorkspaceId:String?,siteSourceId:String?,
        equipmentWorkspaceId:String?,equipmentSourceId:String?,mimeType:String,width:Int,height:Int,
    ):String {
        fun identity(workspace:String?,source:String?):Any = if(workspace==null&&source==null) JSONObject.NULL else JSONObject()
            .put("originWorkspaceId",workspace?.let{TechnicianIdCodec.normalize(it) ?: it} ?: JSONObject.NULL)
            .put("sourceEntityId",source ?: JSONObject.NULL)
        val canonicalMetadata=JSONObject().put("originWorkspaceId",TechnicianIdCodec.normalize(originWorkspaceId) ?: originWorkspaceId)
            .put("sourcePhotoId",sourcePhotoId).put("sourceVisitId",sourceVisitId).put("sourceWorkItemId",sourceWorkItemId)
            .put("sourceFinalRevisionId",sourceFinalRevisionId ?: JSONObject.NULL).put("serviceDate",serviceDate)
            .put("visitReference",visitReference).put("serviceName",serviceName).put("caption",caption ?: JSONObject.NULL)
            .put("visibility",visibility).put("includedInCustomerReport",includedInCustomerReport)
            // The origin* pairs are immutable source identities; receiver-local convenience IDs are deliberately absent.
            .put("customerIdentity",identity(customerWorkspaceId,customerSourceId))
            .put("siteIdentity",identity(siteWorkspaceId,siteSourceId)).put("equipmentIdentity",identity(equipmentWorkspaceId,equipmentSourceId))
            .put("mimeType",mimeType).put("width",width).put("height",height)
        return sha256(canonical(canonicalMetadata).toByteArray(Charsets.UTF_8))
    }

    private fun withNullableFields(value:JSONObject,vararg fields:String):JSONObject = JSONObject(value.toString()).apply {
        fields.forEach { key -> if(!has(key))put(key,JSONObject.NULL) }
    }
    private fun JSONObject.copyForFingerprint():JSONObject=JSONObject(toString()).apply{remove("originWorkspaceId");remove("sourceEntityId");remove("sourceRevisionId");remove("binaryName");remove("sha256");remove("byteSize");remove("width");remove("height");remove("mimeType")}
    private fun canonical(value:Any?):String=when(value){is JSONObject->value.keys().asSequence().toList().sorted().joinToString(",","{","}"){key->"${JSONObject.quote(key)}:${canonical(value.get(key))}"};is JSONArray->(0 until value.length()).joinToString(",","[","]"){canonical(value.get(it))};JSONObject.NULL,null->"null";is String->JSONObject.quote(value);else->value.toString()}
    private fun sourceKey(row:JSONObject,type:String):SourceEntityKey =
        SourceEntityKey(validOrigin(row.getString("originWorkspaceId")), SourceEntityType.valueOf(type), required(row,"sourceEntityId"))
    private fun parentKey(row:JSONObject,name:String):SourceEntityKey {
        val prefix="${name}OriginWorkspaceId";val source="${name}SourceEntityId"
        return SourceEntityKey(validOrigin(required(row,prefix)), SourceEntityType.valueOf(name.uppercase()), required(row,source))
    }
    private fun optionalRef(row:JSONObject,name:String):SourceEntityKey? {
        if (!row.has(name) || row.isNull(name)) return null
        val value=row.get(name)
        require(value is JSONObject) { "Invalid $name reference" }
        return sourceKey(value,name.uppercase())
    }
    private fun validOrigin(value:String)=TechnicianIdCodec.normalize(value)?:throw IllegalArgumentException("Invalid record origin workspace ID")
    private fun required(row:JSONObject,key:String,max:Int=4000):String {
        val value=row.get(key)
        require(value is String) { "Invalid $key type" }
        return value.trim().also { require(it.isNotEmpty()&&it.length<=max) { "Invalid $key" } }
    }
    private fun JSONObject.optNullable(key:String):String? = if (!has(key) || isNull(key)) null else {
        val value = get(key)
        require(value is String) { "Invalid $key type" }
        value.takeIf(String::isNotBlank)
    }
    private fun JSONArray.forEachJson(block:(JSONObject)->Unit){for(i in 0 until length())block(getJSONObject(i))}
    private suspend fun validateHistoryMappings(row:JSONObject,mapping:Map<SourceEntityKey,String>){listOf("customer","site","equipment").forEach{name->val key=optionalRef(row,name)?:return@forEach;if(key !in mapping){val type=name.uppercase();if(resolveBinding(key,type)==null)throw IllegalArgumentException("Transferred ${name} dependency is missing")}}}
    private suspend fun historyMappings(row:JSONObject,mapping:Map<SourceEntityKey,String>):Triple<String?,String?,String?>{
        suspend fun mapped(name:String,type:String):String?{val key=optionalRef(row,name)?:return null;return mapping[key]?:resolveBinding(key,type)}
        return Triple(mapped("customer","CUSTOMER"),mapped("site","SITE"),mapped("equipment","EQUIPMENT"))
    }
    private fun packageId(payload:DataTransferPayload)=payload.packageId
    private fun transferEvidenceSourceKey(origin:String,photo:String,revision:String?) = SourceIdentityKeys.evidence(origin,photo,revision)
    private fun relative(file:File)=filesRoot.canonicalFile.toPath().relativize(file.canonicalFile.toPath()).toString().replace('\\','/')
    private fun ownedFile(path:String):File{val root=filesRoot.canonicalFile;val file=File(root,path).canonicalFile;require(file.path.startsWith(root.path+File.separator)){"Unsafe evidence path"};return file}
    private fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
}
