package com.v16studio.serviceloop.data

import androidx.room.withTransaction
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.domain.CompletionLine
import com.v16studio.serviceloop.domain.CustomerSummary
import com.v16studio.serviceloop.domain.EquipmentDetail
import com.v16studio.serviceloop.domain.EquipmentPlan
import com.v16studio.serviceloop.domain.EquipmentSummary
import com.v16studio.serviceloop.domain.FulfillmentEligibility
import com.v16studio.serviceloop.domain.HomeSummary
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.InspectionQuestion
import com.v16studio.serviceloop.domain.ResponseDisposition
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
}

fun interface DraftWriteGate { suspend fun beforeWrite() }

class RoomServiceLoopRepository(
    private val database: ServiceLoopDatabase,
    private val businessTime: BusinessTime,
    private val writeGate: DraftWriteGate = DraftWriteGate {},
) : ServiceLoopRepository {
    private val dao = database.serviceLoopDao()

    override suspend fun home(): HomeSummary {
        val today = businessTime.today()
        val visit = dao.latestWorkingVisit()
        val booked = dao.nextBookedVisit()
        val followUp = dao.firstDueFollowUp(today.toString())
        return HomeSummary(
            workingVisitId = visit?.id,
            workingVisitReference = visit?.reference,
            workingSite = visit?.siteNameSnapshot,
            savedAtEpochMillis = visit?.modifiedAtEpochMillis,
            inspectionWorkItemId = visit?.id?.let { dao.firstWorkItemId(it) },
            bookedVisitReference = booked?.reference,
            bookedVisitDate = booked?.actualServiceDate,
            dueFollowUpCount = dao.dueFollowUpCount(today.toString()),
            dueFollowUpReference = followUp?.reference,
            dueFollowUpTitle = followUp?.title,
            overdueCount = dao.overdueCount(today.toString()),
            dueSoonCount = dao.dueSoonCount(today.toString(), today.plusDays(14).toString()),
        )
    }

    override suspend fun equipmentList(): List<EquipmentSummary> = dao.equipmentList().map {
        EquipmentSummary(it.id, it.name, it.reference, it.technicianIdentifier, it.siteName, it.customerName, it.nearestDueDate)
    }

    override suspend fun customerList(): List<CustomerSummary> = dao.customerList().map {
        CustomerSummary(it.id, it.name, it.reference, it.siteCount, it.equipmentCount)
    }

    override suspend fun equipment(id: String): EquipmentDetail? {
        val rows = dao.equipmentPlans(id)
        val first = rows.firstOrNull() ?: return null
        return EquipmentDetail(
            id = first.equipmentId,
            name = first.equipmentName,
            reference = first.equipmentReference,
            technicianIdentifier = first.technicianIdentifier,
            makeModel = listOfNotNull(first.make, first.model).joinToString(" "),
            serialNumber = first.serialNumber,
            siteName = first.siteName,
            customerName = first.customerName,
            plans = rows.map {
                EquipmentPlan(
                    id = it.planId,
                    name = it.planName,
                    reference = it.planReference,
                    interval = "Every ${it.intervalCount} ${it.intervalUnit.lowercase()}",
                    dueDate = it.currentDueDate,
                    state = it.planState,
                    currentObligationId = it.currentObligationId,
                    isOverdue = LocalDate.parse(it.currentDueDate).isBefore(businessTime.today()),
                )
            },
            workingItemId = dao.workingItemId(id),
        )
    }

    override suspend fun inspection(workItemId: String): InspectionDraft? {
        val row = dao.inspection(workItemId) ?: return null
        val items = if (row.templateSnapshotId == null) emptyList() else dao.checklistItems(row.templateSnapshotId)
        val responseByItem = dao.responses(workItemId).associateBy { it.checklistItemSnapshotId }
        val revision = row.templateSnapshotId?.let { dao.templateSnapshot(it)?.revision }
        return InspectionDraft(
            workItemId = row.workItemId,
            visitId = row.visitId,
            visitReference = row.visitReference,
            siteName = row.siteNameSnapshot,
            equipmentName = row.equipmentNameSnapshot,
            equipmentReference = row.equipmentReferenceSnapshot,
            serviceName = row.serviceNameSnapshot,
            dueDate = row.dueDateSnapshot,
            interval = row.intervalCountSnapshot?.let { "Every $it ${row.intervalUnitSnapshot?.lowercase()}" },
            templateRevision = revision,
            workPerformed = row.workPerformed,
            privateInternalNote = row.privateInternalNote,
            checklistReviewed = row.checklistReviewed,
            outcome = row.outcome,
            fulfillsCurrentObligation = row.fulfillsCurrentObligation,
            modifiedAtEpochMillis = row.modifiedAtEpochMillis,
            questions = items.map { item ->
                val response = responseByItem[item.id]
                InspectionQuestion(
                    responseId = response?.id ?: UUID.nameUUIDFromBytes("${workItemId}:${item.id}".toByteArray()).toString(),
                    snapshotItemId = item.id,
                    position = item.position,
                    label = item.label,
                    responseType = item.responseType,
                    unit = item.unit,
                    required = item.required,
                    disposition = response?.disposition?.let(ResponseDisposition::valueOf) ?: if (item.responseType == "STATUS") ResponseDisposition.NOT_CHECKED else ResponseDisposition.UNANSWERED,
                    textValue = response?.textValue,
                    numberValue = response?.numberValue,
                    reason = response?.reason,
                )
            },
        )
    }

    override suspend fun completionLines(visitId: String): List<CompletionLine> {
        val actualServiceDate = dao.visit(visitId)?.actualServiceDate?.let(LocalDate::parse)
        return dao.visitWorkItems(visitId).map { item ->
            val public = dao.inspection(item.id)
            val eligibility = when {
                item.outcome != "PERFORMED" -> FulfillmentEligibility.OUTCOME_INELIGIBLE
                item.templateSnapshotId != null && !item.checklistReviewed -> FulfillmentEligibility.CHECKLIST_NOT_REVIEWED
                else -> FulfillmentEligibility.ELIGIBLE
            }
            val fulfills = eligibility == FulfillmentEligibility.ELIGIBLE && item.fulfillsCurrentObligation == true
            val proposed = if (fulfills) {
                item.intervalCountSnapshot?.let { count ->
                    val date = actualServiceDate ?: return@let null
                    when (item.intervalUnitSnapshot) {
                        "DAYS" -> date.plusDays(count.toLong())
                        "WEEKS" -> date.plusWeeks(count.toLong())
                        "MONTHS" -> date.plusMonths(count.toLong())
                        "YEARS" -> date.plusYears(count.toLong())
                        else -> null
                    }?.toString()
                }
            } else null
            CompletionLine(item.id, item.equipmentNameSnapshot, item.equipmentReferenceSnapshot, item.serviceNameSnapshot, item.outcome, eligibility, fulfills, item.dueDateSnapshot, proposed, public?.workPerformed.orEmpty())
        }
    }

    override suspend fun saveResponse(
        workItemId: String,
        questionId: String,
        disposition: ResponseDisposition,
        value: String?,
        reason: String?,
    ): Long {
        writeGate.beforeWrite()
        val inspection = dao.inspection(workItemId) ?: error("Working item no longer exists")
        val item = dao.checklistItems(inspection.templateSnapshotId ?: error("Checklist no longer exists")).firstOrNull { it.id == questionId }
            ?: error("Checklist item no longer exists")
        val now = businessTime.instant().toEpochMilli()
        val id = dao.responses(workItemId).firstOrNull { it.checklistItemSnapshotId == questionId }?.id
            ?: UUID.nameUUIDFromBytes("${workItemId}:${item.id}".toByteArray()).toString()
        val allowed = when (item.responseType) {
            "STATUS" -> setOf(ResponseDisposition.OK, ResponseDisposition.ISSUE_FOUND, ResponseDisposition.NOT_APPLICABLE, ResponseDisposition.NOT_CHECKED)
            "TEXT", "NUMBER" -> setOf(ResponseDisposition.UNANSWERED, ResponseDisposition.NOT_APPLICABLE, ResponseDisposition.VALUE)
            else -> emptySet()
        }
        require(disposition in allowed) { "${disposition.name} is not valid for ${item.responseType}" }
        if (disposition == ResponseDisposition.VALUE) require(!value.isNullOrBlank()) { "A saved value cannot be blank" }
        if (disposition == ResponseDisposition.NOT_APPLICABLE) require(!reason.isNullOrBlank()) { "Not applicable requires a reason" }
        val response = WorkingResponseEntity(
            id = id,
            workItemId = workItemId,
            checklistItemSnapshotId = questionId,
            disposition = disposition.name,
            textValue = if (item.responseType == "TEXT" && disposition == ResponseDisposition.VALUE) value else null,
            numberValue = if (item.responseType == "NUMBER" && disposition == ResponseDisposition.VALUE) value else null,
            reason = when (disposition) {
                ResponseDisposition.ISSUE_FOUND, ResponseDisposition.NOT_APPLICABLE -> reason?.trim()?.takeIf(String::isNotEmpty)
                else -> null
            },
            modifiedAtEpochMillis = now,
        )
        database.withTransaction { dao.persistResponse(response, inspection.visitId) }
        return now
    }
}
