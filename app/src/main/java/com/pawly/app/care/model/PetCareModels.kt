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
    val completed: Boolean,
    val dose: String = "",
    val time: String = "",
    val completedOn: String = "",
    val action: String = "Otro"
)

/** A timestamped record of who performed a shared pet-care action. */
data class CareLogEntry(
    val id: String,
    val taskTitle: String,
    val action: String,
    val actorUid: String,
    val actorName: String,
    val occurredAt: Long,
    val note: String = ""
)
