package com.pawly.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Context
import org.json.JSONObject
import com.pawly.app.care.data.PetCareLocalStore
import com.pawly.app.care.model.PassportEntry
import com.pawly.app.care.model.CareLogEntry
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

internal data class PetTimelineItem(
    val id: String,
    val title: String,
    val detail: String,
    val category: String,
    val occurredAt: Long
)

internal fun buildPetTimelineItems(
    passportEntries: List<PassportEntry>,
    careLogs: List<CareLogEntry>,
    diaryEntries: List<String>,
    calendarReminders: List<String>,
    healthDates: Map<String, String>,
    zoneId: ZoneId = ZoneId.systemDefault()
): List<PetTimelineItem> {
    val items = mutableListOf<PetTimelineItem>()
    passportEntries.forEach { entry ->
        val date = runCatching { LocalDate.parse(entry.date.take(10)).atStartOfDay(zoneId).toInstant().toEpochMilli() }.getOrNull()
        if (date != null) items += PetTimelineItem(
            "passport-${entry.id}", entry.title.ifBlank { entry.type },
            listOf(entry.type, entry.detail).filter { it.isNotBlank() }.joinToString(" · "),
            "Salud", date
        )
    }
    careLogs.forEach { log ->
        if (log.occurredAt > 0L) items += PetTimelineItem(
            "care-${log.id}", log.taskTitle,
            listOf(log.action, log.actorName).filter { it.isNotBlank() }.joinToString(" · "),
            "Cuidado", log.occurredAt
        )
    }
    diaryEntries.forEachIndexed { index, raw ->
        runCatching {
            val json = JSONObject(raw)
            if (json.optInt("pawlyDiaryVersion") == 1) {
                val date = json.optLong("date")
                if (date > 0L) items += PetTimelineItem(
                    "diary-$index", json.optString("title").ifBlank { "Un recuerdo especial" },
                    json.optString("category").ifBlank { "Diario" }, "Diario", date
                )
            }
        }
    }
    calendarReminders.forEachIndexed { index, raw ->
        runCatching {
            val json = JSONObject(raw)
            val date = json.optLong("date")
            if (date > 0L) items += PetTimelineItem(
                "reminder-${json.optString("id").ifBlank { index.toString() }}",
                json.optString("title").ifBlank { "Cita o recordatorio" },
                json.optString("category").ifBlank { "Cita" }, "Cita", date
            )
        }
    }
    healthDates.forEach { (name, value) ->
        val date = runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).parse(value.replace("|", " "))?.time
        }.getOrNull()
        if (date != null) items += PetTimelineItem("health-date-$name-$date", name, "Cuidado programado", "Cita", date)
    }
    return items.sortedByDescending { it.occurredAt }
}

/** Filters existing records without duplicating or mutating timeline data. */
internal fun filterPetTimelineItems(items: List<PetTimelineItem>, filter: String): List<PetTimelineItem> =
    when (filter) {
        "Salud" -> items.filter { it.category == "Salud" || it.category == "Cuidado" }
        "Diario" -> items.filter { it.category == "Diario" }
        "Citas" -> items.filter { it.category == "Cita" }
        else -> items
    }

@Composable
internal fun PetTimelineSection(
    petName: String,
    petId: String,
    storageScope: String?,
    diaryEntries: List<String>,
    calendarReminders: List<String>,
    healthDates: Map<String, String>,
    onOpenHealth: () -> Unit,
    onOpenDiary: () -> Unit,
    onOpenCalendar: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val language = LocalPawlyLanguage.current
    val locale = PawlyI18n.locale(language)
    var selectedFilter by remember(petName, petId) { androidx.compose.runtime.mutableStateOf("Todo") }
    val savedLocalLabel = PawlyI18n.text("Guardado en este dispositivo", language)
    val loadingLabel = PawlyI18n.text("Cargando historial…", language)
    val syncedLabel = PawlyI18n.text("Historial sincronizado", language)
    val offlineLabel = PawlyI18n.text("Sin conexión: se conserva la copia local", language)
    val localEntries = remember(petName, storageScope) {
        PetCareLocalStore.loadEntries(context, petName, storageScope)
    }
    val localLogs = remember(petName, storageScope) {
        PetCareLocalStore.loadCareLogs(context, petName, storageScope)
    }
    var timelineItems by remember(petName, petId, storageScope, diaryEntries, calendarReminders, healthDates) {
        androidx.compose.runtime.mutableStateOf(
            buildPetTimelineItems(localEntries, localLogs, diaryEntries, calendarReminders, healthDates)
        )
    }
    val visibleTimelineItems = remember(timelineItems, selectedFilter) {
        filterPetTimelineItems(timelineItems, selectedFilter)
    }
    var syncLabel by remember(storageScope, language) {
        androidx.compose.runtime.mutableStateOf(if (storageScope.isNullOrBlank()) savedLocalLabel else loadingLabel)
    }
    androidx.compose.runtime.LaunchedEffect(petName, petId, storageScope, diaryEntries, calendarReminders, healthDates, language) {
        if (storageScope.isNullOrBlank()) return@LaunchedEffect
        runCatching {
            val repository = com.pawly.app.care.data.FirebasePetCareRepository()
            repository.ensureSharedPet(petId, petName)
            val remoteEntries = repository.loadPassportEntries(petId)
            val entries = if (remoteEntries.isEmpty() && localEntries.isNotEmpty()) {
                repository.savePassportEntries(petId, localEntries)
                localEntries
            } else remoteEntries.ifEmpty { localEntries }
            val remoteLogs = repository.loadCareLogs(petId)
            val logs = remoteLogs.ifEmpty { localLogs }
            PetCareLocalStore.saveEntries(context, petName, entries, storageScope)
            PetCareLocalStore.saveCareLogs(context, petName, logs, storageScope)
            timelineItems = buildPetTimelineItems(entries, logs, diaryEntries, calendarReminders, healthDates)
            syncLabel = syncedLabel
        }.onFailure {
            syncLabel = offlineLabel
        }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(tr("TODO VUESTRO"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(tr("La historia de") + " " + petName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(tr("Salud, cuidados, recuerdos y citas en orden cronológico."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Text(syncLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Todo", "Salud", "Diario", "Citas").forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(tr(filter)) }
                )
            }
        }
        if (timelineItems.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Pets, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Text(tr("Aquí aparecerá vuestra historia"), fontWeight = FontWeight.Bold)
                    Text(tr("Cuando guardes una vacuna, un cuidado, un recuerdo o una cita, aparecerá aquí automáticamente."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (visibleTimelineItems.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(22.dp)) {
                Text(
                    tr("No hay registros de este tipo todavía."),
                    Modifier.fillMaxWidth().padding(18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            visibleTimelineItems.forEach { item ->
                val icon = when (item.category) {
                    "Salud" -> Icons.Filled.HealthAndSafety
                    "Cuidado" -> Icons.Filled.CheckCircle
                    "Diario" -> Icons.Filled.AutoStories
                    else -> Icons.Filled.CalendarMonth
                }
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(tr(item.category).uppercase(locale), color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(item.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            if (item.detail.isNotBlank()) Text(item.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(SimpleDateFormat("dd MMM yyyy", locale).format(Date(item.occurredAt)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.TextButton(onClick = onOpenHealth) { Text(tr("Salud")) }
            androidx.compose.material3.TextButton(onClick = onOpenDiary) { Text(tr("Diario")) }
            androidx.compose.material3.TextButton(onClick = onOpenCalendar) { Text(tr("Citas")) }
        }
    }
}
