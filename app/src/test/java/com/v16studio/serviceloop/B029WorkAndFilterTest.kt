package com.v16studio.serviceloop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.RoomServiceLoopRepository
import com.v16studio.serviceloop.data.ServiceLoopDatabase
import com.v16studio.serviceloop.domain.CustomerInput
import com.v16studio.serviceloop.domain.DueServiceDateFilter
import com.v16studio.serviceloop.domain.DueServiceVisitFilter
import com.v16studio.serviceloop.domain.FollowUpDateFilter
import com.v16studio.serviceloop.domain.FollowUpStatusFilter
import com.v16studio.serviceloop.domain.PlanInput
import com.v16studio.serviceloop.domain.SiteInput
import com.v16studio.serviceloop.domain.EquipmentInput
import com.v16studio.serviceloop.domain.TemplateItemDraft
import com.v16studio.serviceloop.domain.VisitDateFilter
import com.v16studio.serviceloop.domain.VisitStatusFilter
import com.v16studio.serviceloop.domain.BusinessTime
import com.v16studio.serviceloop.ui.UiFilterPreferences
import java.io.File
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
class B029WorkAndFilterTest {
    private lateinit var db: ServiceLoopDatabase
    private lateinit var root: File
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val time = object : BusinessTime {
        override val zoneId = ZoneId.of("Europe/Bucharest")
        override fun instant() = Instant.parse("2026-09-05T10:00:00Z")
    }
    private val repo get() = RoomServiceLoopRepository(db, time, attachmentRoot = root)

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        ServiceLoopDatabase.configureStage4Tracking(db.openHelper.writableDatabase)
        root = File(context.cacheDir, "b029-${System.nanoTime()}").apply { mkdirs() }
        context.getSharedPreferences("serviceloop_ui_filter_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun close() {
        db.close()
        root.deleteRecursively()
        context.getSharedPreferences("serviceloop_ui_filter_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun filterMemorySurvivesScreenReentryAndIsPresentationOnly() {
        val first = UiFilterPreferences(context)
        first.saveDueDate(DueServiceDateFilter.DUE_SOON)
        first.saveDueVisit(DueServiceVisitFilter.HAS_VISIT)
        first.saveVisitDate(VisitDateFilter.PAST_30_DAYS)
        first.saveVisitStatus(VisitStatusFilter.WORKING)
        first.saveFollowUpDate(FollowUpDateFilter.UPCOMING)
        first.saveFollowUpStatus(FollowUpStatusFilter.CLOSED)
        first.saveShowOneTimeCustomers(true)

        val reentered = UiFilterPreferences(context)
        assertEquals(DueServiceDateFilter.DUE_SOON, reentered.dueDate(DueServiceDateFilter.ALL))
        assertEquals(DueServiceVisitFilter.HAS_VISIT, reentered.dueVisit(DueServiceVisitFilter.ALL))
        assertEquals(VisitDateFilter.PAST_30_DAYS, reentered.visitDate(VisitDateFilter.ALL))
        assertEquals(VisitStatusFilter.WORKING, reentered.visitStatus(VisitStatusFilter.ALL))
        assertEquals(FollowUpDateFilter.UPCOMING, reentered.followUpDate(FollowUpDateFilter.ALL))
        assertEquals(FollowUpStatusFilter.CLOSED, reentered.followUpStatus(FollowUpStatusFilter.ALL))
        assertTrue(reentered.showOneTimeCustomers())
    }

    @Test fun disabledTemplateRemainsUsableByExistingPlanButCannotBeAssignedOrDeleted() = runTest {
        val customer = repo.createCustomer(CustomerInput("B029 customer"))
        val site = repo.createSite(customer, SiteInput("Main site", "B029 address"))
        val equipment = repo.createEquipment(site, EquipmentInput("Pump"))
        val template = repo.createTemplate("Inspection", listOf(TemplateItemDraft("Guard", "STATUS", required = true)))
        val plan = repo.createPlan(equipment, PlanInput("Annual service", 1, "YEARS", "2026-09-01", template))

        repo.setTemplateState(template, "DISABLED")
        repo.updatePlan(plan, PlanInput("Annual service", 1, "YEARS", "2026-09-01", template))
        val visit = repo.createVisit(listOf(plan), "WORKING", "2026-09-05")
        assertEquals(1, repo.inspection(db.serviceLoopDao().firstWorkItemId(visit)!!)?.questions?.size)

        val assignmentFailure = runCatching { repo.createPlan(equipment, PlanInput("Second service", 1, "YEARS", "2027-09-01", template)) }.exceptionOrNull()
        assertEquals("Template is unavailable", assignmentFailure?.message)
        assertEquals(1, repo.templateServicePlanReferenceCount(template))

        val deleteFailure = runCatching { repo.deleteTemplate(template) }.exceptionOrNull()
        assertTrue(deleteFailure?.message.orEmpty().contains("This template is still used by a service plan"))
        repo.updatePlan(plan, PlanInput("Annual service", 1, "YEARS", "2026-09-01", null))
        repo.deleteTemplate(template)
        assertTrue(repo.templates().none { it.id == template })
    }
}
