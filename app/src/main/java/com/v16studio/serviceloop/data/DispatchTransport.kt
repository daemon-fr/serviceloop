package com.v16studio.serviceloop.data

import androidx.room.withTransaction
import com.v16studio.serviceloop.domain.CustomerType
import com.v16studio.serviceloop.domain.VisitCancellationOrigin
import com.v16studio.serviceloop.domain.WorkSubjectType
import com.v16studio.serviceloop.domain.CustomerWithFirstSiteInput
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

const val WORK_PACKAGE_MIME = "application/vnd.serviceloop.work-package+json"
const val TECHNICIAN_IDENTITY_MIME = "application/vnd.serviceloop.technician+json"
const val INSPECTION_TEMPLATES_MIME = "application/vnd.serviceloop.inspection-templates+json"

data class TechnicianIdentity(val technicianId: String, val name: String, val designation: String? = null)
data class DispatchTechnicianSnapshot(val technicianId: String, val name: String, val designation: String? = null)
data class DispatchTeamSnapshot(val teamId: String, val name: String, val memberIds: List<String>, val leaderIds: List<String>)
data class DispatchCustomer(
    val reference: String,
    val name: String,
    val customerType: CustomerType = CustomerType.STANDARD,
)
data class DispatchSite(val reference: String, val customerReference: String, val name: String, val address: String?)
data class DispatchEquipment(val reference: String, val siteReference: String, val name: String, val identifier: String?, val make: String?, val model: String?, val serial: String?)
data class DispatchInspectionItem(val position: Int, val label: String, val responseType: String, val unit: String?, val required: Boolean, val privateGuidance: String?)
data class DispatchInspectionSnapshot(val snapshotId: String, val templateName: String, val sourceTemplateReference: String?, val sourceRevision: Int?, val items: List<DispatchInspectionItem>)
data class DispatchWork(
    val dispatchItemId: String,
    val subjectType: WorkSubjectType,
    val equipmentReference: String? = null,
    val equipmentDescription: String? = null,
    val taskName: String,
    val servicePlanReference: String? = null,
    val dueDateSnapshot: String? = null,
    val assignedTechnicians: List<DispatchTechnicianSnapshot> = emptyList(),
    val inspectionSnapshotId: String? = null,
) {
    /** Source compatibility for existing known-equipment fixtures. */
    constructor(
        dispatchItemId: String,
        equipmentReference: String,
        taskName: String,
        servicePlanReference: String? = null,
        dueDateSnapshot: String? = null,
        assignedTechnicians: List<DispatchTechnicianSnapshot> = emptyList(),
        inspectionSnapshotId: String? = null,
    ) : this(
        dispatchItemId,
        WorkSubjectType.EQUIPMENT,
        equipmentReference,
        null,
        taskName,
        servicePlanReference,
        dueDateSnapshot,
        assignedTechnicians,
        inspectionSnapshotId,
    )
}
data class DispatchVisit(val dispatchVisitId: String, val generation: Int, val managerReference: String?, val serviceDate: String, val appointmentLocalTime: String?, val appointmentZoneId: String, val siteReference: String, val instructions: String?, val teams: List<DispatchTeamSnapshot>, val participants: List<DispatchTechnicianSnapshot>, val leaderTechnicianIds: List<String>, val work: List<DispatchWork>, val transportLifecycle: String = "ACTIVE", val cancellationReason: String? = null)
data class DispatchPackage(val packageId: String, val createdAt: String, val senderLabel: String, val customers: List<DispatchCustomer>, val sites: List<DispatchSite>, val equipment: List<DispatchEquipment>, val visits: List<DispatchVisit>, val inspectionSnapshots: List<DispatchInspectionSnapshot> = emptyList())

