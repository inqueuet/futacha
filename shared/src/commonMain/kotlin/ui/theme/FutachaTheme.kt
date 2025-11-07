package com.valoser.futacha.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006494),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7E7FF),
    onPrimaryContainer = Color(0xFF001E31),
    secondary = Color(0xFF48566A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E4FF),
    onSecondaryContainer = Color(0xFF021C33),
    tertiary = Color(0xFF6B5F97),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE9DDFF),
    onTertiaryContainer = Color(0xFF241049),
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF1A1C1E),
    background = Color(0xFFF7F9FF),
    onBackground = Color(0xFF1A1C1E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF88CEFF),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF004B71),
    onPrimaryContainer = Color(0xFFC7E7FF),
    secondary = Color(0xFFB6C8E8),
    onSecondary = Color(0xFF1F3149),
    secondaryContainer = Color(0xFF364860),
    onSecondaryContainer = Color(0xFFD3E4FF),
    tertiary = Color(0xFFCFBCFF),
    onTertiary = Color(0xFF3B255E),
    tertiaryContainer = Color(0xFF523E78),
    onTertiaryContainer = Color(0xFFE9DDFF),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE1E2E5),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE1E2E5)
)

@Composable
fun FutachaTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content
    )
}
