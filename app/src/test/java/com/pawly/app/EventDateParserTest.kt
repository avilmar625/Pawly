package com.pawly.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class EventDateParserTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    @Test
    fun parsesLeapDayAndKeepsTheRequestedTimezone() {
        val actual = EventDateParser.parseMillis("29/02/2028 · 10:30", madrid)
        val expected = LocalDateTime.parse("2028-02-29T10:30")
            .atZone(madrid).toInstant().toEpochMilli()
        assertEquals(expected, actual)
    }

    @Test
    fun rejectsImpossibleCalendarDates() {
        assertThrows(IllegalArgumentException::class.java) {
            EventDateParser.parseMillis("31/02/2028 10:30", madrid)
        }
    }

    @Test
    fun rejectsInvalidTime() {
        assertThrows(IllegalArgumentException::class.java) {
            EventDateParser.parseMillis("12/06/2028 25:61", madrid)
        }
    }
}
