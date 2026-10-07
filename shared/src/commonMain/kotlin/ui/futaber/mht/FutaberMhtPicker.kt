package com.valoser.futacha.shared.ui.futaber.mht

import androidx.compose.runtime.Composable
import com.valoser.futacha.shared.util.ImageData

/**
 * Lets the person choose an MHT file in the files app and hands back its bytes (name in `fileName`).
 * The first value returned is what to call to open the chooser. Used by ふたばー風モード only.
 */
@Composable
internal expect fun rememberFutaberMhtPickerLauncher(
    onSelected: (ImageData) -> Unit,
    onError: (String) -> Unit
): () -> Unit
