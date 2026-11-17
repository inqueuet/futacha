package com.valoser.futacha.shared.ui.futaber.mht

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.pickMhtFromDocuments
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal actual fun rememberFutaberMhtPickerLauncher(
    onSelected: (ImageData) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnSelected = rememberUpdatedState(onSelected)
    val currentOnError = rememberUpdatedState(onError)
    return remember {
        {
            scope.launch {
                // A chosen file that cannot be taken in (empty, too big, unreadable, too slow) comes back as a failure with its
                // reason; only leaving the chooser without a file is silent.
                try {
                    pickMhtFromDocuments()?.let { selected -> currentOnSelected.value(selected) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    currentOnError.value(error.message ?: "ファイルを読み込めませんでした")
                }
            }
        }
    }
}
