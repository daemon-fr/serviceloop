package com.v16studio.serviceloop.data

import androidx.room.withTransaction
import java.time.Instant
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class InspectionTemplateTransfer(
    val generatedAt: String,
    val templates: List<InspectionTemplateTransferEntry>,
)

data class InspectionTemplateTransferEntry(
    val reference: String,
    val name: String,
    val revision: Int,
    val items: List<DispatchInspectionItem>,
    val fingerprint: String = "",
)

enum class InspectionTemplateImportClassification { NEW, EXACT_EXISTING, CONFLICT }

data class InspectionTemplateImportEntry(
    val transfer: InspectionTemplateTransferEntry,
    val classification: InspectionTemplateImportClassification,
)

data class InspectionTemplateImportPreview(val value: InspectionTemplateTransfer, val entries: List<InspectionTemplateImportEntry>) {
    val canImport: Boolean get() = entries.isNotEmpty() && entries.all { it.classification != InspectionTemplateImportClassification.CONFLICT }
    fun canImport(createSeparate: Set<String>): Boolean = entries.isNotEmpty() && entries.all { entry ->
        entry.classification != InspectionTemplateImportClassification.CONFLICT || entry.transfer.reference in createSeparate
    }
}

data class InspectionTemplateImportResult(
    val importedReferences: List<String>,
    val exactReferences: List<String>,
    val createdSeparateReferences: List<String>,
)

object InspectionTemplateCodec {
    const val FORMAT = "ServiceLoopInspectionTemplates"
    const val CURRENT_VERSION = 1
    const val MAX_BYTES = 1_048_576
    private const val MAX_TEMPLATES = 100
    private const val MAX_ITEMS = 500

