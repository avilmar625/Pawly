package com.pawly.app.care.data

/** Pure input validation shared by the profile repository and unit tests. */
internal object PetProfileValidation {
    fun name(value: String): String {
        val clean = value.trim()
        require(clean.isNotBlank() && clean.length <= 40) { "El nombre debe tener entre 1 y 40 caracteres." }
        return clean
    }

    fun photoUrl(value: String?): String? {
        require(value == null || (value.startsWith("https://") && value.length <= 2048)) {
            "La fotografía debe tener una URL HTTPS válida."
        }
        return value
    }

    fun diary(entries: List<String>): List<String> {
        require(entries.size <= 200) { "El diario admite hasta 200 recuerdos por mascota." }
        val clean = entries.map { it.trim() }
        require(clean.all { it.isNotBlank() && it.length <= 1000 }) {
            "Cada recuerdo debe tener entre 1 y 1.000 caracteres."
        }
        return clean
    }
}
