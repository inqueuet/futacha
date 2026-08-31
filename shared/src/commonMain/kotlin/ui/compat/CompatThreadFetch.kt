package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.network.buildInqueuetArchiveThreadUrlFromUrl
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val COMPAT_ARCHIVE_CANDIDATE_TIMEOUT_MILLIS = 4_000L
private val compatThreadGoneHttpRegex = Regex("HTTP(?: error)?\\s+(?:404|410)\\b", RegexOption.IGNORE_CASE)

/** Identifies which network tier supplied a compatibility thread page. */
internal enum class CompatThreadFetchSource {
    CACHE,
    PRIMARY,
    ARCHIVE,
    MERGED
}

internal data class CompatThreadFetchResult(
    val page: ThreadPage,
    val source: CompatThreadFetchSource,
    /**
     * True when the live board returned 404/410 and the page shown here came
     * from an archive.  The archive copy is still useful for viewing, but it
     * must not make the read-aloud poller believe that the live thread is
     * still active.
     */
    val primaryThreadGone: Boolean = false
)

internal fun shouldFetchCompatThread(
    manual: Boolean,
    refreshOnActivation: Boolean,
    cachedPostCount: Int
): Boolean = manual || refreshOnActivation || cachedPostCount <= 0

/**
 * Failure details are kept so the UI can retain the APK-era 404/410 dead-thread behavior
 * even though the actual three-tier request sequence is shared and unit-testable.
 */
internal class CompatThreadFetchException(
    val primaryFailure: Throwable?,
    val fallbackFailure: Throwable?
) : IllegalStateException(
    (primaryFailure ?: fallbackFailure)?.message ?: "thread load failed",
    primaryFailure ?: fallbackFailure
)

/**
 * A compatibility thread is considered gone only for an explicit 404/410.
 * In particular, a timeout or another transient network failure must not stop
 * hands-free read-aloud polling.
 */
internal fun isCompatThreadGoneFailure(failure: Throwable?): Boolean {
    var current = failure
    while (current != null) {
        if (current is NetworkException && current.statusCode in setOf(404, 410)) {
            return true
        }
        if (current.message.orEmpty().contains(compatThreadGoneHttpRegex)) {
            return true
        }
        current = current.cause
    }
    return false
}

/**
 * Reads a thread from the configured cache first, then the live board, then the archive.
 * Cancellation is never converted into a fallback request: leaving a screen must stop all
 * network work immediately.
 */
