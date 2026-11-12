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
    fun translatesBasqueAndNewCommunityNavigation() {
        assertEquals("Hasiera", PawlyI18n.text("Inicio", "eu"))
        assertEquals("Ekitaldiak", PawlyI18n.text("Eventos", "eu"))
        assertEquals("Events", PawlyI18n.text("Eventos", "en"))
        assertEquals("Esdeveniments", PawlyI18n.text("Eventos", "ca"))
        assertEquals("Événements", PawlyI18n.text("Eventos", "fr"))
        assertEquals("Frantsesa", PawlyI18n.text("Français", "eu"))
        assertEquals("Mapa", PawlyI18n.text("Mapa", "eu"))
        assertEquals("Sortu ekitaldia", PawlyI18n.text("Crear evento", "eu"))
        assertEquals("Interesatzen zait", PawlyI18n.text("Me interesa", "eu"))
        assertEquals("Ekitaldi guztiak", PawlyI18n.text("Todos los eventos", "eu"))
    }

    @Test
    fun spanishRemainsTheDefaultAndUnknownKeysFallBackSafely() {
        assertEquals("Inicio", PawlyI18n.text("Inicio", "es"))
        assertEquals("Texto nuevo", PawlyI18n.text("Texto nuevo", "en"))
    }
}
