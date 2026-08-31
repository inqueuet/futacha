package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.SaveLocation
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.ArrayDeque
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

private class JvmFileSystem : FileSystem {
    private val rootDirectory = File(System.getProperty("java.io.tmpdir"), "futacha-jvm")

    override suspend fun createDirectory(path: String): Result<Unit> = runCatching {
        validateFileSystemPath(path)
        File(resolveAbsolutePath(path)).mkdirs()
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

    override suspend fun writeByteStream(path: String, block: suspend (FileWriteSink) -> Unit): Result<Unit> {
        return try {
            validateFileSystemPath(path)
            val file = File(resolveAbsolutePath(path))
            file.parentFile?.mkdirs()
            FileOutputStream(file, false).use { output ->
                var totalWritten = 0L
                val sink = object : FileWriteSink {
                    override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
                        require(offset >= 0 && length >= 0 && offset + length <= bytes.size) {
                            "Invalid write range: offset=$offset length=$length size=${bytes.size}"
                        }
                        val nextTotal = totalWritten + length
                        validateFileSystemSize(nextTotal, "file")
                        if (length > 0) {
                            output.write(bytes, offset, length)
                            totalWritten = nextTotal
                        }
                    }
                }
                block(sink)
                output.flush()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun writeString(path: String, content: String): Result<Unit> =
        writeBytes(path, content.encodeToByteArray())

    override suspend fun readBytes(path: String): Result<ByteArray> = runCatching {
        validateFileSystemPath(path)
        val file = File(resolveAbsolutePath(path))
        validateFileSystemSize(file.length(), "file")
        file.readBytes()
    }

    override suspend fun readString(path: String): Result<String> = runCatching {
        validateFileSystemPath(path)
        val file = File(resolveAbsolutePath(path))
        validateFileSystemSize(file.length(), "file")
        file.readText()
    }

    override suspend fun delete(path: String): Result<Unit> = runCatching {
        validateFileSystemPath(path)
        File(resolveAbsolutePath(path)).delete()
    }

    override suspend fun deleteRecursively(path: String): Result<Unit> = try {
        validateFileSystemPath(path)
        val root = File(resolveAbsolutePath(path))
        if (root.exists() || Files.isSymbolicLink(root.toPath())) {
            deleteFileTreeBounded(root)
        }
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun deleteFileTreeBounded(root: File) {
        val deadlineNanos = System.nanoTime() + FILE_TREE_DELETE_MAX_DURATION_NANOS
        val stack = ArrayDeque<Pair<File, Boolean>>()
        stack.add(root to false)
        var count = 0
        while (!stack.isEmpty()) {
            coroutineContext.ensureActive()
            check(System.nanoTime() <= deadlineNanos) { "Timed out while deleting file tree: ${root.absolutePath}" }
            val (file, visitedChildren) = stack.removeLast()
            val symbolicLink = Files.isSymbolicLink(file.toPath())
            if (!file.exists() && !symbolicLink) continue
            count += 1
            check(count <= FILE_TREE_DELETE_MAX_ITEMS) { "Too many files while deleting file tree: ${root.absolutePath}" }
            if (symbolicLink) {
                check(file.delete() || (!file.exists() && !Files.isSymbolicLink(file.toPath()))) {
                    "Failed to delete symbolic link: ${file.absolutePath}"
                }
                continue
            }
            if (file.isDirectory && !visitedChildren) {
                stack.add(file to true)
                file.listFiles().orEmpty().forEach { child -> stack.add(child to false) }
                continue
            }
            check(file.delete() || !file.exists()) { "Failed to delete file: ${file.absolutePath}" }
        }
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

    override suspend fun createDirectory(base: SaveLocation, relativePath: String): Result<Unit> {
        validateRelativePathResult(relativePath, allowEmpty = true)?.let { return Result.failure(it) }
        return when (base) {
            is SaveLocation.Path -> createDirectory(join(base.path, relativePath))
            is SaveLocation.TreeUri -> Result.failure(UnsupportedOperationException("TreeUri unsupported on JVM"))
            is SaveLocation.Bookmark -> Result.failure(UnsupportedOperationException("Bookmark unsupported on JVM"))
        }
    }

    override suspend fun writeBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> {
        validateRelativePathResult(relativePath)?.let { return Result.failure(it) }
        return when (base) {
            is SaveLocation.Path -> writeBytes(join(base.path, relativePath), bytes)
            is SaveLocation.TreeUri -> Result.failure(UnsupportedOperationException("TreeUri unsupported on JVM"))
            is SaveLocation.Bookmark -> Result.failure(UnsupportedOperationException("Bookmark unsupported on JVM"))
        }
    }

    override suspend fun appendBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> {
        validateRelativePathResult(relativePath)?.let { return Result.failure(it) }
        return when (base) {
            is SaveLocation.Path -> appendBytes(join(base.path, relativePath), bytes)
            is SaveLocation.TreeUri -> Result.failure(UnsupportedOperationException("TreeUri unsupported on JVM"))
            is SaveLocation.Bookmark -> Result.failure(UnsupportedOperationException("Bookmark unsupported on JVM"))
        }
    }

    override suspend fun writeByteStream(
        base: SaveLocation,
        relativePath: String,
        block: suspend (FileWriteSink) -> Unit
    ): Result<Unit> {
        validateRelativePathResult(relativePath)?.let { return Result.failure(it) }
        return when (base) {
            is SaveLocation.Path -> writeByteStream(join(base.path, relativePath), block)
            is SaveLocation.TreeUri -> Result.failure(UnsupportedOperationException("TreeUri unsupported on JVM"))
            is SaveLocation.Bookmark -> Result.failure(UnsupportedOperationException("Bookmark unsupported on JVM"))
        }
    }

    override suspend fun writeString(base: SaveLocation, relativePath: String, content: String): Result<Unit> =
        writeBytes(base, relativePath, content.encodeToByteArray())

    override suspend fun readString(base: SaveLocation, relativePath: String): Result<String> {
        validateRelativePathResult(relativePath)?.let { return Result.failure(it) }
        return when (base) {
            is SaveLocation.Path -> readString(join(base.path, relativePath))
            is SaveLocation.TreeUri -> Result.failure(UnsupportedOperationException("TreeUri unsupported on JVM"))
            is SaveLocation.Bookmark -> Result.failure(UnsupportedOperationException("Bookmark unsupported on JVM"))
        }
    }

    override suspend fun readBytes(base: SaveLocation, relativePath: String): Result<ByteArray> {
        validateRelativePathResult(relativePath)?.let { return Result.failure(it) }
        return when (base) {
            is SaveLocation.Path -> readBytes(join(base.path, relativePath))
            is SaveLocation.TreeUri -> Result.failure(UnsupportedOperationException("TreeUri unsupported on JVM"))
            is SaveLocation.Bookmark -> Result.failure(UnsupportedOperationException("Bookmark unsupported on JVM"))
        }
    }

    override suspend fun exists(base: SaveLocation, relativePath: String): Boolean {
        if (validateRelativePathResult(relativePath, allowEmpty = true) != null) return false
        return when (base) {
            is SaveLocation.Path -> exists(join(base.path, relativePath))
            is SaveLocation.TreeUri, is SaveLocation.Bookmark -> false
        }
    }

    override suspend fun getFileSize(base: SaveLocation, relativePath: String): Long {
        if (validateRelativePathResult(relativePath, allowEmpty = true) != null) return 0L
        return when (base) {
            is SaveLocation.Path -> getFileSize(join(base.path, relativePath))
            is SaveLocation.TreeUri, is SaveLocation.Bookmark -> 0L
        }
    }

    override suspend fun listFiles(base: SaveLocation, directory: String): List<String> {
        if (validateRelativePathResult(directory, "directory", allowEmpty = true) != null) return emptyList()
        return when (base) {
            is SaveLocation.Path -> listFiles(join(base.path, directory))
            is SaveLocation.TreeUri, is SaveLocation.Bookmark -> emptyList()
        }
    }

    override suspend fun delete(base: SaveLocation, relativePath: String): Result<Unit> {
        validateRelativePathResult(relativePath, allowEmpty = true)?.let { return Result.failure(it) }
        return when (base) {
            is SaveLocation.Path -> deleteRecursively(join(base.path, relativePath))
            is SaveLocation.TreeUri -> Result.failure(UnsupportedOperationException("TreeUri unsupported on JVM"))
            is SaveLocation.Bookmark -> Result.failure(UnsupportedOperationException("Bookmark unsupported on JVM"))
        }
    }

    private fun validateRelativePathResult(
        relativePath: String,
        paramName: String = "relativePath",
        allowEmpty: Boolean = false
    ): Throwable? = runCatching {
        validateFileSystemRelativePath(relativePath, paramName, allowEmpty)
    }.exceptionOrNull()

    private fun join(base: String, relativePath: String): String =
        if (relativePath.isBlank()) base else "$base/$relativePath"

    private companion object {
        const val FILE_TREE_DELETE_MAX_ITEMS = 10_000
        const val FILE_TREE_DELETE_MAX_DURATION_NANOS = 30_000_000_000L
    }
}

actual fun createFileSystem(platformContext: Any?): FileSystem = JvmFileSystem()
