package com.pawly.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
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
    onCalendar: () -> Unit,
    onSettings: () -> Unit,
    onProfessionals: () -> Unit,
    onPawlyMarket: () -> Unit,
    onLostPet: () -> Unit,
    onSharePetPdf: () -> Unit,
    onSavePetPdf: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
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
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD910251F)))))
                Column(Modifier.align(Alignment.BottomStart).padding(19.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(t("TU UNIVERSO PAWLY"), color = Color.White.copy(alpha = .82f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
                    Text(t("Todo lo vuestro,\nen un mismo lugar."), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                }
            }
        } else {
            PawlyPageHeader(
                eyebrow = "Tu espacio Pawly",
                title = "Todo lo vuestro, en un mismo lugar.",
                subtitle = "Ayuda profesional, recuerdos, aprendizaje y recursos prácticos para vuestra mascota."
            )
        }
        MoreSectionLabel(t("AYUDA URGENTE"))
        MoreDestinationCard(Icons.Filled.Campaign, t("He perdido a mi mascota"), t("Acceso rápido para preparar y difundir un aviso de pérdida."), onLostPet)
        MoreSectionLabel(t("CUIDADO Y COMUNIDAD"))
        MoreDestinationCard(Icons.Filled.ShoppingBag, t("Pawly Market y cuidados"), t("Productos por categorías, alimentación, higiene, gastos y ficha de emergencia."), onPawlyMarket)
        MoreDestinationCard(Icons.Filled.WorkspacePremium, t("Servicios profesionales"), t("Adiestradores, etólogos, veterinarios, paseadores y cuidadores."), onProfessionals)
        MoreSectionLabel(t("VUESTROS RECUERDOS Y RECURSOS"))
        PdfActionCard(t("Ficha completa y PDF"), t("Incluye el perfil, el pasaporte sanitario y las rutinas de cuidado."), t("Guardar en el móvil"), t("Compartir PDF"), onSavePetPdf, onSharePetPdf)
        BackupActionCard(
            title = t("Copia de seguridad y cambio de móvil"),
            description = t("Exporta salud, rutinas, diario, agenda y foto de perfil a un archivo JSON que puedes guardar y restaurar cuando quieras."),
            exportLabel = t("Crear copia"),
            importLabel = t("Restaurar copia"),
            onExport = onExportBackup,
            onImport = onImportBackup
        )
        MoreDestinationCard(Icons.Filled.AutoStories, t("Diario de recuerdos"), t("Guarda momentos, pequeñas historias y fotografías."), onDiary)
        MoreDestinationCard(Icons.Filled.CalendarMonth, t("Calendario y agenda"), t("Eventos de la Comunidad y recordatorios para no olvidar nada."), onCalendar)
        MoreDestinationCard(Icons.Filled.MenuBook, t("Aprender"), t("Artículos, consejos y recursos para su bienestar."), onLearn)
        MoreSectionLabel(t("AYUDA Y CUENTA"))
        MoreDestinationCard(Icons.Filled.PersonAddAlt1, t("Invita a un amigo"), t("Comparte Pawly con otras personas amantes de los animales."), onInviteFriend)
        MoreDestinationCard(Icons.Filled.MailOutline, t("Contacta con nosotros"), t("Envíanos una consulta, incidencia o sugerencia."), onContact)
        MoreDestinationCard(Icons.Filled.Settings, t("Ajustes"), t("Diseño, colores, idioma y servicios multimedia."), onSettings, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
        MoreSectionLabel(t("INFORMACIÓN LEGAL"))
        MoreDestinationCard(Icons.Filled.Policy, t("Política de privacidad"), t("Consulta el borrador de privacidad de Pawly."), onPrivacy)
        MoreDestinationCard(Icons.Filled.Gavel, t("Términos y condiciones"), t("Consulta las condiciones de uso en preparación."), onTerms)
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
private fun MoreSectionLabel(label: String) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
}

@Composable
private fun PdfActionCard(title: String, description: String, saveLabel: String, shareLabel: String, onSave: () -> Unit, onShare: () -> Unit) {
    Surface(shape = RoundedCornerShape(19.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Icon(Icons.Filled.PictureAsPdf, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(11.dp).size(24.dp))
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
private fun BackupActionCard(
    title: String,
    description: String,
    exportLabel: String,
    importLabel: String,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Surface(shape = RoundedCornerShape(19.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Filled.FileDownload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(11.dp).size(24.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(exportLabel)
                }
                Button(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.FileUpload, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(importLabel)
                }
            }
        }
    }
}

@Composable
private fun MoreDestinationCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(11.dp).size(23.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}
