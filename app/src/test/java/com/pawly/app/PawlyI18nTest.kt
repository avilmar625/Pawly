package com.pawly.app

import org.junit.Assert.assertEquals
import java.util.Locale
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
        assertEquals("Deskribapena", PawlyI18n.text("Descripción", "eu"))
        assertEquals("Argitaratu", PawlyI18n.text("Publicar", "eu"))
        assertEquals("Latitudea", PawlyI18n.text("Latitud", "eu"))
        assertEquals("Longitudea", PawlyI18n.text("Longitud", "eu"))
        assertEquals("Couldn't publish the event.", PawlyI18n.text("No se pudo publicar el evento.", "en"))
        assertEquals("No s'ha pogut publicar l'esdeveniment.", PawlyI18n.text("No se pudo publicar el evento.", "ca"))
        assertEquals("Impossible de publier l’événement.", PawlyI18n.text("No se pudo publicar el evento.", "fr"))
    }

    @Test
    fun eventReportingIsTranslatedInEverySupportedLanguage() {
        assertEquals("Denunciar evento", PawlyI18n.text("Denunciar evento", "es"))
        assertEquals("Report event", PawlyI18n.text("Denunciar evento", "en"))
        assertEquals("Denuncia l'esdeveniment", PawlyI18n.text("Denunciar evento", "ca"))
        assertEquals("Signaler l’événement", PawlyI18n.text("Denunciar evento", "fr"))
        assertEquals("Salatu ekitaldia", PawlyI18n.text("Denunciar evento", "eu"))
    }

    @Test
    fun professionalContactConsentIsTranslatedInAllSupportedLanguages() {
        assertEquals("Mostrar contacto profesional en tu perfil público", PawlyI18n.text("Mostrar contacto profesional en tu perfil público", "es"))
        assertEquals("Show professional contact on your public profile", PawlyI18n.text("Mostrar contacto profesional en tu perfil público", "en"))
        assertEquals("Mostra el contacte professional al teu perfil públic", PawlyI18n.text("Mostrar contacto profesional en tu perfil público", "ca"))
        assertEquals("Afficher le contact professionnel sur votre profil public", PawlyI18n.text("Mostrar contacto profesional en tu perfil público", "fr"))
        assertEquals("Erakutsi kontaktu profesionala zure profil publikoan", PawlyI18n.text("Mostrar contacto profesional en tu perfil público", "eu"))
    }

    @Test
    fun weeklyRoutineControlsAndReminderProgressAreLocalized() {
        assertEquals("Days of the week", PawlyI18n.text("Días de la semana", "en"))
        assertEquals("Dies de la setmana", PawlyI18n.text("Días de la semana", "ca"))
        assertEquals("Jours de la semaine", PawlyI18n.text("Días de la semana", "fr"))
        assertEquals("Asteko egunak", PawlyI18n.text("Días de la semana", "eu"))
        assertEquals("Reminder time (HH:mm)", PawlyI18n.text("Hora del aviso (HH:mm)", "en"))
        assertEquals("Rappels les jours sélectionnés", PawlyI18n.text("Avisos en los días seleccionados", "fr"))
        assertEquals("3 of 5 routines scheduled for today completed", PawlyI18n.careRoutineProgress(3, 5, "en"))
    }

    @Test
    fun supportsGermanAndEuropeanPortugueseNavigationAndDiaryLabels() {
        assertEquals("Start", PawlyI18n.text("Inicio", "de"))
        assertEquals("Tagebuch", PawlyI18n.text("Diario", "de"))
        assertEquals("Início", PawlyI18n.text("Inicio", "pt"))
        assertEquals("ERINNERUNGSTAGEBUCH", PawlyI18n.text("DIARIO DE RECUERDOS", "de"))
        assertEquals("DIÁRIO DE MEMÓRIAS", PawlyI18n.text("DIARIO DE RECUERDOS", "pt"))
        assertEquals("Erinnerungen filtern", PawlyI18n.text("Filtrar recuerdos", "de"))
        assertEquals("Filtrar memórias", PawlyI18n.text("Filtrar recuerdos", "pt"))
        assertEquals(java.util.Locale.GERMANY, PawlyI18n.locale("de"))
        assertEquals(java.util.Locale.forLanguageTag("pt-PT"), PawlyI18n.locale("pt"))
    }

    @Test
    fun germanAndEuropeanPortugueseAreAvailableForCoreNavigationAndMenus() {
        assertEquals("Community", PawlyI18n.text("Comunidad", "de"))
        assertEquals("Comunidade", PawlyI18n.text("Comunidad", "pt"))
        assertEquals("Bevorzugte Sprache", PawlyI18n.text("Idioma preferido", "de"))
        assertEquals("Idioma preferido", PawlyI18n.text("Idioma preferido", "pt"))
        assertEquals("Kategorie", PawlyI18n.text("Categoría", "de"))
        assertEquals("Categoria", PawlyI18n.text("Categoría", "pt"))
        assertEquals(Locale.GERMANY, PawlyI18n.locale("de"))
        assertEquals(Locale.forLanguageTag("pt-PT"), PawlyI18n.locale("pt"))
    }


    @Test
    fun healthPassportActionsAndStatusesAreTranslatedInAllSupportedLanguages() {
        assertEquals("Upcoming actions", PawlyI18n.text("Próximas actuaciones", "en"))
        assertEquals("Properes actuacions", PawlyI18n.text("Próximas actuaciones", "ca"))
        assertEquals("Prochaines actions", PawlyI18n.text("Próximas actuaciones", "fr"))
        assertEquals("Anstehende Maßnahmen", PawlyI18n.text("Próximas actuaciones", "de"))
        assertEquals("Próximas ações", PawlyI18n.text("Próximas actuaciones", "pt"))
        assertEquals("Hurrengo ekintzak", PawlyI18n.text("Próximas actuaciones", "eu"))
        assertEquals("Overdue", PawlyI18n.text("Vencido", "en"))
        assertEquals("Vençut", PawlyI18n.text("Vencido", "ca"))
        assertEquals("En retard", PawlyI18n.text("Vencido", "fr"))
        assertEquals("Überfällig", PawlyI18n.text("Vencido", "de"))
        assertEquals("Em atraso", PawlyI18n.text("Vencido", "pt"))
        assertEquals("Atzeratuta", PawlyI18n.text("Vencido", "eu"))
        assertEquals("Administration date", PawlyI18n.text("Fecha de administración", "en"))
        assertEquals("Verabreichungsdatum", PawlyI18n.text("Fecha de administración", "de"))
    }

    @Test
    fun spanishRemainsTheDefaultAndUnknownKeysFallBackSafely() {
        assertEquals("Inicio", PawlyI18n.text("Inicio", "es"))
        assertEquals("Texto nuevo", PawlyI18n.text("Texto nuevo", "en"))
    }
}
