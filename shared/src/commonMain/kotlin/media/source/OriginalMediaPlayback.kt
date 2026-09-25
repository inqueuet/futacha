package com.valoser.futacha.shared.media.source

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okio.FileHandle
import okio.IOException

/** A pinned original with a growing, verified prefix; no player may open its HTTP URL again. */
interface OriginalMediaPlayback : AutoCloseable {
    /** sizeBytes is -1 until EOF when the server omits Content-Length. */
    suspend fun info(requireSize: Boolean = false): OriginalMediaInfo
    /** Returns available bytes, waits for an unfilled position, and returns empty only at verified EOF. */
    suspend fun readAt(offset: Long, length: Int): ByteArray
    /**
     * [readAt] into [buffer] at [bufferOffset]; returns the byte count, 0 only at
     * verified EOF. Lets a per-read caller (a player data source) reuse one buffer.
     */
    suspend fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int {
        require(bufferOffset >= 0 && length >= 0 && bufferOffset <= buffer.size - length)
        val bytes = readAt(offset, length)
        bytes.copyInto(buffer, bufferOffset)
        return bytes.size
    }
    /** An independent consumer of this exact transfer/revision, including across cache clear. */
    fun retain(): OriginalMediaPlayback
    /** Wait for validation/commit and pin the same complete file for parsers, WebKit or export. */
    suspend fun complete(): OriginalMediaStore.Lease
    /**
     * The verified byte count each time it grows, then [DOWNLOAD_COMPLETE] once the original
     * is complete, after which the flow ends. A failed transfer fails the flow.
     */
    fun downloadProgress(): Flow<Long>

    companion object {
        const val DOWNLOAD_COMPLETE = -1L
    }
}

/** No verified bytes arrived for the stall window; a slow but moving download never raises this. */
class OriginalMediaDownloadStalled(val availableBytes: Long) :
    IOException("Original media download stalled at $availableBytes bytes")

/**
 * Runs [block], failing with [OriginalMediaDownloadStalled] only when the download makes no
 * progress for [stallMillis]. Unlike a total timeout this lets a large original on a slow
 * link finish; once the original is complete [block] is no longer time-limited.
 */
internal suspend fun <T> OriginalMediaPlayback.withDownloadStallTimeout(stallMillis: Long, block: suspend () -> T): T =
    coroutineScope {
        val watchdog = launch {
            downloadProgress().collectLatest { bytes ->
                if (bytes == OriginalMediaPlayback.DOWNLOAD_COMPLETE) return@collectLatest
                delay(stallMillis)
                throw OriginalMediaDownloadStalled(bytes)
            }
        }
        try { block() } finally { watchdog.cancel() }
    }

internal data class OriginalMediaReadState(
    val info: OriginalMediaInfo? = null,
    val availableBytes: Long = 0,
    val complete: Boolean = false,
    val failure: Throwable? = null,
    val read: (suspend (Long, Int) -> ByteArray)? = null,
    /** [read] into a caller's buffer, when the source supports it. */
    val readInto: (suspend (Long, ByteArray, Int, Int) -> Unit)? = null
)

