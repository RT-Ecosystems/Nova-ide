package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val NovaIdeLightColorScheme = lightColorScheme(
    primary = BrandIndigo,
    onPrimary = OnBrandLight,
    primaryContainer = BrandIndigoContainer,
    onPrimaryContainer = BrandIndigoDark,
    secondary = AccentEmerald,
    onSecondary = OnBrandLight,
    secondaryContainer = AccentEmeraldContainer,
    onSecondaryContainer = AccentEmerald,
    tertiary = AccentViolet,
    onTertiary = OnBrandLight,
    background = SurfaceBackground,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = SurfaceCard,
    surfaceContainerLow = SurfaceBackground,
    surfaceContainer = SurfaceElevated,
    surfaceContainerHigh = SurfaceElevated,
    surfaceContainerHighest = SurfaceSubtle,
    outline = BorderLight,
    outlineVariant = BorderSubtle,
    error = AccentRose,
    onError = OnBrandLight,
    errorContainer = AccentRoseContainer,
    onErrorContainer = AccentRose
)

private val NovaIdeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NovaIdeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NovaIdeLightColorScheme,
        typography = Typography,
        shapes = NovaIdeShapes,
        content = content
    )
}
