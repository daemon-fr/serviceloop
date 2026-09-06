package com.v16studio.serviceloop

import androidx.room.withTransaction
import com.v16studio.serviceloop.data.AttachmentEntity
import com.v16studio.serviceloop.data.BusinessProfileEntity
import com.v16studio.serviceloop.data.ChecklistItemSnapshotEntity
import com.v16studio.serviceloop.data.CustomerEntity
import com.v16studio.serviceloop.data.EquipmentEntity
import com.v16studio.serviceloop.data.FollowUpEntity
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.data.ServiceObligationEntity
import com.v16studio.serviceloop.data.ServicePlanEntity
import com.v16studio.serviceloop.data.SiteEntity
import com.v16studio.serviceloop.data.TemplateSnapshotEntity
import com.v16studio.serviceloop.data.WorkItemEntity
import com.v16studio.serviceloop.data.WorkItemPrivateDraftEntity
import com.v16studio.serviceloop.data.WorkItemPublicDraftEntity
import com.v16studio.serviceloop.data.WorkingResponseEntity
import com.v16studio.serviceloop.data.WorkingVisitEntity
import java.time.Instant

object FixtureIds {
    const val CUSTOMER = "2a7f4ff8-6fc3-45ea-bc5e-e3f20bd7fd01"
    const val SITE = "5d26e3b5-fbe4-4f13-9bbb-632047f95601"
    const val EQUIPMENT_1 = "8716a8e6-41d4-4cc5-97e6-a8c1e4afe001"
    const val EQUIPMENT_2 = "8716a8e6-41d4-4cc5-97e6-a8c1e4afe002"
    const val EQUIPMENT_3 = "8716a8e6-41d4-4cc5-97e6-a8c1e4afe003"
    const val VISIT_1 = "3ed972d9-0479-40ce-aec8-0f53f60e1001"
    const val VISIT_2 = "3ed972d9-0479-40ce-aec8-0f53f60e1002"
    const val WORK_INSPECTION = "b9ce30a5-4c3c-49b8-a7a8-73390822f001"
    const val TEMPLATE = "1d90783e-cd9a-47c2-8c52-833df6347001"
}

object FixtureSeederFactory {
    fun create(database: ServiceLoopDatabase): StartupSeeder = DebugFixtureSeeder(database)
}

