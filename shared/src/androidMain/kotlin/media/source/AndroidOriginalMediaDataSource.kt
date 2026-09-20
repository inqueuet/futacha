package com.valoser.futacha.shared.media.source

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.runBlocking
import java.io.IOException

/** Media3's loader thread reads the one growing original. This source has no HTTP fallback. */
@UnstableApi
internal class AndroidOriginalMediaDataSource(private val original: OriginalMediaPlayback) : BaseDataSource(false) {
    private var reader: OriginalMediaPlayback? = null
    private var uri: Uri? = null
    private var position = 0L
    private var remaining = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long = io {
        check(reader == null) { "Original data source is already open" }
        require(dataSpec.uri.scheme == "futacha-original")
        transferInitializing(dataSpec)
        val lease = original.retain()
        reader = lease
        try {
            val info = runBlocking { lease.info() }
            if (info.sizeBytes >= 0 && dataSpec.position > info.sizeBytes) {
                throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            }
            position = dataSpec.position
            remaining = if (info.sizeBytes >= 0) info.sizeBytes - position else C.LENGTH_UNSET.toLong()
            if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                remaining = if (remaining < 0) dataSpec.length else minOf(remaining, dataSpec.length)
            }
            uri = dataSpec.uri
            opened = true
            transferStarted(dataSpec)
            remaining
        } catch (failure: Throwable) {
            close(); throw failure
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = io {
        require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
        val lease = checkNotNull(reader) { "Original data source is not open" }
        if (length == 0) return@io 0
        if (remaining == 0L) return@io C.RESULT_END_OF_INPUT
        val count = minOf(length.toLong(), 64 * 1024L, if (remaining >= 0) remaining else Long.MAX_VALUE).toInt()
        val bytes = runBlocking { lease.readAt(position, count) }
        if (bytes.isEmpty()) {
            if (remaining > 0) throw IOException("Original media ended before the requested range")
            return@io C.RESULT_END_OF_INPUT
        }
        bytes.copyInto(buffer, offset)
        position += bytes.size
        if (remaining >= 0) remaining -= bytes.size
        bytesTransferred(bytes.size)
        bytes.size
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        val previous = reader
        reader = null; uri = null
        try { previous?.close() } finally {
            if (opened) { opened = false; transferEnded() }
        }
    }

    private inline fun <T> io(block: () -> T): T = try { block() }
    catch (failure: IOException) { throw failure }
    catch (failure: Exception) { throw IOException("Cannot read the shared original", failure) }
}
