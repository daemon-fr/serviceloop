package com.v16studio.serviceloop.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class ReminderPreferences(
    val dailySummaryEnabled: Boolean = true,
    val summaryHour: Int = 8,
    val summaryMinute: Int = 0,
    val summaryDaysMask: Int = ALL_DAYS,
    val dueSoonHorizonDays: Int = 14,
    val includeDueServices: Boolean = true,
    val includeVisits: Boolean = true,
    val includeFollowUps: Boolean = true,
    val includeUnfinishedVisits: Boolean = true,
    val includeBackupReminder: Boolean = true,
    val appointmentAlertsEnabled: Boolean = false,
    val defaultAppointmentLeadMinutes: Int = 120,
) {
    fun validate() {
        require(summaryHour in 0..23 && summaryMinute in 0..59) { "Choose a valid summary time" }
        require(dueSoonHorizonDays in HORIZONS) { "Choose a supported due-soon horizon" }
        require(defaultAppointmentLeadMinutes in APPOINTMENT_LEADS) { "Choose a supported appointment lead" }
        require(!dailySummaryEnabled || summaryDaysMask and ALL_DAYS != 0) { "Select at least one summary day" }
    }
    fun includes(day: DayOfWeek) = summaryDaysMask and (1 shl (day.value - 1)) != 0
    companion object {
        val HORIZONS = setOf(0, 7, 14, 30)
        val APPOINTMENT_LEADS = setOf(120, 1440)
        const val ALL_DAYS = 0x7f
    }
}

data class ReminderRuntimeState(
    val deliveryRequested: Boolean = false,
    val permissionGranted: Boolean = false,
    val summariesChannelEnabled: Boolean = false,
    val appointmentsChannelEnabled: Boolean = false,
    val schedulingError: String? = null,
) {
    val label: String get() = when {
        !deliveryRequested -> "Off"
        !permissionGranted -> "Requested · blocked by Android"
        !summariesChannelEnabled && !appointmentsChannelEnabled -> "Requested · channels blocked"
        schedulingError != null -> "Requested · scheduling needs retry"
        else -> "Active · approximate"
    }
}

data class DailySummaryCounts(
    val dueServices: Int = 0,
    val visits: Int = 0,
    val followUps: Int = 0,
    val unfinishedVisits: Int = 0,
    val backupDue: Boolean = false,
)

object DailySummaryText {
    fun build(preferences: ReminderPreferences, counts: DailySummaryCounts): String? {
        val parts = buildList {
            if (preferences.includeDueServices && counts.dueServices > 0) add("${counts.dueServices} due service${if (counts.dueServices == 1) "" else "s"}")
            if (preferences.includeVisits && counts.visits > 0) add("${counts.visits} visit${if (counts.visits == 1) "" else "s"}")
            if (preferences.includeFollowUps && counts.followUps > 0) add("${counts.followUps} follow-up${if (counts.followUps == 1) "" else "s"}")
            if (preferences.includeUnfinishedVisits && counts.unfinishedVisits > 0) add("${counts.unfinishedVisits} unfinished")
            if (preferences.includeBackupReminder && counts.backupDue) add("backup due")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}

object DueClassifier {
    fun bucket(due: java.time.LocalDate, today: java.time.LocalDate, horizonDays: Int): DueBucket = when {
        due.isBefore(today) -> DueBucket.OVERDUE
        due == today -> DueBucket.TODAY
        !due.isAfter(today.plusDays(horizonDays.toLong())) -> DueBucket.DUE_SOON
        else -> DueBucket.UPCOMING
    }
}

object ReminderScheduleRules {
    fun nextSummaryAfter(now: Instant, zoneId: ZoneId, preferences: ReminderPreferences): ZonedDateTime? {
        if (!preferences.dailySummaryEnabled) return null
        val localNow = now.atZone(zoneId)
        var date = localNow.toLocalDate()
        repeat(8) {
            val target = date.atTime(preferences.summaryHour, preferences.summaryMinute).atZone(zoneId)
            if (preferences.includes(date.dayOfWeek) && target.toInstant().isAfter(now)) return target
            date = date.plusDays(1)
        }
        return null
    }
}

object AppointmentReminderRules {
    fun effectiveLead(visitOverrideMinutes: Int?, preferences: ReminderPreferences) = visitOverrideMinutes ?: preferences.defaultAppointmentLeadMinutes
    fun eligible(state: String, appointmentStartMillis: Long?, nowMillis: Long, visitOverrideMinutes: Int?, preferences: ReminderPreferences): Boolean =
        preferences.appointmentAlertsEnabled && state == "BOOKED" && appointmentStartMillis != null && appointmentStartMillis > nowMillis && effectiveLead(visitOverrideMinutes, preferences) > 0
    fun triggerAt(appointmentStartMillis: Long, leadMinutes: Int) = appointmentStartMillis - leadMinutes * 60_000L
}

class DailySummaryGate(private val read: () -> String?, private val write: (String) -> Boolean) {
    @Synchronized fun claim(datasetId: String, businessDate: java.time.LocalDate): Boolean {
        val identity = "$datasetId/$businessDate"
        if (read() == identity) return false
        return write(identity)
    }
}
