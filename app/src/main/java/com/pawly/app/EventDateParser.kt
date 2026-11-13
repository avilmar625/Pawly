package com.pawly.app

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Pure event date parsing shared by the data layer and suitable for iOS parity tests. */
internal object EventDateParser {
    private val formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)

    fun parseMillis(value: String, zone: ZoneId = ZoneId.systemDefault()): Long {
        val normalized = value.trim().replace("·", " ").replace(Regex("\\s+"), " ").trim()
        return runCatching {
            LocalDateTime.parse(normalized, formatter).atZone(zone).toInstant().toEpochMilli()
        }.getOrElse {
            throw IllegalArgumentException("Introduce la fecha como DD/MM/AAAA · HH:mm.")
        }
    }
}
