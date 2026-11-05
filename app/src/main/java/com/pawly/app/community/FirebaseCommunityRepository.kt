package com.pawly.app

import android.content.Context
import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

/**
 * Firebase-backed community data source. It is only created when google-services.json
 * has been supplied; the rest of Pawly remains usable without a Firebase project.
 */
class FirebaseCommunityRepository private constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
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
                FirebaseStorage.getInstance(app)
            )
        }.getOrNull()
    }

    suspend fun register(email: String, password: String, username: String) {
        val cleanName = username.trim()
        require(cleanName.length in 3..24) { "El nombre de usuario debe tener entre 3 y 24 caracteres." }
        require(cleanName.matches(Regex("[A-Za-z0-9_.]+"))) { "Usa solo letras, números, puntos y guiones bajos." }
        val normalized = cleanName.lowercase()
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
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
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun sendPasswordReset(email: String) {
        require(email.contains("@")) { "Escribe primero tu correo electrónico." }
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun resendVerificationEmail() {
        auth.currentUser?.sendEmailVerification()?.await() ?: error("Inicia sesión para verificar tu correo.")
    }

    suspend fun refreshAuthentication() {
        auth.currentUser?.reload()?.await()
    }

    fun signOut() = auth.signOut()

    suspend fun updateProfile(displayName: String, bio: String, isPublic: Boolean) {
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
        val ref = storage.reference.child("$folder/$uid/${System.currentTimeMillis()}.jpg")
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun saveDog(dog: PawlyDog) {
        val uid = requireUid()
        val ref = db.collection("users").document(uid)
        val snapshot = ref.get().await()
        val current = (snapshot.get("dogProfiles") as? List<Map<String, Any>>).orEmpty().toMutableList()
        val map = mapOf(
            "id" to dog.id.ifBlank { java.util.UUID.randomUUID().toString() },
            "name" to dog.name.trim().take(40),
            "breed" to dog.breed.trim().take(60),
            "age" to dog.age.trim().take(30),
            "description" to dog.description.trim().take(240),
            "traits" to dog.traits.take(8),
            "photoUrl" to dog.photoUrl
        )
        val id = map["id"]
        val index = current.indexOfFirst { it["id"] == id }
        if (index >= 0) current[index] = map else current.add(map)
        ref.update("dogProfiles", current).await()
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
                photoUrl = map["photoUrl"] as? String ?: ""
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
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0
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
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0
                    )
                })
            }
    }

    suspend fun publishPost(text: String, imageUrl: String, dogName: String) {
        val uid = requireVerifiedUid()
        val user = getMyProfile() ?: error("Completa primero tu perfil.")
        require(text.isNotBlank() || imageUrl.isNotBlank()) { "Añade un texto o una fotografía." }
        db.collection("posts").add(
            mapOf(
                "authorId" to uid,
                "authorName" to user.displayName.ifBlank { user.username },
                "authorPhoto" to user.photoUrl,
                "dogName" to dogName.take(40),
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
        val clean = text.trim().take(1000)
        require(clean.isNotBlank()) { "Escribe un comentario." }
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
            PawlyDog(m["id"] as? String ?: "", m["name"] as? String ?: "", m["breed"] as? String ?: "", m["age"] as? String ?: "", m["description"] as? String ?: "", (m["traits"] as? List<*>)?.filterIsInstance<String>().orEmpty(), m["photoUrl"] as? String ?: "")
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
        val me = requireUid()
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

    suspend fun report(targetId: String, targetType: String, reason: String) {
        val uid = requireVerifiedUid()
        db.collection("reports").add(mapOf(
            "reporterId" to uid, "targetId" to targetId, "targetType" to targetType,
            "reason" to reason.take(500), "status" to "open", "createdAt" to FieldValue.serverTimestamp()
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
        db.runTransaction { tx ->
            if (!tx.get(ref).exists()) {
                tx.set(ref, mapOf(
                    "participants" to ids,
                    "participantNames" to mapOf(me to (myProfile?.displayName ?: "Yo"), otherUid to (other?.displayName ?: "Usuario")),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
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
                    PawlyMessage(d.id, d.getString("senderId") ?: "", d.getString("text") ?: "", d.getTimestamp("createdAt")?.toDate()?.time ?: 0)
                })
            }
    }

    suspend fun sendMessage(conversationId: String, text: String) {
        val uid = requireVerifiedUid()
        val clean = text.trim().take(2000)
        require(clean.isNotBlank()) { "Escribe un mensaje." }
        val ref = db.collection("conversations").document(conversationId)
        db.runTransaction { tx ->
            tx.set(ref.collection("messages").document(), mapOf("senderId" to uid, "text" to clean, "createdAt" to FieldValue.serverTimestamp()))
            tx.update(ref, mapOf("lastMessage" to clean, "updatedAt" to FieldValue.serverTimestamp()))
        }.await()
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
