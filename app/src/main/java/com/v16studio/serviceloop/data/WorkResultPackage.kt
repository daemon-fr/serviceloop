package com.v16studio.serviceloop.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Transport-neutral WORK_RESULT contract. Every photo is a declared envelope entry. */
internal object WorkResultPackageCodec {
    const val VERSION = 2
    const val MAX_PHOTO_BYTES = 10 * 1024 * 1024
    const val MAX_RESULTS = 100
    const val MAX_PHOTOS = 120
    private fun resultSection(version: Int) = ServiceLoopSyncSectionDeclaration("results", version, "results.json")

    data class Photo(val sourcePhotoId: String, val bytes: ByteArray, val caption: String?, val includeInReport: Boolean, val visibility: String, val workItemId: String, val width: Int, val height: Int) {
        init {
            require(sourcePhotoId.isNotBlank() && workItemId.isNotBlank())
            require(bytes.size in 1..MAX_PHOTO_BYTES && width in 1..1200 && height in 1..1200)
            require(visibility in setOf("PUBLIC", "PRIVATE", "INTERNAL"))
        }
    }

    data class Result(val value: JSONObject, val photos: List<Photo>)
    data class Package(val packageId: String, val exporterId: String, val targetIssuerId: String, val generatedAt: String, val results: List<Result>)

    fun encode(value: Package): ByteArray {
        require(value.results.isNotEmpty() && value.results.size <= MAX_RESULTS)
        require(value.results.sumOf { it.photos.size } <= MAX_PHOTOS)
        require(value.targetIssuerId.isNotBlank())
        Instant.parse(value.generatedAt)
        val sections = linkedMapOf<String, ByteArray>()
        val hasV2Source = value.results.map { it.value.has("originWorkspaceId") }
        require(hasV2Source.all { it } || hasV2Source.none { it }) { "Mixed work-result versions" }
        val version = if (hasV2Source.all { it }) VERSION else 1
        val declarations = mutableListOf(resultSection(version))
        val results = JSONArray()
        val photoIds = mutableSetOf<Triple<String, String, String>>()
        value.results.forEach { result ->
            validateResultSafe(result.value, value.targetIssuerId, value.exporterId, version)
            if (version == VERSION) validateSourcePhotos(result.value, result.photos)
            val json = JSONObject(result.value.toString())
            val photos = JSONArray()
            result.photos.forEach { photo ->
                require(photoIds.add(Triple(json.getString("resultId"), json.getString("sourceFinalRevisionId"), photo.sourcePhotoId))) { "Duplicate source photo in result revision" }
                val sectionName = "photo-${UUID.randomUUID()}"
                val fileName = "$sectionName.jpg"
                declarations += ServiceLoopSyncSectionDeclaration(sectionName, 1, fileName)
                sections[sectionName] = photo.bytes
                photos.put(JSONObject().put("sourcePhotoId", photo.sourcePhotoId).put("workItemId", photo.workItemId)
                    .put("section", sectionName).put("sha256", sha256(photo.bytes)).put("byteSize", photo.bytes.size)
                    .put("mimeType", "image/jpeg").put("width", photo.width).put("height", photo.height)
                    .put("caption", photo.caption).put("includeInReport", photo.includeInReport).put("visibility", photo.visibility))
            }
            json.put("photos", photos)
            results.put(json)
        }
        sections["results"] = JSONObject().put("version", version).put("targetIssuerId", value.targetIssuerId)
            .put("results", results).toString().toByteArray(Charsets.UTF_8)
        return ServiceLoopSyncEnvelopeCodec.encode(
            ServiceLoopSyncManifest(value.packageId, "ServiceLoop work results", "WORK_RESULT", value.generatedAt, sections = declarations, exporterId = value.exporterId), sections,
        )
    }

