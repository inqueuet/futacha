package com.valoser.futacha.shared.media.source

import coil3.disk.DiskCache
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.Buffer
import okio.FileHandle
import okio.Sink
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.buffer
import okio.use
import kotlin.random.Random

/** A request for original bytes, independent of decoding size and metadata parsing. */
data class OriginalMediaRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    /** A nonzero token identifies one explicit reload operation. */
    val reloadToken: Long = 0,
    val allowNetwork: Boolean = true,
    /** False retains a file only while it has consumers. */
    val persist: Boolean = true
) {
    init {
        require(headers.keys.map { it.lowercase() }.distinct().size == headers.size) {
            "Duplicate original media header names"
        }
    }
    internal fun cacheKey(): String = buildString {
        append(url.length).append(':').append(url)
        headers.entries.sortedBy { it.key.lowercase() }.forEach { (name, value) ->
            append('|').append(name.lowercase()).append(':').append(value.length).append(':').append(value)
        }
    }.encodeUtf8().sha256().hex()
}

@Serializable
data class OriginalMediaInfo(
    val mimeType: String? = null,
    val sizeBytes: Long,
    val etag: String? = null,
    val resolvedUrl: String,
    val cacheable: Boolean = true
)

/** Streams one successful original into the supplied sink; it never owns that sink. */
fun interface OriginalMediaDownloader {
    suspend fun download(request: OriginalMediaRequest, sink: BufferedSink): OriginalMediaInfo
    /** Called only after successful response/header validation; unknown length is -1. */
    suspend fun download(request: OriginalMediaRequest, sink: BufferedSink, onHeaders: (OriginalMediaInfo) -> Unit): OriginalMediaInfo =
        download(request, sink).also(onHeaders)
    /** Optional migration of verified legacy original bytes, without any network request. */
    suspend fun downloadCached(request: OriginalMediaRequest, sink: BufferedSink): OriginalMediaInfo? = null

    /** Called after the failed file is discarded. At most two retries are allowed. */
    suspend fun retryAfter(retry: Int, failure: Throwable): Boolean = false
}

interface OriginalMediaSource {
    val cacheIdentity: String
    fun registerCacheImporter(importer: OriginalMediaCacheImporter): AutoCloseable? = null
    suspend fun acquire(request: OriginalMediaRequest): OriginalMediaStore.Lease
    suspend fun acquireForPlayback(request: OriginalMediaRequest): OriginalMediaPlayback =
        acquire(request).asPlayback(AppDispatchers.io)
    /** Byte-only consumers must not start metadata analysis as a side effect. */
    suspend fun acquireForExport(request: OriginalMediaRequest): OriginalMediaStore.Lease = acquire(request)
    suspend fun clear()
    suspend fun sizeBytes(): Long
}

/** Leases still open while a store waits to shut down. */
data class OriginalMediaShutdownReport(
    val liveEntries: Int,
    val playbackEntries: Int,
    val oldestAgeMillis: Long
)

class OriginalMediaCacheUnavailable(cause: Throwable) : IOException("Original media cache is unavailable", cause)

/**
 * A cache-only request (allowNetwork = false) found nothing. This is a normal
 * answer, not a transient network failure: it must never be retried or waited
 * on, and must not create a network client.
 */
class OriginalMediaNotCached : IOException("Original media is not cached")

/**
 * One owner for original downloads and files. Consumers share an in-flight job and obtain
 * independent leases on its bytes. A metadata consumer has no network client of its own.
 * The caller owns this store for the lifetime of its HTTP/cookie session.
 */
