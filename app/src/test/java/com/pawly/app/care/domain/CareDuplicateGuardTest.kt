package com.pawly.app.care.domain

import com.pawly.app.care.model.CareLogEntry
import com.pawly.app.care.model.CareTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CareDuplicateGuardTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val today = LocalDate.of(2026, 11, 16)
    private val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val task = CareTask(
        id = "monthly-pill", title = "Pastilla antiparasitaria",
        responsible = "Ana", frequency = "Mensual", completed = false,
        dose = "1 comprimido", time = "08:40", action = "Medicación"
    )

    private fun log(uid: String, title: String = task.title, day: LocalDate = today) = CareLogEntry(
        id = uid, taskTitle = title, action = "Medicación", actorUid = uid,
        actorName = uid, occurredAt = day.atTime(8, 40).atZone(zone).toInstant().toEpochMilli()
    )

    @Test fun detectsSameDayMedicationByAnotherCaregiver() {
        assertEquals("ana", CareDuplicateGuard.findRecentMedicationLog(
            listOf(log("ana")), task, "current-user", now, zone
        )?.actorUid)
    }

    @Test fun ignoresOwnMedicationLog() {
        assertNull(CareDuplicateGuard.findRecentMedicationLog(
            listOf(log("current-user")), task, "current-user", now, zone
        ))
    }

    @Test fun ignoresPreviousDayMedicationLog() {
        assertNull(CareDuplicateGuard.findRecentMedicationLog(
            listOf(log("ana", day = today.minusDays(1))), task, "current-user", now, zone
        ))
    }

    @Test fun ignoresDifferentMedicationTask() {
        assertNull(CareDuplicateGuard.findRecentMedicationLog(
            listOf(log("ana", title = "Otra medicación")), task, "current-user", now, zone
        ))
    }

    @Test fun ignoresNonMedicationTasks() {
        val walk = task.copy(action = "Paseo")
        assertNull(CareDuplicateGuard.findRecentMedicationLog(
            listOf(log("ana")), walk, "current-user", now, zone
        ))
    }
}
