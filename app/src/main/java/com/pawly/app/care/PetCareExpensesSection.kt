package com.pawly.app.care

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var kind by remember { mutableStateOf("Alimentación") }
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var entries by remember(preferences) { mutableStateOf(loadExpenses(preferences)) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PawlySectionHeading(
                title = "Gastos de $petName",
                subtitle = "Apunta compras y cuidados para consultar cuánto llevas registrado. Estos datos se guardan en este dispositivo."
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    PawlyDropdownSelector(
                        title = "Tipo de gasto",
                        selectedLabel = tr(kind),
                        options = listOf("Alimentación", "Higiene", "Gasto veterinario", "Accesorios", "Otros").map { it to tr(it) },
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
                                feedback = tr("Añade un concepto para guardar el registro.")
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
                                feedback = tr("Registro guardado en este dispositivo.")
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
        item {
            val total = entries.sumOf { it.amount }
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(tr("Total registrado"), style = MaterialTheme.typography.labelMedium)
                        Text("%.2f €".format(total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(tr("${entries.size} registros"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (feedback != null) item {
            Text(feedback!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        if (entries.isEmpty()) item {
            Text(tr("Todavía no hay gastos guardados."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        items(entries, key = { it.id }) { entry ->
            Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(start = 13.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        feedback = tr("Gasto eliminado.")
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
