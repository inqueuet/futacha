package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavePhase
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.service.MediaSaveDestinationException
import com.valoser.futacha.shared.service.SingleMediaSaveService
import com.valoser.futacha.shared.service.ThreadSaveAlreadyRunningException
import com.valoser.futacha.shared.service.runProtectedThreadSave
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** The result of the gallery's selection save. [error] explains why the save stopped early. */
internal data class FtutterBatchSaveResult(val saved: Int, val failed: Int, val error: String? = null)

internal fun ftutterBatchSaveMessage(result: FtutterBatchSaveResult): String = when {
    result.error != null -> if (result.saved > 0) "${result.error}（${result.saved}個は保存しました）" else result.error
    result.failed > 0 -> "${result.saved}個のファイルを保存しました（${result.failed}個は保存できませんでした）"
    else -> "${result.saved}個のファイルを保存しました"
}

/**
 * Saves the gallery selection into the destination's root, one file after another, with the other modes'
 * safeguards (L140): one save at a time with the platform protection (foreground service / background task)
 * and its cancel action, one shared SAF index, and a stop with the reason when the destination is lost.
 */
internal suspend fun ftutterSaveMediaBatch(
    service: SingleMediaSaveService,
    fileSystem: FileSystem,
    urls: List<String>,
    boardKey: String,
    threadKey: String,
    location: SaveLocation?,
    title: String,
    onProgress: (done: Int) -> Unit
): FtutterBatchSaveResult {
    var saved = 0
    var failed = 0
    val progress = MutableStateFlow<SaveProgress?>(SaveProgress(SavePhase.DOWNLOADING, 0, urls.size, title))
    return try {
        runProtectedThreadSave(title, progress) {
            withContext(fileSystem.saveBatchContext()) {
                urls.forEachIndexed { i, url ->
                    service.saveMedia(url, boardKey, threadKey, baseSaveLocation = location,
                        storageDirectoryOverride = "", useTypeSubdirectory = false).fold(
                        onSuccess = { saved++ },
                        onFailure = { if (it is MediaSaveDestinationException) throw it; failed++ }
                    )
                    progress.value = SaveProgress(SavePhase.DOWNLOADING, i + 1, urls.size, title)
                    onProgress(i + 1)
                }
            }
        }
        FtutterBatchSaveResult(saved, failed)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (lost: MediaSaveDestinationException) {
        FtutterBatchSaveResult(saved, urls.size - saved, lost.message)
    } catch (running: ThreadSaveAlreadyRunningException) {
        FtutterBatchSaveResult(0, urls.size, running.message)
    } catch (failure: Exception) {
        FtutterBatchSaveResult(saved, urls.size - saved, "保存できませんでした")
    }
}
