package com.pawly.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
