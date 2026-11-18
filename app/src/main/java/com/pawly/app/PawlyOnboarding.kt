package com.pawly.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
internal fun PawlyOnboardingDialog(
    petName: String,
    onPetNameChange: (String) -> Unit,
    hasPhoto: Boolean,
    onPickPhoto: () -> Unit,
    onPickCamera: () -> Unit,
    selectedPhoto: Bitmap? = null,
    onEnableNotifications: () -> Unit,
    notificationsEnabled: Boolean = false,
    notificationStatus: String = "",
    onTestNotifications: () -> Unit = {},
    onFinish: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onFinish,
        shape = RoundedCornerShape(26.dp),
        title = {
            Text(
                when (step) {
                    0 -> "¡Bienvenidos a Pawly!"
                    1 -> "Una foto para recordar"
                    else -> "Recordatorios a tiempo"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { index ->
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { if (index <= step) 1f else 0f },
                            modifier = Modifier.weight(1f).height(4.dp),
                            trackColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
                when (step) {
                    0 -> {
                        Icon(Icons.Default.Pets, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                        Text("Crea el espacio de tu compañero para organizar sus cuidados, salud, recuerdos y aventuras.")
                        OutlinedTextField(
                            value = petName,
                            onValueChange = onPetNameChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("¿Cómo se llama tu mascota?") },
                            singleLine = true
                        )
                        Text("Su perfil empieza siendo privado. Tú decides qué compartir con la comunidad.")
                    }
                    1 -> {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                        Text("Añade una foto para reconocer rápidamente su perfil. Puedes cambiarla cuando quieras.")
                        PawlyPhotoSourceButton(
                            onGallery = onPickPhoto,
                            onCamera = onPickCamera,
                            label = if (hasPhoto) "Cambiar foto" else "Elegir foto",
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (selectedPhoto != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Image(
                                    bitmap = selectedPhoto.asImageBitmap(),
                                    contentDescription = "Vista previa de la foto de tu mascota",
                                    modifier = Modifier.size(64.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Text("Vista previa · podrás cambiarla más adelante", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            }
                        } else if (hasPhoto) Text("¡Foto añadida!")
                    }
                    else -> {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                        Text("Activa las notificaciones para recibir recordatorios de cuidados y avisos importantes. Puedes decidirlo ahora o más adelante en los ajustes del móvil.")
                        OutlinedButton(
                            onClick = if (notificationsEnabled) onTestNotifications else onEnableNotifications,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (notificationsEnabled) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Enviar aviso de prueba")
                            } else {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (notificationStatus.isNotBlank()) "Revisar ajustes" else "Activar notificaciones")
                            }
                        }
                        Text(
                            when {
                                notificationsEnabled -> "¡Perfecto! Los avisos del sistema están permitidos. Pulsa «Enviar aviso de prueba» para comprobarlo; los recordatorios llegarán cuando programes cuidados o citas."
                                notificationStatus.isNotBlank() -> notificationStatus
                                else -> "Al pulsar, Android te pedirá permiso. Si ya lo rechazaste o bloqueaste, Pawly te llevará a sus ajustes para activarlo."
                            },
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = if (notificationsEnabled) androidx.compose.material3.MaterialTheme.colorScheme.primary
                                else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (step < 2) step++ else onFinish()
            }, enabled = step != 0 || petName.trim().isNotEmpty()) {
                Text(if (step == 2) "Empezar a usar Pawly" else "Continuar")
            }
        },
        dismissButton = {
            TextButton(onClick = onFinish) { Text("Ahora no") }
        }
    )
}
