package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.video.model.VideoFrameIndex
import com.valoser.futacha.shared.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.Clock

internal const val VIDEO_EDIT_MAX_BYTES = 1024L * 1024 * 1024
internal const val VIDEO_EDIT_COPY_BUFFER = 256 * 1024

internal enum class VideoContainer { ISO_MEDIA, WEBM }

/** Container sniffing is only an early rejection; the platform must also find a video track. */
internal fun editableVideoContainer(header: ByteArray): VideoContainer {
    require(header.size >= 12) { "動画ファイルが空か壊れています" }
    if (header[0] == 0x1a.toByte() && header[1] == 0x45.toByte() &&
        header[2] == 0xdf.toByte() && header[3] == 0xa3.toByte()) return VideoContainer.WEBM
    require(header.copyOfRange(4, 8).decodeToString() in setOf("ftyp", "moov", "free", "wide", "mdat")) {
        "MP4・MOV・WebMの動画を選択してください"
    }
    return VideoContainer.ISO_MEDIA
}

/** The source file belongs to this editing session. It never points at the user's original. */
internal class VideoEditSource private constructor(
    val path: String,
    val displayName: String,
    val byteSize: Long,
    val container: VideoContainer,
    private val directory: String,
    private val fileSystem: FileSystem,
    private val gate: MediaFeatureGate,
    private val permit: MediaFeaturePermit
) {
    private val mutex = Mutex()
    private val closed = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun checkActive() {
        check(!closed.value && gate.isCurrent(permit)) { "動画編集は無効になりました" }
    }

    /** Close waits for readers/exporters, so a codec never loses its file underneath it. */
    suspend fun <T> useFile(block: suspend (String) -> T): T = mutex.withLock {
        checkActive()
        coroutineScope {
            val operation = this
            val monitor = launch(start = CoroutineStart.UNDISPATCHED) {
                gate.permits(MediaFeature.VIDEO_EDITOR).collect {
                    if (!gate.isCurrent(permit)) operation.cancel("動画編集は無効になりました")
                }
            }
            try {
                block(path).also { currentCoroutineContext().ensureActive(); checkActive() }
            } finally { monitor.cancel() }
        }
    }

    /**
     * Never throws: pickers call this from finally blocks of scopes without an
     * exception handler, where a failed delete used to crash the app. A work
     * directory that cannot be removed now is collected by the next process's
     * first import.
     */
    suspend fun close() = withContext(NonCancellable) {
        mutex.withLock {
            closed.value = true
            fileSystem.deleteRecursively(directory).onFailure {
                Logger.w("VideoEditSource", "Failed to delete video work directory $directory: ${it.message}")
            }
        }
        Unit
    }

    companion object {
        private val cleanupMutex = Mutex()
        private val preparedDirectories = mutableSetOf<String>()

        // First use in this process precedes every new session in the same app-owned root.
        // Never repeat this scan while a decoder/exporter may still own a session there.
        private suspend fun prepareDirectory(fileSystem: FileSystem, root: String) = cleanupMutex.withLock {
            val key = fileSystem.resolveAbsolutePath(root).trimEnd('/')
            if (key !in preparedDirectories) {
                // An undeletable leftover must not block every later import in this process.
                fileSystem.listFiles(root).filter { it.matches(Regex("device-video-[0-9]+-[0-9a-z]+")) }
                    .forEach { name ->
                        fileSystem.deleteRecursively("${root.trimEnd('/')}/$name").onFailure {
                            Logger.w("VideoEditSource", "Failed to delete abandoned video session $name: ${it.message}")
                        }
                    }
                preparedDirectories.add(key)
            }
        }

        /** The caller opens the selected device resource once, and closes it in finally/use. */
        suspend fun import(
            fileSystem: FileSystem,
            cacheDirectory: String,
            displayName: String,
            reader: FileReadSource,
            gate: MediaFeatureGate,
            permit: MediaFeaturePermit,
            maxBytes: Long = VIDEO_EDIT_MAX_BYTES,
            onProgress: (Long) -> Unit = {}
        ): VideoEditSource {
            var transferred: VideoEditSource? = null
            try {
                return withContext(AppDispatchers.io) {
                    require(permit.feature == MediaFeature.VIDEO_EDITOR)
                    require(maxBytes in 12..VIDEO_EDIT_MAX_BYTES)
                    fun validate() { check(gate.isCurrent(permit)) { "動画編集は無効になりました" } }
                    validate()
                    prepareDirectory(fileSystem, cacheDirectory)
                    currentCoroutineContext().ensureActive(); validate()
                    val id = "${Clock.System.now().toEpochMilliseconds()}-${Random.nextLong().toULong().toString(36)}"
                    val directory = "${cacheDirectory.trimEnd('/')}/device-video-$id"
                    var published = false
                    try {
                        fileSystem.createDirectory(directory).getOrThrow()
                        val header = ByteArray(12)
                        var offset = 0
                        while (offset < header.size) {
                            currentCoroutineContext().ensureActive(); validate()
                            val count = reader.read(header, offset, header.size - offset)
                            require(count in 1..(header.size - offset)) { "動画ファイルが空か壊れています" }
                            offset += count
                        }
                        val container = editableVideoContainer(header)
                        // AVFoundation requires a recognized file type; bytes remain completely unchanged.
                        val path = "$directory/input.${if (container == VideoContainer.WEBM) "webm" else "mp4"}"
                        var total = 12L
                        fileSystem.writeByteStream(path) { sink ->
                            sink.write(header)
                            val buffer = ByteArray(VIDEO_EDIT_COPY_BUFFER)
                            while (true) {
                                currentCoroutineContext().ensureActive(); validate()
                                val count = reader.read(buffer)
                                if (count == -1) break
                                check(count in 1..buffer.size) { "動画の読み込みが停止しました" }
                                check(count <= maxBytes - total) { "動画のサイズ上限を超えています（最大1GB）" }
                                sink.write(buffer, length = count)
                                total += count
                                onProgress(total)
                            }
                        }.getOrThrow()
                        currentCoroutineContext().ensureActive(); validate()
                        VideoEditSource(fileSystem.resolveAbsolutePath(path), displayName.take(255), total,
                            container, directory, fileSystem, gate, permit).also { transferred = it; published = true }
                    } finally {
                        // Keep the original failure; a cleanup error must not replace it.
                        if (!published) withContext(NonCancellable) {
                            fileSystem.deleteRecursively(directory).onFailure {
                                Logger.w("VideoEditSource", "Failed to delete unpublished video session: ${it.message}")
                            }
                        }
                    }
                }
            } catch (failure: Throwable) {
                // withContext can cancel while handing an already-created resource back to the caller.
                try { transferred?.close() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                throw failure
            }
        }
    }
}

/** Presentation order, upright dimensions and audio presence are inspected without prompt parsing. */
internal data class VideoEditInfo(
    val width: Int,
    val height: Int,
    val frames: VideoFrameIndex,
    val hasAudio: Boolean,
    val hdr: Boolean,
    val rotationDegrees: Int
) {
    init { require(width > 0 && height > 0 && rotationDegrees in setOf(0, 90, 180, 270)) }
}

internal expect suspend fun inspectDeviceVideo(path: String): VideoEditInfo

/** PTS are collected in decode order by extractors; normalize only ordering, never frame spacing. */
internal fun buildVideoFrameIndex(timestamps: List<Long>, reportedDurationUs: Long): VideoFrameIndex {
    require(timestamps.isNotEmpty() && timestamps.size <= VideoFrameIndex.MAX_FRAMES) { "映像フレームを読み取れません" }
    require(timestamps.all { it >= 0 }) { "動画のフレーム時刻が不正です" }
    val sorted = timestamps.sorted()
    // Duplicate presentation timestamps cannot support unambiguous stepping or verified export.
    require(sorted.zipWithNext().all { (a, b) -> a < b }) { "同じ時刻の映像フレームが重複しています" }
    val last = sorted.last()
    val lastDuration = if (sorted.size > 1) last - sorted[sorted.lastIndex - 1] else 33_333L
    require(last <= Long.MAX_VALUE - lastDuration) { "動画の長さが不正です" }
    val end = if (reportedDurationUs > last) reportedDurationUs else last + lastDuration
    return VideoFrameIndex(sorted.toLongArray(), end)
}

internal fun verifyVideoEditTiming(before: VideoEditInfo, after: VideoEditInfo) {
    require(before.width == after.width && before.height == after.height) { "保存動画の解像度が元動画と一致しません" }
    require(before.hasAudio == after.hasAudio) { "音声の引き継ぎを確認できません" }
    val a = before.frames; val b = after.frames
    require(a.size == b.size) { "保存動画のフレーム数が元動画と一致しません" }
    val startA = a.timeAt(0); val startB = b.timeAt(0)
    require(kotlin.math.abs((a.durationUs - startA) - (b.durationUs - startB)) <= 1000) { "保存動画の長さが元動画と一致しません" }
    require((0 until a.size).all { kotlin.math.abs((a.timeAt(it) - startA) - (b.timeAt(it) - startB)) <= 1000 }) {
        "保存動画のフレーム時刻が元動画と一致しません"
    }
}
