package com.pawly.app.care.domain

import com.pawly.app.care.model.CareTask
import java.time.LocalDate
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
