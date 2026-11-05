package com.pawly.app.care.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pawly.app.care.model.CareTask
import com.pawly.app.care.model.PassportEntry
import kotlinx.coroutines.tasks.await

/**
 * Cloud persistence for private pet-care records.
 *
 * Records are isolated below users/{uid}; Firestore rules enforce ownership.
 * This repository deliberately does not migrate local SharedPreferences data
 * automatically. Migration must happen after the account owner is known and
 * after the UI can present/confirm the import to avoid cross-account leakage.
 */
class FirebasePetCareRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun uid(): String = auth.currentUser?.uid
        ?: error("Inicia sesión para sincronizar los datos de salud.")

    private fun passport(petId: String) =
        db.collection("users").document(uid())
            .collection("petCare").document(petId)
            .collection("passport")

    private fun tasks(petId: String) =
        db.collection("users").document(uid())
            .collection("petCare").document(petId)
            .collection("tasks")

    suspend fun savePassportEntry(petId: String, entry: PassportEntry) {
        require(petId.isNotBlank() && entry.id.isNotBlank())
        require(entry.title.isNotBlank() && entry.title.length <= 120)
        require(entry.detail.length <= 1200 && entry.date.length <= 40 && entry.type.length <= 40)
        passport(petId).document(entry.id).set(
            mapOf(
                "id" to entry.id,
                "type" to entry.type,
                "title" to entry.title.trim(),
                "detail" to entry.detail.trim(),
                "date" to entry.date.trim()
            )
        ).await()
    }

    suspend fun loadPassportEntries(petId: String): List<PassportEntry> =
        passport(petId).get().await().documents.mapNotNull { doc ->
            val title = doc.getString("title") ?: return@mapNotNull null
            PassportEntry(
                id = doc.id,
                type = doc.getString("type").orEmpty(),
                title = title,
                detail = doc.getString("detail").orEmpty(),
                date = doc.getString("date").orEmpty()
            )
        }.sortedByDescending { it.date }

    suspend fun deletePassportEntry(petId: String, entryId: String) {
        require(petId.isNotBlank() && entryId.isNotBlank())
        passport(petId).document(entryId).delete().await()
    }

    suspend fun saveCareTask(petId: String, task: CareTask) {
        require(petId.isNotBlank() && task.id.isNotBlank())
        require(task.title.isNotBlank() && task.title.length <= 120)
        require(task.responsible.length <= 80 && task.frequency.length <= 80)
        tasks(petId).document(task.id).set(
            mapOf(
                "id" to task.id,
                "title" to task.title.trim(),
                "responsible" to task.responsible.trim(),
                "frequency" to task.frequency.trim(),
                "completed" to task.completed
            )
        ).await()
    }

    suspend fun loadCareTasks(petId: String): List<CareTask> =
        tasks(petId).get().await().documents.mapNotNull { doc ->
            val title = doc.getString("title") ?: return@mapNotNull null
            CareTask(
                id = doc.id,
                title = title,
                responsible = doc.getString("responsible").orEmpty(),
                frequency = doc.getString("frequency").orEmpty(),
                completed = doc.getBoolean("completed") ?: false
            )
        }

    suspend fun deleteCareTask(petId: String, taskId: String) {
        require(petId.isNotBlank() && taskId.isNotBlank())
        tasks(petId).document(taskId).delete().await()
    }
}
