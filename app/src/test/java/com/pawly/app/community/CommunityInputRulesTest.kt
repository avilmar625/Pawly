package com.pawly.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CommunityInputRulesTest {
    @Test fun emailIsTrimmedAndNormalized() {
        assertEquals("persona@example.com", CommunityInputRules.validateEmail(" Persona@Example.com "))
    }

    @Test fun rejectsMalformedEmail() {
        assertThrows(IllegalArgumentException::class.java) {
            CommunityInputRules.validateEmail("no-es-un-correo")
        }
    }

    @Test fun usernameIsTrimmedAndRestricted() {
        assertEquals("chispita_32", CommunityInputRules.validateUsername(" chispita_32 "))
    }

    @Test fun rejectsShortOrUnsafeUsername() {
        assertThrows(IllegalArgumentException::class.java) {
            CommunityInputRules.validateUsername("ab!")
        }
    }

    @Test fun messageIsTrimmed() {
        assertEquals("Hola", CommunityInputRules.validateMessage("  Hola  "))
    }

    @Test fun rejectsBlankMessage() {
        assertThrows(IllegalArgumentException::class.java) {
            CommunityInputRules.validateMessage("   ")
        }
    }

    @Test fun rejectsInsecureImageUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            CommunityInputRules.validatePost("", "http://example.com/image.jpg")
        }
    }
}
