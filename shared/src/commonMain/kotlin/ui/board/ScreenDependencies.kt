package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient

data class ScreenServiceDependencies(
    val stateStore: AppStateStore? = null,
    val autoSavedThreadRepository: SavedThreadRepository? = null,
    val cookieRepository: CookieRepository? = null,
    val fileSystem: FileSystem? = null,
    val httpClient: HttpClient? = null
)

data class BoardManagementScreenDependencies(
    val services: ScreenServiceDependencies = ScreenServiceDependencies()
) {
    val cookieRepository: CookieRepository? get() = services.cookieRepository
    val fileSystem: FileSystem? get() = services.fileSystem
    val autoSavedThreadRepository: SavedThreadRepository? get() = services.autoSavedThreadRepository
}

data class CatalogScreenDependencies(
    val repository: BoardRepository? = null,
    val services: ScreenServiceDependencies = ScreenServiceDependencies()
) {
    val stateStore: AppStateStore? get() = services.stateStore
    val autoSavedThreadRepository: SavedThreadRepository? get() = services.autoSavedThreadRepository
    val cookieRepository: CookieRepository? get() = services.cookieRepository
    val fileSystem: FileSystem? get() = services.fileSystem
    val httpClient: HttpClient? get() = services.httpClient
}

data class ThreadScreenDependencies(
    val repository: BoardRepository? = null,
    val services: ScreenServiceDependencies = ScreenServiceDependencies()
) {
    val httpClient: HttpClient? get() = services.httpClient
    val fileSystem: FileSystem? get() = services.fileSystem
    val cookieRepository: CookieRepository? get() = services.cookieRepository
    val stateStore: AppStateStore? get() = services.stateStore
    val autoSavedThreadRepository: SavedThreadRepository? get() = services.autoSavedThreadRepository
}