object TechnicianIdentityCodec {
    const val MAX_BYTES = 16_384
    fun encode(value: TechnicianIdentity): ByteArray {
        require(value.name.trim().isNotEmpty()) { "Technician name is required" }
        require(value.name.length <= 200 && value.designation.orEmpty().length <= 200) { "Technician identity text is too long" }
        return JSONObject().put("format", "ServiceLoopTechnician").put("formatVersion", 2).put("technicianId", value.technicianId).put("name", value.name.trim()).put("designation", value.designation?.trim()?.takeIf(String::isNotEmpty) ?: JSONObject.NULL).toString(2).toByteArray()
    }
    fun decode(bytes: ByteArray): TechnicianIdentity {
        require(bytes.size in 1..MAX_BYTES) { "Invalid technician identity file size" }
        val root = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrElse { throw IllegalArgumentException("Malformed technician identity file") }
        require(root.optString("format") == "ServiceLoopTechnician" && root.optInt("formatVersion") == 2) { "Unsupported technician identity file" }
        val id = root.getString("technicianId").trim(); val name = root.getString("name").trim(); val designation = if (root.isNull("designation")) null else root.getString("designation").trim().takeIf(String::isNotEmpty)
        require(id.length in 16..200 && name.length in 1..200 && designation.orEmpty().length <= 200) { "Invalid technician identity" }
        return TechnicianIdentity(id, name, designation)
    }
}

object DispatchPackageCodec {
    const val MAX_BYTES = 1_048_576
    private const val MAX_DIRECTORY = 500
    const val MAX_VISITS = 100
    const val CURRENT_VERSION = 5
    private const val MAX_WORK = 500
    private const val MAX_STRING = 4_000

