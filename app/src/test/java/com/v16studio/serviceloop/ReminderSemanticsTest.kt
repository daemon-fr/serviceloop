package com.v16studio.serviceloop

import com.v16studio.serviceloop.domain.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSemanticsTest {
    @Test fun businessDateInvalidatesAcrossBucharestMidnightWithoutPolling() = runTest {
        val clock = MutableClock(Instant.parse("2026-09-09T20:59:59Z"))
        val time = MutableBusinessTime(clock, ZoneId.of("Europe/Bucharest"))
        val signal = BusinessDateSignal(time, this) { awaitCancellation() }
        assertEquals(LocalDate.parse("2026-09-09"), signal.tokens.value.date)
        clock.now = Instant.parse("2026-09-09T21:00:00Z")
        signal.invalidate()
        assertEquals(LocalDate.parse("2026-09-10"), signal.tokens.value.date)
        assertTrue(signal.tokens.value.revision > 0)
        signal.stop()
    }

    @Test fun resumeAndBusinessZoneChangesEmitButDeviceZoneDoesNotControlBusinessDate() = runTest {
        val clock = MutableClock(Instant.parse("2026-09-09T21:30:00Z"))
        val time = MutableBusinessTime(clock, ZoneId.of("Europe/London"))
        val signal = BusinessDateSignal(time, this) { awaitCancellation() }
        val first = signal.tokens.value.revision
        signal.invalidate()
        assertTrue(signal.tokens.value.revision > first)
        assertEquals(LocalDate.parse("2026-09-09"), signal.tokens.value.date)
        time.updateZone(ZoneId.of("Europe/Bucharest")); signal.invalidate()
        assertEquals(LocalDate.parse("2026-09-10"), signal.tokens.value.date)
        signal.stop()
    }

    @Test fun nextBoundaryUsesLocalMidnightIncludingZoneRules() {
        val now = Instant.parse("2026-09-09T20:59:59Z")
        assertEquals(1_000L, BusinessDateSignal.millisUntilNextMidnight(now, ZoneId.of("Europe/Bucharest")))
    }

    @Test fun allAdoptedHorizonsClassifyExactBoundariesWithoutChangingDates() {
        val today = LocalDate.parse("2026-09-10")
        val due = listOf(0L,1L,7L,14L,30L,31L).associateWith { today.plusDays(it) }
        listOf(0,7,14,30).forEach { horizon ->
            due.forEach { (offset,date) ->
                val expected = if (offset == 0L) DueBucket.TODAY else if (offset <= horizon) DueBucket.DUE_SOON else DueBucket.UPCOMING
                assertEquals("horizon=$horizon offset=$offset", expected, DueClassifier.bucket(date, today, horizon))
                assertEquals(today.plusDays(offset), date)
            }
        }
        assertEquals(DueBucket.OVERDUE, DueClassifier.bucket(today.minusDays(1), today, 14))
    }

    @Test fun defaultsAndValidationMatchAdoptedReminderSettings() {
        val p = ReminderPreferences(); p.validate()
        assertTrue(p.dailySummaryEnabled); assertEquals(8,p.summaryHour); assertEquals(0,p.summaryMinute)
        assertEquals(127,p.summaryDaysMask); assertEquals(14,p.dueSoonHorizonDays)
        assertTrue(p.includeDueServices && p.includeVisits && p.includeFollowUps && p.includeUnfinishedVisits && p.includeBackupReminder)
        assertFalse(p.appointmentAlertsEnabled); assertEquals(120,p.defaultAppointmentLeadMinutes)
        runCatching { p.copy(summaryDaysMask=0).validate() }.onSuccess { error("Expected validation failure") }
    }

    @Test fun dailySummaryOmitsDisabledAndEmptyCategoriesAndContainsOnlyAggregateText() {
        val counts = DailySummaryCounts(3,2,1,1,true)
        val all = DailySummaryText.build(ReminderPreferences(), counts)!!
        assertEquals("3 due services · 2 visits · 1 follow-up · 1 unfinished · backup due", all)
        listOf("Acme", "Main site", "1 Private Road", "access code", "finding").forEach { assertFalse(all.contains(it, true)) }
        val onlyVisits = DailySummaryText.build(ReminderPreferences(includeDueServices=false,includeFollowUps=false,includeUnfinishedVisits=false,includeBackupReminder=false), counts)
        assertEquals("2 visits", onlyVisits)
        assertNull(DailySummaryText.build(ReminderPreferences(), DailySummaryCounts()))
    }

    @Test fun summaryIdentityAllowsAtMostOnePerBusinessDateAndSkipsDowntimeReplay() {
        var saved: String? = null
        val gate = DailySummaryGate({ saved }) { saved = it; true }
        assertTrue(gate.claim("dataset", LocalDate.parse("2026-09-09")))
        assertFalse(gate.claim("dataset", LocalDate.parse("2026-09-09")))
        assertTrue(gate.claim("dataset", LocalDate.parse("2026-09-10")))
        val p = ReminderPreferences(summaryDaysMask = 1 shl (java.time.DayOfWeek.MONDAY.value - 1))
        val afterDowntime = ReminderScheduleRules.nextSummaryAfter(Instant.parse("2026-09-10T12:00:00Z"), ZoneId.of("Europe/Bucharest"), p)!!
        assertEquals(java.time.DayOfWeek.MONDAY, afterDowntime.dayOfWeek)
        assertTrue(afterDowntime.toInstant().isAfter(Instant.parse("2026-09-10T12:00:00Z")))
    }

    @Test fun appointmentRulesRescheduleAndSuppressEveryIneligibleStateWithoutMutation() {
        val p = ReminderPreferences(appointmentAlertsEnabled = true)
        val now = Instant.parse("2026-09-09T05:00:00Z").toEpochMilli()
        val ten = Instant.parse("2026-09-09T07:00:00Z").toEpochMilli()
        assertTrue(AppointmentReminderRules.eligible("BOOKED", ten, now, null, p))
        assertEquals(Instant.parse("2026-09-09T05:00:00Z").toEpochMilli(), AppointmentReminderRules.triggerAt(ten, 120))
        val twelve = Instant.parse("2026-09-09T09:00:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-09-09T07:00:00Z").toEpochMilli(), AppointmentReminderRules.triggerAt(twelve, 120))
        listOf("WORKING","FINALIZED","CANCELLED","DISPATCH_WITHDRAWN","PARTICIPATION_COMPLETE").forEach { state ->
            assertFalse(state, AppointmentReminderRules.eligible(state, ten, now, null, p))
        }
        assertFalse(AppointmentReminderRules.eligible("BOOKED", now, now, null, p))
        assertFalse(AppointmentReminderRules.eligible("BOOKED", ten, now, 0, p))
        assertEquals("BOOKED", "BOOKED")
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }
}
