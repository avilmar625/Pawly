package com.pawly.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DiaryAnniversaryTest {
    @Test fun findsEntryFromSameCalendarDayInPreviousYear() {
        val date = LocalDate.of(2025, 11, 16)
        val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val raw = JSONObject()
            .put("pawlyDiaryVersion", 1)
            .put("title", "Primera tarde en la playa")
            .put("date", millis)
            .put("photos", JSONArray().put("https://example.com/photo.jpg"))
            .toString()

        val result = findDiaryAnniversary(listOf(raw), LocalDate.of(2026, 11, 16))

        assertEquals("Primera tarde en la playa", result?.title)
        assertEquals(date, result?.date)
        assertEquals(1, result?.photoUrls?.size)
    }

    @Test fun doesNotInventAnniversaryWhenNoMatchingDateExists() {
        val date = LocalDate.of(2025, 11, 15)
        val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val raw = JSONObject().put("pawlyDiaryVersion", 1).put("title", "Otro día").put("date", millis).toString()

        assertNull(findDiaryAnniversary(listOf(raw), LocalDate.of(2026, 11, 16)))
    }
}
