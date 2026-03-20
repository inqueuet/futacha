@file:OptIn(kotlinx.cinterop.BetaInteropApi::class)

package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.posix.memcpy
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.coroutines.cancellation.CancellationException

/**
 * iOS版FileSystem実装
 */
@OptIn(ExperimentalForeignApi::class)
class IosFileSystem : FileSystem {

    private val fileManager = NSFileManager.defaultManager
    private val cleanupMaxAgeMillis = 60 * 60 * 1000L

    private inline fun <T> runFsCatching(block: () -> T): Result<T> = com.valoser.futacha.shared.util.runFsCatching(block)

    private fun validatePath(path: String, paramName: String = "path") = validateFileSystemPath(path, paramName)

    private fun validateFileSize(size: Long, paramName: String = "file") = validateFileSystemSize(size, paramName)

    private fun isNoSuchFileError(error: NSError?): Boolean {
        // NSFileNoSuchFileError
        return error?.code == 4L
    }

    private fun resolveSaveLocationPath(basePath: String, relativePath: String = ""): String {
        val resolvedBase = resolveAbsolutePath(basePath)
        return joinPathSegments(resolvedBase, relativePath)
    }

    private fun joinBaseAndRelativePath(basePath: String, relativePath: String): String =
        joinPathSegments(basePath, relativePath)

    private suspend fun <T> withBookmarkPath(
        bookmarkData: String,
        relativePath: String,
        block: suspend (String) -> T
    ): T {
        val url = resolveBookmarkUrl(bookmarkData)
        val startedAccess = url.startAccessingSecurityScopedResource()
        try {
            val basePath = resolveBookmarkPath(url)
            return block(joinBaseAndRelativePath(basePath, relativePath))
        } finally {
            if (startedAccess) {
                url.stopAccessingSecurityScopedResource()
            }
        }
    }

    private suspend fun <T> withSaveLocationPath(
        base: SaveLocation,
        relativePath: String,
        onTreeUri: () -> T,
        block: suspend (String) -> T
    ): T {
        return when (base) {
            is SaveLocation.Path -> block(resolveSaveLocationPath(base.path, relativePath))
            is SaveLocation.Bookmark -> withBookmarkPath(base.bookmarkData, relativePath, block)
            is SaveLocation.TreeUri -> {
                val resolvedPath = resolveTreeUriPath(base.uri)
                if (resolvedPath == null) {
                    onTreeUri()
                } else {
                    block(joinBaseAndRelativePath(resolvedPath, relativePath))
                }
            }
        }
    }

