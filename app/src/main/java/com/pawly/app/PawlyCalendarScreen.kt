package com.pawly.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import java.util.Calendar
import java.util.Date
import org.json.JSONObject
import com.pawly.app.care.CareReminderScheduler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class AgendaReminder(val id: String, val title: String, val date: Long, val category: String, val raw: String = "")

@Composable
internal fun PawlyCalendarScreen(
    reminders: List<String>,
    onRemindersChange: (List<String>) -> Unit,
    repository: FirebaseCommunityRepository?,
    healthDates: Map<String, String> = emptyMap(),
    diaryEntries: List<String> = emptyList(),
    dogName: String = "tu mascota"
) {
    val context = LocalContext.current
    var events by remember { mutableStateOf(emptyList<PawlyEvent>()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var monthAnchor by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showAdd by remember { mutableStateOf(false) }
    val eventLoadErrorText = tr("No se pudieron cargar los eventos.")
    LaunchedEffect(repository) {
        if (repository != null) runCatching { repository.loadEvents() }
            .onSuccess { events = it }
            .onFailure { loadError = it.localizedMessage ?: eventLoadErrorText }
    }
    val month = remember(monthAnchor) { Calendar.getInstance().apply { timeInMillis = monthAnchor; set(Calendar.DAY_OF_MONTH, 1) } }
    val monthYear = month.get(Calendar.YEAR)
    val monthIndex = month.get(Calendar.MONTH)
    val firstWeekday = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)
    val parsedReminders = remember(reminders) { reminders.mapNotNull { runCatching {
        val j = JSONObject(it)
        AgendaReminder(j.optString("id"), j.optString("title"), j.optLong("date"), j.optString("category", "Personal"), it)
    }.getOrNull() } }
    fun dayKey(value: Long): String = Calendar.getInstance().apply { timeInMillis = value }.let {
        "${it.get(Calendar.YEAR)}-${it.get(Calendar.MONTH)}-${it.get(Calendar.DAY_OF_MONTH)}"
    }
    val healthReminders = remember(healthDates) {
        healthDates.mapNotNull { (name, value) ->
            runCatching {
                val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT)
                    .parse(value.replace("|", " "))?.time ?: return@runCatching null
                AgendaReminder("health-$name-$date", name, date, "Cuidado")
            }.getOrNull()
        }
    }
    val diaryMemories = remember(diaryEntries) {
        diaryEntries.mapIndexedNotNull { index, raw ->
            runCatching {
                val json = JSONObject(raw)
                if (json.optInt("pawlyDiaryVersion") != 1) return@runCatching null
                AgendaReminder("diary-$index", json.optString("title").ifBlank { "Recuerdo" }, json.optLong("date"), "Recuerdo")
            }.getOrNull()
        }
    }
    val selectedKey = dayKey(selectedDate)
    val eventsForDay = events.filter { it.startAt > 0 && dayKey(it.startAt) == selectedKey }
    val remindersForDay = (parsedReminders + healthReminders + diaryMemories).filter { dayKey(it.date) == selectedKey }
    val daysWithItems = (events.mapNotNull { it.startAt.takeIf { v -> v > 0 } } + (parsedReminders + healthReminders + diaryMemories).map { it.date }).map(::dayKey).toSet()
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("TU AGENDA PAWLY"), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.7.sp)
                Text(tr("Calendario"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                Text(tr("Eventos, planes y recordatorios para que nada importante se os pase."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { val target = Calendar.getInstance().apply { timeInMillis = monthAnchor; add(Calendar.MONTH, -1) }; monthAnchor = target.timeInMillis; selectedDate = target.timeInMillis }) { Icon(Icons.Default.ChevronLeft, tr("Mes anterior")) }
                        Text(java.text.DateFormatSymbols.getInstance().let { it.months[monthIndex].replaceFirstChar { ch -> ch.uppercase() } } + " $monthYear", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = { val target = Calendar.getInstance().apply { timeInMillis = monthAnchor; add(Calendar.MONTH, 1) }; monthAnchor = target.timeInMillis; selectedDate = target.timeInMillis }) { Icon(Icons.Default.ChevronRight, tr("Mes siguiente")) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf("L", "M", "X", "J", "V", "S", "D").forEach { Text(it, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                    }
                    val totalCells = ((firstWeekday + daysInMonth + 6) / 7) * 7
                    for (week in 0 until totalCells / 7) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (column in 0..6) {
                                val day = week * 7 + column - firstWeekday + 1
                                if (day in 1..daysInMonth) {
                                    val cellDate = Calendar.getInstance().apply { set(monthYear, monthIndex, day, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                                    val key = dayKey(cellDate)
                                    val selected = key == selectedKey
                                    Column(
                                        Modifier.weight(1f).height(43.dp).clip(RoundedCornerShape(12.dp))
                                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                            .clickable { selectedDate = cellDate; if (key != dayKey(monthAnchor)) monthAnchor = cellDate }
                                            .padding(vertical = 3.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(day.toString(), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                        if (key in daysWithItems) Box(Modifier.size(5.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary))
                                    }
                                } else Spacer(Modifier.weight(1f).height(43.dp))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Seleccionado: ") + java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(Date(selectedDate)), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = { selectedDate = System.currentTimeMillis(); monthAnchor = selectedDate }) { Text(tr("Hoy")) }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Tu día"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(tr("Eventos, cuidados y recuerdos de esta fecha"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text(tr("Añadir")) }
            }
        }
        if (loadError != null) item { Text(loadError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (eventsForDay.isEmpty() && remindersForDay.isEmpty()) item {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    Text(tr("No hay nada apuntado para este día"), fontWeight = FontWeight.SemiBold)
                    Text(tr("Puedes añadir un recordatorio personal o consultar los eventos de la Comunidad."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(eventsForDay, key = { "event-" + it.id }) { event ->
            AgendaRow(Icons.Default.Event, event.title, "${event.dateText} · ${event.location}", MaterialTheme.colorScheme.primary)
        }
        items(remindersForDay, key = { "reminder-" + it.id }) { reminder ->
            if (reminder.raw.isNotBlank()) {
                AgendaReminderRow(reminder, onDelete = {
                    onRemindersChange(reminders.filterNot { raw -> runCatching { JSONObject(raw).optString("id") == reminder.id }.getOrDefault(false) })
                    CareReminderScheduler.cancel(context, dogName, "Agenda: ${reminder.title}")
                })
            } else {
                AgendaRow(if (reminder.category == "Recuerdo") Icons.Default.AutoStories else Icons.Default.Pets, reminder.title, reminder.category, if (reminder.category == "Recuerdo") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary)
            }
        }
        item {
            Text(tr("Próximos eventos de la Comunidad"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(events.filter { it.startAt >= System.currentTimeMillis() }.sortedBy { it.startAt }.take(8), key = { "upcoming-" + it.id }) { event ->
            AgendaRow(Icons.Default.Event, event.title, "${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(Date(event.startAt))} · ${event.location}", MaterialTheme.colorScheme.primary)
        }
    }
    if (showAdd) AddAgendaReminderDialog(
        initialDate = selectedDate,
        onDismiss = { showAdd = false },
        onSave = { title, date, category ->
            val id = java.util.UUID.randomUUID().toString()
            val item = JSONObject().put("id", id).put("title", title).put("date", date).put("category", category).toString()
            val dateTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(Date(date))
            if (CareReminderScheduler.schedule(context, dogName, "Agenda: $title", dateTime)) {
                onRemindersChange(listOf(item) + reminders)
                selectedDate = date
                monthAnchor = date
                showAdd = false
                true
            } else false
        }
    )
}

@Composable
private fun AgendaReminderRow(reminder: AgendaReminder, onDelete: () -> Unit) {
    Surface(shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(reminder.title, fontWeight = FontWeight.SemiBold)
                Text("${reminder.category} · ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(Date(reminder.date))}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, tr("Eliminar recordatorio"), tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun AgendaRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, tint: Color) {
    Surface(shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = .12f)) { Icon(icon, null, tint = tint, modifier = Modifier.padding(10.dp)) }
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun AddAgendaReminderDialog(initialDate: Long, onDismiss: () -> Unit, onSave: (String, Long, String) -> Boolean) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Personal") }
    var hour by remember { mutableIntStateOf(9) }
    var minute by remember { mutableIntStateOf(0) }
    var date by remember { mutableLongStateOf(Calendar.getInstance().apply { timeInMillis = initialDate; set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis) }
    var saveError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Nuevo recordatorio")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it.take(100) }, label = { Text(tr("Qué no quieres olvidar")) }, singleLine = true)
                OutlinedButton(onClick = {
                    val initial = Calendar.getInstance().apply { timeInMillis = date }
                    DatePickerDialog(context, { _, y, m, d -> date = Calendar.getInstance().apply { set(y, m, d, hour, minute, 0) }.timeInMillis }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).apply { datePicker.minDate = System.currentTimeMillis() }.show()
                }, modifier = Modifier.fillMaxWidth()) { Text(java.text.DateFormat.getDateInstance().format(Date(date))) }
                OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> hour = h; minute = m; date = Calendar.getInstance().apply { timeInMillis = date; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m) }.timeInMillis }, hour, minute, true).show() }, modifier = Modifier.fillMaxWidth()) { Text(String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute)) }
                if (saveError != null) Text(saveError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Personal", "Veterinario", "Cuidado", "Paseo", "Viaje").forEach { value -> FilterChip(selected = category == value, onClick = { category = value }, label = { Text(tr(value)) }) }
                }
            }
        },
        confirmButton = { Button(onClick = { if (!onSave(title.trim(), date, category)) saveError = tr("Elige una fecha y hora futuras para programar el aviso.") }, enabled = title.trim().length >= 2 && date > System.currentTimeMillis()) { Text(tr("Guardar")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar")) } }
    )
}
