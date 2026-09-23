package com.v16studio.serviceloop

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Room18To19MigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), ServiceLoopDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun contactsKeepValuesAndReceiveStablePerCustomerOrder() {
        val name = "b049-room-18-19-${System.nanoTime()}.db"
        helper.createDatabase(name, 18).apply {
            execSQL("INSERT INTO customers(id,reference,name,state,customerType) VALUES('c','CU-1','Customer','ACTIVE','STANDARD')")
            execSQL("INSERT INTO customer_contacts(id,customerId,channel,value,createdAtEpochMillis,modifiedAtEpochMillis) VALUES('late','c','PHONE','123',1,20)")
            execSQL("INSERT INTO customer_contacts(id,customerId,channel,value,createdAtEpochMillis,modifiedAtEpochMillis) VALUES('early','c','EMAIL','x@example.com',1,10)")
            execSQL("INSERT INTO sites(id,customerId,reference,name) VALUES('s','c','ST-1','Site')")
            execSQL("INSERT INTO working_visits(id,reference,customerId,siteId,actualServiceDate,customerNameSnapshot,siteNameSnapshot,state,modifiedAtEpochMillis) VALUES('v','V-1','c','s','2026-09-22','Customer','Site','COMPLETED',25)")
            execSQL("INSERT INTO work_items(id,visitId,serviceNameSnapshot,checklistReviewed,subjectType) VALUES('w','v','Service',0,'SITE')")
            execSQL("INSERT INTO dispatch_outbox_visits(dispatchVisitId,siteId,serviceDate,appointmentZoneId,createdAtEpochMillis,modifiedAtEpochMillis) VALUES('out','s','2026-09-22','Europe/Bucharest',1,1)")
            execSQL("INSERT INTO dispatch_outbox_items(dispatchItemId,dispatchVisitId,position,taskName,subjectType) VALUES('out-item','out',1,'Service','SITE')")
            execSQL("INSERT INTO dispatch_visit_bindings(dispatchVisitId,localVisitId,appliedGeneration,packageId,senderLabel,participantSnapshotJson,leaderIdsJson,teamSnapshotJson,appliedMaterialHash,controlledFingerprint,importedAtEpochMillis,updatedAtEpochMillis) VALUES('in','v',1,'p','Coordinator','[]','[]','[]','hash','fingerprint',1,1)")
            execSQL("INSERT INTO technician_identity(id,technicianId,displayName,createdAtEpochMillis,modifiedAtEpochMillis) VALUES('primary','TECH-1','Technician',1,1)")
            execSQL("INSERT INTO trusted_service_loop_ids(peerId,name,createdAtEpochMillis,modifiedAtEpochMillis) VALUES('TRUST-1','Office',1,1)")
            execSQL("INSERT INTO final_records(id,visitId,currentRevisionId,createdAtEpochMillis,voided) VALUES('record','v','rev',50,0)")
            execSQL("INSERT INTO final_record_revisions(id,recordId,revisionNumber,visitReference,actualServiceDate,recordedAtEpochMillis,customerName,siteName,businessName,technicianName,businessZoneId) VALUES('rev','record',1,'V-1','2026-09-22',50,'Customer','Site','Business','Technician','Europe/Bucharest')")
            execSQL("INSERT INTO final_work_items(id,revisionId,position,sourceWorkItemId,serviceName,outcome,fulfilledObligation,subjectType) VALUES('fw','rev',1,'w','Service','DONE',0,'SITE')")
            execSQL("INSERT INTO final_photo_entries(id,finalWorkItemId,position,sourceAttachmentId,storedRelativePath,sha256,byteSize,mimeType,addedInCorrection) VALUES('fp','fw',1,'a','photos/a.jpg','abc',3,'image/jpeg',0)")
            execSQL("INSERT INTO attachments(id,ownerType,ownerId,storedRelativePath,sha256,mimeType,includedInCustomerReport,availability,byteSize,caption) VALUES('a','WORK_ITEM','w','photos/a.jpg','abc','image/jpeg',1,'PRESENT',3,'Caption')")
            execSQL("INSERT INTO final_dispatch_visits(revisionId,dispatchVisitId,generation,senderLabel,documentingTechnicianId,documentingTechnicianName) VALUES('rev','in',1,'Coordinator','TECH-1','Technician')")
            close()
        }
        helper.runMigrationsAndValidate(name, 19, true, ServiceLoopDatabase.MIGRATION_18_19).use { migrated ->
            migrated.query("SELECT id,value,notes,position FROM customer_contacts WHERE customerId='c' ORDER BY position").use { rows ->
                check(rows.moveToFirst()); assertEquals("late", rows.getString(0)); assertEquals("123", rows.getString(1)); assertNull(rows.getString(2)); assertEquals(1, rows.getInt(3))
                check(rows.moveToNext()); assertEquals("early", rows.getString(0)); assertEquals("x@example.com", rows.getString(1)); assertEquals(2, rows.getInt(3))
            }
            migrated.query("SELECT localVisitId FROM dispatch_outbox_visits WHERE dispatchVisitId='out'").use { row -> check(row.moveToFirst()); assertNull(row.getString(0)) }
            migrated.query("SELECT assignmentIssuerId FROM dispatch_visit_bindings WHERE dispatchVisitId='in'").use { row -> check(row.moveToFirst()); assertNull(row.getString(0)) }
            migrated.query("SELECT assignmentMaterialHash,assignmentIssuerId FROM final_dispatch_visits WHERE revisionId='rev'").use { row -> check(row.moveToFirst()); assertNull(row.getString(0)); assertNull(row.getString(1)) }
            migrated.query("SELECT storedRelativePath,sha256,byteSize,includedInCustomerReport,visibility FROM final_photo_entries WHERE id='fp'").use { row -> check(row.moveToFirst()); assertEquals("photos/a.jpg",row.getString(0)); assertEquals("abc",row.getString(1)); assertEquals(3L,row.getLong(2)); assertEquals(1,row.getInt(3)); assertEquals("PUBLIC",row.getString(4)) }
            migrated.query("SELECT caption,visibility FROM attachments WHERE id='a'").use { row -> check(row.moveToFirst()); assertEquals("Caption",row.getString(0)); assertEquals("PUBLIC",row.getString(1)) }
            migrated.query("SELECT displayName FROM technician_identity WHERE id='primary'").use { row -> check(row.moveToFirst()); assertEquals("Technician",row.getString(0)) }
            migrated.query("SELECT name FROM trusted_service_loop_ids WHERE peerId='TRUST-1'").use { row -> check(row.moveToFirst()); assertEquals("Office",row.getString(0)) }
        }
    }
}
