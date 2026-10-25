package com.valoser.futacha.shared.audio.voicevox

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

/**
 * Publication boundary for an already verified model and extracted dictionary.
 * The caller owns exclusive VoiceAssetAccess mutation and cleans staging on failure.
 *
 * The former installation is set aside rather than deleted first. If the final move fails or is cancelled,
 * the verified model returns to its download path (outside the disposable staging) and the former pack is
 * restored, so a retry needs no new transfer and reading keeps the old data meanwhile (L6).
 */
internal suspend fun promoteVerifiedVoicePack(fileSystem: FileSystem, model: Path, staging: Path, current: Path) {
    currentCoroutineContext().ensureActive()
    val stagedModel = staging.resolve("0.vvm")
    val previous = requireNotNull(current.parent).resolve("${current.name}.previous")
    fileSystem.deleteRecursively(previous, mustExist = false)
    fileSystem.atomicMove(model, stagedModel)
    var setAside = false
    try {
        if (fileSystem.metadataOrNull(current) != null) {
            fileSystem.atomicMove(current, previous)
            setAside = true
        }
        fileSystem.atomicMove(staging, current)
    } catch (failure: Throwable) {
        withContext(NonCancellable) {
            runCatching { if (fileSystem.metadataOrNull(stagedModel) != null) fileSystem.atomicMove(stagedModel, model) }
            if (setAside && fileSystem.metadataOrNull(current) == null) runCatching { fileSystem.atomicMove(previous, current) }
        }
        throw failure
    }
    fileSystem.deleteRecursively(previous, mustExist = false)
}
