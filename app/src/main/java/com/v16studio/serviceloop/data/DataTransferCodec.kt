package com.v16studio.serviceloop.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Versioned selective ServiceLoop-to-ServiceLoop payload inside the shared envelope. */
enum class DataTransferFamily(val section: String) {
    REGISTER("register"), SERVICE_PLANS("plans"), INSPECTION_TEMPLATES("inspections"),
    PERFORMED_WORK("performed"), FOLLOW_UPS("followups"), CONTACT_NOTES("contactnotes"),
    CHANGE_HISTORY("history"), EVIDENCE("evidence"),
}

data class DataTransferPayload(
    val packageId: String,
    val exporterId: String,
    val sourceWorkspaceId: String,
    val options: DataTransferOptions,
    val families: Map<DataTransferFamily, ByteArray>,
    val binaries: Map<String, ByteArray>,
)

data class DataTransferOptions(
    val includePrivate: Boolean = false,
    val includeInactive: Boolean = false,
    val includePreviousRevisions: Boolean = false,
)

object DataTransferCodec {
    const val VERSION = 2
    private const val FAMILY_VERSION = 1

    fun encode(
        exporterId: String,
        generatedAt: String = Instant.now().toString(),
        families: Map<DataTransferFamily, ByteArray>,
        binaries: Map<String, ByteArray> = emptyMap(),
        sourceWorkspaceId: String,
        options: DataTransferOptions = DataTransferOptions(),
    ): ByteArray {
        require(families.isNotEmpty()) { "Choose data to share" }
        require(TechnicianIdCodec.normalize(exporterId) != null) { "A valid exporter ID is required" }
        require(TechnicianIdCodec.normalize(sourceWorkspaceId) != null) { "A valid source workspace ID is required" }
        require(binaries.isEmpty() || DataTransferFamily.EVIDENCE in families) { "Evidence bytes need an evidence family" }
        require(binaries.keys.all { validBinaryName(it) }) { "Unsafe evidence path" }
        require(binaries.values.all { it.isNotEmpty() }) { "Evidence file is empty" }
        require(1 + 1 + families.size + binaries.size <= ServiceLoopSyncEnvelopeCodec.MAX_ENTRIES) { "Too many transfer entries" }
        val familyMetadata = JSONArray().also { array ->
            families.keys.sortedBy { it.ordinal }.forEach { family ->
                array.put(JSONObject().put("name", family.name).put("version", FAMILY_VERSION).put("section", family.section))
            }
        }
        val binaryMetadata = JSONArray().also { array ->
            binaries.toSortedMap().forEach { (name, bytes) ->
                array.put(JSONObject().put("name", name).put("size", bytes.size).put("sha256", WorkResultPackageCodec.sha256(bytes)))
            }
        }
        val metadata = JSONObject().put("version", VERSION).put("families", familyMetadata)
            .put("binaries", binaryMetadata).put("sourceWorkspaceId", TechnicianIdCodec.normalize(sourceWorkspaceId))
            .put("options", JSONObject().put("includePrivate", options.includePrivate)
                .put("includeInactive", options.includeInactive).put("includePreviousRevisions", options.includePreviousRevisions))
        val sections = linkedMapOf("transfer" to metadata.toString().toByteArray(Charsets.UTF_8))
        val declarations = mutableListOf(ServiceLoopSyncSectionDeclaration("transfer", VERSION, "transfer.json"))
        families.keys.sortedBy { it.ordinal }.forEach { family ->
            sections[family.section] = families.getValue(family)
            declarations += ServiceLoopSyncSectionDeclaration(family.section, FAMILY_VERSION, "${family.section}.json")
        }
        binaries.toSortedMap().forEach { (name, bytes) ->
            sections[name] = bytes
            declarations += ServiceLoopSyncSectionDeclaration(name, 1, "$name.bin")
        }
        return ServiceLoopSyncEnvelopeCodec.encode(
            ServiceLoopSyncManifest(UUID.randomUUID().toString(), "Shared ServiceLoop data", "DATA_TRANSFER", generatedAt,
                sections = declarations, exporterId = exporterId), sections)
    }

    fun decode(bytes: ByteArray): DataTransferPayload {
        val envelope = ServiceLoopSyncEnvelopeCodec.decode(bytes)
        require(envelope.manifest.purpose == "DATA_TRANSFER") { "This is not a data transfer" }
        val transferDeclaration = ServiceLoopSyncSectionDeclaration("transfer", VERSION, "transfer.json")
        require(envelope.manifest.sections.firstOrNull() == transferDeclaration) { "Unsupported transfer metadata" }
        val metadata = JSONObject(envelope.section("transfer").toString(Charsets.UTF_8))
        require(metadata.getInt("version") == VERSION) { "Unsupported data-transfer version" }
        val sourceWorkspaceId = TechnicianIdCodec.normalize(metadata.getString("sourceWorkspaceId"))
            ?: throw IllegalArgumentException("A valid source workspace ID is required")
        val optionJson = metadata.getJSONObject("options")
        val options = DataTransferOptions(
            optionJson.getBoolean("includePrivate"),
            optionJson.getBoolean("includeInactive"),
            optionJson.getBoolean("includePreviousRevisions"),
        )
        val listed = metadata.getJSONArray("families")
        require(listed.length() in 1..DataTransferFamily.entries.size) { "Invalid transfer families" }
        val families = linkedMapOf<DataTransferFamily, ByteArray>()
        val expected = mutableListOf(transferDeclaration)
        repeat(listed.length()) { index ->
            val entry = listed.getJSONObject(index)
            val family = DataTransferFamily.entries.firstOrNull { it.name == entry.getString("name") }
                ?: error("Unsupported transfer family")
            require(entry.getInt("version") == FAMILY_VERSION && entry.getString("section") == family.section) { "Unsupported transfer family version" }
            require(family !in families) { "Duplicate transfer family" }
            val declaration = ServiceLoopSyncSectionDeclaration(family.section, FAMILY_VERSION, "${family.section}.json")
            expected += declaration
            families[family] = envelope.section(family.section)
        }
        val binaries = linkedMapOf<String, ByteArray>()
        val listedBinaries = metadata.optJSONArray("binaries") ?: JSONArray()
        require(listedBinaries.length() <= 128) { "Too many evidence files" }
        repeat(listedBinaries.length()) { index ->
            val entry = listedBinaries.getJSONObject(index)
            val name = entry.getString("name")
            require(validBinaryName(name) && name !in binaries && DataTransferFamily.EVIDENCE in families) { "Invalid evidence declaration" }
            val data = envelope.section(name)
            require(data.size.toLong() == entry.getLong("size") && WorkResultPackageCodec.sha256(data) == entry.getString("sha256")) { "Evidence integrity check failed" }
            expected += ServiceLoopSyncSectionDeclaration(name, 1, "$name.bin")
            binaries[name] = data
        }
        require(envelope.manifest.sections.toSet() == expected.toSet() && envelope.manifest.sections.size == expected.size) { "Transfer sections do not match family declarations" }
        require(expected.size + 1 <= ServiceLoopSyncEnvelopeCodec.MAX_ENTRIES) { "Too many transfer entries" }
        require(envelope.manifest.sections == expected) { "Transfer sections are not in canonical order" }
        return DataTransferPayload(envelope.manifest.syncId, requireNotNull(envelope.manifest.exporterId), sourceWorkspaceId, options, families, binaries)
    }

    private fun validBinaryName(name: String) = name.matches(Regex("binary-[a-zA-Z0-9_-]{1,80}"))
}
