package com.pawly.app

import com.pawly.app.tr

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
    PawlySectionHeading(title = title, subtitle = subtitle)
}

@Composable
internal fun SectionLabel(value: String) {
    Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun NewsSearch(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        placeholder = { Text(tr("Buscar noticias o temas")) },
        shape = MaterialTheme.shapes.medium
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
            Text(tr("Últimas publicaciones"), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            IconButton(onClick = onRefresh, enabled = !loading) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar noticias", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (loading) {
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(tr("Buscando noticias…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else if (error != null) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(error, color = MaterialTheme.colorScheme.onSurface)
                OutlinedButton(onClick = onRefresh) { Text(tr("Volver a intentar")) }
            }
        }
    } else if (articles.isEmpty()) {
        Text(tr("No hay resultados para esta búsqueda. Prueba con otras palabras o actualiza."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    } else {
        articles.forEach { article ->
            NewsArticleCard(article, article.id in savedIds, onToggleSave = { onToggleSave(article.id) }, onOpen = { onOpen(article.url) })
        }
    }
}

@Composable
internal fun NewsArticleCard(article: DogNewsArticle, saved: Boolean, onToggleSave: () -> Unit, onOpen: () -> Unit) {
    PawlyCard(contentPadding = 15.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(article.source, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text(article.publishedAt, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Text(article.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            if (article.summary.isNotBlank()) {
                Text(article.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpen) {
                    Text(tr("Leer en la fuente"))
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToggleSave) {
                    Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = if (saved) "Quitar de guardados" else "Guardar noticia", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
internal fun ContentLinkCard(link: ContentLink, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(link.emoji, fontSize = 26.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(link.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(link.tag, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
                }
                Text(link.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.OpenInNew, contentDescription = "Abrir", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun VideoCard(video: VideoCollection, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Text(video.emoji, fontSize = 30.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(video.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(video.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.PlayCircle, contentDescription = "Buscar vídeos", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
internal fun GuideCard(guide: LearningGuide) {
    var expanded by remember(guide.id) { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(guide.emoji, fontSize = 28.sp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(guide.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(guide.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Text(if (expanded) "−" else "+", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp, fontWeight = FontWeight.Light)
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                guide.points.forEachIndexed { index, point ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
                        Text(tr("${index + 1}."), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        Text(point, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Text(guide.note, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun NearbyShelterCard(onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("📍"), fontSize = 27.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tr("Buscar protectoras cerca de ti"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(tr("Abre el mapa y consulta centros y asociaciones de tu zona."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.OpenInNew, contentDescription = "Abrir mapa", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
