package com.valoser.futacha.shared.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.ThemeMode
import com.valoser.futacha.shared.model.ThemePalette

private val CurrentLightColors = lightColorScheme(
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

private val CurrentDarkColors = darkColorScheme(
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

private val ClassicLightColors = lightColorScheme(
    primary = Color(0xFF800000),
    onPrimary = Color(0xFFFFF8EF),
    primaryContainer = Color(0xFFEEAA88),
    onPrimaryContainer = Color(0xFF5E0000),
    secondary = Color(0xFF117743),
    onSecondary = Color(0xFFFFF8EF),
    secondaryContainer = Color(0xFFE7F2E4),
    onSecondaryContainer = Color(0xFF0B4D2D),
    tertiary = Color(0xFFCC1105),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE9CCCC),
    onTertiaryContainer = Color(0xFF800000),
    background = Color(0xFFFFFFEE),
    onBackground = Color(0xFF800000),
    surface = Color(0xFFF8EBDD),
    onSurface = Color(0xFF800000),
    surfaceVariant = Color(0xFFE9CCCC),
    onSurfaceVariant = Color(0xFF7C4A4A),
    outline = Color(0xFF9A6767)
)

private val ClassicDarkColors = darkColorScheme(
    primary = Color(0xFFE5C5B8),
    onPrimary = Color(0xFF3D1515),
    primaryContainer = Color(0xFF8A4D4D),
    onPrimaryContainer = Color(0xFFFFE6D8),
    secondary = Color(0xFF7DD6A3),
    onSecondary = Color(0xFF08341F),
    secondaryContainer = Color(0xFF184E33),
    onSecondaryContainer = Color(0xFFB8F0C9),
    tertiary = Color(0xFFFF8A70),
    onTertiary = Color(0xFF55120B),
    tertiaryContainer = Color(0xFF7A241B),
    onTertiaryContainer = Color(0xFFFFDAD4),
    background = Color(0xFF201614),
    onBackground = Color(0xFFF4DDD3),
    surface = Color(0xFF281B19),
    onSurface = Color(0xFFF4DDD3),
    surfaceVariant = Color(0xFF4A3430),
    onSurfaceVariant = Color(0xFFD8BDB7),
    outline = Color(0xFFA98B85)
)

private val FutabaBlackLightColors = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color(0xFFF9F4EA),
    primaryContainer = Color(0xFF2C2C2C),
    onPrimaryContainer = Color(0xFFF9F4EA),
    secondary = Color(0xFF117743),
    onSecondary = Color(0xFFFFFFEE),
    secondaryContainer = Color(0xFFE2F1E5),
    onSecondaryContainer = Color(0xFF0B4D2D),
    tertiary = Color(0xFFB51D12),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF4D3CF),
    onTertiaryContainer = Color(0xFF6F110B),
    background = Color(0xFFFFFFEE),
    onBackground = Color(0xFF241816),
    surface = Color(0xFFFFF7E8),
    onSurface = Color(0xFF241816),
    surfaceVariant = Color(0xFFE9CCCC),
    onSurfaceVariant = Color(0xFF5E3A37),
    outline = Color(0xFF8B6B65),
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val FutabaBlackDarkColors = darkColorScheme(
    primary = Color(0xFF050505),
    onPrimary = Color(0xFFF4EFE6),
    primaryContainer = Color(0xFF202020),
    onPrimaryContainer = Color(0xFFF4EFE6),
    secondary = Color(0xFF70D49B),
    onSecondary = Color(0xFF0A321E),
    secondaryContainer = Color(0xFF143F2A),
    onSecondaryContainer = Color(0xFFB9F2CA),
    tertiary = Color(0xFFFF9B8A),
    onTertiary = Color(0xFF5A120C),
    tertiaryContainer = Color(0xFF6E2118),
    onTertiaryContainer = Color(0xFFFFDAD4),
    background = Color(0xFF17110F),
    onBackground = Color(0xFFEFE2D8),
    surface = Color(0xFF211816),
    onSurface = Color(0xFFEFE2D8),
    surfaceVariant = Color(0xFF3D2C29),
    onSurfaceVariant = Color(0xFFD8C3BC),
    outline = Color(0xFF9E817A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val MidnightLightColors = lightColorScheme(
    primary = Color(0xFF0E5F73),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBDEAF4),
    onPrimaryContainer = Color(0xFF002B35),
    secondary = Color(0xFF005E5E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB7ECE9),
    onSecondaryContainer = Color(0xFF00201F),
    tertiary = Color(0xFF3C5C88),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6E3FF),
    onTertiaryContainer = Color(0xFF001B3E),
    background = Color(0xFFF2F6FA),
    onBackground = Color(0xFF101A21),
    surface = Color(0xFFF8FBFF),
    onSurface = Color(0xFF101A21),
    surfaceVariant = Color(0xFFDDE4EB),
    onSurfaceVariant = Color(0xFF40484F),
    outline = Color(0xFF70787F)
)

