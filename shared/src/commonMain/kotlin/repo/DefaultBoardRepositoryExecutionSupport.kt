package com.valoser.futacha.shared.repo

import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

internal data class DefaultBoardRepositoryOpImageFetchResult(
    val url: String?
)

internal suspend fun initializeDefaultBoardRepositoryCookies(
    board: String,
    logTag: String,
    initializedBoards: MutableSet<String>,
    cookieRepository: CookieRepository?,
    boardInitMutex: kotlinx.coroutines.sync.Mutex,
    fetchCatalogSetup: suspend (String) -> Unit
) {
    val shouldInitialize = resolveDefaultBoardRepositoryCookieInitializationState(
        initializedBoards = initializedBoards,
        board = board,
        cookieRepository = cookieRepository,
        boardInitMutex = boardInitMutex
    )
    if (!shouldInitialize) return

    if (hasDefaultBoardRepositoryCookies(cookieRepository, board)) {
        Logger.d(logTag, "Skipping catalog setup for board $board (existing cookies found)")
        return
    }

    var fetchedSetup = false
    try {
        fetchCatalogSetup(board)
        fetchedSetup = true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.e(logTag, "Failed to initialize cookies for board $board", e)
    }

    val hasCookies = hasDefaultBoardRepositoryCookies(cookieRepository, board)
    if (fetchedSetup || hasCookies) {
        markDefaultBoardRepositoryBoardInitialized(
            initializedBoards = initializedBoards,
            board = board,
            boardInitMutex = boardInitMutex
        )
    } else {
        Logger.w(logTag, "Cookie initialization incomplete for board $board; will retry on next request")
    }
}

internal suspend fun <T> withDefaultBoardRepositoryAuthRetry(
    board: String,
    logTag: String,
    ensureCookiesInitialized: suspend (String) -> Unit,
    invalidateCookies: suspend (String) -> Unit,
    block: suspend () -> T
): T {
    try {
        ensureCookiesInitialized(board)
        return block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        if (!isDefaultBoardRepositoryLikelyCookieAuthFailure(e)) throw e

        Logger.w(logTag, "Operation failed for board $board, retrying with fresh cookies: ${e.message}")
        invalidateCookies(board)
        ensureCookiesInitialized(board)
        return block()
    }
}

internal suspend fun <T> runDefaultBoardRepositoryWithInitializedCookies(
    board: String,
    cookieRepository: CookieRepository?,
    ensureCookiesInitialized: suspend (String) -> Unit,
    block: suspend () -> T
): T {
    ensureCookiesInitialized(board)
    val exec: suspend () -> T = { block() }
    return cookieRepository?.commitOnSuccess { exec() } ?: exec()
}

internal suspend fun fetchDefaultBoardRepositoryOpImageWithPermit(
    threadId: String,
    semaphoreTimeoutMillis: Long,
    semaphore: Semaphore,
    logTag: String,
    fetch: suspend () -> String?
): DefaultBoardRepositoryOpImageFetchResult? {
    val fetchResult = withTimeoutOrNull(semaphoreTimeoutMillis) {
        semaphore.withPermit {
            DefaultBoardRepositoryOpImageFetchResult(fetch())
        }
    }
    if (fetchResult == null) {
        Logger.w(logTag, "Timeout waiting for image fetch permit for thread $threadId")
    }
    return fetchResult
}

internal suspend fun resolveDefaultBoardRepositoryOpImageUrl(
    threadId: String,
    logTag: String,
    fetchThreadHead: suspend () -> String,
    extractOpImageUrl: suspend (String) -> String?
): String? {
    return try {
        val snippet = fetchThreadHead()
        extractOpImageUrl(snippet)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Logger.w(logTag, "Failed to resolve OP image for thread $threadId: ${e.message}")
        null
    }
}
