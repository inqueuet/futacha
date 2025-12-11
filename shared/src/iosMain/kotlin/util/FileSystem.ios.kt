package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.posix.memcpy

/**
 * iOS版FileSystem実装
 */
@OptIn(ExperimentalForeignApi::class)
class IosFileSystem : FileSystem {

    private val fileManager = NSFileManager.defaultManager

    override suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
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

    override suspend fun writeBytes(path: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
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

    override suspend fun writeString(path: String, content: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
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

    override suspend fun readBytes(path: String): Result<ByteArray> = withContext(Dispatchers.Default) {
        runCatching {
            val absolutePath = resolveAbsolutePath(path)
            val data = NSData.dataWithContentsOfFile(absolutePath)
                ?: throw Exception("File not found: $absolutePath")

            val length = data.length.toInt()
            if (length <= 0) {
                return@runCatching ByteArray(0)
            }

            val bytes = ByteArray(length)
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
            bytes
        }
    }

    override suspend fun readString(path: String): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            val absolutePath = resolveAbsolutePath(path)
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

    override suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val absolutePath = resolveAbsolutePath(path)
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                val success = fileManager.removeItemAtPath(absolutePath, error = error.ptr)
                if (!success) {
                    throw Exception("Failed to delete file: ${error.value?.localizedDescription ?: "Unknown error"}")
                }
            }
            Unit
        }
    }

    override suspend fun deleteRecursively(path: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val absolutePath = resolveAbsolutePath(path)
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                val success = fileManager.removeItemAtPath(absolutePath, error = error.ptr)
                if (!success) {
                    val nsError = error.value
                    // If file doesn't exist, consider it a success (already deleted)
                    if (nsError?.code == 4L) { // NSFileNoSuchFileError
                        return@runCatching Unit
                    }
                    throw Exception("Failed to delete recursively: ${nsError?.localizedDescription ?: "Unknown error"}")
                }
            }
            Unit
        }
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.Default) {
        val absolutePath = resolveAbsolutePath(path)
        fileManager.fileExistsAtPath(absolutePath)
    }

    override suspend fun getFileSize(path: String): Long = withContext(Dispatchers.Default) {
        val absolutePath = resolveAbsolutePath(path)
        val attributes = fileManager.attributesOfItemAtPath(absolutePath, error = null)
        (attributes?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
    }

    override suspend fun listFiles(directory: String): List<String> = withContext(Dispatchers.Default) {
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

    override suspend fun appendBytes(path: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
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

            val existingData = if (fileManager.fileExistsAtPath(absolutePath)) {
                NSData.dataWithContentsOfFile(absolutePath) ?: NSData()
            } else {
                NSData()
            }

            val combinedData = NSMutableData.dataWithData(existingData)
            bytes.usePinned { pinned ->
                val newData = NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                combinedData.appendData(newData)
            }

            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                val writeSuccess = combinedData.writeToFile(absolutePath, options = NSDataWritingAtomic, error = error.ptr)
                if (!writeSuccess) {
                    throw Exception("Failed to append to file: ${error.value?.localizedDescription ?: "Unknown error"}")
                }
            }
            Unit
        }
    }

    // ========================================
    // SaveLocation-based implementations
    // ========================================

    override suspend fun createDirectory(base: SaveLocation, relativePath: String): Result<Unit> = withContext(Dispatchers.Default) {
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = if (relativePath.isEmpty()) base.path else "${base.path}/$relativePath"
                createDirectory(fullPath)
            }
            is SaveLocation.Bookmark -> {
                runCatching {
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

    override suspend fun writeBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.Default) {
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = "${base.path}/$relativePath"
                writeBytes(fullPath, bytes)
            }
            is SaveLocation.Bookmark -> {
                runCatching {
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

    override suspend fun appendBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.Default) {
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = "${base.path}/$relativePath"
                appendBytes(fullPath, bytes)
            }
            is SaveLocation.Bookmark -> {
                runCatching {
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
        return writeBytes(base, relativePath, content.toByteArray(Charsets.UTF_8))
    }

    override suspend fun readString(base: SaveLocation, relativePath: String): Result<String> = withContext(Dispatchers.Default) {
        when (base) {
            is SaveLocation.Path -> {
                val fullPath = "${base.path}/$relativePath"
                readString(fullPath)
            }
            is SaveLocation.Bookmark -> {
                runCatching {
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

    override suspend fun exists(base: SaveLocation, relativePath: String): Boolean = withContext(Dispatchers.Default) {
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

    // ========================================
    // Helper methods for secure bookmark
    // ========================================

    private fun resolveBookmarkUrl(bookmarkData: String): NSURL {
        val data = bookmarkData.decodeBase64ToNSData()
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val isStale = alloc<ObjCObjectVar<Boolean>>()
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

    private fun String.decodeBase64ToNSData(): NSData {
        val base64String = this as NSString
        val data = NSData.create(base64EncodedString = base64String, options = 0u)
            ?: throw IllegalArgumentException("Invalid base64 bookmark data")
        return data
    }
}

actual fun createFileSystem(platformContext: Any?): FileSystem {
    return IosFileSystem()
}
