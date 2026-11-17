package com.pawly.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

private data class PawlyLogEntry(
    val id: String,
    val kind: String,
    val title: String,
    val detail: String,
    val amount: Double,
    val date: Long
)

@Composable
internal fun PawlyMarketHubScreen(
    petName: String,
    elegantDesign: Boolean,
    storageScope: String?,
    onBack: () -> Unit,
    onProfessionals: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(petName, storageScope) {
        val account = storageScope?.takeIf { it.isNotBlank() } ?: "local"
        val pet = petName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        context.getSharedPreferences("pawly_phase3_${account}_$pet", android.content.Context.MODE_PRIVATE)
    }
    var section by remember { mutableStateOf("market") }
    var category by remember { mutableStateOf("Todo") }
    var search by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("Alimentación") }
    var entries by remember(prefs) { mutableStateOf(loadPawlyEntries(prefs)) }
    var emergencyName by remember(prefs) { mutableStateOf(prefs.getString("emergency_name", "") ?: "") }
    var emergencyPhone by remember(prefs) { mutableStateOf(prefs.getString("emergency_phone", "") ?: "") }
    var emergencyVet by remember(prefs) { mutableStateOf(prefs.getString("emergency_vet", "") ?: "") }
    var allergies by remember(prefs) { mutableStateOf(prefs.getString("emergency_allergies", "") ?: "") }
    var medication by remember(prefs) { mutableStateOf(prefs.getString("emergency_medication", "") ?: "") }
    var microchip by remember(prefs) { mutableStateOf(prefs.getString("emergency_chip", "") ?: "") }
    var emergencyNotes by remember(prefs) { mutableStateOf(prefs.getString("emergency_notes", "") ?: "") }
    var feedback by remember { mutableStateOf<String?>(null) }

    fun openExternal(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { feedback = "No se ha podido abrir el enlace." }
    }
    fun shareReport() {
        val report = buildString {
            appendLine("FICHA DE EMERGENCIA · PAWLY")
            appendLine("Mascota: $petName")
            appendLine("Contacto: $emergencyName · $emergencyPhone")
            appendLine("Veterinario: $emergencyVet")
            appendLine("Alergias: $allergies")
            appendLine("Medicación: $medication")
            appendLine("Microchip: $microchip")
            appendLine("Notas: $emergencyNotes")
            appendLine()
            appendLine("INFORME DE REGISTROS")
            entries.sortedByDescending { it.date }.forEach {
                appendLine("• ${it.kind}: ${it.title} — ${it.detail}${if (it.amount > 0) " · %.2f €".format(it.amount) else ""}")
            }
            appendLine()
            appendLine("Documento informativo creado por el propietario. No sustituye un informe clínico veterinario.")
        }
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Ficha de emergencia de $petName")
            putExtra(Intent.EXTRA_TEXT, report)
        }, "Compartir ficha de emergencia"))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null); Text(" Volver") }
                Spacer(Modifier.weight(1f))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(30.dp)) {
                    Text("PAWLY · CUIDADOS", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(Modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)))) {
                    Column(Modifier.padding(21.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("EL UNIVERSO DE $petName", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .78f),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                        Text("Todo lo que necesita,
en un solo lugar.", color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Compras útiles, rutinas, gastos, salud y profesionales.", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .88f))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                listOf("market" to "Market", "care" to "Cuidados", "emergency" to "Emergencia", "learn" to "Aprender", "map" to "Mapa").forEach { (id, label) ->
                    FilterChip(selected = section == id, onClick = { section = id }, label = { Text(label) })
                }
            }
        }
        when (section) {
            "market" -> {
                item {
                    Text("Pawly Market", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Encuentra productos por necesidad. Pawly te lleva a tiendas externas; no procesa pagos ni gestiona pedidos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, label = { Text("¿Qué estás buscando?") }, leadingIcon = { Icon(Icons.Default.Search, null) })
                }
                item {
                    val cats = listOf("Todo", "Pienso", "Comida húmeda", "Snacks", "Juguetes", "Paseo", "Higiene", "Descanso")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        cats.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                row.forEach { c ->
                                    FilterChip(selected = category == c, onClick = { category = c },
                                        label = { Text(c, maxLines = 1) })
                                }
                            }
                        }
                    }
                }
                item {
                    val products = listOf(
                        Triple("Pienso y alimentación", "Nutrición diaria y dietas especiales", "pienso para perros"),
                        Triple("Comida húmeda", "Texturas y formatos para cada etapa", "comida húmeda perros"),
                        Triple("Snacks y premios", "Premios para entrenamiento y disfrute", "snacks naturales perros"),
                        Triple("Juguetes y enriquecimiento", "Olfato, masticación y juego", "juguetes enriquecimiento perros"),
                        Triple("Paseo y viaje", "Arneses, correas y accesorios", "arneses correas perros"),
                        Triple("Higiene y cuidado", "Champús, cepillos y limpieza", "higiene perros"),
                        Triple("Camas y descanso", "Camas, mantas y transportines", "camas perros")
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        products.filter { category == "Todo" || when(category) {
                            "Pienso" -> it.first.startsWith("Pienso")
                            "Comida húmeda" -> it.first.startsWith("Comida")
                            "Snacks" -> it.first.startsWith("Snacks")
                            "Juguetes" -> it.first.startsWith("Juguetes")
                            "Paseo" -> it.first.startsWith("Paseo")
                            "Higiene" -> it.first.startsWith("Higiene")
                            "Descanso" -> it.first.startsWith("Camas")
                            else -> true
                        }}.forEach { p ->
                            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(Modifier.fillMaxWidth().clickable {
                                    val q = if (search.isBlank()) p.third else search
                                    openExternal("https://www.google.com/search?tbm=shop&q=" + Uri.encode(q))
                                }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                        Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(p.first, fontWeight = FontWeight.Bold)
                                        Text(p.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.OpenInNew, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                item {
                    Text("Selección responsable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Compara ingredientes, tamaño, necesidades veterinarias y precio por kilo. Los enlaces abren búsquedas externas; Pawly no certifica productos ni recibe pagos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            "care" -> {
                item {
                    Text("Alimentación, higiene y gastos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Registra compras, comidas y cuidados para llevar un control sencillo de $petName.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        PawlyDropdownSelector(title = "Tipo de registro", selectedLabel = kind,
                            options = listOf("Alimentación", "Higiene", "Gasto veterinario", "Accesorios", "Otros").map { it to it },
                            selectedValue = kind, onSelected = { kind = it })
                        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Concepto (p. ej. pienso, baño...)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = detail, onValueChange = { detail = it }, label = { Text("Detalle, cantidad o frecuencia") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = amount, onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                            label = { Text("Coste en € (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(onClick = {
                            if (title.isBlank()) { feedback = "Añade un concepto para guardar el registro." }
                            else {
                                val item = PawlyLogEntry(System.currentTimeMillis().toString(), kind, title.trim(), detail.trim(),
                                    amount.replace(',', '.').toDoubleOrNull() ?: 0.0, System.currentTimeMillis())
                                entries = listOf(item) + entries
                                savePawlyEntries(prefs, entries)
                                title = ""; detail = ""; amount = ""; feedback = "Registro guardado en este dispositivo."
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("Guardar registro") }
                    }
                }
                item {
                    val total = entries.sumOf { it.amount }
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { Text("Gasto registrado", style = MaterialTheme.typography.labelMedium); Text("%.2f €".format(total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                            Text("${entries.size} registros", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(entries, key = { it.id }) { e ->
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.title, fontWeight = FontWeight.Bold)
                                Text("${e.kind} · ${e.detail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (e.amount > 0) Text("%.2f €".format(e.amount), fontWeight = FontWeight.Bold)
                            IconButton(onClick = { entries = entries.filterNot { it.id == e.id }; savePawlyEntries(prefs, entries) }) {
                                Icon(Icons.Default.DeleteOutline, "Eliminar registro")
                            }
                        }
                    }
                }
                item {
                    Text("Los registros se guardan localmente en este dispositivo y perfil de mascota. La sincronización entre dispositivos queda pendiente de conectar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            "emergency" -> {
                item {
                    Text("Ficha de emergencia", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Ten a mano los datos importantes si otra persona debe cuidar de tu mascota.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedTextField(emergencyName, { emergencyName = it }, label = { Text("Persona de contacto") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(emergencyPhone, { emergencyPhone = it }, label = { Text("Teléfono de emergencia") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(emergencyVet, { emergencyVet = it }, label = { Text("Clínica veterinaria habitual") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(allergies, { allergies = it }, label = { Text("Alergias y reacciones") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        OutlinedTextField(medication, { medication = it }, label = { Text("Medicación y dosis indicadas") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        OutlinedTextField(microchip, { microchip = it }, label = { Text("Número de microchip") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(emergencyNotes, { emergencyNotes = it }, label = { Text("Otras indicaciones") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        Button(onClick = {
                            prefs.edit().putString("emergency_name", emergencyName).putString("emergency_phone", emergencyPhone)
                                .putString("emergency_vet", emergencyVet).putString("emergency_allergies", allergies)
                                .putString("emergency_medication", medication).putString("emergency_chip", microchip)
                                .putString("emergency_notes", emergencyNotes).apply()
                            feedback = "Ficha de emergencia guardada."
                        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Save, null); Text("Guardar ficha") }
                        OutlinedButton(onClick = { shareReport() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Share, null); Text("Compartir ficha e informe de registros")
                        }
                    }
                }
                item {
                    Text("Los informes clínicos oficiales deben ser emitidos por el centro veterinario. Esta ficha reúne datos aportados por el propietario y no sustituye un diagnóstico ni una historia clínica.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            "learn" -> {
                item {
                    Text("Educación y comportamiento", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Pequeñas guías prácticas para avanzar con paciencia y refuerzo positivo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(listOf(
                    "Paseo sin tirones" to "Premia la correa floja, cambia de dirección con calma y practica primero en entornos tranquilos.",
                    "Llamada fiable" to "Empieza a corta distancia, usa una señal constante y recompensa siempre que vuelva.",
                    "Enriquecimiento olfativo" to "Esconde parte de su ración en una alfombra olfativa o en varios puntos seguros.",
                    "Miedo y reactividad" to "Aumenta distancia, evita forzar acercamientos y busca ayuda de un profesional cualificado si hay sufrimiento.",
                    "Higiene cooperativa" to "Acostumbra gradualmente al cepillo, patas y manipulación con sesiones breves y premios."
                )) { (heading, body) ->
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(heading, fontWeight = FontWeight.Bold)
                            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { Text("Estas pautas son educativas y generales. Ante dolor, agresividad, miedo intenso o cambios bruscos, consulta con veterinario o profesional acreditado.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            "map" -> {
                item {
                    Text("Mapa inteligente", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Busca servicios y lugares adaptados a mascotas en una zona concreta.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    OutlinedTextField(value = area, onValueChange = { area = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ciudad, barrio o código postal") }, singleLine = true, leadingIcon = { Icon(Icons.Default.LocationOn, null) })
                }
                item {
                    listOf("Parques y zonas verdes" to "parques para perros", "Playas para perros" to "playas para perros",
                        "Veterinarios y urgencias" to "veterinario urgencias", "Tiendas de mascotas" to "tiendas mascotas",
                        "Peluquerías caninas" to "peluquería canina").forEach { (label, query) ->
                        OutlinedButton(onClick = {
                            val place = area.trim()
                            if (place.isBlank()) feedback = "Indica una zona para buscar."
                            else openExternal("https://www.google.com/maps/search/?api=1&query=" + Uri.encode("$query en $place"))
                        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Map, null); Text(label) }
                    }
                    Button(onClick = onProfessionals, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.WorkspacePremium, null); Text("Ver directorio de profesionales Pawly") }
                }
                item { Text("El mapa abre resultados externos y no comparte tu ubicación exacta con Pawly. Comprueba horarios, accesibilidad y normas locales antes de desplazarte.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (feedback != null) item {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp)) {
                Text(feedback!!, modifier = Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

private fun loadPawlyEntries(prefs: android.content.SharedPreferences): List<PawlyLogEntry> =
    runCatching {
        val array = JSONArray(prefs.getString("care_expense_entries", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            PawlyLogEntry(o.optString("id"), o.optString("kind"), o.optString("title"), o.optString("detail"),
                o.optDouble("amount", 0.0), o.optLong("date"))
        }
    }.getOrDefault(emptyList())

private fun savePawlyEntries(prefs: android.content.SharedPreferences, entries: List<PawlyLogEntry>) {
    val array = JSONArray()
    entries.forEach { e ->
        array.put(JSONObject().put("id", e.id).put("kind", e.kind).put("title", e.title)
            .put("detail", e.detail).put("amount", e.amount).put("date", e.date))
    }
    prefs.edit().putString("care_expense_entries", array.toString()).apply()
}
