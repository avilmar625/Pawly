package com.pawly.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pawly.app.care.data.SharedPetRepository
import com.pawly.app.care.data.PetShareMember
import com.pawly.app.care.data.PetShareInvitation
import kotlinx.coroutines.launch

@Composable
internal fun PetSharingSection(petName: String, petId: String, ownerUid: String?) {
    val repo = remember { runCatching { SharedPetRepository() }.getOrNull() }
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var members by remember { mutableStateOf(emptyList<PetShareMember>()) }
    var invitations by remember { mutableStateOf(emptyList<PetShareInvitation>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun refresh() {
        val repository = repo ?: return
        scope.launch {
            loading = true
            runCatching {
                repository.ensureOwnedPet(petId, petName)
                members = repository.getMembers(petId)
                invitations = repository.getMyInvitations()
            }.onFailure { message = it.localizedMessage ?: "No se pudo actualizar el acceso." }
            loading = false
        }
    }
    LaunchedEffect(repo, petId, petName) { refresh() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Compartir el perfil de $petName", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Invita a tu pareja u otra persona de confianza mediante su nombre de usuario de Pawly. Podrá aceptar o rechazar la invitación.")
                    Text("En esta versión se comparte el acceso al perfil; el historial sanitario y las rutinas siguen siendo privados de la cuenta propietaria.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("Invitar a un cuidador", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(username, { username = it.take(24) }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, label = { Text("Nombre de usuario") }, placeholder = { Text("@usuario") })
            Button(onClick = {
                val repository = repo ?: return@Button
                busy = true; message = null
                scope.launch {
                    runCatching { repository.inviteByUsername(petId, username) }
                        .onSuccess { message = "Invitación enviada a @${username.removePrefix("@")}"; username = ""; refresh() }
                        .onFailure { message = it.localizedMessage ?: "No se pudo enviar la invitación." }
                    busy = false
                }
            }, enabled = !busy && username.trim().removePrefix("@").length >= 3 && repo != null,
                modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else { Icon(Icons.Default.GroupAdd, null); Spacer(Modifier.width(8.dp)); Text("Enviar invitación") }
            }
        }
        if (message != null) item { Text(message!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        item { Text("Personas con acceso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        items(members, key = { it.uid }) { member ->
            Card {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(member.displayName, fontWeight = FontWeight.SemiBold)
                        Text(if (member.role == "owner") "Propietario" else "Cuidador · ${member.role}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (member.role != "owner" && ownerUid != null && repo != null) {
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { repo.removeMember(petId, member.uid); refresh() }
                                    .onFailure { message = it.localizedMessage }
                            }
                        }) { Icon(Icons.Default.PersonRemove, contentDescription = "Retirar acceso") }
                    }
                }
            }
        }
        item { Text("Invitaciones que has recibido", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (invitations.isEmpty()) item {
            Text("No tienes invitaciones pendientes.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        items(invitations, key = { it.id }) { invite ->
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${invite.ownerName} quiere compartir contigo el perfil de ${invite.petName}.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                runCatching { repo?.respondToInvitation(invite.id, true); message = "Ya tienes acceso al perfil de ${invite.petName}."; refresh() }
                                    .onFailure { message = it.localizedMessage }
                            }
                        }) { Text("Aceptar") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                runCatching { repo?.respondToInvitation(invite.id, false); refresh() }
                                    .onFailure { message = it.localizedMessage }
                            }
                        }) { Text("Rechazar") }
                    }
                }
            }
        }
    }
}
