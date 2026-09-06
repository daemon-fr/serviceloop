package com.v16studio.serviceloop.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

data class EquipmentPlanRow(
    val equipmentId: String,
    val equipmentName: String,
    val equipmentReference: String,
    val technicianIdentifier: String?,
    val make: String?,
    val model: String?,
    val serialNumber: String?,
    val siteName: String,
    val customerName: String,
    val planId: String,
    val planName: String,
    val planReference: String,
    val intervalCount: Int,
    val intervalUnit: String,
    val currentDueDate: String,
    val planState: String,
    val currentObligationId: String?,
)

data class InspectionRow(
    val workItemId: String,
    val visitId: String,
    val visitReference: String,
    val siteNameSnapshot: String,
    val equipmentNameSnapshot: String,
    val equipmentReferenceSnapshot: String,
    val serviceNameSnapshot: String,
    val dueDateSnapshot: String?,
    val intervalCountSnapshot: Int?,
    val intervalUnitSnapshot: String?,
    val templateSnapshotId: String?,
    val checklistReviewed: Boolean,
    val outcome: String?,
    val fulfillsCurrentObligation: Boolean?,
    val workPerformed: String,
    val privateInternalNote: String,
    val modifiedAtEpochMillis: Long,
)

data class EquipmentSummaryRow(
    val id: String,
    val name: String,
    val reference: String,
    val technicianIdentifier: String?,
    val siteName: String,
    val customerName: String,
    val nearestDueDate: String?,
)

data class CustomerSummaryRow(
    val id: String,
    val name: String,
    val reference: String,
    val siteCount: Int,
    val equipmentCount: Int,
)

data class VisitSummaryRow(val id: String, val reference: String, val siteName: String, val actualServiceDate: String, val state: String, val finalRecordId: String?, val resumeWorkItemId: String?)

