package com.pawly.app.care.domain

/**
 * Parses a manually entered pet weight from a passport entry.
 *
 * Accepts decimal commas or dots, with or without the kg suffix.
 * Values outside the supported pet-weight range are ignored rather than
 * displayed as health measurements.
 */
internal object WeightMeasurementParser {
    private val weightPattern = Regex(
        """(?<![\\d.,])(\\d{1,3}(?:[.,]\\d{1,2})?)\\s*(?:kg)?""",
        RegexOption.IGNORE_CASE
    )

    fun parse(raw: String): Float? {
        val value = weightPattern.find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(',', '.')
            ?.toFloatOrNull()
            ?: return null
        return value.takeIf { it in 0.1f..150f }
    }
}
