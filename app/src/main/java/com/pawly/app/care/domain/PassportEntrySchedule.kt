package com.pawly.app.care.domain

import com.pawly.app.care.model.PassportEntry
import com.pawly.app.care.model.PassportEntryStates
import java.time.LocalDate

/** Pure scheduling rules for health-passport entries. */
object PassportEntrySchedule {
    fun dueDate(entry: PassportEntry): LocalDate? {
        val raw = entry.nextDoseDate.ifBlank {
            if (entry.status == PassportEntryStates.PENDIENTE) entry.date else ""
        }
        return raw.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    fun status(entry: PassportEntry, today: LocalDate = LocalDate.now()): String {
        val due = dueDate(entry)
        if (due != null) return if (due.isBefore(today)) PassportEntryStates.VENCIDO else PassportEntryStates.PENDIENTE
        return if (entry.status == PassportEntryStates.PENDIENTE) PassportEntryStates.VENCIDO
        else PassportEntryStates.REALIZADO
    }

    fun agenda(entries: List<PassportEntry>, today: LocalDate = LocalDate.now()): List<PassportEntry> =
        entries.filter { status(it, today) != PassportEntryStates.REALIZADO }
            .sortedWith(compareBy<PassportEntry> { dueDate(it) ?: LocalDate.MAX }.thenBy { it.title.lowercase() })
}
