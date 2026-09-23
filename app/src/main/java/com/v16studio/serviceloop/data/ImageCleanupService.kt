package com.v16studio.serviceloop.data

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

enum class ImageRetention(val months: Int, val title: String) {
    NEVER(0, "Never"), ONE_MONTH(1, "1 month"), THREE_MONTHS(3, "3 months"), SIX_MONTHS(6, "6 months"), ONE_YEAR(12, "1 year");
    companion object { fun fromMonths(value: Int) = entries.firstOrNull { it.months == value } ?: NEVER }
}

data class ImageCleanupResult(val inspected: Int, val derivativesCreated: Int, val originalsRemoved: Int, val retainedBecauseOfError: Int)

/** Bounded opportunistic cleanup; historical photo rows remain immutable. */
class ImageCleanupService(private val context: Context, private val database: ServiceLoopDatabase) {
    private val prefs = context.getSharedPreferences("serviceloop_image_cleanup", Context.MODE_PRIVATE)
    private val dao = database.serviceLoopDao()
    private val root = context.filesDir

    fun preference(): ImageRetention = ImageRetention.fromMonths(prefs.getInt("months", 0))

    fun savePreference(value: ImageRetention) {
        check(prefs.edit().putInt("months", value.months).commit()) { "Image cleanup preference was not saved" }
    }

    suspend fun runIfDue(now: Long = System.currentTimeMillis()): ImageCleanupResult? {
        if (preference() == ImageRetention.NEVER) return null
        if (now - prefs.getLong("lastRun", 0) < 86_400_000L) return null
        return runNow(now).also { check(prefs.edit().putLong("lastRun", now).commit()) }
    }

    suspend fun runNow(now: Long = System.currentTimeMillis()): ImageCleanupResult {
        val choice = preference()
        if (choice == ImageRetention.NEVER) return ImageCleanupResult(0, 0, 0, 0)
        val cutoff = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).minusMonths(choice.months.toLong()).toInstant().toEpochMilli()
        var inspected = 0; var created = 0; var removed = 0; var errors = 0
        val seen = mutableSetOf<String>()
        dao.allFinalRecords().filterNot { it.voided }.forEach { record ->
            val revisions = dao.finalRevisions(record.id)
            val workIds = dao.visitWorkItems(record.visitId).map { it.id }
            val attachments = workIds.flatMap { dao.workItemAttachments(it) }
            attachments.forEach { attachment ->
                val key = "ATTACHMENT:${attachment.id}"
                if (seen.add(key)) {
                    inspected++
                    runCatching { process("ATTACHMENT", attachment.id, attachment.storedRelativePath, attachment.sha256, attachment.byteSize, maxOf(record.createdAtEpochMillis, ownedFile(attachment.storedRelativePath).lastModified()), cutoff, now) }
                        .onSuccess { (didCreate, didRemove) -> if (didCreate) created++; if (didRemove) removed++ }
                        .onFailure { errors++ }
                }
            }
            val attachmentIds = attachments.map { it.id }.toSet()
            revisions.flatMap { revision -> dao.finalWorkItems(revision.id).flatMap { dao.finalPhotos(it.id) } }.filter { it.sourceAttachmentId !in attachmentIds }.forEach { photo ->
                val key = "FINAL_PHOTO:${photo.id}"
                if (seen.add(key)) {
                    inspected++
                    runCatching { process("FINAL_PHOTO", photo.id, photo.storedRelativePath, photo.sha256, photo.byteSize, maxOf(record.createdAtEpochMillis, ownedFile(photo.storedRelativePath).lastModified()), cutoff, now) }
                        .onSuccess { (didCreate, didRemove) -> if (didCreate) created++; if (didRemove) removed++ }
                        .onFailure { errors++ }
                }
            }
        }
        return ImageCleanupResult(inspected, created, removed, errors)
    }

    private suspend fun process(kind: String, id: String, path: String, hash: String, size: Long, eligibleAt: Long, cutoff: Long, now: Long): Pair<Boolean, Boolean> {
        if (eligibleAt > cutoff) return false to false
        val original = ownedFile(path)
        if (!original.isFile) return false to false
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
        if (!original.delete()) return created to false
        dao.updateRetainedImage(retained.copy(originalDeletedAtEpochMillis = now))
        return created to true
    }

    private fun ownedFile(path: String): File {
        val base = root.canonicalFile
        val file = File(base, path).canonicalFile
        require(file.path.startsWith(base.path + File.separator)) { "Unsafe owned image path" }
        return file
    }
}
