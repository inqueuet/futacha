package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.ImageData

@Composable
actual fun rememberAttachmentPickerLauncher(
    preference: AttachmentPickerPreference,
    mimeType: String,
    onImageSelected: (ImageData) -> Unit,
    preferredFileManagerPackage: String?
): () -> Unit = {}

@Composable
actual fun ImagePickerButton(
    onImageSelected: (ImageData) -> Unit,
    preference: AttachmentPickerPreference,
    preferredFileManagerPackage: String?
) = Unit

@Composable
actual fun rememberDirectoryPickerLauncher(
    onDirectorySelected: (SaveLocation) -> Unit,
    preferredFileManagerPackage: String?
): () -> Unit = {}
