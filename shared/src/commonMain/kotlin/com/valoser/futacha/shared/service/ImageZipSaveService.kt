package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.isSupportedMediaSaveSource
import com.valoser.futacha.shared.util.withMediaSaveSource
import com.valoser.futacha.shared.media.source.OriginalMediaSource
import com.valoser.futacha.shared.media.source.originalMediaSourceOrNull
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock

data class ImageZipSaveResult(
    val fileName: String,
    val savedItems: Int,
    val failedItems: Int,
    val byteSize: Long,
    val failedUrls: List<String> = emptyList()
)

/**
 * Writes a streaming ZIP without holding large media in memory. Entries use the
 * DEFLATE method with uncompressed (stored) blocks: STORED entries with a data
 * descriptor cannot be read by java.util.zip.ZipInputStream, while this keeps
 * the data uncompressed and the archive streamable.
 */
class ImageZipSaveService(
    private val httpClient: HttpClient,
    private val fileSystem: FileSystem,
    private val originalMediaSource: OriginalMediaSource? = httpClient.originalMediaSourceOrNull()
) {
    suspend fun save(
        mediaUrls: List<String>,
        boardId: String,
        threadId: String,
        baseSaveLocation: SaveLocation? = null,
        baseDirectory: String = MANUAL_SAVE_DIRECTORY,
        fileNameSuffix: String? = null,
        onProgress: (
            current: Int,
            total: Int,
            currentItem: String,
            currentItemBytes: Long,
            currentItemTotalBytes: Long
        ) -> Unit = { _, _, _, _, _ -> }
    ): Result<ImageZipSaveResult> = withContext(AppDispatchers.io) {
        try {
            sweepStaleZipStagingFiles(fileSystem)
            val urls = mediaUrls.map(String::trim)
                .filter(::isSupportedMediaSaveSource)
                .distinct()
            require(urls.isNotEmpty()) { "保存するメディアがありません" }
            val suffix = fileNameSuffix?.let(::safeSegment)?.takeIf { it.isNotBlank() }
            val fileName = buildString {
                append(safeSegment(boardId)).append('_').append(safeSegment(threadId))
                if (suffix != null) append('_').append(suffix)
                append("_media.zip")
            }
            val destination = fileSystem.resolveSavedFile(baseSaveLocation ?: SaveLocation.Path(baseDirectory), fileName).getOrNull()
            require(urls.none { com.valoser.futacha.shared.util.localMediaSavePath(it) == destination }) {
                "元のファイルとZIP保存先が同じです"
            }
            val relativePath = fileName
            var savedItems = 0
            var failedItems = 0
            val failedUrls = mutableListOf<String>()
            var byteSize = 0L
            val writer: suspend (com.valoser.futacha.shared.util.FileWriteSink) -> Unit = { sink ->
                val zip = StreamingStoredZipWriter(sink)
                val usedNames = mutableSetOf<String>()
                urls.forEachIndexed { index, url ->
                    coroutineContext.ensureActive()
                    val requestedName = safeMediaName(url, index)
                    onProgress(index, urls.size, requestedName, 0L, 0L)
                    val entryName = uniqueName(requestedName, usedNames)
                    var entryStarted = false
                    val stagingPath = ZipStaging.newPath(index)
                    try {
                        var declaredSize = 0L
                        var currentBytes = 0L
                        // Validate a complete source before emitting its ZIP local header.
                        fileSystem.createDirectory(ZIP_STAGING_DIRECTORY).getOrThrow()
                        withMediaSaveSource(httpClient, fileSystem, url, originalMediaSource) { source ->
                            require(source.declaredSize <= MAX_ZIP_ENTRY_BYTES) { "ファイルが大きすぎます" }
                            declaredSize = source.declaredSize
                            fileSystem.writeByteStream(stagingPath) { staged ->
                                val buffer = ByteArray(512 * 1024)
                                while (true) {
                                    val read = withTimeoutOrNull(READ_IDLE_TIMEOUT_MILLIS) { source.read(buffer) }
                                        ?: error("メディアの読み込みがタイムアウトしました")
                                    if (read < 0) break
                                    if (read == 0) continue
                                    require(read.toLong() <= MAX_ZIP_ENTRY_BYTES - currentBytes) { "ファイルが大きすぎます" }
                                    staged.write(buffer, 0, read)
                                    currentBytes += read
                                    onProgress(index, urls.size, requestedName, currentBytes, declaredSize)
                                }
                                check(currentBytes > 0) { "メディアが空です" }
                                check(declaredSize <= 0 || currentBytes == declaredSize) {
                                    "ファイルを最後まで読み込めませんでした"
                                }
                            }.getOrThrow()
                        }
                        entryStarted = true
                        fileSystem.readByteStream(stagingPath) { source ->
                            zip.writeEntry(entryName, MAX_ZIP_ENTRY_BYTES, verify = {
                                check(it == currentBytes) { "ファイルを最後まで読み込めませんでした" }
                            }) {
                                source.read(it, 0, it.size)
                            }
                        }.getOrThrow()
                        savedItems += 1
                        onProgress(index + 1, urls.size, requestedName, currentBytes, declaredSize)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        // Once a validated entry starts, any local/sink failure aborts
                        // the replacement archive; a truncated local entry is never published.
                        if (entryStarted) throw failure
                        failedItems += 1
                        failedUrls += url
                    } finally {
                        withContext(kotlinx.coroutines.NonCancellable) {
                            fileSystem.delete(stagingPath)
                            ZipStaging.release(stagingPath)
                        }
                    }
                }
                require(savedItems > 0) { "メディアを保存できませんでした" }
                byteSize = zip.finish()
            }
            val location = baseSaveLocation ?: SaveLocation.Path(baseDirectory).also {
                fileSystem.createDirectory(baseDirectory).getOrThrow()
            }
            // The file name is fixed per thread, so a re-save must not destroy the
            // previous archive when this one fails or is cancelled midway.
            val savedPath = fileSystem.writeByteStreamReplacing(location, relativePath, writer).getOrThrow()
            Result.success(ImageZipSaveResult(savedPath, savedItems, failedItems, byteSize, failedUrls))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Result.failure(failure)
        }
    }

    private fun safeSegment(value: String): String = value.replace(UNSAFE_FILE_NAME, "_")
        .trim('_').take(64).ifBlank { "thread" }

    private fun safeMediaName(url: String, index: Int): String {
        val raw = url.substringBefore('#').substringBefore('?').substringAfterLast('/')
        return raw.replace(UNSAFE_FILE_NAME, "_").trim('_').take(120)
            .ifBlank { "image_${index + 1}.bin" }
    }

    private fun uniqueName(name: String, used: MutableSet<String>): String {
        if (used.add(name)) return name
        val stem = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
        var suffix = 1
        while (!used.add("$stem($suffix)$extension")) suffix += 1
        return "$stem($suffix)$extension"
    }

    private companion object {
        val UNSAFE_FILE_NAME = Regex("[^A-Za-z0-9._-]")
        const val MAX_ZIP_ENTRY_BYTES = 512L * 1024L * 1024L
        const val READ_IDLE_TIMEOUT_MILLIS = 30_000L
    }
}

