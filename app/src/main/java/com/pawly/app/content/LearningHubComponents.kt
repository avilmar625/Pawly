package com.pawly.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pawly.app.content.ContentLink
import com.pawly.app.content.DogNewsArticle
import com.pawly.app.content.LearningGuide
import com.pawly.app.content.VideoCollection

@Composable
internal fun NewsSectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
        Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun SectionLabel(value: String) {
    Text(value, color = Forest, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun NewsSearch(value: String, onValueChange: (String) -> Unit) {
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
internal fun NewsResults(
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
internal fun NewsArticleCard(article: DogNewsArticle, saved: Boolean, onToggleSave: () -> Unit, onOpen: () -> Unit) {
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
internal fun ContentLinkCard(link: ContentLink, onClick: () -> Unit) {
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
internal fun VideoCard(video: VideoCollection, onClick: () -> Unit) {
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
internal fun GuideCard(guide: LearningGuide) {
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

@Composable
internal fun NearbyShelterCard(onClick: () -> Unit) {
    Surface(onClick = onClick, color = Lime.copy(alpha = .48f), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("📍", fontSize = 27.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Buscar protectoras cerca de ti", color = Forest, fontWeight = FontWeight.Bold)
                Text("Abre el mapa y consulta centros y asociaciones de tu zona.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.OpenInNew, contentDescription = "Abrir mapa", tint = Forest)
        }
    }
}
