package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SalmanDarkColorScheme = darkColorScheme(
    primary = SalmanCrimson,
    onPrimary = TextPrimary,
    primaryContainer = SalmanCrimsonDark,
    onPrimaryContainer = TextPrimary,
    secondary = SalmanCyan,
    onSecondary = ObsidianBg,
    secondaryContainer = ObsidianSurfaceElevated,
    onSecondaryContainer = SalmanCyan,
    tertiary = SalmanGold,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = OutlineSubtle,
    error = SalmanError,
    onError = TextPrimary
)

val SalmanShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SalmanVideoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SalmanDarkColorScheme,
        typography = SalmanTypography,
        shapes = SalmanShapes,
        content = content
    )
}
