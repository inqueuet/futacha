package com.valoser.futacha.shared.media.source

import coil3.disk.DiskCache
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path

data class OriginalMediaCacheConfiguration(val directory: Path, val maxBytes: Long) {
    init { require(maxBytes > 0) }
}

fun interface OriginalMediaCacheImporter {
    /** Return null only before writing to sink. HTTP is forbidden in this operation. */
    suspend fun copyCached(request: OriginalMediaRequest, sink: okio.BufferedSink): OriginalMediaInfo?
}

/** Lives with the host's HTTP client, across Activity and Compose mode recreation. */
class OriginalMediaSession internal constructor(
    override val cacheIdentity: String,
    private val downloader: OriginalMediaDownloader,
    private val closeDownloader: () -> Unit = {},
    private val createCache: suspend (OriginalMediaCacheConfiguration) -> DiskCache = {
        DiskCache.Builder().directory(it.directory).maxSizeBytes(it.maxBytes).build()
    },
    private val dispatcher: CoroutineDispatcher = AppDispatchers.io
) : OriginalMediaSource, AutoCloseable {
    private data class Ready(val configuration: OriginalMediaCacheConfiguration, val store: OriginalMediaStore)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val requested = MutableStateFlow<OriginalMediaCacheConfiguration?>(null)
    private val ready = MutableStateFlow<Ready?>(null)
    private val closed = MutableStateFlow(false)
    private val shutdown = CompletableDeferred<Unit>()
    internal val shutdownSignal: Deferred<Unit> get() = shutdown
    private val transition = Mutex()
    private val importers = MutableStateFlow<List<OriginalMediaCacheImporter>>(emptyList())
    private val sharedDownloader = object : OriginalMediaDownloader {
        override suspend fun download(request: OriginalMediaRequest, sink: okio.BufferedSink) = downloader.download(request, sink)
        override suspend fun download(request: OriginalMediaRequest, sink: okio.BufferedSink, onHeaders: (OriginalMediaInfo) -> Unit) =
            downloader.download(request, sink, onHeaders)
        override suspend fun retryAfter(retry: Int, failure: Throwable) = downloader.retryAfter(retry, failure)
        override suspend fun downloadCached(request: OriginalMediaRequest, sink: okio.BufferedSink): OriginalMediaInfo? {
            if (request.reloadToken != 0L) return null
            for (importer in importers.value) importer.copyCached(request, sink)?.let { return it }
            return null
        }
    }
    private val worker = scope.launch {
        requested.filterNotNull().collect { configuration ->
            transition.withLock {
                if (closed.value || ready.value?.configuration == configuration) return@withLock
                val old = ready.value
                ready.value = null
                if (old != null) {
                    withContext(NonCancellable) {
                        try {
                            // Do not leave an unaccounted second cache at the old location.
                            if (old.configuration.directory != configuration.directory) {
                                runCatching { old.store.clear() }.onFailure {
                                    Logger.e("OriginalMediaSession", "Failed to clear the previous original cache", it)
                                }
                            }
                        } finally { old.store.closeAndAwait() }
                    }
                }
                currentCoroutineContext().ensureActive()
                if (closed.value) return@withLock
                // Cache opening is lazy and happens on the store's IO dispatcher.
                ready.value = Ready(configuration, OriginalMediaStore(
                    cacheNamespace = cacheIdentity,
                    createCache = { createCache(configuration) },
                    downloader = sharedDownloader,
                    dispatcher = dispatcher
                ))
            }
        }
    }

    override fun registerCacheImporter(importer: OriginalMediaCacheImporter): AutoCloseable {
        check(!closed.value)
        importers.update { it + importer }
        return AutoCloseable { importers.update { it - importer } }
    }

    /** Publishing settings is nonblocking; callers wait for safe cache reconfiguration. */
    fun configure(configuration: OriginalMediaCacheConfiguration) {
        check(!closed.value) { "Original media session is closed" }
        requested.value = configuration
    }

    /** Background-only launches cannot wait for Compose to publish its settings.
     * A late background initializer must never override a live UI configuration. */
    fun configureIfAbsent(configuration: OriginalMediaCacheConfiguration) {
        check(!closed.value) { "Original media session is closed" }
        requested.compareAndSet(null, configuration)
    }

    override suspend fun acquire(request: OriginalMediaRequest): OriginalMediaStore.Lease {
        while (true) {
            val snapshot = combine(requested, ready, closed) { desired, current, stopped ->
                check(!stopped) { "Original media session is closed" }
                current?.takeIf { it.configuration == desired }
            }.filterNotNull().first()
            try {
                val lease = snapshot.store.acquire(request)
                if (ready.value === snapshot && requested.value == snapshot.configuration && !closed.value) return lease
                lease.close()
            } catch (failure: CancellationException) {
                currentCoroutineContext().ensureActive()
                // A configuration switch retires its store; explicit clear/caller
                // cancellation must not silently start a replacement download.
                if (ready.value === snapshot && requested.value == snapshot.configuration) throw failure
            } catch (failure: IllegalStateException) {
                if (ready.value === snapshot && requested.value == snapshot.configuration) throw failure
            }
        }
    }

    override suspend fun acquireForPlayback(request: OriginalMediaRequest): OriginalMediaPlayback {
        while (true) {
            val snapshot = combine(requested, ready, closed) { desired, current, stopped ->
                check(!stopped) { "Original media session is closed" }
                current?.takeIf { it.configuration == desired }
            }.filterNotNull().first()
            try {
                val playback = snapshot.store.acquireForPlayback(request)
                if (ready.value === snapshot && requested.value == snapshot.configuration && !closed.value) return playback
                playback.close()
            } catch (failure: CancellationException) {
                currentCoroutineContext().ensureActive()
                if (ready.value === snapshot && requested.value == snapshot.configuration) throw failure
            } catch (failure: IllegalStateException) {
                if (ready.value === snapshot && requested.value == snapshot.configuration) throw failure
            }
        }
    }

    override suspend fun clear(): Unit = withContext(dispatcher) {
        transition.withLock { ready.value?.store?.clear(); Unit }
    }

    override suspend fun sizeBytes(): Long = withContext(dispatcher) {
        transition.withLock { ready.value?.store?.sizeBytes() ?: 0L }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        CoroutineScope(NonCancellable + dispatcher).launch {
            try {
                worker.cancelAndJoin()
                transition.withLock {
                    val old = ready.value
                    ready.value = null
                    old?.store?.closeAndAwait()
                }
            } finally {
                importers.value = emptyList()
                try { closeDownloader() } finally { scope.cancel(); shutdown.complete(Unit) }
            }
        }
    }

    suspend fun closeAndAwait() { close(); shutdown.await() }
}

/** Shared public Futaba assets; private/credential-specific sources need another namespace. */
fun createOriginalMediaSession(client: HttpClient, beforeOpenCache: suspend () -> Unit = {}): OriginalMediaSession {
    val transport = lazy { KtorOriginalMediaDownloader(client, maxBytes = 512L * 1024 * 1024) }
    val downloader = object : OriginalMediaDownloader {
        override suspend fun download(request: OriginalMediaRequest, sink: okio.BufferedSink) =
            transport.value.download(request, sink)
        override suspend fun download(request: OriginalMediaRequest, sink: okio.BufferedSink, onHeaders: (OriginalMediaInfo) -> Unit) =
            transport.value.download(request, sink, onHeaders)
        override suspend fun retryAfter(retry: Int, failure: Throwable) = transport.value.retryAfter(retry, failure)
    }
    return OriginalMediaSession(
        cacheIdentity = "futaba-public-originals-v1",
        downloader = downloader,
        closeDownloader = { if (transport.isInitialized()) transport.value.close() },
        createCache = {
            beforeOpenCache()
            DiskCache.Builder().directory(it.directory).maxSizeBytes(it.maxBytes).build()
        }
    )
}
