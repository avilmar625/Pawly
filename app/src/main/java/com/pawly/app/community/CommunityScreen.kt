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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
        FirebaseSetupPanel(onContinueOffline = {})
        return
    }
    var uid by remember { mutableStateOf(repository.currentUid) }
    if (uid.isNullOrBlank()) CommunityAuthPanel(repository, onAuthenticated = { uid = repository.currentUid }, onContinueOffline = {})
    else CommunityHub(repository, uid!!) { uid = repository.currentUid }
}


@Composable
internal fun FirebaseSetupPanel(onContinueOffline: () -> Unit) {
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
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onContinueOffline, modifier = Modifier.fillMaxWidth()) {
            Text("Continuar en modo local")
        }
    }
}


@Composable
internal fun CommunityAuthPanel(repo: FirebaseCommunityRepository, onAuthenticated: () -> Unit, onContinueOffline: () -> Unit) {
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
    Column(
        Modifier.fillMaxSize().background(Cream).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(28.dp))) {
            AsyncImage(
                model = "https://images.unsplash.com/photo-1543466835-00a7907e9de1?w=1200&h=700&fit=crop",
                contentDescription = "Perro disfrutando al aire libre",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD910251F)))))
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Text("PAWLY COMMUNITY", color = Color.White.copy(alpha = .82f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                Text("Una comunidad para compartir más vida juntos.", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("TU COMUNIDAD PERRUNA", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))
        Text(if (register) "Crea tu cuenta" else "Qué alegría verte", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
        Text("Comparte aventuras, descubre perros y conoce a sus personas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        if (register) OutlinedTextField(username, { username = it }, label = { Text("Nombre de usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            email, { email = it }, label = { Text("Correo electrónico") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            password, { password = it }, label = { Text("Contraseña (mín. 6 caracteres)") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        Button(onClick = {
            busy = true; error = null
            scope.launch {
                try {
                    val configuredRepo = repo
                    if (configuredRepo == null) {
                        error = "La pantalla está disponible, pero para iniciar sesión o crear una cuenta debes configurar Firebase en esta instalación. Añade app/google-services.json y activa Authentication y Firestore."
                    } else {
                        if (register) configuredRepo.register(email, password, username) else configuredRepo.signIn(email, password)
                        onAuthenticated()
                    }
                } catch (e: Exception) { error = e.toPawlyMessage("No se pudo completar la operación.") }
                finally { busy = false }
            }
        }, enabled = !busy && email.contains("@") && password.length >= 6 && (!register || username.length >= 3), modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
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
                                    val configuredRepo = repo
                                    if (configuredRepo == null) {
                                        error = "Configura Firebase en esta instalación para poder acceder con Google."
                                    } else {
                                        configuredRepo.signInWithGoogle(googleToken)
                                        onAuthenticated()
                                    }
                                }
                            } catch (e: Exception) {
                                error = e.toPawlyMessage("No se pudo iniciar sesión con Google.")
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)
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
                runCatching {
                    val configuredRepo = repo ?: error("Configura Firebase en esta instalación para recuperar la contraseña.")
                    configuredRepo.sendPasswordReset(email)
                    status = "Te hemos enviado un enlace para restablecer la contraseña."
                }
                    .onFailure { error = it.toPawlyMessage("No se pudo enviar el enlace de recuperación.") }
            }
        }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("He olvidado mi contraseña", color = MaterialTheme.colorScheme.primary) }
        TextButton(onClick = { register = !register; error = null; status = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (register) "Ya tengo una cuenta · Iniciar sesión" else "¿Primera vez en Pawly? Crear cuenta", color = MaterialTheme.colorScheme.primary)
        }
        OutlinedButton(onClick = onContinueOffline, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
            Text("Continuar sin cuenta (modo local)")
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
    val context = LocalContext.current
    var notificationsAllowed by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsAllowed = granted
        notice = if (granted) "Notificaciones activadas." else "Puedes activarlas más adelante desde los ajustes de Android."
    }
    val signOut: () -> Unit = {
        scope.launch {
            runCatching { repo.signOut() }
                .onFailure { notice = it.localizedMessage ?: "No se pudo cerrar la sesión correctamente." }
            onSignOut()
        }
    }
    LaunchedEffect(repo, uid) {
        admin = runCatching { repo.isAdmin() }.getOrDefault(false)
        runCatching { repo.syncMessagingToken() }
            .onFailure { notice = it.localizedMessage ?: "No se pudo registrar este dispositivo para notificaciones." }
    }
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
            IconButton(onClick = signOut) { Icon(Icons.Default.Logout, "Cerrar sesión", tint = Forest) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 7.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            CommunityPage.entries.filter { it != CommunityPage.MODERATION || admin }.forEach { item ->
                FilterChip(selected = page == item, onClick = { page = item; selectedUser = null; chat = null }, label = { Text(item.label, maxLines = 1) })
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAllowed) {
            Surface(color = Paper, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("No te pierdas los avisos de Pawly", color = Forest, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("Activa las notificaciones para recibir avisos cuando estén disponibles.", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text("Activar")
                    }
                }
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
            page == CommunityPage.PROFILE -> MyCommunityProfile(repo, signOut)
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
private fun BlockConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bloquear esta cuenta") },
        text = { Text("Dejarás de ver sus publicaciones en tu muro y no podréis iniciar ni continuar conversaciones. Puedes desbloquearla más adelante desde la sección «Cuentas bloqueadas» de tu perfil.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Bloquear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

