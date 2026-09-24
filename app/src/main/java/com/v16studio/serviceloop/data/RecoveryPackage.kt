package com.v16studio.serviceloop.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.room.withTransaction
import com.v16studio.serviceloop.domain.BackupInspection
import com.v16studio.serviceloop.domain.BackupResult
import com.v16studio.serviceloop.domain.CustomerType
import com.v16studio.serviceloop.domain.ReminderPreferences
import com.v16studio.serviceloop.domain.WorkSubjectType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
        reconcilePendingOriginalDeletions(database, fileRoot, System.currentTimeMillis())
        val snapshotAt = System.currentTimeMillis()
        val rawSnapshot = database.withTransaction {
            database.openHelper.writableDatabase.execSQL(
                "INSERT OR IGNORE INTO technician_identity(id,technicianId,displayName,createdAtEpochMillis,modifiedAtEpochMillis) SELECT 'primary', ?, COALESCE(NULLIF(TRIM((SELECT technicianName FROM business_profiles WHERE id='primary')),''), 'Technician'), CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)",
                arrayOf(TechnicianIdCodec.generate()),
            )
            exportDatabase()
        }
        val databaseObject = JSONObject(rawSnapshot.toString(Charsets.UTF_8))
        // An unopened preference store can have no persisted singleton yet. Capture the
        // product default as portable state so this backup passes the same restore contract.
        if (tableRows(databaseObject, "reminder_preferences").isEmpty()) normalizeLegacyReminderState(databaseObject)
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
        // Ordinary Room writers need not take the business-file mutex. Reject an
        // archive assembled across two database states instead of calling it complete.
        database.withTransaction {
            require(exportDatabase().contentEquals(rawSnapshot)) { "Business data changed during backup; retry" }
        }
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
        require(version == FORMAT_VERSION && manifest.getInt("schemaVersion") in SUPPORTED_SCHEMA_VERSIONS) { "Unsupported backup format" }
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
        require(db.getInt("schemaVersion") == manifest.getInt("schemaVersion")) { "Backup schema versions disagree" }
        validateDatabase(db)
        require(tableRows(db, "recovery_metadata").single().getString("datasetId") == manifest.getString("datasetId")) { "Backup dataset identity disagrees" }
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
        val manifest = JSONObject(entries.getValue("manifest.json").toString(Charsets.UTF_8))
        require(manifest.getInt("formatVersion") == FORMAT_VERSION &&
            manifest.getString("datasetId") == inspection.datasetId &&
            manifest.getLong("snapshotAtEpochMillis") == inspection.snapshotAtEpochMillis) { "Recovery inspection no longer matches the staged package" }
        require(manifest.getInt("schemaVersion") == databaseObject.getInt("schemaVersion") && manifest.getInt("schemaVersion") in SUPPORTED_SCHEMA_VERSIONS)
        require(sha256(entries.getValue("database.json")) == manifest.getString("databaseSha256")) { "Database snapshot integrity check failed" }
        require(tableRows(databaseObject, "recovery_metadata").single().getString("datasetId") == manifest.getString("datasetId")) { "Backup dataset identity disagrees" }
        validateDatabase(databaseObject)
        val missing = manifest.getJSONArray("missingFiles").let { array -> List(array.length()) { array.getString(it) } }
        require(missing == inspection.missingFiles && missing.size == missing.toSet().size && missing.all(::safeRelativePath)) { "Recovery missing-file declarations changed" }
        val stagedFiles = manifest.getJSONArray("files")
        val stagedPaths = mutableSetOf<String>()
        for (index in 0 until stagedFiles.length()) {
            val descriptor = stagedFiles.getJSONObject(index)
            val path = descriptor.getString("path")
            require(safeRelativePath(path) && stagedPaths.add(path) && path !in missing) { "Unsafe or duplicate staged file" }
            val bytes = entries["files/${path.replace('\\', '/')}"] ?: error("Staged recovery file is missing: $path")
            require(bytes.size.toLong() == descriptor.getLong("size") && sha256(bytes) == descriptor.getString("sha256")) { "Staged recovery file changed: $path" }
        }
        require(entries.keys.all { it == "manifest.json" || it == "database.json" || (it.startsWith("files/") && it.removePrefix("files/") in stagedPaths) }) { "Unexpected staged recovery entry" }
        require(manifest.getBoolean("complete") == missing.isEmpty() && inspection.complete == missing.isEmpty()) { "Recovery completeness changed" }
        crossValidateFiles(databaseObject, stagedFiles, missing)
        val recoveryRoot = File(fileRoot, "recovery")
        var restrictedQuarantine: File? = null
        if (recoverInterrupted(database, fileRoot) == RecoveryResult.RESTRICTED) {
            restrictedQuarantine = File(fileRoot, "recovery-restricted-${UUID.randomUUID()}")
            check(recoveryRoot.renameTo(restrictedQuarantine)) { "Unable to isolate the damaged recovery journal" }
        }
        val stage = File(recoveryRoot, "restore-candidate")
        val rollback = File(recoveryRoot, "restore-rollback")
        check(!journalPresent(journalFile(recoveryRoot))) { "Another recovery operation is unfinished" }
        stage.deleteRecursively(); rollback.deleteRecursively()
        require(stage.mkdirs() && rollback.mkdirs()) { "Unable to create recovery staging" }
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
                val target = OwnedBusinessFiles.resolve(stage, relative)
                require(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                target.outputStream().use { stream -> stream.write(bytes); stream.fd.sync() }
                require(target.length() == bytes.size.toLong() && sha256(target.readBytes()) == sha256(bytes)) {
                    "Staged recovery file failed verification"
                }
            }
            val initialJournal = JSONObject().put("operation", "RESTORE").put("adoptionToken", adoptionToken).put("paths", JSONArray(touchedPaths)).put("candidatePaths", JSONArray(paths)).put("adoptedPaths", JSONArray()).put("phase", "PREPARED")
            restrictedQuarantine?.let { initialJournal.put("restrictedQuarantine", it.name) }
            writeJournal(journal, initialJournal)
            failureInjector(FailurePoint.BEFORE_FILE_ADOPTION)
            writeJournal(journal, readJournal(journal).put("phase", "ADOPTING_FILES"))
            touchedPaths.forEachIndexed { index, relative ->
                val current = OwnedBusinessFiles.resolve(fileRoot, relative)
                writeJournal(journal, readJournal(journal).put("processingPath", relative).put("processingOriginalExisted", current.isFile))
                if (current.isFile) {
                    val saved = OwnedBusinessFiles.resolve(rollback, relative)
                    require(saved.parentFile!!.isDirectory || saved.parentFile!!.mkdirs())
                    atomicMove(current, saved)
                }
                val candidate = OwnedBusinessFiles.resolve(stage, relative)
                if (candidate.isFile) {
                    val target = OwnedBusinessFiles.resolve(fileRoot, relative)
                    require(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                    atomicMove(candidate, target)
                }
                val state = readJournal(journal); state.getJSONArray("adoptedPaths").put(relative); state.remove("processingPath"); state.remove("processingOriginalExisted"); writeJournal(journal, state)
                if (index == 0) failureInjector(FailurePoint.DURING_FILE_ADOPTION)
            }
            writeJournal(journal, readJournal(journal).put("phase", "DB_COMMITTING"))
            failureInjector(FailurePoint.BEFORE_DB_TRANSACTION)
            database.withTransaction { replaceDatabase(databaseObject); failureInjector(FailurePoint.DURING_DB_TRANSACTION) }
            failureInjector(FailurePoint.AFTER_DB_COMMIT)
            writeJournal(journal, readJournal(journal).put("phase", "COMMITTED"))
            failureInjector(FailurePoint.AFTER_COMMITTED_JOURNAL)
            rollback.deleteRecursively(); stage.deleteRecursively()
            restrictedQuarantine?.let { check(it.deleteRecursively()) { "Damaged recovery quarantine could not be removed" } }
            check(journal.delete())
            if (inspection.missingFiles.isEmpty()) recoveryRoot.delete()
        } catch (failure: Exception) {
            recoverInterrupted(database, fileRoot)
            throw failure
        } finally {
            if (!journalPresent(journal)) stage.deleteRecursively()
        }
    }

    private data class RequiredFile(val path: String, val size: Long, val hash: String, val kind: String)
    private fun requiredFiles(root: JSONObject): List<RequiredFile> {
        val deletedOriginals = tableRows(root, "retained_images")
            .filter { !it.isNull("originalDeletedAtEpochMillis") }
            .map { it.getString("sourceKind") to it.getString("sourceId") }.toSet()
        val attachments = tableRows(root, "attachments")
            .filterNot { ("ATTACHMENT" to it.getString("id")) in deletedOriginals }
            .map { RequiredFile(it.getString("storedRelativePath"), it.getLong("byteSize"), it.getString("sha256"), "ATTACHMENT") }
        val finalPhotos = tableRows(root, "final_photo_entries")
            .filterNot { ("FINAL_PHOTO" to it.getString("id")) in deletedOriginals ||
                ("ATTACHMENT" to it.getString("sourceAttachmentId")) in deletedOriginals }
            .map { RequiredFile(it.getString("storedRelativePath"), it.getLong("byteSize"), it.getString("sha256"), "FINAL_PHOTO") }
        val reports = tableRows(root, "report_renditions").filter { it.getString("status") in setOf("READY", "MISSING") && !it.isNull("sha256") }.map { RequiredFile(it.getString("relativePath"), it.optLong("byteSize"), it.getString("sha256"), "REPORT") }
        val remotePhotos = tableRows(root, "remote_result_photos").map { RequiredFile(it.getString("relativePath"), it.getLong("byteSize"), it.getString("sha256"), "REMOTE_PHOTO") }
        val aggregateReports = tableRows(root, "aggregate_report_renditions").filter { it.getString("status") in setOf("READY", "MISSING") && !it.isNull("relativePath") && !it.isNull("sha256") }.map { RequiredFile(it.getString("relativePath"), it.getLong("byteSize"), it.getString("sha256"), "AGGREGATE_REPORT") }
        val derivatives = tableRows(root, "retained_images").map { RequiredFile(it.getString("derivativeRelativePath"), it.getLong("derivativeByteSize"), it.getString("derivativeSha256"), "IMAGE_DERIVATIVE") }
        val transferredPhotos = tableRows(root, "transferred_evidence").map { RequiredFile(it.getString("relativePath"), it.getLong("byteSize"), it.getString("sha256"), "TRANSFERRED_EVIDENCE") }
        val all = attachments + finalPhotos + reports + remotePhotos + aggregateReports + derivatives + transferredPhotos
        all.forEach { OwnedBusinessFiles.resolve(fileRoot, it.path) }
        return all.groupBy { it.path }.map { (path, references) ->
            val first = references.first()
            require(references.all { it.hash == first.hash && it.size == first.size }) { "Conflicting file descriptors for $path" }
            first
        }
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
        val sourceVersion = root.getInt("schemaVersion")
        require(sourceVersion in SUPPORTED_SCHEMA_VERSIONS) { "Unsupported Recovery schema" }
        val originalTables = root.getJSONArray("tables")
        val declaredNames = (0 until originalTables.length()).map { originalTables.getJSONObject(it).getString("name") }
        require(declaredNames == expectedTables(sourceVersion)) { "Recovery source table declarations are incomplete, duplicated, or out of order" }
        originalTables.let { tables ->
            for (index in 0 until tables.length()) {
                val declaration = tables.getJSONObject(index)
                val rows = declaration.getJSONArray("rows")
                require(rows.length() <= MAX_ROWS_PER_TABLE) { "Backup contains too many records" }
                val expectedColumns = RecoverySourceShapes.columns(sourceVersion, declaration.getString("name"))
                val columnTypes = mutableMapOf<String, String>()
                database.openHelper.readableDatabase.query("PRAGMA table_info(\"${declaration.getString("name")}\")").use { cursor ->
                    while (cursor.moveToNext()) columnTypes[cursor.getString(1)] = cursor.getString(2).uppercase()
                }
                require(expectedColumns.all { it in columnTypes }) { "Recovery source has unknown column types" }
                for (rowIndex in 0 until rows.length()) {
                    val row = rows.getJSONObject(rowIndex)
                    require(row.keys().asSequence().toSet() == expectedColumns) {
                        "Recovery source row has missing or conflicting columns in ${declaration.getString("name")}"
                    }
                    expectedColumns.forEach { column ->
                        val value = row.get(column)
                        if (value != JSONObject.NULL) require(when (columnTypes.getValue(column)) {
                            "TEXT" -> value is String
                            "INTEGER" -> value is Number && value.toString().toLongOrNull() != null
                            "REAL" -> value is Number
                            "BLOB" -> value is JSONObject && value.has("blob") && value.get("blob") is String
                            else -> false
                        }) { "Recovery source column has an invalid type: ${declaration.getString("name")}.$column" }
                    }
                }
            }
        }
        if (sourceVersion < 11) normalizeLegacyReminderState(root)
        if (sourceVersion < 14) normalizeWorkingInputBuffers(root)
        if (sourceVersion < 15) normalizeB026State(root)
        if (sourceVersion < 16) normalizeCustomerContacts(root)
        if (sourceVersion < 18) normalizeContactOrder(root)
        if (sourceVersion < 17) normalizeTrustedServiceLoopIds(root)
        if (sourceVersion < 18) normalizeB049Tables(root)
        if (sourceVersion < 19) normalizeRepairColumns(root)
        root.put("schemaVersion", SCHEMA_VERSION)
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
        tableRows(root, "customers").forEach { CustomerType.fromCode(it.getString("customerType")) }
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
        val siteCustomers = tableRows(root, "sites").associate { it.getString("id") to it.getString("customerId") }
        val equipmentSites = tableRows(root, "equipment").associate { it.getString("id") to it.getString("siteId") }
        require(tableRows(root, "service_plans").all { plan ->
            val customerId = equipmentSites[plan.getString("equipmentId")]?.let(siteCustomers::get)
            tableRows(root, "customers").firstOrNull { it.getString("id") == customerId }?.getString("customerType") == CustomerType.STANDARD.code
        }) { "Recurring service requires a Standard customer" }
        validateB026Rows(root, equipment, plans)
        val templateRevisions = tableRows(root, "reusable_template_revisions").associateBy { it.getString("id") }
        require(tableRows(root, "reusable_templates").all { template -> templateRevisions[template.getString("currentRevisionId")]?.getString("templateId") == template.getString("id") }) { "A reusable template has an invalid current revision" }
        require(tableRows(root, "correction_drafts").all { draft -> revisions[draft.getString("baseRevisionId")]?.getString("recordId") == draft.getString("recordId") }) { "A correction base revision does not belong to its record" }
        val recovery = tableRows(root, "recovery_metadata")
        require(recovery.size == 1 && recovery.single().getString("id") == "primary") { "Recovery metadata singleton is invalid" }
        val reminders = tableRows(root, "reminder_preferences")
        require(reminders.size == 1 && reminders.single().getString("id") == "primary") { "Reminder preferences singleton is invalid" }
        reminders.single().let { value ->
            require(value.getInt("summaryHour") in 0..23 && value.getInt("summaryMinute") in 0..59)
            require(value.getInt("summaryDaysMask") in 0..127 && value.getInt("dueSoonHorizonDays") in ReminderPreferences.HORIZONS)
            require(value.getInt("defaultAppointmentLeadMinutes") in ReminderPreferences.APPOINTMENT_LEADS)
            val booleanFields = listOf("dailySummaryEnabled", "includeDueServices", "includeVisits", "includeFollowUps", "includeUnfinishedVisits", "includeBackupReminder", "appointmentAlertsEnabled")
            require(booleanFields.all { value.getInt(it) in 0..1 })
            require(value.getInt("dailySummaryEnabled") == 0 || value.getInt("summaryDaysMask") != 0)
        }
        val identity = tableRows(root, "technician_identity")
        require(identity.size == 1 && identity.single().getString("id") == "primary" && identity.single().getString("technicianId").isNotBlank()) { "Technician identity singleton is invalid" }
        val ownId = TechnicianIdCodec.normalize(identity.single().getString("technicianId")) ?: identity.single().getString("technicianId")
        val trustedRows = tableRows(root, "trusted_service_loop_ids")
        val trustedIds = trustedRows.map { it.getString("peerId") }
        require(trustedIds.size == trustedIds.toSet().size && trustedRows.all { row ->
            val peerId = row.getString("peerId")
            TechnicianIdCodec.normalize(peerId) == peerId && peerId != ownId && row.getString("name").trim().let { it.isNotEmpty() && it.length <= 80 } && row.getLong("createdAtEpochMillis") >= 0 && row.getLong("modifiedAtEpochMillis") >= row.getLong("createdAtEpochMillis")
        }) { "Trusted ServiceLoop IDs are invalid" }
        val visitIds = ids("working_visits")
        require(tableRows(root, "dispatch_visit_bindings").all { it.getString("localVisitId") in visitIds }) { "Dispatch binding points to a missing Visit" }
        validateImportedGraph(root)
        validateAgainstRoomSchema(root)
    }

    private fun validateImportedGraph(root: JSONObject) {
        val receipts = tableRows(root, "work_result_receipts").associateBy {
            Triple(it.getString("exporterId"), it.getString("resultId"), it.getString("sourceFinalRevisionId"))
        }
        val remote = tableRows(root, "remote_final_results")
        val remoteById = remote.associateBy { it.getString("id") }
        require(remote.all { row ->
            val receipt = receipts[Triple(row.getString("technicianId"), row.getString("resultId"), row.getString("sourceFinalRevisionId"))]
            receipt != null && receipt.getString("dispatchVisitId") == row.getString("dispatchVisitId") &&
                receipt.getString("dispatchItemId") == row.getString("dispatchItemId")
        }) { "A received result disagrees with its author-scoped receipt" }
        require(tableRows(root, "remote_result_photos").all { row ->
            val owner = remoteById[row.getString("remoteFinalResultId")]
            owner != null && owner.getString("dispatchItemId") == row.getString("dispatchItemId")
        }) { "A received photo has the wrong result owner" }
        remote.forEach { row ->
            if (!row.isNull("sourcePayloadJson")) {
                val source = FinalSourceSnapshot.record(row.getString("sourcePayloadJson"), "WORK_RESULT")
                require(source.getString("resultId") == row.getString("resultId") &&
                    source.getString("sourceFinalRevisionId") == row.getString("sourceFinalRevisionId") &&
                    source.getString("technicianId") == row.getString("technicianId") &&
                    source.getString("dispatchVisitId") == row.getString("dispatchVisitId") &&
                    source.getString("dispatchItemId") == row.getString("dispatchItemId") &&
                    source.getString("serviceDate") == row.getString("serviceDate") &&
                    source.getString("outcome") == row.getString("outcome")) { "Received result source snapshot disagrees with its indexed facts" }
            }
        }
        val transfers = tableRows(root, "transferred_final_results")
        val transfersById = transfers.associateBy { it.getString("id") }
        transfers.forEach { row ->
            if (!row.isNull("sourcePayloadJson")) {
                val source = FinalSourceSnapshot.record(row.getString("sourcePayloadJson"), "PERFORMED_WORK")
                require(source.getString("originWorkspaceId") == row.getString("originWorkspaceId") &&
                    source.getString("sourceVisitId") == row.getString("sourceVisitId") &&
                    source.getString("sourceWorkItemId") == row.getString("sourceWorkItemId") &&
                    source.getString("sourceFinalRevisionId") == row.getString("sourceFinalRevisionId") &&
                    source.getString("serviceDate") == row.getString("serviceDate") &&
                    source.getString("outcome") == row.getString("outcome")) { "Transferred source snapshot disagrees with its indexed facts" }
            }
        }
        require(tableRows(root, "transferred_evidence").all { evidence ->
            if (evidence.isNull("transferredFinalResultId")) true else {
                val owner = transfersById[evidence.getString("transferredFinalResultId")]
                owner != null && owner.getString("originWorkspaceId") == evidence.getString("originWorkspaceId") &&
                    owner.getString("sourceVisitId") == evidence.getString("sourceVisitId") &&
                    owner.getString("sourceWorkItemId") == evidence.getString("sourceWorkItemId") &&
                    !evidence.isNull("sourceFinalRevisionId") &&
                    owner.getString("sourceFinalRevisionId") == evidence.getString("sourceFinalRevisionId")
            }
        }) { "Transferred evidence is linked to another source execution" }
        val aggregateIds = tableRows(root, "aggregate_reports").map { it.getString("id") }.toSet()
        val localRevisions = tableRows(root, "final_record_revisions").associateBy { it.getString("id") }
        val localRecords = tableRows(root, "final_records").associateBy { it.getString("id") }
        val localWork = tableRows(root, "final_work_items").associateBy { it.getString("id") }
        require(tableRows(root, "aggregate_report_sources").all { source ->
            if (source.getString("aggregateReportId") !in aggregateIds) false else when (source.getString("sourceKind")) {
                "LOCAL" -> {
                    val revisionId = source.getString("sourceFinalRevisionId")
                    val revision = localRevisions[revisionId]
                    val sourceId = source.getString("sourceEntityId")
                    revision != null && localRecords[revision.getString("recordId")]?.getString("visitId") == source.getString("visitId") &&
                        (sourceId == revisionId || (sourceId.startsWith("$revisionId:") &&
                            localWork[sourceId.removePrefix("$revisionId:")]?.getString("revisionId") == revisionId))
                }
                "REMOTE" -> remoteById[source.getString("sourceEntityId")]?.let { remoteRow ->
                    remoteRow.getString("sourceFinalRevisionId") == source.getString("sourceFinalRevisionId") &&
                        !remoteRow.isNull("localVisitId") && remoteRow.getString("localVisitId") == source.getString("visitId")
                } ?: false
                "TRANSFERRED" -> transfersById[source.getString("sourceEntityId")]?.let { transferRow ->
                    transferRow.getString("sourceFinalRevisionId") == source.getString("sourceFinalRevisionId") &&
                        source.getString("visitId") == "TRANSFERRED:${transferRow.getString("originWorkspaceId")}:${transferRow.getString("sourceVisitId")}"
                } ?: false
                else -> false
            }
        }) { "An aggregate source does not resolve to its exact final revision" }
        require(tableRows(root, "aggregate_report_renditions").all { it.getString("aggregateReportId") in aggregateIds }) {
            "An aggregate rendition has no report owner"
        }
    }

    private fun expectedTables(version: Int): List<String> = TABLE_ORDER.filter { table ->
        when (table) {
            "reminder_preferences" -> version >= 11
            "working_input_buffers" -> version >= 14
            "customer_contacts" -> version >= 16
            "trusted_service_loop_ids" -> version >= 17
            "work_result_receipts", "remote_final_results", "remote_result_photos",
            "aggregate_reports", "aggregate_report_sources", "aggregate_report_renditions", "retained_images",
            "data_transfer_bindings", "transferred_final_results", "transferred_evidence",
            "transferred_history_entries" -> version >= 18
            else -> true
        }
    }

    private fun normalizeRepairColumns(root: JSONObject) {
        listOf(
            "final_work_items" to "followUpsSnapshotJson",
            "remote_final_results" to "sourcePayloadJson",
            "transferred_final_results" to "sourcePayloadJson",
            "retained_images" to "originalDeletionRequestedAtEpochMillis",
        ).forEach { (table, column) ->
            tableRows(root, table).forEach { row ->
                require(!row.has(column)) { "Recovery source claims a newer column" }
                row.put(column, JSONObject.NULL)
            }
        }
    }

    /** Replays the current generated Room schema into an isolated throwaway database. */
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

    /** v9/v10 backups predate portable reminder preferences; adopt exact product defaults. */
    private fun normalizeLegacyReminderState(root: JSONObject) {
        val tables = root.getJSONArray("tables")
        for (index in 0 until tables.length()) {
            val table = tables.getJSONObject(index)
            if (table.getString("name") == "reminder_preferences") {
                if (table.getJSONArray("rows").length() == 0) table.put("rows", JSONArray().put(defaultReminderRow()))
                return
            }
        }
        val normalized = JSONArray()
        for (index in 0 until tables.length()) {
            val table = tables.getJSONObject(index)
            if (table.getString("name") == "recovery_metadata") {
                normalized.put(JSONObject().put("name", "reminder_preferences").put("rows", JSONArray().put(defaultReminderRow())))
            }
            normalized.put(table)
        }
        root.put("tables", normalized)
    }

    /** v9-v13 backups predate durable raw Service edit buffers. Do not infer any rows. */
    private fun normalizeWorkingInputBuffers(root: JSONObject) {
        val tables = root.getJSONArray("tables")
        if ((0 until tables.length()).any { tables.getJSONObject(it).getString("name") == "working_input_buffers" }) return
        val normalized = JSONArray()
        for (index in 0 until tables.length()) {
            val table = tables.getJSONObject(index)
            normalized.put(table)
            if (table.getString("name") == "work_item_private_drafts") {
                normalized.put(JSONObject().put("name", "working_input_buffers").put("rows", JSONArray()))
            }
        }
        root.put("tables", normalized)
    }

    /** B026 backups predate typed customers and flexible work subjects. Legacy rows are known Equipment work. */
    private fun normalizeB026State(root: JSONObject) {
        tableRows(root, "customers").forEach { row -> if (!row.has("customerType") || row.isNull("customerType")) row.put("customerType", CustomerType.STANDARD.code) }
        tableRows(root, "work_items").forEach { row ->
            if (!row.has("subjectType") || row.isNull("subjectType")) row.put("subjectType", WorkSubjectType.EQUIPMENT.code)
            if (!row.has("equipmentDescriptionSnapshot")) row.put("equipmentDescriptionSnapshot", JSONObject.NULL)
        }
        tableRows(root, "final_work_items").forEach { row ->
            if (!row.has("subjectType") || row.isNull("subjectType")) row.put("subjectType", WorkSubjectType.EQUIPMENT.code)
            if (!row.has("equipmentDescription")) row.put("equipmentDescription", JSONObject.NULL)
        }
        tableRows(root, "dispatch_outbox_items").forEach { row ->
            if (!row.has("subjectType") || row.isNull("subjectType")) row.put("subjectType", WorkSubjectType.EQUIPMENT.code)
            if (!row.has("equipmentDescription")) row.put("equipmentDescription", JSONObject.NULL)
        }
        tableRows(root, "dispatch_item_bindings").forEach { row ->
            if (!row.has("subjectType") || row.isNull("subjectType")) row.put("subjectType", WorkSubjectType.EQUIPMENT.code)
            if (!row.has("equipmentDescriptionSnapshot")) row.put("equipmentDescriptionSnapshot", JSONObject.NULL)
        }
    }

    private fun normalizeCustomerContacts(root: JSONObject) {
        val tables = root.getJSONArray("tables")
        if ((0 until tables.length()).none { tables.getJSONObject(it).getString("name") == "customer_contacts" }) {
            val contacts = JSONArray()
            val insertAt = (0 until tables.length()).firstOrNull { tables.getJSONObject(it).getString("name") == "customers" }?.plus(1) ?: 0
            val normalized = JSONArray()
            for (index in 0 until tables.length()) {
                if (index == insertAt) normalized.put(JSONObject().put("name", "customer_contacts").put("rows", contacts))
                normalized.put(tables.get(index))
            }
            root.put("tables", normalized)
        }
    }

    private fun normalizeContactOrder(root: JSONObject) {
        val rows = tableRows(root, "customer_contacts")
        rows.groupBy { it.getString("customerId") }.values.forEach { contacts ->
            val ordered = if (contacts.all { it.has("position") && it.optInt("position") > 0 } &&
                contacts.map { it.optInt("position") }.toSet().size == contacts.size) {
                contacts.sortedWith(compareBy<JSONObject> { it.getInt("position") }.thenBy { it.getString("id") })
            } else {
                contacts.sortedWith(compareByDescending<JSONObject> { it.optLong("modifiedAtEpochMillis") }.thenBy { it.getString("id") })
            }
            ordered.forEachIndexed { index, row ->
                if (!row.has("notes")) row.put("notes", JSONObject.NULL)
                row.put("position", index + 1)
            }
        }
    }

    /** Recovery schema v17 adds device-local peer trust without inventing entries in old backups. */
    internal fun normalizeTrustedServiceLoopIds(root: JSONObject) {
        val tables = root.getJSONArray("tables")
        if ((0 until tables.length()).none { tables.getJSONObject(it).getString("name") == "trusted_service_loop_ids" }) {
            val insertAt = (0 until tables.length()).firstOrNull { tables.getJSONObject(it).getString("name") == "technician_identity" }?.plus(1) ?: tables.length()
            for (index in tables.length() downTo insertAt + 1) tables.put(index, tables.get(index - 1))
            tables.put(insertAt, JSONObject().put("name", "trusted_service_loop_ids").put("rows", JSONArray()))
        }
    }

    private fun normalizeB049Tables(root: JSONObject) {
        normalizeB049PhotoFlags(root)
        tableRows(root, "dispatch_outbox_visits").forEach { if (!it.has("localVisitId")) it.put("localVisitId", JSONObject.NULL) }
        tableRows(root, "dispatch_outbox_items").forEach { if (!it.has("localWorkItemId")) it.put("localWorkItemId", JSONObject.NULL) }
        tableRows(root, "dispatch_visit_bindings").forEach { if (!it.has("assignmentIssuerId")) it.put("assignmentIssuerId", JSONObject.NULL) }
        tableRows(root, "final_dispatch_visits").forEach {
            if (!it.has("assignmentMaterialHash")) it.put("assignmentMaterialHash", JSONObject.NULL)
            if (!it.has("assignmentIssuerId")) it.put("assignmentIssuerId", JSONObject.NULL)
        }
        val tables = root.getJSONArray("tables")
        val present = (0 until tables.length()).map { tables.getJSONObject(it).getString("name") }.toSet()
        require(present.all { it in TABLE_ORDER }) { "Unexpected database table" }
        val newTables = setOf("work_result_receipts", "remote_final_results", "remote_result_photos", "aggregate_reports", "aggregate_report_sources", "aggregate_report_renditions", "retained_images", "data_transfer_bindings", "transferred_final_results", "transferred_evidence", "transferred_history_entries")
        require(TABLE_ORDER.filterNot { it in newTables }.all { it in present }) { "Backup is missing an established business table" }
        val normalized = JSONArray()
        TABLE_ORDER.forEach { name ->
            val existing = (0 until tables.length()).firstOrNull { tables.getJSONObject(it).getString("name") == name }
            normalized.put(if (existing != null) tables.getJSONObject(existing) else JSONObject().put("name", name).put("rows", JSONArray()))
        }
        root.put("tables", normalized)
    }

    internal fun normalizeB049PhotoFlags(root: JSONObject) {
        tableRows(root, "attachments").forEach { if (!it.has("visibility")) it.put("visibility", "PUBLIC") }
        tableRows(root, "final_photo_entries").forEach {
            if (!it.has("includedInCustomerReport")) it.put("includedInCustomerReport", 1)
            if (!it.has("visibility")) it.put("visibility", "PUBLIC")
        }
    }

    private fun validateB026Rows(root: JSONObject, equipmentIds: Set<String>, planIds: Set<String>) {
        tableRows(root, "work_items").forEach { row ->
            val subject = WorkSubjectType.fromCode(row.getString("subjectType"))
            val equipmentId = nullableString(row, "equipmentId")
            val description = nullableString(row, "equipmentDescriptionSnapshot")
            when (subject) {
                WorkSubjectType.SITE -> require(equipmentId == null && nullableString(row, "equipmentNameSnapshot") == null && nullableString(row, "equipmentReferenceSnapshot") == null && nullableString(row, "equipmentIdentifierSnapshot") == null && nullableString(row, "equipmentMakeSnapshot") == null && nullableString(row, "equipmentModelSnapshot") == null && nullableString(row, "equipmentSerialSnapshot") == null && description == null && nullableString(row, "servicePlanId") == null && nullableString(row, "capturedObligationId") == null && row.optInt("fulfillsCurrentObligation", 0) == 0) { "SITE work item has Equipment-only data" }
                WorkSubjectType.EQUIPMENT -> if (equipmentId == null) {
                    require(nullableString(row, "equipmentNameSnapshot") == null && nullableString(row, "equipmentReferenceSnapshot") == null && nullableString(row, "equipmentIdentifierSnapshot") == null && nullableString(row, "equipmentMakeSnapshot") == null && nullableString(row, "equipmentModelSnapshot") == null && nullableString(row, "equipmentSerialSnapshot") == null && nullableString(row, "servicePlanId") == null && nullableString(row, "capturedObligationId") == null && row.optInt("fulfillsCurrentObligation", 0) == 0 && (description == null || description.trim().length <= 500)) { "Unidentified Equipment work item is invalid" }
                } else {
                    require(equipmentId in equipmentIds && nullableString(row, "equipmentNameSnapshot")?.isNotBlank() == true && nullableString(row, "equipmentReferenceSnapshot")?.isNotBlank() == true && description == null) { "Known Equipment work item is invalid" }
                }
            }
        }
        tableRows(root, "final_work_items").forEach { row ->
            val subject = WorkSubjectType.fromCode(row.getString("subjectType"))
            val equipmentId = nullableString(row, "equipmentId")
            val description = nullableString(row, "equipmentDescription")
            when (subject) {
                WorkSubjectType.SITE -> require(equipmentId == null && nullableString(row, "equipmentName") == null && nullableString(row, "equipmentReference") == null && nullableString(row, "equipmentIdentifier") == null && nullableString(row, "equipmentMake") == null && nullableString(row, "equipmentModel") == null && nullableString(row, "equipmentSerial") == null && description == null && nullableString(row, "planId") == null && nullableString(row, "capturedObligationId") == null) { "SITE final work item has Equipment-only data" }
                WorkSubjectType.EQUIPMENT -> if (equipmentId == null) {
                    require(nullableString(row, "equipmentName") == null && nullableString(row, "equipmentReference") == null && nullableString(row, "equipmentIdentifier") == null && nullableString(row, "equipmentMake") == null && nullableString(row, "equipmentModel") == null && nullableString(row, "equipmentSerial") == null && nullableString(row, "planId") == null && nullableString(row, "capturedObligationId") == null && (description == null || description.trim().length <= 500)) { "Unidentified Equipment final work item is invalid" }
                } else {
                    require(equipmentId in equipmentIds && nullableString(row, "equipmentName")?.isNotBlank() == true && nullableString(row, "equipmentReference")?.isNotBlank() == true && description == null) { "Known Equipment final work item is invalid" }
                }
            }
        }
        tableRows(root, "dispatch_outbox_items").forEach { row ->
            val subject = WorkSubjectType.fromCode(row.getString("subjectType"))
            val equipmentId = nullableString(row, "equipmentId")
            val description = nullableString(row, "equipmentDescription")
            require(subject == WorkSubjectType.SITE && equipmentId == null && description == null || subject == WorkSubjectType.EQUIPMENT && (equipmentId == null || equipmentId in equipmentIds) && (description == null || description.trim().length <= 500)) { "Dispatch work item is invalid" }
        }
        tableRows(root, "dispatch_item_bindings").forEach { row ->
            val subject = WorkSubjectType.fromCode(row.getString("subjectType"))
            val description = nullableString(row, "equipmentDescriptionSnapshot")
            require(subject == WorkSubjectType.SITE && nullableString(row, "equipmentReferenceSnapshot") == null && description == null || subject == WorkSubjectType.EQUIPMENT && (description == null || description.trim().length <= 500)) { "Dispatch binding work item is invalid" }
        }
    }

    private fun nullableString(row: JSONObject, key: String): String? = if (!row.has(key) || row.isNull(key)) null else row.optString(key).trim().takeIf { it.isNotEmpty() }

    private fun defaultReminderRow() = JSONObject()
        .put("id", "primary").put("dailySummaryEnabled", 1).put("summaryHour", 8).put("summaryMinute", 0)
        .put("summaryDaysMask", 127).put("dueSoonHorizonDays", 14).put("includeDueServices", 1)
        .put("includeVisits", 1).put("includeFollowUps", 1).put("includeUnfinishedVisits", 1)
        .put("includeBackupReminder", 1).put("appointmentAlertsEnabled", 0).put("defaultAppointmentLeadMinutes", 180)
    private fun countRecords(root: JSONObject): Int = TABLE_ORDER.sumOf { tableRows(root, it).size }

    /** Recoverable erase: file adoption and the empty database share one durable commit identity. */
    suspend fun eraseDatabaseAndOwnedFiles(newDatasetId: String) = BusinessFileCoordinator.mutex.withLock { eraseUnlocked(newDatasetId) }

    private suspend fun eraseUnlocked(newDatasetId: String) {
        check(recoverInterrupted(database, fileRoot) != RecoveryResult.RESTRICTED) { "Recovery is restricted; erase cannot continue" }
        val recoveryRoot = File(fileRoot, "recovery")
        val rollback = File(recoveryRoot, "restore-rollback")
        val journal = journalFile(recoveryRoot)
        check(!journalPresent(journal)) { "Another recovery operation is unfinished" }
        rollback.deleteRecursively()
        require(rollback.mkdirs()) { "Unable to create erase rollback staging" }
        val paths = ownedBusinessFiles().sorted()
        val token = UUID.randomUUID().toString()
        try {
            writeJournal(journal, JSONObject().put("operation", "ERASE").put("adoptionToken", token).put("paths", JSONArray(paths)).put("candidatePaths", JSONArray()).put("adoptedPaths", JSONArray()).put("phase", "ADOPTING_FILES"))
            paths.forEachIndexed { index, relative ->
                val current = File(fileRoot, relative)
                writeJournal(journal, readJournal(journal).put("processingPath", relative).put("processingOriginalExisted", current.isFile))
                if (current.isFile) {
                    val saved = File(rollback, relative)
                    saved.parentFile?.mkdirs()
                    check(current.renameTo(saved) || runCatching { current.copyTo(saved, overwrite = true); current.delete() }.getOrDefault(false))
                }
                val state = readJournal(journal); state.getJSONArray("adoptedPaths").put(relative); state.remove("processingPath"); state.remove("processingOriginalExisted"); writeJournal(journal, state)
                if (index == 0) failureInjector(FailurePoint.DURING_ERASE_FILES)
            }
            writeJournal(journal, readJournal(journal).put("phase", "DB_COMMITTING"))
            database.withTransaction {
                val db = database.openHelper.writableDatabase
                val preserved = setOf("recovery_metadata", "technician_identity", "trusted_service_loop_ids")
                TABLE_ORDER.asReversed().filterNot { it in preserved }.forEach { db.execSQL("DELETE FROM `$it`") }
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
            writeJournal(journal, readJournal(journal).put("phase", "COMMITTED"))
            failureInjector(FailurePoint.AFTER_COMMITTED_JOURNAL)
            check(rollback.deleteRecursively()) { "Private file cleanup did not complete" }
            android.util.AtomicFile(journal).delete()
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
        tableRows(root, "aggregate_report_renditions").forEach { row ->
            if (!row.isNull("relativePath") && row.getString("relativePath") in missing && row.getString("status") == "READY") {
                row.put("status", "MISSING").put("failureReason", "File declared missing in incomplete recovery copy")
            }
        }
        recoveryMetadata(root).put("restoredFromIncompleteCopy", if (missing.isEmpty()) 0 else 1)
    }

    private fun crossValidateFiles(root: JSONObject, manifest: JSONArray, missing: List<String>) {
        val references = requiredFiles(root)
        val expected = references.associateBy { it.path }
        require(expected.size == references.size) { "Conflicting file references in database snapshot" }
        val declarations = List(manifest.length()) { manifest.getJSONObject(it) }
        val declared = declarations.associateBy { it.getString("path") }
        require(declared.size == declarations.size) { "Duplicate recovery file declaration" }
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
        tableRows(root, "aggregate_report_renditions").forEach { row ->
            val absent = !row.isNull("relativePath") && row.getString("relativePath") in missing
            if (absent) require(row.getString("status") == "MISSING") { "Invalid aggregate report availability metadata" }
        }
    }

    private fun recoveryMetadata(root: JSONObject) = tableRows(root, "recovery_metadata").single()
    private fun setAdoptionToken(root: JSONObject, token: String) { recoveryMetadata(root).put("adoptionToken", token).put("restrictedRecoveryState", 0) }
    private fun ownedRootDirectories() = OwnedBusinessFiles.roots.map { File(fileRoot, it) }
    private fun ownedBusinessFiles(): List<String> = OwnedBusinessFiles.existing(fileRoot)
    private fun journalPresent(file: File) = file.isFile || File(file.path + ".pending").isFile
    private fun readJournal(file: File) = JSONObject(file.readText(Charsets.UTF_8))
    private fun writeJournal(file: File, value: JSONObject) {
        check(file.parentFile?.isDirectory == true || file.parentFile?.mkdirs() == true)
        val pending = File(file.path + ".pending")
        check(!pending.exists()) { "An earlier journal write needs recovery" }
        java.io.FileOutputStream(pending).use { output ->
            output.write(value.toString().toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        java.nio.file.Files.move(pending.toPath(), file.toPath(),
            java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
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
        val centralNames = validateZipDirectory(bytes)
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
        require(result.keys == centralNames) { "Backup ZIP entries disagree with its directory" }
        return result
    }
    private fun validateZipDirectory(bytes: ByteArray): Set<String> {
        // ZipInputStream only reads local headers. A truncated central directory can
        // otherwise leave all visible entries apparently valid after decryption.
        val earliest = (bytes.size - 22 - 65535).coerceAtLeast(0)
        val eocd = (bytes.size - 22 downTo earliest).firstOrNull { offset ->
            bytes[offset] == 0x50.toByte() && bytes[offset + 1] == 0x4b.toByte() &&
                bytes[offset + 2] == 0x05.toByte() && bytes[offset + 3] == 0x06.toByte() &&
                ByteBuffer.wrap(bytes, offset + 20, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt().and(0xffff) == bytes.size - offset - 22
        } ?: throw IllegalArgumentException("Backup ZIP directory is missing or truncated")
        val footer = ByteBuffer.wrap(bytes, eocd, 22).slice().order(ByteOrder.LITTLE_ENDIAN)
        footer.position(4)
        val disk = footer.short.toInt().and(0xffff)
        val directoryDisk = footer.short.toInt().and(0xffff)
        val diskCount = footer.short.toInt().and(0xffff)
        val totalCount = footer.short.toInt().and(0xffff)
        val directorySize = footer.int.toLong().and(0xffffffffL)
        val directoryOffset = footer.int.toLong().and(0xffffffffL)
        require(disk == 0 && directoryDisk == 0 && diskCount == totalCount && totalCount in 2..MAX_ENTRIES &&
            directorySize > 0 && directoryOffset + directorySize == eocd.toLong()) {
            "Backup ZIP directory is invalid"
        }
        val names = linkedSetOf<String>()
        var offset = directoryOffset.toInt()
        repeat(totalCount) {
            require(offset <= eocd - 46 && ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int == 0x02014b50) {
                "Backup ZIP directory entry is malformed"
            }
            val header = ByteBuffer.wrap(bytes, offset, 46).slice().order(ByteOrder.LITTLE_ENDIAN)
            header.position(28)
            val nameLength = header.short.toInt().and(0xffff)
            val extraLength = header.short.toInt().and(0xffff)
            val commentLength = header.short.toInt().and(0xffff)
            val startDisk = header.short.toInt().and(0xffff)
            header.position(42)
            val localOffset = header.int.toLong().and(0xffffffffL)
            val next = offset.toLong() + 46 + nameLength + extraLength + commentLength
            require(startDisk == 0 && nameLength > 0 && next <= eocd.toLong() &&
                localOffset <= directoryOffset - 4 &&
                ByteBuffer.wrap(bytes, localOffset.toInt(), 4).order(ByteOrder.LITTLE_ENDIAN).int == 0x04034b50) {
                "Backup ZIP directory points outside local entries"
            }
            val name = bytes.copyOfRange(offset + 46, offset + 46 + nameLength).toString(Charsets.UTF_8)
            require(safeArchiveEntry(name) && names.add(name)) { "Unsafe or duplicate ZIP directory entry" }
            offset = next.toInt()
        }
        require(offset == eocd) { "Backup ZIP directory length disagrees with its entries" }
        return names
    }
    private fun put(zip: ZipOutputStream, name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
    private fun safeArchiveEntry(path: String) = !path.startsWith('/') && !path.startsWith('\\') && !path.contains(':') && path.split('/', '\\').none { it.isBlank() || it == "." || it == ".." }
    private fun safeRelativePath(path: String) = safeArchiveEntry(path.replace('\\', '/')) && !File(path).isAbsolute
    private fun atomicMove(source: File, target: File) = Companion.atomicMove(source, target)
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val JOURNAL = "restore-journal.json"
        private fun atomicMove(source: File, target: File) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            check(!source.exists() && target.isFile) { "Atomic business-file adoption failed" }
        }
        internal const val SCHEMA_VERSION = 19
        private val SUPPORTED_SCHEMA_VERSIONS = (9..SCHEMA_VERSION).toSet()
        const val FORMAT_VERSION = 2
        const val ITERATIONS = 310_000
        const val MAX_PACKAGE_BYTES = 512 * 1024 * 1024
        const val MAX_EXPANDED_BYTES = 1024L * 1024 * 1024
        const val MAX_ENTRIES = 5000
        const val MAX_ROWS_PER_TABLE = 1_000_000
        val MAGIC = byteArrayOf('S'.code.toByte(), 'L'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
        val TABLE_ORDER = listOf(
            "customers", "customer_contacts", "sites", "equipment", "service_plans", "service_obligations",
            "template_snapshots", "checklist_item_snapshots", "working_visits", "work_items",
            "work_item_public_drafts", "work_item_private_drafts", "working_input_buffers", "working_responses", "attachments",
            "follow_ups", "business_profiles", "final_records", "final_record_revisions", "final_work_items",
            "final_checklist_items", "report_renditions", "reusable_templates", "reusable_template_revisions",
            "reusable_template_items", "contact_notes", "follow_up_events", "part_entries", "visit_claims",
            "final_part_entries", "final_photo_entries", "plan_schedule_changes", "visit_schedule_events",
            "correction_drafts", "correction_work_items", "change_entries", "equipment_moves",
            "technician_identity", "trusted_service_loop_ids", "dispatch_technicians", "dispatch_teams", "dispatch_team_members",
            "dispatch_outbox_visits", "dispatch_outbox_visit_teams", "dispatch_outbox_items", "dispatch_outbox_item_assignees",
            "dispatch_visit_bindings", "dispatch_item_bindings", "final_dispatch_visits", "final_dispatch_items",
            "work_result_receipts", "remote_final_results", "remote_result_photos",
            "aggregate_reports", "aggregate_report_sources", "aggregate_report_renditions", "retained_images",
            "data_transfer_bindings", "transferred_final_results", "transferred_evidence", "transferred_history_entries",
            "reminder_preferences", "recovery_metadata",
        )

        /** Resolves a crashed cross-filesystem adoption using the token committed with the database transaction. */
        fun recoverInterrupted(database: ServiceLoopDatabase, fileRoot: File): RecoveryResult {
            val recoveryRoot = File(fileRoot, "recovery"); val journal = File(recoveryRoot, JOURNAL)
            if (!journal.isFile && !File(journal.path + ".pending").isFile) return RecoveryResult.NONE
            val state = runCatching { JSONObject(journal.readText(Charsets.UTF_8)) }.getOrElse {
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
                paths.forEach { OwnedBusinessFiles.resolve(fileRoot, it) }
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
                    val target = OwnedBusinessFiles.resolve(fileRoot, relative)
                    val saved = OwnedBusinessFiles.resolve(File(recoveryRoot, "restore-rollback"), relative)
                    if (saved.isFile) {
                        if (target.exists()) check(target.delete())
                        target.parentFile?.mkdirs()
                        atomicMove(saved, target)
                    } else if (relative in adoptedPaths || (relative == processingPath && !state.optBoolean("processingOriginalExisted", false))) {
                        if (target.exists()) check(target.delete())
                    }
                }
                check(File(recoveryRoot, "restore-candidate").deleteRecursively())
                check(File(recoveryRoot, "restore-rollback").deleteRecursively())
                if (candidateWon && quarantine != null) check(quarantine.deleteRecursively())
                check(journal.delete())
                check(!journal.isFile && !File(journal.path + ".pending").isFile)
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
        private fun safeRelativePathStatic(path: String) = !path.startsWith('/') && !path.startsWith('\\') && !path.contains(':') &&
            path.replace('\\', '/').split('/').none { it.isBlank() || it == "." || it == ".." } && !File(path).isAbsolute &&
            path.substringBefore('/') in OwnedBusinessFiles.roots && '/' in path
    }
}
