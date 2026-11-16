package com.pawly.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DiaryAnniversaryTest {
    @Test fun findsEntryFromSameCalendarDayInPreviousYear() {
        val date = LocalDate.of(2025, 11, 16)
        val record = DiaryRecordData("Primera tarde en la playa", date, listOf("https://example.com/photo.jpg"))

        val result = selectDiaryAnniversary(listOf(record), LocalDate.of(2026, 11, 16))

        assertEquals("Primera tarde en la playa", result?.title)
        assertEquals(date, result?.date)
        assertEquals(1, result?.photoUrls?.size)
    }

    @Test fun doesNotInventAnniversaryWhenNoMatchingDateExists() {
        val record = DiaryRecordData("Otro día", LocalDate.of(2025, 11, 15))

        assertNull(selectDiaryAnniversary(listOf(record), LocalDate.of(2026, 11, 16)))
    }

    @Test fun selectsTheMostRecentMatchingAnniversary() {
        val older = DiaryRecordData("Recuerdo antiguo", LocalDate.of(2023, 11, 16))
        val newer = DiaryRecordData("Recuerdo reciente", LocalDate.of(2025, 11, 16))

        assertEquals("Recuerdo reciente", selectDiaryAnniversary(listOf(older, newer), LocalDate.of(2026, 11, 16))?.title)
    }
}
