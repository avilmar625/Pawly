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
    
    var passportEntries by remember(dogName, storageScope) {
        mutableStateOf(com.pawly.app.care.data.PetCareLocalStore.loadEntries(context, dogName, storageScope))
    }
    LaunchedEffect(dogName, storageScope) {
        val local = com.pawly.app.care.data.PetCareLocalStore.loadEntries(context, dogName, storageScope)
        passportEntries = local
        if (!storageScope.isNullOrBlank()) {
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
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
            }
        }
    }

    val nextUpcomingItem = remember(passportEntries, healthDates, calendarReminders) {
        findNextHomeUpcomingItem(
            passportEntries = passportEntries,
            healthDates = healthDates,
            calendarReminders = calendarReminders
        )
    }
    val parsedWeights = weightEntries.mapNotNull { entry ->
        com.pawly.app.care.domain.WeightMeasurementParser
            .parse("${entry.title} ${entry.detail}")
            ?.let { entry.date to it }
    }.sortedBy { it.first }
    val latestWeight = parsedWeights.lastOrNull()?.second
    val previousWeight = parsedWeights.dropLast(1).lastOrNull()?.second
    val weightChange = if (latestWeight != null && previousWeight != null) latestWeight - previousWeight else null

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
            HomePetHeader(
                name = dogName,
                photo = dogPhoto,
                photoUrl = dogPhotoUrl,
                onEditProfile = onEditProfile,
                onNotifications = onNotifications,
                onSettings = onSettings
            )
        }
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
        }
    }
}

@Composable
private fun HomePetHeader(
    name: String,
    photo: Bitmap?,
    photoUrl: String?,
    onEditProfile: () -> Unit,
    onNotifications: () -> Unit,
    onSettings: () -> Unit
) {
    var useDefaultPhoto by remember(photoUrl) { mutableStateOf(photoUrl.isNullOrBlank()) }
    var defaultPhotoUnavailable by remember(photoUrl) { mutableStateOf(false) }
    val customPhotoUrl = photoUrl?.takeIf { it.startsWith("https://") }
    val displayedPhotoUrl = if (useDefaultPhoto) PAWLY_DEFAULT_DOG_PHOTO_URL else customPhotoUrl

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onEditProfile).padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                when {
                    photo != null -> Image(
                        bitmap = photo.asImageBitmap(),
                        contentDescription = tr("Foto de $name"),
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    displayedPhotoUrl != null && !defaultPhotoUnavailable -> AsyncImage(
                        model = displayedPhotoUrl,
                        contentDescription = tr("Foto de ${name.ifBlank { "tu mascota" }}"),
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        onError = {
                            if (!useDefaultPhoto) useDefaultPhoto = true else defaultPhotoUnavailable = true
                        }
                    )
                    else -> Icon(Icons.Filled.Pets, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(name.ifBlank { tr("Tu mascota") }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tr("Editar perfil"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
        Surface(onClick = onNotifications, shape = CircleShape, color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.NotificationsNone, contentDescription = tr("Notificaciones"), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Surface(onClick = onSettings, shape = CircleShape, color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Settings, contentDescription = tr("Ajustes"), tint = MaterialTheme.colorScheme.primary)
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
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(23.dp))
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
internal fun SectionHeading(title: String, subtitle: String = "") {
    PawlySectionHeading(title = title, subtitle = subtitle)
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
