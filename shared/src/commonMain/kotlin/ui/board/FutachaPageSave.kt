package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.service.*
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
    boardKey: String, boardName: String, boardUrl: String, title: String, onDismiss: () -> Unit) {
    val client = features.httpClient
    val fs = features.fileSystem
    if (client == null || fs == null) {
        AlertDialog(onDismissRequest = onDismiss, text = { Text("保存機能を利用できません") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } })
        return
    }
    val saver = remember(client, fs) { ThreadSaveService(client, fs) }
    val progress by saver.saveProgress.collectAsState()
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val destination = rememberCompatManualSaveDestinationLauncher(features.store, features.preferences) {
        message = it.message ?: "保存先を記録できませんでした"
    }
    if (job == null && message == null) AlertDialog(onDismissRequest = onDismiss, title = { Text("保存形式") }, text = {
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
                } catch (cancelled: CancellationException) { message = "保存を中止しました"; throw cancelled }
                catch (failure: Exception) { message = failure.message ?: "保存できませんでした" }
                finally { job = null }
            } }
        }) { Text(mode.label) } } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } })
    if (job != null) SaveProgressDialog(progress, onDismissRequest = {}, onCancelRequest = { job?.cancel() })
    message?.let { text -> AlertDialog(onDismissRequest = onDismiss, text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }) }
}
