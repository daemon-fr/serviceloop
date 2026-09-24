package com.v16studio.serviceloop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecoveryLegacyCompatibilityTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: ServiceLoopDatabase
    private lateinit var root: File
    private val password = "synthetic legacy recovery".toCharArray()

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        ServiceLoopDatabase.configureStage4Tracking(database.openHelper.writableDatabase)
        ServiceLoopDatabase.configureReminderDefaults(database.openHelper.writableDatabase)
        root = File(context.cacheDir, "legacy-recovery-${System.nanoTime()}").apply { mkdirs() }
        val dao = database.serviceLoopDao()
        dao.insertCustomers(listOf(CustomerEntity("legacy-customer", "CU-9", "Historical customer")))
        dao.insertSites(listOf(SiteEntity("legacy-site", "legacy-customer", "ST-9", "Historical site", null, null)))
        dao.insertEquipment(listOf(EquipmentEntity("legacy-equipment", "legacy-site", "EQ-9", null, "Historical unit", null, null, null, null)))
        dao.insertPlans(listOf(ServicePlanEntity("legacy-plan", "legacy-equipment", "PL-9", "Annual service", 1, "YEARS", "2026-09-01", "ACTIVE", "legacy-obligation")))
        dao.insertObligations(listOf(ServiceObligationEntity("legacy-obligation", "legacy-plan", 1, "2026-09-01", 1)))
    }

    @After fun teardown() { database.close(); root.deleteRecursively() }

    @Test fun populatedRecoveryVersionsNineThroughNineteenRestoreExactBusinessValues() = runBlocking {
        val recovery = RecoveryPackage(database, root)
        val current = recovery.create(password, false).bytes
        for (version in 9..19) {
            val fixture = sourceFixture(recovery, current, version)
            val inspection = recovery.inspect(fixture, password)
            assertEquals(2, inspection.formatVersion)
            database.serviceLoopDao().renameCustomer("legacy-customer", "Changed after backup")
            recovery.restore(inspection)
            val dao = database.serviceLoopDao()
            assertEquals("Historical customer", dao.customer("legacy-customer")!!.name)
            assertEquals("legacy-customer", dao.site("legacy-site")!!.customerId)
            assertEquals("legacy-site", dao.equipment("legacy-equipment")!!.siteId)
            assertEquals("legacy-equipment", dao.plan("legacy-plan")!!.equipmentId)
            assertEquals("legacy-obligation", dao.plan("legacy-plan")!!.currentObligationId)
            assertEquals("2026-09-01", dao.obligation("legacy-obligation")!!.dueDate)
        }
    }

    @Test fun replacementRemovesUnownedBytesAcrossAllSixRootsButKeepsExternalFiles() = runBlocking {
        val recovery = RecoveryPackage(database, root)
        val backup = recovery.create(password, false)
        val replaced = OwnedBusinessFiles.roots.map { ownedRoot ->
            File(root, "$ownedRoot/stale/unreferenced.bin").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(7)) }
        }
        val external = File(root, "recovery-not-owned.bin").apply { writeBytes(byteArrayOf(9)) }
        database.serviceLoopDao().renameCustomer("legacy-customer", "Changed after backup")
        recovery.restore(recovery.inspect(backup.bytes, password))
        assertEquals("Historical customer", database.serviceLoopDao().customer("legacy-customer")!!.name)
        replaced.forEach { assertEquals(false, it.exists()) }
        assertEquals(true, external.isFile)
        assertEquals(listOf(9.toByte()), external.readBytes().toList())
    }

    @Test fun sharedRenditionPathCoalescesOnlyWhenEveryDescriptorAgrees() = runBlocking {
        val dao = database.serviceLoopDao()
        dao.insertAggregateReport(AggregateReportEntity("shared-report", "legacy-customer", "{}", null, null, null, null, "{}", 1, "ACTIVE"))
        val path = "aggregate-reports/shared/rendition.pdf"
        val bytes = byteArrayOf(1, 2, 3, 4)
        File(root, path).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        val descriptor = AggregateReportRenditionEntity("shared-rendition-a", "shared-report", path,
            sha256(bytes), bytes.size.toLong(), 1, 1, "READY", null)
        dao.insertAggregateRendition(descriptor)
        dao.insertAggregateRendition(descriptor.copy(id = "shared-rendition-b"))
        val recovery = RecoveryPackage(database, root)
        val backup = recovery.create(password, false)
        assertEquals(1, recovery.inspect(backup.bytes, password).files)
        dao.insertAggregateRendition(descriptor.copy(id = "shared-rendition-c", sha256 = "a".repeat(64)))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { recovery.create(password, false) } }
        assertEquals(3, dao.aggregateRenditions("shared-report").size)
        assertEquals(bytes.toList(), File(root, path).readBytes().toList())
    }

    @Test fun duplicateHistoricalTableFailsBeforeReplacingDurableRows() = runBlocking {
        val recovery = RecoveryPackage(database, root)
        val current = recovery.create(password, false).bytes
        val malformed = sourceFixture(recovery, current, 14) { snapshot ->
            val tables = snapshot.getJSONArray("tables")
            tables.put(JSONObject(tables.getJSONObject(0).toString()))
        }
        assertThrows(IllegalArgumentException::class.java) { recovery.inspect(malformed, password) }
        assertEquals("Historical customer", database.serviceLoopDao().customer("legacy-customer")!!.name)
        assertNull(database.serviceLoopDao().customer("unexpected"))
    }

    @Test fun authenticatedZipTamperingAndTruncationRejectWithoutAdoption() = runBlocking {
        val recovery = RecoveryPackage(database, root)
        val current = recovery.create(password, false).bytes
        val plain = invokeCrypt(recovery, "unprotect", current)
        val entries = unzip(plain)
        val malformed = mutableListOf<ByteArray>()
        malformed += current.copyOf(current.size - 1) // invalid GCM tag
        malformed += invokeCrypt(recovery, "protect", plain.copyOf(plain.size - 22)) // missing ZIP directory
        val wrongDirectory = plain.copyOf()
        val footer = wrongDirectory.size - 22
        val firstCentral = java.nio.ByteBuffer.wrap(wrongDirectory, footer + 16, 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        wrongDirectory[firstCentral + 46] = (wrongDirectory[firstCentral + 46].toInt() xor 1).toByte()
        malformed += invokeCrypt(recovery, "protect", wrongDirectory) // local entry and central name disagree
        malformed += invokeCrypt(recovery, "protect", zip(entries - "manifest.json"))
        malformed += invokeCrypt(recovery, "protect", ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { stream ->
                entries.forEach { (name, bytes) ->
                    stream.putNextEntry(ZipEntry(name)); stream.write(bytes); stream.closeEntry()
                }
                stream.putNextEntry(ZipEntry("../outside")); stream.write(byteArrayOf(1)); stream.closeEntry()
            }
        }.toByteArray())
        val wrongPath = entries.toMutableMap()
        val manifest = JSONObject(wrongPath.getValue("manifest.json").toString(Charsets.UTF_8))
            .put("missingFiles", JSONArray().put("../outside")).put("complete", false)
        wrongPath["manifest.json"] = manifest.toString().toByteArray(Charsets.UTF_8)
        malformed += invokeCrypt(recovery, "protect", zip(wrongPath))
        malformed.forEachIndexed { index, bytes ->
            assertThrows("Bad authenticated ZIP variant $index", Exception::class.java) { recovery.inspect(bytes, password) }
            assertEquals("Historical customer", database.serviceLoopDao().customer("legacy-customer")!!.name)
        }
    }

    @Test fun malformedSourceVersionsFamiliesTypesAndAncestryRejectBeforeAdoption() = runBlocking {
        val recovery = RecoveryPackage(database, root)
        val current = recovery.create(password, false).bytes
        fun table(snapshot: JSONObject, name: String): JSONObject {
            val tables = snapshot.getJSONArray("tables")
            return (0 until tables.length()).map(tables::getJSONObject).first { it.getString("name") == name }
        }
        val malformed = listOf(
            sourceFixture(recovery, current, 19) { snapshot -> snapshot.put("schemaVersion", 18) },
            sourceFixture(recovery, current, 20),
            sourceFixture(recovery, current, 19) { snapshot ->
                val tables = snapshot.getJSONArray("tables")
                val index = (0 until tables.length()).first { tables.getJSONObject(it).getString("name") == "customer_contacts" }
                tables.remove(index)
            },
            sourceFixture(recovery, current, 19) { snapshot ->
                table(snapshot, "customers").getJSONArray("rows").getJSONObject(0).put("name", 7)
            },
            sourceFixture(recovery, current, 19) { snapshot ->
                table(snapshot, "sites").getJSONArray("rows").getJSONObject(0).put("customerId", "missing-customer")
            },
        )
        malformed.forEachIndexed { index, bytes ->
            assertThrows("Malformed Recovery variant $index", Exception::class.java) { recovery.inspect(bytes, password) }
            assertEquals("Historical customer", database.serviceLoopDao().customer("legacy-customer")!!.name)
            assertEquals("legacy-customer", database.serviceLoopDao().site("legacy-site")!!.customerId)
        }
    }

    @Test fun incompleteBackupMarksMissingAggregateRenditionWithoutClaimingReadyBytes() = runBlocking {
        val dao = database.serviceLoopDao()
        dao.insertAggregateReport(AggregateReportEntity("aggregate", "legacy-customer", "{}", null, null, null, null, "{}", 1, "ACTIVE"))
        val path = "aggregate-reports/aggregate/rendition.pdf"
        val file = File(root, path).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        dao.insertAggregateRendition(AggregateReportRenditionEntity("rendition", "aggregate", path,
            sha256(file.readBytes()), file.length(), 1, 1, "READY", null))
        file.delete()
        val recovery = RecoveryPackage(database, root)
        val backup = recovery.create(password, true)
        assertEquals(false, backup.complete)
        assertEquals(listOf(path), backup.missingFiles)
        recovery.restore(recovery.inspect(backup.bytes, password))
        val restored = dao.aggregateRenditions("aggregate").single()
        assertEquals("MISSING", restored.status)
        assertEquals(true, restored.failureReason.orEmpty().contains("missing"))
        assertEquals(false, File(root, path).exists())
        assertThrows(IllegalStateException::class.java) { runBlocking { recovery.create(password, false) } }
        val repeated = recovery.create(password, true)
        assertEquals(false, repeated.complete)
        assertEquals(listOf(path), repeated.missingFiles)
        assertEquals("MISSING", dao.aggregateRenditions("aggregate").single().status)
    }

    @Test fun corruptAggregateBytesAreQuarantinedAsIncompleteAcrossRepeatedBackup() = runBlocking {
        val dao = database.serviceLoopDao()
        dao.insertAggregateReport(AggregateReportEntity("corrupt-aggregate", "legacy-customer", "{}", null, null, null, null, "{}", 1, "ACTIVE"))
        val path = "aggregate-reports/corrupt-aggregate/rendition.pdf"
        val file = File(root, path).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        dao.insertAggregateRendition(AggregateReportRenditionEntity("corrupt-rendition", "corrupt-aggregate", path,
            sha256(file.readBytes()), file.length(), 1, 1, "READY", null))
        file.writeBytes(byteArrayOf(3, 2, 1)) // same length, wrong authenticated content
        val recovery = RecoveryPackage(database, root)
        assertThrows(IllegalStateException::class.java) { runBlocking { recovery.create(password, false) } }
        val backup = recovery.create(password, true)
        assertEquals(listOf(path), backup.missingFiles)
        recovery.restore(recovery.inspect(backup.bytes, password))
        assertEquals("MISSING", dao.aggregateRenditions("corrupt-aggregate").single().status)
        assertEquals(false, file.exists())
        assertEquals(listOf(path), recovery.create(password, true).missingFiles)
    }

    @Test fun concurrentBusinessWriteAfterBackupSnapshotRejectsMixedArchive() = runBlocking {
        val racing = RecoveryPackage(database, root) { point ->
            if (point == RecoveryPackage.FailurePoint.AFTER_BACKUP_SNAPSHOT) {
                database.openHelper.writableDatabase.execSQL(
                    "UPDATE customers SET name='Updated during backup' WHERE id='legacy-customer'")
            }
        }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { racing.create(password, false) } }
        assertEquals("Updated during backup", database.serviceLoopDao().customer("legacy-customer")!!.name)
        val stable = RecoveryPackage(database, root).create(password, false)
        assertEquals(true, stable.complete)
    }

    @Test fun orphanAggregateSourceAndRemotePhotoRejectBeforeReplacement() = runBlocking {
        val recovery = RecoveryPackage(database, root)
        val current = recovery.create(password, false).bytes
        fun addRow(snapshot: JSONObject, name: String, row: JSONObject) {
            val tables = snapshot.getJSONArray("tables")
            (0 until tables.length()).map(tables::getJSONObject).first { it.getString("name") == name }
                .getJSONArray("rows").put(row)
        }
        val orphanSource = sourceFixture(recovery, current, 19) { snapshot -> addRow(snapshot, "aggregate_report_sources",
            JSONObject().put("aggregateReportId", "missing-report").put("sourceOrder", 1)
                .put("sourceFinalRevisionId", "missing-revision").put("sourceKind", "LOCAL")
                .put("visitId", "missing-visit").put("sourceEntityId", "missing-revision")) }
        val orphanPhoto = sourceFixture(recovery, current, 19) { snapshot -> addRow(snapshot, "remote_result_photos",
            JSONObject().put("id", "orphan-photo").put("remoteFinalResultId", "missing-result")
                .put("sourcePhotoId", "source-photo").put("relativePath", "remote-results/orphan.jpg")
                .put("sha256", "a".repeat(64)).put("byteSize", 1).put("width", 1).put("height", 1)
                .put("mimeType", "image/jpeg").put("caption", JSONObject.NULL).put("dispatchItemId", "item")
                .put("includeInReport", 1).put("visibility", "PUBLIC")) }
        listOf(orphanSource, orphanPhoto).forEach { bytes ->
            assertThrows(Exception::class.java) { recovery.inspect(bytes, password) }
            assertEquals("Historical customer", database.serviceLoopDao().customer("legacy-customer")!!.name)
        }
    }

    private fun sourceFixture(recovery: RecoveryPackage, encrypted: ByteArray, version: Int, alter: (JSONObject) -> Unit = {}): ByteArray {
        val entries = unzip(invokeCrypt(recovery, "unprotect", encrypted))
        val snapshot = JSONObject(entries.getValue("database.json").toString(Charsets.UTF_8))
        val sourceTables = JSONArray()
        val currentTables = snapshot.getJSONArray("tables")
        for (index in 0 until currentTables.length()) {
            val table = currentTables.getJSONObject(index)
            val name = table.getString("name")
            val columns = runCatching { RecoverySourceShapes.columns(version, name) }.getOrNull() ?: continue
            val sourceRows = JSONArray()
            val currentRows = table.getJSONArray("rows")
            for (rowIndex in 0 until currentRows.length()) {
                val row = currentRows.getJSONObject(rowIndex)
                val projected = JSONObject()
                columns.forEach { column -> projected.put(column, row.get(column)) }
                sourceRows.put(projected)
            }
            sourceTables.put(JSONObject().put("name", name).put("rows", sourceRows))
        }
        snapshot.put("schemaVersion", version).put("tables", sourceTables)
        alter(snapshot)
        val bytes = snapshot.toString().toByteArray(Charsets.UTF_8)
        entries["database.json"] = bytes
        val manifest = JSONObject(entries.getValue("manifest.json").toString(Charsets.UTF_8))
        manifest.put("schemaVersion", version).put("databaseSha256", sha256(bytes))
        entries["manifest.json"] = manifest.toString().toByteArray(Charsets.UTF_8)
        return invokeCrypt(recovery, "protect", zip(entries))
    }

    private fun invokeCrypt(recovery: RecoveryPackage, method: String, bytes: ByteArray): ByteArray =
        RecoveryPackage::class.java.getDeclaredMethod(method, ByteArray::class.java, CharArray::class.java)
            .apply { isAccessible = true }.invoke(recovery, bytes, password) as ByteArray

    private fun unzip(bytes: ByteArray): MutableMap<String, ByteArray> = linkedMapOf<String, ByteArray>().also { entries ->
        ZipInputStream(ByteArrayInputStream(bytes)).use { stream ->
            while (true) {
                val entry = stream.nextEntry ?: break
                entries[entry.name] = stream.readBytes()
            }
        }
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { stream ->
            entries.forEach { (name, bytes) ->
                stream.putNextEntry(ZipEntry(name))
                stream.write(bytes)
                stream.closeEntry()
            }
        }
    }.toByteArray()

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