private class DebugFixtureSeeder(private val database: ServiceLoopDatabase) : StartupSeeder {
    override suspend fun seedIfNeeded() {
        val dao = database.serviceLoopDao()
        database.withTransaction {
            val created = Instant.parse("2026-09-05T07:14:00Z").toEpochMilli()
            if (dao.customerCount() != 0) {
                val fixture = dao.customer(FixtureIds.CUSTOMER)
                if (fixture?.name == "Harbor Fitness and Rehabilitation Cooperative" && dao.businessProfile() == null) {
                    dao.upsertBusinessProfile(BusinessProfileEntity(businessName = "Riverside Equipment Service", technicianName = "Alex Morgan", phone = "+40 21 555 0142", email = "service@example.invalid", postalAddress = "Bucharest", zoneId = "Europe/Bucharest", modifiedAtEpochMillis = created))
                }
                return@withTransaction
            }
            dao.insertCustomers(listOf(CustomerEntity(FixtureIds.CUSTOMER, "CU-001", "Harbor Fitness and Rehabilitation Cooperative")))
            dao.upsertBusinessProfile(BusinessProfileEntity(businessName = "Riverside Equipment Service", technicianName = "Alex Morgan", phone = "+40 21 555 0142", email = "service@example.invalid", postalAddress = "Bucharest", zoneId = "Europe/Bucharest", modifiedAtEpochMillis = created))
            dao.insertSites(listOf(SiteEntity(FixtureIds.SITE, FixtureIds.CUSTOMER, "ST-001", "Riverside Centre — East Building, Second-floor Training Room", "18 River Lane, Riverside", "Saturday access via east reception.")))
            dao.insertEquipment(listOf(
                EquipmentEntity(FixtureIds.EQUIPMENT_1, FixtureIds.SITE, "EQ-001", "T-01", "Treadmill 01 — window side", "StrideWorks", "R8", "SW-R8-0417", "Drive belt history held internally."),
                EquipmentEntity(FixtureIds.EQUIPMENT_2, FixtureIds.SITE, "EQ-002", "T-02", "Treadmill 02 — door side", "StrideWorks", "R8", null, null),
                EquipmentEntity(FixtureIds.EQUIPMENT_3, FixtureIds.SITE, "EQ-003", "B-01", "Stationary bike 01", "WheelWorks", "C2", "WW-C2-023", null),
            ))
            val plans = listOf(
                plan("plan-001", FixtureIds.EQUIPMENT_1, "P-001", "Condition inspection", 3, "MONTHS", "2026-09-01", "obl-001"),
                plan("plan-002", FixtureIds.EQUIPMENT_1, "P-002", "Lubrication service", 6, "MONTHS", "2026-09-01", "obl-002"),
                plan("plan-003", FixtureIds.EQUIPMENT_2, "P-003", "Maintenance", 6, "MONTHS", "2026-09-01", "obl-003"),
                plan("plan-005", FixtureIds.EQUIPMENT_2, "P-005", "Electrical inspection", 12, "MONTHS", "2026-09-15", "obl-005"),
                plan("plan-004", FixtureIds.EQUIPMENT_3, "P-004", "Maintenance", 6, "MONTHS", "2026-09-01", "obl-004"),
            )
            dao.insertPlans(plans)
            dao.insertObligations(plans.mapIndexed { index, plan -> ServiceObligationEntity(plan.currentObligationId!!, plan.id, 1, plan.currentDueDate, created + index) })
            dao.insertTemplateSnapshots(listOf(TemplateSnapshotEntity(FixtureIds.TEMPLATE, "template-general-condition", "General condition check", 2, created)))
            val checklist = listOf(
                item("check-guard", 1, "Guard fixing", "STATUS", null, true),
                item("check-belt", 2, "Belt condition", "STATUS", null, true),
                item("check-distance", 3, "Displayed distance", "NUMBER", "km", true),
                item("check-accessory", 4, "Optional accessory", "STATUS", null, true),
                item("check-observation", 5, "Additional observation", "TEXT", null, false),
            )
            dao.insertChecklistItems(checklist)
            dao.insertVisits(listOf(
                WorkingVisitEntity(FixtureIds.VISIT_1, "V-001", FixtureIds.CUSTOMER, FixtureIds.SITE, "2026-09-05", "Harbor Fitness and Rehabilitation Cooperative", "Riverside Centre — East Building, Second-floor Training Room", "18 River Lane, Riverside", "WORKING", created, "CU-001", "ST-001", "Riverside Equipment Service", "Alex Morgan", "+40 21 555 0142", "service@example.invalid", "Bucharest", "Europe/Bucharest"),
                WorkingVisitEntity(FixtureIds.VISIT_2, "V-002", FixtureIds.CUSTOMER, FixtureIds.SITE, "2026-09-08", "Harbor Fitness and Rehabilitation Cooperative", "Riverside Centre — East Building, Second-floor Training Room", "18 River Lane, Riverside", "BOOKED", created - 60_000),
            ))
            val workItems = listOf(
                work(FixtureIds.WORK_INSPECTION, FixtureIds.EQUIPMENT_1, "plan-001", "obl-001", FixtureIds.TEMPLATE, "Treadmill 01 — window side", "EQ-001", "Condition inspection", "P-001", "2026-09-01", 3, "MONTHS", true, "PERFORMED", false),
                work("work-002", FixtureIds.EQUIPMENT_1, "plan-002", "obl-002", null, "Treadmill 01 — window side", "EQ-001", "Lubrication service", "P-002", "2026-09-01", 6, "MONTHS", false, "PARTLY_PERFORMED", false),
                work("work-003", FixtureIds.EQUIPMENT_2, "plan-003", "obl-003", null, "Treadmill 02 — door side", "EQ-002", "Maintenance", "P-003", "2026-09-01", 6, "MONTHS", false, "PERFORMED", true),
                work("work-004", FixtureIds.EQUIPMENT_3, "plan-004", "obl-004", null, "Stationary bike 01", "EQ-003", "Maintenance", "P-004", "2026-09-01", 6, "MONTHS", false, "NOT_PERFORMED", false),
            )
            dao.insertWorkItems(workItems)
            dao.insertPublicDrafts(listOf(
                WorkItemPublicDraftEntity(FixtureIds.WORK_INSPECTION, "Inspected recorded items; belt issue observed."),
                WorkItemPublicDraftEntity("work-002", "Cleaned accessible surfaces; lubricant unavailable."),
                WorkItemPublicDraftEntity("work-003", "Checked and secured guard fixing."),
                WorkItemPublicDraftEntity("work-004", "Room unavailable."),
            ))
            dao.insertPrivateDrafts(workItems.map { WorkItemPrivateDraftEntity(it.id, if (it.id == FixtureIds.WORK_INSPECTION) "Confirm belt part availability before return." else "") })
            dao.upsertResponses(listOf(
                response("response-guard", "check-guard", "OK", null, null, null, created),
                response("response-belt", "check-belt", "ISSUE_FOUND", null, null, "Fraying on outer belt edge", created),
                response("response-distance", "check-distance", "VALUE", null, "1240.5", null, created),
                response("response-accessory", "check-accessory", "NOT_APPLICABLE", null, null, "Not fitted", created),
                response("response-observation", "check-observation", "UNANSWERED", null, null, null, created),
            ))
            dao.insertAttachments(listOf(
                AttachmentEntity("attach-ph-01", "WORK_ITEM", FixtureIds.WORK_INSPECTION, "attachments/attach-ph-01/original.jpg", "fixture-sha256-ph01", "belt.jpg", "image/jpeg", true, "PRESENT"),
                AttachmentEntity("attach-ph-02", "WORK_ITEM", FixtureIds.WORK_INSPECTION, "attachments/attach-ph-02/original.jpg", "fixture-sha256-ph02", "nameplate.jpg", "image/jpeg", false, "PRESENT"),
            ))
            dao.insertFollowUps(listOf(FollowUpEntity("follow-up-001", "FU-001", "CONTACT", "Confirm access for the return visit", "2026-09-05", "OPEN", FixtureIds.CUSTOMER, FixtureIds.SITE, null, "Call reception before arrival.")))
        }
    }

