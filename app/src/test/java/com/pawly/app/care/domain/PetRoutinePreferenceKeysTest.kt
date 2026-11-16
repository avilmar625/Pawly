package com.pawly.app.care.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PetRoutinePreferenceKeysTest {
    @Test fun scopeIsStableForSamePetName() {
        assertEquals(PetRoutinePreferenceKeys.scope("Nala"), PetRoutinePreferenceKeys.scope("Nala"))
    }

    @Test fun differentPetNamesHaveDifferentScopes() {
        assertNotEquals(PetRoutinePreferenceKeys.scope("Nala"), PetRoutinePreferenceKeys.scope("Chispita"))
    }

    @Test fun preferenceKeysAreSeparatedByPurpose() {
        val scope = PetRoutinePreferenceKeys.scope("Nala")
        assertNotEquals(PetRoutinePreferenceKeys.day(scope), PetRoutinePreferenceKeys.walkDone(scope))
        assertNotEquals(PetRoutinePreferenceKeys.walkDone(scope), PetRoutinePreferenceKeys.medicineDone(scope))
        assertNotEquals(PetRoutinePreferenceKeys.photoPath(scope), PetRoutinePreferenceKeys.day(scope))
    }
}
