package com.pawly.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PawlyI18nTest {
    @Test
    fun translatesMainNavigationIntoEnglish() {
        assertEquals("Home", PawlyI18n.text("Inicio", "en"))
        assertEquals("Community", PawlyI18n.text("Comunidad", "en"))
        assertEquals("More", PawlyI18n.text("Más", "en"))
    }

    @Test
    fun translatesMainNavigationIntoCatalanAndFrench() {
        assertEquals("Inici", PawlyI18n.text("Inicio", "ca"))
        assertEquals("Communauté", PawlyI18n.text("Comunidad", "fr"))
    }

    @Test
    fun translatesDynamicPetNamesWithoutLosingTheName() {
        assertEquals("Shared profile · Nala", PawlyI18n.text("Perfil compartido · Nala", "en"))
        assertEquals("Profil partagé · Nala", PawlyI18n.text("Perfil compartido · Nala", "fr"))
    }

    @Test
    fun translatesSharedPetInvitationDynamically() {
        assertEquals(
            "Alex wants to share Nala's profile with you.",
            PawlyI18n.text("Alex quiere compartir contigo el perfil de Nala.", "en")
        )
    }
    @Test
    fun spanishRemainsTheDefaultAndUnknownKeysFallBackSafely() {
        assertEquals("Inicio", PawlyI18n.text("Inicio", "es"))
        assertEquals("Texto nuevo", PawlyI18n.text("Texto nuevo", "en"))
    }
}
