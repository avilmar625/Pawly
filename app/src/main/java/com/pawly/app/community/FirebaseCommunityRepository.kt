package com.pawly.app

import com.pawly.app.community.ChatMessagePolicy
import com.pawly.app.community.ProfessionalContactPolicy
import com.pawly.app.community.ExploreCollectionPolicy
import android.content.Context
import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import com.pawly.app.community.FcmTokenStore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.ZoneId
import java.util.Date

/**
 * Firebase-backed community data source. It is only created when google-services.json
 * has been supplied; the rest of Pawly remains usable without a Firebase project.
 */
data class PawlyExploreCollection(
    val name: String,
    val description: String,
    val entries: Set<String>
)

class FirebaseCommunityRepository private constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val appContext: Context
) {
    private val backendHttpClient = OkHttpClient()
    val currentUid: String? get() = auth.currentUser?.uid
    val currentEmail: String? get() = auth.currentUser?.email
    val isEmailVerified: Boolean get() = auth.currentUser?.isEmailVerified == true

    // Short-lived in-memory snapshots let Compose draw the profile immediately while
    // background refreshes read the latest Firestore values.
    private var cachedMyProfileState: PawlyUser? = null
    private var cachedMyDogsState: List<PawlyDog>? = null

    fun cachedMyProfile(): PawlyUser? = cachedMyProfileState
    fun cachedMyDogs(): List<PawlyDog>? = cachedMyDogsState

    /** Loads the signed-in user's private Home pet selection, if one has been saved. */
    suspend fun loadHomePetState(): Pair<String, Set<String>>? {
        val uid = requireUid()
        val snapshot = db.collection("users").document(uid)
            .collection("privateData").document("home").get().await()
        if (!snapshot.exists()) return null
        val name = snapshot.getString("dogName")?.trim().orEmpty()
        if (name.isBlank()) return null
        val profiles = ((snapshot.get("dogProfiles") as? List<*>)?.filterIsInstance<String>()
            ?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()) + name
        return name to profiles
    }

    /** Saves the Home pet names privately so the selection follows the account across devices. */
    suspend fun saveHomePetState(name: String, profiles: Set<String>) {
        val uid = requireUid()
        val cleanName = name.trim()
        require(cleanName.isNotBlank() && cleanName.length <= 40) { "El nombre debe tener entre 1 y 40 caracteres." }
        val cleanProfiles = (listOf(cleanName) + profiles.map { it.trim() })
            .filter { it.isNotBlank() }.distinct()
        require(cleanProfiles.size <= 100) { "Puedes guardar como máximo 100 perfiles de mascota." }
        db.collection("users").document(uid).collection("privateData").document("home")
            .set(mapOf(
                "dogName" to cleanName,
                "dogProfiles" to cleanProfiles,
                "updatedAt" to FieldValue.serverTimestamp()
            )).await()
    }

    companion object {
        fun create(context: Context): FirebaseCommunityRepository? = runCatching {
            val app = FirebaseApp.initializeApp(context.applicationContext) ?: return null
            FirebaseCommunityRepository(
                FirebaseAuth.getInstance(app),
                FirebaseFirestore.getInstance(app),
                context.applicationContext
            )
        }.getOrNull()
    }

    suspend fun register(email: String, password: String, username: String) {
        val cleanName = CommunityInputRules.validateUsername(username)
        val cleanEmail = CommunityInputRules.validateEmail(email)
        CommunityInputRules.validatePassword(password)
        val normalized = cleanName.lowercase()
        val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
        val uid = result.user?.uid ?: error("Firebase no devolvió el identificador de la cuenta.")
        val usernameRef = db.collection("usernames").document(normalized)
        val reservationRef = db.collection("usernameReservations").document(uid)
        val userRef = db.collection("users").document(uid)

        try {
            // One atomic batch: the three Firestore rules validate the related documents
            // through getAfter/existsAfter, avoiding a partially-created account.
            val batch = db.batch()
            batch.set(usernameRef, mapOf("uid" to uid))
            batch.set(reservationRef, mapOf("username" to normalized))
            batch.set(userRef, mapOf(
                "uid" to uid,
                "username" to cleanName,
                "usernameLower" to normalized,
                "displayName" to cleanName,
                "bio" to "",
                "photoUrl" to "",
                "isPublic" to false,
                "followersCount" to 0,
                "followingCount" to 0,
                "accountType" to "personal",
                "professionalCategory" to "",
                "professionalLocation" to "",
                "professionalDescription" to "",
                "professionalContactPublic" to false,
                "dogProfiles" to emptyList<Map<String, Any>>(),
                "createdAt" to FieldValue.serverTimestamp()
            ))
            batch.commit().await()
        } catch (error: Exception) {
            // Do not leave an Auth account without a matching Pawly profile.
            runCatching { result.user?.delete()?.await() }
            cachedMyProfileState = null
            cachedMyDogsState = null
            throw error
        }

        cachedMyProfileState = PawlyUser(
            uid = uid, username = cleanName, usernameLower = normalized,
            displayName = cleanName, isPublic = false
        )
        cachedMyDogsState = emptyList()

        // Email delivery is best-effort. A mail-provider/network failure must not
        // delete a valid account after the Firestore batch has already succeeded.
        runCatching { result.user?.sendEmailVerification()?.await() }
    }

        suspend fun signIn(email: String, password: String) {
        val cleanEmail = CommunityInputRules.validateEmail(email)
        require(password.isNotEmpty()) { "Escribe tu contraseña." }
        auth.signInWithEmailAndPassword(cleanEmail, password).await()
        ensureMyProfile()
    }

    /**
     * Signs in with a Google ID token obtained through Android Credential Manager.
     * A first-time Google user receives a Pawly profile and a generated username.
     */
    suspend fun signInWithGoogle(idToken: String) {
        require(idToken.isNotBlank()) { "Google no ha devuelto un token de acceso válido." }
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
        ensureMyProfile()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(CommunityInputRules.validateEmail(email)).await()
    }

    suspend fun resendVerificationEmail() {
        auth.currentUser?.sendEmailVerification()?.await() ?: error("Inicia sesión para verificar tu correo.")
    }

    suspend fun refreshAuthentication() {
        auth.currentUser?.reload()?.await()
    }

    suspend fun syncMessagingToken() {
        val uid = currentUid ?: return
        val token = FirebaseMessaging.getInstance().token.await()
        FcmTokenStore.save(uid, token).await()
    }

    suspend fun requestAccountDeletion() {
        val uid = requireUid()
        // One stable document per account prevents concurrent taps or repeated
        // requests from creating an unbounded queue of duplicate deletion jobs.
        val requestRef = db.collection("accountDeletionRequests").document(uid)
        val existing = requestRef.get().await()
        if (existing.exists()) {
            if (existing.getString("status") == "pending") return
            error("Ya existe una solicitud de eliminación registrada para esta cuenta.")
        }
        requestRef.set(
            mapOf(
                "uid" to uid,
                "email" to (currentEmail ?: ""),
                "requestedAt" to FieldValue.serverTimestamp(),
                "status" to "pending",
                "source" to "android"
            )
        ).await()
    }

    suspend fun signOut() {
        val uid = currentUid
        if (uid != null) {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                FcmTokenStore.delete(uid, token).await()
            }
        }
        auth.signOut()
        cachedMyProfileState = null
        cachedMyDogsState = null
    }

    suspend fun updateProfile(
        displayName: String, bio: String, isPublic: Boolean,
        accountType: String = "personal", professionalCategory: String = "",
        professionalLocation: String = "", professionalDescription: String = "",
        professionalContact: String = "", professionalContactPublic: Boolean = false
    ) {
        require(displayName.trim().isNotBlank()) { "El nombre público no puede estar vacío." }
        require(accountType in setOf("personal", "professional")) { "Selecciona un tipo de perfil válido." }
        if (accountType == "professional") require(professionalCategory.isNotBlank()) { "Selecciona tu especialidad profesional." }
        val uid = ensureMyProfile().uid
        val cleanName = displayName.trim().take(50)
        val cleanBio = bio.trim().take(240)
        val cleanCategory = professionalCategory.trim().take(60)
        val cleanLocation = professionalLocation.trim().take(100)
        val cleanDescription = professionalDescription.trim().take(500)
        val cleanContact = professionalContact.trim().take(120)
        val userRef = db.collection("users").document(uid)
        val privateContactRef = userRef.collection("privateData").document("professionalContact")
        val showContact = ProfessionalContactPolicy.isPublic(accountType, professionalContactPublic, cleanContact)
        val batch = db.batch()
        batch.update(userRef, mapOf(
            "displayName" to cleanName, "bio" to cleanBio, "isPublic" to isPublic,
            "accountType" to accountType, "professionalCategory" to cleanCategory,
            "professionalLocation" to cleanLocation, "professionalDescription" to cleanDescription,
            "professionalContactPublic" to showContact,
            "professionalPublicContact" to if (showContact) cleanContact else ""
        ))
        if (cleanContact.isNotBlank()) batch.set(privateContactRef, mapOf("value" to cleanContact))
        else batch.delete(privateContactRef)
        batch.commit().await()
        cachedMyProfileState = cachedMyProfileState?.copy(
            displayName = cleanName, bio = cleanBio, isPublic = isPublic, accountType = accountType,
            professionalCategory = cleanCategory, professionalLocation = cleanLocation,
            professionalDescription = cleanDescription, professionalContact = cleanContact,
            professionalContactPublic = showContact
        )
    }
    suspend fun updateProfilePhoto(uri: Uri): String {
        val uid = ensureMyProfile().uid
        val url = uploadImage(uri, "profile-photos")
        db.collection("users").document(uid).update("photoUrl", url).await()
        cachedMyProfileState = cachedMyProfileState?.copy(photoUrl = url)
        return url
    }

        suspend fun uploadImage(uri: Uri, folder: String): String {
        val uid = requireVerifiedUid()
        return CloudinaryImageUploader(appContext).upload(uri, folder, uid)
    }

    suspend fun uploadVideo(uri: Uri, folder: String): String {
        val uid = requireVerifiedUid()
        return CloudinaryImageUploader(appContext).uploadVideo(uri, folder, uid)
    }

    suspend fun saveDog(dog: PawlyDog) {
        val uid = ensureMyProfile().uid
        val cleanName = dog.name.trim()
        require(cleanName.isNotBlank()) { "El perro necesita un nombre." }
        require(cleanName.length <= 40) { "El nombre no puede superar los 40 caracteres." }
        require(dog.species in setOf("Perro", "Gato", "Conejo", "Ave", "Roedor", "Reptil", "Otro")) { "Selecciona un tipo de mascota válido." }
        val ref = db.collection("users").document(uid)
        val id = dog.id.ifBlank { java.util.UUID.randomUUID().toString() }
        val dogData = mapOf(
            "id" to id,
            "name" to cleanName,
            "breed" to dog.breed.trim().take(60),
            "age" to dog.age.trim().take(30),
            "description" to dog.description.trim().take(240),
            "traits" to dog.traits.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(8),
            "photoUrl" to dog.photoUrl,
            "species" to dog.species
        )
        db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            val current = (snapshot.get("dogProfiles") as? List<Map<String, Any>>).orEmpty().toMutableList()
            val index = current.indexOfFirst { it["id"] == id }
            if (index >= 0) current[index] = dogData else current.add(dogData)
            transaction.update(ref, "dogProfiles", current)
        }.await()
        cachedMyDogsState = null
    }

    suspend fun deleteDog(dogId: String) {
        require(dogId.isNotBlank()) { "No se ha indicado qué perro eliminar." }
        val ref = db.collection("users").document(ensureMyProfile().uid)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            val current = (snapshot.get("dogProfiles") as? List<Map<String, Any>>).orEmpty()
            check(current.any { it["id"] == dogId }) { "No se ha encontrado ese perfil de perro." }
            transaction.update(ref, "dogProfiles", current.filterNot { it["id"] == dogId })
        }.await()
        cachedMyDogsState = cachedMyDogsState?.filterNot { it.id == dogId }
    }

    suspend fun getMyProfile(): PawlyUser? = ensureMyProfile()

    suspend fun refreshMyProfile(): PawlyUser {
        // ensureMyProfile refreshes Firestore, migrates legacy public contact data,
        // and loads the private contact for the account owner.
        val profile = ensureMyProfile()
        cachedMyProfileState = profile
        return profile
    }

    suspend fun getMyDogs(forceRefresh: Boolean = false): List<PawlyDog> {
        if (!forceRefresh) cachedMyDogsState?.let { return it }
        val uid = ensureMyProfile().uid
        val data = db.collection("users").document(uid).get().await().get("dogProfiles") as? List<Map<String, Any>>
        val dogs = data.orEmpty().map { map ->
            PawlyDog(
                id = map["id"] as? String ?: "",
                name = map["name"] as? String ?: "",
                breed = map["breed"] as? String ?: "",
                age = map["age"] as? String ?: "",
                description = map["description"] as? String ?: "",
                traits = (map["traits"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                photoUrl = map["photoUrl"] as? String ?: "",
                species = map["species"] as? String ?: "Perro"
            )
        }
        cachedMyDogsState = dogs
        return dogs
    }

    private suspend fun dispatchActivityPush(
        targetUid: String,
        type: String,
        relatedId: String = "",
        sourceEventId: String = ""
    ) {
        val baseUrl = BuildConfig.PAWLY_BACKEND_URL.trim().trimEnd('/')
        if (baseUrl.isBlank() || targetUid.isBlank()) return
        val idToken = auth.currentUser?.getIdToken(false)?.await()?.token ?: return
        val payload = JSONObject()
            .put("type", type)
            .put("recipientUid", targetUid)
            .put("relatedId", relatedId)
            .put("sourceEventId", sourceEventId)
        val request = Request.Builder()
            .url("$baseUrl/v1/activity-notifications")
            .header("Authorization", "Bearer $idToken")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        withContext(Dispatchers.IO) {
            backendHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("El servicio de notificaciones respondió ${response.code}.")
                }
            }
        }
    }

    private suspend fun createNotification(
        targetUid: String,
        type: String,
        message: String,
        relatedId: String = "",
        sourceEventId: String = ""
    ) {
        val actorUid = requireUid()
        if (targetUid.isBlank() || targetUid == actorUid) return
        val actor = getMyProfile()
        val safeActorName = actor?.displayName?.ifBlank { actor.username } ?: "Usuario Pawly"
        val notificationId = when (type) {
            "LIKE" -> "LIKE-$actorUid-$relatedId"
            "COMMENT" -> "COMMENT-$actorUid-$sourceEventId"
            "FOLLOW" -> "FOLLOW-$actorUid-$targetUid"
            else -> return
        }
        val notification = db.collection("users").document(targetUid)
            .collection("notifications").document(notificationId)
        try {
            // Rules verify the underlying like, comment or follower relationship.
            // Deterministic IDs prevent replaying the same interaction as new alerts.
            notification.set(mapOf(
                "actorId" to actorUid,
                "actorName" to safeActorName.take(80),
                "type" to type,
                "message" to message,
                "relatedId" to relatedId.take(128),
                "sourceEventId" to sourceEventId.take(128),
                "createdAt" to FieldValue.serverTimestamp(),
                "read" to false
            )).await()
        } catch (_: Exception) {
            // The interaction itself has already committed. Notification failure must
            // never make a successful like/comment/follow appear to have failed.
            // Firestore rules remain the security boundary, not this client-side guard.
        }
    }

    fun listenNotifications(onData: (List<PawlyNotification>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        val uid = requireUid()
        return db.collection("users").document(uid).collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(60)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(error.localizedMessage ?: "No se pudieron cargar las notificaciones."); return@addSnapshotListener }
                onData(snapshot?.documents.orEmpty().map { doc ->
                    PawlyNotification(
                        id = doc.id,
                        actorId = doc.getString("actorId") ?: "",
                        actorName = doc.getString("actorName") ?: "Usuario Pawly",
                        type = doc.getString("type") ?: "",
                        message = doc.getString("message") ?: "",
                        relatedId = doc.getString("relatedId") ?: "",
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0,
                        read = doc.getBoolean("read") ?: false
                    )
                })
            }
    }

    suspend fun markNotificationRead(notificationId: String) {
        require(notificationId.isNotBlank())
        db.collection("users").document(requireUid()).collection("notifications")
            .document(notificationId).update("read", true).await()
    }

    suspend fun markAllNotificationsRead() {
        val uid = requireUid()
        val docs = db.collection("users").document(uid).collection("notifications")
            .whereEqualTo("read", false).limit(60).get().await().documents
        docs.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.update(it.reference, "read", true) }
            batch.commit().await()
        }
    }

    fun listenFollowingIds(onData: (Set<String>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        val uid = requireUid()
        return db.collection("users").document(uid).collection("following")
            .addSnapshotListener { snapshot, error ->
                if (error != null) onError(error.localizedMessage ?: "No se pudo cargar la lista de seguidos.")
                else onData(snapshot?.documents.orEmpty().map { it.id }.toSet())
            }
    }

    fun listenFeed(onData: (List<PawlyPost>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        val uid = requireUid()
        var latestPosts = emptyList<PawlyPost>()
        var blockedIds = emptySet<String>()
        fun emit() { onData(latestPosts.filterNot { it.authorId in blockedIds }) }
        val postsListener = db.collection("posts").orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(error.localizedMessage ?: "No se pudo cargar el muro."); return@addSnapshotListener }
                latestPosts = snapshot?.documents.orEmpty().map { doc ->
                    val likedBy = doc.get("likedBy") as? List<*> ?: emptyList<Any>()
                    PawlyPost(
                        id = doc.id,
                        authorId = doc.getString("authorId") ?: "",
                        authorName = doc.getString("authorName") ?: "Usuario Pawly",
                        authorPhoto = doc.getString("authorPhoto") ?: "",
                        dogName = doc.getString("dogName") ?: "",
                        text = doc.getString("text") ?: "",
                        imageUrl = doc.getString("imageUrl") ?: "",
                        videoUrl = doc.getString("videoUrl") ?: "",
                        likeCount = doc.getLong("likeCount") ?: 0,
                        commentCount = doc.getLong("commentCount") ?: 0,
                        likedByMe = uid in likedBy,
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0,
                        category = doc.getString("category") ?: "STORY"
                    )
                }
                emit()
            }
        val blockedListener = db.collection("users").document(uid).collection("blocked")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(error.localizedMessage ?: "No se pudo cargar la lista de bloqueos."); return@addSnapshotListener }
                blockedIds = snapshot?.documents.orEmpty().map { it.id }.toSet()
                emit()
            }
        return object : ListenerRegistration {
            override fun remove() { postsListener.remove(); blockedListener.remove() }
        }
    }

    fun listenUserPosts(uid: String, onData: (List<PawlyPost>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        return db.collection("posts").whereEqualTo("authorId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(30)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(error.localizedMessage ?: "No se pudieron cargar las publicaciones."); return@addSnapshotListener }
                onData(snapshot?.documents.orEmpty().map { doc ->
                    val likedBy = doc.get("likedBy") as? List<*> ?: emptyList<Any>()
                    PawlyPost(
                        id = doc.id,
                        authorId = doc.getString("authorId") ?: "",
                        authorName = doc.getString("authorName") ?: "Usuario Pawly",
                        authorPhoto = doc.getString("authorPhoto") ?: "",
                        dogName = doc.getString("dogName") ?: "",
                        text = doc.getString("text") ?: "",
                        imageUrl = doc.getString("imageUrl") ?: "",
                        videoUrl = doc.getString("videoUrl") ?: "",
                        likeCount = doc.getLong("likeCount") ?: 0,
                        commentCount = doc.getLong("commentCount") ?: 0,
                        likedByMe = currentUid in likedBy,
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0,
                        category = doc.getString("category") ?: "STORY"
                    )
                })
            }
    }

    suspend fun publishPost(text: String, imageUrl: String, videoUrl: String, dogName: String, category: String = "STORY"): String {
        val uid = requireVerifiedUid()
        val user = getMyProfile() ?: error("Completa primero tu perfil.")
        CommunityInputRules.validatePost(text, imageUrl, videoUrl)
        require(category in setOf("STORY", "ADOPTION", "LOST", "FOUND", "FOSTER")) { "Selecciona una categoría válida." }
        val created = db.collection("posts").add(
            mapOf(
                "authorId" to uid,
                "authorName" to user.displayName.ifBlank { user.username },
                "authorPhoto" to user.photoUrl,
                "dogName" to dogName.take(40),
                "category" to category,
                "text" to text.trim().take(2000),
                "imageUrl" to imageUrl,
                "videoUrl" to videoUrl,
                "likeCount" to 0,
                "commentCount" to 0,
                "likedBy" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        return created.id
    }

    suspend fun toggleLike(postId: String) {
        val uid = requireVerifiedUid()
        val ref = db.collection("posts").document(postId)
        val postOwner = ref.get().await().getString("authorId").orEmpty()
        val added = db.runTransaction { tx ->
            val doc = tx.get(ref)
            val liked = (doc.get("likedBy") as? List<*>)?.contains(uid) == true
            tx.update(ref, "likedBy", if (liked) FieldValue.arrayRemove(uid) else FieldValue.arrayUnion(uid))
            tx.update(ref, "likeCount", FieldValue.increment(if (liked) -1 else 1))
            !liked
        }.await()
        if (added) {
            createNotification(postOwner, "LIKE", "Le ha gustado tu publicación.", postId, postId)
            runCatching { dispatchActivityPush(postOwner, "LIKE", postId, postId) }
        }
    }

    fun listenComments(postId: String, onData: (List<PawlyComment>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        db.collection("posts").document(postId).collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING).limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(error.localizedMessage ?: "No se pudieron cargar los comentarios."); return@addSnapshotListener }
                onData(snapshot?.documents.orEmpty().map { doc ->
                    PawlyComment(doc.id, doc.getString("authorId") ?: "", doc.getString("authorName") ?: "Usuario", doc.getString("text") ?: "", doc.getTimestamp("createdAt")?.toDate()?.time ?: 0)
                })
            }

    suspend fun addComment(postId: String, text: String) {
        val uid = requireVerifiedUid()
        val clean = CommunityInputRules.validateComment(text)
        val user = getMyProfile()
        val post = db.collection("posts").document(postId)
        val comment = post.collection("comments").document()
        db.runTransaction { tx ->
            tx.set(comment, mapOf(
                "authorId" to uid, "authorName" to (user?.displayName ?: "Usuario Pawly"),
                "text" to clean, "createdAt" to FieldValue.serverTimestamp()
            ))
            tx.update(post, mapOf(
                "commentCount" to FieldValue.increment(1),
                "lastCommentId" to comment.id
            ))
        }.await()
        val ownerUid = post.get().await().getString("authorId").orEmpty()
        createNotification(ownerUid, "COMMENT", "Ha comentado tu publicación.", postId, comment.id)
        runCatching { dispatchActivityPush(ownerUid, "COMMENT", postId, comment.id) }
    }

    suspend fun searchUsers(query: String): List<PawlyUser> {
        val clean = query.trim().lowercase()
        if (clean.length < 2) return emptyList()
        val docs = db.collection("users").whereEqualTo("isPublic", true).orderBy("usernameLower")
            .startAt(clean).endAt(clean + "\uf8ff").limit(25).get().await()
        val blocked = currentUid?.let { db.collection("users").document(it).collection("blocked").get().await().documents.map { doc -> doc.id }.toSet() }.orEmpty()
        return docs.documents.mapNotNull { it.toPawlyUser() }.filter { it.uid != currentUid && it.isPublic && it.uid !in blocked }
    }

    suspend fun getUser(uid: String): PawlyUser? = db.collection("users").document(uid).get().await()
        .toPawlyUser()?.let { profile ->
            if (profile.professionalContactPublic) profile.withPrivateProfessionalContact() else profile
        }

    suspend fun getUserDogs(uid: String): List<PawlyDog> {
        val data = db.collection("users").document(uid).get().await().get("dogProfiles") as? List<Map<String, Any>>
        return data.orEmpty().map { m ->
            PawlyDog(id = m["id"] as? String ?: "", name = m["name"] as? String ?: "", breed = m["breed"] as? String ?: "", age = m["age"] as? String ?: "", description = m["description"] as? String ?: "", traits = (m["traits"] as? List<*>)?.filterIsInstance<String>().orEmpty(), photoUrl = m["photoUrl"] as? String ?: "", species = m["species"] as? String ?: "Perro")
        }
    }

    suspend fun isFollowing(uid: String): Boolean {
        val me = requireUid()
        return db.collection("users").document(me).collection("following").document(uid).get().await().exists()
    }

    suspend fun follow(uid: String, follow: Boolean) {
        val me = requireVerifiedUid()
        ensureMyProfile()
        require(uid != me) { "No puedes seguirte a ti mismo." }
        val following = db.collection("users").document(me).collection("following").document(uid)
        val followers = db.collection("users").document(uid).collection("followers").document(me)
        val myProfile = db.collection("users").document(me)
        val theirProfile = db.collection("users").document(uid)
        val myBlocked = db.collection("users").document(me).collection("blocked").document(uid)
        val theirBlocked = db.collection("users").document(uid).collection("blocked").document(me)
        val newlyFollowed = db.runTransaction { tx ->
            val alreadyFollowing = tx.get(following).exists()
            val blockedByMe = tx.get(myBlocked).exists()
            val blockedByThem = tx.get(theirBlocked).exists()
            check(!follow || (!blockedByMe && !blockedByThem)) { "No puedes seguir a una cuenta bloqueada." }
            if (follow && !alreadyFollowing) {
                tx.set(following, mapOf("uid" to uid, "createdAt" to FieldValue.serverTimestamp()))
                tx.set(followers, mapOf("uid" to me, "createdAt" to FieldValue.serverTimestamp()))
                tx.update(myProfile, mapOf(
                    "followingCount" to FieldValue.increment(1),
                    "lastFollowingChangeUid" to uid
                ))
                tx.update(theirProfile, "followersCount", FieldValue.increment(1))
            } else if (!follow && alreadyFollowing) {
                tx.delete(following)
                tx.delete(followers)
                tx.update(myProfile, mapOf(
                    "followingCount" to FieldValue.increment(-1),
                    "lastFollowingChangeUid" to uid
                ))
                tx.update(theirProfile, "followersCount", FieldValue.increment(-1))
            }
            follow && !alreadyFollowing
        }.await()
        if (newlyFollowed) {
            createNotification(uid, "FOLLOW", "Ha empezado a seguirte.")
            runCatching { dispatchActivityPush(uid, "FOLLOW") }
        }
    }

    suspend fun block(uid: String) {
        val me = requireVerifiedUid()
        ensureMyProfile()
        require(uid != me) { "No puedes bloquear tu propia cuenta." }
        val myFollowing = db.collection("users").document(me).collection("following").document(uid)
        val theirFollowing = db.collection("users").document(uid).collection("following").document(me)
        val myFollower = db.collection("users").document(me).collection("followers").document(uid)
        val theirFollower = db.collection("users").document(uid).collection("followers").document(me)
        val myProfile = db.collection("users").document(me)
        val theirProfile = db.collection("users").document(uid)
        val blocked = db.collection("users").document(me).collection("blocked").document(uid)
        val blockedProfile = runCatching { getUser(uid) }.getOrNull()
        db.runTransaction { tx ->
            val iFollow = tx.get(myFollowing).exists()
            val theyFollow = tx.get(theirFollowing).exists()
            tx.set(blocked, mapOf("uid" to uid, "username" to (blockedProfile?.username ?: ""), "displayName" to (blockedProfile?.displayName ?: "Usuario"), "createdAt" to FieldValue.serverTimestamp()))
            tx.delete(myFollowing); tx.delete(theirFollowing); tx.delete(myFollower); tx.delete(theirFollower)
            if (iFollow) { tx.update(myProfile, "followingCount", FieldValue.increment(-1)); tx.update(theirProfile, "followersCount", FieldValue.increment(-1)) }
            if (theyFollow) { tx.update(theirProfile, "followingCount", FieldValue.increment(-1)); tx.update(myProfile, "followersCount", FieldValue.increment(-1)) }
        }.await()
    }

    suspend fun unblock(uid: String) {
        val me = requireUid()
        db.collection("users").document(me).collection("blocked").document(uid).delete().await()
    }

    fun listenBlockedUsers(onData: (List<PawlyBlockedUser>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        val uid = requireUid()
        return db.collection("users").document(uid).collection("blocked")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(error.localizedMessage ?: "No se pudo cargar la lista de bloqueos."); return@addSnapshotListener }
                onData(snapshot?.documents.orEmpty().map { doc ->
                    PawlyBlockedUser(doc.id, doc.getString("displayName") ?: "Usuario", doc.getString("username") ?: "")
                })
            }
    }

    /** Public, non-location-sensitive suggestions for the signed-in community member. */
    suspend fun getSuggestedUsers(limit: Long = 12): List<PawlyUser> {
        val me = requireUid()
        val blocked = db.collection("users").document(me).collection("blocked")
            .get().await().documents.map { it.id }.toSet()
        val followed = db.collection("users").document(me).collection("following")
            .get().await().documents.map { it.id }.toSet()
        return db.collection("users").whereEqualTo("isPublic", true)
            .limit((limit + followed.size + blocked.size + 1).coerceAtMost(60))
            .get().await().documents.mapNotNull { it.toPawlyUser() }
            .filter { it.uid != me && it.uid !in blocked && it.uid !in followed && it.isPublic }
            .take(limit.toInt())
    }

    /**
     * Syncs the signed-in user's saved Explore collections. The collection data is private
     * to its owner and is deliberately separate from public community recommendations.
     */
    suspend fun loadExploreCollections(): List<PawlyExploreCollection> {
        val uid = requireUid()
        return db.collection("users").document(uid).collection("exploreCollections")
            .get().await().documents.mapNotNull { doc ->
                val name = doc.getString("name")?.trim().orEmpty()
                if (name.isBlank()) null else PawlyExploreCollection(
                    name = name,
                    description = doc.getString("description").orEmpty(),
                    entries = (doc.get("entries") as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
                )
            }
    }

    /** Replaces the cloud snapshot atomically; bounded to 30 collections and 100 entries each. */
    suspend fun syncExploreCollections(collections: List<PawlyExploreCollection>) {
        val uid = requireUid()
        require(collections.size <= 30) { "Puedes guardar como máximo 30 colecciones." }
        collections.forEach { ExploreCollectionPolicy.validate(it.name, it.description, it.entries.size) }
        val root = db.collection("users").document(uid).collection("exploreCollections")
        val existing = root.get().await().documents
        val wantedIds = collections.map { ExploreCollectionPolicy.documentId(it.name) }.toSet()
        val batch = db.batch()
        existing.filter { it.id !in wantedIds }.forEach { batch.delete(it.reference) }
        collections.forEach { item ->
            val id = ExploreCollectionPolicy.documentId(item.name)
            batch.set(root.document(id), mapOf(
                "name" to item.name,
                "description" to item.description,
                "entries" to item.entries.toList().sorted(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }
        batch.commit().await()
    }

    suspend fun loadLostPetSubscription(): Triple<Boolean, Int, Pair<Double, Double>>? {
        val uid = requireUid()
        val doc = db.collection("lostPetSubscriptions").document(uid).get().await()
        if (!doc.exists()) return null
        val latitude = doc.getDouble("latitude") ?: return null
        val longitude = doc.getDouble("longitude") ?: return null
        return Triple(doc.getBoolean("enabled") ?: false, (doc.getLong("radiusKm") ?: 10L).toInt(), latitude to longitude)
    }

    /** Saves a coarse location (rounded to ~1 km) and explicit radius consent for lost-pet alerts. */
    suspend fun saveLostPetSubscription(enabled: Boolean, radiusKm: Int, latitude: Double, longitude: Double) {
        val uid = requireVerifiedUid()
        require(radiusKm in setOf(5, 10, 20)) { "Selecciona un radio de 5, 10 o 20 km." }
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        db.collection("lostPetSubscriptions").document(uid).set(mapOf(
            "uid" to uid, "enabled" to enabled, "radiusKm" to radiusKm,
            "latitude" to kotlin.math.round(latitude * 100.0) / 100.0,
            "longitude" to kotlin.math.round(longitude * 100.0) / 100.0,
            "updatedAt" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun publishLostPetAlert(text: String, imageUrl: String, dogName: String, latitude: Double, longitude: Double): String {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        val postId = publishPost(text, imageUrl, "", dogName, "LOST")
        db.collection("lostPetAlerts").document(postId).set(mapOf(
            "postId" to postId, "authorUid" to requireUid(), "dogName" to dogName.take(40),
            "latitude" to kotlin.math.round(latitude * 100.0) / 100.0,
            "longitude" to kotlin.math.round(longitude * 100.0) / 100.0,
            "status" to "active", "createdAt" to FieldValue.serverTimestamp()
        )).await()
        runCatching { postBackend("/v1/lost-pet-alerts", JSONObject().put("alertId", postId)) }
        return postId
    }

    suspend fun loadCommunityPlaces(): List<PawlyCommunityPlace> {
        requireUid()
        return db.collection("communityPlaces").whereEqualTo("status", "approved").limit(100).get().await()
            .documents.map { doc ->
                PawlyCommunityPlace(doc.id, doc.getString("name").orEmpty(), doc.getString("category").orEmpty(),
                    doc.getString("location").orEmpty(), doc.getString("description").orEmpty(),
                    doc.getString("authorUid").orEmpty(), doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L)
            }.sortedByDescending { it.createdAt }
    }

    suspend fun submitCommunityPlace(name: String, category: String, location: String, description: String) {
        val uid = requireVerifiedUid()
        val cleanName = name.trim()
        val cleanLocation = location.trim()
        val cleanDescription = description.trim()
        require(cleanName.length in 3..80 && cleanLocation.length in 3..120 && cleanDescription.length in 10..500)
        require(category in setOf("Parque", "Playa", "Restaurante", "Alojamiento", "Ruta", "Veterinario", "Otro"))
        db.collection("communityPlaces").add(mapOf(
            "name" to cleanName, "category" to category, "location" to cleanLocation,
            "description" to cleanDescription, "authorUid" to uid, "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun searchProfessionalProfiles(category: String = "", area: String = ""): List<PawlyUser> {
        val uid = requireUid()
        val profiles = db.collection("users").whereEqualTo("isPublic", true).limit(100).get().await()
            .documents.mapNotNull { it.toPawlyUser() }
            .filter { it.accountType == "professional" }
            .filter { category.isBlank() || it.professionalCategory.contains(category, true) }
            .filter { area.isBlank() || it.professionalLocation.contains(area, true) }
        val blocked = db.collection("users").document(uid).collection("blocked").get().await().documents.map { it.id }.toSet()
        return profiles.filter { it.uid != uid && it.uid !in blocked }
    }

    /** Raw contact numbers are sent only over TLS to the trusted Worker and never stored. */
    suspend fun loadPhoneDiscoveryEnabled(): Boolean {
        val baseUrl = BuildConfig.PAWLY_BACKEND_URL.trim().trimEnd('/')
        check(baseUrl.isNotBlank()) { "El servicio seguro de Pawly todavía no está configurado." }
        val token = auth.currentUser?.getIdToken(false)?.await()?.token ?: error("Inicia sesión de nuevo.")
        val request = Request.Builder().url("$baseUrl/v1/contact-discovery").header("Authorization", "Bearer $token").get().build()
        return withContext(Dispatchers.IO) {
            backendHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("No se pudo consultar la preferencia de contactos.")
                JSONObject(body).optBoolean("enabled", false)
            }
        }
    }

    suspend fun setPhoneDiscovery(phone: String, enabled: Boolean) {
        if (enabled) require(phone.trim().length in 7..24) { "Introduce un teléfono válido." }
        postBackend("/v1/contact-discovery", JSONObject().put("enabled", enabled).put("phone", phone.trim()))
    }

    suspend fun findContactMatches(phoneNumbers: List<String>): List<PawlyContactMatch> {
        require(phoneNumbers.size in 1..30) { "Selecciona entre 1 y 30 contactos." }
        val response = postBackend("/v1/contact-matches", JSONObject().put("phoneNumbers", org.json.JSONArray(phoneNumbers)))
        val items = response.optJSONArray("matches") ?: org.json.JSONArray()
        return List(items.length()) { i ->
            val item = items.getJSONObject(i)
            PawlyContactMatch(item.optString("uid"), item.optString("displayName"), item.optString("username"), item.optString("photoUrl"))
        }.filter { it.uid.isNotBlank() }
    }

    private suspend fun postBackend(path: String, payload: JSONObject): JSONObject {
        val baseUrl = BuildConfig.PAWLY_BACKEND_URL.trim().trimEnd('/')
        check(baseUrl.isNotBlank()) { "El servicio seguro de Pawly todavía no está configurado." }
        val token = auth.currentUser?.getIdToken(false)?.await()?.token ?: error("Inicia sesión de nuevo.")
        val request = Request.Builder().url(baseUrl + path).header("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        return withContext(Dispatchers.IO) {
            backendHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(
                    runCatching { JSONObject(body).optString("error") }.getOrNull().takeUnless { it.isNullOrBlank() }
                        ?: "El servicio de Pawly respondió ${response.code}."
                )
                if (body.isBlank()) JSONObject() else JSONObject(body)
            }
        }
    }

    /** Loads public community events; coordinates belong to the event venue, never to a person. */
    suspend fun loadEvents(): List<PawlyEvent> {
        val me = requireUid()
        val snapshot = db.collection("events").orderBy("createdAt", Query.Direction.DESCENDING).limit(60).get().await()
        return snapshot.documents.map { doc ->
            val interested = doc.reference.collection("interested").document(me).get().await().exists()
            PawlyEvent(
                id = doc.id,
                title = doc.getString("title") ?: "",
                description = doc.getString("description") ?: "",
                category = doc.getString("category") ?: "Quedada",
                location = doc.getString("location") ?: "",
                dateText = doc.getString("dateText") ?: "",
                startAt = doc.getTimestamp("startAt")?.toDate()?.time ?: 0L,
                timeZoneId = doc.getString("timeZoneId") ?: "",
                latitude = doc.getDouble("latitude"),
                longitude = doc.getDouble("longitude"),
                organizerName = doc.getString("organizerName") ?: "Comunidad Pawly",
                organizerUid = doc.getString("organizerUid") ?: "",
                interestedCount = doc.getLong("interestedCount") ?: 0,
                interestedByMe = interested
            )
        }.sortedWith(compareBy { if (it.startAt > 0L) it.startAt else Long.MAX_VALUE })
    }

    suspend fun createEvent(
        title: String, description: String, category: String, location: String,
        dateText: String, latitude: Double?, longitude: Double?
    ) {
        val uid = requireVerifiedUid()
        val profile = getMyProfile() ?: error("Completa primero tu perfil.")
        val cleanTitle = title.trim()
        val cleanDescription = description.trim()
        val cleanLocation = location.trim()
        val cleanDate = dateText.trim()
        val startAtMillis = EventDateParser.parseMillis(cleanDate)
        require(startAtMillis > System.currentTimeMillis()) { "La fecha del evento debe estar en el futuro." }
        require(cleanTitle.length in 4..90) { "El título debe tener entre 4 y 90 caracteres." }
        require(cleanDescription.length in 10..1200) { "La descripción debe tener entre 10 y 1200 caracteres." }
        require(cleanLocation.length in 3..160) { "Indica un lugar para el evento." }
        require(cleanDate.length in 4..80) { "Indica la fecha y hora del evento." }
        require(category in setOf("Quedada", "Ruta", "Taller", "Adopción")) { "Selecciona una categoría válida." }
        require((latitude == null && longitude == null) ||
            (latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0)) {
            "Selecciona un punto válido en el mapa o elimina sus coordenadas."
        }
        val eventData = mutableMapOf<String, Any>(
            "title" to cleanTitle, "description" to cleanDescription,
            "category" to category, "location" to cleanLocation, "dateText" to cleanDate,
            "startAt" to Date(startAtMillis), "timeZoneId" to ZoneId.systemDefault().id,
            "organizerUid" to uid, "organizerName" to profile.displayName.ifBlank { profile.username },
            "interestedCount" to 0, "createdAt" to FieldValue.serverTimestamp()
        )
        if (latitude != null && longitude != null) {
            eventData["latitude"] = latitude
            eventData["longitude"] = longitude
        }
        db.collection("events").add(eventData).await()
    }

    suspend fun toggleEventInterest(eventId: String) {
        val uid = requireVerifiedUid()
        val event = db.collection("events").document(eventId)
        val interest = event.collection("interested").document(uid)
        db.runTransaction { tx ->
            val eventSnapshot = tx.get(event)
            check(eventSnapshot.exists()) { "El evento ya no está disponible." }
            val interestSnapshot = tx.get(interest)
            val current = eventSnapshot.getLong("interestedCount") ?: 0
            if (interestSnapshot.exists()) {
                tx.delete(interest)
                tx.update(event, "interestedCount", (current - 1).coerceAtLeast(0))
            } else {
                tx.set(interest, mapOf("uid" to uid, "createdAt" to FieldValue.serverTimestamp()))
                tx.update(event, "interestedCount", current + 1)
            }
        }.await()
    }

    suspend fun isAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return user.getIdToken(true).await().claims["admin"] == true
    }

    fun listenReports(onData: (List<PawlyReport>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        return db.collection("reports").orderBy("createdAt", Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(error.localizedMessage ?: "No se pudo cargar la moderación."); return@addSnapshotListener }
                onData(snapshot?.documents.orEmpty().map { doc ->
                    PawlyReport(doc.id, doc.getString("reporterId") ?: "", doc.getString("targetId") ?: "", doc.getString("targetType") ?: "", doc.getString("reason") ?: "", doc.getString("status") ?: "open")
                })
            }
    }

    suspend fun hideReportedUser(uid: String) {
        check(isAdmin()) { "No tienes permisos de moderación." }
        db.collection("users").document(uid).update(mapOf("isPublic" to false, "moderationStatus" to "restricted")).await()
    }

    suspend fun markReportReviewed(reportId: String) {
        check(isAdmin()) { "No tienes permisos de moderación." }
        db.collection("reports").document(reportId).update(mapOf("status" to "reviewed", "reviewedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun removeReportedPost(postId: String) {
        check(isAdmin()) { "No tienes permisos de moderación." }
        db.collection("posts").document(postId).delete().await()
    }

    suspend fun removeReportedEvent(eventId: String) {
        check(isAdmin()) { "No tienes permisos de moderación." }
        db.collection("events").document(eventId).delete().await()
    }

    suspend fun report(targetId: String, targetType: String, reason: String) {
        val uid = requireVerifiedUid()
        require(targetId.isNotBlank()) { "No se ha seleccionado el elemento que quieres denunciar." }
        require(targetType in setOf("post", "user", "event")) { "El tipo de denuncia no es válido." }
        val cleanReason = reason.trim()
        require(cleanReason.isNotBlank()) { "Selecciona un motivo para la denuncia." }
        db.collection("reports").add(mapOf(
            "reporterId" to uid, "targetId" to targetId, "targetType" to targetType,
            "reason" to cleanReason.take(500), "status" to "open", "createdAt" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun startConversation(otherUid: String): String {
        val me = requireVerifiedUid()
        require(otherUid != me) { "No puedes enviarte mensajes a ti mismo." }
        val ids = listOf(me, otherUid).sorted()
        val id = ids.joinToString("_")
        val myProfile = getMyProfile()
        val other = getUser(otherUid)
        val myBlocked = db.collection("users").document(me).collection("blocked").document(otherUid).get().await().exists()
        val blockedMe = db.collection("users").document(otherUid).collection("blocked").document(me).get().await().exists()
        check(!myBlocked && !blockedMe) { "No puedes iniciar una conversación con esta cuenta." }
        val ref = db.collection("conversations").document(id)
        // Existing conversations may only update lastMessage/updatedAt under Firestore rules.
        // Re-opening a chat must not rewrite participants or participantNames.
        db.runTransaction { transaction ->
            val existing = transaction.get(ref)
            if (existing.exists()) {
                check((existing.get("participants") as? List<*>)?.toSet() == ids.toSet()) {
                    "La conversación existente no coincide con sus participantes."
                }
            } else {
                transaction.set(
                    ref,
                    mapOf(
                        "participants" to ids,
                        "participantNames" to mapOf(
                            me to (myProfile?.displayName ?: "Yo"),
                            otherUid to (other?.displayName ?: "Usuario")
                        ),
                        "participantPhotos" to mapOf(
                            me to myProfile?.photoUrl.orEmpty(),
                            otherUid to other?.photoUrl.orEmpty()
                        ),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }.await()
        return id
    }

    fun listenConversations(onData: (List<PawlyConversation>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        val uid = requireUid()
        var latest = emptyList<PawlyConversation>()
        var blockedIds = emptySet<String>()
        fun emit() { onData(latest.filterNot { it.otherUserId in blockedIds }) }
        val conversationsListener = db.collection("conversations").whereArrayContains("participants", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(error.localizedMessage ?: "No se pudieron cargar los chats."); return@addSnapshotListener }
                latest = snapshot?.documents.orEmpty().map { doc ->
                    val participants = doc.get("participants") as? List<String> ?: emptyList()
                    val other = participants.firstOrNull { it != uid } ?: ""
                    val names = doc.get("participantNames") as? Map<*, *> ?: emptyMap<Any, Any>()
                    val photos = doc.get("participantPhotos") as? Map<*, *> ?: emptyMap<Any, Any>()
                    PawlyConversation(
                        id = doc.id,
                        participants = participants,
                        otherUserId = other,
                        otherUserName = names[other] as? String ?: "Usuario",
                        lastMessage = doc.getString("lastMessage") ?: "",
                        updatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0,
                        otherUserPhoto = photos[other] as? String ?: ""
                    )
                }.sortedByDescending { it.updatedAt }
                emit()
            }
        val blockedListener = db.collection("users").document(uid).collection("blocked")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(error.localizedMessage ?: "No se pudo cargar la lista de bloqueos."); return@addSnapshotListener }
                blockedIds = snapshot?.documents.orEmpty().map { it.id }.toSet()
                emit()
            }
        return object : ListenerRegistration {
            override fun remove() { conversationsListener.remove(); blockedListener.remove() }
        }
    }

    suspend fun getUnreadMessageCount(conversationId: String): Int {
        val uid = requireUid()
        val conversation = db.collection("conversations").document(conversationId).get().await()
        val participants = conversation.get("participants") as? List<String> ?: emptyList()
        if (uid !in participants || participants.size != 2) return 0
        val otherUid = participants.first { it != uid }
        val recentIncoming = db.collection("conversations").document(conversationId)
            .collection("messages")
            .whereEqualTo("senderId", otherUid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .get().await()
        return recentIncoming.documents.count { uid !in (it.get("readBy") as? List<*>)?.filterIsInstance<String>().orEmpty() }
    }

    fun listenMessages(conversationId: String, onData: (List<PawlyMessage>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        return db.collection("conversations").document(conversationId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING).limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(error.localizedMessage ?: "No se pudieron cargar los mensajes."); return@addSnapshotListener }
                onData(snapshot?.documents.orEmpty().map { d ->
                    PawlyMessage(
                        id = d.id,
                        senderId = d.getString("senderId") ?: "",
                        text = d.getString("text") ?: "",
                        createdAt = d.getTimestamp("createdAt")?.toDate()?.time ?: 0,
                        imageUrl = d.getString("imageUrl") ?: "",
                        videoUrl = d.getString("videoUrl") ?: "",
                        readBy = (d.get("readBy") as? List<*>)?.filterIsInstance<String>().orEmpty()
                    )
                })
            }
    }

    suspend fun sendMessage(conversationId: String, text: String, imageUrl: String = "", videoUrl: String = "") {
        val uid = requireVerifiedUid()
        val payload = ChatMessagePolicy.prepare(text, imageUrl, videoUrl)
        val ref = db.collection("conversations").document(conversationId)
        val messageRef = ref.collection("messages").document()
        val preview = payload.text.ifBlank {
            if (payload.videoUrl.isNotBlank()) "🎥 Vídeo" else "📷 Fotografía"
        }.take(120)

        // Rules validate conversation membership, verification and both block lists.
        // A single atomic batch avoids several sequential network reads before sending.
        val batch = db.batch()
        batch.set(messageRef, mapOf(
            "senderId" to uid, "text" to payload.text, "imageUrl" to payload.imageUrl,
            "videoUrl" to payload.videoUrl, "readBy" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp()
        ))
        batch.update(ref, mapOf("lastMessage" to preview, "updatedAt" to FieldValue.serverTimestamp()))
        batch.commit().await()
    }

        suspend fun markConversationRead(conversationId: String) {
        val uid = requireUid()
        val conversation = db.collection("conversations").document(conversationId).get().await()
        val participants = conversation.get("participants") as? List<String> ?: emptyList()
        check(uid in participants && participants.size == 2) { "No formas parte de esta conversación." }
        val otherUid = participants.first { it != uid }
        val messages = db.collection("conversations").document(conversationId)
            .collection("messages").whereEqualTo("senderId", otherUid)
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(200).get().await()
        val unread = messages.documents.filter { uid !in (it.get("readBy") as? List<*>)?.filterIsInstance<String>().orEmpty() }
        unread.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { doc -> batch.update(doc.reference, "readBy", FieldValue.arrayUnion(uid)) }
            batch.commit().await()
        }
    }

    private fun requireUid(): String = currentUid ?: error("Inicia sesión para utilizar la comunidad.")

    /**
     * Repairs accounts that authenticated successfully but have no Firestore profile yet.
     * This is needed by accounts created before the community profile was initialized.
     */
    private suspend fun ensureMyProfile(): PawlyUser {
        val user = auth.currentUser ?: error("Inicia sesión para utilizar la comunidad.")
        val uid = user.uid
        val userRef = db.collection("users").document(uid)
        val existingSnapshot = userRef.get().await()
        var existingProfile = existingSnapshot.toPawlyUser()
        if (existingProfile != null) {
            // Move legacy contact out of the public user document. Old visibility was implicit,
            // so it is deliberately disabled until the owner opts in again.
            val legacyContact = existingSnapshot.getString("professionalContact").orEmpty()
            if (legacyContact.isNotBlank()) {
                val migration = db.batch()
                migration.set(userRef.collection("privateData").document("professionalContact"), mapOf("value" to legacyContact.take(120)))
                migration.update(userRef, mapOf(
                    "professionalContact" to com.google.firebase.firestore.FieldValue.delete(),
                    "professionalContactPublic" to false
                ))
                migration.commit().await()
            }
            existingProfile = existingProfile.withPrivateProfessionalContact()
            cachedMyProfileState = existingProfile
            // Repair the username claim for accounts created before reservation claims
            // were introduced. Failure here should not lock an existing account out.
            runCatching {
                val normalized = existingProfile.usernameLower.ifBlank { existingProfile.username.lowercase() }
                check(normalized.matches(Regex("^[a-z0-9_.]{3,24}$"))) { "El nombre de usuario guardado no es válido." }
                val usernameRef = db.collection("usernames").document(normalized)
                val reservationRef = db.collection("usernameReservations").document(uid)
                db.runTransaction { transaction ->
                    val usernameDoc = transaction.get(usernameRef)
                    val reservation = transaction.get(reservationRef)
                    check(usernameDoc.exists() && usernameDoc.getString("uid") == uid) {
                        "No se puede validar el nombre de usuario guardado."
                    }
                    if (reservation.exists()) {
                        check(reservation.getString("username") == normalized) { "La reserva de usuario no coincide." }
                    } else {
                        transaction.set(reservationRef, mapOf("username" to normalized))
                    }
                }.await()
            }
            return existingProfile
        }

        val cleanUid = uid.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }.ifBlank { "account" }
        val usernameCandidates = (
            listOf("pawly_" + cleanUid.takeLast(8), "pawly_" + cleanUid.take(16)) +
                (1..12).map { "pawly_" + cleanUid.take(10) + "_" + it }
            )
            .filter { it.length in 3..24 && Regex("^[a-z0-9_.]{3,24}$").matches(it) }
            .distinct()
        check(usernameCandidates.isNotEmpty()) { "No se pudo preparar un nombre de usuario." }

        val reservationRef = db.collection("usernameReservations").document(uid)
        val username = db.runTransaction { transaction ->
            val existingReservation = transaction.get(reservationRef)
            if (existingReservation.exists()) {
                val reserved = existingReservation.getString("username")
                    ?: error("La reserva de usuario está incompleta.")
                val usernameDoc = transaction.get(db.collection("usernames").document(reserved))
                check(usernameDoc.exists() && usernameDoc.getString("uid") == uid) {
                    "La reserva de usuario no coincide con la cuenta."
                }
                return@runTransaction reserved
            }
            val candidates = usernameCandidates.map { candidate ->
                candidate to transaction.get(db.collection("usernames").document(candidate))
            }
            val selected = candidates.firstOrNull { it.second.exists() && it.second.getString("uid") == uid }
                ?: candidates.firstOrNull { !it.second.exists() }
                ?: error("No se ha encontrado un nombre de usuario libre.")
            val normalized = selected.first
            if (!selected.second.exists()) {
                transaction.set(db.collection("usernames").document(normalized), mapOf("uid" to uid))
            }
            transaction.set(reservationRef, mapOf("username" to normalized))
            normalized
        }.await()

        // New accounts start private and do not import a Google name, email prefix or
        // Google photo into the public-facing profile.
        val profileData = mapOf(
            "uid" to uid,
            "username" to username,
            "usernameLower" to username,
            "displayName" to username,
            "bio" to "",
            "photoUrl" to "",
            "isPublic" to false,
            "followersCount" to 0,
            "followingCount" to 0,
            "accountType" to "personal",
            "professionalCategory" to "",
            "professionalLocation" to "",
            "professionalDescription" to "",
            "professionalContactPublic" to false,
            "dogProfiles" to emptyList<Map<String, Any>>(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            userRef.set(profileData).await()
        } catch (writeError: Exception) {
            val concurrentProfile = runCatching { userRef.get().await().toPawlyUser() }.getOrNull()
            if (concurrentProfile != null) {
                cachedMyProfileState = concurrentProfile
                return concurrentProfile
            }
            throw writeError
        }
        val profile = PawlyUser(
            uid = uid, username = username, usernameLower = username,
            displayName = username, isPublic = false
        )
        cachedMyProfileState = profile
        cachedMyDogsState = emptyList()
        return profile
    }

        private suspend fun requireVerifiedUid(): String {
        val user = auth.currentUser ?: error("Inicia sesión para utilizar la comunidad.")
        // Avoid round trips for already verified accounts. Firestore rules remain the
        // final authority if the cached token is stale.
        if (user.isEmailVerified) return user.uid
        user.reload().await()
        val token = user.getIdToken(true).await()
        val verifiedClaim = token.claims["email_verified"] as? Boolean == true
        check(user.isEmailVerified && verifiedClaim) {
            "Verifica tu correo electrónico y vuelve a iniciar sesión para seguir personas y enviar mensajes."
        }
        return user.uid
    }

    private suspend fun PawlyUser.withPrivateProfessionalContact(): PawlyUser {
        val contact = db.collection("users").document(uid)
            .collection("privateData").document("professionalContact").get().await()
            .getString("value").orEmpty()
        return copy(professionalContact = contact)
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toPawlyUser(): PawlyUser? {
        if (!exists()) return null
        return PawlyUser(
            uid = getString("uid") ?: id,
            username = getString("username") ?: "",
            usernameLower = getString("usernameLower") ?: "",
            displayName = getString("displayName") ?: getString("username") ?: "",
            bio = getString("bio") ?: "",
            photoUrl = getString("photoUrl") ?: "",
            isPublic = getBoolean("isPublic") ?: false,
            followersCount = getLong("followersCount") ?: 0,
            followingCount = getLong("followingCount") ?: 0,
            accountType = getString("accountType")?.takeIf { it in setOf("personal", "professional") } ?: "personal",
            professionalCategory = getString("professionalCategory") ?: "",
            professionalLocation = getString("professionalLocation") ?: "",
            professionalDescription = getString("professionalDescription") ?: "",
            professionalContact = "",
            professionalContactPublic = getBoolean("professionalContactPublic") ?: false
        ).copy(professionalContact = getString("professionalPublicContact").orEmpty())
    }
}
