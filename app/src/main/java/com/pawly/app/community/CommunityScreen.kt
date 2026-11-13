package com.pawly.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.net.Uri
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class CommunityPage(val label: String) {
    FEED("Muro"), SEARCH("Personas"), EVENTS("Eventos"), MAP("Mapa"), MESSAGES("Mensajes"), NOTIFICATIONS("Avisos"), PROFILE("Mi perfil"), MODERATION("Moderar")
}

@Composable
internal fun CommunityScreen(elegantDesign: Boolean = false) {
    val context = LocalContext.current
    val repository = remember(context) { FirebaseCommunityRepository.create(context) }
    if (repository == null) {
        FirebaseSetupPanel(onContinueOffline = {})
        return
    }
    var uid by remember { mutableStateOf(repository.currentUid) }
    if (uid.isNullOrBlank()) CommunityAuthPanel(repository, onAuthenticated = { uid = repository.currentUid }, onContinueOffline = {})
    else CommunityHub(repository, uid!!, elegantDesign)
}


@Composable
internal fun FirebaseSetupPanel(onContinueOffline: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tr("🐾"), fontSize = 54.sp)
        Text(tr("La comunidad Pawly"), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(tr("Conecta con personas que comparten su vida con perros. Para activar cuentas, sincronización y fotografías hay que vincular el proyecto Firebase."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Servicios que se activarán"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(tr("• Cuentas con correo y contraseña"))
                Text(tr("• Perfiles públicos y perfiles de perros"))
                Text(tr("• Muro, likes, comentarios y seguidores"))
                Text(tr("• Mensajes privados en tiempo real"))
                Text(tr("• Fotografías optimizadas con Cloudinary"))
                Text(tr("Añade app/google-services.json siguiendo docs/FIREBASE_SETUP.md."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onContinueOffline, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Continuar en modo local"))
        }
    }
}


@Composable
internal fun CommunityAuthPanel(repo: FirebaseCommunityRepository, onAuthenticated: () -> Unit, onContinueOffline: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val webClientId = remember(context) {
        val resourceId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resourceId != 0) resources.getString(resourceId) else ""
    }
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(24.dp),
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
                Text(tr("PAWLY COMMUNITY"), color = Color.White.copy(alpha = .82f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                Text(tr("Una comunidad para compartir más vida juntos."), color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(tr("TU COMUNIDAD PERRUNA"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))
        Text(if (register) "Crea tu cuenta" else "Qué alegría verte", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(tr("Comparte aventuras, descubre perros y conoce a sus personas."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        if (register) OutlinedTextField(username, { username = it }, label = { Text(tr("Nombre de usuario")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            email, { email = it }, label = { Text(tr("Correo electrónico")) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            password, { password = it }, label = { Text(tr("Contraseña (mín. 6 caracteres)")) },
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
                                // Este botón explícito utiliza el flujo Sign in with Google.
                                // GetGoogleIdOption está pensado para la hoja de credenciales y
                                // puede devolver NoCredentialException si el dispositivo no tiene
                                // cuentas autorizadas o el proveedor no puede ofrecerlas.
                                val googleOption = GetSignInWithGoogleOption.Builder(webClientId)
                                    .build()
                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleOption)
                                    .build()
                                // If Google tries to reauthenticate a stale cached account, clear
                                // Credential Manager state and retry the explicit sign-in flow once.
                                val result = try {
                                    credentialManager.getCredential(context, request)
                                } catch (firstError: androidx.credentials.exceptions.GetCredentialCancellationException) {
                                    val detail = firstError.localizedMessage.orEmpty()
                                    if (detail.contains("Account reauth failed", ignoreCase = true)) {
                                        Log.i("PawlyGoogleSignIn", "Cached Google account session needs reauthentication; clearing provider state and opening the account chooser.")
                                        runCatching {
                                            credentialManager.clearCredentialState(ClearCredentialStateRequest())
                                        }.onFailure {
                                            Log.w("PawlyGoogleSignIn", "Could not clear the cached Google credential state.", it)
                                        }
                                        val chooserOption = GetGoogleIdOption.Builder()
                                            .setServerClientId(webClientId)
                                            .setFilterByAuthorizedAccounts(false)
                                            .setAutoSelectEnabled(false)
                                            .build()
                                        val chooserRequest = GetCredentialRequest.Builder()
                                            .addCredentialOption(chooserOption)
                                            .build()
                                        credentialManager.getCredential(context, chooserRequest)
                                    } else {
                                        throw firstError
                                    }
                                } catch (noCredential: androidx.credentials.exceptions.NoCredentialException) {
                                    // The explicit button flow can have no eligible credential even when
                                    // a Google account exists on the device. Retry with the account
                                    // chooser and include accounts not yet authorized for this app.
                                    Log.i("PawlyGoogleSignIn", "No credential from explicit Google sign-in; retrying with account chooser.", noCredential)
                                    val chooserOption = GetGoogleIdOption.Builder()
                                        .setServerClientId(webClientId)
                                        .setFilterByAuthorizedAccounts(false)
                                        .setAutoSelectEnabled(false)
                                        .build()
                                    val chooserRequest = GetCredentialRequest.Builder()
                                        .addCredentialOption(chooserOption)
                                        .build()
                                    credentialManager.getCredential(context, chooserRequest)
                                }
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
                                val exceptionName = e.javaClass.simpleName
                                val exceptionMessage = e.localizedMessage.orEmpty()
                                val accountReauthFailed = exceptionMessage.contains("Account reauth failed", ignoreCase = true)
                                val cancelled = exceptionName.contains("Cancellation", ignoreCase = true) ||
                                    exceptionMessage.contains("cancelled by the user", ignoreCase = true)
                                val noCredential = exceptionName.contains("NoCredential", ignoreCase = true) ||
                                    exceptionMessage.contains("no credentials available", ignoreCase = true)

                                // Keep the provider's real exception in Logcat. Credential Manager can
                                // surface several different cancellation/provider failures through
                                // similar UI messages; the exact class and cause are essential here.
                                Log.e("PawlyGoogleSignIn", "Google credential flow failed: ${e.javaClass.name}: $exceptionMessage", e)

                                error = when {
                                    accountReauthFailed ->
                                        "Google necesita volver a validar la sesión guardada y no pudo hacerlo. He limpiado la sesión del proveedor e intentado abrir el selector de cuentas. Si sigue fallando, actualiza Google Play services y quita y vuelve a añadir la cuenta desde Ajustes > Contraseñas y cuentas."
                                    noCredential ->
                                        "Credential Manager no pudo ofrecer una cuenta de Google. Comprueba en Ajustes > Contraseñas y cuentas que la cuenta esté añadida al dispositivo, y que Google Play services esté actualizado. Si el selector sí muestra la cuenta pero el acceso falla, revisa en Firebase la SHA-1 de esta variante y descarga de nuevo app/google-services.json con el cliente OAuth Web actualizado."
                                    cancelled ->
                                        "Google ha interrumpido el acceso ( ${e.javaClass.simpleName} ). Detalle: ${exceptionMessage.ifBlank { "sin mensaje" }}.\n\nSi has elegido una cuenta y vuelve a esta pantalla, envíame el error completo de Logcat filtrando por PawlyGoogleSignIn."
                                    else ->
                                        e.toPawlyMessage("No se pudo iniciar sesión con Google.") +
                                            "\n\nError técnico: ${e.javaClass.simpleName}: ${exceptionMessage}\n\nComprueba que Google está activado en Firebase Authentication, que la SHA-1 de esta compilación está registrada y que app/google-services.json contiene el cliente OAuth Web actualizado."
                                }
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
                Text(tr(if (register) "Crear cuenta con Google" else "Continuar con Google"))
            }
        if (status != null) Text(status!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
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
        }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(tr("He olvidado mi contraseña"), color = MaterialTheme.colorScheme.primary) }
        TextButton(onClick = { register = !register; error = null; status = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (register) "Ya tengo una cuenta · Iniciar sesión" else "¿Primera vez en Pawly? Crear cuenta", color = MaterialTheme.colorScheme.primary)
        }
        OutlinedButton(onClick = onContinueOffline, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
            Text(tr("Continuar sin cuenta (modo local)"))
        }
        Text(tr("Respeta la privacidad de otras personas y no compartas datos personales sensibles."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommunityHub(repo: FirebaseCommunityRepository, uid: String, elegantDesign: Boolean = false) {
    var page by remember { mutableStateOf(CommunityPage.FEED) }
    var posts by remember { mutableStateOf(emptyList<PawlyPost>()) }
    var suggestedUsers by remember { mutableStateOf(emptyList<PawlyUser>()) }
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
    LaunchedEffect(repo, uid) {
        suggestedUsers = runCatching { repo.getSuggestedUsers() }.getOrDefault(emptyList())
        admin = runCatching { repo.isAdmin() }.getOrDefault(false)
        runCatching { repo.syncMessagingToken() }
            .onFailure { notice = it.localizedMessage ?: "No se pudo registrar este dispositivo para notificaciones." }
    }
    DisposableEffect(repo, uid) {
        val feedListener = repo.listenFeed({ posts = it; error = null }, { error = it })
        val followingListener = repo.listenFollowingIds({ followingIds = it }, { error = it })
        onDispose { feedListener.remove(); followingListener.remove() }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (elegantDesign) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).height(190.dp).clip(RoundedCornerShape(26.dp))) {
                AsyncImage(
                    model = "https://images.unsplash.com/photo-1516734212186-a967f81ad0d7?w=1200&h=700&fit=crop",
                    contentDescription = "Perros jugando juntos",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE610251F)))))
                Row(Modifier.fillMaxSize().padding(start = 19.dp, end = 8.dp, top = 15.dp, bottom = 15.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(tr("PAWLY COMMUNITY"), color = Color.White.copy(alpha = .84f), fontWeight = FontWeight.ExtraBold, letterSpacing = 1.7.sp, fontSize = 9.sp)
                        Text(tr("Comunidad"), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                        Text(tr("Aventuras que se comparten."), color = Color.White.copy(alpha = .86f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 19.dp, end = 8.dp, top = 15.dp, bottom = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(tr("PAWLY COMMUNITY"), color = Lime, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.7.sp, fontSize = 9.sp)
                    Text(tr("Comunidad"), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text(tr("Aventuras que se comparten."), color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CommunityPage.entries.filter { it != CommunityPage.MODERATION || admin }.forEach { item ->
                FilterChip(
                    selected = page == item,
                    onClick = { page = item; selectedUser = null; chat = null },
                    label = { Text(tr(item.label), maxLines = 1) },
                    shape = RoundedCornerShape(50)
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAllowed) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("No te pierdas los avisos de Pawly"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(tr("Activa las notificaciones para recibir avisos cuando estén disponibles."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text(tr("Activar"))
                    }
                }
            }
        }
        if (!emailVerified) {
            Surface(color = Lime.copy(alpha = .45f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text(tr("Verifica tu correo para proteger tu cuenta."), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = {
                            scope.launch { runCatching { repo.resendVerificationEmail(); notice = "Correo de verificación enviado." }.onFailure { notice = it.localizedMessage } }
                        }) { Text(tr("Reenviar correo")) }
                        TextButton(onClick = {
                            scope.launch { runCatching { repo.refreshAuthentication(); emailVerified = repo.isEmailVerified }.onFailure { notice = it.localizedMessage } }
                        }) { Text(tr("Ya lo he verificado")) }
                    }
                }
            }
        }
        if (notice != null) Text(notice!!, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
        when {
            selectedUser != null -> PublicUserScreen(repo, selectedUser!!, onBack = { selectedUser = null }, onMessage = { id ->
                scope.launch {
                    runCatching {
                        val conversationId = repo.startConversation(id)
                        chat = PawlyConversation(id = conversationId, otherUserId = id)
                        page = CommunityPage.MESSAGES
                        selectedUser = null
                        notice = null
                    }.onFailure { failure ->
                        Log.e("PawlyCommunity", "Start conversation failed", failure)
                        notice = failure.toPawlyMessage("No se pudo iniciar la conversación.")
                    }
                }
            }, onReport = { reportTarget = it }, onBlock = { id -> blockTarget = id })
            chat != null -> ChatScreen(repo, chat!!, onBack = { chat = null })
            page == CommunityPage.FEED -> Column(Modifier.fillMaxSize()) {
                if (suggestedUsers.isNotEmpty()) {
                    SuggestedProfilesRow(
                        users = suggestedUsers,
                        onOpen = { selectedUser = it },
                        onFollow = { user ->
                            scope.launch {
                                runCatching { repo.follow(user.uid, true) }
                                    .onSuccess { suggestedUsers = suggestedUsers.filterNot { it.uid == user.uid }; notice = "Ahora sigues a esta persona." }
                                    .onFailure { notice = it.localizedMessage ?: "No se pudo seguir a esta persona." }
                            }
                        }
                    )
                }
                Box(Modifier.weight(1f)) {
                    FeedScreen(posts, followingIds, error, onCreate = { createPost = true }, onComments = { commentsPost = it }, onOpenUser = { selectedUser = it }, onLike = { post ->
                        scope.launch { runCatching { repo.toggleLike(post.id) }.onFailure { notice = it.localizedMessage } }
                    }, onReport = { reportTarget = it }, onBlock = { id -> blockTarget = id })
                }
            }
            page == CommunityPage.SEARCH -> PeopleSearchScreen(repo) { selectedUser = it }
            page == CommunityPage.EVENTS -> PawlyEventsScreen(repository = repo, elegantDesign = elegantDesign)
            page == CommunityPage.MAP -> PawlyEventsScreen(repository = repo, mapOnly = true, elegantDesign = elegantDesign)
            page == CommunityPage.MESSAGES -> ConversationsScreen(repo) { chat = it }
            page == CommunityPage.NOTIFICATIONS -> NotificationsScreen(repo, onOpenProfile = { selectedUser = it })
            page == CommunityPage.PROFILE -> MyCommunityProfile(repo)
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
private fun SuggestedProfilesRow(
    users: List<PawlyUser>,
    onOpen: (String) -> Unit,
    onFollow: (PawlyUser) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 8.dp)) {
        Column {
            Text(tr("Personas que podrías conocer"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(tr("Más vidas perrunas para descubrir."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            users.forEach { user ->
                Surface(modifier = Modifier.width(174.dp).clickable { onOpen(user.uid) }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (user.photoUrl.isNotBlank()) AsyncImage(model = user.photoUrl, contentDescription = null, modifier = Modifier.size(54.dp).clip(CircleShape))
                        else Surface(modifier = Modifier.size(54.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(contentAlignment = Alignment.Center) { Text(user.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                        }
                        Text(user.displayName.ifBlank { user.username }, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("@${user.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        if (user.accountType == "professional") Text(tr("Perfil profesional"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Button(onClick = { onFollow(user) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Seguir")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Bloquear esta cuenta")) },
        text = { Text(tr("Dejarás de ver sus publicaciones en tu muro y no podréis iniciar ni continuar conversaciones. Puedes desbloquearla más adelante desde la sección «Cuentas bloqueadas» de tu perfil.")) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(tr("Bloquear")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar")) } }
    )
}



@Composable
private fun NotificationsScreen(repo: FirebaseCommunityRepository, onOpenProfile: (String) -> Unit) {
    var notifications by remember { mutableStateOf(emptyList<PawlyNotification>()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(repo) {
        val listener = repo.listenNotifications({ notifications = it; error = null }, { error = it })
        onDispose { listener.remove() }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Actividad"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(tr("Likes, comentarios y nuevos seguidores."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = {
                scope.launch { runCatching { repo.markAllNotificationsRead() }.onFailure { error = it.localizedMessage } }
            }, enabled = notifications.any { !it.read }) { Text(tr("Marcar todo leído")) }
        }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        if (notifications.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(tr("Todavía no tienes notificaciones."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(notifications, key = { it.id }) { item ->
                    Surface(
                        color = if (item.read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = .42f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            scope.launch { runCatching { repo.markNotificationRead(item.id) } }
                            if (item.actorId.isNotBlank()) onOpenProfile(item.actorId)
                        }
                    ) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when (item.type) {
                                    "LIKE" -> Icons.Default.Favorite
                                    "COMMENT" -> Icons.Default.ChatBubbleOutline
                                    "FOLLOW" -> Icons.Default.PersonAdd
                                    else -> Icons.Default.Notifications
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(25.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.actorName, fontWeight = FontWeight.Bold)
                                Text(item.message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (item.createdAt > 0) java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.createdAt)) else tr("Ahora"),
                                    color = MaterialTheme.colorScheme.outline,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (!item.read) Surface(color = MaterialTheme.colorScheme.error, shape = CircleShape, modifier = Modifier.size(9.dp)) {}
                        }
                    }
                }
            }
        }
    }
}
