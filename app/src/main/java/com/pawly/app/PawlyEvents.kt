package com.pawly.app

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal data class PawlyEvent(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val location: String,
    val dateText: String,
    val latitude: Double,
    val longitude: Double,
    val organizerName: String,
    val organizerUid: String,
    val interestedCount: Long,
    val interestedByMe: Boolean
)

@Composable
internal fun PawlyEventsScreen(
    repository: FirebaseCommunityRepository,
    mapOnly: Boolean = false,
    elegantDesign: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf(emptyList<PawlyEvent>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("Todos") }
    LaunchedEffect(repository) {
        runCatching { repository.loadEvents() }
            .onSuccess { events = it; error = null }
            .onFailure { error = it.localizedMessage ?: "No se pudieron cargar los eventos." }
        loading = false
    }
    val visible = events.filter { category == "Todos" || it.category == category }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp)) {
        if (elegantDesign && !mapOnly) {
            Box(Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(24.dp))) {
                AsyncImage(
                    model = "https://images.unsplash.com/photo-1548199973-03cce0bbc87b?w=1200&h=500&fit=crop",
                    contentDescription = tr("Perros disfrutando juntos al aire libre"),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xC9152B22)))))
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text(tr("PAWLY · PLANES BONITOS"), color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelSmall)
                    Text(tr("Cada encuentro puede ser el comienzo de una amistad."), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (mapOnly) tr("Explora cerca de ti") else tr("Planes para compartir"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(tr("Encuentros reales para personas y mascotas."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (!mapOnly) FilledTonalIconButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, contentDescription = tr("Crear evento")) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Todos", "Quedada", "Ruta", "Taller", "Adopción").forEach { item ->
                FilterChip(selected = category == item, onClick = { category = item }, label = { Text(tr(if (item == "Todos") "Todos los eventos" else item)) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (mapOnly) {
            val mapPoints = visible.filter { it.latitude != 0.0 || it.longitude != 0.0 }
            val centerLat = mapPoints.map { it.latitude }.average().takeIf { it.isFinite() } ?: 36.7213
            val centerLon = mapPoints.map { it.longitude }.average().takeIf { it.isFinite() } ?: -4.4214
            val markers = mapPoints.joinToString(",") { event ->
                "{lat:${event.latitude},lon:${event.longitude},title:${org.json.JSONObject.quote(event.title)},place:${org.json.JSONObject.quote(event.location)}}"
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(235.dp).clip(RoundedCornerShape(22.dp)),
                factory = { view ->
                    WebView(view).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        setBackgroundColor(android.graphics.Color.rgb(247, 243, 235))
                    }
                },
                update = { web ->
                    val zoom = if (mapPoints.isEmpty()) 11 else 12
                    val html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                        "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'>" +
                        "<style>html,body,#map{height:100%;margin:0}.leaflet-popup-content{font:14px sans-serif}</style>" +
                        "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script></head><body><div id='map'></div><script>" +
                        "const map=L.map('map').setView([$centerLat,$centerLon],$zoom);" +
                        "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; OpenStreetMap'}).addTo(map);" +
                        "const points=[$markers];points.forEach(p=>L.marker([p.lat,p.lon]).addTo(map).bindPopup('<b>'+p.title+'</b><br>'+p.place));" +
                        "if(points.length>1){map.fitBounds(points.map(p=>[p.lat,p.lon]),{padding:[24,24]});}</script></body></html>"
                    web.loadDataWithBaseURL("https://www.openstreetmap.org/", html, "text/html", "UTF-8", null)
                }
            )
            Text(tr("Los marcadores corresponden a lugares de eventos publicados por la comunidad."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (visible.isEmpty() && !loading) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Pets, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                    Text(tr("Todavía no hay eventos publicados"), fontWeight = FontWeight.Bold)
                    Text(tr("Sé de las primeras personas en proponer un plan bonito."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (!mapOnly) Button(onClick = { showCreate = true }) { Text(tr("Crear el primer evento")) }
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
            items(visible, key = { it.id }) { event ->
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp), tonalElevation = if (elegantDesign) 2.dp else 0.dp) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                                Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.padding(9.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(event.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("${tr(event.category)} · ${event.dateText}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(event.description, style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                            Text(event.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${tr("Organiza")} ${event.organizerName} · ${event.interestedCount} ${tr("personas interesadas")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val url = "https://www.openstreetmap.org/?mlat=${event.latitude}&mlon=${event.longitude}#map=16/${event.latitude}/${event.longitude}"
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Map, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text(tr("Ver mapa"))
                            }
                            Button(onClick = {
                                scope.launch {
                                    runCatching { repository.toggleEventInterest(event.id) }
                                        .onSuccess {
                                            events = events.map { if (it.id == event.id) it.copy(interestedByMe = !it.interestedByMe, interestedCount = (it.interestedCount + if (it.interestedByMe) -1 else 1).coerceAtLeast(0)) else it }
                                        }
                                        .onFailure { error = it.localizedMessage ?: tr("No se pudo actualizar tu participación.") }
                                }
                            }, modifier = Modifier.weight(1f)) {
                                Text(if (event.interestedByMe) tr("Me interesa ✓") else tr("Me interesa"))
                            }
                        }
                    }
                }
            }
        }
    }
    if (showCreate) CreatePawlyEventDialog(
        onDismiss = { showCreate = false },
        onPublish = { title, description, categoryValue, location, date, lat, lon ->
            scope.launch {
                runCatching { repository.createEvent(title, description, categoryValue, location, date, lat, lon) }
                    .onSuccess { events = repository.loadEvents(); showCreate = false; error = null }
                    .onFailure { error = it.localizedMessage ?: tr("No se pudo publicar el evento.") }
            }
        }
    )
}

@Composable
private fun CreatePawlyEventDialog(
    onDismiss: () -> Unit,
    onPublish: (String, String, String, String, String, Double, Double) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Quedada") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Crear evento")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Comparte un plan real con la comunidad."), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(title, { title = it }, label = { Text(tr("Nombre del evento")) }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text(tr("Descripción")) }, minLines = 2)
                OutlinedTextField(location, { location = it }, label = { Text(tr("Lugar o dirección")) }, singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text(tr("Fecha y hora")) }, singleLine = true, placeholder = { Text(tr("DD/MM/AAAA · HH:mm")) })
                Text(tr("Tipo de evento"), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Quedada", "Ruta", "Taller", "Adopción").forEach { value ->
                        FilterChip(selected = category == value, onClick = { category = value }, label = { Text(tr(value)) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(latitude, { latitude = it }, label = { Text(tr("Latitud")) }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(longitude, { longitude = it }, label = { Text(tr("Longitud")) }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Text(tr("Puedes obtener las coordenadas desde el punto del evento en OpenStreetMap."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && description.isNotBlank() && location.isNotBlank() && date.isNotBlank() &&
                    (latitude.toDoubleOrNull() ?: 999.0) in -90.0..90.0 &&
                    (longitude.toDoubleOrNull() ?: 999.0) in -180.0..180.0,
                onClick = { onPublish(title, description, category, location, date, latitude.toDouble(), longitude.toDouble()) }
            ) { Text(tr("Publicar")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar")) } }
    )
}
