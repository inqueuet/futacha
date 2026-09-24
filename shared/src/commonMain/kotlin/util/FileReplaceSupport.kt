package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.SaveLocation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Writes a file so that an existing file at [relativePath] is never truncated
 * before a complete replacement exists.
 *
 * Locations that can rename atomically write a hidden sibling and swap it in.
 * Others (SAF) write the new file under a timestamped alternate name, remove
 * the old file only afterwards and then try to take over the normal name. A
 * failure or cancellation at any point leaves either the old file or the
 * complete new one. Returns the relative path that holds the new content.
 */
internal suspend fun FileSystem.writeByteStreamReplacingImpl(
    base: SaveLocation,
    relativePath: String,
    block: suspend (FileWriteSink) -> Unit
): Result<String> = runSuspendCatchingPreservingCancellation {
    if (supportsAtomicReplace(base)) {
        val temp = siblingSavedFilePath(
            relativePath,
            ".${relativePath.substringAfterLast('/')}.partial-${Random.nextLong().toULong().toString(16)}"
        )
        try {
            writeByteStream(base, temp, block).getOrThrow()
            replaceAtomically(base, temp, relativePath).getOrThrow()
        } catch (failure: Throwable) {
            withContext(NonCancellable) { delete(base, temp) }
            throw failure
        }
        relativePath
    } else {
        val alternate = uniqueAlternateSavedFilePath(base, relativePath)
        try {
            writeByteStream(base, alternate, block).getOrThrow()
        } catch (failure: Throwable) {
            withContext(NonCancellable) { delete(base, alternate) }
            throw failure
        }
        // The new file is complete; only now may the previous one go away.
        if (exists(base, relativePath)) {
            val removed = delete(base, relativePath)
            if (removed.isFailure || exists(base, relativePath)) {
                Logger.w(
                    "FileReplace",
                    "Kept the previous $relativePath because it could not be removed; saved as $alternate " +
                        "(${removed.exceptionOrNull()?.message})"
                )
                return@runSuspendCatchingPreservingCancellation alternate
            }
        }
        renameIfAbsent(base, alternate, relativePath).getOrNull() ?: alternate
    }
}

internal fun siblingSavedFilePath(relativePath: String, fileName: String): String {
    val parent = relativePath.substringBeforeLast('/', missingDelimiterValue = "")
    return if (parent.isEmpty()) fileName else "$parent/$fileName"
}

/** `thread_media.zip` becomes `thread_media-20260924-112233.zip`, with a counter if taken. */
internal suspend fun FileSystem.uniqueAlternateSavedFilePath(
    base: SaveLocation,
    relativePath: String,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds()
): String {
    val fileName = relativePath.substringAfterLast('/')
    val dot = fileName.lastIndexOf('.').takeIf { it > 0 }
    val stem = if (dot == null) fileName else fileName.substring(0, dot)
    val extension = if (dot == null) "" else fileName.substring(dot)
    val stamp = formatAlternateSavedFileStamp(nowEpochMillis)
    var attempt = 1
    while (true) {
        val suffix = if (attempt == 1) stamp else "$stamp-$attempt"
        val candidate = siblingSavedFilePath(relativePath, "$stem-$suffix$extension")
        if (!exists(base, candidate)) return candidate
        attempt += 1
    }
}

internal fun formatAlternateSavedFileStamp(epochMillis: Long): String {
    val time = kotlin.time.Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    fun two(value: Int) = value.toString().padStart(2, '0')
    return "${time.year}${two(time.month.ordinal + 1)}${two(time.day)}-" +
        "${two(time.hour)}${two(time.minute)}${two(time.second)}"
}
