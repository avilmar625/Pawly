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

@Composable
internal fun FeedScreen(posts: List<PawlyPost>, followingIds: Set<String>, error: String?, onCreate: () -> Unit, onComments: (PawlyPost) -> Unit, onOpenUser: (String) -> Unit, onLike: (PawlyPost) -> Unit, onReport: (Pair<String, String>) -> Unit, onBlock: (String) -> Unit) {
    var activeFilter by remember { mutableStateOf("ALL") }
    var keyword by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf("recent") }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val filterOptions = listOf(
        "ALL" to "Todo",
        "FOLLOWING" to "Siguiendo",
        "STORY" to "Historias",
        "ADOPTION" to "Adopción",
        "LOST" to "Perdidos",
        "FOUND" to "Encontrados",
        "FOSTER" to "Acogida"
    )
    val visiblePosts = remember(posts, followingIds, activeFilter, keyword, sortOrder) {
        val filtered = posts.filter { post ->
            (activeFilter != "FOLLOWING" || post.authorId in followingIds) &&
                (activeFilter == "ALL" || activeFilter == "FOLLOWING" || post.category == activeFilter) &&
                (keyword.isBlank() || listOf(post.text, post.dogName, post.authorName).any { value ->
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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                onClick = onCreate,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(17.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddAPhoto, null, tint = Lime)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(tr("¿Qué habéis hecho hoy?"), color = Color.White, fontWeight = FontWeight.Bold)
                        Text(tr("Comparte una foto, una aventura o una historia"), color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.ArrowForward, null, tint = Color.White)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it.take(80) },
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp, max = 58.dp),
                    singleLine = true,
                    placeholder = { Text(tr("Buscar en Comunidad…"), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (keyword.isNotBlank()) {
                            IconButton(onClick = { keyword = "" }) {
                                Icon(Icons.Default.Close, contentDescription = tr("Borrar búsqueda"))
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                )
                Box {
                    OutlinedButton(
                        onClick = { showFilterMenu = true },
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr(filterOptions.firstOrNull { it.first == activeFilter }?.second ?: "Todo"), maxLines = 1)
                    }
                    DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                        filterOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(tr(label)) },
                                onClick = { activeFilter = value; showFilterMenu = false },
                                leadingIcon = if (activeFilter == value) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = tr("Ordenar publicaciones"), tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        listOf(
                            "recent" to "Más recientes",
                            "oldest" to "Más antiguas",
                            "likes" to "Más populares",
                            "comments" to "Más comentadas"
                        ).forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(tr(label)) },
                                onClick = { sortOrder = value; showSortMenu = false },
                                leadingIcon = if (sortOrder == value) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                tr("${visiblePosts.size} publicaciones"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
        if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error) }
        if (visiblePosts.isEmpty() && error == null) item {
            EmptyCommunityCard(
                if (activeFilter == "FOLLOWING") "Aún no hay novedades" else if (activeFilter != "ALL") "Todavía no hay publicaciones de esta categoría" else "Todavía no hay publicaciones",
                if (activeFilter == "FOLLOWING") "Sigue a otras personas para ver aquí sus aventuras." else if (activeFilter != "ALL") "Prueba otra categoría o publica el primer aviso." else "Sé de las primeras personas en compartir una aventura."
            )
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

