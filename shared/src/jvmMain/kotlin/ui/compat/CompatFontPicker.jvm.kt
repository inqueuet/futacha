package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import com.valoser.futacha.shared.util.ImageData

@Composable
internal actual fun rememberCompatFontPickerLauncher(
    onSelected: (ImageData) -> Unit,
    onError: (String) -> Unit
): () -> Unit = { onError("この互換モードではカスタムフォントを選択できません") }
