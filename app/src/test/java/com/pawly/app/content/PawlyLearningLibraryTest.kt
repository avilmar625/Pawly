package com.pawly.app.content

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PawlyLearningLibraryTest {
    @Test fun learningLibraryHasPracticalGuides() {
        assertTrue(PawlyLearningLibrary.guides.size >= 5)
        assertTrue(PawlyLearningLibrary.guides.all { it.points.isNotEmpty() && it.note.isNotBlank() })
    }

    @Test fun externalResourcesUseSecureLinks() {
        assertTrue(PawlyLearningLibrary.organisations.isNotEmpty())
        assertTrue(PawlyLearningLibrary.organisations.all { it.url.startsWith("https://") })
    }

    @Test fun videoCollectionsHaveSearchQueries() {
        assertFalse(PawlyLearningLibrary.videos.isEmpty())
        assertTrue(PawlyLearningLibrary.videos.all { it.searchQuery.isNotBlank() })
    }
}
