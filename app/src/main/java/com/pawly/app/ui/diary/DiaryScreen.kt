package com.pawly.app

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.app.DatePickerDialog
import java.io.File
import java.util.Calendar
import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.Location
import android.location.LocationListener
import android.os.Build
import android.os.Looper
import android.location.Geocoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.core.content.ContextCompat
import org.json.JSONArray
import coil3.compose.AsyncImage
import com.pawly.app.care.data.FirebasePetProfileRepository
import kotlinx.coroutines.launch

@Composable
internal fun DiaryScreen(
    dogName: String,
    entries: List<String>,
    onEntriesChange: (List<String>) -> Unit,
    elegantDesign: Boolean = false,
    sharedPetOwnerUid: String? = null,
    petId: String? = null
) {
    var note by remember { mutableStateOf("") }
    val sharedDiaryRepository = remember(sharedPetOwnerUid) {
        sharedPetOwnerUid?.let { runCatching { FirebasePetProfileRepository(ownerUid = it) }.getOrNull() }
    }
    var visibleEntries by remember(sharedPetOwnerUid, petId, entries) { mutableStateOf(if (sharedPetOwnerUid == null) entries else emptyList()) }
    var canEditSharedDiary by remember(sharedPetOwnerUid, petId) { mutableStateOf(sharedPetOwnerUid == null) }
    var diarySyncMessage by remember(sharedPetOwnerUid) { mutableStateOf<String?>(null) }
    val diaryScope = rememberCoroutineScope()
    LaunchedEffect(sharedPetOwnerUid, petId, sharedDiaryRepository) {
        if (sharedPetOwnerUid != null) {
            visibleEntries = emptyList()
            canEditSharedDiary = false
            if (petId != null && sharedDiaryRepository != null) {
                runCatching {
                    visibleEntries = sharedDiaryRepository.loadDiary(petId)
                    canEditSharedDiary = sharedDiaryRepository.canEditDiary(petId)
                    diarySyncMessage = if (canEditSharedDiary) "Diario compartido · puedes añadir recuerdos" else "Diario compartido · solo lectura"
                }.onFailure { diarySyncMessage = "No se pudo cargar el diario compartido" }
            } else {
                diarySyncMessage = "No se puede conectar con el diario compartido"
            }
        } else {
            visibleEntries = entries
            canEditSharedDiary = true
            diarySyncMessage = null
        }
    }
    fun updateEntries(updated: List<String>) {
        visibleEntries = updated
        if (sharedPetOwnerUid == null) {
            onEntriesChange(updated)
        } else if (petId != null && sharedDiaryRepository != null) {
            diaryScope.launch {
                runCatching { sharedDiaryRepository.replaceDiary(petId, updated) }
                    .onSuccess { diarySyncMessage = "Diario compartido sincronizado" }
                    .onFailure { diarySyncMessage = "No se pudo sincronizar el cambio" }
            }
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(if (elegantDesign) 245.dp else 205.dp).clip(RoundedCornerShape(28.dp))) {
                AsyncImage(
                    model = "https://images.unsplash.com/photo-1517849845537-4d257902454a?w=1100&h=500&fit=crop",
                    contentDescription = "Perro disfrutando de un paseo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC10251F)))))
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    Text("VUESTROS RECUERDOS", color = Color.White.copy(alpha = .82f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    Text("Cada paseo merece un recuerdo.", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                }
            }
            Text("Un álbum personal para volver a vuestros mejores momentos.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primary) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("UN RECUERDO NUEVO", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        enabled = canEditSharedDiary,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("¿Qué habéis hecho hoy?", color = Color.White.copy(alpha = .6f)) },
                        shape = RoundedCornerShape(15.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Lime,
                            unfocusedBorderColor = Color.White.copy(alpha = .3f),
                            cursorColor = Lime
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (note.isNotBlank()) {
                                updateEntries(listOf(note.trim()) + visibleEntries)
                                note = ""
                            }
                        },
                        enabled = note.isNotBlank() && canEditSharedDiary,
                        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Forest),
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text("Guardar recuerdo")
                    }
                }
            }
        }
        if (diarySyncMessage != null) item { Text(diarySyncMessage!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
        item { SectionHeading("Pequeñas grandes historias", "Diario de $dogName · ${visibleEntries.size} recuerdos guardados") }
        if (visibleEntries.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.AutoStories, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        Text("Aquí empieza vuestra historia", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Guarda el primer recuerdo y vuelve a él cuando quieras.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        items(visibleEntries) { entry ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(49.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFE9F0E5)), contentAlignment = Alignment.Center) {
                        Text("🐾", fontSize = 24.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry, fontWeight = FontWeight.SemiBold)
                        Text(if (sharedPetOwnerUid != null) "Recuerdo del perfil compartido" else "Guardado en tu espacio personal", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    Icon(Icons.Filled.FavoriteBorder, contentDescription = null, tint = Peach)
                }
            }
        }
        item {
            Text(if (sharedPetOwnerUid != null) "Los recuerdos pertenecen al perfil compartido y respetan sus permisos de acceso." else "Los recuerdos se sincronizan con tu cuenta cuando hay conexión. No se incluyen fotografías en las entradas.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}
