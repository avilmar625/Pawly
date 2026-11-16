package com.pawly.app.care

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Strict parser for the reminder date format stored in local preferences.
 * It rejects impossible dates and any trailing characters instead of silently
 * accepting a valid prefix.
 */
internal object CareReminderDateTimeParser {
    fun parseMillis(value: String, timeZone: TimeZone = TimeZone.getDefault()): Long? {
        if (value.isBlank()) return null
        val normalized = value.replace("|", " ")
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply {
            isLenient = false
            this.timeZone = timeZone
        }
        val position = ParsePosition(0)
        val parsed = formatter.parse(normalized, position) ?: return null
        if (position.index != normalized.length || position.errorIndex >= 0) return null
        return parsed.time
    }
}
