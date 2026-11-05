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
private fun HomeScreen(
    dogName: String,
    walkDone: Boolean,
    medicineDone: Boolean,
    onWalkToggle: (Boolean) -> Unit,
    onMedicineToggle: (Boolean) -> Unit,
    onExplore: () -> Unit,
    onAbout: () -> Unit,
    onEditProfile: () -> Unit,
    diaryCount: Int,
    dogPhoto: Bitmap?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PAWLY", color = Forest, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                    Text("Tu vida, a su lado.", style = MaterialTheme.typography.headlineLarge, color = Ink)
                }
                Surface(
                    onClick = onAbout,
                    shape = CircleShape,
                    color = Paper,
                    shadowElevation = 1.dp
                ) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, contentDescription = "Acerca de Pawly", tint = Forest)
                    }
                }
            }
        }
        item {
            DogHeroCard(dogName, dogPhoto, onEditProfile)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Un día bonito empieza aquí", style = MaterialTheme.typography.titleLarge)
                    Text("Pequeños planes, grandes recuerdos.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
                Text("HOY", color = ForestLight, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(listOf(walkDone, medicineDone).count { !it }.toString(), "tareas pendientes", Icons.Filled.Explore, Modifier.weight(1f))
                MetricCard(diaryCount.toString(), "recuerdos guardados", Icons.Filled.PhotoLibrary, Modifier.weight(1f))
            }
        }
        item {
            SectionHeading("La rutina de hoy", "Un poquito cada día")
        }
        item {
            TaskRow(
                icon = Icons.Filled.DirectionsWalk,
                title = "Paseo y olfateo",
                subtitle = "Un rato para explorar sin prisa",
                checked = walkDone,
                onChecked = onWalkToggle,
                tint = Color(0xFFE9F0DB)
            )
        }
        item {
            TaskRow(
                icon = Icons.Filled.Medication,
                title = "Revisar cuidados",
                subtitle = "Recordatorio de ejemplo · datos demo",
                checked = medicineDone,
                onChecked = onMedicineToggle,
                tint = Color(0xFFF8E9DF)
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("¿Salimos a descubrir?", style = MaterialTheme.typography.titleLarge)
                    Text("Encuentra ideas para vuestra próxima aventura.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onExplore) {
                    Icon(Icons.Filled.ArrowForward, contentDescription = "Explorar", tint = Forest)
                }
            }
            Button(
                onClick = onExplore,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Forest)
            ) {
                Icon(Icons.Filled.Explore, contentDescription = null)
                Spacer(Modifier.width(9.dp))
                Text("Explorar lugares", fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Text("Pawly · Hecho para compartir más vida juntos.", color = Muted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun DogHeroCard(name: String, photo: Bitmap?, onEdit: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onEdit)
            .background(Brush.linearGradient(listOf(Forest, Color(0xFF285D4D), Color(0xFF497A62))))
            .padding(21.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("VUESTRO COMPAÑERO", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(name, color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                    Text("Toca para editar su perfil", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
                }
                Box(
                    Modifier.size(82.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (photo != null) Image(photo.asImageBitmap(), contentDescription = "Foto de $name", modifier = Modifier.fillMaxSize().clip(CircleShape))
                    else Text("🐕", fontSize = 47.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroPill("🐾  Aventurera")
                HeroPill("♡  Muy querida")
            }
        }
    }
}

@Composable
private fun HeroPill(text: String) {
    Surface(color = Color.White.copy(alpha = 0.13f), shape = RoundedCornerShape(50)) {
        Text(text, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
    }
}

@Composable
private fun MetricCard(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = Paper, shadowElevation = 1.dp) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = ForestLight, modifier = Modifier.size(21.dp))
            Spacer(Modifier.height(12.dp))
            Text(value, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(label, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}

@Composable
private fun TaskRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    tint: Color
) {
    Surface(shape = RoundedCornerShape(18.dp), color = Paper, shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(tint), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Forest)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = if (checked) Muted else Ink)
                Text(subtitle, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Checkbox(checked = checked, onCheckedChange = onChecked, colors = CheckboxDefaults.colors(checkedColor = Forest))
        }
    }
}
