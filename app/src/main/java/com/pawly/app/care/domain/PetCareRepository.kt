package com.pawly.app.care.domain

import com.pawly.app.care.model.CareTask
import com.pawly.app.care.model.PassportEntry

/**
 * Platform-independent contract for pet-care persistence.
 *
 * The Android application currently supplies a Firebase implementation. Keeping
 * Firebase types, Android Context and Firestore documents out of this contract
 * makes it possible to add another implementation (local database or iOS) without
 * changing the care feature's business-facing API.
 */
interface PetCareRepository {
    suspend fun ensureSharedPet(petId: String, petName: String)
    suspend fun canEditSharedPet(petId: String): Boolean

    suspend fun savePassportEntry(petId: String, entry: PassportEntry)
    suspend fun savePassportEntries(petId: String, entries: List<PassportEntry>)
    suspend fun loadPassportEntries(petId: String): List<PassportEntry>
    suspend fun deletePassportEntry(petId: String, entryId: String)

    suspend fun saveCareTask(petId: String, task: CareTask)
    suspend fun saveCareTasks(petId: String, careTasks: List<CareTask>)
    suspend fun loadCareTasks(petId: String): List<CareTask>
    suspend fun deleteCareTask(petId: String, taskId: String)
}
