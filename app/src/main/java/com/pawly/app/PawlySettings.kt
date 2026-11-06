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
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Ajustes", fontWeight = FontWeight.ExtraBold)
                Text("Haz Pawly tuya", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(19.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Estilo de diseño", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Elige cómo quieres vivir Pawly. Puedes cambiarlo cuando quieras.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        DesignStyleCard(
                            modifier = Modifier.weight(1f),
                            selected = designStyle == "simple",
                            title = "Simple",
                            subtitle = "Limpio y directo",
                            icon = Icons.Filled.Tune,
                            onClick = { onDesignStyleChange("simple") }
                        )
                        DesignStyleCard(
                            modifier = Modifier.weight(1f),
                            selected = designStyle == "elegant",
                            title = "Elegante",
                            subtitle = "Fotos y color",
                            icon = Icons.Filled.AutoAwesome,
                            onClick = { onDesignStyleChange("elegant") }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Apariencia", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Modo oscuro")
                            Text("Reduce el brillo de la interfaz", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Color de Pawly", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("forest" to Color(0xFF173F35), "ocean" to Color(0xFF176B87), "berry" to Color(0xFF805080), "sunset" to Color(0xFFB65D3C)).forEach { (key, color) ->
                            Box(Modifier.size(42.dp).background(color, CircleShape).clickable { onAccentChange(key) }, contentAlignment = Alignment.Center) {
                                if (accent == key) Icon(Icons.Default.Check, contentDescription = "Seleccionado", tint = Color.White)
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Idioma preferido", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("es" to "Español", "en" to "English", "ca" to "Català").forEach { (key, label) ->
                            FilterChip(selected = language == key, onClick = { onLanguageChange(key) }, label = { Text(label) })
                        }
                    }
                    Text("La traducción integral de todas las pantallas se incorporará progresivamente; por ahora la interfaz principal sigue en español.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Tus ajustes se guardan en este dispositivo.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
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
