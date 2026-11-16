package com.pawly.app.care.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PetProfileValidationTest {
    @Test fun trimsPetName() {
        assertEquals("Chispita", PetProfileValidation.name("  Chispita  "))
    }

    @Test fun rejectsBlankOrLongPetName() {
        assertThrows(IllegalArgumentException::class.java) { PetProfileValidation.name("  ") }
        assertThrows(IllegalArgumentException::class.java) { PetProfileValidation.name("a".repeat(41)) }
    }

    @Test fun acceptsOnlyHttpsPhotoUrls() {
        assertEquals("https://cdn.example/photo.jpg", PetProfileValidation.photoUrl("https://cdn.example/photo.jpg"))
        assertNull(PetProfileValidation.photoUrl(null))
        assertThrows(IllegalArgumentException::class.java) { PetProfileValidation.photoUrl("http://cdn.example/photo.jpg") }
    }

    @Test fun trimsDiaryAndPreservesOrder() {
        assertEquals(listOf("Primero", "Después"), PetProfileValidation.diary(listOf(" Primero ", "Después")))
    }

    @Test fun rejectsEmptyOversizedAndTooManyMemories() {
        assertThrows(IllegalArgumentException::class.java) { PetProfileValidation.diary(listOf(" ")) }
        assertThrows(IllegalArgumentException::class.java) { PetProfileValidation.diary(listOf("x".repeat(1001))) }
        assertThrows(IllegalArgumentException::class.java) { PetProfileValidation.diary(List(201) { "recuerdo" }) }
    }
}
