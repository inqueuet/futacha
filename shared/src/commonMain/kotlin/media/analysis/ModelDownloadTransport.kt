package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.download.*
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import okio.BufferedSink
import okio.Path

internal fun HttpClientConfig<*>.configureModelDownloads() = configureAssetDownloads()

internal class KtorModelDownloader(client: HttpClient = createModelDownloadClient()) : ModelDownloader, AutoCloseable {
    private val delegate = KtorAssetDownloader(client)
    override val supportsResume: Boolean get() = delegate.supportsResume
    override suspend fun download(distribution: ModelDistribution, sink: BufferedSink) =
        delegate.download(distribution.asset(), sink)
    override suspend fun resumeDownload(distribution: ModelDistribution, sink: BufferedSink, offset: Long) =
        delegate.resumeDownload(distribution.asset(), sink, offset)
    override fun close() = delegate.close()
    private fun ModelDistribution.asset() = AssetDistribution(url, bytes, sha256)
}
internal typealias ModelResumeUnavailableException = AssetResumeUnavailableException
internal expect fun createModelDownloadClient(): HttpClient
internal expect fun modelStoreDirectory(platformContext: Any?): Path
