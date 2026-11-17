package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.normalizeIosPostingAttachment
import com.valoser.futacha.shared.util.pickIosImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val FUTABER_MEDIA_TAG = "FutaberPostMedia.ios"

/** iOS's PHPicker (no photo library permission), the same picker 「画像・動画を添付」 used for pictures. */
@Composable
internal actual fun rememberFutaberPhotoPicker(
    maxBytes: Long,
    onPicked: (ImageData) -> Unit,
    onFinished: () -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val finished by rememberUpdatedState(onFinished)
    val failed by rememberUpdatedState(onError)
    val busy = remember { mutableStateOf(false) }
    return launch@{
        if (busy.value) return@launch
        busy.value = true
        scope.launch {
            try {
                pickIosImage(maxBytes)?.let { picked(normalizeIosPostingAttachment(it, maxBytes)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_MEDIA_TAG, "Failed to read the picked photo", error)
                failed(FUTABER_PHOTO_PICKER_FAILED_MESSAGE)
            } finally {
                busy.value = false
                finished()
            }
        }
    }
}