internal const val ZIP_STAGING_DIRECTORY = "private/zip-staging"

/**
 * Staged entries are deleted in `finally`, but a force-killed save leaves its
 * file (up to 512MB) behind. Names carry the creation time so a later save can
 * tell leftovers of an earlier process from files this process still writes.
 */
private object ZipStaging {
    val processStartEpochMillis: Long = Clock.System.now().toEpochMilliseconds()
    val mutex = Mutex()
    val active = mutableSetOf<String>()

    suspend fun newPath(index: Int): String {
        val now = Clock.System.now().toEpochMilliseconds()
        val path = "$ZIP_STAGING_DIRECTORY/t$now-${kotlin.random.Random.nextLong().toULong()}-$index.media"
        mutex.withLock { active += path.substringAfterLast('/') }
        return path
    }

    suspend fun release(path: String) {
        mutex.withLock { active -= path.substringAfterLast('/') }
    }
}

/**
 * Whether [name] in the staging folder is a leftover: not written by a live save
 * of this process and created before it started (or in the older unstamped format).
 */
internal fun isStaleZipStagingFile(name: String, processStartEpochMillis: Long, active: Set<String>): Boolean {
    if (name.isBlank() || name in active) return false
    if (!name.startsWith("t")) return true
    val stamp = name.substring(1).substringBefore('-').toLongOrNull() ?: return true
    return stamp < processStartEpochMillis
}

