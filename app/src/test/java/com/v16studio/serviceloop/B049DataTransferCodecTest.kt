package com.v16studio.serviceloop

import com.v16studio.serviceloop.data.*
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class B049DataTransferCodecTest {
    private val exporter = TechnicianIdCodec.generate()

    @Test fun v2DeclaresFamiliesAndChecksEvidenceIntegrity() {
        val register = "{\"customers\":[]}".toByteArray()
        val evidence = "{\"photos\":[]}".toByteArray()
        val image = byteArrayOf(1, 2, 3)
        val bytes = DataTransferCodec.encode(exporter, families = mapOf(DataTransferFamily.REGISTER to register, DataTransferFamily.EVIDENCE to evidence), binaries = mapOf("binary-photo" to image))
        val decoded = DataTransferCodec.decode(bytes)
        assertEquals(setOf(DataTransferFamily.REGISTER, DataTransferFamily.EVIDENCE), decoded.families.keys)
        assertArrayEquals(register, decoded.families.getValue(DataTransferFamily.REGISTER))
        assertArrayEquals(image, decoded.binaries.getValue("binary-photo"))
        val entries = unzip(bytes)
        val metadata = JSONObject(entries.getValue("transfer.json").toString(Charsets.UTF_8))
        assertEquals(2, metadata.getInt("version"))
        metadata.getJSONArray("binaries").getJSONObject(0).put("sha256", "bad")
        entries["transfer.json"] = metadata.toString().toByteArray()
        assertThrows(IllegalArgumentException::class.java) { DataTransferCodec.decode(zip(entries)) }
    }

    @Test fun rejectsDuplicateAndUnsupportedFamilyDeclarations() {
        val bytes = DataTransferCodec.encode(exporter, families = mapOf(DataTransferFamily.REGISTER to "{}".toByteArray()))
        val entries = unzip(bytes)
        val metadata = JSONObject(entries.getValue("transfer.json").toString(Charsets.UTF_8))
        val families = metadata.getJSONArray("families")
        families.put(JSONObject(families.getJSONObject(0).toString()))
        entries["transfer.json"] = metadata.toString().toByteArray()
        assertThrows(IllegalArgumentException::class.java) { DataTransferCodec.decode(zip(entries)) }
        families.remove(1)
        families.getJSONObject(0).put("version", 99)
        entries["transfer.json"] = metadata.toString().toByteArray()
        assertThrows(IllegalArgumentException::class.java) { DataTransferCodec.decode(zip(entries)) }
    }

    @Test fun rejectsUndeclaredAndMissingSectionsAndUnsafeBinaryName() {
        val bytes = DataTransferCodec.encode(exporter, families = mapOf(DataTransferFamily.REGISTER to "{}".toByteArray()))
        val original = ServiceLoopSyncEnvelopeCodec.decode(bytes)
        val added = original.manifest.copy(sections = original.manifest.sections + ServiceLoopSyncSectionDeclaration("extra", 1, "extra.json"))
        val withExtra = ServiceLoopSyncEnvelopeCodec.encode(added, original.sections + ("extra" to "{}".toByteArray()))
        assertThrows(IllegalArgumentException::class.java) { DataTransferCodec.decode(withExtra) }
        val missing = original.manifest.copy(sections = original.manifest.sections.filterNot { it.name == "register" })
        val withoutRegister = ServiceLoopSyncEnvelopeCodec.encode(missing, original.sections - "register")
        assertThrows(IllegalStateException::class.java) { DataTransferCodec.decode(withoutRegister) }
        assertThrows(IllegalArgumentException::class.java) {
            DataTransferCodec.encode(exporter, families = mapOf(DataTransferFamily.EVIDENCE to "{}".toByteArray()), binaries = mapOf("../unsafe" to byteArrayOf(1)))
        }
    }

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> = linkedMapOf<String, ByteArray>().also { entries ->
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() } }
    }
    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
    }.toByteArray()
}
