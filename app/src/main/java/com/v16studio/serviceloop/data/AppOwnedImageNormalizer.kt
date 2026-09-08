package com.v16studio.serviceloop.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Produces the privacy-safe bytes ServiceLoop owns for every imported photograph. */
internal object AppOwnedImageNormalizer {
    const val MAX_SOURCE_BYTES = 30 * 1024 * 1024

    data class Result(val bytes: ByteArray, val mimeType: String)

    fun normalize(source: ByteArray): Result {
        require(source.isNotEmpty()) { "The selected image is empty" }
        require(source.size <= MAX_SOURCE_BYTES) { "Choose a photo smaller than 30 MB" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected file is not a readable image" }
        var sample = 1
        while (bounds.outWidth / sample > 5120 || bounds.outHeight / sample > 5120) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(source, 0, source.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("The selected image could not be decoded")
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(source)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val oriented = if (degrees == 0f) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees) }, true)
        val scale = minOf(1f, 2560f / maxOf(oriented.width, oriented.height))
        val resized = if (scale < 1f) Bitmap.createScaledBitmap(oriented, (oriented.width * scale).toInt().coerceAtLeast(1), (oriented.height * scale).toInt().coerceAtLeast(1), true) else oriented
        val output = ByteArrayOutputStream()
        val hasAlpha = resized.hasAlpha()
        check(resized.compress(if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (hasAlpha) 100 else 90, output)) { "The selected image could not be stored" }
        if (resized !== oriented) resized.recycle()
        if (oriented !== decoded) oriented.recycle()
        decoded.recycle()
        return Result(output.toByteArray(), if (hasAlpha) "image/png" else "image/jpeg")
    }
}
