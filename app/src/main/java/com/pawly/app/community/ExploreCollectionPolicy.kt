package com.pawly.app.community

import java.util.UUID

/** Pure validation and stable identity rules for a user's private Explore collections. */
internal object ExploreCollectionPolicy {
    fun validate(name: String, description: String, entryCount: Int) {
        require(name.isNotBlank() && name.length <= 32) { "El nombre debe tener entre 1 y 32 caracteres." }
        require(description.length <= 120) { "La descripción no puede superar 120 caracteres." }
        require(entryCount in 0..100) { "Puedes guardar como máximo 100 elementos por colección." }
    }

    fun documentId(name: String): String =
        UUID.nameUUIDFromBytes(name.trim().lowercase().toByteArray(Charsets.UTF_8)).toString()
}
