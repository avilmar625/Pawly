package com.pawly.app.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class CareReminderDateTimeParserTest {
    @Test
    fun parsesValidReminderInRequestedTimeZone() {
        val utc = TimeZone.getTimeZone("UTC")
        assertEquals(
            1_796_119_200_000L,
            CareReminderDateTimeParser.parseMillis("2026-12-01 10:00", utc)
        )
    }

    @Test
    fun rejectsImpossibleCalendarDate() {
        assertNull(CareReminderDateTimeParser.parseMillis("2026-02-30 10:00", TimeZone.getTimeZone("UTC")))
    }

    @Test
    fun rejectsTrailingGarbageAndIncompleteValues() {
        assertNull(CareReminderDateTimeParser.parseMillis("2026-12-01 10:00oops", TimeZone.getTimeZone("UTC")))
        assertNull(CareReminderDateTimeParser.parseMillis("2026-12-01", TimeZone.getTimeZone("UTC")))
        assertNull(CareReminderDateTimeParser.parseMillis("", TimeZone.getTimeZone("UTC")))
    }

    @Test
    fun supportsLegacySeparatorStoredByOlderReminderEntries() {
        val utc = TimeZone.getTimeZone("UTC")
        assertEquals(
            CareReminderDateTimeParser.parseMillis("2026-12-01 10:00", utc),
            CareReminderDateTimeParser.parseMillis("2026-12-01|10:00", utc)
        )
    }
}
