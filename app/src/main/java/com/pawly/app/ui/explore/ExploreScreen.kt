package com.pawly.app

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

@Composable
internal fun ExploreScreen(savedPlaces: Set<String>, onToggleSave: (String) -> Unit, elegantDesign: Boolean = false) {
    val context = LocalContext.current
    var currentCoordinates by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var currentAddress by remember { mutableStateOf<String?>(null) }
    var mapSearchQuery by remember { mutableStateOf("") }
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
    var selectedPlace by remember { mutableStateOf<Place?>(null) }
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
                        contentDescription = "Paisaje de montaña para descubrir con tu perro",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD910251F)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("AVENTURAS POR COMPARTIR", color = Color.White.copy(alpha = .85f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp)
                        Text("El mundo es mejor cuando vais juntos.", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
            Text("EXPLORA", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.5.sp)
            Text("El mundo es mejor\ncuando vais juntos.", style = MaterialTheme.typography.headlineLarge)
            Text("Ideas para vuestra próxima aventura.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
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
                Text(if (currentCoordinates == null) "Usar mi ubicación" else "Actualizar ubicación")
            }
            currentAddress?.let { address ->
                Text(address, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Busca un lugar real", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("Pawly abre el mapa para consultar resultados actuales; comprueba siempre las condiciones con el establecimiento.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = mapSearchQuery,
                        onValueChange = { mapSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Ej. veterinario 24 horas, parque canino…") },
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
                        Text("Buscar en el mapa")
                    }
                }
            }
            currentCoordinates?.let { (latitude, longitude) ->
                val point = "$latitude,$longitude"
                val searches = listOf("Parques caninos" to "parques para perros", "Protectoras" to "protectoras de animales", "Veterinarios" to "veterinarios", "Urgencias veterinarias" to "urgencias veterinarias 24 horas", "Playas caninas" to "playas para perros", "Restaurantes dog-friendly" to "restaurantes que admiten perros", "Alojamientos" to "alojamientos que admiten perros", "Rutas" to "rutas de senderismo aptas para perros")
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Cerca de la ubicación obtenida", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    searches.forEach { (label, term) ->
                        TextButton(onClick = {
                            val encoded = Uri.encode(term)
                            val geo = Uri.parse("geo:$point?q=$encoded")
                            val mapsApp = Intent(Intent.ACTION_VIEW, geo).setPackage("com.google.android.apps.maps")
                            if (mapsApp.resolveActivity(context.packageManager) != null) context.startActivity(mapsApp)
                            else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/$encoded/@$point,14z")))
                        }) { Text(label) }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar planes o lugares…") },
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
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                filters.forEach { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { filter = item },
                        label = { Text(item, fontSize = 11.sp) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primary) {
                Row(Modifier.fillMaxWidth().padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("VUESTROS FAVORITOS", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("${savedPlaces.size} lugares guardados", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Guarda ideas para otro día.", color = Color.White.copy(alpha = .72f), fontSize = 11.sp)
                    }
                    Icon(Icons.Filled.Bookmark, contentDescription = null, tint = Lime, modifier = Modifier.size(32.dp))
                }
            }
        }
        item { SectionHeading("Ideas para vuestra próxima salida", "Guarda categorías e inicia búsquedas reales cuando quieras") }
        if (visible.isEmpty()) {
            item { Text("No hay resultados. Prueba con otra búsqueda.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)) }
        }
        items(visible) { place ->
            PlaceCard(place = place, saved = place.name in savedPlaces, onOpen = { selectedPlace = place }, onSave = { onToggleSave(place.name) })
        }
        item {
            Text("Antes de salir: verifica horarios, acceso de perros, normas locales y condiciones con cada lugar.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
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
            title = { Text(place.name, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(place.detail, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(place.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Esta tarjeta es una idea de plan, no un establecimiento verificado. Consulta las normas, horarios y condiciones antes de salir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                }) { Text("Buscar opciones en Maps") }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onToggleSave(place.name) }) {
                        Icon(if (place.name in savedPlaces) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, null)
                        Spacer(Modifier.width(5.dp))
                        Text(if (place.name in savedPlaces) "Guardado" else "Guardar idea")
                    }
                    TextButton(onClick = { selectedPlace = null }) { Text("Cerrar") }
                }
            }
        )
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
                    contentDescription = place.name,
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
                        contentDescription = if (saved) "Quitar de guardados" else "Guardar idea",
                        tint = Forest
                    )
                }
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 17.dp, vertical = 15.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(place.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(place.detail, color = Color.White.copy(alpha = .9f), fontSize = 12.sp)
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
                Icon(Icons.Filled.ArrowForward, contentDescription = "Ver idea", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
