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
    private val dbName = "sl2-migration-test.db"

    @Test fun migrationPreservesVersionOneRowsAndAddsCoherentDefaults() {
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
        val migrated = Room.databaseBuilder(context, ServiceLoopDatabase::class.java, dbName).addMigrations(ServiceLoopDatabase.MIGRATION_1_2).build()
        try {
            val dao = migrated.serviceLoopDao()
            kotlinx.coroutines.runBlocking {
                assertEquals("Preserved customer", dao.customer("customer-1")?.name)
                assertEquals(null, dao.plan("plan-1")?.lastCountedCompletionDate)
                assertEquals(0, dao.finalRecordCount())
            }
        } finally { migrated.close(); context.deleteDatabase(dbName) }
    }
}
