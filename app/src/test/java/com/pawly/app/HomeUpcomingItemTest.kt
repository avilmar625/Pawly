package com.pawly.app

import com.pawly.app.care.model.PassportEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class HomeUpcomingItemTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val now = Instant.parse("2026-11-16T12:00:00Z")

    @Test
    fun choosesTheNearestItemAcrossPassportHealthAndCalendar() {
        val calendarAt = Instant.parse("2026-11-17T08:00:00Z").toEpochMilli()
        val item = findNextHomeUpcomingItem(
            passportEntries = listOf(
                PassportEntry("vax-later", "Vacuna", "Vacuna anual", "", "2026-11-20"),
                PassportEntry("weight", "Peso", "65 kg", "", "2026-11-16")
            ),
            healthDates = mapOf("Vacunación" to "2026-11-18|09:00"),
            calendarReminders = listOf(
                """{"id":"vet","title":"Revisión veterinaria","category":"Veterinario","date":$calendarAt}"""
            ),
            now = now,
            zoneId = zone
        )

        assertEquals("Revisión veterinaria", item?.title)
        assertEquals(HomeUpcomingDestination.CALENDAR, item?.destination)
    }

    @Test
    fun ignoresExpiredTimedRemindersAndKeepsTheNextHealthDate() {
        val expired = Instant.parse("2026-11-16T10:00:00Z").toEpochMilli()
        val item = findNextHomeUpcomingItem(
            passportEntries = emptyList(),
            healthDates = mapOf(
                "Desparasitación" to "2026-11-17|09:00",
                "Vacunación" to "2026-11-16|10:00"
            ),
            calendarReminders = listOf(
                """{"id":"old","title":"Evento anterior","date":$expired}"""
            ),
            now = now,
            zoneId = zone
        )

        assertEquals("Desparasitación", item?.title)
        assertEquals(HomeUpcomingDestination.HEALTH_AGENDA, item?.destination)
    }

    @Test
    fun returnsNullWhenThereAreNoUpcomingEntries() {
        val past = Instant.parse("2026-11-16T10:00:00Z").toEpochMilli()
        val item = findNextHomeUpcomingItem(
            passportEntries = listOf(PassportEntry("past", "Vacuna", "Vacuna antigua", "", "2026-11-10")),
            healthDates = mapOf("Revisión" to "2026-11-16|10:00"),
            calendarReminders = listOf("""{"id":"old","title":"Evento anterior","date":$past}"""),
            now = now,
            zoneId = zone
        )

        assertNull(item)
    }
}
