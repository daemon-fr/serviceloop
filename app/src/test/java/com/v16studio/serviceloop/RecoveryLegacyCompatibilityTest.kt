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