    fun decode(bytes: ByteArray): Package {
        val envelope = ServiceLoopSyncEnvelopeCodec.decode(bytes)
        val manifest = envelope.manifest
        require(manifest.formatVersion == 2 && manifest.purpose == "WORK_RESULT") { "Choose a work-result ServiceLoop file" }
        require(manifest.sections.firstOrNull()?.let { it == resultSection(1) || it == resultSection(VERSION) } == true) { "Unsupported work-result payload" }
        val root = JSONObject(envelope.section("results").toString(Charsets.UTF_8))
        val version = root.getInt("version")
        require(version in 1..VERSION && manifest.sections.first() == resultSection(version)) { "Unsupported work-result version" }
        val issuerId = root.getString("targetIssuerId")
        require(TechnicianIdCodec.normalize(issuerId) == issuerId) { "Invalid assignment issuer" }
        val resultArray = root.getJSONArray("results")
        require(resultArray.length() in 1..MAX_RESULTS) { "Invalid result count" }
        val usedSections = mutableSetOf("results")
        val resultKeys = mutableSetOf<Pair<String, String>>()
        val photoIds = mutableSetOf<Triple<String, String, String>>()
        val results = (0 until resultArray.length()).map { index ->
            val json = resultArray.getJSONObject(index)
            validateResultSafe(json, issuerId, manifest.exporterId ?: error("Missing exporter identity"), version)
            require(resultKeys.add(json.getString("resultId") to json.getString("sourceFinalRevisionId"))) { "Duplicate result revision" }
            val photoArray = json.getJSONArray("photos")
            require(photoArray.length() <= MAX_PHOTOS) { "Too many result photos" }
            val photos = (0 until photoArray.length()).map { photoIndex ->
                val photo = photoArray.getJSONObject(photoIndex)
                val sectionName = photo.getString("section")
                require(usedSections.add(sectionName) && manifest.sections.any { it == ServiceLoopSyncSectionDeclaration(sectionName, 1, "$sectionName.jpg") }) { "Undeclared or duplicate photo" }
                val photoId = photo.getString("sourcePhotoId")
                require(photoIds.add(Triple(json.getString("resultId"), json.getString("sourceFinalRevisionId"), photoId))) { "Duplicate source photo in result revision" }
                val data = envelope.section(sectionName)
                require(data.size in 1..MAX_PHOTO_BYTES && photo.getLong("byteSize") == data.size.toLong() && photo.getString("sha256") == sha256(data)) { "Result photo failed integrity check" }
                require(photo.getString("mimeType") == "image/jpeg") { "Unsupported result photo format" }
                Photo(photoId, data, photo.optString("caption").takeIf { it.isNotBlank() }, photo.getBoolean("includeInReport"), photo.getString("visibility"), photo.getString("workItemId"), photo.getInt("width"), photo.getInt("height"))
            }
            if (version == VERSION) validateSourcePhotos(json, photos)
            Result(json, photos)
        }
        require(results.sumOf { it.photos.size } <= MAX_PHOTOS && usedSections == manifest.sections.map { it.name }.toSet()) { "Work-result binary declarations do not match" }
        return Package(manifest.syncId, requireNotNull(manifest.exporterId), issuerId, manifest.generatedAt, results)
    }

    private fun validateResultSafe(o: JSONObject, targetIssuerId: String, exporterId: String, version: Int) {
        try {
            validateResult(o, targetIssuerId, exporterId, version)
        } catch (failure: JSONException) {
            throw IllegalArgumentException("Malformed work-result semantics: ${failure.message}", failure)
        }
    }