    fun encode(value: DispatchPackage): ByteArray {
        validate(value)
        fun nullable(v: Any?) = v ?: JSONObject.NULL
        fun tech(v: DispatchTechnicianSnapshot) = JSONObject().put("technicianId", v.technicianId).put("name", v.name).put("designation", nullable(v.designation))
        val root = JSONObject().put("format", "ServiceLoopWorkPackage").put("formatVersion", CURRENT_VERSION).put("packageId", value.packageId).put("createdAt", value.createdAt).put("senderLabel", value.senderLabel)
        root.put("customers", JSONArray(value.customers.map { JSONObject().put("reference", it.reference).put("name", it.name).put("customerType", it.customerType.code) }))
        root.put("sites", JSONArray(value.sites.map { JSONObject().put("reference", it.reference).put("customerReference", it.customerReference).put("name", it.name).put("address", nullable(it.address)) }))
        root.put("equipment", JSONArray(value.equipment.map { JSONObject().put("reference", it.reference).put("siteReference", it.siteReference).put("name", it.name).put("identifier", nullable(it.identifier)).put("make", nullable(it.make)).put("model", nullable(it.model)).put("serial", nullable(it.serial)) }))
        root.put("visits", JSONArray(value.visits.map { visit -> JSONObject().put("dispatchVisitId", visit.dispatchVisitId).put("generation", visit.generation).put("managerReference", nullable(visit.managerReference)).put("serviceDate", visit.serviceDate).put("appointmentLocalTime", nullable(visit.appointmentLocalTime)).put("appointmentZoneId", visit.appointmentZoneId).put("siteReference", visit.siteReference).put("instructions", nullable(visit.instructions)).put("transportLifecycle", visit.transportLifecycle).put("cancellationReason", nullable(visit.cancellationReason)).put("teams", JSONArray(visit.teams.map { JSONObject().put("teamId", it.teamId).put("name", it.name).put("memberIds", JSONArray(it.memberIds)).put("leaderIds", JSONArray(it.leaderIds)) })).put("participants", JSONArray(visit.participants.map(::tech))).put("leaderTechnicianIds", JSONArray(visit.leaderTechnicianIds)).put("items", JSONArray(visit.work.map { item -> JSONObject().put("dispatchItemId", item.dispatchItemId).put("subjectType", item.subjectType.code).put("equipmentReference", nullable(item.equipmentReference)).put("equipmentDescription", nullable(item.equipmentDescription)).put("taskName", item.taskName).put("servicePlanReference", nullable(item.servicePlanReference)).put("dueDateSnapshot", nullable(item.dueDateSnapshot)).put("inspectionSnapshotId", nullable(item.inspectionSnapshotId)).put("assignedTechnicians", JSONArray(item.assignedTechnicians.map(::tech))) })) }))
        root.put("inspectionSnapshots", JSONArray(value.inspectionSnapshots.map { snapshot -> JSONObject().put("snapshotId", snapshot.snapshotId).put("templateName", snapshot.templateName).put("sourceTemplateReference", nullable(snapshot.sourceTemplateReference)).put("sourceRevision", snapshot.sourceRevision ?: JSONObject.NULL).put("items", JSONArray(snapshot.items.map { item -> JSONObject().put("position", item.position).put("label", item.label).put("responseType", item.responseType).put("unit", nullable(item.unit)).put("required", item.required).put("privateGuidance", nullable(item.privateGuidance)) })) }))
        return root.toString(2).toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) { "Work package exceeds 1 MiB" } }
    }

    fun decode(bytes: ByteArray): DispatchPackage {
        require(bytes.size in 1..MAX_BYTES) { "Work package exceeds 1 MiB" }
        val root = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrElse { throw IllegalArgumentException("Malformed work package") }
        require(root.optString("format") == "ServiceLoopWorkPackage") { "Not a ServiceLoop work package" }; val version = root.optInt("formatVersion", -1); require(version == CURRENT_VERSION) { "Unsupported work package version" }
        fun JSONObject.text(name: String) = getString(name).also { require(it.isNotBlank() && it.length <= MAX_STRING) { "Invalid $name" } }
        fun JSONObject.nullable(name: String) = if (isNull(name)) null else getString(name).also { require(it.length <= MAX_STRING) { "$name is too long" } }
        fun JSONObject.requiredNullable(name: String) = get(name).let { if (it == JSONObject.NULL) null else getString(name).also { value -> require(value.length <= MAX_STRING) { "$name is too long" } } }
        fun tech(o: JSONObject) = DispatchTechnicianSnapshot(o.text("technicianId"), o.text("name"), o.nullable("designation"))
        fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
        fun <T> array(name: String, limit: Int, read: (JSONObject) -> T): List<T> { val a=root.getJSONArray(name); require(a.length()<=limit){"Too many $name"}; return (0 until a.length()).map{read(a.getJSONObject(it))} }
        val customers=array("customers",MAX_DIRECTORY){DispatchCustomer(it.text("reference"),it.text("name"),CustomerType.fromCode(it.text("customerType")))}; val sites=array("sites",MAX_DIRECTORY){DispatchSite(it.text("reference"),it.text("customerReference"),it.text("name"),it.nullable("address"))}; val equipment=array("equipment",MAX_DIRECTORY){DispatchEquipment(it.text("reference"),it.text("siteReference"),it.text("name"),it.nullable("identifier"),it.nullable("make"),it.nullable("model"),it.nullable("serial"))}
        val snapshots=array("inspectionSnapshots",MAX_DIRECTORY){s->val items=s.getJSONArray("items");require(items.length() in 1..MAX_WORK){"Invalid inspection snapshot item count"};DispatchInspectionSnapshot(s.text("snapshotId"),s.text("templateName"),s.nullable("sourceTemplateReference"),if(s.isNull("sourceRevision"))null else s.getInt("sourceRevision"),(0 until items.length()).map{items.getJSONObject(it).let{i->DispatchInspectionItem(i.getInt("position"),i.text("label"),i.text("responseType"),i.nullable("unit"),i.getBoolean("required"),i.nullable("privateGuidance"))}})}
        val visits=array("visits",MAX_VISITS){v-> val teams=v.getJSONArray("teams");val participants=v.getJSONArray("participants");val items=v.getJSONArray("items");require(items.length() in 1..MAX_WORK){"Invalid item count"};val lifecycle=v.text("transportLifecycle");val cancellation=v.nullable("cancellationReason");DispatchVisit(v.text("dispatchVisitId"),v.getInt("generation"),v.nullable("managerReference"),v.text("serviceDate"),v.nullable("appointmentLocalTime"),v.text("appointmentZoneId"),v.text("siteReference"),v.nullable("instructions"),(0 until teams.length()).map{teams.getJSONObject(it).let{t->DispatchTeamSnapshot(t.text("teamId"),t.text("name"),strings(t.getJSONArray("memberIds")),strings(t.getJSONArray("leaderIds")))}},(0 until participants.length()).map{tech(participants.getJSONObject(it))},strings(v.getJSONArray("leaderTechnicianIds")),(0 until items.length()).map{items.getJSONObject(it).let{item->val assigned=item.getJSONArray("assignedTechnicians");DispatchWork(item.text("dispatchItemId"),WorkSubjectType.fromCode(item.text("subjectType")),item.requiredNullable("equipmentReference"),item.requiredNullable("equipmentDescription"),item.text("taskName"),item.requiredNullable("servicePlanReference"),item.requiredNullable("dueDateSnapshot"),(0 until assigned.length()).map{a->tech(assigned.getJSONObject(a))},item.requiredNullable("inspectionSnapshotId"))}},lifecycle,cancellation)}
        return DispatchPackage(root.text("packageId"),root.text("createdAt"),root.text("senderLabel"),customers,sites,equipment,visits,snapshots).also(::validate)
    }
    fun materialHash(value: DispatchVisit, snapshots: List<DispatchInspectionSnapshot> = emptyList()): String {
        val canonical=value.copy(
            teams=value.teams.sortedBy{it.teamId}.map{it.copy(memberIds=it.memberIds.sorted(),leaderIds=it.leaderIds.sorted())},
            participants=value.participants.sortedBy{it.technicianId},leaderTechnicianIds=value.leaderTechnicianIds.sorted(),
            work=value.work.sortedBy{it.dispatchItemId}.map{it.copy(assignedTechnicians=it.assignedTechnicians.sortedBy{x->x.technicianId})},
        ).toString()
        val referenced = snapshots.filter { snapshot -> value.work.any { it.inspectionSnapshotId == snapshot.snapshotId } }.sortedBy { it.snapshotId }.joinToString("|") { it.contentCanonical() }
        return sha256("$canonical|$referenced")
    }

    fun contentAddressedSnapshotId(snapshot: DispatchInspectionSnapshot): String = "snapshot-${sha256(snapshot.contentCanonical()).take(24)}"

    private fun DispatchInspectionSnapshot.canonical(): String = buildString {
        append(snapshotId).append('|').append(contentCanonical())
    }

    private fun DispatchInspectionSnapshot.contentCanonical(): String = buildString {
        append(templateName).append('|')
        append(sourceTemplateReference.orEmpty()).append('|').append(sourceRevision ?: "")
        items.forEach { item ->
            append('|').append(item.position).append(':').append(item.label).append(':')
                .append(item.responseType).append(':').append(item.unit.orEmpty()).append(':')
                .append(item.required).append(':').append(item.privateGuidance.orEmpty())
        }
    }
    private fun validate(value: DispatchPackage) {
        require(value.packageId.isNotBlank()&&value.senderLabel.isNotBlank());Instant.parse(value.createdAt);require(value.visits.isNotEmpty()&&value.visits.size<=MAX_VISITS);require(value.visits.map{it.dispatchVisitId}.distinct().size==value.visits.size){"Duplicate dispatch visit ID"};require(value.customers.size<=MAX_DIRECTORY&&value.sites.size<=MAX_DIRECTORY&&value.equipment.size<=MAX_DIRECTORY);require(value.customers.map{it.reference}.distinct().size==value.customers.size);require(value.sites.map{it.reference}.distinct().size==value.sites.size);require(value.equipment.map{it.reference}.distinct().size==value.equipment.size)
        val customerRefs=value.customers.map{it.reference}.toSet();val siteRefs=value.sites.map{it.reference}.toSet();val equipmentRefs=value.equipment.map{it.reference}.toSet();require(value.sites.all{it.customerReference in customerRefs}&&value.equipment.all{it.siteReference in siteRefs}){"Broken directory reference"}
        require(value.inspectionSnapshots.map { it.snapshotId }.distinct().size == value.inspectionSnapshots.size) { "Duplicate inspection snapshot ID" }
        value.inspectionSnapshots.forEach { snapshot ->
            require(snapshot.snapshotId.isNotBlank() && snapshot.templateName.isNotBlank() && snapshot.templateName.length <= MAX_STRING && snapshot.items.isNotEmpty() && snapshot.items.size <= MAX_WORK)
            require(snapshot.sourceRevision == null || snapshot.sourceRevision > 0)
            require(snapshot.items.map { it.position } == (1..snapshot.items.size).toList()) { "Inspection snapshot positions must be contiguous" }
            snapshot.items.forEach { item -> require(item.label.isNotBlank() && item.responseType in setOf("STATUS", "TEXT", "NUMBER")); require(item.label.length <= MAX_STRING); require(item.unit.orEmpty().length <= MAX_STRING); require(item.privateGuidance.orEmpty().length <= MAX_STRING) }
        }
        require(value.visits.flatMap { it.work }.map { it.dispatchItemId }.distinct().size == value.visits.sumOf { it.work.size }) { "Duplicate dispatch item ID" }
        value.visits.forEach { v ->
            require(v.generation >= 1); LocalDate.parse(v.serviceDate); ZoneId.of(v.appointmentZoneId); v.appointmentLocalTime?.let(LocalTime::parse)
            require(v.transportLifecycle in setOf("ACTIVE", "CANCELED")) { "Unsupported Visit transport lifecycle" }
            if (v.transportLifecycle == "CANCELED") require(!v.cancellationReason.isNullOrBlank()) { "Canceled Visit requires a reason" } else require(v.cancellationReason == null) { "Active Visit cannot carry a cancellation reason" }
            require(v.siteReference in siteRefs && v.work.isNotEmpty() && v.work.size <= MAX_WORK)
            require(v.teams.map { it.teamId }.distinct().size == v.teams.size) { "Duplicate team ID" }
            v.teams.forEach { team ->
                require(team.memberIds.distinct().size == team.memberIds.size) { "Duplicate team member" }
                require(team.leaderIds.distinct().size == team.leaderIds.size) { "Duplicate team leader" }
                require(team.leaderIds.all { it in team.memberIds }) { "Team leader is not a member of that team" }
            }
            val participants = v.participants.map { it.technicianId }
            require(participants.distinct().size == participants.size && participants.isNotEmpty())
            require(v.leaderTechnicianIds.distinct().size == v.leaderTechnicianIds.size) { "Duplicate Visit leader" }
            require(v.leaderTechnicianIds.all { it in participants })
            require(v.teams.flatMap { it.memberIds }.toSet() == participants.toSet())
            require(v.teams.flatMap { it.leaderIds }.toSet() == v.leaderTechnicianIds.toSet())
            val names = v.participants.associate { it.technicianId to it.name }
            require(v.participants.all { it.designation.orEmpty().length <= 200 })
            val site = value.sites.single { it.reference == v.siteReference }
            val customer = value.customers.single { it.reference == site.customerReference }
            v.work.forEach { i ->
                DispatchPackageWorkValidator.validate(i, v.siteReference, customer.customerType, value.equipment)
                require(i.assignedTechnicians.map { it.technicianId }.distinct().size == i.assignedTechnicians.size)
                require(i.assignedTechnicians.all { it.technicianId in participants })
                require(i.assignedTechnicians.all { names[it.technicianId] == it.name }) { "Assignee name conflicts with participant snapshot" }
                require(i.inspectionSnapshotId == null || value.inspectionSnapshots.any { it.snapshotId == i.inspectionSnapshotId }) { "Broken inspection snapshot reference" }
            }
        }
    }
}

