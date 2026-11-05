package com.pawly.app

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
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class CommunityPage(val label: String) {
    FEED("Muro"), SEARCH("Personas"), MESSAGES("Mensajes"), PROFILE("Mi perfil"), MODERATION("Moderar")
}

@Composable
internal fun CommunityScreen() {
    val context = LocalContext.current
    val repository = remember(context) { FirebaseCommunityRepository.create(context) }
    if (repository == null) {
        FirebaseSetupPanel()
        return
    }
    var uid by remember { mutableStateOf(repository.currentUid) }
    if (uid.isNullOrBlank()) CommunityAuthPanel(repository) { uid = repository.currentUid }
    else CommunityHub(repository, uid!!) { uid = repository.currentUid }
}

@Composable
private fun FirebaseSetupPanel() {
    Column(Modifier.fillMaxSize().background(Cream).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🐾", fontSize = 54.sp)
        Text("La comunidad Pawly", style = MaterialTheme.typography.headlineMedium, color = Forest)
        Text("Conecta con personas que comparten su vida con perros. Para activar cuentas, sincronización y fotografías hay que vincular el proyecto Firebase.", color = Muted)
        Spacer(Modifier.height(18.dp))
        Surface(color = Paper, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Servicios que se activarán", fontWeight = FontWeight.Bold, color = Forest)
                Text("• Cuentas con correo y contraseña")
                Text("• Perfiles públicos y perfiles de perros")
                Text("• Muro, likes, comentarios y seguidores")
                Text("• Mensajes privados en tiempo real")
                Text("• Fotografías optimizadas con Cloudinary")
                Text("Añade app/google-services.json siguiendo docs/FIREBASE_SETUP.md.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CommunityAuthPanel(repo: FirebaseCommunityRepository, onAuthenticated: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val webClientId = remember(context) {
        val resourceId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resourceId != 0) context.getString(resourceId) else ""
    }
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(Cream).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("PAWLY · COMUNIDAD", color = ForestLight, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Text(if (register) "Crea tu cuenta" else "Qué alegría verte", style = MaterialTheme.typography.headlineLarge, color = Forest)
        Text("Comparte aventuras, descubre perros y conoce a sus personas.", color = Muted)
        Spacer(Modifier.height(20.dp))
        if (register) OutlinedTextField(username, { username = it }, label = { Text("Nombre de usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña (mín. 6 caracteres)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        Button(onClick = {
            busy = true; error = null
            scope.launch {
                try {
                    if (register) repo.register(email, password, username) else repo.signIn(email, password)
                    onAuthenticated()
                } catch (e: Exception) { error = e.localizedMessage ?: "No se pudo completar la operación." }
                finally { busy = false }
            }
        }, enabled = !busy && email.contains("@") && password.length >= 6 && (!register || username.length >= 3), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else Text(if (register) "Crear cuenta" else "Iniciar sesión")
        }
        if (!register) {
            OutlinedButton(
                onClick = {
                    error = null
                    status = null
                    if (webClientId.isBlank()) {
                        error = "Falta la configuración OAuth de Google. Descarga de nuevo google-services.json después de activar Google en Firebase y colócalo en app/."
                    } else {
                        busy = true
                        scope.launch {
                            try {
                                val googleOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(webClientId)
                                    .build()
                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleOption)
                                    .build()
                                val result = credentialManager.getCredential(context, request)
                                val credential = result.credential as? CustomCredential
                                if (credential == null || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                    error = "No se ha recibido una credencial de Google válida."
                                } else {
                                    val googleToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                                    repo.signInWithGoogle(googleToken)
                                    onAuthenticated()
                                }
                            } catch (e: Exception) {
                                error = e.localizedMessage ?: "No se pudo iniciar sesión con Google."
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Continuar con Google")
            }
        }
        if (status != null) Text(status!!, color = ForestLight, style = MaterialTheme.typography.bodySmall)
        if (!register) TextButton(onClick = {
            error = null; status = null
            scope.launch {
                runCatching { repo.sendPasswordReset(email); status = "Te hemos enviado un enlace para restablecer la contraseña." }
                    .onFailure { error = it.localizedMessage }
            }
        }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("He olvidado mi contraseña", color = Forest) }
        TextButton(onClick = { register = !register; error = null; status = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (register) "Ya tengo una cuenta · Iniciar sesión" else "¿Primera vez en Pawly? Crear cuenta", color = Forest)
        }
        Text("Respeta la privacidad de otras personas y no compartas datos personales sensibles.", color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CommunityHub(repo: FirebaseCommunityRepository, uid: String, onSignOut: () -> Unit) {
    var page by remember { mutableStateOf(CommunityPage.FEED) }
    var posts by remember { mutableStateOf(emptyList<PawlyPost>()) }
    var followingIds by remember { mutableStateOf(emptySet<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedUser by remember { mutableStateOf<String?>(null) }
    var chat by remember { mutableStateOf<PawlyConversation?>(null) }
    var createPost by remember { mutableStateOf(false) }
    var commentsPost by remember { mutableStateOf<PawlyPost?>(null) }
    var reportTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var blockTarget by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var emailVerified by remember { mutableStateOf(repo.isEmailVerified) }
    var admin by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(repo) { admin = runCatching { repo.isAdmin() }.getOrDefault(false) }
    DisposableEffect(repo, uid) {
        val feedListener = repo.listenFeed({ posts = it; error = null }, { error = it })
        val followingListener = repo.listenFollowingIds({ followingIds = it }, { error = it })
        onDispose { feedListener.remove(); followingListener.remove() }
    }
    Column(Modifier.fillMaxSize().background(Cream)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PAWLY", color = Forest, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, fontSize = 12.sp)
                Text("Comunidad", color = Ink, style = MaterialTheme.typography.headlineMedium)
            }
            IconButton(onClick = { repo.signOut(); onSignOut() }) { Icon(Icons.Default.Logout, "Cerrar sesión", tint = Forest) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 7.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            CommunityPage.entries.filter { it != CommunityPage.MODERATION || admin }.forEach { item ->
                FilterChip(selected = page == item, onClick = { page = item; selectedUser = null; chat = null }, label = { Text(item.label, maxLines = 1) })
            }
        }
        if (!emailVerified) {
            Surface(color = Lime.copy(alpha = .45f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text("Verifica tu correo para proteger tu cuenta.", color = Forest, style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = {
                            scope.launch { runCatching { repo.resendVerificationEmail(); notice = "Correo de verificación enviado." }.onFailure { notice = it.localizedMessage } }
                        }) { Text("Reenviar correo") }
                        TextButton(onClick = {
                            scope.launch { runCatching { repo.refreshAuthentication(); emailVerified = repo.isEmailVerified }.onFailure { notice = it.localizedMessage } }
                        }) { Text("Ya lo he verificado") }
                    }
                }
            }
        }
        if (notice != null) Text(notice!!, color = ForestLight, modifier = Modifier.padding(horizontal = 16.dp))
        when {
            selectedUser != null -> PublicUserScreen(repo, selectedUser!!, onBack = { selectedUser = null }, onMessage = { id ->
                scope.launch { runCatching { chat = PawlyConversation(id = repo.startConversation(id), otherUserId = id); page = CommunityPage.MESSAGES; selectedUser = null }.onFailure { notice = it.localizedMessage } }
            }, onReport = { reportTarget = it }, onBlock = { id -> blockTarget = id })
            chat != null -> ChatScreen(repo, chat!!, onBack = { chat = null })
            page == CommunityPage.FEED -> FeedScreen(posts, followingIds, error, onCreate = { createPost = true }, onComments = { commentsPost = it }, onOpenUser = { selectedUser = it }, onLike = { post ->
                scope.launch { runCatching { repo.toggleLike(post.id) }.onFailure { notice = it.localizedMessage } }
            }, onReport = { reportTarget = it }, onBlock = { id -> blockTarget = id })
            page == CommunityPage.SEARCH -> PeopleSearchScreen(repo) { selectedUser = it }
            page == CommunityPage.MESSAGES -> ConversationsScreen(repo) { chat = it }
            page == CommunityPage.PROFILE -> MyCommunityProfile(repo, onSignOut)
            page == CommunityPage.MODERATION -> if (admin) ModerationScreen(repo) else EmptyCommunityCard("Acceso restringido", "Solo el equipo de moderación puede entrar aquí.")
        }
    }
    if (createPost) CreatePostDialog(repo, { createPost = false }) { notice = "Publicación compartida."; createPost = false }
    if (commentsPost != null) CommentsDialog(repo, commentsPost!!, { commentsPost = null })
    if (reportTarget != null) ReportDialog(repo, reportTarget!!.first, reportTarget!!.second, { reportTarget = null }) {
        notice = "Gracias. Hemos recibido tu aviso."
        reportTarget = null
    }
    if (blockTarget != null) BlockConfirmDialog(onDismiss = { blockTarget = null }) {
        val target = blockTarget!!
        scope.launch {
            runCatching { repo.block(target); notice = "Usuario bloqueado y ocultado de tu muro." }
                .onFailure { notice = it.localizedMessage }
            blockTarget = null
        }
    }
}

@Composable
private fun FeedScreen(posts: List<PawlyPost>, followingIds: Set<String>, error: String?, onCreate: () -> Unit, onComments: (PawlyPost) -> Unit, onOpenUser: (String) -> Unit, onLike: (PawlyPost) -> Unit, onReport: (Pair<String, String>) -> Unit, onBlock: (String) -> Unit) {
    var followingOnly by remember { mutableStateOf(false) }
    val visiblePosts = if (followingOnly) posts.filter { it.authorId in followingIds } else posts
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Surface(onClick = onCreate, color = Forest, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddAPhoto, null, tint = Lime)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("¿Qué aventura habéis vivido?", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Comparte una foto o una historia", color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.ArrowForward, null, tint = Color.White)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !followingOnly, onClick = { followingOnly = false }, label = { Text("Todos") })
                FilterChip(selected = followingOnly, onClick = { followingOnly = true }, label = { Text("Siguiendo") })
            }
        }
        if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error) }
        if (visiblePosts.isEmpty() && error == null) item {
            EmptyCommunityCard(if (followingOnly) "Aún no hay novedades" else "Todavía no hay publicaciones", if (followingOnly) "Sigue a otras personas para ver aquí sus aventuras." else "Sé de las primeras personas en compartir una aventura.")
        }
        items(visiblePosts, key = { it.id }) { post ->
            PostCard(post, onComments, onOpenUser, onLike, onReport, onBlock)
        }
    }
}

@Composable
private fun PostCard(post: PawlyPost, onComments: (PawlyPost) -> Unit, onOpenUser: (String) -> Unit, onLike: (PawlyPost) -> Unit, onReport: (Pair<String, String>) -> Unit, onBlock: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = Paper, shape = RoundedCornerShape(21.dp), shadowElevation = 1.dp) {
        Column {
            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(post.authorPhoto, post.authorName)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f).clickable { onOpenUser(post.authorId) }) {
                    Text(post.authorName, color = Ink, fontWeight = FontWeight.Bold)
                    Text(if (post.dogName.isBlank()) "Una historia Pawly" else "Con " + post.dogName, color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más opciones", tint = Muted) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Denunciar publicación") }, onClick = { menu = false; onReport(post.id to "post") })
                        DropdownMenuItem(text = { Text("Bloquear usuario") }, onClick = { menu = false; onBlock(post.authorId) })
                    }
                }
            }
            if (post.text.isNotBlank()) Text(post.text, color = Ink, modifier = Modifier.padding(horizontal = 15.dp, vertical = 4.dp))
            if (post.imageUrl.isNotBlank()) AsyncImage(post.imageUrl, "Fotografía de la publicación", Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 340.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onLike(post) }) {
                    Icon(if (post.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (post.likedByMe) Color(0xFFC65B55) else Forest)
                    Text(" " + post.likeCount, color = Ink)
                }
                TextButton(onClick = { onComments(post) }) {
                    Icon(Icons.Default.ChatBubbleOutline, null, tint = Forest)
                    Text(" " + post.commentCount + " comentarios", color = Ink)
                }
            }
        }
    }
}

