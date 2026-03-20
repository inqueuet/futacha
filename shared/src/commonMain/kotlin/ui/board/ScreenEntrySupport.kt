package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient

internal data class ResolvedScreenHistoryCallbacks(
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    val onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit,
    val onHistoryCleared: () -> Unit,
    val onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit,
    val onHistoryRefresh: suspend () -> Unit
)

internal data class ResolvedScreenServiceDependencies(
    val stateStore: AppStateStore?,
    val autoSavedThreadRepository: SavedThreadRepository?,
    val cookieRepository: CookieRepository?,
    val fileSystem: FileSystem?,
    val httpClient: HttpClient?
)

internal fun resolveScreenHistoryCallbacks(
    historyCallbacks: ScreenHistoryCallbacks = ScreenHistoryCallbacks(),
    onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntrySelected,
    onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntryDismissed,
    onHistoryCleared: () -> Unit = historyCallbacks.onHistoryCleared,
    onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit = historyCallbacks.onHistoryEntryUpdated,
    onHistoryRefresh: suspend () -> Unit = historyCallbacks.onHistoryRefresh
): ResolvedScreenHistoryCallbacks {
    return ResolvedScreenHistoryCallbacks(
        onHistoryEntrySelected = onHistoryEntrySelected,
        onHistoryEntryDismissed = onHistoryEntryDismissed,
        onHistoryCleared = onHistoryCleared,
        onHistoryEntryUpdated = onHistoryEntryUpdated,
        onHistoryRefresh = onHistoryRefresh
    )
}

internal fun resolveScreenServiceDependencies(
    dependencies: ScreenServiceDependencies = ScreenServiceDependencies(),
    stateStore: AppStateStore? = dependencies.stateStore,
    autoSavedThreadRepository: SavedThreadRepository? = dependencies.autoSavedThreadRepository,
    cookieRepository: CookieRepository? = dependencies.cookieRepository,
    fileSystem: FileSystem? = dependencies.fileSystem,
    httpClient: HttpClient? = dependencies.httpClient
): ResolvedScreenServiceDependencies {
    return ResolvedScreenServiceDependencies(
        stateStore = stateStore,
        autoSavedThreadRepository = autoSavedThreadRepository,
        cookieRepository = cookieRepository,
        fileSystem = fileSystem,
        httpClient = httpClient
    )
}
