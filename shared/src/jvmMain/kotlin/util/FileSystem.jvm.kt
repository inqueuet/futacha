package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.SaveLocation
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.ArrayDeque
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Every suspend operation switches to [Dispatchers.IO] like the mobile file
 * systems: desktop UI coroutines run on the Swing event thread, and a caller
 * saving an attachment or reading a font there must not freeze the window.
 */
internal class JvmFileSystem(private val rootDirectory: File) : FileSystem {

    override suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        validateFileSystemPath(path)
        val directory = File(resolveAbsolutePath(path))
        check(directory.isDirectory || directory.mkdirs()) { "フォルダを作成できません: $path" }
    } }

    override suspend fun writeBytes(path: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        validateFileSystemPath(path)
        validateFileSystemSize(bytes.size.toLong(), "bytes")
        val file = File(resolveAbsolutePath(path))
        file.parentFile?.mkdirs()
        writeFileAtomically(file, bytes)
    } }

    override suspend fun appendBytes(path: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        validateFileSystemPath(path)
        validateFileSystemSize(bytes.size.toLong(), "bytes")
        val file = File(resolveAbsolutePath(path))
        file.parentFile?.mkdirs()
        file.appendBytes(bytes)
    } }

    override suspend fun writeByteStream(path: String, block: suspend (FileWriteSink) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
        try {
            validateFileSystemPath(path)
            val file = File(resolveAbsolutePath(path))
            file.parentFile?.mkdirs()
            FileOutputStream(file, false).use { output ->
                var totalWritten = 0L
                val sink = object : FileWriteSink {
                    override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
                        coroutineContext.ensureActive()
                        require(offset >= 0 && length >= 0 && offset + length <= bytes.size) {
                            "Invalid write range: offset=$offset length=$length size=${bytes.size}"
                        }
                        val nextTotal = totalWritten + length
                        validateFileSystemStreamSize(nextTotal, "file")
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

    override suspend fun readBytes(path: String): Result<ByteArray> = withContext(Dispatchers.IO) { runCatching {
        validateFileSystemPath(path)
        val file = File(resolveAbsolutePath(path))
        validateFileSystemSize(file.length(), "file")
        file.readBytes()
    } }

    override suspend fun <T> readByteStream(path: String, block: suspend (FileReadSource) -> T): Result<T> =
        withContext(Dispatchers.IO) { runSuspendCatchingPreservingCancellation {
            validateFileSystemPath(path)
            File(resolveAbsolutePath(path)).inputStream().use { input ->
                block(object : FileReadSource {
                    override suspend fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                        coroutineContext.ensureActive()
                        return input.read(bytes, offset, length)
                    }
                })
            }
        } }

    override suspend fun readString(path: String): Result<String> = withContext(Dispatchers.IO) { runCatching {
        validateFileSystemPath(path)
        val file = File(resolveAbsolutePath(path))
        validateFileSystemSize(file.length(), "file")
        file.readText()
    } }

    override suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        validateFileSystemPath(path)
        val file = File(resolveAbsolutePath(path))
        check(!file.exists() || file.delete()) { "ファイルを削除できません: $path" }
    } }

    override suspend fun deleteRecursively(path: String): Result<Unit> = withContext(Dispatchers.IO) { try {
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
    } }

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

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        File(resolveAbsolutePath(path)).exists()
    }

    override suspend fun getFileSize(path: String): Long = withContext(Dispatchers.IO) {
        File(resolveAbsolutePath(path)).takeIf { it.exists() }?.length() ?: 0L
    }

    override suspend fun listFiles(directory: String): List<String> = withContext(Dispatchers.IO) {
        File(resolveAbsolutePath(directory)).list()?.toList() ?: emptyList()
    }

    override fun getAppDataDirectory(): String {
        rootDirectory.mkdirs()
        return rootDirectory.absoluteFile.invariantSeparatorsPath
    }

    override fun resolveAbsolutePath(relativePath: String): String =
        (if (File(relativePath).isAbsolute) File(relativePath) else File(getAppDataDirectory(), relativePath))
            .absoluteFile.invariantSeparatorsPath

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

    override suspend fun linkOrCopy(fromPath: String, toPath: String): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        validateFileSystemPath(fromPath)
        validateFileSystemPath(toPath)
        val from = File(resolveAbsolutePath(fromPath)).toPath()
        val to = File(resolveAbsolutePath(toPath)).toPath()
        to.parent?.let { java.nio.file.Files.createDirectories(it) }
        java.nio.file.Files.deleteIfExists(to)
        try {
            java.nio.file.Files.createLink(to, from)
        } catch (_: Exception) {
            // Other volume or no link support: copy the finished file instead.
            java.nio.file.Files.copy(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        Unit
    } }

    override fun supportsAtomicReplace(base: SaveLocation): Boolean = base is SaveLocation.Path

    override suspend fun replaceAtomically(
        base: SaveLocation,
        fromRelative: String,
        toRelative: String
    ): Result<Unit> = withContext(Dispatchers.IO) { runCatching<Unit> {
        val path = base as? SaveLocation.Path
            ?: throw UnsupportedOperationException("Atomic replace needs a file path location")
        validateFileSystemPath(fromRelative)
        validateFileSystemPath(toRelative)
        val from = File(resolveAbsolutePath(join(path.path, fromRelative))).toPath()
        val to = File(resolveAbsolutePath(join(path.path, toRelative))).toPath()
        try {
            java.nio.file.Files.move(
                from, to,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            // Still a move of the finished file; the destination is never truncated first.
            java.nio.file.Files.move(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    } }

    private fun join(base: String, relativePath: String): String =
        if (relativePath.isBlank()) base else "$base/$relativePath"

    /**
     * Writes beside [file], syncs, then renames over it, so a crash or power loss
     * leaves either the old or the new index.json/history/cookies, never a
     * truncated one. Temp names match Android's `tmp_*.tmp` for [cleanupTempFiles].
     */
    private fun writeFileAtomically(file: File, bytes: ByteArray) {
        val temporary = File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, file.absoluteFile.parentFile)
        try {
            FileOutputStream(temporary, false).use { output ->
                output.write(bytes)
                output.flush()
                output.channel.force(true)
            }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                // Still a rename of the finished file; the destination is never truncated first.
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temporary.exists() && !temporary.delete()) {
                Logger.w("JvmFileSystem", "Failed to delete temp file: ${temporary.absolutePath}")
            }
        }
    }

    /**
     * Deletes `tmp_*.tmp` files older than an hour that a crash left behind in
     * the app data directory (bounded like Android's startup cleanup).
     */
    fun cleanupTempFiles(): Int {
        val root = File(getAppDataDirectory())
        val cutoff = System.currentTimeMillis() - TEMP_MAX_AGE_MILLIS
        val deadline = System.nanoTime() + TEMP_CLEANUP_MAX_DURATION_NANOS
        var examined = 0
        var deleted = 0
        val pending = ArrayDeque<Pair<File, Int>>()
        pending.add(root to 0)
        while (!pending.isEmpty() && examined < TEMP_CLEANUP_MAX_ENTRIES && System.nanoTime() < deadline) {
            val (directory, depth) = pending.removeLast()
            for (child in directory.listFiles().orEmpty()) {
                if (++examined > TEMP_CLEANUP_MAX_ENTRIES) break
                if (Files.isSymbolicLink(child.toPath())) continue
                if (child.isDirectory) {
                    if (depth < TEMP_CLEANUP_MAX_DEPTH) pending.add(child to depth + 1)
                } else if (child.name.startsWith(TEMP_PREFIX) && child.name.endsWith(TEMP_SUFFIX) &&
                    child.lastModified() < cutoff && child.delete()
                ) {
                    deleted++
                }
            }
        }
        if (deleted > 0) Logger.i("JvmFileSystem", "Cleaned up $deleted temp files")
        return deleted
    }

    internal companion object {
        const val FILE_TREE_DELETE_MAX_ITEMS = 10_000
        const val FILE_TREE_DELETE_MAX_DURATION_NANOS = 30_000_000_000L
        const val TEMP_PREFIX = "tmp_"
        const val TEMP_SUFFIX = ".tmp"
        const val TEMP_MAX_AGE_MILLIS = 60 * 60 * 1000L
        const val TEMP_CLEANUP_MAX_DEPTH = 3
        const val TEMP_CLEANUP_MAX_ENTRIES = 5_000
        const val TEMP_CLEANUP_MAX_DURATION_NANOS = 2_000_000_000L
    }
}

actual fun createFileSystem(platformContext: Any?): FileSystem = JvmFileSystem(
    ((platformContext as? com.valoser.futacha.shared.desktop.DesktopEnvironment)
        ?: com.valoser.futacha.shared.desktop.DesktopEnvironment.current)?.dataDirectory
        ?: File(System.getProperty("java.io.tmpdir"), "futacha-jvm")
)
