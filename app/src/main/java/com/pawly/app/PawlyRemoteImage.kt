package com.pawly.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Remote image renderer with a visible failure state. This makes an expired or
 * invalid Cloudinary URL distinguishable from a post/message with no attachment.
 */
@Composable
internal fun PawlyRemoteImage(url: String, description: String, modifier: Modifier = Modifier) {
    var failed by remember(url) { mutableStateOf(false) }
    if (url.isBlank()) return
    if (failed) {
        Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.Icon(Icons.Default.BrokenImage, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No se ha podido cargar esta fotografía", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } else {
        AsyncImage(
            model = url.trim(),
            contentDescription = description,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            onError = { failed = true }
        )
    }
}
