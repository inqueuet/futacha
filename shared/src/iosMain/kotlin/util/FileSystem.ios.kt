package com.valoser.futacha.shared.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
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
            fileManager.createDirectoryAtPath(
                absolutePath,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
            Unit
        }
    }

    override suspend fun writeBytes(path: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val absolutePath = resolveAbsolutePath(path)
            val parentDir = (absolutePath as NSString).stringByDeletingLastPathComponent
            fileManager.createDirectoryAtPath(
                parentDir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )

            val data = bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            }
            data.writeToFile(absolutePath, atomically = true)
            Unit
        }
    }

    override suspend fun writeString(path: String, content: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val absolutePath = resolveAbsolutePath(path)
            val parentDir = (absolutePath as NSString).stringByDeletingLastPathComponent
            fileManager.createDirectoryAtPath(
                parentDir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )

            (content as NSString).writeToFile(
                absolutePath,
                atomically = true,
                encoding = NSUTF8StringEncoding,
                error = null
            )
            Unit
        }
    }

    override suspend fun readBytes(path: String): Result<ByteArray> = withContext(Dispatchers.Default) {
        runCatching {
            val absolutePath = resolveAbsolutePath(path)
            val data = NSData.dataWithContentsOfFile(absolutePath)
                ?: throw Exception("File not found: $absolutePath")

            val bytes = ByteArray(data.length.toInt())
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
            bytes
        }
    }

    override suspend fun readString(path: String): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            val absolutePath = resolveAbsolutePath(path)
            NSString.stringWithContentsOfFile(
                absolutePath,
                encoding = NSUTF8StringEncoding,
                error = null
            ) as String? ?: throw Exception("File not found: $absolutePath")
        }
    }

    override suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val absolutePath = resolveAbsolutePath(path)
            fileManager.removeItemAtPath(absolutePath, error = null)
            Unit
        }
    }

    override suspend fun deleteRecursively(path: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val absolutePath = resolveAbsolutePath(path)
            fileManager.removeItemAtPath(absolutePath, error = null)
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
            "${getAppDataDirectory()}/$relativePath"
        }
    }
}

actual fun createFileSystem(platformContext: Any?): FileSystem {
    return IosFileSystem()
}
