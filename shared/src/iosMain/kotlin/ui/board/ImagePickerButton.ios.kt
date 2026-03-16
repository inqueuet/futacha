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
import com.valoser.futacha.shared.util.pickImageFromDocuments
import com.valoser.futacha.shared.util.pickDirectoryPath
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

    return {
        scope.launch {
            val imageData = when (preference) {
                AttachmentPickerPreference.MEDIA -> pickImage()
                AttachmentPickerPreference.DOCUMENT -> pickImageFromDocuments(preferredFileManagerPackage)
                AttachmentPickerPreference.ALWAYS_ASK -> {
                    val presented = presentIosTwoOptionAlert(
                        title = "画像を選択",
                        message = "選択元を選んでください。",
                        primaryLabel = "フォトライブラリ",
                        secondaryLabel = "ファイル",
                        onPrimary = {
                            scope.launch {
                                pickImage()?.let(onImageSelected)
                            }
                        },
                        onSecondary = {
                            scope.launch {
                                pickImageFromDocuments()?.let(onImageSelected)
                            }
                        }
                    )
                    if (!presented) {
                        pickImage()
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
