package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SkmOrange,
    onPrimary = SkmWhite,
    primaryContainer = SkmOrangeDark,
    onPrimaryContainer = SkmWhite,
    secondary = SkmInfo,
    onSecondary = SkmWhite,
    background = SkmCanvasDark,
    onBackground = SkmWhite,
    surface = SkmSurfaceDark,
    onSurface = SkmWhite,
    surfaceVariant = SkmSurfaceMutedDark,
    onSurfaceVariant = SkmBorder,
    outline = SkmBorderDark,
    error = SkmDanger,
    onError = SkmWhite
)

private val LightColorScheme = lightColorScheme(
    primary = SkmOrange,
    onPrimary = SkmWhite,
    primaryContainer = SkmOrangeLight,
    onPrimaryContainer = SkmGraphite,
    secondary = SkmGraphite,
    onSecondary = SkmWhite,
    secondaryContainer = SkmSurfaceMuted,
    onSecondaryContainer = SkmTextPrimary,
    background = SkmCanvas,
    onBackground = SkmTextPrimary,
    surface = SkmWhite,
    onSurface = SkmTextPrimary,
    surfaceVariant = SkmSurfaceMuted,
    onSurfaceVariant = SkmTextSecondary,
    outline = SkmBorder,
    error = SkmDanger,
    onError = SkmWhite,
    errorContainer = SkmDangerSurface,
    onErrorContainer = SkmDanger
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
