package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

internal val FutabaBackground = Color(0xFFFFFFEE)
internal val FutabaSurface = Color(0xFFF0E0D6)
internal val FutabaSurfaceVariant = Color(0xFFE9CCCC)
internal val FutabaLabelSurface = Color(0xFFEEAA88)
internal val FutabaText = Color(0xFF800000)
internal val FutabaTextDim = Color(0xFF800000)
internal val FutabaAccentRed = Color(0xFFCC1105)
internal val FutabaNameGreen = Color(0xFF117743)
internal val FutabaQuoteGreen = Color(0xFF789922)

@Composable
internal fun rememberFutabaThreadColorScheme(
    base: ColorScheme = MaterialTheme.colorScheme
): ColorScheme {
    return remember(base) {
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
