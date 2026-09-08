package com.v16studio.serviceloop.data

import androidx.room.withTransaction
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

const val WORK_PACKAGE_MIME = "application/vnd.serviceloop.work-package+json"

data class DispatchCustomer(val reference: String, val name: String)
data class DispatchSite(val reference: String, val customerReference: String, val name: String, val address: String?)
data class DispatchEquipment(val reference: String, val siteReference: String, val name: String, val identifier: String?, val make: String?, val model: String?, val serial: String?)
data class DispatchWork(val equipmentReference: String, val taskName: String, val servicePlanReference: String? = null, val dueDateSnapshot: String? = null)
data class DispatchVisit(val dispatchVisitId: String, val managerReference: String?, val serviceDate: String, val scheduledAt: Long?, val siteReference: String, val instructions: String?, val work: List<DispatchWork>)
data class DispatchPackage(val packageId: String, val createdAt: String, val senderLabel: String, val recipientLabel: String?, val customers: List<DispatchCustomer>, val sites: List<DispatchSite>, val equipment: List<DispatchEquipment>, val visits: List<DispatchVisit>)

object DispatchPackageCodec {
    const val MAX_BYTES = 1_048_576
    private const val MAX_DIRECTORY = 500
    private const val MAX_VISITS = 100
    private const val MAX_WORK = 500
    private const val MAX_STRING = 4_000

    fun encode(value: DispatchPackage): ByteArray {
        validate(value)
        fun nullable(v: String?) = v ?: JSONObject.NULL
        val root = JSONObject().put("format", "ServiceLoopWorkPackage").put("formatVersion", 1)
            .put("packageId", value.packageId).put("createdAt", value.createdAt).put("senderLabel", value.senderLabel)
            .put("recipientLabel", nullable(value.recipientLabel))
        root.put("customers", JSONArray(value.customers.map { JSONObject().put("reference", it.reference).put("name", it.name) }))
        root.put("sites", JSONArray(value.sites.map { JSONObject().put("reference", it.reference).put("customerReference", it.customerReference).put("name", it.name).put("address", nullable(it.address)) }))
        root.put("equipment", JSONArray(value.equipment.map { JSONObject().put("reference", it.reference).put("siteReference", it.siteReference).put("name", it.name).put("identifier", nullable(it.identifier)).put("make", nullable(it.make)).put("model", nullable(it.model)).put("serial", nullable(it.serial)) }))
        root.put("visits", JSONArray(value.visits.map { visit -> JSONObject().put("dispatchVisitId", visit.dispatchVisitId).put("managerReference", nullable(visit.managerReference)).put("serviceDate", visit.serviceDate).put("scheduledAt", visit.scheduledAt ?: JSONObject.NULL).put("siteReference", visit.siteReference).put("instructions", nullable(visit.instructions)).put("work", JSONArray(visit.work.map { JSONObject().put("equipmentReference", it.equipmentReference).put("taskName", it.taskName).put("servicePlanReference", nullable(it.servicePlanReference)).put("dueDateSnapshot", nullable(it.dueDateSnapshot)) })) }))
        return root.toString(2).toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) { "Work package exceeds 1 MiB" } }
    }

    fun decode(bytes: ByteArray): DispatchPackage {
        require(bytes.size <= MAX_BYTES) { "Work package exceeds 1 MiB" }
        val root = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrElse { throw IllegalArgumentException("Malformed work package") }
        require(root.optString("format") == "ServiceLoopWorkPackage") { "Not a ServiceLoop work package" }
        require(root.optInt("formatVersion", -1) == 1) { "Unsupported work package version" }
        fun JSONObject.text(name: String) = getString(name).also { require(it.isNotBlank() && it.length <= MAX_STRING) { "Invalid $name" } }
        fun JSONObject.nullable(name: String) = if (isNull(name)) null else getString(name).also { require(it.length <= MAX_STRING) { "$name is too long" } }
        fun <T> array(name: String, limit: Int, read: (JSONObject) -> T): List<T> { val a=root.getJSONArray(name); require(a.length()<=limit){"Too many $name"}; return (0 until a.length()).map{read(a.getJSONObject(it))} }
        val customers=array("customers",MAX_DIRECTORY){DispatchCustomer(it.text("reference"),it.text("name"))}
        val sites=array("sites",MAX_DIRECTORY){DispatchSite(it.text("reference"),it.text("customerReference"),it.text("name"),it.nullable("address"))}
        val equipment=array("equipment",MAX_DIRECTORY){DispatchEquipment(it.text("reference"),it.text("siteReference"),it.text("name"),it.nullable("identifier"),it.nullable("make"),it.nullable("model"),it.nullable("serial"))}
        val visits=array("visits",MAX_VISITS){v-> val work=v.getJSONArray("work"); require(work.length() in 1..MAX_WORK){"Invalid work count"}; DispatchVisit(v.text("dispatchVisitId"),v.nullable("managerReference"),v.text("serviceDate"),if(v.isNull("scheduledAt"))null else v.getLong("scheduledAt"),v.text("siteReference"),v.nullable("instructions"),(0 until work.length()).map{w->work.getJSONObject(w).let{DispatchWork(it.text("equipmentReference"),it.text("taskName"),it.nullable("servicePlanReference"),it.nullable("dueDateSnapshot"))}})}
        return DispatchPackage(root.text("packageId"),root.text("createdAt"),root.text("senderLabel"),root.nullable("recipientLabel"),customers,sites,equipment,visits).also(::validate)
    }

    private fun validate(value: DispatchPackage) {
        require(value.packageId.isNotBlank() && value.senderLabel.isNotBlank())
        Instant.parse(value.createdAt)
        require(value.visits.isNotEmpty() && value.visits.size <= MAX_VISITS)
        require(value.visits.map { it.dispatchVisitId }.distinct().size == value.visits.size) { "Duplicate dispatch visit ID" }
        require(value.customers.map { it.reference }.distinct().size == value.customers.size)
        require(value.sites.map { it.reference }.distinct().size == value.sites.size)
        require(value.equipment.map { it.reference }.distinct().size == value.equipment.size)
        value.visits.forEach { LocalDate.parse(it.serviceDate); require(it.work.isNotEmpty()); it.work.forEach { w -> w.dueDateSnapshot?.let(LocalDate::parse) } }
        listOf(value.packageId,value.senderLabel,value.recipientLabel).filterNotNull().forEach { require(it.length<=MAX_STRING) }
    }
}

