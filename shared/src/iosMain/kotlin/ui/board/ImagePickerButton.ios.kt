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
import com.valoser.futacha.shared.util.pickDirectoryPath
import com.valoser.futacha.shared.util.pickDirectorySaveLocation
import com.valoser.futacha.shared.util.AttachmentPickerPreference
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
                AttachmentPickerPreference.DOCUMENT -> pickImageFromDocuments()
                AttachmentPickerPreference.ALWAYS_ASK -> {
                    // For ALWAYS_ASK, we use PHPicker as the primary choice
                    // In a full implementation, could show an action sheet here
                    pickImage()
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
    // iOS does not support preferred file manager package
    return {
        scope.launch {
            val picked = pickDirectorySaveLocation()
            picked?.let(onDirectorySelected)
        }
    }
}
