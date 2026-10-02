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
        val now = Clock.System.now().toEpochMilliseconds()
        sweepStalePartialFiles(base, relativePath, now)
        val temp = siblingSavedFilePath(
            relativePath,
            partialFileName(relativePath.substringAfterLast('/'), now, Random.nextLong().toULong().toString(16))
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

private const val STALE_PARTIAL_FILE_AGE_MILLIS = 60L * 60L * 1000L

/** `.<name>.partial-t<epochMillis>-<random>`; the time lets later writes tell a leftover from a live write. */
internal fun partialFileName(fileName: String, nowEpochMillis: Long, random: String): String =
    ".$fileName.partial-t$nowEpochMillis-$random"

/**
 * Whether [candidate] is a temporary file of [fileName] left by a write that was killed:
 * one stamped over an hour ago, or one in the older unstamped format (no live write uses it).
 */
internal fun isStalePartialFile(candidate: String, fileName: String, nowEpochMillis: Long): Boolean {
    val prefix = ".$fileName.partial-"
    if (!candidate.startsWith(prefix)) return false
    val rest = candidate.substring(prefix.length)
    if (rest.isEmpty()) return false
    if (!rest.startsWith("t")) return rest.all { it in '0'..'9' || it in 'a'..'f' }
    val stamp = rest.substring(1).substringBefore('-').toLongOrNull() ?: return false
    return stamp <= nowEpochMillis - STALE_PARTIAL_FILE_AGE_MILLIS
}

/** A process killed mid-write leaves its hidden temp file; remove such leftovers next to the target. */
private suspend fun FileSystem.sweepStalePartialFiles(base: SaveLocation, relativePath: String, nowEpochMillis: Long) {
    val fileName = relativePath.substringAfterLast('/')
    val parent = relativePath.substringBeforeLast('/', missingDelimiterValue = "")
    val stale = runSuspendCatchingPreservingCancellation { listFiles(base, parent) }.getOrNull().orEmpty()
        .map { it.trim().trimEnd('/').substringAfterLast('/') }
        .filter { isStalePartialFile(it, fileName, nowEpochMillis) }
        .take(16)
    stale.forEach { name -> delete(base, siblingSavedFilePath(relativePath, name)) }
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
