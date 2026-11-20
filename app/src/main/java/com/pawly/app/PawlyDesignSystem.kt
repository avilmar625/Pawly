package com.pawly.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pawly's shared visual primitives. Feature screens should use these building
 * blocks and MaterialTheme.colorScheme instead of defining local palettes.
 */
@Composable
internal fun PawlyCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(contentPadding), content = { content() })
    }
}

@Composable
internal fun PawlySectionHeading(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    var showInfo by remember(title, subtitle) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
            if (!subtitle.isNullOrBlank()) {
                IconButton(onClick = { showInfo = !showInfo }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Info, contentDescription = "Más información sobre $title", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (!subtitle.isNullOrBlank()) {
            AnimatedVisibility(visible = showInfo) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** A compact, consistent top-level tab strip used across the app. */
@Composable
internal fun PawlyTabStrip(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp).height(42.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                labels.forEachIndexed { index, label ->
                    val isSelected = index == selectedIndex
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .semantics { selected = isSelected }
                            .clickable(role = Role.Tab) { onSelect(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = if (label.length > 11) 10.sp else 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        }
    }
}

/** Shared title, action and tab layout for main sections with their own pages. */
@Composable
internal fun PawlySectionNavigationHeader(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    includeStatusBarInset: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .then(if (includeStatusBarInset) Modifier.statusBarsPadding() else Modifier)
                    .padding(start = 18.dp, end = 8.dp, top = 0.dp, bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                actions()
            }
            PawlyTabStrip(labels = labels, selectedIndex = selectedIndex, onSelect = onSelect)
        }
    }
}

@Composable
internal fun PawlyStatusPill(
    label: String,
    modifier: Modifier = Modifier,
    tone: PawlyStatusTone = PawlyStatusTone.Neutral
) {
    val colors = MaterialTheme.colorScheme
    val pair = when (tone) {
        PawlyStatusTone.Success -> colors.tertiaryContainer to colors.onTertiaryContainer
        PawlyStatusTone.Warning -> colors.secondaryContainer to colors.onSecondaryContainer
        PawlyStatusTone.Error -> colors.errorContainer to colors.onErrorContainer
        PawlyStatusTone.Neutral -> colors.surfaceVariant to colors.onSurfaceVariant
        PawlyStatusTone.Brand -> colors.primaryContainer to colors.onPrimaryContainer
    }
    Surface(modifier = modifier, color = pair.first, contentColor = pair.second, shape = CircleShape) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal enum class PawlyStatusTone { Neutral, Brand, Success, Warning, Error }

@Composable
internal fun PawlyIconTile(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.primaryContainer
) {
    Surface(modifier = modifier, color = background, shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) { content() }
    }
}
