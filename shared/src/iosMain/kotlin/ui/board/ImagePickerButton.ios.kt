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

@Composable
actual fun rememberAttachmentPickerLauncher(
    preference: AttachmentPickerPreference,
    mimeType: String,
    onImageSelected: (ImageData) -> Unit,
    preferredFileManagerPackage: String?
): () -> Unit {
    val scope = rememberCoroutineScope()
    val isVideo = mimeType.startsWith("video/", ignoreCase = true)

    return {
        scope.launch {
            val imageData = when (preference) {
                AttachmentPickerPreference.MEDIA -> if (isVideo) pickVideo() else pickImage()
                AttachmentPickerPreference.DOCUMENT -> pickMediaFromDocuments(
                    mimeType = mimeType,
                    preferredProviderIdentifier = preferredFileManagerPackage
                )
                AttachmentPickerPreference.ALWAYS_ASK -> {
                    val presented = presentIosTwoOptionAlert(
                        title = if (isVideo) "動画を選択" else "画像を選択",
                        message = "選択元を選んでください。",
                        primaryLabel = if (isVideo) "ビデオライブラリ" else "フォトライブラリ",
                        secondaryLabel = "ファイル",
                        onPrimary = {
                            scope.launch {
                                (if (isVideo) pickVideo() else pickImage())?.let(onImageSelected)
                            }
                        },
                        onSecondary = {
                            scope.launch {
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
            }
            imageData?.let(onImageSelected)
        }
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
