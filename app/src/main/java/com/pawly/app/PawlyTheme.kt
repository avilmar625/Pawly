package com.pawly.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val Forest = Color(0xFF173F35)
internal val ForestLight = Color(0xFF2D6655)
internal val Cream = Color(0xFFF8F7F2)
internal val Paper = Color(0xFFFFFFFF)
internal val Ink = Color(0xFF202923)
internal val Muted = Color(0xFF7C887F)
internal val Lime = Color(0xFFD8E8A8)
internal val Peach = Color(0xFFF4D7C5)
internal val Line = Color(0xFFE9EAE3)

@Composable
internal fun PawlyTheme(darkMode: Boolean = false, accent: String = "forest", content: @Composable () -> Unit) {
    val primary = when (accent) {
        "ocean" -> Color(0xFF176B87)
        "berry" -> Color(0xFF805080)
        "sunset" -> Color(0xFFB65D3C)
        else -> Forest
    }
    val secondary = when (accent) {
        "ocean" -> Color(0xFF398CA8)
        "berry" -> Color(0xFFA678A6)
        "sunset" -> Color(0xFFD68A61)
        else -> ForestLight
    }
    MaterialTheme(
        colorScheme = if (darkMode) darkColorScheme(
            primary = primary,
            secondary = secondary,
            background = Color(0xFF111815),
            surface = Color(0xFF1B2420),
            onSurface = Color(0xFFE7EEE9),
            onBackground = Color(0xFFE7EEE9)
        ) else lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            secondary = secondary,
            background = Cream,
            surface = Paper,
            onSurface = Ink,
            onBackground = Ink
        ),
        typography = Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 35.sp),
            headlineMedium = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold),
            titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
            labelMedium = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        ),
        content = content
    )
}

