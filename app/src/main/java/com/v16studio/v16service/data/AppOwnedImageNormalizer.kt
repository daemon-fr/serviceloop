package com.v16studio.v16service.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Produces the privacy-safe bytes V16 Service owns for every imported photograph. */
internal object AppOwnedImageNormalizer {
    const val MAX_SOURCE_BYTES = 30 * 1024 * 1024

    data class Result(val bytes: ByteArray, val mimeType: String)
    data class TransportDerivative(val bytes: ByteArray, val width: Int, val height: Int)

    /** A bounded, metadata-free JPEG copy for exchange; the app-owned source is untouched. */
    fun workResultDerivative(source: ByteArray): TransportDerivative {
        require(source.isNotEmpty() && source.size <= MAX_SOURCE_BYTES) { "Result photo is missing or too large" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        require(bounds.outWidth in 1..20000 && bounds.outHeight in 1..20000) { "Result photo cannot be decoded safely" }
        var sample = 1
        while (bounds.outWidth / sample > 2400 || bounds.outHeight / sample > 2400) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(source, 0, source.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Result photo could not be decoded")
        try {
            val orientation = runCatching { ExifInterface(ByteArrayInputStream(source)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                }
            }
            val oriented = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            try {
                val scale = minOf(1f, 1200f / maxOf(oriented.width, oriented.height))
                val resized = if (scale < 1f) Bitmap.createScaledBitmap(oriented, (oriented.width * scale).toInt().coerceAtLeast(1), (oriented.height * scale).toInt().coerceAtLeast(1), true) else oriented
                try {
                    val output = ByteArrayOutputStream()
                    require(resized.compress(Bitmap.CompressFormat.JPEG, 75, output)) { "Result photo could not be encoded" }
                    val bytes = output.toByteArray()
                    require(bytes.size in 1..WorkResultPackageCodec.MAX_PHOTO_BYTES) { "Result photo derivative is too large" }
                    return TransportDerivative(bytes, resized.width, resized.height)
                } finally { if (resized !== oriented) resized.recycle() }
            } finally { if (oriented !== decoded) oriented.recycle() }
        } finally { decoded.recycle() }
    }

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
