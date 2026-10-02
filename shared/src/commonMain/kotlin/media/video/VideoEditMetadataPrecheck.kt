package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.prompt.*
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.*
import okio.FileHandle
import okio.Path.Companion.toPath
import okio.use

/**
 * Reads the owned source's generation record with the same rules the post-export writer uses,
 * so a source whose tags cannot be preserved (an incomplete scan such as a browser-recorded WebM
 * with unknown-length clusters, a broken or duplicated record) is rejected before encoding up to
 * 1GB instead of after. The record must also be appendable within the writer's limits (B-7): a long record
 * that the source scan accepts but the post-export check could not read back is rejected here instead.
 * The source is opened read-only and is not modified.
 */
internal suspend fun requirePreservableVideoMetadata(input: String): PreservedVideoMetadata = withContext(AppDispatchers.io) {
    okio.FileSystem.SYSTEM.openReadOnly(input.toPath(normalize = true)).use { handle ->
        PreservedVideoMetadata.fromScan(VideoMetadataReader(VIDEO_PRESERVATION_SCAN_BUDGET).read(handle.size()) { offset, count ->
            currentCoroutineContext().ensureActive()
            handle.readPrecheckBytes(offset, count)
        })
    }.also { requireWritableVideoMetadata(it) }
}

private fun FileHandle.readPrecheckBytes(offset: Long, count: Int): ByteArray {
    val bytes = ByteArray(count)
    var position = 0
    while (position < count) {
        val read = read(offset + position, bytes, position, count - position)
        check(read > 0) { "動画のメタデータが途中で終わっています" }
        position += read
    }
    return bytes
}
