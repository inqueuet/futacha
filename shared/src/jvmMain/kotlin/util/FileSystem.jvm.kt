package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.SaveLocation
import java.io.File

private class JvmFileSystem : FileSystem {
    private val rootDirectory = File(System.getProperty("java.io.tmpdir"), "futacha-jvm")

    override suspend fun createDirectory(path: String): Result<Unit> = runCatching {
        validateFileSystemPath(path)
        File(resolveAbsolutePath(path)).mkdirs()
        Unit
    }

    override suspend fun writeBytes(path: String, bytes: ByteArray): Result<Unit> = runCatching {
        validateFileSystemPath(path)
        validateFileSystemSize(bytes.size.toLong(), "bytes")
        val file = File(resolveAbsolutePath(path))
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    override suspend fun appendBytes(path: String, bytes: ByteArray): Result<Unit> = runCatching {
        validateFileSystemPath(path)
        validateFileSystemSize(bytes.size.toLong(), "bytes")
        val file = File(resolveAbsolutePath(path))
        file.parentFile?.mkdirs()
        file.appendBytes(bytes)
    }

    override suspend fun writeString(path: String, content: String): Result<Unit> =
        writeBytes(path, content.encodeToByteArray())

    override suspend fun readBytes(path: String): Result<ByteArray> = runCatching {
        validateFileSystemPath(path)
        File(resolveAbsolutePath(path)).readBytes()
    }

    override suspend fun readString(path: String): Result<String> = runCatching {
        validateFileSystemPath(path)
        File(resolveAbsolutePath(path)).readText()
    }

    override suspend fun delete(path: String): Result<Unit> = runCatching {
        validateFileSystemPath(path)
        File(resolveAbsolutePath(path)).delete()
        Unit
    }

    override suspend fun deleteRecursively(path: String): Result<Unit> = runCatching {
        validateFileSystemPath(path)
        File(resolveAbsolutePath(path)).deleteRecursively()
        Unit
    }

    override suspend fun exists(path: String): Boolean =
        File(resolveAbsolutePath(path)).exists()

    override suspend fun getFileSize(path: String): Long =
        File(resolveAbsolutePath(path)).takeIf { it.exists() }?.length() ?: 0L

    override suspend fun listFiles(directory: String): List<String> =
        File(resolveAbsolutePath(directory)).list()?.toList() ?: emptyList()

    override fun getAppDataDirectory(): String {
        rootDirectory.mkdirs()
        return rootDirectory.absolutePath
    }

    override fun resolveAbsolutePath(relativePath: String): String =
        if (relativePath.startsWith("/")) relativePath else File(getAppDataDirectory(), relativePath).absolutePath

    override suspend fun createDirectory(base: SaveLocation, relativePath: String): Result<Unit> =
        when (base) {
            is SaveLocation.Path -> createDirectory(join(base.path, relativePath))
            is SaveLocation.TreeUri -> Result.failure(UnsupportedOperationException("TreeUri unsupported on JVM"))
            is SaveLocation.Bookmark -> Result.failure(UnsupportedOperationException("Bookmark unsupported on JVM"))
        }

    override suspend fun writeBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> =
        when (base) {
            is SaveLocation.Path -> writeBytes(join(base.path, relativePath), bytes)
            is SaveLocation.TreeUri -> Result.failure(UnsupportedOperationException("TreeUri unsupported on JVM"))
            is SaveLocation.Bookmark -> Result.failure(UnsupportedOperationException("Bookmark unsupported on JVM"))
        }

    override suspend fun appendBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> =
        when (base) {
            is SaveLocation.Path -> appendBytes(join(base.path, relativePath), bytes)
            is SaveLocation.TreeUri -> Result.failure(UnsupportedOperationException("TreeUri unsupported on JVM"))
            is SaveLocation.Bookmark -> Result.failure(UnsupportedOperationException("Bookmark unsupported on JVM"))
        }

    override suspend fun writeString(base: SaveLocation, relativePath: String, content: String): Result<Unit> =
        writeBytes(base, relativePath, content.encodeToByteArray())

    override suspend fun readString(base: SaveLocation, relativePath: String): Result<String> =
        when (base) {
            is SaveLocation.Path -> readString(join(base.path, relativePath))
            is SaveLocation.TreeUri -> Result.failure(UnsupportedOperationException("TreeUri unsupported on JVM"))
            is SaveLocation.Bookmark -> Result.failure(UnsupportedOperationException("Bookmark unsupported on JVM"))
        }

    override suspend fun exists(base: SaveLocation, relativePath: String): Boolean =
        when (base) {
            is SaveLocation.Path -> exists(join(base.path, relativePath))
            is SaveLocation.TreeUri, is SaveLocation.Bookmark -> false
        }

    override suspend fun delete(base: SaveLocation, relativePath: String): Result<Unit> =
        when (base) {
            is SaveLocation.Path -> deleteRecursively(join(base.path, relativePath))
            is SaveLocation.TreeUri -> Result.failure(UnsupportedOperationException("TreeUri unsupported on JVM"))
            is SaveLocation.Bookmark -> Result.failure(UnsupportedOperationException("Bookmark unsupported on JVM"))
        }

    private fun join(base: String, relativePath: String): String =
        if (relativePath.isBlank()) base else "$base/$relativePath"
}

actual fun createFileSystem(platformContext: Any?): FileSystem = JvmFileSystem()
