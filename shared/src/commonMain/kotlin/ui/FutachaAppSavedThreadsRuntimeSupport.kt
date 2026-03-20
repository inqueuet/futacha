package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.util.FileSystem

internal data class FutachaSavedThreadsRepositories(
    val currentRepository: SavedThreadRepository?,
    val legacyRepository: SavedThreadRepository?
)

internal data class FutachaSavedThreadsRepositoryInputs(
    val fileSystem: FileSystem?,
    val manualSaveDirectory: String,
    val manualSaveLocation: SaveLocation
)

internal data class FutachaActiveSavedThreadsRepositoryInputs(
    val currentRepository: SavedThreadRepository?,
    val legacyRepository: SavedThreadRepository?
)

internal fun buildFutachaSavedThreadsRepositories(
    inputs: FutachaSavedThreadsRepositoryInputs
): FutachaSavedThreadsRepositories {
    val currentRepository = inputs.fileSystem?.let { fs ->
        SavedThreadRepository(
            fs,
            baseDirectory = inputs.manualSaveDirectory,
            baseSaveLocation = inputs.manualSaveLocation
        )
    }
    val shouldUseLegacyFallback = inputs.fileSystem != null &&
        !(inputs.manualSaveLocation is SaveLocation.Path &&
            isDefaultManualSaveRoot(inputs.manualSaveDirectory))
    val legacyRepository = if (!shouldUseLegacyFallback) {
        null
    } else {
        SavedThreadRepository(
            inputs.fileSystem,
            baseDirectory = DEFAULT_MANUAL_SAVE_ROOT,
            baseSaveLocation = SaveLocation.Path(DEFAULT_MANUAL_SAVE_ROOT)
        )
    }
    return FutachaSavedThreadsRepositories(
        currentRepository = currentRepository,
        legacyRepository = legacyRepository
    )
}

internal suspend fun shouldResetInaccessibleManualSaveBookmark(
    fileSystem: FileSystem?,
    manualSaveLocation: SaveLocation
): Boolean {
    if (manualSaveLocation !is SaveLocation.Bookmark || fileSystem == null) {
        return false
    }
    return !runCatching { fileSystem.exists(manualSaveLocation, "") }.getOrDefault(false)
}

internal fun resolveFutachaManualSaveDirectoryDisplay(
    fileSystem: FileSystem?,
    manualSaveDirectory: String,
    manualSaveLocation: SaveLocation
): String? {
    return when (manualSaveLocation) {
        is SaveLocation.TreeUri -> "SAF: ${manualSaveLocation.uri}"
        is SaveLocation.Bookmark -> "Bookmark: 保存先が選択済みです"
        is SaveLocation.Path -> {
            runCatching { fileSystem?.resolveAbsolutePath(manualSaveDirectory) }.getOrNull()
        }
    }
}

internal fun selectPreferredSavedThreadsRepository(
    currentRepository: SavedThreadRepository?,
    legacyRepository: SavedThreadRepository?,
    currentCount: Int,
    legacyCount: Int
): SavedThreadRepository? {
    return currentRepository ?: legacyRepository
}

internal suspend fun resolveActiveSavedThreadsRepository(
    inputs: FutachaActiveSavedThreadsRepositoryInputs
): SavedThreadRepository? {
    if (inputs.currentRepository == null) {
        return inputs.legacyRepository
    }
    val currentCount = runCatching { inputs.currentRepository.getThreadCount() }.getOrDefault(0)
    val legacyCount = inputs.legacyRepository?.let {
        runCatching { it.getThreadCount() }.getOrDefault(0)
    } ?: 0
    return selectPreferredSavedThreadsRepository(
        currentRepository = inputs.currentRepository,
        legacyRepository = inputs.legacyRepository,
        currentCount = currentCount,
        legacyCount = legacyCount
    )
}

internal fun isDefaultManualSaveRoot(directory: String): Boolean {
    val normalized = directory.trim().removePrefix("./").trimEnd('/')
    return normalized.equals(DEFAULT_MANUAL_SAVE_ROOT, ignoreCase = true)
}
