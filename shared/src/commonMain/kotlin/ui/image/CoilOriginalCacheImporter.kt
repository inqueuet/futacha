@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)
package com.valoser.futacha.shared.ui.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.fetch.SourceFetchResult
import coil3.network.NetworkClient
import coil3.network.NetworkFetcher
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.CachePolicy
import coil3.request.Options
import coil3.toUri
import com.valoser.futacha.shared.media.source.OriginalMediaCacheImporter
import com.valoser.futacha.shared.media.source.OriginalMediaInfo
import com.valoser.futacha.shared.media.source.OriginalMediaRequest
import kotlinx.coroutines.CancellationException
import okio.BufferedSink

/** Uses Coil's own response metadata reader; no dependency on its disk serialization format. */
internal class CoilOriginalCacheImporter(
    private val context: PlatformContext,
    private val loader: ImageLoader
) : OriginalMediaCacheImporter {
    private val factory = NetworkFetcher.Factory(
        networkClient = { object : NetworkClient {
            override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T =
                error("Legacy original import must never access the network")
        } },
        cacheStrategy = { ImageCacheStrategy }
    )

    override suspend fun copyCached(request: OriginalMediaRequest, sink: BufferedSink): OriginalMediaInfo? {
        (loader as? StableImageLoader)?.awaitDiskCacheInitialization()
        if (loader.diskCache == null || !isSharedOriginalImageUrl(request.url)) return null
        // Legacy Coil URL keys did not partition credentials or negotiated representations.
        if (request.headers.keys.any { !it.equals("Referer", ignoreCase = true) }) return null
        val result = try {
            factory.create(request.url.toUri(), Options(
                context = context,
                diskCachePolicy = CachePolicy.READ_ONLY,
                networkCachePolicy = CachePolicy.DISABLED
            ), loader)?.fetch() as? SourceFetchResult
        } catch (failure: CancellationException) { throw failure }
        catch (_: Exception) { return null }
        result ?: return null
        result.source.use { source ->
            val size = source.source().readAll(sink)
            return OriginalMediaInfo(result.mimeType, size, resolvedUrl = request.url)
        }
    }
}
