package com.v16studio.serviceloop.domain

import java.time.LocalDate

object RecurrenceCalculator {
    fun nextDate(completionDate: LocalDate, count: Int, unit: String): LocalDate {
        require(count > 0) { "Interval must be positive" }
        return when (unit) {
            "DAYS" -> completionDate.plusDays(count.toLong())
            "WEEKS" -> completionDate.plusWeeks(count.toLong())
            "MONTHS" -> completionDate.plusMonths(count.toLong())
            "YEARS" -> completionDate.plusYears(count.toLong())
            else -> error("Unsupported interval unit")
        }
    }
}
