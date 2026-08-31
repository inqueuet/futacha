package com.valoser.futacha.shared.util

internal const val MAX_FILE_SYSTEM_FILE_SIZE = 100 * 1024 * 1024L
// Streaming writers do not materialize the payload in memory. Keep their
// safety ceiling separate so large Futaba videos and image ZIPs are not
// rejected by the 100 MiB byte-array/read guard.
internal const val MAX_FILE_SYSTEM_STREAM_SIZE = 0xffff_ffffL
internal const val MAX_FILE_SYSTEM_FILENAME_LENGTH = 255
internal const val MAX_FILE_SYSTEM_PATH_LENGTH = 4096

internal fun validateFileSystemPath(path: String, paramName: String = "path") {
    if (path.isEmpty()) {
        throw IllegalArgumentException("$paramName must not be empty")
    }
    if (path.contains('\u0000')) {
        throw IllegalArgumentException("$paramName contains null character")
    }
    if (path.length > MAX_FILE_SYSTEM_PATH_LENGTH) {
        throw IllegalArgumentException(
            "$paramName exceeds maximum length ($MAX_FILE_SYSTEM_PATH_LENGTH): ${path.length}"
        )
    }

    val normalized = path.replace('\\', '/')
    if (normalized.contains("../") || normalized.contains("/..") || normalized == "..") {
        throw IllegalArgumentException("$paramName contains path traversal sequence: $path")
    }

    val fileName = normalized.substringAfterLast('/', normalized)
    if (fileName.length > MAX_FILE_SYSTEM_FILENAME_LENGTH) {
        throw IllegalArgumentException(
            "File name exceeds maximum length ($MAX_FILE_SYSTEM_FILENAME_LENGTH): $fileName"
        )
    }
}

/**
 * Validates a child path that must remain below an already-resolved save root.
 * Absolute paths are valid for a user-selected [SaveLocation.Path] itself, but
 * never for the relative path passed alongside that root.
 */
internal fun validateFileSystemRelativePath(
    path: String,
    paramName: String = "relativePath",
    allowEmpty: Boolean = false
) {
    if (path.isEmpty() && allowEmpty) return
    validateFileSystemPath(path, paramName)

    val normalized = path.replace('\\', '/')
    val hasWindowsDrivePrefix =
        normalized.length >= 2 && normalized[0].isLetter() && normalized[1] == ':'
    if (normalized.startsWith('/') || hasWindowsDrivePrefix) {
        throw IllegalArgumentException("$paramName must be relative: $path")
    }
}

internal fun validateFileSystemSize(size: Long, paramName: String = "file") {
    if (size > MAX_FILE_SYSTEM_FILE_SIZE) {
        throw IllegalArgumentException(
            "$paramName size ($size bytes) exceeds maximum allowed ($MAX_FILE_SYSTEM_FILE_SIZE bytes)"
        )
    }
    if (size < 0) {
        throw IllegalArgumentException("$paramName size cannot be negative: $size")
    }
}

internal fun validateFileSystemStreamSize(size: Long, paramName: String = "file") {
    if (size < 0L || size > MAX_FILE_SYSTEM_STREAM_SIZE) {
        throw IllegalArgumentException(
            "$paramName stream size ($size bytes) exceeds maximum allowed ($MAX_FILE_SYSTEM_STREAM_SIZE bytes)"
        )
    }
}

internal inline fun <T> runFsCatching(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (t: Exception) {
        Result.failure(t)
    }
}


internal fun splitParentAndFileName(relativePath: String): Pair<String, String> {
    val normalized = relativePath.trim().trim('/')
    val lastSlash = normalized.lastIndexOf('/')
    return if (lastSlash < 0) {
        "" to normalized
    } else {
        normalized.substring(0, lastSlash) to normalized.substring(lastSlash + 1)
    }
}

internal fun parentDirectory(path: String): String =
    path.substringBeforeLast('/', "")

internal fun joinPathSegments(basePath: String, relativePath: String): String {
    return if (relativePath.isEmpty()) {
        basePath
    } else {
        "${basePath.trimEnd('/')}/$relativePath"
    }
}
