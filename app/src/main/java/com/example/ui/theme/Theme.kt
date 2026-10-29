package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NexusDarkColorScheme = darkColorScheme(
    primary = NexusCyan,
    onPrimary = NexusOnCyan,
    primaryContainer = NexusCyanContainer,
    onPrimaryContainer = Color(0xFFB5F7FF),
    secondary = NexusViolet,
    onSecondary = Color(0xFF1A083B),
    secondaryContainer = NexusVioletContainer,
    onSecondaryContainer = Color(0xFFE7DAFF),
    tertiary = NexusYouTubeCrimson,
    onTertiary = Color.White,
    tertiaryContainer = NexusCrimsonContainer,
    onTertiaryContainer = Color(0xFFFFD9DE),
    background = NexusObsidian,
    onBackground = NexusTextPrimary,
    surface = NexusDeepSurface,
    onSurface = NexusTextPrimary,
    surfaceVariant = NexusElevatedCard,
    onSurfaceVariant = NexusTextSecondary,
    surfaceContainerHigh = NexusElevatedHigh,
    outline = NexusOutline
)

private val NexusLightColorScheme = lightColorScheme(
    primary = LightCyanPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4F3FC),
    onPrimaryContainer = Color(0xFF001F25),
    secondary = LightVioletSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9DDFF),
    onSecondaryContainer = Color(0xFF1E0060),
    tertiary = LightCrimsonTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDADE),
    onTertiaryContainer = Color(0xFF40000D),
    background = LightCanvas,
    onBackground = Color(0xFF0E1422),
    surface = LightSurface,
    onSurface = Color(0xFF0E1422),
    surfaceVariant = Color(0xFFE8EEF7),
    onSurfaceVariant = Color(0xFF46526B),
    outline = Color(0xFFC3CEE2)
)

val NexusShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NexusTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NexusDarkColorScheme else NexusLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = NexusShapes,
        content = content
    )
}
