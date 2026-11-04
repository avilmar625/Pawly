package com.pawly.app

import android.os.Bundle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Forest = Color(0xFF173F35)
private val ForestLight = Color(0xFF2D6655)
private val Cream = Color(0xFFF8F7F2)
private val Paper = Color(0xFFFFFFFF)
private val Ink = Color(0xFF202923)
private val Muted = Color(0xFF7C887F)
private val Lime = Color(0xFFD8E8A8)
private val Peach = Color(0xFFF4D7C5)
private val Line = Color(0xFFE9EAE3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PawlyTheme { PawlyApp() } }
    }
}

@Composable
private fun PawlyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Forest,
            onPrimary = Color.White,
            secondary = ForestLight,
            background = Cream,
            surface = Paper,
            onSurface = Ink,
            onBackground = Ink
        ),
        typography = Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 35.sp),
            headlineMedium = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold),
            titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
            labelMedium = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        ),
        content = content
    )
}

private enum class Tab(val label: String) {
    HOME("Inicio"), EXPLORE("Explorar"), HEALTH("Salud"), DIARY("Diario")
}

@Composable
private fun PawlyApp() {
    var selected by remember { mutableStateOf(Tab.HOME) }
    var showAbout by remember { mutableStateOf(false) }
    var dogName by remember { mutableStateOf("Nala") }
    var walkDone by remember { mutableStateOf(false) }
    var medicineDone by remember { mutableStateOf(false) }
    var savedPlaces by remember { mutableIntStateOf(2) }

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
                    onAbout = { showAbout = true }
                )
                Tab.EXPLORE -> ExploreScreen(
                    savedPlaces = savedPlaces,
                    onSave = { savedPlaces += 1 }
                )
                Tab.HEALTH -> HealthScreen(
                    medicineDone = medicineDone,
                    onMedicineToggle = { medicineDone = it }
                )
                Tab.DIARY -> DiaryScreen(dogName = dogName)
            }
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
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
    onAbout: () -> Unit
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
            DogHeroCard(dogName)
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
                MetricCard("2", "planes pendientes", Icons.Filled.Explore, Modifier.weight(1f))
                MetricCard("5", "recuerdos guardados", Icons.Filled.PhotoLibrary, Modifier.weight(1f))
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
private fun DogHeroCard(name: String) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Forest, Color(0xFF285D4D), Color(0xFF497A62))))
            .padding(21.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("VUESTRO COMPAÑERO", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(name, color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                    Text("Perfil de demostración", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
                }
                Box(
                    Modifier.size(82.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🐕", fontSize = 47.sp)
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
private fun ExploreScreen(savedPlaces: Int, onSave: () -> Unit) {
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
                        Text("$savedPlaces lugares guardados", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
            PlaceCard(place = place, onSave = onSave)
        }
        item {
            Text("Antes de salir: verifica horarios, acceso de perros, normas locales y condiciones con cada lugar.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PlaceCard(place: Place, onSave: () -> Unit) {
    var saved by remember { mutableStateOf(false) }
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
                IconButton(onClick = {
                    saved = !saved
                    if (saved) onSave()
                }) {
                    Icon(if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = "Guardar", tint = Forest)
                }
            }
            HorizontalDivider(color = Line)
            Text(place.description, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
    }
}

@Composable
private fun HealthScreen(medicineDone: Boolean, onMedicineToggle: (Boolean) -> Unit) {
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
            HealthItem(Icons.Filled.Vaccines, "Vacunación", "Añade aquí la próxima fecha", "Sin fecha")
        }
        item {
            HealthItem(Icons.Filled.BugReport, "Desparasitación", "Registra el producto y la pauta indicada", "Sin fecha")
        }
        item {
            HealthItem(Icons.Filled.MedicalServices, "Próxima revisión", "Guarda las citas de vuestro veterinario", "Sin fecha")
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
private fun HealthItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, date: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = Paper, shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE9F0E5)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Forest)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, fontSize = 11.sp)
            }
            Text(date, color = ForestLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DiaryScreen(dogName: String) {
    var note by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(listOf("Primer paseo de la semana", "Una tarde de juegos")) }
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
                                entries = listOf(note.trim()) + entries
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
        item { SectionHeading("Pequeñas grandes historias", "Diario de $dogName · contenido de ejemplo") }
        items(entries) { entry ->
            Surface(shape = RoundedCornerShape(20.dp), color = Paper, shadowElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(49.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFE9F0E5)), contentAlignment = Alignment.Center) {
                        Text("🐾", fontSize = 24.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry, fontWeight = FontWeight.SemiBold)
                        Text("Recuerdo guardado en esta sesión", color = Muted, fontSize = 11.sp)
                    }
                    Icon(Icons.Filled.FavoriteBorder, contentDescription = null, tint = Peach)
                }
            }
        }
        item {
            Text("En esta versión las notas se mantienen en memoria mientras la app está abierta. La galería y el almacenamiento permanente llegarán en una siguiente fase.", color = Muted, fontSize = 11.sp)
        }
    }
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
