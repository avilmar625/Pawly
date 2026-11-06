package com.pawly.app.care.model

/** A user-entered item in a pet's shared health passport. */
data class PassportEntry(
    val id: String,
    val type: String,
    val title: String,
    val detail: String,
    val date: String
)

/** A care routine stored locally for offline use and synchronized to the pet's shared Firestore space when signed in. */
data class CareTask(
    val id: String,
    val title: String,
    val responsible: String,
    val frequency: String,
    val completed: Boolean
)
