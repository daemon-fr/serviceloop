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
    ],
    version = 4,
    exportSchema = true,
)
abstract class ServiceLoopDatabase : RoomDatabase() {
    abstract fun serviceLoopDao(): ServiceLoopDao

    companion object {
        fun open(context: Context): ServiceLoopDatabase = Room.databaseBuilder(
            context.applicationContext,
            ServiceLoopDatabase::class.java,
            "serviceloop.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()

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
    }
}
