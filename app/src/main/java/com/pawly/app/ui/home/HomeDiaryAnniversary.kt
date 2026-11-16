package com.pawly.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class DiaryAnniversary(
    val title: String,
    val date: LocalDate,
    val photoUrls: List<String>
)

internal data class DiaryRecordData(
    val title: String,
    val date: LocalDate,
    val photoUrls: List<String> = emptyList()
)

/** Finds a real diary entry from this calendar day in an earlier year. */
internal fun selectDiaryAnniversary(records: List<DiaryRecordData>, today: LocalDate): DiaryAnniversary? {
    return records.filter { it.date.isBefore(today) && it.date.month == today.month && it.date.dayOfMonth == today.dayOfMonth }
        .maxByOrNull { it.date }
        ?.let { DiaryAnniversary(it.title.ifBlank { "Un recuerdo especial" }, it.date, it.photoUrls.take(4)) }
}

internal fun findDiaryAnniversary(entries: List<String>, today: LocalDate): DiaryAnniversary? {
    val records = entries.mapNotNull { raw ->
        runCatching {
            val json = JSONObject(raw)
            require(json.optInt("pawlyDiaryVersion") == 1)
            val millis = json.optLong("date", 0L)
            require(millis > 0L)
            val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
            val photos = json.optJSONArray("photos") ?: JSONArray()
            DiaryRecordData(
                title = json.optString("title"),
                date = date,
                photoUrls = List(photos.length()) { photos.optString(it) }.filter { it.startsWith("https://") }
            )
        }.getOrNull()
    }
    return selectDiaryAnniversary(records, today)
}