/** Removes staged entries left by a save that was killed; never fails the caller. */
internal suspend fun sweepStaleZipStagingFiles(fileSystem: FileSystem) {
    try {
        ZipStaging.mutex.withLock {
            fileSystem.listFiles(ZIP_STAGING_DIRECTORY)
                .map { it.trim().trimEnd('/').substringAfterLast('/') }
                .filter { isStaleZipStagingFile(it, ZipStaging.processStartEpochMillis, ZipStaging.active) }
                .forEach { fileSystem.delete("$ZIP_STAGING_DIRECTORY/$it") }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        // A failed sweep only delays reclaiming space; the save itself proceeds.
        com.valoser.futacha.shared.util.Logger.w("ImageZipSave", "Could not remove stale staging files: ${failure.message}")
    }
}

private data class StoredZipEntry(
    val name: ByteArray,
    val crc: Long,
    val compressedSize: Long,
    val size: Long,
    val localOffset: Long
)

/** The entry's source failed; its bytes were closed off but it is not listed in the archive. */
internal class ZipEntrySkippedException(cause: Throwable) : Exception(cause.message, cause)

private fun Throwable.isZipEntrySkipped(): Boolean {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < 8) {
        if (current is ZipEntrySkippedException) return true
        current = current.cause
        depth++
    }
    return false
}

