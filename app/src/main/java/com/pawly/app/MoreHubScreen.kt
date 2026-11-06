package com.pawly.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MoreHubScreen(
    onLearn: () -> Unit,
    onDiary: () -> Unit,
    onHealth: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("TU ESPACIO PAWLY", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.7.sp)
            Text("Todo lo vuestro,\nen un mismo lugar.", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
            Text("Herramientas y recuerdos para acompañaros cada día.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))) {
                Row(Modifier.fillMaxWidth().padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Vuestra historia merece quedarse", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text("Guarda los momentos pequeños. Son los que más cuentan.", color = Color.White.copy(alpha = .84f), style = MaterialTheme.typography.bodySmall)
                    }
                    Box(Modifier.padding(start = 8.dp).size(56.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.AutoStories, contentDescription = null, tint = Color.White, modifier = Modifier.size(29.dp))
                    }
                }
            }
        }
        Text("VUESTROS ESPACIOS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        MoreDestinationCard(
            icon = Icons.Filled.FavoriteBorder,
            title = "Salud y cuidados",
            description = "Agenda, pasaporte veterinario, rutinas y tratamientos.",
            tint = Color(0xFFE5EFE5),
            onClick = onHealth
        )
        MoreDestinationCard(
            icon = Icons.Filled.AutoStories,
            title = "Diario de recuerdos",
            description = "Guarda momentos, pequeñas historias y fotografías.",
            tint = Color(0xFFF5E7DD),
            onClick = onDiary
        )
        MoreDestinationCard(
            icon = Icons.Filled.MenuBook,
            title = "Aprender",
            description = "Artículos, consejos y recursos para su bienestar.",
            tint = Color(0xFFE9E8F3),
            onClick = onLearn
        )
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(19.dp)) {
            Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Pawly crece con vosotros. Iremos incorporando nuevas herramientas para que cuidar, recordar y descubrir sea cada vez más fácil.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MoreDestinationCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(Modifier.size(49.dp).clip(RoundedCornerShape(15.dp)).background(tint), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color(0xFF315A45), modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Filled.ArrowForwardIos, contentDescription = "Abrir $title", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
        }
    }
}
