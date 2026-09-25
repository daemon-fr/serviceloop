package com.v16studio.v16service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.v16service.data.AppOwnedImageNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImageDerivativeInstrumentedTest {
    @Test fun realCodecHonorsRotationAndFlipWithoutCarryingExif() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = File(context.cacheDir, "source-orientation-${System.nanoTime()}.jpg")
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        for (y in 0 until 100) for (x in 0 until 200) bitmap.setPixel(x, y, if (x < 100) Color.RED else Color.BLUE)
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        try {
            fun derivative(orientation: Int): AppOwnedImageNormalizer.TransportDerivative {
                ExifInterface(original.absolutePath).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                    setAttribute(ExifInterface.TAG_MAKE, "private camera metadata")
                    saveAttributes()
                }
                return AppOwnedImageNormalizer.workResultDerivative(original.readBytes())
            }
            val rotated = derivative(ExifInterface.ORIENTATION_ROTATE_90)
            assertEquals(100, rotated.width)
            assertEquals(200, rotated.height)
            assertTrue(rotated.bytes.contentEquals(AppOwnedImageNormalizer.workResultDerivative(original.readBytes()).bytes))
            val outputExif = ExifInterface(ByteArrayInputStream(rotated.bytes))
            assertEquals(null, outputExif.getAttribute(ExifInterface.TAG_MAKE))
            val flipped = derivative(ExifInterface.ORIENTATION_FLIP_HORIZONTAL)
            assertEquals(200, flipped.width)
            assertEquals(100, flipped.height)
            val decoded = BitmapFactory.decodeByteArray(flipped.bytes, 0, flipped.bytes.size)
            try {
                val left = decoded.getPixel(25, 50)
                val right = decoded.getPixel(175, 50)
                assertTrue(Color.blue(left) > Color.red(left))
                assertTrue(Color.red(right) > Color.blue(right))
            } finally { decoded.recycle() }
        } finally { original.delete() }
    }
}
