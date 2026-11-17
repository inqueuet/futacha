package com.valoser.futacha.shared.ui.board

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.valoser.futacha.shared.util.Logger
import java.io.File

internal const val VIDEO_PLAYBACK_CACHE_DIRECTORY = "futacha_video_playback_cache"
internal const val VIDEO_PLAYBACK_CACHE_MAX_BYTES = 96L * 1024L * 1024L

/**
 * Process-wide Media3 byte cache used only by Android video playback.
 *
 * [SimpleCache] exclusively locks its directory, so ownership must not follow a
 * viewer's Compose lifecycle. Keeping one application-scoped owner also lets a
 * reopened MP4/WebM reuse the same immutable Futaba media bytes.
 */
@UnstableApi
object AndroidVideoPlaybackCache {
    private val lock = Any()

    @Volatile
    private var cache: SimpleCache? = null

    @Volatile
    private var initializationFailed = false

    fun initialize(context: Context) {
        getOrCreate(context.applicationContext)
    }

    fun createDataSourceFactory(context: Context): DataSource.Factory {
        val appContext = context.applicationContext
        val upstream = DefaultDataSource.Factory(appContext)
        val activeCache = getOrCreate(appContext) ?: return upstream
        val cached = CacheDataSource.Factory()
            .setCache(activeCache)
            .setUpstreamDataSourceFactory(upstream)
            // A damaged or externally cleared cache must never prevent playback.
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return RemoteOnlyVideoDataSourceFactory(upstream, cached)
    }

    private fun getOrCreate(context: Context): SimpleCache? {
        cache?.let { return it }
        if (initializationFailed) return null
        return synchronized(lock) {
            cache?.let { return@synchronized it }
            if (initializationFailed) return@synchronized null
            runCatching {
                val directory = File(context.cacheDir, VIDEO_PLAYBACK_CACHE_DIRECTORY)
                SimpleCache(
                    directory,
                    LeastRecentlyUsedCacheEvictor(VIDEO_PLAYBACK_CACHE_MAX_BYTES),
                    StandaloneDatabaseProvider(context)
                )
            }.onSuccess { created ->
                cache = created
            }.onFailure { error ->
                initializationFailed = true
                Logger.w(
                    "AndroidVideoPlaybackCache",
                    "Video playback cache unavailable; using direct playback: " +
                        error::class.simpleName.orEmpty()
                )
            }.getOrNull()
        }
    }
}

/** Keeps file/content assets on Media3's original direct path and caches only HTTP media. */
@UnstableApi
private class RemoteOnlyVideoDataSourceFactory(
    private val directFactory: DataSource.Factory,
    private val cachedFactory: DataSource.Factory
) : DataSource.Factory {
    override fun createDataSource(): DataSource = RemoteOnlyVideoDataSource(
        direct = directFactory.createDataSource(),
        cached = cachedFactory.createDataSource()
    )
}

@UnstableApi
private class RemoteOnlyVideoDataSource(
    private val direct: DataSource,
    private val cached: DataSource
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        direct.addTransferListener(transferListener)
        cached.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(active == null) { "Video data source is already open" }
        active = if (dataSpec.uri.scheme.equals("http", ignoreCase = true) ||
            dataSpec.uri.scheme.equals("https", ignoreCase = true)
        ) {
            cached
        } else {
            direct
        }
        return requireNotNull(active).open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        requireNotNull(active) { "Video data source is not open" }.read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        active?.responseHeaders.orEmpty()

    override fun close() {
        val opened = active ?: return
        active = null
        opened.close()
    }
}
