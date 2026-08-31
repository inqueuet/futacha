package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

data class ImageZipSaveResult(
    val fileName: String,
    val savedItems: Int,
    val failedItems: Int,
    val byteSize: Long,
    val failedUrls: List<String> = emptyList()
)

/** Writes an uncompressed, streaming ZIP without holding large media in memory. */
class ImageZipSaveService(
    private val httpClient: HttpClient,
    private val fileSystem: FileSystem
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
        var pendingOutputFileName: String? = null
        try {
            val urls = mediaUrls.map(String::trim)
                .filter { it.startsWith("https://", true) || it.startsWith("http://", true) }
                .distinct()
            require(urls.isNotEmpty()) { "保存するメディアがありません" }
            val suffix = fileNameSuffix?.let(::safeSegment)?.takeIf { it.isNotBlank() }
            val fileName = buildString {
                append(safeSegment(boardId)).append('_').append(safeSegment(threadId))
                if (suffix != null) append('_').append(suffix)
                append("_media.zip")
            }
            pendingOutputFileName = fileName
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
                    val response = runCatching { httpClient.get(url) }.getOrNull()
                    if (response == null || !response.status.isSuccess()) {
                        response?.let { runCatching { it.bodyAsChannel().cancel() } }
                        failedItems += 1
                        failedUrls += url
                        return@forEachIndexed
                    }
                    try {
                        val declared = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                        if (declared != null && declared !in 1..MAX_ZIP_ENTRY_BYTES) {
                            failedItems += 1
                            failedUrls += url
                            return@forEachIndexed
                        }
                        val channel = response.bodyAsChannel()
                        var currentBytes = 0L
                        val saved = zip.writeEntry(entryName, MAX_ZIP_ENTRY_BYTES) { buffer ->
                            val read = withTimeoutOrNull(READ_IDLE_TIMEOUT_MILLIS) {
                                channel.readAvailable(buffer, 0, buffer.size)
                            } ?: throw IllegalStateException("画像の読み込みがタイムアウトしました")
                            if (read > 0) {
                                currentBytes += read
                                onProgress(
                                    index,
                                    urls.size,
                                    requestedName,
                                    currentBytes,
                                    declared ?: 0L
                                )
                            }
                            read
                        }
                        if (saved <= 0L) throw IllegalStateException("画像が空です")
                        savedItems += 1
                        onProgress(index + 1, urls.size, requestedName, currentBytes, declared ?: currentBytes)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        // Once a local header has been written the archive
                        // cannot safely skip that entry without seeking.
                        // Abort so callers never receive a corrupt ZIP.
                        throw failure
                    } finally {
                        runCatching { response.bodyAsChannel().cancel() }
                    }
                }
                require(savedItems > 0) { "メディアを保存できませんでした" }
                byteSize = zip.finish()
            }
            val writeResult = if (baseSaveLocation != null) {
                fileSystem.writeByteStream(baseSaveLocation, relativePath, writer)
            } else {
                fileSystem.createDirectory(baseDirectory).getOrThrow()
                fileSystem.writeByteStream("$baseDirectory/$relativePath", writer)
            }
            writeResult.onFailure {
                if (baseSaveLocation != null) {
                    fileSystem.delete(baseSaveLocation, relativePath)
                } else {
                    fileSystem.delete("$baseDirectory/$relativePath")
                }
            }
            writeResult.getOrThrow()
            Result.success(ImageZipSaveResult(fileName, savedItems, failedItems, byteSize, failedUrls))
        } catch (cancelled: CancellationException) {
            pendingOutputFileName?.let { fileName ->
                withContext(NonCancellable) {
                    if (baseSaveLocation != null) {
                        fileSystem.delete(baseSaveLocation, fileName)
                    } else {
                        fileSystem.delete("$baseDirectory/$fileName")
                    }
                }
            }
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

private data class StoredZipEntry(
    val name: ByteArray,
    val crc: Long,
    val size: Long,
    val localOffset: Long
)

private class StreamingStoredZipWriter(
    private val sink: com.valoser.futacha.shared.util.FileWriteSink
) {
    private val entries = mutableListOf<StoredZipEntry>()
    private var written = 0L

    suspend fun writeEntry(
        name: String,
        maxEntryBytes: Long,
        read: suspend (ByteArray) -> Int
    ): Long {
        val nameBytes = name.encodeToByteArray()
        require(nameBytes.size <= 0xffff) { "ファイル名が長すぎます" }
        require(written <= UINT_MAX) { "ZIPサイズが4GBを超えています" }
        val offset = written
        writeInt(LOCAL_HEADER_SIGNATURE)
        writeShort(20)
        writeShort(DATA_DESCRIPTOR_FLAG)
        writeShort(STORED_METHOD)
        writeShort(0); writeShort(0)
        writeInt(0); writeInt(0); writeInt(0)
        writeShort(nameBytes.size); writeShort(0)
        write(nameBytes)

        val buffer = ByteArray(512 * 1024)
        var size = 0L
        var crc = 0xffffffffL
        while (true) {
            val count = read(buffer)
            if (count == -1) break
            if (count == 0) continue
            size += count
            require(size <= maxEntryBytes.coerceAtMost(UINT_MAX)) {
                "ZIP内のファイルが上限を超えています"
            }
            crc = updateCrc32(crc, buffer, count)
            sink.write(buffer, 0, count)
            written += count
        }
        val finalCrc = crc xor 0xffffffffL
        writeInt(DATA_DESCRIPTOR_SIGNATURE)
        writeInt(finalCrc)
        writeInt(size)
        writeInt(size)
        entries += StoredZipEntry(nameBytes, finalCrc, size, offset)
        return size
    }

    suspend fun finish(): Long {
        require(entries.size <= 0xffff) { "ZIP内のファイル数が多すぎます" }
        val centralOffset = written
        entries.forEach { entry ->
            writeInt(CENTRAL_HEADER_SIGNATURE)
            writeShort(20); writeShort(20)
            writeShort(DATA_DESCRIPTOR_FLAG); writeShort(STORED_METHOD)
            writeShort(0); writeShort(0)
            writeInt(entry.crc); writeInt(entry.size); writeInt(entry.size)
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
        const val STORED_METHOD = 0
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
