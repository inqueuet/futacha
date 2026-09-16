package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.util.FileSystem

internal fun Map<String, String>.compatManualSaveLocation(): SaveLocation? =
    parseCompatSaveLocation(compatPreferenceValue("storage", "dummyDownloadDir", "保存ファイルの保存先"))

internal fun createCompatSavedThreadRepository(fileSystem: FileSystem, location: SaveLocation?): SavedThreadRepository =
    SavedThreadRepository(
        fileSystem,
        baseDirectory = (location as? SaveLocation.Path)?.path ?: MANUAL_SAVE_DIRECTORY,
        baseSaveLocation = location
    )

internal suspend fun completeCompatThreadSave(
    saved: SavedThread,
    fileSystem: FileSystem,
    location: SaveLocation?
): String {
    val indexed = createCompatSavedThreadRepository(fileSystem, location).addThreadToIndex(saved)
    return buildString {
        append(compatThreadSaveCompletionMessage(saved))
        append("\n保存先: ").append(manualSaveDestinationLabel(fileSystem, location))
        if (indexed.isFailure) append("\n保存一覧に反映できませんでした。保存先を確認してください。")
    }
}

internal fun manualSaveDestinationLabel(fileSystem: FileSystem, location: SaveLocation?): String = when (location) {
    is SaveLocation.TreeUri -> "選択したフォルダ（${location.uri.substringAfterLast('/').replace("%3A", ":").replace("%2F", "/")}）"
    is SaveLocation.Bookmark -> "「ファイル」で選択したフォルダ"
    is SaveLocation.Path -> fileSystem.resolveAbsolutePath(location.path)
    null -> fileSystem.resolveAbsolutePath(MANUAL_SAVE_DIRECTORY)
}

@androidx.compose.runtime.Composable
internal fun rememberCompatManualSaveDestinationLauncher(
    store: com.valoser.futacha.shared.compat.CompatibilityStore,
    preferences: Map<String, String>,
    onFailure: (Throwable) -> Unit
): ((SaveLocation?) -> Unit) -> Unit {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return com.valoser.futacha.shared.ui.board.rememberManualSaveDestinationLauncher(
        location = preferences.compatManualSaveLocation(),
        onSelected = { location ->
            scope.launchCompatScreenAction("CompatSaveDestination", onFailure) {
                store.savePreference(compatPreferenceStorageKey("storage", "dummyDownloadDir"),
                    with(SaveLocation.Companion) { location.toRawString() })
            }
        }
    )
}

internal fun compatMediaSaveCompletionMessage(
    saved: com.valoser.futacha.shared.service.SavedMediaFile,
    fileSystem: FileSystem,
    location: SaveLocation?
): String = "${saved.fileName}を保存しました\n保存先: ${manualSaveDestinationLabel(fileSystem, location)}/${saved.relativePath}\nファイルとして保存しました。写真アプリへ追加する場合は「共有」を使ってください。"
