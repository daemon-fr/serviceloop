package com.v16studio.serviceloop.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CustomerEntity::class, CustomerContactEntity::class, SiteEntity::class, EquipmentEntity::class,
        ServicePlanEntity::class, ServiceObligationEntity::class,
        TemplateSnapshotEntity::class, ChecklistItemSnapshotEntity::class,
        WorkingVisitEntity::class, WorkItemEntity::class,
        WorkItemPublicDraftEntity::class, WorkItemPrivateDraftEntity::class,
        WorkingInputBufferEntity::class,
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
        TechnicianIdentityEntity::class, TrustedServiceLoopIdEntity::class, DispatchTechnicianEntity::class,
        DispatchTeamEntity::class, DispatchTeamMemberEntity::class,
        DispatchOutboxVisitEntity::class, DispatchOutboxVisitTeamEntity::class,
        DispatchOutboxItemEntity::class, DispatchOutboxItemAssigneeEntity::class,
        DispatchVisitBindingEntity::class, DispatchItemBindingEntity::class,
        FinalDispatchVisitEntity::class, FinalDispatchItemEntity::class,
        ReminderPreferencesEntity::class,
        WorkResultReceiptEntity::class, RemoteFinalResultEntity::class, RemoteResultPhotoEntity::class,
        AggregateReportEntity::class, AggregateReportSourceEntity::class, AggregateReportRenditionEntity::class,
        RetainedImageEntity::class,
        DataTransferBindingEntity::class, TransferredFinalResultEntity::class,
        TransferredEvidenceEntity::class, TransferredHistoryEntryEntity::class,
    ],
    version = 1,
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
        ).addCallback(object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                configureDispatchIdentity(db)
                configureReminderDefaults(db)
                configureStage4Tracking(db)
            }
        }).build().also { database ->
            if (RecoveryPackage.recoverInterrupted(database, context.applicationContext.filesDir) !=
                RecoveryPackage.RecoveryResult.RESTRICTED) {
                BusinessFileAdoptionJournal.recoverInterrupted(database, context.applicationContext.filesDir)
            }
        }

        internal fun configureReminderDefaults(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT OR IGNORE INTO reminder_preferences(id,dailySummaryEnabled,summaryHour,summaryMinute,summaryDaysMask,dueSoonHorizonDays,includeDueServices,includeVisits,includeFollowUps,includeUnfinishedVisits,includeBackupReminder,appointmentAlertsEnabled,defaultAppointmentLeadMinutes) VALUES('primary',1,8,0,127,14,1,1,1,1,1,0,180)")
        }

        private fun configureDispatchIdentity(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT OR IGNORE INTO technician_identity(id,technicianId,displayName,createdAtEpochMillis,modifiedAtEpochMillis) SELECT 'primary', ?, COALESCE(NULLIF(TRIM((SELECT technicianName FROM business_profiles WHERE id='primary')),''), 'Technician'), CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)", arrayOf(TechnicianIdCodec.generate()))
        }

        internal fun configureStage4Tracking(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT OR IGNORE INTO recovery_metadata(id,datasetId,firstBusinessWriteAtEpochMillis,lastBusinessWriteAtEpochMillis,lastBackupAttemptAtEpochMillis,lastVerifiedFullBackupAtEpochMillis,lastVerifiedSnapshotAtEpochMillis,lastVerifiedDestination,lastVerifiedSize,backupReminderDays,restoredFromIncompleteCopy,restrictedRecoveryState) VALUES('primary', lower(hex(randomblob(16))), NULL, NULL, NULL, NULL, NULL, NULL, NULL, 7, 0, 0)")
            val tracked = listOf("customers", "customer_contacts", "sites", "equipment", "service_plans", "service_obligations", "template_snapshots", "checklist_item_snapshots", "working_visits", "work_items", "work_item_public_drafts", "work_item_private_drafts", "working_input_buffers", "working_responses", "attachments", "follow_ups", "business_profiles", "final_records", "final_record_revisions", "final_work_items", "final_checklist_items", "report_renditions", "reusable_templates", "reusable_template_revisions", "reusable_template_items", "contact_notes", "follow_up_events", "part_entries", "visit_claims", "final_part_entries", "final_photo_entries", "plan_schedule_changes", "visit_schedule_events", "correction_drafts", "correction_work_items", "change_entries", "equipment_moves", "technician_identity", "dispatch_technicians", "dispatch_teams", "dispatch_team_members", "dispatch_outbox_visits", "dispatch_outbox_visit_teams", "dispatch_outbox_items", "dispatch_outbox_item_assignees", "dispatch_visit_bindings", "dispatch_item_bindings", "final_dispatch_visits", "final_dispatch_items", "work_result_receipts", "remote_final_results", "remote_result_photos", "aggregate_reports", "aggregate_report_sources", "aggregate_report_renditions", "retained_images", "data_transfer_bindings", "transferred_final_results", "transferred_evidence", "transferred_history_entries", "reminder_preferences")
            val existing = mutableSetOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor -> while (cursor.moveToNext()) existing += cursor.getString(0) }
            tracked.filter { it in existing }.forEach { table ->
                listOf("INSERT", "UPDATE", "DELETE").forEach { operation ->
                    val trigger = "track_" + table + "_" + operation.lowercase()
                    db.execSQL("DROP TRIGGER IF EXISTS " + trigger)
                    db.execSQL(
                        "CREATE TRIGGER IF NOT EXISTS " + trigger + " AFTER " + operation + " ON " + table +
                            " BEGIN UPDATE recovery_metadata SET " +
                            "firstBusinessWriteAtEpochMillis=COALESCE(firstBusinessWriteAtEpochMillis, CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)), " +
                            "lastBusinessWriteAtEpochMillis=CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER) " +
                            "WHERE id='primary'; END",
                    )
                }
            }
        }
    }

}
