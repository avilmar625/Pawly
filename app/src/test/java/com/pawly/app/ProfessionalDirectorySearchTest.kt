package com.pawly.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfessionalDirectorySearchTest {
    @Test
    fun usesCategoryDefaultWhenSearchIsBlank() {
        assertEquals(
            "adiestradores caninos educación canina en Málaga",
            ProfessionalDirectorySearch.buildQuery("adiestradores caninos educación canina", "  ", " Málaga ")
        )
    }

    @Test
    fun customSearchOverridesCategoryAndKeepsArea() {
        assertEquals(
            "adiestramiento en positivo en Teatinos",
            ProfessionalDirectorySearch.buildQuery("adiestradores", "adiestramiento en positivo", "Teatinos")
        )
    }

    @Test
    fun blankAreaDoesNotAppendEmptyLocation() {
        assertEquals(
            "clínicas veterinarias",
            ProfessionalDirectorySearch.buildQuery("clínicas veterinarias", "", " ")
        )
    }
}
