package com.pawly.app

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.TextStyle
import com.pawly.app.care.domain.CareTaskSchedule
import java.util.UUID
import com.pawly.app.care.data.PetCareLocalStore
import com.pawly.app.care.data.FirebasePetCareRepository
import com.pawly.app.care.data.PetShareInvitation
import com.pawly.app.care.data.FirebasePetProfileRepository
import kotlinx.coroutines.launch
import com.pawly.app.care.model.CareTask
import com.pawly.app.care.model.CareLogEntry
import com.pawly.app.care.model.PassportEntry
import com.pawly.app.care.CareReminderScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.pawly.app.care.export.PetCarePdfExporter
import com.google.firebase.firestore.FirebaseFirestoreException

@Composable
internal fun PetCareHubScreen(
    petName: String,
    petId: String,
    storageScope: String? = null,
    medicineDone: Boolean,
    onMedicineToggle: (Boolean) -> Unit,
    dates: Map<String, String>,
    onDateChange: (String, String?) -> Unit,
    elegantDesign: Boolean = false,
    sharedPetOwnerUid: String? = null,
    onOpenSharedPet: (PetShareInvitation) -> Unit = {},
    onCloseSharedPet: () -> Unit = {},
    onOpenDiary: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    diaryEntries: List<String> = emptyList(),
    calendarReminders: List<String> = emptyList(),
    initialSection: String? = null,
    initialPassportType: String? = null,
    initialCareAction: String? = null,
    onInitialActionConsumed: () -> Unit = {}
) {
    var section by remember { mutableStateOf("Todo") }
    var sectionMenuExpanded by remember { mutableStateOf(false) }
    var sharedPhotoUrl by remember(sharedPetOwnerUid, petId) { mutableStateOf<String?>(null) }
    LaunchedEffect(sharedPetOwnerUid, storageScope, petId) {
        section = if (sharedPetOwnerUid == null) "Todo" else "Pasaporte"
        val profileOwner = sharedPetOwnerUid ?: storageScope
        sharedPhotoUrl = if (profileOwner.isNullOrBlank()) null else runCatching {
            FirebasePetProfileRepository(ownerUid = profileOwner).loadProfile(petId)?.photoUrl
        }.getOrNull()
    }
    LaunchedEffect(initialSection, initialPassportType, initialCareAction) {
        if (!initialSection.isNullOrBlank()) {
            section = initialSection
            // A destination-only deep link is one-shot. Typed add actions are
            // consumed by their destination screen after the form has opened.
            if (initialPassportType.isNullOrBlank() && initialCareAction.isNullOrBlank()) {
                onInitialActionConsumed()
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (sharedPetOwnerUid != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Perfil compartido · ${petName}"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = onCloseSharedPet) { Text(tr("Volver a mi mascota")) }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (elegantDesign) {
                Box(Modifier.fillMaxWidth().height(190.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(25.dp))) {
                    AsyncImage(
                        model = sharedPhotoUrl ?: "https://images.unsplash.com/photo-1516734212186-a967f81ad0d7?w=1200&h=700&fit=crop",
                        contentDescription = "Perro en un entorno natural",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE610251F)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(tr("MI MASCOTA · BIENESTAR Y CUIDADOS"), color = Color.White.copy(alpha = .85f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                        Text(petName, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        Text(tr("Su salud, sus recuerdos y su día a día."), color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(tr("SU BIENESTAR, SIEMPRE A MANO"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text("El cuidado de ${petName.ifBlank { "tu mascota" }}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(tr("Agenda, salud y rutinas en un solo lugar."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.Pets, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                    }
                }
            }
            val sections = if (sharedPetOwnerUid == null) {
                listOf("Todo", "Agenda", "Pasaporte", "Cuidados", "Compartir", "Diario", "Citas")
            } else listOf("Pasaporte", "Cuidados")
            val selectedLabel = when (section) {
                "Pasaporte" -> tr("Salud")
                "Diario" -> tr("Diario")
                "Citas" -> tr("Citas")
                else -> tr(section)
            }
            val selectedIcon = when (section) {
                "Todo" -> Icons.Default.Pets
                "Agenda", "Citas" -> Icons.Default.CalendarMonth
                "Pasaporte" -> Icons.Default.HealthAndSafety
                "Diario" -> Icons.Default.AutoStories
                "Cuidados", "Compartir" -> Icons.Default.People
                else -> Icons.Default.Pets
            }
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { sectionMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp)
                ) {
                    Icon(selectedIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text(tr("Estás viendo"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(selectedLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.ExpandMore, contentDescription = tr("Cambiar sección"))
                }
                DropdownMenu(expanded = sectionMenuExpanded, onDismissRequest = { sectionMenuExpanded = false }) {
                    sections.forEach { item ->
                        val label = when (item) { "Pasaporte" -> tr("Salud"); "Diario" -> tr("Diario"); "Citas" -> tr("Citas"); else -> tr(item) }
                        val icon = when (item) {
                            "Todo" -> Icons.Default.Pets
                            "Agenda", "Citas" -> Icons.Default.CalendarMonth
                            "Pasaporte" -> Icons.Default.HealthAndSafety
                            "Diario" -> Icons.Default.AutoStories
                            "Cuidados", "Compartir" -> Icons.Default.People
                            else -> Icons.Default.Pets
                        }
                        DropdownMenuItem(
                            text = { Text(label, fontWeight = if (section == item) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailingIcon = { if (section == item) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                sectionMenuExpanded = false
                                when (item) { "Diario" -> onOpenDiary(); "Citas" -> onOpenCalendar(); else -> section = item }
                            }
                        )
                    }
                }
            }
        }
        when (section) {
            "Todo" -> PetTimelineSection(
                petName = petName,
                petId = petId,
                storageScope = storageScope,
                diaryEntries = diaryEntries,
                calendarReminders = calendarReminders,
                healthDates = dates,
                onOpenHealth = { section = "Pasaporte" },
                onOpenDiary = onOpenDiary,
                onOpenCalendar = onOpenCalendar
            )
            "Agenda" -> HealthScreen(
                dogName = petName,
                medicineDone = medicineDone,
                onMedicineToggle = onMedicineToggle,
                dates = dates,
                onDateChange = onDateChange
            )
            "Pasaporte" -> PetPassportSection(petName, storageScope, petId, sharedPetOwnerUid, initialPassportType, onInitialActionConsumed)
            "Cuidados" -> PetCarePlanSection(petName, storageScope, petId, sharedPetOwnerUid, initialCareAction, onInitialActionConsumed)
            else -> PetSharingSection(petName = petName, petId = petId, ownerUid = storageScope, onOpenSharedPet = onOpenSharedPet)
        }
    }
}

@Composable
private fun PetPassportSection(
    petName: String,
    storageScope: String?,
    petId: String,
    sharedPetOwnerUid: String? = null,
    initialType: String? = null,
    onInitialActionConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val localStorageScope = if (sharedPetOwnerUid != null) "${storageScope.orEmpty()}_shared_$sharedPetOwnerUid" else storageScope
    var entries by remember(petName, localStorageScope, petId) { mutableStateOf(PetCareLocalStore.loadEntries(context, petName, localStorageScope)) }
    val cloudOwnerUid = sharedPetOwnerUid ?: storageScope
    // La mascota propia usa la ruta privada users/{uid}/petCare/{petId}.
    // Solo las mascotas compartidas utilizan sharedPets/{ownerUid}_{petId}.
    val cloudRepository = remember(storageScope, sharedPetOwnerUid) {
        if (storageScope != null) runCatching {
            FirebasePetCareRepository(sharedPetOwnerUid = sharedPetOwnerUid)
        }.getOrNull() else null
    }
    val coroutineScope = rememberCoroutineScope()
    var syncMessage by remember(cloudOwnerUid) {
        mutableStateOf(if (storageScope == null) "Guardado en este dispositivo" else "Conectando con Firebase…")
    }
    var cloudLoading by remember(cloudOwnerUid) { mutableStateOf(cloudOwnerUid != null) }
    var canEdit by remember(cloudOwnerUid, petId) { mutableStateOf(sharedPetOwnerUid == null) }
    LaunchedEffect(cloudRepository, petId) {
        val repo = cloudRepository ?: return@LaunchedEffect
        cloudLoading = true
        try {
            repo.ensureSharedPet(petId, petName)
            canEdit = repo.canEditSharedPet(petId)
            val remoteEntries = repo.loadPassportEntries(petId)
            if (remoteEntries.isEmpty() && entries.isNotEmpty()) {
                repo.savePassportEntries(petId, entries)
                syncMessage = "Historial local sincronizado con tu cuenta"
            } else {
                entries = remoteEntries
                PetCareLocalStore.saveEntries(context, petName, remoteEntries, localStorageScope)
                syncMessage = "Sincronizado con tu cuenta"
            }
        } catch (error: Exception) {
            syncMessage = error.toCareSyncMessage()
        } finally {
            cloudLoading = false
        }
    }
    var showAdd by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<PassportEntry?>(null) }
    var selectedType by remember { mutableStateOf("Vacuna") }
    LaunchedEffect(initialType) {
        if (!initialType.isNullOrBlank()) {
            selectedType = initialType
            showAdd = true
            onInitialActionConsumed()
        }
    }
    var title by remember { mutableStateOf("") }
    var weightKg by remember { mutableFloatStateOf(0f) }
    val lastRecordedWeight = entries.asSequence().filter { it.type.equals("Peso", ignoreCase = true) }.sortedByDescending { it.date }.mapNotNull { entry ->
        val pattern = Regex("""\d+(?:[.,]\d+)?""")
        (pattern.find(entry.title)?.value ?: pattern.find(entry.detail)?.value)?.replace(",", ".")?.toFloatOrNull()
    }.firstOrNull()
    LaunchedEffect(initialType, lastRecordedWeight) {
        if (initialType == "Peso") {
            weightKg = lastRecordedWeight ?: 10f
            title = String.format(java.util.Locale.US, "%.1f", weightKg)
        }
    }
    var detail by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    val types = listOf("Vacuna", "Desparasitación", "Medicación", "Visita veterinaria", "Alergia", "Intervención", "Peso", "Microchip", "Seguro", "Veterinario habitual", "Contacto de emergencia", "Otro")

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Pets, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Pasaporte sanitario"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Text(tr("Historial de vacunas, tratamientos y visitas. Añade siempre datos confirmados por tu clínica veterinaria."), style = MaterialTheme.typography.bodyMedium)
                    Text(syncMessage, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    if (cloudLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        item {
            val emergencyTypes = setOf("Microchip", "Alergia", "Seguro", "Veterinario habitual", "Contacto de emergencia")
            val emergencyEntries = entries.filter { it.type in emergencyTypes }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(tr("Ficha de emergencia"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (emergencyEntries.isEmpty()) {
                        Text(tr("Añade microchip, alergias, seguro y veterinario habitual para tener los datos importantes a mano."), style = MaterialTheme.typography.bodySmall)
                    } else {
                        emergencyEntries.forEach { entry ->
                            Text("${entry.type}: ${entry.detail.ifBlank { entry.title }}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val message = buildString {
                                append("Ficha de emergencia de $petName\\n")
                                emergencyEntries.forEach { append("${it.type}: ${it.detail.ifBlank { it.title }}\\n") }
                            }
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Ficha de emergencia de $petName")
                                putExtra(Intent.EXTRA_TEXT, message)
                            }
                            runCatching { context.startActivity(Intent.createChooser(send, "Compartir ficha de emergencia")) }
                        },
                        enabled = emergencyEntries.isNotEmpty()
                    ) { Text(tr("Compartir ficha con veterinario o persona de confianza")) }
                }
            }
        }
        item {
            val weightPoints = entries
                .filter { it.type.equals("Peso", ignoreCase = true) }
                .mapNotNull { entry ->
                    val weightPattern = Regex("""\d+(?:[.,]\d+)?""")
                    val rawValue = weightPattern.find(entry.title)?.value ?: weightPattern.find(entry.detail)?.value
                    val kg = rawValue?.replace(",", ".")?.toFloatOrNull()
                    val day = runCatching { LocalDate.parse(entry.date.take(10)) }.getOrNull()
                    if (kg != null && kg > 0f && day != null) Triple(day, kg, entry.id) else null
                }
                .sortedBy { it.first }
                .takeLast(12)
            if (weightPoints.isNotEmpty()) {
                val lineColor = MaterialTheme.colorScheme.primary
                val guideColor = MaterialTheme.colorScheme.outlineVariant
                val surfaceColor = MaterialTheme.colorScheme.surface
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tr("Evolución del peso"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(tr("Últimas ${weightPoints.size} mediciones registradas"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                String.format(LocalLocale.current.platformLocale, "%.1f kg", weightPoints.last().second),
                                color = lineColor,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        val values = weightPoints.map { it.second }
                        val minWeight = values.minOrNull() ?: 0f
                        val maxWeight = values.maxOrNull() ?: minWeight
                        val range = (maxWeight - minWeight).coerceAtLeast(1f)
                        Canvas(Modifier.fillMaxWidth().height(118.dp)) {
                            val left = 10.dp.toPx()
                            val right = size.width - 10.dp.toPx()
                            val top = 12.dp.toPx()
                            val bottom = size.height - 12.dp.toPx()
                            val points = values.mapIndexed { index, value ->
                                val x = if (values.size == 1) (left + right) / 2f
                                    else left + (right - left) * index / (values.size - 1)
                                val y = bottom - ((value - minWeight) / range) * (bottom - top)
                                Offset(x, y)
                            }
                            drawLine(guideColor, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
                            points.zipWithNext().forEach { (a, b) ->
                                drawLine(lineColor, a, b, strokeWidth = 3.dp.toPx())
                            }
                            points.forEach { point ->
                                drawCircle(surfaceColor, radius = 6.dp.toPx(), center = point)
                                drawCircle(lineColor, radius = 4.dp.toPx(), center = point)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(weightPoints.first().first.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (weightPoints.size > 1) Text(weightPoints.last().first.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(tr("La gráfica utiliza únicamente registros de tipo Peso con fecha y valor numérico. No representa un objetivo veterinario."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Historial"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(tr("${entries.size} registros"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { showAdd = true }, enabled = !cloudLoading && canEdit) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text(tr("Añadir")) }
                    TextButton(onClick = {
                        runCatching { PetCarePdfExporter.sharePassport(context, petName, entries) }
                            .onFailure { syncMessage = "No se pudo preparar el PDF" }
                    }) { Text(tr("Compartir PDF")) }
                }
            }
        }
        if (entries.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Favorite, null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(tr("El historial empieza aquí"), fontWeight = FontWeight.SemiBold)
                        Text(tr("Registra su primera vacuna, revisión o tratamiento para tenerlo todo localizado."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        items(entries.sortedByDescending { it.date }, key = { it.id }) { entry ->
            Card(modifier = Modifier.fillMaxWidth().clickable(enabled = canEdit) {
                editingEntry = entry
                selectedType = entry.type
                title = entry.title
                detail = entry.detail
                date = entry.date
                if (entry.type.equals("Peso", true)) {
                    val raw = Regex("[0-9]+([.,][0-9]+)?").find(entry.title)?.value ?: Regex("[0-9]+([.,][0-9]+)?").find(entry.detail)?.value
                    weightKg = raw?.replace(",", ".")?.toFloatOrNull() ?: 10f
                }
                showAdd = true
            }) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.HealthAndSafety, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(entry.title, fontWeight = FontWeight.SemiBold)
                        Text(entry.type + " · " + entry.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        if (entry.detail.isNotBlank()) Text(entry.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (canEdit) Text(tr("Toca para editar"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        entries = entries.filterNot { it.id == entry.id }
                        PetCareLocalStore.saveEntries(context, petName, entries, localStorageScope)
                        cloudRepository?.let { repo ->
                            coroutineScope.launch {
                                runCatching { repo.deletePassportEntry(petId, entry.id) }
                                    .onSuccess { syncMessage = "Cambios sincronizados" }
                                    .onFailure { syncMessage = "No se pudo sincronizar la eliminación; conserva la copia local" }
                            }
                        }
                    }, enabled = canEdit) { Icon(Icons.Default.DeleteOutline, contentDescription = tr("Eliminar registro")) }
                }
            }
        }
        item {
            Text(tr("Este historial no sustituye a la cartilla oficial ni a la historia clínica de tu veterinario. Los documentos adjuntos y la exportación PDF se incorporarán en una fase posterior."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(tr("Nuevo registro")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        types.forEach { type ->
                            FilterChip(selected = selectedType == type, onClick = { selectedType = type; if (type == "Peso") { weightKg = lastRecordedWeight ?: 10f; title = String.format(java.util.Locale.US, "%.1f", weightKg) } }, label = { Text(type) })
                        }
                    }
                    if (selectedType == "Peso") {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(tr("Peso de tu mascota"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Box(Modifier.size(184.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        IconButton(onClick = { weightKg = (weightKg - 0.1f).coerceAtLeast(0.1f); title = String.format(java.util.Locale.US, "%.1f", weightKg) }) { Icon(Icons.Default.Remove, contentDescription = tr("Restar 100 gramos")) }
                                        Text(String.format(java.util.Locale.US, "%.1f", weightKg), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                                        IconButton(onClick = { weightKg = (weightKg + 0.1f).coerceAtMost(150f); title = String.format(java.util.Locale.US, "%.1f", weightKg) }) { Icon(Icons.Default.Add, contentDescription = tr("Añadir 100 gramos")) }
                                    }
                                    Text("kg", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(if (lastRecordedWeight != null) "Último registro: " + String.format(java.util.Locale.US, "%.1f", lastRecordedWeight) + " kg · ajusta la nueva medición" else tr("Primera medición · ajusta el valor con + y −"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        OutlinedTextField(title, { title = it }, label = { Text(tr("Nombre o concepto")) }, singleLine = true)
                    }
                    OutlinedTextField(date, { date = it }, label = { Text(tr("Fecha (AAAA-MM-DD)")) }, singleLine = true)
                    OutlinedTextField(detail, { detail = it }, label = { Text(tr("Notas, clínica o detalles")) }, minLines = 2)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cleanTitle = title.trim()
                    if (cleanTitle.isNotBlank() && runCatching { LocalDate.parse(date.trim()) }.isSuccess) {
                        val newEntry = PassportEntry(UUID.randomUUID().toString(), selectedType, cleanTitle.take(80), detail.trim().take(500), date.trim())
                        entries = entries + newEntry
                        PetCareLocalStore.saveEntries(context, petName, entries, localStorageScope)
                        cloudRepository?.let { repo ->
                            coroutineScope.launch {
                                runCatching { repo.savePassportEntry(petId, newEntry) }
                                    .onSuccess { syncMessage = "Registro guardado y sincronizado" }
                                    .onFailure { syncMessage = "Guardado localmente; pendiente de sincronizar" }
                            }
                        }
                        title = ""; detail = ""; date = LocalDate.now().toString(); selectedType = "Vacuna"; showAdd = false
                    }
                }, enabled = canEdit && title.isNotBlank() && runCatching { LocalDate.parse(date.trim()) }.isSuccess) { Text(tr("Guardar")) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(tr("Cancelar")) } }
        )
    }
}

@Composable
private fun PetCarePlanSection(
    petName: String,
    storageScope: String?,
    petId: String,
    sharedPetOwnerUid: String? = null,
    initialAction: String? = null,
    onInitialActionConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val localStorageScope = if (sharedPetOwnerUid != null) "${storageScope.orEmpty()}_shared_$sharedPetOwnerUid" else storageScope
    val today = remember { LocalDate.now().toString() }
    val todayDate = remember(today) { LocalDate.parse(today) }
    val weekdayLocale = PawlyI18n.locale(LocalPawlyLanguage.current)
    var tasks by remember(petName, localStorageScope, petId) {
        mutableStateOf(PetCareLocalStore.loadTasks(context, petName, localStorageScope).map {
            if (it.frequency == "Diario" && it.completed && it.completedOn != today) it.copy(completed = false, completedOn = "") else it
        })
    }
    var careLogs by remember(petName, localStorageScope, petId) {
        mutableStateOf(PetCareLocalStore.loadCareLogs(context, petName, localStorageScope))
    }
    val cloudOwnerUid = sharedPetOwnerUid ?: storageScope
    // La mascota propia usa la ruta privada users/{uid}/petCare/{petId}.
    // Solo las mascotas compartidas utilizan sharedPets/{ownerUid}_{petId}.
    val cloudRepository = remember(storageScope, sharedPetOwnerUid) {
        if (storageScope != null) runCatching {
            FirebasePetCareRepository(sharedPetOwnerUid = sharedPetOwnerUid)
        }.getOrNull() else null
    }
    val coroutineScope = rememberCoroutineScope()
    var syncMessage by remember(cloudOwnerUid) {
        mutableStateOf(if (storageScope == null) "Guardado en este dispositivo" else "Conectando con Firebase…")
    }
    var cloudLoading by remember(cloudOwnerUid) { mutableStateOf(cloudOwnerUid != null) }
    var canEdit by remember(cloudOwnerUid, petId) { mutableStateOf(storageScope.isNullOrBlank()) }
    LaunchedEffect(cloudRepository, petId) {
        val repo = cloudRepository ?: return@LaunchedEffect
        cloudLoading = true
        try {
            repo.ensureSharedPet(petId, petName)
            canEdit = repo.canEditSharedPet(petId)
            val remoteTasks = repo.loadCareTasks(petId).map {
                if (it.frequency == "Diario" && it.completed && it.completedOn != today) it.copy(completed = false, completedOn = "") else it
            }
            if (remoteTasks.isEmpty() && tasks.isNotEmpty()) {
                repo.saveCareTasks(petId, tasks)
                syncMessage = "Rutinas locales sincronizadas con tu cuenta"
            } else {
                tasks = remoteTasks
                PetCareLocalStore.saveTasks(context, petName, remoteTasks, localStorageScope)
                syncMessage = "Sincronizado con tu cuenta"
            }
            runCatching { repo.loadCareLogs(petId) }.onSuccess {
                careLogs = it
                PetCareLocalStore.saveCareLogs(context, petName, it, localStorageScope)
            }
        } catch (error: Exception) {
            syncMessage = error.toCareSyncMessage()
        } finally {
            cloudLoading = false
        }
    }
    var showAdd by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var responsible by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("Diario") }
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }
    var action by remember { mutableStateOf("Otro") }
    LaunchedEffect(initialAction) {
        if (!initialAction.isNullOrBlank()) {
            action = initialAction
            showAdd = true
            onInitialActionConsumed()
        }
    }
    var dose by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var reminderEnabled by remember { mutableStateOf(false) }
    var pendingMedicationConfirmation by remember { mutableStateOf<Pair<CareTask, CareLogEntry>?>(null) }

    fun saveTaskCompletion(task: CareTask, checked: Boolean) {
        val updated = task.copy(completed = checked, completedOn = if (checked) today else "")
        tasks = tasks.map { if (it.id == task.id) updated else it }
        PetCareLocalStore.saveTasks(context, petName, tasks, localStorageScope)
        if (checked) {
            val localLog = CareLogEntry(
                UUID.randomUUID().toString(), task.title, task.action, "local", "Tú",
                System.currentTimeMillis()
            )
            careLogs = (listOf(localLog) + careLogs).take(100)
            PetCareLocalStore.saveCareLogs(context, petName, careLogs, localStorageScope)
        }
        cloudRepository?.let { repo ->
            coroutineScope.launch {
                runCatching {
                    repo.saveCareTask(petId, updated)
                    if (checked) repo.logCareAction(petId, task.title, task.action)
                    repo.loadCareLogs(petId)
                }
                    .onSuccess { logs ->
                        if (checked) careLogs = logs
                        PetCareLocalStore.saveCareLogs(context, petName, careLogs, localStorageScope)
                        syncMessage = "Cambios sincronizados"
                    }
                    .onFailure { syncMessage = "Guardado localmente; pendiente de sincronizar" }
            }
        }
    }

    fun requestTaskToggle(task: CareTask, checked: Boolean) {
        if (!canEdit) return
        if (checked && task.action.equals("Medicación", ignoreCase = true)) {
            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            val duplicate = com.pawly.app.care.domain.CareDuplicateGuard.findRecentMedicationLog(careLogs, task, currentUid)
            if (duplicate != null) {
                pendingMedicationConfirmation = task to duplicate
                return
            }
        }
        saveTaskCompletion(task, checked)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.People, null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Organización de cuidados"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Text(tr("Anota las rutinas y quién suele encargarse para que la familia tenga un plan claro."), style = MaterialTheme.typography.bodyMedium)
                    Text(syncMessage, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    if (cloudLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Rutinas"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val dueToday = tasks.filter { CareTaskSchedule.isDueOn(it, todayDate) }
                    val completedToday = dueToday.count { CareTaskSchedule.isCompleteOn(it, todayDate) }
                    Text(
                        PawlyI18n.careRoutineProgress(completedToday, dueToday.size, LocalPawlyLanguage.current),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { showAdd = true }, enabled = !cloudLoading && canEdit) { Icon(Icons.Default.Add, null); Text(tr("Añadir")) }
                    TextButton(onClick = {
                        runCatching { PetCarePdfExporter.shareCarePlan(context, petName, tasks) }
                            .onFailure { syncMessage = "No se pudo preparar el PDF" }
                    }) { Text(tr("Compartir PDF")) }
                }
            }
        }
        if (tasks.isEmpty()) {
            item { Text(tr("Todavía no hay rutinas. Puedes añadir paseos, comidas, medicación o cualquier cuidado recurrente."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(tasks, key = { it.id }) { task ->
            Card {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = CareTaskSchedule.isCompleteOn(task, todayDate),
                        enabled = canEdit && CareTaskSchedule.isDueOn(task, todayDate),
                        onCheckedChange = { checked -> requestTaskToggle(task, checked) }
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(task.title, fontWeight = FontWeight.SemiBold)
                        val weekdayLabel = CareTaskSchedule.normalizeDays(task.daysOfWeek).joinToString(", ") { day ->
                            DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, weekdayLocale)
                                .replace(".", "").replaceFirstChar { it.uppercase(weekdayLocale) }
                        }
                        Text(
                            "${task.action} · ${task.frequency}" +
                                (weekdayLabel.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") +
                                " · Responsable: ${task.responsible.ifBlank { "Sin asignar" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (task.dose.isNotBlank() || task.time.isNotBlank()) {
                            Text(listOf(task.dose, task.time).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    TextButton(enabled = canEdit, onClick = {
                        tasks = tasks.filterNot { it.id == task.id }
                        PetCareLocalStore.saveTasks(context, petName, tasks, localStorageScope)
                        CareReminderScheduler.cancel(context, petName, task.title)
                        cloudRepository?.let { repo ->
                            coroutineScope.launch {
                                runCatching { repo.deleteCareTask(petId, task.id) }
                                    .onSuccess { syncMessage = "Cambios sincronizados" }
                                    .onFailure { syncMessage = "No se pudo sincronizar la eliminación; conserva la copia local" }
                            }
                        }
                    }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar rutina") }
                }
            }
        }
        if (careLogs.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Últimos cuidados registrados"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(tr("Así podéis comprobar quién ha hecho cada paseo, comida o toma."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            items(careLogs.take(10), key = { "care-log-${it.id}" }) { log ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(log.taskTitle, fontWeight = FontWeight.SemiBold)
                            Text("${log.action} · ${log.actorName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            SimpleDateFormat("dd/MM HH:mm", LocalLocale.current.platformLocale).format(Date(log.occurredAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    pendingMedicationConfirmation?.let { (task, previousLog) ->
        AlertDialog(
            onDismissRequest = { pendingMedicationConfirmation = null },
            title = { Text(tr("¿Registrar otra toma?")) },
            text = {
                Text(tr("${previousLog.actorName} ya registró «${task.title}» hoy. Comprueba que no se vaya a administrar dos veces."))
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingMedicationConfirmation = null
                    saveTaskCompletion(task, true)
                }) { Text(tr("Registrar igualmente")) }
            },
            dismissButton = {
                TextButton(onClick = { pendingMedicationConfirmation = null }) {
                    Text(tr("No, ya está dada"))
                }
            }
        )
    }
    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(tr("Añadir rutina")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text(tr("Cuidado (paseo, comida…)")) }, singleLine = true)
                    OutlinedTextField(responsible, { responsible = it }, label = { Text(tr("Responsable")) }, singleLine = true)
                    Text(tr("Tipo de cuidado"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Paseo", "Comida", "Medicación", "Higiene", "Otro").forEach { item ->
                            FilterChip(selected = action == item, onClick = { action = item }, label = { Text(tr(item)) })
                        }
                    }
                    if (action == "Medicación") {
                        OutlinedTextField(dose, { dose = it }, label = { Text(tr("Dosis (ej. 1 comprimido)")) }, singleLine = true)
                        OutlinedTextField(time, { time = it }, label = { Text(tr("Hora del aviso (HH:mm)")) }, singleLine = true)
                        if (frequency != "Puntual") {
                            val weeklyReminder = frequency == "Semanal"
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        tr(if (weeklyReminder) "Avisos en los días seleccionados" else "Aviso diario"),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        tr(
                                            if (weeklyReminder) "Crea avisos locales a esta hora en cada día seleccionado."
                                            else "Crea un recordatorio local a esta hora."
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                androidx.compose.material3.Switch(
                                    checked = reminderEnabled,
                                    onCheckedChange = { reminderEnabled = it }
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Diario", "Semanal", "Puntual").forEach { item ->
                            FilterChip(
                                selected = frequency == item,
                                onClick = {
                                    frequency = item
                                    selectedDays = if (item == "Semanal") {
                                        selectedDays.ifEmpty { setOf(LocalDate.now().dayOfWeek.value) }
                                    } else emptySet()
                                    if (item == "Puntual") reminderEnabled = false
                                },
                                label = { Text(tr(item)) }
                            )
                        }
                    }
                    if (frequency == "Semanal") {
                        Text(tr("Días de la semana"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            (1..7).forEach { day ->
                                val label = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, weekdayLocale)
                                    .replace(".", "").replaceFirstChar { it.uppercase(weekdayLocale) }
                                FilterChip(
                                    selected = day in selectedDays,
                                    onClick = {
                                        selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                        if (selectedDays.isEmpty()) {
                            Text(
                                tr("Selecciona al menos un día para la rutina semanal."),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        } else {
                            val selectedDaysLabel = CareTaskSchedule.normalizeDays(selectedDays.toList()).joinToString(", ") { day ->
                                DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, weekdayLocale)
                                    .replace(".", "").replaceFirstChar { it.uppercase(weekdayLocale) }
                            }
                            Text(
                                tr("Se repetirá: ") + selectedDaysLabel,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank()) {
                        val newTask = CareTask(
                            UUID.randomUUID().toString(), title.trim().take(80), responsible.trim().take(60),
                            frequency, false, dose.trim().take(80), time.trim().take(5), "", action,
                            daysOfWeek = if (frequency == "Semanal") CareTaskSchedule.normalizeDays(selectedDays.toList()) else emptyList()
                        )
                        tasks = tasks + newTask
                        PetCareLocalStore.saveTasks(context, petName, tasks, localStorageScope)
                        if (action == "Medicación" && reminderEnabled) {
                            val scheduled = when (frequency) {
                                "Semanal" -> CareReminderScheduler.scheduleWeekly(
                                    context, petName, newTask.title, time.trim(), newTask.daysOfWeek
                                )
                                "Diario" -> CareReminderScheduler.scheduleDaily(context, petName, newTask.title, time.trim())
                                else -> false
                            }
                            syncMessage = when {
                                !scheduled -> "Revisa la hora y los días programados."
                                frequency == "Semanal" -> "Recordatorio en días programados."
                                else -> "Recordatorio diario programado."
                            }
                        }
                        cloudRepository?.let { repo ->
                            coroutineScope.launch {
                                runCatching { repo.saveCareTask(petId, newTask) }
                                    .onSuccess { syncMessage = "Rutina guardada y sincronizada" }
                                    .onFailure { syncMessage = "Guardado localmente; pendiente de sincronizar" }
                            }
                        }
                        title = ""; responsible = ""; frequency = "Diario"; selectedDays = emptySet(); action = "Otro"
                        dose = ""; time = ""; reminderEnabled = false; showAdd = false
                    }
                }, enabled = canEdit && title.isNotBlank() &&
                    (frequency != "Semanal" || selectedDays.isNotEmpty()) &&
                    (!reminderEnabled || time.matches(Regex("\\d{2}:\\d{2}"))) &&
                    (!reminderEnabled || action == "Medicación") &&
                    (!reminderEnabled || frequency in setOf("Diario", "Semanal"))) { Text(tr("Guardar")) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(tr("Cancelar")) } }
        )
    }
}


private fun Throwable.toCareSyncMessage(): String {
    val firestoreError = this as? FirebaseFirestoreException
    return when (firestoreError?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Firebase ha rechazado el acceso. Comprueba las reglas de Firestore y los permisos de esta cuenta."
        FirebaseFirestoreException.Code.UNAUTHENTICATED ->
            "La sesión de Firebase no está activa. Vuelve a iniciar sesión para sincronizar."
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "No se puede contactar ahora con Firebase. Se conserva la copia local; inténtalo de nuevo."
        FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            "Firestore necesita una configuración adicional (posiblemente un índice). Revisa la consola de Firebase."
        else -> localizedMessage?.takeIf { it.isNotBlank() }?.let { "No se pudo sincronizar: $it" }
            ?: "No se pudo sincronizar con Firebase. Se conserva la copia local."
    }
}
