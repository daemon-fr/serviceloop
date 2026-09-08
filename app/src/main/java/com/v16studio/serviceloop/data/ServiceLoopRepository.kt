package com.v16studio.serviceloop.data

import androidx.room.withTransaction
import com.v16studio.serviceloop.domain.*
import java.time.LocalDate
import java.math.BigDecimal
import java.util.UUID
import java.io.File
import java.math.RoundingMode
import java.security.MessageDigest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

interface ServiceLoopRepository {
    suspend fun home(): HomeSummary
    suspend fun equipment(id: String): EquipmentDetail?
    suspend fun equipmentList(): List<EquipmentSummary>
    suspend fun customerList(): List<CustomerSummary>
    suspend fun siteList(): List<SiteRegisterSummary> = emptyList()
    suspend fun inspection(workItemId: String): InspectionDraft?
    suspend fun completionLines(visitId: String): List<CompletionLine>
    suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?): Long
    suspend fun visits(): List<VisitSummary> = emptyList()
    suspend fun businessProfile(): BusinessProfile? = null
    suspend fun visitReportIdentity(visitId: String): BusinessProfile? = null
    suspend fun refreshVisitReportIdentity(visitId: String): Long = error("Visit report identity unavailable")
    suspend fun saveBusinessProfile(profile: BusinessProfile): Long = error("Business profile unavailable")
    suspend fun savePublicWork(workItemId: String, text: String): Long = error("Work note unavailable")
    suspend fun markChecklistReviewed(workItemId: String): Long = error("Checklist review unavailable")
    suspend fun saveCompletionDraft(workItemId: String, outcome: String?, fulfills: Boolean, reason: String?, nextDue: String?, calculated: Boolean?, overrideReason: String?): Long = error("Completion draft unavailable")
    suspend fun finalizeVisit(visitId: String): FinalizeResult = FinalizeResult.Blocked("Finalization unavailable")
    suspend fun finalRecord(recordId: String): FinalRecordDetail? = null
    suspend fun customer(id: String): CustomerDetail? = null
    suspend fun site(id: String): SiteDetail? = null
    suspend fun dueServices(): List<DueService> = emptyList()
    fun observeDueServices(): Flow<List<DueService>> = flow { emit(dueServices()) }
    suspend fun visitSites(): List<VisitSiteOption> = emptyList()
    suspend fun plan(id: String): PlanDetail? = null
    suspend fun templates(): List<TemplateSummary> = emptyList()
    suspend fun template(id: String): TemplateDetail? = null
    suspend fun visit(id: String): VisitDetail? = null
    suspend fun followUps(): List<FollowUpDetail> = emptyList()
    suspend fun followUp(id: String): FollowUpDetail? = null
    suspend fun parts(workItemId: String): List<PartEntry> = emptyList()
    suspend fun photos(workItemId: String): List<PhotoEntry> = emptyList()
    suspend fun search(query: String): List<SearchTarget> = emptyList()
    suspend fun createCustomer(input: CustomerInput): String = error("Customer editor unavailable")
    suspend fun updateCustomer(id: String, input: CustomerInput): Long = error("Customer editor unavailable")
    suspend fun createSite(customerId: String, input: SiteInput): String = error("Site editor unavailable")
    suspend fun updateSite(id: String, input: SiteInput): Long = error("Site editor unavailable")
    suspend fun createEquipment(siteId: String, input: EquipmentInput): String = error("Equipment editor unavailable")
    suspend fun updateEquipment(id: String, input: EquipmentInput): Long = error("Equipment editor unavailable")
    suspend fun createPlan(equipmentId: String, input: PlanInput): String = error("Plan editor unavailable")
    suspend fun updatePlan(id: String, input: PlanInput): Long = error("Plan editor unavailable")
    suspend fun createTemplate(name: String, items: List<TemplateItemDraft>): String = error("Template editor unavailable")
    suspend fun reviseTemplate(id: String, name: String, items: List<TemplateItemDraft>): Long = error("Template editor unavailable")
    suspend fun createVisit(planIds: List<String>, state: String, serviceDate: String, scheduledAtEpochMillis: Long? = null): String = error("Visit setup unavailable")
    suspend fun createVisitForSite(siteId: String, planIds: List<String>, oneOffEquipmentId: String?, oneOffName: String?, state: String, serviceDate: String, scheduledAtEpochMillis: Long? = null): String = error("Visit setup unavailable")
    suspend fun addOneOffWork(visitId: String, equipmentId: String, name: String): String = error("One-off work unavailable")
    suspend fun startVisit(id: String): Long = error("Visit unavailable")
    suspend fun rescheduleVisit(id: String, serviceDate: String, scheduledAtEpochMillis: Long?, reason: String): Long = error("Visit unavailable")
    suspend fun cancelVisit(id: String, reason: String): Long = error("Visit unavailable")
    suspend fun restoreVisit(id: String, serviceDate: String): Long = error("Visit unavailable")
    suspend fun addPart(workItemId: String, description: String, quantity: String, unit: String): String = error("Part entry unavailable")
    suspend fun savePhoto(workItemId: String, bytes: ByteArray, displayName: String?, mimeType: String, includeInReport: Boolean, caption: String?): String = error("Photo intake unavailable")
    suspend fun createContactNote(input: ContactNoteInput): String = error("Contact note unavailable")
    suspend fun markContactNoteEnteredInError(id: String, reason: String): Long = error("Contact note unavailable")
    suspend fun createFollowUp(input: FollowUpInput): String = error("Follow-up unavailable")
    suspend fun updateFollowUp(id: String, title: String, dueDate: String, privateNote: String, reason: String): Long = error("Follow-up unavailable")
    suspend fun createCorrectiveFollowUp(workItemId: String, title: String, dueDate: String, privateNote: String): String = error("Corrective follow-up unavailable")
    suspend fun changeFollowUpState(id: String, state: String, reason: String, newDueDate: String? = null): Long = error("Follow-up unavailable")
}

fun interface DraftWriteGate { suspend fun beforeWrite() }
fun interface FinalizationWriteGate { suspend fun beforeCommit() }

