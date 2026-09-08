package com.v16studio.serviceloop.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
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
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.sync.withLock

/** Portable authenticated backup format. It contains no passphrase-derived verifier. */
class RecoveryPackage(
    private val database: ServiceLoopDatabase,
    private val fileRoot: File,
    private val failureInjector: (FailurePoint) -> Unit = {},
) {
    enum class FailurePoint { BEFORE_FILE_ADOPTION, DURING_FILE_ADOPTION, BEFORE_DB_TRANSACTION, DURING_DB_TRANSACTION, AFTER_DB_COMMIT, AFTER_COMMITTED_JOURNAL, DURING_ERASE_FILES }
    enum class RecoveryResult { NONE, CANDIDATE_COMMITTED, ORIGINAL_RESTORED, RESTRICTED }

    suspend fun create(passphrase: CharArray, allowIncomplete: Boolean): BackupResult = BusinessFileCoordinator.mutex.withLock { createUnlocked(passphrase, allowIncomplete) }

    private suspend fun createUnlocked(passphrase: CharArray, allowIncomplete: Boolean): BackupResult {
        require(passphrase.size >= 12) { "Passphrase must contain at least 12 characters" }
        val snapshotAt = System.currentTimeMillis()
        val databaseObject = database.withTransaction {
            database.openHelper.writableDatabase.execSQL("INSERT OR IGNORE INTO technician_identity(id,technicianId,displayName,createdAtEpochMillis,modifiedAtEpochMillis) SELECT 'primary', lower(hex(randomblob(16))), COALESCE(NULLIF(TRIM((SELECT technicianName FROM business_profiles WHERE id='primary')),''), 'Technician'), CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)")
            JSONObject(exportDatabase().toString(Charsets.UTF_8))
        }
        val files = requiredFiles(databaseObject)
        val missing = files.filterNot { File(fileRoot, it.path).isFile }.map { it.path }
        if (missing.isNotEmpty() && !allowIncomplete) error("Complete backup is unavailable because ${missing.size} saved file(s) are missing")
        normalizeMissingAvailability(databaseObject, missing.toSet())
        val databaseJson = databaseObject.toString().toByteArray(Charsets.UTF_8)
        val manifest = JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("snapshotAtEpochMillis", snapshotAt)
            .put("datasetId", recoveryMetadata(databaseObject).getString("datasetId"))
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
        require(version == FORMAT_VERSION && manifest.getInt("schemaVersion") == SCHEMA_VERSION) { "Unsupported backup format" }
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
        require(missing.size == missing.toSet().size && missing.all(::safeRelativePath) && missing.none { it in paths }) { "Unsafe or conflicting missing-file declaration" }
        val complete = manifest.getBoolean("complete")
        require(complete == missing.isEmpty()) { "Deceptive completeness metadata" }
        crossValidateFiles(db, declaredFiles, missing)
        return BackupInspection(version, manifest.getLong("snapshotAtEpochMillis"), manifest.getString("datasetId"), complete, db.getJSONArray("tables").length(), countRecords(db), paths.size, missing, plain)
    }

    suspend fun restore(inspection: BackupInspection) = BusinessFileCoordinator.mutex.withLock { restoreUnlocked(inspection) }

    private suspend fun restoreUnlocked(inspection: BackupInspection) {
        val entries = unzip(inspection.stagedPayload)
        val databaseObject = JSONObject(entries.getValue("database.json").toString(Charsets.UTF_8))
        validateDatabase(databaseObject)
        val recoveryRoot = File(fileRoot, "recovery")
        var restrictedQuarantine: File? = null
        if (recoverInterrupted(database, fileRoot) == RecoveryResult.RESTRICTED) {
            restrictedQuarantine = File(fileRoot, "recovery-restricted-${UUID.randomUUID()}")
            check(recoveryRoot.renameTo(restrictedQuarantine)) { "Unable to isolate the damaged recovery journal" }
        }
        val stage = File(recoveryRoot, "restore-candidate")
        val rollback = File(recoveryRoot, "restore-rollback")
        check(!journalFile(recoveryRoot).exists()) { "Another recovery operation is unfinished" }
        stage.deleteRecursively(); rollback.deleteRecursively()
        require(stage.mkdirs() && rollback.mkdirs()) { "Unable to create recovery staging" }
        val manifest = JSONObject(entries.getValue("manifest.json").toString(Charsets.UTF_8))
        val declared = manifest.getJSONArray("files")
        val paths = List(declared.length()) { declared.getJSONObject(it).getString("path") }
        val touchedPaths = (ownedBusinessFiles() + paths).distinct().sorted()
        val journal = journalFile(recoveryRoot)
        val adoptionToken = UUID.randomUUID().toString()
        setAdoptionToken(databaseObject, adoptionToken)
        try {
            entries.filterKeys { it.startsWith("files/") }.forEach { (name, bytes) ->
                val relative = name.removePrefix("files/")
                require(safeRelativePath(relative))
                val target = File(stage, relative).canonicalFile
                require(target.path.startsWith(stage.canonicalPath + File.separator))
                target.parentFile?.mkdirs(); target.writeBytes(bytes)
            }
            val initialJournal = JSONObject().put("operation", "RESTORE").put("adoptionToken", adoptionToken).put("paths", JSONArray(touchedPaths)).put("candidatePaths", JSONArray(paths)).put("adoptedPaths", JSONArray()).put("phase", "PREPARED")
            restrictedQuarantine?.let { initialJournal.put("restrictedQuarantine", it.name) }
            writeJournal(journal, initialJournal)
            failureInjector(FailurePoint.BEFORE_FILE_ADOPTION)
            writeJournal(journal, JSONObject(journal.readText()).put("phase", "ADOPTING_FILES"))
            touchedPaths.forEachIndexed { index, relative ->
                val current = File(fileRoot, relative)
                writeJournal(journal, JSONObject(journal.readText()).put("processingPath", relative).put("processingOriginalExisted", current.isFile))
                if (current.isFile) { val saved = File(rollback, relative); saved.parentFile?.mkdirs(); check(current.renameTo(saved) || runCatching { current.copyTo(saved, overwrite = true); current.delete() }.isSuccess) }
                val candidate = File(stage, relative)
                if (candidate.isFile) { val target = File(fileRoot, relative); target.parentFile?.mkdirs(); check(candidate.renameTo(target) || runCatching { candidate.copyTo(target, overwrite = true); candidate.delete() }.getOrDefault(false)) }
                val state = JSONObject(journal.readText()); state.getJSONArray("adoptedPaths").put(relative); state.remove("processingPath"); state.remove("processingOriginalExisted"); writeJournal(journal, state)
                if (index == 0) failureInjector(FailurePoint.DURING_FILE_ADOPTION)
            }
            writeJournal(journal, JSONObject(journal.readText()).put("phase", "DB_COMMITTING"))
            failureInjector(FailurePoint.BEFORE_DB_TRANSACTION)
            database.withTransaction { replaceDatabase(databaseObject); failureInjector(FailurePoint.DURING_DB_TRANSACTION) }
            failureInjector(FailurePoint.AFTER_DB_COMMIT)
            writeJournal(journal, JSONObject(journal.readText()).put("phase", "COMMITTED"))
            failureInjector(FailurePoint.AFTER_COMMITTED_JOURNAL)
            rollback.deleteRecursively(); stage.deleteRecursively()
            restrictedQuarantine?.let { check(it.deleteRecursively()) { "Damaged recovery quarantine could not be removed" } }
            journal.delete()
            if (inspection.missingFiles.isEmpty()) recoveryRoot.delete()
        } catch (failure: Exception) {
            recoverInterrupted(database, fileRoot)
            throw failure
        } finally {
            if (!journal.exists()) stage.deleteRecursively()
        }
    }

    private data class RequiredFile(val path: String, val size: Long, val hash: String, val kind: String)
    private fun requiredFiles(root: JSONObject): List<RequiredFile> {
        val attachments = tableRows(root, "attachments").map { RequiredFile(it.getString("storedRelativePath"), it.getLong("byteSize"), it.getString("sha256"), "ATTACHMENT") }
        val reports = tableRows(root, "report_renditions").filter { it.getString("status") in setOf("READY", "MISSING") && !it.isNull("sha256") }.map { RequiredFile(it.getString("relativePath"), it.optLong("byteSize"), it.getString("sha256"), "REPORT") }
        val all = attachments + reports
        require(all.map { it.path }.size == all.map { it.path }.toSet().size) { "Two database file references use the same path" }
        return all
    }

    private fun exportDatabase(): ByteArray {
        val db = database.openHelper.writableDatabase
        val tables = JSONArray()
        TABLE_ORDER.forEach { table ->
            val rows = JSONArray()
            db.query("SELECT * FROM `$table`").use { cursor -> while (cursor.moveToNext()) rows.put(cursorRow(cursor)) }
            tables.put(JSONObject().put("name", table).put("rows", rows))
        }
        return JSONObject().put("schemaVersion", SCHEMA_VERSION).put("tables", tables).toString().toByteArray(Charsets.UTF_8)
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
        require(root.getInt("schemaVersion") == SCHEMA_VERSION)
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
        val revisions = tableRows(root, "final_record_revisions").associateBy { it.getString("id") }
        val records = tableRows(root, "final_records").associateBy { it.getString("id") }
        require(records.values.all { record -> revisions[record.getString("currentRevisionId")]?.getString("recordId") == record.getString("id") }) { "A final record points to a revision that does not belong to it" }
        val obligations = tableRows(root, "service_obligations").associateBy { it.getString("id") }
        require(tableRows(root, "service_plans").all { plan ->
            val current = plan.optString("currentObligationId").takeIf { it.isNotBlank() }
            (current == null && plan.getString("state") != "ACTIVE") || (current != null && obligations[current]?.getString("planId") == plan.getString("id") && obligations[current]?.getString("dueDate") == plan.getString("currentDueDate"))
        }) { "A service plan has an invalid current obligation" }
        val templateRevisions = tableRows(root, "reusable_template_revisions").associateBy { it.getString("id") }
        require(tableRows(root, "reusable_templates").all { template -> templateRevisions[template.getString("currentRevisionId")]?.getString("templateId") == template.getString("id") }) { "A reusable template has an invalid current revision" }
        require(tableRows(root, "correction_drafts").all { draft -> revisions[draft.getString("baseRevisionId")]?.getString("recordId") == draft.getString("recordId") }) { "A correction base revision does not belong to its record" }
        val recovery = tableRows(root, "recovery_metadata")
        require(recovery.size == 1 && recovery.single().getString("id") == "primary") { "Recovery metadata singleton is invalid" }
        val identity = tableRows(root, "technician_identity")
        require(identity.size == 1 && identity.single().getString("id") == "primary" && identity.single().getString("technicianId").isNotBlank()) { "Technician identity singleton is invalid" }
        val visitIds = ids("working_visits")
        require(tableRows(root, "dispatch_visit_bindings").all { it.getString("localVisitId") in visitIds }) { "Dispatch binding points to a missing Visit" }
        validateAgainstRoomSchema(root)
    }

    /** Replays the generated Room v8 schema into an isolated throwaway database. */
    private fun validateAgainstRoomSchema(root: JSONObject) {
        // The platform temp directory avoids path-length failures while remaining app-private on Android.
        val stagingFile = File.createTempFile("slrv-", ".db")
        var staging: SQLiteDatabase? = null
        try {
            val candidate = SQLiteDatabase.openOrCreateDatabase(stagingFile, null)
            staging = candidate
            database.openHelper.readableDatabase.query("SELECT type,name,sql FROM sqlite_master WHERE sql IS NOT NULL AND type IN ('table','index') AND name NOT LIKE 'sqlite_%' AND name!='android_metadata' ORDER BY CASE type WHEN 'table' THEN 0 ELSE 1 END,name").use { cursor ->
                while (cursor.moveToNext()) candidate.execSQL(cursor.getString(2))
            }
            candidate.setForeignKeyConstraintsEnabled(true)
            TABLE_ORDER.forEach { table ->
                tableRows(root, table).forEach { row ->
                    require(candidate.insert(table, null, contentValues(row)) != -1L) { "Could not validate $table" }
                }
            }
            candidate.rawQuery("PRAGMA foreign_key_check", null).use { require(!it.moveToFirst()) { "Restored references are invalid" } }
        } catch (failure: Exception) {
            throw IllegalArgumentException("Backup database relationships are invalid", failure)
        } finally {
            staging?.close()
            listOf(stagingFile, File(stagingFile.path + "-wal"), File(stagingFile.path + "-shm"), File(stagingFile.path + "-journal")).forEach { it.delete() }
        }
    }

    private fun replaceDatabase(root: JSONObject) {
        val db = database.openHelper.writableDatabase
        TABLE_ORDER.asReversed().forEach { db.execSQL("DELETE FROM `$it`") }
        TABLE_ORDER.forEach { table ->
            tableRows(root, table).forEach { row ->
                require(db.insert(table, SQLiteDatabase.CONFLICT_ABORT, contentValues(row)) != -1L) { "Could not restore $table" }
            }
        }
        db.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) { "Restored references are invalid" } }
    }

    private fun contentValues(row: JSONObject) = ContentValues().also { values ->
        row.keys().forEach { key ->
            when (val value = row.get(key)) {
                JSONObject.NULL -> values.putNull(key)
                is Int -> values.put(key, value)
                is Long -> values.put(key, value)
                is Double -> values.put(key, value)
                is String -> values.put(key, value)
                is JSONObject -> values.put(key, Base64.decode(value.getString("blob"), Base64.NO_WRAP))
                else -> error("Unsupported snapshot value")
            }
        }
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

    /** Recoverable erase: file adoption and the empty database share one durable commit identity. */
    suspend fun eraseDatabaseAndOwnedFiles(newDatasetId: String) = BusinessFileCoordinator.mutex.withLock { eraseUnlocked(newDatasetId) }

    private suspend fun eraseUnlocked(newDatasetId: String) {
        check(recoverInterrupted(database, fileRoot) != RecoveryResult.RESTRICTED) { "Recovery is restricted; erase cannot continue" }
        val recoveryRoot = File(fileRoot, "recovery")
        val rollback = File(recoveryRoot, "restore-rollback")
        val journal = journalFile(recoveryRoot)
        check(!journal.exists()) { "Another recovery operation is unfinished" }
        rollback.deleteRecursively()
        require(rollback.mkdirs()) { "Unable to create erase rollback staging" }
        val paths = ownedBusinessFiles().sorted()
        val token = UUID.randomUUID().toString()
        try {
            writeJournal(journal, JSONObject().put("operation", "ERASE").put("adoptionToken", token).put("paths", JSONArray(paths)).put("candidatePaths", JSONArray()).put("adoptedPaths", JSONArray()).put("phase", "ADOPTING_FILES"))
            paths.forEachIndexed { index, relative ->
                val current = File(fileRoot, relative)
                writeJournal(journal, JSONObject(journal.readText()).put("processingPath", relative).put("processingOriginalExisted", current.isFile))
                if (current.isFile) {
                    val saved = File(rollback, relative)
                    saved.parentFile?.mkdirs()
                    check(current.renameTo(saved) || runCatching { current.copyTo(saved, overwrite = true); current.delete() }.getOrDefault(false))
                }
                val state = JSONObject(journal.readText()); state.getJSONArray("adoptedPaths").put(relative); state.remove("processingPath"); state.remove("processingOriginalExisted"); writeJournal(journal, state)
                if (index == 0) failureInjector(FailurePoint.DURING_ERASE_FILES)
            }
            writeJournal(journal, JSONObject(journal.readText()).put("phase", "DB_COMMITTING"))
            database.withTransaction {
                val db = database.openHelper.writableDatabase
                TABLE_ORDER.asReversed().filterNot { it == "recovery_metadata" }.forEach { db.execSQL("DELETE FROM `$it`") }
                database.serviceLoopDao().upsertRecoveryMetadata(
                    RecoveryMetadataEntity(
                        datasetId = newDatasetId,
                        firstBusinessWriteAtEpochMillis = null,
                        lastBusinessWriteAtEpochMillis = null,
                        lastBackupAttemptAtEpochMillis = null,
                        lastVerifiedFullBackupAtEpochMillis = null,
                        lastVerifiedSnapshotAtEpochMillis = null,
                        lastVerifiedDestination = null,
                        lastVerifiedSize = null,
                        adoptionToken = token,
                    ),
                )
                failureInjector(FailurePoint.DURING_DB_TRANSACTION)
            }
            failureInjector(FailurePoint.AFTER_DB_COMMIT)
            writeJournal(journal, JSONObject(journal.readText()).put("phase", "COMMITTED"))
            failureInjector(FailurePoint.AFTER_COMMITTED_JOURNAL)
            check(rollback.deleteRecursively()) { "Private file cleanup did not complete" }
            journal.delete()
            ownedRootDirectories().forEach { deleteEmptyTree(it) }
            recoveryRoot.delete()
        } catch (failure: Exception) {
            recoverInterrupted(database, fileRoot)
            throw failure
        }
    }

    private fun normalizeMissingAvailability(root: JSONObject, missing: Set<String>) {
        tableRows(root, "attachments").forEach { row ->
            if (row.getString("storedRelativePath") in missing) row.put("availability", "MISSING")
        }
        tableRows(root, "report_renditions").forEach { row ->
            if (row.getString("relativePath") in missing && row.getString("status") == "READY") {
                row.put("status", "MISSING").put("failureMessage", "File declared missing in incomplete recovery copy")
            }
        }
        recoveryMetadata(root).put("restoredFromIncompleteCopy", if (missing.isEmpty()) 0 else 1)
    }

    private fun crossValidateFiles(root: JSONObject, manifest: JSONArray, missing: List<String>) {
        val expected = requiredFiles(root).associateBy { it.path }
        val declared = List(manifest.length()) { manifest.getJSONObject(it) }.associateBy { it.getString("path") }
        require(expected.keys == declared.keys + missing.toSet()) { "Database file references do not match the backup manifest" }
        declared.forEach { (path, item) ->
            val reference = expected.getValue(path)
            require(item.getString("kind") == reference.kind && item.getLong("size") == reference.size && item.getString("sha256") == reference.hash) { "Manifest metadata conflicts with the database reference: $path" }
        }
        tableRows(root, "attachments").forEach { row ->
            val absent = row.getString("storedRelativePath") in missing
            require((row.getString("availability") == "MISSING") == absent) { "Invalid attachment availability metadata" }
        }
        tableRows(root, "report_renditions").forEach { row ->
            val absent = row.getString("relativePath") in missing
            if (absent) require(row.getString("status") == "MISSING") { "Invalid report availability metadata" }
        }
    }

    private fun recoveryMetadata(root: JSONObject) = tableRows(root, "recovery_metadata").single()
    private fun setAdoptionToken(root: JSONObject, token: String) { recoveryMetadata(root).put("adoptionToken", token).put("restrictedRecoveryState", 0) }
    private fun ownedRootDirectories() = BUSINESS_ROOTS.map { File(fileRoot, it) }
    private fun ownedBusinessFiles(): List<String> = BUSINESS_ROOTS.flatMap { rootName ->
        val root = File(fileRoot, rootName)
        if (!root.isDirectory) emptyList() else root.walkTopDown().filter { it.isFile }.map { it.relativeTo(fileRoot).invariantSeparatorsPath }.toList()
    }
    private fun writeJournal(file: File, value: JSONObject) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "$JOURNAL.tmp")
        temporary.writeText(value.toString())
        check(temporary.renameTo(file) || runCatching { temporary.copyTo(file, overwrite = true); temporary.delete() }.getOrDefault(false))
    }
    private fun journalFile(root: File) = File(root, JOURNAL)
    private fun deleteEmptyTree(root: File) { if (root.isDirectory) root.walkBottomUp().filter { it.isDirectory && it.list()?.isEmpty() == true }.forEach { it.delete() } }

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
        private const val SCHEMA_VERSION = 8
        private val BUSINESS_ROOTS = listOf("attachments", "reports")
        const val FORMAT_VERSION = 2
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
            "correction_drafts", "correction_work_items", "change_entries", "equipment_moves",
            "technician_identity", "dispatch_technicians", "dispatch_teams", "dispatch_team_members",
            "dispatch_outbox_visits", "dispatch_outbox_visit_teams", "dispatch_outbox_items", "dispatch_outbox_item_assignees",
            "dispatch_visit_bindings", "dispatch_item_bindings", "final_dispatch_visits", "final_dispatch_items",
            "recovery_metadata",
        )

        /** Resolves a crashed cross-filesystem adoption using the token committed with the database transaction. */
        fun recoverInterrupted(database: ServiceLoopDatabase, fileRoot: File): RecoveryResult {
            val recoveryRoot = File(fileRoot, "recovery"); val journal = File(recoveryRoot, JOURNAL)
            if (!journal.isFile) return RecoveryResult.NONE
            val state = runCatching { JSONObject(journal.readText()) }.getOrElse {
                markRestricted(database)
                return RecoveryResult.RESTRICTED
            }
            val parsed = runCatching {
                val operation = state.getString("operation")
                require(operation in setOf("RESTORE", "ERASE"))
                val token = state.getString("adoptionToken"); require(token.isNotBlank())
                val paths = state.getJSONArray("paths").let { array -> List(array.length()) { array.getString(it) } }
                val candidatePaths = state.getJSONArray("candidatePaths").let { array -> List(array.length()) { array.getString(it) } }
                require(paths.size == paths.toSet().size && paths.all(::safeRelativePathStatic))
                require(candidatePaths.size == candidatePaths.toSet().size && candidatePaths.all(::safeRelativePathStatic) && candidatePaths.all { it in paths })
                Triple(token, paths, candidatePaths)
            }.getOrElse {
                markRestricted(database)
                return RecoveryResult.RESTRICTED
            }
            val (token, paths) = parsed
            val adoptedPaths = runCatching { state.getJSONArray("adoptedPaths").let { array -> List(array.length()) { array.getString(it) } } }.getOrElse { markRestricted(database); return RecoveryResult.RESTRICTED }
            if (adoptedPaths.size != adoptedPaths.toSet().size || adoptedPaths.any { it !in paths }) { markRestricted(database); return RecoveryResult.RESTRICTED }
            val processingPath = state.optString("processingPath").takeIf { it.isNotBlank() }
            if (processingPath != null && processingPath !in paths) { markRestricted(database); return RecoveryResult.RESTRICTED }
            val quarantineName = state.optString("restrictedQuarantine").takeIf { it.isNotBlank() }
            if (quarantineName != null && (!quarantineName.startsWith("recovery-restricted-") || quarantineName.contains('/') || quarantineName.contains('\\'))) { markRestricted(database); return RecoveryResult.RESTRICTED }
            val quarantine = quarantineName?.let { File(fileRoot, it) }
            val rollbackPaths = (adoptedPaths + listOfNotNull(processingPath)).distinct()
            val durableToken = runCatching { database.openHelper.writableDatabase.query("SELECT adoptionToken FROM recovery_metadata WHERE id='primary'").use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null } }.getOrNull()
            val candidateWon = durableToken == token && state.optString("phase") in setOf("DB_COMMITTING", "COMMITTED")
            val resolved = runCatching {
                if (!candidateWon) rollbackPaths.forEach { relative ->
                    val target = File(fileRoot, relative)
                    val saved = File(recoveryRoot, "restore-rollback/$relative")
                    if (saved.isFile) {
                        if (target.exists()) check(target.delete())
                        target.parentFile?.mkdirs()
                        check(saved.renameTo(target) || runCatching { saved.copyTo(target, overwrite = true); saved.delete() }.getOrDefault(false))
                    } else if (relative in adoptedPaths || (relative == processingPath && !state.optBoolean("processingOriginalExisted", false))) {
                        if (target.exists()) check(target.delete())
                    }
                }
                check(File(recoveryRoot, "restore-candidate").deleteRecursively())
                check(File(recoveryRoot, "restore-rollback").deleteRecursively())
                if (candidateWon && quarantine != null) check(quarantine.deleteRecursively())
                check(journal.delete())
                recoveryRoot.delete()
                if (!candidateWon && quarantine != null) {
                    check(!recoveryRoot.exists() && quarantine.renameTo(recoveryRoot))
                    markRestricted(database)
                }
                if (candidateWon || quarantine == null) database.openHelper.writableDatabase.execSQL("UPDATE recovery_metadata SET restrictedRecoveryState=0 WHERE id='primary'")
            }.isSuccess
            if (!resolved) {
                markRestricted(database)
                return RecoveryResult.RESTRICTED
            }
            if (!candidateWon && quarantine != null) return RecoveryResult.RESTRICTED
            return if (candidateWon) RecoveryResult.CANDIDATE_COMMITTED else RecoveryResult.ORIGINAL_RESTORED
        }
        private fun markRestricted(database: ServiceLoopDatabase) = runCatching { database.openHelper.writableDatabase.execSQL("UPDATE recovery_metadata SET restrictedRecoveryState=1 WHERE id='primary'") }
        private fun safeRelativePathStatic(path: String) = !path.startsWith('/') && !path.startsWith('\\') && !path.contains(':') && path.replace('\\', '/').split('/').none { it.isBlank() || it == "." || it == ".." } && !File(path).isAbsolute
    }
}
