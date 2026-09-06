package com.v16studio.serviceloop

import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.domain.CompletionLine
import com.v16studio.serviceloop.domain.CustomerSummary
import com.v16studio.serviceloop.domain.EquipmentDetail
import com.v16studio.serviceloop.domain.EquipmentSummary
import com.v16studio.serviceloop.domain.HomeSummary
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.InspectionQuestion
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.domain.SaveStatus
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StateSemanticsTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun unansweredAndNotCheckedAreDistinctFromPassing() {
        assertTrue(ResponseDisposition.UNANSWERED != ResponseDisposition.OK)
        assertTrue(ResponseDisposition.NOT_CHECKED != ResponseDisposition.OK)
        assertFalse(ResponseDisposition.NOT_CHECKED.name.contains("OK"))
    }

    @Test fun viewModelReportsFailedNotSavedAfterPersistenceFailure() = runTest {
        val draft = inspectionDraft()
        val repository = object : ServiceLoopRepository {
            override suspend fun home() = HomeSummary(null, null, null, null, null, null, null, 0, null, null, 0, 0)
            override suspend fun equipment(id: String): EquipmentDetail? = null
            override suspend fun equipmentList(): List<EquipmentSummary> = emptyList()
            override suspend fun customerList(): List<CustomerSummary> = emptyList()
            override suspend fun inspection(workItemId: String): InspectionDraft = draft
            override suspend fun completionLines(visitId: String): List<CompletionLine> = emptyList()
            override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?): Long = error("database unavailable")
        }
        val viewModel = ServiceLoopViewModel(repository) {}
        viewModel.loadInspection("work-1")
        viewModel.saveResponse("check-1", ResponseDisposition.OK)
        val status = viewModel.state.value.saveStatus
        assertTrue(status is SaveStatus.Failed)
        assertEquals(100L, (status as SaveStatus.Failed).lastSavedAtEpochMillis)
    }

    @Test fun releaseFactoryIsNoOpAndProductionSourceContainsNoFixtureCustomer() {
        val releaseFactory = File("src/release/java/com/v16studio/serviceloop/FixtureSeederFactory.kt").readText()
        val productionSources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertTrue(releaseFactory.contains("NoOpStartupSeeder"))
        assertFalse(releaseFactory.contains("DebugFixtureSeeder"))
        assertFalse(productionSources.contains("Harbor Fitness and Rehabilitation Cooperative"))
    }

    private fun inspectionDraft() = InspectionDraft(
        "work-1", "visit-1", "V-001", "Captured site", "Captured machine", "EQ-001", "Inspection",
        "2026-09-01", "Every 3 months", 2, "Public", "Private", false, null, null, 100,
        listOf(InspectionQuestion("response-1", "check-1", 1, "Guard", "STATUS", null, true, ResponseDisposition.NOT_CHECKED, null, null, null)),
    )
}
