package com.v16studio.serviceloop

import com.v16studio.serviceloop.data.SourceCanonicalJson
import com.v16studio.serviceloop.data.WorkResultPackageCodec
import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SourceCanonicalJsonTest {
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
