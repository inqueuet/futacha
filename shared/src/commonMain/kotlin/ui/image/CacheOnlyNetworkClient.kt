@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)
package com.valoser.futacha.shared.ui.image

import coil3.network.NetworkClient
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse

/**
 * A cache-only image request (network policy disabled, or offline) found no
 * usable disk-cache entry. This is an answer, not a transient failure: it is
 * never retried and is not evidence that the URL is missing.
 */
internal class ImageNotCachedException : Exception("Image is not cached")

/**
 * Coil expresses "read the disk cache only" as `Cache-Control: only-if-cached`
 * and relies on the HTTP client to honour it. OkHttp answers with a local 504
 * (which the retry strategy then waited on), and Darwin ignores the header and
 * downloads the image. Neither may touch the network, so answer here.
 */
internal class CacheOnlyAwareNetworkClient(private val delegate: NetworkClient) : NetworkClient {
    override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T {
        if (isCacheOnlyImageRequest(request)) throw ImageNotCachedException()
        return delegate.executeRequest(request, block)
    }
}

internal fun isCacheOnlyImageRequest(request: NetworkRequest): Boolean =
    request.headers.getAll("Cache-Control").any { value ->
        value.split(',').any { it.trim().equals("only-if-cached", ignoreCase = true) }
    }
