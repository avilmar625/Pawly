package com.pawly.app.care.domain

import com.pawly.app.care.model.CareTask
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.WeekFields

/**
 * Evaluates routines against a local calendar date.
 *
 * An empty daysOfWeek list means a legacy weekly routine: keep its previous
 * once-per-ISO-week behaviour. New weekly routines can restrict themselves to
 * explicit ISO weekdays (Monday=1 … Sunday=7).
 */
internal object CareTaskSchedule {
    fun normalizeDays(days: List<Int>): List<Int> = days.filter { it in 1..7 }.distinct().sorted()

    fun isDueOn(task: CareTask, date: LocalDate): Boolean =
        task.frequency != "Semanal" || task.daysOfWeek.isEmpty() ||
            date.dayOfWeek.value in task.daysOfWeek

    fun isCompleteOn(task: CareTask, date: LocalDate): Boolean {
        if (!task.completed) return false
        if (task.frequency == "Puntual") return true
        val completedDate = runCatching { LocalDate.parse(task.completedOn) }.getOrNull() ?: return false
        return when (task.frequency) {
            "Diario" -> completedDate == date
            "Semanal" -> {
                if (task.daysOfWeek.isNotEmpty()) {
                    completedDate == date
                } else {
                    val fields = WeekFields.ISO
                    completedDate.get(fields.weekOfWeekBasedYear()) == date.get(fields.weekOfWeekBasedYear()) &&
                        completedDate.get(fields.weekBasedYear()) == date.get(fields.weekBasedYear())
                }
            }
            else -> completedDate == date
        }
    }

    /**
     * Returns the next local wall-clock occurrence for a weekly reminder.
     * The supplied ZonedDateTime carries the device zone, so daylight-saving
     * changes are evaluated using calendar days rather than fixed 24-hour spans.
     */
    fun nextWeeklyReminderAt(
        timeOfDay: String,
        days: List<Int>,
        now: ZonedDateTime = ZonedDateTime.now()
    ): ZonedDateTime? {
        if (!timeOfDay.matches(Regex("\\d{2}:\\d{2}"))) return null
        val parts = timeOfDay.split(":")
        val hour = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        val selectedDays = normalizeDays(days)
        if (selectedDays.isEmpty()) return null
        return (0L..7L)
            .map { offset ->
                now.plusDays(offset)
                    .withHour(hour)
                    .withMinute(minute)
                    .withSecond(0)
                    .withNano(0)
            }
            .filter { candidate -> candidate.isAfter(now) && candidate.dayOfWeek.value in selectedDays }
            .minByOrNull { it.toInstant() }
    }

    fun daysLabel(days: List<Int>): String = normalizeDays(days).joinToString(", ") { day ->
        when (day) {
            1 -> "Lun"
            2 -> "Mar"
            3 -> "Mié"
            4 -> "Jue"
            5 -> "Vie"
            6 -> "Sáb"
            else -> "Dom"
        }
    }
}
