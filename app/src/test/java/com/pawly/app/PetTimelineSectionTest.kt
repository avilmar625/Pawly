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
