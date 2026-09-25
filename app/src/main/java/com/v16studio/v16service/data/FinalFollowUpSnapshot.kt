package com.v16studio.v16service.data

import org.json.JSONArray
import org.json.JSONObject

/** Revision-time follow-up facts. SQL null on older final rows means unknown history. */
internal object FinalFollowUpSnapshot {
    private const val MAX_RECORDS = 1000
    private const val MAX_JSON_CHARS = 256_000

    fun capture(capturedAtEpochMillis: Long, records: List<FollowUpEntity>): String {
        require(records.size <= MAX_RECORDS) { "Too many follow-ups to freeze with a final revision" }
        val values = JSONArray()
        records.sortedBy { it.id }.forEach { row ->
            values.put(JSONObject()
                .put("sourceId", row.id)
                .put("type", row.type)
                .put("title", row.title)
                .put("dueDate", row.dueDate)
                .put("privatePlanningNote", row.privatePlanningNote)
                .put("state", row.state)
                .put("customerId", row.customerId)
                .put("siteId", row.siteId)
                .put("equipmentId", row.equipmentId)
                .put("sourceVisitId", row.sourceVisitId)
                .put("sourceWorkItemId", row.sourceWorkItemId))
        }
        return JSONObject().put("snapshotFormatVersion", 1)
            .put("captureState", "CAPTURED_AT_REVISION")
            .put("capturedAtEpochMillis", capturedAtEpochMillis)
            .put("records", values).toString().also {
                require(it.length <= MAX_JSON_CHARS) { "Follow-up snapshot is too large" }
            }
    }

    fun records(snapshotJson: String): JSONArray = JSONObject(snapshotJson).also { root ->
        require(root.getInt("snapshotFormatVersion") == 1 &&
            root.getString("captureState") == "CAPTURED_AT_REVISION")
    }.getJSONArray("records")
}
