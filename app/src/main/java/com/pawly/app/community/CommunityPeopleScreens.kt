package com.pawly.app

import android.Manifest
import android.content.Intent
import android.provider.ContactsContract
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
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
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf(emptyList<PawlyUser>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var inviteNotice by remember { mutableStateOf<String?>(null) }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { contactUri ->
        if (contactUri != null) {
            val contactName = runCatching {
                context.contentResolver.query(contactUri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull().orEmpty()
            val invite = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "¡Hola! Estoy usando Pawly, una comunidad para personas que comparten su vida con animales. ¡Únete y nos encontramos allí!")
            }
            runCatching { context.startActivity(Intent.createChooser(invite, if (contactName.isBlank()) "Invitar a Pawly" else "Invitar a $contactName")) }
                .onFailure { inviteNotice = "No se pudo abrir el menú para compartir." }
        }
    }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { users = emptyList(); return@LaunchedEffect }
        delay(350); loading = true
        try { users = repo.searchUsers(query); error = null } catch (e: Exception) { error = e.localizedMessage } finally { loading = false }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedButton(onClick = { contactPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Contacts, null)
            Spacer(Modifier.width(8.dp))
            Text(tr("Invitar a alguien desde tu agenda"))
        }
        Text(tr("La agenda se abre mediante el selector seguro de Android. Pawly no lee ni sube tu lista de contactos."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        if (inviteNotice != null) Text(inviteNotice!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text(tr("Buscar personas en Pawly")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        if (query.length < 2) Text(tr("Escribe al menos dos caracteres."), color = Muted)
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
        TextButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null); Text(tr("Volver")) }
        if (user == null) { Text(error ?: "Cargando perfil…", color = Muted); return@Column }
        Surface(color = Paper, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(user!!.photoUrl, user!!.displayName, 72)
                Text(user!!.displayName, style = MaterialTheme.typography.titleLarge, color = Forest)
                Text("@" + user!!.username, color = Muted)
                if (user!!.accountType == "professional") {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WorkspacePremium, null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Perfil profesional") + " · " + user!!.professionalCategory, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (user!!.professionalLocation.isNotBlank()) Text(tr("Zona: ") + user!!.professionalLocation, color = Muted, style = MaterialTheme.typography.bodySmall)
                    if (user!!.professionalDescription.isNotBlank()) Text(user!!.professionalDescription, color = Ink, modifier = Modifier.padding(8.dp))
                    if (user!!.professionalContactPublic && user!!.professionalContact.isNotBlank()) Text(tr("Contacto: ") + user!!.professionalContact, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                if (user!!.bio.isNotBlank()) Text(user!!.bio, color = Ink, modifier = Modifier.padding(8.dp))
                Text(user!!.followersCount.toString() + " seguidores · " + user!!.followingCount + " siguiendo", color = Muted, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uid != repo.currentUid) {
                        Button(onClick = {
                            error = null
                            busy = true
                            scope.launch {
                                runCatching {
                                    val next = !following
                                    repo.follow(uid, next)
                                    following = next
                                    user = user?.copy(followersCount = (user?.followersCount ?: 0) + if (next) 1 else -1)
                                }.onFailure { failure ->
                                    Log.e("PawlyCommunity", "Follow action failed", failure)
                                    error = failure.toPawlyMessage("No se pudo actualizar el seguimiento.")
                                }
                                busy = false
                            }
                        }, enabled = !busy) { Text(if (following) "Dejar de seguir" else "Seguir") }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(onClick = { onMessage(uid) }) { Icon(Icons.Default.Chat, null); Text(tr("Mensaje")) }
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, "Más opciones") }
                        DropdownMenu(menu, { menu = false }) {
                            if (uid != repo.currentUid) {
                                DropdownMenuItem(text = { Text(tr("Denunciar perfil")) }, onClick = { menu = false; onReport(uid to "user") })
                                DropdownMenuItem(text = { Text(tr("Bloquear usuario")) }, onClick = { menu = false; onBlock(uid) })
                            }
                        }
                    }
                }
            }
        }
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(tr("Sus compañeros"), color = Forest, fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(dogs, key = { it.id }) { DogProfileCard(it) }
            item { Text(tr("Publicaciones"), color = Forest, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
            if (userPosts.isEmpty()) item { Text(tr("Todavía no ha compartido publicaciones."), color = Muted) }
            items(userPosts, key = { "post-" + it.id }) { post ->
                Surface(color = Paper, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        if (post.text.isNotBlank()) Text(post.text, color = Ink)
                        if (post.imageUrl.isNotBlank()) AsyncImage(post.imageUrl, "Fotografía de la publicación", Modifier.fillMaxWidth().heightIn(max = 260.dp))
                        if (post.videoUrl.isNotBlank()) PawlyVideoPlayer(post.videoUrl, Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(12.dp)))
                        Text(post.likeCount.toString() + " me gusta · " + post.commentCount + " comentarios", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

