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
import com.valoser.futacha.shared.util.pickImage
import com.valoser.futacha.shared.util.pickVideo
import com.valoser.futacha.shared.util.pickMediaFromDocuments
import com.valoser.futacha.shared.util.pickDirectorySaveLocation
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.presentIosTwoOptionAlert
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

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
    val isVideo = mimeType.startsWith("video/", ignoreCase = true)
    val allowsAnyMedia = mimeType == "*/*" || mimeType.equals("application/octet-stream", ignoreCase = true)

    fun launchPicker(block: suspend () -> Unit) {
        scope.launch {
            runIosAttachmentPickerCatching(currentOnSelectionError, block)
        }
    }

    fun pickLibraryMedia() {
        launchPicker {
            // PHPicker's image/video filters are separate on the deployed iOS
            // range. Letting the user choose here preserves the Android
            // `*/*` result (either a photo or a video) without misclassifying
            // every wildcard attachment as an image.
            if (allowsAnyMedia) {
                val presented = presentIosTwoOptionAlert(
                    title = "メディアを選択",
                    message = "フォトライブラリから種類を選んでください。",
                    primaryLabel = "写真",
                    secondaryLabel = "動画",
                    onPrimary = { launchPicker { pickImage()?.let(onImageSelected) } },
                    onSecondary = { launchPicker { pickVideo()?.let(onImageSelected) } }
                )
                if (!presented) pickImage()?.let(onImageSelected)
            } else {
                (if (isVideo) pickVideo() else pickImage())?.let(onImageSelected)
            }
        }
    }

    return {
        launchPicker {
            val imageData = when (preference) {
                AttachmentPickerPreference.MEDIA -> {
                    if (allowsAnyMedia) {
                        // This call owns its nested picker coroutine.
                        pickLibraryMedia()
                        null
                    } else if (isVideo) {
                        pickVideo()
                    } else {
                        pickImage()
                    }
                }
                AttachmentPickerPreference.DOCUMENT -> pickMediaFromDocuments(
                    mimeType = mimeType,
                    preferredProviderIdentifier = preferredFileManagerPackage
                )
                AttachmentPickerPreference.COMPAT_REFERENCE_GET_CONTENT -> {
                    // Android's ACTION_GET_CONTENT exposes both media and
                    // document providers. Mapping it to Files alone on iOS
                    // made photos appear impossible to attach in compatibility
                    // mode. Preserve the reference semantics with an explicit
                    // source chooser on a platform that has no equivalent
                    // combined system intent.
                    val presented = presentIosTwoOptionAlert(
                        title = "添付ファイルを選択",
                        message = "選択元を選んでください。",
                        primaryLabel = "フォトライブラリ",
                        secondaryLabel = "ファイル",
                        onPrimary = { pickLibraryMedia() },
                        onSecondary = {
                            launchPicker {
                                pickMediaFromDocuments(
                                    mimeType = mimeType,
                                    preferredProviderIdentifier = preferredFileManagerPackage
                                )?.let(onImageSelected)
                            }
                        }
                    )
                    if (!presented) {
                        pickMediaFromDocuments(
                            mimeType = mimeType,
                            preferredProviderIdentifier = preferredFileManagerPackage
                        )
                    } else {
                        null
                    }
                }
                AttachmentPickerPreference.ALWAYS_ASK -> {
                    val presented = presentIosTwoOptionAlert(
                        title = if (isVideo) "動画を選択" else "画像を選択",
                        message = "選択元を選んでください。",
                        primaryLabel = if (isVideo) "ビデオライブラリ" else "フォトライブラリ",
                        secondaryLabel = "ファイル",
                        onPrimary = {
                            pickLibraryMedia()
                        },
                        onSecondary = {
                            launchPicker {
                                pickMediaFromDocuments(
                                    mimeType = mimeType,
                                    preferredProviderIdentifier = preferredFileManagerPackage
                                )?.let(onImageSelected)
                            }
                        }
                    )
                    if (!presented) {
                        if (isVideo) pickVideo() else pickImage()
                    } else {
                        null
                    }
                }
                AttachmentPickerPreference.LEGACY_GET_CONTENT -> pickMediaFromDocuments(
                    mimeType = mimeType,
                    preferredProviderIdentifier = preferredFileManagerPackage
                )
            }
            imageData?.let(onImageSelected)
        }
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
