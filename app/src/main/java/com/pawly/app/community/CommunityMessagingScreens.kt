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
internal fun CreatePostDialog(repo: FirebaseCommunityRepository, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var dog by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("STORY") }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri = it }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (category == "LOST") "Aviso de perro perdido" else if (category == "FOUND") "Aviso de perro encontrado" else if (category == "ADOPTION") "Publicar adopción" else if (category == "FOSTER") "Buscar casa de acogida" else "Compartir en Pawly") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tipo de publicación", color = Forest, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("STORY" to "Historia", "ADOPTION" to "Adopción", "LOST" to "Perdido", "FOUND" to "Encontrado", "FOSTER" to "Acogida").forEach { (value, label) ->
                    FilterChip(selected = category == value, onClick = { category = value }, label = { Text(label) })
                }
            }
            OutlinedTextField(text, { text = it }, label = { Text(if (category == "LOST" || category == "FOUND") "Dónde y cuándo ocurrió, señas y contacto seguro" else "¿Qué queréis contar?") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
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
                    repo.publishPost(text, image, dog, category)
                    onDone()
                } catch (e: Exception) { error = e.localizedMessage ?: "No se pudo publicar." }
                finally { busy = false }
            }
        }) { Text(if (busy) "Publicando…" else "Publicar") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}


@Composable
internal fun CommentsDialog(repo: FirebaseCommunityRepository, post: PawlyPost, onDismiss: () -> Unit) {
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
internal fun ConversationsScreen(repo: FirebaseCommunityRepository, onOpen: (PawlyConversation) -> Unit) {
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
internal fun ChatScreen(repo: FirebaseCommunityRepository, conversation: PawlyConversation, onBack: () -> Unit) {
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

