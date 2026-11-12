package com.pawly.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class ProfessionalCategory(
    val id: String,
    val title: String,
    val description: String,
    val searchTerm: String,
    val icon: ImageVector
)

internal object ProfessionalDirectorySearch {
    val categories = listOf(
        ProfessionalCategory("training", "Adiestramiento", "Educación amable, obediencia y habilidades", "adiestradores de perros educación canina", Icons.Filled.School),
        ProfessionalCategory("behavior", "Etología", "Ayuda especializada para problemas de conducta", "etólogos veterinarios comportamiento animal", Icons.Filled.Psychology),
        ProfessionalCategory("vets", "Veterinarios", "Clínicas generales y especialistas", "clínicas veterinarias", Icons.Filled.MedicalServices),
        ProfessionalCategory("emergency", "Urgencias", "Atención veterinaria urgente 24 horas", "urgencias veterinarias 24 horas", Icons.Filled.HealthAndSafety),
        ProfessionalCategory("grooming", "Peluquería", "Higiene, baño y cuidado del pelaje", "peluquerías caninas y felinas", Icons.Filled.ContentCut),
        ProfessionalCategory("walking", "Paseadores", "Paseos y actividad adaptada", "paseadores de perros", Icons.Filled.DirectionsWalk),
        ProfessionalCategory("sitting", "Cuidadores", "Cuidado a domicilio y visitas", "cuidadores de mascotas a domicilio", Icons.Filled.Home),
        ProfessionalCategory("boarding", "Residencias", "Guardería y alojamiento", "residencias y guarderías para mascotas", Icons.Filled.Hotel),
        ProfessionalCategory("physio", "Fisioterapia", "Rehabilitación y movilidad", "fisioterapia y rehabilitación veterinaria", Icons.Filled.AccessibilityNew),
        ProfessionalCategory("nutrition", "Nutrición", "Asesoramiento nutricional profesional", "nutricionistas veterinarios para mascotas", Icons.Filled.Restaurant),
        ProfessionalCategory("shops", "Tiendas", "Comercios y alimentación especializada", "tiendas de mascotas", Icons.Filled.Storefront),
        ProfessionalCategory("other", "Otros servicios", "Otros profesionales para animales", "servicios profesionales para mascotas", Icons.Filled.Pets)
    )

    fun buildQuery(category: ProfessionalCategory, search: String, area: String): String =
        buildQuery(category.searchTerm, search, area)

    fun buildQuery(defaultSearchTerm: String, search: String, area: String): String {
        val service = search.trim().ifBlank { defaultSearchTerm.trim() }
        val place = area.trim()
        return if (place.isBlank()) service else "$service en $place"
    }
}

@Composable
internal fun ProfessionalServicesScreen(
    onBack: () -> Unit,
    elegantDesign: Boolean = false
) {
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf("training") }
    var search by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    val selected = ProfessionalDirectorySearch.categories.first { it.id == selectedId }

    fun openSearch(query: String) {
        val url = "https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}"
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")))
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹  Volver") }
            Spacer(Modifier.weight(1f))
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                Text("PAWLY · SERVICIOS", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PROFESIONALES PARA SU BIENESTAR", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
                Text("Encontrad a la persona adecuada.", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Explora servicios para cuidar, educar y acompañar a tu mascota en cada etapa.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        Text("¿Qué servicio necesitáis?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ProfessionalDirectorySearch.categories.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    pair.forEach { category ->
                        val active = selectedId == category.id
                        Surface(
                            onClick = { selectedId = category.id; search = "" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(17.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Icon(category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                Text(category.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(category.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, minLines = 2)
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Afina la búsqueda", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(selected.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Servicio o especialidad (opcional)") },
                    placeholder = { Text(selected.searchTerm) },
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = area,
                    onValueChange = { area = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Ciudad o zona (opcional)") },
                    placeholder = { Text("Ej. Málaga, Teatinos…") },
                    shape = RoundedCornerShape(14.dp)
                )
                Button(
                    onClick = { openSearch(ProfessionalDirectorySearch.buildQuery(selected, search, area)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Text("  Buscar profesionales en el mapa")
                }
                TextButton(
                    onClick = { openSearch(ProfessionalDirectorySearch.buildQuery(selected, search, area).replace(" en ", " ")) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("Buscar también en la web") }
            }
        }

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Antes de contratar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ProfessionalCheck("Comprueba titulación, experiencia y especialización.")
                ProfessionalCheck("Pregunta por el método de trabajo y evita técnicas basadas en miedo o dolor.")
                ProfessionalCheck("Solicita precio, condiciones, seguro y qué incluye el servicio.")
                ProfessionalCheck("En adiestramiento, pide una primera valoración y objetivos realistas.")
            }
        }

        Text(
            "Los resultados proceden de servicios externos y no están verificados ni contratados por Pawly. Comprueba identidad, credenciales, disponibilidad, precios y opiniones antes de facilitar datos o realizar pagos. Pawly no gestiona reservas ni pagos en esta versión.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Volver a Más") }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ProfessionalCheck(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
