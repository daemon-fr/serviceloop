package com.v16studio.serviceloop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class B049WorkResultExportTest {
    private lateinit var database: ServiceLoopDatabase
    private lateinit var root: File
    private val technician = TechnicianIdCodec.generate()
    private val issuer = TechnicianIdCodec.generate()

    @Before fun setUp() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        ServiceLoopDatabase.configureStage4Tracking(database.openHelper.writableDatabase)
        root = File(context.cacheDir, "b049-result-export-${System.nanoTime()}").apply { mkdirs() }
        val dao = database.serviceLoopDao()
        dao.insertCustomers(listOf(CustomerEntity("c", "CU-1", "Customer")))
        dao.insertSites(listOf(SiteEntity("s", "c", "ST-1", "Site", null, null)))
        dao.insertVisits(listOf(WorkingVisitEntity("v", "V-1", "c", "s", "2026-09-23", "Customer", "Site", null, "FINALIZED", 1)))
        dao.insertWorkItems(listOf(WorkItemEntity("w", "v", null, null, null, null, null, null, "Site inspection", null, null, null, null, false, null, null, subjectType = "SITE")))
        dao.insertFinalRecord(FinalRecordEntity("record", "v", "revision", 2))
        dao.insertFinalRevision(FinalRecordRevisionEntity("revision", "record", 1, "V-1", "2026-09-23", 2, "Customer", "Site", null, "Business", "Tech", null, null, null, "Europe/Bucharest", null, "CU-1", "ST-1"))
        dao.insertFinalWorkItems(listOf(FinalWorkItemEntity("final-work", "revision", 1, "w", null, null, null, null, null, null, null, "Site inspection", null, null, "PERFORMED", "Inspected site", null, false, null, null, null, null, null, null, subjectType = "SITE")))
        val dispatch = database.dispatchDao()
        dispatch.insertTechnicianIdentity(TechnicianIdentityEntity("primary", technician, "Tech", 1, 1))
        dispatch.insertFinalDispatchVisit(FinalDispatchVisitEntity("revision", "dispatch-v", 1, null, "Coordinator", technician, "Tech", "a".repeat(64), issuer))
        dispatch.insertFinalDispatchItems(listOf(FinalDispatchItemEntity("final-work", "dispatch-i", "[]", "ASSIGNED", "TECHNICIAN")))
    }

    @After fun tearDown() { database.close(); root.deleteRecursively() }

    @Test fun issuerTrustAndDocumentingIdentityGateExportAndRevisionIdentityIsStable() = runTest {
        val exchange = WorkResultExchangeService(database, root)
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { exchange.exportFinalRevisions(listOf("revision")) } }
        ServiceLoopPeerTrustStore(database).add(issuer, "Coordinator")
        val firstBytes = exchange.exportFinalRevisions(listOf("revision"))
        assertEquals(2, ServiceLoopSyncEnvelopeCodec.decode(firstBytes).manifest.sections.first().version)
        val first = WorkResultPackageCodec.decode(firstBytes)
        val second = WorkResultPackageCodec.decode(exchange.exportFinalRevisions(listOf("revision")))
        assertEquals(issuer, first.targetIssuerId)
        assertEquals(technician, first.exporterId)
        assertEquals(first.results.single().value.getString("resultId"), second.results.single().value.getString("resultId"))
        assertEquals("revision", first.results.single().value.getString("sourceFinalRevisionId"))
        assertEquals("v", first.results.single().value.getString("sourceVisitId"))
        assertEquals("w", first.results.single().value.getString("sourceWorkItemId"))
        assertEquals(1, first.results.single().value.getInt("sourceWorkItemPosition"))
        assertEquals(1, first.results.single().value.getInt("sourceFinalRevisionNumber"))
        assertTrue(first.results.single().value.isNull("supersedesSourceFinalRevisionId"))
        database.openHelper.writableDatabase.execSQL("UPDATE final_dispatch_visits SET documentingTechnicianId=?, documentingTechnicianName=? WHERE revisionId='revision'", arrayOf(issuer, "Wrong author"))
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { exchange.exportFinalRevisions(listOf("revision")) } }
    }

    @Test fun capturedFollowUpsStayFixedWhenLivePlanningChanges() = runTest {
        val dao = database.serviceLoopDao()
        ServiceLoopPeerTrustStore(database).add(issuer, "Coordinator")
        val original = FollowUpEntity("follow-up", "FU-1", "SERVICE", "Call customer", "2026-09-30", "OPEN",
            "c", "s", null, "Private plan", "v", "w", 2)
        dao.insertFollowUp(original)
        val snapshot = FinalFollowUpSnapshot.capture(2, listOf(original))
        assertEquals(1, dao.setFinalFollowUpSnapshot("final-work", snapshot))
        val exchange = WorkResultExchangeService(database, root)
        val first = WorkResultPackageCodec.decode(exchange.exportFinalRevisions(listOf("revision"))).results.single().value
        dao.updateFollowUp(original.copy(title = "Changed today", state = "CANCELLED", privatePlanningNote = "New plan", updatedAtEpochMillis = 10))
        val second = WorkResultPackageCodec.decode(exchange.exportFinalRevisions(listOf("revision"))).results.single().value
        assertEquals("CAPTURED_AT_REVISION", second.getString("followUpCaptureState"))
        assertEquals(first.getJSONArray("followUps").toString(), second.getJSONArray("followUps").toString())
        assertEquals("Call customer", second.getJSONArray("followUps").getJSONObject(0).getString("title"))
    }

    @Test fun resultPhotosComeOnlyFromFrozenRevisionAndKeepIndependentFlags() = runTest {
        val dao = database.serviceLoopDao()
        ServiceLoopPeerTrustStore(database).add(issuer, "Coordinator")
        val frozen = listOf(
            Triple("public", true, "PUBLIC"),
            Triple("operational", false, "PUBLIC"),
            Triple("private", true, "PRIVATE"),
        )
        frozen.forEachIndexed { index, (id, included, visibility) ->
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(listOf(Color.RED, Color.BLUE, Color.GREEN)[index]) }
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it); bitmap.recycle() }.toByteArray()
            val path = "$id.jpg"
            File(root, path).writeBytes(bytes)
            val hash = WorkResultPackageCodec.sha256(bytes)
            dao.insertAttachments(listOf(AttachmentEntity(id, "WORK_ITEM", "w", path, hash, null, "image/jpeg", included, "PRESENT", bytes.size.toLong(), "live-$id", visibility)))
            dao.insertFinalPhotos(listOf(FinalPhotoEntryEntity("final-$id", "final-work", index + 1, id, path, hash, bytes.size.toLong(), "image/jpeg", "frozen-$id", includedInCustomerReport = included, visibility = visibility)))
        }
        val exchange = WorkResultExchangeService(database, root)
        val first = WorkResultPackageCodec.decode(exchange.exportFinalRevisions(listOf("revision"))).results.single().photos
        assertEquals(3, first.size)
        assertEquals(listOf(true, false, true), first.map { it.includeInReport })
        assertEquals(listOf("PUBLIC", "PUBLIC", "PRIVATE"), first.map { it.visibility })
        assertEquals(listOf("frozen-public", "frozen-operational", "frozen-private"), first.map { it.caption })
        database.openHelper.writableDatabase.execSQL("UPDATE attachments SET caption='changed', includedInCustomerReport=0, visibility='INTERNAL' WHERE id='public'")
        dao.deleteAttachment("operational")
        val second = WorkResultPackageCodec.decode(exchange.exportFinalRevisions(listOf("revision"))).results.single().photos
        assertEquals(first.map { Triple(it.sourcePhotoId, it.caption, it.visibility) }, second.map { Triple(it.sourcePhotoId, it.caption, it.visibility) })
        assertEquals(first.map { it.includeInReport }, second.map { it.includeInReport })
        val repository = RoomServiceLoopRepository(database, object : com.v16studio.serviceloop.domain.BusinessTime {
            override val zoneId = java.time.ZoneId.of("UTC")
            override fun instant() = java.time.Instant.parse("2026-09-23T12:00:00Z")
        }, attachmentRoot = root)
        val publicPhotos = repository.finalRecordRevision("record", "revision")!!.public.lines.single().photos
        assertEquals(1, publicPhotos.size)
        assertTrue(publicPhotos.single().caption == "frozen-public")
        dao.insertFinalRevision(dao.finalRevision("revision")!!.copy(id = "revision-2", revisionNumber = 2, supersedesRevisionId = "revision"))
        dao.insertFinalWorkItems(listOf(dao.finalWorkItems("revision").single().copy(id = "final-work-2", revisionId = "revision-2")))
        database.dispatchDao().insertFinalDispatchVisit(database.dispatchDao().finalDispatchVisit("revision")!!.copy(revisionId = "revision-2"))
        database.dispatchDao().insertFinalDispatchItems(listOf(database.dispatchDao().finalDispatchItems("revision").single().copy(finalWorkItemId = "final-work-2")))
        dao.insertFinalPhotos(listOf(dao.finalPhotos("final-work").single { it.sourceAttachmentId == "private" }
            .copy(id = "corrected-private", finalWorkItemId = "final-work-2", position = 1, caption = "corrected-private")))
        database.openHelper.writableDatabase.execSQL("UPDATE final_records SET currentRevisionId='revision-2' WHERE id='record'")
        val oldAgain = WorkResultPackageCodec.decode(exchange.exportFinalRevisions(listOf("revision"))).results.single()
        val corrected = WorkResultPackageCodec.decode(exchange.exportFinalRevisions(listOf("revision-2"))).results.single()
        assertEquals(3, oldAgain.photos.size)
        assertEquals("revision", oldAgain.value.getString("sourceFinalRevisionId"))
        assertEquals(1, corrected.photos.size)
        assertEquals("corrected-private", corrected.photos.single().caption)
        assertEquals("revision-2", corrected.value.getString("sourceFinalRevisionId"))
    }

    @Test fun retainedDerivativeBytesStayIdenticalBeforeAndAfterOriginalRemoval() = runTest {
        ServiceLoopPeerTrustStore(database).add(issuer, "Coordinator")
        val bitmap = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        val original = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it); bitmap.recycle() }.toByteArray()
        val derivative = AppOwnedImageNormalizer.workResultDerivative(original)
        val originalPath = "attachments/source.jpg"
        val derivativePath = "retained-images/source.jpg"
        File(root, originalPath).apply { parentFile!!.mkdirs(); writeBytes(original) }
        File(root, derivativePath).apply { parentFile!!.mkdirs(); writeBytes(derivative.bytes) }
        val dao = database.serviceLoopDao()
        dao.insertFinalPhotos(listOf(FinalPhotoEntryEntity("final-source", "final-work", 1, "source", originalPath,
            WorkResultPackageCodec.sha256(original), original.size.toLong(), "image/jpeg", "Frozen", includedInCustomerReport = true, visibility = "PUBLIC")))
        dao.insertRetainedImage(RetainedImageEntity("retained-source", "ATTACHMENT", "source", originalPath, null,
            derivativePath, WorkResultPackageCodec.sha256(derivative.bytes), derivative.bytes.size.toLong(), derivative.width,
            derivative.height, "image/jpeg", 1))
        val exchange = WorkResultExchangeService(database, root)
        val first = WorkResultPackageCodec.decode(exchange.exportFinalRevisions(listOf("revision"))).results.single().photos.single().bytes
        assertTrue(derivative.bytes.contentEquals(first))
        File(root, originalPath).delete()
        dao.updateRetainedImage(dao.retainedImage("ATTACHMENT", "source")!!.copy(originalDeletedAtEpochMillis = 2))
        val second = WorkResultPackageCodec.decode(exchange.exportFinalRevisions(listOf("revision"))).results.single().photos.single().bytes
        assertTrue(first.contentEquals(second))
        val transfer = DataTransferCodec.decode(DataTransferExportService(database, root).export(
            ExportCenterSelection(families = setOf(ExportFamily.PHOTO_METADATA, ExportFamily.IMAGE_FILES))))
        assertTrue(derivative.bytes.contentEquals(transfer.binaries.values.single()))
    }
}
