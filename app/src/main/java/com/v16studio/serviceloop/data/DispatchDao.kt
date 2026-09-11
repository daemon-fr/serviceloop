package com.v16studio.serviceloop.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface DispatchDao {
    @Query("SELECT * FROM technician_identity WHERE id='primary'") suspend fun technicianIdentity(): TechnicianIdentityEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTechnicianIdentity(value: TechnicianIdentityEntity)
    @Update suspend fun updateTechnicianIdentity(value: TechnicianIdentityEntity)

    @Query("SELECT * FROM dispatch_technicians ORDER BY displayName COLLATE NOCASE, technicianId") suspend fun technicians(): List<DispatchTechnicianEntity>
    @Query("SELECT * FROM dispatch_technicians WHERE technicianId=:id") suspend fun technician(id: String): DispatchTechnicianEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTechnician(value: DispatchTechnicianEntity)
    @Update suspend fun updateTechnician(value: DispatchTechnicianEntity)

    @Query("SELECT * FROM dispatch_teams ORDER BY name COLLATE NOCASE") suspend fun teams(): List<DispatchTeamEntity>
    @Query("SELECT * FROM dispatch_teams WHERE id=:id") suspend fun team(id: String): DispatchTeamEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTeam(value: DispatchTeamEntity)
    @Update suspend fun updateTeam(value: DispatchTeamEntity)
    @Query("SELECT * FROM dispatch_team_members WHERE teamId=:teamId") suspend fun teamMembers(teamId: String): List<DispatchTeamMemberEntity>
    @Query("SELECT * FROM dispatch_team_members") suspend fun allTeamMembers(): List<DispatchTeamMemberEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTeamMember(value: DispatchTeamMemberEntity)
    @Update suspend fun updateTeamMember(value: DispatchTeamMemberEntity)
    @Query("DELETE FROM dispatch_team_members WHERE teamId=:teamId AND technicianId=:technicianId") suspend fun removeTeamMember(teamId: String, technicianId: String)

    @Query("SELECT * FROM dispatch_outbox_visits ORDER BY serviceDate, createdAtEpochMillis") suspend fun outboxVisits(): List<DispatchOutboxVisitEntity>
    @Query("SELECT * FROM dispatch_outbox_visits WHERE dispatchVisitId=:id") suspend fun outboxVisit(id: String): DispatchOutboxVisitEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertOutboxVisit(value: DispatchOutboxVisitEntity)
    @Update suspend fun updateOutboxVisit(value: DispatchOutboxVisitEntity)
    @Query("SELECT * FROM dispatch_outbox_visit_teams WHERE dispatchVisitId=:visitId") suspend fun outboxVisitTeams(visitId: String): List<DispatchOutboxVisitTeamEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertOutboxVisitTeams(values: List<DispatchOutboxVisitTeamEntity>)
    @Query("DELETE FROM dispatch_outbox_visit_teams WHERE dispatchVisitId=:visitId") suspend fun clearOutboxVisitTeams(visitId: String)
    @Query("SELECT * FROM dispatch_outbox_items WHERE dispatchVisitId=:visitId ORDER BY position") suspend fun outboxItems(visitId: String): List<DispatchOutboxItemEntity>
    @Query("SELECT * FROM dispatch_outbox_items ORDER BY dispatchVisitId, position") suspend fun outboxItems(): List<DispatchOutboxItemEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertOutboxItem(value: DispatchOutboxItemEntity)
    @Update suspend fun updateOutboxItem(value: DispatchOutboxItemEntity)
    @Query("DELETE FROM dispatch_outbox_items WHERE dispatchItemId=:id") suspend fun deleteOutboxItem(id: String)
    @Query("SELECT * FROM dispatch_outbox_item_assignees WHERE dispatchItemId=:itemId") suspend fun outboxItemAssignees(itemId: String): List<DispatchOutboxItemAssigneeEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertOutboxItemAssignees(values: List<DispatchOutboxItemAssigneeEntity>)
    @Query("DELETE FROM dispatch_outbox_item_assignees WHERE dispatchItemId=:itemId") suspend fun clearOutboxItemAssignees(itemId: String)

    @Query("SELECT * FROM dispatch_visit_bindings WHERE dispatchVisitId=:id") suspend fun visitBinding(id: String): DispatchVisitBindingEntity?
    @Query("SELECT * FROM dispatch_visit_bindings WHERE localVisitId=:id") suspend fun visitBindingForLocalVisit(id: String): DispatchVisitBindingEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertVisitBinding(value: DispatchVisitBindingEntity)
    @Update suspend fun updateVisitBinding(value: DispatchVisitBindingEntity)
    @Query("SELECT * FROM dispatch_item_bindings WHERE dispatchVisitId=:visitId ORDER BY dispatchItemId") suspend fun itemBindings(visitId: String): List<DispatchItemBindingEntity>
    @Query("SELECT * FROM dispatch_item_bindings WHERE dispatchVisitId=:visitId AND dispatchItemId=:itemId") suspend fun itemBinding(visitId: String, itemId: String): DispatchItemBindingEntity?
    @Query("SELECT * FROM dispatch_item_bindings WHERE localWorkItemId=:workItemId") suspend fun itemBindingForWorkItem(workItemId: String): DispatchItemBindingEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertItemBindings(values: List<DispatchItemBindingEntity>)
    @Update suspend fun updateItemBinding(value: DispatchItemBindingEntity)
    @Query("DELETE FROM dispatch_item_bindings WHERE dispatchVisitId=:visitId AND dispatchItemId=:itemId") suspend fun deleteItemBinding(visitId: String, itemId: String)
    @Query("DELETE FROM work_items WHERE id=:id") suspend fun deleteWorkItem(id: String)
    @Query("UPDATE working_visits SET actualServiceDate=:date, scheduledAtEpochMillis=:scheduled, appointmentZoneId=:zone, modifiedAtEpochMillis=:now WHERE id=:id AND state='BOOKED'") suspend fun updateBookedDispatchVisit(id: String, date: String, scheduled: Long?, zone: String, now: Long): Int
    @Query("UPDATE work_items SET equipmentId=:equipmentId, equipmentNameSnapshot=:equipmentName, equipmentReferenceSnapshot=:equipmentReference, equipmentIdentifierSnapshot=:identifier, equipmentMakeSnapshot=:make, equipmentModelSnapshot=:model, equipmentSerialSnapshot=:serial, serviceNameSnapshot=:task, servicePlanId=NULL, capturedObligationId=NULL, planReferenceSnapshot=NULL, dueDateSnapshot=NULL WHERE id=:id") suspend fun updateBookedDispatchWork(id: String, equipmentId: String, equipmentName: String, equipmentReference: String, identifier: String?, make: String?, model: String?, serial: String?, task: String): Int
    @Query("UPDATE work_items SET servicePlanId=:planId, capturedObligationId=:obligationId, planReferenceSnapshot=:planReference, dueDateSnapshot=:dueDate, intervalCountSnapshot=:intervalCount, intervalUnitSnapshot=:intervalUnit WHERE id=:id") suspend fun linkDispatchWork(id: String, planId: String, obligationId: String, planReference: String, dueDate: String, intervalCount: Int, intervalUnit: String): Int
    @Query("UPDATE work_items SET servicePlanId=NULL, capturedObligationId=NULL, planReferenceSnapshot=NULL, dueDateSnapshot=NULL, intervalCountSnapshot=NULL, intervalUnitSnapshot=NULL, fulfillsCurrentObligation=0, confirmedNextDueDate=NULL, nextDueDateCalculated=NULL, nextDueOverrideReason=NULL WHERE id=:id") suspend fun unlinkDispatchWork(id: String): Int
    @Query("UPDATE working_visits SET state='COMPLETED', modifiedAtEpochMillis=:now WHERE id=:id AND state='WORKING'") suspend fun completeParticipation(id: String, now: Long): Int
    @Query("UPDATE working_visits SET state='CANCELED', cancellationOrigin='ASSIGNMENT_REMOVAL', modifiedAtEpochMillis=:now WHERE id=:id AND state IN ('BOOKED','WORKING')") suspend fun withdrawDispatchVisit(id: String, now: Long): Int
    @Query("DELETE FROM visit_claims WHERE visitId=:visitId AND obligationId=:obligationId") suspend fun releaseItemClaim(visitId: String, obligationId: String): Int

    @Query("SELECT COUNT(*) FROM working_responses WHERE workItemId=:id") suspend fun responseCount(id: String): Int
    @Query("SELECT COUNT(*) FROM part_entries WHERE workItemId=:id") suspend fun partCount(id: String): Int
    @Query("SELECT COUNT(*) FROM attachments WHERE ownerType='WORK_ITEM' AND ownerId=:id") suspend fun attachmentCount(id: String): Int
    @Query("SELECT * FROM attachments WHERE ownerType='WORK_ITEM' AND ownerId=:id") suspend fun workAttachments(id: String): List<AttachmentEntity>
    @Query("DELETE FROM working_responses WHERE workItemId=:id") suspend fun deleteResponses(id: String)
    @Query("DELETE FROM part_entries WHERE workItemId=:id") suspend fun deleteParts(id: String)
    @Query("DELETE FROM attachments WHERE ownerType='WORK_ITEM' AND ownerId=:id") suspend fun deleteWorkAttachments(id: String)
    @Query("UPDATE work_item_public_drafts SET workPerformed='' WHERE workItemId=:id") suspend fun clearPublicDraft(id: String)
    @Query("UPDATE work_item_private_drafts SET internalNote='' WHERE workItemId=:id") suspend fun clearPrivateDraft(id: String)
    @Query("UPDATE work_item_private_drafts SET internalNote=:note WHERE workItemId=:id") suspend fun updatePrivateDraftNote(id: String, note: String)
    @Query("UPDATE work_items SET checklistReviewed=0, outcome=NULL, fulfillsCurrentObligation=NULL, notPerformedReason=NULL, confirmedNextDueDate=NULL, nextDueDateCalculated=NULL, nextDueOverrideReason=NULL WHERE id=:id") suspend fun clearCompletionDraft(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFinalDispatchVisit(value: FinalDispatchVisitEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFinalDispatchItems(values: List<FinalDispatchItemEntity>)
    @Query("SELECT * FROM final_dispatch_visits WHERE revisionId=:revisionId") suspend fun finalDispatchVisit(revisionId: String): FinalDispatchVisitEntity?
    @Query("SELECT * FROM final_dispatch_items WHERE finalWorkItemId IN (SELECT id FROM final_work_items WHERE revisionId=:revisionId)") suspend fun finalDispatchItems(revisionId: String): List<FinalDispatchItemEntity>
}