/** Transport-level validation for the three truthful Dispatch subject forms. */
object DispatchPackageWorkValidator {
    fun validate(
        work: DispatchWork,
        siteReference: String,
        customerType: CustomerType,
        equipment: List<DispatchEquipment>,
    ) {
        when (work.subjectType) {
            WorkSubjectType.SITE -> {
                require(work.equipmentReference == null) { "SITE work cannot reference Equipment" }
                require(work.equipmentDescription == null) { "SITE work cannot have an Equipment description" }
                require(work.servicePlanReference == null) { "SITE work cannot carry a service plan" }
                require(work.dueDateSnapshot == null) { "SITE work cannot carry a due date" }
            }
            WorkSubjectType.EQUIPMENT -> {
                val reference = work.equipmentReference
                if (reference == null) {
                    require(work.equipmentDescription == null || (work.equipmentDescription.trim().isNotBlank() && work.equipmentDescription.trim().length <= 500)) {
                        "Equipment description must be 500 characters or fewer"
                    }
                    require(work.servicePlanReference == null) { "Unidentified Equipment work cannot carry a service plan" }
                    require(work.dueDateSnapshot == null) { "Unidentified Equipment work cannot carry a due date" }
                } else {
                    require(reference.isNotBlank()) { "Known Equipment reference is required" }
                    val directory = equipment.singleOrNull { it.reference == reference }
                        ?: throw IllegalArgumentException("Equipment reference is missing from the work package")
                    require(directory.siteReference == siteReference) { "Equipment must belong to the Visit Site" }
                    require(work.equipmentDescription == null) { "Known Equipment work cannot have an Equipment description" }
                    work.servicePlanReference?.let {
                        require(customerType == CustomerType.STANDARD) { "Recurring service requires a Standard customer" }
                    }
                    work.dueDateSnapshot?.let {
                        require(work.servicePlanReference != null) { "Due date requires a service plan reference" }
                        LocalDate.parse(it)
                    }
                }
            }
        }
        require(work.dispatchItemId.isNotBlank()) { "Work item identity is missing" }
        require(work.taskName.trim().isNotBlank()) { "Every work item needs a task name" }
    }
}

