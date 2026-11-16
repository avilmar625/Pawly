package com.pawly.app.care.domain

import java.security.MessageDigest
import java.util.Locale

/** Stable preference keys for daily routine state, isolated per pet profile. */
internal object PetRoutinePreferenceKeys {
    fun scope(petName: String): String {
        val normalized = petName.trim().lowercase(Locale.ROOT)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { byte -> "%02x".format(byte) }
    }

    fun day(scope: String) = "routine_day_$scope"
    fun walkDone(scope: String) = "walk_done_$scope"
    fun medicineDone(scope: String) = "medicine_done_$scope"
}
