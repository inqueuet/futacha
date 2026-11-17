package com.valoser.futacha.shared.ui.futaber.mht

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.FileWriteSink
import com.valoser.futacha.shared.util.writeByteStreamReplacingImpl

/**
 * The in-memory file system for the MHT library's tests. The library writes a file under a temporary name and swaps it in,
 * which goes through a [SaveLocation] (the app's data folder); the in-memory file system keeps the paths of a location
 * apart from plain relative paths, so here a location is the data folder itself, as on the devices.
 */
internal class MhtTestFileSystem(
    val delegate: InMemoryFileSystem = InMemoryFileSystem()
) : FileSystem by delegate {
    override fun supportsAtomicReplace(base: SaveLocation): Boolean = true

    override suspend fun writeByteStreamReplacing(
        base: SaveLocation,
        relativePath: String,
        block: suspend (FileWriteSink) -> Unit
    ): Result<String> = writeByteStreamReplacingImpl(base, relativePath, block)

    override suspend fun writeByteStream(
        base: SaveLocation,
        relativePath: String,
        block: suspend (FileWriteSink) -> Unit
    ): Result<Unit> = delegate.writeByteStream(relativePath, block)

    override suspend fun listFiles(base: SaveLocation, directory: String): List<String> = delegate.listFiles(directory)

    override suspend fun exists(base: SaveLocation, relativePath: String): Boolean = delegate.exists(relativePath)

    override suspend fun delete(base: SaveLocation, relativePath: String): Result<Unit> = delegate.deleteRecursively(relativePath)

    override suspend fun replaceAtomically(base: SaveLocation, fromRelative: String, toRelative: String): Result<Unit> =
        runCatching {
            val bytes = delegate.readBytes(fromRelative).getOrThrow()
            delegate.writeBytes(toRelative, bytes).getOrThrow()
            delegate.delete(fromRelative).getOrThrow()
        }
}

/** A sink that keeps what is written to it. */
internal class CollectingSink : FileWriteSink {
    private val buffer = GrowableBytes()
    val bytes: ByteArray get() = buffer.toByteArray()

    override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
        buffer.append(bytes.copyOfRange(offset, offset + length), length)
    }
}
