package com.pawly.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val Forest = Color(0xFF173F35)
internal val ForestLight = Color(0xFF2D6655)
internal val Cream = Color(0xFFF7F7F2)
internal val Paper = Color(0xFFFFFFFF)
internal val Ink = Color(0xFF202923)
internal val Muted = Color(0xFF7C887F)
internal val Lime = Color(0xFFD8E8A8)
internal val Peach = Color(0xFFF4D7C5)
internal val Line = Color(0xFFE5E9E3)

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
    val scheme = if (darkMode) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFF071D18),
            primaryContainer = Color(0xFF254B3F),
            onPrimaryContainer = Color(0xFFE0F2E7),
            secondary = secondary,
            onSecondary = Color(0xFF102027),
            secondaryContainer = Color(0xFF293D42),
            onSecondaryContainer = Color(0xFFDCEEF0),
            background = Color(0xFF101713),
            onBackground = Color(0xFFE6EEE8),
            surface = Color(0xFF19221D),
            onSurface = Color(0xFFE6EEE8),
            surfaceVariant = Color(0xFF29342E),
            onSurfaceVariant = Color(0xFFB7C4BA),
            outline = Color(0xFF53645A),
            outlineVariant = Color(0xFF39463E)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = when (accent) {
                "ocean" -> Color(0xFFD9EDF3)
                "berry" -> Color(0xFFF0E2F0)
                "sunset" -> Color(0xFFF9E5DA)
                else -> Color(0xFFE2EDE3)
            },
            onPrimaryContainer = Color(0xFF17352B),
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE9EFE9),
            onSecondaryContainer = Color(0xFF263A30),
            background = Cream,
            onBackground = Ink,
            surface = Paper,
            onSurface = Ink,
            surfaceVariant = Color(0xFFF0F2ED),
            onSurfaceVariant = Muted,
            outline = Color(0xFF87948A),
            outlineVariant = Line
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
            headlineMedium = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp),
            titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp),
            titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
            labelMedium = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        ),
        content = content
    )
}
