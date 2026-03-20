package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.BoardManagementScreenDependencies
import com.valoser.futacha.shared.ui.board.CatalogScreenDependencies
import com.valoser.futacha.shared.ui.board.ScreenServiceDependencies
import com.valoser.futacha.shared.ui.board.ThreadScreenDependencies
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient

internal fun buildScreenServiceDependencies(
    stateStore: AppStateStore? = null,
    autoSavedThreadRepository: SavedThreadRepository? = null,
    cookieRepository: CookieRepository? = null,
    fileSystem: FileSystem? = null,
    httpClient: HttpClient? = null
): ScreenServiceDependencies {
    return ScreenServiceDependencies(
        stateStore = stateStore,
        autoSavedThreadRepository = autoSavedThreadRepository,
        cookieRepository = cookieRepository,
        fileSystem = fileSystem,
        httpClient = httpClient
    )
}

internal fun buildBoardManagementScreenDependencies(
    cookieRepository: CookieRepository?,
    fileSystem: FileSystem?,
    autoSavedThreadRepository: SavedThreadRepository?
): BoardManagementScreenDependencies {
    return BoardManagementScreenDependencies(
        services = buildScreenServiceDependencies(
            autoSavedThreadRepository = autoSavedThreadRepository,
            cookieRepository = cookieRepository,
            fileSystem = fileSystem
        )
    )
}

internal fun buildCatalogScreenDependencies(
    board: BoardSummary,
    sharedRepository: BoardRepository,
    stateStore: AppStateStore,
    autoSavedThreadRepository: SavedThreadRepository?,
    cookieRepository: CookieRepository?,
    httpClient: HttpClient?
): CatalogScreenDependencies {
    return CatalogScreenDependencies(
        repository = resolveFutachaBoardRepository(board, sharedRepository),
        services = buildScreenServiceDependencies(
            stateStore = stateStore,
            autoSavedThreadRepository = autoSavedThreadRepository,
            cookieRepository = cookieRepository,
            httpClient = httpClient
        )
    )
}

internal fun buildThreadScreenDependencies(
    board: BoardSummary,
    sharedRepository: BoardRepository,
    httpClient: HttpClient?,
    fileSystem: FileSystem?,
    cookieRepository: CookieRepository?,
    stateStore: AppStateStore,
    autoSavedThreadRepository: SavedThreadRepository?
): ThreadScreenDependencies {
    return ThreadScreenDependencies(
        repository = resolveFutachaBoardRepository(board, sharedRepository),
        services = buildScreenServiceDependencies(
            stateStore = stateStore,
            autoSavedThreadRepository = autoSavedThreadRepository,
            cookieRepository = cookieRepository,
            fileSystem = fileSystem,
            httpClient = httpClient
        )
    )
}
