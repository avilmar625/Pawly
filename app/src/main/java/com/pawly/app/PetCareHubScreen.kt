package com.pawly.app

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

private data class PassportEntry(
    val id: String,
    val type: String,
    val title: String,
    val detail: String,
    val date: String
)

private data class CareTask(
    val id: String,
    val title: String,
    val responsible: String,
    val frequency: String,
    val completed: Boolean
)

private object PetCareLocalStore {
    private fun key(prefix: String, petName: String) =
        "v16_${prefix}_${petName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")}"

    fun loadEntries(context: Context, petName: String): List<PassportEntry> = runCatching {
        val raw = context.getSharedPreferences("pawly_local", Context.MODE_PRIVATE)
            .getString(key("passport", petName), "[]") ?: "[]"
        val json = JSONArray(raw)
        List(json.length()) { i ->
            val row = json.getJSONObject(i)
            PassportEntry(
                id = row.optString("id"),
                type = row.optString("type"),
                title = row.optString("title"),
                detail = row.optString("detail"),
                date = row.optString("date")
            )
        }
    }.getOrDefault(emptyList())

    fun saveEntries(context: Context, petName: String, entries: List<PassportEntry>) {
        val json = JSONArray()
        entries.forEach { entry ->
            json.put(JSONObject().put("id", entry.id).put("type", entry.type)
                .put("title", entry.title).put("detail", entry.detail).put("date", entry.date))
        }
        context.getSharedPreferences("pawly_local", Context.MODE_PRIVATE).edit()
            .putString(key("passport", petName), json.toString()).apply()
    }

    fun loadTasks(context: Context, petName: String): List<CareTask> = runCatching {
        val raw = context.getSharedPreferences("pawly_local", Context.MODE_PRIVATE)
            .getString(key("care", petName), "[]") ?: "[]"
        val json = JSONArray(raw)
        List(json.length()) { i ->
            val row = json.getJSONObject(i)
            CareTask(
                id = row.optString("id"),
                title = row.optString("title"),
                responsible = row.optString("responsible"),
                frequency = row.optString("frequency"),
                completed = row.optBoolean("completed")
            )
        }
    }.getOrDefault(emptyList())

    fun saveTasks(context: Context, petName: String, tasks: List<CareTask>) {
        val json = JSONArray()
        tasks.forEach { task ->
            json.put(JSONObject().put("id", task.id).put("title", task.title)
                .put("responsible", task.responsible).put("frequency", task.frequency)
                .put("completed", task.completed))
        }
        context.getSharedPreferences("pawly_local", Context.MODE_PRIVATE).edit()
            .putString(key("care", petName), json.toString()).apply()
    }
}

