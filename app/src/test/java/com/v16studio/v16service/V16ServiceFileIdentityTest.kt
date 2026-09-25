package com.v16studio.v16service

import android.content.ContentResolver
import android.net.Uri
import com.v16studio.v16service.data.V16_SERVICE_SYNC_MIME
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class V16ServiceFileIdentityTest {
    private val opaque = Uri.parse("content://provider/documents/id%3A7128")

    @Test fun acceptsDeclaredOrResolvedNativeMime() {
        assertTrue(isV16ServiceFileIdentity(opaque, V16_SERVICE_SYNC_MIME, null, null))
        assertTrue(isV16ServiceFileIdentity(opaque, "application/octet-stream", V16_SERVICE_SYNC_MIME, null))
        assertTrue(isV16ServiceFileIdentity(opaque, "$V16_SERVICE_SYNC_MIME; charset=binary", null, null))
    }

    @Test fun opaqueContentUriUsesProviderDisplayName() {
        assertFalse(isV16ServiceFileIdentity(opaque, "application/octet-stream", "application/octet-stream", null))
        assertTrue(isV16ServiceFileIdentity(opaque, "application/octet-stream", "application/octet-stream", "assignment.V16SERVICE"))
    }

    @Test fun nonContentUriCanUseItsFilenameButUnknownContentCannotUsePath() {
        assertTrue(isV16ServiceFileIdentity(Uri.parse("file:///downloads/assignment.v16service"), null, null, null))
        assertFalse(isV16ServiceFileIdentity(Uri.parse("content://provider/documents/assignment.v16service"), null, null, null))
    }

    @Test fun unrelatedMimeAndFilenameAreRejected() {
        assertFalse(isV16ServiceFileIdentity(opaque, "application/zip", "application/zip", "archive.zip"))
    }
}
