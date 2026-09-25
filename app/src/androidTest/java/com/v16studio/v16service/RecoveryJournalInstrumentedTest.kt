package com.v16studio.v16service

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.v16service.data.CustomerEntity
import com.v16studio.v16service.data.RecoveryPackage
import com.v16studio.v16service.data.V16ServiceDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RecoveryJournalInstrumentedTest {
    @Test fun atomicJournalRestoresCommittedSnapshotOnDevice() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, V16ServiceDatabase::class.java).build()
        val root = File(context.cacheDir, "recovery-journal-${System.nanoTime()}").apply { mkdirs() }
        try {
            V16ServiceDatabase.configureStage4Tracking(database.openHelper.writableDatabase); V16ServiceDatabase.configureReminderDefaults(database.openHelper.writableDatabase)
            val dao = database.v16ServiceDao()
            dao.insertCustomers(listOf(CustomerEntity("customer", "CU-1", "Before")))
            val recovery = RecoveryPackage(database, root)
            val secret = "synthetic device recovery".toCharArray()
            val backup = recovery.create(secret, false)
            dao.renameCustomer("customer", "After")
            recovery.restore(recovery.inspect(backup.bytes, secret))
            assertEquals("Before", dao.customer("customer")!!.name)
            assertFalse(File(root, "recovery/restore-journal.json").exists())
            assertFalse(File(root, "recovery/restore-journal.json.pending").exists())
        } finally { database.close(); root.deleteRecursively() }
    }
}
