package com.valoser.futacha.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF008D63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2E7),
    onPrimaryContainer = Color(0xFF004229),
    secondary = Color(0xFF3F5F4B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE7D9),
    onSecondaryContainer = Color(0xFF1A2D22),
    tertiary = Color(0xFF51606D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5E4F4),
    onTertiaryContainer = Color(0xFF111C26),
    surface = Color(0xFFFEFEFA),
    onSurface = Color(0xFF1E1F1B),
    background = Color(0xFFF4F2EA),
    onBackground = Color(0xFF1E1F1B),
    surfaceVariant = Color(0xFFE5E2D6),
    onSurfaceVariant = Color(0xFF4B4A42),
    outline = Color(0xFF797869)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6DD4B1),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005236),
    onPrimaryContainer = Color(0xFFB0FCD2),
    secondary = Color(0xFFB2CCBA),
    onSecondary = Color(0xFF1F3527),
    secondaryContainer = Color(0xFF364B3C),
    onSecondaryContainer = Color(0xFFD0EADB),
    tertiary = Color(0xFFBAC8D5),
    onTertiary = Color(0xFF24323F),
    tertiaryContainer = Color(0xFF3A4856),
    onTertiaryContainer = Color(0xFFD6E6F5),
    surface = Color(0xFF151712),
    onSurface = Color(0xFFE4E2DA),
    background = Color(0xFF151712),
    onBackground = Color(0xFFE4E2DA),
    surfaceVariant = Color(0xFF42463D),
    onSurfaceVariant = Color(0xFFC6C3B6),
    outline = Color(0xFF929080)
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
