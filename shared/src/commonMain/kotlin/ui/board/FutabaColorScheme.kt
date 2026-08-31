package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.valoser.futacha.shared.model.ThemePalette

internal val FutabaBackground = Color(0xFFFFFFEE)
internal val FutabaSurface = Color(0xFFF0E0D6)
internal val FutabaSurfaceVariant = Color(0xFFE9CCCC)
internal val FutabaLabelSurface = Color(0xFFEEAA88)
internal val FutabaText = Color(0xFF800000)
internal val FutabaTextDim = Color(0xFF800000)
internal val FutabaAccentRed = Color(0xFFCC1105)
internal val FutabaNameGreen = Color(0xFF117743)
internal val FutabaQuoteGreen = Color(0xFF789922)

@Immutable
internal data class FutabaThreadColors(
    val footerText: Color,
    val accent: Color,
    val author: Color,
    val quote: Color,
    val link: Color
)

internal val LocalFutabaThreadColors = staticCompositionLocalOf {
    FutabaThreadColors(
        footerText = FutabaTextDim,
        accent = FutabaAccentRed,
        author = FutabaNameGreen,
        quote = FutabaQuoteGreen,
        link = FutabaText
    )
}

internal fun resolveFutabaThreadColors(
    palette: ThemePalette,
    base: ColorScheme
): FutabaThreadColors {
    val isDark = base.background.luminance() < 0.5f
    return if (palette == ThemePalette.FutabaClassic && !isDark) {
        FutabaThreadColors(
            footerText = FutabaTextDim,
            accent = FutabaAccentRed,
            author = FutabaNameGreen,
            quote = FutabaQuoteGreen,
            link = FutabaText
        )
    } else {
        FutabaThreadColors(
            footerText = base.onSurfaceVariant,
            accent = base.tertiary,
            author = base.secondary,
            quote = base.secondary,
            // FutabaBlack deliberately keeps dark chrome in both modes. Its
            // dark-mode primary is therefore near-black and cannot be reused
            // as body-link text on the thread surface.
            link = if (isDark) base.onPrimaryContainer else base.primary
        )
    }
}

internal fun resolveFutabaThreadColorScheme(
    palette: ThemePalette,
    base: ColorScheme
): ColorScheme {
    val isDark = base.background.luminance() < 0.5f
    return if (palette != ThemePalette.FutabaClassic || isDark) {
        base
    } else {
        base.copy(
            primary = FutabaSurface,
            onPrimary = FutabaText,
            primaryContainer = FutabaLabelSurface,
            onPrimaryContainer = FutabaText,
            inversePrimary = FutabaAccentRed,
            secondary = FutabaNameGreen,
            onSecondary = FutabaBackground,
            secondaryContainer = FutabaSurface,
            onSecondaryContainer = FutabaNameGreen,
            tertiary = FutabaAccentRed,
            onTertiary = FutabaBackground,
            tertiaryContainer = FutabaSurfaceVariant,
            onTertiaryContainer = FutabaText,
            background = FutabaBackground,
            onBackground = FutabaText,
            surface = FutabaSurface,
            onSurface = FutabaText,
            surfaceVariant = FutabaSurfaceVariant,
            onSurfaceVariant = FutabaTextDim,
            surfaceTint = FutabaSurface,
            error = FutabaAccentRed,
            onError = FutabaBackground,
            errorContainer = FutabaSurfaceVariant,
            onErrorContainer = FutabaText,
            outline = FutabaText,
            outlineVariant = FutabaTextDim
        )
    }
}

@Composable
internal fun rememberFutabaThreadColorScheme(
    palette: ThemePalette,
    base: ColorScheme = MaterialTheme.colorScheme
): ColorScheme {
    return remember(base, palette) {
        resolveFutabaThreadColorScheme(palette, base)
    }
}

@Composable
internal fun rememberFutabaThreadColors(
    palette: ThemePalette,
    base: ColorScheme = MaterialTheme.colorScheme
): FutabaThreadColors = remember(base, palette) {
    resolveFutabaThreadColors(palette, base)
}
