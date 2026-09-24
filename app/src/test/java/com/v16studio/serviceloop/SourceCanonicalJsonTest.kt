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
    @Test fun portableV2RecordFixtureMatchesLiteralBytesDigestAndAcceptedWireSemantics() {
        fun fixture(name: String) = requireNotNull(javaClass.classLoader!!.getResourceAsStream("contracts/$name")).use { it.readBytes() }
        val record = JSONObject(fixture("work-result-v2-record.json").toString(Charsets.UTF_8))
        val canonical = fixture("work-result-v2-canonical.json")
        val digest = fixture("work-result-v2.sha256").toString(Charsets.US_ASCII).trim()
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
