package com.v16studio.v16service.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException
import java.security.MessageDigest
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Transport-neutral WORK_RESULT contract. Every photo is a declared envelope entry. */
internal object WorkResultPackageCodec {
    const val VERSION = 1
    const val MAX_PHOTO_BYTES = 10 * 1024 * 1024
    const val MAX_RESULTS = 100
    const val MAX_PHOTOS = 120
    private fun resultSection(version: Int) = V16ServiceSyncSectionDeclaration("results", version, "results.json")

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
        val version = VERSION
        val declarations = mutableListOf(resultSection(version))
        val results = JSONArray()
        val photoIds = mutableSetOf<Triple<String, String, String>>()
        value.results.forEach { result ->
            validateResultSafe(result.value, value.targetIssuerId, value.exporterId)
            validateSourcePhotos(result.value, result.photos)
            val json = JSONObject(result.value.toString())
            val photos = JSONArray()
            result.photos.forEach { photo ->
                require(photoIds.add(Triple(json.getString("resultId"), json.getString("sourceFinalRevisionId"), photo.sourcePhotoId))) { "Duplicate source photo in result revision" }
                val sectionName = "photo-${UUID.randomUUID()}"
                val fileName = "$sectionName.jpg"
                declarations += V16ServiceSyncSectionDeclaration(sectionName, 1, fileName)
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
        return V16ServiceSyncEnvelopeCodec.encode(
            V16ServiceSyncManifest(value.packageId, "V16 Service work results", "WORK_RESULT", value.generatedAt, sections = declarations, exporterId = value.exporterId), sections,
        )
    }

    fun decode(bytes: ByteArray): Package {
        val envelope = V16ServiceSyncEnvelopeCodec.decode(bytes)
        val manifest = envelope.manifest
        require(manifest.formatVersion == 1 && manifest.purpose == "WORK_RESULT") { "Choose a work-result V16 Service file" }
        require(manifest.sections.firstOrNull()?.let { it == resultSection(VERSION) } == true) { "Unsupported work-result payload" }
        val root = JSONObject(envelope.section("results").toString(Charsets.UTF_8))
        val version = root.getInt("version")
        require(version == VERSION && manifest.sections.first() == resultSection(version)) { "Unsupported work-result version" }
        val issuerId = root.getString("targetIssuerId")
        require(TechnicianIdCodec.normalize(issuerId) == issuerId) { "Invalid assignment issuer" }
        val resultArray = root.getJSONArray("results")
        require(resultArray.length() in 1..MAX_RESULTS) { "Invalid result count" }
        val usedSections = mutableSetOf("results")
        val resultKeys = mutableSetOf<Pair<String, String>>()
        val photoIds = mutableSetOf<Triple<String, String, String>>()
        val results = (0 until resultArray.length()).map { index ->
            val json = resultArray.getJSONObject(index)
            validateResultSafe(json, issuerId, manifest.exporterId)
            require(resultKeys.add(json.getString("resultId") to json.getString("sourceFinalRevisionId"))) { "Duplicate result revision" }
            val photoArray = json.getJSONArray("photos")
            require(photoArray.length() <= MAX_PHOTOS) { "Too many result photos" }
            val photos = (0 until photoArray.length()).map { photoIndex ->
                val photo = photoArray.getJSONObject(photoIndex)
                val sectionName = photo.getString("section")
                require(usedSections.add(sectionName) && manifest.sections.any { it == V16ServiceSyncSectionDeclaration(sectionName, 1, "$sectionName.jpg") }) { "Undeclared or duplicate photo" }
                val photoId = photo.getString("sourcePhotoId")
                require(photoIds.add(Triple(json.getString("resultId"), json.getString("sourceFinalRevisionId"), photoId))) { "Duplicate source photo in result revision" }
                require(photo.get("sourcePhotoId") is String && photo.get("workItemId") is String &&
                    photo.getString("workItemId") == json.getString("dispatchItemId") &&
                    photo.get("section") is String && photo.get("sha256") is String &&
                    photo.getString("sha256").matches(Regex("[a-fA-F0-9]{64}")) &&
                    photo.get("mimeType") is String &&
                    photo.get("includeInReport") is Boolean && photo.get("visibility") is String &&
                    (photo.isNull("caption") || photo.get("caption") is String) &&
                    photo.get("width") is Number && photo.get("width").toString().matches(Regex("[1-9][0-9]*")) &&
                    photo.get("height") is Number && photo.get("height").toString().matches(Regex("[1-9][0-9]*")) &&
                    photo.get("byteSize") is Number && photo.get("byteSize").toString().matches(Regex("[1-9][0-9]*"))) {
                    "Invalid result photo descriptor"
                }
                val data = envelope.section(sectionName)
                require(data.size in 1..MAX_PHOTO_BYTES && photo.getLong("byteSize") == data.size.toLong() && photo.getString("sha256") == sha256(data)) { "Result photo failed integrity check" }
                require(photo.getString("mimeType") == "image/jpeg") { "Unsupported result photo format" }
                Photo(photoId, data, photo.optString("caption").takeIf { it.isNotBlank() }, photo.getBoolean("includeInReport"), photo.getString("visibility"), photo.getString("workItemId"), photo.getInt("width"), photo.getInt("height"))
            }
            validateSourcePhotos(json, photos)
            Result(json, photos)
        }
        require(results.sumOf { it.photos.size } <= MAX_PHOTOS && usedSections == manifest.sections.map { it.name }.toSet()) { "Work-result binary declarations do not match" }
        return Package(manifest.syncId, requireNotNull(manifest.exporterId), issuerId, manifest.generatedAt, results)
    }

    private fun validateResultSafe(o: JSONObject, targetIssuerId: String, exporterId: String) {
        try {
            validateResult(o, targetIssuerId, exporterId)
        } catch (failure: JSONException) {
            throw IllegalArgumentException("Malformed work-result semantics: ${failure.message}", failure)
        }
    }

    private fun validateResult(o: JSONObject, targetIssuerId: String, exporterId: String) {
        listOf("resultId", "sourceFinalRevisionId", "dispatchVisitId", "dispatchItemId", "assignmentMaterialHash", "assignmentIssuerId", "technicianId", "technicianName", "serviceDate", "outcome", "customerSnapshot", "siteSnapshot", "subjectSnapshot", "workSnapshot", "checklist", "findings", "parts", "followUps", "recurrence").forEach { key ->
            require(o.has(key) && !o.isNull(key)) { "Missing result $key" }
        }
        listOf("resultId", "sourceFinalRevisionId", "dispatchVisitId", "dispatchItemId", "assignmentMaterialHash", "assignmentIssuerId", "technicianId", "technicianName", "serviceDate", "outcome").forEach { key ->
            require(o.get(key) is String) { "Invalid result $key type" }
        }
        require(o.get("assignmentGeneration") is Number && o.get("assignmentGeneration").toString().matches(Regex("[1-9][0-9]*"))) { "Invalid assignment generation" }
        require(o.getString("assignmentIssuerId") == targetIssuerId) { "Result issuer mismatch" }
        listOf("originWorkspaceId", "sourceVisitId", "sourceWorkItemId", "sourceFinalRevisionId", "visitReference", "recordedAt").forEach { key ->
            require(o.get(key) is String && o.getString(key).isNotBlank()) { "Missing source identity: $key" }
        }
        require(o.getString("originWorkspaceId") == exporterId) { "Result source origin differs from its author" }
        Instant.parse(o.getString("recordedAt"))
        require(o.get("sourceWorkItemPosition") is Number && o.get("sourceWorkItemPosition").toString().matches(Regex("[1-9][0-9]*"))) { "Invalid source work position" }
        require(o.get("sourceFinalRevisionNumber") is Number && o.get("sourceFinalRevisionNumber").toString().matches(Regex("[1-9][0-9]*"))) { "Invalid source revision number" }
        require(o.has("supersedesSourceFinalRevisionId") && (o.isNull("supersedesSourceFinalRevisionId") || o.get("supersedesSourceFinalRevisionId") is String) &&
            o.has("correctionReason") && o.has("publicNote")) { "Invalid source correction lineage or note" }
        require(o.has("followUpCaptureState") && o.getString("followUpCaptureState") == "CAPTURED_AT_REVISION") { "Missing source follow-up capture state" }
        listOf("sourceCustomerRef", "sourceSiteRef", "sourceEquipmentRef").forEach { key ->
            if (o.has(key) && !o.isNull(key)) {
                val ref = o.getJSONObject(key)
                require(ref.get("originWorkspaceId") is String &&
                    TechnicianIdCodec.normalize(ref.getString("originWorkspaceId")) == ref.getString("originWorkspaceId") &&
                    ref.get("sourceEntityId") is String && ref.getString("sourceEntityId").isNotBlank()) {
                    "Invalid $key source identity"
                }
            }
        }
        o.getJSONArray("sourcePhotos")
        require(o.getInt("assignmentGeneration") > 0) { "Invalid assignment generation" }
        require(o.getString("resultId").isNotBlank() && o.getString("sourceFinalRevisionId").isNotBlank())
        require(o.getString("dispatchVisitId").isNotBlank() && o.getString("dispatchItemId").isNotBlank())
        require(o.getString("assignmentMaterialHash").matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid assignment material hash" }
        require(o.getString("technicianId").isNotBlank() && o.getString("technicianName").isNotBlank())
        require(o.getString("technicianId") == exporterId) { "Result author differs from package exporter" }
        java.time.LocalDate.parse(o.getString("serviceDate"))
        o.getJSONObject("customerSnapshot"); o.getJSONObject("siteSnapshot"); o.getJSONObject("subjectSnapshot")
        val subject = o.getJSONObject("subjectSnapshot")
        require(subject.has("type") && subject.get("type") is String && subject.getString("type") in setOf("SITE", "EQUIPMENT")) {
            "Invalid result subject"
        }
        val work = o.getJSONObject("workSnapshot")
        require(work.get("serviceName") is String && work.getString("serviceName").isNotBlank()) { "Result service is missing" }
        val recurrence = o.getJSONObject("recurrence")
        val checklist = o.getJSONArray("checklist")
        val findings = o.getJSONArray("findings")
        val parts = o.getJSONArray("parts")
        val followUps = o.getJSONArray("followUps")
        require(checklist.length() <= 1000) { "Too many checklist responses" }
        val checklistPositions = mutableSetOf<Int>()
        for (index in 0 until checklist.length()) {
            val item = checklist.getJSONObject(index)
            require(item.get("position") is Number && item.get("position").toString().matches(Regex("[1-9][0-9]*")) &&
                checklistPositions.add(item.getInt("position")) &&
                item.get("label") is String && item.getString("label").isNotBlank() &&
                item.get("responseType") is String && item.getString("responseType") in setOf("STATUS", "TEXT", "NUMBER") &&
                item.get("required") is Boolean && item.get("disposition") is String &&
                item.getString("disposition") in setOf("UNANSWERED", "NOT_CHECKED", "OK", "ISSUE_FOUND", "NOT_APPLICABLE", "VALUE")) {
                "Invalid result checklist response"
            }
            fun nullableText(key: String) = !item.has(key) || item.isNull(key) || item.get(key) is String
            require(nullableText("unit") && nullableText("textValue") && nullableText("numberValue") && nullableText("reason")) {
                "Invalid checklist answer type"
            }
            when (item.getString("disposition")) {
                "VALUE" -> require(when (item.getString("responseType")) {
                    "TEXT" -> !item.isNull("textValue") && item.getString("textValue").isNotBlank()
                    "NUMBER" -> !item.isNull("numberValue") && runCatching { BigDecimal(item.getString("numberValue")) }.isSuccess
                    else -> false
                }) { "Checklist value does not match response type" }
                "ISSUE_FOUND" -> require(item.getString("responseType") == "STATUS") { "Invalid issue response type" }
                else -> Unit
            }
        }
        require(findings.length() <= 1000 && parts.length() <= 1000 && followUps.length() <= 1000) { "Too many result facts" }
        val issues = (0 until checklist.length()).map { checklist.getJSONObject(it) }
            .filter { it.getString("disposition") == "ISSUE_FOUND" }
        require(findings.length() == issues.size) { "Findings differ from checklist issues" }
        for (index in 0 until findings.length()) {
            val finding = findings.getJSONObject(index)
            val issue = issues[index]
            require(finding.get("position") is Number && finding.getInt("position") == issue.getInt("position") &&
                finding.get("label") is String && finding.getString("label") == issue.getString("label") &&
                (!finding.has("description") || finding.isNull("description") || finding.get("description") is String) &&
                (if (!issue.has("reason") || issue.isNull("reason")) !finding.has("description") || finding.isNull("description")
                 else finding.getString("description") == issue.getString("reason"))) {
                "Finding differs from checklist issue"
            }
        }
        val partPositions = mutableSetOf<Int>()
        for (index in 0 until parts.length()) {
            val part = parts.getJSONObject(index)
            require(part.get("position") is Number && part.get("position").toString().matches(Regex("[1-9][0-9]*")) &&
                partPositions.add(part.getInt("position")) && part.get("description") is String &&
                part.getString("description").isNotBlank() && part.get("quantity") is String &&
                runCatching { BigDecimal(part.getString("quantity")).signum() > 0 }.getOrDefault(false) &&
                part.get("unit") is String && part.getString("unit").isNotBlank()) { "Invalid result part" }
        }
        val followUpIds = mutableSetOf<String>()
        for (index in 0 until followUps.length()) {
            val followUp = followUps.getJSONObject(index)
            require(followUp.get("sourceId") is String && followUpIds.add(followUp.getString("sourceId")) &&
                followUp.getString("sourceId").isNotBlank() && followUp.get("type") is String &&
                followUp.get("title") is String && followUp.getString("title").isNotBlank() &&
                followUp.get("dueDate") is String && followUp.get("state") is String &&
                (!followUp.has("privatePlanningNote") || followUp.isNull("privatePlanningNote") || followUp.get("privatePlanningNote") is String)) {
                "Invalid captured follow-up"
            }
            java.time.LocalDate.parse(followUp.getString("dueDate"))
        }
        val outcome = o.getString("outcome")
        require(outcome in setOf("PERFORMED", "PARTLY_PERFORMED", "NOT_PERFORMED")) { "Unsupported result outcome" }
        if (outcome == "NOT_PERFORMED") {
            require(work.get("notPerformedReason") is String)
            require(work.getString("notPerformedReason").isNotBlank()) { "Not performed needs a reason" }
        } else {
            require(work.get("publicWork") is String)
            require(work.getString("publicWork").isNotBlank()) { "Performed work needs a description" }
        }
        require(work.get("fulfilledObligation") is Boolean) { "Result needs an explicit fulfillment decision" }
        val fulfilled = work.getBoolean("fulfilledObligation")
        require(recurrence.get("fulfilledObligation") is Boolean && recurrence.getBoolean("fulfilledObligation") == fulfilled) {
            "Result recurrence contradicts work"
        }
        if (recurrence.has("intervalCount") && !recurrence.isNull("intervalCount")) require(
            recurrence.get("intervalCount") is Number && recurrence.get("intervalCount").toString().matches(Regex("[1-9][0-9]*"))) {
            "Invalid result interval count"
        }
        if (recurrence.has("intervalUnit") && !recurrence.isNull("intervalUnit")) require(
            recurrence.get("intervalUnit") is String && recurrence.getString("intervalUnit") in setOf("DAYS", "WEEKS", "MONTHS", "YEARS")) {
            "Invalid result interval unit"
        }
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
        fun nullableFact(value: JSONObject, key: String): Any? =
            if (!value.has(key) || value.isNull(key)) null else value.get(key)
        listOf("planId", "capturedObligationId", "nextDueDateCalculated", "nextDueOverrideReason").forEach { key ->
            require(nullableFact(work, key) == nullableFact(recurrence, key)) { "Work and recurrence disagree on $key" }
        }
        val calculated = nullableFact(recurrence, "nextDueDateCalculated")
        val overrideReason = nullableFact(recurrence, "nextDueOverrideReason")
        require(calculated == null || calculated is Boolean) { "Invalid next due calculation choice" }
        require(overrideReason == null || overrideReason is String) { "Invalid next due override reason" }
        if (fulfilled) {
            require(nullableFact(recurrence, "capturedObligationId") is String &&
                (nullableFact(recurrence, "capturedObligationId") as String).isNotBlank() &&
                recurrence.get("intervalCount") is Number && recurrence.get("intervalUnit") is String &&
                calculated is Boolean) { "Fulfilled result lacks captured recurrence facts" }
            val expected = com.v16studio.v16service.domain.RecurrenceCalculator.nextDate(
                java.time.LocalDate.parse(o.getString("serviceDate")), recurrence.getInt("intervalCount"),
                recurrence.getString("intervalUnit")).toString()
            require(if (calculated) nextDue == expected && overrideReason == null
                else overrideReason is String && overrideReason.isNotBlank()) {
                "Next due date contradicts its calculation or override"
            }
        } else require(calculated == null && overrideReason == null && nextDue == null) {
            "Unfulfilled result cannot change recurrence"
        }
    }

    private fun validateSourcePhotos(result: JSONObject, photos: List<Photo>) {
        val facts = result.getJSONArray("sourcePhotos")
        require(facts.length() == photos.size) { "Source photo facts do not match result binaries" }
        val seen = mutableSetOf<String>()
        for (index in 0 until facts.length()) {
            val fact = facts.getJSONObject(index)
            val sourceId = fact.getString("sourcePhotoId")
            require(fact.get("sourcePhotoId") is String && fact.get("sourceWorkItemId") is String &&
                sourceId.isNotBlank() && seen.add(sourceId) &&
                fact.getString("sourceWorkItemId") == result.getString("sourceWorkItemId")) { "Invalid source photo identity" }
            require(fact.get("position") is Number && fact.get("position").toString().matches(Regex("[1-9][0-9]*")) &&
                fact.get("originalByteSize") is Number && fact.get("originalByteSize").toString().matches(Regex("[1-9][0-9]*")) &&
                fact.get("originalByteSize").toString().toLong() <= AppOwnedImageNormalizer.MAX_SOURCE_BYTES &&
                fact.get("originalSha256") is String && fact.get("originalMimeType") is String &&
                fact.get("visibility") is String && fact.get("includeInReport") is Boolean &&
                fact.get("addedInCorrection") is Boolean &&
                (!fact.has("addedAtEpochMillis") || fact.isNull("addedAtEpochMillis") ||
                    fact.get("addedAtEpochMillis") is Number && fact.get("addedAtEpochMillis").toString().matches(Regex("[0-9]+"))) &&
                (!fact.has("caption") || fact.isNull("caption") || fact.get("caption") is String) &&
                fact.getString("originalSha256").matches(Regex("[a-fA-F0-9]{64}")) &&
                fact.getString("originalMimeType").startsWith("image/")) { "Invalid original photo descriptor" }
            val photo = photos.singleOrNull { it.sourcePhotoId == sourceId } ?: throw IllegalArgumentException("Source photo binary is missing")
            require(fact.getString("visibility") == photo.visibility && fact.getBoolean("includeInReport") == photo.includeInReport &&
                (if (fact.isNull("caption")) null else fact.getString("caption")) == photo.caption) { "Source photo flags conflict with binary descriptor" }
        }
    }

    internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
