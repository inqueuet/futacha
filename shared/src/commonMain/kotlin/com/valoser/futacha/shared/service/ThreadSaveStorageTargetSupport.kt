package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.withTimeoutOrNull

internal data class ThreadSaveStorageTarget(
    val saveLocation: SaveLocation?,
    val baseDirectory: String,
    val storageId: String
) {
    val absoluteBaseDir: String = "$baseDirectory/$storageId"

    fun relativeStoragePath(relativePath: String): String {
        return if (relativePath.isBlank()) {
            storageId
        } else {
            "$storageId/$relativePath"
        }
    }

    fun absoluteStoragePath(relativePath: String): String {
        return if (relativePath.isBlank()) {
            absoluteBaseDir
        } else {
            "$absoluteBaseDir/$relativePath"
        }
    }
}

internal data class ThreadSaveBinaryWriteTarget(
    val saveLocation: SaveLocation?,
    val relativePath: String?,
    val absolutePath: String?
)

internal fun buildThreadSaveStorageTarget(
    saveLocation: SaveLocation?,
    baseDirectory: String,
    storageId: String
): ThreadSaveStorageTarget {
    return ThreadSaveStorageTarget(
        saveLocation = saveLocation,
        baseDirectory = baseDirectory,
        storageId = storageId
    )
}

internal suspend fun prepareThreadSaveStorageTarget(
    fileSystem: FileSystem,
    target: ThreadSaveStorageTarget,
    boardPath: String
) {
    if (target.saveLocation != null) {
        fileSystem.delete(target.saveLocation, target.storageId).getOrNull()
        fileSystem.createDirectory(target.saveLocation, target.storageId).getOrThrow()
        val boardMediaPath = target.relativeStoragePath(boardPath)
        fileSystem.createDirectory(target.saveLocation, "$boardMediaPath/src").getOrThrow()
        fileSystem.createDirectory(target.saveLocation, "$boardMediaPath/thumb").getOrThrow()
    } else {
        fileSystem.deleteRecursively(target.absoluteBaseDir).getOrNull()
        fileSystem.createDirectory(target.baseDirectory).getOrThrow()
        fileSystem.createDirectory(target.absoluteBaseDir).getOrThrow()
        val boardMediaRoot = target.absoluteStoragePath(boardPath)
        fileSystem.createDirectory(boardMediaRoot).getOrThrow()
        fileSystem.createDirectory("$boardMediaRoot/src").getOrThrow()
        fileSystem.createDirectory("$boardMediaRoot/thumb").getOrThrow()
    }
}

internal suspend fun writeThreadSaveTextFile(
    fileSystem: FileSystem,
    target: ThreadSaveStorageTarget,
    relativePath: String,
    content: String,
    measureAbsolutePathSize: suspend (String) -> Long
): Long {
    return if (target.saveLocation != null) {
        fileSystem.writeString(
            target.saveLocation,
            target.relativeStoragePath(relativePath),
            content
        ).getOrThrow()
        measureThreadSaveUtf8ByteLength(content)
    } else {
        val absolutePath = target.absoluteStoragePath(relativePath)
        fileSystem.writeString(absolutePath, content).getOrThrow()
        measureAbsolutePathSize(absolutePath)
    }
}

internal suspend fun cleanupThreadSaveStorageTarget(
    fileSystem: FileSystem,
    target: ThreadSaveStorageTarget
) {
    if (target.saveLocation != null) {
        fileSystem.delete(target.saveLocation, target.storageId).getOrNull()
    } else {
        fileSystem.deleteRecursively(target.absoluteBaseDir).getOrNull()
    }
}

internal fun resolveThreadSaveBinaryWriteTarget(
    target: ThreadSaveStorageTarget,
    relativePath: String
): ThreadSaveBinaryWriteTarget {
    return if (target.saveLocation != null) {
        ThreadSaveBinaryWriteTarget(
            saveLocation = target.saveLocation,
            relativePath = target.relativeStoragePath(relativePath),
            absolutePath = null
        )
    } else {
        ThreadSaveBinaryWriteTarget(
            saveLocation = null,
            relativePath = null,
            absolutePath = target.absoluteStoragePath(relativePath)
        )
    }
}

internal suspend fun writeThreadSaveBinaryFile(
    fileSystem: FileSystem,
    target: ThreadSaveBinaryWriteTarget,
    payload: ByteArray,
    writeTimeoutMillis: Long
) {
    val completed = withTimeoutOrNull(writeTimeoutMillis) {
        when {
            target.saveLocation != null && target.relativePath != null -> {
                fileSystem.writeBytes(target.saveLocation, target.relativePath, payload).getOrThrow()
            }
            target.absolutePath != null -> {
                fileSystem.writeBytes(target.absolutePath, payload).getOrThrow()
            }
            else -> {
                throw IllegalStateException("No target path specified for media stream")
            }
        }
        true
    } ?: false
    if (!completed) {
        throw IllegalStateException("Save aborted: timed out while writing media file")
    }
}

internal suspend fun cleanupThreadSaveBinaryWriteTarget(
    fileSystem: FileSystem,
    target: ThreadSaveBinaryWriteTarget
) {
    when {
        target.saveLocation != null && target.relativePath != null -> {
            fileSystem.delete(target.saveLocation, target.relativePath).getOrNull()
        }
        target.absolutePath != null -> {
            fileSystem.delete(target.absolutePath).getOrNull()
        }
    }
}