    private fun resolveTreeUriPath(uri: String): String? {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("/")) {
            return trimmed
        }
        val url = NSURL(string = trimmed)
        if (url.scheme == "file") {
            return url.path
        }
        if (trimmed.startsWith("content://")) {
            val treeSegment = trimmed.substringAfter("/tree/", "")
            if (treeSegment.isNotEmpty()) {
                val decoded = NSString.create(string = treeSegment).stringByRemovingPercentEncoding ?: treeSegment
                val normalized = decoded.substringAfter("primary:", decoded)
                if (normalized.isNotBlank()) {
                    return resolveSaveLocationPath(normalized)
                }
            }
        }
        return null
    }

    private fun parentDirectory(path: String): String =
        com.valoser.futacha.shared.util.parentDirectory(path)

    override suspend fun createDirectory(path: String): Result<Unit> = withContext(AppDispatchers.io) {
        runFsCatching {
            validatePath(path, "path") // FIX: 入力検証
            val absolutePath = resolveAbsolutePath(path)
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                val success = fileManager.createDirectoryAtPath(
                    absolutePath,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = error.ptr
                )
                if (!success) {
                    val nsError = error.value
                    throw Exception("Failed to create directory: ${nsError?.localizedDescription ?: "Unknown error"}")
                }
            }
            Unit
        }
    }

    override suspend fun writeBytes(path: String, bytes: ByteArray): Result<Unit> = withContext(AppDispatchers.io) {
        runFsCatching {
            validatePath(path, "path") // FIX: 入力検証
            validateFileSize(bytes.size.toLong(), "bytes") // FIX: サイズ検証
            val absolutePath = resolveAbsolutePath(path)
            val parentDir = parentDirectory(absolutePath)

            // Create parent directory with error checking
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                val success = fileManager.createDirectoryAtPath(
                    parentDir,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = error.ptr
                )
                if (!success && error.value != null) {
                    throw Exception("Failed to create parent directory: ${error.value?.localizedDescription}")
                }
            }

            // Pin bytes and write atomically within the same scope for memory safety
            val success = bytes.usePinned { pinned ->
                val data = NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                memScoped {
                    val error = alloc<ObjCObjectVar<NSError?>>()
                    val writeSuccess = data.writeToFile(absolutePath, options = NSDataWritingAtomic, error = error.ptr)
                    if (!writeSuccess) {
                        throw Exception("Failed to write file: ${error.value?.localizedDescription ?: "Unknown error"}")
                    }
                    writeSuccess
                }
            }

            if (!success) {
                throw Exception("Failed to write file")
            }
            Unit
        }
    }

    override suspend fun writeString(path: String, content: String): Result<Unit> = withContext(AppDispatchers.io) {
        runFsCatching {
            validatePath(path, "path") // FIX: 入力検証
            val contentBytes = content.encodeToByteArray()
            validateFileSize(contentBytes.size.toLong(), "content") // FIX: サイズ検証
            val absolutePath = resolveAbsolutePath(path)
            val parentDir = parentDirectory(absolutePath)

            // Create parent directory with error checking
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                val success = fileManager.createDirectoryAtPath(
                    parentDir,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = error.ptr
                )
                if (!success && error.value != null) {
                    throw Exception("Failed to create parent directory: ${error.value?.localizedDescription}")
                }
            }

            // Write string with error checking
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                val success = NSString.create(string = content).writeToFile(
                    absolutePath,
                    atomically = true,
                    encoding = NSUTF8StringEncoding,
                    error = error.ptr
                )
                if (!success) {
                    throw Exception("Failed to write string: ${error.value?.localizedDescription ?: "Unknown error"}")
                }
            }
            Unit
        }
    }

    override suspend fun readBytes(path: String): Result<ByteArray> = withContext(AppDispatchers.io) {
        runFsCatching {
            validatePath(path, "path") // FIX: 入力検証
            val absolutePath = resolveAbsolutePath(path)
            val data = NSData.dataWithContentsOfFile(absolutePath)
                ?: throw Exception("File not found: $absolutePath")

            val length = data.length.toLong()
            validateFileSize(length, "file") // FIX: 読み込み前にサイズチェック
            val lengthInt = length.toInt()
            if (lengthInt <= 0) {
                return@runFsCatching ByteArray(0)
            }

            val bytes = ByteArray(lengthInt)
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
            bytes
        }
    }

    override suspend fun readString(path: String): Result<String> = withContext(AppDispatchers.io) {
        runFsCatching {
            validatePath(path, "path") // FIX: 入力検証
            val absolutePath = resolveAbsolutePath(path)
            // FIX: サイズチェックのため、まずファイルサイズを確認
            val attributes = fileManager.attributesOfItemAtPath(absolutePath, error = null)
            val fileSize = (attributes?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
            validateFileSize(fileSize, "file") // FIX: 読み込み前にサイズチェック
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                val content = NSString.stringWithContentsOfFile(
                    absolutePath,
                    encoding = NSUTF8StringEncoding,
                    error = error.ptr
                )
                if (content == null) {
                    throw Exception("Failed to read file: ${error.value?.localizedDescription ?: "File not found"}")
                }
                content
            }
        }
    }

    override suspend fun delete(path: String): Result<Unit> = withContext(AppDispatchers.io) {
        runFsCatching {
            validatePath(path, "path") // FIX: 入力検証
            val absolutePath = resolveAbsolutePath(path)
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                val success = fileManager.removeItemAtPath(absolutePath, error = error.ptr)
                if (!success) {
                    val nsError = error.value
                    if (isNoSuchFileError(nsError)) {
                        return@runFsCatching Unit
                    }
                    throw Exception("Failed to delete file: ${nsError?.localizedDescription ?: "Unknown error"}")
                }
            }
            Unit
        }
    }

    override suspend fun deleteRecursively(path: String): Result<Unit> = withContext(AppDispatchers.io) {
        runFsCatching {
            validatePath(path, "path") // FIX: 入力検証
            val absolutePath = resolveAbsolutePath(path)
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                val success = fileManager.removeItemAtPath(absolutePath, error = error.ptr)
                if (!success) {
                    val nsError = error.value
                    // If file doesn't exist, consider it a success (already deleted)
                    if (isNoSuchFileError(nsError)) {
                        return@runFsCatching Unit
                    }
                    throw Exception("Failed to delete recursively: ${nsError?.localizedDescription ?: "Unknown error"}")
                }
            }
            Unit
        }
    }

    override suspend fun exists(path: String): Boolean = withContext(AppDispatchers.io) {
        val absolutePath = resolveAbsolutePath(path)
        fileManager.fileExistsAtPath(absolutePath)
    }

    override suspend fun getFileSize(path: String): Long = withContext(AppDispatchers.io) {
        val absolutePath = resolveAbsolutePath(path)
        val attributes = fileManager.attributesOfItemAtPath(absolutePath, error = null)
        (attributes?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
    }

    override suspend fun listFiles(directory: String): List<String> = withContext(AppDispatchers.io) {
        val absolutePath = resolveAbsolutePath(directory)
        val contents = fileManager.contentsOfDirectoryAtPath(absolutePath, error = null)
        contents?.filterIsInstance<String>() ?: emptyList()
    }

    suspend fun cleanupTempFiles(): Result<Int> = withContext(AppDispatchers.io) {
        runFsCatching {
            val nowMillis = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
            val searchDirectories = buildList {
                NSTemporaryDirectory().takeIf { it.isNotBlank() }?.let(::add)
                val caches = NSSearchPathForDirectoriesInDomains(
                    NSCachesDirectory,
                    NSUserDomainMask,
                    true
                ).firstOrNull() as? String
                caches?.takeIf { it.isNotBlank() }?.let(::add)
            }.distinct()

            var deletedCount = 0
            searchDirectories.forEach { directory ->
                deletedCount += cleanupTempFilesRecursive(
                    directory = directory,
                    nowMillis = nowMillis,
                    maxAgeMillis = cleanupMaxAgeMillis,
                    currentDepth = 0,
                    maxDepth = 3
                )
            }
            deletedCount
        }
    }

    override fun getAppDataDirectory(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )
        return (paths.firstOrNull() as? String) ?: ""
    }

    override fun resolveAbsolutePath(relativePath: String): String {
        return resolveIosAbsolutePath(
            relativePath = relativePath,
            appDataDirectory = getAppDataDirectory(),
            privateAppDataDirectory = getPrivateAppDataDirectory()
        )
    }

    /**
     * ライブラリ配下のアプリ専用ディレクトリを取得 (Filesアプリには表示されない)
     */
    private fun getPrivateAppDataDirectory(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory,
            NSUserDomainMask,
            true
        )
        val basePath = (paths.firstOrNull() as? String) ?: getAppDataDirectory()
        val appDir = "$basePath/futacha"

        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val success = fileManager.createDirectoryAtPath(
                appDir,
                withIntermediateDirectories = true,
                attributes = null,
                error = error.ptr
            )

            if (!success && error.value != null) {
                // フォールバックとして Documents を使いつつ、既存の挙動は維持する
                return getAppDataDirectory()
            }
        }

        return appDir
    }

    override suspend fun appendBytes(path: String, bytes: ByteArray): Result<Unit> = withContext(AppDispatchers.io) {
        runFsCatching {
            validatePath(path, "path") // FIX: 入力検証
            validateFileSize(bytes.size.toLong(), "bytes") // FIX: サイズ検証
            val absolutePath = resolveAbsolutePath(path)
            val parentDir = parentDirectory(absolutePath)

            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                val success = fileManager.createDirectoryAtPath(
                    parentDir,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = error.ptr
                )
                if (!success && error.value != null) {
                    throw Exception("Failed to create parent directory: ${error.value?.localizedDescription}")
                }
            }

            val existingSize = if (fileManager.fileExistsAtPath(absolutePath)) {
                val attributes = fileManager.attributesOfItemAtPath(absolutePath, error = null)
                (attributes?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
            } else {
                0L
            }
            validateFileSize(existingSize + bytes.size.toLong(), "file")

            if (!fileManager.fileExistsAtPath(absolutePath)) {
                val created = fileManager.createFileAtPath(absolutePath, contents = null, attributes = null)
                if (!created) {
                    throw Exception("Failed to create file for append: $absolutePath")
                }
            }

            val fileHandle = NSFileHandle.fileHandleForWritingAtPath(absolutePath)
                ?: throw Exception("Failed to open file for append: $absolutePath")

            try {
                fileHandle.seekToEndOfFile()
                bytes.usePinned { pinned ->
                    val chunk = NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                    fileHandle.writeData(chunk)
                }
            } finally {
                fileHandle.closeFile()
            }
            Unit
        }
    }

    // ========================================
    // SaveLocation-based implementations
    // ========================================

    override suspend fun createDirectory(base: SaveLocation, relativePath: String): Result<Unit> = withContext(AppDispatchers.io) {
        // FIX: 入力検証 - 空文字列の場合はベースディレクトリなので検証不要
        if (relativePath.isNotEmpty()) {
            runFsCatching { validatePath(relativePath, "relativePath") }.getOrElse {
                return@withContext Result.failure(it)
            }
        }
        withSaveLocationPath(
            base = base,
            relativePath = relativePath,
            onTreeUri = {
                Result.failure(unsupportedTreeUriOnIos())
            }
        ) { fullPath ->
            createDirectory(fullPath)
        }
    }

    override suspend fun writeBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> = withContext(AppDispatchers.io) {
        // FIX: 入力検証
        runFsCatching {
            validatePath(relativePath, "relativePath")
            validateFileSize(bytes.size.toLong(), "bytes")
        }.getOrElse {
            return@withContext Result.failure(it)
        }
        withSaveLocationPath(
            base = base,
            relativePath = relativePath,
            onTreeUri = {
                Result.failure(unsupportedTreeUriOnIos())
            }
        ) { fullPath ->
            writeBytes(fullPath, bytes)
        }
    }

    override suspend fun appendBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> = withContext(AppDispatchers.io) {
        // FIX: 入力検証
        runFsCatching {
            validatePath(relativePath, "relativePath")
            validateFileSize(bytes.size.toLong(), "bytes")
        }.getOrElse {
            return@withContext Result.failure(it)
        }
        withSaveLocationPath(
            base = base,
            relativePath = relativePath,
            onTreeUri = {
                Result.failure(unsupportedTreeUriOnIos())
            }
        ) { fullPath ->
            appendBytes(fullPath, bytes)
        }
    }

    override suspend fun writeString(base: SaveLocation, relativePath: String, content: String): Result<Unit> {
        // FIX: 入力検証はwriteBytesで実行される
        return writeBytes(base, relativePath, content.encodeToByteArray())
    }

    override suspend fun readString(base: SaveLocation, relativePath: String): Result<String> = withContext(AppDispatchers.io) {
        // FIX: 入力検証
        runFsCatching { validatePath(relativePath, "relativePath") }.getOrElse {
            return@withContext Result.failure(it)
        }
        withSaveLocationPath(
            base = base,
            relativePath = relativePath,
            onTreeUri = {
                Result.failure(unsupportedTreeUriOnIos())
            }
        ) { fullPath ->
            readString(fullPath)
        }
    }

    override suspend fun exists(base: SaveLocation, relativePath: String): Boolean = withContext(AppDispatchers.io) {
        try {
            withSaveLocationPath(
                base = base,
                relativePath = relativePath,
                onTreeUri = { false }
            ) { fullPath ->
                exists(fullPath)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val tag = if (base is SaveLocation.Bookmark) "Bookmark" else "SaveLocation"
            Logger.e("IosFileSystem", "Error checking existence for $tag, path: $relativePath", e)
            false
        }
    }

    override suspend fun delete(base: SaveLocation, relativePath: String): Result<Unit> = withContext(AppDispatchers.io) {
        // FIX: 入力検証 - 空文字列の場合はベースを削除するので検証不要
        if (relativePath.isNotEmpty()) {
            runFsCatching { validatePath(relativePath, "relativePath") }.getOrElse {
                return@withContext Result.failure(it)
            }
        }
        if (base is SaveLocation.Bookmark && relativePath.isEmpty()) {
            Logger.w(
                "IosFileSystem",
                "Refusing to delete bookmark base directory directly"
            )
            return@withContext Result.success(Unit)
        }
        withSaveLocationPath(
            base = base,
            relativePath = relativePath,
            onTreeUri = {
                Result.failure(unsupportedTreeUriOnIos())
            }
        ) { fullPath ->
            deleteRecursively(fullPath)
        }
    }

    // ========================================
    // Helper methods for secure bookmark
    // ========================================

    private class BookmarkResolutionException(message: String, cause: Throwable? = null) :
        IllegalStateException(message, cause)

    private fun resolveBookmarkPath(url: NSURL): String {
        url.path?.let { return it }
        val absolute = url.absoluteString?.trim().orEmpty()
        if (absolute.startsWith("file://")) {
            return absolute.removePrefix("file://")
        }
        throw IllegalStateException("Resolved bookmark URL has no filesystem path")
    }

    private fun resolveBookmarkUrl(bookmarkData: String): NSURL {
        val data = runCatching { bookmarkData.decodeBase64ToNSData() }.getOrElse { decodeError ->
            throw BookmarkResolutionException(
                "Invalid bookmark data. Please re-select the save directory.",
                decodeError
            )
        }
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val isStale = alloc<BooleanVar>()
            val url = NSURL.URLByResolvingBookmarkData(
                data,
                options = NSURLBookmarkResolutionWithSecurityScope,
                relativeToURL = null,
                bookmarkDataIsStale = isStale.ptr,
                error = error.ptr
            )
            if (url == null) {
                throw BookmarkResolutionException(
                    "Failed to resolve bookmark: ${error.value?.localizedDescription ?: "Unknown error"}. Please re-select the directory."
                )
            }
            if (isStale.value) {
                Logger.w("IosFileSystem", "Bookmark data is stale. The bookmark may not work after app restart. Consider re-selecting the directory.")
                // Note: Stale bookmarks can still work in current session, but may fail after restart.
                // To refresh, user should re-select via directory picker.
            }
            return url
        }
    }

    private fun unsupportedTreeUriOnIos(): UnsupportedOperationException {
        return UnsupportedOperationException(
            "TreeUri SaveLocation is Android SAF-specific and intentionally unsupported on iOS. " +
                "Use Path or Bookmark instead."
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun String.decodeBase64ToNSData(): NSData {
        val normalized = trim()
            .replace("\n", "")
            .replace("\r", "")

        fun decode(candidate: String): ByteArray? {
            return runCatching { Base64.decode(candidate) }.getOrNull()
        }

        val standard = normalized
            .replace('-', '+')
            .replace('_', '/')
        val padded = standard + "=".repeat((4 - standard.length % 4) % 4)

        val bytes = decode(normalized)
            ?: decode(standard)
            ?: decode(padded)
            ?: throw IllegalArgumentException("Invalid base64 bookmark data")
        return bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
    }

    private fun cleanupTempFilesRecursive(
        directory: String,
        nowMillis: Long,
        maxAgeMillis: Long,
        currentDepth: Int,
        maxDepth: Int
    ): Int {
        val entries = fileManager.contentsOfDirectoryAtPath(directory, error = null)
            ?.filterIsInstance<String>()
            ?: return 0
        var deletedCount = 0
        entries.forEach { name ->
            val path = "$directory/$name"
            runCatching {
                val attributes = fileManager.attributesOfItemAtPath(path, error = null) ?: return@runCatching
                val fileType = attributes[NSFileType] as? String
                if (fileType == NSFileTypeDirectory) {
                    if (currentDepth < maxDepth) {
                        deletedCount += cleanupTempFilesRecursive(
                            directory = path,
                            nowMillis = nowMillis,
                            maxAgeMillis = maxAgeMillis,
                            currentDepth = currentDepth + 1,
                            maxDepth = maxDepth
                        )
                    }
                    return@runCatching
                }
                if (!name.startsWith("tmp_") || !name.endsWith(".tmp")) {
                    return@runCatching
                }
                val modifiedAt = attributes[NSFileModificationDate] as? NSDate ?: return@runCatching
                val ageMillis = nowMillis - (modifiedAt.timeIntervalSince1970 * 1000.0).toLong()
                if (ageMillis < maxAgeMillis) {
                    return@runCatching
                }
                if (fileManager.removeItemAtPath(path, error = null)) {
                    deletedCount++
                }
            }.onFailure { error ->
                Logger.w("IosFileSystem", "Failed to process temp cleanup entry: $path - ${error.message}")
            }
        }
        return deletedCount
    }
}

actual fun createFileSystem(platformContext: Any?): FileSystem {
    return IosFileSystem()
}
