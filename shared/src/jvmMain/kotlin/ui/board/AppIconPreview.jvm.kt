package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.AppIconVariant

@Composable
internal actual fun AppIconVariantPreview(
    variant: AppIconVariant,
    modifier: Modifier
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = variant.label,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