@Composable
internal fun PetCareHubScreen(
    petName: String,
    medicineDone: Boolean,
    onMedicineToggle: (Boolean) -> Unit,
    dates: Map<String, String>,
    onDateChange: (String, String?) -> Unit
) {
    var section by remember { mutableStateOf("Agenda") }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("El cuidado de ${petName.ifBlank { "tu mascota" }}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Todo lo importante, en un mismo lugar.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Agenda", "Pasaporte", "Cuidados").forEach { item ->
                    FilterChip(
                        selected = section == item,
                        onClick = { section = item },
                        label = { Text(item) },
                        leadingIcon = {
                            Icon(
                                when (item) {
                                    "Agenda" -> Icons.Default.CalendarMonth
                                    "Pasaporte" -> Icons.Default.HealthAndSafety
                                    else -> Icons.Default.People
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }
        when (section) {
            "Agenda" -> HealthScreen(
                dogName = petName,
                medicineDone = medicineDone,
                onMedicineToggle = onMedicineToggle,
                dates = dates,
                onDateChange = onDateChange
            )
            "Pasaporte" -> PetPassportSection(petName)
            else -> PetCarePlanSection(petName)
        }
    }
}

@Composable
private fun PetPassportSection(petName: String) {
    val context = LocalContext.current
    var entries by remember(petName) { mutableStateOf(PetCareLocalStore.loadEntries(context, petName)) }
    var showAdd by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf("Vacuna") }
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    val types = listOf("Vacuna", "Desparasitación", "Medicación", "Visita veterinaria", "Alergia", "Intervención", "Peso", "Otro")

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
                        Text("Pasaporte sanitario", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Text("Un historial privado para tener a mano vacunas, tratamientos y visitas. Añade siempre datos confirmados por tu clínica veterinaria.", style = MaterialTheme.typography.bodyMedium)
                    Text("Guardado en este dispositivo", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Historial", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${entries.size} registros", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Añadir") }
            }
        }
        if (entries.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Favorite, null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("El historial empieza aquí", fontWeight = FontWeight.SemiBold)
                        Text("Registra su primera vacuna, revisión o tratamiento para tenerlo todo localizado.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        items(entries.sortedByDescending { it.date }, key = { it.id }) { entry ->
            Card {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.HealthAndSafety, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(entry.title, fontWeight = FontWeight.SemiBold)
                        Text(entry.type + " · " + entry.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        if (entry.detail.isNotBlank()) Text(entry.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = {
                        entries = entries.filterNot { it.id == entry.id }
                        PetCareLocalStore.saveEntries(context, petName, entries)
                    }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar registro") }
                }
            }
        }
        item {
            Text("Este historial no sustituye a la cartilla oficial ni a la historia clínica de tu veterinario. Los documentos adjuntos y la exportación PDF se incorporarán en una fase posterior.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Nuevo registro") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        types.forEach { type ->
                            FilterChip(selected = selectedType == type, onClick = { selectedType = type }, label = { Text(type) })
                        }
                    }
                    OutlinedTextField(title, { title = it }, label = { Text("Nombre o concepto") }, singleLine = true)
                    OutlinedTextField(date, { date = it }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true)
                    OutlinedTextField(detail, { detail = it }, label = { Text("Notas, clínica o detalles") }, minLines = 2)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cleanTitle = title.trim()
                    if (cleanTitle.isNotBlank() && runCatching { LocalDate.parse(date.trim()) }.isSuccess) {
                        entries = entries + PassportEntry(UUID.randomUUID().toString(), selectedType, cleanTitle.take(80), detail.trim().take(500), date.trim())
                        PetCareLocalStore.saveEntries(context, petName, entries)
                        title = ""; detail = ""; date = LocalDate.now().toString(); selectedType = "Vacuna"; showAdd = false
                    }
                }, enabled = title.isNotBlank() && runCatching { LocalDate.parse(date.trim()) }.isSuccess) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun PetCarePlanSection(petName: String) {
    val context = LocalContext.current
    var tasks by remember(petName) { mutableStateOf(PetCareLocalStore.loadTasks(context, petName)) }
    var showAdd by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var responsible by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("Diario") }

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
                        Text("Organización de cuidados", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Text("Anota las rutinas y quién suele encargarse para que la familia tenga un plan claro.", style = MaterialTheme.typography.bodyMedium)
                    Text("Por ahora se guarda solo en este dispositivo. La invitación y sincronización entre cuidadores requieren una fase de backend.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rutinas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${tasks.count { it.completed }} de ${tasks.size} marcadas como hechas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null); Text("Añadir") }
            }
        }
        if (tasks.isEmpty()) {
            item { Text("Todavía no hay rutinas. Puedes añadir paseos, comidas, medicación o cualquier cuidado recurrente.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(tasks, key = { it.id }) { task ->
            Card {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = task.completed,
                        onCheckedChange = { checked ->
                            tasks = tasks.map { if (it.id == task.id) it.copy(completed = checked) else it }
                            PetCareLocalStore.saveTasks(context, petName, tasks)
                        }
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(task.title, fontWeight = FontWeight.SemiBold)
                        Text("${task.frequency} · Responsable: ${task.responsible.ifBlank { "Sin asignar" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = {
                        tasks = tasks.filterNot { it.id == task.id }
                        PetCareLocalStore.saveTasks(context, petName, tasks)
                    }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar rutina") }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Añadir rutina") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Cuidado (paseo, comida…)") }, singleLine = true)
                    OutlinedTextField(responsible, { responsible = it }, label = { Text("Responsable") }, singleLine = true)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Diario", "Semanal", "Puntual").forEach { item ->
                            FilterChip(selected = frequency == item, onClick = { frequency = item }, label = { Text(item) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank()) {
                        tasks = tasks + CareTask(UUID.randomUUID().toString(), title.trim().take(80), responsible.trim().take(60), frequency, false)
                        PetCareLocalStore.saveTasks(context, petName, tasks)
                        title = ""; responsible = ""; frequency = "Diario"; showAdd = false
                    }
                }, enabled = title.isNotBlank()) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancelar") } }
        )
    }
}
