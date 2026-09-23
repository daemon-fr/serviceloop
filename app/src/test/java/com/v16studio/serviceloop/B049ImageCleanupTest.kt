package com.v16studio.serviceloop

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.domain.ServiceLoopScopeFilter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class B049ImageCleanupTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: ServiceLoopDatabase
    private lateinit var directory: File
    private lateinit var cleanup: ImageCleanupService
    private val old = Instant.parse("2025-01-01T00:00:00Z").toEpochMilli()
    private val now = Instant.parse("2026-09-23T00:00:00Z").toEpochMilli()

    @Before fun setup() = kotlinx.coroutines.runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        ServiceLoopDatabase.configureStage4Tracking(database.openHelper.writableDatabase)
        directory = File(context.filesDir, "b049-retention-${System.nanoTime()}").apply { mkdirs() }
        cleanup = ImageCleanupService(context, database)
        cleanup.savePreference(ImageRetention.NEVER)
        val dao = database.serviceLoopDao()
        dao.upsertBusinessProfile(BusinessProfileEntity("primary","Coordinator Business","Coordinator",null,null,null,"UTC",old))
        dao.insertCustomers(listOf(CustomerEntity("c","CU-1","Customer")))
        dao.insertSites(listOf(SiteEntity("s","c","ST-1","Site",null,null)))
        dao.insertVisits(listOf(
            WorkingVisitEntity("final-visit","V-1","c","s","2025-01-01","Customer","Site",null,"COMPLETED",old),
            WorkingVisitEntity("working-visit","V-2","c","s","2025-01-01","Customer","Site",null,"WORKING",old),
        ))
        dao.insertWorkItems(listOf(
            WorkItemEntity("final-work","final-visit",null,null,null,null,null,null,"Inspect",null,null,null,null,false,null,null,subjectType="SITE"),
            WorkItemEntity("working-work","working-visit",null,null,null,null,null,null,"Inspect",null,null,null,null,false,null,null,subjectType="SITE"),
        ))
        dao.insertFinalRecord(FinalRecordEntity("record","final-visit","revision",old))
        dao.insertFinalRevision(FinalRecordRevisionEntity("revision","record",1,"V-1","2025-01-01",old,"Customer","Site",null,"Business","Technician",null,null,null,"UTC",null))
        dao.insertFinalWorkItems(listOf(FinalWorkItemEntity("final-item","revision",1,"final-work",null,null,null,null,null,null,null,"Inspect",null,null,"DONE",null,null,false,null,null,null,null,null,null,null)))
        val image = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.JPEG, 90, it); image.recycle() }.toByteArray()
        val finalFile = File(directory, "final.jpg").apply { writeBytes(bytes); setLastModified(old) }
        val excludedFile = File(directory, "excluded.jpg").apply { writeBytes(bytes); setLastModified(old) }
        val workingFile = File(directory, "working.jpg").apply { writeBytes(bytes); setLastModified(old) }
        dao.insertAttachments(listOf(
            AttachmentEntity("final-photo","WORK_ITEM","final-work",relative(finalFile),WorkResultPackageCodec.sha256(bytes),null,"image/jpeg",true,"PRESENT",bytes.size.toLong(),"Final image"),
            AttachmentEntity("excluded-photo","WORK_ITEM","final-work",relative(excludedFile),WorkResultPackageCodec.sha256(bytes),null,"image/jpeg",false,"PRESENT",bytes.size.toLong(),"Operational evidence"),
            AttachmentEntity("working-photo","WORK_ITEM","working-work",relative(workingFile),WorkResultPackageCodec.sha256(bytes),null,"image/jpeg",false,"PRESENT",bytes.size.toLong(),"Working image"),
        ))
        dao.insertFinalPhotos(listOf(
            FinalPhotoEntryEntity("final-photo-entry","final-item",1,"final-photo",relative(finalFile),WorkResultPackageCodec.sha256(bytes),bytes.size.toLong(),"image/jpeg","Final image"),
            FinalPhotoEntryEntity("excluded-photo-entry","final-item",2,"excluded-photo",relative(excludedFile),WorkResultPackageCodec.sha256(bytes),bytes.size.toLong(),"image/jpeg","Operational evidence", includedInCustomerReport = false),
        ))
    }

    @After fun teardown() {
        cleanup.savePreference(ImageRetention.NEVER)
        database.close()
        directory.deleteRecursively()
    }

    @Test fun neverKeepsAllAndAgedFinalGetsVerifiedCopyBeforeRemoval() = runTest {
        val finalFile = File(directory, "final.jpg")
        val excludedFile = File(directory, "excluded.jpg")
        val workingFile = File(directory, "working.jpg")
        assertEquals(0, cleanup.runNow(now).originalsRemoved)
        assertTrue(finalFile.isFile)
        cleanup.savePreference(ImageRetention.ONE_MONTH)
        val result = cleanup.runNow(now)
        assertEquals(2, result.originalsRemoved)
        assertFalse(finalFile.exists())
        assertFalse(excludedFile.exists())
        assertTrue(workingFile.isFile)
        val retained = database.serviceLoopDao().retainedImage("ATTACHMENT", "final-photo")
        assertNotNull(retained)
        assertNotNull(database.serviceLoopDao().retainedImage("ATTACHMENT", "excluded-photo"))
        val derivative = File(context.filesDir, retained!!.derivativeRelativePath)
        assertTrue(derivative.isFile)
        assertEquals(retained.derivativeSha256, WorkResultPackageCodec.sha256(derivative.readBytes()))
        assertTrue(maxOf(retained.derivativeWidth, retained.derivativeHeight) <= 1200)
        val time = object : BusinessTime { override val zoneId = ZoneId.of("UTC"); override fun instant() = Instant.ofEpochMilli(now) }
        val public = RoomServiceLoopRepository(database, time, attachmentRoot = context.filesDir).finalRecordRevision("record", "revision")!!.public
        assertEquals(retained.derivativeRelativePath, public.lines.single().photos.single().relativePath)
        val archive = ExportCenterService(database, context.filesDir).export(ExportCenterSelection(ServiceLoopScopeFilter(customerId = "c"), ExportPreset.IMAGE_ARCHIVE.families))
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(archive.inputStream()).use { zip -> while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() } }
        assertTrue(entries.keys.any { it.startsWith("images/") })
        assertTrue(entries.getValue("photos.csv").toString(Charsets.UTF_8).contains("DERIVATIVE"))
        assertTrue(entries.getValue("photos.csv").toString(Charsets.UTF_8).contains("Operational evidence"))
        assertTrue(entries.getValue("photos.csv").toString(Charsets.UTF_8).contains("ORIGINAL"))
        derivative.delete()
    }

    @Test fun aggregateFreezesExactSourceAndCreatesNewPdfFromRetainedTruth() = runTest {
        cleanup.savePreference(ImageRetention.ONE_MONTH)
        cleanup.runNow(now)
        val time = object : BusinessTime { override val zoneId = ZoneId.of("UTC"); override fun instant() = Instant.ofEpochMilli(now) }
        val repository = RoomServiceLoopRepository(database, time, attachmentRoot = context.filesDir)
        val service = AggregateReportService(database, repository, context.filesDir, AggregateReportWriter { models, businessName, _, _, target, _ ->
            assertEquals("Coordinator Business", businessName)
            assertEquals("Technician", models.single().technicianName)
            assertTrue(models.single().lines.single().photos.single().relativePath.startsWith("retained-images/"))
            target.writeBytes("%PDF-fixture".toByteArray())
            1
        })
        val filter = ServiceLoopScopeFilter(customerId = "c")
        val sources = service.reportable(filter)
        assertEquals(listOf("VISIT:final-visit"), sources.map { it.key })
        val generated = service.generate(filter, sources.map { it.key })
        val file = File(context.filesDir, generated.relativePath)
        assertTrue(file.isFile && file.length() > 0)
        assertTrue(file.readBytes().copyOfRange(0, 4).contentEquals("%PDF".toByteArray()))
        assertEquals("revision", database.serviceLoopDao().aggregateSources(generated.reportId).single().sourceFinalRevisionId)
        val rendition = database.serviceLoopDao().aggregateRenditions(generated.reportId).single()
        assertEquals("READY", rendition.status)
        assertEquals(file.length(), rendition.byteSize)
        assertEquals(WorkResultPackageCodec.sha256(file.readBytes()), rendition.sha256)
        database.serviceLoopDao().insertFinalRevision(FinalRecordRevisionEntity("revision-2","record",2,"V-1","2025-01-01",now,"Customer","Site",null,"Business","Technician",null,null,null,"UTC",null,supersedesRevisionId="revision"))
        database.openHelper.writableDatabase.execSQL("UPDATE final_records SET currentRevisionId='revision-2' WHERE id='record'")
        assertEquals("revision-2", service.reportable(filter).single().revisionId)
        assertEquals("revision", database.serviceLoopDao().aggregateSources(generated.reportId).single().sourceFinalRevisionId)
        file.delete()
    }

    @Test fun aggregateAcceptsTwoVisitsForOneCustomerAndRejectsCrossCustomerSelection() = runTest {
        val dao = database.serviceLoopDao()
        suspend fun addFinalVisit(id: String, customerId: String, siteId: String) {
            dao.insertVisits(listOf(WorkingVisitEntity(id, id, customerId, siteId, "2025-01-02", "Customer", "Site", null, "COMPLETED", old)))
            dao.insertWorkItems(listOf(WorkItemEntity("work-$id", id, null, null, null, null, null, null, "Inspect", null, null, null, null, false, null, null, subjectType = "SITE")))
            dao.insertFinalRecord(FinalRecordEntity("record-$id", id, "revision-$id", old))
            dao.insertFinalRevision(FinalRecordRevisionEntity("revision-$id", "record-$id", 1, id, "2025-01-02", old, "Customer", "Site", null, "Business", "Technician", null, null, null, "UTC", null))
            dao.insertFinalWorkItems(listOf(FinalWorkItemEntity("final-$id", "revision-$id", 1, "work-$id", null, null, null, null, null, null, null, "Inspect", null, null, "DONE", "Inspected", null, false, null, null, null, null, null, null, subjectType = "SITE")))
        }
        addFinalVisit("second", "c", "s")
        dao.insertCustomers(listOf(CustomerEntity("other-c", "CU-2", "Other customer")))
        dao.insertSites(listOf(SiteEntity("other-s", "other-c", "ST-2", "Other site", null, null)))
        addFinalVisit("foreign", "other-c", "other-s")
        val time = object : BusinessTime { override val zoneId = ZoneId.of("UTC"); override fun instant() = Instant.ofEpochMilli(now) }
        val repository = RoomServiceLoopRepository(database, time, attachmentRoot = context.filesDir)
        val service = AggregateReportService(database, repository, context.filesDir, AggregateReportWriter { models, _, _, _, target, _ ->
            assertEquals(2, models.size)
            target.writeBytes("%PDF-fixture".toByteArray())
            1
        })
        val scope = ServiceLoopScopeFilter(customerId = "c")
        val keys = service.reportable(scope).map { it.key }
        assertEquals(2, keys.size)
        val report = service.generate(scope, keys)
        assertEquals(2, dao.aggregateSources(report.reportId).size)
        val foreign = service.reportable(ServiceLoopScopeFilter(customerId = "other-c")).single().key
        assertTrue(runCatching { service.generate(scope, keys + foreign) }.isFailure)
        File(context.filesDir, report.relativePath).delete()
    }

    private fun relative(file: File) = context.filesDir.toPath().relativize(file.toPath()).toString().replace('\\','/')
}
