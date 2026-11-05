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
internal fun MyCommunityProfile(repo: FirebaseCommunityRepository, onSignOut: () -> Unit) {
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
                Text("Mis mascotas", color = Forest, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
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
internal fun DogProfileCard(dog: PawlyDog, onClick: (() -> Unit)? = null) {
    Surface(color = Paper, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dog.photoUrl.isNotBlank()) AsyncImage(dog.photoUrl, "Foto de " + dog.name, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
            else Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Lime), contentAlignment = Alignment.Center) { Text(petEmoji(dog.species), fontSize = 30.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(dog.name, color = Forest, fontWeight = FontWeight.Bold)
                Text(listOf(dog.species, dog.breed, dog.age).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, style = MaterialTheme.typography.bodySmall)
                if (dog.description.isNotBlank()) Text(dog.description, color = Ink, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                if (dog.traits.isNotEmpty()) Text(dog.traits.joinToString(" · "), color = ForestLight, style = MaterialTheme.typography.labelSmall)
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "Añadir perro" else "Editar perro") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Photo, null); Text(if (uri != null || photoUrl.isNotBlank()) "Cambiar foto" else "Añadir foto") }
            Text("Tipo de mascota", color = Forest, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("Perro", "Gato", "Conejo", "Ave", "Roedor", "Reptil", "Otro").forEach { option ->
                    FilterChip(selected = species == option, onClick = { species = option }, label = { Text(option) })
                }
            }
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
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") }
        }
    })
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar perfil de ${existing.name}?") },
            text = { Text("Se eliminarán sus datos y su fotografía dejará de aparecer en Pawly. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        runCatching { repo.deleteDog(existing.id); onSaved() }
                            .onFailure { error = it.toPawlyMessage("No se pudo eliminar el perfil.") }
                        busy = false
                        confirmDelete = false
                    }
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Conservar") } }
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
