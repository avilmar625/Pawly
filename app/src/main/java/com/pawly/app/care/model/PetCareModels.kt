package com.pawly.app.care.model

/** A private, user-entered item in a pet's health history. */
data class PassportEntry(
    val id: String,
    val type: String,
    val title: String,
    val detail: String,
    val date: String
)

/** A local care routine. It is not shared with other devices yet. */
data class CareTask(
    val id: String,
    val title: String,
    val responsible: String,
    val frequency: String,
    val completed: Boolean
)
