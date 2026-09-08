package com.v16studio.serviceloop

import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.domain.DueBucket
import com.v16studio.serviceloop.domain.DueService
import com.v16studio.serviceloop.domain.HomeSummary
import com.v16studio.serviceloop.domain.CustomerSummary
import com.v16studio.serviceloop.domain.EquipmentSummary
import com.v16studio.serviceloop.domain.InspectionDraft
import com.v16studio.serviceloop.domain.CompletionLine
import com.v16studio.serviceloop.domain.ResponseDisposition
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DueServicesStateTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun cachedDueRowsAreNotASettledStateWhileAuthoritativeReloadIsPending() = runTest {
        val repository = ControlledDueRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        repository.next.complete(listOf(due("P-002")))
        viewModel.loadDueServices()
        assertEquals(listOf("P-002"), viewModel.state.value.dueServices.map { it.planReference })

        repository.next = CompletableDeferred()
        viewModel.loadDueServices()

        assertTrue(viewModel.state.value.dueServicesLoading)
        assertNull(viewModel.state.value.dueServicesError)
        // The old rows remain only as an internal checkpoint; the UI must gate on loading.
        assertEquals(listOf("P-002"), viewModel.state.value.dueServices.map { it.planReference })
        repository.next.complete(emptyList())
        assertFalse(viewModel.state.value.dueServicesLoading)
        assertTrue(viewModel.state.value.dueServices.isEmpty())
    }

    @Test fun reorderedRefreshesKeepNewestAuthoritativeRowsIncludingClaimedObligations() = runTest {
        val repository = ControlledDueRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        val first = CompletableDeferred<List<DueService>>()
        val second = CompletableDeferred<List<DueService>>()
        repository.responses += first
        repository.responses += second

        viewModel.loadDueServices()
        viewModel.loadDueServices()
        second.complete(listOf(due("P-002"), due("P-003", claimedVisitId = "V-001"), due("P-004")))

        assertEquals(listOf("P-002", "P-003", "P-004"), viewModel.state.value.dueServices.map { it.planReference })
        assertEquals("V-001", viewModel.state.value.dueServices.single { it.planReference == "P-003" }.claimedVisitId)
        // Simulate a DAO call which is slow to observe cancellation and returns stale truth.
        first.complete(emptyList())
        assertEquals(listOf("P-002", "P-003", "P-004"), viewModel.state.value.dueServices.map { it.planReference })
        assertFalse(viewModel.state.value.dueServicesLoading)
    }

    @Test fun repeatedAuthoritativeRefreshesRetainCurrentUnconsumedRows() = runTest {
        val repository = ControlledDueRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        repeat(3) {
            repository.responses += CompletableDeferred<List<DueService>>().also { gate -> gate.complete(listOf(due("P-004"))) }
            viewModel.loadDueServices()
            assertEquals(listOf("P-004"), viewModel.state.value.dueServices.map { it.planReference })
        }
    }

    private fun due(reference: String, claimedVisitId: String? = null) = DueService(
        planId = reference, planReference = reference, planName = "Maintenance", dueDate = "2026-09-01",
        obligationId = "obligation-$reference", equipmentId = "equipment-$reference", equipmentReference = "EQ-$reference",
        equipmentName = "Machine", siteId = "site", siteName = "Site", customerId = "customer",
        customerName = "Customer", claimedVisitId = claimedVisitId, bucket = DueBucket.OVERDUE,
    )

    private class ControlledDueRepository : ServiceLoopRepository {
        var next = CompletableDeferred<List<DueService>>()
        val responses = ArrayDeque<CompletableDeferred<List<DueService>>>()
        override suspend fun dueServices(): List<DueService> {
            val response = if (responses.isEmpty()) next else responses.removeFirst()
            return withContext(NonCancellable) { response.await() }
        }
        override suspend fun home() = HomeSummary(null, null, null, null, null, null, null, 0, null, null, 0, 0)
        override suspend fun equipment(id: String) = null
        override suspend fun equipmentList(): List<EquipmentSummary> = emptyList()
        override suspend fun customerList(): List<CustomerSummary> = emptyList()
        override suspend fun inspection(workItemId: String): InspectionDraft? = null
        override suspend fun completionLines(visitId: String): List<CompletionLine> = emptyList()
        override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?): Long = 0L
    }
}
