package com.valoser.futacha.shared.ui.futaber

import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.valoser.futacha.shared.compat.rememberExperienceProfileActivityResultLauncher
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.readImageDataFromUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FUTABER_MEDIA_TAG = "FutaberPostMedia"

/** Android's Photo Picker (no permission; on a device without it, androidx falls back to the document picker). */
@Composable
internal actual fun rememberFutaberPhotoPicker(
    maxBytes: Long,
    onPicked: (ImageData) -> Unit,
    onFinished: () -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val finished by rememberUpdatedState(onFinished)
    val failed by rememberUpdatedState(onError)
    val launcher = rememberExperienceProfileActivityResultLauncher(ActivityResultContracts.PickVisualMedia()) { uri: Uri?, _ ->
        if (uri == null) {
            finished()
            return@rememberExperienceProfileActivityResultLauncher
        }
        scope.launch {
            try {
                val image = withContext(Dispatchers.IO) { readImageDataFromUri(context, uri, maxBytes) }
                if (image != null) picked(image) else failed(FUTABER_PHOTO_PICKER_FAILED_MESSAGE)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_MEDIA_TAG, "Failed to read the picked photo", error)
                failed(FUTABER_PHOTO_PICKER_FAILED_MESSAGE)
            } finally {
                finished()
            }
        }
    }
    return {
        try {
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (error: Exception) {
            Logger.e(FUTABER_MEDIA_TAG, "Failed to open the photo picker", error)
            failed(FUTABER_PHOTO_PICKER_FAILED_MESSAGE)
            finished()
        }
    }
}
