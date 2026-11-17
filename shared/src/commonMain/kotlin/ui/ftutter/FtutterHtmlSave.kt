package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.service.*
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.board.FutachaSharedFeatures
import com.valoser.futacha.shared.ui.board.SaveProgressDialog
import com.valoser.futacha.shared.ui.board.isSaveProgressCompleted
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

internal data class FtutterHtmlOptions(val thumbnails: Boolean = true, val files: Boolean = true,
    val apu: Boolean = true, val zip: Boolean = true)
internal fun parseFtutterHtmlOptions(p: Map<String, String>) = FtutterHtmlOptions(
    p.ftutterBool(FtutterKeys.SAVE_HTML_THUMB, true), p.ftutterBool(FtutterKeys.SAVE_HTML_FILE, true),
    p.ftutterBool(FtutterKeys.SAVE_HTML_APU, true), p.ftutterBool(FtutterKeys.SAVE_HTML_ZIP, true))
internal fun ftutterHtmlPosts(page: ThreadPage, options: FtutterHtmlOptions) = page.posts.map {
    it.copy(thumbnailUrl = it.thumbnailUrl.takeIf { options.thumbnails }, imageUrl = it.imageUrl.takeIf { options.files })
}

/** Backend completion precedes indexing and ZIP creation; the whole job is still cancellable. */
internal fun ftutterHtmlActiveSaveProgress(progress: SaveProgress?, zipBuilding: Boolean): SaveProgress? = when {
    zipBuilding -> SaveProgress(SavePhase.FINALIZING, 0, 1, "ZIPを作成しています")
    isSaveProgressCompleted(progress) -> progress?.copy(current = 0, total = 1, currentItem = "保存結果をまとめています")
    else -> progress
}

/**
 * The result of a save (L13). Once the HTML and its index entry are saved, a ZIP that fails or is cancelled no
 * longer replaces that result: it is added below it with a reason a person can act on, never a raw exception text.
 */
internal fun ftutterHtmlSaveResultMessage(completed: String?, zipPath: String?, failure: Throwable?, cancelled: Boolean): String = when {
    completed == null && cancelled -> "保存を中止しました"
    completed == null -> failure?.message ?: "保存できませんでした"
    cancelled -> "$completed\nZIPの作成を中止しました（HTMLは保存済みです）"
    failure != null -> "$completed\nZIPは作成できませんでした：${ftutterZipFailureReason(failure)}"
    else -> completed + (zipPath?.let { "\nZIP: $it" } ?: "")
}

internal fun ftutterZipFailureReason(failure: Throwable): String = when (failure) {
    is ThreadSaveAlreadyRunningException -> "別の保存を実行中です"
    is com.valoser.futacha.shared.service.MediaSaveDestinationException -> failure.message ?: "保存先を確認してください"
    // Our own checks (a file too large or a name that cannot go in a ZIP) carry their reason.
    is IllegalArgumentException -> failure.message?.takeIf { it.isNotBlank() && it != "Failed requirement." } ?: "保存先を確認してください"
    else -> "保存先を確認してください"
}

/** Archive the files from this save only; each media file is read separately and the ZIP is streamed to storage. */
internal suspend fun ftutterZipSavedThread(fs: FileSystem, base: SaveLocation, id: String,
    mediaPaths: Set<String>): String = withContext(fs.saveBatchContext()) {
    val metadata = Json { ignoreUnknownKeys = true }.decodeFromString<SavedThreadMetadata>(
        fs.readString(base, "$id/metadata.json").getOrThrow())
    val paths = linkedSetOf("metadata.json").apply {
        metadata.rawHtmlPath?.let(::add)
        addAll(mediaPaths)
        metadata.posts.forEach { post -> addAll(listOfNotNull(post.localImagePath, post.localVideoPath, post.localThumbnailPath)) }
    }
    val zipPath = "$id.zip"
    var complete = false
    try {
        fs.writeByteStream(base, zipPath) { sink ->
            val zip = StreamingStoredZipWriter(sink)
            paths.forEach { path ->
                require(!path.startsWith('/') && path.split('/').none { it == ".." }) { "ZIPに含められないファイル名があります" }
                val size = fs.getFileSize(base, "$id/$path")
                require(size <= 64L * 1024 * 1024) { "ZIPに含めるファイルが大きすぎます" }
                val bytes = fs.readBytes(base, "$id/$path").getOrThrow()
                var at = 0
                zip.writeEntry(path, 64L * 1024 * 1024) { buffer ->
                    if (at == bytes.size) -1 else {
                        val count = minOf(buffer.size, bytes.size - at)
                        bytes.copyInto(buffer, 0, at, at + count); at += count; count
                    }
                }
            }
            zip.finish()
        }.getOrThrow()
        complete = true
    } finally {
        if (!complete) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            fs.delete(base, zipPath).onFailure {
                com.valoser.futacha.shared.util.Logger.w("FtutterSave", "Failed to remove an incomplete ZIP")
            }
        }
    }
    zipPath
}

