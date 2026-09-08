package com.v16studio.serviceloop.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CustomerEntity::class, SiteEntity::class, EquipmentEntity::class,
        ServicePlanEntity::class, ServiceObligationEntity::class,
        TemplateSnapshotEntity::class, ChecklistItemSnapshotEntity::class,
        WorkingVisitEntity::class, WorkItemEntity::class,
        WorkItemPublicDraftEntity::class, WorkItemPrivateDraftEntity::class,
        WorkingResponseEntity::class, AttachmentEntity::class,
        FollowUpEntity::class,
        BusinessProfileEntity::class, FinalRecordEntity::class,
        FinalRecordRevisionEntity::class, FinalWorkItemEntity::class,
        FinalChecklistItemEntity::class, ReportRenditionEntity::class,
        ReusableTemplateEntity::class, ReusableTemplateRevisionEntity::class,
        ReusableTemplateItemEntity::class, ContactNoteEntity::class,
        FollowUpEventEntity::class, PartEntryEntity::class,
        VisitClaimEntity::class, FinalPartEntryEntity::class, FinalPhotoEntryEntity::class,
        PlanScheduleChangeEntity::class,
        VisitScheduleEventEntity::class,
        CorrectionDraftEntity::class, CorrectionWorkItemEntity::class,
        ChangeEntryEntity::class, EquipmentMoveEntity::class, RecoveryMetadataEntity::class,
        TechnicianIdentityEntity::class, DispatchTechnicianEntity::class,
        DispatchTeamEntity::class, DispatchTeamMemberEntity::class,
        DispatchOutboxVisitEntity::class, DispatchOutboxVisitTeamEntity::class,
        DispatchOutboxItemEntity::class, DispatchOutboxItemAssigneeEntity::class,
        DispatchVisitBindingEntity::class, DispatchItemBindingEntity::class,
        FinalDispatchVisitEntity::class, FinalDispatchItemEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class ServiceLoopDatabase : RoomDatabase() {
    abstract fun serviceLoopDao(): ServiceLoopDao
    abstract fun dispatchDao(): DispatchDao

    companion object {
        fun open(context: Context): ServiceLoopDatabase = Room.databaseBuilder(
            context.applicationContext,
            ServiceLoopDatabase::class.java,
            "serviceloop.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    configureDispatchIdentity(db)
                    configureStage4Tracking(db)
                }
            })
            .build().also { database -> RecoveryPackage.recoverInterrupted(database, context.applicationContext.filesDir) }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE service_plans ADD COLUMN lastCountedCompletionDate TEXT")
                db.execSQL("ALTER TABLE service_plans ADD COLUMN lastCountedRevisionId TEXT")
                db.execSQL("ALTER TABLE service_obligations ADD COLUMN consumedByRevisionId TEXT")
                db.execSQL("ALTER TABLE work_items ADD COLUMN notPerformedReason TEXT")
                db.execSQL("ALTER TABLE work_items ADD COLUMN confirmedNextDueDate TEXT")
                db.execSQL("ALTER TABLE work_items ADD COLUMN nextDueDateCalculated INTEGER")
                db.execSQL("ALTER TABLE work_items ADD COLUMN nextDueOverrideReason TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS business_profiles (id TEXT NOT NULL PRIMARY KEY, businessName TEXT NOT NULL, technicianName TEXT NOT NULL, phone TEXT, email TEXT, postalAddress TEXT, zoneId TEXT NOT NULL, modifiedAtEpochMillis INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS final_records (id TEXT NOT NULL PRIMARY KEY, visitId TEXT NOT NULL, currentRevisionId TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(visitId) REFERENCES working_visits(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_final_records_visitId ON final_records(visitId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS final_record_revisions (id TEXT NOT NULL PRIMARY KEY, recordId TEXT NOT NULL, revisionNumber INTEGER NOT NULL, visitReference TEXT NOT NULL, actualServiceDate TEXT NOT NULL, recordedAtEpochMillis INTEGER NOT NULL, customerName TEXT NOT NULL, siteName TEXT NOT NULL, siteAddress TEXT, businessName TEXT NOT NULL, technicianName TEXT NOT NULL, businessPhone TEXT, businessEmail TEXT, businessAddress TEXT, businessZoneId TEXT NOT NULL, privateInternalNote TEXT, FOREIGN KEY(recordId) REFERENCES final_records(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_final_record_revisions_recordId_revisionNumber ON final_record_revisions(recordId, revisionNumber)")
                db.execSQL("CREATE TABLE IF NOT EXISTS final_work_items (id TEXT NOT NULL PRIMARY KEY, revisionId TEXT NOT NULL, position INTEGER NOT NULL, sourceWorkItemId TEXT NOT NULL, equipmentId TEXT NOT NULL, equipmentName TEXT NOT NULL, equipmentReference TEXT NOT NULL, equipmentIdentifier TEXT, equipmentMake TEXT, equipmentModel TEXT, equipmentSerial TEXT, serviceName TEXT NOT NULL, planId TEXT, planReference TEXT, outcome TEXT NOT NULL, publicWorkNote TEXT, notPerformedReason TEXT, fulfilledObligation INTEGER NOT NULL, oldDueDate TEXT, nextDueDate TEXT, intervalCount INTEGER, intervalUnit TEXT, capturedObligationId TEXT, privateInternalNote TEXT, FOREIGN KEY(revisionId) REFERENCES final_record_revisions(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_final_work_items_revisionId ON final_work_items(revisionId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_final_work_items_revisionId_position ON final_work_items(revisionId, position)")
                db.execSQL("CREATE TABLE IF NOT EXISTS final_checklist_items (id TEXT NOT NULL PRIMARY KEY, finalWorkItemId TEXT NOT NULL, position INTEGER NOT NULL, templateSnapshotId TEXT, templateRevision INTEGER, label TEXT NOT NULL, responseType TEXT NOT NULL, unit TEXT, required INTEGER NOT NULL, disposition TEXT NOT NULL, textValue TEXT, numberValue TEXT, reason TEXT, FOREIGN KEY(finalWorkItemId) REFERENCES final_work_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_final_checklist_items_finalWorkItemId ON final_checklist_items(finalWorkItemId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_final_checklist_items_finalWorkItemId_position ON final_checklist_items(finalWorkItemId, position)")
                db.execSQL("CREATE TABLE IF NOT EXISTS report_renditions (id TEXT NOT NULL PRIMARY KEY, revisionId TEXT NOT NULL, versionNumber INTEGER NOT NULL, generatedAtEpochMillis INTEGER, relativePath TEXT NOT NULL, sha256 TEXT, byteSize INTEGER, pageCount INTEGER, status TEXT NOT NULL, kind TEXT NOT NULL, failureMessage TEXT, FOREIGN KEY(revisionId) REFERENCES final_record_revisions(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_report_renditions_revisionId_versionNumber ON report_renditions(revisionId, versionNumber)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_report_renditions_relativePath ON report_renditions(relativePath)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recovery-only backfill for legacy drafts. New work captures these values at start time.
                db.execSQL("ALTER TABLE working_visits ADD COLUMN customerReferenceSnapshot TEXT")
                db.execSQL("ALTER TABLE working_visits ADD COLUMN siteReferenceSnapshot TEXT")
                db.execSQL("ALTER TABLE working_visits ADD COLUMN reportBusinessNameSnapshot TEXT")
                db.execSQL("ALTER TABLE working_visits ADD COLUMN reportTechnicianNameSnapshot TEXT")
                db.execSQL("ALTER TABLE working_visits ADD COLUMN reportPhoneSnapshot TEXT")
                db.execSQL("ALTER TABLE working_visits ADD COLUMN reportEmailSnapshot TEXT")
                db.execSQL("ALTER TABLE working_visits ADD COLUMN reportPostalAddressSnapshot TEXT")
                db.execSQL("ALTER TABLE working_visits ADD COLUMN reportZoneIdSnapshot TEXT")
                db.execSQL("UPDATE working_visits SET customerReferenceSnapshot=(SELECT reference FROM customers WHERE customers.id=working_visits.customerId), siteReferenceSnapshot=(SELECT reference FROM sites WHERE sites.id=working_visits.siteId)")
                db.execSQL("ALTER TABLE work_items ADD COLUMN equipmentIdentifierSnapshot TEXT")
                db.execSQL("ALTER TABLE work_items ADD COLUMN equipmentMakeSnapshot TEXT")
                db.execSQL("ALTER TABLE work_items ADD COLUMN equipmentModelSnapshot TEXT")
                db.execSQL("ALTER TABLE work_items ADD COLUMN equipmentSerialSnapshot TEXT")
                db.execSQL("UPDATE work_items SET equipmentIdentifierSnapshot=(SELECT technicianIdentifier FROM equipment WHERE equipment.id=work_items.equipmentId), equipmentMakeSnapshot=(SELECT make FROM equipment WHERE equipment.id=work_items.equipmentId), equipmentModelSnapshot=(SELECT model FROM equipment WHERE equipment.id=work_items.equipmentId), equipmentSerialSnapshot=(SELECT serialNumber FROM equipment WHERE equipment.id=work_items.equipmentId)")
                db.execSQL("ALTER TABLE final_record_revisions ADD COLUMN customerReference TEXT")
                db.execSQL("ALTER TABLE final_record_revisions ADD COLUMN siteReference TEXT")
                db.execSQL("UPDATE final_record_revisions SET customerReference=(SELECT c.reference FROM final_records f JOIN working_visits v ON v.id=f.visitId JOIN customers c ON c.id=v.customerId WHERE f.id=final_record_revisions.recordId), siteReference=(SELECT s.reference FROM final_records f JOIN working_visits v ON v.id=f.visitId JOIN sites s ON s.id=v.siteId WHERE f.id=final_record_revisions.recordId)")
                db.execSQL("ALTER TABLE final_work_items ADD COLUMN nextDueDateCalculated INTEGER")
                db.execSQL("ALTER TABLE final_work_items ADD COLUMN nextDueOverrideReason TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN contactName TEXT")
                db.execSQL("ALTER TABLE customers ADD COLUMN phone TEXT")
                db.execSQL("ALTER TABLE customers ADD COLUMN email TEXT")
                db.execSQL("ALTER TABLE customers ADD COLUMN privateNote TEXT")
                db.execSQL("ALTER TABLE customers ADD COLUMN state TEXT NOT NULL DEFAULT 'ACTIVE'")
                db.execSQL("ALTER TABLE sites ADD COLUMN contactName TEXT")
                db.execSQL("ALTER TABLE sites ADD COLUMN phone TEXT")
                db.execSQL("ALTER TABLE sites ADD COLUMN email TEXT")
                db.execSQL("ALTER TABLE sites ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sites ADD COLUMN state TEXT NOT NULL DEFAULT 'ACTIVE'")
                db.execSQL("ALTER TABLE equipment ADD COLUMN state TEXT NOT NULL DEFAULT 'ACTIVE'")
                db.execSQL("ALTER TABLE service_plans ADD COLUMN reusableTemplateId TEXT")
                db.execSQL("ALTER TABLE working_visits ADD COLUMN scheduledAtEpochMillis INTEGER")
                db.execSQL("ALTER TABLE working_visits ADD COLUMN appointmentZoneId TEXT")
                db.execSQL("ALTER TABLE working_visits ADD COLUMN scheduleChangeReason TEXT")
                db.execSQL("ALTER TABLE working_visits ADD COLUMN cancellationReason TEXT")
                db.execSQL("ALTER TABLE working_visits ADD COLUMN cancelledAtEpochMillis INTEGER")
                db.execSQL("ALTER TABLE follow_ups ADD COLUMN sourceVisitId TEXT")
                db.execSQL("ALTER TABLE follow_ups ADD COLUMN sourceWorkItemId TEXT")
                db.execSQL("ALTER TABLE follow_ups ADD COLUMN updatedAtEpochMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE follow_ups ADD COLUMN closedAtEpochMillis INTEGER")
                db.execSQL("ALTER TABLE follow_ups ADD COLUMN closureReason TEXT")
                db.execSQL("ALTER TABLE attachments ADD COLUMN byteSize INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE attachments ADD COLUMN caption TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS reusable_templates (id TEXT NOT NULL PRIMARY KEY, reference TEXT NOT NULL, name TEXT NOT NULL, currentRevisionId TEXT NOT NULL, state TEXT NOT NULL DEFAULT 'ACTIVE', modifiedAtEpochMillis INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_reusable_templates_reference ON reusable_templates(reference)")
                db.execSQL("CREATE TABLE IF NOT EXISTS reusable_template_revisions (id TEXT NOT NULL PRIMARY KEY, templateId TEXT NOT NULL, revisionNumber INTEGER NOT NULL, nameSnapshot TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(templateId) REFERENCES reusable_templates(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reusable_template_revisions_templateId ON reusable_template_revisions(templateId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_reusable_template_revisions_templateId_revisionNumber ON reusable_template_revisions(templateId, revisionNumber)")
                db.execSQL("CREATE TABLE IF NOT EXISTS reusable_template_items (id TEXT NOT NULL PRIMARY KEY, revisionId TEXT NOT NULL, position INTEGER NOT NULL, label TEXT NOT NULL, responseType TEXT NOT NULL, unit TEXT, required INTEGER NOT NULL, privateGuidance TEXT, FOREIGN KEY(revisionId) REFERENCES reusable_template_revisions(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reusable_template_items_revisionId ON reusable_template_items(revisionId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_reusable_template_items_revisionId_position ON reusable_template_items(revisionId, position)")
                db.execSQL("CREATE TABLE IF NOT EXISTS contact_notes (id TEXT NOT NULL PRIMARY KEY, reference TEXT NOT NULL, customerId TEXT NOT NULL, siteId TEXT, equipmentId TEXT, channel TEXT NOT NULL, occurredAtEpochMillis INTEGER NOT NULL, outcome TEXT NOT NULL, privateNote TEXT, createdAtEpochMillis INTEGER NOT NULL, editedAtEpochMillis INTEGER, enteredInError INTEGER NOT NULL DEFAULT 0, errorReason TEXT, FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_notes_customerId ON contact_notes(customerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_notes_siteId ON contact_notes(siteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_notes_equipmentId ON contact_notes(equipmentId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_contact_notes_reference ON contact_notes(reference)")
                db.execSQL("CREATE TABLE IF NOT EXISTS follow_up_events (id TEXT NOT NULL PRIMARY KEY, followUpId TEXT NOT NULL, eventType TEXT NOT NULL, occurredAtEpochMillis INTEGER NOT NULL, reason TEXT NOT NULL, dueDate TEXT, FOREIGN KEY(followUpId) REFERENCES follow_ups(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_follow_up_events_followUpId ON follow_up_events(followUpId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS part_entries (id TEXT NOT NULL PRIMARY KEY, workItemId TEXT NOT NULL, description TEXT NOT NULL, quantity TEXT NOT NULL, unit TEXT NOT NULL, modifiedAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(workItemId) REFERENCES work_items(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_part_entries_workItemId ON part_entries(workItemId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS visit_claims (obligationId TEXT NOT NULL PRIMARY KEY, visitId TEXT NOT NULL, claimedAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(obligationId) REFERENCES service_obligations(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(visitId) REFERENCES working_visits(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_visit_claims_visitId_obligationId ON visit_claims(visitId, obligationId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_visit_claims_visitId ON visit_claims(visitId)")
                db.execSQL("INSERT OR IGNORE INTO visit_claims(obligationId, visitId, claimedAtEpochMillis) SELECT wi.capturedObligationId, wi.visitId, v.modifiedAtEpochMillis FROM work_items wi JOIN working_visits v ON v.id=wi.visitId JOIN service_plans p ON p.id=wi.servicePlanId JOIN service_obligations o ON o.id=wi.capturedObligationId WHERE v.state IN ('BOOKED','WORKING') AND wi.capturedObligationId IS NOT NULL AND p.currentObligationId=wi.capturedObligationId AND o.consumedAtEpochMillis IS NULL")
                db.execSQL("CREATE TABLE IF NOT EXISTS final_part_entries (id TEXT NOT NULL PRIMARY KEY, finalWorkItemId TEXT NOT NULL, position INTEGER NOT NULL, description TEXT NOT NULL, quantity TEXT NOT NULL, unit TEXT NOT NULL, FOREIGN KEY(finalWorkItemId) REFERENCES final_work_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_final_part_entries_finalWorkItemId ON final_part_entries(finalWorkItemId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS final_photo_entries (id TEXT NOT NULL PRIMARY KEY, finalWorkItemId TEXT NOT NULL, position INTEGER NOT NULL, sourceAttachmentId TEXT NOT NULL, storedRelativePath TEXT NOT NULL, sha256 TEXT NOT NULL, byteSize INTEGER NOT NULL, mimeType TEXT NOT NULL, caption TEXT, FOREIGN KEY(finalWorkItemId) REFERENCES final_work_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_final_photo_entries_finalWorkItemId ON final_photo_entries(finalWorkItemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_final_photo_entries_sourceAttachmentId ON final_photo_entries(sourceAttachmentId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS plan_schedule_changes (id TEXT NOT NULL PRIMARY KEY, planId TEXT NOT NULL, oldDueDate TEXT NOT NULL, newDueDate TEXT NOT NULL, reason TEXT NOT NULL, changedAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(planId) REFERENCES service_plans(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_schedule_changes_planId ON plan_schedule_changes(planId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS visit_schedule_events (id TEXT NOT NULL PRIMARY KEY, visitId TEXT NOT NULL, eventType TEXT NOT NULL, oldServiceDate TEXT NOT NULL, newServiceDate TEXT, oldScheduledAtEpochMillis INTEGER, newScheduledAtEpochMillis INTEGER, reason TEXT NOT NULL, occurredAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(visitId) REFERENCES working_visits(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_visit_schedule_events_visitId ON visit_schedule_events(visitId)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE final_records ADD COLUMN voided INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE final_records ADD COLUMN voidedAtEpochMillis INTEGER")
                db.execSQL("ALTER TABLE final_records ADD COLUMN publicVoidReason TEXT")
                db.execSQL("ALTER TABLE final_records ADD COLUMN privateVoidReason TEXT")
                db.execSQL("ALTER TABLE final_record_revisions ADD COLUMN supersedesRevisionId TEXT")
                db.execSQL("ALTER TABLE final_record_revisions ADD COLUMN correctionReason TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS correction_drafts (id TEXT NOT NULL PRIMARY KEY, recordId TEXT NOT NULL, baseRevisionId TEXT NOT NULL, reason TEXT NOT NULL, actualServiceDate TEXT NOT NULL, customerName TEXT NOT NULL, siteName TEXT NOT NULL, siteAddress TEXT, businessName TEXT NOT NULL, technicianName TEXT NOT NULL, publicNote TEXT, privateNote TEXT, scheduleAcknowledged INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL, modifiedAtEpochMillis INTEGER NOT NULL, commitToken TEXT NOT NULL, FOREIGN KEY(recordId) REFERENCES final_records(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(baseRevisionId) REFERENCES final_record_revisions(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_correction_drafts_recordId ON correction_drafts(recordId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_correction_drafts_baseRevisionId ON correction_drafts(baseRevisionId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS correction_work_items (id TEXT NOT NULL PRIMARY KEY, draftId TEXT NOT NULL, sourceFinalWorkItemId TEXT NOT NULL, position INTEGER NOT NULL, outcome TEXT NOT NULL, publicWorkNote TEXT, notPerformedReason TEXT, fulfilledObligation INTEGER NOT NULL, proposedNextDueDate TEXT, FOREIGN KEY(draftId) REFERENCES correction_drafts(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_correction_work_items_draftId ON correction_work_items(draftId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_correction_work_items_draftId_position ON correction_work_items(draftId, position)")
                db.execSQL("CREATE TABLE IF NOT EXISTS change_entries (id TEXT NOT NULL PRIMARY KEY, subjectType TEXT NOT NULL, subjectId TEXT NOT NULL, changeType TEXT NOT NULL, eventDate TEXT NOT NULL, recordedAtEpochMillis INTEGER NOT NULL, reason TEXT NOT NULL, oldValue TEXT, newValue TEXT, customerId TEXT, siteId TEXT, equipmentId TEXT, recordId TEXT, customerNameSnapshot TEXT, siteNameSnapshot TEXT, equipmentNameSnapshot TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_change_entries_subjectType_subjectId ON change_entries(subjectType, subjectId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_change_entries_customerId ON change_entries(customerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_change_entries_siteId ON change_entries(siteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_change_entries_equipmentId ON change_entries(equipmentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_change_entries_recordId ON change_entries(recordId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS equipment_moves (id TEXT NOT NULL PRIMARY KEY, equipmentId TEXT NOT NULL, oldSiteId TEXT NOT NULL, newSiteId TEXT NOT NULL, effectiveDate TEXT NOT NULL, reason TEXT NOT NULL, recordedAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(equipmentId) REFERENCES equipment(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_equipment_moves_equipmentId ON equipment_moves(equipmentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_equipment_moves_oldSiteId ON equipment_moves(oldSiteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_equipment_moves_newSiteId ON equipment_moves(newSiteId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS recovery_metadata (id TEXT NOT NULL PRIMARY KEY, datasetId TEXT NOT NULL, firstBusinessWriteAtEpochMillis INTEGER, lastBusinessWriteAtEpochMillis INTEGER, lastBackupAttemptAtEpochMillis INTEGER, lastVerifiedFullBackupAtEpochMillis INTEGER, lastVerifiedSnapshotAtEpochMillis INTEGER, lastVerifiedDestination TEXT, lastVerifiedSize INTEGER, backupReminderDays INTEGER NOT NULL, restoredFromIncompleteCopy INTEGER NOT NULL, restrictedRecoveryState INTEGER NOT NULL)")
                db.execSQL("INSERT OR IGNORE INTO recovery_metadata(id,datasetId,firstBusinessWriteAtEpochMillis,lastBusinessWriteAtEpochMillis,lastBackupAttemptAtEpochMillis,lastVerifiedFullBackupAtEpochMillis,lastVerifiedSnapshotAtEpochMillis,lastVerifiedDestination,lastVerifiedSize,backupReminderDays,restoredFromIncompleteCopy,restrictedRecoveryState) VALUES('primary', lower(hex(randomblob(16))), NULL, NULL, NULL, NULL, NULL, NULL, NULL, 7, 0, 0)")
                configureStage4Tracking(db)
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recovery_metadata ADD COLUMN adoptionToken TEXT")
                db.execSQL("ALTER TABLE correction_drafts ADD COLUMN followUpEffectsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE correction_drafts ADD COLUMN newFollowUpsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE correction_work_items ADD COLUMN checklistJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE correction_work_items ADD COLUMN partsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE correction_work_items ADD COLUMN photosJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE correction_work_items ADD COLUMN nextDueDateCalculated INTEGER")
                db.execSQL("ALTER TABLE correction_work_items ADD COLUMN nextDueOverrideReason TEXT")
                db.execSQL("ALTER TABLE final_photo_entries ADD COLUMN addedInCorrection INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE final_photo_entries ADD COLUMN addedAtEpochMillis INTEGER")
                db.execSQL("ALTER TABLE final_record_revisions ADD COLUMN publicNote TEXT")
                configureStage4Tracking(db)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS technician_identity (id TEXT NOT NULL PRIMARY KEY, technicianId TEXT NOT NULL, displayName TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, modifiedAtEpochMillis INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_technician_identity_technicianId ON technician_identity(technicianId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS dispatch_technicians (technicianId TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, modifiedAtEpochMillis INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS dispatch_teams (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, modifiedAtEpochMillis INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_dispatch_teams_name ON dispatch_teams(name)")
                db.execSQL("CREATE TABLE IF NOT EXISTS dispatch_team_members (teamId TEXT NOT NULL, technicianId TEXT NOT NULL, isLeader INTEGER NOT NULL, PRIMARY KEY(teamId, technicianId), FOREIGN KEY(teamId) REFERENCES dispatch_teams(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(technicianId) REFERENCES dispatch_technicians(technicianId) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dispatch_team_members_teamId ON dispatch_team_members(teamId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dispatch_team_members_technicianId ON dispatch_team_members(technicianId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS dispatch_outbox_visits (dispatchVisitId TEXT NOT NULL PRIMARY KEY, managerReference TEXT, siteId TEXT NOT NULL, serviceDate TEXT NOT NULL, appointmentLocalTime TEXT, appointmentZoneId TEXT NOT NULL, instructions TEXT, lastExportedGeneration INTEGER, lastExportedMaterialHash TEXT, lastExportedAtEpochMillis INTEGER, createdAtEpochMillis INTEGER NOT NULL, modifiedAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(siteId) REFERENCES sites(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dispatch_outbox_visits_siteId ON dispatch_outbox_visits(siteId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS dispatch_outbox_visit_teams (dispatchVisitId TEXT NOT NULL, teamId TEXT NOT NULL, PRIMARY KEY(dispatchVisitId, teamId), FOREIGN KEY(dispatchVisitId) REFERENCES dispatch_outbox_visits(dispatchVisitId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(teamId) REFERENCES dispatch_teams(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dispatch_outbox_visit_teams_dispatchVisitId ON dispatch_outbox_visit_teams(dispatchVisitId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dispatch_outbox_visit_teams_teamId ON dispatch_outbox_visit_teams(teamId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS dispatch_outbox_items (dispatchItemId TEXT NOT NULL PRIMARY KEY, dispatchVisitId TEXT NOT NULL, position INTEGER NOT NULL, equipmentId TEXT NOT NULL, taskName TEXT NOT NULL, servicePlanReference TEXT, dueDateSnapshot TEXT, FOREIGN KEY(dispatchVisitId) REFERENCES dispatch_outbox_visits(dispatchVisitId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(equipmentId) REFERENCES equipment(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dispatch_outbox_items_dispatchVisitId ON dispatch_outbox_items(dispatchVisitId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dispatch_outbox_items_equipmentId ON dispatch_outbox_items(equipmentId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_dispatch_outbox_items_dispatchVisitId_position ON dispatch_outbox_items(dispatchVisitId, position)")
                db.execSQL("CREATE TABLE IF NOT EXISTS dispatch_outbox_item_assignees (dispatchItemId TEXT NOT NULL, technicianId TEXT NOT NULL, PRIMARY KEY(dispatchItemId, technicianId), FOREIGN KEY(dispatchItemId) REFERENCES dispatch_outbox_items(dispatchItemId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(technicianId) REFERENCES dispatch_technicians(technicianId) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dispatch_outbox_item_assignees_dispatchItemId ON dispatch_outbox_item_assignees(dispatchItemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dispatch_outbox_item_assignees_technicianId ON dispatch_outbox_item_assignees(technicianId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS dispatch_visit_bindings (dispatchVisitId TEXT NOT NULL PRIMARY KEY, localVisitId TEXT NOT NULL, appliedGeneration INTEGER NOT NULL, packageId TEXT NOT NULL, senderLabel TEXT NOT NULL, managerReference TEXT, participantSnapshotJson TEXT NOT NULL, leaderIdsJson TEXT NOT NULL, teamSnapshotJson TEXT NOT NULL, appliedMaterialHash TEXT NOT NULL, controlledFingerprint TEXT NOT NULL, importedAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(localVisitId) REFERENCES working_visits(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_dispatch_visit_bindings_localVisitId ON dispatch_visit_bindings(localVisitId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS dispatch_item_bindings (dispatchVisitId TEXT NOT NULL, dispatchItemId TEXT NOT NULL, localWorkItemId TEXT, equipmentReferenceSnapshot TEXT NOT NULL, taskNameSnapshot TEXT NOT NULL, servicePlanReferenceSnapshot TEXT, dueDateSnapshot TEXT, assignedTechniciansJson TEXT NOT NULL, assignmentMeaning TEXT NOT NULL, localRole TEXT NOT NULL, documentationDisposition TEXT NOT NULL, deferredToTechnicianId TEXT, deferredToName TEXT, PRIMARY KEY(dispatchVisitId, dispatchItemId), FOREIGN KEY(dispatchVisitId) REFERENCES dispatch_visit_bindings(dispatchVisitId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(localWorkItemId) REFERENCES work_items(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dispatch_item_bindings_dispatchVisitId ON dispatch_item_bindings(dispatchVisitId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_dispatch_item_bindings_localWorkItemId ON dispatch_item_bindings(localWorkItemId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS final_dispatch_visits (revisionId TEXT NOT NULL PRIMARY KEY, dispatchVisitId TEXT NOT NULL, generation INTEGER NOT NULL, managerReference TEXT, senderLabel TEXT NOT NULL, documentingTechnicianId TEXT NOT NULL, documentingTechnicianName TEXT NOT NULL, FOREIGN KEY(revisionId) REFERENCES final_record_revisions(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_final_dispatch_visits_dispatchVisitId ON final_dispatch_visits(dispatchVisitId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS final_dispatch_items (finalWorkItemId TEXT NOT NULL PRIMARY KEY, dispatchItemId TEXT NOT NULL, assignedTechniciansJson TEXT NOT NULL, assignmentMeaning TEXT NOT NULL, localDocumentationRole TEXT NOT NULL, FOREIGN KEY(finalWorkItemId) REFERENCES final_work_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_final_dispatch_items_dispatchItemId ON final_dispatch_items(dispatchItemId)")
                configureDispatchIdentity(db)
                configureStage4Tracking(db)
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dispatch_visit_bindings ADD COLUMN instructionsSnapshot TEXT")
                db.execSQL("UPDATE dispatch_visit_bindings SET instructionsSnapshot=(SELECT NULLIF(p.internalNote,'') FROM dispatch_item_bindings i JOIN work_item_private_drafts p ON p.workItemId=i.localWorkItemId WHERE i.dispatchVisitId=dispatch_visit_bindings.dispatchVisitId ORDER BY i.dispatchItemId LIMIT 1)")
                db.execSQL("UPDATE work_item_private_drafts SET internalNote='' WHERE workItemId IN (SELECT i.localWorkItemId FROM dispatch_item_bindings i JOIN dispatch_visit_bindings v ON v.dispatchVisitId=i.dispatchVisitId JOIN working_visits w ON w.id=v.localVisitId WHERE w.state='BOOKED' AND work_item_private_drafts.internalNote=COALESCE(v.instructionsSnapshot,''))")
                configureStage4Tracking(db)
            }
        }

        private fun configureDispatchIdentity(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT OR IGNORE INTO technician_identity(id,technicianId,displayName,createdAtEpochMillis,modifiedAtEpochMillis) SELECT 'primary', lower(hex(randomblob(16))), COALESCE(NULLIF(TRIM((SELECT technicianName FROM business_profiles WHERE id='primary')),''), 'Technician'), CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)")
        }

        internal fun configureStage4Tracking(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT OR IGNORE INTO recovery_metadata(id,datasetId,firstBusinessWriteAtEpochMillis,lastBusinessWriteAtEpochMillis,lastBackupAttemptAtEpochMillis,lastVerifiedFullBackupAtEpochMillis,lastVerifiedSnapshotAtEpochMillis,lastVerifiedDestination,lastVerifiedSize,backupReminderDays,restoredFromIncompleteCopy,restrictedRecoveryState) VALUES('primary', lower(hex(randomblob(16))), NULL, NULL, NULL, NULL, NULL, NULL, NULL, 7, 0, 0)")
            val tracked = listOf("customers", "sites", "equipment", "service_plans", "service_obligations", "template_snapshots", "checklist_item_snapshots", "working_visits", "work_items", "work_item_public_drafts", "work_item_private_drafts", "working_responses", "attachments", "follow_ups", "business_profiles", "final_records", "final_record_revisions", "final_work_items", "final_checklist_items", "report_renditions", "reusable_templates", "reusable_template_revisions", "reusable_template_items", "contact_notes", "follow_up_events", "part_entries", "visit_claims", "final_part_entries", "final_photo_entries", "plan_schedule_changes", "visit_schedule_events", "correction_drafts", "correction_work_items", "change_entries", "equipment_moves", "technician_identity", "dispatch_technicians", "dispatch_teams", "dispatch_team_members", "dispatch_outbox_visits", "dispatch_outbox_visit_teams", "dispatch_outbox_items", "dispatch_outbox_item_assignees", "dispatch_visit_bindings", "dispatch_item_bindings", "final_dispatch_visits", "final_dispatch_items")
            val existing = mutableSetOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor -> while (cursor.moveToNext()) existing += cursor.getString(0) }
            tracked.filter { it in existing }.forEach { table ->
                listOf("INSERT", "UPDATE", "DELETE").forEach { operation ->
                    val trigger = "track_${table}_${operation.lowercase()}"
                    db.execSQL("DROP TRIGGER IF EXISTS `$trigger`")
                    db.execSQL(
                        "CREATE TRIGGER IF NOT EXISTS `$trigger` AFTER $operation ON `$table` " +
                            "BEGIN UPDATE recovery_metadata SET " +
                            "firstBusinessWriteAtEpochMillis=COALESCE(firstBusinessWriteAtEpochMillis, CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)), " +
                            "lastBusinessWriteAtEpochMillis=CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER) " +
                            "WHERE id='primary'; END",
                    )
                }
            }
        }
    }
}
