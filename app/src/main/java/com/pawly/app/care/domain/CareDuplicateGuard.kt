package com.pawly.app.care.domain

import com.pawly.app.care.model.CareLogEntry
import com.pawly.app.care.model.CareTask
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Finds a same-day medication log recorded by another caregiver.
 * This is a UX safeguard only; Firestore rules remain the security boundary.
 */
internal object CareDuplicateGuard {
    fun findRecentMedicationLog(
        logs: List<CareLogEntry>,
        task: CareTask,
        currentUid: String,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CareLogEntry? {
        if (!task.action.equals("Medicación", ignoreCase = true) || currentUid.isBlank()) return null
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        return logs.asSequence()
            .filter { it.actorUid.isNotBlank() && it.actorUid != "local" && it.actorUid != currentUid }
            .filter { it.action.equals("Medicación", ignoreCase = true) }
            .filter { it.taskTitle.trim().equals(task.title.trim(), ignoreCase = true) }
            .filter {
                runCatching {
                    Instant.ofEpochMilli(it.occurredAt).atZone(zoneId).toLocalDate() == today
                }.getOrDefault(false)
            }
            .maxByOrNull { it.occurredAt }
    }
}
