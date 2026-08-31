package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.valoser.futacha.shared.model.ThemePalette
import com.valoser.futacha.shared.ui.theme.LocalFutachaThemePalette

@Composable
internal fun futachaDialogTextButtonColors(): ButtonColors {
    val contentColor = if (LocalFutachaThemePalette.current == ThemePalette.FutabaClassic) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.primary
    }
    return ButtonDefaults.textButtonColors(
        contentColor = contentColor,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    )
}
