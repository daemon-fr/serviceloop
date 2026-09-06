package com.v16studio.serviceloop.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