    fun encode(value: InspectionTemplateTransfer): ByteArray {
        require(value.templates.isNotEmpty() && value.templates.size <= MAX_TEMPLATES) { "Select at least one inspection template" }
        Instant.parse(value.generatedAt)
        val normalized = value.templates.map { it.normalized() }
        require(normalized.map { it.reference }.distinct().size == normalized.size) { "Duplicate inspection template reference" }
        val root = JSONObject().put("format", FORMAT).put("formatVersion", CURRENT_VERSION).put("generatedAt", value.generatedAt).put("generatedWith", "ServiceLoop")
        root.put("templates", JSONArray(normalized.map { template ->
            JSONObject().put("reference", template.reference).put("name", template.name).put("revision", template.revision).put("fingerprint", fingerprint(template)).put("items", JSONArray(template.items.map { item ->
                JSONObject().put("position", item.position).put("label", item.label).put("responseType", item.responseType).put("unit", item.unit ?: JSONObject.NULL).put("required", item.required).put("privateGuidance", item.privateGuidance ?: JSONObject.NULL)
            }))
        }))
        return root.toString(2).toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) { "Inspection template file exceeds 1 MiB" } }
    }

    fun decode(bytes: ByteArray): InspectionTemplateTransfer {
        require(bytes.size in 1..MAX_BYTES) { "Invalid inspection template file size" }
        val root = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrElse { throw IllegalArgumentException("Malformed inspection template file") }
        require(root.optString("format") == FORMAT && root.optInt("formatVersion", -1) == CURRENT_VERSION) { "Unsupported inspection template file" }
        val generatedAt = root.getString("generatedAt").also { Instant.parse(it) }
        val array = root.getJSONArray("templates")
        require(array.length() in 1..MAX_TEMPLATES) { "Invalid inspection template count" }
        fun JSONObject.text(name: String, max: Int = 4_000) = getString(name).trim().also { require(it.isNotEmpty() && it.length <= max) { "Invalid $name" } }
        fun JSONObject.nullable(name: String, max: Int = 4_000) = if (isNull(name)) null else getString(name).trim().also { require(it.length <= max) { "$name is too long" } }.takeIf { it.isNotEmpty() }
        val templates = (0 until array.length()).map { index ->
            val template = array.getJSONObject(index)
            val items = template.getJSONArray("items")
            require(items.length() in 1..MAX_ITEMS) { "Invalid inspection item count" }
            val parsedItems = (0 until items.length()).map { itemIndex ->
                val item = items.getJSONObject(itemIndex)
                DispatchInspectionItem(item.getInt("position"), item.text("label"), item.text("responseType", 20), item.nullable("unit", 100), item.getBoolean("required"), item.nullable("privateGuidance", 2_000))
            }
            val entry = InspectionTemplateTransferEntry(template.text("reference", 200), template.text("name", 200), template.getInt("revision"), parsedItems)
            require(entry.revision > 0 && entry.items.map { it.position } == (1..entry.items.size).toList()) { "Inspection items must be ordered" }
            require(entry.items.all { it.responseType in setOf("STATUS", "TEXT", "NUMBER") }) { "Unsupported inspection response type" }
            val declared = template.optString("fingerprint").takeIf { it.isNotBlank() }
            require(declared == null || declared == fingerprint(entry)) { "Inspection template fingerprint does not match its content" }
            entry.copy(fingerprint = fingerprint(entry))
        }
        require(templates.map { it.reference }.distinct().size == templates.size) { "Duplicate inspection template reference" }
        return InspectionTemplateTransfer(generatedAt, templates)
    }

    fun fingerprint(value: InspectionTemplateTransferEntry): String = sha256(value.normalized().let { template ->
        buildString {
            append(template.reference).append('|').append(template.name).append('|').append(template.revision)
            template.items.forEach { item -> append('|').append(item.position).append(':').append(item.label).append(':').append(item.responseType).append(':').append(item.unit.orEmpty()).append(':').append(item.required).append(':').append(item.privateGuidance.orEmpty()) }
        }
    })

    fun contentFingerprint(value: InspectionTemplateTransferEntry): String = fingerprint(value.copy(reference = "_content_"))

    private fun InspectionTemplateTransferEntry.normalized() = copy(
        reference = reference.trim(),
        name = name.trim(),
        items = items.map { it.copy(label = it.label.trim(), unit = it.unit?.trim()?.takeIf(String::isNotEmpty), privateGuidance = it.privateGuidance?.trim()?.takeIf(String::isNotEmpty)) },
    ).also { template ->
        require(template.reference.isNotEmpty() && template.name.isNotEmpty() && template.revision > 0 && template.items.isNotEmpty() && template.items.size <= MAX_ITEMS)
        require(template.items.map { it.position } == (1..template.items.size).toList())
        require(template.items.all { it.label.isNotEmpty() && it.responseType in setOf("STATUS", "TEXT", "NUMBER") })
    }
}

class InspectionTemplateExchangeService(private val database: ServiceLoopDatabase) {
    private val dao = database.serviceLoopDao()

    suspend fun export(templateIds: Set<String>): InspectionTemplateTransfer {
        require(templateIds.isNotEmpty()) { "Select at least one inspection template" }
        val templates = templateIds.toList().sorted().map { id ->
            val template = dao.reusableTemplate(id) ?: error("Inspection template no longer exists")
            val revision = dao.reusableTemplateRevision(template.currentRevisionId) ?: error("Inspection template revision is missing")
            InspectionTemplateTransferEntry(template.reference, revision.nameSnapshot, revision.revisionNumber, dao.reusableTemplateItems(revision.id).map { item -> DispatchInspectionItem(item.position, item.label, item.responseType, item.unit, item.required, item.privateGuidance) })
        }
        return InspectionTemplateTransfer(Instant.now().toString(), templates)
    }

