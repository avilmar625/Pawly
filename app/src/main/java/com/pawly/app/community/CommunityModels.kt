package com.pawly.app

data class PawlyUser(
    val uid: String = "",
    val username: String = "",
    val usernameLower: String = "",
    val displayName: String = "",
    val bio: String = "",
    val photoUrl: String = "",
    val isPublic: Boolean = true,
    val followersCount: Long = 0,
    val followingCount: Long = 0
)

data class PawlyDog(
    val id: String = "",
    val name: String = "",
    val breed: String = "",
    val age: String = "",
    val description: String = "",
    val traits: List<String> = emptyList(),
    val photoUrl: String = ""
)

data class PawlyPost(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhoto: String = "",
    val dogName: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val likeCount: Long = 0,
    val commentCount: Long = 0,
    val likedByMe: Boolean = false,
    val createdAt: Long = 0
)

data class PawlyComment(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val createdAt: Long = 0
)

data class PawlyConversation(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val otherUserId: String = "",
    val otherUserName: String = "",
    val lastMessage: String = "",
    val updatedAt: Long = 0
)

data class PawlyMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val createdAt: Long = 0
)
