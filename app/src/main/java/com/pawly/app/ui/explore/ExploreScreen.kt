package com.pawly.app

import com.pawly.app.tr

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.app.DatePickerDialog
import java.io.File
import java.util.Calendar
import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.Location
import android.location.LocationListener
import android.os.Build
import android.os.Looper
import android.location.Geocoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.core.content.ContextCompat
import org.json.JSONArray
import coil3.compose.AsyncImage

private data class Place(
    val emoji: String,
    val category: String,
    val name: String,
    val detail: String,
    val description: String,
    val color: Color,
    val imageUrl: String
)

private data class NearbyPlace(
    val id: String,
    val name: String,
    val category: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

private suspend fun loadNearbyPlaces(latitude: Double, longitude: Double, category: String): List<NearbyPlace> =
    withContext(Dispatchers.IO) {
        val selectors = when (category) {
            "Naturaleza" -> listOf("""["leisure"~"dog_park|park"]""")
            "Playas" -> listOf("""["natural"="beach"]""")
            "Comer" -> listOf("""["amenity"~"restaurant|cafe|bar"]""")
            "Alojarse" -> listOf("""["tourism"~"hotel|guest_house|hostel|camp_site"]""")
            else -> listOf(
                """["leisure"="dog_park"]""",
                """["natural"="beach"]""",
                """["amenity"~"restaurant|cafe|bar"]""",
                """["tourism"~"hotel|guest_house|hostel|camp_site"]"""
            )
        }
        val statements = selectors.joinToString("") { tag ->
            "node(around:12000,$latitude,$longitude)$tag;way(around:12000,$latitude,$longitude)$tag;relation(around:12000,$latitude,$longitude)$tag;"
        }
        val query = """[out:json][timeout:20];($statements);out center tags;"""
        val request = okhttp3.Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .post(okhttp3.FormBody.Builder().add("data", query).build())
            .header("User-Agent", "Pawly Android app")
            .build()
        val response = okhttp3.OkHttpClient().newCall(request).execute()
        response.use { result ->
            if (!result.isSuccessful) throw IllegalStateException("El directorio de lugares no está disponible ahora (HTTP ${result.code}).")
            val payload = org.json.JSONObject(result.body?.string().orEmpty())
            val elements = payload.optJSONArray("elements") ?: JSONArray()
            buildList {
                val seen = mutableSetOf<String>()
                for (index in 0 until elements.length()) {
                    val element = elements.optJSONObject(index) ?: continue
                    val tags = element.optJSONObject("tags") ?: continue
                    val name = tags.optString("name").trim().ifBlank { tags.optString("brand").trim() }
                    if (name.isBlank()) continue
                    val center = element.optJSONObject("center")
                    val lat = if (element.has("lat")) element.optDouble("lat", Double.NaN) else center?.optDouble("lat", Double.NaN) ?: Double.NaN
                    val lon = if (element.has("lon")) element.optDouble("lon", Double.NaN) else center?.optDouble("lon", Double.NaN) ?: Double.NaN
                    if (!lat.isFinite() || !lon.isFinite()) continue
                    val id = element.optString("type") + "/" + element.optString("id")
                    if (!seen.add(id)) continue
                    val address = listOf(
                        tags.optString("addr:street").takeIf { it.isNotBlank() },
                        tags.optString("addr:housenumber").takeIf { it.isNotBlank() },
                        tags.optString("addr:city").takeIf { it.isNotBlank() }
                    ).filterNotNull().joinToString(", ").ifBlank { tags.optString("operator").ifBlank { "Ubicación en OpenStreetMap" } }
                    val type = when {
                        tags.optString("leisure") == "dog_park" -> "Parque canino"
                        tags.optString("leisure") == "park" -> "Parque"
                        tags.optString("natural") == "beach" -> "Playa"
                        tags.optString("tourism").isNotBlank() -> "Alojamiento"
                        tags.optString("amenity") == "cafe" -> "Cafetería"
                        tags.optString("amenity") == "bar" -> "Bar"
                        tags.optString("amenity") == "restaurant" -> "Restaurante"
                        else -> "Lugar cercano"
                    }
                    add(NearbyPlace(id, name, type, address, lat, lon))
                }
            }.take(60)
        }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExploreScreen(savedPlaces: Set<String>, onToggleSave: (String) -> Unit, elegantDesign: Boolean = false, repository: FirebaseCommunityRepository? = null) {
    val context = LocalContext.current
    var currentCoordinates by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var currentAddress by remember { mutableStateOf<String?>(null) }
    var mapSearchQuery by remember { mutableStateOf("") }
    var nearbyPlaces by remember { mutableStateOf(emptyList<NearbyPlace>()) }
    var placesLoading by remember { mutableStateOf(false) }
    var placesError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentCoordinates) {
        val point = currentCoordinates ?: return@LaunchedEffect
        currentAddress = withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault()).getFromLocation(point.first, point.second, 1)
                    ?.firstOrNull()?.getAddressLine(0)
            }.getOrNull()
        }
    }
    fun requestFreshLocation() {
        val manager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null) {
            currentAddress = "Activa la ubicación del teléfono para buscar cerca."
            return
        }
        currentAddress = "Buscando tu ubicación…"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.getCurrentLocation(provider, null, context.mainExecutor) { location ->
                    currentCoordinates = location?.let { it.latitude to it.longitude }
                    if (location == null) currentAddress = "No se pudo obtener la ubicación. Comprueba el GPS e inténtalo de nuevo."
                }
            } else {
                @Suppress("DEPRECATION")
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        currentCoordinates = location.latitude to location.longitude
                        manager.removeUpdates(this)
                    }
                    @Deprecated("Deprecated in Android")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) { currentAddress = "El proveedor de ubicación está desactivado." }
                }
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            currentAddress = "Permiso de ubicación denegado."
        } catch (_: IllegalArgumentException) {
            currentAddress = "No hay proveedor de ubicación disponible."
        }
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) requestFreshLocation()
        else currentAddress = "Activa el permiso de ubicación para buscar cerca."
    }
    var filter by remember { mutableStateOf("Todo") }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(currentCoordinates, filter) {
        val point = currentCoordinates ?: run { nearbyPlaces = emptyList(); return@LaunchedEffect }
        placesLoading = true
        placesError = null
        runCatching { loadNearbyPlaces(point.first, point.second, filter) }
            .onSuccess { nearbyPlaces = it }
            .onFailure { placesError = it.localizedMessage ?: "No se han podido cargar lugares cercanos." }
        placesLoading = false
    }

    var selectedPlace by remember { mutableStateOf<Place?>(null) }
    var selectedNearbyPlace by remember { mutableStateOf<NearbyPlace?>(null) }
    val explorePreferences = remember(context) { context.getSharedPreferences("pawly_explore", android.content.Context.MODE_PRIVATE) }
    var customCollections by remember(explorePreferences) {
        mutableStateOf(runCatching {
            val stored = JSONArray(explorePreferences.getString("custom_collections", "[]") ?: "[]")
            List(stored.length()) { stored.getString(it) }.distinct()
        }.getOrDefault(emptyList()))
    }
    var collectionContents by remember(explorePreferences) {
        mutableStateOf(runCatching {
            val stored = org.json.JSONObject(explorePreferences.getString("collection_contents", "{}") ?: "{}")
            buildMap {
                val keys = stored.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val values = stored.optJSONArray(key) ?: JSONArray()
                    put(key, List(values.length()) { values.getString(it) }.toSet())
                }
            }
        }.getOrDefault(emptyMap()))
    }
    var collectionDescriptions by remember(explorePreferences) {
        mutableStateOf(runCatching {
            val stored = org.json.JSONObject(explorePreferences.getString("collection_descriptions", "{}") ?: "{}")
            buildMap { val keys = stored.keys(); while (keys.hasNext()) { val key = keys.next(); put(key, stored.optString(key)) } }
        }.getOrDefault(emptyMap()))
    }
    var showCreateCollection by remember { mutableStateOf(false) }
    var showCollectionPicker by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }
    var newCollectionDescription by remember { mutableStateOf("") }
    var collectionNotice by remember { mutableStateOf<String?>(null) }
    var viewingCollection by remember { mutableStateOf<String?>(null) }
    fun persistCollections() {
        val names = JSONArray().apply { customCollections.forEach { put(it) } }
        val content = org.json.JSONObject().apply {
            collectionContents.forEach { (name, values) -> put(name, JSONArray().apply { values.forEach { put(it) } }) }
        }
        val descriptions = org.json.JSONObject().apply { collectionDescriptions.forEach { (name, description) -> put(name, description) } }
        explorePreferences.edit().putString("custom_collections", names.toString())
            .putString("collection_contents", content.toString())
            .putString("collection_descriptions", descriptions.toString()).apply()
    }
    var collectionsLoaded by remember { mutableStateOf(false) }
    var collectionSyncMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(repository) {
        val repo = repository
        if (repo == null || repo.currentUid == null) {
            collectionsLoaded = true
        } else {
            runCatching { repo.loadExploreCollections() }
                .onSuccess { remote ->
                    if (remote.isNotEmpty()) {
                        customCollections = remote.map { it.name }
                        collectionContents = remote.associate { it.name to it.entries }
                        collectionDescriptions = remote.associate { it.name to it.description }
                        persistCollections()
                    } else if (customCollections.isNotEmpty()) {
                        repo.syncExploreCollections(customCollections.map { name ->
                            PawlyExploreCollection(name, collectionDescriptions[name].orEmpty(), collectionContents[name].orEmpty())
                        })
                    }
                    collectionSyncMessage = "Colecciones sincronizadas con tu cuenta Pawly."
                }
                .onFailure { collectionSyncMessage = "No se han podido sincronizar las colecciones. Se conservan en este dispositivo." }
            collectionsLoaded = true
        }
    }
    LaunchedEffect(customCollections, collectionContents, collectionDescriptions, collectionsLoaded, repository) {
        val repo = repository
        if (collectionsLoaded && repo?.currentUid != null) {
            runCatching {
                repo.syncExploreCollections(customCollections.map { name ->
                    PawlyExploreCollection(name, collectionDescriptions[name].orEmpty(), collectionContents[name].orEmpty())
                })
            }.onFailure {
                collectionSyncMessage = "Sin conexión: los cambios de las colecciones siguen guardados en este dispositivo."
            }.onSuccess {
                collectionSyncMessage = "Colecciones sincronizadas con tu cuenta Pawly."
            }
        }
    }
    val filters = listOf("Todo", "Naturaleza", "Playas", "Comer", "Alojarse")
    val places = listOf(
        Place("🌿", "Naturaleza", "Rutas y senderos", "Ideas para caminar juntos", "Busca caminos, sombra y agua antes de salir.", Color(0xFFE6EEDB), "https://images.unsplash.com/photo-1470770841072-f978cf4d019e?w=1000&h=500&fit=crop"),
        Place("🌊", "Playas", "Playas caninas", "Consulta normas y temporada", "Las restricciones cambian según municipio y época del año.", Color(0xFFDDEBF0), "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1000&h=500&fit=crop"),
        Place("☕", "Comer", "Terrazas dog-friendly", "Cafés y restaurantes", "Comprueba siempre con el establecimiento antes de ir.", Color(0xFFF4E6D9), "https://images.unsplash.com/photo-1554118811-1e0d58224f24?w=1000&h=500&fit=crop"),
        Place("🏡", "Alojarse", "Escapadas con perro", "Alojamientos pet-friendly", "Revisa tamaño permitido, suplementos y condiciones.", Color(0xFFEDE3F0), "https://images.unsplash.com/photo-1449158743715-0a90ebb6d2d8?w=1000&h=500&fit=crop")
    )
    val visible = places.filter { (filter == "Todo" || it.category == filter) && (query.isBlank() || it.name.contains(query, true) || it.description.contains(query, true)) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        item {
            if (elegantDesign) {
                Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(27.dp))) {
                    AsyncImage(
                        model = "https://images.unsplash.com/photo-1470770841072-f978cf4d019e?w=1200&h=700&fit=crop",
                        contentDescription = tr("Paisaje de montaña para descubrir con tu perro"),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD910251F)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(tr("AVENTURAS POR COMPARTIR"), color = Color.White.copy(alpha = .85f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp)
                        Text(tr("El mundo es mejor cuando vais juntos."), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
            Text(tr("EXPLORA"), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.5.sp)
            Text(tr("El mundo es mejor\ncuando vais juntos."), style = MaterialTheme.typography.headlineLarge)
            Text(tr("Ideas para vuestra próxima aventura."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = {
                    val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (!hasLocation) locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    else requestFreshLocation()
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(tr(if (currentCoordinates == null) "Usar mi ubicación" else "Actualizar ubicación"))
            }
            currentAddress?.let { address ->
                Text(tr(address), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Busca un lugar real"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(tr("Pawly abre el mapa para consultar resultados actuales; comprueba siempre las condiciones con el establecimiento."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = mapSearchQuery,
                        onValueChange = { mapSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(tr("Ej. veterinario 24 horas, parque canino…")) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        shape = RoundedCornerShape(13.dp)
                    )
                    Button(
                        onClick = {
                            val clean = mapSearchQuery.trim()
                            if (clean.isNotBlank()) {
                                val point = currentCoordinates
                                val url = if (point != null) {
                                    "https://www.google.com/maps/search/${Uri.encode(clean)}/@${point.first},${point.second},14z"
                                } else "https://www.google.com/maps/search/${Uri.encode(clean)}"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                        enabled = mapSearchQuery.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Icon(Icons.Default.Map, null)
                        Spacer(Modifier.width(7.dp))
                        Text(tr("Buscar en el mapa"))
                    }
                }
            }
            currentCoordinates?.let { (latitude, longitude) ->
                val point = "$latitude,$longitude"
                Text(tr("Mapa de tu zona"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                val nearbyMapMarkers = buildList {
                    add(PawlyMapMarker(latitude, longitude, "Tu ubicación"))
                    nearbyPlaces.take(40).forEach { place ->
                        add(PawlyMapMarker(place.latitude, place.longitude, place.name, place.category))
                    }
                }
                PawlyOpenStreetMap(
                    modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(20.dp)),
                    centerLatitude = latitude,
                    centerLongitude = longitude,
                    zoom = 12.0,
                    markers = nearbyMapMarkers
                )
                Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val searches = listOf("Parques caninos" to "parques para perros", "Protectoras" to "protectoras de animales", "Veterinarios" to "veterinarios", "Urgencias veterinarias" to "urgencias veterinarias 24 horas", "Playas caninas" to "playas para perros", "Restaurantes dog-friendly" to "restaurantes que admiten perros", "Alojamientos" to "alojamientos que admiten perros", "Rutas" to "rutas de senderismo aptas para perros")
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(tr("Cerca de la ubicación obtenida"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    searches.forEach { (label, term) ->
                        TextButton(onClick = {
                            val encoded = Uri.encode(term)
                            val geo = Uri.parse("geo:$point?q=$encoded")
                            val mapsApp = Intent(Intent.ACTION_VIEW, geo).setPackage("com.google.android.apps.maps")
                            if (mapsApp.resolveActivity(context.packageManager) != null) context.startActivity(mapsApp)
                            else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/$encoded/@$point,14z")))
                        }) { Text(tr(label)) }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(tr("Buscar planes o lugares…")) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = Line,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        item {
            var filterMenuExpanded by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { filterMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(9.dp))
                    Text(tr("Categoría: ") + tr(filter), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    Icon(Icons.Default.ExpandMore, null)
                }
                DropdownMenu(expanded = filterMenuExpanded, onDismissRequest = { filterMenuExpanded = false }, modifier = Modifier.fillMaxWidth(.9f)) {
                    filters.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(tr(option), fontWeight = if (filter == option) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = { if (filter == option) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = { filter = option; filterMenuExpanded = false }
                        )
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primary) {
                Row(Modifier.fillMaxWidth().padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("VUESTROS FAVORITOS"), color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(tr("${savedPlaces.size} lugares guardados"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(tr("Guarda ideas para otro día."), color = Color.White.copy(alpha = .72f), fontSize = 11.sp)
                    }
                    Icon(Icons.Filled.Bookmark, contentDescription = null, tint = Lime, modifier = Modifier.size(32.dp))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("TUS COLECCIONES"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.2.sp)
                    Text(tr("Organiza tus ideas para futuras salidas"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalButton(onClick = { newCollectionName = ""; newCollectionDescription = ""; showCreateCollection = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text(tr("Crear"))
                }
            }
            if (customCollections.isEmpty()) {
                Text(tr("Aún no tienes colecciones. Crea, por ejemplo, «Playas para el verano» o «Rutas pendientes»."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    customCollections.forEach { name ->
                        Surface(
                            onClick = { viewingCollection = name },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                        ) {
                            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(11.dp).size(22.dp))
                                }
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(collectionDescriptions[name].orEmpty().ifBlank { "Tu lista personal de lugares e ideas" }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${(collectionContents[name] ?: emptySet()).size} elementos", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = {
                                    customCollections = customCollections - name
                                    collectionContents = collectionContents - name
                                    collectionDescriptions = collectionDescriptions - name
                                    persistCollections()
                                }) { Icon(Icons.Default.DeleteOutline, contentDescription = tr("Eliminar colección")) }
                                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item { SectionHeading(if (currentCoordinates != null) "Lugares cercanos" else "Ideas para vuestra próxima salida", if (currentCoordinates != null) "Resultados de OpenStreetMap en un radio aproximado de 12 km" else "Activa tu ubicación para consultar lugares reales") }
        if (currentCoordinates != null) {
            if (placesLoading) item { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text(tr("Buscando lugares cercanos…"), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            if (placesError != null) item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                    Text(placesError!!, modifier = Modifier.padding(13.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!placesLoading && placesError == null && nearbyPlaces.isEmpty()) item {
                Text(tr("No hay lugares cartografiados en esta categoría cerca de tu ubicación. Prueba otra categoría o amplía la búsqueda en el mapa."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
            }
            items(nearbyPlaces, key = { it.id }) { place ->
                NearbyPlaceCard(
                    place = place,
                    saved = place.name in savedPlaces,
                    onSave = { onToggleSave(place.name) },
                    onAddToCollection = { selectedNearbyPlace = place; showCollectionPicker = true },
                    onOpenMap = {
                        val uri = Uri.parse("geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}(${Uri.encode(place.name)})")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
                        else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/?mlat=${place.latitude}&mlon=${place.longitude}#map=16/${place.latitude}/${place.longitude}")))
                    }
                )
            }
        } else {
            if (visible.isEmpty()) item { Text(tr("No hay resultados. Prueba con otra búsqueda."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)) }
            items(visible) { place ->
                PlaceCard(place = place, saved = place.name in savedPlaces, onOpen = { selectedPlace = place }, onSave = { onToggleSave(place.name) })
            }
        }
        item {
            Text(tr("Antes de salir: verifica horarios, acceso de perros, normas locales y condiciones con cada lugar."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
    selectedPlace?.let { place ->
        val mapSearch = when (place.category) {
            "Naturaleza" -> "rutas de senderismo aptas para perros"
            "Playas" -> "playas para perros y playas caninas"
            "Comer" -> "terrazas y restaurantes que admiten perros"
            "Alojarse" -> "alojamientos pet friendly que admiten perros"
            else -> place.name
        }
        AlertDialog(
            onDismissRequest = { selectedPlace = null },
            icon = { Text(place.emoji, fontSize = 30.sp) },
            title = { Text(tr(place.name), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr(place.detail), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(tr(place.description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(tr("Esta tarjeta es una idea de plan, no un establecimiento verificado. Consulta las normas, horarios y condiciones antes de salir."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val point = currentCoordinates
                    val encoded = Uri.encode(mapSearch + if (point == null) " cerca de mí" else "")
                    val url = if (point != null) {
                        "https://www.google.com/maps/search/$encoded/@${point.first},${point.second},13z"
                    } else "https://www.google.com/maps/search/$encoded"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    selectedPlace = null
                }) { Text(tr("Buscar opciones en Maps")) }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showCollectionPicker = true }) {
                        Icon(Icons.Default.FolderOpen, null)
                        Spacer(Modifier.width(5.dp))
                        Text(tr("Colección"))
                    }
                    TextButton(onClick = { onToggleSave(place.name) }) {
                        Icon(if (place.name in savedPlaces) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, null)
                        Spacer(Modifier.width(5.dp))
                        Text(tr(if (place.name in savedPlaces) "Guardado" else "Guardar idea"))
                    }
                    TextButton(onClick = { selectedPlace = null }) { Text(tr("Cerrar")) }
                }
            }
        )
    }
    viewingCollection?.let { name ->
        AlertDialog(
            onDismissRequest = { viewingCollection = null },
            title = { Text(name, fontWeight = FontWeight.Bold) },
            text = {
                val entries = collectionContents[name] ?: emptySet()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    collectionDescriptions[name]?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${entries.size} elementos guardados", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    if (entries.isEmpty()) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(Icons.Default.BookmarkAdd, null, tint = MaterialTheme.colorScheme.primary)
                                Text(tr("Tu colección está lista"), fontWeight = FontWeight.Bold)
                                Text(tr("Abre una idea de Explora y utiliza «Colección» para guardarla aquí."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        entries.forEach { entry ->
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(13.dp)) {
                                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(9.dp))
                                    Text(entry, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                    IconButton(onClick = {
                                        collectionContents = collectionContents + (name to entries - entry)
                                        persistCollections()
                                    }) { Icon(Icons.Default.DeleteOutline, contentDescription = tr("Quitar de la colección")) }
                                }
                            }
                        }
                    }
                    Text(collectionSyncMessage ?: if (repository?.currentUid != null) tr("Tus colecciones se sincronizan con tu cuenta Pawly.") else tr("Inicia sesión para sincronizar tus colecciones entre dispositivos."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { viewingCollection = null }) { Text(tr("Cerrar")) } }
        )
    }
    if (showCreateCollection) {
        AlertDialog(
            onDismissRequest = { showCreateCollection = false },
            title = { Text(tr("Nueva colección")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("Crea un espacio para organizar playas, rutas, restaurantes y planes que quieras guardar."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = newCollectionName, onValueChange = { newCollectionName = it.take(32) }, label = { Text(tr("Nombre de la colección")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = newCollectionDescription, onValueChange = { newCollectionDescription = it.take(120) }, label = { Text(tr("Descripción (opcional)")) }, placeholder = { Text(tr("Ej. Playas tranquilas para el verano")) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clean = newCollectionName.trim()
                    if (clean.isNotBlank() && customCollections.none { it.equals(clean, true) } && customCollections.size < 30) {
                        customCollections = customCollections + clean
                        collectionContents = collectionContents + (clean to emptySet())
                        collectionDescriptions = collectionDescriptions + (clean to newCollectionDescription.trim())
                        persistCollections()
                        showCreateCollection = false
                    }
                }, enabled = newCollectionName.trim().isNotBlank() && customCollections.size < 30) { Text(tr("Crear")) }
            },
            dismissButton = { TextButton(onClick = { showCreateCollection = false }) { Text(tr("Cancelar")) } }
        )
    }
    val collectionTarget = selectedPlace?.name ?: selectedNearbyPlace?.name
    if (showCollectionPicker && collectionTarget != null) {
        AlertDialog(
            onDismissRequest = { showCollectionPicker = false; selectedNearbyPlace = null },
            title = { Text(tr("Guardar en una colección")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(collectionTarget, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    if (customCollections.isEmpty()) Text(tr("Primero crea una colección para organizar tus ideas."))
                    else customCollections.forEach { name ->
                        val isSaved = collectionTarget in (collectionContents[name] ?: emptySet())
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isSaved, onCheckedChange = { checked ->
                                val current = collectionContents[name] ?: emptySet()
                                collectionContents = collectionContents + (name to if (checked) current + collectionTarget else current - collectionTarget)
                                persistCollections()
                            })
                            Text(name, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCollectionPicker = false; selectedNearbyPlace = null }) { Text(tr("Listo")) } },
            dismissButton = {
                TextButton(onClick = {
                    showCollectionPicker = false
                    selectedNearbyPlace = null
                    newCollectionName = ""
                    newCollectionDescription = ""
                    showCreateCollection = true
                }) { Text(tr("Nueva colección")) }
            }
        )
    }
}

@Composable
private fun NearbyPlaceCard(place: NearbyPlace, saved: Boolean, onSave: () -> Unit, onAddToCollection: () -> Unit, onOpenMap: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(19.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(place.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(place.category, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onSave) {
                    Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = if (saved) "Guardado" else "Guardar")
                }
            }
            Text(place.address, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onAddToCollection, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(5.dp))
                    Text(tr("Colección"), maxLines = 1)
                }
                Button(onClick = onOpenMap, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Map, null)
                    Spacer(Modifier.width(5.dp))
                    Text(tr("Ver mapa"), maxLines = 1)
                }
            }
            Text(tr("Datos cartográficos comunitarios. Comprueba que el lugar admite perros antes de desplazarte."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PlaceCard(place: Place, saved: Boolean, onOpen: () -> Unit, onSave: () -> Unit) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(25.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(178.dp)) {
                AsyncImage(
                    model = place.imageUrl,
                    contentDescription = tr(place.name),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0x990E211B)))
                    )
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(13.dp),
                    color = Color.White.copy(alpha = .94f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        place.category.uppercase(),
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        color = Forest,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
                IconButton(
                    onClick = onSave,
                    modifier = Modifier.align(Alignment.TopEnd).padding(7.dp)
                        .background(Color.White.copy(alpha = .94f), CircleShape)
                ) {
                    Icon(
                        if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = tr(if (saved) "Quitar de guardados" else "Guardar idea"),
                        tint = Forest
                    )
                }
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 17.dp, vertical = 15.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(tr(place.name), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(tr(place.detail), color = Color.White.copy(alpha = .9f), fontSize = 12.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    place.description,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.ArrowForward, contentDescription = tr("Ver idea"), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