enum class DispatchClassification { EXISTING_UNCHANGED, NEW, CONFLICT, POSSIBLE_DUPLICATE }
enum class DispatchVisitClassification { NEW_VISIT, ALREADY_IMPORTED, CONFLICT }
data class DispatchPreviewLine(val reference: String, val classification: DispatchClassification, val detail: String)
data class DispatchVisitPreview(val dispatchVisitId: String, val classification: DispatchVisitClassification, val planLinked: Int, val oneOff: Int, val reasons: List<String>, val localVisitId: String? = null)
data class DispatchPreview(val value: DispatchPackage, val directory: List<DispatchPreviewLine>, val visits: List<DispatchVisitPreview>) { val canImport get()=directory.none{it.classification==DispatchClassification.CONFLICT}&&visits.none{it.classification==DispatchVisitClassification.CONFLICT} }
data class DispatchImportResult(val createdVisitIds: List<String>, val alreadyImportedVisitIds: List<String>)

class DispatchPackageService(private val database: ServiceLoopDatabase) {
    private val dao = database.serviceLoopDao()
    private fun normal(v:String?)=v.orEmpty().trim().lowercase().replace(Regex("\\s+")," ")
    private fun stable(vararg parts:String)=UUID.nameUUIDFromBytes(parts.joinToString("|").toByteArray()).toString()
    private fun hash(v:DispatchVisit)=MessageDigest.getInstance("SHA-256").digest(JSONObject().put("date",v.serviceDate).put("time",v.scheduledAt).put("site",v.siteReference).put("instructions",v.instructions).put("work",JSONArray(v.work.map{JSONObject().put("equipment",it.equipmentReference).put("task",it.taskName).put("plan",it.servicePlanReference).put("due",it.dueDateSnapshot)})).toString().toByteArray()).joinToString(""){"%02x".format(it)}
    private fun provenanceId(p:String,v:String)=stable("dispatch-import",p,v)

