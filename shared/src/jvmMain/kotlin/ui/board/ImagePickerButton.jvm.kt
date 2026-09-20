package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.*
import com.valoser.futacha.shared.desktop.*
import kotlinx.coroutines.*

@Composable
actual fun rememberAttachmentPickerLauncher(preference: AttachmentPickerPreference, mimeType: String, maxBytes: Long,
    onImageSelected: (ImageData) -> Unit, preferredFileManagerPackage: String?, onSelectionError: (String) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val selected by rememberUpdatedState(onImageSelected)
    val failed by rememberUpdatedState(onSelectionError)
    var job by remember { mutableStateOf<Job?>(null) }
    return { if (job?.isActive != true) job = scope.launch {
        try {
            val extensions = when (mimeType) {
                "image/*" -> setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "bmp")
                "video/*" -> setOf("mp4", "webm", "mov")
                else -> emptySet()
            }
            chooseDesktopFile("添付ファイルを選択", extensions)?.let { selected(readDesktopAttachment(it, maxBytes)) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { failed(failure.message ?: "ファイルを読み込めませんでした") }
    } }
}

@Composable
actual fun ImagePickerButton(onImageSelected: (ImageData) -> Unit, preference: AttachmentPickerPreference, preferredFileManagerPackage: String?) {
    var error by remember { mutableStateOf<String?>(null) }
    val launch = rememberAttachmentPickerLauncher(preference, "*/*", 64L * 1024 * 1024, onImageSelected, preferredFileManagerPackage, { error = it })
    TextButton(onClick = launch) { Text("画像・動画を選択") }
    error?.let { Text(it) }
}

@Composable
actual fun rememberDirectoryPickerLauncher(onDirectorySelected: (SaveLocation) -> Unit, preferredFileManagerPackage: String?): () -> Unit {
    val scope = rememberCoroutineScope()
    val selected by rememberUpdatedState(onDirectorySelected)
    return { scope.launch { pickDirectorySaveLocation()?.let(selected) } }
}
