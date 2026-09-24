package com.v16studio.serviceloop.data

import org.json.JSONObject

/** Immutable accepted semantic record, separate from receiver indexes and binary storage paths. */
internal object FinalSourceSnapshot {
    fun encode(purpose: String, payloadVersion: Int, fingerprintVersion: Int, record: JSONObject): String {
        require(purpose in setOf("WORK_RESULT", "PERFORMED_WORK") && payloadVersion == 1 && fingerprintVersion == 1)
        return JSONObject().put("snapshotFormatVersion", 1).put("sourcePurpose", purpose)
            .put("sourcePayloadVersion", payloadVersion).put("fingerprintVersion", fingerprintVersion)
            .put("record", JSONObject(record.toString())).toString()
    }

    /** Current immutable source snapshots always use the v1 wrapper and full v1 payload. */
    fun record(raw: String, expectedPurpose: String): JSONObject {
        val snapshot = JSONObject(raw)
        require(snapshot.keys().asSequence().toSet() == setOf(
            "snapshotFormatVersion", "sourcePurpose", "sourcePayloadVersion", "fingerprintVersion", "record",
        )) { "Unsupported source snapshot shape" }
        require(snapshot.getInt("snapshotFormatVersion") == 1 &&
            snapshot.getString("sourcePurpose") == expectedPurpose &&
            snapshot.getInt("sourcePayloadVersion") == 1 &&
            snapshot.getInt("fingerprintVersion") == 1) { "Unsupported source snapshot version" }
        return snapshot.getJSONObject("record")
    }

    fun fingerprintVersion(raw: String): Int {
        val wrapper = JSONObject(raw)
        val purpose = wrapper.getString("sourcePurpose")
        record(raw, purpose)
        return wrapper.getInt("fingerprintVersion")
    }
}
