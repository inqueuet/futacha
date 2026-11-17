package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import com.valoser.futacha.shared.util.ImageData

@Composable
internal expect fun rememberCompatFontPickerLauncher(
    onSelected: (ImageData) -> Unit,
    onError: (String) -> Unit
): () -> Unit
