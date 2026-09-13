package com.v16studio.serviceloop.ui.service

import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.data.isFiniteSignedDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

data class ServiceDraftFieldId(val workItemId: String, val fieldKey: String)

sealed interface ServiceDraftFieldState {
    data class Clean(val savedAtEpochMillis: Long?) : ServiceDraftFieldState
    data class Pending(val rawValue: String) : ServiceDraftFieldState
    data object Saving : ServiceDraftFieldState
    data class Invalid(val rawValue: String, val message: String) : ServiceDraftFieldState
    data class Failed(val rawValue: String, val message: String, val lastSavedAtEpochMillis: Long?) : ServiceDraftFieldState
}

data class ServiceDraftFlushResult(
    val success: Boolean,
    val pendingFields: Set<ServiceDraftFieldId>,
    val invalidFields: Set<ServiceDraftFieldId>,
    val failedFields: Set<ServiceDraftFieldId>,
)

data class ServiceDraftValidation(val message: String? = null) {
    val valid: Boolean get() = message == null
}

object ServiceDraftValidators {
    fun alwaysValid(): (String) -> ServiceDraftValidation = { ServiceDraftValidation() }

    fun privateNote(): (String) -> ServiceDraftValidation = { raw ->
        if (raw.trim().length <= 5_000) ServiceDraftValidation() else ServiceDraftValidation("Private note is too long")
    }

    fun requiredText(label: String): (String) -> ServiceDraftValidation = { raw ->
        if (raw.trim().isNotEmpty()) ServiceDraftValidation() else ServiceDraftValidation("$label is required")
    }

    fun issueDescription(): (String) -> ServiceDraftValidation = alwaysValid()
    fun notApplicableReason(): (String) -> ServiceDraftValidation = alwaysValid()
    fun notPerformedReason(): (String) -> ServiceDraftValidation = { raw ->
        if (raw.trim().isNotEmpty()) ServiceDraftValidation() else ServiceDraftValidation("Not performed reason is required")
    }

    fun number(): (String) -> ServiceDraftValidation = { raw ->
        val value = raw.trim()
        when {
            value.isEmpty() -> ServiceDraftValidation("A number is required")
            value in setOf("-", "+", ".", "-.", "+.") || value.endsWith('.') -> ServiceDraftValidation("Enter a complete number")
            isFiniteSignedDecimal(value) -> ServiceDraftValidation()
            else -> ServiceDraftValidation("Enter a signed decimal number, for example -12.5")
        }
    }

    fun isoDate(): (String) -> ServiceDraftValidation = { raw ->
        if (runCatching { LocalDate.parse(raw.trim()) }.isSuccess) ServiceDraftValidation() else ServiceDraftValidation("Enter a date as YYYY-MM-DD")
    }
}

