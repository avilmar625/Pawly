package com.pawly.app

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pawly.app.content.ContentLink
import com.pawly.app.content.DogNewsArticle
import com.pawly.app.content.DogNewsRepository
import com.pawly.app.content.LearningGuide
import com.pawly.app.content.PawlyContentSection
import com.pawly.app.content.PawlyLearningLibrary
import com.pawly.app.content.VideoCollection
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun LearningHubScreen() {
    val context = LocalContext.current
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
            return@LaunchedEffect
        }
        loading = true
        error = null
        val result = runCatching { DogNewsRepository.load(selectedSection) }
        result.onSuccess { articles = it }
            .onFailure { error = "No se han podido cargar las noticias. Comprueba tu conexión e inténtalo de nuevo." }
        loading = false
    }

    LaunchedEffect(savedArticles) {
        val json = JSONArray()
        savedArticles.values.forEach { article ->
            json.put(JSONObject()
                .put("title", article.title)
                .put("url", article.url)
                .put("source", article.source)
                .put("summary", article.summary)
                .put("publishedAt", article.publishedAt)
                .put("publishedAtMillis", article.publishedAtMillis)
                .put("section", article.section.name))
        }
        preferences.edit().putString("saved_articles_json", json.toString()).apply()
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
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("PAWLY · APRENDER Y DESCUBRIR", color = ForestLight, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.7.sp)
            Text("Un mundo mejor para ellos.", style = MaterialTheme.typography.headlineMedium, color = Ink)
            Text("Actualidad, ideas, educación y recursos para compartir más vida juntos.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }

        Surface(
            color = Forest,
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
                NewsSearch(search, { search = it })
                NewsResults(visibleArticles, loading, error, savedArticles.keys, onToggleSave = { id ->
                    val article = articles.firstOrNull { it.id == id }
                    if (article != null) savedArticles = if (id in savedArticles) savedArticles - id else savedArticles + (id to article)
                }, onRefresh = { refreshKey++ }, onOpen = { url -> runCatching { uriHandler.openUri(url) } })
                SectionLabel("Entidades y directorios")
                Text("Consulta sus canales oficiales para conocer animales disponibles, requisitos y formas de colaborar.", color = Muted, style = MaterialTheme.typography.bodySmall)
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
                        Modifier.padding(14.dp), color = Forest, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            PawlyContentSection.SAVED -> {
                NewsSectionHeader("Tus noticias guardadas", "Artículos que has marcado para leer más tarde. Se conservan en este dispositivo.")
                if (savedArticles.isEmpty()) {
                    Text("Todavía no has guardado ninguna noticia. Usa el icono de marcador en cualquier artículo.", color = Muted)
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
            color = Muted, style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun NewsSectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
        Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionLabel(value: String) {
    Text(value, color = Forest, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun NewsSearch(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        placeholder = { Text("Buscar noticias o temas") },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun NewsResults(
    articles: List<DogNewsArticle>,
    loading: Boolean,
    error: String?,
    savedIds: Set<String>,
    onToggleSave: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    showRefresh: Boolean = true
) {
    if (showRefresh) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Últimas publicaciones", modifier = Modifier.weight(1f), color = Forest, fontWeight = FontWeight.Bold)
            IconButton(onClick = onRefresh, enabled = !loading) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar noticias", tint = Forest)
            }
        }
    }
    if (loading) {
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Buscando noticias…", color = Muted)
        }
    } else if (error != null) {
        Surface(color = Peach.copy(alpha = .35f), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(error, color = Ink)
                OutlinedButton(onClick = onRefresh) { Text("Volver a intentar") }
            }
        }
    } else if (articles.isEmpty()) {
        Text("No hay resultados para esta búsqueda. Prueba con otras palabras o actualiza.", color = Muted, style = MaterialTheme.typography.bodyMedium)
    } else {
        articles.forEach { article ->
            NewsArticleCard(article, article.id in savedIds, onToggleSave = { onToggleSave(article.id) }, onOpen = { onOpen(article.url) })
        }
    }
}

@Composable
private fun NewsArticleCard(article: DogNewsArticle, saved: Boolean, onToggleSave: () -> Unit, onOpen: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(article.source, color = ForestLight, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text(article.publishedAt, color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            Text(article.title, color = Ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            if (article.summary.isNotBlank()) {
                Text(article.summary, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpen) {
                    Text("Leer en la fuente")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToggleSave) {
                    Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = if (saved) "Quitar de guardados" else "Guardar noticia", tint = Forest)
                }
            }
        }
    }
}

@Composable
private fun ContentLinkCard(link: ContentLink, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Paper, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(link.emoji, fontSize = 26.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(link.title, color = Forest, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(link.tag, color = ForestLight, style = MaterialTheme.typography.labelSmall)
                }
                Text(link.description, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.OpenInNew, contentDescription = "Abrir", tint = ForestLight, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun VideoCard(video: VideoCollection, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Paper, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Text(video.emoji, fontSize = 30.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(video.title, color = Forest, fontWeight = FontWeight.Bold)
                Text(video.description, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.PlayCircle, contentDescription = "Buscar vídeos", tint = ForestLight, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun GuideCard(guide: LearningGuide) {
    var expanded by remember(guide.id) { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        color = Paper,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(guide.emoji, fontSize = 28.sp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(guide.title, color = Forest, fontWeight = FontWeight.Bold)
                    Text(guide.subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Text(if (expanded) "−" else "+", color = Forest, fontSize = 24.sp, fontWeight = FontWeight.Light)
            }
            if (expanded) {
                HorizontalDivider(color = Line)
                guide.points.forEachIndexed { index, point ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
                        Text("${index + 1}.", color = ForestLight, fontWeight = FontWeight.Bold)
                        Text(point, color = Ink, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }
                Surface(color = Lime.copy(alpha = .35f), shape = RoundedCornerShape(12.dp)) {
                    Text(guide.note, Modifier.padding(12.dp), color = Forest, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun readSavedArticles(preferences: android.content.SharedPreferences): Map<String, DogNewsArticle> {
    return runCatching {
        val json = JSONArray(preferences.getString("saved_articles_json", "[]") ?: "[]")
        buildMap {
            for (index in 0 until json.length()) {
                val row = json.getJSONObject(index)
                val section = runCatching {
                    PawlyContentSection.valueOf(row.optString("section", PawlyContentSection.NEWS.name))
                }.getOrDefault(PawlyContentSection.NEWS)
                val article = DogNewsArticle(
                    title = row.optString("title"),
                    url = row.optString("url"),
                    source = row.optString("source"),
                    summary = row.optString("summary"),
                    publishedAt = row.optString("publishedAt"),
                    publishedAtMillis = row.optLong("publishedAtMillis"),
                    section = section
                )
                if (article.url.startsWith("https://")) put(article.id, article)
            }
        }
    }.getOrDefault(emptyMap())
}
