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

/** Finds a real diary entry from this calendar day in an earlier year. */
internal fun findDiaryAnniversary(entries: List<String>, today: LocalDate): DiaryAnniversary? {
    return entries.mapNotNull { raw ->
        runCatching {
            val json = JSONObject(raw)
            require(json.optInt("pawlyDiaryVersion") == 1)
            val millis = json.optLong("date", 0L)
            require(millis > 0L)
            val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
            require(date.isBefore(today) && date.month == today.month && date.dayOfMonth == today.dayOfMonth)
            val photos = json.optJSONArray("photos") ?: JSONArray()
            DiaryAnniversary(
                title = json.optString("title").ifBlank { "Un recuerdo especial" },
                date = date,
                photoUrls = List(photos.length()) { photos.optString(it) }.filter { it.startsWith("https://") }.take(4)
            )
        }.getOrNull()
    }.maxByOrNull { it.date }
}
