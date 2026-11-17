package com.valoser.futacha.shared.ui.futaber.mht

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.valoser.futacha.shared.desktop.chooseDesktopFile
import com.valoser.futacha.shared.desktop.readDesktopAttachment
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal actual fun rememberFutaberMhtPickerLauncher(
    onSelected: (ImageData) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val selected by rememberUpdatedState(onSelected)
    val failed by rememberUpdatedState(onError)
    return {
        scope.launch {
            try {
                chooseDesktopFile("MHTファイルを選択", setOf("mht", "mhtml"))
                    ?.let { selected(readDesktopAttachment(it, FUTABER_MHT_MAX_READ_BYTES)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OutOfMemoryError) {
                failed("ファイルが大きすぎるか、メモリが足りないため読み込めませんでした")
            } catch (error: Exception) {
                failed(error.message ?: "ファイルを読み込めませんでした")
            }
        }
    }
}
