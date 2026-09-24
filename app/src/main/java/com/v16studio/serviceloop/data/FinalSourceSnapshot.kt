package com.v16studio.serviceloop.data

import org.json.JSONObject

/** Immutable accepted semantic record, separate from receiver indexes and binary storage paths. */
internal object FinalSourceSnapshot {
    fun encode(purpose: String, payloadVersion: Int, fingerprintVersion: Int, record: JSONObject): String {
        require(purpose in setOf("WORK_RESULT", "PERFORMED_WORK") && payloadVersion in 1..2 && fingerprintVersion in 1..2)
        return JSONObject().put("snapshotFormatVersion", 1).put("sourcePurpose", purpose)
            .put("sourcePayloadVersion", payloadVersion).put("fingerprintVersion", fingerprintVersion)
            .put("record", JSONObject(record.toString())).toString()
    }

    /** The prior repair checkpoint used result/raw shapes. Preserve those during a normal upgrade. */
    fun record(raw: String, expectedPurpose: String): JSONObject {
        val snapshot = JSONObject(raw)
        if (snapshot.has("snapshotFormatVersion")) {
            require(snapshot.getInt("snapshotFormatVersion") == 1 && snapshot.getString("sourcePurpose") == expectedPurpose)
            require(snapshot.getInt("sourcePayloadVersion") in 1..2 && snapshot.getInt("fingerprintVersion") in 1..2)
            return snapshot.getJSONObject("record")
        }
        return if (expectedPurpose == "WORK_RESULT") snapshot.getJSONObject("result") else snapshot
    }

    fun fingerprintVersion(raw: String): Int {
        val snapshot = JSONObject(raw)
        return if (snapshot.has("snapshotFormatVersion")) snapshot.getInt("fingerprintVersion") else snapshot.optInt("comparisonVersion", 1)
    }
}
