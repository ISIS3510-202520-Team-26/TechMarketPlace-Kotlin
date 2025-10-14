package com.techmarketplace.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ---------- Brand tokens (from your Figma shots) ---------- */
val BrandGreen = Color(0xFF114232)          // primary
val BrandGreenSecondary = Color(0xFF759188) // subtle accent
val BackgroundLight = Color(0xFFF5F5F5)     // page background
val SurfaceLight = Color(0xFFFFFFFF)        // cards / containers
val TextMain = Color(0xFF000000)            // main text on light surfaces
val TextSecondary = Color(0xFF666666)       // placeholder / helper / secondary
val OutlineLight = Color(0xFFE0E0E0)        // borders / dividers
val ErrorRed = Color(0xFFB3261E)

/* ---------- Color schemes ---------- */
private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,

    background = BackgroundLight,
    onBackground = TextMain,

    surface = SurfaceLight,
    onSurface = TextMain,
    onSurfaceVariant = TextSecondary,

    secondary = BrandGreenSecondary,
    outline = OutlineLight,

    error = ErrorRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,

    background = Color(0xFF121212),
    onBackground = Color(0xFFF2F2F2),

    surface = Color(0xFF1C1C1C),
    onSurface = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFFB8C0C2),

    secondary = BrandGreenSecondary,
    outline = Color(0xFF2C2C2C),

    error = ErrorRed,
    onError = Color.White,
)

/* ---------- Typography (headline/field/cta from Figma) ---------- */
val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal
    )
)

/* ---------- Shapes (rounded pills & soft cards) ---------- */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp) // buttons / bottom actions
)

/* ---------- Theme entry ---------- */
@Composable
fun TMTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // keep, even if you run light-only for now
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
