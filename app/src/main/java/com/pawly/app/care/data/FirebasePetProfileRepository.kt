package com.pawly.app.care.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.json.JSONObject
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

    suspend fun canEditDiary(petId: String): Boolean {
        val member = root(petId).collection("members").document(uid()).get().await()
        return member.getString("role") in setOf("owner", "editor")
    }

    suspend fun saveProfile(petId: String, name: String, photoUrl: String?) {
        require(canEditDiary(petId)) { "Tu permiso en este perfil es de solo lectura." }
        val cleanName = PetProfileValidation.name(name)
        val cleanPhotoUrl = PetProfileValidation.photoUrl(photoUrl)
        profile(petId).set(mapOf(
            "petId" to petId,
            "name" to cleanName,
            "photoUrl" to cleanPhotoUrl,
            "updatedAt" to FieldValue.serverTimestamp()
        )).await()
    }

    private fun withCloudDiaryId(raw: String, id: String): String = runCatching {
        JSONObject(raw).put("pawlyCloudId", id).toString()
    }.getOrDefault(raw)

    private fun withoutCloudDiaryId(raw: String): String = runCatching {
        JSONObject(raw).remove("pawlyCloudId").toString()
    }.getOrDefault(raw)

    suspend fun loadDiary(petId: String): List<String> =
        diary(petId).orderBy("position").limit(500).get().await().documents.mapNotNull { doc ->
            doc.getString("text")?.takeIf { it.isNotBlank() }?.let { withCloudDiaryId(it, doc.id) }
        }

    /** Backup view strips device/cloud bookkeeping while retaining the full diary payload. */
    suspend fun loadDiaryForBackup(petId: String): List<String> =
        loadDiary(petId).map(::withoutCloudDiaryId)

    fun listenDiary(
        petId: String,
        onEntries: (List<String>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration = diary(petId).orderBy("position").limit(200)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error.localizedMessage ?: "No se pudo sincronizar el diario.")
                return@addSnapshotListener
            }
            onEntries(snapshot?.documents.orEmpty().mapNotNull { doc ->
                doc.getString("text")?.takeIf { it.isNotBlank() }?.let { withCloudDiaryId(it, doc.id) }
            })
        }

    /** Adds one immutable diary item. Each memory has its own document, so concurrent carers never replace each other's entries. */
    suspend fun addDiaryEntry(petId: String, rawEntry: String) {
        require(canEditDiary(petId)) { "Tu permiso en este perfil es de solo lectura." }
        val clean = PetProfileValidation.diary(listOf(withoutCloudDiaryId(rawEntry))).single()
        val collection = diary(petId)
        val existing = collection.limit(201).get().await()
        require(existing.size() < 200) { "El diario admite hasta 200 recuerdos por mascota." }
        val id = java.util.UUID.randomUUID().toString()
        collection.document(id).set(mapOf(
            "id" to id,
            "text" to clean,
            "position" to 0,
            "updatedAt" to FieldValue.serverTimestamp()
        )).await()
    }

    /** Deletes only the selected memory document; it never rewrites the other carer's entries. */
    suspend fun deleteDiaryEntry(petId: String, rawEntry: String) {
        require(canEditDiary(petId)) { "Tu permiso en este perfil es de solo lectura." }
        val id = runCatching { JSONObject(rawEntry).optString("pawlyCloudId").takeIf { it.isNotBlank() } }.getOrNull()
        val collection = diary(petId)
        val target = id?.let { collection.document(it) } ?: collection.whereEqualTo("text", withoutCloudDiaryId(rawEntry)).limit(1).get().await().documents.firstOrNull()?.reference
        target?.delete()?.await()
    }

    /** Imports backup entries additively. Existing cloud memories are never overwritten or removed. */
    suspend fun mergeDiary(petId: String, entries: List<String>) {
        require(canEditDiary(petId)) { "Tu permiso en este perfil es de solo lectura." }
        val existing = loadDiary(petId).map(::withoutCloudDiaryId).toSet()
        PetProfileValidation.diary(entries).map(::withoutCloudDiaryId).distinct()
            .filterNot { it in existing }
            .forEach { addDiaryEntry(petId, it) }
    }

    /** Kept for source compatibility; this method now merges instead of destructively replacing shared memories. */
    suspend fun replaceDiary(petId: String, entries: List<String>) = mergeDiary(petId, entries)
}
