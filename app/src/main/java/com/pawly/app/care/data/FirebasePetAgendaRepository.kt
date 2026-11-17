package com.pawly.app.care.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/** Realtime, per-document agenda shared by every accepted carer of a pet. */
class FirebasePetAgendaRepository(
    private val ownerUid: String,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun uid(): String = auth.currentUser?.uid ?: error("Inicia sesión para sincronizar la agenda.")

    private fun collection(petId: String) = db.collection("sharedPets")
        .document("${ownerUid}_${petId}")
        .collection("agenda")

    suspend fun canEditAgenda(petId: String): Boolean {
        val member = db.collection("sharedPets").document("${ownerUid}_${petId}")
            .collection("members").document(uid()).get().await()
        return member.getString("role") in setOf("owner", "editor")
    }

    fun listenReminders(
        petId: String,
        onReminders: (List<String>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration = collection(petId).orderBy("date")
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error.localizedMessage ?: "No se pudo sincronizar la agenda.")
                return@addSnapshotListener
            }
            onReminders(snapshot?.documents.orEmpty().map { doc ->
                JSONObject()
                    .put("id", doc.id)
                    .put("title", doc.getString("title").orEmpty())
                    .put("date", doc.getLong("date") ?: 0L)
                    .put("category", doc.getString("category") ?: "Personal")
                    .toString()
            })
        }

    /** One-time migration: seed the shared agenda only when it has no cloud records yet. */
    suspend fun migrateLocalIfCloudEmpty(petId: String, localReminders: List<String>) {
        if (collection(petId).limit(1).get().await().isEmpty && localReminders.isNotEmpty()) {
            localReminders.distinct().forEach { raw ->
                runCatching { saveReminder(petId, raw) }
            }
        }
    }

    suspend fun saveReminder(petId: String, raw: String) {
        val json = JSONObject(raw)
        val id = json.optString("id")
        val title = json.optString("title").trim()
        val date = json.optLong("date", -1L)
        val category = json.optString("category", "Personal")
        require(id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Identificador de recordatorio no válido." }
        require(title.length in 2..100) { "El título debe tener entre 2 y 100 caracteres." }
        require(date in 0..4102444800000L) { "La fecha del recordatorio no es válida." }
        require(category in setOf("Personal", "Veterinario", "Cuidado", "Paseo", "Viaje")) { "Categoría de recordatorio no válida." }
        val ref = collection(petId).document(id)
        val existing = ref.get().await()
        if (existing.exists()) {
            ref.update(mapOf(
                "title" to title,
                "date" to date,
                "category" to category,
                "updatedAt" to FieldValue.serverTimestamp()
            )).await()
        } else {
            ref.set(mapOf(
                "id" to id,
                "title" to title,
                "date" to date,
                "category" to category,
                "createdBy" to uid(),
                "updatedAt" to FieldValue.serverTimestamp()
            )).await()
        }
    }

    suspend fun deleteReminder(petId: String, reminderId: String) {
        require(reminderId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Identificador de recordatorio no válido." }
        collection(petId).document(reminderId).delete().await()
    }
}
