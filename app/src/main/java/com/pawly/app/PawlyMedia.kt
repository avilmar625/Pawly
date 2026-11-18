package com.pawly.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

internal const val PAWLY_DEFAULT_DOG_PHOTO_URL =
    "https://images.unsplash.com/photo-1517849845537-4d257902454a?w=900&h=900&fit=crop&crop=faces"

@Composable
internal fun rememberPawlyCameraCapture(
    onImageSelected: (Uri) -> Unit,
    onError: (String) -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val photoUri = runCatching {
                val photoDirectory = File(context.cacheDir, "profile_photos").apply { mkdirs() }
                val photoFile = File(photoDirectory, "capture-${System.currentTimeMillis()}.jpg")
                photoFile.outputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                        "No se pudo preparar la fotografía."
                    }
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            }.getOrElse {
                onError(it.localizedMessage ?: "No se pudo guardar la fotografía de la cámara.")
                null
            }
            photoUri?.let(onImageSelected)
        }
    }
    return remember(launcher) { { launcher.launch(null) } }
}

@Composable
internal fun PawlyPhotoSourceButton(
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(tr(label))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(tr("Galería")) },
                onClick = { expanded = false; onGallery() },
                leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(tr("Cámara")) },
                onClick = { expanded = false; onCamera() },
                leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) }
            )
        }
    }
}
