package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
internal fun futachaDialogTextButtonColors(): ButtonColors {
    return ButtonDefaults.textButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurface,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
