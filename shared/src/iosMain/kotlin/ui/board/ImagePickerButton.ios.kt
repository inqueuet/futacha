package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.normalizeIosPostingAttachment
import com.valoser.futacha.shared.util.pickIosImage
import com.valoser.futacha.shared.util.pickVideo
import com.valoser.futacha.shared.util.pickMediaFromDocuments
import com.valoser.futacha.shared.util.pickDirectorySaveLocation
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.awaitIosTwoOptionChoice
import kotlin.concurrent.AtomicReference
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

// Shared by image/video buttons: UIKit can only own one attachment flow at a time.
private val activeAttachmentPicker = AtomicReference<Any?>(null)

@Composable
actual fun rememberAttachmentPickerLauncher(
    preference: AttachmentPickerPreference,
    mimeType: String,
    maxBytes: Long,
    onImageSelected: (ImageData) -> Unit,
    preferredFileManagerPackage: String?,
    onSelectionError: (String) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnSelectionError by rememberUpdatedState(onSelectionError)
    val currentOnImageSelected by rememberUpdatedState(onImageSelected)
    val isVideo = mimeType.startsWith("video/", ignoreCase = true)
    val allowsAnyMedia = mimeType == "*/*" || mimeType.equals("application/octet-stream", ignoreCase = true)

    suspend fun pickLibraryMedia(): ImageData? {
        val video = if (allowsAnyMedia) {
            when (awaitIosTwoOptionChoice("メディアを選択", "フォトライブラリから種類を選んでください。", "写真", "動画")) {
                true -> false
                false -> true
                null -> return null
            }
        } else isVideo
        return if (video) pickVideo(maxBytes) else pickIosImage(maxBytes)
    }
    suspend fun pickDocumentMedia() = pickMediaFromDocuments(mimeType, preferredFileManagerPackage, maxBytes)

    return launch@{
        val session = Any()
        if (!activeAttachmentPicker.compareAndSet(null, session)) return@launch
        scope.launch {
            runIosAttachmentPickerCatching(currentOnSelectionError) {
                val selected = when (preference) {
                    AttachmentPickerPreference.MEDIA -> pickLibraryMedia()
                    AttachmentPickerPreference.DOCUMENT,
                    AttachmentPickerPreference.LEGACY_GET_CONTENT -> pickDocumentMedia()
                    AttachmentPickerPreference.COMPAT_REFERENCE_GET_CONTENT,
                    AttachmentPickerPreference.ALWAYS_ASK -> {
                        when (awaitIosTwoOptionChoice(
                            title = "添付ファイルを選択", message = "選択元を選んでください。",
                            primaryLabel = "フォトライブラリ", secondaryLabel = "ファイル"
                        )) {
                            true -> pickLibraryMedia()
                            false -> pickDocumentMedia()
                            null -> null
                        }
                    }
                }
                selected?.let { currentOnImageSelected(normalizeIosPostingAttachment(it, maxBytes)) }
            }
        }.invokeOnCompletion { activeAttachmentPicker.compareAndSet(session, null) }
    }
}

internal suspend fun runIosAttachmentPickerCatching(
    onSelectionError: (String) -> Unit,
    block: suspend () -> Unit
) {
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        onSelectionError(ATTACHMENT_LOAD_FAILURE_MESSAGE)
    }
}

@Composable
actual fun ImagePickerButton(
    onImageSelected: (ImageData) -> Unit,
    preference: AttachmentPickerPreference,
    preferredFileManagerPackage: String?
) {
    val launchPicker = rememberAttachmentPickerLauncher(
        preference = preference,
        onImageSelected = onImageSelected,
        preferredFileManagerPackage = preferredFileManagerPackage
    )

    Button(
        onClick = { launchPicker() }
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("画像を選択")
    }
}

@Composable
actual fun rememberDirectoryPickerLauncher(
    onDirectorySelected: (SaveLocation) -> Unit,
    preferredFileManagerPackage: String?
): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val picked = pickDirectorySaveLocation(preferredFileManagerPackage)
            picked?.let(onDirectorySelected)
        }
    }
}
