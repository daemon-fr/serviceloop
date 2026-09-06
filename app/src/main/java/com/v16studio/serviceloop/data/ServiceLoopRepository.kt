package com.v16studio.serviceloop.data

import androidx.room.withTransaction
import com.v16studio.serviceloop.domain.*
import java.time.LocalDate
import java.util.UUID

interface ServiceLoopRepository {
    suspend fun home(): HomeSummary
    suspend fun equipment(id: String): EquipmentDetail?
    suspend fun equipmentList(): List<EquipmentSummary>
    suspend fun customerList(): List<CustomerSummary>
    suspend fun inspection(workItemId: String): InspectionDraft?
    suspend fun completionLines(visitId: String): List<CompletionLine>
    suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?): Long
    suspend fun visits(): List<VisitSummary> = emptyList()
    suspend fun businessProfile(): BusinessProfile? = null
    suspend fun saveBusinessProfile(profile: BusinessProfile): Long = error("Business profile unavailable")
    suspend fun savePublicWork(workItemId: String, text: String): Long = error("Work note unavailable")
    suspend fun markChecklistReviewed(workItemId: String): Long = error("Checklist review unavailable")
    suspend fun saveCompletionDraft(workItemId: String, outcome: String?, fulfills: Boolean, reason: String?, nextDue: String?, calculated: Boolean?, overrideReason: String?): Long = error("Completion draft unavailable")
    suspend fun finalizeVisit(visitId: String): FinalizeResult = FinalizeResult.Blocked("Finalization unavailable")
    suspend fun finalRecord(recordId: String): FinalRecordDetail? = null
}

fun interface DraftWriteGate { suspend fun beforeWrite() }
fun interface FinalizationWriteGate { suspend fun beforeCommit() }

