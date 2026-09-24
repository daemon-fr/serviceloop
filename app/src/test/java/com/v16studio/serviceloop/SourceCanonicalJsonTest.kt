package com.v16studio.serviceloop

import com.v16studio.serviceloop.data.SourceCanonicalJson
import com.v16studio.serviceloop.data.WorkResultPackageCodec
import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SourceCanonicalJsonTest {
    @Test fun resultAndPhotoCountLimitsRejectBeforePackagingBytes() {
        val record = JSONObject(requireNotNull(javaClass.classLoader!!.getResourceAsStream("contracts/work-result-v1-record.json"))
            .use { it.readBytes().toString(Charsets.UTF_8) })
        val result = WorkResultPackageCodec.Result(record, emptyList())
        val base = WorkResultPackageCodec.Package("bounded", record.getString("technicianId"),
            record.getString("assignmentIssuerId"), "2026-09-24T10:00:00Z", listOf(result))
        assertThrows(IllegalArgumentException::class.java) {
            WorkResultPackageCodec.encode(base.copy(results = List(WorkResultPackageCodec.MAX_RESULTS + 1) { result }))
        }
        val photos = List(WorkResultPackageCodec.MAX_PHOTOS + 1) { index ->
            WorkResultPackageCodec.Photo("source-$index", byteArrayOf(1), null, true, "PUBLIC", "dispatch:item", 1, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkResultPackageCodec.encode(base.copy(results = listOf(result.copy(photos = photos))))
        }
    }

    @Test fun v1RejectsContradictoryTypedChecklistPartsAndFollowUps() {
        val record = JSONObject(requireNotNull(javaClass.classLoader!!.getResourceAsStream("contracts/work-result-v1-record.json"))
            .use { it.readBytes().toString(Charsets.UTF_8) })
        fun rejects(change: (JSONObject) -> Unit) {
            val changed = JSONObject(record.toString()).also(change)
            val value = WorkResultPackageCodec.Package("semantic-negative", changed.getString("technicianId"),
                changed.getString("assignmentIssuerId"), "2026-09-24T10:00:00Z",
                listOf(WorkResultPackageCodec.Result(changed, emptyList())))
            assertThrows(IllegalArgumentException::class.java) { WorkResultPackageCodec.encode(value) }
        }
        rejects { it.getJSONArray("checklist").getJSONObject(0).put("position", "1") }
        rejects { it.getJSONArray("checklist").getJSONObject(0).put("disposition", "VALUE") }
        rejects { it.getJSONArray("checklist").getJSONObject(0).put("disposition", "ISSUE_FOUND") }
        rejects { it.getJSONArray("parts").getJSONObject(0).put("quantity", "0") }
        rejects { it.getJSONArray("parts").getJSONObject(0).put("quantity", "1.2.3") }
        rejects { it.getJSONArray("parts").getJSONObject(0).put("quantity", 1.25) }
        rejects { it.getJSONObject("workSnapshot").put("fulfilledObligation", true) }
        rejects { it.getJSONObject("recurrence").put("nextDueDate", "2027-09-24") }
        rejects { it.getJSONObject("recurrence").put("nextDueDateCalculated", true) }
        rejects { it.getJSONObject("workSnapshot").put("nextDueOverrideReason", "Unmatched override") }
        rejects { it.put("followUpCaptureState", "UNCAPTURED").put("followUps", JSONArray().put(
            JSONObject().put("sourceId", "f").put("type", "CALL").put("title", "Call")
                .put("dueDate", "2026-10-01").put("state", "OPEN"))) }
        rejects { it.put("followUps", JSONArray().put(JSONObject().put("sourceId", "f").put("type", "CALL")
            .put("title", "Call").put("dueDate", "2026-10-01").put("state", "OPEN"))
            .put(JSONObject().put("sourceId", "f").put("type", "CALL").put("title", "Call again")
                .put("dueDate", "2026-10-02").put("state", "OPEN"))) }
    }

    @Test fun v1SourcePhotoFactsRejectCoercedNumericAndBooleanTypes() {
        val record = JSONObject(requireNotNull(javaClass.classLoader!!.getResourceAsStream("contracts/work-result-v1-record.json"))
            .use { it.readBytes().toString(Charsets.UTF_8) })
        val fact = JSONObject().put("sourcePhotoId", "p").put("sourceWorkItemId", record.getString("sourceWorkItemId"))
            .put("position", 1).put("originalByteSize", 1).put("originalSha256", "a".repeat(64))
            .put("originalMimeType", "image/jpeg").put("caption", JSONObject.NULL)
            .put("visibility", "PUBLIC").put("includeInReport", true).put("addedInCorrection", false)
            .put("addedAtEpochMillis", JSONObject.NULL)
        val photo = WorkResultPackageCodec.Photo("p", byteArrayOf(1), null, true, "PUBLIC",
            record.getString("dispatchItemId"), 1, 1)
        fun rejects(key: String, value: Any) {
            val changed = JSONObject(record.toString()).put("sourcePhotos", JSONArray().put(JSONObject(fact.toString()).put(key, value)))
            val payload = WorkResultPackageCodec.Package("typed-photo", changed.getString("technicianId"),
                changed.getString("assignmentIssuerId"), "2026-09-24T10:00:00Z",
                listOf(WorkResultPackageCodec.Result(changed, listOf(photo))))
            assertThrows(IllegalArgumentException::class.java) { WorkResultPackageCodec.encode(payload) }
        }
        rejects("position", "1")
        rejects("sourcePhotoId", 7)
        rejects("originalByteSize", "1")
        rejects("includeInReport", "true")
        rejects("addedInCorrection", 0)
    }

    @Test fun portableV1RecordFixtureMatchesLiteralBytesDigestAndAcceptedWireSemantics() {
        fun fixture(name: String) = requireNotNull(javaClass.classLoader!!.getResourceAsStream("contracts/$name")).use { it.readBytes() }
        val record = JSONObject(fixture("work-result-v1-record.json").toString(Charsets.UTF_8))
        val canonical = fixture("work-result-v1-canonical.json")
        val digest = fixture("work-result-v1.sha256").toString(Charsets.US_ASCII).trim()
        assertEquals(canonical.toList(), SourceCanonicalJson.bytes(record).toList())
        assertEquals(digest, WorkResultPackageCodec.sha256(canonical))
        assertEquals("SLT-0000-0000-0001-58", record.getString("technicianId"))
        assertTrue(record.isNull("publicNote"))
        assertEquals("1.25", record.getJSONArray("parts").getJSONObject(0).getString("quantity"))
        val exporter = record.getString("technicianId")
        val issuer = record.getString("assignmentIssuerId")
        val value = WorkResultPackageCodec.Package("fixed-package", exporter, issuer, "2026-09-24T10:00:00Z",
            listOf(WorkResultPackageCodec.Result(record, emptyList())))
        val decoded = WorkResultPackageCodec.decode(WorkResultPackageCodec.encode(value))
        assertEquals("work|one", decoded.results.single().value.getString("sourceWorkItemId"))
        assertEquals("revision:one", decoded.results.single().value.getString("supersedesSourceFinalRevisionId"))
        assertThrows(IllegalArgumentException::class.java) {
            WorkResultPackageCodec.encode(value.copy(results = listOf(WorkResultPackageCodec.Result(
                JSONObject(record.toString()).put("outcome", "DONE"), emptyList()))))
        }
    }

    @Test fun literalBytesAndDigestAreStable() {
        val value = JSONObject().put("b", JSONArray().put(JSONObject.NULL).put(true).put("x\n")).put("a", 1)
        val expected = "{\"a\":1,\"b\":[null,true,\"x\\n\"]}"
        assertEquals(expected, SourceCanonicalJson.text(value))
        assertEquals("a6b2a555ff79293a8ca2dadbfa7fe35f7f1008ac2b71507086cdce782a12b338",
            WorkResultPackageCodec.sha256(SourceCanonicalJson.bytes(value)))
    }

    @Test fun codePointOrderAndExactDecimalText() {
        val value = JSONObject().put("\uD800\uDC00", 2).put("\uE000", BigDecimal("1.2300"))
        assertEquals("{\"\uE000\":1.2300,\"\uD800\uDC00\":2}", SourceCanonicalJson.text(value))
    }

    @Test fun invalidSurrogatesAndFloatingPointValuesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { SourceCanonicalJson.text("\uD800") }
        assertThrows(IllegalArgumentException::class.java) { SourceCanonicalJson.text("\uDC00") }
        assertThrows(IllegalArgumentException::class.java) { SourceCanonicalJson.text(1.25) }
    }
}
