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
        val localDateTime = try {
            LocalDateTime.parse(normalized, formatter)
        } catch (_: Exception) {
            throw IllegalArgumentException("Introduce la fecha como DD/MM/AAAA · HH:mm.")
        }
        // LocalDateTime.atZone() silently shifts nonexistent times during the
        // spring DST transition and guesses during the autumn overlap. Events
        // must preserve the exact wall-clock time entered by the organizer.
        val validOffsets = zone.rules.getValidOffsets(localDateTime)
        require(validOffsets.size == 1) {
            "Esa hora no existe o es ambigua por el cambio horario. Elige otra hora."
        }
        return localDateTime.toInstant(validOffsets.single()).toEpochMilli()
    }
}
