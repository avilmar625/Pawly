package com.pawly.app

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * Shared page introduction for Pawly's primary destinations.
 * Keeps hierarchy, spacing and typography consistent without coupling
 * feature screens to one another or changing their existing actions.
 */
@Composable
internal fun PawlyPageHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().animateContentSize(animationSpec = tween(durationMillis = 180)).padding(bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            tr(eyebrow.uppercase()),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.7.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            tr(title),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            tr(subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}


/** Reusable shimmer placeholder for genuinely asynchronous content. */
@Composable
internal fun PawlySkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    val transition = rememberInfiniteTransition(label = "pawly-skeleton")
    val shimmer = transition.animateFloat(
        initialValue = -0.7f,
        targetValue = 1.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250),
            repeatMode = RepeatMode.Restart
        ),
        label = "pawly-skeleton-shimmer"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .height(height)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = androidx.compose.ui.geometry.Offset(shimmer.value * 360f, 0f),
                    end = androidx.compose.ui.geometry.Offset((shimmer.value + 0.8f) * 360f, 0f)
                ),
                shape = shape
            )
    )
}
