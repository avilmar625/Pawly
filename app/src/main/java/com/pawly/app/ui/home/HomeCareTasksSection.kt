package com.pawly.app

import android.net.Uri
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pawly.app.care.data.FirebasePetCareRepository
import com.pawly.app.care.data.PetCareLocalStore
import com.pawly.app.care.model.CareLogEntry
import com.pawly.app.care.model.CareTask
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun mergeCareLogs(
    remoteLogs: List<CareLogEntry>,
    cachedLogs: List<CareLogEntry>
): List<CareLogEntry> {
    val remoteIds = remoteLogs.mapTo(mutableSetOf()) { it.id }
    val mergedRemote = remoteLogs.map { remote ->
        val cached = cachedLogs.firstOrNull { it.id == remote.id }
        val localPhotos = cached?.photoUrls.orEmpty().filterNot {
            it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
        }
        if (localPhotos.isEmpty()) remote
        else remote.copy(photoUrls = (remote.photoUrls + localPhotos).distinct())
    }
    val pendingLocal = cachedLogs.filter { it.id.startsWith("local-") && it.id !in remoteIds }
    return (mergedRemote + pendingLocal).sortedByDescending { it.occurredAt }.take(100)
}

@Composable
internal fun HomeCareTasksSection(
    petName: String,
    storageScope: String?,
    walkDone: Boolean,
    medicineDone: Boolean,
    onWalkToggle: (Boolean) -> Unit,
    onMedicineToggle: (Boolean) -> Unit,
    onOpenCalendar: () -> Unit,
    onWeight: () -> Unit,
    onPhoto: () -> Unit,
    onFood: () -> Unit,
    upcomingTitle: String? = null,
    upcomingType: String? = null,
    upcomingDate: String? = null,
    upcomingTime: String? = null,
    onUpcomingClick: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val language = LocalPawlyLanguage.current
    val locale = PawlyI18n.locale(language)
    val savedLocalMessage = PawlyI18n.text("Guardado en este dispositivo", language)
    val loadingMessage = PawlyI18n.text("Cargando cuidados…", language)
    val localRoutinesSyncedMessage = PawlyI18n.text("Rutinas locales sincronizadas", language)
    val careSyncedMessage = PawlyI18n.text("Cuidados sincronizados", language)
    val offlineMessage = PawlyI18n.text("Sin conexión: se conserva la copia local", language)
    val changesSyncedMessage = PawlyI18n.text("Cambios sincronizados", language)
    val localPendingMessage = PawlyI18n.text("Guardado localmente; sincronización pendiente", language)
    val walkLabel = PawlyI18n.text("Paseo", language)
    val youLabel = PawlyI18n.text("Tú", language)
    val cannotRegisterCareMessage = PawlyI18n.text("No tienes permiso para registrar cuidados de esta mascota.", language)
    val medicineLabel = PawlyI18n.text("Medicación", language)
    val todayLabel = PawlyI18n.text("hoy", language)
    val tomorrowLabel = PawlyI18n.text("mañana", language)
    val inLabel = PawlyI18n.text("dentro de", language)
    val daysLabel = PawlyI18n.text("días", language)
    val today = LocalDate.now()
    val todayKey = today.toString()
    var tasks by remember(petName, storageScope) {
        mutableStateOf(PetCareLocalStore.loadTasks(context, petName, storageScope))
    }
    var logs by remember(petName, storageScope) {
        mutableStateOf(PetCareLocalStore.loadCareLogs(context, petName, storageScope))
    }
    val repository = remember(storageScope) {
        if (storageScope.isNullOrBlank()) null
        else runCatching { FirebasePetCareRepository() }.getOrNull()
    }
    val scope = rememberCoroutineScope()
    var canEdit by remember(petName, storageScope) { mutableStateOf(storageScope.isNullOrBlank()) }
    var syncMessage by remember(petName, storageScope) {
        mutableStateOf(if (storageScope.isNullOrBlank()) savedLocalMessage else loadingMessage)
    }
    var loading by remember(petName, storageScope) { mutableStateOf(!storageScope.isNullOrBlank()) }
    var pendingIds by remember(petName, storageScope) { mutableStateOf(emptySet<String>()) }
    var pendingMedicationConfirmation by remember(petName, storageScope) { mutableStateOf<Pair<CareTask, CareLogEntry>?>(null) }

    var showWalkDialog by remember { mutableStateOf(false) }
    var walkDistance by remember { mutableStateOf("") }
    var walkMapUrl by remember { mutableStateOf("") }
    var walkDescription by remember { mutableStateOf("") }
    var walkWalker by remember { mutableStateOf("") }
    var walkObservations by remember { mutableStateOf("") }
    var walkPhotoUris by remember { mutableStateOf(emptyList<Uri>()) }
    var walkPhotoLocalPaths by remember { mutableStateOf(emptyList<String>()) }
    var walkError by remember { mutableStateOf<String?>(null) }
    var walkSaving by remember { mutableStateOf(false) }
    val walkPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
        val limitedUris = selectedUris.take(4)
        walkPhotoUris = limitedUris
        walkPhotoLocalPaths = emptyList()
        walkError = if (selectedUris.size > 4) "Puedes adjuntar un máximo de cuatro fotografías." else null
        scope.launch {
            val localCopies = withContext(Dispatchers.IO) {
                val photoDirectory = File(context.filesDir, "walk-photos").apply { mkdirs() }
                limitedUris.mapIndexedNotNull { index, uri ->
                    runCatching {
                        val destination = File(photoDirectory, "walk-${System.currentTimeMillis()}-$index.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            destination.outputStream().use { output -> input.copyTo(output) }
                        } ?: error("No se pudo leer una fotografía.")
                        Uri.fromFile(destination).toString()
                    }.getOrNull()
                }
            }
            walkPhotoLocalPaths = localCopies
            if (localCopies.size < limitedUris.size) {
                walkError = "No se han podido preparar todas las fotografías; vuelve a seleccionarlas."
            }
        }
    }

    LaunchedEffect(petName, storageScope, repository, language) {
        val repo = repository ?: run { loading = false; return@LaunchedEffect }
        loading = true
        runCatching {
            repo.ensureSharedPet(petName, petName)
            canEdit = repo.canEditSharedPet(petName)
            val remoteTasks = repo.loadCareTasks(petName)
            if (remoteTasks.isEmpty() && tasks.isNotEmpty()) {
                repo.saveCareTasks(petName, tasks)
                syncMessage = localRoutinesSyncedMessage
            } else {
                tasks = remoteTasks
                PetCareLocalStore.saveTasks(context, petName, remoteTasks, storageScope)
                syncMessage = careSyncedMessage
            }
            logs = mergeCareLogs(repo.loadCareLogs(petName), logs)
            PetCareLocalStore.saveCareLogs(context, petName, logs, storageScope)
        }.onFailure {
            syncMessage = offlineMessage
        }
        loading = false
    }

    val visibleTasks = tasks.filter { task ->
        com.pawly.app.care.domain.CareTaskSchedule.isDueOn(task, today) &&
            (task.frequency != "Puntual" || !task.completed || task.completedOn == todayKey)
    }
    fun isComplete(task: CareTask): Boolean =
        com.pawly.app.care.domain.CareTaskSchedule.isCompleteOn(task, today)
    val orderedVisibleTasks = visibleTasks.sortedWith(
        compareBy<CareTask>({ it.time.ifBlank { "99:99" } }, { it.title })
    )
    // Home reflects only routines explicitly configured by the household.
    // Never manufacture default walks or medication tasks.
    val completedCount = orderedVisibleTasks.count(::isComplete)
    val totalCount = orderedVisibleTasks.size
    val progress = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount
    val nextCareLabel = orderedVisibleTasks.firstOrNull { !isComplete(it) }?.title

    fun toggleTask(task: CareTask, checked: Boolean) {
        if (!canEdit || task.id in pendingIds) return
        val updated = task.copy(completed = checked, completedOn = if (checked) todayKey else "")
        tasks = tasks.map { if (it.id == task.id) updated else it }
        PetCareLocalStore.saveTasks(context, petName, tasks, storageScope)
        if (checked) {
            val local = CareLogEntry(
                id = "local-${System.currentTimeMillis()}-${task.id}",
                taskTitle = task.title,
                action = task.action,
                actorUid = "local",
                actorName = "Tú",
                occurredAt = System.currentTimeMillis()
            )
            logs = (listOf(local) + logs).take(30)
            PetCareLocalStore.saveCareLogs(context, petName, logs, storageScope)
        }
        val repo = repository ?: return
        pendingIds = pendingIds + task.id
        scope.launch {
            runCatching {
                repo.saveCareTask(petName, updated)
                if (checked) repo.logCareAction(petName, task.title, task.action)
                repo.loadCareLogs(petName)
            }.onSuccess { remoteLogs ->
                if (checked) {
                    logs = mergeCareLogs(remoteLogs, logs)
                    PetCareLocalStore.saveCareLogs(context, petName, logs, storageScope)
                }
                syncMessage = changesSyncedMessage
            }.onFailure {
                syncMessage = localPendingMessage
            }
            pendingIds = pendingIds - task.id
        }
    }

    fun requestTaskToggle(task: CareTask, checked: Boolean) {
        if (!canEdit || task.id in pendingIds) return
        if (checked && task.action.equals("Medicación", ignoreCase = true)) {
            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            val duplicate = com.pawly.app.care.domain.CareDuplicateGuard.findRecentMedicationLog(
                logs = logs,
                task = task,
                currentUid = currentUid
            )
            if (duplicate != null) {
                pendingMedicationConfirmation = task to duplicate
                return
            }
        }
        toggleTask(task, checked)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${tr("HOY PARA")} ${petName.uppercase(locale)}",
                        color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("$completedCount/$totalCount ${tr("cuidados completados")}",
                        color = Color.White, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold)
                    Text(
                        when {
                            totalCount == 0 -> tr("Aún no has creado rutinas para hoy.")
                            completedCount == totalCount -> tr("¡Todo listo! Qué buen equipo.")
                            completedCount == totalCount - 1 && !nextCareLabel.isNullOrBlank() -> "${tr("Solo falta")} $nextCareLabel."
                            else -> "${tr("Aún quedan")} ${totalCount - completedCount} ${tr("cuidados por completar")}."
                        },
                        color = Color.White.copy(alpha = .86f), style = MaterialTheme.typography.bodySmall
                    )
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(68.dp)) {
                    CircularProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        trackColor = Color.White.copy(alpha = .18f),
                        strokeWidth = 7.dp
                    )
                    Text("$completedCount/$totalCount", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            PawlySectionHeading(title = tr("Lo de hoy"), modifier = Modifier.weight(1f))
            if (loading) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onOpenCalendar) {
                    Text(tr("Ver agenda"))
                }
            }
        }

        if (visibleTasks.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(tr(if (tasks.isEmpty()) "Todavía no hay rutinas configuradas. Añádelas en Mi perro → Cuidados para ver aquí los cuidados reales." else "No hay cuidados programados para hoy. Puedes añadir rutinas en Mi perro → Cuidados."),
                    Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        } else {
            orderedVisibleTasks.forEach { task ->
                    val complete = isComplete(task)
                    val icon = when (task.action) {
                        "Paseo" -> Icons.Filled.DirectionsWalk
                        "Comida" -> Icons.Filled.Restaurant
                        "Medicación" -> Icons.Filled.Medication
                        "Higiene" -> Icons.Filled.Bathtub
                        else -> Icons.Filled.CheckCircle
                    }
                    val subtitle = listOf(
                        task.responsible.takeIf { it.isNotBlank() }?.let { "${tr("Responsable")}: $it" },
                        task.time.takeIf { it.isNotBlank() },
                        task.dose.takeIf { it.isNotBlank() }
                    ).filterNotNull().joinToString(" · ").ifBlank { "${tr(task.action)} · ${tr(task.frequency)}" }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = canEdit && task.id !in pendingIds) {
                                requestTaskToggle(task, !complete)
                            }.padding(horizontal = 13.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(44.dp).background(
                                when (task.action) {
                                    "Medicación" -> MaterialTheme.colorScheme.secondaryContainer
                                    "Comida" -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                }, RoundedCornerShape(14.dp)
                            ), contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(task.title, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis)
                            }
                            Checkbox(
                                checked = complete,
                                enabled = canEdit && task.id !in pendingIds,
                                onCheckedChange = { requestTaskToggle(task, it) }
                            )
                        }
                    }
                }
        }
        if (!upcomingTitle.isNullOrBlank() && !upcomingDate.isNullOrBlank()) {
            val upcomingLabel = runCatching {
                val date = LocalDate.parse(upcomingDate)
                val dayLabel = date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d", locale))
                    .replaceFirstChar { it.uppercase(locale) }
                val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, date)
                val relative = when (daysUntil) {
                    0L -> todayLabel
                    1L -> tomorrowLabel
                    else -> "$inLabel $daysUntil $daysLabel"
                }
                "$dayLabel · $relative"
            }.getOrDefault(upcomingDate)
            val upcomingDisplayLabel = listOf(
                upcomingLabel,
                upcomingTime?.takeIf { it.isNotBlank() }
            ).filterNotNull().joinToString(" · ")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PawlySectionHeading(title = tr("Próximamente"))
                Surface(
                    onClick = onUpcomingClick,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(42.dp).background(Color.White.copy(alpha = .72f), RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (upcomingType?.contains("vacun", ignoreCase = true) == true) Icons.Filled.HealthAndSafety else Icons.Filled.CalendarMonth,
                                null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(upcomingDisplayLabel, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelSmall)
                            Text(upcomingTitle, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }

        PawlySectionHeading(title = tr("Registrar rápido"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            HomeCareQuickAction(Icons.Filled.DirectionsWalk, tr("Paseo"), MaterialTheme.colorScheme.primaryContainer, {
                if (canEdit) {
                    walkError = null
                    showWalkDialog = true
                } else {
                    syncMessage = cannotRegisterCareMessage
                }
            }, Modifier.weight(1f))
            HomeCareQuickAction(Icons.Filled.MonitorWeight, tr("Peso"), MaterialTheme.colorScheme.secondaryContainer, onWeight, Modifier.weight(1f))
            HomeCareQuickAction(Icons.Filled.PhotoCamera, tr("Foto"), MaterialTheme.colorScheme.secondaryContainer, onPhoto, Modifier.weight(1f))
            HomeCareQuickAction(Icons.Filled.Restaurant, tr("Comida"), MaterialTheme.colorScheme.tertiaryContainer, {
                val task = visibleTasks.firstOrNull { it.action == "Comida" }
                if (task != null) requestTaskToggle(task, !isComplete(task)) else onFood()
            }, Modifier.weight(1f))
        }
        if (syncMessage.isNotBlank()) {
            Text(syncMessage, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall)
        }
        if (logs.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${tr("En casa de")} $petName", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    logs.take(3).forEach { log ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Box(Modifier.size(30.dp).background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("${log.actorName} · ${log.taskTitle}", style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(SimpleDateFormat("dd MMM · HH:mm", locale).format(Date(log.occurredAt)),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (log.distanceKm != null) {
                                    Text(String.format(locale, "%.2f km", log.distanceKm), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                                if (log.walker.isNotBlank()) {
                                    Text("${tr("Paseador/a")}: ${log.walker}", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (log.description.isNotBlank()) {
                                    Text(log.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                                if (log.note.isNotBlank()) {
                                    Text("${tr("Observaciones")}: ${log.note}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (log.mapUrl.isNotBlank()) {
                                    TextButton(onClick = {
                                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(log.mapUrl))) }
                                    }) {
                                        Icon(Icons.Default.Map, null)
                                        Spacer(Modifier.width(5.dp))
                                        Text(tr("Ver mapa"))
                                    }
                                }
                                if (log.photoUrls.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        log.photoUrls.take(4).forEach { photo ->
                                            AsyncImage(
                                                model = photo,
                                                contentDescription = tr("Fotografía del paseo"),
                                                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showWalkDialog) {
        AlertDialog(
            onDismissRequest = { if (!walkSaving) showWalkDialog = false },
            title = { Text(tr("Registrar paseo")) },
            text = {
                Column(
                    Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(
                        tr("Guarda los detalles de esta salida para consultar después la ruta y cómo fue."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = walkDistance,
                        onValueChange = { value -> walkDistance = value.filter { it.isDigit() || it == '.' || it == ',' } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("Distancia recorrida (km)")) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Route, null) }
                    )
                    OutlinedTextField(
                        value = walkMapUrl,
                        onValueChange = { walkMapUrl = it.trim().take(2048) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("Enlace del mapa (opcional)")) },
                        placeholder = { Text("https://maps.google.com/...") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Map, null) }
                    )
                    OutlinedTextField(
                        value = walkDescription,
                        onValueChange = { walkDescription = it.take(600) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("Descripción del paseo")) },
                        minLines = 2,
                        maxLines = 4
                    )
                    OutlinedTextField(
                        value = walkWalker,
                        onValueChange = { walkWalker = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("Paseador/a")) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, null) }
                    )
                    OutlinedTextField(
                        value = walkObservations,
                        onValueChange = { walkObservations = it.take(500) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("Observaciones")) },
                        minLines = 2,
                        maxLines = 4
                    )
                    OutlinedButton(
                        onClick = { walkPhotoPicker.launch("image/*") },
                        enabled = !walkSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (walkPhotoLocalPaths.isEmpty()) tr("Añadir fotografías") else "${walkPhotoLocalPaths.size} ${tr("fotografías seleccionadas")}")
                    }
                    if (walkPhotoLocalPaths.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            walkPhotoLocalPaths.forEach { photoPath ->
                                AsyncImage(
                                    model = photoPath,
                                    contentDescription = tr("Fotografía del paseo"),
                                    modifier = Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                        }
                    }
                    walkError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !walkSaving,
                    onClick = {
                        val parsedDistance = walkDistance.replace(',', '.').toDoubleOrNull()
                        val hasInvalidDistance = walkDistance.isNotBlank() &&
                            (parsedDistance == null || !parsedDistance.isFinite() || parsedDistance !in 0.01..1000.0)
                        val mapIsValid = walkMapUrl.isBlank() || runCatching {
                            val parsed = Uri.parse(walkMapUrl)
                            parsed.scheme in setOf("https", "http") && !parsed.host.isNullOrBlank()
                        }.getOrDefault(false)
                        when {
                            hasInvalidDistance -> walkError = tr("Introduce una distancia válida entre 0,01 y 1.000 km.")
                            !mapIsValid -> walkError = tr("El enlace del mapa debe ser una URL web válida.")
                            else -> {
                                walkSaving = true
                                walkError = null
                                val now = System.currentTimeMillis()
                                val localId = "local-walk-$now"
                                val localPhotoUrls = walkPhotoLocalPaths
                                val localEntry = CareLogEntry(
                                    id = localId,
                                    taskTitle = walkLabel,
                                    action = "Paseo",
                                    actorUid = "local",
                                    actorName = walkWalker.trim().ifBlank { youLabel },
                                    occurredAt = now,
                                    note = walkObservations.trim(),
                                    distanceKm = parsedDistance,
                                    mapUrl = walkMapUrl.trim(),
                                    description = walkDescription.trim(),
                                    walker = walkWalker.trim(),
                                    photoUrls = localPhotoUrls
                                )
                                logs = (listOf(localEntry) + logs).take(100)
                                PetCareLocalStore.saveCareLogs(context, petName, logs, storageScope)
                                syncMessage = savedLocalMessage
                                showWalkDialog = false

                                val photoUrisToUpload = walkPhotoUris
                                walkDistance = ""; walkMapUrl = ""; walkDescription = ""
                                walkWalker = ""; walkObservations = ""
                                walkPhotoUris = emptyList(); walkPhotoLocalPaths = emptyList()
                                repository?.let { repo ->
                                    scope.launch {
                                        var failedPhotoUploads = 0
                                        val remotePhotoUrls = mutableListOf<String>()
                                        val localFallbackPhotos = mutableListOf<String>()
                                        photoUrisToUpload.forEachIndexed { index, uri ->
                                            runCatching { repo.uploadCarePhoto(context, uri) }
                                                .onSuccess { remotePhotoUrls += it }
                                                .onFailure {
                                                    failedPhotoUploads++
                                                    localPhotoUrls.getOrNull(index)?.let { localFallbackPhotos += it }
                                                }
                                        }
                                        runCatching {
                                            repo.logCareAction(
                                                petName, "Paseo", "Paseo",
                                                note = localEntry.note,
                                                distanceKm = localEntry.distanceKm,
                                                mapUrl = localEntry.mapUrl,
                                                description = localEntry.description,
                                                walker = localEntry.walker,
                                                photoUrls = remotePhotoUrls
                                            )
                                        }.onSuccess { remoteEntry ->
                                            val visibleEntry = remoteEntry.copy(
                                                photoUrls = remotePhotoUrls + localFallbackPhotos
                                            )
                                            logs = (listOf(visibleEntry) + logs.filterNot { it.id == localId }).take(100)
                                            PetCareLocalStore.saveCareLogs(context, petName, logs, storageScope)
                                            syncMessage = if (failedPhotoUploads > 0) {
                                                tr("Paseo sincronizado; alguna fotografía queda solo en este dispositivo.")
                                            } else changesSyncedMessage
                                        }.onFailure {
                                            syncMessage = tr("Paseo guardado localmente; pendiente de sincronizar.")
                                        }
                                    }
                                }
                                walkSaving = false
                            }
                        }
                    }
                ) {
                    if (walkSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(tr("Guardar paseo"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWalkDialog = false }, enabled = !walkSaving) {
                    Text(tr("Cancelar"))
                }
            }
        )
    }

    pendingMedicationConfirmation?.let { (task, previousLog) ->
        AlertDialog(
            onDismissRequest = { pendingMedicationConfirmation = null },
            title = { Text(tr("¿Registrar otra toma?")) },
            text = {
                Text(
                    "${previousLog.actorName} ${tr("ya registró")} «${task.title}» ${tr("hoy")}. " +
                        tr("Comprueba que no se vaya a administrar dos veces.")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingMedicationConfirmation = null
                    toggleTask(task, true)
                }) { Text(tr("Registrar igualmente")) }
            },
            dismissButton = {
                TextButton(onClick = { pendingMedicationConfirmation = null }) {
                    Text(tr("No, ya está dada"))
                }
            }
        )
    }
}

@Composable
private fun HomeRoutineRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    iconBackground: Color,
    iconTint: Color
) {
    Row(
        Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).background(iconBackground, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Checkbox(checked = checked, onCheckedChange = onChecked)
    }
}


@Composable
private fun HomeCareQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(17.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp).background(tint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
