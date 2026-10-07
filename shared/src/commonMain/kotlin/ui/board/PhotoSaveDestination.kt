package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.*
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Column
import com.valoser.futacha.shared.util.*
import com.valoser.futacha.shared.service.SingleMediaSaveService
import com.valoser.futacha.shared.service.SavedMediaType
import com.valoser.futacha.shared.model.SaveLocation
import io.ktor.client.HttpClient
import kotlinx.coroutines.*
import kotlin.uuid.Uuid

/** A shared destination choice for both modes; Files remains the normal Android action. */
@Composable
internal fun rememberPhotoSaveDestination(
    httpClient: HttpClient?, fileSystem: FileSystem?,
    onMessage: (String) -> Unit
): (String, () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    val message by rememberUpdatedState(onMessage)
    var pending by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var saving by remember { mutableStateOf(false) }
    pending?.let { target -> FutachaAppLockAwareWindow {
        AlertDialog(
            onDismissRequest = { if (!saving) pending = null },
            title = { Text("保存先") },
            text = { Column {
                TextButton(enabled = !saving, onClick = { pending = null; target.second() }) { Text("ファイルに保存") }
                TextButton(enabled = !saving && httpClient != null && fileSystem != null, onClick = {
                    saving = true
                    scope.launch {
                        try {
                            saveMediaToPhotos(target.first, checkNotNull(httpClient), checkNotNull(fileSystem))
                            message("写真に保存しました")
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { message(e.message ?: "写真に保存できませんでした") }
                        finally { saving = false; pending = null }
                    }
                }) { Text(if (saving) "保存中…" else "写真に保存") }
            } },
            confirmButton = { TextButton(enabled = !saving, onClick = { pending = null }) { Text("閉じる") } }
        )
    } }
    return { url, files ->
        if (!photoLibrarySaveAvailable()) files()
        else if (!saving && pending == null) pending = url to files
    }
}

internal suspend fun saveMediaToPhotos(
    url: String, client: HttpClient, fs: FileSystem,
    requestPermission: suspend () -> Unit = ::requestPhotoLibraryAddPermission,
    saveFile: suspend (String, Boolean) -> Unit = ::saveFileToPhotoLibrary
) {
    requestPermission()
    val temporaryDirectory = "photo_export/${Uuid.random()}"
    try {
        val saved = SingleMediaSaveService(client, fs).saveMedia(
            url, "photos", "export", baseDirectory = temporaryDirectory,
            outputFileNameOverride = if (com.valoser.futacha.shared.ui.image.isTutorialImageUrl(url)) "tutorial.png" else null
        ).getOrThrow()
        val path = fs.resolveSavedFile(SaveLocation.Path(temporaryDirectory), saved.relativePath).getOrThrow()
        // Photos reads the file asynchronously. Keep it until the completion callback, even if the screen closes.
        withContext(NonCancellable) { saveFile(path, saved.mediaType == SavedMediaType.VIDEO) }
    } finally {
        withContext(NonCancellable + AppDispatchers.io) { fs.deleteRecursively(temporaryDirectory) }
    }
}
