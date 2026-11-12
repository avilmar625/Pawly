package com.pawly.app

import com.pawly.app.tr

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.text.DateFormat
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
    dogPhoto: Bitmap?,
    dogPhotoUrl: String? = null,
    elegantDesign: Boolean = false
) {
    val completed = listOf(walkDone, medicineDone).count { it }
    val language = LocalPawlyLanguage.current
    val locale = PawlyI18n.locale(language)
    val dateLabel = DateFormat.getDateInstance(DateFormat.FULL, locale)
        .format(Date()).replaceFirstChar { it.uppercase(locale) }
    val firstName = dogName.ifBlank { "tu compañero" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(dateLabel.uppercase(locale), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.25.sp)
                    Text(tr("Qué bien tenerte por aquí"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Surface(
                    onClick = onAbout,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Pets, contentDescription = tr("Acerca de Pawly"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        item { HomePetHero(dogName, dogPhoto, dogPhotoUrl, onEditProfile, elegantDesign) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(tr("Un día bonito\ncon $firstName."), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(tr("Cuidados, recuerdos y nuevas aventuras. Todo vuestro, en un mismo lugar."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.CheckCircle,
                    value = "$completed/2",
                    label = "rutinas de hoy",
                    accent = Color(0xFFE5EFE5)
                )
                HomeStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.AutoStories,
                    value = diaryCount.toString(),
                    label = "recuerdos",
                    accent = Color(0xFFF6E9DF)
                )
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(tr("Vuestro día, paso a paso"), style = MaterialTheme.typography.titleLarge)
                            Text(tr(if (completed == 2) "¡Todo hecho! Os lo habéis ganado." else "Pequeños gestos, grandes cuidados."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                            Text(tr("$completed/2"), modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    LinearProgressIndicator(
                        progress = { completed / 2f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    HomeRoutineRow(
                        icon = Icons.Filled.DirectionsWalk,
                        title = "Paseo y olfateo",
                        subtitle = "Un rato para explorar sin prisa",
                        checked = walkDone,
                        onChecked = onWalkToggle,
                        iconBackground = Color(0xFFE6EFDF),
                        iconTint = Color(0xFF456C50)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    HomeRoutineRow(
                        icon = Icons.Filled.FavoriteBorder,
                        title = "Revisar sus cuidados",
                        subtitle = "Comprueba lo que toca hoy",
                        checked = medicineDone,
                        onChecked = onMedicineToggle,
                        iconBackground = Color(0xFFF6E9DF),
                        iconTint = Color(0xFF986747)
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(tr("Salid a descubrir"), style = MaterialTheme.typography.titleLarge)
                        Text(tr("Hay un mundo de planes por compartir."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onExplore) { Text(tr("Ver todo")) }
                }
                HomeExploreCard(onClick = onExplore)
            }
        }
        item { HomeLearnCard(onClick = onLearn) }
        item {
            Text(
                tr("HECHO PARA COMPARTIR MÁS VIDA JUNTOS"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.25.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HomePetHero(name: String, photo: Bitmap?, photoUrl: String?, onEdit: () -> Unit, elegantDesign: Boolean) {
    Box(
        Modifier.fillMaxWidth()
            .height(if (elegantDesign) 292.dp else 246.dp)
            .clip(RoundedCornerShape(30.dp))
            .clickable(onClick = onEdit)
    ) {
        if (photo != null) {
            Image(
                photo.asImageBitmap(),
                contentDescription = tr("Foto de $name"),
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            AsyncImage(
                model = photoUrl?.takeIf { it.startsWith("https://") }
                    ?: "https://images.unsplash.com/photo-1552053831-71594a27632d?w=1200&h=800&fit=crop&crop=faces",
                contentDescription = "Perro disfrutando al aire libre",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x08101D17),
                    0.38f to Color(0x28101D17),
                    1f to Color(0xE610281F)
                )
            )
        )
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(color = if (elegantDesign) Color(0xFFFFD7B8) else Color(0xFFDDEBAF), shape = RoundedCornerShape(50)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Filled.Pets, contentDescription = null, tint = Color(0xFF234638), modifier = Modifier.size(12.dp))
                    Text(tr(if (elegantDesign) "EL PROTAGONISTA DE VUESTRA HISTORIA" else "VUESTRO COMPAÑERO"), color = Color(0xFF234638), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
            }
            Text(tr(name.ifBlank { "Tu compañero" }), color = Color.White, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("El protagonista de cada aventura"), color = Color.White.copy(alpha = .92f), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Surface(color = Color.White.copy(alpha = .2f), shape = RoundedCornerShape(50)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(tr("Editar perfil"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(accent), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color(0xFF315A45), modifier = Modifier.size(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(tr(label), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun HomeRoutineRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    iconBackground: Color,
    iconTint: Color
) {
    Row(
        Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(iconBackground), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tr(title), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(tr(subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onChecked,
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun HomeExploreCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.primary
    ) {
        Row(Modifier.fillMaxWidth().padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Una aventura os espera"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(tr("Encuentra rutas, playas y rincones dog-friendly para vuestra próxima escapada."), color = Color.White.copy(alpha = .82f), fontSize = 12.sp, lineHeight = 17.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("EXPLORAR LUGARES"), color = Color(0xFFDDEBAF), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .8.sp)
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color(0xFFDDEBAF), modifier = Modifier.size(14.dp))
                }
            }
            Box(Modifier.padding(start = 8.dp).size(58.dp).clip(CircleShape).background(Color.White.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Explore, contentDescription = null, tint = Color.White, modifier = Modifier.size(29.dp))
            }
        }
    }
}

@Composable
private fun HomeLearnCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFF1EBD9)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = Color(0xFF8B7443), modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tr("Un minuto para aprender"), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(tr("Consejos e ideas para cuidar mejor."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(tint), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Forest)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr(title), fontWeight = FontWeight.SemiBold, color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                Text(tr(subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Checkbox(checked = checked, onCheckedChange = onChecked, colors = CheckboxDefaults.colors(checkedColor = Forest))
        }
    }
}
