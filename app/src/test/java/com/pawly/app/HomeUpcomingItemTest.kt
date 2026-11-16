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
    fun communityEventsCompeteWithHealthAndCalendarReminders() {
        val eventAt = Instant.parse("2026-11-16T15:00:00Z").toEpochMilli()
        val laterReminder = Instant.parse("2026-11-16T17:00:00Z").toEpochMilli()
        val event = PawlyEvent(
            id = "community-1",
            title = "Paseo por el monte",
            description = "",
            category = "Quedada",
            location = "Málaga",
            dateText = "",
            latitude = null,
            longitude = null,
            organizerName = "Alex",
            organizerUid = "owner-1",
            interestedCount = 0,
            interestedByMe = false,
            startAt = eventAt,
            timeZoneId = "Europe/Madrid"
        )
        val item = findNextHomeUpcomingItem(
            passportEntries = emptyList(),
            healthDates = mapOf("Revisión" to "2026-11-17|09:00"),
            calendarReminders = listOf(
                """{"id":"later","title":"Recordatorio","date":$laterReminder}"""
            ),
            communityEvents = listOf(event),
            now = now,
            zoneId = zone
        )

        assertEquals("Paseo por el monte", item?.title)
        assertEquals(HomeUpcomingDestination.CALENDAR, item?.destination)
        assertEquals("Quedada", item?.type)
    }

    @Test
    fun ignoresPastCommunityEventsAndOrdersDateOnlyPassportAtEndOfDay() {
        val eventAt = Instant.parse("2026-11-16T10:00:00Z").toEpochMilli()
        val todayPassport = PassportEntry("today", "Vacuna", "Vacuna de hoy", "", "2026-11-16")
        val item = findNextHomeUpcomingItem(
            passportEntries = listOf(todayPassport),
            healthDates = emptyMap(),
            calendarReminders = emptyList(),
            communityEvents = listOf(
                PawlyEvent(
                    id = "past-event",
                    title = "Evento pasado",
                    description = "",
                    category = "Quedada",
                    location = "Málaga",
                    dateText = "",
                    latitude = null,
                    longitude = null,
                    organizerName = "Alex",
                    organizerUid = "owner-1",
                    interestedCount = 0,
                    interestedByMe = false,
                    startAt = eventAt
                )
            ),
            now = now,
            zoneId = zone
        )

        assertEquals("Vacuna de hoy", item?.title)
        assertEquals(HomeUpcomingDestination.PASSPORT, item?.destination)
        assertEquals(Instant.parse("2026-11-16T22:59:59.999999999Z"), item?.scheduledAt)
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
