package com.pawly.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

// Nunito is bundled locally (SIL Open Font License); rendering never requires a network request.
private val PawlyNunito = FontFamily(
    Font(R.font.nunito_variable, FontWeight.Thin),
    Font(R.font.nunito_variable, FontWeight.ExtraLight),
    Font(R.font.nunito_variable, FontWeight.Light),
    Font(R.font.nunito_variable, FontWeight.Normal),
    Font(R.font.nunito_variable, FontWeight.Medium),
    Font(R.font.nunito_variable, FontWeight.SemiBold),
    Font(R.font.nunito_variable, FontWeight.Bold),
    Font(R.font.nunito_variable, FontWeight.ExtraBold),
    Font(R.font.nunito_variable, FontWeight.Black)
)

internal val Forest = Color(0xFF173F35)
internal val ForestLight = Color(0xFF2D6655)
internal val Cream = Color(0xFFFFF8F0)
internal val Paper = Color(0xFFFFFEFB)
internal val Ink = Color(0xFF202923)
// Slightly darker secondary text keeps small labels readable on the warm surface palette.
internal val Muted = Color(0xFF625F56)
internal val Lime = Color(0xFFE4EBC1)
internal val Peach = Color(0xFFF3DCCF)
internal val Line = Color(0xFFECE5DC)
// Shared dark overlay for photographic headers; keeps white text legible across destinations.
internal val PawlyPhotoScrim = Color(0xD910251F)

private data class PawlyAccent(
    val primary: Color,
    val secondary: Color,
    val lightContainer: Color,
    val darkPrimary: Color,
    val darkContainer: Color
)

private fun pawlyAccent(key: String) = when (key) {
    "ocean" -> PawlyAccent(Color(0xFF176B87), Color(0xFF398CA8), Color(0xFFD9EDF3), Color(0xFF8CCBE0), Color(0xFF244957))
    "berry" -> PawlyAccent(Color(0xFF805080), Color(0xFFA678A6), Color(0xFFF0E2F0), Color(0xFFD9B4DA), Color(0xFF4A354D))
    "sunset" -> PawlyAccent(Color(0xFFB65D3C), Color(0xFFD68A61), Color(0xFFF9E5DA), Color(0xFFF0B18E), Color(0xFF593A30))
    else -> PawlyAccent(Forest, ForestLight, Color(0xFFE2EDE3), Color(0xFFA7D0B7), Color(0xFF2D5144))
}

private val PawlyTypography = Typography(
    displayLarge = TextStyle(fontFamily = PawlyNunito, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 54.sp, letterSpacing = (-1.1).sp),
    displayMedium = TextStyle(fontFamily = PawlyNunito, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 46.sp, letterSpacing = (-0.7).sp),
    displaySmall = TextStyle(fontFamily = PawlyNunito, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = PawlyNunito, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp, letterSpacing = (-0.35).sp),
    headlineMedium = TextStyle(fontFamily = PawlyNunito, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp),
    headlineSmall = TextStyle(fontFamily = PawlyNunito, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp),
    titleLarge = TextStyle(fontFamily = PawlyNunito, fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = PawlyNunito, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = PawlyNunito, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontFamily = PawlyNunito, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = PawlyNunito, fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = PawlyNunito, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = PawlyNunito, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = PawlyNunito, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.15.sp),
    labelSmall = TextStyle(fontFamily = PawlyNunito, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
)

@Composable
internal fun PawlyTheme(darkMode: Boolean = false, accent: String = "forest", content: @Composable () -> Unit) {
    val colors = pawlyAccent(accent)
    val scheme = if (darkMode) {
        darkColorScheme(
            primary = colors.darkPrimary,
            onPrimary = Color(0xFF14241C),
            primaryContainer = colors.darkContainer,
            onPrimaryContainer = Color(0xFFE8F3EC),
            secondary = colors.darkPrimary.copy(alpha = 0.82f),
            onSecondary = Color(0xFF14241C),
            secondaryContainer = Color(0xFF293D35),
            onSecondaryContainer = Color(0xFFDCEBE1),
            tertiary = Color(0xFFE6C58B),
            onTertiary = Color(0xFF302515),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            background = Color(0xFF101713),
            onBackground = Color(0xFFE6EEE8),
            surface = Color(0xFF1B2620),
            onSurface = Color(0xFFEAF1EC),
            surfaceVariant = Color(0xFF29342E),
            onSurfaceVariant = Color(0xFFB7C4BA),
            outline = Color(0xFF718278),
            outlineVariant = Color(0xFF39463E)
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = Color.White,
            primaryContainer = colors.lightContainer,
            onPrimaryContainer = Color(0xFF17352B),
            secondary = colors.secondary,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF4E9DF),
            onSecondaryContainer = Color(0xFF263A30),
            tertiary = Color(0xFF8A6B36),
            onTertiary = Color.White,
            error = Color(0xFFB3261E),
            onError = Color.White,
            background = Cream,
            onBackground = Ink,
            surface = Paper,
            onSurface = Ink,
            surfaceVariant = Color(0xFFF5F0E9),
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
        typography = PawlyTypography,
        content = {
            // Material controls keep a consistent 48dp minimum hit target across the app.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 48.dp) {
                content()
            }
        }
    )
}
