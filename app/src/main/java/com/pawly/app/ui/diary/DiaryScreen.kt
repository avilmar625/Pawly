package com.pawly.app

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
private fun DiaryScreen(
    dogName: String,
    entries: List<String>,
    onEntriesChange: (List<String>) -> Unit
) {
    var note by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        item {
            Text("VUESTROS RECUERDOS", color = Forest, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.5.sp)
            Text("Una vida llena\nde historias.", style = MaterialTheme.typography.headlineLarge)
            Text("Guarda esos momentos que merecen quedarse.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = Forest) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("UN RECUERDO NUEVO", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("¿Qué habéis hecho hoy?", color = Color.White.copy(alpha = .6f)) },
                        shape = RoundedCornerShape(15.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Lime,
                            unfocusedBorderColor = Color.White.copy(alpha = .3f),
                            cursorColor = Lime
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (note.isNotBlank()) {
                                onEntriesChange(listOf(note.trim()) + entries)
                                note = ""
                            }
                        },
                        enabled = note.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Forest),
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text("Guardar recuerdo")
                    }
                }
            }
        }
        item { SectionHeading("Pequeñas grandes historias", "Diario de $dogName · ${entries.size} recuerdos guardados") }
        items(entries) { entry ->
            Surface(shape = RoundedCornerShape(20.dp), color = Paper, shadowElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(49.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFE9F0E5)), contentAlignment = Alignment.Center) {
                        Text("🐾", fontSize = 24.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry, fontWeight = FontWeight.SemiBold)
                        Text("Guardado en este dispositivo", color = Muted, fontSize = 11.sp)
                    }
                    Icon(Icons.Filled.FavoriteBorder, contentDescription = null, tint = Peach)
                }
            }
        }
        item {
            Text("Tus recuerdos se guardan localmente en este dispositivo. No se sincronizan con otros teléfonos ni se incluyen fotografías.", color = Muted, fontSize = 11.sp)
        }
    }
}
