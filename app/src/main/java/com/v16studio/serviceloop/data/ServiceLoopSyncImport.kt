package com.v16studio.serviceloop.data

import androidx.room.withTransaction
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.domain.CustomerType
import com.v16studio.serviceloop.domain.WorkSubjectType
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** Applies a decoded trusted FULL_WORKSPACE package as one Room replacement. */
internal class ServiceLoopSyncImporter(
    private val database: ServiceLoopDatabase,
    private val businessTime: BusinessTime,
    private val writeGate: DraftWriteGate = DraftWriteGate {},
) {
    private val dao get() = database.serviceLoopDao()
    private val dispatch get() = database.dispatchDao()

    suspend fun apply(value: ServiceLoopSyncPackage): SyncImportResult = BusinessFileCoordinator.mutex.withLock {
        require(dao.recoveryMetadata()?.restrictedRecoveryState != true) { "Recovery is restricted; resolve recovery before importing a workspace" }
        ServiceLoopSyncCodec.preview(value).also { validateReferences(value) }
        writeGate.beforeWrite()
        val importedAt = businessTime.instant().toEpochMilli()
        database.withTransaction {
            clearPortableBusinessState()
            insertBusiness(value, importedAt)
            insertRegister(value.register)
            insertTemplates(value.inspections, importedAt)
            insertPlans(value.plans, importedAt)
            insertTeam(value.team, importedAt)
            insertVisits(value, importedAt)
            insertDispatchDrafts(value.visits.dispatchDrafts, value, importedAt)
            insertFollowups(value.followups, importedAt)
            val previous = dao.recoveryMetadata()
            dao.upsertRecoveryMetadata(
                RecoveryMetadataEntity(
                    datasetId = UUID.randomUUID().toString(),
                    firstBusinessWriteAtEpochMillis = importedAt,
                    lastBusinessWriteAtEpochMillis = importedAt,
                    lastBackupAttemptAtEpochMillis = null,
                    lastVerifiedFullBackupAtEpochMillis = null,
                    lastVerifiedSnapshotAtEpochMillis = null,
                    lastVerifiedDestination = null,
                    lastVerifiedSize = null,
                    backupReminderDays = previous?.backupReminderDays ?: 7,
                    restoredFromIncompleteCopy = false,
                    restrictedRecoveryState = false,
                    adoptionToken = null,
                ),
            )
        }
        SyncImportResult(ServiceLoopSyncCodec.preview(value).counts, importedAt, value.business.zoneId)
    }

    private fun validateReferences(value: ServiceLoopSyncPackage) {
        val customers = value.register.customers.associateBy { it.id }
        val sites = value.register.sites.associateBy { it.id }
        val equipment = value.register.equipment.associateBy { it.id }
        val plans = value.plans.plans.associateBy { it.id }
        val templates = value.inspections.templates.associateBy { it.id }
        val technicians = value.team.technicians.map { it.technicianId }.toSet()
        val teams = value.team.teams.associateBy { it.id }
        require(customers.values.all { CustomerType.fromCode(it.customerType) == CustomerType.STANDARD || CustomerType.fromCode(it.customerType) == CustomerType.ONE_TIME })
        require(value.register.sites.all { sitesRow -> customers[sitesRow.customerId] != null })
        require(value.register.equipment.all { equipmentRow -> sites[equipmentRow.siteId] != null })
        require(value.plans.plans.all { plan -> equipment[plan.equipmentId] != null && (plan.templateId == null || templates[plan.templateId] != null) })
        require(value.visits.bookedVisits.map { it.id }.size == value.visits.bookedVisits.map { it.id }.toSet().size)
        require(value.visits.dispatchDrafts.map { it.id }.size == value.visits.dispatchDrafts.map { it.id }.toSet().size)
        val claimedPlans = mutableSetOf<String>()
        value.visits.bookedVisits.forEach { visit ->
            val site = sites[visit.siteId]!!
            require(visit.work.map { it.id }.size == visit.work.map { it.id }.toSet().size)
            require(visit.work.all { work ->
                when (work.kind) {
                    "PLAN" -> {
                        val plan = plans[work.planId] ?: return@all false
                        require(claimedPlans.add(plan.id)) { "A plan obligation cannot be claimed twice" }
                        plan.equipmentId.let { equipment[it]?.siteId == site.id }
                    }
                    "AD_HOC" -> work.equipmentId?.let { equipment[it]?.siteId == site.id } ?: true
                    else -> false
                }
            })
        }
        value.team.teams.forEach { team -> require(team.memberIds.all { it in technicians } && team.leaderIds.all { it in team.memberIds }) }
        value.visits.dispatchDrafts.forEach { draft ->
            require(draft.teamIds.all { teams[it] != null })
            val site = sites[draft.siteId]!!
            require(draft.items.map { it.dispatchItemId }.size == draft.items.map { it.dispatchItemId }.toSet().size)
            draft.items.forEach { item ->
                require(item.assignedTechnicianIds.all { it in technicians })
                when (item.kind) {
                    "PLAN" -> {
                        val plan = plans[item.planId] ?: error("Dispatch plan is missing")
                        require(equipment[plan.equipmentId]?.siteId == site.id)
                    }
                    "AD_HOC" -> require(item.equipmentId?.let { equipment[it]?.siteId == site.id } ?: true)
                    else -> error("Unsupported dispatch work kind")
                }
            }
        }
        value.followups.followUps.forEach { followUp ->
            require(customers[followUp.customerId] != null)
            require(followUp.siteId?.let { sites[it]?.customerId == followUp.customerId } != false)
            require(followUp.equipmentId?.let { equipment[it]?.siteId == followUp.siteId } != false)
        }
        value.followups.contactNotes.forEach { note ->
            require(customers[note.customerId] != null)
            require(note.siteId?.let { sites[it]?.customerId == note.customerId } != false)
            require(note.equipmentId?.let { equipment[it]?.siteId == note.siteId } != false)
        }
    }

    private fun clearPortableBusinessState() {
        val preserved = setOf("technician_identity", "reminder_preferences", "recovery_metadata")
        RecoveryPackage.TABLE_ORDER.asReversed().filterNot { it in preserved }.forEach { table -> database.openHelper.writableDatabase.execSQL("DELETE FROM `$table`") }
    }

    private suspend fun insertBusiness(value: ServiceLoopSyncPackage, importedAt: Long) {
        dao.upsertBusinessProfile(BusinessProfileEntity("primary", value.business.businessName, value.business.technicianName, value.business.phone, value.business.email, value.business.postalAddress, value.business.zoneId, importedAt))
    }

    private suspend fun insertRegister(register: SyncRegister) {
        dao.insertCustomers(register.customers.map { CustomerEntity(it.id, it.reference, it.name, it.contactName, it.phone, it.email, it.privateNote, it.state, it.customerType) })
        dao.insertSites(register.sites.map { SiteEntity(it.id, it.customerId, it.reference, it.name, it.address, it.privateAccessNote, it.contactName, it.phone, it.email, it.isDefault, it.state) })
        dao.insertEquipment(register.equipment.map { EquipmentEntity(it.id, it.siteId, it.reference, it.technicianIdentifier, it.name, it.make, it.model, it.serialNumber, it.privateNote, it.state) })
    }

    private suspend fun insertTemplates(section: SyncInspections, importedAt: Long) {
        section.templates.forEach { template ->
            val revisionId = "${template.id}-revision-${template.revision}"
            dao.insertReusableTemplate(ReusableTemplateEntity(template.id, template.reference, template.name, revisionId, template.state, importedAt))
            dao.insertReusableTemplateRevision(ReusableTemplateRevisionEntity(revisionId, template.id, template.revision, template.name, importedAt))
            dao.insertReusableTemplateItems(template.items.map { item -> ReusableTemplateItemEntity("${template.id}-revision-${template.revision}-item-${item.position}", revisionId, item.position, item.label, item.responseType, item.unit, item.required, item.privateGuidance) })
        }
    }

    private suspend fun insertPlans(section: SyncPlans, importedAt: Long) {
        section.plans.forEach { plan ->
            val obligationId = "${plan.id}-obligation-1"
            dao.insertPlans(listOf(ServicePlanEntity(plan.id, plan.equipmentId, plan.reference, plan.name, plan.intervalCount, plan.intervalUnit, plan.currentDueDate, "ACTIVE", obligationId, null, null, plan.templateId)))
            dao.insertObligations(listOf(ServiceObligationEntity(obligationId, plan.id, 1, plan.currentDueDate, importedAt, null, null)))
        }
    }

    private suspend fun insertTeam(section: SyncTeamDirectory, importedAt: Long) {
        section.technicians.forEach { technician -> dispatch.insertTechnician(DispatchTechnicianEntity(technician.technicianId, technician.displayName, importedAt, importedAt, technician.designation)) }
        section.teams.forEach { team -> dispatch.insertTeam(DispatchTeamEntity(team.id, team.name, importedAt, importedAt)) }
        section.teams.flatMap { team -> team.memberIds.map { member -> DispatchTeamMemberEntity(team.id, member, member in team.leaderIds) } }.forEach { dispatch.insertTeamMember(it) }
    }

    private suspend fun insertVisits(value: ServiceLoopSyncPackage, importedAt: Long) {
        val profile = value.business
        val customers = value.register.customers.associateBy { it.id }
        val sites = value.register.sites.associateBy { it.id }
        val equipment = value.register.equipment.associateBy { it.id }
        val plans = value.plans.plans.associateBy { it.id }
        val templates = value.inspections.templates.associateBy { it.id }
        value.visits.bookedVisits.forEach { visit ->
            val site = sites.getValue(visit.siteId); val customer = customers.getValue(site.customerId)
            val scheduled = visit.appointmentTime?.let { scheduledEpoch(visit.serviceDate, it, visit.zoneId) }
            dao.insertVisits(listOf(WorkingVisitEntity(visit.id, visit.reference, customer.id, site.id, visit.serviceDate, customer.name, site.name, site.address, "BOOKED", importedAt, customer.reference, site.reference, profile.businessName, profile.technicianName, profile.phone, profile.email, profile.postalAddress, profile.zoneId, scheduled, if (scheduled != null) visit.zoneId else profile.zoneId)))
            visit.work.forEach { work ->
                val item = when (work.kind) {
                    "PLAN" -> {
                        val plan = plans.getValue(work.planId!!); val eq = equipment.getValue(plan.equipmentId); val obligationId = "${plan.id}-obligation-1"
                        WorkItemEntity(work.id, visit.id, eq.id, plan.id, obligationId, null, eq.name, eq.reference, plan.name, plan.reference, plan.currentDueDate, plan.intervalCount, plan.intervalUnit, false, null, null, equipmentIdentifierSnapshot = eq.technicianIdentifier, equipmentMakeSnapshot = eq.make, equipmentModelSnapshot = eq.model, equipmentSerialSnapshot = eq.serialNumber)
                    }
                    "AD_HOC" -> {
                        val eq = work.equipmentId?.let(equipment::getValue); val snapshotId = "${work.id}-template-snapshot"; val template = templates.getValue(work.templateId!!); val revisionId = "${template.id}-revision-${template.revision}"
                        dao.insertTemplateSnapshots(listOf(TemplateSnapshotEntity(snapshotId, template.id, template.name, template.revision, importedAt)))
                        dao.insertChecklistItems(template.items.map { i -> ChecklistItemSnapshotEntity("${work.id}-template-snapshot-item-${i.position}", snapshotId, i.position, i.label, i.responseType, i.unit, i.required, i.privateGuidance) })
                        WorkItemEntity(work.id, visit.id, eq?.id, null, null, snapshotId, eq?.name, eq?.reference, work.taskName!!, null, null, null, null, false, null, null, equipmentIdentifierSnapshot = eq?.technicianIdentifier, equipmentMakeSnapshot = eq?.make, equipmentModelSnapshot = eq?.model, equipmentSerialSnapshot = eq?.serialNumber, subjectType = work.subjectType, equipmentDescriptionSnapshot = if (eq == null) work.equipmentDescription else null)
                    }
                    else -> error("Unsupported booked work kind")
                }
                WorkSubjectValidator.validateWorkItem(item, CustomerType.fromCode(customer.customerType), item.servicePlanId?.let { plans.getValue(it).equipmentId })
                dao.insertWorkItems(listOf(item)); dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity(item.id, ""))); dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity(item.id, "")))
                item.capturedObligationId?.let { dao.insertVisitClaim(VisitClaimEntity(it, visit.id, importedAt)) }
            }
        }
    }

    private suspend fun insertDispatchDrafts(drafts: List<SyncDispatchDraft>, value: ServiceLoopSyncPackage, importedAt: Long) {
        val equipment = value.register.equipment.associateBy { it.id }; val plans = value.plans.plans.associateBy { it.id }
        drafts.forEach { draft ->
            dispatch.insertOutboxVisit(DispatchOutboxVisitEntity(draft.id, draft.managerReference, draft.siteId, draft.serviceDate, draft.appointmentTime, draft.zoneId, draft.instructions, null, null, null, importedAt, importedAt, null, null, null, null))
            dispatch.insertOutboxVisitTeams(draft.teamIds.map { DispatchOutboxVisitTeamEntity(draft.id, it) })
            draft.items.forEachIndexed { index, item ->
                val entity = when (item.kind) {
                    "PLAN" -> { val plan=plans.getValue(item.planId!!); DispatchOutboxItemEntity(item.dispatchItemId,draft.id,index+1,plan.equipmentId,plan.name,plan.reference,plan.currentDueDate,WorkSubjectType.EQUIPMENT.code,null,null) }
                    "AD_HOC" -> { val eq=item.equipmentId?.let(equipment::getValue); DispatchOutboxItemEntity(item.dispatchItemId,draft.id,index+1,eq?.id,item.taskName!!,null,null,item.subjectType,if(eq==null)item.equipmentDescription else null,item.templateId) }
                    else -> error("Unsupported dispatch work kind")
                }
                dispatch.insertOutboxItem(entity); if (item.assignedTechnicianIds.isNotEmpty()) dispatch.insertOutboxItemAssignees(item.assignedTechnicianIds.map { DispatchOutboxItemAssigneeEntity(item.dispatchItemId, it) })
            }
        }
    }

    private suspend fun insertFollowups(section: SyncFollowUps, importedAt: Long) {
        dao.insertFollowUps(section.followUps.map { FollowUpEntity(it.id, it.reference, it.type, it.title, it.dueDate, "OPEN", it.customerId, it.siteId, it.equipmentId, it.privatePlanningNote, null, null, importedAt) })
        section.contactNotes.forEach { note -> val occurred = java.time.Instant.parse(note.occurredAt).toEpochMilli(); dao.insertContactNote(ContactNoteEntity(note.id, note.reference, note.customerId, note.siteId, note.equipmentId, note.channel, occurred, note.outcome, note.privateNote, occurred, null, false, null)) }
    }

    private fun scheduledEpoch(date: String, time: String, zone: String): Long { LocalDateTime.of(java.time.LocalDate.parse(date), LocalTime.parse(time)); return LocalDateTime.of(java.time.LocalDate.parse(date), LocalTime.parse(time)).atZone(ZoneId.of(zone)).toInstant().toEpochMilli() }
}