    suspend fun preview(bytes: ByteArray): InspectionTemplateImportPreview {
        val transfer = InspectionTemplateCodec.decode(bytes)
        val existing = dao.reusableTemplates().associateBy { it.reference }
        val existingFingerprints = existing.values.mapNotNull { template ->
            runCatching { currentFingerprint(template) }.getOrNull()
        }.toSet()
        val existingContentFingerprints = existing.values.mapNotNull { template ->
            runCatching { currentContentFingerprint(template) }.getOrNull()?.let { fingerprint -> template.reference to fingerprint }
        }.toMap()
        val entries = transfer.templates.map { incoming ->
            val current = existing[incoming.reference]
            val classification = when {
                incoming.fingerprint in existingFingerprints -> InspectionTemplateImportClassification.EXACT_EXISTING
                existingContentFingerprints.any { (reference, fingerprint) -> reference.startsWith("${incoming.reference}-imported-") && fingerprint == InspectionTemplateCodec.contentFingerprint(incoming) } -> InspectionTemplateImportClassification.EXACT_EXISTING
                current == null -> InspectionTemplateImportClassification.NEW
                currentFingerprint(current) == incoming.fingerprint -> InspectionTemplateImportClassification.EXACT_EXISTING
                else -> InspectionTemplateImportClassification.CONFLICT
            }
            InspectionTemplateImportEntry(incoming, classification)
        }
        return InspectionTemplateImportPreview(transfer, entries)
    }

    suspend fun import(preview: InspectionTemplateImportPreview, createSeparate: Set<String> = emptySet()): InspectionTemplateImportResult = database.withTransaction {
        require(preview.canImport(createSeparate)) { "Resolve inspection template conflicts before importing" }
        val existing = dao.reusableTemplates().associateBy { it.reference }.toMutableMap()
        val imported = mutableListOf<String>(); val exact = mutableListOf<String>(); val separate = mutableListOf<String>()
        preview.entries.forEach { entry ->
            val incoming = entry.transfer
            if (entry.classification == InspectionTemplateImportClassification.EXACT_EXISTING) { exact += incoming.reference; return@forEach }
            val targetReference = if (entry.classification == InspectionTemplateImportClassification.CONFLICT) {
                require(incoming.reference in createSeparate) { "Choose Create separate for ${incoming.reference}" }
                uniqueReference(incoming.reference, incoming.fingerprint, existing.keys)
            } else incoming.reference
            val templateId = UUID.randomUUID().toString(); val revisionId = UUID.randomUUID().toString(); val now = System.currentTimeMillis()
            dao.insertReusableTemplate(ReusableTemplateEntity(templateId, targetReference, incoming.name, revisionId, modifiedAtEpochMillis = now))
            dao.insertReusableTemplateRevision(ReusableTemplateRevisionEntity(revisionId, templateId, incoming.revision, incoming.name, now))
            dao.insertReusableTemplateItems(incoming.items.map { item -> ReusableTemplateItemEntity(UUID.randomUUID().toString(), revisionId, item.position, item.label, item.responseType, item.unit, item.required, item.privateGuidance) })
            existing[targetReference] = ReusableTemplateEntity(templateId, targetReference, incoming.name, revisionId, modifiedAtEpochMillis = now)
            imported += targetReference
            if (entry.classification == InspectionTemplateImportClassification.CONFLICT) separate += targetReference
        }
        InspectionTemplateImportResult(imported, exact, separate)
    }

    private suspend fun currentFingerprint(template: ReusableTemplateEntity): String {
        val revision = dao.reusableTemplateRevision(template.currentRevisionId) ?: return "missing"
        return InspectionTemplateCodec.fingerprint(InspectionTemplateTransferEntry(template.reference, revision.nameSnapshot, revision.revisionNumber, dao.reusableTemplateItems(revision.id).map { item -> DispatchInspectionItem(item.position, item.label, item.responseType, item.unit, item.required, item.privateGuidance) }))
    }

    private suspend fun currentContentFingerprint(template: ReusableTemplateEntity): String {
        val revision = dao.reusableTemplateRevision(template.currentRevisionId) ?: return "missing"
        return InspectionTemplateCodec.contentFingerprint(InspectionTemplateTransferEntry(template.reference, revision.nameSnapshot, revision.revisionNumber, dao.reusableTemplateItems(revision.id).map { item -> DispatchInspectionItem(item.position, item.label, item.responseType, item.unit, item.required, item.privateGuidance) }))
    }

    private fun uniqueReference(reference: String, fingerprint: String, occupied: Set<String>): String {
        val base = "$reference-imported-${fingerprint.take(8)}"
        var candidate = base; var suffix = 2
        while (candidate in occupied) { candidate = "$base-$suffix"; suffix++ }
        return candidate
    }
}
