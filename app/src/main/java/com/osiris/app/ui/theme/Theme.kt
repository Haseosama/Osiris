package com.osiris.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val OsirisColorScheme = darkColorScheme(
    primary = OsirisAccent,
    onPrimary = OsirisBackground,
    secondary = OsirisAccentDim,
    background = OsirisBackground,
    onBackground = OsirisTextPrimary,
    surface = OsirisSurface,
    onSurface = OsirisTextPrimary,
    surfaceVariant = OsirisSurfaceVariant,
    onSurfaceVariant = OsirisTextSecondary,
    error = OsirisDanger,
)

@Composable
fun OsirisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OsirisColorScheme,
        typography = OsirisTypography,
        content = content,
    )
}
