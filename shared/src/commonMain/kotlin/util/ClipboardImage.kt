package com.valoser.futacha.shared.util

import androidx.compose.runtime.*
import com.valoser.futacha.shared.ui.compat.COMPAT_POST_PICKER_MAX_BYTES
import com.valoser.futacha.shared.ui.compat.detectCompatPostImageFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Reads only on an explicit paste action; never observes clipboard contents in the background. */
@Composable
internal expect fun rememberClipboardImageReader(): suspend () -> ImageData

internal fun clipboardImageData(bytes: ByteArray): ImageData {
    require(bytes.isNotEmpty() && bytes.size.toLong() <= COMPAT_POST_PICKER_MAX_BYTES) {
        "画像を読み込めませんでした。32MB以下の画像をコピーしてください"
    }
    val extension = detectCompatPostImageFormat(bytes).extension
        ?: error("コピーされた内容は対応する画像ではありません。画像そのものをコピーしてください")
    return ImageData(bytes, "clipboard.$extension")
}

internal class ClipboardImagePasteState {
    var busy by mutableStateOf(false)
        internal set
    var paste: () -> Unit = {}
        internal set
}

@Composable
internal fun rememberClipboardImagePaste(
    onImage: (ImageData) -> Unit,
    onError: (String) -> Unit
): ClipboardImagePasteState {
    val reader = rememberClipboardImageReader()
    val scope = rememberCoroutineScope()
    val currentImage by rememberUpdatedState(onImage)
    val currentError by rememberUpdatedState(onError)
    val state = remember { ClipboardImagePasteState() }
    state.paste = {
        if (!state.busy) {
            state.busy = true
            scope.launch {
                try { currentImage(reader()) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { currentError(failure.message ?: "画像を貼り付けられませんでした") }
                finally { state.busy = false }
            }
        }
    }
    return state
}
