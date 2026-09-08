package com.v16studio.serviceloop.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

data class DueServiceRow(
    val planId: String, val planReference: String, val planName: String, val dueDate: String,
    val obligationId: String, val equipmentId: String, val equipmentReference: String,
    val equipmentName: String, val siteId: String, val siteName: String,
    val customerId: String, val customerName: String, val claimedVisitId: String?,
)

data class SearchRow(val type: String, val id: String, val reference: String, val title: String, val subtitle: String)

    data class VisitSiteRow(val id: String, val reference: String, val name: String, val customerName: String, val address: String?)

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
    @Query("DELETE FROM attachments WHERE id=:id") suspend fun deleteAttachment(id: String): Int
    @Query("SELECT * FROM attachments WHERE ownerType=:ownerType AND ownerId=:ownerId ORDER BY id") suspend fun attachmentsForOwner(ownerType: String, ownerId: String): List<AttachmentEntity>
    @Query("DELETE FROM attachments WHERE ownerType=:ownerType AND ownerId=:ownerId") suspend fun deleteAttachmentsForOwner(ownerType: String, ownerId: String): Int
    @Query("UPDATE attachments SET ownerType=:newOwnerType, ownerId=:newOwnerId, includedInCustomerReport=:included WHERE id=:id AND ownerType=:expectedOwnerType AND ownerId=:expectedOwnerId")
    suspend fun reparentAttachment(id: String, expectedOwnerType: String, expectedOwnerId: String, newOwnerType: String, newOwnerId: String, included: Boolean): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFollowUps(values: List<FollowUpEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBusinessProfile(value: BusinessProfileEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFinalRecord(value: FinalRecordEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFinalRevision(value: FinalRecordRevisionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFinalWorkItems(values: List<FinalWorkItemEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFinalChecklistItems(values: List<FinalChecklistItemEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReportRendition(value: ReportRenditionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReusableTemplate(value: ReusableTemplateEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReusableTemplateRevision(value: ReusableTemplateRevisionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReusableTemplateItems(values: List<ReusableTemplateItemEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertContactNote(value: ContactNoteEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFollowUp(value: FollowUpEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFollowUpEvent(value: FollowUpEventEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPart(value: PartEntryEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertVisitClaim(value: VisitClaimEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFinalParts(values: List<FinalPartEntryEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFinalPhotos(values: List<FinalPhotoEntryEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPlanScheduleChange(value: PlanScheduleChangeEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertVisitScheduleEvent(value: VisitScheduleEventEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCorrectionDraft(value: CorrectionDraftEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCorrectionWorkItems(values: List<CorrectionWorkItemEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertChangeEntry(value: ChangeEntryEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertEquipmentMove(value: EquipmentMoveEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertRecoveryMetadata(value: RecoveryMetadataEntity)
    @Update suspend fun updateReportRendition(value: ReportRenditionEntity)
    @Update suspend fun updateCustomer(value: CustomerEntity)
    @Update suspend fun updateSite(value: SiteEntity)
    @Update suspend fun updateEquipment(value: EquipmentEntity)
    @Update suspend fun updatePlan(value: ServicePlanEntity)
    @Update suspend fun updateVisit(value: WorkingVisitEntity)
    @Update suspend fun updateFollowUp(value: FollowUpEntity)
    @Update suspend fun updateCorrectionDraft(value: CorrectionDraftEntity)
    @Update suspend fun updateCorrectionWorkItem(value: CorrectionWorkItemEntity)

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
    @Query("SELECT * FROM report_renditions WHERE revisionId=:revisionId ORDER BY versionNumber DESC LIMIT 1") suspend fun reportRendition(revisionId: String): ReportRenditionEntity?
    @Query("SELECT * FROM report_renditions WHERE revisionId=:revisionId ORDER BY versionNumber") suspend fun reportRenditions(revisionId: String): List<ReportRenditionEntity>
    @Query("SELECT * FROM report_renditions WHERE id=:id") suspend fun reportRenditionById(id: String): ReportRenditionEntity?
    @Query("SELECT * FROM report_renditions ORDER BY generatedAtEpochMillis, id") suspend fun allReportRenditions(): List<ReportRenditionEntity>
    @Query("SELECT * FROM final_record_revisions WHERE recordId=:recordId ORDER BY revisionNumber") suspend fun finalRevisions(recordId: String): List<FinalRecordRevisionEntity>
    @Query("SELECT * FROM sites WHERE customerId=:customerId ORDER BY isDefault DESC, name, reference") suspend fun sitesForCustomer(customerId: String): List<SiteEntity>
    @Query("SELECT s.id, s.reference, s.name, c.name customerName, s.address FROM sites s JOIN customers c ON c.id=s.customerId WHERE s.state='ACTIVE' AND c.state='ACTIVE' ORDER BY c.name, s.name, s.reference") suspend fun activeVisitSites(): List<VisitSiteRow>
    @Query("SELECT * FROM equipment WHERE siteId=:siteId ORDER BY name, reference") suspend fun equipmentForSite(siteId: String): List<EquipmentEntity>
    @Query("SELECT * FROM service_plans WHERE equipmentId=:equipmentId ORDER BY currentDueDate, reference") suspend fun plansForEquipment(equipmentId: String): List<ServicePlanEntity>
    @Query("SELECT * FROM follow_ups WHERE customerId=:customerId ORDER BY CASE state WHEN 'OPEN' THEN 0 ELSE 1 END, dueDate, reference") suspend fun followUpsForCustomer(customerId: String): List<FollowUpEntity>
    @Query("SELECT * FROM follow_ups ORDER BY CASE state WHEN 'OPEN' THEN 0 ELSE 1 END, dueDate, reference") suspend fun followUps(): List<FollowUpEntity>
    @Query("SELECT * FROM follow_ups WHERE id=:id") suspend fun followUp(id: String): FollowUpEntity?
    @Query("SELECT * FROM contact_notes WHERE customerId=:customerId ORDER BY occurredAtEpochMillis DESC, reference") suspend fun contactNotesForCustomer(customerId: String): List<ContactNoteEntity>
    @Query("SELECT * FROM contact_notes WHERE id=:id") suspend fun contactNote(id: String): ContactNoteEntity?
    @Query("SELECT * FROM reusable_templates ORDER BY name, reference") suspend fun reusableTemplates(): List<ReusableTemplateEntity>
    @Query("SELECT * FROM reusable_templates WHERE id=:id") suspend fun reusableTemplate(id: String): ReusableTemplateEntity?
    @Query("SELECT * FROM reusable_template_revisions WHERE id=:id") suspend fun reusableTemplateRevision(id: String): ReusableTemplateRevisionEntity?
    @Query("SELECT * FROM reusable_template_revisions WHERE templateId=:templateId ORDER BY revisionNumber DESC") suspend fun reusableTemplateRevisions(templateId: String): List<ReusableTemplateRevisionEntity>
    @Query("SELECT * FROM reusable_template_items WHERE revisionId=:revisionId ORDER BY position") suspend fun reusableTemplateItems(revisionId: String): List<ReusableTemplateItemEntity>
    @Query("SELECT * FROM part_entries WHERE workItemId=:workItemId ORDER BY modifiedAtEpochMillis, id") suspend fun parts(workItemId: String): List<PartEntryEntity>
    @Query("SELECT * FROM attachments WHERE ownerType='WORK_ITEM' AND ownerId=:workItemId ORDER BY id") suspend fun workItemAttachments(workItemId: String): List<AttachmentEntity>
    @Query("SELECT COUNT(*) FROM attachments a JOIN work_items wi ON a.ownerType='WORK_ITEM' AND a.ownerId=wi.id WHERE wi.visitId=:visitId AND wi.equipmentId=:equipmentId") suspend fun machinePhotoCount(visitId: String, equipmentId: String): Int
    @Query("SELECT COUNT(*) FROM attachments a JOIN work_items wi ON a.ownerType='WORK_ITEM' AND a.ownerId=wi.id WHERE wi.visitId=:visitId") suspend fun visitPhotoCount(visitId: String): Int
    @Query("SELECT * FROM final_part_entries WHERE finalWorkItemId=:finalWorkItemId ORDER BY position") suspend fun finalParts(finalWorkItemId: String): List<FinalPartEntryEntity>
    @Query("SELECT * FROM final_photo_entries WHERE finalWorkItemId=:finalWorkItemId ORDER BY position") suspend fun finalPhotos(finalWorkItemId: String): List<FinalPhotoEntryEntity>
    @Query("SELECT COUNT(*) FROM final_records") suspend fun finalRecordCount(): Int
    @Query("SELECT COUNT(*) FROM final_record_revisions") suspend fun finalRevisionCount(): Int
    @Query("SELECT COUNT(*) FROM template_snapshots") suspend fun templateSnapshotCount(): Int
    @Query("SELECT COUNT(*) FROM checklist_item_snapshots") suspend fun checklistSnapshotItemCount(): Int
    @Query("SELECT COUNT(*) FROM service_obligations WHERE planId=:planId") suspend fun obligationCount(planId: String): Int
    @Query("SELECT COUNT(*) FROM sites") suspend fun siteCount(): Int
    @Query("SELECT COUNT(*) FROM equipment") suspend fun equipmentCount(): Int
    @Query("SELECT COUNT(*) FROM service_plans") suspend fun planCount(): Int
    @Query("SELECT COUNT(*) FROM working_visits") suspend fun visitCount(): Int
    @Query("SELECT COUNT(*) FROM reusable_templates") suspend fun reusableTemplateCount(): Int
    @Query("SELECT COUNT(*) FROM follow_ups") suspend fun followUpCount(): Int
    @Query("SELECT COUNT(*) FROM contact_notes") suspend fun contactNoteCount(): Int
    @Query("SELECT * FROM visit_schedule_events WHERE visitId=:visitId ORDER BY occurredAtEpochMillis, rowid") suspend fun visitScheduleEvents(visitId: String): List<VisitScheduleEventEntity>
    @Query("SELECT * FROM plan_schedule_changes ORDER BY changedAtEpochMillis, id") suspend fun allPlanScheduleChanges(): List<PlanScheduleChangeEntity>
    @Query("SELECT * FROM visit_schedule_events ORDER BY occurredAtEpochMillis, id") suspend fun allVisitScheduleEvents(): List<VisitScheduleEventEntity>
    @Query("SELECT * FROM contact_notes ORDER BY occurredAtEpochMillis, id") suspend fun allContactNotes(): List<ContactNoteEntity>
    @Query("SELECT * FROM follow_up_events ORDER BY occurredAtEpochMillis, id") suspend fun allFollowUpEvents(): List<FollowUpEventEntity>
    @Query("SELECT * FROM change_entries ORDER BY recordedAtEpochMillis, id") suspend fun allChangeEntries(): List<ChangeEntryEntity>
    @Query("SELECT * FROM change_entries WHERE id=:id") suspend fun changeEntry(id: String): ChangeEntryEntity?
    @Query("SELECT * FROM correction_drafts WHERE recordId=:recordId") suspend fun correctionDraftForRecord(recordId: String): CorrectionDraftEntity?
    @Query("SELECT * FROM correction_drafts WHERE id=:id") suspend fun correctionDraft(id: String): CorrectionDraftEntity?
    @Query("SELECT * FROM correction_drafts ORDER BY modifiedAtEpochMillis DESC") suspend fun correctionDrafts(): List<CorrectionDraftEntity>
    @Query("SELECT * FROM correction_work_items WHERE draftId=:draftId ORDER BY position") suspend fun correctionWorkItems(draftId: String): List<CorrectionWorkItemEntity>
    @Query("SELECT * FROM correction_work_items WHERE id=:id") suspend fun correctionWorkItem(id: String): CorrectionWorkItemEntity?
    @Query("SELECT COUNT(*) FROM correction_drafts cd JOIN correction_work_items cw ON cw.draftId=cd.id JOIN final_work_items fw ON fw.id=cw.sourceFinalWorkItemId WHERE fw.equipmentId=:equipmentId") suspend fun correctionDraftCountForEquipment(equipmentId: String): Int
    @Query("SELECT cd.recordId FROM correction_drafts cd JOIN correction_work_items cw ON cw.draftId=cd.id JOIN final_work_items fw ON fw.id=cw.sourceFinalWorkItemId WHERE fw.equipmentId=:equipmentId LIMIT 1") suspend fun correctionRecordForEquipment(equipmentId: String): String?
    @Query("SELECT * FROM recovery_metadata WHERE id='primary'") suspend fun recoveryMetadata(): RecoveryMetadataEntity?
    @Query("SELECT * FROM customers ORDER BY reference") suspend fun allCustomers(): List<CustomerEntity>
    @Query("SELECT * FROM sites ORDER BY reference") suspend fun allSites(): List<SiteEntity>
    @Query("SELECT * FROM equipment ORDER BY reference") suspend fun allEquipment(): List<EquipmentEntity>
    @Query("SELECT * FROM service_plans ORDER BY reference") suspend fun allPlans(): List<ServicePlanEntity>
    @Query("SELECT * FROM working_visits ORDER BY actualServiceDate, reference") suspend fun allVisits(): List<WorkingVisitEntity>
    @Query("SELECT * FROM final_records ORDER BY createdAtEpochMillis") suspend fun allFinalRecords(): List<FinalRecordEntity>
    @Query("SELECT * FROM follow_ups ORDER BY reference") suspend fun allFollowUps(): List<FollowUpEntity>
    @Query("SELECT * FROM attachments ORDER BY id") suspend fun allAttachments(): List<AttachmentEntity>
    @Query("SELECT visitId FROM visit_claims WHERE obligationId=:obligationId LIMIT 1") suspend fun claimForObligation(obligationId: String): String?
    @Query("UPDATE service_plans SET currentObligationId=:obligationId WHERE id=:planId") suspend fun setCurrentObligationForTest(planId: String, obligationId: String): Int
    @Query("UPDATE service_obligations SET dueDate=:dueDate WHERE id=:id AND consumedAtEpochMillis IS NULL") suspend fun updateCurrentObligationDueDate(id: String, dueDate: String): Int
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

    @Query("SELECT COUNT(*) FROM working_visits WHERE state='WORKING'")
    suspend fun workingVisitCount(): Int

    @Query("SELECT * FROM working_visits WHERE state='BOOKED' ORDER BY actualServiceDate LIMIT 1")
    suspend fun nextBookedVisit(): WorkingVisitEntity?

    @Query("SELECT COUNT(*) FROM working_visits WHERE state='BOOKED'")
    suspend fun bookedVisitCount(): Int

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

    @Query("""
        SELECT p.id planId, p.reference planReference, p.name planName, p.currentDueDate dueDate,
               o.id obligationId, e.id equipmentId, e.reference equipmentReference, e.name equipmentName,
               s.id siteId, s.name siteName, c.id customerId, c.name customerName, vc.visitId claimedVisitId
        FROM service_plans p JOIN service_obligations o ON o.id=p.currentObligationId
        JOIN equipment e ON e.id=p.equipmentId JOIN sites s ON s.id=e.siteId JOIN customers c ON c.id=s.customerId
        LEFT JOIN visit_claims vc ON vc.obligationId=o.id
        WHERE p.state='ACTIVE' AND e.state='ACTIVE' AND s.state='ACTIVE' AND c.state='ACTIVE'
          AND o.consumedAtEpochMillis IS NULL
        ORDER BY p.currentDueDate, c.name, s.name, e.name, p.reference
    """)
    suspend fun dueServices(): List<DueServiceRow>

    @Query("""
        SELECT p.id planId, p.reference planReference, p.name planName, p.currentDueDate dueDate,
               o.id obligationId, e.id equipmentId, e.reference equipmentReference, e.name equipmentName,
               s.id siteId, s.name siteName, c.id customerId, c.name customerName, vc.visitId claimedVisitId
        FROM service_plans p JOIN service_obligations o ON o.id=p.currentObligationId
        JOIN equipment e ON e.id=p.equipmentId JOIN sites s ON s.id=e.siteId JOIN customers c ON c.id=s.customerId
        LEFT JOIN visit_claims vc ON vc.obligationId=o.id
        WHERE p.state='ACTIVE' AND e.state='ACTIVE' AND s.state='ACTIVE' AND c.state='ACTIVE'
          AND o.consumedAtEpochMillis IS NULL
        ORDER BY p.currentDueDate, c.name, s.name, e.name, p.reference
    """)
    fun observeDueServices(): Flow<List<DueServiceRow>>

    @Query("""
        SELECT 'CUSTOMER' type, id, reference, name title, COALESCE(contactName,'') subtitle FROM customers WHERE name LIKE :pattern OR reference LIKE :pattern OR COALESCE(contactName,'') LIKE :pattern
        UNION ALL SELECT 'SITE', s.id, s.reference, s.name, c.name FROM sites s JOIN customers c ON c.id=s.customerId WHERE s.name LIKE :pattern OR s.reference LIKE :pattern OR COALESCE(s.address,'') LIKE :pattern
        UNION ALL SELECT 'EQUIPMENT', e.id, e.reference, e.name, c.name || ' · ' || s.name FROM equipment e JOIN sites s ON s.id=e.siteId JOIN customers c ON c.id=s.customerId WHERE e.name LIKE :pattern OR e.reference LIKE :pattern OR COALESCE(e.technicianIdentifier,'') LIKE :pattern OR COALESCE(e.serialNumber,'') LIKE :pattern OR COALESCE(e.make,'') LIKE :pattern OR COALESCE(e.model,'') LIKE :pattern
        UNION ALL SELECT 'PLAN', p.id, p.reference, p.name, e.name FROM service_plans p JOIN equipment e ON e.id=p.equipmentId WHERE p.name LIKE :pattern OR p.reference LIKE :pattern
        UNION ALL SELECT CASE v.state WHEN 'FINALIZED' THEN 'FINAL_RECORD' ELSE 'VISIT' END, COALESCE(f.id,v.id), v.reference, v.siteNameSnapshot, v.state FROM working_visits v LEFT JOIN final_records f ON f.visitId=v.id WHERE v.reference LIKE :pattern OR v.customerNameSnapshot LIKE :pattern OR v.siteNameSnapshot LIKE :pattern
        UNION ALL SELECT 'FOLLOW_UP', fu.id, fu.reference, fu.title, fu.state FROM follow_ups fu WHERE fu.reference LIKE :pattern OR fu.title LIKE :pattern
        ORDER BY reference, title
    """)
    suspend fun search(pattern: String): List<SearchRow>

    @Query("SELECT COUNT(*) FROM service_plans WHERE state='ACTIVE' AND currentDueDate < :today")
    suspend fun overdueCount(today: String): Int

    @Query("SELECT COUNT(*) FROM service_plans WHERE state='ACTIVE' AND currentDueDate BETWEEN :today AND :horizon")
    suspend fun dueSoonCount(today: String, horizon: String): Int

    @Query("UPDATE working_responses SET disposition=:disposition, textValue=:textValue, numberValue=:numberValue, reason=:reason, modifiedAtEpochMillis=:modified WHERE id=:id")
    suspend fun updateResponse(id: String, disposition: String, textValue: String?, numberValue: String?, reason: String?, modified: Long): Int

    @Query("UPDATE working_visits SET modifiedAtEpochMillis=:modified WHERE id=:visitId")
    suspend fun touchVisit(visitId: String, modified: Long)

    @Query("UPDATE work_items SET templateSnapshotId=:snapshotId, equipmentNameSnapshot=:equipmentName, equipmentReferenceSnapshot=:equipmentReference, equipmentIdentifierSnapshot=:identifier, equipmentMakeSnapshot=:make, equipmentModelSnapshot=:model, equipmentSerialSnapshot=:serial, serviceNameSnapshot=:serviceName, planReferenceSnapshot=:planReference, dueDateSnapshot=:dueDate, intervalCountSnapshot=:intervalCount, intervalUnitSnapshot=:intervalUnit WHERE id=:workItemId")
    suspend fun refreshWorkItemSnapshot(workItemId: String, snapshotId: String?, equipmentName: String, equipmentReference: String, identifier: String?, make: String?, model: String?, serial: String?, serviceName: String, planReference: String?, dueDate: String?, intervalCount: Int?, intervalUnit: String?): Int

    @Query("UPDATE working_visits SET state='WORKING', actualServiceDate=:serviceDate, customerNameSnapshot=:customerName, customerReferenceSnapshot=:customerReference, siteNameSnapshot=:siteName, siteReferenceSnapshot=:siteReference, siteAddressSnapshot=:siteAddress, reportBusinessNameSnapshot=:businessName, reportTechnicianNameSnapshot=:technicianName, reportPhoneSnapshot=:phone, reportEmailSnapshot=:email, reportPostalAddressSnapshot=:address, reportZoneIdSnapshot=:zoneId, modifiedAtEpochMillis=:modified WHERE id=:visitId AND state='BOOKED'")
    suspend fun startBookedVisit(visitId: String, serviceDate: String, customerName: String, customerReference: String, siteName: String, siteReference: String, siteAddress: String?, businessName: String?, technicianName: String?, phone: String?, email: String?, address: String?, zoneId: String?, modified: Long): Int

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

    @Query("DELETE FROM visit_claims WHERE visitId=:visitId") suspend fun releaseVisitClaims(visitId: String): Int
    @Query("SELECT COUNT(*) FROM visit_claims WHERE visitId=:visitId AND obligationId=:obligationId") suspend fun visitOwnsClaim(visitId: String, obligationId: String): Int
    @Query("UPDATE reusable_templates SET name=:name, currentRevisionId=:revisionId, modifiedAtEpochMillis=:modified WHERE id=:id") suspend fun publishTemplateRevision(id: String, name: String, revisionId: String, modified: Long): Int
    @Query("UPDATE contact_notes SET enteredInError=1, errorReason=:reason, editedAtEpochMillis=:modified WHERE id=:id AND enteredInError=0") suspend fun markContactNoteEnteredInError(id: String, reason: String, modified: Long): Int
    @Query("UPDATE final_records SET currentRevisionId=:revisionId WHERE id=:recordId AND currentRevisionId=:expectedRevisionId AND voided=0") suspend fun advanceRecordRevision(recordId: String, expectedRevisionId: String, revisionId: String): Int
    @Query("UPDATE final_records SET voided=1, voidedAtEpochMillis=:at, publicVoidReason=:publicReason, privateVoidReason=:privateReason WHERE id=:recordId AND voided=0") suspend fun markRecordVoided(recordId: String, at: Long, publicReason: String, privateReason: String?): Int
    @Query("DELETE FROM correction_drafts WHERE id=:id") suspend fun deleteCorrectionDraft(id: String): Int
    @Query("UPDATE equipment SET siteId=:siteId WHERE id=:id AND siteId=:expectedSiteId AND state='ACTIVE'") suspend fun moveEquipment(id: String, expectedSiteId: String, siteId: String): Int
    @Query("UPDATE customers SET state=:state WHERE id=:id AND state=:expected") suspend fun changeCustomerState(id: String, expected: String, state: String): Int
    @Query("UPDATE sites SET state=:state WHERE id=:id AND state=:expected") suspend fun changeSiteState(id: String, expected: String, state: String): Int
    @Query("UPDATE equipment SET state=:state WHERE id=:id AND state=:expected") suspend fun changeEquipmentState(id: String, expected: String, state: String): Int
    @Query("UPDATE service_plans SET state=:state, currentObligationId=:obligationId WHERE id=:id AND state=:expected") suspend fun changePlanState(id: String, expected: String, state: String, obligationId: String?): Int
    @Query("SELECT COUNT(*) FROM sites WHERE customerId=:customerId AND state='ACTIVE'") suspend fun activeSiteCount(customerId: String): Int
    @Query("SELECT COUNT(*) FROM equipment WHERE siteId=:siteId AND state='ACTIVE'") suspend fun activeEquipmentCount(siteId: String): Int
    @Query("SELECT COUNT(*) FROM service_plans WHERE equipmentId=:equipmentId AND state!='ENDED'") suspend fun nonEndedPlanCount(equipmentId: String): Int
    @Query("SELECT COUNT(*) FROM follow_ups WHERE customerId=:customerId AND siteId IS NULL AND state='OPEN'") suspend fun openCustomerFollowUpCount(customerId: String): Int
    @Query("SELECT COUNT(*) FROM follow_ups WHERE siteId=:siteId AND state='OPEN'") suspend fun openSiteFollowUpCount(siteId: String): Int
    @Query("SELECT COUNT(*) FROM follow_ups WHERE equipmentId=:equipmentId AND state='OPEN'") suspend fun openEquipmentFollowUpCount(equipmentId: String): Int
    @Query("SELECT COUNT(*) FROM working_visits WHERE customerId=:customerId AND state IN ('BOOKED','WORKING')") suspend fun activeVisitCountForCustomer(customerId: String): Int
    @Query("SELECT COUNT(*) FROM working_visits WHERE siteId=:siteId AND state IN ('BOOKED','WORKING')") suspend fun activeVisitCountForSite(siteId: String): Int
    @Query("SELECT COUNT(*) FROM work_items wi JOIN working_visits v ON v.id=wi.visitId WHERE wi.equipmentId=:equipmentId AND v.state IN ('BOOKED','WORKING')") suspend fun activeVisitCountForEquipment(equipmentId: String): Int
    @Query("SELECT COUNT(*) FROM visit_claims vc JOIN service_obligations o ON o.id=vc.obligationId WHERE o.planId=:planId") suspend fun activeClaimCountForPlan(planId: String): Int
    @Query("SELECT COUNT(*) FROM sites WHERE customerId=:customerId AND isDefault=1") suspend fun defaultSiteCount(customerId: String): Int
    @Query("UPDATE sites SET isDefault=0 WHERE id=:siteId") suspend fun clearDefaultSite(siteId: String): Int
    @Query("UPDATE service_plans SET currentDueDate=:dueDate, lastCountedCompletionDate=:completionDate, lastCountedRevisionId=:revisionId WHERE id=:planId AND lastCountedRevisionId=:expectedRevisionId") suspend fun reconcileLatestPlan(planId: String, expectedRevisionId: String, dueDate: String, completionDate: String?, revisionId: String?): Int

    @Transaction
    suspend fun persistResponse(response: WorkingResponseEntity, visitId: String, invalidateReview: Boolean = true) {
        val changed = updateResponse(response.id, response.disposition, response.textValue, response.numberValue, response.reason, response.modifiedAtEpochMillis)
        if (changed == 0) upsertResponses(listOf(response))
        if (invalidateReview) updateChecklistReviewed(response.workItemId, false)
        touchVisit(visitId, response.modifiedAtEpochMillis)
    }
}