class ServiceDraftAutosaveCoordinator(
    private val repository: ServiceLoopRepository,
    private val scope: CoroutineScope,
    private val debounceMillis: Long = 300L,
) {
    private data class TextOperation(
        val rawValue: String,
        val validator: (String) -> ServiceDraftValidation,
        val writer: (suspend (String) -> Long)?,
        val onSaved: suspend (Long) -> Unit = {},
    )

    private data class ChoiceOperation(
        val rawValue: String,
        val writer: suspend (String) -> Long,
        val onSaved: suspend (Long) -> Unit,
    )

    private val stateLock = Any()
    private val versions = mutableMapOf<ServiceDraftFieldId, Long>()
    private val latestOperations = mutableMapOf<ServiceDraftFieldId, TextOperation>()
    private val latestChoices = mutableMapOf<ServiceDraftFieldId, ChoiceOperation>()
    private val jobs = mutableMapOf<ServiceDraftFieldId, Job>()
    private val savedAt = mutableMapOf<ServiceDraftFieldId, Long?>()
    private val canonicalWriteMutex = Mutex()
    private val bufferWriteMutex = Mutex()
    private val _states = MutableStateFlow<Map<ServiceDraftFieldId, ServiceDraftFieldState>>(emptyMap())
    val states: StateFlow<Map<ServiceDraftFieldId, ServiceDraftFieldState>> = _states.asStateFlow()

    fun scheduleText(
        fieldId: ServiceDraftFieldId,
        rawValue: String,
        validator: (String) -> ServiceDraftValidation = ServiceDraftValidators.alwaysValid(),
        writer: suspend (String) -> Long,
    ) {
        val operation = TextOperation(rawValue, validator, writer)
        val version = synchronized(stateLock) {
            val next = versions.getOrDefault(fieldId, 0L) + 1L
            versions[fieldId] = next
            latestOperations[fieldId] = operation
            latestChoices.remove(fieldId)
            jobs[fieldId]?.cancel()
            jobs[fieldId] = scope.launch { persistText(fieldId, next, operation, immediate = false) }
            next
        }
        check(version > 0)
    }

    fun scheduleRaw(
        fieldId: ServiceDraftFieldId,
        rawValue: String,
        validator: (String) -> ServiceDraftValidation = ServiceDraftValidators.alwaysValid(),
    ) {
        val version = synchronized(stateLock) {
            val next = versions.getOrDefault(fieldId, 0L) + 1L
            versions[fieldId] = next
            latestOperations[fieldId] = TextOperation(rawValue, validator, null)
            latestChoices.remove(fieldId)
            jobs[fieldId]?.cancel()
            jobs[fieldId] = scope.launch { persistText(fieldId, next, latestOperations[fieldId]!!, immediate = true) }
            next
        }
        check(version > 0)
    }

    fun saveTextNow(
        fieldId: ServiceDraftFieldId,
        rawValue: String,
        validator: (String) -> ServiceDraftValidation = ServiceDraftValidators.alwaysValid(),
        writer: suspend (String) -> Long,
        onSaved: suspend (Long) -> Unit = {},
    ) {
        val operation = TextOperation(rawValue, validator, writer, onSaved)
        val version = synchronized(stateLock) {
            val next = versions.getOrDefault(fieldId, 0L) + 1L
            versions[fieldId] = next
            latestOperations[fieldId] = operation
            latestChoices.remove(fieldId)
            jobs[fieldId]?.cancel()
            jobs[fieldId] = scope.launch { persistText(fieldId, next, operation, immediate = true) }
            next
        }
        check(version > 0)
    }

    fun immediateChoice(
        fieldId: ServiceDraftFieldId,
        rawValue: String,
        writer: suspend (String) -> Long,
    ) = immediateChoice(fieldId, rawValue, writer, {})

    fun immediateChoice(
        fieldId: ServiceDraftFieldId,
        rawValue: String,
        writer: suspend (String) -> Long,
        onSaved: suspend (Long) -> Unit,
    ) {
        val version = synchronized(stateLock) {
            val next = versions.getOrDefault(fieldId, 0L) + 1L
            versions[fieldId] = next
            latestOperations.remove(fieldId)
            latestChoices[fieldId] = ChoiceOperation(rawValue, writer, onSaved)
            jobs[fieldId]?.cancel()
            jobs[fieldId] = scope.launch { persistChoice(fieldId, next, rawValue, writer, onSaved) }
            next
        }
        check(version > 0)
    }

    suspend fun flush(workItemId: String): ServiceDraftFlushResult {
        val persisted = repository.workingInputBuffers(workItemId)
            .mapKeys { (fieldKey, _) -> ServiceDraftFieldId(workItemId, fieldKey) }
        val tracked = synchronized(stateLock) { versions.keys.filter { it.workItemId == workItemId }.toSet() }
        val relevant = tracked + persisted.keys
        persisted.forEach { (fieldId, rawValue) ->
            val hasCurrentOperation = synchronized(stateLock) {
                latestOperations.containsKey(fieldId) || latestChoices.containsKey(fieldId)
            }
            if (!hasCurrentOperation) setState(fieldId, ServiceDraftFieldState.Pending(rawValue))
        }
        val waitingJobs = mutableListOf<Job>()
        val immediateJobs = relevant.mapNotNull { fieldId ->
            val operation = synchronized(stateLock) { latestOperations[fieldId] } ?: return@mapNotNull null
            val validation = operation.validator(operation.rawValue)
            if (!validation.valid) {
                setState(fieldId, ServiceDraftFieldState.Invalid(operation.rawValue, validation.message!!))
                null
            } else if (currentState(fieldId) is ServiceDraftFieldState.Failed || operation.writer == null) {
                null
            } else {
                synchronized(stateLock) { jobs[fieldId]?.cancel() }
                val version = synchronized(stateLock) { versions[fieldId] ?: return@mapNotNull null }
                scope.launch { persistText(fieldId, version, operation, immediate = true) }
            }
        }
        relevant.filter { synchronized(stateLock) { latestOperations[it] == null } }.forEach { fieldId ->
            synchronized(stateLock) { jobs[fieldId]?.let(waitingJobs::add) }
        }
        immediateJobs.joinAll()
        waitingJobs.joinAll()
        return resultFor(relevant)
    }

    suspend fun flushVisit(visitId: String): ServiceDraftFlushResult {
        val results = repository.serviceDraftWorkItemIds(visitId).map { flush(it) }
        return ServiceDraftFlushResult(
            success = results.all { it.success },
            pendingFields = results.flatMap { it.pendingFields }.toSet(),
            invalidFields = results.flatMap { it.invalidFields }.toSet(),
            failedFields = results.flatMap { it.failedFields }.toSet(),
        )
    }

    fun retry(fieldId: ServiceDraftFieldId) {
        val operation = synchronized(stateLock) { latestOperations[fieldId] }
        if (operation != null) {
            operation.writer?.let { saveTextNow(fieldId, operation.rawValue, operation.validator, it, operation.onSaved) }
                ?: scheduleRaw(fieldId, operation.rawValue, operation.validator)
            return
        }
        synchronized(stateLock) { latestChoices[fieldId] }?.let { choice -> immediateChoice(fieldId, choice.rawValue, choice.writer, choice.onSaved) }
    }

    fun cancel(fieldId: ServiceDraftFieldId) {
        synchronized(stateLock) {
            versions.remove(fieldId)
            jobs.remove(fieldId)?.cancel()
            latestOperations.remove(fieldId)
            latestChoices.remove(fieldId)
            savedAt.remove(fieldId)
        }
        _states.update { current -> current - fieldId }
    }

    private suspend fun persistText(fieldId: ServiceDraftFieldId, version: Long, operation: TextOperation, immediate: Boolean) {
        try {
            bufferWriteMutex.withLock {
                if (!isCurrent(fieldId, version)) return
                repository.saveWorkingInputBuffer(fieldId.workItemId, fieldId.fieldKey, operation.rawValue)
            }
            if (!isCurrent(fieldId, version)) return
            val validation = operation.validator(operation.rawValue)
            if (!validation.valid) {
                setState(fieldId, ServiceDraftFieldState.Invalid(operation.rawValue, validation.message!!))
                return
            }
            setState(fieldId, ServiceDraftFieldState.Pending(operation.rawValue))
            if (operation.writer == null) return
            if (!immediate) delay(debounceMillis)
            if (!isCurrent(fieldId, version)) return
            canonicalWriteMutex.withLock {
                if (!isCurrent(fieldId, version)) return@withLock
                setState(fieldId, ServiceDraftFieldState.Saving)
                val savedAt = operation.writer(operation.rawValue)
                if (!isCurrent(fieldId, version)) return@withLock
                repository.clearWorkingInputBuffer(fieldId.workItemId, fieldId.fieldKey)
                if (isCurrent(fieldId, version)) {
                    synchronized(stateLock) { this@ServiceDraftAutosaveCoordinator.savedAt[fieldId] = savedAt }
                    setState(fieldId, ServiceDraftFieldState.Clean(savedAt))
                    runCatching { operation.onSaved(savedAt) }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (isCurrent(fieldId, version)) setState(fieldId, ServiceDraftFieldState.Failed(operation.rawValue, failure.message ?: "Save failed", lastSavedAt(fieldId)))
        }
    }

    private suspend fun persistChoice(fieldId: ServiceDraftFieldId, version: Long, rawValue: String, writer: suspend (String) -> Long, onSaved: suspend (Long) -> Unit) {
        try {
            if (!isCurrent(fieldId, version)) return
            setState(fieldId, ServiceDraftFieldState.Saving)
            val savedAt = canonicalWriteMutex.withLock {
                if (!isCurrent(fieldId, version)) return@withLock null
                writer(rawValue)
            } ?: return
            if (isCurrent(fieldId, version)) {
                synchronized(stateLock) { this@ServiceDraftAutosaveCoordinator.savedAt[fieldId] = savedAt }
                setState(fieldId, ServiceDraftFieldState.Clean(savedAt))
                runCatching { onSaved(savedAt) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (isCurrent(fieldId, version)) setState(fieldId, ServiceDraftFieldState.Failed(rawValue, failure.message ?: "Save failed", lastSavedAt(fieldId)))
        }
    }

    private fun isCurrent(fieldId: ServiceDraftFieldId, version: Long): Boolean = synchronized(stateLock) { versions[fieldId] == version }

    private fun currentState(fieldId: ServiceDraftFieldId): ServiceDraftFieldState? = _states.value[fieldId]

    private fun lastSavedAt(fieldId: ServiceDraftFieldId): Long? = synchronized(stateLock) { savedAt[fieldId] }

    private fun setState(fieldId: ServiceDraftFieldId, state: ServiceDraftFieldState) {
        _states.update { current -> current + (fieldId to state) }
    }

    private fun resultFor(fields: Set<ServiceDraftFieldId>): ServiceDraftFlushResult {
        val pending = fields.filterTo(mutableSetOf()) { _states.value[it] is ServiceDraftFieldState.Pending || _states.value[it] is ServiceDraftFieldState.Saving }
        val invalid = fields.filterTo(mutableSetOf()) { _states.value[it] is ServiceDraftFieldState.Invalid }
        val failed = fields.filterTo(mutableSetOf()) { _states.value[it] is ServiceDraftFieldState.Failed }
        return ServiceDraftFlushResult(pending.isEmpty() && invalid.isEmpty() && failed.isEmpty(), pending, invalid, failed)
    }
}
