package com.pawly.app.care

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pawly.app.PawlyDropdownSelector
import com.pawly.app.PawlySectionHeading
import com.pawly.app.tr
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

private data class PetCareExpense(
    val id: String,
    val kind: String,
    val title: String,
    val detail: String,
    val amount: Double,
    val date: Long
)

@Composable
internal fun PetCareExpensesSection(petName: String, storageScope: String?) {
    val context = LocalContext.current
    val preferences = remember(petName, storageScope) {
        val account = storageScope?.takeIf { it.isNotBlank() } ?: "local"
        val pet = petName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        context.getSharedPreferences("pawly_phase3_${account}_$pet", Context.MODE_PRIVATE)
    }
    val kinds = listOf("Alimentación", "Higiene", "Gasto veterinario", "Accesorios", "Otros")
    var kind by remember { mutableStateOf(kinds.first()) }
    var categoryFilter by remember { mutableStateOf("Todos") }
    var selectedMonth by remember { mutableStateOf(java.time.YearMonth.now()) }
    var showEntryForm by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var entries by remember(preferences) { mutableStateOf(loadExpenses(preferences)) }

    val monthEntries = remember(entries, selectedMonth) {
        entries.filter { entry ->
            runCatching {
                java.time.YearMonth.from(
                    java.time.Instant.ofEpochMilli(entry.date)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                ) == selectedMonth
            }.getOrDefault(false)
        }
    }
    val categoryTotals = remember(monthEntries) {
        monthEntries.filter { it.amount > 0.0 }
            .groupBy { it.kind }
            .mapValues { (_, categoryEntries) -> categoryEntries.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }
    }
    val maxCategoryTotal = categoryTotals.maxOfOrNull { it.value } ?: 0.0
    val monthlyTotal = monthEntries.sumOf { it.amount }
    val visibleEntries = remember(monthEntries, categoryFilter) {
        if (categoryFilter == "Todos") monthEntries
        else monthEntries.filter { it.kind == categoryFilter }
    }
    val monthLabel = selectedMonth.atDay(1).format(
        java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.getDefault())
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            PawlySectionHeading(title = "Gastos de $petName")
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Gastos por categoría"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = tr("Mes anterior"))
                        }
                        Text(monthLabel, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { selectedMonth = selectedMonth.plusMonths(1) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = tr("Mes siguiente"))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Total del mes"), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Text("%.2f €".format(monthlyTotal), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    if (categoryTotals.isEmpty()) {
                        Text(tr("Sin gastos este mes"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    } else {
                        categoryTotals.forEach { (category, total) ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(tr(category), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Text("%.2f €".format(total), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                }
                                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                    Box(
                                        Modifier.fillMaxWidth((total / maxCategoryTotal).toFloat().coerceIn(0f, 1f))
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                    PawlyDropdownSelector(
                        title = tr("Filtrar por categoría"),
                        selectedLabel = tr(categoryFilter),
                        options = (listOf("Todos") + kinds).map { it to tr(it) },
                        selectedValue = categoryFilter,
                        onSelected = { categoryFilter = it },
                        leadingIcon = Icons.Default.ReceiptLong
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Añadir gasto"), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { showEntryForm = !showEntryForm }) {
                            Icon(if (showEntryForm) Icons.Filled.ExpandLess else Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(tr(if (showEntryForm) "Cerrar" else "Añadir"))
                        }
                    }
                    if (showEntryForm) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            PawlyDropdownSelector(
                                title = tr("Tipo de gasto"),
                                selectedLabel = tr(kind),
                                options = kinds.map { it to tr(it) },
                                selectedValue = kind,
                                onSelected = { kind = it },
                                leadingIcon = Icons.Default.ReceiptLong
                            )
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it.take(80) },
                                label = { Text(tr("Concepto")) },
                                placeholder = { Text(tr("Pienso, baño, consulta…")) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = detail,
                                onValueChange = { detail = it.take(160) },
                                label = { Text(tr("Detalle (opcional)")) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' || char == ',' }.take(12) },
                                label = { Text(tr("Coste en € (opcional)")) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (title.isBlank()) {
                                        feedback = "Añade un concepto para guardar el registro."
                                    } else {
                                        val entry = PetCareExpense(
                                            id = java.util.UUID.randomUUID().toString(),
                                            kind = kind,
                                            title = title.trim(),
                                            detail = detail.trim(),
                                            amount = amount.replace(',', '.').toDoubleOrNull() ?: 0.0,
                                            date = System.currentTimeMillis()
                                        )
                                        entries = listOf(entry) + entries
                                        saveExpenses(preferences, entries)
                                        title = ""
                                        detail = ""
                                        amount = ""
                                        showEntryForm = false
                                        feedback = "Registro guardado en este dispositivo."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(tr("Guardar gasto"))
                            }
                        }
                    }
                }
            }
        }
        if (feedback != null) item {
            Text(feedback!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        if (visibleEntries.isEmpty() && monthEntries.isNotEmpty()) item {
            Text(tr("Sin gastos en esta categoría"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        items(visibleEntries, key = { it.id }) { entry ->
            Card(shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(entry.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(tr(entry.kind), entry.detail.takeIf { it.isNotBlank() }, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.date))).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (entry.amount > 0) Text("%.2f €".format(entry.amount), fontWeight = FontWeight.Bold)
                    IconButton(onClick = {
                        entries = entries.filterNot { it.id == entry.id }
                        saveExpenses(preferences, entries)
                        feedback = "Gasto eliminado."
                    }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = tr("Eliminar gasto"), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
private fun loadExpenses(preferences: android.content.SharedPreferences): List<PetCareExpense> = runCatching {
    val array = JSONArray(preferences.getString("care_expense_entries", "[]"))
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        PetCareExpense(
            id = item.optString("id"),
            kind = item.optString("kind"),
            title = item.optString("title"),
            detail = item.optString("detail"),
            amount = item.optDouble("amount", 0.0),
            date = item.optLong("date")
        ).takeIf { it.id.isNotBlank() && it.title.isNotBlank() }
    }
}.getOrDefault(emptyList())

private fun saveExpenses(preferences: android.content.SharedPreferences, entries: List<PetCareExpense>) {
    val array = JSONArray()
    entries.forEach { entry ->
        array.put(JSONObject().put("id", entry.id).put("kind", entry.kind).put("title", entry.title)
            .put("detail", entry.detail).put("amount", entry.amount).put("date", entry.date))
    }
    preferences.edit().putString("care_expense_entries", array.toString()).apply()
}
