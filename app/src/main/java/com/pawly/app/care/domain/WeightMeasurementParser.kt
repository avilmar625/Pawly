package com.pawly.app.care.domain

/**
 * Parses a manually entered pet weight from a passport entry.
 *
 * Explicit "kg" values may appear inside a longer note and take priority.
 * Without a unit, only a standalone number (optionally prefixed by "Peso")
 * is accepted so dates, ages and unrelated numbers are not mistaken for weight.
 */
internal object WeightMeasurementParser {
    private val withUnit = Regex(
        """(?<![\d.,])(\d{1,3}(?:[.,]\d{1,2})?)\s*kg\b""",
        RegexOption.IGNORE_CASE
    )
    private val standalone = Regex(
        """^\s*(?:peso\s*[:=-]?\s*)?(\d{1,3}(?:[.,]\d{1,2})?)\s*$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(raw: String): Float? {
        val match = withUnit.find(raw) ?: standalone.matchEntire(raw)
        val value = match?.groupValues?.getOrNull(1)
            ?.replace(',', '.')
            ?.toFloatOrNull()
            ?: return null
        return value.takeIf { it in 0.1f..150f }
    }
}
