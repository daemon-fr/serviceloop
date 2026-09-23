package com.v16studio.serviceloop

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Room17To18MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ServiceLoopDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationKeepsTechnicianIdentityAndCreatesEmptyTrustedIdTable() {
        val databaseName = "b048-room-17-18.db"
        helper.createDatabase(databaseName, 17).apply {
            execSQL("INSERT INTO technician_identity(id, technicianId, displayName, createdAtEpochMillis, modifiedAtEpochMillis) VALUES('primary', 'SL-TEST-IDENTITY', 'Coordinator', 11, 22)")
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 18, true, ServiceLoopDatabase.MIGRATION_17_18).use { migrated ->
            migrated.query("SELECT technicianId, displayName FROM technician_identity WHERE id='primary'").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("SL-TEST-IDENTITY", cursor.getString(0))
                assertEquals("Coordinator", cursor.getString(1))
            }
            migrated.query("SELECT COUNT(*) FROM trusted_service_loop_ids").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }
}
