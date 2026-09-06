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
    ],
    version = 3,
    exportSchema = true,
)
abstract class ServiceLoopDatabase : RoomDatabase() {
    abstract fun serviceLoopDao(): ServiceLoopDao

    companion object {
        fun open(context: Context): ServiceLoopDatabase = Room.databaseBuilder(
            context.applicationContext,
            ServiceLoopDatabase::class.java,
            "serviceloop.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

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
    }
}
