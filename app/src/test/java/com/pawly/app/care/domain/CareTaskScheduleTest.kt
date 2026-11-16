package com.pawly.app.care.domain

import com.pawly.app.care.model.CareTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CareTaskScheduleTest {
    @Test fun weeklyRoutineIsDueOnlyOnSelectedIsoWeekdays() {
        val routine = CareTask(
            id = "walk", title = "Paseo largo", responsible = "Alex",
            frequency = "Semanal", completed = false, action = "Paseo",
            daysOfWeek = listOf(1, 3, 5)
        )
        assertTrue(CareTaskSchedule.isDueOn(routine, LocalDate.parse("2026-11-16"))) // Monday
        assertFalse(CareTaskSchedule.isDueOn(routine, LocalDate.parse("2026-11-17"))) // Tuesday
        assertTrue(CareTaskSchedule.isDueOn(routine, LocalDate.parse("2026-11-18"))) // Wednesday
        assertTrue(CareTaskSchedule.isDueOn(routine, LocalDate.parse("2026-11-20"))) // Friday
        assertFalse(CareTaskSchedule.isDueOn(routine, LocalDate.parse("2026-11-21"))) // Saturday
    }

    @Test fun selectedWeekdayRoutineMustBeCompletedOnThatDate() {
        val routine = CareTask(
            id = "brush", title = "Cepillado", responsible = "",
            frequency = "Semanal", completed = true, completedOn = "2026-11-16",
            daysOfWeek = listOf(1, 3), action = "Higiene"
        )
        assertTrue(CareTaskSchedule.isCompleteOn(routine, LocalDate.parse("2026-11-16")))
        assertFalse(CareTaskSchedule.isCompleteOn(routine, LocalDate.parse("2026-11-18")))
    }

    @Test fun legacyWeeklyRoutineWithoutSelectedDaysKeepsOncePerIsoWeekBehaviour() {
        val routine = CareTask(
            id = "legacy", title = "Revisión semanal", responsible = "",
            frequency = "Semanal", completed = true, completedOn = "2026-11-16"
        )
        assertTrue(CareTaskSchedule.isDueOn(routine, LocalDate.parse("2026-11-17")))
        assertTrue(CareTaskSchedule.isCompleteOn(routine, LocalDate.parse("2026-11-20")))
    }

    @Test fun weeklyReminderUsesTheNextSelectedDayAtTheChosenLocalTime() {
        val now = java.time.ZonedDateTime.parse("2026-11-16T08:00:00+01:00[Europe/Madrid]")
        val next = CareTaskSchedule.nextWeeklyReminderAt("09:30", listOf(1, 3, 5), now)
        assertEquals(java.time.ZonedDateTime.parse("2026-11-16T09:30:00+01:00[Europe/Madrid]"), next)
    }

    @Test fun weeklyReminderSkipsTheCurrentDayWhenItsTimeHasPassed() {
        val now = java.time.ZonedDateTime.parse("2026-11-16T10:00:00+01:00[Europe/Madrid]")
        val next = CareTaskSchedule.nextWeeklyReminderAt("09:30", listOf(1, 3, 5), now)
        assertEquals(java.time.ZonedDateTime.parse("2026-11-18T09:30:00+01:00[Europe/Madrid]"), next)
    }

    @Test fun weeklyReminderRejectsInvalidTimeOrEmptyDays() {
        val now = java.time.ZonedDateTime.parse("2026-11-16T08:00:00+01:00[Europe/Madrid]")
        assertEquals(null, CareTaskSchedule.nextWeeklyReminderAt("25:00", listOf(1), now))
        assertEquals(null, CareTaskSchedule.nextWeeklyReminderAt("09:00", emptyList(), now))
    }

    @Test fun normalizesWeekdaysAndRejectsValuesOutsideIsoRange() {
        assertEquals(listOf(1, 3, 7), CareTaskSchedule.normalizeDays(listOf(7, 3, 3, 0, 8, 1)))
    }
}
