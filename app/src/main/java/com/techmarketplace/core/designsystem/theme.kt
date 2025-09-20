package com.techmarketplace.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = GreenDark,
    onPrimary = OffWhite,
    background = GreenDark,
    onBackground = OffWhite,
    surface = GreenDark,
    onSurface = OffWhite,
    secondary = Sage,
)

@Composable
fun TMTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content
    )
}
