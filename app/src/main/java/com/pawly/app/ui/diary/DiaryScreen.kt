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
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
internal fun DiaryScreen(
    dogName: String,
    entries: List<String>,
    onEntriesChange: (List<String>) -> Unit,
    sharedPetOwnerUid: String? = null,
    petId: String? = null,
    repository: FirebaseCommunityRepository? = null,
    openPhotoPickerOnEnter: Boolean = false,
    onPhotoPickerOpened: () -> Unit = {}
) {
    val context = LocalContext.current
    val photoConfigErrorText = tr("La subida de fotografías no está disponible ahora. Comprueba la conexión e inténtalo de nuevo.")
    val diarySaveErrorText = tr("No se pudo guardar el recuerdo.")
    var note by remember { mutableStateOf("") }
    var memoryTitle by remember { mutableStateOf("") }
    val diaryPreferenceScope = "${sharedPetOwnerUid ?: "personal"}_${petId ?: dogName}_$dogName"
        .replace(Regex("[^A-Za-z0-9_-]"), "_")
    val (newMemoryExpanded, toggleNewMemoryExpanded) =
        rememberPawlySectionExpanded("diary_new_memory_$diaryPreferenceScope")
    var memoryCategory by remember { mutableStateOf("Paseos") }
    var diaryFilter by remember { mutableStateOf("Todos") }
    var newestFirst by remember { mutableStateOf(true) }
    var memoryDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedPhotos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var photoUploadBusy by remember { mutableStateOf(false) }
    var photoUploadError by remember { mutableStateOf<String?>(null) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedPhotos = it.take(8) }
    val cameraCapture = rememberPawlyCameraCapture(
        onImageSelected = { uri ->
            if (selectedPhotos.size < 8) selectedPhotos = selectedPhotos + uri
            else photoUploadError = "Puedes adjuntar un máximo de ocho fotografías."
        },
        onError = { photoUploadError = it }
    )
    LaunchedEffect(openPhotoPickerOnEnter) {
        if (openPhotoPickerOnEnter) {
            showPhotoSourceDialog = true
            onPhotoPickerOpened()
        }
    }
    val sharedDiaryRepository = remember(sharedPetOwnerUid) {
        sharedPetOwnerUid?.let { runCatching { FirebasePetProfileRepository(ownerUid = it) }.getOrNull() }
    }
    var visibleEntries by remember(sharedPetOwnerUid, petId, entries) { mutableStateOf(if (sharedPetOwnerUid == null) entries else emptyList()) }
    var canEditSharedDiary by remember(sharedPetOwnerUid, petId) { mutableStateOf(sharedPetOwnerUid == null) }
    var diaryReady by remember(sharedPetOwnerUid, petId) { mutableStateOf(sharedPetOwnerUid == null) }
    var diarySyncMessage by remember(sharedPetOwnerUid) { mutableStateOf<String?>(null) }
    val diaryScope = rememberCoroutineScope()
    LaunchedEffect(sharedPetOwnerUid, petId, sharedDiaryRepository) {
        if (sharedPetOwnerUid != null) {
            visibleEntries = emptyList()
            canEditSharedDiary = false
            if (petId != null && sharedDiaryRepository != null) {
                runCatching {
                    if (sharedPetOwnerUid == FirebaseAuth.getInstance().currentUser?.uid) {
                        sharedDiaryRepository.ensurePet(petId, dogName)
                    }
                    canEditSharedDiary = sharedDiaryRepository.canEditDiary(petId)
                    diaryReady = true
                }
                    .onSuccess { diarySyncMessage = if (canEditSharedDiary) "Diario compartido · cambios en tiempo real" else "Diario compartido · solo lectura" }
                    .onFailure {
                        diaryReady = false
                        diarySyncMessage = "No se pudieron preparar los permisos del diario: ${it.localizedMessage}"
                    }
            } else {
                diarySyncMessage = "No se puede conectar con el diario compartido"
            }
        } else {
            visibleEntries = entries
            canEditSharedDiary = true
            diarySyncMessage = null
        }
    }
    DisposableEffect(sharedPetOwnerUid, petId, sharedDiaryRepository, diaryReady) {
        val listener = if (sharedPetOwnerUid != null && petId != null && sharedDiaryRepository != null && diaryReady) {
            sharedDiaryRepository.listenDiary(
                petId,
                onEntries = { remoteEntries -> visibleEntries = remoteEntries },
                onError = { message -> diarySyncMessage = message }
            )
        } else null
        onDispose { listener?.remove() }
    }
    fun updateEntries(updated: List<String>) {
        val previous = visibleEntries
        visibleEntries = updated
        val removed = previous.filterNot { it in updated }
        if (sharedPetOwnerUid == null) {
            onEntriesChange(updated)
            if (removed.isNotEmpty() && repository != null) {
                diaryScope.launch {
                    removed.forEach { raw ->
                        parseDiaryRecord(raw).photos.forEach { url ->
                            runCatching { repository.deleteCloudinaryAsset(url) }
                        }
                    }
                }
            }
        } else if (petId != null && sharedDiaryRepository != null) {
            val added = updated.filterNot { it in previous }
            diaryScope.launch {
                runCatching {
                    added.forEach { sharedDiaryRepository.addDiaryEntry(petId, it) }
                    removed.forEach { raw ->
                        sharedDiaryRepository.deleteDiaryEntry(petId, raw)
                        if (repository != null) parseDiaryRecord(raw).photos.forEach { url ->
                            runCatching { repository.deleteCloudinaryAsset(url) }
                        }
                    }
                }.onSuccess {
                    diarySyncMessage = "Diario compartido sincronizado en todos los móviles"
                }.onFailure {
                    diarySyncMessage = "No se pudo sincronizar el cambio: ${it.localizedMessage ?: "comprueba la conexión"}"
                }
            }
        }
    }
    val filteredEntries = remember(visibleEntries, diaryFilter, newestFirst) {
        val matching = if (diaryFilter == "Todos") visibleEntries
            else visibleEntries.filter { parseDiaryRecord(it).category == diaryFilter }
        matching.sortedBy { parseDiaryRecord(it).dateMillis }.let { if (newestFirst) it.reversed() else it }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                            Icon(
                                Icons.Default.AutoStories,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(9.dp).size(21.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                tr("UN RECUERDO NUEVO").lowercase().replaceFirstChar { it.titlecase() },
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                tr("Guarda los momentos especiales de vuestra historia."),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(
                            onClick = toggleNewMemoryExpanded,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                if (newMemoryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = tr(if (newMemoryExpanded) "Ocultar formulario" else "Mostrar formulario")
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(tr(if (newMemoryExpanded) "Ocultar" else "Crear recuerdo"))
                        }
                    }
                    AnimatedVisibility(visible = newMemoryExpanded, enter = fadeIn(), exit = fadeOut()) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(
                        value = memoryTitle,
                        onValueChange = { memoryTitle = it.take(80) },
                        enabled = canEditSharedDiary,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(tr("Título del recuerdo")) },
                        singleLine = true,
                        shape = RoundedCornerShape(15.dp),
                        colors = OutlinedTextFieldDefaults.colors()
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(1200) },
                        enabled = canEditSharedDiary,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(tr("Cuenta qué habéis hecho y qué lo hizo especial…")) },
                        minLines = 2,
                        shape = RoundedCornerShape(15.dp),
                        colors = OutlinedTextFieldDefaults.colors()
                    )
                    PawlyDropdownSelector(
                        title = tr("Categoría"),
                        selectedLabel = tr(memoryCategory),
                        options = listOf("Paseos", "Viajes", "Juegos", "Amigos", "Celebraciones", "Otros").map { it to tr(it) },
                        selectedValue = memoryCategory,
                        onSelected = { memoryCategory = it },
                        enabled = canEditSharedDiary
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val initial = Calendar.getInstance().apply { timeInMillis = memoryDate }
                                DatePickerDialog(context, { _, year, month, day ->
                                    memoryDate = Calendar.getInstance().apply { set(year, month, day, 12, 0, 0) }.timeInMillis
                                }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).show()
                            },
                            enabled = canEditSharedDiary,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CalendarMonth, null)
                            Spacer(Modifier.width(5.dp))
                            Text(java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(memoryDate)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        PawlyPhotoSourceButton(
                            onGallery = { photoPicker.launch("image/*") },
                            onCamera = { cameraCapture() },
                            label = "Fotos",
                            modifier = Modifier.weight(1f),
                            enabled = canEditSharedDiary
                        )
                    }
                    if (selectedPhotos.isNotEmpty()) {
                        Text(tr("${selectedPhotos.size} fotografías seleccionadas"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            selectedPhotos.forEach { uri -> AsyncImage(model = uri, contentDescription = tr("Vista previa de fotografía"), modifier = Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                        }
                    }
                    if (photoUploadError != null) Text(photoUploadError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    Button(
                        onClick = {
                            diaryScope.launch {
                                photoUploadBusy = true
                                photoUploadError = null
                                var uploadedPhotoUrls = emptyList<String>()
                                runCatching {
                                    uploadedPhotoUrls = selectedPhotos.map { uri ->
                                        repository?.uploadImage(uri, "diary-photos")
                                            ?: error(photoConfigErrorText)
                                    }
                                    val urls = uploadedPhotoUrls
                                    val record = JSONObject()
                                        .put("pawlyDiaryVersion", 1)
                                        .put("title", memoryTitle.trim().ifBlank { note.trim().take(48) })
                                        .put("text", note.trim())
                                        .put("category", memoryCategory)
                                        .put("date", memoryDate)
                                        .put("photos", JSONArray(urls))
                                    updateEntries(listOf(record.toString()) + visibleEntries)
                                    memoryTitle = ""; note = ""; selectedPhotos = emptyList()
                                }.onFailure {
                                    uploadedPhotoUrls.forEach { url -> runCatching { repository?.deleteCloudinaryAsset(url) } }
                                    photoUploadError = it.localizedMessage ?: diarySaveErrorText
                                }
                                photoUploadBusy = false
                            }
                        },
                        enabled = (memoryTitle.isNotBlank() || note.isNotBlank()) && canEditSharedDiary && !photoUploadBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
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
            }
        }
        if (diarySyncMessage != null) item { Text(diarySyncMessage!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("${filteredEntries.size} recuerdos"),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
                TextButton(onClick = { newestFirst = !newestFirst }) {
                    Icon(if (newestFirst) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null)
                    Spacer(Modifier.width(5.dp))
                    Text(tr(if (newestFirst) "Más recientes" else "Más antiguos"))
                }
            }
            PawlyDropdownSelector(
                title = tr("Filtrar recuerdos"),
                selectedLabel = tr(diaryFilter),
                options = listOf("Todos", "Paseos", "Viajes", "Juegos", "Amigos", "Celebraciones", "Otros").map { it to tr(it) },
                selectedValue = diaryFilter,
                onSelected = { diaryFilter = it }
            )
        }
        if (filteredEntries.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Text(tr("🐾"), fontSize = 22.sp) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(record.title.ifBlank { tr("Un recuerdo especial") }, fontWeight = FontWeight.Bold)
                            Text("${tr(record.category)} · ${java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(record.dateMillis))}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        if (canEditSharedDiary) IconButton(onClick = { updateEntries(visibleEntries - entry) }) { Icon(Icons.Default.DeleteOutline, contentDescription = tr("Eliminar recuerdo"), tint = MaterialTheme.colorScheme.error) }
                    else Icon(Icons.Filled.FavoriteBorder, contentDescription = null, tint = Peach)
                    }
                    if (record.text.isNotBlank()) Text(record.text, style = MaterialTheme.typography.bodyMedium)
                    if (record.photos.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        record.photos.forEach { url -> AsyncImage(model = url, contentDescription = tr("Fotografía del recuerdo"), modifier = Modifier.width(190.dp).height(150.dp).clip(RoundedCornerShape(14.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                    }
                    Text(if (sharedPetOwnerUid != null) tr("Recuerdo del perfil compartido") else tr("Guardado en tu espacio personal"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
        }
        item {
            Text(if (sharedPetOwnerUid != null) "Los recuerdos pertenecen al perfil compartido y respetan sus permisos de acceso." else "Los recuerdos se guardan en tu espacio personal. Las fotografías se alojan en Cloudinary y se muestran junto a cada historia.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = { Text(tr("Añadir fotografías")) },
            text = { Text(tr("Elige desde dónde quieres añadir las fotos del recuerdo.")) },
            confirmButton = {
                Row {
                    TextButton(onClick = { showPhotoSourceDialog = false; photoPicker.launch("image/*") }) { Text(tr("Galería")) }
                    TextButton(onClick = { showPhotoSourceDialog = false; cameraCapture() }) { Text(tr("Cámara")) }
                }
            },
            dismissButton = { TextButton(onClick = { showPhotoSourceDialog = false }) { Text(tr("Cancelar")) } }
        )
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
