package com.v16studio.serviceloop.data

import android.content.ContentValues
import android.database.Cursor
import android.util.Base64
import androidx.room.withTransaction
import com.v16studio.serviceloop.domain.BackupInspection
import com.v16studio.serviceloop.domain.BackupResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

/** Portable authenticated backup format. It contains no passphrase-derived verifier. */
class RecoveryPackage(private val database: ServiceLoopDatabase, private val fileRoot: File) {
    suspend fun create(passphrase: CharArray, allowIncomplete: Boolean): BackupResult {
        require(passphrase.size >= 12) { "Passphrase must contain at least 12 characters" }
        val snapshotAt = System.currentTimeMillis()
        val files = requiredFiles()
        val missing = files.filterNot { File(fileRoot, it.path).isFile }.map { it.path }
        if (missing.isNotEmpty() && !allowIncomplete) error("Complete backup is unavailable because ${missing.size} saved file(s) are missing")
        val databaseJson = database.withTransaction { exportDatabase() }
        val manifest = JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("schemaVersion", 5)
            .put("snapshotAtEpochMillis", snapshotAt)
            .put("datasetId", database.serviceLoopDao().recoveryMetadata()?.datasetId ?: "unknown")
            .put("complete", missing.isEmpty())
            .put("databaseSha256", sha256(databaseJson))
            .put("missingFiles", JSONArray(missing))
        val fileManifest = JSONArray()
        val zipBytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                put(zip, "database.json", databaseJson)
                files.filterNot { it.path in missing }.forEach { expected ->
                    require(safeRelativePath(expected.path)) { "Unsafe owned-file path" }
                    val bytes = File(fileRoot, expected.path).readBytes()
                    require(bytes.size.toLong() == expected.size && sha256(bytes) == expected.hash) { "Saved file integrity check failed: ${expected.path}" }
                    fileManifest.put(JSONObject().put("path", expected.path).put("kind", expected.kind).put("size", bytes.size).put("sha256", expected.hash))
                    put(zip, "files/${expected.path.replace('\\', '/')}", bytes)
                }
                manifest.put("files", fileManifest)
                put(zip, "manifest.json", manifest.toString().toByteArray(Charsets.UTF_8))
            }
        }.toByteArray()
        return BackupResult(protect(zipBytes, passphrase), snapshotAt, missing.isEmpty(), missing)
    }

    fun inspect(protectedBytes: ByteArray, passphrase: CharArray): BackupInspection {
        require(protectedBytes.size <= MAX_PACKAGE_BYTES) { "Backup exceeds the supported size" }
        val plain = unprotect(protectedBytes, passphrase)
        val entries = unzip(plain)
        val manifestBytes = entries["manifest.json"] ?: error("Backup manifest is missing")
        val databaseBytes = entries["database.json"] ?: error("Database snapshot is missing")
        val manifest = JSONObject(manifestBytes.toString(Charsets.UTF_8))
        val version = manifest.getInt("formatVersion")
        require(version <= FORMAT_VERSION) { "This backup needs a newer ServiceLoop version" }
        require(version == FORMAT_VERSION && manifest.getInt("schemaVersion") == 5) { "Unsupported backup format" }
        require(sha256(databaseBytes) == manifest.getString("databaseSha256")) { "Database snapshot integrity check failed" }
        val declaredFiles = manifest.getJSONArray("files")
        val paths = mutableSetOf<String>()
        for (index in 0 until declaredFiles.length()) {
            val item = declaredFiles.getJSONObject(index)
            val path = item.getString("path")
            require(safeRelativePath(path) && paths.add(path)) { "Unsafe or duplicate file entry" }
            val bytes = entries["files/${path.replace('\\', '/')}"] ?: error("Declared file is missing: $path")
            require(bytes.size.toLong() == item.getLong("size") && sha256(bytes) == item.getString("sha256")) { "File integrity check failed: $path" }
        }
        require(entries.keys.all { it == "manifest.json" || it == "database.json" || (it.startsWith("files/") && it.removePrefix("files/") in paths) }) { "Unexpected backup entry" }
        val db = JSONObject(databaseBytes.toString(Charsets.UTF_8))
        validateDatabase(db)
        val missing = manifest.getJSONArray("missingFiles").let { array -> List(array.length()) { array.getString(it) } }
        require(missing.all(::safeRelativePath)) { "Unsafe missing-file declaration" }
        val complete = manifest.getBoolean("complete")
        require(complete == missing.isEmpty()) { "Deceptive completeness metadata" }
        return BackupInspection(version, manifest.getLong("snapshotAtEpochMillis"), manifest.getString("datasetId"), complete, db.getJSONArray("tables").length(), countRecords(db), paths.size, missing, plain)
    }

    suspend fun restore(inspection: BackupInspection) {
        val entries = unzip(inspection.stagedPayload)
        val databaseObject = JSONObject(entries.getValue("database.json").toString(Charsets.UTF_8))
        validateDatabase(databaseObject)
        recoverInterrupted(database, fileRoot)
        val recoveryRoot = File(fileRoot, "recovery")
        val stage = File(recoveryRoot, "restore-candidate")
        val rollback = File(recoveryRoot, "restore-rollback")
        stage.deleteRecursively(); rollback.deleteRecursively()
        require(stage.mkdirs() && rollback.mkdirs()) { "Unable to create recovery staging" }
        val manifest = JSONObject(entries.getValue("manifest.json").toString(Charsets.UTF_8))
        val declared = manifest.getJSONArray("files")
        val paths = List(declared.length()) { declared.getJSONObject(it).getString("path") }
        val journal = File(recoveryRoot, JOURNAL)
        try {
            entries.filterKeys { it.startsWith("files/") }.forEach { (name, bytes) ->
                val relative = name.removePrefix("files/")
                require(safeRelativePath(relative))
                val target = File(stage, relative).canonicalFile
                require(target.path.startsWith(stage.canonicalPath + File.separator))
                target.parentFile?.mkdirs(); target.writeBytes(bytes)
            }
            journal.writeText(JSONObject().put("targetDatasetId", manifest.getString("datasetId")).put("paths", JSONArray(paths)).put("phase", "ADOPTING_FILES").toString())
            paths.forEach { relative ->
                val current = File(fileRoot, relative)
                if (current.isFile) { val saved = File(rollback, relative); saved.parentFile?.mkdirs(); check(current.renameTo(saved) || runCatching { current.copyTo(saved, overwrite = true); current.delete() }.isSuccess) }
                val candidate = File(stage, relative); val target = File(fileRoot, relative); target.parentFile?.mkdirs(); check(candidate.renameTo(target) || runCatching { candidate.copyTo(target, overwrite = true) }.isSuccess)
            }
            journal.writeText(JSONObject(journal.readText()).put("phase", "DB_COMMITTING").toString())
            database.withTransaction { replaceDatabase(databaseObject) }
            journal.writeText(JSONObject(journal.readText()).put("phase", "COMMITTED").toString())
            rollback.deleteRecursively(); stage.deleteRecursively(); journal.delete()
            if (inspection.missingFiles.isEmpty()) recoveryRoot.delete()
        } catch (failure: Exception) {
            recoverInterrupted(database, fileRoot)
            throw failure
        } finally {
            if (!journal.exists()) stage.deleteRecursively()
        }
    }

    private data class RequiredFile(val path: String, val size: Long, val hash: String, val kind: String)
    private suspend fun requiredFiles(): List<RequiredFile> {
        val dao = database.serviceLoopDao()
        val attachments = dao.allAttachments().map { RequiredFile(it.storedRelativePath, it.byteSize, it.sha256, "ATTACHMENT") }
        val reports = dao.allReportRenditions().filter { it.status == "READY" && it.sha256 != null }.map { RequiredFile(it.relativePath, it.byteSize ?: 0, it.sha256!!, "REPORT") }
        return (attachments + reports).distinctBy { it.path }
    }

    private fun exportDatabase(): ByteArray {
        val db = database.openHelper.writableDatabase
        val tables = JSONArray()
        TABLE_ORDER.forEach { table ->
            val rows = JSONArray()
            db.query("SELECT * FROM `$table`").use { cursor -> while (cursor.moveToNext()) rows.put(cursorRow(cursor)) }
            tables.put(JSONObject().put("name", table).put("rows", rows))
        }
        return JSONObject().put("schemaVersion", 5).put("tables", tables).toString().toByteArray(Charsets.UTF_8)
    }

    private fun cursorRow(cursor: Cursor): JSONObject = JSONObject().also { row ->
        for (index in 0 until cursor.columnCount) {
            val value = when (cursor.getType(index)) {
                Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                Cursor.FIELD_TYPE_BLOB -> JSONObject().put("blob", Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP))
                else -> cursor.getString(index)
            }
            row.put(cursor.getColumnName(index), value)
        }
    }

    private fun validateDatabase(root: JSONObject) {
        require(root.getInt("schemaVersion") == 5)
        val tables = root.getJSONArray("tables")
        require(tables.length() == TABLE_ORDER.size)
        val names = mutableSetOf<String>()
        for (index in 0 until tables.length()) {
            val table = tables.getJSONObject(index)
            require(table.getString("name") == TABLE_ORDER[index] && names.add(TABLE_ORDER[index])) { "Unexpected database table" }
            require(table.getJSONArray("rows").length() <= MAX_ROWS_PER_TABLE) { "Backup contains too many records" }
        }
        fun ids(table: String, column: String = "id") = tableRows(root, table).map { it.getString(column) }.toSet()
        val customers = ids("customers"); val sites = ids("sites"); val equipment = ids("equipment"); val plans = ids("service_plans")
        require(tableRows(root, "sites").all { it.getString("customerId") in customers })
        require(tableRows(root, "equipment").all { it.getString("siteId") in sites })
        require(tableRows(root, "service_plans").all { it.getString("equipmentId") in equipment })
        require(tableRows(root, "service_obligations").all { it.getString("planId") in plans })
        require(tableRows(root, "recovery_metadata").size == 1)
    }

    private fun replaceDatabase(root: JSONObject) {
        val db = database.openHelper.writableDatabase
        TABLE_ORDER.asReversed().forEach { db.execSQL("DELETE FROM `$it`") }
        TABLE_ORDER.forEach { table ->
            tableRows(root, table).forEach { row ->
                val values = ContentValues()
                row.keys().forEach { key ->
                    val value = row.get(key)
                    when (value) {
                        JSONObject.NULL -> values.putNull(key)
                        is Int -> values.put(key, value)
                        is Long -> values.put(key, value)
                        is Double -> values.put(key, value)
                        is String -> values.put(key, value)
                        is JSONObject -> values.put(key, Base64.decode(value.getString("blob"), Base64.NO_WRAP))
                        else -> error("Unsupported snapshot value")
                    }
                }
                require(db.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, values) != -1L) { "Could not restore $table" }
            }
        }
        db.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) { "Restored references are invalid" } }
    }

    private fun tableRows(root: JSONObject, name: String): List<JSONObject> {
        val tables = root.getJSONArray("tables")
        for (index in 0 until tables.length()) {
            val table = tables.getJSONObject(index)
            if (table.getString("name") == name) return table.getJSONArray("rows").let { rows -> List(rows.length()) { rows.getJSONObject(it) } }
        }
        error("Missing table $name")
    }
    private fun countRecords(root: JSONObject): Int = TABLE_ORDER.sumOf { tableRows(root, it).size }

    private fun protect(plain: ByteArray, passphrase: CharArray): ByteArray {
        val random = SecureRandom(); val salt = ByteArray(16).also(random::nextBytes); val nonce = ByteArray(12).also(random::nextBytes)
        val key = derive(passphrase, salt, ITERATIONS)
        val header = ByteBuffer.allocate(4 + 4 + 4 + 16 + 12).order(ByteOrder.BIG_ENDIAN).put(MAGIC).putInt(FORMAT_VERSION).putInt(ITERATIONS).put(salt).put(nonce).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce)); updateAAD(header) }
        return header + cipher.doFinal(plain)
    }

    private fun unprotect(bytes: ByteArray, passphrase: CharArray): ByteArray {
        require(bytes.size > 52) { "Not a ServiceLoop backup" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN); val magic = ByteArray(4).also(buffer::get)
        require(magic.contentEquals(MAGIC)) { "Not a ServiceLoop backup" }
        val version = buffer.int; require(version <= FORMAT_VERSION) { "This backup needs a newer ServiceLoop version" }; require(version == FORMAT_VERSION)
        val iterations = buffer.int; require(iterations in 100_000..2_000_000) { "Unsupported key-derivation parameters" }
        val salt = ByteArray(16).also(buffer::get); val nonce = ByteArray(12).also(buffer::get)
        val header = bytes.copyOfRange(0, buffer.position()); val encrypted = bytes.copyOfRange(buffer.position(), bytes.size)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.DECRYPT_MODE, derive(passphrase, salt, iterations), GCMParameterSpec(128, nonce)); updateAAD(header); doFinal(encrypted) }
        } catch (_: AEADBadTagException) { error("Wrong passphrase or damaged backup") }
    }

    private fun derive(passphrase: CharArray, salt: ByteArray, iterations: Int) = SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(passphrase, salt, iterations, 256)).encoded, "AES")
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>(); var expanded = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && safeArchiveEntry(entry.name) && !result.containsKey(entry.name)) { "Unsafe or duplicate backup entry" }
                val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val count = zip.read(buffer); if (count < 0) break; expanded += count; require(expanded <= MAX_EXPANDED_BYTES); output.write(buffer, 0, count) }
                result[entry.name] = output.toByteArray(); require(result.size <= MAX_ENTRIES)
            }
        }
        return result
    }
    private fun put(zip: ZipOutputStream, name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
    private fun safeArchiveEntry(path: String) = !path.startsWith('/') && !path.startsWith('\\') && !path.contains(':') && path.split('/', '\\').none { it.isBlank() || it == "." || it == ".." }
    private fun safeRelativePath(path: String) = safeArchiveEntry(path.replace('\\', '/')) && !File(path).isAbsolute
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val JOURNAL = "restore-journal.json"
        const val FORMAT_VERSION = 1
        const val ITERATIONS = 310_000
        const val MAX_PACKAGE_BYTES = 512 * 1024 * 1024
        const val MAX_EXPANDED_BYTES = 1024L * 1024 * 1024
        const val MAX_ENTRIES = 5000
        const val MAX_ROWS_PER_TABLE = 1_000_000
        val MAGIC = byteArrayOf('S'.code.toByte(), 'L'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
        val TABLE_ORDER = listOf(
            "customers", "sites", "equipment", "service_plans", "service_obligations",
            "template_snapshots", "checklist_item_snapshots", "working_visits", "work_items",
            "work_item_public_drafts", "work_item_private_drafts", "working_responses", "attachments",
            "follow_ups", "business_profiles", "final_records", "final_record_revisions", "final_work_items",
            "final_checklist_items", "report_renditions", "reusable_templates", "reusable_template_revisions",
            "reusable_template_items", "contact_notes", "follow_up_events", "part_entries", "visit_claims",
            "final_part_entries", "final_photo_entries", "plan_schedule_changes", "visit_schedule_events",
            "correction_drafts", "correction_work_items", "change_entries", "equipment_moves", "recovery_metadata",
        )

        /** Resolves a crashed cross-filesystem adoption to the dataset whose id durably won. */
        fun recoverInterrupted(database: ServiceLoopDatabase, fileRoot: File) {
            val recoveryRoot = File(fileRoot, "recovery"); val journal = File(recoveryRoot, JOURNAL)
            if (!journal.isFile) return
            val state = runCatching { JSONObject(journal.readText()) }.getOrElse { return }
            val targetDatasetId = state.optString("targetDatasetId")
            val paths = state.optJSONArray("paths")?.let { array -> List(array.length()) { array.getString(it) } }.orEmpty().filter { safeRelativePathStatic(it) }
            val durableDatasetId = runCatching { database.openHelper.writableDatabase.query("SELECT datasetId FROM recovery_metadata WHERE id='primary'").use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()
            val candidateWon = durableDatasetId == targetDatasetId && state.optString("phase") in setOf("DB_COMMITTING", "COMMITTED")
            if (!candidateWon) paths.forEach { relative ->
                File(fileRoot, relative).delete()
                val saved = File(recoveryRoot, "restore-rollback/$relative")
                if (saved.isFile) { val target = File(fileRoot, relative); target.parentFile?.mkdirs(); saved.renameTo(target) || runCatching { saved.copyTo(target, overwrite = true) }.isSuccess }
            }
            File(recoveryRoot, "restore-candidate").deleteRecursively(); File(recoveryRoot, "restore-rollback").deleteRecursively(); journal.delete(); recoveryRoot.delete()
        }
        private fun safeRelativePathStatic(path: String) = !path.startsWith('/') && !path.startsWith('\\') && !path.contains(':') && path.replace('\\', '/').split('/').none { it.isBlank() || it == "." || it == ".." } && !File(path).isAbsolute
    }
}
