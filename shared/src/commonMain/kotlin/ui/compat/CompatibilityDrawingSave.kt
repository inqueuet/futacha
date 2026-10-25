package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatBuildDraft
import com.valoser.futacha.shared.compat.CompatHost
import com.valoser.futacha.shared.compat.CompatReplyDraft
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Writes the person's copy of a handwritten picture to "手書きファイルの保存先" (when set) after the picture is attached,
 * without holding the attachment up (L39: every mode kept the drawing screen busy until the copy was written, which can
 * be slow in a chosen folder). Runs on [scope], which should outlive the post screen; [onFailure] is called when the
 * copy could not be written. The attachment stays either way.
 */
internal fun launchCompatDrawingCopy(
    scope: CoroutineScope,
    fileSystem: FileSystem,
    location: SaveLocation?,
    drawing: ImageData,
    now: Long,
    onFailure: () -> Unit
): Job? {
    if (location == null) return null
    return scope.launch {
        val failed = try {
            persistCompatDrawingCopy(fileSystem, location, drawing, now)?.isFailure == true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            true
        }
        if (failed) onFailure()
    }
}

/** Saves the private PNG and its draft reference; the person's copy is written separately ([launchCompatDrawingCopy]). */
internal suspend fun saveCompatDrawingToDraft(
    store: CompatibilityStore,
    fileSystem: FileSystem,
    origin: CompatHost,
    drawing: ImageData,
    now: Long
) {
    val reply = (origin as? CompatHost.Post)?.let { store.loadDraft(it.tabKey) }
    val build = (origin as? CompatHost.PostBuild)?.let { store.loadBuildDraft(it.boardKey) }
    val owner = when (origin) {
        is CompatHost.Post -> origin.tabKey
        is CompatHost.PostBuild -> "build:${origin.boardKey}"
        else -> error("手書き画像の添付先を開けません")
    }
    val previous = reply?.attachmentUri ?: build?.attachmentUri
    val locator = persistCompatPostAttachment(fileSystem, owner, drawing).getOrThrow()
    try {
        when (origin) {
            is CompatHost.Post -> store.saveDraft(reply?.copy(attachmentUri = locator,
                attachmentIsHandwriting = true, updatedAtEpochMillis = now)
                ?: CompatReplyDraft(origin.tabKey, attachmentUri = locator,
                    attachmentIsHandwriting = true, updatedAtEpochMillis = now))
            is CompatHost.PostBuild -> store.saveBuildDraft(build?.copy(attachmentUri = locator,
                attachmentIsHandwriting = true, updatedAtEpochMillis = now)
                ?: CompatBuildDraft(origin.boardKey, attachmentUri = locator,
                    attachmentIsHandwriting = true, updatedAtEpochMillis = now))
            else -> error("手書き画像の添付先を開けません")
        }
    } catch (failure: Throwable) {
        // A store can fail/cancel after committing. Delete only when a successful read confirms
        // this payload is unreferenced; an unreadable draft must never lose a possibly saved PNG.
        withContext(NonCancellable) {
            val referenced = runCatching {
                when (origin) {
                    is CompatHost.Post -> store.loadDraft(origin.tabKey)?.attachmentUri
                    is CompatHost.PostBuild -> store.loadBuildDraft(origin.boardKey)?.attachmentUri
                    else -> null
                }
            }
            if (referenced.isSuccess && referenced.getOrNull() != locator) {
                deleteCompatPostAttachment(fileSystem, locator, deleteContainer = true)
            }
        }
        throw failure
    }
    previous?.takeIf { it != locator }?.let {
        deleteCompatPostAttachment(fileSystem, it, deleteContainer = true)
            .onFailure { Logger.w("CompatibilityDrawing", "Could not remove the previous attachment") }
    }
}
