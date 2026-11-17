package com.pawly.app

import android.Manifest
import android.app.Activity
import android.telephony.PhoneNumberUtils
import com.google.firebase.pnv.FirebasePhoneNumberVerification
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
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf(emptyList<PawlyUser>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var inviteNotice by remember { mutableStateOf<String?>(null) }
    var contactSelected by remember { mutableStateOf(false) }
    var selectedContactName by remember { mutableStateOf("") }
    var selectedPhone by remember { mutableStateOf<String?>(null) }
    var phoneStatus by remember { mutableStateOf("Comprobando la configuración de contactos…") }
    var phoneVerified by remember { mutableStateOf(false) }
    var phoneDiscoveryEnabled by remember { mutableStateOf(false) }
    var phoneTestNumber by remember { mutableStateOf(false) }
    var phoneBusy by remember { mutableStateOf(false) }
    var phoneMatches by remember { mutableStateOf(emptyList<PawlyContactMatch>()) }
    var phoneSearchError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repo) {
        runCatching { repo.loadPhoneDiscoveryStatus() }
            .onSuccess {
                phoneVerified = it.verified
                phoneDiscoveryEnabled = it.enabled
                phoneTestNumber = it.testNumber
                phoneStatus = if (it.verified) "Tu número está verificado." else "Verifica tu propio número para activar la búsqueda por teléfono."
            }
            .onFailure {
                phoneStatus = it.localizedMessage ?: "El servicio seguro de contactos no está configurado todavía."
            }
    }

    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { contactUri ->
        if (contactUri != null) {
            val contactId = contactUri.lastPathSegment
            val contactName = runCatching {
                context.contentResolver.query(contactUri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull().orEmpty()
            val rawPhone = runCatching {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC"
                )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull().orEmpty()
            val country = java.util.Locale.getDefault().country.ifBlank { "ES" }
            val normalized = runCatching { PhoneNumberUtils.formatNumberToE164(rawPhone, country) }.getOrNull()
            selectedContactName = contactName
            selectedPhone = normalized
            phoneMatches = emptyList()
            phoneSearchError = null
            if (contactName.isNotBlank()) {
                query = contactName
                contactSelected = true
                inviteNotice = "Contacto seleccionado. La agenda no se sube a Pawly."
            }
            if (normalized.isNullOrBlank()) {
                phoneStatus = "No se ha podido normalizar el teléfono del contacto. Comprueba que incluya el prefijo del país."
            }
        }
    }

    LaunchedEffect(query) {
        if (query.trim().length < 2) { users = emptyList(); return@LaunchedEffect }
        delay(350); loading = true
        try { users = repo.searchUsers(query); error = null } catch (e: Exception) { error = e.localizedMessage } finally { loading = false }
    }

    fun requestPhoneVerification() {
        if (!BuildConfig.DEBUG && !BuildConfig.PHONE_VERIFICATION_PRODUCTION_ENABLED) {
            phoneStatus = "La verificación real de números tiene coste por uso. Se mantiene desactivada para respetar el plan gratuito."
            return
        }
        if (BuildConfig.DEBUG && BuildConfig.FPNV_TEST_TOKEN.isBlank()) {
            phoneStatus = "Para probar gratis, genera un token SIM-less de Firebase PNV y añádelo como FPNV_TEST_TOKEN en local.properties."
            return
        }
        val activity = context as? Activity
        if (activity == null) {
            phoneStatus = "No se ha podido iniciar la verificación desde esta pantalla."
            return
        }
        phoneBusy = true
        FirebasePhoneNumberVerification.getInstance().getVerifiedPhoneNumber(activity)
            .addOnSuccessListener { result ->
                scope.launch {
                    runCatching { repo.verifyPhoneNumberToken(result.getToken()) }
                        .onSuccess {
                            phoneVerified = it.verified
                            phoneDiscoveryEnabled = it.enabled
                            phoneTestNumber = it.testNumber
                            phoneStatus = if (it.verified) "Número verificado. Activa la búsqueda para encontrar contactos que también hayan dado su consentimiento."
                                else "No se ha podido confirmar el número."
                        }
                        .onFailure { phoneStatus = it.localizedMessage ?: "No se pudo guardar la verificación en Pawly." }
                    phoneBusy = false
                }
            }
            .addOnFailureListener { exception ->
                phoneStatus = exception.localizedMessage ?: "La verificación telefónica se ha cancelado o no está disponible en este dispositivo."
                phoneBusy = false
            }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedButton(onClick = { contactPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Contacts, null)
            Spacer(Modifier.width(8.dp))
            Text(tr("Seleccionar contacto de mi agenda"))
        }
        Text(tr("Android abre el selector seguro de contactos. Pawly no lee ni sube tu agenda completa."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        if (inviteNotice != null) Text(inviteNotice!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        if (selectedContactName.isNotBlank()) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Contacto seleccionado: $selectedContactName", fontWeight = FontWeight.SemiBold)
                    Text(if (selectedPhone == null) "No se ha detectado un número válido." else "Número detectado: ••••••${selectedPhone!!.takeLast(3)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (!phoneVerified) {
                        OutlinedButton(onClick = { requestPhoneVerification() }, enabled = !phoneBusy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (phoneBusy) "Verificando…" else "Verificar mi propio número")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Permitir que me encuentren por teléfono", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                Text("Solo aparecerás si tú y la otra persona habéis activado esta opción.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            }
                            Switch(
                                checked = phoneDiscoveryEnabled,
                                onCheckedChange = { enabled ->
                                    phoneBusy = true
                                    scope.launch {
                                        runCatching { repo.setPhoneDiscovery(enabled) }
                                            .onSuccess { phoneDiscoveryEnabled = it.enabled; phoneStatus = if (it.enabled) "Búsqueda de contactos activada." else "Ya no podrán encontrarte por teléfono." }
                                            .onFailure { phoneSearchError = it.localizedMessage ?: "No se pudo guardar la preferencia." }
                                        phoneBusy = false
                                    }
                                },
                                enabled = !phoneBusy
                            )
                        }
                        Button(
                            onClick = {
                                val number = selectedPhone ?: return@Button
                                phoneBusy = true
                                phoneSearchError = null
                                scope.launch {
                                    runCatching { repo.findContactMatches(listOf(number)) }
                                        .onSuccess { phoneMatches = it; phoneStatus = if (it.isEmpty()) "No hay coincidencias para este contacto (o la otra persona no ha activado la búsqueda)." else "Coincidencias encontradas." }
                                        .onFailure { phoneSearchError = it.localizedMessage ?: "No se pudo buscar este contacto." }
                                    phoneBusy = false
                                }
                            },
                            enabled = !phoneBusy && phoneDiscoveryEnabled && selectedPhone != null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (phoneBusy) "Buscando…" else "Buscar este contacto en Pawly") }
                    }
                    Text(phoneStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    if (phoneTestNumber) Text("Modo de pruebas: el número devuelto por Firebase es ficticio y no se puede usar para encontrar personas reales.", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
                    if (phoneSearchError != null) Text(phoneSearchError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        phoneMatches.forEach { match ->
            Surface(onClick = { onOpenUser(match.uid) }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(match.photoUrl, match.displayName)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(match.displayName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Text("@${match.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        OutlinedTextField(query, { query = it; contactSelected = false }, modifier = Modifier.fillMaxWidth(), label = { Text(tr("Buscar personas por nombre o usuario")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        if (query.length < 2) Text(tr("Escribe al menos dos caracteres."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (contactSelected && query.length >= 2 && !loading && users.isEmpty()) {
            OutlinedButton(onClick = {
                val invite = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "¡Hola! Estoy usando Pawly, una comunidad para personas que comparten su vida con animales. ¡Únete y nos encontramos allí!")
                }
                runCatching { context.startActivity(Intent.createChooser(invite, "Invitar a $query")) }
                    .onFailure { inviteNotice = "No se pudo abrir el menú para compartir." }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PersonAddAlt1, null)
                Spacer(Modifier.width(7.dp))
                Text("No aparece en Pawly · Invitar")
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(users, key = { it.uid }) { user ->
                Surface(onClick = { onOpenUser(user.uid) }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(user.photoUrl, user.displayName)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(user.displayName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            Text("@" + user.username + " · " + user.followersCount + " seguidores", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
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
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(uid) {
        try {
            user = repo.getUser(uid)
            dogs = repo.getUserDogs(uid)
            following = repo.isFollowing(uid)
        } catch (e: Exception) { error = e.localizedMessage }
    }
    DisposableEffect(uid) {
        val listener = repo.listenUserPosts(uid, { userPosts = it }, { error = it })
        onDispose { listener.remove() }
    }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = tr("Volver"))
                }
                Text(tr("Perfil de la comunidad"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, tr("Más opciones")) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (uid != repo.currentUid) {
                            DropdownMenuItem(text = { Text(tr("Denunciar perfil")) }, onClick = { menu = false; onReport(uid to "user") })
                            DropdownMenuItem(text = { Text(tr("Bloquear usuario")) }, onClick = { menu = false; onBlock(uid) })
                        }
                    }
                }
            }
        }
        if (user == null) {
            item {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (error == null) CircularProgressIndicator()
                        Text(error ?: tr("Cargando perfil…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            val currentUser = user!!
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(112.dp)
                                .background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                                    )
                                )
                        ) {
                            Text("PAWLY · COMMUNITY", Modifier.align(Alignment.TopStart).padding(18.dp),
                                color = Color.White.copy(alpha = .88f), style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
                            Icon(Icons.Default.Pets, null, tint = Color.White.copy(alpha = .18f),
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp).size(82.dp))
                        }
                        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).offset(y = (-48).dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp,
                                    border = androidx.compose.foundation.BorderStroke(4.dp, MaterialTheme.colorScheme.surface)) {
                                    Avatar(currentUser.photoUrl, currentUser.displayName, 88)
                                }
                                Spacer(Modifier.weight(1f))
                                if (uid != repo.currentUid) {
                                    OutlinedButton(onClick = { onMessage(uid) }) {
                                        Icon(Icons.Default.ChatBubbleOutline, null)
                                        Spacer(Modifier.width(6.dp))
                                        Text(tr("Mensaje"))
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(currentUser.displayName, style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text("@${currentUser.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (currentUser.accountType == "professional") {
                                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(50)) {
                                        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.WorkspacePremium, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(5.dp))
                                            Text(tr("Profesional"), color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    currentUser.followersCount.toString() to tr("Seguidores"),
                                    currentUser.followingCount.toString() to tr("Siguiendo"),
                                    dogs.size.toString() to tr("Mascotas"),
                                    userPosts.size.toString() to tr("Publicaciones")
                                ).forEach { (value, label) ->
                                    Surface(Modifier.weight(1f), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .58f),
                                        shape = RoundedCornerShape(15.dp)) {
                                        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold,
                                                style = MaterialTheme.typography.titleMedium)
                                            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1)
                                        }
                                    }
                                }
                            }
                            if (currentUser.bio.isNotBlank()) {
                                Text(currentUser.bio, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (currentUser.accountType == "professional") {
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f),
                                    shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(currentUser.professionalCategory.ifBlank { tr("Perfil profesional") },
                                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        if (currentUser.professionalLocation.isNotBlank()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                Text(currentUser.professionalLocation, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        if (currentUser.professionalDescription.isNotBlank()) Text(currentUser.professionalDescription,
                                            color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                                        if (currentUser.professionalContactPublic && currentUser.professionalContact.isNotBlank()) {
                                            Text(tr("Contacto: ") + currentUser.professionalContact,
                                                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                            if (uid != repo.currentUid) {
                                Button(
                                    onClick = {
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
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(15.dp)
                                ) {
                                    Icon(if (following) Icons.Default.PersonRemove else Icons.Default.PersonAdd, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (busy) tr("Actualizando…") else if (following) tr("Dejar de seguir") else tr("Seguir"))
                                }
                            } else {
                                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(tr("Volver a mi comunidad")) }
                            }
                        }
                    }
                }
            }
            if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Sus compañeros"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Text(dogs.size.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            if (dogs.isEmpty()) item {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Todavía no ha añadido mascotas."), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(dogs, key = { it.id }) { dog -> DogProfileCard(dog) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Publicaciones"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Text(userPosts.size.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            if (userPosts.isEmpty()) item {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Text(tr("Todavía no ha compartido publicaciones."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(userPosts, key = { "post-" + it.id }) { post ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(22.dp),
                    tonalElevation = 1.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(currentUser.photoUrl, currentUser.displayName, 38)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(currentUser.displayName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(tr("En la comunidad Pawly"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (post.text.isNotBlank()) Text(post.text, color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 15.dp))
                        if (post.imageUrl.isNotBlank()) AsyncImage(post.imageUrl, "Fotografía de la publicación",
                            Modifier.fillMaxWidth().heightIn(max = 320.dp))
                        if (post.videoUrl.isNotBlank()) PawlyVideoPlayer(post.videoUrl,
                            Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(12.dp)))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FavoriteBorder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                            Text(" ${post.likeCount} " + tr("me gusta"), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(14.dp))
                            Icon(Icons.Default.ChatBubbleOutline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                            Text(" ${post.commentCount} " + tr("comentarios"), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
