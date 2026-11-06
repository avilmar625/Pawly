package com.pawly.app.care.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pawly.app.care.model.CareTask
import com.pawly.app.care.model.PassportEntry
import kotlinx.coroutines.tasks.await

/**
 * Cloud persistence for pet-care records.
 *
 * By default records stay private under users/{uid}. When sharedPetOwnerUid is
 * supplied, records live below sharedPets/{ownerUid}_{petId}; Firestore rules
 * then enforce the accepted member's owner/editor/reader role.
 */
class FirebasePetCareRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val sharedPetOwnerUid: String? = null
) {
    private fun uid(): String = auth.currentUser?.uid
        ?: error("Inicia sesión para sincronizar los datos de salud.")

    private fun sharedId(petId: String): String {
        val owner = requireNotNull(sharedPetOwnerUid)
        require(owner.isNotBlank() && petId.isNotBlank() && petId.length <= 100)
        return "${owner}_${petId}"
    }

    /** Creates the shared space for its owner or verifies that the current user is a member. */
    suspend fun ensureSharedPet(petId: String, petName: String) {
        val owner = sharedPetOwnerUid
        if (owner == null) {
            // A previous Android build accidentally stored the owner's own records
            // in the shared-pet namespace. Copy them once to the private namespace.
            migrateLegacySharedRecords(petId)
            return
        }
        val id = sharedId(petId)
        if (uid() == owner) {
            SharedPetRepository(auth, db).ensureOwnedPet(petId, petName)
            migrateLegacyPrivateRecords(petId)
        } else {
            check(db.collection("sharedPets").document(id)
                .collection("members").document(uid()).get().await().exists()) {
                "No tienes acceso a este perfil compartido."
            }
        }
    }

    /**
     * Recover records written by the earlier implementation to the shared path
     * for the owner's own pet. Existing private records always take precedence.
     */
    private suspend fun migrateLegacySharedRecords(petId: String) {
        val currentUid = uid()
        val legacyRef = db.collection("sharedPets").document("${currentUid}_${petId}")
        val legacyPet = runCatching { legacyRef.get().await() }.getOrNull() ?: return
        if (!legacyPet.exists() || legacyPet.getString("ownerUid") != currentUid) return
        val member = runCatching {
            legacyRef.collection("members").document(currentUid).get().await()
        }.getOrNull() ?: return
        if (!member.exists()) return

        val privatePassport = passport(petId)
        if (privatePassport.limit(1).get().await().isEmpty) {
            val legacy = legacyRef.collection("passport").get().await()
            if (!legacy.isEmpty) {
                val batch = db.batch()
                legacy.documents.forEach { document ->
                    batch.set(privatePassport.document(document.id), document.data ?: emptyMap<String, Any>())
                }
                batch.commit().await()
            }
        }

        val privateTasks = tasks(petId)
        if (privateTasks.limit(1).get().await().isEmpty) {
            val legacy = legacyRef.collection("tasks").get().await()
            if (!legacy.isEmpty) {
                val batch = db.batch()
                legacy.documents.forEach { document ->
                    batch.set(privateTasks.document(document.id), document.data ?: emptyMap<String, Any>())
                }
                batch.commit().await()
            }
        }
    }

    /**
     * One-time, non-destructive migration from the previous owner-private paths.
     * Existing shared records always win; legacy documents are left untouched.
     */
    private suspend fun migrateLegacyPrivateRecords(petId: String) {
        val legacyRoot = db.collection("users").document(uid())
            .collection("petCare").document(petId)

        val sharedPassport = passport(petId)
        if (sharedPassport.limit(1).get().await().isEmpty) {
            val legacy = legacyRoot.collection("passport").get().await()
            if (!legacy.isEmpty) {
                val batch = db.batch()
                legacy.documents.forEach { document ->
                    batch.set(sharedPassport.document(document.id), document.data ?: emptyMap<String, Any>())
                }
                batch.commit().await()
            }
        }

        val sharedTasks = tasks(petId)
        if (sharedTasks.limit(1).get().await().isEmpty) {
            val legacy = legacyRoot.collection("tasks").get().await()
            if (!legacy.isEmpty) {
                val batch = db.batch()
                legacy.documents.forEach { document ->
                    batch.set(sharedTasks.document(document.id), document.data ?: emptyMap<String, Any>())
                }
                batch.commit().await()
            }
        }
    }

    suspend fun canEditSharedPet(petId: String): Boolean {
        if (sharedPetOwnerUid == null) return true
        val member = db.collection("sharedPets").document(sharedId(petId))
            .collection("members").document(uid()).get().await()
        return member.getString("role") in setOf("owner", "editor")
    }

    private fun passport(petId: String) =
        if (sharedPetOwnerUid == null) {
            db.collection("users").document(uid()).collection("petCare").document(petId).collection("passport")
        } else {
            db.collection("sharedPets").document(sharedId(petId)).collection("passport")
        }

    private fun tasks(petId: String) =
        if (sharedPetOwnerUid == null) {
            db.collection("users").document(uid()).collection("petCare").document(petId).collection("tasks")
        } else {
            db.collection("sharedPets").document(sharedId(petId)).collection("tasks")
        }

    private fun passportData(entry: PassportEntry) = mapOf(
        "id" to entry.id, "type" to entry.type.trim().take(40),
        "title" to entry.title.trim(), "detail" to entry.detail.trim(), "date" to entry.date.trim()
    )

    private fun taskData(task: CareTask) = mapOf(
        "id" to task.id, "title" to task.title.trim(),
        "responsible" to task.responsible.trim(), "frequency" to task.frequency.trim(),
        "completed" to task.completed
    )

    suspend fun savePassportEntry(petId: String, entry: PassportEntry) {
        require(petId.isNotBlank() && entry.id.isNotBlank())
        require(entry.title.isNotBlank() && entry.title.length <= 120)
        require(entry.detail.length <= 1200 && entry.date.length <= 40 && entry.type.length <= 40)
        passport(petId).document(entry.id).set(passportData(entry)).await()
    }

    suspend fun savePassportEntries(petId: String, entries: List<PassportEntry>) {
        require(petId.isNotBlank() && entries.size <= 450)
        val batch = db.batch()
        entries.forEach { entry ->
            require(entry.id.isNotBlank() && entry.title.isNotBlank())
            require(entry.title.length <= 120 && entry.detail.length <= 1200)
            require(entry.date.length <= 40 && entry.type.length <= 40)
            batch.set(passport(petId).document(entry.id), passportData(entry))
        }
        if (entries.isNotEmpty()) batch.commit().await()
    }

    suspend fun loadPassportEntries(petId: String): List<PassportEntry> =
        passport(petId).get().await().documents.mapNotNull { doc ->
            val title = doc.getString("title") ?: return@mapNotNull null
            PassportEntry(doc.id, doc.getString("type").orEmpty(), title,
                doc.getString("detail").orEmpty(), doc.getString("date").orEmpty())
        }.sortedByDescending { it.date }

    suspend fun deletePassportEntry(petId: String, entryId: String) {
        require(petId.isNotBlank() && entryId.isNotBlank())
        passport(petId).document(entryId).delete().await()
    }

    suspend fun saveCareTask(petId: String, task: CareTask) {
        require(petId.isNotBlank() && task.id.isNotBlank())
        require(task.title.isNotBlank() && task.title.length <= 120)
        require(task.responsible.length <= 80 && task.frequency.length <= 80)
        tasks(petId).document(task.id).set(taskData(task)).await()
    }

    suspend fun saveCareTasks(petId: String, careTasks: List<CareTask>) {
        require(petId.isNotBlank() && careTasks.size <= 450)
        val batch = db.batch()
        careTasks.forEach { task ->
            require(task.id.isNotBlank() && task.title.isNotBlank())
            require(task.title.length <= 120 && task.responsible.length <= 80 && task.frequency.length <= 80)
            batch.set(tasks(petId).document(task.id), taskData(task))
        }
        if (careTasks.isNotEmpty()) batch.commit().await()
    }

    suspend fun loadCareTasks(petId: String): List<CareTask> =
        tasks(petId).get().await().documents.mapNotNull { doc ->
            val title = doc.getString("title") ?: return@mapNotNull null
            CareTask(doc.id, title, doc.getString("responsible").orEmpty(),
                doc.getString("frequency").orEmpty(), doc.getBoolean("completed") ?: false)
        }

    suspend fun deleteCareTask(petId: String, taskId: String) {
        require(petId.isNotBlank() && taskId.isNotBlank())
        tasks(petId).document(taskId).delete().await()
    }
}
