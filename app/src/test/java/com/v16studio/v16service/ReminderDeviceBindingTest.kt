package com.v16studio.v16service

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.v16service.data.RecoveryMetadataEntity
import com.v16studio.v16service.data.V16ServiceDatabase
import com.v16studio.v16service.domain.BusinessTime
import com.v16studio.v16service.reminders.ReminderCoordinator
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderDeviceBindingTest {
    @Test fun requestRequiresCurrentDatasetAndAdoptionTokenAcrossReopenAndRollback() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("v16service_reminder_device", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val database = Room.inMemoryDatabaseBuilder(context, V16ServiceDatabase::class.java).allowMainThreadQueries().build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val original = RecoveryMetadataEntity(datasetId = "dataset-a", firstBusinessWriteAtEpochMillis = null,
                lastBusinessWriteAtEpochMillis = null, lastBackupAttemptAtEpochMillis = null,
                lastVerifiedFullBackupAtEpochMillis = null, lastVerifiedSnapshotAtEpochMillis = null,
                lastVerifiedDestination = null, lastVerifiedSize = null, adoptionToken = "first")
            val dao = database.v16ServiceDao()
            dao.upsertRecoveryMetadata(original)
            val time = object : BusinessTime {
                override val zoneId = ZoneId.of("UTC")
                override fun instant(): Instant = Instant.parse("2026-09-24T10:00:00Z")
            }
            val coordinator = ReminderCoordinator(context, database, time, scope)
            coordinator.setDeliveryRequested(true)
            assertTrue(coordinator.deliveryRequested())
            assertTrue(ReminderCoordinator(context, database, time, scope).deliveryRequested())
            dao.upsertRecoveryMetadata(original.copy(adoptionToken = "second"))
            assertFalse(coordinator.deliveryRequested())
            coordinator.setDeliveryRequested(true)
            assertTrue(coordinator.deliveryRequested())
            dao.upsertRecoveryMetadata(original.copy(datasetId = "dataset-b", adoptionToken = "second"))
            assertFalse(coordinator.deliveryRequested())
            dao.upsertRecoveryMetadata(original)
            assertFalse(coordinator.deliveryRequested())
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            database.close()
            preferences.edit().clear().commit()
        }
    }
}
