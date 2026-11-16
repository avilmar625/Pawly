package com.pawly.app.community

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfessionalContactPolicyTest {
    @Test
    fun contactIsPrivateByDefault() {
        assertFalse(ProfessionalContactPolicy.isPublic("professional", false, "work@example.com"))
    }

    @Test
    fun explicitConsentIsRequiredForProfessionalAccounts() {
        assertTrue(ProfessionalContactPolicy.isPublic("professional", true, "work@example.com"))
        assertFalse(ProfessionalContactPolicy.isPublic("personal", true, "work@example.com"))
    }

    @Test
    fun emptyContactIsNeverPublished() {
        assertFalse(ProfessionalContactPolicy.isPublic("professional", true, "  "))
    }
}