private class StreamingStoredZipWriter(
    private val sink: com.valoser.futacha.shared.util.FileWriteSink
) {
    private val entries = mutableListOf<StoredZipEntry>()
    private var written = 0L
    private val block = ByteArray(MAX_STORED_BLOCK_BYTES)
    private var blockLength = 0

    /**
     * Streams one entry. When [read] or [verify] fails, the deflate stream and
     * data descriptor are still completed (so streaming readers can step over
     * it) but the entry is left out of the central directory and
     * [ZipEntrySkippedException] is thrown. Sink failures propagate as is.
     */
    suspend fun writeEntry(
        name: String,
        maxEntryBytes: Long,
        verify: (size: Long) -> Unit = {},
        read: suspend (ByteArray) -> Int
    ): Long {
        val nameBytes = name.encodeToByteArray()
        require(nameBytes.size <= 0xffff) { "ファイル名が長すぎます" }
        require(written <= UINT_MAX) { "ZIPサイズが4GBを超えています" }
        val offset = written
        writeInt(LOCAL_HEADER_SIGNATURE)
        writeShort(20)
        writeShort(DATA_DESCRIPTOR_FLAG)
        writeShort(DEFLATED_METHOD)
        writeShort(0); writeShort(0)
        writeInt(0); writeInt(0); writeInt(0)
        writeShort(nameBytes.size); writeShort(0)
        write(nameBytes)

        val dataStart = written
        val buffer = ByteArray(512 * 1024)
        var size = 0L
        var crc = 0xffffffffL
        var failure: Throwable? = null
        blockLength = 0
        while (true) {
            val count = try {
                read(buffer)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (sourceFailure: Throwable) {
                failure = sourceFailure
                break
            }
            if (count == -1) break
            if (count == 0) continue
            if (size + count > maxEntryBytes.coerceAtMost(UINT_MAX)) {
                failure = IllegalArgumentException("ZIP内のファイルが上限を超えています")
                break
            }
            size += count
            crc = updateCrc32(crc, buffer, count)
            appendToBlocks(buffer, count)
        }
        writeStoredBlock(final = true)
        val compressedSize = written - dataStart
        val finalCrc = crc xor 0xffffffffL
        writeInt(DATA_DESCRIPTOR_SIGNATURE)
        writeInt(finalCrc)
        writeInt(compressedSize)
        writeInt(size)
        if (failure == null) {
            failure = runCatching { verify(size) }.exceptionOrNull()
        }
        failure?.let { throw ZipEntrySkippedException(it) }
        entries += StoredZipEntry(nameBytes, finalCrc, compressedSize, size, offset)
        return size
    }

    private suspend fun appendToBlocks(bytes: ByteArray, length: Int) {
        var position = 0
        while (position < length) {
            val chunk = minOf(length - position, MAX_STORED_BLOCK_BYTES - blockLength)
            bytes.copyInto(block, blockLength, position, position + chunk)
            blockLength += chunk
            position += chunk
            if (blockLength == MAX_STORED_BLOCK_BYTES) writeStoredBlock(final = false)
        }
    }

    /** RFC 1951 non-compressed block: BFINAL/BTYPE=00 byte, LEN, NLEN, data. */
    private suspend fun writeStoredBlock(final: Boolean) {
        write(byteArrayOf(if (final) 1 else 0))
        writeShort(blockLength)
        writeShort(blockLength.inv() and 0xffff)
        if (blockLength > 0) {
            sink.write(block, 0, blockLength)
            written += blockLength
        }
        blockLength = 0
    }

    suspend fun finish(): Long {
        require(entries.size <= 0xffff) { "ZIP内のファイル数が多すぎます" }
        val centralOffset = written
        entries.forEach { entry ->
            writeInt(CENTRAL_HEADER_SIGNATURE)
            writeShort(20); writeShort(20)
            writeShort(DATA_DESCRIPTOR_FLAG); writeShort(DEFLATED_METHOD)
            writeShort(0); writeShort(0)
            writeInt(entry.crc); writeInt(entry.compressedSize); writeInt(entry.size)
            writeShort(entry.name.size); writeShort(0); writeShort(0)
            writeShort(0); writeShort(0); writeInt(0)
            writeInt(entry.localOffset)
            write(entry.name)
        }
        val centralSize = written - centralOffset
        require(written <= UINT_MAX && centralSize <= UINT_MAX && centralOffset <= UINT_MAX) {
            "ZIPサイズが4GBを超えています"
        }
        writeInt(END_SIGNATURE)
        writeShort(0); writeShort(0)
        writeShort(entries.size); writeShort(entries.size)
        writeInt(centralSize); writeInt(centralOffset); writeShort(0)
        return written
    }

    private suspend fun write(bytes: ByteArray) {
        sink.write(bytes)
        written += bytes.size
    }

    private suspend fun writeShort(value: Int) = write(
        byteArrayOf((value and 0xff).toByte(), ((value ushr 8) and 0xff).toByte())
    )

    private suspend fun writeInt(value: Long) = write(
        byteArrayOf(
            (value and 0xff).toByte(),
            ((value ushr 8) and 0xff).toByte(),
            ((value ushr 16) and 0xff).toByte(),
            ((value ushr 24) and 0xff).toByte()
        )
    )

    private companion object {
        const val LOCAL_HEADER_SIGNATURE = 0x04034b50L
        const val CENTRAL_HEADER_SIGNATURE = 0x02014b50L
        const val DATA_DESCRIPTOR_SIGNATURE = 0x08074b50L
        const val END_SIGNATURE = 0x06054b50L
        const val DATA_DESCRIPTOR_FLAG = 0x0008
        const val DEFLATED_METHOD = 8
        const val MAX_STORED_BLOCK_BYTES = 0xffff
        const val UINT_MAX = 0xffffffffL
    }
}

private val CRC32_TABLE = LongArray(256) { initial ->
    var value = initial.toLong()
    repeat(8) {
        value = if ((value and 1L) != 0L) 0xedb88320L xor (value ushr 1) else value ushr 1
    }
    value
}

private fun updateCrc32(crc: Long, bytes: ByteArray, length: Int): Long {
    var value = crc
    for (index in 0 until length) {
        value = CRC32_TABLE[((value xor (bytes[index].toLong() and 0xffL)) and 0xffL).toInt()] xor
            (value ushr 8)
    }
    return value
}
