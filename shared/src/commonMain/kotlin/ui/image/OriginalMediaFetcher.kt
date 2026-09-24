package com.valoser.futacha.shared.ui.image

import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.network.httpBody
import coil3.network.httpHeaders
import coil3.network.httpMethod
import coil3.request.Options
import com.valoser.futacha.shared.media.source.OriginalMediaRequest
import com.valoser.futacha.shared.media.source.OriginalMediaSource
import com.valoser.futacha.shared.media.source.OriginalMediaCacheUnavailable
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.IOException

/** A confirmed original URL, distinct from a thumbnail or a guessed extension. */
class OriginalMediaRef(request: OriginalMediaRequest) {
    val request: OriginalMediaRequest = request.copy(headers = request.headers.toMap())

    // Do not put headers, query strings or credentials in logs/debug descriptions.
    override fun toString(): String = "OriginalMediaRef(${request.cacheKey()})"
}

/** Both catalog and viewer loaders must register the SAME store instance. */
fun ComponentRegistry.Builder.addOriginalMediaSupport(store: OriginalMediaSource) = apply {
    add(OriginalMediaKeyer(store.cacheIdentity))
    add(OriginalMediaFetcher.Factory(store))
    add(SharedOriginalUriFetcherFactory(store))
}

/** Only already requested public originals; thumbnail requests never cause an upgrade. */
internal fun isSharedOriginalImageUrl(value: String): Boolean =
    com.valoser.futacha.shared.media.source.isSharedOriginalImageUrl(value)

internal class SharedOriginalUriFetcherFactory(private val store: OriginalMediaSource) : Fetcher.Factory<Uri> {
    override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
        if (!isSharedOriginalImageUrl(data.toString())) return null
        val original = OriginalMediaFetcher.Factory(store).create(
            OriginalMediaRef(OriginalMediaRequest(data.toString())), options, imageLoader
        )
        return Fetcher {
            try { original.fetch() }
            catch (_: OriginalMediaCacheUnavailable) {
                // Preserve memory-only image display when the filesystem is unavailable.
                // This happens before any original HTTP request; metadata must report
                // source unavailable instead of downloading a second copy independently.
                null
            }
        }
    }
}

internal class OriginalMediaKeyer(private val cacheIdentity: String) : Keyer<OriginalMediaRef> {
    override fun key(data: OriginalMediaRef, options: Options): String {
        val request = effectiveRequest(data, options)
        return "original-media:$cacheIdentity:${request.cacheKey()}:${request.reloadToken}"
    }
}

internal class OriginalMediaFetcher(
    private val store: OriginalMediaSource,
    private val request: OriginalMediaRequest
) : Fetcher {
    override suspend fun fetch(): SourceFetchResult {
        val lease = try {
            store.acquire(request)
        } catch (cancelled: CancellationException) {
            // Coil rethrows cancellation without an error result, which would
            // leave the image loading forever. If this request itself is still
            // wanted, report the lost download as an ordinary failure.
            currentCoroutineContext().ensureActive()
            throw IOException("Original media request was cancelled by another operation", cancelled)
        }
        try {
            return SourceFetchResult(
                source = ImageSource(lease.file, lease.fileSystem, closeable = lease),
                mimeType = lease.info.mimeType,
                dataSource = if (lease.fromCache) DataSource.DISK else DataSource.NETWORK
            )
        } catch (failure: Throwable) {
            lease.close()
            throw failure
        }
    }

    class Factory(private val store: OriginalMediaSource) : Fetcher.Factory<OriginalMediaRef> {
        override fun create(data: OriginalMediaRef, options: Options, imageLoader: ImageLoader): Fetcher {
            require(options.httpMethod == "GET" && options.httpBody == null) { "Original media requires GET" }
            val request = effectiveRequest(data, options)
            val reload = if (!options.diskCachePolicy.readEnabled && request.reloadToken == 0L) {
                Random.nextLong().takeUnless { it == 0L } ?: 1L
            } else request.reloadToken
            return OriginalMediaFetcher(store, request.copy(reloadToken = reload))
        }
    }
}

private fun effectiveRequest(data: OriginalMediaRef, options: Options): OriginalMediaRequest {
    val headers = data.request.headers.mapKeys { it.key.lowercase() }.toMutableMap()
    options.httpHeaders.asMap().forEach { (name, values) ->
        require(values.size == 1) { "Original media request headers require one value per name" }
        headers[name.lowercase()] = values.single()
    }
    return data.request.copy(
        headers = headers,
        reloadToken = options.imageRefreshOperation.takeUnless { it == 0L } ?: data.request.reloadToken,
        allowNetwork = data.request.allowNetwork && options.networkCachePolicy.readEnabled,
        persist = data.request.persist && options.diskCachePolicy.writeEnabled
    )
}
