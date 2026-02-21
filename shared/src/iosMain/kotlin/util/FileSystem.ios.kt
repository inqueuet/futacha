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
import kotlinx.coroutines.Dispatchers
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

    // FIX: 安全性チェックのための定数
    companion object {
        // FIX: ファイルサイズ上限（100MB） - OOM防止
        private const val MAX_FILE_SIZE = 100 * 1024 * 1024L
        // FIX: ファイル名の最大長（iOSの制限）
        private const val MAX_FILENAME_LENGTH = 255
        // FIX: パスの最大長（合理的な上限）
        private const val MAX_PATH_LENGTH = 4096
    }

    private inline fun <T> runFsCatching(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * FIX: 入力検証 - セキュリティとデータ整合性のための検証
     *
     * @throws IllegalArgumentException パスが不正な場合
     */
    private fun validatePath(path: String, paramName: String = "path") {
        // FIX: 空文字列チェック
        if (path.isEmpty()) {
            throw IllegalArgumentException("$paramName must not be empty")
        }

        // FIX: null文字チェック（セキュリティ脆弱性防止）
        if (path.contains('\u0000')) {
            throw IllegalArgumentException("$paramName contains null character")
        }

        // FIX: パス長制限
        if (path.length > MAX_PATH_LENGTH) {
            throw IllegalArgumentException("$paramName exceeds maximum length ($MAX_PATH_LENGTH): ${path.length}")
        }

        // FIX: パストラバーサル攻撃防止
        val normalized = path.replace('\\', '/')
        if (normalized.contains("../") || normalized.contains("/..") || normalized == "..") {
            throw IllegalArgumentException("$paramName contains path traversal sequence: $path")
        }

        // FIX: ファイル名の長さチェック（最後のセグメントのみ）
        val fileName = normalized.substringAfterLast('/', normalized)
        if (fileName.length > MAX_FILENAME_LENGTH) {
            throw IllegalArgumentException("File name exceeds maximum length ($MAX_FILENAME_LENGTH): $fileName")
        }
    }

    /**
     * FIX: ファイルサイズ検証 - OOM防止
     *
     * @throws IllegalArgumentException サイズが上限を超える場合
     */
    private fun validateFileSize(size: Long, paramName: String = "file") {
        if (size > MAX_FILE_SIZE) {
            throw IllegalArgumentException("$paramName size ($size bytes) exceeds maximum allowed ($MAX_FILE_SIZE bytes)")
        }
        // FIX: 負のサイズチェック
        if (size < 0) {
            throw IllegalArgumentException("$paramName size cannot be negative: $size")
        }
    }

    private fun isNoSuchFileError(error: NSError?): Boolean {
        // NSFileNoSuchFileError
        return error?.code == 4L
    }

    override suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
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

    override suspend fun writeBytes(path: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runFsCatching {
            validatePath(path, "path") // FIX: 入力検証
            validateFileSize(bytes.size.toLong(), "bytes") // FIX: サイズ検証
            val absolutePath = resolveAbsolutePath(path)
            val parentDir = (absolutePath as NSString).stringByDeletingLastPathComponent

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

    override suspend fun writeString(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runFsCatching {
            validatePath(path, "path") // FIX: 入力検証
            val contentBytes = content.encodeToByteArray()
            validateFileSize(contentBytes.size.toLong(), "content") // FIX: サイズ検証
            val absolutePath = resolveAbsolutePath(path)
            val parentDir = (absolutePath as NSString).stringByDeletingLastPathComponent

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
                val success = (content as NSString).writeToFile(
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

    override suspend fun readBytes(path: String): Result<ByteArray> = withContext(Dispatchers.IO) {
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

    override suspend fun readString(path: String): Result<String> = withContext(Dispatchers.IO) {
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
                content as String
            }
        }
    }

    override suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.IO) {
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

    override suspend fun deleteRecursively(path: String): Result<Unit> = withContext(Dispatchers.IO) {
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

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        val absolutePath = resolveAbsolutePath(path)
        fileManager.fileExistsAtPath(absolutePath)
    }

    override suspend fun getFileSize(path: String): Long = withContext(Dispatchers.IO) {
        val absolutePath = resolveAbsolutePath(path)
        val attributes = fileManager.attributesOfItemAtPath(absolutePath, error = null)
        (attributes?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
    }

    override suspend fun listFiles(directory: String): List<String> = withContext(Dispatchers.IO) {
        val absolutePath = resolveAbsolutePath(directory)
        val contents = fileManager.contentsOfDirectoryAtPath(absolutePath, error = null)
        (contents as? List<*>)?.filterIsInstance<String>() ?: emptyList()
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
        return if (relativePath.startsWith("/")) {
            relativePath
        } else {
            val baseDir = if (relativePath.startsWith(AUTO_SAVE_DIRECTORY) || relativePath.startsWith("private/")) {
                getPrivateAppDataDirectory()
            } else {
                getAppDataDirectory()
            }
            val cleaned = if (relativePath.startsWith("private/")) {
                relativePath.removePrefix("private/").ifBlank { "" }
            } else {
                relativePath
            }
            "$baseDir/$cleaned"
        }
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

    override suspend fun appendBytes(path: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runFsCatching {
            validatePath(path, "path") // FIX: 入力検証
            validateFileSize(bytes.size.toLong(), "bytes") // FIX: サイズ検証
            val absolutePath = resolveAbsolutePath(path)
            val parentDir = (absolutePath as NSString).stringByDeletingLastPathComponent

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

    override suspend fun createDirectory(base: SaveLocation, relativePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        // FIX: 入力検証 - 空文字列の場合はベースディレクトリなので検証不要
        if (relativePath.isNotEmpty()) {
            runFsCatching { validatePath(relativePath, "relativePath") }.getOrElse {
                return@withContext Result.failure(it)
            }
        }
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = if (relativePath.isEmpty()) base.path else "${base.path}/$relativePath"
                createDirectory(fullPath)
            }
            is SaveLocation.Bookmark -> {
                runFsCatching {
                    val url = resolveBookmarkUrl(base.bookmarkData)
                    val startedAccess = url.startAccessingSecurityScopedResource()
                    try {
                        val fullPath = if (relativePath.isEmpty()) {
                            url.path ?: throw IllegalStateException("URL path is null")
                        } else {
                            "${url.path ?: throw IllegalStateException("URL path is null")}/$relativePath"
                        }
                        createDirectory(fullPath).getOrThrow()
                    } finally {
                        if (startedAccess) {
                            url.stopAccessingSecurityScopedResource()
                        }
                    }
                }
            }
            is SaveLocation.TreeUri -> {
                Result.failure(UnsupportedOperationException("TreeUri SaveLocation is not supported on iOS"))
            }
        }
    }

    override suspend fun writeBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        // FIX: 入力検証
        runFsCatching {
            validatePath(relativePath, "relativePath")
            validateFileSize(bytes.size.toLong(), "bytes")
        }.getOrElse {
            return@withContext Result.failure(it)
        }
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = "${base.path}/$relativePath"
                writeBytes(fullPath, bytes)
            }
            is SaveLocation.Bookmark -> {
                runFsCatching {
                    val url = resolveBookmarkUrl(base.bookmarkData)
                    val startedAccess = url.startAccessingSecurityScopedResource()
                    try {
                        val fullPath = "${url.path ?: throw IllegalStateException("URL path is null")}/$relativePath"
                        writeBytes(fullPath, bytes).getOrThrow()
                    } finally {
                        if (startedAccess) {
                            url.stopAccessingSecurityScopedResource()
                        }
                    }
                }
            }
            is SaveLocation.TreeUri -> {
                Result.failure(UnsupportedOperationException("TreeUri SaveLocation is not supported on iOS"))
            }
        }
    }

    override suspend fun appendBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        // FIX: 入力検証
        runFsCatching {
            validatePath(relativePath, "relativePath")
            validateFileSize(bytes.size.toLong(), "bytes")
        }.getOrElse {
            return@withContext Result.failure(it)
        }
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = "${base.path}/$relativePath"
                appendBytes(fullPath, bytes)
            }
            is SaveLocation.Bookmark -> {
                runFsCatching {
                    val url = resolveBookmarkUrl(base.bookmarkData)
                    val startedAccess = url.startAccessingSecurityScopedResource()
                    try {
                        val fullPath = "${url.path ?: throw IllegalStateException("URL path is null")}/$relativePath"
                        appendBytes(fullPath, bytes).getOrThrow()
                    } finally {
                        if (startedAccess) {
                            url.stopAccessingSecurityScopedResource()
                        }
                    }
                }
            }
            is SaveLocation.TreeUri -> {
                Result.failure(UnsupportedOperationException("TreeUri SaveLocation is not supported on iOS"))
            }
        }
    }

    override suspend fun writeString(base: SaveLocation, relativePath: String, content: String): Result<Unit> {
        // FIX: 入力検証はwriteBytesで実行される
        return writeBytes(base, relativePath, content.encodeToByteArray())
    }

    override suspend fun readString(base: SaveLocation, relativePath: String): Result<String> = withContext(Dispatchers.IO) {
        // FIX: 入力検証
        runFsCatching { validatePath(relativePath, "relativePath") }.getOrElse {
            return@withContext Result.failure(it)
        }
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = "${base.path}/$relativePath"
                readString(fullPath)
            }
            is SaveLocation.Bookmark -> {
                runFsCatching {
                    val url = resolveBookmarkUrl(base.bookmarkData)
                    val startedAccess = url.startAccessingSecurityScopedResource()
                    try {
                        val fullPath = "${url.path ?: throw IllegalStateException("URL path is null")}/$relativePath"
                        readString(fullPath).getOrThrow()
                    } finally {
                        if (startedAccess) {
                            url.stopAccessingSecurityScopedResource()
                        }
                    }
                }
            }
            is SaveLocation.TreeUri -> {
                Result.failure(UnsupportedOperationException("TreeUri SaveLocation is not supported on iOS"))
            }
        }
    }

    override suspend fun exists(base: SaveLocation, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = if (relativePath.isEmpty()) base.path else "${base.path}/$relativePath"
                exists(fullPath)
            }
            is SaveLocation.Bookmark -> {
                try {
                    val url = resolveBookmarkUrl(base.bookmarkData)
                    val startedAccess = url.startAccessingSecurityScopedResource()
                    try {
                        val fullPath = if (relativePath.isEmpty()) {
                            url.path ?: throw IllegalStateException("URL path is null")
                        } else {
                            "${url.path ?: throw IllegalStateException("URL path is null")}/$relativePath"
                        }
                        exists(fullPath)
                    } finally {
                        if (startedAccess) {
                            url.stopAccessingSecurityScopedResource()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e("IosFileSystem", "Error checking existence for Bookmark, path: $relativePath", e)
                    false
                }
            }
            is SaveLocation.TreeUri -> {
                false
            }
        }
    }

    override suspend fun delete(base: SaveLocation, relativePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        // FIX: 入力検証 - 空文字列の場合はベースを削除するので検証不要
        if (relativePath.isNotEmpty()) {
            runFsCatching { validatePath(relativePath, "relativePath") }.getOrElse {
                return@withContext Result.failure(it)
            }
        }
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = if (relativePath.isEmpty()) base.path else "${base.path}/$relativePath"
                delete(fullPath)
            }
            is SaveLocation.Bookmark -> {
                runFsCatching {
                    val url = resolveBookmarkUrl(base.bookmarkData)
                    val startedAccess = url.startAccessingSecurityScopedResource()
                    try {
                        val fullPath = if (relativePath.isEmpty()) {
                            url.path ?: throw IllegalStateException("URL path is null")
                        } else {
                            "${url.path ?: throw IllegalStateException("URL path is null")}/$relativePath"
                        }
                        delete(fullPath).getOrThrow()
                    } finally {
                        if (startedAccess) {
                            url.stopAccessingSecurityScopedResource()
                        }
                    }
                }
            }
            is SaveLocation.TreeUri -> {
                Result.failure(UnsupportedOperationException("TreeUri SaveLocation is not supported on iOS"))
            }
        }
    }

    // ========================================
    // Helper methods for secure bookmark
    // ========================================

    private fun resolveBookmarkUrl(bookmarkData: String): NSURL {
        val data = bookmarkData.decodeBase64ToNSData()
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val isStale = alloc<BooleanVar>()
            val url = NSURL.URLByResolvingBookmarkData(
                data,
                options = 0u,
                relativeToURL = null,
                bookmarkDataIsStale = isStale.ptr,
                error = error.ptr
            )
            if (url == null) {
                throw Exception("Failed to resolve bookmark: ${error.value?.localizedDescription ?: "Unknown error"}. Please re-select the directory.")
            }
            if (isStale.value) {
                Logger.w("IosFileSystem", "Bookmark data is stale. The bookmark may not work after app restart. Consider re-selecting the directory.")
                // Note: Stale bookmarks can still work in current session, but may fail after restart.
                // To refresh, user should re-select via directory picker.
            }
            return url
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun String.decodeBase64ToNSData(): NSData {
        val bytes = runCatching { Base64.decode(this) }.getOrNull()
            ?: throw IllegalArgumentException("Invalid base64 bookmark data")
        return bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
    }
}

actual fun createFileSystem(platformContext: Any?): FileSystem {
    return IosFileSystem()
}
