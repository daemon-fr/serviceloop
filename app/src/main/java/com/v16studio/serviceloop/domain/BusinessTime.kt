package com.v16studio.serviceloop.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface BusinessTime {
    val zoneId: ZoneId
    fun instant(): Instant
    fun today(): LocalDate = instant().atZone(zoneId).toLocalDate()
}

class ClockBusinessTime(
    private val clock: Clock = Clock.systemDefaultZone(),
    override val zoneId: ZoneId = clock.zone,
) : BusinessTime {
    override fun instant(): Instant = clock.instant()
}

class MutableBusinessTime(
    private val clock: Clock = Clock.systemUTC(),
    initialZoneId: ZoneId = ZoneId.systemDefault(),
) : BusinessTime {
    @Volatile private var currentZone = initialZoneId
    override val zoneId: ZoneId get() = currentZone
    override fun instant(): Instant = clock.instant()
    fun updateZone(zoneId: ZoneId) { currentZone = zoneId }
}

data class BusinessDateToken(val date: LocalDate, val zoneId: ZoneId, val revision: Long)

/** Emits explicit business-date/time invalidations and waits only for the next local midnight. */
class BusinessDateSignal(
    private val businessTime: BusinessTime,
    private val scope: CoroutineScope,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
) {
    private val _tokens = MutableStateFlow(token(0))
    val tokens: StateFlow<BusinessDateToken> = _tokens.asStateFlow()
    private var boundaryJob: Job? = null

    fun start() = invalidate()
    fun stop() { boundaryJob?.cancel(); boundaryJob = null }

    @Synchronized
    fun invalidate() {
        boundaryJob?.cancel()
        val next = token(_tokens.value.revision + 1)
        _tokens.value = next
        val wait = millisUntilNextMidnight(businessTime.instant(), next.zoneId)
        boundaryJob = scope.launch {
            delayMillis(wait)
            invalidate()
        }
    }

    private fun token(revision: Long) = BusinessDateToken(businessTime.today(), businessTime.zoneId, revision)

    companion object {
        fun millisUntilNextMidnight(now: Instant, zoneId: ZoneId): Long {
            val current = ZonedDateTime.ofInstant(now, zoneId)
            val next = current.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant()
            return (next.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(1)
        }
    }
}
