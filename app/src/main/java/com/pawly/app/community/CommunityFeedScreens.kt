package com.pawly.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeedScreen(posts: List<PawlyPost>, followingIds: Set<String>, error: String?, onCreate: () -> Unit, onComments: (PawlyPost) -> Unit, onOpenUser: (String) -> Unit, onLike: (PawlyPost) -> Unit, onReport: (Pair<String, String>) -> Unit, onBlock: (String) -> Unit) {
    var followingOnly by remember { mutableStateOf(false) }
    var categoryFilter by remember { mutableStateOf("ALL") }
    var keyword by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf("recent") }
    val categoryOptions = listOf("ALL" to "Todo", "STORY" to "Historias", "ADOPTION" to "Adopción", "LOST" to "Perdidos", "FOUND" to "Encontrados", "FOSTER" to "Acogida")
    // Filtering and sorting can be O(n log n); keep the result stable across unrelated recompositions.
    val visiblePosts = remember(posts, followingIds, followingOnly, categoryFilter, keyword, sortOrder) {
        val filtered = posts.filter {
            (categoryFilter == "ALL" || it.category == categoryFilter) &&
                (!followingOnly || it.authorId in followingIds) &&
                (keyword.isBlank() || listOf(it.text, it.dogName, it.authorName).any { value ->
                    value.contains(keyword.trim(), ignoreCase = true)
                })
        }
        when (sortOrder) {
            "oldest" -> filtered.sortedBy { it.createdAt }
            "likes" -> filtered.sortedWith(compareByDescending<PawlyPost> { it.likeCount }.thenByDescending { it.createdAt })
            "comments" -> filtered.sortedWith(compareByDescending<PawlyPost> { it.commentCount }.thenByDescending { it.createdAt })
            else -> filtered.sortedByDescending { it.createdAt }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Surface(onClick = onCreate, color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddAPhoto, null, tint = Lime)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr("¿Qué aventura habéis vivido?"), color = Color.White, fontWeight = FontWeight.Bold)
                        Text(tr("Comparte una foto o una historia"), color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.ArrowForward, null, tint = Color.White)
                }
            }
        }
        item {
            PawlyDropdownSelector(
                title = tr("Mostrar publicaciones"),
                selectedLabel = tr(if (followingOnly) "Siguiendo" else "Todos"),
                options = listOf("all" to tr("Todos"), "following" to tr("Siguiendo")),
                selectedValue = if (followingOnly) "following" else "all",
                onSelected = { followingOnly = it == "following" },
                leadingIcon = Icons.Default.People
            )
            PawlyDropdownSelector(
                title = tr("Categoría"),
                selectedLabel = tr(categoryOptions.firstOrNull { it.first == categoryFilter }?.second ?: "Todo"),
                options = categoryOptions.map { it.first to tr(it.second) },
                selectedValue = categoryFilter,
                onSelected = { categoryFilter = it },
                leadingIcon = Icons.Default.FilterList
            )
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it.take(80) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                placeholder = { Text(tr("Buscar publicaciones por palabra clave…")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = { if (keyword.isNotBlank()) IconButton(onClick = { keyword = "" }) { Icon(Icons.Default.Close, contentDescription = tr("Borrar búsqueda")) } },
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(8.dp))
            val sortOptions = listOf(
                "recent" to tr("Más recientes primero"),
                "oldest" to tr("Más antiguas primero"),
                "likes" to tr("Más populares"),
                "comments" to tr("Más comentadas")
            )
            PawlyDropdownSelector(
                title = tr("Ordenar por"),
                selectedLabel = sortOptions.firstOrNull { it.first == sortOrder }?.second ?: tr("Más recientes primero"),
                options = sortOptions,
                selectedValue = sortOrder,
                onSelected = { sortOrder = it },
                leadingIcon = Icons.Default.Sort
            )

        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(tr("El muro de Pawly"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(tr(if (followingOnly) "Publicaciones de las cuentas que sigues" else "Historias, adopciones y avisos de la comunidad"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                        Text("${visiblePosts.size}", modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
        if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error) }
        if (visiblePosts.isEmpty() && error == null) item {
            EmptyCommunityCard(if (followingOnly) "Aún no hay novedades" else if (categoryFilter != "ALL") "Todavía no hay publicaciones de esta categoría" else "Todavía no hay publicaciones", if (followingOnly) "Sigue a otras personas para ver aquí sus aventuras." else if (categoryFilter != "ALL") "Prueba otra categoría o publica el primer aviso." else "Sé de las primeras personas en compartir una aventura.")
        }
        items(visiblePosts, key = { it.id }) { post ->
            PostCard(post, onComments, onOpenUser, onLike, onReport, onBlock)
        }
    }
}


@Composable
internal fun PostCard(post: PawlyPost, onComments: (PawlyPost) -> Unit, onOpenUser: (String) -> Unit, onLike: (PawlyPost) -> Unit, onReport: (Pair<String, String>) -> Unit, onBlock: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable { onOpenUser(post.authorId) }) {
                    Avatar(post.authorPhoto, post.authorName)
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f).clickable { onOpenUser(post.authorId) }) {
                    Text(post.authorName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    Text((if (post.dogName.isBlank()) "Una historia Pawly" else "Con " + post.dogName) + " · " + when (post.category) { "ADOPTION" -> "Adopción"; "LOST" -> "Perdido"; "FOUND" -> "Encontrado"; "FOSTER" -> "Acogida"; else -> "Comunidad" }, color = if (post.category == "LOST") Color(0xFFB44E43) else ForestLight, style = MaterialTheme.typography.labelSmall)
                    if (post.createdAt > 0L) Text(
                        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(post.createdAt)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más opciones", tint = Muted) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(tr("Denunciar publicación")) }, onClick = { menu = false; onReport(post.id to "post") })
                        DropdownMenuItem(text = { Text(tr("Bloquear usuario")) }, onClick = { menu = false; onBlock(post.authorId) })
                    }
                }
            }
            if (post.text.isNotBlank()) Text(post.text, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 15.dp, vertical = 4.dp))
            if (post.imageUrl.isNotBlank()) PawlyRemoteImage(post.imageUrl, "Fotografía de la publicación", Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 340.dp))
            if (post.videoUrl.isNotBlank()) PawlyVideoPlayer(post.videoUrl, Modifier.fillMaxWidth().height(240.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onLike(post) }) {
                    Icon(if (post.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (post.likedByMe) Color(0xFFC65B55) else Forest)
                    Text(" " + post.likeCount, color = MaterialTheme.colorScheme.onSurface)
                }
                TextButton(onClick = { onComments(post) }) {
                    Icon(Icons.Default.ChatBubbleOutline, null, tint = MaterialTheme.colorScheme.primary)
                    Text(" " + post.commentCount + " comentarios", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

