package com.v16studio.v16service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.v16studio.v16service.data.OwnedBusinessFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OwnedBusinessFilesTest {
    @Test fun allSixDurableRootsAreEnumeratedAndOperationalStagingIsExcluded() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val base = File(context.cacheDir, "owned-roots-${System.nanoTime()}").apply { mkdirs() }
        try {
            val expected = OwnedBusinessFiles.roots.map { root ->
                "$root/synthetic/file.bin".also { relative ->
                    File(base, relative).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
                }
            }
            File(base, "recovery/restore-candidate/staged.bin").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(2)) }
            File(base, "cache/share.bin").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(3)) }
            assertEquals(expected.sorted(), OwnedBusinessFiles.existing(base).sorted())
            expected.forEach { assertEquals(File(base, it).canonicalFile, OwnedBusinessFiles.resolve(base, it)) }
            listOf("reports/../private.bin", "reports//private.bin", "recovery/private.bin", "C:/private.bin", "reports\\private.bin")
                .forEach { unsafe -> assertThrows(IllegalArgumentException::class.java) { OwnedBusinessFiles.resolve(base, unsafe) } }
        } finally { base.deleteRecursively() }
    }
}