private val MidnightDarkColors = darkColorScheme(
    primary = Color(0xFF7AD3E7),
    onPrimary = Color(0xFF003642),
    primaryContainer = Color(0xFF004E5F),
    onPrimaryContainer = Color(0xFFBDEAF4),
    secondary = Color(0xFF7AD4CF),
    onSecondary = Color(0xFF003736),
    secondaryContainer = Color(0xFF004F4D),
    onSecondaryContainer = Color(0xFFB7ECE9),
    tertiary = Color(0xFFACC7FF),
    onTertiary = Color(0xFF032C5D),
    tertiaryContainer = Color(0xFF234476),
    onTertiaryContainer = Color(0xFFD6E3FF),
    background = Color(0xFF081019),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0D1620),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF25313C),
    onSurfaceVariant = Color(0xFFBEC8D2),
    outline = Color(0xFF88939E)
)

internal fun resolveFutachaColorScheme(
    useDarkTheme: Boolean,
    palette: ThemePalette
): ColorScheme {
    return when (palette) {
        ThemePalette.Current -> if (useDarkTheme) CurrentDarkColors else CurrentLightColors
        ThemePalette.FutabaClassic -> if (useDarkTheme) ClassicDarkColors else ClassicLightColors
        ThemePalette.FutabaBlack -> if (useDarkTheme) FutabaBlackDarkColors else FutabaBlackLightColors
        ThemePalette.Midnight -> if (useDarkTheme) MidnightDarkColors else MidnightLightColors
    }
}

/** Resolve the explicit light/dark choice before selecting a palette branch. */
internal fun resolveFutachaDarkMode(themeMode: ThemeMode, systemDarkTheme: Boolean): Boolean =
    when (themeMode) {
        ThemeMode.System -> systemDarkTheme
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

data class FutachaChromeColors(
    val systemBar: Color,
    val topBar: Color,
    val bottomBar: Color,
    val onBar: Color,
    val useDarkSystemBarIcons: Boolean
)

internal fun resolveFutachaChromeColors(
    colors: ColorScheme,
    useDarkTheme: Boolean,
    palette: ThemePalette
): FutachaChromeColors {
    val barColor = when (palette) {
        ThemePalette.FutabaBlack -> Color(0xFF050505)
        else -> colors.primary
    }
    return FutachaChromeColors(
        systemBar = barColor,
        topBar = barColor,
        bottomBar = barColor,
        onBar = when (palette) {
            ThemePalette.FutabaBlack -> Color(0xFFF4EFE6)
            else -> colors.onPrimary
        },
        useDarkSystemBarIcons = palette != ThemePalette.FutabaBlack && !useDarkTheme
    )
}

val LocalFutachaThemePalette = staticCompositionLocalOf { ThemePalette.FutabaClassic }
val LocalFutachaChromeColors = staticCompositionLocalOf {
    FutachaChromeColors(
        systemBar = Color(0xFF800000),
        topBar = Color(0xFF800000),
        bottomBar = Color(0xFF800000),
        onBar = Color.White,
        useDarkSystemBarIcons = false
    )
}

@Composable
internal expect fun ApplyFutachaSystemBars(
    systemBarColor: Color,
    useDarkIcons: Boolean
)

@Composable
fun FutachaTheme(
    themeMode: ThemeMode = ThemeMode.System,
    themePalette: ThemePalette = ThemePalette.FutabaClassic,
    content: @Composable () -> Unit
) {
    val systemDarkTheme = isSystemInDarkTheme()
    val useDarkTheme = resolveFutachaDarkMode(themeMode, systemDarkTheme)
    val colors = remember(useDarkTheme, themePalette) {
        resolveFutachaColorScheme(
            useDarkTheme = useDarkTheme,
            palette = themePalette
        )
    }
    val chromeColors = remember(colors, useDarkTheme, themePalette) {
        resolveFutachaChromeColors(
            colors = colors,
            useDarkTheme = useDarkTheme,
            palette = themePalette
        )
    }
    ApplyFutachaSystemBars(
        systemBarColor = chromeColors.systemBar,
        useDarkIcons = chromeColors.useDarkSystemBarIcons
    )
    CompositionLocalProvider(
        LocalFutachaThemePalette provides themePalette,
        LocalFutachaChromeColors provides chromeColors
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = MaterialTheme.typography,
            content = {
                Box(modifier = Modifier.fillMaxSize()) {
                    content()
                    // MainActivity is edge-to-edge, so Android makes the system
                    // bars transparent.  The reference APK paints these pixels
                    // as part of the themed chrome; drawing them here keeps the
                    // icon contrast and the bottom gesture area stable on API 29+.
                    FutachaSystemBarOverlays(chromeColors.systemBar)
                }
            }
        )
    }
}

@Composable
private fun FutachaSystemBarOverlays(systemBarColor: Color) {
    Box(modifier = Modifier.fillMaxSize()) {
        Spacer(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(systemBarColor)
        )
        Spacer(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .background(systemBarColor)
        )
    }
}