    private fun plan(id: String, equipmentId: String, reference: String, name: String, count: Int, unit: String, due: String, obligation: String) =
        ServicePlanEntity(id, equipmentId, reference, name, count, unit, due, "ACTIVE", obligation)

    private fun item(id: String, position: Int, label: String, type: String, unit: String?, required: Boolean) =
        ChecklistItemSnapshotEntity(id, FixtureIds.TEMPLATE, position, label, type, unit, required, "Fixture guidance — not in customer report")

    private fun work(id: String, equipmentId: String, planId: String, obligationId: String, templateId: String?, equipmentName: String, equipmentRef: String, serviceName: String, planRef: String, due: String, count: Int, unit: String, reviewed: Boolean, outcome: String, fulfills: Boolean) =
        WorkItemEntity(id, FixtureIds.VISIT_1, equipmentId, planId, obligationId, templateId, equipmentName, equipmentRef, serviceName, planRef, due, count, unit, reviewed, outcome, fulfills,
            equipmentIdentifierSnapshot = when (equipmentId) { FixtureIds.EQUIPMENT_1 -> "T-01"; FixtureIds.EQUIPMENT_2 -> "T-02"; else -> "B-01" },
            equipmentMakeSnapshot = if (equipmentId == FixtureIds.EQUIPMENT_3) "WheelWorks" else "StrideWorks",
            equipmentModelSnapshot = if (equipmentId == FixtureIds.EQUIPMENT_3) "C2" else "R8",
            equipmentSerialSnapshot = when (equipmentId) { FixtureIds.EQUIPMENT_1 -> "SW-R8-0417"; FixtureIds.EQUIPMENT_3 -> "WW-C2-023"; else -> null })

    private fun response(id: String, itemId: String, disposition: String, text: String?, number: String?, reason: String?, modified: Long) =
        WorkingResponseEntity(id, FixtureIds.WORK_INSPECTION, itemId, disposition, text, number, reason, modified)
}
