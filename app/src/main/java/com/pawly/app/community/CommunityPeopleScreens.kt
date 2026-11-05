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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
internal fun PeopleSearchScreen(repo: FirebaseCommunityRepository, onOpenUser: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf(emptyList<PawlyUser>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { users = emptyList(); return@LaunchedEffect }
        delay(350); loading = true
        try { users = repo.searchUsers(query); error = null } catch (e: Exception) { error = e.localizedMessage } finally { loading = false }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Buscar personas") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        if (query.length < 2) Text("Escribe al menos dos caracteres.", color = Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(users, key = { it.uid }) { user ->
                Surface(onClick = { onOpenUser(user.uid) }, color = Paper, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(user.photoUrl, user.displayName)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(user.displayName, color = Ink, fontWeight = FontWeight.Bold)
                            Text("@" + user.username + " · " + user.followersCount + " seguidores", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Forest)
                    }
                }
            }
        }
    }
}


@Composable
internal fun PublicUserScreen(repo: FirebaseCommunityRepository, uid: String, onBack: () -> Unit, onMessage: (String) -> Unit, onReport: (Pair<String, String>) -> Unit, onBlock: (String) -> Unit) {
    var user by remember(uid) { mutableStateOf<PawlyUser?>(null) }
    var dogs by remember(uid) { mutableStateOf(emptyList<PawlyDog>()) }
    var userPosts by remember(uid) { mutableStateOf(emptyList<PawlyPost>()) }
    var following by remember(uid) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(uid) {
        try { user = repo.getUser(uid); dogs = repo.getUserDogs(uid); following = repo.isFollowing(uid) }
        catch (e: Exception) { error = e.localizedMessage }
    }
    DisposableEffect(uid) {
        val listener = repo.listenUserPosts(uid, { userPosts = it }, { error = it })
        onDispose { listener.remove() }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null); Text("Volver") }
        if (user == null) { Text(error ?: "Cargando perfil…", color = Muted); return@Column }
        Surface(color = Paper, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(user!!.photoUrl, user!!.displayName, 72)
                Text(user!!.displayName, style = MaterialTheme.typography.titleLarge, color = Forest)
                Text("@" + user!!.username, color = Muted)
                if (user!!.bio.isNotBlank()) Text(user!!.bio, color = Ink, modifier = Modifier.padding(8.dp))
                Text(user!!.followersCount.toString() + " seguidores · " + user!!.followingCount + " siguiendo", color = Muted, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        busy = true
                        scope.launch {
                            runCatching {
                                val next = !following
                                repo.follow(uid, next)
                                following = next
                                user = user?.copy(followersCount = (user?.followersCount ?: 0) + if (next) 1 else -1)
                            }.onFailure { error = it.localizedMessage }
                            busy = false
                        }
                    }, enabled = !busy) { Text(if (following) "Dejar de seguir" else "Seguir") }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(onClick = { onMessage(uid) }) { Icon(Icons.Default.Chat, null); Text("Mensaje") }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, "Más opciones") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Denunciar perfil") }, onClick = { menu = false; onReport(uid to "user") })
                            DropdownMenuItem(text = { Text("Bloquear usuario") }, onClick = { menu = false; onBlock(uid) })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Sus compañeros", color = Forest, fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(dogs, key = { it.id }) { DogProfileCard(it) }
            item { Text("Publicaciones", color = Forest, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
            if (userPosts.isEmpty()) item { Text("Todavía no ha compartido publicaciones.", color = Muted) }
            items(userPosts, key = { "post-" + it.id }) { post ->
                Surface(color = Paper, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        if (post.text.isNotBlank()) Text(post.text, color = Ink)
                        if (post.imageUrl.isNotBlank()) AsyncImage(post.imageUrl, "Fotografía de la publicación", Modifier.fillMaxWidth().heightIn(max = 260.dp))
                        Text(post.likeCount.toString() + " me gusta · " + post.commentCount + " comentarios", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