@Dao
interface ServiceLoopDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCustomers(values: List<CustomerEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSites(values: List<SiteEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertEquipment(values: List<EquipmentEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPlans(values: List<ServicePlanEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertObligations(values: List<ServiceObligationEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTemplateSnapshots(values: List<TemplateSnapshotEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertChecklistItems(values: List<ChecklistItemSnapshotEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertVisits(values: List<WorkingVisitEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertWorkItems(values: List<WorkItemEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPublicDrafts(values: List<WorkItemPublicDraftEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPrivateDrafts(values: List<WorkItemPrivateDraftEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertResponses(values: List<WorkingResponseEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAttachments(values: List<AttachmentEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFollowUps(values: List<FollowUpEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBusinessProfile(value: BusinessProfileEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFinalRecord(value: FinalRecordEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFinalRevision(value: FinalRecordRevisionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFinalWorkItems(values: List<FinalWorkItemEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFinalChecklistItems(values: List<FinalChecklistItemEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReportRendition(value: ReportRenditionEntity)
    @Update suspend fun updateReportRendition(value: ReportRenditionEntity)

    @Query("SELECT COUNT(*) FROM customers") suspend fun customerCount(): Int
    @Query("SELECT * FROM customers WHERE id = :id") suspend fun customer(id: String): CustomerEntity?
    @Query("SELECT * FROM sites WHERE id = :id") suspend fun site(id: String): SiteEntity?
    @Query("SELECT * FROM equipment WHERE id = :id") suspend fun equipment(id: String): EquipmentEntity?
    @Query("SELECT * FROM service_plans WHERE id = :id") suspend fun plan(id: String): ServicePlanEntity?
    @Query("SELECT * FROM service_obligations WHERE id = :id") suspend fun obligation(id: String): ServiceObligationEntity?
    @Query("SELECT * FROM work_items WHERE id = :id") suspend fun workItem(id: String): WorkItemEntity?
    @Query("SELECT * FROM working_visits WHERE id = :id") suspend fun visit(id: String): WorkingVisitEntity?
    @Query("SELECT * FROM attachments WHERE id = :id") suspend fun attachment(id: String): AttachmentEntity?
    @Query("SELECT * FROM template_snapshots WHERE id = :id") suspend fun templateSnapshot(id: String): TemplateSnapshotEntity?
    @Query("SELECT * FROM business_profiles WHERE id='primary'") suspend fun businessProfile(): BusinessProfileEntity?
    @Query("SELECT * FROM final_records WHERE visitId=:visitId") suspend fun finalRecordForVisit(visitId: String): FinalRecordEntity?
    @Query("SELECT * FROM final_records WHERE id=:id") suspend fun finalRecord(id: String): FinalRecordEntity?
    @Query("SELECT * FROM final_record_revisions WHERE id=:id") suspend fun finalRevision(id: String): FinalRecordRevisionEntity?
    @Query("SELECT * FROM final_work_items WHERE revisionId=:revisionId ORDER BY position") suspend fun finalWorkItems(revisionId: String): List<FinalWorkItemEntity>
    @Query("SELECT * FROM final_checklist_items WHERE finalWorkItemId=:workItemId ORDER BY position") suspend fun finalChecklistItems(workItemId: String): List<FinalChecklistItemEntity>
    @Query("SELECT * FROM report_renditions WHERE revisionId=:revisionId AND versionNumber=1") suspend fun reportRendition(revisionId: String): ReportRenditionEntity?
    @Query("SELECT COUNT(*) FROM final_records") suspend fun finalRecordCount(): Int
    @Query("SELECT COUNT(*) FROM final_record_revisions") suspend fun finalRevisionCount(): Int
    @Query("SELECT COUNT(*) FROM service_obligations WHERE planId=:planId") suspend fun obligationCount(planId: String): Int
    @Query("UPDATE service_plans SET currentObligationId=:obligationId WHERE id=:planId") suspend fun setCurrentObligationForTest(planId: String, obligationId: String): Int
    @Query("UPDATE customers SET name = :name WHERE id = :id") suspend fun renameCustomer(id: String, name: String)
    @Query("UPDATE equipment SET name = :name WHERE id = :id") suspend fun renameEquipment(id: String, name: String)
    @Query("UPDATE equipment SET technicianIdentifier=:identifier, make=:make, model=:model, serialNumber=:serial WHERE id=:id") suspend fun updateEquipmentIdentity(id: String, identifier: String?, make: String?, model: String?, serial: String?)

    @Query("""
        SELECT e.id equipmentId, e.name equipmentName, e.reference equipmentReference,
               e.technicianIdentifier, e.make, e.model, e.serialNumber,
               s.name siteName, c.name customerName,
               p.id planId, p.name planName, p.reference planReference, p.intervalCount,
               p.intervalUnit, p.currentDueDate, p.state planState, p.currentObligationId
        FROM equipment e JOIN sites s ON s.id=e.siteId JOIN customers c ON c.id=s.customerId
        JOIN service_plans p ON p.equipmentId=e.id
        WHERE e.id=:equipmentId ORDER BY p.currentDueDate, p.name
    """)
    suspend fun equipmentPlans(equipmentId: String): List<EquipmentPlanRow>

    @Query("""
        SELECT wi.id workItemId, wi.visitId, v.reference visitReference, v.siteNameSnapshot,
               wi.equipmentNameSnapshot, wi.equipmentReferenceSnapshot, wi.serviceNameSnapshot,
               wi.dueDateSnapshot, wi.intervalCountSnapshot, wi.intervalUnitSnapshot,
               wi.templateSnapshotId, wi.checklistReviewed, wi.outcome, wi.fulfillsCurrentObligation,
               pub.workPerformed, priv.internalNote privateInternalNote, v.modifiedAtEpochMillis
        FROM work_items wi JOIN working_visits v ON v.id=wi.visitId
        JOIN work_item_public_drafts pub ON pub.workItemId=wi.id
        JOIN work_item_private_drafts priv ON priv.workItemId=wi.id
        WHERE wi.id=:workItemId
    """)
    suspend fun inspection(workItemId: String): InspectionRow?

    @Query("SELECT * FROM checklist_item_snapshots WHERE templateSnapshotId=:snapshotId ORDER BY position")
    suspend fun checklistItems(snapshotId: String): List<ChecklistItemSnapshotEntity>

    @Query("SELECT * FROM working_responses WHERE workItemId=:workItemId")
    suspend fun responses(workItemId: String): List<WorkingResponseEntity>

    @Query("SELECT * FROM work_items WHERE visitId=:visitId ORDER BY id")
    suspend fun visitWorkItems(visitId: String): List<WorkItemEntity>

    @Query("SELECT * FROM working_visits WHERE state='WORKING' ORDER BY modifiedAtEpochMillis DESC LIMIT 1")
    suspend fun latestWorkingVisit(): WorkingVisitEntity?

    @Query("SELECT * FROM working_visits WHERE state='BOOKED' ORDER BY actualServiceDate LIMIT 1")
    suspend fun nextBookedVisit(): WorkingVisitEntity?

    @Query("SELECT v.id, v.reference, v.siteNameSnapshot siteName, v.actualServiceDate, v.state, f.id finalRecordId, (SELECT wi.id FROM work_items wi WHERE wi.visitId=v.id ORDER BY wi.id LIMIT 1) resumeWorkItemId FROM working_visits v LEFT JOIN final_records f ON f.visitId=v.id ORDER BY v.actualServiceDate DESC, v.reference")
    suspend fun visits(): List<VisitSummaryRow>

    @Query("SELECT id FROM work_items WHERE visitId=:visitId ORDER BY id LIMIT 1")
    suspend fun firstWorkItemId(visitId: String): String?

    @Query("SELECT wi.id FROM work_items wi JOIN working_visits v ON v.id=wi.visitId WHERE wi.equipmentId=:equipmentId AND v.state='WORKING' ORDER BY v.modifiedAtEpochMillis DESC LIMIT 1")
    suspend fun workingItemId(equipmentId: String): String?

    @Query("SELECT * FROM follow_ups WHERE state='OPEN' AND dueDate <= :today ORDER BY dueDate, reference LIMIT 1")
    suspend fun firstDueFollowUp(today: String): FollowUpEntity?

    @Query("SELECT COUNT(*) FROM follow_ups WHERE state='OPEN' AND dueDate <= :today")
    suspend fun dueFollowUpCount(today: String): Int

    @Query("""
        SELECT e.id, e.name, e.reference, e.technicianIdentifier, s.name siteName, c.name customerName,
               MIN(CASE WHEN p.state='ACTIVE' THEN p.currentDueDate ELSE NULL END) nearestDueDate
        FROM equipment e JOIN sites s ON s.id=e.siteId JOIN customers c ON c.id=s.customerId
        LEFT JOIN service_plans p ON p.equipmentId=e.id
        GROUP BY e.id ORDER BY e.name
    """)
    suspend fun equipmentList(): List<EquipmentSummaryRow>

    @Query("""
        SELECT c.id, c.name, c.reference, COUNT(DISTINCT s.id) siteCount, COUNT(DISTINCT e.id) equipmentCount
        FROM customers c LEFT JOIN sites s ON s.customerId=c.id LEFT JOIN equipment e ON e.siteId=s.id
        GROUP BY c.id ORDER BY c.name
    """)
    suspend fun customerList(): List<CustomerSummaryRow>

    @Query("SELECT COUNT(*) FROM service_plans WHERE state='ACTIVE' AND currentDueDate < :today")
    suspend fun overdueCount(today: String): Int

    @Query("SELECT COUNT(*) FROM service_plans WHERE state='ACTIVE' AND currentDueDate BETWEEN :today AND :horizon")
    suspend fun dueSoonCount(today: String, horizon: String): Int

    @Query("UPDATE working_responses SET disposition=:disposition, textValue=:textValue, numberValue=:numberValue, reason=:reason, modifiedAtEpochMillis=:modified WHERE id=:id")
    suspend fun updateResponse(id: String, disposition: String, textValue: String?, numberValue: String?, reason: String?, modified: Long): Int

    @Query("UPDATE working_visits SET modifiedAtEpochMillis=:modified WHERE id=:visitId")
    suspend fun touchVisit(visitId: String, modified: Long)

    @Query("UPDATE work_item_public_drafts SET workPerformed=:text WHERE workItemId=:workItemId")
    suspend fun updatePublicWork(workItemId: String, text: String): Int

    @Query("UPDATE work_items SET checklistReviewed=:reviewed WHERE id=:workItemId")
    suspend fun updateChecklistReviewed(workItemId: String, reviewed: Boolean): Int

    @Query("UPDATE work_items SET outcome=:outcome, fulfillsCurrentObligation=:fulfills, notPerformedReason=:reason, confirmedNextDueDate=:nextDue, nextDueDateCalculated=:calculated, nextDueOverrideReason=:overrideReason WHERE id=:workItemId")
    suspend fun updateCompletionDraft(workItemId: String, outcome: String?, fulfills: Boolean, reason: String?, nextDue: String?, calculated: Boolean?, overrideReason: String?): Int

    @Query("UPDATE working_visits SET reportBusinessNameSnapshot=:businessName, reportTechnicianNameSnapshot=:technicianName, reportPhoneSnapshot=:phone, reportEmailSnapshot=:email, reportPostalAddressSnapshot=:address, reportZoneIdSnapshot=:zoneId, modifiedAtEpochMillis=:modified WHERE id=:visitId AND state='WORKING'")
    suspend fun updateVisitReportIdentity(visitId: String, businessName: String, technicianName: String, phone: String?, email: String?, address: String?, zoneId: String, modified: Long): Int

    @Query("UPDATE service_obligations SET consumedAtEpochMillis=:consumedAt, consumedByRevisionId=:revisionId WHERE id=:id AND planId=:planId AND consumedAtEpochMillis IS NULL")
    suspend fun consumeObligation(id: String, planId: String, consumedAt: Long, revisionId: String): Int

    @Query("UPDATE service_plans SET currentDueDate=:nextDue, currentObligationId=:nextObligationId, lastCountedCompletionDate=:completionDate, lastCountedRevisionId=:revisionId WHERE id=:planId AND state='ACTIVE' AND currentObligationId=:expectedObligationId")
    suspend fun advancePlan(planId: String, expectedObligationId: String, nextDue: String, nextObligationId: String, completionDate: String, revisionId: String): Int

    @Query("UPDATE working_visits SET state='FINALIZED', modifiedAtEpochMillis=:modified WHERE id=:visitId AND state='WORKING'")
    suspend fun finalizeVisit(visitId: String, modified: Long): Int

    @Transaction
    suspend fun persistResponse(response: WorkingResponseEntity, visitId: String, invalidateReview: Boolean = true) {
        val changed = updateResponse(response.id, response.disposition, response.textValue, response.numberValue, response.reason, response.modifiedAtEpochMillis)
        if (changed == 0) upsertResponses(listOf(response))
        if (invalidateReview) updateChecklistReviewed(response.workItemId, false)
        touchVisit(visitId, response.modifiedAtEpochMillis)
    }
}
