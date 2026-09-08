package com.v16studio.serviceloop

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration1To2Test {
    private val dbName = "sl3-migration-test.db"

    @Test fun migrationOneToSevenPreservesRowsThroughTheRegisteredChain() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.deleteDatabase(dbName)
        val schema = JSONObject(instrumentation.context.assets.open("com.v16studio.serviceloop.data.ServiceLoopDatabase/1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", table))
            }
            old.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            old.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '${schema.getString("identityHash")}')")
            old.execSQL("INSERT INTO customers(id,reference,name) VALUES('customer-1','CU-1','Preserved customer')")
            old.execSQL("INSERT INTO sites(id,customerId,reference,name,address,privateAccessNotes) VALUES('site-1','customer-1','ST-1','Preserved site',NULL,'private')")
            old.execSQL("INSERT INTO equipment(id,siteId,reference,technicianIdentifier,name,make,model,serialNumber,privateNotes) VALUES('equipment-1','site-1','EQ-1',NULL,'Preserved equipment',NULL,NULL,NULL,NULL)")
            old.execSQL("INSERT INTO service_plans(id,equipmentId,reference,name,intervalCount,intervalUnit,currentDueDate,state,currentObligationId) VALUES('plan-1','equipment-1','P-1','Plan',3,'MONTHS','2026-09-01','ACTIVE',NULL)")
            old.version = 1
        }
        val migrated = Room.databaseBuilder(context, ServiceLoopDatabase::class.java, dbName).addMigrations(ServiceLoopDatabase.MIGRATION_1_2, ServiceLoopDatabase.MIGRATION_2_3, ServiceLoopDatabase.MIGRATION_3_4, ServiceLoopDatabase.MIGRATION_4_5, ServiceLoopDatabase.MIGRATION_5_6, ServiceLoopDatabase.MIGRATION_6_7).build()
        try {
            val dao = migrated.serviceLoopDao()
            kotlinx.coroutines.runBlocking {
                assertEquals("Preserved customer", dao.customer("customer-1")?.name)
                assertEquals(null, dao.plan("plan-1")?.lastCountedCompletionDate)
                assertEquals(0, dao.finalRecordCount())
            }
        } finally { migrated.close(); context.deleteDatabase(dbName) }
    }

    @Test fun migrationTwoToSevenBackfillsWorkingSnapshotsAndPreservesFinalReportAndObligation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation(); val context = instrumentation.targetContext
        context.deleteDatabase(dbName)
        val schema = JSONObject(instrumentation.context.assets.open("com.v16studio.serviceloop.data.ServiceLoopDatabase/2.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices -> for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", table)) }
            }
            old.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            old.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '${schema.getString("identityHash")}')")
            old.execSQL("INSERT INTO customers VALUES('c','CU-9','Customer')")
            old.execSQL("INSERT INTO sites VALUES('s','c','ST-9','Site','Address','private')")
            old.execSQL("INSERT INTO equipment VALUES('e','s','EQ-9','TECH-9','Equipment','Maker','Model','Serial','private')")
            old.execSQL("INSERT INTO service_plans VALUES('p','e','P-9','Plan',3,'MONTHS','2026-12-05','ACTIVE','o2','2026-09-05','rev')")
            old.execSQL("INSERT INTO service_obligations VALUES('o1','p',1,'2026-09-01',1,2,'rev')")
            old.execSQL("INSERT INTO service_obligations VALUES('o2','p',2,'2026-12-05',2,NULL,NULL)")
            old.execSQL("INSERT INTO working_visits VALUES('v','V-9','c','s','2026-09-05','Captured customer','Captured site','Captured address','FINALIZED',2)")
            old.execSQL("INSERT INTO work_items VALUES('w','v','e','p','o1',NULL,'Captured equipment','EQ-9','Service','P-9','2026-09-01',3,'MONTHS',0,'PERFORMED',1,NULL,'2026-12-05',1,NULL)")
            old.execSQL("INSERT INTO final_records VALUES('r','v','rev',2)")
            old.execSQL("INSERT INTO final_record_revisions VALUES('rev','r',1,'V-9','2026-09-05',2,'Captured customer','Captured site','Captured address','Business','Technician',NULL,NULL,NULL,'Europe/Bucharest',NULL)")
            old.execSQL("INSERT INTO final_work_items VALUES('fw','rev',1,'w','e','Captured equipment','EQ-9','TECH-9','Maker','Model','Serial','Service','p','P-9','PERFORMED','Done',NULL,1,'2026-09-01','2026-12-05',3,'MONTHS','o1',NULL)")
            old.execSQL("INSERT INTO report_renditions VALUES('rr','rev',1,3,'reports/r/rr.pdf','hash',65369,1,'READY','ORIGINAL',NULL)")
            old.version = 2
        }
        val migrated = Room.databaseBuilder(context, ServiceLoopDatabase::class.java, dbName).addMigrations(ServiceLoopDatabase.MIGRATION_2_3, ServiceLoopDatabase.MIGRATION_3_4, ServiceLoopDatabase.MIGRATION_4_5, ServiceLoopDatabase.MIGRATION_5_6, ServiceLoopDatabase.MIGRATION_6_7).build()
        try { kotlinx.coroutines.runBlocking {
            val dao = migrated.serviceLoopDao(); val visit = dao.visit("v")!!; val work = dao.workItem("w")!!; val revision = dao.finalRevision("rev")!!
            assertEquals("CU-9", visit.customerReferenceSnapshot); assertEquals("ST-9", visit.siteReferenceSnapshot)
            assertEquals("TECH-9", work.equipmentIdentifierSnapshot); assertEquals("Maker", work.equipmentMakeSnapshot)
            assertEquals("CU-9", revision.customerReference); assertEquals("ST-9", revision.siteReference)
            assertEquals("2026-12-05", dao.plan("p")!!.currentDueDate); assertEquals("READY", dao.reportRendition("rev")!!.status); assertEquals(1, dao.finalRecordCount())
        } } finally { migrated.close(); context.deleteDatabase(dbName) }
    }

    @Test fun migrationThreeToSevenPreservesDailyWorkAndBackfillsActiveClaim() {
        val instrumentation = InstrumentationRegistry.getInstrumentation(); val context = instrumentation.targetContext
        context.deleteDatabase(dbName)
        val schema = JSONObject(instrumentation.context.assets.open("com.v16studio.serviceloop.data.ServiceLoopDatabase/3.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) { val entity=entities.getJSONObject(i); val table=entity.getString("tableName"); old.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}",table)); entity.optJSONArray("indices")?.let { indices -> for(j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}",table)) } }
            old.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"); old.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '${schema.getString("identityHash")}')")
            old.execSQL("INSERT INTO customers(id,reference,name) VALUES('c','CU-1','Preserved customer')")
            old.execSQL("INSERT INTO sites(id,customerId,reference,name,address,privateAccessNotes) VALUES('s','c','ST-1','Preserved site','Address','PRIVATE_ACCESS')")
            old.execSQL("INSERT INTO equipment(id,siteId,reference,technicianIdentifier,name,make,model,serialNumber,privateNotes) VALUES('e','s','EQ-1','E-1','Preserved equipment','Make','Model','Serial','PRIVATE_EQUIPMENT')")
            old.execSQL("INSERT INTO service_plans(id,equipmentId,reference,name,intervalCount,intervalUnit,currentDueDate,state,currentObligationId,lastCountedCompletionDate,lastCountedRevisionId) VALUES('p','e','P-1','Plan',3,'MONTHS','2026-12-05','ACTIVE','o',NULL,NULL)")
            old.execSQL("INSERT INTO service_obligations(id,planId,sequence,dueDate,createdAtEpochMillis,consumedAtEpochMillis,consumedByRevisionId) VALUES('o','p',2,'2026-12-05',1,NULL,NULL)")
            old.execSQL("INSERT INTO working_visits(id,reference,customerId,siteId,actualServiceDate,customerNameSnapshot,siteNameSnapshot,siteAddressSnapshot,state,modifiedAtEpochMillis,customerReferenceSnapshot,siteReferenceSnapshot) VALUES('v','V-2','c','s','2026-09-08','Preserved customer','Preserved site','Address','BOOKED',2,'CU-1','ST-1')")
            old.execSQL("INSERT INTO work_items(id,visitId,equipmentId,servicePlanId,capturedObligationId,templateSnapshotId,equipmentNameSnapshot,equipmentReferenceSnapshot,serviceNameSnapshot,planReferenceSnapshot,dueDateSnapshot,intervalCountSnapshot,intervalUnitSnapshot,checklistReviewed,outcome,fulfillsCurrentObligation) VALUES('w','v','e','p','o',NULL,'Preserved equipment','EQ-1','Plan','P-1','2026-12-05',3,'MONTHS',0,NULL,0)")
            old.version=3
        }
        val migrated=Room.databaseBuilder(context,ServiceLoopDatabase::class.java,dbName).addMigrations(ServiceLoopDatabase.MIGRATION_3_4, ServiceLoopDatabase.MIGRATION_4_5, ServiceLoopDatabase.MIGRATION_5_6, ServiceLoopDatabase.MIGRATION_6_7).build()
        try { kotlinx.coroutines.runBlocking { val dao=migrated.serviceLoopDao(); assertEquals("2026-12-05",dao.plan("p")!!.currentDueDate); assertEquals("v",dao.dueServices().single().claimedVisitId); assertEquals("ACTIVE",dao.customer("c")!!.state); assertEquals("PRIVATE_ACCESS",dao.site("s")!!.privateAccessNotes) } } finally { migrated.close(); context.deleteDatabase(dbName) }
    }

    @Test fun migrationFourToSevenPreservesDirectoryAndCreatesRecoveryFoundation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation(); val context = instrumentation.targetContext
        context.deleteDatabase(dbName)
        val schema = JSONObject(instrumentation.context.assets.open("com.v16studio.serviceloop.data.ServiceLoopDatabase/4.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) { val entity=entities.getJSONObject(i); val table=entity.getString("tableName"); old.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}",table)); entity.optJSONArray("indices")?.let { indices -> for(j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}",table)) } }
            old.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"); old.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '${schema.getString("identityHash")}')")
            old.execSQL("INSERT INTO customers(id,reference,name,state) VALUES('c4','CU-4','Version four customer','ACTIVE')")
            old.version=4
        }
        val migrated=Room.databaseBuilder(context,ServiceLoopDatabase::class.java,dbName).addMigrations(ServiceLoopDatabase.MIGRATION_4_5, ServiceLoopDatabase.MIGRATION_5_6, ServiceLoopDatabase.MIGRATION_6_7).build()
        try { kotlinx.coroutines.runBlocking { val dao=migrated.serviceLoopDao(); assertEquals("Version four customer",dao.customer("c4")!!.name); assertEquals(0,dao.correctionDrafts().size); assertEquals(32,dao.recoveryMetadata()!!.datasetId.length) } } finally { migrated.close(); context.deleteDatabase(dbName) }
    }

    @Test fun everyRetainedSchemaMigratesToSevenWithIdentityAndForeignKeysIntact() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        for(version in 1..6){context.deleteDatabase(dbName);val schema=JSONObject(instrumentation.context.assets.open("com.v16studio.serviceloop.data.ServiceLoopDatabase/$version.json").bufferedReader().use{it.readText()}).getJSONObject("database");SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName),null).use{old->val entities=schema.getJSONArray("entities");for(i in 0 until entities.length()){val entity=entities.getJSONObject(i);val table=entity.getString("tableName");old.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}",table));entity.optJSONArray("indices")?.let{indices->for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}",table))}};old.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");old.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '${schema.getString("identityHash")}')");old.version=version};val migrated=Room.databaseBuilder(context,ServiceLoopDatabase::class.java,dbName).addMigrations(ServiceLoopDatabase.MIGRATION_1_2,ServiceLoopDatabase.MIGRATION_2_3,ServiceLoopDatabase.MIGRATION_3_4,ServiceLoopDatabase.MIGRATION_4_5,ServiceLoopDatabase.MIGRATION_5_6,ServiceLoopDatabase.MIGRATION_6_7).build();try{kotlinx.coroutines.runBlocking{assertEquals("primary",migrated.dispatchDao().technicianIdentity()?.id)};migrated.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use{assertEquals(0,it.count)}}finally{migrated.close();context.deleteDatabase(dbName)}}
    }
}