internal class SharedOriginalMediaPlayback(
    private val state: StateFlow<OriginalMediaReadState>,
    private val dispatcher: CoroutineDispatcher,
    private val completedLease: suspend () -> OriginalMediaStore.Lease,
    private val references: OriginalMediaStore.LeaseReferences
) : OriginalMediaPlayback {
    private val closed = MutableStateFlow(false)

    private suspend fun awaitState(ready: (OriginalMediaReadState) -> Boolean): OriginalMediaReadState =
        combine(state, closed) { value, stopped ->
            check(!stopped) { "Original media playback lease is closed" }
            value.failure?.let { throw it }
            value
        }.first(ready)

    override suspend fun info(requireSize: Boolean): OriginalMediaInfo =
        requireNotNull(awaitState { it.info != null && (!requireSize || it.info.sizeBytes >= 0) }.info)

    override suspend fun readAt(offset: Long, length: Int): ByteArray {
        require(offset >= 0 && length in 0..2 * 1024 * 1024 && offset <= Long.MAX_VALUE - length)
        check(!closed.value) { "Original media playback lease is closed" }
        if (length == 0) return ByteArray(0)
        // A native reader can be closed on another thread while a positional read is executing.
        // Keep the file handle alive until that read returns; closing still wakes an unfilled read.
        val pin = retain()
        try {
            return withContext(dispatcher) {
                val value = awaitState { it.availableBytes > offset || it.complete }
                currentCoroutineContext().ensureActive()
                if (offset >= value.availableBytes) return@withContext ByteArray(0)
                val count = minOf(length.toLong(), value.availableBytes - offset).toInt()
                val bytes = requireNotNull(value.read)(offset, count)
                if (bytes.size != count) throw IOException("Original media prefix ended before its published size")
                bytes
            }
        } finally { pin.close() }
    }

    override suspend fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int {
        require(offset >= 0 && length in 0..2 * 1024 * 1024 && offset <= Long.MAX_VALUE - length)
        require(bufferOffset >= 0 && bufferOffset <= buffer.size - length)
        check(!closed.value) { "Original media playback lease is closed" }
        if (length == 0) return 0
        val pin = retain()
        try {
            return withContext(dispatcher) {
                val value = awaitState { it.availableBytes > offset || it.complete }
                currentCoroutineContext().ensureActive()
                if (offset >= value.availableBytes) return@withContext 0
                val count = minOf(length.toLong(), value.availableBytes - offset).toInt()
                val readInto = value.readInto
                if (readInto != null) {
                    readInto(offset, buffer, bufferOffset, count)
                } else {
                    val bytes = requireNotNull(value.read)(offset, count)
                    if (bytes.size != count) throw IOException("Original media prefix ended before its published size")
                    bytes.copyInto(buffer, bufferOffset)
                }
                count
            }
        } finally { pin.close() }
    }

    override suspend fun complete(): OriginalMediaStore.Lease {
        val pin = retain()
        try {
            awaitState { it.complete }
            currentCoroutineContext().ensureActive()
            val lease = completedLease()
            try {
                currentCoroutineContext().ensureActive()
                check(!closed.value) { "Original media playback lease is closed" }
                return lease
            } catch (failure: Throwable) { lease.close(); throw failure }
        } finally { pin.close() }
    }

    override fun downloadProgress(): Flow<Long> = state.transformWhile { value ->
        value.failure?.let { throw it }
        if (value.complete) { emit(OriginalMediaPlayback.DOWNLOAD_COMPLETE); false }
        else { emit(value.availableBytes); true }
    }.distinctUntilChanged()

    override fun retain(): OriginalMediaPlayback {
        check(!closed.value) { "Original media playback lease is closed" }
        references.retain()
        if (closed.value) { references.close(); error("Original media playback lease is closed") }
        return SharedOriginalMediaPlayback(state, dispatcher, completedLease, references)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) references.close()
    }
}

internal fun OriginalMediaStore.Lease.asPlayback(dispatcher: CoroutineDispatcher): OriginalMediaPlayback {
    val references = OriginalMediaStore.LeaseReferences(::close)
    return SharedOriginalMediaPlayback(
        MutableStateFlow(OriginalMediaReadState(info, info.sizeBytes, true, read = ::readAt)),
        dispatcher, { retain() }, references
    )
}

internal suspend fun FileHandle.readOriginalMediaPrefix(offset: Long, length: Int): ByteArray {
    val bytes = ByteArray(length)
    readOriginalMediaPrefixInto(offset, bytes, 0, length)
    return bytes
}

internal suspend fun FileHandle.readOriginalMediaPrefixInto(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int) {
    var copied = 0
    while (copied < length) {
        currentCoroutineContext().ensureActive()
        val count = read(offset + copied, buffer, bufferOffset + copied, length - copied)
        if (count <= 0) throw IOException("Original media prefix is truncated")
        copied += count
    }
}
