package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.AppIconVariant

@Composable
internal expect fun AppIconVariantPreview(
    variant: AppIconVariant,
    modifier: Modifier = Modifier
)
