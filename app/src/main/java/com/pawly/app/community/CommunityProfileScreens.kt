package com.pawly.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.content.Intent
import android.provider.ContactsContract
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
internal fun MyCommunityProfile(repo: FirebaseCommunityRepository) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var discoveryPhone by remember { mutableStateOf("") }
    var phoneDiscoveryEnabled by remember { mutableStateOf(false) }
    var contactMatches by remember { mutableStateOf(emptyList<PawlyContactMatch>()) }
    var contactMatchStatus by remember { mutableStateOf<String?>(null) }
    var phoneDiscoveryBusy by remember { mutableStateOf(false) }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val selectedUri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && selectedUri != null) {
            val number = runCatching {
                context.contentResolver.query(
                    selectedUri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null
                )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull()
            if (!number.isNullOrBlank()) {
                phoneDiscoveryBusy = true
                scope.launch {
                    runCatching { repo.findContactMatches(listOf(number)) }
                        .onSuccess { contactMatches = it; contactMatchStatus = if (it.isEmpty()) "No hemos encontrado una cuenta pública asociada a ese teléfono." else "Contactos de Pawly encontrados." }
                        .onFailure { contactMatchStatus = it.localizedMessage ?: "No se ha podido consultar el contacto." }
                    phoneDiscoveryBusy = false
                }
            } else contactMatchStatus = "El contacto seleccionado no tiene un teléfono disponible."
        }
    }
    var profile by remember(repo) { mutableStateOf(repo.cachedMyProfile()) }
    var dogs by remember(repo) { mutableStateOf(repo.cachedMyDogs().orEmpty()) }
    var blockedUsers by remember { mutableStateOf(emptyList<PawlyBlockedUser>()) }
    var name by remember(repo) { mutableStateOf(repo.cachedMyProfile()?.displayName.orEmpty()) }
    var bio by remember(repo) { mutableStateOf(repo.cachedMyProfile()?.bio.orEmpty()) }
    var isPublic by remember(repo) { mutableStateOf(repo.cachedMyProfile()?.isPublic ?: false) }
    var accountType by remember(repo) { mutableStateOf(repo.cachedMyProfile()?.accountType ?: "personal") }
    var professionalCategory by remember(repo) { mutableStateOf(repo.cachedMyProfile()?.professionalCategory.orEmpty()) }
    var professionalLocation by remember(repo) { mutableStateOf(repo.cachedMyProfile()?.professionalLocation.orEmpty()) }
    var professionalDescription by remember(repo) { mutableStateOf(repo.cachedMyProfile()?.professionalDescription.orEmpty()) }
    var professionalContact by remember(repo) { mutableStateOf(repo.cachedMyProfile()?.professionalContact.orEmpty()) }
    var professionalContactPublic by remember(repo) { mutableStateOf(repo.cachedMyProfile()?.professionalContactPublic ?: false) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { avatarUri = it }
    var editDog by remember { mutableStateOf<PawlyDog?>(null) }
    var showDogEditor by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmAccountDeletion by remember { mutableStateOf(false) }
    var accountDeletionSubmitted by remember { mutableStateOf(false) }
    LaunchedEffect(repo) {
        runCatching { repo.loadPhoneDiscoveryEnabled() }.onSuccess { phoneDiscoveryEnabled = it }
    }
    LaunchedEffect(repo) {
        // Paint the in-memory snapshot first; refresh both records in the background.
        repo.cachedMyProfile()?.let {
            profile = it
            name = it.displayName
            bio = it.bio
            isPublic = it.isPublic
            accountType = it.accountType
            professionalCategory = it.professionalCategory
            professionalLocation = it.professionalLocation
            professionalDescription = it.professionalDescription
            professionalContact = it.professionalContact
            professionalContactPublic = it.professionalContactPublic
        }
        repo.cachedMyDogs()?.let { dogs = it }
        runCatching {
            val freshProfile = repo.refreshMyProfile()
            val freshDogs = repo.getMyDogs(forceRefresh = true)
            profile = freshProfile
            dogs = freshDogs
            name = freshProfile.displayName
            bio = freshProfile.bio
            isPublic = freshProfile.isPublic
            accountType = freshProfile.accountType
            professionalCategory = freshProfile.professionalCategory
            professionalLocation = freshProfile.professionalLocation
            professionalDescription = freshProfile.professionalDescription
            professionalContact = freshProfile.professionalContact
            professionalContactPublic = freshProfile.professionalContactPublic
        }.onFailure { error = it.localizedMessage }
    }
    DisposableEffect(repo) {
        val listener = repo.listenBlockedUsers({ blockedUsers = it }, { error = it })
        onDispose { listener.remove() }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp), tonalElevation = 1.dp) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(profile?.photoUrl.orEmpty(), name, 68)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(name.ifBlank { "Tu perfil" }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                            Text("@" + profile?.username.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text((profile?.followersCount ?: 0).toString() + " seguidores · " + (profile?.followingCount ?: 0) + " siguiendo", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(onClick = { avatarPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PhotoCamera, null)
                        Text(if (avatarUri != null) "Fotografía seleccionada" else "Cambiar foto de perfil")
                    }
                    OutlinedTextField(name, { name = it }, label = { Text(tr("Nombre público")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(bio, { bio = it }, label = { Text(tr("Presentación")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(tr("Perfil público"), fontWeight = FontWeight.SemiBold); Text(tr("Permite que te encuentren."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                        Switch(isPublic, { isPublic = it })
                    }
                    Text(tr("Tipo de cuenta"), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = accountType == "personal", onClick = { accountType = "personal" }, label = { Text(tr("Particular")) }, leadingIcon = { Icon(Icons.Default.Pets, null) })
                        FilterChip(selected = accountType == "professional", onClick = { accountType = "professional" }, label = { Text(tr("Profesional")) }, leadingIcon = { Icon(Icons.Default.WorkspacePremium, null) })
                    }
                    if (accountType == "professional") {
                        Text(tr("Perfil profesional"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(tr("Estos datos aparecerán en tu perfil público si activas la visibilidad. La insignia indica el tipo de cuenta, no una verificación de credenciales."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Text(tr("El contacto se guarda de forma privada. Solo se comparte si activas expresamente esta opción y tu perfil es público."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(professionalCategory, { professionalCategory = it }, label = { Text(tr("Especialidad o servicio")) }, placeholder = { Text(tr("Adiestramiento, veterinaria, peluquería…")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(professionalLocation, { professionalLocation = it }, label = { Text(tr("Ciudad o zona de trabajo")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(professionalDescription, { professionalDescription = it }, label = { Text(tr("Servicios y experiencia")) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 5)
                        OutlinedTextField(professionalContact, { professionalContact = it }, label = { Text(tr("Contacto profesional (opcional)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tr("Mostrar contacto profesional en tu perfil público"), fontWeight = FontWeight.SemiBold)
                                Text(tr("Solo se mostrará si tu perfil también es público."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = professionalContactPublic, onCheckedChange = { professionalContactPublic = it })
                        }
                    }
                    Button(onClick = {
                        busy = true
                        scope.launch { runCatching {
                            repo.updateProfile(name, bio, isPublic, accountType, professionalCategory, professionalLocation, professionalDescription, professionalContact, professionalContactPublic)
                            if (avatarUri != null) {
                                val url = repo.updateProfilePhoto(avatarUri!!)
                                profile = profile?.copy(photoUrl = url)
                                avatarUri = null
                            }
                            profile = profile?.copy(
                                displayName = name, bio = bio, isPublic = isPublic, accountType = accountType,
                                professionalCategory = professionalCategory, professionalLocation = professionalLocation,
                                professionalDescription = professionalDescription, professionalContact = professionalContact,
                                professionalContactPublic = accountType == "professional" && professionalContactPublic && professionalContact.isNotBlank()
                            )
                            status = "Perfil actualizado."
                        }.onFailure { error = it.localizedMessage }; busy = false }
                    }, enabled = !busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Guardando…" else "Guardar perfil") }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Mis mascotas"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { editDog = null; showDogEditor = true }) { Icon(Icons.Default.Add, null); Text(tr("Añadir")) }
            }
        }
        items(dogs, key = { it.id }) { dog -> DogProfileCard(dog) { editDog = dog; showDogEditor = true } }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("ENCUENTRA A TU GENTE"), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp)
                            Text(tr("Contactos de tu agenda"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.PeopleAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(27.dp))
                    }
                    Text(tr("Elige un contacto con el selector seguro del teléfono. Su número se envía cifrado en tránsito al servicio de Pawly, se transforma mediante HMAC y no se guarda en claro ni se muestra a otros usuarios."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = { contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !phoneDiscoveryBusy
                    ) {
                        Icon(Icons.Default.ContactPhone, null)
                        Text("  " + tr("Buscar un contacto de mi agenda"))
                    }
                    if (phoneDiscoveryBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    contactMatchStatus?.let { Text(tr(it), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                    contactMatches.forEach { match ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(match.photoUrl, match.displayName, 42)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(match.displayName, fontWeight = FontWeight.SemiBold)
                                Text("@${match.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            }
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    HorizontalDivider()
                    Text(tr("Permite que otras personas que tengan tu número te encuentren"), fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = discoveryPhone,
                        onValueChange = { discoveryPhone = it.take(24) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(tr("Tu teléfono (formato internacional)")) },
                        placeholder = { Text("+34 600 000 000") }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Permitir que me encuentren por teléfono"), fontWeight = FontWeight.Medium)
                            Text(tr("Es voluntario y puedes retirarlo cuando quieras."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = phoneDiscoveryEnabled, onCheckedChange = { phoneDiscoveryEnabled = it })
                    }
                    Button(
                        onClick = {
                            phoneDiscoveryBusy = true
                            scope.launch {
                                runCatching { repo.setPhoneDiscovery(discoveryPhone, phoneDiscoveryEnabled) }
                                    .onSuccess { contactMatchStatus = if (phoneDiscoveryEnabled) "La búsqueda por teléfono está activada." else "Has dejado de ser localizable por teléfono." }
                                    .onFailure { contactMatchStatus = it.localizedMessage ?: "No se pudo guardar la preferencia." }
                                phoneDiscoveryBusy = false
                            }
                        },
                        enabled = !phoneDiscoveryBusy && (!phoneDiscoveryEnabled || discoveryPhone.trim().length >= 7),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(tr("Guardar privacidad de contactos")) }
                }
            }
        }
        item { Text(tr("Cuentas bloqueadas"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium) }
        if (blockedUsers.isEmpty()) item { Text(tr("No has bloqueado a nadie."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        items(blockedUsers, key = { it.uid }) { blocked ->
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(blocked.displayName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        if (blocked.username.isNotBlank()) Text("@" + blocked.username, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = {
                        scope.launch { runCatching { repo.unblock(blocked.uid); status = "Cuenta desbloqueada." }.onFailure { error = it.localizedMessage } }
                    }) { Text(tr("Desbloquear")) }
                }
            }
        }
        if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        if (status != null) item { Text(status!!, color = MaterialTheme.colorScheme.primary) }
        item {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(tr("Privacidad y cuenta"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(tr("Puedes solicitar que eliminemos tu cuenta y los datos asociados. La solicitud queda registrada para su revisión; no se borra nada automáticamente en este paso."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = { confirmAccountDeletion = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !accountDeletionSubmitted
                    ) {
                        Icon(Icons.Default.DeleteForever, null)
                        Text(if (accountDeletionSubmitted) "Solicitud enviada" else "Solicitar eliminación de cuenta")
                    }
                    if (accountDeletionSubmitted) Text(tr("Solicitud registrada. El equipo deberá verificarla y completar la eliminación de datos."), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text(tr("Comparte solo la información que quieras hacer pública. Evita direcciones y datos sensibles."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }
    }
    if (confirmAccountDeletion) {
        AlertDialog(
            onDismissRequest = { confirmAccountDeletion = false },
            title = { Text(tr("¿Solicitar la eliminación de tu cuenta?")) },
            text = { Text(tr("Se enviará una solicitud al equipo de Pawly para revisar la eliminación de tu cuenta, perfil, publicaciones y demás datos asociados. La solicitud no elimina los datos inmediatamente. Puedes cerrar esta ventana si has cambiado de opinión.")) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        runCatching {
                            repo.requestAccountDeletion()
                            accountDeletionSubmitted = true
                            status = "Solicitud de eliminación registrada."
                        }.onFailure { error = it.localizedMessage ?: "No se pudo enviar la solicitud." }
                        busy = false
                        confirmAccountDeletion = false
                    }
                }) { Text(if (busy) "Enviando…" else "Enviar solicitud", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmAccountDeletion = false }) { Text(tr("Cancelar")) } }
        )
    }
    if (showDogEditor) DogEditorDialog(repo, editDog, { showDogEditor = false }) {
        showDogEditor = false
        scope.launch { runCatching { dogs = repo.getMyDogs() }.onFailure { error = it.localizedMessage } }
    }
}


@Composable
internal fun DogProfileCard(dog: PawlyDog, onClick: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dog.photoUrl.isNotBlank()) AsyncImage(dog.photoUrl, "Foto de " + dog.name, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
            else Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Lime), contentAlignment = Alignment.Center) { Text(petEmoji(dog.species), fontSize = 30.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(dog.name, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(listOf(dog.species, dog.breed, dog.age).filter { it.isNotBlank() }.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (dog.description.isNotBlank()) Text(dog.description, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                if (dog.traits.isNotEmpty()) Text(dog.traits.joinToString(" · "), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}


@Composable
internal fun DogEditorDialog(repo: FirebaseCommunityRepository, existing: PawlyDog?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var species by remember { mutableStateOf(existing?.species ?: "Perro") }
    var breed by remember { mutableStateOf(existing?.breed.orEmpty()) }
    var age by remember { mutableStateOf(existing?.age.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var traits by remember { mutableStateOf(existing?.traits?.joinToString(", ").orEmpty()) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var photoUrl by remember { mutableStateOf(existing?.photoUrl.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri = it }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "Añadir mascota" else "Editar mascota") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Photo, null); Text(if (uri != null || photoUrl.isNotBlank()) "Cambiar foto" else "Añadir foto") }
            Text(tr("Tipo de mascota"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("Perro", "Gato", "Conejo", "Ave", "Roedor", "Reptil", "Otro").forEach { option ->
                    FilterChip(selected = species == option, onClick = { species = option }, label = { Text(option) })
                }
            }
            OutlinedTextField(name, { name = it }, label = { Text(tr("Nombre")) })
            OutlinedTextField(breed, { breed = it }, label = { Text(tr("Raza")) })
            OutlinedTextField(age, { age = it }, label = { Text(tr("Edad")) })
            OutlinedTextField(description, { description = it }, label = { Text(tr("Descripción")) }, minLines = 2)
            OutlinedTextField(traits, { traits = it }, label = { Text(tr("Características separadas por comas")) })
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = {
        TextButton(enabled = !busy && name.isNotBlank(), onClick = {
            busy = true
            scope.launch {
                try {
                    if (uri != null) photoUrl = repo.uploadImage(uri!!, "pet-photos")
                    repo.saveDog(PawlyDog(id = existing?.id ?: "", name = name, breed = breed, age = age, description = description, traits = traits.split(",").map { it.trim() }.filter { it.isNotBlank() }, photoUrl = photoUrl, species = species))
                    onSaved()
                } catch (e: Exception) { error = e.localizedMessage ?: "No se pudo guardar." }
                finally { busy = false }
            }
        }) { Text(if (busy) "Guardando…" else "Guardar") }
    }, dismissButton = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (existing != null) {
                TextButton(onClick = { confirmDelete = true }, enabled = !busy) {
                    Text(tr("Eliminar"), color = MaterialTheme.colorScheme.error)
                }
            }
            TextButton(onClick = onDismiss, enabled = !busy) { Text(tr("Cancelar")) }
        }
    })
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Eliminar perfil de ${existing.name}?")) },
            text = { Text(tr("Se eliminarán sus datos y su fotografía dejará de aparecer en Pawly. Esta acción no se puede deshacer.")) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        runCatching { repo.deleteDog(existing.id); onSaved() }
                            .onFailure { error = it.toPawlyMessage("No se pudo eliminar el perfil.") }
                        busy = false
                        confirmDelete = false
                    }
                }) { Text(tr("Eliminar"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Conservar")) } }
        )
    }
}



private fun petEmoji(species: String): String = when (species.lowercase()) {
    "gato" -> "🐈"
    "conejo" -> "🐇"
    "ave" -> "🦜"
    "roedor" -> "🐹"
    "reptil" -> "🦎"
    "perro" -> "🐕"
    else -> "🐾"
}
