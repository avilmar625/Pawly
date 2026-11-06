package com.pawly.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

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
            onPrimary = Color.White,
            primaryContainer = Color(0xFF2D5144),
            onPrimaryContainer = Color(0xFFE5F2E9),
            secondary = secondary,
            onSecondary = Color(0xFF102027),
            secondaryContainer = Color(0xFF293D42),
            onSecondaryContainer = Color(0xFFDCEEF0),
            background = Color(0xFF101713),
            onBackground = Color(0xFFE6EEE8),
            surface = Color(0xFF1B2620),
            onSurface = Color(0xFFEAF1EC),
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
            background = Color(0xFFF6F7F2),
            onBackground = Color(0xFF202A23),
            surface = Color(0xFFFFFEFC),
            onSurface = Color(0xFF202A23),
            surfaceVariant = Color(0xFFF0F2ED),
            onSurfaceVariant = Muted,
            outline = Color(0xFF87948A),
            outlineVariant = Line
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(26.dp),
            extraLarge = RoundedCornerShape(32.dp)
        ),
        typography = Typography(
            displayLarge = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 54.sp, letterSpacing = (-1.1).sp),
            displayMedium = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 46.sp, letterSpacing = (-0.7).sp),
            displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp),
            headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp, letterSpacing = (-0.35).sp),
            headlineMedium = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp),
            headlineSmall = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp),
            titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp),
            titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
            titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp),
            bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
            bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
            labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
            labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.15.sp),
            labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
        ),
        content = content
    )
}