@Composable
private fun PeopleSearchScreen(repo: FirebaseCommunityRepository, onOpenUser: (String) -> Unit) {
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
private fun PublicUserScreen(repo: FirebaseCommunityRepository, uid: String, onBack: () -> Unit, onMessage: (String) -> Unit, onReport: (Pair<String, String>) -> Unit, onBlock: (String) -> Unit) {
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

@Composable
private fun MyCommunityProfile(repo: FirebaseCommunityRepository, onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<PawlyUser?>(null) }
    var dogs by remember { mutableStateOf(emptyList<PawlyDog>()) }
    var blockedUsers by remember { mutableStateOf(emptyList<PawlyBlockedUser>()) }
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { avatarUri = it }
    var editDog by remember { mutableStateOf<PawlyDog?>(null) }
    var showDogEditor by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { profile = repo.getMyProfile(); dogs = repo.getMyDogs(); name = profile?.displayName.orEmpty(); bio = profile?.bio.orEmpty(); isPublic = profile?.isPublic ?: true }
            .onFailure { error = it.localizedMessage }
    }
    DisposableEffect(repo) {
        val listener = repo.listenBlockedUsers({ blockedUsers = it }, { error = it })
        onDispose { listener.remove() }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(color = Paper, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(profile?.photoUrl.orEmpty(), name, 58)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(name.ifBlank { "Tu perfil" }, color = Forest, style = MaterialTheme.typography.titleLarge)
                            Text("@" + profile?.username.orEmpty(), color = Muted)
                            Text((profile?.followersCount ?: 0).toString() + " seguidores · " + (profile?.followingCount ?: 0) + " siguiendo", color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(onClick = { avatarPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PhotoCamera, null)
                        Text(if (avatarUri != null) "Fotografía seleccionada" else "Cambiar foto de perfil")
                    }
                    OutlinedTextField(name, { name = it }, label = { Text("Nombre público") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(bio, { bio = it }, label = { Text("Presentación") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Perfil público", fontWeight = FontWeight.SemiBold); Text("Permite que te encuentren.", color = Muted, style = MaterialTheme.typography.bodySmall) }
                        Switch(isPublic, { isPublic = it })
                    }
                    Button(onClick = {
                        busy = true
                        scope.launch { runCatching {
                            repo.updateProfile(name, bio, isPublic)
                            if (avatarUri != null) {
                                val url = repo.updateProfilePhoto(avatarUri!!)
                                profile = profile?.copy(photoUrl = url)
                                avatarUri = null
                            }
                            profile = profile?.copy(displayName = name, bio = bio, isPublic = isPublic)
                            status = "Perfil actualizado."
                        }.onFailure { error = it.localizedMessage }; busy = false }
                    }, enabled = !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Guardando…" else "Guardar perfil") }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mis perros", color = Forest, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { editDog = null; showDogEditor = true }) { Icon(Icons.Default.Add, null); Text("Añadir") }
            }
        }
        items(dogs, key = { it.id }) { dog -> DogProfileCard(dog) { editDog = dog; showDogEditor = true } }
        item { Text("Cuentas bloqueadas", color = Forest, style = MaterialTheme.typography.titleMedium) }
        if (blockedUsers.isEmpty()) item { Text("No has bloqueado a nadie.", color = Muted, style = MaterialTheme.typography.bodySmall) }
        items(blockedUsers, key = { it.uid }) { blocked ->
            Surface(color = Paper, shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(blocked.displayName, color = Ink, fontWeight = FontWeight.SemiBold)
                        if (blocked.username.isNotBlank()) Text("@" + blocked.username, color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = {
                        scope.launch { runCatching { repo.unblock(blocked.uid); status = "Cuenta desbloqueada." }.onFailure { error = it.localizedMessage } }
                    }) { Text("Desbloquear") }
                }
            }
        }
        if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        if (status != null) item { Text(status!!, color = ForestLight) }
        item { OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Logout, null); Text("Cerrar sesión") } }
        item { Text("Comparte solo la información que quieras hacer pública. Evita direcciones y datos sensibles.", color = Muted, style = MaterialTheme.typography.labelSmall) }
    }
    if (showDogEditor) DogEditorDialog(repo, editDog, { showDogEditor = false }) {
        showDogEditor = false
        scope.launch { runCatching { dogs = repo.getMyDogs() }.onFailure { error = it.localizedMessage } }
    }
}

