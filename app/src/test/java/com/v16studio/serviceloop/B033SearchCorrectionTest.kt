package com.v16studio.serviceloop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.domain.TemplateItemDraft
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class B033SearchCorrectionTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: ServiceLoopDatabase
    private lateinit var attachmentRoot: File
    private val time = object : BusinessTime {
        override val zoneId = ZoneId.of("Europe/Bucharest")
        override fun instant() = Instant.parse("2026-09-17T09:00:00Z")
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        attachmentRoot = File(context.cacheDir, "b033-search-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        database.close()
        attachmentRoot.deleteRecursively()
    }

    @Test
    fun templateSearchReturnsCurrentRevisionAndStructuredItemCountOnly() = runTest {
        val repository = RoomServiceLoopRepository(database, time, attachmentRoot = attachmentRoot)
        val templateId = repository.createTemplate(
            "Safety checks",
            listOf(
                TemplateItemDraft("Guard intact", "STATUS", required = true),
                TemplateItemDraft("Pressure recorded", "NUMBER", "bar"),
            ),
        )

        val target = repository.search("Safety checks").single { it.type == "TEMPLATE" }

        assertEquals(templateId, target.id)
        assertEquals("IT-001", target.reference)
        assertEquals("Safety checks (v1)", target.title)
        assertEquals("", target.subtitle)
        assertEquals(1, target.revisionNumber)
        assertEquals(2, target.itemCount)
    }
}
