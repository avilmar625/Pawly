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

private data class Place(
    val emoji: String,
    val category: String,
    val name: String,
    val detail: String,
    val description: String,
    val color: Color
)

@Composable
internal fun ExploreScreen(savedPlaces: Set<String>, onToggleSave: (String) -> Unit) {
    val context = LocalContext.current
    var currentCoordinates by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var currentAddress by remember { mutableStateOf<String?>(null) }
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
    val filters = listOf("Todo", "Naturaleza", "Playas", "Comer", "Alojarse")
    val places = listOf(
        Place("🌿", "Naturaleza", "Rutas y senderos", "Ideas para caminar juntos", "Busca caminos, sombra y agua antes de salir.", Color(0xFFE6EEDB)),
        Place("🌊", "Playas", "Playas caninas", "Consulta normas y temporada", "Las restricciones cambian según municipio y época del año.", Color(0xFFDDEBF0)),
        Place("☕", "Comer", "Terrazas dog-friendly", "Cafés y restaurantes", "Comprueba siempre con el establecimiento antes de ir.", Color(0xFFF4E6D9)),
        Place("🏡", "Alojarse", "Escapadas con perro", "Alojamientos pet-friendly", "Revisa tamaño permitido, suplementos y condiciones.", Color(0xFFEDE3F0))
    )
    val visible = places.filter { (filter == "Todo" || it.category == filter) && (query.isBlank() || it.name.contains(query, true) || it.description.contains(query, true)) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        item {
            Text("EXPLORA", color = Forest, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.5.sp)
            Text("El mundo es mejor\ncuando vais juntos.", style = MaterialTheme.typography.headlineLarge)
            Text("Ideas para vuestra próxima aventura.", color = Muted, style = MaterialTheme.typography.bodyMedium)
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
                Text(if (currentCoordinates == null) "Usar mi ubicación" else "Abrir lugares cercanos")
            }
            currentAddress?.let { address ->
                Text(address, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            currentCoordinates?.let { (latitude, longitude) ->
                val point = "$latitude,$longitude"
                val searches = listOf("Parques para perros" to "parques para perros", "Rutas para perros" to "rutas de senderismo aptas para perros", "Playas caninas" to "playas para perros")
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Cerca de la ubicación obtenida", color = ForestLight, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
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
                    unfocusedContainerColor = Paper,
                    focusedContainerColor = Paper,
                    unfocusedBorderColor = Line,
                    focusedBorderColor = Forest
                )
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                filters.forEach { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { filter = item },
                        label = { Text(item, fontSize = 11.sp) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Forest,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = Forest) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("VUESTROS FAVORITOS", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("${savedPlaces.size} lugares guardados", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Guarda ideas para otro día.", color = Color.White.copy(alpha = .72f), fontSize = 11.sp)
                    }
                    Icon(Icons.Filled.Bookmark, contentDescription = null, tint = Lime, modifier = Modifier.size(32.dp))
                }
            }
        }
        item { SectionHeading("Inspiración para salir", "Contenido ilustrativo: aún no son lugares verificados") }
        if (visible.isEmpty()) {
            item { Text("No hay resultados. Prueba con otra búsqueda.", color = Muted, modifier = Modifier.padding(12.dp)) }
        }
        items(visible) { place ->
            PlaceCard(place = place, saved = place.name in savedPlaces, onSave = { onToggleSave(place.name) })
        }
        item {
            Text("Antes de salir: verifica horarios, acceso de perros, normas locales y condiciones con cada lugar.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun PlaceCard(place: Place, saved: Boolean, onSave: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = Paper, shadowElevation = 1.dp) {
        Column {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(17.dp)).background(place.color), contentAlignment = Alignment.Center) {
                    Text(place.emoji, fontSize = 29.sp)
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(place.category.uppercase(), color = ForestLight, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(place.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(place.detail, color = Muted, fontSize = 11.sp)
                }
                IconButton(onClick = onSave) {
                    Icon(if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = "Guardar", tint = Forest)
                }
            }
            HorizontalDivider(color = Line)
            Text(place.description, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
    }
}
