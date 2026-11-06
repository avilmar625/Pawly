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
    onDarkModeChange: (Boolean) -> Unit,
    onAccentChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Personaliza Pawly", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Apariencia", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Modo oscuro")
                            Text("Reduce el brillo de la interfaz", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Color de Pawly", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("forest" to Color(0xFF173F35), "ocean" to Color(0xFF176B87), "berry" to Color(0xFF805080), "sunset" to Color(0xFFB65D3C)).forEach { (key, color) ->
                            Box(Modifier.size(42.dp).background(color, CircleShape).clickable { onAccentChange(key) }, contentAlignment = Alignment.Center) {
                                if (accent == key) Icon(Icons.Default.Check, contentDescription = "Seleccionado", tint = Color.White)
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Idioma preferido", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("es" to "Español", "en" to "English", "ca" to "Català").forEach { (key, label) ->
                            FilterChip(selected = language == key, onClick = { onLanguageChange(key) }, label = { Text(label) })
                        }
                    }
                    Text("La preferencia queda guardada. La traducción integral de las pantallas actuales se incorporará progresivamente; por ahora la interfaz principal sigue en español.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Tus ajustes se guardan en este dispositivo.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
        shape = RoundedCornerShape(24.dp)
    )
}
