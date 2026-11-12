package com.pawly.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun PawlySettingsDialog(
    darkMode: Boolean,
    accent: String,
    language: String,
    designStyle: String,
    onDesignStyleChange: (String) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onAccentChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    cloudinaryCloudName: String,
    cloudinaryImagePreset: String,
    cloudinaryVideoPreset: String,
    onSaveCloudinary: (String, String, String) -> Unit,
    onDismiss: () -> Unit
 ) {
    fun t(value: String) = PawlyI18n.text(value, language)
    var cloudName by remember(cloudinaryCloudName) { mutableStateOf(cloudinaryCloudName) }
    var imagePreset by remember(cloudinaryImagePreset) { mutableStateOf(cloudinaryImagePreset) }
    var videoPreset by remember(cloudinaryVideoPreset) { mutableStateOf(cloudinaryVideoPreset) }
    var cloudinaryFeedback by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(t("Ajustes"), fontWeight = FontWeight.ExtraBold)
                Text(t("Haz Pawly tuya"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(19.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(t("Estilo de diseño"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(t("Elige cómo quieres vivir Pawly. Puedes cambiarlo cuando quieras."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(t(if (designStyle == "elegant") "Vista elegante activada" else "Vista simple activada"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(t(if (designStyle == "elegant") "Fotografías, portadas y tarjetas con más personalidad." else "Interfaz ligera, directa y con menos elementos visuales."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        DesignStyleCard(
                            modifier = Modifier.weight(1f),
                            selected = designStyle == "simple",
                            title = t("Simple"),
                            subtitle = t("Limpio y directo"),
                            icon = Icons.Filled.Tune,
                            onClick = { onDesignStyleChange("simple") }
                        )
                        DesignStyleCard(
                            modifier = Modifier.weight(1f),
                            selected = designStyle == "elegant",
                            title = t("Elegante"),
                            subtitle = t("Fotos y color"),
                            icon = Icons.Filled.AutoAwesome,
                            onClick = { onDesignStyleChange("elegant") }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t("Apariencia"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(t("Modo oscuro"))
                            Text(t("Reduce el brillo de la interfaz"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("Color de Pawly"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("forest" to Color(0xFF173F35), "ocean" to Color(0xFF176B87), "berry" to Color(0xFF805080), "sunset" to Color(0xFFB65D3C)).forEach { (key, color) ->
                            Box(Modifier.size(42.dp).background(color, CircleShape).clickable { onAccentChange(key) }, contentAlignment = Alignment.Center) {
                                if (accent == key) Icon(Icons.Default.Check, contentDescription = "Seleccionado", tint = Color.White)
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("Idioma preferido"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("es" to t("Español"), "ca" to t("Català"), "fr" to t("Français"), "en" to t("English")).forEach { (key, label) ->
                            FilterChip(selected = language == key, onClick = { onLanguageChange(key) }, label = { Text(label) })
                        }
                    }
                    Text(t("El idioma se aplica a la navegación y a las secciones compatibles. Algunas pantallas antiguas aún pueden conservar textos en español."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("Servicios multimedia · Cloudinary"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(t("Configura aquí las subidas de fotos y vídeos sin tener que recompilar la aplicación. Estos datos son nombres de configuración, no incluyas el API Secret."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = cloudName,
                        onValueChange = { cloudName = it.trim(); cloudinaryFeedback = null },
                        label = { Text("Cloud name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = imagePreset,
                        onValueChange = { imagePreset = it.trim(); cloudinaryFeedback = null },
                        label = { Text(t("Preset unsigned de imágenes")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = videoPreset,
                        onValueChange = { videoPreset = it.trim(); cloudinaryFeedback = null },
                        label = { Text(t("Preset unsigned de vídeos")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (!cloudName.matches(Regex("[A-Za-z0-9_-]+")) || imagePreset.isBlank() || videoPreset.isBlank()) {
                                cloudinaryFeedback = t("Introduce un Cloud name válido y los dos presets.")
                            } else {
                                onSaveCloudinary(cloudName, imagePreset, videoPreset)
                                cloudinaryFeedback = t("Configuración guardada. Ya puedes volver a intentar la subida.")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(t("Guardar configuración multimedia")) }
                    if (cloudinaryFeedback != null) {
                        Text(cloudinaryFeedback!!, color = if (cloudinaryFeedback!!.startsWith("Configuración")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(t("Crea los presets como Unsigned en Cloudinary. No pegues aquí una API Key ni un API Secret."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(t("Tus ajustes se guardan en este dispositivo."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Listo")) } },
        shape = RoundedCornerShape(26.dp)
    )
}

@Composable
private fun DesignStyleCard(
    modifier: Modifier,
    selected: Boolean,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (selected) Icon(Icons.Default.Check, contentDescription = "Seleccionado", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
            }
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
