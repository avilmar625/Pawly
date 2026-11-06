package com.pawly.app

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import coil3.compose.AsyncImage
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pawly.app.content.DogNewsArticle
import com.pawly.app.content.DogNewsRepository
import com.pawly.app.content.PawlyContentSection
import com.pawly.app.content.PawlyLearningLibrary
import kotlinx.coroutines.CancellationException

@Composable
internal fun LearningHubScreen(dogName: String, elegantDesign: Boolean = false) {
    val context = LocalContext.current
    val communityRepository = remember(context) { FirebaseCommunityRepository.create(context) }
    var dogProfile by remember(dogName) { mutableStateOf<PawlyDog?>(null) }
    LaunchedEffect(dogName, communityRepository) {
        dogProfile = if (communityRepository?.currentUid != null) {
            runCatching { communityRepository.getMyDogs().firstOrNull { it.name.equals(dogName, ignoreCase = true) } }.getOrNull()
        } else null
    }
    val recommendedGuides = remember(dogProfile) {
        val profileSignals = (dogProfile?.traits.orEmpty().joinToString(" ") + " " + dogProfile?.age.orEmpty()).lowercase()
        val ids = when {
            listOf("cachorro", "puppy", "juvenil").any { it in profileSignals } ->
                listOf("positive-training", "body-language", "adoption")
            listOf("miedo", "insegur", "reactiv").any { it in profileSignals } ->
                listOf("body-language", "positive-training", "adoption")
            listOf("senior", "mayor", "anciano").any { it in profileSignals } ->
                listOf("sniffing", "enrichment", "body-language")
            listOf("activo", "energ", "deport").any { it in profileSignals } ->
                listOf("sniffing", "enrichment", "positive-training")
            else -> listOf("body-language", "positive-training", "sniffing")
        }
        PawlyLearningLibrary.guides.filter { it.id in ids }
    }
    val uriHandler = LocalUriHandler.current
    val preferences = remember(context) {
        context.getSharedPreferences("pawly_content", android.content.Context.MODE_PRIVATE)
    }
    var selectedSection by remember { mutableStateOf(PawlyContentSection.NEWS) }
    var search by remember { mutableStateOf("") }
    var articles by remember { mutableStateOf<List<DogNewsArticle>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var savedArticles by remember(preferences) {
        mutableStateOf(readSavedArticles(preferences))
    }

    LaunchedEffect(selectedSection, refreshKey) {
        if (selectedSection !in setOf(PawlyContentSection.NEWS, PawlyContentSection.ADOPTION, PawlyContentSection.TRAINING)) {
            articles = emptyList()
            error = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            articles = DogNewsRepository.load(selectedSection)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "No se han podido cargar las noticias. Comprueba tu conexión e inténtalo de nuevo."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(savedArticles) {
        writeSavedArticles(preferences, savedArticles)
    }

    val visibleArticles = remember(articles, search) {
        if (search.isBlank()) articles
        else articles.filter {
            it.title.contains(search, ignoreCase = true) ||
                it.source.contains(search, ignoreCase = true) ||
                it.summary.contains(search, ignoreCase = true)
        }
    }

    Column(
        Modifier.fillMaxSize().background(Cream).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (elegantDesign) {
            Box(Modifier.fillMaxWidth().height(218.dp).clip(RoundedCornerShape(28.dp))) {
                AsyncImage(
                    model = "https://images.unsplash.com/photo-1537151608828-ea2b11777ee8?w=1200&h=700&fit=crop",
                    contentDescription = "Perro explorando la naturaleza",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE610251F)))))
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("PAWLY · APRENDER Y DESCUBRIR", color = Color.White.copy(alpha = .85f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.7.sp)
                    Text("Un mundo mejor para ellos.", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("PAWLY · APRENDER Y DESCUBRIR", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.7.sp)
            Text("Un mundo mejor para ellos.", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("Actualidad, ideas, educación y recursos para compartir más vida juntos.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }

        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Aprender también es cuidar", color = Lime, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Pequeños cambios pueden mejorar su día a día.", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
                    Text("Fuentes externas y guías prácticas, reunidas en Pawly.", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .75f), style = MaterialTheme.typography.bodySmall)
                }
                Text("🐾", fontSize = 42.sp)
            }
        }

        if (dogProfile != null) {
            Surface(color = Paper, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Una selección para ${dogProfile!!.name}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Basada en la edad y las características que has guardado en su ficha.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    recommendedGuides.forEach { GuideCard(it) }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PawlyContentSection.entries.forEach { section ->
                FilterChip(
                    selected = selectedSection == section,
                    onClick = { selectedSection = section; search = "" },
                    label = { Text(section.label) }
                )
            }
        }

        when (selectedSection) {
            PawlyContentSection.NEWS -> {
                NewsSectionHeader("Noticias del mundo canino", "Actualidad de medios y organizaciones; abre cada noticia en su fuente original.")
                NewsSearch(search, { search = it })
                NewsResults(visibleArticles, loading, error, savedArticles.keys, onToggleSave = { id ->
                    val article = articles.firstOrNull { it.id == id }
                    if (article != null) savedArticles = if (id in savedArticles) savedArticles - id else savedArticles + (id to article)
                }, onRefresh = { refreshKey++ }, onOpen = { url -> runCatching { uriHandler.openUri(url) } })
            }
            PawlyContentSection.ADOPTION -> {
                NewsSectionHeader("Adopción y protección animal", "Noticias de adopción y entidades que trabajan por el bienestar animal.")
                NearbyShelterCard { runCatching { uriHandler.openUri("https://www.google.com/maps/search/?api=1&query=protectoras+de+animales+cerca+de+mi") } }
                NewsSearch(search, { search = it })
                NewsResults(visibleArticles, loading, error, savedArticles.keys, onToggleSave = { id ->
                    val article = articles.firstOrNull { it.id == id }
                    if (article != null) savedArticles = if (id in savedArticles) savedArticles - id else savedArticles + (id to article)
                }, onRefresh = { refreshKey++ }, onOpen = { url -> runCatching { uriHandler.openUri(url) } })
                SectionLabel("Entidades y directorios")
                Text("Consulta sus canales oficiales para conocer animales disponibles, requisitos y formas de colaborar.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                PawlyLearningLibrary.organisations.filter { it.tag in setOf("Adopción", "Protección", "Directorio") }.forEach {
                    ContentLinkCard(it) { runCatching { uriHandler.openUri(it.url) } }
                }
            }
            PawlyContentSection.TRAINING -> {
                NewsSectionHeader("Educación y comportamiento", "Artículos y recursos para comprender mejor a tu perro.")
                NewsSearch(search, { search = it })
                NewsResults(visibleArticles, loading, error, savedArticles.keys, onToggleSave = { id ->
                    val article = articles.firstOrNull { it.id == id }
                    if (article != null) savedArticles = if (id in savedArticles) savedArticles - id else savedArticles + (id to article)
                }, onRefresh = { refreshKey++ }, onOpen = { url -> runCatching { uriHandler.openUri(url) } })
                SectionLabel("Fuentes para seguir aprendiendo")
                PawlyLearningLibrary.organisations.filter { it.tag in setOf("Educación", "Bienestar") }.forEach {
                    ContentLinkCard(it) { runCatching { uriHandler.openUri(it.url) } }
                }
            }
            PawlyContentSection.VIDEOS -> {
                NewsSectionHeader("Vídeos para aprender", "Colecciones de búsquedas temáticas en YouTube. Revisa siempre la calidad y el enfoque de cada creador.")
                PawlyLearningLibrary.videos.forEach { video ->
                    VideoCard(video) {
                        val query = Uri.encode(video.searchQuery)
                        runCatching { uriHandler.openUri("https://www.youtube.com/results?search_query=$query") }
                    }
                }
                Surface(color = Lime.copy(alpha = .35f), shape = RoundedCornerShape(16.dp)) {
                    Text(
                        "Consejo Pawly: prioriza vídeos que expliquen el porqué de los ejercicios, respeten el ritmo del perro y no utilicen miedo, dolor ni intimidación.",
                        Modifier.padding(14.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            PawlyContentSection.SAVED -> {
                NewsSectionHeader("Tus noticias guardadas", "Artículos que has marcado para leer más tarde. Se conservan en este dispositivo.")
                if (savedArticles.isEmpty()) {
                    Text("Todavía no has guardado ninguna noticia. Usa el icono de marcador en cualquier artículo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    NewsResults(
                        articles = savedArticles.values.sortedByDescending { it.publishedAtMillis },
                        loading = false,
                        error = null,
                        savedIds = savedArticles.keys,
                        onToggleSave = { id -> savedArticles = savedArticles - id },
                        onRefresh = {},
                        onOpen = { url -> runCatching { uriHandler.openUri(url) } },
                        showRefresh = false
                    )
                }
            }
            PawlyContentSection.GUIDES -> {
                NewsSectionHeader("Guías Pawly", "Ideas prácticas para el día a día. Amplía cada tema para consultar sus puntos clave.")
                PawlyLearningLibrary.guides.forEach { guide -> GuideCard(guide) }
                SectionLabel("Recursos externos")
                PawlyLearningLibrary.organisations.forEach {
                    ContentLinkCard(it) { runCatching { uriHandler.openUri(it.url) } }
                }
            }
        }

        Text(
            "Pawly selecciona enlaces y muestra metadatos de fuentes externas. El contenido original pertenece a sus autores; comprueba fecha, contexto y credenciales. Las guías no sustituyen al veterinario ni a un profesional cualificado.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(8.dp))
    }
}