    suspend fun preview(value: DispatchPackage): DispatchPreview {
        val customers=dao.allCustomers(); val sites=dao.allSites(); val equipment=dao.allEquipment(); val plans=dao.allPlans()
        val directory=buildList {
            value.customers.forEach { x -> val exact=customers.find{it.reference==x.reference}; add(DispatchPreviewLine(x.reference,when{exact!=null&&normal(exact.name)==normal(x.name)->DispatchClassification.EXISTING_UNCHANGED;exact!=null->DispatchClassification.CONFLICT;customers.any{normal(it.name)==normal(x.name)}->DispatchClassification.POSSIBLE_DUPLICATE;else->DispatchClassification.NEW},"Customer · ${x.name}")) }
            value.sites.forEach { x -> val exact=sites.find{it.reference==x.reference}; val parent=exact?.let{site->customers.find{it.id==site.customerId}?.reference}; add(DispatchPreviewLine(x.reference,when{exact!=null&&parent==x.customerReference&&normal(exact.name)==normal(x.name)&&normal(exact.address)==normal(x.address)->DispatchClassification.EXISTING_UNCHANGED;exact!=null->DispatchClassification.CONFLICT;sites.any{normal(it.name)==normal(x.name)&&normal(it.address)==normal(x.address)}->DispatchClassification.POSSIBLE_DUPLICATE;else->DispatchClassification.NEW},"Site · ${x.name}")) }
            value.equipment.forEach { x -> val exact=equipment.find{it.reference==x.reference}; val parent=exact?.let{eq->sites.find{it.id==eq.siteId}?.reference}; add(DispatchPreviewLine(x.reference,when{exact!=null&&parent==x.siteReference&&listOf(exact.name,exact.technicianIdentifier,exact.make,exact.model,exact.serialNumber).map(::normal)==listOf(x.name,x.identifier,x.make,x.model,x.serial).map(::normal)->DispatchClassification.EXISTING_UNCHANGED;exact!=null->DispatchClassification.CONFLICT;equipment.any{normal(it.name)==normal(x.name)&&normal(it.serialNumber)==normal(x.serial)&&normal(x.serial).isNotBlank()}->DispatchClassification.POSSIBLE_DUPLICATE;else->DispatchClassification.NEW},"Equipment · ${x.name}")) }
        }
        val visits=value.visits.map { v ->
            val prior=dao.changeEntry(provenanceId(value.packageId,v.dispatchVisitId)); val material=hash(v)
            var linked=0; val reasons=mutableListOf<String>(); v.work.forEach { w -> val eq=equipment.find{it.reference==w.equipmentReference}; val site=eq?.let{e->sites.find{it.id==e.siteId}}; val customer=site?.let{s->customers.find{it.id==s.customerId}}; val plan=plans.find{it.reference==w.servicePlanReference}; val obligation=plan?.currentObligationId?.let{dao.obligation(it)}; val hierarchy=site?.reference==v.siteReference; val safe=hierarchy&&customer?.state=="ACTIVE"&&site.state=="ACTIVE"&&eq.state=="ACTIVE"&&plan?.equipmentId==eq.id&&plan.state=="ACTIVE"&&obligation!=null&&obligation.consumedAtEpochMillis==null&&dao.claimForObligation(obligation.id)==null&&(w.dueDateSnapshot==null||w.dueDateSnapshot==obligation.dueDate); if(safe) linked++ else if(w.servicePlanReference!=null) reasons += "${w.taskName}: plan cannot safely link; import as one-off" }
            DispatchVisitPreview(v.dispatchVisitId,when{prior==null->DispatchVisitClassification.NEW_VISIT;prior.newValue==material->DispatchVisitClassification.ALREADY_IMPORTED;else->DispatchVisitClassification.CONFLICT},linked,v.work.size-linked,reasons,prior?.subjectId)
        }
        return DispatchPreview(value,directory,visits)
    }

