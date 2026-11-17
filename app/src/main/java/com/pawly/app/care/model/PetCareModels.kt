package com.pawly.app.care.model

/** A user-entered item in a pet's health passport.
 *
 * New fields are optional and have defaults so records written by older app
 * versions remain readable without a destructive migration.
 */
data class PassportEntry(
    val id: String,
    val type: String,
    val title: String,
    val detail: String,
    /** Legacy date field; represents the administration/record date. */
    val date: String,
    val product: String = "",
    val dose: String = "",
    val frequency: String = "",
    val clinic: String = "",
    val nextDoseDate: String = "",
    val status: String = PassportEntryStates.REALIZADO,
    val reminderEnabled: Boolean = false,
    val reminderTime: String = "09:00"
)

object PassportEntryStates {
    const val PENDIENTE = "PENDIENTE"
    const val REALIZADO = "REALIZADO"
    const val VENCIDO = "VENCIDO"

    val all = setOf(PENDIENTE, REALIZADO, VENCIDO)
}

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
    val action: String = "Otro",
    /** ISO weekdays (Monday=1 … Sunday=7); empty preserves legacy weekly behaviour. */
    val daysOfWeek: List<Int> = emptyList()
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
