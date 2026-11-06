package com.pawly.app

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

/**
 * Firebase-backed community data source. It is only created when google-services.json
 * has been supplied; the rest of Pawly remains usable without a Firebase project.
 */
class FirebaseCommunityRepository private constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val appContext: Context
) {
    val currentUid: String? get() = auth.currentUser?.uid
    val currentEmail: String? get() = auth.currentUser?.email
    val isEmailVerified: Boolean get() = auth.currentUser?.isEmailVerified == true

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
        val uid = result.user?.uid ?: error("No se pudo crear la cuenta.")
        val usernameRef = db.collection("usernames").document(normalized)
        try {
            db.runTransaction { transaction ->
                check(!transaction.get(usernameRef).exists()) { "Ese nombre de usuario ya está ocupado." }
                transaction.set(usernameRef, mapOf("uid" to uid))
            }.await()
            db.collection("users").document(uid).set(
                mapOf(
                    "uid" to uid,
                    "username" to cleanName,
                    "usernameLower" to normalized,
                    "displayName" to cleanName,
                    "bio" to "",
                    "photoUrl" to "",
                    "isPublic" to true,
                    "followersCount" to 0,
                    "followingCount" to 0,
                    "dogProfiles" to emptyList<Map<String, Any>>(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            result.user?.sendEmailVerification()?.await()
        } catch (error: Exception) {
            runCatching { usernameRef.delete().await() }
            runCatching { result.user?.delete()?.await() }
            throw error
        }
    }

    suspend fun signIn(email: String, password: String) {
        val cleanEmail = CommunityInputRules.validateEmail(email)
        require(password.isNotEmpty()) { "Escribe tu contraseña." }
        auth.signInWithEmailAndPassword(cleanEmail, password).await()
    }

    /**
     * Signs in with a Google ID token obtained through Android Credential Manager.
     * A first-time Google user receives a Pawly profile and a generated username.
     */
    suspend fun signInWithGoogle(idToken: String) {
        require(idToken.isNotBlank()) { "Google no ha devuelto un token de acceso válido." }
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        val user = result.user ?: error("Firebase no ha devuelto el usuario de Google.")
        val userRef = db.collection("users").document(user.uid)

        if (!userRef.get().await().exists()) {
            val username = "pawly_${user.uid.takeLast(8)}"
            val usernameRef = db.collection("usernames").document(username.lowercase())
            usernameRef.set(mapOf("uid" to user.uid)).await()
            try {
                userRef.set(
                    mapOf(
                        "uid" to user.uid,
                        "username" to username,
                        "usernameLower" to username.lowercase(),
                        "displayName" to (user.displayName?.take(50)?.takeIf { it.isNotBlank() } ?: username),
                        "bio" to "",
                        "photoUrl" to (user.photoUrl?.toString() ?: ""),
                        "isPublic" to true,
                        "followersCount" to 0,
                        "followingCount" to 0,
                        "dogProfiles" to emptyList<Map<String, Any>>(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (error: Exception) {
                runCatching { usernameRef.delete().await() }
                throw error
            }
        }
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
        val pendingRequests = db.collection("accountDeletionRequests")
            .whereEqualTo("uid", uid)
            .get().await().documents
        if (pendingRequests.any { it.getString("status") == "pending" }) return
        db.collection("accountDeletionRequests").add(
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
    }

    suspend fun updateProfile(displayName: String, bio: String, isPublic: Boolean) {
        require(displayName.trim().isNotBlank()) { "El nombre público no puede estar vacío." }
        val uid = requireUid()
        db.collection("users").document(uid).update(
            mapOf("displayName" to displayName.trim().take(50), "bio" to bio.trim().take(240), "isPublic" to isPublic)
        ).await()
    }

    suspend fun updateProfilePhoto(uri: Uri): String {
        val uid = requireUid()
        val url = uploadImage(uri, "profile-photos")
        db.collection("users").document(uid).update("photoUrl", url).await()
        return url
    }

    suspend fun uploadImage(uri: Uri, folder: String): String {
        val uid = requireVerifiedUid()
        return CloudinaryImageUploader(appContext).upload(uri, folder, uid)
    }

    suspend fun saveDog(dog: PawlyDog) {
        val uid = requireUid()
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
    }

    suspend fun deleteDog(dogId: String) {
        require(dogId.isNotBlank()) { "No se ha indicado qué perro eliminar." }
        val ref = db.collection("users").document(requireUid())
        db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            val current = (snapshot.get("dogProfiles") as? List<Map<String, Any>>).orEmpty()
            check(current.any { it["id"] == dogId }) { "No se ha encontrado ese perfil de perro." }
            transaction.update(ref, "dogProfiles", current.filterNot { it["id"] == dogId })
        }.await()
    }

    suspend fun getMyProfile(): PawlyUser? {
        val uid = requireUid()
        val doc = db.collection("users").document(uid).get().await()
        return doc.toPawlyUser()
    }

    suspend fun getMyDogs(): List<PawlyDog> {
        val uid = requireUid()
        val data = db.collection("users").document(uid).get().await().get("dogProfiles") as? List<Map<String, Any>>
        return data.orEmpty().map { map ->
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
                        likeCount = doc.getLong("likeCount") ?: 0,
                        commentCount = doc.getLong("commentCount") ?: 0,
                        likedByMe = currentUid in likedBy,
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0,
                        category = doc.getString("category") ?: "STORY"
                    )
                })
            }
    }

    suspend fun publishPost(text: String, imageUrl: String, dogName: String, category: String = "STORY") {
        val uid = requireVerifiedUid()
        val user = getMyProfile() ?: error("Completa primero tu perfil.")
        CommunityInputRules.validatePost(text, imageUrl)
        require(category in setOf("STORY", "ADOPTION", "LOST", "FOUND", "FOSTER")) { "Selecciona una categoría válida." }
        db.collection("posts").add(
            mapOf(
                "authorId" to uid,
                "authorName" to user.displayName.ifBlank { user.username },
                "authorPhoto" to user.photoUrl,
                "dogName" to dogName.take(40),
                "category" to category,
                "text" to text.trim().take(2000),
                "imageUrl" to imageUrl,
                "likeCount" to 0,
                "commentCount" to 0,
                "likedBy" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun toggleLike(postId: String) {
        val uid = requireVerifiedUid()
        val ref = db.collection("posts").document(postId)
        db.runTransaction { tx ->
            val doc = tx.get(ref)
            val liked = (doc.get("likedBy") as? List<*>)?.contains(uid) == true
            tx.update(ref, "likedBy", if (liked) FieldValue.arrayRemove(uid) else FieldValue.arrayUnion(uid))
            tx.update(ref, "likeCount", FieldValue.increment(if (liked) -1 else 1))
        }.await()
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
    }

    suspend fun searchUsers(query: String): List<PawlyUser> {
        val clean = query.trim().lowercase()
        if (clean.length < 2) return emptyList()
        val docs = db.collection("users").whereEqualTo("isPublic", true).orderBy("usernameLower")
            .startAt(clean).endAt(clean + "\uf8ff").limit(25).get().await()
        val blocked = currentUid?.let { db.collection("users").document(it).collection("blocked").get().await().documents.map { doc -> doc.id }.toSet() }.orEmpty()
        return docs.documents.mapNotNull { it.toPawlyUser() }.filter { it.uid != currentUid && it.isPublic && it.uid !in blocked }
    }

    suspend fun getUser(uid: String): PawlyUser? = db.collection("users").document(uid).get().await().toPawlyUser()

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
        require(uid != me) { "No puedes seguirte a ti mismo." }
        val following = db.collection("users").document(me).collection("following").document(uid)
        val followers = db.collection("users").document(uid).collection("followers").document(me)
        val myProfile = db.collection("users").document(me)
        val theirProfile = db.collection("users").document(uid)
        val myBlocked = db.collection("users").document(me).collection("blocked").document(uid)
        val theirBlocked = db.collection("users").document(uid).collection("blocked").document(me)
        db.runTransaction { tx ->
            val alreadyFollowing = tx.get(following).exists()
            val blockedByMe = tx.get(myBlocked).exists()
            val blockedByThem = tx.get(theirBlocked).exists()
            check(!follow || (!blockedByMe && !blockedByThem)) { "No puedes seguir a una cuenta bloqueada." }
            if (follow && !alreadyFollowing) {
                tx.set(following, mapOf("uid" to uid, "createdAt" to FieldValue.serverTimestamp()))
                tx.set(followers, mapOf("uid" to me, "createdAt" to FieldValue.serverTimestamp()))
                tx.update(myProfile, "followingCount", FieldValue.increment(1))
                tx.update(theirProfile, "followersCount", FieldValue.increment(1))
            } else if (!follow && alreadyFollowing) {
                tx.delete(following)
                tx.delete(followers)
                tx.update(myProfile, "followingCount", FieldValue.increment(-1))
                tx.update(theirProfile, "followersCount", FieldValue.increment(-1))
            }
        }.await()
    }

    suspend fun block(uid: String) {
        val me = requireVerifiedUid()
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

    suspend fun report(targetId: String, targetType: String, reason: String) {
        val uid = requireVerifiedUid()
        require(targetId.isNotBlank()) { "No se ha seleccionado el elemento que quieres denunciar." }
        require(targetType in setOf("post", "user")) { "El tipo de denuncia no es válido." }
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
        ref.set(
            mapOf(
                "participants" to ids,
                "participantNames" to mapOf(me to (myProfile?.displayName ?: "Yo"), otherUid to (other?.displayName ?: "Usuario")),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
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
                    PawlyConversation(doc.id, participants, other, names[other] as? String ?: "Usuario", doc.getString("lastMessage") ?: "", doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0)
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
                        readBy = (d.get("readBy") as? List<*>)?.filterIsInstance<String>().orEmpty()
                    )
                })
            }
    }

    suspend fun sendMessage(conversationId: String, text: String, imageUrl: String = "") {
        val uid = requireVerifiedUid()
        val clean = text.trim()
        require(clean.length <= 2000) { "El mensaje no puede superar los 2000 caracteres." }
        require(clean.isNotBlank() || imageUrl.isNotBlank()) { "Escribe un mensaje o adjunta una fotografía." }
        require(imageUrl.length <= 2048) { "La dirección de la imagen no es válida." }
        val ref = db.collection("conversations").document(conversationId)
        val conversationSnapshot = ref.get().await()
        val participants = conversationSnapshot.get("participants") as? List<String> ?: emptyList()
        check(uid in participants && participants.size == 2) { "No formas parte de esta conversación." }
        val otherUid = participants.first { it != uid }
        val blockedByMe = db.collection("users").document(uid).collection("blocked").document(otherUid).get().await().exists()
        val blockedMe = db.collection("users").document(otherUid).collection("blocked").document(uid).get().await().exists()
        check(!blockedByMe && !blockedMe) { "No puedes enviar mensajes a esta cuenta." }
        val messageRef = ref.collection("messages").document()
        val preview = clean.ifBlank { "📷 Fotografía" }.take(120)
        db.runTransaction { tx ->
            val conversation = tx.get(ref)
            check(uid in (conversation.get("participants") as? List<String>).orEmpty()) { "No formas parte de esta conversación." }
            tx.set(messageRef, mapOf(
                "senderId" to uid, "text" to clean.take(2000), "imageUrl" to imageUrl,
                "readBy" to listOf(uid), "createdAt" to FieldValue.serverTimestamp()
            ))
            tx.update(ref, mapOf("lastMessage" to preview, "updatedAt" to FieldValue.serverTimestamp()))
        }.await()
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

    private fun requireVerifiedUid(): String {
        val user = auth.currentUser ?: error("Inicia sesión para utilizar la comunidad.")
        check(user.isEmailVerified) { "Verifica tu correo electrónico para publicar, seguir, comentar y enviar mensajes." }
        return user.uid
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
            isPublic = getBoolean("isPublic") ?: true,
            followersCount = getLong("followersCount") ?: 0,
            followingCount = getLong("followingCount") ?: 0
        )
    }
}
