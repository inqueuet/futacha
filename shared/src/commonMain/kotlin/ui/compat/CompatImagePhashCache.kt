package com.valoser.futacha.shared.ui.compat

import coil3.ImageLoader
import com.valoser.futacha.shared.media.source.OriginalMediaRequest
import com.valoser.futacha.shared.media.source.originalMediaSourceOrNull
import com.valoser.futacha.shared.ui.image.isSharedOriginalImageUrl
import com.valoser.futacha.shared.util.AppDispatchers
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Reuse the exact cached bytes, never a resized memory bitmap or an unrelated image. */
internal suspend fun readCompatCachedImageBytes(
    client: HttpClient,
    loader: ImageLoader?,
    url: String,
    maxBytes: Int
): ByteArray? = withContext(AppDispatchers.io) {
    if (isSharedOriginalImageUrl(url)) {
        val cached = try {
            client.originalMediaSourceOrNull()?.acquire(OriginalMediaRequest(url, allowNetwork = false))?.use { lease ->
                if (lease.info.sizeBytes in 1..maxBytes.toLong()) lease.readAt(0, lease.info.sizeBytes.toInt()) else null
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        if (cached != null) return@withContext cached
    }
    try {
        val disk = loader?.diskCache ?: return@withContext null
        disk.openSnapshot(url)?.use { snapshot ->
            val size = disk.fileSystem.metadata(snapshot.data).size ?: return@use null
            if (size !in 1..maxBytes.toLong()) return@use null
            // A snapshot pins immutable data; its checked size cannot grow while reading it.
            disk.fileSystem.read(snapshot.data) { readByteArray() }
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null }
}
