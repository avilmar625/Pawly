package com.pawly.app

import com.pawly.app.tr

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
import android.location.Location
import android.location.LocationListener
import android.os.Build
import android.os.Looper
import android.location.Geocoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.core.content.ContextCompat
import org.json.JSONArray

@Composable
internal fun ProfileDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    hasPhoto: Boolean,
    syncStatus: String = "",
    profiles: Set<String>,
    onSelectProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onDeleteProfile: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Perfil de tu compañero"), color = Forest) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text(tr("Nombre")) }, singleLine = true, shape = RoundedCornerShape(14.dp))
                Text(if (hasPhoto) "Foto de perfil disponible" else "Añade una foto para reconocer a tu compañero", color = Muted, style = MaterialTheme.typography.bodySmall)
                if (syncStatus.isNotBlank()) {
                    Text(syncStatus, color = ForestLight, style = MaterialTheme.typography.labelSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onGallery) { Icon(Icons.Filled.PhotoLibrary, contentDescription = null); Spacer(Modifier.width(5.dp)); Text(tr("Galería")) }
                    OutlinedButton(onClick = onCamera) { Icon(Icons.Filled.PhotoCamera, contentDescription = null); Spacer(Modifier.width(5.dp)); Text(tr("Cámara")) }
                }
                Text(tr("Perfiles de perros"), color = Forest, fontWeight = FontWeight.SemiBold)
                profiles.sorted().forEach { profile ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onSelectProfile(profile) }, modifier = Modifier.weight(1f)) { Text(profile) }
                        if (profiles.size > 1) IconButton(onClick = { onDeleteProfile(profile) }) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Eliminar perfil $profile") }
                    }
                }
                OutlinedButton(onClick = onAddProfile) { Icon(Icons.Filled.Add, contentDescription = null); Text(tr("Añadir con el nombre escrito")) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = name.isNotBlank()) { Text(tr("Guardar"), color = Forest) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Cancelar"), color = Muted) }
        },
        containerColor = Paper
    )
}

@Composable
internal fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        icon = { Text(tr("🐾"), fontSize = 34.sp) },
        title = { Text(tr("Pawly"), color = Forest, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Más aventuras. Más recuerdos. Más vida juntos."), color = Ink)
                Spacer(Modifier.height(5.dp))
                Text(tr("Creadores"), color = ForestLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(tr("Alex Vilarrubla Martín"), fontWeight = FontWeight.SemiBold)
                Text(tr("Maialen Cruz Tudanca"), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text(tr("Versión inicial 1.0.0 · Proyecto en desarrollo"), color = Muted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(tr("Cerrar"), color = Forest) }
        }
    )
}
