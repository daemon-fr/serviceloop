package com.v16studio.serviceloop.data

import android.graphics.BitmapFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Called while the business-file mutex is held; immutable final source bytes get one transport representation. */
internal class CanonicalFinalPhotoDerivative(private val database: ServiceLoopDatabase, private val root: File) {
    private val dao = database.serviceLoopDao()

    suspend fun bytes(photo: FinalPhotoEntryEntity): AppOwnedImageNormalizer.TransportDerivative {
        val attachment = dao.attachment(photo.sourceAttachmentId)?.takeIf { it.storedRelativePath == photo.storedRelativePath }
        val kind = if (attachment != null) "ATTACHMENT" else "FINAL_PHOTO"
        val id = attachment?.id ?: photo.id
        val retained = dao.retainedImage(kind, id)
        if (retained != null) {
            require(retained.originalRelativePath == photo.storedRelativePath) { "Retained photo belongs to another original" }
            return verified(retained)
        }
        val original = OwnedBusinessFiles.resolve(root, photo.storedRelativePath)
        require(original.isFile && original.length() == photo.byteSize && photo.byteSize in 1..AppOwnedImageNormalizer.MAX_SOURCE_BYTES.toLong()) {
            "Final photo original is missing"
        }
        val source = original.readBytes()
        require(WorkResultPackageCodec.sha256(source) == photo.sha256) { "Final photo original changed" }
        val derivative = AppOwnedImageNormalizer.workResultDerivative(source)
        val derivativeHash = WorkResultPackageCodec.sha256(derivative.bytes)
        val fileId = UUID.randomUUID().toString()
        val relative = "retained-images/$fileId.jpg"
        val target = OwnedBusinessFiles.resolve(root, relative)
        require(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
        val staged = File(target.parentFile, ".$fileId.tmp")
        try {
            staged.outputStream().use { stream -> stream.write(derivative.bytes); stream.fd.sync() }
            require(staged.length() == derivative.bytes.size.toLong() && WorkResultPackageCodec.sha256(staged.readBytes()) == derivativeHash)
            Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            dao.insertRetainedImage(RetainedImageEntity(fileId, kind, id, photo.storedRelativePath, null,
                relative, derivativeHash, derivative.bytes.size.toLong(), derivative.width, derivative.height, "image/jpeg", System.currentTimeMillis()))
            return derivative
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                staged.delete()
                if (dao.retainedImage(kind, id)?.derivativeRelativePath != relative) target.delete()
            }
            throw failure
        }
    }

    private fun verified(retained: RetainedImageEntity): AppOwnedImageNormalizer.TransportDerivative {
        require(retained.derivativeMimeType == "image/jpeg" && retained.derivativeWidth in 1..1200 && retained.derivativeHeight in 1..1200)
        val file = OwnedBusinessFiles.resolve(root, retained.derivativeRelativePath)
        require(file.isFile && file.length() == retained.derivativeByteSize && retained.derivativeByteSize in 1..WorkResultPackageCodec.MAX_PHOTO_BYTES.toLong()) {
            "Retained final photo is missing"
        }
        val bytes = file.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(WorkResultPackageCodec.sha256(bytes) == retained.derivativeSha256 && bytes.size >= 4 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
            bounds.outWidth == retained.derivativeWidth && bounds.outHeight == retained.derivativeHeight) {
            "Retained final photo failed verification"
        }
        return AppOwnedImageNormalizer.TransportDerivative(bytes, retained.derivativeWidth, retained.derivativeHeight)
    }
}
