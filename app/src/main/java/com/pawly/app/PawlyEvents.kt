package com.pawly.app


import android.content.Intent
import android.provider.CalendarContract
import android.net.Uri
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import java.util.Locale
import java.util.Calendar
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.view.MotionEvent
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Event
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
    val interestedByMe: Boolean,
    val startAt: Long = 0,
    val timeZoneId: String = ""
)

internal data class PawlyMapMarker(
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val description: String = "",
    val eventId: String = ""
)

/** Native OSM map. It does not depend on WebView, Leaflet CDNs or a Google Maps API key. */
@Composable
internal fun PawlyOpenStreetMap(
    modifier: Modifier,
    centerLatitude: Double,
    centerLongitude: Double,
    zoom: Double = 12.0,
    markers: List<PawlyMapMarker> = emptyList(),
    onMapTap: ((Double, Double) -> Unit)? = null,
    onMarkerClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val latestOnMapTap by rememberUpdatedState(onMapTap)
    val latestOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val mapView = remember(context) {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(true)
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            overlays.add(object : Overlay() {
                override fun onSingleTapConfirmed(event: MotionEvent, mapView: MapView): Boolean {
                    val callback = latestOnMapTap ?: return false
                    val point = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt())
                    callback(point.latitude, point.longitude)
                    return true
                }
            })
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { map ->
            val centerKey = "$centerLatitude|$centerLongitude|$zoom"
            if (map.tag != centerKey) {
                map.tag = centerKey
                map.controller.setZoom(zoom)
                map.controller.setCenter(GeoPoint(centerLatitude, centerLongitude))
            }
            map.overlays.removeAll { it is Marker }
            markers.forEach { point ->
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(point.latitude, point.longitude)
                    title = point.title
                    snippet = point.description
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { _, _ ->
                        latestOnMarkerClick?.invoke(point.eventId)
                        latestOnMarkerClick != null
                    }
                })
            }
            map.invalidate()
        }
    )
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PawlyEventsScreen(
    repository: FirebaseCommunityRepository,
    mapOnly: Boolean = false,
    elegantDesign: Boolean = false,
    followingIds: Set<String> = emptySet(),
    onOpenProfile: (String) -> Unit = {},
    onMessage: (String) -> Unit = {},
    onFollow: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf(emptyList<PawlyEvent>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var reportEventId by remember { mutableStateOf<String?>(null) }
    var selectedEvent by remember { mutableStateOf<PawlyEvent?>(null) }
    var eventAttendees by remember { mutableStateOf(emptyList<PawlyEventAttendee>()) }
    var attendeeNextPageToken by remember { mutableStateOf("") }
    var attendeesLoading by remember { mutableStateOf(false) }
    var attendeesError by remember { mutableStateOf<String?>(null) }
    var createError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedEvent?.id) {
        eventAttendees = emptyList()
        attendeeNextPageToken = ""
        attendeesError = null
        attendeesLoading = false
        val openedEvent = selectedEvent
        if (openedEvent != null && repository.currentUid == openedEvent.organizerUid) {
            attendeesLoading = true
            runCatching { repository.loadEventAttendees(openedEvent.id) }
                .onSuccess {
                    eventAttendees = it.attendees
                    attendeeNextPageToken = it.nextPageToken
                    attendeesError = null
                }
                .onFailure { attendeesError = it.localizedMessage ?: "No se pudo cargar la lista de personas interesadas." }
            attendeesLoading = false
        }
    }
    var category by remember { mutableStateOf("Todos") }
    var sortAscending by remember { mutableStateOf(true) }
    var sortByInterested by remember { mutableStateOf(false) }
    val loadPermissionDeniedMessage = tr("Firestore no permite leer los eventos. Publica las reglas incluidas en cambios55 desde la consola de Firebase.")
    val defaultLoadErrorMessage = tr("No se pudieron cargar los eventos.")
    val publishPermissionDeniedMessage = tr("Firebase ha rechazado la publicación. Publica las reglas de firestore.rules de esta rama y confirma que el correo de la cuenta está verificado.")
    val defaultPublishErrorMessage = tr("No se pudo publicar el evento.")
    val attendeesLoadErrorText = tr("No se pudo cargar la lista de personas interesadas.")
    val chooserTitle = tr("Añadir a mi calendario")
    val calendarErrorMessage = tr("No hay ninguna aplicación de calendario disponible en este dispositivo.")
    val interestUpdateErrorText = tr("No se pudo actualizar tu interés.")
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
        .let { filtered ->
            if (sortByInterested) filtered.sortedWith(compareByDescending<PawlyEvent> { it.interestedCount }.thenBy { it.startAt })
            else {
                val dated = filtered.filter { it.startAt > 0L }.sortedBy { it.startAt }
                val withoutDate = filtered.filter { it.startAt <= 0L }
                (if (sortAscending) dated else dated.asReversed()) + withoutDate
            }
        }
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
        PawlyDropdownSelector(
            title = tr("Tipo de evento"),
            selectedLabel = tr(if (category == "Todos") "Todos los eventos" else category),
            options = listOf(
                "Todos" to tr("Todos los eventos"),
                "Quedada" to tr("Quedada"),
                "Ruta" to tr("Ruta"),
                "Taller" to tr("Taller"),
                "Adopción" to tr("Adopción")
            ),
            selectedValue = category,
            onSelected = { category = it },
            leadingIcon = Icons.Default.FilterList
        )

        if (!mapOnly) {
            Spacer(Modifier.height(6.dp))
            val eventSort = when {
                sortByInterested -> "interested"
                sortAscending -> "nearest"
                else -> "farthest"
            }
            PawlyDropdownSelector(
                title = tr("Ordenar por"),
                selectedLabel = tr(when (eventSort) {
                    "interested" -> "Más personas interesadas"
                    "nearest" -> "Más próximos primero"
                    else -> "Más lejanos primero"
                }),
                options = listOf(
                    "nearest" to tr("Más próximos primero"),
                    "farthest" to tr("Más lejanos primero"),
                    "interested" to tr("Más personas interesadas")
                ),
                selectedValue = eventSort,
                onSelected = { value ->
                    sortByInterested = value == "interested"
                    sortAscending = value == "nearest"
                },
                leadingIcon = Icons.Default.Sort
            )

        }
        Spacer(Modifier.height(8.dp))
        if (mapOnly) {
            val mapPoints = visible.filter { it.latitude != null && it.longitude != null }
            val centerLat = mapPoints.mapNotNull { it.latitude }.average().takeIf { it.isFinite() } ?: 36.7213
            val centerLon = mapPoints.mapNotNull { it.longitude }.average().takeIf { it.isFinite() } ?: -4.4214
            val mapMarkers = mapPoints.mapNotNull { event ->
                val lat = event.latitude ?: return@mapNotNull null
                val lon = event.longitude ?: return@mapNotNull null
                PawlyMapMarker(lat, lon, event.title, event.location, event.id)
            }
            PawlyOpenStreetMap(
                modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(22.dp)),
                centerLatitude = centerLat,
                centerLongitude = centerLon,
                zoom = if (mapMarkers.size > 1) 11.0 else 13.0,
                markers = mapMarkers,
                onMarkerClick = { eventId -> selectedEvent = visible.firstOrNull { it.id == eventId } }
            )
            Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/"))) }
            }) { Text(tr("Abrir el mapa en el navegador")) }
            Text(
                tr(if (mapPoints.isEmpty()) "Todavía no hay eventos con ubicación exacta."
                else "Los marcadores corresponden a lugares de eventos publicados por la comunidad."),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(22.dp),
                    tonalElevation = if (elegantDesign) 2.dp else 0.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().clickable { selectedEvent = event }
                ) {
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
                        Row(
                            Modifier.fillMaxWidth().then(if (event.organizerUid.isNotBlank()) Modifier.clickable {
                                onOpenProfile(event.organizerUid)
                            } else Modifier),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Pets, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${tr("Organiza")} ${event.organizerName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (event.organizerUid.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("· ${event.interestedCount} ${tr("personas interesadas")}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
                            val toggleInterest: () -> Unit = {
                                scope.launch {
                                    runCatching { repository.toggleEventInterest(event.id) }
                                        .onSuccess {
                                            events = events.map { item ->
                                                if (item.id == event.id) item.copy(
                                                    interestedByMe = !item.interestedByMe,
                                                    interestedCount = (item.interestedCount + if (item.interestedByMe) -1 else 1).coerceAtLeast(0)
                                                ) else item
                                            }
                                        }
                                        .onFailure { error = it.localizedMessage ?: "No se pudo actualizar tu participación." }
                                }
                            }
                            if (event.interestedByMe) {
                                OutlinedButton(onClick = toggleInterest, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.PersonRemove, null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(tr("Quitar interés"))
                                }
                            } else {
                                Button(onClick = toggleInterest, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(tr("Me interesa"))
                                }
                            }
                        }
                        TextButton(onClick = { reportEventId = event.id }) {
                            Text(tr("Denunciar evento"))
                        }
                    }
                }
            }
        }
    }
    selectedEvent?.let { event ->
        EventDetailsDialog(
            event = event,
            canViewAttendees = repository.currentUid == event.organizerUid,
            attendees = eventAttendees,
            hasMoreAttendees = attendeeNextPageToken.isNotBlank(),
            attendeesLoading = attendeesLoading,
            attendeesError = attendeesError,
            onLoadAttendees = {
                if (!attendeesLoading) scope.launch {
                    attendeesLoading = true
                    runCatching { repository.loadEventAttendees(event.id, attendeeNextPageToken) }
                        .onSuccess {
                            eventAttendees = eventAttendees + it.attendees
                            attendeeNextPageToken = it.nextPageToken
                            attendeesError = null
                        }
                        .onFailure { attendeesError = it.localizedMessage ?: attendeesLoadErrorText }
                    attendeesLoading = false
                }
            },
            onDismiss = { selectedEvent = null },
            onAddToCalendar = {
                val begin = event.startAt
                val insert = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                    .putExtra(CalendarContract.Events.TITLE, event.title)
                    .putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
                    .putExtra(CalendarContract.Events.DESCRIPTION, event.description)
                    .putExtra(CalendarContract.Events.EVENT_TIMEZONE, event.timeZoneId.ifBlank { java.util.TimeZone.getDefault().id })
                try {
                    context.startActivity(Intent.createChooser(insert, chooserTitle))
                } catch (_: Exception) {
                    error = calendarErrorMessage
                }
            },
            onToggleInterest = {
                scope.launch {
                    runCatching { repository.toggleEventInterest(event.id) }
                        .onSuccess {
                            events = events.map { item ->
                                if (item.id == event.id) item.copy(
                                    interestedByMe = !item.interestedByMe,
                                    interestedCount = (item.interestedCount + if (item.interestedByMe) -1 else 1).coerceAtLeast(0)
                                ) else item
                            }
                            selectedEvent = events.firstOrNull { it.id == event.id }
                        }
                        .onFailure { error = it.localizedMessage ?: interestUpdateErrorText }
                }
            },
            onOpenProfile = onOpenProfile
        )
    }
    if (reportEventId != null) ReportDialog(
        repo = repository,
        targetId = reportEventId!!,
        type = "event",
        onDismiss = { reportEventId = null },
        onDone = { reportEventId = null }
    )
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
private fun EventDetailsDialog(
    event: PawlyEvent,
    canViewAttendees: Boolean,
    attendees: List<PawlyEventAttendee>,
    hasMoreAttendees: Boolean,
    attendeesLoading: Boolean,
    attendeesError: String?,
    onLoadAttendees: () -> Unit,
    onDismiss: () -> Unit,
    onAddToCalendar: () -> Unit,
    onToggleInterest: () -> Unit,
    onOpenProfile: (String) -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(.96f).widthIn(max = 560.dp).heightIn(max = 780.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column {
                Box(
                    Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))).padding(22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Color.White.copy(alpha = .18f), shape = RoundedCornerShape(12.dp)) {
                                Text(tr(event.category).uppercase(), modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = tr("Cerrar"), tint = Color.White) }
                        }
                        Text(event.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(Icons.Default.CalendarMonth, null, tint = Color.White.copy(alpha = .9f), modifier = Modifier.size(18.dp))
                            Text(event.dateText, color = Color.White.copy(alpha = .95f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Column(
                    Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(event.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(tr("Lugar"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(event.location, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                Modifier.fillMaxWidth().then(if (event.organizerUid.isNotBlank()) Modifier.clickable {
                                    onOpenProfile(event.organizerUid)
                                } else Modifier),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Pets, null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(tr("Organiza"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(event.organizerName, fontWeight = FontWeight.SemiBold,
                                        color = if (event.organizerUid.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(tr("Personas interesadas"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(event.interestedCount.toString(), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Asistentes"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (canViewAttendees) {
                                TextButton(onClick = onLoadAttendees, enabled = !attendeesLoading && (attendees.isEmpty() || hasMoreAttendees)) {
                                    Text(tr(if (attendees.isEmpty()) "Ver asistentes" else if (hasMoreAttendees) "Cargar más" else "Lista completa"))
                                }
                            }
                        }
                        if (canViewAttendees) {
                            if (attendeesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (attendeesError != null) {
                                Text(attendeesError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = onLoadAttendees, enabled = !attendeesLoading, modifier = Modifier.fillMaxWidth()) { Text(tr("Reintentar")) }
                            }
                            if (attendees.isEmpty() && !attendeesLoading && attendeesError == null) {
                                Text(tr("Todavía no hay asistentes cargados. Pulsa «Ver asistentes» para consultar la lista."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            attendees.forEach { attendee ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AsyncImage(model = attendee.photoUrl.takeIf { it.isNotBlank() }, contentDescription = null, modifier = Modifier.size(42.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                    Column(Modifier.weight(1f)) {
                                        Text(attendee.displayName.ifBlank { tr("Usuario de Pawly") }, fontWeight = FontWeight.SemiBold)
                                        if (attendee.username.isNotBlank()) Text("@${attendee.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            if (attendees.isNotEmpty()) Text(tr("La lista se pagina y oculta los perfiles bloqueados."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        } else {
                            Text(tr("Por privacidad, solo la persona organizadora puede consultar la lista de asistentes. Aquí se muestra el número total de personas interesadas."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(onClick = onToggleInterest, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (event.interestedByMe) Icons.Default.PersonRemove else Icons.Default.PersonAdd, null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr(if (event.interestedByMe) "Ya no me interesa" else "Me interesa"))
                    }
                    OutlinedButton(onClick = onAddToCalendar, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.EventAvailable, null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Añadir al calendario"))
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
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
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    var selectedHour by remember { mutableStateOf(-1) }
    var selectedMinute by remember { mutableStateOf(-1) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf("Quedada") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var showMapPicker by remember { mutableStateOf(false) }
    val eventDateTimeMillis = if (selectedDateMillis != null && selectedHour >= 0 && selectedMinute >= 0) {
        Calendar.getInstance().apply {
            timeInMillis = selectedDateMillis!!
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    } else null
    val dateText = eventDateTimeMillis?.let { millis ->
        Calendar.getInstance().apply { timeInMillis = millis }.let { c ->
            String.format(Locale.ROOT, "%02d/%02d/%04d %02d:%02d", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        }
    }.orEmpty()
    val eventDateIsFuture = eventDateTimeMillis?.let { it > System.currentTimeMillis() } == true
    val parsedLatitude = latitude.toDoubleOrNull()
    val parsedLongitude = longitude.toDoubleOrNull()
    val coordinatesValid = (latitude.isBlank() && longitude.isBlank()) ||
        (parsedLatitude != null && parsedLongitude != null &&
            parsedLatitude in -90.0..90.0 && parsedLongitude in -180.0..180.0)
    val titleValidationMessage = tr("Escribe un título de al menos 4 caracteres.")
    val descriptionValidationMessage = tr("Añade una descripción de al menos 10 caracteres.")
    val locationValidationMessage = tr("Indica el lugar o la dirección del evento.")
    val dateValidationMessage = tr("Selecciona una fecha y una hora.")
    val futureDateValidationMessage = tr("La fecha y hora deben ser futuras.")
    val coordinatesValidationMessage = tr("Quita o corrige el marcador del mapa.")

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
                    OutlinedButton(onClick = {
                        val initial = Calendar.getInstance().apply { selectedDateMillis?.let { timeInMillis = it } }
                        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                        DatePickerDialog(context, { _, year, month, day ->
                            selectedDateMillis = Calendar.getInstance().apply { set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, day); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                            validationError = null
                        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).apply { datePicker.minDate = today }.show()
                    }, modifier = Modifier.weight(1f)) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(tr("Fecha"), style = MaterialTheme.typography.labelSmall)
                            Text(selectedDateMillis?.let { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(it)) } ?: tr("Elegir fecha"), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(onClick = {
                        val initial = Calendar.getInstance().apply {
                            if (selectedHour >= 0) set(Calendar.HOUR_OF_DAY, selectedHour)
                            if (selectedMinute >= 0) set(Calendar.MINUTE, selectedMinute)
                        }
                        TimePickerDialog(context, { _, hour, minute -> selectedHour = hour; selectedMinute = minute; validationError = null }, initial.get(Calendar.HOUR_OF_DAY), initial.get(Calendar.MINUTE), true).show()
                    }, modifier = Modifier.weight(1f)) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(tr("Hora"), style = MaterialTheme.typography.labelSmall)
                            Text(if (selectedHour >= 0 && selectedMinute >= 0) String.format(Locale.ROOT, "%02d:%02d", selectedHour, selectedMinute) else tr("Elegir hora"), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (eventDateTimeMillis != null && !eventDateIsFuture) Text(tr("La fecha y hora deben ser futuras."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                Text(tr("Tipo de evento"), style = MaterialTheme.typography.labelMedium)
                PawlyDropdownSelector(
                    title = tr("Tipo de evento"),
                    selectedLabel = tr(category),
                    options = listOf("Quedada", "Ruta", "Taller", "Adopción").map { it to tr(it) },
                    selectedValue = category,
                    onSelected = { category = it },
                    leadingIcon = Icons.Default.Event
                )
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
                        TextButton(onClick = { latitude = ""; longitude = "" }) { Text(tr("Quitar ubicación")) }
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
                enabled = title.trim().length >= 4 && description.trim().length >= 10 &&
                    location.trim().length >= 3 && eventDateIsFuture && coordinatesValid,
                onClick = {
                    validationError = when {
                        title.trim().length < 4 -> titleValidationMessage
                        description.trim().length < 10 -> descriptionValidationMessage
                        location.trim().length < 3 -> locationValidationMessage
                        eventDateTimeMillis == null -> dateValidationMessage
                        !eventDateIsFuture -> futureDateValidationMessage
                        !coordinatesValid -> coordinatesValidationMessage
                        else -> null
                    }
                    if (validationError == null) onPublish(title, description, category, location, dateText, parsedLatitude, parsedLongitude)
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
    var selectedPoint by remember(initialLatitude, initialLongitude) {
        mutableStateOf(if (initialLatitude != null && initialLongitude != null) initialLatitude to initialLongitude else null)
    }
    val centerLatitude = selectedPoint?.first ?: initialLatitude ?: 36.7213
    val centerLongitude = selectedPoint?.second ?: initialLongitude ?: -4.4214
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Marca el lugar del evento")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Toca el mapa para elegir el punto. Puedes acercar, alejar y mover el mapa antes de confirmar."), style = MaterialTheme.typography.bodySmall)
                PawlyOpenStreetMap(
                    modifier = Modifier.fillMaxWidth().height(310.dp).clip(RoundedCornerShape(16.dp)),
                    centerLatitude = centerLatitude,
                    centerLongitude = centerLongitude,
                    zoom = if (selectedPoint != null) 14.0 else 11.0,
                    markers = selectedPoint?.let { listOf(PawlyMapMarker(it.first, it.second, "Ubicación del evento")) }.orEmpty(),
                    onMapTap = { latitude, longitude -> selectedPoint = latitude to longitude }
                )
                Text(
                    selectedPoint?.let { String.format(Locale.US, "%.5f, %.5f", it.first, it.second) }
                        ?: tr("Todavía no has seleccionado una ubicación."),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
                Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { selectedPoint?.let { onSelected(it.first, it.second) } }, enabled = selectedPoint != null) {
                Text(tr("Usar esta ubicación"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar")) } }
    )
}
