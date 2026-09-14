package com.v16studio.serviceloop.ui.service

import com.v16studio.serviceloop.data.ServiceLoopRepository
import com.v16studio.serviceloop.data.isFiniteSignedDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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

/** All response fields for one checklist question share one semantic mutation family. */
data class ServiceDraftQuestionId(val workItemId: String, val questionId: String)

data class ServiceDraftQuestionDrafts(
    val issueReason: String?,
    val notApplicableReason: String?,
)

enum class ServiceDraftQuestionFieldKind {
    VALUE,
    ISSUE_REASON,
    NOT_APPLICABLE_REASON,
    DISPOSITION,
}

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
        val question: ServiceDraftQuestionId? = null,
        val questionFieldKind: ServiceDraftQuestionFieldKind? = null,
        val questionWriter: (suspend (String, ServiceDraftQuestionDrafts) -> Long)? = null,
        val onFailed: suspend (Exception) -> Unit = {},
    )

    private data class ChoiceOperation(
        val rawValue: String,
        val writer: suspend (String) -> Long,
        val onSaved: suspend (Long) -> Unit,
        val question: ServiceDraftQuestionId? = null,
        val questionWriter: (suspend (ServiceDraftQuestionDrafts) -> Long)? = null,
        val discardRawFields: Set<ServiceDraftFieldId> = emptySet(),
        val onFailed: suspend (Exception) -> Unit = {},
    )

    private val stateLock = Any()
    private val versions = mutableMapOf<ServiceDraftFieldId, Long>()
    private val latestOperations = mutableMapOf<ServiceDraftFieldId, TextOperation>()
    private val latestChoices = mutableMapOf<ServiceDraftFieldId, ChoiceOperation>()
    private val jobs = mutableMapOf<ServiceDraftFieldId, Job>()
    private val savedAt = mutableMapOf<ServiceDraftFieldId, Long?>()
    private val questionVersions = mutableMapOf<ServiceDraftQuestionId, Long>()
    private val questionFields = mutableMapOf<ServiceDraftQuestionId, MutableMap<ServiceDraftFieldId, ServiceDraftQuestionFieldKind>>()
    private val canonicalWriteMutex = Mutex()
    private val bufferWriteMutex = Mutex()
    private val _states = MutableStateFlow<Map<ServiceDraftFieldId, ServiceDraftFieldState>>(emptyMap())
    val states: StateFlow<Map<ServiceDraftFieldId, ServiceDraftFieldState>> = _states.asStateFlow()

    fun scheduleText(
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
            jobs[fieldId] = scope.launch { persistText(fieldId, next, operation, immediate = false) }
            next
        }
        check(version > 0)
    }

    fun scheduleQuestionText(
        question: ServiceDraftQuestionId,
        fieldId: ServiceDraftFieldId,
        fieldKind: ServiceDraftQuestionFieldKind,
        rawValue: String,
        validator: (String) -> ServiceDraftValidation = ServiceDraftValidators.alwaysValid(),
        writer: suspend (String, ServiceDraftQuestionDrafts) -> Long,
        onSaved: suspend (Long) -> Unit = {},
        onFailed: suspend (Exception) -> Unit = {},
    ) {
        require(fieldId.workItemId == question.workItemId)
        val operation = TextOperation(
            rawValue = rawValue,
            validator = validator,
            writer = null,
            onSaved = onSaved,
            question = question,
            questionFieldKind = fieldKind,
            questionWriter = writer,
            onFailed = onFailed,
        )
        val version = synchronized(stateLock) {
            registerQuestionField(question, fieldId, fieldKind)
            val next = versions.getOrDefault(fieldId, 0L) + 1L
            versions[fieldId] = next
            questionVersions[question] = questionVersions.getOrDefault(question, 0L) + 1L
            latestOperations[fieldId] = operation
            latestChoices.keys.filter { it in questionFields[question].orEmpty() }.toList().forEach(latestChoices::remove)
            cancelQuestionJobs(question)
            jobs[fieldId] = scope.launch { persistText(fieldId, next, operation, immediate = false, questionVersion = questionVersions[question]) }
            next
        }
        check(version > 0)
    }

    /** Source-compatible form for the original trailing writer lambda. */
    fun scheduleText(
        fieldId: ServiceDraftFieldId,
        rawValue: String,
        validator: (String) -> ServiceDraftValidation = ServiceDraftValidators.alwaysValid(),
        writer: suspend (String) -> Long,
    ) = scheduleText(fieldId, rawValue, validator, writer, {})

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

    fun saveQuestionTextNow(
        question: ServiceDraftQuestionId,
        fieldId: ServiceDraftFieldId,
        fieldKind: ServiceDraftQuestionFieldKind,
        rawValue: String,
        validator: (String) -> ServiceDraftValidation = ServiceDraftValidators.alwaysValid(),
        writer: suspend (String, ServiceDraftQuestionDrafts) -> Long,
        onSaved: suspend (Long) -> Unit = {},
        onFailed: suspend (Exception) -> Unit = {},
    ) {
        require(fieldId.workItemId == question.workItemId)
        val operation = TextOperation(rawValue, validator, null, onSaved, question, fieldKind, writer, onFailed)
        val version = synchronized(stateLock) {
            registerQuestionField(question, fieldId, fieldKind)
            val next = versions.getOrDefault(fieldId, 0L) + 1L
            versions[fieldId] = next
            questionVersions[question] = questionVersions.getOrDefault(question, 0L) + 1L
            latestOperations[fieldId] = operation
            latestChoices.keys.filter { it in questionFields[question].orEmpty() }.toList().forEach(latestChoices::remove)
            cancelQuestionJobs(question)
            jobs[fieldId] = scope.launch { persistText(fieldId, next, operation, immediate = true, questionVersion = questionVersions[question]) }
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

    fun immediateQuestionChoice(
        question: ServiceDraftQuestionId,
        fieldId: ServiceDraftFieldId,
        rawValue: String,
        writer: suspend (ServiceDraftQuestionDrafts) -> Long,
        discardRawFields: Set<ServiceDraftFieldId> = emptySet(),
        onSaved: suspend (Long) -> Unit = {},
        onFailed: suspend (Exception) -> Unit = {},
    ) {
        require(fieldId.workItemId == question.workItemId)
        val operation = ChoiceOperation(
            rawValue = rawValue,
            writer = { error("Question choice writer unavailable") },
            onSaved = onSaved,
            question = question,
            questionWriter = writer,
            discardRawFields = discardRawFields,
            onFailed = onFailed,
        )
        val version = synchronized(stateLock) {
            registerQuestionField(question, fieldId, ServiceDraftQuestionFieldKind.DISPOSITION)
            val next = versions.getOrDefault(fieldId, 0L) + 1L
            versions[fieldId] = next
            val nextQuestionVersion = questionVersions.getOrDefault(question, 0L) + 1L
            questionVersions[question] = nextQuestionVersion
            latestOperations.keys.filter { it in questionFields[question].orEmpty() && it != fieldId }.toList().forEach { sibling ->
                // Keep text operations so their latest reason can be carried into the
                // new response, but their jobs and old disposition choices are stale.
                latestChoices.remove(sibling)
            }
            latestChoices[fieldId] = operation
            cancelQuestionJobs(question)
            jobs[fieldId] = scope.launch { persistChoice(fieldId, next, rawValue, operation.writer, onSaved, operation, nextQuestionVersion) }
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
            } else if (currentState(fieldId) is ServiceDraftFieldState.Clean || currentState(fieldId) is ServiceDraftFieldState.Failed || !operation.hasCanonicalWriter()) {
                null
            } else {
                synchronized(stateLock) { jobs[fieldId]?.cancel() }
                val version = synchronized(stateLock) { versions[fieldId] ?: return@mapNotNull null }
                scope.launch { persistText(fieldId, version, operation, immediate = true, questionVersion = operation.question?.let { currentQuestionVersion(it) }) }
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
            if (operation.question != null && operation.questionWriter != null && operation.questionFieldKind != null) {
                saveQuestionTextNow(operation.question, fieldId, operation.questionFieldKind, operation.rawValue, operation.validator, operation.questionWriter, operation.onSaved, operation.onFailed)
            } else {
                operation.writer?.let { saveTextNow(fieldId, operation.rawValue, operation.validator, it, operation.onSaved) }
                    ?: scheduleRaw(fieldId, operation.rawValue, operation.validator)
            }
            return
        }
        synchronized(stateLock) { latestChoices[fieldId] }?.let { choice ->
            if (choice.question != null && choice.questionWriter != null) {
                immediateQuestionChoice(choice.question, fieldId, choice.rawValue, choice.questionWriter, choice.discardRawFields, choice.onSaved, choice.onFailed)
            } else immediateChoice(fieldId, choice.rawValue, choice.writer, choice.onSaved)
        }
    }

    suspend fun cancelAndJoin(fieldId: ServiceDraftFieldId) {
        val job = synchronized(stateLock) {
            val removedJob = jobs.remove(fieldId)
            versions.remove(fieldId)
            latestOperations.remove(fieldId)
            latestChoices.remove(fieldId)
            savedAt.remove(fieldId)
            removedJob
        }
        job?.cancelAndJoin()
        _states.update { current -> current - fieldId }
    }

    private suspend fun persistText(fieldId: ServiceDraftFieldId, version: Long, operation: TextOperation, immediate: Boolean, questionVersion: Long? = null) {
        try {
            bufferWriteMutex.withLock {
                if (!isCurrent(fieldId, version, operation.question, questionVersion)) return
                repository.saveWorkingInputBuffer(fieldId.workItemId, fieldId.fieldKey, operation.rawValue)
            }
            if (!isCurrent(fieldId, version, operation.question, questionVersion)) return
            val validation = operation.validator(operation.rawValue)
            if (!validation.valid) {
                setState(fieldId, ServiceDraftFieldState.Invalid(operation.rawValue, validation.message!!))
                return
            }
            setState(fieldId, ServiceDraftFieldState.Pending(operation.rawValue))
            if (!operation.hasCanonicalWriter()) return
            if (!immediate) delay(debounceMillis)
            if (!isCurrent(fieldId, version, operation.question, questionVersion)) return
            canonicalWriteMutex.withLock {
                if (!isCurrent(fieldId, version, operation.question, questionVersion)) return@withLock
                setState(fieldId, ServiceDraftFieldState.Saving)
                val savedAt = if (operation.question != null && operation.questionWriter != null) {
                    operation.questionWriter(operation.rawValue, questionDrafts(operation.question))
                } else operation.writer?.invoke(operation.rawValue) ?: return@withLock
                if (!isCurrent(fieldId, version, operation.question, questionVersion)) return@withLock
                if (operation.question != null) finishQuestionTransition(operation.question, questionVersion!!, savedAt, emptySet())
                else {
                    repository.clearWorkingInputBuffer(fieldId.workItemId, fieldId.fieldKey)
                    if (isCurrent(fieldId, version)) {
                        synchronized(stateLock) { this@ServiceDraftAutosaveCoordinator.savedAt[fieldId] = savedAt }
                        setState(fieldId, ServiceDraftFieldState.Clean(savedAt))
                    }
                }
                runCatching { operation.onSaved(savedAt) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (isCurrent(fieldId, version, operation.question, questionVersion)) {
                setState(fieldId, ServiceDraftFieldState.Failed(operation.rawValue, failure.message ?: "Save failed", lastSavedAt(fieldId)))
                runCatching { operation.onFailed(failure) }
            }
        }
    }

    private suspend fun persistChoice(fieldId: ServiceDraftFieldId, version: Long, rawValue: String, writer: suspend (String) -> Long, onSaved: suspend (Long) -> Unit, operation: ChoiceOperation? = null, questionVersion: Long? = null) {
        try {
            if (!isCurrent(fieldId, version, operation?.question, questionVersion)) return
            setState(fieldId, ServiceDraftFieldState.Saving)
            val savedAt = canonicalWriteMutex.withLock {
                if (!isCurrent(fieldId, version, operation?.question, questionVersion)) return@withLock null
                if (operation?.question != null && operation.questionWriter != null) operation.questionWriter(questionDrafts(operation.question)) else writer(rawValue)
            } ?: return
            if (isCurrent(fieldId, version, operation?.question, questionVersion)) {
                if (operation?.question != null) finishQuestionTransition(operation.question, questionVersion!!, savedAt, operation.discardRawFields)
                else {
                    synchronized(stateLock) { this@ServiceDraftAutosaveCoordinator.savedAt[fieldId] = savedAt }
                    setState(fieldId, ServiceDraftFieldState.Clean(savedAt))
                }
                runCatching { onSaved(savedAt) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (isCurrent(fieldId, version, operation?.question, questionVersion)) {
                setState(fieldId, ServiceDraftFieldState.Failed(rawValue, failure.message ?: "Save failed", lastSavedAt(fieldId)))
                runCatching { operation?.onFailed?.invoke(failure) }
            }
        }
    }

    private fun isCurrent(fieldId: ServiceDraftFieldId, version: Long): Boolean = synchronized(stateLock) { versions[fieldId] == version }

    private fun isCurrent(fieldId: ServiceDraftFieldId, version: Long, question: ServiceDraftQuestionId?, questionVersion: Long?): Boolean = synchronized(stateLock) {
        versions[fieldId] == version && (question == null || questionVersion != null && questionVersions[question] == questionVersion)
    }

    private fun currentQuestionVersion(question: ServiceDraftQuestionId): Long? = synchronized(stateLock) { questionVersions[question] }

    private fun registerQuestionField(question: ServiceDraftQuestionId, fieldId: ServiceDraftFieldId, kind: ServiceDraftQuestionFieldKind) {
        questionFields.getOrPut(question) { linkedMapOf() }[fieldId] = kind
    }

    private fun cancelQuestionJobs(question: ServiceDraftQuestionId) {
        questionFields[question].orEmpty().keys.forEach { fieldId -> jobs[fieldId]?.cancel() }
    }

    private fun questionDrafts(question: ServiceDraftQuestionId): ServiceDraftQuestionDrafts {
        val fields = synchronized(stateLock) { questionFields[question].orEmpty().toMap() }
        val latest = synchronized(stateLock) { latestOperations.toMap() }
        return ServiceDraftQuestionDrafts(
            issueReason = fields.entries.firstOrNull { it.value == ServiceDraftQuestionFieldKind.ISSUE_REASON }?.key?.let { latest[it]?.rawValue },
            notApplicableReason = fields.entries.firstOrNull { it.value == ServiceDraftQuestionFieldKind.NOT_APPLICABLE_REASON }?.key?.let { latest[it]?.rawValue },
        )
    }

    private suspend fun finishQuestionTransition(question: ServiceDraftQuestionId, questionVersion: Long, savedAtEpochMillis: Long, discardRawFields: Set<ServiceDraftFieldId>) {
        val fields = synchronized(stateLock) { questionFields[question].orEmpty().keys.toSet() }
        bufferWriteMutex.withLock {
            if (!isCurrentQuestion(question, questionVersion)) return
            val persistedBufferKeys = repository.workingInputBuffers(question.workItemId).keys
            fields.filter { it.fieldKey in persistedBufferKeys }.forEach { fieldId ->
                repository.clearWorkingInputBuffer(fieldId.workItemId, fieldId.fieldKey)
            }
        }
        synchronized(stateLock) {
            if (questionVersions[question] != questionVersion) return
            discardRawFields.forEach { fieldId -> latestOperations.remove(fieldId) }
            fields.forEach { fieldId -> this@ServiceDraftAutosaveCoordinator.savedAt[fieldId] = savedAtEpochMillis }
            _states.update { current -> fields.fold(current) { result, fieldId -> result + (fieldId to ServiceDraftFieldState.Clean(savedAtEpochMillis)) } }
        }
    }

    private fun isCurrentQuestion(question: ServiceDraftQuestionId, version: Long): Boolean = synchronized(stateLock) { questionVersions[question] == version }

    private fun TextOperation.hasCanonicalWriter(): Boolean = writer != null || questionWriter != null

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
