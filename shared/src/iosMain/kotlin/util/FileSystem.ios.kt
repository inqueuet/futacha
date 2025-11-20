package com.valoser.futacha.shared.util

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
            val baseDir = if (relativePath.startsWith(AUTO_SAVE_DIRECTORY)) {
                getPrivateAppDataDirectory()
            } else {
                getAppDataDirectory()
            }
            "$baseDir/$relativePath"
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
}

actual fun createFileSystem(platformContext: Any?): FileSystem {
    return IosFileSystem()
}