enum class DispatchClassification { EXISTING_UNCHANGED, NEW, CONFLICT, POSSIBLE_DUPLICATE }
enum class DispatchVisitClassification { NEW_VISIT, UPDATE, CANCELED, ASSIGNMENT_REMOVED, ALREADY_CURRENT, OLDER_GENERATION, CONFLICT, LOCAL_CONFLICT, UPDATE_BLOCKED, NOT_ASSIGNED }
enum class DispatchDuplicateDecision { CREATE_SEPARATE, SKIP_BRANCH }
enum class DispatchOutboxStatus { DRAFT, DISPATCHED, CANCELED, CONCLUDED }
val DispatchOutboxVisitEntity.outboxStatus: DispatchOutboxStatus get() = when {
    concludedAtEpochMillis != null -> DispatchOutboxStatus.CONCLUDED
    canceledAtEpochMillis != null -> DispatchOutboxStatus.CANCELED
    lastExportedGeneration != null -> DispatchOutboxStatus.DISPATCHED
    else -> DispatchOutboxStatus.DRAFT
}

/** True only while a cancellation for an already-dispatched Visit still needs an artifact. */
val DispatchOutboxVisitEntity.cancellationExportPending: Boolean
    get() = outboxStatus == DispatchOutboxStatus.CANCELED &&
        lastExportedGeneration != null &&
        canceledAtEpochMillis != null &&
        (lastExportedCancellationAtEpochMillis == null || lastExportedCancellationAtEpochMillis < canceledAtEpochMillis)
