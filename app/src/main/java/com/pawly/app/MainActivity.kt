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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.json.JSONArray

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PawlyTheme { PawlyApp() } }
    }
}

private enum class Tab(val label: String) {
    HOME("Inicio"), EXPLORE("Explorar"), HEALTH("Salud"), DIARY("Diario")
}

@Composable
private fun PawlyApp() {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("pawly_local", android.content.Context.MODE_PRIVATE) }
    var selected by remember { mutableStateOf(Tab.HOME) }
    var showAbout by remember { mutableStateOf(false) }
    var showProfileEditor by remember { mutableStateOf(false) }
    var profileDraft by remember { mutableStateOf("") }
    var dogName by remember { mutableStateOf(preferences.getString("dog_name", "Nala") ?: "Nala") }
    var dogProfiles by remember { mutableStateOf(preferences.getStringSet("dog_profiles", setOf(dogName))?.toSet() ?: setOf(dogName)) }
    var dogPhotoPath by remember { mutableStateOf(preferences.getString("dog_photo_path", null)) }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val destination = File(context.filesDir, "dog-profile-${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            dogPhotoPath = destination.absolutePath
        }
    }
    val cameraCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) runCatching {
            val destination = File(context.filesDir, "dog-profile-${System.currentTimeMillis()}.jpg")
            destination.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            dogPhotoPath = destination.absolutePath
        }
    }
    val dogPhoto = remember(dogPhotoPath) { dogPhotoPath?.let(BitmapFactory::decodeFile) }
    var walkDone by remember { mutableStateOf(preferences.getBoolean("walk_done", false)) }
    var medicineDone by remember { mutableStateOf(preferences.getBoolean("medicine_done", false)) }
    var healthDates by remember {
        mutableStateOf(runCatching {
            val json = JSONArray(preferences.getString("health_dates", "[]") ?: "[]")
            buildMap { for (i in 0 until json.length()) { val row = json.getJSONArray(i); put(row.getString(0), row.getString(1)) } }
        }.getOrDefault(emptyMap()))
    }
    var savedPlaceNames by remember {
        mutableStateOf(preferences.getStringSet("saved_places", emptySet())?.toSet() ?: emptySet())
    }
    val storedEntries = remember(preferences) {
        runCatching {
            val json = JSONArray(preferences.getString("diary_entries", "[]") ?: "[]")
            List(json.length()) { index -> json.getString(index) }
        }.getOrDefault(emptyList())
    }
    var diaryEntries by remember { mutableStateOf(storedEntries) }

    LaunchedEffect(dogName) { preferences.edit().putString("dog_name", dogName).apply() }
    LaunchedEffect(dogProfiles) { preferences.edit().putStringSet("dog_profiles", dogProfiles.toSet()).apply() }
    LaunchedEffect(dogPhotoPath) { preferences.edit().putString("dog_photo_path", dogPhotoPath).apply() }
    LaunchedEffect(walkDone) { preferences.edit().putBoolean("walk_done", walkDone).apply() }
    LaunchedEffect(medicineDone) { preferences.edit().putBoolean("medicine_done", medicineDone).apply() }
    LaunchedEffect(healthDates) {
        val json = JSONArray()
        healthDates.forEach { (key, value) -> json.put(org.json.JSONArray().put(key).put(value)) }
        preferences.edit().putString("health_dates", json.toString()).apply()
    }
    LaunchedEffect(savedPlaceNames) {
        preferences.edit().putStringSet("saved_places", savedPlaceNames.toSet()).apply()
    }
    LaunchedEffect(diaryEntries) {
        val json = JSONArray()
        diaryEntries.forEach { entry -> json.put(entry) }
        preferences.edit().putString("diary_entries", json.toString()).apply()
    }

    Scaffold(
        containerColor = Cream,
        bottomBar = {
            NavigationBar(
                containerColor = Paper,
                contentColor = Forest,
                tonalElevation = 0.dp
            ) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = {
                            Icon(
                                when (tab) {
                                    Tab.HOME -> Icons.Filled.Home
                                    Tab.EXPLORE -> Icons.Filled.Explore
                                    Tab.HEALTH -> Icons.Filled.FavoriteBorder
                                    Tab.DIARY -> Icons.Filled.AutoStories
                                },
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Forest,
                            selectedTextColor = Forest,
                            indicatorColor = Lime.copy(alpha = 0.65f),
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                Tab.HOME -> HomeScreen(
                    dogName = dogName,
                    walkDone = walkDone,
                    medicineDone = medicineDone,
                    onWalkToggle = { walkDone = it },
                    onMedicineToggle = { medicineDone = it },
                    onExplore = { selected = Tab.EXPLORE },
                    onAbout = { showAbout = true },
                    onEditProfile = { profileDraft = dogName; showProfileEditor = true },
                    diaryCount = diaryEntries.size,
                    dogPhoto = dogPhoto
                )
                Tab.EXPLORE -> ExploreScreen(
                    savedPlaces = savedPlaceNames,
                    onToggleSave = { name ->
                        savedPlaceNames = if (name in savedPlaceNames) savedPlaceNames - name else savedPlaceNames + name
                    }
                )
                Tab.HEALTH -> HealthScreen(
                    medicineDone = medicineDone,
                    onMedicineToggle = { medicineDone = it },
                    dates = healthDates.filterKeys { it.startsWith("$dogName|") },
                    onDateChange = { key, value ->
                        val fullKey = "$dogName|$key"
                        healthDates = if (value == null) healthDates - fullKey else healthDates + (fullKey to value)
                    }
                )
                Tab.DIARY -> DiaryScreen(
                    dogName = dogName,
                    entries = diaryEntries,
                    onEntriesChange = { diaryEntries = it }
                )
            }
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
    if (showProfileEditor) {
        ProfileDialog(
            name = profileDraft,
            onNameChange = { profileDraft = it },
            onDismiss = { showProfileEditor = false },
            onSave = {
                val cleaned = profileDraft.trim()
                if (cleaned.isNotEmpty()) {
                    dogProfiles = (dogProfiles - dogName) + cleaned
                    dogName = cleaned
                }
                showProfileEditor = false
            },
            onGallery = { galleryPicker.launch("image/*") },
            onCamera = { cameraCapture.launch(null) },
            hasPhoto = dogPhoto != null,
            profiles = dogProfiles,
            onSelectProfile = { dogName = it; showProfileEditor = false },
            onAddProfile = {
                val cleaned = profileDraft.trim()
                if (cleaned.isNotEmpty()) { dogProfiles = dogProfiles + cleaned; dogName = cleaned; showProfileEditor = false }
            },
            onDeleteProfile = { name ->
                if (dogProfiles.size > 1) {
                    dogProfiles = dogProfiles - name
                    if (dogName == name) dogName = dogProfiles.firstOrNull() ?: "Nala"
                }
            }
        )
    }
}

@Composable
private fun HomeScreen(
    dogName: String,
    walkDone: Boolean,
    medicineDone: Boolean,
    onWalkToggle: (Boolean) -> Unit,
    onMedicineToggle: (Boolean) -> Unit,
    onExplore: () -> Unit,
    onAbout: () -> Unit,
    onEditProfile: () -> Unit,
    diaryCount: Int,
    dogPhoto: Bitmap?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PAWLY", color = Forest, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                    Text("Tu vida, a su lado.", style = MaterialTheme.typography.headlineLarge, color = Ink)
                }
                Surface(
                    onClick = onAbout,
                    shape = CircleShape,
                    color = Paper,
                    shadowElevation = 1.dp
                ) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, contentDescription = "Acerca de Pawly", tint = Forest)
                    }
                }
            }
        }
        item {
            DogHeroCard(dogName, dogPhoto, onEditProfile)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Un día bonito empieza aquí", style = MaterialTheme.typography.titleLarge)
                    Text("Pequeños planes, grandes recuerdos.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
                Text("HOY", color = ForestLight, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(listOf(walkDone, medicineDone).count { !it }.toString(), "tareas pendientes", Icons.Filled.Explore, Modifier.weight(1f))
                MetricCard(diaryCount.toString(), "recuerdos guardados", Icons.Filled.PhotoLibrary, Modifier.weight(1f))
            }
        }
        item {
            SectionHeading("La rutina de hoy", "Un poquito cada día")
        }
        item {
            TaskRow(
                icon = Icons.Filled.DirectionsWalk,
                title = "Paseo y olfateo",
                subtitle = "Un rato para explorar sin prisa",
                checked = walkDone,
                onChecked = onWalkToggle,
                tint = Color(0xFFE9F0DB)
            )
        }
        item {
            TaskRow(
                icon = Icons.Filled.Medication,
                title = "Revisar cuidados",
                subtitle = "Recordatorio de ejemplo · datos demo",
                checked = medicineDone,
                onChecked = onMedicineToggle,
                tint = Color(0xFFF8E9DF)
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("¿Salimos a descubrir?", style = MaterialTheme.typography.titleLarge)
                    Text("Encuentra ideas para vuestra próxima aventura.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onExplore) {
                    Icon(Icons.Filled.ArrowForward, contentDescription = "Explorar", tint = Forest)
                }
            }
            Button(
                onClick = onExplore,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Forest)
            ) {
                Icon(Icons.Filled.Explore, contentDescription = null)
                Spacer(Modifier.width(9.dp))
                Text("Explorar lugares", fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Text("Pawly · Hecho para compartir más vida juntos.", color = Muted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun DogHeroCard(name: String, photo: Bitmap?, onEdit: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onEdit)
            .background(Brush.linearGradient(listOf(Forest, Color(0xFF285D4D), Color(0xFF497A62))))
            .padding(21.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("VUESTRO COMPAÑERO", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(name, color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                    Text("Toca para editar su perfil", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
                }
                Box(
                    Modifier.size(82.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (photo != null) Image(photo.asImageBitmap(), contentDescription = "Foto de $name", modifier = Modifier.fillMaxSize().clip(CircleShape))
                    else Text("🐕", fontSize = 47.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroPill("🐾  Aventurera")
                HeroPill("♡  Muy querida")
            }
        }
    }
}

@Composable
private fun HeroPill(text: String) {
    Surface(color = Color.White.copy(alpha = 0.13f), shape = RoundedCornerShape(50)) {
        Text(text, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
    }
}

@Composable
private fun MetricCard(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = Paper, shadowElevation = 1.dp) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = ForestLight, modifier = Modifier.size(21.dp))
            Spacer(Modifier.height(12.dp))
            Text(value, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(label, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}

@Composable
private fun TaskRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    tint: Color
) {
    Surface(shape = RoundedCornerShape(18.dp), color = Paper, shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(tint), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Forest)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = if (checked) Muted else Ink)
                Text(subtitle, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Checkbox(checked = checked, onCheckedChange = onChecked, colors = CheckboxDefaults.colors(checkedColor = Forest))
        }
    }
}

private data class Place(
    val emoji: String,
    val category: String,
    val name: String,
    val detail: String,
    val description: String,
    val color: Color
)

@Composable
private fun ExploreScreen(savedPlaces: Set<String>, onToggleSave: (String) -> Unit) {
    val context = LocalContext.current
    var currentCoordinates by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val manager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
            @Suppress("MissingPermission")
            val location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            currentCoordinates = location?.let { it.latitude to it.longitude }
        }
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
                    if (!hasLocation) {
                        locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    } else {
                        val manager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
                        @Suppress("MissingPermission")
                        val location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                            ?: manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        currentCoordinates = location?.let { it.latitude to it.longitude }
                    }
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (currentCoordinates == null) "Usar mi ubicación" else "Abrir lugares cercanos")
            }
            currentCoordinates?.let { (latitude, longitude) ->
                TextButton(onClick = {
                    val mapUri = Uri.parse("geo:$latitude,$longitude?q=parques+para+perros")
                    val googleMaps = Intent(Intent.ACTION_VIEW, mapUri).setPackage("com.google.android.apps.maps")
                    if (googleMaps.resolveActivity(context.packageManager) != null) context.startActivity(googleMaps)
                    else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/parques+para+perros/@$latitude,$longitude,14z")))
                }) { Text("Ver mapa alrededor de ${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}") }
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
private fun PlaceCard(place: Place, saved: Boolean, onSave: () -> Unit) {
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

@Composable
private fun HealthScreen(
    medicineDone: Boolean,
    onMedicineToggle: (Boolean) -> Unit,
    dates: Map<String, String>,
    onDateChange: (String, String?) -> Unit
) {
    val context = LocalContext.current
    fun selectDate(key: String) {
        val today = Calendar.getInstance()
        DatePickerDialog(context, { _, year, month, day ->
            onDateChange(key, "%04d-%02d-%02d".format(year, month + 1, day))
        }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show()
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        item {
            Text("BIENESTAR", color = Forest, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.5.sp)
            Text("Cuidar también\nes querer.", style = MaterialTheme.typography.headlineLarge)
            Text("Organiza su día a día con tranquilidad.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Surface(shape = RoundedCornerShape(23.dp), color = Color(0xFFE8EFE2)) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = Forest, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(13.dp))
                    Column {
                        Text("Su salud, siempre a mano", fontWeight = FontWeight.Bold, color = Forest)
                        Text("Una agenda sencilla para no olvidar nada.", color = ForestLight, fontSize = 11.sp)
                    }
                }
            }
        }
        item { SectionHeading("Próximos cuidados", "Ejemplos de recordatorios · no son pautas veterinarias") }
        item {
            HealthItem(Icons.Filled.Vaccines, "Vacunación", "Fecha orientativa de tu próxima cita", dates["Vacunación"] ?: "Añadir fecha", { selectDate("Vacunación") }, { onDateChange("Vacunación", null) })
        }
        item {
            HealthItem(Icons.Filled.BugReport, "Desparasitación", "Sigue la pauta indicada por tu veterinario", dates["Desparasitación"] ?: "Añadir fecha", { selectDate("Desparasitación") }, { onDateChange("Desparasitación", null) })
        }
        item {
            HealthItem(Icons.Filled.MedicalServices, "Próxima revisión", "Organiza una cita con vuestro veterinario", dates["Próxima revisión"] ?: "Añadir fecha", { selectDate("Próxima revisión") }, { onDateChange("Próxima revisión", null) })
        }
        item {
            TaskRow(
                icon = Icons.Filled.Medication,
                title = "Recordatorio diario",
                subtitle = "Ejemplo interactivo · márcalo al completarlo",
                checked = medicineDone,
                onChecked = onMedicineToggle,
                tint = Peach
            )
        }
        item {
            Text("Importante: Pawly no sustituye al veterinario. Consulta siempre con un profesional para vacunas, medicación, dosis y tratamientos.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun HealthItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, date: String, onClick: () -> Unit, onClear: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Paper, shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE9F0E5)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Forest)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(date, color = ForestLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                if (date != "Añadir fecha") TextButton(onClick = onClear, contentPadding = PaddingValues(0.dp)) { Text("Quitar", fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun DiaryScreen(
    dogName: String,
    entries: List<String>,
    onEntriesChange: (List<String>) -> Unit
) {
    var note by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        item {
            Text("VUESTROS RECUERDOS", color = Forest, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.5.sp)
            Text("Una vida llena\nde historias.", style = MaterialTheme.typography.headlineLarge)
            Text("Guarda esos momentos que merecen quedarse.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = Forest) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("UN RECUERDO NUEVO", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("¿Qué habéis hecho hoy?", color = Color.White.copy(alpha = .6f)) },
                        shape = RoundedCornerShape(15.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Lime,
                            unfocusedBorderColor = Color.White.copy(alpha = .3f),
                            cursorColor = Lime
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (note.isNotBlank()) {
                                onEntriesChange(listOf(note.trim()) + entries)
                                note = ""
                            }
                        },
                        enabled = note.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Forest),
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text("Guardar recuerdo")
                    }
                }
            }
        }
        item { SectionHeading("Pequeñas grandes historias", "Diario de $dogName · ${entries.size} recuerdos guardados") }
        items(entries) { entry ->
            Surface(shape = RoundedCornerShape(20.dp), color = Paper, shadowElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(49.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFE9F0E5)), contentAlignment = Alignment.Center) {
                        Text("🐾", fontSize = 24.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry, fontWeight = FontWeight.SemiBold)
                        Text("Guardado en este dispositivo", color = Muted, fontSize = 11.sp)
                    }
                    Icon(Icons.Filled.FavoriteBorder, contentDescription = null, tint = Peach)
                }
            }
        }
        item {
            Text("Tus recuerdos se guardan localmente en este dispositivo. No se sincronizan con otros teléfonos ni se incluyen fotografías.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ProfileDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    hasPhoto: Boolean,
    profiles: Set<String>,
    onSelectProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onDeleteProfile: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Perfil de tu compañero", color = Forest) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text("Nombre") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                Text(if (hasPhoto) "Foto de perfil guardada en este dispositivo" else "Añade una foto para reconocer a tu compañero", color = Muted, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onGallery) { Icon(Icons.Filled.PhotoLibrary, contentDescription = null); Spacer(Modifier.width(5.dp)); Text("Galería") }
                    OutlinedButton(onClick = onCamera) { Icon(Icons.Filled.PhotoCamera, contentDescription = null); Spacer(Modifier.width(5.dp)); Text("Cámara") }
                }
                Text("Perfiles de perros", color = Forest, fontWeight = FontWeight.SemiBold)
                profiles.sorted().forEach { profile ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onSelectProfile(profile) }, modifier = Modifier.weight(1f)) { Text(profile) }
                        if (profiles.size > 1) IconButton(onClick = { onDeleteProfile(profile) }) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Eliminar perfil $profile") }
                    }
                }
                OutlinedButton(onClick = onAddProfile) { Icon(Icons.Filled.Add, contentDescription = null); Text("Añadir con el nombre escrito") }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = name.isNotBlank()) { Text("Guardar", color = Forest) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Muted) }
        },
        containerColor = Paper
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        icon = { Text("🐾", fontSize = 34.sp) },
        title = { Text("Pawly", color = Forest, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Más aventuras. Más recuerdos. Más vida juntos.", color = Ink)
                Spacer(Modifier.height(5.dp))
                Text("Creadores", color = ForestLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Alex Vilarrubla Martín", fontWeight = FontWeight.SemiBold)
                Text("Maialen Cruz Tudanca", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text("Versión inicial 1.0.0 · Proyecto en desarrollo", color = Muted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = Forest) }
        }
    )
}
