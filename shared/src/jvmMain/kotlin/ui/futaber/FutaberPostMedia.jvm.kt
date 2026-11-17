package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.valoser.futacha.shared.ui.board.rememberAttachmentPickerLauncher
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.ImageData

/** The desktop has no photo library of its own: the file chooser, as 「ファイル」. */
@Composable
internal actual fun rememberFutaberPhotoPicker(
    maxBytes: Long,
    onPicked: (ImageData) -> Unit,
    onFinished: () -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val finished by rememberUpdatedState(onFinished)
    return rememberAttachmentPickerLauncher(
        preference = AttachmentPickerPreference.DOCUMENT,
        maxBytes = maxBytes,
        onImageSelected = { onPicked(it); finished() },
        onSelectionError = { onError(it); finished() }
    )
}
