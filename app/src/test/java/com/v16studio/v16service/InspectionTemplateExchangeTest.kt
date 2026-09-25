package com.v16studio.v16service

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.v16service.data.*
import com.v16studio.v16service.domain.ClockBusinessTime
import com.v16studio.v16service.domain.TemplateItemDraft
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InspectionTemplateExchangeTest {
    private lateinit var database: V16ServiceDatabase
    private lateinit var exchange: InspectionTemplateExchangeService
    private lateinit var repository: RoomV16ServiceRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, V16ServiceDatabase::class.java).allowMainThreadQueries().build()
        exchange = InspectionTemplateExchangeService(database)
        repository = RoomV16ServiceRepository(database, ClockBusinessTime(Clock.fixed(Instant.parse("2026-09-12T08:00:00Z"), ZoneId.of("Europe/Bucharest")), ZoneId.of("Europe/Bucharest")))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun currentOnlyCodecRoundTripsAndConflictSeparateImportIsIdempotent() = runTest {
        val templateId = repository.createTemplate("Safety checks", listOf(TemplateItemDraft("Guard intact", "STATUS", required = true)))
        val exported = exchange.export(setOf(templateId))
        val encoded = InspectionTemplateCodec.encode(exported)
        assertEquals(InspectionTemplateImportClassification.EXACT_EXISTING, exchange.preview(encoded).entries.single().classification)

        val changed = exported.copy(templates = exported.templates.map { it.copy(name = "Safety checks received", fingerprint = "") })
        val conflictPreview = exchange.preview(InspectionTemplateCodec.encode(changed))
        assertEquals(InspectionTemplateImportClassification.CONFLICT, conflictPreview.entries.single().classification)
        val firstImport = importTrusted(conflictPreview, setOf(changed.templates.single().reference))
        assertEquals(1, firstImport.createdSeparateReferences.size)

        val retryPreview = exchange.preview(InspectionTemplateCodec.encode(changed))
        assertEquals(InspectionTemplateImportClassification.EXACT_EXISTING, retryPreview.entries.single().classification)
        val retry = importTrusted(retryPreview)
        assertTrue(retry.importedReferences.isEmpty())
        assertEquals(listOf(changed.templates.single().reference), retry.exactReferences)
        assertEquals(2, database.v16ServiceDao().reusableTemplates().size)
    }

    @Test
    fun templateMutationRequiresTrustedExporterEvenForRetry() = runTest {
        val templateId = repository.createTemplate("Safety checks", listOf(TemplateItemDraft("Guard intact", "STATUS", required = true)))
        val exported = exchange.export(setOf(templateId))
        val changed = exported.copy(templates = exported.templates.map { it.copy(name = "Received checks", fingerprint = "") })
        val preview = exchange.preview(InspectionTemplateCodec.encode(changed))
        val peerId = TechnicianIdCodec.generate()
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { exchange.import(preview, peerId, setOf(changed.templates.single().reference)) } }
        assertEquals(1, database.v16ServiceDao().reusableTemplates().size)
        val trust = V16ServicePeerTrustStore(database)
        trust.add(peerId, "Office")
        exchange.import(preview, peerId, setOf(changed.templates.single().reference))
        trust.remove(peerId)
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { exchange.import(preview, peerId, setOf(changed.templates.single().reference)) } }
        assertEquals(2, database.v16ServiceDao().reusableTemplates().size)
    }

    @Test
    fun multipleConflictsRequireEveryCreateSeparateDecision() = runTest {
        val preview = twoConflictPreview()
        val references = preview.entries.map { it.transfer.reference }

        assertFalse(preview.canImport(emptySet()))
        assertFalse(preview.canImport(setOf(references.first())))
        assertTrue(preview.canImport(references.toSet()))
    }

    @Test
    fun importingMultipleConflictsCreatesBothAndRetryIsExact() = runTest {
        val preview = twoConflictPreview()
        val references = preview.entries.map { it.transfer.reference }
        val result = importTrusted(preview, references.toSet())

        assertEquals(2, result.importedReferences.size)
        assertEquals(2, result.createdSeparateReferences.size)
        assertEquals(4, database.v16ServiceDao().reusableTemplates().size)

        val retryPreview = exchange.preview(InspectionTemplateCodec.encode(preview.value))
        assertTrue(retryPreview.entries.all { it.classification == InspectionTemplateImportClassification.EXACT_EXISTING })
        val retry = importTrusted(retryPreview)
        assertTrue(retry.importedReferences.isEmpty())
        assertEquals(references, retry.exactReferences)
        assertEquals(4, database.v16ServiceDao().reusableTemplates().size)
    }

    @Test
    fun unresolvedMultipleConflictImportFailsBeforeAnyTemplateIsPersisted() = runTest {
        val preview = twoConflictPreview()
        val references = preview.entries.map { it.transfer.reference }
        val failure = assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { importTrusted(preview, setOf(references.first())) }
        }

        assertTrue(failure.message.orEmpty().contains("Resolve inspection template conflicts"))
        assertEquals(2, database.v16ServiceDao().reusableTemplates().size)
    }

    private suspend fun importTrusted(preview: InspectionTemplateImportPreview, createSeparate: Set<String> = emptySet()) =
        exchange.import(preview, V16ServicePeerTrustStore(database).localIdentity().technicianId, createSeparate)

    private suspend fun twoConflictPreview(): InspectionTemplateImportPreview {
        val first = repository.createTemplate("Safety checks", listOf(TemplateItemDraft("Guard intact", "STATUS", required = true)))
        val second = repository.createTemplate("Pressure checks", listOf(TemplateItemDraft("Pressure recorded", "NUMBER", "bar", true)))
        val exported = exchange.export(setOf(first, second))
        val changed = exported.copy(templates = exported.templates.map { it.copy(name = "${it.name} received") })
        return exchange.preview(InspectionTemplateCodec.encode(changed))
    }
}
