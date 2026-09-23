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

enum class DataTransferClassification { NEW, MATCH_EXISTING, ALREADY_CURRENT, NEW_HISTORY, ALREADY_IMPORTED, CONFLICT }

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
        val mapping: Map<Pair<String, String>, String>,
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
        val createdFiles = mutableListOf<File>()
        try {
            return BusinessFileCoordinator.mutex.withLock {
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
                    if (current.current.any { it.classification == DataTransferClassification.NEW || it.classification == DataTransferClassification.MATCH_EXISTING } ||
                        current.history.any { it.classification == DataTransferClassification.NEW_HISTORY } || current.evidence.any { it.classification == DataTransferClassification.NEW_HISTORY }) {
                        dao.recoveryMetadata()?.let { metadata -> dao.upsertRecoveryMetadata(metadata.copy(
                            firstBusinessWriteAtEpochMillis = metadata.firstBusinessWriteAtEpochMillis ?: importedAt,
                            lastBusinessWriteAtEpochMillis = importedAt,
                        )) }
                    }
                    DataTransferImportPreview(payload, preview.packageId, current.counts, current.items, current.templatePreview)
                }
            }
        } catch (failure: Throwable) {
            createdFiles.forEach { runCatching { it.delete() } }
            throw failure
        }
    }

    private suspend fun buildPlan(payload: DataTransferPayload, createSeparateTemplates: Set<String>): PlanSet {
        require(payload.sourceWorkspaceId.isNotBlank() && TechnicianIdCodec.normalize(payload.sourceWorkspaceId) == payload.sourceWorkspaceId) { "Invalid source workspace" }
        require(TechnicianIdCodec.normalize(payload.exporterId) == payload.exporterId) { "Invalid exporting ServiceLoop ID" }
        val current = mutableListOf<CurrentPlan>()
        val history = mutableListOf<ParsedHistory>()
        val items = mutableListOf<DataTransferImportItem>()
        val mapping = linkedMapOf<Pair<String, String>, String>()
        val register = payload.families[DataTransferFamily.REGISTER]?.let { JSONObject(it.toString(Charsets.UTF_8)) }
        validateRegister(register)
        if (register != null) {
            val customers = register.getJSONArray("customers")
            for (i in 0 until customers.length()) {
                val row = customers.getJSONObject(i); val id = sourceKey(row); val fp = fingerprint(row)
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
                val row = sites.getJSONObject(i); val id = sourceKey(row); val customerKey = parentKey(row, "customer")
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
                val row = equipment.getJSONObject(i); val id = sourceKey(row); val siteKey = parentKey(row,"site")
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
                val row=contacts.getJSONObject(i);val id=sourceKey(row);val parent=parentKey(row,"customer");val customerId=mapping[parent]?:resolveBinding(parent,"CUSTOMER")
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
            current+=plan;mapping[sourceOrigin to sourceId]=localId
            items+=DataTransferImportItem(DataTransferFamily.INSPECTION_TEMPLATES,if(classification==DataTransferClassification.CONFLICT)entry.reference else "$sourceOrigin:$sourceId",classification,"Template ${entry.reference}",if(classification==DataTransferClassification.CONFLICT)"An inspection template with this reference or bound source has different content."else null)
        }

        val plansRoot=payload.families[DataTransferFamily.SERVICE_PLANS]?.let{JSONObject(it.toString(Charsets.UTF_8))}
        validatePlans(plansRoot)
        plansRoot?.getJSONArray("plans")?.let{rows->for(i in 0 until rows.length()){
            val row=rows.getJSONObject(i);val id=sourceKey(row);val equipmentKey=parentKey(row,"equipment");val equipmentId=mapping[equipmentKey]?:resolveBinding(equipmentKey,"EQUIPMENT")
            if(equipmentId==null){current+=conflictPlan(DataTransferFamily.SERVICE_PLANS,"SERVICE_PLAN",row,"Service Plan has no resolvable Equipment dependency");items+=display(current.last(),"Plan ${row.optString("reference")}");continue}
            val templateId=if(row.isNull("templateSourceEntityId"))null else {
                val key=row.optString("templateOriginWorkspaceId") to row.optString("templateSourceEntityId")
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
            val existing=dao.customerContacts(customerId);val next=existing.size
            val ordered=contacts.sortedWith(compareBy<CurrentPlan>{it.json.optInt("position",Int.MAX_VALUE)}.thenBy{it.sourceId})
            dao.insertCustomerContacts(ordered.mapIndexed { index,item -> val row=item.json;CustomerContactEntity(item.localId,customerId,row.optNullable("personName"),row.getString("channel"),row.getString("value"),now,now,row.optNullable("notes"),next+index) })
        }
        inserted.filter { it.type == "CUSTOMER" || it.type == "SITE" || it.type == "EQUIPMENT" || it.type == "CUSTOMER_CONTACT" }.forEach { insertBinding(it,now) }
        items.filter { it.type in setOf("CUSTOMER","SITE","EQUIPMENT","CUSTOMER_CONTACT") && it.classification==DataTransferClassification.MATCH_EXISTING }.forEach { insertBinding(it,now) }
    }

    private suspend fun applyTemplates(plan: PlanSet, payload: DataTransferPayload, createSeparate: Set<String>, now: Long): Map<Pair<String,String>,String> {
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
        val out=linkedMapOf<Pair<String,String>,String>()
        plan.templateEntries.forEach { entry ->
            val origin=entry.originWorkspaceId?:payload.sourceWorkspaceId;val source=entry.sourceEntityId?:entry.reference
            val existingBinding=dao.dataTransferBinding(origin,"INSPECTION_TEMPLATE",source)
            val target=existingBinding?.localEntityId?.let{dao.reusableTemplate(it)} ?: run {
                val separateReference=if(entry.reference in createSeparate) result.createdSeparateReferences.firstOrNull{it.startsWith("${entry.reference}-imported-")} else null
                val wantedReference=separateReference?:entry.reference
                localRows.firstOrNull{it.reference==wantedReference} ?: localRows.firstOrNull{it.reference.startsWith("${entry.reference}-imported-")&&templateContentEquals(it,entry)}
            } ?: error("Imported inspection template could not be resolved")
            out[origin to source]=target.id
            if(existingBinding==null) dao.insertDataTransferBinding(DataTransferBindingEntity(UUID.randomUUID().toString(),origin,"INSPECTION_TEMPLATE",source,target.id,InspectionTemplateCodec.fingerprint(entry),now))
        }
        return out
    }

    private suspend fun applyPlans(items: List<CurrentPlan>, mapping: Map<Pair<String,String>,String>, templateIds: Map<Pair<String,String>,String>, now: Long) {
        val plans=items.filter{it.type=="SERVICE_PLAN"}
        plans.filter{it.classification==DataTransferClassification.NEW}.forEach { item ->
            val row=item.json;val templateKey=if(row.isNull("templateSourceEntityId"))null else row.optString("templateOriginWorkspaceId") to row.optString("templateSourceEntityId")
            val templateId=templateKey?.let{templateIds[it]?:mapping[it]}
            val active=row.optString("state")=="ACTIVE";val obligationId=if(active)UUID.randomUUID().toString()else null
            dao.insertPlans(listOf(ServicePlanEntity(item.localId,item.parentLocalId!!,row.getString("reference"),row.getString("name"),row.getInt("intervalCount"),row.getString("intervalUnit"),row.getString("currentDueDate"),row.getString("state"),obligationId,reusableTemplateId=templateId)))
            if(obligationId!=null)dao.insertObligations(listOf(ServiceObligationEntity(obligationId,item.localId,1,row.getString("currentDueDate"),now,null,null)))
        }
        plans.filter{it.classification in setOf(DataTransferClassification.NEW,DataTransferClassification.MATCH_EXISTING)}.forEach{insertBinding(it,now)}
    }

    private suspend fun applyHistory(history: List<ParsedHistory>, payload: DataTransferPayload, mapping: Map<Pair<String,String>,String>, staged: Map<String,File>, now: Long) {
        history.filter{it.family!=DataTransferFamily.PERFORMED_WORK}.filter{it.classification==DataTransferClassification.NEW_HISTORY}.forEach{item->
            val row=item.source;val refs=historyMappings(row,mapping)
            dao.insertTransferredHistoryEntries(listOf(TransferredHistoryEntryEntity(UUID.randomUUID().toString(),row.getString("originWorkspaceId"),item.family.name,row.getString("sourceEntityId"),row.optNullable("sourceRevisionId"),row.optNullable("sourceRevisionId")?:"",payload.exporterId,refs.first,refs.second,refs.third,row.optNullable("eventDateTime"),row.getJSONObject("payload").toString(),item.fingerprint,now)))
        }
        history.filter{it.family==DataTransferFamily.PERFORMED_WORK}.filter{it.classification==DataTransferClassification.NEW_HISTORY}.forEach{item->
            val row=item.source;val refs=historyMappings(row,mapping);val subject=row.getJSONObject("subjectSnapshot");val recurrence=row.getJSONObject("recurrence")
            val evidence=row.optJSONArray("evidence")?:JSONArray()
            val entity=TransferredFinalResultEntity(UUID.randomUUID().toString(),row.getString("originWorkspaceId"),row.getString("sourceVisitId"),row.getString("sourceWorkItemId"),row.getString("sourceFinalRevisionId"),row.getString("logicalResultId"),payload.exporterId,now,refs.first,refs.second,refs.third,
                row.getJSONObject("customerSnapshot").toString(),row.getJSONObject("siteSnapshot").toString(),subject.toString(),row.getString("visitReference"),row.getString("serviceDate"),row.getString("technicianId"),row.getString("technicianName"),row.optNullable("technicianDesignation"),row.getString("serviceName"),row.getString("outcome"),row.optNullable("workPerformed"),row.optNullable("notPerformedReason"),row.getJSONArray("checklist").toString(),row.getJSONArray("findings").toString(),row.getJSONArray("parts").toString(),row.optNullable("internalNotes"),recurrence.toString(),item.fingerprint,JSONObject().put("recordedAt",row.optString("recordedAt")).put("sourceWorkItemPosition",row.optInt("sourceWorkItemPosition",0)).put("relayExporterId",payload.exporterId).put("evidence",evidence).toString())
            dao.insertTransferredFinalResults(listOf(entity))
        }
    }

    private suspend fun applyEvidence(evidence: List<ParsedEvidence>, payload: DataTransferPayload, mapping: Map<Pair<String,String>,String>, staged: Map<String,File>, now: Long) {
        evidence.filter{it.classification==DataTransferClassification.NEW_HISTORY}.forEach{item->
            val row=item.value;val customerKey=optionalRef(row,"customer");val siteKey=optionalRef(row,"site");val equipmentKey=optionalRef(row,"equipment")
            val customerId=customerKey?.let{mapping[it]?:resolveBinding(it,"CUSTOMER")};val siteId=siteKey?.let{mapping[it]?:resolveBinding(it,"SITE")};val equipmentId=equipmentKey?.let{mapping[it]?:resolveBinding(it,"EQUIPMENT")}
            val result=dao.transferredFinalResult(row.getString("originWorkspaceId"),row.getString("sourceWorkItemId"),row.optString("sourceFinalRevisionId"))
            val target=staged.getValue(item.key)
            dao.insertTransferredEvidence(listOf(TransferredEvidenceEntity(UUID.randomUUID().toString(),item.key,row.getString("originWorkspaceId"),row.getString("sourcePhotoId"),row.getString("sourceVisitId"),row.getString("sourceWorkItemId"),row.optNullable("sourceFinalRevisionId"),result?.id,payload.exporterId,customerId,siteId,equipmentId,row.getString("serviceDate"),row.getString("visitReference"),row.getString("serviceName"),relative(target),item.hash,item.bytes.size.toLong(),row.getInt("width"),row.getInt("height"),row.optString("mimeType","image/jpeg"),row.optNullable("caption"),row.getString("visibility"),row.optBoolean("includedInCustomerReport"),now,
                JSONObject().put("customerOriginWorkspaceId",row.optString("originCustomerWorkspaceId")).put("customerSourceEntityId",row.optString("originCustomerSourceId")).put("siteOriginWorkspaceId",row.optString("originSiteWorkspaceId")).put("siteSourceEntityId",row.optString("originSiteSourceId")).put("equipmentOriginWorkspaceId",row.optString("originEquipmentWorkspaceId")).put("equipmentSourceEntityId",row.optString("originEquipmentSourceId")).put("relayExporterId",payload.exporterId).toString())))
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

    private suspend fun parsePerformed(payload: DataTransferPayload, mapping: Map<Pair<String,String>,String>, history: MutableList<ParsedHistory>, items: MutableList<DataTransferImportItem>) {
        val bytes=payload.families[DataTransferFamily.PERFORMED_WORK]?:return;val root=JSONObject(bytes.toString(Charsets.UTF_8));require(root.getInt("version")==1)
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
                require(resultKeys.add(Triple(recordOrigin,work,revision))){"Duplicate performed revision"}
                validateHistoryMappings(row,mapping)
                val key="${recordOrigin}:$work:$revision";val fp=fingerprint(row)
                val old=dao.transferredFinalResult(recordOrigin,work,revision)
                val classification=when{old==null->DataTransferClassification.NEW_HISTORY;old.payloadSha256==fp->DataTransferClassification.ALREADY_IMPORTED;else->DataTransferClassification.CONFLICT}
                val source=row
                history+=ParsedHistory(DataTransferFamily.PERFORMED_WORK,key,source,fp,classification)
                items+=DataTransferImportItem(DataTransferFamily.PERFORMED_WORK,key,classification,"${visit.getString("visitReference")} · ${row.getString("serviceName")}",if(classification==DataTransferClassification.CONFLICT)"The same immutable source revision has different content."else null)
            }
        }
    }

    private suspend fun parseHistory(payload: DataTransferPayload, mapping: Map<Pair<String,String>,String>, history: MutableList<ParsedHistory>, items: MutableList<DataTransferImportItem>) {
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

    private suspend fun parseEvidence(payload: DataTransferPayload, mapping: Map<Pair<String,String>,String>, items: MutableList<DataTransferImportItem>): List<ParsedEvidence> {
        val bytes=payload.families[DataTransferFamily.EVIDENCE]?:return emptyList();val array=JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("photos");val seen=mutableSetOf<String>();val result=mutableListOf<ParsedEvidence>()
        for(i in 0 until array.length()){
            val row=array.getJSONObject(i);val origin=validOrigin(row.getString("originWorkspaceId"));val photo=required(row,"sourcePhotoId");val revision=row.optString("sourceFinalRevisionId").takeIf{it.isNotBlank()&&it!="null"};val key=transferEvidenceSourceKey(origin,photo,revision)
            require(seen.add(key)){"Duplicate evidence source identity"};val binaryName=required(row,"binaryName");val content=payload.binaries[binaryName]?:error("Evidence binary is missing");val hash=required(row,"sha256")
            require(content.size==row.getLong("byteSize").toInt()&&sha256(content)==hash){"Evidence binary integrity check failed"};require(row.getString("visibility") in setOf("PUBLIC","PRIVATE","INTERNAL"))
            require(row.getString("mimeType")=="image/jpeg"&&row.getInt("width") in 1..1200&&row.getInt("height") in 1..1200&&maxOf(row.getInt("width"),row.getInt("height"))<=1200&&content.size>=4&&content[0]==0xFF.toByte()&&content[1]==0xD8.toByte()){"Transferred evidence is not a bounded JPEG derivative"}
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(content,0,content.size,bounds);require(bounds.outWidth==row.getInt("width")&&bounds.outHeight==row.getInt("height")){"Transferred evidence dimensions do not match"}
            validateHistoryMappings(row,mapping)
            val existing=dao.transferredEvidenceBySourceKey(key);val classification=when{existing==null->DataTransferClassification.NEW_HISTORY;existing.sha256==hash&&existing.byteSize==content.size.toLong()->DataTransferClassification.ALREADY_IMPORTED;else->DataTransferClassification.CONFLICT}
            result+=ParsedEvidence(row,content,key,hash,classification);items+=DataTransferImportItem(DataTransferFamily.EVIDENCE,key,classification,"Photo · ${row.optString("visitReference")}",if(classification==DataTransferClassification.CONFLICT)"The same photo revision has different bytes or metadata."else null)
        }
        return result
    }

    private fun validateRegister(root:JSONObject?) {
        if(root==null)return;require(root.getInt("version")==1)
        val seen=mutableMapOf<String,MutableSet<String>>();val arrays=mapOf("CUSTOMER" to "customers","CUSTOMER_CONTACT" to "contacts","SITE" to "sites","EQUIPMENT" to "equipment")
        arrays.forEach{(type,name)->val array=root.getJSONArray(name);val set=mutableSetOf<String>();seen[type]=set;for(i in 0 until array.length()){val row=array.getJSONObject(i);val key=sourceKey(row);require(set.add("${key.first}:${key.second}")){"Duplicate $type source"};when(type){"CUSTOMER"->{required(row,"reference");required(row,"name");require(row.optString("customerType","STANDARD") in setOf("STANDARD","ONE_TIME"))};"CUSTOMER_CONTACT"->{required(row,"value");require(row.getString("channel") in setOf("PHONE","SMS","WHATSAPP","EMAIL","OTHER"));require(row.has("position")&&row.optInt("position")>=0){"Invalid Customer contact position"}};else->required(row,"reference")}}}
        root.getJSONArray("sites").forEachJson{row->parentKey(row,"customer")};root.getJSONArray("equipment").forEachJson{row->parentKey(row,"site")};root.getJSONArray("contacts").forEachJson{row->parentKey(row,"customer")}
    }

    private fun validatePlans(root:JSONObject?) { if(root==null)return;require(root.getInt("version")==1);val a=root.getJSONArray("plans");val seen=mutableSetOf<String>();for(i in 0 until a.length()){val row=a.getJSONObject(i);val id=sourceKey(row);require(seen.add("${id.first}:${id.second}"));parentKey(row,"equipment");required(row,"reference");required(row,"name");require(row.getInt("intervalCount")>0&&row.getString("intervalUnit") in setOf("DAYS","WEEKS","MONTHS","YEARS"));LocalDate.parse(row.getString("currentDueDate"));require(row.getString("state") in setOf("ACTIVE","ARCHIVED","RETIRED","DISABLED"))} }

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

    private suspend fun resolveBinding(key:Pair<String,String>,type:String):String? {
        val local=dao.dataTransferBinding(key.first,type,key.second)?.localEntityId?:return null
        return when(type){"CUSTOMER"->if(dao.customer(local)!=null)local else null;"SITE"->if(dao.site(local)!=null)local else null;"EQUIPMENT"->if(dao.equipment(local)!=null)local else null;"SERVICE_PLAN"->if(dao.plan(local)!=null)local else null;"INSPECTION_TEMPLATE"->if(dao.reusableTemplate(local)!=null)local else null;"CUSTOMER_CONTACT"->if(contactById(local)!=null)local else null;else->null}
    }
    private suspend fun contactById(id:String):CustomerContactEntity? {
        for(customer in dao.allCustomers()) { val found=dao.customerContacts(customer.id).firstOrNull{it.id==id};if(found!=null)return found }
        return null
    }
    private suspend fun templateContentEquals(template:ReusableTemplateEntity,entry:InspectionTemplateTransferEntry):Boolean{val rev=dao.reusableTemplateRevision(template.currentRevisionId)?:return false;val items=dao.reusableTemplateItems(rev.id).map{DispatchInspectionItem(it.position,it.label,it.responseType,it.unit,it.required,it.privateGuidance)};return InspectionTemplateCodec.contentFingerprint(InspectionTemplateTransferEntry(template.reference,rev.nameSnapshot,rev.revisionNumber,items))==InspectionTemplateCodec.contentFingerprint(entry)}
    private suspend fun templateContentFingerprint(template:ReusableTemplateEntity):String?{val rev=dao.reusableTemplateRevision(template.currentRevisionId)?:return null;val items=dao.reusableTemplateItems(rev.id).map{DispatchInspectionItem(it.position,it.label,it.responseType,it.unit,it.required,it.privateGuidance)};return InspectionTemplateCodec.contentFingerprint(InspectionTemplateTransferEntry(template.reference,rev.nameSnapshot,rev.revisionNumber,items))}

    private fun customerJson(row:CustomerEntity,source:JSONObject)=JSONObject().put("originWorkspaceId",source.optString("originWorkspaceId")).put("sourceEntityId",source.optString("sourceEntityId")).put("reference",row.reference).put("name",row.name).put("customerType",row.customerType).put("state",row.state).apply{if(source.has("privateFieldsIncluded"))put("privateFieldsIncluded",true).put("contactName",row.contactName).put("phone",row.phone).put("email",row.email).put("privateNote",row.privateNote)}
    private fun siteJson(row:SiteEntity,source:JSONObject)=JSONObject().put("originWorkspaceId",source.optString("originWorkspaceId")).put("sourceEntityId",source.optString("sourceEntityId")).put("customerOriginWorkspaceId",source.optString("customerOriginWorkspaceId")).put("customerSourceEntityId",source.optString("customerSourceEntityId")).put("reference",row.reference).put("name",row.name).put("isDefault",row.isDefault).put("state",row.state).apply{if(source.has("privateFieldsIncluded"))put("privateFieldsIncluded",true).put("address",row.address).put("privateAccessNotes",row.privateAccessNotes).put("contactName",row.contactName).put("phone",row.phone).put("email",row.email)}
    private fun equipmentJson(row:EquipmentEntity,source:JSONObject)=JSONObject().put("originWorkspaceId",source.optString("originWorkspaceId")).put("sourceEntityId",source.optString("sourceEntityId")).put("siteOriginWorkspaceId",source.optString("siteOriginWorkspaceId")).put("siteSourceEntityId",source.optString("siteSourceEntityId")).put("reference",row.reference).put("name",row.name).put("state",row.state).apply{if(source.has("privateFieldsIncluded"))put("privateFieldsIncluded",true).put("technicianIdentifier",row.technicianIdentifier).put("make",row.make).put("model",row.model).put("serialNumber",row.serialNumber).put("privateNotes",row.privateNotes)}
    private fun contactJson(row:CustomerContactEntity,source:JSONObject)=JSONObject().put("originWorkspaceId",source.optString("originWorkspaceId")).put("sourceEntityId",source.optString("sourceEntityId")).put("customerOriginWorkspaceId",source.optString("customerOriginWorkspaceId")).put("customerSourceEntityId",source.optString("customerSourceEntityId")).put("position",source.optInt("position")).put("personName",row.personName).put("channel",row.channel).put("value",row.value).apply{if(source.has("notes"))put("notes",row.notes)}
    private fun planJson(row:ServicePlanEntity,source:JSONObject)=JSONObject().put("originWorkspaceId",source.optString("originWorkspaceId")).put("sourceEntityId",source.optString("sourceEntityId")).put("customerOriginWorkspaceId",source.optString("customerOriginWorkspaceId")).put("customerSourceEntityId",source.optString("customerSourceEntityId")).put("siteOriginWorkspaceId",source.optString("siteOriginWorkspaceId")).put("siteSourceEntityId",source.optString("siteSourceEntityId")).put("equipmentOriginWorkspaceId",source.optString("equipmentOriginWorkspaceId")).put("equipmentSourceEntityId",source.optString("equipmentSourceEntityId")).put("templateOriginWorkspaceId",source.optString("templateOriginWorkspaceId")).put("templateSourceEntityId",source.optString("templateSourceEntityId")).put("reference",row.reference).put("name",row.name).put("intervalCount",row.intervalCount).put("intervalUnit",row.intervalUnit).put("currentDueDate",row.currentDueDate).put("state",row.state)
    private fun normalizedContactMatch(local:CustomerContactEntity,incoming:JSONObject)=local.channel.trim().uppercase()==incoming.optString("channel").trim().uppercase()&&local.value.trim()==incoming.optString("value").trim()&&local.personName.orEmpty().trim()==incoming.optString("personName").trim()&&(!incoming.has("notes")||local.notes.orEmpty().trim()==incoming.optString("notes").trim())
    private fun fingerprint(value:JSONObject):String=sha256(canonical(value.copyForFingerprint()).toByteArray(Charsets.UTF_8))
    private fun JSONObject.copyForFingerprint():JSONObject=JSONObject(toString()).apply{remove("originWorkspaceId");remove("sourceEntityId");remove("sourceRevisionId");remove("binaryName");remove("sha256");remove("byteSize");remove("width");remove("height");remove("mimeType")}
    private fun canonical(value:Any?):String=when(value){is JSONObject->value.keys().asSequence().toList().sorted().joinToString(",","{","}"){key->"${JSONObject.quote(key)}:${canonical(value.get(key))}"};is JSONArray->(0 until value.length()).joinToString(",","[","]"){canonical(value.get(it))};JSONObject.NULL,null->"null";is String->JSONObject.quote(value);else->value.toString()}
    private fun sourceKey(row:JSONObject):Pair<String,String> = validOrigin(row.getString("originWorkspaceId")) to required(row,"sourceEntityId")
    private fun parentKey(row:JSONObject,name:String):Pair<String,String> { val prefix="${name}OriginWorkspaceId";val source="${name}SourceEntityId";return validOrigin(required(row,prefix)) to required(row,source) }
    private fun optionalRef(row:JSONObject,name:String):Pair<String,String>? {val value=row.optJSONObject(name)?:return null;return sourceKey(value)}
    private fun validOrigin(value:String)=TechnicianIdCodec.normalize(value)?:throw IllegalArgumentException("Invalid record origin workspace ID")
    private fun required(row:JSONObject,key:String,max:Int=4000)=row.getString(key).trim().also{require(it.isNotEmpty()&&it.length<=max){"Invalid $key"}}
    private fun JSONObject.optNullable(key:String):String?=if(!has(key)||isNull(key))null else optString(key).takeIf{it!="null"&&it.isNotBlank()}
    private fun JSONArray.forEachJson(block:(JSONObject)->Unit){for(i in 0 until length())block(getJSONObject(i))}
    private suspend fun validateHistoryMappings(row:JSONObject,mapping:Map<Pair<String,String>,String>){listOf("customer","site","equipment").forEach{name->val key=optionalRef(row,name)?:return@forEach;if(key !in mapping){val type=name.uppercase();if(resolveBinding(key,type)==null)throw IllegalArgumentException("Transferred ${name} dependency is missing")}}}
    private suspend fun historyMappings(row:JSONObject,mapping:Map<Pair<String,String>,String>):Triple<String?,String?,String?>{
        suspend fun mapped(name:String,type:String):String?{val key=optionalRef(row,name)?:return null;return mapping[key]?:resolveBinding(key,type)}
        return Triple(mapped("customer","CUSTOMER"),mapped("site","SITE"),mapped("equipment","EQUIPMENT"))
    }
    private fun packageId(payload:DataTransferPayload)=payload.packageId
    private fun transferEvidenceSourceKey(origin:String,photo:String,revision:String?)="$origin|$photo|${revision?:"standalone"}"
    private fun relative(file:File)=filesRoot.canonicalFile.toPath().relativize(file.canonicalFile.toPath()).toString().replace('\\','/')
    private fun ownedFile(path:String):File{val root=filesRoot.canonicalFile;val file=File(root,path).canonicalFile;require(file.path.startsWith(root.path+File.separator)){"Unsafe evidence path"};return file}
    private fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
}
