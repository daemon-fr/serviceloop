package com.v16studio.v16service.data

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.sync.withLock

enum class ImageRetention(val months: Int, val title: String) {
    NEVER(0, "Never"), ONE_MONTH(1, "1 month"), THREE_MONTHS(3, "3 months"), SIX_MONTHS(6, "6 months"), ONE_YEAR(12, "1 year");
    companion object { fun fromMonths(value: Int) = entries.firstOrNull { it.months == value } ?: NEVER }
}

data class ImageCleanupResult(val inspected: Int, val derivativesCreated: Int, val originalsRemoved: Int, val retainedBecauseOfError: Int)

/** Bounded opportunistic cleanup; historical photo rows remain immutable. */
class ImageCleanupService(private val context: Context, private val database: V16ServiceDatabase) {
    private val prefs = context.getSharedPreferences("v16service_image_cleanup", Context.MODE_PRIVATE)
    private val dao = database.v16ServiceDao()
    private val root = context.filesDir

    private fun binding(): String? = database.openHelper.readableDatabase
        .query("SELECT datasetId,adoptionToken FROM recovery_metadata WHERE id='primary'").use { cursor ->
            if (!cursor.moveToFirst()) null else {
                val dataset = cursor.getString(0)
                val generation = if (cursor.isNull(1)) "initial" else cursor.getString(1)
                "${dataset.length}:$dataset${generation.length}:$generation"
            }
        }

    fun requiresReconfirmation(): Boolean = prefs.getInt("months", 0) != 0 && prefs.getString("binding", null) != binding()

    fun preference(): ImageRetention = if (prefs.getString("binding", null) == binding()) {
        ImageRetention.fromMonths(prefs.getInt("months", 0))
    } else ImageRetention.NEVER

    fun savePreference(value: ImageRetention) {
        val current = binding() ?: error("Dataset identity is unavailable")
        check(prefs.edit().putInt("months", value.months).putString("binding", current).remove("lastRun").commit()) { "Image cleanup preference was not saved" }
    }

    suspend fun runIfDue(now: Long = System.currentTimeMillis()): ImageCleanupResult? {
        BusinessFileCoordinator.mutex.withLock { reconcilePendingOriginalDeletions(database, root, now) }
        if (preference() == ImageRetention.NEVER) return null
        if (now - prefs.getLong("lastRun", 0) < 86_400_000L) return null
        return runNow(now).also { check(prefs.edit().putLong("lastRun", now).commit()) }
    }

    suspend fun runNow(now: Long = System.currentTimeMillis()): ImageCleanupResult = BusinessFileCoordinator.mutex.withLock {
        runNowLocked(now)
    }

    private suspend fun runNowLocked(now: Long): ImageCleanupResult {
        reconcilePendingOriginalDeletions(database, root, now)
        val choice = preference()
        if (choice == ImageRetention.NEVER) return ImageCleanupResult(0, 0, 0, 0)
        val businessZone = ZoneId.of(dao.businessProfile()?.zoneId ?: "UTC")
        val cutoff = Instant.ofEpochMilli(now).atZone(businessZone).minusMonths(choice.months.toLong()).toInstant().toEpochMilli()
        var inspected = 0; var created = 0; var removed = 0; var errors = 0
        val seen = mutableSetOf<String>()
        val records = dao.allFinalRecords()
        val activeFinalVisits = records.filterNot { it.voided }.map { it.visitId }.toSet()
        val attachmentsByPath = dao.allAttachments().groupBy { it.storedRelativePath }
        val ownersByPath = attachmentsByPath.mapValues { (_, references) -> references.map { "ATTACHMENT:${it.id}" }.toMutableSet() }.toMutableMap()
        val protectedPaths = mutableSetOf<String>()
        attachmentsByPath.forEach { (path, references) ->
            if (references.any { it.ownerType != "WORK_ITEM" || dao.workItem(it.ownerId)?.visitId !in activeFinalVisits })
                protectedPaths += path
        }
        records.forEach { record ->
            dao.finalRevisions(record.id).forEach { revision ->
                dao.finalWorkItems(revision.id).flatMap { dao.finalPhotos(it.id) }.forEach { photo ->
                    val owner = if (attachmentsByPath[photo.storedRelativePath]?.any { it.id == photo.sourceAttachmentId } == true)
                        "ATTACHMENT:${photo.sourceAttachmentId}" else "FINAL_PHOTO:${photo.id}"
                    ownersByPath.getOrPut(photo.storedRelativePath) { mutableSetOf() } += owner
                }
            }
        }
        ownersByPath.filterValues { it.size > 1 }.keys.forEach(protectedPaths::add)
        records.filterNot { it.voided }.forEach { record ->
            val revisions = dao.finalRevisions(record.id)
            val workIds = dao.visitWorkItems(record.visitId).map { it.id }
            val attachments = workIds.flatMap { dao.workItemAttachments(it) }
            attachments.forEach { attachment ->
                val key = "ATTACHMENT:${attachment.id}"
                if (seen.add(key)) {
                    inspected++
                    runCatching { if (attachment.storedRelativePath in protectedPaths) false to false else process("ATTACHMENT", attachment.id, attachment.storedRelativePath, attachment.sha256, attachment.byteSize, maxOf(record.createdAtEpochMillis, ownedFile(attachment.storedRelativePath).lastModified()), cutoff, now) }
                        .onSuccess { (didCreate, didRemove) -> if (didCreate) created++; if (didRemove) removed++ }
                        .onFailure { errors++ }
                }
            }
            val attachmentIds = attachments.map { it.id }.toSet()
            revisions.flatMap { revision -> dao.finalWorkItems(revision.id).flatMap { dao.finalPhotos(it.id) } }.filter { it.sourceAttachmentId !in attachmentIds }.forEach { photo ->
                val key = "FINAL_PHOTO:${photo.id}"
                if (seen.add(key)) {
                    inspected++
                    runCatching { if (photo.storedRelativePath in protectedPaths) false to false else process("FINAL_PHOTO", photo.id, photo.storedRelativePath, photo.sha256, photo.byteSize, maxOf(record.createdAtEpochMillis, ownedFile(photo.storedRelativePath).lastModified()), cutoff, now) }
                        .onSuccess { (didCreate, didRemove) -> if (didCreate) created++; if (didRemove) removed++ }
                        .onFailure { errors++ }
                }
            }
        }
        return ImageCleanupResult(inspected, created, removed, errors)
    }

