package com.pawly.app

import com.pawly.app.care.model.PassportEntry
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class HomeUpcomingDestination {
    PASSPORT,
    HEALTH_AGENDA,
    CALENDAR
}

internal data class HomeUpcomingItem(
    val title: String,
    val type: String,
    val date: LocalDate,
    val scheduledAt: Instant,
    val destination: HomeUpcomingDestination
)

/**
 * Selects the nearest real upcoming item for the Hoy card.
 *
 * Passport entries are date-only; health reminders and calendar reminders retain
 * their stored time. Expired timed reminders are ignored. The helper is pure so
 * its ordering and edge cases can be unit tested without Compose or Android UI.
 */
internal fun findNextHomeUpcomingItem(
    passportEntries: List<PassportEntry>,
    healthDates: Map<String, String>,
    calendarReminders: List<String>,
    communityEvents: List<PawlyEvent> = emptyList(),
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): HomeUpcomingItem? {
    val candidates = mutableListOf<HomeUpcomingItem>()
    val today = now.atZone(zoneId).toLocalDate()
    val healthFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    passportEntries.forEach { entry ->
        if (entry.type.equals("Peso", ignoreCase = true)) return@forEach
        val date = runCatching { LocalDate.parse(entry.date.take(10)) }.getOrNull()
            ?: return@forEach
        if (!date.isBefore(today)) {
            candidates += HomeUpcomingItem(
                title = entry.title.ifBlank { entry.type },
                type = entry.type,
                date = date,
                scheduledAt = date.atTime(LocalTime.MAX).atZone(zoneId).toInstant(),
                destination = HomeUpcomingDestination.PASSPORT
            )
        }
    }

    healthDates.forEach { (title, rawDate) ->
        val localDateTime = runCatching {
            LocalDateTime.parse(rawDate.replace('|', ' '), healthFormatter)
        }.getOrNull() ?: return@forEach
        val scheduledAt = localDateTime.atZone(zoneId).toInstant()
        if (scheduledAt.isAfter(now)) {
            candidates += HomeUpcomingItem(
                title = title,
                type = "Cuidado",
                date = localDateTime.toLocalDate(),
                scheduledAt = scheduledAt,
                destination = HomeUpcomingDestination.HEALTH_AGENDA
            )
        }
    }

    calendarReminders.forEach { raw ->
        runCatching {
            val json = JSONObject(raw)
            val timestamp = json.optLong("date")
            val scheduledAt = Instant.ofEpochMilli(timestamp)
            if (timestamp > 0L && scheduledAt.isAfter(now)) {
                val localDate = scheduledAt.atZone(zoneId).toLocalDate()
                val title = json.optString("title").ifBlank { "Cita o recordatorio" }
                val category = json.optString("category").ifBlank { "Cita" }
                candidates += HomeUpcomingItem(
                    title = title,
                    type = category,
                    date = localDate,
                    scheduledAt = scheduledAt,
                    destination = HomeUpcomingDestination.CALENDAR
                )
            }
        }
    }

    communityEvents.forEach { event ->
        val timestamp = event.startAt
        if (timestamp > 0L) {
            val scheduledAt = Instant.ofEpochMilli(timestamp)
            if (scheduledAt.isAfter(now)) {
                val localDate = scheduledAt.atZone(zoneId).toLocalDate()
                candidates += HomeUpcomingItem(
                    title = event.title.ifBlank { "Evento de la comunidad" },
                    type = event.category.ifBlank { "Evento" },
                    date = localDate,
                    scheduledAt = scheduledAt,
                    destination = HomeUpcomingDestination.CALENDAR
                )
            }
        }
    }

    return candidates.minByOrNull { it.scheduledAt }
}
