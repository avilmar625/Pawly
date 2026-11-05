package com.pawly.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun Avatar(url: String, label: String, size: Int = 44) {
    if (url.isNotBlank()) AsyncImage(url, "Foto de " + label, Modifier.size(size.dp).clip(CircleShape))
    else Box(Modifier.size(size.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
        Text(label.trim().firstOrNull()?.uppercase() ?: "🐾", color = Forest, fontWeight = FontWeight.Bold, fontSize = (size / 2).sp)
    }
}


@Composable
internal fun EmptyCommunityCard(title: String, subtitle: String) {
    Surface(color = Paper, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🐾", fontSize = 32.sp)
            Text(title, color = Forest, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
