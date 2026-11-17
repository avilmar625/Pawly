package com.pawly.app

import com.pawly.app.tr

import android.graphics.Bitmap
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
    onSettings: () -> Unit = onAbout,
    onEditProfile: () -> Unit,
    diaryCount: Int,
    diaryEntries: List<String> = emptyList(),
    dogPhoto: Bitmap?,
    dogPhotoUrl: String? = null,
    elegantDesign: Boolean = false,
    onNotifications: () -> Unit = {},
    onDiary: () -> Unit = {},
    onCalendar: () -> Unit = {},
    onCommunity: () -> Unit = {},
    onHealth: () -> Unit = {},
    onWeight: () -> Unit = onHealth,
    onPhoto: () -> Unit = onDiary,
    onFood: () -> Unit = onHealth,
    healthDates: Map<String, String> = emptyMap(),
    calendarReminders: List<String> = emptyList(),
    onOpenHealthAgenda: () -> Unit = onHealth,
    onOpenPassport: () -> Unit = onHealth,
    storageScope: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val language = LocalPawlyLanguage.current
    val locale = PawlyI18n.locale(language)
    val dateLabel = DateFormat.getDateInstance(DateFormat.FULL, locale)
        .format(Date()).replaceFirstChar { it.uppercase(locale) }
    var passportEntries by remember(dogName, storageScope) {
        mutableStateOf(com.pawly.app.care.data.PetCareLocalStore.loadEntries(context, dogName, storageScope))
    }

    LaunchedEffect(dogName, storageScope) {
        val local = com.pawly.app.care.data.PetCareLocalStore.loadEntries(context, dogName, storageScope)
        passportEntries = local
        if (!storageScope.isNullOrBlank()) {
            isLoadingRemotePassport = local.isEmpty()
            try {
                runCatching {
                    val repository = com.pawly.app.care.data.FirebasePetCareRepository()
                    repository.ensureSharedPet(dogName, dogName)
                    val remote = repository.loadPassportEntries(dogName)
                    if (remote.isEmpty() && local.isNotEmpty()) {
                        repository.savePassportEntries(dogName, local)
                        local
                    } else {
                        remote
                    }
                }.onSuccess { merged ->
                    passportEntries = merged
                    com.pawly.app.care.data.PetCareLocalStore.saveEntries(context, dogName, merged, storageScope)
                }
            } finally {
                isLoadingRemotePassport = false
            }
        } else {
            isLoadingRemotePassport = false
        }
    }

    val weightEntries = passportEntries.filter { it.type.equals("Peso", true) }
    val today = java.time.LocalDate.now()
    val nextUpcomingItem = remember(passportEntries, healthDates, calendarReminders) {
        findNextHomeUpcomingItem(
            passportEntries = passportEntries,
            healthDates = healthDates,
            calendarReminders = calendarReminders
        )
    }
    val diaryAnniversary = remember(diaryEntries, today) { findDiaryAnniversary(diaryEntries, today) }

    val parsedWeights = weightEntries.mapNotNull { entry ->
        com.pawly.app.care.domain.WeightMeasurementParser
            .parse("${entry.title} ${entry.detail}")
            ?.let { entry.date to it }
    }.sortedBy { it.first }
    val latestWeight = parsedWeights.lastOrNull()?.second
    val previousWeight = parsedWeights.dropLast(1).lastOrNull()?.second
    val weightChange = if (latestWeight != null && previousWeight != null) latestWeight - previousWeight else null
    val completed = listOf(walkDone, medicineDone).count { it }
    var isLoadingRemotePassport by remember(dogName, storageScope) { mutableStateOf(!storageScope.isNullOrBlank()) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= 700.dp
        LazyColumn(
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).align(Alignment.TopCenter),
            contentPadding = PaddingValues(
                start = if (wideLayout) 28.dp else 18.dp,
                end = if (wideLayout) 28.dp else 18.dp,
                top = if (wideLayout) 22.dp else 14.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (wideLayout) 19.dp else 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(dateLabel.uppercase(locale), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tr("¡Qué bien tenerte por aquí!"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Surface(onClick = onNotifications, shape = CircleShape, color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.NotificationsNone, contentDescription = tr("Notificaciones"), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(onClick = onSettings, shape = CircleShape, color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Settings, contentDescription = tr("Ajustes"), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item { HomePetHero(dogName, dogPhoto, dogPhotoUrl, onEditProfile, elegantDesign) }
        item {
            HomeCareTasksSection(
                petName = dogName,
                storageScope = storageScope,
                walkDone = walkDone,
                medicineDone = medicineDone,
                onWalkToggle = onWalkToggle,
                onMedicineToggle = onMedicineToggle,
                onOpenCalendar = onCalendar,
                onWeight = onWeight,
                onPhoto = onPhoto,
                onFood = onFood,
                upcomingTitle = nextUpcomingItem?.title,
                upcomingType = nextUpcomingItem?.type,
                upcomingDate = nextUpcomingItem?.date?.toString(),
                upcomingTime = nextUpcomingItem?.takeIf { it.destination != HomeUpcomingDestination.PASSPORT }
                    ?.scheduledAt?.atZone(java.time.ZoneId.systemDefault())
                    ?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm", locale)),
                onUpcomingClick = {
                    when (nextUpcomingItem?.destination) {
                        HomeUpcomingDestination.PASSPORT -> onOpenPassport()
                        HomeUpcomingDestination.HEALTH_AGENDA -> onOpenHealthAgenda()
                        HomeUpcomingDestination.CALENDAR -> onCalendar()
                        null -> onHealth()
                    }
                }
            )
        }
        item {
            Surface(onClick = onWeight, modifier = Modifier.animateContentSize(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MonitorWeight, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Evolución del peso"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(tr("Ver salud"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    if (isLoadingRemotePassport && passportEntries.isEmpty()) {
                        HomeWeightSkeleton()
                    } else if (latestWeight != null) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(String.format(locale, "%.1f", latestWeight), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(5.dp))
                            Text("kg", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 5.dp))
                            Spacer(Modifier.weight(1f))
                            if (weightChange != null) {
                                Text(String.format(locale, "%+.1f kg", weightChange), color = if (weightChange > 0) MaterialTheme.colorScheme.tertiary else if (weightChange < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (parsedWeights.size > 1) {
                            Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                                val recent = parsedWeights.takeLast(7).map { it.second }
                                val min = (recent.minOrNull() ?: 0f) - .25f
                                val max = (recent.maxOrNull() ?: 1f) + .25f
                                recent.forEachIndexed { index, value ->
                                    val fraction = ((value - min) / (max - min).coerceAtLeast(.1f)).coerceIn(.12f, 1f)
                                    Box(Modifier.weight(1f).fillMaxHeight(fraction).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(if (index == recent.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = .28f)))
                                }
                            }
                            Text(tr("Últimos registros guardados en el pasaporte de salud"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        } else {
                            Text(tr("Añade más mediciones para ver la evolución."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text(tr("Todavía no hay mediciones. Registra el primer peso en Mi perro → Pasaporte → Peso."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = onWeight) { Text(tr("Registrar primer peso")) }
                    }
                }
            }
        }
        item {
            Surface(onClick = onDiary, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFF8EBDD)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.AutoStories, null, tint = Color(0xFF9A7043), modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(tr("Diario de recuerdos"), fontWeight = FontWeight.Bold)
                        Text(tr(if (diaryCount == 0) "Guarda vuestra primera aventura." else "$diaryCount recuerdos guardados"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (diaryAnniversary != null) {
            item {
                HomeAnniversaryCard(anniversary = diaryAnniversary, onClick = onDiary)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Ideas para vuestra próxima salida"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(tr("Planes y lugares dog-friendly"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onExplore) { Text(tr("Ver todo")) }
                }
                HomeExploreCard(onClick = onExplore, elegantDesign = elegantDesign)
            }
        }
        item { HomeLearnCard(onClick = onLearn) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCommunity, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Groups, null); Spacer(Modifier.width(5.dp)); Text(tr("Comunidad")) }
                TextButton(onClick = onCalendar, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.CalendarMonth, null); Spacer(Modifier.width(5.dp)); Text(tr("Calendario")) }
            }
        }
        }
    }
}

@Composable
private fun HomePetHero(name: String, photo: Bitmap?, photoUrl: String?, onEdit: () -> Unit, elegantDesign: Boolean) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            Modifier.size(58.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            when {
                photo != null -> Image(
                    photo.asImageBitmap(),
                    contentDescription = tr("Foto de $name"),
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                photoUrl?.startsWith("https://") == true -> AsyncImage(
                    model = photoUrl,
                    contentDescription = tr("Foto de $name"),
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                else -> Text(
                    name.trim().take(1).uppercase().ifBlank { "P" },
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(tr("Hoy para"), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
            Text(name.ifBlank { tr("Tu mascota") }, color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = tr("Editar perfil de mascota"),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(13.dp).size(19.dp)
            )
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
private fun HomeExploreCard(onClick: () -> Unit, elegantDesign: Boolean) {
    if (elegantDesign) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(Modifier.fillMaxWidth().height(176.dp)) {
                AsyncImage(
                    model = "https://images.unsplash.com/photo-1548199973-03cce0bbc87b?w=1200&h=600&fit=crop",
                    contentDescription = tr("Perros disfrutando de una salida al aire libre"),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xE8173F35)),
                            startY = 30f
                        )
                    )
                )
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(tr("UNA AVENTURA OS ESPERA"), color = Color(0xFFE4EBC1),
                        fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    Text(tr("Encuentra vuestro próximo lugar favorito"),
                        color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("EXPLORAR LUGARES"), color = Color.White,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(5.dp))
                        Icon(Icons.Filled.ArrowForward, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    } else {
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
    val haptic = LocalHapticFeedback.current
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChecked(!checked)
            }.padding(horizontal = 15.dp, vertical = 13.dp),
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
            Checkbox(checked = checked, onCheckedChange = { checkedNow -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChecked(checkedNow) }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
        }
    }
}


@Composable
private fun HomeQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp))
            }
            Text(tr(label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}


@Composable
private fun HomeWeightSkeleton() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PawlySkeletonBlock(Modifier.fillMaxWidth(0.38f), height = 12.dp)
        PawlySkeletonBlock(Modifier.fillMaxWidth(0.54f), height = 29.dp)
        PawlySkeletonBlock(Modifier.fillMaxWidth(), height = 44.dp, shape = RoundedCornerShape(10.dp))
    }
}
