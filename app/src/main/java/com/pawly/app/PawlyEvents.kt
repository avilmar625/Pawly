package com.pawly.app

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.webkit.JavascriptInterface
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.Calendar
import java.util.Date
import java.text.DateFormat
import java.text.SimpleDateFormat
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

data class PawlyEvent(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val location: String,
    val dateText: String,
    val latitude: Double?,
    val longitude: Double?,
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
    val language = LocalPawlyLanguage.current
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf(emptyList<PawlyEvent>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    var mapLoadFailed by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("Todos") }
    val loadPermissionDeniedMessage = tr("Firestore no permite leer los eventos. Publica las reglas incluidas en cambios55 desde la consola de Firebase.")
    val defaultLoadErrorMessage = tr("No se pudieron cargar los eventos.")
    val publishPermissionDeniedMessage = tr("Firebase ha rechazado la publicación. Publica las reglas de firestore.rules de esta rama y confirma que el correo de la cuenta está verificado.")
    val defaultPublishErrorMessage = tr("No se pudo publicar el evento.")
    LaunchedEffect(repository) {
        runCatching { repository.loadEvents() }
            .onSuccess { events = it; error = null }
            .onFailure {
                val detail = it.localizedMessage.orEmpty()
                error = if (detail.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    loadPermissionDeniedMessage
                } else detail.ifBlank { defaultLoadErrorMessage }
            }
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
            if (!mapOnly) FilledTonalIconButton(onClick = { createError = null; showCreate = true }) { Icon(Icons.Default.Add, contentDescription = tr("Crear evento")) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Todos", "Quedada", "Ruta", "Taller", "Adopción").forEach { item ->
                FilterChip(selected = category == item, onClick = { category = item }, label = { Text(tr(if (item == "Todos") "Todos los eventos" else item)) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (mapOnly) {
            val mapPoints = visible.filter { it.latitude != null && it.longitude != null }
            val centerLat = mapPoints.mapNotNull { it.latitude }.average().takeIf { it.isFinite() } ?: 40.4168
            val centerLon = mapPoints.mapNotNull { it.longitude }.average().takeIf { it.isFinite() } ?: -3.7038
            val minLatitude = mapPoints.minOfOrNull { it.latitude!! } ?: centerLat
            val maxLatitude = mapPoints.maxOfOrNull { it.latitude!! } ?: centerLat
            val minLongitude = mapPoints.minOfOrNull { it.longitude!! } ?: centerLon
            val maxLongitude = mapPoints.maxOfOrNull { it.longitude!! } ?: centerLon
            val latitudePadding = ((maxLatitude - minLatitude).coerceAtLeast(0.04)) * 0.2
            val longitudePadding = ((maxLongitude - minLongitude).coerceAtLeast(0.04)) * 0.2
            val south = (minLatitude - latitudePadding).coerceAtLeast(-85.0)
            val north = (maxLatitude + latitudePadding).coerceAtMost(85.0)
            val west = (minLongitude - longitudePadding).coerceAtLeast(-180.0)
            val east = (maxLongitude + longitudePadding).coerceAtMost(180.0)
            val mapUrlBuilder = Uri.parse("https://www.openstreetmap.org/export/embed.html").buildUpon()
                .appendQueryParameter("bbox", "$west,$south,$east,$north")
                .appendQueryParameter("layer", "mapnik")
            mapPoints.firstOrNull()?.let { point ->
                mapUrlBuilder.appendQueryParameter("marker", "${point.latitude},${point.longitude}")
            }
            val mapUrl = mapUrlBuilder.build().toString()
            val mapMarkersScript = mapPoints.joinToString(separator = "") { point ->
                "L.marker([${point.latitude},${point.longitude}]).addTo(map).bindPopup(" +
                    org.json.JSONObject.quote(point.title) + ");"
            }
            val mapMarkerBounds = mapPoints.joinToString(separator = ",") { point ->
                "[${point.latitude},${point.longitude}]"
            }
            val fallbackMapHtmlScript = org.json.JSONObject.quote(
                "<iframe title=\\"OpenStreetMap\\" src=\\"$mapUrl\\" style=\\"width:100%;height:100%;border:0\\"></iframe>"
            )
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(22.dp)),
                factory = { view ->
                    WebView(view).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                mapLoadFailed = false
                            }
                            override fun onReceivedError(
                                view: WebView,
                                request: android.webkit.WebResourceRequest,
                                error: android.webkit.WebResourceError
                            ) {
                                if (request.isForMainFrame || request.url.toString().contains("/export/embed.html")) mapLoadFailed = true
                            }
                        }
                        setBackgroundColor(android.graphics.Color.rgb(247, 243, 235))
                    }
                },
                update = { web ->
                    if (web.tag != mapUrl) {
                        web.tag = mapUrl
                        val fitMarkersScript = if (mapPoints.size > 1) {
                            "map.fitBounds(L.latLngBounds([$mapMarkerBounds]),{padding:[24,24],maxZoom:14});"
                        } else ""
                        val mapZoom = if (mapPoints.isEmpty()) 11 else 12
                        val html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                            "<link rel='stylesheet' href='https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css' onerror=\\"this.onerror=null;this.href='https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.css'\\">" +
                            "<style>html,body,#map{width:100%;height:100%;margin:0;padding:0;overflow:hidden}</style>" +
                            "<script src='https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js' onerror=\\"this.onerror=null;this.src='https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.js'\\"></script></head><body><div id='map'></div><script>" +
                            "let attempts=0;function initPawlyMap(){if(!window.L){if(attempts++<40){setTimeout(initPawlyMap,125);return;}document.getElementById('map').innerHTML=$fallbackMapHtmlScript;return;}" +
                            "const map=L.map('map').setView([$centerLat,$centerLon],$mapZoom);" +
                            "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; OpenStreetMap'}).addTo(map);" +
                            "$mapMarkersScript$fitMarkersScript}" +
                            "initPawlyMap();</script></body></html>"
                        web.loadDataWithBaseURL("https://www.openstreetmap.org/", html, "text/html", "UTF-8", null)
                    }
                }
            )
            if (mapLoadFailed) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        tr("No se pudo cargar el mapa. Comprueba tu conexión."),
                        modifier = Modifier.padding(10.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(
                tr(if (mapPoints.isEmpty()) "Aún no hay eventos con ubicación exacta; el mapa muestra Madrid como referencia."
                else "El mapa encuadra los eventos con ubicación exacta. Abre cada evento para ver su marcador."),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapUrl))) }
            }) { Text(tr("Abrir el mapa en el navegador")) }
            Spacer(Modifier.height(6.dp))
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (visible.isEmpty() && !loading && error == null) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Pets, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                    Text(tr("Todavía no hay eventos publicados"), fontWeight = FontWeight.Bold)
                    Text(tr("Sé de las primeras personas en proponer un plan bonito."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (!mapOnly) Button(onClick = { createError = null; showCreate = true }) { Text(tr("Crear el primer evento")) }
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
                                Text("${tr(event.category)} · ${formatEventDate(event.dateText, language)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
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
                                val url = if (event.latitude != null && event.longitude != null) {
                                    "https://www.openstreetmap.org/?mlat=${event.latitude}&mlon=${event.longitude}#map=16/${event.latitude}/${event.longitude}"
                                } else {
                                    "https://www.openstreetmap.org/search?query=${Uri.encode(event.location)}"
                                }
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Map, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text(tr(if (event.latitude != null && event.longitude != null) "Ver mapa" else "Buscar lugar"))
                            }
                            Button(onClick = {
                                scope.launch {
                                    runCatching { repository.toggleEventInterest(event.id) }
                                        .onSuccess {
                                            events = events.map { if (it.id == event.id) it.copy(interestedByMe = !it.interestedByMe, interestedCount = (it.interestedCount + if (it.interestedByMe) -1 else 1).coerceAtLeast(0)) else it }
                                        }
                                        .onFailure { error = it.localizedMessage ?: "No se pudo actualizar tu participación." }
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
        error = createError,
        onDismiss = { showCreate = false },
        onPublish = { title, description, categoryValue, location, date, lat, lon ->
            scope.launch {
                runCatching { repository.createEvent(title, description, categoryValue, location, date, lat, lon) }
                    .onSuccess {
                        runCatching { repository.loadEvents() }.onSuccess { events = it }
                        showCreate = false
                        createError = null
                        error = null
                    }
                    .onFailure {
                        val detail = it.localizedMessage.orEmpty()
                        createError = if (detail.contains("PERMISSION_DENIED", ignoreCase = true)) {
                            publishPermissionDeniedMessage
                        } else detail.ifBlank { defaultPublishErrorMessage }
                    }
            }
        }
    )
}

@Composable
private fun CreatePawlyEventDialog(
    error: String?,
    onDismiss: () -> Unit,
    onPublish: (String, String, String, String, String, Double?, Double?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    val context = LocalContext.current
    val language = LocalPawlyLanguage.current
    var selectedEventDateMillis by remember { mutableStateOf<Long?>(null) }
    var selectedEventHour by remember { mutableStateOf(-1) }
    var selectedEventMinute by remember { mutableStateOf(-1) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf("Quedada") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var showMapPicker by remember { mutableStateOf(false) }
    val parsedLatitude = latitude.toDoubleOrNull()
    val parsedLongitude = longitude.toDoubleOrNull()
    val coordinatesValid = (latitude.isBlank() && longitude.isBlank()) ||
        (parsedLatitude != null && parsedLongitude != null &&
            parsedLatitude in -90.0..90.0 && parsedLongitude in -180.0..180.0)

    val eventDateTimeMillis = if (selectedEventDateMillis != null && selectedEventHour >= 0 && selectedEventMinute >= 0) {
        Calendar.getInstance().apply {
            timeInMillis = selectedEventDateMillis!!
            set(Calendar.HOUR_OF_DAY, selectedEventHour)
            set(Calendar.MINUTE, selectedEventMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    } else null
    val dateText = eventDateTimeMillis?.let {
        SimpleDateFormat("yyyy-MM-dd|HH:mm", Locale.ROOT).format(Date(it))
    }.orEmpty()
    val dateLabel = selectedEventDateMillis?.let {
        DateFormat.getDateInstance(DateFormat.MEDIUM, PawlyI18n.locale(language)).format(Date(it))
    } ?: tr("Elegir fecha")
    val timeLabel = if (selectedEventHour >= 0 && selectedEventMinute >= 0) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, selectedEventHour)
            set(Calendar.MINUTE, selectedEventMinute)
        }.let { DateFormat.getTimeInstance(DateFormat.SHORT, PawlyI18n.locale(language)).format(it.time) }
    } else tr("Elegir hora")
    val eventDateIsFuture = eventDateTimeMillis?.let { it > System.currentTimeMillis() } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Crear evento")) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(tr("Comparte un plan real con la comunidad."), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(title, { title = it.take(90); validationError = null }, label = { Text(tr("Nombre del evento")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it.take(1200); validationError = null }, label = { Text(tr("Descripción")) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(location, { location = it.take(160); validationError = null }, label = { Text(tr("Lugar o dirección")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val initial = Calendar.getInstance().apply {
                                selectedEventDateMillis?.let { timeInMillis = it }
                            }
                            val startOfToday = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    selectedEventDateMillis = Calendar.getInstance().apply {
                                        set(Calendar.DAY_OF_MONTH, 1)
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, day)
                                        set(Calendar.HOUR_OF_DAY, 0)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis
                                    validationError = null
                                },
                                initial.get(Calendar.YEAR),
                                initial.get(Calendar.MONTH),
                                initial.get(Calendar.DAY_OF_MONTH)
                            ).apply { datePicker.minDate = startOfToday }.show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(tr("Fecha"), style = MaterialTheme.typography.labelSmall)
                            Text(dateLabel, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val initial = Calendar.getInstance().apply {
                                selectedEventDateMillis?.let { timeInMillis = it }
                                if (selectedEventHour >= 0) set(Calendar.HOUR_OF_DAY, selectedEventHour)
                                if (selectedEventMinute >= 0) set(Calendar.MINUTE, selectedEventMinute)
                            }
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    selectedEventHour = hour
                                    selectedEventMinute = minute
                                    validationError = null
                                },
                                initial.get(Calendar.HOUR_OF_DAY),
                                initial.get(Calendar.MINUTE),
                                true
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(tr("Hora"), style = MaterialTheme.typography.labelSmall)
                            Text(timeLabel, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (eventDateTimeMillis != null && !eventDateIsFuture) {
                    Text(tr("La fecha y hora deben ser futuras."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                Text(tr("Tipo de evento"), style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    listOf("Quedada", "Ruta", "Taller", "Adopción").forEach { value ->
                        FilterChip(selected = category == value, onClick = { category = value; validationError = null }, label = { Text(tr(value), maxLines = 1) })
                    }
                }
                OutlinedButton(onClick = { showMapPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr(if (parsedLatitude != null && parsedLongitude != null) "Cambiar punto en el mapa" else "Elegir punto en el mapa (opcional)"))
                }
                if (parsedLatitude != null && parsedLongitude != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            String.format(Locale.US, "%.5f, %.5f", parsedLatitude, parsedLongitude),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { latitude = ""; longitude = ""; validationError = null }) { Text(tr("Quitar ubicación")) }
                    }
                } else {
                    Text(tr("La dirección basta para publicar; añade un marcador solo si quieres mostrar el punto exacto."),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (validationError != null) Text(validationError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    validationError = when {
                        title.trim().length < 4 -> tr("Escribe un título de al menos 4 caracteres.")
                        description.trim().length < 10 -> tr("Añade una descripción de al menos 10 caracteres.")
                        location.trim().length < 3 -> tr("Indica el lugar o la dirección del evento.")
                        eventDateTimeMillis == null -> tr("Selecciona una fecha y una hora.")
                        !eventDateIsFuture -> tr("La fecha y hora deben ser futuras.")
                        !coordinatesValid -> tr("Quita o corrige el marcador del mapa.")
                        else -> null
                    }
                    if (validationError == null) {
                        onPublish(title, description, category, location, dateText, parsedLatitude, parsedLongitude)
                    }
                }
            ) { Text(tr("Publicar")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar")) } }
    )

    if (showMapPicker) {
        EventLocationPickerDialog(
            initialLatitude = parsedLatitude,
            initialLongitude = parsedLongitude,
            onDismiss = { showMapPicker = false },
            onSelected = { lat, lon ->
                latitude = String.format(Locale.US, "%.6f", lat)
                longitude = String.format(Locale.US, "%.6f", lon)
                validationError = null
                showMapPicker = false
            }
        )
    }
}

@Composable
private fun EventLocationPickerDialog(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onDismiss: () -> Unit,
    onSelected: (Double, Double) -> Unit
) {
    val bridge = remember(onSelected) { PawlyEventMapBridge(onSelected) }
    val centerLatitude = initialLatitude ?: 40.4168
    val centerLongitude = initialLongitude ?: -3.7038
    val zoom = if (initialLatitude != null && initialLongitude != null) 14 else 5
    val hasInitialMarker = initialLatitude != null && initialLongitude != null
    val fallbackMapUrlBuilder = Uri.parse("https://www.openstreetmap.org/export/embed.html").buildUpon()
        .appendQueryParameter(
            "bbox",
            "${centerLongitude - 0.05},${centerLatitude - 0.05},${centerLongitude + 0.05},${centerLatitude + 0.05}"
        )
        .appendQueryParameter("layer", "mapnik")
    if (hasInitialMarker) fallbackMapUrlBuilder.appendQueryParameter("marker", "${centerLatitude},${centerLongitude}")
    val fallbackMapHtmlScript = org.json.JSONObject.quote(
        "<iframe title=\"OpenStreetMap\" src=\"${fallbackMapUrlBuilder.build()}\" style=\"width:100%;height:100%;border:0\"></iframe>"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Marca el lugar del evento")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Toca el mapa para colocar el marcador. Puedes moverlo antes de confirmar."),
                    style = MaterialTheme.typography.bodySmall)
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(310.dp).clip(RoundedCornerShape(16.dp)),
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            webViewClient = WebViewClient()
                            addJavascriptInterface(bridge, "PawlyEventMap")
                            setBackgroundColor(android.graphics.Color.rgb(247, 243, 235))
                            val markerScript = if (hasInitialMarker) "marker=L.marker([$centerLatitude,$centerLongitude]).addTo(map);" else ""
                            val html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                                "<link rel='stylesheet' href='https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css' onerror=\"this.onerror=null;this.href='https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.css'\">" +
                                "<style>html,body,#map{height:100%;margin:0}</style>" +
                                "<script src='https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js' onerror=\"this.onerror=null;this.src='https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.js'\"></script></head><body><div id='map'></div><script>" +
                                "let attempts=0;function initPawlyMap(){if(!window.L){if(attempts++<40){setTimeout(initPawlyMap,125);return;}document.getElementById('map').innerHTML=$fallbackMapHtmlScript;return;}" +
                                "const map=L.map('map').setView([$centerLatitude,$centerLongitude],$zoom);" +
                                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; OpenStreetMap'}).addTo(map);" +
                                "let marker=null;$markerScript" +
                                "map.on('click',function(e){if(marker)map.removeLayer(marker);marker=L.marker(e.latlng).addTo(map);window.PawlyEventMap.select(e.latlng.lat,e.latlng.lng);});}" +
                                "initPawlyMap();" +
                                "</script></body></html>"
                            loadDataWithBaseURL("https://www.openstreetmap.org/", html, "text/html", "UTF-8", null)
                        }
                    }
                )
                Text(tr("Si el mapa no permite colocar el marcador, puedes publicar usando solo la dirección."),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Listo")) } },
        dismissButton = {}
    )
}

private fun formatEventDate(value: String, language: String): String {
    val parsed = runCatching {
        SimpleDateFormat("yyyy-MM-dd|HH:mm", Locale.ROOT).parse(value)
    }.getOrNull() ?: return value
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, PawlyI18n.locale(language)).format(parsed)
}

private class PawlyEventMapBridge(
    private val onSelected: (Double, Double) -> Unit
) {
    @JavascriptInterface
    fun select(latitude: Double, longitude: Double) {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return
        Handler(Looper.getMainLooper()).post { onSelected(latitude, longitude) }
    }
}
