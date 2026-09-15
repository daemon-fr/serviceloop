package com.v16studio.serviceloop.data

import androidx.room.withTransaction
import com.v16studio.serviceloop.domain.CustomerType
import com.v16studio.serviceloop.domain.VisitCancellationOrigin
import com.v16studio.serviceloop.domain.OneTimeVisitInput
import com.v16studio.serviceloop.domain.WorkSubjectType
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


fun interface DispatchMutationFault { fun checkpoint(name: String) }

class DispatchPackageService(
    private val database: ServiceLoopDatabase,
    private val fileRoot: File? = null,
    private val mutationFault: DispatchMutationFault = DispatchMutationFault { },
) {
    private val dao=database.serviceLoopDao();private val dispatch=database.dispatchDao()
    private fun normal(v:String?)=v.orEmpty().trim().lowercase().replace(Regex("\\s+")," ");private fun stable(vararg p:String)=UUID.nameUUIDFromBytes(p.joinToString("|").toByteArray()).toString()
    private fun techJson(v:List<DispatchTechnicianSnapshot>)=JSONArray(v.map{JSONObject().put("technicianId",it.technicianId).put("name",it.name).put("designation",it.designation?:JSONObject.NULL)}).toString();private fun teamsJson(v:List<DispatchTeamSnapshot>)=JSONArray(v.map{JSONObject().put("teamId",it.teamId).put("name",it.name).put("memberIds",JSONArray(it.memberIds)).put("leaderIds",JSONArray(it.leaderIds))}).toString();private fun idsJson(v:List<String>)=JSONArray(v).toString()
    suspend fun identity():TechnicianIdentity {
        val profileName = dao.businessProfile()?.technicianName?.trim()?.takeIf { it.isNotEmpty() }
        val existing = dispatch.technicianIdentity()
        if (existing != null) {
            val synced = if (profileName != null && existing.displayName != profileName) {
                existing.copy(displayName = profileName, modifiedAtEpochMillis = System.currentTimeMillis()).also { dispatch.updateTechnicianIdentity(it) }
            } else existing
            dispatch.technician(synced.technicianId)?.let { directory ->
                if (directory.displayName != synced.displayName) dispatch.updateTechnician(directory.copy(displayName = synced.displayName, modifiedAtEpochMillis = synced.modifiedAtEpochMillis))
            }
            return TechnicianIdentity(synced.technicianId, synced.displayName, synced.designation)
        }
        val now = System.currentTimeMillis()
        val created = TechnicianIdentityEntity(technicianId = TechnicianIdCodec.generate(), displayName = profileName ?: "Technician", createdAtEpochMillis = now, modifiedAtEpochMillis = now)
        runCatching { dispatch.insertTechnicianIdentity(created) }
        return dispatch.technicianIdentity()!!.let { TechnicianIdentity(it.technicianId, it.displayName, it.designation) }
    }
    suspend fun updateIdentityDesignation(value:String?):TechnicianIdentity {
        val designation = value?.trim()?.takeIf { it.isNotEmpty() }
        require(designation.orEmpty().length <= 200) { "Team designation or badge is too long" }
        val current = identity(); val now = System.currentTimeMillis(); val row = dispatch.technicianIdentity()!!
        if (row.designation != designation) dispatch.updateTechnicianIdentity(row.copy(designation = designation, modifiedAtEpochMillis = now))
        dispatch.technician(current.technicianId)?.let { dispatch.updateTechnician(it.copy(designation = designation, modifiedAtEpochMillis = now)) }
        return identity()
    }
    @Deprecated("Actual technician name is managed by BusinessProfile")
    suspend fun renameIdentity(name:String):TechnicianIdentity {
        val profileName = dao.businessProfile()?.technicianName?.trim()
        require(profileName == name.trim()) { "Technician name is managed under Business and report identity" }
        return identity()
    }
    suspend fun importTechnician(v:TechnicianIdentity,confirmRename:Boolean=false){
        require(v.name.trim().isNotEmpty()) { "Technician name is required" }
        require(v.designation.orEmpty().length <= 200) { "Team designation or badge is too long" }
        val name=v.name.trim(); val designation=v.designation?.trim()?.takeIf{it.isNotEmpty()}; val now=System.currentTimeMillis(); val p=dispatch.technician(v.technicianId)
        when{p==null->dispatch.insertTechnician(DispatchTechnicianEntity(v.technicianId,name,now,now,designation));p.displayName==name&&p.designation==designation->Unit;confirmRename->dispatch.updateTechnician(p.copy(displayName=name,designation=designation,modifiedAtEpochMillis=now));else->error("Technician ID already exists with a different name or designation; confirm the update")}
    }
    suspend fun importTechnicianManually(v:TechnicianIdentity,confirmRename:Boolean=false){val normalized=TechnicianIdCodec.normalize(v.technicianId)?:throw IllegalArgumentException("Technician ID is not a valid ServiceLoop Technician ID.");importTechnician(v.copy(technicianId=normalized),confirmRename)}
    suspend fun technicianRenameReview(v:TechnicianIdentity)=dispatch.technician(v.technicianId)?.takeIf{it.displayName!=v.name||it.designation!=v.designation}?.let{TechnicianRenameReview(v,it.displayName,it.designation)}
    suspend fun technicians()=dispatch.technicians();suspend fun createTeam(name:String):String{require(name.trim().isNotEmpty());val id=UUID.randomUUID().toString();val n=System.currentTimeMillis();dispatch.insertTeam(DispatchTeamEntity(id,name.trim(),n,n));return id}
    suspend fun renameTeam(id:String,name:String){val team=dispatch.team(id)?:error("Team missing");require(name.trim().isNotEmpty());dispatch.updateTeam(team.copy(name=name.trim(),modifiedAtEpochMillis=System.currentTimeMillis()))}
    suspend fun setTeamMember(teamId:String,techId:String,present:Boolean,isLeader:Boolean=false){dispatch.team(teamId)?:error("Team missing");dispatch.technician(techId)?:error("Technician missing");val c=dispatch.teamMembers(teamId).find{it.technicianId==techId};if(!present&&c!=null)dispatch.removeTeamMember(teamId,techId)else if(present&&c==null)dispatch.insertTeamMember(DispatchTeamMemberEntity(teamId,techId,isLeader))else if(present&&c!!.isLeader!=isLeader)dispatch.updateTeamMember(c.copy(isLeader=isLeader))}
    suspend fun teams():List<DispatchTeamDetail>{val t=dispatch.technicians().associateBy{it.technicianId};return dispatch.teams().map{x->DispatchTeamDetail(x,dispatch.teamMembers(x.id).mapNotNull{m->t[m.technicianId]?.let{it to m.isLeader}})}}
    suspend fun createOutboxVisit(manager:String?,siteId:String,date:String,time:String?,zone:String,instructions:String?,teamIds:List<String>):String=database.withTransaction{LocalDate.parse(date);time?.let(LocalTime::parse);ZoneId.of(zone);require(teamIds.isNotEmpty());dao.site(siteId)?:error("Site missing");val id=UUID.randomUUID().toString();val n=System.currentTimeMillis();dispatch.insertOutboxVisit(DispatchOutboxVisitEntity(id,manager?.trim()?.takeIf{it.isNotEmpty()},siteId,date,time,zone,instructions?.trim()?.takeIf{it.isNotEmpty()},null,null,null,n,n));dispatch.insertOutboxVisitTeams(teamIds.distinct().map{DispatchOutboxVisitTeamEntity(id,it)});id}
    suspend fun saveOutboxVisit(draft:DispatchOutboxEditorDraft):String=database.withTransaction{
        val date=LocalDate.parse(draft.serviceDate).toString()
        val time=draft.appointmentLocalTime?.trim()?.takeIf{it.isNotEmpty()}?.let{LocalTime.parse(it).toString()}
        val zone=ZoneId.of(draft.appointmentZoneId.trim()).id
        val existing=draft.dispatchVisitId?.let{dispatch.outboxVisit(it)}
        if(draft.dispatchVisitId!=null){
            require(existing!=null){"Outbox Visit no longer exists"}
            require(existing.outboxStatus!=DispatchOutboxStatus.CONCLUDED){"Reopen this concluded Visit before editing"}
            require(draft.expectedModifiedAtEpochMillis==existing.modifiedAtEpochMillis){"This Dispatch Visit changed after the editor was opened. Reopen it and review the latest version."}
        }
        require(draft.oneTimeSite == null || draft.dispatchVisitId == null){"One-time customer details are only available for a new Visit"}
        val oneTime=draft.oneTimeSite
        val site=if(oneTime!=null){
            validateOneTimeDispatchInput(oneTime)
            val customerId=UUID.randomUUID().toString()
            val siteId=UUID.randomUUID().toString()
            val customerName=oneTime.customerName.trim()
            val address=oneTime.address.trim().takeIf{it.isNotEmpty()}
            val siteName=oneTime.locationLabel.trim().ifBlank{address?:customerName}
            val customer=CustomerEntity(customerId,ordinaryReference("CU",dao.customerCount()+1),customerName,phone=oneTime.phone.trim().takeIf{it.isNotEmpty()},email=oneTime.email.trim().takeIf{it.isNotEmpty()},customerType=CustomerType.ONE_TIME.code)
            dao.insertCustomers(listOf(customer))
            val created=SiteEntity(siteId,customerId,ordinaryReference("ST",dao.siteCount()+1),siteName,address,null,isDefault=true)
            dao.insertSites(listOf(created))
            created
        }else{
            require(draft.siteId.isNotBlank()){"Choose a valid Site"}
            dao.site(draft.siteId)?:error("Choose a valid Site")
        }
        val customer=dao.customer(site.customerId)?:error("Customer missing")
        val teamIds=draft.teamIds.distinct()
        require(teamIds.isNotEmpty()){"Choose at least one Team"}
        require(teamIds.all{dispatch.team(it)!=null}){"A selected Team no longer exists"}
        require(draft.items.map{it.dispatchItemId}.distinct().size==draft.items.size){"Duplicate work item identity"}
        val participants=dispatch.allTeamMembers().filter{it.teamId in teamIds}.map{it.technicianId}.toSet()
        require(participants.isNotEmpty()){ "Selected Teams need at least one Technician" }
        draft.items.forEach{item->
            require(item.dispatchItemId.isNotBlank()){"Work item identity is missing"}
            require(item.taskName.trim().isNotEmpty()){"Every work item needs a task name"}
            validateOutboxSubject(site,customer,item.subjectType,item.equipmentId,item.equipmentDescription,item.servicePlanReference,item.dueDateSnapshot)
            require(item.assignedTechnicianIds.distinct().all{it in participants}){"An item assignee is outside the selected Teams"}
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
            val normalizedDescription=item.equipmentDescription.trim().takeIf{it.isNotEmpty()}
            val entity=DispatchOutboxItemEntity(item.dispatchItemId,id,index,item.equipmentId,item.taskName.trim(),item.servicePlanReference?.trim()?.takeIf{it.isNotEmpty()},item.dueDateSnapshot?.trim()?.takeIf{it.isNotEmpty()},item.subjectType.code,normalizedDescription)
            if(item.dispatchItemId in remaining)dispatch.updateOutboxItem(entity) else dispatch.insertOutboxItem(entity)
            dispatch.clearOutboxItemAssignees(item.dispatchItemId)
            dispatch.insertOutboxItemAssignees(item.assignedTechnicianIds.distinct().map{DispatchOutboxItemAssigneeEntity(item.dispatchItemId,it)})
        }
        id
    }
    private fun ordinaryReference(prefix:String,sequence:Int)="$prefix-${sequence.toString().padStart(3,'0')}"
    private fun validateOneTimeDispatchInput(input:OneTimeVisitInput){
        require(input.customerName.trim().isNotBlank()){"Customer name is required"}
        require(input.customerName.trim().length<=200){"Customer name must be 200 characters or fewer"}
        require(input.phone.trim().length<=100){"Phone must be 100 characters or fewer"}
        require(input.email.trim().length<=320){"Email must be 320 characters or fewer"}
        require(input.locationLabel.trim().length<=200){"Location label must be 200 characters or fewer"}
        require(input.address.trim().length<=500){"Address must be 500 characters or fewer"}
    }
    private suspend fun validateOutboxSubject(site:SiteEntity,customer:CustomerEntity,subjectType:WorkSubjectType,equipmentId:String?,equipmentDescription:String,planReference:String?,dueDate:String?):EquipmentEntity?{
        val normalizedPlan=planReference?.trim()?.takeIf{it.isNotEmpty()}
        val normalizedDue=dueDate?.trim()?.takeIf{it.isNotEmpty()}
        return when(subjectType){
            WorkSubjectType.SITE->{
                require(equipmentId==null){"SITE work cannot reference Equipment"}
                require(equipmentDescription.trim().isEmpty()){"SITE work cannot have an Equipment description"}
                require(normalizedPlan==null&&normalizedDue==null){"SITE work cannot carry recurring provenance"}
                null
            }
            WorkSubjectType.EQUIPMENT->{
                val equipment=equipmentId?.let{dao.equipment(it)}
                if(equipment!=null){
                    require(equipment.state=="ACTIVE"){"Equipment is not active"}
                    require(equipment.siteId==site.id){"Work items must use Equipment from the selected Site"}
                    require(equipmentDescription.trim().isEmpty()){"Known Equipment work cannot have an unidentified description"}
                    normalizedPlan?.let{reference->
                        require(CustomerType.fromCode(customer.customerType)==CustomerType.STANDARD){"Recurring service requires a Standard customer"}
                        val plan=dao.allPlans().firstOrNull{it.reference==reference}?:error("Service Plan no longer exists")
                        require(plan.equipmentId==equipment.id){"Service Plan does not belong to the selected Equipment"}
                    }
                    normalizedDue?.let{date->require(normalizedPlan!=null){"Due date requires a service plan reference"};LocalDate.parse(date)}
                    equipment
                }else{
                    require(equipmentId==null){"A selected Equipment item no longer exists"}
                    require(equipmentDescription.trim().isEmpty()||equipmentDescription.trim().length<=500){"Equipment description must be 500 characters or fewer"}
                    require(normalizedPlan==null&&normalizedDue==null){"Unidentified Equipment work cannot carry recurring provenance"}
                    null
                }
            }
        }
    }
    suspend fun updateOutboxVisit(id:String,manager:String?,siteId:String,date:String,time:String?,zone:String,instructions:String?,teamIds:List<String>)=database.withTransaction{
        LocalDate.parse(date);time?.takeIf{it.isNotBlank()}?.let(LocalTime::parse);ZoneId.of(zone);require(teamIds.isNotEmpty());val old=dispatch.outboxVisit(id)?:error("Outbox visit missing");require(old.outboxStatus!=DispatchOutboxStatus.CONCLUDED&&old.outboxStatus!=DispatchOutboxStatus.CANCELED){"Canceled and concluded Visits are read-only"};val site=dao.site(siteId)?:error("Site missing");val customer=dao.customer(site.customerId)?:error("Customer missing");dispatch.outboxItems(id).forEach{item->validateOutboxSubject(site,customer,WorkSubjectType.fromCode(item.subjectType),item.equipmentId,item.equipmentDescription.orEmpty(),item.servicePlanReference,item.dueDateSnapshot)};dispatch.clearOutboxVisitTeams(id);dispatch.insertOutboxVisitTeams(teamIds.distinct().map{DispatchOutboxVisitTeamEntity(id,it)});val participants=expand(id).first.map{it.technicianId}.toSet();dispatch.outboxItems(id).forEach{item->require(dispatch.outboxItemAssignees(item.dispatchItemId).all{it.technicianId in participants}){"An item assignee is outside the selected Teams"}};dispatch.updateOutboxVisit(old.copy(managerReference=manager?.trim()?.takeIf{it.isNotEmpty()},siteId=siteId,serviceDate=date,appointmentLocalTime=time?.trim()?.takeIf{it.isNotEmpty()},appointmentZoneId=zone,instructions=instructions?.trim()?.takeIf{it.isNotEmpty()},modifiedAtEpochMillis=modifiedAfter(old)))}
    suspend fun addOutboxItem(visitId:String,equipmentId:String,task:String,plan:String?,due:String?,assigned:List<String>):String =
        addOutboxItem(visitId,WorkSubjectType.EQUIPMENT,equipmentId,"",task,plan,due,assigned)
    suspend fun addOutboxItem(visitId:String,subjectType:WorkSubjectType,equipmentId:String?,equipmentDescription:String,task:String,plan:String?,due:String?,assigned:List<String>):String=database.withTransaction{
        require(task.trim().isNotEmpty());val v=dispatch.outboxVisit(visitId)?:error("Outbox visit missing");require(v.outboxStatus!=DispatchOutboxStatus.CONCLUDED){"Reopen this concluded Visit before editing"};val site=dao.site(v.siteId)?:error("Site missing");val customer=dao.customer(site.customerId)?:error("Customer missing");validateOutboxSubject(site,customer,subjectType,equipmentId,equipmentDescription,plan,due);val participants=expand(visitId).first.map{it.technicianId}.toSet();require(assigned.all{it in participants}){"Assignee is outside selected teams"};val id=UUID.randomUUID().toString();dispatch.insertOutboxItem(DispatchOutboxItemEntity(id,visitId,dispatch.outboxItems(visitId).size,equipmentId,task.trim(),plan?.trim()?.takeIf{it.isNotEmpty()},due?.trim()?.takeIf{it.isNotEmpty()},subjectType.code,equipmentDescription.trim().takeIf{it.isNotEmpty()}));dispatch.insertOutboxItemAssignees(assigned.distinct().map{DispatchOutboxItemAssigneeEntity(id,it)});dispatch.updateOutboxVisit(v.copy(modifiedAtEpochMillis=modifiedAfter(v)));id}
    suspend fun updateOutboxItem(itemId:String,equipmentId:String,task:String,plan:String?,due:String?,assigned:List<String>) =
        updateOutboxItem(itemId,WorkSubjectType.EQUIPMENT,equipmentId,"",task,plan,due,assigned)
    suspend fun updateOutboxItem(itemId:String,subjectType:WorkSubjectType,equipmentId:String?,equipmentDescription:String,task:String,plan:String?,due:String?,assigned:List<String>)=database.withTransaction{
        require(task.trim().isNotEmpty());val old=dispatch.outboxItems().find{it.dispatchItemId==itemId}?:error("Outbox item missing");val visit=dispatch.outboxVisit(old.dispatchVisitId)?:error("Outbox Visit missing");require(visit.outboxStatus!=DispatchOutboxStatus.CONCLUDED){"Reopen this concluded Visit before editing"};val site=dao.site(visit.siteId)?:error("Site missing");val customer=dao.customer(site.customerId)?:error("Customer missing");validateOutboxSubject(site,customer,subjectType,equipmentId,equipmentDescription,plan,due);val participants=expand(visit.dispatchVisitId).first.map{it.technicianId}.toSet();require(assigned.all{it in participants}){"Assignee is outside selected Teams"};dispatch.clearOutboxItemAssignees(itemId);dispatch.insertOutboxItemAssignees(assigned.distinct().map{DispatchOutboxItemAssigneeEntity(itemId,it)});dispatch.updateOutboxItem(old.copy(subjectType=subjectType.code,equipmentId=equipmentId,equipmentDescription=equipmentDescription.trim().takeIf{it.isNotEmpty()},taskName=task.trim(),servicePlanReference=plan?.trim()?.takeIf{it.isNotEmpty()},dueDateSnapshot=due?.trim()?.takeIf{it.isNotEmpty()}));dispatch.updateOutboxVisit(visit.copy(modifiedAtEpochMillis=modifiedAfter(visit)))}
    suspend fun outboxItems(visitId:String)=dispatch.outboxItems(visitId).map{it to dispatch.outboxItemAssignees(it.dispatchItemId).map{x->x.technicianId}}
    suspend fun outboxParticipants(visitId:String)=expand(visitId)
    suspend fun outboxTeamIds(visitId:String)=dispatch.outboxVisitTeams(visitId).map{it.teamId}
    suspend fun outboxVisits()=dispatch.outboxVisits();private suspend fun expand(id:String):Pair<List<DispatchTechnicianEntity>,Set<String>>{val teams=dispatch.outboxVisitTeams(id).map{it.teamId}.toSet();val members=dispatch.allTeamMembers().filter{it.teamId in teams};val tech=dispatch.technicians().associateBy{it.technicianId};return members.mapNotNull{tech[it.technicianId]}.distinctBy{it.technicianId} to members.filter{it.isLeader}.map{it.technicianId}.toSet()}
    suspend fun rescheduleOutboxVisit(id:String,date:String,time:String?,zone:String){LocalDate.parse(date);time?.let(LocalTime::parse);ZoneId.of(zone);val value=dispatch.outboxVisit(id)?:error("Outbox visit missing");require(value.outboxStatus!=DispatchOutboxStatus.CONCLUDED){"Reopen this concluded Visit before editing"};dispatch.updateOutboxVisit(value.copy(serviceDate=date,appointmentLocalTime=time,appointmentZoneId=zone,modifiedAtEpochMillis=modifiedAfter(value)))}
    private suspend fun compose(o:DispatchOutboxVisitEntity,g:Int):DispatchVisit{
        val site=dao.site(o.siteId)?:error("Site missing")
        val customer=dao.customer(site.customerId)?:error("Customer missing")
        val teamIds=dispatch.outboxVisitTeams(o.dispatchVisitId).map{it.teamId}
        val allTeams=dispatch.teams().associateBy{it.id};val members=dispatch.allTeamMembers();val tech=dispatch.technicians().associateBy{it.technicianId}
        val teams=teamIds.map{id->val t=allTeams[id]?:error("Team missing");val m=members.filter{it.teamId==id};DispatchTeamSnapshot(id,t.name,m.map{it.technicianId}.sorted(),m.filter{it.isLeader}.map{it.technicianId}.sorted())}
        val participants=teams.flatMap{it.memberIds}.distinct().sorted().map{tech[it]?.let{x->DispatchTechnicianSnapshot(x.technicianId,x.displayName,x.designation)}?:error("Technician missing")}
        val items=dispatch.outboxItems(o.dispatchVisitId).map{i->
            val subject=WorkSubjectType.fromCode(i.subjectType)
            val equipment=validateOutboxSubject(site,customer,subject,i.equipmentId,i.equipmentDescription.orEmpty(),i.servicePlanReference,i.dueDateSnapshot)
            val assigned=dispatch.outboxItemAssignees(i.dispatchItemId).map{it.technicianId}.sorted().map{tech[it]?.let{x->DispatchTechnicianSnapshot(x.technicianId,x.displayName,x.designation)}?:error("Technician missing")}
            val snapshot=snapshotForPlanReference(i.servicePlanReference)
            DispatchWork(i.dispatchItemId,subject,equipment?.reference,i.equipmentDescription?.trim()?.takeIf{equipment==null&&!it.isNullOrBlank()},i.taskName,i.servicePlanReference,i.dueDateSnapshot,assigned,snapshot?.snapshotId)
        }
        require(items.isNotEmpty())
        return DispatchVisit(o.dispatchVisitId,g,o.managerReference,o.serviceDate,o.appointmentLocalTime,o.appointmentZoneId,site.reference,o.instructions,teams,participants,teams.flatMap{it.leaderIds}.distinct().sorted(),items,if(o.canceledAtEpochMillis!=null)"CANCELED" else "ACTIVE",o.cancellationReason)
    }
    private suspend fun snapshotForPlanReference(reference:String?):DispatchInspectionSnapshot? {
        val plan = reference?.let { ref -> dao.allPlans().firstOrNull { it.reference == ref } } ?: return null
        val master = plan.reusableTemplateId?.let { dao.reusableTemplate(it) } ?: return null
        val revision = dao.reusableTemplateRevision(master.currentRevisionId) ?: return null
        val draft = DispatchInspectionSnapshot(
            snapshotId = "",
            templateName = revision.nameSnapshot,
            sourceTemplateReference = master.reference,
            sourceRevision = revision.revisionNumber,
            items = dao.reusableTemplateItems(revision.id).map { item -> DispatchInspectionItem(item.position, item.label, item.responseType, item.unit, item.required, item.privateGuidance) },
        )
        return draft.copy(snapshotId = DispatchPackageCodec.contentAddressedSnapshotId(draft))
    }
    private suspend fun inspectionSnapshotsFor(visit:DispatchVisit):List<DispatchInspectionSnapshot> = visit.work.mapNotNull { work ->
        work.inspectionSnapshotId?.let { expected -> snapshotForPlanReference(work.servicePlanReference)?.takeIf { it.snapshotId == expected } }
    }.distinctBy { it.snapshotId }
    suspend fun prepareExport(ids:List<String>,sender:String):DispatchExportPreparation=database.withTransaction{
        require(sender.trim().isNotEmpty());val selected=ids.distinct();require(selected.isNotEmpty()){ "Select at least one Visit" };require(selected.size<=DispatchPackageCodec.MAX_VISITS){"A work package can contain at most ${DispatchPackageCodec.MAX_VISITS} Visits. Reduce the selection."};val now=System.currentTimeMillis();val expected=linkedMapOf<String,Pair<Long,String>>()
        val visits=selected.map{id->val o=dispatch.outboxVisit(id)?:error("Outbox visit missing");require(o.outboxStatus!=DispatchOutboxStatus.CONCLUDED){"Concluded Visits must be reopened before export"};require(!(o.outboxStatus==DispatchOutboxStatus.CANCELED&&o.lastExportedGeneration==null)){"A canceled Draft has no technician export to send"};val candidate=compose(o,o.lastExportedGeneration?:1);val candidateSnapshots=inspectionSnapshotsFor(candidate);val candidateHash=DispatchPackageCodec.materialHash(candidate,candidateSnapshots);val generation=when{o.lastExportedGeneration==null->1;candidateHash==o.lastExportedMaterialHash->o.lastExportedGeneration;else->o.lastExportedGeneration+1};val visit=if(generation==candidate.generation)candidate else compose(o,generation);expected[id]=o.modifiedAtEpochMillis to DispatchPackageCodec.materialHash(visit,inspectionSnapshotsFor(visit));visit}
        val snapshots=visits.flatMap { visit -> inspectionSnapshotsFor(visit) }.distinctBy{it.snapshotId};val value=packageSnapshot(UUID.randomUUID().toString(),Instant.ofEpochMilli(now).toString(),sender.trim(),visits,snapshots);DispatchExportPreparation(value,DispatchPackageCodec.encode(value),expected,packageSourceHash(visits,snapshots))
    }
    suspend fun exportPackage(ids:List<String>,sender:String):DispatchPackage=prepareExport(ids,sender).packageValue
    suspend fun commitPreparedExport(prepared:DispatchExportPreparation)=database.withTransaction{val now=System.currentTimeMillis();prepared.packageValue.visits.forEach{visit->val current=dispatch.outboxVisit(visit.dispatchVisitId)?:error("Outbox visit missing");val expected=prepared.expected[visit.dispatchVisitId]?:error("Prepared Visit missing");val currentVisit=compose(current,visit.generation);require(current.outboxStatus!=DispatchOutboxStatus.CONCLUDED&&current.modifiedAtEpochMillis==expected.first&&DispatchPackageCodec.materialHash(currentVisit,inspectionSnapshotsFor(currentVisit))==expected.second){"Dispatch Visit changed while the work package was being prepared"}};require(packageSourceHash(prepared.packageValue.visits,prepared.packageValue.inspectionSnapshots)==prepared.sourceHash){"Dispatch directory data changed while the work package was being reviewed"};prepared.packageValue.visits.forEach{visit->val current=dispatch.outboxVisit(visit.dispatchVisitId)?:error("Outbox visit missing");val expected=prepared.expected.getValue(visit.dispatchVisitId);dispatch.updateOutboxVisit(current.copy(lastExportedGeneration=visit.generation,lastExportedMaterialHash=expected.second,lastExportedAtEpochMillis=now,lastExportedCancellationAtEpochMillis=if(visit.transportLifecycle=="CANCELED")current.canceledAtEpochMillis else current.lastExportedCancellationAtEpochMillis))}}
    suspend fun createExportFile(ids:List<String>,sender:String,cacheRoot:File):DispatchExportArtifact=createExportFile(prepareExport(ids,sender),cacheRoot)
    suspend fun createExportFile(prepared:DispatchExportPreparation,cacheRoot:File):DispatchExportArtifact{val visits=prepared.packageValue.visits;val first=visits.minOf{it.serviceDate};val last=visits.maxOf{it.serviceDate};val directory=File(cacheRoot,"work-packages").apply{check(isDirectory||mkdirs()){"Cannot create work-package cache"}};val suffix=prepared.packageValue.packageId.take(8);val final=File(directory,"serviceloop-dispatch-${first}_${last}-${visits.size}-visits-$suffix.slwork");val temp=File(directory,".${prepared.packageValue.packageId}.tmp");try{mutationFault.checkpoint("before_export_file_write");temp.outputStream().use{it.write(prepared.bytes);it.fd.sync()};require(temp.isFile&&temp.readBytes().contentEquals(prepared.bytes)){"Work-package file verification failed"};mutationFault.checkpoint("after_export_file_write_before_commit");check(temp.renameTo(final)){"Cannot finalize work-package file"};require(final.isFile&&final.readBytes().contentEquals(prepared.bytes)){"Work-package file verification failed"};commitPreparedExport(prepared);return DispatchExportArtifact(prepared.packageValue,final)}catch(failure:Throwable){temp.delete();final.delete();if(failure is CancellationException)throw failure;throw failure}}
    suspend fun concludeOutboxVisits(ids:List<String>)=database.withTransaction{val selected=ids.distinct();require(selected.isNotEmpty());val current=selected.map{dispatch.outboxVisit(it)?:error("Outbox visit missing")};require(current.all{it.outboxStatus==DispatchOutboxStatus.DISPATCHED}){"Every selected Visit must be Dispatched"};current.forEach{val changedAt=modifiedAfter(it);dispatch.updateOutboxVisit(it.copy(concludedAtEpochMillis=changedAt,modifiedAtEpochMillis=changedAt))}}
    suspend fun cancelOutboxVisits(ids:List<String>,reason:String)=database.withTransaction{val selected=ids.distinct();require(selected.isNotEmpty());val normalized=reason.trim();require(normalized.isNotEmpty()){"Cancellation reason is required"};val current=selected.map{dispatch.outboxVisit(it)?:error("Outbox visit missing")};require(current.all{it.outboxStatus==DispatchOutboxStatus.DRAFT||it.outboxStatus==DispatchOutboxStatus.DISPATCHED}){"Only Draft or Dispatched Visits can be canceled"};current.forEach{val changedAt=modifiedAfter(it);dispatch.updateOutboxVisit(it.copy(canceledAtEpochMillis=changedAt,cancellationReason=normalized,modifiedAtEpochMillis=changedAt,lastExportedCancellationAtEpochMillis=null))}}
    suspend fun reopenOutboxVisits(ids:List<String>)=database.withTransaction{val selected=ids.distinct();require(selected.isNotEmpty());val current=selected.map{dispatch.outboxVisit(it)?:error("Outbox visit missing")};require(current.all{it.outboxStatus==DispatchOutboxStatus.CONCLUDED}){"Every selected Visit must be Concluded"};current.forEach{dispatch.updateOutboxVisit(it.copy(concludedAtEpochMillis=null,modifiedAtEpochMillis=modifiedAfter(it)))}}
    private fun modifiedAfter(value:DispatchOutboxVisitEntity)=maxOf(System.currentTimeMillis(),value.modifiedAtEpochMillis+1)
    private suspend fun packageSnapshot(packageId:String,createdAt:String,sender:String,visits:List<DispatchVisit>,snapshots:List<DispatchInspectionSnapshot>):DispatchPackage{
        val siteRefs=visits.map{it.siteReference}.toSet();val sites=dao.allSites().filter{it.reference in siteRefs};val customers=dao.allCustomers().filter{c->sites.any{it.customerId==c.id}}
        val equipmentRefs=visits.flatMap{it.work}.filter{it.subjectType==WorkSubjectType.EQUIPMENT}.mapNotNull{it.equipmentReference}.toSet();val equipment=dao.allEquipment().filter{it.reference in equipmentRefs}
        return DispatchPackage(packageId,createdAt,sender,customers.map{DispatchCustomer(it.reference,it.name,CustomerType.fromCode(it.customerType))},sites.map{s->DispatchSite(s.reference,customers.single{it.id==s.customerId}.reference,s.name,s.address)},equipment.map{e->DispatchEquipment(e.reference,sites.single{it.id==e.siteId}.reference,e.name,e.technicianIdentifier,e.make,e.model,e.serialNumber)},visits,snapshots)
    }
    private suspend fun packageSourceHash(visits:List<DispatchVisit>,snapshots:List<DispatchInspectionSnapshot>)=sha256(DispatchPackageCodec.encode(packageSnapshot("source-fingerprint",Instant.EPOCH.toString(),"source-fingerprint",visits,snapshots)).toString(Charsets.UTF_8))
    private fun applicable(v:DispatchVisit,id:TechnicianIdentity):List<Pair<DispatchWork,String>>{val leader=id.technicianId in v.leaderTechnicianIds;return v.work.mapNotNull{i->val assigned=i.assignedTechnicians.isEmpty()||i.assignedTechnicians.any{it.technicianId==id.technicianId};when{assigned->i to "ASSIGNED";leader->i to "LEADER_VISIBLE";else->null}}};private fun scheduled(v:DispatchVisit)=v.appointmentLocalTime?.let{LocalDate.parse(v.serviceDate).atTime(LocalTime.parse(it)).atZone(ZoneId.of(v.appointmentZoneId)).toInstant().toEpochMilli()}
    private suspend fun fingerprint(localId:String):String{val v=dao.visit(localId)?:return "missing";val b=dispatch.visitBindingForLocalVisit(localId)?:return "missing";val items=dispatch.itemBindings(b.dispatchVisitId).mapNotNull{x->x.localWorkItemId?.let{dao.workItem(it)}?.let{w->"${x.dispatchItemId}|${w.subjectType}|${w.equipmentReferenceSnapshot}|${w.equipmentDescriptionSnapshot}|${x.servicePlanReferenceSnapshot}|${x.dueDateSnapshot}|${w.templateSnapshotId}|${w.serviceNameSnapshot}"}}.sorted();return sha256(listOf(v.actualServiceDate,v.scheduledAtEpochMillis,v.appointmentZoneId,items.joinToString(";")).joinToString("|"))};private fun incoming(v:DispatchVisit,r:List<Pair<DispatchWork,String>>,snapshots:List<DispatchInspectionSnapshot>)=sha256(listOf(v.serviceDate,scheduled(v),v.appointmentZoneId,r.map{val snapshotId=it.first.inspectionSnapshotId?.let{value->snapshots.single{snapshot->snapshot.snapshotId==value}.let(DispatchPackageCodec::contentAddressedSnapshotId)};"${it.first.dispatchItemId}|${it.first.subjectType.code}|${it.first.equipmentReference}|${it.first.equipmentDescription}|${it.first.servicePlanReference}|${it.first.dueDateSnapshot}|${snapshotId}|${it.first.taskName}"}.sorted().joinToString(";")).joinToString("|"))
    suspend fun preview(value:DispatchPackage):DispatchPreview {
        val id=identity(); val customers=dao.allCustomers(); val sites=dao.allSites(); val equipment=dao.allEquipment()
        val projected=value.visits.associateWith{applicable(it,id)}
        val relevant=projected.filterValues{it.isNotEmpty()}
        val neededSites=relevant.keys.map{it.siteReference}.toSet()
        val neededEquipment=relevant.values.flatten().mapNotNull{it.first.equipmentReference}.toSet()
        val neededCustomers=value.sites.filter{it.reference in neededSites}.map{it.customerReference}.toSet()
        val directory=buildList {
            value.customers.filter{it.reference in neededCustomers}.forEach{x->val exact=customers.find{it.reference==x.reference};add(DispatchPreviewLine(x.reference,when{exact!=null&&normal(exact.name)==normal(x.name)&&CustomerType.fromCode(exact.customerType)==x.customerType->DispatchClassification.EXISTING_UNCHANGED;exact!=null->DispatchClassification.CONFLICT;customers.any{normal(it.name)==normal(x.name)}->DispatchClassification.POSSIBLE_DUPLICATE;else->DispatchClassification.NEW},"Customer · ${x.name}${if(x.customerType==CustomerType.ONE_TIME)" · One-time" else ""}"))}
            value.sites.filter{it.reference in neededSites}.forEach{x->val exact=sites.find{it.reference==x.reference};val parent=exact?.let{s->customers.find{it.id==s.customerId}?.reference};add(DispatchPreviewLine(x.reference,when{exact!=null&&parent==x.customerReference&&normal(exact.name)==normal(x.name)&&normal(exact.address)==normal(x.address)->DispatchClassification.EXISTING_UNCHANGED;exact!=null->DispatchClassification.CONFLICT;sites.any{normal(it.name)==normal(x.name)&&normal(it.address)==normal(x.address)}->DispatchClassification.POSSIBLE_DUPLICATE;else->DispatchClassification.NEW},"Site · ${x.name}"))}
            value.equipment.filter{it.reference in neededEquipment}.forEach{x->val exact=equipment.find{it.reference==x.reference};val parent=exact?.let{e->sites.find{it.id==e.siteId}?.reference};add(DispatchPreviewLine(x.reference,when{exact!=null&&parent==x.siteReference&&normal(exact.name)==normal(x.name)&&normal(exact.serialNumber)==normal(x.serial)->DispatchClassification.EXISTING_UNCHANGED;exact!=null->DispatchClassification.CONFLICT;equipment.any{normal(it.serialNumber)==normal(x.serial)&&normal(x.serial).isNotBlank()}->DispatchClassification.POSSIBLE_DUPLICATE;else->DispatchClassification.NEW},"Equipment · ${x.name}"))}
        }
        val visits=value.visits.map{v->
            val r=projected.getValue(v);val b=dispatch.visitBinding(v.dispatchVisitId);val h=DispatchPackageCodec.materialHash(v,value.inspectionSnapshots);val local=b?.let{dao.visit(it.localVisitId)}
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
            val newItems=r.associateBy{it.first.dispatchItemId};val itemPreviews=(oldItems.keys+newItems.keys).sorted().map{key->val old=oldItems[key];val next=newItems[key];val oldWork=old?.localWorkItemId?.let{dao.workItem(it)};val oldSnapshot=oldWork?.templateSnapshotId;val nextSnapshot=next?.first?.inspectionSnapshotId?.let{snapshotId->value.inspectionSnapshots.find{x->x.snapshotId==snapshotId}?.let(DispatchPackageCodec::contentAddressedSnapshotId)};val subjectChanged=old!=null&&next!=null&&(old.subjectType!=next.first.subjectType.code||old.equipmentReferenceSnapshot!=next.first.equipmentReference||old.equipmentDescriptionSnapshot!=next.first.equipmentDescription);val planChanged=old!=null&&next!=null&&(old.servicePlanReferenceSnapshot!=next.first.servicePlanReference||old.dueDateSnapshot!=next.first.dueDateSnapshot);val change=when{old==null->"ADDED";next==null->"REMOVED";subjectChanged->"SUBJECT_CHANGED";planChanged->"PLAN_CHANGED";oldSnapshot!=nextSnapshot->"INSPECTION_CHANGED";old.taskNameSnapshot!=next.first.taskName->"TASK_CHANGED";old.assignedTechniciansJson!=techJson(next.first.assignedTechnicians)||old.localRole!=next.second->"ASSIGNMENT_CHANGED";else->"UNCHANGED"};DispatchItemPreview(key,next?.first?.taskName?:old!!.taskNameSnapshot,next?.second?:old!!.localRole,change)}
            val changes=buildList{if(b!=null&&local!=null){if(local.actualServiceDate!=v.serviceDate)add(DispatchFieldChange("Service date",local.actualServiceDate,v.serviceDate));if(local.appointmentZoneId!=v.appointmentZoneId||local.scheduledAtEpochMillis!=scheduled(v))add(DispatchFieldChange("Appointment","${local.scheduledAtEpochMillis?:"None"} · ${local.appointmentZoneId?:"No zone"}","${scheduled(v)?:"None"} · ${v.appointmentZoneId}"));if(b.instructionsSnapshot.orEmpty()!=v.instructions.orEmpty())add(DispatchFieldChange("Dispatch instructions",b.instructionsSnapshot.orEmpty().ifBlank{"None"},v.instructions.orEmpty().ifBlank{"None"}));if(b.participantSnapshotJson!=techJson(v.participants))add(DispatchFieldChange("Participants",parseTech(b.participantSnapshotJson).joinToString{it.name},v.participants.joinToString{it.name}));itemPreviews.filter{it.change!="UNCHANGED"}.forEach{add(DispatchFieldChange("Item ${it.dispatchItemId.take(8)}",it.change.replace('_',' ').lowercase(),it.taskName+" · "+it.localRole.replace('_',' ').lowercase()))}}}
            val site=value.sites.single{it.reference==v.siteReference};val dependencies=if(c in setOf(DispatchVisitClassification.ASSIGNMENT_REMOVED,DispatchVisitClassification.CANCELED)) emptySet() else (setOf(v.siteReference,site.customerReference)+r.mapNotNull{it.first.equipmentReference}).toSet()
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
            val neededEquipment=activeVisits.flatMap{projected.getValue(it)}.mapNotNull{it.first.equipmentReference}.toSet()
            val neededCustomers=p.value.sites.filter{it.reference in neededSites}.map{it.customerReference}.toSet()
            val customers=dao.allCustomers().associateBy{it.reference}.toMutableMap();p.value.customers.filter{it.reference in neededCustomers}.forEach{x->if(x.reference !in customers){CustomerEntity(stable("dispatch-customer",x.reference),x.reference,x.name,customerType=x.customerType.code).also{dao.insertCustomers(listOf(it));customers[x.reference]=it}}}
            val sites=dao.allSites().associateBy{it.reference}.toMutableMap();p.value.sites.filter{it.reference in neededSites}.forEach{x->if(x.reference !in sites){val c=customers[x.customerReference]?:error("Missing customer");val hasSite=dao.sitesForCustomer(c.id).isNotEmpty();SiteEntity(stable("dispatch-site",x.reference),c.id,x.reference,x.name,x.address,null,isDefault=!hasSite).also{dao.insertSites(listOf(it));sites[x.reference]=it}}}
            val equipment=dao.allEquipment().associateBy{it.reference}.toMutableMap();p.value.equipment.filter{it.reference in neededEquipment}.forEach{x->if(x.reference !in equipment){val s=sites[x.siteReference]?:error("Missing site");EquipmentEntity(stable("dispatch-equipment",x.reference),s.id,x.reference,x.identifier,x.name,x.make,x.model,x.serial,null).also{dao.insertEquipment(listOf(it));equipment[x.reference]=it}}}
            val now=System.currentTimeMillis(); val snapshots=p.value.inspectionSnapshots.associateBy { it.snapshotId }
            activeVisits.forEach{v->val pv=actionable.getValue(v.dispatchVisitId);val r=projected.getValue(v);when(pv.classification){
                DispatchVisitClassification.NEW_VISIT->{val s=sites[v.siteReference]?:error("Missing site");val c=customers.values.single{it.id==s.customerId};val local=stable("dispatch-visit",v.dispatchVisitId);dao.insertVisits(listOf(WorkingVisitEntity(local,"D-${v.dispatchVisitId.take(12)}",c.id,s.id,v.serviceDate,c.name,s.name,s.address,"BOOKED",now,c.reference,s.reference,scheduledAtEpochMillis=scheduled(v),appointmentZoneId=v.appointmentZoneId)));dispatch.insertVisitBinding(DispatchVisitBindingEntity(v.dispatchVisitId,local,v.generation,p.value.packageId,p.value.senderLabel,v.managerReference,v.instructions,techJson(v.participants),idsJson(v.leaderTechnicianIds),teamsJson(v.teams),DispatchPackageCodec.materialHash(v,p.value.inspectionSnapshots),incoming(v,r,p.value.inspectionSnapshots),now,now));r.forEach{(i,role)->insertItem(v,i,role,local,equipment,snapshots)};created+=local}
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
                        val binding=DispatchVisitBindingEntity(v.dispatchVisitId,local,v.generation,p.value.packageId,p.value.senderLabel,v.managerReference,v.instructions,techJson(v.participants),idsJson(v.leaderTechnicianIds),teamsJson(v.teams),DispatchPackageCodec.materialHash(v,p.value.inspectionSnapshots),incoming(v,r,p.value.inspectionSnapshots),now,now)
                        dispatch.insertVisitBinding(binding)
                        r.forEach{(i,role)->insertItem(v,i,role,local,equipment,snapshots,i.equipmentReference?.let{reference->p.value.equipment.single{it.reference==reference}})}
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
    private suspend fun ensureImportedSnapshot(snapshot:DispatchInspectionSnapshot,now:Long):String {
        val localId=DispatchPackageCodec.contentAddressedSnapshotId(snapshot)
        dao.templateSnapshot(localId)?.let { existing ->
            require(existing.sourceTemplateId==null&&existing.templateName==snapshot.templateName&&existing.revision==snapshot.sourceRevision) { "Imported inspection snapshot conflicts with local history" }
            return localId
        }
        dao.insertTemplateSnapshots(listOf(TemplateSnapshotEntity(localId,null,snapshot.templateName,snapshot.sourceRevision?:1,now)))
        dao.insertChecklistItems(snapshot.items.map { item -> ChecklistItemSnapshotEntity(stable("snapshot-item",localId,item.position.toString(),item.label,item.responseType),localId,item.position,item.label,item.responseType,item.unit,item.required,item.privateGuidance) })
        return localId
    }
    private suspend fun insertItem(v:DispatchVisit,i:DispatchWork,role:String,local:String,equipment:Map<String,EquipmentEntity>,snapshots:Map<String,DispatchInspectionSnapshot>,equipmentSnapshot:DispatchEquipment?=null){
        val e=i.equipmentReference?.let{equipment[it]?:error("Equipment missing")};val work=stable("dispatch-work",v.dispatchVisitId,i.dispatchItemId);val localSnapshot=i.inspectionSnapshotId?.let{snapshots[it]?.let{snapshot->ensureImportedSnapshot(snapshot,System.currentTimeMillis())}}
        val item=WorkItemEntity(work,local,e?.id,null,null,localSnapshot,equipmentSnapshot?.name?:e?.name,equipmentSnapshot?.reference?:e?.reference,i.taskName,i.servicePlanReference,i.dueDateSnapshot,null,null,false,null,null,equipmentIdentifierSnapshot=if(equipmentSnapshot!=null)equipmentSnapshot.identifier else e?.technicianIdentifier,equipmentMakeSnapshot=if(equipmentSnapshot!=null)equipmentSnapshot.make else e?.make,equipmentModelSnapshot=if(equipmentSnapshot!=null)equipmentSnapshot.model else e?.model,equipmentSerialSnapshot=if(equipmentSnapshot!=null)equipmentSnapshot.serial else e?.serialNumber,subjectType=i.subjectType.code,equipmentDescriptionSnapshot=i.equipmentDescription?.trim()?.takeIf{it.isNotBlank()})
        val site=dao.site((dao.visit(local)?:error("Visit missing")).siteId)?:error("Site missing");val customer=dao.customer(site.customerId)?:error("Customer missing");WorkSubjectValidator.validateWorkItem(item,CustomerType.fromCode(customer.customerType));dao.insertWorkItems(listOf(item));dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity(work,"")));dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity(work,"")));dispatch.insertItemBindings(listOf(DispatchItemBindingEntity(v.dispatchVisitId,i.dispatchItemId,work,i.equipmentReference,i.taskName,i.servicePlanReference,i.dueDateSnapshot,techJson(i.assignedTechnicians),if(i.assignedTechnicians.isEmpty())"EVERYONE" else "EXPLICIT",role,if(role=="LEADER_VISIBLE")"LEADER_OBSERVE" else "PENDING",subjectType=i.subjectType.code,equipmentDescriptionSnapshot=i.equipmentDescription?.trim()?.takeIf{it.isNotBlank()})))
    }
    private suspend fun applyUpdate(v:DispatchVisit,p:DispatchPackage,local:String,equipment:Map<String,EquipmentEntity>,now:Long,r:List<Pair<DispatchWork,String>>) {
        check(dispatch.updateBookedDispatchVisit(local,v.serviceDate,scheduled(v),v.appointmentZoneId,now)==1)
        val snapshots=p.inspectionSnapshots.associateBy { it.snapshotId }
        val existing=dispatch.itemBindings(v.dispatchVisitId).associateBy{it.dispatchItemId}
        existing.values.filter{it.dispatchItemId !in r.map{x->x.first.dispatchItemId}.toSet()}.forEach{x->x.localWorkItemId?.let{dispatch.deleteWorkItem(it)};dispatch.deleteItemBinding(v.dispatchVisitId,x.dispatchItemId)}
        r.forEach{(i,role)->
            val old=existing[i.dispatchItemId]
            if(old==null) insertItem(v,i,role,local,equipment,snapshots)
            else {
                val e=i.equipmentReference?.let{equipment[it]?:error("Equipment missing")}
                val localSnapshot=i.inspectionSnapshotId?.let{snapshots[it]?.let{snapshot->ensureImportedSnapshot(snapshot,now)}}
                check(dispatch.updateBookedDispatchWork(old.localWorkItemId!!,i.subjectType.code,e?.id,i.equipmentDescription?.trim()?.takeIf{it.isNotBlank()},localSnapshot,e?.name,e?.reference,e?.technicianIdentifier,e?.make,e?.model,e?.serialNumber,i.taskName,i.servicePlanReference,i.dueDateSnapshot)==1)
                dispatch.updateItemBinding(old.copy(equipmentReferenceSnapshot=i.equipmentReference,taskNameSnapshot=i.taskName,servicePlanReferenceSnapshot=i.servicePlanReference,dueDateSnapshot=i.dueDateSnapshot,assignedTechniciansJson=techJson(i.assignedTechnicians),assignmentMeaning=if(i.assignedTechnicians.isEmpty())"EVERYONE" else "EXPLICIT",localRole=role,documentationDisposition=if(role=="LEADER_VISIBLE")"LEADER_OBSERVE" else "PENDING",deferredToTechnicianId=null,deferredToName=null,subjectType=i.subjectType.code,equipmentDescriptionSnapshot=i.equipmentDescription?.trim()?.takeIf{it.isNotBlank()}))
            }
        }
        val b=dispatch.visitBinding(v.dispatchVisitId)!!
        dispatch.updateVisitBinding(b.copy(appliedGeneration=v.generation,packageId=p.packageId,senderLabel=p.senderLabel,managerReference=v.managerReference,instructionsSnapshot=v.instructions,participantSnapshotJson=techJson(v.participants),leaderIdsJson=idsJson(v.leaderTechnicianIds),teamSnapshotJson=teamsJson(v.teams),appliedMaterialHash=DispatchPackageCodec.materialHash(v,p.inspectionSnapshots),controlledFingerprint=incoming(v,r,p.inspectionSnapshots),updatedAtEpochMillis=now))
    }
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
    suspend fun documentLocally(local:String,itemId:String):String=database.withTransaction{val visit=dao.visit(local)?:error("Visit missing");require(visit.state=="WORKING");val vb=dispatch.visitBindingForLocalVisit(local)?:error("Not dispatched");val item=dispatch.itemBinding(vb.dispatchVisitId,itemId)?:error("Item missing");val work=item.localWorkItemId?:error("Work missing");val plan=dao.allPlans().find{it.reference==item.servicePlanReferenceSnapshot};val eq=dao.workItem(work)?.equipmentId?.let{equipmentId->dao.equipment(equipmentId)};val obligation=plan?.currentObligationId?.let{dao.obligation(it)};val safe=plan!=null&&eq!=null&&plan.equipmentId==eq?.id&&plan.state=="ACTIVE"&&obligation!=null&&obligation.consumedAtEpochMillis==null&&dao.claimForObligation(obligation.id)==null&&(item.dueDateSnapshot==null||item.dueDateSnapshot==obligation.dueDate);if(safe){check(dispatch.linkDispatchWork(work,plan!!.id,obligation!!.id,plan.reference,obligation.dueDate,plan.intervalCount,plan.intervalUnit)==1);dao.insertVisitClaim(VisitClaimEntity(obligation.id,local,System.currentTimeMillis()))};dispatch.updateItemBinding(item.copy(documentationDisposition="DOCUMENT_LOCAL",deferredToTechnicianId=null,deferredToName=null));if(safe)"Linked to current local obligation" else "Documenting as one-off"}
    private suspend fun substantive(workId:String,binding:DispatchVisitBindingEntity):Boolean{val w=dao.workItem(workId)?:return false;val privateNote=dao.privateDraft(workId)?.internalNote.orEmpty();return dao.publicDraft(workId)?.workPerformed?.isNotBlank()==true||(privateNote.isNotBlank()&&privateNote!=binding.instructionsSnapshot.orEmpty())||w.checklistReviewed||w.outcome!=null||w.fulfillsCurrentObligation==true||w.notPerformedReason!=null||w.confirmedNextDueDate!=null||w.nextDueDateCalculated!=null||w.nextDueOverrideReason!=null||dao.workingInputBuffers(workId).isNotEmpty()||dispatch.responseCount(workId)>0||dispatch.partCount(workId)>0||dispatch.attachmentCount(workId)>0}
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
    private suspend fun appendEvent(visit:WorkingVisitEntity,binding:DispatchVisitBindingEntity,type:String,reason:String,item:DispatchItemBindingEntity?,now:Long){val identity=identity();val equipment=item?.localWorkItemId?.let{workId->dao.workItem(workId)}?.let{work->work.equipmentId?.let{equipmentId->dao.equipment(equipmentId)}};dao.insertChangeEntry(ChangeEntryEntity(UUID.randomUUID().toString(),"VISIT",visit.id,type,visit.actualServiceDate,now,reason,"dispatchVisitId=${binding.dispatchVisitId}",listOfNotNull(item?.let{"dispatchItemId=${it.dispatchItemId}"},"localTechnician=${identity.name} (${identity.technicianId})").joinToString(" · "),visit.customerId,visit.siteId,equipment?.id,null,visit.customerNameSnapshot,visit.siteNameSnapshot,equipment?.name))}
    fun parseTech(json:String):List<DispatchTechnicianSnapshot>{val a=JSONArray(json);return(0 until a.length()).map{a.getJSONObject(it).let{o->DispatchTechnicianSnapshot(o.getString("technicianId"),o.getString("name"))}}};fun parseIds(json:String):List<String>{val a=JSONArray(json);return(0 until a.length()).map{a.getString(it)}}
}
internal fun sha256(v:String)=MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString(""){"%02x".format(it)}
