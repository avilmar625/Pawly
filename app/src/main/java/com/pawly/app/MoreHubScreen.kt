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
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    onSharePetPdf: () -> Unit,
    onSavePetPdf: () -> Unit,
    onInviteFriend: () -> Unit,
    onContact: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    onSignOut: () -> Unit,
    showSignOut: Boolean = true,
    language: String = "es",
    elegantDesign: Boolean = false
) {
    fun t(value: String) = PawlyI18n.text(value, language)
    var confirmSignOut by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        if (elegantDesign) {
            Box(Modifier.fillMaxWidth().height(195.dp).clip(RoundedCornerShape(27.dp))) {
                coil3.compose.AsyncImage(model = "https://images.unsplash.com/photo-1543466835-00a7907e9de1?w=1200&h=700&fit=crop", contentDescription = t("Perro disfrutando al aire libre"), modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE610251F)))))
                Column(Modifier.align(Alignment.BottomStart).padding(19.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(t("TU UNIVERSO PAWLY"), color = Color.White.copy(alpha = .82f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
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
        MoreSectionLabel(t("CUIDADO Y COMUNIDAD"))
        MoreDestinationCard(Icons.Filled.WorkspacePremium, t("Servicios profesionales"), t("Adiestradores, etólogos, veterinarios, paseadores y cuidadores."), onProfessionals, Color(0xFF247A67), Color(0xFFE2F3EC))
        MoreDestinationCard(Icons.Filled.Help, t("He perdido a mi mascota"), t("Ayuda a encontrarla y difunde un aviso."), onLostPet, Color(0xFFB34F42), Color(0xFFFFE9E4))
        SectionLabel(t("VUESTROS RECUERDOS Y RECURSOS"))
        PdfActionCard(t("Ficha completa y PDF"), t("Incluye el perfil, el pasaporte sanitario y las rutinas de cuidado."), t("Guardar en el móvil"), t("Compartir PDF"), onSavePetPdf, onSharePetPdf)
        MoreDestinationCard(Icons.Filled.AutoStories, t("Diario de recuerdos"), t("Guarda momentos, pequeñas historias y fotografías."), onDiary, Color(0xFF8C5AA7), Color(0xFFF2E9F8))
        MoreDestinationCard(Icons.Filled.MenuBook, t("Aprender"), t("Artículos, consejos y recursos para su bienestar."), onLearn, Color(0xFF3674A8), Color(0xFFE6F1FB))
        SectionLabel(t("AYUDA Y CUENTA"))
        MoreDestinationCard(Icons.Filled.PersonAddAlt1, t("Invita a un amigo"), t("Comparte Pawly con otras personas amantes de los animales."), onInviteFriend, Color(0xFFB16C27), Color(0xFFFFF0DB))
        MoreDestinationCard(Icons.Filled.MailOutline, t("Contacta con nosotros"), t("Envíanos una consulta, incidencia o sugerencia."), onContact, Color(0xFF397A83), Color(0xFFE3F3F4))
        MoreDestinationCard(Icons.Filled.Settings, t("Ajustes"), t("Diseño, colores, idioma y servicios multimedia."), onSettings, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
        SectionLabel(t("INFORMACIÓN LEGAL"))
        MoreDestinationCard(Icons.Filled.Policy, t("Política de privacidad"), t("Consulta el borrador de privacidad de Pawly."), onPrivacy, Color(0xFF63758A), Color(0xFFEAF0F6))
        MoreDestinationCard(Icons.Filled.Gavel, t("Términos y condiciones"), t("Consulta las condiciones de uso en preparación."), onTerms, Color(0xFF63758A), Color(0xFFEAF0F6))
        if (showSignOut) MoreDestinationCard(Icons.Filled.Logout, t("Cerrar sesión"), t("Salir de tu cuenta de Pawly en este dispositivo."), { confirmSignOut = true }, Color(0xFFB34F42), Color(0xFFFFE9E4))
        Text(t("La Comunidad y Salud siguen accesibles desde la navegación inferior para evitar accesos duplicados."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp))
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(t("¿Cerrar sesión?")) },
            text = { Text(t("Tendrás que volver a identificarte para acceder a tu cuenta.")) },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text(t("Cerrar sesión"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(t("Cancelar")) } }
        )
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
}

@Composable
private fun PdfActionCard(title: String, description: String, saveLabel: String, shareLabel: String, onSave: () -> Unit, onShare: () -> Unit) {
    Surface(shape = RoundedCornerShape(19.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFFE8E6)) {
                    Icon(Icons.Filled.PictureAsPdf, null, tint = Color(0xFFB6423B), modifier = Modifier.padding(11.dp).size(24.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) { Text(saveLabel) }
                Button(onClick = onShare, modifier = Modifier.weight(1f)) { Text(shareLabel) }
            }
        }
    }
}

@Composable
private fun MoreDestinationCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, onClick: () -> Unit, tint: Color, container: Color) {
    Surface(onClick = onClick, shape = RoundedCornerShape(19.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = container) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(11.dp).size(23.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Filled.Pets, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}