data class DispatchExportPreparation(
    val packageValue: DispatchPackage,
    val bytes: ByteArray,
    internal val expected: Map<String, Pair<Long, String>>,
    internal val sourceHash: String,
)
data class DispatchExportArtifact(val packageValue: DispatchPackage, val file: File)
data class DispatchPreviewLine(val reference:String,val classification:DispatchClassification,val detail:String,val duplicateDecision:DispatchDuplicateDecision?=null)
data class DispatchFieldChange(val label:String,val before:String,val after:String)
data class DispatchItemPreview(val dispatchItemId:String,val taskName:String,val localRole:String,val change:String)
data class DispatchVisitPreview(val dispatchVisitId:String,val generation:Int,val classification:DispatchVisitClassification,val reasons:List<String>,val localVisitId:String?=null,val items:List<DispatchItemPreview> = emptyList(),val changes:List<DispatchFieldChange> = emptyList(),val directoryReferences:Set<String> = emptySet())
data class DispatchPreview(val value:DispatchPackage,val identity:TechnicianIdentity,val directory:List<DispatchPreviewLine>,val visits:List<DispatchVisitPreview>){
    private val actionableClasses=setOf(DispatchVisitClassification.NEW_VISIT,DispatchVisitClassification.UPDATE,DispatchVisitClassification.CANCELED,DispatchVisitClassification.ASSIGNMENT_REMOVED)
    fun directoryBlockers(visit:DispatchVisitPreview)=directory.filter{it.reference in visit.directoryReferences&&(it.classification==DispatchClassification.CONFLICT||(it.classification==DispatchClassification.POSSIBLE_DUPLICATE&&it.duplicateDecision==null))}
    fun skipped(visit:DispatchVisitPreview)=directory.any{it.reference in visit.directoryReferences&&it.duplicateDecision==DispatchDuplicateDecision.SKIP_BRANCH}
    val safeActionableVisits get()=visits.filter{it.classification in actionableClasses&&directoryBlockers(it).isEmpty()&&!skipped(it)}
    internal val idempotentNoOp get()=visits.isNotEmpty()&&visits.all{it.classification in setOf(DispatchVisitClassification.ALREADY_CURRENT,DispatchVisitClassification.NOT_ASSIGNED)}&&visits.any{it.classification==DispatchVisitClassification.ALREADY_CURRENT}&&visits.filter{it.classification==DispatchVisitClassification.ALREADY_CURRENT}.all{directoryBlockers(it).isEmpty()&&!skipped(it)}
    val canImport get()=safeActionableVisits.isNotEmpty()
}
data class DispatchImportResult(val createdVisitIds:List<String>,val updatedVisitIds:List<String>,val unchangedVisitIds:List<String>,val withdrawnVisitIds:List<String> = emptyList(),val canceledVisitIds:List<String> = emptyList())
data class DispatchTeamDetail(val team:DispatchTeamEntity,val members:List<Pair<DispatchTechnicianEntity,Boolean>>)
data class TechnicianRenameReview(val incoming:TechnicianIdentity,val existingName:String,val existingDesignation:String?=null)
data class HandoffReview(val candidates:List<DispatchTechnicianSnapshot>,val hasSubstantiveDraft:Boolean)
data class DispatchOutboxItemDraft(
    val dispatchItemId:String,
    val subjectType:WorkSubjectType,
    val equipmentId:String? = null,
    val equipmentDescription:String = "",
    val taskName:String,
    val servicePlanReference:String?=null,
    val dueDateSnapshot:String?=null,
    val reusableTemplateId:String?=null,
    val assignedTechnicianIds:List<String> = emptyList(),
) {
    /** Source compatibility for existing known-equipment callers. */
    constructor(
        dispatchItemId:String,
        equipmentId:String,
        taskName:String,
        servicePlanReference:String?=null,
        dueDateSnapshot:String?=null,
        assignedTechnicianIds:List<String> = emptyList(),
    ) : this(dispatchItemId,WorkSubjectType.EQUIPMENT,equipmentId,"",taskName,servicePlanReference,dueDateSnapshot,null,assignedTechnicianIds)
}
data class DispatchOutboxEditorDraft(
    val dispatchVisitId:String?=null,
    val expectedModifiedAtEpochMillis:Long?=null,
    val managerReference:String?=null,
    val siteId:String,
    val serviceDate:String,
    val appointmentLocalTime:String?=null,
    val appointmentZoneId:String,
    val instructions:String?=null,
    val teamIds:List<String>,
    val items:List<DispatchOutboxItemDraft>,
    val newCustomerSite:CustomerWithFirstSiteInput?=null,
)
