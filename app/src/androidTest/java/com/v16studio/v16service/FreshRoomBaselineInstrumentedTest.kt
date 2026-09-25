package com.v16studio.v16service

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.v16studio.v16service.data.CustomerEntity
import com.v16studio.v16service.data.V16ServiceDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FreshRoomBaselineInstrumentedTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun productionDatabaseOpensFreshCurrentSchemaWithoutBusinessRows() = runBlocking {
        val database = V16ServiceDatabase.open(context)
        try {
            val sql = database.openHelper.writableDatabase
            assertEquals(1, sql.version)
            fun rows(table: String): Long = sql.query("SELECT COUNT(*) FROM $table").use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getLong(0)
            }
            listOf(
                "customers", "customer_contacts", "sites", "equipment", "service_plans",
                "service_obligations", "working_visits", "work_items", "follow_ups", "final_records",
                "final_record_revisions", "remote_final_results", "transferred_final_results",
            ).forEach { table -> assertEquals("Fresh $table table", 0L, rows(table)) }
            sql.query("PRAGMA integrity_check").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
            sql.query("PRAGMA foreign_key_check").use { cursor ->
                assertTrue("Fresh persistent database has no foreign-key violations", !cursor.moveToFirst())
            }
        } finally {
            database.close()
        }
    }

    @Test fun freshDatabaseUsesCurrentSchemaAndEnforcesSnapshotColumns() = runBlocking {
        val name = "b050-fresh-${UUID.randomUUID()}.db"
        val database = Room.databaseBuilder(context, V16ServiceDatabase::class.java, name).build()
        try {
            val sql = database.openHelper.writableDatabase
            assertEquals(1, sql.version)
            val tables = sql.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            assertTrue(tables.containsAll(setOf("customers", "service_plans", "working_visits", "final_work_items", "remote_final_results", "transferred_final_results")))
            val indexes = sql.query("SELECT name FROM sqlite_master WHERE type='index'").use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            assertTrue(indexes.containsAll(setOf(
                "index_customers_reference",
                "index_final_work_items_revisionId_position",
                "index_remote_final_results_technicianId_resultId_sourceFinalRevisionId",
                "index_transferred_final_results_originWorkspaceId_sourceWorkItemId_sourceFinalRevisionId",
            )))

            fun assertRequired(table: String, columnName: String) {
                val isRequired = sql.query("PRAGMA table_info('$table')").use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    val requiredIndex = cursor.getColumnIndexOrThrow("notnull")
                    var found = false
                    while (cursor.moveToNext()) if (cursor.getString(nameIndex) == columnName) {
                        found = cursor.getInt(requiredIndex) == 1
                        break
                    }
                    found
                }
                assertTrue("$table.$columnName must be NOT NULL", isRequired)
            }
            assertRequired("final_work_items", "followUpsSnapshotJson")
            assertRequired("remote_final_results", "sourcePayloadJson")
            assertRequired("transferred_final_results", "sourcePayloadJson")

            val customer = CustomerEntity("b050-customer", "CU-B050", "Fresh workspace customer")
            database.v16ServiceDao().insertCustomers(listOf(customer))
            assertEquals(customer, database.v16ServiceDao().customer(customer.id))
            sql.query("PRAGMA integrity_check").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
            sql.query("PRAGMA foreign_key_check").use { cursor -> assertTrue("Fresh database has no foreign-key violations", !cursor.moveToFirst()) }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
