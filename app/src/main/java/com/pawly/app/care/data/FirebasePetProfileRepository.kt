package com.pawly.app.care.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Cloud profile and memory book for one pet.
 *
 * Documents live below sharedPets/{ownerUid}_{petId}; accepted members inherit
 * the role-based access rules already used by the health passport and care plan.
 * Images are stored in Cloudinary; Firestore only stores their HTTPS URL.
 */
data class CloudPetProfile(
    val name: String,
    val photoUrl: String?
)

class FirebasePetProfileRepository(
    private val ownerUid: String,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun uid(): String = auth.currentUser?.uid
        ?: error("Inicia sesión para sincronizar el perfil.")
    private fun sharedPetId(petId: String): String {
        require(petId.isNotBlank() && petId.length <= 100)
        return "${ownerUid}_$petId"
    }
    private fun root(petId: String) = db.collection("sharedPets").document(sharedPetId(petId))
    private fun profile(petId: String) = root(petId).collection("profile").document("current")
    private fun diary(petId: String) = root(petId).collection("diary")

    suspend fun ensurePet(petId: String, name: String) {
        require(uid() == ownerUid) { "Solo el propietario puede preparar este perfil." }
        SharedPetRepository(auth, db).ensureOwnedPet(petId, name)
    }

    suspend fun loadProfile(petId: String): CloudPetProfile? {
        val snapshot = profile(petId).get().await()
        if (!snapshot.exists()) return null
        return CloudPetProfile(
            name = snapshot.getString("name").orEmpty(),
            photoUrl = snapshot.getString("photoUrl")?.takeIf { it.startsWith("https://") }
        )
    }

    suspend fun saveProfile(petId: String, name: String, photoUrl: String?) {
        require(uid() == ownerUid) { "Solo el propietario puede modificar este perfil." }
        val cleanName = name.trim()
        require(cleanName.isNotBlank() && cleanName.length <= 40)
        require(photoUrl == null || (photoUrl.startsWith("https://") && photoUrl.length <= 2048))
        profile(petId).set(mapOf(
            "petId" to petId,
            "name" to cleanName,
            "photoUrl" to photoUrl,
            "updatedAt" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun loadDiary(petId: String): List<String> =
        diary(petId).orderBy("position").limit(200).get().await().documents.mapNotNull { doc ->
            doc.getString("text")?.takeIf { it.isNotBlank() }
        }

    /** Replaces the bounded memory book atomically, preserving the visible order. */
    suspend fun replaceDiary(petId: String, entries: List<String>) {
        require(uid() == ownerUid) { "Solo el propietario puede modificar el diario." }
        require(entries.size <= 200) { "El diario admite hasta 200 recuerdos por mascota." }
        require(entries.all { it.isNotBlank() && it.length <= 1000 }) {
            "Cada recuerdo debe tener entre 1 y 1.000 caracteres."
        }
        val collection = diary(petId)
        val existing = collection.get().await()
        require(existing.size() <= 450) {
            "El diario remoto tiene demasiados registros para actualizarse de una vez."
        }
        val batch = db.batch()
        val retainedIds = entries.indices.map { index -> "entry_${index.toString().padStart(3, '0')}" }.toSet()
        entries.forEachIndexed { index, text ->
            val id = "entry_${index.toString().padStart(3, '0')}"
            batch.set(collection.document(id), mapOf(
                "id" to id,
                "text" to text.trim(),
                "position" to index,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }
        existing.documents.filterNot { it.id in retainedIds }.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }
}
