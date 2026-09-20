@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.media.source

import com.valoser.futacha.shared.ui.board.buildEmbeddedVideoHtml
import com.valoser.futacha.shared.ui.board.WebmPlaybackInfo
import com.valoser.futacha.shared.ui.board.WEBM_TRACK_PROBE_BYTES
import com.valoser.futacha.shared.ui.board.readWebmPlaybackInfo
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import kotlin.random.Random
import okio.Path.Companion.toPath
import okio.use

/** A private HTML document and a hard link to the same original inode; no second media copy. */
internal class IosLocalVideoDocument private constructor(
    val url: NSURL,
    val readAccessUrl: NSURL,
    val webmInfo: WebmPlaybackInfo?,
    private val references: OriginalMediaStore.LeaseReferences,
    val cleanup: Deferred<Unit>
) : AutoCloseable {
    private val closed = MutableStateFlow(false)
    fun retain(): IosLocalVideoDocument {
        check(!closed.value)
        references.retain()
        if (closed.value) { references.close(); error("Local video document is closed") }
        return IosLocalVideoDocument(url, readAccessUrl, webmInfo, references, cleanup)
    }
    override fun close() { if (closed.compareAndSet(false, true)) references.close() }

    companion object {
        private val cleanupMutex = Mutex()
        private val preparedDirectories = mutableSetOf<okio.Path>()

        private suspend fun prepareDirectory(fs: okio.FileSystem, root: okio.Path) = cleanupMutex.withLock {
            if (root !in preparedDirectories) {
                fs.listOrNull(root).orEmpty().filter { it.name.matches(Regex("web-player-[0-9]+")) }
                    .forEach { fs.deleteRecursively(it, mustExist = false) }
                preparedDirectories.add(root)
            }
        }

        suspend fun create(original: OriginalMediaPlayback, extension: String): IosLocalVideoDocument {
            require(extension.matches(Regex("[a-z0-9]{1,8}")))
            val lease = original.complete()
            return createLinked(lease.fileSystem, lease.file, extension, lease.info.sizeBytes, lease::close)
        }

        suspend fun createLocal(filePath: String, extension: String): IosLocalVideoDocument {
            val file = filePath.toPath()
            val fs = okio.FileSystem.SYSTEM
            val size = requireNotNull(withContext(AppDispatchers.io) { fs.metadata(file).size })
            return createLinked(fs, file, extension, size, {}, okio.FileSystem.SYSTEM_TEMPORARY_DIRECTORY)
        }

        private suspend fun createLinked(fs: okio.FileSystem, source: okio.Path, extension: String,
            size: Long, release: () -> Unit, directory: okio.Path = requireNotNull(source.parent)): IosLocalVideoDocument {
            require(extension.matches(Regex("[a-z0-9]{1,8}")))
            val work = directory.resolve("web-player-${Random.nextLong().toULong()}")
            try {
                val webmInfo = if (extension == "webm") withContext(AppDispatchers.io) {
                    fs.openReadOnly(source).use { handle ->
                        readWebmPlaybackInfo(handle.readOriginalMediaPrefix(0, minOf(size, WEBM_TRACK_PROBE_BYTES.toLong()).toInt()))
                    }
                } else null
                withContext(AppDispatchers.io) {
                    prepareDirectory(fs, directory)
                    fs.createDirectories(work)
                    check(NSFileManager.defaultManager.linkItemAtPath(source.toString(), work.resolve("video.$extension").toString(), null)) {
                        "Cannot make the original available to the local video player"
                    }
                    fs.write(work.resolve("player.html")) { writeUtf8(buildEmbeddedVideoHtml("video.$extension", webmInfo?.mimeType)) }
                }
                currentCoroutineContext().ensureActive()
                val cleanup = CompletableDeferred<Unit>()
                val references = OriginalMediaStore.LeaseReferences {
                    CoroutineScope(NonCancellable + AppDispatchers.io).launch {
                        try { runCatching { fs.deleteRecursively(work, mustExist = false) } }
                        finally { release(); cleanup.complete(Unit) }
                    }
                }
                return IosLocalVideoDocument(NSURL.fileURLWithPath(work.resolve("player.html").toString()),
                    NSURL.fileURLWithPath(work.toString(), isDirectory = true), webmInfo, references, cleanup)
            } catch (failure: Throwable) {
                withContext(NonCancellable + AppDispatchers.io) {
                    try { runCatching { fs.deleteRecursively(work, mustExist = false) } } finally { release() }
                }
                throw failure
            }
        }
    }
}
