package com.pawly.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CommunityInputRulesTest {
    @Test fun petProfilesSupportDifferentSpecies() {
        assertEquals("Perro", PawlyDog().species)
        assertEquals("Gato", PawlyDog(species = "Gato").species)
        assertEquals("Conejo", PawlyDog(species = "Conejo").species)
    }

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
    @Test fun videoOnlyPostIsValid() {
        CommunityInputRules.validatePost("", "", "https://res.cloudinary.com/demo/video/upload/sample.mp4")
    }

    @Test fun rejectsInsecureVideoUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            CommunityInputRules.validatePost("", "", "http://example.com/video.mp4")
        }
    }

    @Test fun rejectsPhotoAndVideoTogether() {
        assertThrows(IllegalArgumentException::class.java) {
            CommunityInputRules.validatePost(
                "",
                "https://res.cloudinary.com/demo/image/upload/photo.jpg",
                "https://res.cloudinary.com/demo/video/upload/sample.mp4"
            )
        }
    }

}
