package com.pawly.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MoreHubScreen(
    onLearn: () -> Unit,
    onDiary: () -> Unit,
    onSettings: () -> Unit,
    onProfessionals: () -> Unit,
    onLostPet: () -> Unit,
    onExportPetPdf: () -> Unit,
    language: String = "es",
    elegantDesign: Boolean = false
) {
    fun t(value: String) = PawlyI18n.text(value, language)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        if (elegantDesign) {
            Box(Modifier.fillMaxWidth().height(195.dp).clip(RoundedCornerShape(27.dp))) {
                coil3.compose.AsyncImage(model = "https://images.unsplash.com/photo-1543466835-00a7907e9de1?w=1200&h=700&fit=crop", contentDescription = "Perro disfrutando al aire libre", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE610251F)))))
                Column(Modifier.align(Alignment.BottomStart).padding(19.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("TU UNIVERSO PAWLY", color = Color.White.copy(alpha = .82f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
                    Text(t("Todo lo vuestro,\nen un mismo lugar."), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t("TU ESPACIO PAWLY"), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.7.sp)
                Text(t("Todo lo vuestro,\nen un mismo lugar."), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(t("Herramientas, ayuda y recuerdos para acompañaros cada día."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primary) {
            Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(t("¿Necesitáis ayuda profesional?"), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(t("Encontrad a la persona adecuada."), color = Color.White.copy(alpha = .86f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onProfessionals, contentPadding = PaddingValues(0.dp)) { Text(t("Explorar profesionales  →"), color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                    Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
                }
            }
        }
        Text(t("CUIDADO Y COMUNIDAD"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        MoreDestinationCard(Icons.Filled.WorkspacePremium, t("Servicios profesionales"), t("Adiestradores, etólogos, veterinarios, paseadores y cuidadores."), onProfessionals)
        MoreDestinationCard(Icons.Filled.Help, t("He perdido a mi mascota"), t("Ayuda a difundir avisos y comunica una pérdida."), onLostPet, accent = true)
        Text(t("VUESTROS RECUERDOS Y RECURSOS"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        MoreDestinationCard(Icons.Filled.PictureAsPdf, "Ficha completa y PDF", "Genera y comparte un informe con el perfil, el pasaporte sanitario y las rutinas de cuidado.", onExportPetPdf)
        MoreDestinationCard(Icons.Filled.AutoStories, t("Diario de recuerdos"), t("Guarda momentos, pequeñas historias y fotografías."), onDiary)
        MoreDestinationCard(Icons.Filled.MenuBook, t("Aprender"), t("Artículos, consejos y recursos para su bienestar."), onLearn)
        MoreDestinationCard(Icons.Filled.Settings, t("Ajustes"), t("Diseño, colores, modo oscuro, idioma y servicios multimedia."), onSettings)
        Text("La Comunidad y Salud siguen accesibles desde la navegación inferior para evitar accesos duplicados.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp))
    }
}

@Composable
private fun MoreDestinationCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, onClick: () -> Unit, accent: Boolean = false) {
    Surface(onClick = onClick, shape = RoundedCornerShape(19.dp), color = if (accent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, if (accent) MaterialTheme.colorScheme.error.copy(alpha = .3f) else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = if (accent) MaterialTheme.colorScheme.error.copy(alpha = .12f) else MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, contentDescription = null, tint = if (accent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(11.dp).size(23.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.Bold, color = if (accent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
                Text(description, color = if (accent) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = .8f) else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Filled.Pets, contentDescription = null, tint = if (accent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}
