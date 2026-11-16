package com.pawly.app

import com.pawly.app.care.model.CareLogEntry
import com.pawly.app.care.model.PassportEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PetTimelineSectionTest {
    @Test fun combinesRealHealthCareDiaryAndCalendarRecordsInReverseChronologicalOrder() {
        val careTime = Instant.parse("2026-11-16T08:40:00Z").toEpochMilli()
        val diaryTime = Instant.parse("2026-11-15T18:30:00Z").toEpochMilli()
        val calendarTime = Instant.parse("2026-11-17T09:00:00Z").toEpochMilli()
        val diary = """{"pawlyDiaryVersion":1,"title":"Primera tarde en la playa","category":"Aventura","date":$diaryTime}"""
        val reminder = """{"id":"vet-1","title":"Revisión veterinaria","category":"Veterinario","date":$calendarTime}"""

        val result = buildPetTimelineItems(
            passportEntries = listOf(PassportEntry("vax-1", "Vacuna", "Rabia", "Dosis anual", "2026-11-14")),
            careLogs = listOf(CareLogEntry("log-1", "Antiparasitario", "Medicación", "ana", "Ana", careTime)),
            diaryEntries = listOf(diary),
            calendarReminders = listOf(reminder),
            healthDates = emptyMap(),
            zoneId = ZoneId.of("UTC")
        )

        assertEquals(listOf("Cita", "Cuidado", "Diario", "Salud"), result.map { it.category })
        assertEquals("Revisión veterinaria", result.first().title)
        assertEquals("Antiparasitario", result[1].title)
        assertEquals("Primera tarde en la playa", result[2].title)
        assertEquals("Rabia", result.last().title)
    }

    @Test fun communityEventsAppearInTimelineAndInTheAppointmentsFilter() {
        val eventTime = Instant.parse("2026-11-18T17:00:00Z").toEpochMilli()
        val event = PawlyEvent(
            id = "walk-1",
            title = "Paseo en grupo",
            description = "Ruta fácil",
            category = "Quedada",
            location = "Málaga",
            dateText = "18/11/2026 18:00",
            latitude = null,
            longitude = null,
            organizerName = "Ana",
            organizerUid = "organizer-1",
            interestedCount = 4,
            interestedByMe = false,
            startAt = eventTime,
            timeZoneId = "Europe/Madrid"
        )
        val items = buildPetTimelineItems(
            passportEntries = emptyList(),
            careLogs = emptyList(),
            diaryEntries = emptyList(),
            calendarReminders = emptyList(),
            healthDates = emptyMap(),
            communityEvents = listOf(event),
            zoneId = ZoneId.of("UTC")
        )

        assertEquals("Evento", items.single().category)
        assertEquals("Paseo en grupo", items.single().title)
        assertEquals("Quedada · Málaga", items.single().detail)
        assertEquals(items, filterPetTimelineItems(items, "Citas"))
    }

    @Test fun filtersTimelineByHealthDiaryAndAppointmentsWithoutDroppingSourceItems() {
        val items = listOf(
            PetTimelineItem("health-1", "Vacuna", "Rabia", "Salud", 4L),
            PetTimelineItem("care-1", "Paseo", "Ana", "Cuidado", 3L),
            PetTimelineItem("diary-1", "Playa", "Aventura", "Diario", 2L),
            PetTimelineItem("appointment-1", "Veterinario", "Revisión", "Cita", 1L)
        )
        assertEquals(listOf("health-1", "care-1"), filterPetTimelineItems(items, "Salud").map { it.id })
        assertEquals(listOf("diary-1"), filterPetTimelineItems(items, "Diario").map { it.id })
        assertEquals(listOf("appointment-1"), filterPetTimelineItems(items, "Citas").map { it.id })
        assertEquals(items, filterPetTimelineItems(items, "Todo"))
    }

    @Test fun ignoresMalformedOrUnsupportedDiaryAndCalendarRecords() {
        val result = buildPetTimelineItems(
            passportEntries = emptyList(),
            careLogs = emptyList(),
            diaryEntries = listOf("not-json", """{"pawlyDiaryVersion":2,"date":1234}"""),
            calendarReminders = listOf("not-json", """{"title":"Sin fecha","date":0}"""),
            healthDates = emptyMap(),
            zoneId = ZoneId.of("UTC")
        )
        assertTrue(result.isEmpty())
    }
}
