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

internal fun HealthScreen(
    medicineDone: Boolean,
    onMedicineToggle: (Boolean) -> Unit,
    dates: Map<String, String>,
    onDateChange: (String, String?) -> Unit
) {
    val context = LocalContext.current
    fun selectDate(key: String) {
        val today = Calendar.getInstance()
        DatePickerDialog(context, { _, year, month, day ->
            onDateChange(key, "%04d-%02d-%02d".format(year, month + 1, day))
        }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show()
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        item {
            Text("BIENESTAR", color = Forest, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.5.sp)
            Text("Cuidar también\nes querer.", style = MaterialTheme.typography.headlineLarge)
            Text("Organiza su día a día con tranquilidad.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Surface(shape = RoundedCornerShape(23.dp), color = Color(0xFFE8EFE2)) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = Forest, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(13.dp))
                    Column {
                        Text("Su salud, siempre a mano", fontWeight = FontWeight.Bold, color = Forest)
                        Text("Una agenda sencilla para no olvidar nada.", color = ForestLight, fontSize = 11.sp)
                    }
                }
            }
        }
        item { SectionHeading("Próximos cuidados", "Ejemplos de recordatorios · no son pautas veterinarias") }
        item {
            HealthItem(Icons.Filled.Vaccines, "Vacunación", "Fecha orientativa de tu próxima cita", dates["Vacunación"] ?: "Añadir fecha", { selectDate("Vacunación") }, { onDateChange("Vacunación", null) })
        }
        item {
            HealthItem(Icons.Filled.BugReport, "Desparasitación", "Sigue la pauta indicada por tu veterinario", dates["Desparasitación"] ?: "Añadir fecha", { selectDate("Desparasitación") }, { onDateChange("Desparasitación", null) })
        }
        item {
            HealthItem(Icons.Filled.MedicalServices, "Próxima revisión", "Organiza una cita con vuestro veterinario", dates["Próxima revisión"] ?: "Añadir fecha", { selectDate("Próxima revisión") }, { onDateChange("Próxima revisión", null) })
        }
        item {
            TaskRow(
                icon = Icons.Filled.Medication,
                title = "Recordatorio diario",
                subtitle = "Ejemplo interactivo · márcalo al completarlo",
                checked = medicineDone,
                onChecked = onMedicineToggle,
                tint = Peach
            )
        }
        item {
            Text("Importante: Pawly no sustituye al veterinario. Consulta siempre con un profesional para vacunas, medicación, dosis y tratamientos.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun HealthItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, date: String, onClick: () -> Unit, onClear: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Paper, shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE9F0E5)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Forest)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(date, color = ForestLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                if (date != "Añadir fecha") TextButton(onClick = onClear, contentPadding = PaddingValues(0.dp)) { Text("Quitar", fontSize = 10.sp) }
            }
        }
    }
}

@Composable
