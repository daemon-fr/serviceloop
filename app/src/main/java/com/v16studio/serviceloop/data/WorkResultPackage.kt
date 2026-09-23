package com.v16studio.serviceloop.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Transport-neutral WORK_RESULT v1 contract. Every photo is a declared envelope entry. */
internal object WorkResultPackageCodec {
    const val VERSION = 1
    const val MAX_PHOTO_BYTES = 10 * 1024 * 1024
    const val MAX_RESULTS = 100
    const val MAX_PHOTOS = 120
    private val resultSection = ServiceLoopSyncSectionDeclaration("results", VERSION, "results.json")

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
        val declarations = mutableListOf(resultSection)
        val results = JSONArray()
        val photoIds = mutableSetOf<String>()
        value.results.forEach { result ->
            validateResult(result.value, value.targetIssuerId)
            val json = JSONObject(result.value.toString())
            val photos = JSONArray()
            result.photos.forEach { photo ->
                require(photoIds.add(photo.sourcePhotoId)) { "Duplicate source photo" }
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
        sections["results"] = JSONObject().put("version", VERSION).put("targetIssuerId", value.targetIssuerId)
            .put("results", results).toString().toByteArray(Charsets.UTF_8)
        return ServiceLoopSyncEnvelopeCodec.encode(
            ServiceLoopSyncManifest(value.packageId, "ServiceLoop work results", "WORK_RESULT", value.generatedAt, sections = declarations, exporterId = value.exporterId), sections,
        )
    }

    fun decode(bytes: ByteArray): Package {
        val envelope = ServiceLoopSyncEnvelopeCodec.decode(bytes)
        val manifest = envelope.manifest
        require(manifest.formatVersion == 2 && manifest.purpose == "WORK_RESULT") { "Choose a work-result ServiceLoop file" }
        require(manifest.sections.firstOrNull() == resultSection) { "Unsupported work-result payload" }
        val root = JSONObject(envelope.section("results").toString(Charsets.UTF_8))
        require(root.getInt("version") == VERSION) { "Unsupported work-result version" }
        val issuerId = root.getString("targetIssuerId")
        require(TechnicianIdCodec.normalize(issuerId) == issuerId) { "Invalid assignment issuer" }
        val resultArray = root.getJSONArray("results")
        require(resultArray.length() in 1..MAX_RESULTS) { "Invalid result count" }
        val usedSections = mutableSetOf("results")
        val resultKeys = mutableSetOf<Pair<String, String>>()
        val photoIds = mutableSetOf<String>()
        val results = (0 until resultArray.length()).map { index ->
            val json = resultArray.getJSONObject(index)
            validateResult(json, issuerId)
            require(resultKeys.add(json.getString("resultId") to json.getString("sourceFinalRevisionId"))) { "Duplicate result revision" }
            val photoArray = json.getJSONArray("photos")
            require(photoArray.length() <= MAX_PHOTOS) { "Too many result photos" }
            val photos = (0 until photoArray.length()).map { photoIndex ->
                val photo = photoArray.getJSONObject(photoIndex)
                val sectionName = photo.getString("section")
                require(usedSections.add(sectionName) && manifest.sections.any { it == ServiceLoopSyncSectionDeclaration(sectionName, 1, "$sectionName.jpg") }) { "Undeclared or duplicate photo" }
                val photoId = photo.getString("sourcePhotoId")
                require(photoIds.add(photoId)) { "Duplicate source photo" }
                val data = envelope.section(sectionName)
                require(data.size in 1..MAX_PHOTO_BYTES && photo.getLong("byteSize") == data.size.toLong() && photo.getString("sha256") == sha256(data)) { "Result photo failed integrity check" }
                require(photo.getString("mimeType") == "image/jpeg") { "Unsupported result photo format" }
                Photo(photoId, data, photo.optString("caption").takeIf { it.isNotBlank() }, photo.getBoolean("includeInReport"), photo.getString("visibility"), photo.getString("workItemId"), photo.getInt("width"), photo.getInt("height"))
            }
            Result(json, photos)
        }
        require(results.sumOf { it.photos.size } <= MAX_PHOTOS && usedSections == manifest.sections.map { it.name }.toSet()) { "Work-result binary declarations do not match" }
        return Package(manifest.syncId, requireNotNull(manifest.exporterId), issuerId, manifest.generatedAt, results)
    }

    private fun validateResult(o: JSONObject, targetIssuerId: String) {
        listOf("resultId", "sourceFinalRevisionId", "dispatchVisitId", "dispatchItemId", "assignmentMaterialHash", "assignmentIssuerId", "technicianId", "technicianName", "serviceDate", "outcome", "customerSnapshot", "siteSnapshot", "subjectSnapshot", "workSnapshot", "checklist", "findings", "parts", "followUps", "recurrence").forEach { key ->
            require(o.has(key) && !o.isNull(key)) { "Missing result $key" }
        }
        require(o.getString("assignmentIssuerId") == targetIssuerId) { "Result issuer mismatch" }
        require(o.getInt("assignmentGeneration") > 0) { "Invalid assignment generation" }
        require(o.getString("resultId").isNotBlank() && o.getString("sourceFinalRevisionId").isNotBlank())
        require(o.getString("dispatchVisitId").isNotBlank() && o.getString("dispatchItemId").isNotBlank())
        require(o.getString("assignmentMaterialHash").matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid assignment material hash" }
        require(o.getString("technicianId").isNotBlank() && o.getString("technicianName").isNotBlank())
        java.time.LocalDate.parse(o.getString("serviceDate"))
        o.getJSONObject("customerSnapshot"); o.getJSONObject("siteSnapshot"); o.getJSONObject("subjectSnapshot"); o.getJSONObject("workSnapshot")
        o.getJSONArray("checklist"); o.getJSONArray("findings"); o.getJSONArray("parts"); o.getJSONArray("followUps"); o.getJSONObject("recurrence")
    }

    internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
