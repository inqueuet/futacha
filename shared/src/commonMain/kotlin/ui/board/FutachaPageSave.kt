package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.service.*
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.compat.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class FutachaPageSaveMode(val label: String) {
    HTML("HTMLのみ"), THUMBNAILS("HTMLとサムネイル"), ALL("HTMLと全メディア")
}

internal fun ThreadPage.postsForFutachaSave(mode: FutachaPageSaveMode) = posts.map { post ->
    post.copy(imageUrl = post.imageUrl.takeIf { mode == FutachaPageSaveMode.ALL },
        thumbnailUrl = post.thumbnailUrl.takeIf { mode != FutachaPageSaveMode.HTML })
}

@Composable
internal fun FutachaPageSaveDialog(features: FutachaSharedFeatures, page: ThreadPage,
    boardKey: String, boardName: String, boardUrl: String, title: String, onDismiss: () -> Unit,
    /** When set, the dialog also offers MHT (one file); [fullImages] says whether the full-size pictures are taken. */
    onSaveMht: ((fullImages: Boolean) -> Unit)? = null,
    /** When true, also offers 「メディアのみ（ZIP）／（フォルダ）」 (ふたちゃ, and ふたばー風 with its extension on; other callers keep the old list). */
    offerMediaOnly: Boolean = false) {
    val client = features.httpClient
    val fs = features.fileSystem
    if (client == null || fs == null) {
        FutachaAppLockAwareWindow { AlertDialog(onDismissRequest = onDismiss, text = { Text("保存機能を利用できません") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }) }
        return
    }
    val saver = remember(client, fs) { ThreadSaveService(client, fs) }
    val progress by saver.saveProgress.collectAsState()
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val mediaOnlyProgressFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<com.valoser.futacha.shared.model.SaveProgress?>(null) }
    val mediaOnlyProgress by mediaOnlyProgressFlow.collectAsState()
    var mediaOnlyRunning by remember { mutableStateOf(false) }
    val destination = rememberCompatManualSaveDestinationLauncher(features.store, features.preferences) {
        message = it.message ?: "保存先を記録できませんでした"
    }
    if (job == null && message == null) FutachaAppLockAwareWindow { AlertDialog(onDismissRequest = onDismiss, title = { Text("保存形式") }, text = {
        Column { FutachaPageSaveMode.entries.forEach { mode -> TextButton(onClick = {
            destination { location -> job = scope.launch {
                try {
                    val result = runProtectedThreadSave(title, saver.saveProgress) {
                        saver.saveThread(page.threadId, boardKey, boardName, boardUrl, title, page.expiresAtLabel,
                            page.postsForFutachaSave(mode), isTruncated = page.isTruncated,
                            truncationReason = page.truncationReason, baseSaveLocation = location,
                            baseDirectory = MANUAL_SAVE_DIRECTORY, writeMetadata = true,
                            rawHtmlOptions = RawHtmlSaveOptions(enable = true, stripExternalResources = true),
                            limits = ThreadSaveLimits(maxMediaItems = if (mode == FutachaPageSaveMode.HTML) 0 else ThreadSaveService.DEFAULT_MAX_MEDIA_ITEMS),
                            storageOptions = buildManualThreadSaveStorageOptions(boardKey, page.threadId))
                    }
                    message = result.fold({ completeCompatThreadSave(it, fs, location) }, { it.message ?: "保存できませんでした" })
                } catch (cancelled: CancellationException) {
                    // The wording of としあき(仮) (L74).
                    message = com.valoser.futacha.shared.ui.compat.compatThreadSaveCancellationMessage(); throw cancelled
                }
                catch (failure: Exception) { message = failure.message ?: "保存できませんでした" }
                finally { job = null }
            } }
        }) { Text(mode.label) } }
            if (offerMediaOnly) FutachaMediaOnlySaveKind.entries.forEach { kind -> TextButton(onClick = {
                destination { location ->
                    mediaOnlyProgressFlow.value = null
                    mediaOnlyRunning = true
                    var savedSoFar = 0
                    job = scope.launch {
                        try {
                            message = runFutachaMediaOnlySave(kind, page, boardKey, boardName, title, location,
                                client, fs, mediaOnlyProgressFlow, onSaved = { savedSoFar = it })
                        } catch (cancelled: CancellationException) {
                            // As としあき(仮) says it: what was saved before the cancel stays, and is counted (L74).
                            message = com.valoser.futacha.shared.ui.compat.compatThreadSaveCancellationMessage(savedSoFar)
                            throw cancelled
                        }
                        catch (failure: Exception) { message = failure.message ?: "保存できませんでした" }
                        finally { job = null; mediaOnlyRunning = false }
                    }
                }
            }) { Text(kind.label) } }
            if (onSaveMht != null) {
                TextButton(onClick = { onSaveMht(false) }) { Text("MHT（1ファイル・サムネイル）") }
                TextButton(onClick = { onSaveMht(true) }) { Text("MHT（1ファイル・全画像）") }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }) }
    if (job != null) FutachaAppLockAwareWindow {
        SaveProgressDialog(if (mediaOnlyRunning) mediaOnlyProgress else progress, onDismissRequest = {}, onCancelRequest = { job?.cancel() })
    }
    message?.let { text -> FutachaAppLockAwareWindow { AlertDialog(onDismissRequest = onDismiss, text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }) } }
}
