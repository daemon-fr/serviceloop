package com.v16studio.serviceloop.data

import androidx.room.withTransaction
import com.v16studio.serviceloop.domain.VisitCancellationOrigin
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

data class TechnicianIdentity(val technicianId: String, val name: String)
data class DispatchTechnicianSnapshot(val technicianId: String, val name: String)
data class DispatchTeamSnapshot(val teamId: String, val name: String, val memberIds: List<String>, val leaderIds: List<String>)
data class DispatchCustomer(val reference: String, val name: String)
data class DispatchSite(val reference: String, val customerReference: String, val name: String, val address: String?)
data class DispatchEquipment(val reference: String, val siteReference: String, val name: String, val identifier: String?, val make: String?, val model: String?, val serial: String?)
data class DispatchWork(val dispatchItemId: String, val equipmentReference: String, val taskName: String, val servicePlanReference: String? = null, val dueDateSnapshot: String? = null, val assignedTechnicians: List<DispatchTechnicianSnapshot> = emptyList())
data class DispatchVisit(val dispatchVisitId: String, val generation: Int, val managerReference: String?, val serviceDate: String, val appointmentLocalTime: String?, val appointmentZoneId: String, val siteReference: String, val instructions: String?, val teams: List<DispatchTeamSnapshot>, val participants: List<DispatchTechnicianSnapshot>, val leaderTechnicianIds: List<String>, val work: List<DispatchWork>, val transportLifecycle: String = "ACTIVE", val cancellationReason: String? = null)
data class DispatchPackage(val packageId: String, val createdAt: String, val senderLabel: String, val customers: List<DispatchCustomer>, val sites: List<DispatchSite>, val equipment: List<DispatchEquipment>, val visits: List<DispatchVisit>)

object TechnicianIdentityCodec {
    const val MAX_BYTES = 16_384
    fun encode(value: TechnicianIdentity): ByteArray = JSONObject().put("format", "ServiceLoopTechnician").put("formatVersion", 1).put("technicianId", value.technicianId).put("name", value.name).toString(2).toByteArray()
    fun decode(bytes: ByteArray): TechnicianIdentity {
        require(bytes.size in 1..MAX_BYTES) { "Invalid technician identity file size" }
        val root = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrElse { throw IllegalArgumentException("Malformed technician identity file") }
        require(root.optString("format") == "ServiceLoopTechnician" && root.optInt("formatVersion") == 1) { "Unsupported technician identity file" }
        val id = root.getString("technicianId").trim(); val name = root.getString("name").trim()
        require(id.length in 16..200 && name.length in 1..200) { "Invalid technician identity" }
        return TechnicianIdentity(id, name)
    }
}

object DispatchPackageCodec {
    const val MAX_BYTES = 1_048_576
    private const val MAX_DIRECTORY = 500
    const val MAX_VISITS = 100
    const val CURRENT_VERSION = 3
    private const val MAX_WORK = 500
    private const val MAX_STRING = 4_000

    fun encode(value: DispatchPackage): ByteArray {
        validate(value)
        fun nullable(v: Any?) = v ?: JSONObject.NULL
        fun tech(v: DispatchTechnicianSnapshot) = JSONObject().put("technicianId", v.technicianId).put("name", v.name)
        val root = JSONObject().put("format", "ServiceLoopWorkPackage").put("formatVersion", CURRENT_VERSION).put("packageId", value.packageId).put("createdAt", value.createdAt).put("senderLabel", value.senderLabel)
        root.put("customers", JSONArray(value.customers.map { JSONObject().put("reference", it.reference).put("name", it.name) }))
        root.put("sites", JSONArray(value.sites.map { JSONObject().put("reference", it.reference).put("customerReference", it.customerReference).put("name", it.name).put("address", nullable(it.address)) }))
        root.put("equipment", JSONArray(value.equipment.map { JSONObject().put("reference", it.reference).put("siteReference", it.siteReference).put("name", it.name).put("identifier", nullable(it.identifier)).put("make", nullable(it.make)).put("model", nullable(it.model)).put("serial", nullable(it.serial)) }))
        root.put("visits", JSONArray(value.visits.map { visit -> JSONObject().put("dispatchVisitId", visit.dispatchVisitId).put("generation", visit.generation).put("managerReference", nullable(visit.managerReference)).put("serviceDate", visit.serviceDate).put("appointmentLocalTime", nullable(visit.appointmentLocalTime)).put("appointmentZoneId", visit.appointmentZoneId).put("siteReference", visit.siteReference).put("instructions", nullable(visit.instructions)).put("transportLifecycle", visit.transportLifecycle).put("cancellationReason", nullable(visit.cancellationReason)).put("teams", JSONArray(visit.teams.map { JSONObject().put("teamId", it.teamId).put("name", it.name).put("memberIds", JSONArray(it.memberIds)).put("leaderIds", JSONArray(it.leaderIds)) })).put("participants", JSONArray(visit.participants.map(::tech))).put("leaderTechnicianIds", JSONArray(visit.leaderTechnicianIds)).put("items", JSONArray(visit.work.map { item -> JSONObject().put("dispatchItemId", item.dispatchItemId).put("equipmentReference", item.equipmentReference).put("taskName", item.taskName).put("servicePlanReference", nullable(item.servicePlanReference)).put("dueDateSnapshot", nullable(item.dueDateSnapshot)).put("assignedTechnicians", JSONArray(item.assignedTechnicians.map(::tech))) })) }))
        return root.toString(2).toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) { "Work package exceeds 1 MiB" } }
    }

    fun decode(bytes: ByteArray): DispatchPackage {
        require(bytes.size in 1..MAX_BYTES) { "Work package exceeds 1 MiB" }
        val root = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrElse { throw IllegalArgumentException("Malformed work package") }
        require(root.optString("format") == "ServiceLoopWorkPackage") { "Not a ServiceLoop work package" }; val version = root.optInt("formatVersion", -1); require(version == 2 || version == CURRENT_VERSION) { "Unsupported work package version" }
        fun JSONObject.text(name: String) = getString(name).also { require(it.isNotBlank() && it.length <= MAX_STRING) { "Invalid $name" } }
        fun JSONObject.nullable(name: String) = if (isNull(name)) null else getString(name).also { require(it.length <= MAX_STRING) { "$name is too long" } }
        fun tech(o: JSONObject) = DispatchTechnicianSnapshot(o.text("technicianId"), o.text("name"))
        fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
        fun <T> array(name: String, limit: Int, read: (JSONObject) -> T): List<T> { val a=root.getJSONArray(name); require(a.length()<=limit){"Too many $name"}; return (0 until a.length()).map{read(a.getJSONObject(it))} }
        val customers=array("customers",MAX_DIRECTORY){DispatchCustomer(it.text("reference"),it.text("name"))}; val sites=array("sites",MAX_DIRECTORY){DispatchSite(it.text("reference"),it.text("customerReference"),it.text("name"),it.nullable("address"))}; val equipment=array("equipment",MAX_DIRECTORY){DispatchEquipment(it.text("reference"),it.text("siteReference"),it.text("name"),it.nullable("identifier"),it.nullable("make"),it.nullable("model"),it.nullable("serial"))}
        val visits=array("visits",MAX_VISITS){v-> val teams=v.getJSONArray("teams");val participants=v.getJSONArray("participants");val items=v.getJSONArray("items");require(items.length() in 1..MAX_WORK){"Invalid item count"};val lifecycle=if(version==2)"ACTIVE" else v.text("transportLifecycle");val cancellation=if(version==2)null else v.nullable("cancellationReason");DispatchVisit(v.text("dispatchVisitId"),v.getInt("generation"),v.nullable("managerReference"),v.text("serviceDate"),v.nullable("appointmentLocalTime"),v.text("appointmentZoneId"),v.text("siteReference"),v.nullable("instructions"),(0 until teams.length()).map{teams.getJSONObject(it).let{t->DispatchTeamSnapshot(t.text("teamId"),t.text("name"),strings(t.getJSONArray("memberIds")),strings(t.getJSONArray("leaderIds")))}},(0 until participants.length()).map{tech(participants.getJSONObject(it))},strings(v.getJSONArray("leaderTechnicianIds")),(0 until items.length()).map{items.getJSONObject(it).let{item->val assigned=item.getJSONArray("assignedTechnicians");DispatchWork(item.text("dispatchItemId"),item.text("equipmentReference"),item.text("taskName"),item.nullable("servicePlanReference"),item.nullable("dueDateSnapshot"),(0 until assigned.length()).map{a->tech(assigned.getJSONObject(a))})}},lifecycle,cancellation)}
        return DispatchPackage(root.text("packageId"),root.text("createdAt"),root.text("senderLabel"),customers,sites,equipment,visits).also(::validate)
    }
    fun materialHash(value: DispatchVisit): String {
        val canonical=value.copy(
            teams=value.teams.sortedBy{it.teamId}.map{it.copy(memberIds=it.memberIds.sorted(),leaderIds=it.leaderIds.sorted())},
            participants=value.participants.sortedBy{it.technicianId},leaderTechnicianIds=value.leaderTechnicianIds.sorted(),
            work=value.work.sortedBy{it.dispatchItemId}.map{it.copy(assignedTechnicians=it.assignedTechnicians.sortedBy{x->x.technicianId})},
        ).toString()
        return sha256(canonical)
    }
    private fun validate(value: DispatchPackage) {
        require(value.packageId.isNotBlank()&&value.senderLabel.isNotBlank());Instant.parse(value.createdAt);require(value.visits.isNotEmpty()&&value.visits.size<=MAX_VISITS);require(value.visits.map{it.dispatchVisitId}.distinct().size==value.visits.size){"Duplicate dispatch visit ID"};require(value.customers.size<=MAX_DIRECTORY&&value.sites.size<=MAX_DIRECTORY&&value.equipment.size<=MAX_DIRECTORY);require(value.customers.map{it.reference}.distinct().size==value.customers.size);require(value.sites.map{it.reference}.distinct().size==value.sites.size);require(value.equipment.map{it.reference}.distinct().size==value.equipment.size)
        val customerRefs=value.customers.map{it.reference}.toSet();val siteRefs=value.sites.map{it.reference}.toSet();val equipmentRefs=value.equipment.map{it.reference}.toSet();require(value.sites.all{it.customerReference in customerRefs}&&value.equipment.all{it.siteReference in siteRefs}){"Broken directory reference"}
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
            v.work.forEach { i ->
                require(i.equipmentReference in equipmentRefs); i.dueDateSnapshot?.let(LocalDate::parse)
                require(i.assignedTechnicians.map { it.technicianId }.distinct().size == i.assignedTechnicians.size)
                require(i.assignedTechnicians.all { it.technicianId in participants })
                require(i.assignedTechnicians.all { names[it.technicianId] == it.name }) { "Assignee name conflicts with participant snapshot" }
            }
        }
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
data class TechnicianRenameReview(val incoming:TechnicianIdentity,val existingName:String)
data class HandoffReview(val candidates:List<DispatchTechnicianSnapshot>,val hasSubstantiveDraft:Boolean)
data class DispatchOutboxItemDraft(
    val dispatchItemId:String,
    val equipmentId:String,
    val taskName:String,
    val servicePlanReference:String?=null,
    val dueDateSnapshot:String?=null,
    val assignedTechnicianIds:List<String> = emptyList(),
)
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
)

