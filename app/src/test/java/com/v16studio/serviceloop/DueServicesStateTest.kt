package com.v16studio.serviceloop

import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.domain.*
import com.v16studio.serviceloop.ui.ServiceLoopViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription
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

    @Test fun firstAuthoritativeResultAtomicallyMakesRowsAvailable() = runTest {
        val repository = ObservableDueRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        assertFalse(viewModel.state.value.dueServicesReady)
        repository.updates.emit(listOf(due("P-002"), due("P-003"), due("P-004")))
        assertTrue(viewModel.state.value.dueServicesReady)
        assertEquals(listOf("P-002", "P-003", "P-004"), viewModel.state.value.dueServices.map { it.planReference })
        assertNull(viewModel.state.value.dueServicesError)
    }

    @Test fun repeatedRootAndVisitSetupEntryDoesNotRestartOrInvalidateProjection() = runTest {
        val repository = ObservableDueRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        repository.updates.emit(listOf(due("P-004")))
        repeat(20) { viewModel.loadVisitSetup() }
        assertEquals(1, repository.subscriptions)
        assertTrue(viewModel.state.value.dueServicesReady)
        assertEquals(listOf("P-004"), viewModel.state.value.dueServices.map { it.planReference })
    }

    @Test fun databaseEmissionReplacesProjectionWithNewestTruth() = runTest {
        val repository = ObservableDueRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        repository.updates.emit(listOf(due("P-002")))
        repository.updates.emit(listOf(due("P-003"), due("P-004")))
        assertEquals(listOf("P-003", "P-004"), viewModel.state.value.dueServices.map { it.planReference })
        assertEquals(1, repository.subscriptions)
    }

    @Test fun claimedUnconsumedObligationRemainsInProjection() = runTest {
        val repository = ObservableDueRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        repository.updates.emit(listOf(due("P-003", claimedVisitId = "V-001")))
        assertEquals("V-001", viewModel.state.value.dueServices.single().claimedVisitId)
        assertTrue(viewModel.state.value.dueServicesReady)
    }

    @Test fun authoritativeEmptyIsDistinctFromUnresolved() = runTest {
        val repository = ObservableDueRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        assertFalse(viewModel.state.value.dueServicesReady)
        repository.updates.emit(emptyList())
        assertTrue(viewModel.state.value.dueServicesReady)
        assertTrue(viewModel.state.value.dueServices.isEmpty())
        assertNull(viewModel.state.value.dueServicesError)
    }

    @Test fun collectionFailureAfterSuccessPreservesLastGoodRows() = runTest {
        val repository = FixedFlowDueRepository(flow {
            emit(listOf(due("P-002"), due("P-004")))
            error("projection failed")
        })
        val viewModel = ServiceLoopViewModel(repository) {}
        assertTrue(viewModel.state.value.dueServicesReady)
        assertEquals(listOf("P-002", "P-004"), viewModel.state.value.dueServices.map { it.planReference })
        assertEquals("projection failed", viewModel.state.value.dueServicesError)
    }

    @Test fun initialCollectionFailureSettlesAsUnavailableInsteadOfLoadingForever() = runTest {
        val repository = FixedFlowDueRepository(flow { error("initial failure") })
        val viewModel = ServiceLoopViewModel(repository) {}
        assertFalse(viewModel.state.value.dueServicesReady)
        assertTrue(viewModel.state.value.dueServices.isEmpty())
        assertEquals("initial failure", viewModel.state.value.dueServicesError)
    }

    @Test fun upstreamCancellationSettlesAsUnavailableInsteadOfLoadingForever() = runTest {
        val repository = FixedFlowDueRepository(flow { throw CancellationException("upstream cancelled") })
        val viewModel = ServiceLoopViewModel(repository) {}
        assertFalse(viewModel.state.value.dueServicesReady)
        assertEquals("upstream cancelled", viewModel.state.value.dueServicesError)
    }

    @Test fun workAndNewVisitShareOneCoherentProjection() = runTest {
        val repository = ObservableDueRepository()
        val viewModel = ServiceLoopViewModel(repository) {}
        val expected = listOf(due("P-002"), due("P-003", claimedVisitId = "V-002"), due("P-004"))
        repository.updates.emit(expected)
        viewModel.loadVisitSetup()
        assertEquals(expected, viewModel.state.value.dueServices)
        assertTrue(viewModel.state.value.dueServicesReady)
        assertEquals(1, repository.subscriptions)
    }

    private fun due(reference: String, claimedVisitId: String? = null) = DueService(
        planId = reference, planReference = reference, planName = "Maintenance", dueDate = "2026-09-01",
        obligationId = "obligation-$reference", equipmentId = "equipment-$reference", equipmentReference = "EQ-$reference",
        equipmentName = "Machine", siteId = "site", siteName = "Site", customerId = "customer",
        customerName = "Customer", claimedVisitId = claimedVisitId, bucket = DueBucket.OVERDUE,
    )

    private open class BaseRepository : ServiceLoopRepository {
        override suspend fun home() = HomeSummary(null, null, null, null, null, null, null, 0, null, null, 0, 0)
        override suspend fun equipment(id: String) = null
        override suspend fun equipmentList(): List<EquipmentSummary> = emptyList()
        override suspend fun customerList(): List<CustomerSummary> = emptyList()
        override suspend fun inspection(workItemId: String): InspectionDraft? = null
        override suspend fun completionLines(visitId: String): List<CompletionLine> = emptyList()
        override suspend fun saveResponse(workItemId: String, questionId: String, disposition: ResponseDisposition, value: String?, reason: String?): Long = 0L
    }

    private class ObservableDueRepository : BaseRepository() {
        val updates = MutableSharedFlow<List<DueService>>(extraBufferCapacity = 1)
        var subscriptions = 0
        override fun observeDueServices(): Flow<List<DueService>> = updates.onSubscription { subscriptions++ }
    }

    private class FixedFlowDueRepository(private val values: Flow<List<DueService>>) : BaseRepository() {
        override fun observeDueServices(): Flow<List<DueService>> = values
    }
}