class RoomServiceLoopRepository(
    private val database: ServiceLoopDatabase,
    private val businessTime: BusinessTime,
    private val writeGate: DraftWriteGate = DraftWriteGate {},
    private val finalizationWriteGate: FinalizationWriteGate = FinalizationWriteGate {},
) : ServiceLoopRepository {
    private val dao = database.serviceLoopDao()

    override suspend fun home(): HomeSummary {
        val today = businessTime.today(); val visit = dao.latestWorkingVisit(); val booked = dao.nextBookedVisit(); val followUp = dao.firstDueFollowUp(today.toString())
        return HomeSummary(visit?.id, visit?.reference, visit?.siteNameSnapshot, visit?.modifiedAtEpochMillis, visit?.id?.let { dao.firstWorkItemId(it) }, booked?.reference, booked?.actualServiceDate, dao.dueFollowUpCount(today.toString()), followUp?.reference, followUp?.title, dao.overdueCount(today.toString()), dao.dueSoonCount(today.toString(), today.plusDays(14).toString()))
    }

    override suspend fun visits() = dao.visits().map { VisitSummary(it.id, it.reference, it.siteName, it.actualServiceDate, it.state, it.finalRecordId) }
    override suspend fun equipmentList() = dao.equipmentList().map { EquipmentSummary(it.id, it.name, it.reference, it.technicianIdentifier, it.siteName, it.customerName, it.nearestDueDate) }
    override suspend fun customerList() = dao.customerList().map { CustomerSummary(it.id, it.name, it.reference, it.siteCount, it.equipmentCount) }

    override suspend fun equipment(id: String): EquipmentDetail? {
        val rows = dao.equipmentPlans(id); val first = rows.firstOrNull() ?: return null
        return EquipmentDetail(first.equipmentId, first.equipmentName, first.equipmentReference, first.technicianIdentifier, listOfNotNull(first.make, first.model).joinToString(" "), first.serialNumber, first.siteName, first.customerName,
            rows.map { EquipmentPlan(it.planId, it.planName, it.planReference, "Every ${it.intervalCount} ${it.intervalUnit.lowercase()}", it.currentDueDate, it.planState, it.currentObligationId, LocalDate.parse(it.currentDueDate).isBefore(businessTime.today())) }, dao.workingItemId(id))
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
            val eligibility = when { plan == null || item.capturedObligationId == null -> FulfillmentEligibility.NO_CURRENT_OBLIGATION; plan.state != "ACTIVE" -> FulfillmentEligibility.PLAN_INELIGIBLE; item.outcome != "PERFORMED" -> FulfillmentEligibility.OUTCOME_INELIGIBLE; item.templateSnapshotId != null && !item.checklistReviewed -> FulfillmentEligibility.CHECKLIST_NOT_REVIEWED; else -> FulfillmentEligibility.ELIGIBLE }
            val fulfills = eligibility == FulfillmentEligibility.ELIGIBLE && item.fulfillsCurrentObligation == true
            val calculated = if (fulfills && item.intervalCountSnapshot != null && item.intervalUnitSnapshot != null) RecurrenceCalculator.nextDate(LocalDate.parse(visit.actualServiceDate), item.intervalCountSnapshot, item.intervalUnitSnapshot).toString() else null
            val public = dao.inspection(item.id)?.workPerformed.orEmpty()
            val blockers = buildList { if ((item.outcome == "PERFORMED" || item.outcome == "PARTLY_PERFORMED") && public.isBlank()) add("Work performed is required"); if (item.outcome == "NOT_PERFORMED" && item.notPerformedReason.isNullOrBlank()) add("Reason is required"); if (fulfills && item.confirmedNextDueDate == null) add("Confirm the next due date") }
            CompletionLine(item.id, item.equipmentNameSnapshot, item.equipmentReferenceSnapshot, item.serviceNameSnapshot, item.outcome, eligibility, fulfills, item.dueDateSnapshot, calculated, public, item.checklistReviewed, item.notPerformedReason, calculated, item.confirmedNextDueDate, item.nextDueDateCalculated, item.nextDueOverrideReason, blockers)
        }
    }

    override suspend fun businessProfile(): BusinessProfile? = dao.businessProfile()?.let { BusinessProfile(it.businessName, it.technicianName, it.phone.orEmpty(), it.email.orEmpty(), it.postalAddress.orEmpty(), it.zoneId) }

    override suspend fun saveBusinessProfile(profile: BusinessProfile): Long {
        require(profile.ready) { "Business and technician names are required" }; writeGate.beforeWrite(); val now = businessTime.instant().toEpochMilli()
        dao.upsertBusinessProfile(BusinessProfileEntity(businessName = profile.businessName.trim(), technicianName = profile.technicianName.trim(), phone = profile.phone.trim().ifBlank { null }, email = profile.email.trim().ifBlank { null }, postalAddress = profile.postalAddress.trim().ifBlank { null }, zoneId = profile.zoneId, modifiedAtEpochMillis = now)); return now
    }

    override suspend fun savePublicWork(workItemId: String, text: String): Long {
        writeGate.beforeWrite(); val row = dao.inspection(workItemId) ?: error("Working item no longer exists"); if (row.workPerformed == text) return row.modifiedAtEpochMillis
        val now = businessTime.instant().toEpochMilli(); database.withTransaction { check(dao.updatePublicWork(workItemId, text.trim()) == 1); dao.touchVisit(row.visitId, now) }; return now
    }

    override suspend fun markChecklistReviewed(workItemId: String): Long {
        writeGate.beforeWrite(); val draft = inspection(workItemId) ?: error("Working item no longer exists")
        val invalid = draft.questions.filter { q -> q.required && when (q.responseType) { "STATUS" -> q.disposition == ResponseDisposition.NOT_CHECKED || (q.disposition == ResponseDisposition.ISSUE_FOUND && q.reason.isNullOrBlank()) || (q.disposition == ResponseDisposition.NOT_APPLICABLE && q.reason.isNullOrBlank()); else -> q.disposition == ResponseDisposition.UNANSWERED || (q.disposition == ResponseDisposition.NOT_APPLICABLE && q.reason.isNullOrBlank()) } }
        require(invalid.isEmpty()) { "${invalid.size} required checklist item(s) need attention" }
        val now = businessTime.instant().toEpochMilli(); database.withTransaction { check(dao.updateChecklistReviewed(workItemId, true) == 1); dao.touchVisit(draft.visitId, now) }; return now
    }

    override suspend fun saveCompletionDraft(workItemId: String, outcome: String?, fulfills: Boolean, reason: String?, nextDue: String?, calculated: Boolean?, overrideReason: String?): Long {
        require(outcome == null || outcome in setOf("PERFORMED", "PARTLY_PERFORMED", "NOT_PERFORMED")); writeGate.beforeWrite()
        val item = dao.workItem(workItemId) ?: error("Working item no longer exists"); val visit = dao.visit(item.visitId) ?: error("Visit no longer exists")
        val eligible = outcome == "PERFORMED" && item.servicePlanId != null && item.capturedObligationId != null && (item.templateSnapshotId == null || item.checklistReviewed); val effectiveFulfills = fulfills && eligible
        val calculatedDate = if (effectiveFulfills && item.intervalCountSnapshot != null && item.intervalUnitSnapshot != null) RecurrenceCalculator.nextDate(LocalDate.parse(visit.actualServiceDate), item.intervalCountSnapshot, item.intervalUnitSnapshot).toString() else null
        val chosen = nextDue?.takeIf { effectiveFulfills }; if (chosen != null) require(LocalDate.parse(chosen).isAfter(LocalDate.parse(visit.actualServiceDate))) { "Next due must be after the service date" }; if (chosen != null && chosen != calculatedDate) require(!overrideReason.isNullOrBlank()) { "Override reason is required" }
        val now = businessTime.instant().toEpochMilli(); database.withTransaction { check(dao.updateCompletionDraft(workItemId, outcome, effectiveFulfills, reason?.trim()?.ifBlank { null }, chosen, calculated?.takeIf { chosen != null }, overrideReason?.trim()?.ifBlank { null }) == 1); dao.touchVisit(item.visitId, now) }; return now
    }

    override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?): Long {
        val inspection = dao.inspection(workItemId) ?: error("Working item no longer exists"); val item = dao.checklistItems(inspection.templateSnapshotId ?: error("Checklist no longer exists")).firstOrNull { it.id == questionId } ?: error("Checklist item no longer exists")
        val allowed = if (item.responseType == "STATUS") setOf(ResponseDisposition.OK, ResponseDisposition.ISSUE_FOUND, ResponseDisposition.NOT_APPLICABLE, ResponseDisposition.NOT_CHECKED) else setOf(ResponseDisposition.UNANSWERED, ResponseDisposition.NOT_APPLICABLE, ResponseDisposition.VALUE); require(disposition in allowed); if (disposition == ResponseDisposition.VALUE) require(!value.isNullOrBlank()); if (disposition == ResponseDisposition.NOT_APPLICABLE) require(!reason.isNullOrBlank())
        val existing = dao.responses(workItemId).firstOrNull { it.checklistItemSnapshotId == questionId }
        val normalizedValue = value?.trim(); val normalizedReason = reason?.trim()?.ifBlank { null }
        if (existing != null && existing.disposition == disposition.name && (if (item.responseType == "NUMBER") existing.numberValue else existing.textValue) == normalizedValue?.takeIf { disposition == ResponseDisposition.VALUE } && existing.reason == normalizedReason?.takeIf { disposition in setOf(ResponseDisposition.ISSUE_FOUND, ResponseDisposition.NOT_APPLICABLE) }) return inspection.modifiedAtEpochMillis
        writeGate.beforeWrite()
        val now = businessTime.instant().toEpochMilli()
        val response = WorkingResponseEntity(existing?.id ?: stableId("response", workItemId, item.id), workItemId, questionId, disposition.name, value?.takeIf { item.responseType == "TEXT" && disposition == ResponseDisposition.VALUE }, value?.takeIf { item.responseType == "NUMBER" && disposition == ResponseDisposition.VALUE }, reason?.trim()?.takeIf { disposition in setOf(ResponseDisposition.ISSUE_FOUND, ResponseDisposition.NOT_APPLICABLE) && it.isNotEmpty() }, now)
        database.withTransaction { dao.persistResponse(response, inspection.visitId) }; return now
    }

    override suspend fun finalizeVisit(visitId: String): FinalizeResult = database.withTransaction {
        dao.finalRecordForVisit(visitId)?.let { return@withTransaction FinalizeResult.Success(it.id) }
        val visit = dao.visit(visitId) ?: return@withTransaction FinalizeResult.Blocked("Working visit no longer exists"); if (visit.state != "WORKING") return@withTransaction FinalizeResult.Blocked("Visit is not working")
        val profile = dao.businessProfile() ?: return@withTransaction FinalizeResult.Blocked("Business/report identity is required"); if (profile.businessName.isBlank() || profile.technicianName.isBlank()) return@withTransaction FinalizeResult.Blocked("Business/report identity is required")
        val items = dao.visitWorkItems(visitId); if (items.isEmpty()) return@withTransaction FinalizeResult.Blocked("Visit has no work items")
        data class Prepared(val item: WorkItemEntity, val work: String, val privateNote: String, val plan: ServicePlanEntity?, val oldObligation: ServiceObligationEntity?, val nextDue: String?)
        val prepared = mutableListOf<Prepared>()
        for (item in items) {
            val row = dao.inspection(item.id) ?: return@withTransaction FinalizeResult.Blocked("Saved work is incomplete"); val outcome = item.outcome ?: return@withTransaction FinalizeResult.Blocked("Choose an outcome for every line")
            if (outcome in setOf("PERFORMED", "PARTLY_PERFORMED") && row.workPerformed.isBlank()) return@withTransaction FinalizeResult.Blocked("Work performed is required for $outcome work")
            if (outcome == "NOT_PERFORMED" && item.notPerformedReason.isNullOrBlank()) return@withTransaction FinalizeResult.Blocked("Reason is required for Not performed work")
            if (item.templateSnapshotId != null && !item.checklistReviewed) return@withTransaction FinalizeResult.Blocked("Checklist needs review")
            if (item.templateSnapshotId != null) {
                val answers = dao.responses(item.id).associateBy { it.checklistItemSnapshotId }
                val invalid = dao.checklistItems(item.templateSnapshotId).any { q -> q.required && answers[q.id].let { a -> a == null || when (q.responseType) { "STATUS" -> a.disposition == "NOT_CHECKED" || (a.disposition in setOf("ISSUE_FOUND", "NOT_APPLICABLE") && a.reason.isNullOrBlank()); else -> a.disposition == "UNANSWERED" || (a.disposition == "NOT_APPLICABLE" && a.reason.isNullOrBlank()) } } }
                if (invalid) return@withTransaction FinalizeResult.Blocked("Checklist needs review")
            }
            val plan = item.servicePlanId?.let { dao.plan(it) }; val obligation = item.capturedObligationId?.let { dao.obligation(it) }; val fulfills = item.fulfillsCurrentObligation == true
            if (fulfills) {
                if (outcome != "PERFORMED" || plan == null || plan.state != "ACTIVE" || plan.currentObligationId != item.capturedObligationId || obligation == null || obligation.planId != plan.id || obligation.consumedAtEpochMillis != null) return@withTransaction FinalizeResult.Blocked("Current service obligation changed — review this line before finalizing")
                val actual = LocalDate.parse(visit.actualServiceDate); if (plan.lastCountedCompletionDate?.let(LocalDate::parse)?.let { !actual.isAfter(it) } == true) return@withTransaction FinalizeResult.Blocked("Service date must be after the latest counted completion")
                val next = item.confirmedNextDueDate ?: return@withTransaction FinalizeResult.Blocked("Confirm the next due date before finalizing"); if (!LocalDate.parse(next).isAfter(actual)) return@withTransaction FinalizeResult.Blocked("Next due must be after the service date")
            }
            prepared += Prepared(item, row.workPerformed, row.privateInternalNote, plan, obligation, item.confirmedNextDueDate)
        }
        finalizationWriteGate.beforeCommit(); val now = businessTime.instant().toEpochMilli(); val recordId = stableId("record", visitId); val revisionId = stableId("revision-1", visitId)
        dao.insertFinalRecord(FinalRecordEntity(recordId, visitId, revisionId, now)); dao.insertFinalRevision(FinalRecordRevisionEntity(revisionId, recordId, 1, visit.reference, visit.actualServiceDate, now, visit.customerNameSnapshot, visit.siteNameSnapshot, visit.siteAddressSnapshot, profile.businessName, profile.technicianName, profile.phone, profile.email, profile.postalAddress, profile.zoneId, null))
        prepared.forEachIndexed { index, p ->
            val finalItemId = stableId("final-work", revisionId, p.item.id); val equipment = dao.equipment(p.item.equipmentId); val fulfills = p.item.fulfillsCurrentObligation == true
            dao.insertFinalWorkItems(listOf(FinalWorkItemEntity(finalItemId, revisionId, index + 1, p.item.id, p.item.equipmentId, p.item.equipmentNameSnapshot, p.item.equipmentReferenceSnapshot, equipment?.technicianIdentifier, equipment?.make, equipment?.model, equipment?.serialNumber, p.item.serviceNameSnapshot, p.item.servicePlanId, p.item.planReferenceSnapshot, p.item.outcome!!, p.work.takeIf(String::isNotBlank), p.item.notPerformedReason, fulfills, p.item.dueDateSnapshot, p.nextDue.takeIf { fulfills }, p.item.intervalCountSnapshot, p.item.intervalUnitSnapshot, p.item.capturedObligationId, p.privateNote.takeIf(String::isNotBlank))))
            p.item.templateSnapshotId?.let { snapshotId -> val responseById = dao.responses(p.item.id).associateBy { it.checklistItemSnapshotId }; val template = dao.templateSnapshot(snapshotId); dao.insertFinalChecklistItems(dao.checklistItems(snapshotId).map { q -> val a = responseById[q.id]; FinalChecklistItemEntity(stableId("final-check", finalItemId, q.id), finalItemId, q.position, snapshotId, template?.revision, q.label, q.responseType, q.unit, q.required, a?.disposition ?: if (q.responseType == "STATUS") "NOT_CHECKED" else "UNANSWERED", a?.textValue, a?.numberValue, a?.reason) }) }
            if (fulfills) { val plan = p.plan!!; val old = p.oldObligation!!; val nextId = stableId("obligation", revisionId, plan.id); check(dao.consumeObligation(old.id, plan.id, now, revisionId) == 1) { "Current service obligation changed — review this line before finalizing" }; dao.insertObligations(listOf(ServiceObligationEntity(nextId, plan.id, old.sequence + 1, p.nextDue!!, now))); check(dao.advancePlan(plan.id, old.id, p.nextDue, nextId, visit.actualServiceDate, revisionId) == 1) { "Current service obligation changed — review this line before finalizing" } }
        }
        check(dao.finalizeVisit(visitId, now) == 1); FinalizeResult.Success(recordId)
    }

    override suspend fun finalRecord(recordId: String): FinalRecordDetail? {
        val record = dao.finalRecord(recordId) ?: return null; val revision = dao.finalRevision(record.currentRevisionId) ?: return null; val items = dao.finalWorkItems(revision.id)
        val publicLines = items.map { item -> PublicWorkLine(item.position, item.equipmentName, item.equipmentReference, listOfNotNull(item.equipmentIdentifier, item.equipmentMake, item.equipmentModel, item.equipmentSerial).joinToString(" · ").ifBlank { "Not recorded" }, item.serviceName, item.outcome, item.publicWorkNote, item.notPerformedReason, item.fulfilledObligation, item.oldDueDate, item.nextDueDate, dao.finalChecklistItems(item.id).map { q -> PublicChecklistItem(q.position, q.label, q.responseType, q.unit, q.required, q.disposition, q.textValue ?: q.numberValue, q.reason) }) }
        val model = PublicReportModel(record.id, revision.id, revision.revisionNumber, revision.visitReference, revision.actualServiceDate, revision.recordedAtEpochMillis, revision.businessName, revision.technicianName, listOfNotNull(revision.businessPhone, revision.businessEmail, revision.businessAddress).joinToString(" · "), revision.customerName, revision.siteName, revision.siteAddress, publicLines)
        val rendition = dao.reportRendition(revision.id)?.let { ReportRendition(it.id, it.revisionId, it.versionNumber, it.generatedAtEpochMillis, it.relativePath, it.sha256, it.byteSize, it.pageCount, it.status, it.failureMessage) }
        return FinalRecordDetail(model, items.mapNotNull { it.privateInternalNote }, rendition)
    }

    private fun stableId(vararg parts: String) = UUID.nameUUIDFromBytes(parts.joinToString(":").toByteArray()).toString()
}
