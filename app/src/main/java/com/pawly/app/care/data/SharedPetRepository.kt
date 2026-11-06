package com.pawly.app.care.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class PetShareMember(val uid: String, val username: String, val displayName: String, val role: String)
data class PetShareInvitation(
    val id: String, val sharedPetId: String, val petName: String,
    val ownerName: String, val ownerUid: String, val role: String, val status: String
)

/** Collaboration for pet profile access. Health records remain owner-private in cambios23. */
class SharedPetRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun uid(): String = auth.currentUser?.uid ?: error("Inicia sesión para compartir perfiles.")
    private fun sharedPetId(petId: String): String {
        require(petId.isNotBlank() && petId.length <= 100)
        return "${uid()}_${petId}"
    }
    private fun petRef(id: String) = db.collection("sharedPets").document(id)

    suspend fun ensureOwnedPet(petId: String, petName: String) {
        val uid = uid()
        val id = sharedPetId(petId)
        val ref = petRef(id)
        db.runTransaction { tx ->
            val snapshot = tx.get(ref)
            if (!snapshot.exists()) {
                tx.set(ref, mapOf(
                    "id" to id, "petId" to petId, "name" to petName.trim().take(40),
                    "ownerUid" to uid, "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                tx.set(ref.collection("members").document(uid), mapOf(
                    "uid" to uid, "role" to "owner", "joinedAt" to FieldValue.serverTimestamp()
                ))
            } else {
                check(snapshot.getString("ownerUid") == uid) { "Este perfil compartido no pertenece a tu cuenta." }
                tx.update(ref, mapOf("name" to petName.trim().take(40), "updatedAt" to FieldValue.serverTimestamp()))
            }
        }.await()
    }

    suspend fun inviteByUsername(petId: String, username: String) {
        val clean = username.trim().removePrefix("@").lowercase()
        require(clean.matches(Regex("[a-z0-9_.]{3,24}"))) { "Escribe un nombre de usuario válido." }
        val ownerUid = uid()
        val shareId = sharedPetId(petId)
        val pet = petRef(shareId).get().await()
        check(pet.exists() && pet.getString("ownerUid") == ownerUid) { "Solo quien creó el perfil puede invitar." }
        val usernameDoc = db.collection("usernames").document(clean).get().await()
        val inviteeUid = usernameDoc.getString("uid") ?: error("No existe una cuenta con ese nombre de usuario.")
        require(inviteeUid != ownerUid) { "No puedes invitarte a ti mismo." }
        check(!petRef(shareId).collection("members").document(inviteeUid).get().await().exists()) {
            "Esta persona ya tiene acceso al perfil."
        }
        val duplicate = db.collection("petInvitations").whereEqualTo("sharedPetId", shareId)
            .whereEqualTo("inviteeUid", inviteeUid).whereEqualTo("status", "pending").limit(1).get().await()
        check(duplicate.isEmpty) { "Ya hay una invitación pendiente para esta persona." }
        val ownerProfile = db.collection("users").document(ownerUid).get().await()
        db.collection("petInvitations").document(UUID.randomUUID().toString()).set(mapOf(
            "sharedPetId" to shareId, "petName" to (pet.getString("name") ?: "Mascota"),
            "ownerUid" to ownerUid,
            "ownerName" to (ownerProfile.getString("displayName") ?: ownerProfile.getString("username") ?: "Usuario Pawly"),
            "inviteeUid" to inviteeUid, "inviteeUsername" to clean,
            "role" to "editor", "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun getMembers(petId: String): List<PetShareMember> {
        return petRef(sharedPetId(petId)).collection("members").get().await().documents.map { doc ->
            PetShareMember(doc.id, doc.getString("username").orEmpty(),
                doc.getString("displayName") ?: doc.getString("username") ?: "Cuidador",
                doc.getString("role") ?: "editor")
        }
    }

    suspend fun getMyInvitations(): List<PetShareInvitation> {
        return db.collection("petInvitations").whereEqualTo("inviteeUid", uid())
            .whereEqualTo("status", "pending").limit(30).get().await().documents.map { doc ->
                PetShareInvitation(
                    doc.id, doc.getString("sharedPetId") ?: "", doc.getString("petName") ?: "Mascota",
                    doc.getString("ownerName") ?: "Usuario Pawly", doc.getString("ownerUid") ?: "",
                    doc.getString("role") ?: "editor", doc.getString("status") ?: "pending"
                )
            }
    }

    suspend fun respondToInvitation(invitationId: String, accept: Boolean) {
        val uid = uid()
        val ref = db.collection("petInvitations").document(invitationId)
        db.runTransaction { tx ->
            val invite = tx.get(ref)
            check(invite.exists() && invite.getString("inviteeUid") == uid &&
                invite.getString("status") == "pending") { "La invitación ya no está disponible." }
            if (accept) {
                val memberRef = petRef(invite.getString("sharedPetId") ?: "").collection("members").document(uid)
                tx.set(memberRef, mapOf(
                    "uid" to uid, "role" to (invite.getString("role") ?: "editor"),
                    "invitationId" to invitationId, "joinedAt" to FieldValue.serverTimestamp()
                ))
            }
            tx.update(ref, mapOf("status" to if (accept) "accepted" else "declined",
                "respondedAt" to FieldValue.serverTimestamp()))
        }.await()
    }

    suspend fun removeMember(petId: String, memberUid: String) {
        require(memberUid != uid()) { "No puedes quitarte como propietario." }
        val ref = petRef(sharedPetId(petId))
        check(ref.get().await().getString("ownerUid") == uid()) { "Solo el propietario puede gestionar el acceso." }
        ref.collection("members").document(memberUid).delete().await()
    }
}