    private fun validateResult(o: JSONObject, targetIssuerId: String, exporterId: String, version: Int) {
        listOf("resultId", "sourceFinalRevisionId", "dispatchVisitId", "dispatchItemId", "assignmentMaterialHash", "assignmentIssuerId", "technicianId", "technicianName", "serviceDate", "outcome", "customerSnapshot", "siteSnapshot", "subjectSnapshot", "workSnapshot", "checklist", "findings", "parts", "followUps", "recurrence").forEach { key ->
            require(o.has(key) && !o.isNull(key)) { "Missing result $key" }
        }
        listOf("resultId", "sourceFinalRevisionId", "dispatchVisitId", "dispatchItemId", "assignmentMaterialHash", "assignmentIssuerId", "technicianId", "technicianName", "serviceDate", "outcome").forEach { key ->
            require(o.get(key) is String) { "Invalid result $key type" }
        }
        require(o.get("assignmentGeneration") is Number && o.get("assignmentGeneration").toString().matches(Regex("[1-9][0-9]*"))) { "Invalid assignment generation" }
        require(o.getString("assignmentIssuerId") == targetIssuerId) { "Result issuer mismatch" }
        if (version == VERSION) {
            listOf("originWorkspaceId", "sourceVisitId", "sourceWorkItemId", "sourceFinalRevisionId", "visitReference", "recordedAt").forEach { key ->
                require(o.get(key) is String && o.getString(key).isNotBlank()) { "Missing v2 source identity: $key" }
            }
            require(o.getString("originWorkspaceId") == exporterId) { "Result source origin differs from its author" }
            Instant.parse(o.getString("recordedAt"))
            require(o.get("sourceWorkItemPosition") is Number && o.getInt("sourceWorkItemPosition") > 0) { "Invalid source work position" }
            require(o.get("sourceFinalRevisionNumber") is Number && o.getInt("sourceFinalRevisionNumber") > 0) { "Invalid source revision number" }
            require(o.has("supersedesSourceFinalRevisionId") && (o.isNull("supersedesSourceFinalRevisionId") || o.get("supersedesSourceFinalRevisionId") is String) &&
                o.has("correctionReason") && o.has("publicNote")) { "Invalid source correction lineage or note" }
            require(o.has("followUpCaptureState") && o.getString("followUpCaptureState") in setOf("CAPTURED_AT_REVISION", "UNAVAILABLE_LEGACY")) { "Missing source follow-up capture state" }
            o.getJSONArray("sourcePhotos")
        }
        require(o.getInt("assignmentGeneration") > 0) { "Invalid assignment generation" }
        require(o.getString("resultId").isNotBlank() && o.getString("sourceFinalRevisionId").isNotBlank())
        require(o.getString("dispatchVisitId").isNotBlank() && o.getString("dispatchItemId").isNotBlank())
        require(o.getString("assignmentMaterialHash").matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid assignment material hash" }
        require(o.getString("technicianId").isNotBlank() && o.getString("technicianName").isNotBlank())
        java.time.LocalDate.parse(o.getString("serviceDate"))
        o.getJSONObject("customerSnapshot"); o.getJSONObject("siteSnapshot"); o.getJSONObject("subjectSnapshot")
        val work = o.getJSONObject("workSnapshot")
        val recurrence = o.getJSONObject("recurrence")
        o.getJSONArray("checklist"); o.getJSONArray("findings"); o.getJSONArray("parts"); o.getJSONArray("followUps")
        val outcome = o.getString("outcome")
        require(outcome in setOf("PERFORMED", "PARTLY_PERFORMED", "NOT_PERFORMED")) { "Unsupported result outcome" }
        if (outcome == "NOT_PERFORMED") {
            require(work.get("notPerformedReason") is String)
            require(work.getString("notPerformedReason").isNotBlank()) { "Not performed needs a reason" }
        } else {
            require(work.get("publicWork") is String)
            require(work.getString("publicWork").isNotBlank()) { "Performed work needs a description" }
        }
        val fulfilled = if (work.has("fulfilledObligation")) {
            require(work.get("fulfilledObligation") is Boolean) { "Invalid fulfillment decision" }
            work.getBoolean("fulfilledObligation")
        } else {
            require(version != VERSION && outcome != "PARTLY_PERFORMED") { "Result needs an explicit fulfillment decision" }
            false
        }
        if (recurrence.has("fulfilledObligation")) {
            require(recurrence.get("fulfilledObligation") is Boolean && recurrence.getBoolean("fulfilledObligation") == fulfilled) {
                "Result recurrence contradicts work"
            }
        } else require(version != VERSION) { "Result recurrence needs a fulfillment decision" }
        require(outcome != "NOT_PERFORMED" || !fulfilled) { "Not performed cannot fulfill an obligation" }
        fun nullableDate(value: JSONObject, key: String): String? {
            if (!value.has(key) || value.isNull(key)) return null
            require(value.get(key) is String) { "Invalid $key date type" }
            return value.getString(key).also { java.time.LocalDate.parse(it) }
        }
        val oldDue = nullableDate(recurrence, "oldDueDate")
        val nextDue = nullableDate(recurrence, "nextDueDate")
        if (work.has("oldDueDate")) require(nullableDate(work, "oldDueDate") == oldDue) { "Captured old due date differs from recurrence" }
        if (work.has("nextDueDate")) require(nullableDate(work, "nextDueDate") == nextDue) { "Captured next due date differs from recurrence" }
        if (fulfilled) require(oldDue != null && nextDue != null && nextDue > oldDue) { "Fulfilled result needs a later due date" }
        if (outcome == "NOT_PERFORMED") require(nextDue == null) { "Not performed result cannot advance recurrence" }
    }

    private fun validateSourcePhotos(result: JSONObject, photos: List<Photo>) {
        val facts = result.getJSONArray("sourcePhotos")
        require(facts.length() == photos.size) { "Source photo facts do not match result binaries" }
        val seen = mutableSetOf<String>()
        for (index in 0 until facts.length()) {
            val fact = facts.getJSONObject(index)
            val sourceId = fact.getString("sourcePhotoId")
            require(sourceId.isNotBlank() && seen.add(sourceId) && fact.getString("sourceWorkItemId") == result.getString("sourceWorkItemId")) { "Invalid source photo identity" }
            require(fact.getInt("position") > 0 && fact.getLong("originalByteSize") > 0 &&
                fact.getString("originalSha256").matches(Regex("[a-fA-F0-9]{64}")) &&
                fact.getString("originalMimeType").startsWith("image/")) { "Invalid original photo descriptor" }
            val photo = photos.singleOrNull { it.sourcePhotoId == sourceId } ?: throw IllegalArgumentException("Source photo binary is missing")
            require(fact.getString("visibility") == photo.visibility && fact.getBoolean("includeInReport") == photo.includeInReport &&
                (if (fact.isNull("caption")) null else fact.getString("caption")) == photo.caption) { "Source photo flags conflict with binary descriptor" }
        }
    }

    internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
