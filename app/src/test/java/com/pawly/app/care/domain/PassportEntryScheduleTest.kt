package com.pawly.app.care.domain

import com.pawly.app.care.model.PassportEntry
import com.pawly.app.care.model.PassportEntryStates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PassportEntryScheduleTest {
    private val today = LocalDate.parse("2026-11-17")

    @Test fun legacyEntryWithoutNewFieldsRemainsCompleted() {
        val legacy = PassportEntry("old", "Vacuna", "Rabia", "", "2025-02-01")
        assertEquals(PassportEntryStates.REALIZADO, PassportEntrySchedule.status(legacy, today))
    }

    @Test fun futureNextDoseIsPendingAndAppearsInAgenda() {
        val entry = PassportEntry("v1", "Vacuna", "Refuerzo", "", "2026-10-01", nextDoseDate = "2026-12-01")
        assertEquals(PassportEntryStates.PENDIENTE, PassportEntrySchedule.status(entry, today))
        assertEquals(listOf(entry), PassportEntrySchedule.agenda(listOf(entry), today))
    }

    @Test fun pastNextDoseIsOverdue() {
        val entry = PassportEntry("v2", "Desparasitación", "Refuerzo", "", "2026-08-01", nextDoseDate = "2026-11-01")
        assertEquals(PassportEntryStates.VENCIDO, PassportEntrySchedule.status(entry, today))
    }

    @Test fun explicitlyPendingWithoutNextDoseRemainsInAgenda() {
        val entry = PassportEntry("v3", "Visita veterinaria", "Revisión", "", "2026-11-20", status = PassportEntryStates.PENDIENTE)
        assertEquals(PassportEntryStates.PENDIENTE, PassportEntrySchedule.status(entry, today))
        assertTrue(PassportEntrySchedule.agenda(listOf(entry), today).isNotEmpty())
    }
    @Test fun pendingWithoutNextDoseDoesNotTreatAdministrationDateAsDueDate() {
        val entry = PassportEntry("v4", "Vacuna", "Revisión", "", "2026-11-10", status = PassportEntryStates.PENDIENTE)
        assertEquals(null, PassportEntrySchedule.dueDate(entry))
        assertEquals(PassportEntryStates.PENDIENTE, PassportEntrySchedule.status(entry, today))
        assertTrue(PassportEntrySchedule.agenda(listOf(entry), today).isNotEmpty())
    }

    @Test fun explicitlyOverdueEntryStaysOverdueEvenIfNextDateWasCorrected() {
        val entry = PassportEntry("v5", "Medicación", "Tratamiento", "", "2026-11-01",
            nextDoseDate = "2026-12-01", status = PassportEntryStates.VENCIDO)
        assertEquals(PassportEntryStates.VENCIDO, PassportEntrySchedule.status(entry, today))
    }

    @Test fun completedAdministrationCanHaveANextDoseInAgenda() {
        val entry = PassportEntry("v6", "Vacuna", "Refuerzo", "", "2026-11-01",
            nextDoseDate = "2026-12-01", status = PassportEntryStates.REALIZADO)
        assertEquals(PassportEntryStates.PENDIENTE, PassportEntrySchedule.status(entry, today))
        assertEquals(listOf(entry), PassportEntrySchedule.agenda(listOf(entry), today))
    }
}
