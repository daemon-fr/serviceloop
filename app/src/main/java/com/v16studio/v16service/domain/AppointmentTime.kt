package com.v16studio.v16service.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val appointmentTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withResolverStyle(java.time.format.ResolverStyle.STRICT)

/** Combines an optional local appointment time with the existing business-date truth. */
fun appointmentEpochMillis(date: String, time: String?, zoneId: ZoneId): Long? {
    val localTime = time?.trim()?.takeIf(String::isNotEmpty)?.let { LocalTime.parse(it, appointmentTimeFormatter) }
        ?: return null
    return LocalDate.parse(date).atTime(localTime).atZone(zoneId).toInstant().toEpochMilli()
}

fun formatAppointmentTime(epochMillis: Long?, zoneId: ZoneId): String? = epochMillis?.let {
    appointmentTimeFormatter.format(java.time.Instant.ofEpochMilli(it).atZone(zoneId))
}

fun parseAppointmentTime(epochMillis: Long?, zoneId: ZoneId): String = formatAppointmentTime(epochMillis, zoneId).orEmpty()