class OriginalMediaStore(
    /** A stable, non-secret credential/profile generation; do not reuse across sessions. */
    private val cacheNamespace: String,
    /** A dedicated cache owned exclusively by this store, opened off the UI thread. */
    private val createCache: suspend () -> DiskCache,
    private val downloader: OriginalMediaDownloader,
    private val dispatcher: CoroutineDispatcher = AppDispatchers.io,
    /**
     * How long an unfinished playback download keeps running after its last player let go.
     * A player that gave up (stall, error screen) and is retried joins the same transfer
     * instead of restarting from byte 0. Explicit reloads (a new token) still restart.
     */
    private val playbackLingerMillis: Long = DEFAULT_PLAYBACK_LINGER_MILLIS
) : OriginalMediaSource, AutoCloseable {
    init { require(cacheNamespace.isNotBlank()) }
    override val cacheIdentity = cacheNamespace.encodeUtf8().sha256().hex()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val cleanupScope = CoroutineScope(NonCancellable + dispatcher)
    private val mutex = Mutex()
    private val closed = MutableStateFlow(false)
    private val shutdown = CompletableDeferred<Unit>()
    private val cache = scope.async(start = CoroutineStart.LAZY) {
        try { createCache() }
        catch (failure: CancellationException) { throw failure }
        catch (failure: Exception) { throw OriginalMediaCacheUnavailable(failure) }
    }
    private val active = mutableMapOf<String, Entry>()
    private val live = mutableSetOf<Entry>()
    private var generation = 0L
    private var cacheClosed = false
    private val json = Json { ignoreUnknownKeys = true }

    private class Entry(val key: String, val diskKey: String, val request: OriginalMediaRequest, val generation: Long) {
        val createdAt = kotlin.time.TimeSource.Monotonic.markNow()
        lateinit var job: Deferred<Asset>
        var users = 0
        var retired = false
        /** Retired because a reload with another token replaced it (not clear/close). */
        var superseded = false
        var finished = false
        var asset: Asset? = null
        val progress = MutableStateFlow(OriginalMediaReadState())
        var readHandle: FileHandle? = null
        val playbackRequested = MutableStateFlow(false)
        /** No users, but the download continues for [playbackLingerMillis] so a retry can join. */
        var lingering = false
        var lingerSerial = 0L
    }

    private class Asset(
        val snapshot: DiskCache.Snapshot,
        val disk: DiskCache,
        val info: OriginalMediaInfo,
        val identity: String,
        val fromCache: Boolean
    )

    @Serializable
    private data class Pointer(val revision: String, val reloadToken: Long, val info: OriginalMediaInfo)

    /** File handles must be released even when the caller's own coroutine is cancelled. */
    class Lease internal constructor(
        val file: Path,
        val fileSystem: FileSystem,
        val info: OriginalMediaInfo,
        /** Changes whenever a fresh response replaces the original. */
        val identity: String,
        val fromCache: Boolean,
        private val dispatcher: CoroutineDispatcher,
        private val release: () -> Unit,
        private val references: LeaseReferences = LeaseReferences(release)
    ) : AutoCloseable {
        private val closed = MutableStateFlow(false)

        /** Pin this exact revision for another consumer, without consulting URL/cache/network. */
        fun retain(): Lease {
            check(!closed.value) { "Original media lease is closed" }
            references.retain()
            return Lease(file, fileSystem, info, identity, fromCache, dispatcher, release, references)
        }

        /** Opens the file for this one read; a parser should use [withReader]. */
        suspend fun readAt(offset: Long, length: Int): ByteArray = withContext(dispatcher) {
            check(!closed.value) { "Original media lease is closed" }
            fileSystem.openReadOnly(file).use { handle -> readFrom(handle, offset, length) }
        }

        /**
         * Runs [block] with a positional reader over one file handle opened for the
         * whole session. Metadata parsing issues thousands of small reads; opening
         * and closing the file for each one used up the parse's time budget.
         */
        suspend fun <T> withReader(block: suspend (readAt: suspend (Long, Int) -> ByteArray) -> T): T {
            check(!closed.value) { "Original media lease is closed" }
            val handle = withContext(dispatcher) { fileSystem.openReadOnly(file) }
            try {
                return block { offset, length ->
                    check(!closed.value) { "Original media lease is closed" }
                    withContext(dispatcher) { readFrom(handle, offset, length) }
                }
            } finally {
                withContext(NonCancellable + dispatcher) {
                    try { handle.close() } catch (failure: IOException) {
                        Logger.w("OriginalMediaStore", "Failed to close original media reader: ${failure.message}")
                    }
                }
            }
        }

        private suspend fun readFrom(handle: FileHandle, offset: Long, length: Int): ByteArray {
            require(offset >= 0 && length in 0..MAX_READ_BYTES && offset <= Long.MAX_VALUE - length)
            if (offset >= info.sizeBytes || length == 0) return ByteArray(0)
            val result = ByteArray(minOf(length.toLong(), info.sizeBytes - offset).toInt())
            var count = 0
            while (count < result.size) {
                currentCoroutineContext().ensureActive()
                val read = handle.read(offset + count, result, count, result.size - count)
                if (read <= 0) throw IOException("Original media ended before its recorded size")
                count += read
            }
            return result
        }

        override fun close() {
            if (closed.compareAndSet(expect = false, update = true)) references.close()
        }
    }

    internal class LeaseReferences(private val release: () -> Unit) {
        private val count = MutableStateFlow(1)
        fun retain() {
            while (true) {
                val previous = count.value
                check(previous > 0) { "Original media lease is closed" }
                if (count.compareAndSet(previous, previous + 1)) return
            }
        }
        fun close() {
            while (true) {
                val previous = count.value
                check(previous > 0)
                if (count.compareAndSet(previous, previous - 1)) {
                    if (previous == 1) release()
                    return
                }
            }
        }
    }

    private suspend fun join(request: OriginalMediaRequest, playback: Boolean): Entry {
        // Snapshot mutable caller-owned maps before computing the key or starting a job.
        val frozen = request.copy(headers = request.headers.toMap())
        val diskKey = frozen.cacheKey()
        // A cache-only metadata miss must never become the job an online display joins.
        val key = "$diskKey:${frozen.persist}:${frozen.allowNetwork}"
        val entry = mutex.withLock {
            check(!closed.value) { "Original media store is closed" }
            var current = active[key]
            if (current != null && frozen.reloadToken != 0L && current.request.reloadToken != frozen.reloadToken) {
                current.superseded = true
                retireLocked(current)
                current = null
            }
            // Cache-only consumers cannot join an operation that would start network traffic.
            if (current != null && !frozen.allowNetwork && current.asset == null) {
                throw IOException("Original media is not yet available offline")
            }
            (current ?: Entry(key, diskKey, frozen, generation).also { created ->
                created.job = scope.async(start = CoroutineStart.LAZY) { load(created) }
                // A lazy job cancelled before its body starts never enters load's finally.
                created.job.invokeOnCompletion { failure ->
                    if (failure != null) created.progress.update { it.copy(failure = failure) }
                    cleanupScope.launch {
                        mutex.withLock {
                            created.finished = true
                            disposeIfUnusedLocked(created)
                        }
                    }
                }
                active[key] = created
                live += created
            }).also { it.users++; it.lingering = false; if (playback) it.playbackRequested.value = true }
        }
        entry.job.start()
        return entry
    }

    override suspend fun acquireForPlayback(request: OriginalMediaRequest): OriginalMediaPlayback {
        val entry = join(request, playback = true)
        val references = LeaseReferences { cleanupScope.launch { release(entry) } }
        // No suspending return after ownership transfer: cancellation must not discard the lease.
        return SharedOriginalMediaPlayback(entry.progress, dispatcher, completedLease = {
            val asset = entry.job.await()
            currentCoroutineContext().ensureActive()
            references.retain()
            Lease(asset.snapshot.data, asset.disk.fileSystem, asset.info, asset.identity, asset.fromCache,
                dispatcher, release = {}, references = references)
        }, references = references)
    }

    override suspend fun acquire(request: OriginalMediaRequest): Lease {
        var attempt = request
        repeat(MAX_SUPERSEDED_REJOINS + 1) {
            acquireOnce(attempt)?.let { return it }
            // Another screen reloaded the same original while this caller waited.
            // Wait for that newer download instead of failing with its
            // cancellation, which Coil would leave as a permanent loading state.
            // Token 0 joins the running entry without retiring it again.
            attempt = attempt.copy(reloadToken = 0L)
        }
        throw IOException("Original media reload was superseded repeatedly")
    }

    /** Returns null when the joined entry was superseded by another reload. */
    private suspend fun acquireOnce(request: OriginalMediaRequest): Lease? {
        val entry = join(request, playback = false)
        try {
            val asset = entry.job.await()
            currentCoroutineContext().ensureActive()
            mutex.withLock {
                if (entry.retired || closed.value) throw CancellationException("Original media request invalidated")
            }
            // Do not wrap this return in withContext: prompt cancellation on its return
            // could discard a newly created lease before the caller can close it.
            return Lease(asset.snapshot.data, asset.disk.fileSystem, asset.info, asset.identity, asset.fromCache, dispatcher, release = {
                cleanupScope.launch { release(entry) }
            })
        } catch (failure: Throwable) {
            withContext(NonCancellable + dispatcher) { release(entry) }
            if (failure is CancellationException && currentCoroutineContext().isActive && entry.superseded && !closed.value) {
                return null
            }
            throw failure
        }
    }

    private suspend fun load(entry: Entry): Asset {
        var pending: Asset? = null
        try {
            val disk = cache.await()
            pending = mutex.withLock {
                ensureCurrentLocked(entry)
                readCached(disk, entry)
            }
            if (pending == null) {
                var retries = 0
                while (pending == null) {
                    val revision = "asset-${Random.nextLong().toULong().toString(16)}-${Random.nextLong().toULong().toString(16)}"
                    val editor = disk.openEditor(revision) ?: throw IOException("Cannot create original media cache entry")
                    var downloading = true
                    try {
                        var fromLegacyCache = false
                        val rawSink = disk.fileSystem.sink(editor.data)
                        val readHandle = try { disk.fileSystem.openOriginalMediaReadHandle(editor.data) }
                        catch (failure: Throwable) { rawSink.close(); throw failure }
                        entry.readHandle = readHandle
                        entry.progress.value = OriginalMediaReadState(
                            read = readHandle::readOriginalMediaPrefix,
                            readInto = readHandle::readOriginalMediaPrefixInto
                        )
                        val streamingSink = object : Sink by rawSink {
                            override fun write(source: Buffer, byteCount: Long) {
                                rawSink.write(source, byteCount)
                                // Native Okio uses stdio buffering underneath Sink. Publish only
                                // after fflush exposes bytes to the independently opened reader.
                                rawSink.flush()
                                entry.progress.update { it.copy(availableBytes = it.availableBytes + byteCount) }
                            }
                        }
                        val downloaded = streamingSink.buffer().use {
                            (downloader.downloadCached(entry.request, it)?.also { fromLegacyCache = true } ?: run {
                                if (!entry.request.allowNetwork) throw OriginalMediaNotCached()
                                downloader.download(entry.request, it) { info -> entry.progress.update { state -> state.copy(info = info) } }
                            }).also { downloading = false }
                        }
                        val result = downloaded.copy(cacheable = downloaded.cacheable && entry.request.persist)
                        if (disk.fileSystem.metadata(editor.data).size != result.sizeBytes || result.sizeBytes <= 0) {
                            throw IOException("Original media size does not match the downloaded body")
                        }
                        disk.fileSystem.write(editor.metadata) { writeUtf8(json.encodeToString(result)) }
                        currentCoroutineContext().ensureActive()
                        val snapshot = editor.commitAndOpenSnapshot()
                            ?: throw IOException("Cannot retain the downloaded original")
                        pending = Asset(snapshot, disk, result, revision, fromCache = fromLegacyCache)
                        mutex.withLock {
                            ensureCurrentLocked(entry)
                            if (!result.cacheable) {
                                // A caller disabling writes must not erase another
                                // consumer's persistent cache entry. Server no-store can.
                                if (!downloaded.cacheable) disk.remove(pointerKey(entry.diskKey))
                                return@withLock
                            }
                            val pointer = disk.openEditor(pointerKey(entry.diskKey))
                                ?: throw IOException("Cannot update the original media cache index")
                            try {
                                disk.fileSystem.write(pointer.data) {
                                    writeUtf8(json.encodeToString(Pointer(revision, entry.request.reloadToken, result)))
                                }
                                disk.fileSystem.write(pointer.metadata) { writeUtf8("original-media-v1") }
                                pointer.commit()
                            } catch (failure: Throwable) {
                                pointer.abort()
                                throw failure
                            }
                        }
                    } catch (failure: Throwable) {
                        runCatching { editor.abort() }
                        val failedAsset = pending
                        pending = null
                        if (failedAsset != null) {
                            cleanupIo("close failed original media snapshot") { failedAsset.snapshot.close() }
                        }
                        val discarded = cleanupIo("remove failed original media") { disk.remove(revision) }
                        // Keep the original failure and do not retry if its file could not be discarded.
                        if (!discarded || !downloading || failure is CancellationException || failure is OriginalMediaNotCached ||
                            entry.playbackRequested.value || retries >= 2 || !downloader.retryAfter(retries++, failure)
                        ) {
                            throw failure
                        }
                        mutex.withLock {
                            // A player may have joined while retryAfter was suspended. Never splice a
                            // replacement HTTP response into bytes a decoder has already consumed.
                            if (entry.playbackRequested.value) throw failure
                            closeReadHandleLocked(entry)
                            entry.progress.value = OriginalMediaReadState()
                        }
                    }
                }
            }
            return mutex.withLock {
                ensureCurrentLocked(entry)
                requireNotNull(pending).also { asset ->
                    if (entry.readHandle == null) entry.readHandle = asset.disk.fileSystem.openOriginalMediaReadHandle(asset.snapshot.data)
                    val reader = requireNotNull(entry.readHandle)
                    entry.asset = asset
                    entry.progress.value = OriginalMediaReadState(
                        asset.info, asset.info.sizeBytes, true,
                        read = reader::readOriginalMediaPrefix,
                        readInto = reader::readOriginalMediaPrefixInto
                    )
                    pending = null
                }
            }
        } finally {
            withContext(NonCancellable) {
                pending?.let {
                    cleanupIo("close pending original media snapshot") { it.snapshot.close() }
                    cleanupIo("remove pending original media") { it.disk.remove(it.identity) }
                }
            }
        }
    }

    private fun readCached(disk: DiskCache, entry: Entry): Asset? {
        val pointer = disk.openSnapshot(pointerKey(entry.diskKey))?.use { snapshot ->
            runCatching {
                if ((disk.fileSystem.metadata(snapshot.data).size ?: Long.MAX_VALUE) > MAX_INDEX_BYTES) return@runCatching null
                json.decodeFromString<Pointer>(disk.fileSystem.read(snapshot.data) { readUtf8() })
            }.getOrNull()
        } ?: return null
        if (entry.request.reloadToken != 0L && pointer.reloadToken != entry.request.reloadToken) return null
        val snapshot = disk.openSnapshot(pointer.revision) ?: return null
        val valid = runCatching {
            pointer.info.cacheable && pointer.info.sizeBytes > 0 &&
                disk.fileSystem.metadata(snapshot.data).size == pointer.info.sizeBytes
        }.getOrDefault(false)
        if (!valid) {
            snapshot.close()
            disk.remove(pointer.revision)
            disk.remove(pointerKey(entry.diskKey))
            return null
        }
        return Asset(snapshot, disk, pointer.info, pointer.revision, fromCache = true)
    }

    private fun ensureCurrentLocked(entry: Entry) {
        if (closed.value || entry.retired || entry.generation != generation || (entry.users == 0 && !entry.lingering)) {
            throw CancellationException("Original media request invalidated")
        }
    }

    private fun retireLocked(entry: Entry) {
        entry.retired = true
        entry.lingering = false
        if (active[entry.key] === entry) active.remove(entry.key)
        if (entry.asset == null) entry.job.cancel()
    }

    private suspend fun release(entry: Entry) = mutex.withLock {
        check(entry.users > 0)
        entry.users--
        if (entry.users == 0) {
            if (shouldLingerLocked(entry)) lingerLocked(entry)
            else {
                retireLocked(entry)
                disposeIfUnusedLocked(entry)
            }
        }
    }

    private fun shouldLingerLocked(entry: Entry) = playbackLingerMillis > 0 && entry.playbackRequested.value &&
        entry.asset == null && !entry.finished && !entry.retired && !closed.value &&
        entry.generation == generation && active[entry.key] === entry

    private fun lingerLocked(entry: Entry) {
        entry.lingering = true
        val serial = ++entry.lingerSerial
        scope.launch {
            kotlinx.coroutines.delay(playbackLingerMillis)
            mutex.withLock {
                if (entry.lingering && entry.lingerSerial == serial && entry.users == 0) {
                    retireLocked(entry)
                    disposeIfUnusedLocked(entry)
                }
            }
        }
    }

    private fun disposeIfUnusedLocked(entry: Entry) {
        if (entry.users == 0 && entry.finished) {
            // A lingering download ended with nobody attached; later requests start afresh
            // (a committed original is then read back from the disk cache).
            if (entry.lingering) retireLocked(entry)
            closeReadHandleLocked(entry)
            val asset = entry.asset
            entry.asset = null
            if (asset != null) {
                cleanupIo("close original media snapshot") { asset.snapshot.close() }
                if (!asset.info.cacheable) {
                    cleanupIo("remove transient original media") { asset.disk.remove(asset.identity) }
                }
            }
            live.remove(entry)
        }
        finishShutdownLocked()
    }

    private fun closeReadHandleLocked(entry: Entry) {
        val handle = entry.readHandle ?: return
        // Detach before closing: a failed close must not leave an owned handle or be retried.
        entry.readHandle = null
        cleanupIo("close original media reader") { handle.close() }
    }

    private inline fun cleanupIo(operation: String, block: () -> Unit): Boolean =
        try {
            block()
            true
        } catch (failure: IOException) {
            // Cleanup runs in standalone coroutines; an I/O error must not crash the app
            // or prevent releasing the remaining resources and completing shutdown.
            Logger.e("OriginalMediaStore", "Failed to $operation", failure)
            false
        }

    /** Does not delete files pinned by a decoder until its lease is released. */
    override suspend fun clear() = withContext(dispatcher) {
        val disk = cache.await()
        mutex.withLock {
            check(!closed.value)
            generation++
            active.values.toList().forEach(::retireLocked)
            disk.clear()
        }
    }

    override suspend fun sizeBytes(): Long = withContext(dispatcher) { cache.await().size }

    override fun close() {
        if (!closed.compareAndSet(expect = false, update = true)) return
        cleanupScope.launch {
            mutex.withLock {
                generation++
                active.values.toList().forEach(::retireLocked)
                finishShutdownLocked()
            }
        }
    }

    /**
     * Closes and waits until every lease is returned. The wait is required: a
     * decoder may still read the files. [onStillWaiting] runs every
     * [reportIntervalMillis] so a long playback or export can be told apart
     * from a lease that is never returned.
     */
    suspend fun closeAndAwait(
        reportIntervalMillis: Long = DEFAULT_SHUTDOWN_REPORT_INTERVAL_MILLIS,
        onStillWaiting: suspend (OriginalMediaShutdownReport) -> Unit = {}
    ) {
        close()
        while (withTimeoutOrNull(reportIntervalMillis) { shutdown.await() } == null) {
            onStillWaiting(pendingShutdownReport())
        }
    }

    internal suspend fun pendingShutdownReport(): OriginalMediaShutdownReport = mutex.withLock {
        OriginalMediaShutdownReport(
            liveEntries = live.size,
            playbackEntries = live.count { it.playbackRequested.value },
            oldestAgeMillis = live.maxOfOrNull { it.createdAt.elapsedNow().inWholeMilliseconds } ?: 0L
        )
    }

    private fun finishShutdownLocked() {
        if (!closed.value || live.isNotEmpty() || cacheClosed) return
        cacheClosed = true
        cleanupScope.launch {
            try {
                if (cache.isActive || cache.isCompleted) runCatching { cache.await().shutdown() }
            } finally {
                scope.cancel()
                shutdown.complete(Unit)
            }
        }
    }

    private fun pointerKey(key: String) = "original-media-v1-$cacheIdentity-$key"

    companion object {
        internal const val DEFAULT_SHUTDOWN_REPORT_INTERVAL_MILLIS = 30_000L
        internal const val DEFAULT_PLAYBACK_LINGER_MILLIS = 60_000L
        private const val MAX_SUPERSEDED_REJOINS = 3
        private const val MAX_INDEX_BYTES = 32 * 1024
        private const val MAX_READ_BYTES = 2 * 1024 * 1024
    }
}