class RoomServiceLoopRepository(
    private val database: ServiceLoopDatabase,
    private val businessTime: BusinessTime,
    private val writeGate: DraftWriteGate = DraftWriteGate {},
    private val finalizationWriteGate: FinalizationWriteGate = FinalizationWriteGate {},
    private val attachmentRoot: File? = null,
) : ServiceLoopRepository {
    private val dao = database.serviceLoopDao()

    override suspend fun home(): HomeSummary {
        val today = businessTime.today(); val visit = dao.latestWorkingVisit(); val booked = dao.nextBookedVisit(); val followUp = dao.firstDueFollowUp(today.toString())
        return HomeSummary(visit?.id, visit?.reference, visit?.siteNameSnapshot, visit?.modifiedAtEpochMillis, visit?.id?.let { dao.firstWorkItemId(it) }, booked?.reference, booked?.actualServiceDate, dao.dueFollowUpCount(today.toString()), followUp?.reference, followUp?.title, dao.overdueCount(today.toString()), dao.dueSoonCount(today.toString(), today.plusDays(14).toString()), dao.workingVisitCount(), dao.bookedVisitCount())
    }

    override suspend fun visits() = dao.visits().map { VisitSummary(it.id, it.reference, it.siteName, it.actualServiceDate, it.state, it.finalRecordId, it.resumeWorkItemId) }
    override suspend fun equipmentList() = dao.equipmentList().map { EquipmentSummary(it.id, it.name, it.reference, it.technicianIdentifier, it.siteName, it.customerName, it.nearestDueDate) }
    override suspend fun customerList() = dao.customerList().map { CustomerSummary(it.id, it.name, it.reference, it.siteCount, it.equipmentCount) }
    override suspend fun siteList() = dao.activeVisitSites().map { site -> SiteRegisterSummary(site.id, site.reference, site.name, site.customerName, site.address.orEmpty(), dao.equipmentForSite(site.id).size) }

    override suspend fun customer(id: String): CustomerDetail? {
        val customer = dao.customer(id) ?: return null
        val siteEntities = dao.sitesForCustomer(id)
        val sites = siteEntities.map { site -> SiteSummary(site.id, site.reference, site.name, site.address.orEmpty(), dao.equipmentForSite(site.id).size, site.isDefault) }
        val equipment = siteEntities.flatMap { site -> dao.equipmentForSite(site.id).map { item -> EquipmentSummary(item.id, item.name, item.reference, item.technicianIdentifier, site.name, customer.name, dao.plansForEquipment(item.id).filter { it.state == "ACTIVE" }.minOfOrNull { it.currentDueDate }) } }
        val followUps = dao.followUpsForCustomer(id).filter { it.state == "OPEN" }.map { followUpDetail(it) }
        val contacts = dao.contactNotesForCustomer(id).take(5).map { ContactNoteDetail(it.id, it.reference, it.channel, it.occurredAtEpochMillis, it.outcome, it.privateNote.orEmpty(), it.enteredInError, it.errorReason) }
        return CustomerDetail(customer.id, customer.reference, customer.name, customer.contactName.orEmpty(), customer.phone.orEmpty(), customer.email.orEmpty(), customer.privateNote.orEmpty(), sites, equipment, followUps, contacts)
    }

    override suspend fun site(id: String): SiteDetail? {
        val site = dao.site(id) ?: return null
        val customer = dao.customer(site.customerId) ?: return null
        val equipment = dao.equipmentForSite(id).map { item ->
            EquipmentSummary(item.id, item.name, item.reference, item.technicianIdentifier, site.name, customer.name, dao.plansForEquipment(item.id).filter { it.state == "ACTIVE" }.minOfOrNull { it.currentDueDate })
        }
        return SiteDetail(site.id, customer.id, customer.name, site.reference, site.name, site.address.orEmpty(), site.contactName.orEmpty(), site.phone.orEmpty(), site.email.orEmpty(), site.privateAccessNotes.orEmpty(), site.isDefault, equipment)
    }

    override suspend fun dueServices(): List<DueService> {
        return mapDueServices(dao.dueServices())
    }

    override fun observeDueServices(): Flow<List<DueService>> =
        dao.observeDueServices().map(::mapDueServices)

    private fun mapDueServices(rows: List<DueServiceRow>): List<DueService> {
        val today = businessTime.today()
        return rows.map { row ->
            val due = LocalDate.parse(row.dueDate)
            val bucket = when { due.isBefore(today) -> DueBucket.OVERDUE; due == today -> DueBucket.TODAY; !due.isAfter(today.plusDays(14)) -> DueBucket.DUE_SOON; else -> DueBucket.UPCOMING }
            DueService(row.planId, row.planReference, row.planName, row.dueDate, row.obligationId, row.equipmentId, row.equipmentReference, row.equipmentName, row.siteId, row.siteName, row.customerId, row.customerName, row.claimedVisitId, bucket)
        }
    }

    override suspend fun visitSites(): List<VisitSiteOption> = dao.activeVisitSites().map { site ->
        VisitSiteOption(
            site.id,
            site.reference,
            site.name,
            site.customerName,
            dao.equipmentForSite(site.id).filter { it.state == "ACTIVE" }.map { item ->
                EquipmentSummary(item.id, item.name, item.reference, item.technicianIdentifier, site.name, site.customerName, dao.plansForEquipment(item.id).filter { it.state == "ACTIVE" }.minOfOrNull { it.currentDueDate })
            },
        )
    }

    override suspend fun plan(id: String): PlanDetail? {
        val plan = dao.plan(id) ?: return null; val equipment = dao.equipment(plan.equipmentId) ?: return null
        return PlanDetail(plan.id, equipment.id, equipment.name, plan.reference, plan.name, plan.intervalCount, plan.intervalUnit, plan.currentDueDate, plan.state, plan.reusableTemplateId)
    }

    override suspend fun templates(): List<TemplateSummary> = dao.reusableTemplates().map { template ->
        val revision = dao.reusableTemplateRevision(template.currentRevisionId)
        TemplateSummary(template.id, template.reference, template.name, revision?.revisionNumber ?: 0, revision?.let { dao.reusableTemplateItems(it.id).size } ?: 0, template.state)
    }

    override suspend fun template(id: String): TemplateDetail? {
        val template = dao.reusableTemplate(id) ?: return null
        val revision = dao.reusableTemplateRevision(template.currentRevisionId) ?: return null
        return TemplateDetail(template.id, template.reference, template.name, revision.revisionNumber, template.state, dao.reusableTemplateItems(revision.id).map { TemplateItemDraft(it.label, it.responseType, it.unit.orEmpty(), it.required, it.privateGuidance.orEmpty()) })
    }

    override suspend fun visit(id: String): VisitDetail? {
        val visit = dao.visit(id) ?: return null
        return VisitDetail(visit.id, visit.reference, visit.state, visit.customerId, visit.customerNameSnapshot, visit.siteId, visit.siteNameSnapshot, visit.siteAddressSnapshot.orEmpty(), visit.actualServiceDate, visit.scheduledAtEpochMillis, visit.appointmentZoneId, dao.visitWorkItems(id).map { VisitLine(it.id, it.equipmentNameSnapshot, it.equipmentReferenceSnapshot, it.serviceNameSnapshot, it.dueDateSnapshot, it.outcome) }, visit.cancellationReason)
    }

    override suspend fun followUps() = dao.followUps().map { followUpDetail(it) }
    override suspend fun followUp(id: String) = dao.followUp(id)?.let { followUpDetail(it) }
    override suspend fun parts(workItemId: String) = dao.parts(workItemId).map { PartEntry(it.id, it.description, it.quantity, it.unit) }
    override suspend fun photos(workItemId: String) = dao.workItemAttachments(workItemId).map { PhotoEntry(it.id, it.storedRelativePath, it.mimeType, it.byteSize, it.includedInCustomerReport, it.caption) }
    override suspend fun search(query: String): List<SearchTarget> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return emptyList()
        return dao.search("%${normalized.replace("%", "\\%").replace("_", "\\_")}%").map { SearchTarget(it.type, it.id, it.reference, it.title, it.subtitle) }
    }

    override suspend fun createCustomer(input: CustomerInput): String {
        validateCustomer(input); writeGate.beforeWrite(); val id = UUID.randomUUID().toString()
        dao.insertCustomers(listOf(CustomerEntity(id, reference("CU", dao.customerCount() + 1), input.name.trim(), clean(input.contactName), clean(input.phone), clean(input.email), clean(input.privateNote))))
        return id
    }

    override suspend fun updateCustomer(id: String, input: CustomerInput): Long {
        validateCustomer(input); val old = dao.customer(id) ?: error("Customer no longer exists")
        val value = old.copy(name = input.name.trim(), contactName = clean(input.contactName), phone = clean(input.phone), email = clean(input.email), privateNote = clean(input.privateNote))
        if (value == old) return businessTime.instant().toEpochMilli()
        writeGate.beforeWrite(); dao.updateCustomer(value); return businessTime.instant().toEpochMilli()
    }

    override suspend fun createSite(customerId: String, input: SiteInput): String {
        validateSite(input); val customer = dao.customer(customerId) ?: error("Customer no longer exists"); require(customer.state == "ACTIVE")
        writeGate.beforeWrite(); val id = UUID.randomUUID().toString(); val existing = dao.sitesForCustomer(customerId)
        val value = SiteEntity(id, customerId, reference("ST", dao.siteCount() + 1), input.name.trim(), clean(input.address), clean(input.privateAccessNote), clean(input.contactName), clean(input.phone), clean(input.email), input.isDefault || existing.isEmpty())
        database.withTransaction { if (value.isDefault) existing.filter { it.isDefault }.forEach { dao.updateSite(it.copy(isDefault = false)) }; dao.insertSites(listOf(value)) }
        return id
    }

    override suspend fun updateSite(id: String, input: SiteInput): Long {
        validateSite(input); val old = dao.site(id) ?: error("Site no longer exists")
        val value = old.copy(name = input.name.trim(), address = clean(input.address), contactName = clean(input.contactName), phone = clean(input.phone), email = clean(input.email), privateAccessNotes = clean(input.privateAccessNote), isDefault = input.isDefault)
        if (value == old) return businessTime.instant().toEpochMilli(); writeGate.beforeWrite()
        database.withTransaction { if (value.isDefault) dao.sitesForCustomer(old.customerId).filter { it.id != id && it.isDefault }.forEach { dao.updateSite(it.copy(isDefault = false)) }; dao.updateSite(value) }
        return businessTime.instant().toEpochMilli()
    }

    override suspend fun createEquipment(siteId: String, input: EquipmentInput): String {
        validateEquipment(input); val site = dao.site(siteId) ?: error("Site no longer exists"); require(site.state == "ACTIVE")
        writeGate.beforeWrite(); val id = UUID.randomUUID().toString(); dao.insertEquipment(listOf(EquipmentEntity(id, siteId, reference("EQ", dao.equipmentCount() + 1), clean(input.technicianIdentifier), input.name.trim(), clean(input.make), clean(input.model), clean(input.serialNumber), clean(input.privateNote))))
        return id
    }

    override suspend fun updateEquipment(id: String, input: EquipmentInput): Long {
        validateEquipment(input); val old = dao.equipment(id) ?: error("Equipment no longer exists")
        val value = old.copy(name = input.name.trim(), technicianIdentifier = clean(input.technicianIdentifier), make = clean(input.make), model = clean(input.model), serialNumber = clean(input.serialNumber), privateNotes = clean(input.privateNote))
        if (value == old) return businessTime.instant().toEpochMilli(); writeGate.beforeWrite(); dao.updateEquipment(value); return businessTime.instant().toEpochMilli()
    }

    override suspend fun createPlan(equipmentId: String, input: PlanInput): String {
        validatePlan(input); val equipment = dao.equipment(equipmentId) ?: error("Equipment no longer exists"); require(equipment.state == "ACTIVE")
        input.reusableTemplateId?.let { require(dao.reusableTemplate(it)?.state == "ACTIVE") { "Template is unavailable" } }
        writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); val id = UUID.randomUUID().toString(); val obligationId = UUID.randomUUID().toString()
        database.withTransaction { dao.insertPlans(listOf(ServicePlanEntity(id, equipmentId, reference("P", dao.planCount() + 1), input.name.trim(), input.intervalCount, input.intervalUnit, input.dueDate, "ACTIVE", obligationId, reusableTemplateId = input.reusableTemplateId))); dao.insertObligations(listOf(ServiceObligationEntity(obligationId, id, 1, input.dueDate, now))) }
        return id
    }

    override suspend fun updatePlan(id: String, input: PlanInput): Long {
        validatePlan(input); val old = dao.plan(id) ?: error("Plan no longer exists"); val obligation = old.currentObligationId?.let { dao.obligation(it) } ?: error("Current obligation missing")
        input.reusableTemplateId?.let { require(dao.reusableTemplate(it)?.state == "ACTIVE") { "Template is unavailable" } }
        if (input.dueDate != old.currentDueDate) require(input.dueDateChangeReason.trim().isNotEmpty()) { "Explain why the due date changed" }
        require(obligation.consumedAtEpochMillis == null); val value = old.copy(name = input.name.trim(), intervalCount = input.intervalCount, intervalUnit = input.intervalUnit, currentDueDate = input.dueDate, reusableTemplateId = input.reusableTemplateId)
        if (value == old) return businessTime.instant().toEpochMilli(); writeGate.beforeWrite(); val now=businessTime.instant().toEpochMilli(); database.withTransaction { check(dao.updateCurrentObligationDueDate(obligation.id, input.dueDate) == 1); dao.updatePlan(value); if(input.dueDate!=old.currentDueDate) dao.insertPlanScheduleChange(PlanScheduleChangeEntity(UUID.randomUUID().toString(),id,old.currentDueDate,input.dueDate,input.dueDateChangeReason.trim(),now)) }; return now
    }

    override suspend fun createTemplate(name: String, items: List<TemplateItemDraft>): String {
        validateTemplate(name, items); writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); val id = UUID.randomUUID().toString(); val revisionId = UUID.randomUUID().toString()
        database.withTransaction { dao.insertReusableTemplate(ReusableTemplateEntity(id, reference("IT", dao.reusableTemplateCount() + 1), name.trim(), revisionId, modifiedAtEpochMillis = now)); dao.insertReusableTemplateRevision(ReusableTemplateRevisionEntity(revisionId, id, 1, name.trim(), now)); dao.insertReusableTemplateItems(items.mapIndexed { index, item -> reusableItem(revisionId, index, item) }) }
        return id
    }

    override suspend fun reviseTemplate(id: String, name: String, items: List<TemplateItemDraft>): Long {
        validateTemplate(name, items); val template = dao.reusableTemplate(id) ?: error("Template no longer exists"); val revisionNumber = (dao.reusableTemplateRevisions(id).maxOfOrNull { it.revisionNumber } ?: 0) + 1
        writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); val revisionId = UUID.randomUUID().toString(); database.withTransaction { dao.insertReusableTemplateRevision(ReusableTemplateRevisionEntity(revisionId, id, revisionNumber, name.trim(), now)); dao.insertReusableTemplateItems(items.mapIndexed { index, item -> reusableItem(revisionId, index, item) }); check(dao.publishTemplateRevision(template.id, name.trim(), revisionId, now) == 1) }; return now
    }

    override suspend fun createVisit(planIds: List<String>, state: String, serviceDate: String, scheduledAtEpochMillis: Long?): String = database.withTransaction {
        require(planIds.isNotEmpty()) { "Select at least one due service" }; require(state in setOf("BOOKED", "WORKING", "HISTORICAL")); LocalDate.parse(serviceDate)
        val historical = state == "HISTORICAL"
        val plans = planIds.distinct().map { dao.plan(it) ?: error("Service plan no longer exists") }; val equipment = plans.map { dao.equipment(it.equipmentId) ?: error("Equipment no longer exists") }; val sites = equipment.map { it.siteId }.distinct(); require(sites.size == 1) { "One visit can contain work at one site only" }
        val site = dao.site(sites.single()) ?: error("Site no longer exists"); val customer = dao.customer(site.customerId) ?: error("Customer no longer exists"); val obligations = plans.map { plan -> dao.obligation(plan.currentObligationId ?: error("Plan has no current obligation")) ?: error("Current obligation missing") }
        require(plans.all { it.state == "ACTIVE" } && obligations.all { it.consumedAtEpochMillis == null }) { "A selected obligation is no longer current" }
        writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); val id = UUID.randomUUID().toString(); val profile = dao.businessProfile(); val zone = businessTime.zoneId.id
        dao.insertVisits(listOf(WorkingVisitEntity(id, reference("V", dao.visitCount() + 1), customer.id, site.id, serviceDate, customer.name, site.name, site.address, if (historical) "WORKING" else state, now, customer.reference, site.reference, profile?.businessName, profile?.technicianName, profile?.phone, profile?.email, profile?.postalAddress, profile?.zoneId, scheduledAtEpochMillis, if (scheduledAtEpochMillis != null) zone else profile?.zoneId)))
        plans.forEachIndexed { index, plan ->
            val eq = equipment[index]; val obligation = obligations[index]
            if (!historical) dao.insertVisitClaim(VisitClaimEntity(obligation.id, id, now))
            val snapshotId = if (state == "BOOKED") null else captureTemplateSnapshot(plan.reusableTemplateId, id, plan.id, now)
            val workId = UUID.randomUUID().toString(); dao.insertWorkItems(listOf(WorkItemEntity(workId, id, eq.id, plan.id, if (historical) null else obligation.id, snapshotId, eq.name, eq.reference, plan.name, plan.reference, obligation.dueDate, plan.intervalCount, plan.intervalUnit, false, null, false, equipmentIdentifierSnapshot = eq.technicianIdentifier, equipmentMakeSnapshot = eq.make, equipmentModelSnapshot = eq.model, equipmentSerialSnapshot = eq.serialNumber))); dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity(workId, ""))); dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity(workId, "")))
        }
        return@withTransaction id
    }

    override suspend fun createVisitForSite(siteId: String, planIds: List<String>, oneOffEquipmentId: String?, oneOffName: String?, state: String, serviceDate: String, scheduledAtEpochMillis: Long?): String = database.withTransaction {
        require(planIds.isNotEmpty() || !oneOffName.isNullOrBlank()) { "Select planned work or add one-off work" }
        if (planIds.isNotEmpty()) {
            val selectedSiteIds = planIds.distinct().map { planId -> dao.plan(planId)?.let { dao.equipment(it.equipmentId)?.siteId } ?: error("Service plan no longer exists") }.distinct()
            require(selectedSiteIds == listOf(siteId)) { "All planned work must belong to the selected site" }
        }
        val visitId = if (planIds.isNotEmpty()) {
            createVisit(planIds, state, serviceDate, scheduledAtEpochMillis)
        } else {
            require(state in setOf("BOOKED", "WORKING", "HISTORICAL")); LocalDate.parse(serviceDate)
            val site = dao.site(siteId) ?: error("Site no longer exists"); val customer = dao.customer(site.customerId) ?: error("Customer no longer exists")
            writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); val id = UUID.randomUUID().toString(); val profile = dao.businessProfile()
            dao.insertVisits(listOf(WorkingVisitEntity(id, reference("V", dao.visitCount() + 1), customer.id, site.id, serviceDate, customer.name, site.name, site.address, if(state=="HISTORICAL") "WORKING" else state, now, customer.reference, site.reference, profile?.businessName, profile?.technicianName, profile?.phone, profile?.email, profile?.postalAddress, profile?.zoneId, scheduledAtEpochMillis, scheduledAtEpochMillis?.let { businessTime.zoneId.id } ?: profile?.zoneId)))
            id
        }
        if (!oneOffName.isNullOrBlank()) addOneOffWork(visitId, oneOffEquipmentId ?: error("Choose equipment for one-off work"), oneOffName)
        return@withTransaction visitId
    }

    override suspend fun addOneOffWork(visitId: String, equipmentId: String, name: String): String {
        require(name.trim().isNotEmpty() && name.length <= 200)
        writeGate.beforeWrite(); val id = UUID.randomUUID().toString(); val now = businessTime.instant().toEpochMilli(); database.withTransaction { val visit=dao.visit(visitId)?:error("Visit no longer exists"); require(visit.state in setOf("BOOKED","WORKING")){"Visit no longer accepts work"}; val eq=dao.equipment(equipmentId)?:error("Equipment no longer exists"); require(eq.siteId==visit.siteId){"Equipment must belong to this visit site"}; dao.insertWorkItems(listOf(WorkItemEntity(id, visitId, eq.id, null, null, null, eq.name, eq.reference, name.trim(), null, null, null, null, false, null, false, equipmentIdentifierSnapshot = eq.technicianIdentifier, equipmentMakeSnapshot = eq.make, equipmentModelSnapshot = eq.model, equipmentSerialSnapshot = eq.serialNumber))); dao.insertPublicDrafts(listOf(WorkItemPublicDraftEntity(id, ""))); dao.insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity(id, ""))); dao.touchVisit(visitId, now) }; return id
    }

    override suspend fun startVisit(id: String): Long { writeGate.beforeWrite(); return database.withTransaction {
        val visit = dao.visit(id) ?: error("Visit no longer exists"); require(visit.state == "BOOKED") { "Only a booked visit can be started" }
        val workItems=dao.visitWorkItems(id); require(workItems.isNotEmpty()){ "Add at least one service line before starting" }
        workItems.filter { it.servicePlanId != null }.forEach { item ->
            val plan = dao.plan(item.servicePlanId!!) ?: error("Plan missing")
            val captured = item.capturedObligationId ?: error("Booked recurring work has no captured obligation")
            val obligation = dao.obligation(captured)
            require(
                plan.currentObligationId == captured &&
                    obligation?.planId == plan.id &&
                    obligation.consumedAtEpochMillis == null &&
                    dao.visitOwnsClaim(id, captured) == 1
            ) { "${item.equipmentReferenceSnapshot} · ${item.serviceNameSnapshot} is stale — rebook this work" }
        }
        val site=dao.site(visit.siteId)?:error("Site missing"); val customer=dao.customer(site.customerId)?:error("Customer missing"); val profile=dao.businessProfile(); val now = businessTime.instant().toEpochMilli()
        workItems.forEach { item -> val eq=dao.equipment(item.equipmentId)?:error("Equipment missing"); require(eq.siteId==visit.siteId); val plan=item.servicePlanId?.let{dao.plan(it) ?: error("Plan missing")}; val snapshot=plan?.let{captureTemplateSnapshot(it.reusableTemplateId,id,it.id,now)}; check(dao.refreshWorkItemSnapshot(item.id,snapshot,eq.name,eq.reference,eq.technicianIdentifier,eq.make,eq.model,eq.serialNumber,plan?.name ?: item.serviceNameSnapshot,plan?.reference,plan?.currentDueDate,item.intervalCountSnapshot?.let{plan?.intervalCount},item.intervalUnitSnapshot?.let{plan?.intervalUnit})==1) }
        check(dao.startBookedVisit(id,businessTime.today().toString(),customer.name,customer.reference,site.name,site.reference,site.address,profile?.businessName,profile?.technicianName,profile?.phone,profile?.email,profile?.postalAddress,profile?.zoneId,now)==1){"Only a booked visit can be started"}; now
    } }

    override suspend fun rescheduleVisit(id: String, serviceDate: String, scheduledAtEpochMillis: Long?, reason: String): Long {
        LocalDate.parse(serviceDate); require(reason.trim().isNotEmpty()); writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); database.withTransaction { val visit=dao.visit(id)?:error("Visit no longer exists"); require(visit.state=="BOOKED"){"Only a booked visit can be rescheduled"}; dao.updateVisit(visit.copy(actualServiceDate = serviceDate, scheduledAtEpochMillis = scheduledAtEpochMillis, scheduleChangeReason = reason.trim(), modifiedAtEpochMillis = now)); dao.insertVisitScheduleEvent(VisitScheduleEventEntity(UUID.randomUUID().toString(),id,"RESCHEDULED",visit.actualServiceDate,serviceDate,visit.scheduledAtEpochMillis,scheduledAtEpochMillis,reason.trim(),now)) }; return now
    }

    override suspend fun cancelVisit(id: String, reason: String): Long {
        require(reason.trim().isNotEmpty()); writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); database.withTransaction { val visit=dao.visit(id)?:error("Visit no longer exists"); require(visit.state=="BOOKED"){"Only a booked visit can be cancelled"}; dao.updateVisit(visit.copy(state = "CANCELLED", cancellationReason = reason.trim(), cancelledAtEpochMillis = now, modifiedAtEpochMillis = now)); dao.insertVisitScheduleEvent(VisitScheduleEventEntity(UUID.randomUUID().toString(),id,"CANCELLED",visit.actualServiceDate,null,visit.scheduledAtEpochMillis,null,reason.trim(),now)); dao.releaseVisitClaims(id) }; return now
    }

    override suspend fun restoreVisit(id: String, serviceDate: String): Long {
        LocalDate.parse(serviceDate); require(!LocalDate.parse(serviceDate).isBefore(businessTime.today())) { "Choose today or a future appointment date" }
        writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli()
        database.withTransaction {
            val visit = dao.visit(id) ?: error("Visit no longer exists")
            require(visit.state == "CANCELLED") { "Only a cancelled visit can be restored" }
            val recurring = dao.visitWorkItems(id).filter { it.servicePlanId != null }
            recurring.forEach { item ->
                val plan = dao.plan(item.servicePlanId!!) ?: error("The old booking can no longer be restored because its service obligation changed or is already claimed")
                val obligationId = item.capturedObligationId ?: error("The old booking can no longer be restored because its service obligation changed or is already claimed")
                val obligation = dao.obligation(obligationId)
                require(plan.state == "ACTIVE" && plan.currentObligationId == obligationId && obligation?.planId == plan.id && obligation.consumedAtEpochMillis == null && dao.claimForObligation(obligationId) == null) { "The old booking can no longer be restored because its service obligation changed or is already claimed" }
            }
            recurring.forEach { dao.insertVisitClaim(VisitClaimEntity(it.capturedObligationId!!, id, now)) }
            dao.updateVisit(visit.copy(state = "BOOKED", actualServiceDate = serviceDate, scheduledAtEpochMillis = LocalDate.parse(serviceDate).atStartOfDay(businessTime.zoneId).toInstant().toEpochMilli(), cancellationReason = visit.cancellationReason, modifiedAtEpochMillis = now))
            dao.insertVisitScheduleEvent(VisitScheduleEventEntity(UUID.randomUUID().toString(), id, "RESTORED", visit.actualServiceDate, serviceDate, visit.scheduledAtEpochMillis, LocalDate.parse(serviceDate).atStartOfDay(businessTime.zoneId).toInstant().toEpochMilli(), "Restored booking", now))
        }
        return now
    }

    override suspend fun addPart(workItemId: String, description: String, quantity: String, unit: String): String {
        require(description.trim().isNotEmpty() && description.length <= 200); require(unit.trim().isNotEmpty() && unit.length <= 30); val numeric = runCatching { BigDecimal(quantity.trim()) }.getOrNull(); require(numeric != null && numeric > BigDecimal.ZERO) { "Quantity must be a finite positive number" }
        writeGate.beforeWrite(); val id = UUID.randomUUID().toString(); val now = businessTime.instant().toEpochMilli(); database.withTransaction { val item=workingItem(workItemId); dao.insertPart(PartEntryEntity(id, workItemId, description.trim(), numeric.stripTrailingZeros().toPlainString(), unit.trim(), now)); dao.touchVisit(item.visitId, now) }; return id
    }

    override suspend fun savePhoto(workItemId: String, bytes: ByteArray, displayName: String?, mimeType: String, includeInReport: Boolean, caption: String?): String {
        require(bytes.isNotEmpty()); require(bytes.size <= MAX_PHOTO_SOURCE_BYTES) { "Choose a photo smaller than 30 MB" }; require(mimeType.startsWith("image/")); val root = attachmentRoot ?: error("Attachment storage unavailable")
        val normalized = normalizePhoto(bytes)
        writeGate.beforeWrite(); val id = UUID.randomUUID().toString(); val relative = "attachments/$id/original"; val target = File(root, relative); val temp = File(target.parentFile, "incoming.tmp"); target.parentFile?.mkdirs(); try { temp.writeBytes(normalized.bytes); require(temp.length() == normalized.bytes.size.toLong()); if (!temp.renameTo(target)) { temp.copyTo(target, overwrite = false); temp.delete() }; val hash = MessageDigest.getInstance("SHA-256").digest(normalized.bytes).joinToString("") { "%02x".format(it) }; database.withTransaction { val item=workingItem(workItemId); require(dao.machinePhotoCount(item.visitId,item.equipmentId)<20); require(dao.visitPhotoCount(item.visitId)<100); dao.insertAttachments(listOf(AttachmentEntity(id,"WORK_ITEM",workItemId,relative,hash,displayName,normalized.mimeType,includeInReport,"PRESENT",normalized.bytes.size.toLong(),clean(caption)))); dao.touchVisit(item.visitId,businessTime.instant().toEpochMilli()) }; return id } catch (failure: Throwable) { temp.delete(); target.delete(); throw failure }
    }

    override suspend fun createContactNote(input: ContactNoteInput): String {
        require(input.outcome.trim().isNotEmpty() && input.outcome.length <= 2000); require(input.channel in setOf("CALL", "SMS", "EMAIL", "IN_PERSON", "OTHER")); dao.customer(input.customerId) ?: error("Customer no longer exists"); writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); val id = UUID.randomUUID().toString(); dao.insertContactNote(ContactNoteEntity(id, reference("CN", dao.contactNoteCount() + 1), input.customerId, input.siteId, input.equipmentId, input.channel, now, input.outcome.trim(), clean(input.privateNote), now)); return id
    }

    override suspend fun markContactNoteEnteredInError(id: String, reason: String): Long { require(reason.trim().isNotEmpty()); writeGate.beforeWrite(); val now=businessTime.instant().toEpochMilli(); check(dao.markContactNoteEnteredInError(id,reason.trim(),now)==1){"Contact note is unavailable or already entered in error"}; return now }

    override suspend fun createFollowUp(input: FollowUpInput): String {
        require(input.type in setOf("CONTACT", "CORRECTIVE")); require(input.title.trim().isNotEmpty() && input.title.length <= 200); LocalDate.parse(input.dueDate); dao.customer(input.customerId) ?: error("Customer no longer exists"); writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); val id = UUID.randomUUID().toString(); dao.insertFollowUp(FollowUpEntity(id, reference("FU", dao.followUpCount() + 1), input.type, input.title.trim(), input.dueDate, "OPEN", input.customerId, input.siteId, input.equipmentId, clean(input.privatePlanningNote), input.sourceVisitId, input.sourceWorkItemId, now)); return id
    }

    override suspend fun updateFollowUp(id: String, title: String, dueDate: String, privateNote: String, reason: String): Long {
        require(title.trim().isNotEmpty()&&title.length<=200); LocalDate.parse(dueDate); writeGate.beforeWrite(); val now=businessTime.instant().toEpochMilli(); database.withTransaction { val old=dao.followUp(id)?:error("Follow-up no longer exists"); require(old.state=="OPEN"){"Only an open follow-up can be edited"}; if(dueDate!=old.dueDate) require(reason.trim().isNotEmpty()){ "Explain why the follow-up date changed" }; dao.updateFollowUp(old.copy(title=title.trim(),dueDate=dueDate,privatePlanningNote=clean(privateNote),updatedAtEpochMillis=now)); if(dueDate!=old.dueDate) dao.insertFollowUpEvent(FollowUpEventEntity(UUID.randomUUID().toString(),id,"RESCHEDULED",now,reason.trim(),dueDate)) }; return now
    }

    override suspend fun createCorrectiveFollowUp(workItemId: String, title: String, dueDate: String, privateNote: String): String {
        val item = dao.workItem(workItemId) ?: error("Work item no longer exists"); val visit = dao.visit(item.visitId) ?: error("Visit no longer exists")
        return createFollowUp(FollowUpInput("CORRECTIVE", title, dueDate, visit.customerId, visit.siteId, item.equipmentId, privateNote, visit.id, item.id))
    }

    override suspend fun changeFollowUpState(id: String, state: String, reason: String, newDueDate: String?): Long {
        require(state in setOf("RESOLVED", "CANCELLED", "OPEN")); require(reason.trim().isNotEmpty()); newDueDate?.let(LocalDate::parse)
        writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); database.withTransaction { val old=dao.followUp(id)?:error("Follow-up no longer exists"); if(state=="OPEN") require(old.state!="OPEN"&&newDueDate!=null){"Only a closed follow-up can be reopened"} else require(old.state=="OPEN"){"Only an open follow-up can be closed"}; dao.updateFollowUp(old.copy(state = state, dueDate = newDueDate ?: old.dueDate, updatedAtEpochMillis = now, closedAtEpochMillis = if (state == "OPEN") null else now, closureReason = if (state == "OPEN") null else reason.trim())); dao.insertFollowUpEvent(FollowUpEventEntity(UUID.randomUUID().toString(), id, state, now, reason.trim(), newDueDate)) }; return now
    }

    override suspend fun equipment(id: String): EquipmentDetail? {
        val equipment=dao.equipment(id)?:return null; val site=dao.site(equipment.siteId)?:return null; val customer=dao.customer(site.customerId)?:return null
        return EquipmentDetail(equipment.id,equipment.name,equipment.reference,equipment.technicianIdentifier,listOfNotNull(equipment.make,equipment.model).joinToString(" "),equipment.serialNumber,site.name,customer.name,
            dao.plansForEquipment(id).map { EquipmentPlan(it.id,it.name,it.reference,"Every ${it.intervalCount} ${it.intervalUnit.lowercase()}",it.currentDueDate,it.state,it.currentObligationId,LocalDate.parse(it.currentDueDate).isBefore(businessTime.today())) },dao.workingItemId(id),equipment.make.orEmpty(),equipment.model.orEmpty(),equipment.privateNotes.orEmpty())
    }

    override suspend fun inspection(workItemId: String): InspectionDraft? {
        val row = dao.inspection(workItemId) ?: return null; val items = row.templateSnapshotId?.let { dao.checklistItems(it) }.orEmpty(); val responses = dao.responses(workItemId).associateBy { it.checklistItemSnapshotId }
        return InspectionDraft(row.workItemId, row.visitId, row.visitReference, row.siteNameSnapshot, row.equipmentNameSnapshot, row.equipmentReferenceSnapshot, row.serviceNameSnapshot, row.dueDateSnapshot,
            row.intervalCountSnapshot?.let { "Every $it ${row.intervalUnitSnapshot?.lowercase()}" }, row.templateSnapshotId?.let { dao.templateSnapshot(it)?.revision }, row.workPerformed, row.privateInternalNote, row.checklistReviewed, row.outcome, row.fulfillsCurrentObligation, row.modifiedAtEpochMillis,
            items.map { item -> val response = responses[item.id]; InspectionQuestion(response?.id ?: stableId("response", workItemId, item.id), item.id, item.position, item.label, item.responseType, item.unit, item.required, response?.disposition?.let(ResponseDisposition::valueOf) ?: if (item.responseType == "STATUS") ResponseDisposition.NOT_CHECKED else ResponseDisposition.UNANSWERED, response?.textValue, response?.numberValue, response?.reason) })
    }

    override suspend fun completionLines(visitId: String): List<CompletionLine> {
        val visit = dao.visit(visitId) ?: return emptyList()
        return dao.visitWorkItems(visitId).map { item ->
            val plan = item.servicePlanId?.let { dao.plan(it) }
            val obligation = item.capturedObligationId?.let { dao.obligation(it) }
            val eligibility = when {
                item.servicePlanId == null -> FulfillmentEligibility.NO_CURRENT_OBLIGATION
                item.capturedObligationId == null -> FulfillmentEligibility.HISTORY_ONLY
                plan == null || plan.state != "ACTIVE" -> FulfillmentEligibility.PLAN_INELIGIBLE
                item.outcome != "PERFORMED" -> FulfillmentEligibility.OUTCOME_INELIGIBLE
                item.templateSnapshotId != null && !item.checklistReviewed -> FulfillmentEligibility.CHECKLIST_NOT_REVIEWED
                obligation == null || obligation.planId != plan.id || obligation.consumedAtEpochMillis != null || plan.currentObligationId != item.capturedObligationId -> FulfillmentEligibility.CURRENT_OBLIGATION_CHANGED
                else -> FulfillmentEligibility.ELIGIBLE
            }
            val fulfills = eligibility == FulfillmentEligibility.ELIGIBLE && item.fulfillsCurrentObligation == true
            val calculated = if (fulfills && item.intervalCountSnapshot != null && item.intervalUnitSnapshot != null) RecurrenceCalculator.nextDate(LocalDate.parse(visit.actualServiceDate), item.intervalCountSnapshot, item.intervalUnitSnapshot).toString() else null
            val public = dao.inspection(item.id)?.workPerformed.orEmpty()
            val questions = item.templateSnapshotId?.let { snapshot ->
                val answers = dao.responses(item.id).associateBy { it.checklistItemSnapshotId }
                dao.checklistItems(snapshot).map { it to answers[it.id] }
            }.orEmpty()
            val missingFindings = questions.filter { (_, answer) -> answer?.disposition == "ISSUE_FOUND" && answer.reason.isNullOrBlank() }
            val blockers = buildList {
                if (item.outcome == null) add(CompletionBlocker(CompletionBlockerKind.OUTCOME, "Choose an outcome"))
                if ((item.outcome == "PERFORMED" || item.outcome == "PARTLY_PERFORMED") && public.isBlank()) add(CompletionBlocker(CompletionBlockerKind.WORK_PERFORMED, "Work performed is required"))
                if (item.outcome == "NOT_PERFORMED" && item.notPerformedReason.isNullOrBlank()) add(CompletionBlocker(CompletionBlockerKind.NOT_PERFORMED_REASON, "Reason is required"))
                if (item.outcome == "PERFORMED" && item.templateSnapshotId != null && !item.checklistReviewed) add(CompletionBlocker(CompletionBlockerKind.CHECKLIST_REVIEW, "Checklist needs review"))
                missingFindings.forEach { (question, _) -> add(CompletionBlocker(CompletionBlockerKind.FINDING_DESCRIPTION, "${question.label}: Issue found needs a public description", question.id, question.label)) }
                if (fulfills && item.confirmedNextDueDate == null) add(CompletionBlocker(CompletionBlockerKind.NEXT_DUE, "Confirm the next due date"))
            }
            CompletionLine(item.id, item.equipmentNameSnapshot, item.equipmentReferenceSnapshot, item.serviceNameSnapshot, item.outcome, eligibility, fulfills, item.dueDateSnapshot, calculated, public, item.checklistReviewed, item.notPerformedReason, calculated, item.confirmedNextDueDate.takeIf { fulfills }, item.nextDueDateCalculated.takeIf { fulfills }, item.nextDueOverrideReason.takeIf { fulfills }, blockers)
        }
    }

    override suspend fun businessProfile(): BusinessProfile? = dao.businessProfile()?.let { BusinessProfile(it.businessName, it.technicianName, it.phone.orEmpty(), it.email.orEmpty(), it.postalAddress.orEmpty(), it.zoneId, it.modifiedAtEpochMillis) }

    override suspend fun visitReportIdentity(visitId: String): BusinessProfile? = dao.visit(visitId)?.let { visit ->
        val business = visit.reportBusinessNameSnapshot ?: return@let null
        val technician = visit.reportTechnicianNameSnapshot ?: return@let null
        val zone = visit.reportZoneIdSnapshot ?: return@let null
        BusinessProfile(business, technician, visit.reportPhoneSnapshot.orEmpty(), visit.reportEmailSnapshot.orEmpty(), visit.reportPostalAddressSnapshot.orEmpty(), zone)
    }

    override suspend fun refreshVisitReportIdentity(visitId: String): Long {
        val profile = dao.businessProfile() ?: error("Set Business and report identity first")
        require(profile.businessName.isNotBlank() && profile.technicianName.isNotBlank()) { "Set Business and report identity first" }
        val visit = dao.visit(visitId) ?: error("Visit no longer exists")
        val normalized = listOf(profile.businessName, profile.technicianName, profile.phone, profile.email, profile.postalAddress, profile.zoneId)
        val current = listOf(visit.reportBusinessNameSnapshot, visit.reportTechnicianNameSnapshot, visit.reportPhoneSnapshot, visit.reportEmailSnapshot, visit.reportPostalAddressSnapshot, visit.reportZoneIdSnapshot)
        if (normalized == current) return visit.modifiedAtEpochMillis
        writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli()
        check(dao.updateVisitReportIdentity(visitId, profile.businessName, profile.technicianName, profile.phone, profile.email, profile.postalAddress, profile.zoneId, now) == 1)
        return now
    }

    override suspend fun saveBusinessProfile(profile: BusinessProfile): Long {
        val businessName = profile.businessName.trim(); val technicianName = profile.technicianName.trim(); val phone = profile.phone.trim().ifBlank { null }; val email = profile.email.trim().ifBlank { null }; val address = profile.postalAddress.trim().ifBlank { null }; val zoneId = profile.zoneId.trim()
        require(businessName.isNotBlank() && technicianName.isNotBlank()) { "Business and technician names are required" }; require(zoneId.isNotBlank()) { "Business time zone is required" }
        dao.businessProfile()?.let { existing -> if (existing.businessName == businessName && existing.technicianName == technicianName && existing.phone == phone && existing.email == email && existing.postalAddress == address && existing.zoneId == zoneId) return existing.modifiedAtEpochMillis }
        writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli()
        dao.upsertBusinessProfile(BusinessProfileEntity(businessName = businessName, technicianName = technicianName, phone = phone, email = email, postalAddress = address, zoneId = zoneId, modifiedAtEpochMillis = now)); return now
    }

    override suspend fun savePublicWork(workItemId: String, text: String): Long {
        val row = dao.inspection(workItemId) ?: error("Working item no longer exists"); val normalizedText = text.trim()
        if(row.workPerformed==normalizedText) return database.withTransaction { workingItem(workItemId); dao.inspection(workItemId)?.modifiedAtEpochMillis ?: error("Working item no longer exists") }
        writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); database.withTransaction { val item=workingItem(workItemId); check(dao.updatePublicWork(workItemId, normalizedText) == 1); dao.touchVisit(item.visitId, now) }; return now
    }

    override suspend fun markChecklistReviewed(workItemId: String): Long {
        val draft = inspection(workItemId) ?: error("Working item no longer exists")
        val invalid = draft.questions.filter { q ->
            (q.disposition == ResponseDisposition.ISSUE_FOUND && q.reason.isNullOrBlank()) ||
                (q.required && when (q.responseType) {
                    "STATUS" -> q.disposition == ResponseDisposition.NOT_CHECKED || (q.disposition == ResponseDisposition.NOT_APPLICABLE && q.reason.isNullOrBlank())
                    "NUMBER" -> q.disposition == ResponseDisposition.UNANSWERED || (q.disposition == ResponseDisposition.VALUE && !isFiniteSignedDecimal(q.numberValue.orEmpty())) || (q.disposition == ResponseDisposition.NOT_APPLICABLE && q.reason.isNullOrBlank())
                    else -> q.disposition == ResponseDisposition.UNANSWERED || (q.disposition == ResponseDisposition.VALUE && q.textValue.isNullOrBlank()) || (q.disposition == ResponseDisposition.NOT_APPLICABLE && q.reason.isNullOrBlank())
                })
        }
        require(invalid.isEmpty()) { "${invalid.size} required checklist item(s) need attention" }
        writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); database.withTransaction { val item=workingItem(workItemId); check(dao.updateChecklistReviewed(workItemId, true) == 1); dao.touchVisit(item.visitId, now) }; return now
    }

    override suspend fun saveCompletionDraft(workItemId: String, outcome: String?, fulfills: Boolean, reason: String?, nextDue: String?, calculated: Boolean?, overrideReason: String?): Long {
        require(outcome == null || outcome in setOf("PERFORMED", "PARTLY_PERFORMED", "NOT_PERFORMED"))
        val item = dao.workItem(workItemId) ?: error("Working item no longer exists"); val visit = dao.visit(item.visitId) ?: error("Visit no longer exists")
        val eligible = outcome == "PERFORMED" && item.servicePlanId != null && item.capturedObligationId != null && (item.templateSnapshotId == null || item.checklistReviewed); val effectiveFulfills = fulfills && eligible
        val calculatedDate = if (effectiveFulfills && item.intervalCountSnapshot != null && item.intervalUnitSnapshot != null) RecurrenceCalculator.nextDate(LocalDate.parse(visit.actualServiceDate), item.intervalCountSnapshot, item.intervalUnitSnapshot).toString() else null
        val chosen = nextDue?.takeIf { effectiveFulfills }; if (chosen != null) require(LocalDate.parse(chosen).isAfter(LocalDate.parse(visit.actualServiceDate))) { "Next due must be after the service date" }
        val normalizedReason = reason?.trim()?.ifBlank { null }?.takeIf { outcome == "NOT_PERFORMED" }
        val derivedCalculated = chosen?.let { it == calculatedDate }
        val normalizedOverride = overrideReason?.trim()?.ifBlank { null }?.takeIf { chosen != null && derivedCalculated == false }
        if (chosen != null && derivedCalculated == false) require(normalizedOverride != null) { "Override reason is required" }
        if(item.outcome==outcome&&item.fulfillsCurrentObligation==effectiveFulfills&&item.notPerformedReason==normalizedReason&&item.confirmedNextDueDate==chosen&&item.nextDueDateCalculated==derivedCalculated&&item.nextDueOverrideReason==normalizedOverride) return database.withTransaction { workingItem(workItemId); dao.visit(item.visitId)?.modifiedAtEpochMillis ?: error("Visit no longer exists") }
        writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli(); database.withTransaction { val current=workingItem(workItemId); check(dao.updateCompletionDraft(workItemId, outcome, effectiveFulfills, normalizedReason, chosen, derivedCalculated, normalizedOverride) == 1); dao.touchVisit(current.visitId, now) }; return now
    }

    override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?): Long {
        val inspection = dao.inspection(workItemId) ?: error("Working item no longer exists"); val item = dao.checklistItems(inspection.templateSnapshotId ?: error("Checklist no longer exists")).firstOrNull { it.id == questionId } ?: error("Checklist item no longer exists")
        val normalizedValue = value?.trim()?.takeIf { disposition == ResponseDisposition.VALUE }; val normalizedReason = reason?.trim()?.ifBlank { null }?.takeIf { disposition in setOf(ResponseDisposition.ISSUE_FOUND, ResponseDisposition.NOT_APPLICABLE) }
        val allowed = if (item.responseType == "STATUS") setOf(ResponseDisposition.OK, ResponseDisposition.ISSUE_FOUND, ResponseDisposition.NOT_APPLICABLE, ResponseDisposition.NOT_CHECKED) else setOf(ResponseDisposition.UNANSWERED, ResponseDisposition.NOT_APPLICABLE, ResponseDisposition.VALUE); require(disposition in allowed); if (disposition == ResponseDisposition.VALUE) require(!normalizedValue.isNullOrBlank()); if (item.responseType == "NUMBER" && disposition == ResponseDisposition.VALUE) require(isFiniteSignedDecimal(normalizedValue!!)) { "Enter a signed decimal number, for example -12.5" }; if (disposition == ResponseDisposition.NOT_APPLICABLE) require(normalizedReason != null)
        val existing = dao.responses(workItemId).firstOrNull { it.checklistItemSnapshotId == questionId }
        if(existing!=null&&existing.disposition==disposition.name&&(if(item.responseType=="NUMBER") existing.numberValue else existing.textValue)==normalizedValue?.takeIf{disposition==ResponseDisposition.VALUE}&&existing.reason==normalizedReason?.takeIf{disposition in setOf(ResponseDisposition.ISSUE_FOUND,ResponseDisposition.NOT_APPLICABLE)}) return database.withTransaction { workingItem(workItemId); dao.inspection(workItemId)?.modifiedAtEpochMillis ?: error("Working item no longer exists") }
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        val response = WorkingResponseEntity(existing?.id ?: stableId("response", workItemId, item.id), workItemId, questionId, disposition.name, normalizedValue?.takeIf { item.responseType == "TEXT" }, normalizedValue?.takeIf { item.responseType == "NUMBER" }, normalizedReason, now)
        database.withTransaction { workingItem(workItemId); dao.persistResponse(response, inspection.visitId) }; return now
    }

    override suspend fun finalizeVisit(visitId: String): FinalizeResult = database.withTransaction {
        dao.finalRecordForVisit(visitId)?.let { return@withTransaction FinalizeResult.Success(it.id) }
        val visit = dao.visit(visitId) ?: return@withTransaction FinalizeResult.Blocked("Working visit no longer exists"); if (visit.state != "WORKING") return@withTransaction FinalizeResult.Blocked("Visit is not working")
        val profile = visitReportIdentity(visitId)
        if (profile == null || profile.businessName.trim().isEmpty() || profile.technicianName.trim().isEmpty() || profile.zoneId.trim().isEmpty() || visit.customerReferenceSnapshot.isNullOrBlank() || visit.siteReferenceSnapshot.isNullOrBlank()) return@withTransaction FinalizeResult.Blocked("Visit/report identity is incomplete — review it before finalizing")
        val items = dao.visitWorkItems(visitId); if (items.isEmpty()) return@withTransaction FinalizeResult.Blocked("Visit has no work items")
        data class Prepared(val item: WorkItemEntity, val work: String, val privateNote: String, val plan: ServicePlanEntity?, val oldObligation: ServiceObligationEntity?, val nextDue: String?)
        val prepared = mutableListOf<Prepared>()
        for (item in items) {
            val row = dao.inspection(item.id) ?: return@withTransaction FinalizeResult.Blocked("Saved work is incomplete"); val outcome = item.outcome ?: return@withTransaction FinalizeResult.Blocked("Choose an outcome for every line")
            for (photo in dao.workItemAttachments(item.id).filter { it.includedInCustomerReport }) {
                val file = attachmentRoot?.let { File(it, photo.storedRelativePath) }
                if (photo.availability != "PRESENT" || file?.isFile != true || file.length() != photo.byteSize || sha256(file) != photo.sha256) return@withTransaction FinalizeResult.Blocked("A selected customer-report photograph is missing or changed")
            }
            if (outcome in setOf("PERFORMED", "PARTLY_PERFORMED") && row.workPerformed.isBlank()) return@withTransaction FinalizeResult.Blocked("Work performed is required for $outcome work")
            if (outcome == "NOT_PERFORMED" && item.notPerformedReason.isNullOrBlank()) return@withTransaction FinalizeResult.Blocked("Reason is required for Not performed work")
            if (item.templateSnapshotId != null) {
                val answers = dao.responses(item.id).associateBy { it.checklistItemSnapshotId }
                val questions = dao.checklistItems(item.templateSnapshotId)
                if (questions.any { q -> answers[q.id]?.let { it.disposition == "ISSUE_FOUND" && it.reason.isNullOrBlank() } == true }) return@withTransaction FinalizeResult.Blocked("Issue found needs a public description")
                if (questions.any { q -> !isValidExplicitAnswer(q, answers[q.id]) }) return@withTransaction FinalizeResult.Blocked("A saved checklist answer is invalid — review it before finalizing")
                if (outcome == "PERFORMED") {
                    if (!item.checklistReviewed) return@withTransaction FinalizeResult.Blocked("Checklist needs review")
                    val invalid = questions.any { q -> q.required && !isCompleteChecklistAnswer(q, answers[q.id]) }
                    if (invalid) return@withTransaction FinalizeResult.Blocked("Checklist needs review")
                }
            }
            val plan = item.servicePlanId?.let { dao.plan(it) }; val obligation = item.capturedObligationId?.let { dao.obligation(it) }; val fulfills = item.fulfillsCurrentObligation == true
            if (fulfills) {
                if (outcome != "PERFORMED" || plan == null || plan.state != "ACTIVE" || plan.currentObligationId != item.capturedObligationId || obligation == null || obligation.planId != plan.id || obligation.consumedAtEpochMillis != null) return@withTransaction FinalizeResult.Blocked("Current service obligation changed — review this line before finalizing")
                val actual = LocalDate.parse(visit.actualServiceDate); if (plan.lastCountedCompletionDate?.let(LocalDate::parse)?.let { !actual.isAfter(it) } == true) return@withTransaction FinalizeResult.Blocked("Service date must be after the latest counted completion")
                val next = item.confirmedNextDueDate ?: return@withTransaction FinalizeResult.Blocked("Confirm the next due date before finalizing"); if (!LocalDate.parse(next).isAfter(actual)) return@withTransaction FinalizeResult.Blocked("Next due must be after the service date")
                val calculatedNext = RecurrenceCalculator.nextDate(actual, item.intervalCountSnapshot ?: return@withTransaction FinalizeResult.Blocked("Review the confirmed next due date"), item.intervalUnitSnapshot ?: return@withTransaction FinalizeResult.Blocked("Review the confirmed next due date")).toString()
                if (item.nextDueDateCalculated != (next == calculatedNext) || (next == calculatedNext && item.nextDueOverrideReason != null) || (next != calculatedNext && item.nextDueOverrideReason.isNullOrBlank())) return@withTransaction FinalizeResult.Blocked("Review the confirmed next due date")
            } else if (item.confirmedNextDueDate != null || item.nextDueDateCalculated != null || item.nextDueOverrideReason != null) {
                return@withTransaction FinalizeResult.Blocked("Review the confirmed next due date")
            }
            prepared += Prepared(item, row.workPerformed, row.privateInternalNote, plan, obligation, item.confirmedNextDueDate)
        }
        finalizationWriteGate.beforeCommit(); val now = businessTime.instant().toEpochMilli(); val recordId = stableId("record", visitId); val revisionId = stableId("revision-1", visitId)
        dao.insertFinalRecord(FinalRecordEntity(recordId, visitId, revisionId, now)); dao.insertFinalRevision(FinalRecordRevisionEntity(revisionId, recordId, 1, visit.reference, visit.actualServiceDate, now, visit.customerNameSnapshot, visit.siteNameSnapshot, visit.siteAddressSnapshot, profile.businessName, profile.technicianName, profile.phone.ifBlank { null }, profile.email.ifBlank { null }, profile.postalAddress.ifBlank { null }, profile.zoneId, null, visit.customerReferenceSnapshot, visit.siteReferenceSnapshot))
        prepared.forEachIndexed { index, p ->
            val finalItemId = stableId("final-work", revisionId, p.item.id); val fulfills = p.item.fulfillsCurrentObligation == true
            dao.insertFinalWorkItems(listOf(FinalWorkItemEntity(finalItemId, revisionId, index + 1, p.item.id, p.item.equipmentId, p.item.equipmentNameSnapshot, p.item.equipmentReferenceSnapshot, p.item.equipmentIdentifierSnapshot, p.item.equipmentMakeSnapshot, p.item.equipmentModelSnapshot, p.item.equipmentSerialSnapshot, p.item.serviceNameSnapshot, p.item.servicePlanId, p.item.planReferenceSnapshot, p.item.outcome!!, p.work.takeIf(String::isNotBlank), p.item.notPerformedReason, fulfills, p.item.dueDateSnapshot, p.nextDue.takeIf { fulfills }, p.item.intervalCountSnapshot, p.item.intervalUnitSnapshot, p.item.capturedObligationId, p.privateNote.takeIf(String::isNotBlank), p.item.nextDueDateCalculated.takeIf { fulfills }, p.item.nextDueOverrideReason.takeIf { fulfills })))
            p.item.templateSnapshotId?.let { snapshotId -> val responseById = dao.responses(p.item.id).associateBy { it.checklistItemSnapshotId }; val template = dao.templateSnapshot(snapshotId); dao.insertFinalChecklistItems(dao.checklistItems(snapshotId).map { q -> val a = responseById[q.id]; FinalChecklistItemEntity(stableId("final-check", finalItemId, q.id), finalItemId, q.position, snapshotId, template?.revision, q.label, q.responseType, q.unit, q.required, a?.disposition ?: if (q.responseType == "STATUS") "NOT_CHECKED" else "UNANSWERED", a?.textValue, a?.numberValue, a?.reason) }) }
            dao.insertFinalParts(dao.parts(p.item.id).mapIndexed { partIndex, part -> FinalPartEntryEntity(stableId("final-part", finalItemId, part.id), finalItemId, partIndex + 1, part.description, part.quantity, part.unit) })
            dao.insertFinalPhotos(dao.workItemAttachments(p.item.id).filter { it.includedInCustomerReport && it.availability == "PRESENT" }.mapIndexed { photoIndex, photo -> FinalPhotoEntryEntity(stableId("final-photo", finalItemId, photo.id), finalItemId, photoIndex + 1, photo.id, photo.storedRelativePath, photo.sha256, photo.byteSize, photo.mimeType, photo.caption) })
            if (fulfills) { val plan = p.plan!!; val old = p.oldObligation!!; val nextId = stableId("obligation", revisionId, plan.id); check(dao.consumeObligation(old.id, plan.id, now, revisionId) == 1) { "Current service obligation changed — review this line before finalizing" }; dao.insertObligations(listOf(ServiceObligationEntity(nextId, plan.id, old.sequence + 1, p.nextDue!!, now))); check(dao.advancePlan(plan.id, old.id, p.nextDue, nextId, visit.actualServiceDate, revisionId) == 1) { "Current service obligation changed — review this line before finalizing" } }
        }
        check(dao.finalizeVisit(visitId, now) == 1); dao.releaseVisitClaims(visitId); FinalizeResult.Success(recordId)
    }

    override suspend fun finalRecord(recordId: String): FinalRecordDetail? {
        val record = dao.finalRecord(recordId) ?: return null; val revision = dao.finalRevision(record.currentRevisionId) ?: return null; val items = dao.finalWorkItems(revision.id)
        val publicLines = items.map { item -> PublicWorkLine(item.position, item.equipmentName, item.equipmentReference, listOfNotNull(item.equipmentIdentifier, item.equipmentMake, item.equipmentModel, item.equipmentSerial).joinToString(" · ").ifBlank { "Not recorded" }, item.serviceName, item.outcome, item.publicWorkNote, item.notPerformedReason, item.fulfilledObligation, item.oldDueDate, item.nextDueDate, dao.finalChecklistItems(item.id).map { q -> PublicChecklistItem(q.position, q.label, q.responseType, q.unit, q.required, q.disposition, q.textValue ?: q.numberValue, q.reason) }, item.planReference, item.planId != null, dao.finalParts(item.id).map { PublicPart(it.description, it.quantity, it.unit) }, dao.finalPhotos(item.id).map { PublicPhoto(it.storedRelativePath, it.sha256, it.byteSize, it.mimeType, it.caption) }, historyOnly=item.planId!=null&&item.capturedObligationId==null) }
        val model = PublicReportModel(record.id, revision.id, revision.revisionNumber, revision.visitReference, revision.actualServiceDate, revision.recordedAtEpochMillis, revision.businessName, revision.technicianName, listOfNotNull(revision.businessPhone, revision.businessEmail, revision.businessAddress).joinToString(" · "), revision.customerName, revision.siteName, revision.siteAddress, publicLines, revision.customerReference, revision.siteReference)
        val rendition = dao.reportRendition(revision.id)?.let { ReportRendition(it.id, it.revisionId, it.versionNumber, it.generatedAtEpochMillis, it.relativePath, it.sha256, it.byteSize, it.pageCount, it.status, it.failureMessage) }
        return FinalRecordDetail(model, items.mapNotNull { it.privateInternalNote }, rendition)
    }

    private fun stableId(vararg parts: String) = UUID.nameUUIDFromBytes(parts.joinToString(":").toByteArray()).toString()

    private suspend fun workingItem(workItemId: String): WorkItemEntity {
        val item=dao.workItem(workItemId)?:error("Work item no longer exists")
        require(dao.visit(item.visitId)?.state=="WORKING"){"Visit is no longer working"}
        return item
    }

    private fun clean(value: String?): String? = value?.trim()?.ifBlank { null }
    private data class NormalizedPhoto(val bytes: ByteArray, val mimeType: String)
    private fun normalizePhoto(bytes: ByteArray): NormalizedPhoto {
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}; BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds); require(bounds.outWidth>0&&bounds.outHeight>0){"The selected file is not a readable image"}
        var sample=1; while(bounds.outWidth/sample>5120||bounds.outHeight/sample>5120) sample*=2
        val decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample})?:error("The selected image could not be decoded")
        val orientation=runCatching{ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees=when(orientation){ExifInterface.ORIENTATION_ROTATE_90->90f;ExifInterface.ORIENTATION_ROTATE_180->180f;ExifInterface.ORIENTATION_ROTATE_270->270f;else->0f}
        val oriented=if(degrees==0f) decoded else Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,Matrix().apply{postRotate(degrees)},true)
        val scale=minOf(1f,2560f/maxOf(oriented.width,oriented.height)); val resized=if(scale<1f) Bitmap.createScaledBitmap(oriented,(oriented.width*scale).toInt().coerceAtLeast(1),(oriented.height*scale).toInt().coerceAtLeast(1),true) else oriented
        val output=ByteArrayOutputStream(); val hasAlpha=resized.hasAlpha(); check(resized.compress(if(hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,if(hasAlpha) 100 else 90,output)){"The selected image could not be stored"}; return NormalizedPhoto(output.toByteArray(),if(hasAlpha)"image/png" else "image/jpeg")
    }
    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    private fun reference(prefix: String, sequence: Int) = "$prefix-${sequence.toString().padStart(3, '0')}"
    private fun validateCustomer(input: CustomerInput) { require(input.name.trim().isNotEmpty() && input.name.length <= 200); require(input.contactName.length <= 200 && input.phone.length <= 100 && input.email.length <= 320 && input.privateNote.length <= 5000) }
    private fun validateSite(input: SiteInput) { require(input.name.trim().isNotEmpty() && input.name.length <= 200); require(input.address.length <= 500 && input.contactName.length <= 200 && input.phone.length <= 100 && input.email.length <= 320 && input.privateAccessNote.length <= 5000) }
    private fun validateEquipment(input: EquipmentInput) { require(input.name.trim().isNotEmpty() && input.name.length <= 200); require(input.technicianIdentifier.length <= 100 && input.make.length <= 100 && input.model.length <= 100 && input.serialNumber.length <= 150 && input.privateNote.length <= 5000) }
    private fun validatePlan(input: PlanInput) { require(input.name.trim().isNotEmpty() && input.name.length <= 200); require(input.intervalCount > 0); require(input.intervalUnit in setOf("DAYS", "WEEKS", "MONTHS", "YEARS")); LocalDate.parse(input.dueDate) }
    private fun validateTemplate(name: String, items: List<TemplateItemDraft>) { require(name.trim().isNotEmpty() && name.length <= 200); require(items.isNotEmpty()); items.forEach { require(it.label.trim().isNotEmpty() && it.label.length <= 300); require(it.responseType in setOf("STATUS", "TEXT", "NUMBER")); require(it.unit.length <= 30 && it.privateGuidance.length <= 2000) } }
    private fun reusableItem(revisionId: String, index: Int, item: TemplateItemDraft) = ReusableTemplateItemEntity(UUID.randomUUID().toString(), revisionId, index + 1, item.label.trim(), item.responseType, clean(item.unit).takeIf { item.responseType == "NUMBER" }, item.required, clean(item.privateGuidance))
    private suspend fun followUpDetail(value: FollowUpEntity) = FollowUpDetail(value.id, value.reference, value.type, value.title, value.dueDate, value.state, value.customerId, value.siteId, value.equipmentId, value.privatePlanningNote.orEmpty(), value.closureReason, dao.customer(value.customerId)?.name.orEmpty(), value.siteId?.let { dao.site(it)?.name }, value.equipmentId?.let { dao.equipment(it)?.name })

    private suspend fun captureTemplateSnapshot(templateId: String?, visitId: String, planId: String, now: Long): String? {
        val master = templateId?.let { dao.reusableTemplate(it) } ?: return null
        val revision = dao.reusableTemplateRevision(master.currentRevisionId) ?: error("Template revision missing")
        val snapshotId = stableId("template-snapshot", visitId, planId, revision.id)
        val revisionItems=dao.reusableTemplateItems(revision.id)
        dao.templateSnapshot(snapshotId)?.let { existing ->
            require(existing.sourceTemplateId==master.id&&existing.templateName==revision.nameSnapshot&&existing.revision==revision.revisionNumber){"Existing template snapshot does not match the current immutable revision"}
            val expected=revisionItems.map { item -> ChecklistItemSnapshotEntity(stableId("snapshot-item",snapshotId,item.id),snapshotId,item.position,item.label,item.responseType,item.unit,item.required,item.privateGuidance) }
            val actual=dao.checklistItems(snapshotId)
            require(actual==expected){"Existing checklist snapshot does not match the current immutable revision"}
            return snapshotId
        }
        dao.insertTemplateSnapshots(listOf(TemplateSnapshotEntity(snapshotId, master.id, revision.nameSnapshot, revision.revisionNumber, now)))
        dao.insertChecklistItems(revisionItems.map { item -> ChecklistItemSnapshotEntity(stableId("snapshot-item", snapshotId, item.id), snapshotId, item.position, item.label, item.responseType, item.unit, item.required, item.privateGuidance) })
        return snapshotId
    }

    private fun isFiniteSignedDecimal(value: String): Boolean = SIGNED_DECIMAL.matches(value.trim()) && runCatching { BigDecimal(value.trim()) }.isSuccess

    private fun isCompleteChecklistAnswer(question: ChecklistItemSnapshotEntity, answer: WorkingResponseEntity?): Boolean {
        answer ?: return false
        return when (question.responseType) {
            "STATUS" -> when (answer.disposition) { "OK" -> true; "ISSUE_FOUND", "NOT_APPLICABLE" -> !answer.reason.isNullOrBlank(); else -> false }
            "NUMBER" -> when (answer.disposition) { "VALUE" -> isFiniteSignedDecimal(answer.numberValue.orEmpty()); "NOT_APPLICABLE" -> !answer.reason.isNullOrBlank(); else -> false }
            else -> when (answer.disposition) { "VALUE" -> !answer.textValue.isNullOrBlank(); "NOT_APPLICABLE" -> !answer.reason.isNullOrBlank(); else -> false }
        }
    }

    private fun isValidExplicitAnswer(question: ChecklistItemSnapshotEntity, answer: WorkingResponseEntity?): Boolean {
        answer ?: return true
        return when (question.responseType) {
            "STATUS" -> when (answer.disposition) { "OK", "NOT_CHECKED" -> true; "ISSUE_FOUND", "NOT_APPLICABLE" -> !answer.reason.isNullOrBlank(); else -> false }
            "TEXT" -> when (answer.disposition) { "VALUE" -> !answer.textValue.isNullOrBlank(); "NOT_APPLICABLE" -> !answer.reason.isNullOrBlank(); "UNANSWERED" -> true; else -> false }
            "NUMBER" -> when (answer.disposition) { "VALUE" -> isFiniteSignedDecimal(answer.numberValue.orEmpty()); "NOT_APPLICABLE" -> !answer.reason.isNullOrBlank(); "UNANSWERED" -> true; else -> false }
            else -> false
        }
    }

    private companion object { val SIGNED_DECIMAL = Regex("^[+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)$"); const val MAX_PHOTO_SOURCE_BYTES = 30 * 1024 * 1024 }
}
