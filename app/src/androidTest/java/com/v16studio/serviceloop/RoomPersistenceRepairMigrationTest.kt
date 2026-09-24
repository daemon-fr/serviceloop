package com.v16studio.serviceloop

import androidx.room.testing.MigrationTestHelper
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPersistenceRepairMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), ServiceLoopDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory(),
    )
    private val databases = mutableListOf<String>()

    @After fun cleanup() {
        databases.forEach(InstrumentationRegistry.getInstrumentation().targetContext::deleteDatabase)
    }

    @Test fun populatedFourteenToFifteenPreservesEveryDependentChildAndBinding() {
        val name = newName("14-15")
        helper.createDatabase(name, 14).use(::seedFourteen)
        helper.runMigrationsAndValidate(name, 15, true, ServiceLoopDatabase.MIGRATION_14_15).use(::assertFourteenChildren)
    }

    @Test fun populatedFourteenToTwentyPreservesEveryDependentChildAndBinding() {
        val name = newName("14-20")
        helper.createDatabase(name, 14).use(::seedFourteen)
        helper.runMigrationsAndValidate(
            name, 20, true,
            ServiceLoopDatabase.MIGRATION_14_15, ServiceLoopDatabase.MIGRATION_15_16,
            ServiceLoopDatabase.MIGRATION_16_17, ServiceLoopDatabase.MIGRATION_17_18,
            ServiceLoopDatabase.MIGRATION_18_19, ServiceLoopDatabase.MIGRATION_19_20,
        ).use(::assertFourteenChildren)
    }

    @Test fun nineteenToTwentyKeepsRowsAndAddsNullableSnapshotsAndAuthorIndexes() {
        val name = newName("19-20")
        helper.createDatabase(name, 19).apply {
            execSQL("INSERT INTO work_result_receipts(id,packageId,resultId,sourceFinalRevisionId,assignmentIssuerId,exporterId,dispatchVisitId,dispatchItemId,assignmentGeneration,assignmentMaterialHash,receivedAtEpochMillis,payloadSha256,status) VALUES('receipt','package','result','revision','issuer','author-a','visit','item',1,'hash',1,'payload','APPLIED')")
            execSQL("INSERT INTO remote_final_results(id,resultId,sourceFinalRevisionId,dispatchVisitId,dispatchItemId,technicianId,technicianName,customerSnapshotJson,siteSnapshotJson,subjectSnapshotJson,serviceDate,outcome,checklistJson,findingsJson,partsJson,followUpsJson,recurrenceJson,provenanceJson,importedAtEpochMillis) VALUES('remote','result','revision','visit','item','author-a','A','{}','{}','{}','2026-09-24','PERFORMED','[]','[]','[]','[]','{}','{}',1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 20, true, ServiceLoopDatabase.MIGRATION_19_20).use { db ->
            db.query("SELECT sourcePayloadJson FROM remote_final_results WHERE id='remote'").use { it.moveToFirst(); assertNull(it.getString(0)) }
            db.query("SELECT followUpsSnapshotJson FROM final_work_items LIMIT 0").close()
            db.execSQL("INSERT INTO work_result_receipts(id,packageId,resultId,sourceFinalRevisionId,assignmentIssuerId,exporterId,dispatchVisitId,dispatchItemId,assignmentGeneration,assignmentMaterialHash,receivedAtEpochMillis,payloadSha256,status) VALUES('receipt-b','package','result','revision','issuer','author-b','visit','item',1,'hash',1,'payload','APPLIED')")
            db.execSQL("INSERT INTO remote_final_results(id,resultId,sourceFinalRevisionId,dispatchVisitId,dispatchItemId,technicianId,technicianName,customerSnapshotJson,siteSnapshotJson,subjectSnapshotJson,serviceDate,outcome,checklistJson,findingsJson,partsJson,followUpsJson,recurrenceJson,provenanceJson,importedAtEpochMillis) VALUES('remote-b','result','revision','visit','item','author-b','B','{}','{}','{}','2026-09-24','PERFORMED','[]','[]','[]','[]','{}','{}',1)")
            count(db, "work_result_receipts", 2)
            count(db, "remote_final_results", 2)
        }
    }

    @Test fun eighteenToTwentyPreservesPopulatedRows() {
        val name = newName("18-20")
        helper.createDatabase(name, 18).apply {
            execSQL("INSERT INTO customers(id,reference,name) VALUES('older-customer','LEGACY-18','Legacy customer')")
            close()
        }
        helper.runMigrationsAndValidate(name, 20, true, ServiceLoopDatabase.MIGRATION_18_19,
            ServiceLoopDatabase.MIGRATION_19_20).use { db ->
            db.query("SELECT reference,name FROM customers WHERE id='older-customer'").use {
                assertEquals(true, it.moveToFirst())
                assertEquals("LEGACY-18", it.getString(0))
                assertEquals("Legacy customer", it.getString(1))
            }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    @Test fun everySupportedSchemaStartHasAnExplicitPathToTwenty() {
        val steps: List<Migration> = listOf(
            ServiceLoopDatabase.MIGRATION_1_2, ServiceLoopDatabase.MIGRATION_2_3,
            ServiceLoopDatabase.MIGRATION_3_4, ServiceLoopDatabase.MIGRATION_4_5,
            ServiceLoopDatabase.MIGRATION_5_6, ServiceLoopDatabase.MIGRATION_6_7,
            ServiceLoopDatabase.MIGRATION_7_8, ServiceLoopDatabase.MIGRATION_8_9,
            ServiceLoopDatabase.MIGRATION_9_10, ServiceLoopDatabase.MIGRATION_10_11,
            ServiceLoopDatabase.MIGRATION_11_12, ServiceLoopDatabase.MIGRATION_12_13,
            ServiceLoopDatabase.MIGRATION_13_14, ServiceLoopDatabase.MIGRATION_14_15,
            ServiceLoopDatabase.MIGRATION_15_16, ServiceLoopDatabase.MIGRATION_16_17,
            ServiceLoopDatabase.MIGRATION_17_18, ServiceLoopDatabase.MIGRATION_18_19,
            ServiceLoopDatabase.MIGRATION_19_20,
        )
        for (start in 1..19) {
            val name = newName("$start-20")
            helper.createDatabase(name, start).close()
            helper.runMigrationsAndValidate(name, 20, true, *steps.drop(start - 1).toTypedArray()).use { db ->
                db.query("PRAGMA foreign_key_check").use { assertFalse("FK violation from schema $start", it.moveToFirst()) }
            }
        }
    }

    private fun newName(label: String): String = "repair-$label-${System.nanoTime()}.db".also(databases::add)

    private fun seedFourteen(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO customers(id,reference,name) VALUES('c','CU','Customer')")
        db.execSQL("INSERT INTO sites(id,customerId,reference,name) VALUES('s','c','ST','Site')")
        db.execSQL("INSERT INTO equipment(id,siteId,reference,name) VALUES('e','s','EQ','Equipment')")
        db.execSQL("INSERT INTO working_visits(id,reference,customerId,siteId,actualServiceDate,customerNameSnapshot,siteNameSnapshot,state,modifiedAtEpochMillis) VALUES('v','V','c','s','2026-09-24','Customer','Site','COMPLETED',1)")
        db.execSQL("INSERT INTO template_snapshots(id,templateName,revision,capturedAtEpochMillis) VALUES('template','Checklist',1,1)")
        db.execSQL("INSERT INTO checklist_item_snapshots(id,templateSnapshotId,position,label,responseType,required) VALUES('check','template',1,'Check','TEXT',0)")
        db.execSQL("INSERT INTO work_items(id,visitId,equipmentId,equipmentNameSnapshot,equipmentReferenceSnapshot,serviceNameSnapshot,checklistReviewed) VALUES('work','v','e','Equipment','EQ','Service',1)")
        db.execSQL("INSERT INTO work_item_public_drafts(workItemId,workPerformed) VALUES('work','Public draft')")
        db.execSQL("INSERT INTO work_item_private_drafts(workItemId,internalNote) VALUES('work','Private draft')")
        db.execSQL("INSERT INTO working_input_buffers(workItemId,fieldKey,rawValue,modifiedAtEpochMillis) VALUES('work','raw','alternate draft',2)")
        db.execSQL("INSERT INTO working_responses(id,workItemId,checklistItemSnapshotId,disposition,modifiedAtEpochMillis) VALUES('response','work','check','ANSWERED',3)")
        db.execSQL("INSERT INTO part_entries(id,workItemId,description,quantity,unit,modifiedAtEpochMillis) VALUES('part','work','Part','2','pcs',4)")
        db.execSQL("INSERT INTO final_records(id,visitId,currentRevisionId,createdAtEpochMillis) VALUES('record','v','revision',5)")
        db.execSQL("INSERT INTO final_record_revisions(id,recordId,revisionNumber,visitReference,actualServiceDate,recordedAtEpochMillis,customerName,siteName,businessName,technicianName,businessZoneId) VALUES('revision','record',1,'V','2026-09-24',5,'Customer','Site','Business','Technician','Europe/Bucharest')")
        db.execSQL("INSERT INTO final_work_items(id,revisionId,position,sourceWorkItemId,equipmentId,equipmentName,equipmentReference,serviceName,outcome,fulfilledObligation) VALUES('final','revision',1,'work','e','Equipment','EQ','Service','PERFORMED',0)")
        db.execSQL("INSERT INTO final_checklist_items(id,finalWorkItemId,position,label,responseType,required,disposition) VALUES('final-check','final',1,'Check','TEXT',0,'ANSWERED')")
        db.execSQL("INSERT INTO final_part_entries(id,finalWorkItemId,position,description,quantity,unit) VALUES('final-part','final',1,'Part','2','pcs')")
        db.execSQL("INSERT INTO final_photo_entries(id,finalWorkItemId,position,sourceAttachmentId,storedRelativePath,sha256,byteSize,mimeType,addedInCorrection) VALUES('final-photo','final',1,'attachment','attachments/photo.jpg','abc',3,'image/jpeg',0)")
        db.execSQL("INSERT INTO dispatch_technicians(technicianId,displayName,createdAtEpochMillis,modifiedAtEpochMillis) VALUES('tech','Technician',1,1)")
        db.execSQL("INSERT INTO dispatch_outbox_visits(dispatchVisitId,siteId,serviceDate,appointmentZoneId,createdAtEpochMillis,modifiedAtEpochMillis) VALUES('out','s','2026-09-24','Europe/Bucharest',1,1)")
        db.execSQL("INSERT INTO dispatch_outbox_items(dispatchItemId,dispatchVisitId,position,equipmentId,taskName) VALUES('out-item','out',1,'e','Service')")
        db.execSQL("INSERT INTO dispatch_outbox_item_assignees(dispatchItemId,technicianId) VALUES('out-item','tech')")
        db.execSQL("INSERT INTO dispatch_visit_bindings(dispatchVisitId,localVisitId,appliedGeneration,packageId,senderLabel,participantSnapshotJson,leaderIdsJson,teamSnapshotJson,appliedMaterialHash,controlledFingerprint,importedAtEpochMillis,updatedAtEpochMillis) VALUES('in','v',1,'package','Coordinator','[]','[]','[]','hash','fingerprint',1,1)")
        db.execSQL("INSERT INTO dispatch_item_bindings(dispatchVisitId,dispatchItemId,localWorkItemId,equipmentReferenceSnapshot,taskNameSnapshot,assignedTechniciansJson,assignmentMeaning,localRole,documentationDisposition) VALUES('in','in-item','work','EQ','Service','[]','ASSIGNED','TECHNICIAN','PERFORM')")
        db.execSQL("INSERT INTO final_dispatch_items(finalWorkItemId,dispatchItemId,assignedTechniciansJson,assignmentMeaning,localDocumentationRole) VALUES('final','in-item','[]','ASSIGNED','TECHNICIAN')")
    }

    private fun assertFourteenChildren(db: SupportSQLiteDatabase) {
        listOf("work_item_public_drafts", "work_item_private_drafts", "working_input_buffers", "working_responses",
            "part_entries", "final_checklist_items", "final_part_entries", "final_photo_entries",
            "final_dispatch_items", "dispatch_outbox_item_assignees", "dispatch_item_bindings").forEach { count(db, it, 1) }
        db.query("SELECT localWorkItemId FROM dispatch_item_bindings").use { assertFalse(!it.moveToFirst()); assertEquals("work", it.getString(0)) }
        db.query("SELECT rawValue FROM working_input_buffers").use { it.moveToFirst(); assertEquals("alternate draft", it.getString(0)) }
        db.query("SELECT storedRelativePath FROM final_photo_entries").use { it.moveToFirst(); assertEquals("attachments/photo.jpg", it.getString(0)) }
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    private fun count(db: SupportSQLiteDatabase, table: String, expected: Int) {
        db.query("SELECT count(*) FROM `$table`").use { it.moveToFirst(); assertEquals(expected, it.getInt(0)) }
    }
}