@Composable
private fun DogProfileCard(dog: PawlyDog, onClick: (() -> Unit)? = null) {
    Surface(color = Paper, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dog.photoUrl.isNotBlank()) AsyncImage(dog.photoUrl, "Foto de " + dog.name, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
            else Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Lime), contentAlignment = Alignment.Center) { Text("🐕", fontSize = 30.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(dog.name, color = Forest, fontWeight = FontWeight.Bold)
                Text(listOf(dog.breed, dog.age).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, style = MaterialTheme.typography.bodySmall)
                if (dog.description.isNotBlank()) Text(dog.description, color = Ink, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                if (dog.traits.isNotEmpty()) Text(dog.traits.joinToString(" · "), color = ForestLight, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun DogEditorDialog(repo: FirebaseCommunityRepository, existing: PawlyDog?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var breed by remember { mutableStateOf(existing?.breed.orEmpty()) }
    var age by remember { mutableStateOf(existing?.age.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var traits by remember { mutableStateOf(existing?.traits?.joinToString(", ").orEmpty()) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var photoUrl by remember { mutableStateOf(existing?.photoUrl.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri = it }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "Añadir perro" else "Editar perro") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Photo, null); Text(if (uri != null || photoUrl.isNotBlank()) "Cambiar foto" else "Añadir foto") }
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") })
            OutlinedTextField(breed, { breed = it }, label = { Text("Raza") })
            OutlinedTextField(age, { age = it }, label = { Text("Edad") })
            OutlinedTextField(description, { description = it }, label = { Text("Descripción") }, minLines = 2)
            OutlinedTextField(traits, { traits = it }, label = { Text("Características separadas por comas") })
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = {
        TextButton(enabled = !busy && name.isNotBlank(), onClick = {
            busy = true
            scope.launch {
                try {
                    if (uri != null) photoUrl = repo.uploadImage(uri!!, "dog-photos")
                    repo.saveDog(PawlyDog(existing?.id ?: "", name, breed, age, description, traits.split(",").map { it.trim() }.filter { it.isNotBlank() }, photoUrl))
                    onSaved()
                } catch (e: Exception) { error = e.localizedMessage ?: "No se pudo guardar." }
                finally { busy = false }
            }
        }) { Text(if (busy) "Guardando…" else "Guardar") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun CreatePostDialog(repo: FirebaseCommunityRepository, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var dog by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri = it }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Compartir una aventura") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(text, { text = it }, label = { Text("¿Qué queréis contar?") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            OutlinedTextField(dog, { dog = it }, label = { Text("Nombre del perro (opcional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AddAPhoto, null); Text(if (uri == null) "Añadir fotografía" else "Foto seleccionada") }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = {
        TextButton(enabled = !busy && (text.isNotBlank() || uri != null), onClick = {
            busy = true
            scope.launch {
                try {
                    val image = uri?.let { repo.uploadImage(it, "post-photos") }.orEmpty()
                    repo.publishPost(text, image, dog)
                    onDone()
                } catch (e: Exception) { error = e.localizedMessage ?: "No se pudo publicar." }
                finally { busy = false }
            }
        }) { Text(if (busy) "Publicando…" else "Publicar") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun CommentsDialog(repo: FirebaseCommunityRepository, post: PawlyPost, onDismiss: () -> Unit) {
    var comments by remember(post.id) { mutableStateOf(emptyList<PawlyComment>()) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(post.id) {
        val listener = repo.listenComments(post.id, { comments = it }, { error = it })
        onDispose { listener.remove() }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Comentarios") }, text = {
        Column(Modifier.heightIn(max = 420.dp)) {
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(comments, key = { it.id }) { comment ->
                    Column {
                        Text(comment.authorName, color = Forest, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Text(comment.text, color = Ink)
                    }
                }
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            OutlinedTextField(draft, { draft = it }, label = { Text("Escribe un comentario") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { scope.launch { runCatching { repo.addComment(post.id, draft); draft = "" }.onFailure { error = it.localizedMessage } } }, enabled = draft.isNotBlank(), modifier = Modifier.align(Alignment.End)) { Text("Enviar") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

@Composable
private fun ConversationsScreen(repo: FirebaseCommunityRepository, onOpen: (PawlyConversation) -> Unit) {
    var conversations by remember { mutableStateOf(emptyList<PawlyConversation>()) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(repo) {
        val listener = repo.listenConversations({ conversations = it }, { error = it })
        onDispose { listener.remove() }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (conversations.isEmpty()) item { EmptyCommunityCard("Tu bandeja está vacía", "Cuando escribas a otra persona, la conversación aparecerá aquí.") }
        if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        items(conversations, key = { it.id }) { c ->
            Surface(onClick = { onOpen(c) }, color = Paper, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar("", c.otherUserName)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.otherUserName, color = Forest, fontWeight = FontWeight.Bold)
                        Text(c.lastMessage.ifBlank { "Empezad a hablar" }, color = Muted, maxLines = 1)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Forest)
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(repo: FirebaseCommunityRepository, conversation: PawlyConversation, onBack: () -> Unit) {
    val uid = repo.currentUid.orEmpty()
    var messages by remember(conversation.id) { mutableStateOf(emptyList<PawlyMessage>()) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(conversation.id) {
        val listener = repo.listenMessages(conversation.id, { messages = it }, { error = it })
        onDispose { listener.remove() }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        TextButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null); Text(conversation.otherUserName.ifBlank { "Conversación" }) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(messages, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.senderId == uid) Arrangement.End else Arrangement.Start) {
                    Surface(color = if (item.senderId == uid) Forest else Paper, shape = RoundedCornerShape(15.dp)) {
                        Text(item.text, color = if (item.senderId == uid) Color.White else Ink, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(draft, { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Escribe un mensaje…") }, maxLines = 3)
            IconButton(onClick = { val text = draft; scope.launch { runCatching { repo.sendMessage(conversation.id, text); draft = "" }.onFailure { error = it.localizedMessage } } }, enabled = draft.isNotBlank()) { Icon(Icons.Default.Send, "Enviar", tint = Forest) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ModerationScreen(repo: FirebaseCommunityRepository) {
    var reports by remember { mutableStateOf(emptyList<PawlyReport>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(repo) {
        val listener = repo.listenReports({ reports = it }, { error = it })
        onDispose { listener.remove() }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Centro de moderación", color = Forest, style = MaterialTheme.typography.titleLarge)
            Text("Los avisos quedan registrados para revisión del equipo.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        if (status != null) item { Text(status!!, color = ForestLight) }
        if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        if (reports.isEmpty()) item { EmptyCommunityCard("Sin denuncias pendientes", "Cuando alguien envíe un aviso, aparecerá aquí.") }
        items(reports, key = { it.id }) { report ->
            Surface(color = Paper, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(report.targetType.uppercase(), color = Forest, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(report.status, color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("Motivo: " + report.reason, color = Ink)
                    Text("Elemento: " + report.targetId, color = Muted, style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (report.targetType == "post") {
                            OutlinedButton(onClick = {
                                scope.launch { runCatching { repo.removeReportedPost(report.targetId); repo.markReportReviewed(report.id); status = "Publicación eliminada y aviso revisado." }.onFailure { error = it.localizedMessage } }
                            }) { Icon(Icons.Default.DeleteOutline, null); Text("Eliminar publicación") }
                        }
                        if (report.targetType == "user") {
                            OutlinedButton(onClick = {
                                scope.launch { runCatching { repo.hideReportedUser(report.targetId); repo.markReportReviewed(report.id); status = "Perfil restringido y aviso revisado." }.onFailure { error = it.localizedMessage } }
                            }) { Icon(Icons.Default.VisibilityOff, null); Text("Restringir perfil") }
                        }
                        TextButton(onClick = {
                            scope.launch { runCatching { repo.markReportReviewed(report.id); status = "Aviso marcado como revisado." }.onFailure { error = it.localizedMessage } }
                        }) { Text("Marcar revisado") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportDialog(repo: FirebaseCommunityRepository, targetId: String, type: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("Contenido inapropiado") }
    var busy by remember { mutableStateOf(false) }
    val reasons = listOf("Contenido inapropiado", "Acoso o intimidación", "Spam o fraude", "Bienestar animal", "Suplantación de identidad", "Otro")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Denunciar") }, text = {
        Column {
            Text("Selecciona el motivo del aviso.", color = Muted)
            reasons.forEach { item -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(reason == item, { reason = item }); Text(item) } }
        }
    }, confirmButton = {
        TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch { runCatching { repo.report(targetId, type, reason); onDone() }.onFailure { busy = false } }
        }) { Text(if (busy) "Enviando…" else "Enviar denuncia") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun BlockConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bloquear esta cuenta") },
        text = { Text("Dejarás de ver sus publicaciones en tu muro y no podréis iniciar ni continuar conversaciones. Puedes desbloquearla más adelante desde la sección «Cuentas bloqueadas» de tu perfil.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Bloquear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun Avatar(url: String, label: String, size: Int = 44) {
    if (url.isNotBlank()) AsyncImage(url, "Foto de " + label, Modifier.size(size.dp).clip(CircleShape))
    else Box(Modifier.size(size.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
        Text(label.trim().firstOrNull()?.uppercase() ?: "🐾", color = Forest, fontWeight = FontWeight.Bold, fontSize = (size / 2).sp)
    }
}

@Composable
private fun EmptyCommunityCard(title: String, subtitle: String) {
    Surface(color = Paper, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🐾", fontSize = 32.sp)
            Text(title, color = Forest, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
