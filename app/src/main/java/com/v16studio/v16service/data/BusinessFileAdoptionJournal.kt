package com.v16studio.v16service.data

import android.util.AtomicFile
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Narrow crash record for newly created WORK_RESULT and DATA_TRANSFER evidence bytes. */
internal class BusinessFileAdoptionJournal private constructor(
    private val database: V16ServiceDatabase,
    private val root: File,
    private val entries: Map<String, Entry>,
) {
    private data class Entry(val target: String, val stage: String, val sha256: String, val size: Long)

    fun adopt(relative: String, bytes: ByteArray) {
        val entry = entries[relative] ?: error("Unplanned business-file adoption")
        require(entry.size == bytes.size.toLong() && entry.sha256 == WorkResultPackageCodec.sha256(bytes)) {
            "Business-file adoption bytes changed"
        }
        val target = OwnedBusinessFiles.resolve(root, entry.target)
        val stage = OwnedBusinessFiles.resolve(root, entry.stage)
        require(!target.exists() && !stage.exists()) { "Business-file adoption path is already in use" }
        require(stage.parentFile!!.isDirectory || stage.parentFile!!.mkdirs())
        try {
            stage.outputStream().use { stream -> stream.write(bytes); stream.fd.sync() }
            require(stage.length() == entry.size && WorkResultPackageCodec.sha256(stage.readBytes()) == entry.sha256) {
                "Business-file staging failed verification"
            }
            Files.move(stage.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            // The durable journal owns a stage left by a process death. An in-process
            // write failure can remove its own stage immediately.
            if (stage.exists()) check(stage.delete())
        }
    }

    fun finish() {
        val file = journalFile(root)
        AtomicFile(file).delete()
        check(!file.exists() && !File(file.path + ".new").exists()) { "Business-file journal cleanup failed" }
    }

    fun reconcileAfterFailure() {
        check(recoverInterrupted(database, root)) { "Business-file adoption needs recovery before further work" }
    }

    companion object {
        private const val JOURNAL = "business-file-adoption.json"

        fun begin(database: V16ServiceDatabase, root: File, planned: Map<String, ByteArray>): BusinessFileAdoptionJournal {
            check(recoverInterrupted(database, root)) { "An earlier business-file adoption needs recovery" }
            val id = UUID.randomUUID().toString()
            val entries = planned.entries.mapIndexed { index, (relative, bytes) ->
                val target = OwnedBusinessFiles.resolve(root, relative)
                require(relative.startsWith("remote-results/") || relative.startsWith("transferred-evidence/")) {
                    "Unsupported business-file adoption root"
                }
                require(!target.exists()) { "Business-file adoption target already exists" }
                val stage = "${relative.substringBeforeLast('/')}/.$id-$index.tmp"
                OwnedBusinessFiles.resolve(root, stage)
                Entry(relative, stage, WorkResultPackageCodec.sha256(bytes), bytes.size.toLong())
            }
            require(entries.map { it.target }.distinct().size == entries.size) { "Duplicate business-file adoption target" }
            val metadata = adoptionIdentity(database)
            val journal = JSONObject().put("version", 1).put("operationId", id)
                .put("datasetId", metadata.first).put("adoptionToken", metadata.second)
                .put("entries", JSONArray().also { array -> entries.forEach { entry -> array.put(JSONObject()
                    .put("target", entry.target).put("stage", entry.stage)
                    .put("sha256", entry.sha256).put("size", entry.size)) } })
            val file = journalFile(root)
            require(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
            writeAtomic(file, journal.toString().toByteArray(Charsets.UTF_8))
            return BusinessFileAdoptionJournal(database, root, entries.associateBy { it.target })
        }

        /** Called at database open before normal startup work, and under the file mutex on retry. */
        fun recoverInterrupted(database: V16ServiceDatabase, root: File): Boolean {
            val file = journalFile(root)
            if (!file.exists() && !File(file.path + ".new").exists()) return true
            val recovered = runCatching {
                val journal = JSONObject(AtomicFile(file).openRead().use { it.readBytes().toString(Charsets.UTF_8) })
                require(journal.getInt("version") == 1 && journal.getString("operationId").isNotBlank())
                require(adoptionIdentity(database) == (journal.getString("datasetId") to journal.getString("adoptionToken"))) {
                    "Business-file adoption belongs to another dataset generation"
                }
                val rows = journal.getJSONArray("entries")
                val targets = mutableSetOf<String>()
                val stages = mutableSetOf<String>()
                val entries = (0 until rows.length()).map { index ->
                    val row = rows.getJSONObject(index)
                    val entry = Entry(row.getString("target"), row.getString("stage"), row.getString("sha256"), row.getLong("size"))
                    require(entry.target.startsWith("remote-results/") || entry.target.startsWith("transferred-evidence/"))
                    require(targets.add(entry.target) && stages.add(entry.stage) && entry.size in 1..WorkResultPackageCodec.MAX_PHOTO_BYTES.toLong() &&
                        entry.sha256.matches(Regex("[a-f0-9]{64}")))
                    val expectedStage = "${entry.target.substringBeforeLast('/')}/.${journal.getString("operationId")}-$index.tmp"
                    require(entry.stage == expectedStage)
                    OwnedBusinessFiles.resolve(root, entry.target)
                    OwnedBusinessFiles.resolve(root, entry.stage)
                    entry
                }
                // Validate every byte before unlinking anything. A damaged or ambiguous
                // journal stays in place and restricts normal startup.
                entries.forEach { entry ->
                    listOf(entry.target, entry.stage).forEach { relative ->
                        val candidate = OwnedBusinessFiles.resolve(root, relative)
                        if (candidate.exists()) require(candidate.isFile && candidate.length() == entry.size &&
                            WorkResultPackageCodec.sha256(candidate.readBytes()) == entry.sha256) {
                            "Business-file adoption content changed: $relative"
                        }
                    }
                    if (referenceCount(database, entry.target) > 0) require(OwnedBusinessFiles.resolve(root, entry.target).isFile) {
                        "Committed business evidence is missing"
                    }
                }
                entries.forEach { entry ->
                    val stage = OwnedBusinessFiles.resolve(root, entry.stage)
                    if (stage.exists()) check(stage.delete())
                    val target = OwnedBusinessFiles.resolve(root, entry.target)
                    if (target.exists() && referenceCount(database, entry.target) == 0) check(target.delete())
                }
                AtomicFile(file).delete()
                check(!file.exists() && !File(file.path + ".new").exists())
            }.isSuccess
            if (!recovered) runCatching { database.openHelper.writableDatabase.execSQL(
                "UPDATE recovery_metadata SET restrictedRecoveryState=1 WHERE id='primary'") }
            return recovered
        }

        private fun adoptionIdentity(database: V16ServiceDatabase): Pair<String, String> =
            database.openHelper.writableDatabase.query(
                "SELECT datasetId, adoptionToken FROM recovery_metadata WHERE id='primary'").use { cursor ->
                if (!cursor.moveToFirst()) "" to "" else cursor.getString(0) to (cursor.getString(1) ?: "")
            }

        private fun referenceCount(database: V16ServiceDatabase, path: String): Int {
            val table = if (path.startsWith("remote-results/")) "remote_result_photos" else "transferred_evidence"
            return database.openHelper.writableDatabase.query(
                "SELECT COUNT(*) FROM $table WHERE relativePath=?", arrayOf(path)).use { cursor ->
                check(cursor.moveToFirst()); cursor.getInt(0)
            }
        }

        private fun journalFile(root: File) = File(root, "recovery/$JOURNAL")

        private fun writeAtomic(file: File, bytes: ByteArray) {
            val atomic = AtomicFile(file)
            val stream = atomic.startWrite()
            try {
                stream.write(bytes)
                stream.fd.sync()
                atomic.finishWrite(stream)
            } catch (failure: Throwable) {
                atomic.failWrite(stream)
                throw failure
            }
        }
    }
}
