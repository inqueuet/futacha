package com.valoser.futacha.shared.ui.image

import coil3.Extras
import coil3.ImageLoader
import coil3.getExtra
import coil3.intercept.Interceptor
import coil3.request.ImageRequest
import coil3.request.ImageResult
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val PrefetchRequest = Extras.Key(false)
private val prefetchPermit = Semaphore(1)

/** Counts consumers, not decoded images: Coil still owns size/key-specific caching. */
internal object VisibleImageRequests {
    val counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    suspend fun <T> track(url: String, block: suspend () -> T): T {
        counts.update { it + (url to ((it[url] ?: 0) + 1)) }
        try { return block() } finally {
            counts.update { current ->
                val remaining = (current[url] ?: 1) - 1
                if (remaining == 0) current - url else current + (url to remaining)
            }
        }
    }
}

internal class VisibleImageRequestInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult =
        if (chain.request.getExtra(PrefetchRequest)) chain.proceed()
        else VisibleImageRequests.track(chain.request.data.toString()) { chain.proceed() }
}

/** A bounded session. Closing drops unused work, but lets a visible consumer finish. */
internal class ImagePrefetcher(private val imageLoader: ImageLoader) {
    private data class Key(val url: String, val memoryKey: String?, val size: coil3.size.Size)
    private data class Desired(val requests: Map<Key, ImageRequest>, val currentUrl: String?, val closed: Boolean = false)
    private data class Entry(val job: Job)
    private val scope = CoroutineScope(SupervisorJob() + AppDispatchers.io)
    private val desired = MutableStateFlow(Desired(emptyMap(), null))
    private val completed = MutableStateFlow(0L)

    init {
        scope.launch {
            val entries = mutableMapOf<Key, Entry>()
            try {
                combine(desired, VisibleImageRequests.counts, completed) { target, visible, _ -> target to visible }
                    .collect { (target, visible) ->
                        val obsolete = entries.keys.filter { key ->
                            key !in target.requests && key.url != target.currentUrl && (visible[key.url] ?: 0) == 0
                        }
                        obsolete.forEach { key -> entries.remove(key)?.job?.cancel() }
                        target.requests.forEach { (key, request) ->
                            if (key !in entries) {
                                val job = scope.launch(start = CoroutineStart.LAZY) {
                                    try {
                                        prefetchPermit.withPermit {
                                            imageLoader.execute(request.newBuilder().apply { extras[PrefetchRequest] = true }.build())
                                        }
                                    } finally { completed.update { it + 1 } }
                                }
                                entries[key] = Entry(job)
                                job.start()
                            }
                        }
                        if (target.closed && entries.isEmpty()) scope.cancel()
                    }
            } finally { entries.values.forEach { it.job.cancel() } }
        }
    }

    suspend fun update(requests: List<ImageRequest>, currentUrl: String?) {
        val candidates = requests.take(2).associateBy { Key(it.data.toString(), it.memoryCacheKey, it.sizeResolver.size()) }
        desired.update { previous ->
            if (previous.closed) previous else Desired(
                candidates,
                currentUrl
            )
        }
    }

    fun close() { desired.value = Desired(emptyMap(), null, closed = true) }
}
