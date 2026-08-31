package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.ImageData

/**
 * Platform-specific image picker button
 */
@Composable
expect fun ImagePickerButton(
    onImageSelected: (ImageData) -> Unit,
    preference: AttachmentPickerPreference = AttachmentPickerPreference.MEDIA,
    preferredFileManagerPackage: String? = null
)

/**
 * Platform-specific directory picker launcher
 */
@Composable
expect fun rememberDirectoryPickerLauncher(
    onDirectorySelected: (SaveLocation) -> Unit,
    preferredFileManagerPackage: String? = null
): () -> Unit
