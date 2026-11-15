package com.pawly.app

import com.pawly.app.tr

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
import org.json.JSONObject
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
    petId: String? = null,
    repository: FirebaseCommunityRepository? = null
) {
    val context = LocalContext.current
    val photoConfigErrorText = tr("Configura la subida de fotografías para guardar imágenes en el diario.")
    val diarySaveErrorText = tr("No se pudo guardar el recuerdo.")
    var note by remember { mutableStateOf("") }
    var memoryTitle by remember { mutableStateOf("") }
    var memoryCategory by remember { mutableStateOf("Paseos") }
    var diaryFilter by remember { mutableStateOf("Todos") }
    var memoryDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedPhotos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var photoUploadBusy by remember { mutableStateOf(false) }
    var photoUploadError by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedPhotos = it.take(8) }
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
    val filteredEntries = remember(visibleEntries, diaryFilter) {
        if (diaryFilter == "Todos") visibleEntries else visibleEntries.filter { parseDiaryRecord(it).category == diaryFilter }
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
                    Text(tr("VUESTROS RECUERDOS"), color = Color.White.copy(alpha = .82f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    Text(tr("Cada paseo merece un recuerdo."), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                }
            }
            Text(tr("Un álbum personal para volver a vuestros mejores momentos."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primary) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(tr("UN RECUERDO NUEVO"), color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = memoryTitle,
                        onValueChange = { memoryTitle = it.take(80) },
                        enabled = canEditSharedDiary,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(tr("Título del recuerdo"), color = Color.White.copy(alpha = .6f)) },
                        singleLine = true,
                        shape = RoundedCornerShape(15.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Lime, unfocusedBorderColor = Color.White.copy(alpha = .3f), cursorColor = Lime)
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(1200) },
                        enabled = canEditSharedDiary,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(tr("Cuenta qué habéis hecho y qué lo hizo especial…"), color = Color.White.copy(alpha = .6f)) },
                        minLines = 2,
                        shape = RoundedCornerShape(15.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Lime, unfocusedBorderColor = Color.White.copy(alpha = .3f), cursorColor = Lime)
                    )
                    Text(tr("Categoría"), color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Paseos", "Viajes", "Juegos", "Amigos", "Celebraciones", "Otros").forEach { value ->
                            FilterChip(selected = memoryCategory == value, onClick = { memoryCategory = value }, enabled = canEditSharedDiary, label = { Text(tr(value)) })
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val initial = Calendar.getInstance().apply { timeInMillis = memoryDate }
                            DatePickerDialog(context, { _, year, month, day ->
                                memoryDate = Calendar.getInstance().apply { set(year, month, day, 12, 0, 0) }.timeInMillis
                            }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).show()
                        },
                        enabled = canEditSharedDiary,
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(7.dp)); Text(java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(memoryDate))) }
                    OutlinedButton(onClick = { photoPicker.launch("image/*") }, enabled = canEditSharedDiary, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(7.dp)); Text(tr("Añadir fotografías (hasta 8)"))
                    }
                    if (selectedPhotos.isNotEmpty()) Text(tr("${selectedPhotos.size} fotografías seleccionadas"), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    if (photoUploadError != null) Text(photoUploadError!!, color = Color(0xFFFFC4BC), style = MaterialTheme.typography.labelSmall)
                    Button(
                        onClick = {
                            diaryScope.launch {
                                photoUploadBusy = true
                                photoUploadError = null
                                runCatching {
                                    val urls = selectedPhotos.map { uri ->
                                        repository?.uploadImage(uri, "diary-photos")
                                            ?: error(photoConfigErrorText)
                                    }
                                    val record = JSONObject()
                                        .put("pawlyDiaryVersion", 1)
                                        .put("title", memoryTitle.trim().ifBlank { note.trim().take(48) })
                                        .put("text", note.trim())
                                        .put("category", memoryCategory)
                                        .put("date", memoryDate)
                                        .put("photos", JSONArray(urls))
                                    updateEntries(listOf(record.toString()) + visibleEntries)
                                    memoryTitle = ""; note = ""; selectedPhotos = emptyList()
                                }.onFailure { photoUploadError = it.localizedMessage ?: diarySaveErrorText }
                                photoUploadBusy = false
                            }
                        },
                        enabled = (memoryTitle.isNotBlank() || note.isNotBlank()) && canEditSharedDiary && !photoUploadBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Forest),
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        if (photoUploadBusy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text(tr(if (photoUploadBusy) "Guardando…" else "Guardar recuerdo"))
                    }
                }
            }
        }
        if (diarySyncMessage != null) item { Text(diarySyncMessage!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
        item { SectionHeading("Pequeñas grandes historias", "Diario de $dogName · ${visibleEntries.size} recuerdos guardados") }
        item {
            Text(tr("Organiza vuestra historia por categorías, fechas y fotografías."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Todos", "Paseos", "Viajes", "Juegos", "Amigos", "Celebraciones", "Otros").forEach { value ->
                    FilterChip(selected = diaryFilter == value, onClick = { diaryFilter = value }, label = { Text(tr(value)) })
                }
            }
        }
        if (filteredEntries.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.AutoStories, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        Text(tr("Aquí empieza vuestra historia"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(tr("Guarda el primer recuerdo y vuelve a él cuando quieras."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        items(filteredEntries) { entry ->
            val record = remember(entry) { parseDiaryRecord(entry) }
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFFE9F0E5)), contentAlignment = Alignment.Center) { Text(tr("🐾"), fontSize = 22.sp) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(record.title.ifBlank { tr("Un recuerdo especial") }, fontWeight = FontWeight.Bold)
                            Text("${tr(record.category)} · ${java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(record.dateMillis))}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        if (canEditSharedDiary) IconButton(onClick = { updateEntries(visibleEntries - entry) }) { Icon(Icons.Default.DeleteOutline, contentDescription = tr("Eliminar recuerdo"), tint = MaterialTheme.colorScheme.error) }
                    else Icon(Icons.Filled.FavoriteBorder, contentDescription = null, tint = Peach)
                    }
                    if (record.text.isNotBlank()) Text(record.text, style = MaterialTheme.typography.bodyMedium)
                    record.photos.forEach { url ->
                        AsyncImage(model = url, contentDescription = tr("Fotografía del recuerdo"), modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(14.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    }
                    Text(if (sharedPetOwnerUid != null) tr("Recuerdo del perfil compartido") else tr("Guardado en tu espacio personal"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
        }
        item {
            Text(if (sharedPetOwnerUid != null) "Los recuerdos pertenecen al perfil compartido y respetan sus permisos de acceso." else "Los recuerdos se guardan en tu espacio personal. Las fotografías se alojan en Cloudinary y se muestran junto a cada historia.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

private data class DiaryRecordDisplay(
    val title: String,
    val text: String,
    val category: String,
    val dateMillis: Long,
    val photos: List<String>
)

private fun parseDiaryRecord(raw: String): DiaryRecordDisplay {
    return runCatching {
        val json = JSONObject(raw)
        if (json.optInt("pawlyDiaryVersion") != 1) error("legacy")
        val photos = json.optJSONArray("photos") ?: JSONArray()
        DiaryRecordDisplay(
            title = json.optString("title"),
            text = json.optString("text"),
            category = json.optString("category", "Otros"),
            dateMillis = json.optLong("date", System.currentTimeMillis()),
            photos = List(photos.length()) { photos.optString(it) }.filter { it.startsWith("https://") }
        )
    }.getOrElse {
        DiaryRecordDisplay(title = raw, text = "", category = "Otros", dateMillis = System.currentTimeMillis(), photos = emptyList())
    }
}