    private suspend fun process(kind: String, id: String, path: String, hash: String, size: Long, eligibleAt: Long, cutoff: Long, now: Long): Pair<Boolean, Boolean> {
        val original = ownedFile(path)
        if (!original.isFile) {
            return false to false
        }
        if (eligibleAt > cutoff) return false to false
        require(original.length() == size && WorkResultPackageCodec.sha256(original.readBytes()) == hash) { "Original photo failed integrity check" }
        var retained = dao.retainedImage(kind, id)
        var created = false
        if (retained == null) {
            val derivative = AppOwnedImageNormalizer.workResultDerivative(original.readBytes())
            val relative = "retained-images/${UUID.nameUUIDFromBytes("$kind:$id".toByteArray())}.jpg"
            val target = ownedFile(relative)
            require(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
            if (!target.exists()) target.outputStream().use { stream -> stream.write(derivative.bytes); stream.fd.sync() }
            require(target.isFile && target.length() == derivative.bytes.size.toLong() && WorkResultPackageCodec.sha256(target.readBytes()) == WorkResultPackageCodec.sha256(derivative.bytes)) { "Retained photo was not stored safely" }
            retained = RetainedImageEntity(UUID.randomUUID().toString(),kind,id,path,null,relative,WorkResultPackageCodec.sha256(derivative.bytes),derivative.bytes.size.toLong(),derivative.width,derivative.height,"image/jpeg",now)
            dao.insertRetainedImage(retained)
            created = true
        }
        val derivativeFile = ownedFile(retained.derivativeRelativePath)
        require(derivativeFile.isFile && derivativeFile.length() == retained.derivativeByteSize && WorkResultPackageCodec.sha256(derivativeFile.readBytes()) == retained.derivativeSha256) { "Retained photo is unreadable" }
        if (preference() == ImageRetention.NEVER) return created to false
        if (retained.originalDeletionRequestedAtEpochMillis == null) {
            retained = retained.copy(originalDeletionRequestedAtEpochMillis = now)
            dao.updateRetainedImage(retained)
        }
        if (!original.delete() && original.exists()) return created to false
        dao.updateRetainedImage(retained.copy(originalDeletedAtEpochMillis = now))
        return created to true
    }

    private fun ownedFile(path: String): File {
        return OwnedBusinessFiles.resolve(root, path)
    }
}

/** Called with the business-file mutex. A missing original is completed only for a durable intent. */
internal suspend fun reconcilePendingOriginalDeletions(database: V16ServiceDatabase, root: File, now: Long) {
    val dao = database.v16ServiceDao()
    dao.pendingRetainedImageDeletions().forEach { pending ->
        val original = OwnedBusinessFiles.resolve(root, requireNotNull(pending.originalRelativePath) { "Deletion intent has no original path" })
        if (!original.exists()) {
            val derivative = OwnedBusinessFiles.resolve(root, pending.derivativeRelativePath)
            if (derivative.isFile && derivative.length() == pending.derivativeByteSize &&
                WorkResultPackageCodec.sha256(derivative.readBytes()) == pending.derivativeSha256) {
                dao.updateRetainedImage(pending.copy(originalDeletedAtEpochMillis = now))
            }
        }
    }
}
