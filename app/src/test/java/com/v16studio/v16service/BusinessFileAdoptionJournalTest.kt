package com.v16studio.v16service

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.v16service.data.BusinessFileAdoptionJournal
import com.v16studio.v16service.data.RecoveryMetadataEntity
import com.v16studio.v16service.data.V16ServiceDatabase
import com.v16studio.v16service.data.TransferredEvidenceEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BusinessFileAdoptionJournalTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun interruptedUncommittedAdoptionRemovesOnlyItsOwnedBytes() {
        val root = File(context.cacheDir, "adoption-uncommitted-${System.nanoTime()}").apply { mkdirs() }
        val db = Room.inMemoryDatabaseBuilder(context, V16ServiceDatabase::class.java).allowMainThreadQueries().build()
        try {
            val path = "transferred-evidence/new.jpg"
            val unrelated = File(root, "transferred-evidence/owner.jpg").apply {
                parentFile!!.mkdirs(); writeBytes(byteArrayOf(7))
            }
            BusinessFileAdoptionJournal.begin(db, root, mapOf(path to byteArrayOf(1, 2, 3)))
                .adopt(path, byteArrayOf(1, 2, 3))
            assertTrue(File(root, path).isFile)
            assertTrue(BusinessFileAdoptionJournal.recoverInterrupted(db, root))
            assertFalse(File(root, path).exists())
            assertArrayEquals(byteArrayOf(7), unrelated.readBytes())
            assertFalse(File(root, "recovery/business-file-adoption.json").exists())
        } finally { db.close(); root.deleteRecursively() }
    }

    @Test fun interruptedCommittedAdoptionKeepsReferencedBytes() = runBlocking {
        val root = File(context.cacheDir, "adoption-committed-${System.nanoTime()}").apply { mkdirs() }
        val db = Room.inMemoryDatabaseBuilder(context, V16ServiceDatabase::class.java).allowMainThreadQueries().build()
        try {
            val path = "transferred-evidence/committed.jpg"
            val bytes = byteArrayOf(4, 5, 6)
            val hash = com.v16studio.v16service.data.WorkResultPackageCodec.sha256(bytes)
            BusinessFileAdoptionJournal.begin(db, root, mapOf(path to bytes)).adopt(path, bytes)
            db.v16ServiceDao().insertTransferredEvidence(listOf(TransferredEvidenceEntity(
                "local-photo", "source-photo-key", "origin", "source-photo", "visit", "work", null, null,
                "relay", null, null, null, "2026-09-24", "VISIT", "Service", path, hash,
                bytes.size.toLong(), 1, 1, "image/jpeg", null, "PUBLIC", true, 1L, "{}")))
            assertTrue(BusinessFileAdoptionJournal.recoverInterrupted(db, root))
            assertArrayEquals(bytes, File(root, path).readBytes())
            assertFalse(File(root, "recovery/business-file-adoption.json").exists())
        } finally { db.close(); root.deleteRecursively() }
    }

    @Test fun damagedJournalRestrictsRecoveryWithoutDeletingEvidence() = runBlocking {
        val root = File(context.cacheDir, "adoption-damaged-${System.nanoTime()}").apply { mkdirs() }
        val db = Room.inMemoryDatabaseBuilder(context, V16ServiceDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.v16ServiceDao().upsertRecoveryMetadata(RecoveryMetadataEntity(datasetId = "dataset",
                firstBusinessWriteAtEpochMillis = null, lastBusinessWriteAtEpochMillis = null,
                lastBackupAttemptAtEpochMillis = null, lastVerifiedFullBackupAtEpochMillis = null,
                lastVerifiedSnapshotAtEpochMillis = null, lastVerifiedDestination = null, lastVerifiedSize = null))
            val path = "transferred-evidence/unknown.jpg"
            BusinessFileAdoptionJournal.begin(db, root, mapOf(path to byteArrayOf(1))).adopt(path, byteArrayOf(1))
            File(root, "recovery/business-file-adoption.json").writeText("broken")
            assertFalse(BusinessFileAdoptionJournal.recoverInterrupted(db, root))
            assertTrue(db.v16ServiceDao().recoveryMetadata()!!.restrictedRecoveryState)
            assertTrue(File(root, path).isFile)
            assertTrue(File(root, "recovery/business-file-adoption.json").isFile)
        } finally { db.close(); root.deleteRecursively() }
    }

    @Test fun databaseOpenReconcilesUncommittedAdoptionBeforeNormalUse() {
        val path = "transferred-evidence/startup-${System.nanoTime()}.jpg"
        try {
            val first = V16ServiceDatabase.open(context)
            BusinessFileAdoptionJournal.begin(first, context.filesDir, mapOf(path to byteArrayOf(9)))
                .adopt(path, byteArrayOf(9))
            first.close()
            val reopened = V16ServiceDatabase.open(context)
            try {
                assertFalse(File(context.filesDir, path).exists())
                assertFalse(File(context.filesDir, "recovery/business-file-adoption.json").exists())
                assertNotNull(reopened.openHelper.writableDatabase)
            } finally { reopened.close() }
        } finally { File(context.filesDir, path).delete() }
    }
}
