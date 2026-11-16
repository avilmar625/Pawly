package com.pawly.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun HomeUpcomingCard(
    entryType: String,
    title: String,
    date: LocalDate,
    onClick: () -> Unit
) {
    val days = ChronoUnit.DAYS.between(LocalDate.now(), date).coerceAtLeast(0)
    val dateLabel = date.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.getDefault()))
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(46.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.HealthAndSafety, null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("PRÓXIMAMENTE", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1f)
                Text(title.ifBlank { entryType }, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text("$dateLabel · ${if (days == 0L) "hoy" else "dentro de $days días"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .8f))
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun HomeAnniversaryCard(
    anniversary: DiaryAnniversary,
    onClick: () -> Unit
) {
    val dateLabel = anniversary.date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (anniversary.photoUrls.isNotEmpty()) {
                AsyncImage(
                    model = anniversary.photoUrls.first(),
                    contentDescription = "Fotografía de un recuerdo anterior",
                    modifier = Modifier.size(76.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(
                    Modifier.size(76.dp).background(Color(0xFFE4EBC1), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Pets, null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("HACE UN AÑO · $dateLabel", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Text(anniversary.title, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text(
                    if (anniversary.photoUrls.size == 1) "1 foto · Diario" else if (anniversary.photoUrls.size > 1) "${anniversary.photoUrls.size} fotos · Diario" else "Diario de recuerdos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
