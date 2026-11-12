package com.pawly.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
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
    onSettings: () -> Unit,
    onProfessionals: () -> Unit,
    onCommunity: () -> Unit,
    elegantDesign: Boolean = false
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        if (elegantDesign) {
            Box(Modifier.fillMaxWidth().height(195.dp).clip(RoundedCornerShape(27.dp))) {
                coil3.compose.AsyncImage(
                    model = "https://images.unsplash.com/photo-1543466835-00a7907e9de1?w=1200&h=700&fit=crop",
                    contentDescription = "Perro disfrutando al aire libre",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE610251F)))))
                Column(Modifier.align(Alignment.BottomStart).padding(19.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("TU UNIVERSO PAWLY", color = Color.White.copy(alpha = .82f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
                    Text("Todo lo que compartís, en un mismo lugar.", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("TU ESPACIO PAWLY", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.7.sp)
                Text("Todo lo vuestro,\nen un mismo lugar.", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("Herramientas, ayuda y recuerdos para acompañaros cada día.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primary) {
            Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("¿Necesitáis ayuda profesional?", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text("Encontrad educadores, veterinarios y cuidadores en vuestra zona.", color = Color.White.copy(alpha = .86f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onProfessionals, contentPadding = PaddingValues(0.dp)) {
                            Text("Explorar profesionales  →", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
                }
            }
        }

        Text("CUIDADO Y COMUNIDAD", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        MoreDestinationCard(
            icon = Icons.Filled.WorkspacePremium,
            title = "Servicios profesionales",
            description = "Adiestradores, etólogos, veterinarios, paseadores y cuidadores.",
            onClick = onProfessionals
        )
        MoreDestinationCard(
            icon = Icons.Filled.Pets,
            title = "Mascotas perdidas y encontradas",
            description = "Accede a los avisos de la comunidad y ayuda a difundirlos.",
            onClick = onCommunity
        )
        MoreDestinationCard(
            icon = Icons.Filled.Groups,
            title = "Comunidad",
            description = "Publicaciones, perfiles, seguidores y mensajes.",
            onClick = onCommunity
        )

        Text("VUESTROS RECUERDOS Y RECURSOS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        MoreDestinationCard(
            icon = Icons.Filled.AutoStories,
            title = "Diario de recuerdos",
            description = "Guarda momentos, pequeñas historias y fotografías.",
            onClick = onDiary
        )
        MoreDestinationCard(
            icon = Icons.Filled.MenuBook,
            title = "Aprender",
            description = "Artículos, consejos y recursos para su bienestar.",
            onClick = onLearn
        )
        MoreDestinationCard(
            icon = Icons.Filled.Settings,
            title = "Ajustes",
            description = "Diseño, colores, modo oscuro, idioma y servicios multimedia.",
            onClick = onSettings
        )

        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Pets, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Pawly reúne el cuidado diario, la ayuda profesional y una comunidad para todas las mascotas.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MoreDestinationCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 25.sp, fontWeight = FontWeight.Light)
        }
    }
}
