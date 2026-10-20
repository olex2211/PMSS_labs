package com.example.lab1.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FlashlightColors = darkColorScheme(
    primary = Color(0xFFBCF36B),
    onPrimary = Color(0xFF182800),
    primaryContainer = Color(0xFF2D3F1C),
    onPrimaryContainer = Color(0xFFDDF8BB),
    secondary = Color(0xFF94CFF6),
    background = Color(0xFF10140F),
    surface = Color(0xFF1B2119),
    surfaceVariant = Color(0xFF2B3327),
    onBackground = Color(0xFFF1F4EB),
    onSurface = Color(0xFFF1F4EB),
    onSurfaceVariant = Color(0xFFAFB9A8),
    outline = Color(0xFF74806C),
)

@Composable
fun Lab1Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FlashlightColors, typography = Typography, content = content)
}