fun interface DispatchMutationFault { fun checkpoint(name: String) }

class DispatchPackageService(
    private val database: ServiceLoopDatabase,
    private val fileRoot: File? = null,
    private val mutationFault: DispatchMutationFault = DispatchMutationFault { },
) {
    private val dao=database.serviceLoopDao();private val dispatch=database.dispatchDao()
    private fun normal(v:String?)=v.orEmpty().trim().lowercase().replace(Regex("\\s+")," ");private fun stable(vararg p:String)=UUID.nameUUIDFromBytes(p.joinToString("|").toByteArray()).toString()
    private fun techJson(v:List<DispatchTechnicianSnapshot>)=JSONArray(v.map{JSONObject().put("technicianId",it.technicianId).put("name",it.name)}).toString();private fun teamsJson(v:List<DispatchTeamSnapshot>)=JSONArray(v.map{JSONObject().put("teamId",it.teamId).put("name",it.name).put("memberIds",JSONArray(it.memberIds)).put("leaderIds",JSONArray(it.leaderIds))}).toString();private fun idsJson(v:List<String>)=JSONArray(v).toString()
    suspend fun identity():TechnicianIdentity{dispatch.technicianIdentity()?.let{return TechnicianIdentity(it.technicianId,it.displayName)};val now=System.currentTimeMillis();val name=dao.businessProfile()?.technicianName?.trim().takeUnless{it.isNullOrEmpty()}?:"Technician";runCatching{dispatch.insertTechnicianIdentity(TechnicianIdentityEntity(technicianId=TechnicianIdCodec.generate(),displayName=name,createdAtEpochMillis=now,modifiedAtEpochMillis=now))};return dispatch.technicianIdentity()!!.let{TechnicianIdentity(it.technicianId,it.displayName)}}
    suspend fun renameIdentity(name:String):TechnicianIdentity{require(name.trim().isNotEmpty());val c=dispatch.technicianIdentity()?:run{identity();dispatch.technicianIdentity()!!};dispatch.updateTechnicianIdentity(c.copy(displayName=name.trim(),modifiedAtEpochMillis=System.currentTimeMillis()));return identity()}
    suspend fun importTechnician(v:TechnicianIdentity,confirmRename:Boolean=false){val now=System.currentTimeMillis();val p=dispatch.technician(v.technicianId);when{p==null->dispatch.insertTechnician(DispatchTechnicianEntity(v.technicianId,v.name,now,now));p.displayName==v.name->Unit;confirmRename->dispatch.updateTechnician(p.copy(displayName=v.name,modifiedAtEpochMillis=now));else->error("Technician ID already exists with a different name; confirm the name update")}}
    suspend fun importTechnicianManually(v:TechnicianIdentity,confirmRename:Boolean=false){val normalized=TechnicianIdCodec.normalize(v.technicianId)?:throw IllegalArgumentException("Technician ID is not a valid ServiceLoop Technician ID.");importTechnician(v.copy(technicianId=normalized),confirmRename)}
    suspend fun technicianRenameReview(v:TechnicianIdentity)=dispatch.technician(v.technicianId)?.takeIf{it.displayName!=v.name}?.let{TechnicianRenameReview(v,it.displayName)}
    suspend fun technicians()=dispatch.technicians();suspend fun createTeam(name:String):String{require(name.trim().isNotEmpty());val id=UUID.randomUUID().toString();val n=System.currentTimeMillis();dispatch.insertTeam(DispatchTeamEntity(id,name.trim(),n,n));return id}
    suspend fun renameTeam(id:String,name:String){val team=dispatch.team(id)?:error("Team missing");require(name.trim().isNotEmpty());dispatch.updateTeam(team.copy(name=name.trim(),modifiedAtEpochMillis=System.currentTimeMillis()))}
    suspend fun setTeamMember(teamId:String,techId:String,present:Boolean,isLeader:Boolean=false){dispatch.team(teamId)?:error("Team missing");dispatch.technician(techId)?:error("Technician missing");val c=dispatch.teamMembers(teamId).find{it.technicianId==techId};if(!present&&c!=null)dispatch.removeTeamMember(teamId,techId)else if(present&&c==null)dispatch.insertTeamMember(DispatchTeamMemberEntity(teamId,techId,isLeader))else if(present&&c!!.isLeader!=isLeader)dispatch.updateTeamMember(c.copy(isLeader=isLeader))}
    suspend fun teams():List<DispatchTeamDetail>{val t=dispatch.technicians().associateBy{it.technicianId};return dispatch.teams().map{x->DispatchTeamDetail(x,dispatch.teamMembers(x.id).mapNotNull{m->t[m.technicianId]?.let{it to m.isLeader}})}}
    suspend fun createOutboxVisit(manager:String?,siteId:String,date:String,time:String?,zone:String,instructions:String?,teamIds:List<String>):String=database.withTransaction{LocalDate.parse(date);time?.let(LocalTime::parse);ZoneId.of(zone);require(teamIds.isNotEmpty());dao.site(siteId)?:error("Site missing");val id=UUID.randomUUID().toString();val n=System.currentTimeMillis();dispatch.insertOutboxVisit(DispatchOutboxVisitEntity(id,manager?.trim()?.takeIf{it.isNotEmpty()},siteId,date,time,zone,instructions?.trim()?.takeIf{it.isNotEmpty()},null,null,null,n,n));dispatch.insertOutboxVisitTeams(teamIds.distinct().map{DispatchOutboxVisitTeamEntity(id,it)});id}
    suspend fun saveOutboxVisit(draft:DispatchOutboxEditorDraft):String=database.withTransaction{
        val date=LocalDate.parse(draft.serviceDate).toString()
        val time=draft.appointmentLocalTime?.trim()?.takeIf{it.isNotEmpty()}?.let{LocalTime.parse(it).toString()}
        val zone=ZoneId.of(draft.appointmentZoneId.trim()).id
        val site=dao.site(draft.siteId)?:error("Choose a valid Site")
        val teamIds=draft.teamIds.distinct()
        require(teamIds.isNotEmpty()){"Choose at least one Team"}
        require(teamIds.all{dispatch.team(it)!=null}){"A selected Team no longer exists"}
        require(draft.items.map{it.dispatchItemId}.distinct().size==draft.items.size){"Duplicate work item identity"}
        val participants=dispatch.allTeamMembers().filter{it.teamId in teamIds}.map{it.technicianId}.toSet()
        require(participants.isNotEmpty()){ "Selected Teams need at least one Technician" }
        draft.items.forEach{item->
            require(item.dispatchItemId.isNotBlank()){"Work item identity is missing"}
            require(item.taskName.trim().isNotEmpty()){"Every work item needs a task name"}
            val equipment=dao.equipment(item.equipmentId)?:error("A selected Equipment item no longer exists")
            require(equipment.siteId==site.id){"Work items must use Equipment from the selected Site"}
            item.dueDateSnapshot?.trim()?.takeIf{it.isNotEmpty()}?.let(LocalDate::parse)
            require(item.assignedTechnicianIds.distinct().all{it in participants}){"An item assignee is outside the selected Teams"}
        }
        val existing=draft.dispatchVisitId?.let{dispatch.outboxVisit(it)}
        if(draft.dispatchVisitId!=null){
            require(existing!=null){"Outbox Visit no longer exists"}
            require(existing.outboxStatus!=DispatchOutboxStatus.CONCLUDED){"Reopen this concluded Visit before editing"}
            require(draft.expectedModifiedAtEpochMillis==existing.modifiedAtEpochMillis){"This Dispatch Visit changed after the editor was opened. Reopen it and review the latest version."}
        }
        val id=existing?.dispatchVisitId?:UUID.randomUUID().toString()
        val existingItems=if(existing==null) emptyList() else dispatch.outboxItems(id)
        val allExistingItems=dispatch.outboxItems().associateBy{it.dispatchItemId}
        draft.items.forEach{item->require(allExistingItems[item.dispatchItemId]?.dispatchVisitId in setOf(null,id)){"Work item belongs to another Visit"}}
        val now=System.currentTimeMillis()
        val modified=existing?.modifiedAtEpochMillis?.let{maxOf(now,it+1)}?:now
        val value=(existing?:DispatchOutboxVisitEntity(id,null,site.id,date,time,zone,null,null,null,null,now,now)).copy(
            managerReference=draft.managerReference?.trim()?.takeIf{it.isNotEmpty()},siteId=site.id,serviceDate=date,
            appointmentLocalTime=time,appointmentZoneId=zone,instructions=draft.instructions?.trim()?.takeIf{it.isNotEmpty()},modifiedAtEpochMillis=modified,
        )
        if(existing==null)dispatch.insertOutboxVisit(value) else dispatch.updateOutboxVisit(value)
        dispatch.clearOutboxVisitTeams(id)
        dispatch.insertOutboxVisitTeams(teamIds.map{DispatchOutboxVisitTeamEntity(id,it)})
        val keptIds=draft.items.map{it.dispatchItemId}.toSet()
        existingItems.filter{it.dispatchItemId !in keptIds}.forEach{dispatch.deleteOutboxItem(it.dispatchItemId)}
        val remaining=existingItems.filter{it.dispatchItemId in keptIds}.associateBy{it.dispatchItemId}
        remaining.values.forEachIndexed{index,item->dispatch.updateOutboxItem(item.copy(position=-index-1))}
        draft.items.forEachIndexed{index,item->
            val entity=DispatchOutboxItemEntity(item.dispatchItemId,id,index,item.equipmentId,item.taskName.trim(),item.servicePlanReference?.trim()?.takeIf{it.isNotEmpty()},item.dueDateSnapshot?.trim()?.takeIf{it.isNotEmpty()})
            if(item.dispatchItemId in remaining)dispatch.updateOutboxItem(entity) else dispatch.insertOutboxItem(entity)
            dispatch.clearOutboxItemAssignees(item.dispatchItemId)
            dispatch.insertOutboxItemAssignees(item.assignedTechnicianIds.distinct().map{DispatchOutboxItemAssigneeEntity(item.dispatchItemId,it)})
        }
        id
    }
    suspend fun updateOutboxVisit(id:String,manager:String?,siteId:String,date:String,time:String?,zone:String,instructions:String?,teamIds:List<String>)=database.withTransaction{LocalDate.parse(date);time?.takeIf{it.isNotBlank()}?.let(LocalTime::parse);ZoneId.of(zone);require(teamIds.isNotEmpty());val old=dispatch.outboxVisit(id)?:error("Outbox visit missing");require(old.outboxStatus!=DispatchOutboxStatus.CONCLUDED&&old.outboxStatus!=DispatchOutboxStatus.CANCELED){"Canceled and concluded Visits are read-only"};require(dispatch.outboxItems(id).all{dao.equipment(it.equipmentId)?.siteId==siteId}){"Remove items from the old site before changing Site"};dispatch.clearOutboxVisitTeams(id);dispatch.insertOutboxVisitTeams(teamIds.distinct().map{DispatchOutboxVisitTeamEntity(id,it)});val participants=expand(id).first.map{it.technicianId}.toSet();dispatch.outboxItems(id).forEach{item->require(dispatch.outboxItemAssignees(item.dispatchItemId).all{it.technicianId in participants}){"An item assignee is outside the selected Teams"}};dispatch.updateOutboxVisit(old.copy(managerReference=manager?.trim()?.takeIf{it.isNotEmpty()},siteId=siteId,serviceDate=date,appointmentLocalTime=time?.trim()?.takeIf{it.isNotEmpty()},appointmentZoneId=zone,instructions=instructions?.trim()?.takeIf{it.isNotEmpty()},modifiedAtEpochMillis=modifiedAfter(old)))}
    suspend fun addOutboxItem(visitId:String,equipmentId:String,task:String,plan:String?,due:String?,assigned:List<String>):String=database.withTransaction{require(task.trim().isNotEmpty());val v=dispatch.outboxVisit(visitId)?:error("Outbox visit missing");require(v.outboxStatus!=DispatchOutboxStatus.CONCLUDED){"Reopen this concluded Visit before editing"};val e=dao.equipment(equipmentId)?:error("Equipment missing");require(e.siteId==v.siteId);val participants=expand(visitId).first.map{it.technicianId}.toSet();require(assigned.all{it in participants}){"Assignee is outside selected teams"};val id=UUID.randomUUID().toString();dispatch.insertOutboxItem(DispatchOutboxItemEntity(id,visitId,dispatch.outboxItems(visitId).size,equipmentId,task.trim(),plan,due));dispatch.insertOutboxItemAssignees(assigned.distinct().map{DispatchOutboxItemAssigneeEntity(id,it)});dispatch.updateOutboxVisit(v.copy(modifiedAtEpochMillis=modifiedAfter(v)));id}
    suspend fun updateOutboxItem(itemId:String,equipmentId:String,task:String,plan:String?,due:String?,assigned:List<String>)=database.withTransaction{require(task.trim().isNotEmpty());val old=dispatch.outboxItems().find{it.dispatchItemId==itemId}?:error("Outbox item missing");val visit=dispatch.outboxVisit(old.dispatchVisitId)?:error("Outbox Visit missing");require(visit.outboxStatus!=DispatchOutboxStatus.CONCLUDED){"Reopen this concluded Visit before editing"};val equipment=dao.equipment(equipmentId)?:error("Equipment missing");require(equipment.siteId==visit.siteId);val participants=expand(visit.dispatchVisitId).first.map{it.technicianId}.toSet();require(assigned.all{it in participants}){"Assignee is outside selected Teams"};dispatch.clearOutboxItemAssignees(itemId);dispatch.insertOutboxItemAssignees(assigned.distinct().map{DispatchOutboxItemAssigneeEntity(itemId,it)});dispatch.updateOutboxItem(old.copy(equipmentId=equipmentId,taskName=task.trim(),servicePlanReference=plan?.trim()?.takeIf{it.isNotEmpty()},dueDateSnapshot=due?.trim()?.takeIf{it.isNotEmpty()}));dispatch.updateOutboxVisit(visit.copy(modifiedAtEpochMillis=modifiedAfter(visit)))}
    suspend fun outboxItems(visitId:String)=dispatch.outboxItems(visitId).map{it to dispatch.outboxItemAssignees(it.dispatchItemId).map{x->x.technicianId}}
    suspend fun outboxParticipants(visitId:String)=expand(visitId)
    suspend fun outboxTeamIds(visitId:String)=dispatch.outboxVisitTeams(visitId).map{it.teamId}
    suspend fun outboxVisits()=dispatch.outboxVisits();private suspend fun expand(id:String):Pair<List<DispatchTechnicianEntity>,Set<String>>{val teams=dispatch.outboxVisitTeams(id).map{it.teamId}.toSet();val members=dispatch.allTeamMembers().filter{it.teamId in teams};val tech=dispatch.technicians().associateBy{it.technicianId};return members.mapNotNull{tech[it.technicianId]}.distinctBy{it.technicianId} to members.filter{it.isLeader}.map{it.technicianId}.toSet()}
    suspend fun rescheduleOutboxVisit(id:String,date:String,time:String?,zone:String){LocalDate.parse(date);time?.let(LocalTime::parse);ZoneId.of(zone);val value=dispatch.outboxVisit(id)?:error("Outbox visit missing");require(value.outboxStatus!=DispatchOutboxStatus.CONCLUDED){"Reopen this concluded Visit before editing"};dispatch.updateOutboxVisit(value.copy(serviceDate=date,appointmentLocalTime=time,appointmentZoneId=zone,modifiedAtEpochMillis=modifiedAfter(value)))}
    private suspend fun compose(o:DispatchOutboxVisitEntity,g:Int):DispatchVisit{val s=dao.site(o.siteId)?:error("Site missing");val teamIds=dispatch.outboxVisitTeams(o.dispatchVisitId).map{it.teamId};val allTeams=dispatch.teams().associateBy{it.id};val members=dispatch.allTeamMembers();val tech=dispatch.technicians().associateBy{it.technicianId};val teams=teamIds.map{id->val t=allTeams[id]?:error("Team missing");val m=members.filter{it.teamId==id};DispatchTeamSnapshot(id,t.name,m.map{it.technicianId}.sorted(),m.filter{it.isLeader}.map{it.technicianId}.sorted())};val participants=teams.flatMap{it.memberIds}.distinct().sorted().map{tech[it]?.let{x->DispatchTechnicianSnapshot(x.technicianId,x.displayName)}?:error("Technician missing")};val items=dispatch.outboxItems(o.dispatchVisitId).map{i->val e=dao.equipment(i.equipmentId)?:error("Equipment missing");val assigned=dispatch.outboxItemAssignees(i.dispatchItemId).map{it.technicianId}.sorted().map{tech[it]?.let{x->DispatchTechnicianSnapshot(x.technicianId,x.displayName)}?:error("Technician missing")};DispatchWork(i.dispatchItemId,e.reference,i.taskName,i.servicePlanReference,i.dueDateSnapshot,assigned)};require(items.isNotEmpty());return DispatchVisit(o.dispatchVisitId,g,o.managerReference,o.serviceDate,o.appointmentLocalTime,o.appointmentZoneId,s.reference,o.instructions,teams,participants,teams.flatMap{it.leaderIds}.distinct().sorted(),items,if(o.canceledAtEpochMillis!=null)"CANCELED" else "ACTIVE",o.cancellationReason)}
    suspend fun prepareExport(ids:List<String>,sender:String):DispatchExportPreparation=database.withTransaction{
        require(sender.trim().isNotEmpty());val selected=ids.distinct();require(selected.isNotEmpty()){ "Select at least one Visit" };require(selected.size<=DispatchPackageCodec.MAX_VISITS){"A work package can contain at most ${DispatchPackageCodec.MAX_VISITS} Visits. Reduce the selection."};val now=System.currentTimeMillis();val expected=linkedMapOf<String,Pair<Long,String>>()
        val visits=selected.map{id->val o=dispatch.outboxVisit(id)?:error("Outbox visit missing");require(o.outboxStatus!=DispatchOutboxStatus.CONCLUDED){"Concluded Visits must be reopened before export"};require(!(o.outboxStatus==DispatchOutboxStatus.CANCELED&&o.lastExportedGeneration==null)){"A canceled Draft has no technician export to send"};val candidate=compose(o,o.lastExportedGeneration?:1);val candidateHash=DispatchPackageCodec.materialHash(candidate);val generation=when{o.lastExportedGeneration==null->1;candidateHash==o.lastExportedMaterialHash->o.lastExportedGeneration;else->o.lastExportedGeneration+1};val visit=if(generation==candidate.generation)candidate else compose(o,generation);expected[id]=o.modifiedAtEpochMillis to DispatchPackageCodec.materialHash(visit);visit}
        val value=packageSnapshot(UUID.randomUUID().toString(),Instant.ofEpochMilli(now).toString(),sender.trim(),visits);DispatchExportPreparation(value,DispatchPackageCodec.encode(value),expected,packageSourceHash(visits))
    }
    suspend fun exportPackage(ids:List<String>,sender:String):DispatchPackage=prepareExport(ids,sender).packageValue
    suspend fun commitPreparedExport(prepared:DispatchExportPreparation)=database.withTransaction{val now=System.currentTimeMillis();prepared.packageValue.visits.forEach{visit->val current=dispatch.outboxVisit(visit.dispatchVisitId)?:error("Outbox visit missing");val expected=prepared.expected[visit.dispatchVisitId]?:error("Prepared Visit missing");require(current.outboxStatus!=DispatchOutboxStatus.CONCLUDED&&current.modifiedAtEpochMillis==expected.first&&DispatchPackageCodec.materialHash(compose(current,visit.generation))==expected.second){"Dispatch Visit changed while the work package was being prepared"}};require(packageSourceHash(prepared.packageValue.visits)==prepared.sourceHash){"Dispatch directory data changed while the work package was being reviewed"};prepared.packageValue.visits.forEach{visit->val current=dispatch.outboxVisit(visit.dispatchVisitId)?:error("Outbox visit missing");val expected=prepared.expected.getValue(visit.dispatchVisitId);dispatch.updateOutboxVisit(current.copy(lastExportedGeneration=visit.generation,lastExportedMaterialHash=expected.second,lastExportedAtEpochMillis=now,lastExportedCancellationAtEpochMillis=if(visit.transportLifecycle=="CANCELED")current.canceledAtEpochMillis else current.lastExportedCancellationAtEpochMillis))}}
    suspend fun createExportFile(ids:List<String>,sender:String,cacheRoot:File):DispatchExportArtifact=createExportFile(prepareExport(ids,sender),cacheRoot)
    suspend fun createExportFile(prepared:DispatchExportPreparation,cacheRoot:File):DispatchExportArtifact{val visits=prepared.packageValue.visits;val first=visits.minOf{it.serviceDate};val last=visits.maxOf{it.serviceDate};val directory=File(cacheRoot,"work-packages").apply{check(isDirectory||mkdirs()){"Cannot create work-package cache"}};val suffix=prepared.packageValue.packageId.take(8);val final=File(directory,"serviceloop-dispatch-${first}_${last}-${visits.size}-visits-$suffix.slwork");val temp=File(directory,".${prepared.packageValue.packageId}.tmp");try{mutationFault.checkpoint("before_export_file_write");temp.outputStream().use{it.write(prepared.bytes);it.fd.sync()};require(temp.isFile&&temp.readBytes().contentEquals(prepared.bytes)){"Work-package file verification failed"};mutationFault.checkpoint("after_export_file_write_before_commit");check(temp.renameTo(final)){"Cannot finalize work-package file"};require(final.isFile&&final.readBytes().contentEquals(prepared.bytes)){"Work-package file verification failed"};commitPreparedExport(prepared);return DispatchExportArtifact(prepared.packageValue,final)}catch(failure:Throwable){temp.delete();final.delete();if(failure is CancellationException)throw failure;throw failure}}
    suspend fun concludeOutboxVisits(ids:List<String>)=database.withTransaction{val selected=ids.distinct();require(selected.isNotEmpty());val current=selected.map{dispatch.outboxVisit(it)?:error("Outbox visit missing")};require(current.all{it.outboxStatus==DispatchOutboxStatus.DISPATCHED}){"Every selected Visit must be Dispatched"};current.forEach{val changedAt=modifiedAfter(it);dispatch.updateOutboxVisit(it.copy(concludedAtEpochMillis=changedAt,modifiedAtEpochMillis=changedAt))}}
    suspend fun cancelOutboxVisits(ids:List<String>,reason:String)=database.withTransaction{val selected=ids.distinct();require(selected.isNotEmpty());val normalized=reason.trim();require(normalized.isNotEmpty()){"Cancellation reason is required"};val current=selected.map{dispatch.outboxVisit(it)?:error("Outbox visit missing")};require(current.all{it.outboxStatus==DispatchOutboxStatus.DRAFT||it.outboxStatus==DispatchOutboxStatus.DISPATCHED}){"Only Draft or Dispatched Visits can be canceled"};current.forEach{val changedAt=modifiedAfter(it);dispatch.updateOutboxVisit(it.copy(canceledAtEpochMillis=changedAt,cancellationReason=normalized,modifiedAtEpochMillis=changedAt,lastExportedCancellationAtEpochMillis=null))}}
    suspend fun reopenOutboxVisits(ids:List<String>)=database.withTransaction{val selected=ids.distinct();require(selected.isNotEmpty());val current=selected.map{dispatch.outboxVisit(it)?:error("Outbox visit missing")};require(current.all{it.outboxStatus==DispatchOutboxStatus.CONCLUDED}){"Every selected Visit must be Concluded"};current.forEach{dispatch.updateOutboxVisit(it.copy(concludedAtEpochMillis=null,modifiedAtEpochMillis=modifiedAfter(it)))}}
    private fun modifiedAfter(value:DispatchOutboxVisitEntity)=maxOf(System.currentTimeMillis(),value.modifiedAtEpochMillis+1)
    private suspend fun packageSnapshot(packageId:String,createdAt:String,sender:String,visits:List<DispatchVisit>):DispatchPackage{val siteRefs=visits.map{it.siteReference}.toSet();val sites=dao.allSites().filter{it.reference in siteRefs};val customers=dao.allCustomers().filter{c->sites.any{it.customerId==c.id}};val equipmentRefs=visits.flatMap{it.work}.map{it.equipmentReference}.toSet();val equipment=dao.allEquipment().filter{it.reference in equipmentRefs};return DispatchPackage(packageId,createdAt,sender,customers.map{DispatchCustomer(it.reference,it.name)},sites.map{s->DispatchSite(s.reference,customers.single{it.id==s.customerId}.reference,s.name,s.address)},equipment.map{e->DispatchEquipment(e.reference,sites.single{it.id==e.siteId}.reference,e.name,e.technicianIdentifier,e.make,e.model,e.serialNumber)},visits)}
    private suspend fun packageSourceHash(visits:List<DispatchVisit>)=sha256(DispatchPackageCodec.encode(packageSnapshot("source-fingerprint",Instant.EPOCH.toString(),"source-fingerprint",visits)).toString(Charsets.UTF_8))
    private fun applicable(v:DispatchVisit,id:TechnicianIdentity):List<Pair<DispatchWork,String>>{val leader=id.technicianId in v.leaderTechnicianIds;return v.work.mapNotNull{i->val assigned=i.assignedTechnicians.isEmpty()||i.assignedTechnicians.any{it.technicianId==id.technicianId};when{assigned->i to "ASSIGNED";leader->i to "LEADER_VISIBLE";else->null}}};private fun scheduled(v:DispatchVisit)=v.appointmentLocalTime?.let{LocalDate.parse(v.serviceDate).atTime(LocalTime.parse(it)).atZone(ZoneId.of(v.appointmentZoneId)).toInstant().toEpochMilli()}
    private suspend fun fingerprint(localId:String):String{val v=dao.visit(localId)?:return "missing";val b=dispatch.visitBindingForLocalVisit(localId)?:return "missing";val items=dispatch.itemBindings(b.dispatchVisitId).mapNotNull{x->x.localWorkItemId?.let{dao.workItem(it)}?.let{w->"${x.dispatchItemId}|${w.equipmentReferenceSnapshot}|${w.serviceNameSnapshot}"}}.sorted();return sha256(listOf(v.actualServiceDate,v.scheduledAtEpochMillis,v.appointmentZoneId,items.joinToString(";")).joinToString("|"))};private fun incoming(v:DispatchVisit,r:List<Pair<DispatchWork,String>>)=sha256(listOf(v.serviceDate,scheduled(v),v.appointmentZoneId,r.map{"${it.first.dispatchItemId}|${it.first.equipmentReference}|${it.first.taskName}"}.sorted().joinToString(";")).joinToString("|"))
    suspend fun preview(value:DispatchPackage):DispatchPreview {
        val id=identity(); val customers=dao.allCustomers(); val sites=dao.allSites(); val equipment=dao.allEquipment()
        val projected=value.visits.associateWith{applicable(it,id)}
        val relevant=projected.filterValues{it.isNotEmpty()}
        val neededSites=relevant.keys.map{it.siteReference}.toSet()
        val neededEquipment=relevant.values.flatten().map{it.first.equipmentReference}.toSet()
        val neededCustomers=value.sites.filter{it.reference in neededSites}.map{it.customerReference}.toSet()
        val directory=buildList {
            value.customers.filter{it.reference in neededCustomers}.forEach{x->val exact=customers.find{it.reference==x.reference};add(DispatchPreviewLine(x.reference,when{exact!=null&&normal(exact.name)==normal(x.name)->DispatchClassification.EXISTING_UNCHANGED;exact!=null->DispatchClassification.CONFLICT;customers.any{normal(it.name)==normal(x.name)}->DispatchClassification.POSSIBLE_DUPLICATE;else->DispatchClassification.NEW},"Customer · ${x.name}"))}
            value.sites.filter{it.reference in neededSites}.forEach{x->val exact=sites.find{it.reference==x.reference};val parent=exact?.let{s->customers.find{it.id==s.customerId}?.reference};add(DispatchPreviewLine(x.reference,when{exact!=null&&parent==x.customerReference&&normal(exact.name)==normal(x.name)&&normal(exact.address)==normal(x.address)->DispatchClassification.EXISTING_UNCHANGED;exact!=null->DispatchClassification.CONFLICT;sites.any{normal(it.name)==normal(x.name)&&normal(it.address)==normal(x.address)}->DispatchClassification.POSSIBLE_DUPLICATE;else->DispatchClassification.NEW},"Site · ${x.name}"))}
            value.equipment.filter{it.reference in neededEquipment}.forEach{x->val exact=equipment.find{it.reference==x.reference};val parent=exact?.let{e->sites.find{it.id==e.siteId}?.reference};add(DispatchPreviewLine(x.reference,when{exact!=null&&parent==x.siteReference&&normal(exact.name)==normal(x.name)&&normal(exact.serialNumber)==normal(x.serial)->DispatchClassification.EXISTING_UNCHANGED;exact!=null->DispatchClassification.CONFLICT;equipment.any{normal(it.serialNumber)==normal(x.serial)&&normal(x.serial).isNotBlank()}->DispatchClassification.POSSIBLE_DUPLICATE;else->DispatchClassification.NEW},"Equipment · ${x.name}"))}
        }
        val visits=value.visits.map{v->
            val r=projected.getValue(v);val b=dispatch.visitBinding(v.dispatchVisitId);val h=DispatchPackageCodec.materialHash(v);val local=b?.let{dao.visit(it.localVisitId)}
            val c=when{
                b==null&&v.transportLifecycle=="CANCELED"&&r.isEmpty()->DispatchVisitClassification.NOT_ASSIGNED
                b==null&&v.transportLifecycle=="CANCELED"->DispatchVisitClassification.CANCELED
                b==null&&r.isEmpty()->DispatchVisitClassification.NOT_ASSIGNED
                b==null->DispatchVisitClassification.NEW_VISIT
                v.generation==b.appliedGeneration&&h==b.appliedMaterialHash->DispatchVisitClassification.ALREADY_CURRENT
                v.generation==b.appliedGeneration->DispatchVisitClassification.CONFLICT
                v.generation<b.appliedGeneration->DispatchVisitClassification.OLDER_GENERATION
                v.transportLifecycle=="CANCELED"->DispatchVisitClassification.CANCELED
                r.isEmpty()&&local?.let{it.state in setOf("BOOKED","WORKING","COMPLETED","CANCELED")&&(it.state!="BOOKED"||fingerprint(b.localVisitId)==b.controlledFingerprint)}==true->DispatchVisitClassification.ASSIGNMENT_REMOVED
                r.isEmpty()->DispatchVisitClassification.UPDATE_BLOCKED
                local?.state!="BOOKED"->DispatchVisitClassification.UPDATE_BLOCKED
                fingerprint(b.localVisitId)!=b.controlledFingerprint->DispatchVisitClassification.LOCAL_CONFLICT
                else->DispatchVisitClassification.UPDATE
            }
            val reasons=when(c){
                DispatchVisitClassification.NOT_ASSIGNED->listOf("No work in this Visit is assigned to this Technician identity")
                DispatchVisitClassification.ASSIGNMENT_REMOVED->listOf("This generation removes this Technician assignment; the local Visit will become Canceled and local evidence will be preserved")
                DispatchVisitClassification.CANCELED->listOf("Coordinator canceled this Visit; Booked or Working local state will become Canceled without deleting evidence. Completed local state stays Completed and records provenance.")
                DispatchVisitClassification.CONFLICT->listOf("Same generation has different content")
                DispatchVisitClassification.LOCAL_CONFLICT->listOf("Local changes conflict with dispatch generation ${v.generation}")
                DispatchVisitClassification.UPDATE_BLOCKED->if(r.isEmpty())listOf("New dispatch generation removes this Technician assignment, but the local Visit has already started/completed")else listOf("Newer generation available, but this local Visit has already started")
                DispatchVisitClassification.OLDER_GENERATION->listOf("Older generation will not roll local work backward")
                else->emptyList()
            }
            val oldItems=b?.let{dispatch.itemBindings(it.dispatchVisitId)}.orEmpty().associateBy{it.dispatchItemId}
            val newItems=r.associateBy{it.first.dispatchItemId};val itemPreviews=(oldItems.keys+newItems.keys).sorted().map{key->val old=oldItems[key];val next=newItems[key];val change=when{old==null->"ADDED";next==null->"REMOVED";old.taskNameSnapshot!=next.first.taskName->"TASK_CHANGED";old.assignedTechniciansJson!=techJson(next.first.assignedTechnicians)||old.localRole!=next.second->"ASSIGNMENT_CHANGED";else->"UNCHANGED"};DispatchItemPreview(key,next?.first?.taskName?:old!!.taskNameSnapshot,next?.second?:old!!.localRole,change)}
            val changes=buildList{if(b!=null&&local!=null){if(local.actualServiceDate!=v.serviceDate)add(DispatchFieldChange("Service date",local.actualServiceDate,v.serviceDate));if(local.appointmentZoneId!=v.appointmentZoneId||local.scheduledAtEpochMillis!=scheduled(v))add(DispatchFieldChange("Appointment","${local.scheduledAtEpochMillis?:"None"} · ${local.appointmentZoneId?:"No zone"}","${scheduled(v)?:"None"} · ${v.appointmentZoneId}"));if(b.instructionsSnapshot.orEmpty()!=v.instructions.orEmpty())add(DispatchFieldChange("Dispatch instructions",b.instructionsSnapshot.orEmpty().ifBlank{"None"},v.instructions.orEmpty().ifBlank{"None"}));if(b.participantSnapshotJson!=techJson(v.participants))add(DispatchFieldChange("Participants",parseTech(b.participantSnapshotJson).joinToString{it.name},v.participants.joinToString{it.name}));itemPreviews.filter{it.change!="UNCHANGED"}.forEach{add(DispatchFieldChange("Item ${it.dispatchItemId.take(8)}",it.change.replace('_',' ').lowercase(),it.taskName+" · "+it.localRole.replace('_',' ').lowercase()))}}}
            val site=value.sites.single{it.reference==v.siteReference};val dependencies=if(c in setOf(DispatchVisitClassification.ASSIGNMENT_REMOVED,DispatchVisitClassification.CANCELED)) emptySet() else (setOf(v.siteReference,site.customerReference)+r.map{it.first.equipmentReference}).toSet()
            DispatchVisitPreview(v.dispatchVisitId,v.generation,c,reasons,b?.localVisitId,itemPreviews,changes,dependencies)
        }
        return DispatchPreview(value,id,directory,visits)
    }

    fun resolveDuplicate(preview:DispatchPreview,reference:String,decision:DispatchDuplicateDecision)=preview.copy(directory=preview.directory.map{if(it.reference==reference&&it.classification==DispatchClassification.POSSIBLE_DUPLICATE)it.copy(duplicateDecision=decision)else it})
    suspend fun import(p:DispatchPreview):DispatchImportResult {
        require(p.canImport||p.idempotentNoOp);val created=mutableListOf<String>();val updated=mutableListOf<String>();val unchanged=mutableListOf<String>();val withdrawn=mutableListOf<String>();val canceled=mutableListOf<String>()
        database.withTransaction {
            var fresh=this@DispatchPackageService.preview(p.value)
            p.directory.mapNotNull{line->line.duplicateDecision?.let{line.reference to it}}.forEach{(reference,decision)->fresh=resolveDuplicate(fresh,reference,decision)}
            require(fresh.canImport||fresh.idempotentNoOp)
            val skip=p.directory.filter{it.duplicateDecision==DispatchDuplicateDecision.SKIP_BRANCH}.map{it.reference}.toMutableSet()
            p.value.sites.filter{it.customerReference in skip}.forEach{skip+=it.reference};p.value.equipment.filter{it.siteReference in skip}.forEach{skip+=it.reference}
            val actionable=fresh.safeActionableVisits.associateBy{it.dispatchVisitId}
            val projected=p.value.visits.associateWith{v->applicable(v,fresh.identity).filterNot{it.first.equipmentReference in skip}}
            val activeVisits=p.value.visits.filter{v->v.dispatchVisitId in actionable&&v.siteReference !in skip&&(actionable[v.dispatchVisitId]!!.classification in setOf(DispatchVisitClassification.ASSIGNMENT_REMOVED,DispatchVisitClassification.CANCELED)||projected.getValue(v).isNotEmpty())}
            val neededSites=activeVisits.filter{actionable[it.dispatchVisitId]!!.classification!=DispatchVisitClassification.ASSIGNMENT_REMOVED}.map{it.siteReference}.toSet()
            val neededEquipment=activeVisits.flatMap{projected.getValue(it)}.map{it.first.equipmentReference}.toSet()
            val neededCustomers=p.value.sites.filter{it.reference in neededSites}.map{it.customerReference}.toSet()
            val customers=dao.allCustomers().associateBy{it.reference}.toMutableMap();p.value.customers.filter{it.reference in neededCustomers}.forEach{x->if(x.reference !in customers){CustomerEntity(stable("dispatch-customer",x.reference),x.reference,x.name).also{dao.insertCustomers(listOf(it));customers[x.reference]=it}}}
            val sites=dao.allSites().associateBy{it.reference}.toMutableMap();p.value.sites.filter{it.reference in neededSites}.forEach{x->if(x.reference !in sites){val c=customers[x.customerReference]?:error("Missing customer");SiteEntity(stable("dispatch-site",x.reference),c.id,x.reference,x.name,x.address,null).also{dao.insertSites(listOf(it));sites[x.reference]=it}}}
            val equipment=dao.allEquipment().associateBy{it.reference}.toMutableMap();p.value.equipment.filter{it.reference in neededEquipment}.forEach{x->if(x.reference !in equipment){val s=sites[x.siteReference]?:error("Missing site");EquipmentEntity(stable("dispatch-equipment",x.reference),s.id,x.reference,x.identifier,x.name,x.make,x.model,x.serial,null).also{dao.insertEquipment(listOf(it));equipment[x.reference]=it}}}
            val now=System.currentTimeMillis()
            activeVisits.forEach{v->val pv=actionable.getValue(v.dispatchVisitId);val r=projected.getValue(v);when(pv.classification){
                DispatchVisitClassification.NEW_VISIT->{val s=sites[v.siteReference]?:error("Missing site");val c=customers.values.single{it.id==s.customerId};val local=stable("dispatch-visit",v.dispatchVisitId);dao.insertVisits(listOf(WorkingVisitEntity(local,"D-${v.dispatchVisitId.take(12)}",c.id,s.id,v.serviceDate,c.name,s.name,s.address,"BOOKED",now,c.reference,s.reference,scheduledAtEpochMillis=scheduled(v),appointmentZoneId=v.appointmentZoneId)));dispatch.insertVisitBinding(DispatchVisitBindingEntity(v.dispatchVisitId,local,v.generation,p.value.packageId,p.value.senderLabel,v.managerReference,v.instructions,techJson(v.participants),idsJson(v.leaderTechnicianIds),teamsJson(v.teams),DispatchPackageCodec.materialHash(v),incoming(v,r),now,now));r.forEach{(i,role)->insertItem(v,i,role,local,equipment)};created+=local}
                DispatchVisitClassification.UPDATE->{applyUpdate(v,p.value,pv.localVisitId!!,equipment,now,r);updated+=pv.localVisitId}
                DispatchVisitClassification.CANCELED->{
                    if(pv.localVisitId==null){
                        val s=sites[v.siteReference]?:error("Missing site")
                        val c=customers.values.single{it.id==s.customerId}
                        val incomingSite=p.value.sites.single{it.reference==v.siteReference}
                        val incomingCustomer=p.value.customers.single{it.reference==incomingSite.customerReference}
                        val local=stable("dispatch-visit",v.dispatchVisitId)
                        val canceledVisit=WorkingVisitEntity(local,"D-${v.dispatchVisitId.take(12)}",c.id,s.id,v.serviceDate,incomingCustomer.name,incomingSite.name,incomingSite.address,"CANCELED",now,incomingCustomer.reference,incomingSite.reference,scheduledAtEpochMillis=scheduled(v),appointmentZoneId=v.appointmentZoneId,cancellationReason=v.cancellationReason,cancelledAtEpochMillis=now,cancellationOrigin=VisitCancellationOrigin.COORDINATOR.code)
                        dao.insertVisits(listOf(canceledVisit))
                        val binding=DispatchVisitBindingEntity(v.dispatchVisitId,local,v.generation,p.value.packageId,p.value.senderLabel,v.managerReference,v.instructions,techJson(v.participants),idsJson(v.leaderTechnicianIds),teamsJson(v.teams),DispatchPackageCodec.materialHash(v),incoming(v,r),now,now)
                        dispatch.insertVisitBinding(binding)
                        r.forEach{(i,role)->insertItem(v,i,role,local,equipment,p.value.equipment.single{it.reference==i.equipmentReference})}
                        appendEvent(canceledVisit,binding,"DISPATCH_COORDINATOR_CANCELED","Coordinator cancellation received for generation ${v.generation}: ${v.cancellationReason}; the first-seen local Visit was preserved as Canceled",null,now)
                        created+=local
                        canceled+=local
                    }else{
                        applyCoordinatorCancellation(v,p.value,pv.localVisitId!!,now)
                        updated+=pv.localVisitId
                        canceled+=pv.localVisitId
                    }
                }
                DispatchVisitClassification.ASSIGNMENT_REMOVED->{applyAssignmentRemoval(v,p.value,pv.localVisitId!!,now);updated+=pv.localVisitId;withdrawn+=pv.localVisitId}
                else->Unit
            }}
            fresh.visits.filter{it.localVisitId!=null&&it.dispatchVisitId !in activeVisits.map{x->x.dispatchVisitId}.toSet()}.forEach{unchanged+=it.localVisitId!!}
        }
        return DispatchImportResult(created,updated,unchanged.distinct(),withdrawn,canceled)
    }
    private suspend fun insertItem(v:DispatchVisit,i:DispatchWork,role:String,local:String,equipment:Map<String,EquipmentEntity>,equipmentSnapshot:DispatchEquipment?=null){val e=equipment[i.equipmentReference]?:error("Equipment missing");val work=stable("dispatch-work",v.dispatchVisitId,i.dispatchItemId);dao.insertWorkItems(listOf(WorkItemEntity(work,local,e.id,null,null,null,equipmentSnapshot?.name?:e.name,equipmentSnapshot?.reference?:e.reference,i.taskName,i.servicePlanReference,i.dueDateSnapshot,null,null,false,null,false,equipmentIdentifierSnapshot=if(equipmentSnapshot!=null)equipmentSnapshot.identifier else e.technicianIdentifier,equipmentMakeSnapshot=if(equipmentSnapshot!=null)equipmentSnapshot.make else e.make,equipmentModelSnapshot=if(equipmentSnapshot!=null)equipmentSnapshot.model else e.model,equipmentSerialSnapshot=if(equipmentSnapshot!=null)equipmentSnapshot.serial else e.serialNumber)));dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity(work,"")));dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity(work,"")));dispatch.insertItemBindings(listOf(DispatchItemBindingEntity(v.dispatchVisitId,i.dispatchItemId,work,i.equipmentReference,i.taskName,i.servicePlanReference,i.dueDateSnapshot,techJson(i.assignedTechnicians),if(i.assignedTechnicians.isEmpty())"EVERYONE" else "EXPLICIT",role,if(role=="LEADER_VISIBLE")"LEADER_OBSERVE" else "PENDING")))}
    private suspend fun applyUpdate(v:DispatchVisit,p:DispatchPackage,local:String,equipment:Map<String,EquipmentEntity>,now:Long,r:List<Pair<DispatchWork,String>>){check(dispatch.updateBookedDispatchVisit(local,v.serviceDate,scheduled(v),v.appointmentZoneId,now)==1);val existing=dispatch.itemBindings(v.dispatchVisitId).associateBy{it.dispatchItemId};existing.values.filter{it.dispatchItemId !in r.map{x->x.first.dispatchItemId}.toSet()}.forEach{x->x.localWorkItemId?.let{dispatch.deleteWorkItem(it)};dispatch.deleteItemBinding(v.dispatchVisitId,x.dispatchItemId)};r.forEach{(i,role)->val old=existing[i.dispatchItemId];if(old==null)insertItem(v,i,role,local,equipment)else{val e=equipment[i.equipmentReference]?:error("Equipment missing");check(dispatch.updateBookedDispatchWork(old.localWorkItemId!!,e.id,e.name,e.reference,e.technicianIdentifier,e.make,e.model,e.serialNumber,i.taskName)==1);dispatch.updateItemBinding(old.copy(equipmentReferenceSnapshot=i.equipmentReference,taskNameSnapshot=i.taskName,servicePlanReferenceSnapshot=i.servicePlanReference,dueDateSnapshot=i.dueDateSnapshot,assignedTechniciansJson=techJson(i.assignedTechnicians),assignmentMeaning=if(i.assignedTechnicians.isEmpty())"EVERYONE" else "EXPLICIT",localRole=role,documentationDisposition=if(role=="LEADER_VISIBLE")"LEADER_OBSERVE" else "PENDING",deferredToTechnicianId=null,deferredToName=null))}};val b=dispatch.visitBinding(v.dispatchVisitId)!!;dispatch.updateVisitBinding(b.copy(appliedGeneration=v.generation,packageId=p.packageId,senderLabel=p.senderLabel,managerReference=v.managerReference,instructionsSnapshot=v.instructions,participantSnapshotJson=techJson(v.participants),leaderIdsJson=idsJson(v.leaderTechnicianIds),teamSnapshotJson=teamsJson(v.teams),appliedMaterialHash=DispatchPackageCodec.materialHash(v),controlledFingerprint=incoming(v,r),updatedAtEpochMillis=now))}
    private suspend fun applyCoordinatorCancellation(v:DispatchVisit,p:DispatchPackage,local:String,now:Long){
        val visit=dao.visit(local)?:error("Visit missing")
        val binding=dispatch.visitBinding(v.dispatchVisitId)?:error("Dispatch binding missing")
        if(visit.state=="BOOKED"||visit.state=="WORKING"){
            dao.updateVisit(visit.copy(state="CANCELED",cancellationReason=v.cancellationReason,cancellationOrigin=VisitCancellationOrigin.COORDINATOR.code,cancelledAtEpochMillis=now,modifiedAtEpochMillis=now))
            dao.releaseVisitClaims(local)
        }else if(visit.state=="CANCELED"){
            // A later Coordinator package records provenance, but does not replace the first cause.
            dao.updateVisit(visit.copy(modifiedAtEpochMillis=now))
        }else{
            // COMPLETED is not a cancellation transition; preserve its null/legacy cause.
            dao.updateVisit(visit.copy(modifiedAtEpochMillis=now))
        }
        dispatch.updateVisitBinding(binding.copy(appliedGeneration=v.generation,packageId=p.packageId,senderLabel=p.senderLabel,managerReference=v.managerReference,instructionsSnapshot=v.instructions,participantSnapshotJson=techJson(v.participants),leaderIdsJson=idsJson(v.leaderTechnicianIds),teamSnapshotJson=teamsJson(v.teams),appliedMaterialHash=DispatchPackageCodec.materialHash(v),controlledFingerprint="coordinator-canceled",updatedAtEpochMillis=now))
        appendEvent(visit,binding,"DISPATCH_COORDINATOR_CANCELED","Coordinator cancellation received for generation ${v.generation}: ${v.cancellationReason}; local evidence and any completed record were preserved",null,now)
    }
    private suspend fun applyAssignmentRemoval(v:DispatchVisit,p:DispatchPackage,local:String,now:Long){
        val visit=dao.visit(local)?:error("Visit missing")
        val binding=dispatch.visitBinding(v.dispatchVisitId)?:error("Dispatch binding missing")
        if(visit.state=="BOOKED"&&fingerprint(local)!=binding.controlledFingerprint) error("Local changes prevent automatic assignment removal")
        val items=dispatch.itemBindings(v.dispatchVisitId)
        items.forEach{item->val incoming=v.work.find{it.dispatchItemId==item.dispatchItemId};dispatch.updateItemBinding(item.copy(assignedTechniciansJson=incoming?.let{techJson(it.assignedTechnicians)}?:item.assignedTechniciansJson,assignmentMeaning=incoming?.let{if(it.assignedTechnicians.isEmpty())"EVERYONE" else "EXPLICIT"}?:item.assignmentMeaning,localRole="ASSIGNMENT_REMOVED",documentationDisposition=if(item.documentationDisposition=="DOCUMENT_LOCAL")"DOCUMENT_LOCAL" else "DEFERRED",deferredToTechnicianId=null,deferredToName=null))}
        if(visit.state=="BOOKED"||visit.state=="WORKING"){
            dao.updateVisit(visit.copy(state="CANCELED",cancellationReason="Coordinator removed this Technician assignment",cancellationOrigin=VisitCancellationOrigin.ASSIGNMENT_REMOVAL.code,cancelledAtEpochMillis=now,modifiedAtEpochMillis=now));dao.releaseVisitClaims(local)
        }else if(visit.state=="CANCELED"||visit.state=="COMPLETED") {
            // A later assignment event does not manufacture or replace the first cancellation cause.
            dao.updateVisit(visit.copy(modifiedAtEpochMillis=now))
        }
        dispatch.updateVisitBinding(binding.copy(appliedGeneration=v.generation,packageId=p.packageId,senderLabel=p.senderLabel,managerReference=v.managerReference,instructionsSnapshot=v.instructions,participantSnapshotJson=techJson(v.participants),leaderIdsJson=idsJson(v.leaderTechnicianIds),teamSnapshotJson=teamsJson(v.teams),appliedMaterialHash=DispatchPackageCodec.materialHash(v),controlledFingerprint="assignment-removed",updatedAtEpochMillis=now))
        appendEvent(visit,binding,"DISPATCH_ASSIGNMENT_REMOVED","Generation ${v.generation} removed this Technician assignment; the local Visit is Canceled and local work/evidence was preserved",null,now)
    }
    suspend fun dispatchDetail(local:String)=dispatch.visitBindingForLocalVisit(local)?.let{it to dispatch.itemBindings(it.dispatchVisitId)}
    suspend fun documentLocally(local:String,itemId:String):String=database.withTransaction{val visit=dao.visit(local)?:error("Visit missing");require(visit.state=="WORKING");val vb=dispatch.visitBindingForLocalVisit(local)?:error("Not dispatched");val item=dispatch.itemBinding(vb.dispatchVisitId,itemId)?:error("Item missing");val work=item.localWorkItemId?:error("Work missing");val plan=dao.allPlans().find{it.reference==item.servicePlanReferenceSnapshot};val eq=dao.workItem(work)?.let{dao.equipment(it.equipmentId)};val obligation=plan?.currentObligationId?.let{dao.obligation(it)};val safe=plan!=null&&eq!=null&&plan.equipmentId==eq.id&&plan.state=="ACTIVE"&&obligation!=null&&obligation.consumedAtEpochMillis==null&&dao.claimForObligation(obligation.id)==null&&(item.dueDateSnapshot==null||item.dueDateSnapshot==obligation.dueDate);if(safe){check(dispatch.linkDispatchWork(work,plan!!.id,obligation!!.id,plan.reference,obligation.dueDate,plan.intervalCount,plan.intervalUnit)==1);dao.insertVisitClaim(VisitClaimEntity(obligation.id,local,System.currentTimeMillis()))};dispatch.updateItemBinding(item.copy(documentationDisposition="DOCUMENT_LOCAL",deferredToTechnicianId=null,deferredToName=null));if(safe)"Linked to current local obligation" else "Documenting as one-off"}
    private suspend fun substantive(workId:String,binding:DispatchVisitBindingEntity):Boolean{val w=dao.workItem(workId)?:return false;val privateNote=dao.privateDraft(workId)?.internalNote.orEmpty();return dao.publicDraft(workId)?.workPerformed?.isNotBlank()==true||(privateNote.isNotBlank()&&privateNote!=binding.instructionsSnapshot.orEmpty())||w.checklistReviewed||w.outcome!=null||w.fulfillsCurrentObligation==true||w.notPerformedReason!=null||w.confirmedNextDueDate!=null||w.nextDueDateCalculated!=null||w.nextDueOverrideReason!=null||dispatch.responseCount(workId)>0||dispatch.partCount(workId)>0||dispatch.attachmentCount(workId)>0}
    suspend fun handoffReview(local:String,itemId:String):HandoffReview{val vb=dispatch.visitBindingForLocalVisit(local)?:error("Not dispatched");val item=dispatch.itemBinding(vb.dispatchVisitId,itemId)?:error("Item missing");val self=identity().technicianId;val candidates=((if(item.assignmentMeaning=="EVERYONE")parseTech(vb.participantSnapshotJson)else parseTech(item.assignedTechniciansJson))+parseTech(vb.participantSnapshotJson).filter{it.technicianId in parseIds(vb.leaderIdsJson)}).distinctBy{it.technicianId}.filter{it.technicianId!=self};return HandoffReview(candidates,item.localWorkItemId?.let{substantive(it,vb)}==true)}
    suspend fun handoff(local:String,itemId:String,target:DispatchTechnicianSnapshot,discard:Boolean=false)=BusinessFileCoordinator.mutex.withLock{
        val initialBinding=dispatch.visitBindingForLocalVisit(local)?:error("Not dispatched");val initialItem=dispatch.itemBinding(initialBinding.dispatchVisitId,itemId)?:error("Item missing");val workId=initialItem.localWorkItemId?:error("Work missing");require(!substantive(workId,initialBinding)||discard){"Local documentation exists; explicitly discard it before handoff"}
        val attachments=if(discard)dispatch.workAttachments(workId)else emptyList();val root=fileRoot
        val backupDir=if(attachments.isNotEmpty()){requireNotNull(root){"App-owned storage is unavailable"};File(root,"dispatch-handoff-rollback/${UUID.randomUUID()}").apply{check(mkdirs())}}else null
        val originals=attachments.map{a->val owned=File(requireNotNull(root),"attachments").canonicalFile;val file=File(root,a.storedRelativePath).canonicalFile;require(file.path.startsWith(owned.path+File.separator)){"Unsafe attachment path"};require(file.isFile&&file.length()==a.byteSize){"Draft attachment is missing or changed"};require(MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString(""){"%02x".format(it)}==a.sha256){"Draft attachment is missing or changed"};val backup=File(backupDir,a.id);file.copyTo(backup);require(backup.length()==a.byteSize);Triple(a,file,backup)}
        try{
            database.withTransaction{
                val visit=dao.visit(local)?:error("Visit missing");require(visit.state=="WORKING");val binding=dispatch.visitBindingForLocalVisit(local)?:error("Not dispatched");val item=dispatch.itemBinding(binding.dispatchVisitId,itemId)?:error("Item missing");require(item.localWorkItemId==workId);val eligible=handoffReview(local,itemId).candidates.map{it.technicianId}.toSet();require(target.technicianId in eligible)
                mutationFault.checkpoint("before_file_delete");originals.forEach{(_,file,_)->check(file.delete()){"Draft attachment cleanup failed"}};mutationFault.checkpoint("after_file_delete")
                val work=dao.workItem(workId)?:error("Work missing");if(discard){dispatch.deleteResponses(workId);dispatch.deleteParts(workId);dispatch.deleteWorkAttachments(workId);dispatch.clearPublicDraft(workId);dispatch.clearPrivateDraft(workId);dispatch.clearCompletionDraft(workId)}
                work.capturedObligationId?.let{dispatch.releaseItemClaim(local,it)};check(dispatch.unlinkDispatchWork(workId)==1);dispatch.updateItemBinding(item.copy(documentationDisposition="DEFERRED",deferredToTechnicianId=target.technicianId,deferredToName=target.name));val now=System.currentTimeMillis();appendEvent(visit,binding,"DISPATCH_DOCUMENTATION_DEFERRED","Local documentation handed off to ${target.name} (${target.technicianId}); ServiceLoop cannot confirm acceptance",item,now);mutationFault.checkpoint("before_db_commit")
            }
            backupDir?.let{check(it.deleteRecursively()){"Rollback evidence cleanup failed"}}
        }catch(failure:Throwable){originals.forEach{(_,file,backup)->if(!file.exists()&&backup.isFile){file.parentFile?.mkdirs();backup.copyTo(file)}};backupDir?.deleteRecursively();if(failure is CancellationException)throw failure;throw failure}
    }
    suspend fun undoHandoff(local:String,itemId:String)=database.withTransaction{val visit=dao.visit(local)?:error("Visit missing");require(visit.state=="WORKING");val vb=dispatch.visitBindingForLocalVisit(local)?:error("Not dispatched");val item=dispatch.itemBinding(vb.dispatchVisitId,itemId)?:error("Item missing");require(item.documentationDisposition=="DEFERRED");dispatch.updateItemBinding(item.copy(documentationDisposition=if(item.localRole=="LEADER_VISIBLE")"LEADER_OBSERVE" else "PENDING",deferredToTechnicianId=null,deferredToName=null));appendEvent(visit,vb,"DISPATCH_DOCUMENTATION_RESUMED","Local technician resumed the option to document; no recurring obligation was claimed",item,System.currentTimeMillis())}
     suspend fun finishInvolvement(local:String)=database.withTransaction{val visit=dao.visit(local)?:error("Visit missing");require(visit.state=="WORKING");val vb=dispatch.visitBindingForLocalVisit(local)?:error("Not dispatched");val items=dispatch.itemBindings(vb.dispatchVisitId);require(items.none{it.documentationDisposition=="DOCUMENT_LOCAL"}&&items.filter{it.localRole=="ASSIGNED"}.all{it.documentationDisposition=="DEFERRED"});items.mapNotNull{it.localWorkItemId}.forEach{workId->dao.workItem(workId)?.capturedObligationId?.let{dispatch.releaseItemClaim(local,it)};dispatch.unlinkDispatchWork(workId)};val now=System.currentTimeMillis();dao.updateVisit(visit.copy(state="COMPLETED",modifiedAtEpochMillis=now));dao.releaseVisitClaims(local);appendEvent(visit,vb,"DISPATCH_VISIT_COMPLETED","Visit completed locally without creating a final record or PDF; this does not confirm central acceptance",null,now)}
    private suspend fun appendEvent(visit:WorkingVisitEntity,binding:DispatchVisitBindingEntity,type:String,reason:String,item:DispatchItemBindingEntity?,now:Long){val identity=identity();val equipment=item?.localWorkItemId?.let{dao.workItem(it)}?.let{dao.equipment(it.equipmentId)};dao.insertChangeEntry(ChangeEntryEntity(UUID.randomUUID().toString(),"VISIT",visit.id,type,visit.actualServiceDate,now,reason,"dispatchVisitId=${binding.dispatchVisitId}",listOfNotNull(item?.let{"dispatchItemId=${it.dispatchItemId}"},"localTechnician=${identity.name} (${identity.technicianId})").joinToString(" · "),visit.customerId,visit.siteId,equipment?.id,null,visit.customerNameSnapshot,visit.siteNameSnapshot,equipment?.name))}
    fun parseTech(json:String):List<DispatchTechnicianSnapshot>{val a=JSONArray(json);return(0 until a.length()).map{a.getJSONObject(it).let{o->DispatchTechnicianSnapshot(o.getString("technicianId"),o.getString("name"))}}};fun parseIds(json:String):List<String>{val a=JSONArray(json);return(0 until a.length()).map{a.getString(it)}}
}
internal fun sha256(v:String)=MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString(""){"%02x".format(it)}
