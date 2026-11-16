package com.pawly.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PawlyHomeTranslationsTest {
    @Test fun translatesMyPetAndAppointmentsNavigation() {
        assertEquals("My pet", PawlyI18n.text("Mi perro", "en"))
        assertEquals("La meva mascota", PawlyI18n.text("Mi perro", "ca"))
        assertEquals("Mon animal", PawlyI18n.text("Mi perro", "fr"))
        assertEquals("Appointments", PawlyI18n.text("Citas", "en"))
        assertEquals("Cites", PawlyI18n.text("Citas", "ca"))
        assertEquals("Rendez-vous", PawlyI18n.text("Citas", "fr"))
    }

    @Test fun translatesDailyCareStatusAndActions() {
        assertEquals("Today's care", PawlyI18n.text("Lo de hoy", "en"))
        assertEquals("Les cures d'avui", PawlyI18n.text("Lo de hoy", "ca"))
        assertEquals("Aujourd'hui", PawlyI18n.text("Lo de hoy", "fr"))
        assertEquals("Medication", PawlyI18n.text("Medicación", "en"))
        assertEquals("Medicació", PawlyI18n.text("Medicación", "ca"))
        assertEquals("Médicament", PawlyI18n.text("Medicación", "fr"))
    }

    @Test fun keepsSpanishFallbackForUnsupportedOrUnknownStrings() {
        assertEquals("Lo de hoy", PawlyI18n.text("Lo de hoy", "es"))
        assertEquals("Texto sin traducir", PawlyI18n.text("Texto sin traducir", "en"))
    }
}
