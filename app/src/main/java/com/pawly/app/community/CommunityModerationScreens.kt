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
internal fun ModerationScreen(repo: FirebaseCommunityRepository) {
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
            Text(tr("Centro de moderación"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
            Text(tr("Los avisos quedan registrados para revisión del equipo."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (status != null) item { Text(status!!, color = MaterialTheme.colorScheme.primary) }
        if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        if (reports.isEmpty()) item { EmptyCommunityCard("Sin denuncias pendientes", "Cuando alguien envíe un aviso, aparecerá aquí.") }
        items(reports, key = { it.id }) { report ->
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(report.targetType.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(report.status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("Motivo: " + report.reason, color = MaterialTheme.colorScheme.onSurface)
                    Text("Elemento: " + report.targetId, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (report.targetType == "post") {
                            OutlinedButton(onClick = {
                                scope.launch { runCatching { repo.removeReportedPost(report.targetId); repo.markReportReviewed(report.id); status = "Publicación eliminada y aviso revisado." }.onFailure { error = it.localizedMessage } }
                            }) { Icon(Icons.Default.DeleteOutline, null); Text(tr("Eliminar publicación")) }
                        }
                        if (report.targetType == "user") {
                            OutlinedButton(onClick = {
                                scope.launch { runCatching { repo.hideReportedUser(report.targetId); repo.markReportReviewed(report.id); status = "Perfil restringido y aviso revisado." }.onFailure { error = it.localizedMessage } }
                            }) { Icon(Icons.Default.VisibilityOff, null); Text(tr("Restringir perfil")) }
                        }
                        if (report.targetType == "event") {
                            OutlinedButton(onClick = {
                                scope.launch { runCatching { repo.removeReportedEvent(report.targetId); repo.markReportReviewed(report.id); status = "Evento eliminado y aviso revisado." }.onFailure { error = it.localizedMessage } }
                            }) { Icon(Icons.Default.DeleteOutline, null); Text(tr("Eliminar evento")) }
                        }
                        TextButton(onClick = {
                            scope.launch { runCatching { repo.markReportReviewed(report.id); status = "Aviso marcado como revisado." }.onFailure { error = it.localizedMessage } }
                        }) { Text(tr("Marcar revisado")) }
                    }
                }
            }
        }
    }
}


@Composable
internal fun ReportDialog(repo: FirebaseCommunityRepository, targetId: String, type: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("Contenido inapropiado") }
    var busy by remember { mutableStateOf(false) }
    val reasons = listOf("Contenido inapropiado", "Acoso o intimidación", "Spam o fraude", "Bienestar animal", "Suplantación de identidad", "Otro")
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Denunciar")) }, text = {
        Column {
            Text(tr("Selecciona el motivo del aviso."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            reasons.forEach { item -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(reason == item, { reason = item }); Text(item) } }
        }
    }, confirmButton = {
        TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch { runCatching { repo.report(targetId, type, reason); onDone() }.onFailure { busy = false } }
        }) { Text(if (busy) "Enviando…" else "Enviar denuncia") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar")) } })
}

