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
    /** An independent consumer of this exact transfer/revision, including across cache clear. */
    fun retain(): OriginalMediaPlayback
    /** Wait for validation/commit and pin the same complete file for parsers, WebKit or export. */
    suspend fun complete(): OriginalMediaStore.Lease
}

internal data class OriginalMediaReadState(
    val info: OriginalMediaInfo? = null,
    val availableBytes: Long = 0,
    val complete: Boolean = false,
    val failure: Throwable? = null,
    val read: (suspend (Long, Int) -> ByteArray)? = null
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
    var copied = 0
    while (copied < length) {
        currentCoroutineContext().ensureActive()
        val count = read(offset + copied, bytes, copied, length - copied)
        if (count <= 0) throw IOException("Original media prefix is truncated")
        copied += count
    }
    return bytes
}