    suspend fun import(preview: DispatchPreview): DispatchImportResult {
        require(preview.canImport){"Resolve package conflicts before import"}; val value=preview.value; val created=mutableListOf<String>(); val existing=mutableListOf<String>()
        database.withTransaction {
            require(this@DispatchPackageService.preview(value).canImport) { "Package conflicts changed; review again" }
            val customers=dao.allCustomers().associateBy{it.reference}.toMutableMap(); value.customers.forEach{x->if(x.reference !in customers){CustomerEntity(stable("customer",value.packageId,x.reference),x.reference,x.name).also{dao.insertCustomers(listOf(it));customers[x.reference]=it}}}
            val sites=dao.allSites().associateBy{it.reference}.toMutableMap(); value.sites.forEach{x->if(x.reference !in sites){val c=customers[x.customerReference]?:error("Missing customer ${x.customerReference}");SiteEntity(stable("site",value.packageId,x.reference),c.id,x.reference,x.name,x.address,null).also{dao.insertSites(listOf(it));sites[x.reference]=it}}}
            val equipment=dao.allEquipment().associateBy{it.reference}.toMutableMap(); value.equipment.forEach{x->if(x.reference !in equipment){val s=sites[x.siteReference]?:error("Missing site ${x.siteReference}");EquipmentEntity(stable("equipment",value.packageId,x.reference),s.id,x.reference,x.identifier,x.name,x.make,x.model,x.serial,null).also{dao.insertEquipment(listOf(it));equipment[x.reference]=it}}}
            val plans=dao.allPlans().associateBy{it.reference}; val now=System.currentTimeMillis()
            value.visits.forEach { v -> val pid=provenanceId(value.packageId,v.dispatchVisitId); val prior=dao.changeEntry(pid); if(prior!=null){existing+=prior.subjectId;return@forEach}; val site=sites[v.siteReference]?:error("Missing site ${v.siteReference}"); val customer=customers.values.find{it.id==site.customerId}?:error("Missing customer"); val visitId=stable("dispatch-visit",value.packageId,v.dispatchVisitId); dao.insertVisits(listOf(WorkingVisitEntity(visitId,"D-${stable(value.packageId,v.dispatchVisitId).take(12)}",customer.id,site.id,v.serviceDate,customer.name,site.name,site.address,"BOOKED",now,customer.reference,site.reference,scheduledAtEpochMillis=v.scheduledAt)))
                v.work.forEachIndexed { index,w -> val eq=equipment[w.equipmentReference]?:error("Missing equipment ${w.equipmentReference}"); require(eq.siteId==site.id){"Work equipment is not at visit site"}; val candidate=plans[w.servicePlanReference]; val obligation=candidate?.currentObligationId?.let{dao.obligation(it)}; val safe=customer.state=="ACTIVE"&&site.state=="ACTIVE"&&eq.state=="ACTIVE"&&candidate?.equipmentId==eq.id&&candidate.state=="ACTIVE"&&obligation!=null&&obligation.consumedAtEpochMillis==null&&dao.claimForObligation(obligation.id)==null&&(w.dueDateSnapshot==null||w.dueDateSnapshot==obligation.dueDate); val workId=stable("dispatch-work",value.packageId,v.dispatchVisitId,index.toString()); dao.insertWorkItems(listOf(WorkItemEntity(workId,visitId,eq.id,candidate?.id.takeIf{safe},obligation?.id.takeIf{safe},null,eq.name,eq.reference,w.taskName,candidate?.reference.takeIf{safe},obligation?.dueDate.takeIf{safe},candidate?.intervalCount.takeIf{safe},candidate?.intervalUnit.takeIf{safe},false,null,false,equipmentIdentifierSnapshot=eq.technicianIdentifier,equipmentMakeSnapshot=eq.make,equipmentModelSnapshot=eq.model,equipmentSerialSnapshot=eq.serialNumber))); dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity(workId,""))); dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity(workId,listOfNotNull("Dispatched by ${value.senderLabel}; package ${value.packageId}",v.instructions?.takeIf{it.isNotBlank()}).joinToString("\n")))); if(safe)dao.insertVisitClaim(VisitClaimEntity(obligation!!.id,visitId,now)) }
                dao.insertChangeEntry(ChangeEntryEntity(pid,"VISIT",visitId,"DISPATCH_IMPORTED",v.serviceDate,now,"From ${value.senderLabel}; package ${value.packageId}",null,hash(v),customer.id,site.id,null,null,customer.name,site.name,null)); created+=visitId }
        }
        return DispatchImportResult(created,existing)
    }
}
