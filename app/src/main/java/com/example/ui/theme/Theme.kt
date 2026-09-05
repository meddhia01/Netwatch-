package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NetWatchColorScheme = darkColorScheme(
    primary = NetCyan,
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF004F5D),
    onPrimaryContainer = Color(0xFF9CF0FF),
    secondary = NetEmeraldRx,
    onSecondary = Color(0xFF003824),
    secondaryContainer = Color(0xFF005237),
    onSecondaryContainer = Color(0xFF86F8BF),
    tertiary = NetAmberTx,
    onTertiary = Color(0xFF472A00),
    tertiaryContainer = Color(0xFF653E00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = NetDarkBackground,
    onBackground = NetTextPrimary,
    surface = NetDarkSurface,
    onSurface = NetTextPrimary,
    surfaceVariant = NetDarkSurfaceElevated,
    onSurfaceVariant = NetTextSecondary,
    outline = NetBorder,
    outlineVariant = NetDarkSurfaceHighlight,
    error = NetCoral,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NetWatchColorScheme,
        typography = Typography,
        content = content
    )
}
