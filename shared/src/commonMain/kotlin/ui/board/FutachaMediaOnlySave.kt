package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.toCompatPostSnapshot
import com.valoser.futacha.shared.model.SavePhase
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.service.ImageZipSaveService
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.service.SingleMediaSaveService
import com.valoser.futacha.shared.service.buildCompatManualImageFolderName
import com.valoser.futacha.shared.service.runProtectedThreadSave
import com.valoser.futacha.shared.ui.compat.compatBatchMediaUrls
import com.valoser.futacha.shared.ui.compat.compatBatchOutputFileNames
import com.valoser.futacha.shared.ui.compat.manualSaveDestinationLabel
import com.valoser.futacha.shared.ui.compat.toCompatUserMessage
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow

/** 「メディアのみ」の保存形式（としあき(仮)の「画像をZIPで保存」「画像をフォルダに保存」と同じ処理）。 */
internal enum class FutachaMediaOnlySaveKind(val label: String) {
    ZIP("メディアのみ（ZIP）"), FOLDER("メディアのみ（フォルダ）")
}

/** Same media list as としあき(仮): the original file (or the thumbnail) of every post, de-duplicated. */
internal fun futachaMediaOnlyUrls(page: ThreadPage): List<String> =
    compatBatchMediaUrls(page.posts.mapIndexed { index, post -> post.toCompatPostSnapshot(index) })

/**
 * Saves only the media of [page]. The calls and the output names are the ones
 * としあき(仮) uses for save_images_zip / save_images_folder; the returned text is
 * the completion or failure message.
 */
internal suspend fun runFutachaMediaOnlySave(
    kind: FutachaMediaOnlySaveKind,
    page: ThreadPage,
    boardKey: String,
    boardName: String,
    title: String,
    location: SaveLocation?,
    httpClient: HttpClient,
    fileSystem: FileSystem,
    progress: MutableStateFlow<SaveProgress?>
): String = runProtectedThreadSave(title, progress) {
    val urls = futachaMediaOnlyUrls(page)
    progress.value = SaveProgress(phase = SavePhase.DOWNLOADING, current = 0, total = urls.size,
        currentItem = "しばらくお待ち下さい")
    when (kind) {
        FutachaMediaOnlySaveKind.ZIP -> ImageZipSaveService(httpClient, fileSystem).save(
            mediaUrls = urls,
            boardId = boardKey,
            threadId = page.threadId,
            baseSaveLocation = location,
            baseDirectory = MANUAL_SAVE_DIRECTORY,
            onProgress = { current, total, item, itemBytes, itemTotalBytes ->
                progress.value = SaveProgress(phase = SavePhase.DOWNLOADING, current = current, total = total,
                    currentItem = item, currentItemBytes = itemBytes, currentItemTotalBytes = itemTotalBytes)
            }
        ).fold(
            onSuccess = { saved ->
                "${saved.fileName} を保存しました 成功${saved.savedItems}件 / 失敗${saved.failedItems}件\n保存先: ${manualSaveDestinationLabel(fileSystem, location)}"
            },
            onFailure = { it.toCompatUserMessage("ZIPを保存できませんでした") }
        )
        FutachaMediaOnlySaveKind.FOLDER -> {
            if (urls.isEmpty()) return@runProtectedThreadSave "保存するメディアがありません"
            val saver = SingleMediaSaveService(httpClient, fileSystem)
            val outputNames = compatBatchOutputFileNames(urls)
            val imageFolder = buildCompatManualImageFolderName(boardName = boardName, title = title, threadId = page.threadId)
            var success = 0
            var failure = 0
            urls.forEachIndexed { index, url ->
                val item = url.substringBefore('?').substringAfterLast('/')
                progress.value = SaveProgress(phase = SavePhase.DOWNLOADING, current = index, total = urls.size, currentItem = item)
                saver.saveMedia(
                    url, boardKey, page.threadId,
                    baseSaveLocation = location,
                    baseDirectory = MANUAL_SAVE_DIRECTORY,
                    storageDirectoryOverride = imageFolder,
                    useTypeSubdirectory = false,
                    outputFileNameOverride = outputNames[url],
                    onProgress = { itemBytes, itemTotalBytes ->
                        progress.value = SaveProgress(phase = SavePhase.DOWNLOADING, current = index, total = urls.size,
                            currentItem = item, currentItemBytes = itemBytes, currentItemTotalBytes = itemTotalBytes)
                    }
                ).fold({ success += 1 }, { failure += 1 })
            }
            "メディアを保存しました 成功${success}件 / 失敗${failure}件\n保存先: ${manualSaveDestinationLabel(fileSystem, location)}/$imageFolder"
        }
    }
}
