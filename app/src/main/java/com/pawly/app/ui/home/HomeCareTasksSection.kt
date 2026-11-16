package com.pawly.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onUpcomingClick: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
        mutableStateOf(if (storageScope.isNullOrBlank()) "Guardado en este dispositivo" else "Cargando cuidados…")
    }
    var loading by remember(petName, storageScope) { mutableStateOf(!storageScope.isNullOrBlank()) }
    var pendingIds by remember(petName, storageScope) { mutableStateOf(emptySet<String>()) }
    var pendingMedicationConfirmation by remember(petName, storageScope) { mutableStateOf<Pair<CareTask, CareLogEntry>?>(null) }

    LaunchedEffect(petName, storageScope, repository) {
        val repo = repository ?: run { loading = false; return@LaunchedEffect }
        loading = true
        runCatching {
            repo.ensureSharedPet(petName, petName)
            canEdit = repo.canEditSharedPet(petName)
            val remoteTasks = repo.loadCareTasks(petName)
            if (remoteTasks.isEmpty() && tasks.isNotEmpty()) {
                repo.saveCareTasks(petName, tasks)
                syncMessage = "Rutinas locales sincronizadas"
            } else {
                tasks = remoteTasks
                PetCareLocalStore.saveTasks(context, petName, remoteTasks, storageScope)
                syncMessage = "Cuidados sincronizados"
            }
            logs = repo.loadCareLogs(petName)
            PetCareLocalStore.saveCareLogs(context, petName, logs, storageScope)
        }.onFailure {
            syncMessage = "Sin conexión: se conserva la copia local"
        }
        loading = false
    }

    val visibleTasks = tasks.filter { task ->
        task.frequency != "Puntual" || !task.completed || task.completedOn == todayKey
    }
    fun isComplete(task: CareTask): Boolean {
        if (!task.completed) return false
        val completedDate = runCatching { LocalDate.parse(task.completedOn) }.getOrNull()
            ?: return task.frequency == "Puntual"
        return when (task.frequency) {
            "Diario" -> completedDate == today
            "Semanal" -> {
                val fields = WeekFields.ISO
                completedDate.get(fields.weekOfWeekBasedYear()) == today.get(fields.weekOfWeekBasedYear()) &&
                    completedDate.get(fields.weekBasedYear()) == today.get(fields.weekBasedYear())
            }
            else -> task.frequency == "Puntual" || completedDate == today
        }
    }
    val completedCount = if (tasks.isNotEmpty()) visibleTasks.count(::isComplete)
        else listOf(walkDone, medicineDone).count { it }
    val totalCount = if (tasks.isNotEmpty()) visibleTasks.size else 2
    val progress = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount
    val nextCareLabel = if (tasks.isNotEmpty()) visibleTasks.firstOrNull { !isComplete(it) }?.title
        else if (!walkDone) "Paseo" else if (!medicineDone) "Medicación" else null

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
                    logs = remoteLogs
                    PetCareLocalStore.saveCareLogs(context, petName, remoteLogs, storageScope)
                }
                syncMessage = "Cambios sincronizados"
            }.onFailure {
                syncMessage = "Guardado localmente; sincronización pendiente"
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
                    Text("HOY PARA ${petName.uppercase(Locale.getDefault())}",
                        color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("$completedCount de $totalCount hechas",
                        color = Color.White, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold)
                    Text(
                        when {
                            totalCount == 0 || completedCount == totalCount -> "¡Todo listo! Qué buen equipo."
                            completedCount == totalCount - 1 && !nextCareLabel.isNullOrBlank() -> "Solo falta $nextCareLabel."
                            else -> "Aún quedan ${totalCount - completedCount} cuidados por completar."
                        },
                        color = Color.White.copy(alpha = .86f), style = MaterialTheme.typography.bodySmall
                    )
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(68.dp)) {
                    CircularProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFFE4EBC1),
                        trackColor = Color.White.copy(alpha = .18f),
                        strokeWidth = 7.dp
                    )
                    Text("$completedCount/$totalCount", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lo de hoy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            if (loading) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onOpenCalendar) {
                    Text("Ver agenda")
                }
            }
        }

        if (tasks.isEmpty()) {
            HomeRoutineRow(
                Icons.Filled.DirectionsWalk, "Paseo diario", "Un rato para explorar y olfatear",
                walkDone, onWalkToggle, Color(0xFFE2F1E6), Color(0xFF287A4B)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HomeRoutineRow(
                Icons.Filled.Medication, "Medicación", "Comprueba la toma de hoy",
                medicineDone, onMedicineToggle, Color(0xFFFCE8E8), Color(0xFFB54F5C)
            )
        } else if (visibleTasks.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text("No hay cuidados programados para hoy. Puedes añadir rutinas en Mi perro → Cuidados.",
                    Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        } else {
            visibleTasks.sortedWith(compareBy<CareTask>({ it.time.ifBlank { "99:99" } }, { it.title }))
                .forEach { task ->
                    val complete = isComplete(task)
                    val icon = when (task.action) {
                        "Paseo" -> Icons.Filled.DirectionsWalk
                        "Comida" -> Icons.Filled.Restaurant
                        "Medicación" -> Icons.Filled.Medication
                        "Higiene" -> Icons.Filled.Bathtub
                        else -> Icons.Filled.CheckCircle
                    }
                    val subtitle = listOf(
                        task.responsible.takeIf { it.isNotBlank() }?.let { "Responsable: $it" },
                        task.time.takeIf { it.isNotBlank() },
                        task.dose.takeIf { it.isNotBlank() }
                    ).filterNotNull().joinToString(" · ").ifBlank { "${task.action} · ${task.frequency}" }
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
                                    "Medicación" -> Color(0xFFF3DCCF)
                                    "Comida" -> Color(0xFFE4EBC1)
                                    else -> Color(0xFFDDEBE3)
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
                val dayLabel = date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d", Locale.getDefault()))
                    .replaceFirstChar { it.uppercase(Locale.getDefault()) }
                val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, date)
                val relative = when (daysUntil) {
                    0L -> "hoy"
                    1L -> "mañana"
                    else -> "dentro de $daysUntil días"
                }
                "$dayLabel · $relative"
            }.getOrDefault(upcomingDate)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Próximamente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(
                    onClick = onUpcomingClick,
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF3DCCF)
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
                                tint = Color(0xFF315A45)
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(upcomingLabel, color = Color(0xFF67574E), style = MaterialTheme.typography.labelSmall)
                            Text(upcomingTitle, color = Color(0xFF263A30), fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFF315A45))
                    }
                }
            }
        }

        Text("Registrar rápido", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            HomeCareQuickAction(Icons.Filled.DirectionsWalk, "Paseo", Color(0xFFE2F1E6), {
                val task = visibleTasks.firstOrNull { it.action == "Paseo" }
                if (task != null) toggleTask(task, !isComplete(task)) else onWalkToggle(!walkDone)
            }, Modifier.weight(1f))
            HomeCareQuickAction(Icons.Filled.MonitorWeight, "Peso", Color(0xFFDDF1EE), onWeight, Modifier.weight(1f))
            HomeCareQuickAction(Icons.Filled.PhotoCamera, "Foto", Color(0xFFFFE9DF), onPhoto, Modifier.weight(1f))
            HomeCareQuickAction(Icons.Filled.Restaurant, "Comida", Color(0xFFE4EBC1), {
                val task = visibleTasks.firstOrNull { it.action == "Comida" }
                if (task != null) toggleTask(task, !isComplete(task)) else onFood()
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
                    Text("En casa de $petName", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    logs.take(3).forEach { log ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Box(Modifier.size(30.dp).background(Color(0xFFE4EBC1), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("${log.actorName} · ${log.taskTitle}", style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()).format(Date(log.occurredAt)),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    pendingMedicationConfirmation?.let { (task, previousLog) ->
        AlertDialog(
            onDismissRequest = { pendingMedicationConfirmation = null },
            title = { Text("¿Registrar otra toma?") },
            text = {
                Text(
                    "${previousLog.actorName} ya registró «${task.title}» hoy. " +
                        "Comprueba que no se vaya a administrar dos veces."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingMedicationConfirmation = null
                    toggleTask(task, true)
                }) { Text("Registrar igualmente") }
            },
            dismissButton = {
                TextButton(onClick = { pendingMedicationConfirmation = null }) {
                    Text("No, ya está dada")
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
