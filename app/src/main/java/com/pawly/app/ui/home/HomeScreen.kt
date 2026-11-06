package com.pawly.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import coil3.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun HomeScreen(
    dogName: String,
    walkDone: Boolean,
    medicineDone: Boolean,
    onWalkToggle: (Boolean) -> Unit,
    onMedicineToggle: (Boolean) -> Unit,
    onExplore: () -> Unit,
    onLearn: () -> Unit,
    onAbout: () -> Unit,
    onEditProfile: () -> Unit,
    diaryCount: Int,
    dogPhoto: Bitmap?
) {
    val completed = listOf(walkDone, medicineDone).count { it }
    val dateLabel = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES"))
        .format(Date()).replaceFirstChar { it.uppercase(Locale("es", "ES")) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("HOLA, QUÉ ALEGRÍA VEROS", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                    Text(dateLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Surface(onClick = onAbout, shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, contentDescription = "Acerca de Pawly", tint = Forest)
                    }
                }
            }
        }
        item { HomePetHero(dogName, dogPhoto, onEditProfile) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Un día más juntos, ${dogName.ifBlank { "amigo" }}", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("Todo lo importante para disfrutar juntos, en un mismo lugar.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.CheckCircle,
                    value = "$completed/2",
                    label = "rutinas hechas",
                    accent = Color(0xFFE5EEDC)
                )
                HomeStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.AutoStories,
                    value = diaryCount.toString(),
                    label = "recuerdos",
                    accent = Color(0xFFF5E7DD)
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("La rutina de hoy", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (completed == 2) "¡Todo al día! Qué bien lo hacéis." else "Pequeños cuidados que suman.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Text("$completed de 2", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            LinearProgressIndicator(
                progress = { completed / 2f },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryLight,
                trackColor = Line
            )
        }
        item {
            HomeRoutineCard(
                icon = Icons.Filled.DirectionsWalk,
                title = "Paseo y olfateo",
                subtitle = "Un rato para explorar sin prisa",
                helper = "MOVIMIENTO",
                checked = walkDone,
                onChecked = onWalkToggle,
                iconColor = Color(0xFF456C50),
                iconBackground = Color(0xFFE6EFDF)
            )
        }
        item {
            HomeRoutineCard(
                icon = Icons.Filled.Medication,
                title = "Revisar cuidados",
                subtitle = "Comprueba lo que toca hoy",
                helper = "BIENESTAR",
                checked = medicineDone,
                onChecked = onMedicineToggle,
                iconColor = Color(0xFF986747),
                iconBackground = Color(0xFFF6E9DF)
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Vuestra próxima aventura", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("Planes para salir de la rutina.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onExplore) { Text("Ver todo") }
                }
                HomeFeatureCard(
                    title = "Hay mucho por descubrir",
                    subtitle = "Encuentra rutas, rincones y planes para compartir.",
                    action = "EXPLORAR LUGARES",
                    icon = Icons.Filled.Explore,
                    onClick = onExplore
                )
            }
        }
        item {
            HomeLearnCard(onClick = onLearn)
        }
        item {
            Text(
                "HECHO PARA COMPARTIR MÁS VIDA JUNTOS",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun HomePetHero(name: String, photo: Bitmap?, onEdit: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF123D32), Color(0xFF285E4C), Color(0xFF6C8D68))))
            .clickable(onClick = onEdit)
            .padding(21.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("VUESTRO COMPAÑERO", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
                    Text(name, color = Color.White, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold)
                    Text("El protagonista de cada aventura.", color = Color.White.copy(alpha = .78f), fontSize = 12.sp)
                }
                Box(
                    Modifier.size(104.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = .16f))
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = .12f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (photo != null) {
                        Image(photo.asImageBitmap(), contentDescription = "Foto de $name", modifier = Modifier.fillMaxSize().clip(CircleShape))
                    } else {
                        AsyncImage(
                            model = "https://images.unsplash.com/photo-1552053831-71594a27632d?w=420&h=420&fit=crop&crop=faces",
                            contentDescription = "Perro al aire libre",
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color.White.copy(alpha = .15f), shape = RoundedCornerShape(50)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Editar perfil", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.Pets, contentDescription = null, tint = Color.White.copy(alpha = .35f), modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun HomeStatCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    accent: Color
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(accent), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Forest, modifier = Modifier.size(21.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun HomeRoutineCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    helper: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    iconColor: Color,
    iconBackground: Color
) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(iconBackground), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(helper, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Text(title, color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Checkbox(checked = checked, onCheckedChange = onChecked, colors = CheckboxDefaults.colors(checkedColor = Forest))
        }
    }
}

@Composable
private fun HomeFeatureCard(
    title: String,
    subtitle: String,
    action: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(25.dp),
        color = MaterialTheme.colorScheme.primary
    ) {
        Row(Modifier.fillMaxWidth().padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color.White.copy(alpha = .76f), fontSize = 12.sp, lineHeight = 17.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(action, color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Lime, modifier = Modifier.size(14.dp))
                }
            }
            Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun HomeLearnCard(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF1EBD9)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = Color(0xFF8B7443), modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Un minuto para aprender", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Consejos, artículos e ideas para cuidar mejor.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Forest)
        }
    }
}

/* Shared components used by the health and care areas. */
@Composable
internal fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun TaskRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    tint: Color
) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(tint), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Forest)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Checkbox(checked = checked, onCheckedChange = onChecked, colors = CheckboxDefaults.colors(checkedColor = Forest))
        }
    }
}