@Composable
internal fun FtutterHtmlSaveDialog(features: FutachaSharedFeatures, page: ThreadPage,
    boardKey: String, boardName: String, boardUrl: String, title: String,
    preferences: Map<String, String>, save: (String, String) -> Unit, onDismiss: () -> Unit) {
    val client = features.httpClient
    val fs = features.fileSystem
    if (client == null || fs == null) {
        FutachaAppLockAwareWindow { AlertDialog(onDismissRequest = onDismiss,
            text = { Text("保存機能を利用できません") }, confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }) }
        return
    }
    val saver = remember(client, fs) { ThreadSaveService(client, fs) }
    // Compose's UI dispatcher can pause while the Activity is stopped. Storage,
    // notification progress and cancellation cleanup must continue without frames.
    val scope = rememberCoroutineScope { AppDispatchers.io }
    val zipBuilding = remember { MutableStateFlow(false) }
    val protectedProgress = remember(saver, scope) {
        combine(saver.saveProgress, zipBuilding, ::ftutterHtmlActiveSaveProgress)
            .stateIn(scope, SharingStarted.Eagerly, null)
    }
    val progress by protectedProgress.collectAsState()
    var options by remember { mutableStateOf(parseFtutterHtmlOptions(preferences)) }
    var job by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val destination = rememberCompatManualSaveDestinationLauncher(features.store, features.preferences) {
        message = "保存先を記録できませんでした"
    }
    if (job == null && message == null) FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss, title = { Text("スレを保存") }, text = {
            Column {
                listOf("サムネイルを含める" to options.thumbnails, "元ファイルを含める" to options.files,
                    "あぷ小を含める" to options.apu, "ZIPで保存" to options.zip).forEachIndexed { index, (label, checked) ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked, { value ->
                            options = when (index) { 0 -> options.copy(thumbnails = value); 1 -> options.copy(files = value)
                                2 -> options.copy(apu = value); else -> options.copy(zip = value) }
                            save(listOf(FtutterKeys.SAVE_HTML_THUMB, FtutterKeys.SAVE_HTML_FILE,
                                FtutterKeys.SAVE_HTML_APU, FtutterKeys.SAVE_HTML_ZIP)[index], value.toString())
                        }); Text(label)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = {
            destination { location ->
                scope.launch(start = CoroutineStart.LAZY) {
                    var completed: String? = null
                    var zipPath: String? = null
                    try {
                        runProtectedThreadSave(title, protectedProgress) {
                            var mediaPaths = emptySet<String>()
                            val saved = saver.saveThread(page.threadId, boardKey, boardName, boardUrl, title,
                                page.expiresAtLabel, ftutterHtmlPosts(page, options), page.isTruncated, page.truncationReason,
                                baseSaveLocation = location, baseDirectory = MANUAL_SAVE_DIRECTORY, writeMetadata = true,
                                rawHtmlOptions = RawHtmlSaveOptions(enable = true, stripExternalResources = true),
                                additionalMediaUrls = if (options.apu) page.posts.flatMap { ftutterApuImages(it.messageHtml) }.distinct() else emptyList(),
                                onMediaPathsSaved = { mediaPaths = it }).getOrThrow()
                            completed = completeCompatThreadSave(saved, fs, location)
                            if (options.zip) {
                                zipBuilding.value = true
                                zipPath = ftutterZipSavedThread(fs, location ?: SaveLocation.Path(MANUAL_SAVE_DIRECTORY),
                                    requireNotNull(saved.storageId), mediaPaths)
                            }
                        }
                        message = ftutterHtmlSaveResultMessage(completed, zipPath, null, cancelled = false)
                    } catch (cancelled: CancellationException) {
                        message = ftutterHtmlSaveResultMessage(completed, null, null, cancelled = true); throw cancelled
                    } catch (failure: Exception) { message = ftutterHtmlSaveResultMessage(completed, null, failure, cancelled = false) }
                    finally { zipBuilding.value = false; job = null }
                }.also { job = it; it.start() }
            }
        }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    ) }
    if (job != null) FutachaAppLockAwareWindow { SaveProgressDialog(progress, onDismissRequest = {}, onCancelRequest = { job?.cancel() }, trackAnalytics = false) }
    message?.let { FutachaAppLockAwareWindow { AlertDialog(onDismissRequest = onDismiss, text = { Text(it) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }) } }
}