internal suspend fun loadCompatThreadWithFallback(
    sourceUrl: String,
    cacheEnabled: Boolean,
    cacheBaseUrl: String?,
    expectedReplyCount: Int? = null,
    archiveLoader: (suspend (String) -> ThreadPage)? = null,
    loader: suspend (String) -> ThreadPage
): Result<CompatThreadFetchResult> {
    suspend fun attempt(url: String): Result<ThreadPage> = try {
        Result.success(loader(url))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure)
    }

    val cacheUrl = if (cacheEnabled) {
        buildCompatCacheThreadUrl(sourceUrl, cacheBaseUrl)
    } else {
        null
    }
    val cacheResult: Result<ThreadPage>? = cacheUrl
        ?.takeIf { it != sourceUrl }
        ?.let { url -> attempt(url) }
    val cachedPage = cacheResult?.getOrNull()
    if (cachedPage != null) {
        val expectedPostCount = expectedReplyCount?.plus(1)
        val needsArchiveSupplement = archiveLoader != null && (
            cachedPage.isTruncated ||
                (expectedPostCount != null && cachedPage.posts.size < expectedPostCount)
            )
        if (needsArchiveSupplement) {
            val archivePages = loadArchivePages(sourceUrl, archiveLoader)
            val merged = withContext(AppDispatchers.parsing) {
                mergeCompatThreadPages(cachedPage, archivePages)
            }
            if (merged.posts.size > cachedPage.posts.size ||
                (cachedPage.isTruncated && !merged.isTruncated)
            ) {
                return Result.success(CompatThreadFetchResult(merged, CompatThreadFetchSource.MERGED))
            }
        }
        return Result.success(CompatThreadFetchResult(cachedPage, CompatThreadFetchSource.CACHE))
    }

    val primaryResult = attempt(sourceUrl)
    if (primaryResult.isSuccess) {
        val primaryPage = primaryResult.getOrThrow()
        val expectedPostCount = expectedReplyCount?.plus(1)
        val needsArchiveSupplement = archiveLoader != null && (
            primaryPage.isTruncated ||
                (expectedPostCount != null && primaryPage.posts.size < expectedPostCount)
            )
        if (needsArchiveSupplement) {
            val archivePages = loadArchivePages(sourceUrl, archiveLoader)
            val merged = withContext(AppDispatchers.parsing) {
                mergeCompatThreadPages(primaryPage, archivePages)
            }
            if (merged.posts.size > primaryPage.posts.size ||
                (primaryPage.isTruncated && !merged.isTruncated)
            ) {
                return Result.success(CompatThreadFetchResult(merged, CompatThreadFetchSource.MERGED))
            }
        }
        return Result.success(CompatThreadFetchResult(primaryPage, CompatThreadFetchSource.PRIMARY))
    }

    // The archive provider is a separate fallback tier. A custom cache endpoint must not
    // accidentally replace it, otherwise a cache outage would skip the historical archive.
    val archiveResults = if (archiveLoader != null) {
        loadArchivePagesWithResults(sourceUrl, archiveLoader)
    } else {
        val archiveUrl = buildInqueuetArchiveThreadUrlFromUrl(sourceUrl)
        if (archiveUrl != null && archiveUrl != cacheUrl) {
            listOf(archiveUrl to attempt(archiveUrl))
        } else {
            emptyList()
        }
    }
    val firstArchiveIndex = archiveResults.indexOfFirst { it.second.isSuccess }
    val archivedPage = archiveResults.getOrNull(firstArchiveIndex)?.second?.getOrNull()
    if (archivedPage != null) {
        val additionalPages = archiveResults.drop(firstArchiveIndex + 1)
            .mapNotNull { it.second.getOrNull() }
        val merged = if (additionalPages.isEmpty()) archivedPage else {
            withContext(AppDispatchers.parsing) {
                mergeCompatThreadPages(archivedPage, additionalPages)
            }
        }
        return Result.success(
            CompatThreadFetchResult(
                page = merged,
                source = CompatThreadFetchSource.ARCHIVE,
                primaryThreadGone = isCompatThreadGoneFailure(primaryResult.exceptionOrNull())
            )
        )
    }

    return Result.failure(
        CompatThreadFetchException(
            primaryFailure = primaryResult.exceptionOrNull(),
            fallbackFailure = archiveResults.firstNotNullOfOrNull { it.second.exceptionOrNull() }
                ?: cacheResult?.exceptionOrNull()
        )
    )
}

private suspend fun loadArchivePages(
    sourceUrl: String,
    archiveLoader: suspend (String) -> ThreadPage
): List<ThreadPage> = loadArchivePagesWithResults(sourceUrl, archiveLoader)
    .mapNotNull { it.second.getOrNull() }

private suspend fun loadArchivePagesWithResults(
    sourceUrl: String,
    archiveLoader: suspend (String) -> ThreadPage
): List<Pair<String, Result<ThreadPage>>> {
    return buildCompatArchiveThreadCandidates(sourceUrl).map { archiveUrl ->
        archiveUrl to try {
            withTimeoutOrNull(COMPAT_ARCHIVE_CANDIDATE_TIMEOUT_MILLIS) {
                Result.success(archiveLoader(archiveUrl))
            } ?: Result.failure(IllegalStateException("archive candidate timeout"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Result.failure(failure)
        }
    }
}
