package com.v16studio.serviceloop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.v16studio.serviceloop.data.AppOwnedImageNormalizer
import com.v16studio.serviceloop.data.ServiceLoopSyncEnvelopeCodec
import com.v16studio.serviceloop.data.TechnicianIdCodec
import com.v16studio.serviceloop.data.WorkResultPackageCodec
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class B049WorkResultCodecTest {
    private val issuer = TechnicianIdCodec.generate()
    private val exporter = TechnicianIdCodec.generate()

    private fun result(): JSONObject = JSONObject()
        .put("resultId", "result-1").put("sourceFinalRevisionId", "revision-1")
        .put("dispatchVisitId", "visit-1").put("dispatchItemId", "item-1")
        .put("assignmentIssuerId", issuer).put("assignmentGeneration", 1)
        .put("assignmentMaterialHash", "a".repeat(64))
        .put("technicianId", exporter).put("technicianName", "Field technician")
        .put("serviceDate", "2026-09-23").put("outcome", "DONE")
        .put("customerSnapshot", JSONObject().put("name", "Customer"))
        .put("siteSnapshot", JSONObject().put("name", "Site"))
        .put("subjectSnapshot", JSONObject().put("equipmentName", "Machine"))
        .put("workSnapshot", JSONObject().put("serviceName", "Service"))
        .put("checklist", JSONArray()).put("findings", JSONArray()).put("parts", JSONArray()).put("followUps", JSONArray())
        .put("recurrence", JSONObject())

    private fun photo(): ByteArray {
        val image = Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.JPEG, 75, it); image.recycle() }.toByteArray()
    }

    private fun encoded(): ByteArray = WorkResultPackageCodec.encode(WorkResultPackageCodec.Package(
        "package-1", exporter, issuer, "2026-09-23T10:00:00Z",
        listOf(WorkResultPackageCodec.Result(result(), listOf(WorkResultPackageCodec.Photo("photo-1", photo(), "A public photo", true, "PUBLIC", "item-1", 16, 12)))),
    ))

    @Test fun roundTripPreservesStructuredResultAndPhotoIntegrity() {
        val bytes = encoded()
        val decoded = WorkResultPackageCodec.decode(bytes)
        assertEquals("WORK_RESULT", ServiceLoopSyncEnvelopeCodec.decode(bytes).manifest.purpose)
        assertEquals(issuer, decoded.targetIssuerId)
        assertEquals("result-1", decoded.results.single().value.getString("resultId"))
        assertEquals("A public photo", decoded.results.single().photos.single().caption)
        assertTrue(decoded.results.single().photos.single().bytes.contentEquals(WorkResultPackageCodec.decode(bytes).results.single().photos.single().bytes))
    }

    @Test fun rejectsWrongIssuerAndTamperedPhoto() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkResultPackageCodec.encode(WorkResultPackageCodec.Package("p", exporter, TechnicianIdCodec.generate(), "2026-09-23T10:00:00Z", listOf(WorkResultPackageCodec.Result(result(), emptyList()))))
        }
        val tampered = ByteArrayOutputStream()
        ZipInputStream(encoded().inputStream()).use { input -> ZipOutputStream(tampered).use { output ->
            while (true) {
                val entry = input.nextEntry ?: break
                val data = input.readBytes()
                output.putNextEntry(ZipEntry(entry.name))
                output.write(if (entry.name.endsWith(".jpg")) data + 1.toByte() else data)
                output.closeEntry()
            }
        } }
        assertThrows(IllegalArgumentException::class.java) { WorkResultPackageCodec.decode(tampered.toByteArray()) }
    }

    @Test fun photoDerivativeIsJpegWithinReportQualityBoundsAndSourceRemainsUnchanged() {
        val bitmap = Bitmap.createBitmap(2400, 1600, Bitmap.Config.ARGB_8888)
        val source = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it); bitmap.recycle() }.toByteArray()
        val originalHash = WorkResultPackageCodec.sha256(source)
        val derivative = AppOwnedImageNormalizer.workResultDerivative(source)
        assertEquals(1200, derivative.width)
        assertEquals(800, derivative.height)
        assertEquals(0xFF.toByte(), derivative.bytes[0])
        assertEquals(0xD8.toByte(), derivative.bytes[1])
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(derivative.bytes, 0, derivative.bytes.size, bounds)
        assertEquals(1200, bounds.outWidth)
        assertEquals(800, bounds.outHeight)
        assertEquals(originalHash, WorkResultPackageCodec.sha256(source))
    }

    @Test fun measuredTwelvePhotoPackageFitsPurposeSpecificBudget() {
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(49)
        val pixels = IntArray(1200 * 800) { 0xff000000.toInt() or random.nextInt(0x1000000) }
        bitmap.setPixels(pixels, 0, 1200, 0, 0, 1200, 800)
        val source = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it); bitmap.recycle() }.toByteArray()
        val derivative = AppOwnedImageNormalizer.workResultDerivative(source)
        val photos = (1..12).map { index -> WorkResultPackageCodec.Photo("source-photo-$index", derivative.bytes, null, true, "PUBLIC", "item-1", derivative.width, derivative.height) }
        val bytes = WorkResultPackageCodec.encode(WorkResultPackageCodec.Package("measured-package", exporter, issuer, "2026-09-23T10:00:00Z", listOf(WorkResultPackageCodec.Result(result(), photos))))
        assertEquals(12, WorkResultPackageCodec.decode(bytes).results.single().photos.size)
        assertTrue(bytes.size <= ServiceLoopSyncEnvelopeCodec.MAX_PACKAGE_BYTES)
        println("B049_WORK_RESULT_FIXTURE source=${source.size} derivative=${derivative.bytes.size} compressedPackage=${bytes.size} photos=12 dimensions=${derivative.width}x${derivative.height}")
    }
}
