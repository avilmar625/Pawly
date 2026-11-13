package com.pawly.app.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExploreCollectionPolicyTest {
    @Test
    fun acceptsCollectionWithinLimits() {
        ExploreCollectionPolicy.validate("Playas de verano", "Sitios tranquilos", 100)
    }

    @Test
    fun rejectsBlankOrOverlongNames() {
        assertThrows(IllegalArgumentException::class.java) {
            ExploreCollectionPolicy.validate("  ", "", 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExploreCollectionPolicy.validate("x".repeat(33), "", 0)
        }
    }

    @Test
    fun rejectsLongDescriptionsAndTooManyEntries() {
        assertThrows(IllegalArgumentException::class.java) {
            ExploreCollectionPolicy.validate("Rutas", "x".repeat(121), 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExploreCollectionPolicy.validate("Rutas", "", 101)
        }
    }

    @Test
    fun documentIdIsStableAndCaseInsensitive() {
        assertEquals(
            ExploreCollectionPolicy.documentId("Playas"),
            ExploreCollectionPolicy.documentId(" playas ")
        )
    }
}
