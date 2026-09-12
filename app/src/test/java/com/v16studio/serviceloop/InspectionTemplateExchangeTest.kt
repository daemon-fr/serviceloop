package com.v16studio.serviceloop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.domain.ClockBusinessTime
import com.v16studio.serviceloop.domain.TemplateItemDraft
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InspectionTemplateExchangeTest {
    private lateinit var database: ServiceLoopDatabase
    private lateinit var exchange: InspectionTemplateExchangeService
    private lateinit var repository: RoomServiceLoopRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        exchange = InspectionTemplateExchangeService(database)
        repository = RoomServiceLoopRepository(database, ClockBusinessTime(Clock.fixed(Instant.parse("2026-09-12T08:00:00Z"), ZoneId.of("Europe/Bucharest")), ZoneId.of("Europe/Bucharest")))
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
        val firstImport = exchange.import(conflictPreview, setOf(changed.templates.single().reference))
        assertEquals(1, firstImport.createdSeparateReferences.size)

        val retryPreview = exchange.preview(InspectionTemplateCodec.encode(changed))
        assertEquals(InspectionTemplateImportClassification.EXACT_EXISTING, retryPreview.entries.single().classification)
        val retry = exchange.import(retryPreview)
        assertTrue(retry.importedReferences.isEmpty())
        assertEquals(listOf(changed.templates.single().reference), retry.exactReferences)
        assertEquals(2, database.serviceLoopDao().reusableTemplates().size)
    }
}
