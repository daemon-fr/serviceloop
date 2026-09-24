package com.v16studio.serviceloop

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OsBackupRulesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val appearance = listOf("sharedpref" to "serviceloop_appearance.xml")

    @Test fun legacyFullBackupAllowsOnlyAppearance() {
        assertEquals(mapOf("full-backup-content" to appearance), includes(R.xml.backup_rules))
    }

    @Test fun cloudAndDeviceTransferEachAllowOnlyAppearance() {
        assertEquals(mapOf("cloud-backup" to appearance, "device-transfer" to appearance),
            includes(R.xml.data_extraction_rules))
    }

    private fun includes(resource: Int): Map<String, List<Pair<String, String>>> {
        val result = linkedMapOf<String, MutableList<Pair<String, String>>>()
        context.resources.getXml(resource).use { parser ->
            var section = ""
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "full-backup-content", "cloud-backup", "device-transfer" -> {
                            section = parser.name
                            result.getOrPut(section) { mutableListOf() }
                        }
                        "include" -> result.getValue(section).add(
                            parser.getAttributeValue(null, "domain") to parser.getAttributeValue(null, "path"))
                        else -> require(parser.name == "data-extraction-rules") { "Unexpected OS backup rule" }
                    }
                }
                parser.next()
            }
        }
        return result
    }
}
